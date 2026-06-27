FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy health checker script
COPY health_checker.py .

# Run the health checker
CMD ["python", "health_checker.py"]
