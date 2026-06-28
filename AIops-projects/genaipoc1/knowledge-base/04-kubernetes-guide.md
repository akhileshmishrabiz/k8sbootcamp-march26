# Kubernetes Troubleshooting for Failure Monitoring App

## Pod Status Issues

### CrashLoopBackOff
**Symptom:** Pod keeps restarting repeatedly

**Check:**
```bash
kubectl get pods -n failure-monitoring
kubectl describe pod <pod-name> -n failure-monitoring
kubectl logs <pod-name> -n failure-monitoring --previous
```

**Common Causes:**

1. **Missing Secrets**
   ```bash
   # Check if secret exists
   kubectl get secret app-secrets -n failure-monitoring

   # If missing, create it
   kubectl apply -f k8s-manifests/07-secrets.yaml
   ```

2. **Missing ConfigMap**
   ```bash
   # Check if configmap exists
   kubectl get configmap app-config -n failure-monitoring

   # If missing, create it
   kubectl apply -f k8s-manifests/08-configmap.yaml
   ```

3. **Database Not Ready**
   ```bash
   # Check if PostgreSQL is running
   kubectl get pods -n failure-monitoring -l app=postgres

   # If not ready, wait or restart
   kubectl rollout restart deployment/postgres -n failure-monitoring
   ```

4. **Application Error on Startup**
   ```bash
   # Check logs for Java exceptions
   kubectl logs deployment/failure-app -n failure-monitoring --tail=100 | grep -i exception

   # Common issues: NullPointerException, IllegalStateException
   ```

### ImagePullBackOff
**Symptom:** Cannot pull Docker image

**Check:**
```bash
kubectl describe pod <pod-name> -n failure-monitoring | grep -A 5 "Events:"
```

**Solution:**
```bash
# For locally built images on kind, ensure imagePullPolicy is Never
kubectl get deployment failure-app -n failure-monitoring -o jsonpath='{.spec.template.spec.containers[0].imagePullPolicy}'

# Should be: Never

# If not, patch it
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/imagePullPolicy", "value": "Never"}]'

# Rebuild and load image into kind
docker build -t failure-app:latest ./java-app
kind load docker-image failure-app:latest --name failure-monitoring

# Delete pod to force recreation
kubectl delete pod -l app=failure-app -n failure-monitoring
```

### Pending
**Symptom:** Pod stuck in Pending state

**Check:**
```bash
kubectl describe pod <pod-name> -n failure-monitoring
```

**Common Causes:**

1. **Insufficient Resources**
   ```bash
   # Check node resources
   kubectl describe node

   # Check resource requests
   kubectl get deployment failure-app -n failure-monitoring -o jsonpath='{.spec.template.spec.containers[0].resources}'

   # Reduce resource requests if needed
   kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/requests/memory", "value": "256Mi"}]'
   ```

2. **PersistentVolumeClaim Not Bound**
   ```bash
   # Check PVC status
   kubectl get pvc -n failure-monitoring

   # If pending, check if storage class exists
   kubectl get storageclass

   # kind includes a default storage class (standard)
   kubectl get storageclass
   ```

### OOMKilled
**Symptom:** Pod killed due to out-of-memory

**Check:**
```bash
kubectl describe pod <pod-name> -n failure-monitoring | grep -A 5 "Last State"
```

**Solution:**
```bash
# Increase memory limit
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "1Gi"}]'

# Check if memory leak is active
kubectl exec deployment/failure-app -n failure-monitoring -- curl http://localhost:8080/api/failure/status

# Clear memory leak if active
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/memory_leak
```

### Running but Not Ready
**Symptom:** Pod shows 0/1 Ready

**Check:**
```bash
kubectl describe pod <pod-name> -n failure-monitoring
kubectl logs <pod-name> -n failure-monitoring --tail=50
```

**Common Causes:**

1. **Application Startup Taking Long**
   ```bash
   # Wait for Spring Boot to fully start (can take 30-60 seconds)
   kubectl wait --for=condition=ready pod -l app=failure-app -n failure-monitoring --timeout=120s
   ```

2. **Health Check Failing**
   ```bash
   # Check if health endpoint is accessible
   kubectl exec <pod-name> -n failure-monitoring -- curl http://localhost:8080/api/health

   # If failing, check logs for errors
   kubectl logs <pod-name> -n failure-monitoring
   ```

