# Ops

Workshop utility resources: local streaming infrastructure and optional New Relic observability config.

## Layout

```
ops/
├── infrastructure/
│   ├── server/nginx.conf
│   ├── transcoding/generate-hls.sh
│   ├── test-content/generate-test-video.sh
│   └── network-simulation/tc-slow.sh
└── monitoring/newrelic/
    ├── newrelic.yml
    ├── alerts/                      # Alert policy JSON (optional)
    └── scripts/install-dashboard.sh
```

## Infrastructure

```bash
# Synthetic test video
cd ops/infrastructure/test-content && ./generate-test-video.sh output.mp4 30

# HLS transcode
cd ops/infrastructure/transcoding && ./generate-hls.sh input.mp4 output-dir

# Local CDN
docker compose up nginx -d   # http://localhost:8081/videos/
```

## Monitoring (New Relic, optional)

```bash
NEW_RELIC_USER_API_KEY=NRAK-... NEW_RELIC_ACCOUNT_ID=... \
  ops/monitoring/newrelic/scripts/install-dashboard.sh
```

Add dashboard JSON under `monitoring/newrelic/dashboards/` when ready — CI skips deploy when the folder is empty.
