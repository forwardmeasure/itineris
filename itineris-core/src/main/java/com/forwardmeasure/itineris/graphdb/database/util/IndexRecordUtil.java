// Copyright 2022 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.database.util;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import org.apache.commons.lang.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.ReadBuffer;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexEntry;
import com.forwardmeasure.itineris.diskstorage.indexing.StandardKeyInformation;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.util.HashingUtil;
import com.forwardmeasure.itineris.diskstorage.util.StaticArrayEntry;
import com.forwardmeasure.itineris.graphdb.database.EdgeSerializer;
import com.forwardmeasure.itineris.graphdb.database.IndexRecordEntry;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.idhandling.IDHandler;
import com.forwardmeasure.itineris.graphdb.database.idhandling.VariableLong;
import com.forwardmeasure.itineris.graphdb.database.index.IndexMutationType;
import com.forwardmeasure.itineris.graphdb.database.index.IndexRecords;
import com.forwardmeasure.itineris.graphdb.database.index.IndexUpdate;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.graphdb.database.serialize.DataOutput;
import com.forwardmeasure.itineris.graphdb.database.serialize.InternalAttributeUtil;
import com.forwardmeasure.itineris.graphdb.database.serialize.Serializer;
import com.forwardmeasure.itineris.graphdb.idmanagement.IDManager;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelation;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;
import com.forwardmeasure.itineris.graphdb.olap.QueryContainer;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.relations.RelationIdentifier;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.CompositeIndexType;
import com.forwardmeasure.itineris.graphdb.types.IndexField;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;
import com.forwardmeasure.itineris.graphdb.types.ParameterIndexField;
import com.forwardmeasure.itineris.graphdb.types.ParameterType;
import com.forwardmeasure.itineris.graphdb.types.TypeInspector;
import com.forwardmeasure.itineris.util.IDUtils;
import com.forwardmeasure.itineris.util.encoding.LongEncoding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.forwardmeasure.itineris.util.encoding.LongEncoding.STRING_ENCODING_MARKER;

public class IndexRecordUtil {

    public static final IndexAppliesToFunction FULL_INDEX_APPLIES_TO_FILTER = IndexRecordUtil::indexAppliesTo;
    public static final IndexAppliesToFunction INDEX_APPLIES_TO_NO_CONSTRAINTS_FILTER = IndexRecordUtil::indexAppliesToWithoutConstraints;

    private static final int DEFAULT_OBJECT_BYTELEN = 30;
    private static final byte FIRST_INDEX_COLUMN_BYTE = 0;

    public static Object[] getValues(IndexRecordEntry[] record) {
        final Object[] values = new Object[record.length];
        for (int i = 0; i < values.length; i++) {
            values[i]=record[i].getValue();
        }
        return values;
    }

    public static MixedIndexType getMixedIndex(String indexName, StandardItinerisTx transaction) {
        final IndexType index = ManagementSystem.getGraphIndexDirect(indexName, transaction);
        Preconditions.checkArgument(index!=null,"Index with name [%s] is unknown or not configured properly",indexName);
        Preconditions.checkArgument(index.isMixedIndex());
        return (MixedIndexType)index;
    }

    public static String element2String(ItinerisElement element) {
        return element2String(element.id());
    }

    /**
     * Convert an element's (including vertex and relation) id into a String
     *
     * @param elementId
     * @return
     */
    public static String element2String(Object elementId) {
        Preconditions.checkArgument(elementId instanceof Long || elementId instanceof RelationIdentifier || elementId instanceof String);
        if (elementId instanceof RelationIdentifier) {
            return ((RelationIdentifier) elementId).toString();
        } else {
            return id2Name(elementId);
        }
    }

    public static Object string2ElementId(String str) {
        if (StringUtils.isEmpty(str)) {
            throw new IllegalArgumentException("Empty string cannot be converted to a valid id");
        }
        if (str.contains(RelationIdentifier.TOSTRING_DELIMITER)) {
            return RelationIdentifier.parse(str);
        } else {
            return name2Id(str);
        }
    }

    public static String key2Field(MixedIndexType index, PropertyKey key) {
        return key2Field(index.getField(key));
    }

    public static String key2Field(ParameterIndexField field) {
        assert field!=null;
        return ParameterType.MAPPED_NAME.findParameter(field.getParameters(),keyID2Name(field.getFieldKey()));
    }

    public static String keyID2Name(PropertyKey key) {
        return id2Name(key.longId());
    }

