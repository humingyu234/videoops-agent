-- VideoOps Agent T1 user script schema.
-- Derived from: 20260805_01_user_script_manual_input.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_070_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_070_target_assert_sql = IF(
    @videoops_070_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_070_wrong_target_database'
);
PREPARE videoops_070_target_assert_stmt FROM @videoops_070_target_assert_sql;
EXECUTE videoops_070_target_assert_stmt;
DEALLOCATE PREPARE videoops_070_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_user_script (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_type VARCHAR(16) NOT NULL DEFAULT 'personal',
    owner_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    draft_id BIGINT NULL,
    display_title VARCHAR(100) NOT NULL,
    current_version_id BIGINT NULL,
    current_confirmed_version_id BIGINT NULL,
    create_idempotency_key VARCHAR(64) NOT NULL,
    create_request_hash CHAR(64) NOT NULL,
    script_revision BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_user_script_create_intent
        (tenant_id, owner_type, owner_id, create_idempotency_key, deleted),
    KEY idx_av_user_script_owner_updated
        (tenant_id, owner_type, owner_id, deleted, updated_at, id),
    CONSTRAINT ck_av_user_script_owner_type CHECK (owner_type = 'personal'),
    CONSTRAINT ck_av_user_script_revision CHECK (script_revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私有文案主体';

CREATE TABLE IF NOT EXISTS av_script_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_type VARCHAR(16) NOT NULL DEFAULT 'personal',
    owner_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    parent_version_id BIGINT NULL,
    version_no INT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    script_text LONGTEXT NOT NULL,
    effective_character_count INT NOT NULL,
    estimated_duration_seconds INT NOT NULL,
    effective_chars_per_minute INT NOT NULL,
    rule_config_versions_json VARCHAR(500) NOT NULL,
    manual_idempotency_key VARCHAR(64) NOT NULL,
    manual_request_hash CHAR(64) NOT NULL,
    result_display_title VARCHAR(100) NOT NULL,
    result_script_revision BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_script_version_no (tenant_id, script_id, version_no),
    UNIQUE KEY uk_av_script_version_manual_intent
        (tenant_id, script_id, manual_idempotency_key),
    KEY idx_av_script_version_history (tenant_id, script_id, version_no, id),
    CONSTRAINT fk_av_script_version_script FOREIGN KEY (script_id) REFERENCES av_user_script(id),
    CONSTRAINT fk_av_script_version_parent FOREIGN KEY (parent_version_id) REFERENCES av_script_version(id),
    CONSTRAINT ck_av_script_version_source CHECK (source_type IN ('manual_input', 'manual_edit')),
    CONSTRAINT ck_av_script_version_no CHECK (version_no > 0),
    CONSTRAINT ck_av_script_version_count CHECK (effective_character_count >= 0),
    CONSTRAINT ck_av_script_version_duration CHECK (estimated_duration_seconds >= 0),
    CONSTRAINT ck_av_script_version_rate CHECK (effective_chars_per_minute = 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变文案版本';

SET @videoops_070_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 2
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_user_script', 'av_script_version'))
    AND (SELECT COUNT(*) = 8
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_user_script', 'id'),
              ('av_user_script', 'owner_id'),
              ('av_user_script', 'current_version_id'),
              ('av_user_script', 'current_confirmed_version_id'),
              ('av_script_version', 'id'),
              ('av_script_version', 'script_id'),
              ('av_script_version', 'script_text'),
              ('av_script_version', 'manual_idempotency_key')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 5
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_user_script', 'uk_av_user_script_create_intent'),
              ('av_user_script', 'idx_av_user_script_owner_updated'),
              ('av_script_version', 'uk_av_script_version_no'),
              ('av_script_version', 'uk_av_script_version_manual_intent'),
              ('av_script_version', 'idx_av_script_version_history')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 7
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_user_script', 'ck_av_user_script_owner_type'),
              ('av_user_script', 'ck_av_user_script_revision'),
              ('av_script_version', 'ck_av_script_version_source'),
              ('av_script_version', 'ck_av_script_version_no'),
              ('av_script_version', 'ck_av_script_version_count'),
              ('av_script_version', 'ck_av_script_version_duration'),
              ('av_script_version', 'ck_av_script_version_rate')
          ))
);
SET @videoops_070_schema_assert_sql = IF(
    @videoops_070_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_070_schema_contract_failed'
);
PREPARE videoops_070_schema_assert_stmt FROM @videoops_070_schema_assert_sql;
EXECUTE videoops_070_schema_assert_stmt;
DEALLOCATE PREPARE videoops_070_schema_assert_stmt;
