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

package com.forwardmeasure.itineris.diskstorage.opensearch;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.core.attribute.Geo;
import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.core.attribute.Text;
import com.forwardmeasure.itineris.core.schema.Mapping;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.CommonsConfiguration;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexProvider;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexProviderTest;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexQuery;
import com.forwardmeasure.itineris.diskstorage.indexing.KeyInformation;
import com.forwardmeasure.itineris.diskstorage.indexing.StandardKeyInformation;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.internal.Order;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.types.ParameterType;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import com.google.common.base.Throwables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
@Testcontainers
public class OpenSearchIndexTest extends IndexProviderTest {

    @Container
    public static ItinerisOpenSearchContainer esr = new ItinerisOpenSearchContainer();
    static HttpHost host;
    static CloseableHttpClient httpClient;
    static ObjectMapper objectMapper;

    private static char REPLACEMENT_CHAR = '\u2022';

    @BeforeAll
    public static void prepareOpenSearch() throws Exception {
        httpClient = HttpClients.createDefault();
        objectMapper = new ObjectMapper();
        host = new HttpHost(InetAddress.getByName(esr.getHostname()), esr.getPort());
        IOUtils.closeQuietly(httpClient.execute(host, new HttpDelete("_ingest/pipeline/pipeline_1")));
        final HttpPut newPipeline = new HttpPut("_ingest/pipeline/pipeline_1");
        newPipeline.setHeader("Content-Type", "application/json");
        newPipeline
                .setEntity(new StringEntity("{\"description\":\"Test pipeline\",\"processors\":[{\"set\":{\"field\":\""
                        + STRING + "\",\"value\":\"hello\"}}]}", StandardCharsets.UTF_8));
        IOUtils.closeQuietly(httpClient.execute(host, newPipeline));
    }

    @AfterAll
    public static void cleanupOpenSearch() throws IOException {
        IOUtils.closeQuietly(httpClient.execute(host, new HttpDelete("itineris*")));
        IOUtils.closeQuietly(httpClient);
    }

    @Override
    public IndexProvider openIndex() throws BackendException {
        return new OpenSearchIndex(getESTestConfig());
    }

    @Override
    public boolean supportsLuceneStyleQueries() {
        return true;
    }

    @Override
    public String getEnglishAnalyzerName() {
        return "english";
    }

    @Override
    public String getKeywordAnalyzerName() {
        return "keyword";
    }

    @Override
    public Mapping preferredGeoShapeMapping() {
        return Mapping.BKD;
    }

