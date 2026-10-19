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

package com.forwardmeasure.itineris.diskstorage.util;

import com.codahale.metrics.Timer;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.StoreMetaData;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KCVMutation;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStore;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyRange;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreFeatures;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;
import com.forwardmeasure.itineris.util.stats.MetricManager;

import java.util.List;
import java.util.Map;

import static com.forwardmeasure.itineris.diskstorage.util.MetricInstrumentedStore.M_CALLS;
import static com.forwardmeasure.itineris.diskstorage.util.MetricInstrumentedStore.M_EXCEPTIONS;
import static com.forwardmeasure.itineris.diskstorage.util.MetricInstrumentedStore.M_MUTATE;
import static com.forwardmeasure.itineris.diskstorage.util.MetricInstrumentedStore.M_TIME;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class MetricInstrumentedStoreManager implements KeyColumnValueStoreManager {

    public static final String M_OPEN_DATABASE = "openDatabase";
    public static final String M_START_TX = "startTransaction";
    public static final String M_CLOSE_MANAGER = "closeManager";


    public static final String GLOBAL_PREFIX = "global";

    private final KeyColumnValueStoreManager backend;
    private final boolean mergeStoreMetrics;
    private final String mergedMetricsName;
    private final String managerMetricsName;

    public MetricInstrumentedStoreManager(KeyColumnValueStoreManager backend, String managerMetricsName,
                                          boolean mergeStoreMetrics, String mergedMetricsName) {
        this.backend = backend;
        this.mergeStoreMetrics = mergeStoreMetrics;
        this.mergedMetricsName = mergedMetricsName;
        this.managerMetricsName = managerMetricsName;
    }


    private String getMetricsStoreName(String storeName) {
        return mergeStoreMetrics ? mergedMetricsName : storeName;
    }

    @Override
    public KeyColumnValueStore openDatabase(String name, StoreMetaData.Container metaData) throws BackendException {
        MetricManager.INSTANCE.getCounter(GLOBAL_PREFIX, managerMetricsName, M_OPEN_DATABASE, M_CALLS).inc();
        return new MetricInstrumentedStore(backend.openDatabase(name, metaData),getMetricsStoreName(name));
    }

    @Override
    public void mutateMany(Map<String, Map<StaticBuffer, KCVMutation>> mutations, StoreTransaction txh) throws BackendException {
        if (!txh.getConfiguration().hasGroupName()) {
            backend.mutateMany(mutations,txh);
            return;
        }
        String prefix = txh.getConfiguration().getGroupName();

        final MetricManager mgr = MetricManager.INSTANCE;
        mgr.getCounter(prefix, managerMetricsName, M_MUTATE, M_CALLS).inc();
        final Timer.Context tc = mgr.getTimer(prefix,  managerMetricsName, M_MUTATE, M_TIME).time();

        try {
            backend.mutateMany(mutations,txh);
        } catch (BackendException | RuntimeException e) {
            mgr.getCounter(prefix,  managerMetricsName, M_MUTATE, M_EXCEPTIONS).inc();
            throw e;
        } finally {
            tc.stop();
        }
    }

    @Override
    public StoreTransaction beginTransaction(BaseTransactionConfig config) throws BackendException {
        MetricManager.INSTANCE.getCounter(GLOBAL_PREFIX, managerMetricsName, M_START_TX, M_CALLS).inc();
        return backend.beginTransaction(config);
    }

    @Override
    public void close() throws BackendException {
        backend.close();
        MetricManager.INSTANCE.getCounter(GLOBAL_PREFIX, managerMetricsName, M_CLOSE_MANAGER, M_CALLS).inc();
    }

    @Override
    public void clearStorage() throws BackendException {
        backend.clearStorage();
    }

    @Override
    public boolean exists() throws BackendException {
        return backend.exists();
    }

    @Override
    public StoreFeatures getFeatures() {
        return backend.getFeatures();
    }

    @Override
    public String getName() {
        return backend.getName();
    }

    @Override
    public List<KeyRange> getLocalKeyPartition() throws BackendException {
        return backend.getLocalKeyPartition();
    }

    @Override
    public Object getHadoopManager() throws BackendException {
        return backend.getHadoopManager();
    }
}
