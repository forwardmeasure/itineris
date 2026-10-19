// Copyright 2026 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.inmemory;

import org.apache.commons.io.FileUtils;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.util.stats.MetricManager.GraphReporter;
import com.forwardmeasure.itineris.util.stats.MetricManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.LOCK_LOCAL_MEDIATOR_GROUP;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.PARALLEL_BACKEND_EXECUTOR_SERVICE_CLASS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.PARALLEL_BACKEND_EXECUTOR_SERVICE_CORE_POOL_SIZE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.UNIQUE_INSTANCE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The threads which a graph starts end when it is closed or dropped, and the Metrics reporters it starts stop once
 * no open graph uses them.
 */
public class InMemoryGraphResourcesTest {

    private static final long WAIT_MS = TimeUnit.SECONDS.toMillis(10);
    //The thread of the Metrics Slf4jReporter
    private static final String SLF4J_REPORTER_THREAD_PREFIX = "metrics-logger-reporter-";

    private Set<Thread> threadsBefore;

    @BeforeEach
    public void setUp() {
        threadsBefore = Thread.getAllStackTraces().keySet();
    }

    @AfterEach
    public void removeReporters() {
        MetricManager.INSTANCE.removeAllReporters();
    }

    private List<Thread> newThreadsNamed(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.getName().startsWith(prefix) && !threadsBefore.contains(t))
            .collect(Collectors.toList());
    }

    //The threads of the prefix started since the test began, once there is one: a reporter's start doesn't wait for
    //its thread
    private List<Thread> awaitNewThreadsNamed(String prefix) throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
        List<Thread> threads = newThreadsNamed(prefix);
        while (threads.isEmpty() && System.nanoTime() - deadline < 0) {
            Thread.sleep(10);
            threads = newThreadsNamed(prefix);
        }
        return threads;
    }

    private static void assertEnd(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            thread.join(WAIT_MS);
            assertFalse(thread.isAlive(), thread + " is still alive");
        }
    }

    private static boolean slf4jReporterRuns() {
        return MetricManager.INSTANCE.isRunning(GraphReporter.SLF4J);
    }

    @Test
    public void droppingAGraphEndsTheThreadsOfTheBackendWhichClearsIt() throws BackendException, InterruptedException {
        //A cached pool starts its core threads at once, so the backend which the drop opens to clear the storage
        //starts them as well
        final Itineris graph = ItinerisFactory.build()
            .set("storage.backend", "inmemory")
            .set("storage.parallel-backend-executor-service.class", "cached")
            .set("storage.parallel-backend-executor-service.core-pool-size", 2)
            .open();
        assertEquals(2, newThreadsNamed("Backend[").size());

        //The drop opens a backend of its own to clear the storage, whose threads can't be seen before it: that it
        //started some shows in the JVM's count of started threads, and none of the backends' threads may stay alive
        final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        final long startedBefore = threads.getTotalStartedThreadCount();
        ItinerisFactory.drop(graph);

        assertTrue(threads.getTotalStartedThreadCount() - startedBefore >= 2, "the drop started no backend threads");
        assertEnd(newThreadsNamed("Backend["));
    }

    @Test
    public void aBackendWhichFailsToClearTheStorageEndsTheThreadsOfItsExecutor() throws InterruptedException {
        final ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration()
            .set(STORAGE_BACKEND, "inmemory")
            .set(PARALLEL_BACKEND_EXECUTOR_SERVICE_CLASS, "cached")
            .set(PARALLEL_BACKEND_EXECUTOR_SERVICE_CORE_POOL_SIZE, 2)
            .set(UNIQUE_INSTANCE_ID, "inst")
            .set(LOCK_LOCAL_MEDIATOR_GROUP, "tmp");
        //It opens its stores in initialize(), so clearing the storage of a backend which never was fails
        final Backend backend = new Backend(config);
        assertEquals(2, newThreadsNamed("Backend[").size());

        assertThrows(Exception.class, backend::clearStorage);

        assertEnd(newThreadsNamed("Backend["));
    }

    private static ItinerisFactory.Builder graphWithSlf4jReporter() {
        //The reporter reports an hour after it starts, and once more when it stops
        return ItinerisFactory.build()
            .set("storage.backend", "inmemory")
            .set("metrics.enabled", true)
            .set("metrics.slf4j.interval", TimeUnit.HOURS.toMillis(1));
    }

    @Test
    public void aMetricsReporterStopsWhenTheLastGraphUsingItCloses() throws InterruptedException {
        final Itineris first = graphWithSlf4jReporter().open();
        final Itineris second;
        try {
            second = graphWithSlf4jReporter().open();
        } catch (RuntimeException e) {
            first.close();
            throw e;
        }
        try {
            final List<Thread> reporterThreads = awaitNewThreadsNamed(SLF4J_REPORTER_THREAD_PREFIX);
            assertEquals(1, reporterThreads.size(), "reporter threads: " + reporterThreads);
            final Thread reporterThread = reporterThreads.get(0);

            first.close();
            reporterThread.join(1000);
            assertTrue(reporterThread.isAlive(), "the reporter stopped while the second graph uses it");

            second.close();
            assertEnd(reporterThreads);
        } finally {
            if (first.isOpen()) {
                first.close();
            }
            if (second.isOpen()) {
                second.close();
            }
        }
    }

    @Test
    public void aGraphOpenedAfterDroppingItsStorageHasAMetricsReporter() {
        //The graph which the drop opens and closes, on the same configuration, stops its reporter as it closes; the one
        //which opens after it starts one anew
        final Itineris graph = graphWithSlf4jReporter().set("schema.init.drop-before-startup", true).open();
        try {
            assertTrue(slf4jReporterRuns());
        } finally {
            graph.close();
        }
        assertFalse(slf4jReporterRuns());
    }

    @Test
    public void aGraphWhoseMetricsReporterFailsToStartClosesWhatItOpened() throws Exception {
        //Starting a CSV reporter without an interval fails, as the last step of opening the graph, after its backend,
        //with the cached executor's threads, has opened
        final File directory = Files.createTempDirectory("csv-metrics").toFile();
        try {
            final ItinerisFactory.Builder graph = ItinerisFactory.build()
                .set("storage.backend", "inmemory")
                .set("storage.parallel-backend-executor-service.class", "cached")
                .set("storage.parallel-backend-executor-service.core-pool-size", 2)
                .set("metrics.enabled", true)
                .set("metrics.csv.directory", directory.getAbsolutePath());

            assertThrows(RuntimeException.class, graph::open);

            assertEnd(newThreadsNamed("Backend["));
            assertFalse(slf4jReporterRuns());
        } finally {
            FileUtils.deleteQuietly(directory);
        }
    }

    @Test
    public void aGraphWhichFailsToOpenLeavesNoMetricsReporterRunning() {
        //Fails once the configuration, which used to start the reporters, is built, and before a graph is constructed
        assertThrows(RuntimeException.class,
            () -> graphWithSlf4jReporter().set("schema.init.strategy", "com.forwardmeasure.itineris.NoSuchStrategy").open());

        assertFalse(slf4jReporterRuns());
    }

    @Test
    public void aGraphWhoseConstructionFailsLeavesNoMetricsReporterRunning() {
        //Fails in the graph's constructor, which opens the backend with its index providers
        assertThrows(RuntimeException.class,
            () -> graphWithSlf4jReporter().set("index.search.backend", "com.forwardmeasure.itineris.NoSuchIndexProvider").open());

        assertFalse(slf4jReporterRuns());
    }
}
