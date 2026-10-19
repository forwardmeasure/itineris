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

package com.forwardmeasure.itineris.graphdb.query.graph;

import com.google.common.collect.ImmutableList;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexQuery;
import com.forwardmeasure.itineris.graphdb.query.condition.And;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.Or;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Narrowing the first subquery of a joint query, which reads a mixed index, joins the condition to the conjuncts of the
 * subquery's conjunction rather than nesting the conjunction, which Solr can't parse as it writes it.
 */
public class JointIndexQueryTest {

    private static Condition<ItinerisElement> condition(String field, Cmp cmp, Object value) {
        return new PredicateCondition<>(field, cmp, value);
    }

    private static JointIndexQuery jointQueryOf(Condition<ItinerisElement> condition) {
        final MixedIndexType index = mock(MixedIndexType.class);
        when(index.isMixedIndex()).thenReturn(true);
        final JointIndexQuery query = new JointIndexQuery();
        query.add(index, new IndexQuery("store", condition, ImmutableList.of(), 10));
        return query;
    }

    private static IndexQuery firstOf(JointIndexQuery query) {
        return query.getQuery(0).getMixedQuery();
    }

    @Test
    public void joinsTheConditionToTheConjunctsOfTheFirstSubquery() {
        final Condition<ItinerisElement> kind = condition("kind", Cmp.EQUAL, "k");
        final Condition<ItinerisElement> after = condition("weight", Cmp.GREATER_THAN, 615);
        final Condition<ItinerisElement> range = condition("weight", Cmp.LESS_THAN_EQUAL, 764);
        final IndexQuery narrowed = firstOf(jointQueryOf(new And<>(kind, after)).narrowFirst(range));
        assertTrue(narrowed.getCondition() instanceof And);
        assertEquals(Arrays.asList(kind, after, range), ImmutableList.copyOf(narrowed.getCondition().getChildren()));
        assertEquals(10, narrowed.getLimit());
    }

    @Test
    public void joinsAnyOtherConditionWithTheGivenOne() {
        final Condition<ItinerisElement> either = new Or<>(condition("kind", Cmp.EQUAL, "a"),
            condition("kind", Cmp.EQUAL, "b"));
        final Condition<ItinerisElement> range = condition("weight", Cmp.LESS_THAN_EQUAL, 764);
        final IndexQuery narrowed = firstOf(jointQueryOf(either).narrowFirst(range));
        assertTrue(narrowed.getCondition() instanceof And);
        assertEquals(Arrays.asList(either, range), ImmutableList.copyOf(narrowed.getCondition().getChildren()));
    }
}
