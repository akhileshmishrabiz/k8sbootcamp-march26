# E-commerce Helm Chart (ECR Version)

E-commerce microservices platform with 6 services, PostgreSQL, Redis, and RabbitMQ. This version pulls images from Amazon ECR.

## Architecture

```
┌─────────────┐     ┌─────────────┐
│  Frontend   │────▶│ API Gateway │
└─────────────┘     └──────┬──────┘
                          │
      ┌───────────────────┼───────────────────┐
      ▼                   ▼                   ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Product   │     │    User     │     │    Cart     │
│   Service   │     │   Service   │     │   Service   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
      │                   │                   │
      ▼                   ▼                   ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ PostgreSQL  │     │ PostgreSQL  │     │    Redis    │
│  (products) │     │   (users)   │     │             │
└─────────────┘     └─────────────┘     └─────────────┘

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│    Order    │     │   Payment   │     │Notification │
│   Service   │     │   Service   │     │   Service   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
      │                   │                   │
      ▼                   ▼                   ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ PostgreSQL  │     │ PostgreSQL  │     │  RabbitMQ   │
│  (orders)   │     │ (payments)  │     │             │
└─────────────┘     └─────────────┘     └─────────────┘
```

## Prerequisites

- Kind cluster running
- Helm 3.x
- kubectl configured
- AWS CLI configured with ECR access
- Docker images pushed to ECR

## ECR Repositories

The following ECR repositories should exist in `ap-south-1`:

| Repository | Description |
|------------|-------------|
| `ecommerce-product-service` | Product catalog service |
| `ecommerce-user-service` | User authentication service |
| `ecommerce-cart-service` | Shopping cart service |
| `ecommerce-order-service` | Order management service |
| `ecommerce-payment-service` | Payment processing service |
| `ecommerce-notification-service` | Notification service |
| `ecommerce-api-gateway` | NGINX API gateway |
| `ecommerce-frontend` | React frontend |

**ECR Registry:** `879381241087.dkr.ecr.ap-south-1.amazonaws.com`

## Quick Start

### Step 1: Create Kind Cluster

```bash
kind create cluster --name ecom-ms
```

### Step 2: Create ECR Image Pull Secret

Kind needs credentials to pull images from ECR. Create the secret using your AWS credentials:

```bash
# Login to ECR and get the password
ECR_PASSWORD=$(aws ecr get-login-password --region ap-south-1)

# Create the namespace first
kubectl create namespace ecommerce

# Create the docker-registry secret
kubectl create secret docker-registry ecr-registry-secret \
  --namespace ecommerce \
  --docker-server=879381241087.dkr.ecr.ap-south-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password="${ECR_PASSWORD}"
```

**Important:** ECR tokens expire after 12 hours. You'll need to refresh the secret periodically. See the "Refreshing ECR Credentials" section below.

### Step 3: Deploy with Helm

```bash
# Basic installation
helm install ecommerce ./helm/ecommerce --namespace ecommerce

# Or with custom values
helm install ecommerce ./helm/ecommerce --namespace ecommerce -f my-values.yaml

# Dry run (preview what will be deployed)
helm install ecommerce ./helm/ecommerce --namespace ecommerce --dry-run --debug
```

### Step 4: Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n ecommerce

# Check services
kubectl get svc -n ecommerce

# Watch deployment progress
kubectl get pods -n ecommerce -w

# Check if images are being pulled correctly
kubectl describe pod -n ecommerce -l app=product-service
```

### Step 5: Access the Application

```bash
# Frontend (NodePort)
# Access at: http://localhost:30000

# API Gateway (NodePort)
# Access at: http://localhost:30080

# Or use port-forward
kubectl port-forward svc/frontend -n ecommerce 3000:80
kubectl port-forward svc/api-gateway -n ecommerce 8080:80
```

## Refreshing ECR Credentials

ECR tokens expire after 12 hours. To refresh the secret:

```bash
# Delete the old secret
kubectl delete secret ecr-registry-secret -n ecommerce

# Get new ECR password
ECR_PASSWORD=$(aws ecr get-login-password --region ap-south-1)

# Create new secret
kubectl create secret docker-registry ecr-registry-secret \
  --namespace ecommerce \
  --docker-server=879381241087.dkr.ecr.ap-south-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password="${ECR_PASSWORD}"

# Restart deployments to use new credentials (optional, only if pods are failing)
kubectl rollout restart deployment -n ecommerce
```

### Automated Credential Refresh (Optional)

For production, consider using one of these approaches:
1. **CronJob**: Create a Kubernetes CronJob to refresh credentials periodically
2. **External Secrets Operator**: Use ESO with AWS Secrets Manager
3. **IAM Roles for Service Accounts (IRSA)**: If running on EKS

## Configuration

### ECR Settings

| Parameter | Description | Default |
|-----------|-------------|---------|
| `ecr.enabled` | Enable ECR image pulling | `true` |
| `ecr.accountId` | AWS Account ID | `879381241087` |
| `ecr.region` | AWS Region | `ap-south-1` |
| `ecr.secretName` | Image pull secret name | `ecr-registry-secret` |

### Override Values

Create a custom values file:

```yaml
# my-values.yaml
ecr:
  accountId: "123456789012"  # Your AWS Account ID
  region: "ap-south-1"

