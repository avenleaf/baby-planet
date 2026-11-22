CREATE TABLE parent_password_reset (
    account_id BIGINT NOT NULL PRIMARY KEY,
    token_hash BINARY(32) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_parent_password_reset FOREIGN KEY (account_id) REFERENCES parent_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
