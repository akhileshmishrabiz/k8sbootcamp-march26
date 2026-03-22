user: postgres
password: admin1234
host: studentportal.cvik8accw2tk.ap-south-1.rds.amazonaws.com
db_name: postgres 

DB_LINK=postgresql://postgres:admin1234@studentportal.cvik8accw2tk.ap-south-1.rds.amazonaws.com:5432/postgres

# minkkube deployment
image: 879381241087.dkr.ecr.ap-south-1.amazonaws.com/jan26week5-studentportal:1.0

aws configure

aws ecr describe-images --repository-name jan26week5-studentportal


aws ecr get-login-password --region ap-south-1 | docker login --username AWS --password-stdin 879381241087.dkr.ecr.ap-south-1.amazonaws.com

# k8s secret that has value of ecr pull image token



kubectl create secret docker-registry ecr-secret \
  --docker-server=879381241087.dkr.ecr.ap-south-1.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region ap-south-1) 



kubectl port-forward svc/studentportal 8080:80 
#                    ^^^^^^^^^^^ ^^^^ ^^    ^^^^^^^^^^^^^
#                    service     local target 
#                    name        port  port



echo -n "postgresql://postgres:admin1234@studentportal.cvik8accw2tk.ap-south-1.rds.amazonaws.com:5432/postgres"

use the base64 encoded value for secret