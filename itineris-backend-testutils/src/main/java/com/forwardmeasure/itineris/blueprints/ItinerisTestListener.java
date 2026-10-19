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

package com.forwardmeasure.itineris.blueprints;

import org.apache.tinkerpop.gremlin.GraphProvider;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.ProfileTest;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisMultiQueryStrategy;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

import java.util.HashMap;
import java.util.Map;

/**
 * This class allows to exclude certain strategies for specified tests.
 * It can sometimes be necessary to do so, in case one of the TinkerPop
 * tests expects a specific number of steps in a query. (e.g. ProfileTest)
 */
public class ItinerisTestListener implements GraphProvider.TestListener {

    private static final ItinerisTestListener INSTANCE = new ItinerisTestListener();

    private static final Map<Class<?>, TraversalStrategy[]> testSpecificDisabledStrategies;

    static {
        testSpecificDisabledStrategies = new HashMap<>();

        // reference: https://groups.google.com/g/gremlin-users/c/93dssDZVJEM
        testSpecificDisabledStrategies.put(ProfileTest.Traversals.class, new TraversalStrategy[] {ItinerisMultiQueryStrategy.instance()});
    }

    private ItinerisTestListener() {
    }

    public static ItinerisTestListener instance() {
        return INSTANCE;
    }

    public void onTestStart(final Class<?> test, final String testName) {
        if (testSpecificDisabledStrategies.containsKey(test)) {
            /*
             * disable test-specific strategies
             */
            TraversalStrategy[] disabledStrategies = testSpecificDisabledStrategies.get(test);
            Class[] strategyClasses = new Class[disabledStrategies.length];
            for (int i = 0; i < disabledStrategies.length; i++) {
                strategyClasses[i] = disabledStrategies[i].getClass();
            }
            TraversalStrategies.GlobalCache.registerStrategies(StandardItineris.class,
                TraversalStrategies.GlobalCache.getStrategies(StandardItineris.class).clone()
                .removeStrategies(strategyClasses));
            TraversalStrategies.GlobalCache.registerStrategies(StandardItinerisTx.class,
                TraversalStrategies.GlobalCache.getStrategies(StandardItinerisTx.class).clone()
                .removeStrategies(strategyClasses));
        }
    }

    public void onTestEnd(final Class<?> test, final String testName) {
        if (testSpecificDisabledStrategies.containsKey(test)) {
            /*
             * re-enable test-specific strategies
             */
            TraversalStrategy[] disabledStrategies = testSpecificDisabledStrategies.get(test);
            TraversalStrategies.GlobalCache.registerStrategies(StandardItineris.class,
                TraversalStrategies.GlobalCache.getStrategies(StandardItineris.class).clone()
                .addStrategies(disabledStrategies));
            TraversalStrategies.GlobalCache.registerStrategies(StandardItinerisTx.class,
                TraversalStrategies.GlobalCache.getStrategies(StandardItinerisTx.class).clone()
                .addStrategies(disabledStrategies));
        }
    }
}
