"""Shared Slack helpers for CI scripts and workflows."""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.request

_CHANNEL_ID_RE = re.compile(r"^[CGD][A-Z0-9]+$", re.IGNORECASE)

_SLACK_ERRORS: dict[str, str] = {
    "channel_not_found": (
        "Use the channel ID (starts with C), not #channel-name. "
        "Slack → channel name → About → copy ID. Invite the bot to private channels."
    ),
    "not_in_channel": "Invite the bot to the channel: /invite @YourBotName",
    "invalid_auth": "Regenerate the bot token and update SLACK_BOT_TOKEN.",
    "missing_scope": "Add chat:write (and channels:read for private channels) to the Slack app.",
}


def normalize_channel_id(raw: str) -> str:
    """Trim whitespace, quotes, and accidental # prefix from a channel ID."""
    if not raw:
        return ""
    s = raw.strip().strip('"').strip("'")
    if s.startswith("#"):
        s = s[1:]
    return re.sub(r"\s+", "", s)


def post_message(token: str, payload: dict) -> dict:
    """POST chat.postMessage; exit with a GitHub Actions error annotation on failure."""
    channel = normalize_channel_id(payload.get("channel") or "")
    if not channel:
        _fail("SLACK_CHANNEL_ID is empty after normalization")

    if not _CHANNEL_ID_RE.match(channel):
        print(
            f"::warning::SLACK_CHANNEL_ID '{channel}' does not look like a Slack channel ID "
            "(expected C…, G…, or D…). See docs/SLACK-SETUP.md",
            file=sys.stderr,
        )

    payload = {**payload, "channel": channel}
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "https://slack.com/api/chat.postMessage",
        data=data,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        _fail(f"Slack HTTP {exc.code}: {exc.read().decode()[:200]}")

    print(f"Slack response: {json.dumps(body)}")

    if not body.get("ok"):
        err = str(body.get("error", "unknown"))
        hint = _SLACK_ERRORS.get(err, "")
        _fail(f"Slack API error: {err}. {hint} See docs/SLACK-SETUP.md")

    return body


def _fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(1)
