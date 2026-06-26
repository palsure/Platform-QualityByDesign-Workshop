#!/usr/bin/env python3
"""
pr_summary_web.py

Reads test-result artifacts for the three web pipeline stages and writes
pr-comment-web.md — a Markdown PR comment that shows a results table with
Allure report links.

Artifacts expected (paths relative to repo root):
  artifacts/unit/vitest-junit.xml     — Vitest JUnit XML (web-unit-junit)
  artifacts/bat/playwright-results-bat.json   — Playwright JSON (web-bat-playwright-json)
  artifacts/smoke/playwright-results-smoke.json — Playwright JSON (web-smoke-playwright-json)

Required env vars:
  GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER, GITHUB_SHA
  PR_NUMBER, BRANCH_NAME
  STAGE_UNIT_RESULT, STAGE_BAT_RESULT, STAGE_SMOKE_RESULT  — success|failure|skipped
  RUN_NUMBER, BASE_PAGES_URL
"""
from __future__ import annotations

import glob
import json
import math
import os
import xml.etree.ElementTree as ET
from pathlib import Path

from pr_summary_common import format_count_row, load_test_row, pct, security_row, verdict


# ── Parsers ──────────────────────────────────────────────────────────────────

def parse_junit(xml_path: str) -> tuple[int, int, int, int]:
    """(passed, failed, skipped, total) from a JUnit XML file."""
    try:
        root = ET.parse(xml_path).getroot()
        # Handle both <testsuite> root and <testsuites> wrapping <testsuite>
        suites = [root] if root.tag == 'testsuite' else root.findall('testsuite')
        passed = failed = skipped = total = 0
        for suite in suites:
            t  = int(suite.attrib.get('tests',    0))
            fa = int(suite.attrib.get('failures', 0)) + int(suite.attrib.get('errors', 0))
            s  = int(suite.attrib.get('skipped',  0))
            total   += t
            failed  += fa
            skipped += s
            passed  += max(0, t - fa - s)
        return passed, failed, skipped, total
    except Exception as e:
        print(f"⚠️  JUnit parse error ({xml_path}): {e}")
        return 0, 0, 0, 0


def parse_playwright_json(json_path: str) -> tuple[int, int, int, int]:
    """(passed, failed, skipped, total) from a Playwright JSON report."""
    try:
        data = json.loads(Path(json_path).read_text(encoding='utf-8'))
        stats = data.get('stats', {})
        if stats:
            passed  = stats.get('expected',   0)
            failed  = stats.get('unexpected', 0) + stats.get('flaky', 0)
            skipped = stats.get('skipped',    0)
            return passed, failed, skipped, passed + failed + skipped
        # Fallback flat shape
        passed  = data.get('passed',  0)
        failed  = data.get('failed',  0)
        skipped = data.get('skipped', 0)
        total   = data.get('total',   passed + failed + skipped)
        return passed, failed, skipped, total
    except Exception as e:
        print(f"⚠️  Playwright JSON parse error ({json_path}): {e}")
        return 0, 0, 0, 0


# ── Helpers ───────────────────────────────────────────────────────────────────

def verdict_text(passed: int, failed: int, total: int, job_result: str) -> str:
    _, label = verdict(passed, failed, total, job_result)
    return label


# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    repo       = os.environ.get('GITHUB_REPOSITORY', 'owner/repo')
    run_id     = os.environ.get('GITHUB_RUN_ID',     '')
    run_number = os.environ.get('GITHUB_RUN_NUMBER', '')
    sha        = os.environ.get('GITHUB_SHA', 'unknown')[:7]
    pr_number  = os.environ.get('PR_NUMBER',  '')
    branch     = os.environ.get('BRANCH_NAME', 'unknown')
    pages_base = os.environ.get('BASE_PAGES_URL', '')  # e.g. https://user.github.io/repo/allure/27/web

    module     = os.environ.get('MODULE_NAME', 'WEB')

    unit_result  = os.environ.get('STAGE_UNIT_RESULT',  'skipped').lower()
    bat_result   = os.environ.get('STAGE_BAT_RESULT',   'skipped').lower()
    smoke_result = os.environ.get('STAGE_SMOKE_RESULT', 'skipped').lower()

    run_url = f"https://github.com/{repo}/actions/runs/{run_id}" if run_id else f"https://github.com/{repo}"
    pr_url  = f"https://github.com/{repo}/pull/{pr_number}" if pr_number else ""

    # ── Parse results ─────────────────────────────────────────────────────────
    unit_junit_candidates = glob.glob('artifacts/unit/**/vitest-junit.xml', recursive=True)
    unit_junit_candidates += glob.glob('artifacts/unit/vitest-junit.xml')
    unit_junit = unit_junit_candidates[0] if unit_junit_candidates else 'artifacts/unit/vitest-junit.xml'
    bat_json_candidates = glob.glob('artifacts/bat/**/playwright-results-bat.json', recursive=True)
    bat_json_candidates += glob.glob('artifacts/bat/playwright-results-bat.json')
    bat_json = bat_json_candidates[0] if bat_json_candidates else 'artifacts/bat/playwright-results-bat.json'
    smoke_json_candidates = glob.glob('artifacts/smoke/**/playwright-results-smoke.json', recursive=True)
    smoke_json_candidates += glob.glob('artifacts/smoke/playwright-results-smoke.json')
    smoke_json = smoke_json_candidates[0] if smoke_json_candidates else 'artifacts/smoke/playwright-results-smoke.json'

    unit_p, unit_f, unit_s, unit_t   = (
        parse_junit(unit_junit) if Path(unit_junit).exists()
        else (0, 0, 0, 0)
    )
    bat_p, bat_f, bat_s, bat_t       = (
        parse_playwright_json(bat_json) if Path(bat_json).exists()
        else (0, 0, 0, 0)
    )
    smoke_p, smoke_f, smoke_s, smoke_t = (
        parse_playwright_json(smoke_json) if Path(smoke_json).exists()
        else (0, 0, 0, 0)
    )

    # ── Verdict per stage ─────────────────────────────────────────────────────
    stages = [
        ('Unit Tests (Vitest)', unit_p, unit_f, unit_s, unit_t, unit_result,  'unit'),
        ('BAT Tests',           bat_p,  bat_f,  bat_s,  bat_t,  bat_result,   'bat'),
        ('Smoke Tests',         smoke_p, smoke_f, smoke_s, smoke_t, smoke_result, 'smoke'),
    ]

    # ── Overall verdict ───────────────────────────────────────────────────────
    overall_failed = False
    rows = []
    sec = security_row(module)
    if sec:
        rows.append(sec)
        if '**FAILED**' in sec:
            overall_failed = True

    for name, p, f, s, t, job_result, stage_key in stages:
        label = verdict_text(p, f, t, job_result)
        if label == 'FAILED':
            overall_failed = True
        report_link = f'[📊 Allure]({pages_base}/{stage_key})' if pages_base else '–'
        rows.append(format_count_row(name, p, f, s, t, job_result, report_link))

    load = load_test_row()
    if load:
        rows.append(load)
        if '**FAILED**' in load:
            overall_failed = True

    overall_icon = '❌' if overall_failed else '✅'

    # ── Build table rows ──────────────────────────────────────────────────────
    table = '\n'.join(rows)

    # ── Footer ────────────────────────────────────────────────────────────────
    commit_url  = f'https://github.com/{repo}/commit/{sha}'
    commit_link = f'[`{sha}`]({commit_url})'
    run_link    = f'[▶️ View Run #{run_number}]({run_url})' if run_number else f'[▶️ View Run]({run_url})'
    pr_link     = f' · [PR #{pr_number}]({pr_url})' if pr_url else ''

    title_suffix = f'PR #{pr_number}' if pr_number else f'Build #{run_number}'

    comment = f"""\
<!-- qoe-web-results -->
## {overall_icon} \\[WEB\\] Test Results — {title_suffix}

| Stage | Passed | Failed | Skipped | Total | Pass Rate | Verdict | Report |
|---|---|---|---|---|---|---|---|
{table}

**Branch:** `{branch}` &nbsp;·&nbsp; **Commit:** {commit_link} &nbsp;·&nbsp; {run_link}{pr_link}

*Updated automatically on every push to this PR.*
"""

    out = Path('pr-comment-web.md')
    out.write_text(comment, encoding='utf-8')
    print(f'✅ Wrote {out}')
    print(f'   Unit:  {unit_p}/{unit_t} ({pct(unit_p, unit_t)}%)')
    print(f'   BAT:   {bat_p}/{bat_t} ({pct(bat_p, bat_t)}%)')
    print(f'   Smoke: {smoke_p}/{smoke_t} ({pct(smoke_p, smoke_t)}%)')


if __name__ == '__main__':
    main()
