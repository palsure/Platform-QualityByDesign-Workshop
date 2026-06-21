#!/usr/bin/env python3
"""
stage_result_to_slack.py

Generates a Slack Block Kit thread-reply payload for a single pipeline stage.
Parses JUnit-compatible XML (Gradle test results, Maven Surefire) for real stats.
Optionally parses a Playwright JSON report for web E2E stats.

Required env vars:
  MODULE_NAME    — display name e.g. "ANDROID", "WEB", "iOS", "API"
  STAGE_NAME     — e.g. "Unit Tests", "E2E Tests", "Automation E2E"

Optional env vars:
  JUNIT_DIR      — directory containing TEST-*.xml or surefire TEST-*.xml files
  PLAYWRIGHT_JSON — path to Playwright JSON report file (web E2E)
  REPORT_URL     — link to the HTML report artifact
  STAGE_RESULT   — success | failure | skipped (fallback when no XML found)
  THREAD_TS      — Slack thread_ts for the reply (set to post as thread reply)
  GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER, GITHUB_SHA

Output: stage-slack-payload.json
"""
from __future__ import annotations

import glob
import json
import math
import os
import xml.etree.ElementTree as ET
from pathlib import Path

from slack_utils import normalize_channel_id

PASS_THRESHOLD = 80.0


def parse_junit(junit_dir: str) -> tuple[int, int, int, int]:
    """Return (passed, failed, skipped, total) from JUnit XML files.

    Handles both common root shapes:
      • root = <testsuite>            (Gradle TEST-*.xml, Maven Surefire)
      • root = <testsuites>           (Swift `swift test --xunit-output`,
                                       Jest, Vitest, Playwright, …)

    For the latter we sum across every direct <testsuite> child, since the
    aggregate counts are typically not present on the <testsuites> root.
    """
    total = passed = failed = skipped = 0
    patterns = [
        f"{junit_dir}/**/TEST-*.xml",
        f"{junit_dir}/**/*-junit.xml",
        f"{junit_dir}/**/vitest-junit.xml",
    ]
    seen: set[str] = set()
    for pattern in patterns:
        for f in glob.glob(pattern, recursive=True):
            if f in seen:
                continue
            seen.add(f)
            try:
                root = ET.parse(f).getroot()
                suites = root.findall("testsuite") if root.tag == "testsuites" else [root]
                for s in suites:
                    t  = int(s.attrib.get("tests",    0))
                    fa = int(s.attrib.get("failures", 0)) + int(s.attrib.get("errors", 0))
                    sk = int(s.attrib.get("skipped",  0))
                    total   += t
                    failed  += fa
                    skipped += sk
                    passed  += max(0, t - fa - sk)
            except Exception:
                pass
    return passed, failed, skipped, total


def parse_playwright(json_path: str) -> tuple[int, int, int, int]:
    """Return (passed, failed, skipped, total) from Playwright JSON report.

    Playwright's built-in JSON reporter wraps counts under a "stats" object:
      stats.expected   → tests that passed as expected
      stats.unexpected → tests that failed unexpectedly
      stats.flaky      → tests that passed on retry (count as failures for gate)
      stats.skipped    → tests that were skipped
    Falls back to a flat { passed, failed, skipped, total } shape if needed.
    """
    try:
        data = json.loads(Path(json_path).read_text(encoding="utf-8"))
        stats = data.get("stats", {})
        if stats:
            passed  = stats.get("expected",   0)
            failed  = stats.get("unexpected", 0) + stats.get("flaky", 0)
            skipped = stats.get("skipped",    0)
            total   = passed + failed + skipped
            return passed, failed, skipped, total
        # Fallback: flat format
        passed  = data.get("passed",  0)
        failed  = data.get("failed",  0)
        skipped = data.get("skipped", 0)
        total   = data.get("total",   passed + failed + skipped)
        return passed, failed, skipped, total
    except Exception:
        return 0, 0, 0, 0


def status_icon(passed: int, total: int) -> str:
    if total == 0:
        return ":white_circle:"
    rate = 100.0 * passed / total
    if rate >= PASS_THRESHOLD:
        return ":large_green_circle:"
    if rate >= 50.0:
        return ":large_yellow_circle:"
    return ":red_circle:"


def pct(passed: int, total: int) -> str:
    if total == 0:
        return "0%"
    return f"{math.floor(100.0 * passed / total)}%"


def fmt_duration(seconds: int) -> str:
    """Format integer seconds as 'Xm Ys' or 'Xs'."""
    if seconds < 60:
        return f"{seconds}s"
    return f"{seconds // 60}m {seconds % 60:02d}s"


