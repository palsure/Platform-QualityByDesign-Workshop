#!/usr/bin/env python3
"""Validate Slack bot token + channel ID before relying on CI notifications."""

from __future__ import annotations

import os
import sys
from pathlib import Path

_SCRIPT_DIR = Path(__file__).resolve().parent
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from slack_utils import normalize_channel_id, validate_channel_access


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

    try:
        validate_channel_access(token, channel)
    except SystemExit as exc:
        return int(exc.code) if exc.code else 1

    print("::notice::Configuration looks valid. Re-run your pipeline.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
