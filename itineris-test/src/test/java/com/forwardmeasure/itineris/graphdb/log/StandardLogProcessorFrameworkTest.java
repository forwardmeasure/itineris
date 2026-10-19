// Copyright 2020 JanusGraph Authors
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

package com.forwardmeasure.itineris.graphdb.log;

import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.log.Log;
import com.forwardmeasure.itineris.diskstorage.log.ReadMarker;
import com.forwardmeasure.itineris.diskstorage.log.kcvs.KCVSLog;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProvider;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.database.serialize.Serializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StandardLogProcessorFrameworkTest {

    @Test
    public void testAddLogProcessor() throws BackendException, NoSuchFieldException, IllegalAccessException {
        StandardItineris graph = createGraphWithMockedInternals();
        StandardLogProcessorFramework logProcessorFramework = new StandardLogProcessorFramework(graph);

        logProcessorFramework.addLogProcessor("foo-identifier")
            .addProcessor((tx, txId, changeState) -> {
                // no-op processor
            })
            .setStartTimeNow()
            .setProcessorIdentifier("bar-processor")
            .build();

        Field processorLogsField = logProcessorFramework.getClass().getDeclaredField("processorLogs");
        processorLogsField.setAccessible(true);
        Map<String, Log> processorLogs = (Map<String, Log>) processorLogsField.get(logProcessorFramework);

        assertNotNull(processorLogs, "processorLogs must not be null or empty after adding a log processor");
        assertFalse(processorLogs.isEmpty(), "processorLogs should be non-empty after adding a processor");
    }

    private StandardItineris createGraphWithMockedInternals() throws BackendException {
        StandardItineris mockGraph = mock(StandardItineris.class);
        Backend mockBackend = mock(Backend.class);
        KCVSLog mockKCVSLog = mock(KCVSLog.class);
        GraphDatabaseConfiguration gdbConfig = mock(GraphDatabaseConfiguration.class);
        TimestampProvider tsProvider = mock(TimestampProvider.class);
        Serializer mockSerializer = mock(Serializer.class);

        when(mockGraph.getConfiguration()).thenReturn(gdbConfig);
        when(mockGraph.isOpen()).thenReturn(true);
        when(mockGraph.getDataSerializer()).thenReturn(mockSerializer);
        when(mockGraph.getBackend()).thenReturn(mockBackend);
        when(mockBackend.getUserLog(anyString())).thenReturn(mockKCVSLog);
        when(gdbConfig.getTimestampProvider()).thenReturn(tsProvider);

        mockKCVSLog.registerReaders(mock(ReadMarker.class), new LinkedList<>());

        return mockGraph;
    }
}
