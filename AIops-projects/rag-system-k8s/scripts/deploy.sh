#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLUSTER_NAME="${CLUSTER_NAME:-rag-system}"

"${ROOT_DIR}/scripts/build-images.sh"

if ! kind get clusters | grep -qx "${CLUSTER_NAME}"; then
  echo "Creating kind cluster '${CLUSTER_NAME}'..."
  kind create cluster --config "${ROOT_DIR}/kind-config.yaml" --name "${CLUSTER_NAME}"
else
  echo "Kind cluster '${CLUSTER_NAME}' already exists."
fi

"${ROOT_DIR}/scripts/load-images.sh"

echo "Installing Helm chart..."
helm upgrade --install rag-system "${ROOT_DIR}/helm/rag-system" \
  --namespace rag-system \
  --create-namespace

echo
echo "Deployment started. Watch progress with:"
echo "  kubectl get pods -n rag-system -w"
echo
echo "Once ready, open:"
echo "  UI:     http://localhost:8080"
echo "  Health: http://localhost:8080/health"
echo "  Qdrant: http://localhost:6333/dashboard"
