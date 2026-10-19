# Syncing with upstream JanusGraph

Itineris is a fork of [JanusGraph](https://github.com/JanusGraph/janusgraph), taken at v1.1.0.
Most of the code is still upstream code under new names. This directory holds the tooling
that keeps those names mechanical, so upstream fixes can be merged with ordinary `git merge`.

## The idea

`rebrand.sh` turns an upstream source tree into the Itineris layout:

| Upstream                          | Itineris                              |
|-----------------------------------|---------------------------------------|
| `janusgraph-<module>/`            | `itineris-<module>/`                  |
| package `org.janusgraph`          | package `com.forwardmeasure.itineris` |
| groupId `org.janusgraph`          | groupId `com.forwardmeasure`          |
| `JanusGraph`, `janusgraph`, `JANUSGRAPH` in names and text | `Itineris`, `itineris`, `ITINERIS` |

Upstream's project version is replaced by a constant (`PINNED_VERSION` in `rebrand.sh`), so upstream
version bumps never touch Itineris's poms; the Itineris version is set only on the Itineris side.

Two things are left alone:

- Copyright lines naming the JanusGraph Authors. Apache-2.0 section 4(c) requires derived
  works to keep them.
- URLs, so links to upstream issues, docs and mailing lists keep working.

The branch `upstream-rebranded` holds nothing but `rebrand.sh` output, one commit per upstream
commit we have synced to. `develop` has it as an ancestor, so a merge sees exactly what upstream
changed since the last sync, already in Itineris names.

## What is synced

Only the upstream-derived parts listed in `rebrand.sh` (`MODULES` and `VERBATIM`): core, cql,
berkeleyje, inmemory, driver, server, grpc, hadoop, mixed-index-utils, test, backend-testutils,
cassandra-hadoop-util and the root `pom.xml`.

Everything else is Itineris-owned and never touched by a sync: `itineris-opensearch`,
`itineris-config`, `itineris-distribution`, `docs/`, and the top-level documents.

`itineris-opensearch` started as a copy of upstream's `janusgraph-es`, but with ES names changed
to OpenSearch names by hand, so upstream changes to `janusgraph-es` have to be ported by hand
for now.

## Syncing

```bash
upstream-sync/sync.sh master          # or a tag or commit, e.g. v1.2.0
git switch -c sync/janusgraph-<date> develop
git merge upstream-rebranded
# resolve conflicts, build, run the tests
```

Then review and merge the sync branch into `develop` as usual.

`sync.sh` keeps a blobless clone of JanusGraph in `~/.cache/itineris-upstream/`, so no upstream
history lands in this repository.

## Rules that keep merges cheap

- **Do not reformat upstream-derived files.** Turn off format-on-save for them. Reformatting
  turns every upstream change near the reformatted lines into a conflict.
- **Do not rename upstream identifiers by hand.** If a name should change, change `rebrand.sh`
  and regenerate; a hand edit is a permanent difference from upstream.
- **Keep real changes small and local.** Prefer adding new classes in Itineris-owned modules
  over editing upstream classes.
- In conflicts, prefer upstream's side for fixes we also made ourselves (e.g. the TinkerPop 3.8
  and Mockito 5 migrations); keep ours where Itineris is deliberately ahead (dependency versions,
  the OpenSearch-only index backend).
