# read the backend config to understand what it needs

## Backend 
# pull config data
FLASK_APP=run.py
FLASK_DEBUG=1
DB_HOST=db_hostname
DB_PORT=5432
DB_NAME=devops_learning
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:80


# pull secret data
# db_url = postgresql://<db_user>:<db_password>@<db_hostname_dns>:<db_port>/<db_name>
DATABASE_URL=postgresql://postgres:postgres@db:5432/devops_learning
SECRET_KEY="your-secret-key-here"
DB_USERNAME=postgres
DB_PASSWORD=postgres


## frontend
BACKEND_URL=http://backend:8000

## AWS CLI Command

To delete an AWS Secrets Manager secret without retention:

```bash
aws secretsmanager delete-secret \
    --secret-id k8s-3tier-db-password \
    --force-delete-without-recovery
```

The `--force-delete-without-recovery` flag immediately deletes the secret without the default 7-day recovery window.
