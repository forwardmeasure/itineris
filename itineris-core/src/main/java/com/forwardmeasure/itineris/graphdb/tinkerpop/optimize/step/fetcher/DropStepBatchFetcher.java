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

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.fetcher;

import com.forwardmeasure.itineris.core.ItinerisMultiVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;

import java.util.Map;

public class DropStepBatchFetcher extends MultiQueriableStepBatchFetcher<Iterable<ItinerisRelation>>{

    private final FetchQueryBuildFunction fetchQueryBuildFunction;

    public DropStepBatchFetcher(FetchQueryBuildFunction fetchQueryBuildFunction, int batchSize,
                                TriggeredBatchConsumer<ItinerisVertex> batchConsumerBeforeQuery) {
        super(batchSize, batchConsumerBeforeQuery);
        this.fetchQueryBuildFunction = fetchQueryBuildFunction;
    }

    @Override
    protected Map<ItinerisVertex, Iterable<ItinerisRelation>> makeQueryAndExecute(ItinerisMultiVertexQuery multiQuery) {
        multiQuery = fetchQueryBuildFunction.makeQuery(multiQuery);
        return multiQuery.drop();
    }
}
