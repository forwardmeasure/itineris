// Copyright 2023 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step;

import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.Profiling;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.LabelStep;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.core.BaseVertexQuery;
import com.forwardmeasure.itineris.graphdb.query.profile.QueryProfiler;
import com.forwardmeasure.itineris.graphdb.query.vertex.BasicVertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.query.vertex.BasicVertexCentricQueryUtil;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.fetcher.LabelStepBatchFetcher;
import com.forwardmeasure.itineris.graphdb.tinkerpop.profile.TP3ProfileWrapper;
import com.forwardmeasure.itineris.graphdb.util.CopyStepUtil;
import com.forwardmeasure.itineris.graphdb.util.ItinerisTraverserUtil;

/**
 * This class extends the default TinkerPop's {@link  LabelStep} and adds vertices multi-query optimization to this step.
 * <p>
 * Before this step is evaluated it usually receives multiple future vertices which might be processed next with this step.
 * This step stores all these vertices which might be needed later for evaluation and whenever this step receives the
 * vertex for evaluation which wasn't preFetched previously it sends multi-query for a batch of vertices to fetch their
 * labels.
 * <p>
 * This step optimizes only access to Vertex labels and skips optimization for any other Element.
 */
public class ItinerisLabelStep<S extends Element> extends LabelStep<S> implements Profiling, MultiQueriable<S,String> {

    private boolean useMultiQuery = false;
    private QueryProfiler queryProfiler = QueryProfiler.NO_OP;
    private int batchSize = Integer.MAX_VALUE;
    private LabelStepBatchFetcher labelStepBatchFetcher;

    public ItinerisLabelStep(LabelStep<S> originalStep){
        super(originalStep.getTraversal());
        CopyStepUtil.copyAbstractStepModifiableFields(originalStep, this);

        if (originalStep instanceof ItinerisLabelStep) {
            ItinerisLabelStep originalItinerisLabelStep = (ItinerisLabelStep) originalStep;
            setBatchSize(originalItinerisLabelStep.batchSize);
            setUseMultiQuery(originalItinerisLabelStep.useMultiQuery);
        }
    }

    @Override
    protected String map(final Traverser.Admin<S> traverser) {
        if (useMultiQuery && traverser.get() instanceof Vertex) {
            return labelStepBatchFetcher.fetchData(getTraversal(), (Vertex) traverser.get(), ItinerisTraverserUtil.getLoops(traverser));
        }
        return super.map(traverser);
    }

    @Override
    public void setUseMultiQuery(boolean useMultiQuery) {
        this.useMultiQuery = useMultiQuery;
        if(this.useMultiQuery && labelStepBatchFetcher == null){
            labelStepBatchFetcher = new LabelStepBatchFetcher(this::makeLabelsQuery, batchSize);
        }
    }

    private <Q extends BaseVertexQuery> Q makeLabelsQuery(Q query) {
        return (Q) BasicVertexCentricQueryUtil.withLabelVertices((BasicVertexCentricQueryBuilder) query)
            .profiler(queryProfiler);
    }

    @Override
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        if(labelStepBatchFetcher != null){
            labelStepBatchFetcher.setBatchSize(batchSize);
        }
    }

    @Override
    public void registerFirstNewLoopFutureVertexForPrefetching(Vertex futureVertex, int futureVertexTraverserLoop) {
        if(useMultiQuery){
            labelStepBatchFetcher.registerFirstNewLoopFutureVertexForPrefetching(futureVertex);
        }
    }

    @Override
    public void registerSameLoopFutureVertexForPrefetching(Vertex futureVertex, int futureVertexTraverserLoop) {
        if(useMultiQuery){
            labelStepBatchFetcher.registerCurrentLoopFutureVertexForPrefetching(futureVertex, futureVertexTraverserLoop);
        }
    }

    @Override
    public void registerNextLoopFutureVertexForPrefetching(Vertex futureVertex, int futureVertexTraverserLoop) {
        if(useMultiQuery){
            labelStepBatchFetcher.registerNextLoopFutureVertexForPrefetching(futureVertex, futureVertexTraverserLoop);
        }
    }

    @Override
    public void setMetrics(MutableMetrics metrics) {
        queryProfiler = new TP3ProfileWrapper(metrics);
    }
}
