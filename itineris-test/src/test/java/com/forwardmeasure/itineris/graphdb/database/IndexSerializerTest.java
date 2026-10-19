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

package com.forwardmeasure.itineris.graphdb.database;

import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedProperty;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.core.schema.SchemaAction;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BaseTransaction;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfigurable;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexEntry;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexFeatures;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexInformation;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexMutation;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexProvider;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexQuery;
import com.forwardmeasure.itineris.diskstorage.indexing.KeyInformation;
import com.forwardmeasure.itineris.diskstorage.indexing.RawQuery;
import com.forwardmeasure.itineris.diskstorage.util.DefaultTransaction;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.graphdb.database.serialize.Serializer;
import com.forwardmeasure.itineris.graphdb.internal.ElementCategory;
import com.forwardmeasure.itineris.graphdb.internal.ElementLifeCycle;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicate;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.transaction.TransactionConfiguration;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.Aggregation;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;
import com.forwardmeasure.itineris.graphdb.types.ParameterIndexField;
import com.forwardmeasure.itineris.graphdb.vertices.StandardVertex;
import com.forwardmeasure.itineris.util.stats.MetricManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.forwardmeasure.itineris.graphdb.database.util.IndexRecordUtil.key2Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

public class IndexSerializerTest {

    private static final String REINDEX_TEST_INDEX_BACKEND_NAME = "search";
    private static final String REINDEX_TEST_INDEX_NAME = "nameMixed";
    private static final int REINDEX_TEST_STORAGE_PAGE_SIZE = 5;
    private static final int REINDEX_TEST_BATCH_SIZE = 20;
    private static final int REINDEX_TEST_VERTEX_COUNT = 65;

    @Test
    public void testReindexElementNotAppliesTo() {
        Configuration config = mock(Configuration.class);
        Serializer serializer = mock(Serializer.class);
        EdgeSerializer edgeSerializer = mock(EdgeSerializer.class);
        Map<String, ? extends IndexInformation> indexes = new HashMap<>();

        IndexSerializer mockSerializer = new IndexSerializer(config, edgeSerializer, serializer, indexes, true);
        ItinerisElement nonIndexableElement = mock(ItinerisElement.class);
        MixedIndexType mit = mock(MixedIndexType.class);
        doReturn(ElementCategory.VERTEX).when(mit).getElement();

        Map<String, Map<String, List<IndexEntry>>> docStore = new HashMap<>();
        assertFalse("re-index", mockSerializer.reindexElement(nonIndexableElement, mit, docStore));

    }

    @Test
    public void testReindexElementAppliesToWithEntries() {
        Map<String, Map<String, List<IndexEntry>>> docStore = new HashMap<>();
        IndexSerializer mockSerializer = mockSerializer();
        MixedIndexType mit = mock(MixedIndexType.class);

        ItinerisElement indexableElement = mockIndexAppliesTo(mit, true);

        assertTrue("re-index", mockSerializer.reindexElement(indexableElement, mit, docStore));
        assertEquals("doc store size", 1, docStore.size());

    }

    @Test
    public void testReindexElementAppliesToNoEntries() {
        Map<String, Map<String, List<IndexEntry>>> docStore = new HashMap<>();
        IndexSerializer mockSerializer = mockSerializer();
        MixedIndexType mit = mock(MixedIndexType.class);
        ItinerisElement indexableElement = mockIndexAppliesTo(mit, false);

        assertFalse("re-index", mockSerializer.reindexElement(indexableElement, mit, docStore));
        assertEquals("doc store size", 0, docStore.size());

    }

    private IndexSerializer mockSerializer() {
        Configuration config = mock(Configuration.class);
        Serializer serializer = mock(Serializer.class);
        EdgeSerializer edgeSerializer = mock(EdgeSerializer.class);
        Map<String, ? extends IndexInformation> indexes = new HashMap<>();
        return spy(new IndexSerializer(config, edgeSerializer, serializer, indexes, true));
    }

    private ItinerisElement mockIndexAppliesTo(MixedIndexType mit, boolean indexable) {
        String key = "foo";
        String value = "bar";

        ItinerisElement indexableElement = mockIndexableElement(key, value, indexable);
        ElementCategory ec = ElementCategory.VERTEX;

        doReturn(ec).when(mit).getElement();

        doReturn(false).when(mit).hasSchemaTypeConstraint();

        PropertyKey pk = mock(PropertyKey.class);
        doReturn(1L).when(pk).id();
        doReturn(1L).when(pk).longId();
        doReturn(key).when(pk).name();
        ParameterIndexField pif = mock(ParameterIndexField.class);
        Parameter[] parameter = { new Parameter(key, value) };
        doReturn(parameter).when(pif).getParameters();
        doReturn(SchemaStatus.REGISTERED).when(pif).getStatus();
        doReturn(pk).when(pif).getFieldKey();

        ParameterIndexField[] ifField = { pif };

        doReturn(ifField).when(mit).getFieldKeys();

        return indexableElement;

    }

