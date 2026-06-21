"""Shared Slack helpers for CI scripts and workflows."""

from __future__ import annotations

import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

_CHANNEL_ID_RE = re.compile(r"^[CGD][A-Z0-9]+$", re.IGNORECASE)

_SLACK_ERRORS: dict[str, str] = {
    "channel_not_found": (
        "Channel ID not found in the bot workspace. Re-copy from Slack → channel → About. "
        "If you set both a Variable and an old Secret, delete the Secret or update it to match."
    ),
    "not_in_channel": "Invite the bot: /invite @YourBotName (required for private channels).",
    "invalid_auth": "Regenerate the bot token and update SLACK_BOT_TOKEN.",
    "missing_scope": "Add chat:write, channels:read, and channels:join to the Slack app, then reinstall.",
}


def normalize_channel_id(raw: str) -> str:
    """Trim whitespace, quotes, and accidental # prefix from a channel ID."""
    if not raw:
        return ""
    s = raw.strip().strip('"').strip("'")
    if s.startswith("#"):
        s = s[1:]
    return re.sub(r"\s+", "", s)


def _mask_channel_id(channel: str) -> str:
    if len(channel) <= 6:
        return channel
    return f"{channel[:4]}…{channel[-3:]}"


def slack_api(method: str, token: str, params: dict | None = None, *, post: bool = False) -> dict:
    url = f"https://slack.com/api/{method}"
    headers = {"Authorization": f"Bearer {token}"}
    try:
        if post:
            data = urllib.parse.urlencode(params or {}).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            req = urllib.request.Request(url, data=data, headers=headers, method="POST")
        else:
            query = urllib.parse.urlencode(params or {})
            if query:
                url = f"{url}?{query}"
            req = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as exc:
        _fail(f"Slack HTTP {exc.code} on {method}: {exc.read().decode()[:200]}")


def validate_channel_access(token: str, channel: str) -> dict:
    """Verify token + channel; join public channels when the bot is not a member."""
    channel = normalize_channel_id(channel)
    if not channel:
        _fail("SLACK_CHANNEL_ID is empty after normalization")

    if not _CHANNEL_ID_RE.match(channel):
        print(
            f"::warning::SLACK_CHANNEL_ID '{channel}' does not look like a Slack channel ID "
            "(expected C…, G…, or D…). See docs/SLACK-SETUP.md",
            file=sys.stderr,
        )

    auth = slack_api("auth.test", token)
    if not auth.get("ok"):
        _fail(f"Slack auth.test failed: {auth.get('error', 'unknown')}")

    team = auth.get("team", "unknown workspace")
    bot = auth.get("user", "bot")
    print(f"Slack bot '{bot}' in workspace '{team}'")
    print(f"Target channel ID: {_mask_channel_id(channel)} ({len(channel)} chars)")

    info = slack_api("conversations.info", token, {"channel": channel})
    if info.get("ok"):
        ch = info.get("channel", {})
        print(f"Channel resolved: #{ch.get('name', '?')} (private={ch.get('is_private', False)})")
        return info

    err = str(info.get("error", "unknown"))
    if err == "not_in_channel" or err == "channel_not_found":
        join = slack_api("conversations.join", token, {"channel": channel}, post=True)
        if join.get("ok"):
            print(f"Joined public channel {_mask_channel_id(channel)} before posting")
            return slack_api("conversations.info", token, {"channel": channel})

    hint = _SLACK_ERRORS.get(err, "")
    _fail(
        f"Slack conversations.info failed: {err} for channel {_mask_channel_id(channel)} "
        f"in workspace '{team}'. {hint} See docs/SLACK-SETUP.md"
    )
    return info  # unreachable


def post_message(token: str, payload: dict) -> dict:
    """POST chat.postMessage; exit with a GitHub Actions error annotation on failure."""
    channel = normalize_channel_id(payload.get("channel") or "")
    validate_channel_access(token, channel)

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
