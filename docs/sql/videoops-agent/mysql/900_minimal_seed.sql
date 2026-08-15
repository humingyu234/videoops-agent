-- VideoOps Agent T1 最小合成 seed。
--
-- 前置条件：
-- 1. 当前 schema 必须精确为 videoops_agent_dev；
-- 2. 最终 schema baseline 与迁移已经完成；
-- 3. 调用方必须在当前 MySQL 会话中设置 @videoops_creator_password_hash，
--    值只能来自本机安全生成的 BCrypt 摘要，不得把明文口令写入本文件。
--
-- 本文件只写入 T1 黄金链的账号、客户端、权限、积分和合成文案知识。
-- 不包含人物、声音、OSS、任务、Provider、RunningHub 或公司业务数据。

SET NAMES utf8mb4;

-- 仅会话级门禁。必须由未启用 --force 的批处理客户端执行：任一 CHECK
-- 失败都会终止批处理；若已经开始事务，连接关闭时 MySQL 会回滚未提交写入。
DROP TEMPORARY TABLE IF EXISTS videoops_seed_guard;
CREATE TEMPORARY TABLE videoops_seed_guard (
    ok TINYINT NOT NULL CHECK (ok = 1)
) ENGINE = MEMORY;

-- 错库时只创建临时表，不发生任何持久写入。
INSERT INTO videoops_seed_guard (ok)
VALUES (
    DATABASE() IS NOT NULL
    AND BINARY DATABASE() = BINARY 'videoops_agent_dev'
);

-- 只接受完整的最小 baseline；缺表时在持久事务开始前失败。
INSERT INTO videoops_seed_guard (ok)
SELECT COUNT(*) = 11
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_type = 'BASE TABLE'
  AND table_name IN (
      'app_user', 'app_auth_client', 'app_permission', 'app_role',
      'app_role_permission', 'app_user_role', 'av_quota_account',
      'av_knowledge_item', 'av_knowledge_version', 'av_knowledge_binding',
      'av_video_type_rule'
  );

-- 摘要只能由当前会话注入；文件本身不提供默认口令或默认摘要。
INSERT INTO videoops_seed_guard (ok)
VALUES (
    COALESCE(
        CHAR_LENGTH(@videoops_creator_password_hash) = 60
        AND REGEXP_LIKE(
            @videoops_creator_password_hash,
            '^[$]2[aby][$](0[4-9]|[12][0-9]|3[01])[$][./A-Za-z0-9]{53}$',
            'c'
        ),
        0
    )
);

SET @videoops_seed_now = TIMESTAMP('2026-08-14 00:00:00');
SET @videoops_flow_content =
    '先确认用户的交付目标、受众和时长。只使用用户提供且可验证的事实，生成简短、可朗读的中文口播文案；不得编造价格、效果、资质或承诺。';

