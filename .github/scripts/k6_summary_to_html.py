#!/usr/bin/env python3
"""Render a k6 --summary-export JSON file as a simple HTML report."""

from __future__ import annotations

import json
import sys
from pathlib import Path


def fmt_metric(name: str, data: dict) -> str:
    values = data.get("values") or {}
    if data.get("type") == "rate":
        rate = values.get("rate", 0)
        return f"{rate * 100:.2f}% failed ({values.get('fails', 0)} fails / {values.get('passes', 0)} passes)"
    if data.get("type") == "trend":
        parts = []
        for key in ("avg", "min", "med", "max", "p(90)", "p(95)"):
            if key in values:
                parts.append(f"{key}={values[key]:.2f}ms")
        return ", ".join(parts) if parts else "—"
    return ", ".join(f"{k}={v}" for k, v in values.items()) or "—"


def main() -> int:
    src = Path(sys.argv[1] if len(sys.argv) > 1 else "platform/tests/load/k6-summary.json")
    out_dir = Path(sys.argv[2] if len(sys.argv) > 2 else "ephemeral-k6-report")
    title = sys.argv[3] if len(sys.argv) > 3 else "k6 Performance Smoke"

    if not src.exists():
        print(f"No k6 summary at {src}", file=sys.stderr)
        return 1

    summary = json.loads(src.read_text(encoding="utf-8"))
    metrics = summary.get("metrics") or {}

    rows = "\n".join(
        f"<tr><td><code>{name}</code></td><td>{fmt_metric(name, data)}</td></tr>"
        for name, data in sorted(metrics.items())
        if name.startswith("http_") or name in {"checks", "iterations", "vus"}
    )

    html = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <title>{title}</title>
  <style>
    body {{ font-family: system-ui, sans-serif; margin: 2rem; color: #111; }}
    h1 {{ font-size: 1.4rem; }}
    table {{ border-collapse: collapse; width: 100%; max-width: 960px; }}
    th, td {{ border: 1px solid #ddd; padding: 0.5rem 0.75rem; text-align: left; }}
    th {{ background: #f5f5f5; }}
  </style>
</head>
<body>
  <h1>{title}</h1>
  <p>Generated from k6 summary export.</p>
  <table>
    <thead><tr><th>Metric</th><th>Values</th></tr></thead>
    <tbody>
      {rows or '<tr><td colspan="2">No metrics recorded.</td></tr>'}
    </tbody>
  </table>
</body>
</html>
"""

    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "index.html").write_text(html, encoding="utf-8")
    (out_dir / ".nojekyll").touch()
    print(f"Wrote {out_dir / 'index.html'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
