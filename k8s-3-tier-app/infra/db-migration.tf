# deployment.tf

resource "kubernetes_deployment" "backend" {
  metadata {
    name      = "backend"
    namespace = var.app_namepace
    labels = {
      app = "backend"
    }
  }

  depends_on = [
    kubernetes_config_map.backend,
    kubernetes_secret.backend,
    kubernetes_namespace.namespace
  ]

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "backend"
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
          app = "backend"
        }
      }

      spec {
        node_selector = {
          "kubernetes.io/arch" = "amd64"
        }

        init_container {
          name    = "wait-for-db"
          image   = "busybox"
          command = [
            "sh", "-c",
            "until nslookup ${aws_db_instance.postgres.address}; do echo waiting for database; sleep 2; done;"
          ]
        }

        container {
          name              = "backend"
          image             = var.backend_image
          image_pull_policy = "Always"

          port {
            container_port = 8000
          }

          # ── Plain value envs ──────────────────────────────────────────

          env {
            name = "FLASK_APP"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.backend.metadata[0].name
                key  = "FLASK_APP"
              }
            }
          }

          env {
            name = "FLASK_DEBUG"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.backend.metadata[0].name
                key  = "FLASK_DEBUG"
              }
            }
          }

          env {
            name = "ALLOWED_ORIGINS"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.backend.metadata[0].name
                key  = "ALLOWED_ORIGINS"
              }
            }
          }

          env {
            name = "DB_HOST"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.backend.metadata[0].name
                key  = "DB_HOST"
              }
            }
          }

          env {
            name = "DB_PORT"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.backend.metadata[0].name
                key  = "DB_PORT"
              }
            }
          }

          env {
            name = "DB_NAME"
            value_from {
              config_map_key_ref {
                name = kubernetes_config_map.backend.metadata[0].name
                key  = "DB_NAME"
              }
            }
          }

          # ── Secret envs ───────────────────────────────────────────────

          env {
            name = "DATABASE_URL"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.backend.metadata[0].name
                key  = "DATABASE_URL"
              }
            }
          }

          env {
            name = "SECRET_KEY"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.backend.metadata[0].name
                key  = "SECRET_KEY"
              }
            }
          }

          env {
            name = "DB_USERNAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.backend.metadata[0].name
                key  = "DB_USERNAME"
              }
            }
          }

          env {
            name = "DB_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.backend.metadata[0].name
                key  = "DB_PASSWORD"
              }
            }
          }

          resources {
            requests = {
              memory = "256Mi"
              cpu    = "100m"
            }
            limits = {
              memory = "512Mi"
              cpu    = "500m"
            }
          }
        }
      }
    }
  }
}

# service.tf

resource "kubernetes_service" "backend" {
  metadata {
    name      = "backend"
    namespace = var.app_namepace
  }

  depends_on = [kubernetes_namespace.namespace]

  spec {
    selector = {
      app = "backend"
    }

    port {
      port        = 8000
      target_port = 8000
    }

    type = "ClusterIP"
  }
}