---

## Service Issues

### Cannot Connect to Service

**Check:**
```bash
# Verify service exists
kubectl get svc -n failure-monitoring

# Check service endpoints
kubectl get endpoints -n failure-monitoring

# Describe service
kubectl describe svc failure-app-service -n failure-monitoring
```

**Common Issues:**

1. **Service Selector Doesn't Match Pod Labels**
   ```bash
   # Check service selector
   kubectl get svc failure-app-service -n failure-monitoring -o jsonpath='{.spec.selector}'

   # Check pod labels
   kubectl get pods -n failure-monitoring -l app=failure-app -o jsonpath='{.items[0].metadata.labels}'

   # They should match
   ```

2. **No Pods Backing the Service**
   ```bash
   # Check if pods are running
   kubectl get pods -n failure-monitoring -l app=failure-app

   # If no pods, check deployment
   kubectl get deployment failure-app -n failure-monitoring
   ```

3. **Wrong Port**
   ```bash
   # Check service port
   kubectl get svc failure-app-service -n failure-monitoring -o jsonpath='{.spec.ports[0].port}'

   # Should be: 8080

   # Check target port
   kubectl get svc failure-app-service -n failure-monitoring -o jsonpath='{.spec.ports[0].targetPort}'

   # Should be: 8080
   ```

### Service DNS Not Working

**Check:**
```bash
# Test DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -n failure-monitoring -- nslookup failure-app-service

# Expected: Should resolve to ClusterIP
```

**Solution:**
```bash
# Check CoreDNS
kubectl get pods -n kube-system -l k8s-app=kube-dns

# Use full service name
# Format: <service-name>.<namespace>.svc.cluster.local
# Example: failure-app-service.failure-monitoring.svc.cluster.local
```

---

## ConfigMap and Secret Issues

### Changes Not Taking Effect

**Problem:** Updated ConfigMap/Secret but app still uses old values

**Solution:**
```bash
# ConfigMaps and Secrets are not automatically reloaded
# Must restart pods

# Option 1: Rollout restart (graceful)
kubectl rollout restart deployment/failure-app -n failure-monitoring

# Option 2: Delete pods (forces recreation)
kubectl delete pod -l app=failure-app -n failure-monitoring

# Verify new pod is using new config
kubectl exec deployment/failure-app -n failure-monitoring -- curl http://localhost:8080/api/config/values
```

### Secret Not Visible in Pod

**Check:**
```bash
# Check if secret exists
kubectl get secret app-secrets -n failure-monitoring

# Check if secret is referenced in deployment
kubectl get deployment failure-app -n failure-monitoring -o yaml | grep -A 20 "env:"

# Check environment variables in pod
kubectl exec deployment/failure-app -n failure-monitoring -- env | grep -E "PAYMENT|JWT"
```

**Solution:**
```bash
# Ensure deployment has proper secretKeyRef
kubectl get deployment failure-app -n failure-monitoring -o yaml | grep -A 3 "secretKeyRef"

# If missing, reapply deployment
kubectl apply -f k8s-manifests/03-java-app.yaml
```

### ConfigMap Mount Issues

**Check:**
```bash
# Check if configmap is mounted
kubectl describe pod -l app=failure-app -n failure-monitoring | grep -A 5 "Mounts:"

# Check volumes
kubectl describe pod -l app=failure-app -n failure-monitoring | grep -A 10 "Volumes:"
```

---

## Deployment Issues

### Deployment Stuck

**Check:**
```bash
kubectl rollout status deployment/failure-app -n failure-monitoring
kubectl describe deployment failure-app -n failure-monitoring
```

**Solution:**
```bash
# Check for failed pods
kubectl get pods -n failure-monitoring -l app=failure-app

# Check replica set
kubectl get rs -n failure-monitoring

# Force new rollout
kubectl rollout restart deployment/failure-app -n failure-monitoring

# If still stuck, delete and recreate
kubectl delete deployment failure-app -n failure-monitoring
kubectl apply -f k8s-manifests/03-java-app.yaml
```

### Rollout Failed

