#!/usr/bin/env python3
"""PR summary for Quality-by-Design gate chain on sample apps."""
from __future__ import annotations

import os
from pathlib import Path

MARKER = "<!-- qbd-pipeline -->"


def icon(result: str) -> str:
    if result == "success":
        return "✅"
    if result in ("skipped", "cancelled"):
        return "⏭"
    return "❌"


def main() -> None:
    repo = os.environ.get("GITHUB_REPOSITORY", "org/repo")
    run_id = os.environ.get("GITHUB_RUN_ID", "0")
    run_number = os.environ.get("GITHUB_RUN_NUMBER", "0")
    sha = os.environ.get("GITHUB_SHA", "")[:7]
    pr = os.environ.get("PR_NUMBER", "")

    stages = [
        ("DevSecOps defaults", os.environ.get("STAGE_DEVSECOPS", "skipped")),
        ("Unit tests (backend-api)", os.environ.get("STAGE_UNIT", "skipped")),
        ("Contract tests (backend-api)", os.environ.get("STAGE_CONTRACT", "skipped")),
        ("Integration / BAT (backend-api)", os.environ.get("STAGE_INTEGRATION", "skipped")),
        ("Web unit tests (web-player)", os.environ.get("STAGE_WEB", "skipped")),
        ("Ephemeral env + smoke", os.environ.get("STAGE_SMOKE", "skipped")),
        ("Performance smoke (k6)", os.environ.get("STAGE_PERF", "skipped")),
        ("Promotion gate", os.environ.get("STAGE_PROMOTE", "skipped")),
    ]

    promote = os.environ.get("STAGE_PROMOTE", "failure")
    verdict = "✅ **Eligible to merge**" if promote == "success" else "❌ **Promotion blocked**"

    body = [
        MARKER,
        "## Quality by Design — Pipeline",
        "",
        f"**PR #{pr}** · run [#{run_number}](https://github.com/{repo}/actions/runs/{run_id}) · `{sha}`",
        "",
        verdict,
        "",
        "| Stage | Result |",
        "|-------|--------|",
    ]
    for name, result in stages:
        body.append(f"| {name} | {icon(result)} {result.upper()} |")

    body.extend([
        "",
        "**Sample apps:** `backend-api` · `web-player` · `android-player` · `ios-player`",
        "",
        "### Gate chain",
        "`unit → contract → integration (BAT) → ephemeral env → smoke → perf smoke → promote`",
        "",
        "_DevSecOps defaults run in parallel. Mobile apps use existing per-platform pipelines._",
    ])

    Path("pr-comment.md").write_text("\n".join(body), encoding="utf-8")
    print("Wrote pr-comment.md")


if __name__ == "__main__":
    main()
