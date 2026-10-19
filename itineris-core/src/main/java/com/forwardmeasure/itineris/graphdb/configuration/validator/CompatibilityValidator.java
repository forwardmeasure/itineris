// Copyright 2018 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.configuration.validator;

import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.graphdb.configuration.ItinerisConstants;
import com.google.common.base.Preconditions;

/**
 * Validator for checking backward compatibility with Titan
 */
public class CompatibilityValidator {

    private static final String INCOMPATIBLE_VERSION_EXCEPTION = "Runtime version is incompatible with current Itineris version: Itineris [%1s] vs. runtime [%2s]";

    public static void validateBackwardCompatibilityWithTitan(String version, String localConfigurationIdsStoreName) {

        Preconditions.checkArgument(version != null,
                "Itineris version nor Titan compatibility have not been initialized");

        if (!ItinerisConstants.TITAN_COMPATIBLE_VERSIONS.contains(version)) {
            throw new ItinerisException(
                    String.format(INCOMPATIBLE_VERSION_EXCEPTION, version, ItinerisConstants.VERSION));
        }

        // When connecting to a store created by Titan the ID store name will not be in
        // the
        // global configuration as it was not something which was configurable with
        // Titan.
        // So to ensure compatibility override the default to titan_ids.
        boolean localIdStoreIsDefault = ItinerisConstants.ITINERIS_GRAPH_ID_STORE_NAME
                .equals(localConfigurationIdsStoreName);

        boolean usingTitanIdStore = localIdStoreIsDefault
                || ItinerisConstants.TITAN_ID_STORE_NAME.equals(localConfigurationIdsStoreName);

        Preconditions.checkArgument(usingTitanIdStore,
                "ID store for Titan compatibility has not been initialized to: %s",
                ItinerisConstants.TITAN_ID_STORE_NAME);
    }

}
