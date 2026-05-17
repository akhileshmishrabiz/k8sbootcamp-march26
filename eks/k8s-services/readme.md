# IRSA  aws IAM -> K8s oidc
# aws load balancer controller 




# pod identity -> 

# logging-monitoring
- kube-prometheus-stack (Prometheus, Grafana, Alertmanager, node-exporter, kube-state-metrics)
- Loki + Promtail (via loki-stack chart) — logs shipped to Loki, queryable in Grafana as the "Loki" datasource
- Ingress: grafana.<domain>, prometheus.<domain> (ALB, ACM TLS, shared group `k8sbatch-shared-alb`)
