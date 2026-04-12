# rds db

# certain subnets

resource "aws_subnet" "private_1" {
  vpc_id            = data.aws_vpc.main.id
  cidr_block        = var.rds_subnet_cidrs[0]
  availability_zone = "${var.aws_region}a"

  tags = {
    Name = "k8s-3tier-private-1"
  }
}

resource "aws_subnet" "private_2" {
  vpc_id            = data.aws_vpc.main.id
  cidr_block        = var.rds_subnet_cidrs[1]
  availability_zone = "${var.aws_region}b"

  tags = {
    Name = "k8s-3tier-private-2"
  }
}
resource "aws_db_subnet_group" "main" {
  name       = "k8s-3tier-db-subnet-group"
  subnet_ids = [aws_subnet.private_1.id, aws_subnet.private_2.id]

  tags = {
    Name = "k8s-3tier-db-subnet"
  }
}
# subnet group

# security group

resource "aws_security_group" "rds" {
  name   = "k8s-3tier-rds-sg"
  vpc_id = data.aws_vpc.main.id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    # security group of eks nodes can be added here instead of
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "k8s-3tier-rds-sg"
  }
}

# need password generation

resource "random_password" "db_password" {
  length           = 10
  special          = false
  override_special = "abcdgktyhtfAZVNNHDD1223434"
}

# secret manager to store the password

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "k8s-3tier-db-password"
  recovery_window_in_days = 7

  tags = {
    Name = "k8s-3tier-db-password"
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = "postgresql://${aws_db_instance.postgres.username}:${random_password.db_password.result}@${aws_db_instance.postgres.address}:${aws_db_instance.postgres.port}/${aws_db_instance.postgres.db_name}"
}

# rds instance for postgres

resource "aws_db_instance" "postgres" {
  identifier             = "k8s-3tier-postgres"
  allocated_storage      = 30
  engine                 = "postgres"
  engine_version         = "15.10"
  instance_class         = "db.t3.medium"
  db_name                = "devopsdojo"
  username               = "postgres"
  password               = random_password.db_password.result
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  skip_final_snapshot    = true

  tags = {
    Name = "k8s-3tier-postgres"
  }
}

