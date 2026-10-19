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

import org.apache.tinkerpop.gremlin.process.traversal.Step;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.MaxGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.MeanGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.MinGlobalStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.PropertiesStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.SumGlobalStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.Aggregation;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisMixedIndexAggStep;

import java.util.Collections;
import java.util.Set;

/**
 * If the query can be satisfied by a single mixed index query, and the query is followed by an aggregation step, then
 * this strategy replaces original step with {@link ItinerisMixedIndexAggStep}, which fires an aggregation query
 * against mixed index backend without retrieving all elements
 *
 * @author Thomas Franco (thomas@strangebee.com)
 */
public class ItinerisMixedIndexAggStrategy extends AbstractItinerisMixedIndexAggStrategy {
    private static final ItinerisMixedIndexAggStrategy INSTANCE = new ItinerisMixedIndexAggStrategy();
    private static final Set<Class<? extends ProviderOptimizationStrategy>> PRIORS = Collections.singleton(ItinerisStepStrategy.class);

    private ItinerisMixedIndexAggStrategy() {
    }

    @Override
    protected Aggregation getAggregation(final GraphStep originalGraphStep) {
        Step<?, ?> currentStep = originalGraphStep.getNextStep();
        while (isEligibleToSkip(currentStep)) {
            currentStep = currentStep.getNextStep();
        }
        if (currentStep instanceof PropertiesStep) {
            final String[] propertyKeys = ((PropertiesStep<?>) currentStep).getPropertyKeys();
            if (propertyKeys.length == 1) {
                final String fieldName = propertyKeys[0];
                currentStep = currentStep.getNextStep();
                while (isEligibleToSkip(currentStep)) {
                    currentStep = currentStep.getNextStep();
                }
                if (currentStep instanceof MaxGlobalStep) return Aggregation.MAX(fieldName);
                else if (currentStep instanceof MinGlobalStep) return Aggregation.MIN(fieldName);
                else if (currentStep instanceof MeanGlobalStep) return Aggregation.AVG(fieldName);
                else if (currentStep instanceof SumGlobalStep) return Aggregation.SUM(fieldName);
            }
        }
        return null;
    }

    public static ItinerisMixedIndexAggStrategy instance() {
        return INSTANCE;
    }

    @Override
    public Set<Class<? extends ProviderOptimizationStrategy>> applyPrior() {
        return PRIORS;
    }
}
