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

package com.forwardmeasure.itineris.diskstorage.opensearch;

import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex.ALLOW_MAPPING_UPDATE;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex.USE_EXTERNAL_MAPPINGS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_HOSTS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.tinkerpop.shaded.jackson.core.type.TypeReference;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.attribute.Text;
import com.forwardmeasure.itineris.core.schema.Mapping;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.PermanentBackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.CommonsConfiguration;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexProvider;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexProviderTest;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexQuery;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexTransaction;
import com.forwardmeasure.itineris.diskstorage.indexing.KeyInformation;
import com.forwardmeasure.itineris.diskstorage.opensearch.mapping.IndexMapping;
import com.forwardmeasure.itineris.diskstorage.opensearch.mapping.TypelessIndexMappings;
import com.forwardmeasure.itineris.diskstorage.util.StandardBaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProviders;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;

/**
 * Test behavior Itineris ConfigOptions governing OpenSearch client setup.
 */
@Testcontainers
public class OpenSearchConfigTest {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchConfigTest.class);

    @Container
    public static final ItinerisOpenSearchContainer OSR = new ItinerisOpenSearchContainer();

    private static final String INDEX_NAME = "escfg";
    private static final String ANALYZER_KEYWORD = "keyword";
    private static final String ANALYZER_ENGLISH = "english";
    private static final String ANALYZER_STANDARD = "standard";

    private HttpHost host;
    private CloseableHttpClient httpClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() throws Exception {
        httpClient = HttpClients.createDefault();
        host = HttpHost.create("http://" + OSR.getHostname() + ":" + OSR.getPort());
        objectMapper = new ObjectMapper();

        try (CloseableHttpResponse response = httpClient.execute(host, new HttpDelete("/_index_template/template_1"))) {
            EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    @AfterEach
    public void teardown() throws Exception {
        try (CloseableHttpResponse response = httpClient.execute(host, new HttpDelete("/itineris*"))) {
            EntityUtils.consumeQuietly(response.getEntity());
        }
        IOUtils.closeQuietly(httpClient);
    }

    @Test
    public void testItinerisFactoryBuilder() {
        final ModifiableConfiguration config = OSR.setConfiguration(
                GraphDatabaseConfiguration.buildGraphConfiguration(), INDEX_NAME);
        config.set(GraphDatabaseConfiguration.STORAGE_BACKEND, "inmemory");
        final Itineris graph = ItinerisFactory.open(config.getConfiguration());
        assertTrue(graph.isOpen());
        graph.close();
    }

    @Test
    public void testClientThrowsExceptionIfServerNotReachable() throws BackendException, InterruptedException {
        final ModifiableConfiguration config = OSR
                .setConfiguration(GraphDatabaseConfiguration.buildGraphConfiguration(), INDEX_NAME);
        final Configuration indexConfig = config.restrictTo(INDEX_NAME);
        final IndexProvider idx = open(indexConfig);
        simpleWriteAndQuery(idx);
        idx.close();

        config.set(INDEX_HOSTS, new String[] { "localhost:" + (OSR.getPort() + 1) }, INDEX_NAME);
        final Configuration wrongHostConfig = config.restrictTo(INDEX_NAME);

        assertThrows(Exception.class, () -> new OpenSearchIndex(wrongHostConfig));
    }

    @Test
    public void testIndexCreationOptions() throws InterruptedException, BackendException, IOException {
        final int shards = 7;

        final CommonsConfiguration cc = new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration());
        cc.set("index." + INDEX_NAME + ".opensearch.create.ext.number_of_shards", String.valueOf(shards));

        final ModifiableConfiguration config = new ModifiableConfiguration(
                GraphDatabaseConfiguration.ROOT_NS,
                cc,
                BasicConfiguration.Restriction.NONE);

        OSR.setConfiguration(config, INDEX_NAME);

        final Configuration indexConfig = config.restrictTo(INDEX_NAME);
        final IndexProvider idx = open(indexConfig);
        simpleWriteAndQuery(idx);
        idx.close();

        final OpenSearchClient client = OpenSearchSetup.REST_CLIENT.connect(indexConfig).getClient();

        assertEquals(
                String.valueOf(shards),
                client.getIndexSettings("itineris_jvmlocal_test_store").get("number_of_shards"));

        client.close();
    }

    @Test
    public void testExternalMappingsViaMapping() throws Exception {
        final Duration maxWrite = Duration.ofMillis(2000L);
        final String storeName = "test_mapping";

        final ModifiableConfiguration modifiableConfiguration = OSR
                .setConfiguration(GraphDatabaseConfiguration.buildGraphConfiguration(), INDEX_NAME)
                .set(USE_EXTERNAL_MAPPINGS, true, INDEX_NAME);

        final Configuration indexConfig = modifiableConfiguration.restrictTo(INDEX_NAME);
        final IndexProvider idx = open(indexConfig);

        final KeyInformation.IndexRetriever indexRetriever = IndexProviderTest.getIndexRetriever(
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping()));

        final BaseTransactionConfig txConfig = StandardBaseTransactionConfig.of(TimestampProviders.MILLI);
        final IndexTransaction itx = new IndexTransaction(idx, indexRetriever, txConfig, maxWrite);

        try {
            idx.register(
                    storeName,
                    "date",
                    IndexProviderTest.getMapping(
                            idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                            .get("date"),
                    itx);
            fail("should fail");
        } catch (final PermanentBackendException e) {
            log.debug(e.getMessage(), e);
        }

        executeRequestWithStringEntity("/itineris_" + storeName,
                readTypelessMapping("/typeless_strict_mapping.json"));

        idx.register(
                storeName,
                "date",
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                        .get("date"),
                itx);

        try {
            idx.register(
                    storeName,
                    "weight",
                    IndexProviderTest.getMapping(
                            idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                            .get("weight"),
                    itx);
            fail("should fail");
        } catch (final BackendException e) {
            log.debug(e.getMessage(), e);
        }

        itx.rollback();
        idx.close();
    }

    private TypelessIndexMappings readTypelessMapping(final String mappingFilePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(mappingFilePath)) {
            final TypelessIndexMappings typelessIndexMappings = objectMapper.readValue(
                    inputStream,
                    new TypeReference<TypelessIndexMappings>() {
                    });

            final IndexMapping indexMappings = typelessIndexMappings.getMappings();
            if (indexMappings.getProperties().containsKey("boundary")) {
                indexMappings.getProperties().put("boundary", Collections.singletonMap("type", "geo_shape"));
            }

            return typelessIndexMappings;
        }
    }

    @Test
    public void testExternalDynamic() throws Exception {
        testExternalDynamic(false);
    }

    @Test
    public void testUpdateExternalDynamicMapping() throws Exception {
        testExternalDynamic(true);
    }

    @Test
    public void testExternalMappingsViaTemplate() throws Exception {
        final Duration maxWrite = Duration.ofMillis(2000L);
        final String storeName = "test_mapping";

        final ModifiableConfiguration modifiableConfiguration = OSR
                .setConfiguration(GraphDatabaseConfiguration.buildGraphConfiguration(), INDEX_NAME)
                .set(USE_EXTERNAL_MAPPINGS, true, INDEX_NAME);

        final Configuration indexConfig = modifiableConfiguration.restrictTo(INDEX_NAME);
        final IndexProvider idx = open(indexConfig);

        final Map<String, Object> content = new HashMap<>(2);
        content.put("index_patterns", Collections.singletonList("itineris_test_mapping*"));
        content.put(
                "template",
                Collections.singletonMap("mappings",
                        readTypelessMapping("/typeless_strict_mapping.json").getMappings()));

        executeRequestWithStringEntity("/_index_template/template_1", content);

        final HttpPut newMapping = new HttpPut("/itineris_" + storeName);
        executeRequest(newMapping);

        final KeyInformation.IndexRetriever indexRetriever = IndexProviderTest.getIndexRetriever(
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping()));

        final BaseTransactionConfig txConfig = StandardBaseTransactionConfig.of(TimestampProviders.MILLI);
        final IndexTransaction itx = new IndexTransaction(idx, indexRetriever, txConfig, maxWrite);

        idx.register(
                storeName,
                "date",
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                        .get("date"),
                itx);

        try {
            idx.register(
                    storeName,
                    "weight",
                    IndexProviderTest.getMapping(
                            idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                            .get("weight"),
                    itx);
            fail("should fail");
        } catch (final BackendException e) {
            log.debug(e.getMessage(), e);
        }

        itx.rollback();
        idx.close();
    }

    private void simpleWriteAndQuery(final IndexProvider idx) throws BackendException, InterruptedException {
        final Duration maxWrite = Duration.ofMillis(2000L);
        final String storeName = "jvmlocal_test_store";

        final KeyInformation.IndexRetriever indexRetriever = IndexProviderTest.getIndexRetriever(
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_STANDARD, ANALYZER_KEYWORD, preferredGeoShapeMapping()));

        final BaseTransactionConfig txConfig = StandardBaseTransactionConfig.of(TimestampProviders.MILLI);
        IndexTransaction itx = new IndexTransaction(idx, indexRetriever, txConfig, maxWrite);

        for (final Entry<String, KeyInformation> entry : IndexProviderTest
                .getMapping(idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                .entrySet()) {
            idx.register(storeName, entry.getKey(), entry.getValue(), itx);
        }

        assertEquals(
                0,
                itx.queryStream(
                        new IndexQuery(storeName, PredicateCondition.of(IndexProviderTest.NAME, Text.PREFIX, "ali")))
                        .count());

        itx.add(storeName, "doc", IndexProviderTest.NAME, "alice", false);
        itx.commit();

        Thread.sleep(1500L);

        itx = new IndexTransaction(idx, indexRetriever, txConfig, maxWrite);

        assertEquals(
                0,
                itx.queryStream(
                        new IndexQuery(storeName, PredicateCondition.of(IndexProviderTest.NAME, Text.PREFIX, "zed")))
                        .count());

        assertEquals(
                1,
                itx.queryStream(
                        new IndexQuery(storeName, PredicateCondition.of(IndexProviderTest.NAME, Text.PREFIX, "ali")))
                        .count());

        itx.rollback();
    }

    private void executeRequest(final HttpUriRequestBase request) throws IOException, ParseException {
        request.setHeader("Content-Type", "application/json");

        try (CloseableHttpResponse res = httpClient.execute(host, request)) {
            final int statusCode = res.getCode();
            final String entity = res.getEntity() != null ? EntityUtils.toString(res.getEntity()) : "";

            if (statusCode < 200 || statusCode >= 300 || entity.contains("error")) {
                fail("Failed to execute request: " + request + ". Entity: " + entity);
            }
        }
    }

    private IndexProvider open(final Configuration indexConfig) throws BackendException {
        final OpenSearchIndex idx = new OpenSearchIndex(indexConfig);
        idx.clearStorage();
        idx.close();
        return new OpenSearchIndex(indexConfig);
    }

    private void testExternalDynamic(final boolean withUpdateMapping) throws Exception {
        final Duration maxWrite = Duration.ofMillis(2000L);
        final String storeName = "test_mapping";

        final Configuration indexConfig = buildIndexConfigurationForExternalDynamic(withUpdateMapping);
        final IndexProvider idx = open(indexConfig);

        final KeyInformation.IndexRetriever indexRetriever = IndexProviderTest.getIndexRetriever(
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping()));

        final BaseTransactionConfig txConfig = StandardBaseTransactionConfig.of(TimestampProviders.MILLI);
        final IndexTransaction itx = new IndexTransaction(idx, indexRetriever, txConfig, maxWrite);

        try {
            idx.register(
                    storeName,
                    "date",
                    IndexProviderTest.getMapping(
                            idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                            .get("date"),
                    itx);
            fail("should fail");
        } catch (final PermanentBackendException e) {
            log.debug(e.getMessage(), e);
        }

        executeRequestWithStringEntity("/itineris_" + storeName,
                readTypelessMapping("/typeless_dynamic_mapping.json"));

        idx.register(
                storeName,
                "date",
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                        .get("date"),
                itx);

        idx.register(
                storeName,
                "weight",
                IndexProviderTest.getMapping(
                        idx.getFeatures(), ANALYZER_ENGLISH, ANALYZER_KEYWORD, preferredGeoShapeMapping())
                        .get("weight"),
                itx);

        itx.rollback();
        idx.close();

        final OpenSearchClient client = OpenSearchSetup.REST_CLIENT.connect(indexConfig).getClient();
        final Map<String, Object> properties = client.getMapping("itineris_" + storeName, storeName).getProperties();

        assertEquals(withUpdateMapping, properties.containsKey("weight"), properties.toString());
    }

    private Configuration buildIndexConfigurationForExternalDynamic(final boolean withUpdateMapping) {
        ModifiableConfiguration indexConfig = GraphDatabaseConfiguration.buildGraphConfiguration()
                .set(USE_EXTERNAL_MAPPINGS, true, INDEX_NAME);

        indexConfig = indexConfig.set(INDEX_HOSTS, new String[] { OSR.getHostname() }, INDEX_NAME);
        indexConfig = indexConfig.set(INDEX_PORT, OSR.getPort(), INDEX_NAME);

        if (withUpdateMapping) {
            indexConfig = indexConfig.set(ALLOW_MAPPING_UPDATE, true, INDEX_NAME);
        }

        return indexConfig.restrictTo(INDEX_NAME);
    }

    private void executeRequestWithStringEntity(final String endpoint, final Object content)
            throws IOException, ParseException {
        final HttpPut request = new HttpPut(endpoint);
        request.setEntity(new StringEntity(objectMapper.writeValueAsString(content), StandardCharsets.UTF_8));
        executeRequest(request);
    }

    private Mapping preferredGeoShapeMapping() {
        return Mapping.BKD;
    }
}

