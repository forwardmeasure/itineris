// Copyright 2019 Itineris Authors
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


import org.apache.tinkerpop.gremlin.structure.Vertex
import com.forwardmeasure.itineris.core.Itineris
import com.forwardmeasure.itineris.core.ItinerisVertex

ItinerisVertex getOrCreateVertex(FaunusVertex faunusVertex, Itineris graph, TaskInputOutputContext context, Logger log) {
    String uniqueKey = "name"
    Object uniqueValue = faunusVertex.value(uniqueKey)
    Vertex janusgraphVertex
    if (null == uniqueValue) {
        throw new RuntimeException("The provided Faunus vertex does not have a property for the unique key: " + faunusVertex)
    }
    Iterator<Vertex> itty = graph.query().has(uniqueKey, uniqueValue).vertexStream().iterator()
    if (itty.hasNext()) {
      janusgraphVertex = itty.next()
      if (itty.hasNext()) {
          log.info("The unique key is not unique as more than one vertex with the value {}", uniqueValue)
      }
    } else {
      janusgraphVertex = graph.addVertex(faunusVertex.longId(),faunusVertex.label())
    }
    return janusgraphVertex
}

ItinerisEdge getOrCreateEdge(FaunusEdge faunusEdge, ItinerisVertex inVertex, ItinerisVertex outVertex, Itineris graph, TaskInputOutputContext context, Logger log) {
    final String label = faunusEdge.label()

    log.debug("outVertex:{} label:{} inVertex:{}", outVertex, label, inVertex)

    final Edge janusgraphEdge = !outVertex.out(label).has("id", inVertex.id()).hasNext() ?
        graph.addEdge(null, outVertex, inVertex, label) :
        outVertex.outE(label).as("here").inV().has("id", inVertex.id()).back("here").next()

    return janusgraphEdge
}

void getOrCreateVertexProperty(ItinerisProperty faunusProperty, ItinerisVertex vertex, Itineris graph, TaskInputOutputContext context, Logger log) {

    final com.forwardmeasure.itineris.core.PropertyKey pkey = faunusProperty.propertyKey()
    if (pkey.cardinality().equals(com.forwardmeasure.itineris.core.Cardinality.SINGLE)) {
        vertex.property(pkey.name(), faunusProperty.value())
    } else {
//        Iterator<com.forwardmeasure.itineris.core.ItinerisProperty> itty = vertex.getProperties(pkey.getName()).iterator();
//        if (!itty.hasNext()) {
            vertex.property(pkey.name(), faunusProperty.value())
//        }
    }

    log.debug("Set property {}={} on {}", pkey.name(), faunusProperty.value(), vertex)
}
