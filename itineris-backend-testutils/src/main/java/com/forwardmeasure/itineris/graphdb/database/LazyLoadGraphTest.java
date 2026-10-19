// Copyright 2024 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.database;

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategies;
import org.apache.tinkerpop.gremlin.structure.Graph;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.AdjacentVertexFilterOptimizerStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.AdjacentVertexHasIdOptimizerStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.AdjacentVertexHasUniquePropertyOptimizerStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.AdjacentVertexIsOptimizerStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisHasStepStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisIoRegistrationStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisLocalQueryOptimizerStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisMixedIndexAggStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisMixedIndexCountStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisMultiQueryStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisStepStrategy;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.ItinerisUnusedMultiQueryRemovalStrategy;
import com.forwardmeasure.itineris.graphdb.transaction.StandardTransactionBuilder;

public class LazyLoadGraphTest extends StandardItineris {

    static {
        TraversalStrategies graphStrategies =
            TraversalStrategies.GlobalCache.getStrategies(Graph.class)
                .clone()
                .addStrategies(AdjacentVertexFilterOptimizerStrategy.instance(),
                    AdjacentVertexHasIdOptimizerStrategy.instance(),
                    AdjacentVertexIsOptimizerStrategy.instance(),
                    AdjacentVertexHasUniquePropertyOptimizerStrategy.instance(),
                    ItinerisLocalQueryOptimizerStrategy.instance(),
                    ItinerisHasStepStrategy.instance(),
                    ItinerisMultiQueryStrategy.instance(),
                    ItinerisUnusedMultiQueryRemovalStrategy.instance(),
                    ItinerisMixedIndexAggStrategy.instance(),
                    ItinerisMixedIndexCountStrategy.instance(),
                    ItinerisStepStrategy.instance(),
                    ItinerisIoRegistrationStrategy.instance());

        //Register with cache
        TraversalStrategies.GlobalCache.registerStrategies(LazyLoadGraphTest.class, graphStrategies);
    }

    public LazyLoadGraphTest(GraphDatabaseConfiguration configuration) {
        super(configuration);
    }

    @Override
    public StandardTransactionBuilder buildTransaction() {
        return (StandardTransactionBuilder) new StandardTransactionBuilder(getConfiguration(), this)
            .lazyLoadRelations();
    }
}
