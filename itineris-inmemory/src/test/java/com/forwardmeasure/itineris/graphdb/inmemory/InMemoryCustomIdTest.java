// Copyright 2022 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.inmemory;

import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.graphdb.ItinerisCustomIdTest;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import org.junit.jupiter.api.Disabled;

public class InMemoryCustomIdTest extends ItinerisCustomIdTest {

    @Override
    protected ModifiableConfiguration getModifiableConfiguration() {
        ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
        config.set(GraphDatabaseConfiguration.STORAGE_BACKEND,"inmemory");
        return config;
    }

    @Disabled
    @Override
    public void testUpgrade() {
        // an in-memory graph is destroyed when it's closed
    }

    @Disabled
    @Override
    public void testEnableAndDisableStringId() {
        // an in-memory graph is destroyed when it's closed
    }
}
