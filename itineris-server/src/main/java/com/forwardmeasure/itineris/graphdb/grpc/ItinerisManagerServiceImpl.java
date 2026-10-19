// Copyright 2020 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.grpc;

import com.jcabi.manifests.Manifests;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import com.forwardmeasure.itineris.graphdb.grpc.types.ItinerisContext;
import com.forwardmeasure.itineris.graphdb.server.ItinerisServer;

public class ItinerisManagerServiceImpl extends ItinerisManagerServiceGrpc.ItinerisManagerServiceImplBase {
    private final ItinerisContextHandler contextHandler;

    public ItinerisManagerServiceImpl(ItinerisContextHandler contextHandler) {
        this.contextHandler = contextHandler;
    }

    @Override
    public void getItinerisContexts(GetItinerisContextsRequest request, StreamObserver<GetItinerisContextsResponse> responseObserver) {
        GetItinerisContextsResponse.Builder response = GetItinerisContextsResponse.newBuilder();

        for (ItinerisContext context : contextHandler.getContexts()) {
            response.addContexts(context);
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void getItinerisContextByGraphName(GetItinerisContextByGraphNameRequest request,
                                                StreamObserver<GetItinerisContextByGraphNameResponse> responseObserver) {
        final String graphName = request.getGraphName();
        if (graphName.isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("graphName is required").asException());
            return;
        }
        ItinerisContext context = contextHandler.getContextByGraphName(graphName);
        if (context == null) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription("ItinerisContext wasn't found for graphName").asException());
        }
        responseObserver.onNext(GetItinerisContextByGraphNameResponse.newBuilder()
            .setContext(context)
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getVersion(GetVersionRequest request, StreamObserver<GetVersionResponse> responseObserver) {
        String tinkerPopVersion = "debug-tp";
        String janusGraphVersion = "debug-jg";
        if (Manifests.exists(ItinerisServer.MANIFEST_TINKERPOP_VERSION_ATTRIBUTE)){
            tinkerPopVersion = Manifests.read(ItinerisServer.MANIFEST_TINKERPOP_VERSION_ATTRIBUTE);
        }
        if (Manifests.exists(ItinerisServer.MANIFEST_ITINERIS_VERSION_ATTRIBUTE)){
            janusGraphVersion = Manifests.read(ItinerisServer.MANIFEST_ITINERIS_VERSION_ATTRIBUTE);
        }
        responseObserver.onNext(GetVersionResponse.newBuilder()
            .setTinkerpopVersion(tinkerPopVersion)
            .setItinerisVersion(janusGraphVersion)
            .build());
        responseObserver.onCompleted();
    }
}
