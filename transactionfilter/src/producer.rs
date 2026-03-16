use std::time::Duration;

use log::{error, info};
use rdkafka::config::ClientConfig;
use rdkafka::producer::{FutureProducer, FutureRecord};

use crate::models::FilterResult;

const FLAGGED_TOPIC: &str = "flagged-transactions-topic";
const APPROVED_TOPIC: &str = "approved-transactions-topic";

pub fn create_producer() -> FutureProducer {
    let bootstrap_servers = std::env::var("KAFKA_BOOTSTRAP_SERVERS")
        .unwrap_or_else(|_| "kafka-1:9092,kafka-2:9092,kafka-3:9092".to_string());

    let producer: FutureProducer = ClientConfig::new()
        .set("bootstrap.servers", &bootstrap_servers)
        .set("acks", "all")
        .create()
        .expect("Failed to create Kafka producer");

    info!("Kafka producer created");
    producer
}

pub async fn publish_flagged(producer: &FutureProducer, result: &FilterResult) {
    publish_to_topic(producer, result, FLAGGED_TOPIC, "Flagged").await;
}

pub async fn publish_approved(producer: &FutureProducer, result: &FilterResult) {
    publish_to_topic(producer, result, APPROVED_TOPIC, "Approved").await;
}

async fn publish_to_topic(
    producer: &FutureProducer,
    result: &FilterResult,
    topic: &str,
    label: &str,
) {
    let payload = match serde_json::to_string(result) {
        Ok(json) => json,
        Err(e) => {
            error!("Failed to serialize FilterResult: {}", e);
            return;
        }
    };

    let key = result.transaction.transaction_id.to_string();
    let record = FutureRecord::to(topic)
        .key(&key)
        .payload(&payload);

    match producer.send(record, Duration::from_secs(5)).await {
        Ok((partition, offset)) => {
            info!(
                "{} transaction {} published to partition {} offset {}",
                label, result.transaction.transaction_id, partition, offset
            );
        }
        Err((e, _)) => {
            error!(
                "Failed to publish {} transaction {}: {}",
                label.to_lowercase(), result.transaction.transaction_id, e
            );
        }
    }
}
