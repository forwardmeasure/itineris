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

package com.forwardmeasure.itineris.graphdb;

import org.apache.commons.configuration2.MapConfiguration;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigElement;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.CommonsConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.builder.GraphDatabaseConfigurationBuilder;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Map;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.REPLACE_INSTANCE_IF_EXISTS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.UNIQUE_INSTANCE_ID;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.UNIQUE_INSTANCE_ID_HOSTNAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.UNIQUE_INSTANCE_ID_SUFFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class GraphDatabaseConfigurationInstanceIdTest {

    private static final String NON_UNIQUE_INSTANCE_ID = "not-unique";
    private static final String NON_UNIQUE_CURRENT_INSTANCE_ID = toCurrentInstance(NON_UNIQUE_INSTANCE_ID);

    public abstract Map<String, Object> getStorageConfiguration();

    @Test
    public void graphShouldOpenWithSameInstanceId() {
        final Map<String, Object> map = getStorageConfiguration();
        map.put(UNIQUE_INSTANCE_ID.toStringWithoutRoot(), NON_UNIQUE_INSTANCE_ID);
        map.put(REPLACE_INSTANCE_IF_EXISTS.toStringWithoutRoot(), true);
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph1 = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));

        assertEquals(graph1.openManagement().getOpenInstances().size(), 1);
        assertEquals(NON_UNIQUE_CURRENT_INSTANCE_ID, graph1.openManagement().getOpenInstances().iterator().next());

        final StandardItineris graph2 = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));

        assertEquals(1, graph1.openManagement().getOpenInstances().size());
        assertEquals(NON_UNIQUE_CURRENT_INSTANCE_ID, graph1.openManagement().getOpenInstances().iterator().next());
        assertEquals(1, graph2.openManagement().getOpenInstances().size());
        assertEquals(NON_UNIQUE_CURRENT_INSTANCE_ID, graph2.openManagement().getOpenInstances().iterator().next());
        graph1.close();
        graph2.close();
    }

    @Disabled("Not working anymore. The bug is tracked here: https://github.com/JanusGraph/janusgraph/issues/2696")
    @Test
    public void graphShouldNotOpenWithSameInstanceId() {
        final Map<String, Object> map = getStorageConfiguration();
        map.put(UNIQUE_INSTANCE_ID.toStringWithoutRoot(), NON_UNIQUE_INSTANCE_ID);
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph1 = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
        assertEquals(1, graph1.openManagement().getOpenInstances().size());
        assertEquals(NON_UNIQUE_CURRENT_INSTANCE_ID, graph1.openManagement().getOpenInstances().iterator().next());
        ItinerisException janusGraphException = assertThrows(ItinerisException.class, () -> {
            final StandardItineris graph2 = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
            graph1.close();
        });
        assertEquals("A Itineris graph with the same instance id ["+NON_UNIQUE_INSTANCE_ID+"] is already open. Might required forced shutdown.",
            janusGraphException.getMessage());
    }

    @Test
    public void instanceIdShouldEqualHostname() throws UnknownHostException {
        final Map<String, Object> map = getStorageConfiguration();
        map.put(UNIQUE_INSTANCE_ID_HOSTNAME.toStringWithoutRoot(), true);
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
        assertEquals(1, graph.openManagement().getOpenInstances().size());
        assertEquals(toCurrentInstance(Inet4Address.getLocalHost().getHostName()), graph.openManagement().getOpenInstances().iterator().next());
        graph.close();
    }

    @Test
    public void instanceIdShouldEqualHostnamePlusSuffix() throws UnknownHostException {
        final Map<String, Object> map = getStorageConfiguration();
        map.put(UNIQUE_INSTANCE_ID_HOSTNAME.toStringWithoutRoot(), true);
        map.put(UNIQUE_INSTANCE_ID_SUFFIX.toStringWithoutRoot(), 1);
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
        assertEquals(1, graph.openManagement().getOpenInstances().size());
        assertEquals(toCurrentInstance(Inet4Address.getLocalHost().getHostName() + "1"), graph.openManagement().getOpenInstances().iterator().next());
        graph.close();
    }

    private static String toCurrentInstance(String instanceId){
        return ConfigElement.replaceIllegalChars(instanceId) + ManagementSystem.CURRENT_INSTANCE_SUFFIX;
    }
}
