#!/bin/bash
set -e

PR_ID="${1:-local}"
NAMESPACE="qoe-pr-${PR_ID}"

echo "Creating namespace: $NAMESPACE"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

echo "Applying manifests to $NAMESPACE"
kubectl apply -n "$NAMESPACE" -f "$(dirname "$0")/../env/backend.yaml"

echo "Waiting for deployment..."
kubectl rollout status -n "$NAMESPACE" deploy/qoe-backend --timeout=180s

echo "Environment ready: $NAMESPACE"
