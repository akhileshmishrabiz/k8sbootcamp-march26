# Week 1 - Class 1 Exercise: Kubernetes Fundamentals
**LivingDevOps | Advanced Kubernetes on AWS/EKS Bootcamp**

> Follow each step in order. Do not copy-paste blindly — read the command, understand what it does, then run it.

---

## Setup: GitHub Codespaces

Go to [github.com/codespaces](https://github.com/codespaces) and create a new Codespace on your repository. Choose a **2-core** machine.

Once your Codespace is open, you will have a terminal ready. All commands below run in that terminal.

---

## Task 1: Install kubectl

```bash
# Download kubectl binary (version 1.33)
curl -LO "https://dl.k8s.io/release/v1.33.0/bin/linux/amd64/kubectl"

# Make it executable
chmod +x kubectl

# Move it to a directory in your PATH
sudo mv kubectl /usr/local/bin/

# Verify installation
kubectl version --client
```

Expected output:
```
Client Version: v1.33.0
```

---

## Task 2: Install Minikube

```bash
# Download Minikube binary
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64

# Install it
sudo install minikube-linux-amd64 /usr/local/bin/minikube

# Verify installation
minikube version
```

---

## Task 3: Start Your Cluster

```bash
# Start minikube (uses Docker driver by default in Codespaces)
minikube start --driver=docker

# Check cluster status
minikube status
```

Expected output:
```
minikube
type: Control Plane
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```

```bash
# Check your node
kubectl get nodes
```

Expected output:
```
NAME       STATUS   ROLES           AGE   VERSION
minikube   Ready    control-plane   Xs    v1.33.x
```

> **Screenshot this output** before moving on.

---

## Task 4: Run Your First Pod

```bash
# Run a single nginx pod
kubectl run web-server --image=nginx

# Check if it is running
kubectl get pods

# Get more details including IP and node
kubectl get pods -o wide
```

```bash
# Describe the pod — always read the Events section at the bottom
kubectl describe pod web-server
```

```bash
# View logs of the pod
kubectl logs web-server
```

```bash
# Delete the pod
kubectl delete pod web-server

# Check again — it should NOT come back
kubectl get pods
```

> **Key observation:** When you delete a bare pod, it stays gone. Remember this — it is why we use Deployments.

---

## Task 5: Create a Deployment

Create a file called `deployment.yaml`:

```bash
mkdir -p k8s
cat > k8s/deployment.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx-container
        image: nginx:latest
        ports:
        - containerPort: 80
EOF
```

```bash
# Apply the deployment
kubectl apply -f k8s/deployment.yaml

# Watch pods come up (Ctrl+C to exit)
kubectl get pods -w
```

```bash
# Check your deployment
kubectl get deployment nginx-deployment

# Get all pods with IPs
kubectl get pods -o wide
```

> **Screenshot:** Show all 3 pods in Running state.

---

## Task 6: Self-Healing in Action

```bash
# Get the name of one pod
kubectl get pods

# Delete one pod (replace <pod-name> with an actual pod name from above)
kubectl delete pod <pod-name>

# Immediately watch what happens
kubectl get pods -w
```

> **Key observation:** Kubernetes detects that only 2 pods are running. It creates a new one automatically to bring the count back to 3. This is self-healing.

> **Screenshot:** Capture the new pod being created.

---

## Task 7: Scale the Deployment

```bash
# Scale up to 5 replicas
kubectl scale deployment nginx-deployment --replicas=5

# Check
kubectl get pods

# Scale back down to 3
kubectl scale deployment nginx-deployment --replicas=3

# Check again
kubectl get pods
```

---

## Task 8: Simulate a Failure (Bad Image)

```bash
# Edit the image name to something that does not exist
kubectl set image deployment/nginx-deployment nginx-container=nginx:doesnotexist

# Watch what happens
kubectl get pods -w
```

You will see pods going into `ImagePullBackOff` or `ErrImagePull` state.

```bash
# Describe a failing pod to understand why
kubectl describe pod <failing-pod-name>
```

Look at the **Events** section at the bottom. You will see:

```
Failed to pull image "nginx:doesnotexist": ...
```

> **Screenshot:** The Events section showing the image pull error.

```bash
# Fix the image back to a valid one
kubectl set image deployment/nginx-deployment nginx-container=nginx:latest

# Watch pods recover
kubectl get pods -w
```

---

## Task 9: Create a Service

Create a file called `service.yaml`:

```bash
cat > k8s/service.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  name: nginx-service
spec:
  type: ClusterIP
  selector:
    app: nginx
  ports:
  - port: 8080
    targetPort: 80
EOF
```

```bash
# Apply the service
kubectl apply -f k8s/service.yaml

# Check the service — note the ClusterIP assigned
kubectl get svc nginx-service
```

Expected output:
```
NAME            TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)    AGE
nginx-service   ClusterIP   10.x.x.x        <none>        8080/TCP   Xs
```

---

## Task 10: Test the Service

```bash
# Check which container minikube is running as
docker ps
```

```bash
# Log into the minikube container
docker exec -it minikube bash
```

Inside the minikube container:

```bash
# Get your service ClusterIP first (run this outside minikube if needed)
# kubectl get svc nginx-service

# Curl the service IP on port 8080 (replace with your actual ClusterIP)
curl http://<CLUSTER-IP>:8080
```

You should see the nginx welcome page HTML.

```bash
# Exit minikube container
exit
```

---

## Task 11: Prove the Service Survives Pod Restarts

```bash
# Delete a pod
kubectl delete pod <any-pod-name>

# Log back into minikube
docker exec -it minikube bash

# Curl the service again — it should still work
curl http://<CLUSTER-IP>:8080

exit
```

> **Key observation:** The pod IP changed, but the service IP stayed the same. The service always finds healthy pods via labels. This is why you never use pod IPs directly.

---

## Task 12: Explore the kubeconfig File

```bash
# View your kubeconfig — this is how kubectl knows which cluster to talk to
cat ~/.kube/config
```

> Notice it has: cluster address, user credentials, and context. Every time you create a new cluster, an entry is added here automatically.

---

## Cleanup

```bash
# Delete everything you created
kubectl delete -f k8s/

# Verify
kubectl get pods
kubectl get svc
```

---

## Commit Your Work

```bash
# Add all files to git
git add k8s/

# Commit
git commit -m "Week 1 Class 1: deployment and service exercise"

# Push to GitHub
git push
```

---

## Quick Reference: Commands Used Today

| Command | What it does |
|---|---|
| `kubectl get pods` | List all pods |
| `kubectl get pods -o wide` | List pods with IP and node info |
| `kubectl describe pod <name>` | Full details + events of a pod |
| `kubectl logs <name>` | App logs from a pod |
| `kubectl apply -f <file>` | Create or update a resource from YAML |
| `kubectl delete pod <name>` | Delete a specific pod |
| `kubectl delete -f <file>` | Delete all resources in a YAML file |
| `kubectl scale deployment <name> --replicas=N` | Scale a deployment |
| `kubectl set image deployment/<name> <container>=<image>` | Update container image |
| `kubectl get svc` | List services |
| `minikube status` | Check cluster health |
| `minikube start` | Start the local cluster |

---

*LivingDevOps — livingdevops.com*