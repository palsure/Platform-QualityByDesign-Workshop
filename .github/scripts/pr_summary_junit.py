#!/usr/bin/env python3
"""
pr_summary_junit.py

Reads JUnit XML test results for unit / BAT / smoke stages and writes a
Markdown PR comment with a results table and Allure report links.

Used by Android, iOS, and other modules that publish JUnit artifacts.

Artifacts expected (paths relative to repo root):
  artifacts/unit/   — unit JUnit XML
  artifacts/bat/    — BAT JUnit XML
  artifacts/smoke/  — smoke JUnit XML

Required env vars:
  GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER, GITHUB_SHA
  PR_NUMBER, BRANCH_NAME, MODULE_NAME
  STAGE_UNIT_RESULT, STAGE_BAT_RESULT, STAGE_SMOKE_RESULT  — success|failure|skipped
  BASE_PAGES_URL   — e.g. https://user.github.io/repo/allure/27/android
  COMMENT_MARKER   — HTML comment marker for upsert (e.g. <!-- qoe-android-results -->)
  OUTPUT_FILE      — output path (default pr-comment.md)
"""
from __future__ import annotations

import glob
import math
import os
import xml.etree.ElementTree as ET
from pathlib import Path

from pr_summary_common import format_count_row, load_test_row, pct, security_row, verdict


def parse_junit_dir(directory: str) -> tuple[int, int, int, int]:
    """(passed, failed, skipped, total) from all JUnit XML in a directory."""
    passed = failed = skipped = total = 0
    patterns = [
        f'{directory}/**/TEST-*.xml',
        f'{directory}/**/*-junit.xml',
        f'{directory}/**/swift-junit.xml',
        f'{directory}/**/vitest-junit.xml',
    ]
    seen: set[str] = set()
    for pat in patterns:
        for f in glob.glob(pat, recursive=True):
            if f in seen:
                continue
            seen.add(f)
            try:
                root = ET.parse(f).getroot()
                suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
                for suite in suites:
                    t = int(suite.attrib.get('tests', 0))
                    fa = int(suite.attrib.get('failures', 0)) + int(suite.attrib.get('errors', 0))
                    s = int(suite.attrib.get('skipped', 0))
                    total += t
                    failed += fa
                    skipped += s
                    passed += max(0, t - fa - s)
            except Exception as e:
                print(f'⚠️  XML parse error ({f}): {e}')
    return passed, failed, skipped, total


def pct(passed: int, total: int) -> int:
    return math.floor(100.0 * passed / total) if total else 0


def verdict_row(passed: int, failed: int, total: int, job_result: str) -> tuple[str, str]:
    return verdict(passed, failed, total, job_result)


def main() -> None:
    repo = os.environ.get('GITHUB_REPOSITORY', 'owner/repo')
    run_id = os.environ.get('GITHUB_RUN_ID', '')
    run_number = os.environ.get('GITHUB_RUN_NUMBER', '')
    sha = os.environ.get('GITHUB_SHA', 'unknown')[:7]
    pr_number = os.environ.get('PR_NUMBER', '')
    branch = os.environ.get('BRANCH_NAME', 'unknown')
    module = os.environ.get('MODULE_NAME', 'MODULE')
    pages_base = os.environ.get('BASE_PAGES_URL', '')
    marker = os.environ.get('COMMENT_MARKER', '<!-- qoe-junit-results -->')
    out_path = os.environ.get('OUTPUT_FILE', 'pr-comment.md')

    unit_result = os.environ.get('STAGE_UNIT_RESULT', 'skipped').lower()
    bat_result = os.environ.get('STAGE_BAT_RESULT', 'skipped').lower()
    smoke_result = os.environ.get('STAGE_SMOKE_RESULT', 'skipped').lower()

    unit_dir = os.environ.get('JUNIT_UNIT_PATH', 'artifacts/unit')
    bat_dir = os.environ.get('JUNIT_BAT_PATH', 'artifacts/bat')
    smoke_dir = os.environ.get('JUNIT_SMOKE_PATH', 'artifacts/smoke')

    run_url = f'https://github.com/{repo}/actions/runs/{run_id}' if run_id else f'https://github.com/{repo}'
    pr_url = f'https://github.com/{repo}/pull/{pr_number}' if pr_number else ''

    unit_p, unit_f, unit_s, unit_t = parse_junit_dir(unit_dir)
    bat_p, bat_f, bat_s, bat_t = parse_junit_dir(bat_dir)
    smoke_p, smoke_f, smoke_s, smoke_t = parse_junit_dir(smoke_dir)

    stages = [
        ('Unit Tests', unit_p, unit_f, unit_s, unit_t, unit_result, 'unit'),
        ('BAT Tests', bat_p, bat_f, bat_s, bat_t, bat_result, 'bat'),
        ('Smoke Tests', smoke_p, smoke_f, smoke_s, smoke_t, smoke_result, 'smoke'),
    ]

    overall_failed = False
    rows = []
    sec = security_row(module)
    if sec:
        rows.append(sec)
        if '**FAILED**' in sec:
            overall_failed = True

    for name, p, f, s, t, job_result, stage_key in stages:
        _, label = verdict_row(p, f, t, job_result)
        if label == 'FAILED':
            overall_failed = True
        report_link = f'[📊 Allure]({pages_base}/{stage_key})' if pages_base else '–'
        rows.append(format_count_row(name, p, f, s, t, job_result, report_link))

    load = load_test_row()
    if load:
        rows.append(load)
        if '**FAILED**' in load:
            overall_failed = True

    table = '\n'.join(rows)

    overall_icon = '❌' if overall_failed else '✅'
    commit_link = f'[`{sha}`](https://github.com/{repo}/commit/{sha})'
    run_link = f'[▶️ View Run #{run_number}]({run_url})' if run_number else f'[▶️ View Run]({run_url})'
    pr_link = f' · [PR #{pr_number}]({pr_url})' if pr_url else ''
    title_suffix = f'PR #{pr_number}' if pr_number else f'Build #{run_number}'

    comment = f"""\
{marker}
## {overall_icon} \\[{module}\\] Test Results — {title_suffix}

| Stage | Passed | Failed | Skipped | Total | Pass Rate | Verdict | Report |
|---|---|---|---|---|---|---|---|
{table}

**Branch:** `{branch}` &nbsp;·&nbsp; **Commit:** {commit_link} &nbsp;·&nbsp; {run_link}{pr_link}

*Updated automatically on every push to this PR.*
"""

    out = Path(out_path)
    out.write_text(comment, encoding='utf-8')
    print(f'✅ Wrote {out}')
    print(f'   Unit:  {unit_p}/{unit_t} ({pct(unit_p, unit_t)}%)')
    print(f'   BAT:   {bat_p}/{bat_t} ({pct(bat_p, bat_t)}%)')
    print(f'   Smoke: {smoke_p}/{smoke_t} ({pct(smoke_p, smoke_t)}%)')


if __name__ == '__main__':
    main()
