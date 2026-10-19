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

package com.forwardmeasure.itineris.graphdb.cql.customid;

import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.graphdb.ItinerisBaseTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_SETTING_VERTEX_ID;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_CUSTOM_VERTEX_ID_TYPES;
import static com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier.ITINERIS_RELATION_DELIMITER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junitpioneer.jupiter.SetEnvironmentVariable;

@Testcontainers
public class CQLCustomDelimiterTest extends ItinerisBaseTest {
    @Container
    public static final ItinerisCassandraContainer cqlContainer = new ItinerisCassandraContainer();

    @Test
    @SetEnvironmentVariable(key = ITINERIS_RELATION_DELIMITER, value = "@")
    public void testCustomRelationDelimiter() throws Exception {
        clopen(option(ALLOW_SETTING_VERTEX_ID), true, option(ALLOW_CUSTOM_VERTEX_ID_TYPES), true);
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        Vertex v1 = graph.addVertex(T.id, id1, "prop", "val");
        Vertex v2 = graph.addVertex(T.id, id2, "prop", "val");
        graph.traversal().addE("link").from(v1).to(v2).property("count", 1).next();
        graph.tx().commit();

        assertEquals(2, graph.traversal().V().count().next());
        assertEquals(1, graph.traversal().E().count().next());
        Edge edge = graph.traversal().E().has("count", 1).next();
        assertEquals(id1, edge.outVertex().id());
        assertEquals(id2, edge.inVertex().id());
    }

    @Override
    public WriteConfiguration getConfiguration() {
        return cqlContainer.getConfiguration(getClass().getSimpleName()).getConfiguration();
    }
}
