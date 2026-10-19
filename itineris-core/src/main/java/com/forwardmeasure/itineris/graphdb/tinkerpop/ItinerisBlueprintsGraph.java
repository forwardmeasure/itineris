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

package com.forwardmeasure.itineris.graphdb.tinkerpop;

import com.google.common.base.Preconditions;
import org.apache.commons.configuration2.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.GraphComputer;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Transaction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONVersion;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoVersion;
import org.apache.tinkerpop.gremlin.structure.util.AbstractThreadLocalTransaction;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import com.forwardmeasure.itineris.core.EdgeLabel;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisIndexQuery;
import com.forwardmeasure.itineris.core.ItinerisMultiVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisQuery;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.MixedIndexAggQuery;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.VertexLabel;
import com.forwardmeasure.itineris.core.schema.EdgeLabelMaker;
import com.forwardmeasure.itineris.core.schema.PropertyKeyMaker;
import com.forwardmeasure.itineris.core.schema.VertexLabelMaker;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.olap.computer.FulgoraGraphComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Blueprints specific implementation for {@link Itineris}.
 * Handles thread-bound transactions.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public abstract class ItinerisBlueprintsGraph implements Itineris {

    private static final Logger log =
            LoggerFactory.getLogger(ItinerisBlueprintsGraph.class);




    // ########## TRANSACTION HANDLING ###########################

    final GraphTransaction tinkerpopTxContainer = new GraphTransaction();

    private ThreadLocal<ItinerisBlueprintsTransaction> txs = ThreadLocal.withInitial(() -> null);

    public abstract ItinerisTransaction newThreadBoundTransaction();

    private ItinerisBlueprintsTransaction getAutoStartTx() {
        if (txs == null) throw new IllegalStateException("Graph has been closed");
        tinkerpopTxContainer.readWrite();

        ItinerisBlueprintsTransaction tx = txs.get();
        Preconditions.checkNotNull(tx,"Invalid read-write behavior configured: " +
                "Should either open transaction or throw exception.");
        return tx;
    }

    private void startNewTx() {
        ItinerisBlueprintsTransaction tx = txs.get();
        if (tx!=null && tx.isOpen()) throw Transaction.Exceptions.transactionAlreadyOpen();
        tx = (ItinerisBlueprintsTransaction) newThreadBoundTransaction();
        txs.set(tx);
        log.debug("Created new thread-bound transaction {}", tx);
    }

    public ItinerisTransaction getCurrentThreadTx() {
        return getAutoStartTx();
    }


    @Override
    public synchronized void close() {
        // If user closes the graph before committing/rolling back thread-bound transactions, there could be a leakage.
        // In case that happens, here we at least remove the ThreadLocal object belonging to the current thread.
        txs.remove();
        txs = null;
    }

    @Override
    public Transaction tx() {
        return tinkerpopTxContainer;
    }

    @Override
    public String toString() {
        GraphDatabaseConfiguration config = ((StandardItineris) this).getConfiguration();
        return StringFactory.graphString(this,config.getBackendDescription());
    }

    @Override
    public Variables variables() {
        return new ItinerisVariables(((StandardItineris)this).getBackend().getUserConfiguration());
    }

    @Override
    public Configuration configuration() {
        GraphDatabaseConfiguration config = ((StandardItineris) this).getConfiguration();
        return config.getConfigurationAtOpen();
    }

    @Override
    public <I extends Io> I io(final Io.Builder<I> builder) {
        if (builder.requiresVersion(GryoVersion.V1_0) || builder.requiresVersion(GraphSONVersion.V1_0)) {
            return (I) builder.graph(this).onMapper(mapper -> {
                mapper.addRegistry(ItinerisIoRegistryV1d0.instance());
                if (mapper instanceof GraphSONMapper.Builder) {
                    // GraphSON 1.0 embedded types only deserialize explicitly allowed @class type ids (TinkerPop 3.8.2+)
                    ItinerisIoRegistryV1d0.allowGraphSONTypeIds((GraphSONMapper.Builder) mapper);
                }
            }).create();
        } else if (builder.requiresVersion(GraphSONVersion.V2_0)) {
            return (I) builder.graph(this).onMapper(mapper ->  mapper.addRegistry(ItinerisIoRegistry.instance())).create();
        } else {
            return (I) builder.graph(this).onMapper(mapper ->  mapper.addRegistry(ItinerisIoRegistry.instance())).create();
        }
    }

    // ########## TRANSACTIONAL FORWARDING ###########################

    @Override
    public ItinerisVertex addVertex(Object... keyValues) {
        return getAutoStartTx().addVertex(keyValues);
    }

    @Override
    public Iterator<Vertex> vertices(Object... vertexIds) {
        return getAutoStartTx().vertices(vertexIds);
    }

    @Override
    public Iterator<Edge> edges(Object... edgeIds) {
        return getAutoStartTx().edges(edgeIds);
    }

    @Override
    public <C extends GraphComputer> C compute(Class<C> graphComputerClass) throws IllegalArgumentException {
        if (!graphComputerClass.equals(FulgoraGraphComputer.class)) {
            throw Graph.Exceptions.graphDoesNotSupportProvidedGraphComputer(graphComputerClass);
        } else {
            return (C)compute();
        }
    }

    @Override
    public FulgoraGraphComputer compute() throws IllegalArgumentException {
        StandardItineris graph = (StandardItineris)this;
        return new FulgoraGraphComputer(graph,graph.getConfiguration().getConfiguration());
    }

    @Override
    public ItinerisVertex addVertex(String vertexLabel) {
        return getAutoStartTx().addVertex(vertexLabel);
    }

    @Override
    public ItinerisQuery<? extends ItinerisQuery> query() {
        return getAutoStartTx().query();
    }
    
    @Override
    public MixedIndexAggQuery mixedIndexAggQuery() {
        return getAutoStartTx().mixedIndexAggQuery();
    }

    @Override
    public ItinerisIndexQuery indexQuery(String indexName, String query) {
        return getAutoStartTx().indexQuery(indexName,query);
    }

    @Override
    public ItinerisMultiVertexQuery multiQuery(ItinerisVertex... vertices) {
        return getAutoStartTx().multiQuery(vertices);
    }

    @Override
    public ItinerisMultiVertexQuery multiQuery(Collection<ItinerisVertex> vertices) {
        return getAutoStartTx().multiQuery(vertices);
    }


    //Schema

    @Override
    public PropertyKeyMaker makePropertyKey(String name) {
        return getAutoStartTx().makePropertyKey(name);
    }

    @Override
    public EdgeLabelMaker makeEdgeLabel(String name) {
        return getAutoStartTx().makeEdgeLabel(name);
    }

    @Override
    public VertexLabelMaker makeVertexLabel(String name) {
        return getAutoStartTx().makeVertexLabel(name);
    }

    @Override
    public VertexLabel addProperties(VertexLabel vertexLabel, PropertyKey... keys) {
        return getAutoStartTx().addProperties(vertexLabel, keys);
    }

    @Override
    public EdgeLabel addProperties(EdgeLabel edgeLabel, PropertyKey... keys) {
        return getAutoStartTx().addProperties(edgeLabel, keys);
    }

    @Override
    public EdgeLabel addConnection(EdgeLabel edgeLabel, VertexLabel outVLabel, VertexLabel inVLabel) {
        return getAutoStartTx().addConnection(edgeLabel, outVLabel, inVLabel);
    }

    @Override
    public boolean containsPropertyKey(String name) {
        return getAutoStartTx().containsPropertyKey(name);
    }

    @Override
    public PropertyKey getOrCreatePropertyKey(String name) {
        return getAutoStartTx().getOrCreatePropertyKey(name);
    }

    @Override
    public PropertyKey getPropertyKey(String name) {
        return getAutoStartTx().getPropertyKey(name);
    }

    @Override
    public boolean containsEdgeLabel(String name) {
        return getAutoStartTx().containsEdgeLabel(name);
    }

    @Override
    public EdgeLabel getOrCreateEdgeLabel(String name) {
        return getAutoStartTx().getOrCreateEdgeLabel(name);
    }

    @Override
    public EdgeLabel getEdgeLabel(String name) {
        return getAutoStartTx().getEdgeLabel(name);
    }

    @Override
    public boolean containsRelationType(String name) {
        return getAutoStartTx().containsRelationType(name);
    }

    @Override
    public RelationType getRelationType(String name) {
        return getAutoStartTx().getRelationType(name);
    }

    @Override
    public boolean containsVertexLabel(String name) {
        return getAutoStartTx().containsVertexLabel(name);
    }

    @Override
    public VertexLabel getVertexLabel(String name) {
        return getAutoStartTx().getVertexLabel(name);
    }

    @Override
    public VertexLabel getOrCreateVertexLabel(String name) {
        return getAutoStartTx().getOrCreateVertexLabel(name);
    }



    class GraphTransaction extends AbstractThreadLocalTransaction {

        public GraphTransaction() {
            super(ItinerisBlueprintsGraph.this);
        }

        @Override
        public void commit() {
            try {
                super.commit();
            } finally {
                doClose();
            }
        }

        @Override
        public void rollback() {
            try {
                super.rollback();
            } finally {
                doClose();
            }
        }

        @Override
        public void doOpen() {
            startNewTx();
        }

        @Override
        public void doCommit() {
            getAutoStartTx().commit();
        }

        @Override
        public void doRollback() {
            getAutoStartTx().rollback();
        }

        @Override
        public ItinerisTransaction createThreadedTx() {
            return newTransaction();
        }

        @Override
        public boolean isOpen() {
            if (null == txs) {
                // Graph has been closed
                return false;
            }
            ItinerisBlueprintsTransaction tx = txs.get();
            return tx!=null && tx.isOpen();
        }

        @Override
        protected void doClose() {
            // Snapshot the shared txs reference first: close() may null it out concurrently on another
            // thread. Capturing it before the calls below keeps the race window minimal so this thread
            // still removes its own ThreadLocal entry rather than relying on delayed GC-based cleanup.
            ThreadLocal<ItinerisBlueprintsTransaction> localTxs = txs;
            super.doClose();
            transactionListeners.remove();
            if (localTxs != null) localTxs.remove();
        }

        @Override
        public Transaction onReadWrite(Consumer<Transaction> transactionConsumer) {
            Preconditions.checkArgument(transactionConsumer instanceof READ_WRITE_BEHAVIOR,
                    "Only READ_WRITE_BEHAVIOR instances are accepted argument, got: %s", transactionConsumer);
            return super.onReadWrite(transactionConsumer);
        }

        @Override
        public Transaction onClose(Consumer<Transaction> transactionConsumer) {
            Preconditions.checkArgument(transactionConsumer instanceof CLOSE_BEHAVIOR,
                    "Only CLOSE_BEHAVIOR instances are accepted argument, got: %s", transactionConsumer);
            return super.onClose(transactionConsumer);
        }
    }

}
