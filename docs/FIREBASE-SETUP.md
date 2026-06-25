# Firebase Deploy Setup

The repo uses Firebase in two ways:

| Module | Firebase product | Pipeline |
|---|---|---|
| **Web player** | [Firebase Hosting](https://firebase.google.com/docs/hosting) — preview channel → live promotion | `streaming-app-web.yml` |
| **Android player** | [App Distribution](https://firebase.google.com/docs/app-distribution) — internal canary → public promotion | `streaming-app-android.yml` |
| **iOS player** | App Distribution | `streaming-app-ios.yml` |

The **backend API** does not deploy to Firebase (Docker / your own infra). Local manual scripts exist under `android-player/deploy-firebase.sh` and `ios-player/deploy-firebase.sh`.

## Prerequisites

```bash
npm install -g firebase-tools
firebase login          # interactive — for local setup
firebase login:ci       # prints CI token — for GitHub Actions
```

Save the CI token output — that is `FIREBASE_TOKEN`.

---

## 1. Create a Firebase project

1. [Firebase Console](https://console.firebase.google.com/) → **Add project**.
2. Note the **Project ID** (e.g. `streamapp-demo`). This is `FIREBASE_PROJECT_ID`.

Register each app you need:

| App | Console step | Config file in repo |
|---|---|---|
| Web | **Add app → Web** (`</>`) | `web-player/firebase.json` |
| Android | **Add app → Android** | `android-player/firebase.json` |
| iOS | **Add app → iOS** (same bundle ID as Xcode) | — |

Copy each **App ID** from **Project settings → Your apps** (format `1:123456789:web:abc…`, `…:android:…`, `…:ios:…`).

---

## 2. Web player — Firebase Hosting

### Local one-time setup

```bash
cd web-player
npm install
npm run build          # produces dist/

# Link project (creates .firebaserc — not committed; add locally or in CI via env)
firebase use --add     # pick your FIREBASE_PROJECT_ID

# First preview channel deploy (creates a stable preview URL)
firebase hosting:channel:deploy staging \
  --expires 30d \
  --project YOUR_PROJECT_ID
```

Copy the **Hosting URL** printed for the `staging` channel (e.g. `https://YOUR_PROJECT_ID--staging-RANDOM.web.app`).

> The CI pipeline reuses a **fixed channel name** (`staging` by default) so the preview URL stays the same across deploys. Set `FIREBASE_PREVIEW_URL` once; do not re-extract from CLI output each run.

### GitHub configuration (web)

**Secrets** (Settings → Secrets and variables → Actions → Secrets):

| Secret | Value |
|---|---|
| `FIREBASE_TOKEN` | Output of `firebase login:ci` |
| `FIREBASE_PROJECT_ID` | Project ID (optional here if set as variable) |
| `FIREBASE_PREVIEW_URL` | Preview channel URL from first deploy (optional if set as variable) |

**Variables** (recommended for non-secret values):

| Variable | Value | Notes |
|---|---|---|
| `FIREBASE_PROJECT_ID` | `platformworkshop-6c399` | Project ID only |
| `FIREBASE_PREVIEW_URL` | `https://platformworkshop-6c399--staging-yjktmfq1.web.app` | **Full** channel URL from deploy log — not the project ID |
| `FIREBASE_CHANNEL_ID` | `staging` | Channel name (default: `staging`) |

### Pipeline flow

```
Unit tests (≥80%) → Deploy preview channel → BAT (Playwright vs preview URL)
                 → BAT gate passes → Clone preview → live (https://PROJECT_ID.web.app)
                 → Smoke (async, vs live URL)
```

If `FIREBASE_TOKEN` or `FIREBASE_PREVIEW_URL` is missing, the deploy job fails with setup instructions in the log.

### `firebase.json` (already in repo)

Hosting serves `dist/` with SPA rewrites. No changes needed unless you change the build output path.

---

## 3. Android — App Distribution

### Firebase Console

1. Register the Android app with package name **`com.platform.android`** (must match `applicationId` in `android-player/app/build.gradle.kts`).
2. **App Distribution → Testers & Groups** → create groups (use the **alias**, not the display name):
   - `internal-testers` — canary builds (set `FIREBASE_INTERNAL_GROUPS=internal-testers`, or use `FIREBASE_INTERNAL_TESTERS` emails instead)
   - `external-testers` or custom — public promotion (optional)

   The pipeline **auto-creates** any missing group alias before upload. You still need to **add tester emails** to that group in the Firebase Console (or set `FIREBASE_INTERNAL_TESTERS` so the CLI invites them by email).

3. Add tester emails to each group, **or** set `FIREBASE_INTERNAL_TESTERS` / `FIREBASE_PUBLIC_TESTERS` with comma-separated emails in GitHub Variables.

If neither emails nor groups are configured, the pipeline still uploads the APK to App Distribution but does not notify anyone.

**Troubleshooting `failed to distribute to testers/groups` (HTTP 404):** `FIREBASE_INTERNAL_GROUPS` in GitHub only tells the pipeline which alias to use — the group must exist in Firebase (the pipeline creates it if missing). **Add at least one tester email** to the group in Firebase Console, or set `FIREBASE_INTERNAL_TESTERS=you@email.com` in GitHub Variables.

Update `android-player/firebase.json` with your App ID if deploying locally:

```json
{
  "appdistribution": {
    "appId": "1:YOUR_PROJECT:android:YOUR_APP_ID",
    "groups": ["testers"]
  }
}
```

### GitHub configuration (Android)

**Secrets:**

| Secret | Value |
|---|---|
| `FIREBASE_TOKEN` | CI token (`firebase login:ci`) — **must be a secret**, not a variable |

**Variables or secrets** (App IDs and project metadata):

| Name | Example | Notes |
|---|---|---|
| `FIREBASE_APP_ID_ANDROID` | `1:976224…:android:…` | Variable **or** secret (Android pipeline reads both) |
| `FIREBASE_APP_ID_IOS` | `1:976224…:ios:…` | Variable **or** secret (iOS pipeline reads both) |
| `FIREBASE_PROJECT_ID` | `platformworkshop-…` | Console link in Slack |

**Variables or secrets** (distribution targets — use **either** emails **or** groups, not both):

| Name | Example | Used for |
|---|---|---|
| `FIREBASE_INTERNAL_TESTERS` | `alice@co.com,bob@co.com` | Internal canary |
| `FIREBASE_INTERNAL_GROUPS` | `internal-testers` | Internal canary (if no email list) |
| `FIREBASE_PUBLIC_TESTERS` | `qa@co.com` | Public promotion |
| `FIREBASE_PUBLIC_GROUPS` | `external-testers` | Public promotion |

### Pipeline flow

```
Unit gate → Build APK → Publish internal (Firebase)
         → BAT (Firebase Test Lab virtual device → emulator fallback)
         → Public promotion (if BAT passes)
         → Smoke (Firebase Test Lab → emulator fallback)
```

Firebase publish is skipped when `FIREBASE_TOKEN` or `FIREBASE_APP_ID_ANDROID` is empty.

### Firebase Test Lab (BAT / Smoke device lab)

CI runs Espresso on **Firebase Test Lab virtual devices** first (free-tier friendly), then **falls back to the GitHub-hosted emulator** if Test Lab is unavailable (quota, auth, or no results).

**Setup (one time in GCP):**

1. Enable billing on the Firebase project (Spark free quotas still apply; Blaze adds minute-based free time).
2. [Google Cloud Console](https://console.cloud.google.com/) → **IAM** → create a service account, e.g. `github-test-lab`.
3. Grant roles:
   - **Firebase Test Lab Admin** (or *Cloud Testing Service Account*)
   - **Storage Admin** on the results bucket (or let CI create `gs://PROJECT-test-lab-results`)
4. Create a JSON key → add as GitHub secret **`GCP_SA_KEY`**.

**GitHub configuration:**

| Name | Where | Notes |
|---|---|---|
| `GCP_SA_KEY` | Secret | Service account JSON |
| `FIREBASE_PROJECT_ID` | Variable | Already used for App Distribution links |
| `FTL_DEVICE_ANDROID` | Variable (optional) | Default `model=MediumPhone.arm,version=33,locale=en,orientation=portrait` |
| `FTL_RESULTS_BUCKET` | Variable (optional) | Default `gs://PROJECT-test-lab-results` |

**Free tier (Spark):** 10 virtual + 5 physical test runs per day. The pipeline uses **virtual devices only** by default to stay within the free quota.

**Without `GCP_SA_KEY`:** BAT/Smoke use the local KVM emulator (same as before).

**Disable Smoke tests:** set repository variable `RUN_SMOKE_TESTS_ANDROID=false` (Android) or `RUN_SMOKE_TESTS_IOS=false` (iOS). Smoke jobs are skipped and later stages (report, Firebase publish on iOS) continue.

View runs: [Firebase Console → Test Lab](https://console.firebase.google.com/project/_/testlab/histories).

### Manual deploy

```bash
cd android-player
FIREBASE_APP_ID=1:xxx:android:yyy ./deploy-firebase.sh
```

---

## 4. iOS — App Distribution

### Prerequisites

- Apple Developer Program membership
- Xcode signing configured (Team ID, bundle ID `com.devopsdays.qoe.iosplayer`)
- iOS app registered in Firebase with matching bundle ID

### GitHub configuration (iOS)

**Secrets:**

| Secret | Value |
|---|---|
| `FIREBASE_TOKEN` | CI token |
| `FIREBASE_APP_ID_IOS` | iOS App ID from Firebase Console |
| `FIREBASE_TESTERS` | Comma-separated emails or group name for public promotion (legacy; default group `external-testers`) |

Apple signing secrets for CI (if not using manual signing in workflow) are configured separately in `streaming-app-ios.yml` — see that workflow's header comments for `APPLE_*` secrets when enabling device builds.

### Manual deploy

```bash
cd ios-player
FIREBASE_APP_ID=1:xxx:ios:yyy APPLE_TEAM_ID=ABCDE12345 ./deploy-firebase.sh
```

---

## 5. Shared secrets summary

These secrets are reused across pipelines:

| Secret | Used by |
|---|---|
| `FIREBASE_TOKEN` | Web, Android, iOS |
| `FIREBASE_PROJECT_ID` | Web (also as variable), Android, iOS |
| `FIREBASE_APP_ID_ANDROID` | Android only |
| `FIREBASE_APP_ID_IOS` | iOS only |
| `FIREBASE_PREVIEW_URL` | Web only (variable preferred) |

Slack setup (separate): [SLACK-SETUP.md](SLACK-SETUP.md)

---

## 6. Verify web deploy in CI

1. Configure secrets/variables above.
2. Push a change under `web-player/**` or run **Actions → Streaming App — Web → Run workflow**.
3. Expected jobs:
   - **Deploy Firebase Preview** — succeeds, logs preview URL
   - **BAT Tests** — Playwright against `FIREBASE_PREVIEW_URL`
   - **Promote to Live** — clones staging channel to live
4. Open `https://YOUR_PROJECT_ID.web.app` after promotion.

## 7. Verify mobile deploy in CI

1. Configure `FIREBASE_TOKEN` + `FIREBASE_APP_ID_ANDROID` (or iOS).
2. Push under `android-player/**` or `ios-player/**`.
3. Check **Firebase Console → App Distribution** for the new build.
4. Slack thread should include a link to the distribution console when `FIREBASE_PROJECT_ID` is set.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `FIREBASE_TOKEN … missing` | Run `firebase login:ci`; add token as secret |
| `FIREBASE_PREVIEW_URL is not set` | Deploy channel once locally; save URL as repo variable |
| BAT fails with wrong URL | `FIREBASE_PREVIEW_URL` must start with `https://` (full channel URL, not project ID) |
| Slack Preview link broken (`<platformworkshop…\|Preview URL>`) | Same fix — set full `https://…--staging-….web.app` URL; pipeline now auto-extracts from deploy log |
| App Distribution 404 on group | Create the group in Firebase Console; or use email list via `FIREBASE_INTERNAL_TESTERS` |
| Preview deploy OK, promote fails | Ensure preview job output `channel` matches deployed channel name |
| iOS upload fails | Check signing, Team ID, and that IPA path exists in workflow logs |

## Related

- Web pipeline: [`.github/workflows/streaming-app-web.yml`](../.github/workflows/streaming-app-web.yml)
- Android pipeline: [`.github/workflows/streaming-app-android.yml`](../.github/workflows/streaming-app-android.yml)
- iOS pipeline: [`.github/workflows/streaming-app-ios.yml`](../.github/workflows/streaming-app-ios.yml)
- Slack notifications: [SLACK-SETUP.md](SLACK-SETUP.md)
