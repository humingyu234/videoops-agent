-- 个人积分账户已用积分字段。当前只存储并读取，不在本迁移中增加统计或扣减逻辑。

SET @quota_account_table_exists = (
    SELECT COUNT(*) = 1
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_quota_account'
      AND TABLE_TYPE = 'BASE TABLE'
);
SET @quota_account_table_assert_sql = IF(
    @quota_account_table_exists,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_account_table_missing'
);
PREPARE quota_account_table_assert_stmt FROM @quota_account_table_assert_sql;
EXECUTE quota_account_table_assert_stmt;
DEALLOCATE PREPARE quota_account_table_assert_stmt;

SET @quota_used_column_sql = IF(
    (
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_quota_account'
          AND COLUMN_NAME = 'used_balance'
    ) = 0,
    'ALTER TABLE av_quota_account ADD COLUMN used_balance BIGINT NOT NULL DEFAULT 0 AFTER locked_balance',
    'SELECT 1'
);
PREPARE quota_used_column_stmt FROM @quota_used_column_sql;
EXECUTE quota_used_column_stmt;
DEALLOCATE PREPARE quota_used_column_stmt;

-- 同名列存在时必须严格匹配 BIGINT（有符号）、NOT NULL、DEFAULT 0，禁止静默接受错误旧定义。
SET @quota_used_column_contract_ok = (
    SELECT COUNT(*) = 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_quota_account'
      AND COLUMN_NAME = 'used_balance'
      AND DATA_TYPE = 'bigint'
      AND LOWER(COLUMN_TYPE) NOT LIKE '%unsigned%'
      AND IS_NULLABLE = 'NO'
      AND CAST(COLUMN_DEFAULT AS CHAR) = '0'
);
SET @quota_used_column_assert_sql = IF(
    @quota_used_column_contract_ok,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_used_balance_column_contract_mismatch'
);
PREPARE quota_used_column_assert_stmt FROM @quota_used_column_assert_sql;
EXECUTE quota_used_column_assert_stmt;
DEALLOCATE PREPARE quota_used_column_assert_stmt;

SET @quota_used_values_valid = (
    SELECT COUNT(*) = 0
    FROM av_quota_account
    WHERE used_balance < 0
);
SET @quota_used_values_assert_sql = IF(
    @quota_used_values_valid,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_negative_used_balance_data'
);
PREPARE quota_used_values_assert_stmt FROM @quota_used_values_assert_sql;
EXECUTE quota_used_values_assert_stmt;
DEALLOCATE PREPARE quota_used_values_assert_stmt;

SET @quota_used_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'av_quota_account'
      AND CONSTRAINT_NAME = 'ck_av_quota_used_nonnegative'
      AND CONSTRAINT_TYPE = 'CHECK'
);
SET @quota_used_check_contract_ok = (
    @quota_used_check_exists = 0
    OR (
        SELECT COUNT(*) = 1
        FROM information_schema.TABLE_CONSTRAINTS tc
        INNER JOIN information_schema.CHECK_CONSTRAINTS cc
            ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
           AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
        WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
          AND tc.TABLE_NAME = 'av_quota_account'
          AND tc.CONSTRAINT_NAME = 'ck_av_quota_used_nonnegative'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND REPLACE(REPLACE(REPLACE(REPLACE(LOWER(cc.CHECK_CLAUSE), '`', ''), ' ', ''), '(', ''), ')', '')
              = 'used_balance>=0'
    )
);
SET @quota_used_check_assert_sql = IF(
    @quota_used_check_contract_ok,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_used_balance_check_contract_mismatch'
);
PREPARE quota_used_check_assert_stmt FROM @quota_used_check_assert_sql;
EXECUTE quota_used_check_assert_stmt;
DEALLOCATE PREPARE quota_used_check_assert_stmt;

SET @quota_used_check_sql = IF(
    @quota_used_check_exists = 0,
    'ALTER TABLE av_quota_account ADD CONSTRAINT ck_av_quota_used_nonnegative CHECK (used_balance >= 0)',
    'SELECT 1'
);
PREPARE quota_used_check_stmt FROM @quota_used_check_sql;
EXECUTE quota_used_check_stmt;
DEALLOCATE PREPARE quota_used_check_stmt;

SET @quota_used_postcondition_ok = (
    SELECT COUNT(*) = 1
    FROM information_schema.TABLE_CONSTRAINTS tc
    INNER JOIN information_schema.CHECK_CONSTRAINTS cc
        ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
       AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
    WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
      AND tc.TABLE_NAME = 'av_quota_account'
      AND tc.CONSTRAINT_NAME = 'ck_av_quota_used_nonnegative'
      AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND REPLACE(REPLACE(REPLACE(REPLACE(LOWER(cc.CHECK_CLAUSE), '`', ''), ' ', ''), '(', ''), ')', '')
          = 'used_balance>=0'
);
SET @quota_used_postcondition_sql = IF(
    @quota_used_postcondition_ok,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_used_balance_postcondition_failed'
);
PREPARE quota_used_postcondition_stmt FROM @quota_used_postcondition_sql;
EXECUTE quota_used_postcondition_stmt;
DEALLOCATE PREPARE quota_used_postcondition_stmt;
