-- 用户端手工文案：个人归属主体与不可变版本。

CREATE TABLE IF NOT EXISTS av_user_script (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_type VARCHAR(16) NOT NULL DEFAULT 'personal',
    owner_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    draft_id BIGINT NULL,
    display_title VARCHAR(100) NOT NULL,
    current_version_id BIGINT NULL,
    current_confirmed_version_id BIGINT NULL,
    create_idempotency_key VARCHAR(64) NOT NULL,
    create_request_hash CHAR(64) NOT NULL,
    script_revision BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_user_script_create_intent
        (tenant_id, owner_type, owner_id, create_idempotency_key, deleted),
    KEY idx_av_user_script_owner_updated
        (tenant_id, owner_type, owner_id, deleted, updated_at, id),
    CONSTRAINT ck_av_user_script_owner_type CHECK (owner_type = 'personal'),
    CONSTRAINT ck_av_user_script_revision CHECK (script_revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户私有文案主体';

CREATE TABLE IF NOT EXISTS av_script_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_type VARCHAR(16) NOT NULL DEFAULT 'personal',
    owner_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    script_id BIGINT NOT NULL,
    parent_version_id BIGINT NULL,
    version_no INT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    script_text LONGTEXT NOT NULL,
    effective_character_count INT NOT NULL,
    estimated_duration_seconds INT NOT NULL,
    effective_chars_per_minute INT NOT NULL,
    rule_config_versions_json VARCHAR(500) NOT NULL,
    manual_idempotency_key VARCHAR(64) NOT NULL,
    manual_request_hash CHAR(64) NOT NULL,
    result_display_title VARCHAR(100) NOT NULL,
    result_script_revision BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_script_version_no (tenant_id, script_id, version_no),
    UNIQUE KEY uk_av_script_version_manual_intent
        (tenant_id, script_id, manual_idempotency_key),
    KEY idx_av_script_version_history (tenant_id, script_id, version_no, id),
    CONSTRAINT fk_av_script_version_script FOREIGN KEY (script_id) REFERENCES av_user_script(id),
    CONSTRAINT fk_av_script_version_parent FOREIGN KEY (parent_version_id) REFERENCES av_script_version(id),
    CONSTRAINT ck_av_script_version_source CHECK (source_type IN ('manual_input', 'manual_edit')),
    CONSTRAINT ck_av_script_version_no CHECK (version_no > 0),
    CONSTRAINT ck_av_script_version_count CHECK (effective_character_count >= 0),
    CONSTRAINT ck_av_script_version_duration CHECK (estimated_duration_seconds >= 0),
    CONSTRAINT ck_av_script_version_rate CHECK (effective_chars_per_minute = 240)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变文案版本';

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000005, 'aivideo:script:query', '脚本查看', 'script', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000006, 'aivideo:script:edit', '脚本编辑', 'script', 'edit', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000008, 'aivideo:script:remove', '脚本删除', 'script', 'remove', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), status = VALUES(status), update_time = NOW();

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000205, 1000101, 1000005, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000206, 1000101, 1000006, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000208, 1000101, 1000008, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status), update_time = NOW();
