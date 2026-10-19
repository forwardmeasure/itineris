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

package com.forwardmeasure.itineris.graphdb.inmemory;

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RemovedVertexTrackingTest {

    private Itineris graph;

    @BeforeEach
    public void open() {
        graph = ItinerisFactory.build().set("storage.backend", "inmemory").open();
    }

    @AfterEach
    public void close() {
        if (graph != null) graph.close();
    }

    @Test
    public void removedVertexIsTracked() {
        Object id;
        try (com.forwardmeasure.itineris.core.ItinerisTransaction setup = graph.newTransaction()) {
            ItinerisVertex v = setup.addVertex();
            setup.commit();
            id = v.id();
        }
        StandardItinerisTx tx = (StandardItinerisTx) graph.newTransaction();
        try {
            assertFalse(tx.isVertexFullyRemoved(id));
            ItinerisVertex v = (ItinerisVertex) tx.vertices(id).next();
            v.remove();
            assertTrue(tx.isVertexFullyRemoved(id));
        } finally {
            tx.rollback();
        }
    }

    @Test
    public void removeViaStaleCrossTransactionReferenceDoesNotThrow() {
        // Obtain a vertex in one (thread-bound) transaction, commit it -- closing that transaction --
        // then remove the now-stale reference. remove() must resolve it() to the new transaction
        // without throwing "was removed". Regression test: recordRemovedVertex must not re-resolve
        // it() after the lifecycle has been flipped to REMOVED.
        final ItinerisVertex v = graph.addVertex();
        final Object id = v.id();
        graph.tx().commit();

        v.remove();
        graph.tx().commit();

        assertFalse(graph.traversal().V(id).hasNext());
    }
}