    public Configuration getESTestConfig() {
        final String index = "es";
        final CommonsConfiguration cc = new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration());
        cc.set("index." + index + ".opensearch.ingest-pipeline.ingestvertex", "pipeline_1");
        return makeESTestConfig(index, cc);
    }

    public Configuration makeESTestConfig(String index, CommonsConfiguration cc) {
        return esr
                .setConfiguration(new ModifiableConfiguration(GraphDatabaseConfiguration.ROOT_NS, cc,
                        BasicConfiguration.Restriction.NONE), index)
                .set(GraphDatabaseConfiguration.INDEX_MAX_RESULT_SET_SIZE, 3, index)
                .restrictTo(index);
    }

    @Test
    public void testSupport() {
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE), Text.CONTAINS));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE), Text.NOT_CONTAINS));
        assertTrue(
                index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()), Text.CONTAINS_PREFIX));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()),
                Text.NOT_CONTAINS_PREFIX));
        assertTrue(
                index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()), Text.CONTAINS_PHRASE));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()),
                Text.NOT_CONTAINS_PHRASE));
        assertTrue(
                index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()), Text.CONTAINS_REGEX));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()),
                Text.NOT_CONTAINS_REGEX));
        assertTrue(
                index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()), Text.CONTAINS_FUZZY));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()),
                Text.NOT_CONTAINS_FUZZY));
        assertFalse(index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()), Text.REGEX));
        assertFalse(index.supports(of(String.class, Cardinality.SINGLE, Mapping.TEXT.asParameter()), Text.NOT_REGEX));
        assertFalse(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.CONTAINS));
        assertFalse(
                index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.NOT_CONTAINS));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.PREFIX));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.NOT_PREFIX));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.FUZZY));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.NOT_FUZZY));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.REGEX));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Text.NOT_REGEX));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Cmp.EQUAL));
        assertTrue(index.supports(of(String.class, Cardinality.SINGLE, Mapping.STRING.asParameter()), Cmp.NOT_EQUAL));

        assertTrue(index.supports(of(Date.class, Cardinality.SINGLE), Cmp.EQUAL));
        assertTrue(index.supports(of(Date.class, Cardinality.SINGLE), Cmp.LESS_THAN_EQUAL));
        assertTrue(index.supports(of(Date.class, Cardinality.SINGLE), Cmp.LESS_THAN));
        assertTrue(index.supports(of(Date.class, Cardinality.SINGLE), Cmp.GREATER_THAN));
        assertTrue(index.supports(of(Date.class, Cardinality.SINGLE), Cmp.GREATER_THAN_EQUAL));
        assertTrue(index.supports(of(Date.class, Cardinality.SINGLE), Cmp.NOT_EQUAL));

        assertTrue(index.supports(of(Boolean.class, Cardinality.SINGLE), Cmp.EQUAL));
        assertTrue(index.supports(of(Boolean.class, Cardinality.SINGLE), Cmp.NOT_EQUAL));

        assertTrue(index.supports(of(UUID.class, Cardinality.SINGLE), Cmp.EQUAL));
        assertTrue(index.supports(of(UUID.class, Cardinality.SINGLE), Cmp.NOT_EQUAL));

        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE)));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE), Geo.WITHIN));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE), Geo.INTERSECT));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE), Geo.DISJOINT));
        assertFalse(index.supports(of(Geoshape.class, Cardinality.SINGLE), Geo.CONTAINS));
        assertTrue(
                index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.PREFIX_TREE.asParameter()), Geo.WITHIN));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.PREFIX_TREE.asParameter()),
                Geo.INTERSECT));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.PREFIX_TREE.asParameter()),
                Geo.CONTAINS));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.PREFIX_TREE.asParameter()),
                Geo.DISJOINT));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.BKD.asParameter()), Geo.WITHIN));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.BKD.asParameter()), Geo.INTERSECT));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.BKD.asParameter()), Geo.CONTAINS));
        assertTrue(index.supports(of(Geoshape.class, Cardinality.SINGLE, Mapping.BKD.asParameter()), Geo.DISJOINT));
    }

    @Test
    public void testErrorInBatch() throws Exception {
        initialize("vertex");
        Multimap<String, Object> doc1 = HashMultimap.create();
        doc1.put(TIME, "not a time");

        add("vertex", "failing-doc", doc1, true);
        add("vertex", "non-failing-doc", getRandomDocument(), true);

        ItinerisException janusGraphException = assertThrows(ItinerisException.class, () -> {
            tx.commit();
        }, "Commit should not have succeeded.");

        String message = Throwables.getRootCause(janusGraphException).getMessage();

        assertTrue(message.contains("document_parsing_exception"));

        tx = null;
    }

    @Test
    public void testUnescapedDollarInSet() throws Exception {
        initialize("vertex");

        Multimap<String, Object> initialDoc = HashMultimap.create();
        initialDoc.put(PHONE_SET, "12345");

        add("vertex", "unescaped", initialDoc, true);

        clopen();

        Multimap<String, Object> updateDoc = HashMultimap.create();
        updateDoc.put(PHONE_SET, "$123");
        add("vertex", "unescaped", updateDoc, false);

        add("vertex", "other", getRandomDocument(), true);

        clopen();

        assertEquals("unescaped",
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(PHONE_SET, Cmp.EQUAL, "$123")))
                        .toArray()[0]);
        assertEquals("unescaped",
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(PHONE_SET, Cmp.EQUAL, "12345")))
                        .toArray()[0]);
    }

    /**
     * Test adding and overwriting with long string content.
     *
     */
    @Test
    public void testUpdateAdditionWithLongString() throws Exception {
        initialize("vertex");
        Multimap<String, Object> initialDoc = HashMultimap.create();
        initialDoc.put(TEXT,
                RandomStringUtils.randomAlphanumeric(500000) + " bob " + RandomStringUtils.randomAlphanumeric(500000));

        add("vertex", "long", initialDoc, true);

        clopen();

        assertEquals(1,
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT, Text.CONTAINS, "bob"))).count());
        assertEquals(0,
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT, Text.CONTAINS, "world"))).count());

        tx.add("vertex", "long", TEXT,
                RandomStringUtils.randomAlphanumeric(500000) + " world " + RandomStringUtils.randomAlphanumeric(500000),
                false);

        clopen();

        assertEquals(0,
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT, Text.CONTAINS, "bob"))).count());
        assertEquals(1,
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT, Text.CONTAINS, "world"))).count());
    }

    /**
     * Test ingest pipeline.
     */
    @Test
    public void testIngestPipeline() throws Exception {
        initialize("ingestvertex");
        final Multimap<String, Object> docs = HashMultimap.create();
        docs.put(TEXT, "bob");
        add("ingestvertex", "pipeline", docs, true);
        clopen();
        assertEquals(1,
                tx.queryStream(new IndexQuery("ingestvertex", PredicateCondition.of(TEXT, Text.CONTAINS, "bob")))
                        .count());
        assertEquals(1,
                tx.queryStream(new IndexQuery("ingestvertex", PredicateCondition.of(STRING, Cmp.EQUAL, "hello")))
                        .count());
    }

    @Test
    public void testMapKey2Field_IllegalCharacter() {
        assertThrows(IllegalArgumentException.class, () -> {
            index.mapKey2Field("here is an illegal character: " + REPLACEMENT_CHAR, null);
        });
    }

    @Test
    public void testMapKey2Field_MappingSpaces() {
        String expected = "field" + REPLACEMENT_CHAR + "name" + REPLACEMENT_CHAR + "with" + REPLACEMENT_CHAR + "spaces";
        assertEquals(expected, index.mapKey2Field("field name with spaces", null));
    }

    @Test
    public void testClearStorageWithAliases() throws Exception {
        IOUtils.closeQuietly(httpClient.execute(host, new HttpPut("test1")));
        IOUtils.closeQuietly(httpClient.execute(host, new HttpPut("test2")));
        final HttpPost addAlias = new HttpPost("_aliases");
        addAlias.setHeader("Content-Type", "application/json");
        addAlias.setEntity(new StringEntity(
                "{\"actions\": [{\"add\": {\"indices\": [\"test1\", \"test2\"], \"alias\": \"alias1\"}}]}",
                StandardCharsets.UTF_8));
        IOUtils.closeQuietly(httpClient.execute(host, addAlias));

        initialize("vertex");
        assertTrue(indexExists(GraphDatabaseConfiguration.INDEX_NAME.getDefaultValue()));

        index.clearStorage();

        assertFalse(indexExists(GraphDatabaseConfiguration.INDEX_NAME.getDefaultValue()));
        assertTrue(indexExists("test1"));
        assertTrue(indexExists("test2"));
    }

    @Test
    public void testCustomMappingProperty() throws BackendException, IOException, ParseException, URISyntaxException,
            org.apache.hc.core5.http.ParseException {

        String mappingTypeName = "vertex";
        String indexPrefix = "itineris";
        String parameterName = "store";
        Boolean parameterValue = true;

        String field = "field_with_custom_prop";

        KeyInformation keyInfo = new StandardKeyInformation(
                String.class,
                Cardinality.SINGLE,
                Mapping.STRING.asParameter(),
                Parameter.of(ParameterType.customParameterName(parameterName), parameterValue));

        index.register(mappingTypeName, field, keyInfo, tx);

        String indexName = indexPrefix + "_" + mappingTypeName;

        CloseableHttpResponse response = getESMapping(indexName, mappingTypeName);

        // Fallback to multitype index
        if (response.getCode() != 200) {
            indexName = indexPrefix;
            response = getESMapping(indexName, mappingTypeName);
        }

        HttpEntity entity = response.getEntity();

        JSONObject json = (JSONObject) new JSONParser().parse(EntityUtils.toString(entity));

        String returnedProperty = returnedProperty = retrieveValueFromJSON(json,
                indexName, "mappings", "properties", field, parameterName);

        assertEquals(parameterValue.toString(), returnedProperty);

        IOUtils.closeQuietly(response);
    }

    public static Stream<String> cardinalityTestCollectionNameParams() {
        return Stream.of(PHONE_SET, PHONE_LIST);
    }

    @ParameterizedTest
    @MethodSource("cardinalityTestCollectionNameParams")
    public void testCollectionCardinality(String collectionName) throws Exception {
        initialize("vertex");

        Multimap<String, Object> initialDoc = HashMultimap.create();
        initialDoc.put(collectionName, "12345");

        add("vertex", "test", initialDoc, true);

        clopen();

        Multimap<String, Object> updateDoc = HashMultimap.create();
        updateDoc.put(collectionName, "123456");

        add("vertex", "test", updateDoc, false);

        clopen();

        add("vertex", "test", initialDoc, false);

        clopen();

        tx.delete("vertex", "test", collectionName, "12345", false);

        clopen();

        assertEquals("test",
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(collectionName, Cmp.EQUAL, "123456")))
                        .toArray()[0]);

        if (PHONE_SET.equals(collectionName)) {
            assertEquals(0,
                    tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(PHONE_SET, Cmp.EQUAL, "12345")))
                            .count());
        } else {
            assertEquals("test",
                    tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(PHONE_LIST, Cmp.EQUAL, "12345")))
                            .toArray()[0]);
        }
    }

    @Test
    public void testTextStringMapping() throws Exception {
        initialize("vertex");

        Multimap<String, Object> firstDoc = HashMultimap.create();
        firstDoc.put(TEXT_STRING, "John Doe");

        Multimap<String, Object> secondDoc = HashMultimap.create();
        secondDoc.put(TEXT_STRING, "John");

        add("vertex", "test1", firstDoc, true);
        add("vertex", "test2", secondDoc, true);

        clopen();

        assertEquals(1, tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT_STRING, Cmp.EQUAL, "John")))
                .count());
        assertEquals(1,
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT_STRING, Cmp.EQUAL, "John Doe")))
                        .count());
        assertEquals(2,
                tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT_STRING, Text.CONTAINS, "John")))
                        .count());
    }

    @Test
    public void testTextStringSort() throws Exception {
        initialize("vertex");

        Multimap<String, Object> firstDoc = HashMultimap.create();
        firstDoc.put(TEXT_STRING, "John Doe");

        Multimap<String, Object> secondDoc = HashMultimap.create();
        secondDoc.put(TEXT_STRING, "Jane Doe");

        add("vertex", "test1", firstDoc, true);
        add("vertex", "test2", secondDoc, true);

        clopen();

        Object[] result = tx
                .queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT_STRING, Text.CONTAINS, "Doe"),
                        ImmutableList.of(new IndexQuery.OrderEntry(TEXT_STRING, Order.ASC, String.class))))
                .toArray();
        assertEquals("test2", result[0]);
        assertEquals("test1", result[1]);

        result = tx.queryStream(new IndexQuery("vertex", PredicateCondition.of(TEXT_STRING, Text.CONTAINS, "Doe"),
                ImmutableList.of(new IndexQuery.OrderEntry(TEXT_STRING, Order.DESC, String.class)))).toArray();
        assertEquals("test1", result[0]);
        assertEquals("test2", result[1]);
    }

    @Test
    public void testShouldNotShareIndexStoreNameCacheBetweenOpenSearchIndexInstances()
            throws BackendException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        final String index1 = "es1";
        final String index2 = "es2";

        final CommonsConfiguration cc1 = new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration());
        cc1.set("index." + index1 + ".opensearch.ingest-pipeline.ingestvertex", "pipeline_1");
        cc1.set("index." + index1 + ".opensearch.enable_index_names_cache", true);

        final CommonsConfiguration cc2 = new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration());
        cc1.set("index." + index2 + ".opensearch.ingest-pipeline.ingestvertex", "pipeline_1");
        cc1.set("index." + index2 + ".opensearch.enable_index_names_cache", true);

        Configuration configuration1 = makeESTestConfig(index1, cc1);
        Configuration configuration2 = makeESTestConfig(index2, cc2);

        OpenSearchIndex instance1 = new OpenSearchIndex(configuration1);
        OpenSearchIndex instance2 = new OpenSearchIndex(configuration2);

        String indexName1 = configuration1.get(INDEX_NAME);
        String indexName2 = configuration2.get(INDEX_NAME);

        Map<String, String> indexStoreNamesCache1 = (Map<String, String>) FieldUtils.readField(instance1,
                "indexStoreNamesCache", true);
        Map<String, String> indexStoreNamesCache2 = (Map<String, String>) FieldUtils.readField(instance2,
                "indexStoreNamesCache", true);

        Method method1 = instance1.getClass().getDeclaredMethod("getIndexStoreName", String.class);
        method1.setAccessible(true);

        Method method2 = instance1.getClass().getDeclaredMethod("getIndexStoreName", String.class);
        method2.setAccessible(true);

        String store = "Test_store";

        method1.invoke(instance1, store);
        assertEquals(1, indexStoreNamesCache1.size());
        assertEquals(0, indexStoreNamesCache2.size());

        method2.invoke(instance2, store);
        assertEquals(1, indexStoreNamesCache1.size());
        assertEquals(1, indexStoreNamesCache2.size());

        assertEquals(indexName1 + OpenSearchIndex.INDEX_NAME_SEPARATOR + store.toLowerCase(),
                indexStoreNamesCache1.get(store));
        assertEquals(indexName2 + OpenSearchIndex.INDEX_NAME_SEPARATOR + store.toLowerCase(),
                indexStoreNamesCache2.get(store));
    }

    @Test
    public void testShouldNotUseIndexStoreNameCache()
            throws BackendException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        final String index = "es1";

        final CommonsConfiguration cc = new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration());
        cc.set("index." + index + ".opensearch.ingest-pipeline.ingestvertex", "pipeline_1");
        cc.set("index." + index + ".opensearch.enable_index_names_cache", false);

        OpenSearchIndex instance = new OpenSearchIndex(makeESTestConfig(index, cc));

        Map<String, String> indexStoreNamesCache = (Map<String, String>) FieldUtils.readField(instance,
                "indexStoreNamesCache", true);

        Method method = instance.getClass().getDeclaredMethod("getIndexStoreName", String.class);
        method.setAccessible(true);

        String store = "Test_store";

        method.invoke(instance, store);
        assertEquals(0, indexStoreNamesCache.size());
    }

    private CloseableHttpResponse getESMapping(String indexName, String mappingTypeName)
            throws IOException, URISyntaxException {

        URIBuilder uriBuilder = new URIBuilder(indexName + "/_mapping");

        final HttpGet httpGet = new HttpGet(uriBuilder.build());
        return httpClient.execute(host, httpGet);
    }

    private String retrieveValueFromJSON(JSONObject json, String... hierarchy) {

        for (int i = 0; i < hierarchy.length; i++) {
            if (i + 1 == hierarchy.length) {
                return json.get(hierarchy[i]).toString();
            }
            json = (JSONObject) json.get(hierarchy[i]);
        }

        return null;
    }

    private boolean indexExists(String name) throws IOException {
        final CloseableHttpResponse response = httpClient.execute(host, new HttpHead(name));
        final boolean exists = response.getCode() == 200;
        IOUtils.closeQuietly(response);
        return exists;
    }
}
