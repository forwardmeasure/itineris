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

package com.forwardmeasure.itineris.graphdb.query.vertex;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.tinkerpop.gremlin.structure.Direction;
import com.forwardmeasure.itineris.core.BaseVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.VertexList;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.graphdb.database.EdgeSerializer;
import com.forwardmeasure.itineris.graphdb.database.idhandling.IDHandler;
import com.forwardmeasure.itineris.graphdb.internal.ElementLifeCycle;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.internal.OrderList;
import com.forwardmeasure.itineris.graphdb.internal.RelationCategory;
import com.forwardmeasure.itineris.graphdb.query.BackendQueryHolder;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicate;
import com.forwardmeasure.itineris.graphdb.query.Query;
import com.forwardmeasure.itineris.graphdb.query.QueryProcessor;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.query.ResultMergeSortIterator;
import com.forwardmeasure.itineris.graphdb.query.ResultSetIterator;
import com.forwardmeasure.itineris.graphdb.query.condition.And;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.DirectionCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.IncidenceCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.Or;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.RelationTypeCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.VisibilityFilterCondition;
import com.forwardmeasure.itineris.graphdb.query.profile.QueryProfiler;
import com.forwardmeasure.itineris.graphdb.relations.RelationComparator;
import com.forwardmeasure.itineris.graphdb.relations.StandardVertexProperty;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.system.ImplicitKey;
import com.forwardmeasure.itineris.graphdb.types.system.SystemRelationType;
import com.forwardmeasure.itineris.graphdb.vertices.CacheVertex;
import com.forwardmeasure.itineris.util.datastructures.Interval;
import com.forwardmeasure.itineris.util.datastructures.PointInterval;
import com.forwardmeasure.itineris.util.datastructures.RangeInterval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link BaseVertexQuery}, optimizes the query and compiles the result into
 * a {@link com.forwardmeasure.itineris.graphdb.query.vertex.BaseVertexCentricQuery} which is then executed by one of the extending
 * classes.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public abstract class BasicVertexCentricQueryBuilder<Q extends BaseVertexQuery<Q>> extends BaseVertexCentricQueryBuilder<Q> {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BasicVertexCentricQueryBuilder.class);

    /**
     * Transaction in which this query is executed
     */
    protected final StandardItinerisTx tx;

    /**
     * The query profiler used to observe this query
     */
    protected QueryProfiler profiler = QueryProfiler.NO_OP;

    /**
     * Whether to query for system relations only
     */
    private boolean querySystem = false;

    /**
     * Whether to query for all relations, including system.
     */
    private boolean queryAll = false;

    /**
     Whether to query only for persisted edges, i.e. ignore any modifications to the vertex made in this transaction.
     This is achieved by using the {@link SimpleVertexQueryProcessor} for execution.
     */
    private boolean queryOnlyLoaded = false;

    /**
     * Whether this query should only focus on the provided vertex representative of a (potentially) partitioned vertex.
     * This effectively disables the automatic querying for other vertex representatives and focuses on the provided
     * vertex object only. This is used in combination with {@link com.forwardmeasure.itineris.graphdb.vertices.PreloadedVertex}.
     */
    private boolean queryOnlyGivenVertex = false;

    /**
     * Whether to restrict this query to the specified "local" partitions in this transaction
     */
    private boolean restrict2Partitions = true;


    public BasicVertexCentricQueryBuilder(final StandardItinerisTx tx) {
        super(tx);
        this.tx = Preconditions.checkNotNull(tx);
    }

    @Override
    public ItinerisVertex getVertex(Object vertexId) {
        return tx.getVertex(vertexId);
    }

    /* ---------------------------------------------------------------
     * Query Construction
	 * ---------------------------------------------------------------
	 */

    /**
     * Removes any query partition restriction for this query
     *
     * @return
     */
    public Q noPartitionRestriction() {
        this.restrict2Partitions = false;
        return getThis();
    }

    /**
     * Sets the query profiler to observe this query. Must be set before the query is executed to take effect.
     *
     * @param profiler
     * @return
     */
    public Q profiler(QueryProfiler profiler) {
        Preconditions.checkNotNull(profiler);
        this.profiler=profiler;
        return getThis();
    }

    /**
     * Restricts the result set of this query to only system types.
     * @return
     */
    public Q system() {
        this.querySystem = true;
        return getThis();
    }

    /**
     * Query all relations, including system types.
     * @return
     */
    public Q all() {
        this.queryAll = true;
        return getThis();
    }

    /**
     * Calling this method will cause this query to only included loaded (i.e. unmodified) relations in the
     * result set.
     * @return
     */
    public Q queryOnlyLoaded() {
        queryOnlyLoaded=true;
        return getThis();
    }

    public Q queryOnlyGivenVertex() {
        queryOnlyGivenVertex=true;
        return getThis();
    }


    /* ---------------------------------------------------------------
     * Inspection Methods
	 * ---------------------------------------------------------------
	 */

    protected boolean hasAllCanonicalTypes() {
        if (types.length==0) return false;
        for (String typeName : types) {
            InternalRelationType type = QueryUtil.getType(tx, typeName);
            if (type!=null && !type.isPropertyKey() && !type.multiplicity().isUnique(dir)) return false;
        }
        return true;
    }

    public boolean hasQueryOnlyGivenVertex() {
        return queryOnlyGivenVertex;
    }

    public boolean hasQueryOnlyLoaded() { return queryOnlyLoaded; }

    /* ---------------------------------------------------------------
     * Utility Methods
	 * ---------------------------------------------------------------
	 */

    protected static Iterable<ItinerisVertex> edges2Vertices(final Iterable<ItinerisEdge> edges,
                                                               final ItinerisVertex other) {
        // We don't use here `stream::iterator` because such iterable can be traversed only once.
        // We don't use `stream.collect(Collectors.toList())` because such list will be evaluated immediately instead of lazily.
        return Iterables.transform(edges, itinerisEdge ->  itinerisEdge.otherVertex(other));
    }

    protected VertexList edges2VertexIds(final Iterable<ItinerisEdge> edges, final ItinerisVertex other) {
        VertexArrayList vertices = new VertexArrayList(tx);
        for (ItinerisEdge edge : edges) vertices.add(edge.otherVertex(other));
        return vertices;
    }

    /* ---------------------------------------------------------------
     * Query Execution (Helper methods)
	 * ---------------------------------------------------------------
	 */

    /**
     * If {@link #isImplicitKeyQuery(com.forwardmeasure.itineris.graphdb.internal.RelationCategory)} is true,
     * this method provides the result set for the query based on the evaluation of the {@link ImplicitKey}.
     * <p>
     * Handling of implicit keys is completely distinct from "normal" query execution and handled extra
     * for completeness reasons.
     *
     * @param v
     * @return
     */
    protected Iterable<ItinerisRelation> executeImplicitKeyQuery(InternalVertex v) {
        assert isImplicitKeyQuery(RelationCategory.PROPERTY);
        if (dir==Direction.IN || limit<1) return Collections.emptyList();
        ImplicitKey key = (ImplicitKey)tx.getRelationType(types[0]);
        return ImmutableList.of(new StandardVertexProperty(0, key, v, key.computeProperty(v),
                v.isNew() ? ElementLifeCycle.New : ElementLifeCycle.Loaded));
    }

    protected interface ResultConstructor<Q> {

        Q getResult(InternalVertex v, BaseVertexCentricQuery bq);

        Q emptyResult();

    }

    protected class RelationConstructor implements ResultConstructor<Iterable<? extends ItinerisRelation>> {

        @Override
        public Iterable<? extends ItinerisRelation> getResult(InternalVertex v, BaseVertexCentricQuery bq) {
            return executeRelations(v,bq);
        }

        @Override
        public Iterable<? extends ItinerisRelation> emptyResult() {
            return Collections.emptyList();
        }

    }

    protected class CachedRelationConstructor implements ResultConstructor<Iterable<? extends ItinerisRelation>> {

        @Override
        public Iterable<? extends ItinerisRelation> getResult(InternalVertex v, BaseVertexCentricQuery bq) {
            return loadRelationsFromCache(v,bq);
        }

        @Override
        public Iterable<? extends ItinerisRelation> emptyResult() {
            return Collections.emptyList();
        }

    }

    protected class VertexConstructor implements ResultConstructor<Iterable<ItinerisVertex>> {

        @Override
        public Iterable<ItinerisVertex> getResult(InternalVertex v, BaseVertexCentricQuery bq) {
            return executeVertices(v,bq);
        }

        @Override
        public Iterable<ItinerisVertex> emptyResult() {
            return Collections.emptyList();
        }

    }

    protected class VertexIdConstructor implements ResultConstructor<VertexList> {

        @Override
        public VertexList getResult(InternalVertex v, BaseVertexCentricQuery bq) {
            return executeVertexIds(v,bq);
        }

        @Override
        public VertexList emptyResult() {
            return new VertexArrayList(tx);
        }

    }

    protected List<InternalVertex> allRequiredRepresentatives(InternalVertex partitionedVertex) {
        if (hasAllCanonicalTypes()) {
            return ImmutableList.of(tx.getCanonicalVertex(partitionedVertex));
        }
        return Arrays.asList(tx.getAllRepresentatives(partitionedVertex,restrict2Partitions));
    }

    protected final boolean isPartitionedVertex(InternalVertex vertex) {
        return !queryOnlyGivenVertex && tx.isPartitionedVertex(vertex);
    }

    protected boolean useSimpleQueryProcessor(BaseVertexCentricQuery query, InternalVertex... vertices) {
        assert vertices.length > 0;
        if (!query.isSimple()) return false;
        if (queryOnlyLoaded) return true;
        for (InternalVertex vertex : vertices) if (!vertex.isLoaded()) return false;
        return true;
    }

    protected Iterable<ItinerisRelation> executeRelations(InternalVertex vertex, BaseVertexCentricQuery baseQuery) {
        if (isPartitionedVertex(vertex)) {
            if (!hasAllCanonicalTypes()) {
                InternalVertex[] representatives = tx.getAllRepresentatives(vertex,restrict2Partitions);
                Iterable<ItinerisRelation> merge = null;
                //Each representative returns its relations in the order of a RelationComparator, which compares them
                //by the query's orders first
                final OrderList relationOrders = baseQuery.getOrders();
                final Comparator<ItinerisRelation> byOrders =
                    (r1, r2) -> RelationComparator.compareOnOrders(r1, r2, relationOrders);

                for (InternalVertex rep : representatives) {
                    Iterable<ItinerisRelation> iterable = executeIndividualRelations(rep,baseQuery);
                    if (merge==null) {
                        merge = iterable;
                    } else {
                        merge = ResultMergeSortIterator.mergeSort(merge, iterable, byOrders, false);
                    }
                }
                return ResultSetIterator.wrap(merge,baseQuery.getLimit());
            } else vertex = tx.getCanonicalVertex(vertex);
        }
        return executeIndividualRelations(vertex,baseQuery);
    }

    /**
     * Takes advantage of query result being cached in CachedVertex.
     * Deserializes properties only on access.
     * @param vertex
     * @param baseQuery
     * @return
     */
    protected Iterable<ItinerisRelation> loadRelationsFromCache(InternalVertex vertex, BaseVertexCentricQuery baseQuery) {
        Iterable<ItinerisRelation> merge = null;
        if (vertex instanceof CacheVertex && !isPartitionedVertex(vertex) && vertex.isLoaded()) {
            CacheVertex cacheVertex = (CacheVertex) vertex;
            boolean hasNotCachedQueries = baseQuery.getQueries().stream()
                .anyMatch(q -> cacheVertex.getFromCache(q.getBackendQuery()) == null || !q.isFitted());
            if (hasNotCachedQueries) {
                //Fall back to default path
                return executeRelations(vertex, baseQuery);
            } else {
                //Note: we optimize fetching relation from Vertex, if the query is already cached to avoid constructing a new query
                Comparator relationComparator = new RelationComparator(vertex, baseQuery.getOrders());
                for (BackendQueryHolder<SliceQuery> bqSubQuery : baseQuery.getQueries()) {
                    EntryList entryList = cacheVertex.getFromCache(bqSubQuery.getBackendQuery());
                    Preconditions.checkArgument(entryList != null, "Missing EntryList for Lazy load");
                    Iterable<ItinerisRelation> iterable = com.forwardmeasure.itineris.graphdb.transaction.RelationConstructor.readRelation(vertex,
                        entryList, tx);

                    if (merge == null) {
                        merge = iterable;
                    } else {
                        merge = ResultMergeSortIterator.mergeSort(merge, iterable, relationComparator, false);
                    }
                }
                
                return ResultSetIterator.wrap(merge, baseQuery.getLimit());
            }
        } else {
            //Fall back to default path
            return executeRelations(vertex, baseQuery);
        }
    }

    private Iterable<ItinerisRelation> executeIndividualRelations(InternalVertex vertex,
                                                                    BaseVertexCentricQuery baseQuery) {
        VertexCentricQuery query = constructQuery(vertex, baseQuery);
        return executeIndividualRelations(vertex, query);
    }

    private Iterable<ItinerisRelation> executeIndividualRelations(InternalVertex vertex,
                                                                    VertexCentricQuery query) {
        if (useSimpleQueryProcessor(query,vertex)) return new SimpleVertexQueryProcessor(query,tx).relations();
        else return new QueryProcessor<>(query, tx.edgeProcessor);
    }

    public Iterable<ItinerisVertex> executeVertices(InternalVertex vertex, BaseVertexCentricQuery baseQuery) {
        if (isPartitionedVertex(vertex)) {
            //If there is a sort order, we need to first merge the relations (and sort) and then compute vertices
            if (!orders.isEmpty()) return edges2VertexIds((Iterable) executeRelations(vertex,baseQuery), vertex);

            if (!hasAllCanonicalTypes()) {
                InternalVertex[] representatives = tx.getAllRepresentatives(vertex,restrict2Partitions);
                Iterable<ItinerisVertex> merge = null;

                for (InternalVertex rep : representatives) {
                    Iterable<ItinerisVertex> iterable = executeIndividualVertices(rep,baseQuery);
                    if (merge==null) merge = iterable;
                    else merge = ResultMergeSortIterator.mergeSort(merge, iterable,
                            VertexArrayList.VERTEX_ID_COMPARATOR,false);
                }
                return ResultSetIterator.wrap(merge,baseQuery.getLimit());
            } else vertex = tx.getCanonicalVertex(vertex);
        }
        return executeIndividualVertices(vertex,baseQuery);
    }

    private Iterable<ItinerisVertex> executeIndividualVertices(InternalVertex vertex,
                                                                 BaseVertexCentricQuery baseQuery) {
        VertexCentricQuery query = constructQuery(vertex, baseQuery);
        if (useSimpleQueryProcessor(query, vertex)) return new SimpleVertexQueryProcessor(query,tx).vertexIds();
        else return edges2Vertices((Iterable) executeIndividualRelations(vertex, query), query.getVertex());
    }

    public VertexList executeVertexIds(InternalVertex vertex, BaseVertexCentricQuery baseQuery) {
        if (isPartitionedVertex(vertex)) {
            //If there is a sort order, we need to first merge the relations (and sort) and then compute vertices
            if (!orders.isEmpty()) return edges2VertexIds((Iterable) executeRelations(vertex,baseQuery), vertex);

            if (!hasAllCanonicalTypes()) {
                InternalVertex[] representatives = tx.getAllRepresentatives(vertex,restrict2Partitions);
                VertexListInternal merge = null;

                for (InternalVertex rep : representatives) {
                    if (merge!=null && merge.size()>=baseQuery.getLimit() && baseQuery.hasLimit()) break;
                    VertexList vertexList = executeIndividualVertexIds(rep,baseQuery);
                    if (merge==null) merge = (VertexListInternal)vertexList;
                    else merge.addAll(vertexList);
                }
                if (merge != null && merge.size()>baseQuery.getLimit() && baseQuery.hasLimit()) {
                    merge = (VertexListInternal)merge.subList(0,baseQuery.getLimit());
                }
                return merge;
            } else vertex = tx.getCanonicalVertex(vertex);
        }
        return executeIndividualVertexIds(vertex,baseQuery);
    }

    private VertexList executeIndividualVertexIds(InternalVertex vertex, BaseVertexCentricQuery baseQuery) {
        VertexCentricQuery query = constructQuery(vertex, baseQuery);
        if (useSimpleQueryProcessor(query, vertex)) return new SimpleVertexQueryProcessor(query,tx).vertexIds();
        return edges2VertexIds((Iterable) executeIndividualRelations(vertex, query), vertex);
    }


    /* ---------------------------------------------------------------
     * Query Optimization and Construction
	 * ---------------------------------------------------------------
	 */

    private static final int HARD_MAX_LIMIT   = 300000;

    /**
     * Constructs a {@link VertexCentricQuery} for this query builder. The query construction and optimization
     * logic is taken from {@link #constructQuery(com.forwardmeasure.itineris.graphdb.internal.RelationCategory)}
     * This method only adds the additional conditions that are based on the base vertex.
     *
     * @param vertex for which to construct this query
     * @param baseQuery as constructed by {@link #constructQuery(com.forwardmeasure.itineris.graphdb.internal.RelationCategory)}
     * @return
     */
    protected VertexCentricQuery constructQuery(InternalVertex vertex, BaseVertexCentricQuery baseQuery) {
        Condition<ItinerisRelation> condition = baseQuery.getCondition();
        if (!baseQuery.isEmpty()) {
            //Add adjacent-vertex and direction related conditions; copy conditions to so that baseQuery does not change
            final And<ItinerisRelation> newCondition = new And<>();
            if (condition instanceof And) newCondition.addAll((And) condition);
            else newCondition.add(condition);

            newCondition.add(new DirectionCondition<>(vertex,dir));
            if (adjacentVertex != null)
                newCondition.add(new IncidenceCondition<>(vertex,adjacentVertex));

            condition = newCondition;
        }
        VertexCentricQuery query = new VertexCentricQuery(vertex, condition, baseQuery.getDirection(),
            baseQuery.getQueries(), baseQuery.getOrders(), baseQuery.getLimit());
        Preconditions.checkArgument(!queryOnlyLoaded || query.isSimple(),
                "Query-only-loaded only works on simple queries");
        return query;
    }

    //As QueryUtil.getType, but a query of one type has resolved it already
    private InternalRelationType resolveType(String typeName) {
        final RelationType type = types.length == 1 ? singleType() : tx.getRelationType(typeName);
        if (type == null && !tx.getConfiguration().getAutoSchemaMaker().ignoreUndefinedQueryTypes()) {
            throw new IllegalArgumentException("Undefined type used in query: " + typeName);
        }
        return (InternalRelationType) type;
    }

    protected BaseVertexCentricQuery constructQuery(RelationCategory returnType) {
        QueryProfiler optProfiler = profiler.addNested(QueryProfiler.OPTIMIZATION);
        optProfiler.startTimer();
        BaseVertexCentricQuery query = constructQueryWithoutProfile(returnType);
        optProfiler.stopTimer();
        query.observeWith(profiler);
        return query;
    }

    private BaseVertexCentricQuery getAllRelationsQuery() {
        SliceQuery fullQuery = new SliceQuery(
            IDHandler.getBounds(RelationCategory.RELATION, true)[0],
            IDHandler.getBounds(RelationCategory.RELATION, false)[1]);

        List<BackendQueryHolder<SliceQuery>> queries = Collections.singletonList(new BackendQueryHolder<>(fullQuery, true, true));
        return new BaseVertexCentricQuery(RelationCategory.RELATION, Direction.BOTH, queries, OrderList.NO_ORDER, Query.NO_LIMIT);
    }

    protected BaseVertexCentricQuery constructQueryWithoutProfile(RelationCategory returnType) {
        assert returnType != null;
        Preconditions.checkArgument(adjacentVertex==null || returnType == RelationCategory.EDGE,
                "Vertex constraints only apply to edges");

        if(queryAll) {
            return getAllRelationsQuery();
        }

        if (limit <= 0)
            return BaseVertexCentricQuery.emptyQuery();

        //Prepare direction
        if (returnType == RelationCategory.PROPERTY) {
            if (dir == Direction.IN)
                return BaseVertexCentricQuery.emptyQuery();
            dir = Direction.OUT;
        }
        //Prepare order
        orders.makeImmutable();
        assert orders.hasCommonOrder();

        //Prepare constraints
        And<ItinerisRelation> conditions = QueryUtil.constraints2QNF(tx, constraints);
        if (conditions == null)
            return BaseVertexCentricQuery.emptyQuery();

        //Don't be smart with query limit adjustments - it just messes up the caching layer and
        //penalizes when appropriate limits are set by the user!
        int sliceLimit = limit;

        //Construct (optimal) SliceQueries
        EdgeSerializer serializer = tx.getEdgeSerializer();
        List<BackendQueryHolder<SliceQuery>> queries;
        if (!hasTypes()) {
            final BackendQueryHolder<SliceQuery> query= new BackendQueryHolder<>(
                serializer.getQuery(returnType, querySystem),
                (adjacentVertex == null && dir == Direction.BOTH || returnType == RelationCategory.PROPERTY && dir == Direction.OUT)
                        && !conditions.hasChildren(),
                orders.isEmpty());
            if (sliceLimit!=Query.NO_LIMIT && sliceLimit<Integer.MAX_VALUE/3) {
                //If only one direction is queried, ask for twice the limit from backend since approximately
                //half will be filtered
                if (dir != Direction.BOTH
                        && (returnType == RelationCategory.EDGE || returnType == RelationCategory.RELATION)) {
                    sliceLimit *= 2;
                }
            }
            query.getBackendQuery().setLimit(computeLimit(conditions.size(),sliceLimit));
            queries = ImmutableList.of(query);
            conditions.add(returnType);
            conditions.add(new VisibilityFilterCondition<>(
                //Need this to filter out newly created invisible relations in the transaction
                querySystem ? VisibilityFilterCondition.Visibility.SYSTEM
                        : VisibilityFilterCondition.Visibility.NORMAL));
        } else {
            final Set<RelationType> ts = new HashSet<>(types.length);
            queries = new ArrayList<>(types.length + 2);
            final Map<RelationType,Interval> intervalConstraints = new HashMap<>(conditions.size());
            final boolean isIntervalFittedConditions = compileConstraints(conditions,intervalConstraints);
            for (Interval pint : intervalConstraints.values()) {
                //Check if one of the constraints leads to an empty result set
                if (pint.isEmpty()) return BaseVertexCentricQuery.emptyQuery();
            }

            for (String typeName : types) {
                if (typeName == null) continue;
                InternalRelationType type = resolveType(typeName);
                if (type==null) continue;
                Preconditions.checkArgument(!querySystem || (type instanceof SystemRelationType),
                        "Can only query for system types: %s", type);
                if (type instanceof ImplicitKey) {
                    throw new UnsupportedOperationException("Implicit types are not supported in complex queries: "
                            + type);
                }
                ts.add(type);

                Direction typeDir = dir;
                if (type.isPropertyKey()) {
                    Preconditions.checkArgument(returnType != RelationCategory.EDGE,
                            "Querying for edges but including a property key: %s", type.name());
                    returnType = RelationCategory.PROPERTY;
                    typeDir = Direction.OUT;
                }
                if (type.isEdgeLabel()) {
                    Preconditions.checkArgument(returnType != RelationCategory.PROPERTY,
                        "Querying for properties but including an edge label: %s", type.name());
                    returnType = RelationCategory.EDGE;
                    if (!type.isUnidirected(Direction.BOTH)) {
                        //Make sure unidirectionality lines up
                        if (typeDir==Direction.BOTH) {
                            if (type.isUnidirected(Direction.OUT)) typeDir=Direction.OUT;
                            else typeDir=Direction.IN;
                        } else if (!type.isUnidirected(typeDir)) continue; //Directions are incompatible
                    }
                }


                if (type.isEdgeLabel() && typeDir==Direction.BOTH && intervalConstraints.isEmpty()
                        && orders.isEmpty()) {
                    //TODO: This if-condition is a little too restrictive - we also want to include those cases where
                    //there ARE intervalConstraints or orders but those cannot be covered by any sort-keys
                    SliceQuery q = serializer.getQuery(type, typeDir, null);
                    q.setLimit(sliceLimit);
                    queries.add(new BackendQueryHolder<>(q, isIntervalFittedConditions, true));
                } else {
                    //Optimize for each direction independently
                    Direction[] dirs = {typeDir};
                    if (typeDir == Direction.BOTH) {
                        if (type.isEdgeLabel())
                            dirs = new Direction[]{Direction.OUT, Direction.IN};
                        else
                            dirs = new Direction[]{Direction.OUT}; //property key
                    }

                    for (Direction direction : dirs) {
                        /*
                        Find best scoring relation type to answer this query with. We score each candidate by the number
                        of conditions that each sort-keys satisfy. Equality conditions score higher than interval
                        conditions since they are more restrictive. We assign additional points if the sort key
                        satisfies the order of this query.
                        */
                        InternalRelationType bestCandidate = null;
                        double bestScore = Double.NEGATIVE_INFINITY;
                        boolean bestCandidateSupportsOrder = false;
                        PropertyKey[] bestCandidateExtendedSortKey = null;
                        for (InternalRelationType candidate : type.getRelationIndexes()) {
                            //Filter out those that don't apply
                            if (!candidate.isUnidirected(Direction.BOTH) && !candidate.isUnidirected(direction)) {
                                continue;
                            }
                            if (!candidate.equals(type) && candidate.getStatus()!= SchemaStatus.ENABLED) continue;

                            boolean supportsOrder = orders.isEmpty()
                                    || orders.getCommonOrder() == candidate.getSortOrder();
                            int currentOrder = 0;

                            double score = 0.0;
                            PropertyKey[] extendedSortKey = getExtendedSortKey(candidate,direction,tx);

                            for (PropertyKey keyType : extendedSortKey) {
                                if (currentOrder < orders.size() && orders.getKey(currentOrder).equals(keyType))
                                    currentOrder++;

                                Interval interval = intervalConstraints.get(keyType);
                                if (interval == null || !interval.isPoints()) {
                                    if (interval != null) score += 1;
                                    break;
                                } else {
                                    assert interval.isPoints();
                                    score += 5.0 / interval.getPoints().size();
                                }
                            }
                            if (supportsOrder && currentOrder==orders.size()) score+=3;
                            if (score>bestScore) {
                                bestScore=score;
                                bestCandidate=candidate;
                                bestCandidateSupportsOrder=supportsOrder && currentOrder==orders.size();
                                bestCandidateExtendedSortKey = extendedSortKey;
                            }
                        }
                        Preconditions.checkArgument(bestCandidate != null,
                            "Current graph schema does not support the specified query constraints for type: %s",
                            type.name());

                        //Construct sort key constraints for the best candidate and then serialize into a SliceQuery
                        //that is wrapped into a BackendQueryHolder
                        EdgeSerializer.TypedInterval[] sortKeyConstraints
                                = new EdgeSerializer.TypedInterval[bestCandidateExtendedSortKey.length];
                        constructSliceQueries(bestCandidateExtendedSortKey, sortKeyConstraints, 0, bestCandidate, direction,
                                intervalConstraints, sliceLimit, isIntervalFittedConditions, bestCandidateSupportsOrder,
                                queries);
                    }
                }
            }
            if (queries.isEmpty())
                return BaseVertexCentricQuery.emptyQuery();

            conditions.add(getTypeCondition(ts));
        }
        return new BaseVertexCentricQuery(QueryUtil.simplifyAnd(conditions), dir, queries, orders, limit);
    }

    private void constructSliceQueries(PropertyKey[] extendedSortKey, EdgeSerializer.TypedInterval[] sortKeyConstraints,
                                       int position,
                                       InternalRelationType bestCandidate, Direction direction,
                                       Map<RelationType,Interval> intervalConstraints, int sliceLimit,
                                       boolean isIntervalFittedConditions, boolean bestCandidateSupportsOrder,
                                       List<BackendQueryHolder<SliceQuery>> queries) {
        if (position<extendedSortKey.length) {
            PropertyKey keyType = extendedSortKey[position];
            Interval interval = intervalConstraints.get(keyType);
            if (interval!=null) {
                sortKeyConstraints[position]=new EdgeSerializer.TypedInterval(keyType,interval);
                position++;
            }
            if (interval!=null && interval.isPoints()) {
                //Keep invoking recursively to see if we can satisfy more constraints...
                for (Object point : interval.getPoints()) {
                    EdgeSerializer.TypedInterval[] clonedSKC
                            = Arrays.copyOf(sortKeyConstraints,sortKeyConstraints.length);
                    clonedSKC[position-1]=new EdgeSerializer.TypedInterval(keyType,new PointInterval(point));
                    constructSliceQueries(extendedSortKey, clonedSKC, position,
                            bestCandidate, direction, intervalConstraints, sliceLimit,
                            isIntervalFittedConditions, bestCandidateSupportsOrder, queries);
                }
                return;
            }
        }
        //...otherwise this is it and we can construct the slicequery

        boolean isFitted = isIntervalFittedConditions && position==intervalConstraints.size();
        if (isFitted && position>0) {
            //If the last interval is open ended toward the larger values, then its not fitted because we need to
            //filter out NULL values which are serialized with -1 (largest value) byte up front.
            EdgeSerializer.TypedInterval lastInterval = sortKeyConstraints[position-1];
            if (!lastInterval.interval.isPoints() && lastInterval.interval.getEnd()==null) isFitted=false;
        }
        EdgeSerializer serializer = tx.getEdgeSerializer();
        SliceQuery q = serializer.getQuery(bestCandidate, direction, sortKeyConstraints);
        q.setLimit(computeLimit(intervalConstraints.size()-position, sliceLimit));
        queries.add(new BackendQueryHolder<>(q, isFitted, bestCandidateSupportsOrder));
    }


    /**
     * Returns the extended sort key of the given type. The extended sort key extends the type's primary sort key
     * by ADJACENT_ID and ID depending on the multiplicity of the type in the given direction.
     * It also converts the type ids to actual types.
     *
     * @param type
     * @param dir
     * @param tx
     * @return
     */
    private static PropertyKey[] getExtendedSortKey(InternalRelationType type, Direction dir, StandardItinerisTx tx) {
        int additional = 0;
        if (!type.multiplicity().isUnique(dir)) {
            if (!type.multiplicity().isConstrained()) additional++;
            if (type.isEdgeLabel()) additional++;
        }
        PropertyKey[] entireKey = new PropertyKey[type.getSortKey().length+additional];
        int i;
        for (i=0;i<type.getSortKey().length;i++) {
            entireKey[i]=tx.getExistingPropertyKey(type.getSortKey()[i]);
        }
        if (type.isEdgeLabel() && !type.multiplicity().isUnique(dir)) entireKey[i++]=ImplicitKey.ADJACENT_ID;
        if (!type.multiplicity().isConstrained()) entireKey[i]=ImplicitKey.ITINERISID;
        return entireKey;
    }

    /**
     * Converts the constraint conditions of this query into a constraintMap which is passed as an argument.
     * If all the constraint conditions could be accounted for in the constraintMap, this method returns true, else -
     * if some constraints cannot be captured in an interval - it returns false to indicate that further in-memory
     * filtering will be necessary.
     * </p>
     * This constraint map is used in constructing the SliceQueries and query optimization since this representation
     * is easier to handle.
     *
     * @param conditions
     * @param constraintMap
     * @return
     */
    private boolean compileConstraints(And<ItinerisRelation> conditions, Map<RelationType,Interval> constraintMap) {
        boolean isFitted = true;
        for (Condition<ItinerisRelation> condition : conditions.getChildren()) {
            RelationType type=null;
            Interval newInterval=null;
            if (condition instanceof Or) {
                Map.Entry<RelationType,Collection> orEqual = QueryUtil.extractOrCondition((Or)condition);
                if (orEqual!=null) {
                    type = orEqual.getKey();
                    newInterval = new PointInterval(orEqual.getValue());
                    //The conditions on the same key before it narrow it, as it narrows those after it
                    Interval interval = constraintMap.get(type);
                    if (interval!=null) newInterval = intersect(interval, newInterval);
                }
            } else if (condition instanceof PredicateCondition) {
                PredicateCondition<RelationType, ItinerisRelation> atom = (PredicateCondition)condition;
                type = atom.getKey();
                Interval interval = constraintMap.get(type);
                newInterval = intersectConstraints(interval, type, atom.getPredicate(), atom.getValue());
            }
            if (newInterval!=null) {
                constraintMap.put(type,newInterval);
            } else isFitted = false;
        }
        if (adjacentVertex!=null) {
            if (adjacentVertex.hasId()) {
                constraintMap.put(ImplicitKey.ADJACENT_ID,new PointInterval(adjacentVertex.id()));
            }
            else isFitted=false;
        }
        return isFitted;
    }

    private static Interval intersectConstraints(Interval pint, RelationType type,
                                                 ItinerisPredicate predicate, Object value) {
        Interval newInt;
        if (predicate instanceof Cmp) {
            switch ((Cmp) predicate) {
                case EQUAL:
                    if (value==null) return null;
                    newInt = new PointInterval(value);
                    break;
                case NOT_EQUAL:
                    return null;
                case LESS_THAN:
                    newInt = new RangeInterval().setEnd(value, false);
                    break;
                case LESS_THAN_EQUAL:
                    newInt = new RangeInterval().setEnd(value, true);
                    break;
                case GREATER_THAN:
                    newInt = new RangeInterval().setStart(value, false);
                    break;
                case GREATER_THAN_EQUAL:
                    newInt = new RangeInterval().setStart(value, true);
                    break;
                default: throw new AssertionError();
            }
        } else return null;
        assert newInt!=null;
        return pint!=null?intersect(pint, newInt):newInt;
    }

    //The intersection of the intervals, or null where their values are of different types, as those of a key of any
    //type may be: the conditions compare numbers of different types, which the intervals don't
    private static Interval intersect(Interval interval, Interval other) {
        final Class<?> type = valueType(interval);
        return type != null && type == valueType(other) ? interval.intersect(other) : null;
    }

    //The type of the values of the interval, or null where they are of several types
    private static Class<?> valueType(Interval<?> interval) {
        final Collection<?> values = interval instanceof PointInterval ? interval.getPoints()
            : Arrays.asList(interval.getStart(), interval.getEnd());
        Class<?> type = null;
        for (Object value : values) {
            if (value == null) continue;
            if (type == null) type = value.getClass();
            else if (type != value.getClass()) return null;
        }
        return type;
    }

    /**
     * Constructs a condition that is equivalent to the type constraints of this query if there are any.
     *
     * @param types
     * @return
     */
    private static Condition<ItinerisRelation> getTypeCondition(Set<RelationType> types) {
        assert !types.isEmpty();
        if (types.size() == 1)
            return new RelationTypeCondition<>(types.iterator().next());

        final Or<ItinerisRelation> typeCond = new Or<>(types.size());
        for (RelationType type : types)
            typeCond.add(new RelationTypeCondition<>(type));

        return typeCond;
    }

    /**
     * Updates a given user limit based on the number of conditions that can not be fulfilled by the backend query, i.e.
     * the query is not fitted and these remaining conditions must be enforced by filtering in-memory. By filtering in
     * memory, we will discard results returned from the backend and hence we should increase the limit to account for
     * this "waste" in order to not have to adjust the limit too often
     * in {@link com.forwardmeasure.itineris.graphdb.query.LimitAdjustingIterator}.
     *
     * @param remainingConditions
     * @param baseLimit
     * @return
     */
    private int computeLimit(int remainingConditions, int baseLimit) {
        if (baseLimit == Query.NO_LIMIT) {
            return baseLimit;
        }
        assert baseLimit > 0;
        baseLimit = Math.max(baseLimit,
                Math.min(HARD_MAX_LIMIT, QueryUtil.adjustLimitForTxModifications(tx, remainingConditions, baseLimit)));
        assert baseLimit > 0;
        return baseLimit;
    }


}
