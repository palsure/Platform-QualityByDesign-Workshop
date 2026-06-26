# Platform — Quality by Design

Platform-managed scripts, policy-as-code, and post-deploy tests for the **Quality-by-Design** workshop gate chain.

Used by [`../.github/workflows/quality-by-design.yaml`](../.github/workflows/quality-by-design.yaml).

## Layout

```
platform/
├── env/backend.yaml           # K8s manifest (namespace-per-PR pattern)
├── policy/k8s.rego            # Conftest/OPA — labels, resources, no :latest
├── scripts/
│   ├── create-env.sh          # kubectl namespace qoe-pr-<id>
│   ├── destroy-env.sh
│   ├── devsecops-gates.sh     # local best-effort DevSecOps
│   ├── pr-summary.py          # PR comment with gate stage table
│   └── quality-gate.sh        # post-k6 perf threshold gate (error rate + p95)
└── tests/
    ├── smoke/smoke-test.sh    # post-deploy smoke (API + web)
    └── load/load-test.js      # k6 performance smoke
```

## Local usage

```bash
# DevSecOps gates (from repo root — no chmod needed)
bash platform/scripts/devsecops-gates.sh              # all checks (QBD)
bash platform/scripts/devsecops-gates.sh API          # API module only
bash platform/scripts/devsecops-gates.sh WEB          # web module only

# Ephemeral K8s env (optional — kind/minikube)
docker compose build backend
./platform/scripts/create-env.sh 123
API_BASE=http://localhost:8080 WEB_BASE=http://localhost:3000 ./platform/tests/smoke/smoke-test.sh
./platform/scripts/destroy-env.sh 123

# Smoke against running compose stack
docker compose up -d
API_BASE=http://localhost:8080 WEB_BASE=http://localhost:3000 ./platform/tests/smoke/smoke-test.sh

# Perf threshold gate (after k6 — thresholds match load-test.js)
MAX_ERROR_RATE=0.01 MAX_P95_MS=800 ERROR_RATE=0 P95_MS=200 ./platform/scripts/quality-gate.sh local
```

## Sample apps

This platform layer validates the sample apps in [`../backend-api`](../backend-api), [`../web-player`](../web-player), [`../android-player`](../android-player), and [`../ios-player`](../ios-player).
