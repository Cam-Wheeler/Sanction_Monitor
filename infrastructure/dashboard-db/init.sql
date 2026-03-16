CREATE TABLE transactions (
    transaction_id        UUID PRIMARY KEY,

    -- Transaction core
    amount                DOUBLE PRECISION NOT NULL,
    date                  TEXT NOT NULL,
    time                  TEXT NOT NULL,
    type                  TEXT NOT NULL,

    -- Sender
    sender_uid            UUID NOT NULL,
    sender_name           TEXT NOT NULL,
    sender_nationality    TEXT NOT NULL,
    sender_account        UUID NOT NULL,
    sender_bank           TEXT NOT NULL,
    sender_location       TEXT NOT NULL,

    -- Receiver
    receiver_uid            UUID NOT NULL,
    receiver_name           TEXT NOT NULL,
    receiver_nationality    TEXT NOT NULL,
    receiver_account        UUID NOT NULL,
    receiver_bank           TEXT NOT NULL,
    receiver_location       TEXT NOT NULL,

    -- Filter result
    flagged               BOOLEAN NOT NULL,

    -- Match scores (NULL for approved transactions)
    sender_match_score    REAL,
    sender_match_name     TEXT,
    receiver_match_score  REAL,
    receiver_match_name   TEXT,

    -- Analysis (NULL for approved transactions)
    verdict               TEXT,
    confidence            DOUBLE PRECISION,
    reasoning             TEXT,
    model                 TEXT,
    analysed_at           TIMESTAMPTZ,

    -- Status tracking
    status                TEXT NOT NULL DEFAULT 'APPROVED',
    ingested_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_flagged ON transactions (flagged);
CREATE INDEX idx_transactions_date ON transactions (date);
CREATE INDEX idx_transactions_verdict ON transactions (verdict) WHERE verdict IS NOT NULL;
