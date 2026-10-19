// Copyright 2023 Itineris Authors
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

package com.forwardmeasure.itineris.diskstorage.opensearch;

import java.util.function.Consumer;

import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.processor.CircleProcessor;
import com.forwardmeasure.itineris.diskstorage.mixed.utils.processor.FixedErrorDistanceCircleProcessor;

public class TestCircleProcessor implements CircleProcessor {

    private static Consumer<Geoshape> preProcessConsumer;
    private static Consumer<Geoshape> postProcessConsumer;

    private final CircleProcessor wrappedCircleProcessor;

    public TestCircleProcessor(Configuration config) {
        wrappedCircleProcessor = new FixedErrorDistanceCircleProcessor(config);
    }

    @Override
    public Geoshape process(Geoshape circle) {
        if (preProcessConsumer != null) {
            preProcessConsumer.accept(circle);
        }
        Geoshape transformedGeoshape = wrappedCircleProcessor.process(circle);
        if (postProcessConsumer != null) {
            postProcessConsumer.accept(transformedGeoshape);
        }
        return transformedGeoshape;
    }

    public static void setPreProcessConsumer(Consumer<Geoshape> preProcessConsumer) {
        TestCircleProcessor.preProcessConsumer = preProcessConsumer;
    }

    public static void setPostProcessConsumer(Consumer<Geoshape> postProcessConsumer) {
        TestCircleProcessor.postProcessConsumer = postProcessConsumer;
    }
}
