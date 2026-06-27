# Failure Monitoring POC - OpenAI Edition

A proof-of-concept system for monitoring application failures with **OpenAI-powered** log analysis, running on Minikube.

## Overview

This is a streamlined version of the failure monitoring system that uses **OpenAI's GPT-4o-mini** instead of local LLM (Ollama) and RAG system (Qdrant). This approach provides:

- **No local AI models** - Uses OpenAI API instead of running models locally
- **Faster deployment** - No need to download 2GB+ of AI models
- **Better accuracy** - GPT-4o-mini provides more accurate analysis than Gemma 2B
- **Simpler architecture** - Removes Ollama and Qdrant dependencies
- **Lower resource usage** - Requires less RAM and CPU

## Architecture

```
┌─────────────────────────────────────────┐
│           Dashboard                     │
│  (React + Node.js - Port 30002)        │
│  ┌───────────┐ ┌──────────┐           │
│  │Monitoring │ │ AI Chat  │           │
│  └───────────┘ └──────────┘           │
└────┬──────────────────┬────────────────┘
     │                  │
     │                  └──────────┐
     │                             │
┌────▼────────┐              ┌────▼─────┐
│  Java App   │              │ OpenAI   │
│(Spring Boot)│              │   API    │
└────┬────────┘              └──────────┘
     │
┌────▼────────┐
│ PostgreSQL  │
└────┬────────┘
     │
┌────▼────────┐
│   Health    │
│  Checker    │
│ (CronJob)   │
└─────────────┘
```

## Components

1. **Java Application** - Spring Boot app with controllable failure injection
2. **Health Checker** - Python-based CronJob that uses OpenAI for log analysis
3. **Dashboard** - React-based web UI with OpenAI-powered chat
4. **PostgreSQL** - Database for storing health check results
5. **OpenAI API** - GPT-4o-mini for intelligent log analysis and chat

## Prerequisites

- Minikube installed and running
- kubectl configured
- Docker installed
- **OpenAI API Key** - Get one from https://platform.openai.com/api-keys
- At least 4GB RAM available for Minikube (vs 8GB for local LLM version)

## Quick Start

### 1. Set Your OpenAI API Key

```bash
export OPENAI_API_KEY='sk-proj-...'
```

**Important:** Keep your API key secure. Never commit it to version control.

### 2. Start Minikube

```bash
minikube start --memory=4096 --cpus=2
```

### 3. Deploy the POC

```bash
./deploy.sh
```

This script will:
- Build all Docker images
- Create Kubernetes namespace: `openai-monitor`
- Deploy PostgreSQL, Java app, health checker, and dashboard
- Configure OpenAI API key from environment variable

**Deployment time:** ~3-5 minutes (much faster than the 10-15 minutes for local LLM version)

### 4. Access the Dashboard

After deployment completes:

```bash
minikube service dashboard-service -n openai-monitor
```

This will open the dashboard in your browser at `http://localhost:30002`

## Features

### Failure Types

The Java application supports 10 realistic failure scenarios:

1. **HTTP 409 Conflict** - Duplicate resource creation
2. **HTTP 404 Not Found** - Entity lookup failure
3. **BusinessException** - Task object not found
4. **JWT Expired** - Okta authentication failure
5. **Invalid UUID** - Type mismatch from legacy systems
6. **DB Constraint Violation** - Database unique constraint violations
7. **Malformed JSON Request** - JSON parsing failures
8. **Transaction Failure** - JPA validation errors
9. **Downstream Timeout** - Service integration timeouts
10. **Optimistic Lock** - Concurrent update conflicts

### Dashboard Features

#### Monitoring Tab
- **Real-time Status** - Current application health status
- **Failure Controls** - Trigger/clear failures with button clicks
- **AI-Analyzed Errors** - Concise summaries powered by OpenAI
- **Health History** - Recent health check results
- **Auto-refresh** - Updates every 10 seconds

