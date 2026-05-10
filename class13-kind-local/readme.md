# build the lcoal images before deployment else kind wornt find the images


```bash
cd microservices-k8s-ecommerce/apps
# From project root
docker build -t product-service:local ./services/product-service
docker build -t user-service:local ./services/user-service
docker build -t cart-service:local ./services/cart-service
docker build -t order-service:local ./services/order-service
docker build -t payment-service:local ./services/payment-service
docker build -t notification-service:local ./services/notification-service
docker build -t frontend:local ./frontend

# build image for seed job
cd class13/seed-job
docker build -t ms-ecom-seed:latest .
```

``` bash
kind load docker-image product-service:local --name ecom-ms
kind load docker-image user-service:local --name ecom-ms
kind load docker-image cart-service:local --name ecom-ms
kind load docker-image order-service:local --name ecom-ms
kind load docker-image payment-service:local --name ecom-ms
kind load docker-image notification-service:local --name ecom-ms
kind load docker-image frontend:local --name ecom-ms
kind load docker-image ms-ecom-seed:latest --name ecom-ms
```


# once app is up, run the seed job
``` bash
cd class13/seed-job
kubectl apply -f seed-job.yaml

```