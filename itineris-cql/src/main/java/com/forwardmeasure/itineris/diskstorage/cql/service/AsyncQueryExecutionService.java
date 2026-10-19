// Copyright 2023 Itineris Authors
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

package com.forwardmeasure.itineris.diskstorage.cql.service;

import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeySliceQuery;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.MultiKeysQueryGroups;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AsyncQueryExecutionService {
    CompletableFuture<EntryList> executeSingleKeySingleSlice(final KeySliceQuery query, final StoreTransaction txh);
    Map<StaticBuffer, CompletableFuture<EntryList>> executeMultiKeySingleSlice(final List<StaticBuffer> keys, final SliceQuery query, final StoreTransaction txh);
    Map<SliceQuery, Map<StaticBuffer, CompletableFuture<EntryList>>> executeMultiKeyMultiSlice(final MultiKeysQueryGroups<StaticBuffer, SliceQuery> multiSliceQueriesForKeys, StoreTransaction txh);
}
