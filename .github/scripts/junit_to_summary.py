#!/usr/bin/env python3
"""Aggregate JUnit XML files into one platform-summary.json for the acceptance gate."""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET


def parse_testsuite(elem: ET.Element) -> tuple[int, int, int, int]:
    """Return (tests, failures, errors, skipped) from a <testsuite> element."""
    tests = int(elem.attrib.get("tests", 0))
    failures = int(elem.attrib.get("failures", 0))
    errors = int(elem.attrib.get("errors", 0))
    skipped = int(elem.attrib.get("skipped", 0))
    return tests, failures, errors, skipped


def accumulate_file(path: str, acc: list[tuple[int, int, int, int]]) -> None:
    tree = ET.parse(path)
    root = tree.getroot()
    if root.tag == "testsuite":
        acc.append(parse_testsuite(root))
        return
    if root.tag == "testsuites":
        for child in root:
            if child.tag == "testsuite":
                acc.append(parse_testsuite(child))
        return
    # Some tools nest further
    for child in root.iter("testsuite"):
        acc.append(parse_testsuite(child))


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("platform", help="api | web | android | ios | automation")
    p.add_argument("glob_dir", help="Directory containing TEST-*.xml or subdirs")
    args = p.parse_args()

    patterns = [
        os.path.join(args.glob_dir, "**", "TEST-*.xml"),
        os.path.join(args.glob_dir, "**", "vitest-junit.xml"),
    ]
    files = sorted({p for pat in patterns for p in glob.glob(pat, recursive=True)})
    if not files:
        print(f"No JUnit files under {args.glob_dir}", file=sys.stderr)
        return 1

    rows: list[tuple[int, int, int, int]] = []
    for f in files:
        try:
            accumulate_file(f, rows)
        except ET.ParseError as e:
            print(f"Skip bad XML {f}: {e}", file=sys.stderr)

    if not rows:
        print("No parseable testsuite entries", file=sys.stderr)
        return 1

    tests = sum(r[0] for r in rows)
    failures = sum(r[1] for r in rows)
    errors = sum(r[2] for r in rows)
    skipped = sum(r[3] for r in rows)
    failed = failures + errors
    passed = max(0, tests - failed - skipped)

    summary = {
        "platform": args.platform,
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "total": tests,
    }
    with open("platform-summary.json", "w", encoding="utf-8") as out:
        json.dump(summary, out, indent=2)
    print(json.dumps(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
