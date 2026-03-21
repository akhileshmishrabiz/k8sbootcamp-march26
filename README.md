# k8sbootcamp-march26

curl -O https://s3.us-west-2.amazonaws.com/amazon-eks/1.33.8/2026-02-27/bin/darwin/amd64/kubectl


# minikube install

curl -LO https://github.com/kubernetes/minikube/releases/latest/download/minikube-linux-amd64

sudo install minikube-linux-amd64 /usr/local/bin/minikube && rm minikube-linux-amd64

#  start the cluster
minikube start

minikube status

kubectl run web-server --image=nginx

kubectl create deployment nginxwith-dep --image=nginx --replicas=3

kubectl scale deployment nginxwith-dep --replicas=5