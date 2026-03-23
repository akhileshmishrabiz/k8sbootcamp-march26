# Week 1 - Class 2 Assignment: Secrets, ConfigMaps & Autoscaling
**LivingDevOps | Advanced Kubernetes on AWS/EKS Bootcamp**

> All YAML files are already in the repository. Your job is to read them, understand them, apply them, and answer the questions below.

---

## Part 1: Deploy the Student Portal App

1. Go to the `k8s/` folder in your repository and open `deployment.yaml`. Read it fully before applying anything.

2. The app uses a private ECR image. Before applying the deployment, you need to authenticate with ECR and create an image pull secret. Run the command from class to create the `ecr-secret` in your cluster.

3. Apply the deployment. Check the pod status. It will fail — that is expected. Read the events section and note down **why** it failed.

4. The app needs a database connection. Go to your AWS account, find the RDS endpoint from class, and build the DB link string in this format:
   ```
   postgresql://username:password@host:5432/dbname
   ```

5. Apply the secret file. Apply the configmap file. Apply the deployment again. Watch the pods come up.

6. Once pods are running, apply the service file. Confirm the service is created with `kubectl get svc`.

7. Port-forward the service to your local machine and open the app in your browser. Register an account and add one student. Take a screenshot.

---

## Part 2: Break Things on Purpose

These are interview scenarios. Do each one, observe the output, note what command helped you find the issue.

**Scenario 1 — Missing secret**
- Delete the secret you created.
- Delete the running pod (not the deployment).
- Watch the new pod come up. What state does it go into? What does `kubectl describe pod` tell you?
- Re-apply the secret and confirm the pod recovers.

**Scenario 2 — Wrong image tag**
- Edit the deployment image to use a tag that does not exist (example: `nginx:doesnotexist999`).
- Apply the change. What error do you see in pod events?
- Which command shows you the error — `kubectl logs` or `kubectl describe`? Why?
- Fix the image and apply again.

**Scenario 3 — Missing configmap**
- Delete the configmap.
- Delete one pod manually so a new one is forced to start.
- What happens? Note the exact error message.
- Re-apply the configmap to fix it.

---

## Part 3: Resources — Request and Limit

1. Open `deployment.yaml` and find the `resources` block. Write down:
   - What does `requests.cpu` mean in one line?
   - What does `limits.memory` mean in one line?
   - If a node does not have enough memory to satisfy the request, what happens to the pod?

2. Change the memory request to something unrealistically high (example: `8Gi`). Apply the deployment. What state does the pod go into? What does `kubectl describe pod` say in the Events section?

3. Revert to a sensible value and apply again.

4. Answer this: If you set `requests.cpu: 100m` and `limits.cpu: 1000m`, what does that mean in plain English?

---

## Part 4: HPA — Horizontal Pod Autoscaler

1. Make sure the metrics-server addon is enabled in Minikube. Run:
   ```
   minikube addons enable metrics-server
   ```
   Wait 60 seconds, then run `kubectl top pods`. You should see CPU and memory numbers.

2. Apply the HPA file from the repo. Confirm it was created with:
   ```
   kubectl get hpa
   ```
   Note what it shows in the `TARGETS` column.

3. Wait 2-3 minutes. Check HPA again. If load is low, how many replicas does it maintain?

4. Now generate some load. Log into the Minikube container and run curl in a loop against your service ClusterIP:
   ```
   for i in $(seq 1 500); do curl -s http://<CLUSTER-IP>:8080 > /dev/null; done
   ```
   While that runs, watch your pods in another terminal:
   ```
   kubectl get pods -w
   ```
   Do you see new pods being created? Screenshot the output.

5. Stop the load. Wait for the stabilization window. Check if pods scale back down.

---

## Part 5: VPA — Vertical Pod Autoscaler (Self-Study Task)

The VPA config is in the repo. Read it and answer these questions without running it:

- What is the difference between HPA and VPA in one sentence?
- When would you prefer VPA over HPA?
- What does the `updateMode: Auto` setting mean in the VPA config?

Apply the VPA. Check its recommendation after a few minutes:
```
kubectl describe vpa
```
Note what CPU and memory values it recommends for your container.

---

## Part 6: Reflection Questions

Answer these in your own words. 3-5 lines each.

1. You have a pod that keeps restarting (CrashLoopBackOff). Walk through the exact commands you would run to troubleshoot it, in order.

2. A teammate says "just put the database password directly in the deployment YAML". What do you tell them and why?

3. What is the difference between `kubectl apply` and `kubectl create`? When would you use each?

4. Your HPA is configured but `kubectl get hpa` shows `TARGETS: <unknown>`. What is likely missing and how do you fix it?

---

## Submission

- Push all your YAML files to your GitHub repo in the `k8s/` folder.
- Save screenshots in a `/screenshots` folder — one per numbered task that asks for it.
- Write your answers to Part 3 question 4, Part 5 questions, and all of Part 6 in a file called `answers.md`.
- Post the repo link in the group before the next class.

---

*LivingDevOps — livingdevops.com*