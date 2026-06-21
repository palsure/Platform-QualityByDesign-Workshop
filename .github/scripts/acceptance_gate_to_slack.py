#!/usr/bin/env python3
"""
Build a Slack Block Kit payload from acceptance-gate-manifest.json.

Table format:
  Build Acceptance Results — 🔴 BLOCKED  | Build #9
  Overall: 70.67% | Gate: 80% | Branch: main | Commit: b6ef14a
  ────────────────────────────────────────────────────────────────
  Passed(%)    Failed  Skipped  Total  Status  Reports   Platform
  ────────────────────────────────────────────────────────────────
  39 (67%)     19      0        58     🔴      Report    api
  2/3 (67%)    0       0        3      🟡      Report    web
  ...
  ────────────────────────────────────────────────────────────────
  Duration: 6 min, 17 sec    [View Run]
"""
from __future__ import annotations

import json
import math
import os
import sys
import time
from pathlib import Path


PASS_THRESHOLD = float(os.environ.get("QUALITY_GATE_THRESHOLD", "80"))


def status_icon(passed: int, total: int) -> str:
    if total == 0:
        return ":white_circle:"
    rate = 100.0 * passed / total
    if rate >= PASS_THRESHOLD:
        return ":large_green_circle:"
    return ":red_circle:"


def pct(passed: int, total: int) -> str:
    if total == 0:
        return "0%"
    return f"{math.floor(100.0 * passed / total)}%"


def fmt_duration(seconds: float) -> str:
    seconds = int(seconds)
    if seconds < 60:
        return f"{seconds} sec"
    m, s = divmod(seconds, 60)
    return f"{m} min, {s:02d} sec"


def main() -> int:
    manifest_path = Path(sys.argv[1] if len(sys.argv) > 1 else "acceptance-gate-manifest.json")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as e:
        print(f"Failed to read {manifest_path}: {e}", file=sys.stderr)
        return 1

    approved   = bool(manifest.get("approved", False))
    rate       = float(manifest.get("overallPassRatePercent", 0.0))
    threshold  = float(manifest.get("gateThresholdPercent", PASS_THRESHOLD))
    platforms  = manifest.get("platforms", [])

    repo       = os.environ.get("GITHUB_REPOSITORY", "repo")
    run_id     = os.environ.get("GITHUB_RUN_ID", "")
    run_number = os.environ.get("GITHUB_RUN_NUMBER", "")
    sha_full   = os.environ.get("GITHUB_SHA", "") or "unknown"
    sha        = sha_full[:7]
    branch     = os.environ.get("GITHUB_REF_NAME", "unknown")
    run_url    = (
        f"https://github.com/{repo}/actions/runs/{run_id}"
        if run_id else f"https://github.com/{repo}"
    )
    commit_url = f"https://github.com/{repo}/commit/{sha_full}"

    # ── Duration ──────────────────────────────────────────────────────────────
    duration_str = ""
    start_epoch = os.environ.get("WORKFLOW_START_EPOCH", "")
    if start_epoch:
        try:
            elapsed = time.time() - float(start_epoch)
            duration_str = fmt_duration(elapsed)
        except ValueError:
            pass

    # ── Table ─────────────────────────────────────────────────────────────────
    col = {"pct": 13, "fail": 8, "skip": 9, "tot": 7, "st": 8, "rep": 10, "plat": 12}
    sep = "─" * 69

    header = (
        f"{'Passed(%)'.ljust(col['pct'])}"
        f"{'Failed'.ljust(col['fail'])}"
        f"{'Skipped'.ljust(col['skip'])}"
        f"{'Total'.ljust(col['tot'])}"
        f"{'Status'.ljust(col['st'])}"
        f"{'Reports'.ljust(col['rep'])}"
        f"Platform"
    )

    rows: list[str] = []
    for p in platforms:
        ps   = int(p.get("passed",   0))
        pf   = int(p.get("failed",   0))
        pk   = int(p.get("skipped",  0))
        pt   = int(p.get("total",    0))
        plat = p.get("platform", "unknown")
        icon = status_icon(ps, pt)
        passed_col = f"{ps} ({pct(ps, pt)})"
        rows.append(
            f"{passed_col.ljust(col['pct'])}"
            f"{str(pf).ljust(col['fail'])}"
            f"{str(pk).ljust(col['skip'])}"
            f"{str(pt).ljust(col['tot'])}"
            f"{icon}       "
            f"{'Report'.ljust(col['rep'])}"
            f"{plat}"
        )

    if not rows:
        rows = ["No platform summaries were found."]

    table_text = "\n".join([sep, header, sep] + rows + [sep])

    # ── Footer ────────────────────────────────────────────────────────────────
    footer_parts = []
    if duration_str:
        footer_parts.append(f"*Duration:* {duration_str}")
    footer_parts.append(f"<{run_url}|:arrow_forward: View Run #{run_number or run_id}>")
    footer_text = "    ".join(footer_parts)

    # ── Verdict ───────────────────────────────────────────────────────────────
    verdict      = "RELEASED" if approved else "BLOCKED"
    overall_icon = ":large_green_circle:" if approved else ":red_circle:"
    build_label  = f"Build #{run_number}" if run_number else f"Run {run_id}"

    payload = {
        "unfurl_links": False,
        "unfurl_media": False,
        "blocks": [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": f"Build Acceptance Results — {verdict}  |  {build_label}",
                    "emoji": True,
                },
            },
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": (
                        f"{overall_icon}  "
                        f"*Overall:* {rate:.1f}%  |  *Gate:* {threshold:.0f}%  |  "
                        f"*Branch:* `{branch}`  |  *Commit:* <{commit_url}|{sha}>"
                    ),
                },
            },
            {"type": "divider"},
            {
                "type": "section",
                "text": {
                    "type": "mrkdwn",
                    "text": f"```\n{table_text}\n```",
                },
            },
            {
                "type": "context",
                "elements": [
                    {"type": "mrkdwn", "text": footer_text},
                ],
            },
        ],
    }

    out = Path("acceptance-slack-payload.json")
    out.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
