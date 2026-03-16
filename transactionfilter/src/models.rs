use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TransactionParty {
    pub party_type: String,
    #[serde(rename = "UID")]
    pub uid: Uuid,
    pub name: String,
    pub nationality: String,
    pub account_number: Uuid,
    pub bank: String,
    pub location: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Transaction {
    pub transaction_id: Uuid,
    pub amount: f64,
    pub date: String,
    pub time: String,
    #[serde(rename = "type")]
    pub transaction_type: String,
    pub sender: TransactionParty,
    pub receiver: TransactionParty,
}

#[derive(Debug, Clone, Serialize)]
pub struct SanctionInfo {
    pub name: String,
    pub nationality: Option<String>,
    pub gender: Option<String>,
    pub dob: Option<String>,
    pub position: Option<String>,
    pub sanctions: Option<String>,
    pub sanction_creator: Option<String>,
    pub reason: Option<String>,
    pub other_info: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PartyMatch {
    pub party_name: String,
    pub base_similarity: f32,
    pub nationality_boosted: bool,
    pub final_score: f32,
    pub sanction_info: SanctionInfo,
}

#[derive(Debug, Clone, Serialize)]
pub struct FilterResult {
    pub transaction: Transaction,
    pub flagged: bool,
    pub sender_match: Option<PartyMatch>,
    pub receiver_match: Option<PartyMatch>,
}
