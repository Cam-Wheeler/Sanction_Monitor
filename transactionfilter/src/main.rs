mod consumer;
mod db;
mod filter;
mod models;
mod producer;

use log::info;

#[tokio::main]
async fn main() {
    env_logger::init();

    info!("Starting transaction filter service");

    let pool = db::create_pool().await;
    let consumer = consumer::create_consumer();
    let kafka_producer = producer::create_producer();

    let threshold: f32 = std::env::var("MATCH_THRESHOLD")
        .unwrap_or_else(|_| "0.5".to_string())
        .parse()
        .expect("MATCH_THRESHOLD must be a valid float");

    info!("Match threshold set to {}", threshold);

    consumer::consume_loop(&consumer, &pool, &kafka_producer, threshold).await;
}
