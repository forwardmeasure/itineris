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

package com.forwardmeasure.itineris.hadoop;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.tinkerpop.gremlin.structure.Graph;
import com.forwardmeasure.itineris.core.Itineris;
import com.forwardmeasure.itineris.core.RelationType;
import com.forwardmeasure.itineris.core.schema.Index;
import com.forwardmeasure.itineris.core.schema.ItinerisIndex;
import com.forwardmeasure.itineris.core.schema.RelationTypeIndex;
import com.forwardmeasure.itineris.core.schema.SchemaAction;
import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigElement;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.CompletedJobFuture;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.FailedJobFuture;
import com.forwardmeasure.itineris.diskstorage.keycolumnvalue.scan.ScanJobFuture;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.database.StandardItineris;
import com.forwardmeasure.itineris.graphdb.olap.job.IndexRemoveJob;
import com.forwardmeasure.itineris.graphdb.olap.job.IndexRepairJob;
import com.forwardmeasure.itineris.graphdb.olap.job.IndexUpdateJob;
import com.forwardmeasure.itineris.hadoop.config.ItinerisHadoopConfiguration;
import com.forwardmeasure.itineris.hadoop.config.ModifiableHadoopConfiguration;
import com.forwardmeasure.itineris.hadoop.scan.HadoopScanMapper;
import com.forwardmeasure.itineris.hadoop.scan.HadoopScanRunner;
import com.forwardmeasure.itineris.hadoop.scan.HadoopVertexScanMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;

public class MapReduceIndexManagement {

    private static final Logger log = LoggerFactory.getLogger(MapReduceIndexManagement.class);

    private final StandardItineris graph;

    private static final EnumSet<SchemaAction> SUPPORTED_ACTIONS =
            EnumSet.of(SchemaAction.REINDEX, SchemaAction.DISCARD_INDEX);

    private static final String SUPPORTED_ACTIONS_STRING =
            Joiner.on(", ").join(SUPPORTED_ACTIONS);

    public MapReduceIndexManagement(Itineris g) {
        this.graph = (StandardItineris)g;
    }

    public ScanJobFuture updateIndex(Index index, SchemaAction updateAction) throws BackendException {
        return updateIndex(index, updateAction, new Configuration());
    }

