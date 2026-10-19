// Copyright 2026 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.query;

import org.apache.tinkerpop.gremlin.process.traversal.NotP;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.core.attribute.Contain;
import com.forwardmeasure.itineris.core.attribute.Geo;
import com.forwardmeasure.itineris.core.attribute.Geoshape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ItinerisPredicateUtilsTest {

    @Test
    public void testConvertTinkerPopPredicates() {
        assertEquals(Cmp.EQUAL, ItinerisPredicateUtils.convert(P.eq(1).getBiPredicate()));
        assertEquals(Cmp.NOT_EQUAL, ItinerisPredicateUtils.convert(P.neq(1).getBiPredicate()));
        assertEquals(Cmp.GREATER_THAN, ItinerisPredicateUtils.convert(P.gt(1).getBiPredicate()));
        assertEquals(Cmp.GREATER_THAN_EQUAL, ItinerisPredicateUtils.convert(P.gte(1).getBiPredicate()));
        assertEquals(Cmp.LESS_THAN, ItinerisPredicateUtils.convert(P.lt(1).getBiPredicate()));
        assertEquals(Cmp.LESS_THAN_EQUAL, ItinerisPredicateUtils.convert(P.lte(1).getBiPredicate()));
        assertEquals(Contain.IN, ItinerisPredicateUtils.convert(P.within(1, 2).getBiPredicate()));
        assertEquals(Contain.NOT_IN, ItinerisPredicateUtils.convert(P.without(1, 2).getBiPredicate()));
    }

    @Test
    public void testConvertNegatedTinkerPopPredicates() {
        // Since TinkerPop 3.8 a negated predicate is wrapped into NotP instead of swapping in the
        // complementary bi-predicate, so the negation has to be unwrapped to be pushed down to the backend.
        assertTrue(P.not(P.eq(1)) instanceof NotP);
        assertTrue(P.eq(1).negate() instanceof NotP);

        assertEquals(Cmp.NOT_EQUAL, ItinerisPredicateUtils.convert(P.not(P.eq(1)).getBiPredicate()));
        assertEquals(Cmp.EQUAL, ItinerisPredicateUtils.convert(P.neq(1).negate().getBiPredicate()));
        assertEquals(Cmp.LESS_THAN_EQUAL, ItinerisPredicateUtils.convert(P.not(P.gt(1)).getBiPredicate()));
        assertEquals(Cmp.LESS_THAN, ItinerisPredicateUtils.convert(P.not(P.gte(1)).getBiPredicate()));
        assertEquals(Cmp.GREATER_THAN_EQUAL, ItinerisPredicateUtils.convert(P.not(P.lt(1)).getBiPredicate()));
        assertEquals(Cmp.GREATER_THAN, ItinerisPredicateUtils.convert(P.not(P.lte(1)).getBiPredicate()));
        assertEquals(Contain.NOT_IN, ItinerisPredicateUtils.convert(P.not(P.within(1, 2)).getBiPredicate()));
        assertEquals(Contain.IN, ItinerisPredicateUtils.convert(P.not(P.without(1, 2)).getBiPredicate()));

        // a double negation unwraps to the original predicate
        assertEquals(Cmp.GREATER_THAN, ItinerisPredicateUtils.convert(P.not(P.not(P.gt(1))).getBiPredicate()));

        assertTrue(ItinerisPredicateUtils.supports(P.not(P.eq(1)).getBiPredicate()));
        assertTrue(ItinerisPredicateUtils.supports(P.not(P.within(1, 2)).getBiPredicate()));
    }

    @Test
    public void testNegatedPredicateWithoutNegationIsNotSupported() {
        // Geo.WITHIN has no Itineris negation, so P.not(geoWithin) cannot be pushed down but must not throw either
        assertFalse(Geo.WITHIN.hasNegation());
        final P<Object> geoWithin = new P<>(Geo.WITHIN, Geoshape.point(0, 0));
        assertEquals(Geo.WITHIN, ItinerisPredicateUtils.convert(geoWithin.getBiPredicate()));
        assertFalse(ItinerisPredicateUtils.supports(new NotP<>(geoWithin).getBiPredicate()));
    }

    @Test
    public void testUnsupportedNegatedPredicate() {
        // an unknown bi-predicate stays unsupported when it is negated
        final P<Object> unknown = new P<>((value, condition) -> true, (Object) 1);
        assertFalse(ItinerisPredicateUtils.supports(unknown.getBiPredicate()));
        assertFalse(ItinerisPredicateUtils.supports(new NotP<>(unknown).getBiPredicate()));
        assertThrows(IllegalArgumentException.class,
            () -> ItinerisPredicateUtils.convert(new NotP<>(unknown).getBiPredicate()));
    }
}
