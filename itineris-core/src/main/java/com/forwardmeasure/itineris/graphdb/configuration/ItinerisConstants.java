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

package com.forwardmeasure.itineris.graphdb.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.google.common.base.Preconditions;

/**
 * Collection of constants used throughput the Itineris codebase.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class ItinerisConstants {

    public static final String ITINERIS_GRAPH__PROPERTIES_FILE = "itineris.internal.properties";

    /**
     * Runtime version of Itineris, as read from a properties file inside the core
     * jar
     */
    public static final String VERSION;

    /**
     * Past versions of Titan Graph with which the runtime version shares a
     * compatible storage format
     */
    public static final List<String> TITAN_COMPATIBLE_VERSIONS;

    /**
     * Name of the ids.store-name used by Itineris which is configurable
     */
    public static final String ITINERIS_GRAPH_ID_STORE_NAME = "itineris_graph_ids";

    /**
     * Past name of the ids.store-name used by Titan Graph but which was not
     * configurable
     */
    public static final String TITAN_ID_STORE_NAME = "titan_ids";

    /**
     * Storage format version currently used by Itineris, version 1 is for Itineris
     * 0.2.x and below
     */
    public static final String STORAGE_VERSION;

    /**
     * List of FIXED fields that can be modified when graph.allow-upgrade is set to
     * true
     */
    public static final Set<String> UPGRADEABLE_FIXED;

    static {

        /*
         * Preempt some potential NPEs with Preconditions bearing messages. They
         * are unlikely to fail outside of some crazy test environment. Still,
         * if something goes horribly wrong, even a cryptic error message is
         * better than a message-less NPE.
         */
        Package p = ItinerisConstants.class.getPackage();
        Preconditions.checkNotNull(p, "Unable to load package containing class " + ItinerisConstants.class);
        String packageName = p.getName();
        Preconditions.checkNotNull(packageName, "Unable to get name of package containing " + ItinerisConstants.class);
        String resourceName = packageName.replace('.', '/') + "/" + ITINERIS_GRAPH__PROPERTIES_FILE;

        Properties props = new Properties();

        try (InputStream is = ItinerisFactory.class.getClassLoader().getResourceAsStream(resourceName)) {
            Preconditions.checkNotNull(is,
                    "Unable to locate classpath resource " + resourceName + " containing Itineris version");
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties from " + resourceName, e);
        }

        VERSION = props.getProperty("itineris.version");
        STORAGE_VERSION = props.getProperty("itineris.storage-version");
        TITAN_COMPATIBLE_VERSIONS = getCompatibleVersions(props, "titan.compatible-versions");
        UPGRADEABLE_FIXED = getPropertySet(props, "itineris.upgradeable-fixed");
    }

    static List<String> getCompatibleVersions(Properties props, String key) {
        List<String> b = Stream.of(props.getProperty(key, "").split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toList());
        return Collections.unmodifiableList(b);
    }

    static Set<String> getPropertySet(Properties props, String key) {
        Set<String> buildSet = Stream.of(props.getProperty(key, "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        return Collections.unmodifiableSet(buildSet);
    }
}
