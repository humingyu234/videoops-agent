START TRANSACTION;

CREATE TEMPORARY TABLE tmp_voice_delete_permission_guard (
    guard_name VARCHAR(64) NOT NULL PRIMARY KEY,
    valid_value TINYINT NOT NULL,
    CONSTRAINT ck_tmp_voice_delete_permission_guard CHECK (valid_value = 1)
);

INSERT INTO tmp_voice_delete_permission_guard (guard_name, valid_value)
SELECT 'personal_creator_role',
       (
           (SELECT COUNT(*) FROM app_role WHERE role_id = 1000101) = 1
           AND (SELECT COUNT(*) FROM app_role WHERE role_code = 'personal_creator') = 1
           AND (SELECT COUNT(*) FROM app_role
                WHERE role_id = 1000101
                  AND role_code = 'personal_creator'
                  AND scope_type = 'personal'
                  AND status = 'active'
                  AND del_flag = '0') = 1
       );

INSERT INTO tmp_voice_delete_permission_guard (guard_name, valid_value)
SELECT 'voice_delete_permission_precondition',
       (
           (
               (SELECT COUNT(*) FROM app_permission WHERE permission_id = 1000024) = 0
               AND (SELECT COUNT(*) FROM app_permission WHERE permission_code = 'aivideo:voice:delete') = 0
           )
           OR
           (
               (SELECT COUNT(*) FROM app_permission WHERE permission_id = 1000024) = 1
               AND (SELECT COUNT(*) FROM app_permission WHERE permission_code = 'aivideo:voice:delete') = 1
               AND (SELECT COUNT(*) FROM app_permission
                    WHERE permission_id = 1000024
                      AND permission_code = 'aivideo:voice:delete'
                      AND permission_name = '声音删除'
                      AND resource_type = 'voice'
                      AND action = 'delete'
                      AND permission_revision = 1
                      AND status = 'active'
                      AND created_by_type = 'sys_user'
                      AND created_by_id = 1761100000000000001
                      AND updated_by_type = 'sys_user'
                      AND updated_by_id = 1761100000000000001) = 1
           )
       );

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
)
SELECT 1000024, 'aivideo:voice:delete', '声音删除', 'voice', 'delete', 1, 'active',
       'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM app_permission
    WHERE permission_id = 1000024
      AND permission_code = 'aivideo:voice:delete'
      AND permission_name = '声音删除'
      AND resource_type = 'voice'
      AND action = 'delete'
      AND permission_revision = 1
      AND status = 'active'
      AND created_by_type = 'sys_user'
      AND created_by_id = 1761100000000000001
      AND updated_by_type = 'sys_user'
      AND updated_by_id = 1761100000000000001
);

INSERT INTO tmp_voice_delete_permission_guard (guard_name, valid_value)
SELECT 'voice_delete_binding_precondition',
       (
           (
               (SELECT COUNT(*) FROM app_role_permission WHERE id = 1000224) = 0
               AND (SELECT COUNT(*) FROM app_role_permission
                    WHERE role_id = 1000101 AND permission_id = 1000024) = 0
           )
           OR
           (
               (SELECT COUNT(*) FROM app_role_permission WHERE id = 1000224) = 1
               AND (SELECT COUNT(*) FROM app_role_permission
                    WHERE role_id = 1000101 AND permission_id = 1000024) = 1
               AND (SELECT COUNT(*) FROM app_role_permission
                    WHERE id = 1000224
                      AND role_id = 1000101
                      AND permission_id = 1000024
                      AND status = 'active'
                      AND created_by_type = 'sys_user'
                      AND created_by_id = 1761100000000000001
                      AND updated_by_type = 'sys_user'
                      AND updated_by_id = 1761100000000000001) = 1
           )
       );

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
)
SELECT 1000224, 1000101, 1000024, 'active', 'sys_user', 1761100000000000001,
       'sys_user', 1761100000000000001, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_permission
    WHERE id = 1000224
      AND role_id = 1000101
      AND permission_id = 1000024
      AND status = 'active'
      AND created_by_type = 'sys_user'
      AND created_by_id = 1761100000000000001
      AND updated_by_type = 'sys_user'
      AND updated_by_id = 1761100000000000001
);
SET @voice_delete_binding_inserted = ROW_COUNT();

UPDATE app_role
SET role_revision = role_revision + 1,
    updated_by_type = 'sys_user',
    updated_by_id = 1761100000000000001,
    update_time = NOW()
WHERE @voice_delete_binding_inserted = 1
  AND role_id = 1000101
  AND role_code = 'personal_creator'
  AND scope_type = 'personal'
  AND status = 'active'
  AND del_flag = '0';

UPDATE app_user AS app_user
JOIN app_user_role AS app_user_role
  ON app_user_role.user_id = app_user.user_id
 AND app_user_role.role_id = 1000101
SET app_user.permission_revision = app_user.permission_revision + 1,
    app_user.updated_by_type = 'sys_user',
    app_user.updated_by_id = 1761100000000000001,
    app_user.update_time = NOW()
WHERE @voice_delete_binding_inserted = 1
  AND app_user.status = 'active'
  AND app_user.del_flag = '0'
  AND app_user_role.status = 'active'
  AND (app_user_role.valid_from IS NULL OR app_user_role.valid_from <= NOW())
  AND (app_user_role.valid_until IS NULL OR app_user_role.valid_until > NOW());

INSERT INTO tmp_voice_delete_permission_guard (guard_name, valid_value)
SELECT 'voice_delete_permission_postcondition',
       ((SELECT COUNT(*) FROM app_permission
         WHERE permission_id = 1000024
           AND permission_code = 'aivideo:voice:delete'
           AND permission_name = '声音删除'
           AND resource_type = 'voice'
           AND action = 'delete'
           AND permission_revision = 1
           AND status = 'active'
           AND created_by_type = 'sys_user'
           AND created_by_id = 1761100000000000001
           AND updated_by_type = 'sys_user'
           AND updated_by_id = 1761100000000000001) = 1);

INSERT INTO tmp_voice_delete_permission_guard (guard_name, valid_value)
SELECT 'voice_delete_binding_postcondition',
       ((SELECT COUNT(*) FROM app_role_permission
         WHERE id = 1000224
           AND role_id = 1000101
           AND permission_id = 1000024
           AND status = 'active'
           AND created_by_type = 'sys_user'
           AND created_by_id = 1761100000000000001
           AND updated_by_type = 'sys_user'
           AND updated_by_id = 1761100000000000001) = 1);

DROP TEMPORARY TABLE tmp_voice_delete_permission_guard;
COMMIT;

-- 人工回滚：先核对并删除 id=1000224、role_id=1000101、permission_id=1000024 的精确绑定；任一字段漂移立即停止。
-- 人工回滚：再核对并删除 permission_id=1000024、permission_code='aivideo:voice:delete' 的精确权限；任一字段漂移立即停止。
