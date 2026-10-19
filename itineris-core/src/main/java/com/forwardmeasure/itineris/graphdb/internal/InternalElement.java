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

import com.forwardmeasure.itineris.core.ItinerisElement;
import com.forwardmeasure.itineris.core.ItinerisTransaction;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

/**
 * Internal Element interface adding methods that should only be used by Itineris
 *
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public interface InternalElement extends ItinerisElement {

    /**
     * Returns this element in the context of the current transaction.
     *
     * @return
     */
    InternalElement it();

    /**
     * Returns the transaction to which the element is currently bound or should be refreshed into
     * @return
     */
    StandardItinerisTx tx();


    @Override
    default ItinerisTransaction graph() {
        return tx();
    }

    void setId(Object id);

    /**
     * @see ElementLifeCycle
     * @return The lifecycle of this element
     */
    byte getLifeCycle();

    /**
     * Whether this element is invisible and should only be returned to queries that explicitly ask for invisible elements.
     * @return
     */
    boolean isInvisible();

    Object getCompareId();
}
