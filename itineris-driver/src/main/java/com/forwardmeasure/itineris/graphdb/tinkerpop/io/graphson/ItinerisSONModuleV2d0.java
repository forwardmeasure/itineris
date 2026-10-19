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

package com.forwardmeasure.itineris.graphdb.tinkerpop.io.graphson;

import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.ItinerisP;

/**
 * Supports GraphSON 2.0
 */
public class ItinerisSONModuleV2d0 extends ItinerisSONModule {

    private ItinerisSONModuleV2d0() {
        super();
        addSerializer(RelationIdentifier.class, new RelationIdentifierSerializerV2d0());
        addSerializer(Geoshape.class, new Geoshape.GeoshapeGsonSerializerV2d0());
        addSerializer(ItinerisP.class, new ItinerisPSerializerV2d0());

        addDeserializer(RelationIdentifier.class, new RelationIdentifierDeserializerV2d0());
        addDeserializer(Geoshape.class, new Geoshape.GeoshapeGsonDeserializerV2d0());
        addDeserializer(ItinerisP.class, new ItinerisPDeserializerV2d0());
    }

    private static final ItinerisSONModuleV2d0 INSTANCE = new ItinerisSONModuleV2d0();

    public static ItinerisSONModuleV2d0 getInstance() {
        return INSTANCE;
    }

}
