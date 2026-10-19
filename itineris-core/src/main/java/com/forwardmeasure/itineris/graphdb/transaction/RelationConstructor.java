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

package com.forwardmeasure.itineris.graphdb.transaction;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.structure.Direction;
import com.forwardmeasure.itineris.core.EdgeLabel;
import com.forwardmeasure.itineris.core.ItinerisLazyRelationConstructor;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.graphdb.database.EdgeSerializer;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.relations.CacheEdge;
import com.forwardmeasure.itineris.graphdb.relations.CacheVertexProperty;
import com.forwardmeasure.itineris.graphdb.relations.RelationCache;
import com.forwardmeasure.itineris.graphdb.types.TypeInspector;
import com.forwardmeasure.itineris.graphdb.types.TypeUtil;

import java.util.Iterator;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class RelationConstructor {

    public static RelationCache readRelationCache(Entry data, StandardItinerisTx tx) {
        return tx.getEdgeSerializer().readRelation(data, false, tx);
    }

    public static InternalRelation readRelation(final InternalVertex vertex,
                                                final Entry data,
                                                final boolean parseHeaderOnly,
                                                final StandardItinerisTx tx) {
        RelationCache relation = tx.getEdgeSerializer().readRelation(data, parseHeaderOnly, tx);
        return readRelation(vertex,relation,data,tx,tx);
    }

    public static Iterable<ItinerisRelation> readRelation(final InternalVertex vertex,
                                                            final Iterable<Entry> data,
                                                            final StandardItinerisTx tx) {
        return () -> new Iterator<ItinerisRelation>() {

            private final Iterator<Entry> iterator = data.iterator();
            private ItinerisRelation current = null;

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ItinerisRelation next() {
                Entry entry = iterator.next();
                if (tx.getConfiguration().isLazyLoadRelations()) {
                    current = ItinerisLazyRelationConstructor.create(entry, vertex, tx);
                } else {
                    current = readRelation(vertex, entry, tx);
                }
                return current;
            }

            @Override
            public void remove() {
                Preconditions.checkNotNull(current);
                current.remove();
            }
        };
    }

    public static InternalRelation readRelation(final InternalVertex vertex, final Entry data, final StandardItinerisTx tx) {
        RelationCache relation = tx.getEdgeSerializer().readRelation(data, true, tx);
        return readRelation(vertex,relation,data,tx,tx);
    }

    public static InternalRelation readRelation(final InternalVertex vertex, final Entry data,
                                                final EdgeSerializer serializer, final TypeInspector types,
                                                final VertexFactory vertexFac) {
        RelationCache relation = serializer.readRelation(data, true, types);
        return readRelation(vertex,relation,data,types,vertexFac);
    }


    private static InternalRelation readRelation(final InternalVertex vertex, final RelationCache relation,
                                         final Entry data, final TypeInspector types, final VertexFactory vertexFac) {
        InternalRelationType type = TypeUtil.getBaseType((InternalRelationType) types.getExistingRelationType(relation.typeId));

        if (type.isPropertyKey()) {
            assert relation.direction == Direction.OUT;
            return new CacheVertexProperty(relation.relationId, (PropertyKey) type, vertex, relation.getValue(), data);
        }

        if (type.isEdgeLabel()) {
            InternalVertex otherVertex = vertexFac.getInternalVertex(relation.getOtherVertexId());
            switch (relation.direction) {
                case IN:
                    return new CacheEdge(relation.relationId, (EdgeLabel) type, otherVertex, vertex, data);

                case OUT:
                    return new CacheEdge(relation.relationId, (EdgeLabel) type, vertex, otherVertex, data);

                default:
                    throw new AssertionError();
            }
        }

        throw new AssertionError();
    }

}
