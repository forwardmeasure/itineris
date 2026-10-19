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

package com.forwardmeasure.itineris.graphdb;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.ItinerisBaseStoreFeaturesTest;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.EdgeLabel;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisQuery;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.ItinerisVertexQuery;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.Transaction;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.core.schema.SchemaAction;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigElement;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigOption;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanJob;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanMetrics;
import com.forwardmeasure.itineris.diskstorage.log.Log;
import com.forwardmeasure.itineris.diskstorage.log.LogManager;
import com.forwardmeasure.itineris.diskstorage.log.kcvs.KCVSLogManager;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.graphdb.internal.ElementCategory;
import com.forwardmeasure.itineris.graphdb.internal.Order;
import com.forwardmeasure.itineris.graphdb.internal.OrderList;
import com.forwardmeasure.itineris.graphdb.internal.RelationCategory;
import com.forwardmeasure.itineris.graphdb.olap.VertexJobConverter;
import com.forwardmeasure.itineris.graphdb.olap.VertexScanJob;
import com.forwardmeasure.itineris.graphdb.query.graph.GraphCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.profile.QueryProfiler;
import com.forwardmeasure.itineris.graphdb.query.profile.SimpleQueryProfiler;
import com.forwardmeasure.itineris.graphdb.query.vertex.BasicVertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.types.StandardEdgeLabelMaker;
import com.forwardmeasure.itineris.testutil.TestGraphConfigs;
import com.forwardmeasure.itineris.util.datastructures.IterablesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.LOG_BACKEND;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.MANAGEMENT_LOG;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.TRANSACTION_LOG;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.USER_LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public abstract class ItinerisBaseTest implements ItinerisBaseStoreFeaturesTest {

    public static final String LABEL_NAME = T.label.getAccessor();
    public static final String ID_NAME = T.id.getAccessor();

    public WriteConfiguration config;
    public BasicConfiguration readConfig;
    public StandardItineris graph;
    public StoreFeatures features;
    public ItinerisTransaction tx;
    public ItinerisManagement mgmt;
    public TestInfo testInfo;

    public Map<String,LogManager> logManagers;

    public ItinerisBaseTest() {
    }

    public abstract WriteConfiguration getConfiguration();

    public Configuration getConfig() {
        return new BasicConfiguration(GraphDatabaseConfiguration.ROOT_NS, config.copy(), BasicConfiguration.Restriction.NONE);
    }

    public static void clearGraph(WriteConfiguration config) throws BackendException {
        getBackend(config, true).clearStorage();
    }

    public static Backend getBackend(WriteConfiguration config, boolean initialize) throws BackendException {
        final ModifiableConfiguration adjustedConfig = new ModifiableConfiguration(GraphDatabaseConfiguration.ROOT_NS,config.copy(), BasicConfiguration.Restriction.NONE);
        adjustedConfig.set(GraphDatabaseConfiguration.LOCK_LOCAL_MEDIATOR_GROUP, "tmp");
        adjustedConfig.set(GraphDatabaseConfiguration.UNIQUE_INSTANCE_ID, "inst");
        final Backend backend = new Backend(adjustedConfig);
        if (initialize) {
            backend.initialize(adjustedConfig);
        }
        return backend;
    }

    public StoreFeatures getStoreFeatures(){
        return features;
    }

    @BeforeEach
    public void setUp(TestInfo testInfo) throws Exception {
        this.testInfo = testInfo;
        this.config = getConfiguration();
        TestGraphConfigs.applyOverrides(config);
        Preconditions.checkNotNull(config);
        logManagers = new HashMap<>();
        clearGraph(config);
        readConfig = new BasicConfiguration(GraphDatabaseConfiguration.ROOT_NS, config, BasicConfiguration.Restriction.NONE);
        open(config);
    }

    public void open(WriteConfiguration config) {
        graph = (StandardItineris) ItinerisFactory.open(config);
        features = graph.getConfiguration().getStoreFeatures();
        tx = graph.newTransaction();
        mgmt = graph.openManagement();
    }

    @AfterEach
    public void tearDown() throws Exception {
        close();
        closeLogs();
    }

    public void finishSchema() {
        if (mgmt!=null && mgmt.isOpen())
            mgmt.commit();
        mgmt=graph.openManagement();
        newTx();
        graph.tx().commit();
    }

    public void close() {
        if (mgmt!=null && mgmt.isOpen()) mgmt.rollback();
        if (null != tx && tx.isOpen())
            tx.commit();


        if (null != graph && graph.isOpen())
            graph.close();
    }

    public void newTx() {
        if (null != tx && tx.isOpen())
            tx.commit();
        //tx = graph.newThreadBoundTransaction();
        tx = graph.newTransaction();
    }

    public static Map<TestConfigOption,Object> validateConfigOptions(Object... settings) {
        //Parse settings
        Preconditions.checkArgument(settings.length%2==0, "Expected even number of settings: %s", settings);
        final Map<TestConfigOption,Object> options = Maps.newHashMap();
        for (int i=0;i<settings.length;i=i+2) {
            Preconditions.checkArgument(settings[i] instanceof TestConfigOption, "Expected configuration option but got: %s", settings[i]);
            Preconditions.checkNotNull(settings[i+1], "Null setting at position [%s]", i+1);
            options.put((TestConfigOption)settings[i],settings[i+1]);
        }
        return options;
    }

    public void setupConfig(Object... settings){
        config = getConfiguration();
        if (mgmt!=null && mgmt.isOpen()) mgmt.rollback();
        if (null != tx && tx.isOpen()) tx.commit();
        if (settings!=null && settings.length>0) {
            final Map<TestConfigOption,Object> options = validateConfigOptions(settings);
            ItinerisManagement janusGraphManagement = null;
            final ModifiableConfiguration modifiableConfiguration = new ModifiableConfiguration(GraphDatabaseConfiguration.ROOT_NS,config, BasicConfiguration.Restriction.LOCAL);
            for (final Map.Entry<TestConfigOption,Object> option : options.entrySet()) {
                if (option.getKey().option.isLocal()) {
                    modifiableConfiguration.set(option.getKey().option,option.getValue(),option.getKey().umbrella);
                } else {
                    if (janusGraphManagement==null) janusGraphManagement = graph.openManagement();
                    janusGraphManagement.set(ConfigElement.getPath(option.getKey().option,option.getKey().umbrella),option.getValue());
                }
            }
            if (janusGraphManagement!=null) janusGraphManagement.commit();
            modifiableConfiguration.close();
        }
    }

    public void clopen(Object... settings) {
        setupConfig(settings);
        if (null != graph && null != graph.tx() && graph.tx().isOpen())
            graph.tx().commit();
        if (null != graph && graph.isOpen())
            graph.close();
        Preconditions.checkNotNull(config);
        open(config);
    }

    public void clopenNoDelimiterUsage(Map<String, Object> extraConfigs, boolean clearGraph) {
        this.config = getConfiguration();
        if (null != graph && null != graph.tx() && graph.tx().isOpen())
            graph.tx().commit();
        if (null != graph && graph.isOpen())
            graph.close();
        if(clearGraph) {
            try {
                clearGraph(config);
            } catch (BackendException e) {
                throw new RuntimeException(e);
            }
        }
        readConfig = new BasicConfiguration(GraphDatabaseConfiguration.ROOT_NS, config, BasicConfiguration.Restriction.NONE);
        Map<String, Object> finalConfig = new HashMap<>();
        readConfig.getAll().forEach((pathIdentifier, value) -> finalConfig.put(pathIdentifier.element.toStringWithoutRoot(), value));
        finalConfig.putAll(extraConfigs);

        graph = (StandardItineris) ItinerisFactory.open(new MapConfiguration(finalConfig));
        features = graph.getConfiguration().getStoreFeatures();
        tx = graph.newTransaction();
        mgmt = graph.openManagement();
    }

    public static TestConfigOption option(ConfigOption option, String... umbrella) {
        return new TestConfigOption(option,umbrella);
    }

    public static final class TestConfigOption {

        public final ConfigOption option;
        public final String[] umbrella;

        public TestConfigOption(ConfigOption option, String... umbrella) {
            Preconditions.checkNotNull(option);
            this.option = option;
            if (umbrella==null) umbrella=new String[0];
            this.umbrella = umbrella;
        }
    }

    /*
    ========= Log Helpers ============
     */

    private KeyColumnValueStoreManager logStoreManager = null;

    private void closeLogs() {
        try {
            for (final LogManager lm : logManagers.values()) lm.close();
            logManagers.clear();
            if (logStoreManager!=null) {
                logStoreManager.close();
                logStoreManager=null;
            }
        } catch (final BackendException e) {
            throw new ItinerisException(e);
        }
    }

    public void closeLogManager(String logManagerName) {
        if (logManagers.containsKey(logManagerName)) {
            try {
                logManagers.remove(logManagerName).close();
            } catch (final BackendException e) {
                throw new ItinerisException("Could not close log manager " + logManagerName,e);
            }
        }
    }

    public Log openUserLog(String identifier) {
        return openLog(USER_LOG, GraphDatabaseConfiguration.USER_LOG_PREFIX +identifier);
    }

    public Log openTxLog() {
        return openLog(TRANSACTION_LOG, Backend.SYSTEM_TX_LOG_NAME);
    }

    private Log openLog(String logManagerName, String logName) {
        try {
            final ModifiableConfiguration configuration = new ModifiableConfiguration(GraphDatabaseConfiguration.ROOT_NS,config.copy(), BasicConfiguration.Restriction.NONE);
            configuration.set(GraphDatabaseConfiguration.UNIQUE_INSTANCE_ID, "reader");
            configuration.set(GraphDatabaseConfiguration.LOG_READ_INTERVAL, Duration.ofMillis(500L), logManagerName);
            if (logStoreManager==null) {
                logStoreManager = Backend.getStorageManager(configuration);
            }
            final StoreFeatures f = logStoreManager.getFeatures();
            final boolean part = f.isDistributed() && f.isKeyOrdered();
            if (part) {
                for (final String partitionedLogName : new String[]{USER_LOG,TRANSACTION_LOG,MANAGEMENT_LOG})
                configuration.set(KCVSLogManager.LOG_MAX_PARTITIONS,8,partitionedLogName);
            }
            Preconditions.checkNotNull(logStoreManager);
            if (!logManagers.containsKey(logManagerName)) {
                //Open log manager - only supports KCVSLog
                final Configuration logConfig = configuration.restrictTo(logManagerName);
                Preconditions.checkState(logConfig.get(LOG_BACKEND).equals(LOG_BACKEND.getDefaultValue()));
                logManagers.put(logManagerName,new KCVSLogManager(logStoreManager,logConfig));
            }
            Preconditions.checkState(logManagers.containsKey(logManagerName));
            return logManagers.get(logManagerName).openLog(logName);
        } catch (final BackendException e) {
            throw new ItinerisException("Could not open log: "+ logName,e);
        }
    }

    /*
    ========= Schema Type Definition Helpers ============
     */

    public PropertyKey makeVertexIndexedKey(String name, Class dataType) {
        final PropertyKey key = mgmt.makePropertyKey(name).dataType(dataType).cardinality(Cardinality.SINGLE).make();
        mgmt.buildIndex(name,Vertex.class).addKey(key).buildCompositeIndex();
        return key;
    }

    public PropertyKey makeVertexIndexedUniqueKey(String name, Class dataType) {
        final PropertyKey key = mgmt.makePropertyKey(name).dataType(dataType).cardinality(Cardinality.SINGLE).make();
        mgmt.buildIndex(name,Vertex.class).addKey(key).unique().buildCompositeIndex();
        return key;
    }

    public void createExternalVertexIndex(PropertyKey key, String backingIndex) {
        createExternalIndex(key,Vertex.class,backingIndex);
    }

    public void createExternalEdgeIndex(PropertyKey key, String backingIndex) {
        createExternalIndex(key,Edge.class,backingIndex);
    }

    public ItinerisIndex getExternalIndex(Class<? extends Element> clazz, String backingIndex) {
        String prefix;
        if (Vertex.class.isAssignableFrom(clazz)) prefix = "v";
        else if (Edge.class.isAssignableFrom(clazz)) prefix = "e";
        else if (ItinerisVertexProperty.class.isAssignableFrom(clazz)) prefix = "p";
        else throw new AssertionError(clazz.toString());

        final String indexName = prefix+backingIndex;
        ItinerisIndex index = mgmt.getGraphIndex(indexName);
        if (index==null) {
            index = mgmt.buildIndex(indexName,clazz).buildMixedIndex(backingIndex);
        }
        return index;
    }

    private void createExternalIndex(PropertyKey key, Class<? extends Element> clazz, String backingIndex) {
        mgmt.addIndexKey(getExternalIndex(clazz,backingIndex),key);
    }

    public PropertyKey makeKey(String name, Class dataType) {
        return mgmt.makePropertyKey(name).dataType(dataType).cardinality(Cardinality.SINGLE).make();
    }

    public EdgeLabel makeLabel(String name) {
        return mgmt.makeEdgeLabel(name).make();
    }

    public EdgeLabel makeKeyedEdgeLabel(String name, PropertyKey sort, PropertyKey signature) {
        return ((StandardEdgeLabelMaker) tx.makeEdgeLabel(name)).sortKey(sort).signature(signature).directed().make();
    }

    /*
    ========= General Helpers ===========
     */

    public static final int DEFAULT_THREAD_COUNT = 4;

    public static int getThreadCount() {
        final String s = System.getProperty("itineris.test.threads");
        if (null != s)
            return Integer.parseInt(s);
        else
            return DEFAULT_THREAD_COUNT;
    }

    public static int wrapAround(int value, int maxValue) {
        value = value % maxValue;
        if (value < 0) value = value + maxValue;
        return value;
    }

    public ItinerisVertex getVertex(String key, Object value) {
        return getVertex(tx,key,value);
    }

    public ItinerisVertex getVertex(PropertyKey key, Object value) {
        return getVertex(tx,key,value);
    }

    public static ItinerisVertex getVertex(ItinerisTransaction tx, String key, Object value) {
        return getOnlyElement(tx.query().has(key,value).vertices(),null);
    }

    public static ItinerisVertex getVertex(ItinerisTransaction tx, PropertyKey key, Object value) {
        return getVertex(tx, key.name(), value);
    }

    public static double round(double d) {
        return Math.round(d*1000.0)/1000.0;
    }

    public static ItinerisVertex getOnlyVertex(ItinerisQuery<?> query) {
        return getOnlyElement(query.vertices());
    }

    public static ItinerisEdge getOnlyEdge(ItinerisVertexQuery<?> query) {
        return getOnlyElement(query.edges());
    }

    public static<E> E getOnlyElement(Iterable<E> traversal) {
        return getOnlyElement(traversal.iterator());
    }

    public static<E> E getOnlyElement(Iterator<E> traversal) {
        if (!traversal.hasNext()) throw new NoSuchElementException();
        return getOnlyElement(traversal,null);
    }

    public static<E> E getOnlyElement(Iterable<E> traversal, E defaultElement) {
        return getOnlyElement(traversal.iterator(),defaultElement);
    }

    public static<E> E getOnlyElement(Iterator<E> traversal, E defaultElement) {
        if (!traversal.hasNext()) return defaultElement;
        final E result = traversal.next();
        if (traversal.hasNext()) throw new IllegalArgumentException("Traversal contains more than 1 element: " + result + ", " + traversal.next());
        return result;
    }

    public static void assertMissing(Transaction g, Object vid) {
        assertFalse(g.vertices(vid).hasNext());
    }

    public static ItinerisVertex getV(Transaction g, Object vid) {
        if (!g.vertices(vid).hasNext()) return null;
        return (ItinerisVertex)g.vertices(vid).next();
    }

    public static ItinerisEdge getE(Transaction g, Object eid) {
        if (!g.edges(eid).hasNext()) return null;
        return (ItinerisEdge)g.edges(eid).next();
    }

    public static String n(Object obj) {
        if (obj instanceof RelationType) return ((RelationType)obj).name();
        else return obj.toString();
    }

    public static Object getId(Element e) {
        return e.id();
    }

    public static void verifyElementOrder(Iterable<? extends Element> elements, String key, Order order, int expectedCount) {
        verifyElementOrder(elements.iterator(), key, order, expectedCount);
    }

    public static void verifyElementOrder(Iterator<? extends Element> elements, String key, Order order, int expectedCount) {
        Comparable previous = null;
        int count = 0;
        while (elements.hasNext()) {
            final Element element = elements.next();
            final Comparable current = element.value(key);
            if (previous != null) {
                final int cmp = previous.compareTo(current);
                assertTrue(order == Order.ASC ? cmp <= 0 : cmp >= 0,
                    previous + " <> " + current + " @ " + count);
            }
            previous = current;
            count++;
        }
        assertEquals(expectedCount, count);
    }

    public static <T> Stream<T> asStream(final Iterator<T> source) {
        final Iterable<T> iterable = () -> source;
        return IterablesUtil.stream(iterable);
    }

    public Itineris getForceIndexGraph() {
        return getForceIndexGraph(getConfiguration());
    }

    public Itineris getForceIndexGraph(WriteConfiguration writeConfiguration) {
        final ModifiableConfiguration adjustedConfig = new ModifiableConfiguration(GraphDatabaseConfiguration.ROOT_NS, writeConfiguration, BasicConfiguration.Restriction.NONE);
        adjustedConfig.set(GraphDatabaseConfiguration.FORCE_INDEX_USAGE, true);
        final WriteConfiguration adjustedWriteConfig = adjustedConfig.getConfiguration();
        TestGraphConfigs.applyOverrides(adjustedWriteConfig);
        Preconditions.checkNotNull(adjustedWriteConfig);
        return ItinerisFactory.open(adjustedWriteConfig);
    }

    protected void discardIndex(String indexName) throws InterruptedException, ExecutionException {
        mgmt.updateIndex(mgmt.getGraphIndex(indexName), SchemaAction.DISCARD_INDEX).get();
        mgmt.commit();
        assertTrue(ManagementSystem
            .awaitGraphIndexStatus(graph, indexName)
            .status(SchemaStatus.DISCARDED)
            .call()
            .getSucceeded()
        );
        mgmt = graph.openManagement();
    }

    protected void markIndexDiscarded(String indexName) throws InterruptedException, ExecutionException {
        mgmt.updateIndex(mgmt.getGraphIndex(indexName), SchemaAction.MARK_DISCARDED).get();
        mgmt.commit();
        assertTrue(ManagementSystem
            .awaitGraphIndexStatus(graph, indexName)
            .status(SchemaStatus.DISCARDED)
            .call()
            .getSucceeded()
        );
        mgmt = graph.openManagement();
    }

    protected void enableIndex(String indexName) throws InterruptedException, ExecutionException {
        mgmt.updateIndex(mgmt.getGraphIndex(indexName), SchemaAction.ENABLE_INDEX).get();
        mgmt.commit();
        assertTrue(ManagementSystem
            .awaitGraphIndexStatus(graph, indexName)
            .status(SchemaStatus.ENABLED)
            .call()
            .getSucceeded()
        );
        mgmt = graph.openManagement();
    }

    protected void dropIndex(String indexName) throws InterruptedException, ExecutionException {
        mgmt.updateIndex(mgmt.getGraphIndex(indexName), SchemaAction.DROP_INDEX).get();
        mgmt.commit();
        mgmt = graph.openManagement();
        assertNull(mgmt.getGraphIndex(indexName));
    }

    protected void disableIndex(String indexName) throws InterruptedException, ExecutionException {
        mgmt.updateIndex(mgmt.getGraphIndex(indexName), SchemaAction.DISABLE_INDEX).get();
        mgmt.commit();
        assertTrue(ManagementSystem
            .awaitGraphIndexStatus(graph, indexName)
            .status(SchemaStatus.DISABLED)
            .call()
            .getSucceeded()
        );
        mgmt = graph.openManagement();
    }
    public static void evaluateQuery(ItinerisVertexQuery query, RelationCategory resultType,
                                     int expectedResults, int numSubQueries, boolean[] subQuerySpecs) {
        evaluateQuery(query, resultType, expectedResults, numSubQueries, subQuerySpecs, ImmutableMap.of());
    }

    public static void evaluateQuery(ItinerisVertexQuery query, RelationCategory resultType,
                                     int expectedResults, int numSubQueries, boolean[] subQuerySpecs,
                                     PropertyKey orderKey, Order order) {
        evaluateQuery(query, resultType, expectedResults, numSubQueries, subQuerySpecs, ImmutableMap.of(orderKey, order));
    }


    public static void evaluateQuery(ItinerisVertexQuery query, RelationCategory resultType,
                                     int expectedResults, int numSubQueries, boolean[] subQuerySpecs,
                                     Map<PropertyKey, Order> orderMap) {
        SimpleQueryProfiler profiler = new SimpleQueryProfiler();
        ((BasicVertexCentricQueryBuilder) query).profiler(profiler);

        Iterable<? extends ItinerisElement> result;
        switch (resultType) {
            case PROPERTY:
                result = query.properties();
                break;
            case EDGE:
                result = query.edges();
                break;
            case RELATION:
                result = query.relations();
                break;
            default:
                throw new AssertionError();
        }
        OrderList orders = profiler.getAnnotation(QueryProfiler.ORDERS_ANNOTATION);

        //Check elements and that they are returned in the correct order
        int no = 0;
        ItinerisElement previous = null;
        for (ItinerisElement e : result) {
            assertNotNull(e);
            no++;
            if (previous != null && !orders.isEmpty()) {
                assertTrue(orders.compare(previous, e) <= 0);
            }
            previous = e;
        }
        assertEquals(expectedResults, no);

        //Check OrderList of query
        assertNotNull(orders);
        assertEquals(orderMap.size(), orders.size());
        for (int i = 0; i < orders.size(); i++) {
            assertEquals(orderMap.get(orders.getKey(i)), orders.getOrder(i));
        }
        for (PropertyKey key : orderMap.keySet()) assertTrue(orders.containsKey(key));

        //Check subqueries
        assertEquals(Integer.valueOf(1), profiler.getAnnotation(QueryProfiler.NUMVERTICES_ANNOTATION));
        int subQueryCounter = 0;
        for (SimpleQueryProfiler subProfiler : profiler) {
            assertNotNull(subProfiler);
            if (subProfiler.getGroupName().equals(QueryProfiler.OPTIMIZATION) || Boolean.TRUE.equals(subProfiler.getAnnotation(QueryProfiler.MULTI_SLICES_ANNOTATION))) continue;
            if (subQuerySpecs.length == 2) { //0=>fitted, 1=>ordered
                assertEquals(subQuerySpecs[0], subProfiler.getAnnotation(QueryProfiler.FITTED_ANNOTATION));
                assertEquals(subQuerySpecs[1], subProfiler.getAnnotation(QueryProfiler.ORDERED_ANNOTATION));
            }
            //assertEquals(1,Iterables.size(subProfiler)); This only applies if a disk call is necessary
            subQueryCounter++;
        }
        assertEquals(numSubQueries, subQueryCounter);
    }

    public static void evaluateQuery(ItinerisQuery query, ElementCategory resultType,
                                     int expectedResults, boolean[] subQuerySpecs,
                                     PropertyKey orderKey1, Order order1,
                                     String... intersectingIndexes) {
        evaluateQuery(query, resultType, expectedResults, subQuerySpecs,
            ImmutableMap.of(orderKey1, order1), intersectingIndexes);
    }

    public static void evaluateQuery(ItinerisQuery query, ElementCategory resultType,
                                     int expectedResults, boolean[] subQuerySpecs,
                                     PropertyKey orderKey1, Order order1, PropertyKey orderKey2, Order order2,
                                     String... intersectingIndexes) {
        evaluateQuery(query, resultType, expectedResults, subQuerySpecs,
            ImmutableMap.of(orderKey1, order1, orderKey2, order2), intersectingIndexes);
    }

    public static void evaluateQuery(ItinerisQuery query, ElementCategory resultType,
                                     int expectedResults, boolean[] subQuerySpecs,
                                     String... intersectingIndexes) {
        evaluateQuery(query, resultType, expectedResults, subQuerySpecs, ImmutableMap.of(), intersectingIndexes);
    }

    public static void evaluateQuery(ItinerisQuery query, ElementCategory resultType,
                                     int expectedResults, boolean[] subQuerySpecs,
                                     Map<PropertyKey, Order> orderMap, String... intersectingIndexes) {
        if (intersectingIndexes == null) intersectingIndexes = new String[0];

        SimpleQueryProfiler profiler = new SimpleQueryProfiler();
        ((GraphCentricQueryBuilder) query).profiler(profiler);

        Iterable<? extends ItinerisElement> result;
        switch (resultType) {
            case PROPERTY:
                result = query.properties();
                break;
            case EDGE:
                result = query.edges();
                break;
            case VERTEX:
                result = query.vertices();
                break;
            default:
                throw new AssertionError();
        }
        OrderList orders = profiler.getAnnotation(QueryProfiler.ORDERS_ANNOTATION);

        //Check elements and that they are returned in the correct order
        int no = 0;
        ItinerisElement previous = null;
        for (ItinerisElement e : result) {
            assertNotNull(e);
            no++;
            if (previous != null && !orders.isEmpty()) {
                assertTrue(orders.compare(previous, e) <= 0);
            }
            previous = e;
        }
        assertEquals(expectedResults, no);

        //Check OrderList of query
        assertNotNull(orders);
        assertEquals(orderMap.size(), orders.size());
        for (int i = 0; i < orders.size(); i++) {
            assertEquals(orderMap.get(orders.getKey(i)), orders.getOrder(i));
        }
        for (PropertyKey key : orderMap.keySet()) assertTrue(orders.containsKey(key));

        //Check subqueries
        SimpleQueryProfiler simpleQueryProfiler = Iterables.getOnlyElement(IterablesUtil.stream(profiler)
            .filter(p -> !p.getGroupName().equals(QueryProfiler.OPTIMIZATION)).collect(Collectors.toList()));
        if (subQuerySpecs.length == 2) { //0=>fitted, 1=>ordered
            assertEquals(subQuerySpecs[0], simpleQueryProfiler.getAnnotation(QueryProfiler.FITTED_ANNOTATION));
            assertEquals(subQuerySpecs[1], simpleQueryProfiler.getAnnotation(QueryProfiler.ORDERED_ANNOTATION));
        }
        Set<String> indexNames = new HashSet<>();
        int indexQueries = 0;
        boolean fullScan = false;
        for (SimpleQueryProfiler indexProfiler : simpleQueryProfiler) {
            if (indexProfiler.getAnnotation(QueryProfiler.FULLSCAN_ANNOTATION) != null) {
                fullScan = true;
            } else {
                indexNames.add(indexProfiler.getAnnotation(QueryProfiler.INDEX_ANNOTATION));
                indexQueries++;
            }
        }
        if (indexQueries > 0) assertFalse(fullScan);
        if (fullScan) assertEquals(0, intersectingIndexes.length);
        assertEquals(intersectingIndexes.length, indexQueries);
        assertEquals(Sets.newHashSet(intersectingIndexes), indexNames);
    }

    protected ScanMetrics executeScanJob(VertexScanJob job) throws Exception {
        return executeScanJob(VertexJobConverter.convert(graph,job));
    }

    protected ScanMetrics executeScanJob(ScanJob job) throws Exception {
        return graph.getBackend().buildEdgeScanJob()
            .setNumProcessingThreads(2)
            .setWorkBlockSize(100)
            .setJob(job)
            .execute().get();
    }
}
