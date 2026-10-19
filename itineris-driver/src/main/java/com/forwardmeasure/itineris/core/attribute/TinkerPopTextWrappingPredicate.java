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

package com.forwardmeasure.itineris.core.attribute;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.Text;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicate;

/**
 * A {@link ItinerisPredicate} that just wraps a TinkerPop {@link Text} predicate.
 *
 * This enables Itineris to use TinkerPop {@link Text} predicates in places where it expects a
 * {@link ItinerisPredicate}.
 */
public class TinkerPopTextWrappingPredicate implements ItinerisPredicate {
    private Text internalPredicate;

    public TinkerPopTextWrappingPredicate(Text wrappedPredicate) {
        internalPredicate = wrappedPredicate;
    }

    @Override
    public boolean isValidCondition(Object condition) {
        return condition instanceof String && StringUtils.isNoneBlank(condition.toString());
    }

    @Override
    public boolean isValidValueType(Class<?> clazz) {
        return clazz.equals(String.class);
    }

    @Override
    public boolean hasNegation() {
        return true;
    }

    @Override
    public ItinerisPredicate negate() {
        return new TinkerPopTextWrappingPredicate(internalPredicate.negate());
    }

    @Override
    public boolean isQNF() {
        return true;
    }

    @Override
    public boolean test(Object value, Object condition) {
        return internalPredicate.test(value.toString(), (String) condition);
    }
}
