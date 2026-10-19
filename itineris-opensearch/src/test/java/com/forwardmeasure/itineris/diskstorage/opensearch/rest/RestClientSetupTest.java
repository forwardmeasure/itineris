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

package com.forwardmeasure.itineris.diskstorage.opensearch.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.RestClientBuilder.HttpClientConfigCallback;

import com.forwardmeasure.itineris.diskstorage.StandardIndexProvider;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.backend.CommonsConfiguration;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchClient;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchIndex;
import com.forwardmeasure.itineris.diskstorage.opensearch.rest.util.HttpAuthTypes;
import com.forwardmeasure.itineris.diskstorage.opensearch.rest.util.RestClientAuthenticator;
import com.forwardmeasure.itineris.diskstorage.opensearch.rest.util.SSLConfigurationCallback;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.util.system.ConfigurationUtil;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

@ExtendWith(MockitoExtension.class)
public class RestClientSetupTest {

    private static final String INDEX_NAME = "junit";
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final String OPENSEARCH_HOST_01 = "es-host-01";
    private static final String OPENSEARCH_HOST_02 = "es-host-02";
    private static final int OPENSEARCH_PORT = 8080;

    // Derive the config key prefix from the namespace constant — compile-safe
    private static final String OS_NS = OpenSearchIndex.OPENSEARCH_NS.getName();
    private static final String BACKEND = StandardIndexProvider.OPENSEARCH.name();

    private static final int OPENSEARCH_SCROLL_KA = OpenSearchIndex.OPENSEARCH_SCROLL_KEEP_ALIVE.getDefaultValue() * 2;
    private static final String OPENSEARCH_BULK_REFRESH = String
            .valueOf(!Boolean.parseBoolean(OpenSearchIndex.BULK_REFRESH.getDefaultValue()));
    private static final Integer RETRY_ON_CONFLICT = OpenSearchIndex.RETRY_ON_CONFLICT.getDefaultValue();
    private static final Integer RETRY_LIMIT = OpenSearchIndex.RETRY_LIMIT.getDefaultValue();
    private static final Long RETRY_INITIAL_WAIT = OpenSearchIndex.RETRY_INITIAL_WAIT.getDefaultValue();
    private static final Long RETRY_MAX_WAIT = OpenSearchIndex.RETRY_MAX_WAIT.getDefaultValue();
    private static final AtomicInteger instanceCount = new AtomicInteger();

    @Captor
    ArgumentCaptor<HttpHost[]> hostListCaptor;
    @Captor
    ArgumentCaptor<Integer> scrollKACaptor;
    @Captor
    ArgumentCaptor<Integer> retryAttemptLimitCaptor;
    @Captor
    ArgumentCaptor<Long> retryInitialWaitCaptor;
    @Captor
    ArgumentCaptor<Long> retryMaxWaitCaptor;
    @Captor
    ArgumentCaptor<Set<Integer>> retryErrorCodesCaptor;
    @Captor
    ArgumentCaptor<Integer> bulkChunkSerializedLimitCaptor;

    @Spy
    private RestClientSetup restClientSetup = new RestClientSetup();
    @Mock
    private RestClient restClientMock;
    @Mock
    private RestOpenSearchClient restSearchClientMock;
    @Mock
    private SSLContext sslContextMock;
    @Mock
    private RestClientBuilder restClientBuilderMock;

    @BeforeEach
    public void setUp() {
        doReturn(restClientMock).when(restClientBuilderMock).build();
    }

