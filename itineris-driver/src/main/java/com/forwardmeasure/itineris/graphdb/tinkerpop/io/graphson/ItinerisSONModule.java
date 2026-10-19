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

package com.forwardmeasure.itineris.graphdb.tinkerpop.io.graphson;

import org.apache.tinkerpop.gremlin.structure.io.graphson.AbstractObjectDeserializer;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONTokens;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONUtil;
import org.apache.tinkerpop.gremlin.structure.io.graphson.TinkerPopJacksonModule;
import org.apache.tinkerpop.shaded.jackson.core.JsonGenerator;
import org.apache.tinkerpop.shaded.jackson.core.JsonParser;
import org.apache.tinkerpop.shaded.jackson.core.JsonToken;
import org.apache.tinkerpop.shaded.jackson.core.type.WritableTypeId;
import org.apache.tinkerpop.shaded.jackson.databind.DeserializationContext;
import org.apache.tinkerpop.shaded.jackson.databind.SerializerProvider;
import org.apache.tinkerpop.shaded.jackson.databind.deser.std.StdDeserializer;
import org.apache.tinkerpop.shaded.jackson.databind.jsontype.TypeSerializer;
import org.apache.tinkerpop.shaded.jackson.databind.ser.std.StdSerializer;
import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier;
import com.forwardmeasure.itineris.graphdb.tinkerpop.ItinerisPSerializer;
import com.forwardmeasure.itineris.graphdb.tinkerpop.io.ItinerisP;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Stephen Mallette (https://stephen.genoprime.com)
 */
public abstract class ItinerisSONModule extends TinkerPopJacksonModule {

    private static final String TYPE_NAMESPACE = "itineris";

    private static final Map<Class, String> TYPE_DEFINITIONS = Collections
            .unmodifiableMap(new LinkedHashMap<Class, String>() {
                {
                    put(RelationIdentifier.class, "RelationIdentifier");
                    put(Geoshape.class, "Geoshape");
                    put(ItinerisP.class, "ItinerisP");
                }
            });

    protected ItinerisSONModule() {
        super("itineris");
    }

    @Override
    public Map<Class, String> getTypeDefinitions() {
        return TYPE_DEFINITIONS;
    }

    @Override
    public String getTypeNamespace() {
        return TYPE_NAMESPACE;
    }

    public static class RelationIdentifierSerializerV1d0 extends StdSerializer<RelationIdentifier> {

        public RelationIdentifierSerializerV1d0() {
            super(RelationIdentifier.class);
        }

        @Override
        public void serialize(final RelationIdentifier relationIdentifier, final JsonGenerator jsonGenerator,
                final SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(relationIdentifier.toString());
        }

        @Override
        public void serializeWithType(final RelationIdentifier relationIdentifier, final JsonGenerator jsonGenerator,
                final SerializerProvider serializerProvider, final TypeSerializer typeSerializer)
                throws IOException {
            // since jackson 2.9, must keep track of `typeIdDef` in order to close it properly
            final WritableTypeId typeIdDef = typeSerializer.writeTypePrefix(jsonGenerator, typeSerializer.typeId(relationIdentifier, JsonToken.VALUE_STRING));
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField(GraphSONTokens.VALUE, relationIdentifier.toString());
            jsonGenerator.writeStringField(GraphSONTokens.CLASS, HashMap.class.getName());
            jsonGenerator.writeEndObject();
            typeSerializer.writeTypeSuffix(jsonGenerator, typeIdDef);
        }
    }

    public static class RelationIdentifierDeserializerV1d0 extends StdDeserializer<RelationIdentifier> {
        public RelationIdentifierDeserializerV1d0() {
            super(RelationIdentifier.class);
        }

        @Override
        public RelationIdentifier deserialize(final JsonParser jsonParser,
                final DeserializationContext deserializationContext) throws IOException {
            jsonParser.nextToken();
            final Map<String, Object> mapData = deserializationContext.readValue(jsonParser, Map.class);
            return RelationIdentifier.parse((String) mapData.get(GraphSONTokens.VALUE));
        }
    }

    public static class RelationIdentifierSerializerV2d0 extends StdSerializer<RelationIdentifier> {

        public RelationIdentifierSerializerV2d0() {
            super(RelationIdentifier.class);
        }

        @Override
        public void serialize(final RelationIdentifier relationIdentifier, final JsonGenerator jsonGenerator,
                              final SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(relationIdentifier.toString());
        }

        @Override
        public void serializeWithType(final RelationIdentifier relationIdentifier, final JsonGenerator jsonGenerator,
                                      final SerializerProvider serializerProvider, final TypeSerializer typeSerializer) throws IOException {

            jsonGenerator.writeStartObject();
            if (typeSerializer != null) jsonGenerator.writeStringField(GraphSONTokens.VALUETYPE, TYPE_NAMESPACE + ":" + TYPE_DEFINITIONS.get(RelationIdentifier.class));
            jsonGenerator.writeFieldName(GraphSONTokens.VALUEPROP);
            GraphSONUtil.writeStartObject(relationIdentifier, jsonGenerator, typeSerializer);
            GraphSONUtil.writeWithType("relationId", relationIdentifier.toString(), jsonGenerator, serializerProvider, typeSerializer);
            GraphSONUtil.writeEndObject(relationIdentifier, jsonGenerator, typeSerializer);
            jsonGenerator.writeEndObject();
        }
    }

    public static class RelationIdentifierDeserializerV2d0 extends AbstractObjectDeserializer<RelationIdentifier> {
        public RelationIdentifierDeserializerV2d0() {
            super(RelationIdentifier.class);
        }

        @Override
        public RelationIdentifier createObject(Map data) {
            return RelationIdentifier.parse((String) data.get("relationId"));
        }
    }

    public static class ItinerisPSerializerV2d0 extends StdSerializer<ItinerisP> {
        public ItinerisPSerializerV2d0() {
            super(ItinerisP.class);
        }

        @Override
        public void serialize(final ItinerisP predicate, final JsonGenerator jsonGenerator,
                              final SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(predicate.toString());
        }

        @Override
        public void serializeWithType(final ItinerisP value, final JsonGenerator jgen,
                                      final SerializerProvider serializerProvider, final TypeSerializer typeSerializer) throws IOException {
            String predicateName = value.getBiPredicate().toString();
            Object arg = value.getValue();

            jgen.writeStartObject();
            if (typeSerializer != null) jgen.writeStringField(GraphSONTokens.VALUETYPE, TYPE_NAMESPACE + ":" + TYPE_DEFINITIONS.get(ItinerisP.class));
            jgen.writeFieldName(GraphSONTokens.VALUEPROP);
            GraphSONUtil.writeStartObject(value, jgen, typeSerializer);
            GraphSONUtil.writeWithType(GraphSONTokens.PREDICATE, predicateName, jgen, serializerProvider, typeSerializer);
            GraphSONUtil.writeWithType(GraphSONTokens.VALUE, arg, jgen, serializerProvider, typeSerializer);
            GraphSONUtil.writeEndObject(value, jgen, typeSerializer);
            jgen.writeEndObject();
        }

    }

    public static class ItinerisPDeserializerV2d0 extends AbstractObjectDeserializer<ItinerisP> {

        public ItinerisPDeserializerV2d0() {
            super(ItinerisP.class);
        }

        @Override
        public ItinerisP createObject(Map<String, Object> data) {
            String predicate = (String) data.get(GraphSONTokens.PREDICATE);
            Object value = data.get(GraphSONTokens.VALUE);
            return ItinerisPSerializer.createPredicateWithValue(predicate, value);
        }

        @Override
        public boolean isCachable() {
            return true;
        }
    }

}
