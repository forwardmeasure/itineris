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

package com.forwardmeasure.itineris.graphdb.types;

import org.apache.tinkerpop.gremlin.structure.Direction;
import com.forwardmeasure.itineris.core.Connection;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.VertexLabel;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertexLabel;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.vertices.ItinerisSchemaVertex;
import com.forwardmeasure.itineris.graphdb.util.CollectionsUtil;

import java.util.Collection;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class VertexLabelVertex extends ItinerisSchemaVertex implements InternalVertexLabel {

    //Cleared by resetCache() and kept only if no reset overlapped the read, under the rules of ItinerisSchemaVertex
    private volatile Integer ttl = null;

    public VertexLabelVertex(StandardItinerisTx tx, long id, byte lifecycle) {
        super(tx, id, lifecycle);
    }

    @Override
    public boolean isPartitioned() {
        return getDefinition().getValue(TypeDefinitionCategory.PARTITIONED, Boolean.class);
    }

    @Override
    public boolean isStatic() {
        return getDefinition().getValue(TypeDefinitionCategory.STATIC, Boolean.class);
    }

    @Override
    public Collection<PropertyKey> mappedProperties() {
        return CollectionsUtil.toArrayList(
            getRelated(TypeDefinitionCategory.PROPERTY_KEY_EDGE, Direction.OUT),
            entry -> (PropertyKey) entry.getSchemaType()
        );
    }

    @Override
    public Collection<Connection> mappedConnections() {
        return CollectionsUtil.toArrayList(
            getRelated(TypeDefinitionCategory.CONNECTION_EDGE, Direction.OUT),
            entry -> new Connection((String) entry.getModifier(), this, (VertexLabel) entry.getSchemaType())
        );
    }

    @Override
    public boolean hasDefaultConfiguration() {
        return !isPartitioned() && !isStatic();
    }

    @Override
    public int getTTL() {
        Integer result = ttl;
        if (result == null) {
            final long resetsBefore = cacheResets();
            result = TypeUtil.getTTL(this);
            keepUnlessReset(resetsBefore, result, value -> ttl = value);
        }
        return result;
    }

    @Override
    public void resetCache() {
        super.resetCache();
        ttl = null;
    }

}
