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

package com.forwardmeasure.itineris.graphdb.grpc;

import org.apache.tinkerpop.gremlin.server.GraphManager;
import org.apache.tinkerpop.gremlin.structure.Graph;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.graphdb.grpc.schema.SchemaManagerProvider;
import com.forwardmeasure.itineris.graphdb.grpc.types.ItinerisContext;

import java.util.ArrayList;

public class ItinerisContextHandler {
    private final GraphManager graphManager;

    public ItinerisContextHandler(GraphManager graphManager) {
        this.graphManager = graphManager;
    }

    public Iterable<ItinerisContext> getContexts() {
        ArrayList<ItinerisContext> contexts = new ArrayList<>();
        for (String graphName : graphManager.getGraphNames()) {
            Graph graph = graphManager.getGraph(graphName);
            if (!(graph instanceof Itineris)) {
                continue;
            }
            contexts.add(ItinerisContext.newBuilder().setGraphName(graphName).build());
        }
        return contexts;
    }

    public ItinerisContext getContextByGraphName(String graphName) {
        Graph graph = graphManager.getGraph(graphName);
        if (!(graph instanceof Itineris)) {
            return null;
        }
        return ItinerisContext.newBuilder().setGraphName(graphName).build();
    }

    public SchemaManagerProvider getSchemaManagerProviderByContext(ItinerisContext context) {
        Graph graph = graphManager.getGraph(context.getGraphName());
        if (!(graph instanceof Itineris)) {
            return null;
        }
        return new SchemaManagerProvider((Itineris) graph);
    }
}
