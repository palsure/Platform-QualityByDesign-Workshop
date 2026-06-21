# Build context is the repository root (see docker-compose.yml) so we can pull
# in the shared monitoring config from ops/. All COPY paths therefore include
# the `backend-api/` prefix.

# ── 1. Build the Spring Boot fat jar ─────────────────────────────────────────
FROM gradle:8.7-jdk21 AS build

WORKDIR /app

COPY backend-api/build.gradle backend-api/settings.gradle backend-api/gradle.properties ./
COPY backend-api/gradle ./gradle
COPY backend-api/src ./src

# Tests run in CI pipeline — skip here to keep image build fast
RUN gradle bootJar -x test --no-daemon

# ── 2. Download the New Relic Java agent ────────────────────────────────────
# Pinned to a known-good version. Bump when the workshop refreshes the agent.
FROM eclipse-temurin:21-jre-jammy AS newrelic-agent

ARG NEWRELIC_AGENT_VERSION=8.12.0
ENV NEWRELIC_AGENT_URL=https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NEWRELIC_AGENT_VERSION}/newrelic-agent-${NEWRELIC_AGENT_VERSION}.jar

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/newrelic \
 && curl -fsSL "$NEWRELIC_AGENT_URL" -o /opt/newrelic/newrelic.jar

# ── 3. Final runtime image ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Fixed name from bootJar task — avoids ambiguous COPY when multiple jars exist
COPY --from=build /app/build/libs/application.jar app.jar

# New Relic Java agent + workshop-shared yaml. The agent is only attached at
# runtime when NEWRELIC_ENABLED=true (see entrypoint.sh below); otherwise it
# is just dead weight on disk and adds zero startup overhead.
COPY --from=newrelic-agent /opt/newrelic/newrelic.jar /opt/newrelic/newrelic.jar
COPY ops/monitoring/newrelic/newrelic.yml             /opt/newrelic/newrelic.yml

# Conditional agent attachment — keeps the image usable without a license key
# (e.g. local docker-compose, CI). NEWRELIC_LICENSE_KEY is interpolated by the
# agent itself via the ${NEWRELIC_LICENSE_KEY} placeholder in newrelic.yml.
COPY backend-api/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
