// Copyright 2024 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.query.vertex;

import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.util.EntryArrayList;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VertexWithInlineProps {
    private final Object vertexId;
    private final Map<SliceQuery, EntryList> inlineProperties;

    private static final Logger log = LoggerFactory.getLogger(VertexWithInlineProps.class);

    public VertexWithInlineProps(Object vertexId, EntryList inlineProperties, Map<String, SliceQuery> inlineQueries, StandardItinerisTx tx) {
        this.vertexId = vertexId;
        this.inlineProperties = loadInlineProperties(inlineProperties, inlineQueries, tx);
    }

    public Object getVertexId() {
        return vertexId;
    }

    public Map<SliceQuery, EntryList> getInlineProperties() {
        return inlineProperties;
    }

    private Map<SliceQuery, EntryList> loadInlineProperties(EntryList inlineProperties,
                                                            Map<String, SliceQuery> inlineQueries,
                                                            StandardItinerisTx tx) {
        if (inlineProperties.isEmpty()) {
            return Collections.emptyMap();
        } else {
            Map<SliceQuery, EntryList> result = new HashMap<>();
            for (Entry dataEntry : inlineProperties) {
                long typeId = tx.getEdgeSerializer().parseTypeId(dataEntry);
                InternalRelationType type = tx.getOrLoadRelationTypeById(typeId);
                assert type.isPropertyKey();

                SliceQuery sq = inlineQueries.get(type.name());
                if(sq != null) {
                    if (result.containsKey(sq)) {
                        result.get(sq).add(dataEntry);
                    } else {
                        EntryList entryList = new EntryArrayList();
                        entryList.add(dataEntry);
                        result.put(sq, entryList);
                    }
                } else {
                    log.error("Missing key=" + type.name() + " in inlineQueries. Check index definition.");
                }
            }
            return result;
        }
    }

    @Override
    public int hashCode() {
        return vertexId.hashCode();
    }

    @Override
    public boolean equals(Object oth) {
        if (this == oth) return true;
        if (getClass().isInstance(oth)) {
            return vertexId.equals((((VertexWithInlineProps) oth).vertexId));
        } else {
            return vertexId.equals(oth);
        }
    }
}
