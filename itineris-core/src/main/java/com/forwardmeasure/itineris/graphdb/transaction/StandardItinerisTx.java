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

package com.forwardmeasure.itineris.graphdb.transaction;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.Connection;
import com.forwardmeasure.itineris.core.EdgeLabel;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.ItinerisIndexQuery;
import com.forwardmeasure.itineris.core.ItinerisMultiVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.MixedIndexAggQuery;
import com.forwardmeasure.itineris.core.Multiplicity;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.ReadOnlyTransactionException;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.SchemaViolationException;
import com.forwardmeasure.itineris.core.VertexLabel;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.core.schema.ConsistencyModifier;
import com.forwardmeasure.itineris.core.schema.EdgeLabelMaker;
import com.forwardmeasure.itineris.core.schema.ItinerisSchemaElement;
import com.forwardmeasure.itineris.core.schema.PropertyKeyMaker;
import com.forwardmeasure.itineris.core.schema.SchemaInspector;
import com.forwardmeasure.itineris.core.schema.VertexLabelMaker;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BackendTransaction;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexTransaction;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.MultiKeysQueryGroups;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProvider;
import com.forwardmeasure.itineris.graphdb.database.EdgeSerializer;
import com.forwardmeasure.itineris.graphdb.database.IndexSerializer;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.idassigner.IDPool;
import com.forwardmeasure.itineris.graphdb.database.index.IndexRecords;
import com.forwardmeasure.itineris.graphdb.database.serialize.AttributeHandler;
import com.forwardmeasure.itineris.graphdb.database.util.IndexRecordUtil;
import com.forwardmeasure.itineris.graphdb.idmanagement.IDManager;
import com.forwardmeasure.itineris.graphdb.internal.ElementCategory;
import com.forwardmeasure.itineris.graphdb.internal.ElementLifeCycle;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertexLabel;
import com.forwardmeasure.itineris.graphdb.internal.ItinerisSchemaCategory;
import com.forwardmeasure.itineris.graphdb.internal.RelationCategory;
import com.forwardmeasure.itineris.graphdb.query.BackendQueryHolder;
import com.forwardmeasure.itineris.graphdb.query.MetricsQueryExecutor;
import com.forwardmeasure.itineris.graphdb.query.Query;
import com.forwardmeasure.itineris.graphdb.query.QueryExecutor;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.query.condition.And;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.ConditionUtil;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.query.graph.GraphCentricQuery;
import com.forwardmeasure.itineris.graphdb.query.graph.GraphCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.graph.IndexQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.graph.JointIndexQuery;
import com.forwardmeasure.itineris.graphdb.query.graph.MixedIndexAggQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.index.IndexSelectionStrategy;
import com.forwardmeasure.itineris.graphdb.query.profile.QueryProfiler;
import com.forwardmeasure.itineris.graphdb.query.vertex.MultiVertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexCentricQuery;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexWithInlineProps;
import com.forwardmeasure.itineris.graphdb.relations.RelationComparator;
import com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier;
import com.forwardmeasure.itineris.graphdb.relations.RelationIdentifierUtils;
import com.forwardmeasure.itineris.graphdb.relations.StandardEdge;
import com.forwardmeasure.itineris.graphdb.relations.StandardVertexProperty;
import com.forwardmeasure.itineris.graphdb.tinkerpop.ItinerisBlueprintsTransaction;
import com.forwardmeasure.itineris.graphdb.transaction.addedrelations.AddedRelationsContainer;
import com.forwardmeasure.itineris.graphdb.transaction.addedrelations.ConcurrentAddedRelations;
import com.forwardmeasure.itineris.graphdb.transaction.addedrelations.EmptyAddedRelations;
import com.forwardmeasure.itineris.graphdb.transaction.addedrelations.SimpleAddedRelations;
import com.forwardmeasure.itineris.graphdb.transaction.indexcache.ConcurrentIndexCache;
import com.forwardmeasure.itineris.graphdb.transaction.indexcache.EmptyIndexCache;
import com.forwardmeasure.itineris.graphdb.transaction.indexcache.IndexCache;
import com.forwardmeasure.itineris.graphdb.transaction.indexcache.SimpleIndexCache;
import com.forwardmeasure.itineris.graphdb.transaction.lock.CombinerLock;
import com.forwardmeasure.itineris.graphdb.transaction.lock.FakeLock;
import com.forwardmeasure.itineris.graphdb.transaction.lock.IndexLockTuple;
import com.forwardmeasure.itineris.graphdb.transaction.lock.LockTuple;
import com.forwardmeasure.itineris.graphdb.transaction.lock.ReentrantTransactionLock;
import com.forwardmeasure.itineris.graphdb.transaction.lock.TransactionLock;
import com.forwardmeasure.itineris.graphdb.transaction.subquerycache.CaffeineSubqueryCache;
import com.forwardmeasure.itineris.graphdb.transaction.subquerycache.EmptySubqueryCache;
import com.forwardmeasure.itineris.graphdb.transaction.subquerycache.SubqueryCache;
import com.forwardmeasure.itineris.graphdb.transaction.vertexcache.CaffeineVertexCache;
import com.forwardmeasure.itineris.graphdb.transaction.vertexcache.EmptyVertexCache;
import com.forwardmeasure.itineris.graphdb.transaction.vertexcache.VertexCache;
import com.forwardmeasure.itineris.graphdb.types.CompositeIndexType;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.StandardEdgeLabelMaker;
import com.forwardmeasure.itineris.graphdb.types.StandardPropertyKeyMaker;
import com.forwardmeasure.itineris.graphdb.types.StandardVertexLabelMaker;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionCategory;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionDescription;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionMap;
import com.forwardmeasure.itineris.graphdb.types.TypeInspector;
import com.forwardmeasure.itineris.graphdb.types.TypeUtil;
import com.forwardmeasure.itineris.graphdb.types.VertexLabelVertex;
import com.forwardmeasure.itineris.graphdb.types.system.BaseKey;
import com.forwardmeasure.itineris.graphdb.types.system.BaseLabel;
import com.forwardmeasure.itineris.graphdb.types.system.BaseVertexLabel;
import com.forwardmeasure.itineris.graphdb.types.system.ImplicitKey;
import com.forwardmeasure.itineris.graphdb.types.system.SystemRelationType;
import com.forwardmeasure.itineris.graphdb.types.system.SystemTypeManager;
import com.forwardmeasure.itineris.graphdb.types.vertices.EdgeLabelVertex;
import com.forwardmeasure.itineris.graphdb.types.vertices.ItinerisSchemaVertex;
import com.forwardmeasure.itineris.graphdb.types.vertices.PropertyKeyVertex;
import com.forwardmeasure.itineris.graphdb.util.IndexHelper;
import com.forwardmeasure.itineris.graphdb.util.MultiSliceQueriesGroupingUtil;
import com.forwardmeasure.itineris.graphdb.util.ProfiledIterator;
import com.forwardmeasure.itineris.graphdb.util.SubqueryIterator;
import com.forwardmeasure.itineris.graphdb.util.VertexCentricEdgeIterable;
import com.forwardmeasure.itineris.graphdb.vertices.CacheVertex;
import com.forwardmeasure.itineris.graphdb.vertices.PreloadedVertex;
import com.forwardmeasure.itineris.graphdb.vertices.StandardVertex;
import com.forwardmeasure.itineris.util.IDUtils;
import com.forwardmeasure.itineris.util.datastructures.Retriever;
import com.forwardmeasure.itineris.util.stats.MetricManager;
import org.jctools.maps.NonBlockingHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public class StandardItinerisTx extends ItinerisBlueprintsTransaction implements TypeInspector, SchemaInspector, VertexFactory {

    private static final Logger log = LoggerFactory.getLogger(StandardItinerisTx.class);

    private static final Map<Long, InternalRelation> EMPTY_DELETED_RELATIONS = Collections.emptyMap();
    private static final ConcurrentMap<LockTuple, TransactionLock> UNINITIALIZED_LOCKS = null;
    private static final Duration LOCK_TIMEOUT = Duration.ofMillis(5000L);

    /**
     * This is a workaround for #893.  Cache sizes small relative to the level
     * of thread parallelism can lead to Itineris generating multiple copies of
     * a single vertex in a single transaction.
     */
    private static final long MIN_VERTEX_CACHE_SIZE = 100L;


    private final StandardItineris graph;
    private final TransactionConfiguration config;
    private final IDManager idManager;
    private final IDManager idInspector;
    private final AttributeHandler attributeHandler;
    private BackendTransaction txHandle;
    private final EdgeSerializer edgeSerializer;
    private final IndexSerializer indexSerializer;
    private final IndexSelectionStrategy indexSelector;

    /* ###############################################
            Internal Data Structures
     ############################################### */

    //####### Vertex Cache
    /**
     * Keeps track of vertices already loaded in memory. Cannot release vertices with added relations.
     */
    private VertexCache vertexCache;

    /**
     * Keeps track of relation types vertex. Used only in {@link com.forwardmeasure.itineris.core.ItinerisLazyRelation}.
     * Cache is thread-safe.
     */
    private final Map<Long, InternalRelationType> relTypeCache;

    //######## Data structures that keep track of new and deleted elements
    //These data structures cannot release elements, since we would loose track of what was added or deleted
    /**
     * Keeps track of all added relations in this transaction
     */
    private AddedRelationsContainer addedRelations;
    /**
     * Keeps track of all deleted relations in this transaction
     */
    private volatile Map<Long, InternalRelation> deletedRelations;

    //######## Index Caches
    /**
     * Caches the result of index calls so that repeated index queries don't need
     * to be passed to the IndexProvider. This cache will drop entries when it overflows
     * since the result set can always be retrieved from the IndexProvider
     */
    private SubqueryCache indexCache;
    /**
     * Builds an inverted index for newly added properties so they can be considered in index queries.
     * This cache my not release elements since that would entail an expensive linear scan over addedRelations
     */
    private IndexCache newVertexIndexEntries;

    //######## Lock applications
    /**
     * Transaction-local data structure for unique lock applications so that conflicting applications can be discovered
     * at the transactional level.
     */
    private volatile Map<LockTuple, TransactionLock> uniqueLocks;

    //####### Other Data structures
    /**
     * Caches Itineris types by name so that they can be quickly retrieved once they are loaded in the transaction.
     * Since type retrieval by name is common and there are only a few types, since cache is a simple map (i.e. no release)
     */
    private Map<String, Long> newTypeCache;

    /**
     * Used to assign temporary ids to new vertices and relations added in this transaction.
     * If ids are assigned immediately, this is not used. This IDPool is shared across all elements.
     */
    private final IDPool temporaryIds;

    /**
     * This belongs in ItinerisConfig.
     */
    private final TimestampProvider times;

    /**
     * Whether or not this transaction is open
     */
    private volatile boolean isOpen;

    private final VertexConstructor existingVertexRetriever;
    private final VertexConstructor externalVertexRetriever;
    private final VertexConstructor internalVertexRetriever;

    public StandardItinerisTx(StandardItineris graph, TransactionConfiguration config) {
        Preconditions.checkNotNull(graph);
        Preconditions.checkArgument(graph.isOpen());
        Preconditions.checkNotNull(config);
        this.graph = graph;
        this.times = graph.getConfiguration().getTimestampProvider();
        this.allowCustomVertexIdType = graph.getConfiguration().allowCustomVertexIdType();
        this.config = config;
        this.idManager = graph.getIDManager();
        this.idInspector = idManager;
//        this.idInspector = idManager.getIdInspector();
        this.attributeHandler = graph.getDataSerializer();
        this.edgeSerializer = graph.getEdgeSerializer();
        this.indexSerializer = graph.getIndexSerializer();
        this.indexSelector = graph.getIndexSelector();

        temporaryIds = new IDPool() {

            private final AtomicLong counter = new AtomicLong(1);

            @Override
            public long nextID() {
                return counter.getAndIncrement();
            }

            @Override
            public void close() {
                //Do nothing
            }
        };

        if (config.isSingleThreaded()) {
            addedRelations = new SimpleAddedRelations(false);
            newTypeCache = new HashMap<>();
            newVertexIndexEntries = new SimpleIndexCache();
        } else {
            addedRelations = new ConcurrentAddedRelations(false);
            newTypeCache = new NonBlockingHashMap<>();
            newVertexIndexEntries = new ConcurrentIndexCache();
        }

        boolean preloadedData = config.hasPreloadedData();
        externalVertexRetriever = new VertexConstructor(config.hasVerifyExternalVertexExistence(), preloadedData);
        internalVertexRetriever = new VertexConstructor(config.hasVerifyInternalVertexExistence(), preloadedData);
        existingVertexRetriever = new VertexConstructor(false, preloadedData);

        long effectiveVertexCacheSize = config.getVertexCacheSize();
        if (!config.isReadOnly()) {
            effectiveVertexCacheSize = Math.max(MIN_VERTEX_CACHE_SIZE, effectiveVertexCacheSize);
            log.debug("Caffeine vertex cache size: requested={} effective={} (min={})",
                    config.getVertexCacheSize(), effectiveVertexCacheSize, MIN_VERTEX_CACHE_SIZE);
        }

        if(this.config.isLazyLoadRelations()) {
            relTypeCache = new ConcurrentHashMap<>(30);
        } else {
            relTypeCache = null;
        }

        vertexCache = new CaffeineVertexCache(effectiveVertexCacheSize,config.getDirtyVertexSize());

        indexCache = new CaffeineSubqueryCache(config.getIndexCacheWeight());

        uniqueLocks = UNINITIALIZED_LOCKS;
        deletedRelations = EMPTY_DELETED_RELATIONS;

        this.isOpen = true;
        if (null != config.getGroupName()) {
            MetricManager.INSTANCE.getCounter(config.getGroupName(), "tx", "begin").inc();
            elementProcessor = new MetricsQueryExecutor<>(config.getGroupName(), "graph", elementProcessorImpl);
            edgeProcessor    = new MetricsQueryExecutor<>(config.getGroupName(), "vertex", edgeProcessorImpl);
        } else {
            elementProcessor = elementProcessorImpl;
            edgeProcessor    = edgeProcessorImpl;
        }
    }

    public void setBackendTransaction(BackendTransaction txHandle) {
        Preconditions.checkArgument(this.txHandle==null && txHandle!=null);
        this.txHandle = txHandle;
    }

    /*
     * ------------------------------------ Utility Access Verification methods ------------------------------------
     */

    private void verifyWriteAccess(ItinerisVertex... vertices) {
        if (config.isReadOnly())
            throw new ReadOnlyTransactionException("Cannot create new entities in read-only transaction");
        for (ItinerisVertex v : vertices) {
            if (v.hasId() && idInspector.isUnmodifiableVertex(v.id()) && !v.isNew())
                throw new SchemaViolationException("Cannot modify unmodifiable vertex: "+v);
        }
        verifyAccess(vertices);
    }

    public final void verifyAccess(ItinerisVertex... vertices) {
        verifyOpen();
        for (ItinerisVertex v : vertices) {
            Preconditions.checkArgument(v instanceof InternalVertex, "Invalid vertex: %s", v);
            if (!(v instanceof SystemRelationType) && this != ((InternalVertex) v).tx())
                throw new IllegalStateException("The vertex or type is not associated with this transaction [" + v + "]");
            if (v.isRemoved())
                throw new IllegalStateException("The vertex or type has been removed [" + v + "]");
        }
    }

    private void verifyOpen() {
        if (isClosed())
            throw new IllegalStateException("Operation cannot be executed because the enclosing transaction is closed");
    }

    /*
     * ------------------------------------ External Access ------------------------------------
     */

    public StandardItinerisTx getNextTx() {
        Preconditions.checkArgument(isClosed());
        if (!config.isThreadBound())
            throw new IllegalStateException("Cannot access element because its enclosing transaction is closed and unbound");
        else return (StandardItinerisTx) graph.getCurrentThreadTx();
    }

    public TransactionConfiguration getConfiguration() {
        return config;
    }

    @Override
    public StandardItineris getGraph() {
        return graph;
    }

    public BackendTransaction getTxHandle() {
        return txHandle;
    }

    public EdgeSerializer getEdgeSerializer() {
        return edgeSerializer;
    }

    public IDManager getIdInspector() {
        return idInspector;
    }

    public boolean isPartitionedVertex(ItinerisVertex vertex) {
        return vertex.hasId() && idInspector.isPartitionedVertex(vertex.id());
    }

    public InternalVertex getCanonicalVertex(InternalVertex partitionedVertex) {
        Preconditions.checkArgument(isPartitionedVertex(partitionedVertex));
        long canonicalId = idManager.getCanonicalVertexId(((Number) partitionedVertex.id()).longValue());
        if (partitionedVertex.id().equals(canonicalId)) return partitionedVertex;
        else return getExistingVertex(canonicalId);
    }

    public InternalVertex getOtherPartitionVertex(ItinerisVertex partitionedVertex, long otherPartition) {
        Preconditions.checkArgument(isPartitionedVertex(partitionedVertex));
        return getExistingVertex(idManager.getPartitionedVertexId(((Number) partitionedVertex.id()).longValue(), otherPartition));
    }

    public InternalVertex[] getAllRepresentatives(ItinerisVertex partitionedVertex, boolean restrict2Partitions) {
        Preconditions.checkArgument(isPartitionedVertex(partitionedVertex));
        long[] ids;
        if (!restrict2Partitions || !config.hasRestrictedPartitions()) {
            ids = idManager.getPartitionedVertexRepresentatives(((Number) partitionedVertex.id()).longValue());
        } else {
            int[] restrictedPartitions = config.getRestrictedPartitions();
            ids = new long[restrictedPartitions.length];
            for (int i=0;i<ids.length;i++) {
                ids[i]=idManager.getPartitionedVertexId(((Number) partitionedVertex.id()).longValue(),restrictedPartitions[i]);
            }
        }
        Preconditions.checkArgument(ids.length>0);
        InternalVertex[] vertices = new InternalVertex[ids.length];
        for (int i=0;i<ids.length;i++) vertices[i]=getExistingVertex(ids[i]);
        return vertices;
    }

    /*
     * ------------------------------------ Relation Type Handling ------------------------------------
     */

    public InternalRelationType getOrLoadRelationTypeById(long typeId) {
        if (relTypeCache == null) {
            return loadRelationTypeById(typeId);
        } else {
            return this.relTypeCache.computeIfAbsent(typeId, this::loadRelationTypeById);
        }
    }

    private InternalRelationType loadRelationTypeById(long typeId) {
        return TypeUtil.getBaseType((InternalRelationType) this.getExistingRelationType(typeId));
    }

    /*
     * ------------------------------------ Vertex Handling ------------------------------------
     */

    public boolean containsVertex(final Object vertexId) {
        return getVertex(vertexId) != null;
    }

    private boolean isValidVertexId(Object id) {
        if (!idInspector.isSchemaVertexId(id) && !idInspector.isUserVertexId(id)) {
            return false;
        }
        if (id instanceof Number) {
            return ((Number) id).longValue() > 0;
        } else {
            assert id instanceof String;
            if (!StringUtils.isAsciiPrintable((String) id)) {
                log.warn("ID contains non-ascii or non-printable character, ignored: " + id);
                return false;
            }
            if (((String) id).contains(RelationIdentifier.TOSTRING_DELIMITER)) {
                log.warn("ID contains illegal " + RelationIdentifier.TOSTRING_DELIMITER + " substring, ignored: " + id);
                return false;
            }
            return true;
        }
    }

    @Override
    public ItinerisVertex getVertex(Object vertexId) {
        verifyOpen();
        if (null != config.getGroupName()) {
            MetricManager.INSTANCE.getCounter(config.getGroupName(), "db", "getVertexByID").inc();
        }
        if (!isValidVertexId(vertexId)) return null;
        //Make canonical partitioned vertex id
        if (idInspector.isPartitionedVertex(vertexId)) vertexId=idManager.getCanonicalVertexId(((Number) vertexId).longValue());

        final InternalVertex v = vertexCache.get(vertexId, externalVertexRetriever);
        return (null == v || v.isRemoved()) ? null : v;
    }

    @Override
    public Iterable<ItinerisVertex> getVertices(Object... ids) {
        verifyOpen();
        if (ids==null || ids.length==0) return (Iterable)getInternalVertices();

        if (null != config.getGroupName()) {
            MetricManager.INSTANCE.getCounter(config.getGroupName(), "db", "getVerticesByID").inc();
        }
        final List<ItinerisVertex> result = new ArrayList<>(ids.length);
        final List<Object> vertexIds = new ArrayList<>(ids.length);
        for (Object id : ids) {
            assert id instanceof String || id instanceof Number;
            if (!allowCustomVertexIdType && id instanceof String) {
                // Convert string to long to keep backward compatibility prior to 1.0.0
                // prior to Itineris 1.0.0, vertices always have ids of long types. However, even if
                // a vertex has id 100L, both g.V(100L) and g.V("100") will return this vertex.
                // Since Itineris 1.0.0, vertex id can be of either long type or string type. To
                // keep backward compatibility, we hereby explicitly cast string id to long ids, if
                // string custom vertex id functionality is disabled.
                id = Long.valueOf((String) id);
            }
            if (isValidVertexId(id)) {
                if (idInspector.isPartitionedVertex(id)) id=idManager.getCanonicalVertexId(((Number) id).longValue());
                if (vertexCache.contains(id))
                    result.add(vertexCache.get(id, existingVertexRetriever));
                else
                    vertexIds.add(id);
            }
        }
        if (!vertexIds.isEmpty()) {
            if (externalVertexRetriever.hasVerifyExistence()) {
                List<EntryList> existence = graph.edgeMultiQuery(vertexIds,graph.vertexExistenceQuery,txHandle);
                for (int i = 0; i < vertexIds.size(); i++) {
                    if (!existence.get(i).isEmpty()) {
                        Object id = vertexIds.get(i);
                        result.add(vertexCache.get(id, existingVertexRetriever));
                    }
                }
            } else {
                for (int i = 0; i < vertexIds.size(); i++) {
                    result.add(vertexCache.get(vertexIds.get(i),externalVertexRetriever));
                }
            }
        }
        //Filter out potentially removed vertices
        result.removeIf(ItinerisElement::isRemoved);
        return result;
    }

    private InternalVertex getExistingVertex(Object vertexId) {
        //return vertex no matter what, even if deleted, and assume the id has the correct format
        return vertexCache.get(vertexId, existingVertexRetriever);
    }

    public InternalVertex getInternalVertex(Object vertexId) {
        //return vertex but potentially check for existence
        return vertexCache.get(vertexId, internalVertexRetriever);
    }

    private class VertexConstructor implements Retriever<Object, InternalVertex> {

        private final boolean verifyExistence;
        private final boolean createStubVertex;

        private VertexConstructor(boolean verifyExistence, boolean createStubVertex) {
            this.verifyExistence = verifyExistence;
            this.createStubVertex = createStubVertex;
        }

        public boolean hasVerifyExistence() {
            return verifyExistence;
        }

        @Override
        public InternalVertex get(Object vertexId) {
            IDUtils.checkId(vertexId);
            Preconditions.checkArgument(idInspector.isSchemaVertexId(vertexId) || idInspector.isUserVertexId(vertexId), "Not a valid vertex id: %s", vertexId);

            byte lifecycle = ElementLifeCycle.Loaded;
            Object canonicalVertexId = idInspector.isPartitionedVertex(vertexId)?idManager.getCanonicalVertexId(((Number) vertexId).longValue()):vertexId;
            if (verifyExistence) {
                if (graph.edgeQuery(canonicalVertexId, graph.vertexExistenceQuery, txHandle).isEmpty())
                    lifecycle = ElementLifeCycle.Removed;
            }
            if (!vertexId.equals(canonicalVertexId)) {
                //Take lifecycle from canonical representative
                lifecycle = getExistingVertex(canonicalVertexId).getLifeCycle();
            }

            final InternalVertex vertex;
            if (idInspector.isRelationTypeId(vertexId)) {
                if (idInspector.isPropertyKeyId(vertexId)) {
                    if (IDManager.isSystemRelationTypeId(vertexId)) {
                        vertex = SystemTypeManager.getSystemType(((Number) vertexId).longValue());
                    } else {
                        vertex = new PropertyKeyVertex(StandardItinerisTx.this, ((Number) vertexId).longValue(), lifecycle);
                    }
                } else {
                    assert idInspector.isEdgeLabelId(vertexId);
                    if (IDManager.isSystemRelationTypeId(vertexId)) {
                        vertex = SystemTypeManager.getSystemType(((Number) vertexId).longValue());
                    } else {
                        vertex = new EdgeLabelVertex(StandardItinerisTx.this, ((Number) vertexId).longValue(), lifecycle);
                    }
                }
            } else if (idInspector.isVertexLabelVertexId(vertexId)) {
                vertex = new VertexLabelVertex(StandardItinerisTx.this, ((Number) vertexId).longValue(), lifecycle);
            } else if (idInspector.isGenericSchemaVertexId(vertexId)) {
                vertex = new ItinerisSchemaVertex(StandardItinerisTx.this,vertexId, lifecycle);
            } else if (idInspector.isUserVertexId(vertexId)) {
                if (createStubVertex) vertex = new PreloadedVertex(StandardItinerisTx.this, vertexId, lifecycle);
                else vertex = new CacheVertex(StandardItinerisTx.this, vertexId, lifecycle);
            } else throw new IllegalArgumentException("ID could not be recognized");
            return vertex;
        }
    }

    @Override
    public ItinerisVertex addVertex(Object vertexId, VertexLabel label) {
        verifyWriteAccess();
        if (label==null) label=BaseVertexLabel.DEFAULT_VERTEXLABEL;
        if (vertexId != null && !graph.getConfiguration().allowVertexIdSetting()) {
            log.info("Provided vertex id [{}] is ignored because vertex id setting is not enabled", vertexId);
            vertexId = null;
        }
        Preconditions.checkArgument(vertexId != null || !graph.getConfiguration().allowVertexIdSetting(), "Must provide vertex id");
        Preconditions.checkArgument(vertexId == null || IDManager.VertexIDType.NormalVertex.is(vertexId), "Not a valid vertex id: %s", vertexId);
        Preconditions.checkArgument(vertexId == null || ((InternalVertexLabel)label).hasDefaultConfiguration(), "Cannot only use default vertex labels: %s",label);
        Preconditions.checkArgument(vertexId == null || !config.hasVerifyExternalVertexExistence() || !containsVertex(vertexId), "Vertex with given id already exists: %s", vertexId);
        if (vertexId != null && vertexId instanceof String && !StringUtils.isAsciiPrintable((String) vertexId)) {
            throw new IllegalArgumentException("Custom string id contains non-ascii or non-printable character: " + vertexId);
        }
        if (vertexId != null && vertexId instanceof String && ((String) vertexId).contains(RelationIdentifier.TOSTRING_DELIMITER)) {
            throw new IllegalArgumentException("Custom string id contains reserved string ("
                + RelationIdentifier.TOSTRING_DELIMITER + "): " + vertexId);
        }
        StandardVertex vertex = new StandardVertex(this, IDManager.getTemporaryVertexID(IDManager.VertexIDType.NormalVertex, temporaryIds.nextID()), ElementLifeCycle.New);
        if (vertexId != null) {
            vertex.setId(vertexId);
        } else if (config.hasAssignIDsImmediately() || label.isPartitioned()) {
            graph.assignID(vertex,label);
        }
        addProperty(vertex, BaseKey.VertexExists, Boolean.TRUE);
        if (label!=BaseVertexLabel.DEFAULT_VERTEXLABEL) { //Add label
            Preconditions.checkArgument(label instanceof VertexLabelVertex);
            addEdge(vertex, label, BaseLabel.VertexLabelEdge);
        }
        vertexCache.add(vertex, vertex.id());
        return vertex;

    }

    @Override
    public ItinerisVertex addVertex(String vertexLabel) {
        return addVertex(getOrCreateVertexLabel(vertexLabel));
    }

    public ItinerisVertex addVertex(VertexLabel vertexLabel) {
        return addVertex(null, vertexLabel);
    }

    private Iterable<InternalVertex> getInternalVertices() {
        Iterable<InternalVertex> allVertices;
        if (!addedRelations.isEmpty()) {
            //There are possible new vertices
            List<InternalVertex> newVs = vertexCache.getAllNew();
            newVs.removeIf(internalVertex -> internalVertex instanceof ItinerisSchemaElement);
            allVertices = Iterables.concat(newVs, new VertexIterable(graph, this));
        } else {
            allVertices = new VertexIterable(graph, this);
        }
        //Filter out all but one PartitionVertex representative
        return Iterables.filter(allVertices,
            internalVertex -> !isPartitionedVertex(internalVertex) ||
                internalVertex.id().equals(idInspector.getCanonicalVertexId(((Number) internalVertex.id()).longValue())));
    }

    /*
     * ------------------------------------ Adding and Removing Relations ------------------------------------
     */

    public final boolean validDataType(Class datatype) {
        return attributeHandler.validDataType(datatype);
    }

    public final Object verifyAttribute(PropertyKey key, Object attribute) {
        Class<?> datatype = key.dataType();
        if (datatype.equals(Object.class)) {
            if (!attributeHandler.validDataType(attribute.getClass())) {
                throw Property.Exceptions.dataTypeOfPropertyValueNotSupported(attribute);
            }
        } else {
            if (!attribute.getClass().equals(datatype)) {

                Object converted = null;
                try {
                    converted = attributeHandler.convert(datatype, attribute);
                } catch (IllegalArgumentException e) {
                    //Just means that data could not be converted
                }
                if (converted == null) throw new SchemaViolationException(
                        "Value [%s] is not an instance of the expected data type for property key [%s] and cannot be converted. Expected: %s, found: %s", attribute,
                        key.name(), datatype, attribute.getClass());
                attribute = converted;
            }
            assert (attribute.getClass().equals(datatype));
            attributeHandler.verifyAttribute(datatype, attribute);
        }
        return attribute;
    }

    public void removeRelation(InternalRelation relation) {
        Preconditions.checkArgument(!relation.isRemoved());
        relation = relation.it();
        for (int i = 0; i < relation.getLen(); i++)
            verifyWriteAccess(relation.getVertex(i));

        //Delete from Vertex
        for (int i = 0; i < relation.getLen(); i++) {
            InternalVertex vertex = relation.getVertex(i);
            vertex.removeRelation(relation);
            if (!vertex.isNew()) {
                vertexCache.add(vertex, vertex.id());
            }
        }
        //Update transaction data structures
        if (relation.isNew()) {
            addedRelations.remove(relation);
            if (TypeUtil.hasSimpleInternalVertexKeyIndex(relation)) newVertexIndexEntries.remove((ItinerisVertexProperty) relation);
        } else {
            Preconditions.checkArgument(relation.isLoaded());
            Map<Long, InternalRelation> result = deletedRelations;
            if (result == EMPTY_DELETED_RELATIONS) {
                if (config.isSingleThreaded()) {
                    deletedRelations = result = new HashMap<>();
                } else {
                    synchronized (this) {
                        result = deletedRelations;
                        if (result == EMPTY_DELETED_RELATIONS)
                            deletedRelations = result = new ConcurrentHashMap<>();
                    }
                }
            }
            result.put(relation.longId(), relation);
        }
    }

    public boolean isRemovedRelation(Long relationId) {
        return deletedRelations.containsKey(relationId);
    }

    private TransactionLock getLock(final Object... tuple) {
        return getLock(new LockTuple(tuple));
    }

    private TransactionLock getLock(final LockTuple la) {
        if (config.isSingleThreaded()) return FakeLock.INSTANCE;
        Map<LockTuple, TransactionLock> result = uniqueLocks;
        if (result == UNINITIALIZED_LOCKS) {
            Preconditions.checkArgument(!config.isSingleThreaded());
            synchronized (this) {
                result = uniqueLocks;
                if (result == UNINITIALIZED_LOCKS)
                    uniqueLocks = result = new ConcurrentHashMap<>();
            }
        }
        //TODO: clean out no longer used locks from uniqueLocks when it grows to large (use ReadWriteLock to protect against race conditions)
        TransactionLock lock = new ReentrantTransactionLock();
        TransactionLock existingLock = result.putIfAbsent(la, lock);
        return (existingLock == null)?lock:existingLock;
    }

    private TransactionLock getUniquenessLock(final ItinerisVertex out, final InternalRelationType type, final Object in) {
        Multiplicity multiplicity = type.multiplicity();
        TransactionLock uniqueLock = FakeLock.INSTANCE;
        if (config.hasVerifyUniqueness() && multiplicity.isConstrained()) {
            uniqueLock = null;
            if (multiplicity==Multiplicity.SIMPLE) {
                uniqueLock = getLock(out, type, in);
            } else {
                for (Direction dir : Direction.proper) {
                    if (multiplicity.isUnique(dir)) {
                        TransactionLock lock = getLock(dir == Direction.OUT ? out : in, type, dir);
                        if (uniqueLock==null) uniqueLock=lock;
                        else uniqueLock=new CombinerLock(uniqueLock,lock,times);
                    }
                }
            }
        }
        assert uniqueLock!=null;
        return uniqueLock;
    }

    private void checkPropertyConstraintForVertexOrCreatePropertyConstraint(ItinerisVertex vertex, PropertyKey key) {
        if (config.hasDisabledSchemaConstraints()) return;
        VertexLabel vertexLabel = vertex.vertexLabel();
        if (vertexLabel instanceof BaseVertexLabel) return;
        Collection<PropertyKey> propertyKeys = vertexLabel.mappedProperties();
        if (propertyKeys.contains(key)) return;
        config.getAutoSchemaMaker().makePropertyConstraintForVertex(vertexLabel, key, this);
    }

    public void checkPropertyConstraintForEdgeOrCreatePropertyConstraint(StandardEdge edge, PropertyKey key) {
        if (config.hasDisabledSchemaConstraints()) return;
        EdgeLabel edgeLabel = edge.edgeLabel();
        if (edgeLabel instanceof BaseLabel) return;
        Collection<PropertyKey> propertyKeys = edgeLabel.mappedProperties();
        if (propertyKeys.contains(key)) return;
        config.getAutoSchemaMaker().makePropertyConstraintForEdge(edgeLabel, key, this);
    }

    private void checkConnectionConstraintOrCreateConnectionConstraint(ItinerisVertex outVertex, ItinerisVertex inVertex, EdgeLabel edgeLabel) {
        if (config.hasDisabledSchemaConstraints()) return;

        VertexLabel outVertexLabel = outVertex.vertexLabel();
        if (outVertexLabel instanceof BaseVertexLabel) return;

        VertexLabel inVertexLabel = inVertex.vertexLabel();
        if (inVertexLabel instanceof BaseVertexLabel) return;

        Collection<Connection> connections = outVertexLabel.mappedConnections();
        for (Connection connection : connections) {
            if (connection.getIncomingVertexLabel().longId() != inVertexLabel.longId()) continue;
            if (connection.getEdgeLabel().equals(edgeLabel.name())) return;
        }
        config.getAutoSchemaMaker().makeConnectionConstraint(edgeLabel, outVertexLabel, inVertexLabel, this);
    }

    public ItinerisEdge addEdge(ItinerisVertex outVertex, ItinerisVertex inVertex, EdgeLabel label) {
       return addEdge(null, outVertex, inVertex, label, 0L);
    }

    public ItinerisEdge addEdge(Long id, ItinerisVertex outVertex, ItinerisVertex inVertex, EdgeLabel label, Long previousId) {
        verifyWriteAccess(outVertex, inVertex);
        outVertex = ((InternalVertex) outVertex).it();
        inVertex = ((InternalVertex) inVertex).it();
        Preconditions.checkNotNull(label);
        checkConnectionConstraintOrCreateConnectionConstraint(outVertex, inVertex, label);
        Multiplicity multiplicity = label.multiplicity();
        TransactionLock uniqueLock = getUniquenessLock(outVertex, (InternalRelationType) label,inVertex);
        uniqueLock.lock(LOCK_TIMEOUT);
        try {
            //Check uniqueness
            if (config.hasVerifyUniqueness()) {
                if (multiplicity==Multiplicity.SIMPLE) {
                    if (!Iterables.isEmpty(query(outVertex).type(label).direction(Direction.OUT).adjacent(inVertex).edges()))
                            throw new SchemaViolationException("An edge with the given label already exists between the pair of vertices and the label [%s] is simple", label.name());
                }
                if (multiplicity.isUnique(Direction.OUT)) {
                    if (!Iterables.isEmpty(query(outVertex).type(label).direction(Direction.OUT).edges()))
                            throw new SchemaViolationException("An edge with the given label already exists on the out-vertex and the label [%s] is out-unique", label.name());
                }
                if (multiplicity.isUnique(Direction.IN)) {
                    if (!Iterables.isEmpty(query(inVertex).type(label).direction(Direction.IN).edges()))
                            throw new SchemaViolationException("An edge with the given label already exists on the in-vertex and the label [%s] is in-unique", label.name());
                }
            }
            long edgeId = id == null ? IDManager.getTemporaryRelationID(temporaryIds.nextID()) : id;
            StandardEdge edge = new StandardEdge(edgeId, label, (InternalVertex) outVertex, (InternalVertex) inVertex, ElementLifeCycle.New);
            if (previousId > 0) edge.setPreviousID(previousId);
            if (config.hasAssignIDsImmediately() && id == null) graph.assignID(edge);
            connectRelation(edge);
            return edge;
        } finally {
            uniqueLock.unlock();
        }
    }

    private void connectRelation(InternalRelation r) {
        for (int i = 0; i < r.getLen(); i++) {
            boolean success = r.getVertex(i).addRelation(r);
            if (!success) throw new AssertionError("Could not connect relation: " + r);
        }
        addedRelations.add(r);
        for (int pos = 0; pos < r.getLen(); pos++) {
            InternalVertex vertex = r.getVertex(pos);
            if (!vertex.isNew()) {
                vertexCache.add(vertex, vertex.id());
            }
        }
        if (TypeUtil.hasSimpleInternalVertexKeyIndex(r)) {
            newVertexIndexEntries.add((ItinerisVertexProperty) r);
        }
    }

    public ItinerisVertexProperty addProperty(ItinerisVertex vertex, PropertyKey key, Object value) {
        return addProperty(vertex, key, value, null, 0L);
    }

    public ItinerisVertexProperty addProperty(ItinerisVertex vertex, PropertyKey key, Object value, Long id, Long previousId) {
        return addProperty(key.cardinality().convert(), vertex, key, value, id, previousId);
    }

    public ItinerisVertexProperty addProperty(VertexProperty.Cardinality cardinality, ItinerisVertex vertex, PropertyKey key, Object value) {
        return addProperty(cardinality, vertex, key, value, null, 0L);
    }

    public ItinerisVertexProperty addProperty(VertexProperty.Cardinality cardinality, ItinerisVertex vertex, PropertyKey key, Object value, Long id, Long previousId) {
        if (key.cardinality().convert()!=cardinality && cardinality!=VertexProperty.Cardinality.single)
            throw new SchemaViolationException("Key is defined for %s cardinality which conflicts with specified: %s",key.cardinality(),cardinality);
        verifyWriteAccess(vertex);
        Preconditions.checkArgument(!(key instanceof ImplicitKey),"Cannot create a property of implicit type: %s",key.name());
        vertex = ((InternalVertex) vertex).it();
        Preconditions.checkNotNull(key);
        checkPropertyConstraintForVertexOrCreatePropertyConstraint(vertex, key);
        final Object normalizedValue = verifyAttribute(key, value);
        Cardinality keyCardinality = key.cardinality();

        //Determine unique indexes
        final List<IndexLockTuple> uniqueIndexTuples = new ArrayList<>();
        for (CompositeIndexType index : TypeUtil.getUniqueIndexes(key)) {
            IndexRecords matches = IndexRecordUtil.indexMatches(vertex, index, key, normalizedValue);
            for (Object[] match : matches.getRecordValues()) uniqueIndexTuples.add(new IndexLockTuple(index,match));
        }

        TransactionLock uniqueLock = getUniquenessLock(vertex, (InternalRelationType) key, normalizedValue);
        //Add locks for unique indexes
        for (IndexLockTuple lockTuple : uniqueIndexTuples) uniqueLock = new CombinerLock(uniqueLock,getLock(lockTuple),times);
        uniqueLock.lock(LOCK_TIMEOUT);
        try {
//            //Check vertex-centric uniqueness -> this doesn't really make sense to check
//            if (config.hasVerifyUniqueness()) {
//                if (cardinality == Cardinality.SINGLE) {
//                    if (!Iterables.isEmpty(query(vertex).type(key).properties()))
//                        throw new SchemaViolationException("A property with the given key [%s] already exists on the vertex [%s] and the property key is defined as single-valued", key.name(), vertex);
//                }
//                if (cardinality == Cardinality.SET) {
//                    if (!Iterables.isEmpty(Iterables.filter(query(vertex).type(key).properties(), new Predicate<ItinerisVertexProperty>() {
//                        @Override
//                        public boolean apply(@Nullable ItinerisVertexProperty janusgraphProperty) {
//                            return normalizedValue.equals(janusgraphProperty.value());
//                        }
//                    })))
//                        throw new SchemaViolationException("A property with the given key [%s] and value [%s] already exists on the vertex and the property key is defined as set-valued", key.name(), normalizedValue);
//                }
//            }

            long propId = id == null ? IDManager.getTemporaryRelationID(temporaryIds.nextID()) : id;
            StandardVertexProperty prop = new StandardVertexProperty(propId, key, (InternalVertex) vertex, normalizedValue, ElementLifeCycle.New);
            if (previousId > 0) prop.setPreviousID(previousId);
            if (config.hasAssignIDsImmediately() && id == null) graph.assignID(prop);

            //Delete properties if the cardinality is restricted
            if (cardinality==VertexProperty.Cardinality.single || cardinality== VertexProperty.Cardinality.set) {
                Consumer<ItinerisVertexProperty> propertyRemover = ItinerisVertexProperty.getRemover(cardinality, normalizedValue);
                /* If we are simply overwriting a vertex property, then we don't have to explicitly remove it thereby saving a read operation
                   However, this only applies if
                   1) we don't lock on the property key or consistency checks are disabled and
                   2) there are no indexes for this property key
                   3) the cardinalities match (if we overwrite a set with single, we need to read all other values to delete)
                */

                if ( (!config.hasVerifyUniqueness() || ((InternalRelationType)key).getConsistencyModifier()!=ConsistencyModifier.LOCK) &&
                        !TypeUtil.hasAnyIndex(key) && cardinality==keyCardinality.convert()) {
                    //Only delete in-memory so as to not trigger a read from the database which isn't necessary because we will overwrite blindly
                    //We need to label the new property as "upsert", so that in case property deletion happens, we not only delete this new
                    //in-memory property, but also read from database to delete the old value (if exists)
                    ((InternalVertex) vertex).getDuplicatedAddedRelation(key, normalizedValue).forEach(p -> propertyRemover.accept((ItinerisVertexProperty) p));
                    prop.setUpsert(true);
                } else {
                    vertex.properties(key.name()).forEachRemaining(p -> propertyRemover.accept((ItinerisVertexProperty) p));
                }
            }

            //Check index uniqueness
            if (config.hasVerifyUniqueness()) {
                //Check all unique indexes
                for (IndexLockTuple lockTuple : uniqueIndexTuples) {
                    if (!Iterables.isEmpty(IndexHelper.getQueryResults(lockTuple.getIndex(), lockTuple.getAll(), this)))
                        throw new SchemaViolationException("Adding this property for key [%s] and value [%s] violates a uniqueness constraint [%s]", key.name(), normalizedValue, lockTuple.getIndex());
                }
            }
            connectRelation(prop);
            return prop;
        } finally {
            uniqueLock.unlock();
        }

    }


    @Override
    public Iterable<ItinerisEdge> getEdges(RelationIdentifier... ids) {
        verifyOpen();
        if (ids==null || ids.length==0) return new VertexCentricEdgeIterable(getInternalVertices(),RelationCategory.EDGE);

        if (null != config.getGroupName()) {
            MetricManager.INSTANCE.getCounter(config.getGroupName(), "db", "getEdgesByID").inc();
        }
        List<ItinerisEdge> result = new ArrayList<>(ids.length);
        for (RelationIdentifier id : ids) {
            if (id==null) continue;
            ItinerisEdge edge = RelationIdentifierUtils.findEdge(id,this);
            if (edge!=null && !edge.isRemoved()) result.add(edge);
        }
        return result;
    }


    /*
     * ------------------------------------ Schema Handling ------------------------------------
     */

    public final ItinerisSchemaVertex makeSchemaVertex(ItinerisSchemaCategory schemaCategory, String name, TypeDefinitionMap definition) {
        verifyOpen();
        Preconditions.checkArgument(!schemaCategory.hasName() || StringUtils.isNotBlank(name), "Need to provide a valid name for type [%s]", schemaCategory);
        schemaCategory.verifyValidDefinition(definition);
        ItinerisSchemaVertex schemaVertex;
        if (schemaCategory.isRelationType()) {
            if (schemaCategory == ItinerisSchemaCategory.PROPERTYKEY) {
                schemaVertex = new PropertyKeyVertex(this, IDManager.getTemporaryVertexID(IDManager.VertexIDType.UserPropertyKey, temporaryIds.nextID()), ElementLifeCycle.New);
            } else {
                assert schemaCategory == ItinerisSchemaCategory.EDGELABEL;
                schemaVertex = new EdgeLabelVertex(this, IDManager.getTemporaryVertexID(IDManager.VertexIDType.UserEdgeLabel,temporaryIds.nextID()), ElementLifeCycle.New);
            }
        } else if (schemaCategory==ItinerisSchemaCategory.VERTEXLABEL) {
            schemaVertex = new VertexLabelVertex(this, IDManager.getTemporaryVertexID(IDManager.VertexIDType.GenericSchemaType,temporaryIds.nextID()), ElementLifeCycle.New);
        } else {
            schemaVertex = new ItinerisSchemaVertex(this, IDManager.getTemporaryVertexID(IDManager.VertexIDType.GenericSchemaType,temporaryIds.nextID()), ElementLifeCycle.New);
        }

        graph.assignID(schemaVertex, BaseVertexLabel.DEFAULT_VERTEXLABEL);
        Preconditions.checkArgument(schemaVertex.longId() > 0);
        if (schemaCategory.hasName()) addProperty(schemaVertex, BaseKey.SchemaName, schemaCategory.getSchemaName(name));
        addProperty(schemaVertex, BaseKey.VertexExists, Boolean.TRUE);
        addProperty(schemaVertex, BaseKey.SchemaCategory, schemaCategory);
        updateSchemaVertex(schemaVertex);
        for (Map.Entry<TypeDefinitionCategory,Object> def : definition.entrySet()) {
            ItinerisVertexProperty p = addProperty(schemaVertex, BaseKey.SchemaDefinitionProperty, def.getValue());
            p.property(BaseKey.SchemaDefinitionDesc.name(), TypeDefinitionDescription.of(def.getKey()));
        }
        vertexCache.add(schemaVertex, schemaVertex.longId());
        if (schemaCategory.hasName()) newTypeCache.put(schemaCategory.getSchemaName(name), schemaVertex.longId());
        return schemaVertex;

    }

    public void updateSchemaVertex(ItinerisSchemaVertex schemaVertex) {
        addProperty(VertexProperty.Cardinality.single, schemaVertex, BaseKey.SchemaUpdateTime, times.getTime(times.getTime()));
    }

    public PropertyKey makePropertyKey(String name, TypeDefinitionMap definition) {
        return (PropertyKey) makeSchemaVertex(ItinerisSchemaCategory.PROPERTYKEY, name, definition);
    }

    public EdgeLabel makeEdgeLabel(String name, TypeDefinitionMap definition) {
        return (EdgeLabel) makeSchemaVertex(ItinerisSchemaCategory.EDGELABEL, name, definition);
    }


    public ItinerisEdge addSchemaEdge(ItinerisVertex out, ItinerisVertex in, TypeDefinitionCategory def, Object modifier) {
        assert def.isEdge();
        ItinerisEdge edge = addEdge(out, in, BaseLabel.SchemaDefinitionEdge);
        TypeDefinitionDescription desc = new TypeDefinitionDescription(def, modifier);
        edge.property(BaseKey.SchemaDefinitionDesc.name(), desc);
        return edge;
    }

    @Override
    public VertexLabel addProperties(VertexLabel vertexLabel, PropertyKey... keys) {
        for (PropertyKey key : keys) {
            addSchemaEdge(vertexLabel, key, TypeDefinitionCategory.PROPERTY_KEY_EDGE, null);
        }
        return vertexLabel;
    }

    @Override
    public EdgeLabel addProperties(EdgeLabel edgeLabel, PropertyKey... keys) {
        for (PropertyKey key : keys) {
            if (key.cardinality() != Cardinality.SINGLE) {
                throw new IllegalArgumentException(String.format("An Edge [%s] can not have a property [%s] with the cardinality [%s].", edgeLabel, key, key.cardinality()));
            }
            addSchemaEdge(edgeLabel, key, TypeDefinitionCategory.PROPERTY_KEY_EDGE, null);
        }
        return edgeLabel;
    }

    @Override
    public EdgeLabel addConnection(EdgeLabel edgeLabel, VertexLabel outVLabel, VertexLabel inVLabel) {
        addSchemaEdge(outVLabel, inVLabel, TypeDefinitionCategory.CONNECTION_EDGE, edgeLabel.name());
        addSchemaEdge(edgeLabel, outVLabel, TypeDefinitionCategory.UPDATE_CONNECTION_EDGE, null);
        return edgeLabel;
    }

    public ItinerisSchemaVertex getSchemaVertex(String schemaName) {
        Long schemaId = newTypeCache.get(schemaName);
        if (schemaId==null) schemaId=graph.getSchemaCache().getSchemaId(schemaName);
        if (schemaId != null) {
            InternalVertex typeVertex = vertexCache.get(schemaId, existingVertexRetriever);
            assert typeVertex!=null;
            return (ItinerisSchemaVertex)typeVertex;
        } else return null;
    }

    @Override
    public boolean containsRelationType(String name) {
        return getRelationType(name)!=null;
    }

    @Override
    public RelationType getRelationType(String name) {
        verifyOpen();

        RelationType type = SystemTypeManager.getSystemType(name);
        if (type!=null) return type;

        return (RelationType)getSchemaVertex(ItinerisSchemaCategory.getRelationTypeName(name));
    }

    @Override
    public boolean containsPropertyKey(String name) {
        RelationType type = getRelationType(name);
        return type!=null && type.isPropertyKey();
    }

    @Override
    public boolean containsEdgeLabel(String name) {
        RelationType type = getRelationType(name);
        return type!=null && type.isEdgeLabel();
    }

    // this is critical path we can't allow anything heavier than assertion in here
    @Override
    public RelationType getExistingRelationType(long typeId) {
        assert idInspector.isRelationTypeId(typeId);
        if (IDManager.isSystemRelationTypeId(typeId)) {
            return SystemTypeManager.getSystemType(typeId);
        } else {
            InternalVertex v = getInternalVertex(typeId);
            return (RelationType) v;
        }
    }

    @Override
    public PropertyKey getPropertyKey(String name) {
        RelationType pk = getRelationType(name);
        Preconditions.checkArgument(pk==null || pk.isPropertyKey(), "The relation type with name [%s] is not a property key",name);
        return (PropertyKey)pk;
    }

    @Override
    public PropertyKey getOrCreatePropertyKey(String name, Object value) {
        return getOrCreatePropertyKey(name, value, null);
    }

    @Override
    public PropertyKey getOrCreatePropertyKey(String name, Object value, VertexProperty.Cardinality cardinality) {
        RelationType et = getRelationType(name);
        if (et == null) {
            PropertyKeyMaker propertyKeyMaker = makePropertyKey(name);
            if (cardinality != null) {
                propertyKeyMaker = propertyKeyMaker.cardinality(cardinality);
            }
            return config.getAutoSchemaMaker().makePropertyKey(propertyKeyMaker, value);
        } else if (et.isPropertyKey()) {
            return (PropertyKey) et;
        } else {
            throw new IllegalArgumentException("The type of given name is not a key: " + name);
        }
    }

    @Override
    public PropertyKey getOrCreatePropertyKey(String name) {
        RelationType et = getRelationType(name);
        if (et == null) {
            return config.getAutoSchemaMaker().makePropertyKey(makePropertyKey(name));
        } else if (et.isPropertyKey()) {
            return (PropertyKey) et;
        } else {
            throw new IllegalArgumentException("The type of given name is not a key: " + name);
        }
    }

    @Override
    public EdgeLabel getEdgeLabel(String name) {
        RelationType el = getRelationType(name);
        Preconditions.checkArgument(el==null || el.isEdgeLabel(), "The relation type with name [%s] is not an edge label",name);
        return (EdgeLabel)el;
    }

    @Override
    public EdgeLabel getOrCreateEdgeLabel(String name) {
        RelationType et = getRelationType(name);
        if (et == null) {
            return config.getAutoSchemaMaker().makeEdgeLabel(makeEdgeLabel(name));
        } else if (et.isEdgeLabel()) {
            return (EdgeLabel) et;
        } else
            throw new IllegalArgumentException("The type of given name is not a label: " + name);
    }

    @Override
    public PropertyKeyMaker makePropertyKey(String name) {
        return new StandardPropertyKeyMaker(this, name, indexSerializer, attributeHandler);
    }

    @Override
    public EdgeLabelMaker makeEdgeLabel(String name) {
        return new StandardEdgeLabelMaker(this, name, indexSerializer, attributeHandler);
    }

    //-------- Vertex Labels -----------------

    @Override
    public VertexLabel getExistingVertexLabel(long id) {
        assert idInspector.isVertexLabelVertexId(id);
        InternalVertex v = getInternalVertex(id);
        return (VertexLabelVertex)v;
    }

    @Override
    public boolean containsVertexLabel(String name) {
        verifyOpen();
        return BaseVertexLabel.DEFAULT_VERTEXLABEL.name().equals(name) || getSchemaVertex(ItinerisSchemaCategory.VERTEXLABEL.getSchemaName(name)) != null;

    }

    @Override
    public VertexLabel getVertexLabel(String name) {
        verifyOpen();
        if (BaseVertexLabel.DEFAULT_VERTEXLABEL.name().equals(name)) return BaseVertexLabel.DEFAULT_VERTEXLABEL;
        return (VertexLabel)getSchemaVertex(ItinerisSchemaCategory.VERTEXLABEL.getSchemaName(name));
    }

    @Override
    public VertexLabel getOrCreateVertexLabel(String name) {
        VertexLabel vertexLabel = getVertexLabel(name);
        if (vertexLabel==null) {
            vertexLabel = config.getAutoSchemaMaker().makeVertexLabel(makeVertexLabel(name));
        }
        return vertexLabel;
    }

    @Override
    public VertexLabelMaker makeVertexLabel(String name) {
        StandardVertexLabelMaker maker = new StandardVertexLabelMaker(this);
        maker.name(name);
        return maker;
    }
    /*
     * ------------------------------------ Query Answering ------------------------------------
     */

    public VertexCentricQueryBuilder query(ItinerisVertex vertex) {
        return new VertexCentricQueryBuilder(((InternalVertex) vertex).it());
    }

    @Override
    public ItinerisMultiVertexQuery multiQuery(ItinerisVertex... vertices) {
        MultiVertexCentricQueryBuilder builder = vertices.length == 0 ?
            new MultiVertexCentricQueryBuilder(this) :
            new MultiVertexCentricQueryBuilder(this, vertices.length);
        for (ItinerisVertex v : vertices) builder.addVertex(v);
        return builder;
    }

    @Override
    public ItinerisMultiVertexQuery multiQuery(Collection<ItinerisVertex> vertices) {
        MultiVertexCentricQueryBuilder builder = new MultiVertexCentricQueryBuilder(this, vertices.size());
        builder.addAllVertices(vertices);
        return builder;
    }

    public void executeMultiQuery(final Collection<InternalVertex> vertices, final SliceQuery sq, final QueryProfiler profiler) {
        List<Object> vertexIds = new ArrayList<>(vertices.size());
        for (InternalVertex v : vertices) {
            if (!v.isNew() && v.hasId() && (v instanceof CacheVertex) && !v.hasLoadedRelations(sq)) vertexIds.add(v.id());
        }

        if (!vertexIds.isEmpty()) {
            List<EntryList> results = QueryProfiler.profile(profiler, sq, true, q -> graph.edgeMultiQuery(vertexIds, q, txHandle));
            int pos = 0;
            for (ItinerisVertex v : vertices) {
                if (pos<vertexIds.size() && vertexIds.get(pos).equals(v.id())) {
                    final EntryList vresults = results.get(pos);
                    ((CacheVertex) v).loadRelations(sq, query -> vresults);
                    pos++;
                }
            }
        }
    }

    public void executeMultiSliceMultiQuery(final Collection<InternalVertex> vertices, final List<BackendQueryHolder<SliceQuery>> queries, QueryProfiler profiler) {
        MultiKeysQueryGroups<Object, SliceQuery> groupedMultiSliceQueries = MultiSliceQueriesGroupingUtil.toMultiKeysQueryGroups(vertices, queries);
        if (!groupedMultiSliceQueries.getQueryGroups().isEmpty()) {
            Map<SliceQuery, Map<Object, EntryList>> allResults = QueryProfiler.profile(profiler, groupedMultiSliceQueries, true, q -> graph.edgeMultiQuery(q, txHandle));
            Map<Object, ItinerisVertex> vertexIdToVertexMap = vertices.stream().collect(Collectors.toMap(ItinerisElement::id, v -> v));
            allResults.forEach((sliceQuery, resultsPerQuery) -> resultsPerQuery.forEach((vertexId, vertexSliceResult) ->
                ((CacheVertex) vertexIdToVertexMap.get(vertexId)).loadRelations(sliceQuery, query -> vertexSliceResult)));
        }
    }

    public final QueryExecutor<VertexCentricQuery, ItinerisRelation, SliceQuery> edgeProcessor;

    public final QueryExecutor<VertexCentricQuery, ItinerisRelation, SliceQuery> edgeProcessorImpl = new QueryExecutor<VertexCentricQuery, ItinerisRelation, SliceQuery>() {

        @Override
        public Iterator<ItinerisRelation> getNew(final VertexCentricQuery query) {
            InternalVertex vertex = query.getVertex();
            if (vertex.isNew() || vertex.hasAddedRelations()) {
                return getMatchedRelations(query, vertex);
            } else {
                return Collections.emptyIterator();
            }
        }

        private Iterator<ItinerisRelation> getMatchedRelations(final VertexCentricQuery query, final InternalVertex vertex) {
            // Need to filter out self-loops if query only asks for one direction
            return new Iterator<ItinerisRelation>() {
                Iterator<InternalRelation> iterator = vertex.getAddedRelations(t -> true).iterator();
                InternalRelation loop = null;
                InternalRelation current = null;

                @Override
                public boolean hasNext() {
                    if (current == null) {
                        current = nextRelation();
                        return current != null;
                    }
                    return true;
                }

                @Override
                public ItinerisRelation next() {
                    if (hasNext()) {
                        InternalRelation current = this.current;
                        this.current = null;
                        return current;
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                private InternalRelation nextRelation() {
                    if (loop != null) {
                        InternalRelation loop = this.loop;
                        this.loop = null;
                        return loop;
                    }
                    while (iterator.hasNext()) {
                        InternalRelation next = iterator.next();
                        if (query.matches(next)) {
                            if (query.getDirection() == Direction.BOTH && next instanceof ItinerisEdge && next.isLoop()) {
                                loop = next;
                            }
                            return next;
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public boolean hasDeletions(VertexCentricQuery query) {
            InternalVertex vertex = query.getVertex();
            if (vertex.isNew()) return false;
            //In addition to deleted, we need to also check for added relations since those can potentially
            //replace existing ones due to a multiplicity constraint
            return vertex.hasRemovedRelations() || vertex.hasAddedRelations();
        }

        @Override
        public boolean isDeleted(VertexCentricQuery query, ItinerisRelation result) {
            if (deletedRelations.containsKey(result.longId()) || result != ((InternalRelation) result).it())
                return true;
            //Check if this relation is replaced by an added one due to a multiplicity constraint
            InternalRelationType type = (InternalRelationType) result.getType();
            InternalVertex vertex = query.getVertex();
            if (type.multiplicity().isConstrained() && vertex.hasAddedRelations()) {
                final RelationComparator comparator = new RelationComparator(vertex);
                final Predicate<InternalRelation> predicate = internalRelation -> comparator.compare((InternalRelation) result, internalRelation) == 0;
                if (result.isProperty()) {
                    //Search only in added properties, this might be faster in case of high number of edges
                    return !Iterables.isEmpty(vertex.findAddedProperty(predicate));
                } else {
                    //Search in all added relations
                    return !Iterables.isEmpty(vertex.getAddedRelations(predicate));
                }
            }
            return false;
        }

        @Override
        public Iterator<ItinerisRelation> execute(final VertexCentricQuery query, final SliceQuery sq, final Object exeInfo, final QueryProfiler profiler) {
            assert exeInfo==null;
            if (query.getVertex().isNew())
                return Collections.emptyIterator();

            final InternalVertex v = query.getVertex();

            final EntryList iterable = v.loadRelations(sq, query1 -> QueryProfiler.profile(profiler, query1, q -> graph.edgeQuery(v.id(), q, txHandle)));

            return RelationConstructor.readRelation(v, iterable, StandardItinerisTx.this).iterator();
        }
    };

    public final QueryExecutor<GraphCentricQuery, ItinerisElement, JointIndexQuery> elementProcessor;

    public final QueryExecutor<GraphCentricQuery, ItinerisElement, JointIndexQuery> elementProcessorImpl = new QueryExecutor<GraphCentricQuery, ItinerisElement, JointIndexQuery>() {

        private PredicateCondition<PropertyKey, ItinerisElement> getEqualityCondition(Condition<ItinerisElement> condition) {
            if (condition instanceof PredicateCondition) {
                final PredicateCondition<PropertyKey, ItinerisElement> pc = (PredicateCondition) condition;
                if (pc.getPredicate() == Cmp.EQUAL && TypeUtil.hasSimpleInternalVertexKeyIndex(pc.getKey())) return pc;
            } else if (condition instanceof And) {
                for (final Condition<ItinerisElement> child : condition.getChildren()) {
                    final PredicateCondition<PropertyKey, ItinerisElement> p = getEqualityCondition(child);
                    if (p != null) return p;
                }
            }
            return null;
        }


        @Override
        public Iterator<ItinerisElement> getNew(final GraphCentricQuery query) {
            //If the query is unconstrained then we don't need to add new elements, so will be picked up by getVertices()/getEdges() below
            if (query.numSubQueries()==1 && query.getSubQuery(0).getBackendQuery().isEmpty())
                return Collections.emptyIterator();
            Preconditions.checkArgument(query.getCondition().hasChildren(),"If the query is non-empty it needs to have a condition");

            if (query.getResultType() == ElementCategory.VERTEX && hasModifications()) {
                Preconditions.checkArgument(QueryUtil.isQueryNormalForm(query.getCondition()));
                PredicateCondition<PropertyKey, ItinerisElement> standardIndexKey = getEqualityCondition(query.getCondition());
                Iterator<ItinerisVertex> vertices;
                if (standardIndexKey == null) {
                    final Set<PropertyKey> keys = new HashSet<>();
                    ConditionUtil.traversal(query.getCondition(), cond -> {
                        Preconditions.checkArgument(cond.getType() != Condition.Type.LITERAL || cond instanceof PredicateCondition);
                        if (cond instanceof PredicateCondition) {
                            keys.add(((PredicateCondition<PropertyKey, ItinerisElement>) cond).getKey());
                        }
                        return true;
                    });
                    Preconditions.checkArgument(!keys.isEmpty(), "Invalid query condition: %s", query.getCondition());
                    Set<ItinerisVertex> vertexSet = new HashSet<>();
                    for (final ItinerisRelation r : addedRelations.getView(relation -> keys.contains(relation.getType()))) {
                        vertexSet.add(((ItinerisVertexProperty) r).element());
                    }
                    for (ItinerisRelation r : deletedRelations.values()) {
                        if (keys.contains(r.getType())) {
                            ItinerisVertex v = ((ItinerisVertexProperty) r).element();
                            if (!v.isRemoved()) vertexSet.add(v);
                        }
                    }
                    vertices = vertexSet.iterator();
                } else {
                    vertices = com.google.common.collect.Iterators.transform(newVertexIndexEntries.get(standardIndexKey.getValue(), standardIndexKey.getKey()).iterator(), new com.google.common.base.Function<ItinerisVertexProperty, ItinerisVertex>() {
                        @Nullable
                        @Override
                        public ItinerisVertex apply(final ItinerisVertexProperty o) {
                            return o.element();
                        }
                    });
                }

                return (Iterator) com.google.common.collect.Iterators.filter(vertices, query::matches);
            } else if ( (query.getResultType() == ElementCategory.EDGE || query.getResultType()==ElementCategory.PROPERTY)
                                        && !addedRelations.isEmpty()) {
                return (Iterator) addedRelations.getView(relation -> query.getResultType().isInstance(relation) && !relation.isInvisible() && query.matches(relation)).iterator();
            } else return Collections.emptyIterator();
        }


        @Override
        public boolean hasDeletions(GraphCentricQuery query) {
            return hasModifications();
        }

        @Override
        public boolean isDeleted(GraphCentricQuery query, ItinerisElement result) {
            if (result == null || result.isRemoved()) return true;
            else if (query.getResultType() == ElementCategory.VERTEX) {
                Preconditions.checkArgument(result instanceof InternalVertex);
                InternalVertex v = ((InternalVertex) result).it();
                return (v.hasAddedRelations() || v.hasRemovedRelations()) && !query.matches(result);
            } else if (query.getResultType() == ElementCategory.EDGE || query.getResultType()==ElementCategory.PROPERTY) {
                Preconditions.checkArgument(result.isLoaded() || result.isNew());
                //Loaded relations are immutable so we don't need to check those
                //New relations could be modified in this transaction to now longer match the query, hence we need to
                //check for this case and consider the relations deleted
                return result.isNew() && !query.matches(result);
            } else throw new IllegalArgumentException("Unexpected type: " + query.getResultType());
        }

        @Override
        public Iterator<ItinerisElement> execute(final GraphCentricQuery query, final JointIndexQuery indexQuery, final Object exeInfo, final QueryProfiler profiler) {
            Iterator<ItinerisElement> iterator;
            if (!indexQuery.isEmpty()) {
                final List<QueryUtil.IndexCall<Object>> retrievals = new ArrayList<>();
                // Leave first index for streaming, and prepare the rest for intersecting and lookup
                for (int i = 1; i < indexQuery.size(); i++) {
                    final JointIndexQuery.Subquery subquery = indexQuery.getQuery(i);
                    retrievals.add(limit -> {
                        final JointIndexQuery.Subquery adjustedQuery = subquery.updateLimit(limit);
                        try {
                            return indexCache.get(adjustedQuery,
                                () -> QueryProfiler.profile(subquery.getProfiler(), adjustedQuery, q -> indexSerializer.query(q, txHandle, StandardItinerisTx.this).collect(Collectors.toList())));
                        } catch (Exception e) {
                            throw new ItinerisException("Could not call index", e);
                        }
                    });
                }
                // Constructs an iterator which lazily streams results from 1st index, and filters by looking up in the intersection of results from all other indices (if any)
                // NOTE NO_LIMIT is passed to processIntersectingRetrievals to prevent incomplete intersections, which could lead to missed results
                iterator = new SubqueryIterator(indexQuery.getQuery(0), indexSerializer, txHandle, StandardItinerisTx.this, indexCache, indexQuery.getLimit(), getConversionFunction(query.getResultType()),
                        retrievals.isEmpty() ? null: QueryUtil.processIntersectingRetrievals(retrievals, Query.NO_LIMIT));
            } else {
                if (config.hasForceIndexUsage()) throw new ItinerisException("Could not find a suitable index to answer graph query and graph scans are disabled: " + query);
                log.warn("Query requires iterating over all vertices [{}]. For better performance, use indexes", query.getCondition());

                QueryProfiler sub = profiler.addNested("scan");
                sub.setAnnotation(QueryProfiler.QUERY_ANNOTATION,indexQuery);
                sub.setAnnotation(QueryProfiler.FULLSCAN_ANNOTATION,true);
                sub.setAnnotation(QueryProfiler.CONDITION_ANNOTATION,query.getResultType());

                Supplier<Iterator<ItinerisElement>> iteratorSupplier;
                switch (query.getResultType()) {
                    case VERTEX:
                        iteratorSupplier = () -> (Iterator) getVertices().iterator();
                        break;
                    case EDGE:
                        iteratorSupplier = () -> (Iterator) getEdges().iterator();
                        break;
                    case PROPERTY:
                        iteratorSupplier = () -> new VertexCentricEdgeIterable(getInternalVertices(),RelationCategory.PROPERTY).iterator();
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected type: " + query.getResultType());
                }
                return new ProfiledIterator<>(sub, iteratorSupplier);
            }

            return iterator;
        }

    };

    public Function<Object, ? extends ItinerisElement> getConversionFunction(final ElementCategory elementCategory) {
        switch (elementCategory) {
            case VERTEX:
                return vertexIDConversionFct;
            case EDGE:
                return edgeIDConversionFct;
            case PROPERTY:
                return propertyIDConversionFct;
            default:
                throw new IllegalArgumentException("Unexpected result type: " + elementCategory);
        }
    }

    private final Function<Object, ItinerisVertex> vertexIDConversionFct = id -> {
        Preconditions.checkNotNull(id);
        if (id instanceof VertexWithInlineProps) {
            VertexWithInlineProps v = (VertexWithInlineProps) id;
            InternalVertex vertex = getInternalVertex(v.getVertexId());
            if (vertex instanceof CacheVertex) {
                v.getInlineProperties().forEach((sq, entryList) -> ((CacheVertex) vertex).addToQueryCache(sq, entryList));
            }
            return vertex;
        } else {
            return getInternalVertex(id);
        }
    };

    private final Function<Object, ItinerisEdge> edgeIDConversionFct = id -> {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(id instanceof RelationIdentifier);
        return RelationIdentifierUtils.findEdge((RelationIdentifier)id, StandardItinerisTx.this);
    };

    private final Function<Object, ItinerisVertexProperty> propertyIDConversionFct = id -> {
        Preconditions.checkNotNull(id);
        Preconditions.checkArgument(id instanceof RelationIdentifier);
        return RelationIdentifierUtils.findProperty((RelationIdentifier)id, StandardItinerisTx.this);
    };

    @Override
    public GraphCentricQueryBuilder query() {
        return new GraphCentricQueryBuilder(this, graph.getIndexSerializer(), indexSelector);
    }

    @Override
    public MixedIndexAggQuery mixedIndexAggQuery() {
        return new MixedIndexAggQueryBuilder(indexSerializer, txHandle);
    }

    @Override
    public ItinerisIndexQuery indexQuery(String indexName, String query) {
        return new IndexQueryBuilder(this,indexSerializer).setIndex(indexName).setQuery(query);
    }

    /*
     * ------------------------------------ Transaction State ------------------------------------
     */

    @Override
    public synchronized void commit() {
        Preconditions.checkArgument(isOpen(), "The transaction has already been closed");
        boolean success = false;
        if (null != config.getGroupName()) {
            MetricManager.INSTANCE.getCounter(config.getGroupName(), "tx", "commit").inc();
        }
        try {
            if (hasModifications()) {
                graph.commit(addedRelations.getAllUnsafe(), deletedRelations.values(), this);
            } else {
                txHandle.commit();
            }
            success = true;
        } catch (Exception e) {
            try {
                txHandle.rollback();
            } catch (BackendException e1) {
                throw new ItinerisException("Could not rollback after a failed commit", e);
            }
            throw new ItinerisException("Could not commit transaction due to exception during persistence", e);
        } finally {
            releaseTransaction();
            if (null != config.getGroupName() && !success) {
                MetricManager.INSTANCE.getCounter(config.getGroupName(), "tx", "commit.exceptions").inc();
            }
        }
    }

    @Override
    public synchronized void rollback() {
        Preconditions.checkArgument(isOpen(), "The transaction has already been closed");
        boolean success = false;
        if (null != config.getGroupName()) {
            MetricManager.INSTANCE.getCounter(config.getGroupName(), "tx", "rollback").inc();
        }
        try {
            txHandle.rollback();
            success = true;
        } catch (Exception e) {
            throw new ItinerisException("Could not rollback transaction due to exception", e);
        } finally {
            releaseTransaction();
            if (null != config.getGroupName() && !success) {
                MetricManager.INSTANCE.getCounter(config.getGroupName(), "tx", "rollback.exceptions").inc();
            }
        }
    }

    private void releaseTransaction() {
        isOpen = false;
        graph.closeTransaction(this);
        vertexCache = EmptyVertexCache.getInstance();
        indexCache = EmptySubqueryCache.getInstance();
        addedRelations = EmptyAddedRelations.getInstance();
        deletedRelations = Collections.emptyMap();
        uniqueLocks = Collections.emptyMap();
        newVertexIndexEntries = EmptyIndexCache.getInstance();
        newTypeCache = Collections.emptyMap();
    }

    @Override
    public final boolean isOpen() {
        return isOpen;
    }

    @Override
    public final boolean isClosed() {
        return !isOpen;
    }

    @Override
    public boolean hasModifications() {
        return !addedRelations.isEmpty() || !deletedRelations.isEmpty();
    }

    @Override
    public void expireSchemaElement(final long id) {
        if (vertexCache.contains(id)) {
            final InternalVertex v = vertexCache.get(id, externalVertexRetriever);
            if (v instanceof ItinerisSchemaVertex) {
                ItinerisSchemaVertex sv = (ItinerisSchemaVertex) v;
                sv.resetCache();
                if (sv.getDefinition().containsKey(TypeDefinitionCategory.INTERNAL_INDEX)) {
                    IndexType indexType = sv.asIndexType();
                    if (indexType.isMixedIndex()) {
                        // Invalidate mixed index
                        String store = indexType.getBackingIndexName();
                        if (txHandle.hasIndexTransaction(store)) {
                            IndexTransaction indexTx = txHandle.getIndexTransaction(store);
                            indexTx.invalidate(sv.name());
                        }
                    }
                }
            }
        }
    }
}
