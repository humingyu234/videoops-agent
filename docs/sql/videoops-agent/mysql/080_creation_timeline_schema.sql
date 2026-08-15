-- VideoOps Agent T1 creation timeline schema.
-- Derived from: 20260808_01_creation_timeline.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_080_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_080_target_assert_sql = IF(
    @videoops_080_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_080_wrong_target_database'
);
PREPARE videoops_080_target_assert_stmt FROM @videoops_080_target_assert_sql;
EXECUTE videoops_080_target_assert_stmt;
DEALLOCATE PREPARE videoops_080_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_creation_asset (
    asset_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    asset_type VARCHAR(16) NOT NULL,
    usage_origin VARCHAR(32) NOT NULL,
    source_ref_id BIGINT NULL,
    asset_status VARCHAR(16) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    mime_type VARCHAR(127) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    duration_ms BIGINT NULL,
    width INT NULL,
    height INT NULL,
    has_video_stream TINYINT NOT NULL DEFAULT 0,
    has_audio_stream TINYINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_av_creation_asset_idempotency (owner_user_id, idempotency_key),
    UNIQUE KEY uk_av_creation_asset_source (owner_user_id, usage_origin, source_ref_id),
    KEY idx_av_creation_asset_owner_status (owner_user_id, del_flag, asset_status, update_time),
    CONSTRAINT ck_av_creation_asset_type CHECK (asset_type IN ('video', 'image', 'audio')),
    CONSTRAINT ck_av_creation_asset_origin CHECK (
        usage_origin IN ('upload', 'digital_human_output', 'timeline_render_output')
    ),
    CONSTRAINT ck_av_creation_asset_status CHECK (asset_status IN ('pending', 'ready', 'failed')),
    CONSTRAINT ck_av_creation_asset_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_av_creation_asset_duration CHECK (duration_ms IS NULL OR duration_ms > 0),
    CONSTRAINT ck_av_creation_asset_dimensions CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    ),
    CONSTRAINT ck_av_creation_asset_stream_flags CHECK (
        has_video_stream IN (0, 1) AND has_audio_stream IN (0, 1)
    ),
    CONSTRAINT ck_av_creation_asset_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id),
    CONSTRAINT ck_av_creation_asset_deleted CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='创作时间轴通用素材';

CREATE TABLE IF NOT EXISTS av_creation_project (
    project_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    project_title VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref_id BIGINT NOT NULL,
    base_video_asset_id BIGINT NOT NULL,
    primary_audio_asset_id BIGINT NULL,
    script_text_snapshot LONGTEXT NOT NULL,
    canvas_width INT NOT NULL,
    canvas_height INT NOT NULL,
    frame_rate INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    project_status VARCHAR(16) NOT NULL,
    current_output_asset_id BIGINT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (project_id),
    UNIQUE KEY uk_av_creation_project_idempotency (owner_user_id, idempotency_key),
    KEY idx_av_creation_project_owner_time (owner_user_id, del_flag, update_time),
    KEY idx_av_creation_project_source (owner_user_id, source_type, source_ref_id),
    KEY idx_av_creation_project_base_video (owner_user_id, base_video_asset_id, del_flag),
    KEY idx_av_creation_project_primary_audio (owner_user_id, primary_audio_asset_id, del_flag),
    KEY idx_av_creation_project_output (owner_user_id, current_output_asset_id, del_flag),
    CONSTRAINT ck_av_creation_project_source CHECK (source_type = 'digital_human_job'),
    CONSTRAINT ck_av_creation_project_canvas CHECK (
        canvas_width = 1080 AND canvas_height = 1920 AND frame_rate = 30
    ),
    CONSTRAINT ck_av_creation_project_duration CHECK (duration_ms BETWEEN 1 AND 120000),
    CONSTRAINT ck_av_creation_project_status CHECK (
        project_status IN ('editing', 'rendering', 'ready', 'archived')
    ),
    CONSTRAINT ck_av_creation_project_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id),
    CONSTRAINT ck_av_creation_project_deleted CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间轴创作项目';

