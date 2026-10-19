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

package com.forwardmeasure.itineris.graphdb.berkeleyje;

import com.google.common.base.Preconditions;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.dbi.DbEnvPool;
import com.forwardmeasure.itineris.BerkeleyStorageSetup;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.berkeleyje.BerkeleyJEStoreManager;
import com.forwardmeasure.itineris.diskstorage.berkeleyje.BerkeleyJEStoreManager.IsolationLevel;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigElement;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigOption;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.keyvalue.OrderedKeyValueStoreManagerAdapter;
import com.forwardmeasure.itineris.diskstorage.locking.PermanentLockingException;
import com.forwardmeasure.itineris.graphdb.ItinerisTest;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_SETTING_VERTEX_ID;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ALLOW_CUSTOM_VERTEX_ID_TYPES;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_DIRECTORY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BerkeleyGraphTest extends ItinerisTest {

    private static final Logger log =
            LoggerFactory.getLogger(BerkeleyGraphTest.class);

    public EnvironmentConfig getCurrentEnvironmentConfig() {
        BerkeleyJEStoreManager storeManager = (BerkeleyJEStoreManager) ((OrderedKeyValueStoreManagerAdapter) graph.getBackend().getStoreManager()).getManager();
        Environment environment = storeManager.getEnvironment();
        return environment.getConfig();
    }

    @Override
    public WriteConfiguration getConfiguration() {
        ModifiableConfiguration modifiableConfiguration = BerkeleyStorageSetup.getBerkeleyJEConfiguration();
        String methodName = testInfo.getTestMethod().toString();
        if (methodName.equals("testConsistencyEnforcement")) {
            IsolationLevel iso = IsolationLevel.SERIALIZABLE;
            log.debug("Forcing isolation level {} for test method {}", iso, methodName);
            modifiableConfiguration.set(BerkeleyJEStoreManager.ISOLATION_LEVEL, iso.toString());
        } else {
            IsolationLevel iso = null;
            if (modifiableConfiguration.has(BerkeleyJEStoreManager.ISOLATION_LEVEL)) {
                iso = ConfigOption.getEnumValue(modifiableConfiguration.get(BerkeleyJEStoreManager.ISOLATION_LEVEL),IsolationLevel.class);
            }
            log.debug("Using isolation level {} (null means adapter default) for test method {}", iso, methodName);
        }
        return modifiableConfiguration.getConfiguration();
    }

    @Override
    @Test
    public void testClearStorage() throws Exception {
        tearDown();
        config.set(ConfigElement.getPath(GraphDatabaseConfiguration.DROP_ON_CLEAR), true);
        Backend backend = getBackend(config, false);
        assertTrue(backend.getStoreManager().exists(), "graph should exist before clearing storage");
        clearGraph(config);
        backend.close();
        backend = getBackend(config, false);
        assertFalse(backend.getStoreManager().exists(), "graph should not exist after clearing storage");
        backend.close();
    }

    @Test
    public void testVertexCentricQuerySmall() {
        testVertexCentricQuery(1450 /*noVertices*/);
    }

    @Override
    @Test
    @Disabled("#4153: Tests fails with NPE for some reason")
    public void testConsistencyEnforcement() {
        // Check that getConfiguration() explicitly set serializable isolation
        // This could be enforced with a JUnit assertion instead of a Precondition,
        // but a failure here indicates a problem in the test itself rather than the
        // system-under-test, so a Precondition seems more appropriate
        IsolationLevel effective = ConfigOption.getEnumValue(config.get(ConfigElement.getPath(BerkeleyJEStoreManager.ISOLATION_LEVEL), String.class),IsolationLevel.class);
        Preconditions.checkState(IsolationLevel.SERIALIZABLE.equals(effective));
        super.testConsistencyEnforcement();
    }

    @Override
    @Test
    @Disabled("BerkeleyJE support only hour discrete TTL, we try speed up internal clock but tests so flaky. See BerkeleyStorageSetup")
    public void testEdgeTTLTiming() {
    }

    @Override
    @Test
    @Disabled
    public void testEdgeTTLWithTransactions() {
    }

    @Override
    @Test
    @Disabled
    public void testUnsettingTTL() {
    }

    @Override
    @Test
    @Disabled
    public void testVertexTTLWithCompositeIndex() {
    }

    @Disabled("Unable to run on GitHub Actions.")
    @Test
    public void testIDBlockAllocationTimeout() throws BackendException {
        config.set("ids.authority.wait-time", Duration.of(0L, ChronoUnit.NANOS));
        config.set("ids.renew-timeout", Duration.of(1L, ChronoUnit.MILLIS));
        close();
        ItinerisFactory.drop(graph);
        open(config);
        assertThrows(ItinerisException.class, () -> graph.addVertex());

        assertTrue(graph.isOpen());

        close(); // must be able to close cleanly

        // Must be able to reopen
        open(config);

        assertEquals(0L, (long)graph.traversal().V().count().next());
    }

    /**
     * Graph instances which open the same directory share its BerkeleyJE environment, and with it the locks of its
     * keys.
     */
    @Test
    public void testGraphInstancesOfOneDirectoryShareTheLocksOfItsKeys() throws Exception {
        makeLockedUniqueName();
        final Itineris other = ItinerisFactory.open(config.copy());
        try {
            //A transaction of one instance fails to commit a value which a transaction of the other committed
            final ItinerisTransaction late = other.newTransaction();
            late.addVertex("name", "first");
            tx.addVertex("name", "first");
            newTx();
            final ItinerisException failure = assertThrows(ItinerisException.class, late::commit);
            assertTrue(failure.isCausedBy(PermanentLockingException.class), failure::toString);

            //Of the transactions of both instances which commit the same value at once, one adds it
            assertEquals(1, addVerticesAtOnce(Arrays.asList(graph, other), 8, "same"));
            newTx();
            assertEquals(1, verticesNamed(tx, "same").size());
        } finally {
            other.close();
        }
    }

    @Override
    public void clopenForStaleIndex(){
        // We set LOCK_MODE to READ_UNCOMMITTED here to mitigate an issue with deadlock problem described in
        // https://github.com/JanusGraph/janusgraph/issues/1623
        clopen(option(BerkeleyJEStoreManager.LOCK_MODE), LockMode.READ_UNCOMMITTED.toString());
    }

    @Test
    public void testCannotUseCustomStringId() {
        ItinerisException ex = assertThrows(ItinerisException.class,
            () -> clopen(option(ALLOW_SETTING_VERTEX_ID), true, option(ALLOW_CUSTOM_VERTEX_ID_TYPES), true));
        assertEquals("allow-custom-vid-types is not supported for OrderedKeyValueStore", ex.getMessage());
        //The failed open closed the environment it opened. One left open would keep its files, which the next test
        //deletes, so BerkeleyJE would fail that test once it noticed.
        assertFalse(DbEnvPool.getInstance().isOpen(new File(readConfig.get(STORAGE_DIRECTORY))));
    }

    @Test
    public void testExposedConfigurations() throws BackendException {
        clopen(option(BerkeleyJEStoreManager.EXT_LOCK_TIMEOUT), "4321 ms");
        assertEquals(4321, getCurrentEnvironmentConfig().getLockTimeout(TimeUnit.MILLISECONDS));
        close();
        WriteConfiguration configuration = getConfiguration();
        clearGraph(configuration);
        configuration.set(BerkeleyJEStoreManager.BERKELEY_EXTRAS_NS.toStringWithoutRoot()+"."+EnvironmentConfig.LOCK_TIMEOUT, "12345 ms");
        open(configuration);
        assertEquals(12345, getCurrentEnvironmentConfig().getLockTimeout(TimeUnit.MILLISECONDS));
        close();
        clearGraph(configuration);
        configuration.set(BerkeleyJEStoreManager.BERKELEY_EXTRAS_NS.toStringWithoutRoot()+"."+EnvironmentConfig.ENV_IS_TRANSACTIONAL, "true");
        open(configuration);
        assertTrue(getCurrentEnvironmentConfig().getTransactional());
        close();
        clearGraph(configuration);
    }

}
