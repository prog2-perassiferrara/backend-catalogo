CREATE TABLE professional_category (
    id BIGINT NOT NULL PRIMARY KEY,
    name LONGTEXT NOT NULL,
    description LONGTEXT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE professional (
    id BIGINT NOT NULL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    first_name LONGTEXT NOT NULL,
    last_name LONGTEXT NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_professional_category FOREIGN KEY (category_id) REFERENCES professional_category(id)
) ENGINE=InnoDB;

CREATE TABLE weekly_schedule (
    id BIGINT NOT NULL PRIMARY KEY,
    professional_id BIGINT NOT NULL,
    day_of_week VARCHAR(9) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INT NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_schedule_professional FOREIGN KEY (professional_id) REFERENCES professional(id)
) ENGINE=InnoDB;

CREATE TABLE catalog_sync_state (
    id BIGINT NOT NULL PRIMARY KEY,
    applied_version BIGINT NULL,
    applied_run_id VARCHAR(36) NULL,
    generated_at DATETIME(6) NULL,
    applied_at DATETIME(6) NULL,
    last_run_id VARCHAR(36) NULL,
    phase VARCHAR(32) NOT NULL,
    attempted_at DATETIME(6) NULL,
    safe_diagnostic VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

INSERT INTO catalog_sync_state (id, phase, safe_diagnostic)
VALUES (1, 'NOT_STARTED', 'NOT_STARTED');
