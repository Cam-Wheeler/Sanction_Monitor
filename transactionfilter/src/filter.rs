use sqlx::postgres::PgPool;

use crate::db;
use crate::models::{FilterResult, PartyMatch, SanctionInfo, Transaction};

const NATIONALITY_BOOST: f32 = 0.1;

async fn check_party(
    pool: &PgPool,
    party_name: &str,
    party_nationality: &str,
    threshold: f32,
) -> Option<PartyMatch> {
    let sanction_match = db::find_best_match(pool, party_name).await?;

    let nationality_boosted = sanction_match
        .nationality
        .as_deref()
        .is_some_and(|n| n.eq_ignore_ascii_case(party_nationality));

    let boost = if nationality_boosted { NATIONALITY_BOOST } else { 0.0 };
    let final_score = (sanction_match.sim_score + boost).min(1.0);

    if final_score >= threshold {
        Some(PartyMatch {
            party_name: party_name.to_string(),
            base_similarity: sanction_match.sim_score,
            nationality_boosted,
            final_score,
            sanction_info: SanctionInfo {
                name: sanction_match.name,
                nationality: sanction_match.nationality,
                gender: sanction_match.gender,
                dob: sanction_match.dob,
                position: sanction_match.position,
                sanctions: sanction_match.sanctions,
                sanction_creator: sanction_match.sanction_creator,
                reason: sanction_match.reason,
                other_info: sanction_match.other_info,
            },
        })
    } else {
        None
    }
}

pub async fn filter_transaction(
    pool: &PgPool,
    transaction: Transaction,
    threshold: f32,
) -> FilterResult {
    let (sender_match, receiver_match) = tokio::join!(
        check_party(pool, &transaction.sender.name, &transaction.sender.nationality, threshold),
        check_party(pool, &transaction.receiver.name, &transaction.receiver.nationality, threshold),
    );

    let flagged = sender_match.is_some() || receiver_match.is_some();

    FilterResult {
        transaction,
        flagged,
        sender_match,
        receiver_match,
    }
}
