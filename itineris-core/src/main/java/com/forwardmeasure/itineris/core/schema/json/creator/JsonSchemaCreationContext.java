// Copyright 2024 JanusGraph Authors
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

package com.forwardmeasure.itineris.core.schema.json.creator;

import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.core.schema.json.definition.index.AbstractJsonIndexDefinition;

import java.util.HashSet;
import java.util.Set;

public class JsonSchemaCreationContext {

    private ItinerisManagement graphManagement;
    private final boolean createSchemaElements;
    private final boolean createIndices;
    private final Set<AbstractJsonIndexDefinition> createdOrUpdatedIndices = new HashSet<>();

    public JsonSchemaCreationContext(ItinerisManagement graphManagement, boolean createSchemaElements, boolean createIndices) {
        this.graphManagement=graphManagement;
        this.createSchemaElements = createSchemaElements;
        this.createIndices = createIndices;
    }

    public void setGraphManagement(ItinerisManagement graphManagement) {
        this.graphManagement = graphManagement;
    }

    public ItinerisManagement getGraphManagement() {
        return graphManagement;
    }

    public boolean isCreateSchemaElements() {
        return createSchemaElements;
    }

    public boolean isCreateIndices() {
        return createIndices;
    }

    public Set<AbstractJsonIndexDefinition> getCreatedOrUpdatedIndices() {
        return createdOrUpdatedIndices;
    }
}
