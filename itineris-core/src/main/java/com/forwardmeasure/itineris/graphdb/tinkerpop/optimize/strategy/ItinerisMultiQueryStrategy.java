// Copyright 2021 JanusGraph Authors
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
import org.apache.tinkerpop.gremlin.process.traversal.Traversal.Admin;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.branch.RepeatStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.NoOpBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.ItinerisTraversalUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.MultiQueryPositions;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisMultiQueryStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.MultiQueriable;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisNoOpBarrierVertexOnlyStep;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (https://markorodriguez.com)
 * @author Matthias Broecheler (http://matthiasb.com)
 */
public class ItinerisMultiQueryStrategy extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy> implements TraversalStrategy.ProviderOptimizationStrategy {

    private static final Set<Class<? extends ProviderOptimizationStrategy>> PRIORS = new HashSet<>(Arrays.asList(ItinerisLocalQueryOptimizerStrategy.class, ItinerisHasStepStrategy.class, ItinerisStepStrategy.class));
    private static final ItinerisMultiQueryStrategy INSTANCE = new ItinerisMultiQueryStrategy();

    private static final MultiQueriableStepRegistrationConsumer ATTACH_FIRST_LOOP = ItinerisMultiQueryStep::attachFirstLoopClient;
    private static final MultiQueriableStepRegistrationConsumer ATTACH_SAME_LOOP = ItinerisMultiQueryStep::attachSameLoopClient;
    private static final MultiQueriableStepRegistrationConsumer ATTACH_NEXT_LOOP = ItinerisMultiQueryStep::attachNextLoopClient;

    private ItinerisMultiQueryStrategy() {
    }

    @Override
    public void apply(final Admin<?, ?> traversal) {
        if (!traversal.getGraph().isPresent() || TraversalHelper.onGraphComputer(traversal)) {
            return;
        }

        final StandardItineris janusGraph = ItinerisTraversalUtil.getItineris(traversal);
        if (janusGraph == null) {
            return;
        }

        final Optional<StandardItinerisTx> tx = ItinerisTraversalUtil.getItinerisTx(traversal);
        boolean useMultiQuery = tx.isPresent() ? tx.get().getConfiguration().useMultiQuery() : janusGraph.getConfiguration().useMultiQuery();
        if (!useMultiQuery) {
            return;
        }

        GraphDatabaseConfiguration graphConfig = janusGraph.getConfiguration();
        boolean limitedBatch = graphConfig.limitedBatch();
        int limitedBatchSize = graphConfig.limitedBatchSize();
        MultiQueryStrategyRepeatStepMode repeatStepMode = graphConfig.repeatStepMode();
        boolean multiNestedRepeatEligible;
        boolean multiNestedRepeatNextIterationEligible;
        switch (repeatStepMode){
            case CLOSEST_REPEAT_PARENT:
                multiNestedRepeatEligible = false;
                multiNestedRepeatNextIterationEligible = false;
                break;
            case ALL_REPEAT_PARENTS:
                multiNestedRepeatEligible = true;
                multiNestedRepeatNextIterationEligible = true;
                break;
            case STARTS_ONLY_OF_ALL_REPEAT_PARENTS:
                multiNestedRepeatEligible = true;
                multiNestedRepeatNextIterationEligible = false;
                break;
            default: throw new IllegalStateException("Unimplemented `repeat` step mode "+repeatStepMode.getConfigName());
        }

        insertMultiQuerySteps(traversal, limitedBatch, limitedBatchSize);
        configureMultiQueriables(traversal, limitedBatch, limitedBatchSize, multiNestedRepeatEligible, multiNestedRepeatNextIterationEligible);
    }

    /**
     * Insert ItinerisMultiQuerySteps everywhere in the current traversal where MultiQueriable steps could benefit.
     * It inserts `ItinerisMultiQueryStep` before each `MultiQueriable` step, before each `TraversalParent` step,
     * and at the end of each `RepeatTraversal` of `RepeatStep`.
     * This is OK to insert `ItinerisMultiQueryStep` before the step which won't be using it because
     * `ItinerisUnusedMultiQueryRemovalStrategy` will remove any unnecessary `ItinerisMultiQueryStep`.
     *
     * @param traversal The local traversal layer.
     */
    private void insertMultiQuerySteps(final Admin<?, ?> traversal, boolean limitedBatch, int limitedBatchSize) {
        ItinerisTraversalUtil.getSteps(step -> step instanceof MultiQueriable || step instanceof TraversalParent, traversal).forEach(step -> {
            Optional<Step> multiQueryPosition = ItinerisTraversalUtil.getLocalMultiQueryPositionForStep(step);
            if (multiQueryPosition.isPresent() && ItinerisTraversalUtil.isLegalMultiQueryPosition(multiQueryPosition.get())) {
                insertMultiQueryStep(multiQueryPosition.get(), limitedBatch, limitedBatchSize);
            }
            if(step instanceof RepeatStep){
                insertMultiQueryStepToTraversalEnd(((RepeatStep) step).getRepeatTraversal(), limitedBatch, limitedBatchSize);
            }
        });
    }

    private void insertMultiQueryStep(Step position, boolean limitedBatch, int limitedBatchSize){
        final Admin<?, ?> traversal = position.getTraversal();
        final ItinerisMultiQueryStep multiQueryStep;
        if(limitedBatch){
            if(position instanceof NoOpBarrierStep){
                multiQueryStep = new ItinerisMultiQueryStep(traversal, limitedBatch,
                    ((NoOpBarrierStep) position).getMaxBarrierSize());
            } else {
                NoOpBarrierStep barrier = new ItinerisNoOpBarrierVertexOnlyStep(traversal, limitedBatchSize);
                TraversalHelper.insertBeforeStep(barrier, position, traversal);
                position = barrier;
                multiQueryStep = new ItinerisMultiQueryStep(traversal, limitedBatch, barrier);
            }
        } else {
            multiQueryStep = new ItinerisMultiQueryStep(traversal, limitedBatch);
        }
        TraversalHelper.insertBeforeStep(multiQueryStep, position, traversal);
    }

    private void insertMultiQueryStepToTraversalEnd(Traversal.Admin traversal, boolean limitedBatch, int limitedBatchSize){
        Optional<Step> optionalRepeatEndMultiQueryPosition = ItinerisTraversalUtil.getEndMultiQueryPosition(traversal);
        if(!optionalRepeatEndMultiQueryPosition.isPresent()){
            return;
        }

        Step currentStep = optionalRepeatEndMultiQueryPosition.get();
        insertMultiQueryStep(currentStep, limitedBatch, limitedBatchSize);
    }

    /**
     * Looks for MultiQueriables in within the traversal and registers them as clients of their respective
     * ItinerisMultiQuerySteps
     *
     * @param traversal The local traversal layer.
     */
    private void configureMultiQueriables(final Admin<?, ?> traversal,
                                          final boolean limitedBatch,
                                          final int limitedBatchSize,
                                          final boolean multiNestedRepeatEligible,
                                          final boolean multiNestedRepeatNextIterationEligible) {
        TraversalHelper.getStepsOfAssignableClass(MultiQueriable.class, traversal).forEach(multiQueriable -> {
            MultiQueryPositions multiQueryPositions = ItinerisTraversalUtil.getAllMultiQueryPositionsForMultiQueriable(
                multiQueriable, multiNestedRepeatEligible, multiNestedRepeatNextIterationEligible);

            // If one position is not legal, this means that the entire step can not use the multiQuery feature.
            if (hasIllegalPositions(multiQueryPositions)) {
                return;
            }

            boolean barrierSizeSet = !limitedBatch;

            for (Step mqPos : multiQueryPositions.currentLoopMultiQueryStepLocations) {
                if(applyPreviousMultiQueryAndReturnIfBarrierSizeSet(multiQueriable, mqPos, barrierSizeSet, ATTACH_SAME_LOOP)){
                    barrierSizeSet = true;
                }
            }

            if(multiQueryPositions.nextLoopMultiQueryStepLocation != null &&
                applyPreviousMultiQueryAndReturnIfBarrierSizeSet(multiQueriable,
                    multiQueryPositions.nextLoopMultiQueryStepLocation, barrierSizeSet, ATTACH_NEXT_LOOP)){
                barrierSizeSet = true;
            }

            for (Step mqPos : multiQueryPositions.firstLoopMultiQueryStepLocations) {
                if(applyPreviousMultiQueryAndReturnIfBarrierSizeSet(multiQueriable, mqPos, barrierSizeSet, ATTACH_FIRST_LOOP)){
                    barrierSizeSet = true;
                }
            }

            if(!barrierSizeSet){
                multiQueriable.setBatchSize(limitedBatchSize);
            }

            if(!multiQueryPositions.currentLoopMultiQueryStepLocations.isEmpty() ||
                !multiQueryPositions.firstLoopMultiQueryStepLocations.isEmpty() ||
                multiQueryPositions.nextLoopMultiQueryStepLocation != null){
                // MultiQuery is applicable
                multiQueriable.setUseMultiQuery(true);
            }
        });
    }

    private boolean applyPreviousMultiQueryAndReturnIfBarrierSizeSet(MultiQueriable multiQueriableStep, Step multiQueryPositionStep, boolean barrierSizeSet, MultiQueriableStepRegistrationConsumer attachClientConsumer){
        final Optional<ItinerisMultiQueryStep> optionalMultiQueryStep =
            ItinerisTraversalUtil.getPreviousStepOfClass(ItinerisMultiQueryStep.class, multiQueryPositionStep);
        if(optionalMultiQueryStep.isPresent()){
            ItinerisMultiQueryStep multiQueryStep = optionalMultiQueryStep.get();
            attachClientConsumer.attachClient(multiQueryStep, multiQueriableStep);
            if(!barrierSizeSet){
                Optional<Integer> localSetBatchSize = ItinerisTraversalUtil.getLocalNonMultiQueryProvidedBatchSize(multiQueriableStep);
                if(localSetBatchSize.isPresent()){
                    multiQueriableStep.setBatchSize(localSetBatchSize.get());
                    return true;
                } else {
                    Optional<Integer> optionalRelatedBarrierStepSize = multiQueryStep.getRelatedBarrierStepSize();
                    if(optionalRelatedBarrierStepSize.isPresent()){
                        multiQueriableStep.setBatchSize(optionalRelatedBarrierStepSize.get());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean hasIllegalPositions(MultiQueryPositions multiQueryPositions){
        if (multiQueryPositions.nextLoopMultiQueryStepLocation != null && !ItinerisTraversalUtil.isLegalMultiQueryPosition(multiQueryPositions.nextLoopMultiQueryStepLocation)) {
            return true;
        }
        for (Step mqPos : multiQueryPositions.currentLoopMultiQueryStepLocations) {
            if (!ItinerisTraversalUtil.isLegalMultiQueryPosition(mqPos)) {
                return true;
            }
        }
        for (Step mqPos : multiQueryPositions.firstLoopMultiQueryStepLocations) {
            if (!ItinerisTraversalUtil.isLegalMultiQueryPosition(mqPos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPrior() {
        return PRIORS;
    }

    public static ItinerisMultiQueryStrategy instance() {
        return INSTANCE;
    }
}
