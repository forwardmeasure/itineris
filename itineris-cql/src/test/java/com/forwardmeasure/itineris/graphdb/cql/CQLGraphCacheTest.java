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
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.StorageSetup;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.graphdb.ItinerisTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.ExecutionException;

@Testcontainers
public class CQLGraphCacheTest extends ItinerisTest {

    @Container
    public static final ItinerisCassandraContainer cqlContainer = new ItinerisCassandraContainer();

    @Override
    public WriteConfiguration getConfiguration() {
        return StorageSetup.addPermanentCache(cqlContainer.getConfiguration(getClass().getSimpleName()));
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1457
    // The parameterized variant of rerunner-jupiter's retry annotation does not work with JUnit 5.13+, so the
    // parameterized test of the base class is overridden without a test annotation (which excludes it from this
    // class) and its two parameter values are expanded into the dedicated repeatable tests below.
    @Override
    public void simpleLogTest(boolean useStringId) throws InterruptedException {
        super.simpleLogTest(useStringId);
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    public void simpleLogTestWithLongId() throws InterruptedException {
        simpleLogTest(false);
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    public void simpleLogTestWithStringId() throws InterruptedException {
        simpleLogTest(true);
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1457
    // The parameterized variant of rerunner-jupiter's retry annotation does not work with JUnit 5.13+, so the
    // parameterized test of the base class is overridden without a test annotation (which excludes it from this
    // class) and its two parameter values are expanded into the dedicated repeatable tests below.
    @Override
    public void simpleLogTestWithFailure(boolean useStringId) throws InterruptedException {
        super.simpleLogTestWithFailure(useStringId);
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    public void simpleLogTestWithFailureWithLongId() throws InterruptedException {
        simpleLogTestWithFailure(false);
    }

    @RepeatedIfExceptionsTest(repeats = 3)
    public void simpleLogTestWithFailureWithStringId() throws InterruptedException {
        simpleLogTestWithFailure(true);
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1497
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testEdgeTTLTiming() throws Exception {
        super.testEdgeTTLTiming();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/1462
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testEdgeTTLWithTransactions() throws Exception {
        super.testEdgeTTLWithTransactions();
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
}
