#!/usr/bin/env python3
"""
Merge platform-summary.json files and enforce QUALITY_GATE_THRESHOLD (percent).
Writes GitHub Actions outputs: approved, overall_pass_rate, gate_threshold
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path


def main() -> int:
    root = Path(os.environ.get("SUMMARY_ROOT", "."))
    threshold = float(os.environ.get("QUALITY_GATE_THRESHOLD", "80"))

    summaries: list[dict] = []
    for path in sorted(root.rglob("platform-summary.json")):
        try:
            with open(path, encoding="utf-8") as f:
                summaries.append(json.load(f))
        except (OSError, json.JSONDecodeError) as e:
            print(f"skip {path}: {e}", file=sys.stderr)

    if not summaries:
        print("No platform-summary.json files found", file=sys.stderr)
        approved = False
        rate = 0.0
    else:
        passed = sum(int(s.get("passed", 0)) for s in summaries)
        total = sum(int(s.get("total", 0)) for s in summaries)
        rate = (100.0 * passed / total) if total > 0 else 0.0
        approved = rate >= threshold

    print(f"Overall pass rate: {rate:.2f}% (threshold {threshold}%)")
    print(f"Approved for release: {approved}")

    gh = os.environ.get("GITHUB_OUTPUT")
    if gh:
        with open(gh, "a", encoding="utf-8") as out:
            out.write(f"approved={'true' if approved else 'false'}\n")
            out.write(f"overall_pass_rate={rate:.4f}\n")
            out.write(f"gate_threshold={threshold}\n")

    manifest = {
        "approved": approved,
        "overallPassRatePercent": rate,
        "gateThresholdPercent": threshold,
        "platforms": summaries,
    }
    Path("acceptance-gate-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    # Always exit 0 so the workflow step can write GITHUB_OUTPUT; enforce with a follow-up `jq -e` step.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
