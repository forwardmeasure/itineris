// Copyright 2018 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.management;

import org.apache.commons.configuration2.MapConfiguration;
import org.apache.tinkerpop.gremlin.server.Settings;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.CommonsConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.builder.GraphDatabaseConfigurationBuilder;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.GRAPH_NAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ManagementLoggerGraphCacheEvictionTest {

    @AfterEach
    public void cleanUp() {
        ItinerisManager.shutdownItinerisManager();
    }

    @Test
    public void shouldNotBeAbleToEvictGraphWhenItinerisManagerIsNull() {
        final Map<String, Object> map = new HashMap<>();
        map.put(STORAGE_BACKEND.toStringWithoutRoot(), "inmemory");
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
        final ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
        mgmt.evictGraphFromCache();
        mgmt.commit();

        assertNull(ItinerisManager.getInstance());
    }

    @Test
    public void graphShouldBeRemovedFromCache() throws InterruptedException {
        final ItinerisManager jgm = new ItinerisManager(new Settings());
        assertNotNull(jgm);
        assertNotNull(ItinerisManager.getInstance());
        assertNull(jgm.getGraph("graph1"));

        final Map<String, Object> map = new HashMap<>();
        map.put(STORAGE_BACKEND.toStringWithoutRoot(), "inmemory");
        map.put(GRAPH_NAME.toStringWithoutRoot(), "graph1");
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
        jgm.putGraph("graph1", graph);
        assertEquals("graph1", ((StandardItineris) ItinerisManager.getInstance().getGraph("graph1")).getGraphName());

        final ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
        mgmt.evictGraphFromCache();
        mgmt.commit();

        // wait for log to be asynchronously pulled
        Thread.sleep(10000);

        assertNull(jgm.getGraph("graph1"));
    }

    @Test
    public void graphClosedWhileItsEvictionWaitsForATransactionIsRemovedFromCache() throws InterruptedException {
        final ItinerisManager jgm = new ItinerisManager(new Settings());
        final Map<String, Object> map = new HashMap<>();
        map.put(STORAGE_BACKEND.toStringWithoutRoot(), "inmemory");
        map.put(GRAPH_NAME.toStringWithoutRoot(), "graph1");
        //The eviction arrives within a tenth of a second
        map.put("log.itineris.read-interval", 100);
        map.put("log.itineris.send-delay", 0);
        final MapConfiguration config = ConfigurationUtil.loadMapConfiguration(map);
        final StandardItineris graph = new StandardItineris(new GraphDatabaseConfigurationBuilder().build(new CommonsConfiguration(config)));
        jgm.putGraph("graph1", graph);
        final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        final Set<Long> before = liveThreadIds(threads);

        try {
            //The acknowledgement of the eviction, which removes the graph from the cache, waits for this transaction
            graph.newTransaction();
            final ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
            mgmt.evictGraphFromCache();
            mgmt.commit();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean acknowledging = false;
            while (!acknowledging && System.nanoTime() - deadline < 0) {
                //The threads without their stack traces, which Thread.getAllStackTraces() would capture at each poll
                acknowledging = Arrays.stream(threads.getThreadInfo(threads.getAllThreadIds(), 0))
                    .anyMatch(t -> t != null && !before.contains(t.getThreadId())
                        && t.getThreadName().startsWith("ManagementLogger-ack-"));
                Thread.sleep(10);
            }
            assertTrue(acknowledging, "no acknowledgement waits for the transaction");
            assertNotNull(jgm.getGraph("graph1"), "removed while the transaction is open");

            //Closing the graph closes the transaction, but drops the acknowledgement: the graph is removed all the same
            graph.close();

            assertNull(jgm.getGraph("graph1"));
        } finally {
            //A failure above would otherwise leave the graph, and its threads, to the tests after this one
            if (graph.isOpen()) {
                graph.close();
            }
        }
    }

    private static Set<Long> liveThreadIds(ThreadMXBean threads) {
        final Set<Long> ids = new HashSet<>();
        for (long id : threads.getAllThreadIds()) {
            ids.add(id);
        }
        return ids;
    }
}