    // A vertex with a value of the index's key when indexable, and with a value of another key alone otherwise
    private ItinerisElement mockIndexableElement(String key, String value, boolean indexable) {
        return vertexWithProperties(true, new DetachedProperty<>(indexable ? key : "other", value));
    }

    // A vertex whose transaction prefetches properties is first accessed by a single key, which prefetches all its
    // properties as the reads field by field did on the first field, and then has the index's keys queried once. The
    // fields come in the order of the index, each with its values in the order they come, and neither a disabled
    // field nor a key the index lacks is in the document
    @Test
    public void aCompleteDocumentOfAVertexPrefetchesItsPropertiesThenQueriesTheIndexKeysOnce() {
        assertCompleteDocumentRead(vertexWithProperties(true, DOCUMENT_PROPERTIES), ElementCategory.VERTEX,
            Arrays.asList(Collections.singletonList("first"), Arrays.asList("first", "second")));
    }

    // Without the prefetching, a vertex has the index's enabled keys queried once
    @Test
    public void aCompleteDocumentOfAVertexQueriesTheIndexKeysOnceWithoutPrefetching() {
        assertCompleteDocumentRead(vertexWithProperties(false, DOCUMENT_PROPERTIES), ElementCategory.VERTEX,
            Collections.singletonList(Arrays.asList("first", "second")));
    }

    // A relation, which has its properties at hand, has the index's keys queried once
    @Test
    public void aCompleteDocumentOfARelationQueriesTheIndexKeysOnce() {
        final ItinerisEdge edge = mock(ItinerisEdge.class);
        answerPropertiesWith(edge, DOCUMENT_PROPERTIES);
        assertCompleteDocumentRead(edge, ElementCategory.EDGE,
            Collections.singletonList(Arrays.asList("first", "second")));
    }

    private static final Property<?>[] DOCUMENT_PROPERTIES = {new DetachedProperty<>("second", "b"),
        new DetachedProperty<>("other", "x"), new DetachedProperty<>("first", "a1"),
        new DetachedProperty<>("disabled", "d"), new DetachedProperty<>("first", "a2")};

    private void assertCompleteDocumentRead(ItinerisElement element, ElementCategory category,
                                            List<List<String>> propertiesCalls) {
        final ParameterIndexField first = indexField("first", 1L, SchemaStatus.ENABLED);
        final ParameterIndexField second = indexField("second", 2L, SchemaStatus.REGISTERED);
        final ParameterIndexField disabled = indexField("disabled", 3L, SchemaStatus.DISABLED);
        final MixedIndexType index = mock(MixedIndexType.class);
        doReturn(category).when(index).getElement();
        doReturn(false).when(index).hasSchemaTypeConstraint();
        doReturn(new ParameterIndexField[]{first, second, disabled}).when(index).getFieldKeys();

        assertEquals(Arrays.asList(key2Field(first) + "=a1", key2Field(first) + "=a2", key2Field(second) + "=b"),
            mockSerializer().getCompleteDocument(element, index).stream().map(entry -> entry.field + "=" + entry.value)
                .collect(Collectors.toList()));
        assertEquals(propertiesCalls, calls(element, "properties"));
        assertEquals(Collections.emptyList(), calls(element, "values"));
    }

    private static ParameterIndexField indexField(String name, long id, SchemaStatus status) {
        final PropertyKey key = mock(PropertyKey.class);
        doReturn(id).when(key).longId();
        doReturn(name).when(key).name();
        final ParameterIndexField field = mock(ParameterIndexField.class);
        doReturn(key).when(field).getFieldKey();
        doReturn(status).when(field).getStatus();
        doReturn(new Parameter[0]).when(field).getParameters();
        return field;
    }

    private static ItinerisElement vertexWithProperties(boolean prefetching, Property<?>... properties) {
        final StandardItinerisTx tx = mock(StandardItinerisTx.class);
        doReturn(tx).when(tx).getNextTx();
        final TransactionConfiguration configuration = mock(TransactionConfiguration.class);
        doReturn(prefetching).when(configuration).hasPropertyPrefetching();
        doReturn(configuration).when(tx).getConfiguration();
        final ItinerisElement vertex = spy(new StandardVertex(tx, 1L, ElementLifeCycle.New));
        answerPropertiesWith(vertex, properties);
        return vertex;
    }

