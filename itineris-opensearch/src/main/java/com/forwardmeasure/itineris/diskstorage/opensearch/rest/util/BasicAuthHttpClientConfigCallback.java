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

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.opensearch.client.RestClientBuilder.HttpClientConfigCallback;

import com.google.common.base.Preconditions;

public class BasicAuthHttpClientConfigCallback implements HttpClientConfigCallback {

    private final CredentialsProvider credentialsProvider;

    public BasicAuthHttpClientConfigCallback(
            final String realm,
            final String username,
            final String password) {

        Preconditions.checkArgument(
                StringUtils.isNotBlank(username),
                "HTTP Basic Authentication: username must be provided");
        Preconditions.checkArgument(
                StringUtils.isNotBlank(password),
                "HTTP Basic Authentication: password must be provided");

        final BasicCredentialsProvider provider = new BasicCredentialsProvider();

        final AuthScope authScope;
        if (StringUtils.isNotBlank(realm)) {
            authScope = new AuthScope(null, null, -1, realm, null);
        } else {
            authScope = new AuthScope(null, null, -1, null, null);
        }

        provider.setCredentials(
                authScope,
                new UsernamePasswordCredentials(username, password.toCharArray()));

        this.credentialsProvider = provider;
    }

    @Override
    public HttpAsyncClientBuilder customizeHttpClient(final HttpAsyncClientBuilder httpClientBuilder) {
        return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
    }
}
