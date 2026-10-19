// Copyright 2019 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.relations;

import org.apache.tinkerpop.gremlin.structure.Direction;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.schema.ConsistencyModifier;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.system.ImplicitKey;

public class RelationIdentifierUtils {
    public static RelationIdentifier get(InternalRelation r, long relationId) {
        if (relationId > 0) {
            RelationIdentifier rId = new RelationIdentifier(r.getVertex(0).id(),
                r.getType().longId(),
                relationId, (r.isEdge() ? r.getVertex(1).id() : null));
            return rId;
        } else return null;
    }

    protected static ItinerisRelation findRelation(RelationIdentifier rId, ItinerisTransaction tx) {
        ItinerisVertex v = ((StandardItinerisTx)tx).getInternalVertex(rId.getOutVertexId());
        if (v == null || v.isRemoved()) return null;

        ItinerisVertex typeVertex = tx.getVertex(rId.getTypeId());
        if (typeVertex == null) return null;

        if (!(typeVertex instanceof RelationType))
            throw new IllegalArgumentException("Invalid RelationIdentifier: typeID does not reference a type");

        Iterable<? extends ItinerisRelation> relations = getItinerisRelations(rId, tx, v, (RelationType) typeVertex);

        for (ItinerisRelation r : relations) {
            //Find current or previous relation
            if (r.longId() == rId.getRelationId() ||
                ((r instanceof StandardRelation) && ((StandardRelation) r).getPreviousID() == rId.getRelationId())) return r;
        }
        return null;
    }

    private static Iterable<? extends ItinerisRelation> getItinerisRelations(RelationIdentifier rId, ItinerisTransaction tx, ItinerisVertex v, RelationType typeVertex) {
        if (typeVertex.isEdgeLabel()) {
            return findEdgeRelations(v, typeVertex, rId, tx);
        } else {
            return ((VertexCentricQueryBuilder) v.query()).noPartitionRestriction().types(typeVertex).properties();
        }
    }

    public static Iterable<? extends ItinerisRelation> findEdgeRelations(ItinerisVertex v, RelationType type, RelationIdentifier rId, ItinerisTransaction tx){
        Direction dir = Direction.OUT;
        ItinerisVertex other = ((StandardItinerisTx)tx).getInternalVertex(rId.getInVertexId());
        if (other==null || other.isRemoved()) return null;
        if (((StandardItinerisTx) tx).isPartitionedVertex(v) && !((StandardItinerisTx) tx).isPartitionedVertex(other)) { //Swap for likely better performance
            ItinerisVertex tmp = other;
            other = v;
            v = tmp;
            dir = Direction.IN;
        }
        VertexCentricQueryBuilder query =
            ((VertexCentricQueryBuilder) v.query()).noPartitionRestriction().types(type).direction(dir).adjacent(other);

        RelationType internalVertex = ((StandardItinerisTx) tx).getExistingRelationType(type.longId());
        if (((InternalRelationType) internalVertex).getConsistencyModifier() != ConsistencyModifier.FORK) {
            query.has(ImplicitKey.ITINERISID.name(), rId.getRelationId());
        }
        return query.edges();
    }

    public static ItinerisEdge findEdge(RelationIdentifier rId, ItinerisTransaction tx) {
        ItinerisRelation r = findRelation(rId, tx);
        if (r == null) return null;
        else if (r instanceof ItinerisEdge) return (ItinerisEdge) r;
        else throw new UnsupportedOperationException("Referenced relation is a property not an edge");
    }

    public static ItinerisVertexProperty findProperty(RelationIdentifier rId, ItinerisTransaction tx) {
        ItinerisRelation r = findRelation(rId, tx);
        if (r == null) return null;
        else if (r instanceof ItinerisVertexProperty) return (ItinerisVertexProperty) r;
        else throw new UnsupportedOperationException("Referenced relation is a edge not a property");
    }
}
