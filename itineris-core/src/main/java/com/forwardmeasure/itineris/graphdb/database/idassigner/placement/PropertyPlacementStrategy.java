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

package com.forwardmeasure.itineris.graphdb.database.idassigner.placement;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import com.forwardmeasure.itineris.core.ItinerisVertex;
import com.forwardmeasure.itineris.core.ItinerisVertexProperty;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigOption;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration;
import com.forwardmeasure.itineris.graphdb.configuration.PreInitializeConfigOptions;
import com.forwardmeasure.itineris.graphdb.database.idassigner.IDPoolExhaustedException;
import com.forwardmeasure.itineris.graphdb.idmanagement.IDManager;
import com.forwardmeasure.itineris.graphdb.internal.InternalElement;
import com.forwardmeasure.itineris.graphdb.internal.InternalVertex;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
@PreInitializeConfigOptions
public class PropertyPlacementStrategy extends SimpleBulkPlacementStrategy {

    public static final ConfigOption<String> PARTITION_KEY = new ConfigOption<String>(GraphDatabaseConfiguration.IDS_NS,
            "partition-key","Partitions the graph by properties of this key", ConfigOption.Type.MASKABLE,
            String.class, StringUtils::isNotBlank);


    private String key;
    private IDManager idManager;

    public PropertyPlacementStrategy(Configuration config) {
        super(config);
        setPartitionKey(config.get(PARTITION_KEY));
    }

    public PropertyPlacementStrategy(String key, int concurrentPartitions) {
        super(concurrentPartitions);
        setPartitionKey(key);
    }

    public void setPartitionKey(String key) {
        Preconditions.checkArgument(StringUtils.isNotBlank(key),"Invalid key configured: %s",key);
        this.key=key;
    }

    @Override
    public void injectIDManager(IDManager idManager) {
        Preconditions.checkNotNull(idManager);
        this.idManager=idManager;
    }


    @Override
    public int getPartition(InternalElement element) {
        if (element instanceof ItinerisVertex) {
            int pid = getPartitionIDbyKey((ItinerisVertex)element);
            if (pid>=0) return pid;
        }
        return super.getPartition(element);
    }

    @Override
    public void getPartitions(Map<InternalVertex, PartitionAssignment> vertices) {
        super.getPartitions(vertices);
        for (Map.Entry<InternalVertex, PartitionAssignment> entry : vertices.entrySet()) {
            int pid = getPartitionIDbyKey(entry.getKey());
            if (pid>=0) ((SimplePartitionAssignment)entry.getValue()).setPartitionID(pid);
        }
    }

    private int getPartitionIDbyKey(ItinerisVertex vertex) {
        Preconditions.checkState(idManager!=null && key!=null,
                "PropertyPlacementStrategy has not been initialized correctly");
        assert idManager.getPartitionBound()<=Integer.MAX_VALUE;
        int partitionBound = (int)idManager.getPartitionBound();
        Iterator<ItinerisVertexProperty> it = vertex.query().keys(key).properties().iterator();
        final ItinerisVertexProperty p = it.hasNext()? it.next() : null;
        if (p==null){
            return -1;
        }
        int hashPid = Math.abs(p.value().hashCode())%partitionBound;
        assert hashPid>=0 && hashPid<partitionBound;
        if (isExhaustedPartition(hashPid)) {
            //We keep trying consecutive partition ids until we find a non-exhausted one
            int newPid=hashPid;
            do {
                newPid = (newPid+1)%partitionBound;
                if (newPid==hashPid) //We have gone full circle - no more ids to try
                    throw new IDPoolExhaustedException("Could not find non-exhausted partition");
            } while (isExhaustedPartition(newPid));
            return newPid;
        } else return hashPid;
    }
}
