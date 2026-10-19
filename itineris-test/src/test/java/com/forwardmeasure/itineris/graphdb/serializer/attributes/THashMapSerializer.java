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

package com.forwardmeasure.itineris.graphdb.serializer.attributes;

import com.forwardmeasure.itineris.diskstorage.ScanBuffer;
import com.forwardmeasure.itineris.graphdb.database.serialize.attribute.SerializableSerializer;

import java.util.HashMap;

/**
 * @author Scott McQuillan (scott.mcquillan@uk.ibm.com)
 */
public class THashMapSerializer extends SerializableSerializer<HashMap> {

    @Override
    public HashMap read(ScanBuffer buffer) {
        HashMap hashMap = super.read(buffer);
        hashMap.put(THashMapSerializer.class.getName(), null); // Unit test will check this got added
        return hashMap;
    }
}
