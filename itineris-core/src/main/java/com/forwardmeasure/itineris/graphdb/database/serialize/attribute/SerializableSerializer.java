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

package com.forwardmeasure.itineris.graphdb.database.serialize.attribute;

import org.apache.commons.lang3.SerializationUtils;
import com.forwardmeasure.itineris.core.attribute.AttributeSerializer;
import com.forwardmeasure.itineris.diskstorage.ScanBuffer;
import com.forwardmeasure.itineris.diskstorage.WriteBuffer;
import com.forwardmeasure.itineris.graphdb.database.serialize.DataOutput;
import com.forwardmeasure.itineris.graphdb.database.serialize.Serializer;
import com.forwardmeasure.itineris.graphdb.database.serialize.SerializerInjected;

import java.io.Serializable;

/**
 * Serializes {@link Serializable} objects.
 * @param <T> Serializable type
 */
public class SerializableSerializer<T extends Serializable> implements AttributeSerializer<T>, SerializerInjected {

    private Serializer serializer;

    @Override
    public T read(ScanBuffer buffer) {
        byte[] data = serializer.readObjectNotNull(buffer,byte[].class);
        return SerializationUtils.deserialize(data);
    }

    @Override
    public void write(WriteBuffer buffer, T attribute) {
        DataOutput out = (DataOutput) buffer;
        out.writeObjectNotNull(SerializationUtils.serialize(attribute));
    }

    @Override
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

}
