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

package com.forwardmeasure.itineris.graphdb.log;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.log.Change;
import com.forwardmeasure.itineris.core.log.ChangeState;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
class StandardChangeState implements ChangeState {

    private final EnumMap<Change,Set<ItinerisVertex>> vertices;
    private final EnumMap<Change,Set<ItinerisRelation>> relations;


    StandardChangeState() {
        vertices = new EnumMap<>(Change.class);
        relations = new EnumMap<>(Change.class);
        for (Change state : new Change[]{Change.ADDED,Change.REMOVED}) {
            vertices.put(state, new HashSet<>());
            relations.put(state, new HashSet<>());
        }
    }


    void addVertex(InternalVertex vertex, Change state) {
        vertices.get(state).add(vertex);
    }

    void addRelation(InternalRelation rel, Change state) {
        relations.get(state).add(rel);
    }

    @Override
    public Set<ItinerisVertex> getVertices(Change change) {
        if (change.isProper()) return vertices.get(change);
        assert change==Change.ANY;
        final Set<ItinerisVertex> all = new HashSet<>();
        for (Change state : new Change[]{Change.ADDED,Change.REMOVED}) {
            all.addAll(vertices.get(state));
            for (ItinerisRelation rel : relations.get(state)) {
                InternalRelation internalRelation = (InternalRelation)rel;
                for (int p=0;p<internalRelation.getLen();p++) all.add(internalRelation.getVertex(p));
            }
        }
        return all;
    }

    private<T> Set<T> toSet(T... types) {
        if (types==null || types.length==0) return new HashSet<>();
        return new HashSet<>(Arrays.asList(types));
    }

    private Iterable<ItinerisRelation> getRelations(final Change change, final Predicate<ItinerisRelation> filter) {
        Iterable<ItinerisRelation> base;
        if(change.isProper()) base=relations.get(change);
        else base=Iterables.concat(relations.get(Change.ADDED),relations.get(Change.REMOVED));
        return Iterables.filter(base,filter);
    }

    @Override
    public Iterable<ItinerisRelation> getRelations(final Change change, final RelationType... types) {
        final Set<RelationType> typeSet = toSet(types);
        return getRelations(change, itinerisRelation -> typeSet.isEmpty() || typeSet.contains(itinerisRelation.getType()));
    }

    @Override
    public Iterable<ItinerisEdge> getEdges(final Vertex vertex, final Change change, final Direction dir, final String... labels) {
        final Set<String> stypes = toSet(labels);
        return (Iterable)getRelations(change, itinerisRelation -> itinerisRelation.isEdge() && itinerisRelation.isIncidentOn(vertex) &&
                (dir==Direction.BOTH || ((ItinerisEdge)itinerisRelation).vertex(dir).equals(vertex)) &&
                (stypes.isEmpty() || stypes.contains(itinerisRelation.getType().name())));
    }


    @Override
    public Iterable<ItinerisVertexProperty> getProperties(final Vertex vertex, final Change change, final String... keys) {
        final Set<String> stypes = toSet(keys);
        return (Iterable)getRelations(change, itinerisRelation -> itinerisRelation.isProperty() && itinerisRelation.isIncidentOn(vertex) &&
                (stypes.isEmpty() || stypes.contains(itinerisRelation.getType().name())));
    }

}
