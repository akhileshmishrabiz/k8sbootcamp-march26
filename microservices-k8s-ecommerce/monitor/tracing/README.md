# Tracing stack (Tempo + OTel Collector)

Off by default. Apply alongside the rest of the monitoring stack:

```bash
kubectl apply -f monitor/tracing/
```

Then enable instrumentation env vars on the apps:

```bash
helm upgrade ecommerce-vault ./helm-cnpg-vault -n ecommerce --set tracing.enabled=true
kubectl rollout restart deploy -n ecommerce
```

Or use the deploy script:

```bash
TRACING_ENABLED=true ./helm-cnpg-vault-deploy.sh
```

## What gets deployed

| Component | Purpose | Endpoint |
|---|---|---|
| `tempo` | Trace storage + query API | `tempo.monitoring:3200` (HTTP) / `:4317` (OTLP gRPC) |
| `otel-collector` | Receives OTLP from apps, batches, exports to Tempo | `otel-collector.monitoring:4318` (OTLP HTTP) |

App pods send to `http://otel-collector.monitoring.svc.cluster.local:4318` (set via `OTEL_EXPORTER_OTLP_ENDPOINT` from Helm). The collector debug exporter logs every received span to stdout for quick troubleshooting:

```bash
kubectl logs -n monitoring deploy/otel-collector -f
```

## Viewing traces

Grafana → Explore → Tempo datasource → search by service name. Or use TraceQL:

```
{ resource.service.name = "order-service" && duration > 100ms }
```

## Storage

Tempo uses local `emptyDir` storage — traces are lost when the pod restarts. Adequate for demos. For longer retention add a PVC or point Tempo at object storage (S3/GCS/MinIO).
