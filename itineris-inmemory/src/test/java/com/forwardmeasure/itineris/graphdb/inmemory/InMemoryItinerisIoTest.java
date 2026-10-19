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

package com.forwardmeasure.itineris.graphdb.inmemory;

import com.google.common.base.Preconditions;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.graphdb.ItinerisBaseTest;
import com.forwardmeasure.itineris.graphdb.ItinerisIoTest;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;

import java.util.Map;

/**
 * @author Stephen Mallette (https://stephen.genoprime.com)
 */
public class InMemoryItinerisIoTest extends ItinerisIoTest {
    public WriteConfiguration getConfiguration() {
        ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
        config.set(GraphDatabaseConfiguration.STORAGE_BACKEND,"inmemory");
        return config.getConfiguration();
    }

    @Override
    public void clopen(Object... settings) {
        if (settings!=null && settings.length>0) {
            if (graph!=null && graph.isOpen()) {
                Preconditions.checkArgument(!graph.vertices().hasNext() &&
                        !graph.edges().hasNext(),"Graph cannot be re-initialized for InMemory since that would delete all data");
                graph.close();
            }
            Map<ItinerisBaseTest.TestConfigOption,Object> options = validateConfigOptions(settings);
            ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
            config.set(GraphDatabaseConfiguration.STORAGE_BACKEND,"inmemory");
            for (Map.Entry<ItinerisBaseTest.TestConfigOption,Object> option : options.entrySet()) {
                config.set(option.getKey().option, option.getValue(), option.getKey().umbrella);
            }
            open(config.getConfiguration());
        }
        newTx();
    }
}
