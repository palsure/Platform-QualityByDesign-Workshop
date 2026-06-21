#!/usr/bin/env bash
# cleanup-stale-workflows.sh
#
# After renaming workflow files (e.g. qoe-android-tests.yml ->
# stream-qoe-app-android.yml), the OLD workflow entry stays in
# GitHub's Actions sidebar for as long as it still has run history.
# That's why the UI shows two of every workflow after a rename.
#
# This script removes the lingering entries by deleting all workflow
# runs that belonged to the OLD file paths. Once a workflow has zero
# runs AND no file in the default branch, GitHub drops it from the
# sidebar (usually within a few minutes).
#
# Safety rails:
#   - Dry-run by default. Pass --apply to actually delete.
#   - Skips any path that still exists in the default branch.
#   - Prints a summary before exiting.
#
# Requirements:
#   - gh CLI (>= 2.0) authenticated with `repo` scope:
#       brew install gh && gh auth login
#   - jq
#
# Usage:
#   .github/scripts/cleanup-stale-workflows.sh           # dry-run
#   .github/scripts/cleanup-stale-workflows.sh --apply   # really delete

set -euo pipefail

REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
APPLY=0
[[ "${1:-}" == "--apply" ]] && APPLY=1

# Old workflow file paths whose runs should be purged. Add new paths
# here whenever you rename or delete a workflow.
OLD_PATHS=(
  ".github/workflows/qoe-android-tests.yml"
  ".github/workflows/qoe-api-tests.yml"
  ".github/workflows/qoe-ios-tests.yml"
  ".github/workflows/qoe-pr-e2e.yml"
  ".github/workflows/qoe-validation.yml"
  ".github/workflows/qoe-web-tests.yml"
  ".github/workflows/qoe-newrelic.yml"
  ".github/workflows/build-acceptance-release.yml"
  ".github/workflows/shared-notify-start.yml"
)

echo "Repository : $REPO"
echo "Mode       : $([[ $APPLY -eq 1 ]] && echo APPLY || echo DRY-RUN)"
echo "----------------------------------------------------------------"

# Pull the full workflow inventory once (includes deleted/disabled).
WORKFLOWS_JSON="$(gh api -X GET "repos/$REPO/actions/workflows" --paginate)"

total_runs_deleted=0
total_workflows_cleaned=0

for path in "${OLD_PATHS[@]}"; do
  # Defence-in-depth: refuse to delete runs for a path that's still
  # tracked in the repo (would nuke the new workflow's history too if
  # someone re-added the old name).
  if git ls-files --error-unmatch "$path" >/dev/null 2>&1; then
    echo "SKIP  $path (still present in repo)"
    continue
  fi

  workflow_id="$(jq -r --arg p "$path" '.workflows[] | select(.path == $p) | .id' <<<"$WORKFLOWS_JSON")"

  if [[ -z "$workflow_id" ]]; then
    echo "MISS  $path (no GitHub record — already cleaned)"
    continue
  fi

  # Count runs for this workflow.
  run_count="$(gh api -X GET "repos/$REPO/actions/workflows/$workflow_id/runs" --paginate \
    | jq '[.workflow_runs[].id] | length')"

  if [[ "$run_count" -eq 0 ]]; then
    echo "EMPTY $path (id=$workflow_id, 0 runs — sidebar should clear shortly)"
    continue
  fi

  echo "FOUND $path (id=$workflow_id, runs=$run_count)"

  if [[ $APPLY -eq 0 ]]; then
    continue
  fi

  # Page through and delete every run.
  while :; do
    ids="$(gh api -X GET "repos/$REPO/actions/workflows/$workflow_id/runs?per_page=100" \
      | jq -r '.workflow_runs[].id')"
    [[ -z "$ids" ]] && break
    while IFS= read -r run_id; do
      [[ -z "$run_id" ]] && continue
      gh api -X DELETE "repos/$REPO/actions/runs/$run_id" >/dev/null \
        && total_runs_deleted=$((total_runs_deleted + 1)) \
        || echo "      ! failed to delete run $run_id"
    done <<<"$ids"
  done

  total_workflows_cleaned=$((total_workflows_cleaned + 1))
  echo "      cleaned"
done

echo "----------------------------------------------------------------"
if [[ $APPLY -eq 1 ]]; then
  echo "Done. Workflows cleaned: $total_workflows_cleaned. Runs deleted: $total_runs_deleted."
  echo "Wait 1-2 minutes and refresh the Actions page — the duplicates should be gone."
else
  echo "Dry run complete. Re-run with --apply to delete the runs above."
fi
