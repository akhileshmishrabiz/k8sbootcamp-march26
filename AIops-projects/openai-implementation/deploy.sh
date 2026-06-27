#!/bin/bash

set -e

# Ensure we're in the right directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "================================================"
echo "  OpenAI-Powered Failure Monitoring Deployment  "
echo "================================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if OpenAI API key is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo -e "${YELLOW}WARNING: OPENAI_API_KEY environment variable is not set.${NC}"
    echo "Please set it before deploying:"
    echo "  export OPENAI_API_KEY=PLACEHOLDER_API_KEY"
    echo ""
    read -p "Do you want to continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

echo -e "${GREEN}Step 1: Building Docker images...${NC}"
echo ""

# Configure Docker to use Minikube's Docker daemon
eval $(minikube docker-env)

# Build Java app
echo "Building Java application..."
docker build -t failure-app:latest ./java-app

# Build dashboard
echo "Building dashboard (with OpenAI)..."
cd dashboard
# Build frontend
cd frontend
npm install
npm run build
cd ..
# Build Docker image
docker build -f Dockerfile -t dashboard-openai:latest .
cd ..

# Build health checker
echo "Building health checker (with OpenAI)..."
cd health-checker
docker build -t health-checker:latest .
cd ..

echo ""
echo -e "${GREEN}Step 2: Creating Kubernetes namespace...${NC}"
kubectl apply -f k8s-manifests/00-namespace.yaml

echo ""
echo -e "${GREEN}Step 3: Creating secrets...${NC}"
# Update OpenAI secret if environment variable is set
if [ ! -z "$OPENAI_API_KEY" ]; then
    echo "Using OPENAI_API_KEY from environment..."
    kubectl create secret generic openai-secret \
        --from-literal=openai-api-key="$OPENAI_API_KEY" \
        --namespace=openai-monitor \
        --dry-run=client -o yaml | kubectl apply -f -
else
    echo "Using placeholder API key (update k8s-manifests/01-secrets.yaml with your key)"
    kubectl apply -f k8s-manifests/01-secrets.yaml
fi

kubectl apply -f k8s-manifests/03-app-secrets.yaml

echo ""
echo -e "${GREEN}Step 4: Deploying PostgreSQL...${NC}"
kubectl apply -f k8s-manifests/02-postgres.yaml
echo "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n openai-monitor --timeout=120s

echo ""
echo -e "${GREEN}Step 5: Creating ConfigMaps...${NC}"
kubectl apply -f k8s-manifests/04-app-config.yaml

echo ""
echo -e "${GREEN}Step 6: Deploying Java application...${NC}"
kubectl apply -f k8s-manifests/05-java-app.yaml
echo "Waiting for Java app to be ready..."
kubectl wait --for=condition=ready pod -l app=failure-app -n openai-monitor --timeout=120s

echo ""
echo -e "${GREEN}Step 7: Deploying health checker...${NC}"
kubectl apply -f k8s-manifests/06-health-checker.yaml

echo ""
echo -e "${GREEN}Step 8: Deploying dashboard...${NC}"
kubectl apply -f k8s-manifests/07-dashboard.yaml
echo "Waiting for dashboard to be ready..."
kubectl wait --for=condition=ready pod -l app=dashboard -n openai-monitor --timeout=120s

echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN}  Deployment Complete!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "All services are running. To access the dashboard:"
echo ""
echo "  minikube service dashboard-service -n openai-monitor"
echo ""
echo "Or get the URL with:"
echo ""
echo "  minikube service dashboard-service -n openai-monitor --url"
echo ""
echo "To view logs:"
echo "  kubectl logs -f deployment/dashboard -n openai-monitor"
echo "  kubectl logs -f deployment/failure-app -n openai-monitor"
echo ""
echo "To manually trigger a health check:"
echo "  kubectl create job --from=cronjob/health-checker manual-check -n openai-monitor"
echo ""
echo -e "${YELLOW}Note: Make sure your OpenAI API key is set correctly in the secret!${NC}"
echo ""
