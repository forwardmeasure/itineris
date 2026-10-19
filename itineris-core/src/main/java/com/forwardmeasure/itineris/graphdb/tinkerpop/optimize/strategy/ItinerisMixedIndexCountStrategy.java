// Copyright 2020 JanusGraph Authors
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
import org.apache.tinkerpop.gremlin.process.traversal.step.map.CountGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.Aggregation;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisMixedIndexAggStep;

import java.util.Collections;
import java.util.Set;

/**
 * If the query can be satisfied by a single mixed index query, and the query is followed by a count step, then
 * this strategy replaces original step with {@link ItinerisMixedIndexAggStep}, which fires a count aggregation query
 * against mixed index backend without retrieving all elements
 *
 * @author Boxuan Li (liboxuan@connect.hku.hk)
 */
public class ItinerisMixedIndexCountStrategy extends AbstractItinerisMixedIndexAggStrategy {
    private static final ItinerisMixedIndexCountStrategy INSTANCE = new ItinerisMixedIndexCountStrategy();
    private static final Set<Class<? extends ProviderOptimizationStrategy>> PRIORS = Collections.singleton(ItinerisStepStrategy.class);

    private ItinerisMixedIndexCountStrategy() {
    }

    @Override
    protected Aggregation getAggregation(final GraphStep originalGraphStep) {
        Step<?, ?> currentStep = originalGraphStep.getNextStep();
        while (isEligibleToSkip(currentStep)) {
            currentStep = currentStep.getNextStep();
        }
        if (currentStep instanceof CountGlobalStep) {
            return Aggregation.COUNT;
        }
        return null;
    }
    
    public static ItinerisMixedIndexCountStrategy instance() {
        return INSTANCE;
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPrior() {
        return PRIORS;
    }
}
