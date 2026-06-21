#!/usr/bin/env python3
"""Validate Slack bot token + channel ID before relying on CI notifications."""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

_SCRIPT_DIR = Path(__file__).resolve().parent
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from slack_utils import normalize_channel_id


def slack_api(method: str, token: str, params: dict | None = None) -> dict:
    url = f"https://slack.com/api/{method}"
    if params:
        url = f"{url}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(
        url,
        headers={"Authorization": f"Bearer {token}"},
        method="GET",
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())


def main() -> int:
    token = os.environ.get("SLACK_BOT_TOKEN", "").strip()
    raw_channel = os.environ.get("SLACK_CHANNEL_ID", "")
    channel = normalize_channel_id(raw_channel)

    print("── Slack configuration check ──")
    print(f"SLACK_BOT_TOKEN set:  {'yes' if token else 'NO'}")
    print(f"SLACK_CHANNEL_ID raw:   {len(raw_channel)} chars")
    print(f"SLACK_CHANNEL_ID norm:  {channel or '<empty>'}")

    if not token:
        print("::error::SLACK_BOT_TOKEN is missing")
        return 1

    if not channel:
        print("::error::SLACK_CHANNEL_ID is missing or empty after normalization")
        return 1

    if not channel.startswith(("C", "G", "D")):
        print(
            "::error::Value does not look like a Slack channel ID. "
            "Copy the ID from Slack → channel → About (starts with C). "
            "Do not use #channel-name."
        )
        return 1

    auth = slack_api("auth.test", token)
    print(f"auth.test ok:           {auth.get('ok')}")
    if not auth.get("ok"):
        print(f"::error::auth.test failed: {auth.get('error')}")
        return 1

    print(f"Bot user:               {auth.get('user')}")
    print(f"Team / workspace:       {auth.get('team')} ({auth.get('team_id')})")

    info = slack_api("conversations.info", token, {"channel": channel})
    if info.get("ok"):
        ch = info.get("channel", {})
        print(f"Channel found:          #{ch.get('name')} (id={ch.get('id')})")
        print(f"Channel is private:     {ch.get('is_private', False)}")
        if ch.get("is_archived"):
            print("::warning::Channel is archived — unarchive or pick another channel")
        print("::notice::Configuration looks valid. Re-run your pipeline.")
        return 0

    err = info.get("error", "unknown")
    print(f"conversations.info:     FAILED — {err}")

    if err == "channel_not_found":
        print(
            "::error::channel_not_found — the ID in SLACK_CHANNEL_ID does not exist in "
            f"workspace '{auth.get('team')}'. Common fixes:\n"
            "  • Re-copy channel ID from Slack → open channel → name → About → bottom\n"
            "  • Ensure the Slack app is installed to the SAME workspace as the channel\n"
            "  • For private channels: /invite @YourBot in the channel first\n"
            "  • Do not paste workspace ID, team ID, or #channel-name"
        )
    elif err == "missing_scope":
        print(
            "::error::Add Bot Token Scopes: channels:read (public) and/or groups:read (private), "
            "then reinstall the app to the workspace."
        )
    else:
        print(f"::error::Slack API error: {err}")

    return 1


if __name__ == "__main__":
    raise SystemExit(main())
