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

package com.forwardmeasure.itineris.graphdb.query;

import org.apache.tinkerpop.gremlin.process.traversal.Compare;
import org.apache.tinkerpop.gremlin.process.traversal.Contains;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Text;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.AndP;
import org.apache.tinkerpop.gremlin.process.traversal.util.ConnectiveP;
import org.apache.tinkerpop.gremlin.process.traversal.util.OrP;
import com.forwardmeasure.itineris.core.attribute.Cmp;
import com.forwardmeasure.itineris.core.attribute.Contain;
import com.forwardmeasure.itineris.core.attribute.TinkerPopTextWrappingPredicate;
import com.forwardmeasure.itineris.graphdb.predicate.AndJanusPredicate;
import com.forwardmeasure.itineris.graphdb.predicate.ConnectiveItinerisP;
import com.forwardmeasure.itineris.graphdb.predicate.ConnectiveJanusPredicate;
import com.forwardmeasure.itineris.graphdb.predicate.OrJanusPredicate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class ItinerisPredicateUtils {

    /**
     * Convert Tinkerpop's comparison operators to Itineris's
     *
     * @param p Any predicate
     * @return A ItinerisPredicate equivalent to the given predicate
     * @throws IllegalArgumentException if the given Predicate is unknown
     */
    public static ItinerisPredicate convertInternal(BiPredicate p) {
        if (p instanceof ItinerisPredicate) {
            return (ItinerisPredicate)p;
        } else if (p instanceof Compare) {
            final Compare comp = (Compare)p;
            switch(comp) {
                case eq: return Cmp.EQUAL;
                case neq: return Cmp.NOT_EQUAL;
                case gt: return Cmp.GREATER_THAN;
                case gte: return Cmp.GREATER_THAN_EQUAL;
                case lt: return Cmp.LESS_THAN;
                case lte: return Cmp.LESS_THAN_EQUAL;
                default: throw new IllegalArgumentException("Unexpected comparator: " + comp);
            }
        } else if (p instanceof Contains) {
            final Contains con = (Contains)p;
            switch (con) {
                case within: return Contain.IN;
                case without: return Contain.NOT_IN;
                default: throw new IllegalArgumentException("Unexpected container: " + con);

            }
        } else return null;
    }

    public static ItinerisPredicate convert(BiPredicate p) {
        final ItinerisPredicate itinerisPredicate = convertInternal(p);
        if (itinerisPredicate==null) throw new IllegalArgumentException("Itineris does not support the given predicate: " + p);
        return itinerisPredicate;
    }

    public static boolean supports(BiPredicate p) {
        return convertInternal(p)!=null;
    }

    public static HasContainer convert(final HasContainer container){
        if (!(container.getPredicate() instanceof ConnectiveP)) {
            return container;
        }
        final ConnectiveJanusPredicate connectivePredicate = instanceConnectiveJanusPredicate(container.getPredicate());
        return new HasContainer(container.getKey(), new ConnectiveItinerisP(connectivePredicate, convert(((ConnectiveP<?>) container.getPredicate()), connectivePredicate)));
    }

    public static ConnectiveJanusPredicate instanceConnectiveJanusPredicate(final P<?> predicate) {
        final ConnectiveJanusPredicate connectivePredicate;
        if (predicate.getClass().isAssignableFrom(AndP.class)){
            connectivePredicate = new AndJanusPredicate();
        } else if (predicate.getClass().isAssignableFrom(OrP.class)){
            connectivePredicate = new OrJanusPredicate();
        } else {
            throw new IllegalArgumentException("Itineris does not support the given predicate: " + predicate);
        }
        return connectivePredicate;
    }

    public static List<Object> convert(final ConnectiveP<?> predicate, final ConnectiveJanusPredicate connectivePredicate) {
        final List<Object> toReturn = new ArrayList<>();
        for (final P<?> p : predicate.getPredicates()){
            if (p instanceof ConnectiveP) {
                final ConnectiveJanusPredicate subPredicate = instanceConnectiveJanusPredicate(p);
                toReturn.add(convert((ConnectiveP<?>)p, subPredicate));
                connectivePredicate.add(subPredicate);
            } else if (p.getBiPredicate() instanceof Text) {
                Text text = (Text) p.getBiPredicate();
                connectivePredicate.add(new TinkerPopTextWrappingPredicate(text));
                toReturn.add(p.getValue());
            } else {
                connectivePredicate.add(ItinerisPredicateUtils.convert(p.getBiPredicate()));
                toReturn.add(p.getValue());
            }
        }
        return toReturn;
    }
}
