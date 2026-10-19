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

import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

/**
 * Blueprint's features of a Itineris.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public class ItinerisFeatures implements Graph.Features {

    private final GraphFeatures graphFeatures;
    private final VertexFeatures vertexFeatures;
    private final EdgeFeatures edgeFeatures;

    private final StandardItineris graph;
    private final StoreFeatures storageFeatures;
    /**
     * The transaction schema dependent features (such as {@link VertexFeatures#getCardinality(String)}) are resolved
     * against, or {@code null} if these are the graph-level features which have to open their own transaction.
     */
    private final ItinerisTransaction tx;

    private ItinerisFeatures(StandardItineris graph, StoreFeatures storageFeatures, ItinerisTransaction tx) {
        graphFeatures = new ItinerisGeneralFeatures(storageFeatures.supportsPersistence());
        vertexFeatures = new ItinerisVertexFeatures();
        edgeFeatures = new ItinerisEdgeFeatures();
        this.graph = graph;
        this.storageFeatures = storageFeatures;
        this.tx = tx;
    }

    @Override
    public GraphFeatures graph() {
        return graphFeatures;
    }

    @Override
    public VertexFeatures vertex() {
        return vertexFeatures;
    }

    @Override
    public EdgeFeatures edge() {
        return edgeFeatures;
    }

    @Override
    public String toString() {
        return StringFactory.featureString(this);
    }

    public static ItinerisFeatures getFeatures(StandardItineris graph, StoreFeatures storageFeatures) {
        return new ItinerisFeatures(graph, storageFeatures, null);
    }

    /**
     * Returns a view of these features which resolves schema dependent features, such as the cardinality of a
     * property key, against the given transaction so that schema elements created (but not yet committed) in that
     * transaction are taken into account.
     */
    public ItinerisFeatures forTransaction(ItinerisTransaction tx) {
        return new ItinerisFeatures(graph, storageFeatures, tx);
    }

    private static class ItinerisDataTypeFeatures implements DataTypeFeatures {

        @Override
        public boolean supportsMapValues() {
            return true;
        }

        @Override
        public boolean supportsMixedListValues() {
            return false;
        }

        @Override
        public boolean supportsSerializableValues() {
            return false;
        }

        @Override
        public boolean supportsUniformListValues() {
            return false;
        }
    }

    private static class ItinerisVariableFeatures extends ItinerisDataTypeFeatures implements VariableFeatures { }

    private static class ItinerisGeneralFeatures extends ItinerisDataTypeFeatures implements GraphFeatures {

        private final boolean persists;

        private ItinerisGeneralFeatures(boolean persists) {
            this.persists = persists;
        }

        @Override
        public VariableFeatures variables() {
            return new ItinerisVariableFeatures();
        }

        @Override
        public boolean supportsComputer() {
            return true;
        }

        @Override
        public boolean supportsPersistence() {
            return persists;
        }

        @Override
        public boolean supportsTransactions() {
            return true;
        }

        @Override
        public boolean supportsThreadedTransactions() {
            return true;
        }
    }

    private static class ItinerisVertexPropertyFeatures extends ItinerisDataTypeFeatures implements VertexPropertyFeatures {

        @Override
        public boolean supportsUserSuppliedIds() {
            return false;
        }

        @Override
        public boolean supportsNumericIds() { return false; }

        @Override
        public boolean supportsAnyIds() {
            return false;
        }

        @Override
        public boolean supportsUuidIds() {
            return false;
        }

        @Override
        public boolean supportsNullPropertyValues() {
            return false;
        }
    }

    private static class ItinerisEdgePropertyFeatures extends ItinerisDataTypeFeatures implements EdgePropertyFeatures {

    }

    private class ItinerisVertexFeatures implements VertexFeatures {

        @Override
        public VertexProperty.Cardinality getCardinality(final String key) {
            // Prefer the transaction these features are bound to: it sees property keys which were created in it
            // but are not committed yet and it saves opening a transaction per lookup. TinkerPop resolves the
            // cardinality of every property(key, value) mutation through this method since 3.8.
            final ItinerisTransaction boundTx = ItinerisFeatures.this.tx;
            if (boundTx instanceof StandardItinerisTx && boundTx.isOpen()) {
                return getCardinality((StandardItinerisTx) boundTx, key);
            }
            StandardItinerisTx tx = (StandardItinerisTx)ItinerisFeatures.this.graph.buildTransaction()
                .dirtyVertexSize(0)
                .vertexCacheSize(0)
                .readOnly()
                .start();
            try {
                return getCardinality(tx, key);
            } finally {
                tx.rollback();
            }
        }

        private VertexProperty.Cardinality getCardinality(final StandardItinerisTx tx, final String key) {
            if (!tx.containsPropertyKey(key)) {
                return tx.getConfiguration().getAutoSchemaMaker().defaultPropertyCardinality(key).convert();
            }
            return tx.getPropertyKey(key).cardinality().convert();
        }

        @Override
        public VertexPropertyFeatures properties() {
            return new ItinerisVertexPropertyFeatures();
        }

        @Override
        public boolean supportsNumericIds()
        {
            return true;
        }

        @Override
        public boolean supportsUserSuppliedIds() {
            return ItinerisFeatures.this.graph.getConfiguration().allowVertexIdSetting();
        }

        @Override
        public boolean supportsAnyIds() {
            return false;
        }

        @Override
        public boolean supportsUuidIds() {
            return false;
        }

        @Override
        public boolean supportsStringIds()
        {
            return ItinerisFeatures.this.graph.getConfiguration().allowCustomVertexIdType();
        }

        @Override
        public boolean supportsCustomIds()
        {
            return false;
        }

        @Override
        public boolean supportsNullPropertyValues() {
            return false;
        }
    }

    private static class ItinerisEdgeFeatures implements EdgeFeatures {
        @Override
        public EdgePropertyFeatures properties() {
            return new ItinerisEdgePropertyFeatures();
        }

        @Override
        public boolean supportsCustomIds()
        {
            return true;
        }

        @Override
        public boolean supportsUserSuppliedIds() {
            return false;
        }

        @Override
        public boolean supportsNumericIds() { return false; }

        @Override
        public boolean supportsAnyIds() {
            return false;
        }

        @Override
        public boolean supportsUuidIds() {
            return false;
        }

        @Override
        public boolean supportsStringIds()
        {
            return false;
        }

        @Override
        public boolean supportsNullPropertyValues() {
            return false;
        }
    }

}
