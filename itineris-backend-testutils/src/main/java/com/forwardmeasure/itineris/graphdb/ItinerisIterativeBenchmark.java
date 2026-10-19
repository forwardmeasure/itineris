// Copyright 2017 Itineris Authors
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

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterators;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import com.forwardmeasure.itineris.TestCategory;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStore;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyIterator;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;
import com.forwardmeasure.itineris.diskstorage.util.BufferUtil;
import com.forwardmeasure.itineris.diskstorage.util.RecordIterator;
import com.forwardmeasure.itineris.diskstorage.util.StandardBaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProviders;
import com.forwardmeasure.itineris.graphdb.types.StandardEdgeLabelMaker;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A benchmark to test the performance of sequential row retrieval from the
 * underlying KeyColumnValueStore which is the basic operation underlying the
 * Fulgora OLAP component of Itineris.
 * <p>
 * Hence, this is effectively a benchmark for {@link com.forwardmeasure.itineris.olap.OLAPTest}
 * or at least the primitive backend operations used therein.
 * <p>
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
@Tag(TestCategory.PERFORMANCE_TESTS)
public abstract class ItinerisIterativeBenchmark extends ItinerisBaseTest {

    private static final Random random = new Random();

    public abstract KeyColumnValueStoreManager openStorageManager() throws BackendException;

    @RepeatedTest(10)
    public void testDataSequential() throws Exception {
        loadData(200000,2);
        close();
        KeyColumnValueStoreManager manager = openStorageManager();
        KeyColumnValueStore store = manager.openDatabase(Backend.EDGESTORE_NAME);
        SliceQuery query = new SliceQuery(BufferUtil.zeroBuffer(8),BufferUtil.oneBuffer(8));
        query.setLimit(2);
        Stopwatch watch = Stopwatch.createStarted();
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(TimestampProviders.MILLI));
        KeyIterator iterator = store.getKeys(query,txh);
        int numV = 0;
        while(iterator.hasNext()) {
            iterator.next();
            RecordIterator<Entry> entries = iterator.getEntries();
            assertEquals(2, Iterators.size(entries));
            numV++;
        }
        iterator.close();
        txh.commit();
        System.out.println("Time taken: " + watch.elapsed(TimeUnit.MILLISECONDS));
        System.out.println("Num Vertices: " + numV);
        store.close();
        manager.close();

    }

    @RepeatedTest(10)
    public void testLoadData() throws Exception {
        loadData(100000,2);
    }

    public void loadData(final int numVertices, final int numThreads) throws Exception {
        makeKey("w",Integer.class);
        PropertyKey time = makeKey("t",Long.class);
        ((StandardEdgeLabelMaker)mgmt.makeEdgeLabel("l")).sortKey(time).make();
        finishSchema();

        final int maxQueue = 1000;
        final int verticesPerTask = 1000;
        final int maxWeight = 10;
        final int maxTime = 10000;
        final BlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(maxQueue);
        ExecutorService exe = Executors.newFixedThreadPool(numThreads);
        for (int i=0;i<numVertices/verticesPerTask;i++) {
            while (tasks.size()>=maxQueue) Thread.sleep(maxQueue);
            Preconditions.checkState(tasks.size()<maxQueue);
            exe.submit(() -> {
                final ItinerisTransaction tx = graph.newTransaction();
                final ItinerisVertex[] vs = new ItinerisVertex[verticesPerTask];
                for (int j=0;j<verticesPerTask;j++) {
                    vs[j]=tx.addVertex();
                    vs[j].property(VertexProperty.Cardinality.single, "w",  random.nextInt(maxWeight));
                }
                for (int j=0;j<verticesPerTask*10;j++) {
                    final ItinerisEdge e = vs[random.nextInt(verticesPerTask)]
                        .addEdge("l",vs[random.nextInt(verticesPerTask)]);
                    e.property("t",random.nextInt(maxTime));
                }
                System.out.print(".");
                tx.commit();
            });
        }
        exe.shutdown();
        exe.awaitTermination(numVertices/1000, TimeUnit.SECONDS);
        if (!exe.isTerminated()) System.err.println("Could not load data in time");
        System.out.println("Loaded "+numVertices+"vertices");
    }
}
