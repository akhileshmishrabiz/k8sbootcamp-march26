#!/usr/bin/env bash
# Shared helpers for kind cluster setup and local image loading.

CLUSTER_NAME="${CLUSTER_NAME:-failure-monitoring}"
DASHBOARD_URL="http://localhost:30001"

require_kind() {
  if ! command -v kind >/dev/null 2>&1; then
    echo "Error: kind is not installed. See https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
    exit 1
  fi
}

require_kubectl() {
  if ! command -v kubectl >/dev/null 2>&1; then
    echo "Error: kubectl is not installed."
    exit 1
  fi
}

ensure_kind_cluster() {
  local root_dir="$1"
  require_kind
  require_kubectl

  if kind get clusters 2>/dev/null | grep -qx "${CLUSTER_NAME}"; then
    echo "Kind cluster '${CLUSTER_NAME}' already exists."
    kubectl cluster-info --context "kind-${CLUSTER_NAME}" >/dev/null
    return 0
  fi

  echo "Creating kind cluster '${CLUSTER_NAME}'..."
  kind create cluster --config "${root_dir}/kind-config.yaml" --name "${CLUSTER_NAME}"
  kubectl cluster-info --context "kind-${CLUSTER_NAME}"
}

load_images() {
  local images=("$@")
  require_kind

  echo ""
  echo "Loading images into kind cluster '${CLUSTER_NAME}'..."
  for image in "${images[@]}"; do
    echo "  → ${image}"
    kind load docker-image "${image}" --name "${CLUSTER_NAME}"
  done
}

prepare_ollama_image() {
  echo "Pulling Ollama image (used by in-cluster deployment)..."
  docker pull ollama/ollama:latest
  load_images "ollama/ollama:latest"
}

print_dashboard_access() {
  local namespace="${1:-failure-monitoring}"
  echo ""
  echo "Access the dashboard at:"
  echo "  ${DASHBOARD_URL}"
  echo ""
  echo "If the URL is unreachable, port-forward instead:"
  echo "  kubectl port-forward -n ${namespace} service/dashboard-service 3001:3001"
  echo "  → http://localhost:3001"
}
