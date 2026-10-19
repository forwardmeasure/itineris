// Copyright 2024 JanusGraph Authors
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
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.DropStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.event.CallbackRegistry;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.event.Event;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.event.EventCallback;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.event.EventUtil;
import org.apache.tinkerpop.gremlin.process.traversal.strategy.decoration.EventStrategy;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.core.BaseVertexQuery;
import com.forwardmeasure.itineris.graphdb.query.profile.QueryProfiler;
import com.forwardmeasure.itineris.graphdb.query.vertex.BasicVertexCentricQueryBuilder;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.fetcher.DropStepBatchFetcher;
import com.forwardmeasure.itineris.graphdb.tinkerpop.profile.TP3ProfileWrapper;
import com.forwardmeasure.itineris.graphdb.util.CopyStepUtil;
import com.forwardmeasure.itineris.graphdb.util.ItinerisTraverserUtil;

import java.util.List;

/**
 * This class extends the default TinkerPop's {@link  DropStep} and adds vertices multi-query optimization to this step.
 * <p>
 * Before this step is evaluated it usually receives multiple future vertices which might be processed next with this step.
 * This step stores all these vertices which might be needed later for evaluation and whenever this step receives the
 * vertex for evaluation which wasn't evaluated previously it sends multi-query for a batch of vertices to drop them.
 * <p>
 * This step optimizes only drop of Vertices and skips optimization for any other Element.
 */
public class ItinerisDropStep<S extends Element> extends DropStep<S> implements Profiling, MultiQueriable<S,S> {

    private boolean useMultiQuery = false;
    private QueryProfiler queryProfiler = QueryProfiler.NO_OP;
    private int batchSize = Integer.MAX_VALUE;
    private DropStepBatchFetcher dropStepBatchFetcher;

    public ItinerisDropStep(DropStep<S> originalStep){
        super(originalStep.getTraversal());
        CopyStepUtil.copyAbstractStepModifiableFields(originalStep, this);

        CallbackRegistry<Event> callbackRegistry = getMutatingCallbackRegistry();
        for(EventCallback<Event> callback : originalStep.getMutatingCallbackRegistry().getCallbacks()){
            callbackRegistry.addCallback(callback);
        }

        if (originalStep instanceof ItinerisDropStep) {
            ItinerisDropStep originalItinerisLabelStep = (ItinerisDropStep) originalStep;
            setBatchSize(originalItinerisLabelStep.batchSize);
            setUseMultiQuery(originalItinerisLabelStep.useMultiQuery);
        }
    }

    @Override
    protected boolean filter(final Traverser.Admin<S> traverser) {
        if (useMultiQuery && traverser.get() instanceof Vertex) {
            dropStepBatchFetcher.fetchData(getTraversal(), (Vertex) traverser.get(), ItinerisTraverserUtil.getLoops(traverser));
            return false;
        } else {
            return super.filter(traverser);
        }
    }

    @Override
    public void setUseMultiQuery(boolean useMultiQuery) {
        this.useMultiQuery = useMultiQuery;
        if(this.useMultiQuery && dropStepBatchFetcher == null){
            dropStepBatchFetcher = new DropStepBatchFetcher(this::makeLabelsQuery, batchSize, (batchVertices, requiredVertex) -> {
                List<EventCallback<Event>> callbacksForRemovalEvents = getMutatingCallbackRegistry().getCallbacks();
                if(!callbacksForRemovalEvents.isEmpty()){
                    final EventStrategy eventStrategy = EventUtil.forceGetEventStrategy(traversal);
                    produceRemovedEvent(eventStrategy, callbacksForRemovalEvents, requiredVertex);
                    for(Vertex vertexInBatch : batchVertices){
                        if(vertexInBatch != requiredVertex){
                            produceRemovedEvent(eventStrategy, callbacksForRemovalEvents, vertexInBatch);
                        }
                    }
                }
            });
        }
    }

    private static void produceRemovedEvent(EventStrategy eventStrategy,
                                     List<EventCallback<Event>> callbacksForRemovalEvents,
                                     Vertex vertex){
        final Event removeEvent = new Event.VertexRemovedEvent(eventStrategy.detach(vertex));
        for(EventCallback<Event> callback : callbacksForRemovalEvents){
            callback.accept(removeEvent);
        }
    }

    private <Q extends BaseVertexQuery> Q makeLabelsQuery(Q query) {
        ((BasicVertexCentricQueryBuilder) query).profiler(queryProfiler);
        return query;
    }

    @Override
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
        if(dropStepBatchFetcher != null){
            dropStepBatchFetcher.setBatchSize(batchSize);
        }
    }

    @Override
    public void registerFirstNewLoopFutureVertexForPrefetching(Vertex futureVertex, int futureVertexTraverserLoop) {
        if(useMultiQuery){
            dropStepBatchFetcher.registerFirstNewLoopFutureVertexForPrefetching(futureVertex);
        }
    }

    @Override
    public void registerSameLoopFutureVertexForPrefetching(Vertex futureVertex, int futureVertexTraverserLoop) {
        if(useMultiQuery){
            dropStepBatchFetcher.registerCurrentLoopFutureVertexForPrefetching(futureVertex, futureVertexTraverserLoop);
        }
    }

    @Override
    public void registerNextLoopFutureVertexForPrefetching(Vertex futureVertex, int futureVertexTraverserLoop) {
        if(useMultiQuery){
            dropStepBatchFetcher.registerNextLoopFutureVertexForPrefetching(futureVertex, futureVertexTraverserLoop);
        }
    }

    @Override
    public void setMetrics(MutableMetrics metrics) {
        queryProfiler = new TP3ProfileWrapper(metrics);
    }
}
