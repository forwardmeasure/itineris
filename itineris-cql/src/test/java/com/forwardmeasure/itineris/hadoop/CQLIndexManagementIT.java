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

package com.forwardmeasure.itineris.hadoop;

import io.github.artsok.RepeatedIfExceptionsTest;
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.ExecutionException;

@Testcontainers
public class CQLIndexManagementIT extends AbstractIndexManagementIT {
    @Container
    private static ItinerisCassandraContainer cql = new ItinerisCassandraContainer();

    @Override
    public WriteConfiguration getConfiguration() {
        return cql.getConfiguration(getClass().getSimpleName().toLowerCase()).getConfiguration();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/3132
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testRepairRelationIndex() throws ExecutionException, InterruptedException, BackendException {
        super.testRepairRelationIndex();
    }
}
