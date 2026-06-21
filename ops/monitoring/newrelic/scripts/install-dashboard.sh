#!/usr/bin/env bash
# Install (or replace) the QoE dashboard in New Relic via NerdGraph.
#
# Usage:
#   NEW_RELIC_USER_API_KEY=NRAK-... NEW_RELIC_ACCOUNT_ID=7996933 \
#     ops/monitoring/newrelic/scripts/install-dashboard.sh
#
# Notes:
#   • Requires a USER API key (the NRAK-... type), NOT the ingest license key.
#     Get one at https://one.newrelic.com/api-keys → Create key → "User".
#   • The dashboard JSON is loaded from the standard repo location.
#   • For EU accounts, set NR_REGION=eu.
set -euo pipefail

: "${NEW_RELIC_USER_API_KEY:?must set NEW_RELIC_USER_API_KEY (NRAK-...)}"
: "${NEW_RELIC_ACCOUNT_ID:?must set NEW_RELIC_ACCOUNT_ID (e.g. 7996933)}"

REGION="${NR_REGION:-us}"
case "$REGION" in
  us) ENDPOINT="https://api.newrelic.com/graphql" ;;
  eu) ENDPOINT="https://api.eu.newrelic.com/graphql" ;;
  *)  echo "NR_REGION must be 'us' or 'eu' (got: $REGION)" >&2; exit 1 ;;
esac

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
DASHBOARD_JSON="$REPO_ROOT/ops/monitoring/newrelic/dashboards/qoe-dashboard.json"

if ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: jq is required (brew install jq)" >&2
  exit 1
fi

# Inject the caller's account ID into every widget. Lets the same JSON file be
# imported into any account without manual find-and-replace.
echo "→ Loading $DASHBOARD_JSON, retargeting account → $NEW_RELIC_ACCOUNT_ID"
DASHBOARD=$(jq --argjson acct "$NEW_RELIC_ACCOUNT_ID" '
  (.. | objects | select(has("accountId")) .accountId) |= $acct
' "$DASHBOARD_JSON")

# Build the GraphQL mutation. We embed the dashboard JSON as a *string* via jq,
# wrap that in the GraphQL payload, and POST it.
MUTATION='mutation($acct: Int!, $dashboard: DashboardInput!) {
  dashboardCreate(accountId: $acct, dashboard: $dashboard) {
    entityResult { guid name }
    errors { description type }
  }
}'

PAYLOAD=$(jq -n \
  --argjson acct "$NEW_RELIC_ACCOUNT_ID" \
  --argjson dashboard "$DASHBOARD" \
  --arg query "$MUTATION" \
  '{query: $query, variables: {acct: $acct, dashboard: $dashboard}}')

echo "→ Posting to $ENDPOINT"
RESPONSE=$(curl -sS -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -H "API-Key: $NEW_RELIC_USER_API_KEY" \
  -d "$PAYLOAD")

ERRORS=$(echo "$RESPONSE" | jq '.errors // .data.dashboardCreate.errors // []')
if [ "$(echo "$ERRORS" | jq 'length')" -gt 0 ]; then
  echo "✗ NerdGraph returned errors:"
  echo "$ERRORS" | jq .
  exit 1
fi

GUID=$(echo "$RESPONSE" | jq -r '.data.dashboardCreate.entityResult.guid')
NAME=$(echo "$RESPONSE" | jq -r '.data.dashboardCreate.entityResult.name')

echo "✓ Created dashboard '$NAME'"
echo "  GUID: $GUID"
echo "  Open: https://one.newrelic.com/redirect/entity/$GUID"
