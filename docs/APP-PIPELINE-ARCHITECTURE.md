# Application & Pipeline Architecture

Visual reference for the **streaming app** runtime and **Quality by Design** CI/CD pipelines in this repo.

Related: [`ARCHITECTURE.md`](ARCHITECTURE.md) (gate policy & lessons) · [`WORKSHOP-GUIDE.md`](WORKSHOP-GUIDE.md) (hands-on script)

> **Module pipeline (per-platform)** — see the diagram in [README.md](../README.md#cicd-pipeline-shape) and §6–§8 below.

---

## 1. End-to-end overview

```mermaid
flowchart TB
  subgraph Clients["Player clients"]
    Web["Web Player<br/>React · Vite · HLS.js"]
    Android["Android Player<br/>Kotlin · ExoPlayer"]
    iOS["iOS Player<br/>Swift · AVPlayer"]
  end

  subgraph Runtime["Runtime stack — docker compose"]
    API["Backend API<br/>Spring Boot :8080"]
    DB[("PostgreSQL 15")]
    CDN["nginx CDN<br/>:8081"]
    API --> DB
  end

  subgraph External["External HLS origins"]
    Mux["Mux / Apple / Akamai<br/>test streams"]
  end

  subgraph CICD["GitHub Actions"]
  direction TB
    Ship["Shipping pipelines<br/>streaming-app-*.yml"]
    Reuse["Reusable templates<br/>reusable-*.yaml"]
    QBD["QBD orchestrator<br/>quality-by-design.yaml"]
    Plat["platform/<br/>smoke · k6 · policy"]
    Ship --> Reuse
    QBD --> Reuse
    Reuse --> Plat
  end

  Web -->|"catalog API smoke"| API
  Web -->|"HLS playback"| Mux
  Android --> Mux
  iOS --> Mux
  CDN -.->|"local test media"| Mux

  Developers((Developers)) --> CICD
  CICD -->|"build · test · deploy"| Runtime
  CICD -.-> Firebase["Firebase<br/>Hosting · App Distribution"]
  CICD -.-> Slack["Slack<br/>stage threads"]
  CICD -.-> Pages["GitHub Pages<br/>reports"]
```

---

## 2. Application architecture

### 2.1 Logical components

```mermaid
flowchart LR
  subgraph WebApp["web-player/"]
    Pages["BrowsePage · DetailPage"]
    VP["VideoPlayer<br/>HLS.js + video element"]
    CatWeb["catalog.ts<br/>embedded catalog"]
    Pages --> VP
    CatWeb --> Pages
  end

  subgraph APIApp["backend-api/"]
    VC["VideoController"]
    VS["VideoService"]
    VR["VideoRepository"]
    VC --> VS --> VR
  end

  subgraph Mobile["android-player/ · ios-player/"]
    MA["Catalog UI"]
    PA["Player screen"]
    CatMob["VideoCatalog<br/>bundled HLS URLs"]
    MA --> PA
    CatMob --> PA
  end

  DB[("PostgreSQL<br/>videos table")]

  VR --> DB
  WebApp -.->|"smoke / contract tests"| APIApp
```

| Component | Responsibility | Backend required? |
|-----------|----------------|-------------------|
| **backend-api** | REST catalog (`/api/v1/videos`), health, OpenAPI | — |
| **web-player** | Browse UI, HLS playback, Playwright E2E | Optional locally; validated in CI smoke |
| **android-player** | RecyclerView catalog, ExoPlayer | No — bundled `VideoCatalog.kt` |
| **ios-player** | SwiftUI catalog, AVPlayer (`StreamApp` package) | No — bundled `VideoCatalog.swift` |

### 2.2 API surface

```mermaid
flowchart TB
  Client["HTTP client"]
  Client --> Health["GET /actuator/health"]
  Client --> List["GET /api/v1/videos"]
  Client --> Search["GET /api/v1/videos/search?q="]
  Client --> One["GET /api/v1/videos/{id}"]
  Client --> Manifest["GET /api/v1/videos/{id}/manifest"]
  List --> PG[("PostgreSQL")]
  Search --> PG
  One --> PG
  Manifest --> PG
```

### 2.3 Video playback data flow

```mermaid
sequenceDiagram
  participant User
  participant Player as Web / Mobile Player
  participant API as Backend API
  participant HLS as HLS origin

  Note over Player: Catalog from embedded data<br/>or API in CI smoke
  User->>Player: Select title
  Player->>HLS: GET master.m3u8
  HLS-->>Player: Manifest + segments
  Player-->>User: Playback

  Note over API: Smoke test only
  Player->>API: GET /api/v1/videos
  API-->>Player: JSON catalog
```

---

## 3. Docker Compose runtime

Local and CI ephemeral environments use the same `docker-compose.yml`.

```mermaid
flowchart TB
  subgraph Host["Developer machine / CI runner"]
    subgraph Compose["docker compose"]
      PG["postgres:15<br/>:5432"]
      BE["backend<br/>Spring Boot :8080"]
      WEB["web-player<br/>nginx :3000"]
      NGX["nginx:alpine<br/>:8081"]
    end
  end

  User((User)) --> WEB
  User --> BE
  User --> NGX

  BE --> PG
  WEB --> BE
  NGX -.->|"serves local test media"| Vol["ops/infrastructure/test-content"]

  BE --- HC1["healthcheck<br/>/actuator/health"]
  PG --- HC2["pg_isready"]
```

| Service | Port | URL |
|---------|------|-----|
| **backend** | 8080 | http://localhost:8080 |
| **web-player** | 3000 | http://localhost:3000 |
| **postgres** | 5432 | `jdbc:postgresql://localhost:5432/qoe_db` |
| **nginx** | 8081 | http://localhost:8081/videos/ |

---

## 4. CI/CD platform layers

```mermaid
flowchart TB
  subgraph Consumer["External repos — adoption"]
    TeamA["Product repo A"]
    TeamB["Product repo B"]
  end

  subgraph Orchestrator["Callable orchestrator"]
    QBD["quality-by-design.yaml<br/>workflow_call · manual"]
  end

  subgraph Shipping["Per-module shipping — push/PR triggers"]
    APIW["streaming-app-api.yml"]
    WEBW["streaming-app-web.yml"]
    ANDW["streaming-app-android.yml"]
    IOSW["streaming-app-ios.yml"]
    REL["streaming-app-release.yml"]
    NRW["streaming-app-newrelic.yml<br/>optional"]
  end

  subgraph Reusable["Reusable workflow templates"]
    TG["reusable-test-gates.yaml"]
    DS["reusable-devsecops.yaml"]
    EV["reusable-ephemeral-validation.yaml"]
    NS["shared-notify-build-started.yml"]
  end

  subgraph Actions["Composite actions"]
    JG["evaluate-jest-gate"]
    SN["slack-stage-notify"]
    PS["publish-static-report"]
  end

  subgraph PlatformDir["platform/"]
    Smoke["tests/smoke/"]
    K6["tests/load/"]
    QG["scripts/quality-gate.sh"]
    PR["scripts/pr-summary.py"]
    Pol["policy/k8s.rego"]
  end

  TeamA --> QBD
  TeamB --> QBD
  QBD --> TG
  QBD --> DS
  QBD --> EV

  APIW --> DS
  APIW --> TG
  APIW --> EV
  WEBW --> DS
  WEBW --> EV

  TG --> Actions
  DS --> Pol
  EV --> Smoke
  EV --> K6
  EV --> QG
  APIW --> PR
  QBD --> PR
```

---

## 5. Golden path gate chain

```mermaid
flowchart LR
  Commit((Commit / PR))

  Commit --> Unit["Unit<br/>Gradle · Vitest"]
  Unit --> Contract["Contract<br/>ApiContractIT"]
  Contract --> Build["Build artifact"]

  Commit --> DevSec["DevSecOps<br/>parallel"]

  Build --> Ephemeral["Ephemeral env<br/>docker compose"]
  Ephemeral --> BAT["BAT<br/>integration"]
  BAT --> Smoke["Smoke<br/>HTTP checks"]
  Smoke --> Perf["Perf smoke<br/>k6 + quality-gate"]
  Perf --> Promote{Promote?}

  DevSec --> Promote
  Contract --> Promote
  Promote -->|yes| Live["Production / preview"]
  Promote -->|no| Block["Blocked"]

  Live --> AsyncSmoke["Post-promote smoke<br/>async"]
  Promote --> PRComment["PR summary comment"]
```

---

## 6. API pipeline (`streaming-app-api.yml`)

Triggered on `backend-api/**`, `platform/**`, `docker-compose.yml`.

```mermaid
flowchart TB
  Push((Push / PR)) --> Notify["notify-start<br/>Slack thread"]

  Notify --> DevSec["devsecops<br/>reusable-devsecops"]
  Notify --> Unit["unit-tests<br/>@Tag unit · gate ≥80%"]
  Unit --> Contract["contract-tests<br/>@Tag contract"]
  Contract --> Build["build-and-verify<br/>JAR + health check"]
  Build --> BAT["bat-tests<br/>@Tag BAT · Testcontainers"]
  BAT --> Ephemeral["ephemeral-validation<br/>compose · smoke · k6"]
  Ephemeral --> Promote["promote-live"]

  Promote --> Report["api-report<br/>Allure · Slack summary"]
  Promote --> Smoke["smoke-tests<br/>@Tag Smoke · async"]

  Report --> PR["api-pr-comment<br/>pr-summary.py"]

  DevSec -.->|must pass| Promote
  Ephemeral -.->|must pass| Promote
  BAT -.->|must pass| Promote

  style Smoke fill:#f0f4f8,stroke:#718096
```

| Job | Blocking? | Test tag / tool |
|-----|-----------|-----------------|
| devsecops | Yes | Gitleaks, Trivy, Conftest, SBOM |
| unit-tests | Yes | `@Tag("unit")` |
| contract-tests | Yes | `@Tag("contract")` |
| build-and-verify | Yes | Spring Boot health |
| bat-tests | Yes | `@Tag("BAT")` |
| ephemeral-validation | Yes | smoke-test.sh + k6 |
| promote-live | Gate | All upstream green |
| smoke-tests | No | `@Tag("Smoke")` post-promote |

---

## 7. Web pipeline (`streaming-app-web.yml`)

Triggered on `web-player/**`, `platform/**`.

```mermaid
flowchart TB
  Push((Push / PR)) --> Notify["notify-start"]

  Notify --> DevSec["devsecops"]
  Notify --> Build["build<br/>tsc + vite dist/"]
  Build --> Unit["unit-tests<br/>Vitest · evaluate-jest-gate"]
  Unit --> Preview["deploy-preview<br/>Firebase preview channel"]
  Preview --> BAT["bat-tests<br/>Playwright @BAT · 4 workers"]
  BAT --> Promote["promote-live<br/>preview → live"]
  Promote --> Report["report · Allure"]
  Promote --> Smoke["smoke-tests<br/>Playwright @Smoke · async"]

  DevSec -.->|parallel| Build
  BAT -.->|gate| Promote

  Preview -.-> Firebase["Firebase Hosting"]
  Promote -.-> Firebase

  style Smoke fill:#f0f4f8,stroke:#718096
```

Playwright BAT runs against the **real Firebase preview URL** — not localhost.

---

## 8. Mobile pipelines

### Android (`streaming-app-android.yml`)

```mermaid
flowchart LR
  Push((Push / PR)) --> Notify --> Unit["unit-tests<br/>JUnit + MockK"]
  Unit --> Build["build APK"]
  Build --> Internal["Firebase internal<br/>distribution"]
  Internal --> BAT["BAT Espresso<br/>Test Lab / emulator<br/>soft gate"]
  BAT --> Public["Firebase public"]
  Public --> Smoke["Smoke Espresso"]
  Smoke --> Report["report · Slack"]

  BAT -.->|skipped if no lab| Public
```

### iOS (`streaming-app-ios.yml`)

```mermaid
flowchart LR
  Push((Push / PR)) --> Notify --> Unit["swift test<br/>StreamAppTests"]
  Unit --> Build["xcodebuild"]
  Build --> Internal["Firebase internal"]
  Internal --> BAT["BAT XCTest<br/>simulator · soft gate"]
  BAT --> Public["Firebase public"]
  Public --> Smoke["Smoke XCTest"]
  Smoke --> Report["report · Slack"]
```

Mobile apps use **bundled catalog URLs** — pipelines focus on build, distribution, and instrumented UI tests.

---

## 9. QBD orchestrator (`quality-by-design.yaml`)

Callable via `workflow_call` or `workflow_dispatch` — **not** triggered on push/PR in this repo. Day-to-day gates live in API/Web shipping pipelines.

```mermaid
flowchart TB
  Trigger["workflow_call<br/>or manual dispatch"] --> Notify["notify-start"]

  Notify --> DevSec["devsecops<br/>all checks"]
  Notify --> TestGates["test-gates<br/>reusable-test-gates"]
  Notify --> WebUnit["web-unit<br/>npm test + jest gate"]

  DevSec --> Promote["promote<br/>gate aggregator"]
  TestGates --> Promote
  WebUnit --> Promote
  EV["ephemeral-validation"] --> Promote

  Notify --> EV

  Promote --> Report["qbd-report · Slack"]
  Report --> PR["pr-comment<br/>pr-summary.py"]
```

**Adoption pattern:**

```yaml
jobs:
  quality:
    uses: your-org/platform/.github/workflows/quality-by-design.yaml@v1
    secrets: inherit
```

---

## 10. Ephemeral validation flow

`reusable-ephemeral-validation.yaml` — **Full-Stack Smoke + Perf**

```mermaid
sequenceDiagram
  participant GH as GitHub Actions
  participant DC as docker compose
  participant API as backend :8080
  participant WEB as web-player :3000
  participant K6 as k6
  participant QG as quality-gate.sh

  GH->>DC: compose up -d --wait
  DC->>API: start + healthcheck
  DC->>WEB: start

  GH->>API: smoke-test.sh
  GH->>WEB: smoke-test.sh

  GH->>K6: load-test.js
  K6-->>GH: summary JSON

  GH->>QG: evaluate thresholds
  alt pass
    GH->>GH: publish k6 HTML report
  else fail
    GH-->>GH: fail job
  end

  GH->>DC: compose down
```

---

## 11. DevSecOps module scoping

```mermaid
flowchart LR
  subgraph API["API module"]
    A1[Gitleaks]
    A2[Trivy]
    A3[Conftest]
    A4[Syft SBOM]
  end

  subgraph Web["Web module"]
    W1[Gitleaks]
    W2[npm audit]
    W3[Syft SBOM]
  end

  subgraph Mobile["Android / iOS"]
    M1[Gitleaks]
  end

  subgraph QBD["QBD orchestrator"]
    Q1[All checks]
  end
```

Implementation: `.github/scripts/devsecops_checks.py` + `reusable-devsecops.yaml`

---

## 12. Optional integrations

```mermaid
flowchart TB
  subgraph Pipelines["All shipping pipelines"]
    N["notify-start"]
    S["slack-stage-notify"]
    R["report jobs"]
  end

  subgraph Optional["Skipped when secrets missing"]
    Slack["Slack<br/>SLACK_BOT_TOKEN"]
    FB["Firebase<br/>FIREBASE_TOKEN"]
    NR["New Relic<br/>streaming-app-newrelic.yml"]
    GP["GitHub Pages<br/>Allure · k6 HTML"]
  end

  N --> Slack
  R --> Slack
  Pipelines -.-> FB
  Pipelines -.-> GP
  NR -.-> NRDash["dashboards · alerts"]
```

| Integration | Setup doc |
|-------------|-----------|
| Slack threaded stages | [`SLACK-SETUP.md`](SLACK-SETUP.md) |
| Firebase Hosting / App Distribution | [`FIREBASE-SETUP.md`](FIREBASE-SETUP.md) |
| GitHub Pages reports | [`GITHUB-PAGES-SETUP.md`](GITHUB-PAGES-SETUP.md) |

---

## 13. Repository map

```
platform/
├── backend-api/              Spring Boot API + Gradle test tiers
├── web-player/               React app + Vitest + Playwright
├── android-player/           Kotlin + Espresso tiers
├── ios-player/               Swift package + XCTest tiers
├── platform/                 smoke · k6 · policy · scripts
├── docker-compose.yml        ephemeral runtime stack
├── ops/infrastructure/       nginx · HLS tooling
└── .github/
    ├── workflows/
    │   ├── quality-by-design.yaml       orchestrator
    │   ├── reusable-*.yaml              platform templates
    │   └── streaming-app-*.yml          shipping pipelines
    ├── actions/                         composite steps
    └── scripts/                         Slack · Allure · DevSecOps helpers
```

---

## Rendering these diagrams

- **GitHub** — Mermaid renders natively in Markdown preview
- **VS Code / Cursor** — Markdown Preview Mermaid Support extension
- **Export** — [Mermaid Live Editor](https://mermaid.live) or `mmdc` CLI for PNG/SVG slides

For workshop slides, key diagrams are also referenced in [`presentations/01-main-deck.md`](../presentations/01-main-deck.md).
