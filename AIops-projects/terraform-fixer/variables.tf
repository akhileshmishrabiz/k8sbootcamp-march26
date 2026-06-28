variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "bucket_name" {
  type    = string
  default = "terraform-fixer-demo-bucket"
}

variable "logging_bucket_name" {
  type    = string
  default = "terraform-fixer-demo-logging-bucket"
}

variable "replication_bucket_arn" {
  type    = string
  default = "arn:aws:s3:::terraform-fixer-demo-replication-bucket"
}
