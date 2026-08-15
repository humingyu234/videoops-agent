-- VideoOps Agent T1 quota schema.
-- Derived from: 20260803_02_personal_quota_account.sql + 20260803_03_quota_used_balance.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_050_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_050_target_assert_sql = IF(
    @videoops_050_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_050_wrong_target_database'
);
PREPARE videoops_050_target_assert_stmt FROM @videoops_050_target_assert_sql;
EXECUTE videoops_050_target_assert_stmt;
DEALLOCATE PREPARE videoops_050_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_quota_account (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    available_balance BIGINT NOT NULL DEFAULT 0,
    locked_balance BIGINT NOT NULL DEFAULT 0,
    used_balance BIGINT NOT NULL DEFAULT 0,
    account_revision BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_quota_subject_unit (tenant_id, subject_type, subject_id, unit_code),
    CONSTRAINT ck_av_quota_available_nonnegative CHECK (available_balance >= 0),
    CONSTRAINT ck_av_quota_locked_nonnegative CHECK (locked_balance >= 0),
    CONSTRAINT ck_av_quota_used_nonnegative CHECK (used_balance >= 0),
    CONSTRAINT ck_av_quota_personal_subject CHECK (subject_type = 'app_user')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端个人积分账户';

SET @videoops_050_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 1
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_quota_account'))
    AND (SELECT COUNT(*) = 6
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_quota_account', 'id'),
              ('av_quota_account', 'subject_type'),
              ('av_quota_account', 'available_balance'),
              ('av_quota_account', 'locked_balance'),
              ('av_quota_account', 'used_balance'),
              ('av_quota_account', 'account_revision')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 1
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_quota_account', 'uk_av_quota_subject_unit')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 4
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_quota_account', 'ck_av_quota_available_nonnegative'),
              ('av_quota_account', 'ck_av_quota_locked_nonnegative'),
              ('av_quota_account', 'ck_av_quota_used_nonnegative'),
              ('av_quota_account', 'ck_av_quota_personal_subject')
          ))
);
SET @videoops_050_schema_assert_sql = IF(
    @videoops_050_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_050_schema_contract_failed'
);
PREPARE videoops_050_schema_assert_stmt FROM @videoops_050_schema_assert_sql;
EXECUTE videoops_050_schema_assert_stmt;
DEALLOCATE PREPARE videoops_050_schema_assert_stmt;
