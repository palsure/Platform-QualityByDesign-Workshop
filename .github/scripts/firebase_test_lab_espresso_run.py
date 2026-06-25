#!/usr/bin/env python3
"""
firebase_test_lab_espresso_run.py

Run Espresso instrumentation tests on Firebase Test Lab (virtual devices by
default — free-tier friendly) and download JUnit XML for Allure / gate logic.

Exit codes (mirrors lambdatest_espresso_run.py):
  0   tests ran AND passed
  1   tests ran AND failed
  2   lab unavailable (quota, auth, timeout, no results) — caller may fall back
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

EXIT_PASSED = 0
EXIT_TEST_FAIL = 1
EXIT_NO_RESULTS = 2

UNAVAILABLE_HINTS = (
    "quota",
    "resource exhausted",
    "permission denied",
    "billing",
    "not enabled",
    "api has not been used",
    "service disabled",
    "unable to",
    "unavailable",
    "could not",
    "invalid argument",
    "does not exist",
    "access denied",
    "429",
    "503",
)


def _run(cmd: list[str], *, timeout: int | None = None) -> subprocess.CompletedProcess[str]:
    print(f"$ {' '.join(cmd)}")
    return subprocess.run(
        cmd,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )


def _looks_unavailable(text: str) -> bool:
    lower = text.lower()
    return any(hint in lower for hint in UNAVAILABLE_HINTS)


def _ensure_results_bucket(project_id: str, bucket: str) -> None:
    """Create the GCS bucket if missing (best-effort)."""
    if not bucket.startswith("gs://"):
        bucket = f"gs://{bucket}"
    probe = _run(["gsutil", "ls", "-b", bucket])
    if probe.returncode == 0:
        return
    print(f"::notice::Creating results bucket {bucket}")
    loc = os.environ.get("FTL_RESULTS_LOCATION", "US")
    create = _run(["gsutil", "mb", "-p", project_id, "-l", loc, bucket])
    if create.returncode != 0 and "BucketAlreadyOwnedByYou" not in create.stderr:
        print(create.stderr or create.stdout)


def _collect_junit_xml(gcs_path: str, output_dir: Path) -> int:
    """Copy test_result*.xml files from GCS into output_dir. Returns test count."""
    output_dir.mkdir(parents=True, exist_ok=True)
    listing = _run(["gsutil", "ls", "-r", gcs_path.rstrip("/")])
    if listing.returncode != 0:
        print(f"::warning::Could not list objects under {gcs_path}: {listing.stderr}")
        return 0

    paths = [
        line.strip()
        for line in listing.stdout.splitlines()
        if line.strip().startswith("gs://") and "test_result" in line and line.endswith(".xml")
    ]
    if not paths:
        print(f"::warning::No JUnit XML found under {gcs_path}")
        return 0

    total_tests = 0
    for idx, gcs_file in enumerate(paths, start=1):
        dest = output_dir / f"firebase-test-lab-{idx}.xml"
        cp = _run(["gsutil", "-q", "cp", gcs_file, str(dest)])
        if cp.returncode != 0:
            print(f"::warning::Failed to copy {gcs_file}: {cp.stderr}")
            continue
        text = dest.read_text(encoding="utf-8", errors="replace")
        match = re.search(r'tests="(\d+)"', text)
        if match:
            total_tests += int(match.group(1))
        print(f"   ✅ {dest.name} ({dest.stat().st_size:,} bytes)")

    return total_tests


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--app-apk", required=True)
    ap.add_argument("--test-apk", required=True)
    ap.add_argument("--annotation", required=True)
    ap.add_argument("--matrix-label", required=True)
    ap.add_argument("--output-dir", required=True)
    ap.add_argument("--project-id", default=os.environ.get("FIREBASE_PROJECT_ID", "").strip())
    ap.add_argument(
        "--device",
        default=os.environ.get(
            "FTL_DEVICE_ANDROID",
            "model=MediumPhone.arm,version=33,locale=en,orientation=portrait",
        ),
    )
    ap.add_argument("--timeout", default="15m")
    ap.add_argument("--results-bucket", default="")
    ap.add_argument("--results-dir", default="")
    args = ap.parse_args()

    project_id = args.project_id
    if not project_id:
        print("❌ FIREBASE_PROJECT_ID / --project-id is required", file=sys.stderr)
        return EXIT_NO_RESULTS

    app_apk = Path(args.app_apk)
    test_apk = Path(args.test_apk)
    output_dir = Path(args.output_dir)
    if not app_apk.is_file() or not test_apk.is_file():
        print(f"❌ APK not found: app={app_apk} test={test_apk}", file=sys.stderr)
        return EXIT_NO_RESULTS

    if not shutil.which("gcloud"):
        print("❌ gcloud CLI not found on PATH", file=sys.stderr)
        return EXIT_NO_RESULTS

    bucket = args.results_bucket or f"gs://{project_id}-test-lab-results"
    results_dir = args.results_dir or f"android/{int(time.time())}-{args.matrix_label.replace(' ', '-')}"

    print("::group::Firebase Test Lab Espresso run")
    print(f"   project       : {project_id}")
    print(f"   device        : {args.device}")
    print(f"   annotation    : {args.annotation}")
    print(f"   matrix label  : {args.matrix_label}")
    print(f"   results       : {bucket}/{results_dir}")
    print("::endgroup::")

    _run(["gcloud", "config", "set", "project", project_id])
    _ensure_results_bucket(project_id, bucket)

    cmd = [
        "gcloud", "firebase", "test", "android", "run",
        "--type", "instrumentation",
        "--app", str(app_apk),
        "--test", str(test_apk),
        "--device", args.device,
        "--use-orchestrator",
        "--environment-variables", "clearPackageData=true",
        "--test-targets", f"annotation {args.annotation}",
        "--results-bucket", bucket,
        "--results-dir", results_dir,
        "--timeout", args.timeout,
        "--client-details", f"matrixLabel={args.matrix_label}",
        "--format", "json",
    ]

    result = _run(cmd, timeout=25 * 60)
    combined = (result.stdout or "") + "\n" + (result.stderr or "")
    print(combined[-8000:])  # tail — matrix JSON can be large

    gcs_path = f"{bucket.rstrip('/')}/{results_dir}"
    matrix_id = ""
    console_url = f"https://console.firebase.google.com/project/{project_id}/testlab/histories"

    try:
        payload = json.loads(result.stdout) if result.stdout.strip().startswith("{") else {}
        storage = payload.get("resultStorage", {}).get("googleCloudStorage", {})
        if storage.get("gcsPath"):
            gcs_path = storage["gcsPath"]
        matrix_id = payload.get("testMatrixId", "") or payload.get("name", "").split("/")[-1]
        if matrix_id:
            console_url = (
                f"https://console.firebase.google.com/project/{project_id}"
                f"/testlab/histories/bh.{matrix_id}"
            )
    except json.JSONDecodeError:
        match = re.search(r"(gs://[^\s\]]+)", combined)
        if match:
            gcs_path = match.group(1)

    gh_output = os.environ.get("GITHUB_OUTPUT")
    if gh_output:
        with open(gh_output, "a", encoding="utf-8") as f:
            f.write(f"matrix_id={matrix_id}\n")
            f.write(f"console_url={console_url}\n")
            f.write(f"gcs_path={gcs_path}\n")

    total_tests = _collect_junit_xml(gcs_path, output_dir)
    print(f"Tests counted from JUnit XML: {total_tests}")

    if total_tests == 0:
        if _looks_unavailable(combined):
            print("::warning title=Firebase Test Lab unavailable::"
                  "No tests executed — quota/auth/device issue.")
        else:
            print("::warning title=No JUnit XML::"
                  "Firebase Test Lab produced no downloadable results.")
        return EXIT_NO_RESULTS

    if result.returncode == 0:
        print(f"✅ Firebase Test Lab passed ({total_tests} tests)")
        return EXIT_PASSED

    if _looks_unavailable(combined) and total_tests == 0:
        return EXIT_NO_RESULTS

    print(f"❌ Firebase Test Lab finished with failures (gcloud exit {result.returncode})")
    return EXIT_TEST_FAIL


if __name__ == "__main__":
    raise SystemExit(main())
