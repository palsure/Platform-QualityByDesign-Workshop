#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────
# deploy-firebase.sh — Build Android APK and push to Firebase App Distribution
#
# Prerequisites:
#   1. Run `firebase login` once to authenticate
#   2. Set FIREBASE_APP_ID to your Android app ID from the Firebase Console
#      e.g. export FIREBASE_APP_ID="1:123456789012:android:abc123def456"
#      (Firebase Console → Project Settings → Your apps → App ID)
#
# Usage:
#   ./deploy-firebase.sh
#   FIREBASE_APP_ID=1:xxx:android:yyy ./deploy-firebase.sh
# ─────────────────────────────────────────────────────────────────
set -euo pipefail

APP_ID="${FIREBASE_APP_ID:-1:710273242702:android:23bc7b2f44d278c4ff151f}"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
RELEASE_NOTES="Debug build – $(date '+%Y-%m-%d %H:%M')"

if [[ -z "$APP_ID" ]]; then
  echo "❌  FIREBASE_APP_ID is not set."
  exit 1
fi

echo "📦  Building debug APK…"
ANDROID_HOME=~/Library/Android/sdk gradle assembleDebug

echo "🚀  Uploading to Firebase App Distribution…"
firebase appdistribution:distribute "$APK_PATH" \
  --app "$APP_ID" \
  --release-notes "$RELEASE_NOTES"

echo "✅  Done! Check Firebase Console for the new release."
