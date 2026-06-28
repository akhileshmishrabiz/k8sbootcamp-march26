#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/kind-utils.sh
source "${ROOT_DIR}/scripts/kind-utils.sh"

NAMESPACE="${1:-failure-monitoring}"

echo "=========================================="
echo "Accessing Failure Monitoring Dashboard"
echo "=========================================="
echo ""

require_kind
require_kubectl

if ! kind get clusters 2>/dev/null | grep -qx "${CLUSTER_NAME}"; then
    echo "Error: kind cluster '${CLUSTER_NAME}' not found. Run ./setup-kind.sh or ./deploy.sh first."
    exit 1
fi

if ! kubectl get namespace "${NAMESPACE}" >/dev/null 2>&1; then
    echo "Error: Namespace '${NAMESPACE}' not found. Run ./deploy.sh first."
    exit 1
fi

echo "NodePort (if kind port mapping is active): ${DASHBOARD_URL}"
echo ""
echo "Port-forwarding dashboard to http://localhost:3001"
echo ""
echo "Press Ctrl+C to stop the port-forward"
echo ""

kubectl port-forward -n "${NAMESPACE}" service/dashboard-service 3001:3001
