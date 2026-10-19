// Copyright 2023 Itineris Authors
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

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.NoOpBarrierStep;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.ItinerisTraversalUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisMultiQueryStep;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Removes any `ItinerisMultiQueryStep` without any registered `clientSteps`. When removing such
 * `ItinerisMultiQueryStep` this strategy also removes any barrier step created in `ItinerisMultiQueryStrategy`
 * for the respected `ItinerisMultiQueryStep`. This strategy doesn't remove any barrier step which was explicitly created
 * (i.e. those, which are not in direct relation with the unused `ItinerisMultiQueryStep`).
 */
public class ItinerisUnusedMultiQueryRemovalStrategy extends AbstractTraversalStrategy<TraversalStrategy.ProviderOptimizationStrategy> implements TraversalStrategy.ProviderOptimizationStrategy {

    private static final Set<Class<? extends ProviderOptimizationStrategy>> PRIORS = new HashSet<>(Arrays.asList(ItinerisMultiQueryStrategy.class));
    private static final ItinerisUnusedMultiQueryRemovalStrategy INSTANCE = new ItinerisUnusedMultiQueryRemovalStrategy();

    private ItinerisUnusedMultiQueryRemovalStrategy() {
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
        boolean useMultiQuery = tx.isPresent() ? tx.get().getConfiguration().useMultiQuery() : janusGraph.getConfiguration().useMultiQuery();
        if (!useMultiQuery) {
            return;
        }

        List<ItinerisMultiQueryStep> unusedMultiQuerySteps = findUnusedMultiQuerySteps(traversal);
        unusedMultiQuerySteps.forEach(multiQueryStep -> {
            NoOpBarrierStep generatedBarrierStep = multiQueryStep.getGeneratedBarrierStep();
            traversal.removeStep(multiQueryStep);
            if(generatedBarrierStep != null){
                traversal.removeStep(generatedBarrierStep);
            }
        });
    }

    private List<ItinerisMultiQueryStep> findUnusedMultiQuerySteps(final Traversal.Admin<?, ?> traversal){
        List<ItinerisMultiQueryStep> unusedMultiQuerySteps = new ArrayList<>();
        traversal.getSteps().forEach(step -> {
            if(step instanceof ItinerisMultiQueryStep
                && ((ItinerisMultiQueryStep) step).isSameLoopClientStepsEmpty()
                && ((ItinerisMultiQueryStep) step).isNextLoopClientStepsEmpty()
                && ((ItinerisMultiQueryStep) step).isFirstLoopClientStepsEmpty()){
                unusedMultiQuerySteps.add((ItinerisMultiQueryStep) step);
            }
        });
        return unusedMultiQuerySteps;
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPrior() {
        return PRIORS;
    }

    public static ItinerisUnusedMultiQueryRemovalStrategy instance() {
        return INSTANCE;
    }
}
