// Copyright 2024 Itineris Authors
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

package com.forwardmeasure.itineris.core.schema;

import org.apache.commons.lang3.StringUtils;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.Namifiable;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.schema.json.creator.GeneralJsonSchemaCreator;
import com.forwardmeasure.itineris.core.schema.json.creator.JsonSchemaCreationContext;
import com.forwardmeasure.itineris.core.schema.json.creator.SchemaCreationException;
import com.forwardmeasure.itineris.core.schema.json.definition.JsonSchemaDefinition;
import com.forwardmeasure.itineris.core.schema.json.definition.index.AbstractJsonGraphCentricIndexDefinition;
import com.forwardmeasure.itineris.core.schema.json.definition.index.AbstractJsonIndexDefinition;
import com.forwardmeasure.itineris.core.schema.json.definition.index.AbstractJsonVertexCentricIndexDefinition;
import com.forwardmeasure.itineris.core.schema.json.definition.index.JsonVertexCentricEdgeIndexDefinition;
import com.forwardmeasure.itineris.core.schema.json.definition.index.JsonVertexCentricPropertyIndexDefinition;
import com.forwardmeasure.itineris.core.util.JsonUtil;
import com.forwardmeasure.itineris.core.util.ManagementUtil;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonSchemaInitStrategy implements SchemaInitStrategy{

    private static final Logger LOG = LoggerFactory.getLogger(JsonSchemaInitStrategy.class);

    @Override
    public Itineris initializeSchemaAndStart(GraphDatabaseConfiguration graphDatabaseConfiguration) {

        Itineris graph = new StandardItineris(graphDatabaseConfiguration);

        try{
            if(StringUtils.isNotEmpty(graphDatabaseConfiguration.getSchemaInitJsonString())) {

                initializeSchemaFromString(graph, graphDatabaseConfiguration.getSchemaInitJsonString());

            } else if(StringUtils.isNotEmpty(graphDatabaseConfiguration.getSchemaInitJsonFile())){

                initializeSchemaFromFile(graph, graphDatabaseConfiguration.getSchemaInitJsonFile());

            } else {
                throw new SchemaCreationException("When JSON schema initialization is enabled it's necessary to provide either JSON file or string for schema initialization.");
            }
        } catch (Throwable throwable){
            graph.close();
            if(throwable instanceof SchemaCreationException){
                throw (SchemaCreationException)throwable;
            }
            throw new SchemaCreationException(throwable);
        }

        return graph;
    }

    public static void initializeSchemaFromFile(Itineris graph, String jsonSchemaFilePath) {
        GraphDatabaseConfiguration graphDatabaseConfiguration = ((StandardItineris) graph).getConfiguration();
        IndicesActivationType indicesActivationType = graphDatabaseConfiguration.getSchemaInitJsonIndicesActivationType();
        boolean createSchemaElements = !graphDatabaseConfiguration.getSchemaInitJsonSkipElements();
        boolean createSchemaIndices = !graphDatabaseConfiguration.getSchemaInitJsonSkipIndices();
        long indexStatusTimeout = graphDatabaseConfiguration.getSchemaInitJsonIndexStatusAwaitTimeout();

        initializeSchemaFromFile(graph, createSchemaElements, createSchemaIndices, indicesActivationType, true,
            graphDatabaseConfiguration.getSchemaInitJsonForceCloseOtherInstances(), indexStatusTimeout, jsonSchemaFilePath);
    }

    public static void initializeSchemaFromString(Itineris graph, String jsonSchemaString) {
        GraphDatabaseConfiguration graphDatabaseConfiguration = ((StandardItineris) graph).getConfiguration();
        IndicesActivationType indicesActivationType = graphDatabaseConfiguration.getSchemaInitJsonIndicesActivationType();
        boolean createSchemaElements = !graphDatabaseConfiguration.getSchemaInitJsonSkipElements();
        boolean createSchemaIndices = !graphDatabaseConfiguration.getSchemaInitJsonSkipIndices();
        long indexStatusTimeout = graphDatabaseConfiguration.getSchemaInitJsonIndexStatusAwaitTimeout();

        initializeSchemaFromString(graph, createSchemaElements, createSchemaIndices, indicesActivationType, true,
            graphDatabaseConfiguration.getSchemaInitJsonForceCloseOtherInstances(), indexStatusTimeout, jsonSchemaString);
    }

    public static void initializeSchemaFromFile(Itineris graph, boolean createSchemaElements, boolean createSchemaIndices,
                                                IndicesActivationType indicesActivationType,
                                                boolean forceRollBackActiveTransactions, boolean forceCloseOtherInstances,
                                                long indexStatusTimeout, String jsonSchemaFilePath) {

        JsonSchemaDefinition generalDefinition;
        try {
            generalDefinition = JsonUtil.jsonFilePathToObject(jsonSchemaFilePath, JsonSchemaDefinition.class);
        } catch (IOException e) {
            throw new SchemaCreationException(e);
        }

        initializeSchema(graph, createSchemaElements, createSchemaIndices, indicesActivationType,
            forceRollBackActiveTransactions, forceCloseOtherInstances, indexStatusTimeout, generalDefinition);
    }

    public static void initializeSchemaFromString(Itineris graph, boolean createSchemaElements, boolean createSchemaIndices,
                                                  IndicesActivationType indicesActivationType,
                                                  boolean forceRollBackActiveTransactions, boolean forceCloseOtherInstances,
                                                  long indexStatusTimeout, String jsonSchemaString) {

        JsonSchemaDefinition generalDefinition;
        try {
            generalDefinition = JsonUtil.jsonStringToObject(jsonSchemaString, JsonSchemaDefinition.class);
        } catch (IOException e) {
            throw new SchemaCreationException(e);
        }

        initializeSchema(graph, createSchemaElements, createSchemaIndices, indicesActivationType,
            forceRollBackActiveTransactions, forceCloseOtherInstances, indexStatusTimeout, generalDefinition);
    }

    public static void initializeSchema(Itineris graph, boolean createSchemaElements, boolean createSchemaIndices,
                                        IndicesActivationType indicesActivationType,
                                        boolean forceRollBackActiveTransactions, boolean forceCloseOtherInstances,
                                        long indexStatusTimeout, JsonSchemaDefinition generalDefinition) {

        LOG.info("Starting schema initialization.");

        if(forceCloseOtherInstances){
            ManagementUtil.forceCloseOtherInstances(graph);
        }

        if(forceRollBackActiveTransactions){
            ManagementUtil.forceRollbackAllTransactions(graph);
        }

        final ManagementSystem schemaCreationMgmt = (ManagementSystem) graph.openManagement();
        JsonSchemaCreationContext schemaCreationContext = new JsonSchemaCreationContext(
            schemaCreationMgmt,
            createSchemaElements,
            createSchemaIndices);

        GeneralJsonSchemaCreator generalJsonSchemaCreator = new GeneralJsonSchemaCreator();

        // initialize schema
        generalJsonSchemaCreator.create(generalDefinition, schemaCreationContext);

        List<String> updatedButNonEnabledIndicesNames = schemaCreationContext.getCreatedOrUpdatedIndices().stream()
            .filter(indexDefinition -> !isIndexEnabled(indexDefinition, schemaCreationMgmt))
            .map(AbstractJsonIndexDefinition::getName)
            .collect(Collectors.toList());

        schemaCreationMgmt.commit();

        // process indices activation (re-index and enable)
        processIndicesActivation(graph, updatedButNonEnabledIndicesNames, indicesActivationType, indexStatusTimeout);

        LOG.info("Schema initialization completed.");
    }

    private static void processIndicesActivation(Itineris graph, List<String> updatedButNonEnabledIndicesNames,
                                                 IndicesActivationType indicesActivationType, long indexStatusTimeout){

        if(IndicesActivationType.SKIP_ACTIVATION.equals(indicesActivationType)){
            return;
        }

        ManagementSystem schemaFetchMgmt = (ManagementSystem) graph.openManagement();

        List<ItinerisIndex> nonEnabledGraphIndices = schemaFetchMgmt.getGraphIndices(SchemaStatus.ENABLED);
        List<RelationTypeIndex> nonEnabledRelationTypeIndices = schemaFetchMgmt.getVertexCentricIndices(SchemaStatus.ENABLED);

        if(IndicesActivationType.REINDEX_AND_ENABLE_UPDATED_ONLY.equals(indicesActivationType) || IndicesActivationType.FORCE_ENABLE_UPDATED_ONLY.equals(indicesActivationType)){
            List<ItinerisIndex> nonEnabledGraphIndicesCopy = nonEnabledGraphIndices;
            List<RelationTypeIndex> nonEnabledRelationTypeIndicesCopy = nonEnabledRelationTypeIndices;
            nonEnabledGraphIndices = new ArrayList<>(nonEnabledGraphIndices.size());
            nonEnabledRelationTypeIndices = new ArrayList<>(nonEnabledRelationTypeIndices.size());
            for (ItinerisIndex index : nonEnabledGraphIndicesCopy) {
                if(updatedButNonEnabledIndicesNames.contains(index.name())){
                    nonEnabledGraphIndices.add(index);
                }
            }
            for (RelationTypeIndex index : nonEnabledRelationTypeIndicesCopy) {
                if(updatedButNonEnabledIndicesNames.contains(index.name())){
                    nonEnabledRelationTypeIndices.add(index);
                }
            }
        }

        List<String> nonEnabledGraphIndexNames = nonEnabledGraphIndices.stream().map(ItinerisIndex::name).collect(Collectors.toList());
        Map<String, String> nonEnabledRelationTypeIndexNamesAndTypes = nonEnabledRelationTypeIndices.stream().collect(Collectors.toMap(Namifiable::name, o -> o.getType().name()));

        schemaFetchMgmt.rollback();

        List<String> nonEnabledIndexNames = Stream.concat(nonEnabledGraphIndexNames.stream(), nonEnabledRelationTypeIndexNamesAndTypes.keySet().stream()).collect(Collectors.toList());

        if(!nonEnabledIndexNames.isEmpty()){

            String nonEnabledIndexNamesJoined = String.join(", ", nonEnabledIndexNames);
            if(!nonEnabledIndexNames.isEmpty() && LOG.isInfoEnabled()){
                LOG.info("Found {} non-enabled indices. Awaiting for their status updates. Indexes [{}].",
                    nonEnabledIndexNames.size(), nonEnabledIndexNamesJoined);
            }

            if(IndicesActivationType.REINDEX_AND_ENABLE_UPDATED_ONLY.equals(indicesActivationType) || IndicesActivationType.REINDEX_AND_ENABLE_NON_ENABLED.equals(indicesActivationType)){

                ManagementUtil.reindexAndEnableIndices(graph, nonEnabledGraphIndexNames, nonEnabledRelationTypeIndexNamesAndTypes, indexStatusTimeout);

            } else if(IndicesActivationType.FORCE_ENABLE_UPDATED_ONLY.equals(indicesActivationType) || IndicesActivationType.FORCE_ENABLE_NON_ENABLED.equals(indicesActivationType)){

                ManagementUtil.forceEnableIndices(graph, nonEnabledGraphIndexNames, nonEnabledRelationTypeIndexNamesAndTypes, indexStatusTimeout);

            } else {
                throw new IllegalStateException("Unknown indicesActivationType: " + indicesActivationType);
            }
        }
    }

    private static boolean isIndexEnabled(AbstractJsonIndexDefinition createdIndexesDefinition, ItinerisManagement graphManagement){
        return isIndexHasStatus(createdIndexesDefinition, graphManagement, SchemaStatus.ENABLED);
    }

    private static boolean isIndexHasStatus(AbstractJsonIndexDefinition createdIndexesDefinition, ItinerisManagement graphManagement, SchemaStatus schemaStatus){
        if(createdIndexesDefinition instanceof AbstractJsonVertexCentricIndexDefinition){

            AbstractJsonVertexCentricIndexDefinition vertexCentricIndex =
                (AbstractJsonVertexCentricIndexDefinition) createdIndexesDefinition;
            String indexedElement;
            if(vertexCentricIndex instanceof JsonVertexCentricEdgeIndexDefinition){
                indexedElement = ((JsonVertexCentricEdgeIndexDefinition) vertexCentricIndex).getIndexedEdgeLabel();
            } else if (vertexCentricIndex instanceof JsonVertexCentricPropertyIndexDefinition){
                indexedElement = ((JsonVertexCentricPropertyIndexDefinition) vertexCentricIndex).getIndexedPropertyKey();
            } else {
                throw new SchemaCreationException("Unknown index type definition: "+createdIndexesDefinition.getClass().getName());
            }
            RelationType relationType = graphManagement.getRelationType(indexedElement);
            RelationTypeIndex relationIndex = graphManagement.getRelationIndex(relationType, vertexCentricIndex.getName());
            return schemaStatus.equals(relationIndex.getIndexStatus());

        } else if (createdIndexesDefinition instanceof AbstractJsonGraphCentricIndexDefinition) {

            ItinerisIndex janusGraphIndex = graphManagement.getGraphIndex(createdIndexesDefinition.getName());
            for(PropertyKey propertyKey : janusGraphIndex.getFieldKeys()){
                if(!schemaStatus.equals(janusGraphIndex.getIndexStatus(propertyKey))){
                    return false;
                }
            }
            return true;

        } else {
            throw new SchemaCreationException("Unknown index type definition: "+createdIndexesDefinition.getClass().getName());
        }
    }

}
