#!/usr/bin/env bash
# Local DevSecOps gates — mirrors CI checks in reusable-devsecops.yaml (best-effort).
#
# Usage (from repo root):
#   bash platform/scripts/devsecops-gates.sh              # QBD — all checks
#   bash platform/scripts/devsecops-gates.sh API            # API module only
#   MODULE=WEB bash platform/scripts/devsecops-gates.sh     # env var also works
#
# Options:
#   STRICT=1  — fail when a check is skipped (tool missing), not just when it fails
#
# Install hints (optional — Docker fallbacks used when available):
#   brew install gitleaks trivy syft conftest

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

MODULE="${1:-${MODULE:-QBD}}"
MODULE="$(echo "$MODULE" | tr '[:lower:]' '[:upper:]')"
STRICT="${STRICT:-0}"

OUTCOME_GITLEAKS=skipped
OUTCOME_NPM_AUDIT=skipped
OUTCOME_TRIVY=skipped
OUTCOME_CONFTEST=skipped
OUTCOME_SBOM_API=skipped
OUTCOME_SBOM_WEB=skipped

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() { printf '%b\n' "$*"; }

set_outcome() {
  local key="$1"
  local value="$2"
  eval "OUTCOME_${key}=\"\$value\""
}

get_outcome() {
  local key="$1"
  eval "echo \"\${OUTCOME_${key}}\""
}

pass() { set_outcome "$1" success; log "${GREEN}PASS${NC}  $2"; }
fail() { set_outcome "$1" failure; log "${RED}FAIL${NC}  $2"; }
skip() { set_outcome "$1" skipped; log "${YELLOW}SKIP${NC}  $2 — ${3:-tool not available}"; }

docker_ok() {
  command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1
}

# True when MODULE is QBD or matches the target platform (API, WEB, …).
module_includes() {
  local target="$1"
  [ "$MODULE" = "QBD" ] || [ "$MODULE" = "$target" ]
}

module_is_mobile() {
  [ "$MODULE" = "ANDROID" ] || [ "$MODULE" = "IOS" ]
}

run_gitleaks() {
  log ""
  log "${BLUE}[Gitleaks]${NC} secrets scan (git tracked commits)"
  local gitleaks_args=(detect --source "$ROOT_DIR" --config "$ROOT_DIR/.gitleaks.toml" --no-banner --redact)
  if command -v gitleaks >/dev/null 2>&1; then
    if gitleaks "${gitleaks_args[@]}"; then
      pass GITLEAKS "No secrets detected"
    else
      fail GITLEAKS "Secrets or gitleaks findings — see output above"
    fi
  elif docker_ok; then
    # Git mode (no --no-git): scan commits only — avoids false positives in .build/, node_modules/, etc.
    if docker run --rm -v "$ROOT_DIR:/repo:ro" -w /repo zricethezav/gitleaks:latest \
         detect --source=. --config=.gitleaks.toml --no-banner --redact; then
      pass GITLEAKS "No secrets detected (Docker)"
    else
      fail GITLEAKS "Secrets found (Docker gitleaks)"
    fi
  else
    skip GITLEAKS "Gitleaks" "install: brew install gitleaks (or start Docker)"
  fi
}

run_npm_audit() {
  log ""
  log "${BLUE}[npm audit]${NC} web-player dependencies (high+)"
  if ! module_includes WEB; then
    skip NPM_AUDIT "npm audit" "not in scope for module $MODULE"
    return 0
  fi
  if ! command -v npm >/dev/null 2>&1; then
    skip NPM_AUDIT "npm audit" "install Node.js / npm"
    return 0
  fi
  if [ ! -f "$ROOT_DIR/web-player/package.json" ]; then
    skip NPM_AUDIT "npm audit" "web-player/package.json missing"
    return 0
  fi
  if (
    cd "$ROOT_DIR/web-player"
    npm install --ignore-scripts --no-audit --silent
    npm audit --audit-level=high
  ); then
    pass NPM_AUDIT "No high/critical npm vulnerabilities"
  else
    fail NPM_AUDIT "npm audit reported high/critical issues"
  fi
}

run_trivy() {
  log ""
  log "${BLUE}[Trivy]${NC} backend-api filesystem (CRITICAL/HIGH)"
  if ! module_includes API; then
    skip TRIVY "Trivy" "not in scope for module $MODULE"
    return 0
  fi
  if command -v trivy >/dev/null 2>&1; then
    if trivy fs "$ROOT_DIR/backend-api" \
         --severity CRITICAL,HIGH --ignore-unfixed --exit-code 1 --quiet; then
      pass TRIVY "No critical/high unfixed issues"
    else
      fail TRIVY "Trivy found critical/high vulnerabilities"
    fi
  elif docker_ok; then
    if docker run --rm -v "$ROOT_DIR/backend-api:/scan:ro" aquasec/trivy:latest \
         fs /scan --severity CRITICAL,HIGH --ignore-unfixed --exit-code 1 --quiet; then
      pass TRIVY "No critical/high unfixed issues (Docker)"
    else
      fail TRIVY "Trivy found critical/high vulnerabilities (Docker)"
    fi
  else
    skip TRIVY "Trivy" "install: brew install trivy (or start Docker)"
  fi
}

