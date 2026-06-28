FROM python:3.13-slim-bookworm

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl wget && \
    pip install --no-cache-dir flask requests && \
    apt-get purge -y --auto-remove curl wget && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . /app/

USER nobody

CMD ["python", "-m", "http.server", "8080"]
