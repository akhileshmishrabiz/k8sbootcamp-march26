variable "cluster_name" {
  default = "eks-cluster"
}

variable "region" {
  default = "ap-south-1"
}

variable "domain_name" {
  default = "livingdevops.org"
}

variable "aws_alb_zoneid" {
  default = "ZP97RAFLXTNZK"
}

variable "acm_cert_arn" {
  default = "arn:aws:acm:ap-south-1:879381241087:certificate/d7c449d8-1540-4157-8959-bc48bb44b128"
}
