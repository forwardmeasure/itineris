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

package com.forwardmeasure.itineris.graphdb.tinkerpop.optimize;

import org.apache.tinkerpop.gremlin.process.traversal.step.filter.DiscardStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.NoOpBarrierStep;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisHasStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.ItinerisNoOpBarrierVertexOnlyStep;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.strategy.AdjacentVertexHasUniquePropertyOptimizerStrategy;
import org.junit.jupiter.api.Test;

import static com.forwardmeasure.itineris.graphdb.ItinerisBaseTest.option;
import static com.forwardmeasure.itineris.testutil.ItinerisAssert.assertNumStep;
import static com.forwardmeasure.itineris.testutil.ItinerisAssert.assertSameResultWithOptimizations;

/**
 * @author Florian Grieskamp (Florian.Grieskamp@gdata.de)
 */
public class AdjacentVertexHasUniquePropertyOptimizerStrategyTest extends OptimizerStrategyTest {

    @Test
    public void testWithAndWithoutStrategy() {
        makeSampleGraph();
        assertNumStep(1, 0, g.V(sv[0]).out().has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, graph.traversal().withoutStrategies(AdjacentVertexHasUniquePropertyOptimizerStrategy.class).V(sv[0]).out().has("uniqueId", 0), ItinerisHasStep.class);
    }

    @Test
    public void testWithBackendAccess() {
        makeSampleGraph();

        // AdjacentVertexHasUniquePropertyOptimizer
        assertNumStep(1, 0, g.V(sv[0]).out().has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, g.V(sv[0]).out().barrier(2500).has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().inV().has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 0, g.V(sv[0]).outE().inV().has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().inV().barrier(2500).has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().barrier(2500).inV().has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 1, g.V(sv[0]).barrier(2500).out().barrier(2500).has("uniqueId", 0), NoOpBarrierStep.class);
        assertNumStep(1, 0, g.V(sv[0]).out().barrier(2500).has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, g.V(sv[0]).out().has("id", 0), ItinerisHasStep.class);
        assertNumStep(1, 0, g.V(sv[0]).out().has("id", 0).has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(0, 0, g.V(sv[0]).out().has("uniqueId", 10000), ItinerisHasStep.class);
        assertNumStep(0, 1, g.V(sv[0]).out().has("uniqueId", 10000), DiscardStep.class);

        // ensure step labels are handled correctly
        assertSameResultWithOptimizations(g.V().as("v1").out().has("uniqueId", 0).as("v2").select("v1", "v2").by("uniqueId"),
            AdjacentVertexHasUniquePropertyOptimizerStrategy.instance());
    }

    @Test
    public void testWithoutBackendAccess() {
        clopen(option(GraphDatabaseConfiguration.OPTIMIZER_BACKEND_ACCESS), false);
        makeSampleGraph();

        // AdjacentVertexHasUniquePropertyOptimizer
        assertNumStep(1, 1, g.V(sv[0]).out().has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 2, g.V(sv[0]).outE().inV().has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().inV().has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().inV().barrier(2500).has("uniqueId", 0), NoOpBarrierStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().inV().barrier(2500).has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 1, g.V(sv[0]).outE().barrier(2500).inV().has("uniqueId", 0), NoOpBarrierStep.class);
        assertNumStep(1, 2, g.V(sv[0]).outE().barrier(2500).inV().has("uniqueId", 0), ItinerisNoOpBarrierVertexOnlyStep.class);
        assertNumStep(1, 2, g.V(sv[0]).barrier(2500).out().barrier(2500).has("uniqueId", 0), NoOpBarrierStep.class);
        assertNumStep(1, 1, g.V(sv[0]).out().barrier(2500).has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, g.V(sv[0]).out().has("id", 0), ItinerisHasStep.class);
        assertNumStep(1, 1, g.V(sv[0]).out().has("id", 0).has("uniqueId", 0), ItinerisHasStep.class);
        assertNumStep(0, 1, g.V(sv[0]).out().has("uniqueId", 10000), ItinerisHasStep.class);
        assertNumStep(0, 0, g.V(sv[0]).out().has("uniqueId", 10000), DiscardStep.class);

        // ensure step labels are handled correctly
        assertSameResultWithOptimizations(g.V().as("v1").out().has("uniqueId", 0).as("v2").select("v1", "v2").by("uniqueId"),
            AdjacentVertexHasUniquePropertyOptimizerStrategy.instance());
    }
}
