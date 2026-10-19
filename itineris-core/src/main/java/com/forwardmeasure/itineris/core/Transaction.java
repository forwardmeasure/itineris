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

package com.forwardmeasure.itineris.core;

import org.apache.tinkerpop.gremlin.structure.Graph;
import com.forwardmeasure.itineris.core.schema.SchemaManager;
import com.forwardmeasure.itineris.graphdb.transaction.StandardItinerisTx;

import java.util.Collection;

/**
 * Transaction defines a transactional context for a {@link com.forwardmeasure.itineris.core.Itineris}. Since Itineris is a transactional graph
 * database, all interactions with the graph are mitigated by a Transaction.
 * <p>
 * All vertex and edge retrievals are channeled by a graph transaction which bundles all such retrievals, creations and
 * deletions into one transaction. A graph transaction is analogous to a
 * <a href="https://en.wikipedia.org/wiki/Database_transaction">database transaction</a>.
 * The isolation level and <a href="https://en.wikipedia.org/wiki/ACID">ACID support</a> are configured through the storage
 * backend, meaning whatever level of isolation is supported by the storage backend is mirrored by a graph transaction.
 * <p>
 * A graph transaction supports:
 * <ul>
 * <li>Creating vertices, properties and edges</li>
 * <li>Creating types</li>
 * <li>Index-based retrieval of vertices</li>
 * <li>Querying edges and vertices</li>
 * <li>Aborting and committing transaction</li>
 * </ul>
 *
 * @author Matthias Br&ouml;cheler (http://www.matthiasb.com)
 */
public interface Transaction extends Graph, SchemaManager {

   /* ---------------------------------------------------------------
    * Modifications
    * ---------------------------------------------------------------
    */

    /**
     * Creates a new vertex in the graph with the vertex label named by the argument.
     *
     * @param vertexLabel the name of the vertex label to use
     * @return a new vertex in the graph created in the context of this transaction
     */
    ItinerisVertex addVertex(String vertexLabel);

    @Override
    ItinerisVertex addVertex(Object... objects);

    /**
     * @return
     * @see Itineris#query()
     */
    ItinerisQuery<? extends ItinerisQuery> query();

    /**
     * @return a mixed index aggregation query which leverages mixed index for aggregation
     * @see StandardItinerisTx#mixedIndexAggQuery()
     */
    MixedIndexAggQuery mixedIndexAggQuery();

    /**
     * Returns a {@link com.forwardmeasure.itineris.core.ItinerisIndexQuery} to query for vertices or edges against the specified indexing backend using
     * the given query string. The query string is analyzed and answered by the underlying storage backend.
     * <p>
     * Note, that using indexQuery may ignore modifications in the current transaction.
     *
     * @param indexName Name of the index to query as configured
     * @param query Query string
     * @return ItinerisIndexQuery object to query the index directly
     */
    ItinerisIndexQuery indexQuery(String indexName, String query);

    /**
     * @return
     * @see Itineris#multiQuery(com.forwardmeasure.itineris.core.ItinerisVertex...)
     */
    ItinerisMultiVertexQuery<? extends ItinerisMultiVertexQuery> multiQuery(ItinerisVertex... vertices);

    /**
     * @return
     * @see Itineris#multiQuery(java.util.Collection)
     */
    ItinerisMultiVertexQuery<? extends ItinerisMultiVertexQuery> multiQuery(Collection<ItinerisVertex> vertices);

    @Override
    void close();


}
