#!/usr/bin/env python3
"""Convert Playwright JSON reporter output into platform-summary fragment (web)."""
from __future__ import annotations

import argparse
import json
import sys


def collect_specs(suites, acc=None):
    if acc is None:
        acc = []
    for suite in suites or []:
        for spec in suite.get("specs") or []:
            first_test = (spec.get("tests") or [None])[0]
            first_result = (first_test or {}).get("results", [None])[0] if first_test else None
            ok = spec.get("ok")
            if ok is None and first_result:
                ok = first_result.get("status") == "passed"
            acc.append(bool(ok))
        collect_specs(suite.get("suites"), acc)
    return acc


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("json_path")
    args = ap.parse_args()
    try:
        with open(args.json_path, encoding="utf-8") as f:
            report = json.load(f)
    except OSError as e:
        print(e, file=sys.stderr)
        return 1

    specs = collect_specs(report.get("suites"))
    stats = report.get("stats") or {}
    total = len(specs) or int(stats.get("expected", 0)) + int(stats.get("unexpected", 0)) + int(
        stats.get("skipped", 0)
    )
    passed = sum(1 for s in specs if s) or int(stats.get("expected", 0))
    failed = sum(1 for s in specs if not s) or int(stats.get("unexpected", 0))
    skipped = int(stats.get("skipped", 0))
    if not specs and total == 0:
        total = passed + failed + skipped or 1
    summary = {"platform": "web", "passed": passed, "failed": failed, "skipped": skipped, "total": total}
    with open("playwright-summary.json", "w", encoding="utf-8") as out:
        json.dump(summary, out, indent=2)
    print(json.dumps(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
