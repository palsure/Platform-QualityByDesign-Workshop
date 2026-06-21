# Quality by Design: Embedding DevSecOps and Test Automation into CI/CD

> **PlatformCon 2026** · Theme 2: Platform Engineering in Practice

A hands-on workshop repo: reusable GitHub Actions workflow templates, security-by-default checks, ephemeral test environments, and enforceable quality gates — demonstrated on real **QoE streaming sample apps** (web, iOS, Android, API).

| | |
|---|---|
| **Workshop guide** | [`docs/WORKSHOP-GUIDE.md`](docs/WORKSHOP-GUIDE.md) |
| **Gate chain** | `unit → contract → integration → ephemeral env → smoke → perf smoke → promote` (+ DevSecOps in parallel) |
| **Orchestrator** | [`.github/workflows/quality-by-design.yaml`](.github/workflows/quality-by-design.yaml) |
| **CFP submission** | [`docs/SUBMISSION.md`](docs/SUBMISSION.md) |

Teams rarely lack tests or security — they lack **consistency**. This repo shows how to turn DevSecOps and test automation into CI/CD defaults instead of one-off pipelines per team.

## Quick start

```bash
docker compose up -d
cd backend-api && ./gradlew unitTest contractTest batTest
cd ../web-player && npm test
```

| Service | URL |
|---|---|
| Web Player | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

See [`QUICKSTART.md`](QUICKSTART.md) and [`docs/WORKSHOP-GUIDE.md`](docs/WORKSHOP-GUIDE.md) for the full hands-on script.

---

## Sample apps — QoE streaming demo

Cross-platform Quality of Experience (QoE) validation for streaming video: shared backend, metrics schema, multi-stage tests, and per-platform CI/CD pipelines with Slack notifications and Allure reports.

## Architecture

The four player platforms share a single backend, a single metrics schema, and a single set of CI/CD primitives. Every player collects the same payload shape and posts it to the API every 5 seconds; the API stores it, validates it against thresholds, and exposes a pipeline acceptance gate.

```mermaid
flowchart LR
  subgraph Players["📱 Player clients"]
    direction TB
    Web["Web Player<br/>React + HLS.js"]
    iOS["iOS Player<br/>Swift + AVPlayer"]
    Android["Android Player<br/>Kotlin + ExoPlayer"]
  end

  subgraph Backend["🛰  Backend (Java / Spring Boot)"]
    direction TB
    API["REST API<br/>POST /api/v1/metrics<br/>POST /api/v1/validations<br/>POST /api/v1/pipeline-runs"]
    Validation["Validation engine<br/>thresholds &amp; quality score"]
    Gate["Acceptance gate"]
    DB[("PostgreSQL 15<br/>Flyway migrations")]
    API --> Validation --> Gate
    API --> DB
    Validation --> DB
  end

  subgraph Schema["📐 Shared contract"]
    SchemaJSON["ops/shared/schema/<br/>qoe-metrics.schema.json<br/>qoe-metrics.types.ts"]
  end

  subgraph Obs["📊 Observability"]
    NR["New Relic<br/>RUM + APM"]
  end

  Web -- "QoE payload<br/>every 5s" --> API
  iOS -- "QoE payload<br/>every 5s" --> API
  Android -- "QoE payload<br/>every 5s" --> API

  Web -- "browser RUM" --> NR
  API -- "APM" --> NR

  SchemaJSON -. "validates payloads" .-> API
  SchemaJSON -. "shapes types" .-> Web
  SchemaJSON -. "shapes types" .-> iOS
  SchemaJSON -. "shapes types" .-> Android
```

## CI/CD pipeline shape

Each module owns its own GitHub Actions workflow. Pipelines all follow the same gated shape — *test → build → ship a canary → re-validate → promote* — with threaded Slack notifications and Allure reports published to GitHub Pages along the way.

```mermaid
flowchart LR
  Push((Push / PR))
  subgraph Pipeline["Module pipeline (per-platform)"]
    direction LR
    Notify[notify-start]
    Lint[lint]
    Unit["unit-tests<br/>(gate ≥80%)"]
    Build["build<br/>(jar / apk / dist)"]
    Internal[publish-internal]
    BAT["BAT e2e<br/>(soft-gated)"]
    Public[publish-public]
    Smoke[smoke e2e]
    Reg[regression<br/>nightly]
    Report[report &amp; Slack summary]
    Notify --> Lint
    Notify --> Unit
    Lint --> Build
    Unit --> Build
    Build --> Internal
    Internal --> BAT
    BAT -- "gate=true" --> Public
    Public --> Smoke
    Smoke --> Report
    Reg -.-> Report
  end
  Push --> Notify
  Smoke -.-> Slack[Slack thread]
  Internal -.-> Slack
  BAT -.-> Slack
  Report -.-> Slack
  Smoke -.-> Pages[GitHub Pages<br/>Allure reports]
  Build -.-> Firebase[Firebase App Distribution]
  Internal -.-> Firebase
  Public -.-> Firebase
```

