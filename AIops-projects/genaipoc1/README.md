# Failure Monitoring POC

A Kubernetes-based proof of concept for **AIOps-style failure monitoring**: a Spring Boot app that simulates realistic production failures, a health checker that analyzes logs with local AI, and a React dashboard for control, chat, and RAG.

Runs on **kind** (Kubernetes in Docker) with **Ollama** (local LLM) and **Qdrant** (vector store). No external AI APIs.

---

## What Is What

| Component | Path | Purpose |
|-----------|------|---------|
| **Java app** | `java-app/` | Spring Boot e-commerce simulator. Injects realistic failures (HTTP 409, JWT expiry, DB errors, etc.) and reads config from Kubernetes Secrets/ConfigMaps. |
| **Health checker** | `health-checker/` | Python CronJob (every 5 min). Hits the app, pulls logs from K8s, asks Ollama for a one-line summary, stores results in PostgreSQL. |
| **Dashboard** | `dashboard/` | React UI + Node.js API (`server.js`). Monitoring tab, AI chat, and knowledge-base upload. |
| **K8s manifests** | `k8s-manifests/` | Full stack manifests (`00`–`08`). |
| **Postgres init** | `postgres-init/init.sql` | Schema for `health_checks` and `app_status` tables. |
| **Knowledge base** | `knowledge-base/` | Sample `.md` docs to upload into the RAG system. Demo content for the AI, not app source. |
| **Deploy scripts** | `setup-kind.sh`, `deploy.sh`, `start.sh` | Create kind cluster and deploy the stack. |
| **kind config** | `kind-config.yaml` | Cluster definition with dashboard NodePort mapped to `localhost:30001`. |
| **Utilities** | `cleanup.sh`, `access-dashboard.sh` | Tear down resources (and optionally the kind cluster); port-forward dashboard. |

---

## Architecture

```
Dashboard (React + Node.js, port 30001)
    ├── Monitoring ──► Java App (Spring Boot)
    ├── AI Chat ──────► Ollama (gemma:2b)
    └── Documents ────► Qdrant ◄── nomic-embed-text (Ollama)

Health Checker (CronJob) ──► Java App logs + Ollama ──► PostgreSQL
```

---

## Prerequisites

