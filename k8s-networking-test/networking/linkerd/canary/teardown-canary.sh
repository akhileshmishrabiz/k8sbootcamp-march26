#!/bin/bash
# Remove canary lab artifacts and restore single order-service deployment.
set -euo pipefail

NAMESPACE="${NAMESPACE:-ecommerce}"

kubectl delete trafficsplit order-service-split -n "$NAMESPACE" --ignore-not-found
kubectl delete deploy order-service-canary -n "$NAMESPACE" --ignore-not-found
kubectl delete svc order-service-stable order-service-canary -n "$NAMESPACE" --ignore-not-found

# Remove version label from stable deployment selector
kubectl patch deploy order-service -n "$NAMESPACE" --type=json \
  -p='[{"op":"remove","path":"/spec/selector/matchLabels/version"}]' 2>/dev/null || true
kubectl patch deploy order-service -n "$NAMESPACE" -p \
  '{"spec":{"template":{"metadata":{"labels":{"version":null}}}}}' 2>/dev/null || true
kubectl rollout restart deploy/order-service -n "$NAMESPACE" 2>/dev/null || true

echo "Canary teardown complete. order-service restored to single deployment."
