locals {

  backend_config = {

    FLASK_APP       = "run.py",
    FLASK_DEBUG     = "1",
    DB_HOST         = aws_db_instance.postgres.address,
    DB_PORT         = aws_db_instance.postgres.port,
    DB_NAME         = aws_db_instance.postgres.db_name,
    ALLOWED_ORIGINS = "http://localhost:80"
  }

  backend_secrets = {
    DATABASE_URL = "postgresql://${aws_db_instance.postgres.username}:${random_password.db_password.result}@${aws_db_instance.postgres.address}:${aws_db_instance.postgres.port}/${aws_db_instance.postgres.db_name}",
    SECRET_KEY   = random_password.backend_secret_key.result,
    DB_USERNAME  = aws_db_instance.postgres.username,
    DB_PASSWORD  = random_password.db_password.result

  }

  frontend_config = {
    BACKEND_URL = "http://localhost:5000"
  }
}


resource "random_password" "backend_secret_key" {
  length           = 16
  special          = false
  override_special = "#$%asfddgddwgeqge^@&*()_+"
}
