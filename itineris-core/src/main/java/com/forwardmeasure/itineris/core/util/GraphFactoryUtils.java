// Copyright 2024 Itineris Authors
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

package com.forwardmeasure.itineris.core.util;

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.schema.SchemaInitializationManager;
import com.forwardmeasure.itineris.diskstorage.configuration.ReadConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.builder.GraphDatabaseConfigurationBuilder;

public class GraphFactoryUtils {

    public static Itineris defineSchemaAndStart(ReadConfiguration configuration) {
        GraphDatabaseConfiguration graphDatabaseConfiguration = new GraphDatabaseConfigurationBuilder().build(configuration);
        return SchemaInitializationManager.initializeSchemaAndStart(graphDatabaseConfiguration);
    }
}
