#!/usr/bin/env python3
"""Evaluate the DevSecOps gate and write a PR-summary artifact."""

from __future__ import annotations

import json
import os
from pathlib import Path

from devsecops_checks import CHECKS, applicable_checks, gate_passed, normalize_module


def write_pr_summary(module: str, outcomes: dict[str, str], passed: bool) -> Path:
    out = Path('artifacts/devsecops/summary.json')
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        'module': module,
        'passed': passed,
        'checks': {check.key: outcomes.get(check.key, 'skipped') for check in CHECKS},
    }
    out.write_text(json.dumps(payload, indent=2), encoding='utf-8')
    print(f'Wrote {out}')
    return out


def main() -> int:
    module = normalize_module(os.environ.get('MODULE_NAME', ''))
    outcomes = {check.key: os.environ.get(check.key, 'skipped') for check in CHECKS}

    active = applicable_checks(module)
    labels = ', '.join(check.name for check in active) or '(none)'
    print(f'Module={module or "unknown"} | checks in scope: {labels}')

    for check in active:
        outcome = outcomes[check.key]
        print(f'  {check.name}={outcome}')

    passed = gate_passed(module, outcomes)
    write_pr_summary(module, outcomes, passed)

    if passed:
        print('passed=true')
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as gh:
            gh.write('passed=true\n')
        return 0

    print('passed=false')
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as gh:
        gh.write('passed=false\n')
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