START TRANSACTION;

    INSERT IGNORE INTO app_user (
        user_id, username, username_normalized, password_hash,
        phone_normalized, email_normalized, personal_tenant_id, display_name,
        status, must_change_password, credential_revision, identity_revision,
        permission_revision, created_by_type, created_by_id, updated_by_type,
        updated_by_id, create_time, update_time, del_flag
    ) VALUES (
        910000000000000001, 'videoops_creator', 'videoops_creator',
        @videoops_creator_password_hash,
        NULL, NULL, 910000000000000001, 'VideoOps T1 合成用户',
        'active', 0, 1, 1, 1,
        'app_user', 910000000000000001, 'app_user', 910000000000000001,
        @videoops_seed_now, @videoops_seed_now, '0'
    );

    INSERT IGNORE INTO app_auth_client (
        id, client_id, client_key, client_secret_hash, grant_types, access_paths,
        ip_whitelist, token_timeout, active_timeout, client_revision, status,
        created_by_type, created_by_id, updated_by_type, updated_by_id,
        create_time, update_time, del_flag
    ) VALUES (
        910000000000000002, 'videoops-desktop-web-local', 'desktop-web', NULL,
        'password', '/api/**', '127.0.0.0/8,::1/128', 86400, 1800, 1, 'active',
        'app_user', 910000000000000001, 'app_user', 910000000000000001,
        @videoops_seed_now, @videoops_seed_now, '0'
    );

    INSERT IGNORE INTO app_role (
        role_id, role_code, role_name, scope_type, built_in, role_revision,
        status, created_by_type, created_by_id, updated_by_type, updated_by_id,
        create_time, update_time, del_flag
    ) VALUES (
        910000000000000010, 'personal_creator', 'VideoOps 个人创作者',
        'personal', 1, 1, 'active',
        'app_user', 910000000000000001, 'app_user', 910000000000000001,
        @videoops_seed_now, @videoops_seed_now, '0'
    );

    INSERT IGNORE INTO app_permission (
        permission_id, permission_code, permission_name, resource_type, action,
        permission_revision, status, created_by_type, created_by_id,
        updated_by_type, updated_by_id, create_time, update_time
    ) VALUES
        (910000000000000101, 'aivideo:studio:query', '工作台查看', 'studio', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000102, 'aivideo:studio:generate', '工作台生成', 'studio', 'generate',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000103, 'aivideo:portrait:query', '人物形象查看', 'portrait', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000104, 'aivideo:voice:query', '声音查看', 'voice', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000105, 'aivideo:creation:query', '创作项目查看', 'creation', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000106, 'aivideo:creation:edit', '创作项目编辑', 'creation', 'edit',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000107, 'aivideo:creation:generate', '创作内容生成', 'creation', 'generate',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000108, 'aivideo:creation-asset:query', '创作素材查看', 'creation-asset', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000109, 'aivideo:task:query', '生成任务查看', 'task', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000110, 'aivideo:quota:query', '积分账户查看', 'quota', 'query',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000111, 'aivideo:portrait:add', '人物形象创建', 'portrait', 'add',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000112, 'aivideo:voice:upload', '声音上传', 'voice', 'upload',
         1, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now);

    INSERT IGNORE INTO app_role_permission (
        id, role_id, permission_id, status, created_by_type, created_by_id,
        updated_by_type, updated_by_id, create_time, update_time
    ) VALUES
        (910000000000000201, 910000000000000010, 910000000000000101, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000202, 910000000000000010, 910000000000000102, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000203, 910000000000000010, 910000000000000103, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000204, 910000000000000010, 910000000000000104, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000205, 910000000000000010, 910000000000000105, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000206, 910000000000000010, 910000000000000106, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000207, 910000000000000010, 910000000000000107, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000208, 910000000000000010, 910000000000000108, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000209, 910000000000000010, 910000000000000109, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000210, 910000000000000010, 910000000000000110, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000211, 910000000000000010, 910000000000000111, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now),
        (910000000000000212, 910000000000000010, 910000000000000112, 'active', 'app_user', 910000000000000001, 'app_user', 910000000000000001, @videoops_seed_now, @videoops_seed_now);

    INSERT IGNORE INTO app_user_role (
        id, user_id, role_id, status, valid_from, valid_until,
        created_by_type, created_by_id, updated_by_type, updated_by_id,
        create_time, update_time
    ) VALUES (
        910000000000000301, 910000000000000001, 910000000000000010,
        'active', NULL, NULL,
        'app_user', 910000000000000001, 'app_user', 910000000000000001,
        @videoops_seed_now, @videoops_seed_now
    );

    INSERT IGNORE INTO av_quota_account (
        id, tenant_id, subject_type, subject_id, unit_code,
        available_balance, locked_balance, used_balance, account_revision,
        create_time, update_time
    ) VALUES (
        910000000000000401, 910000000000000001, 'app_user',
        910000000000000001, 'ai_text_credit', 1000, 0, 0, 0,
        @videoops_seed_now, @videoops_seed_now
    );

    INSERT IGNORE INTO av_knowledge_item (
        knowledge_item_id, domain_code, knowledge_type_code, stable_code, name,
        summary, tags_json, current_published_version_id, source_type, source_ref,
        create_dept, create_by, create_time, update_by, update_time
    ) VALUES (
        910000000000000501, 'copywriting', 'mandatory_rule',
        'videoops_t1_copywriting_flow', 'VideoOps T1 合成文案流程',
        '仅用于验证问卷与文案链的数据依赖。', JSON_ARRAY('videoops_t1'),
        2084460032627961859, 'synthetic_seed', 'videoops-agent:t1-minimal:v1',
        NULL, NULL, @videoops_seed_now, NULL, @videoops_seed_now
    );

    INSERT IGNORE INTO av_knowledge_version (
        knowledge_version_id, knowledge_item_id, version_no, status, content,
        structure_json, source_summary, reviewed_by, reviewed_at, published_by,
        published_at, create_dept, create_by, create_time, update_by, update_time
    ) VALUES (
        2084460032627961859, 910000000000000501, 1, 'published', @videoops_flow_content,
        JSON_OBJECT(), 'VideoOps T1 纯合成最小知识', NULL, NULL, NULL,
        @videoops_seed_now, NULL, NULL, @videoops_seed_now, NULL, @videoops_seed_now
    );

    INSERT IGNORE INTO av_knowledge_binding (
        knowledge_binding_id, binding_group_code, version_no,
        knowledge_item_id, knowledge_version_id, industry_code, purpose_code,
        video_type_code, angle_codes_json, angle_priorities_json,
        min_duration_seconds, max_duration_seconds, priority, required_flag,
        required_slot_codes_json, audience_tag_codes_json,
        exclusion_conditions_json, status, create_dept, create_by, create_time,
        update_by, update_time
    ) VALUES (
        910000000000000502, 'videoops_t1_copywriting_flow_binding', 1,
        910000000000000501, 2084460032627961859, '*', '*', '*',
        JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 0, 0,
        JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published',
        NULL, NULL, @videoops_seed_now, NULL, @videoops_seed_now
    );

    INSERT IGNORE INTO av_video_type_rule (
        video_type_rule_id, rule_code, version_no, video_type_code,
        industry_code, purpose_code, min_duration_seconds, max_duration_seconds,
        required_slot_codes_json, priority, copy_rules_json, status,
        published_at, create_dept, create_by, create_time, update_by, update_time
    ) VALUES (
        910000000000000503, 'videoops_t1_default_copy_rules', 1, '*', '*', '*',
        NULL, NULL, JSON_ARRAY(), 0,
        JSON_ARRAY(
            '只使用用户提供且可验证的事实',
            '使用简短、可朗读的中文句子',
            '不得编造价格、效果、资质或承诺'
        ),
        'published', @videoops_seed_now, NULL, NULL, @videoops_seed_now, NULL, @videoops_seed_now
    );

