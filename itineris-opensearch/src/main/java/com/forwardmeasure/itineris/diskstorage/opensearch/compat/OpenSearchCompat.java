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

package com.forwardmeasure.itineris.diskstorage.opensearch.compat;

import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_ANALYZER;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_ID_KEY;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_PARAMS_FIELDS_KEY;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_PARAMS_KEY;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_SCRIPT_KEY;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_SOURCE_KEY;
import static com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants.OPENSEARCH_TYPE_KEY;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.forwardmeasure.itineris.core.Cardinality;
import com.forwardmeasure.itineris.core.attribute.Geo;
import com.forwardmeasure.itineris.core.schema.Mapping;
import com.forwardmeasure.itineris.core.schema.Parameter;
import com.forwardmeasure.itineris.diskstorage.indexing.IndexFeatures;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchConstants;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * OpenSearch mapping and query helper.
 */
public class OpenSearchCompat {

    private static final Map<String, Object> MATCH_ALL = ImmutableMap.of("match_all", Collections.emptyMap());

    public IndexFeatures getIndexFeatures() {
        return new IndexFeatures.Builder()
                .setDefaultStringMapping(Mapping.TEXT)
                .supportedStringMappings(Mapping.TEXT, Mapping.TEXTSTRING, Mapping.STRING)
                .setWildcardField("_all")
                .supportsCardinality(Cardinality.SINGLE)
                .supportsCardinality(Cardinality.LIST)
                .supportsCardinality(Cardinality.SET)
                .supportsNanoseconds()
                .supportsCustomAnalyzer()
                .supportsGeoExists()
                .supportNotQueryNormalForm()
                .build();
    }

    public Map<String, Object> createKeywordMapping() {
        return ImmutableMap.of(OPENSEARCH_TYPE_KEY, "keyword");
    }

    public Map<String, Object> createTextMapping(final String textAnalyzer) {
        final ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder().put(
                OPENSEARCH_TYPE_KEY,
                "text");
        return textAnalyzer != null ? builder.put(OPENSEARCH_ANALYZER, textAnalyzer).build() : builder.build();
    }

    public String scriptLang() {
        return "painless";
    }

    public ImmutableMap.Builder<String, Object> prepareScript(final String source) {
        final Map<String, Object> script = ImmutableMap.of(
                OPENSEARCH_SOURCE_KEY, source,
                OpenSearchConstants.OPENSEARCH_LANG_KEY, scriptLang());
        return ImmutableMap.<String, Object>builder().put(OPENSEARCH_SCRIPT_KEY, script);
    }

    public ImmutableMap.Builder<String, Object> prepareStoredScript(
            final String scriptId,
            final List<Map<String, Object>> fields) {
        final Map<String, Object> script = ImmutableMap.of(
                OPENSEARCH_ID_KEY, scriptId,
                OPENSEARCH_PARAMS_KEY, ImmutableMap.of(OPENSEARCH_PARAMS_FIELDS_KEY, fields));
        return ImmutableMap.<String, Object>builder().put(OPENSEARCH_SCRIPT_KEY, script);
    }

    public ImmutableMap.Builder<String, Object> prepareInlineScript(
            final String source,
            final List<Map<String, Object>> fields) {
        final Map<String, Object> script = ImmutableMap.of(
                OPENSEARCH_SOURCE_KEY, source,
                OPENSEARCH_PARAMS_KEY, ImmutableMap.of(OPENSEARCH_PARAMS_FIELDS_KEY, fields),
                OpenSearchConstants.OPENSEARCH_LANG_KEY, scriptLang());
        return ImmutableMap.<String, Object>builder().put(OPENSEARCH_SCRIPT_KEY, script);
    }

    public Map<String, Object> prepareQuery(final Map<String, Object> query) {
        return query;
    }

    public Map<String, Object> term(final String key, final Object value) {
        return ImmutableMap.of("term", ImmutableMap.of(key, value));
    }

    public Map<String, Object> contains(final String key, final List<String> terms) {
        return boolMust(terms.stream().map(term -> term(key, term)).collect(Collectors.toList()));
    }

    public Map<String, Object> boolMust(final List<Map<String, Object>> queries) {
        return queries.size() > 1
                ? ImmutableMap.of("bool", ImmutableMap.of("must", queries))
                : queries.get(0);
    }

    public Map<String, Object> boolMustNot(final Map<String, Object> query) {
        return ImmutableMap.of("bool", ImmutableMap.of("must_not", query));
    }

    public Map<String, Object> boolShould(final List<Map<String, Object>> queries) {
        return ImmutableMap.of("bool", ImmutableMap.of("should", queries));
    }