#### AI Chat Tab
- **Interactive Chat** - Talk with GPT-4o-mini about errors
- **Context-Aware** - Automatically includes recent error logs
- **Expert Analysis** - DevOps-focused troubleshooting advice
- **Conversation History** - Maintains chat context

### Health Checker

- Runs every 5 minutes (configurable)
- Fetches application logs from Kubernetes
- Uses OpenAI GPT-4o-mini to analyze failures
- Stores results in PostgreSQL
- Format: `ISSUE: [description] | FIX: [suggestion]`

## Usage Examples

### Trigger a Failure

1. Open the dashboard in your browser
2. Click "Trigger" button next to any failure type
3. Wait for the next health check (up to 5 minutes)
4. View the AI-analyzed error summary

### Manual Health Check

Run a health check immediately:

```bash
kubectl create job --from=cronjob/health-checker manual-check -n openai-monitor
```

View the job logs:

```bash
kubectl logs -l job-name=manual-check -n openai-monitor
```

### Chat with AI

1. Navigate to the **AI Chat** tab
2. Ask questions like:
   - "What's causing the current error?"
   - "How do I fix the JWT authentication failure?"
   - "What are best practices for handling timeouts?"
3. The AI will provide expert DevOps advice

### View Logs

**Dashboard:**
```bash
kubectl logs -f deployment/dashboard -n openai-monitor
```

**Java App:**
```bash
kubectl logs -f deployment/failure-app -n openai-monitor
```

**Health Checker:**
```bash
kubectl get jobs -n openai-monitor
kubectl logs job/manual-check -n openai-monitor
```

## API Endpoints

### Dashboard Backend (Port 3001)

#### Monitoring
- `GET /api/status` - Get current app status
- `GET /api/health-checks` - Get health check history
- `GET /api/health-checks/latest` - Get latest health check
- `POST /api/trigger-failure/:type` - Trigger failure
- `POST /api/clear-failure/:type` - Clear failure
- `POST /api/clear-all-failures` - Clear all failures

#### AI Chat
- `POST /api/chat` - Chat with OpenAI
  ```json
  {
    "message": "What's causing the error?",
    "history": [{"role": "user", "content": "..."}]
  }
  ```

- `POST /api/analyze-logs` - Analyze specific logs
  ```json
  {
    "logs": "Error logs here..."
  }
  ```

### Java Application (Port 8080)

- `GET /api/health` - Health check endpoint
- `POST /api/failure/trigger/{type}` - Trigger a failure
- `POST /api/failure/clear/{type}` - Clear a failure
- `POST /api/failure/clear-all` - Clear all failures
- `GET /api/failure/status` - Get current failure states

## Configuration

### Change Health Check Frequency

Edit `k8s-manifests/06-health-checker.yaml`:

```yaml
spec:
  schedule: "*/5 * * * *"  # Change to desired cron expression
```

Apply changes:

```bash
kubectl apply -f k8s-manifests/06-health-checker.yaml
```

### Update OpenAI API Key

```bash
kubectl create secret generic openai-secret \
  --from-literal=openai-api-key="sk-proj-new-key" \
  --namespace=openai-monitor \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart pods to pick up new key
kubectl rollout restart deployment/dashboard -n openai-monitor
kubectl rollout restart deployment/health-checker -n openai-monitor
```

### Change OpenAI Model

Edit `dashboard/backend/server.js` and `health-checker/health_checker.py`:

```python
# In health_checker.py
'model': 'gpt-4o'  # Use full GPT-4o for more complex analysis
```

```javascript
// In server.js
model: 'gpt-4o'  // More powerful but more expensive
```

Rebuild and redeploy:

```bash
./deploy.sh
```

## Troubleshooting

### OpenAI API Key Not Working

Check if the key is set correctly:

```bash
kubectl get secret openai-secret -n openai-monitor -o jsonpath='{.data.openai-api-key}' | base64 -d
```

View dashboard logs for API errors:

