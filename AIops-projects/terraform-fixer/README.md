# Terraform Checkov Auto-Fixer

Intentionally **non-compliant** Terraform (bare S3 bucket) → **Checkov** scan → **AI** fixes → **PR**.

Terraform is kept minimal on purpose. Checkov finds the gaps; the workflow adds best practices.

## What's here

```
terraform-fixer/
├── main.tf              # bare S3 bucket (no encryption, versioning, etc.)
├── variables.tf
├── versions.tf
├── fix_terraform.py     # OpenAI + Checkov JSON → patched .tf files
└── requirements.txt
```

## GitHub Actions

Workflow: [`.github/workflows/terraform-fixer.yaml`](../../.github/workflows/terraform-fixer.yaml)

1. Checkov scans the Terraform
2. OpenAI fixes failing rules via `fix_terraform.py`
3. Pushes a branch and opens a PR

### Setup

1. Add repo secret: **`OPENAI_API_KEY`**
2. **Actions → Terraform Checkov Auto-Fixer → Run workflow**

## Run locally (optional)

```bash
cd AIops-projects/terraform-fixer

python3 -m venv .venv && source .venv/bin/activate
pip install checkov -r requirements.txt

checkov -d . --framework terraform -o json --output-file-path checkov-results.json || true

export OPENAI_API_KEY=sk-...
python3 fix_terraform.py

git diff *.tf
```

## Starter Terraform

`main.tf` is deliberately minimal so Checkov fails on rules like:

- `CKV_AWS_18` — versioning
- `CKV_AWS_19` — encryption
- `CKV_AWS_20` — public access block
- `CKV_AWS_21` — logging

The AI workflow adds those fixes in the PR — the starter files stay simple until you merge.
