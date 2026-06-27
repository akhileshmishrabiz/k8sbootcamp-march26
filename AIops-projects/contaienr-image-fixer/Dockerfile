# Intentionally flawed Dockerfile for container security training demos.
# A scanner (e.g. Trivy) should report multiple findings against this image.

FROM python:3.12

USER root

RUN apt-get update && \
    apt-get install -y curl wget netcat-openbsd && \
    pip install --no-cache-dir flask requests

WORKDIR /app

ADD . /app/

EXPOSE 8080 22

CMD ["python", "-m", "http.server", "8080"]
