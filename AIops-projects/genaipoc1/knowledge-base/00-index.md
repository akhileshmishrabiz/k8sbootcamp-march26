# Failure Monitoring Application - Knowledge Base Index

## Quick Start

This knowledge base provides comprehensive troubleshooting information for the Failure Monitoring Application running on Kubernetes. It is designed to help AI-powered diagnostics provide accurate, specific fixes for common issues.

## Document Structure

### 1. Error Patterns and Solutions (01-error-patterns.md)
**Purpose:** Identifies specific error patterns with their exact signatures and fixes

**Covers:**
- HTTP 500 Internal Server Errors (NullPointerException, Connection Pool Exhausted, Thread Pool Exhausted)
- HTTP 503 Service Unavailable (External API Connection Refused, Circuit Breaker Open)
- Database Timeout Errors (Slow Queries, Connection Pool Waiting)
- Configuration Errors (Missing Secrets, Invalid ConfigMap Values, Feature Flags Disabled)
- Memory Issues (OutOfMemoryError, Memory Leaks)
- Deadlock Errors (Database Deadlocks)
- Cache Issues (Cache Stampede)
- Retry Storm
- Performance Issues (Slow Endpoints)
- Quick diagnostic commands

**Use When:**
- Diagnosing specific error messages in logs
- Identifying root causes from error signatures
- Finding quick fixes for known error patterns

### 2. Configuration Troubleshooting Guide (02-configuration-guide.md)
**Purpose:** Complete reference for all Kubernetes Secrets and ConfigMaps

**Covers:**
- **Kubernetes Secrets (app-secrets):**
  - payment.gateway.api.key
  - stripe.secret.key
  - jwt.secret
  - encryption.key
  - db.password
  - Additional secrets (sendgrid, AWS, OAuth, Redis)

- **Kubernetes ConfigMaps (app-config):**
  - Feature flags (payment, notifications, cache, analytics)
  - External service URLs
  - Timeout configuration (HTTP, database)
  - Connection pool configuration (database, Redis)
  - Circuit breaker configuration
  - Retry configuration
  - Rate limiting
  - Cache configuration

**Use When:**
- Application fails to start due to missing secrets
- Feature flags need to be toggled
- Connection pool needs tuning
- Timeout errors occur
- Circuit breaker needs adjustment
- Configuration validation is needed

### 3. Quick Fixes Reference (03-quick-fixes.md)
**Purpose:** One-line commands for immediate problem resolution

**Covers:**
- One-line fixes for common issues (secret missing, configmap missing, connection failures, etc.)
- Fix by error message mappings
- Diagnostic commands (health check, view configuration, check secrets)
- Emergency recovery procedures (application broken, database issues, configuration corrupted)
- Complete system reset
- Performance tuning (high traffic, low resource environments)
- Monitoring commands

**Use When:**
- Need immediate fix for known issue
- Emergency situation requiring fast recovery
- Looking for specific kubectl command
- System performance tuning needed

### 4. Kubernetes Troubleshooting (04-kubernetes-guide.md)
**Purpose:** Kubernetes-specific troubleshooting for pod, service, and cluster issues

**Covers:**
- **Pod Status Issues:**
  - CrashLoopBackOff (missing secrets, missing configmap, database not ready, application errors)
  - ImagePullBackOff
  - Pending (insufficient resources, PVC not bound)
  - OOMKilled
  - Running but not ready (startup time, health check failing)

- **Service Issues:**
  - Cannot connect to service
  - Service DNS not working

- **ConfigMap and Secret Issues:**
  - Changes not taking effect
  - Secret not visible in pod
  - ConfigMap mount issues

- **Deployment Issues:**
  - Deployment stuck
  - Rollout failed

- **Networking Issues:**
  - Cannot access from outside cluster
  - Pod-to-pod communication failing

- **Resource Management:**
  - Pod using too much memory/CPU

- **Namespace Issues**
- **CronJob Issues**
- **Persistent Volume Issues**
- **Debug Commands:**
  - Interactive shell in pod
  - Port forwarding
  - Copy files to/from pod
  - Watch resources
  - Common kubectl commands

**Use When:**
- Pods are not starting or crashing
- Service connectivity issues
- Kubernetes resource problems
- Need kubectl commands for debugging
- Network issues between pods

## Common Troubleshooting Workflows