```bash
kubectl logs -f deployment/dashboard -n openai-monitor
```

### Dashboard Not Accessible

Get the dashboard URL:

```bash
minikube service dashboard-service -n openai-monitor --url
```

If using Docker Desktop on Mac/Windows, use port-forward:

```bash
kubectl port-forward -n openai-monitor service/dashboard-service 3001:3001
```

Then access: http://localhost:3001

### Health Checker Failing

Check CronJob status:

```bash
kubectl get cronjobs -n openai-monitor
kubectl get jobs -n openai-monitor
```

View job logs:

```bash
kubectl logs -l job-name=<job-name> -n openai-monitor
```

### Pods Not Starting

Check pod status:

```bash
kubectl get pods -n openai-monitor
```

View pod details:

```bash
kubectl describe pod <pod-name> -n openai-monitor
```

## Cost Considerations

### OpenAI API Pricing (as of 2024)

**GPT-4o-mini:**
- Input: $0.15 per 1M tokens
- Output: $0.60 per 1M tokens

**Estimated costs for this POC:**
- Health check (every 5 minutes): ~500 tokens per check
- Chat message: ~300-1000 tokens per message
- **Monthly estimate:** $1-5 for moderate usage

**Tips to reduce costs:**
- Adjust health check frequency (e.g., every 15 minutes)
- Use shorter log excerpts
- Cache common responses
- Set usage limits in OpenAI dashboard

## Cleanup

To remove all resources:

```bash
./cleanup.sh
```

This will delete the entire `openai-monitor` namespace.

## Project Structure

```
openai-implementation/
├── dashboard/
│   ├── backend/
│   │   ├── server.js          # OpenAI-powered backend
│   │   └── package.json
│   ├── frontend/              # React application
│   └── Dockerfile
├── health-checker/
│   ├── health_checker.py      # OpenAI-powered health checker
│   ├── requirements.txt
│   └── Dockerfile
├── java-app/                  # Spring Boot application
├── k8s-manifests/             # Kubernetes YAML files
│   ├── 00-namespace.yaml
│   ├── 01-secrets.yaml        # OpenAI API key
│   ├── 02-postgres.yaml
│   ├── 03-app-secrets.yaml
│   ├── 04-app-config.yaml
│   ├── 05-java-app.yaml
│   ├── 06-health-checker.yaml
│   └── 07-dashboard.yaml
├── deploy.sh                  # Deployment script
├── cleanup.sh                 # Cleanup script
└── README.md
```

## Comparison with Local LLM Version

| Feature | OpenAI Version | Local LLM Version |
|---------|---------------|-------------------|
| Deployment Time | 3-5 minutes | 10-15 minutes |
| RAM Required | 4GB | 8GB |
| Disk Space | ~2GB | ~10GB |
| AI Accuracy | High (GPT-4o-mini) | Medium (Gemma 2B) |
| Privacy | Data sent to OpenAI | 100% local |
| Cost | ~$1-5/month | Free |
| RAG Support | No (uses GPT context) | Yes (Qdrant) |
| Internet Required | Yes | No |

## Security Notes

This is a POC for local development. For production:

- **Store API keys securely** - Use sealed secrets or external secret managers
- Use Secrets for database credentials
- Enable authentication on all services
- Use HTTPS/TLS
- Implement proper RBAC
- Use network policies
- Scan images for vulnerabilities
- Monitor API usage and set limits

## Next Steps

- Add rate limiting for OpenAI API calls
- Implement caching for common queries
- Add support for multiple AI providers (Anthropic, etc.)
- Create alert notifications for critical failures
- Add metrics and dashboards (Prometheus/Grafana)
- Implement log aggregation (ELK stack)

## License

This is a proof-of-concept project for demonstration purposes.

## Support

For issues or questions:
- Check Kubernetes pod logs
- Verify OpenAI API key is set correctly
- Ensure Minikube has sufficient resources
- Check OpenAI API status: https://status.openai.com
