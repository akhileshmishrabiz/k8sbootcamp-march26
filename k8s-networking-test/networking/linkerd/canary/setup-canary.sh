#!/bin/bash
# Canary lab: split order-service traffic 90% stable / 10% canary via Linkerd TrafficSplit.
set -euo pipefail

NAMESPACE="${NAMESPACE:-ecommerce}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() { echo -e "${YELLOW}INFO: $1${NC}"; }
ok() { echo -e "${GREEN}✓ $1${NC}"; }
die() { echo -e "${RED}✗ $1${NC}"; exit 1; }

command -v kubectl >/dev/null 2>&1 || die "kubectl required"
command -v linkerd >/dev/null 2>&1 || die "linkerd CLI required"

kubectl get deploy order-service -n "$NAMESPACE" >/dev/null 2>&1 || \
  die "order-service not found in $NAMESPACE — deploy the app first"

if ! kubectl get deploy -n linkerd linkerd-destination >/dev/null 2>&1; then
  die "Linkerd not installed. Run: AUTO_MESH=1 ./networking/install-linkerd.sh"
fi

info "Step 1/4: Label stable deployment (version=stable) and roll out"
kubectl patch deploy order-service -n "$NAMESPACE" -p \
  '{"spec":{"template":{"metadata":{"labels":{"version":"stable"}}}}}'
kubectl patch deploy order-service -n "$NAMESPACE" --type=json \
  -p='[{"op":"add","path":"/spec/selector/matchLabels/version","value":"stable"}]' 2>/dev/null || \
kubectl patch deploy order-service -n "$NAMESPACE" --type=json \
  -p='[{"op":"replace","path":"/spec/selector/matchLabels/version","value":"stable"}]'
kubectl rollout restart deploy/order-service -n "$NAMESPACE"
kubectl rollout status deploy/order-service -n "$NAMESPACE" --timeout=180s
ok "Stable deployment ready"

info "Step 2/4: Create canary deployment"
kubectl get deploy order-service -n "$NAMESPACE" -o json | python3 -c "
import json, sys
d = json.load(sys.stdin)
d['metadata']['name'] = 'order-service-canary'
d['metadata'].pop('resourceVersion', None)
d['metadata'].pop('uid', None)
d['metadata'].pop('generation', None)
d['spec']['selector']['matchLabels']['version'] = 'canary'
d['spec']['template']['metadata']['labels']['version'] = 'canary'
d['spec']['replicas'] = 1
for c in d['spec']['template']['spec']['containers']:
    if c['name'] == 'order-service':
        c.setdefault('env', [])
        c['env'] = [e for e in c['env'] if e.get('name') != 'CANARY']
        c['env'].append({'name': 'CANARY', 'value': 'true'})
json.dump(d, sys.stdout)
" | kubectl apply -f -
kubectl rollout status deploy/order-service-canary -n "$NAMESPACE" --timeout=180s
ok "Canary deployment ready"

info "Step 3/4: Create stable + canary Services"
kubectl apply -f - <<EOF
apiVersion: v1
kind: Service
metadata:
  name: order-service-stable
  namespace: ${NAMESPACE}
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
  namespace: ${NAMESPACE}
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
ok "Backend services created"

info "Step 4/4: Apply TrafficSplit (90/10)"
kubectl apply -f "$SCRIPT_DIR/order-service-traffic-split.yaml"
ok "TrafficSplit applied"

echo ""
kubectl get pods -n "$NAMESPACE" -l app=order-service -L version
kubectl get trafficsplit -n "$NAMESPACE"
echo ""
echo "Generate traffic:"
echo "  kubectl exec -n $NAMESPACE deploy/api-gateway -c nginx -- sh -c 'for i in \$(seq 1 50); do wget -qO- http://order-service:8004/health >/dev/null 2>&1; sleep 0.2; done'"
echo ""
echo "Watch split:"
echo "  linkerd viz stat deploy/order-service -n $NAMESPACE --time-window=5m"
echo "  linkerd viz stat deploy/order-service-canary -n $NAMESPACE --time-window=5m"
echo "  linkerd viz stat deploy/order-service-stable -n $NAMESPACE --time-window=5m"
echo ""
echo "Teardown: $SCRIPT_DIR/teardown-canary.sh"
