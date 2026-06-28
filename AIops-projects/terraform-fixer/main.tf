resource "aws_s3_bucket" "demo" {
  bucket        = var.bucket_name
  force_destroy = true

  versioning {
    enabled = true
  }

  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "aws:kms"
      }
    }
  }

  logging {
    target_bucket = var.logging_bucket_name
    target_prefix = "${var.bucket_name}/"
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "demo" {
  bucket = aws_s3_bucket.demo.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_replication_configuration" "demo" {
  bucket = aws_s3_bucket.demo.id

  role = aws_iam_role.replication_role.arn

  rules {
    id     = "replication_rule"
    status = "Enabled"

    destination {
      bucket        = var.replication_bucket_arn
      storage_class = "STANDARD"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "demo" {
  bucket = aws_s3_bucket.demo.id

  rule {
    id     = "lifecycle_rule"
    status = "Enabled"

    expiration {
      days = 30
    }
  }
}

resource "aws_iam_role" "replication_role" {
  name = "replication_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Principal = {
        Service = "s3.amazonaws.com"
      }
      Effect    = "Allow"
      Sid       = ""
    }]
  })
}
