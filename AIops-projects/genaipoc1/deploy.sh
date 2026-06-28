#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/kind-utils.sh
source "${ROOT_DIR}/scripts/kind-utils.sh"

echo "=========================================="
echo "Failure Monitoring POC - Deployment Script"
echo "=========================================="
echo ""

ensure_kind_cluster "${ROOT_DIR}"

echo ""
echo "Step 1: Building Docker images..."
echo "-----------------------------------"

echo "Building Java application..."
docker build -t failure-app:latest "${ROOT_DIR}/java-app"

echo "Building Health Checker..."
docker build -t health-checker:latest "${ROOT_DIR}/health-checker"

echo "Building Dashboard..."
docker build -t dashboard:latest "${ROOT_DIR}/dashboard"

prepare_ollama_image
load_images failure-app:latest health-checker:latest dashboard:latest

echo ""
echo "Step 2: Creating Kubernetes namespace..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/00-namespace.yaml"

echo ""
echo "Step 3: Deploying PostgreSQL..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/01-postgres.yaml"

echo "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n failure-monitoring --timeout=120s

echo ""
echo "Step 4: Deploying Ollama..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/02-ollama.yaml"

echo "Waiting for Ollama to be ready..."
kubectl wait --for=condition=ready pod -l app=ollama -n failure-monitoring --timeout=180s

echo "Pulling Ollama models..."
kubectl exec -n failure-monitoring deployment/ollama -- ollama pull gemma:2b
kubectl exec -n failure-monitoring deployment/ollama -- ollama pull nomic-embed-text

echo ""
echo "Step 5: Deploying Qdrant vector database..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/06-qdrant.yaml"

echo "Waiting for Qdrant to be ready..."
kubectl wait --for=condition=ready pod -l app=qdrant -n failure-monitoring --timeout=120s

echo ""
echo "Step 6: Deploying app secrets and config..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/07-secrets.yaml"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/08-configmap.yaml"

echo ""
echo "Step 7: Deploying Java application..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/03-java-app.yaml"

echo "Waiting for Java app to be ready..."
kubectl wait --for=condition=ready pod -l app=failure-app -n failure-monitoring --timeout=180s

echo ""
echo "Step 8: Deploying Health Checker CronJob..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/04-health-checker.yaml"

echo ""
echo "Step 9: Deploying Dashboard..."
echo "-----------------------------------"
kubectl apply -f "${ROOT_DIR}/k8s-manifests/05-dashboard.yaml"

echo "Waiting for Dashboard to be ready..."
kubectl wait --for=condition=ready pod -l app=dashboard -n failure-monitoring --timeout=120s

echo ""
echo "=========================================="
echo "Deployment completed successfully!"
echo "=========================================="
print_dashboard_access failure-monitoring
echo "Useful commands:"
echo "  - View all pods: kubectl get pods -n failure-monitoring"
echo "  - View logs: kubectl logs -f <pod-name> -n failure-monitoring"
echo "  - Manual health check: kubectl create job --from=cronjob/health-checker manual-check -n failure-monitoring"
echo "  - Port-forward: ./access-dashboard.sh"
echo ""
