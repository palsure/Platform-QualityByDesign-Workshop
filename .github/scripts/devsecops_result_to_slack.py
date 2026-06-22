#!/usr/bin/env python3
"""
Build a Slack Block Kit payload for DevSecOps scan results.

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
    module = os.environ.get("MODULE_NAME", "MODULE")
    gate_passed = os.environ.get("GATE_PASSED", "false").lower() == "true"
    verdict = "PASSED" if gate_passed else "FAILED"
    icon = ":large_green_circle:" if gate_passed else ":red_circle:"

    checks = [
        ("Gitleaks", "secrets scan", os.environ.get("GITLEAKS", "skipped")),
        ("npm audit", "web-player dependencies", os.environ.get("NPM_AUDIT", "skipped")),
        ("Trivy", "backend-api filesystem (CRITICAL/HIGH)", os.environ.get("TRIVY", "skipped")),
        ("Conftest", "platform policy (backend.yaml)", os.environ.get("CONFTEST", "skipped")),
        ("SBOM — backend-api", "Syft SPDX", os.environ.get("SBOM_API", "skipped")),
        ("SBOM — web-player", "Syft SPDX", os.environ.get("SBOM_WEB", "skipped")),
    ]

    lines = [
        f"{outcome_icon(outcome)}  *{name}* ({desc}) — *{outcome_label(outcome)}*"
        for name, desc, outcome in checks
    ]
    stats_text = "\n".join(lines)

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
