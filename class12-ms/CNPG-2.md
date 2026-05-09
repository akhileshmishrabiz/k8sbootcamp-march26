# CloudNativePG — Complete Setup Guide

> Local Kubernetes (kind) · Primary + Replicas · Backup to MinIO · PITR · Best Practices

---

## Prerequisites

| Tool | Purpose |
|------|---------|
| Docker | Container runtime |
| kind | Local Kubernetes cluster |
| kubectl | Kubernetes CLI |
| helm | Install CNPG operator |
| MinIO CLI (mc) | Interact with local S3 |

---

## Step 1 — Create a kind Cluster

```bash
kind create cluster --name cnpg-lab

kubectl cluster-info --context kind-cnpg-lab
kubectl get nodes
```

> **Best Practice:** Always name your kind cluster. Avoids collisions when running multiple clusters locally.

---

## Step 2 — Install the CNPG Operator

```bash
helm repo add cnpg https://cloudnative-pg.github.io/charts
helm repo update

helm install cnpg \
  --namespace cnpg-system \
  --create-namespace \
  cnpg/cloudnative-pg

# Verify
kubectl get pods -n cnpg-system
```

Expected output:
```
NAME                                   READY   STATUS    RESTARTS
cnpg-cloudnative-pg-xxxxxxxxx-xxxxx    1/1     Running   0
```

> **Best Practice:** Always install the operator in a dedicated namespace (`cnpg-system`). Keep operator concerns separate from application namespaces.

---

## Step 3 — Deploy MinIO (Local S3 for Backup)

```bash
kubectl create namespace minio

kubectl apply -n minio -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: minio
spec:
  replicas: 1
  selector:
    matchLabels:
      app: minio
  template:
    metadata:
      labels:
        app: minio
    spec:
      containers:
      - name: minio
        image: minio/minio:latest
        args: ["server", "/data", "--console-address", ":9001"]
        env:
        - name: MINIO_ROOT_USER
          value: minioadmin
        - name: MINIO_ROOT_PASSWORD
          value: minioadmin
        ports:
        - containerPort: 9000
        - containerPort: 9001
---
apiVersion: v1
kind: Service
metadata:
  name: minio
spec:
  selector:
    app: minio
  ports:
  - name: api
    port: 9000
  - name: console
    port: 9001
EOF
```

Create the backup bucket:

```bash
kubectl port-forward -n minio svc/minio 9000:9000 &

mc alias set local http://localhost:9000 minioadmin minioadmin
mc mb local/cnpg-backups
mc ls local
```

> **Best Practice:** Use a dedicated bucket per cluster. Naming convention: `cnpg-backups-<cluster-name>-<env>`.

---

## Step 4 — Create Secrets

```bash
# MinIO credentials
kubectl create secret generic minio-creds \
  --from-literal=ACCESS_KEY_ID=minioadmin \
  --from-literal=ACCESS_SECRET_KEY=minioadmin \
  -n default

# Postgres superuser
kubectl create secret generic postgres-superuser \
  --from-literal=username=postgres \
  --from-literal=password=StrongPassword123! \
  -n default
```

> ⚠️ **Warning:** Never store plaintext passwords in YAML committed to Git. Use `kubectl create secret` or a secrets manager (Vault, AWS Secrets Manager) in production.

---

## Step 5 — Create the Postgres Cluster

`cluster.yaml`

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: postgres-demo
  namespace: default
spec:
  instances: 3
  imageName: ghcr.io/cloudnative-pg/postgresql:16

  postgresql:
    parameters:
      max_connections: "200"
      shared_buffers: "256MB"
      wal_level: replica

  superuserSecret:
    name: postgres-superuser

  storage:
    size: 2Gi
    storageClass: standard

  backup:
    retentionPolicy: 7d
    barmanObjectStore:
      destinationPath: s3://cnpg-backups/
      endpointURL: http://minio.minio.svc:9000
      s3Credentials:
        accessKeyId:
          name: minio-creds
          key: ACCESS_KEY_ID
        secretAccessKey:
          name: minio-creds
          key: ACCESS_SECRET_KEY
      wal:
        compression: gzip
        maxParallel: 2
```

```bash
kubectl apply -f cluster.yaml
```

> **Best Practice:** Pin `imageName` to a specific version tag (e.g., `:16.3`). Never use `:latest` — upgrades should be deliberate and tested.

---

## Step 6 — Watch the Cluster Come Up

```bash
kubectl get cluster postgres-demo -w
kubectl get pods -w
```

Three pods will appear in order:

```
postgres-demo-1   1/1   Running   # PRIMARY
postgres-demo-2   1/1   Running   # REPLICA
postgres-demo-3   1/1   Running   # REPLICA
```

### Services Created Automatically

| Service | Purpose |
|---------|---------|
| `postgres-demo-rw` | Write endpoint — always points to primary |
| `postgres-demo-ro` | Read endpoint — points to replicas only |
| `postgres-demo-r` | Points to all instances |

> **Best Practice:** Always connect your app to `-rw` for writes and `-ro` for reads. Never connect directly to a pod IP. Service endpoints update automatically on failover.

---

## Step 7 — Verify Replication

```bash
# Check primary
kubectl exec -it postgres-demo-1 -- psql -U postgres -c "SELECT pg_is_in_recovery();"
# Returns: f  (false = primary)

