// Copyright 2021 Itineris Authors
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

package com.forwardmeasure.itineris.graphdb.grpc;

import io.grpc.StatusRuntimeException;
import com.forwardmeasure.itineris.graphdb.grpc.types.ItinerisContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ItinerisManagerClientTest extends ItinerisGrpcServerBaseTest {

    @Test
    public void testGetItinerisVersion() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        String version = janusGraphManagerClient.getItinerisVersion();

        assertNotEquals("", version);
    }

    @Test
    public void testGetTinkerPopVersion() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        String version = janusGraphManagerClient.getTinkerPopVersion();

        assertNotEquals("", version);
    }

    @Test
    public void testContextIsReturnedForGivenGraphName() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        ItinerisContext context = janusGraphManagerClient.getContextByGraphName("graph");

        assertEquals("graph", context.getGraphName());
    }

    @Test
    public void testErrorIsReturnedForGivenGraphNameWhichIsNotAItineris() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        assertThrows(StatusRuntimeException.class, () ->
            janusGraphManagerClient.getContextByGraphName("tinkergraph"));
    }

    @Test
    public void testErrorIsReturnedForEmptyGivenGraphName() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        assertThrows(StatusRuntimeException.class, () ->
            janusGraphManagerClient.getContextByGraphName(""));
    }

    @Test
    public void testErrorIsReturnedForUnknownGivenGraphName() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        assertThrows(StatusRuntimeException.class, () ->
            janusGraphManagerClient.getContextByGraphName("test"));
    }

    @Test
    public void testAllContextAreReturned() {
        ItinerisManagerClient janusGraphManagerClient = new ItinerisManagerClient(managedChannel);

        List<ItinerisContext> contexts = janusGraphManagerClient.getContexts();

        assertEquals(2, contexts.size());
        assertTrue(contexts.stream().anyMatch(it -> it.getGraphName().equals("graph")));
        assertTrue(contexts.stream().anyMatch(it -> it.getGraphName().equals("graph2")));
    }
}
