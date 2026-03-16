CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE sanctioned_individuals (
    id               SERIAL PRIMARY KEY,
    name             TEXT NOT NULL,
    nationality      TEXT,
    gender           TEXT,
    dob              TEXT,
    position         TEXT,
    sanctions        TEXT,
    sanction_creator TEXT,
    reason           TEXT,
    other_info       TEXT
);

CREATE INDEX idx_sanctions_name_trgm ON sanctioned_individuals USING GIN (name gin_trgm_ops);
CREATE INDEX idx_sanctions_nationality ON sanctioned_individuals (nationality);
