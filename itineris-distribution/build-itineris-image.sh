#!/usr/bin/env bash
# Copyright 2024 JanusGraph Authors
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
set -euo pipefail
# ---------------------------------------------------------------------------
# build-itineris-image.sh
#
# Full build pipeline for the Itineris Docker image.
#
# Sources:
#   target/lib/                  Maven-produced runtime jars
#   target/bin/                  Maven-filtered shell scripts (tokens resolved)
#   target/conf/                 Maven-copied config files
#   docker/Dockerfile            read directly from source, always fresh
#   docker/docker-entrypoint.sh  read directly from source, always fresh
#   docker/scripts/              read directly from source, always fresh
#
# Pipeline:
#   1. mvn clean install  — compile all modules, produce jars
#   2. mvn package        — populate target/lib/, target/bin/, target/conf/
#   3. Assemble build context and docker build
#
# --skip-build skips steps 1+2, using existing target/lib/, target/bin/,
# target/conf/ — but always reads docker/ files fresh from source.
#
# Lives in: itineris/itineris-distribution/
# Run from: itineris/itineris-distribution/
#
# Options:
#   --version     1.1.0                        (default)
#   --registry    docker.io/forwardmeasure     (default)
#   --image       itineris                     (default)
#   --base-image  eclipse-temurin:17-jre       (default)
#   --java-home   /opt/java/jdk-17.0.15+6      (default)
#   --push                                     push after build
#   --no-cache                                 docker build --no-cache
#   --skip-build                               skip mvn build, use existing target/lib|bin|conf
# ---------------------------------------------------------------------------

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
DIST_DIR="${SCRIPT_DIR}"
REPO_ROOT="$( cd "${SCRIPT_DIR}/.." &> /dev/null && pwd )"
DIST_TARGET="${DIST_DIR}/target"
BUILD_CONTEXT="${DIST_TARGET}/build-context"
DOCKER_SOURCE="${DIST_DIR}/docker"

MVN_SKIP="-Drat.skip=true -Dskip.rat=true -DskipTests -Dmaven.test.skip=true -Dcheckstyle.skip=true"

VERSION="1.1.0"
REGISTRY="docker.io/forwardmeasure"
IMAGE_NAME="itineris"
BASE_IMAGE="eclipse-temurin:17-jre"
JDK17="/opt/java/jdk-17.0.15+6"
PUSH="false"
NO_CACHE=""
SKIP_BUILD="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)      VERSION="$2";    shift 2 ;;
    --registry)     REGISTRY="$2";   shift 2 ;;
    --image)        IMAGE_NAME="$2"; shift 2 ;;
    --base-image)   BASE_IMAGE="$2"; shift 2 ;;
    --java-home)    JDK17="$2";      shift 2 ;;
    --push)         PUSH="true";     shift 1 ;;
    --no-cache)     NO_CACHE="--no-cache"; shift 1 ;;
    --skip-build)   SKIP_BUILD="true"; shift 1 ;;
    *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
  esac
done

FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${VERSION}"
MVN="mvn"

echo "================================================================"
echo " Itineris Docker Image Builder"
echo "================================================================"
echo " Version:      ${VERSION}"
echo " Image:        ${FULL_IMAGE}"
echo " Base image:   ${BASE_IMAGE}"
echo " JDK 17:       ${JDK17}"
echo " Repo root:    ${REPO_ROOT}"
echo " Distribution: ${DIST_DIR}"
echo " Maven target: ${DIST_TARGET}"
echo " Docker source:${DOCKER_SOURCE}"
echo " Build context:${BUILD_CONTEXT}"
echo " Skip build:   ${SKIP_BUILD}"
echo "================================================================"
echo ""

## ---------------------------------------------------------------------------
## Preflight — docker source files must always exist
## ---------------------------------------------------------------------------
for required in \
    "${DOCKER_SOURCE}/Dockerfile" \
    "${DOCKER_SOURCE}/docker-entrypoint.sh" \
    "${DOCKER_SOURCE}/scripts"; do
  if [[ ! -e "${required}" ]]; then
    echo "ERROR: ${required} not found."
    exit 1
  fi
done

if [[ "${SKIP_BUILD}" == "false" ]]; then
  if [[ ! -d "${JDK17}" ]]; then
    echo "ERROR: JDK 17 not found at ${JDK17}"
    echo "       Override with --java-home <path>"
    exit 1
  fi
fi

## ---------------------------------------------------------------------------
## Step 1 — Build and install all Itineris modules
## ---------------------------------------------------------------------------
if [[ "${SKIP_BUILD}" == "false" ]]; then
  echo "==> Building Itineris (mvn clean install)..."
  JAVA_HOME="${JDK17}" ${MVN} clean install \
    ${MVN_SKIP} \
    -f "${REPO_ROOT}/pom.xml"
  echo ""

  echo "==> Packaging itineris-distribution (mvn package)..."
  JAVA_HOME="${JDK17}" ${MVN} package \
    ${MVN_SKIP} \
    -f "${DIST_DIR}/pom.xml"
  echo ""
else
  echo "==> Skipping Maven build (--skip-build)"
  echo ""
fi

