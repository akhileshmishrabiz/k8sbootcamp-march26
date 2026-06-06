# Networking & Service Mesh — Hands-On Exercise

A step-by-step lab for the ecommerce stack in `k8s-networking-test/`. You will:

1. Deploy the app, then **NetworkPolicies first** (Cilium + Hubble)
2. Run connectivity simulations and **visualize allow/drop flows in Hubble UI**
3. Apply **4 policy tweaks** — break things, observe, restore
4. Add **Linkerd service mesh** — visualize in the Viz dashboard, tap live traffic
5. Apply **4 mesh policy tweaks** — auth, timeouts, retries, identity rules
6. **Simulate traffic** and watch metrics change in both UIs
7. Run a **canary deployment** on `order-service` with `TrafficSplit`

**Estimated time:** 2–3 hours (first deploy ~20 min; rest is interactive).

**Related docs:** [instruction.md](./instruction.md) · [NETWORK-POLICY-README.md](./NETWORK-POLICY-README.md) · [SERVICE-MESH-README.md](./SERVICE-MESH-README.md) · [NETWORKING-MESH-MAP.md](./NETWORKING-MESH-MAP.md)

---

## Prerequisites

| Tool | Check |
|------|-------|
| Docker | `docker info` |
| Kind | `kind version` |
| kubectl | `kubectl version --client` |
| Helm | `helm version` |
| curl, python3 | `curl --version` |

From the repo root:

```bash
cd k8s-networking-test
chmod +x deploy-all.sh helm-cnpg-vault-deploy.sh networking/*.sh networking/linkerd/*.sh simulate-traffic*.sh
```

---

## Access URLs (after deploy)

| Service | URL | Use in exercise |
|---------|-----|-----------------|
| Frontend | http://localhost:4000 | UI flows (login, cart, checkout) |
| API Gateway | http://localhost:9080 | Health + API tests |
| Hubble UI | http://localhost:12000 | NetworkPolicy flow visualization |
| Linkerd Viz | http://127.0.0.1:50750 | Mesh metrics, topology, tap |
| Vault UI | http://localhost:8200 | (token: `root`) — not needed for this lab |

**Demo login:** `demo@example.com` / `password123` (or register a new user).

**Reset policies to known-good state** (run between scenarios):

```bash
./networking/apply-network-policies.sh
```

**Reset mesh policies:**

```bash
./networking/linkerd/apply-all.sh
```

---

# Part A — Network Policies (deploy first)

> **Goal:** Understand L3/L4 firewall rules. See allowed and dropped packets in Hubble before touching the service mesh.

## A.1 — Deploy app + Cilium (no mesh yet)

