#!/usr/bin/env bash
# Upload an APK/AAB to Firebase App Distribution.
# Pass at most one of --groups or --testers; if neither is given the
# release is created without notifying anyone.
#
# For --groups, missing group aliases are created automatically before upload
# (GitHub Variables do not create Firebase groups — only the alias string).
set -euo pipefail

usage() {
  echo "Usage: $0 <apk-path> <app-id> <release-notes> [--groups g1,g2] [--testers e1,e2]" >&2
  exit 2
}

[[ $# -ge 3 ]] || usage

APK_PATH="$1"
APP_ID="$2"
RELEASE_NOTES="$3"
shift 3

if [[ ! -f "$APK_PATH" ]]; then
  echo "::error::APK not found: $APK_PATH" >&2
  exit 1
fi

if [[ -z "${FIREBASE_TOKEN:-}" ]]; then
  echo "::error::FIREBASE_TOKEN is not set" >&2
  exit 1
fi

PROJECT_ARGS=()
if [[ -n "${FIREBASE_PROJECT_ID:-}" ]]; then
  PROJECT_ARGS=(--project "$FIREBASE_PROJECT_ID")
fi

DIST_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --groups)
      [[ $# -ge 2 ]] || usage
      DIST_ARGS+=(--groups "$2")
      GROUPS_CSV="$2"
      shift 2
      ;;
    --testers)
      [[ $# -ge 2 ]] || usage
      DIST_ARGS+=(--testers "$2")
      shift 2
      ;;
    *)
      echo "::error::Unknown argument: $1" >&2
      usage
      ;;
  esac
done

ensure_groups_exist() {
  local groups_csv="$1"
  local alias display
  IFS=',' read -ra ALIASES <<< "$groups_csv"
  for alias in "${ALIASES[@]}"; do
    alias="$(echo "$alias" | tr -d '[:space:]')"
    [[ -n "$alias" ]] || continue
    # Title-case words from alias (internal-testers → Internal Testers).
    display="$(echo "$alias" | tr '-' ' ' | awk '{for (i = 1; i <= NF; i++) $i = toupper(substr($i, 1, 1)) tolower(substr($i, 2))} 1')"
    echo "Ensuring Firebase group exists: alias=$alias display=\"$display\""
    set +e
    CREATE_OUT=$(firebase appdistribution:group:create "$display" "$alias" \
      --token "$FIREBASE_TOKEN" \
      "${PROJECT_ARGS[@]}" 2>&1)
    CREATE_EXIT=$?
    set -e
    echo "$CREATE_OUT"
    if [[ $CREATE_EXIT -ne 0 ]] && ! echo "$CREATE_OUT" | grep -qiE 'already exists|ALREADY_EXISTS'; then
      echo "::warning::Could not create group '$alias' (may already exist): $CREATE_OUT"
    fi
  done
}

if [[ ${#DIST_ARGS[@]} -eq 0 ]]; then
  echo "No --groups or --testers supplied; uploading release without notifying testers."
elif [[ -n "${GROUPS_CSV:-}" ]]; then
  ensure_groups_exist "$GROUPS_CSV"
fi

set +e
OUTPUT=$(firebase appdistribution:distribute "$APK_PATH" \
  --app "$APP_ID" \
  --token "$FIREBASE_TOKEN" \
  --release-notes "$RELEASE_NOTES" \
  "${PROJECT_ARGS[@]}" \
  "${DIST_ARGS[@]}" 2>&1)
EXIT=$?
set -e

echo "$OUTPUT"

if [[ $EXIT -eq 0 ]]; then
  exit 0
fi

# Upload often completes before group/tester distribution fails (404 alias).
if echo "$OUTPUT" | grep -qiE 'uploaded new release|uploaded .* successfully'; then
  if echo "$OUTPUT" | grep -qi 'failed to distribute to testers/groups'; then
    echo "::warning::Release uploaded to Firebase App Distribution but tester notification failed."
    echo "::warning::Add testers to the group in Firebase Console → App Distribution → Testers & Groups,"
    echo "::warning::or set FIREBASE_INTERNAL_TESTERS with comma-separated emails. See docs/FIREBASE-SETUP.md"
    exit 0
  fi
fi

echo "::error::Firebase App Distribution failed (exit $EXIT)"
exit "$EXIT"
