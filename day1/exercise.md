# k8sbootcamp-march26

curl -O https://s3.us-west-2.amazonaws.com/amazon-eks/1.33.8/2026-02-27/bin/darwin/amd64/kubectl


# minikube install

curl -LO https://github.com/kubernetes/minikube/releases/latest/download/minikube-linux-amd64

sudo install minikube-linux-amd64 /usr/local/bin/minikube && rm minikube-linux-amd64

#  start the cluster
minikube start

minikube status

# ============================================
# KUBECONFIG & CLUSTER INFO
# ============================================
ls ~/.kube/config
kubectl cluster-info
kubectl get nodes
kubectl get nodes -o wide

# ============================================
# POD - RUN & INSPECT
# ============================================
kubectl run web-server --image=nginx
kubectl get pods
kubectl get pods -o wide
kubectl describe pod web-server

# Exec into a pod (kubectl-native, no docker needed)
kubectl exec -it web-server -- sh

# Check pod logs
kubectl logs web-server

# ============================================
# POD - DELETE
# ============================================
kubectl delete pod web-server

# ============================================
# DEPLOYMENT - CREATE & INSPECT
# ============================================
kubectl create deployment nginx-dep --image=nginx --replicas=3
kubectl get deployments
kubectl describe deployment nginx-dep

# ============================================
# DEPLOYMENT - SCALE
# ============================================
kubectl scale deployment nginx-dep --replicas=5
kubectl get deployment
kubectl get pods -o wide

# ============================================
# DEPLOYMENT - ROLLOUT
# ============================================
kubectl rollout status deployment nginx-dep
kubectl rollout history deployment nginx-dep
kubectl rollout undo deployment nginx-dep

# ============================================
# DEPLOYMENT - DELETE
# ============================================
kubectl delete deployment nginx-dep



