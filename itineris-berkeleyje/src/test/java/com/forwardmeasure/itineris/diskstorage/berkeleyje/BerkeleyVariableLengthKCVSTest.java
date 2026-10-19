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

package com.forwardmeasure.itineris.diskstorage.berkeleyje;

import com.forwardmeasure.itineris.BerkeleyStorageSetup;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.KeyColumnValueStoreTest;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStore;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeySliceQuery;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.keyvalue.OrderedKeyValueStoreManagerAdapter;
import com.forwardmeasure.itineris.diskstorage.util.StandardBaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.util.StaticArrayBuffer;
import com.forwardmeasure.itineris.diskstorage.util.StaticArrayEntry;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProviders;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BerkeleyVariableLengthKCVSTest extends KeyColumnValueStoreTest {

    public KeyColumnValueStoreManager openStorageManager() throws BackendException {
        BerkeleyJEStoreManager sm = new BerkeleyJEStoreManager(BerkeleyStorageSetup.getBerkeleyJEConfiguration());
        return new OrderedKeyValueStoreManagerAdapter(sm);
    }

    @Test @Override
    public void testConcurrentGetSlice() {

    }

    @Test @Override
    public void testConcurrentGetSliceAndMutate() {

    }

    @Test
    public void retrieveBackendAndGetKeyValue() {
        KeyColumnValueStoreManager storemanager = this.manager;

        KeyColumnValueStore store = this.store;

        try {
            //hello column world
            String keyOne = "hello";
            String colOne = "column";
            String valOne = "world";
            StoreTransaction txh = storemanager.beginTransaction(StandardBaseTransactionConfig.of(TimestampProviders.MICRO));
            StaticBuffer keyBuffOne = StaticBuffer.STATIC_FACTORY.get(keyOne.getBytes(), 0, keyOne.getBytes().length);
            StaticBuffer columnBuffOne = StaticBuffer.STATIC_FACTORY.get(colOne.getBytes(), 0, colOne.getBytes().length);
            StaticBuffer valBuffOne = StaticBuffer.STATIC_FACTORY.get(valOne.getBytes(), 0, valOne.getBytes().length);
            Entry insertEntry = StaticArrayEntry.of(columnBuffOne, valBuffOne);

            store.mutate(keyBuffOne, Collections.singletonList(insertEntry), Collections.emptyList(), txh);
            txh.commit();
            //hel column wor
            String keyTwo = "hel";
            String colTwo = "column";
            String valTwo = "wor";
            txh = storemanager.beginTransaction(StandardBaseTransactionConfig.of(TimestampProviders.MICRO));
            StaticBuffer keyBuffTwo = StaticBuffer.STATIC_FACTORY.get(keyTwo.getBytes(), 0, keyTwo.getBytes().length);
            StaticBuffer columnBuffTwo = StaticBuffer.STATIC_FACTORY.get(colTwo.getBytes(), 0, colTwo.getBytes().length);
            StaticBuffer valBuffTwo = StaticBuffer.STATIC_FACTORY.get(valTwo.getBytes(), 0, valTwo.getBytes().length);
            Entry insertEntryTwo = StaticArrayEntry.of(columnBuffTwo, valBuffTwo);

            store.mutate(keyBuffTwo, Collections.singletonList(insertEntryTwo), Collections.emptyList(), txh);
            txh.commit();

            //retrieve small one
            StaticBuffer startRange = new StaticArrayBuffer(new byte[]{0});
            StaticBuffer endRange = new StaticArrayBuffer(new byte[]{(byte)255});
            txh = storemanager.beginTransaction(StandardBaseTransactionConfig.of(TimestampProviders.MICRO));
            try {
                KeySliceQuery keyslice = new KeySliceQuery(keyBuffTwo, startRange, endRange);
                //Errors out here without fix
                List<Entry> result = store.getSlice(keyslice, txh);
                txh.commit();

                assertEquals(1,result.size());
                assertEquals(valTwo, new String(result.get(0).getValue().as(StaticBuffer.ARRAY_FACTORY)));
            } catch (BackendException e) {
                txh.rollback();
            }

            //delete keys created
            txh = storemanager.beginTransaction(StandardBaseTransactionConfig.of(TimestampProviders.MICRO));
            try {
                store.mutate(keyBuffOne,Collections.emptyList(),Collections.singletonList(columnBuffOne), txh);
                store.mutate(keyBuffTwo,Collections.emptyList(),Collections.singletonList(columnBuffTwo), txh);
                txh.commit();
            } catch (BackendException e) {
                txh.rollback();
            }
        } catch (BackendException e) {
            //generic failure
        }
    }
}
