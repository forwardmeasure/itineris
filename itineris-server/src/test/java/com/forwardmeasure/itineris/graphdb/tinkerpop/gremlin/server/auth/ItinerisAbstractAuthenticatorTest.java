// Copyright 2019 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.tinkerpop.gremlin.server.auth;

import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.CredentialGraphTokens;
import org.apache.tinkerpop.gremlin.groovy.jsr223.dsl.credential.CredentialTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.easymock.EasyMockSupport;
import com.forwardmeasure.itineris.StorageSetup;
import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.PropertyKey;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.SchemaStatus;
import com.forwardmeasure.itineris.graphdb.database.management.ManagementSystem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ItinerisAbstractAuthenticatorTest extends EasyMockSupport {

    public abstract ItinerisAbstractAuthenticator createAuthenticator();

    public abstract MockedItinerisAuthenticatorFactory mockedAuthenticatorFactory();

    public abstract ConfigBuilder configBuilder();

    @Test
    public void testSetupNullConfig() {
        final ItinerisAbstractAuthenticator authenticator = createAuthenticator();

        assertThrows(IllegalArgumentException.class, () -> authenticator.setup(null));
    }

    @Test
    public void testSetupNoCredDb() {
        final ItinerisAbstractAuthenticator authenticator = createAuthenticator();

        assertThrows(IllegalStateException.class, () -> authenticator.setup(new HashMap<>()));
    }

    @Test
    public void testSetupNoUserDefault() {
        final ItinerisAbstractAuthenticator authenticator = createAuthenticator();
        final Map<String, Object> configMap = configBuilder().defaultPassword("pass").create();

        assertThrows(IllegalStateException.class, () -> authenticator.setup(configMap));
    }

    @Test
    public void testSetupNoPassDefault() {
        final ItinerisAbstractAuthenticator authenticator = createAuthenticator();
        final Map<String, Object> configMap = configBuilder().defaultUser("user").create();

        assertThrows(IllegalStateException.class, () -> authenticator.setup(configMap));
    }

    @Test
    public void testSetupEmptyCredentialsGraphCreateDefaultUser() {
        final String defaultUser = "user";
        final String defaultPassword = "pass";
        final Map<String, Object> configMap =
            configBuilder().defaultUser(defaultUser).defaultPassword(defaultPassword).create();
        final Itineris graph = StorageSetup.getInMemoryGraph();
        final ItinerisAbstractAuthenticator authenticator = createInitializedAuthenticator(configMap, graph);

        authenticator.setup(configMap);

        CredentialTraversalSource credentialSource = graph.traversal(CredentialTraversalSource.class);
        List<Vertex> users = credentialSource.users(defaultUser).toList();
        assertEquals(1, users.size());
    }

    @Test
    public void testSetupEmptyCredentialsGraphCreateUsernamePropertyKey() {
        final Map<String, Object> configMap = createConfig();
        final Itineris graph = StorageSetup.getInMemoryGraph();
        final ItinerisAbstractAuthenticator authenticator = createInitializedAuthenticator(configMap, graph);

        authenticator.setup(configMap);

        final ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
        final PropertyKey username = mgmt.getPropertyKey(CredentialGraphTokens.PROPERTY_USERNAME);
        assertEquals(String.class, username.dataType());
        assertEquals(Cardinality.SINGLE, username.cardinality());
    }

    @Test
    public void testSetupEmptyCredentialsGraphCreateUsernameIndex() {
        final Map<String, Object> configMap = createConfig();
        final Itineris graph = StorageSetup.getInMemoryGraph();
        final ItinerisAbstractAuthenticator authenticator = createInitializedAuthenticator(configMap, graph);

        authenticator.setup(configMap);

        final ManagementSystem mgmt = (ManagementSystem) graph.openManagement();
        assertTrue(mgmt.containsGraphIndex(ItinerisSimpleAuthenticator.USERNAME_INDEX_NAME));
        final ItinerisIndex index = mgmt.getGraphIndex(ItinerisSimpleAuthenticator.USERNAME_INDEX_NAME);
        final PropertyKey username = mgmt.getPropertyKey(CredentialGraphTokens.PROPERTY_USERNAME);
        assertEquals(index.getIndexStatus(username), SchemaStatus.ENABLED);
    }

    @Test
    public void testSetupCredentialsGraphWithDefaultUserDontCreateUserAgain() {
        final String defaultUser = "user";
        final String defaultPassword = "pass";
        final Map<String, Object> configMap =
            configBuilder().defaultUser(defaultUser).defaultPassword(defaultPassword).create();
        final Itineris graph = StorageSetup.getInMemoryGraph();
        ItinerisAbstractAuthenticator authenticator = createInitializedAuthenticator(configMap, graph);
        authenticator.setup(configMap);
        authenticator = createInitializedAuthenticator(configMap, graph);

        // set up again:
        authenticator.setup(configMap);

        CredentialTraversalSource credentialSource = graph.traversal(CredentialTraversalSource.class);
        List<Vertex> users = credentialSource.users(defaultUser).toList();
        assertEquals(1, users.size());
    }

    protected ItinerisAbstractAuthenticator createInitializedAuthenticator(final Map<String, Object> config,
                                                                           final Itineris graph) {
        return mockedAuthenticatorFactory().createInitializedAuthenticator(config, graph);
    }

    private Map<String, Object> createConfig() {
        return configBuilder().defaultUser("testUser").defaultPassword("testPassword").create();
    }
}
