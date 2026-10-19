// Copyright 2023 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.fetcher;

import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.core.ItinerisMultiVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.ItinerisTraversalUtil;
import com.forwardmeasure.itineris.util.datastructures.ExceptionUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HasStepBatchFetcher extends MultiQueriableStepBatchFetcher<Boolean>{

    private final List<HasContainer> idHasContainers;
    private final List<HasContainer> labelHasContainers;
    private final List<HasContainer> propertyHasContainers;

    private final FetchQueryBuildFunction labelQueryBuilderFunction;
    private final FetchQueryBuildFunction propertiesQueryBuilderFunction;

    public HasStepBatchFetcher(List<HasContainer> idHasContainers,
                               List<HasContainer> labelHasContainers,
                               List<HasContainer> propertyHasContainers,
                               int batchSize,
                               FetchQueryBuildFunction labelQueryBuilderFunction,
                               FetchQueryBuildFunction propertiesQueryBuilderFunction) {
        super(batchSize);
        this.idHasContainers = idHasContainers;
        this.labelHasContainers = labelHasContainers;
        this.propertyHasContainers = propertyHasContainers;
        this.labelQueryBuilderFunction = labelQueryBuilderFunction;
        this.propertiesQueryBuilderFunction = propertiesQueryBuilderFunction;
    }

    @Override
    protected Map<ItinerisVertex, Boolean> makeQueryAndExecute(ItinerisMultiVertexQuery multiQuery) {
        throw new IllegalStateException("makeQueryAndExecute method for HasStepBatchFetcher.java is unimplemented.");
    }

    @Override
    protected Map<ItinerisVertex, Boolean> prefetchNextBatch(final Traversal.Admin<?, ?> traversal, final ItinerisVertex requiredFetchVertex){

        Collection<ItinerisVertex> verticesBatch = nextBatch();
        Map<ItinerisVertex, Boolean> result = new HashMap<>(verticesBatch.size());
        Collection<ItinerisVertex> verticesToPrefetchLabels = toIdPassedBatch(verticesBatch, result);

        ItinerisVertex currentRequiredVertex = requiredFetchVertex;
        if(result.containsKey(currentRequiredVertex)){
            currentRequiredVertex = null;
        } else if(!HasContainer.testAll(currentRequiredVertex, idHasContainers)){
            result.put(currentRequiredVertex, false);
            currentRequiredVertex = null;
        }

        if(verticesToPrefetchLabels.isEmpty() && currentRequiredVertex == null){
            return result;
        }

        final ItinerisTransaction tx = ItinerisTraversalUtil.getTx(traversal);

        final Collection<ItinerisVertex> verticesToPrefetchProperties;
        if(labelHasContainers.isEmpty()){
            verticesToPrefetchProperties = verticesToPrefetchLabels;
        } else {
            prefetchNextLabelsBatch(tx, verticesToPrefetchLabels, currentRequiredVertex);
            verticesToPrefetchProperties = new ArrayList<>(verticesToPrefetchLabels.size());
            for(ItinerisVertex preFetchedVertex : verticesToPrefetchLabels){
                if (HasContainer.testAll(preFetchedVertex, labelHasContainers)){
                    verticesToPrefetchProperties.add(preFetchedVertex);
                } else {
                    result.put(preFetchedVertex, false);
                }
            }
            if(currentRequiredVertex !=null){
                if(result.containsKey(currentRequiredVertex)){
                    currentRequiredVertex = null;
                } else if(!HasContainer.testAll(currentRequiredVertex, labelHasContainers)){
                    result.put(currentRequiredVertex, false);
                    currentRequiredVertex = null;
                }
            }
        }

        if(propertyHasContainers.isEmpty()){
            for(ItinerisVertex passedVertex : verticesToPrefetchProperties){
                result.put(passedVertex, true);
            }
            if(currentRequiredVertex !=null){
                result.put(currentRequiredVertex, true);
            }
        } else if(!verticesToPrefetchProperties.isEmpty() || currentRequiredVertex != null){
            prefetchNextPropertiesBatch(tx, verticesToPrefetchProperties, currentRequiredVertex);
            for(ItinerisVertex preFetchedVertex : verticesToPrefetchProperties){
                result.put(preFetchedVertex, HasContainer.testAll(preFetchedVertex, propertyHasContainers));
            }
            if(currentRequiredVertex !=null && !result.containsKey(currentRequiredVertex)){
                result.put(currentRequiredVertex, HasContainer.testAll(currentRequiredVertex, propertyHasContainers));
            }
        }

        return result;
    }


    private void prefetchNextLabelsBatch(final ItinerisTransaction tx, Collection<ItinerisVertex> verticesToPrefetch, ItinerisVertex requiredVertex){

        final ItinerisMultiVertexQuery multiQuery = tx.multiQuery(verticesToPrefetch);
        if(requiredVertex != null){
            multiQuery.addVertex(requiredVertex);
        }

        try {
            labelQueryBuilderFunction.makeQuery(multiQuery).vertices();
        } catch (ItinerisException janusGraphException) {
            throw ExceptionUtil.convertIfInterrupted(janusGraphException);
        }
    }

    private void prefetchNextPropertiesBatch(final ItinerisTransaction tx, Collection<ItinerisVertex> verticesToPrefetch, ItinerisVertex requiredVertex){

        final ItinerisMultiVertexQuery multiQuery = tx.multiQuery(verticesToPrefetch);
        if(requiredVertex != null){
            multiQuery.addVertex(requiredVertex);
        }

        try {
            propertiesQueryBuilderFunction.makeQuery(multiQuery).preFetch();
        } catch (ItinerisException janusGraphException) {
            throw ExceptionUtil.convertIfInterrupted(janusGraphException);
        }
    }

    /**
     * Returns vertices which passed id has containers. Vertices which didn't pass are added to `result` with `false`.
     */
    private Collection<ItinerisVertex> toIdPassedBatch(Collection<ItinerisVertex> verticesBatch,
                                                         Map<ItinerisVertex, Boolean> result){
        if(idHasContainers.isEmpty()){
            return verticesBatch;
        }

        List<ItinerisVertex> idsPassedBatch = new ArrayList<>(verticesBatch.size());
        for(ItinerisVertex vertexToPrefetch : verticesBatch){
            if(HasContainer.testAll(vertexToPrefetch, idHasContainers)){
                idsPassedBatch.add(vertexToPrefetch);
            } else {
                result.put(vertexToPrefetch, false);
            }
        }

        return idsPassedBatch;
    }
}
