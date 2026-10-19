// Copyright 2023 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy;

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.LocalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.DropStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.RangeGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.ElementMapStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.LabelStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertyMapStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.VertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.ItinerisTraversalUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.HasStepFolder;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisDropStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisElementMapStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisLabelStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisPropertiesStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisPropertyMapStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisVertexStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.MultiQueriable;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.transaction.TransactionConfiguration;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (https://markorodriguez.com)
 * @author Matthias Broecheler (http://matthiasb.com)
 */
public class ItinerisLocalQueryOptimizerStrategy extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy> implements TraversalStrategy.ProviderOptimizationStrategy {

    private static final ItinerisLocalQueryOptimizerStrategy INSTANCE = new ItinerisLocalQueryOptimizerStrategy();

    private static final Set<Class<? extends ProviderOptimizationStrategy>> PRIORS = Collections.singleton(AdjacentVertexFilterOptimizerStrategy.class);

    private ItinerisLocalQueryOptimizerStrategy() {
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
        if (!traversal.getGraph().isPresent())
            return;

        final StandardItineris janusGraph = ItinerisTraversalUtil.getItineris(traversal);
        if (janusGraph == null) {
            return;
        }

        final Optional<StandardItinerisTx> tx = ItinerisTraversalUtil.getItinerisTx(traversal);
        final MultiQueryPropertiesStrategyMode propertiesStrategyMode;
        final MultiQueryLabelStepStrategyMode labelStepStrategyMode;
        final MultiQueryDropStepStrategyMode dropStepStrategyMode;
        final int txVertexCacheSize;

        if(tx.isPresent()){
            TransactionConfiguration txConfig = tx.get().getConfiguration();
            txVertexCacheSize = txConfig.getVertexCacheSize();
            propertiesStrategyMode = txConfig.getPropertiesStrategyMode();
            labelStepStrategyMode = txConfig.getLabelStepStrategyMode();
            dropStepStrategyMode = txConfig.getDropStepStrategyMode();
        } else {
            GraphDatabaseConfiguration graphConfig = janusGraph.getConfiguration();
            txVertexCacheSize = graphConfig.getTxVertexCacheSize();
            propertiesStrategyMode = graphConfig.propertiesStrategyMode();
            labelStepStrategyMode = graphConfig.labelStepStrategyMode();
            dropStepStrategyMode = graphConfig.dropStepStrategyMode();
        }

        applyItinerisVertexSteps(traversal);
        applyItinerisPropertiesSteps(traversal, txVertexCacheSize, propertiesStrategyMode);
        applyItinerisLabelSteps(traversal, labelStepStrategyMode);
        applyItinerisDropSteps(traversal, dropStepStrategyMode);
        inspectLocalTraversals(traversal, txVertexCacheSize, propertiesStrategyMode);
    }

    private void applyItinerisVertexSteps(Traversal.Admin<?, ?> traversal) {
        TraversalHelper.getStepsOfAssignableClass(VertexStep.class, traversal).forEach(originalStep -> {
            final ItinerisVertexStep vertexStep = new ItinerisVertexStep(originalStep);
            TraversalHelper.replaceStep(originalStep, vertexStep, originalStep.getTraversal());

            if (ItinerisTraversalUtil.isEdgeReturnStep(vertexStep)) {
                HasStepFolder.foldInHasContainer(vertexStep, originalStep.getTraversal(), originalStep.getTraversal());
                //We cannot fold in orders or ranges since they are not local
            }

            assert ItinerisTraversalUtil.isEdgeReturnStep(vertexStep) || ItinerisTraversalUtil.isVertexReturnStep(vertexStep);
            final Step nextStep = ItinerisTraversalUtil.getNextNonIdentityStep(vertexStep);
            if (nextStep instanceof RangeGlobalStep) {
                final int limit = QueryUtil.convertLimit(((RangeGlobalStep) nextStep).getHighRange());
                vertexStep.setLimit(0, QueryUtil.mergeHighLimits(limit, vertexStep.getHighLimit()));
            }
        });
    }

    private void applyItinerisPropertiesSteps(Traversal.Admin<?, ?> traversal, int txVertexCacheSize, MultiQueryPropertiesStrategyMode propertiesStrategyMode) {
        ItinerisTraversalUtil.getSteps(step -> step instanceof PropertiesStep || step instanceof PropertyMapStep || step instanceof ElementMapStep, traversal).forEach(originalStep -> {
            if(originalStep instanceof MultiQueriable){
                return;
            }

            final MultiQueriable propertiesPrefetchingStep;

            boolean prefetchAllPropertiesRequired = MultiQueryPropertiesStrategyMode.ALL_PROPERTIES.equals(propertiesStrategyMode);
            boolean prefetchAllowed = !MultiQueryPropertiesStrategyMode.NONE.equals(propertiesStrategyMode);

            if(originalStep instanceof PropertiesStep){

                final ItinerisPropertiesStep propertiesStep = new ItinerisPropertiesStep((PropertiesStep) originalStep,
                    prefetchAllPropertiesRequired, prefetchAllowed);
                TraversalHelper.replaceStep(originalStep, propertiesStep, originalStep.getTraversal());
                if (propertiesStep.getReturnType().forProperties()) {
                    HasStepFolder.foldInHasContainer(propertiesStep, originalStep.getTraversal(), originalStep.getTraversal());
                    //We cannot fold in orders or ranges since they are not local
                }
                propertiesPrefetchingStep = propertiesStep;

            } else if(originalStep instanceof PropertyMapStep){

                final ItinerisPropertyMapStep propertyMapStep = new ItinerisPropertyMapStep((PropertyMapStep) originalStep,
                    prefetchAllPropertiesRequired, prefetchAllowed);
                TraversalHelper.replaceStep(originalStep, propertyMapStep, originalStep.getTraversal());
                propertiesPrefetchingStep = propertyMapStep;

            } else if(originalStep instanceof ElementMapStep){

                final ItinerisElementMapStep elementMapStep = new ItinerisElementMapStep((ElementMapStep) originalStep,
                    prefetchAllPropertiesRequired, prefetchAllowed);
                TraversalHelper.replaceStep(originalStep, elementMapStep, originalStep.getTraversal());
                propertiesPrefetchingStep = elementMapStep;

            } else {
                return;
            }

            propertiesPrefetchingStep.setBatchSize(txVertexCacheSize);

        });
    }

    private void inspectLocalTraversals(final Traversal.Admin<?, ?> traversal, int txVertexCacheSize, MultiQueryPropertiesStrategyMode propertiesStrategyMode) {
        TraversalHelper.getStepsOfClass(LocalStep.class, traversal).forEach(localStep -> {
            final Traversal.Admin localTraversal = ((LocalStep<?, ?>) localStep).getLocalChildren().get(0);
            final Step localStart = localTraversal.getStartStep();

            if (localStart instanceof VertexStep) {
                final ItinerisVertexStep vertexStep = new ItinerisVertexStep((VertexStep) localStart);
                vertexStep.setBatchSize(txVertexCacheSize);
                TraversalHelper.replaceStep(localStart, vertexStep, localTraversal);

                if (ItinerisTraversalUtil.isEdgeReturnStep(vertexStep)) {
                    HasStepFolder.foldInHasContainer(vertexStep, localTraversal, traversal);
                    HasStepFolder.foldInOrder(vertexStep, vertexStep.getNextStep(), localTraversal, traversal, false, null);
                }
                HasStepFolder.foldInRange(vertexStep, ItinerisTraversalUtil.getNextNonIdentityStep(vertexStep), localTraversal, null);
                unfoldLocalTraversal(traversal, localStep, localTraversal, vertexStep);

            } else if (localStart instanceof PropertiesStep) {

                boolean prefetchAllPropertiesRequired = MultiQueryPropertiesStrategyMode.ALL_PROPERTIES.equals(propertiesStrategyMode);
                boolean prefetchAllowed = !MultiQueryPropertiesStrategyMode.NONE.equals(propertiesStrategyMode);
                final ItinerisPropertiesStep propertiesStep = new ItinerisPropertiesStep((PropertiesStep) localStart, prefetchAllPropertiesRequired, prefetchAllowed);
                propertiesStep.setBatchSize(txVertexCacheSize);

                TraversalHelper.replaceStep(localStart, propertiesStep, localTraversal);

                if (propertiesStep.getReturnType().forProperties()) {
                    HasStepFolder.foldInHasContainer(propertiesStep, localTraversal, traversal);
                    HasStepFolder.foldInOrder(propertiesStep, propertiesStep.getNextStep(), localTraversal, traversal, false, null);
                }
                HasStepFolder.foldInRange(propertiesStep, ItinerisTraversalUtil.getNextNonIdentityStep(propertiesStep), localTraversal, null);
                unfoldLocalTraversal(traversal, localStep, localTraversal, propertiesStep);
            }
        });
    }

    private void applyItinerisLabelSteps(Traversal.Admin<?, ?> traversal, MultiQueryLabelStepStrategyMode labelStepStrategyMode){
        if(MultiQueryLabelStepStrategyMode.NONE.equals(labelStepStrategyMode)){
            return;
        }
        TraversalHelper.getStepsOfAssignableClass(LabelStep.class, traversal).forEach(originalStep -> {
            if(originalStep instanceof ItinerisLabelStep){
                return;
            }
            final ItinerisLabelStep janusGraphLabelStep = new ItinerisLabelStep(originalStep);
            TraversalHelper.replaceStep(originalStep, janusGraphLabelStep, originalStep.getTraversal());
        });
    }

    private void applyItinerisDropSteps(Traversal.Admin<?, ?> traversal, MultiQueryDropStepStrategyMode dropStepStrategyMode){
        if(MultiQueryDropStepStrategyMode.NONE.equals(dropStepStrategyMode)){
            return;
        }
        TraversalHelper.getStepsOfAssignableClass(DropStep.class, traversal).forEach(originalStep -> {
            if(originalStep instanceof ItinerisDropStep){
                return;
            }
            final ItinerisDropStep janusGraphDropStep = new ItinerisDropStep(originalStep);
            TraversalHelper.replaceStep(originalStep, janusGraphDropStep, originalStep.getTraversal());
        });
    }

    private static void unfoldLocalTraversal(final Traversal.Admin<?, ?> traversal,
                                             LocalStep<?, ?> localStep, Traversal.Admin localTraversal,
                                             MultiQueriable vertexStep) {
        assert localTraversal.asAdmin().getSteps().size() > 0;
        if (localTraversal.asAdmin().getSteps().size() == 1) {
            //Can replace the entire localStep by the vertex step in the outer traversal
            assert localTraversal.getStartStep() == vertexStep;
            vertexStep.setTraversal(traversal);
            TraversalHelper.replaceStep(localStep, vertexStep, traversal);
        }
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPrior() {
        return PRIORS;
    }

    public static ItinerisLocalQueryOptimizerStrategy instance() {
        return INSTANCE;
    }
}