### Workflow 1: Application Won't Start
1. Check pod status: `kubectl get pods -n failure-monitoring`
2. If CrashLoopBackOff:
   - Check logs: `kubectl logs deployment/failure-app -n failure-monitoring --tail=50`
   - Look for error signature in **01-error-patterns.md**
   - Check if secrets exist: See **02-configuration-guide.md** → Secrets section
   - Check if configmap exists: See **02-configuration-guide.md** → ConfigMaps section
   - Apply fix from **03-quick-fixes.md** → Secret Missing or ConfigMap Missing

### Workflow 2: HTTP Errors (500, 503)
1. Get error details from logs
2. Match error signature in **01-error-patterns.md** → HTTP 500/503 sections
3. Identify root cause
4. Apply fix (usually configuration change from **02-configuration-guide.md**)
5. Restart application: `kubectl rollout restart deployment/failure-app -n failure-monitoring`

### Workflow 3: Performance Issues
1. Check resource usage: `kubectl top pod -n failure-monitoring`
2. Check for active failure modes: `kubectl exec deployment/failure-app -n failure-monitoring -- curl http://localhost:8080/api/failure/status`
3. If memory/CPU high:
   - See **01-error-patterns.md** → Memory Issues or Performance Issues
   - Clear failure modes if simulated
   - Adjust resource limits from **03-quick-fixes.md** → Performance Tuning
4. If database slow:
   - See **01-error-patterns.md** → Database Timeout Errors
   - Check connection pool: **02-configuration-guide.md** → Connection Pool Configuration
   - Add indexes as suggested in **01-error-patterns.md**

### Workflow 4: Configuration Changes
1. Identify what needs to change in **02-configuration-guide.md**
2. Apply change using commands in **02-configuration-guide.md** or **03-quick-fixes.md**
3. Restart application: `kubectl rollout restart deployment/failure-app -n failure-monitoring`
4. Verify: `kubectl exec deployment/failure-app -n failure-monitoring -- curl http://localhost:8080/api/config/values`

### Workflow 5: Emergency Recovery
1. Go to **03-quick-fixes.md** → Emergency Recovery section
2. Follow appropriate procedure:
   - Application Completely Broken
   - Database Issues
   - Configuration Corrupted
   - Complete System Reset

## Quick Reference Commands

### Health Checks
```bash
# Application health
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/health

# Configuration health
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/health

# Active failures
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/failure/status
```

### View Configuration
```bash
# All config values
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/values

# Secrets (requires proper permissions)
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data}' | jq 'keys'

# ConfigMap
kubectl get configmap app-config -n failure-monitoring -o yaml
```

### Clear Failures
```bash
# Clear all simulated failures
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear-all

# Clear specific failure
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/<FAILURE_TYPE>
```

### Pod Management
```bash
# View pods
kubectl get pods -n failure-monitoring

# View logs
kubectl logs -f deployment/failure-app -n failure-monitoring --tail=50

# Restart application
kubectl rollout restart deployment/failure-app -n failure-monitoring

# Describe pod for events
kubectl describe pod -l app=failure-app -n failure-monitoring
```

## Error Message to Document Mapping

| Error Message Contains | Primary Document | Section |
|------------------------|------------------|---------|
| NullPointerException | 01-error-patterns.md | HTTP 500 → NullPointerException in PaymentService |
| Connection pool exhausted | 01-error-patterns.md | HTTP 500 → Database Connection Pool Exhausted |
| Connection refused | 01-error-patterns.md | HTTP 503 → External API Connection Refused |
| Circuit breaker OPEN | 01-error-patterns.md | HTTP 503 → Circuit Breaker Open |
| Query execution timeout | 01-error-patterns.md | Database Timeout Errors → Slow Query Execution |
| Connection is not available | 01-error-patterns.md | Database Timeout Errors → Connection Pool Waiting |
| Missing secret | 01-error-patterns.md | Configuration Errors → Missing Secret |
| Feature disabled | 01-error-patterns.md | Configuration Errors → Feature Flag Disabled |
| OutOfMemoryError | 01-error-patterns.md | Memory Issues → OutOfMemoryError |
| Memory leak | 01-error-patterns.md | Memory Issues → Memory Leak |
| Deadlock | 01-error-patterns.md | Deadlock Errors → Database Deadlock |
| Cache stampede | 01-error-patterns.md | Cache Issues → Cache Stampede |
| Retry storm | 01-error-patterns.md | Retry Storm |
| Slow endpoint | 01-error-patterns.md | Performance Issues → Slow Endpoint |
| CrashLoopBackOff | 04-kubernetes-guide.md | Pod Status Issues → CrashLoopBackOff |
| ImagePullBackOff | 04-kubernetes-guide.md | Pod Status Issues → ImagePullBackOff |
| OOMKilled | 04-kubernetes-guide.md | Pod Status Issues → OOMKilled |
| Pending pod | 04-kubernetes-guide.md | Pod Status Issues → Pending |
| Service not accessible | 04-kubernetes-guide.md | Service Issues |
| ConfigMap not mounted | 04-kubernetes-guide.md | ConfigMap and Secret Issues |

