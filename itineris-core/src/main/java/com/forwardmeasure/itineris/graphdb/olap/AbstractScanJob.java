// Copyright 2021 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.olap;

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanJob;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.idmanagement.IDManager;
import com.forwardmeasure.itineris.graphdb.relations.RelationCache;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.system.BaseKey;

public abstract class AbstractScanJob implements ScanJob {
    protected final GraphProvider graph;
    protected StandardItinerisTx tx;
    private IDManager idManager;

    public AbstractScanJob(Itineris graph) {
        this.graph = new GraphProvider();
        if (graph != null) this.graph.setGraph(graph);
    }

    public AbstractScanJob(AbstractScanJob copy) {
        this.graph = copy.graph;
        this.tx = copy.tx;
        this.idManager = copy.idManager;
    }

    protected abstract StandardItinerisTx startTransaction(StandardItineris graph);

    protected void open(Configuration graphConfig) {
        graph.initializeGraph(graphConfig);
        idManager = graph.get().getIDManager();
        tx = startTransaction(graph.get());
    }

    protected void close() {
        if (null != tx && tx.isOpen())
            tx.rollback();
        graph.close();
    }

    protected boolean isGhostVertex(Object vertexId, EntryList firstEntries) {
        if (idManager.isPartitionedVertex(vertexId) && !idManager.isCanonicalVertexId(((Number) vertexId).longValue())) return false;

        RelationCache relCache = tx.getEdgeSerializer().parseRelation(
            firstEntries.get(0), true, tx);
        return relCache.typeId != BaseKey.VertexExists.longId();
    }

    protected Object getVertexId(StaticBuffer key) {
        return idManager.getKeyID(key);
    }

    @Override
    public abstract AbstractScanJob clone();
}
