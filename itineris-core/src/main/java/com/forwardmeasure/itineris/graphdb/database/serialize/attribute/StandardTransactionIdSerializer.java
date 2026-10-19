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

package com.forwardmeasure.itineris.graphdb.database.serialize.attribute;

import com.forwardmeasure.itineris.core.attribute.AttributeSerializer;
import com.forwardmeasure.itineris.diskstorage.ScanBuffer;
import com.forwardmeasure.itineris.diskstorage.WriteBuffer;
import com.forwardmeasure.itineris.graphdb.database.serialize.DataOutput;
import com.forwardmeasure.itineris.graphdb.database.serialize.Serializer;
import com.forwardmeasure.itineris.graphdb.database.serialize.SerializerInjected;
import com.forwardmeasure.itineris.graphdb.log.StandardTransactionId;

import java.time.Instant;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class StandardTransactionIdSerializer implements AttributeSerializer<StandardTransactionId>, SerializerInjected {

    private Serializer serializer;

    @Override
    public StandardTransactionId read(ScanBuffer buffer) {
        return new StandardTransactionId(serializer.readObjectNotNull(buffer,String.class),
                serializer.readObjectNotNull(buffer,Long.class),
                serializer.readObjectNotNull(buffer,Instant.class));
    }

    @Override
    public void write(WriteBuffer buffer, StandardTransactionId attribute) {
        DataOutput out = (DataOutput)buffer;
        out.writeObjectNotNull(attribute.getInstanceId());
        out.writeObjectNotNull(attribute.getTransactionId());
        out.writeObjectNotNull(attribute.getTransactionTime());
    }

    @Override
    public void setSerializer(Serializer serializer) {
        this.serializer=serializer;
    }
}