## Configuration Parameters Quick Reference

### Critical Secrets (must be present)
- `payment.gateway.api.key` - Payment gateway authentication
- `jwt.secret` - JWT token signing (security critical)
- `db.password` - Database password

### Key Configuration Parameters
- `database.pool.size` - Default: 20, Range: 5-100
- `http.connect.timeout` - Default: 5000ms, Range: 1000-60000ms
- `circuit.breaker.failure.threshold` - Default: 50%, Range: 20-80%
- `retry.max.attempts` - Default: 3, Range: 1-5
- `cache.ttl.seconds` - Default: 3600s, Range: 300-7200s

### Feature Flags
- `feature.payment.enabled` - Enable/disable payment processing
- `feature.cache.enabled` - Enable/disable caching layer
- `feature.notifications.enabled` - Enable/disable notifications

See **02-configuration-guide.md** for complete details on all parameters.

## System Architecture

### Components
1. **failure-app** (Java Spring Boot)
   - Main application with simulated failure scenarios
   - Depends on: PostgreSQL, Ollama, Qdrant
   - Exposed on port 8080

2. **postgres** (PostgreSQL Database)
   - Stores application data
   - Port 5432

3. **ollama** (LLM Service)
   - Gemma:2b model for AI analysis
   - nomic-embed-text for embeddings
   - Port 11434

4. **qdrant** (Vector Database)
   - Stores knowledge base embeddings
   - Used for RAG (Retrieval Augmented Generation)
   - Port 6333

5. **dashboard** (React Frontend + Node.js Backend)
   - Web UI for monitoring and control
   - Port 3001

6. **health-checker** (CronJob)
   - Automated health checks every 5 minutes
   - Uses RAG + AI for diagnostics

### Namespace
All components run in the `failure-monitoring` namespace.

## Failure Simulation Endpoints

The application can simulate various failure scenarios for testing:

```bash
# List available failure types
curl http://failure-app-service:8080/api/failure/types

# Trigger specific failure
curl -X POST http://failure-app-service:8080/api/failure/trigger/<FAILURE_TYPE>

# Clear specific failure
curl -X POST http://failure-app-service:8080/api/failure/clear/<FAILURE_TYPE>

# Clear all failures
curl -X POST http://failure-app-service:8080/api/failure/clear-all

# Check failure status
curl http://failure-app-service:8080/api/failure/status
```

Available failure types include:
- http_500 - Internal server error
- http_503 - Service unavailable
- timeout - Request timeout
- connection_pool_exhausted - Database connection pool full
- circuit_breaker_open - Circuit breaker triggered
- memory_leak - Memory leak simulation
- cpu_spike - CPU spike simulation
- deadlock - Database deadlock
- cache_stampede - Cache stampede scenario
- slow_query - Slow database queries
- retry_storm - Excessive retry attempts

See **01-error-patterns.md** for detailed information on each failure type.

## RAG System Usage

This knowledge base is designed to work with the RAG (Retrieval Augmented Generation) system:

1. **Document Upload**: Upload all knowledge base documents to the dashboard's "Knowledge Base" feature
2. **Embedding Generation**: Documents are converted to embeddings using nomic-embed-text model
3. **Vector Storage**: Embeddings stored in Qdrant vector database
4. **Search**: Health checker searches knowledge base for relevant context before AI analysis
5. **Enhanced Diagnosis**: AI uses retrieved context to provide specific, actionable fixes

## Support and Feedback

For issues, suggestions, or contributions to this knowledge base, please refer to the project documentation.

---

**Last Updated:** 2024
**Version:** 1.0
**Namespace:** failure-monitoring
**Platform:** Kubernetes (kind)
