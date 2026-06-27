# Quick Setup Guide

## Step-by-Step Installation

### 1. Get OpenAI API Key

1. Go to https://platform.openai.com/api-keys
2. Create a new secret key
3. Copy the key (starts with `sk-proj-...`)

### 2. Set Environment Variable

**Linux/macOS:**
```bash
export OPENAI_API_KEY='sk-proj-your-key-here'
```

**Windows (PowerShell):**
```powershell
$env:OPENAI_API_KEY='sk-proj-your-key-here'
```

**Verify it's set:**
```bash
echo $OPENAI_API_KEY
```

### 3. Start Minikube

```bash
# Start Minikube with sufficient resources
minikube start --memory=4096 --cpus=2

# Verify Minikube is running
minikube status
```

### 4. Deploy Everything

```bash
# Make scripts executable (if not already)
chmod +x deploy.sh cleanup.sh

# Run deployment
./deploy.sh
```

Wait for deployment to complete (~3-5 minutes).

### 5. Access the Dashboard

**Option 1: Automatic (recommended)**
```bash
minikube service dashboard-service -n openai-monitor
```

**Option 2: Get URL manually**
```bash
minikube service dashboard-service -n openai-monitor --url
```

Then open the URL in your browser (e.g., `http://192.168.49.2:30002`)

**Option 3: Port forwarding (if above doesn't work)**
```bash
kubectl port-forward -n openai-monitor service/dashboard-service 3001:3001
```

Then open: http://localhost:3001

### 6. Test the System

1. **Trigger a failure:**
   - In the dashboard, click "Trigger" next to "HTTP 500 Error"
   - Wait 5 minutes for the health check, OR run manual check:
   ```bash
   kubectl create job --from=cronjob/health-checker manual-check -n openai-monitor
   ```

2. **Check health status:**
   - The dashboard will show "Unhealthy" status
   - View the AI-generated error summary

3. **Chat with AI:**
   - Go to "AI Chat" tab
   - Ask: "What's the current error?"
   - The AI will analyze and explain

4. **Clear the failure:**
   - Click "Clear" button or "Clear All Failures"

### 7. View Logs (Optional)

**Dashboard logs:**
```bash
kubectl logs -f deployment/dashboard -n openai-monitor
```

**Java app logs:**
```bash
kubectl logs -f deployment/failure-app -n openai-monitor
```

**Health checker logs:**
```bash
# List jobs
kubectl get jobs -n openai-monitor

# View specific job logs
kubectl logs job/health-checker-xxxxx -n openai-monitor
```

## Troubleshooting

### "OPENAI_API_KEY not set" error

Make sure you exported the environment variable before running deploy.sh:

```bash
export OPENAI_API_KEY='sk-proj-your-key-here'
./deploy.sh
```

### Can't access dashboard

Check if pods are running:

```bash
kubectl get pods -n openai-monitor
```

All pods should show `Running` status. If not:

```bash
kubectl describe pod <pod-name> -n openai-monitor
```

### OpenAI API errors

View dashboard logs:

```bash
kubectl logs -f deployment/dashboard -n openai-monitor
```

Common errors:
- **401 Unauthorized:** Invalid API key
- **429 Too Many Requests:** Rate limit exceeded
- **500 Server Error:** OpenAI service issue

Update the secret if needed:

```bash
kubectl create secret generic openai-secret \
  --from-literal=openai-api-key="sk-proj-new-key" \
  --namespace=openai-monitor \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl rollout restart deployment/dashboard -n openai-monitor
```

### Health checker not running

Check CronJob:

```bash
kubectl get cronjobs -n openai-monitor
```

Manually trigger a health check:

```bash
kubectl create job --from=cronjob/health-checker manual-check -n openai-monitor
```

## Cleanup

When done testing:

```bash
./cleanup.sh
```

This removes all Kubernetes resources but keeps Minikube running.

To also stop Minikube:

```bash
minikube stop
```

## Next Steps

- Explore different failure types
- Customize health check frequency
- Try different OpenAI models
- Integrate with your own applications

## Need Help?

Check the main [README.md](./README.md) for detailed documentation.
