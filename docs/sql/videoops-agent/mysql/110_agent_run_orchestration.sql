-- VideoOps Agent T4 constrained orchestration state upgrade.
-- Evolves only av_agent_run; no task facts, assets, or seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_110_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_110_target_assert_sql = IF(
    @videoops_110_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_110_wrong_target_database'
);
PREPARE videoops_110_target_assert_stmt FROM @videoops_110_target_assert_sql;
EXECUTE videoops_110_target_assert_stmt;
DEALLOCATE PREPARE videoops_110_target_assert_stmt;

SET @videoops_110_table_ok = (
    SELECT COUNT(*) = 1
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_agent_run'
      AND TABLE_TYPE = 'BASE TABLE'
);
SET @videoops_110_retry_column_ok = (
    (SELECT COUNT(*)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_agent_run'
       AND COLUMN_NAME = 'retry_count') = 0
    OR
    (SELECT COUNT(*) = 1
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_agent_run'
       AND COLUMN_NAME = 'retry_count'
       AND DATA_TYPE = 'bigint'
       AND LOWER(COLUMN_TYPE) NOT LIKE '%unsigned%'
       AND IS_NULLABLE = 'NO'
       AND COLUMN_DEFAULT = '0')
);
SET @videoops_110_checks_ok = (
    SELECT COUNT(*) = 3
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME IN (
          'ck_av_agent_run_status',
          'ck_av_agent_run_counters',
          'ck_av_agent_run_result'
      )
);
SET @videoops_110_precondition_ok =
    @videoops_110_table_ok AND @videoops_110_retry_column_ok AND @videoops_110_checks_ok;
SET @videoops_110_precondition_assert_sql = IF(
    @videoops_110_precondition_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_110_agent_run_precondition_failed'
);
PREPARE videoops_110_precondition_assert_stmt FROM @videoops_110_precondition_assert_sql;
EXECUTE videoops_110_precondition_assert_stmt;
DEALLOCATE PREPARE videoops_110_precondition_assert_stmt;

SET @videoops_110_add_retry_count_sql = IF(
    (SELECT COUNT(*)
     FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_agent_run'
       AND COLUMN_NAME = 'retry_count') = 0,
    'ALTER TABLE av_agent_run ADD COLUMN retry_count BIGINT NOT NULL DEFAULT 0 AFTER lease_generation',
    'SELECT 1'
);
PREPARE videoops_110_add_retry_count_stmt FROM @videoops_110_add_retry_count_sql;
EXECUTE videoops_110_add_retry_count_stmt;
DEALLOCATE PREPARE videoops_110_add_retry_count_stmt;

SET @videoops_110_constraints_current = (
    SELECT
        COUNT(*) = 3
        AND SUM(
            CONSTRAINT_NAME = 'ck_av_agent_run_status'
            AND LOWER(CHECK_CLAUSE) LIKE '%waiting_input%'
        ) = 1
        AND SUM(
            CONSTRAINT_NAME = 'ck_av_agent_run_counters'
            AND LOWER(CHECK_CLAUSE) LIKE '%retry_count%'
        ) = 1
        AND SUM(
            CONSTRAINT_NAME = 'ck_av_agent_run_result'
            AND LOWER(CHECK_CLAUSE) LIKE '%waiting_input%'
        ) = 1
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME IN (
          'ck_av_agent_run_status',
          'ck_av_agent_run_counters',
          'ck_av_agent_run_result'
      )
);
SET @videoops_110_upgrade_constraints_sql = IF(
    @videoops_110_constraints_current,
    'SELECT 1',
    'ALTER TABLE av_agent_run
        DROP CHECK ck_av_agent_run_status,
        DROP CHECK ck_av_agent_run_counters,
        DROP CHECK ck_av_agent_run_result,
        ADD CONSTRAINT ck_av_agent_run_status CHECK (
            run_status IN (
                ''queued'', ''running'', ''waiting_input'', ''waiting_external_task'',
                ''completed'', ''failed'', ''cancelled''
            )
        ),
        ADD CONSTRAINT ck_av_agent_run_counters CHECK (
            row_version >= 0 AND lease_generation >= 0 AND retry_count >= 0
        ),
        ADD CONSTRAINT ck_av_agent_run_result CHECK (
            (
                run_status = ''completed''
                AND candidate_asset_id IS NOT NULL
                AND candidate_asset_id > 0
                AND result_summary_json IS NOT NULL
                AND result_digest IS NOT NULL
                AND result_digest REGEXP ''^[0-9a-f]{64}$''
                AND error_code IS NULL
                AND error_summary IS NULL
            )
            OR
            (
                run_status = ''failed''
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NOT NULL
                AND error_summary IS NOT NULL
            )
            OR
            (
                run_status = ''cancelled''
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
            )
            OR
            (
                run_status = ''waiting_input''
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NOT NULL
                AND error_summary IS NOT NULL
            )
            OR
            (
                run_status IN (''queued'', ''running'', ''waiting_external_task'')
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NULL
                AND error_summary IS NULL
            )
        )'
);
PREPARE videoops_110_upgrade_constraints_stmt FROM @videoops_110_upgrade_constraints_sql;
EXECUTE videoops_110_upgrade_constraints_stmt;
DEALLOCATE PREPARE videoops_110_upgrade_constraints_stmt;

SET @videoops_110_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 1
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'av_agent_run'
           AND COLUMN_NAME = 'retry_count'
           AND DATA_TYPE = 'bigint'
           AND LOWER(COLUMN_TYPE) NOT LIKE '%unsigned%'
           AND IS_NULLABLE = 'NO'
           AND COLUMN_DEFAULT = '0')
    AND (SELECT COUNT(*) = 3
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME IN (
               'ck_av_agent_run_status',
               'ck_av_agent_run_counters',
               'ck_av_agent_run_result'
           ))
    AND (SELECT COUNT(*) = 1
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME = 'ck_av_agent_run_status'
           AND LOWER(CHECK_CLAUSE) LIKE '%waiting_input%')
    AND (SELECT COUNT(*) = 1
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME = 'ck_av_agent_run_counters'
           AND LOWER(CHECK_CLAUSE) LIKE '%retry_count%')
    AND (SELECT COUNT(*) = 1
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME = 'ck_av_agent_run_result'
           AND LOWER(CHECK_CLAUSE) LIKE '%waiting_input%')
);
SET @videoops_110_schema_assert_sql = IF(
    @videoops_110_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_110_schema_contract_failed'
);
PREPARE videoops_110_schema_assert_stmt FROM @videoops_110_schema_assert_sql;
EXECUTE videoops_110_schema_assert_stmt;
DEALLOCATE PREPARE videoops_110_schema_assert_stmt;
