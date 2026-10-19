// Copyright 2020 JanusGraph Authors
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

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.FORCE_INDEX_USAGE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_NAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.LOG_READ_INTERVAL;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.LOG_SEND_DELAY;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.MANAGEMENT_LOG;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.stream.IntStream;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;

import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.attribute.Geo;
import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.core.schema.Mapping;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.diskstorage.log.kcvs.KCVSLog;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.MixedIndexUtilsConfigOptions;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.processor.DynamicErrorDistanceCircleProcessor;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.processor.FixedErrorDistanceCircleProcessor;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.processor.NoTransformCircleProcessor;
import com.forwardmeasure.itineris.graphdb.ItinerisIndexTest;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;

public abstract class OpenSearchItinerisIndexTest extends ItinerisIndexTest {
    @Container
    protected static ItinerisOpenSearchContainer container = new ItinerisOpenSearchContainer();

    public OpenSearchItinerisIndexTest() {
        super(true, true, true);
    }

    @Override
    public WriteConfiguration getConfiguration() {
        String[] indexBackends = getIndexBackends();
        ModifiableConfiguration config = container.setConfiguration(getStorageConfiguration(), indexBackends);
        for (String indexBackend : indexBackends) {
            config.set(GraphDatabaseConfiguration.INDEX_MAX_RESULT_SET_SIZE, 3, indexBackend);
        }
        return config.getConfiguration();
    }

    public abstract ModifiableConfiguration getStorageConfiguration();

    @Test
    public void indexShouldExistAfterCreation() throws Exception {
        PropertyKey key = mgmt.makePropertyKey("name").dataType(String.class).make();
        mgmt.buildIndex("verticesByName", Vertex.class).addKey(key).buildMixedIndex("search");
        mgmt.commit();

        String expectedIndexName = INDEX_NAME.getDefaultValue() + "_" + "verticesByName".toLowerCase();
        assertTrue(container.indexExists(expectedIndexName));
    }

    @Test
    public void indexShouldNotExistAfterDeletion() throws Exception {
        clopen(option(LOG_SEND_DELAY, MANAGEMENT_LOG), Duration.ZERO,
                option(KCVSLog.LOG_READ_LAG_TIME, MANAGEMENT_LOG), Duration.ofMillis(50),
                option(LOG_READ_INTERVAL, MANAGEMENT_LOG), Duration.ofMillis(250),
                option(FORCE_INDEX_USAGE), true);

        String indexName = "mixed";
        String propertyName = "prop";

        makeKey(propertyName, String.class);
        finishSchema();

        // Never create new indexes while a transaction is active
        graph.getOpenTransactions().forEach(ItinerisTransaction::rollback);
        mgmt = graph.openManagement();

        registerIndex(indexName, Vertex.class, propertyName);
        enableIndex(indexName);
        disableIndex(indexName);
        discardIndex(indexName);
        dropIndex(indexName);

        String expectedIndexName = INDEX_NAME.getName() + "_" + indexName.toLowerCase();
        assertFalse(container.indexExists(expectedIndexName));
    }

    @Test
    public void writingAnItemLargerThanPermittedChunkLimitFails() {
        PropertyKey key = mgmt.makePropertyKey("some-field").dataType(Integer.class).make();
        mgmt.buildIndex("bulkTooLargeWriteTestIndex", Vertex.class).addKey(key).buildMixedIndex(INDEX);
        mgmt.buildIndex("equalityLookupIndex", Vertex.class).addKey(key).buildCompositeIndex();
        mgmt.commit();

        // Confirm we're able to successfully write initially
        Vertex initiallyWrittenVertex = graph.traversal().addV().property(key.name(), 1).next();
        graph.tx().commit();

        // Retrieve the vertex again based on the composite index and then mixed index
        // that, confirming it's in both
        Vertex initialVertexEqualityLookup = graph.traversal().V().has(key.name(), P.eq(1)).next();
        Vertex initialVertexRangeLookup = graph.traversal().V().has(key.name(), P.gt(0)).next();

        Assertions.assertEquals(initiallyWrittenVertex.id(), initialVertexEqualityLookup.id(),
                "Should have returned the same vertex");
        Assertions.assertEquals(initiallyWrittenVertex.id(), initialVertexRangeLookup.id(),
                "Should have returned the same vertex");

        // Now write a second vertex, but with a limit that prevents the mixed index
        // write from succeeding
        // Writes to mixed indices are "best effort", so a "successful" write that
        // failed to write to a mixed index
        // is still a success. However, lookups via the mixed index's predicates will
        // now be blind to the vertex
        clopen(option(OpenSearchIndex.BULK_CHUNK_SIZE_LIMIT_BYTES, INDEX), 1);
        Vertex secondWriteAttemptVertex = graph.traversal().addV().property(key.name(), 2).next();
        graph.tx().commit();
        Vertex secondVertexEqualityLookup = graph.traversal().V().has(key.name(), P.eq(2)).next();
        boolean secondVertexRangeLookup = graph.traversal().V().has(key.name(), P.gt(1)).hasNext();

        Assertions.assertEquals(secondWriteAttemptVertex.id(), secondVertexEqualityLookup.id(),
                "Should have returned the same vertex");
        Assertions.assertFalse(secondVertexRangeLookup, "The lookup for the second vertex using the mixed index " +
                "predicate should have failed to find it due to a silent mutation failure to the mixed index due to the "
                +
                "chunk size limit");
    }

