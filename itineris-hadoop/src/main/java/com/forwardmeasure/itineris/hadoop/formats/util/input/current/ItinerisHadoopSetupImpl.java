// Copyright 2017 JanusGraph Authors
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

package com.forwardmeasure.itineris.hadoop.formats.util.input.current;

import com.google.common.base.Preconditions;
import org.apache.hadoop.conf.Configuration;
import org.apache.tinkerpop.gremlin.structure.Direction;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.diskstorage.StaticBuffer;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.SliceQuery;
import com.forwardmeasure.itineris.diskstorage.util.StaticArrayBuffer;
import com.forwardmeasure.itineris.graphdb.database.RelationReader;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.idmanagement.IDManager;
import com.forwardmeasure.itineris.graphdb.internal.ItinerisSchemaCategory;
import com.forwardmeasure.itineris.graphdb.query.QueryUtil;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionCategory;
import com.forwardmeasure.itineris.graphdb.types.TypeDefinitionMap;
import com.forwardmeasure.itineris.graphdb.types.TypeInspector;
import com.forwardmeasure.itineris.graphdb.types.system.BaseKey;
import com.forwardmeasure.itineris.graphdb.types.system.BaseLabel;
import com.forwardmeasure.itineris.graphdb.types.vertices.ItinerisSchemaVertex;
import com.forwardmeasure.itineris.hadoop.config.ItinerisHadoopConfiguration;
import com.forwardmeasure.itineris.hadoop.config.ModifiableHadoopConfiguration;
import com.forwardmeasure.itineris.hadoop.formats.util.input.ItinerisHadoopSetup;
import com.forwardmeasure.itineris.hadoop.formats.util.input.SystemTypeInspector;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class ItinerisHadoopSetupImpl implements ItinerisHadoopSetup {

    private static final StaticBuffer DEFAULT_COLUMN = StaticArrayBuffer.of(new byte[0]);
    public static final SliceQuery DEFAULT_SLICE_QUERY = new SliceQuery(DEFAULT_COLUMN, DEFAULT_COLUMN);

    private final ModifiableHadoopConfiguration scanConf;
    private final StandardItineris graph;
    private final StandardItinerisTx tx;

    public ItinerisHadoopSetupImpl(final Configuration config) {
        scanConf = ModifiableHadoopConfiguration.of(ItinerisHadoopConfiguration.MAPRED_NS, config);
        BasicConfiguration bc = scanConf.getItinerisConf();
        graph = (StandardItineris) ItinerisFactory.open(bc);
        tx = (StandardItinerisTx)graph.buildTransaction().readOnly().vertexCacheSize(200).start();
    }

    @Override
    public TypeInspector getTypeInspector() {
        //Pre-load schema
        for (ItinerisSchemaCategory sc : ItinerisSchemaCategory.values()) {
            for (ItinerisVertex k : QueryUtil.getVertices(tx, BaseKey.SchemaCategory, sc)) {
                assert k instanceof ItinerisSchemaVertex;
                ItinerisSchemaVertex s = (ItinerisSchemaVertex)k;
                if (sc.hasName()) {
                    String name = s.name();
                    Preconditions.checkNotNull(name);
                }
                TypeDefinitionMap dm = s.getDefinition();
                Preconditions.checkNotNull(dm);
                s.getRelated(TypeDefinitionCategory.TYPE_MODIFIER, Direction.OUT);
                s.getRelated(TypeDefinitionCategory.TYPE_MODIFIER, Direction.IN);
            }
        }
        return tx;
    }

    @Override
    public SystemTypeInspector getSystemTypeInspector() {
        return new SystemTypeInspector() {
            @Override
            public boolean isSystemType(long typeId) {
                return IDManager.isSystemRelationTypeId(typeId);
            }

            @Override
            public boolean isVertexExistsSystemType(long typeId) {
                return typeId == BaseKey.VertexExists.longId();
            }

            @Override
            public boolean isVertexLabelSystemType(long typeId) {
                return typeId == BaseLabel.VertexLabelEdge.longId();
            }

            @Override
            public boolean isTypeSystemType(long typeId) {
                return typeId == BaseKey.SchemaCategory.longId() ||
                        typeId == BaseKey.SchemaDefinitionProperty.longId() ||
                        typeId == BaseKey.SchemaDefinitionDesc.longId() ||
                        typeId == BaseKey.SchemaName.longId() ||
                        typeId == BaseLabel.SchemaDefinitionEdge.longId();
            }
        };
    }

    @Override
    public IDManager getIDManager() {
        return graph.getIDManager();
    }

    @Override
    public RelationReader getRelationReader() {
        return graph.getEdgeSerializer();
    }

    @Override
    public void close() {
        tx.rollback();
        graph.close();
    }

    @Override
    public boolean getFilterPartitionedVertices() {
        return scanConf.get(ItinerisHadoopConfiguration.FILTER_PARTITIONED_VERTICES, true);
    }
}
