// Copyright 2017 Itineris Authors
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

package com.forwardmeasure.itineris.core;


import com.forwardmeasure.itineris.core.schema.ItinerisSchemaType;

/**
 * RelationType defines the schema for {@link ItinerisRelation}. RelationType can be configured through {@link com.forwardmeasure.itineris.core.schema.RelationTypeMaker} to
 * provide data verification, better storage efficiency, and higher retrieval performance.
 * <br>
 * Each {@link ItinerisRelation} has a unique type which defines many important characteristics of that relation.
 * <br>
 * RelationTypes are constructed through {@link com.forwardmeasure.itineris.core.schema.RelationTypeMaker} which is accessed in the context of a {@link ItinerisTransaction}
 * via {@link com.forwardmeasure.itineris.core.ItinerisTransaction#makePropertyKey(String)} for property keys or {@link com.forwardmeasure.itineris.core.ItinerisTransaction#makeEdgeLabel(String)}
 * for edge labels. Identical methods exist on {@link Itineris}.
 * Note, relation types will only be visible once the transaction in which they were created has been committed.
 * <br>
 * RelationType names must be unique in a graph database. Many methods allow the name of the type as an argument
 * instead of the actual type reference. That also means, that edge labels and property keys may not have the same name.
 *
 * @author Matthias Br&ouml;cheler (me@matthiasb.com);
 * @see ItinerisRelation
 * @see com.forwardmeasure.itineris.core.schema.RelationTypeMaker
 * @see <a href="https://docs.itineris.org/basics/schema/">"Schema and Data Modeling" manual chapter</a>
 */
public interface RelationType extends ItinerisVertex, ItinerisSchemaType {

    /**
     * Checks if this relation type is a property key
     *
     * @return true, if this relation type is a property key, else false.
     * @see PropertyKey
     */
    boolean isPropertyKey();

    /**
     * Checks if this relation type is an edge label
     *
     * @return true, if this relation type is a edge label, else false.
     * @see EdgeLabel
     */
    boolean isEdgeLabel();

}
