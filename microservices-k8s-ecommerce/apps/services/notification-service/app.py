import os
import json
import logging
import smtplib
import threading
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.utils import formataddr, make_msgid
from flask import Flask, jsonify
from flask_cors import CORS
from prometheus_flask_exporter import PrometheusMetrics
from dotenv import load_dotenv
from pythonjsonlogger import jsonlogger
import pika
from email_templates import render_order_confirmation

load_dotenv()

log_level = os.getenv('LOG_LEVEL', 'INFO').upper()
log_handler = logging.StreamHandler()
log_handler.setFormatter(jsonlogger.JsonFormatter(
    '%(asctime)s %(levelname)s %(name)s %(message)s',
    datefmt='%Y-%m-%dT%H:%M:%SZ',
    rename_fields={'asctime': 'time', 'levelname': 'level'},
    static_fields={'service': 'notification-service'},
))
logging.basicConfig(level=log_level, handlers=[log_handler], force=True)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)
metrics = PrometheusMetrics(app)

SMTP_HOST = os.getenv('SMTP_HOST', 'mailpit')
SMTP_PORT = int(os.getenv('SMTP_PORT', '1025'))
SMTP_USER = os.getenv('SMTP_USER', '')
SMTP_PASSWORD = os.getenv('SMTP_PASSWORD', '')
SMTP_USE_TLS = os.getenv('SMTP_USE_TLS', 'false').lower() == 'true'
SENDER_EMAIL = os.getenv('SMTP_SENDER_EMAIL', 'noreply@ecommerce.local')
SENDER_NAME = os.getenv('SMTP_SENDER_NAME', 'E-Commerce Platform')

def send_email(to_email, subject, html_body, text_body=None):
    """Send email via SMTP (defaults to Mailpit in-cluster sink)"""
    try:
        if text_body is None:
            text_body = html_body

        msg = MIMEMultipart('alternative')
        msg['Subject'] = subject
        msg['From'] = formataddr((SENDER_NAME, SENDER_EMAIL))
        msg['To'] = to_email
        msg['Message-ID'] = make_msgid(domain='ecommerce.local')
        msg.attach(MIMEText(text_body, 'plain', 'utf-8'))
        msg.attach(MIMEText(html_body, 'html', 'utf-8'))

        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=10) as server:
            if SMTP_USE_TLS:
                server.starttls()
            if SMTP_USER:
                server.login(SMTP_USER, SMTP_PASSWORD)
            server.sendmail(SENDER_EMAIL, [to_email], msg.as_string())

        logger.info(f'Email sent to {to_email} via {SMTP_HOST}:{SMTP_PORT}, Message-ID: {msg["Message-ID"]}')
        return True
    except Exception as e:
        logger.error(f'Failed to send email to {to_email}: {str(e)}')
        return False

def process_order_event(event_data):
    """Process order created event and send confirmation email"""
    try:
        event_type = event_data.get('event_type')
        logger.info(f'Processing event: {event_type}')

        if event_type == 'order_created':
            user_email = event_data.get('user_email')
            order_id = event_data.get('order_id')
            total_amount = event_data.get('total_amount')
            items = event_data.get('items', [])

            # Render email template
            html_body = render_order_confirmation(order_id, total_amount, items)
            subject = f'Order Confirmation - {order_id}'

            # Send email
            send_email(user_email, subject, html_body)
            logger.info(f'Order confirmation sent to {user_email}')

    except Exception as e:
        logger.error(f'Failed to process order event: {str(e)}')

def rabbitmq_consumer():
    """RabbitMQ consumer that listens for order events"""
    try:
        connection_params = pika.ConnectionParameters(
            host=os.getenv('RABBITMQ_HOST', 'localhost'),
            port=int(os.getenv('RABBITMQ_PORT', 5672)),
            credentials=pika.PlainCredentials(
                os.getenv('RABBITMQ_USER', 'guest'),
                os.getenv('RABBITMQ_PASSWORD', 'guest')
            ),
            heartbeat=600,
            blocked_connection_timeout=300
        )

        connection = pika.BlockingConnection(connection_params)
        channel = connection.channel()

        # Declare exchange and queue
        channel.exchange_declare(exchange='order_events', exchange_type='topic', durable=True)
        channel.queue_declare(queue='notification_queue', durable=True)
        channel.queue_bind(exchange='order_events', queue='notification_queue', routing_key='order.created')

        def callback(ch, method, properties, body):
            try:
                event_data = json.loads(body)
                logger.info(f'Received message: {event_data.get("event_type")}')
                process_order_event(event_data)
                ch.basic_ack(delivery_tag=method.delivery_tag)
            except Exception as e:
                logger.error(f'Failed to process message: {str(e)}')
                ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

        channel.basic_consume(queue='notification_queue', on_message_callback=callback)

        logger.info('Waiting for messages. To exit press CTRL+C')
        channel.start_consuming()

    except Exception as e:
        logger.error(f'RabbitMQ consumer error: {str(e)}')

# Start RabbitMQ consumer in a separate thread
consumer_thread = threading.Thread(target=rabbitmq_consumer, daemon=True)
consumer_thread.start()

@app.route('/health', methods=['GET'])
def health_check():
    return jsonify({
        'status': 'healthy',
        'service': 'notification-service',
        'rabbitmq': 'connected'
    }), 200

if __name__ == '__main__':
    port = int(os.getenv('PORT', 8006))
    logger.info(f'Notification Service starting on port {port}')
    app.run(host='0.0.0.0', port=port, debug=False)
