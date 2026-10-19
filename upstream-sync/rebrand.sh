#!/usr/bin/env bash
# Copyright 2026 ForwardMeasure
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Mechanically rebrands an upstream JanusGraph source tree into the Itineris layout.
#
# Usage: rebrand.sh <upstream-tree> <output-dir>
#
# The output contains only the upstream-derived parts of Itineris (see MODULES below).
# Everything else in the Itineris repository is Itineris-owned and never synced.
# See upstream-sync/README.md for how this fits into the sync workflow.
set -euo pipefail

if [ $# -ne 2 ]; then
    echo "Usage: $0 <upstream-tree> <output-dir>" >&2
    exit 1
fi
SRC=$(cd "$1" && pwd)
DEST=$2

# Upstream modules kept by Itineris: janusgraph-<m> becomes itineris-<m>.
MODULES="core cql berkeleyje inmemory driver server grpc hadoop mixed-index-utils test backend-testutils"
# Upstream paths copied under the same name.
VERBATIM="cassandra-hadoop-util pom.xml"

rm -rf "$DEST"
mkdir -p "$DEST"
DEST=$(cd "$DEST" && pwd)

for m in $MODULES; do
    cp -r "$SRC/janusgraph-$m" "$DEST/itineris-$m"
done
for p in $VERBATIM; do
    cp -r "$SRC/$p" "$DEST/$p"
done

# Move Java packages: org/janusgraph -> com/forwardmeasure/itineris
find "$DEST" -depth -type d -path '*/org/janusgraph' | while read -r d; do
    base=${d%/org/janusgraph}
    mkdir -p "$base/com/forwardmeasure"
    mv "$d" "$base/com/forwardmeasure/itineris"
    rmdir "$base/org" 2>/dev/null || true
done

# Rename files and directories whose names mention JanusGraph.
find "$DEST" -depth -name '*[Jj]anus[Gg]raph*' | while read -r f; do
    dir=$(dirname "$f")
    name=$(basename "$f")
    new=$(printf '%s' "$name" | sed -e 's/JanusGraph/Itineris/g' -e 's/janusgraph/itineris/g')
    mv "$f" "$dir/$new"
done

# Rewrite file contents. Upstream attribution (copyright lines naming the JanusGraph
# Authors, as Apache-2.0 section 4(c) requires) and URLs pointing at JanusGraph
# resources are protected from the rename.
find "$DEST" -type f -print0 | xargs -0 grep -lIZ -i 'janusgraph' | xargs -0 -r perl -pi -e '
    my @keep;
    s{(Copyright\b.*\bJanusGraph Authors|https?://[^\s"<>)\x27`]*)}{push @keep, $1; "\x00" . $#keep . "\x00"}ge;
    s{<groupId>org\.janusgraph</groupId>}{<groupId>com.forwardmeasure</groupId>}g;
    s{org\.janusgraph}{com.forwardmeasure.itineris}g;
    s{org/janusgraph}{com/forwardmeasure/itineris}g;
    s{JanusGraph}{Itineris}g;
    s{janusgraph}{itineris}g;
    s{JANUSGRAPH}{ITINERIS}g;
    s{Janusgraph}{Itineris}g;
    s{\x00(\d+)\x00}{$keep[$1]}g;
'
