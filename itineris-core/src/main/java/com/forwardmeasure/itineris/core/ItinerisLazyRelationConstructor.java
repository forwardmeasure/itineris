// Copyright 2024 JanusGraph Authors
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

package com.forwardmeasure.itineris.core;

import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.system.BaseRelationType;
import com.forwardmeasure.itineris.graphdb.types.system.SystemRelationType;

public class ItinerisLazyRelationConstructor {

    public static InternalRelation create(InternalRelation janusGraphRelation,
                                          final InternalVertex vertex,
                                          final StandardItinerisTx tx) {
        long typeId = janusGraphRelation.getType().longId();
        InternalRelationType type = tx.getOrLoadRelationTypeById(typeId);
        ItinerisLazyRelation lazyRelation;
        if (type.isPropertyKey()) {
            lazyRelation = new ItinerisLazyVertexProperty(janusGraphRelation, vertex, tx, type);
        } else {
            lazyRelation = new ItinerisLazyEdge(janusGraphRelation, vertex, tx, type);
        }

        if (type instanceof BaseRelationType) {
            return lazyRelation.loadValue();
        } else {
            return lazyRelation;
        }
    }

    public static InternalRelation create(Entry dataEntry,
                                          final InternalVertex vertex,
                                          final StandardItinerisTx tx) {

        long typeId = tx.getEdgeSerializer().parseTypeId(dataEntry);
        InternalRelationType type = tx.getOrLoadRelationTypeById(typeId);

        ItinerisLazyRelation lazyRelation;
        if (type.isPropertyKey()) {
            lazyRelation = new ItinerisLazyVertexProperty(dataEntry, vertex, tx, type);
        } else {
            lazyRelation = new ItinerisLazyEdge(dataEntry, vertex, tx, type);
        }

        if (type instanceof SystemRelationType) {
            return lazyRelation.loadValue();
        } else {
            return lazyRelation;
        }
    }
}
