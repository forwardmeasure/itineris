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

package com.forwardmeasure.itineris.graphdb.transaction;

import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ReadOnlyTransactionException;
import com.forwardmeasure.itineris.diskstorage.BackendTransaction;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardTransactionBuilderTest {

    private StandardItineris graph;

    @BeforeEach
    void setUp() {
        graph = (StandardItineris) ItinerisFactory.open("inmemory");
    }

    @Test
    public void testPropertyPrefetching() {
        boolean graphWideEnabled = graph.getConfiguration().hasPropertyPrefetching();
        assertEquals(graphWideEnabled, ((StandardItinerisTx) graph.newTransaction()).getConfiguration().hasPropertyPrefetching());

        StandardItinerisTx tx = (StandardItinerisTx) graph.buildTransaction().propertyPrefetching(true).start();
        assertTrue(tx.getConfiguration().hasPropertyPrefetching());

        tx = (StandardItinerisTx) graph.buildTransaction().propertyPrefetching(false).start();
        assertFalse(tx.getConfiguration().hasPropertyPrefetching());
    }

    @Test
    public void testMultiQuery() {
        boolean graphWideEnabled = graph.getConfiguration().useMultiQuery();
        assertEquals(graphWideEnabled, ((StandardItinerisTx) graph.newTransaction()).getConfiguration().useMultiQuery());

        StandardItinerisTx tx = (StandardItinerisTx) graph.buildTransaction().multiQuery(true).start();
        assertTrue(tx.getConfiguration().useMultiQuery());

        tx = (StandardItinerisTx) graph.buildTransaction().multiQuery(false).start();
        assertFalse(tx.getConfiguration().useMultiQuery());
    }

    @Test
    public void testBatchLoading() {
        boolean graphWideEnabled = graph.getConfiguration().isBatchLoading();
        assertEquals(graphWideEnabled, ((StandardItinerisTx) graph.newTransaction()).getConfiguration().hasEnabledBatchLoading());

        StandardItinerisTx tx = (StandardItinerisTx) graph.buildTransaction().enableBatchLoading().start();
        assertTrue(tx.getConfiguration().hasEnabledBatchLoading());

        tx = (StandardItinerisTx) graph.buildTransaction().disableBatchLoading().start();
        assertFalse(tx.getConfiguration().hasEnabledBatchLoading());
    }

    @Test
    public void testReadOnly() {
        StandardItinerisTx tx = (StandardItinerisTx) graph.buildTransaction().readOnly().start();
        assertTrue(tx.getConfiguration().isReadOnly());

        ReadOnlyTransactionException ex = assertThrows(ReadOnlyTransactionException.class, () -> tx.addVertex());
        assertEquals("Cannot create new entities in read-only transaction", ex.getMessage());
    }

    @Test
    public void testConsistencyChecks() {
        StandardItinerisTx tx = (StandardItinerisTx) graph.buildTransaction().consistencyChecks(true).start();
        assertTrue(tx.getConfiguration().hasVerifyUniqueness());

        tx = (StandardItinerisTx) graph.buildTransaction().consistencyChecks(false).start();
        assertFalse(tx.getConfiguration().hasVerifyUniqueness());
    }

    @Test
    public void testSkipDBCacheRead() {
        StandardItinerisTx tx = (StandardItinerisTx) graph.newTransaction();
        BackendTransaction backendTransaction = tx.getTxHandle();
        assertFalse(tx.getConfiguration().isSkipDBCacheRead());
        assertTrue(backendTransaction.isCacheEnabled());
        tx.rollback();

        tx = (StandardItinerisTx) graph.buildTransaction().skipDBCacheRead().start();
        backendTransaction = tx.getTxHandle();
        assertTrue(tx.getConfiguration().isSkipDBCacheRead());
        assertFalse(backendTransaction.isCacheEnabled());
        tx.rollback();
    }

}
