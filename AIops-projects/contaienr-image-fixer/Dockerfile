FROM python:3.12.0-slim-buster@sha256:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create a non-root user
RUN useradd -m appuser

USER appuser

WORKDIR /app

COPY . /app/

# Add a health check
HEALTHCHECK CMD curl --fail http://localhost:8080/ || exit 1

CMD ["python", "-m", "http.server", "8080"]
