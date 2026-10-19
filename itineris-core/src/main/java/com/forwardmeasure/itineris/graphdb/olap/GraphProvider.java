// Copyright 2021 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.olap;

import com.google.common.base.Preconditions;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;

public class GraphProvider {

    private StandardItineris graph = null;
    private boolean provided = false;

    public void setGraph(Itineris graph) {
        Preconditions.checkArgument(graph != null && graph.isOpen(), "Need to provide open graph");
        this.graph = (StandardItineris) graph;
        provided = true;
    }

    public void initializeGraph(Configuration config) {
        if (!provided) {
            this.graph = (StandardItineris) ItinerisFactory.open((BasicConfiguration) config);
        }
    }

    public void close() {
        if (!provided && null != graph && graph.isOpen()) {
            graph.close();
            graph = null;
        }
    }

    public boolean isProvided() {
        return provided;
    }

    public final StandardItineris get() {
        Preconditions.checkNotNull(graph);
        return graph;
    }
}
