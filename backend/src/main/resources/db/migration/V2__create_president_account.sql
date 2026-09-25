CREATE TABLE president_account (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    credential_version BIGINT NOT NULL DEFAULT 1
);
