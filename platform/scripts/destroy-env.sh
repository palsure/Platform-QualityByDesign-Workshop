#!/bin/bash
set -e

PR_ID="${1:-local}"
NAMESPACE="qoe-pr-${PR_ID}"

echo "Deleting namespace: $NAMESPACE"
kubectl delete namespace "$NAMESPACE" --ignore-not-found
