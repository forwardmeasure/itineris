// Copyright 2024 JanusGraph Authors
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

package com.forwardmeasure.itineris.diskstorage.opensearch;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ItinerisOpenSearchContainerSmokeTest {
    private static final Logger logger = LoggerFactory.getLogger(ItinerisOpenSearchContainerSmokeTest.class);

    private static ItinerisOpenSearchContainer container;

    @BeforeAll
    static void startContainer() {
        logger.info("Starting ItinerisOpenSearchContainer for smoke test");
        container = new ItinerisOpenSearchContainer();
        container.start();
        logger.info("Container started on {}:{}",
                container.getHostname(), container.getPort());
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            logger.info("Stopping ItinerisOpenSearchContainer");
            container.stop();
            logger.info("ItinerisOpenSearchContainer stopped");
        }
    }

    @Test
    void containerShouldBeRunning() {
        logger.info("Verifying that container is running");
        // Most basic assertion: just check Testcontainers state
        org.junit.jupiter.api.Assertions.assertTrue(container.isRunning(),
                "OpenSearch container should be running");
    }
}
