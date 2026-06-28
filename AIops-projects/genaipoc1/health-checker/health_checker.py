import os
import sys
import json
import requests
import psycopg2
from datetime import datetime
from kubernetes import client, config
from qdrant_client import QdrantClient

# Configuration from environment variables
JAVA_APP_URL = os.getenv('JAVA_APP_URL', 'http://failure-app-service:8080')
OLLAMA_URL = os.getenv('OLLAMA_URL', 'http://ollama-service:11434')
QDRANT_URL = os.getenv('QDRANT_URL', 'http://qdrant-service:6333')
DB_HOST = os.getenv('DB_HOST', 'postgres-service')
DB_NAME = os.getenv('DB_NAME', 'failuredb')
DB_USER = os.getenv('DB_USER', 'postgres')
DB_PASSWORD = os.getenv('DB_PASSWORD', 'postgres')
NAMESPACE = os.getenv('NAMESPACE', 'default')
COLLECTION_NAME = 'knowledge_base'

def get_db_connection():
    """Create database connection"""
    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD
        )
        return conn
    except Exception as e:
        print(f"Database connection error: {e}")
        return None

def check_app_health():
    """Check the health of the Java application"""
    try:
        response = requests.get(f"{JAVA_APP_URL}/api/health", timeout=10)
        return {
            'status': 'healthy' if response.status_code == 200 else 'unhealthy',
            'status_code': response.status_code,
            'response': response.json() if response.headers.get('content-type') == 'application/json' else response.text
        }
    except requests.exceptions.Timeout:
        return {
            'status': 'unhealthy',
            'status_code': 0,
            'error': 'Request timeout - application not responding'
        }
    except requests.exceptions.ConnectionError:
        return {
            'status': 'unhealthy',
            'status_code': 0,
            'error': 'Connection error - cannot reach application'
        }
    except Exception as e:
        return {
            'status': 'unhealthy',
            'status_code': 0,
            'error': f'Unexpected error: {str(e)}'
        }

def get_app_logs():
    """Fetch logs from the Java application pod"""
    try:
        config.load_incluster_config()
        v1 = client.CoreV1Api()

        # Find the pod with label app=failure-app
        pods = v1.list_namespaced_pod(
            namespace=NAMESPACE,
            label_selector='app=failure-app'
        )

        if not pods.items:
            return "No pods found with label app=failure-app"

        pod_name = pods.items[0].metadata.name

        # Get logs (last 80 lines)
        logs = v1.read_namespaced_pod_log(
            name=pod_name,
            namespace=NAMESPACE,
            tail_lines=80
        )

        return logs
    except Exception as e:
        return f"Error fetching logs: {str(e)}"

def generate_embedding(text):
    """Generate embedding using Ollama nomic-embed-text model"""
    try:
        response = requests.post(
            f"{OLLAMA_URL}/api/embeddings",
            json={
                "model": "nomic-embed-text",
                "prompt": text
            },
            timeout=30
        )
        if response.status_code == 200:
            return response.json().get('embedding')
        else:
            print(f"Embedding generation failed: {response.status_code}")
            return None
    except Exception as e:
        print(f"Error generating embedding: {e}")
        return None

def search_knowledge_base(query, limit=3):
    """Search Qdrant knowledge base for relevant troubleshooting documents"""
    try:
        # Generate embedding for the query
        embedding = generate_embedding(query)
        if not embedding:
            print("Could not generate embedding for search")
            return []

        # Search Qdrant
        qdrant_client = QdrantClient(url=QDRANT_URL)

        # Check if collection exists
        collections = qdrant_client.get_collections().collections
        if not any(c.name == COLLECTION_NAME for c in collections):
            print(f"Collection '{COLLECTION_NAME}' does not exist")
            return []

        # Perform search
        search_results = qdrant_client.search(
            collection_name=COLLECTION_NAME,
            query_vector=embedding,
            limit=limit
        )

        # Extract relevant documents
        relevant_docs = []
        for result in search_results:
            if result.payload:
                relevant_docs.append({
                    'content': result.payload.get('content', ''),
                    'title': result.payload.get('title', 'Unknown'),
                    'score': result.score
                })

        return relevant_docs
    except Exception as e:
        print(f"Error searching knowledge base: {e}")
        return []

