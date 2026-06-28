#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-rag-system}"

helm uninstall rag-system -n rag-system || true
kubectl delete namespace rag-system --ignore-not-found=true

if [[ "${DELETE_CLUSTER:-false}" == "true" ]]; then
  kind delete cluster --name "${CLUSTER_NAME}"
  echo "Deleted kind cluster '${CLUSTER_NAME}'."
else
  echo "Released Helm resources. Cluster '${CLUSTER_NAME}' kept."
  echo "To delete the cluster: DELETE_CLUSTER=true ./scripts/cleanup.sh"
fi
