#!/usr/bin/env bash
# Upload an APK/AAB to Firebase App Distribution.
# Pass at most one of --groups or --testers; if neither is given the
# release is created without notifying anyone.
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

DIST_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --groups)
      [[ $# -ge 2 ]] || usage
      DIST_ARGS+=(--groups "$2")
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

if [[ ${#DIST_ARGS[@]} -eq 0 ]]; then
  echo "No --groups or --testers supplied; uploading release without notifying testers."
fi

firebase appdistribution:distribute "$APK_PATH" \
  --app "$APP_ID" \
  --token "$FIREBASE_TOKEN" \
  --release-notes "$RELEASE_NOTES" \
  "${DIST_ARGS[@]}"
