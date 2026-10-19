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

package com.forwardmeasure.itineris.graphdb.transaction.indexcache;

import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.PropertyKey;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface IndexCache {

    void add(ItinerisVertexProperty property);

    void remove(ItinerisVertexProperty property);

    Iterable<ItinerisVertexProperty> get(Object value, PropertyKey key);

    /**
     * Closes the cache which allows the cache to release allocated memory.
     * Calling any of the other methods after closing a cache has undetermined behavior.
     */
    void close();

}
