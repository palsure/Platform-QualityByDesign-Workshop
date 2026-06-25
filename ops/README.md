# Ops

Workshop utility resources: local streaming infrastructure and optional New Relic observability config.

## Layout

```
ops/
├── infrastructure/                  # Local streaming infrastructure
│   ├── server/nginx.conf                    # Nginx video CDN config
│   ├── transcoding/generate-hls.sh          # FFmpeg HLS transcoder
│   ├── test-content/generate-test-video.sh  # Synthetic test video generator
│   └── network-simulation/tc-slow.sh        # Linux traffic shaper
└── monitoring/newrelic/             # Observability config (optional)
    ├── newrelic.yml
    ├── dashboards/                  # Importable dashboard JSON
    ├── alerts/                      # Alert policy JSON
    ├── nrql-queries/                # Useful NRQL queries
    └── scripts/                     # install-dashboard.sh, etc.
```

## Infrastructure

### Generate a synthetic test video (FFmpeg required)

```bash
cd ops/infrastructure/test-content
./generate-test-video.sh output.mp4 30      # 30-second test video
```

### Transcode to HLS multi-bitrate streams

```bash
cd ops/infrastructure/transcoding
./generate-hls.sh /path/to/input.mp4 /path/to/output-dir
```

### Serve videos locally with nginx

```bash
docker compose up nginx -d
# Videos served at http://localhost:8081/videos/
```

### Simulate a slow network (Linux only — requires root)

```bash
cd ops/infrastructure/network-simulation
sudo ./tc-slow.sh eth0 1mbit 100ms 1%
sudo tc qdisc del dev eth0 root   # reset
```

## Monitoring (New Relic, optional)

Copy `newrelic.yml` into `backend-api/` and set `NEW_RELIC_LICENSE_KEY`, or use the `streaming-app-newrelic.yml` workflow when dashboard/alert JSON is present under `monitoring/newrelic/`.

```bash
NEW_RELIC_USER_API_KEY=NRAK-... NEW_RELIC_ACCOUNT_ID=... \
  ops/monitoring/newrelic/scripts/install-dashboard.sh
```

The dashboard deploy step skips automatically when no JSON files exist in `dashboards/`.
