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

# Adds one commit to the upstream-rebranded branch: the rebranded tree of a JanusGraph commit.
#
# Usage: upstream-sync/sync.sh <janusgraph-ref>     e.g. master, v1.2.0, 1df9cd6a9
#
# Afterwards, merge upstream-rebranded into a branch cut from develop (see README.md).
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "Usage: $0 <janusgraph-ref>" >&2
    exit 1
fi
REF=$1
BRANCH=upstream-rebranded
UPSTREAM_URL=${UPSTREAM_URL:-git@github.com:JanusGraph/janusgraph.git}
CACHE=${XDG_CACHE_HOME:-$HOME/.cache}/itineris-upstream/janusgraph.git
HERE=$(cd "$(dirname "$0")" && pwd)
REPO=$(git -C "$HERE" rev-parse --show-toplevel)

# Upstream history is kept in a cache outside the repository, so none of it is pushed to origin.
if [ -d "$CACHE" ]; then
    git -C "$CACHE" fetch -q --tags --force origin '+refs/heads/*:refs/heads/*'
else
    mkdir -p "$(dirname "$CACHE")"
    git clone -q --bare --filter=blob:none "$UPSTREAM_URL" "$CACHE"
fi
SHA=$(git -C "$CACHE" rev-parse --verify "$REF^{commit}")
DESC=$(git -C "$CACHE" describe --tags --always "$SHA")

WORK=$(mktemp -d)
trap 'git -C "$REPO" worktree remove --force "$WORK/wt" 2>/dev/null || true; rm -rf "$WORK"' EXIT

mkdir "$WORK/src"
git -C "$CACHE" archive "$SHA" | tar -x -C "$WORK/src"
"$HERE/rebrand.sh" "$WORK/src" "$WORK/out"

if git -C "$REPO" rev-parse --verify -q "refs/heads/$BRANCH" >/dev/null; then
    git -C "$REPO" worktree add -q "$WORK/wt" "$BRANCH"
else
    git -C "$REPO" worktree add -q --detach "$WORK/wt"
    git -C "$WORK/wt" checkout -q --orphan "$BRANCH"
fi

# The branch holds exactly the rebranded tree, so files upstream deleted are deleted here too.
git -C "$WORK/wt" rm -rfq --ignore-unmatch .
git -C "$WORK/wt" clean -fdxq
cp -a "$WORK/out/." "$WORK/wt/"
git -C "$WORK/wt" add -A
if git -C "$WORK/wt" diff --cached --quiet && git -C "$WORK/wt" rev-parse -q --verify HEAD >/dev/null; then
    echo "$BRANCH already matches JanusGraph $DESC ($SHA)"
    exit 0
fi
git -C "$WORK/wt" commit -q -m "Upstream: JanusGraph $DESC" -m "Rebranded with upstream-sync/rebrand.sh from JanusGraph commit $SHA."
echo "$BRANCH now at JanusGraph $DESC ($SHA): $(git -C "$WORK/wt" rev-parse --short HEAD)"
