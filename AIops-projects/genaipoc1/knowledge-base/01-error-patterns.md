# Error Patterns and Solutions

## HTTP 500 Internal Server Error

### Pattern: NullPointerException in PaymentService
**Error Signature:**
```
java.lang.NullPointerException
at com.example.failureapp.service.PaymentService.processPayment
```

**Root Cause:** Payment ID is null when passed from upstream service.

**Fix:**
```bash
# Check payment gateway API key in secrets
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data.payment\.gateway\.api\.key}' | base64 -d

# If missing, add it:
kubectl patch secret app-secrets -n failure-monitoring --type='json' -p='[{"op": "add", "path": "/data/payment.gateway.api.key", "value": "'$(echo -n "YOUR_API_KEY" | base64)'"}]'

# Restart the app
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Pattern: Database Connection Pool Exhausted
**Error Signature:**
```
activeConnections=50, maxConnections=50
Database connection pool exhausted
```

**Root Cause:** Too many concurrent database operations, pool size too small.

**Fix:**
```bash
# Increase pool size in ConfigMap
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "50"}]'

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Pattern: Thread Pool Exhausted
**Error Signature:**
```
Thread pool status: active=200, queued=500, rejected=25
```

**Root Cause:** Too many incoming requests, insufficient thread pool capacity.

**Fix:**
```bash
# Increase pod resources
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/cpu", "value": "1000m"}]'

# Scale horizontally
kubectl scale deployment failure-app -n failure-monitoring --replicas=3
```

---

## HTTP 503 Service Unavailable

### Pattern: External API Connection Refused
**Error Signature:**
```
Connection refused - Unable to reach payment provider
Circuit breaker status: OPEN
java.net.ConnectException: Connection timed out
```

**Root Cause:** Payment gateway API is down or unreachable.

**Fix:**
```bash
# Verify payment gateway URL in ConfigMap
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.payment\.gateway\.url}'

# Expected: https://api.stripe.com/v1

# If wrong, update it:
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/payment.gateway.url", "value": "https://api.stripe.com/v1"}]'

# Check if API key is valid
kubectl exec -n failure-monitoring deployment/failure-app -- curl -H "Authorization: Bearer YOUR_KEY" https://api.stripe.com/v1/charges

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Pattern: Circuit Breaker Open
**Error Signature:**
```
Circuit breaker 'paymentGateway' transitioned to OPEN state
Failure rate: 65% (threshold: 50%)
```

**Root Cause:** Too many failures to external service, circuit breaker protecting system.

**Fix:**
```bash
# Wait for circuit breaker to auto-recover (10 seconds default)
# Or adjust circuit breaker threshold in ConfigMap

kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/circuit.breaker.failure.threshold", "value": "70"}]'

# Clear the failure mode if simulated
curl -X POST http://failure-app-service:8080/api/failure/clear/http_503

# Restart
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Database Timeout Errors

### Pattern: Slow Query Execution
**Error Signature:**
```
Query execution timeout detected
Execution time: 30.5 seconds (timeout threshold: 30s)
Possible cause: Missing index on orders.user_id
```

**Root Cause:** Database query is slow due to missing indexes or full table scans.

**Fix:**
```bash
# Connect to PostgreSQL
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb

# Check slow queries
SELECT query, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;

# Add missing indexes
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

# Increase timeout in ConfigMap if needed
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.query.timeout", "value": "30000"}]'
```

### Pattern: Connection Pool Waiting
**Error Signature:**
```
Connection pool stats: waiting=45, active=50, idle=0, total=50
HikariPool-1 - Connection is not available
```

**Root Cause:** All connections in use, new requests waiting for available connection.

**Fix:**
```bash
# Increase connection pool size
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "100"}]'

# Check PostgreSQL max_connections
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -c "SHOW max_connections;"

# If needed, increase PostgreSQL max_connections
# Edit postgres deployment and add: -c max_connections=200

# Find long-running queries
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "SELECT pid, now() - query_start as duration, state, query FROM pg_stat_activity WHERE state != 'idle' ORDER BY duration DESC;"

# Kill stuck queries
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "SELECT pg_terminate_backend(<PID>);"
```

