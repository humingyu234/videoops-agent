-- VideoOps Agent T1 digital-human job schema.
-- Derived from: 20260803_02_digital_human_vertical_flow.sql + 20260803_03_digital_human_poll_lease.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_040_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_040_target_assert_sql = IF(
    @videoops_040_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_040_wrong_target_database'
);
PREPARE videoops_040_target_assert_stmt FROM @videoops_040_target_assert_sql;
EXECUTE videoops_040_target_assert_stmt;
DEALLOCATE PREPARE videoops_040_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_dh_generation_job (
    id BIGINT NOT NULL COMMENT '任务编号',
    tenant_id BIGINT NOT NULL COMMENT '工作区租户编号',
    owner_user_id BIGINT NOT NULL COMMENT '创作端用户编号',
    job_type VARCHAR(32) NOT NULL COMMENT 'voice_generate 或 video_generate',
    status VARCHAR(16) NOT NULL COMMENT 'queued/running/succeeded/failed',
    stage VARCHAR(40) NOT NULL COMMENT '任务阶段',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',
    parent_job_id BIGINT DEFAULT NULL COMMENT '视频任务引用的声音任务',
    idempotency_key VARCHAR(64) NOT NULL COMMENT '客户端幂等键',
    input_hash CHAR(64) NOT NULL COMMENT '不可逆输入摘要',
    script_text VARCHAR(1000) DEFAULT NULL COMMENT '已确认口播正文快照',
    input_media_key VARCHAR(500) NOT NULL COMMENT '私有输入媒体相对键',
    output_media_key VARCHAR(500) DEFAULT NULL COMMENT '私有输出媒体相对键',
    output_media_type VARCHAR(100) DEFAULT NULL COMMENT '输出媒体类型',
    output_media_size BIGINT DEFAULT NULL COMMENT '输出字节数',
    output_media_sha256 CHAR(64) DEFAULT NULL COMMENT '输出摘要',
    provider VARCHAR(32) NOT NULL COMMENT 'indextts2 或 comfyui',
    provider_job_id VARCHAR(128) DEFAULT NULL COMMENT '供应商任务编号',
    poll_token VARCHAR(64) DEFAULT NULL COMMENT '视频轮询租约令牌',
    poll_lease_until DATETIME DEFAULT NULL COMMENT '视频轮询租约到期时间',
    poll_error_count INT NOT NULL DEFAULT 0 COMMENT '连续轮询异常次数',
    voice_confirmed TINYINT NOT NULL DEFAULT 0 COMMENT '声音是否已由用户确认',
    error_code VARCHAR(64) DEFAULT NULL COMMENT '稳定失败码',
    error_message VARCHAR(255) DEFAULT NULL COMMENT '脱敏失败提示',
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_dh_job_idempotency (tenant_id, owner_user_id, job_type, idempotency_key),
    KEY idx_av_dh_job_owner_time (tenant_id, owner_user_id, create_time, id),
    KEY idx_av_dh_job_parent (tenant_id, owner_user_id, parent_job_id),
    CONSTRAINT ck_av_dh_job_type CHECK (job_type IN ('voice_generate', 'video_generate')),
    CONSTRAINT ck_av_dh_job_status CHECK (status IN ('queued', 'running', 'succeeded', 'failed')),
    CONSTRAINT ck_av_dh_job_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_av_dh_job_parent CHECK (
        (job_type = 'voice_generate' AND parent_job_id IS NULL)
        OR (job_type = 'video_generate' AND parent_job_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数字人声音与视频纵向链任务';

SET @videoops_040_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 1
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_dh_generation_job'))
    AND (SELECT COUNT(*) = 6
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_dh_generation_job', 'id'),
              ('av_dh_generation_job', 'provider_job_id'),
              ('av_dh_generation_job', 'poll_token'),
              ('av_dh_generation_job', 'poll_lease_until'),
              ('av_dh_generation_job', 'poll_error_count'),
              ('av_dh_generation_job', 'output_media_key')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 3
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_dh_generation_job', 'uk_av_dh_job_idempotency'),
              ('av_dh_generation_job', 'idx_av_dh_job_owner_time'),
              ('av_dh_generation_job', 'idx_av_dh_job_parent')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 4
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_dh_generation_job', 'ck_av_dh_job_type'),
              ('av_dh_generation_job', 'ck_av_dh_job_status'),
              ('av_dh_generation_job', 'ck_av_dh_job_progress'),
              ('av_dh_generation_job', 'ck_av_dh_job_parent')
          ))
);
SET @videoops_040_schema_assert_sql = IF(
    @videoops_040_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_040_schema_contract_failed'
);
PREPARE videoops_040_schema_assert_stmt FROM @videoops_040_schema_assert_sql;
EXECUTE videoops_040_schema_assert_stmt;
DEALLOCATE PREPARE videoops_040_schema_assert_stmt;
