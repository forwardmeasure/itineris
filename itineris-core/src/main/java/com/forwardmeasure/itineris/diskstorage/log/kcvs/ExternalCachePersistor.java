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

package com.forwardmeasure.itineris.diskstorage.log.kcvs;

import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.cache.CacheTransaction;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.cache.KCVSCache;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class ExternalCachePersistor implements ExternalPersistor {

    private final KCVSCache kcvs;
    private final CacheTransaction tx;

    public ExternalCachePersistor(KCVSCache kcvs, CacheTransaction tx) {
        this.kcvs = kcvs;
        this.tx = tx;
    }

    @Override
    public void add(StaticBuffer key, Entry cell) {
        try {
            List<Entry> list = new ArrayList<>(1);
            list.add(cell);
            kcvs.mutateEntries(key, list, KCVSCache.NO_DELETIONS,tx);
        } catch (BackendException e) {
            throw new ItinerisException("Unexpected storage exception in log persistence against cache",e);
        }
    }
}
