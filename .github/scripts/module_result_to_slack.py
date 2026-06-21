#!/usr/bin/env python3
"""
Generate a Slack Block Kit payload for a single module build result.

Required env vars:
  MODULE_NAME          — display name, e.g. "API", "WEB", "ANDROID", "iOS"
  STAGE1_NAME          — e.g. "Unit Tests"
  STAGE1_RESULT        — success | failure | skipped | cancelled
  STAGE2_NAME          — e.g. "Build"
  STAGE2_RESULT        — success | failure | skipped | cancelled
  STAGE{N}_NAME        — optional additional stages (N = 3, 4, 5, 6 ...)
  STAGE{N}_RESULT      — result for stage N
  GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER, GITHUB_REF_NAME,
  GITHUB_SHA, WORKFLOW_START_EPOCH

Output file: module-slack-payload.json
"""
from __future__ import annotations

import json
import os
import time
from pathlib import Path


def result_icon(result: str) -> str:
    return {
        "success":     "✅",
        "failure":     "❌",
        "skipped":     "⏭",
        "cancelled":   "⏭",
        "in-progress": "🔄",
    }.get(result.lower(), "⏭")


def fmt_duration(seconds: float) -> str:
    seconds = int(seconds)
    if seconds < 60:
        return f"{seconds} sec"
    m, s = divmod(seconds, 60)
    return f"{m} min, {s:02d} sec"


def main() -> int:
    module = os.environ.get("MODULE_NAME", "MODULE")

    repo       = os.environ.get("GITHUB_REPOSITORY", "repo")
    run_id     = os.environ.get("GITHUB_RUN_ID", "")
    run_number = os.environ.get("GITHUB_RUN_NUMBER", "")
    sha_full   = os.environ.get("GITHUB_SHA", "unknown")
    sha        = sha_full[:7]
    branch     = os.environ.get("GITHUB_REF_NAME", "unknown")

    run_url     = (
        f"https://github.com/{repo}/actions/runs/{run_id}"
        if run_id else f"https://github.com/{repo}"
    )
    commit_url  = f"https://github.com/{repo}/commit/{sha_full}"
    build_label = f"Build #{run_number}" if run_number else f"Run {run_id}"

    # Collect all STAGE{N}_NAME / STAGE{N}_RESULT pairs dynamically (up to 12)
    stages: list[tuple[str, str]] = []
    for n in range(1, 13):
        name = os.environ.get(f"STAGE{n}_NAME", "")
        if not name:
            break
        result = os.environ.get(f"STAGE{n}_RESULT", "skipped")
        stages.append((name, result))

    # Fallback: at least show two placeholder rows
    if not stages:
        stages = [("Stage 1", "skipped"), ("Stage 2", "skipped")]

    # Overall result — failure/cancelled beats success; in-progress is non-blocking
    overall = "success"
    for _, r in stages:
        if r.lower() in ("failure", "cancelled"):
            overall = "failure"
            break

    verdict      = "Success" if overall == "success" else "Failed"
    overall_icon = "✅" if overall == "success" else "❌"

    # Duration
    duration_str = ""
    start_epoch = os.environ.get("WORKFLOW_START_EPOCH", "")
    if start_epoch:
        try:
            elapsed = time.time() - float(start_epoch)
            duration_str = fmt_duration(elapsed)
        except ValueError:
            pass

    # Pipeline flow — stages as an arrow chain: ✅ Build → ✅ Unit Tests → ❌ BAT → …
    # Split into rows of 4 so the chain wraps cleanly in Slack
    COLS = 4
    flow_chunks: list[str] = []
    row_items: list[str] = []
    for i, (name, r) in enumerate(stages):
        row_items.append(f"{result_icon(r)}  *{name}*")
        if len(row_items) == COLS or i == len(stages) - 1:
            flow_chunks.append("  →  ".join(row_items))
            row_items = []
    pipeline_flow = "\n".join(flow_chunks)

    footer_parts = []
    if duration_str:
        footer_parts.append(f"*Duration:* {duration_str}")
    footer_parts.append(f"<{run_url}|:arrow_forward: View Run #{run_number or run_id}>")
    footer_text = "    ".join(footer_parts)

    thread_ts  = os.environ.get("THREAD_TS", "")
    channel_id = os.environ.get("SLACK_CHANNEL_ID", "")

    total_duration = os.environ.get("TOTAL_DURATION", "").strip()
    if total_duration:
        header_text = f"{overall_icon} [{module}] Build Completed  |  Total Duration: {total_duration}  |  {verdict}"
    else:
        header_text = f"{overall_icon} [{module}] Build Completed  |  {verdict}"

    payload = {
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
            {"type": "divider"},
            {
                "type": "section",
                "text": {"type": "mrkdwn", "text": pipeline_flow},
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

    out = Path("module-slack-payload.json")
    out.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
