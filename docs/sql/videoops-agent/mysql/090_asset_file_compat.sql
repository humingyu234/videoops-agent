-- VideoOps Agent T1 legacy asset mapping compatibility.
-- Derived from the file_id compatibility requirement for av_asset in 20260811_01_discovery_runninghub_single_execution.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_090_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_090_target_assert_sql = IF(
    @videoops_090_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_090_wrong_target_database'
);
PREPARE videoops_090_target_assert_stmt FROM @videoops_090_target_assert_sql;
EXECUTE videoops_090_target_assert_stmt;
DEALLOCATE PREPARE videoops_090_target_assert_stmt;

SET @videoops_090_asset_table_ok = (
    SELECT COUNT(*) = 1
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_asset'
      AND TABLE_TYPE = 'BASE TABLE'
);
SET @videoops_090_existing_file_id_ok = (
    (SELECT COUNT(*)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_asset'
       AND COLUMN_NAME = 'file_id') = 0
    OR
    (SELECT COUNT(*) = 1
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_asset'
       AND COLUMN_NAME = 'file_id'
       AND DATA_TYPE = 'bigint'
       AND LOWER(COLUMN_TYPE) NOT LIKE '%unsigned%'
       AND IS_NULLABLE = 'YES')
);
SET @videoops_090_precondition_ok =
    @videoops_090_asset_table_ok AND @videoops_090_existing_file_id_ok;
SET @videoops_090_precondition_assert_sql = IF(
    @videoops_090_precondition_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_090_asset_precondition_failed'
);
PREPARE videoops_090_precondition_assert_stmt FROM @videoops_090_precondition_assert_sql;
EXECUTE videoops_090_precondition_assert_stmt;
DEALLOCATE PREPARE videoops_090_precondition_assert_stmt;

SET @videoops_090_add_file_id_sql = IF(
    (SELECT COUNT(*)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_asset'
       AND COLUMN_NAME = 'file_id') = 0,
    'ALTER TABLE av_asset ADD COLUMN file_id BIGINT NULL AFTER asset_id',
    'SELECT 1'
);
PREPARE videoops_090_add_file_id_stmt FROM @videoops_090_add_file_id_sql;
EXECUTE videoops_090_add_file_id_stmt;
DEALLOCATE PREPARE videoops_090_add_file_id_stmt;

SET @videoops_090_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 1
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_asset'
          AND COLUMN_NAME = 'file_id'
          AND DATA_TYPE = 'bigint'
          AND LOWER(COLUMN_TYPE) NOT LIKE '%unsigned%'
          AND IS_NULLABLE = 'YES')
);
SET @videoops_090_schema_assert_sql = IF(
    @videoops_090_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_090_schema_contract_failed'
);
PREPARE videoops_090_schema_assert_stmt FROM @videoops_090_schema_assert_sql;
EXECUTE videoops_090_schema_assert_stmt;
DEALLOCATE PREPARE videoops_090_schema_assert_stmt;
