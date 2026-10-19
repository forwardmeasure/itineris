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

package com.forwardmeasure.itineris.diskstorage.configuration.backend.builder;

import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.KCVSConfiguration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStore;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;
import com.forwardmeasure.itineris.diskstorage.util.BackendOperation;
import com.forwardmeasure.itineris.diskstorage.util.StandardBaseTransactionConfig;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.SETUP_WAITTIME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.SYSTEM_CONFIGURATION_IDENTIFIER;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.SYSTEM_PROPERTIES_STORE_NAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.TIMESTAMP_PROVIDER;

/**
 * Builder to build {@link KCVSConfiguration} instances
 */
public class KCVSConfigurationBuilder {

    public KCVSConfiguration buildStandaloneGlobalConfiguration(final KeyColumnValueStoreManager manager, final Configuration config) {
        try {
            final StoreFeatures features = manager.getFeatures();
            return buildGlobalConfiguration(new BackendOperation.TransactionalProvider() {
                @Override
                public StoreTransaction openTx() throws BackendException {
                    return manager.beginTransaction(StandardBaseTransactionConfig.of(config.get(TIMESTAMP_PROVIDER),features.getKeyConsistentTxConfig()));
                }

                @Override
                public void close() throws BackendException {
                    manager.close();
                }
            },manager.openDatabase(SYSTEM_PROPERTIES_STORE_NAME),config);
        } catch (BackendException e) {
            throw new ItinerisException("Could not open global configuration",e);
        }
    }

    public KCVSConfiguration buildConfiguration(final BackendOperation.TransactionalProvider txProvider,
                                                final KeyColumnValueStore store, final String identifier,
                                                final Configuration config) {
        try {
            KCVSConfiguration keyColumnValueStoreConfiguration =
                new KCVSConfiguration(txProvider,config,store,identifier);
            keyColumnValueStoreConfiguration.setMaxOperationWaitTime(config.get(SETUP_WAITTIME));
            return keyColumnValueStoreConfiguration;
        } catch (BackendException e) {
            throw new ItinerisException("Could not open global configuration",e);
        }
    }

    public KCVSConfiguration buildGlobalConfiguration(final BackendOperation.TransactionalProvider txProvider,
                                                      final KeyColumnValueStore store,
                                                      final Configuration config) {
        return buildConfiguration(txProvider, store, SYSTEM_CONFIGURATION_IDENTIFIER, config);
    }
}
