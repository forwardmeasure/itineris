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

package com.forwardmeasure.itineris.graphdb.configuration.validator;

import com.forwardmeasure.itineris.core.ItinerisException;
import com.forwardmeasure.itineris.graphdb.configuration.ItinerisConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Validator tests for backward compatibility with Titan
 */
public class CompatibilityValidatorTest {

    @Test
    public void shouldThrowExceptionOnNullVersion() {
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            CompatibilityValidator.validateBackwardCompatibilityWithTitan(null, "");
        });
        assertEquals("Itineris version nor Titan compatibility have not been initialized",
            illegalArgumentException.getMessage());
    }

    @Test
    public void shouldThrowExceptionOnTitanIncompatibleVersion() {
        assertThrows(ItinerisException.class, () -> {
            CompatibilityValidator.validateBackwardCompatibilityWithTitan("not_compatible", "");
        });
    }

    @Test
    public void shouldPassOnItinerisIdStoreName() {
        CompatibilityValidator.validateBackwardCompatibilityWithTitan(
            ItinerisConstants.TITAN_COMPATIBLE_VERSIONS.get(0),
            ItinerisConstants.ITINERIS_ID_STORE_NAME);
    }

    @Test
    public void shouldPassOnTitanIdStoreName() {
        CompatibilityValidator.validateBackwardCompatibilityWithTitan(
            ItinerisConstants.TITAN_COMPATIBLE_VERSIONS.get(0),
            ItinerisConstants.TITAN_ID_STORE_NAME);
    }

    @Test
    public void shouldThrowExceptionOnIncompatibleIdStoreName() {
        expectIncompatibleIdStoreNameException(() -> {
            CompatibilityValidator.validateBackwardCompatibilityWithTitan(
                ItinerisConstants.TITAN_COMPATIBLE_VERSIONS.get(0), "not_compatible");
        });
    }

    @Test
    public void shouldThrowExceptionOnNullIdStoreName() {
        expectIncompatibleIdStoreNameException(() -> {
            CompatibilityValidator.validateBackwardCompatibilityWithTitan(
                ItinerisConstants.TITAN_COMPATIBLE_VERSIONS.get(0), null);
        });
    }

    private void expectIncompatibleIdStoreNameException(Executable executable){
        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, executable);
        assertEquals("ID store for Titan compatibility has not been initialized to: " +
                ItinerisConstants.TITAN_ID_STORE_NAME, illegalArgumentException.getMessage());
    }

}
