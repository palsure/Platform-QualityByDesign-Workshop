#!/bin/bash
set -e

API_BASE="${API_BASE:-http://localhost:8080}"
WEB_BASE="${WEB_BASE:-http://localhost:3000}"

curl -fsS "$API_BASE/actuator/health" | grep -q '"status":"UP"'
curl -fsS "$API_BASE/api/v1/platforms" | grep -q '"key"'
curl -fsS "$WEB_BASE/" | grep -qi 'qoe\|root'

echo "smoke ok"
