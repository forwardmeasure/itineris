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

package com.forwardmeasure.itineris.blueprints;

import org.apache.tinkerpop.gremlin.GraphProvider;
import com.forwardmeasure.itineris.BerkeleyStorageSetup;
import com.forwardmeasure.itineris.StorageSetup;
import com.forwardmeasure.itineris.diskstorage.berkeleyje.BerkeleyJETx;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.olap.computer.FulgoraGraphComputer;

import java.time.Duration;
import java.util.Set;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
@GraphProvider.Descriptor(computer = FulgoraGraphComputer.class)
public class BerkeleyGraphComputerProvider extends AbstractItinerisComputerProvider {

    @Override
    public ModifiableConfiguration getItinerisConfiguration(String graphName, Class<?> test, String testMethodName) {
        ModifiableConfiguration config = super.getItinerisConfiguration(graphName, test, testMethodName);
        config.setAll(BerkeleyStorageSetup.getBerkeleyJEConfiguration(StorageSetup.getHomeDir(graphName)).getAll());
        config.set(GraphDatabaseConfiguration.IDAUTHORITY_WAIT, Duration.ofMillis(20));
        return config;
    }

    @Override
    public Set<Class> getImplementations() {
        final Set<Class> implementations = super.getImplementations();
        implementations.add(BerkeleyJETx.class);
        return implementations;
    }

}
