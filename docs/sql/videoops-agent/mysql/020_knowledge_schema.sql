-- VideoOps Agent T1 knowledge schema.
-- Derived from: 20260803_01_p1_knowledge_lite.sql.
-- Schema only; no seed rows are written.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_020_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_020_target_assert_sql = IF(
    @videoops_020_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_020_wrong_target_database'
);
PREPARE videoops_020_target_assert_stmt FROM @videoops_020_target_assert_sql;
EXECUTE videoops_020_target_assert_stmt;
DEALLOCATE PREPARE videoops_020_target_assert_stmt;

CREATE TABLE IF NOT EXISTS av_knowledge_item (
    knowledge_item_id BIGINT NOT NULL,
    domain_code VARCHAR(64) NOT NULL,
    knowledge_type_code VARCHAR(64) NOT NULL,
    stable_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    summary VARCHAR(500) DEFAULT NULL,
    tags_json JSON NOT NULL,
    current_published_version_id BIGINT DEFAULT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (knowledge_item_id),
    UNIQUE KEY uk_av_knowledge_item_stable_code (stable_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS av_knowledge_version (
    knowledge_version_id BIGINT NOT NULL,
    knowledge_item_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    structure_json JSON NOT NULL,
    source_summary VARCHAR(500) DEFAULT NULL,
    reviewed_by BIGINT DEFAULT NULL,
    reviewed_at DATETIME DEFAULT NULL,
    published_by BIGINT DEFAULT NULL,
    published_at DATETIME DEFAULT NULL,
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (knowledge_version_id),
    UNIQUE KEY uk_av_knowledge_version_item_no (knowledge_item_id, version_no),
    CONSTRAINT fk_av_knowledge_version_item
        FOREIGN KEY (knowledge_item_id) REFERENCES av_knowledge_item (knowledge_item_id),
    CONSTRAINT chk_av_knowledge_version_version_no CHECK (version_no > 0),
    CONSTRAINT chk_av_knowledge_version_status
        CHECK (status IN ('draft', 'reviewing', 'published', 'retired'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS av_knowledge_binding (
    knowledge_binding_id BIGINT NOT NULL,
    binding_group_code VARCHAR(128) NOT NULL,
    version_no INT NOT NULL,
    knowledge_item_id BIGINT NOT NULL,
    knowledge_version_id BIGINT NOT NULL,
    industry_code VARCHAR(64) NOT NULL,
    purpose_code VARCHAR(64) NOT NULL,
    video_type_code VARCHAR(64) NOT NULL,
    angle_codes_json JSON NOT NULL,
    angle_priorities_json JSON NOT NULL,
    min_duration_seconds INT DEFAULT NULL,
    max_duration_seconds INT DEFAULT NULL,
    priority INT NOT NULL,
    required_flag TINYINT(1) NOT NULL,
    required_slot_codes_json JSON NOT NULL,
    audience_tag_codes_json JSON NOT NULL,
    exclusion_conditions_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (knowledge_binding_id),
    UNIQUE KEY uk_av_knowledge_binding_group_no (binding_group_code, version_no),
    KEY idx_av_knowledge_binding_route
        (status, industry_code, purpose_code, video_type_code,
         min_duration_seconds, max_duration_seconds, priority),
    CONSTRAINT fk_av_knowledge_binding_item
        FOREIGN KEY (knowledge_item_id) REFERENCES av_knowledge_item (knowledge_item_id),
    CONSTRAINT fk_av_knowledge_binding_version
        FOREIGN KEY (knowledge_version_id) REFERENCES av_knowledge_version (knowledge_version_id),
    CONSTRAINT chk_av_knowledge_binding_version_no CHECK (version_no > 0),
    CONSTRAINT chk_av_knowledge_binding_priority CHECK (priority BETWEEN -1000 AND 1000),
    CONSTRAINT chk_av_knowledge_binding_duration CHECK (
        (min_duration_seconds IS NULL AND max_duration_seconds IS NULL)
        OR (
            min_duration_seconds IS NOT NULL
            AND max_duration_seconds IS NOT NULL
            AND min_duration_seconds > 0
            AND min_duration_seconds <= max_duration_seconds
        )
    ),
    CONSTRAINT chk_av_knowledge_binding_status
        CHECK (status IN ('draft', 'reviewing', 'published', 'retired'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS av_video_type_rule (
    video_type_rule_id BIGINT NOT NULL,
    rule_code VARCHAR(128) NOT NULL,
    version_no INT NOT NULL,
    video_type_code VARCHAR(64) NOT NULL,
    industry_code VARCHAR(64) NOT NULL,
    purpose_code VARCHAR(64) NOT NULL,
    min_duration_seconds INT DEFAULT NULL,
    max_duration_seconds INT DEFAULT NULL,
    required_slot_codes_json JSON NOT NULL,
    priority INT NOT NULL,
    copy_rules_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    published_at DATETIME DEFAULT NULL,
    create_dept BIGINT DEFAULT NULL,
    create_by BIGINT DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by BIGINT DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (video_type_rule_id),
    UNIQUE KEY uk_av_video_type_rule_code_no (rule_code, version_no),
    KEY idx_av_video_type_rule_route
        (status, industry_code, purpose_code,
         min_duration_seconds, max_duration_seconds, priority),
    CONSTRAINT chk_av_video_type_rule_version_no CHECK (version_no > 0),
    CONSTRAINT chk_av_video_type_rule_priority CHECK (priority BETWEEN -1000 AND 1000),
    CONSTRAINT chk_av_video_type_rule_duration CHECK (
        (min_duration_seconds IS NULL AND max_duration_seconds IS NULL)
        OR (
            min_duration_seconds IS NOT NULL
            AND max_duration_seconds IS NOT NULL
            AND min_duration_seconds > 0
            AND min_duration_seconds <= max_duration_seconds
        )
    ),
    CONSTRAINT chk_av_video_type_rule_status
        CHECK (status IN ('draft', 'reviewing', 'published', 'retired'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @videoops_020_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 4
         FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_TYPE = 'BASE TABLE'
          AND TABLE_NAME IN ('av_knowledge_item', 'av_knowledge_version', 'av_knowledge_binding', 'av_video_type_rule'))
    AND (SELECT COUNT(*) = 9
         FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, COLUMN_NAME) IN (
              ('av_knowledge_item', 'knowledge_item_id'),
              ('av_knowledge_item', 'stable_code'),
              ('av_knowledge_version', 'knowledge_version_id'),
              ('av_knowledge_version', 'knowledge_item_id'),
              ('av_knowledge_version', 'status'),
              ('av_knowledge_binding', 'knowledge_binding_id'),
              ('av_knowledge_binding', 'knowledge_version_id'),
              ('av_video_type_rule', 'video_type_rule_id'),
              ('av_video_type_rule', 'status')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 6
         FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND (TABLE_NAME, INDEX_NAME) IN (
              ('av_knowledge_item', 'uk_av_knowledge_item_stable_code'),
              ('av_knowledge_version', 'uk_av_knowledge_version_item_no'),
              ('av_knowledge_binding', 'uk_av_knowledge_binding_group_no'),
              ('av_knowledge_binding', 'idx_av_knowledge_binding_route'),
              ('av_video_type_rule', 'uk_av_video_type_rule_code_no'),
              ('av_video_type_rule', 'idx_av_video_type_rule_route')
          ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', CONSTRAINT_NAME)) = 10
         FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE = 'CHECK'
          AND (TABLE_NAME, CONSTRAINT_NAME) IN (
              ('av_knowledge_version', 'chk_av_knowledge_version_version_no'),
              ('av_knowledge_version', 'chk_av_knowledge_version_status'),
              ('av_knowledge_binding', 'chk_av_knowledge_binding_version_no'),
              ('av_knowledge_binding', 'chk_av_knowledge_binding_priority'),
              ('av_knowledge_binding', 'chk_av_knowledge_binding_duration'),
              ('av_knowledge_binding', 'chk_av_knowledge_binding_status'),
              ('av_video_type_rule', 'chk_av_video_type_rule_version_no'),
              ('av_video_type_rule', 'chk_av_video_type_rule_priority'),
              ('av_video_type_rule', 'chk_av_video_type_rule_duration'),
              ('av_video_type_rule', 'chk_av_video_type_rule_status')
          ))
);
SET @videoops_020_schema_assert_sql = IF(
    @videoops_020_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_020_schema_contract_failed'
);
PREPARE videoops_020_schema_assert_stmt FROM @videoops_020_schema_assert_sql;
EXECUTE videoops_020_schema_assert_stmt;
DEALLOCATE PREPARE videoops_020_schema_assert_stmt;
