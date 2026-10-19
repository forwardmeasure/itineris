// Copyright 2020 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import com.forwardmeasure.itineris.StorageSetup;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.EdgeLabel;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.graphdb.ItinerisBaseTest;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.apache.tinkerpop.gremlin.process.traversal.Order.asc;
import static org.apache.tinkerpop.gremlin.process.traversal.Order.desc;
import static com.forwardmeasure.itineris.graphdb.ItinerisBaseTest.validateConfigOptions;

/**
 * @author Florian Grieskamp (Florian.Grieskamp@gdata.de)
 */
public abstract class OptimizerStrategyTest {
    protected Itineris graph;
    protected GraphTraversalSource g;
    protected ItinerisTransaction tx;
    protected ItinerisManagement mgmt;

    // sample graph parameters
    protected int numV;
    protected int superV;
    protected int sid;

    // sample graph vertices
    protected ItinerisVertex[] sv;
    protected ItinerisVertex[] vs;

    @BeforeEach
    protected void setUp() {
        open();
    }

    protected void open() {
        graph = StorageSetup.getInMemoryGraph();
        g = graph.traversal();
        tx = graph.newTransaction();
        mgmt = graph.openManagement();
    }

    public void open(WriteConfiguration config) {
        graph = ItinerisFactory.open(config);
        g = graph.traversal();
        tx = graph.newTransaction();
        mgmt = graph.openManagement();
    }

    public void clopen(Object... settings) {
        if (settings!=null && settings.length>0) {
            if (graph!=null && graph.isOpen()) {
                graph.close();
            }
            Map<ItinerisBaseTest.TestConfigOption,Object> options = validateConfigOptions(settings);
            ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
            config.set(GraphDatabaseConfiguration.STORAGE_BACKEND,"inmemory");
            for (Map.Entry<ItinerisBaseTest.TestConfigOption,Object> option : options.entrySet()) {
                config.set(option.getKey().option, option.getValue(), option.getKey().umbrella);
            }
            open(config.getConfiguration());
        }
        newTx();
    }

    @AfterEach
    public void tearDown() {
        close();
    }

    public void finishSchema() {
        if (mgmt!=null && mgmt.isOpen())
            mgmt.commit();
        mgmt=graph.openManagement();
        newTx();
        graph.tx().commit();
    }

    public void close() {
        if (mgmt!=null && mgmt.isOpen()) mgmt.rollback();
        if (null != tx && tx.isOpen())
            tx.commit();

        if (null != graph && graph.isOpen())
            graph.close();
    }

    public void newTx() {
        if (null != tx && tx.isOpen())
            tx.commit();
        tx = graph.newTransaction();
    }

    protected void makeSampleGraph() {
        PropertyKey id = mgmt.makePropertyKey("id").cardinality(Cardinality.SINGLE).dataType(Integer.class).make();
        PropertyKey weight = mgmt.makePropertyKey("weight").cardinality(Cardinality.SINGLE).dataType(Integer.class).make();
        PropertyKey uniqueId = mgmt.makePropertyKey("uniqueId").cardinality(Cardinality.SINGLE).dataType(Integer.class).make();

        mgmt.buildIndex("byId", Vertex.class).addKey(id).buildCompositeIndex();
        mgmt.buildIndex("byUniqueId", Vertex.class).addKey(uniqueId).unique().buildCompositeIndex();
        mgmt.buildIndex("byWeight", Vertex.class).addKey(weight).buildCompositeIndex();
        mgmt.buildIndex("byIdWeight", Vertex.class).addKey(id).addKey(weight).buildCompositeIndex();

        EdgeLabel knows = mgmt.makeEdgeLabel("knows").make();
        mgmt.buildEdgeIndex(knows, "byWeightDesc", Direction.OUT, desc, weight);
        mgmt.buildEdgeIndex(knows, "byWeightAsc", Direction.OUT, asc, weight);

        PropertyKey names = mgmt.makePropertyKey("names").cardinality(Cardinality.LIST).dataType(String.class).make();
        mgmt.buildPropertyIndex(names, "namesByWeight", desc, weight);

        finishSchema();

        numV = 100;
        int uid = 0;
        vs = new ItinerisVertex[numV];
        for (int i = 0; i < numV; i++) {
            vs[i] = graph.addVertex("id", i, "weight", i % 5, "uniqueId", uid++);
        }
        superV = 10;
        sid = -1;
        sv = new ItinerisVertex[superV];
        for (int i = 0; i < superV; i++) {
            sv[i] = graph.addVertex("id", sid, "uniqueId", uid++);
            for (int j = 0; j < numV; j++) {
                sv[i].addEdge("knows", vs[j], "weight", j % 5);
                sv[i].property(VertexProperty.Cardinality.list, "names", "n" + j, "weight", j % 5);
            }
        }
    }
}
