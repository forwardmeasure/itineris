// Copyright 2017 JanusGraph Authors
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

import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Graph;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.idassigner.placement.SimpleBulkPlacementStrategy;
import com.forwardmeasure.itineris.graphdb.olap.computer.FulgoraGraphComputer;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public abstract class AbstractItinerisComputerProvider extends AbstractItinerisProvider {

    @Override
    public GraphTraversalSource traversal(final Graph graph) {
        return new GraphTraversalSource(graph).withComputer(FulgoraGraphComputer.class);
    }

    @Override
    public GraphTraversalSource traversal(final Graph graph, final TraversalStrategy... strategies) {
        return new GraphTraversalSource(graph).withComputer(FulgoraGraphComputer.class).withStrategies(strategies);
    }

    @Override
    public ModifiableConfiguration getItinerisConfiguration(String graphName, Class<?> test, String testMethodName) {
        return GraphDatabaseConfiguration.buildGraphConfiguration()
                .set(SimpleBulkPlacementStrategy.CONCURRENT_PARTITIONS,1)
                .set(GraphDatabaseConfiguration.CLUSTER_MAX_PARTITIONS, 2)
                .set(GraphDatabaseConfiguration.IDAUTHORITY_CAV_BITS,0);
    }

}
