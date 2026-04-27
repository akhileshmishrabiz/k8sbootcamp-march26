# Week 2 — Class 4: Rolling Upgrades, GitOps & Argo CD

**LivingDevOps | Advanced Kubernetes on AWS/EKS Bootcamp**

Last class we deployed Postgres with persistent storage. Today we push a new app version, roll it back, and then throw away the whole manual approach and move to GitOps with Argo CD — the way real teams actually deploy in production. Still on Minikube; EKS starts next week.

---

## Topics Covered

### 1. Rolling upgrades — the default deployment strategy
When you update a Deployment's image, Kubernetes does a rolling upgrade by default: one new pod comes up, passes health checks, then one old pod is killed — repeated until all pods are on the new version. Zero downtime if probes are set up correctly. For big apps like Netflix, more advanced strategies (blue/green, canary) exist and we cover them later.

### 2. `kubectl set image` and `kubectl rollout`
The old-school way to trigger an upgrade from the CLI:
```
kubectl set image deployment/<name> <container>=<new-image:tag>
kubectl rollout status deployment/<name>
kubectl rollout undo deployment/<name>
```
`set image` pushes the new image, `rollout status` watches it happen, `rollout undo` rolls back to the previous version. This was the Jenkins-era deployment pattern.

### 3. Why the Jenkins-and-kubectl approach is broken
Four concrete problems:
- **Hardcoded credentials** — Jenkins has to hold kubeconfigs for every cluster. With 5+ clusters and multiple namespaces this becomes a secret-management nightmare.
- **No single source of truth** — Someone can `kubectl edit` a running Deployment and nothing will tell the team. The pipeline thinks v2 is running; reality is v1.
- **Drift** — the gap between "what's in Git" and "what's actually running." In a Jenkins-push world, drift is silent and invisible.
- **RBAC burden** — giving developers and stakeholders read access to the cluster means managing kubeconfig files, certificates, and RBAC rules for every person. Painful at scale.

### 4. What GitOps actually means
Git is the single source of truth. Whatever is committed to the manifest repo is what runs in the cluster — guaranteed by an agent sitting inside the cluster that continuously reconciles the two. If someone edits a Deployment manually, the agent reverts it. If Git is updated, the cluster is updated. No humans running `kubectl apply` in production.

### 5. Argo CD — the GitOps agent
Argo CD is a Kubernetes controller that watches a Git repo and applies whatever YAML it finds. It runs as a set of pods in its own namespace (`argocd`) and exposes a web dashboard. Stakeholders, developers, and QA can all look at the same dashboard and see what's actually deployed — no cluster access needed.

### 6. Custom Resource Definitions (CRDs)
Argo CD introduces a custom resource called `Application` — Kubernetes doesn't know about "applications" natively. CRDs are how tools extend the Kubernetes API with their own object types. Same mechanism Prometheus, cert-manager, and every other operator uses. You don't need to know the internal architecture of Argo CD — you need to know how to use its resources.

### 7. Namespaces — isolation and organization
Namespaces serve two purposes:
- **Isolation**: resources in namespace A can't use secrets or services from namespace B. Useful when multiple teams share one cluster.
- **Organization**: even within one team, grouping related resources (all monitoring stuff in `monitoring`, all Argo stuff in `argocd`) makes operations dramatically easier.

Not every microservice needs its own namespace. With small teams owning multiple services, putting them in one namespace is fine. Use namespaces when the scope is genuinely different — different teams, different environments, different customers.

### 8. ResourceQuota — limiting namespace consumption
If you're running a multi-tenant cluster (think shared WordPress hosting), you can cap how much CPU/memory/storage a namespace can consume. Most in-house teams don't bother; multi-tenant platforms absolutely need it.

### 9. The `kubeconfig` file is about *you*, not the cluster
Common misconception: kubeconfig lives in the cluster. It doesn't. It lives on *your* machine (or in CI) and describes which clusters *you* have access to. Multiple clusters → multiple contexts in one file → `kubectl config use-context` to switch between them. Freelens reads the same file.

### 10. Authenticating CI to clusters — OIDC vs hardcoded credentials
Old way: store AWS access keys in Jenkins/GitHub Actions → brittle, rotatable, leakable. Modern way: **OIDC federation** between GitHub Actions and AWS IAM. GitHub Actions gets a short-lived token, assumes an IAM role, talks to EKS. No long-lived secrets anywhere. On the EKS side, IRSA (IAM Roles for Service Accounts) does the equivalent for in-cluster workloads. Pod Identity is the newer replacement, but IRSA is still what most production systems use.

