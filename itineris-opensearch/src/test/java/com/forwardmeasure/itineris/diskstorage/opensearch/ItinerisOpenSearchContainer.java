// Copyright 2024 Itineris Authors
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

import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex.BULK_REFRESH;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex.INTERFACE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_HOSTS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_PORT;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.DockerImageName;

import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.github.dockerjava.api.command.InspectContainerResponse;

public class ItinerisOpenSearchContainer extends OpenSearchContainer<ItinerisOpenSearchContainer> {

    private static final Logger logger = LoggerFactory.getLogger(ItinerisOpenSearchContainer.class);
    private static final int OPENSEARCH_PORT = 9200;
    private static final String DEFAULT_VERSION = "2.19.5";
    private static final String DEFAULT_IMAGE = "opensearchproject/opensearch";
    private static final String COMPATIBLE_IMAGE_NAME = "opensearchproject/opensearch";

    private static DockerImageName getDockerImageNameInternal() {
        return DockerImageName
                .parse(DEFAULT_IMAGE + ":" + DEFAULT_VERSION)
                .asCompatibleSubstituteFor(COMPATIBLE_IMAGE_NAME);
    }

    public ItinerisOpenSearchContainer() {
        super(getDockerImageNameInternal());

        // Start with only the minimal environment
        withEnv("discovery.type", "single-node");
        withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m");

        // Let module defaults handle wait strategy
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        logger.info("Container started: {}:{}, creating default index template", getHostname(), getPort());
        createDefaultIndexTemplate();
        logger.info("Container started: {}:{}, done creating default index template", getHostname(), getPort());
    }

    public String getHostname() {
        return getHost();
    }

    public Integer getPort() {
        return getMappedPort(OPENSEARCH_PORT);
    }

    public ModifiableConfiguration setConfiguration(
            final ModifiableConfiguration config,
            final String... indexBackends) {

        for (String indexBackend : indexBackends) {
            config.set(INTERFACE, OpenSearchSetup.REST_CLIENT.toString(), indexBackend);
            config.set(INDEX_HOSTS, new String[] { getHostname() }, indexBackend);
            config.set(INDEX_PORT, getPort(), indexBackend);
            config.set(BULK_REFRESH, "wait_for", indexBackend);
        }

        return config;
    }

    public boolean indexExists(final String name) throws IOException {
        HttpHost host;
        try {
            host = HttpHost.create("http://" + getHostname() + ":" + getPort());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to connect to default OpenSearch engine", e);
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
                CloseableHttpResponse response = httpClient.execute(host, new HttpHead("/" + name))) {
            return response.getCode() == 200;
        }
    }

    private void createDefaultIndexTemplate() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            final HttpHost host = HttpHost.create("http://" + getHostname() + ":" + getPort());
            final HttpPut request = new HttpPut("/_index_template/default");
            request.addHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(
                    """
                            {
                              "index_patterns": ["*"],
                              "template": {
                                "settings": {
                                  "number_of_shards": 1,
                                  "number_of_replicas": 0
                                }
                              }
                            }
                            """));

            try (CloseableHttpResponse response = client.execute(host, request)) {
                final int code = response.getCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException(
                            "Failed to create default OpenSearch index template. HTTP status: " + code);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create default OpenSearch index template", e);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to create default OpenSearch index template", e);
        }
    }

}
