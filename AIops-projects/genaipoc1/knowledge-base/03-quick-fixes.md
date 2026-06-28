# Quick Fixes Reference

## One-Line Fixes for Common Issues

### Application Not Starting

**Error:** Pod in CrashLoopBackOff
```bash
kubectl logs deployment/failure-app -n failure-monitoring --tail=50
# Then apply appropriate fix below
```

### Secret Missing
```bash
kubectl create secret generic app-secrets -n failure-monitoring --from-literal=payment.gateway.api.key=pk_test_123 --from-literal=jwt.secret=mySecretKey123 --from-literal=stripe.secret.key=sk_test_456 --from-literal=encryption.key=aes256key32bytes --from-literal=db.password=postgres && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### ConfigMap Missing
```bash
kubectl create configmap app-config -n failure-monitoring --from-literal=feature.payment.enabled=true --from-literal=database.pool.size=20 --from-literal=payment.gateway.url=https://api.stripe.com/v1 --from-literal=http.connect.timeout=5000 --from-literal=circuit.breaker.failure.threshold=50 && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Database Connection Failed
```bash
# Check if PostgreSQL is running
kubectl get pods -n failure-monitoring -l app=postgres

# Restart PostgreSQL if needed
kubectl rollout restart deployment/postgres -n failure-monitoring

# Check connection from app
kubectl exec deployment/failure-app -n failure-monitoring -- nc -zv postgres-service 5432
```

### Payment Gateway Failing
```bash
# Update payment gateway URL
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/payment.gateway.url", "value": "https://api.stripe.com/v1"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Connection Pool Exhausted
```bash
# Increase pool size to 50
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "50"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Circuit Breaker Stuck Open
```bash
# Clear circuit breaker and increase threshold
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/circuit_breaker_open && kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/circuit.breaker.failure.threshold", "value": "70"}]'
```

### Feature Disabled Error
```bash
# Enable payment feature
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/feature.payment.enabled", "value": "true"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Out of Memory
```bash
# Increase memory limit to 1Gi
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "1Gi"}]'
```

### Memory Leak Active
```bash
# Clear memory leak failure mode
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/memory_leak
```

### CPU Spike
```bash
# Clear CPU spike failure mode
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/cpu_spike
```

### Deadlock Detected
```bash
# Clear deadlock and check PostgreSQL locks
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/deadlock && kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "SELECT * FROM pg_locks WHERE NOT granted;"
```

### Timeout Issues
```bash
# Increase all timeouts
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/http.connect.timeout", "value": "10000"}, {"op": "replace", "path": "/data/http.read.timeout", "value": "60000"}, {"op": "replace", "path": "/data/database.query.timeout", "value": "20000"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Cache Not Working
```bash
# Enable cache feature
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/feature.cache.enabled", "value": "true"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Cache Stampede
```bash
# Clear cache stampede and increase TTL
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/cache_stampede && kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/cache.ttl.seconds", "value": "7200"}]'
```

### Slow Queries
```bash
# Add database indexes
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id); CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status); CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);"
```

### Too Many Retries
```bash
# Reduce retry attempts
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/retry.max.attempts", "value": "2"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Clear All Failures
```bash
# Clear all simulated failures
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear-all
```

### Restart Everything
```bash
# Full system restart
kubectl rollout restart deployment/postgres deployment/ollama deployment/qdrant deployment/failure-app deployment/dashboard -n failure-monitoring
```

### Check Overall Health
```bash
# Get health status of all components
kubectl get pods -n failure-monitoring && kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/health && kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/config/health
```

---

## Fix by Error Message

### "Connection refused"
**Quick Fix:**
```bash
# For payment gateway
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/payment.gateway.url", "value": "https://api.stripe.com/v1"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring

# For database
kubectl get pods -n failure-monitoring -l app=postgres && kubectl rollout restart deployment/postgres -n failure-monitoring
```