-- 每张允许表分别验证总行数与精确业务键；INSERT IGNORE 未写入预期行、
-- 或空库中出现任何未知行时，CHECK 都会失败并阻止 COMMIT。
INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM app_user) = 1
        AND (SELECT COUNT(*) FROM app_user
             WHERE user_id = 910000000000000001
               AND username = 'videoops_creator'
               AND username_normalized = 'videoops_creator'
               AND password_hash = @videoops_creator_password_hash
               AND phone_normalized IS NULL
               AND email_normalized IS NULL
               AND personal_tenant_id = 910000000000000001
               AND display_name = 'VideoOps T1 合成用户'
               AND status = 'active'
               AND must_change_password = 0
               AND credential_revision = 1
               AND identity_revision = 1
               AND permission_revision = 1
               AND created_by_type = 'app_user'
               AND created_by_id = 910000000000000001
               AND updated_by_type = 'app_user'
               AND updated_by_id = 910000000000000001
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now
               AND del_flag = '0') = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM app_auth_client) = 1
        AND (SELECT COUNT(*) FROM app_auth_client
             WHERE id = 910000000000000002
               AND client_id = 'videoops-desktop-web-local'
               AND client_key = 'desktop-web'
               AND client_secret_hash IS NULL
               AND grant_types = 'password'
               AND access_paths = '/api/**'
               AND ip_whitelist = '127.0.0.0/8,::1/128'
               AND token_timeout = 86400
               AND active_timeout = 1800
               AND active_timeout <= token_timeout
               AND client_revision = 1
               AND status = 'active'
               AND created_by_type = 'app_user'
               AND created_by_id = 910000000000000001
               AND updated_by_type = 'app_user'
               AND updated_by_id = 910000000000000001
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now
               AND del_flag = '0') = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM app_role) = 1
        AND (SELECT COUNT(*) FROM app_role
             WHERE role_id = 910000000000000010
               AND role_code = 'personal_creator'
               AND role_name = 'VideoOps 个人创作者'
               AND scope_type = 'personal'
               AND built_in = 1
               AND role_revision = 1
               AND status = 'active'
               AND created_by_type = 'app_user'
               AND created_by_id = 910000000000000001
               AND updated_by_type = 'app_user'
               AND updated_by_id = 910000000000000001
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now
               AND del_flag = '0') = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM app_permission) = 12
        AND (SELECT COUNT(*)
             FROM app_permission
             WHERE permission_revision = 1
               AND status = 'active'
               AND created_by_type = 'app_user'
               AND created_by_id = 910000000000000001
               AND updated_by_type = 'app_user'
               AND updated_by_id = 910000000000000001
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now
               AND (
                   (permission_id = 910000000000000101 AND permission_code = 'aivideo:studio:query' AND permission_name = '工作台查看' AND resource_type = 'studio' AND action = 'query')
                   OR (permission_id = 910000000000000102 AND permission_code = 'aivideo:studio:generate' AND permission_name = '工作台生成' AND resource_type = 'studio' AND action = 'generate')
                   OR (permission_id = 910000000000000103 AND permission_code = 'aivideo:portrait:query' AND permission_name = '人物形象查看' AND resource_type = 'portrait' AND action = 'query')
                   OR (permission_id = 910000000000000104 AND permission_code = 'aivideo:voice:query' AND permission_name = '声音查看' AND resource_type = 'voice' AND action = 'query')
                   OR (permission_id = 910000000000000105 AND permission_code = 'aivideo:creation:query' AND permission_name = '创作项目查看' AND resource_type = 'creation' AND action = 'query')
                   OR (permission_id = 910000000000000106 AND permission_code = 'aivideo:creation:edit' AND permission_name = '创作项目编辑' AND resource_type = 'creation' AND action = 'edit')
                   OR (permission_id = 910000000000000107 AND permission_code = 'aivideo:creation:generate' AND permission_name = '创作内容生成' AND resource_type = 'creation' AND action = 'generate')
                   OR (permission_id = 910000000000000108 AND permission_code = 'aivideo:creation-asset:query' AND permission_name = '创作素材查看' AND resource_type = 'creation-asset' AND action = 'query')
                   OR (permission_id = 910000000000000109 AND permission_code = 'aivideo:task:query' AND permission_name = '生成任务查看' AND resource_type = 'task' AND action = 'query')
                   OR (permission_id = 910000000000000110 AND permission_code = 'aivideo:quota:query' AND permission_name = '积分账户查看' AND resource_type = 'quota' AND action = 'query')
                   OR (permission_id = 910000000000000111 AND permission_code = 'aivideo:portrait:add' AND permission_name = '人物形象创建' AND resource_type = 'portrait' AND action = 'add')
                   OR (permission_id = 910000000000000112 AND permission_code = 'aivideo:voice:upload' AND permission_name = '声音上传' AND resource_type = 'voice' AND action = 'upload')
               )) = 12

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM app_role_permission) = 12
        AND (SELECT COUNT(*)
             FROM app_role_permission
             WHERE role_id = 910000000000000010
               AND permission_id BETWEEN 910000000000000101 AND 910000000000000112
               AND id = permission_id + 100
               AND status = 'active'
               AND created_by_type = 'app_user'
               AND created_by_id = 910000000000000001
               AND updated_by_type = 'app_user'
               AND updated_by_id = 910000000000000001
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now) = 12

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM app_user_role) = 1
        AND (SELECT COUNT(*) FROM app_user_role
             WHERE id = 910000000000000301
               AND user_id = 910000000000000001
               AND role_id = 910000000000000010
               AND status = 'active'
               AND valid_from IS NULL
               AND valid_until IS NULL
               AND created_by_type = 'app_user'
               AND created_by_id = 910000000000000001
               AND updated_by_type = 'app_user'
               AND updated_by_id = 910000000000000001
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now) = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM av_quota_account) = 1
        AND (SELECT COUNT(*) FROM av_quota_account
             WHERE id = 910000000000000401
               AND tenant_id = 910000000000000001
               AND subject_type = 'app_user'
               AND subject_id = 910000000000000001
               AND unit_code = 'ai_text_credit'
               AND available_balance = 1000
               AND locked_balance = 0
               AND used_balance = 0
               AND account_revision = 0
               AND create_time = @videoops_seed_now
               AND update_time = @videoops_seed_now) = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM av_knowledge_item) = 1
        AND (SELECT COUNT(*) FROM av_knowledge_item
             WHERE knowledge_item_id = 910000000000000501
               AND domain_code = 'copywriting'
               AND knowledge_type_code = 'mandatory_rule'
               AND stable_code = 'videoops_t1_copywriting_flow'
               AND name = 'VideoOps T1 合成文案流程'
               AND summary = '仅用于验证问卷与文案链的数据依赖。'
               AND current_published_version_id = 2084460032627961859
               AND source_type = 'synthetic_seed'
               AND source_ref = 'videoops-agent:t1-minimal:v1'
               AND JSON_TYPE(tags_json) = 'ARRAY'
               AND JSON_LENGTH(tags_json) = 1
               AND JSON_UNQUOTE(JSON_EXTRACT(tags_json, '$[0]')) = 'videoops_t1'
               AND create_dept IS NULL
               AND create_by IS NULL
               AND create_time = @videoops_seed_now
               AND update_by IS NULL
               AND update_time = @videoops_seed_now) = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM av_knowledge_version) = 1
        AND (SELECT COUNT(*) FROM av_knowledge_version
             WHERE knowledge_version_id = 2084460032627961859
               AND knowledge_item_id = 910000000000000501
               AND version_no = 1
               AND status = 'published'
               AND BINARY content = BINARY @videoops_flow_content
               AND CHAR_LENGTH(TRIM(content)) > 0
               AND JSON_TYPE(structure_json) = 'OBJECT'
               AND JSON_LENGTH(structure_json) = 0
               AND source_summary = 'VideoOps T1 纯合成最小知识'
               AND reviewed_by IS NULL
               AND reviewed_at IS NULL
               AND published_by IS NULL
               AND published_at = @videoops_seed_now
               AND create_dept IS NULL
               AND create_by IS NULL
               AND create_time = @videoops_seed_now
               AND update_by IS NULL
               AND update_time = @videoops_seed_now) = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM av_knowledge_binding) = 1
        AND (SELECT COUNT(*) FROM av_knowledge_binding
             WHERE knowledge_binding_id = 910000000000000502
               AND binding_group_code = 'videoops_t1_copywriting_flow_binding'
               AND version_no = 1
               AND knowledge_item_id = 910000000000000501
               AND knowledge_version_id = 2084460032627961859
               AND industry_code = '*'
               AND purpose_code = '*'
               AND video_type_code = '*'
               AND JSON_TYPE(angle_codes_json) = 'ARRAY'
               AND JSON_LENGTH(angle_codes_json) = 0
               AND JSON_TYPE(angle_priorities_json) = 'OBJECT'
               AND JSON_LENGTH(angle_priorities_json) = 0
               AND min_duration_seconds IS NULL
               AND max_duration_seconds IS NULL
               AND priority = 0
               AND required_flag = 0
               AND JSON_LENGTH(required_slot_codes_json) = 0
               AND JSON_LENGTH(audience_tag_codes_json) = 0
               AND JSON_LENGTH(exclusion_conditions_json) = 0
               AND status = 'published'
               AND create_dept IS NULL
               AND create_by IS NULL
               AND create_time = @videoops_seed_now
               AND update_by IS NULL
               AND update_time = @videoops_seed_now) = 1

);