---

## Configuration Errors

### Pattern: Missing Secret
**Error Signature:**
```
CRITICAL: Payment Gateway API key is missing
Expected secret: payment.gateway.api.key
Source: Secret 'app-secrets' in namespace 'failure-monitoring'
java.lang.IllegalStateException: Payment gateway credentials not configured
```

**Root Cause:** Secret not created or key missing from secret.

**Fix:**
```bash
# Check if secret exists
kubectl get secret app-secrets -n failure-monitoring

# If missing, create it
kubectl create secret generic app-secrets -n failure-monitoring \
  --from-literal=payment.gateway.api.key=pk_live_abc123 \
  --from-literal=stripe.secret.key=sk_test_xyz789 \
  --from-literal=jwt.secret=mySecretJwtKey123 \
  --from-literal=encryption.key=aes256key32bytes1234567890ab

# If exists but key missing, patch it
kubectl patch secret app-secrets -n failure-monitoring --type='json' -p='[{"op": "add", "path": "/data/payment.gateway.api.key", "value": "'$(echo -n "pk_live_abc123" | base64)'"}]'

# Restart application to reload secrets
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Pattern: Invalid ConfigMap Value
**Error Signature:**
```
Database connection pool configuration error
Configured pool size: -1 (invalid)
com.zaxxer.hikari.HikariConfig$ValidationException: poolSize cannot be negative
Valid range: 1-100
```

**Root Cause:** ConfigMap contains invalid value for database pool size.

**Fix:**
```bash
# Check current value
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.database\.pool\.size}'

# Update to valid value
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "20"}]'

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

### Pattern: Feature Flag Disabled
**Error Signature:**
```
Attempted to process payment but payment feature is disabled
Feature flag 'feature.payment.enabled' = false
java.lang.UnsupportedOperationException: Payment processing is currently disabled
```

**Root Cause:** Feature flag is set to false in ConfigMap.

**Fix:**
```bash
# Enable the feature
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/feature.payment.enabled", "value": "true"}]'

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Memory Issues

### Pattern: OutOfMemoryError
**Error Signature:**
```
java.lang.OutOfMemoryError: Java heap space
Memory usage: heap=1.8GB/2GB, non-heap=256MB/512MB
FATAL: Out of memory condition detected
```

**Root Cause:** Application consuming all available heap memory.

**Fix:**
```bash
# Check current memory limits
kubectl get deployment failure-app -n failure-monitoring -o jsonpath='{.spec.template.spec.containers[0].resources.limits.memory}'

# Increase memory limits
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "1Gi"}]'

# For permanent fix, edit the deployment YAML
kubectl edit deployment failure-app -n failure-monitoring

# Change:
# resources:
#   limits:
#     memory: "1Gi"  # Increased from 512Mi

# If memory leak is suspected, clear the leak
curl -X POST http://failure-app-service:8080/api/failure/clear/memory_leak
```

### Pattern: Memory Leak
**Error Signature:**
```
MEMORY LEAK ALERT: Iteration 5, Used: 1800MB, Max: 2048MB
Objects: 100 (growing continuously)
```

**Root Cause:** Simulated memory leak allocating 10MB/second.

**Fix:**
```bash
# Clear the memory leak failure mode
curl -X POST http://failure-app-service:8080/api/failure/clear/memory_leak

# Force garbage collection
kubectl exec -n failure-monitoring deployment/failure-app -- curl -X POST http://localhost:8080/api/failure/clear/memory_leak

# Monitor memory usage
kubectl top pod -n failure-monitoring -l app=failure-app
```

---

## Deadlock Errors

### Pattern: Database Deadlock
**Error Signature:**
```
Database deadlock in order processing transaction
Transaction 1: Waiting for lock on orders table (held by transaction 2)
Transaction 2: Waiting for lock on payments table (held by transaction 1)
Deadlock victim: Transaction 1 (rollback initiated)
SQL State: 40P01
```

**Root Cause:** Circular lock dependency between transactions.

**Fix:**
```bash
# This is a code-level issue. Check transaction ordering.
# Ensure consistent lock ordering in application code.