    private OpenSearchClient baseConfigTest(final Map<String, String> extraConfigValues) throws Exception {
        final CommonsConfiguration cc = new CommonsConfiguration(ConfigurationUtil.createBaseConfiguration());
        cc.set("index." + INDEX_NAME + ".backend", BACKEND);
        cc.set("index." + INDEX_NAME + "." + OS_NS + ".interface", "REST_CLIENT");
        for (Map.Entry<String, String> me : extraConfigValues.entrySet()) {
            cc.set(me.getKey(), me.getValue());
        }
        final ModifiableConfiguration config = new ModifiableConfiguration(
                GraphDatabaseConfiguration.ROOT_NS,
                cc,
                BasicConfiguration.Restriction.NONE);
        doReturn(restClientBuilderMock).when(restClientSetup).getRestClientBuilder(any());
        doReturn(restSearchClientMock).when(restClientSetup).getSearchClient(
                any(RestClient.class),
                anyInt(),
                anyInt(),
                anySet(),
                anyLong(),
                anyLong(),
                anyInt());
        return restClientSetup.connect(config.restrictTo(INDEX_NAME));
    }

    private List<HttpHost[]> baseHostsConfigTest(final Map<String, String> extraConfigValues) throws Exception {
        final OpenSearchClient elasticClient = baseConfigTest(
                ImmutableMap.<String, String>builder().putAll(extraConfigValues).build());
        assertSame(restSearchClientMock, elasticClient);
        verify(restClientSetup).getRestClient(hostListCaptor.capture(), any(Configuration.class));
        return hostListCaptor.getAllValues();
    }

