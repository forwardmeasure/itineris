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

import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.graphdb.database.IndexSerializer;
import com.forwardmeasure.itineris.graphdb.internal.OrderList;
import com.forwardmeasure.itineris.graphdb.query.condition.Condition;
import com.forwardmeasure.itineris.graphdb.query.condition.MultiCondition;
import com.forwardmeasure.itineris.graphdb.query.graph.JointIndexQuery;
import com.forwardmeasure.itineris.graphdb.types.IndexType;
import com.forwardmeasure.itineris.graphdb.types.MixedIndexType;

import java.util.Set;

/**
 * @author Florian Grieskamp (Florian.Grieskamp@gdata.de)
 */
public class ApproximateIndexSelectionStrategy
    extends AbstractIndexSelectionStrategy {

    public static final String NAME = "approximate";

    private static final double ORDER_MATCH = 1;
    private static final double ALREADY_MATCHED_ADJUSTOR = -1.1;

    public ApproximateIndexSelectionStrategy(Configuration config) {
        super(config);
    }

    /**
     * Iterate over all potential indexes and compute a score based on how many clauses
     * this index covers. The index with the highest score (as long as it covers at least one
     * additional clause) is picked and added to the joint query for as long as such exist.
     */
    @Override
    public SelectedIndexQuery selectIndices(final Set<IndexType> rawCandidates,
                                            final MultiCondition<ItinerisElement> conditions,
                                            final Set<Condition> coveredClauses, OrderList orders,
                                            IndexSerializer serializer) {
        final JointIndexQuery jointQuery = new JointIndexQuery();
        boolean isSorted = orders.isEmpty();
        while (true) {
            IndexCandidate bestCandidate = null;
            boolean candidateSupportsSort = false;

            for (final IndexType index : rawCandidates) {
                final IndexCandidate indexCandidate =
                    createIndexCandidate(index, conditions, serializer);
                if (indexCandidate == null) {
                    continue;
                }

                boolean supportsSort =
                    orders.isEmpty() ||
                    coveredClauses.isEmpty() && index.isMixedIndex() &&
                        IndexSelectionUtil.indexCoversOrder((MixedIndexType) index, orders);
                indexCandidate.setScore(calculateIndexCandidateScore(indexCandidate, coveredClauses, supportsSort));

                if (!coveredClauses.containsAll(indexCandidate.getSubCover()) &&
                    (bestCandidate == null ||
                     indexCandidate.getScore() > bestCandidate.getScore())) {
                    bestCandidate = indexCandidate;
                    candidateSupportsSort = supportsSort;
                }
            }

            if (bestCandidate != null) {
                if (coveredClauses.isEmpty()) {
                    isSorted = candidateSupportsSort;
                }
                coveredClauses.addAll(bestCandidate.getSubCover());
                addToJointQuery(bestCandidate, jointQuery, serializer, orders);
            } else {
                break;
            }
        }
        return new SelectedIndexQuery(jointQuery, isSorted);
    }

    private double calculateIndexCandidateScore(final IndexCandidate indexCandidate,
                                                final Set<Condition> coveredClauses,
                                                boolean supportsSort) {
        double score = 0.0;

        for (final Condition c : indexCandidate.getSubCover()) {
            double subScore = getConditionBasicScore(c);
            if (coveredClauses.contains(c)) {
                subScore = subScore * ALREADY_MATCHED_ADJUSTOR;
            }
            score += subScore + getIndexTypeScore(indexCandidate.getIndex());
        }

        if (supportsSort) {
            score += ORDER_MATCH;
        }

        return score;
    }
}
