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

package com.forwardmeasure.itineris.graphdb.olap.job;

import com.google.common.base.Preconditions;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.RelationTypeIndex;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.diskstorage.BackendTransaction;
import com.forwardmeasure.itineris.diskstorage.Entry;
import com.forwardmeasure.itineris.diskstorage.EntryList;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.cache.KCVSCache;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanJob;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanMetrics;
import com.forwardmeasure.itineris.diskstorage.util.BufferUtil;
import com.forwardmeasure.itineris.graphdb.database.IndexSerializer;
import com.forwardmeasure.itineris.graphdb.database.management.RelationTypeIndexWrapper;
import com.forwardmeasure.itineris.graphdb.idmanagement.IDManager;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.olap.GraphProvider;
import com.forwardmeasure.itineris.graphdb.olap.QueryContainer;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.CompositeIndexType;
import com.forwardmeasure.itineris.graphdb.types.vertices.ItinerisSchemaVertex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class IndexRemoveJob extends IndexUpdateJob implements ScanJob {

    private final GraphProvider graph = new GraphProvider();

    public static final String DELETED_RECORDS_COUNT = "deletes";

    private IndexSerializer indexSerializer;
    private long graphIndexId;
    private IDManager idManager;

    public IndexRemoveJob() {
        super();
    }

    protected IndexRemoveJob(IndexRemoveJob copy) {
        super(copy);
        if (copy.graph.isProvided()) this.graph.setGraph(copy.graph.get());
    }

    public IndexRemoveJob(final Itineris graph, final String indexName, final String indexType) {
        super(indexName,indexType);
        this.graph.setGraph(graph);
    }

    @Override
    public void workerIterationEnd(ScanMetrics metrics) {
        super.workerIterationEnd(metrics);
        graph.close();
    }

    @Override
    public void workerIterationStart(Configuration config, Configuration graphConf, ScanMetrics metrics) {
        graph.initializeGraph(graphConf);
        indexSerializer = graph.get().getIndexSerializer();
        idManager = graph.get().getIDManager();
        try {
            super.workerIterationStart(graph.get(), config, metrics);
        } catch (Throwable e) {
            graph.close();
            throw e;
        }
    }

    @Override
    protected void validateIndexStatus() {
        if(!(index instanceof RelationTypeIndex || index instanceof ItinerisIndex)) {
            throw new UnsupportedOperationException("Unsupported index found: "+index);
        }
        if (index instanceof ItinerisIndex) {
            ItinerisIndex graphIndex = (ItinerisIndex)index;
            if (graphIndex.isMixedIndex())
                throw new UnsupportedOperationException("Cannot remove mixed indexes through Itineris. This can " +
                        "only be accomplished in the indexing system directly.");
            CompositeIndexType indexType = (CompositeIndexType) managementSystem.getSchemaVertex(index).asIndexType();
            graphIndexId = indexType.longId();
        }

        //Must be a relation type index or a composite graph index
        ItinerisSchemaVertex schemaVertex = managementSystem.getSchemaVertex(index);
        SchemaStatus actualStatus = schemaVertex.getStatus();
        Preconditions.checkArgument(
            actualStatus == SchemaStatus.REGISTERED || actualStatus == SchemaStatus.DISABLED || actualStatus == SchemaStatus.DISCARDED,
            "The index [%s] must be disabled before it can be removed, but is currently [%s].", indexName, actualStatus);
    }

    @Override
    public void process(StaticBuffer key, Map<SliceQuery, EntryList> entries, ScanMetrics metrics) {
        //The queries are already tailored enough => everything should be removed
        try {
            BackendTransaction mutator = writeTx.getTxHandle();
            final List<Entry> deletions;
            if (entries.size()==1) {
                deletions = entries.values().iterator().next();
            } else {
                final int size = IteratorUtils.stream(entries.values().iterator()).map(List::size).reduce(0, Integer::sum);
                deletions = new ArrayList<>(size);
                entries.values().forEach(deletions::addAll);
            }
            metrics.incrementCustom(DELETED_RECORDS_COUNT,deletions.size());
            if (isRelationTypeIndex()) {
                mutator.mutateEdges(key, KCVSCache.NO_ADDITIONS, deletions);
            } else {
                mutator.mutateIndex(key, KCVSCache.NO_ADDITIONS, deletions);
            }
        } catch (final Exception e) {
            managementSystem.rollback();
            writeTx.rollback();
            metrics.incrementCustom(FAILED_TX);
            throw new ItinerisException(e.getMessage(), e);
        }
    }

    @Override
    public List<SliceQuery> getQueries() {
        if (isGlobalGraphIndex()) {
            //Everything
            return Collections.singletonList(new SliceQuery(BufferUtil.zeroBuffer(1), BufferUtil.oneBuffer(128)));
        } else {
            RelationTypeIndexWrapper wrapper = (RelationTypeIndexWrapper)index;
            InternalRelationType wrappedType = wrapper.getWrappedType();
            Direction direction=null;
            for (Direction dir : Direction.values()) if (wrappedType.isUnidirected(dir)) direction=dir;
            assert direction!=null;

            StandardItinerisTx tx = (StandardItinerisTx)graph.get().buildTransaction().readOnly().start();
            try {
                QueryContainer qc = new QueryContainer(tx);
                qc.addQuery().type(wrappedType).direction(direction).relations();
                return qc.getSliceQueries();
            } finally {
                tx.rollback();
            }
        }
    }

    @Override
    public Predicate<StaticBuffer> getKeyFilter() {
        if (isGlobalGraphIndex()) {
            assert graphIndexId>0;
            return (k -> {
                try {
                    return indexSerializer.getIndexIdFromKey(k) == graphIndexId;
                } catch (RuntimeException e) {
                    log.error("Filtering key {} due to exception", k, e);
                    return false;
                }
            });
        } else {
            return buffer -> !IDManager.VertexIDType.Invisible.is(idManager.getKeyID(buffer));
        }
    }

    @Override
    public IndexRemoveJob clone() {
        return new IndexRemoveJob(this);
    }
}
