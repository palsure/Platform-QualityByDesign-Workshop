#!/bin/bash
set -e

ENVIRONMENT="${1:-pr}"

MAX_ERROR_RATE="${MAX_ERROR_RATE:-0.05}"
MAX_P95_MS="${MAX_P95_MS:-500}"

ERROR_RATE="${ERROR_RATE:-0.01}"
P95_MS="${P95_MS:-250}"

echo "Quality gate ($ENVIRONMENT): error_rate<$MAX_ERROR_RATE p95<$MAX_P95_MS"

cmp_err=$(awk -v a="$ERROR_RATE" -v b="$MAX_ERROR_RATE" 'BEGIN{print (a<b)?1:0}')
cmp_lat=$(awk -v a="$P95_MS" -v b="$MAX_P95_MS" 'BEGIN{print (a<b)?1:0}')

if [ "$cmp_err" -ne 1 ]; then
  echo "FAIL error_rate=$ERROR_RATE"
  exit 1
fi

if [ "$cmp_lat" -ne 1 ]; then
  echo "FAIL p95_ms=$P95_MS"
  exit 1
fi

echo "PASS"
