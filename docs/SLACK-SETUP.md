# Slack Notifications Setup

CI/CD pipelines post threaded Slack messages for build start, each stage result, gate decisions, and a final summary. Slack is **optional** — if secrets are not configured, pipelines still run; notification steps are skipped.

## What gets posted

| Event | Where | Workflow |
|---|---|---|
| Build started | New channel message (thread root) | `shared-notify-build-started.yml` |
| Stage result (PASSED / FAILED / SKIPPED) | Reply in thread | `slack-stage-notify` action |
| Gate decision (proceed / block) | Reply in thread | `slack-gate-notify` action |
| Final module summary | Reply + header update | `slack-pipeline-report` action |

Pipelines that use Slack:

- `quality-by-design.yaml`
- `streaming-app-api.yml`
- `streaming-app-web.yml`
- `streaming-app-android.yml`
- `streaming-app-ios.yml`
- `streaming-app-release.yml`
- `streaming-app-newrelic.yml`

PR comments (in addition to Slack on pull requests):

- **Quality by Design** (`quality-by-design.yaml`) — updates a PR comment with gate results

## 1. Create a Slack app

1. Open [https://api.slack.com/apps](https://api.slack.com/apps) → **Create New App** → **From scratch**.
2. Name it (e.g. `StreamApp CI`) and pick your workspace.

### Bot token scopes

Under **OAuth & Permissions → Scopes → Bot Token Scopes**, add:

| Scope | Why |
|---|---|
| `chat:write` | Post messages as the bot |
| `chat:write.public` | Post to public channels the bot hasn't joined yet (optional but convenient) |
| `channels:read` | Validate channel ID before posting (used by Slack Config Check) |
| `channels:join` | Auto-join public channels before the first post |

### Install the app

1. **OAuth & Permissions** → **Install to Workspace** → Allow.
2. Copy the **Bot User OAuth Token** (`xoxb-…`). This is `SLACK_BOT_TOKEN`.

> **Which workspace?** The pipeline log line `Slack bot '…' in workspace '…'` comes from Slack’s `auth.test` API — it shows whichever workspace the **bot token** was issued for. There is no workspace name setting in this repo. To use a different workspace, create/reinstall the Slack app in that workspace, copy the new `xoxb-…` token, and update **`SLACK_BOT_TOKEN`** in GitHub Secrets. The channel ID (`SLACK_CHANNEL_ID`) must belong to that same workspace.

## 2. Create a channel and invite the bot

1. Create a channel (e.g. `#streamapp-ci`).
2. Invite the bot: `/invite @StreamApp CI` (or your app name).
3. Get the **channel ID**:
   - Open the channel in Slack (browser or desktop).
   - Click the channel name → scroll to the bottom of **About**.
   - Copy the ID (starts with `C`, e.g. `C012AB3CD`).

   Or right-click the channel → **Copy link** — the ID is the last path segment.

## 3. Add GitHub configuration

In your repo: **Settings → Secrets and variables → Actions**

### Secret (required)

| Name | Value |
|---|---|
| `SLACK_BOT_TOKEN` | Bot token (`xoxb-…`) |

Add under **Secrets → New repository secret**.

### Channel ID — secret **or** variable

The channel ID is not sensitive. Prefer a **repository variable**; a secret also works.

| Name | Where | Value |
|---|---|---|
| `SLACK_CHANNEL_ID` | **Variables** (recommended) or **Secrets** | Channel ID (`C…`) — not `#channel-name` |

Pipelines resolve: `vars.SLACK_CHANNEL_ID` first, then `secrets.SLACK_CHANNEL_ID`.

> **Important:** If you previously saved a wrong value under **Secrets → `SLACK_CHANNEL_ID`**, delete that secret or update it to match your variable. An old secret (e.g. `#channel-name`) can override the correct variable and cause `channel_not_found`.

```text
Settings → Secrets and variables → Actions → Variables → New repository variable
  Name:  SLACK_CHANNEL_ID
  Value: C012AB3CD…
```

### Verify configuration (secrets already set?)

If both secrets are present but you still see `channel_not_found`, the **channel ID value** is wrong for that bot token — not the secret names.

1. **Actions → Slack Config Check → Run workflow** (uses [`.github/workflows/slack-config-check.yml`](../.github/workflows/slack-config-check.yml)).
2. Or locally:

```bash
export SLACK_BOT_TOKEN=xoxb-…
export SLACK_CHANNEL_ID=C…   # paste from Slack → channel → About
python3 .github/scripts/slack_validate_config.py
```

The check confirms the bot workspace matches the channel and prints `#channel-name` when the ID is valid.

**Re-copy the channel ID:** open the channel in Slack → click the channel name → **About** → scroll down → copy **Channel ID** (`C…`). Update the secret even if you set it before — typos and `#name` instead of `C…` are the usual cause.

## 4. Verify

1. Push a change under `backend-api/**` or `web-player/**` (or run a workflow manually via **Actions → Run workflow**).
2. Check `#streamapp-ci`:
   - A header message: `[API] Streaming App | Build #N`
   - Thread replies for Unit Tests, BAT, deploy stages, etc.
   - The header is updated with ✅ or ❌ when the pipeline finishes.

## Message shape

```
[WEB] Streaming App  |  Build #42
Branch: feature/foo  |  Environment: STAGE  |  Commit: abc1234  |  Triggered by: you
────────────────────────────────────────
⏳ Build → Unit → Unit Gate → Deploy Preview → BAT → Promote → Smoke (async)

  ↳ Unit Tests          ✅ PASSED   (12s)   📊 View Report
  ↳ Unit Gate           ✅ PASSED — deploying to Firebase Preview…
  ↳ Firebase Preview    ✅ PASSED   (8s)    🌐 Preview URL
  ↳ BAT Tests           ✅ PASSED   (2m 1s) 📊 View Report
  ↳ Firebase Live       ✅ PASSED   (5s)
  ↳ [WEB] Streaming App | Build #42 — ✅ success
```

## Optional: fork / PR from forks

Slack secrets are not exposed to workflows from fork PRs. Notification steps check for empty tokens and skip cleanly — the pipeline does not fail because Slack is missing.

## Troubleshooting

### `channel_not_found`

Slack returned `channel_not_found` — the bot token is valid but Slack does not recognize the channel value.

1. **Use the channel ID, not the name.** The secret must be `C012AB3CD`, not `#streamapp-ci`.
2. **Copy the ID correctly:** Slack → open channel → channel name → **About** → scroll to bottom → copy ID.
3. **Private channels:** run `/invite @YourBotName` in the channel before the pipeline runs.
4. **No extra characters:** re-paste the ID in GitHub Secrets with no spaces, quotes, or `#` prefix.
5. **Same workspace:** the bot token and channel must belong to the same Slack workspace.

After fixing `SLACK_CHANNEL_ID`, re-run the workflow. The notify step now **fails loudly** when Slack returns an error instead of silently continuing.

| Symptom | Fix |
|---|---|
| No messages at all | Set `SLACK_BOT_TOKEN` (secret) and `SLACK_CHANNEL_ID` (variable or secret) |
| `channel_not_found` | Fix channel ID (see above); check it is in **Variables or Secrets**, not only the wrong tab |
| `not_in_channel` | Invite the bot to the channel (`/invite @…`) |
| `invalid_auth` | Regenerate bot token; reinstall app to workspace |
| Messages but no thread replies | Initial post failed — check notify-start job logs |

## Related

- Composite actions: [`.github/actions/README.md`](../.github/actions/README.md)
- Payload scripts: [`.github/scripts/`](../.github/scripts/)
- Firebase deploy setup: [FIREBASE-SETUP.md](FIREBASE-SETUP.md)
- Allure report links (404 fix): [GITHUB-PAGES-SETUP.md](GITHUB-PAGES-SETUP.md)
