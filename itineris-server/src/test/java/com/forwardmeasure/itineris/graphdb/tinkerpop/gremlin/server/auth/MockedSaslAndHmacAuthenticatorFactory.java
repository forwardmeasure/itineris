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

import com.forwardmeasure.itineris.core.Itineris;

import java.util.Map;

import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;

public class MockedSaslAndHmacAuthenticatorFactory implements MockedItinerisAuthenticatorFactory {

    private final MockedHmacAuthenticatorFactory hmacFactory = new MockedHmacAuthenticatorFactory();
    private final MockedSimpleAuthenticatorFactory saslFactory = new MockedSimpleAuthenticatorFactory();

    public ItinerisAbstractAuthenticator createInitializedAuthenticator(final Map<String, Object> config,
                                                                             final Itineris graph) {
        final ItinerisSimpleAuthenticator jsa =
            (ItinerisSimpleAuthenticator) saslFactory.createInitializedAuthenticator(config, graph);
        final HMACAuthenticator hmacAuthenticator =
            (HMACAuthenticator) hmacFactory.createInitializedAuthenticator(config, graph);

        SaslAndHMACAuthenticator authenticator = createMockBuilder(SaslAndHMACAuthenticator.class)
            .addMockedMethod("openGraph")
            .addMockedMethod("createSimpleAuthenticator")
            .addMockedMethod("createHMACAuthenticator")
            .createMock();

        expect(authenticator.createSimpleAuthenticator()).andReturn(jsa);
        expect(authenticator.createHMACAuthenticator()).andReturn(hmacAuthenticator);
        expect(authenticator.openGraph(isA(String.class))).andReturn(graph);
        replay(authenticator);
        return authenticator;
    }
}
