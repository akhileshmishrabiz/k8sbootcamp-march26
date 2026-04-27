# Week 1 — Class 1: Kubernetes Fundamentals

**LivingDevOps | Advanced Kubernetes on AWS/EKS Bootcamp**

This class sets the foundation. No EKS yet — we stay on Minikube inside a GitHub Codespace so everyone can follow without burning AWS credits. The goal is to understand *why* Kubernetes exists and get hands-on with the smallest building blocks: Pods, Deployments, and Services.

---

## Topics Covered

### 1. The story — from bare metal to Kubernetes
Walked through how deployments evolved: physical servers → virtualization (VMware, hypervisors) → Docker containers → container orchestration. Covered why Netflix's 2008 outage pushed the industry toward microservices, and how Google open-sourced Kubernetes in 2014 (partly to pull users toward GCP/GKE).

### 2. Why containers — namespaces and cgroups
Docker didn't invent containers. It made Google's internal concept (Borg) usable for everyone by wrapping Linux **namespaces** (isolation) and **cgroups** (resource allocation) into a simple CLI. Interviews: two lines on each is enough.

### 3. Kubernetes architecture — control plane vs worker nodes
Used a "management vs employees" analogy. The control plane has four components — **API server** (receptionist, everything routes through it), **etcd** (key-value database with the entire cluster state), **scheduler** (assigns work to nodes), **controller manager** (watches state and drives it toward desired state). Worker nodes run **kubelet** (the supervisor on each node), **kube-proxy** (networking between pods), and a **container runtime** (containerd).

### 4. Minikube setup in a Codespace
Minikube runs the whole cluster as a single Docker container on your machine — good enough for learning, not a real cluster. Installed `kubectl` and `minikube`, then `minikube start` brings up a working single-node cluster. The `~/.kube/config` file is how `kubectl` knows which cluster to talk to.

### 5. Pods — the smallest deployable unit
A Pod wraps one or more containers. For app workloads, almost always one container per pod. Demonstrated `kubectl run`, `kubectl get pods -o wide`, `kubectl describe pod`, `kubectl logs`. Key point: if you delete a bare pod, it stays gone.

### 6. Deployments — self-healing and scaling
A Deployment manages a ReplicaSet, which manages Pods. You declare "I want 3 replicas" and Kubernetes keeps 3 alive forever. Kill a pod — a new one comes back in seconds. Scale up with `kubectl scale` or edit the YAML. Showed failure scenarios (`ImagePullBackOff`) and how `kubectl describe pod` Events section is always your first stop for debugging.

### 7. Services — stable endpoints for unstable pods
Pod IPs change every time pods restart, so you never use them directly. A Service uses **label selectors** to find matching pods and gives you a stable ClusterIP that does round-robin across them. Covered ClusterIP type (the only one used in real production). Noted that NodePort and LoadBalancer types are not the production path — we use Ingress controllers instead, covered later.

### 8. Troubleshooting patterns
- Pod pending → `kubectl describe pod` Events section
- Pod running but broken → `kubectl logs`
- Logs only exist *after* the container has started at least once
- `describe` is for "why didn't it start", `logs` is for "why did it crash"

---

## Exercise

The full step-by-step lab is in [`day1/assignment.md`](./day1/assignment.md).

Summary of what you will do:

1. Install `kubectl` and `minikube` in a GitHub Codespace
2. Start a Minikube cluster and verify with `minikube status` and `kubectl get nodes`
3. Run a single nginx pod with `kubectl run`, inspect it, delete it — observe it stays deleted
4. Write a Deployment YAML for 3 nginx replicas and apply it
5. Delete one pod manually — watch Kubernetes recreate it (self-healing)
6. Scale the Deployment up to 5 and back down to 3
7. Break the image on purpose (`nginx:doesnotexist`) and debug via `kubectl describe pod` Events
8. Write a ClusterIP Service, apply it, and `curl` it from inside the Minikube container
9. Prove the Service keeps working even when you delete its backing pods
10. Read your `~/.kube/config` file and understand what is in it
11. Commit all YAMLs to your repo

**Deliverables:** screenshots of the key steps (3 pods running, self-healing in action, image pull error, Service curl working) and your `k8s/` folder pushed to GitHub.

---

*LivingDevOps — livingdevops.com*