def main() -> int:
    module       = os.environ.get("MODULE_NAME", "MODULE")
    stage        = os.environ.get("STAGE_NAME", "Stage")
    junit_dir    = os.environ.get("JUNIT_DIR", "")
    pw_json      = os.environ.get("PLAYWRIGHT_JSON", "")
    report_url   = os.environ.get("REPORT_URL", "")
    link_label   = os.environ.get("LINK_LABEL", ":bar_chart: View Report")
    stage_result = os.environ.get("STAGE_RESULT", "unknown")
    thread_ts    = os.environ.get("THREAD_TS", "")
    duration_raw = os.environ.get("DURATION", "")

    repo       = os.environ.get("GITHUB_REPOSITORY", "repo")
    run_id     = os.environ.get("GITHUB_RUN_ID", "")
    run_number = os.environ.get("GITHUB_RUN_NUMBER", "")
    sha_full   = os.environ.get("GITHUB_SHA", "unknown")
    sha        = sha_full[:7]

    run_url    = (
        f"https://github.com/{repo}/actions/runs/{run_id}"
        if run_id else f"https://github.com/{repo}"
    )
    commit_url  = f"https://github.com/{repo}/commit/{sha_full}"
    build_label = f"Build #{run_number}" if run_number else f"Run {run_id}"

    # Resolve display duration: prefer explicit DURATION env, else compute
    # from STAGE_START_EPOCH if set, else fall back to build label.
    duration_str = ""
    if duration_raw:
        duration_str = duration_raw
    else:
        start_epoch = os.environ.get("STAGE_START_EPOCH", "")
        if start_epoch:
            try:
                import time
                elapsed = int(time.time()) - int(start_epoch)
                duration_str = fmt_duration(max(0, elapsed))
            except (ValueError, OSError):
                pass

    # ── Parse test results ────────────────────────────────────────────────────
    passed = failed = skipped = total = 0
    has_stats = False

    if pw_json and Path(pw_json).exists():
        passed, failed, skipped, total = parse_playwright(pw_json)
        has_stats = total > 0

    if not has_stats and junit_dir:
        passed, failed, skipped, total = parse_junit(junit_dir)
        has_stats = total > 0

    # ── Determine verdict ─────────────────────────────────────────────────────
    if has_stats:
        verdict = "PASSED" if failed == 0 else "FAILED"
        icon    = status_icon(passed, total)
    else:
        verdict = {
            "success": "PASSED", "failure": "FAILED",
            "skipped": "SKIPPED", "cancelled": "CANCELLED",
        }.get(stage_result.lower(), "UNKNOWN")
        icon = {
            "PASSED": ":large_green_circle:", "FAILED": ":red_circle:",
            "SKIPPED": ":white_circle:",       "CANCELLED": ":white_circle:",
        }.get(verdict, ":white_circle:")

    # ── Stats text ────────────────────────────────────────────────────────────
    if has_stats:
        # Inline pass rate next to the passed count: "Passed: 58 (100%)"
        passed_str = f"{passed} ({pct(passed, total)})"
        stats_text = (
            f"{icon}  "
            f"*Passed:* {passed_str}  |  *Failed:* {failed}  |  "
            f"*Skipped:* {skipped}  |  *Total:* {total}"
        )
    else:
        stats_text = f"{icon}  *Result:* {verdict}"

    # ── Footer ────────────────────────────────────────────────────────────────
    footer_parts = [f"<{commit_url}|{sha}>  •  {build_label}"]
    if report_url:
        footer_parts.append(f"<{report_url}|{link_label}>")
    footer_parts.append(f"<{run_url}|:arrow_forward: View Run>")
    footer_text = "    ".join(footer_parts)

    # ── Payload ───────────────────────────────────────────────────────────────
    channel_id = normalize_channel_id(os.environ.get("SLACK_CHANNEL_ID", ""))

    # No tick/cross prefix — the colored circle in stats_text is sufficient signal
    header_text = (
        f"[{module}] {stage} — {verdict}  |  Duration: {duration_str}"
        if duration_str
        else f"[{module}] {stage} — {verdict}  |  {build_label}"
    )

    payload: dict = {
        "text": header_text,
        "unfurl_links": False,
        "unfurl_media": False,
        "blocks": [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": header_text,
                    "emoji": True,
                },
            },
            {
                "type": "section",
                "text": {"type": "mrkdwn", "text": stats_text},
            },
            {
                "type": "context",
                "elements": [{"type": "mrkdwn", "text": footer_text}],
            },
        ],
    }

    if channel_id:
        payload["channel"] = channel_id
    if thread_ts:
        payload["thread_ts"] = thread_ts

    out = Path("stage-slack-payload.json")
    out.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