# Find and kill stuck transactions in PostgreSQL
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb

# Check for locks
SELECT * FROM pg_locks WHERE NOT granted;

# Find blocking queries
SELECT blocked_locks.pid AS blocked_pid,
       blocking_locks.pid AS blocking_pid,
       blocked_activity.query AS blocked_statement,
       blocking_activity.query AS blocking_statement
FROM pg_locks blocked_locks
JOIN pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
JOIN pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;

# Terminate blocking query
SELECT pg_terminate_backend(<BLOCKING_PID>);

# Clear simulated deadlock
curl -X POST http://failure-app-service:8080/api/failure/clear/deadlock
```

---

## Cache Issues

### Pattern: Cache Stampede
**Error Signature:**
```
CACHE STAMPEDE DETECTED
Cache key expired: product_catalog_all
Concurrent requests: 500+
Database load spike: CPU 95%, Connections: 48/50
Response time: p50=50ms -> 5000ms
```

**Root Cause:** Many requests simultaneously trying to rebuild expired cache.

**Fix:**
```bash
# Enable cache if disabled
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/feature.cache.enabled", "value": "true"}]'

# Adjust cache TTL to prevent simultaneous expiration
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/cache.ttl.seconds", "value": "7200"}]'

# Clear the cache stampede simulation
curl -X POST http://failure-app-service:8080/api/failure/clear/cache_stampede

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Retry Storm

### Pattern: Excessive Retry Attempts
**Error Signature:**
```
RETRY STORM DETECTED
External service: payment-gateway responding with 503
Active retry attempts: 1250 concurrent requests
Retry pattern: 3 attempts with 500ms, 1000ms, 2000ms delays
System load: CPU 85%, Memory 3.2GB/4GB
```

**Root Cause:** Multiple clients retrying failed requests simultaneously.

**Fix:**
```bash
# Reduce retry attempts
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/retry.max.attempts", "value": "2"}]'

# Increase backoff time
kubectl patch configmap app-config -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/data/retry.backoff.ms", "value": "1000"}]'

# Clear the retry storm simulation
curl -X POST http://failure-app-service:8080/api/failure/clear/retry_storm

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Performance Issues

### Pattern: Slow Endpoint
**Error Signature:**
```
Endpoint /api/orders experiencing slow response times
P50 latency: 2500ms (threshold: 200ms)
P95 latency: 8500ms (threshold: 500ms)
Possible causes:
  1. N+1 query problem detected in OrderService
  2. Missing database index on orders.user_id
```

**Root Cause:** Inefficient database queries, N+1 problem, missing indexes.

**Fix:**
```bash
# Add database indexes
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);"

# Check query execution plans
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -d failuredb -c "EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123;"

# Clear slow endpoint simulation
curl -X POST http://failure-app-service:8080/api/failure/clear/slow_endpoint

# Monitor performance
kubectl top pod -n failure-monitoring -l app=failure-app
```

---

## Quick Diagnostic Commands

### Check Application Health
```bash
# Full health check
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/health

# Configuration health
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/health

# Get all config values
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/values
```

### Check Active Failures
```bash
# Get failure status
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/failure/status

# Clear all failures
curl -X POST http://failure-app-service:8080/api/failure/clear-all
```

### View Logs
```bash
# Application logs
kubectl logs -f deployment/failure-app -n failure-monitoring --tail=50

# Filter for errors
kubectl logs deployment/failure-app -n failure-monitoring | grep -i error

# Database logs
kubectl logs deployment/postgres -n failure-monitoring --tail=50
```

### Check Resources
```bash
# Pod resource usage
kubectl top pod -n failure-monitoring

# Node resource usage
kubectl top node

# Describe pod for events
kubectl describe pod -l app=failure-app -n failure-monitoring
```
