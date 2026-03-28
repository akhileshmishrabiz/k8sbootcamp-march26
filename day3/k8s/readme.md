<service-name>.<namespace>.svc.<cluster-domain>

dns for db service -> service name
-> postgres-service.default.svc.cluster.local




 # DB_LINK=postgresql://postgres:password@db:5432/mydb

 DB_link=postgresql://postgres:password@postgres-service.default.svc.cluster.local:5432/mydb




eval $(minikube docker-env)
 minikube image load week2app:2.0