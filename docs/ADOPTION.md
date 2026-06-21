# Quality by Design — Adoption Guide

Sample apps and the full pipeline live in this repo. Teams call the platform-managed gate chain from their own repositories.

## Where everything lives

| Workshop piece | Location |
|----------------|----------|
| Sample apps | [`backend-api`](../backend-api), [`web-player`](../web-player), [`android-player`](../android-player), [`ios-player`](../ios-player) |
| Full QBD pipeline | [`.github/workflows/quality-by-design.yaml`](../.github/workflows/quality-by-design.yaml) |
| Reusable templates | [`.github/workflows/reusable-*.yaml`](../.github/workflows/) |
| DevSecOps + policy | [`platform/`](../platform/) |
| Local stack | [`docker-compose.yml`](../docker-compose.yml) |
| Per-platform shipping pipelines | [`.github/workflows/streaming-app-*.yml`](../.github/workflows/) |

## Gate chain

```
unit → contract → integration (BAT) → ephemeral env → smoke → perf smoke → promote
              ↕
        DevSecOps defaults (parallel)
```

| Stage | Implementation |
|-------|----------------|
| Unit | `./gradlew unitTest` (backend-api) + `npm test` (web-player) |
| Contract | `./gradlew contractTest` |
| Integration | `./gradlew batTest` |
| DevSecOps | Gitleaks · npm audit · Syft · Trivy · Conftest |
| Ephemeral env | `docker compose up` (full stack) |
| Smoke | [`platform/tests/smoke/smoke-test.sh`](../platform/tests/smoke/smoke-test.sh) |
| Perf smoke | k6 [`platform/tests/load/load-test.js`](../platform/tests/load/load-test.js) + [`quality-gate.sh`](../platform/scripts/quality-gate.sh) |
| Promotion | `quality-by-design.yaml` orchestrator |
| PR feedback | [`platform/scripts/pr-summary.py`](../platform/scripts/pr-summary.py) |

## Quick start

```bash
docker compose up -d

# Backend tests
cd backend-api && ./gradlew unitTest contractTest batTest

# DevSecOps (local)
chmod +x platform/scripts/devsecops-gates.sh
./platform/scripts/devsecops-gates.sh
```

## Adoption

Teams call the platform pipeline from their repo:

```yaml
jobs:
  quality:
    uses: your-org/platform/.github/workflows/quality-by-design.yaml@main
```

Set **branch protection** required checks to the QBD orchestrator jobs (`DevSecOps Defaults`, `Backend Test Gates`, `Web Player Unit Tests`, `Ephemeral Env Validation`, `Promotion Gate`).

Mobile apps (Android/iOS) continue to use the existing `streaming-app-android.yml` and `streaming-app-ios.yml` pipelines for shipping and device E2E.

## Optional: Slack & Firebase

Per-platform shipping pipelines support threaded Slack updates and Firebase deploys. Both are optional — missing secrets skip those steps without failing the rest of the pipeline.

| Guide | Covers |
|---|---|
| [SLACK-SETUP.md](SLACK-SETUP.md) | Bot app, `SLACK_BOT_TOKEN`, `SLACK_CHANNEL_ID` |
| [FIREBASE-SETUP.md](FIREBASE-SETUP.md) | Hosting (web), App Distribution (Android/iOS), CI tokens |
