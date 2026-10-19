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

package com.forwardmeasure.itineris.graphdb.transaction.indexcache;

import com.google.common.collect.HashMultimap;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.PropertyKey;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */

public class ConcurrentIndexCache implements IndexCache {

    private final HashMultimap<Object,ItinerisVertexProperty> map;

    public ConcurrentIndexCache() {
        this.map = HashMultimap.create();
    }

    @Override
    public synchronized void add(ItinerisVertexProperty property) {
        map.put(property.value(),property);
    }

    @Override
    public synchronized void remove(ItinerisVertexProperty property) {
        map.remove(property.value(),property);
    }

    @Override
    public synchronized Iterable<ItinerisVertexProperty> get(final Object value, final PropertyKey key) {
        final List<ItinerisVertexProperty> result = new ArrayList<>(4);
        for (ItinerisVertexProperty p : map.get(value)) {
            if (p.propertyKey().equals(key)) result.add(p);
        }
        return result;
    }

    @Override
    public void close() {
        map.clear();
    }
}
