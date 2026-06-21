# Quality by Design

> **PlatformCon 2026** · Theme 2: Platform Engineering in Practice  
> **Embedding DevSecOps and Test Automation into CI/CD Pipeline**

Self-contained demo repo for the PlatformCon workshop. A **streaming app** (`backend-api`, `web-player`, `android-player`, `ios-player`) plus a **platform golden path** — reusable workflow templates, contract tests, DevSecOps defaults, ephemeral validation, and PR feedback.

## Workshop pitch

Teams rarely lack tests or security — they lack **consistency**. This repo shows how to turn DevSecOps and test automation into CI/CD defaults using reusable GitHub Actions templates, security-by-default checks, ephemeral test environments, and enforceable quality gates.

**Facilitator script:** [WORKSHOP-GUIDE.md](WORKSHOP-GUIDE.md) · **CFP text:** [SUBMISSION.md](SUBMISSION.md)

## Gate chain

```
unit → contract → integration (BAT) → ephemeral env → smoke → perf smoke → promote
              ↕
        DevSecOps defaults (parallel)
```

## What the platform layer adds

| Piece | Location |
|-------|----------|
| Orchestrator | [`.github/workflows/quality-by-design.yaml`](../.github/workflows/quality-by-design.yaml) |
| Reusable templates | [`reusable-devsecops.yaml`](../.github/workflows/reusable-devsecops.yaml), [`reusable-test-gates.yaml`](../.github/workflows/reusable-test-gates.yaml), [`reusable-ephemeral-validation.yaml`](../.github/workflows/reusable-ephemeral-validation.yaml) |
| Contract tests | `backend-api` → `./gradlew contractTest` (`ApiContractIT.java`) |
| DevSecOps | Gitleaks · npm audit · Syft SBOM · Trivy · Conftest |
| Ephemeral env | `docker compose` stack per PR (K8s scripts optional in `platform/scripts/`) |
| Smoke + perf | [`platform/tests/smoke/`](../platform/tests/smoke/), [`platform/tests/load/`](../platform/tests/load/), [`quality-gate.sh`](../platform/scripts/quality-gate.sh) |
| PR feedback | [`platform/scripts/pr-summary.py`](../platform/scripts/pr-summary.py) |
| Jest gate action | [`.github/actions/evaluate-jest-gate/`](../.github/actions/evaluate-jest-gate/) |

## Quick start

```bash
docker compose up -d
# API  → http://localhost:8080
# Web  → http://localhost:3000

cd backend-api && ./gradlew unitTest contractTest batTest
cd ../web-player && npm test
```

## Repo layout

```
platform/                  # repo root
├── backend-api/           # Java/Spring Boot + contract tests
├── web-player/            # React/Vite + Vitest + Playwright
├── android-player/        # Kotlin/ExoPlayer
├── ios-player/            # Swift/AVPlayer
├── platform/              # DevSecOps scripts, policy, smoke/k6 tests
├── docs/                  # Workshop docs (this folder)
└── .github/workflows/
    ├── quality-by-design.yaml      ← QBD orchestrator
    ├── reusable-*.yaml             ← platform templates
    └── streaming-app-*.yml        ← per-platform shipping pipelines
```

## Learning outcomes

1. Design test automation as a CI/CD gate policy
2. Build reusable CI/CD pipeline templates (“pipelines as a product”)
3. Provision ephemeral test environments for safer validation
4. Implement fast, enforceable, actionable quality gates
5. Create PR feedback loops that reduce flaky gate failures

## Further reading

| Doc | Purpose |
|-----|---------|
| [WORKSHOP-GUIDE.md](WORKSHOP-GUIDE.md) | Hands-on facilitator script |
| [ADOPTION.md](ADOPTION.md) | How teams call the platform pipeline |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Diagrams, stage table, lessons learned |
| [SUBMISSION.md](SUBMISSION.md) | PlatformCon CFP submission |