    public Map<String, Object> boolFilter(final Map<String, Object> query) {
        return ImmutableMap.of("bool", ImmutableMap.of("must", MATCH_ALL, "filter", query));
    }

    public Map<String, Object> lt(final String key, final Object value) {
        return ImmutableMap.of("range", ImmutableMap.of(key, ImmutableMap.of("lt", value)));
    }

    public Map<String, Object> lte(final String key, final Object value) {
        return ImmutableMap.of("range", ImmutableMap.of(key, ImmutableMap.of("lte", value)));
    }

    public Map<String, Object> gt(final String key, final Object value) {
        return ImmutableMap.of("range", ImmutableMap.of(key, ImmutableMap.of("gt", value)));
    }

    public Map<String, Object> gte(final String key, final Object value) {
        return ImmutableMap.of("range", ImmutableMap.of(key, ImmutableMap.of("gte", value)));
    }

    public Map<String, Object> prefix(final String key, final Object value) {
        return ImmutableMap.of("prefix", ImmutableMap.of(key, value));
    }

    public Map<String, Object> regexp(final String key, final Object value) {
        return ImmutableMap.of("regexp", ImmutableMap.of(key, value));
    }

    public Map<String, Object> exists(final String key) {
        return ImmutableMap.of("exists", ImmutableMap.of("field", key));
    }

    public Map<String, Object> match(final String key, final Object value) {
        return match(key, value, null);
    }

    public Map<String, Object> matchPhrase(final String key, final Object value) {
        return ImmutableMap.of("match_phrase", ImmutableMap.of(key, value));
    }

    public Map<String, Object> fuzzyMatch(final String key, final Object value) {
        return match(key, value, "AUTO");
    }

    public Map<String, Object> match(final String key, final Object value, final String fuzziness) {
        final ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .put("query", value)
                .put("operator", "and");
        if (fuzziness != null) {
            builder.put("fuzziness", fuzziness);
        }
        return ImmutableMap.of("match", ImmutableMap.of(key, builder.build()));
    }

    public Map<String, Object> queryString(final Object query) {
        return ImmutableMap.of("query_string", ImmutableMap.of("query", query));
    }

    public Map<String, Object> geoDistance(
            final String key,
            final double lat,
            final double lon,
            final double radius) {
        return filter(ImmutableMap.of(
                "geo_distance",
                ImmutableMap.of("distance", radius + "km", key, ImmutableList.of(lon, lat))));
    }

    public Map<String, Object> geoBoundingBox(
            final String key,
            final double minLat,
            final double minLon,
            final double maxLat,
            final double maxLon) {
        return filter(ImmutableMap.of(
                "geo_bounding_box",
                ImmutableMap.of(
                        key,
                        ImmutableMap.of(
                                "top_left", ImmutableList.of(minLon, maxLat),
                                "bottom_right", ImmutableList.of(maxLon, minLat)))));
    }

    public Map<String, Object> geoPolygon(final String key, final List<List<Double>> points) {
        return filter(ImmutableMap.of(
                "geo_polygon",
                ImmutableMap.of(key, ImmutableMap.of("points", points))));
    }

    public Map<String, Object> geoShape(
            final String key,
            final Map<String, Object> geoShape,
            final Geo predicate) {
        final String relation = predicate == Geo.INTERSECT
                ? "intersects"
                : predicate.name().toLowerCase();
        return filter(ImmutableMap.of(
                "geo_shape",
                ImmutableMap.of(
                        key,
                        ImmutableMap.of("shape", geoShape, "relation", relation))));
    }

    public Map<String, Object> filter(final Map<String, Object> query) {
        return boolFilter(query);
    }

    public Map<String, Object> createRequestBody(
            final OpenSearchRequest request,
            final Parameter[] parameters) {
        final Map<String, Object> requestBody = createRequestBody(request.getQuery(), parameters);

        if (request.getSize() != null) {
            requestBody.put("size", request.getSize());
        }
        if (request.getFrom() != null) {
            requestBody.put("from", request.getFrom());
        }
        if (!request.getSorts().isEmpty()) {
            requestBody.put("sort", request.getSorts());
        }
        if (request.isDisableSourceRetrieval()) {
            requestBody.put("_source", false);
        }

        return requestBody;
    }

    public Map<String, Object> createRequestBody(
            final Map<String, Object> query,
            final Parameter[] parameters) {
        final Map<String, Object> requestBody = new HashMap<>();

        if (query != null) {
            requestBody.put("query", query);
        }

        if (parameters != null) {
            for (Parameter parameter : parameters) {
                requestBody.put(parameter.key(), parameter.value());
            }
        }

        return requestBody;
    }
}
