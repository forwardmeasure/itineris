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

import java.io.IOException;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;

/**
 * Base class for custom REST client authenticators.
 */
public abstract class RestClientAuthenticatorBase implements RestClientAuthenticator {

    @Override
    public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
        return httpClientBuilder;
    }

    @Override
    public RequestConfig.Builder customizeRequestConfig(RequestConfig.Builder requestConfigBuilder) {
        return requestConfigBuilder;
    }

    @Override
    public void init() throws IOException {
        // does nothing
    }
}
