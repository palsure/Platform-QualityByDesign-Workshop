#!/usr/bin/env python3
"""Post a Slack chat.postMessage payload from stdin or a file."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

_SCRIPT_DIR = Path(__file__).resolve().parent
if str(_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPT_DIR))

from slack_utils import normalize_channel_id, post_message


def main() -> None:
    parser = argparse.ArgumentParser(description="Post a Slack message from JSON payload")
    parser.add_argument(
        "--payload-file",
        default="-",
        help="JSON payload file (- for stdin)",
    )
    parser.add_argument(
        "--output-ts",
        action="store_true",
        help="Write thread_ts= to GITHUB_OUTPUT",
    )
    args = parser.parse_args()

    token = os.environ.get("SLACK_BOT_TOKEN", "").strip()
    if not token:
        print("::error::SLACK_BOT_TOKEN is not set", file=sys.stderr)
        raise SystemExit(1)

    if args.payload_file == "-":
        payload = json.load(sys.stdin)
    else:
        payload = json.loads(Path(args.payload_file).read_text(encoding="utf-8"))

    env_channel = normalize_channel_id(os.environ.get("SLACK_CHANNEL_ID", ""))
    if env_channel:
        payload["channel"] = env_channel
    elif not payload.get("channel"):
        print("::error::SLACK_CHANNEL_ID is not set", file=sys.stderr)
        raise SystemExit(1)

    body = post_message(token, payload)

    if args.output_ts:
        ts = body.get("ts", "")
        github_output = os.environ.get("GITHUB_OUTPUT")
        if github_output:
            with open(github_output, "a", encoding="utf-8") as handle:
                handle.write(f"thread_ts={ts}\n")
        if ts:
            print(ts)


if __name__ == "__main__":
    main()
