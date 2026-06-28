#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLUSTER_NAME="${CLUSTER_NAME:-failure-monitoring}"

echo "=========================================="
echo "Failure Monitoring POC - Cleanup Script"
echo "=========================================="
echo ""

echo "Removing deployment (failure-monitoring namespace)..."
kubectl delete namespace failure-monitoring --ignore-not-found

if [ "${DELETE_KIND_CLUSTER:-}" = "1" ]; then
    echo ""
    echo "Deleting kind cluster '${CLUSTER_NAME}'..."
    kind delete cluster --name "${CLUSTER_NAME}"
fi

echo ""
echo "Cleanup completed."
echo ""
echo "To also delete the kind cluster, run:"
echo "  DELETE_KIND_CLUSTER=1 ./cleanup.sh"
echo "  # or: kind delete cluster --name ${CLUSTER_NAME}"
echo ""
