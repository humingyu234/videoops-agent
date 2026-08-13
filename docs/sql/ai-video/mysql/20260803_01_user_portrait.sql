-- 用户端人物形象库：仅当前 app 用户私有资源，不包含公共形象和运营端入口。

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
        category <> 'portrait_image' OR (file_format IN ('jpeg', 'png') AND file_size <= 10485760)
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
    record_revision BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁修订',
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (portrait_id),
    UNIQUE KEY uk_av_portrait_asset (asset_id),
    KEY idx_av_portrait_owner (tenant_id, workspace_id, owner_id, del_flag, create_time),
    CONSTRAINT fk_av_portrait_asset FOREIGN KEY (asset_id) REFERENCES av_asset (asset_id),
    CONSTRAINT ck_av_portrait_gender CHECK (gender IN ('female', 'male', 'unspecified')),
    CONSTRAINT ck_av_portrait_revision CHECK (record_revision > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户人物形象表';

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000016, 'aivideo:portrait:query', '人物形象查看', 'portrait', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000017, 'aivideo:portrait:add', '人物形象创建', 'portrait', 'add', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000018, 'aivideo:portrait:edit', '人物形象编辑', 'portrait', 'edit', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000019, 'aivideo:portrait:remove', '人物形象删除', 'portrait', 'remove', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), status = VALUES(status), update_time = NOW();

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000216, 1000101, 1000016, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000217, 1000101, 1000017, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000218, 1000101, 1000018, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000219, 1000101, 1000019, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = VALUES(status), update_time = NOW();
