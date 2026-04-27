variable "vpc_id" {
  default = "vpc-0f1a45e06a643cab8"
}
variable "aws_region" {
  default = "ap-south-1"
}

variable "rds_subnet_cidrs" {
    type    = list(string)
    default = ["10.0.201.0/24", "10.0.202.0/24"]
}

variable "cluster_name" {
  default = "eks-cluster"
}

variable "backend_image" {
  default = "879381241087.dkr.ecr.ap-south-1.amazonaws.com/devopsdozo-backend:latest"
}

variable "frontend_image" {
  default = "879381241087.dkr.ecr.ap-south-1.amazonaws.com/devopsdozo-frontend:latest"
}

variable "app_namepace" {
  default = "k8s-3tier-app"
}

variable "vpc_name" {
  default = "eks-vpc"
}

variable "domain_name" {
  description = "Main domain name for the application"
  type        = string
  default     = "livingdevops.org"
}

variable "app_subdomain" {
  description = "Subdomain for the application"
  type        = string
  default     = "devopsdojo"
}

# devopsdojo.livingdevops.org

variable "aws_alb_zoneid" {
  default = "ZP97RAFLXTNZK"
}