**Check:**
```bash
kubectl rollout history deployment/failure-app -n failure-monitoring
kubectl rollout status deployment/failure-app -n failure-monitoring
```

**Rollback:**
```bash
# Rollback to previous version
kubectl rollout undo deployment/failure-app -n failure-monitoring

# Rollback to specific revision
kubectl rollout undo deployment/failure-app -n failure-monitoring --to-revision=2
```

---

## Networking Issues

### Cannot Access from Outside Cluster

**For kind:**
```bash
# NodePort mapped via kind-config.yaml
open http://localhost:30001

# Or use kubectl port-forward
kubectl port-forward svc/dashboard-service -n failure-monitoring 3001:3001

# Then access: http://localhost:3001
```

### Pod-to-Pod Communication Failing

**Check:**
```bash
# From failure-app to postgres
kubectl exec deployment/failure-app -n failure-monitoring -- nc -zv postgres-service 5432

# From failure-app to ollama
kubectl exec deployment/failure-app -n failure-monitoring -- nc -zv ollama-service 11434

# From failure-app to qdrant
kubectl exec deployment/failure-app -n failure-monitoring -- nc -zv qdrant-service 6333
```

**Solution:**
```bash
# Check if services exist
kubectl get svc -n failure-monitoring

# Check if target pods are running
kubectl get pods -n failure-monitoring

# Verify network policy (if any)
kubectl get networkpolicy -n failure-monitoring
```

---

## Resource Management

### Pod Using Too Much Memory

**Check:**
```bash
kubectl top pod -n failure-monitoring
```

**Solution:**
```bash
# Check if memory leak is active
kubectl exec deployment/failure-app -n failure-monitoring -- curl http://localhost:8080/api/failure/status | grep memory_leak

# Clear memory leak
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/memory_leak

# Increase memory limit
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/memory", "value": "1Gi"}]'
```

### Pod Using Too Much CPU

**Check:**
```bash
kubectl top pod -n failure-monitoring
```

**Solution:**
```bash
# Check if CPU spike is active
kubectl exec deployment/failure-app -n failure-monitoring -- curl http://localhost:8080/api/failure/status | grep cpu_spike

# Clear CPU spike
kubectl exec deployment/failure-app -n failure-monitoring -- curl -X POST http://localhost:8080/api/failure/clear/cpu_spike

# Increase CPU limit
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/template/spec/containers/0/resources/limits/cpu", "value": "1000m"}]'
```

---

## Namespace Issues

### Namespace Not Found

**Check:**
```bash
kubectl get namespace failure-monitoring
```

**Solution:**
```bash
# Create namespace
kubectl create namespace failure-monitoring

# Or apply from manifest
kubectl apply -f k8s-manifests/00-namespace.yaml
```

### Resources in Wrong Namespace

**Check:**
```bash
# List all resources in namespace
kubectl get all -n failure-monitoring

# List resources in default namespace (wrong)
kubectl get all -n default
```

**Solution:**
```bash
# Always specify namespace in commands
kubectl get pods -n failure-monitoring

# Or set default namespace
kubectl config set-context --current --namespace=failure-monitoring
```

---

## CronJob Issues

### Health Checker Not Running

**Check:**
```bash
# Check cronjob
kubectl get cronjob -n failure-monitoring

# Check jobs
kubectl get jobs -n failure-monitoring

# Check job history
kubectl get cronjob health-checker -n failure-monitoring -o jsonpath='{.status}'
```

**Solution:**
```bash
# Manually trigger job
kubectl create job --from=cronjob/health-checker manual-check -n failure-monitoring

# Check job logs
kubectl logs -l job-name=manual-check -n failure-monitoring

# If cronjob is suspended, resume it
kubectl patch cronjob health-checker -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/suspend", "value": false}]'
```

### Job Failing

**Check:**
```bash
# Get failed jobs
kubectl get jobs -n failure-monitoring | grep -v Complete

# Check job logs
kubectl logs job/<job-name> -n failure-monitoring

# Describe job
kubectl describe job <job-name> -n failure-monitoring
```

**Solution:**
```bash
# Delete failed job
kubectl delete job <job-name> -n failure-monitoring

# Recreate from cronjob
kubectl create job --from=cronjob/health-checker retry-check -n failure-monitoring
```

