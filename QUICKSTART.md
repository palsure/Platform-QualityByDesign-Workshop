# Quick Start Guide

Get the workshop up and running in 5 minutes!

## Prerequisites check

```bash
# Check Docker (Compose v2 plugin — note the SPACE, not a hyphen)
docker --version
docker compose version

# Check Java
java -version  # Should be 21+

# Check Node.js
node --version  # Should be 18+
```

## Start everything

```bash
# Start all services
docker compose up -d

# Wait for services to be ready (about 30 seconds)
docker compose ps

# Check backend health
curl http://localhost:8080/actuator/health
```

## Access Services

- **Backend API**: http://localhost:8080
- **Web Player**: http://localhost:3000
- **API Docs**: http://localhost:8080/swagger-ui/index.html (OpenAPI JSON: `/v3/api-docs`)

### API tests (from `backend-api`)

- **`gradle unitTest`** — Mockito/unit tests only (fast, no Docker). Run **before** image build or deploy; the API `Dockerfile` runs this before `bootJar`.
- **`gradle e2eTest`** — REST Assured + Testcontainers (full app on a random port). Run **after** your stack is up, or in CI with Docker. Skipped automatically if Docker is unavailable (`@Testcontainers(disabledWithoutDocker = true)`).
- **`gradle test`** — runs **both** `unit` and `e2e` tagged tests (same as `unitTest` + `e2eTest` together).
- **`gradle allureReport`** — runs **`e2eTest`** then generates HTML at `backend-api/build/reports/allure-report/allureReport/index.html`.

## Test the Setup

### 1. Create a Test Video

```bash
curl -X POST http://localhost:8080/api/v1/videos \
  -H "Content-Type: application/json" \
  -d '{
    "videoId": "test-1",
    "title": "Test Video",
    "hlsManifestUrl": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
  }'
```

### 2. Open Web Player

Open http://localhost:3000 in your browser and play the video.

### 3. Check Metrics

```bash
# View collected metrics
curl http://localhost:8080/api/v1/metrics?platform=web

# Get summary
curl http://localhost:8080/api/v1/metrics/summary?platform=web
```

## Stop everything

```bash
docker compose down
```

## Troubleshooting

**Services not starting?**
```bash
docker compose logs
```

**Port already in use?**
Edit `docker-compose.yml` to change ports.

**Database connection issues?**
```bash
docker compose restart postgres
```

## Next steps

See [`TESTING.md`](TESTING.md) for the full local test playbook (unit / BAT / Smoke / Regression for each module, plus Allure report serving).
