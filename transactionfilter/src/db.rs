use log::info;
use sqlx::postgres::PgPool;
use sqlx::FromRow;

#[derive(Debug, FromRow)]

// Define the type so we can actually make the match! 
pub struct SanctionMatch {
    pub name: String,
    pub nationality: Option<String>,
    pub gender: Option<String>,
    pub dob: Option<String>,
    pub position: Option<String>,
    pub sanctions: Option<String>,
    pub sanction_creator: Option<String>,
    pub reason: Option<String>,
    pub other_info: Option<String>,
    pub sim_score: f32,
}

pub async fn create_pool() -> PgPool {
    let database_url =
        std::env::var("DATABASE_URL").expect("DATABASE_URL must be set");

    let pool = PgPool::connect(&database_url)
        .await
        .expect("Failed to create database pool");

    info!("Connected to sanctions database");
    
    // Return the pool
    pool
}

pub async fn find_best_match(pool: &PgPool, name: &str) -> Option<SanctionMatch> {
    sqlx::query_as::<_, SanctionMatch>(
        "SELECT name, nationality, gender, dob, position, sanctions, \
         sanction_creator, reason, other_info, \
         similarity(name, $1) AS sim_score \
         FROM sanctioned_individuals \
         WHERE name % $1 \
         ORDER BY sim_score DESC \
         LIMIT 1",
    )
    .bind(name)
    .fetch_optional(pool)
    .await
    .unwrap_or_else(|e| {
        log::error!("Database query failed: {}", e);
        None
    })
}
