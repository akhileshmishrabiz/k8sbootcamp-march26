#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-rag-system}"

kind load docker-image rag-api:local --name "${CLUSTER_NAME}"
kind load docker-image rag-frontend:local --name "${CLUSTER_NAME}"

echo "Loaded local images into kind cluster '${CLUSTER_NAME}'."
