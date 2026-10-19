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

package com.forwardmeasure.itineris.core.schema;

import com.forwardmeasure.itineris.core.Idfiable;

/**
 * A ItinerisSchemaType is a {@link ItinerisSchemaElement} that represents a label or key
 * used in the graph. As such, a schema type is either a {@link com.forwardmeasure.itineris.core.RelationType}
 * or a {@link com.forwardmeasure.itineris.core.VertexLabel}.
 * <p>
 * ItinerisSchemaTypes are a special {@link ItinerisSchemaElement} in that they are referenced from the
 * main graph when creating vertices, edges, and properties.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface ItinerisSchemaType extends Idfiable, ItinerisSchemaElement {

    /**
     * Checks whether this schema type has been newly created in the current transaction.
     *
     * @return True, if the schema type has been newly created, else false.
     */
    boolean isNew();

    /**
     * Returns the long representation of the id.
     * Since the id of a schema type is always auto-generated, its
     * id must be of long type
     *
     * @return
     */
    default long longId() {
        return ((Number) id()).longValue();
    }
}
