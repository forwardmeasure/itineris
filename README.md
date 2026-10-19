# itineris

A production-ready graph database server based on a focused fork of
[JanusGraph](https://janusgraph.org) (v1.1.0), built for
Kubernetes deployment with CQL (Cassandra) storage and OpenSearch indexing.

## Modules

| Module | Description |
|--------|-------------|
| `itineris-core` | TinkerPop provider SPI, query planning, transaction model |
| `itineris-cql` | Cassandra/CQL storage backend |
| `itineris-opensearch` | OpenSearch mixed index backend |
| `itineris-berkeleyje` | BerkeleyDB JE storage backend |
| `itineris-inmemory` | In-memory backend for unit testing |
| `itineris-server` | Gremlin Server (WebSocket + HTTP) |
| `itineris-grpc` | gRPC interface for schema management |
| `itineris-driver` | Itineris-specific Gremlin serializers |
| `itineris-hadoop` | Hadoop/Spark (OLAP) integration |
| `itineris-config` | Registration of the standard configuration options |
| `itineris-distribution` | Server distribution and Docker image |
| `itineris-test` | Core test suite |
| `itineris-backend-testutils` | Backend-agnostic test base classes |
| `itineris-mixed-index-utils` | Shared index utilities |

## Upstream

Forked from JanusGraph v1.1.0 and last merged with JanusGraph master at
commit `1df9cd6a9` (17 Oct 2026). Itineris is developed independently since
then and no longer tracks JanusGraph.

Packages, classes and names differ from JanusGraph, so upstream commits do
not apply directly; a fix worth taking has to be ported by hand. The code
keeps the JanusGraph copyright notices, as the Apache License 2.0 requires
(see NOTICE.txt).

## License

Apache 2.0 (inherited from JanusGraph)
