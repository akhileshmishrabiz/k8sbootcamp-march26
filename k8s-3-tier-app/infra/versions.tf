

terraform {
 
  required_version = "1.12.1"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
}


terraform {
    backend "s3" {
        bucket         = "state-bucket-879381241087"
        key            = "k8sbootcamp-march26/3tierapp-k8s/terraform.tfstate"
        region         = "ap-south-1"
        encrypt        = true
        use_lockfile = true
    }
}