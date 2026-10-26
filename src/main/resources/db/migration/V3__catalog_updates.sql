ALTER TABLE catalog_sync_state
    ADD COLUMN observed_version BIGINT NULL,
    ADD COLUMN last_checked_at DATETIME(6) NULL;

CREATE TABLE catalog_processed_event (
    event_hash BINARY(32) NOT NULL PRIMARY KEY,
    event_id LONGTEXT NOT NULL,
    new_version BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE catalog_rejected_record (
    topic VARCHAR(249) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    partition_id INT NOT NULL,
    record_offset BIGINT NOT NULL,
    safe_code VARCHAR(100) NOT NULL,
    rejected_at DATETIME(6) NOT NULL,
    PRIMARY KEY (topic, partition_id, record_offset)
) ENGINE=InnoDB;
