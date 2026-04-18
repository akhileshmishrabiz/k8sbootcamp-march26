variable "cluster_name" {
  default = "eks-cluster"
}

variable "vpc_name" {
  default = "eks-vpc"
}

variable "region" {
  default = "ap-south-1"
}

variable "awsloadbalancercontroller_sa" {
  default = "aws-load-balancer-controller"
}