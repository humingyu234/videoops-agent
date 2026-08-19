-- VideoOps Agent manual Studio refresh/login recovery draft.
-- One owner-scoped current snapshot only; generation jobs and projects remain authoritative elsewhere.

SET NAMES utf8mb4;

SET @videoops_130_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_130_target_assert_sql = IF(
    @videoops_130_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_130_wrong_target_database'
);
PREPARE videoops_130_target_assert_stmt FROM @videoops_130_target_assert_sql;
EXECUTE videoops_130_target_assert_stmt;
DEALLOCATE PREPARE videoops_130_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_studio_workflow_draft (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    current_step TINYINT NOT NULL,
    schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_studio_workflow_draft_owner (tenant_id, owner_user_id),
    CONSTRAINT ck_av_studio_workflow_draft_revision CHECK (revision > 0),
    CONSTRAINT ck_av_studio_workflow_draft_step CHECK (current_step BETWEEN 0 AND 6),
    CONSTRAINT ck_av_studio_workflow_draft_schema CHECK (schema_version = 'studio-workflow-1'),
    CONSTRAINT ck_av_studio_workflow_draft_size CHECK (
        OCTET_LENGTH(CAST(snapshot_json AS CHAR)) <= 131072
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='当前用户人工工作台可恢复草稿';

SET @videoops_130_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 1
         FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_TYPE = 'BASE TABLE'
           AND TABLE_NAME = 'av_studio_workflow_draft')
    AND (SELECT COUNT(*) = 7
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'av_studio_workflow_draft'
           AND COLUMN_NAME IN (
               'id', 'tenant_id', 'owner_user_id', 'revision',
               'current_step', 'schema_version', 'snapshot_json'
           ))
    AND (SELECT COUNT(*) = 2
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'av_studio_workflow_draft'
           AND INDEX_NAME = 'uk_av_studio_workflow_draft_owner'
           AND NON_UNIQUE = 0)
    AND (SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'tenant_id,owner_user_id'
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'av_studio_workflow_draft'
           AND INDEX_NAME = 'uk_av_studio_workflow_draft_owner'
           AND NON_UNIQUE = 0)
    AND (SELECT COUNT(*) = 4
         FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'av_studio_workflow_draft'
           AND CONSTRAINT_TYPE = 'CHECK'
           AND CONSTRAINT_NAME IN (
               'ck_av_studio_workflow_draft_revision',
               'ck_av_studio_workflow_draft_step',
               'ck_av_studio_workflow_draft_schema',
               'ck_av_studio_workflow_draft_size'
           ))
);
SET @videoops_130_schema_assert_sql = IF(
    @videoops_130_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_130_schema_contract_failed'
);
PREPARE videoops_130_schema_assert_stmt FROM @videoops_130_schema_assert_sql;
EXECUTE videoops_130_schema_assert_stmt;
DEALLOCATE PREPARE videoops_130_schema_assert_stmt;
