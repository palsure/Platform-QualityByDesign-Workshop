#!/usr/bin/env python3
"""
Build a Slack Block Kit payload for DevSecOps scan results.

Only lists checks that apply to the active pipeline module (API, WEB,
ANDROID, iOS, or QBD for the full cross-module gate).

Required env vars:
  MODULE_NAME, THREAD_TS, SLACK_CHANNEL_ID (optional)
  GATE_PASSED — true | false
  GITLEAKS, NPM_AUDIT, TRIVY, CONFTEST, SBOM_API, SBOM_WEB — step outcomes

Output: stage-slack-payload.json (same file as stage_result_to_slack.py)
"""
from __future__ import annotations

import json
import os
from pathlib import Path

from devsecops_checks import applicable_checks, gate_passed, normalize_module
from slack_utils import normalize_channel_id


def outcome_icon(outcome: str) -> str:
    return {
        "success": ":white_check_mark:",
        "failure": ":x:",
        "skipped": ":fast_forward:",
        "cancelled": ":fast_forward:",
    }.get(outcome.lower(), ":white_circle:")


def outcome_label(outcome: str) -> str:
    return {
        "success": "PASSED",
        "failure": "FAILED",
        "skipped": "SKIPPED",
        "cancelled": "CANCELLED",
    }.get(outcome.lower(), outcome.upper() or "UNKNOWN")


def main() -> int:
    module = normalize_module(os.environ.get("MODULE_NAME", "MODULE"))
    all_outcomes = {
        check.key: os.environ.get(check.key, "skipped")
        for check in applicable_checks(module)
    }
    passed = os.environ.get("GATE_PASSED", "").lower() == "true"
    if os.environ.get("GATE_PASSED", "") == "":
        passed = gate_passed(module, all_outcomes)

    verdict = "PASSED" if passed else "FAILED"
    icon = ":large_green_circle:" if passed else ":red_circle:"

    lines = [
        f"{outcome_icon(all_outcomes.get(check.key, 'skipped'))}  "
        f"*{check.name}* ({check.description}) — "
        f"*{outcome_label(all_outcomes.get(check.key, 'skipped'))}*"
        for check in applicable_checks(module)
    ]
    stats_text = "\n".join(lines) if lines else "_No module-specific security checks configured._"

    repo = os.environ.get("GITHUB_REPOSITORY", "repo")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    run_number = os.environ.get("GITHUB_RUN_NUMBER", "")
    sha_full = os.environ.get("GITHUB_SHA", "unknown")
    sha = sha_full[:7]
    thread_ts = os.environ.get("THREAD_TS", "")
    duration = os.environ.get("DURATION", "").strip()

    run_url = (
        f"https://github.com/{repo}/actions/runs/{run_id}"
        if run_id else f"https://github.com/{repo}"
    )
    commit_url = f"https://github.com/{repo}/commit/{sha_full}"
    build_label = f"Build #{run_number}" if run_number else f"Run {run_id}"

    header_text = (
        f"[{module}] DevSecOps Defaults — {verdict}  |  Duration: {duration}"
        if duration
        else f"[{module}] DevSecOps Defaults — {verdict}  |  {build_label}"
    )

    footer_text = "    ".join([
        f"<{commit_url}|{sha}>  •  {build_label}",
        f"<{run_url}|:arrow_forward: View Run>",
    ])

    channel_id = normalize_channel_id(os.environ.get("SLACK_CHANNEL_ID", ""))

    payload: dict = {
        "text": header_text,
        "unfurl_links": False,
        "unfurl_media": False,
        "blocks": [
            {
                "type": "header",
                "text": {"type": "plain_text", "text": header_text, "emoji": True},
            },
            {
                "type": "section",
                "text": {"type": "mrkdwn", "text": f"{icon}  *Security checks*\n{stats_text}"},
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
