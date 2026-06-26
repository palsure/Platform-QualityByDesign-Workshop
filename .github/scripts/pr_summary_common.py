"""Shared PR comment helpers for security and load-test stages."""

from __future__ import annotations

import glob
import json
import math
import os
from pathlib import Path

from devsecops_checks import CHECKS, applicable_checks

THRESHOLD = 80


def pct(passed: int, total: int) -> int:
    return math.floor(100.0 * passed / total) if total else 0


def verdict(passed: int, failed: int, total: int, job_result: str) -> tuple[str, str]:
    """Returns (icon, label). Prefer counts when tests/checks ran."""
    if total > 0:
        if failed == 0 and pct(passed, total) >= THRESHOLD:
            return '✅', 'PASSED'
        return '❌', 'FAILED'
    if job_result == 'success':
        return '✅', 'PASSED'
    if job_result in ('skipped', 'cancelled'):
        return '⏭', 'SKIPPED'
    if job_result == 'failure':
        return '❌', 'FAILED'
    return '⏭', 'SKIPPED'


def format_count_row(
    name: str,
    passed: int,
    failed: int,
    skipped: int,
    total: int,
    job_result: str,
    report_link: str,
) -> str:
    rate = pct(passed, total)
    icon, label = verdict(passed, failed, total, job_result)
    rate_str = f'{rate}%' if total > 0 else 'n/a'
    return (
        f'| {icon} **{name}** | {passed} | {failed} | {skipped} | {total} '
        f'| {rate_str} | **{label}** | {report_link} |'
    )


def devsecops_counts(module: str) -> tuple[int, int, int, int]:
    """(passed, failed, skipped, total) for checks in scope for this module."""
    outcomes = {check.key: os.environ.get(check.key, 'skipped') for check in CHECKS}
    passed = failed = skipped = 0
    for check in applicable_checks(module):
        outcome = (outcomes.get(check.key) or 'skipped').lower()
        if outcome == 'success':
            passed += 1
        elif outcome == 'failure':
            failed += 1
        else:
            skipped += 1
    total = passed + failed + skipped
    return passed, failed, skipped, total


def parse_k6_summary(path: str) -> tuple[int, int, int, int]:
    """(passed, failed, skipped, total) from k6 checks metric."""
    candidates = [path] if path else []
    candidates += glob.glob('artifacts/k6/**/k6-summary.json', recursive=True)
    candidates += ['platform/tests/load/k6-summary.json']
    seen: set[str] = set()
    for candidate in candidates:
        if not candidate or candidate in seen:
            continue
        seen.add(candidate)
        file_path = Path(candidate)
        if not file_path.exists():
            continue
        try:
            summary = json.loads(file_path.read_text(encoding='utf-8'))
            checks = (summary.get('metrics') or {}).get('checks', {}).get('values') or {}
            passes = int(checks.get('passes', 0))
            fails = int(checks.get('fails', 0))
            return passes, fails, 0, passes + fails
        except Exception as exc:
            print(f'⚠️  k6 summary parse error ({candidate}): {exc}')
    return 0, 0, 0, 0


def security_row(module: str) -> str | None:
    result = os.environ.get('STAGE_SECURITY_RESULT', '').strip().lower()
    if not result:
        return None
    passed, failed, skipped, total = devsecops_counts(module)
    return format_count_row(
        'Security (DevSecOps)',
        passed,
        failed,
        skipped,
        total,
        result,
        '–',
    )


def load_test_row() -> str | None:
    result = os.environ.get('STAGE_LOAD_RESULT', '').strip().lower()
    if not result:
        return None
    k6_path = os.environ.get('K6_SUMMARY_PATH', 'artifacts/k6/k6-summary.json')
    passed, failed, skipped, total = parse_k6_summary(k6_path)
    report_url = os.environ.get('LOAD_REPORT_URL', '').strip()
    report_link = f'[📊 k6 Report]({report_url})' if report_url else '–'
    return format_count_row(
        'Load Test (k6)',
        passed,
        failed,
        skipped,
        total,
        result,
        report_link,
    )


def extra_stage_rows(module: str) -> list[str]:
    rows: list[str] = []
    sec = security_row(module)
    if sec:
        rows.append(sec)
    load = load_test_row()
    if load:
        rows.append(load)
    return rows
