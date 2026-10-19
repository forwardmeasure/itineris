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

package com.forwardmeasure.itineris.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forwardmeasure.itineris.core.util.ReflectiveConfigOptionLoader;

// Backend modules — compile-time safety: if a class is renamed or removed,
// this file will fail to compile. All these modules are direct dependencies
// of itineris-config.
import com.forwardmeasure.itineris.diskstorage.berkeleyje.BerkeleyJEStoreManager;
import com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions;
import com.forwardmeasure.itineris.diskstorage.log.kcvs.KCVSLog;
import com.forwardmeasure.itineris.diskstorage.log.kcvs.KCVSLogManager;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.MixedIndexUtilsConfigOptions;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.idassigner.VertexIDAssigner;
import com.forwardmeasure.itineris.graphdb.database.idassigner.placement.SimpleBulkPlacementStrategy;
import com.forwardmeasure.itineris.graphdb.query.index.ThresholdBasedIndexSelectionStrategy;

/**
 * Registers all standard backend classnames with {@link ReflectiveConfigOptionLoader}.
 *
 * <p>This class lives in {@code itineris-config}, which depends on all backend
 * modules. Class references use {@code .class.getName()} for compile-time safety —
 * if any class is renamed or removed the build fails immediately rather than
 * producing a runtime error.
 *
 * <p>Registration happens in the static initializer, which runs when this class
 * is first loaded by the JVM. {@code itineris-server} depends on
 * {@code itineris-config}, so this class is loaded transitively at server startup
 * before any {@code ConfigNamespace.getChild()} call triggers
 * {@link ReflectiveConfigOptionLoader#loadStandard(Class)}.
 *
 * <p>To add a new backend to standard loading, add its config-options class as a
 * dependency of {@code itineris-config} and add a {@code .class.getName()} entry
 * to {@link #STANDARD_CLASSNAMES}.
 */
public final class ItinerisStandardConfig {

    private static final Logger log = LoggerFactory.getLogger(ItinerisStandardConfig.class);

    /**
     * The definitive list of classes whose static initializers register
     * ConfigOptions for Itineris's supported backends.
     *
     * <p>Ordering: core infrastructure first, then storage backends, then
     * index backends, then query/optimization classes.
     */
    public static final List<String> STANDARD_CLASSNAMES = Arrays.asList(
            // Core graph configuration
            GraphDatabaseConfiguration.class.getName(),

            // Storage backends
            CQLConfigOptions.class.getName(),
            BerkeleyJEStoreManager.class.getName(),

            // Index backend
            OpenSearchIndex.class.getName(),

            // Logging
            KCVSLog.class.getName(),
            KCVSLogManager.class.getName(),

            // ID assignment
            VertexIDAssigner.class.getName(),
            SimpleBulkPlacementStrategy.class.getName(),

            // Query optimization
            ThresholdBasedIndexSelectionStrategy.class.getName(),

            // Mixed index utilities
            MixedIndexUtilsConfigOptions.class.getName()
    );

    static {
        log.debug("Registering {} standard classnames with ReflectiveConfigOptionLoader",
                STANDARD_CLASSNAMES.size());
        ReflectiveConfigOptionLoader.INSTANCE.registerStandardClassnames(STANDARD_CLASSNAMES);
    }

    /**
     * Ensure this class's static initializer has run.
     * Call this from itineris-server startup before any graph operations.
     */
    public static void initialize() {
        // Static initializer runs on first class load.
        // This method exists as an explicit call site for clarity.
        log.debug("ItinerisStandardConfig initialized");
    }

    private ItinerisStandardConfig() {
        // utility class
    }
}

