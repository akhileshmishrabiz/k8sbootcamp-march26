# Notification Service

Email notification microservice with a RabbitMQ consumer that delivers via plain SMTP. Built with Python and Flask.

## Features

- RabbitMQ consumer listening to order events (`order.created`)
- SMTP email sending via Python stdlib `smtplib` (no cloud SDK)
- Order confirmation emails with HTML templates + plain-text fallback
- Async email processing in a background thread

## Technology Stack

- Python 3.11+, Flask, Pika (RabbitMQ), stdlib `smtplib`

## SMTP Target

The service is wired to the in-cluster **Mailpit** SMTP sink by default — every message is captured and shown in the Mailpit web UI rather than delivered to a real inbox. Perfect for dev and demos.

To swap in a real SMTP relay (Postmark, SES SMTP, Mailgun, your own Postfix, etc.) override these env vars — no code change needed:

| Env var | Default | Notes |
|---|---|---|
| `SMTP_HOST` | `mailpit` | DNS name or IP of SMTP server |
| `SMTP_PORT` | `1025` | `587` for STARTTLS, `465` for implicit TLS |
| `SMTP_USER` | _(unset)_ | Set when the relay requires auth |
| `SMTP_PASSWORD` | _(unset)_ | Paired with `SMTP_USER` |
| `SMTP_USE_TLS` | `false` | `true` to STARTTLS after connect |
| `SMTP_SENDER_EMAIL` | `noreply@ecommerce.local` | `From:` address |
| `SMTP_SENDER_NAME` | `E-Commerce Platform` | Display name |

See `.env.example` for the full env list.

## Viewing Captured Emails (Mailpit)

```bash
kubectl port-forward -n ecommerce svc/mailpit 8025:8025
open http://localhost:8025
```

The UI shows every email the service "sent": HTML preview, plain-text, raw source, headers, and a live SMTP traffic log.

## Running Locally

```bash
pip install -r requirements.txt

# Option A: run a local Mailpit alongside
brew install mailpit && mailpit &   # SMTP :1025, UI http://localhost:8025

# Option B: point at any other SMTP server via env vars
export SMTP_HOST=smtp.example.com SMTP_PORT=587 SMTP_USE_TLS=true \
       SMTP_USER=... SMTP_PASSWORD=...

python app.py
```

Service runs on port 8006 and automatically consumes RabbitMQ messages.