Deploy the application stack with Cilium CNI (required — Kind's default CNI does **not** enforce NetworkPolicy):

```bash
./helm-cnpg-vault-deploy.sh
```

Wait until pods are ready:

```bash
kubectl config use-context kind-ecommerce-networking
kubectl wait --for=condition=ready pod -l app=api-gateway -n ecommerce --timeout=300s
curl -s http://localhost:9080/health
```

Open http://localhost:4000 — browse products. The app works **without** policies (open cluster).

## A.2 — Apply NetworkPolicies

```bash
./networking/apply-network-policies.sh
kubectl get networkpolicies -n ecommerce
```

Policies applied (in order):

| File | What it does |
|------|--------------|
| `00-default-deny.yaml` | Deny all ingress/egress; allow DNS :53 |
| `01-api-gateway.yaml` | Gateway ↔ all 6 microservices |
| `02-services.yaml` | Per-service allow rules (cart↔product, order↔cart, …) |
| `03-infrastructure.yaml` | Redis, RabbitMQ, CNPG databases |
| `04-cross-namespace.yaml` | Frontend, Vault/ESO egress, Prometheus scrape |
| `05-seed-job.yaml` | Seed job → api-gateway |
| `07-kubernetes-api.yaml` | K8s API egress for operators |

**Checkpoint:** App still works. Login → add to cart → checkout should succeed.

```bash
curl -s http://localhost:9080/health
# Browse http://localhost:4000 — add a product to cart
```

If add-to-cart fails on a fresh deploy, re-apply service policies:

```bash
kubectl apply -f networking/policies/02-services.yaml
```

---

## A.3 — Baseline connectivity simulation

Run these from a terminal. Each test validates a rule in `networking/CONNECTIVITY.md`.

### Should succeed ✅

```bash
# Cart → Redis (cart persistence)
kubectl exec -n ecommerce deploy/cart-service -- nc -zv redis 6379

# Cart → Product (add to cart needs product lookup)
kubectl exec -n ecommerce deploy/cart-service -- wget -qO- --timeout=3 \
  http://product-service:8001/health

# Order → RabbitMQ (order events)
kubectl exec -n ecommerce deploy/order-service -- nc -zv rabbitmq 5672

# Order → Cart (checkout reads cart)
kubectl exec -n ecommerce deploy/order-service -- wget -qO- --timeout=3 \
  http://cart-service:8003/health
```

### Should fail ❌ (security working correctly)

```bash
# Cart must NOT reach payments DB directly
kubectl exec -n ecommerce deploy/cart-service -- nc -zv payments-rw 5432 -w 2

# Notification must NOT reach users DB
kubectl exec -n ecommerce deploy/notification-service -- nc -zv users-rw 5432 -w 2
```

| From | To | Expected |
|------|-----|----------|
| cart-service | redis:6379 | ✅ |
| cart-service | product-service:8001 | ✅ |
| cart-service | payments-rw:5432 | ❌ timeout |
| notification-service | users-rw:5432 | ❌ timeout |

---

## A.4 — Visualize with Cilium Hubble

Hubble shows **every connection** at L3/L4 — green = forwarded, red = dropped.

### Open Hubble UI

```bash
kubectl wait --for=condition=ready pod -l k8s-app=hubble-ui -n kube-system --timeout=3m
cilium hubble ui
# Opens http://localhost:12000
```

### Hubble UI walkthrough

1. **Namespace filter:** set to `ecommerce`
2. **Run baseline tests** (A.3) in another terminal
3. **Observe flows:**
   - `cart-service → redis:6379` — **FORWARDED** (green)
   - `cart-service → product-service:8001` — **FORWARDED**
   - `cart-service → payments-rw:5432` — **DROPPED** (red)
4. Click a dropped flow → inspect **verdict**, source/destination labels, port

### CLI alternative (no UI)

```bash
# Watch live TCP flows
hubble observe --namespace ecommerce --protocol tcp --follow

# Filter drops only
hubble observe --namespace ecommerce --verdict DROPPED --follow
```

### UI-driven traffic (see flows while browsing)

With Hubble UI open and filter `ecommerce`:

1. Open http://localhost:4000
2. Login → open a product → **Add to cart**
3. Watch flows: `frontend → api-gateway`, `api-gateway → cart-service`, `cart-service → product-service`, `cart-service → redis`

**What to notice:** Hubble sees IP/port/labels. It does **not** see HTTP paths or mTLS — that comes later with Linkerd.

---

## A.5 — Policy tweak lab (4 scenarios)

Run **one scenario at a time**. Start each from a clean state:

```bash
./networking/apply-network-policies.sh
```

For each scenario: **break → observe in UI/CLI/Hubble → diagnose → fix → verify**.

---

### Tweak 1 — Block cart → product (break add to cart)

**Traffic path:**

```
browser → api-gateway → cart-service → product-service:8001
                                    → redis:6379
```

**Break:** Remove `cart-service` from product-service ingress:

```bash
kubectl patch networkpolicy product-service-policy -n ecommerce --type=json \
  -p='[{"op":"replace","path":"/spec/ingress/0/from","value":[
    {"podSelector":{"matchLabels":{"app":"api-gateway"}}},
    {"podSelector":{"matchLabels":{"app":"order-service"}}}
  ]}]'
```

**Observe:**

| Where | What you see |
|-------|--------------|
| UI (http://localhost:4000) | Add to cart → *"Failed to add to cart"* |
| CLI | `kubectl exec … cart-service -- wget … product-service:8001/health` times out |
| Hubble | **DROPPED** `cart-service → product-service:8001` |
| Logs | `kubectl logs -n ecommerce deploy/cart-service --tail=15` — product fetch errors |

**Diagnose:** Egress on `cart-service-policy` still allows product — misleading alone. **Ingress on the target** must also allow the caller.

```bash
kubectl get networkpolicy cart-service-policy -n ecommerce -o yaml | grep -A25 "egress:"
kubectl get networkpolicy product-service-policy -n ecommerce -o yaml | grep -A20 "ingress:"
```

**Fix:**

```bash
kubectl apply -f networking/policies/02-services.yaml
```

**Verify:** Add to cart works again; Hubble shows FORWARDED.

---

### Tweak 2 — Block cart → Redis (break cart persistence)

**Break:**

```bash
kubectl delete networkpolicy redis-policy -n ecommerce
```

Default deny now blocks cart → redis (no explicit allow for Redis ingress).

**Observe:**

| Where | What you see |
|-------|--------------|
| UI | Add to cart fails |
| CLI | `nc -zv redis 6379` from cart-service fails |
| Hubble | DROPPED `cart-service → redis:6379` |

**Fix:**

```bash
kubectl apply -f networking/policies/03-infrastructure.yaml
```

---

### Tweak 3 — Block order → cart (break checkout)

**Break:** Remove order-service from cart-service ingress:

```bash
kubectl patch networkpolicy cart-service-policy -n ecommerce --type=json \
  -p='[{"op":"replace","path":"/spec/ingress/0/from","value":[
    {"podSelector":{"matchLabels":{"app":"api-gateway"}}}
  ]}]'
```

**Observe:**

| Where | What you see |
|-------|--------------|
| UI | Cart works; **checkout fails** |
| CLI | `order-service → cart-service:8003/health` times out |
| Hubble | DROPPED on checkout attempt |

**Fix:**

```bash
kubectl apply -f networking/policies/02-services.yaml
```

---

### Tweak 4 — Block gateway egress (break product browse)

**Break:**

```bash
kubectl delete networkpolicy api-gateway-policy -n ecommerce
```

Gateway loses explicit egress; default deny blocks all outbound calls.

**Observe:**

| Where | What you see |
|-------|--------------|
| UI | Product list empty or errors |
| CLI | `curl http://localhost:9080/api/products` fails or times out |
| Hubble | DROPPED `api-gateway → product-service:8001` |

**Fix:**

```bash
kubectl apply -f networking/policies/01-api-gateway.yaml
curl -s "http://localhost:9080/api/products?page=1&page_size=1" | head -c 200
```

---

### Network policy lab — key takeaways

- NetworkPolicy is **bidirectional**: caller egress **and** target ingress must allow.
- Default deny means **deleting** an allow policy breaks traffic.
- Hubble shows the **exact** src → dst:port that was dropped.
- Restore everything: `./networking/apply-network-policies.sh`

---

# Part B — Service Mesh (Linkerd)

> **Goal:** Add mTLS, golden metrics, live HTTP tap, and L7 identity policies. Visualize in Linkerd Viz dashboard.

## B.1 — Install Linkerd + mesh the namespace

Ensure network policies are in good state, then install mesh:

```bash
./networking/apply-network-policies.sh   # baseline
AUTO_MESH=1 ./networking/install-linkerd.sh
./networking/linkerd/apply-all.sh
```

When using mesh **with** NetworkPolicies, include Linkerd proxy scrape rules:

```bash
INCLUDE_LINKERD_POLICIES=1 ./networking/apply-network-policies.sh
```

**Checkpoint — meshed pods:**

```bash
linkerd check
linkerd check --proxy -n ecommerce
kubectl get pods -n ecommerce
# HTTP microservices: 2/2 READY (app + linkerd-proxy)
```

| Workload | Containers | Notes |
|----------|------------|-------|
| product-service, cart-service, … | 2/2 | Meshed |
| redis, CNPG pods | 1/1 | Not meshed (opaque TCP) |
| rabbitmq | 2/2 | Meshed StatefulSet |

---

## B.2 — Open Linkerd Viz dashboard

```bash
linkerd viz dashboard
# Usually http://127.0.0.1:50750
```

**Dashboard tour:**

1. **Namespaces → ecommerce** — list deployments
2. **Deployments** — SUCCESS %, RPS, P50/P95/P99 latency per service
3. **Routes** — per-path breakdown (requires ServiceProfile)
4. **Tap** — live HTTP request stream
5. **Topology** — service graph with mTLS edges

> **Important:** Traffic from your laptop via `localhost:9080` often **does not** appear in the mesh graph (port-forward bypasses the inbound proxy). Use in-cluster traffic (Part C) for Viz labs.

---

## B.3 — Baseline mesh simulation

**Terminal 1 — generate in-cluster traffic (~2 min):**

```bash
./simulate-traffic-incluster.sh
# optional: DURATION_SEC=180 ./simulate-traffic-incluster.sh
```

**Terminal 2 — inspect mesh:**

```bash
linkerd viz edges deploy -n ecommerce -o wide
linkerd viz stat deploy -n ecommerce --time-window=10m
linkerd viz stat deploy -n ecommerce --from deploy/api-gateway --time-window=10m
```

**Expected edges:**

```
frontend      → api-gateway
api-gateway   → product-service
api-gateway   → user-service
api-gateway   → cart-service
api-gateway   → order-service
...
```

**Look for in `edges -o wide`:**

| Column | Meaning |
|--------|---------|
| `SECURED` | `√` = mTLS encrypted |
| `CLIENT` / `SERVER` | SPIFFE identities (ServiceAccount-based) |

---

## B.4 — Live tap (see HTTP + mTLS)

**Terminal 1 — start tap (leave running):**

```bash
linkerd viz tap deploy/order-service -n ecommerce -o wide
```

**Terminal 2 — send traffic:**

```bash
kubectl exec -n ecommerce deploy/api-gateway -c nginx -- \
  wget -qO- http://order-service:8004/health

# Or drive from frontend
kubectl exec -n ecommerce deploy/frontend -c frontend -- \
  wget -qO- 'http://api-gateway/api/products?page=1&page_size=3'
```

**In tap output, look for:**

| Field | Meaning |
|-------|---------|
| `:method`, `:path` | HTTP route |
| `tls=true` | Request was mTLS |
| `src` / `dst` | Caller and callee identities |
| `status` | HTTP status code |

---

## B.5 — Mesh policy tweak lab (4 scenarios)

Reset mesh config before each scenario:

```bash
./networking/linkerd/apply-all.sh
```

---

### Tweak 1 — Remove all AuthorizationPolicies (mTLS on, auth off)

**Break:**

```bash
kubectl delete authorizationpolicies -n ecommerce --all
```

**Simulate:**

```bash
# Previously blocked by L7 auth — now allowed (NP may still allow pod traffic)
kubectl exec -n ecommerce deploy/notification-service -c notification-service -- \
  curl -sf --max-time 3 http://product-service:8001/health && echo "ALLOWED without authz"
```

**Observe in Viz:**

- `linkerd viz edges` — still shows mTLS (`SECURED=√`)
- Proxy logs: `kubectl logs -n ecommerce deploy/product-service -c linkerd-proxy --tail=20`

**Restore:**

```bash
kubectl apply -f networking/linkerd/authorization/policies.yaml
```

**Verify block returns:**

```bash
kubectl exec -n ecommerce deploy/notification-service -c notification-service -- \
  curl -sf --max-time 3 http://product-service:8001/health || echo "blocked again"
```

**Lesson:** mTLS encrypts traffic; AuthorizationPolicy controls **who** may call **what**.

---

### Tweak 2 — Deny cart-service from product-service (identity rule)

**Break:** Patch `product-service-clients` MeshTLSAuthentication to remove cart identity:

```bash
kubectl patch meshtlsauthentication product-service-clients -n ecommerce --type=json \
  -p='[{"op":"replace","path":"/spec/identities","value":[
    "api-gateway.ecommerce.serviceaccount.identity.linkerd.cluster.local",
    "order-service.ecommerce.serviceaccount.identity.linkerd.cluster.local"
  ]}]'
```

**Simulate:**

```bash
# From cart — should fail at Linkerd proxy (NP still allows L3)
kubectl exec -n ecommerce deploy/cart-service -c cart-service -- \
  curl -sf --max-time 3 http://product-service:8001/health || echo "blocked by mesh auth"

# From api-gateway — should still work
kubectl exec -n ecommerce deploy/api-gateway -c nginx -- \
  wget -qO- http://product-service:8001/health && echo "gateway OK"
```

**Observe:**

| Where | What you see |
|-------|--------------|
| UI | Add to cart may fail (cart → product blocked at proxy) |
| Viz tap on product-service | 403 / connection reset from cart identity |
| Hubble | Still FORWARDED at L3 (NP allows) — mesh blocks at L7 |

**Restore:**

```bash
kubectl apply -f networking/linkerd/authorization/policies.yaml
```

---

### Tweak 3 — Aggressive timeout on order routes (see failures in metrics)

**Break:** Patch ServiceProfile to 1ms timeout on order creation:

```bash
kubectl get serviceprofile order-service.ecommerce.svc.cluster.local -n ecommerce -o yaml \
  > /tmp/order-sp.yaml
# Edit POST /api/orders timeout to 1ms in /tmp/order-sp.yaml, then:
kubectl apply -f /tmp/order-sp.yaml
```

Or quick patch via replace:

```bash
kubectl get serviceprofile order-service.ecommerce.svc.cluster.local -n ecommerce -o json | \
  python3 -c "
import json,sys
sp=json.load(sys.stdin)
for r in sp['spec']['routes']:
    if 'POST' in r.get('name',''):
        r['timeout']='1ms'
print(json.dumps(sp))
" | kubectl apply -f -
```

**Simulate checkout traffic:**

```bash
./simulate-traffic.sh
# or browse UI: login → cart → checkout
```

**Observe in Viz:**

```bash
linkerd viz stat deploy/order-service -n ecommerce --time-window=5m
linkerd viz routes deploy/order-service -n ecommerce
```

- SUCCESS rate drops on `POST /api/orders`
- Tap shows timeouts / 504 responses

**Restore:**

```bash
kubectl apply -f networking/linkerd/service-profiles/all-services.yaml
```

---

### Tweak 4 — Block Viz Prometheus scrape (metrics disappear)

This simulates what happens when NetworkPolicy blocks the mesh observability path.

**Break:**

```bash
kubectl delete networkpolicy allow-linkerd-viz-scrape -n ecommerce 2>/dev/null || true
# If using full 06 file:
kubectl get networkpolicy -n ecommerce -o name | grep linkerd | xargs -r kubectl delete -n ecommerce
```

**Simulate traffic:**

```bash
./simulate-traffic-incluster.sh
```

**Observe:**

```bash
linkerd viz stat deploy -n ecommerce --time-window=10m
# RPS shows '-' — no recent metrics
```

**Fix:**

```bash
INCLUDE_LINKERD_POLICIES=1 ./networking/apply-network-policies.sh
```

Re-run in-cluster traffic; metrics return within ~30s.

**Lesson:** Observability is part of networking — scrape paths need explicit NP rules (`06-linkerd-proxy.yaml`).

---

### Mesh lab — key takeaways

| Layer | Blocks at | Observable in |
|-------|-----------|---------------|
| NetworkPolicy | Cilium (L3/L4) | Hubble UI |
| AuthorizationPolicy | Linkerd proxy (L7 identity) | Viz tap, proxy logs |
| ServiceProfile | Linkerd proxy (timeout/retry) | Viz stat/routes |
| mTLS | Automatic between meshed pods | `linkerd viz edges -o wide` |

---

# Part C — Traffic simulation (both layers)

Use the right script for what you want to see.

## C.1 — Host-side traffic (UI + functional testing)

Drives the full user journey through port-forwarded gateway:

```bash
./simulate-traffic.sh
# Customize: DURATION_SEC=300 INTERVAL_SEC=0.5 ./simulate-traffic.sh
```

**Good for:**

- http://localhost:4000 flows
- Hubble flows (frontend → gateway → backends)
- Functional regression after policy tweaks

**Limited for:** Linkerd Viz edges (laptop traffic bypasses inbound proxy).

## C.2 — In-cluster traffic (mesh visualization)

```bash
./simulate-traffic-incluster.sh
```

**Good for:**

- `linkerd viz edges` / `stat` / topology
- Dashboard golden metrics
- Confirming mTLS between meshed services

## C.3 — Watch both UIs simultaneously

**Setup (3 terminals):**

```bash
# Terminal 1 — Hubble
cilium hubble ui

# Terminal 2 — Linkerd dashboard
linkerd viz dashboard

# Terminal 3 — traffic
./simulate-traffic-incluster.sh &
./simulate-traffic.sh
```

**Compare:**

| Question | Hubble | Linkerd Viz |
|----------|--------|-------------|
| Was packet allowed? | Verdict FORWARDED/DROPPED | — |
| Who called whom? | Pod labels + IP | SPIFFE identity |
| HTTP path | No | Yes (tap/routes) |
| Encrypted? | No | mTLS column |
| Success rate / latency | No | Yes (golden metrics) |

## C.4 — Scripted observation loop

```bash
watch -n3 'linkerd viz stat deploy -n ecommerce --time-window=5m'
```

While `simulate-traffic.sh` runs, watch RPS and latency change live.

---

# Part D — Canary deployment (order-service)

> **Goal:** Split traffic between stable and canary backends using Linkerd `TrafficSplit`. Watch the split in Viz.

Prerequisites: Linkerd installed, mesh policies applied, `order-service` healthy.

## D.1 — Create canary deployment

The stable deployment is `order-service`. We add a canary variant with a distinct label and response header.

```bash
kubectl get deploy order-service -n ecommerce -o yaml > /tmp/order-stable.yaml

# Create canary deployment (copy stable, change name/labels/image tag)
kubectl get deploy order-service -n ecommerce -o json | python3 -c "
import json,sys
d=json.load(sys.stdin)
d['metadata']['name']='order-service-canary'
d['metadata'].pop('resourceVersion',None)
d['metadata'].pop('uid',None)
d['spec']['selector']['matchLabels']['version']='canary'
d['spec']['template']['metadata']['labels']['version']='canary'
d['spec']['replicas']=1
# Optional: bump an env var so logs differ
for c in d['spec']['template']['spec']['containers']:
    if c['name']=='order-service':
        c.setdefault('env',[]).append({'name':'CANARY','value':'true'})
json.dump(d,sys.stdout)
" | kubectl apply -f -

kubectl rollout status deploy/order-service-canary -n ecommerce --timeout=120s
```

## D.2 — Create backing Services

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: order-service-stable
  namespace: ecommerce
  labels:
    app: order-service
spec:
  selector:
    app: order-service
    version: stable
  ports:
    - port: 8004
      targetPort: 8004
---
apiVersion: v1
kind: Service
metadata:
  name: order-service-canary
  namespace: ecommerce
  labels:
    app: order-service
spec:
  selector:
    app: order-service
    version: canary
  ports:
    - port: 8004
      targetPort: 8004
EOF
```

Label the existing deployment as stable:

```bash
kubectl patch deploy order-service -n ecommerce -p '{"spec":{"template":{"metadata":{"labels":{"version":"stable"}}}}}'
kubectl patch deploy order-service -n ecommerce --type=json \
  -p='[{"op":"add","path":"/spec/selector/matchLabels/version","value":"stable"}]'
kubectl rollout restart deploy/order-service -n ecommerce
kubectl rollout status deploy/order-service -n ecommerce --timeout=120s
```

Verify both backends:

```bash
kubectl get pods -n ecommerce -l app=order-service -L version
kubectl exec -n ecommerce deploy/api-gateway -c nginx -- wget -qO- http://order-service-stable:8004/health
kubectl exec -n ecommerce deploy/api-gateway -c nginx -- wget -qO- http://order-service-canary:8004/health
```

## D.3 — Apply TrafficSplit (90/10)

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: split.smi-spec.io/v1alpha1
kind: TrafficSplit
metadata:
  name: order-service-split
  namespace: ecommerce
spec:
  service: order-service
  backends:
    - service: order-service-stable
      weight: 900m
    - service: order-service-canary
      weight: 100m
EOF

kubectl get trafficsplit -n ecommerce
```

`order-service` Service continues to be the **apex** — clients keep calling `order-service:8004`; the mesh splits to stable/canary.

## D.4 — Generate traffic and observe the split

**Terminal 1 — tap order-service (both backends):**

```bash
linkerd viz tap deploy/order-service -n ecommerce -o wide
```

**Terminal 2 — sustained order traffic:**

```bash
# In-cluster health loops hit order-service apex
kubectl exec -n ecommerce deploy/api-gateway -c nginx -- sh -c '
  for i in $(seq 1 100); do
    wget -qO- http://order-service:8004/health >/dev/null 2>&1 || true
    sleep 0.2
  done
  echo done
'
```

**Terminal 3 — check backend distribution via proxy metrics:**

```bash
linkerd viz stat deploy/order-service -n ecommerce --time-window=5m
linkerd viz stat deploy/order-service-canary -n ecommerce --time-window=5m
linkerd viz stat deploy/order-service-stable -n ecommerce --time-window=5m
```

**In the Viz dashboard:**

1. Open **Deployments** → compare RPS on `order-service-stable` vs `order-service-canary`
2. Open **Topology** → see split backends under apex `order-service`
3. Expect ~90% stable / ~10% canary RPS (approximate over short windows)

**Count canary hits in logs:**

```bash
kubectl logs -n ecommerce deploy/order-service-canary -c order-service --tail=5
kubectl logs -n ecommerce deploy/order-service -c order-service --tail=5
```

## D.5 — Shift weights (progressive rollout)

Simulate a rollout: 90/10 → 50/50 → 0/100 → full promote.

```bash
# 50/50
kubectl patch trafficsplit order-service-split -n ecommerce --type=json \
  -p='[{"op":"replace","path":"/spec/backends","value":[
    {"service":"order-service-stable","weight":"500m"},
    {"service":"order-service-canary","weight":"500m"}
  ]}]'

# Re-run traffic loop; watch canary RPS rise in dashboard

# 100% canary
kubectl patch trafficsplit order-service-split -n ecommerce --type=json \
  -p='[{"op":"replace","path":"/spec/backends","value":[
    {"service":"order-service-stable","weight":"0m"},
    {"service":"order-service-canary","weight":"1000m"}
  ]}]'
```

**Observe:** Viz stat on canary deployment dominates; stable drops to ~0 RPS.

## D.6 — Promote or rollback

**Promote canary (make it the only deployment):**

```bash
kubectl delete trafficsplit order-service-split -n ecommerce
kubectl delete svc order-service-stable order-service-canary -n ecommerce
kubectl delete deploy order-service-canary -n ecommerce
kubectl patch deploy order-service -n ecommerce --type=json \
  -p='[{"op":"remove","path":"/spec/selector/matchLabels/version"}]'
```

**Rollback (keep stable, remove canary):**

```bash
kubectl patch trafficsplit order-service-split -n ecommerce --type=json \
  -p='[{"op":"replace","path":"/spec/backends","value":[
    {"service":"order-service-stable","weight":"1000m"},
    {"service":"order-service-canary","weight":"0m"}
  ]}]'
kubectl delete deploy order-service-canary -n ecommerce
kubectl delete trafficsplit order-service-split -n ecommerce
kubectl delete svc order-service-stable order-service-canary -n ecommerce
```

---

# Quick reference

## One-command deploy modes

```bash
./deploy-all.sh --network-policies   # Part A only
./deploy-all.sh --linkerd            # App + mesh (no NP)
./deploy-all.sh --both               # Full stack (recommended for complete exercise)
```

## Restore known-good state

```bash
./networking/apply-network-policies.sh
INCLUDE_LINKERD_POLICIES=1 ./networking/apply-network-policies.sh   # if mesh enabled
./networking/linkerd/apply-all.sh
```

## Observability commands

```bash
# Network layer
cilium hubble ui
hubble observe --namespace ecommerce --verdict DROPPED --follow
kubectl get networkpolicies -n ecommerce

# Service mesh
linkerd viz dashboard
linkerd viz edges deploy -n ecommerce -o wide
linkerd viz stat deploy -n ecommerce --time-window=10m
linkerd viz tap deploy/cart-service -n ecommerce -o wide
linkerd viz routes deploy/order-service -n ecommerce
linkerd viz top deploy/api-gateway -n ecommerce
```

## Traffic scripts

```bash
./simulate-traffic.sh              # Host → gateway (UI flows)
./simulate-traffic-incluster.sh    # Meshed pod → pod (Viz edges)
```

---

# Cleanup

```bash
# Remove canary artifacts
kubectl delete trafficsplit order-service-split -n ecommerce --ignore-not-found
kubectl delete deploy order-service-canary -n ecommerce --ignore-not-found
kubectl delete svc order-service-stable order-service-canary -n ecommerce --ignore-not-found

# Remove network policies
kubectl delete -f networking/policies/ --ignore-not-found

# Uninstall mesh
./networking/uninstall-linkerd.sh

# Delete cluster
kind delete cluster --name ecommerce-networking
```

---

# Exercise checklist

Use this to track progress:

- [ ] **A.1** App deployed on Cilium cluster
- [ ] **A.2** NetworkPolicies applied; app still works
- [ ] **A.3** Baseline connectivity pass/fail tests
- [ ] **A.4** Hubble UI — saw FORWARDED and DROPPED flows
- [ ] **A.5** Policy tweak 1 — cart → product blocked & fixed
- [ ] **A.5** Policy tweak 2 — Redis blocked & fixed
- [ ] **A.5** Policy tweak 3 — checkout blocked & fixed
- [ ] **A.5** Policy tweak 4 — gateway egress blocked & fixed
- [ ] **B.1** Linkerd installed; pods 2/2
- [ ] **B.2** Viz dashboard opened
- [ ] **B.3** In-cluster traffic; edges visible with mTLS
- [ ] **B.4** Live tap shows `tls=true` and HTTP paths
- [ ] **B.5** Mesh tweak 1 — auth removed/restored
- [ ] **B.5** Mesh tweak 2 — cart identity denied/restored
- [ ] **B.5** Mesh tweak 3 — timeout caused metric drop/restored
- [ ] **B.5** Mesh tweak 4 — scrape blocked/metrics restored
- [ ] **C** Ran both traffic scripts; compared Hubble vs Viz
- [ ] **D** Canary deployed; TrafficSplit 90/10 observed
- [ ] **D** Weight shift 50/50 and promote/rollback practiced