### 11. Argo CD Application — the object that deploys your app
An `Application` CR tells Argo CD:
- Which Git repo and branch to watch
- Which folder in the repo contains the manifests
- Which cluster and namespace to deploy to
- Whether to sync automatically and whether to self-heal

With `syncPolicy.automated.selfHeal: true`, any manual edit in the cluster is reverted within seconds. This is what makes Git the real source of truth.

### 12. Image tagging strategy — commit SHA, not `latest`
Never use `latest` in production. Always tag images with the Git commit SHA of the build. Two reasons:
- Every deploy maps to an exact commit — fully traceable.
- `imagePullPolicy: Always` works correctly because the tag itself changes on every commit; Kubernetes isn't fooled into thinking the "same" image is still fresh.

The CI pipeline does two things on every commit: build and push the image, then update the image tag in the manifest file and commit that change. Argo CD sees the Git change and rolls out.

### 13. Multi-environment patterns
Two common patterns for managing dev/staging/prod with one repo:
- **Branch per environment** — `argo-dev`, `argo-test`, `argo-prod` branches. Each Argo CD Application watches a different branch.
- **Folder per environment** — `envs/dev/`, `envs/staging/`, `envs/prod/` in one branch. Each Application watches a different path.

Both work. Team preference. You never give CI permission to push directly to `main` with image-tag bumps — that's what the environment branches or per-env folders are for.

---

## Exercise

Everything still runs on Minikube. You'll need a public GitHub repo for this one — the Argo CD app needs to read manifests from somewhere.

### Part 1 — Rolling upgrade with `kubectl set image`

1. Clean slate. Wipe anything left from Class 3 if you haven't already:
   ```bash
   kubectl delete deployment --all
   kubectl delete pvc --all
   ```

2. Redeploy Postgres (same files as Class 3) and the app with `image: app:1.0`:
   ```bash
   eval $(minikube docker-env)
   cd app/ && docker build -t app:1.0 .
   kubectl apply -f day3/k8s/postgres/
   kubectl apply -f day3/k8s/app/
   ```

3. Port-forward, register an account, add some test data.

4. Make a cosmetic change to the app — change the title in `app/templates/base.html` and maybe remove one nav item. Build version 2.1:
   ```bash
   docker build -t app:2.1 .
   ```

5. Trigger a rolling upgrade without editing any YAML:
   ```bash
   kubectl set image deployment/studentportal studentportal-container=app:2.1
   kubectl rollout status deployment/studentportal
   ```
   Watch the pods in Freelens. Screenshot the moment where both old and new pods are running.

6. Open the app — confirm the new title and nav are live.

7. Something is wrong — stakeholders complain the removed nav item was important. Roll back:
   ```bash
   kubectl rollout undo deployment/studentportal
   ```
   Confirm the old nav is back.

### Part 2 — Install Argo CD

8. Create the namespace and install Argo CD with the upstream manifest:
   ```bash
   kubectl create namespace argocd
   kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   ```

9. Wait for the pods to come up — it pulls several images, give it 2-3 minutes:
   ```bash
   kubectl get pods -n argocd -w
   ```

10. Port-forward the Argo CD server to your laptop:
    ```bash
    kubectl port-forward svc/argocd-server -n argocd 8080:80
    ```

11. Get the initial admin password:
    ```bash
    kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
    ```

12. Open `http://localhost:8080` in your browser. User `admin`, password from step 11. Screenshot the empty dashboard.

### Part 3 — Create your first Argo CD Application (via UI)

13. Push the `day3/k8s/argo-app/` folder from the repo to your own GitHub (public is fine for this exercise).

