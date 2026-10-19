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

package com.forwardmeasure.itineris.graphdb;

import com.google.common.collect.Iterables;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexArrayList;
import com.forwardmeasure.itineris.graphdb.query.vertex.VertexIdList;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.util.IDUtils;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class VertexListTest {

    @Test
    public void testLists() {

        int num = 13;

        Itineris g = ItinerisFactory.open("inmemory");
        StandardItinerisTx tx = (StandardItinerisTx) g.newTransaction();
        VertexIdList vll = new VertexIdList(tx);
        VertexArrayList val = new VertexArrayList(tx);
        for (int i=0; i<num; i++) {
            ItinerisVertex v = tx.addVertex();
            vll.add(v);
            val.add(v);
        }

        assertEquals(num, Iterables.size(vll));
        assertEquals(num, Iterables.size(val));

        vll.sort();
        val.sort();
        assertTrue(vll.isSorted());
        assertTrue(val.isSorted());

        for (Iterable<ItinerisVertex> iterable : new Iterable[]{val,vll}) {
            Iterator<ItinerisVertex> iterator = iterable.iterator();
            ItinerisVertex previous = null;
            for (int i = 0; i < num; i++) {
                ItinerisVertex next = iterator.next();
                if (previous!=null) assertTrue(IDUtils.compare(previous.id(), next.id()) < 0);
                previous = next;
            }
            try {
                iterator.next();
                fail();
            } catch (NoSuchElementException ignored) {

            }
        }


        tx.commit();
        g.close();

    }


}