CREATE TABLE IF NOT EXISTS av_timeline_draft (
    timeline_draft_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    content_json JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    duration_ms BIGINT NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag CHAR(1) NOT NULL DEFAULT '0',
    PRIMARY KEY (timeline_draft_id),
    UNIQUE KEY uk_av_timeline_draft_project (owner_user_id, project_id),
    CONSTRAINT ck_av_timeline_draft_revision CHECK (revision > 0),
    CONSTRAINT ck_av_timeline_draft_schema CHECK (schema_version = 'timeline-1'),
    CONSTRAINT ck_av_timeline_draft_duration CHECK (duration_ms BETWEEN 1 AND 120000),
    CONSTRAINT ck_av_timeline_draft_content_size CHECK (
        OCTET_LENGTH(CAST(content_json AS CHAR)) <= 1048576
    ),
    CONSTRAINT ck_av_timeline_draft_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id),
    CONSTRAINT ck_av_timeline_draft_deleted CHECK (del_flag IN ('0', '1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间轴当前草稿';

CREATE TABLE IF NOT EXISTS av_timeline_version (
    timeline_version_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    version_no BIGINT NOT NULL,
    source_draft_revision BIGINT NOT NULL,
    version_reason VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    content_json JSON NOT NULL,
    content_hash CHAR(64) NOT NULL,
    duration_ms BIGINT NOT NULL,
    source_version_id BIGINT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (timeline_version_id),
    UNIQUE KEY uk_av_timeline_version_no (owner_user_id, project_id, version_no),
    UNIQUE KEY uk_av_timeline_version_idempotency (owner_user_id, project_id, idempotency_key),
    KEY idx_av_timeline_version_history (owner_user_id, project_id, create_time),
    CONSTRAINT ck_av_timeline_version_no CHECK (version_no > 0 AND source_draft_revision > 0),
    CONSTRAINT ck_av_timeline_version_reason CHECK (
        version_reason IN ('manual_save', 'restored', 'render_input', 'conflict_copy')
    ),
    CONSTRAINT ck_av_timeline_version_schema CHECK (schema_version = 'timeline-1'),
    CONSTRAINT ck_av_timeline_version_duration CHECK (duration_ms BETWEEN 1 AND 120000),
    CONSTRAINT ck_av_timeline_version_content_size CHECK (
        OCTET_LENGTH(CAST(content_json AS CHAR)) <= 1048576
    ),
    CONSTRAINT ck_av_timeline_version_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变时间轴版本';

CREATE TABLE IF NOT EXISTS av_timeline_asset_ref (
    timeline_asset_ref_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    document_type VARCHAR(16) NOT NULL,
    document_id BIGINT NOT NULL,
    element_id VARCHAR(64) NOT NULL,
    asset_id BIGINT NOT NULL,
    usage_type VARCHAR(32) NOT NULL,
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (timeline_asset_ref_id),
    UNIQUE KEY uk_av_timeline_asset_ref_projection (
        owner_user_id, document_type, document_id, element_id, asset_id, usage_type
    ),
    KEY idx_av_timeline_asset_ref_document (owner_user_id, document_type, document_id),
    KEY idx_av_timeline_asset_ref_asset (owner_user_id, asset_id, project_id),
    CONSTRAINT ck_av_timeline_asset_ref_document CHECK (document_type IN ('draft', 'version')),
    CONSTRAINT ck_av_timeline_asset_ref_usage CHECK (
        usage_type IN ('base_video', 'primary_audio', 'image', 'pip_video', 'background_music', 'sound_effect')
    ),
    CONSTRAINT ck_av_timeline_asset_ref_time CHECK (start_ms >= 0 AND end_ms > start_ms),
    CONSTRAINT ck_av_timeline_asset_ref_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时间轴素材引用投影';

CREATE TABLE IF NOT EXISTS av_timeline_write_receipt (
    timeline_write_receipt_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    operation_type VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    expected_revision BIGINT NOT NULL,
    result_revision BIGINT NULL,
    result_version_id BIGINT NULL,
    response_summary_json JSON NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (timeline_write_receipt_id),
    UNIQUE KEY uk_av_timeline_write_receipt_idempotency (owner_user_id, project_id, idempotency_key),
    CONSTRAINT ck_av_timeline_write_receipt_operation CHECK (
        operation_type IN ('draft_save', 'manual_version', 'version_restore', 'conflict_version')
    ),
    CONSTRAINT ck_av_timeline_write_receipt_revision CHECK (
        expected_revision > 0 AND (result_revision IS NULL OR result_revision > 0)
    ),
    CONSTRAINT ck_av_timeline_write_receipt_summary_size CHECK (
        OCTET_LENGTH(CAST(response_summary_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_timeline_write_receipt_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变时间轴写回执';

CREATE TABLE IF NOT EXISTS av_ai_task (
    task_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    input_version_id BIGINT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    request_schema_version VARCHAR(32) NOT NULL,
    request_payload_json JSON NOT NULL,
    task_status VARCHAR(16) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    cancel_requested TINYINT NOT NULL DEFAULT 0,
    active_execution_id BIGINT NULL,
    result_asset_id BIGINT NULL,
    result_schema_version VARCHAR(32) NULL,
    result_payload_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    quota_policy_version VARCHAR(32) NOT NULL,
    estimated_usage BIGINT NOT NULL DEFAULT 0,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_av_ai_task_idempotency (owner_user_id, idempotency_key),
    KEY idx_av_ai_task_owner_status (owner_user_id, task_status, create_time),
    KEY idx_av_ai_task_resource (owner_user_id, resource_type, resource_id, create_time),
    KEY idx_av_ai_task_result_asset (owner_user_id, result_asset_id),
    CONSTRAINT ck_av_ai_task_name CHECK (
        task_type REGEXP '^[a-z][a-z0-9_]{1,63}$'
        AND resource_type REGEXP '^[a-z][a-z0-9_]{1,63}$'
    ),
    CONSTRAINT ck_av_ai_task_status CHECK (
        task_status IN ('pending', 'queued', 'running', 'success', 'failed', 'cancelled')
    ),
    CONSTRAINT ck_av_ai_task_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_av_ai_task_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_av_ai_task_cancel CHECK (cancel_requested IN (0, 1)),
    CONSTRAINT ck_av_ai_task_request_size CHECK (
        OCTET_LENGTH(CAST(request_payload_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_ai_task_result_payload_size CHECK (
        result_payload_json IS NULL OR OCTET_LENGTH(CAST(result_payload_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_ai_task_free_policy CHECK (
        quota_policy_version = 'timeline-free-1' AND estimated_usage = 0
    ),
    CONSTRAINT ck_av_ai_task_success_result CHECK (
        (
            task_status <> 'success'
            AND result_asset_id IS NULL
            AND result_schema_version IS NULL
            AND result_payload_json IS NULL
        )
        OR
        (
            task_status = 'success'
            AND (
                (
                    task_type = 'timeline_render'
                    AND result_asset_id IS NOT NULL
                    AND result_schema_version IS NULL
                    AND result_payload_json IS NULL
                )
                OR
                (
                    task_type IN (
                        'timeline_image_prompt_generate',
                        'timeline_fancy_text_suggest',
                        'timeline_subtitle_align'
                    )
                    AND result_asset_id IS NULL
                    AND result_schema_version IS NOT NULL
                    AND result_payload_json IS NOT NULL
                )
            )
        )
    ),
    CONSTRAINT ck_av_ai_task_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一 AI 根任务';

CREATE TABLE IF NOT EXISTS av_ai_task_execution (
    task_execution_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    execution_no BIGINT NOT NULL,
    execution_status VARCHAR(16) NOT NULL,
    stage VARCHAR(64) NOT NULL,
    progress_percent INT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    next_run_at DATETIME NULL,
    lease_owner VARCHAR(128) NULL,
    lease_token VARCHAR(128) NULL,
    lease_expires_at DATETIME NULL,
    cancel_requested_snapshot TINYINT NOT NULL DEFAULT 0,
    input_version_id BIGINT NULL,
    output_config_digest CHAR(64) NULL,
    result_asset_id BIGINT NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_execution_id),
    UNIQUE KEY uk_av_ai_task_execution_no (owner_user_id, task_id, execution_no),
    KEY idx_av_ai_task_execution_dispatch (execution_status, next_run_at),
    KEY idx_av_ai_task_execution_recovery (execution_status, lease_expires_at),
    KEY idx_av_ai_task_execution_result_asset (owner_user_id, result_asset_id),
    CONSTRAINT ck_av_ai_task_execution_no CHECK (execution_no > 0),
    CONSTRAINT ck_av_ai_task_execution_status CHECK (
        execution_status IN ('queued', 'running', 'success', 'failed', 'cancelled')
    ),
    CONSTRAINT ck_av_ai_task_execution_progress CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_av_ai_task_execution_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_av_ai_task_execution_cancel CHECK (cancel_requested_snapshot IN (0, 1)),
    CONSTRAINT ck_av_ai_task_execution_lease CHECK (
        (lease_token IS NULL AND lease_expires_at IS NULL)
        OR (lease_token IS NOT NULL AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_av_ai_task_execution_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一 AI 任务执行';

CREATE TABLE IF NOT EXISTS av_ai_task_attempt (
    task_attempt_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_execution_id BIGINT NOT NULL,
    attempt_no BIGINT NOT NULL,
    attempt_status VARCHAR(16) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    worker_id VARCHAR(128) NOT NULL,
    lease_token_digest CHAR(64) NOT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    exit_category VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (task_attempt_id),
    UNIQUE KEY uk_av_ai_task_attempt_no (owner_user_id, task_execution_id, attempt_no),
    CONSTRAINT ck_av_ai_task_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_av_ai_task_attempt_status CHECK (
        attempt_status IN ('running', 'success', 'failed', 'cancelled', 'abandoned')
    ),
    CONSTRAINT ck_av_ai_task_attempt_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_av_ai_task_attempt_terminal_time CHECK (
        (attempt_status = 'running' AND finished_at IS NULL)
        OR (attempt_status <> 'running' AND finished_at IS NOT NULL)
    ),
    CONSTRAINT ck_av_ai_task_attempt_actor CHECK (actor_type = 'app_user' AND actor_id = owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一 AI 任务真实调用尝试';

SET @videoops_080_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 9
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_creation_asset', 'av_creation_project', 'av_timeline_draft', 'av_timeline_version', 'av_timeline_asset_ref', 'av_timeline_write_receipt', 'av_ai_task', 'av_ai_task_execution', 'av_ai_task_attempt'))
    AND (SELECT COUNT(*) = 12
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_creation_asset', 'asset_id'),
              ('av_creation_asset', 'owner_user_id'),
              ('av_creation_project', 'project_id'),
              ('av_creation_project', 'owner_user_id'),
              ('av_timeline_draft', 'content_json'),
              ('av_timeline_version', 'content_json'),
              ('av_timeline_asset_ref', 'document_type'),
              ('av_timeline_write_receipt', 'response_summary_json'),
              ('av_ai_task', 'request_payload_json'),
              ('av_ai_task', 'result_payload_json'),
              ('av_ai_task_execution', 'execution_status'),
              ('av_ai_task_attempt', 'attempt_status')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 26
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_creation_asset', 'uk_av_creation_asset_idempotency'),
              ('av_creation_asset', 'uk_av_creation_asset_source'),
              ('av_creation_asset', 'idx_av_creation_asset_owner_status'),
              ('av_creation_project', 'uk_av_creation_project_idempotency'),
              ('av_creation_project', 'idx_av_creation_project_owner_time'),
              ('av_creation_project', 'idx_av_creation_project_source'),
              ('av_creation_project', 'idx_av_creation_project_base_video'),
              ('av_creation_project', 'idx_av_creation_project_primary_audio'),
              ('av_creation_project', 'idx_av_creation_project_output'),
              ('av_timeline_draft', 'uk_av_timeline_draft_project'),
              ('av_timeline_version', 'uk_av_timeline_version_no'),
              ('av_timeline_version', 'uk_av_timeline_version_idempotency'),
              ('av_timeline_version', 'idx_av_timeline_version_history'),
              ('av_timeline_asset_ref', 'uk_av_timeline_asset_ref_projection'),
              ('av_timeline_asset_ref', 'idx_av_timeline_asset_ref_document'),
              ('av_timeline_asset_ref', 'idx_av_timeline_asset_ref_asset'),
              ('av_timeline_write_receipt', 'uk_av_timeline_write_receipt_idempotency'),
              ('av_ai_task', 'uk_av_ai_task_idempotency'),
              ('av_ai_task', 'idx_av_ai_task_owner_status'),
              ('av_ai_task', 'idx_av_ai_task_resource'),
              ('av_ai_task', 'idx_av_ai_task_result_asset'),
              ('av_ai_task_execution', 'uk_av_ai_task_execution_no'),
              ('av_ai_task_execution', 'idx_av_ai_task_execution_dispatch'),
              ('av_ai_task_execution', 'idx_av_ai_task_execution_recovery'),
              ('av_ai_task_execution', 'idx_av_ai_task_execution_result_asset'),
              ('av_ai_task_attempt', 'uk_av_ai_task_attempt_no')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 57
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_creation_asset', 'ck_av_creation_asset_type'),
              ('av_creation_asset', 'ck_av_creation_asset_origin'),
              ('av_creation_asset', 'ck_av_creation_asset_status'),
              ('av_creation_asset', 'ck_av_creation_asset_size'),
              ('av_creation_asset', 'ck_av_creation_asset_duration'),
              ('av_creation_asset', 'ck_av_creation_asset_dimensions'),
              ('av_creation_asset', 'ck_av_creation_asset_stream_flags'),
              ('av_creation_asset', 'ck_av_creation_asset_actor'),
              ('av_creation_asset', 'ck_av_creation_asset_deleted'),
              ('av_creation_project', 'ck_av_creation_project_source'),
              ('av_creation_project', 'ck_av_creation_project_canvas'),
              ('av_creation_project', 'ck_av_creation_project_duration'),
              ('av_creation_project', 'ck_av_creation_project_status'),
              ('av_creation_project', 'ck_av_creation_project_actor'),
              ('av_creation_project', 'ck_av_creation_project_deleted'),
              ('av_timeline_draft', 'ck_av_timeline_draft_revision'),
              ('av_timeline_draft', 'ck_av_timeline_draft_schema'),
              ('av_timeline_draft', 'ck_av_timeline_draft_duration'),
              ('av_timeline_draft', 'ck_av_timeline_draft_content_size'),
              ('av_timeline_draft', 'ck_av_timeline_draft_actor'),
              ('av_timeline_draft', 'ck_av_timeline_draft_deleted'),
              ('av_timeline_version', 'ck_av_timeline_version_no'),
              ('av_timeline_version', 'ck_av_timeline_version_reason'),
              ('av_timeline_version', 'ck_av_timeline_version_schema'),
              ('av_timeline_version', 'ck_av_timeline_version_duration'),
              ('av_timeline_version', 'ck_av_timeline_version_content_size'),
              ('av_timeline_version', 'ck_av_timeline_version_actor'),
              ('av_timeline_asset_ref', 'ck_av_timeline_asset_ref_document'),
              ('av_timeline_asset_ref', 'ck_av_timeline_asset_ref_usage'),
              ('av_timeline_asset_ref', 'ck_av_timeline_asset_ref_time'),
              ('av_timeline_asset_ref', 'ck_av_timeline_asset_ref_actor'),
              ('av_timeline_write_receipt', 'ck_av_timeline_write_receipt_operation'),
              ('av_timeline_write_receipt', 'ck_av_timeline_write_receipt_revision'),
              ('av_timeline_write_receipt', 'ck_av_timeline_write_receipt_summary_size'),
              ('av_timeline_write_receipt', 'ck_av_timeline_write_receipt_actor'),
              ('av_ai_task', 'ck_av_ai_task_name'),
              ('av_ai_task', 'ck_av_ai_task_status'),
              ('av_ai_task', 'ck_av_ai_task_progress'),
              ('av_ai_task', 'ck_av_ai_task_row_version'),
              ('av_ai_task', 'ck_av_ai_task_cancel'),
              ('av_ai_task', 'ck_av_ai_task_request_size'),
              ('av_ai_task', 'ck_av_ai_task_result_payload_size'),
              ('av_ai_task', 'ck_av_ai_task_free_policy'),
              ('av_ai_task', 'ck_av_ai_task_success_result'),
              ('av_ai_task', 'ck_av_ai_task_actor'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_no'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_status'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_progress'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_row_version'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_cancel'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_lease'),
              ('av_ai_task_execution', 'ck_av_ai_task_execution_actor'),
              ('av_ai_task_attempt', 'ck_av_ai_task_attempt_no'),
              ('av_ai_task_attempt', 'ck_av_ai_task_attempt_status'),
              ('av_ai_task_attempt', 'ck_av_ai_task_attempt_row_version'),
              ('av_ai_task_attempt', 'ck_av_ai_task_attempt_terminal_time'),
              ('av_ai_task_attempt', 'ck_av_ai_task_attempt_actor')
          ))
);
SET @videoops_080_schema_assert_sql = IF(
    @videoops_080_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_080_schema_contract_failed'
);
PREPARE videoops_080_schema_assert_stmt FROM @videoops_080_schema_assert_sql;
EXECUTE videoops_080_schema_assert_stmt;
DEALLOCATE PREPARE videoops_080_schema_assert_stmt;
