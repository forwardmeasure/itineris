// Copyright 2019 JanusGraph Authors
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

public class MockedHmacAuthenticatorFactory implements MockedItinerisAuthenticatorFactory {

    public ItinerisAbstractAuthenticator createInitializedAuthenticator(final Map<String, Object> config,
                                                                             final Itineris graph) {
        return createInitializedAuthenticator(graph);
    }

    public HMACAuthenticator createInitializedAuthenticator(final Itineris graph) {
        final HMACAuthenticator hmacAuthenticator = createMockBuilder(HMACAuthenticator.class)
            .addMockedMethod("openGraph")
            .createMock();
        expect(hmacAuthenticator.openGraph(isA(String.class))).andReturn(graph);
        replay(hmacAuthenticator);
        return hmacAuthenticator;
    }
}