    public static String id2Name(Object id) {
        IDUtils.checkId(id);
        if (id instanceof Number) {
            return LongEncoding.encode(((Number) id).longValue());
        } else {
            return STRING_ENCODING_MARKER + id.toString();
        }
    }

    public static Object name2Id(String name) {
        if (name.charAt(0) == STRING_ENCODING_MARKER) {
            return name.substring(1);
        } else {
            return LongEncoding.decode(name);
        }
    }

    public static RelationIdentifier bytebuffer2RelationId(ReadBuffer b) {
        Object[] relationId = new Object[4];
        relationId[0] = VariableLong.readPositive(b);
        relationId[1] = IDHandler.readVertexId(b, true);
        relationId[2] = VariableLong.readPositive(b);
        if (b.hasRemaining()) {
            relationId[3] = IDHandler.readVertexId(b, true);
        } else {
            relationId = Arrays.copyOfRange(relationId,0,3);
        }
        return RelationIdentifier.get(relationId);
    }

    public static StandardKeyInformation getKeyInformation(final ParameterIndexField field) {
        return new StandardKeyInformation(field.getFieldKey(),field.getParameters());
    }

    public static IndexMutationType getUpdateType(InternalRelation relation, boolean isInlined) {
        assert relation.isNew() || relation.isRemoved();
        return isInlined ? IndexMutationType.UPDATE : (relation.isNew() ? IndexMutationType.ADD : IndexMutationType.DELETE);
    }

    public static boolean indexAppliesTo(IndexType index, ItinerisElement element) {
        return indexAppliesToWithoutConstraints(index, element) && indexMatchesConstraints(index, element);
    }

    public static boolean indexAppliesToWithoutConstraints(IndexType index, ItinerisElement element) {
        return index.getElement().isInstance(element) &&
            (!(index instanceof CompositeIndexType) || ((CompositeIndexType)index).getStatus()!= SchemaStatus.DISABLED);
    }

    public static boolean indexMatchesConstraints(IndexType index, ItinerisElement element) {
        return !index.hasSchemaTypeConstraint() ||
            index.getElement().matchesConstraint(index.getSchemaTypeConstraint(),element);
    }

    public static PropertyKey[] getKeysOfRecords(IndexRecordEntry[] record) {
        final PropertyKey[] keys = new PropertyKey[record.length];
        for (int i=0;i<record.length;i++) keys[i]=record[i].getKey();
        return keys;
    }

    public static int getIndexTTL(InternalVertex vertex, PropertyKey... keys) {
        int ttl = StandardItineris.getTTL(vertex);
        for (final PropertyKey key : keys) {
            final int kttl = ((InternalRelationType) key).getTTL();
            if (kttl > 0 && (kttl < ttl || ttl <= 0)) ttl = kttl;
        }
        return ttl;
    }

    public static IndexRecordEntry[] indexMatch(ItinerisRelation relation, CompositeIndexType index) {
        final IndexField[] fields = index.getFieldKeys();
        final IndexRecordEntry[] match = new IndexRecordEntry[fields.length];
        for (int i = 0; i <fields.length; i++) {
            final IndexField f = fields[i];
            final Object value = relation.valueOrNull(f.getFieldKey());
            if (value==null) return null; //No match
            match[i] = new IndexRecordEntry(relation.longId(),value,f.getFieldKey());
        }
        return match;
    }

    public static IndexRecords indexMatches(ItinerisVertex vertex, CompositeIndexType index) {
        return indexMatches(vertex,index,null,null);
    }

    public static IndexRecords indexMatches(ItinerisVertex vertex, CompositeIndexType index,
                                            PropertyKey replaceKey, Object replaceValue) {
        final IndexRecords matches = new IndexRecords();
        final IndexField[] fields = index.getFieldKeys();
        if (indexAppliesTo(index,vertex)) {
            indexMatches(vertex,new IndexRecordEntry[fields.length],matches,fields,0,false,
                replaceKey,new IndexRecordEntry(0,replaceValue,replaceKey));
        }
        return matches;
    }

    public static IndexRecords indexMatches(ItinerisVertex vertex, CompositeIndexType index,
                                             boolean onlyLoaded, PropertyKey replaceKey, IndexRecordEntry replaceValue) {
        final IndexRecords matches = new IndexRecords();
        final IndexField[] fields = index.getFieldKeys();
        indexMatches(vertex,new IndexRecordEntry[fields.length],matches,fields,0,onlyLoaded,replaceKey,replaceValue);
        return matches;
    }