# Check replica
kubectl exec -it postgres-demo-2 -- psql -U postgres -c "SELECT pg_is_in_recovery();"
# Returns: t  (true = replica)

# Check replication lag
kubectl exec -it postgres-demo-1 -- psql -U postgres -c "SELECT * FROM pg_stat_replication;"
```

Write on primary, read on replica:

```bash
# Write on primary
kubectl exec -it postgres-demo-1 -- psql -U postgres -c \
  "CREATE TABLE test (id serial, val text); INSERT INTO test(val) VALUES ('hello');"

# Read on replica
kubectl exec -it postgres-demo-2 -- psql -U postgres -c "SELECT * FROM test;"
```

---

## Step 8 — Backup

### Manual Backup

`backup.yaml`

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Backup
metadata:
  name: postgres-demo-backup-01
  namespace: default
spec:
  cluster:
    name: postgres-demo
  method: barmanObjectStore
```

```bash
kubectl apply -f backup.yaml
kubectl get backup postgres-demo-backup-01 -w

# Verify in MinIO
mc ls local/cnpg-backups/
```

### Scheduled Backup

`scheduled-backup.yaml`

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: ScheduledBackup
metadata:
  name: postgres-demo-scheduled
  namespace: default
spec:
  schedule: "0 2 * * *"    # Every day at 2am
  backupOwnerReference: self
  cluster:
    name: postgres-demo
  immediate: true           # Take one backup immediately on apply
```

```bash
kubectl apply -f scheduled-backup.yaml
kubectl get backups
```

> **Best Practice:** Always set `immediate: true`. The first backup runs right away — without it you wait until the next cron trigger.

---

## Step 9 — Test Automatic Failover

```bash
# Find current primary
kubectl get cluster postgres-demo -o jsonpath='{.status.currentPrimary}'

# Delete the primary pod
kubectl delete pod postgres-demo-1

# Watch failover happen
kubectl get cluster postgres-demo -w
```

Within 10-30 seconds:

```
STATUS: Failing over
STATUS: Promoting replica
STATUS: Cluster in healthy state
```

```bash
# Confirm new primary
kubectl get cluster postgres-demo -o jsonpath='{.status.currentPrimary}'
```

> ⚠️ **Warning:** The old primary comes back as a replica automatically. Do not manually promote pods — CNPG handles fencing and resyncing.

---

## Step 10 — Point-in-Time Recovery (PITR)

Restore to any second in the past using base backups + WAL archives.

`recovery-cluster.yaml`

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: postgres-demo-recovered
  namespace: default
spec:
  instances: 1

  bootstrap:
    recovery:
      source: postgres-demo
      recoveryTarget:
        targetTime: "2024-12-01T14:59:00Z"

  externalClusters:
  - name: postgres-demo
    barmanObjectStore:
      destinationPath: s3://cnpg-backups/
      endpointURL: http://minio.minio.svc:9000
      s3Credentials:
        accessKeyId:
          name: minio-creds
          key: ACCESS_KEY_ID
        secretAccessKey:
          name: minio-creds
          key: ACCESS_SECRET_KEY

  storage:
    size: 2Gi
```

```bash
kubectl apply -f recovery-cluster.yaml
kubectl get cluster postgres-demo-recovered -w
```

> **Best Practice:** PITR only works because of continuous WAL archiving. Never disable the `wal:` section in your cluster backup config.

---

## Production Best Practices Summary

### Cluster
- Run 3 instances minimum
- Pin Postgres version — never `:latest`
- Set CPU and memory resource limits
- Use dedicated namespaces per environment

### Storage
- Use fast StorageClass (gp3 on EKS)
- Size PVCs generously — expansion can require downtime
- Each instance gets its own PVC

### Backup
- Daily base backups minimum — schedule at off-peak hours
- Enable WAL archiving — required for PITR
- Set `retentionPolicy: 30d` for production
- Test your restores regularly — an untested backup is not a backup
- Dedicated bucket per cluster

### Security
- Never commit secrets to Git
- Use strong passwords (20+ characters)
- TLS between instances is on by default — never disable it
- Create app-specific users with minimal privileges

### Monitoring
- Install Prometheus + Grafana — CNPG exposes native metrics
- Alert on replication lag > 30s
- Alert on WAL archiving failures
- Alert if last successful backup > 25 hours old

---

## Quick Reference

| Task | Command |
|------|---------|
| Check cluster status | `kubectl get cluster postgres-demo` |
| List all backups | `kubectl get backups` |
| Get primary pod | `kubectl get cluster postgres-demo -o jsonpath='{.status.currentPrimary}'` |
| Connect to primary | `kubectl exec -it postgres-demo-1 -- psql -U postgres` |
| Check replication lag | `kubectl exec -it postgres-demo-1 -- psql -U postgres -c 'SELECT * FROM pg_stat_replication;'` |
| Check WAL archiving | `kubectl exec -it postgres-demo-1 -- psql -U postgres -c 'SELECT * FROM pg_stat_archiver;'` |
| Watch operator logs | `kubectl logs -n cnpg-system deploy/cnpg-cloudnative-pg -f` |
| Describe cluster events | `kubectl describe cluster postgres-demo` |

---

*LivingDevOps · Advanced DevOps Bootcamp · livingdevops.com*