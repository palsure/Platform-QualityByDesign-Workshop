#!/usr/bin/env python3
"""Parse `swift test` log for 'Executed N tests, with M failures' and emit platform-summary.json."""
from __future__ import annotations

import argparse
import json
import re
import sys


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("log_path")
    args = ap.parse_args()
    try:
        text = open(args.log_path, encoding="utf-8", errors="replace").read()
    except OSError as e:
        print(e, file=sys.stderr)
        return 1

    m = re.search(r"Executed\s+(\d+)\s+tests?,?\s+with\s+(\d+)\s+failures?", text, re.IGNORECASE)
    if not m:
        print("Could not find Executed … tests pattern — treating as single failed check", file=sys.stderr)
        summary = {"platform": "ios", "passed": 0, "failed": 1, "skipped": 0, "total": 1}
    else:
        total = int(m.group(1))
        failures = int(m.group(2))
        passed = max(0, total - failures)
        summary = {"platform": "ios", "passed": passed, "failed": failures, "skipped": 0, "total": total}

    with open("platform-summary.json", "w", encoding="utf-8") as out:
        json.dump(summary, out, indent=2)
    print(json.dumps(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
