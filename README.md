# itineris

A production-ready graph database server based on a focused fork of
[JanusGraph](https://janusgraph.org) (v1.1.0), built for
Kubernetes deployment with CQL (Cassandra) storage and OpenSearch indexing.

## Modules

| Module | Description |
|--------|-------------|
| `itineris-core` | TinkerPop provider SPI, query planning, transaction model |
| `itineris-cql` | Cassandra/CQL storage backend |
| `itineris-es` | OpenSearch/Elasticsearch mixed index backend |
| `itineris-inmemory` | In-memory backend for unit testing |
| `itineris-server` | Gremlin Server (WebSocket + HTTP) |
| `itineris-grpc` | gRPC interface for schema management |
| `itineris-driver` | JanusGraph-specific Gremlin serializers |
| `itineris-test` | Shared test utilities |
| `itineris-backend-testutils` | Backend-agnostic test base classes |
| `itineris-mixed-index-utils` | Shared index utilities |

## Upstream

Forked from JanusGraph v1.1.0.
Upstream remote preserved for cherry-picks:

```bash
git fetch upstream
git cherry-pick <commit-sha>
```

## License

Apache 2.0 (inherited from JanusGraph)
