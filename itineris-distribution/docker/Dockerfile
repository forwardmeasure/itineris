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
# Itineris Server — Docker Image
#
# Build args:
#   VERSION       — Itineris version       (default: 1.2.0)
#   BASE_IMAGE    — JRE base image         (default: eclipse-temurin:17-jre)
#   YQ_VERSION    — yq version             (default: 4.40.5)
#   TARGETARCH    — target architecture    (default: amd64)
#   CREATED       — build timestamp
#   REVISION      — git revision
#
# Runtime environment variables:
#   ITINERIS_HOME             — install root        (default: /opt/itineris)
#   ITINERIS_CONFIG_DIR       — config dir          (default: /opt/itineris/conf)
#   ITINERIS_DATA_DIR         — data dir            (default: /opt/itineris/data)
#   ITINERIS_LOG_DIR          — log dir             (default: /opt/itineris/logs)
#   ITINERIS_PROPS_TEMPLATE   — properties template (default: cql-server)
#   JAVA_OPTIONS              — extra JVM flags
#   GREMLIN_REMOTE_HOSTS      — override remote.yaml hosts
# ---------------------------------------------------------------------------

ARG BASE_IMAGE=eclipse-temurin:17-jre
ARG VERSION=1.2.0

FROM ${BASE_IMAGE}

ARG VERSION=1.2.0
ARG YQ_VERSION=4.40.5
ARG TARGETARCH=amd64
ARG CREATED=unknown
ARG REVISION=unknown

LABEL org.opencontainers.image.title="Itineris Graph Database" \
      org.opencontainers.image.description="Itineris: distributed graph database (Itineris fork)" \
      org.opencontainers.image.url="https://github.com/forwardmeasure/itineris" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.created="${CREATED}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.license="Apache-2.0" \
      org.opencontainers.image.vendor="ForwardMeasure"

ENV ITINERIS_VERSION=${VERSION} \
    ITINERIS_HOME=/opt/itineris \
    ITINERIS_LIB=/opt/itineris/lib \
    ITINERIS_CONFIG_DIR=/opt/itineris/conf \
    ITINERIS_DATA_DIR=/opt/itineris/data \
    ITINERIS_LOG_DIR=/opt/itineris/logs \
    ITINERIS_PROPS_TEMPLATE=cql-server \
    ITINERIS_SERVER_TIMEOUT=30 \
    ITINERIS_STORAGE_TIMEOUT=60 \
    JAVA_OPTIONS="" \
    gremlinserver.threadPoolWorker=1 \
    gremlinserver.gremlinPool=8

# Create forwardmeasure group/user and install minimal runtime deps
RUN groupadd -r forwardmeasure --gid=10013 && \
    useradd -r -g forwardmeasure --uid=10013 -d /opt/itineris forwardmeasure && \
    apt-get update -y && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      curl \
      netcat-openbsd \
      krb5-user && \
    rm -rf /var/lib/apt/lists/*

# Install yq for YAML config manipulation in entrypoint
RUN curl -fsSL \
    "https://github.com/mikefarah/yq/releases/download/v${YQ_VERSION}/yq_linux_${TARGETARCH}" \
    -o /usr/bin/yq && \
    chmod 755 /usr/bin/yq

# Copy Itineris runtime artifacts
COPY --chown=forwardmeasure:forwardmeasure lib/     /opt/itineris/lib/
COPY --chown=forwardmeasure:forwardmeasure conf/    /opt/itineris/conf/
COPY --chown=forwardmeasure:forwardmeasure bin/     /opt/itineris/bin/
COPY --chown=forwardmeasure:forwardmeasure scripts/ /opt/itineris/scripts/

# Copy entrypoint
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod 755 /usr/local/bin/docker-entrypoint.sh && \
    chmod 755 /opt/itineris/bin/*.sh && \
    mkdir -p \
      /opt/itineris/ext \
      /opt/itineris/data \
      /opt/itineris/logs && \
    chown -R forwardmeasure:forwardmeasure /opt/itineris

EXPOSE 8182

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -sf http://localhost:8182/?gremlin=1%2B1 | grep -q "result" || exit 1

WORKDIR /opt/itineris
USER forwardmeasure

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]