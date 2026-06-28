# Configuration Troubleshooting Guide

## Kubernetes Secrets (app-secrets)

### Overview
Secrets store sensitive credentials that the application needs to function.

**Location:** `k8s-manifests/07-secrets.yaml`

**Namespace:** `failure-monitoring`

**Secret Name:** `app-secrets`

### Required Secret Keys

#### payment.gateway.api.key
**Purpose:** Authenticates with external payment gateway (Stripe)
**Used By:** PaymentService, OrderController
**Format:** String, typically starts with `pk_live_` or `pk_test_`
**Missing Symptom:** HTTP 500 errors with "Payment gateway credentials not configured"

**Check:**
```bash
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data.payment\.gateway\.api\.key}' | base64 -d
```

**Fix:**
```bash
kubectl patch secret app-secrets -n failure-monitoring --type='json' \
  -p='[{"op": "add", "path": "/data/payment.gateway.api.key", "value": "'$(echo -n "pk_live_your_key_here" | base64)'"}]'
```

#### stripe.secret.key
**Purpose:** Stripe secret key for payment processing
**Used By:** PaymentService
**Format:** String, starts with `sk_live_` or `sk_test_`
**Missing Symptom:** Payment transactions fail with authentication error

**Check:**
```bash
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data.stripe\.secret\.key}' | base64 -d
```

**Fix:**
```bash
kubectl patch secret app-secrets -n failure-monitoring --type='json' \
  -p='[{"op": "add", "path": "/data/stripe.secret.key", "value": "'$(echo -n "sk_test_your_secret" | base64)'"}]'
```

#### jwt.secret
**Purpose:** JWT token signing and verification
**Used By:** SecurityConfig, AuthenticationFilter
**Format:** Long random string (min 32 characters)
**Missing Symptom:** CRITICAL - User authentication fails, security compromised

**Check:**
```bash
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data.jwt\.secret}' | base64 -d
```

**Fix:**
```bash
# Generate secure random JWT secret
JWT_SECRET=$(openssl rand -base64 32)
kubectl patch secret app-secrets -n failure-monitoring --type='json' \
  -p='[{"op": "add", "path": "/data/jwt.secret", "value": "'$(echo -n "$JWT_SECRET" | base64)'"}]'
```

#### encryption.key
**Purpose:** AES encryption for sensitive data at rest
**Used By:** EncryptionService
**Format:** 32-byte key for AES-256
**Missing Symptom:** WARNING - Data stored unencrypted

**Check:**
```bash
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data.encryption\.key}' | base64 -d
```

**Fix:**
```bash
kubectl patch secret app-secrets -n failure-monitoring --type='json' \
  -p='[{"op": "add", "path": "/data/encryption.key", "value": "'$(echo -n "aes256-encryption-key-32bytes!!" | base64)'"}]'
```

#### db.password
**Purpose:** PostgreSQL database password
**Used By:** HikariCP DataSource
**Format:** String
**Missing Symptom:** Application fails to start, database connection error

**Check:**
```bash
kubectl get secret app-secrets -n failure-monitoring -o jsonpath='{.data.db\.password}' | base64 -d
```

**Fix:**
```bash
kubectl patch secret app-secrets -n failure-monitoring --type='json' \
  -p='[{"op": "add", "path": "/data/db.password", "value": "'$(echo -n "postgres" | base64)'"}]'
```

