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

package com.forwardmeasure.itineris.diskstorage.keycolumnvalue;

import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.util.RecordIterator;
import com.forwardmeasure.itineris.graphdb.olap.VertexJobConverter;

import java.util.Map;

/**
 * @author Sergii Karpenko (sergiy.karpenko@gmail.com)
 */

public interface KeySlicesIterator extends RecordIterator<StaticBuffer> {

    /**
     * Returns map of iterators over all entries associated with the current
     * key that match the column range specified in the queries.
     * <p>
     * Closing any of the returned sub-iterators has no effect on this iterator.
     *
     * Calling {@link #next()} might close previously returned RecordIterators
     * depending on the implementation, hence it is important to iterate over
     * (and close) the RecordIterator before calling {@link #next()} or {@link #hasNext()}.
     *
     * Important! Entries should be sorted inside iterators.
     * Otherwise {@link VertexJobConverter} will not work correctly
     *
     * @return
     */
    Map<SliceQuery, RecordIterator<Entry>> getEntries();

}
