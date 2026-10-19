// Copyright 2024 JanusGraph Authors
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

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.schema.SchemaInitStrategy;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;

public class CustomTestSchemaInitStrategy implements SchemaInitStrategy {

    public static volatile boolean initialized = false;

    @Override
    public Itineris initializeSchemaAndStart(GraphDatabaseConfiguration graphDatabaseConfiguration) {
        initialized = true;
        return null;
    }
}
