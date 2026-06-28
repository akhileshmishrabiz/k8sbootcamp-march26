#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Building rag-api..."
docker build -t rag-api:local "${ROOT_DIR}/apps/rag-api"

echo "Building rag-frontend..."
docker build -t rag-frontend:local "${ROOT_DIR}/apps/frontend"

echo "Images built:"
docker images | grep -E 'rag-api|rag-frontend' || true