The Web and API pipelines use Firebase Hosting (preview channel → live promotion); the Android and iOS pipelines use Firebase App Distribution (internal canary → public promotion). On a hard BAT failure the public promotion is blocked but the internal release stays live so the team can investigate on the same artifact testers are running.

**Mobile E2E paths.** Both Android and iOS pipelines support two ways to run BAT/Smoke instrumented tests:

| Mode | Selected when | Where it runs |
|---|---|---|
| **LambdaTest cloud device** | `vars.LT_USERNAME` is set on the repo | Real Pixel/iPhone hardware on LambdaTest |
| **Self-hosted emulator/simulator** | `vars.LT_USERNAME` is unset | KVM-accelerated x86_64 AVD on `ubuntu-latest` (Android) / Xcode simulator on `macos-latest` (iOS) |

**Workshop escape hatches.** Mobile device labs are flaky; these flags keep the rest of the pipeline shipping when the lab is down:

| Variable | Effect |
|---|---|
| `vars.SKIP_BAT=true` *or* `[skip-bat]` in commit/PR title | Skip BAT entirely for one run; Firebase publishes on the Unit gate alone |
| `vars.NO_DEVICE_LAB=true` | Persistent override — BAT reports `SKIPPED` (not `FAILED`) so Slack stays green |
| `inputs.skip_tests=true` | Hotfix mode — bypass every gate, ship straight to public |

## Modules

| Module | Description | README |
|---|---|---|
| [`backend-api/`](backend-api/README.md) | Java 21 / Spring Boot 3 REST API + PostgreSQL | Setup, endpoints, test commands |
| [`web-player/`](web-player/README.md) | React + TypeScript + HLS.js player | Setup, E2E tests, Allure reports |
| [`ios-player/`](ios-player/README.md) | Swift Package library + SwiftUI demo app | Library usage, Xcode build, Firebase deploy |
| [`android-player/`](android-player/README.md) | Kotlin / ExoPlayer Android app | Android Studio setup, APK build |
| [`qoe-automation-tests/`](qoe-automation-tests/README.md) | Java / TestNG cross-platform automation | API, web, mobile, validation tests |
| [`ops/`](ops/README.md) | Infrastructure, monitoring, shared schema | nginx, FFmpeg, New Relic, JSON schema |

## Project structure

```
├── backend-api/                 # Spring Boot REST API + Flyway + Testcontainers
├── web-player/                  # React/Vite SPA + Playwright E2E
├── ios-player/                  # SwiftPM library (QoePlayer) + SwiftUI demo app
├── android-player/              # Gradle (Kotlin DSL) Android app + Espresso
├── qoe-automation-tests/        # Maven/TestNG cross-platform suite
├── ops/
│   ├── infrastructure/          # nginx config, FFmpeg HLS transcoder, tc network sim
│   ├── monitoring/              # New Relic dashboards, alerts, NRQL
│   └── shared/schema/           # Canonical qoe-metrics.schema.json + TS types
├── platform/                    # QBD scripts, policy, smoke/k6 tests
├── docs/                        # PlatformCon workshop docs
├── presentations/               # DevOpsDays slide materials (reference)
├── test-videos/                 # Sample HLS streams (gitignored placeholder)
├── docker-compose.yml           # Full local stack (api + web + db + nginx)
├── QUICKSTART.md                # 5-minute smoke test
├── TESTING.md                   # Per-module test playbook
└── .github/
    ├── workflows/               # QBD + per-module pipelines
    ├── scripts/                 # Slack payload builders + Allure helpers
    └── actions/                 # Reusable composite actions
```

## Quick start

### Prerequisites

| Tool | Min version | Used by |
|---|---|---|
| Docker Desktop | 24+ | full stack |
| Java JDK | **21+** | `backend-api` and `qoe-automation-tests` |
| Node.js | 18+ | `web-player` |
| Maven | 3.9+ | `qoe-automation-tests` |
| Allure CLI | 2.27+ | viewing test reports — `brew install allure` |
| Xcode | 15+ | `ios-player` (macOS only) |
| Android Studio | Hedgehog or later | `android-player` |

### One-line bring-up

