// Copyright 2020 Itineris Authors
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

import static com.forwardmeasure.itineris.graphdb.types.system.ImplicitKey.ADJACENT_ID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tinkerpop.gremlin.process.traversal.Compare;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.DefaultGraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.FilterStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.HasStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.IdStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalHelper;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicateUtils;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.query.condition.MultiCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.query.index.IndexSelectionUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.ItinerisTraversalUtil;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.CompositeIndexType;

/**
 * @author Florian Grieskamp (Florian.Grieskamp@gdata.de)
 */
public class AdjacentVertexHasUniquePropertyOptimizerStrategy
        extends AdjacentVertexOptimizerStrategy<HasStep<?>> {

    private static final AdjacentVertexHasUniquePropertyOptimizerStrategy INSTANCE = new AdjacentVertexHasUniquePropertyOptimizerStrategy();

    private static final Set<Class<? extends ProviderOptimizationStrategy>> POSTS = new HashSet<>(
            Arrays.asList(ItinerisStepStrategy.class, ItinerisLocalQueryOptimizerStrategy.class));

    private AdjacentVertexHasUniquePropertyOptimizerStrategy() {
    }

    public static AdjacentVertexHasUniquePropertyOptimizerStrategy instance() {
        return INSTANCE;
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPost() {
        return POSTS;
    }

    @Override
    public void apply(final Traversal.Admin<?, ?> traversal) {
        if (TraversalHelper.onGraphComputer(traversal) || !traversal.getGraph().isPresent()) {
            return;
        }

        final StandardItineris janusGraph = ItinerisTraversalUtil.getItineris(traversal);
        if (janusGraph == null) {
            return;
        }

        if (!janusGraph.getConfiguration().optimizerBackendAccess()) {
            return;
        }

        TraversalHelper.getStepsOfClass(HasStep.class, traversal)
                .forEach(this::optimizeStep);
    }

    /**
     * Determines whether this HasStep can be answered by a unique index and thus,
     * returns either 0 or 1 match
     */
    @Override
    protected boolean isValidStep(HasStep<?> step) {
        StandardItinerisTx tx = (StandardItinerisTx) ItinerisTraversalUtil.getTx(step.getTraversal());

        List<String> givenKeys = step.getHasContainers().stream()
                .map(HasContainer::getKey).collect(Collectors.toList());

        List<PredicateCondition<String, ItinerisElement>> constraints = step.getHasContainers().stream()
                .filter(hc -> hc.getBiPredicate() == Compare.eq)
                .map(hc -> new PredicateCondition<>(hc.getKey(), ItinerisPredicateUtils.convert(hc.getBiPredicate()),
                        hc.getValue()))
                .filter(pc -> tx.validDataType(pc.getValue().getClass()))
                .collect(Collectors.toList());
        final MultiCondition<ItinerisElement> conditions = QueryUtil.constraints2QNF(tx, constraints);

        // check all matching unique indexes
        return IndexSelectionUtil.existsMatchingIndex(conditions,
                indexType -> indexType.isCompositeIndex()
                        && ((CompositeIndexType) indexType).getCardinality() == Cardinality.SINGLE
                        && IndexSelectionUtil.isIndexSatisfiedByGivenKeys(indexType, givenKeys));
    }

    private Traversal.Admin<?, Long> generateFilter(Traversal.Admin<?, ?> traversal, HasStep<?> originalStep) {
        Traversal.Admin filterTraversal = new DefaultGraphTraversal<>(traversal.getGraph().get())
                .addStep(
                        new GraphStep<>(traversal, Vertex.class, true));

        filterTraversal.addStep(originalStep);
        filterTraversal.addStep(new IdStep<>(filterTraversal));
        return filterTraversal;
    }

    @Override
    protected FilterStep<Edge> makeFilterByAdjacentIdStep(Traversal.Admin<?, ?> traversal, HasStep<?> originalStep) {
        Traversal.Admin<?, Long> filterTraversal = generateFilter(traversal, originalStep);
        if (filterTraversal.hasNext()) {
            HasContainer hc = new HasContainer(ADJACENT_ID.name(), P.eq(filterTraversal.next()));
            return new HasStep<>(traversal, hc);
        } else {
            HasContainer neverMatch = new HasContainer(T.id.getAccessor(), P.within(Collections.emptyList()));
            return new HasStep<>(traversal, neverMatch);
        }
    }
}
