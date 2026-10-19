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

package com.forwardmeasure.itineris.graphdb.grpc.schema;

import io.grpc.Status;
import io.grpc.StatusException;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.graphdb.grpc.schema.util.GrpcUtils;
import com.forwardmeasure.itineris.graphdb.grpc.types.EdgeLabel;
import com.forwardmeasure.itineris.graphdb.grpc.types.VertexCompositeGraphIndex;
import com.forwardmeasure.itineris.graphdb.grpc.types.VertexLabel;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class SchemaManagerProvider {
    private final ItinerisManagement management;

    public SchemaManagerProvider(Itineris graph) {
        this.management = graph.openManagement();
    }

    public VertexLabel getVertexLabelByName(String name) throws StatusException {
        com.forwardmeasure.itineris.core.VertexLabel vertexLabel = management.getVertexLabel(name);
        if (vertexLabel == null) {
            throw Status.NOT_FOUND.withDescription("No vertexLabel found with name: " + name).asException();
        }

        return GrpcUtils.createVertexLabelProto(vertexLabel);
    }

    public List<VertexLabel> getVertexLabels() {
        return StreamSupport
            .stream(management.getVertexLabels().spliterator(), false)
            .map(GrpcUtils::createVertexLabelProto).collect(Collectors.toList());
    }

    public EdgeLabel getEdgeLabelByName(String name) throws StatusException {
        com.forwardmeasure.itineris.core.EdgeLabel edgeLabel = management.getEdgeLabel(name);
        if (edgeLabel == null) {
            throw Status.NOT_FOUND.withDescription("No edgeLabel found with name: " + name).asException();
        }

        return GrpcUtils.createEdgeLabelProto(edgeLabel);
    }

    public List<EdgeLabel> getEdgeLabels() {
        return StreamSupport
            .stream(management.getRelationTypes(com.forwardmeasure.itineris.core.EdgeLabel.class).spliterator(), false)
            .map(GrpcUtils::createEdgeLabelProto).collect(Collectors.toList());
    }

    public VertexCompositeGraphIndex getVertexCompositeGraphIndexByName(String indexName) throws StatusException {
        ItinerisIndex graphIndex = management.getGraphIndex(indexName);
        if (graphIndex == null) {
            throw Status.NOT_FOUND
                .withDescription("No composite graph index found with name: " + indexName).asException();
        }
        if (!graphIndex.isCompositeIndex()) {
            throw Status.FAILED_PRECONDITION
                .withDescription("Graph index isn't a composite index with name: " + indexName).asException();
        }
        if (!Vertex.class.isAssignableFrom(graphIndex.getIndexedElement())) {
            throw Status.FAILED_PRECONDITION
                .withDescription("Composite graph index isn't assignable to a vertex with name: " + indexName)
                .asException();
        }
        return GrpcUtils.createVertexCompositeGraphIndex(graphIndex);
    }

    public List<VertexCompositeGraphIndex> getVertexCompositeGraphIndices() {
        Iterable<ItinerisIndex> indices = management.getGraphIndexes(Vertex.class);
        return StreamSupport
            .stream(indices.spliterator(), false)
            .filter(ItinerisIndex::isCompositeIndex)
            .map(GrpcUtils::createVertexCompositeGraphIndex)
            .collect(Collectors.toList());
    }
}
