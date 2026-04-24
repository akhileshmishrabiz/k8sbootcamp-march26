# Week 1 — Class 2: Secrets, ConfigMaps, Resources & Autoscaling

**LivingDevOps | Advanced Kubernetes on AWS/EKS Bootcamp**

Picks up where Class 1 left off. Now we deploy a real Flask app (the Student Portal) that talks to a Postgres database on RDS, pulls a private image from ECR, and learns how to scale it properly. Still on Minikube — but everything we do here translates directly to EKS.

---

## Topics Covered

### 1. Freelens — GUI for your cluster
Installed Freelens (the open-source fork of OpenLens). It reads the same `~/.kube/config` that `kubectl` uses, so every cluster you can reach on the CLI shows up in the GUI. Lets you view pods, check logs, and exec into containers without typing commands. Used alongside `kubectl`, not instead of it.

### 2. Deploying a real app — the Student Portal
Walked through a Flask + Postgres app using its `docker-compose.yml` as the source of truth for what env vars and ports it needs. Two classes of values: **config data** (port number, Flask app name) vs **credential data** (DB password). Kubernetes has separate primitives for each.

### 3. Pulling a private ECR image — imagePullSecrets
Public images (Docker Hub nginx) need no auth. Private ECR images do. Created a `docker-registry` type Secret holding an ECR token (`aws ecr get-login-password` piped into `kubectl create secret`) and referenced it in the Deployment under `imagePullSecrets`. This is the Minikube way — on EKS you use IAM roles instead and skip this entirely.

### 4. Secrets — for credentials
Used `Opaque` type Secrets to hold the DB connection string. Values must be base64-encoded (`echo -n "..." | base64`). **Base64 is encoding, not encryption** — it's not actually safe. Anyone with access to the YAML can decode it. Real production uses External Secrets Operator or similar, which we'll cover on EKS.

### 5. ConfigMaps — for non-sensitive config
Same shape as Secrets but for plain config values. Referenced via `configMapKeyRef` the same way Secrets use `secretKeyRef`. Deployments can depend on ConfigMaps and Secrets — if either is missing, pods stay in Pending state until you apply them.

### 6. Debugging scenarios (interview gold)
Three causes of a Pending pod, all of which we triggered on purpose:
- Missing `imagePullSecret` → `no basic auth credentials`
- Missing Secret referenced in the Deployment → pod waits indefinitely
- Missing ConfigMap referenced in the Deployment → same behavior

### 7. Resources — requests vs limits
- **Request** = reservation. The pod will not schedule until a node can guarantee this much CPU and memory. Think "entitlement."
- **Limit** = ceiling. The pod can burst up to this if the node has spare capacity, but never beyond.
- Don't make the gap between them too wide — 1x to 2x is a healthy pattern.
- Unit conventions: `500m` = 0.5 CPU, `Mi` for memory (e.g. `256Mi`).

### 8. Port forwarding — the troubleshooting shortcut
`kubectl port-forward svc/studentportal 8080:80` pipes a Service port to your laptop. Used constantly for testing before Ingress is set up. Works for any Service or Pod.

### 9. Metrics Server
Nothing inside Kubernetes knows CPU/memory usage by default. Metrics Server pulls that data from each node's kubelet and makes it available via `kubectl top pods`. Required for HPA to work. On Minikube: `minikube addons enable metrics-server`. On EKS: installed via Helm.

### 10. HPA — Horizontal Pod Autoscaler
Scales the *number* of pods based on CPU or memory utilization thresholds. Configured `minReplicas`, `maxReplicas`, target utilization, and scale-up/scale-down stabilization windows. The `behavior` block controls how fast it reacts — a short `stabilizationWindowSeconds` on scale-down makes it shrink quickly when load drops.

### 11. VPA — Vertical Pod Autoscaler (self-study)
Scales the *size* (CPU/memory) of pods instead of the count. Historically required pod restarts so most teams skipped it; since Kubernetes 1.33 it can scale vertically without downtime. Use VPA when horizontal scaling isn't a good fit (stateful workloads, databases).

### 12. Load testing — why it matters
You can't pick sensible requests/limits or HPA thresholds without real data. Mentioned k6 (from Grafana Labs) as a modern load-testing tool for simulating real traffic. Load testing in a staging environment gives you the numbers you need to tune everything else.

---

## Exercise

The full assignment is in [`day2/exercise.md`](./day2/exercise.md).

Summary of what you will do:

1. **Deploy the Student Portal** — authenticate to ECR, create the image pull Secret, build the DB connection string from your RDS endpoint, apply Secret + ConfigMap + Deployment + Service. Port-forward and register a test account.

2. **Break things on purpose** (interview scenarios):
   - Delete the Secret, then kill a pod — watch it go Pending
   - Set a bad image tag — check events with `describe`
   - Delete the ConfigMap — observe the same Pending pattern
   - For each, write down exactly which command surfaced the problem

3. **Resource limits** — explain `requests` vs `limits` in your own words, set the memory request to something absurd (`8Gi`) and observe what happens, then answer: what does `requests.cpu: 100m, limits.cpu: 1000m` mean?

4. **HPA in action** — enable metrics-server, apply the HPA, generate load with a curl loop inside the Minikube container, watch pods scale up, stop the load, watch them scale down. Screenshot each phase.

5. **VPA self-study** — read the VPA config and answer: HPA vs VPA difference in one sentence, when to prefer VPA, what `updateMode: Auto` means. Apply it and note the recommendations.

6. **Reflection questions** — answer in `answers.md`:
   - Your troubleshooting playbook for a `CrashLoopBackOff` pod
   - Why you don't put DB passwords in the Deployment YAML
   - Difference between `kubectl apply` and `kubectl create`
   - How to fix `TARGETS: <unknown>` on an HPA

**Deliverables:** all YAMLs under `k8s/`, screenshots in `/screenshots`, written answers in `answers.md`, pushed to GitHub before next class.

---

## What's Next

Next week we move past Minikube's single-node simplicity and cover Volumes, PersistentVolumes, StatefulSets, probes, and a basic CI/CD + GitOps workflow with Argo CD — still on Minikube, so the concepts land before we deal with EKS infra complexity. From Week 3 onward, everything runs on EKS.

---

*LivingDevOps — livingdevops.com*
