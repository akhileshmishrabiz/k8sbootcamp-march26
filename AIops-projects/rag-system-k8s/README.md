# RAG System on kind (MVP)

A local Retrieval-Augmented Generation (RAG) stack running on **kind** (Kubernetes in Docker). Upload text documents, embed them with Ollama, store vectors in Qdrant, and ask questions grounded in your knowledge base.

## What this MVP includes

| Component | Role |
|-----------|------|
| **rag-api** | Single FastAPI service: ingest → chunk → embed → retrieve → generate |
| **Ollama** | Local LLM + embedding models |
| **Qdrant** | Vector database for semantic search |
| **Frontend** | Simple web UI for upload + chat |
| **API Gateway** | nginx entry point at `localhost:8080` |

This MVP intentionally keeps the application logic in one service so you can learn the full RAG loop first. Phase 2 can split ingestion, chunking, embedding, and orchestration into separate microservices.

## Architecture

```text
Browser
   │
   ▼
api-gateway (NodePort 30080 → localhost:8080)
   ├── /api/*  → rag-api
   └── /*      → rag-frontend

rag-api
   ├── chunk text
   ├── Ollama (embeddings + generation)
   └── Qdrant (vector storage + search)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for pipeline details, data flow, and upgrade path.

For step-by-step logical flows (deploy → startup → ingest → chat → verify), see **[docs/FLOW.md](docs/FLOW.md)**.

## Prerequisites

- Docker
- [kind](https://kind.sigs.k8s.io/)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Helm 3](https://helm.sh/)

**Recommended host resources**

| Resource | Minimum | Comfortable |
|----------|---------|-------------|
| RAM | 8 GB | 16 GB |
| CPU | 4 cores | 6+ cores |
| Disk | 15 GB free | 25 GB |

First deploy downloads ~2–3 GB of Ollama models.

## Quick start

From the project root:

```bash
cd AIops-projects/rag-system-k8s
./scripts/deploy.sh
```

Watch pods until everything is ready (model pull can take several minutes):

```bash
kubectl get pods -n rag-system -w
```

Expected final state:

```text
NAME                            READY   STATUS      RESTARTS   AGE
api-gateway-...                 1/1     Running     0          ...
ollama-...                      1/1     Running     0          ...
ollama-model-pull-...           0/1     Completed   0          ...
qdrant-...                      1/1     Running     0          ...
rag-api-...                     1/1     Running     0          ...
rag-frontend-...                1/1     Running     0          ...
```

Open the UI:

- **App:** http://localhost:8080
- **Health:** http://localhost:8080/health
- **Qdrant dashboard:** http://localhost:6333/dashboard

## Try it with sample data

1. Open http://localhost:8080
2. Upload `sample-docs/k8s-networking.txt` or paste its contents
3. Click **Ingest Document**
4. Ask: *What is a Kubernetes Service?*

The answer should cite chunks from your uploaded document.

## Manual deploy steps

If you prefer step-by-step control:

```bash
# 1. Build local images
./scripts/build-images.sh

# 2. Create kind cluster
kind create cluster --config kind-config.yaml --name rag-system

# 3. Load images into kind
./scripts/load-images.sh

# 4. Install Helm chart
helm upgrade --install rag-system ./helm/rag-system \
  --namespace rag-system \
  --create-namespace
```

## API reference

Base URL: `http://localhost:8080`

### `GET /health`

Returns dependency status for Ollama and Qdrant.

### `GET /api/documents`

List indexed documents.

### `POST /api/documents`

Ingest plain text.

```json
{
  "title": "My notes",
  "content": "Kubernetes Services provide stable networking..."
}
```

### `POST /api/documents/upload`

Multipart upload for `.txt`, `.md`, and other UTF-8 text files.

### `POST /api/chat`

Ask a question against indexed documents.

```json
{
  "question": "What is a NodePort service?",
  "top_k": 5
}
```

Response includes `answer` and `sources` with document titles and similarity scores.

### `DELETE /api/documents/{doc_id}`

Remove all chunks for a document from Qdrant.

## Configuration

Edit `helm/rag-system/values.yaml`:

| Value | Default | Description |
|-------|---------|-------------|
| `ragApi.embedModel` | `nomic-embed-text` | Ollama embedding model |
| `ragApi.llmModel` | `llama3.2:3b` | Ollama chat model |
| `ragApi.chunkSize` | `500` | Characters per chunk |
| `ragApi.chunkOverlap` | `50` | Overlap between chunks |
| `ragApi.topK` | `5` | Retrieved chunks per question |
| `ollama.models` | see values | Models pulled by init Job |

After changing values:

```bash
helm upgrade rag-system ./helm/rag-system -n rag-system
```

## Project layout

```text
rag-system-k8s/
├── apps/
│   ├── rag-api/          # FastAPI RAG pipeline
│   └── frontend/         # Static UI (nginx)
├── helm/rag-system/      # Kubernetes manifests
├── scripts/              # build, load, deploy, cleanup
├── sample-docs/          # Example knowledge base file
├── docs/
│   ├── ARCHITECTURE.md   # Deep-dive design doc
│   └── FLOW.md           # Logical flow: deploy, ingest, chat, verify
└── kind-config.yaml      # kind cluster + port mappings
```

## Troubleshooting

### `rag-api` stuck in `CrashLoopBackOff`

Models may still be downloading. Check the init Job:

```bash
kubectl logs -n rag-system job/ollama-model-pull
kubectl logs -n rag-system deploy/ollama
```

### `ImagePullBackOff` for `rag-api` or `rag-frontend`

Rebuild and reload local images:

```bash
./scripts/build-images.sh
./scripts/load-images.sh
kubectl rollout restart deployment/rag-api deployment/rag-frontend -n rag-system
```

### Chat returns "I do not know"

- Confirm documents were ingested (`GET /api/documents`)
- Use questions that match uploaded content
- Increase `top_k` in the chat request

### Out of memory on Ollama

Reduce model size in `values.yaml`, for example switch to `gemma2:2b`, then redeploy and re-run the model pull Job.

## Cleanup

```bash
./scripts/cleanup.sh
DELETE_CLUSTER=true ./scripts/cleanup.sh   # also deletes kind cluster
```

## Next steps (Phase 2)

Split the monolithic `rag-api` into separate services:

1. ingestion-service
2. chunking-service
3. embedding-service
4. rag-orchestrator

Add Redis for async ingestion and PostgreSQL for document metadata. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

Educational project for the k8s bootcamp.
