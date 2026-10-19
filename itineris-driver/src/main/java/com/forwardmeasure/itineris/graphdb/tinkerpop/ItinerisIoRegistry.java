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

package com.forwardmeasure.itineris.graphdb.tinkerpop;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.binary.GraphBinaryIo;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONIo;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoIo;
import org.apache.tinkerpop.shaded.kryo.Kryo;
import org.apache.tinkerpop.shaded.kryo.Serializer;
import org.apache.tinkerpop.shaded.kryo.io.Input;
import org.apache.tinkerpop.shaded.kryo.io.Output;

import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.ItinerisP;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.binary.GeoshapeGraphBinarySerializer;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.binary.ItinerisPBinarySerializer;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.binary.RelationIdentifierGraphBinarySerializer;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.graphson.ItinerisSONModuleV2d0;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 * @author Stephen Mallette (https://stephen.genoprime.com)
 */
public class ItinerisIoRegistry extends AbstractIoRegistry {

    private static final ItinerisIoRegistry INSTANCE = new ItinerisIoRegistry();

    private ItinerisIoRegistry() {
        register(GraphSONIo.class, null, ItinerisSONModuleV2d0.getInstance());
        register(GraphBinaryIo.class, RelationIdentifier.class, new RelationIdentifierGraphBinarySerializer());
        register(GraphBinaryIo.class, Geoshape.class, new GeoshapeGraphBinarySerializer());
        register(GraphBinaryIo.class, ItinerisP.class, new ItinerisPBinarySerializer());
        register(GryoIo.class, RelationIdentifier.class, null);
        register(GryoIo.class, Geoshape.class, new Geoshape.GeoShapeGryoSerializer());
        register(GryoIo.class, ItinerisP.class, new ItinerisPSerializer());
        register(GryoIo.class, AtomicLong.class, new AtomicLongKryoSerializer());
    }

    public static ItinerisIoRegistry instance() {
        return INSTANCE;
    }

    /**
     * Kryo serializer for {@link AtomicLong}. Required because Kryo's default
     * {@code FieldSerializer} cannot reflectively instantiate {@code AtomicLong}
     * (no no-arg constructor, internal state not accessible via reflection).
     */
    static class AtomicLongKryoSerializer extends Serializer<AtomicLong> {

        AtomicLongKryoSerializer() {
            setImmutable(false); // AtomicLong is mutable
        }

        @Override
        public void write(Kryo kryo, Output output, AtomicLong object) {
            output.writeLong(object.get());
        }

        @Override
        public AtomicLong read(Kryo kryo, Input input, Class<AtomicLong> type) {
            return new AtomicLong(input.readLong());
        }
    }
}
