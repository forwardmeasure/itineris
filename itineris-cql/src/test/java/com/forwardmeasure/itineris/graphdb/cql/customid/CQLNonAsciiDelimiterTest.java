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

package com.forwardmeasure.itineris.graphdb.cql.customid;

import org.apache.tinkerpop.gremlin.structure.T;
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.graphdb.ItinerisBaseTest;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_SETTING_VERTEX_ID;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_CUSTOM_VERTEX_ID_TYPES;
import static com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier.JANUSGRAPH_RELATION_DELIMITER;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
public class CQLNonAsciiDelimiterTest extends ItinerisBaseTest {
    @Container
    public static final ItinerisCassandraContainer cqlContainer = new ItinerisCassandraContainer();

    @Test
    public void testCustomRelationDelimiter() {
        // Non-ASCII delimiter
        System.setProperty(JANUSGRAPH_RELATION_DELIMITER, "™");
        clopen(option(ALLOW_SETTING_VERTEX_ID), true, option(ALLOW_CUSTOM_VERTEX_ID_TYPES), true);
        assertThrows(ExceptionInInitializerError.class, () -> graph.addVertex(T.id, UUID.randomUUID().toString()));
        System.clearProperty(JANUSGRAPH_RELATION_DELIMITER);
    }

    @Override
    public WriteConfiguration getConfiguration() {
        return cqlContainer.getConfiguration(getClass().getSimpleName()).getConfiguration();
    }
}
