// Copyright 2019 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.database;

import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedProperty;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexEntry;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexInformation;
import com.forwardmeasure.itineris.graphdb.database.serialize.Serializer;
import com.forwardmeasure.itineris.graphdb.internal.ElementCategory;
import com.forwardmeasure.itineris.graphdb.internal.ElementLifeCycle;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;
import com.forwardmeasure.itineris.graphdb.types.ParameterIndexField;
import com.forwardmeasure.itineris.graphdb.vertices.StandardVertex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class IndexSerializerTest {

    @Test
    public void testReindexElementNotAppliesTo() {
        Configuration config = mock(Configuration.class);
        Serializer serializer = mock(Serializer.class);
        EdgeSerializer edgeSerializer = mock(EdgeSerializer.class);
        Map<String, ? extends IndexInformation> indexes = new HashMap<>();

        IndexSerializer mockSerializer = new IndexSerializer(config, edgeSerializer, serializer, indexes, true);
        ItinerisElement nonIndexableElement = mock(ItinerisElement.class);
        MixedIndexType mit = mock(MixedIndexType.class);
        doReturn(ElementCategory.VERTEX).when(mit).getElement();

        Map<String, Map<String, List<IndexEntry>>> docStore = new HashMap<>();
        assertFalse("re-index", mockSerializer.reindexElement(nonIndexableElement, mit, docStore));

    }

    @Test
    public void testReindexElementAppliesToWithEntries() {
        Map<String, Map<String, List<IndexEntry>>> docStore = new HashMap<>();
        IndexSerializer mockSerializer = mockSerializer();
        MixedIndexType mit = mock(MixedIndexType.class);

        ItinerisElement indexableElement = mockIndexAppliesTo(mit, true);

        assertTrue("re-index", mockSerializer.reindexElement(indexableElement, mit, docStore));
        assertEquals("doc store size", 1, docStore.size());

    }

    @Test
    public void testReindexElementAppliesToNoEntries() {
        Map<String, Map<String, List<IndexEntry>>> docStore = new HashMap<>();
        IndexSerializer mockSerializer = mockSerializer();
        MixedIndexType mit = mock(MixedIndexType.class);
        ItinerisElement indexableElement = mockIndexAppliesTo(mit, false);

        assertFalse("re-index", mockSerializer.reindexElement(indexableElement, mit, docStore));
        assertEquals("doc store size", 0, docStore.size());

    }

    private IndexSerializer mockSerializer() {
        Configuration config = mock(Configuration.class);
        Serializer serializer = mock(Serializer.class);
        EdgeSerializer edgeSerializer = mock(EdgeSerializer.class);
        Map<String, ? extends IndexInformation> indexes = new HashMap<>();
        return spy(new IndexSerializer(config, edgeSerializer, serializer, indexes, true));
    }

    private ItinerisElement mockIndexAppliesTo(MixedIndexType mit, boolean indexable) {
        String key = "foo";
        String value = "bar";

        ItinerisElement indexableElement = mockIndexableElement(key, value, indexable);
        ElementCategory ec = ElementCategory.VERTEX;

        doReturn(ec).when(mit).getElement();

        doReturn(false).when(mit).hasSchemaTypeConstraint();

        PropertyKey pk = mock(PropertyKey.class);
        doReturn(1L).when(pk).id();
        doReturn(1L).when(pk).longId();
        doReturn(key).when(pk).name();
        ParameterIndexField pif = mock(ParameterIndexField.class);
        Parameter[] parameter = { new Parameter(key, value) };
        doReturn(parameter).when(pif).getParameters();
        doReturn(SchemaStatus.REGISTERED).when(pif).getStatus();
        doReturn(pk).when(pif).getFieldKey();

        ParameterIndexField[] ifField = { pif };

        doReturn(ifField).when(mit).getFieldKeys();

        return indexableElement;

    }

    private ItinerisElement mockIndexableElement(String key, String value, boolean indexable) {
        StandardItinerisTx tx = mock(StandardItinerisTx.class);
        doReturn(tx).when(tx).getNextTx();
        ItinerisElement indexableElement = spy(new StandardVertex(tx, 1L, ElementLifeCycle.New));
        Property pk2 = new DetachedProperty(key, value);
        Iterator it = Arrays.asList(pk2).iterator();
        doReturn(it).when(indexableElement).properties(key);
        if (indexable)
            doReturn(Arrays.asList(value).iterator()).when(indexableElement).values(key);
        else
            doReturn(new ArrayList<>().iterator()).when(indexableElement).values(key); // skpping the values section!!

        return indexableElement;

    }

}