    @Test
    // Disabled to not slow down CI/CD builds given there's nothing to assert
    // intended for manual observation of the bulk chunking to ElasticSearch
    @Disabled
    public void manuallyObserveBulkWritingChunking() {
        clopen(option(OpenSearchIndex.BULK_CHUNK_SIZE_LIMIT_BYTES, INDEX), 100);
        PropertyKey key = mgmt.makePropertyKey("some-field").dataType(String.class).make();
        mgmt.buildIndex("testChunkingIndex", Vertex.class).addKey(key).buildMixedIndex(INDEX);
        mgmt.commit();

        // Write 10 vertices that will each individually be split up into their own
        // chunks due to the limit configured
        IntStream.range(0, 10).forEach(i -> graph.traversal().addV().property(key.name(), "foobar").toList());
        graph.tx().commit();
    }

    @Override
    public boolean supportsLuceneStyleQueries() {
        return true;
    }

    @Override
    public boolean supportsWildcardQuery() {
        return true;
    }

    @Override
    protected boolean supportsCollections() {
        return true;
    }

    @Override
    public boolean supportsGeoPointExistsQuery() {
        return true;
    }

    @Override
    public boolean supportsGeoShapePrefixTreeMapping() {
        return false;
    }

    @Test
    public void testCircleBKDIndexMappingCanBeUsedWithDefaultProcessor() {
        clopen();
        createIndexedBKDGeoshape();
        Assertions.assertTrue(addVertexWithCircleValueProperty());
    }

    @Test
    public void testCircleBKDIndexMappingCanBeUsedWithNoChangeProcessor() {
        clopen(option(MixedIndexUtilsConfigOptions.BKD_CIRCLE_PROCESSOR_CLASS, INDEX),
                NoTransformCircleProcessor.SHORTHAND);
        createIndexedBKDGeoshape();
        boolean vertexAddedAndFound = addVertexWithCircleValueProperty();
        Assertions.assertTrue(vertexAddedAndFound);
    }

    @Test
    public void testCircleBKDIndexMappingCanBeUsedWithFixedProcessor() {
        clopen(option(MixedIndexUtilsConfigOptions.BKD_CIRCLE_PROCESSOR_CLASS, INDEX),
                FixedErrorDistanceCircleProcessor.SHORTHAND);
        createIndexedBKDGeoshape();
        Assertions.assertTrue(addVertexWithCircleValueProperty());
    }

    @Test
    public void testCircleBKDIndexMappingCanBeUsedWithDynamicProcessor() {
        clopen(option(MixedIndexUtilsConfigOptions.BKD_CIRCLE_PROCESSOR_CLASS, INDEX),
                DynamicErrorDistanceCircleProcessor.SHORTHAND);
        createIndexedBKDGeoshape();
        Assertions.assertTrue(addVertexWithCircleValueProperty());
    }

    @Test
    public void testCircleBKDIndexMappingCanBeUsedWithCustomProcessor() {
        MutableBoolean preProcessingNormalCircle = new MutableBoolean(false);
        MutableBoolean processedIntoPolygon = new MutableBoolean(false);
        TestCircleProcessor.setPreProcessConsumer(geoshape -> {
            if (Geoshape.Type.CIRCLE.equals(geoshape.getType())) {
                preProcessingNormalCircle.setTrue();
            } else {
                Assertions.fail("Circle had to be used");
            }
        });
        TestCircleProcessor.setPostProcessConsumer(geoshape -> {
            if (Geoshape.Type.POLYGON.equals(geoshape.getType())) {
                processedIntoPolygon.setTrue();
            } else {
                Assertions.fail("Expected Polygon Geoshape after process but got " + geoshape.getType().toString());
            }
        });

        clopen(option(MixedIndexUtilsConfigOptions.BKD_CIRCLE_PROCESSOR_CLASS, INDEX),
                TestCircleProcessor.class.getName());

        createIndexedBKDGeoshape();
        Assertions.assertTrue(addVertexWithCircleValueProperty());
        Assertions.assertTrue(preProcessingNormalCircle.booleanValue());
        Assertions.assertTrue(processedIntoPolygon.booleanValue());
    }

    private void createIndexedBKDGeoshape() {
        String indexName = "mixed";
        PropertyKey geoshapeProp = mgmt.makePropertyKey("foo").dataType(Geoshape.class).cardinality(Cardinality.SINGLE)
                .make();
        mgmt.buildIndex(indexName, Vertex.class).addKey(geoshapeProp, new Parameter<>("mapping", Mapping.BKD))
                .buildMixedIndex(INDEX);
        finishSchema();
    }

    private boolean addVertexWithCircleValueProperty() {
        Geoshape circle = Geoshape.circle(50, 50, 50);
        Vertex addedVertex = graph.traversal().addV().property("foo", circle).next();
        graph.tx().commit();
        return graph.traversal().V().has("foo",
                Geo.geoWithin(Geoshape.box(-90, -180, 90, 180)))
                .toList().stream()
                .anyMatch(vertex -> addedVertex.id().equals(vertex.id()) && circle.equals(vertex.value("foo")));
    }

}
