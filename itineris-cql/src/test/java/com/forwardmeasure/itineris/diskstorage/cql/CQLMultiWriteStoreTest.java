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

package com.forwardmeasure.itineris.diskstorage.cql;

import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.BATCH_STATEMENT_SIZE;
import static com.forwardmeasure.itineris.diskstorage.cql.CQLConfigOptions.SESSION_LEAK_THRESHOLD;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.UnhandledException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.datastax.oss.driver.internal.core.session.DefaultSession;
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.MultiWriteKeyColumnValueStoreTest;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.testutil.InMemoryLog4j2Appender;
import com.forwardmeasure.itineris.testutil.TestLoggerUtils;

@Testcontainers
public class CQLMultiWriteStoreTest extends MultiWriteKeyColumnValueStoreTest {

    @Container
    public static final ItinerisCassandraContainer cqlContainer = new ItinerisCassandraContainer();

    protected ModifiableConfiguration getBaseStorageConfiguration() {
        return cqlContainer.getConfiguration(getClass().getSimpleName());
    }

    private CQLStoreManager openStorageManager(final Configuration c) throws BackendException {
        return new CachingCQLStoreManager(c);
    }

    @Override
    public CQLStoreManager openStorageManager() throws BackendException {
        return openStorageManager(getBaseStorageConfiguration());
    }

    @Test
    public void shouldLogSessionLeakWarning() throws BackendException {

        TestLoggerUtils.processWithLoggerReplacement(
                appender -> {

                    ModifiableConfiguration configuration = getBaseStorageConfiguration();
                    configuration.set(SESSION_LEAK_THRESHOLD, 2);

                    Assertions.assertFalse(hasWarnLog(appender));

                    List<CQLStoreManager> storeManagers = new ArrayList<>(3);
                    for (int i = 0; i < 3; i++) {
                        try {
                            storeManagers.add(new CQLStoreManager(configuration));
                        } catch (BackendException e) {
                            Assertions.fail("Unexpected BackendException while creating CQLStoreManager", e);
                        }
                    }

                    Assertions.assertTrue(hasWarnLog(appender));

                    storeManagers.forEach(cqlStoreManager -> {
                        try {
                            cqlStoreManager.close();
                        } catch (BackendException backendException) {
                            throw new UnhandledException(backendException);
                        }
                    });

                },
                DefaultSession.class,
                Level.WARN);
    }

    @Test
    public void shouldProperlyCloseSessionOnExceptionAndNotLogSessionLeakWarnings() {

        TestLoggerUtils.processWithLoggerReplacement(
                appender -> {

                    ModifiableConfiguration configuration = Mockito.spy(getBaseStorageConfiguration());
                    configuration.set(SESSION_LEAK_THRESHOLD, 2);

                    Mockito.doThrow(RuntimeException.class).when(configuration).get(BATCH_STATEMENT_SIZE);

                    Assertions.assertFalse(hasWarnLog(appender));

                    for (int i = 0; i < 3; i++) {
                        Assertions.assertThrows(Throwable.class,
                                () -> new CQLStoreManager(configuration));
                    }

                    Assertions.assertFalse(hasWarnLog(appender));

                },
                DefaultSession.class,
                Level.WARN);
    }

    private boolean hasWarnLog(InMemoryLog4j2Appender appender) {
        for (LogEvent logEvent : appender.getEvents()) {
            if (Level.WARN.equals(logEvent.getLevel())
                    && logEvent.getMessage().getFormattedMessage()
                            .startsWith("You have too many session instances")) {
                return true;
            }
        }
        return false;
    }
}