14. In the Argo CD UI, click **+ NEW APP**. Fill in:
    - **Application name**: `studentportal`
    - **Project**: `default`
    - **Sync policy**: `Manual` (we'll make it automatic in the next part)
    - **Repository URL**: your GitHub repo URL
    - **Revision**: `HEAD`
    - **Path**: `day3/k8s/argo-app`
    - **Cluster**: `https://kubernetes.default.svc` (in-cluster)
    - **Namespace**: `default`

15. Click **CREATE**. The app appears as `OutOfSync`. Click **SYNC** — watch the pods come up in the `default` namespace.

16. Break the source-of-truth principle on purpose: scale the Deployment manually:
    ```bash
    kubectl scale deployment studentportal --replicas=5
    ```
    Argo CD now shows `OutOfSync`. Click sync — it reverts to whatever the Git manifest says (2 replicas).

### Part 4 — Convert to declarative GitOps with auto-sync

Manual sync is not GitOps. Make it automatic.

17. Delete the UI-created Application:
    ```bash
    kubectl delete application studentportal -n argocd
    ```

18. Read `day3/k8s/argocd/application.yaml` — note the `syncPolicy.automated` block with `prune: true` and `selfHeal: true`.

19. Apply it:
    ```bash
    kubectl apply -f day3/k8s/argocd/application.yaml
    ```

20. Test self-healing. In one terminal, watch pods:
    ```bash
    kubectl get pods -w
    ```
    In another, delete a pod:
    ```bash
    kubectl delete pod -l app=studentportal
    ```
    Argo CD recreates it almost instantly, no manual sync.

21. Test drift correction. Scale manually again:
    ```bash
    kubectl scale deployment studentportal --replicas=7
    ```
    Within ~30 seconds Argo CD reverts it. Screenshot before and after.

### Part 5 — Trigger a real deployment by updating Git

22. Build and push a new image to your ECR repo (same flow as Class 2) with a commit-SHA-style tag:
    ```bash
    aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin <your-account>.dkr.ecr.ap-south-1.amazonaws.com
    docker build --platform linux/amd64 -t <your-ecr>/studentportal:argosync .
    docker push <your-ecr>/studentportal:argosync
    ```

23. Edit `day3/k8s/argo-app/deployment.yaml` in your repo — update the image tag to `:argosync`. Commit and push.

24. Either wait (Argo CD polls every ~3 minutes) or force it with:
    ```bash
    argocd app sync studentportal
    ```
    Or just click Refresh → Sync in the UI.

25. Watch the rolling upgrade happen — this time driven entirely by Git. Screenshot the sync timeline in the Argo CD UI.

### Part 6 — Break things and debug

26. **Bad image in Git.** Edit the Deployment in your repo, change the image tag to something that doesn't exist, commit and push. What happens in the Argo CD UI? Is the app marked `Healthy`, `Degraded`, `OutOfSync`, or something else? Fix it by pushing a correct tag.

27. **Self-heal vs no self-heal.** Edit your Application manifest, remove `selfHeal: true` (keep `automated`), reapply. Now delete a pod manually. Does Argo CD bring it back? What is the practical difference?

28. **Unreachable Git repo.** Temporarily rename your GitHub repo in the GitHub settings. What does Argo CD show? How fast does it notice? Rename it back.

### Part 7 — Reflection questions

Answer in `answers.md`:

1. Explain in two or three lines what "drift" means and why GitOps eliminates it.
2. What is the difference between Argo CD's `prune` and `selfHeal` sync options?
3. Your team has five environments (dev, test, stage, pre-prod, prod). Sketch — in words — how you would structure your manifest repo and Argo CD Applications. Branches, folders, separate Argo CD instances?
4. Why is `image: myapp:latest` a bad idea in production even with `imagePullPolicy: Always`?
5. A developer asks you for `kubectl` access to production so they can check why their service is down. In a GitOps-based world, what do you give them instead?

### Submission

- All YAMLs in your repo under `day3/k8s/` and `day3/k8s/argocd/`
- A basic GitHub Actions workflow file under `.github/workflows/` that builds and pushes the image (even a draft is fine — we cover CI properly next week)
- Screenshots of: rolling upgrade in progress, Argo CD empty dashboard, first sync, self-heal correcting a manual edit, the Git-driven deployment
- Written answers in `answers.md`
- Push before the next class

---

## What's Next

From Week 3 onward everything runs on a real EKS cluster. We'll set it up with Terraform, configure OIDC between GitHub Actions and AWS, build a proper CI/CD pipeline that updates image tags in Git, and deploy microservices with Argo CD. Also covered: ingress controllers, external DNS, cert-manager, and the monitoring stack (Prometheus + Grafana + Loki) properly — no more Minikube shortcuts.

Before the next class: re-watch today's recording, read the [Argo CD core concepts docs](https://argo-cd.readthedocs.io/en/stable/core_concepts/), and make sure you're comfortable with basic GitHub Actions syntax — the CI work starts next week.

---

*LivingDevOps — livingdevops.com*
