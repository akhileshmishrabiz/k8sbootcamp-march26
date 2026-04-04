EKS cluster is ready, now need to talk to it

# list eks cluster in a region
```bash
aws eks list-clusters --region ap-south-1
```
"clusters": [ "demo"]

# talk to eks cluster
```bash
aws eks update-kubeconfig --region ap-south-1 --name demo
```
```bash
kubectl config current-context
```

arn:aws:eks:ap-south-1:879381241087:cluster/demo

 kubectl config get-contexts

 kubectl config rename-context arn:aws:eks:ap-south-1:879381241087:cluster/demo demo

 kubectl config use-context minikube
 
 kubectl config use-context demo