### "Connection is not available"
**Quick Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "50"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### "NullPointerException"
**Quick Fix:**
```bash
# Check if secrets are configured
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data}' | jq 'keys'
# Add missing secret
kubectl patch secret app-secrets -n failure-monitoring --type='json' -p='[{"op": "add", "path": "/data/payment.gateway.api.key", "value": "'$(echo -n "pk_test_123" | base64)'"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### "OutOfMemoryError"
**Quick Fix:**
```bash
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "1Gi"}, {"op": "replace", "path": "/spec/template/spec/containers/0/resources/requests/memory", "value": "512Mi"}]'
```

### "Circuit breaker OPEN"
**Quick Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/circuit.breaker.failure.threshold", "value": "70"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### "Feature disabled"
**Quick Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/feature.payment.enabled", "value": "true"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### "Timeout"
**Quick Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.query.timeout", "value": "30000"}]' && kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### "Deadlock"
**Quick Fix:**
```bash
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/deadlock && kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE state = 'idle in transaction' AND state_change < current_timestamp - INTERVAL '10 minutes';"
```

---

## Diagnostic Commands

### Quick Health Check
```bash
kubectl get pods -n failure-monitoring && kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/health | jq .
```

### View Configuration
```bash
kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/config/values | jq .
```

### Check Secrets
```bash
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data}' | jq -r 'to_entries[] | "\(.key): \(.value | @base64d)"'
```

### Check ConfigMap
```bash
kubectl get configmap app-config -n failure-monitoring -o yaml
```

### View Logs
```bash
kubectl logs -f deployment/failure-app -n failure-monitoring --tail=50
```

### Check Resources
```bash
kubectl top pod -n failure-monitoring
```

### Check Active Failures
```bash
kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/failure/status | jq .
```

---

## Emergency Recovery

### Application Completely Broken
```bash
# Step 1: Clear all failures
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear-all 2>/dev/null || echo "App not responding"

# Step 2: Restart app
kubectl rollout restart deployment/failure-app -n failure-monitoring

# Step 3: Wait for ready
kubectl wait --for=condition=ready pod -l app=failure-app -n failure-monitoring --timeout=120s

# Step 4: Check health
kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/health
```

### Database Issues
```bash
# Restart database
kubectl rollout restart deployment/postgres -n failure-monitoring

# Wait for ready
kubectl wait --for=condition=ready pod -l app=postgres -n failure-monitoring --timeout=120s

# Restart app to reconnect
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Configuration Corrupted
```bash
# Recreate secrets
kubectl delete secret app-secrets -n failure-monitoring
kubectl create secret generic app-secrets -n failure-monitoring \
  --from-literal=payment.gateway.api.key=pk_test_123 \
  --from-literal=jwt.secret=mySecretKey123 \
  --from-literal=stripe.secret.key=sk_test_456 \
  --from-literal=encryption.key=aes256key \
  --from-literal=db.password=postgres

# Recreate configmap
kubectl delete configmap app-config -n failure-monitoring
kubectl apply -f k8s-manifests/08-configmap.yaml

# Restart app
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Complete System Reset
```bash
# Delete everything except database data
kubectl delete deployment --all -n failure-monitoring
kubectl delete service --all -n failure-monitoring
kubectl delete cronjob --all -n failure-monitoring

# Redeploy
kubectl apply -f k8s-manifests/07-secrets.yaml
kubectl apply -f k8s-manifests/08-configmap.yaml
kubectl apply -f k8s-manifests/01-postgres.yaml
kubectl apply -f k8s-manifests/02-ollama.yaml
kubectl apply -f k8s-manifests/06-qdrant.yaml
kubectl apply -f k8s-manifests/03-java-app.yaml
kubectl apply -f k8s-manifests/04-health-checker.yaml
kubectl apply -f k8s-manifests/05-dashboard.yaml

# Wait for all pods
kubectl wait --for=condition=ready pod --all -n failure-monitoring --timeout=300s
```

---

## Performance Tuning

### High Traffic Optimization
```bash
# Increase resources and scale
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[
  {"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "2Gi"},
  {"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/cpu", "value": "1000m"},
  {"op": "replace", "path": "/spec/replicas", "value": 3}
]'

# Increase connection pool
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[
  {"op": "replace", "path": "/data/database.pool.size", "value": "100"}
]'

kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Low Resource Environment
```bash
# Reduce resources
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[
  {"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "512Mi"},
  {"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/cpu", "value": "250m"}
]'

# Reduce connection pool
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[
  {"op": "replace", "path": "/data/database.pool.size", "value": "10"}
]'

kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Monitoring Commands

### Watch Pods
```bash
watch -n 2 kubectl get pods -n failure-monitoring
```

### Follow Logs
```bash
kubectl logs -f deployment/failure-app -n failure-monitoring
```

### Resource Usage
```bash
watch -n 5 kubectl top pod -n failure-monitoring
```

### Health Check Loop
```bash
while true; do kubectl exec deployment/failure-app -n failure-monitoring -- curl -s http://localhost:8080/api/health | jq '.status'; sleep 5; done
```
