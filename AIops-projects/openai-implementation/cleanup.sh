#!/bin/bash

echo "Cleaning up OpenAI-powered Failure Monitoring POC..."
echo ""

# Delete namespace (this will delete all resources in it)
kubectl delete namespace openai-monitor

echo ""
echo "Cleanup complete!"
echo ""
echo "Note: Docker images are still available in Minikube's Docker daemon."
echo "To remove them, run:"
echo "  eval \$(minikube docker-env)"
echo "  docker rmi failure-app:latest dashboard-openai:latest health-checker:latest"
echo ""