run_conftest() {
  log ""
  log "${BLUE}[Conftest]${NC} platform/env/backend.yaml vs platform/policy/"
  if ! module_includes API; then
    skip CONFTEST "Conftest" "not in scope for module $MODULE"
    return 0
  fi
  if [ ! -f "$ROOT_DIR/platform/env/backend.yaml" ]; then
    skip CONFTEST "Conftest" "platform/env/backend.yaml missing"
    return 0
  fi
  if command -v conftest >/dev/null 2>&1; then
    if conftest test "$ROOT_DIR/platform/env/backend.yaml" -p "$ROOT_DIR/platform/policy"; then
      pass CONFTEST "Policy checks passed"
    else
      fail CONFTEST "Conftest policy violations"
    fi
  elif docker_ok; then
    if docker run --rm \
         -v "$ROOT_DIR/platform/env:/env:ro" \
         -v "$ROOT_DIR/platform/policy:/policy:ro" \
         openpolicyagent/conftest:v0.56.0 \
         test /env/backend.yaml -p /policy; then
      pass CONFTEST "Policy checks passed (Docker)"
    else
      fail CONFTEST "Conftest policy violations (Docker)"
    fi
  else
    skip CONFTEST "Conftest" "install: brew install conftest (or start Docker)"
  fi
}

run_syft() {
  local target="$1"
  local key="$2"
  local label="$3"
  local out_name="$4"

  log ""
  log "${BLUE}[Syft SBOM]${NC} $label"
  local out_dir="$ROOT_DIR/platform/.local/sbom"
  mkdir -p "$out_dir"
  local out_file="$out_dir/$out_name"

  if command -v syft >/dev/null 2>&1; then
    if syft scan "$target" -o spdx-json > "$out_file"; then
      pass "$key" "SBOM written to platform/.local/sbom/$out_name"
    else
      fail "$key" "Syft scan failed"
    fi
  elif docker_ok; then
    if docker run --rm -v "$target:/scan:ro" anchore/syft:latest \
         scan /scan -o spdx-json > "$out_file"; then
      pass "$key" "SBOM written to platform/.local/sbom/$out_name (Docker)"
    else
      fail "$key" "Syft scan failed (Docker)"
    fi
  else
    skip "$key" "Syft SBOM" "install: brew install syft (or start Docker)"
  fi
}

run_sbom_api() {
  if ! module_includes API; then
    skip SBOM_API "SBOM — backend-api" "not in scope for module $MODULE"
    return 0
  fi
  run_syft "$ROOT_DIR/backend-api" SBOM_API "backend-api" "backend-api.spdx.json"
}

run_sbom_web() {
  if ! module_includes WEB; then
    skip SBOM_WEB "SBOM — web-player" "not in scope for module $MODULE"
    return 0
  fi
  run_syft "$ROOT_DIR/web-player" SBOM_WEB "web-player" "web-player.spdx.json"
}

check_applies() {
  local key="$1"
  case "$MODULE" in
    QBD) return 0 ;;
    ANDROID|IOS)
      [ "$key" = "GITLEAKS" ]
      ;;
    WEB)
      case "$key" in
        GITLEAKS|NPM_AUDIT|SBOM_WEB) return 0 ;;
        *) return 1 ;;
      esac
      ;;
    API)
      case "$key" in
        GITLEAKS|TRIVY|CONFTEST|SBOM_API) return 0 ;;
        *) return 1 ;;
      esac
      ;;
    *) return 1 ;;
  esac
}

print_summary() {
  log ""
  log "${BLUE}=== DevSecOps summary (module: $MODULE) ===${NC}"
  for key in GITLEAKS NPM_AUDIT TRIVY CONFTEST SBOM_API SBOM_WEB; do
    if check_applies "$key"; then
      printf '  %-12s %s\n' "$key" "$(get_outcome "$key")"
    fi
  done
}

gate_passed() {
  local key outcome
  for key in GITLEAKS NPM_AUDIT TRIVY CONFTEST SBOM_API SBOM_WEB; do
    check_applies "$key" || continue
    outcome="$(get_outcome "$key")"
    if [ "$outcome" = "failure" ]; then
      return 1
    fi
    if [ "$STRICT" = "1" ] && [ "$outcome" = "skipped" ]; then
      return 1
    fi
  done
  return 0
}

main() {
  log "${BLUE}=== DevSecOps gates (Quality-by-Design) ===${NC}"
  log "Repo:   $ROOT_DIR"
  log "Module: $MODULE  (QBD | API | WEB | ANDROID | IOS)"

  run_gitleaks

  if module_is_mobile; then
    :
  else
    run_npm_audit
    run_trivy
    run_conftest
    run_sbom_api
    run_sbom_web
  fi

  print_summary

  if gate_passed; then
    log ""
    log "${GREEN}DevSecOps gate: PASS${NC}"
    exit 0
  fi

  log ""
  log "${RED}DevSecOps gate: FAIL${NC}"
  log "Tip: run a scoped module, e.g.  bash platform/scripts/devsecops-gates.sh API"
  exit 1
}

main "$@"
