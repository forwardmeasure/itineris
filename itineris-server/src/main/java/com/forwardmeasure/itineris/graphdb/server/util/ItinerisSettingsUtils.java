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

package com.forwardmeasure.itineris.graphdb.server.util;

import org.apache.tinkerpop.gremlin.server.Settings;
import org.apache.tinkerpop.gremlin.server.util.DefaultGraphManager;
import com.forwardmeasure.itineris.graphdb.management.ConfigurationManagementGraph;
import com.forwardmeasure.itineris.graphdb.server.ItinerisSettings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ItinerisSettingsUtils {

    private static final String CONFIGURATION_MANAGEMENT_GRAPH_KEY = ConfigurationManagementGraph.class.getSimpleName();

    public static ItinerisSettings configureDefaults(ItinerisSettings settings) {
        return configureDefaultOfDynamicGraphs(configureDefaultSerializersIfNotSet(settings));
    }

    private static ItinerisSettings configureDefaultOfDynamicGraphs(ItinerisSettings settings) {
        if (!settings.graphs.containsKey(CONFIGURATION_MANAGEMENT_GRAPH_KEY)) {
            return settings;
        }
        if (settings.graphManager.equals(DefaultGraphManager.class.getName())) {
            settings.graphManager = com.forwardmeasure.itineris.graphdb.management.ItinerisManager.class.getCanonicalName();
        }
        return settings;
    }

    /**
     * Add a default set of serializers, if they are empty.
     *
     * @param settings Parsed Gremlin Server {@link Settings}.
     * @return Update Gremlin Server {@link Settings}.
     */
    private static ItinerisSettings configureDefaultSerializersIfNotSet(ItinerisSettings settings) {
        if (settings.serializers.size() != 0)
            return settings;

        List<Settings.SerializerSettings> serializers = new ArrayList<>();

        addSerializerWithRegistry(
            serializers,
            org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1.class.getCanonicalName()
        );
        addSerializerWithResultToString(
            serializers,
            org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1.class.getCanonicalName()
        );

        addSerializerWithRegistry(
            serializers,
            org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV3.class.getCanonicalName()
        );

        settings.serializers = serializers;

        return settings;
    }

    private static void addSerializerWithRegistry(List<Settings.SerializerSettings> serializers, String className) {
        Settings.SerializerSettings newSerializerSettings = new Settings.SerializerSettings();
        newSerializerSettings.className = className;
        newSerializerSettings.config = Collections.singletonMap("ioRegistries",
            Collections.singletonList(com.forwardmeasure.itineris.graphdb.tinkerpop.ItinerisIoRegistry.class.getCanonicalName()));
        serializers.add(newSerializerSettings);
    }

    private static void addSerializerWithResultToString(List<Settings.SerializerSettings> serializers, String className) {
        Settings.SerializerSettings newSerializerSettings = new Settings.SerializerSettings();
        newSerializerSettings.className = className;
        newSerializerSettings.config = Collections.singletonMap("serializeResultToString", true);
        serializers.add(newSerializerSettings);
    }

}
