terraform {
    backend "s3" {
        bucket         = "state-bucket-879381241087"
        key            = "k8sbootcamp-march26/eks/eks-infra/terraform.tfstate"
        region         = "ap-south-1"
        encrypt        = true
        use_lockfile = true
    }
}