#!/bin/bash
# Copyright 2024 Itineris Authors
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

[[ -n "$DEBUG" ]] && set -x

ITINERIS_HOME="${ITINERIS_HOME:-/opt/itineris}"

ITINERIS_CONF="${ITINERIS_CONF:-${ITINERIS_HOME}/conf}"
ITINERIS_LIB="${ITINERIS_LIB:-${ITINERIS_HOME}/lib}"
LOG_DIR="${ITINERIS_LOG_DIR:-${ITINERIS_HOME}/logs}"
LOG4J_CONF="file:${ITINERIS_CONF}/log4j2.xml"
JAVA="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
ITINERIS_SERVER_CMD=com.forwardmeasure.itineris.graphdb.server.ItinerisServer

# Read JVM options
COLLECTED_JAVA_OPTIONS_FILE=""
JAVA_OPTIONS_FILE="${JAVA_OPTIONS_FILE:-${ITINERIS_CONF}/jvm-17.options}"
if [[ -f "$JAVA_OPTIONS_FILE" ]]; then
  for opt in "$(grep '^-' $JAVA_OPTIONS_FILE)"; do
    opt=$(echo "$opt" | xargs)
    COLLECTED_JAVA_OPTIONS_FILE="$COLLECTED_JAVA_OPTIONS_FILE $opt"
  done
fi

JAMM_JAR="$(find -L ${ITINERIS_LIB} -name 'jamm-*.jar' | head -1)"
JAVA_OPTIONS="$COLLECTED_JAVA_OPTIONS_FILE $JAVA_OPTIONS -javaagent:${JAMM_JAR}"

# Build classpath
CP="${ITINERIS_CONF}"
CP="$CP":$(find -L ${ITINERIS_LIB} -name 'log4j-slf4j2-impl*.jar' | sort | tr '\n' ':')
CP="$CP":$(find -L ${ITINERIS_LIB} -name 'itineris*.jar' | sort | tr '\n' ':')
CP="$CP":$(find -L ${ITINERIS_LIB} -name '*.jar' \
                \! -name 'itineris*' \
                \! -name 'log4j-slf4j2-impl*.jar' | sort | tr '\n' ':')
CP="$CP":$(find -L "${ITINERIS_HOME}/ext" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | \
      sort | sed 's/$/\/plugin\/*/' | tr '\n' ':')

CLASSPATH="${CLASSPATH:-}:$CP"

# Resolve yaml config
ITINERIS_YAML="${1:-${ITINERIS_CONF}/gremlin-server/itineris-server.yaml}"
if [[ ! -r "$ITINERIS_YAML" ]]; then
  echo "ERROR: $ITINERIS_YAML is unreadable"
  exit 1
fi

echo "$ITINERIS_YAML will be used to start Itineris Server in foreground"
cd "$ITINERIS_HOME"
exec $JAVA -Dlog4j2.configurationFile=$LOG4J_CONF \
           $JAVA_OPTIONS \
           -cp $CLASSPATH \
           $ITINERIS_SERVER_CMD "$ITINERIS_YAML"