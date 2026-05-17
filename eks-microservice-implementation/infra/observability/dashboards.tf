# Grafana picks up any ConfigMap in any namespace labeled `grafana_dashboard: "1"`
# via the sidecar that kube-prometheus-stack enables by default. We put the
# ConfigMaps in the monitoring namespace alongside Grafana itself.

locals {
  dashboards = {
    "ecommerce-overview" = {
      file  = "${path.module}/dashboards/ecommerce-overview.json"
      title = "Ecommerce — Service RED"
    }
    "ecommerce-logs" = {
      file  = "${path.module}/dashboards/ecommerce-logs.json"
      title = "Ecommerce — Logs (Loki)"
    }
  }
}

resource "kubernetes_config_map_v1" "dashboard" {
  for_each = local.dashboards

  metadata {
    name      = "ecommerce-${each.key}"
    namespace = var.monitoring_namespace
    labels = {
      grafana_dashboard           = "1"
      "app.kubernetes.io/part-of" = "ecommerce"
    }
    annotations = {
      "k8s-sidecar-target-directory" = "/tmp/dashboards/ecommerce"
    }
  }

  data = {
    "${each.key}.json" = file(each.value.file)
  }
}
