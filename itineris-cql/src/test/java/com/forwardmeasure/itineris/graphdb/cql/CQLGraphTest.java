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

package com.forwardmeasure.itineris.graphdb.cql;

import io.github.artsok.RepeatedIfExceptionsTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.Multiplicity;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.diskstorage.PermanentBackendException;
import com.forwardmeasure.itineris.diskstorage.TemporaryBackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.diskstorage.cql.strategy.GroupedExecutionStrategyBuilder;
import com.forwardmeasure.itineris.diskstorage.util.backpressure.SemaphoreQueryBackPressure;
import com.forwardmeasure.itineris.diskstorage.util.backpressure.builder.QueryBackPressureBuilder;
import com.forwardmeasure.itineris.graphdb.ItinerisTest;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.MultiQueryPropertiesStrategyMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.ATOMIC_BATCH_MUTATE;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.BACK_PRESSURE_CLASS;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.BACK_PRESSURE_LIMIT;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.BATCH_STATEMENT_SIZE;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.KEYS_GROUPING_ALLOWED;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.KEYS_GROUPING_CLASS;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.KEYS_GROUPING_LIMIT;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.KEYS_GROUPING_MIN;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.LOCAL_MAX_CONNECTIONS_PER_HOST;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.MAX_REQUESTS_PER_CONNECTION;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.REMOTE_MAX_CONNECTIONS_PER_HOST;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.SLICE_GROUPING_ALLOWED;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.SLICE_GROUPING_LIMIT;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ASSIGN_TIMESTAMP;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.LIMITED_BATCH;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.PAGE_SIZE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.PROPERTIES_BATCH_MODE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.PROPERTY_PREFETCHING;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.USE_MULTIQUERY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import org.junit.jupiter.api.Tag;
import com.forwardmeasure.itineris.TestCategory;

@Testcontainers
public class CQLGraphTest extends ItinerisTest {

    private final Logger log = LoggerFactory.getLogger(CQLGraphTest.class);

    @Container
    public static final ItinerisCassandraContainer cqlContainer = new ItinerisCassandraContainer();