INSERT INTO videoops_seed_guard (ok)
SELECT (
        (SELECT COUNT(*) FROM av_video_type_rule) = 1
        AND (SELECT COUNT(*) FROM av_video_type_rule
             WHERE video_type_rule_id = 910000000000000503
               AND rule_code = 'videoops_t1_default_copy_rules'
               AND version_no = 1
               AND video_type_code = '*'
               AND industry_code = '*'
               AND purpose_code = '*'
               AND min_duration_seconds IS NULL
               AND max_duration_seconds IS NULL
               AND JSON_TYPE(required_slot_codes_json) = 'ARRAY'
               AND JSON_LENGTH(required_slot_codes_json) = 0
               AND priority = 0
               AND JSON_TYPE(copy_rules_json) = 'ARRAY'
               AND JSON_LENGTH(copy_rules_json) = 3
               AND JSON_UNQUOTE(JSON_EXTRACT(copy_rules_json, '$[0]')) = '只使用用户提供且可验证的事实'
               AND JSON_UNQUOTE(JSON_EXTRACT(copy_rules_json, '$[1]')) = '使用简短、可朗读的中文句子'
               AND JSON_UNQUOTE(JSON_EXTRACT(copy_rules_json, '$[2]')) = '不得编造价格、效果、资质或承诺'
               AND status = 'published'
               AND published_at = @videoops_seed_now
               AND create_dept IS NULL
               AND create_by IS NULL
               AND create_time = @videoops_seed_now
               AND update_by IS NULL
               AND update_time = @videoops_seed_now) = 1
);

COMMIT;
DROP TEMPORARY TABLE videoops_seed_guard;
