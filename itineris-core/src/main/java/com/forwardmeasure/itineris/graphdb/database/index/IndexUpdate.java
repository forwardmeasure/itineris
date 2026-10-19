// Copyright 2022 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.database.index;

import com.google.common.base.Preconditions;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.EntryMetaData;
import com.forwardmeasure.itineris.diskstorage.MetaAnnotatable;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexEntry;
import com.forwardmeasure.itineris.graphdb.types.IndexType;

import java.util.Objects;

public class IndexUpdate<K,E> {

    private final IndexType index;
    private final IndexMutationType mutationType;
    private final K key;
    private final E entry;
    private final ItinerisElement element;

    public IndexUpdate(IndexType index, IndexMutationType mutationType, K key, E entry, ItinerisElement element) {
        assert index!=null && mutationType!=null && key!=null && entry!=null && element!=null;
        assert !index.isCompositeIndex() || (key instanceof StaticBuffer && entry instanceof Entry);
        assert !index.isMixedIndex() || (key instanceof String && entry instanceof IndexEntry);
        this.index = index;
        this.mutationType = mutationType;
        this.key = key;
        this.entry = entry;
        this.element = element;
    }

    public ItinerisElement getElement() {
        return element;
    }

    public IndexType getIndex() {
        return index;
    }

    public IndexMutationType getType() {
        return mutationType;
    }

    public K getKey() {
        return key;
    }

    public E getEntry() {
        return entry;
    }

    public boolean isUpdate() {
        return mutationType == IndexMutationType.UPDATE;
    }

    public boolean isDeletion() {
        return mutationType== IndexMutationType.DELETE;
    }

    public boolean isCompositeIndex() {
        return index.isCompositeIndex();
    }

    public boolean isMixedIndex() {
        return index.isMixedIndex();
    }

    public void setTTL(int ttl) {
        Preconditions.checkArgument(ttl > 0 && mutationType != IndexMutationType.DELETE);
        ((MetaAnnotatable) entry).setMetaData(EntryMetaData.TTL, ttl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, mutationType, key, entry);
    }

    @Override
    public boolean equals(Object other) {
        if (this==other) return true;
        else if (other==null || !(other instanceof IndexUpdate)) return false;
        final IndexUpdate oth = (IndexUpdate)other;
        return index.equals(oth.index) && mutationType==oth.mutationType && key.equals(oth.key) && entry.equals(oth.entry);
    }
}
