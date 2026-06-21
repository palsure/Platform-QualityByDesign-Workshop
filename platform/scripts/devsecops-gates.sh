#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

echo "=== DevSecOps gates (Quality-by-Design) ==="

echo "[1/5] Secrets scan"
echo " - Run gitleaks/gitleaks-action in CI (critical path)."

echo "[2/5] Dependency checks"
if command -v npm >/dev/null 2>&1 && [ -f "$ROOT_DIR/web-player/package.json" ]; then
  (cd "$ROOT_DIR/web-player" && npm install --ignore-scripts --no-audit && npm audit --audit-level=high) \
    || echo "WARN: npm audit reported issues in web-player."
fi
if [ -f "$ROOT_DIR/backend-api/gradlew" ]; then
  echo " - Gradle dependency check runs in CI via OWASP/Trivy on backend-api artifact."
fi

echo "[3/5] SBOM generation"
echo " - Run anchore/sbom-action on backend-api/ and web-player/ in CI."

echo "[4/5] Container/filesystem scan"
echo " - Run aquasecurity/trivy-action on backend-api/ in CI."

echo "[5/5] Policy-as-code (Conftest/OPA)"
if command -v conftest >/dev/null 2>&1; then
  conftest test "$ROOT_DIR/platform/env/backend.yaml" -p "$ROOT_DIR/platform/policy"
else
  echo "SKIP: conftest not installed. CI runs via openpolicyagent/conftest container."
fi

echo "DevSecOps gates completed."
