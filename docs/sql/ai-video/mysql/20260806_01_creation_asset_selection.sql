-- 创作第三步资源选择：支持声音延迟解析和工作区级上传幂等。

SET @voice_table_exists = (
    SELECT COUNT(*) = 1
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_voice'
      AND TABLE_TYPE = 'BASE TABLE'
);
SET @voice_table_assert_sql = IF(
    @voice_table_exists,
    'SELECT 1',
    'SELECT * FROM __voice_asset_selection_migration_error_required_table_missing'
);
PREPARE voice_table_assert_stmt FROM @voice_table_assert_sql;
EXECUTE voice_table_assert_stmt;
DEALLOCATE PREPARE voice_table_assert_stmt;

ALTER TABLE av_voice
    MODIFY COLUMN next_attempt_at DATETIME NULL DEFAULT NULL COMMENT '下次领取时间';

-- 历史建表脚本同时存在命名和匿名 CHECK，按约束表达式发现后一次性全部删除。
SET @voice_status_check_drop_clauses = (
    SELECT GROUP_CONCAT(
        CONCAT('DROP CHECK `', REPLACE(tc.CONSTRAINT_NAME, '`', '``'), '`')
        ORDER BY tc.CONSTRAINT_NAME SEPARATOR ', '
    )
    FROM information_schema.TABLE_CONSTRAINTS tc
    INNER JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
       AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'av_voice'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND LOWER(cc.CHECK_CLAUSE) LIKE '%transcription_status%'
);
SET @voice_status_check_drop_sql = IF(
    @voice_status_check_drop_clauses IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE av_voice ', @voice_status_check_drop_clauses)
);
PREPARE voice_status_check_drop_stmt FROM @voice_status_check_drop_sql;
EXECUTE voice_status_check_drop_stmt;
DEALLOCATE PREPARE voice_status_check_drop_stmt;

ALTER TABLE av_voice
    ADD CONSTRAINT ck_av_voice_transcription_status CHECK (
        transcription_status IN ('unparsed', 'pending', 'transcribing', 'ready', 'failed')
    );

SET @voice_workspace_idempotency_index_ok = (
    SELECT COUNT(*) = 1
    FROM (
        SELECT INDEX_NAME,
               MIN(NON_UNIQUE) AS non_unique,
               GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_in_order
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_voice'
          AND INDEX_NAME = 'uk_av_voice_workspace_idempotency'
        GROUP BY INDEX_NAME
    ) voice_index_contract
    WHERE non_unique = 0
      AND columns_in_order = 'tenant_id,workspace_id,owner_id,idempotency_key'
);
SET @voice_owner_idempotency_index_exists = (
    SELECT COUNT(*) > 0
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_voice'
      AND INDEX_NAME = 'uk_av_voice_owner_idempotency'
);
SET @voice_idempotency_index_drop_clauses = (
    SELECT GROUP_CONCAT(
        CONCAT('DROP INDEX `', REPLACE(INDEX_NAME, '`', '``'), '`')
        ORDER BY INDEX_NAME SEPARATOR ', '
    )
    FROM (
        SELECT DISTINCT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_voice'
          AND INDEX_NAME IN (
              'uk_av_voice_owner_idempotency',
              'uk_av_voice_workspace_idempotency'
          )
    ) voice_indexes
);
SET @voice_idempotency_index_sql = CASE
    WHEN @voice_workspace_idempotency_index_ok = 1
         AND @voice_owner_idempotency_index_exists = 0
        THEN 'SELECT 1'
    WHEN @voice_workspace_idempotency_index_ok = 1
        THEN 'ALTER TABLE av_voice DROP INDEX uk_av_voice_owner_idempotency'
    ELSE CONCAT(
        'ALTER TABLE av_voice ',
        IF(
            @voice_idempotency_index_drop_clauses IS NULL,
            '',
            CONCAT(@voice_idempotency_index_drop_clauses, ', ')
        ),
        'ADD UNIQUE KEY uk_av_voice_workspace_idempotency ',
        '(tenant_id, workspace_id, owner_id, idempotency_key)'
    )
END;
PREPARE voice_idempotency_index_stmt FROM @voice_idempotency_index_sql;
EXECUTE voice_idempotency_index_stmt;
DEALLOCATE PREPARE voice_idempotency_index_stmt;

SET @voice_asset_selection_contract_ok = (
    (
        SELECT COUNT(*) = 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_voice'
          AND COLUMN_NAME = 'next_attempt_at'
          AND DATA_TYPE = 'datetime'
          AND IS_NULLABLE = 'YES'
          AND COLUMN_DEFAULT IS NULL
    )
    AND (
        SELECT COUNT(*) = 1
        FROM information_schema.TABLE_CONSTRAINTS tc
        INNER JOIN information_schema.CHECK_CONSTRAINTS cc
            ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
           AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
        WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
          AND tc.TABLE_NAME = 'av_voice'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%transcription_status%'
    )
    AND (
        SELECT COUNT(*) = 1
        FROM information_schema.TABLE_CONSTRAINTS tc
        INNER JOIN information_schema.CHECK_CONSTRAINTS cc
            ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
           AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
        WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
          AND tc.TABLE_NAME = 'av_voice'
          AND tc.CONSTRAINT_NAME = 'ck_av_voice_transcription_status'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%unparsed%'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%pending%'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%transcribing%'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%ready%'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%failed%'
    )
    AND (
        SELECT COUNT(*) = 1
        FROM (
            SELECT INDEX_NAME,
                   MIN(NON_UNIQUE) AS non_unique,
                   GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_in_order
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'av_voice'
              AND INDEX_NAME = 'uk_av_voice_workspace_idempotency'
            GROUP BY INDEX_NAME
        ) voice_index_contract
        WHERE non_unique = 0
          AND columns_in_order = 'tenant_id,workspace_id,owner_id,idempotency_key'
    )
    AND (
        SELECT COUNT(*) = 0
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_voice'
          AND INDEX_NAME = 'uk_av_voice_owner_idempotency'
    )
);
SET @voice_asset_selection_contract_assert_sql = IF(
    @voice_asset_selection_contract_ok,
    'SELECT 1',
    'SELECT * FROM __voice_asset_selection_migration_error_contract_mismatch'
);
PREPARE voice_asset_selection_contract_assert_stmt FROM @voice_asset_selection_contract_assert_sql;
EXECUTE voice_asset_selection_contract_assert_stmt;
DEALLOCATE PREPARE voice_asset_selection_contract_assert_stmt;