```bash
docker compose up -d && curl http://localhost:8080/actuator/health
```

| Service | URL |
|---|---|
| Backend API | http://localhost:8080 |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui/index.html |
| Web Player | http://localhost:3000 |
| nginx (video CDN) | http://localhost:8081 |
| PostgreSQL | localhost:5432 |

For the 5-minute "is everything working?" walkthrough see [`QUICKSTART.md`](QUICKSTART.md). For the full per-module test playbook (unit / BAT / Smoke / Regression and Allure local serving) see [`TESTING.md`](TESTING.md).

## CI/CD workflows

All workflows live in [`.github/workflows/`](.github/workflows/).

| Workflow | Trigger | Module |
|---|---|---|
| `stream-qoe-app-api.yml` | push / PR on `backend-api/**` | Backend API |
| `stream-qoe-app-web.yml` | push / PR on `web-player/**` | Web Player |
| `stream-qoe-app-android.yml` | push / PR on `android-player/**` | Android Player |
| `stream-qoe-app-ios.yml` | push / PR on `ios-player/**` | iOS Player |
| `stream-qoe-app-validation.yml` | pull request | Lightweight matrix across modules |
| `stream-qoe-app-pr-e2e.yml` | pull request | Web + API (Docker stack + Playwright gate) |
| `stream-qoe-app-newrelic.yml` | push / PR on monitoring config | New Relic dashboards / alerts |
| `stream-qoe-app-release.yml` | manual | All modules — acceptance + release |
| `shared-notify-build-started.yml` | `workflow_call` | Reusable "build started" Slack notify |
| **`quality-by-design.yaml`** | push / PR on `backend-api/**`, `web-player/**`, `platform/**` | **PlatformCon QBD workshop** — full gate chain + DevSecOps |
| `reusable-devsecops.yaml` | `workflow_call` | Gitleaks · npm audit · SBOM · Trivy · Conftest |
| `reusable-test-gates.yaml` | `workflow_call` | unit → contract → integration (BAT) |
| `reusable-ephemeral-validation.yaml` | `workflow_call` | Docker Compose stack + smoke + k6 perf smoke |

### Quality by Design workshop (PlatformCon 2026)

The [`platform/`](platform/) folder and [`quality-by-design.yaml`](.github/workflows/quality-by-design.yaml) workflow add the **platform golden path** on top of these sample apps:

```
unit → contract → integration (BAT) → ephemeral env → smoke → perf smoke → promote
              ↕
        DevSecOps defaults (parallel)
```

- **Contract tests:** `./gradlew contractTest` in `backend-api` (`ApiContractIT.java`)
- **DevSecOps:** [`platform/scripts/`](platform/scripts/) + [`reusable-devsecops.yaml`](.github/workflows/reusable-devsecops.yaml)
- **Ephemeral env:** full `docker compose` stack per PR validation
- **Workshop docs:** [`docs/WORKSHOP-GUIDE.md`](docs/WORKSHOP-GUIDE.md) · [`docs/QUALITY-BY-DESIGN.md`](docs/QUALITY-BY-DESIGN.md) · [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · [`docs/ADOPTION.md`](docs/ADOPTION.md)

Reusable composite actions in [`.github/actions/`](.github/actions/README.md):

| Action | Purpose |
|---|---|
| `slack-stage-notify` | Per-stage Slack message (PASSED / FAILED / SKIPPED + duration + report link) |
| `slack-gate-notify` | "Gate PASSED — proceeding" / "Gate FAILED — blocking" gate decision |
| `slack-pipeline-report` | Final per-platform summary with combined pass rate and total duration |
| `publish-allure` | Generate Allure report from JUnit XML and deploy to GitHub Pages with retry/jitter |
| `evaluate-jest-gate` | Enforceable web unit test gate (QBD orchestrator) |
| `lambdatest-espresso` | Upload APKs + dispatch + poll a LambdaTest Espresso run, normalise JUnit XML output |

Pipeline hardening:

- **Maven Central mirror** baked into Android Gradle setup — falls through to Google's CDN when MC throttles GitHub Actions runners.
- **Gradle dep cache** is written from feature branches so the build job primes cache for downstream BAT/Smoke jobs.
- **3-attempt retry** with 30 s back-off on each on-emulator `connectedDebugAndroidTest` invocation.
- **Allure publish** retries 6 times with exponential back-off + random jitter to survive concurrent GitHub Pages deploys from parallel jobs.

## License

Educational use — PlatformCon 2026 Quality by Design workshop and DevOpsDays Raleigh 2026 QoE demo.
