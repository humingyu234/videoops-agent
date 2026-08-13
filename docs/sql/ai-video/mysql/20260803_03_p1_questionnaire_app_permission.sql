-- Grant personal creators access to the questionnaire generation entrypoint.
-- Stable identifiers and upsert semantics make this migration safe to replay.
INSERT INTO app_role_permission (
    id, role_id, permission_id, status,
    created_by_type, created_by_id,
    updated_by_type, updated_by_id,
    create_time, update_time
)
SELECT
    2026080303000001,
    r.role_id,
    p.permission_id,
    'active',
    'sys_user',
    1761100000000000001,
    'sys_user',
    1761100000000000001,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM app_role AS r
JOIN app_permission AS p
  ON p.permission_id = 1000004
 AND p.permission_code = 'aivideo:studio:generate'
 AND p.status = 'active'
WHERE r.role_id = 1000101
  AND r.role_code = 'personal_creator'
  AND r.scope_type = 'personal'
  AND r.built_in = 1
  AND r.status = 'active'
  AND r.del_flag = '0'
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    updated_by_type = VALUES(updated_by_type),
    updated_by_id = VALUES(updated_by_id),
    update_time = VALUES(update_time);
