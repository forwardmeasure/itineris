package com.forwardmeasure.itineris.testutil;

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
// package com.forwardmeasure.itineris.testutil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class InMemoryLog4j2Appender extends AbstractAppender {

    private final List<LogEvent> events = new ArrayList<>();

    protected InMemoryLog4j2Appender(String name, Filter filter, Layout<?> layout) {
        super(name, filter, layout, false, Property.EMPTY_ARRAY);
    }

    public static InMemoryLog4j2Appender create(String name) {
        Layout<?> layout = PatternLayout.newBuilder()
                .withPattern("%m")
                .build();
        return new InMemoryLog4j2Appender(name, null, layout);
    }

    @Override
    public void append(LogEvent event) {
        // store immutable copy so later reads are safe [web:66][web:71]
        events.add(event.toImmutable());
    }

    public List<LogEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}
