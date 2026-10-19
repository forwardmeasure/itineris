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
package com.forwardmeasure.itineris.diskstorage.opensearch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class VanillaOpenSearchContainerSmokeTest {
    private static final Logger logger = LoggerFactory.getLogger(VanillaOpenSearchContainerSmokeTest.class);

    @Test
    void shouldStartAndStopVanillaOpenSearchContainer() {
        DockerImageName image = DockerImageName
                .parse("opensearchproject/opensearch:2.19.5"); // or whatever version you want

        try (OpenSearchContainer<?> container = new OpenSearchContainer<>(image)) {
            logger.info("Starting vanilla OpenSearchContainer");
            container.start();
            logger.info("Vanilla OpenSearchContainer started on {}:{}",
                    container.getHost(), container.getFirstMappedPort());

            assertTrue(container.isRunning(), "Vanilla OpenSearch container should be running");

            logger.info("Test completed, container will be stopped/removed by try-with-resources");
        } catch (ContainerLaunchException e) {
            logger.error("Failed to start vanilla OpenSearchContainer", e);
            throw e;
        }
    }
}
