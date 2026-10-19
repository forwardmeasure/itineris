// Copyright 2021 JanusGraph Authors
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

import com.forwardmeasure.itineris.StorageSetup;
import com.forwardmeasure.itineris.core.ItinerisRelation;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.core.attribute.Contain;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.predicate.AndJanusPredicate;
import com.forwardmeasure.itineris.graphdb.predicate.OrJanusPredicate;
import com.forwardmeasure.itineris.graphdb.query.condition.And;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.Not;
import com.forwardmeasure.itineris.graphdb.query.condition.Or;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.RelationTypeCondition;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
public class QueryUtilTest {

    @Mock
    private StandardItinerisTx tx;

    @Test
    void testAdjustLimitForTxModifications() {
        Mockito.when(tx.hasModifications()).thenReturn(true);

        assertEquals(1005, QueryUtil.adjustLimitForTxModifications(tx, 0, 1000));
        assertEquals(100005, QueryUtil.adjustLimitForTxModifications(tx, 0, 100000));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 0, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 0, Integer.MAX_VALUE - 1));
        assertEquals(Integer.MAX_VALUE / 2 + 5, QueryUtil.adjustLimitForTxModifications(tx, 0, Integer.MAX_VALUE / 2));

        assertEquals(2005, QueryUtil.adjustLimitForTxModifications(tx, 1, 1000));
        assertEquals(200005, QueryUtil.adjustLimitForTxModifications(tx, 1, 100000));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 1, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 1, Integer.MAX_VALUE - 1));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 1, Integer.MAX_VALUE / 2));

        assertEquals(1024005, QueryUtil.adjustLimitForTxModifications(tx, 10, 1000));
        assertEquals(102400005, QueryUtil.adjustLimitForTxModifications(tx, 10, 100000));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 10, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 10, Integer.MAX_VALUE - 1));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 10, Integer.MAX_VALUE / 2));

        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 30, 100000));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 30, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 30, Integer.MAX_VALUE - 1));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 30, Integer.MAX_VALUE / 2));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 100, 100000));

        Mockito.when(tx.hasModifications()).thenReturn(false);

        assertEquals(1000, QueryUtil.adjustLimitForTxModifications(tx, 0, 1000));
        assertEquals(100000, QueryUtil.adjustLimitForTxModifications(tx, 0, 100000));
        assertEquals(2000, QueryUtil.adjustLimitForTxModifications(tx, 1, 1000));
        assertEquals(200000, QueryUtil.adjustLimitForTxModifications(tx, 1, 100000));
        assertEquals(Integer.MAX_VALUE, QueryUtil.adjustLimitForTxModifications(tx, 10, Integer.MAX_VALUE));
    }

    //A within() or without() keeps one condition for each distinct value, in the order in which the values first appear,
    //and none for a value which the conditions hold already, however the predicate nests them
    @Test
    void testContainmentKeepsOneConditionPerDistinctValue() {
        final StandardItineris graph = (StandardItineris) StorageSetup.getInMemoryGraph();
        try {
            final ItinerisManagement management = graph.openManagement();
            management.makePropertyKey("uid").dataType(Long.class).make();
            management.commit();
            final StandardItinerisTx graphTx = (StandardItinerisTx) graph.newTransaction();
            try {
                final List<Long> repeated = Arrays.asList(3L, 1L, 3L, 2L, 1L);
                final List<Long> five = Arrays.asList(5L, 5L);

                assertEquals("and(or(=3,=1,=2))", describe(qnf(graphTx,
                    new PredicateCondition<>("uid", Contain.IN, repeated))));
                assertEquals("and(!=1,!=3,!=2)", describe(qnf(graphTx,
                    new PredicateCondition<>("uid", Cmp.NOT_EQUAL, 1L),
                    new PredicateCondition<>("uid", Contain.NOT_IN, repeated))));
                assertEquals("and(or(=3,=1,=2),!=5)", describe(qnf(graphTx, new PredicateCondition<>("uid",
                    new AndJanusPredicate(Arrays.asList(Contain.IN, Contain.NOT_IN)), Arrays.asList(repeated, five)))));
                assertEquals("and(or(=3,=1,=2,and(!=5)))", describe(qnf(graphTx, new PredicateCondition<>("uid",
                    new OrJanusPredicate(Arrays.asList(Contain.IN, Contain.NOT_IN)), Arrays.asList(repeated, five)))));

                final List<Long> many = new ArrayList<>();
                for (long i = 0; i < 20_000; i++) {
                    many.add(i);
                }
                many.addAll(many.subList(0, 5_000));
                final Condition<ItinerisVertex> within = qnf(graphTx, new PredicateCondition<>("uid", Contain.IN, many))
                    .getChildren().iterator().next();
                final List<Object> values = StreamSupport.stream(within.getChildren().spliterator(), false)
                    .map(condition -> ((PredicateCondition<?, ?>) condition).getValue()).collect(Collectors.toList());
                assertEquals(new ArrayList<>(many.subList(0, 20_000)), values);
            } finally {
                graphTx.rollback();
            }
        } finally {
            graph.close();
        }
    }

    //A vertex-centric query of some types holds a condition on the type of its relations, alone or in a conjunction,
    //which tells the types any relation it matches has
    @Test
    void testRelationTypesOfACondition() {
        final RelationType a = Mockito.mock(RelationType.class);
        final RelationType b = Mockito.mock(RelationType.class);
        final RelationType c = Mockito.mock(RelationType.class);
        final Condition<ItinerisRelation> other = new PredicateCondition<>("since", Cmp.EQUAL, 1);

        assertEquals(Set.of(a), QueryUtil.getRelationTypes(type(a)));
        assertEquals(Set.of(a, b), QueryUtil.getRelationTypes(Or.of(type(a), type(b))));
        assertEquals(Set.of(a), QueryUtil.getRelationTypes(And.of(other, type(a))));
        assertEquals(Set.of(a, b), QueryUtil.getRelationTypes(And.of(Or.of(type(a), type(b)), other)));
        assertEquals(Set.of(b), QueryUtil.getRelationTypes(And.of(Or.of(type(a), type(b)), Or.of(type(b), type(c)))));
        assertEquals(Set.of(b), QueryUtil.getRelationTypes(And.of(Or.of(type(a), type(b)), And.of(other, type(b)))));
        //No relation has two types
        assertEquals(Collections.emptySet(), QueryUtil.getRelationTypes(And.of(type(a), type(b))));

        //Conditions which don't tell the type, and no condition
        assertNull(QueryUtil.getRelationTypes(null));
        assertNull(QueryUtil.getRelationTypes(other));
        assertNull(QueryUtil.getRelationTypes(And.of(other)));
        assertNull(QueryUtil.getRelationTypes(new And<ItinerisRelation>()));
        assertNull(QueryUtil.getRelationTypes(Or.of(type(a), other)));
        //A disjunction without children holds for every relation
        assertNull(QueryUtil.getRelationTypes(new Or<ItinerisRelation>()));
        assertNull(QueryUtil.getRelationTypes(new Not<>(type(a))));
    }

    private static Condition<ItinerisRelation> type(RelationType type) {
        return new RelationTypeCondition<>(type);
    }

    @SafeVarargs
    private static And<ItinerisVertex> qnf(StandardItinerisTx graphTx,
                                             PredicateCondition<String, ItinerisVertex>... constraints) {
        return QueryUtil.constraints2QNF(graphTx, Arrays.asList(constraints));
    }

    private static String describe(Condition<?> condition) {
        if (condition instanceof PredicateCondition) {
            final PredicateCondition<?, ?> predicate = (PredicateCondition<?, ?>) condition;
            return (predicate.getPredicate() == Cmp.EQUAL ? "=" : predicate.getPredicate() == Cmp.NOT_EQUAL ? "!=" :
                predicate.getPredicate().toString()) + predicate.getValue();
        }
        final String children = StreamSupport.stream(condition.getChildren().spliterator(), false)
            .map(QueryUtilTest::describe).collect(Collectors.joining(","));
        return (condition instanceof And ? "and" : condition instanceof Or ? "or" : condition.getType().toString())
            + "(" + children + ")";
    }
}