    protected static Stream<Arguments> generateSemaphoreBackPressureConfigs() {
        return Arrays.stream(new Arguments[]{
            arguments(0, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(1, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(2, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(3, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(10, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(100, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(1000, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(1500, QueryBackPressureBuilder.SEMAPHORE_QUERY_BACK_PRESSURE_CLASS),
            arguments(0, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(1, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(2, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(3, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(10, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(100, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(1000, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
            arguments(1500, QueryBackPressureBuilder.SEMAPHORE_RELEASE_PROTECTED_QUERY_BACK_PRESSURE_CLASS),
        });
    }

    protected static Stream<Arguments> generateGroupingConfigs() {

        // We should always disable keys grouping option for cases when CQL storage backend doesn't support PER PARTITION LIMIT.
        boolean keysGroupingAllowed = ItinerisCassandraContainer.supportsPerPartitionLimit();
        List<Arguments> argumentsList = new ArrayList<>();
        if(keysGroupingAllowed){
            argumentsList.add(
                arguments(1, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 2})
            );
            argumentsList.add(
                arguments(100, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 2})
            );
            argumentsList.add(
                arguments(2000, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 2})
            );
            argumentsList.add(
                arguments(1, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 200})
            );
            argumentsList.add(
                arguments(100, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 200})
            );
            argumentsList.add(
                arguments(2000, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 200})
            );
            argumentsList.add(
                arguments(1, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 2})
            );
            argumentsList.add(
                arguments(100, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 2})
            );
            argumentsList.add(
                arguments(2000, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                    option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                    option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_LIMIT), 100,
                    option(KEYS_GROUPING_MIN), 2})
            );
        }

        argumentsList.add(
            arguments(1, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), false, option(KEYS_GROUPING_LIMIT), 100,
                option(KEYS_GROUPING_MIN), 2})
        );
        argumentsList.add(
            arguments(100, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), false, option(KEYS_GROUPING_LIMIT), 100,
                option(KEYS_GROUPING_MIN), 2})
        );
        argumentsList.add(
            arguments(2000, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), false, option(KEYS_GROUPING_LIMIT), 100,
                option(KEYS_GROUPING_MIN), 2})
        );

        argumentsList.add(
            arguments(1, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), false, option(KEYS_GROUPING_LIMIT), 100,
                option(KEYS_GROUPING_MIN), 2})
        );
        argumentsList.add(
            arguments(100, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), false, option(KEYS_GROUPING_LIMIT), 100,
                option(KEYS_GROUPING_MIN), 2})
        );
        argumentsList.add(
            arguments(2000, new Object[]{option(USE_MULTIQUERY), true, option(PROPERTY_PREFETCHING), false, option(LIMITED_BATCH), true,
                option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), false, option(KEYS_GROUPING_LIMIT), 100,
                option(KEYS_GROUPING_MIN), 2})
        );

        return argumentsList.stream();
    }

    @Override
    public WriteConfiguration getConfiguration() {
        return cqlContainer.getConfiguration(getClass().getSimpleName()).getConfiguration();
    }

    @Override
    @Test
    @Disabled("testLimitBatchSizeForMultiQueryRepeatStep: fails on CQL backend - see InMemoryGraphTest suppression, needs investigation")
    public void testLimitBatchSizeForMultiQueryRepeatStep() {
    }

    @Override
    @Test
    public void testLimitBatchSizeForMultiQuery() {
        super.testLimitBatchSizeForMultiQuery();
    }

    @Test
    public void testHasTTL() {
        assertTrue(features.hasCellTTL());
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Override
    public void simpleLogTest(boolean useStringId) throws InterruptedException {
        super.simpleLogTest(useStringId);
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1457
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Override
    public void simpleLogTestWithFailure(boolean useStringId) throws InterruptedException {
        super.simpleLogTestWithFailure(useStringId);
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1497
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testEdgeTTLTiming() throws Exception {
        super.testEdgeTTLTiming();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1464
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testVertexTTLWithCompositeIndex() throws Exception {
        super.testVertexTTLWithCompositeIndex();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1465
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testVertexTTLImplicitKey() throws Exception {
        super.testVertexTTLImplicitKey();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/3142
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testReindexingForEdgeIndex() throws ExecutionException, InterruptedException {
        super.testReindexingForEdgeIndex();
    }

    protected static Stream<Arguments> generateConsistencyConfigs() {
        return Arrays.stream(new Arguments[]{
            arguments(true, true, 20),
            arguments(true, false, 20),
            arguments(true, false, 1),
            arguments(false, true, 20),
            arguments(false, false, 20),
            arguments(false, false, 1),
        });
    }

    @Override
    @Test
    @Disabled
    public void testConsistencyEnforcement() {
        // disable original test in favour of parameterized test
    }

    @ParameterizedTest
    @MethodSource("generateConsistencyConfigs")
    public void testConsistencyEnforcement(boolean assignTimestamp, boolean atomicBatch, int batchSize) {
        clopen(option(ASSIGN_TIMESTAMP), assignTimestamp, option(ATOMIC_BATCH_MUTATE), atomicBatch, option(BATCH_STATEMENT_SIZE), batchSize);
        super.testConsistencyEnforcement();
    }

    @Override
    @Test
    @Disabled
    public void testConcurrentConsistencyEnforcement() {
        // disable original test in favour of parameterized test
    }

    @ParameterizedTest
    @MethodSource("generateConsistencyConfigs")
    public void testConcurrentConsistencyEnforcement(boolean assignTimestamp, boolean atomicBatch, int batchSize) throws Exception {
        clopen(option(ASSIGN_TIMESTAMP), assignTimestamp, option(ATOMIC_BATCH_MUTATE), atomicBatch, option(BATCH_STATEMENT_SIZE), batchSize);
        super.testConcurrentConsistencyEnforcement();
    }

    @Test
    public void testQueryLongForPropertyKey() {
        PropertyKey name = mgmt.makePropertyKey("name").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.buildIndex("nameIndex", Vertex.class).addKey(name).buildCompositeIndex();
        finishSchema();

        /* We need to generate a string that is not simple to force the CQL
         * query to throw a ServerError because the key exceeds 64k, which is a
         * compressed form of the string.  This key ends up being 75224 bytes. */
        String testName = RandomStringUtils.random(100000, 0, 0, true, true, null, new Random(0));

        GraphTraversalSource g = graph.traversal();
        ItinerisException ex = assertThrows(ItinerisException.class,
            () -> g.V().has("name", testName).hasNext());
        assertEquals(-1, ExceptionUtils.indexOfType(ex, TemporaryBackendException.class),
            "Query should not produce a TemporaryBackendException");
        assertNotEquals(-1, ExceptionUtils.indexOfType(ex, PermanentBackendException.class),
            "Query should produce a PermanentBackendException");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 10, 100, 1000})
    public void fetchElementsUsingDifferentPageSize(int pageSize) {
        clopen(option(USE_MULTIQUERY), true, option(PAGE_SIZE), pageSize);
        assertSingleTxAdditionAndCount(10, pageSize * 3);
    }

    @ParameterizedTest
    @MethodSource("generateSemaphoreBackPressureConfigs")
    public void testDifferentBackpressureLimitIsApplicable(int backPressureLimit, String backPressureClass) {
        clopen(option(BACK_PRESSURE_LIMIT), backPressureLimit,
            option(BACK_PRESSURE_CLASS), backPressureClass,
            option(MAX_REQUESTS_PER_CONNECTION), Math.max(MAX_REQUESTS_PER_CONNECTION.getDefaultValue(), backPressureLimit),
            option(USE_MULTIQUERY), true);
        assertSingleTxAdditionAndCount(10, 20);
    }

    @Test
    public void testBackpressureIsDisabled() {
        clopen(option(BACK_PRESSURE_CLASS), QueryBackPressureBuilder.PASS_ALL_QUERY_BACK_PRESSURE_CLASS,
            option(USE_MULTIQUERY), true, option(MAX_REQUESTS_PER_CONNECTION), 1024, option(LOCAL_MAX_CONNECTIONS_PER_HOST), 20,
            option(REMOTE_MAX_CONNECTIONS_PER_HOST), 1);
        assertSingleTxAdditionAndCount(10, 20);
    }

    @Test
    public void testCustomBackPressureClassIsSet() {
        CustomQueryBackPressure.acquireIsUsed = false;
        CustomQueryBackPressure.releaseIsUsed = false;
        clopen(option(BACK_PRESSURE_CLASS), CustomQueryBackPressure.class.getName(), option(USE_MULTIQUERY), true);
        assertSingleTxAdditionAndCount(10, 20);
        assertTrue(CustomQueryBackPressure.acquireIsUsed);
        assertTrue(CustomQueryBackPressure.releaseIsUsed);
    }

    @Override @Test @Disabled("Use Parametrized test instead")
    public void testLimitBatchSizeForMultiQueryMultiCardinalityProperties(){
        // ignored. Used testLimitBatchSizeForMultiQueryMultiCardinalityProperties(boolean sliceGroupingAllowed) instead
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testLimitBatchSizeForMultiQueryMultiCardinalityProperties(boolean sliceGroupingAllowed){
        ItinerisVertex[] cs = setupDataForMultiQueryMultiCardinalityProperties();
        int barrierSize = 27;
        // test batching for `values()`
        TraversalMetrics profile = testLimitedBatch(() -> graph.traversal().V(cs).barrier(barrierSize).values("foo", "setProperty", "listProperty"),
            option(USE_MULTIQUERY), true,
            option(LIMITED_BATCH), true,
            option(PROPERTY_PREFETCHING), false,
            option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
            option(SLICE_GROUPING_ALLOWED), sliceGroupingAllowed);
        assertEquals(3, countBackendQueriesOfSize(barrierSize + barrierSize * 4 + barrierSize * 4, profile.getMetrics()));
        int lastBatch = cs.length - 3 * barrierSize;
        assertEquals(1, countBackendQueriesOfSize(lastBatch + lastBatch * 4 + lastBatch * 4, profile.getMetrics()));
    }

    @MethodSource("generateGroupingConfigs")
    @ParameterizedTest
    public void testBatchWithCQLGrouping(int elementsAmount, Object[] configuration){

        mgmt.makeVertexLabel("testVertex").make();
        mgmt.makePropertyKey("singleProperty1").cardinality(Cardinality.SINGLE).dataType(String.class).make();
        mgmt.makePropertyKey("singleProperty2").cardinality(Cardinality.SINGLE).dataType(String.class).make();
        mgmt.makePropertyKey("singleProperty3").cardinality(Cardinality.SINGLE).dataType(String.class).make();
        mgmt.makePropertyKey("setProperty").cardinality(Cardinality.SET).dataType(String.class).make();
        mgmt.makePropertyKey("listProperty").cardinality(Cardinality.LIST).dataType(String.class).make();
        finishSchema();
        ItinerisVertex[] cs = new ItinerisVertex[elementsAmount];
        for (int i = 0; i < elementsAmount; ++i) {
            cs[i] = graph.addVertex("testVertex");
            cs[i].property("singleProperty1", "single value1 "+i);
            cs[i].property("singleProperty2", "single value1 "+i);
            cs[i].property("singleProperty3", "single value1 "+i);
            cs[i].property(VertexProperty.Cardinality.set, "setProperty", "setValue1");
            cs[i].property(VertexProperty.Cardinality.set, "setProperty", "setValue2");
            cs[i].property(VertexProperty.Cardinality.set, "setProperty", "setValue3");
            cs[i].property(VertexProperty.Cardinality.set, "setProperty", "setValue4");
            cs[i].property(VertexProperty.Cardinality.list, "listProperty", "listValue1");
            cs[i].property(VertexProperty.Cardinality.list, "listProperty", "listValue2");
            cs[i].property(VertexProperty.Cardinality.list, "listProperty", "listValue3");
            cs[i].property(VertexProperty.Cardinality.list, "listProperty", "listValue4");
        }

        newTx();

        clopen(configuration);

        // test batching for `values()`
        TraversalMetrics profile = testGroupingBatch(() -> graph.traversal().V(cs).barrier(elementsAmount).values("singleProperty1", "singleProperty2", "singleProperty3", "setProperty", "listProperty"), configuration);
        assertEquals(1, countBackendQueriesOfSize(elementsAmount * 11, profile.getMetrics()));
    }

    protected TraversalMetrics testGroupingBatch(Supplier<GraphTraversal<?, ?>> traversal, Object... settings){
        clopen(settings);
        final List<?> resultWithConfiguredOptions = traversal.get().toList();
        if(ItinerisCassandraContainer.supportsPerPartitionLimit()){
            clopen(option(USE_MULTIQUERY), true, option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_CLASS), GroupedExecutionStrategyBuilder.REPLICAS_AWARE);
            final List<?> resultWithKeysEnabledSliceEnabledReplicasAware = traversal.get().toList();
            assertEquals(resultWithKeysEnabledSliceEnabledReplicasAware, resultWithConfiguredOptions);
            clopen(option(USE_MULTIQUERY), true, option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_CLASS), GroupedExecutionStrategyBuilder.REPLICAS_AWARE);
            final List<?> resultWithKeysEnabledSliceDisabledReplicasAware = traversal.get().toList();
            assertEquals(resultWithKeysEnabledSliceDisabledReplicasAware, resultWithConfiguredOptions);
            clopen(option(USE_MULTIQUERY), true, option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_CLASS), GroupedExecutionStrategyBuilder.TOKEN_RANGE_AWARE);
            final List<?> resultWithKeysEnabledSliceEnabledTokenRangeAware = traversal.get().toList();
            assertEquals(resultWithKeysEnabledSliceEnabledTokenRangeAware, resultWithConfiguredOptions);
            clopen(option(USE_MULTIQUERY), true, option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
                option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), true, option(KEYS_GROUPING_CLASS), GroupedExecutionStrategyBuilder.TOKEN_RANGE_AWARE);
            final List<?> resultWithKeysEnabledSliceDisabledTokenRangeAware = traversal.get().toList();
            assertEquals(resultWithKeysEnabledSliceDisabledTokenRangeAware, resultWithConfiguredOptions);
        }
        clopen(option(USE_MULTIQUERY), true, option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
            option(SLICE_GROUPING_ALLOWED), true, option(KEYS_GROUPING_ALLOWED), false);
        final List<?> resultWithKeysDisabledSliceEnabled = traversal.get().toList();
        assertEquals(resultWithKeysDisabledSliceEnabled, resultWithConfiguredOptions);
        clopen(option(USE_MULTIQUERY), true, option(PROPERTIES_BATCH_MODE), MultiQueryPropertiesStrategyMode.REQUIRED_PROPERTIES_ONLY.getConfigName(),
            option(SLICE_GROUPING_ALLOWED), false, option(KEYS_GROUPING_ALLOWED), false);
        final List<?> resultWithKeysDisabledSliceDisabled = traversal.get().toList();
        assertEquals(resultWithKeysDisabledSliceDisabled, resultWithConfiguredOptions);
        clopen(settings);
        return traversal.get().profile().next();
    }

    @Override @Test @Disabled("Use Parametrized test instead")
    public void testMultiQueryPropertiesWithLimit() {
        // ignored. Used testMultiQueryPropertiesWithLimit(boolean sliceGroupingAllowed) instead
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testMultiQueryPropertiesWithLimit(boolean sliceGroupingAllowed) {
        ItinerisVertex[] cs = setupDataForMultiQueryMultiCardinalityProperties();
        clopen(option(USE_MULTIQUERY), true, option(LIMITED_BATCH), true, option(PROPERTY_PREFETCHING), false,
            option(SLICE_GROUPING_ALLOWED), sliceGroupingAllowed);
        verityMultiQueryPropertiesWithLimit(cs);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 10, 100, 1000, Integer.MAX_VALUE})
    public void testMultiQuerySliceGroupingLimit(int sliceGroupingLimit) {
        clopen(option(USE_MULTIQUERY), true, option(LIMITED_BATCH), true, option(PROPERTY_PREFETCHING), false,
            option(SLICE_GROUPING_ALLOWED), true, option(SLICE_GROUPING_LIMIT), sliceGroupingLimit);

        mgmt.makeVertexLabel("testVertex").make();
        finishSchema();
        int numV = 100;
        int propsCount = sliceGroupingLimit > 100 ? 100 : sliceGroupingLimit*3;
        if(propsCount == 0){
            propsCount = 1;
        }

        ItinerisVertex[] cs = new ItinerisVertex[numV];
        for (int i = 0; i < numV; ++i) {
            cs[i] = graph.addVertex("testVertex");
            for(int j=0; j<propsCount;j++){
                cs[i].property("foo"+j, "bar"+j);
            }
        }
        newTx();

        cs = graph.traversal().V(cs).toList().toArray(new ItinerisVertex[0]);

        String[] keys = new String[propsCount];
        for(int j=0; j<propsCount;j++){
            keys[j] = "foo"+j;
        }

        for(Integer limit : Arrays.asList(1, 2, 3, 5, 10, 15, 100, 150, 1000, 2000, Integer.MAX_VALUE-100)){
            Map<ItinerisVertex, Iterable<ItinerisVertexProperty>> properties = graph.multiQuery(cs[0], cs[1], cs[3]).limit(limit).keys(keys).properties();
            Map<ItinerisVertex, Map<String, Object>> deserializedProperties = new HashMap<>();

            Assertions.assertEquals(3, properties.size());

            properties.forEach((janusGraphVertex, janusGraphVertexProperties) -> {
                Map<String, Object> props = new HashMap<>();
                janusGraphVertexProperties.forEach(janusGraphVertexProperty -> {
                    ((ArrayList) props.computeIfAbsent(janusGraphVertexProperty.key(), k -> new ArrayList<>())).add(janusGraphVertexProperty.value());
                });
                deserializedProperties.put(janusGraphVertex, props);
            });

            for(Map<String, Object> vertexProps : deserializedProperties.values()){
                int numOfValues = 0;
                for(Object val : vertexProps.values()){
                    numOfValues+= ((List) val).size();
                }
                Assertions.assertEquals(Math.min(propsCount, limit), numOfValues);
            }
        }
    }

    private void assertSingleTxAdditionAndCount(int indexedVerticesCount, int adjacentVerticesCount){
        PropertyKey name = mgmt.makePropertyKey("name").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.buildIndex("nameIndex", Vertex.class).addKey(name).buildCompositeIndex();
        mgmt.makeEdgeLabel("testEdge").multiplicity(Multiplicity.SIMPLE).make();
        finishSchema();

        GraphTraversalSource g = graph.traversal();
        for(int i=0; i < indexedVerticesCount; i++){
            Vertex indexedVertex = g.addV().property("name", "testName").next();
            for(int j=0; j<adjacentVerticesCount; j++){
                Vertex adjacentVertex = g.addV().next();
                indexedVertex.addEdge("testEdge", adjacentVertex);
            }
        }

        g.tx().commit();

        int idsCount = g.V().has("name", "testName").out("testEdge").id().toList().size();

        assertEquals(indexedVerticesCount * adjacentVerticesCount, idsCount);
    }

    public static class CustomQueryBackPressure extends SemaphoreQueryBackPressure{

        public static volatile boolean acquireIsUsed;
        public static volatile boolean releaseIsUsed;

        public CustomQueryBackPressure(Configuration configuration, Integer backPressureLimit) {
            super(backPressureLimit);
            // configuration ignored
        }

        @Override
        public void acquireBeforeQuery() {
            acquireIsUsed = true;
            super.acquireBeforeQuery();
        }

        @Override
        public void releaseAfterQuery(){
            releaseIsUsed = true;
            super.releaseAfterQuery();
        }
    }

    @Override
    @Test
    @Tag(TestCategory.BRITTLE_TESTS)
    public void testIndexUpdateSyncWithMultipleInstances() throws InterruptedException {
        super.testIndexUpdateSyncWithMultipleInstances();
    }

    @Override
    @Test
    @Tag(TestCategory.BRITTLE_TESTS)
    public void testIndexShouldRegisterWhenWeRemoveAnInstance() throws InterruptedException {
        super.testIndexShouldRegisterWhenWeRemoveAnInstance();
    }

    @Override
    @Test
    @Tag(TestCategory.BRITTLE_TESTS)
    public void testEdgeTTLWithIndex() throws Exception {
        super.testEdgeTTLWithIndex();
    }

    @Override
    @Test
    @Tag(TestCategory.BRITTLE_TESTS)
    public void testPropertyTTLTiming() throws Exception {
        super.testPropertyTTLTiming();
    }

    @Override
    @Test
    @Tag(TestCategory.BRITTLE_TESTS)
    @Disabled("flaky test:")
    public void testEdgeTTLLimitedByVertexTTL() throws Exception {
        super.testEdgeTTLLimitedByVertexTTL();
    }
}
