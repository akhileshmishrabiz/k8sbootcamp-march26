<service-name>.<namespace>.svc.<cluster-domain>

dns for db service -> service name
-> postgres-service.default.svc.cluster.local




 # DB_LINK=postgresql://postgres:password@db:5432/mydb

 DB_link=postgresql://postgres:password@postgres-service.default.svc.cluster.local:5432/mydb




eval $(minikube docker-env)
 minikube image load week2app:2.0


```bash
# kubectl set image deployments/<deployment-name> <container-name>=<image-repo>/<image-name>:<tag>
kubectl set image deployments/kubernetes-bootcamp kubernetes-bootcamp=docker.io/jocatalin/kubernetes-bootcamp:v2
```

kubectl set image deployments/studentportal studentportal-container=app:2.1

kubectl rollout status deployments/studentportal

kubectl set image deployments/kubernetes-bootcamp kubernetes-bootcamp=gcr.io/google-samples/kubernetes-bootcamp:v10


# old image app:1.0 and new image app:2.1
kubectl set image deployments/studentportal studentportal-container=app:1.0


# app image 

879381241087.dkr.ecr.ap-south-1.amazonaws.com/jan26week5-studentportal


docker build  --platform linux/amd64 -t 879381241087.dkr.ecr.ap-south-1.amazonaws.com/jan26week5-studentportal:latest 

docker build -t  879381241087.dkr.ecr.ap-south-1.amazonaws.com/jan26week5-studentportal:latest 

aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 879381241087.dkr.ecr.ap-south-1.amazonaws.com



kubectl create secret docker-registry ecr-secret \
  --docker-server=879381241087.dkr.ecr.ap-south-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region ap-south-1) 


  docker build -t  879381241087.dkr.ecr.ap-south-1.amazonaws.com/jan26week5-studentportal:argosync  .

# argocd setup
https://gist.github.com/bhimsur/b6c575916883ff7712861beacbe1ff0b

kubectl create namespace argocd

kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/v2.8.4/manifests/install.yaml


