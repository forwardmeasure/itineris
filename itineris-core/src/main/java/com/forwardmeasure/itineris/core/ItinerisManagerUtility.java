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

package com.forwardmeasure.itineris.core;

import com.forwardmeasure.itineris.graphdb.management.ItinerisManager;

/**
 * This class allows an access point to the {@link ItinerisManager}
 * Singleton without throwing a {@link NoClassDefFoundError}
 * if the server has not been configured to use the org.apache.tinkerpop:gremlin-server
 * dependency, since this dependency is optional.
 */
public class ItinerisManagerUtility {
    public static ItinerisManager getInstance() {
        try {
            return ItinerisManager.getInstance();
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }
}
