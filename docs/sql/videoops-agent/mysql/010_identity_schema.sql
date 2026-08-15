-- VideoOps Agent T1 identity schema.
-- Derived from: 20260728_01_p0a_identity_security.sql.
-- Schema only. No application, provider, OSS, or business seed data is written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_010_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_010_target_assert_sql = IF(
    @videoops_010_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_010_wrong_target_database'
);
PREPARE videoops_010_target_assert_stmt FROM @videoops_010_target_assert_sql;
EXECUTE videoops_010_target_assert_stmt;
DEALLOCATE PREPARE videoops_010_target_assert_stmt;


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

SET @videoops_010_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 9
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('app_user', 'app_auth_client', 'app_social_identity', 'app_permission', 'app_role', 'app_role_permission', 'app_user_role', 'app_login_log', 'app_security_audit'))
    AND (SELECT COUNT(*) = 18
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('app_user', 'user_id'),
              ('app_user', 'username_normalized'),
              ('app_user', 'personal_tenant_id'),
              ('app_user', 'permission_revision'),
              ('app_user', 'del_flag'),
              ('app_auth_client', 'client_id'),
              ('app_auth_client', 'client_key'),
              ('app_auth_client', 'client_secret_hash'),
              ('app_auth_client', 'status'),
              ('app_social_identity', 'provider_subject'),
              ('app_permission', 'permission_code'),
              ('app_role', 'role_code'),
              ('app_role_permission', 'role_id'),
              ('app_role_permission', 'permission_id'),
              ('app_user_role', 'user_id'),
              ('app_user_role', 'role_id'),
              ('app_login_log', 'request_id'),
              ('app_security_audit', 'actor_type')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 9
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('app_user', 'uk_app_user_username_normalized'),
              ('app_user', 'uk_app_user_personal_tenant_id'),
              ('app_auth_client', 'uk_app_auth_client_client_id'),
              ('app_auth_client', 'uk_app_auth_client_client_key'),
              ('app_social_identity', 'uk_app_social_identity_provider_subject'),
              ('app_permission', 'uk_app_permission_code'),
              ('app_role', 'uk_app_role_role_code'),
              ('app_role_permission', 'uk_app_role_permission_role_permission'),
              ('app_user_role', 'uk_app_user_role_user_role')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 7
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('app_user', 'ck_app_user_actor_types'),
              ('app_auth_client', 'ck_app_auth_client_actor_types'),
              ('app_permission', 'ck_app_permission_actor_types'),
              ('app_role', 'ck_app_role_scope_type'),
              ('app_role_permission', 'ck_app_role_permission_actor_types'),
              ('app_user_role', 'ck_app_user_role_validity'),
              ('app_security_audit', 'ck_app_security_audit_actor_type')
          ))
);
SET @videoops_010_schema_assert_sql = IF(
    @videoops_010_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_010_schema_contract_failed'
);
PREPARE videoops_010_schema_assert_stmt FROM @videoops_010_schema_assert_sql;
EXECUTE videoops_010_schema_assert_stmt;
DEALLOCATE PREPARE videoops_010_schema_assert_stmt;
