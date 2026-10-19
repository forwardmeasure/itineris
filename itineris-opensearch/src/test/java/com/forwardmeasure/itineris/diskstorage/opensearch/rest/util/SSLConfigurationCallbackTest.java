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

package com.forwardmeasure.itineris.diskstorage.opensearch.rest.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SSLConfigurationCallbackTest {

    @Mock
    private SSLContextBuilder sslContextBuilderMock;

    @Mock
    private SSLContext sslContextMock;

    @Mock
    private HttpAsyncClientBuilder httpAsyncClientBuilderMock;

    @BeforeEach
    public void setUp() throws Exception {
        doReturn(sslContextMock).when(sslContextBuilderMock).build();
        when(httpAsyncClientBuilderMock.setConnectionManager(any(PoolingAsyncClientConnectionManager.class)))
                .thenReturn(httpAsyncClientBuilderMock);
    }

    @Test
    public void testSSLContextInitTrustStoreOnly() throws Exception {
        final String trustStoreFile = "/a/b/c/truststore.jks";
        final String trustStorePassword = "trustStorePassword";

        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .withTrustStore(trustStoreFile, trustStorePassword)
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadTrustMaterial(
                eq(new File(trustStoreFile)),
                eq(trustStorePassword.toCharArray()),
                isNull());
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testSSLContextInitKeyStoreOnly() throws Exception {
        final String keyStoreFile = "/a/b/c/keystore.jks";
        final String keyStorePassword = "keyStorePassword";
        final String keyPassword = "keyPassword";

        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .withKeyStore(keyStoreFile, keyStorePassword, keyPassword)
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadKeyMaterial(
                eq(new File(keyStoreFile)),
                eq(keyStorePassword.toCharArray()),
                eq(keyPassword.toCharArray()));
        verify(sslContextBuilderMock).loadTrustMaterial((TrustStrategy) null);
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testSSLContextInitKeyAndTrustStore() throws Exception {
        final String trustStoreFile = "/a/b/c/truststore.jks";
        final String trustStorePassword = "trustStorePassword";
        final String keyStoreFile = "/a/b/c/keystore.jks";
        final String keyStorePassword = "keyStorePassword";
        final String keyPassword = "keyPassword";

        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .withTrustStore(trustStoreFile, trustStorePassword)
                .withKeyStore(keyStoreFile, keyStorePassword, keyPassword)
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadTrustMaterial(
                eq(new File(trustStoreFile)),
                eq(trustStorePassword.toCharArray()),
                isNull());
        verify(sslContextBuilderMock).loadKeyMaterial(
                eq(new File(keyStoreFile)),
                eq(keyStorePassword.toCharArray()),
                eq(keyPassword.toCharArray()));
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testDisableHostNameVerificationDefaultOff() throws Exception {
        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadTrustMaterial((TrustStrategy) null);
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testDisableHostNameVerification() throws Exception {
        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .disableHostNameVerification()
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadTrustMaterial((TrustStrategy) null);
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testAllowSelfSignedCertsDefaultOff() throws Exception {
        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadTrustMaterial((TrustStrategy) null);
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testAllowSelfSignedCerts() throws Exception {
        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .allowSelfSignedCertificates()
                .build();

        final HttpAsyncClientBuilder result = assertDoesNotThrow(
                () -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        assertSame(httpAsyncClientBuilderMock, result);

        verify(sslContextBuilderMock).loadTrustMaterial(any(TrustSelfSignedStrategy.class));
        verify(sslContextBuilderMock).build();
        verify(httpAsyncClientBuilderMock).setConnectionManager(any(PoolingAsyncClientConnectionManager.class));

        verifyNoMoreInteractions(sslContextBuilderMock, httpAsyncClientBuilderMock);
    }

    @Test
    public void testAllowSelfSignedCertsDoesNotUseDefaultTrustMaterial() throws Exception {
        final SSLConfigurationCallback cb = SSLConfigurationCallback.Builder
                .createCustom(sslContextBuilderMock)
                .allowSelfSignedCertificates()
                .build();

        assertDoesNotThrow(() -> cb.customizeHttpClient(httpAsyncClientBuilderMock));

        verify(sslContextBuilderMock, never()).loadTrustMaterial((TrustStrategy) null);
        verify(sslContextBuilderMock).loadTrustMaterial(any(TrustSelfSignedStrategy.class));
    }
}
