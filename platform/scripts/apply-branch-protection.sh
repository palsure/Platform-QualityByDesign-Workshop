#!/usr/bin/env bash
# Apply branch protection so main cannot merge until "All quality gates passed".
# Requires: gh CLI authenticated with admin on the repository.
set -euo pipefail

REPO="${1:-}"
BRANCH="${2:-main}"

if [[ -z "$REPO" ]]; then
  REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)"
fi
if [[ -z "$REPO" ]]; then
  echo "Usage: $0 [owner/repo] [branch]" >&2
  exit 1
fi

RULESET_NAME="main-merge-protection"
CHECK_CONTEXT="All quality gates passed"

payload="$(cat <<EOF
{
  "name": "${RULESET_NAME}",
  "target": "branch",
  "enforcement": "active",
  "conditions": {
    "ref_name": {
      "include": ["refs/heads/${BRANCH}"],
      "exclude": []
    }
  },
  "rules": [
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [
          { "context": "${CHECK_CONTEXT}" }
        ]
      }
    }
  ]
}
EOF
)"

existing_id="$(gh api "repos/${REPO}/rulesets" --jq ".[] | select(.name==\"${RULESET_NAME}\") | .id" 2>/dev/null | head -1 || true)"

if [[ -n "$existing_id" ]]; then
  echo "Updating ruleset ${RULESET_NAME} (id=${existing_id}) on ${REPO}@${BRANCH}..."
  gh api -X PUT "repos/${REPO}/rulesets/${existing_id}" --input - <<<"$payload" >/dev/null
else
  echo "Creating ruleset ${RULESET_NAME} on ${REPO}@${BRANCH}..."
  gh api -X POST "repos/${REPO}/rulesets" --input - <<<"$payload" >/dev/null
fi

echo "Done. Required check: ${CHECK_CONTEXT}"
echo "Merge to ${BRANCH} is blocked until module pipelines pass and PR Merge Gate succeeds."
