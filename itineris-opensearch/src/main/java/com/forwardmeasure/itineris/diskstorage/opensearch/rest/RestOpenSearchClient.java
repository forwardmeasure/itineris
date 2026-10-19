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

package com.forwardmeasure.itineris.diskstorage.opensearch.rest;

import static com.forwardmeasure.itineris.util.encoding.StringEncoding.UTF8_CHARSET;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.tinkerpop.shaded.jackson.core.JsonParseException;
import org.apache.tinkerpop.shaded.jackson.core.JsonProcessingException;
import org.apache.tinkerpop.shaded.jackson.core.type.TypeReference;
import org.apache.tinkerpop.shaded.jackson.databind.JsonMappingException;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectReader;
import org.apache.tinkerpop.shaded.jackson.databind.ObjectWriter;
import org.apache.tinkerpop.shaded.jackson.databind.SerializationFeature;
import org.apache.tinkerpop.shaded.jackson.databind.module.SimpleModule;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.forwardmeasure.itineris.core.attribute.Geoshape;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchClient;
import com.forwardmeasure.itineris.diskstorage.opensearch.OpenSearchMutation;
import com.forwardmeasure.itineris.diskstorage.opensearch.mapping.IndexMapping;
import com.forwardmeasure.itineris.diskstorage.opensearch.mapping.TypelessIndexMappings;
import com.forwardmeasure.itineris.diskstorage.opensearch.script.OpenSearchScriptResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

public class RestOpenSearchClient implements OpenSearchClient {

    private static final Logger log = LoggerFactory.getLogger(RestOpenSearchClient.class);

    private static final String REQUEST_TYPE_DELETE = "DELETE";
    private static final String REQUEST_TYPE_GET = "GET";
    private static final String REQUEST_TYPE_POST = "POST";
    private static final String REQUEST_TYPE_PUT = "PUT";
    private static final String REQUEST_TYPE_HEAD = "HEAD";
    private static final String REQUEST_SEPARATOR = "/";
    private static final String REQUEST_PARAM_BEGINNING = "?";
    private static final String REQUEST_PARAM_SEPARATOR = "&";

    private static final String RETRY_ON_CONFLICT_KEY = "retry_on_conflict";

    private static final byte[] NEW_LINE_BYTES = "\n".getBytes(UTF8_CHARSET);

    private static final ObjectMapper mapper;
    private static final ObjectReader mapReader;
    private static final ObjectWriter mapWriter;

    static {
        final SimpleModule module = new SimpleModule();
        module.addSerializer(new Geoshape.GeoshapeGsonSerializerV2d0());
        mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapReader = mapper.readerWithView(Map.class).forType(HashMap.class);
        mapWriter = mapper.writerWithView(Map.class);
    }

    private static final Function<StringBuilder, StringBuilder> APPEND_OP = sb -> sb
            .append(sb.length() == 0 ? REQUEST_PARAM_BEGINNING : REQUEST_PARAM_SEPARATOR);

    private final RestClient delegate;
    private String bulkRefresh;
    private boolean bulkRefreshEnabled = false;
    private final String scrollKeepAlive;
    private Integer retryOnConflict;
    private final int retryAttemptLimit;
    private final Set<Integer> retryOnErrorCodes;
    private final long retryInitialWaitMs;
    private final long retryMaxWaitMs;
    private final int bulkChunkSerializedLimitBytes;