    public static void indexMatches(ItinerisVertex vertex, IndexRecordEntry[] current, IndexRecords matches,
                                     IndexField[] fields, int pos,
                                     boolean onlyLoaded, PropertyKey replaceKey, IndexRecordEntry replaceValue) {
        if (pos>= fields.length) {
            matches.add(current);
            return;
        }

        final PropertyKey key = fields[pos].getFieldKey();

        List<IndexRecordEntry> values;
        if (key.equals(replaceKey)) {
            values = ImmutableList.of(replaceValue);
        } else {
            values = new ArrayList<>();
            Iterator<ItinerisVertexProperty> props;
            if (onlyLoaded ||
                (!vertex.isNew() && IDManager.VertexIDType.PartitionedVertex.is(vertex.id()))) {
                //going through transaction so we can query deleted vertices
                final VertexCentricQueryBuilder qb = ((InternalVertex)vertex).tx().query(vertex);
                qb.noPartitionRestriction().type(key);
                if (onlyLoaded) qb.queryOnlyLoaded();
                props = qb.properties().iterator();
            } else {
                props = Iterators.transform(vertex.properties(key.name()), i -> (ItinerisVertexProperty) i);
            }
            while (props.hasNext()) {
                ItinerisVertexProperty p = props.next();
                assert !onlyLoaded || p.isLoaded() || p.isRemoved();
                assert key.dataType().equals(p.value().getClass()) : key + " -> " + p;
                values.add(new IndexRecordEntry(p));
            }
        }
        for (final IndexRecordEntry value : values) {
            current[pos]=value;
            indexMatches(vertex,current,matches,fields,pos+1,onlyLoaded,replaceKey,replaceValue);
        }
    }


    private static Entry getIndexEntry(CompositeIndexType index, IndexRecordEntry[] record,
                                       ItinerisElement element,
                                       Serializer serializer,
                                       TypeInspector typeInspector,
                                       EdgeSerializer edgeSerializer) {

        List<Entry> inlineProperties = getInlineProperties(element, index, typeInspector, edgeSerializer);
        int inlinePropertiesSize = getInlinePropertiesSize(inlineProperties);

        final DataOutput out = serializer.getDataOutput(1 + 8 + 8 * record.length + 4 * 8 + inlinePropertiesSize);
        out.putByte(FIRST_INDEX_COLUMN_BYTE);
        if (index.getCardinality() != Cardinality.SINGLE) {
            if (element instanceof ItinerisVertex) {
                IDHandler.writeVertexId(out, element.id(), true);
            } else {
                assert element instanceof ItinerisRelation;
                assert ((ItinerisRelation) element).longId() == ((RelationIdentifier) element.id()).getRelationId();
                VariableLong.writePositive(out, ((ItinerisRelation) element).longId());
            }
            if (index.getCardinality() != Cardinality.SET) {
                for (final IndexRecordEntry re : record) {
                    VariableLong.writePositive(out, re.getRelationId());
                }
            }
        }
        final int valuePosition = out.getPosition();
        if (element instanceof ItinerisVertex) {
            IDHandler.writeVertexId(out, element.id(), true);
            writeInlineProperties(inlineProperties, out);
        } else {
            assert element instanceof ItinerisRelation;
            final RelationIdentifier rid = (RelationIdentifier) element.id();
            VariableLong.writePositive(out, rid.getRelationId());
            IDHandler.writeVertexId(out, rid.getOutVertexId(), true);
            VariableLong.writePositive(out, rid.getTypeId());
            if (rid.getInVertexId() != null) {
                IDHandler.writeVertexId(out, rid.getInVertexId(), true);
            }
        }
        return new StaticArrayEntry(out.getStaticBuffer(), valuePosition);
    }

    public static StaticBuffer getIndexKey(CompositeIndexType index, IndexRecordEntry[] record, Serializer serializer, boolean hashKeys, HashingUtil.HashLength hashLength) {
        return getIndexKey(index, IndexRecordUtil.getValues(record), serializer, hashKeys, hashLength);
    }

