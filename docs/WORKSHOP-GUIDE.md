# Workshop Guide — Quality by Design (PlatformCon 2026)

**Theme 2: Platform Engineering in Practice** · Live workshop · ~2 hours

## Description

A hands-on workshop on embedding DevSecOps and test automation into CI/CD pipelines using reusable workflow templates, security-by-default checks, ephemeral test environments, and enforceable quality gates.

Teams rarely lack tests or security — they lack **consistency**. One service has strong gates, the next has none, and the platform team ends up maintaining one-off pipelines instead of improving the platform.

Participants turn DevSecOps and test automation into CI/CD defaults. Using this self-contained demo repo (streaming app sample apps), they wire up a standard gate chain, adopt reusable GitHub Actions workflow templates, and spin up ephemeral test environments.

## Learning outcomes

By the end of this workshop, participants will be able to:

1. Design a test automation strategy as a CI/CD gate policy (not a per-team project)
2. Build opinionated, reusable CI/CD pipeline templates (“pipelines as a product”)
3. Provision self-service ephemeral test environments for safer validation
4. Implement quality gates that are fast, enforceable, and actionable
5. Create feedback loops that improve adoption and reduce flaky gate failures

## Gate chain (the golden path)

```
unit → contract → integration (BAT) → ephemeral env → smoke → perf smoke → promote
              ↕
        DevSecOps defaults (parallel)
```

| Stage | Where | Command / artifact |
|-------|-------|-------------------|
| Unit | `backend-api`, `web-player` | `./gradlew unitTest` · `npm test` |
| Contract | `backend-api` | `./gradlew contractTest` |
| Integration (BAT) | `backend-api` | `./gradlew batTest` |
| DevSecOps | parallel | [`reusable-devsecops.yaml`](../.github/workflows/reusable-devsecops.yaml) |
| Ephemeral env | CI + local | `docker compose up -d` |
| Smoke | post-deploy | [`platform/tests/smoke/smoke-test.sh`](../platform/tests/smoke/smoke-test.sh) |
| Perf smoke | post-deploy | k6 [`platform/tests/load/load-test.js`](../platform/tests/load/load-test.js) + [`quality-gate.sh`](../platform/scripts/quality-gate.sh) |
| Promote | orchestrator | [`quality-by-design.yaml`](../.github/workflows/quality-by-design.yaml) |
| PR feedback | every PR | [`platform/scripts/pr-summary.py`](../platform/scripts/pr-summary.py) |

## Hands-on flow (facilitator script)

### 1. Start the demo stack (5 min)

```bash
docker compose up -d
# Web  → http://localhost:3000
# API  → http://localhost:8080
```

### 2. Run test gates locally (10 min)

```bash
cd backend-api
./gradlew unitTest contractTest batTest

cd ../web-player
npm test
```

Talk track: unit = fast feedback; contract = API compatibility; BAT = integration with real dependencies.

### 3. DevSecOps defaults (10 min)

```bash
chmod +x platform/scripts/devsecops-gates.sh
./platform/scripts/devsecops-gates.sh
```

Show [`platform/policy/k8s.rego`](../platform/policy/k8s.rego) and Conftest in [`reusable-devsecops.yaml`](../.github/workflows/reusable-devsecops.yaml).

### 4. Ephemeral validation (15 min)

```bash
API_BASE=http://localhost:8080 WEB_BASE=http://localhost:3000 \
  platform/tests/smoke/smoke-test.sh

# k6 perf smoke (install k6 locally or use CI)
k6 run platform/tests/load/load-test.js

MAX_ERROR_RATE=0.01 MAX_P95_MS=800 ERROR_RATE=0 P95_MS=200 \
  platform/scripts/quality-gate.sh local
```

Talk track: Compose stack = ephemeral env in CI; K8s namespace-per-PR scripts in [`platform/scripts/create-env.sh`](../platform/scripts/create-env.sh) are the optional advanced module.

### 5. Reusable workflow templates (20 min)

Walk through:

- [`.github/workflows/quality-by-design.yaml`](../.github/workflows/quality-by-design.yaml) — orchestrator
- [`reusable-test-gates.yaml`](../.github/workflows/reusable-test-gates.yaml)
- [`reusable-devsecops.yaml`](../.github/workflows/reusable-devsecops.yaml)
- [`reusable-ephemeral-validation.yaml`](../.github/workflows/reusable-ephemeral-validation.yaml)
- [`.github/actions/evaluate-jest-gate/`](../.github/actions/evaluate-jest-gate/) — enforceable web unit gate

Open a PR and show the updating comment from `pr-summary.py`.

### 6. Adoption pattern (10 min)

Teams call the platform pipeline from their repo — see [ADOPTION.md](ADOPTION.md).

## Optional: K8s namespace-per-PR

For participants with kind/minikube:

```bash
docker compose build backend
./platform/scripts/create-env.sh 123
./platform/scripts/destroy-env.sh 123
```

CI uses Docker Compose for speed; K8s scripts demonstrate the self-service environment pattern from the submission.

## Sample apps (demo vehicle)

The workshop uses a real **streaming app** — not a toy CRUD app:

| App | Role |
|-----|------|
| `backend-api` | Spring Boot API + contract/BAT tests |
| `web-player` | React/Vite + Vitest + Playwright |
| `android-player` / `ios-player` | Mobile players (separate per-platform pipelines) |

See [README.md](../README.md) for the full app architecture.

## Further reading

- [SUBMISSION.md](SUBMISSION.md) — PlatformCon CFP text
- [ARCHITECTURE.md](ARCHITECTURE.md) — diagrams and lessons learned
- [ADOPTION.md](ADOPTION.md) — how teams consume the platform pipeline
- [SLACK-SETUP.md](SLACK-SETUP.md) — Slack bot + GitHub secrets for CI notifications
- [FIREBASE-SETUP.md](FIREBASE-SETUP.md) — Firebase Hosting & App Distribution for web/mobile deploys