    // properties(keys) answers with the properties of those keys, and properties() with all of them
    private static void answerPropertiesWith(ItinerisElement element, Property<?>... properties) {
        doAnswer(invocation -> {
            final List<String> keys = Arrays.asList((String[]) invocation.getRawArguments()[0]);
            return Arrays.stream(properties).filter(property -> keys.isEmpty() || keys.contains(property.key()))
                .iterator();
        }).when(element).properties(any(String[].class));
    }

    // The keys of each call of the method, which takes property keys
    private static List<List<String>> calls(Object mock, String method) {
        return mockingDetails(mock).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals(method))
            .map(invocation -> Arrays.asList((String[]) invocation.getRawArguments()[0]))
            .collect(Collectors.toList());
    }

    @Test
    public void mixedIndexReindexUsesConfiguredBatchSizeWhenEnabled() throws Exception {
        List<Integer> restoreBatchSizes = runMixedIndexReindex(true);

        assertEquals(REINDEX_TEST_VERTEX_COUNT, restoreBatchSizes.stream().mapToInt(Integer::intValue).sum());
        assertTrue(restoreBatchSizes.stream().allMatch(batchSize -> batchSize <= REINDEX_TEST_BATCH_SIZE));
        assertTrue(restoreBatchSizes.stream().anyMatch(batchSize -> batchSize > REINDEX_TEST_STORAGE_PAGE_SIZE));
    }

    @Test
    public void mixedIndexReindexKeepsStoragePageSizedBatchesWhenDisabled() throws Exception {
        List<Integer> restoreBatchSizes = runMixedIndexReindex(false);

        assertEquals(REINDEX_TEST_VERTEX_COUNT, restoreBatchSizes.stream().mapToInt(Integer::intValue).sum());
        assertTrue(restoreBatchSizes.stream().allMatch(batchSize -> batchSize <= REINDEX_TEST_STORAGE_PAGE_SIZE));
    }

    // A vertex's document costs the reads of its first property access: with property prefetching, one slice of all
    // its properties, which also answers the query of the index's keys; without, a slice for each of the index's keys,
    // which inmemory, having no multi-query, reads one after the other
    @Test
    public void aCompleteDocumentOfAVertexReadsOneSliceWithPrefetching() throws Exception {
        assertEquals(1, completeDocumentReads(true));
    }

    @Test
    public void aCompleteDocumentOfAVertexReadsASliceForEachIndexKeyWithoutPrefetching() throws Exception {
        assertEquals(3, completeDocumentReads(false));
    }

    // The storage reads which building the document of a vertex with the three keys of an index and another key takes,
    // in a transaction which has read nothing but the vertex's existence
    private long completeDocumentReads(boolean prefetching) throws Exception {
        RecordingIndexProvider.reset();
        final ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
        config.set(GraphDatabaseConfiguration.STORAGE_BACKEND, "inmemory");
        config.set(GraphDatabaseConfiguration.INDEX_BACKEND, RecordingIndexProvider.class.getName(),
            REINDEX_TEST_INDEX_BACKEND_NAME);
        config.set(GraphDatabaseConfiguration.PROPERTY_PREFETCHING, prefetching);
        config.set(GraphDatabaseConfiguration.BASIC_METRICS, true);
        final Itineris graph = ItinerisFactory.open(config.getConfiguration());
        try {
            final ItinerisManagement management = graph.openManagement();
            final ItinerisManagement.IndexBuilder index = management.buildIndex("documentReads", Vertex.class);
            for (String key : Arrays.asList("first", "second", "third")) {
                index.addKey(management.makePropertyKey(key).dataType(String.class).make());
            }
            management.makePropertyKey("other").dataType(String.class).make();
            index.buildMixedIndex(REINDEX_TEST_INDEX_BACKEND_NAME);
            management.commit();
            final Object id = graph.addVertex("first", "a", "second", "b", "third", "c", "other", "d").id();
            graph.tx().commit();

            final String group = "documentReads" + prefetching;
            final ItinerisTransaction tx = graph.buildTransaction().groupName(group).start();
            try {
                final ItinerisVertex vertex = tx.getVertex(id);
                final MixedIndexType mixedIndex = (MixedIndexType) StreamSupport.stream(
                        ((InternalRelationType) tx.getPropertyKey("first")).getKeyIndexes().spliterator(), false)
                    .filter(IndexType::isMixedIndex).findFirst().orElseThrow(AssertionError::new);
                final long before = MetricManager.INSTANCE.getCounter(group, "stores", "getSlice", "calls").getCount();
                final IndexSerializer serializer = ((StandardItineris) graph).getIndexSerializer();
                assertEquals(3, serializer.getCompleteDocument(vertex, mixedIndex).size());
                return MetricManager.INSTANCE.getCounter(group, "stores", "getSlice", "calls").getCount() - before;
            } finally {
                tx.rollback();
            }
        } finally {
            graph.close();
        }
    }

    // A reindex reads the document of each vertex the scan preloaded, whose properties answer the query of the
    // index's keys with and without property prefetching: each document holds the values of the index's two keys for
    // its vertex, and not the value of the key the index lacks
    @Test
    public void mixedIndexReindexRestoresEveryDocumentWhole() throws Exception {
        assertEveryDocumentRestoredWhole(true);
    }

    @Test
    public void mixedIndexReindexWithoutPropertyPrefetchingRestoresEveryDocumentWhole() throws Exception {
        assertEveryDocumentRestoredWhole(false);
    }

    private void assertEveryDocumentRestoredWhole(boolean prefetching) throws Exception {
        runMixedIndexReindex(true, prefetching);
        final List<List<IndexEntry>> documents = RecordingIndexProvider.getRestoredDocuments();
        final Set<String> nameFields = new HashSet<>();
        final Set<String> nickFields = new HashSet<>();
        final Set<Object> names = new HashSet<>();
        for (final List<IndexEntry> document : documents) {
            final Set<Object> values = document.stream().map(entry -> entry.value).collect(Collectors.toSet());
            final Object name = values.stream().filter(value -> value.toString().startsWith("value")).findFirst()
                .orElseThrow(() -> new AssertionError("no name in " + values));
            final String number = name.toString().substring("value".length());
            assertEquals(new HashSet<>(Arrays.asList(name, "nick" + number)), values);
            assertEquals(2, document.size());
            for (final IndexEntry entry : document) {
                (entry.value.equals(name) ? nameFields : nickFields).add(entry.field);
            }
            names.add(name);
        }
        // every document holds its name under one field, and its nick under another, the same two in each document
        assertEquals("the field of the names", 1, nameFields.size());
        assertEquals("the field of the nicks", 1, nickFields.size());
        assertNotEquals(nameFields, nickFields);
        assertEquals(REINDEX_TEST_VERTEX_COUNT, names.size());
        assertEquals(REINDEX_TEST_VERTEX_COUNT, documents.size());
    }

    private List<Integer> runMixedIndexReindex(boolean batchEnabled) throws Exception {
        return runMixedIndexReindex(batchEnabled, true);
    }

    private List<Integer> runMixedIndexReindex(boolean batchEnabled, boolean prefetching) throws Exception {
        RecordingIndexProvider.reset();
        ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
        config.set(GraphDatabaseConfiguration.STORAGE_BACKEND, "inmemory");
        config.set(GraphDatabaseConfiguration.PROPERTY_PREFETCHING, prefetching);
        config.set(GraphDatabaseConfiguration.PAGE_SIZE, REINDEX_TEST_STORAGE_PAGE_SIZE);
        config.set(GraphDatabaseConfiguration.INDEX_BACKEND, RecordingIndexProvider.class.getName(), REINDEX_TEST_INDEX_BACKEND_NAME);
        config.set(GraphDatabaseConfiguration.MIXED_INDEX_REINDEX_BATCH_ENABLED, batchEnabled);
        config.set(GraphDatabaseConfiguration.MIXED_INDEX_REINDEX_BATCH_SIZE, REINDEX_TEST_BATCH_SIZE);

        Itineris graph = ItinerisFactory.open(config.getConfiguration());
        try {
            createReindexTestData(graph);
            registerMixedIndex(graph);
            reindex(graph);
            return RecordingIndexProvider.getRestoreBatchSizes();
        } finally {
            graph.close();
        }
    }

    private void createReindexTestData(Itineris graph) {
        ItinerisManagement management = graph.openManagement();
        management.makePropertyKey("name").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        management.makePropertyKey("nickname").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        management.makePropertyKey("note").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        management.commit();

        IntStream.range(0, REINDEX_TEST_VERTEX_COUNT).forEach(vertexNumber -> graph.addVertex(
            "name", "value" + vertexNumber, "nickname", "nick" + vertexNumber, "note", "not indexed"));
        graph.tx().commit();
    }

    private void registerMixedIndex(Itineris graph) throws Exception {
        ItinerisManagement management = graph.openManagement();
        PropertyKey name = management.getPropertyKey("name");
        PropertyKey nickname = management.getPropertyKey("nickname");
        management.buildIndex(REINDEX_TEST_INDEX_NAME, Vertex.class).addKey(name).addKey(nickname)
            .buildMixedIndex(REINDEX_TEST_INDEX_BACKEND_NAME);
        management.commit();

        management = graph.openManagement();
        management.updateIndex(management.getGraphIndex(REINDEX_TEST_INDEX_NAME), SchemaAction.REGISTER_INDEX).get();
        management.commit();
        ManagementSystem.awaitGraphIndexStatus(graph, REINDEX_TEST_INDEX_NAME).status(SchemaStatus.REGISTERED).call();
    }

    private void reindex(Itineris graph) throws Exception {
        ItinerisManagement management = graph.openManagement();
        management.updateIndex(management.getGraphIndex(REINDEX_TEST_INDEX_NAME), SchemaAction.REINDEX, 1).get();
        management.commit();
        ManagementSystem.awaitGraphIndexStatus(graph, REINDEX_TEST_INDEX_NAME).status(SchemaStatus.ENABLED).call();
    }

    public static class RecordingIndexProvider implements IndexProvider {

        private static final IndexFeatures FEATURES = new IndexFeatures.Builder()
            .supportedStringMappings(com.forwardmeasure.itineris.core.schema.Mapping.TEXT, com.forwardmeasure.itineris.core.schema.Mapping.STRING)
            .supportsCardinality(Cardinality.SINGLE)
            .supportsCardinality(Cardinality.LIST)
            .supportsCardinality(Cardinality.SET)
            .build();

        private static final List<Integer> RESTORE_BATCH_SIZES = Collections.synchronizedList(new ArrayList<>());
        private static final List<List<IndexEntry>> RESTORED_DOCUMENTS =
            Collections.synchronizedList(new ArrayList<>());

        public RecordingIndexProvider(Configuration config) {
        }

        static void reset() {
            RESTORE_BATCH_SIZES.clear();
            RESTORED_DOCUMENTS.clear();
        }

        static List<List<IndexEntry>> getRestoredDocuments() {
            synchronized (RESTORED_DOCUMENTS) {
                return new ArrayList<>(RESTORED_DOCUMENTS);
            }
        }

        static List<Integer> getRestoreBatchSizes() {
            synchronized (RESTORE_BATCH_SIZES) {
                return new ArrayList<>(RESTORE_BATCH_SIZES);
            }
        }

        @Override
        public void register(String store, String key, KeyInformation information, BaseTransaction tx) {
        }

        @Override
        public void mutate(Map<String, Map<String, IndexMutation>> mutations, KeyInformation.IndexRetriever information,
                           BaseTransaction tx) {
        }

        @Override
        public void restore(Map<String, Map<String, List<IndexEntry>>> documents, KeyInformation.IndexRetriever information,
                            BaseTransaction tx) {
            int documentCount = documents.values().stream().mapToInt(Map::size).sum();
            if (documentCount > 0) {
                RESTORE_BATCH_SIZES.add(documentCount);
            }
            documents.values().forEach(store -> store.values().forEach(document ->
                RESTORED_DOCUMENTS.add(new ArrayList<>(document))));
        }

        @Override
        public Number queryAggregation(IndexQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx,
                                       Aggregation aggregation) {
            return 0;
        }

        @Override
        public Stream<String> query(IndexQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx) {
            return Stream.empty();
        }

        @Override
        public Stream<RawQuery.Result<String>> query(RawQuery query, KeyInformation.IndexRetriever information,
                                                     BaseTransaction tx) {
            return Stream.empty();
        }

        @Override
        public Long totals(RawQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx) {
            return 0L;
        }

        @Override
        public BaseTransactionConfigurable beginTransaction(BaseTransactionConfig config) throws BackendException {
            return new DefaultTransaction(config);
        }

        @Override
        public void close() {
        }

        @Override
        public void clearStorage() {
            reset();
        }

        @Override
        public void clearStore(String storeName) {
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean supports(KeyInformation information, ItinerisPredicate itinerisPredicate) {
            return true;
        }

        @Override
        public boolean supports(KeyInformation information) {
            return true;
        }

        @Override
        public String mapKey2Field(String key, KeyInformation information) {
            return key;
        }

        @Override
        public IndexFeatures getFeatures() {
            return FEATURES;
        }
    }

}
