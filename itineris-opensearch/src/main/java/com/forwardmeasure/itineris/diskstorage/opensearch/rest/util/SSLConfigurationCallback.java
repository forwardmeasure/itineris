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

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.opensearch.client.RestClientBuilder.HttpClientConfigCallback;

/**
 * Use {@link Builder#create()} or
 * {@link Builder#createCustom(SSLContextBuilder)} to create an instance of this
 * callback
 *
 */
public class SSLConfigurationCallback implements HttpClientConfigCallback {

    private final String trustStoreFile;
    private final String trustStorePassword;
    private final String keyStoreFile;
    private final String keyStorePassword;
    private final String keyPassword;
    private final SSLContextBuilder sslContextBuilder;
    private final boolean disableHostNameVerification;
    private final boolean allowSelfSignedCertificates;

    private SSLConfigurationCallback(final SSLContextBuilder sslContextBuilder,
            final String trustStoreFile,
            final String trustStorePassword,
            final String keyStoreFile,
            final String keyStorePassword,
            final String keyPassword,
            final boolean disableHostNameVerification,
            final boolean allowSelfSignedCertificates) {

        this.sslContextBuilder = sslContextBuilder;

        this.trustStoreFile = trustStoreFile;
        this.trustStorePassword = trustStorePassword;

        this.keyStoreFile = keyStoreFile;
        this.keyStorePassword = keyStorePassword;
        this.keyPassword = keyPassword;

        this.disableHostNameVerification = disableHostNameVerification;
        this.allowSelfSignedCertificates = allowSelfSignedCertificates;
    }

    @Override
    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
        final SSLContext sslcontext;

        final TrustStrategy trustStrategy = allowSelfSignedCertificates ? new TrustSelfSignedStrategy() : null;

        try {
            if (StringUtils.isNotEmpty(trustStoreFile)) {
                sslContextBuilder.loadTrustMaterial(
                        new File(trustStoreFile),
                        trustStorePassword.toCharArray(),
                        trustStrategy);
            } else {
                sslContextBuilder.loadTrustMaterial(trustStrategy);
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Invalid trust store file " + trustStoreFile, e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load trust store data from " + trustStoreFile, e);
        }

        try {
            if (StringUtils.isNotEmpty(keyStoreFile)) {
                sslContextBuilder.loadKeyMaterial(
                        new File(keyStoreFile),
                        keyStorePassword.toCharArray(),
                        keyPassword.toCharArray());
            }
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new RuntimeException("Invalid key store file " + keyStoreFile, e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load key store data from " + keyStoreFile, e);
        }

        try {
            sslcontext = sslContextBuilder.build();
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException("SSL context initialization failed", e);
        }

        // Build a TlsStrategy using the SSLContext
        ClientTlsStrategyBuilder tlsBuilder = ClientTlsStrategyBuilder.create()
                .setSslContext(sslcontext)
                // preserve ALPN/TlsDetails behavior similar to OpenSearch default
                .setTlsDetailsFactory(new Factory<javax.net.ssl.SSLEngine, TlsDetails>() {
                    @Override
                    public TlsDetails create(javax.net.ssl.SSLEngine sslEngine) {
                        return new TlsDetails(sslEngine.getSession(), sslEngine.getApplicationProtocol());
                    }
                });

        if (disableHostNameVerification) {
            // only for dev/debug: disables hostname verification
            tlsBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }

        TlsStrategy tlsStrategy = tlsBuilder.buildAsync();

        // Build a connection manager with that TLS strategy
        PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setTlsStrategy(tlsStrategy)
                // you can also set max connections here if you want
                .build();

        // Attach the connection manager to the builder
        httpClientBuilder.setConnectionManager(connectionManager);

        return httpClientBuilder;
    }

    /**
     * Constructs the instance of SSLConfigurationCallback
     */
    public static class Builder {
        private final SSLContextBuilder sslContextBuilder;

        private String trustStoreFile;
        private String trustStorePassword;
        private String keyStoreFile;
        private String keyStorePassword;
        private String keyPassword;
        private boolean disableHostNameVerification;
        private boolean allowSelfSignedCertificates;

        private Builder(final SSLContextBuilder sslContextBuilder) {
            this.sslContextBuilder = sslContextBuilder;
        }

        public static Builder createCustom(final SSLContextBuilder sslContextBuilder) {
            return new Builder(sslContextBuilder);
        }

        public static Builder create() {
            return new Builder(SSLContexts.custom());
        }

        public Builder withTrustStore(final String trustStoreFile, final String trustStorePassword) {
            this.trustStoreFile = trustStoreFile;
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder withKeyStore(final String keyStoreFile, final String keyStorePassword,
                final String keyPassword) {
            this.keyStoreFile = keyStoreFile;
            this.keyStorePassword = keyStorePassword;
            this.keyPassword = keyPassword;
            return this;
        }

        public Builder disableHostNameVerification() {
            this.disableHostNameVerification = true;
            return this;
        }

        public Builder allowSelfSignedCertificates() {
            this.allowSelfSignedCertificates = true;
            return this;
        }

        public SSLConfigurationCallback build() {
            return new SSLConfigurationCallback(sslContextBuilder,
                    trustStoreFile,
                    trustStorePassword,
                    keyStoreFile,
                    keyStorePassword,
                    keyPassword,
                    disableHostNameVerification,
                    allowSelfSignedCertificates);
        }
    }
}