    /**
     * Updates the provided index according to the given {@link SchemaAction}.
     * Only {@link SchemaAction#REINDEX} and {@link SchemaAction#DISCARD_INDEX} are supported.
     *
     * @param index the index to process
     * @param updateAction either {@code REINDEX} or {@code DISCARD_INDEX}
     * @param hadoopConf
     * @return a future that returns immediately;
     *         this method blocks until the Hadoop MapReduce job completes
     */
    // TODO make this future actually async and update javadoc @return accordingly
    public ScanJobFuture updateIndex(Index index, SchemaAction updateAction, Configuration hadoopConf)
            throws BackendException {

        Preconditions.checkNotNull(index, "Index parameter must not be null", index);
        Preconditions.checkNotNull(updateAction, "%s parameter must not be null", SchemaAction.class.getSimpleName());
        Preconditions.checkArgument(SUPPORTED_ACTIONS.contains(updateAction),
                "Only these %s parameters are supported: %s (was given %s)",
                SchemaAction.class.getSimpleName(), SUPPORTED_ACTIONS_STRING, updateAction);
        Preconditions.checkArgument(
                RelationTypeIndex.class.isAssignableFrom(index.getClass()) ||
                        ItinerisIndex.class.isAssignableFrom(index.getClass()),
                "Index %s has class %s: must be a %s or %s (or subtype)",
                index.getClass(), RelationTypeIndex.class.getSimpleName(), ItinerisIndex.class.getSimpleName());

        ModifiableHadoopConfiguration janusGraphMapReduceConfiguration =
                ModifiableHadoopConfiguration.of(ItinerisHadoopConfiguration.MAPRED_NS, hadoopConf);

        // The job we'll execute to either REINDEX or DISCARD_INDEX
        final Class<? extends IndexUpdateJob> indexJobClass;
        final Class<? extends Mapper> mapperClass;

        // The class of the IndexUpdateJob and the Mapper that will be used to run it (VertexScanJob vs ScanJob)
        if (updateAction.equals(SchemaAction.REINDEX)) {
            indexJobClass = IndexRepairJob.class;
            mapperClass = HadoopVertexScanMapper.class;
        } else {
            assert updateAction.equals(SchemaAction.DISCARD_INDEX);
            indexJobClass = IndexRemoveJob.class;
            mapperClass = HadoopScanMapper.class;
        }

        // The column family that serves as input to the IndexUpdateJob
        final String readCF;
        if (RelationTypeIndex.class.isAssignableFrom(index.getClass())) {
            readCF = Backend.EDGESTORE_NAME;
        } else {
            ItinerisIndex graphIndex = (ItinerisIndex)index;
            if (graphIndex.isMixedIndex() && !updateAction.equals(SchemaAction.REINDEX))
                throw new UnsupportedOperationException("External mixed indexes must be removed in the indexing system directly.");

            Preconditions.checkState(ItinerisIndex.class.isAssignableFrom(index.getClass()));
            if (updateAction.equals(SchemaAction.DISCARD_INDEX))
                readCF = Backend.INDEXSTORE_NAME;
            else
                readCF = Backend.EDGESTORE_NAME;
        }
        janusGraphMapReduceConfiguration.set(ItinerisHadoopConfiguration.COLUMN_FAMILY_NAME, readCF);

        // The MapReduce InputFormat class based on the open graph's store manager

        HadoopStoreManager storeManager = (HadoopStoreManager) graph.getBackend().getStoreManager().getHadoopManager();
        if (storeManager == null) {
            throw new IllegalArgumentException("Store manager class " + graph.getBackend().getStoreManagerClass() + "is not supported");
        }

        final Class<? extends InputFormat> inputFormat = storeManager.getInputFormat(hadoopConf);

        // The index name and relation type name (if the latter is applicable)
        final String indexName = index.name();
        final RelationType relationType =
            RelationTypeIndex.class.isAssignableFrom(index.getClass()) ? ((RelationTypeIndex) index).getType() : null;
        final String relationTypeName = relationType == null ? StringUtils.EMPTY : relationType.name();
        Preconditions.checkNotNull(indexName);

        // Set the class of the IndexUpdateJob
        janusGraphMapReduceConfiguration.set(ItinerisHadoopConfiguration.SCAN_JOB_CLASS, indexJobClass.getName());
        // Set the configuration of the IndexUpdateJob
        copyIndexJobKeys(hadoopConf, indexName, relationTypeName);
        janusGraphMapReduceConfiguration.set(ItinerisHadoopConfiguration.SCAN_JOB_CONFIG_ROOT,
                GraphDatabaseConfiguration.class.getName() + "#JOB_NS");
        // Copy the StandardItineris configuration under ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS
        org.apache.commons.configuration2.Configuration localConfiguration = graph.getConfiguration().getConfigurationAtOpen();
        localConfiguration.clearProperty(Graph.GRAPH);
        copyInputKeys(hadoopConf, localConfiguration);

        String jobName = HadoopScanMapper.class.getSimpleName() + "[" + indexJobClass.getSimpleName() + "]";

        try {
            return new CompletedJobFuture(HadoopScanRunner.runJob(hadoopConf, inputFormat, jobName, mapperClass));
        } catch (Exception e) {
            return new FailedJobFuture(e);
        }
    }

    private static void copyInputKeys(org.apache.hadoop.conf.Configuration hadoopConf, org.apache.commons.configuration2.Configuration source) {
        // Copy IndexUpdateJob settings into the hadoop-backed cfg
        Iterator<String> keyIter = source.getKeys();
        while (keyIter.hasNext()) {
            String key = keyIter.next();

            String k = ConfigElement.getPath(ItinerisHadoopConfiguration.GRAPH_CONFIG_KEYS, true) + "." + key;
            Object vObject = source.getProperty(key);
            String v;
            if (vObject instanceof Collection) {
                v = Joiner.on(",").join((Collection<String>) vObject);
            } else {
                v = vObject.toString();
            }

            hadoopConf.set(k, v);
            log.debug("[inputkeys] Set {}={}", k, v);
        }
    }

    private static void copyIndexJobKeys(org.apache.hadoop.conf.Configuration hadoopConf, String indexName, String relationType) {
        hadoopConf.set(ConfigElement.getPath(ItinerisHadoopConfiguration.SCAN_JOB_CONFIG_KEYS, true) + "." +
                        ConfigElement.getPath(IndexUpdateJob.INDEX_NAME), indexName);

        hadoopConf.set(ConfigElement.getPath(ItinerisHadoopConfiguration.SCAN_JOB_CONFIG_KEYS, true) + "." +
                ConfigElement.getPath(IndexUpdateJob.INDEX_RELATION_TYPE), relationType);

        hadoopConf.set(ConfigElement.getPath(ItinerisHadoopConfiguration.SCAN_JOB_CONFIG_KEYS, true) + "." +
                ConfigElement.getPath(GraphDatabaseConfiguration.JOB_START_TIME),
                String.valueOf(System.currentTimeMillis()));
    }
}