---

## Persistent Volume Issues

### PVC Pending

**Check:**
```bash
kubectl get pvc -n failure-monitoring
kubectl describe pvc <pvc-name> -n failure-monitoring
```

**Solution:**
```bash
# Check if storage class exists
kubectl get storageclass

# kind ships with a default StorageClass; verify it exists
kubectl get storageclass

# Check if PV exists
kubectl get pv
```

### Data Loss After Pod Restart

**Check:**
```bash
# Verify PVC is used
kubectl get deployment postgres -n failure-monitoring -o yaml | grep -A 5 "volumes:"

# Check if PVC is bound
kubectl get pvc -n failure-monitoring
```

**Solution:**
```bash
# Ensure deployment uses PVC
# Check k8s-manifests/01-postgres.yaml has:
# volumes:
#   - name: postgres-data
#     persistentVolumeClaim:
#       claimName: postgres-pvc
```

---

## Debug Commands

### Interactive Shell in Pod
```bash
kubectl exec -it deployment/failure-app -n failure-monitoring -- /bin/bash
```

### Run Debug Container
```bash
kubectl run -it --rm debug --image=busybox --restart=Never -n failure-monitoring -- sh
```

### Port Forward for Local Access
```bash
# Forward application port
kubectl port-forward deployment/failure-app -n failure-monitoring 8080:8080

# Forward dashboard port
kubectl port-forward svc/dashboard-service -n failure-monitoring 3001:3001

# Forward PostgreSQL port
kubectl port-forward svc/postgres-service -n failure-monitoring 5432:5432
```

### Copy Files To/From Pod
```bash
# Copy from pod
kubectl cp failure-monitoring/<pod-name>:/path/to/file ./local-file

# Copy to pod
kubectl cp ./local-file failure-monitoring/<pod-name>:/path/to/destination
```

### View All Resources
```bash
kubectl get all -n failure-monitoring
kubectl get all,configmap,secret,pvc -n failure-monitoring
```

### Watch Resources
```bash
# Watch pods
watch -n 2 kubectl get pods -n failure-monitoring

# Watch resource usage
watch -n 5 kubectl top pod -n failure-monitoring
```

---

## Common Kubectl Commands

### Get Information
```bash
# List all pods
kubectl get pods -n failure-monitoring

# List with more details
kubectl get pods -n failure-monitoring -o wide

# Describe pod
kubectl describe pod <pod-name> -n failure-monitoring

# Get YAML
kubectl get deployment failure-app -n failure-monitoring -o yaml

# Get JSON
kubectl get pod <pod-name> -n failure-monitoring -o json

# Get specific field
kubectl get deployment failure-app -n failure-monitoring -o jsonpath='{.spec.replicas}'
```

### Logs
```bash
# Follow logs
kubectl logs -f deployment/failure-app -n failure-monitoring

# Last N lines
kubectl logs --tail=50 deployment/failure-app -n failure-monitoring

# Previous container logs
kubectl logs <pod-name> -n failure-monitoring --previous

# Logs from all pods with label
kubectl logs -l app=failure-app -n failure-monitoring
```

### Delete Resources
```bash
# Delete pod (will be recreated)
kubectl delete pod <pod-name> -n failure-monitoring

# Delete deployment
kubectl delete deployment failure-app -n failure-monitoring

# Delete all jobs
kubectl delete jobs --all -n failure-monitoring

# Force delete stuck pod
kubectl delete pod <pod-name> -n failure-monitoring --grace-period=0 --force
```

### Scale
```bash
# Scale deployment
kubectl scale deployment failure-app -n failure-monitoring --replicas=3

# Autoscale
kubectl autoscale deployment failure-app -n failure-monitoring --min=2 --max=5 --cpu-percent=80
```

### Update
```bash
# Edit resource
kubectl edit deployment failure-app -n failure-monitoring

# Patch resource
kubectl patch deployment failure-app -n failure-monitoring --type='json' -p='[{"op": "replace", "path": "/spec/replicas", "value": 2}]'

# Rollout restart
kubectl rollout restart deployment/failure-app -n failure-monitoring

# Rollback
kubectl rollout undo deployment/failure-app -n failure-monitoring
```