### Recreate All Secrets
If secrets are corrupted or completely missing:
```bash
kubectl delete secret app-secrets -n failure-monitoring

kubectl create secret generic app-secrets -n failure-monitoring \
  --from-literal=payment.gateway.api.key=pk_live_abc123xyz789 \
  --from-literal=stripe.secret.key=sk_test_51AbCdEfGhIjKlMnO \
  --from-literal=jwt.secret=myS3cr3tJwtK3yTh4tSh0uldB3L0ng4ndR4nd0m \
  --from-literal=encryption.key=aes256-encryption-key-32bytes!! \
  --from-literal=db.password=postgres \
  --from-literal=sendgrid.api.key=SG.1234567890abcdefghijklmnopqrstuvwxyz \
  --from-literal=aws.access.key=AKIAIOSFODNN7EXAMPLE \
  --from-literal=aws.secret.key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY \
  --from-literal=oauth.client.secret=oauth_secret_1234567890 \
  --from-literal=redis.password=redis_secure_password_123

kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Kubernetes ConfigMap (app-config)

### Overview
ConfigMap stores non-sensitive application configuration.

**Location:** `k8s-manifests/08-configmap.yaml`

**Namespace:** `failure-monitoring`

**ConfigMap Name:** `app-config`

### Feature Flags

#### feature.payment.enabled
**Purpose:** Controls whether payment processing is enabled
**Valid Values:** `true` or `false`
**Default:** `true`
**Impact:** If false, all payment operations return 503 Service Unavailable

**Check:**
```bash
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.feature\.payment\.enabled}'
```

**Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/feature.payment.enabled", "value": "true"}]'
```

#### feature.notifications.enabled
**Purpose:** Controls email/notification system
**Valid Values:** `true` or `false`
**Default:** `true`
**Impact:** If false, no notifications sent

#### feature.cache.enabled
**Purpose:** Controls caching layer (Redis/Caffeine)
**Valid Values:** `true` or `false`
**Default:** `true`
**Impact:** If false, every request hits database

#### feature.analytics.enabled
**Purpose:** Controls analytics tracking
**Valid Values:** `true` or `false`
**Default:** `false`
**Impact:** If false, no analytics data collected

### External Service URLs

#### payment.gateway.url
**Purpose:** Payment gateway API endpoint
**Valid Values:** HTTPS URL
**Default:** `https://api.stripe.com/v1`
**Impact:** Wrong URL causes all payments to fail with connection refused

**Check:**
```bash
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.payment\.gateway\.url}'
```

**Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/payment.gateway.url", "value": "https://api.stripe.com/v1"}]'
```

#### notification.service.url
**Purpose:** Email service endpoint (SendGrid)
**Valid Values:** HTTPS URL
**Default:** `https://api.sendgrid.com/v3`

#### analytics.service.url
**Purpose:** Analytics service endpoint
**Valid Values:** HTTPS URL
**Default:** `https://analytics.example.com/api`

### Timeout Configuration

#### http.connect.timeout
**Purpose:** HTTP connection timeout in milliseconds
**Valid Range:** 1000-60000 (1s to 60s)
**Default:** 5000 (5 seconds)
**Recommended:** 5000-10000
**Impact:** Too low = frequent timeouts, too high = slow error detection

**Check:**
```bash
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.http\.connect\.timeout}'
```

**Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/http.connect.timeout", "value": "5000"}]'
```

#### http.read.timeout
**Purpose:** HTTP read timeout in milliseconds
**Valid Range:** 5000-120000 (5s to 120s)
**Default:** 30000 (30 seconds)
**Recommended:** 30000-60000
**Impact:** Too low = requests timeout before response, too high = slow requests hang

#### database.query.timeout
**Purpose:** Database query execution timeout
**Valid Range:** 1000-60000
**Default:** 10000 (10 seconds)
**Recommended:** 10000-30000
**Impact:** Too low = valid queries timeout, too high = slow queries not killed

**Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/database.query.timeout", "value": "15000"}]'
```

### Connection Pool Configuration

#### database.pool.size
**Purpose:** HikariCP maximum connection pool size
**Valid Range:** 5-100
**Default:** 20
**Recommended:** 20-50 for small apps, 50-100 for high traffic
**Impact:** Too low = connection exhaustion, too high = database overload

**Symptoms of Wrong Value:**
- **Too Low:** "Connection is not available" errors
- **Too High:** PostgreSQL "too many connections" errors

**Check:**
```bash
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.database\.pool\.size}'
```

