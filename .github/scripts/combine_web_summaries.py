#!/usr/bin/env python3
"""Merge vitest + Playwright summaries into one platform-summary.json for platform 'web'."""
from __future__ import annotations

import json
import sys
from pathlib import Path


def load(path: str) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: combine_web_summaries.py <vitest-summary.json> <playwright-summary.json>", file=sys.stderr)
        return 2
    a = load(sys.argv[1])
    b = load(sys.argv[2])
    summary = {
        "platform": "web",
        "passed": int(a.get("passed", 0)) + int(b.get("passed", 0)),
        "failed": int(a.get("failed", 0)) + int(b.get("failed", 0)),
        "skipped": int(a.get("skipped", 0)) + int(b.get("skipped", 0)),
        "total": int(a.get("total", 0)) + int(b.get("total", 0)),
    }
    Path("platform-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
