import json
import logging
import os
import sys
import time
from datetime import datetime, timezone

import psycopg2
from confluent_kafka import Consumer, KafkaError

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-5s %(message)s",
    stream=sys.stdout,
)
log = logging.getLogger("storage-consumer")

APPROVED_TOPIC = "approved-transactions-topic"
ANALYSIS_TOPIC = "analysis-results-topic"

INSERT_APPROVED = """
INSERT INTO transactions (
    transaction_id, amount, date, time, type,
    sender_uid, sender_name, sender_nationality, sender_account, sender_bank, sender_location,
    receiver_uid, receiver_name, receiver_nationality, receiver_account, receiver_bank, receiver_location,
    flagged, status
) VALUES (
    %(transaction_id)s, %(amount)s, %(date)s, %(time)s, %(type)s,
    %(sender_uid)s, %(sender_name)s, %(sender_nationality)s, %(sender_account)s, %(sender_bank)s, %(sender_location)s,
    %(receiver_uid)s, %(receiver_name)s, %(receiver_nationality)s, %(receiver_account)s, %(receiver_bank)s, %(receiver_location)s,
    %(flagged)s, 'APPROVED'
)
ON CONFLICT (transaction_id) DO NOTHING;
"""

INSERT_ANALYSED = """
INSERT INTO transactions (
    transaction_id, amount, date, time, type,
    sender_uid, sender_name, sender_nationality, sender_account, sender_bank, sender_location,
    receiver_uid, receiver_name, receiver_nationality, receiver_account, receiver_bank, receiver_location,
    flagged,
    sender_match_score, sender_match_name, receiver_match_score, receiver_match_name,
    verdict, confidence, reasoning, model, analysed_at,
    status
) VALUES (
    %(transaction_id)s, %(amount)s, %(date)s, %(time)s, %(type)s,
    %(sender_uid)s, %(sender_name)s, %(sender_nationality)s, %(sender_account)s, %(sender_bank)s, %(sender_location)s,
    %(receiver_uid)s, %(receiver_name)s, %(receiver_nationality)s, %(receiver_account)s, %(receiver_bank)s, %(receiver_location)s,
    %(flagged)s,
    %(sender_match_score)s, %(sender_match_name)s, %(receiver_match_score)s, %(receiver_match_name)s,
    %(verdict)s, %(confidence)s, %(reasoning)s, %(model)s, %(analysed_at)s,
    'ANALYSED'
)
ON CONFLICT (transaction_id) DO UPDATE SET
    sender_match_score = EXCLUDED.sender_match_score,
    sender_match_name  = EXCLUDED.sender_match_name,
    receiver_match_score = EXCLUDED.receiver_match_score,
    receiver_match_name  = EXCLUDED.receiver_match_name,
    verdict     = EXCLUDED.verdict,
    confidence  = EXCLUDED.confidence,
    reasoning   = EXCLUDED.reasoning,
    model       = EXCLUDED.model,
    analysed_at = EXCLUDED.analysed_at,
    status      = 'ANALYSED';
"""

def extract_transaction_params(tx):
    """Extract flat transaction fields from the camelCase Transaction JSON object."""
    sender = tx["sender"]
    receiver = tx["receiver"]
    return {
        "transaction_id": tx["transactionId"],
        "amount": tx["amount"],
        "date": tx["date"],
        "time": tx["time"],
        "type": tx["type"],
        "sender_uid": sender["UID"],
        "sender_name": sender["name"],
        "sender_nationality": sender["nationality"],
        "sender_account": sender["accountNumber"],
        "sender_bank": sender["bank"],
        "sender_location": sender["location"],
        "receiver_uid": receiver["UID"],
        "receiver_name": receiver["name"],
        "receiver_nationality": receiver["nationality"],
        "receiver_account": receiver["accountNumber"],
        "receiver_bank": receiver["bank"],
        "receiver_location": receiver["location"],
    }


