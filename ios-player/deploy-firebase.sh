#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────
# deploy-firebase.sh — Build signed iOS IPA and push to Firebase App Distribution
#
# Prerequisites (one-time setup):
#   1. Enroll in the Apple Developer Program (https://developer.apple.com)
#   2. In Xcode → Preferences → Accounts, add your Apple ID
#   3. Set your Team ID and Bundle ID below (or export them as env vars)
#   4. Create a Firebase project and add an iOS app with the same Bundle ID
#   5. Run `firebase login` once to authenticate
#
# Usage:
#   FIREBASE_APP_ID=1:xxx:ios:yyy APPLE_TEAM_ID=ABCDE12345 ./deploy-firebase.sh
# ─────────────────────────────────────────────────────────────────
set -euo pipefail

FIREBASE_APP_ID="${FIREBASE_APP_ID:-1:710273242702:ios:9c29403f15431267ff151f}"
APPLE_TEAM_ID="${APPLE_TEAM_ID:-}"
BUNDLE_ID="com.devopsdays.qoe.iosplayer"
SCHEME="QoePlayerApp"
PROJECT="QoePlayerApp.xcodeproj"
ARCHIVE_PATH="/tmp/QoePlayerApp.xcarchive"
IPA_DIR="/tmp/QoePlayerApp_ipa"
IPA_PATH="$IPA_DIR/QoePlayerApp.ipa"
RELEASE_NOTES="Build $(date '+%Y-%m-%d %H:%M') – iOS QoE Player"

# ── Validate inputs ───────────────────────────────────────────────
if [[ -z "$FIREBASE_APP_ID" ]]; then
  echo "❌  FIREBASE_APP_ID is not set."
  echo "    Get it from Firebase Console → Project Settings → Your apps → App ID"
  echo "    e.g. FIREBASE_APP_ID=1:123456789:ios:abcdef ./deploy-firebase.sh"
  exit 1
fi

if [[ -z "$APPLE_TEAM_ID" ]]; then
  echo "❌  APPLE_TEAM_ID is not set."
  echo "    Find yours at https://developer.apple.com/account → Membership → Team ID"
  echo "    e.g. APPLE_TEAM_ID=ABCDE12345 ./deploy-firebase.sh"
  exit 1
fi

# ── Create ExportOptions.plist for ad-hoc distribution ───────────
EXPORT_PLIST="/tmp/QoePlayerApp_ExportOptions.plist"
cat > "$EXPORT_PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key>
    <string>ad-hoc</string>
    <key>teamID</key>
    <string>${APPLE_TEAM_ID}</string>
    <key>compileBitcode</key>
    <false/>
    <key>thinning</key>
    <string>&lt;none&gt;</string>
</dict>
</plist>
PLIST

# ── Build & Archive ───────────────────────────────────────────────
echo "📦  Archiving $SCHEME…"
xcodebuild archive \
  -project "$PROJECT" \
  -scheme "$SCHEME" \
  -sdk iphoneos \
  -configuration Release \
  -archivePath "$ARCHIVE_PATH" \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  PRODUCT_BUNDLE_IDENTIFIER="$BUNDLE_ID" \
  CODE_SIGN_STYLE=Automatic \
  | xcpretty 2>/dev/null || true

# ── Export IPA ────────────────────────────────────────────────────
echo "📤  Exporting IPA…"
rm -rf "$IPA_DIR"
xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$IPA_DIR" \
  -exportOptionsPlist "$EXPORT_PLIST" \
  | xcpretty 2>/dev/null || true

if [[ ! -f "$IPA_PATH" ]]; then
  # xcpretty may rename it; find it
  IPA_PATH=$(find "$IPA_DIR" -name "*.ipa" | head -1)
fi

if [[ -z "$IPA_PATH" || ! -f "$IPA_PATH" ]]; then
  echo "❌  IPA not found. Check signing configuration in Xcode."
  exit 1
fi

# ── Upload to Firebase App Distribution ──────────────────────────
echo "🚀  Uploading to Firebase App Distribution…"
firebase appdistribution:distribute "$IPA_PATH" \
  --app "$FIREBASE_APP_ID" \
  --release-notes "$RELEASE_NOTES" \
  --groups "testers"

echo "✅  Done! Check Firebase Console for the new iOS release."
