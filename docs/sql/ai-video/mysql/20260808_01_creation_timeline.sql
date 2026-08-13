-- 创作第 6 步时间轴：个人用户归属、timeline-1 文档、统一任务与固定权限。
-- Expand-only：应用回滚不得删除本迁移创建的表或权限；结构修正只能新增后续迁移。

-- 所有目标表必须在第一条目标 DDL 前完成完整结构预检；任一既有同名对象漂移即终止。
DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_ddl_guard;
DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permissions;
DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permission_guard;

SET @creation_timeline_fingerprint_sql = '
SELECT /*+ SET_VAR(group_concat_max_len=1048576) */ SHA2(CONCAT(
    ''T|'', table_info.ENGINE, ''|'', table_info.TABLE_COLLATION,
    ''|C|'', (
        SELECT GROUP_CONCAT(CONCAT_WS(''~'', ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE,
                   IS_NULLABLE, COALESCE(COLUMN_DEFAULT, ''<NULL>''), EXTRA,
                   COALESCE(CHARACTER_SET_NAME, ''<NULL>''), COALESCE(COLLATION_NAME, ''<NULL>''))
               ORDER BY ORDINAL_POSITION SEPARATOR ''||'')
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = table_info.TABLE_NAME
    ),
    ''|I|'', (
        SELECT GROUP_CONCAT(CONCAT_WS(''~'', INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX,
                   COALESCE(COLUMN_NAME, ''<NULL>''), COALESCE(SUB_PART, ''<NULL>''))
               ORDER BY INDEX_NAME, SEQ_IN_INDEX SEPARATOR ''||'')
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = table_info.TABLE_NAME
    ),
    ''|K|'', (
        SELECT GROUP_CONCAT(CONCAT_WS(''~'', check_constraint.CONSTRAINT_NAME,
                   LOWER(REGEXP_REPLACE(check_constraint.CHECK_CLAUSE, ''[[:space:]`]+'', '''')))
               ORDER BY check_constraint.CONSTRAINT_NAME SEPARATOR ''||'')
        FROM information_schema.TABLE_CONSTRAINTS table_constraint
        JOIN information_schema.CHECK_CONSTRAINTS check_constraint
          ON check_constraint.CONSTRAINT_SCHEMA = table_constraint.CONSTRAINT_SCHEMA
         AND check_constraint.CONSTRAINT_NAME = table_constraint.CONSTRAINT_NAME
        WHERE table_constraint.CONSTRAINT_SCHEMA = DATABASE()
          AND table_constraint.TABLE_SCHEMA = DATABASE()
          AND table_constraint.TABLE_NAME = table_info.TABLE_NAME
          AND table_constraint.CONSTRAINT_TYPE = ''CHECK''
    )
), 256)
INTO @creation_timeline_actual_canonical
FROM information_schema.TABLES table_info
WHERE table_info.TABLE_SCHEMA = DATABASE()
  AND table_info.TABLE_TYPE = ''BASE TABLE''
  AND table_info.TABLE_NAME = ?';
PREPARE creation_timeline_fingerprint_stmt FROM @creation_timeline_fingerprint_sql;

SET @creation_timeline_global_preflight_ok = 1;

SET @creation_timeline_fingerprint_target = 'av_creation_asset';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '1fc3c8440bcd30f301dfd47d5d285d3af86032c99733b6e3c1e027fbe8d684ee'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_creation_project';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        'c2c796633f4c3963eac00f69a429ac06a08d5df23815782beb71f1b3856aec36'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_timeline_draft';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '6003cdc231e53514768495582043f13176b2fbf24e2856a334554df4e387730a'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_timeline_version';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '359564bfc15fbd6ca27e2e80e50c62dfd527f767cd704b9939b0b750fcf0e3fd'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_timeline_asset_ref';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '95e237b5921dd72c1d1a0dd13a19137f1c7c01ce38de8c06c29ff6db4cc5635d'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_timeline_write_receipt';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '89cd62f01bba4349f2f392c14254c4d7033b81c187b47c3ffa61090e223949f5'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_ai_task';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '923cf65c5150ee2b0940a283b2f033a9e1a5f1569cdca06d12b13531433e61ad'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_ai_task_execution';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '37129b56aad5e2eb9f749554641a99506aea0e269c5c19e8458275ec265aed4c'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_fingerprint_target = 'av_ai_task_attempt';
SET @creation_timeline_target_exists = (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
);
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_global_preflight_ok = @creation_timeline_global_preflight_ok AND IF(
    @creation_timeline_target_exists = 0, 1,
    @creation_timeline_target_exists = 1
    AND @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '5e6b9ccc4cc1811bf4dd420b8835878cbde71a8e0bd6a54cd2d25596a944a7c0'
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0
);

SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_global_preflight_ok = 1,
    'SELECT 1',
    'SELECT * FROM __creation_timeline_migration_error_global_preflight_failed'
);
DEALLOCATE PREPARE creation_timeline_fingerprint_stmt;
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;
PREPARE creation_timeline_fingerprint_stmt FROM @creation_timeline_fingerprint_sql;

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

SET @creation_timeline_fingerprint_target = 'av_creation_asset';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_creation_asset =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '1fc3c8440bcd30f301dfd47d5d285d3af86032c99733b6e3c1e027fbe8d684ee'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_creation_asset = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_asset_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_creation_project';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_creation_project =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        'c2c796633f4c3963eac00f69a429ac06a08d5df23815782beb71f1b3856aec36'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_creation_project = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_project_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_timeline_draft';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_timeline_draft =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '6003cdc231e53514768495582043f13176b2fbf24e2856a334554df4e387730a'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_timeline_draft = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_draft_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_timeline_version';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_timeline_version =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '359564bfc15fbd6ca27e2e80e50c62dfd527f767cd704b9939b0b750fcf0e3fd'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_timeline_version = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_version_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_timeline_asset_ref';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_timeline_asset_ref =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '95e237b5921dd72c1d1a0dd13a19137f1c7c01ce38de8c06c29ff6db4cc5635d'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_timeline_asset_ref = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_asset_ref_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_timeline_write_receipt';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_timeline_write_receipt =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '89cd62f01bba4349f2f392c14254c4d7033b81c187b47c3ffa61090e223949f5'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_timeline_write_receipt = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_receipt_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_ai_task';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_ai_task =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '923cf65c5150ee2b0940a283b2f033a9e1a5f1569cdca06d12b13531433e61ad'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_ai_task = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_task_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_ai_task_execution';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_ai_task_execution =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '37129b56aad5e2eb9f749554641a99506aea0e269c5c19e8458275ec265aed4c'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_ai_task_execution = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_execution_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

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

SET @creation_timeline_fingerprint_target = 'av_ai_task_attempt';
SET @creation_timeline_actual_canonical = NULL;
EXECUTE creation_timeline_fingerprint_stmt USING @creation_timeline_fingerprint_target;
SET @creation_timeline_post_create_av_ai_task_attempt =
    @creation_timeline_actual_canonical IS NOT NULL
    AND @creation_timeline_actual_canonical =
        '5e6b9ccc4cc1811bf4dd420b8835878cbde71a8e0bd6a54cd2d25596a944a7c0'
    AND (SELECT COUNT(*) FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND TABLE_TYPE = 'BASE TABLE') = 1
    AND (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = @creation_timeline_fingerprint_target
           AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0;
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_post_create_av_ai_task_attempt = 1,
    'SELECT 1', 'SELECT * FROM __ct_post_attempt_error'
);
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

-- MySQL DDL 会隐式提交；全部表结构验证通过后才允许写权限事实。
CREATE TEMPORARY TABLE tmp_creation_timeline_ddl_guard (
    guard_name VARCHAR(96) NOT NULL PRIMARY KEY,
    valid_value TINYINT NOT NULL
);

INSERT INTO tmp_creation_timeline_ddl_guard (guard_name, valid_value)
SELECT 'nine_tables', (
    SELECT COUNT(*) = 9
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_TYPE = 'BASE TABLE'
      AND TABLE_NAME IN (
          'av_creation_asset', 'av_creation_project', 'av_timeline_draft',
          'av_timeline_version', 'av_timeline_asset_ref', 'av_timeline_write_receipt',
          'av_ai_task', 'av_ai_task_execution', 'av_ai_task_attempt'
      )
);

INSERT INTO tmp_creation_timeline_ddl_guard (guard_name, valid_value)
SELECT 'no_forbidden_scope_columns', (
    SELECT COUNT(*) = 0
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'av_creation_asset', 'av_creation_project', 'av_timeline_draft',
          'av_timeline_version', 'av_timeline_asset_ref', 'av_timeline_write_receipt',
          'av_ai_task', 'av_ai_task_execution', 'av_ai_task_attempt'
      )
      AND COLUMN_NAME IN ('tenant_id', 'workspace_id')
);

INSERT INTO tmp_creation_timeline_ddl_guard (guard_name, valid_value)
SELECT 'no_foreign_keys', (
    SELECT COUNT(*) = 0
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME IN (
          'av_creation_asset', 'av_creation_project', 'av_timeline_draft',
          'av_timeline_version', 'av_timeline_asset_ref', 'av_timeline_write_receipt',
          'av_ai_task', 'av_ai_task_execution', 'av_ai_task_attempt'
      )
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

INSERT INTO tmp_creation_timeline_ddl_guard (guard_name, valid_value)
SELECT 'required_columns_and_types', (
    (SELECT COUNT(*) = 9 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'owner_user_id'
       AND TABLE_NAME IN (
           'av_creation_asset', 'av_creation_project', 'av_timeline_draft',
           'av_timeline_version', 'av_timeline_asset_ref', 'av_timeline_write_receipt',
           'av_ai_task', 'av_ai_task_execution', 'av_ai_task_attempt'
       ) AND DATA_TYPE = 'bigint')
    AND
    (SELECT COUNT(*) = 2 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'content_json'
       AND TABLE_NAME IN ('av_timeline_draft', 'av_timeline_version') AND DATA_TYPE = 'json')
    AND
    (SELECT COUNT(*) = 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_timeline_write_receipt'
       AND COLUMN_NAME = 'response_summary_json' AND DATA_TYPE = 'json')
    AND
    (SELECT COUNT(*) = 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_ai_task'
       AND COLUMN_NAME = 'request_payload_json' AND DATA_TYPE = 'json')
    AND
    (SELECT COUNT(*) = 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_ai_task'
       AND COLUMN_NAME = 'result_payload_json' AND DATA_TYPE = 'json' AND IS_NULLABLE = 'YES')
    AND
    (SELECT COUNT(*) = 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_timeline_write_receipt'
       AND COLUMN_NAME = 'operation_type' AND DATA_TYPE = 'varchar')
);

INSERT INTO tmp_creation_timeline_ddl_guard (guard_name, valid_value)
SELECT 'column_counts',
       (SELECT COUNT(*) = 25 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_creation_asset')
       AND (SELECT COUNT(*) = 24 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_creation_project')
       AND (SELECT COUNT(*) = 16 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_timeline_draft')
       AND (SELECT COUNT(*) = 20 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_timeline_version')
       AND (SELECT COUNT(*) = 17 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_timeline_asset_ref')
       AND (SELECT COUNT(*) = 17 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_timeline_write_receipt')
       AND (SELECT COUNT(*) = 32 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_ai_task')
       AND (SELECT COUNT(*) = 26 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_ai_task_execution')
       AND (SELECT COUNT(*) = 20 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_ai_task_attempt');

INSERT INTO tmp_creation_timeline_ddl_guard (guard_name, valid_value)
SELECT 'required_indexes', (
    SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 26
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND CONCAT(TABLE_NAME, ':', INDEX_NAME) IN (
          'av_creation_asset:uk_av_creation_asset_idempotency',
          'av_creation_asset:uk_av_creation_asset_source',
          'av_creation_asset:idx_av_creation_asset_owner_status',
          'av_creation_project:uk_av_creation_project_idempotency',
          'av_creation_project:idx_av_creation_project_owner_time',
          'av_creation_project:idx_av_creation_project_source',
          'av_creation_project:idx_av_creation_project_base_video',
          'av_creation_project:idx_av_creation_project_primary_audio',
          'av_creation_project:idx_av_creation_project_output',
          'av_timeline_draft:uk_av_timeline_draft_project',
          'av_timeline_version:uk_av_timeline_version_no',
          'av_timeline_version:uk_av_timeline_version_idempotency',
          'av_timeline_version:idx_av_timeline_version_history',
          'av_timeline_asset_ref:uk_av_timeline_asset_ref_projection',
          'av_timeline_asset_ref:idx_av_timeline_asset_ref_document',
          'av_timeline_asset_ref:idx_av_timeline_asset_ref_asset',
          'av_timeline_write_receipt:uk_av_timeline_write_receipt_idempotency',
          'av_ai_task:uk_av_ai_task_idempotency',
          'av_ai_task:idx_av_ai_task_owner_status',
          'av_ai_task:idx_av_ai_task_resource',
          'av_ai_task:idx_av_ai_task_result_asset',
          'av_ai_task_execution:uk_av_ai_task_execution_no',
          'av_ai_task_execution:idx_av_ai_task_execution_dispatch',
          'av_ai_task_execution:idx_av_ai_task_execution_recovery',
          'av_ai_task_execution:idx_av_ai_task_execution_result_asset',
          'av_ai_task_attempt:uk_av_ai_task_attempt_no'
      )
);

SET @creation_timeline_ddl_contract_ok = (
    SELECT COUNT(*) = 6 AND MIN(valid_value) = 1
    FROM tmp_creation_timeline_ddl_guard
);
SET @creation_timeline_ddl_assert_sql = IF(
    @creation_timeline_ddl_contract_ok,
    'SELECT 1',
    'SELECT * FROM __creation_timeline_migration_error_ddl_contract_mismatch'
);
DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_ddl_guard;
DEALLOCATE PREPARE creation_timeline_fingerprint_stmt;
PREPARE creation_timeline_ddl_assert_stmt FROM @creation_timeline_ddl_assert_sql;
EXECUTE creation_timeline_ddl_assert_stmt;
DEALLOCATE PREPARE creation_timeline_ddl_assert_stmt;

CREATE TEMPORARY TABLE tmp_creation_timeline_permissions (
    permission_id BIGINT NOT NULL PRIMARY KEY,
    binding_id BIGINT NOT NULL UNIQUE,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL
);

INSERT INTO tmp_creation_timeline_permissions
    (permission_id, binding_id, permission_code, permission_name, resource_type, action)
VALUES
    (1000025, 1000225, 'aivideo:creation:query', '创作项目查看', 'creation', 'query'),
    (1000026, 1000226, 'aivideo:creation:edit', '创作项目编辑', 'creation', 'edit'),
    (1000027, 1000227, 'aivideo:creation:generate', '创作内容生成', 'creation', 'generate'),
    (1000028, 1000228, 'aivideo:creation-asset:query', '创作素材查看', 'creation-asset', 'query'),
    (1000029, 1000229, 'aivideo:creation-asset:upload', '创作素材上传', 'creation-asset', 'upload'),
    (1000030, 1000230, 'aivideo:creation-asset:delete', '创作素材删除', 'creation-asset', 'delete'),
    (1000031, 1000231, 'aivideo:task:retry', '生成任务重试', 'task', 'retry');

CREATE TEMPORARY TABLE tmp_creation_timeline_permission_guard (
    guard_name VARCHAR(96) NOT NULL PRIMARY KEY,
    valid_value TINYINT NOT NULL
);

INSERT INTO tmp_creation_timeline_permission_guard (guard_name, valid_value)
SELECT 'personal_creator_role', (
    SELECT COUNT(*) = 1
    FROM app_role
    WHERE role_id = 1000101
      AND role_code = 'personal_creator'
      AND scope_type = 'personal'
      AND status = 'active'
      AND del_flag = '0'
);

INSERT INTO tmp_creation_timeline_permission_guard (guard_name, valid_value)
SELECT 'permission_precondition', NOT EXISTS (
    SELECT 1
    FROM tmp_creation_timeline_permissions expected
    JOIN app_permission actual
      ON actual.permission_id = expected.permission_id
      OR actual.permission_code = expected.permission_code
    WHERE actual.permission_id <> expected.permission_id
       OR actual.permission_code <> expected.permission_code
       OR actual.permission_name <> expected.permission_name
       OR actual.resource_type <> expected.resource_type
       OR actual.action <> expected.action
       OR actual.permission_revision <> 1
       OR actual.status <> 'active'
       OR actual.created_by_type <> 'sys_user'
       OR actual.created_by_id <> 1761100000000000001
       OR actual.updated_by_type <> 'sys_user'
       OR actual.updated_by_id <> 1761100000000000001
);

INSERT INTO tmp_creation_timeline_permission_guard (guard_name, valid_value)
SELECT 'binding_precondition', NOT EXISTS (
    SELECT 1
    FROM tmp_creation_timeline_permissions expected
    JOIN app_role_permission actual
      ON actual.id = expected.binding_id
      OR (actual.role_id = 1000101 AND actual.permission_id = expected.permission_id)
    WHERE actual.id <> expected.binding_id
       OR actual.role_id <> 1000101
       OR actual.permission_id <> expected.permission_id
       OR actual.status <> 'active'
       OR actual.created_by_type <> 'sys_user'
       OR actual.created_by_id <> 1761100000000000001
       OR actual.updated_by_type <> 'sys_user'
       OR actual.updated_by_id <> 1761100000000000001
);

SET @creation_timeline_permission_pre_ok = (
    SELECT COUNT(*) = 3 AND MIN(valid_value) = 1
    FROM tmp_creation_timeline_permission_guard
);
DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permission_guard;
DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permissions;

SET @creation_timeline_permission_pre_assert_sql = IF(
    @creation_timeline_permission_pre_ok = 1,
    'SELECT 1',
    'SELECT * FROM __creation_timeline_migration_error_permission_precondition_failed'
);
PREPARE creation_timeline_permission_pre_assert_stmt
    FROM @creation_timeline_permission_pre_assert_sql;
EXECUTE creation_timeline_permission_pre_assert_stmt;
DEALLOCATE PREPARE creation_timeline_permission_pre_assert_stmt;

CREATE TEMPORARY TABLE tmp_creation_timeline_permissions (
    permission_id BIGINT NOT NULL PRIMARY KEY,
    binding_id BIGINT NOT NULL UNIQUE,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(64) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL
);

INSERT INTO tmp_creation_timeline_permissions
    (permission_id, binding_id, permission_code, permission_name, resource_type, action)
VALUES
    (1000025, 1000225, 'aivideo:creation:query', '创作项目查看', 'creation', 'query'),
    (1000026, 1000226, 'aivideo:creation:edit', '创作项目编辑', 'creation', 'edit'),
    (1000027, 1000227, 'aivideo:creation:generate', '创作内容生成', 'creation', 'generate'),
    (1000028, 1000228, 'aivideo:creation-asset:query', '创作素材查看', 'creation-asset', 'query'),
    (1000029, 1000229, 'aivideo:creation-asset:upload', '创作素材上传', 'creation-asset', 'upload'),
    (1000030, 1000230, 'aivideo:creation-asset:delete', '创作素材删除', 'creation-asset', 'delete'),
    (1000031, 1000231, 'aivideo:task:retry', '生成任务重试', 'task', 'retry');

START TRANSACTION;

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
    created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
)
SELECT expected.permission_id, expected.permission_code, expected.permission_name,
       expected.resource_type, expected.action, 1, 'active',
       'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()
FROM tmp_creation_timeline_permissions expected
WHERE NOT EXISTS (
    SELECT 1
    FROM app_permission actual
    WHERE actual.permission_id = expected.permission_id
      AND actual.permission_code = expected.permission_code
      AND actual.permission_name = expected.permission_name
      AND actual.resource_type = expected.resource_type
      AND actual.action = expected.action
      AND actual.permission_revision = 1
      AND actual.status = 'active'
      AND actual.created_by_type = 'sys_user'
      AND actual.created_by_id = 1761100000000000001
      AND actual.updated_by_type = 'sys_user'
      AND actual.updated_by_id = 1761100000000000001
);
SET @creation_timeline_permissions_inserted = ROW_COUNT();

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
)
SELECT expected.binding_id, 1000101, expected.permission_id, 'active',
       'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW()
FROM tmp_creation_timeline_permissions expected
WHERE NOT EXISTS (
    SELECT 1
    FROM app_role_permission actual
    WHERE actual.id = expected.binding_id
      AND actual.role_id = 1000101
      AND actual.permission_id = expected.permission_id
      AND actual.status = 'active'
      AND actual.created_by_type = 'sys_user'
      AND actual.created_by_id = 1761100000000000001
      AND actual.updated_by_type = 'sys_user'
      AND actual.updated_by_id = 1761100000000000001
);
SET @creation_timeline_bindings_inserted = ROW_COUNT();

UPDATE app_role
SET role_revision = role_revision + 1,
    updated_by_type = 'sys_user',
    updated_by_id = 1761100000000000001,
    update_time = NOW()
WHERE (@creation_timeline_permissions_inserted > 0 OR @creation_timeline_bindings_inserted > 0)
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
WHERE (@creation_timeline_permissions_inserted > 0 OR @creation_timeline_bindings_inserted > 0)
  AND app_user.status = 'active'
  AND app_user.del_flag = '0'
  AND app_user_role.status = 'active'
  AND (app_user_role.valid_from IS NULL OR app_user_role.valid_from <= NOW())
  AND (app_user_role.valid_until IS NULL OR app_user_role.valid_until > NOW());

SET @creation_timeline_permission_rows_ok = (
    SELECT COUNT(*) = 7
    FROM tmp_creation_timeline_permissions expected
    JOIN app_permission actual
      ON actual.permission_id = expected.permission_id
     AND actual.permission_code = expected.permission_code
     AND actual.permission_name = expected.permission_name
     AND actual.resource_type = expected.resource_type
     AND actual.action = expected.action
     AND actual.permission_revision = 1
     AND actual.status = 'active'
     AND actual.created_by_type = 'sys_user'
     AND actual.created_by_id = 1761100000000000001
     AND actual.updated_by_type = 'sys_user'
     AND actual.updated_by_id = 1761100000000000001
);

SET @creation_timeline_binding_rows_ok = (
    SELECT COUNT(*) = 7
    FROM tmp_creation_timeline_permissions expected
    JOIN app_role_permission actual
      ON actual.id = expected.binding_id
     AND actual.role_id = 1000101
     AND actual.permission_id = expected.permission_id
     AND actual.status = 'active'
     AND actual.created_by_type = 'sys_user'
     AND actual.created_by_id = 1761100000000000001
     AND actual.updated_by_type = 'sys_user'
     AND actual.updated_by_id = 1761100000000000001
);

SET @creation_timeline_permission_post_ok =
    @creation_timeline_permission_rows_ok AND @creation_timeline_binding_rows_ok;

SET @creation_timeline_permission_finish_sql = IF(
    @creation_timeline_permission_post_ok = 1,
    'COMMIT',
    'ROLLBACK'
);
PREPARE creation_timeline_permission_finish_stmt
    FROM @creation_timeline_permission_finish_sql;
EXECUTE creation_timeline_permission_finish_stmt;
DEALLOCATE PREPARE creation_timeline_permission_finish_stmt;

DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permissions;

SET @creation_timeline_permission_post_assert_sql = IF(
    @creation_timeline_permission_post_ok = 1,
    'SELECT 1',
    'SELECT * FROM __creation_timeline_migration_error_permission_postcondition_failed'
);
PREPARE creation_timeline_permission_post_assert_stmt
    FROM @creation_timeline_permission_post_assert_sql;
EXECUTE creation_timeline_permission_post_assert_stmt;
DEALLOCATE PREPARE creation_timeline_permission_post_assert_stmt;

-- 仅供紧急人工处置：必须先逐行核对 1000225..1000231 与 1000025..1000031 的完整映射，
-- 同步恢复 personal_creator 角色及受影响 app_user 的修订和审计字段；不得进入自动回滚流程。