    public RestOpenSearchClient(
            final RestClient delegate,
            final int scrollKeepAlive,
            final int retryAttemptLimit,
            final Set<Integer> retryOnErrorCodes,
            final long retryInitialWaitMs,
            final long retryMaxWaitMs,
            final int bulkChunkSerializedLimitBytes) {
        this.delegate = delegate;
        this.scrollKeepAlive = scrollKeepAlive + "s";
        this.retryAttemptLimit = retryAttemptLimit;
        this.retryOnErrorCodes = Collections.unmodifiableSet(retryOnErrorCodes);
        this.retryInitialWaitMs = retryInitialWaitMs;
        this.retryMaxWaitMs = retryMaxWaitMs;
        this.bulkChunkSerializedLimitBytes = bulkChunkSerializedLimitBytes;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void clusterHealthRequest(final String timeout) throws IOException {
        final Request clusterHealthRequest = new Request(
                REQUEST_TYPE_GET,
                REQUEST_SEPARATOR + "_cluster" + REQUEST_SEPARATOR + "health");
        clusterHealthRequest.addParameter("wait_for_status", "yellow");
        clusterHealthRequest.addParameter("timeout", timeout);

        final Response response = delegate.performRequest(clusterHealthRequest);
        try (InputStream inputStream = response.getEntity().getContent()) {
            final Map<String, Object> values = mapReader.readValue(inputStream);
            if (!values.containsKey("timed_out")) {
                throw new IOException("Unexpected response for OpenSearch cluster health request");
            } else if (!Objects.equals(values.get("timed_out"), false)) {
                throw new IOException("OpenSearch timeout waiting for yellow status");
            }
        }
    }

    @Override
    public boolean indexExists(final String indexName) throws IOException {
        final Response response = delegate.performRequest(
                new Request(REQUEST_TYPE_HEAD, REQUEST_SEPARATOR + indexName));
        return response.getStatusLine().getStatusCode() == 200;
    }

    @Override
    public boolean isIndex(final String indexName) {
        try {
            final Response response = delegate.performRequest(
                    new Request(REQUEST_TYPE_GET, REQUEST_SEPARATOR + indexName));
            try (InputStream inputStream = response.getEntity().getContent()) {
                return mapper.readValue(inputStream, Map.class).containsKey(indexName);
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public boolean isAlias(final String aliasName) {
        try {
            delegate.performRequest(new Request(
                    REQUEST_TYPE_GET,
                    REQUEST_SEPARATOR + "_alias" + REQUEST_SEPARATOR + aliasName));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public void createStoredScript(final String scriptName, final Map<String, Object> script) throws IOException {
        final Request request = new Request(
                REQUEST_TYPE_POST,
                REQUEST_SEPARATOR + "_scripts" + REQUEST_SEPARATOR + scriptName);

        performRequest(request, mapWriter.writeValueAsBytes(script));
    }

    @Override
    public OpenSearchScriptResponse getStoredScript(final String scriptName) throws IOException {
        final Request request = new Request(
                REQUEST_TYPE_GET,
                REQUEST_SEPARATOR + "_scripts" + REQUEST_SEPARATOR + scriptName);

        try {
            final Response response = delegate.performRequest(request);

            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("Error executing request: " + response.getStatusLine().getReasonPhrase());
            }

            try (InputStream inputStream = response.getEntity().getContent()) {
                return mapper.readValue(inputStream, new TypeReference<OpenSearchScriptResponse>() {
                });
            } catch (JsonParseException | JsonMappingException | ResponseException e) {
                throw new IOException("Error parsing OpenSearch script response", e);
            }
        } catch (ResponseException e) {
            final Response response = e.getResponse();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                final OpenSearchScriptResponse openSearchScriptResponse = new OpenSearchScriptResponse();
                openSearchScriptResponse.setFound(false);
                return openSearchScriptResponse;
            }

            throw new IOException("Error executing request: " + response.getStatusLine().getReasonPhrase(), e);
        }
    }

    @Override
    public void createIndex(final String indexName, Map<String, Object> settings) throws IOException {
        final Request request = new Request(REQUEST_TYPE_PUT, REQUEST_SEPARATOR + indexName);

        if (settings != null && !settings.isEmpty()) {
            final Map<String, Object> updatedSettings = new HashMap<>();
            updatedSettings.put("settings", settings);
            settings = updatedSettings;
        }

        performRequest(request, mapWriter.writeValueAsBytes(settings));
    }

    @Override
    public void updateIndexSettings(final String indexName, final Map<String, Object> settings) throws IOException {
        performRequest(
                REQUEST_TYPE_PUT,
                REQUEST_SEPARATOR + indexName + REQUEST_SEPARATOR + "_settings",
                mapWriter.writeValueAsBytes(settings));
    }

    @Override
    public void updateClusterSettings(final Map<String, Object> settings) throws IOException {
        performRequest(
                REQUEST_TYPE_PUT,
                REQUEST_SEPARATOR + "_cluster" + REQUEST_SEPARATOR + "settings",
                mapWriter.writeValueAsBytes(settings));
    }

    @Override
    public void addAlias(final String alias, final String index) throws IOException {
        final Map<String, Object> actionAlias = ImmutableMap.of(
                "actions",
                ImmutableList.of(ImmutableMap.of("add", ImmutableMap.of("index", index, "alias", alias))));
        performRequest(REQUEST_TYPE_POST, REQUEST_SEPARATOR + "_aliases", mapWriter.writeValueAsBytes(actionAlias));
    }

    @Override
    public Map<String, Object> getIndexSettings(final String indexName) throws IOException {
        final Response response = performRequest(
                REQUEST_TYPE_GET,
                REQUEST_SEPARATOR + indexName + REQUEST_SEPARATOR + "_settings",
                null);
        try (InputStream inputStream = response.getEntity().getContent()) {
            final Map<String, RestIndexSettings> settings = mapper.readValue(
                    inputStream,
                    new TypeReference<Map<String, RestIndexSettings>>() {
                    });
            return settings == null ? null : settings.get(indexName).getSettings().getMap();
        }
    }

    @Override
    public void createMapping(final String indexName, final String typeName, final Map<String, Object> mapping)
            throws IOException {
        final Request request = new Request(
                REQUEST_TYPE_PUT,
                REQUEST_SEPARATOR + indexName + REQUEST_SEPARATOR + "_mapping");

        performRequest(request, mapWriter.writeValueAsBytes(mapping));
    }

    @Override
    public IndexMapping getMapping(final String indexName, final String typeName) throws IOException {
        final Request request = new Request(
                REQUEST_TYPE_GET,
                REQUEST_SEPARATOR + indexName + REQUEST_SEPARATOR + "_mapping");

        try (InputStream inputStream = performRequest(request, null).getEntity().getContent()) {
            final Map<String, TypelessIndexMappings> settings = mapper.readValue(
                    inputStream,
                    new TypeReference<Map<String, TypelessIndexMappings>>() {
                    });
            return settings != null ? settings.get(indexName).getMappings() : null;
        } catch (JsonParseException | JsonMappingException | ResponseException e) {
            log.info("Error when trying to get OpenSearch mapping", e);
            return null;
        }
    }

    @Override
    public void deleteIndex(final String indexName) throws IOException {
        if (isAlias(indexName)) {
            final String path = REQUEST_SEPARATOR + "_alias" + REQUEST_SEPARATOR + indexName;
            final Response response = performRequest(REQUEST_TYPE_GET, path, null);
            try (InputStream inputStream = response.getEntity().getContent()) {
                final Map<String, Object> records = mapper.readValue(
                        inputStream,
                        new TypeReference<Map<String, Object>>() {
                        });
                if (records == null) {
                    return;
                }
                for (String index : records.keySet()) {
                    if (indexExists(index)) {
                        performRequest(REQUEST_TYPE_DELETE, REQUEST_SEPARATOR + index, null);
                    }
                }
            }
        } else if (indexExists(indexName)) {
            performRequest(REQUEST_TYPE_DELETE, REQUEST_SEPARATOR + indexName, null);
        }
    }

    @Override
    public void clearStore(final String indexName, final String storeName) throws IOException {
        final String name = indexName + "_" + storeName;
        if (indexExists(name)) {
            performRequest(REQUEST_TYPE_DELETE, REQUEST_SEPARATOR + name, null);
        }
    }

    @VisibleForTesting
    class RequestBytes {
        final byte[] requestBytes;
        final byte[] requestSource;

        @VisibleForTesting
        RequestBytes(final OpenSearchMutation request) throws JsonProcessingException {
            final Map<String, Object> requestData = new HashMap<>();
            requestData.put("_index", request.getIndex());
            requestData.put("_id", request.getId());

            if (retryOnConflict != null && request.getRequestType() == OpenSearchMutation.RequestType.UPDATE) {
                requestData.put(RETRY_ON_CONFLICT_KEY, retryOnConflict);
            }

            this.requestBytes = mapWriter.writeValueAsBytes(
                    ImmutableMap.of(request.getRequestType().name().toLowerCase(), requestData));
            this.requestSource = request.getSource() != null
                    ? mapWriter.writeValueAsBytes(request.getSource())
                    : null;
        }

        @VisibleForTesting
        int getSerializedSize() {
            int serializedSize = this.requestBytes.length + 1;
            if (this.requestSource != null) {
                serializedSize += this.requestSource.length + 1;
            }
            return serializedSize;
        }

        private void writeTo(final OutputStream outputStream) throws IOException {
            outputStream.write(this.requestBytes);
            outputStream.write(NEW_LINE_BYTES);
            if (this.requestSource != null) {
                outputStream.write(this.requestSource);
                outputStream.write(NEW_LINE_BYTES);
            }
        }
    }

    private Pair<String, byte[]> buildBulkRequestInput(
            final List<RequestBytes> requests,
            final String ingestPipeline) throws IOException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (RequestBytes request : requests) {
            request.writeTo(outputStream);
        }

        final StringBuilder bulkRequestQueryParameters = new StringBuilder();
        if (ingestPipeline != null) {
            APPEND_OP.apply(bulkRequestQueryParameters).append("pipeline=").append(ingestPipeline);
        }
        if (bulkRefreshEnabled) {
            APPEND_OP.apply(bulkRequestQueryParameters).append("refresh=").append(bulkRefresh);
        }
        final String bulkRequestPath = REQUEST_SEPARATOR + "_bulk" + bulkRequestQueryParameters;
        return Pair.with(bulkRequestPath, outputStream.toByteArray());
    }

    private List<Triplet<Object, Integer, RequestBytes>> pairErrorsWithSubmittedMutation(
            final List<Map<String, RestBulkResponse.RestBulkItemResponse>> bulkResponseItems,
            final List<RequestBytes> submittedBulkRequestItems) {
        final List<Triplet<Object, Integer, RequestBytes>> errors = new ArrayList<>(bulkResponseItems.size());
        for (int itemIndex = 0; itemIndex < bulkResponseItems.size(); itemIndex++) {
            final Collection<RestBulkResponse.RestBulkItemResponse> bulkResponseItem = bulkResponseItems.get(itemIndex)
                    .values();
            if (bulkResponseItem.size() > 1) {
                throw new IllegalStateException("There should only be a single item per bulk response item entry");
            }
            final RestBulkResponse.RestBulkItemResponse item = bulkResponseItem.iterator().next();
            if (item.getError() != null && item.getStatus() != HttpStatus.SC_NOT_FOUND) {
                errors.add(Triplet.with(item.getError(), item.getStatus(), submittedBulkRequestItems.get(itemIndex)));
            }
        }
        return errors;
    }

    @VisibleForTesting
    class BulkRequestChunker implements Iterator<List<RequestBytes>> {

        private final PeekingIterator<RequestBytes> requestIterator;
        private final int[] exceptionallyLargeRequests;

        @VisibleForTesting
        BulkRequestChunker(final List<OpenSearchMutation> requests) throws JsonProcessingException {
            final List<RequestBytes> serializedRequests = new ArrayList<>(requests.size());
            final List<Integer> requestSizesThatWereTooLarge = new ArrayList<>();

            for (OpenSearchMutation request : requests) {
                final RequestBytes requestBytes = new RequestBytes(request);
                final int requestSerializedSize = requestBytes.getSerializedSize();
                if (requestSerializedSize <= bulkChunkSerializedLimitBytes) {
                    serializedRequests.add(requestBytes);
                } else {
                    requestSizesThatWereTooLarge.add(requestSerializedSize);
                }
            }

            this.requestIterator = Iterators.peekingIterator(serializedRequests.iterator());
            this.exceptionallyLargeRequests = requestSizesThatWereTooLarge.isEmpty()
                    ? null
                    : requestSizesThatWereTooLarge.stream().mapToInt(Integer::intValue).toArray();
        }

        @Override
        public boolean hasNext() {
            return requestIterator.hasNext() || exceptionallyLargeRequests != null;
        }

        @Override
        public List<RequestBytes> next() {
            final List<RequestBytes> serializedRequests = new ArrayList<>();
            int chunkSerializedTotal = 0;

            while (requestIterator.hasNext()) {
                final RequestBytes peeked = requestIterator.peek();
                chunkSerializedTotal += peeked.getSerializedSize();
                if (chunkSerializedTotal <= bulkChunkSerializedLimitBytes) {
                    serializedRequests.add(requestIterator.next());
                } else {
                    return serializedRequests;
                }
            }

            if (serializedRequests.isEmpty() && this.exceptionallyLargeRequests != null) {
                throw new IllegalArgumentException(String.format(
                        "Bulk request item(s) larger than permitted chunk limit. Limit is %s. Serialized item size(s) %s",
                        bulkChunkSerializedLimitBytes,
                        Arrays.toString(this.exceptionallyLargeRequests)));
            }

            return serializedRequests;
        }
    }

    @Override
    public void bulkRequest(final List<OpenSearchMutation> requests, final String ingestPipeline) throws IOException {
        final BulkRequestChunker bulkRequestChunker = new BulkRequestChunker(requests);

        while (bulkRequestChunker.hasNext()) {
            List<RequestBytes> bulkRequestChunk = bulkRequestChunker.next();
            int retryCount = 0;

            while (true) {
                final Pair<String, byte[]> bulkRequestInput = buildBulkRequestInput(bulkRequestChunk, ingestPipeline);

                final Response response = performRequest(
                        REQUEST_TYPE_POST,
                        bulkRequestInput.getValue0(),
                        bulkRequestInput.getValue1());

                try (InputStream inputStream = response.getEntity().getContent()) {
                    final RestBulkResponse bulkResponse = mapper.readValue(inputStream, RestBulkResponse.class);
                    final List<Triplet<Object, Integer, RequestBytes>> bulkItemsThatFailed = pairErrorsWithSubmittedMutation(
                            bulkResponse.getItems(), bulkRequestChunk);

                    if (!bulkItemsThatFailed.isEmpty()) {
                        final Set<Integer> errorCodes = bulkItemsThatFailed.stream()
                                .map(Triplet::getValue1)
                                .collect(Collectors.toSet());

                        if (retryCount < retryAttemptLimit && retryOnErrorCodes.containsAll(errorCodes)) {
                            bulkRequestChunk = bulkItemsThatFailed.stream()
                                    .map(Triplet::getValue2)
                                    .collect(Collectors.toList());
                            performRetryWait(retryCount);
                            retryCount++;
                        } else {
                            final List<Object> errorItems = bulkItemsThatFailed.stream()
                                    .map(Triplet::getValue0)
                                    .collect(Collectors.toList());
                            errorItems.forEach(error -> log.error("Failed to execute OpenSearch query: {}", error));
                            throw new IOException("Failure(s) in OpenSearch bulk request: " + errorItems);
                        }
                    } else {
                        break;
                    }
                }
            }
        }
    }

    public void setRetryOnConflict(final Integer retryOnConflict) {
        this.retryOnConflict = retryOnConflict;
    }

    @Override
    public long countTotal(final String indexName, final Map<String, Object> requestData) throws IOException {
        final Request request = new Request(
                REQUEST_TYPE_GET,
                REQUEST_SEPARATOR + indexName + REQUEST_SEPARATOR + "_count");

        final byte[] requestDataBytes = mapper.writeValueAsBytes(requestData);
        if (log.isDebugEnabled()) {
            log.debug("OpenSearch request: {}",
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestData));
        }

        final Response response = performRequest(request, requestDataBytes);
        try (InputStream inputStream = response.getEntity().getContent()) {
            return mapper.readValue(inputStream, RestCountResponse.class).getCount();
        }
    }

    private double executeAggs(
            final String indexName,
            final Map<String, Object> requestData,
            final String agg,
            final String fieldName) throws IOException {

        final Request request = new Request(
                REQUEST_TYPE_GET,
                REQUEST_SEPARATOR + indexName + REQUEST_SEPARATOR + "_search");

        requestData.put("aggs",
                ImmutableMap.of("agg_result", ImmutableMap.of(agg, ImmutableMap.of("field", fieldName))));

        final byte[] requestDataBytes = mapper.writeValueAsBytes(requestData);
        if (log.isDebugEnabled()) {
            log.debug("OpenSearch request: {}",
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestData));
        }

        final Response response = performRequest(request, requestDataBytes);
        try (InputStream inputStream = response.getEntity().getContent()) {
            return mapper.readValue(inputStream, RestAggResponse.class)
                    .getAggregations()
                    .getAggResult()
                    .getValue();
        }
    }

    private Number adaptNumberType(final double value, final Class<? extends Number> expectedType) {
        if (expectedType == null) {
            return value;
        } else if (Byte.class.isAssignableFrom(expectedType)) {
            return (byte) value;
        } else if (Short.class.isAssignableFrom(expectedType)) {
            return (short) value;
        } else if (Integer.class.isAssignableFrom(expectedType)) {
            return (int) value;
        } else if (Long.class.isAssignableFrom(expectedType)) {
            return (long) value;
        } else if (Float.class.isAssignableFrom(expectedType)) {
            return (float) value;
        } else {
            return value;
        }
    }

    @Override
    public Number min(
            final String indexName,
            final Map<String, Object> requestData,
            final String fieldName,
            final Class<? extends Number> expectedType) throws IOException {
        return adaptNumberType(executeAggs(indexName, requestData, "min", fieldName), expectedType);
    }

    @Override
    public Number max(
            final String indexName,
            final Map<String, Object> requestData,
            final String fieldName,
            final Class<? extends Number> expectedType) throws IOException {
        return adaptNumberType(executeAggs(indexName, requestData, "max", fieldName), expectedType);
    }

    @Override
    public double avg(final String indexName, final Map<String, Object> requestData, final String fieldName)
            throws IOException {
        return executeAggs(indexName, requestData, "avg", fieldName);
    }

    @Override
    public Number sum(
            final String indexName,
            final Map<String, Object> requestData,
            final String fieldName,
            final Class<? extends Number> expectedType) throws IOException {
        final double sum = executeAggs(indexName, requestData, "sum", fieldName);
        if (Float.class.isAssignableFrom(expectedType) || Double.class.isAssignableFrom(expectedType)) {
            return sum;
        }
        return (long) sum;
    }

    @Override
    public RestSearchResponse search(
            final String indexName,
            final Map<String, Object> requestData,
            final boolean useScroll) throws IOException {
        final StringBuilder path = new StringBuilder(REQUEST_SEPARATOR).append(indexName);
        path.append(REQUEST_SEPARATOR).append("_search");
        if (useScroll) {
            path.append(REQUEST_PARAM_BEGINNING).append("scroll=").append(scrollKeepAlive);
        }
        return search(requestData, path.toString());
    }

    @Override
    public RestSearchResponse search(final String scrollId) throws IOException {
        final Map<String, Object> requestData = new HashMap<>();
        requestData.put("scroll", scrollKeepAlive);
        requestData.put("scroll_id", scrollId);
        return search(requestData, REQUEST_SEPARATOR + "_search" + REQUEST_SEPARATOR + "scroll");
    }

    @Override
    public void deleteScroll(final String scrollId) throws IOException {
        delegate.performRequest(new Request(
                REQUEST_TYPE_DELETE,
                REQUEST_SEPARATOR + "_search" + REQUEST_SEPARATOR + "scroll" + REQUEST_SEPARATOR + scrollId));
    }

    public void setBulkRefresh(final String bulkRefresh) {
        this.bulkRefresh = bulkRefresh;
        this.bulkRefreshEnabled = bulkRefresh != null && !bulkRefresh.equalsIgnoreCase("false");
    }

    private RestSearchResponse search(final Map<String, Object> requestData, final String path) throws IOException {
        final Request request = new Request(REQUEST_TYPE_POST, path);

        final byte[] requestDataBytes = mapper.writeValueAsBytes(requestData);
        if (log.isDebugEnabled()) {
            log.debug("OpenSearch request: {}",
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestData));
        }

        final Response response = performRequest(request, requestDataBytes);
        try (InputStream inputStream = response.getEntity().getContent()) {
            return mapper.readValue(inputStream, RestSearchResponse.class);
        }
    }

