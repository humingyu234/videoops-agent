-- P0-A 创作端身份安全基线。
-- app_permission 使用 1000001-1000015，app_role 使用 1000101-1000104：
-- 这些小号区间为固定种子保留区，与运行时雪花 ID 区间隔离。

CREATE TABLE IF NOT EXISTS app_user (
    user_id BIGINT NOT NULL COMMENT '创作端用户 ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    username_normalized VARCHAR(64) NOT NULL COMMENT '标准化用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码摘要',
    phone_normalized VARCHAR(32) DEFAULT NULL COMMENT '标准化手机号',
    email_normalized VARCHAR(128) DEFAULT NULL COMMENT '标准化邮箱',
    personal_tenant_id BIGINT NOT NULL COMMENT '个人租户 ID',
    display_name VARCHAR(64) NOT NULL COMMENT '显示名称',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码',
    credential_revision BIGINT NOT NULL DEFAULT 1 COMMENT '凭据修订号',
    identity_revision BIGINT NOT NULL DEFAULT 1 COMMENT '身份修订号',
    permission_revision BIGINT NOT NULL DEFAULT 1 COMMENT '权限修订号',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_app_user_username_normalized (username_normalized),
    UNIQUE KEY uk_app_user_phone_normalized (phone_normalized),
    UNIQUE KEY uk_app_user_email_normalized (email_normalized),
    UNIQUE KEY uk_app_user_personal_tenant_id (personal_tenant_id),
    CONSTRAINT ck_app_user_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端用户表';

CREATE TABLE IF NOT EXISTS app_auth_client (
    id BIGINT NOT NULL COMMENT '认证客户端 ID',
    client_id VARCHAR(64) NOT NULL COMMENT '客户端标识',
    client_key VARCHAR(64) NOT NULL COMMENT '客户端键',
    client_secret_hash VARCHAR(100) DEFAULT NULL COMMENT '客户端密钥摘要',
    grant_types VARCHAR(500) NOT NULL COMMENT '允许授权类型',
    access_paths VARCHAR(1000) NOT NULL COMMENT '允许访问路径',
    ip_whitelist VARCHAR(1000) DEFAULT NULL COMMENT 'IP 白名单',
    token_timeout BIGINT NOT NULL COMMENT '令牌固定超时秒数',
    active_timeout BIGINT NOT NULL COMMENT '令牌活跃超时秒数',
    client_revision BIGINT NOT NULL DEFAULT 1 COMMENT '客户端修订号',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_auth_client_client_id (client_id),
    UNIQUE KEY uk_app_auth_client_client_key (client_key),
    CONSTRAINT ck_app_auth_client_revision CHECK (client_revision > 0),
    CONSTRAINT ck_app_auth_client_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端认证客户端表';

CREATE TABLE IF NOT EXISTS app_social_identity (
    social_identity_id BIGINT NOT NULL COMMENT '第三方身份 ID',
    user_id BIGINT NOT NULL COMMENT '创作端用户 ID',
    provider VARCHAR(32) NOT NULL COMMENT '第三方提供方',
    provider_subject VARCHAR(128) NOT NULL COMMENT '第三方主体标识',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (social_identity_id),
    UNIQUE KEY uk_app_social_identity_provider_subject (provider, provider_subject),
    UNIQUE KEY uk_app_social_identity_user_provider (user_id, provider),
    CONSTRAINT fk_app_social_identity_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT ck_app_social_identity_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端第三方身份表';

CREATE TABLE IF NOT EXISTS app_permission (
    permission_id BIGINT NOT NULL COMMENT '权限 ID',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    resource_type VARCHAR(32) NOT NULL COMMENT '资源类型',
    action VARCHAR(32) NOT NULL COMMENT '操作类型',
    permission_revision BIGINT NOT NULL DEFAULT 1 COMMENT '权限修订号',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_app_permission_code (permission_code),
    CONSTRAINT ck_app_permission_revision CHECK (permission_revision > 0),
    CONSTRAINT ck_app_permission_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端权限表';

CREATE TABLE IF NOT EXISTS app_role (
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    scope_type VARCHAR(16) NOT NULL COMMENT '作用域类型',
    built_in TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置角色',
    role_revision BIGINT NOT NULL DEFAULT 1 COMMENT '角色修订号',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_app_role_role_code (role_code),
    CONSTRAINT ck_app_role_revision CHECK (role_revision > 0),
    CONSTRAINT ck_app_role_scope_type CHECK (scope_type IN ('personal', 'organization')),
    CONSTRAINT ck_app_role_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端角色表';

CREATE TABLE IF NOT EXISTS app_role_permission (
    id BIGINT NOT NULL COMMENT '角色权限关联 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    permission_id BIGINT NOT NULL COMMENT '权限 ID',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_role_permission_role_permission (role_id, permission_id),
    CONSTRAINT fk_app_role_permission_role FOREIGN KEY (role_id) REFERENCES app_role (role_id),
    CONSTRAINT fk_app_role_permission_permission FOREIGN KEY (permission_id) REFERENCES app_permission (permission_id),
    CONSTRAINT ck_app_role_permission_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端角色权限关联表';

CREATE TABLE IF NOT EXISTS app_user_role (
    id BIGINT NOT NULL COMMENT '用户角色关联 ID',
    user_id BIGINT NOT NULL COMMENT '创作端用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '状态',
    valid_from DATETIME DEFAULT NULL COMMENT '生效时间',
    valid_until DATETIME DEFAULT NULL COMMENT '失效时间',
    created_by_type VARCHAR(16) NOT NULL COMMENT '创建主体类型',
    created_by_id BIGINT NOT NULL COMMENT '创建主体 ID',
    updated_by_type VARCHAR(16) NOT NULL COMMENT '更新主体类型',
    updated_by_id BIGINT NOT NULL COMMENT '更新主体 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_role_user_role (user_id, role_id),
    CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
    CONSTRAINT fk_app_user_role_role FOREIGN KEY (role_id) REFERENCES app_role (role_id),
    CONSTRAINT ck_app_user_role_actor_types CHECK (
        created_by_type IN ('app_user', 'sys_user')
        AND updated_by_type IN ('app_user', 'sys_user')
    ),
    CONSTRAINT ck_app_user_role_validity CHECK (
        valid_from IS NULL OR valid_until IS NULL OR valid_until > valid_from
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端用户角色关联表';

CREATE TABLE IF NOT EXISTS app_login_log (
    login_log_id BIGINT NOT NULL COMMENT '登录日志 ID',
    auth_method VARCHAR(32) NOT NULL COMMENT '认证方式',
    masked_identifier VARCHAR(128) NOT NULL COMMENT '脱敏标识',
    client_id VARCHAR(64) NOT NULL COMMENT '客户端标识',
    result_code INT NOT NULL COMMENT '结果编码',
    failure_category VARCHAR(32) DEFAULT NULL COMMENT '失败分类',
    user_id BIGINT DEFAULT NULL COMMENT '创作端用户 ID',
    session_id VARCHAR(128) DEFAULT NULL COMMENT '会话 ID',
    ip_address VARCHAR(64) NOT NULL COMMENT 'IP 地址',
    device_summary VARCHAR(255) DEFAULT NULL COMMENT '设备摘要',
    request_id VARCHAR(64) NOT NULL COMMENT '请求 ID',
    occurred_at DATETIME NOT NULL COMMENT '发生时间',
    PRIMARY KEY (login_log_id),
    KEY idx_app_login_user_time (user_id, occurred_at),
    KEY idx_app_login_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端登录日志表';

CREATE TABLE IF NOT EXISTS app_security_audit (
    audit_id BIGINT NOT NULL COMMENT '安全审计 ID',
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型',
    resource_id VARCHAR(64) NOT NULL COMMENT '资源 ID',
    action VARCHAR(64) NOT NULL COMMENT '操作',
    actor_type VARCHAR(16) NOT NULL COMMENT '主体类型',
    actor_id BIGINT NOT NULL COMMENT '主体 ID',
    before_digest VARCHAR(128) DEFAULT NULL COMMENT '变更前摘要',
    after_digest VARCHAR(128) DEFAULT NULL COMMENT '变更后摘要',
    reason VARCHAR(500) NOT NULL COMMENT '原因',
    request_id VARCHAR(64) NOT NULL COMMENT '请求 ID',
    ip_address VARCHAR(64) NOT NULL COMMENT 'IP 地址',
    occurred_at DATETIME NOT NULL COMMENT '发生时间',
    PRIMARY KEY (audit_id),
    KEY idx_app_audit_resource (resource_type, resource_id, occurred_at),
    KEY idx_app_audit_actor (actor_type, actor_id, occurred_at),
    CONSTRAINT ck_app_security_audit_actor_type CHECK (actor_type IN ('app_user', 'sys_user'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作端安全审计表';

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
) VALUES
    (1000001, 'aivideo:studio:query', '工作台查看', 'studio', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000002, 'aivideo:studio:create', '工作台创建', 'studio', 'create', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000003, 'aivideo:studio:edit', '工作台编辑', 'studio', 'edit', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000004, 'aivideo:studio:generate', '工作台生成', 'studio', 'generate', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000005, 'aivideo:script:query', '脚本查看', 'script', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000006, 'aivideo:script:edit', '脚本编辑', 'script', 'edit', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000007, 'aivideo:script:confirm', '脚本确认', 'script', 'confirm', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000008, 'aivideo:script:remove', '脚本删除', 'script', 'remove', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000009, 'aivideo:task:query', '任务查看', 'task', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000010, 'aivideo:task:cancel', '任务取消', 'task', 'cancel', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000011, 'aivideo:quota:query', '额度查看', 'quota', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000012, 'aivideo:quota:use', '额度使用', 'quota', 'use', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000013, 'aivideo:quota:organization-query', '组织额度查看', 'quota', 'organization-query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000014, 'aivideo:notification:query', '通知查看', 'notification', 'query', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()),
    (1000015, 'aivideo:notification:edit', '通知编辑', 'notification', 'edit', 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    permission_code = VALUES(permission_code),
    permission_name = VALUES(permission_name),
    resource_type = VALUES(resource_type),
    action = VALUES(action),
    permission_revision = VALUES(permission_revision),
    status = VALUES(status),
    updated_by_type = VALUES(updated_by_type),
    updated_by_id = VALUES(updated_by_id);

INSERT INTO app_role (
    role_id, role_code, role_name, scope_type, built_in, role_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time, del_flag
) VALUES
    (1000101, 'personal_creator', '个人创作者', 'personal', 1, 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW(), '0'),
    (1000102, 'organization_owner', '组织所有者', 'organization', 1, 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW(), '0'),
    (1000103, 'organization_admin', '组织管理员', 'organization', 1, 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW(), '0'),
    (1000104, 'organization_member', '组织成员', 'organization', 1, 1, 'active', 'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW(), '0')
ON DUPLICATE KEY UPDATE
    role_code = VALUES(role_code),
    role_name = VALUES(role_name),
    scope_type = VALUES(scope_type),
    built_in = VALUES(built_in),
    role_revision = VALUES(role_revision),
    status = VALUES(status),
    updated_by_type = VALUES(updated_by_type),
    updated_by_id = VALUES(updated_by_id),
    del_flag = VALUES(del_flag);

-- 运营菜单固定使用 1761400000000020000-1761400000000020016，共 1 个目录、6 个页面和 10 个操作按钮。
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache,
    menu_type, visible, status, perms, icon, create_dept, create_by, create_time,
    update_by, update_time, remark
) VALUES
    (1761400000000020000, '创作端身份安全', 0, 10, 'aivideo-identity', NULL, 'N', 'Y', 'M', '0', '0', '', 'safety-certificate', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端身份安全目录'),
    (1761400000000020001, '用户', 1761400000000020000, 1, 'app-user', 'aivideo/app-user/index', 'N', 'Y', 'C', '0', '0', 'aivideo:app-user:query', 'user', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端用户管理'),
    (1761400000000020002, '角色与权限', 1761400000000020000, 2, 'app-role', 'aivideo/app-role/index', 'N', 'Y', 'C', '0', '0', 'aivideo:app-role:query', 'peoples', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端角色与权限管理'),
    (1761400000000020003, '认证客户端', 1761400000000020000, 3, 'app-auth-client', 'aivideo/app-auth-client/index', 'N', 'Y', 'C', '0', '0', 'aivideo:app-auth-client:query', 'international', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端认证客户端管理'),
    (1761400000000020004, '创作端会话', 1761400000000020000, 4, 'app-session', 'aivideo/app-session/index', 'N', 'Y', 'C', '0', '0', 'aivideo:app-session:query', 'online', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端会话管理'),
    (1761400000000020005, '创作端登录日志', 1761400000000020000, 5, 'app-login-log', 'aivideo/app-login-log/index', 'N', 'Y', 'C', '0', '0', 'aivideo:app-login-log:query', 'logininfo', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端登录日志'),
    (1761400000000020006, '创作端安全审计', 1761400000000020000, 6, 'app-security-audit', 'aivideo/app-security-audit/index', 'N', 'Y', 'C', '0', '0', 'aivideo:app-security-audit:query', 'form', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '创作端安全审计'),
    (1761400000000020007, '用户新增', 1761400000000020001, 1, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-user:add', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020008, '用户修改', 1761400000000020001, 2, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-user:edit', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020009, '重置密码', 1761400000000020001, 3, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-user:reset-password', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020010, '强制下线', 1761400000000020001, 4, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-user:kickout', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020011, '分配角色', 1761400000000020001, 5, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-user:assign-role', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020012, '角色修改', 1761400000000020002, 1, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-role:edit', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020013, '分配权限', 1761400000000020002, 2, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-role:assign-permission', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020014, '客户端修改', 1761400000000020003, 1, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-auth-client:edit', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020015, '轮换密钥', 1761400000000020003, 2, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-auth-client:rotate-secret', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), ''),
    (1761400000000020016, '会话下线', 1761400000000020004, 1, '#', '', 'N', 'Y', 'F', '0', '0', 'aivideo:app-session:kickout', '#', 1761000000000000103, 1761100000000000001, NOW(), 1761100000000000001, NOW(), '')
ON DUPLICATE KEY UPDATE
    menu_name = VALUES(menu_name),
    parent_id = VALUES(parent_id),
    order_num = VALUES(order_num),
    path = VALUES(path),
    component = VALUES(component),
    is_frame = VALUES(is_frame),
    is_cache = VALUES(is_cache),
    menu_type = VALUES(menu_type),
    visible = VALUES(visible),
    status = VALUES(status),
    perms = VALUES(perms),
    icon = VALUES(icon),
    update_by = VALUES(update_by),
    remark = VALUES(remark);