- [kind](https://kind.sigs.k8s.io/), kubectl, Docker
- 8 GB+ RAM for Docker, ~10 GB disk (downloads ~2 GB of Ollama models)

```bash
# One-time cluster setup (or deploy.sh creates it automatically)
./setup-kind.sh
```

---

## Quick Start

```bash
./deploy.sh
# Dashboard: http://localhost:30001
```

Or use the interactive wrapper:

```bash
./start.sh
```

`deploy.sh` creates the kind cluster if needed, builds images, loads them with `kind load docker-image`, then deploys: namespace, secrets, configmap, PostgreSQL, Ollama (+ gemma:2b, nomic-embed-text), Qdrant, Java app, health checker CronJob, dashboard.

Initial run takes **10–15 minutes** (model downloads).

---

## Using the Dashboard

1. Open `http://localhost:30001` (or `./access-dashboard.sh` to port-forward).
2. **Failure Controls** — trigger a failure type, wait for the next health check (up to 5 min), or run a manual check:

```bash
kubectl create job --from=cronjob/health-checker manual-check -n failure-monitoring
```

3. **AI Chat** — ask about errors or uploaded docs (uses RAG when documents exist).
4. **Knowledge Base** — upload `.txt`/`.md` or paste content; chunks are embedded and stored in Qdrant.

To upload the bundled sample docs, copy content from `knowledge-base/` into the Knowledge Base tab.

---

## Failure Types (Java App)

Realistic production-style failures with detailed stack traces in logs:

| ID | Scenario |
|----|----------|
| `conflict_409` | HTTP 409 — duplicate user |
| `not_found_404` | HTTP 404 — entity missing |
| `jwt_expired` | 401 — expired JWT / auth failure |
| `business_exception` | Business logic error |
| `invalid_uuid` | Invalid UUID in request |
| `db_constraint_violation` | Database constraint violation |
| `malformed_request` | Malformed JSON body |
| `transaction_failure` | Transaction commit failure |
| `downstream_timeout` | Downstream service timeout |
| `optimistic_lock` | Optimistic locking / concurrent update |

API: `POST /api/failure/trigger/{type}`, `POST /api/failure/clear/{type}`, `POST /api/failure/clear-all`, `GET /api/failure/status`

The Java app also simulates a full e-commerce domain (users, orders, payments, inventory) with Resilience4j circuit breakers, caching, and config-driven failures via `app-secrets` and `app-config`.

---

## Project Layout

```
genaipoc1/
├── java-app/                 # Spring Boot failure simulator
├── health-checker/           # Python CronJob
├── dashboard/
│   ├── frontend/             # React UI
│   ├── backend/server.js     # Ollama + RAG backend
│   └── Dockerfile
├── k8s-manifests/
│   ├── 00-namespace.yaml
│   ├── 01-postgres.yaml
│   ├── 02-ollama.yaml
│   ├── 03-java-app.yaml      # Requires 07 + 08
│   ├── 04-health-checker.yaml
│   ├── 05-dashboard.yaml
│   ├── 06-qdrant.yaml
│   ├── 07-secrets.yaml       # app-secrets
│   └── 08-configmap.yaml     # app-config
├── postgres-init/init.sql
├── knowledge-base/           # Sample RAG documents
├── kind-config.yaml
├── scripts/kind-utils.sh
├── setup-kind.sh
├── deploy.sh
├── start.sh
├── cleanup.sh
└── access-dashboard.sh
```

---

## API Reference

### Java app (8080)

- `GET /api/health`, `GET /api/test`
- `POST /api/failure/trigger/{type}`, `POST /api/failure/clear/{type}`, `POST /api/failure/clear-all`
- `GET /api/failure/status`
- Order/user/inventory endpoints under `/api/orders`, `/api/users`, etc.

### Dashboard backend (3001)

- `GET /api/status`, `/api/health-checks`, `/api/health-checks/latest`
- `POST /api/trigger-failure/:type`, `/api/clear-failure/:type`, `/api/clear-all-failures`
- `POST /api/chat` — AI chat (body: `{ message, history }`)
- `POST /api/documents/upload`, `GET /api/documents`, `DELETE /api/documents/all` — RAG

---

## Useful Commands

```bash
# Cluster
kind get clusters
kubectl config use-context kind-failure-monitoring

# Pods
kubectl get pods -n failure-monitoring

# Logs
kubectl logs -f deployment/failure-app -n failure-monitoring
kubectl logs -f deployment/dashboard -n failure-monitoring

# Dashboard
open http://localhost:30001
./access-dashboard.sh

# Manual health check
kubectl create job --from=cronjob/health-checker manual-check -n failure-monitoring

# Ollama models
kubectl exec -n failure-monitoring deployment/ollama -- ollama list
kubectl exec -n failure-monitoring deployment/ollama -- ollama pull gemma:2b

# Rebuild and reload after code changes
docker build -t dashboard:latest ./dashboard
kind load docker-image dashboard:latest --name failure-monitoring
kubectl rollout restart deployment/dashboard -n failure-monitoring

# Cleanup
./cleanup.sh
DELETE_KIND_CLUSTER=1 ./cleanup.sh
```

---

## Troubleshooting

**Java app pod won't start** — apply secrets and configmap before the app:

```bash
kubectl apply -f k8s-manifests/07-secrets.yaml -f k8s-manifests/08-configmap.yaml
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

**Ollama model missing** — `kubectl exec -n failure-monitoring deployment/ollama -- ollama pull gemma:2b`

**RAG not working** — confirm Qdrant pod is running and `nomic-embed-text` is pulled; check dashboard logs.

**Dashboard unreachable** — open `http://localhost:30001` or use `./access-dashboard.sh`.

**Image pull errors for custom images** — rebuild and load into kind:

```bash
kind load docker-image failure-app:latest dashboard:latest health-checker:latest --name failure-monitoring
```

**kind cluster missing** — run `./setup-kind.sh` or `./deploy.sh`.

---

## Security Note

This is a **local POC only**. Manifests use placeholder secrets and plain-text credentials. Do not use as-is in production.

---

## License

Proof-of-concept for demonstration and learning.
