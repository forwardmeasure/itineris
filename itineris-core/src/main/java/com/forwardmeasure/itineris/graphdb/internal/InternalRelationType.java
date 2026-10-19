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

package com.forwardmeasure.itineris.graphdb.internal;

import org.apache.tinkerpop.gremlin.structure.Direction;
import com.forwardmeasure.itineris.core.Multiplicity;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.schema.ConsistencyModifier;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.indextype.IndexReferenceType;

/**
 * Internal Type interface adding methods that should only be used by Itineris
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface InternalRelationType extends RelationType, InternalVertex {

    boolean isInvisibleType();

    long[] getSignature();

    long[] getSortKey();

    Order getSortOrder();

    Multiplicity multiplicity();

    ConsistencyModifier getConsistencyModifier();

    Integer getTTL();

    boolean isUnidirected(Direction dir);

    InternalRelationType getBaseType();

    Iterable<InternalRelationType> getRelationIndexes();

    SchemaStatus getStatus();

    Iterable<IndexType> getKeyIndexes();

    Iterable<IndexReferenceType> getKeyIndexesReferences();
}