    @Test
    public void testConnectBasicHttpConfigurationSingleHost() throws Exception {
        final List<HttpHost[]> hostsConfigured = baseHostsConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + ".hostname", OPENSEARCH_HOST_01)
                        .build());
        assertNotNull(hostsConfigured);
        assertEquals(1, hostsConfigured.size());
        final HttpHost host0 = hostsConfigured.get(0)[0];
        assertEquals(OPENSEARCH_HOST_01, host0.getHostName());
        assertEquals(SCHEME_HTTP, host0.getSchemeName());
        assertEquals(OpenSearchIndex.HOST_PORT_DEFAULT, host0.getPort());
        verify(restClientSetup).getSearchClient(
                same(restClientMock),
                scrollKACaptor.capture(),
                anyInt(),
                anySet(),
                anyLong(),
                anyLong(),
                anyInt());
        assertEquals(
                OpenSearchIndex.OPENSEARCH_SCROLL_KEEP_ALIVE.getDefaultValue().intValue(),
                scrollKACaptor.getValue().intValue());
        verify(restSearchClientMock, never()).setBulkRefresh(anyString());
    }

    @Test
    public void testConnectBasicHttpConfigurationMultiHost() throws Exception {
        final List<HttpHost[]> hostsConfigured = baseHostsConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + ".hostname", OPENSEARCH_HOST_01 + "," + OPENSEARCH_HOST_02)
                        .build());
        assertNotNull(hostsConfigured);
        assertEquals(1, hostsConfigured.size());
        final HttpHost host0 = hostsConfigured.get(0)[0];
        assertEquals(OPENSEARCH_HOST_01, host0.getHostName());
        assertEquals(SCHEME_HTTP, host0.getSchemeName());
        assertEquals(OpenSearchIndex.HOST_PORT_DEFAULT, host0.getPort());
        final HttpHost host1 = hostsConfigured.get(0)[1];
        assertEquals(OPENSEARCH_HOST_02, host1.getHostName());
        assertEquals(SCHEME_HTTP, host1.getSchemeName());
        assertEquals(OpenSearchIndex.HOST_PORT_DEFAULT, host1.getPort());
        verify(restClientSetup).getSearchClient(
                same(restClientMock),
                scrollKACaptor.capture(),
                retryAttemptLimitCaptor.capture(),
                retryErrorCodesCaptor.capture(),
                retryInitialWaitCaptor.capture(),
                retryMaxWaitCaptor.capture(),
                bulkChunkSerializedLimitCaptor.capture());
        assertEquals(
                OpenSearchIndex.OPENSEARCH_SCROLL_KEEP_ALIVE.getDefaultValue().intValue(),
                scrollKACaptor.getValue().intValue());
        verify(restSearchClientMock, never()).setBulkRefresh(anyString());
    }

    @Test
    public void testConnectBasicHttpConfigurationAllOptions() throws Exception {
        final List<HttpHost[]> hostsConfigured = baseHostsConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + ".hostname", OPENSEARCH_HOST_01)
                        .put("index." + INDEX_NAME + ".port", String.valueOf(OPENSEARCH_PORT))
                        .put("index." + INDEX_NAME + "." + OS_NS + ".scroll-keep-alive",
                                String.valueOf(OPENSEARCH_SCROLL_KA))
                        .put("index." + INDEX_NAME + "." + OS_NS + ".bulk-refresh", OPENSEARCH_BULK_REFRESH)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".retry_on_conflict",
                                String.valueOf(RETRY_ON_CONFLICT))
                        .put("index." + INDEX_NAME + "." + OS_NS + ".retry-limit", String.valueOf(RETRY_LIMIT))
                        .put("index." + INDEX_NAME + "." + OS_NS + ".retry-initial-wait",
                                String.valueOf(RETRY_INITIAL_WAIT))
                        .put("index." + INDEX_NAME + "." + OS_NS + ".retry-max-wait", String.valueOf(RETRY_MAX_WAIT))
                        .put("index." + INDEX_NAME + "." + OS_NS + ".retry-error-codes", "408,429")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".bulk-chunk-size-limit-bytes", "1000000")
                        .build());
        assertNotNull(hostsConfigured);
        assertEquals(1, hostsConfigured.size());
        final HttpHost host0 = hostsConfigured.get(0)[0];
        assertEquals(OPENSEARCH_HOST_01, host0.getHostName());
        assertEquals(SCHEME_HTTP, host0.getSchemeName());
        assertEquals(OPENSEARCH_PORT, host0.getPort());
        verify(restClientSetup).getSearchClient(
                same(restClientMock),
                scrollKACaptor.capture(),
                retryAttemptLimitCaptor.capture(),
                retryErrorCodesCaptor.capture(),
                retryInitialWaitCaptor.capture(),
                retryMaxWaitCaptor.capture(),
                bulkChunkSerializedLimitCaptor.capture());
        assertEquals(OPENSEARCH_SCROLL_KA, scrollKACaptor.getValue().intValue());
        assertEquals(RETRY_LIMIT, retryAttemptLimitCaptor.getValue().intValue());
        assertEquals(Stream.of(408, 429).collect(Collectors.toSet()), retryErrorCodesCaptor.getValue());
        assertEquals(RETRY_INITIAL_WAIT, retryInitialWaitCaptor.getValue().longValue());
        assertEquals(RETRY_MAX_WAIT, retryMaxWaitCaptor.getValue().longValue());
        assertEquals(1_000_000, bulkChunkSerializedLimitCaptor.getValue().intValue());
        verify(restSearchClientMock).setBulkRefresh(eq(OPENSEARCH_BULK_REFRESH));
        verify(restSearchClientMock).setRetryOnConflict(eq(RETRY_ON_CONFLICT));
    }

    @Test
    public void testConnectBasicHttpsConfigurationSingleHost() throws Exception {
        final List<HttpHost[]> hostsConfigured = baseHostsConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + ".hostname", OPENSEARCH_HOST_01)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.enabled", "true")
                        .build());
        assertNotNull(hostsConfigured);
        assertEquals(1, hostsConfigured.size());
        final HttpHost host0 = hostsConfigured.get(0)[0];
        assertEquals(OPENSEARCH_HOST_01, host0.getHostName());
        assertEquals(SCHEME_HTTPS, host0.getSchemeName());
        assertEquals(OpenSearchIndex.HOST_PORT_DEFAULT, host0.getPort());
        verify(restClientSetup).getSearchClient(
                same(restClientMock),
                scrollKACaptor.capture(),
                anyInt(),
                anySet(),
                anyLong(),
                anyLong(),
                anyInt());
        assertEquals(
                OpenSearchIndex.OPENSEARCH_SCROLL_KEEP_ALIVE.getDefaultValue().intValue(),
                scrollKACaptor.getValue().intValue());
        verify(restSearchClientMock, never()).setBulkRefresh(anyString());
        verify(restSearchClientMock, times(1)).setRetryOnConflict(null);
    }

    private HttpClientConfigCallback authTestBase(final Map<String, String> extraConfigValues) throws Exception {
        baseConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + ".backend", BACKEND)
                        .put("index." + INDEX_NAME + ".hostname", OPENSEARCH_HOST_01)
                        .putAll(extraConfigValues)
                        .build());
        final ArgumentCaptor<HttpClientConfigCallback> hcccCaptor = ArgumentCaptor
                .forClass(HttpClientConfigCallback.class);
        verify(restClientBuilderMock).setHttpClientConfigCallback(hcccCaptor.capture());
        final HttpClientConfigCallback hccc = hcccCaptor.getValue();
        assertNotNull(hccc);
        return hccc;
    }

    private RestClientBuilder.RequestConfigCallback configCallbackTestBase(
            final Map<String, String> extraConfigValues) throws Exception {
        baseConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + ".backend", BACKEND)
                        .put("index." + INDEX_NAME + ".hostname", OPENSEARCH_HOST_01)
                        .putAll(extraConfigValues)
                        .build());
        final ArgumentCaptor<RestClientBuilder.RequestConfigCallback> rccCaptor = ArgumentCaptor
                .forClass(RestClientBuilder.RequestConfigCallback.class);
        verify(restClientBuilderMock).setRequestConfigCallback(rccCaptor.capture());
        final RestClientBuilder.RequestConfigCallback rcc = rccCaptor.getValue();
        assertNotNull(rcc);
        return rcc;
    }

    private CredentialsProvider basicAuthTestBase(
            final Map<String, String> extraConfigValues,
            final String realm,
            final String username,
            final String password) throws Exception {
        final HttpClientConfigCallback hccc = authTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".interface", "REST_CLIENT")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.type", HttpAuthTypes.BASIC.toString())
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.basic.username", username)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.basic.password", password)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.basic.realm", realm)
                        .putAll(extraConfigValues)
                        .build());
        final HttpAsyncClientBuilder hacb = mock(HttpAsyncClientBuilder.class);
        doReturn(hacb).when(hacb).setDefaultCredentialsProvider(any());
        hccc.customizeHttpClient(hacb);
        final ArgumentCaptor<BasicCredentialsProvider> cpCaptor = ArgumentCaptor
                .forClass(BasicCredentialsProvider.class);
        verify(hacb).setDefaultCredentialsProvider(cpCaptor.capture());
        final CredentialsProvider cp = cpCaptor.getValue();
        assertNotNull(cp);
        return cp;
    }

    @Test
    public void testHttpBasicAuthConfiguration() throws Exception {
        final String testRealm = "testRealm";
        final String testUser = "testUser";
        final String testPassword = "testPassword";
        final CredentialsProvider cp = basicAuthTestBase(
                ImmutableMap.<String, String>builder().build(),
                testRealm,
                testUser,
                testPassword);
        final Credentials credentials = cp.getCredentials(
                new AuthScope(null, null, -1, testRealm, null),
                null);
        assertNotNull(credentials);
        assertEquals(testUser, credentials.getUserPrincipal().getName());
        assertEquals(testPassword, new String(credentials.getPassword()));
    }

    @Test
    public void testConnectAndSocketTimeout() throws Exception {
        final RestClientBuilder.RequestConfigCallback rcc = configCallbackTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".connect-timeout", "5000")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".socket-timeout", "60000")
                        .build());
        final RequestConfig.Builder rccb = mock(RequestConfig.Builder.class);
        when(rccb.setConnectTimeout(any(Timeout.class))).thenReturn(rccb);
        when(rccb.setResponseTimeout(any(Timeout.class))).thenReturn(rccb);
        final ArgumentCaptor<Timeout> connectArgumentCaptor = ArgumentCaptor.forClass(Timeout.class);
        final ArgumentCaptor<Timeout> responseArgumentCaptor = ArgumentCaptor.forClass(Timeout.class);
        rcc.customizeRequestConfig(rccb);
        verify(rccb).setConnectTimeout(connectArgumentCaptor.capture());
        verify(rccb).setResponseTimeout(responseArgumentCaptor.capture());
        assertEquals(5000, connectArgumentCaptor.getValue().toMilliseconds());
        assertEquals(60000, responseArgumentCaptor.getValue().toMilliseconds());
    }

    @Test
    public void testCustomAuthenticator() throws Exception {
        final String uniqueInstanceKey = String.valueOf(instanceCount.getAndIncrement());
        final String[] customAuthArgs = new String[] { uniqueInstanceKey, "arg1", "arg2" };
        final String serializedArgList = StringUtils.join(customAuthArgs, ',');
        final HttpClientConfigCallback hccc = authTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".interface", "REST_CLIENT")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.type", HttpAuthTypes.CUSTOM.toString())
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.custom.authenticator-class",
                                TestCustomAuthenticator.class.getName())
                        .put("index." + INDEX_NAME + "." + OS_NS + ".http.auth.custom.authenticator-args",
                                serializedArgList)
                        .build());
        verify(restClientSetup).getCustomAuthenticator(
                eq(TestCustomAuthenticator.class.getName()),
                eq(customAuthArgs));
        final TestCustomAuthenticator customAuth = TestCustomAuthenticator.instanceMap.get(uniqueInstanceKey);
        assertNotNull(customAuth);
        assertEquals(1, customAuth.numInitCalls);
        final HttpAsyncClientBuilder hacb = mock(HttpAsyncClientBuilder.class);
        hccc.customizeHttpClient(hacb);
        assertEquals(1, customAuth.customizeHttpClientHistory.size());
        assertSame(hacb, customAuth.customizeHttpClientHistory.get(0));
        assertArrayEquals(customAuthArgs, customAuth.args);
    }

    public SSLConfigurationCallback.Builder sslSettingsTestBase(
            final Map<String, String> extraConfigValues) throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = mock(SSLConfigurationCallback.Builder.class);
        doReturn(sslConfBuilderMock).when(restClientSetup).getSSLConfigurationCallbackBuilder();
        authTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".interface", "REST_CLIENT")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.enabled", "true")
                        .putAll(extraConfigValues)
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        return sslConfBuilderMock;
    }

    @Test
    public void testSSLTrustStoreSettingsOnly() throws Exception {
        final String trustStoreFile = "/a/b/c/truststore.jks";
        final String trustStorePassword = "averysecretpassword";
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.truststore.location", trustStoreFile)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.truststore.password", trustStorePassword)
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).withTrustStore(eq(trustStoreFile), eq(trustStorePassword));
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLKeyStoreSettingsOnly() throws Exception {
        final String keyStoreFile = "/a/b/c/keystore.jks";
        final String keyStorePassword = "key_store_password";
        final String keyPassword = "key_password";
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.location", keyStoreFile)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.storepassword", keyStorePassword)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.keypassword", keyPassword)
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).withKeyStore(eq(keyStoreFile), eq(keyStorePassword), eq(keyPassword));
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLKeyStoreSettingsOnlyEmptyKeyPass() throws Exception {
        final String keyStoreFile = "/a/b/c/keystore.jks";
        final String keyStorePassword = "key_store_password";
        final String keyPassword = "";
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.location", keyStoreFile)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.storepassword", keyStorePassword)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.keypassword", keyPassword)
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).withKeyStore(eq(keyStoreFile), eq(keyStorePassword), eq(keyPassword));
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLKeyStoreSettingsOnlyNoKeyPass() throws Exception {
        final String keyStoreFile = "/a/b/c/keystore.jks";
        final String keyStorePassword = "key_store_password";
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.location", keyStoreFile)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.storepassword", keyStorePassword)
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).withKeyStore(eq(keyStoreFile), eq(keyStorePassword), eq(keyStorePassword));
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLKeyAndTrustStoreSettingsOnly() throws Exception {
        final String trustStoreFile = "/a/b/c/truststore.jks";
        final String trustStorePassword = "averysecretpassword";
        final String keyStoreFile = "/a/b/c/keystore.jks";
        final String keyStorePassword = "key_store_password";
        final String keyPassword = "key_password";
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.truststore.location", trustStoreFile)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.truststore.password", trustStorePassword)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.location", keyStoreFile)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.storepassword", keyStorePassword)
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.keystore.keypassword", keyPassword)
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).withTrustStore(eq(trustStoreFile), eq(trustStorePassword));
        verify(sslConfBuilderMock).withKeyStore(eq(keyStoreFile), eq(keyStorePassword), eq(keyPassword));
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLDisableHostNameVerifier() throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.disable-hostname-verification", "true")
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).disableHostNameVerification();
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLDisableHostNameVerifierExplicitOff() throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = mock(SSLConfigurationCallback.Builder.class);
        doReturn(sslConfBuilderMock).when(restClientSetup).getSSLConfigurationCallbackBuilder();
        baseConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.enabled", "true")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.disable-hostname-verification", "false")
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock, never()).disableHostNameVerification();
    }

    @Test
    public void testSSLDisableHostNameVerifierDefaultOff() throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = mock(SSLConfigurationCallback.Builder.class);
        doReturn(sslConfBuilderMock).when(restClientSetup).getSSLConfigurationCallbackBuilder();
        baseConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.enabled", "true")
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock, never()).disableHostNameVerification();
    }

    @Test
    public void testSSLAllowSelfSignedCerts() throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = sslSettingsTestBase(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.allow-self-signed-certificates", "true")
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock).allowSelfSignedCertificates();
        verify(sslConfBuilderMock).build();
        verifyNoMoreInteractions(sslConfBuilderMock);
    }

    @Test
    public void testSSLAllowSelfSignedCertsExplicitOff() throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = mock(SSLConfigurationCallback.Builder.class);
        doReturn(sslConfBuilderMock).when(restClientSetup).getSSLConfigurationCallbackBuilder();
        baseConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.enabled", "true")
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.allow-self-signed-certificates", "false")
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock, never()).allowSelfSignedCertificates();
    }

    @Test
    public void testSSLAllowSelfSignedCertsDefaultOff() throws Exception {
        final SSLConfigurationCallback.Builder sslConfBuilderMock = mock(SSLConfigurationCallback.Builder.class);
        doReturn(sslConfBuilderMock).when(restClientSetup).getSSLConfigurationCallbackBuilder();
        baseConfigTest(
                ImmutableMap.<String, String>builder()
                        .put("index." + INDEX_NAME + "." + OS_NS + ".ssl.enabled", "true")
                        .build());
        verify(restClientSetup).getSSLConfigurationCallbackBuilder();
        verify(sslConfBuilderMock, never()).allowSelfSignedCertificates();
    }

    public static class TestCustomAuthenticator implements RestClientAuthenticator {
        private static final Map<String, TestCustomAuthenticator> instanceMap = new HashMap<>();
        private final String[] args;
        private final List<RequestConfig.Builder> customizeRequestConfigHistory = new LinkedList<>();
        private final List<HttpAsyncClientBuilder> customizeHttpClientHistory = new LinkedList<>();
        private int numInitCalls = 0;

        public TestCustomAuthenticator(final String[] args) {
            this.args = args;
            Preconditions.checkArgument(instanceMap.put(args[0], this) == null, "Non-unique key used");
        }

        @Override
        public HttpAsyncClientBuilder customizeHttpClient(final HttpAsyncClientBuilder httpClientBuilder) {
            customizeHttpClientHistory.add(httpClientBuilder);
            return httpClientBuilder;
        }

        @Override
        public RequestConfig.Builder customizeRequestConfig(final RequestConfig.Builder requestConfigBuilder) {
            customizeRequestConfigHistory.add(requestConfigBuilder);
            return requestConfigBuilder;
        }

        @Override
        public void init() throws IOException {
            numInitCalls++;
        }
    }
}
