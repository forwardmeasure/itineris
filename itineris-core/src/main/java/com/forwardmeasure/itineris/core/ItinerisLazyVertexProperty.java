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

import org.apache.tinkerpop.gremlin.structure.Property;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

import java.util.Iterator;

public class ItinerisLazyVertexProperty<V> extends ItinerisLazyRelation<V> implements ItinerisVertexProperty<V> {

    public ItinerisLazyVertexProperty(InternalRelation janusGraphRelation,
                                  final InternalVertex vertex,
                                  final StandardItinerisTx tx,
                                  final InternalRelationType type) {
        super(janusGraphRelation, vertex, tx, type);
    }

    public ItinerisLazyVertexProperty(Entry dataEntry,
                                  final InternalVertex vertex,
                                  final StandardItinerisTx tx,
                                  final InternalRelationType type) {
        super(dataEntry, vertex, tx, type);
    }

    private ItinerisVertexProperty<V> loadProperty() {
        assert this.isProperty();
        return (ItinerisVertexProperty<V>) this.loadValue();
    }

    @Override
    public boolean isPresent() {
        return loadProperty().isPresent();
    }

    @Override
    public ItinerisVertex element() {
        return loadProperty().element();
    }

    @Override
    public <V> Iterator<Property<V>> properties(String... propertyKeys) {
        return this.loadProperty().properties(propertyKeys);
    }
}
