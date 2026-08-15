-- VideoOps Agent T1 asset and portrait schema.
-- Derived from: 20260803_01_user_portrait.sql + 20260804_01_portrait_library_remediation.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_030_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_030_target_assert_sql = IF(
    @videoops_030_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_030_wrong_target_database'
);
PREPARE videoops_030_target_assert_stmt FROM @videoops_030_target_assert_sql;
EXECUTE videoops_030_target_assert_stmt;
DEALLOCATE PREPARE videoops_030_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_asset (
    asset_id BIGINT NOT NULL COMMENT '素材 ID',
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    workspace_id VARCHAR(128) NOT NULL COMMENT '工作区稳定键',
    owner_id BIGINT NOT NULL COMMENT 'app 用户 ID',
    category VARCHAR(32) NOT NULL COMMENT '素材分类',
    object_key VARCHAR(512) NOT NULL COMMENT '私有对象 Key',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    content_type VARCHAR(64) NOT NULL COMMENT '服务端确认 MIME',
    file_format VARCHAR(16) NOT NULL COMMENT '服务端确认格式',
    width INT NOT NULL COMMENT '宽度',
    height INT NOT NULL COMMENT '高度',
    file_size BIGINT NOT NULL COMMENT '字节数',
    status VARCHAR(16) NOT NULL COMMENT 'ready/failed',
    failure_reason VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_av_asset_object_key (object_key),
    KEY idx_av_asset_owner (tenant_id, workspace_id, owner_id, del_flag, create_time),
    CONSTRAINT ck_av_asset_portrait_type CHECK (
        category <> 'portrait_image' OR (file_format IN ('jpeg', 'png', 'webp', 'gif') AND file_size <= 10485760)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 视频私有素材表';

CREATE TABLE IF NOT EXISTS av_portrait (
    portrait_id BIGINT NOT NULL COMMENT '人物形象 ID',
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    workspace_id VARCHAR(128) NOT NULL COMMENT '工作区稳定键',
    owner_id BIGINT NOT NULL COMMENT 'app 用户 ID',
    asset_id BIGINT NOT NULL COMMENT '唯一图片素材 ID',
    name VARCHAR(80) NOT NULL COMMENT '形象名称',
    gender VARCHAR(16) NOT NULL DEFAULT 'unspecified' COMMENT 'female/male/unspecified',
    scene_tags_json JSON NOT NULL COMMENT '场景标签',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '创建幂等键',
    request_digest CHAR(64) DEFAULT NULL COMMENT '规范化创建请求摘要',
    record_revision BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁修订',
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (portrait_id),
    UNIQUE KEY uk_av_portrait_asset (asset_id),
    UNIQUE KEY uk_av_portrait_idempotency (workspace_id, owner_id, idempotency_key),
    KEY idx_av_portrait_owner (tenant_id, workspace_id, owner_id, del_flag, create_time),
    CONSTRAINT fk_av_portrait_asset FOREIGN KEY (asset_id) REFERENCES av_asset (asset_id),
    CONSTRAINT ck_av_portrait_gender CHECK (gender IN ('female', 'male', 'unspecified')),
    CONSTRAINT ck_av_portrait_revision CHECK (record_revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户人物形象表';

SET @videoops_030_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 2
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_asset', 'av_portrait'))
    AND (SELECT COUNT(*) = 8
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_asset', 'asset_id'),
              ('av_asset', 'workspace_id'),
              ('av_asset', 'object_key'),
              ('av_asset', 'file_format'),
              ('av_portrait', 'portrait_id'),
              ('av_portrait', 'asset_id'),
              ('av_portrait', 'idempotency_key'),
              ('av_portrait', 'request_digest')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 5
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_asset', 'uk_av_asset_object_key'),
              ('av_asset', 'idx_av_asset_owner'),
              ('av_portrait', 'uk_av_portrait_asset'),
              ('av_portrait', 'uk_av_portrait_idempotency'),
              ('av_portrait', 'idx_av_portrait_owner')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 3
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_asset', 'ck_av_asset_portrait_type'),
              ('av_portrait', 'ck_av_portrait_gender'),
              ('av_portrait', 'ck_av_portrait_revision')
          ))
);
SET @videoops_030_schema_assert_sql = IF(
    @videoops_030_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_030_schema_contract_failed'
);
PREPARE videoops_030_schema_assert_stmt FROM @videoops_030_schema_assert_sql;
EXECUTE videoops_030_schema_assert_stmt;
DEALLOCATE PREPARE videoops_030_schema_assert_stmt;
