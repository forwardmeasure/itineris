// Copyright 2021 JanusGraph Authors
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

package com.forwardmeasure.itineris.testutil;

import java.util.function.Consumer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public final class TestLoggerUtils {

    private TestLoggerUtils() {
    }

    /**
     * Temporarily attaches an in-memory appender to the logger associated with the
     * given class,
     * runs the test body, then restores the original logger state.
     *
     * This is the log4j2 equivalent of the previous Logback
     * ProcessWithLoggerReplacement helper.
     */
    public static void processWithLoggerReplacement(
            Consumer<InMemoryLog4j2Appender> testBody,
            Class<?> loggerClass,
            Level level) {

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        Logger logger = (Logger) LogManager.getLogger(loggerClass);

        InMemoryLog4j2Appender appender = InMemoryLog4j2Appender.create("TestAppender-" + loggerClass.getSimpleName());

        // attach appender and set level
        logger.addAppender(appender);
        Level oldLevel = logger.getLevel();
        logger.setLevel(level);
        appender.start();
        ctx.updateLoggers(config);

        try {
            testBody.accept(appender);
        } finally {
            // cleanup
            logger.removeAppender(appender);
            logger.setLevel(oldLevel);
            appender.stop();
            ctx.updateLoggers(config);
        }
    }
}
