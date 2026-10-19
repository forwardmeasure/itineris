// Copyright 2017 JanusGraph Authors
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

import static com.forwardmeasure.itineris.BerkeleyStorageSetup.getBerkeleyJEConfiguration;

import java.io.File;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.example.GraphOfTheGodsFactory;
import com.forwardmeasure.itineris.graphdb.ItinerisIndexTest;
import com.forwardmeasure.itineris.util.system.IOUtils;

import io.github.artsok.RepeatedIfExceptionsTest;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */

@Testcontainers
public class BerkeleyOpenSearchTest extends OpenSearchItinerisIndexTest {

    @Override
    public ModifiableConfiguration getStorageConfiguration() {
        return getBerkeleyJEConfiguration();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/3960
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void indexShouldNotExistAfterDeletion() throws Exception {
        super.indexShouldNotExistAfterDeletion();
    }

    /**
     * Test
     * {@link com.forwardmeasure.itineris.example.GraphOfTheGodsFactory#create(String)}.
     */
    @Test
    public void testGraphOfTheGodsFactoryCreate() {
        File bdbtmp = new File("target/gotgfactory");
        IOUtils.deleteDirectory(bdbtmp, true);

        Itineris gotg = ItinerisFactory.open(getConfiguration());
        GraphOfTheGodsFactory.load(gotg);
        ItinerisIndexTest.assertGraphOfTheGods(gotg);
        gotg.close();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/3651
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testDisableAndDiscardManuallyAndDropEnabledIndex() throws Exception {
        super.testDisableAndDiscardManuallyAndDropEnabledIndex();
    }

    // flaky test: https://github.com/JanusGraph/janusgraph/issues/3931
    @RepeatedIfExceptionsTest(repeats = 3)
    @Override
    public void testDiscardAndDropRegisteredIndex() throws ExecutionException, InterruptedException {
        super.testDiscardAndDropRegisteredIndex();
    }
}
