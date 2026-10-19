// Copyright 2017 Itineris Authors
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

// Copyright 2017 Itineris Authors
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

package com.forwardmeasure.itineris.diskstorage.opensearch.rest.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BasicAuthHttpClientConfigCallbackTest {

    private static final String HTTP_USER = "testuser";
    private static final String HTTP_PASSWORD = "testpass";
    private static final String HTTP_REALM = "testrealm";

    @Mock
    private HttpAsyncClientBuilder httpAsyncClientBuilderMock;

    @BeforeEach
    public void setUp() {
        when(httpAsyncClientBuilderMock.setDefaultCredentialsProvider(any()))
                .thenReturn(httpAsyncClientBuilderMock);
    }

    private CredentialsProvider basicAuthTestBase(final String realm) {
        final BasicAuthHttpClientConfigCallback cb = new BasicAuthHttpClientConfigCallback(realm, HTTP_USER,
                HTTP_PASSWORD);

        cb.customizeHttpClient(httpAsyncClientBuilderMock);

        final ArgumentCaptor<BasicCredentialsProvider> cpCaptor = ArgumentCaptor
                .forClass(BasicCredentialsProvider.class);

        verify(httpAsyncClientBuilderMock).setDefaultCredentialsProvider(cpCaptor.capture());

        final CredentialsProvider cp = cpCaptor.getValue();
        assertNotNull(cp);

        return cp;
    }

    @Test
    public void testSetDefaultCredentialsProviderNoRealm() {
        final CredentialsProvider cp = basicAuthTestBase("");

        // expected: matches any host / any realm
        final Credentials credentialsForRealm1 = cp
                .getCredentials(new AuthScope(null, "dummyhost1", 1234, "dummyrealm1", null), null);

        assertNotNull(credentialsForRealm1);
        assertEquals(HTTP_USER, credentialsForRealm1.getUserPrincipal().getName());
        assertEquals(HTTP_PASSWORD, new String(credentialsForRealm1.getPassword()));
    }

    @Test
    public void testSetDefaultCredentialsProviderWithRealm() {
        final CredentialsProvider cp = basicAuthTestBase(HTTP_REALM);

        // expected: matches any host in the specific realm
        final Credentials credentialsForRealm1 = cp
                .getCredentials(new AuthScope(null, "dummyhost1", 1234, HTTP_REALM, null), null);

        assertNotNull(credentialsForRealm1);
        assertEquals(HTTP_USER, credentialsForRealm1.getUserPrincipal().getName());
        assertEquals(HTTP_PASSWORD, new String(credentialsForRealm1.getPassword()));

        // ...but not in other realms
        final Credentials credentialsForRealm2 = cp
                .getCredentials(new AuthScope(null, "dummyhost1", 1234, "Not_" + HTTP_REALM, null), null);

        assertNull(credentialsForRealm2);
    }
}
