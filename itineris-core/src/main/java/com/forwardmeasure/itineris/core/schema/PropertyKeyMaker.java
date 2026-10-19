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

import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.PropertyKey;

/**
 * Used to define new {@link com.forwardmeasure.itineris.core.PropertyKey}s.
 * An property key is defined by its name, {@link Cardinality}, its data type, and its signature - all of which
 * can be specified in this builder.
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface PropertyKeyMaker extends RelationTypeMaker {

    /**
     * Configures the {@link com.forwardmeasure.itineris.core.Cardinality} of this property key.
     *
     * @param cardinality
     * @return this PropertyKeyMaker
     */
    PropertyKeyMaker cardinality(Cardinality cardinality);

    /**
     * Configures the {@link org.apache.tinkerpop.gremlin.structure.VertexProperty.Cardinality} of this property key.
     *
     * @param cardinality
     * @return this PropertyKeyMaker
     */
    PropertyKeyMaker cardinality(VertexProperty.Cardinality cardinality);

    /**
     * This method allows to check if is set using method cardinality();
     *
     * @return
     */
    boolean cardinalityIsSet();

    /**
     * Configures the data type for this property key.
     * <p>
     * Property instances for this key will only accept values that are instances of this class.
     * Every property key must have its data type configured. Setting the data type to Object.class allows
     * any type of value but comes at the expense of longer serialization because class information
     * is stored with the value.
     * <p>
     * It is strongly advised to pick an appropriate data type class so Itineris can enforce it throughout the database.
     *
     * @param clazz Data type to be configured.
     * @return this PropertyKeyMaker
     * @see com.forwardmeasure.itineris.core.PropertyKey#dataType()
     */
    PropertyKeyMaker dataType(Class<?> clazz);

    @Override
    PropertyKeyMaker signature(PropertyKey... types);


    /**
     * Defines the {@link com.forwardmeasure.itineris.core.PropertyKey} specified by this PropertyKeyMaker and returns the resulting key.
     *
     * @return the created {@link PropertyKey}
     */
    @Override
    PropertyKey make();
}
