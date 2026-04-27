# Week 2 — Class 3: Persistent Volumes, Probes & Running a Database on Kubernetes

**LivingDevOps | Advanced Kubernetes on AWS/EKS Bootcamp**

Last week the database lived on RDS. This week we move it into the cluster itself — which opens up a bunch of concepts you need before touching real stateful workloads: persistent volumes, volume claims, storage classes, and health probes. Still on Minikube — the idea is to get the concepts solid before dealing with EBS and storage drivers on EKS.

---

## Topics Covered

### 1. Why stateful workloads are different
On a bare Deployment, if the pod dies, the data dies with it. For stateless apps (a Flask web server, an nginx frontend) that is fine. For databases and anything that writes data, you need the data to outlive the pod. That is what persistent volumes solve.

### 2. Running Postgres as a Deployment (and why that is wrong in production)
We deployed Postgres as a `Deployment` with `replicas: 1` for teaching purposes. Never run more than one replica of a database as a Deployment — two pods writing to the same volume will corrupt your data. Real production uses **StatefulSets**, which we'll cover in a later class. For today, one-replica Deployment is fine.

### 3. Building images locally for Minikube
Two ways to make a local Docker image available inside Minikube: either run `eval $(minikube docker-env)` before `docker build` (scopes your Docker CLI to Minikube's daemon), or use `minikube image load <image:tag>` after building locally. When using a local image, set `imagePullPolicy: Never` so Kubernetes doesn't try to pull it from a registry.

### 4. Readiness and Liveness Probes
- **Readiness probe** runs while the container is starting. The pod is not marked Ready (and does not receive traffic) until the probe passes. Use this to wait for dependencies — a database finishing initialization, a cache warming up.
- **Liveness probe** runs continuously after startup. If it fails repeatedly, Kubernetes restarts the container. Use this to catch apps that are "running" but stuck or deadlocked.
- **Startup probe** (mentioned briefly): for apps with long initialization times — gives them a grace period before liveness kicks in.

The probe itself can be an HTTP GET, a TCP check, or an exec command. For Postgres we use `pg_isready`; for Flask we hit a `/healthy` endpoint.

### 5. Service discovery via DNS inside the cluster
Kubernetes runs CoreDNS by default. Every Service automatically gets a DNS name in the form:
```
<service-name>.<namespace>.svc.cluster.local
```
So our app connects to Postgres using `postgres-service.default.svc.cluster.local` — not an IP, not a hardcoded endpoint. Change the Service port tomorrow and nothing breaks.

### 6. Storage Classes — the driver layer
A StorageClass tells Kubernetes *how* to provision disks. Minikube ships with a default class called `standard` that uses host-path (a directory on the node). On EKS you install the EBS CSI driver and get a class that provisions real EBS volumes. Same YAML, different backend — that is the whole point.

### 7. PersistentVolumes and PersistentVolumeClaims
- A **PersistentVolume (PV)** is the actual disk.
- A **PersistentVolumeClaim (PVC)** is a pod's request for a disk of a certain size and access mode.

You don't create PVs manually in real workflows. You write a PVC saying "I need 5Gi, ReadWriteOnce, from the `standard` class" — and the storage class provisions the PV for you (dynamic provisioning). The analogy: the pod is "claiming" its entitlement. No volume, no pod.

### 8. Mounting the volume into the container
The PVC is the claim; mounting it is a separate step in the pod spec. You tell the container: "mount this volume at `/var/lib/postgresql/data`." Postgres writes to that path inside the container — but the data actually lives on the PV. Container dies, new container comes up, same PV reattaches, data is there.

### 9. The full data persistence chain
`Pod → volumeMounts → volumes → persistentVolumeClaim → (StorageClass) → PersistentVolume → actual disk`

Break any link and the pod stays Pending. Common failures we'll trigger in the exercise:
- PVC references a StorageClass that doesn't exist → pod pending forever
- No StorageClass capacity / driver not installed on the cluster → same thing
- PV already bound to another PVC → new PVC can't claim it

### 10. Debugging stateful pods
- `kubectl describe pod` — Events section will say "no persistent volumes available" or "volume not mounted"
- `kubectl get pvc` — shows whether the claim is `Bound` or `Pending`
- `kubectl get pv` — shows the underlying volumes

### 11. Architecture discussion: RDS vs in-cluster databases
Two legitimate production patterns:
- **App on EKS, database on RDS** — what most enterprise teams do. AWS manages scaling, backups, failover.
- **Everything on Kubernetes** — Netflix-style. Uses CloudNativePG for Postgres, proper StatefulSets, and dedicated operators. Cheaper but you own the complexity.

There is no one right answer. Pick based on how much database operational burden your team can absorb. For the bootcamp we'll see both.

---

## Exercise

This is your hands-on for Class 3. Everything runs on Minikube.

### Part 1 — Build and deploy the app locally

1. Clean up whatever is running from the previous class:
   ```bash
   kubectl delete deployment --all
   kubectl delete pvc --all
   ```

2. Scope your Docker CLI to Minikube's daemon, then build the app image:
   ```bash
   eval $(minikube docker-env)
   cd app/
   docker build -t app:1.0 .
   docker images | grep app
   ```

3. Confirm the image is visible inside Minikube:
   ```bash
   minikube ssh -- docker images | grep app
   ```

### Part 2 — Deploy Postgres with a PVC

4. Read the files in `day3/k8s/postgres/`:
   - `secret.yaml` — Postgres credentials (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`)
   - `pvc.yaml` — claims 5Gi from the `standard` StorageClass
   - `postgres-with-volume.yaml` — the Deployment with `volumeMounts` and the `pg_isready` readiness probe
   - `service.yaml` — ClusterIP on port 5432

5. Apply them in order:
   ```bash
   kubectl apply -f day3/k8s/postgres/
   ```

6. Verify everything bound correctly:
   ```bash
   kubectl get pvc
   kubectl get pv
   kubectl get pods -l app=postgres
   ```
   You should see the PVC in `Bound` state and the pod in `Running` with `1/1 Ready`.

7. Confirm DNS resolution works. Exec into a different pod (or spin up a throwaway busybox) and run:
   ```bash
   nslookup postgres-service.default.svc.cluster.local
   ```

### Part 3 — Deploy the app connected to the in-cluster database

8. Look at `day3/k8s/app/app-deployment.yaml`. Note:
   - `image: app:1.0` with `imagePullPolicy: Never`
   - `DB_LINK` pointing to `postgres-service.default.svc.cluster.local:5432`
   - Both readiness and liveness probes hitting `/healthy`

9. Apply it:
   ```bash
   kubectl apply -f day3/k8s/app/
   ```

10. Watch pods come up. The app pod should wait for Postgres to be Ready before itself becoming Ready. Describe the pod during startup and read the probe-related events.

11. Port-forward and open the app:
    ```bash
    kubectl port-forward svc/app-service 8000:8000
    ```
    Register an account, add a few students. Take a screenshot.

### Part 4 — Prove persistence works

12. With data in the app, kill the Postgres pod:
    ```bash
    kubectl delete pod -l app=postgres
    ```

13. Watch a new Postgres pod come up. Does the PVC reattach? Run:
    ```bash
    kubectl get pvc
    kubectl get pods -l app=postgres -w
    ```

14. Go back to the app in your browser. Your data should still be there. If it is not, check whether your app is trying to re-create tables on startup (this is the class of bug Akhilesh mentioned — we fix it properly with a migration job in Week 3).

### Part 5 — Break things and debug

Do each scenario, write down which command surfaced the problem, and how you fixed it.

15. **Scenario A — PVC references nonexistent StorageClass.** Edit `pvc.yaml` and change `storageClassName` to `doesnotexist`. Delete and reapply. What state is the PVC in? What state is the pod in? Which `describe` output told you what was wrong?

16. **Scenario B — Probe hits wrong path.** Edit the app Deployment and change the readiness probe path from `/healthy` to `/nonsense`. Apply. Watch the pod. It comes up but never goes Ready — why? What HTTP code do the events show?

17. **Scenario C — App starts before Postgres is ready.** Remove the readiness probe from the Postgres Deployment entirely and reapply everything from scratch. What happens to the app pod on the first try? How does adding the probe back fix it?

### Part 6 — Reflection questions

Answer in `answers.md`:

1. Explain the difference between readiness, liveness, and startup probes in one or two lines each.
2. Why would it be wrong to run Postgres as a Deployment with `replicas: 3`? What breaks?
3. What is the full chain of objects involved in getting a 5Gi disk attached to a Postgres container? List them in order.
4. Your PVC is stuck in `Pending` and `kubectl describe pvc` says "waiting for a volume to be created." What are the top three things you would check?
5. When would you choose RDS over running Postgres on Kubernetes? When would you choose the opposite?

### Submission

- All YAMLs under `day3/k8s/`
- Screenshots of the working app and the data persistence test in `screenshots/`
- Written answers in `answers.md`
- Push before the next class

---

## What's Next

Class 4 covers rolling upgrades, rollback strategies, GitOps with Argo CD, and how `kubeconfig` + multi-cluster access actually work. We'll deploy the same app through Argo CD instead of `kubectl apply`, and see why GitOps exists in the first place.

---

*LivingDevOps — livingdevops.com*
