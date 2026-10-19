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

package com.forwardmeasure.itineris.graphdb.server;

import com.jcabi.manifests.Manifests;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.tinkerpop.gremlin.server.GraphManager;
import org.apache.tinkerpop.gremlin.server.GremlinServer;
import org.apache.tinkerpop.gremlin.server.util.ServerGremlinExecutor;
import com.forwardmeasure.itineris.graphdb.grpc.ItinerisContextHandler;
import com.forwardmeasure.itineris.graphdb.grpc.ItinerisManagerServiceImpl;
import com.forwardmeasure.itineris.graphdb.grpc.schema.SchemaManagerImpl;
import com.forwardmeasure.itineris.graphdb.management.ItinerisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ItinerisServer {
    private static final Logger logger = LoggerFactory.getLogger(ItinerisServer.class);
    private GremlinServer gremlinServer = null;
    private ItinerisSettings janusGraphSettings = null;
    private final String confPath;
    private CompletableFuture<Void> serverStarted = null;
    private CompletableFuture<Void> serverStopped = null;
    private Server grpcServer = null;

    public static final String MANIFEST_ITINERIS_VERSION_ATTRIBUTE = "itinerisVersion";
    public static final String MANIFEST_TINKERPOP_VERSION_ATTRIBUTE = "tinkerpopVersion";

    public ItinerisServer(final String file) {
        confPath = file;
    }

    public static void main(final String[] args) {
        printHeader();
        final String file = (args.length > 0) ? args[0] : "conf/itineris-server.yaml";
        ItinerisServer janusGraphServer = new ItinerisServer(file);
        janusGraphServer.start().exceptionally(t -> {
            logger.error("Itineris Server was unable to start and will now begin shutdown", t);
            janusGraphServer.stop().join();
            return null;
        }).join();
    }

    public GremlinServer getGremlinServer() {
        return gremlinServer;
    }

    public ItinerisSettings getItinerisSettings() {
        return janusGraphSettings;
    }

    private Server createGrpcServer(ItinerisSettings janusGraphSettings, GraphManager graphManager) {
        ItinerisContextHandler janusGraphContextHandler = new ItinerisContextHandler(graphManager);
        return ServerBuilder
            .forPort(janusGraphSettings.getGrpcServer().getPort())
            .addService(new ItinerisManagerServiceImpl(janusGraphContextHandler))
            .addService(new SchemaManagerImpl(janusGraphContextHandler))
            .build();
    }

    public synchronized CompletableFuture<Void> start() {
        if (serverStarted != null) {
            return serverStarted;
        }
        serverStarted = new CompletableFuture<>();
        try {
            logger.info("Configuring Itineris Server from {}", confPath);
            janusGraphSettings = ItinerisSettings.read(confPath);
            gremlinServer = new GremlinServer(janusGraphSettings);
            CompletableFuture<Void> grpcServerFuture = CompletableFuture.completedFuture(null);
            if (janusGraphSettings.getGrpcServer().isEnabled()) {
                grpcServerFuture = CompletableFuture.runAsync(() -> {
                    GraphManager graphManager = gremlinServer.getServerGremlinExecutor().getGraphManager();
                    grpcServer = createGrpcServer(janusGraphSettings, graphManager);
                    try {
                        grpcServer.start();
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            CompletableFuture<Void> gremlinServerFuture = gremlinServer.start()
                .thenAcceptAsync(ItinerisServer::configure);
            serverStarted = CompletableFuture.allOf(gremlinServerFuture, grpcServerFuture);
        } catch (Exception ex) {
            serverStarted.completeExceptionally(ex);
        }
        return serverStarted;
    }

    private static void configure(ServerGremlinExecutor serverGremlinExecutor) {
        GraphManager graphManager = serverGremlinExecutor.getGraphManager();
        if (!(graphManager instanceof ItinerisManager)){
            return;
        }
        ((ItinerisManager) graphManager).configureGremlinExecutor(serverGremlinExecutor.getGremlinExecutor());
    }

    public synchronized CompletableFuture<Void> stop() {
        if (gremlinServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (serverStopped != null) {
            return serverStopped;
        }
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
        serverStopped = gremlinServer.stop();
        return serverStopped;
    }

    private static String getHeader() {
        return "                                                                      " + System.lineSeparator() +
            "   mmm                                mmm                       #     " + System.lineSeparator() +
            "     #   mmm   m mm   m   m   mmm   m\"   \"  m mm   mmm   mmmm   # mm  " + System.lineSeparator() +
            "     #  \"   #  #\"  #  #   #  #   \"  #   mm  #\"  \" \"   #  #\" \"#  #\"  # " + System.lineSeparator() +
            "     #  m\"\"\"#  #   #  #   #   \"\"\"m  #    #  #     m\"\"\"#  #   #  #   # " + System.lineSeparator() +
            " \"mmm\"  \"mm\"#  #   #  \"mm\"#  \"mmm\"   \"mmm\"  #     \"mm\"#  ##m#\"  #   # " + System.lineSeparator() +
            "                                                         #            " + System.lineSeparator() +
            "                                                         \"            " + System.lineSeparator();
    }

    private static void printHeader() {
        logger.info(getHeader());
        logger.info("Itineris Version: {}", Manifests.read(MANIFEST_ITINERIS_VERSION_ATTRIBUTE));
        logger.info("TinkerPop Version: {}", Manifests.read(MANIFEST_TINKERPOP_VERSION_ATTRIBUTE));
    }
}
