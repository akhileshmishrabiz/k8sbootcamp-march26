# Quick Start Guide

## Prerequisites
- Minikube running
- Docker installed
- OpenAI API key

## 30-Second Setup

```bash
# 1. Set API key
export OPENAI_API_KEY='sk-proj-your-key-here'

# 2. Start Minikube (if not running)
minikube start --memory=4096 --cpus=2

# 3. Deploy everything
./deploy.sh

# 4. Access dashboard
minikube service dashboard-service -n openai-monitor
```

## What Gets Deployed

- ✅ PostgreSQL database
- ✅ Java Spring Boot app (with failure injection)
- ✅ OpenAI-powered health checker
- ✅ React dashboard with AI chat
- ✅ All in namespace: `openai-monitor`

## Test It

1. **Trigger a failure** - Click "Trigger" on any failure type
2. **Run health check** - `kubectl create job --from=cronjob/health-checker manual-check -n openai-monitor`
3. **View AI analysis** - Check dashboard for AI-generated summary
4. **Chat with AI** - Go to AI Chat tab and ask questions

## View Logs

```bash
# Dashboard
kubectl logs -f deployment/dashboard -n openai-monitor

# Java app
kubectl logs -f deployment/failure-app -n openai-monitor

# Health checker
kubectl get jobs -n openai-monitor
kubectl logs job/<job-name> -n openai-monitor
```

## Cleanup

```bash
./cleanup.sh
```

## Troubleshooting

**Problem:** Can't access dashboard  
**Solution:** `minikube service dashboard-service -n openai-monitor --url`

**Problem:** OpenAI errors  
**Solution:** Check API key with `kubectl get secret openai-secret -n openai-monitor -o yaml`

**Problem:** Pods not starting  
**Solution:** `kubectl get pods -n openai-monitor` and `kubectl describe pod <name> -n openai-monitor`

## Next Steps

- Try different failure types
- Chat with AI about errors
- Customize health check frequency
- Explore the code

For detailed documentation, see [README.md](./README.md)
