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

package com.forwardmeasure.itineris.graphdb.query.vertex;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.VertexList;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.util.IDUtils;
import com.forwardmeasure.itineris.util.datastructures.IterablesUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * An implementation of {@link VertexListInternal} that stores the actual vertex references
 * and simply wraps an {@link ArrayList} and keeps a boolean flag to remember whether this list is in sort order.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class VertexArrayList implements VertexListInternal {

    public static final Comparator<ItinerisVertex> VERTEX_ID_COMPARATOR = (x, y) -> IDUtils.compare(x.id(), y.id());

    private final StandardItinerisTx tx;
    private List<ItinerisVertex> vertices;
    private boolean sorted;

    private VertexArrayList(StandardItinerisTx tx, List<ItinerisVertex> vertices, boolean sorted) {
        Preconditions.checkArgument(tx!=null && vertices!=null);
        this.tx = tx;
        this.vertices=vertices;
        this.sorted=sorted;
    }

    public VertexArrayList(StandardItinerisTx tx) {
        Preconditions.checkNotNull(tx);
        this.tx=tx;
        vertices = new ArrayList<>();
        sorted = true;
    }


    @Override
    public void add(ItinerisVertex n) {
        if (!vertices.isEmpty()) sorted = sorted && (IDUtils.compare(vertices.get(vertices.size()-1).id(), n.id()) <= 0);
        vertices.add(n);
    }

    @Override
    public Object getID(int pos) {
        return vertices.get(pos).id();
    }

    @Override
    public List<Object> getIDs() {
        return toIdList(vertices);
    }

    @Override
    public ItinerisVertex get(int pos) {
        return vertices.get(pos);
    }

    @Override
    public void sort() {
        if (sorted) return;
        vertices.sort(VERTEX_ID_COMPARATOR);
        sorted = true;
    }

    @Override
    public boolean isSorted() {
        return sorted;
    }

    @Override
    public VertexList subList(int fromPosition, int length) {
        return new VertexArrayList(tx,vertices.subList(fromPosition,fromPosition+length),sorted);
    }

    @Override
    public int size() {
        return vertices.size();
    }

    @Override
    public void addAll(VertexList vertexlist) {
        Preconditions.checkArgument(vertexlist instanceof VertexArrayList, "Only supporting union of identical lists.");
        VertexArrayList other = (vertexlist instanceof VertexArrayList)?(VertexArrayList)vertexlist:
                ((VertexIdList)vertexlist).toVertexArrayList();
        if (sorted && other.isSorted()) {
            //Merge sort
            vertices = IterablesUtil.mergeSort(vertices, other.vertices, VERTEX_ID_COMPARATOR);
        } else {
            sorted = false;
            vertices.addAll(other.vertices);
        }
    }

    public VertexIdList toVertexLongList() {
        List<Object> list = toIdList(vertices);
        return new VertexIdList(tx,list,sorted);
    }

    @Override
    public Iterator<ItinerisVertex> iterator() {
        return Iterators.unmodifiableIterator(vertices.iterator());
    }

    /**
     * Utility method used to convert the list of vertices into a list of vertex ids (assuming all vertices have ids)
     *
     * @param vertices
     * @return
     */
    private static List<Object> toIdList(List<ItinerisVertex> vertices) {
        List<Object> result = new ArrayList(vertices.size());
        for (ItinerisVertex n : vertices) {
            result.add(n.id());
        }
        return result;
    }

}