**Fix:**
```bash
# For low traffic
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "20"}]'

# For high traffic
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/database.pool.size", "value": "50"}]'
```

**Important:** Ensure PostgreSQL max_connections is higher than pool size:
```bash
kubectl exec -it deployment/postgres -n failure-monitoring -- psql -U postgres -c "SHOW max_connections;"
# Should be at least 2x your pool size
```

#### database.pool.max.wait.ms
**Purpose:** Maximum time to wait for connection from pool
**Valid Range:** 10000-60000
**Default:** 30000 (30 seconds)
**Recommended:** 30000
**Impact:** Too low = premature failures, too high = requests hang

#### redis.pool.max.connections
**Purpose:** Maximum Redis connections
**Valid Range:** 10-100
**Default:** 50
**Recommended:** 50

### Circuit Breaker Configuration

#### circuit.breaker.failure.threshold
**Purpose:** Percentage of failures before opening circuit
**Valid Range:** 20-80 (percentage)
**Default:** 50 (50%)
**Recommended:** 50-70
**Impact:** Too low = circuit opens too easily, too high = too many failures before protection

**Check:**
```bash
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.circuit\.breaker\.failure\.threshold}'
```

**Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/circuit.breaker.failure.threshold", "value": "60"}]'
```

#### circuit.breaker.timeout.seconds
**Purpose:** How long circuit stays open before trying again
**Valid Range:** 5-60 seconds
**Default:** 10 seconds
**Recommended:** 10-30
**Impact:** Too low = premature retry, too high = long downtime

#### circuit.breaker.half.open.requests
**Purpose:** Number of test requests in half-open state
**Valid Range:** 1-10
**Default:** 3
**Recommended:** 3-5

### Retry Configuration

#### retry.max.attempts
**Purpose:** Maximum number of retry attempts
**Valid Range:** 1-5
**Default:** 3
**Recommended:** 2-3
**Impact:** Too high = retry storms, too low = give up too quickly

**Check:**
```bash
kubectl get configmap app-config -n failure-monitoring -o jsonpath='{.data.retry\.max\.attempts}'
```

**Fix:**
```bash
kubectl patch configmap app-config -n failure-monitoring --type='json' \
  -p='[{"op": "replace", "path": "/data/retry.max.attempts", "value": "3"}]'
```

#### retry.backoff.ms
**Purpose:** Delay between retry attempts in milliseconds
**Valid Range:** 100-5000
**Default:** 500
**Recommended:** 500-1000
**Impact:** Too low = hammering service, too high = slow recovery

### Rate Limiting

#### ratelimit.requests.per.minute
**Purpose:** Maximum requests allowed per minute per user
**Valid Range:** 10-1000
**Default:** 100
**Recommended:** 100-500

#### ratelimit.burst.size
**Purpose:** Allowed burst above rate limit
**Valid Range:** 10-100
**Default:** 20
**Recommended:** 20-50

### Cache Configuration

#### cache.ttl.seconds
**Purpose:** Cache time-to-live in seconds
**Valid Range:** 300-7200 (5 min to 2 hours)
**Default:** 3600 (1 hour)
**Recommended:** 1800-3600
**Impact:** Too low = cache ineffective, too high = stale data

---

## Configuration Validation

### Check All Configuration Health
```bash
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/health
```

**Expected Response:**
```json
{
  "status": "HEALTHY",
  "secrets": {
    "paymentApiKey": "CONFIGURED",
    "jwtSecret": "CONFIGURED",
    "stripeSecretKey": "CONFIGURED",
    "encryptionKey": "CONFIGURED"
  },
  "configuration": {
    "paymentGatewayUrl": "https://api.stripe.com/v1",
    "connectTimeout": "5000ms",
    "readTimeout": "30000ms",
    "dbPoolSize": 20
  },
  "features": {
    "paymentEnabled": true,
    "notificationsEnabled": true
  },
  "errors": [],
  "warnings": []
}
```

### View All Configuration Values
```bash
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/values
```

### Simulate Configuration Errors
```bash
# Test missing secret
curl -X POST http://failure-app-service:8080/api/config/simulate/missing-secret

