// Copyright 2022 JanusGraph Authors
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

package com.forwardmeasure.itineris.core.opensearch;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.GRAPH_NAME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

import org.apache.commons.configuration2.MapConfiguration;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.forwardmeasure.itineris.core.AbstractConfiguredGraphFactoryTest;
import com.forwardmeasure.itineris.core.ConfiguredGraphFactory;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.schema.ItinerisManagement;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.converter.ReadConfigurationConverter;
import com.forwardmeasure.itineris.diskstorage.opensearch.ItinerisOpenSearchContainer;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.management.utils.ConfigurationManagementGraphNotEnabledException;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import com.forwardmeasure.itineris.util.system.IOUtils;

@Testcontainers
public class OpenSearchConfiguredGraphFactoryTest extends AbstractConfiguredGraphFactoryTest {
    private String indexBackendName = "search";

    @Container
    public static final ItinerisOpenSearchContainer container = new ItinerisOpenSearchContainer();

    @Override
    @Test
    public void shouldGetConfigurationManagementGraphInstance() throws ConfigurationManagementGraphNotEnabledException {
        super.shouldGetConfigurationManagementGraphInstance();
    }

    @Override
    protected MapConfiguration getManagementConfig() {
        final ModifiableConfiguration config = GraphDatabaseConfiguration.buildGraphConfiguration();
        config.set(STORAGE_BACKEND, "inmemory");
        WriteConfiguration writeConf = container.setConfiguration(config, indexBackendName).getConfiguration();
        return ReadConfigurationConverter.getInstance().convertToMapConfiguration(writeConf);
    }

    @Override
    protected MapConfiguration getTemplateConfig() {
        return getManagementConfig();
    }

    @Override
    protected MapConfiguration getGraphConfig() {
        final Map<String, Object> map = getTemplateConfig().getMap();
        map.put(GRAPH_NAME.toStringWithoutRoot(), "graph1");
        return ConfigurationUtil.loadMapConfiguration(map);
    }

    private boolean indexExists(String name) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpHost host = new HttpHost(InetAddress.getByName(container.getHostname()), container.getPort());
        final CloseableHttpResponse response = httpClient.execute(host, new HttpHead(name));
        final boolean exists = response.getCode() == 200;
        IOUtils.closeQuietly(response);
        return exists;
    }

    @Test
    public void indexNameShouldContainGraphName() throws Exception {
        final MapConfiguration graphConfig = getGraphConfig();
        final String graphName = graphConfig.getString(GRAPH_NAME.toStringWithoutRoot());

        try {
            ConfiguredGraphFactory.createConfiguration(graphConfig);

            final StandardItineris graph = (StandardItineris) ConfiguredGraphFactory.open(graphName);
            assertNotNull(graph);

            String graphIndexName = "verticesByName";
            ItinerisManagement mgmt = graph.openManagement();
            PropertyKey key = mgmt.makePropertyKey("name").dataType(String.class).make();
            mgmt.buildIndex(graphIndexName, Vertex.class).addKey(key).buildMixedIndex(indexBackendName);
            mgmt.commit();

            String expectedIndexName = graphName + "_" + graphIndexName.toLowerCase();
            assertTrue(indexExists(expectedIndexName));
        } finally {
            ConfiguredGraphFactory.removeConfiguration(graphName);
            ConfiguredGraphFactory.close(graphName);
        }
    }
}
