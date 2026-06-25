#!/bin/bash
set -e

API_BASE="${API_BASE:-http://localhost:8080}"
WEB_BASE="${WEB_BASE:-http://localhost:3000}"

curl -fsS "$API_BASE/actuator/health" | grep -q '"status":"UP"'
curl -fsS "$API_BASE/api/v1/videos" | grep -q '"hlsManifestUrl"'
curl -fsS "$WEB_BASE/" | grep -qi 'streamapp\|root'

echo "smoke ok"
