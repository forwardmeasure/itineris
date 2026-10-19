// Copyright 2021 JanusGraph Authors
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

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.forwardmeasure.itineris.graphdb.grpc.types.ItinerisContext;

import java.util.List;

public class ItinerisManagerClient {

    private final ItinerisManagerServiceGrpc.ItinerisManagerServiceBlockingStub blockingStub;

    public ItinerisManagerClient(Channel channel) {
        blockingStub = ItinerisManagerServiceGrpc.newBlockingStub(channel);
    }

    public ItinerisManagerClient(String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
            .usePlaintext()
            .build();
        blockingStub = ItinerisManagerServiceGrpc.newBlockingStub(channel);
    }

    public ItinerisContext getContextByGraphName(String name) {
        GetItinerisContextByGraphNameRequest request = GetItinerisContextByGraphNameRequest.newBuilder().setGraphName(name).build();
        GetItinerisContextByGraphNameResponse response = blockingStub.getItinerisContextByGraphName(request);
        return response.getContext();
    }

    public List<ItinerisContext> getContexts() {
        GetItinerisContextsRequest request = GetItinerisContextsRequest.newBuilder().build();
        GetItinerisContextsResponse janusGraphContexts = blockingStub.getItinerisContexts(request);
        return janusGraphContexts.getContextsList();
    }

    public String getTinkerPopVersion() {
        GetVersionRequest request = GetVersionRequest.newBuilder().build();
        GetVersionResponse response = blockingStub.getVersion(request);
        return response.getTinkerpopVersion();
    }

    public String getItinerisVersion() {
        GetVersionRequest request = GetVersionRequest.newBuilder().build();
        GetVersionResponse response = blockingStub.getVersion(request);
        return response.getItinerisVersion();
    }
}
