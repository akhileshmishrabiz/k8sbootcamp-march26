# data source for vpc id
data "aws_vpc" "main" {
  id = var.vpc_id
}


