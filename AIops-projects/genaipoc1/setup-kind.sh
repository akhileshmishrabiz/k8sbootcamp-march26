#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/kind-utils.sh
source "${ROOT_DIR}/scripts/kind-utils.sh"

echo "=========================================="
echo "Failure Monitoring POC - kind Setup"
echo "=========================================="
echo ""

ensure_kind_cluster "${ROOT_DIR}"

echo ""
echo "Kind cluster '${CLUSTER_NAME}' is ready."
echo "Context: kind-${CLUSTER_NAME}"
echo ""
echo "Next step:"
echo "  ./deploy.sh"
echo ""
