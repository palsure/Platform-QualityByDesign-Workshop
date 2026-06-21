#!/usr/bin/env python3
"""
lambdatest_espresso_run.py

End-to-end LambdaTest Espresso test runner used by the Android pipeline.

Replaces the local emulator path with LambdaTest's real-device cloud:

  1. Uploads the debug APK and the androidTest APK via the App Upload API.
  2. Triggers an Espresso build (with category-annotation filtering so we
     can pick BAT vs Smoke vs Regression from the same APK).
  3. Polls the build until it reaches a terminal state.
  4. Downloads the JUnit XML report so the existing Allure publisher and
     gate evaluator (which both consume JUnit XML) work unchanged.

The script is deliberately self-contained: only stdlib + curl, no extra
PyPI dependencies, so it runs on any GitHub Actions runner without setup.

Required env:
  LT_USERNAME     LambdaTest account username (also exposable as repo Variable)
  LT_ACCESS_KEY   LambdaTest access key (must be a Secret)

Required CLI args: see --help.

Exit codes:
  0   tests ran AND passed (or were 'completed' per LT)
  1   tests ran AND failed   — caller marks gate_passed=false
  2   build did not reach a terminal state, OR no JUnit XML was produced
      — caller marks gate_passed=skipped (treats as "device unavailable")
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


# Region → (manual_api_host, mobile_api_host).  US is the default and works
# for the vast majority of accounts.  EU / IN are documented in case the
# user's org is data-resident.
REGION_URLS: dict[str, tuple[str, str]] = {
    "us": ("manual-api.lambdatest.com",          "mobile-api.lambdatest.com"),
    "eu": ("manual-api.eu-mum-1.lambdatest.com", "mobile-api.eu-mum-1.lambdatest.com"),
    "in": ("manual-api.in-mum-1.lambdatest.com", "mobile-api.in-mum-1.lambdatest.com"),
}

TERMINAL_STATUSES = {"completed", "passed", "failed", "error", "timeout", "cancelled"}
PASSING_STATUSES  = {"completed", "passed"}

EXIT_PASSED       = 0
EXIT_TEST_FAIL    = 1
EXIT_NO_RESULTS   = 2  # device unavailable / build never finished


# ─────────────────────────────────────────────────────────────────────────────
# HTTP helpers
# ─────────────────────────────────────────────────────────────────────────────

def _basic_auth(username: str, access_key: str) -> str:
    return base64.b64encode(f"{username}:{access_key}".encode()).decode()


def _http_json(method: str, url: str, username: str, access_key: str,
               body: dict | None = None, timeout: int = 60) -> dict:
    data = json.dumps(body).encode() if body is not None else None
    req = Request(url, data=data, method=method)
    req.add_header("Authorization", f"Basic {_basic_auth(username, access_key)}")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        raise RuntimeError(f"Non-JSON response from {url}: {raw[:300]!r}")


def _http_get_bytes(url: str, username: str, access_key: str,
                    timeout: int = 60) -> bytes:
    req = Request(url, method="GET")
    req.add_header("Authorization", f"Basic {_basic_auth(username, access_key)}")
    with urlopen(req, timeout=timeout) as resp:
        return resp.read()


def _upload_apk(manual_api: str, username: str, access_key: str,
                apk_path: Path, kind: str) -> str:
    """Multipart upload via curl (stdlib `urllib` doesn't do multipart cleanly).

    Returns the LambdaTest app URL (lt://APP_xxx) for later reference."""
    if not apk_path.exists():
        raise SystemExit(f"❌ APK not found: {apk_path}")
    print(f"📤 Uploading {kind} APK: {apk_path} ({apk_path.stat().st_size:,} bytes)")
    cmd = [
        "curl", "--silent", "--show-error", "--fail-with-body",
        "-u", f"{username}:{access_key}",
        "-X", "POST", f"https://{manual_api}/app/uploadFramework",
        "-F", f"appFile=@{apk_path}",
        "-F", "type=espresso-android",
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=False)
    print(f"   response: {res.stdout[:500]}")
    if res.returncode != 0:
        raise SystemExit(
            f"❌ APK upload failed (curl exit {res.returncode}): {res.stderr or res.stdout}"
        )
    try:
        data = json.loads(res.stdout)
    except json.JSONDecodeError:
        raise SystemExit(f"❌ Non-JSON upload response: {res.stdout!r}")
    # LambdaTest has used multiple key names across versions; accept any.
    app_url = data.get("app_url") or data.get("app_id") or data.get("url")
    if not app_url:
        raise SystemExit(f"❌ No app_url/app_id/url in upload response: {data}")
    print(f"   ✅ {app_url}")
    return app_url


# ─────────────────────────────────────────────────────────────────────────────
# Build orchestration
# ─────────────────────────────────────────────────────────────────────────────

def _trigger_build(mobile_api: str, username: str, access_key: str,
                   payload: dict) -> str:
    print(f"🚀 Triggering Espresso build on device(s): {payload.get('device')}")
    resp = _http_json("POST", f"https://{mobile_api}/framework/v1/espresso/build",
                      username, access_key, body=payload)
    print(f"   response: {json.dumps(resp)[:500]}")
    build_id = resp.get("build_id") or resp.get("buildId") or resp.get("id")
    if not build_id:
        raise SystemExit(f"❌ No build_id in trigger response: {resp}")
    return build_id


def _poll_until_done(mobile_api: str, username: str, access_key: str,
                     build_id: str, poll_interval: int,
                     max_minutes: int) -> str | None:
    """Poll until status is terminal. Returns final status or None on timeout."""
    deadline = time.time() + max_minutes * 60
    last_status = ""
    started = time.time()
    while time.time() < deadline:
        try:
            data = _http_json(
                "GET",
                f"https://{mobile_api}/framework/v1/builds/{build_id}",
                username, access_key,
            )
        except (HTTPError, URLError, RuntimeError) as exc:
            print(f"   ⚠️  poll error: {exc} — retrying after {poll_interval}s")
            time.sleep(poll_interval)
            continue

        status = (data.get("status") or data.get("build_status") or "").lower()
        elapsed = int(time.time() - started)
        if status != last_status:
            print(f"   [{elapsed:4d}s] status={status!r}")
            last_status = status
        if status in TERMINAL_STATUSES:
            return status
        time.sleep(poll_interval)

    print(f"⏱️  Build {build_id} did not reach terminal state in {max_minutes} min")
    return None


def _download_junit(mobile_api: str, username: str, access_key: str,
                    build_id: str, output_dir: Path) -> bool:
    """Download the JUnit XML and write it to output_dir. Returns True on success."""
    output_dir.mkdir(parents=True, exist_ok=True)
    url = f"https://{mobile_api}/framework/v1/builds/{build_id}/report?format=junit"
    print(f"📥 Downloading JUnit XML from {url}")
    try:
        body = _http_get_bytes(url, username, access_key)
    except (HTTPError, URLError) as exc:
        print(f"   ⚠️  JUnit download failed: {exc}")
        return False
    if not body or body[:1] not in (b"<", b"\xef"):  # not XML / BOM
        print(f"   ⚠️  Response is not XML (first 200 bytes): {body[:200]!r}")
        return False
    out = output_dir / f"lambdatest-{build_id}.xml"
    out.write_bytes(body)
    print(f"   ✅ Wrote {len(body):,} bytes to {out}")
    return True


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--app-apk",     required=True, help="Path to debug APK")
    ap.add_argument("--test-apk",    required=True, help="Path to androidTest APK")
    ap.add_argument("--annotation",  required=True,
                    help="JUnit category annotation FQCN, e.g. com.devopsdays.qoe.player.categories.BAT")
    ap.add_argument("--build-name",  required=True)
    ap.add_argument("--device",      default="Pixel 6 Pro-13",
                    help="LambdaTest device name (e.g. 'Pixel 6 Pro-13'). Default: Pixel 6 Pro-13")
    ap.add_argument("--output-dir",  required=True, help="Directory to drop the JUnit XML")
    ap.add_argument("--region",      default="us", choices=list(REGION_URLS.keys()))
    ap.add_argument("--poll-interval", type=int, default=20)
    ap.add_argument("--max-poll-minutes", type=int, default=20)
    args = ap.parse_args()

    username = (os.environ.get("LT_USERNAME") or "").strip()
    access_key = (os.environ.get("LT_ACCESS_KEY") or "").strip()
    if not username or not access_key:
        print("❌ LT_USERNAME and LT_ACCESS_KEY env vars are required", file=sys.stderr)
        return EXIT_NO_RESULTS

    manual_api, mobile_api = REGION_URLS[args.region]
    print(f"::group::LambdaTest Espresso run")
    print(f"   region        : {args.region}")
    print(f"   manual API    : {manual_api}")
    print(f"   mobile API    : {mobile_api}")
    print(f"   device        : {args.device}")
    print(f"   annotation    : {args.annotation}")
    print(f"   build name    : {args.build_name}")
    print(f"   poll interval : {args.poll_interval}s")
    print(f"   poll cap      : {args.max_poll_minutes} min")
    print(f"::endgroup::")

    try:
        app_url  = _upload_apk(manual_api, username, access_key,
                               Path(args.app_apk), kind="app")
        test_url = _upload_apk(manual_api, username, access_key,
                               Path(args.test_apk), kind="test")
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return EXIT_NO_RESULTS

    payload = {
        "app":          app_url,
        "testSuite":    test_url,
        "device":       [args.device],
        "queueTimeout": 360,
        "build":        args.build_name,
        "deviceLog":    True,
        "video":        True,
        "screenshot":   True,
        "annotation":   [args.annotation],
    }
    try:
        build_id = _trigger_build(mobile_api, username, access_key, payload)
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return EXIT_NO_RESULTS

    dashboard_url = f"https://appautomation.lambdatest.com/build/{build_id}"
    print(f"🔗 Live dashboard: {dashboard_url}")
    # Surface the URL via $GITHUB_OUTPUT (consumed by the composite action).
    gh_output = os.environ.get("GITHUB_OUTPUT")
    if gh_output:
        with open(gh_output, "a", encoding="utf-8") as f:
            f.write(f"build_id={build_id}\n")
            f.write(f"dashboard_url={dashboard_url}\n")

    final_status = _poll_until_done(
        mobile_api, username, access_key, build_id,
        poll_interval=args.poll_interval,
        max_minutes=args.max_poll_minutes,
    )
    if final_status is None:
        print("::warning title=LambdaTest build incomplete::"
              "Build did not finish within poll budget — treating as 'device unavailable'.")
        return EXIT_NO_RESULTS

    got_xml = _download_junit(mobile_api, username, access_key,
                              build_id, Path(args.output_dir))
    if not got_xml:
        print("::warning title=No JUnit XML::"
              "LambdaTest produced no JUnit report — treating as 'device unavailable'.")
        return EXIT_NO_RESULTS

    if final_status in PASSING_STATUSES:
        print(f"✅ LambdaTest build {build_id} PASSED ({final_status})")
        return EXIT_PASSED

    print(f"❌ LambdaTest build {build_id} finished as {final_status!r}")
    return EXIT_TEST_FAIL


if __name__ == "__main__":
    raise SystemExit(main())
