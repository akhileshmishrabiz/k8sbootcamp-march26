# resource "kubernetes_ingress_v1" "app_ingress" {
#   metadata {
#     name      = "app-ingress"
#     namespace = var.app_namepace
#     annotations = {
#       "alb.ingress.kubernetes.io/scheme"           = "internet-facing"
#       "alb.ingress.kubernetes.io/target-type"      = "ip"
#       "alb.ingress.kubernetes.io/listen-ports"     = "[{\"HTTP\": 80}]"
#       "alb.ingress.kubernetes.io/healthcheck-path" = "/health"
#     }
#   }

#   depends_on = [
#     kubernetes_namespace.namespace,
#     kubernetes_service.frontend,
#     kubernetes_service.backend
#   ]

#   spec {
#     ingress_class_name = "alb"

#     rule {
#       http {
#         # Route for backend API
#         path {
#           path      = "/api"
#           path_type = "Prefix"
#           backend {
#             service {
#               # name of backend service
#               name = kubernetes_service.backend.metadata[0].name
#               port {
#                 number = 8000
#               }
#             }
#           }
#         }

#         # Route for frontend (default)
#         path {
#           path      = "/"
#           path_type = "Prefix"
#           backend {
#             service {
#               # frontend service name 
#               name = kubernetes_service.frontend.metadata[0].name
#               port {
#                 number = 80
#               }
#             }
#           }
#         }
#       }
#     }
#   }
# }

# output "ingress_hostname" {
#   description = "The ALB hostname for accessing the application"
#   value       = try(kubernetes_ingress_v1.app_ingress.status[0].load_balancer[0].ingress[0].hostname, "pending")
# }
