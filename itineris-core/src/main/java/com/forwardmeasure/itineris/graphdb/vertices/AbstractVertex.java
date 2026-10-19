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

package com.forwardmeasure.itineris.graphdb.vertices;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import com.forwardmeasure.itineris.core.InvalidElementException;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.VertexLabel;
import com.forwardmeasure.itineris.graphdb.internal.AbstractElement;
import com.forwardmeasure.itineris.graphdb.internal.ElementLifeCycle;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.query.vertex.BasicVertexCentricQueryUtil;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.util.ElementHelper;

import java.util.Iterator;
import javax.annotation.Nullable;

public abstract class AbstractVertex extends AbstractElement implements InternalVertex, Vertex {

    private final StandardItinerisTx tx;


    protected AbstractVertex(StandardItinerisTx tx, Object id) {
        super(id);
        assert tx != null;
        this.tx = tx;
    }

    @Override
    public final InternalVertex it() {
        if (tx.isOpen())
            return this;

        InternalVertex next = (InternalVertex) tx.getNextTx().getVertex(id());
        if (next == null) throw InvalidElementException.removedException(this);
        else return next;
    }

    @Override
    public final StandardItinerisTx tx() {
        return tx.isOpen() ? tx : tx.getNextTx();
    }

    public final boolean isTxOpen() {
        return tx.isOpen();
    }

    @Override
    public Object getCompareId() {
        if (tx.isPartitionedVertex(this)) return tx.getIdInspector().getCanonicalVertexId(((Number) id()).longValue());
        else return id();
    }

    @Override
    public String toString() {
        return StringFactory.vertexString(this);
    }

    @Override
    public boolean isModified() {
        return ElementLifeCycle.isModified(it().getLifeCycle());
    }

    protected final void verifyAccess() {
        if (isRemoved()) {
            throw InvalidElementException.removedException(this);
        }
    }

	/* ---------------------------------------------------------------
     * Changing Edges
	 * ---------------------------------------------------------------
	 */

    @Override
    public synchronized void remove(Iterable<ItinerisRelation> loadedRelations) {
        verifyAccess();
        for (ItinerisRelation r : loadedRelations) {
            r.remove();
        }
    }

    @Override
    public synchronized void remove() {
        verifyAccess();
        for (ItinerisRelation r : it().query().noPartitionRestriction().all().relations()) {
            r.remove();
        }
    }

	/* ---------------------------------------------------------------
	 * ItinerisRelation Iteration/Access
	 * ---------------------------------------------------------------
	 */

    @Override
    public String label() {
        return vertexLabel().name();
    }

    protected Vertex getVertexLabelInternal() {
        return Iterables.getOnlyElement(BasicVertexCentricQueryUtil.withLabelVertices(tx().query(this)).vertices(),null);
    }

    @Override
    public VertexLabel vertexLabel() {
        return BasicVertexCentricQueryUtil.castToVertexLabel(getVertexLabelInternal());
    }

    @Override
    public VertexCentricQueryBuilder query() {
        verifyAccess();
        return tx().query(this);
    }

    @Override
    public <O> O valueOrNull(PropertyKey key) {
        return (O)property(key.name()).orElse(null);
    }

	/* ---------------------------------------------------------------
	 * Convenience Methods for ItinerisElement Creation
	 * ---------------------------------------------------------------
	 */

    public<V> ItinerisVertexProperty<V> property(final String key, final V value, final Object... keyValues) {
        return property(null, key, value, keyValues);
    }

    @Override
    public <V> ItinerisVertexProperty<V> property(@Nullable final VertexProperty.Cardinality cardinality, final String key, final V value, final Object... keyValues) {
        PropertyKey propertyKey = tx().getOrCreatePropertyKey(key, value, cardinality);
        if (propertyKey == null) {
            return ItinerisVertexProperty.empty();
        }
        VertexProperty.Cardinality vCardinality = cardinality == null ? propertyKey.cardinality().convert() : cardinality;
        if (value == null) {
            if (vCardinality.equals(VertexProperty.Cardinality.single)) {
                // putting null value with SINGLE cardinality is equivalent to removing existing value
                properties(key).forEachRemaining(Property::remove);
            } else {
                // simply ignore this mutation
                assert vCardinality.equals(VertexProperty.Cardinality.list) || vCardinality.equals(VertexProperty.Cardinality.set);
            }
            return ItinerisVertexProperty.empty();
        }
        ItinerisVertexProperty<V> p = tx().addProperty(vCardinality, it(), propertyKey, value);
        ElementHelper.attachProperties(p,keyValues);
        return p;
    }

    @Override
    public ItinerisEdge addEdge(String label, Vertex vertex, Object... keyValues) {
        Preconditions.checkArgument(vertex instanceof ItinerisVertex,"Invalid vertex provided: %s",vertex);
        ItinerisEdge edge = tx().addEdge(it(), (ItinerisVertex) vertex, tx().getOrCreateEdgeLabel(label));
        ElementHelper.attachProperties(edge,keyValues);
        return edge;
    }

    public Iterator<Edge> edges(Direction direction, String... labels) {
        return (Iterator)query().direction(direction).labels(labels).edges().iterator();
    }

    public <V> Iterator<VertexProperty<V>> properties(String... keys) {
        return (Iterator)query().direction(Direction.OUT).keys(keys).properties().iterator();
    }

    public Iterator<Vertex> vertices(final Direction direction, final String... edgeLabels) {
        return (Iterator)query().direction(direction).labels(edgeLabels).vertices().iterator();

    }
}
