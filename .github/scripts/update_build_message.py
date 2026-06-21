#!/usr/bin/env python3
"""
update_build_message.py

Updates the initial "build started" Slack message with the final build outcome.
Uses chat.update — works because the bot is the author of the message.

Required env vars:
  SLACK_BOT_TOKEN          — Slack bot OAuth token
  SLACK_CHANNEL_ID         — Target channel ID
  THREAD_TS                — ts of the initial message to update
  BUILD_VERDICT            — "success" | "failure" | "skipped"
                             "skipped" is used when no module changes were
                             detected (workflow_dispatch with no diff) and
                             the gate / release jobs were intentionally
                             skipped — surfaces a neutral header rather
                             than a misleading red "Failed".
  MODULE_NAME              — e.g. "API" or "WEB"  (shown in header as [MODULE_NAME])
  GITHUB_RUN_NUMBER, GITHUB_REPOSITORY, GITHUB_SHA,
  GITHUB_HEAD_REF,         — real source branch on PR events (may be empty)
  GITHUB_REF_NAME,         — fallback branch / tag name
  GITHUB_ACTOR, GITHUB_EVENT_NAME, GITHUB_PR_NUMBER,
  GITHUB_RUN_ID, ENV_LABEL
"""
from __future__ import annotations

import json
import os
import urllib.request


def main() -> int:
    token      = os.environ.get("SLACK_BOT_TOKEN", "")
    channel    = os.environ.get("SLACK_CHANNEL_ID", "")
    ts         = os.environ.get("THREAD_TS", "")
    verdict    = os.environ.get("BUILD_VERDICT", "failure")
    module     = os.environ.get("MODULE_NAME", "API")
    run_number = os.environ.get("GITHUB_RUN_NUMBER", "")
    repo       = os.environ.get("GITHUB_REPOSITORY", "")
    sha_full   = os.environ.get("GITHUB_SHA", "unknown")
    head_ref   = os.environ.get("GITHUB_HEAD_REF", "")
    ref_name   = os.environ.get("GITHUB_REF_NAME", "unknown")
    branch     = head_ref or ref_name
    actor      = os.environ.get("GITHUB_ACTOR", "")
    event_name = os.environ.get("GITHUB_EVENT_NAME", "")
    pr_number  = os.environ.get("GITHUB_PR_NUMBER", "")
    env_label  = os.environ.get("ENV_LABEL", "STAGE")
    run_id     = os.environ.get("GITHUB_RUN_ID", "")

    if not token or not channel or not ts:
        print("Slack not configured — skipping chat.update")
        return 0

    sha        = sha_full[:7]
    commit_url = f"https://github.com/{repo}/commit/{sha_full}"
    run_url    = f"https://github.com/{repo}/actions/runs/{run_id}"

    if pr_number:
        pr_url      = f"https://github.com/{repo}/pull/{pr_number}"
        triggered   = f"{actor} (<{pr_url}|PR #{pr_number}>)"
    else:
        triggered   = f"{actor} ({event_name})"

    if verdict == "success":
        outcome, icon = "Success",                        "\u2705"  # ✅
    elif verdict == "skipped":
        outcome, icon = "Skipped — no changes detected", "\u26AA"  # ⚪
    else:
        outcome, icon = "Failed",                         "\u274c"  # ❌
    header  = f"[{module}] Stream-QoE-App  |  Build #{run_number}  \u2014  {icon} {outcome}"

    payload = json.dumps({
        "channel": channel,
        "ts": ts,
        "text": header,
        "unfurl_links": False,
        "unfurl_media": False,
        "blocks": [
            {"type": "header", "text": {"type": "plain_text", "text": header, "emoji": True}},
            {"type": "section", "text": {"type": "mrkdwn",
                "text": f"*Branch:* `{branch}`  |  *Environment:* {env_label}  |  *Commit:* <{commit_url}|{sha}>  |  *Triggered by:* {triggered}"}},
            {"type": "divider"},
            {"type": "context", "elements": [
                {"type": "mrkdwn", "text": "Results are in this thread."},
            ]},
            {"type": "actions", "elements": [
                {"type": "button",
                 "text": {"type": "plain_text", "text": "View Run", "emoji": True},
                 "url": run_url, "style": "primary"},
            ]},
        ],
    }).encode()

    req = urllib.request.Request(
        "https://slack.com/api/chat.update",
        data=payload,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req) as resp:
        result = json.loads(resp.read())
        ok = result.get("ok", False)
        print(f"chat.update: ok={ok} error='{result.get('error', '')}'")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
