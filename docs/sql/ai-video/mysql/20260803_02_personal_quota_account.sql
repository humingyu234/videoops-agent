-- 创作端个人积分账户与查询权限。
-- 本表只允许 app_user 个人主体；组织或其他主体必须失败关闭。

CREATE TABLE IF NOT EXISTS av_quota_account (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    available_balance BIGINT NOT NULL DEFAULT 0,
    locked_balance BIGINT NOT NULL DEFAULT 0,
    account_revision BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_quota_subject_unit (tenant_id, subject_type, subject_id, unit_code),
    CONSTRAINT ck_av_quota_available_nonnegative CHECK (available_balance >= 0),
    CONSTRAINT ck_av_quota_locked_nonnegative CHECK (locked_balance >= 0),
    CONSTRAINT ck_av_quota_personal_subject CHECK (subject_type = 'app_user')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端个人积分账户';

-- CREATE TABLE IF NOT EXISTS 不会修复旧结构，先断言现存表仍符合个人账户唯一契约。
SET @quota_account_contract_ok = (
    (
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_quota_account'
          AND COLUMN_NAME IN (
              'id', 'tenant_id', 'subject_type', 'subject_id', 'unit_code',
              'available_balance', 'locked_balance', 'account_revision', 'create_time', 'update_time'
          )
    ) = 10
    AND (
        SELECT COUNT(*)
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_quota_account'
          AND COLUMN_NAME IN ('created_at', 'updated_at')
    ) = 0
    AND (
        SELECT COUNT(*)
        FROM information_schema.TABLE_CONSTRAINTS tc
        INNER JOIN information_schema.CHECK_CONSTRAINTS cc
            ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
           AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
        WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
          AND tc.TABLE_NAME = 'av_quota_account'
          AND tc.CONSTRAINT_NAME = 'ck_av_quota_personal_subject'
          AND tc.CONSTRAINT_TYPE = 'CHECK'
          AND LOWER(cc.CHECK_CLAUSE) LIKE '%subject_type%=%app_user%'
          AND LOWER(cc.CHECK_CLAUSE) NOT LIKE '%organization%'
    ) = 1
    AND (
        SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_quota_account'
          AND INDEX_NAME = 'uk_av_quota_subject_unit'
          AND NON_UNIQUE = 0
    ) = 'tenant_id,subject_type,subject_id,unit_code'
);

SET @quota_account_contract_assert_sql = IF(
    @quota_account_contract_ok,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_personal_account_contract_mismatch'
);
PREPARE quota_account_contract_assert_stmt FROM @quota_account_contract_assert_sql;
EXECUTE quota_account_contract_assert_stmt;
DEALLOCATE PREPARE quota_account_contract_assert_stmt;

-- 权限与角色按稳定编码解析；缺失、停用或保留主键被其他关系占用时失败关闭。
SET @quota_seed_actor_id = 1761100000000000001;
SET @quota_migration_now = NOW();
SET @quota_personal_role_id = (
    SELECT role_id
    FROM app_role
    WHERE role_code = 'personal_creator'
      AND status = 'active'
      AND del_flag = '0'
    LIMIT 1
);
SET @quota_query_permission_id = (
    SELECT permission_id
    FROM app_permission
    WHERE permission_code = 'aivideo:quota:query'
      AND status = 'active'
    LIMIT 1
);

SET @quota_permission_seed_valid = (
    @quota_personal_role_id IS NOT NULL
    AND @quota_query_permission_id IS NOT NULL
    AND NOT EXISTS (
        SELECT 1
        FROM app_role_permission
        WHERE id = 1000211
          AND (role_id <> @quota_personal_role_id OR permission_id <> @quota_query_permission_id)
    )
);
SET @quota_permission_seed_assert_sql = IF(
    @quota_permission_seed_valid,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_permission_seed_invalid_or_id_collision'
);
PREPARE quota_permission_seed_assert_stmt FROM @quota_permission_seed_assert_sql;
EXECUTE quota_permission_seed_assert_stmt;
DEALLOCATE PREPARE quota_permission_seed_assert_stmt;

-- 只有首次新增或从非 active 恢复时，才推进角色和当前有效用户的权限修订号。
SET @quota_permission_mapping_changed = IF(
    EXISTS (
        SELECT 1
        FROM app_role_permission
        WHERE role_id = @quota_personal_role_id
          AND permission_id = @quota_query_permission_id
          AND status = 'active'
    ),
    0,
    1
);

UPDATE app_role_permission
SET status = 'active',
    updated_by_type = 'sys_user',
    updated_by_id = @quota_seed_actor_id,
    update_time = @quota_migration_now
WHERE role_id = @quota_personal_role_id
  AND permission_id = @quota_query_permission_id
  AND status <> 'active';

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
)
SELECT
    1000211, @quota_personal_role_id, @quota_query_permission_id, 'active',
    'sys_user', @quota_seed_actor_id, 'sys_user', @quota_seed_actor_id,
    @quota_migration_now, @quota_migration_now
WHERE NOT EXISTS (
    SELECT 1
    FROM app_role_permission
    WHERE role_id = @quota_personal_role_id
      AND permission_id = @quota_query_permission_id
);

UPDATE app_role
SET role_revision = role_revision + 1,
    updated_by_type = 'sys_user',
    updated_by_id = @quota_seed_actor_id,
    update_time = @quota_migration_now
WHERE role_id = @quota_personal_role_id
  AND @quota_permission_mapping_changed = 1;

UPDATE app_user u
INNER JOIN app_user_role ur ON ur.user_id = u.user_id
SET u.permission_revision = u.permission_revision + 1,
    u.updated_by_type = 'sys_user',
    u.updated_by_id = @quota_seed_actor_id,
    u.update_time = @quota_migration_now
WHERE ur.role_id = @quota_personal_role_id
  AND ur.status = 'active'
  AND (ur.valid_from IS NULL OR ur.valid_from <= @quota_migration_now)
  AND (ur.valid_until IS NULL OR ur.valid_until > @quota_migration_now)
  AND u.status = 'active'
  AND u.del_flag = '0'
  AND @quota_permission_mapping_changed = 1;

SET @quota_permission_seed_postcondition_ok = (
    SELECT COUNT(*) = 1
    FROM app_role_permission
    WHERE role_id = @quota_personal_role_id
      AND permission_id = @quota_query_permission_id
      AND status = 'active'
);
SET @quota_permission_seed_postcondition_sql = IF(
    @quota_permission_seed_postcondition_ok,
    'SELECT 1',
    'SELECT * FROM __quota_migration_error_permission_mapping_postcondition_failed'
);
PREPARE quota_permission_seed_postcondition_stmt FROM @quota_permission_seed_postcondition_sql;
EXECUTE quota_permission_seed_postcondition_stmt;
DEALLOCATE PREPARE quota_permission_seed_postcondition_stmt;
