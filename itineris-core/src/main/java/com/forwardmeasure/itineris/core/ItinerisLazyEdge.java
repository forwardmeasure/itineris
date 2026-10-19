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

package com.forwardmeasure.itineris.core;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

public class ItinerisLazyEdge extends ItinerisLazyRelation implements ItinerisEdge {

    public ItinerisLazyEdge(InternalRelation janusGraphRelation,
                                  final InternalVertex vertex,
                                  final StandardItinerisTx tx,
                                  final InternalRelationType type) {
        super(janusGraphRelation, vertex, tx, type);
    }

    public ItinerisLazyEdge(Entry dataEntry,
                                  final InternalVertex vertex,
                                  final StandardItinerisTx tx,
                                  final InternalRelationType type) {
        super(dataEntry, vertex, tx, type);
    }

    private ItinerisEdge loadEdge() {
        assert this.isEdge();
        return (ItinerisEdge) this.loadValue();
    }

    @Override
    public ItinerisVertex vertex(Direction dir) {
        return this.loadEdge().vertex(dir);
    }

    @Override
    public ItinerisVertex otherVertex(Vertex vertex) {
        return this.loadEdge().otherVertex(vertex);
    }
}
