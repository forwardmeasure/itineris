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

package com.forwardmeasure.itineris.graphdb.types.vertices;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.ItinerisVertexQuery;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.graphdb.internal.ItinerisSchemaCategory;
import com.forwardmeasure.itineris.graphdb.transaction.RelationConstructor;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.SchemaSource;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionCategory;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionDescription;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionMap;
import com.forwardmeasure.itineris.graphdb.types.indextype.CompositeIndexTypeWrapper;
import com.forwardmeasure.itineris.graphdb.types.indextype.MixedIndexTypeWrapper;
import com.forwardmeasure.itineris.graphdb.types.system.BaseKey;
import com.forwardmeasure.itineris.graphdb.types.system.BaseLabel;
import com.forwardmeasure.itineris.graphdb.vertices.CacheVertex;

import java.util.List;

public class ItinerisSchemaVertex extends CacheVertex implements SchemaSource {

    public ItinerisSchemaVertex(StandardItinerisTx tx, Object id, byte lifecycle) {
        super(tx, id, lifecycle);
    }

    private String name = null;
    private TypeDefinitionMap definition = null;
    private ListMultimap<TypeDefinitionCategory,Entry> outRelations = null;
    private ListMultimap<TypeDefinitionCategory,Entry> inRelations = null;

    @Override
    public long longId() {
        return ((Number) id()).longValue();
    }

    @Override
    public String name() {
        if (name == null) {
            ItinerisVertexProperty<String> p;
            if (isLoaded()) {
                StandardItinerisTx tx = tx();
                p = (ItinerisVertexProperty) Iterables.getOnlyElement(RelationConstructor.readRelation(this,
                        tx.getGraph().getSchemaCache().getSchemaRelations(longId(), BaseKey.SchemaName, Direction.OUT),
                        tx), null);
            } else {
                p = Iterables.getOnlyElement(query().type(BaseKey.SchemaName).properties(), null);
            }
            Preconditions.checkNotNull(p,"Could not find type for id: %s", longId());
            name = ItinerisSchemaCategory.getName(p.value());
        }
        assert name != null;
        return name;
    }

    @Override
    protected Vertex getVertexLabelInternal() {
        return null;
    }

    @Override
    public TypeDefinitionMap getDefinition() {
        TypeDefinitionMap def = definition;
        if (def == null) {
            def = new TypeDefinitionMap();
            Iterable<ItinerisVertexProperty> ps;
            if (isLoaded()) {
                StandardItinerisTx tx = tx();
                ps = (Iterable)RelationConstructor.readRelation(this,
                        tx.getGraph().getSchemaCache().getSchemaRelations(longId(), BaseKey.SchemaDefinitionProperty, Direction.OUT),
                        tx);
            } else {
                ps = query().type(BaseKey.SchemaDefinitionProperty).properties();
            }
            for (ItinerisVertexProperty property : ps) {
                TypeDefinitionDescription desc = property.valueOrNull(BaseKey.SchemaDefinitionDesc);
                Preconditions.checkArgument(desc!=null && desc.getCategory().isProperty());
                def.setValue(desc.getCategory(), property.value());
            }
            assert def.size()>0;
            definition = def;
        }
        assert def!=null;
        return def;
    }

    @Override
    public List<Entry> getRelated(TypeDefinitionCategory def, Direction dir) {
        assert dir==Direction.OUT || dir==Direction.IN;
        ListMultimap<TypeDefinitionCategory,Entry> relations = dir==Direction.OUT?outRelations:inRelations;
        if (relations==null) {
            ImmutableListMultimap.Builder<TypeDefinitionCategory,Entry> b = ImmutableListMultimap.builder();
            Iterable<ItinerisEdge> edges;
            if (isLoaded()) {
                StandardItinerisTx tx = tx();
                edges = (Iterable)RelationConstructor.readRelation(this,
                        tx.getGraph().getSchemaCache().getSchemaRelations(longId(), BaseLabel.SchemaDefinitionEdge, dir),
                        tx);
            } else {
                edges = query().type(BaseLabel.SchemaDefinitionEdge).direction(dir).edges();
            }
            for (ItinerisEdge edge: edges) {
                ItinerisVertex oth = edge.vertex(dir.opposite());
                assert oth instanceof ItinerisSchemaVertex;
                TypeDefinitionDescription desc = edge.valueOrNull(BaseKey.SchemaDefinitionDesc);
                Object modifier = null;
                if (desc.getCategory().hasDataType()) {
                    assert desc.getModifier()!=null && desc.getModifier().getClass().equals(desc.getCategory().getDataType());
                    modifier = desc.getModifier();
                }
                b.put(desc.getCategory(), new Entry((ItinerisSchemaVertex) oth, modifier));
            }
            relations = b.build();
            if (dir==Direction.OUT) outRelations=relations;
            else inRelations=relations;
        }
        assert relations!=null;
        return relations.get(def);
    }

    /**
     * Resets the internal caches used to speed up lookups on this index type.
     * This is needed when the type gets modified in the {@link com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem}.
     */
    @Override
    public void resetCache() {
        name = null;
        definition=null;
        outRelations=null;
        inRelations=null;
    }

    public Iterable<ItinerisEdge> getEdges(final TypeDefinitionCategory def, final Direction dir) {
        return getEdges(def,dir,null);
    }

    public Iterable<ItinerisEdge> getEdges(final TypeDefinitionCategory def, final Direction dir, ItinerisSchemaVertex other) {
        ItinerisVertexQuery query = query().type(BaseLabel.SchemaDefinitionEdge).direction(dir);
        if (other!=null) query.adjacent(other);
        return Iterables.filter(query.edges(), (Predicate<ItinerisEdge>) edge -> {
            final TypeDefinitionDescription desc = edge.valueOrNull(BaseKey.SchemaDefinitionDesc);
            return desc.getCategory()==def;
        });
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public SchemaStatus getStatus() {
        return getDefinition().getValue(TypeDefinitionCategory.STATUS,SchemaStatus.class);
    }

    @Override
    public IndexType asIndexType() {
        Preconditions.checkArgument(getDefinition().containsKey(TypeDefinitionCategory.INTERNAL_INDEX),"Schema vertex is not a type vertex: [%s,%s]", longId(), name());
        return getDefinition().<Boolean>getValue(TypeDefinitionCategory.INTERNAL_INDEX) ?
            new CompositeIndexTypeWrapper(this) :
            new MixedIndexTypeWrapper(this);
    }

}
