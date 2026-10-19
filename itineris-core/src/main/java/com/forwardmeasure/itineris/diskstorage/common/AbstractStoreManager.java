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

package com.forwardmeasure.itineris.diskstorage.common;

import com.forwardmeasure.itineris.diskstorage.EntryMetaData;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreManager;
import com.forwardmeasure.itineris.diskstorage.util.StaticArrayEntry;

import java.util.ArrayList;
import java.util.List;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ASSIGN_TIMESTAMP;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BATCH;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_TRANSACTIONAL;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORE_META_TIMESTAMPS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORE_META_TTL;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORE_META_VISIBILITY;

/**
 * Abstract Store Manager used as the basis for concrete StoreManager implementations.
 * Simplifies common configuration management.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public abstract class AbstractStoreManager implements StoreManager {

    protected final boolean transactional;
    protected final boolean batchLoading;
    protected final Configuration storageConfig;
    protected final boolean assignTimestamp;

    public AbstractStoreManager(Configuration storageConfig) {
        batchLoading = storageConfig.get(STORAGE_BATCH);
        boolean transactional = storageConfig.get(STORAGE_TRANSACTIONAL);
        if (batchLoading) {
            transactional = false;
        }
        assignTimestamp = storageConfig.get(ASSIGN_TIMESTAMP);
        this.transactional = transactional;
        this.storageConfig = storageConfig;
    }

    public Configuration getStorageConfig() {
        return storageConfig;
    }

    public EntryMetaData[] getMetaDataSchema(String storeName) {
        List<EntryMetaData> schemaBuilder = new ArrayList<>(3);
        StoreFeatures features = getFeatures();
        if (features.hasTimestamps() && storageConfig.get(STORE_META_TIMESTAMPS,storeName))
            schemaBuilder.add(EntryMetaData.TIMESTAMP);
        if (features.hasCellTTL() && storageConfig.get(STORE_META_TTL,storeName))
            schemaBuilder.add(EntryMetaData.TTL);
        if (features.hasVisibility() && storageConfig.get(STORE_META_VISIBILITY,storeName))
            schemaBuilder.add(EntryMetaData.VISIBILITY);

        if (schemaBuilder.isEmpty()) return StaticArrayEntry.EMPTY_SCHEMA;
        return schemaBuilder.toArray(new EntryMetaData[schemaBuilder.size()]);
    }

    public boolean isAssignTimestamp() {
        return this.assignTimestamp;
    }
}
