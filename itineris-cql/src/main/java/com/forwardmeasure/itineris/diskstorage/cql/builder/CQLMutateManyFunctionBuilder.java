// Copyright 2021 JanusGraph Authors
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

package com.forwardmeasure.itineris.diskstorage.cql.builder;

import com.datastax.oss.driver.api.core.CqlSession;
import com.forwardmeasure.itineris.diskstorage.common.DistributedStoreManager;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.cql.CQLKeyColumnValueStore;
import com.forwardmeasure.itineris.diskstorage.cql.function.ConsumerWithBackendException;
import com.forwardmeasure.itineris.diskstorage.cql.function.mutate.CQLMutateManyFunction;
import com.forwardmeasure.itineris.diskstorage.cql.function.mutate.CQLMutateManyLoggedFunction;
import com.forwardmeasure.itineris.diskstorage.cql.function.mutate.CQLMutateManyUnloggedFunction;
import com.forwardmeasure.itineris.diskstorage.util.backpressure.QueryBackPressure;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProvider;

import java.util.Map;

import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.ATOMIC_BATCH_MUTATE;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.BATCH_STATEMENT_SIZE;

public class CQLMutateManyFunctionBuilder {

    public CQLMutateManyFunction build(final CqlSession session, final Configuration configuration,
                                       final TimestampProvider times, final boolean assignTimestamp,
                                       final Map<String, CQLKeyColumnValueStore> openStores,
                                       ConsumerWithBackendException<DistributedStoreManager.MaskedTimestamp> sleepAfterWriteFunction,
                                       QueryBackPressure queriesBackPressure) {

        int batchSize = configuration.get(BATCH_STATEMENT_SIZE);
        boolean atomicBatch = configuration.get(ATOMIC_BATCH_MUTATE);

        final CQLMutateManyFunction mutateManyFunction;
        if (atomicBatch) {
            mutateManyFunction = new CQLMutateManyLoggedFunction(times,
                assignTimestamp, openStores, session, sleepAfterWriteFunction, queriesBackPressure);
        } else {
            mutateManyFunction = new CQLMutateManyUnloggedFunction(batchSize,
                session, openStores, times, assignTimestamp, sleepAfterWriteFunction, queriesBackPressure);
        }

        return mutateManyFunction;
    }

}
