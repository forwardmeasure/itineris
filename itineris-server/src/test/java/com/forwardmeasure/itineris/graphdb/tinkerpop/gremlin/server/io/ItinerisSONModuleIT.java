// Copyright 2019 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.tinkerpop.gremlin.server.io;

import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.util.ser.GraphSONMessageSerializerV3;
import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONMapper;
import com.forwardmeasure.itineris.graphdb.tinkerpop.ItinerisIoRegistry;
import com.forwardmeasure.itineris.graphdb.tinkerpop.ItinerisSerializerBaseIT;

public class ItinerisSONModuleIT extends ItinerisSerializerBaseIT {
    @Override
    protected GraphTraversalSource traversal() {
        Cluster cluster = Cluster.build("localhost").port(8182)
            .serializer(new GraphSONMessageSerializerV3(GraphSONMapper.build().addRegistry(ItinerisIoRegistry.instance())))
            .create();
        return AnonymousTraversalSource.traversal().withRemote(DriverRemoteConnection.using(cluster));
    }
}
