# Intentionally minimal — missing versioning, encryption, public access block, logging, etc.
resource "aws_s3_bucket" "demo" {
  bucket        = var.bucket_name
  force_destroy = true
}
