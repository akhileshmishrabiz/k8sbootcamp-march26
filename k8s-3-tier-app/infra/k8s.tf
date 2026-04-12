#create namespace
resource "kubernetes_namespace" "namespace" {
  metadata {
    annotations = {
      name = var.app_namepace
    }

    name = var.app_namepace
  }
}


# create secrets and configmaps for both apps

resource "kubernetes_config_map" "backend" {
  metadata {
    name = "backend-configmap"
    namespace = var.app_namepace
  }

  data = {
    FLASK_APP       = "run.py",
    FLASK_DEBUG     = "1",
    DB_HOST         = aws_db_instance.postgres.address,
    DB_PORT         = aws_db_instance.postgres.port,
    DB_NAME         = aws_db_instance.postgres.db_name,
    ALLOWED_ORIGINS = "http://frontend:80"
  }

depends_on = [ kubernetes_namespace.namespace ]
}

resource "kubernetes_secret" "backend" {
  metadata {
    name = "backend-secrets"
    namespace = var.app_namepace
  }

  data = {
   DATABASE_URL = "postgresql://${aws_db_instance.postgres.username}:${random_password.db_password.result}@${aws_db_instance.postgres.address}:${aws_db_instance.postgres.port}/${aws_db_instance.postgres.db_name}",
    SECRET_KEY   = random_password.backend_secret_key.result,
    DB_USERNAME  = aws_db_instance.postgres.username,
    DB_PASSWORD  = random_password.db_password.result
  }

  type = "opaque"
}

# menifest for frontend and  backend deployments and services via terraform


resource "kubernetes_config_map" "frontend" {
  metadata {
    name = "frontend-configmap"
    namespace = var.app_namepace
  }

  data = {
    # BACKEND_URL = "http://backend:8000"
    BaCKEND_URL = "http://${kubernetes_service.backend.spec[0].cluster_ip}:${kubernetes_service.backend.spec[0].port[0].port}"
  }

depends_on = [ kubernetes_namespace.namespace ]
}





# ingress   