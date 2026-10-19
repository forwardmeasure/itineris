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

package com.forwardmeasure.itineris.hadoop.scan;

import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.ItinerisFactory;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.graphdb.olap.VertexJobConverter;
import com.forwardmeasure.itineris.graphdb.olap.VertexScanJob;
import com.forwardmeasure.itineris.hadoop.config.ItinerisHadoopConfiguration;
import com.forwardmeasure.itineris.hadoop.config.ModifiableHadoopConfiguration;

import java.io.IOException;

public class HadoopVertexScanMapper extends HadoopScanMapper {

    private Itineris graph;

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        /* Don't call super implementation super.setup(context); */
        org.apache.hadoop.conf.Configuration hadoopConf = context.getConfiguration();
        ModifiableHadoopConfiguration scanConf = ModifiableHadoopConfiguration.of(ItinerisHadoopConfiguration.MAPRED_NS, hadoopConf);
        VertexScanJob vertexScan = getVertexScanJob(scanConf);
        ModifiableConfiguration graphConf = getItinerisConfiguration(context);
        graph = ItinerisFactory.open(graphConf);
        job = VertexJobConverter.convert(graph, vertexScan, null);
        metrics = new HadoopContextScanMetrics(context);
        finishSetup(scanConf, graphConf);
    }

    private VertexScanJob getVertexScanJob(ModifiableHadoopConfiguration conf) {
        String jobClass = conf.get(ItinerisHadoopConfiguration.SCAN_JOB_CLASS);

        try {
            return (VertexScanJob)Class.forName(jobClass).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        super.cleanup(context);
        graph.close();
    }
}
