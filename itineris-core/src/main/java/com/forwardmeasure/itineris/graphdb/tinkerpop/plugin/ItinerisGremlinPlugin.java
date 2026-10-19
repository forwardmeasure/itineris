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

package com.forwardmeasure.itineris.graphdb.tinkerpop.plugin;

import org.apache.tinkerpop.gremlin.jsr223.Customizer;
import org.apache.tinkerpop.gremlin.jsr223.DefaultImportCustomizer;
import com.forwardmeasure.itineris.core.BaseVertexQuery;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.ConfiguredGraphFactory;
import com.forwardmeasure.itineris.core.EdgeLabel;
import com.forwardmeasure.itineris.core.Idfiable;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisComputer;
import com.forwardmeasure.itineris.core.ItinerisEdge;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.core.ItinerisIndexQuery;
import com.forwardmeasure.itineris.core.ItinerisMultiVertexQuery;
import com.forwardmeasure.itineris.core.ItinerisProperty;
import com.forwardmeasure.itineris.core.ItinerisQuery;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.core.ItinerisVertexQuery;
import com.forwardmeasure.itineris.core.Multiplicity;
import com.forwardmeasure.itineris.core.Namifiable;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.QueryDescription;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.Transaction;
import com.forwardmeasure.itineris.core.TransactionBuilder;
import com.forwardmeasure.itineris.core.VertexLabel;
import com.forwardmeasure.itineris.core.VertexList;
import com.forwardmeasure.itineris.core.attribute.AttributeSerializer;
import com.forwardmeasure.itineris.core.schema.ConsistencyModifier;
import com.forwardmeasure.itineris.core.schema.DefaultSchemaMaker;
import com.forwardmeasure.itineris.core.schema.EdgeLabelMaker;
import com.forwardmeasure.itineris.core.schema.Index;
import com.forwardmeasure.itineris.core.schema.ItinerisConfiguration;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.core.schema.ItinerisSchemaElement;
import com.forwardmeasure.itineris.core.schema.ItinerisSchemaType;
import com.forwardmeasure.itineris.core.schema.JobStatus;
import com.forwardmeasure.itineris.core.schema.Mapping;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.core.schema.PropertyKeyMaker;
import com.forwardmeasure.itineris.core.schema.RelationTypeIndex;
import com.forwardmeasure.itineris.core.schema.RelationTypeMaker;
import com.forwardmeasure.itineris.core.schema.SchemaAction;
import com.forwardmeasure.itineris.core.schema.SchemaInspector;
import com.forwardmeasure.itineris.core.schema.SchemaManager;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.core.schema.VertexLabelMaker;
import com.forwardmeasure.itineris.example.GraphOfTheGodsFactory;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import com.forwardmeasure.itineris.graphdb.management.ConfigurationManagementGraph;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Marko A. Rodriguez (https://markorodriguez.com)
 */
public class ItinerisGremlinPlugin extends ItinerisGremlinDriverPlugin {

    private static final String NAME = "itineris.imports";

    private static final Set<Class> CLASS_IMPORTS = new LinkedHashSet<>();
    private static final Set<Enum> ENUM_IMPORTS = new LinkedHashSet<>();
    private static final Set<Method> METHOD_IMPORTS = new LinkedHashSet<>();

    static {
        /////////////
        // CLASSES //
        /////////////

        CLASS_IMPORTS.add(BaseVertexQuery.class);
        CLASS_IMPORTS.add(Cardinality.class);
        CLASS_IMPORTS.add(ConfiguredGraphFactory.class);
        CLASS_IMPORTS.add(EdgeLabel.class);
        CLASS_IMPORTS.add(Idfiable.class);
        CLASS_IMPORTS.add(Itineris.class);
        CLASS_IMPORTS.add(ItinerisComputer.class);
        CLASS_IMPORTS.add(ItinerisEdge.class);
        CLASS_IMPORTS.add(ItinerisElement.class);
        CLASS_IMPORTS.add(ItinerisFactory.class);
        CLASS_IMPORTS.add(ItinerisIndexQuery.class);
        CLASS_IMPORTS.add(ItinerisMultiVertexQuery.class);
        CLASS_IMPORTS.add(ItinerisProperty.class);
        CLASS_IMPORTS.add(ItinerisQuery.class);
        CLASS_IMPORTS.add(ItinerisRelation.class);
        CLASS_IMPORTS.add(ItinerisTransaction.class);
        CLASS_IMPORTS.add(ItinerisVertex.class);
        CLASS_IMPORTS.add(ItinerisVertexProperty.class);
        CLASS_IMPORTS.add(ItinerisVertexQuery.class);
        CLASS_IMPORTS.add(Multiplicity.class);
        CLASS_IMPORTS.add(Namifiable.class);
        CLASS_IMPORTS.add(PropertyKey.class);
        CLASS_IMPORTS.add(QueryDescription.class);
        CLASS_IMPORTS.add(RelationType.class);
        CLASS_IMPORTS.add(Transaction.class);
        CLASS_IMPORTS.add(TransactionBuilder.class);
        CLASS_IMPORTS.add(VertexLabel.class);
        CLASS_IMPORTS.add(VertexList.class);

        CLASS_IMPORTS.add(AttributeSerializer.class);

        CLASS_IMPORTS.add(ConsistencyModifier.class);
        CLASS_IMPORTS.add(DefaultSchemaMaker.class);
        CLASS_IMPORTS.add(EdgeLabelMaker.class);
        CLASS_IMPORTS.add(Index.class);
        CLASS_IMPORTS.add(ItinerisConfiguration.class);
        CLASS_IMPORTS.add(ItinerisIndex.class);
        CLASS_IMPORTS.add(ItinerisManagement.class);
        CLASS_IMPORTS.add(ItinerisSchemaElement.class);
        CLASS_IMPORTS.add(ItinerisSchemaType.class);
        CLASS_IMPORTS.add(JobStatus.class);
        CLASS_IMPORTS.add(Mapping.class);
        CLASS_IMPORTS.add(Parameter.class);
        CLASS_IMPORTS.add(PropertyKeyMaker.class);
        CLASS_IMPORTS.add(RelationTypeIndex.class);
        CLASS_IMPORTS.add(RelationTypeMaker.class);
        CLASS_IMPORTS.add(SchemaAction.class);
        CLASS_IMPORTS.add(SchemaInspector.class);
        CLASS_IMPORTS.add(SchemaManager.class);
        CLASS_IMPORTS.add(SchemaStatus.class);
        CLASS_IMPORTS.add(VertexLabelMaker.class);

        CLASS_IMPORTS.add(GraphOfTheGodsFactory.class);
        CLASS_IMPORTS.add(ConfigurationManagementGraph.class);
        CLASS_IMPORTS.add(ManagementSystem.class);

        ///////////
        // ENUMS //
        ///////////

        // also make sure the class is imported for these enums

        Collections.addAll(ENUM_IMPORTS, Multiplicity.values());
        Collections.addAll(ENUM_IMPORTS, Cardinality.values());

    }

    private static final ItinerisGremlinPlugin instance = new ItinerisGremlinPlugin();

    public ItinerisGremlinPlugin() {
        super(NAME, DefaultImportCustomizer.build()
            .addClassImports(CLASS_IMPORTS)
            .addEnumImports(ENUM_IMPORTS)
            .addMethodImports(METHOD_IMPORTS));
    }

    public static ItinerisGremlinPlugin instance() {
        return instance;
    }

    public ItinerisGremlinPlugin(String moduleName, Customizer... customizers) {
        super(moduleName, customizers);
    }

    @Override
    public boolean requireRestart() {
        return true;
    }

}
