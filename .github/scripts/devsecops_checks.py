"""Shared DevSecOps check definitions scoped per pipeline module."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class DevSecOpsCheck:
    key: str          # env var suffix, e.g. GITLEAKS
    name: str
    description: str
    modules: frozenset[str] | None  # None = every module; QBD always runs all


# QBD is the cross-module quality gate — it runs the full check set.
CHECKS: tuple[DevSecOpsCheck, ...] = (
    DevSecOpsCheck("GITLEAKS", "Gitleaks", "secrets scan", None),
    DevSecOpsCheck("NPM_AUDIT", "npm audit", "web-player dependencies", frozenset({"WEB"})),
    DevSecOpsCheck("TRIVY", "Trivy", "backend-api filesystem (CRITICAL/HIGH)", frozenset({"API"})),
    DevSecOpsCheck("CONFTEST", "Conftest", "platform policy (backend.yaml)", frozenset({"API"})),
    DevSecOpsCheck("SBOM_API", "SBOM — backend-api", "Syft SPDX", frozenset({"API"})),
    DevSecOpsCheck("SBOM_WEB", "SBOM — web-player", "Syft SPDX", frozenset({"WEB"})),
)


def normalize_module(module: str) -> str:
    return (module or "").strip()


def is_qbd(module: str) -> bool:
    return normalize_module(module).upper() == "QBD"


def applicable_checks(module: str) -> list[DevSecOpsCheck]:
    mod = normalize_module(module)
    if is_qbd(mod):
        return list(CHECKS)
    mod_upper = mod.upper()
    return [
        check
        for check in CHECKS
        if check.modules is None or mod_upper in {m.upper() for m in check.modules}
    ]


def gate_passed(module: str, outcomes: dict[str, str]) -> bool:
    for check in applicable_checks(module):
        outcome = (outcomes.get(check.key) or "skipped").lower()
        if outcome != "success":
            return False
    return True
