-- 人物形象库前向整改：扩展安全图片格式，并补齐创建幂等契约。
SET @portrait_tables_exist = (
    SELECT COUNT(*) = 2
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('av_asset', 'av_portrait')
      AND TABLE_TYPE = 'BASE TABLE'
);
SET @portrait_tables_assert_sql = IF(
    @portrait_tables_exist,
    'SELECT 1',
    'SELECT * FROM __portrait_migration_error_required_table_missing'
);
PREPARE portrait_tables_assert_stmt FROM @portrait_tables_assert_sql;
EXECUTE portrait_tables_assert_stmt;
DEALLOCATE PREPARE portrait_tables_assert_stmt;

SET @portrait_idempotency_key_sql = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_portrait'
          AND COLUMN_NAME = 'idempotency_key'
    ) = 0,
    'ALTER TABLE av_portrait ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL COMMENT ''创建幂等键'' AFTER note',
    'SELECT 1'
);
PREPARE portrait_idempotency_key_stmt FROM @portrait_idempotency_key_sql;
EXECUTE portrait_idempotency_key_stmt;
DEALLOCATE PREPARE portrait_idempotency_key_stmt;

SET @portrait_request_digest_sql = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_portrait'
          AND COLUMN_NAME = 'request_digest'
    ) = 0,
    'ALTER TABLE av_portrait ADD COLUMN request_digest CHAR(64) DEFAULT NULL COMMENT ''规范化创建请求摘要'' AFTER idempotency_key',
    'SELECT 1'
);
PREPARE portrait_request_digest_stmt FROM @portrait_request_digest_sql;
EXECUTE portrait_request_digest_stmt;
DEALLOCATE PREPARE portrait_request_digest_stmt;

SET @portrait_idempotency_columns_ok = (
    SELECT COUNT(*) = 2
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_portrait'
      AND (
          (COLUMN_NAME = 'idempotency_key' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH = 64 AND IS_NULLABLE = 'YES')
          OR (COLUMN_NAME = 'request_digest' AND DATA_TYPE = 'char' AND CHARACTER_MAXIMUM_LENGTH = 64 AND IS_NULLABLE = 'YES')
      )
);
SET @portrait_idempotency_columns_assert_sql = IF(
    @portrait_idempotency_columns_ok,
    'SELECT 1',
    'SELECT * FROM __portrait_migration_error_idempotency_column_contract_mismatch'
);
PREPARE portrait_idempotency_columns_assert_stmt FROM @portrait_idempotency_columns_assert_sql;
EXECUTE portrait_idempotency_columns_assert_stmt;
DEALLOCATE PREPARE portrait_idempotency_columns_assert_stmt;

SET @portrait_idempotency_index_sql = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_portrait'
          AND INDEX_NAME = 'uk_av_portrait_idempotency'
    ) = 0,
    'ALTER TABLE av_portrait ADD UNIQUE KEY uk_av_portrait_idempotency (workspace_id, owner_id, idempotency_key)',
    'SELECT 1'
);
PREPARE portrait_idempotency_index_stmt FROM @portrait_idempotency_index_sql;
EXECUTE portrait_idempotency_index_stmt;
DEALLOCATE PREPARE portrait_idempotency_index_stmt;

SET @portrait_idempotency_index_ok = (
    SELECT COUNT(*) = 1
    FROM (
        SELECT INDEX_NAME,
               MIN(NON_UNIQUE) AS non_unique,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_in_order
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_portrait'
          AND INDEX_NAME = 'uk_av_portrait_idempotency'
        GROUP BY INDEX_NAME
    ) index_contract
    WHERE non_unique = 0
      AND columns_in_order = 'workspace_id,owner_id,idempotency_key'
);
SET @portrait_idempotency_index_assert_sql = IF(
    @portrait_idempotency_index_ok,
    'SELECT 1',
    'SELECT * FROM __portrait_migration_error_idempotency_index_contract_mismatch'
);
PREPARE portrait_idempotency_index_assert_stmt FROM @portrait_idempotency_index_assert_sql;
EXECUTE portrait_idempotency_index_assert_stmt;
DEALLOCATE PREPARE portrait_idempotency_index_assert_stmt;

SET @portrait_asset_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_asset'
      AND CONSTRAINT_NAME = 'ck_av_asset_portrait_type'
      AND CONSTRAINT_TYPE = 'CHECK'
);
SET @portrait_asset_check_drop_sql = IF(
    @portrait_asset_check_exists > 0,
    'ALTER TABLE av_asset DROP CHECK ck_av_asset_portrait_type',
    'SELECT 1'
);
PREPARE portrait_asset_check_drop_stmt FROM @portrait_asset_check_drop_sql;
EXECUTE portrait_asset_check_drop_stmt;
DEALLOCATE PREPARE portrait_asset_check_drop_stmt;

ALTER TABLE av_asset
    ADD CONSTRAINT ck_av_asset_portrait_type CHECK (
        category <> 'portrait_image'
        OR (file_format IN ('jpeg', 'png', 'webp', 'gif') AND file_size <= 10485760)
    );

SET @portrait_asset_check_ok = (
    SELECT COUNT(*) = 1
    FROM information_schema.TABLE_CONSTRAINTS tc
    INNER JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
       AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'av_asset'
      AND tc.CONSTRAINT_NAME = 'ck_av_asset_portrait_type'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%jpeg%'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%png%'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%webp%'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%gif%'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%10485760%'
);
SET @portrait_asset_check_assert_sql = IF(
    @portrait_asset_check_ok,
    'SELECT 1',
    'SELECT * FROM __portrait_migration_error_asset_check_contract_mismatch'
);
PREPARE portrait_asset_check_assert_stmt FROM @portrait_asset_check_assert_sql;
EXECUTE portrait_asset_check_assert_stmt;
DEALLOCATE PREPARE portrait_asset_check_assert_stmt;
