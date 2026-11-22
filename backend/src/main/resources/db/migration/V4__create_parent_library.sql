CREATE TABLE parent_library (
    account_id BIGINT NOT NULL PRIMARY KEY,
    revision INT NOT NULL DEFAULT 0,
    library JSON NOT NULL,
    CONSTRAINT fk_parent_library_account FOREIGN KEY (account_id) REFERENCES parent_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
