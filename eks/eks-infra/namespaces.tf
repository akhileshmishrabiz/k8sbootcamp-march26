# terraform {
#     required_providers {
#         kubernetes = {
#             source  = "hashicorp/kubernetes"
#             version = "~> 2.0"
#         }
#     }
# }

# provider "kubernetes" {
#     config_path = "~/.kube/config"
# }

# resource "kubernetes_namespace" "example" {
#     metadata {
#         name = "example-namespace"
#     }
# }

# resource "kubernetes_namespace" "development" {
#     metadata {
#         name = "development"
#         labels = {
#             environment = "dev"
#         }
#     }
# }

# resource "kubernetes_namespace" "production" {
#     metadata {
#         name = "production"
#         labels = {
#             environment = "prod"
#         }
#     }
# }