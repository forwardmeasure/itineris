// Copyright 2017 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.predicate;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David Clement (david.clement90@laposte.net)
 */
public class ConnectiveItinerisP extends P<Object>{

    private static final long serialVersionUID = 1737489543643777182L;

    public ConnectiveItinerisP(ConnectiveJanusPredicate biPredicate, List<Object> value) {
        super(biPredicate, value);
    }
    @Override
    public String toString() {
        return toString((ConnectiveJanusPredicate) this.getBiPredicate(), this.getValue()).toString();
    }

    private StringBuilder toString(final ItinerisPredicate predicate, final Object value) {
        final StringBuilder toReturn = new StringBuilder();
        if (!(predicate instanceof ConnectiveJanusPredicate)) {
            toReturn.append(predicate);
            if (value != null) {
                toReturn.append("(").append(value).append(")");
            }
            return toReturn;
        }
        final ConnectiveJanusPredicate connectivePredicate = (ConnectiveJanusPredicate) predicate;
        final List<Object> values = null == value ? new ArrayList<>() : (List<Object>) value;
        if (connectivePredicate.size() == 1) {
            return toString(connectivePredicate.get(0), values.get(0));
        }
        if (predicate instanceof AndJanusPredicate){
            toReturn.append("and(");
        } else if (predicate instanceof OrJanusPredicate){
            toReturn.append("or(");
        } else {
            throw new IllegalArgumentException("Itineris does not support the given predicate: " + predicate);
        }
        final Iterator<Object> itValues = values.iterator();
        toReturn.append(connectivePredicate.stream().map(p -> toString(p, itValues.next())).collect(Collectors.joining(", "))).append(")");
        return toReturn;
    }
}