services:
  productService:
    replicas: 3
    tag: v1.2.0  # Specific image tag

frontend:
  tag: v2.0.0
```

Deploy with overrides:

```bash
helm install ecommerce ./helm/ecommerce -n ecommerce -f my-values.yaml
```

### Key Configuration Options

| Parameter | Description | Default |
|-----------|-------------|---------|
| `global.namespace` | Kubernetes namespace | `ecommerce` |
| `global.environment` | Environment name | `development` |
| `global.imagePullPolicy` | Image pull policy | `IfNotPresent` |
| `database.user` | Database username | `ecommerce_user` |
| `database.password` | Database password | `secure_password_123` |
| `redis.enabled` | Enable Redis | `true` |
| `rabbitmq.enabled` | Enable RabbitMQ | `true` |
| `services.*.replicas` | Service replicas | `1` |
| `services.*.tag` | Image tag | `latest` |

## Common Operations

### Upgrade

```bash
# Upgrade with new values
helm upgrade ecommerce ./helm/ecommerce -n ecommerce -f my-values.yaml

# Upgrade and wait for rollout
helm upgrade ecommerce ./helm/ecommerce -n ecommerce --wait --timeout 5m
```

### Rollback

```bash
# View history
helm history ecommerce -n ecommerce

# Rollback to previous release
helm rollback ecommerce -n ecommerce

# Rollback to specific revision
helm rollback ecommerce 2 -n ecommerce
```

### Uninstall

```bash
# Uninstall release
helm uninstall ecommerce -n ecommerce

# Delete namespace (also removes the secret)
kubectl delete namespace ecommerce
```

### Debug

```bash
# Template rendering (see generated manifests)
helm template ecommerce ./helm/ecommerce -n ecommerce

# Get release status
helm status ecommerce -n ecommerce

# Check image pull errors
kubectl describe pod <pod-name> -n ecommerce | grep -A5 "Events"

# Check secret exists
kubectl get secret ecr-registry-secret -n ecommerce
```

## Troubleshooting

### ImagePullBackOff Error

If pods show `ImagePullBackOff` or `ErrImagePull`:

```bash
# Check the secret exists
kubectl get secret ecr-registry-secret -n ecommerce

# Check pod events for details
kubectl describe pod <pod-name> -n ecommerce

# Verify you can pull manually
aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 879381241087.dkr.ecr.ap-south-1.amazonaws.com

# Refresh the secret (tokens expire after 12 hours)
kubectl delete secret ecr-registry-secret -n ecommerce
ECR_PASSWORD=$(aws ecr get-login-password --region ap-south-1)
kubectl create secret docker-registry ecr-registry-secret \
  --namespace ecommerce \
  --docker-server=879381241087.dkr.ecr.ap-south-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password="${ECR_PASSWORD}"
```

### Pods not starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n ecommerce

# Check logs
kubectl logs <pod-name> -n ecommerce

# Check events
kubectl get events -n ecommerce --sort-by='.lastTimestamp'
```

### Database connection issues

```bash
# Check PostgreSQL pods
kubectl get pods -n ecommerce -l app=postgres

# Test connection from a service pod
kubectl exec -it <service-pod> -n ecommerce -- nc -zv postgres-products 5432
```

## Chart Structure

```
helm/ecommerce/
├── Chart.yaml           # Chart metadata
├── values.yaml          # Default values (ECR configured)
├── README.md            # This file
└── templates/
    ├── _helpers.tpl     # Template helpers (includes ECR helpers)
    ├── namespace.yaml   # Namespace
    ├── secrets.yaml     # Secrets
    ├── postgres.yaml    # PostgreSQL deployments
    ├── redis.yaml       # Redis deployment
    ├── rabbitmq.yaml    # RabbitMQ deployment
    ├── product-service.yaml
    ├── user-service.yaml
    ├── cart-service.yaml
    ├── order-service.yaml
    ├── payment-service.yaml
    ├── notification-service.yaml
    ├── api-gateway.yaml
    └── frontend.yaml
```

## Seed Data

After deployment, seed the database:

```bash
# Port forward to API gateway
kubectl port-forward svc/api-gateway -n ecommerce 3030:80

# Verify connectivity
curl http://localhost:3030/health

# Run seed script
export API_URL="http://localhost:3030"
bash seed-data.sh
```
