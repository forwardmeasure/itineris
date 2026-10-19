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

package com.forwardmeasure.itineris.graphdb;

import com.forwardmeasure.itineris.diskstorage.Backend;
import com.forwardmeasure.itineris.diskstorage.BackendException;
import com.forwardmeasure.itineris.diskstorage.BaseTransaction;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfig;
import com.forwardmeasure.itineris.diskstorage.BaseTransactionConfigurable;
import com.forwardmeasure.itineris.diskstorage.StandardIndexProvider;
import com.forwardmeasure.itineris.diskstorage.TemporaryBackendException;
import com.forwardmeasure.itineris.diskstorage.configuration.ConfigOption;
import com.forwardmeasure.itineris.diskstorage.configuration.Configuration;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexEntry;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexFeatures;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexMutation;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexProvider;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexQuery;
import com.forwardmeasure.itineris.diskstorage.indexing.KeyInformation;
import com.forwardmeasure.itineris.diskstorage.indexing.RawQuery;
import com.forwardmeasure.itineris.graphdb.query.ItinerisPredicate;
import com.forwardmeasure.itineris.graphdb.tinkerpop.optimize.step.Aggregation;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_BACKEND;
import static com.forwardmeasure.itineris.graphdb.configuration.GraphDatabaseConfiguration.INDEX_NS;

/**
 * @author Matthias Broecheler (me@matthiasb.com)
 */
public class TestMockIndexProvider implements IndexProvider {

    public static final ConfigOption<Boolean> INDEX_MOCK_FAILADD = new ConfigOption<>(INDEX_NS, "fail-adds",
            "Sets the index provider to reject adding documents. FOR TESTING ONLY",
            ConfigOption.Type.LOCAL, false).hide();

    public static final ConfigOption<String> INDEX_BACKEND_PROXY = new ConfigOption<>(INDEX_NS, "proxy-for",
            "Define the indexing backed to use for index support behind the mock proxy",
            ConfigOption.Type.GLOBAL, INDEX_BACKEND.getDefaultValue()).hide();

    private final IndexProvider index;
    private final boolean failAdds;

    public TestMockIndexProvider(Configuration config) {
        this.index = Backend.getImplementationClass(config, config.get(INDEX_BACKEND_PROXY),
                StandardIndexProvider.getAllProviderClasses());
        this.failAdds = config.get(INDEX_MOCK_FAILADD);
    }

    @Override
    public void register(String store, String key, KeyInformation information, BaseTransaction tx) throws BackendException {
        index.register(store,key,information,tx);
    }

    @Override
    public void mutate(Map<String, Map<String, IndexMutation>> mutations, KeyInformation.IndexRetriever information, BaseTransaction tx) throws BackendException {
        if (!failAdds) index.mutate(mutations, information,tx);
        else throw new TemporaryBackendException("Blocked mutation");
    }

    @Override
    public void restore(Map<String, Map<String, List<IndexEntry>>> documents, KeyInformation.IndexRetriever information, BaseTransaction tx) throws BackendException {
        if (!failAdds) index.restore(documents, information, tx);
        else throw new TemporaryBackendException("Blocked mutation");
    }

    @Override
    public Stream<String> query(IndexQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx) throws BackendException {
        return index.query(query, information,tx);
    }

    @Override
    public Number queryAggregation(IndexQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx, Aggregation aggregation) throws BackendException {
        return index.queryAggregation(query, information, tx, aggregation);
    }

    @Override
    public Stream<RawQuery.Result<String>> query(RawQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx) throws BackendException {
        return index.query(query, information,tx);
    }

    @Override
    public Long totals(RawQuery query, KeyInformation.IndexRetriever information, BaseTransaction tx) throws BackendException {
        return index.totals(query, information,tx);
    }
    
    @Override
    public BaseTransactionConfigurable beginTransaction(BaseTransactionConfig config) throws BackendException {
        return index.beginTransaction(config);
    }

    @Override
    public void close() throws BackendException {
        index.close();
    }

    @Override
    public void clearStorage() throws BackendException {
        index.clearStorage();
    }

    @Override
    public void clearStore(String storeName) throws BackendException {
        index.clearStore(storeName);
    }

    @Override
    public boolean exists() throws BackendException {
        return index.exists();
    }

    @Override
    public boolean supports(KeyInformation information, ItinerisPredicate janusgraphPredicate) {
        return index.supports(information,janusgraphPredicate);
    }

    @Override
    public boolean supports(KeyInformation information) {
        return index.supports(information);
    }

    @Override
    public String mapKey2Field(String key, KeyInformation information) {
        return index.mapKey2Field(key,information);
    }

    @Override
    public IndexFeatures getFeatures() {
        return index.getFeatures();
    }

}