# Test invalid config
curl -X POST http://failure-app-service:8080/api/config/simulate/invalid-config

# Test config not mounted
curl -X POST http://failure-app-service:8080/api/config/simulate/config-not-mounted

# Test feature disabled
curl -X POST http://failure-app-service:8080/api/config/simulate/feature-disabled
```

---

## Complete ConfigMap Recreation

If ConfigMap is corrupted:
```bash
kubectl delete configmap app-config -n failure-monitoring

kubectl create configmap app-config -n failure-monitoring \
  --from-literal=app.name="Failure Monitoring App" \
  --from-literal=app.version="2.0.0" \
  --from-literal=app.environment="production" \
  --from-literal=feature.payment.enabled="true" \
  --from-literal=feature.notifications.enabled="true" \
  --from-literal=feature.analytics.enabled="false" \
  --from-literal=feature.cache.enabled="true" \
  --from-literal=feature.ratelimit.enabled="true" \
  --from-literal=payment.gateway.url="https://api.stripe.com/v1" \
  --from-literal=notification.service.url="https://api.sendgrid.com/v3" \
  --from-literal=analytics.service.url="https://analytics.example.com/api" \
  --from-literal=http.connect.timeout="5000" \
  --from-literal=http.read.timeout="30000" \
  --from-literal=database.query.timeout="10000" \
  --from-literal=database.pool.size="20" \
  --from-literal=database.pool.max.wait.ms="30000" \
  --from-literal=redis.pool.max.connections="50" \
  --from-literal=circuit.breaker.failure.threshold="50" \
  --from-literal=circuit.breaker.timeout.seconds="10" \
  --from-literal=circuit.breaker.half.open.requests="3" \
  --from-literal=retry.max.attempts="3" \
  --from-literal=retry.backoff.ms="500" \
  --from-literal=ratelimit.requests.per.minute="100" \
  --from-literal=ratelimit.burst.size="20" \
  --from-literal=cache.ttl.seconds="3600"

kubectl rollout restart deployment/failure-app -n failure-monitoring
```

---

## Troubleshooting Steps

### Configuration Not Taking Effect

**Problem:** Updated ConfigMap but application still uses old values

**Solution:**
```bash
# ConfigMaps are not automatically reloaded
# Must restart the application pod
kubectl rollout restart deployment/failure-app -n failure-monitoring

# Verify new pod is running
kubectl get pods -n failure-monitoring -l app=failure-app

# Check if new config is loaded
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/config/values
```

### Secret Not Mounted

**Problem:** Application can't read secrets

**Check:**
```bash
# Verify secret exists
kubectl get secret app-secrets -n failure-monitoring

# Check if secret is mounted in pod
kubectl describe pod -l app=failure-app -n failure-monitoring | grep -A 5 "Mounts:"

# Check environment variables in pod
kubectl exec deployment/failure-app -n failure-monitoring -- env | grep -E "PAYMENT|JWT|ENCRYPTION"
```

**Solution:**
```bash
# Secrets are injected as environment variables via deployment spec
# Check k8s-manifests/03-java-app.yaml has proper valueFrom configuration
kubectl get deployment failure-app -n failure-monitoring -o yaml | grep -A 5 "valueFrom"

# If missing, reapply deployment
kubectl apply -f k8s-manifests/03-java-app.yaml
```

### Restart After Configuration Change

**Always restart after changing:**
- Secrets
- ConfigMaps
- Deployment environment variables

```bash
# Graceful restart
kubectl rollout restart deployment/failure-app -n failure-monitoring

# Wait for rollout to complete
kubectl rollout status deployment/failure-app -n failure-monitoring

# Verify health
kubectl exec -n failure-monitoring deployment/failure-app -- curl http://localhost:8080/api/health
```