def analyze_with_ai(logs, error_info):
    """Use Ollama with RAG to analyze logs and provide diagnosis with fix suggestion"""
    try:
        # Search knowledge base for relevant troubleshooting info
        error_context = f"{error_info.get('error', '')} {logs[:500]}"
        relevant_docs = search_knowledge_base(error_context, limit=3)

        # Build prompt with RAG context
        prompt = f"""You are analyzing application error logs."""

        # Add knowledge base context if available
        if relevant_docs:
            prompt += f"\n\nRelevant troubleshooting knowledge from documentation:\n"
            for i, doc in enumerate(relevant_docs, 1):
                prompt += f"\n[Knowledge {i}]: {doc['content'][:500]}\n"
            prompt += "\n---\n"

        prompt += f"""
Error Status: {error_info.get('status_code', 'N/A')}
Error Details: {json.dumps(error_info.get('error', 'Unknown'))}

Application Logs:
{logs[:3000]}

Based on the logs and the troubleshooting knowledge provided, give:
1. The exact issue (one sentence)
2. Specific fix suggestion (one sentence)

Format: ISSUE: [description] | FIX: [suggestion]"""

        response = requests.post(
            f"{OLLAMA_URL}/api/generate",
            json={
                "model": "gemma:2b",
                "prompt": prompt,
                "stream": False
            },
            timeout=120
        )

        if response.status_code == 200:
            result = response.json()
            return result.get('response', 'Unable to generate summary').strip()
        else:
            return f"AI analysis unavailable (status: {response.status_code})"
    except Exception as e:
        return f"AI analysis error: {str(e)}"

def save_to_database(status, error_summary, raw_logs):
    """Save health check results to database"""
    conn = get_db_connection()
    if not conn:
        print("Cannot save to database - connection failed")
        return False

    try:
        cursor = conn.cursor()

        # Insert health check record
        cursor.execute(
            """
            INSERT INTO health_checks (timestamp, status, error_summary, raw_logs)
            VALUES (%s, %s, %s, %s)
            """,
            (datetime.now(), status, error_summary, raw_logs)
        )

        # Update app status
        cursor.execute(
            """
            INSERT INTO app_status (id, current_status, last_check_time, failure_count)
            VALUES (1, %s, %s,
                    CASE WHEN %s = 'unhealthy' THEN 1 ELSE 0 END)
            ON CONFLICT (id) DO UPDATE SET
                current_status = EXCLUDED.current_status,
                last_check_time = EXCLUDED.last_check_time,
                failure_count = CASE
                    WHEN EXCLUDED.current_status = 'unhealthy'
                    THEN app_status.failure_count + 1
                    ELSE app_status.failure_count
                END
            """,
            (status, datetime.now(), status)
        )

        conn.commit()
        cursor.close()
        conn.close()
        return True
    except Exception as e:
        print(f"Database save error: {e}")
        if conn:
            conn.close()
        return False

def main():
    """Main health check workflow"""
    print(f"[{datetime.now()}] Starting health check...")

    # Check application health
    health_result = check_app_health()
    print(f"Health check result: {health_result['status']}")

    error_summary = None
    raw_logs = None

    if health_result['status'] == 'unhealthy':
        print("Application is unhealthy, fetching logs...")

        # Get application logs
        raw_logs = get_app_logs()
        print(f"Fetched {len(raw_logs)} characters of logs")

        # Analyze with AI
        print("Analyzing with AI...")
        error_summary = analyze_with_ai(raw_logs, health_result)
        print(f"AI Summary: {error_summary}")
    else:
        error_summary = "Application is healthy"
        raw_logs = "No errors"

    # Save to database
    print("Saving to database...")
    if save_to_database(health_result['status'], error_summary, raw_logs):
        print("Successfully saved to database")
    else:
        print("Failed to save to database")

    print(f"[{datetime.now()}] Health check completed\n")

if __name__ == "__main__":
    main()
