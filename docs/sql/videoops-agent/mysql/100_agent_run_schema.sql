-- VideoOps Agent T2 versioned delivery contract and recoverable AgentRun schema.
-- Schema only; no seed rows are written and no existing task table is replaced.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_100_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_100_target_assert_sql = IF(
    @videoops_100_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_100_wrong_target_database'
);
PREPARE videoops_100_target_assert_stmt FROM @videoops_100_target_assert_sql;
EXECUTE videoops_100_target_assert_stmt;
DEALLOCATE PREPARE videoops_100_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_delivery_brief_version (
    delivery_brief_version_id BIGINT NOT NULL,
    brief_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    version_no BIGINT NOT NULL,
    parent_version_id BIGINT NULL,
    schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    delivery_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    brief_json JSON NOT NULL,
    brief_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_brief_version_id),
    UNIQUE KEY uk_av_delivery_brief_version_no (owner_user_id, brief_id, version_no),
    UNIQUE KEY uk_av_delivery_brief_idempotency (owner_user_id, idempotency_key),
    CONSTRAINT ck_av_delivery_brief_version CHECK (version_no > 0),
    CONSTRAINT ck_av_delivery_brief_parent CHECK (
        (version_no = 1 AND parent_version_id IS NULL)
        OR (version_no > 1 AND parent_version_id IS NOT NULL)
    ),
    CONSTRAINT ck_av_delivery_brief_schema CHECK (schema_version = 'delivery-brief-1'),
    CONSTRAINT ck_av_delivery_brief_type CHECK (
        delivery_type = 'image_to_digital_human_video'
    ),
    CONSTRAINT ck_av_delivery_brief_content_size CHECK (
        OCTET_LENGTH(CAST(brief_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_delivery_brief_digests CHECK (
        brief_hash REGEXP '^[0-9a-f]{64}$'
        AND request_digest REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_av_delivery_brief_actor CHECK (
        actor_type = 'app_user' AND actor_id = owner_user_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变交付目标版本';

CREATE TABLE IF NOT EXISTS av_acceptance_profile_version (
    acceptance_profile_version_id BIGINT NOT NULL,
    acceptance_profile_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    delivery_brief_version_id BIGINT NOT NULL,
    version_no BIGINT NOT NULL,
    parent_version_id BIGINT NULL,
    schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    profile_json JSON NOT NULL,
    profile_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (acceptance_profile_version_id),
    UNIQUE KEY uk_av_acceptance_profile_version_no (
        owner_user_id, acceptance_profile_id, version_no
    ),
    UNIQUE KEY uk_av_acceptance_profile_idempotency (owner_user_id, idempotency_key),
    CONSTRAINT ck_av_acceptance_profile_version CHECK (version_no > 0),
    CONSTRAINT ck_av_acceptance_profile_parent CHECK (
        (version_no = 1 AND parent_version_id IS NULL)
        OR (version_no > 1 AND parent_version_id IS NOT NULL)
    ),
    CONSTRAINT ck_av_acceptance_profile_schema CHECK (
        schema_version = 'acceptance-profile-1'
    ),
    CONSTRAINT ck_av_acceptance_profile_policy CHECK (
        policy_version = 'acceptance-policy-1'
    ),
    CONSTRAINT ck_av_acceptance_profile_content_size CHECK (
        OCTET_LENGTH(CAST(profile_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_acceptance_profile_digests CHECK (
        profile_hash REGEXP '^[0-9a-f]{64}$'
        AND request_digest REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_av_acceptance_profile_actor CHECK (
        actor_type = 'app_user' AND actor_id = owner_user_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变验收偏好版本';

CREATE TABLE IF NOT EXISTS av_agent_run (
    agent_run_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    delivery_brief_version_id BIGINT NOT NULL,
    acceptance_profile_version_id BIGINT NOT NULL,
    contract_revision BIGINT NOT NULL,
    schema_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    run_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    lease_generation BIGINT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(128) NULL,
    lease_token_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(6) NULL,
    resume_after DATETIME(6) NULL,
    waiting_task_source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    waiting_task_id BIGINT NULL,
    waiting_contract_revision BIGINT NULL,
    candidate_asset_id BIGINT NULL,
    result_summary_json JSON NULL,
    result_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    state_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (agent_run_id),
    UNIQUE KEY uk_av_agent_run_idempotency (owner_user_id, idempotency_key),
    KEY idx_av_agent_run_owner_status (owner_user_id, run_status, state_changed_at),
    KEY idx_av_agent_run_dispatch (run_status, resume_after, lease_expires_at),
    KEY idx_av_agent_run_waiting_task (
        owner_user_id, waiting_task_source, waiting_task_id
    ),
    KEY idx_av_agent_run_contract (
        owner_user_id, delivery_brief_version_id, acceptance_profile_version_id
    ),
    CONSTRAINT ck_av_agent_run_contract CHECK (
        delivery_brief_version_id > 0
        AND acceptance_profile_version_id > 0
        AND contract_revision > 0
    ),
    CONSTRAINT ck_av_agent_run_schema CHECK (schema_version = 'agent-run-1'),
    CONSTRAINT ck_av_agent_run_status CHECK (
        run_status IN (
            'queued', 'running', 'waiting_external_task',
            'completed', 'failed', 'cancelled'
        )
    ),
    CONSTRAINT ck_av_agent_run_counters CHECK (
        row_version >= 0 AND lease_generation >= 0
    ),
    CONSTRAINT ck_av_agent_run_request_digest CHECK (
        request_digest REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_av_agent_run_lease CHECK (
        (
            run_status IN ('running', 'waiting_external_task')
            AND lease_generation > 0
            AND lease_owner IS NOT NULL
            AND CHAR_LENGTH(TRIM(lease_owner)) > 0
            AND lease_token_digest IS NOT NULL
            AND lease_token_digest REGEXP '^[0-9a-f]{64}$'
            AND lease_expires_at IS NOT NULL
        )
        OR
        (
            run_status NOT IN ('running', 'waiting_external_task')
            AND lease_owner IS NULL
            AND lease_token_digest IS NULL
            AND lease_expires_at IS NULL
        )
    ),
    CONSTRAINT ck_av_agent_run_waiting_task CHECK (
        (
            run_status = 'waiting_external_task'
            AND waiting_task_source IS NOT NULL
            AND waiting_task_source IN ('digital_human_generation', 'ai_task')
            AND waiting_task_id IS NOT NULL
            AND waiting_task_id > 0
            AND waiting_contract_revision IS NOT NULL
            AND waiting_contract_revision = contract_revision
            AND resume_after IS NOT NULL
        )
        OR
        (
            run_status <> 'waiting_external_task'
            AND waiting_task_source IS NULL
            AND waiting_task_id IS NULL
            AND waiting_contract_revision IS NULL
        )
    ),
    CONSTRAINT ck_av_agent_run_result CHECK (
        (
            run_status = 'completed'
            AND candidate_asset_id IS NOT NULL
            AND candidate_asset_id > 0
            AND result_summary_json IS NOT NULL
            AND result_digest IS NOT NULL
            AND result_digest REGEXP '^[0-9a-f]{64}$'
            AND error_code IS NULL
            AND error_summary IS NULL
        )
        OR
        (
            run_status = 'failed'
            AND candidate_asset_id IS NULL
            AND result_summary_json IS NULL
            AND result_digest IS NULL
            AND error_code IS NOT NULL
            AND error_summary IS NOT NULL
        )
        OR
        (
            run_status = 'cancelled'
            AND candidate_asset_id IS NULL
            AND result_summary_json IS NULL
            AND result_digest IS NULL
        )
        OR
        (
            run_status IN ('queued', 'running', 'waiting_external_task')
            AND candidate_asset_id IS NULL
            AND result_summary_json IS NULL
            AND result_digest IS NULL
            AND error_code IS NULL
            AND error_summary IS NULL
        )
    ),
    CONSTRAINT ck_av_agent_run_terminal_time CHECK (
        (
            run_status IN ('completed', 'failed', 'cancelled')
            AND finished_at IS NOT NULL
            AND resume_after IS NULL
        )
        OR
        (
            run_status NOT IN ('completed', 'failed', 'cancelled')
            AND finished_at IS NULL
        )
    ),
    CONSTRAINT ck_av_agent_run_result_size CHECK (
        result_summary_json IS NULL
        OR OCTET_LENGTH(CAST(result_summary_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_agent_run_actor CHECK (
        actor_type = 'app_user' AND actor_id = owner_user_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可恢复 Agent 运行状态';

SET @videoops_100_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 3
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN (
              'av_delivery_brief_version',
              'av_acceptance_profile_version',
              'av_agent_run'
          ))
    AND (SELECT COUNT(*) = 21
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_delivery_brief_version', 'delivery_brief_version_id'),
              ('av_delivery_brief_version', 'brief_id'),
              ('av_delivery_brief_version', 'owner_user_id'),
              ('av_delivery_brief_version', 'version_no'),
              ('av_delivery_brief_version', 'parent_version_id'),
              ('av_delivery_brief_version', 'brief_json'),
              ('av_delivery_brief_version', 'request_digest'),
              ('av_acceptance_profile_version', 'acceptance_profile_version_id'),
              ('av_acceptance_profile_version', 'acceptance_profile_id'),
              ('av_acceptance_profile_version', 'owner_user_id'),
              ('av_acceptance_profile_version', 'delivery_brief_version_id'),
              ('av_acceptance_profile_version', 'version_no'),
              ('av_acceptance_profile_version', 'profile_json'),
              ('av_acceptance_profile_version', 'request_digest'),
              ('av_agent_run', 'agent_run_id'),
              ('av_agent_run', 'owner_user_id'),
              ('av_agent_run', 'contract_revision'),
              ('av_agent_run', 'row_version'),
              ('av_agent_run', 'lease_generation'),
              ('av_agent_run', 'waiting_task_id'),
              ('av_agent_run', 'candidate_asset_id')
          ))
    AND (SELECT COUNT(*) = 2
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'av_agent_run'
          AND COLUMN_NAME IN ('lease_expires_at', 'resume_after')
          AND DATA_TYPE = 'datetime'
          AND DATETIME_PRECISION = 6)
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 9
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_delivery_brief_version', 'uk_av_delivery_brief_version_no'),
              ('av_delivery_brief_version', 'uk_av_delivery_brief_idempotency'),
              ('av_acceptance_profile_version', 'uk_av_acceptance_profile_version_no'),
              ('av_acceptance_profile_version', 'uk_av_acceptance_profile_idempotency'),
              ('av_agent_run', 'uk_av_agent_run_idempotency'),
              ('av_agent_run', 'idx_av_agent_run_owner_status'),
              ('av_agent_run', 'idx_av_agent_run_dispatch'),
              ('av_agent_run', 'idx_av_agent_run_waiting_task'),
              ('av_agent_run', 'idx_av_agent_run_contract')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 25
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_delivery_brief_version', 'ck_av_delivery_brief_version'),
              ('av_delivery_brief_version', 'ck_av_delivery_brief_parent'),
              ('av_delivery_brief_version', 'ck_av_delivery_brief_schema'),
              ('av_delivery_brief_version', 'ck_av_delivery_brief_type'),
              ('av_delivery_brief_version', 'ck_av_delivery_brief_content_size'),
              ('av_delivery_brief_version', 'ck_av_delivery_brief_digests'),
              ('av_delivery_brief_version', 'ck_av_delivery_brief_actor'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_version'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_parent'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_schema'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_policy'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_content_size'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_digests'),
              ('av_acceptance_profile_version', 'ck_av_acceptance_profile_actor'),
              ('av_agent_run', 'ck_av_agent_run_contract'),
              ('av_agent_run', 'ck_av_agent_run_schema'),
              ('av_agent_run', 'ck_av_agent_run_status'),
              ('av_agent_run', 'ck_av_agent_run_counters'),
              ('av_agent_run', 'ck_av_agent_run_request_digest'),
              ('av_agent_run', 'ck_av_agent_run_lease'),
              ('av_agent_run', 'ck_av_agent_run_waiting_task'),
              ('av_agent_run', 'ck_av_agent_run_result'),
              ('av_agent_run', 'ck_av_agent_run_terminal_time'),
              ('av_agent_run', 'ck_av_agent_run_result_size'),
              ('av_agent_run', 'ck_av_agent_run_actor')
          ))
);
SET @videoops_100_schema_assert_sql = IF(
    @videoops_100_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_100_schema_contract_failed'
);
PREPARE videoops_100_schema_assert_stmt FROM @videoops_100_schema_assert_sql;
EXECUTE videoops_100_schema_assert_stmt;
DEALLOCATE PREPARE videoops_100_schema_assert_stmt;