    public static StaticBuffer getIndexKey(CompositeIndexType index, Object[] values, Serializer serializer, boolean hashKeys, HashingUtil.HashLength hashLength) {
        final DataOutput out = serializer.getDataOutput(8*DEFAULT_OBJECT_BYTELEN + 8);
        VariableLong.writePositive(out, index.longId());
        final IndexField[] fields = index.getFieldKeys();
        Preconditions.checkArgument(fields.length>0 && fields.length==values.length);
        for (int i = 0; i < fields.length; i++) {
            final IndexField f = fields[i];
            final Object value = values[i];
            Preconditions.checkNotNull(value);
            if (InternalAttributeUtil.hasGenericDataType(f.getFieldKey())) {
                out.writeClassAndObject(value);
            } else {
                assert value.getClass().equals(f.getFieldKey().dataType()) : value.getClass() + " - " + f.getFieldKey().dataType();
                out.writeObjectNotNull(value);
            }
        }
        StaticBuffer key = out.getStaticBuffer();
        if (hashKeys) key = HashingUtil.hashPrefixKey(hashLength,key);
        return key;
    }

    public static long getIndexIdFromKey(StaticBuffer key, boolean hashKeys, HashingUtil.HashLength hashLength) {
        if (hashKeys) key = HashingUtil.getKey(hashLength,key);
        return VariableLong.readPositive(key.asReadBuffer());
    }

    public static IndexUpdate<StaticBuffer, Entry> getCompositeIndexUpdate(CompositeIndexType index, IndexMutationType indexMutationType, IndexRecordEntry[] record,
                                                                           ItinerisElement element,
                                                                           Serializer serializer,
                                                                           TypeInspector typeInspector,
                                                                           EdgeSerializer edgeSerializer,
                                                                           boolean hashKeys,
                                                                           HashingUtil.HashLength hashLength){
        return new IndexUpdate<>(index, indexMutationType,
            getIndexKey(index, record, serializer, hashKeys, hashLength),
            getIndexEntry(index, record, element, serializer, typeInspector, edgeSerializer), element);
    }

    public static IndexUpdate<String, IndexEntry> getMixedIndexUpdate(ItinerisElement element, PropertyKey key, Object value,
                                                                      MixedIndexType index, IndexMutationType updateType)  {
        return new IndexUpdate<>(index, updateType, element2String(element), new IndexEntry(key2Field(index.getField(key)), value), element);
    }

    public static int getInlinePropertiesSize(List<Entry> inlineProperties) {
        return inlineProperties.size() * Integer.BYTES * 2 + inlineProperties.stream().mapToInt(StaticBuffer::length).sum();
    }

    public static void writeInlineProperties(List<Entry> inlineProperties, DataOutput out) {
        inlineProperties.forEach(entry -> {
            out.putInt(entry.length());
            out.putInt(entry.getValuePosition());
            out.putBytes(entry);
        });
    }

    public static Iterable<Entry> readInlineProperties(ReadBuffer readBuffer) {

        return () -> new Iterator<Entry>() {
            @Override
            public boolean hasNext() {
                return readBuffer.hasRemaining();
            }

            @Override
            public Entry next() {
                int entryDataSize = readBuffer.getInt();
                int valuePos = readBuffer.getInt();
                byte[] entryBytes = readBuffer.getBytes(entryDataSize);
                return new StaticArrayEntry(entryBytes, valuePos);
            }
        };
    }

    public static List<Entry> getInlineProperties(ItinerisElement element,
                                                  CompositeIndexType index,
                                                  TypeInspector typeInspector,
                                                  EdgeSerializer edgeSerializer) {
        if (element instanceof ItinerisVertex && index.getInlineFieldKeys().length != 0 && !element.isRemoved()) {
            Iterator<VertexProperty<Object>> props = ((ItinerisVertex) element).properties(index.getInlineFieldKeys());
            return IteratorUtils.list(IteratorUtils.map(props,
                prop -> edgeSerializer.writeRelation((InternalRelation) prop, 0, typeInspector)));
        } else {
            return Collections.emptyList();
        }
    }

    public static Map<String, SliceQuery> getInlinePropertiesQueries(CompositeIndexType index, StandardItinerisTx tx) {
        if (index.getInlineFieldKeys().length == 0) {
            return Collections.emptyMap();
        } else {

            Map<String, SliceQuery> result = new HashMap<>(index.getInlineFieldKeys().length);
            for(String inlineKey: index.getInlineFieldKeys()) {
                QueryContainer qc = new QueryContainer(tx);
                qc.addQuery().direction(Direction.OUT).keys(inlineKey).properties();
                List<SliceQuery> sliceQueries = qc.getSliceQueries();
                assert sliceQueries.size() == 1;
                result.put(inlineKey, sliceQueries.get(0));
            }
            return result;
        }
    }
}
