-- VideoOps Agent T1 voice schema.
-- Derived from: 20260803_04_voice_upload_transcription.sql + 20260803_05_voice_transcript_timeline.sql + 20260806_01_creation_asset_selection.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_060_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_060_target_assert_sql = IF(
    @videoops_060_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_060_wrong_target_database'
);
PREPARE videoops_060_target_assert_stmt FROM @videoops_060_target_assert_sql;
EXECUTE videoops_060_target_assert_stmt;
DEALLOCATE PREPARE videoops_060_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_voice (
    voice_id BIGINT NOT NULL COMMENT '声音 ID',
    tenant_id BIGINT NOT NULL COMMENT '租户 ID',
    workspace_id VARCHAR(128) NOT NULL COMMENT '工作区稳定键',
    owner_id BIGINT NOT NULL COMMENT 'app 用户 ID',
    asset_id BIGINT NOT NULL COMMENT '唯一音频素材 ID',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '客户端幂等键',
    upload_fingerprint CHAR(64) NOT NULL COMMENT '文件及元数据摘要',
    voice_type VARCHAR(16) NOT NULL DEFAULT 'origin' COMMENT 'origin/clone/public',
    name VARCHAR(80) NOT NULL COMMENT '声音名称',
    gender VARCHAR(16) NOT NULL DEFAULT 'unspecified' COMMENT 'female/male/unspecified',
    style VARCHAR(40) DEFAULT NULL COMMENT '声音风格',
    tags_json JSON NOT NULL COMMENT '标签 JSON 数组',
    note VARCHAR(500) DEFAULT NULL COMMENT '备注',
    transcript_text TEXT DEFAULT NULL COMMENT '转写或人工修正文本',
    transcript_timeline_json JSON DEFAULT NULL COMMENT 'Whisper 词元时间轴 JSON',
    detected_language VARCHAR(16) DEFAULT NULL COMMENT '识别语言',
    duration_millis BIGINT DEFAULT NULL COMMENT '音频时长（毫秒）',
    transcription_status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/transcribing/ready/failed',
    failure_code VARCHAR(64) DEFAULT NULL COMMENT '稳定失败标识',
    failure_message VARCHAR(500) DEFAULT NULL COMMENT '脱敏失败说明',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '自动尝试次数',
    next_attempt_at DATETIME NULL DEFAULT NULL COMMENT '下次领取时间',
    lease_owner VARCHAR(128) DEFAULT NULL COMMENT '处理租约持有者',
    lease_expires_at DATETIME DEFAULT NULL COMMENT '处理租约过期时间',
    record_revision BIGINT NOT NULL DEFAULT 1 COMMENT '乐观并发修订号',
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (voice_id),
    UNIQUE KEY uk_av_voice_workspace_idempotency (tenant_id, workspace_id, owner_id, idempotency_key),
    UNIQUE KEY uk_av_voice_tenant_asset (tenant_id, asset_id),
    KEY idx_av_voice_owner_list (tenant_id, workspace_id, owner_id, del_flag, create_time, voice_id),
    KEY idx_av_voice_transcription_claim (transcription_status, next_attempt_at, lease_expires_at),
    CONSTRAINT fk_av_voice_asset FOREIGN KEY (asset_id) REFERENCES av_asset (asset_id),
    CONSTRAINT ck_av_voice_type CHECK (voice_type IN ('origin','clone','public')),
    CONSTRAINT ck_av_voice_gender CHECK (gender IN ('female','male','unspecified')),
    CONSTRAINT ck_av_voice_transcription_status CHECK (transcription_status IN ('unparsed','pending','transcribing','ready','failed')),
    CONSTRAINT ck_av_voice_duration CHECK (duration_millis IS NULL OR duration_millis >= 0),
    CONSTRAINT ck_av_voice_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_av_voice_revision CHECK (record_revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户声音资源表';

SET @videoops_060_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 1
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_voice'))
    AND (SELECT COUNT(*) = 7
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_voice', 'voice_id'),
              ('av_voice', 'workspace_id'),
              ('av_voice', 'asset_id'),
              ('av_voice', 'transcript_timeline_json'),
              ('av_voice', 'transcription_status'),
              ('av_voice', 'next_attempt_at'),
              ('av_voice', 'lease_expires_at')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 4
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_voice', 'uk_av_voice_workspace_idempotency'),
              ('av_voice', 'uk_av_voice_tenant_asset'),
              ('av_voice', 'idx_av_voice_owner_list'),
              ('av_voice', 'idx_av_voice_transcription_claim')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 6
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_voice', 'ck_av_voice_type'),
              ('av_voice', 'ck_av_voice_gender'),
              ('av_voice', 'ck_av_voice_transcription_status'),
              ('av_voice', 'ck_av_voice_duration'),
              ('av_voice', 'ck_av_voice_attempt'),
              ('av_voice', 'ck_av_voice_revision')
          ))
);
SET @videoops_060_schema_assert_sql = IF(
    @videoops_060_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_060_schema_contract_failed'
);
PREPARE videoops_060_schema_assert_stmt FROM @videoops_060_schema_assert_sql;
EXECUTE videoops_060_schema_assert_stmt;
DEALLOCATE PREPARE videoops_060_schema_assert_stmt;
