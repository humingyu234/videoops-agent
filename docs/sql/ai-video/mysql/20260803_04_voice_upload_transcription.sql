-- 用户声音上传与本地 Whisper 异步转写。

CREATE TABLE av_voice (
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
    detected_language VARCHAR(16) DEFAULT NULL COMMENT '识别语言',
    duration_millis BIGINT DEFAULT NULL COMMENT '音频时长（毫秒）',
    transcription_status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/transcribing/ready/failed',
    failure_code VARCHAR(64) DEFAULT NULL COMMENT '稳定失败标识',
    failure_message VARCHAR(500) DEFAULT NULL COMMENT '脱敏失败说明',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '自动尝试次数',
    next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次领取时间',
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
    UNIQUE KEY uk_av_voice_owner_idempotency (tenant_id, owner_id, idempotency_key),
    UNIQUE KEY uk_av_voice_tenant_asset (tenant_id, asset_id),
    KEY idx_av_voice_owner_list (tenant_id, workspace_id, owner_id, del_flag, create_time, voice_id),
    KEY idx_av_voice_transcription_claim (transcription_status, next_attempt_at, lease_expires_at),
    CONSTRAINT fk_av_voice_asset FOREIGN KEY (asset_id) REFERENCES av_asset (asset_id),
    CONSTRAINT ck_av_voice_type CHECK (voice_type IN ('origin','clone','public')),
    CONSTRAINT ck_av_voice_gender CHECK (gender IN ('female','male','unspecified')),
    CONSTRAINT ck_av_voice_transcription_status CHECK (transcription_status IN ('pending','transcribing','ready','failed')),
    CONSTRAINT ck_av_voice_duration CHECK (duration_millis IS NULL OR duration_millis >= 0),
    CONSTRAINT ck_av_voice_attempt CHECK (attempt_count >= 0),
    CONSTRAINT ck_av_voice_revision CHECK (record_revision > 0),
    CHECK (transcription_status IN ('pending','transcribing','ready','failed'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户声音资源表';

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000020, 'aivideo:voice:query', '声音查看', 'voice', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000021, 'aivideo:voice:upload', '声音上传', 'voice', 'upload', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000022, 'aivideo:voice:edit', '声音文本修改', 'voice', 'edit', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000023, 'aivideo:voice:transcribe', '声音转写重试', 'voice', 'transcribe', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), status = VALUES(status), update_time = NOW();

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000220, 1000101, 1000020, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000221, 1000101, 1000021, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000222, 1000101, 1000022, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000223, 1000101, 1000023, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status), update_time = NOW();
