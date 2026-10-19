// Copyright 2019 Itineris Authors
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

package com.forwardmeasure.itineris.diskstorage.inmemory;

import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeySliceQuery;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * This is a common interface which allows CompactInMemoryColumnValueStore to switch between single- and multi-page
 * implementations based on number of entries at runtime.
 */
public interface SharedEntryBuffer {
    int numPages();

    int numEntries();

    int byteSize();

    boolean isEmpty();

    EntryList getSlice(KeySliceQuery query);

    void mutate(Entry[] add, Entry[] del, int maxPageSize);

    boolean isPaged();

    SharedEntryBufferFragmentationReport createFragmentationReport(int maxPageSize);

    void quickDefragment(int maxPageSize);

    void dumpTo(DataOutputStream out) throws IOException;

}
