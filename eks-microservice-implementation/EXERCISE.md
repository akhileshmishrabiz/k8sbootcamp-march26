# E-commerce on EKS — Deployment Exercise

End-to-end deployment steps. Run top-to-bottom.

---

## Part A — Cluster layer (`../eks/`)

### 1. EKS cluster + VPC

```bash
cd ../eks/eks-infra
terraform init
terraform apply
```

### 2. Configure kubectl

```bash
aws eks update-kubeconfig --name eks-cluster
kubectl config rename-context arn:aws:eks:ap-south-1:879381241087:cluster/eks-cluster eks
kubectl get nodes
```

### 3. AWS Load Balancer Controller

```bash
cd ../k8s-services/aws-load-balancer-controller
terraform init
terraform apply
kubectl -n kube-system get deploy aws-load-balancer-controller
```

### 4. ArgoCD

```bash
cd ../argocd
terraform init
terraform apply
kubectl -n argocd get pods
kubectl -n argocd get ingress
```

Initial admin password:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d && echo
```

UI: `https://argocd.livingdevops.org` (user `admin`).

### 5. Vault + External Secrets Operator

```bash
cd ../vault-eso
terraform init
terraform apply
kubectl -n vault get pods
kubectl -n external-secrets get pods
```

Vault UI: `https://vault.livingdevops.org` (root token: `root`).

### 6. (Optional) Monitoring stack

```bash
cd ../logging-monitoring
terraform init
terraform apply
```

---

## Part B — App layer (`eks-microservice-implementation/`)

### 7. CloudNativePG operator

```bash
cd ../../../eks-microservice-implementation/infra/cnpg-operator
terraform init
terraform apply
kubectl -n cnpg-system get pods
```

### 8. Write app passwords to Vault (stage 1)

```bash
kubectl port-forward -n vault svc/vault 8200:8200 &
cd ../vault-secrets
terraform init
terraform apply \
  -var vault_addr=http://localhost:8200 \
  -var vault_token=root \
  -var enable_eso_secrets=false
```

### 9. Create namespace + enable ESO bindings (stage 2)

```bash
kubectl create namespace ecommerce
terraform apply \
  -var vault_addr=http://localhost:8200 \
  -var vault_token=root \
  -var enable_eso_secrets=true
kubectl -n ecommerce get externalsecrets
```

### 10. ECR repos + app ingress (`infra/ms-ecom/`)

Creates the 9 ECR repos (one per image) and the shop ingress on the shared ALB. Runs **before** the image build so the repos exist.

```bash
cd ../ms-ecom
terraform init
terraform apply
terraform output ecr_repository_urls
kubectl -n ecommerce get ingress
```

### 11. Build & push images to ECR

EKS nodes are `linux/amd64`. Always set `--platform linux/amd64` when building from an Apple Silicon Mac.

**Option A — GitHub Actions (recommended).** Trigger the `E-commerce Build & Deploy` workflow (`.github/workflows/build-deploy-ms.yaml`) via the `workflow_dispatch` button in GitHub. The matrix builds and pushes all 9 images in parallel under the `:latest` and `:<sha>` tags.

Before the first run, give the workflow AWS credentials — pick **one**:

- **OIDC (recommended)** — no long-lived secrets in GitHub. Apply the terraform in `../aws-github-oidc-terraform/` to create the IAM OIDC provider + role `aws-github-oidc-march26` (already referenced in the workflow's `role-to-assume`). Make sure the role's trust policy allows this repo and the role has ECR push permissions.
- **Access keys** — quicker but less secure. In the workflow file, comment out the `role-to-assume` / `role-session-name` lines and uncomment the `aws-access-key-id` / `aws-secret-access-key` lines. Then add `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` as repo secrets in GitHub (Settings → Secrets and variables → Actions). The IAM user behind those keys needs ECR push permissions.

**Option B — Manual build from local machine.**

```bash
aws ecr get-login-password --region ap-south-1 \
  | docker login --username AWS --password-stdin \
    879381241087.dkr.ecr.ap-south-1.amazonaws.com

cd ../../   # back to eks-microservice-implementation/

# 6 backend services
for svc in product-service user-service cart-service order-service payment-service notification-service; do
  docker build --platform linux/amd64 \
    -t 879381241087.dkr.ecr.ap-south-1.amazonaws.com/ecommerce-$svc:latest \
    apps/services/$svc
  docker push 879381241087.dkr.ecr.ap-south-1.amazonaws.com/ecommerce-$svc:latest
done

# api-gateway + frontend
for svc in api-gateway frontend; do
  docker build --platform linux/amd64 \
    -t 879381241087.dkr.ecr.ap-south-1.amazonaws.com/ecommerce-$svc:latest \
    apps/$svc
  docker push 879381241087.dkr.ecr.ap-south-1.amazonaws.com/ecommerce-$svc:latest
done

# seed job image
docker build --platform linux/amd64 \
  -t 879381241087.dkr.ecr.ap-south-1.amazonaws.com/ecommerce-seed:latest \
  apps/seed-job
docker push 879381241087.dkr.ecr.ap-south-1.amazonaws.com/ecommerce-seed:latest
```

### 12. Deploy the chart via ArgoCD (final helm step)

```bash
cd argocd
kubectl apply -f application.yaml
kubectl -n argocd get application ecommerce -w
```

Wait until `SYNCED` + `HEALTHY`. Verify:

```bash
kubectl -n ecommerce get pods
kubectl -n ecommerce get ingress
```

UI: `https://shop.livingdevops.org`.

### 13. App-level observability

```bash
cd ../../infra/observability
terraform init
terraform apply
```

---

## Endpoints

| Service       | URL                                  |
|---------------|--------------------------------------|
| ArgoCD        | https://argocd.livingdevops.org      |
| Vault UI      | https://vault.livingdevops.org       |
| Grafana       | https://grafana.livingdevops.org     |
| Prometheus    | https://prometheus.livingdevops.org  |
| E-commerce UI | https://shop.livingdevops.org        |

---

## Teardown (reverse order)

```bash
kubectl -n argocd delete application ecommerce
terraform -chdir=infra/observability destroy
terraform -chdir=infra/ms-ecom destroy
terraform -chdir=infra/vault-secrets destroy
terraform -chdir=infra/cnpg-operator destroy
terraform -chdir=../eks/k8s-services/logging-monitoring destroy
terraform -chdir=../eks/k8s-services/vault-eso destroy
terraform -chdir=../eks/k8s-services/argocd destroy
terraform -chdir=../eks/k8s-services/aws-load-balancer-controller destroy
terraform -chdir=../eks/eks-infra destroy
```
