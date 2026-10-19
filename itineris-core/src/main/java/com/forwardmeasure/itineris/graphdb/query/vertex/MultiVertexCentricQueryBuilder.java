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

package com.forwardmeasure.itineris.graphdb.query.vertex;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisMultiVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.VertexList;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.internal.RelationCategory;
import com.forwardmeasure.itineris.graphdb.query.BackendQueryHolder;
import com.forwardmeasure.itineris.graphdb.query.profile.QueryProfiler;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link ItinerisMultiVertexQuery} that extends {@link BasicVertexCentricQueryBuilder}
 * for all the query building and optimization and adds only the execution logic in
 * {@link #execute(com.forwardmeasure.itineris.graphdb.internal.RelationCategory, BasicVertexCentricQueryBuilder.ResultConstructor)}.
 * <p>
 * All other methods just prepare or transform that result set to fit the particular method semantics.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class MultiVertexCentricQueryBuilder extends BasicVertexCentricQueryBuilder<MultiVertexCentricQueryBuilder> implements ItinerisMultiVertexQuery<MultiVertexCentricQueryBuilder> {

    /**
     * The base vertices of this query
     */
    private final Set<InternalVertex> vertices;

    public MultiVertexCentricQueryBuilder(final StandardItinerisTx tx) {
        this(tx, null);
    }

    public MultiVertexCentricQueryBuilder(final StandardItinerisTx tx, Integer initialVerticesCapacity) {
        super(tx);
        vertices = initialVerticesCapacity != null ? new HashSet<>(initialVerticesCapacity) : new HashSet<>();
    }

    @Override
    protected MultiVertexCentricQueryBuilder getThis() {
        return this;
    }

    /* ---------------------------------------------------------------
     * Query Construction
     * ---------------------------------------------------------------
     */

    @Override
    public ItinerisMultiVertexQuery addVertex(Vertex vertex) {
        assert vertex != null;
        assert vertex instanceof InternalVertex;
        vertices.add(((InternalVertex) vertex).it());
        return this;
    }

    @Override
    public ItinerisMultiVertexQuery addAllVertices(Collection<? extends Vertex> vertices) {
        for (Vertex v : vertices) addVertex(v);
        return this;
    }

    /* ---------------------------------------------------------------
     * Query Execution
     * ---------------------------------------------------------------
     */

    /**
     * Constructs the BaseVertexCentricQuery through {@link BasicVertexCentricQueryBuilder#constructQuery(com.forwardmeasure.itineris.graphdb.internal.RelationCategory)}.
     * If the query asks for an implicit key, the resulting map is computed and returned directly.
     * If the query is empty, a map that maps each vertex to an empty list is returned.
     * Otherwise, the query is executed for all vertices through the transaction which will effectively
     * pre-load the return result sets into the associated {@link com.forwardmeasure.itineris.graphdb.vertices.CacheVertex} or
     * don't do anything at all if the vertex is new (and hence no edges in the storage backend).
     * After that, a map is constructed that maps each vertex to the corresponding VertexCentricQuery and wrapped
     * into a QueryProcessor. Hence, upon iteration the query will be executed like any other VertexCentricQuery
     * with the performance difference that the SliceQueries will have already been preloaded and not further
     * calls to the storage backend are needed.
     *
     * @param returnType
     * @return
     */
    protected <Q> Map<ItinerisVertex, Q> execute(RelationCategory returnType, ResultConstructor<Q> resultConstructor) {
        Preconditions.checkArgument(!vertices.isEmpty(), "Need to add at least one vertex to query");
        final Map<ItinerisVertex, Q> result = new HashMap<>(vertices.size());
        BaseVertexCentricQuery bq = super.constructQuery(returnType);
        profiler.setAnnotation(QueryProfiler.MULTIQUERY_ANNOTATION, true);
        profiler.setAnnotation(QueryProfiler.NUMVERTICES_ANNOTATION, vertices.size());
        if (!bq.isEmpty()) {
            Collection<InternalVertex> adjVertices = getResolvedAdjVertices();
            //Overwrite with more accurate size accounting for partitioned vertices
            profiler.setAnnotation(QueryProfiler.NUMVERTICES_ANNOTATION, adjVertices.size());
            if (bq.getQueries().size() == 1) {
                BackendQueryHolder<SliceQuery> query = bq.getQueries().get(0);
                // if it's just a single query - there is no need to group any queries together.
                // Thus, we can execute `executeMultiQuery` instead of `executeMultiSliceMultiQuery`
                tx.executeMultiQuery(adjVertices, query.getBackendQuery(), query.getProfiler());
            } else {
                tx.executeMultiSliceMultiQuery(adjVertices, bq.getQueries(), profiler);
            }
            for (InternalVertex v : vertices) {
                result.put(v, resultConstructor.getResult(v, bq));
            }
        } else {
            for (ItinerisVertex v : vertices)
                result.put(v, resultConstructor.emptyResult());
        }
        return result;
    }

    private Collection<InternalVertex> getResolvedAdjVertices() {
        if (hasQueryOnlyGivenVertex()) {
            return vertices;
        }

        boolean hasPartitionedVertices = false;
        for (InternalVertex v : vertices) {
            if (tx.isPartitionedVertex(v)) {
                hasPartitionedVertices = true;
                break;
            }
        }
        if (!hasPartitionedVertices) {
            return vertices;
        }

        profiler.setAnnotation(QueryProfiler.PARTITIONED_VERTEX_ANNOTATION, true);
        Set<InternalVertex> adjVertices = new HashSet<>(vertices.size() * 2);
        for (InternalVertex v : vertices) {
            if (tx.isPartitionedVertex(v)) {
                adjVertices.addAll(allRequiredRepresentatives(v));
            } else {
                adjVertices.add(v);
            }
        }

        return adjVertices;
    }

    public Map<ItinerisVertex, Iterable<? extends ItinerisRelation>> executeImplicitKeyQuery() {
        return new HashMap<ItinerisVertex, Iterable<? extends ItinerisRelation>>(vertices.size()) {{
            for (InternalVertex v : vertices) put(v, executeImplicitKeyQuery(v));
        }};
    }

    @Override
    public Map<ItinerisVertex, Iterable<ItinerisEdge>> edges() {
        return (Map) execute(RelationCategory.EDGE, new CachedRelationConstructor());
    }

    @Override
    public Map<ItinerisVertex, Iterable<ItinerisVertexProperty>> properties() {
        return (Map) (isImplicitKeyQuery(RelationCategory.PROPERTY) ?
            executeImplicitKeyQuery() :
            execute(RelationCategory.PROPERTY, new CachedRelationConstructor()));
    }

    @Override
    public void preFetch() {
        profiler.setAnnotation(QueryProfiler.MULTIPREFETCH_ANNOTATION, true);
        properties();
    }

    @Override
    public Map<ItinerisVertex, Iterable<ItinerisRelation>> relations() {
        return (Map) (isImplicitKeyQuery(RelationCategory.RELATION) ?
            executeImplicitKeyQuery() :
            execute(RelationCategory.RELATION, new CachedRelationConstructor()));
    }

    @Override
    public Map<ItinerisVertex, Iterable<ItinerisVertex>> vertices() {
        return execute(RelationCategory.EDGE, new VertexConstructor());
    }

    @Override
    public Map<ItinerisVertex, VertexList> vertexIds() {
        return execute(RelationCategory.EDGE, new VertexIdConstructor());
    }

    @Override
    public Map<ItinerisVertex, Iterable<ItinerisRelation>> drop() {
        Map<ItinerisVertex, Iterable<ItinerisRelation>> allRelations = this.noPartitionRestriction().all().relations();
        allRelations.forEach((vertex, relations) -> ((InternalVertex) vertex).remove(relations));
        return allRelations;
    }
}
