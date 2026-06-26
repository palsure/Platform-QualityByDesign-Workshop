# Architecture — Quality by Design (DevSecOps + Test Automation in CI/CD)

> **Full application + pipeline diagrams:** [`APP-PIPELINE-ARCHITECTURE.md`](APP-PIPELINE-ARCHITECTURE.md) — runtime stack, Docker Compose, per-module CI/CD job graphs, golden path, and optional integrations.

## System overview

```mermaid
flowchart TB
  subgraph CICD[CI_CD_Pipeline_Blueprints]
    Templates[Reusable_Workflow_Templates]
    GateSvc[Quality_Gates]
    Feedback[PR_Checks_and_Feedback]
  end

  subgraph Teams[Teams]
    RepoA[Repo_A]
    RepoB[Repo_B]
    RepoC[Repo_C]
  end

  RepoA --> Templates
  RepoB --> Templates
  RepoC --> Templates

  Templates --> GateSvc
  GateSvc --> Feedback
```

## Golden path: standard pipeline stages

```mermaid
flowchart LR
  Commit --> Unit[Unit]
  Unit --> Contract[Contract]
  Contract --> Sec[DevSecOps_Defaults]
  Contract --> Build[Build_Artifact]
  Build --> Env[Ephemeral_Env]
  Env --> Integration[Integration]
  Integration --> Smoke[Smoke]
  Smoke --> Perf[Performance_Smoke]
  Perf --> Decision{Promote?}
```

### What goes where (practical defaults)

| Stage | Purpose | Runs on | Goal |
|------|---------|---------|------|
| Unit | fast feedback | every push/PR | correctness of code |
| Contract | prevent breaking changes | every PR | API compatibility |
| DevSecOps defaults | reduce security drift | every PR | secrets/deps/SBOM/policy |
| Integration | validate dependencies | PR and main | real-world interaction |
| Smoke | validate deploy correctness | ephemeral env | “it works” checks |
| Perf smoke | catch obvious regressions | ephemeral env | latency/error thresholds |

### Ephemeral environments

**CI (default):** Docker Compose stack per PR — see [`reusable-ephemeral-validation.yaml`](../.github/workflows/reusable-ephemeral-validation.yaml).

**Optional (advanced):** K8s namespace-per-PR via [`platform/scripts/create-env.sh`](../platform/scripts/create-env.sh) and [`platform/env/backend.yaml`](../platform/env/backend.yaml).

### Minimal contract for platform-managed checks

| Contract | Endpoint | Required |
|---------|----------|----------|
| Liveness | `GET /actuator/health` | ✅ |
| Readiness | `GET /actuator/health/readiness` | ✅ |
| Metrics | `GET /actuator/prometheus` | ✅ (recommended) |
| Web root | `GET /` (web-player) | ✅ |

## Quality gates and feedback

```mermaid
flowchart LR
  Gates[Quality_Gates] --> Evidence[Test_Evidence]
  Evidence --> Report[PR_Checks_and_Summary]
  Report --> Dev[Developer_Action]
  Dev --> Fix[Fix_or_Improve_Tests]
  Fix --> Gates
```

## DevSecOps-by-default (built into the platform)

```mermaid
flowchart LR
  PR[Pull_Request] --> Secrets[Secrets_Scan]
  Secrets --> Deps[Dependency_Checks]
  Deps --> SBOM[SBOM_Generation]
  SBOM --> Img[Container_Image_Scan]
  Img --> Policy[Policy_as_Code]
  Policy --> Merge{Merge?}
```

Implementation: [`.github/workflows/reusable-devsecops.yaml`](../.github/workflows/reusable-devsecops.yaml)

## Reference implementation

All sample apps and the canonical pipeline live in this repo:

| Workshop item | Location |
|---------------|----------|
| Sample apps | `backend-api`, `web-player`, `android-player`, `ios-player` |
| QBD orchestrator | `.github/workflows/quality-by-design.yaml` |
| Reusable templates | `.github/workflows/reusable-*.yaml` |
| DevSecOps + policy | `platform/` |
| Ephemeral env (compose) | `docker-compose.yml` |
| Shipping pipelines | `.github/workflows/streaming-app-*.yml` |
| PR feedback | `platform/scripts/pr-summary.py` |
| Perf threshold script | `platform/scripts/quality-gate.sh` |

Gate orchestration patterns adapted from `.github/workflows/streaming-app-api.yml`.

### What to keep in the critical path (what worked)
- Secrets scanning and dependency checks (fast, high value)
- Policy-as-code checks for K8s manifests (label/resource limits/no latest)

### What to run async (what worked)
- Full container image scans and deep SAST (longer runtime)
- Scheduled “fleet-wide” SBOM review jobs

### Lessons learned (no fluff)
- **What worked**: simple gates, clear ownership, stable environments, short perf smokes, and release-notes for pipeline changes.
- **What didn’t**: flaky E2E in the critical path, “one template for everything,” unversioned pipeline breaking changes, and ticket-based onboarding.
