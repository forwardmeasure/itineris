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

package com.forwardmeasure.itineris.hadoop;

import org.apache.hadoop.conf.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.ModifiableConfiguration;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanMetrics;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.olap.job.IndexRemoveJob;
import com.forwardmeasure.itineris.graphdb.olap.job.IndexRepairJob;
import com.forwardmeasure.itineris.hadoop.scan.CQLHadoopScanRunner;
import com.forwardmeasure.itineris.util.system.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class CqlMapReduceIndexJobsUtils {

    public static ScanMetrics repair(String itinerisPropertiesPath, String indexName, String relationType, String partitionerName)
        throws InterruptedException, IOException, ClassNotFoundException {
        Properties p = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(itinerisPropertiesPath);
            p.load(fis);
            return repair(p, indexName, relationType, partitionerName);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    public static ScanMetrics repair(Properties itinerisProperties, String indexName, String relationType,
                                     String partitionerName)
        throws InterruptedException, IOException, ClassNotFoundException {
        return repair(itinerisProperties, indexName, relationType, partitionerName, new Configuration());
    }

    public static ScanMetrics repair(Properties itinerisProperties, String indexName, String relationType,
                                     String partitionerName, Configuration hadoopBaseConf)
        throws InterruptedException, IOException, ClassNotFoundException {
        IndexRepairJob job = new IndexRepairJob();
        CQLHadoopScanRunner cr = new CQLHadoopScanRunner(job);
        return executeScanRunner(itinerisProperties, indexName, relationType, partitionerName, hadoopBaseConf, cr);
    }

    public static ScanMetrics remove(String itinerisPropertiesPath, String indexName, String relationType, String partitionerName)
        throws InterruptedException, IOException, ClassNotFoundException {
        Properties p = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(itinerisPropertiesPath);
            p.load(fis);
            return remove(p, indexName, relationType, partitionerName);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }

    public static ScanMetrics remove(Properties itinerisProperties, String indexName, String relationType,
                                     String partitionerName)
        throws InterruptedException, IOException, ClassNotFoundException {
        return remove(itinerisProperties, indexName, relationType, partitionerName, new Configuration());
    }

    public static ScanMetrics remove(Properties itinerisProperties, String indexName, String relationType,
                                     String partitionerName, Configuration hadoopBaseConf)
        throws InterruptedException, IOException, ClassNotFoundException {
        IndexRemoveJob job = new IndexRemoveJob();
        CQLHadoopScanRunner cr = new CQLHadoopScanRunner(job);
        return executeScanRunner(itinerisProperties, indexName, relationType, partitionerName, hadoopBaseConf, cr);
    }

    private static ScanMetrics executeScanRunner(Properties itinerisProperties, String indexName, String relationType, String partitionerName, Configuration hadoopBaseConf, CQLHadoopScanRunner cr) throws InterruptedException, IOException, ClassNotFoundException {
        ModifiableConfiguration mc = MapReduceIndexJobs.getIndexJobConf(indexName, relationType);
        MapReduceIndexJobs.copyPropertiesToInputAndOutputConf(hadoopBaseConf, itinerisProperties);
        cr.partitionerOverride(partitionerName);
        cr.scanJobConf(mc);
        cr.scanJobConfRoot(GraphDatabaseConfiguration.class.getName() + "#JOB_NS");
        cr.baseHadoopConf(hadoopBaseConf);
        return cr.run();
    }

}
