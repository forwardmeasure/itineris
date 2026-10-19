// Copyright 2019 JanusGraph Authors
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

import org.apache.cassandra.hadoop.ColumnFamilySplit;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import com.forwardmeasure.itineris.ItinerisCassandraContainer;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.KeyColumnValueStoreUtil;
import com.forwardmeasure.itineris.diskstorage.KeyValueStoreUtil;
import com.forwardmeasure.itineris.diskstorage.SimpleScanJob;
import com.forwardmeasure.itineris.diskstorage.SimpleScanJobRunner;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigElement;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.configuration.WriteConfiguration;
import com.forwardmeasure.itineris.diskstorage.cql.CQLStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStore;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.StoreTransaction;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanJob;
import com.forwardmeasure.itineris.diskstorage.util.StandardBaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.util.time.TimestampProviders;
import com.forwardmeasure.itineris.graphdb.ItinerisBaseTest;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.hadoop.config.ItinerisHadoopConfiguration;
import com.forwardmeasure.itineris.hadoop.formats.cql.CqlInputFormat;
import com.forwardmeasure.itineris.hadoop.scan.CQLHadoopScanRunner;
import com.forwardmeasure.itineris.hadoop.scan.HadoopScanMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
public class CQLScanJobIT extends ItinerisBaseTest {

    @Container
    private static ItinerisCassandraContainer cql = new ItinerisCassandraContainer(true);

    private static final Logger log = LoggerFactory.getLogger(CQLScanJobIT.class);

    @Test
    public void testSimpleScan()
        throws InterruptedException, ExecutionException, IOException, BackendException {

        int keys = 1000;
        int cols = 40;

        String[][] values = KeyValueStoreUtil.generateData(keys, cols);
        //Make it only half the number of columns for every 2nd key
        for (int i = 0; i < values.length; i++) {
            if (i%2==0) values[i]= Arrays.copyOf(values[i], cols / 2);
        }
        log.debug("Loading values: " + keys + "x" + cols);

        KeyColumnValueStoreManager mgr = new CQLStoreManager(GraphDatabaseConfiguration.buildGraphConfiguration());
        KeyColumnValueStore store = mgr.openDatabase("edgestore");
        StoreTransaction tx = mgr.beginTransaction(StandardBaseTransactionConfig.of(TimestampProviders.MICRO));
        KeyColumnValueStoreUtil.loadValues(store, tx, values);
        tx.commit(); // noop on Cassandra, but harmless

        SimpleScanJobRunner runner = (ScanJob job, Configuration jobConf, String rootNSName) -> {
            try {
                return new CQLHadoopScanRunner(job).scanJobConf(jobConf).scanJobConfRoot(rootNSName)
                    .partitionerOverride("org.apache.cassandra.dht.Murmur3Partitioner").run();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        };

        SimpleScanJob.runBasicTests(keys, cols, runner);
    }

    @Test
    public void testPartitionedVertexScan() throws Exception {
        tearDown();
        clearGraph(getConfiguration());
        WriteConfiguration partConf = getConfiguration();
        open(partConf);
        mgmt.makeVertexLabel("part").partition().make();
        finishSchema();
        ItinerisVertex supernode = graph.addVertex("part");
        for (int i = 0; i < 128; i++) {
            ItinerisVertex v = graph.addVertex("part");
            v.addEdge("default", supernode);
            if (0 < i && 0 == i % 4)
                graph.tx().commit();
        }
        graph.tx().commit();

        org.apache.hadoop.conf.Configuration c = new org.apache.hadoop.conf.Configuration();
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.cql.keyspace", getClass().getSimpleName().toLowerCase());
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.backend", "cql");
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.port", String.valueOf(cql.getMappedCQLPort()));
        c.set("cassandra.input.partitioner.class", "org.apache.cassandra.dht.Murmur3Partitioner");

        Job job = getVertexJobWithDefaultMapper(c);

        // Should throw an exception since filter-partitioned-vertices wasn't enabled
        assertFalse(job.waitForCompletion(true));
    }

    @Test
    public void testPartitionedVertexFilteredScan() throws Exception {
        tearDown();
        clearGraph(getConfiguration());
        WriteConfiguration partConf = getConfiguration();
        open(partConf);
        mgmt.makeVertexLabel("part").partition().make();
        finishSchema();
        ItinerisVertex supernode = graph.addVertex("part");
        for (int i = 0; i < 128; i++) {
            ItinerisVertex v = graph.addVertex("part");
            v.addEdge("default", supernode);
            if (0 < i && 0 == i % 4)
                graph.tx().commit();
        }
        graph.tx().commit();

        org.apache.hadoop.conf.Configuration c = new org.apache.hadoop.conf.Configuration();
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.cql.keyspace", getClass().getSimpleName().toLowerCase());
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.backend", "cql");
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.port", String.valueOf(cql.getMappedCQLPort()));
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.FILTER_PARTITIONED_VERTICES, true), "true");
        c.set("cassandra.input.partitioner.class", "org.apache.cassandra.dht.Murmur3Partitioner");

        Job job = getVertexJobWithDefaultMapper(c);

        // Should succeed
        assertTrue(job.waitForCompletion(true));
    }

    @Test
    public void testSplitThreadsSetting() throws Exception {
        org.apache.hadoop.conf.Configuration c = new org.apache.hadoop.conf.Configuration();
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.cql.keyspace", getClass().getSimpleName().toLowerCase());
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.backend", "cql");
        c.set(ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + "storage.port", String.valueOf(cql.getMappedCQLPort()));
        c.set("cassandra.input.partitioner.class", "org.apache.cassandra.dht.Murmur3Partitioner");
        CqlInputFormat inputFormat = new CqlInputFormat();
        inputFormat.setConf(c);

        List<String> splits = tokenRanges(inputFormat.getSplits(Job.getInstance(c)));
        assertFalse(splits.isEmpty());

        // The same splits, all fetched on one thread
        c.setInt("cassandra.input.split.threads", 1);
        assertEquals(splits, tokenRanges(inputFormat.getSplits(Job.getInstance(c))));

        c.setInt("cassandra.input.split.threads", 0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> inputFormat.getSplits(Job.getInstance(c)));
        assertTrue(e.getMessage().contains("cassandra.input.split.threads"), e.getMessage());
    }

    private static List<String> tokenRanges(List<InputSplit> splits) {
        return splits.stream()
            .map(split -> ((ColumnFamilySplit) split).getStartToken() + ".." + ((ColumnFamilySplit) split).getEndToken())
            .sorted()
            .collect(Collectors.toList());
    }

    private Job getVertexJobWithDefaultMapper(org.apache.hadoop.conf.Configuration c) throws IOException {

        Job job = Job.getInstance(c);

        job.setJarByClass(HadoopScanMapper.class);
        job.setJobName("testPartitionedVertexScan");
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(NullWritable.class);
        job.setMapOutputKeyClass(NullWritable.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setNumReduceTasks(0);
        job.setOutputFormatClass(NullOutputFormat.class);
        job.setInputFormatClass(CqlInputFormat.class);

        return job;
    }

    @Override
    public WriteConfiguration getConfiguration() {
        return cql.getConfiguration(getClass().getSimpleName().toLowerCase()).getConfiguration();
    }
}
