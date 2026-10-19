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

package com.forwardmeasure.itineris.diskstorage.common;

import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigOption;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.CommonsConfiguration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyRange;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration.Restriction.NONE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.GRAPH_NAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ROOT_NS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_DIRECTORY;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LocalStoreManagerTest {

    public class LocalStoreManagerSampleImplementation extends LocalStoreManager {
        public LocalStoreManagerSampleImplementation(Configuration c) throws BackendException {
            super(c);
        }

        /*
         * The following methods are placeholders to adhere to the StoreManager interface.
         */
        @Override
        public List<KeyRange> getLocalKeyPartition() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public StoreFeatures getFeatures() {
            return null;
        }

        @Override
        public void clearStorage() {}

        @Override
        public void close() {}

        @Override
        public StoreTransaction beginTransaction(BaseTransactionConfig config) {
            return null;
        }

        @Override
        public boolean exists() {
            return true;
        }
    }

    public Map<ConfigOption, String> getBaseConfigurationMap() {
        final Map<ConfigOption, String> map = new HashMap<>();
        map.put(STORAGE_BACKEND, "berkeleyje");
        return map;
    }

    public LocalStoreManager getStoreManager(Map<ConfigOption, String> map) throws BackendException {
        final ModifiableConfiguration mc = new ModifiableConfiguration(ROOT_NS, new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration()), NONE);
        map.forEach(mc::set);
        return new LocalStoreManagerSampleImplementation(mc);
    }

    @Test
    public void directoryShouldEqualSuppliedDirectory() throws BackendException {
        final Map<ConfigOption, String> map = getBaseConfigurationMap();
        map.put(STORAGE_DIRECTORY, "specific/absolute/directory");
        final LocalStoreManager mgr = getStoreManager(map);
        File expectedDirectory = new File("specific/absolute/directory");
        assertEquals(expectedDirectory.getPath(), mgr.directory.getPath());
    }

    @Test
    public void directoryShouldEqualStorageRootPlusGraphName() throws BackendException {
        final Map<ConfigOption, String> map = getBaseConfigurationMap();
        map.put(STORAGE_ROOT, "temp/root");
        map.put(GRAPH_NAME, "randomGraphName");
        final LocalStoreManager mgr = getStoreManager(map);
        File expectedFile = new File("temp/root/randomGraphName");
        assertEquals(expectedFile.getPath(), mgr.directory.getPath());
    }

    @Test
    public void shouldThrowError() {
        final Map<ConfigOption, String> map = getBaseConfigurationMap();
        map.put(GRAPH_NAME, "randomGraphName");

        RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> getStoreManager(map));
        assertEquals("Please supply configuration parameter " +
            "\"storage.directory\" or both \"storage.root\" " +
            "and \"graph.graphname\".", runtimeException.getMessage());
    }
}

