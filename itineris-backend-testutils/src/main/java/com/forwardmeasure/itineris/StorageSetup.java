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

package com.forwardmeasure.itineris;


import org.apache.commons.lang3.StringUtils;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.diskstorage.configuration.BasicConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.ReadConfiguration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;

import java.io.File;
import java.time.Duration;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.DB_CACHE;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.DB_CACHE_TIME;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.DROP_ON_CLEAR;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.IDAUTHORITY_WAIT;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.ROOT_NS;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.STORAGE_BACKEND;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.USE_MULTIQUERY;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.buildGraphConfiguration;

public class StorageSetup {

    //############ UTILITIES #############

    public static String getHomeDir(String subDirectory) {
        String homeDirectory = System.getProperty("itineris.testdir");
        if (null == homeDirectory) {
            homeDirectory = "target" + File.separator + "db";
        }
        if (subDirectory!=null && !StringUtils.isEmpty(subDirectory)) homeDirectory += File.separator + subDirectory;
        File homeFile = new File(homeDirectory);
        if (!homeFile.exists()) homeFile.mkdirs();
        return homeDirectory;
    }

    public static ModifiableConfiguration getInMemoryConfiguration() {
        return buildGraphConfiguration()
            .set(STORAGE_BACKEND, "inmemory")
            .set(IDAUTHORITY_WAIT, Duration.ZERO)
            .set(DROP_ON_CLEAR, false);
    }

    public static Itineris getInMemoryGraph() {
        return ItinerisFactory.open(getInMemoryConfiguration());
    }
    
    public static Itineris getInMemoryGraphWithMultiQuery() {
        return ItinerisFactory.open(getInMemoryConfiguration().set(USE_MULTIQUERY, true));
    }

    public static WriteConfiguration addPermanentCache(ModifiableConfiguration conf) {
        conf.set(DB_CACHE, true);
        conf.set(DB_CACHE_TIME, 0L);
        return conf.getConfiguration();
    }

    public static ModifiableConfiguration getConfig(WriteConfiguration config) {
        return new ModifiableConfiguration(ROOT_NS,config, BasicConfiguration.Restriction.NONE);
    }

    public static BasicConfiguration getConfig(ReadConfiguration config) {
        return new BasicConfiguration(ROOT_NS,config, BasicConfiguration.Restriction.NONE);
    }

}
