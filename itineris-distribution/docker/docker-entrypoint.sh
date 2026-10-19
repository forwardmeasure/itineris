#!/bin/bash
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

# ---------------------------------------------------------------------------
# Itineris Server — Docker entrypoint
# ---------------------------------------------------------------------------
set -e

# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------
log_info()  { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [INFO]  $*"; }
log_warn()  { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [WARN]  $*"; }
log_error() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $*" >&2; }

ITINERIS_PROPS="${ITINERIS_CONFIG_DIR}/itineris.properties"
ITINERIS_SERVER_YAML="${ITINERIS_CONFIG_DIR}/itineris-server.yaml"

log_info "Itineris version: ${ITINERIS_VERSION}"
log_info "ITINERIS_HOME:    ${ITINERIS_HOME}"
log_info "Config dir:       ${ITINERIS_CONFIG_DIR}"
log_info "Data dir:         ${ITINERIS_DATA_DIR}"
log_info "Log dir:          ${ITINERIS_LOG_DIR}"
log_info "Props template:   ${ITINERIS_PROPS_TEMPLATE}"

# ---------------------------------------------------------------------------
# Copy selected properties template to the runtime properties file.
# The server yaml (shipped in the image) already references this fixed path:
#   graphs: {graph: conf/itineris.properties}
# so no runtime patching of the yaml graphs key is needed.
# ---------------------------------------------------------------------------
TEMPLATE_FILE="${ITINERIS_HOME}/conf/itineris-${ITINERIS_PROPS_TEMPLATE}.properties"
if [[ ! -f "${TEMPLATE_FILE}" ]]; then
  log_error "Properties template not found: ${TEMPLATE_FILE}"
  log_error "Available templates:"
  ls "${ITINERIS_HOME}/conf/"itineris-*.properties 2>/dev/null || log_error "  none found"
  exit 1
fi
log_info "Using properties template: ${TEMPLATE_FILE}"
cp "${TEMPLATE_FILE}" "${ITINERIS_PROPS}"

# Copy gremlin server yaml — graphs.graph is already set to conf/itineris.properties
# in the shipped yaml, so no patching required.
cp "${ITINERIS_HOME}/conf/gremlin-server/itineris-server.yaml" "${ITINERIS_SERVER_YAML}"
log_info "Config files copied to ${ITINERIS_CONFIG_DIR}"

# ---------------------------------------------------------------------------
# Apply environment variable overrides
#
# itineris.<key>=<value>  → written into itineris.properties
# gremlinserver.<key>=<value> → written into itineris-server.yaml
# gremlinserver%d.<key>       → key deleted from itineris-server.yaml
# ---------------------------------------------------------------------------
log_info "Applying environment variable overrides..."
while IFS=' ' read -r envvar_key envvar_val; do
  if [[ "${envvar_key}" =~ ^itineris\. ]] && [[ -n "${envvar_val}" ]]; then
    prop_key="${envvar_key#itineris.}"
    if grep -q -E "^\s*${prop_key}\s*=" "${ITINERIS_PROPS}"; then
      log_info "  Overriding property: ${prop_key}=${envvar_val}"
      sed -ri "s#^(\s*${prop_key}\s*=).*#\1${envvar_val}#" "${ITINERIS_PROPS}"
    else
      log_info "  Adding property: ${prop_key}=${envvar_val}"
      echo "${prop_key}=${envvar_val}" >> "${ITINERIS_PROPS}"
    fi
  elif [[ "${envvar_key}" =~ ^gremlinserver(%d)?\.(.+) ]]; then
    edit_mode="w"
    [[ "${BASH_REMATCH[1]}" == "%d" ]] && edit_mode="d"
    yaml_key="${BASH_REMATCH[2]}"
    if [[ "${edit_mode}" == "d" ]]; then
      log_info "  Deleting yaml key: ${yaml_key}"
      yq e "del(.${yaml_key})" -i "${ITINERIS_SERVER_YAML}"
    else
      log_info "  Setting yaml key: ${yaml_key}=${envvar_val}"
      # Set numeric values without quotes, strings with quotes
      if [[ "${envvar_val}" =~ ^[0-9]+$ ]]; then
        yq e ".${yaml_key} = ${envvar_val}" -i "${ITINERIS_SERVER_YAML}"
      else
        yq e ".${yaml_key} = \"${envvar_val}\"" -i "${ITINERIS_SERVER_YAML}"
      fi
    fi
  fi
done < <(env | sort -r | awk -F= '{ st = index($0,"="); print $1 " " substr($0,st+1) }')

# ---------------------------------------------------------------------------
# Show config (debug mode)
# ---------------------------------------------------------------------------
if [ "${1:-}" == 'show-config' ]; then
  echo "# ${ITINERIS_PROPS}"
  cat "${ITINERIS_PROPS}"
  echo "---"
  echo "# ${ITINERIS_SERVER_YAML}"
  cat "${ITINERIS_SERVER_YAML}"
  exit 0
fi

# ---------------------------------------------------------------------------
# Wait for storage backend to be reachable
# ---------------------------------------------------------------------------
if [[ -n "${ITINERIS_STORAGE_TIMEOUT:-}" ]]; then
  STORAGE_HOST=$(grep -E "^\s*storage\.hostname\s*=" "${ITINERIS_PROPS}" | \
    head -1 | sed 's/.*=\s*//' | tr ',' '\n' | head -1 | tr -d ' ')
  STORAGE_PORT=$(grep -E "^\s*storage\.port\s*=" "${ITINERIS_PROPS}" | \
    head -1 | sed 's/.*=\s*//' | tr -d ' ')
  STORAGE_PORT="${STORAGE_PORT:-9042}"

  if [[ -n "${STORAGE_HOST}" ]]; then
    log_info "Waiting up to ${ITINERIS_STORAGE_TIMEOUT}s for storage at ${STORAGE_HOST}:${STORAGE_PORT}..."
    START_TS=$(date +%s)
    until nc -z "${STORAGE_HOST}" "${STORAGE_PORT}" 2>/dev/null; do
      NOW=$(date +%s)
      if [[ $((NOW - START_TS)) -ge "${ITINERIS_STORAGE_TIMEOUT}" ]]; then
        log_error "Timed out waiting for storage at ${STORAGE_HOST}:${STORAGE_PORT}"
        exit 1
      fi
      log_info "  waiting for ${STORAGE_HOST}:${STORAGE_PORT}..."
      sleep 5
    done
    log_info "Storage backend is reachable."
  else
    log_info "No storage hostname configured — skipping storage wait."
  fi
fi

# Override Gremlin Console remote hosts if specified
if [[ -n "${GREMLIN_REMOTE_HOSTS:-}" ]]; then
  log_info "Setting Gremlin remote hosts: ${GREMLIN_REMOTE_HOSTS}"
  sed -i "s/hosts\s*:.*/hosts: [$GREMLIN_REMOTE_HOSTS]/" \
    "${ITINERIS_HOME}/conf/remote.yaml" 2>/dev/null || true
fi

log_info "Starting Itineris Server with ${ITINERIS_SERVER_YAML}..."
exec "${ITINERIS_HOME}/bin/itineris-server.sh" "${ITINERIS_SERVER_YAML}"