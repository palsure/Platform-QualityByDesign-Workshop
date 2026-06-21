#!/usr/bin/env bash
# Conditionally attach the New Relic Java agent.
#
# Enabled when NEWRELIC_ENABLED=true *and* NEWRELIC_LICENSE_KEY is non-empty.
# This keeps `docker compose up` working out of the box for workshop attendees
# who haven't signed up for New Relic yet, while making it a one-line opt-in
# (`NEWRELIC_ENABLED=true NEWRELIC_LICENSE_KEY=... docker compose up`) when
# they want APM data.
set -euo pipefail

JAVA_OPTS="${JAVA_OPTS:-}"

if [[ "${NEWRELIC_ENABLED:-false}" == "true" && -n "${NEWRELIC_LICENSE_KEY:-}" ]]; then
  echo "[entrypoint] New Relic APM enabled — attaching agent"
  export NEW_RELIC_LICENSE_KEY="${NEWRELIC_LICENSE_KEY}"
  export NEW_RELIC_APP_NAME="${NEWRELIC_APP_NAME:-QoE API}"
  export NEW_RELIC_LOG_FILE_NAME="STDOUT"
  JAVA_OPTS="${JAVA_OPTS} -javaagent:/opt/newrelic/newrelic.jar -Dnewrelic.config.file=/opt/newrelic/newrelic.yml"
else
  echo "[entrypoint] New Relic APM disabled (set NEWRELIC_ENABLED=true and NEWRELIC_LICENSE_KEY to enable)"
fi

exec java ${JAVA_OPTS} -jar /app/app.jar "$@"
