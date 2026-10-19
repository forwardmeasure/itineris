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

package com.forwardmeasure.itineris.diskstorage.opensearch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.opensearch.rest.RestClientSetup;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import com.google.common.base.Preconditions;

/**
 * Create an ES {@link org.opensearch.client.RestClient} from a Itineris
 * {@link com.forwardmeasure.itineris.diskstorage.configuration.Configuration}.
 * <p>
 * Any key-value pairs under the
 * {@link com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex#OPENSEARCH_CREATE_EXTRAS_NS}
 * namespace
 * are copied into the Elasticsearch settings builder. This allows overriding
 * arbitrary
 * ES settings from within the Itineris properties file.
 * <p>
 * Assumes that an ES cluster is already running. It does not attempt to start
 * an
 * embedded ES instance. It just connects to whatever hosts are given in
 * {@link com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration#INDEX_HOSTS}.
 */
public enum OpenSearchSetup {

    /**
     * Create an ES RestClient connected to
     * {@link com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration#INDEX_HOSTS}.
     */
    REST_CLIENT {
        @Override
        public Connection connect(Configuration config) throws IOException {
            return new Connection(new RestClientSetup().connect(config));
        }
    };

    static Map<String, Object> getSettingsFromItinerisConf(Configuration config) {
        final Map<String, String> settings = ConfigurationUtil.getSettingsFromItinerisConf(config,
                OpenSearchIndex.OPENSEARCH_CREATE_EXTRAS_NS);
        if (log.isDebugEnabled()) {
            settings.forEach((key, val) -> log.debug("[ES ext.* cfg] Set {}: {}", key, val));
            log.debug("Loaded {} settings from the {} Itineris config namespace", settings.size(),
                    OpenSearchIndex.OPENSEARCH_CREATE_EXTRAS_NS);
        }
        return new HashMap<>(settings);
    }

    private static final Logger log = LoggerFactory.getLogger(OpenSearchSetup.class);

    public abstract Connection connect(Configuration config) throws IOException;

    public static class Connection {

        private final OpenSearchClient client;

        public Connection(OpenSearchClient client) {
            this.client = Preconditions.checkNotNull(client, "Unable to instantiate Elasticsearch Client object");
        }

        public OpenSearchClient getClient() {
            return client;
        }
    }
}