def handle_approved(conn, payload):
    """Insert an approved (non-flagged) transaction from FilterResult JSON."""
    tx = payload["transaction"]
    params = extract_transaction_params(tx)
    params["flagged"] = False
    params["status"] = "APPROVED"

    # Place it into the database!
    with conn.cursor() as cur:
        cur.execute(INSERT_APPROVED, params)
    conn.commit()
    log.info("APPROVED %s", params["transaction_id"])


def handle_analysed(conn, payload):
    """Insert an analysed (flagged) transaction from AnalysisResult JSON."""
    filter_result = payload["filter_result"]
    tx = filter_result["transaction"]
    params = extract_transaction_params(tx)
    params["flagged"] = True

    sender_match   = filter_result.get("sender_match")
    receiver_match = filter_result.get("receiver_match")

    params["sender_match_score"] = sender_match["final_score"] if sender_match else None
    params["sender_match_name"] = sender_match["sanction_info"]["name"] if sender_match else None
    params["receiver_match_score"] = receiver_match["final_score"] if receiver_match else None
    params["receiver_match_name"]  = receiver_match["sanction_info"]["name"] if receiver_match else None

    params["verdict"] = payload["verdict"]
    params["confidence"] = payload["confidence"]
    params["reasoning"] = payload["reasoning"]
    params["model"] = payload["model"]
    params["analysed_at"] = payload["analysed_at"]
    params["status"] = "ANALYSED"

    # Place it into the database! 
    with conn.cursor() as cur:
        cur.execute(INSERT_ANALYSED, params)
    conn.commit()
    log.info("ANALYSED %s | verdict=%s confidence=%.2f", params["transaction_id"], params["verdict"], params["confidence"])


def connect_db():
    """Connect to the dashboard PostgreSQL database with retries."""
    database_url = os.environ["DATABASE_URL"]
    # Try to connect to DB. 
    for attempt in range(1, 31):
        try:
            conn = psycopg2.connect(database_url)
            conn.autocommit = False
            log.info("Connected to dashboard database")
            return conn
        except psycopg2.OperationalError:
            log.warning("DB connection attempt %d/30 failed, retrying in 2s...", attempt)
            time.sleep(2)
    log.error("Failed to connect to database after 30 attempts")
    sys.exit(1)


def create_consumer():
    """Create and configure the Kafka consumer."""
    bootstrap_servers = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "kafka-1:9092,kafka-2:9092,kafka-3:9092")
    group_id = os.environ.get("KAFKA_GROUP_ID", "storage-consumer-group")

    consumer = Consumer({
        "bootstrap.servers": bootstrap_servers,
        "group.id": group_id,
        "auto.offset.reset": "earliest",
        "enable.auto.commit": "true",
    })
    consumer.subscribe([APPROVED_TOPIC, ANALYSIS_TOPIC])
    log.info("Subscribed to topics: %s, %s", APPROVED_TOPIC, ANALYSIS_TOPIC)
    return consumer


def main():
    conn= connect_db()
    consumer= create_consumer()

    try:
        while True:
            msg = consumer.poll(timeout=1.0) # Poll baby poll...
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                log.error("Kafka error: %s", msg.error())
                continue

            try:
                payload = json.loads(msg.value().decode("utf-8"))
            except (json.JSONDecodeError, UnicodeDecodeError) as e:
                log.error("Failed to deserialize message: %s", e)
                continue

            topic = msg.topic()
            try:
                if topic == APPROVED_TOPIC:
                    handle_approved(conn, payload)
                elif topic == ANALYSIS_TOPIC:
                    handle_analysed(conn, payload)
            except Exception:
                log.exception("Failed to process message from %s", topic)
                try:
                    conn.rollback()
                except Exception:
                    pass
                try:
                    conn.close()
                except Exception:
                    pass
                conn = connect_db()
    finally:
        consumer.close()
        conn.close()


if __name__ == "__main__":
    main()
