# frontend_deployment.tf

resource "kubernetes_deployment" "frontend" {
  metadata {
    name      = "frontend"
    namespace = var.app_namepace
    labels = {
      app = "frontend"
    }
  }

  depends_on = [
    kubernetes_config_map.frontend,
    kubernetes_namespace.namespace
  ]

  spec {
    replicas = 2

    selector {
      match_labels = {
        app = "frontend"
      }
    }

    strategy {
      type = "RollingUpdate"
      rolling_update {
        max_surge       = "1"
        max_unavailable = "0"
      }
    }

    template {
      metadata {
        labels = {
          app = "frontend"
        }
      }

      spec {
        node_selector = {
          "kubernetes.io/arch" = "amd64"
        }

        container {
          name              = "frontend"
          image             = var.frontend_image
          image_pull_policy = "Always"

          port {
            container_port = 80
          }

          env {
            name = "BACKEND_URL"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.frontend.metadata[0].name
                key  = "BACKEND_URL"
              }
            }
          }

          resources {
            requests = {
              memory = "128Mi"
              cpu    = "100m"
            }
            limits = {
              memory = "256Mi"
              cpu    = "300m"
            }
          }

          readiness_probe {
            http_get {
              path = "/health"
              port = 80
            }
            initial_delay_seconds = 10
            period_seconds        = 5
          }

          liveness_probe {
            http_get {
              path = "/health"
              port = 80
            }
            initial_delay_seconds = 15
            period_seconds        = 20
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "frontend" {
  metadata {
    name      = "frontend"
    namespace = var.app_namepace
  }

  depends_on = [kubernetes_namespace.namespace]

  spec {
    selector = {
      app = "frontend"
    }

    port {
      port        = 80
      target_port = 80
    }

    type = "LoadBalancer"
  }
}