    private Response performRequest(final String method, final String path, final byte[] requestData)
            throws IOException {
        return performRequest(new Request(method, path), requestData);
    }

    private Response performRequestWithRetry(final Request request) throws IOException {
        int retryCount = 0;
        while (true) {
            try {
                return delegate.performRequest(request);
            } catch (ResponseException e) {
                if (!retryOnErrorCodes.contains(e.getResponse().getStatusLine().getStatusCode())
                        || retryCount >= retryAttemptLimit) {
                    throw e;
                }
                performRetryWait(retryCount);
                retryCount++;
            }
        }
    }

    private void performRetryWait(final int retryCount) {
        final long waitDurationMs = Math.min(
                (long) (retryInitialWaitMs * Math.pow(10, retryCount)),
                retryMaxWaitMs);

        log.warn(
                "Retrying OpenSearch request in {} ms. Attempt {} of {}",
                waitDurationMs,
                retryCount + 1,
                retryAttemptLimit);

        try {
            Thread.sleep(waitDurationMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    String.format(
                            "Thread interrupted while waiting for retry attempt %d of %d",
                            retryCount + 1,
                            retryAttemptLimit),
                    interruptedException);
        }
    }

    private Response performRequest(final Request request, final byte[] requestData) throws IOException {
        final HttpEntity entity = requestData != null
                ? new ByteArrayEntity(requestData, ContentType.APPLICATION_JSON)
                : null;

        request.setEntity(entity);

        final Response response = performRequestWithRetry(request);

        if (response.getStatusLine().getStatusCode() >= 400) {
            throw new IOException("Error executing request: " + response.getStatusLine().getReasonPhrase());
        }

        return response;
    }
}
