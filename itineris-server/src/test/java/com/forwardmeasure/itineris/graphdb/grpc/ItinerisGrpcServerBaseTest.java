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

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.apache.tinkerpop.gremlin.server.GraphManager;
import org.apache.tinkerpop.gremlin.server.Settings;
import org.apache.tinkerpop.gremlin.server.util.DefaultGraphManager;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.VertexLabel;
import com.forwardmeasure.itineris.core.schema.EdgeLabelMaker;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.core.schema.PropertyKeyMaker;
import com.forwardmeasure.itineris.core.schema.VertexLabelMaker;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.graphdb.grpc.schema.SchemaManagerImpl;
import com.forwardmeasure.itineris.graphdb.grpc.schema.util.GrpcUtils;
import com.forwardmeasure.itineris.graphdb.grpc.types.EdgeLabel;
import com.forwardmeasure.itineris.graphdb.grpc.types.EdgeLabelOrBuilder;
import com.forwardmeasure.itineris.graphdb.grpc.types.EdgeProperty;
import com.forwardmeasure.itineris.graphdb.grpc.types.VertexCompositeGraphIndexOrBuilder;
import com.forwardmeasure.itineris.graphdb.grpc.types.VertexLabelOrBuilder;
import com.forwardmeasure.itineris.graphdb.grpc.types.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.javatuples.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public abstract class ItinerisGrpcServerBaseTest {

    protected ItinerisContextHandler contextHandler;
    protected GraphManager graphManager;
    protected ManagedChannel managedChannel;
    protected Server server;

    protected static GraphManager getGraphManager() {
        Settings settings = new Settings();
        HashMap<String, String> map = new HashMap<>();
        map.put("graph", "src/test/resources/itineris-inmemory.properties");
        map.put("graph2", "src/test/resources/itineris-inmemory.properties");
        map.put("tinkergraph", "src/test/resources/tinkergraph.properties");
        settings.graphs = map;
        return new DefaultGraphManager(settings);
    }

    public Object createVertexLabel(String graph, VertexLabelOrBuilder builder) {
        ItinerisManagement management = ((Itineris) graphManager.getGraph(graph)).openManagement();
        VertexLabelMaker vertexLabelMaker = management.makeVertexLabel(builder.getName());
        if (builder.getReadOnly()) {
            vertexLabelMaker.setStatic();
        }
        if (builder.getPartitioned()) {
            vertexLabelMaker.partition();
        }
        VertexLabel vertexLabel = vertexLabelMaker.make();
        for (VertexProperty vertexProperty : builder.getPropertiesList()) {
            PropertyKeyMaker propertyKeyMaker = management.makePropertyKey(vertexProperty.getName());
            PropertyKey propertyKey = propertyKeyMaker
                .cardinality(GrpcUtils.convertGrpcCardinality(vertexProperty.getCardinality()))
                .dataType(GrpcUtils.convertGrpcPropertyDataType(vertexProperty.getDataType()))
                .make();
            management.addProperties(vertexLabel, propertyKey);
        }

        management.commit();
        return vertexLabel.id();
    }

    public Object createVertexCompositeGraphIndex(String graph, VertexCompositeGraphIndexOrBuilder builder) {
        Itineris tx = (Itineris) graphManager.getGraph(graph);
        ManagementSystem management = (ManagementSystem) tx.openManagement();
        ItinerisManagement.IndexBuilder indexBuilder = management.buildIndex(builder.getName(), Vertex.class);
        for (VertexProperty vertexProperty : builder.getKeysList()) {
            PropertyKey propertyKey = management.getPropertyKey(vertexProperty.getName());
            indexBuilder.addKey(propertyKey);
        }

        if (builder.hasIndexOnly()) {
            VertexLabel vertexLabel = management.getVertexLabel(builder.getIndexOnly().getName());
            indexBuilder.indexOnly(vertexLabel);
        }
        if (builder.getUnique()) {
            indexBuilder.unique();
        }

        ItinerisIndex index = indexBuilder.buildCompositeIndex();
        management.commit();
        return index.id();
    }

    public Object createEdgeLabel(String graph, EdgeLabelOrBuilder builder) {
        ItinerisManagement management = ((Itineris) graphManager.getGraph(graph)).openManagement();
        EdgeLabelMaker edgeLabelMaker = management.makeEdgeLabel(builder.getName());
        if (builder.getDirection() == EdgeLabel.Direction.DIRECTION_OUT) {
            edgeLabelMaker.unidirected();
        } else {
            edgeLabelMaker.directed();
        }
        edgeLabelMaker.multiplicity(GrpcUtils.convertGrpcEdgeMultiplicity(builder.getMultiplicity()));
        com.forwardmeasure.itineris.core.EdgeLabel edgeLabel = edgeLabelMaker.make();
        for (EdgeProperty edgeProperty : builder.getPropertiesList()) {
            PropertyKeyMaker propertyKeyMaker = management.makePropertyKey(edgeProperty.getName());
            PropertyKey propertyKey = propertyKeyMaker
                .dataType(GrpcUtils.convertGrpcPropertyDataType(edgeProperty.getDataType()))
                .make();
            management.addProperties(edgeLabel, propertyKey);
        }

        management.commit();
        return edgeLabel.id();
    }

    private static Pair<Server, String> createServer(ItinerisContextHandler contextHandler) throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(new ItinerisManagerServiceImpl(contextHandler))
            .addService(new SchemaManagerImpl(contextHandler))
            .build().start();
        return new Pair<>(server, serverName);
    }

    @BeforeEach
    public void startServer() throws IOException {
        graphManager = getGraphManager();
        contextHandler = new ItinerisContextHandler(graphManager);
        Pair<Server, String> server = createServer(contextHandler);
        managedChannel = InProcessChannelBuilder.forName(server.getValue1()).directExecutor().build();
        this.server = server.getValue0();
    }

    @AfterEach
    public void stopServer() throws IOException {
        managedChannel.shutdown();
        server.shutdown();
        try {
            managedChannel.awaitTermination(1, TimeUnit.SECONDS);
            server.awaitTermination();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
