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

package com.forwardmeasure.itineris.blueprints.inmemory;

import org.apache.tinkerpop.gremlin.GraphProvider;
import com.forwardmeasure.itineris.StorageSetup;
import com.forwardmeasure.itineris.blueprints.AbstractItinerisComputerProvider;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.olap.computer.FulgoraGraphComputer;

/**
 * Created by bryn on 06/05/15.
 */
@GraphProvider.Descriptor(computer = FulgoraGraphComputer.class)
public class InMemoryGraphComputerProvider extends AbstractItinerisComputerProvider {

    @Override
    public ModifiableConfiguration getItinerisConfiguration(String graphName, Class<?> test, String testMethodName) {
        ModifiableConfiguration config = super.getItinerisConfiguration(graphName, test, testMethodName);
        config.setAll(StorageSetup.getInMemoryConfiguration().getAll());
        config.set(GraphDatabaseConfiguration.STORAGE_TRANSACTIONAL,false);
        return config;
    }
}
