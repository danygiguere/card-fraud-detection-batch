CREATE TABLE verified_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(50) NOT NULL,
    card_fingerprint VARCHAR(128) NOT NULL,
    encrypted_card_number VARCHAR(256) NOT NULL,
    cardholder_name VARCHAR(100) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    merchant VARCHAR(100) NOT NULL,
    transaction_timestamp TIMESTAMP NOT NULL,
    verified_at TIMESTAMP DEFAULT NOW()
);
