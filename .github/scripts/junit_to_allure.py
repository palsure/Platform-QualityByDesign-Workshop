#!/usr/bin/env python3
"""
junit_to_allure.py

Convert a directory of JUnit / xUnit XML files into Allure 2 result JSON files
so `allure generate` can render a real HTML report.

Why: `allure generate` only consumes the Allure result format
(*-result.json + *-attachment.*). Frameworks like Swift's
`swift test --xunit-output` or plain XCTest emit JUnit XML, which Allure
ignores — producing an "Allure Report Unknown / 0 test cases" page.

This converter handles both common root shapes:
  • <testsuite>   ... </testsuite>            (Gradle TEST-*.xml, Surefire)
  • <testsuites>  <testsuite>…</testsuite> </testsuites>   (Swift, Jest,
                                                           Vitest, Playwright)

It also enriches each test case with the labels Allure uses to build its
"Behaviors" tree and filter chips, even though JUnit XML does not carry
that information natively:

  • epic / feature / story  → Behaviors tree (Epic → Feature → Story)
  • tag                     → Coloured chip on each test card (BAT, Smoke,
                              Regression, Unit) — perfect for filtering
  • severity                → blocker / critical / normal / minor
  • steps[]                 → a single synthesised step derived from the
                              humanised test method name; gives the test
                              body panel some content even for plain XCTest
                              / Espresso tests that don't call `allure.step`
  • description             → short markdown summary

Customise via environment variables:

  ALLURE_EPIC      — top-level group name (e.g. "Android Player E2E").
                     Falls back to a sensible default per stage.
  ALLURE_FEATURE   — feature label override.  Falls back to inferred from
                     test class name + stage.
  ALLURE_TAG       — tag chip override.  Default is inferred from the test
                     class name (BAT/Smoke/Regression/Unit).
  ALLURE_SEVERITY  — severity override.  Default is inferred from tag
                     (BAT=blocker, Smoke=critical, Regression=normal).
  ALLURE_LANGUAGE  — language label (e.g. "kotlin", "swift", "java").

Usage:
  python3 junit_to_allure.py --input <dir-with-xml> --output <allure-results-dir>

The output directory is populated with one `<uuid>-result.json` per test case
plus `categories.json`, `environment.properties`, and `executor.json`.
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

# ─────────────────────────────────────────────────────────────────────────────
# Tag → severity / feature mapping
# ─────────────────────────────────────────────────────────────────────────────

TAG_BAT        = "BAT"
TAG_SMOKE      = "Smoke"
TAG_REGRESSION = "Regression"
TAG_UNIT       = "Unit"

DEFAULT_FEATURE_FOR_TAG = {
    TAG_BAT:        "Build Acceptance Tests",
    TAG_SMOKE:      "Smoke Tests",
    TAG_REGRESSION: "Regression Tests",
    TAG_UNIT:       "Unit Tests",
}

DEFAULT_SEVERITY_FOR_TAG = {
    TAG_BAT:        "blocker",
    TAG_SMOKE:      "critical",
    TAG_REGRESSION: "normal",
    TAG_UNIT:       "normal",
}


def _now_ms() -> int:
    return int(time.time() * 1000)


def _stable_uuid(*parts: str) -> str:
    """Deterministic UUID so re-running on the same XML produces the same files
    (helps Allure's history/trend tracking)."""
    h = hashlib.sha1("\u0001".join(parts).encode("utf-8")).hexdigest()
    return f"{h[:8]}-{h[8:12]}-{h[12:16]}-{h[16:20]}-{h[20:32]}"


def _status_for(testcase: ET.Element) -> tuple[str, dict | None]:
    """Map JUnit testcase result into Allure (status, status_details)."""
    failure = testcase.find("failure")
    error = testcase.find("error")
    skipped = testcase.find("skipped")

    if failure is not None:
        return "failed", {
            "message": failure.attrib.get("message", "") or (failure.text or "").strip()[:500],
            "trace":   (failure.text or "").strip(),
        }
    if error is not None:
        return "broken", {
            "message": error.attrib.get("message", "") or (error.text or "").strip()[:500],
            "trace":   (error.text or "").strip(),
        }
    if skipped is not None:
        return "skipped", {
            "message": skipped.attrib.get("message", "") or "Skipped",
            "trace":   (skipped.text or "").strip(),
        }
    return "passed", None


def _suites(root: ET.Element):
    """Yield every <testsuite> regardless of root shape."""
    if root.tag == "testsuites":
        for s in root.findall("testsuite"):
            yield s
    elif root.tag == "testsuite":
        yield root


# ─────────────────────────────────────────────────────────────────────────────
# Heuristics: classify and humanise test names
# ─────────────────────────────────────────────────────────────────────────────

_BAT_RE        = re.compile(r"\bBAT(s|Test|Tests)?\b", re.IGNORECASE)
_SMOKE_RE      = re.compile(r"\bSmoke(Test|Tests)?\b", re.IGNORECASE)
_REGRESSION_RE = re.compile(r"\bRegression(Test|Tests)?\b", re.IGNORECASE)


def _infer_tag(classname: str, suite_name: str, method: str) -> str:
    """Return BAT / Smoke / Regression / Unit based on class + method names."""
    blob = f"{classname} {suite_name} {method}".lower()
    if "regression" in blob:
        return TAG_REGRESSION
    if "smoke" in blob:
        return TAG_SMOKE
    if re.search(r"\bbat\b|\bbat[_\.]|\.bat[\.]|bat(test|tests)\b", blob):
        return TAG_BAT
    return TAG_UNIT


# Known mixed-case or pure-uppercase acronyms that should never be split.
# Mixed-case acronyms (e.g. "QoE") cannot be detected algorithmically because
# the lowercase letter looks like a CamelCase word boundary; we list them.
_ACRONYMS = (
    "QoE", "QoS", "ABR", "DRM", "HLS", "DASH",
    "URL", "HTTP", "HTTPS", "JSON", "XML", "API", "CTA",
    "iOS", "tvOS", "macOS", "watchOS",
)


def _is_acronym(word: str) -> bool:
    return word in _ACRONYMS or (word.isupper() and len(word) >= 2)


def _merge_acronyms(words: list[str]) -> list[str]:
    """Merge adjacent tokens that together spell a known acronym.

    Comparison is case-insensitive and the canonical case from _ACRONYMS is
    restored — so 'qoe' from snake_case becomes 'QoE'.
    """
    out: list[str] = []
    i = 0
    n = len(words)
    while i < n:
        merged = False
        for ac in _ACRONYMS:
            for k in range(min(4, n - i), 0, -1):  # prefer longest match
                candidate = "".join(words[i:i + k])
                if candidate.lower() == ac.lower():
                    out.append(ac)
                    i += k
                    merged = True
                    break
            if merged:
                break
        if not merged:
            out.append(words[i])
            i += 1
    return out


def _split_words(text: str) -> list[str]:
    """Tokenise snake_case + CamelCase while keeping acronyms intact.

    'app_launches_without_crashing'        → ['app','launches','without','crashing']
    'test_bat_collectorCreatesWithVideoId' → ['test','bat','collector','Creates','With','Video','Id']
    'QoECollectorTest'                     → ['QoE','Collector','Test']
    'parseHTMLString'                      → ['parse','HTML','String']
    'url_input_is_visible'                 → ['URL','input','is','visible']
    """
    if not text:
        return []
    s = text.replace("_", " ").replace("-", " ")
    # Acronym + CapWord boundary: "HTMLP" + "arser" → split between L and P
    s = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1 \2", s)
    # lowercase/digit followed by uppercase
    s = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", s)
    raw = [w for w in s.split() if w]
    return _merge_acronyms(raw)


def _humanize(text: str) -> str:
    """Turn 'app_launches_without_crashing' or 'test_bat_collectorCreatesWithVideoId'
    into 'App launches without crashing' / 'Collector creates with video id'.

    Preserves uppercase acronyms (QoE, URL, HLS, etc.) when they appear as
    standalone tokens after splitting."""
    n = text or ""
    n = re.sub(r"^test[_\W]+", "", n, flags=re.IGNORECASE)
    n = re.sub(r"^(bat|smoke|regression|unit)[_\W]+", "", n, flags=re.IGNORECASE)
    words = _split_words(n)
    if not words:
        return text

    out: list[str] = []
    for i, w in enumerate(words):
        if _is_acronym(w):
            out.append(w)
        elif i == 0:
            out.append(w[:1].upper() + w[1:].lower())
        else:
            out.append(w.lower())
    return " ".join(out)


def _short_class_name(classname: str) -> str:
    """Strip package prefix from a fully-qualified Java/Kotlin class."""
    return classname.rsplit(".", 1)[-1] if classname else ""


def _derive_story(classname: str, method: str) -> str:
    """Derive a 'Story' label so the Allure Behaviors tree has a third level.

    Strategy:
      1. For unit-style tests (e.g. QoECollectorTest, QoEQualityCalculatorTest)
         use the test class minus 'Test'/'Tests' suffix.
      2. For E2E-style tests where the class is just BATTest / SmokeTest,
         derive the story from the first 1-2 words of the humanised method
         name (e.g. 'url_input_is_visible' → 'Url input').
    """
    short = _short_class_name(classname)
    cleaned = re.sub(r"(BAT|Smoke|Regression)?(Test|Tests)$", "", short).strip()

    if cleaned and cleaned.lower() not in ("", "bat", "smoke", "regression"):
        words = _split_words(cleaned)
        if words:
            return " ".join(w if _is_acronym(w) else w.capitalize() for w in words)

    h = _humanize(method)
    parts = h.split()
    if not parts:
        return "General"
    first = parts[0] if _is_acronym(parts[0]) else parts[0].capitalize()
    if len(parts) == 1:
        return first
    second = parts[1] if _is_acronym(parts[1]) else parts[1].lower()
    return f"{first} {second}"


def _derive_feature(tag: str, classname: str) -> str:
    """Allure Feature label — uses env override if set, otherwise inferred."""
    override = os.environ.get("ALLURE_FEATURE", "").strip()
    if override:
        return override
    return DEFAULT_FEATURE_FOR_TAG.get(tag, _short_class_name(classname) or "Tests")


def _derive_severity(tag: str) -> str:
    override = os.environ.get("ALLURE_SEVERITY", "").strip()
    if override:
        return override
    return DEFAULT_SEVERITY_FOR_TAG.get(tag, "normal")


def _derive_epic() -> str:
    """Top-level Behaviors group; passed by the workflow."""
    return os.environ.get("ALLURE_EPIC", "").strip() or "Test Suite"


# ─────────────────────────────────────────────────────────────────────────────
# Conversion
# ─────────────────────────────────────────────────────────────────────────────

def _build_step(name: str, status: str, start: int, stop: int) -> dict:
    """Synthesise a single Allure step from the test name. Without this the
    Allure 'Test body' panel is empty for JUnit-only frameworks."""
    return {
        "name":   name,
        "status": status,
        "stage":  "finished",
        "start":  start,
        "stop":   stop,
    }


def _convert_file(xml_path: Path, out_dir: Path) -> int:
    """Convert one XML file and return the number of test cases written."""
    try:
        root = ET.parse(xml_path).getroot()
    except ET.ParseError as exc:
        print(f"⚠️  {xml_path}: parse error — {exc}", file=sys.stderr)
        return 0

    epic_label     = _derive_epic()
    tag_override   = os.environ.get("ALLURE_TAG", "").strip()
    language       = os.environ.get("ALLURE_LANGUAGE", "").strip()

    written = 0
    for suite in _suites(root):
        suite_name = suite.attrib.get("name", "TestSuite")
        try:
            base_ts = int(float(suite.attrib.get("timestamp", "")) * 1000)
        except (TypeError, ValueError):
            base_ts = _now_ms()

        for tc in suite.findall("testcase"):
            classname = tc.attrib.get("classname", suite_name)
            name = tc.attrib.get("name", "anonymous")
            full_name = f"{classname}.{name}" if classname else name
            try:
                duration_ms = int(float(tc.attrib.get("time", "0")) * 1000)
            except ValueError:
                duration_ms = 0

            status, details = _status_for(tc)
            test_uuid = _stable_uuid(xml_path.name, full_name)
            history_id = hashlib.md5(full_name.encode("utf-8")).hexdigest()

            tag      = tag_override or _infer_tag(classname, suite_name, name)
            feature  = _derive_feature(tag, classname)
            story    = _derive_story(classname, name)
            severity = _derive_severity(tag)

            humanised   = _humanize(name)
            short_class = _short_class_name(classname) or suite_name
            description = (
                f"**{tag}** test in `{short_class}`\n\n"
                f"_Verifies:_ {humanised}."
            )

            labels = [
                {"name": "epic",       "value": epic_label},
                {"name": "feature",    "value": feature},
                {"name": "story",      "value": story},
                {"name": "tag",        "value": tag},
                {"name": "severity",   "value": severity},
                {"name": "suite",      "value": suite_name},
                {"name": "subSuite",   "value": short_class},
                {"name": "testClass",  "value": classname or suite_name},
                {"name": "testMethod", "value": name},
                {"name": "framework",  "value": "junit"},
            ]
            if language:
                labels.append({"name": "language", "value": language})

            step_start = base_ts
            step_stop  = base_ts + max(0, duration_ms)

            result: dict = {
                "uuid":        test_uuid,
                "historyId":   history_id,
                "name":        humanised or name,
                "fullName":    full_name,
                "description": description,
                "status":      status,
                "stage":       "finished",
                "start":       base_ts,
                "stop":        step_stop,
                "labels":      labels,
                "steps":       [_build_step(humanised or name, status, step_start, step_stop)],
            }
            if details is not None:
                result["statusDetails"] = {k: v for k, v in details.items() if v}

            (out_dir / f"{test_uuid}-result.json").write_text(
                json.dumps(result, indent=2), encoding="utf-8"
            )
            written += 1

    return written


def _write_categories_json(out_dir: Path) -> None:
    """Allure's 'Categories' tab buckets failures by tag/status. Empty
    buckets are simply hidden, so writing the full list once is fine."""
    categories = [
        {
            "name":            "🔴 BAT failures (deployment blocker)",
            "matchedStatuses": ["failed", "broken"],
            "messageRegex":    ".*",
            "traceRegex":      ".*",
        },
        {
            "name":            "🟠 Smoke failures",
            "matchedStatuses": ["failed", "broken"],
            "messageRegex":    ".*",
            "traceRegex":      ".*",
        },
        {
            "name":            "🟡 Regression failures",
            "matchedStatuses": ["failed", "broken"],
            "messageRegex":    ".*",
            "traceRegex":      ".*",
        },
        {
            "name":            "⏭️  Skipped tests",
            "matchedStatuses": ["skipped"],
            "messageRegex":    ".*",
            "traceRegex":      ".*",
        },
    ]
    (out_dir / "categories.json").write_text(
        json.dumps(categories, indent=2), encoding="utf-8"
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input",  required=True, help="Directory containing JUnit XML files")
    ap.add_argument("--output", required=True, help="Allure results output directory")
    args = ap.parse_args()

    in_dir = Path(args.input)
    out_dir = Path(args.output)

    if not in_dir.exists():
        print(f"⚠️  Input directory not found: {in_dir}", file=sys.stderr)
        return 0

    out_dir.mkdir(parents=True, exist_ok=True)

    xml_files = sorted(
        set(glob.glob(str(in_dir / "**/*.xml"), recursive=True))
        - set(glob.glob(str(out_dir / "**/*.xml"), recursive=True))
    )
    if not xml_files:
        print(f"ℹ️  No XML files found under {in_dir}")
        return 0

    total = 0
    for xml in xml_files:
        n = _convert_file(Path(xml), out_dir)
        print(f"  • {xml} → {n} test case(s)")
        total += n

    _write_categories_json(out_dir)

    env_props = out_dir / "environment.properties"
    if not env_props.exists():
        env_props.write_text(
            "Source=junit_to_allure.py\n"
            f"Generated={time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}\n"
            f"Epic={_derive_epic()}\n"
            f"FeatureOverride={os.environ.get('ALLURE_FEATURE', '<auto>')}\n"
            f"TagOverride={os.environ.get('ALLURE_TAG', '<auto>')}\n",
            encoding="utf-8",
        )

    executor = out_dir / "executor.json"
    if not executor.exists():
        executor.write_text(json.dumps({
            "name":       os.environ.get("GITHUB_WORKFLOW", "CI"),
            "type":       "github",
            "url":        os.environ.get("GITHUB_SERVER_URL", "https://github.com"),
            "buildOrder": int(os.environ.get("GITHUB_RUN_NUMBER", "0") or "0"),
            "buildName":  f"#{os.environ.get('GITHUB_RUN_NUMBER', '0')}",
            "buildUrl":   (
                f"{os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/"
                f"{os.environ.get('GITHUB_REPOSITORY', '')}/actions/runs/"
                f"{os.environ.get('GITHUB_RUN_ID', '')}"
            ),
            "reportName": os.environ.get("ALLURE_REPORT_NAME", "Allure Report"),
        }, indent=2), encoding="utf-8")

    print(f"✅ Converted {total} test case(s) → {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