## ---------------------------------------------------------------------------
## Preflight — verify Maven outputs exist
## ---------------------------------------------------------------------------
for required in lib bin conf; do
  if [[ ! -d "${DIST_TARGET}/${required}" ]]; then
    echo "ERROR: ${DIST_TARGET}/${required} not found."
    echo "       Run without --skip-build to compile first."
    exit 1
  fi
done

## ---------------------------------------------------------------------------
## Step 2 — Prepare fresh build context
## ---------------------------------------------------------------------------
echo "==> Preparing build context..."
rm -rf "${BUILD_CONTEXT}"
mkdir -p \
  "${BUILD_CONTEXT}/lib" \
  "${BUILD_CONTEXT}/conf" \
  "${BUILD_CONTEXT}/bin" \
  "${BUILD_CONTEXT}/scripts"

## ---------------------------------------------------------------------------
## Step 3 — Copy runtime jars from target/lib/ (Maven-produced)
## ---------------------------------------------------------------------------
echo "==> Copying runtime jars from target/lib/..."
cp -r "${DIST_TARGET}/lib/." "${BUILD_CONTEXT}/lib/"
echo "    Jars: $(ls "${BUILD_CONTEXT}/lib" | wc -l)"

## ---------------------------------------------------------------------------
## Step 4 — Copy filtered bin scripts from target/bin/ (Maven-filtered)
## ---------------------------------------------------------------------------
echo "==> Copying filtered bin scripts from target/bin/..."
cp -r "${DIST_TARGET}/bin/." "${BUILD_CONTEXT}/bin/"
chmod 755 "${BUILD_CONTEXT}/bin/"*.sh
echo "    bin/: $(ls "${BUILD_CONTEXT}/bin" | tr '\n' ' ')"

## ---------------------------------------------------------------------------
## Step 5 — Copy config files from target/conf/ (Maven-copied)
## ---------------------------------------------------------------------------
echo "==> Copying config files from target/conf/..."
cp -r "${DIST_TARGET}/conf/." "${BUILD_CONTEXT}/conf/"
echo "    conf/: $(find "${BUILD_CONTEXT}/conf" -type f | wc -l) files"

## ---------------------------------------------------------------------------
## Step 6 — Copy docker scripts from source docker/scripts/ (always fresh)
## ---------------------------------------------------------------------------
echo "==> Copying docker scripts from docker/scripts/..."
cp -r "${DOCKER_SOURCE}/scripts/." "${BUILD_CONTEXT}/scripts/"
echo "    scripts/: $(ls "${BUILD_CONTEXT}/scripts" | tr '\n' ' ')"

## ---------------------------------------------------------------------------
## Step 7 — Copy entrypoint from source docker/ (always fresh)
## ---------------------------------------------------------------------------
echo "==> Copying Docker entrypoint from docker/..."
cp "${DOCKER_SOURCE}/docker-entrypoint.sh" "${BUILD_CONTEXT}/docker-entrypoint.sh"
chmod 755 "${BUILD_CONTEXT}/docker-entrypoint.sh"

## ---------------------------------------------------------------------------
## Step 8 — Build Docker image using Dockerfile from source docker/
## ---------------------------------------------------------------------------
REVISION="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
CREATED="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

echo ""
echo "==> Building Docker image: ${FULL_IMAGE}"
echo "    Dockerfile: ${DOCKER_SOURCE}/Dockerfile"
echo "    Revision:   ${REVISION}"
echo "    Created:    ${CREATED}"
echo "    Context:    ${BUILD_CONTEXT}"
echo ""

docker build ${NO_CACHE} \
  -f "${DOCKER_SOURCE}/Dockerfile" \
  --build-arg VERSION="${VERSION}" \
  --build-arg BASE_IMAGE="${BASE_IMAGE}" \
  --build-arg CREATED="${CREATED}" \
  --build-arg REVISION="${REVISION}" \
  -t "${FULL_IMAGE}" \
  "${BUILD_CONTEXT}"

echo ""
echo "==> Image built successfully: ${FULL_IMAGE}"

## ---------------------------------------------------------------------------
## Step 9 — Push (optional)
## ---------------------------------------------------------------------------
if [[ "${PUSH}" == "true" ]]; then
  echo "==> Pushing: ${FULL_IMAGE}"
  docker push "${FULL_IMAGE}"
  echo "==> Pushed: ${FULL_IMAGE}"
else
  echo "    (pass --push to push to registry)"
fi

echo ""
echo "================================================================"
echo " Done."
echo ""
echo " Quick test with in-memory backend:"
echo "   docker run --rm -p 8182:8182 \\"
echo "     -e ITINERIS_PROPS_TEMPLATE=inmemory-server \\"
echo "     ${FULL_IMAGE}"
echo ""
echo " CQL backend (point at your k8ssandra cluster):"
echo "   docker run --rm -p 8182:8182 \\"
echo "     -e ITINERIS_PROPS_TEMPLATE=cql-server \\"
echo "     -e itineris.storage.hostname=<cassandra-host> \\"
echo "     -e itineris.storage.cql.keyspace=itineris \\"
echo "     ${FULL_IMAGE}"
echo "================================================================"
