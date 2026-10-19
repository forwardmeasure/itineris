// Copyright 2020 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.query.index;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.mutable.MutableBoolean;
import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.graphdb.internal.InternalRelationType;
import com.forwardmeasure.itineris.graphdb.internal.OrderList;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.ConditionUtil;
import com.forwardmeasure.itineris.graphdb.query.condition.MultiCondition;
import com.forwardmeasure.itineris.graphdb.query.condition.PredicateCondition;
import com.forwardmeasure.itineris.graphdb.types.IndexField;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class IndexSelectionUtil {
    public static boolean indexCoversOrder(MixedIndexType index, OrderList orders) {
        for (int i = 0; i < orders.size(); i++) {
            if (!index.indexesKey(orders.getKey(i)))
                return false;
        }
        return true;
    }

    private static Iterable<IndexType> getKeyIndexesForCondition(Condition<ItinerisElement> condition) {
        if (condition instanceof PredicateCondition) {
            final RelationType type = ((PredicateCondition<RelationType, ItinerisElement>) condition).getKey();
            Preconditions.checkArgument(type != null && type.isPropertyKey());
            return ((InternalRelationType) type).getKeyIndexes();
        }
        return Collections.emptySet();
    }

    public static Set<IndexType> getMatchingIndexes(MultiCondition<ItinerisElement> conditions) {
        return getMatchingIndexes(conditions, i -> true);
    }

    public static Set<IndexType> getMatchingIndexes(MultiCondition<ItinerisElement> conditions, Predicate<IndexType> filter) {
        if (conditions == null || filter == null) {
            return Collections.emptySet();
        }
        final Set<IndexType> availableIndexes = new HashSet<>();
        ConditionUtil.traversal(conditions, condition -> {
            for (IndexType indexCandidate : getKeyIndexesForCondition(condition)) {
                if (filter.test(indexCandidate)) {
                    availableIndexes.add(indexCandidate);
                }
            }
            return true;
        });
        return availableIndexes;
    }

    public static boolean existsMatchingIndex(MultiCondition<ItinerisElement> conditions)  {
        return existsMatchingIndex(conditions, i -> true);
    }

    public static boolean existsMatchingIndex(MultiCondition<ItinerisElement> conditions, Predicate<IndexType> filter) {
        if (conditions == null || filter == null) {
            return false;
        }
        MutableBoolean exists = new MutableBoolean();
        ConditionUtil.traversal(conditions, condition -> {
            if(exists.isFalse()){
                for (IndexType indexCandidate : getKeyIndexesForCondition(condition)) {
                    if (filter.test(indexCandidate)) {
                        exists.setTrue();
                        return true;
                    }
                }
            }
            return true;
        });
        return exists.booleanValue();
    }

    public static boolean isIndexSatisfiedByGivenKeys(IndexType index, Collection<String> givenKeys) {
        for (IndexField indexField : index.getFieldKeys()) {
            if (!givenKeys.contains(indexField.getFieldKey().name())) {
                return false;
            }
        }
        return true;
    }
}
