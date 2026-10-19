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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
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
import com.forwardmeasure.itineris.core.attribute.Geo;
import com.forwardmeasure.itineris.core.attribute.Text;
import com.forwardmeasure.itineris.core.schema.CompositeIndexInfo;
import com.forwardmeasure.itineris.core.schema.ConsistencyModifier;
import com.forwardmeasure.itineris.core.schema.EdgeLabelMaker;
import com.forwardmeasure.itineris.core.schema.ItinerisSchemaElement;
import com.forwardmeasure.itineris.core.schema.Mapping;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.core.schema.PropertyKeyMaker;
import com.forwardmeasure.itineris.core.schema.SchemaInspector;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.core.schema.VertexLabelMaker;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BackendTransaction;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexTransaction;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.MultiKeysQueryGroups;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.util.StaticArrayBuffer;
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
import com.forwardmeasure.itineris.graphdb.internal.Order;
import com.forwardmeasure.itineris.graphdb.internal.OrderList;
import com.forwardmeasure.itineris.graphdb.internal.RelationCategory;
import com.forwardmeasure.itineris.graphdb.query.BackendQueryHolder;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicate;
import com.forwardmeasure.itineris.graphdb.query.MetricsQueryExecutor;
import com.forwardmeasure.itineris.graphdb.query.Query;
import com.forwardmeasure.itineris.graphdb.query.QueryExecutor;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.query.condition.And;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.Or;
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
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;
import com.forwardmeasure.itineris.graphdb.types.ParameterIndexField;
import com.forwardmeasure.itineris.graphdb.types.ParameterType;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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

    /**
     * Keeps track of canonical ids of vertices that have been fully removed in this transaction
     */
    private volatile Set<Object> removedVertexCanonicalIds = null;

    //######## Index Caches
    /**
     * Caches the result of index calls so that repeated index queries don't need
     * to be passed to the IndexProvider. This cache will drop entries when it overflows
     * since the result set can always be retrieved from the IndexProvider
     */
    private SubqueryCache indexCache;
    /**
     * The vertex properties which this transaction added, by key and value, of the keys its graph queries look up, from
     * which a graph query finds the vertices which the transaction changed so that they may now match
     */
    private IndexCache addedPropertyIndex;

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
            addedPropertyIndex = new SimpleIndexCache(addedRelations);
        } else {
            addedRelations = new ConcurrentAddedRelations(false);
            newTypeCache = new NonBlockingHashMap<>();
            addedPropertyIndex = new ConcurrentIndexCache(addedRelations);
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
        //Filter out potentially removed vertices. A concurrent transaction release (e.g. graph.close() on
        //another thread) can swap the vertexCache to an EmptyVertexCache whose get() returns null after this
        //method has already passed verifyOpen(), so guard against null entries the same way getVertex() does.
        result.removeIf(v -> v == null || v.isRemoved());
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

    /**
     * Whether this transaction already holds the vertex with the given id, because it was created or loaded in this
     * transaction. Never accesses storage.
     */
    public boolean isVertexCached(Object vertexId) {
        return vertexCache.contains(vertexId);
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
            if (relation instanceof ItinerisVertexProperty) {
                addedPropertyIndex.remove((ItinerisVertexProperty) relation);
            }
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

    /** Records that the given (loaded) vertex has been fully removed in this transaction, keyed by its canonical id, so commit can issue a single whole-row deletion instead of per-column deletes. */
    public void recordRemovedVertex(InternalVertex vertex) {
        if (vertex.isNew()) return;
        final Object canonicalId = idInspector.isPartitionedVertex(vertex.id())
            ? idManager.getCanonicalVertexId(((Number) vertex.id()).longValue())
            : vertex.id();
        Set<Object> result = removedVertexCanonicalIds;
        if (result == null) {
            if (config.isSingleThreaded()) {
                removedVertexCanonicalIds = result = new HashSet<>();
            } else {
                synchronized (this) {
                    result = removedVertexCanonicalIds;
                    if (result == null) removedVertexCanonicalIds = result = ConcurrentHashMap.newKeySet();
                }
            }
        }
        result.add(canonicalId);
    }

    /** Returns true if the vertex with the given canonical id was fully removed in this transaction (see {@link #recordRemovedVertex}). */
    public boolean isVertexFullyRemoved(Object canonicalId) {
        return removedVertexCanonicalIds != null && removedVertexCanonicalIds.contains(canonicalId);
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
        if (r instanceof ItinerisVertexProperty) {
            addedPropertyIndex.add((ItinerisVertexProperty) r);
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
//                        public boolean apply(@Nullable ItinerisVertexProperty itinerisProperty) {
//                            return normalizedValue.equals(itinerisProperty.value());
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
        addPropertyMappings(vertexLabel, keys, key -> { });
        return vertexLabel;
    }

    @Override
    public EdgeLabel addProperties(EdgeLabel edgeLabel, PropertyKey... keys) {
        addPropertyMappings(edgeLabel, keys, key -> {
            if (key.cardinality() != Cardinality.SINGLE) {
                throw new IllegalArgumentException(String.format("An Edge [%s] can not have a property [%s] with the cardinality [%s].", edgeLabel, key, key.cardinality()));
            }
        });
        return edgeLabel;
    }

    //Writes the mapping of each key which the label doesn't have yet: mappedProperties() lists each copy. A single key,
    //which the schema maker gives for each element it checks, is looked up by a query of its own. For several keys, one
    //query reads all of the label's mappings, rather than one query, and a slice read, per key. Both find the mappings
    //which this transaction wrote too
    private void addPropertyMappings(ItinerisVertex label, PropertyKey[] keys, Consumer<PropertyKey> verifyKey) {
        Set<Object> mapped = null;
        for (PropertyKey key : keys) {
            verifyKey.accept(key);
            verifyWriteAccess(label, key);
            final boolean has;
            if (keys.length == 1) {
                has = hasSchemaEdge(label, key, TypeDefinitionCategory.PROPERTY_KEY_EDGE, null);
            } else {
                if (mapped == null) mapped = mappedKeyIds(label);
                has = !mapped.add(key.id());
            }
            if (!has) {
                addSchemaEdge(label, key, TypeDefinitionCategory.PROPERTY_KEY_EDGE, null);
            }
        }
    }

    //The ids of the keys which the label maps, committed or written by this transaction
    private static Set<Object> mappedKeyIds(ItinerisVertex label) {
        final Set<Object> ids = new HashSet<>();
        if (label instanceof ItinerisSchemaVertex) {
            for (ItinerisEdge edge : ((ItinerisSchemaVertex) label).getEdges(
                    TypeDefinitionCategory.PROPERTY_KEY_EDGE, Direction.OUT)) {
                ids.add(edge.vertex(Direction.IN).id());
            }
        }
        return ids;
    }

    @Override
    public EdgeLabel addConnection(EdgeLabel edgeLabel, VertexLabel outVLabel, VertexLabel inVLabel) {
        //Checked first, as writing the edges would check them, so that a connection the schema has already fails
        //where one it lacks would
        verifyWriteAccess(outVLabel, inVLabel, edgeLabel);
        //The connection, and the edge label's link to the outgoing vertex label through which the edge label finds its
        //connections, are written only where the schema hasn't got them: mappedConnections() lists each copy of a
        //connection, and a copy of a link only takes space
        final String name = edgeLabel.name();
        if (!hasSchemaEdge(outVLabel, inVLabel, TypeDefinitionCategory.CONNECTION_EDGE, name)) {
            addSchemaEdge(outVLabel, inVLabel, TypeDefinitionCategory.CONNECTION_EDGE, name);
        }
        if (!hasSchemaEdge(edgeLabel, outVLabel, TypeDefinitionCategory.UPDATE_CONNECTION_EDGE, null)) {
            addSchemaEdge(edgeLabel, outVLabel, TypeDefinitionCategory.UPDATE_CONNECTION_EDGE, null);
        }
        return edgeLabel;
    }

    //Whether the schema vertex has a definition edge of the category to the other one, of the given modifier unless it
    //is null. The query finds the edges this transaction wrote too, which the cached definitions (getRelated) don't
    private static boolean hasSchemaEdge(ItinerisVertex out, ItinerisVertex in, TypeDefinitionCategory def,
                                         Object modifier) {
        if (!(out instanceof ItinerisSchemaVertex) || !(in instanceof ItinerisSchemaVertex)) return false;
        for (ItinerisEdge edge : ((ItinerisSchemaVertex) out).getEdges(def, Direction.OUT,
                (ItinerisSchemaVertex) in)) {
            final TypeDefinitionDescription desc = edge.valueOrNull(BaseKey.SchemaDefinitionDesc);
            if (modifier == null || modifier.equals(desc.getModifier())) return true;
        }
        return false;
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

    public StaticBuffer getCompositeIndexKey(String indexName, Map<String, Object> fieldValues) {
        ItinerisSchemaVertex schemaVertex = getSchemaVertex(ItinerisSchemaCategory.GRAPHINDEX.getSchemaName(indexName));
        Preconditions.checkNotNull(schemaVertex, "Index with name [" + indexName + "] was not found");
        IndexType indexType = schemaVertex.asIndexType();
        if (indexType instanceof CompositeIndexType) {
            CompositeIndexType compositeIndex = (CompositeIndexType) indexType;
            StaticBuffer indexKey = indexSerializer.getIndexKey(compositeIndex, fieldValues);
            return indexKey;
        } else {
            throw new IllegalArgumentException("Index with name [" + indexName + "] is not a composite index");
        }
    }

    public CompositeIndexInfo getCompositeIndexInfo(StaticArrayBuffer indexKey) {

        com.google.common.base.Function<Long, CompositeIndexType> compositeIndexTypeFunction = input -> {
            long schemaVertexId = indexSerializer.getIndexIdFromKey(indexKey);
            InternalVertex typeVertex = vertexCache.get(schemaVertexId, existingVertexRetriever);

            Preconditions.checkNotNull(typeVertex, "Index with key [" + indexKey + "] was not found");
            ItinerisSchemaVertex schemaVertex = (ItinerisSchemaVertex) typeVertex;

            IndexType indexType = schemaVertex.asIndexType();
            if (indexType instanceof CompositeIndexType) {
                return (CompositeIndexType) indexType;
            } else {
                throw new IllegalArgumentException("Index with key [" + indexKey + "] is not a composite index");
            }
        };

        return indexSerializer.getIndexInfoFromKey(indexKey, compositeIndexTypeFunction);
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
        //Each vertex's refresh count before the read, so that a refresh which overlaps the read keeps its result out of
        //the vertex's cache
        List<Long> refreshesBefore = new ArrayList<>(vertices.size());
        for (InternalVertex v : vertices) {
            if (!v.isNew() && v.hasId() && (v instanceof CacheVertex) && !v.hasLoadedRelations(sq)) {
                vertexIds.add(v.id());
                refreshesBefore.add(((CacheVertex) v).refreshes());
            }
        }

        if (!vertexIds.isEmpty()) {
            List<EntryList> results = QueryProfiler.profile(profiler, sq, true, q -> graph.edgeMultiQuery(vertexIds, q, txHandle));
            int pos = 0;
            for (ItinerisVertex v : vertices) {
                if (pos<vertexIds.size() && vertexIds.get(pos).equals(v.id())) {
                    final EntryList vresults = results.get(pos);
                    ((CacheVertex) v).loadRelations(sq, query -> vresults, refreshesBefore.get(pos));
                    pos++;
                }
            }
        }
    }

    public void executeMultiSliceMultiQuery(final Collection<InternalVertex> vertices, final List<BackendQueryHolder<SliceQuery>> queries, QueryProfiler profiler) {
        MultiKeysQueryGroups<Object, SliceQuery> groupedMultiSliceQueries = MultiSliceQueriesGroupingUtil.toMultiKeysQueryGroups(vertices, queries);
        if (!groupedMultiSliceQueries.getQueryGroups().isEmpty()) {
            //Each vertex's refresh count before the read, as in executeMultiQuery
            Map<Object, Long> refreshesBefore = new HashMap<>(vertices.size());
            for (InternalVertex v : vertices) {
                if (v instanceof CacheVertex) refreshesBefore.put(v.id(), ((CacheVertex) v).refreshes());
            }
            Map<SliceQuery, Map<Object, EntryList>> allResults = QueryProfiler.profile(profiler, groupedMultiSliceQueries, true, q -> graph.edgeMultiQuery(q, txHandle));
            Map<Object, ItinerisVertex> vertexIdToVertexMap = vertices.stream().collect(Collectors.toMap(ItinerisElement::id, v -> v));
            allResults.forEach((sliceQuery, resultsPerQuery) -> resultsPerQuery.forEach((vertexId, vertexSliceResult) ->
                ((CacheVertex) vertexIdToVertexMap.get(vertexId)).loadRelations(sliceQuery, query -> vertexSliceResult,
                    refreshesBefore.get(vertexId))));
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
            //Only the relations of the types the query asks for, where the vertex keeps them by type
            final Set<RelationType> types = QueryUtil.getRelationTypes(query.getCondition());
            // Need to filter out self-loops if query only asks for one direction
            return new Iterator<ItinerisRelation>() {
                Iterator<InternalRelation> iterator = vertex.getAddedRelations(types, t -> true).iterator();
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
                    //Search in the added relations of its type
                    return !Iterables.isEmpty(vertex.getAddedRelations(Collections.singleton(type), predicate));
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

    //A predicate of a graph query's condition, and whether a negation wraps it
    private static final class QueryPredicate {
        final PredicateCondition<RelationType, ItinerisElement> condition;
        final boolean negated;

        QueryPredicate(PredicateCondition<RelationType, ItinerisElement> condition, boolean negated) {
            this.condition = condition;
            this.negated = negated;
        }
    }

    //The predicates which hold when one value matches them, in an index as in memory, so that removing a property never
    //makes them hold; see elementProcessorImpl.holdsAfterARemoval
    private static final Set<ItinerisPredicate> MATCHED_BY_ONE_VALUE = ImmutableSet.of(Cmp.LESS_THAN,
        Cmp.LESS_THAN_EQUAL, Cmp.GREATER_THAN, Cmp.GREATER_THAN_EQUAL, Text.CONTAINS, Text.CONTAINS_PREFIX,
        Text.CONTAINS_REGEX, Text.CONTAINS_FUZZY, Text.CONTAINS_PHRASE, Text.PREFIX, Text.REGEX, Text.FUZZY,
        Geo.INTERSECT);

    public final QueryExecutor<GraphCentricQuery, ItinerisElement, JointIndexQuery> elementProcessor;

    public final QueryExecutor<GraphCentricQuery, ItinerisElement, JointIndexQuery> elementProcessorImpl = new QueryExecutor<GraphCentricQuery, ItinerisElement, JointIndexQuery>() {

        @Override
        public Iterator<ItinerisElement> getNew(final GraphCentricQuery query) {
            //If the query is unconstrained then we don't need to add new elements, so will be picked up by getVertices()/getEdges() below
            if (query.numSubQueries()==1 && query.getSubQuery(0).getBackendQuery().isEmpty())
                return Collections.emptyIterator();
            Preconditions.checkArgument(query.getCondition().hasChildren(),"If the query is non-empty it needs to have a condition");

            if (query.getResultType() == ElementCategory.VERTEX && hasModifications()) {
                //The condition needn't be in normal form: an index which takes any condition, as Lucene and
                //Elasticsearch do, is given a disjunction of conjunctions whole
                final List<QueryPredicate> predicates = new ArrayList<>();
                collectPredicates(query.getCondition(), false, predicates);
                Preconditions.checkArgument(!predicates.isEmpty(), "Invalid query condition: %s", query.getCondition());
                //The vertices which may match come from what the transaction holds at the call, and are checked only
                //once the query reaches them, each once: an unordered query whose limit the first of them fill doesn't
                //check the others, and the caller may have removed a vertex by the time the query reaches it
                final List<Iterable<ItinerisVertex>> candidates = new ArrayList<>();
                addNewVertices(query, predicates, candidates);
                addLoadedVertices(query, predicates, candidates);
                final Set<ItinerisElement> seen = new HashSet<>();
                return com.google.common.collect.Iterators.filter(
                    Iterables.<ItinerisElement>concat(candidates).iterator(),
                    vertex -> !vertex.isRemoved() && seen.add(vertex) && query.matches(vertex));
            } else if ( (query.getResultType() == ElementCategory.EDGE || query.getResultType()==ElementCategory.PROPERTY)
                                        && !addedRelations.isEmpty()) {
                //Copied before they are checked, as an unordered query checks a relation only once it reaches it, by
                //which time the caller may have changed the transaction's added relations
                final List<ItinerisElement> relations = new ArrayList<>();
                addedRelations.getView(relation -> query.getResultType().isInstance(relation)
                    && !relation.isInvisible()).forEach(relations::add);
                return com.google.common.collect.Iterators.filter(relations.iterator(),
                    relation -> !relation.isRemoved() && query.matches(relation));
            } else return Collections.emptyIterator();
        }

        /*
         * The new vertices which may match the query. A new vertex holds an equality only through a property which this
         * transaction added with the value, so the new vertices with the added properties of one conjunct of the query
         * which is an equality, or a disjunction of equalities, are enough: those of such a conjunct on keys of the
         * indexes the query reads which has the fewest. Without such a conjunct, the new vertices with an added
         * property of any key of the query are.
         */
        private void addNewVertices(GraphCentricQuery query, List<QueryPredicate> predicates,
                                    List<Iterable<ItinerisVertex>> candidates) {
            final Condition<ItinerisElement> condition = query.getCondition();
            Condition<ItinerisElement> fewest = null;
            int fewestCount = Integer.MAX_VALUE;
            for (final Condition<ItinerisElement> conjunct : condition instanceof And
                    ? condition.getChildren() : Collections.singletonList(condition)) {
                final int count = countNewVertexEqualityProperties(conjunct, query);
                if (count >= 0 && count < fewestCount) {
                    fewest = conjunct;
                    fewestCount = count;
                    if (count == 0) {
                        break;
                    }
                }
            }
            if (fewest != null) {
                for (final Condition<ItinerisElement> equality : equalities(fewest)) {
                    final PredicateCondition<RelationType, ItinerisElement> predicate = (PredicateCondition) equality;
                    candidates.add(verticesOf(
                        addedPropertyIndex.get((PropertyKey) predicate.getKey(), predicate.getValue(), true)));
                }
            } else {
                final Set<PropertyKey> keys = new HashSet<>();
                for (final QueryPredicate predicate : predicates) {
                    if (predicate.condition.getKey() instanceof PropertyKey) {
                        keys.add((PropertyKey) predicate.condition.getKey());
                    }
                }
                for (final PropertyKey key : keys) {
                    candidates.add(verticesOf(addedPropertyIndex.getAll(key, true)));
                }
            }
        }

        //How many properties of new vertices match a conjunct which is an equality, or a disjunction of equalities, on
        //keys of the indexes the query reads; -1 for any other conjunct
        private int countNewVertexEqualityProperties(Condition<ItinerisElement> conjunct, GraphCentricQuery query) {
            //A disjunction of nothing holds for every vertex, as Or.evaluate has it, so it can't stand for the new ones
            if (conjunct instanceof Or && !conjunct.hasChildren()) {
                return -1;
            }
            int count = 0;
            for (final Condition<ItinerisElement> equality : equalities(conjunct)) {
                if (!(equality instanceof PredicateCondition)) {
                    return -1;
                }
                final PredicateCondition<RelationType, ItinerisElement> predicate = (PredicateCondition) equality;
                if (!isEqualityToAValue(predicate) || !isIndexed(predicate.getKey(), query)) {
                    return -1;
                }
                count += addedPropertyIndex.count((PropertyKey) predicate.getKey(), predicate.getValue(), true);
            }
            return count;
        }

        private Iterable<Condition<ItinerisElement>> equalities(Condition<ItinerisElement> conjunct) {
            return conjunct instanceof Or ? conjunct.getChildren() : Collections.singletonList(conjunct);
        }

        /*
         * The vertices loaded before this transaction which may match the query although the indexes the query reads
         * don't return them, because those indexes hold what the vertices held before the transaction: the vertices
         * whose properties of a key of those indexes, on which the query has a condition, the transaction changed. An
         * equality to a value which isn't negated holds now and didn't before only through a property which the
         * transaction added with that value; any other condition through a property of its key which the transaction
         * added, or, for a condition which a removal can make hold, removed. The vertices which the indexes do return
         * are checked against what they hold now, so a condition on any other key needs no vertices of its own here.
         * An index which sorts what it returns sorts the vertices by the values they held before the transaction, so
         * where every order key has one value, the vertices whose value of an order key the transaction changed are
         * taken too, which the query then places by what they hold now instead of where the index has them.
         */
        private void addLoadedVertices(GraphCentricQuery query, List<QueryPredicate> predicates,
                                       List<Iterable<ItinerisVertex>> candidates) {
            //The keys of which every property the transaction added, or removed, may make a vertex match or move
            final Set<PropertyKey> keysOfAdditions = new HashSet<>();
            final Set<PropertyKey> keysOfRemovals = new HashSet<>();
            for (final QueryPredicate predicate : predicates) {
                if (!isIndexed(predicate.condition.getKey(), query)) {
                    continue;
                }
                final PropertyKey key = (PropertyKey) predicate.condition.getKey();
                if (predicate.negated || !isEqualityToAValue(predicate.condition)) {
                    keysOfAdditions.add(key);
                    if (holdsAfterARemoval(predicate)) {
                        keysOfRemovals.add(key);
                    }
                } else {
                    candidates.add(verticesOf(addedPropertyIndex.get(key, predicate.condition.getValue(), false)));
                }
            }
            if (isSortedByAnIndex(query) && hasOrderKeysOfOneValue(query)) {
                final OrderList orders = query.getOrder();
                for (int i = 0; i < orders.size(); i++) {
                    final PropertyKey key = orders.getKey(i);
                    if (isIndexed(key, query)) {
                        keysOfAdditions.add(key);
                        keysOfRemovals.add(key);
                    }
                }
            }
            for (final PropertyKey key : keysOfAdditions) {
                candidates.add(verticesOf(addedPropertyIndex.getAll(key, false)));
            }
            if (!keysOfRemovals.isEmpty()) {
                final List<ItinerisVertex> removedFrom = new ArrayList<>();
                for (final ItinerisRelation relation : deletedRelations.values()) {
                    if (keysOfRemovals.contains(relation.getType())) {
                        final ItinerisVertex vertex = ((ItinerisVertexProperty) relation).element();
                        if (!vertex.isRemoved()) removedFrom.add(vertex);
                    }
                }
                candidates.add(removedFrom);
            }
        }

        //The vertices of the properties, each turned into its vertex only once it is iterated
        private Iterable<ItinerisVertex> verticesOf(Iterable<ItinerisVertexProperty> properties) {
            return Iterables.transform(properties, ItinerisVertexProperty::element);
        }

        //Whether the backend sorts the results of a subquery of the query, which it does by the values they held before
        //this transaction
        private boolean isSortedByAnIndex(GraphCentricQuery query) {
            if (!query.isSorted()) {
                return false;
            }
            for (int i = 0; i < query.numSubQueries(); i++) {
                if (query.getSubQuery(i).isSorted()) {
                    return true;
                }
            }
            return false;
        }

        /*
         * Whether every order key of the query has one value at most. The vertices which the query places itself are
         * compared by each of its order keys, and a vertex with several values of a key can't be ordered by it in
         * memory. So where a key of several values orders the query, which only an index can sort by, the vertices
         * whose order key the transaction changed keep the places which the index gives them, as the query leaves
         * them to it.
         */
        private boolean hasOrderKeysOfOneValue(GraphCentricQuery query) {
            final OrderList orders = query.getOrder();
            for (int i = 0; i < orders.size(); i++) {
                if (orders.getKey(i).cardinality() != Cardinality.SINGLE) {
                    return false;
                }
            }
            return true;
        }

        /*
         * Whether removing a property can make a condition hold, so that it holds now although the indexes, which hold
         * what the vertices held before, don't return the vertex. In memory a condition holds when one of the values
         * matches it, which a removal can't bring about; but a negation holds when none does, an absence when there
         * is none, and an index may answer other predicates by excluding the elements of which a value matches their
         * negation, as Elasticsearch answers a not-equal, a negated text predicate, or a geoWithin or geoContains of a
         * geo_shape of several values. So every predicate except those which every index, too, answers by one matching
         * value is one.
         */
        private boolean holdsAfterARemoval(QueryPredicate predicate) {
            if (predicate.negated) {
                return true;
            }
            final ItinerisPredicate p = predicate.condition.getPredicate();
            if (p == Cmp.EQUAL || p == Cmp.NOT_EQUAL) {
                //An equality to nothing is an absence; a not-equal to nothing is a presence, and a not-equal to a value
                //may be answered by excluding the elements which hold it
                return (p == Cmp.EQUAL) == (predicate.condition.getValue() == null);
            }
            return !MATCHED_BY_ONE_VALUE.contains(p);
        }

        //Whether a predicate is an equality to a value which the index of added properties can look up: an array
        //equals another one only element by element
        private boolean isEqualityToAValue(PredicateCondition<RelationType, ItinerisElement> condition) {
            return condition.getPredicate() == Cmp.EQUAL && condition.getValue() != null
                && !condition.getValue().getClass().isArray();
        }

        //Whether an index which the query reads holds the property key, and so holds what the vertices held for it
        //before this transaction; a condition on any other type, such as an edge label, isn't indexed
        private boolean isIndexed(RelationType key, GraphCentricQuery query) {
            if (!(key instanceof PropertyKey)) {
                return false;
            }
            for (int i = 0; i < query.numSubQueries(); i++) {
                final JointIndexQuery indexQuery = query.getSubQuery(i).getBackendQuery();
                for (int j = 0; j < indexQuery.size(); j++) {
                    if (indexQuery.getQuery(j).getIndex().indexesKey((PropertyKey) key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        //The predicates of a condition, however its conjunctions, disjunctions and negations nest, each with whether an
        //odd number of negations wraps it
        private void collectPredicates(Condition<ItinerisElement> condition, boolean negated,
                                       List<QueryPredicate> predicates) {
            if (condition.getType() == Condition.Type.LITERAL) {
                Preconditions.checkArgument(condition instanceof PredicateCondition);
                predicates.add(new QueryPredicate((PredicateCondition<RelationType, ItinerisElement>) condition,
                    negated));
            } else {
                final boolean negatesChildren = negated != (condition.getType() == Condition.Type.NOT);
                for (final Condition<ItinerisElement> child : condition.getChildren()) {
                    collectPredicates(child, negatesChildren, predicates);
                }
            }
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

        /*
         * The index query without the results whose value of the query's first order key comes after the given value:
         * its first subquery, which reads the mixed index that sorts the results, takes a range on that key along with
         * its condition, at least the value for a descending order and at most it for an ascending one. Elements
         * without a value of the key, which the range leaves out, come last. The range is taken only for a key of one
         * value, on a field whose values every index compares with the given one as memory orders them
         * (isRangedAsOrdered), and where the index can take it.
         */
        @Override
        public JointIndexQuery withoutResultsAfter(GraphCentricQuery query, JointIndexQuery indexQuery, Object value) {
            if (indexQuery.isEmpty() || !indexQuery.getQuery(0).getIndex().isMixedIndex()
                    || indexQuery.getQuery(0).getMixedQuery().getOrder().isEmpty()) {
                return indexQuery;
            }
            final MixedIndexType index = (MixedIndexType) indexQuery.getQuery(0).getIndex();
            final PropertyKey key = query.getOrder().getKey(0);
            final boolean descending = query.getOrder().getOrder(0) == Order.DESC;
            final ParameterIndexField field = index.getField(key);
            if (field == null || key.cardinality() != Cardinality.SINGLE
                    || !isRangedAsOrdered(value, field, descending)) {
                return indexQuery;
            }
            final Cmp range = descending ? Cmp.GREATER_THAN_EQUAL : Cmp.LESS_THAN_EQUAL;
            if (!indexSerializer.supports(index, field, range)) {
                return indexQuery;
            }
            return indexQuery.narrowFirst(new PredicateCondition<>(IndexRecordUtil.key2Field(field), range, value));
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

    /*
     * Whether every index compares the value with those of the field as memory orders them, so that a range from the
     * value in the direction of the order leaves out only elements which memory orders after it. That holds on an
     * enabled field without custom parameters, which may map it otherwise, for a long, int, short, byte or date; for an
     * instant of whole milliseconds, as the indexes keep instants by the millisecond and Solr takes no finer one; for a
     * float or double in an ascending order, as a descending one puts NaN first, which no range holds; and for a
     * string with no character at or above U+D800 on a field mapped as STRING without an analyzer. Memory orders
     * strings by code point, as the indexes do, but an index holds a surrogate without its pair as U+FFFD, which a
     * string below U+D800 can't hold; an index may compare a TEXTSTRING field by its analyzed terms, and an analyzer
     * may change the terms which a range compares.
     */
    static boolean isRangedAsOrdered(Object value, ParameterIndexField field, boolean descending) {
        if (field.getStatus() != SchemaStatus.ENABLED) {
            return false;
        }
        for (final Parameter parameter : field.getParameters()) {
            if (parameter.key().startsWith(ParameterType.CUSTOM_PARAMETER_PREFIX)) {
                return false;
            }
        }
        if (value instanceof Double || value instanceof Float) {
            return !descending && !Double.isNaN(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            return isComparedWhole(field) && isBelowTheSurrogates((String) value);
        }
        if (value instanceof Instant) {
            return ((Instant) value).getNano() % 1_000_000 == 0;
        }
        return value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte
            || value instanceof Date;
    }

    //Whether a string field's values are compared whole, as they are sorted: mapped as STRING, without an analyzer
    private static boolean isComparedWhole(ParameterIndexField field) {
        if (Mapping.getMapping(IndexRecordUtil.getKeyInformation(field)) != Mapping.STRING) {
            return false;
        }
        for (final Parameter parameter : field.getParameters()) {
            if (ParameterType.STRING_ANALYZER.getName().equalsIgnoreCase(parameter.key())
                    || ParameterType.TEXT_ANALYZER.getName().equalsIgnoreCase(parameter.key())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBelowTheSurrogates(String string) {
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) >= Character.MIN_SURROGATE) {
                return false;
            }
        }
        return true;
    }

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

    /**
     * Rolls the transaction back unless it is closed already. Another thread may close it at any time, also after it
     * was found open; commit and rollback hold the transaction's monitor, as this method does, so the transaction
     * can't close between the check and the rollback.
     *
     * @return whether the transaction was open and has been rolled back
     */
    public synchronized boolean rollbackIfOpen() {
        if (!isOpen()) {
            return false;
        }
        rollback();
        return true;
    }

    private void releaseTransaction() {
        isOpen = false;
        graph.closeTransaction(this);
        vertexCache = EmptyVertexCache.getInstance();
        indexCache = EmptySubqueryCache.getInstance();
        addedRelations = EmptyAddedRelations.getInstance();
        deletedRelations = Collections.emptyMap();
        uniqueLocks = Collections.emptyMap();
        addedPropertyIndex = EmptyIndexCache.getInstance();
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

    /**
     * Whether this transaction added or removed a property of one of the given keys on an element of the given
     * category: an uncommitted change which the indexes don't hold, and which can make an element match a condition on
     * those keys or stop matching it, or change the values of those keys which an index aggregates. A removed element
     * counts by the properties removed with it, and a changed edge, which the transaction replaces, by its own. For
     * elements of another category, whether the transaction changed anything.
     */
    public boolean hasChangedProperties(ElementCategory category, Collection<PropertyKey> keys) {
        if (!hasModifications()) {
            return false;
        }
        final Predicate<InternalRelation> changed;
        if (category == ElementCategory.VERTEX) {
            changed = relation -> relation.isProperty() && keys.contains(relation.getType());
        } else if (category == ElementCategory.EDGE) {
            changed = relation -> relation.isEdge()
                && Iterables.any(relation.getPropertyKeysDirect(), keys::contains);
        } else {
            return true;
        }
        return addedRelations.getView(changed).iterator().hasNext()
            || deletedRelations.values().stream().anyMatch(changed::apply);
    }

    @Override
    public void expireSchemaElement(final long id) {
        if (vertexCache.contains(id)) {
            final InternalVertex v = vertexCache.get(id, externalVertexRetriever);
            if (v instanceof ItinerisSchemaVertex) {
                ItinerisSchemaVertex sv = (ItinerisSchemaVertex) v;
                //The vertex also caches the relations its own queries read, getEdges among them, and a query which
                //this overlaps does not keep what it loaded before, as with the schema caches
                sv.refresh();
                sv.resetCache();
                //A schema vertex the change removed has no definition left to read, and nothing to invalidate for
                if (!sv.hasDefinition()) return;
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
