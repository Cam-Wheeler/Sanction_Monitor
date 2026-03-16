use futures_util::StreamExt;
use log::{error, info, warn};
use rdkafka::config::ClientConfig;
use rdkafka::consumer::{Consumer, StreamConsumer};
use rdkafka::producer::FutureProducer;
use rdkafka::Message;
use sqlx::postgres::PgPool;

use crate::filter;
use crate::models::Transaction;
use crate::producer;

const GROUP_ID: &str = "transaction-filter-group";
const TOPIC: &str = "transactions-topic";

pub fn create_consumer() -> StreamConsumer {
    let bootstrap_servers = std::env::var("KAFKA_BOOTSTRAP_SERVERS")
        .unwrap_or_else(|_| "kafka-1:9092,kafka-2:9092,kafka-3:9092".to_string());

    let consumer: StreamConsumer = ClientConfig::new()
        .set("bootstrap.servers", &bootstrap_servers)
        .set("group.id", GROUP_ID)
        .set("auto.offset.reset", "earliest")
        .set("enable.auto.commit", "true")
        .create()
        .expect("Failed to create Kafka consumer");

    consumer
        .subscribe(&[TOPIC])
        .expect("Failed to subscribe to topic");

    info!("Subscribed to topic: {}", TOPIC);
    consumer
}

pub async fn consume_loop(
    consumer: &StreamConsumer,
    pool: &PgPool,
    kafka_producer: &FutureProducer,
    threshold: f32,
) {
    let mut stream = consumer.stream();

    while let Some(result) = stream.next().await {
        match result {
            Ok(message) => {
                let payload = match message.payload() {
                    Some(bytes) => bytes,
                    None => {
                        warn!("Received message with empty payload");
                        continue;
                    }
                };

                match serde_json::from_slice::<Transaction>(payload) {
                    Ok(transaction) => {
                        let tx_id = transaction.transaction_id;
                        let result = filter::filter_transaction(pool, transaction, threshold).await;

                        if result.flagged {
                            info!(
                                "FLAGGED {} | sender: {} | receiver: {}",
                                tx_id,
                                result
                                    .sender_match
                                    .as_ref()
                                    .map(|m| format!("{:.3}", m.final_score))
                                    .unwrap_or_else(|| "clear".to_string()),
                                result
                                    .receiver_match
                                    .as_ref()
                                    .map(|m| format!("{:.3}", m.final_score))
                                    .unwrap_or_else(|| "clear".to_string()),
                            );
                            producer::publish_flagged(kafka_producer, &result).await;
                        } else {
                            info!("CLEAR  {}", tx_id);
                            producer::publish_approved(kafka_producer, &result).await;
                        }
                    }
                    Err(e) => {
                        let raw = String::from_utf8_lossy(payload);
                        error!("Failed to deserialize transaction: {} | Raw: {}", e, raw);
                    }
                }
            }
            Err(e) => {
                error!("Kafka error: {}", e);
            }
        }
    }
}
