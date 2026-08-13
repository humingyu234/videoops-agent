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

INSERT IGNORE INTO av_knowledge_item (
    knowledge_item_id, domain_code, knowledge_type_code, stable_code, name, summary,
    tags_json, current_published_version_id, source_type, source_ref,
    create_dept, create_by, create_time, update_by, update_time
) VALUES
    (1001, 'copywriting', 'primary_template', 'global_benefit_hook', '通用利益钩子',
     '开头先给出具体利益点，再说明适用人群。', JSON_ARRAY('hook'), 2001,
     'system_seed', 'k0-seed-20260803-01', 0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (1002, 'copywriting', 'writing_technique', 'global_product_proof', '通用产品证据',
     '正文必须使用用户提供且可验证的产品事实支撑卖点。', JSON_ARRAY('proof'), 2002,
     'system_seed', 'k0-seed-20260803-01', 0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (1003, 'copywriting', 'writing_technique', 'global_action_prompt', '通用行动号召',
     '结尾给出一个明确、可执行且不过度承诺的行动。', JSON_ARRAY('cta'), 2003,
     'system_seed', 'k0-seed-20260803-01', 0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (1004, 'copywriting', 'mandatory_rule', 'global_claim_boundary', '通用声明边界',
     '不得虚构价格、活动、功效、销量或用户未提供的事实。', JSON_ARRAY('compliance'), 2004,
     'system_seed', 'k0-seed-20260803-01', 0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00');

INSERT IGNORE INTO av_knowledge_version (
    knowledge_version_id, knowledge_item_id, version_no, status, content, structure_json,
    source_summary, reviewed_by, reviewed_at, published_by, published_at,
    create_dept, create_by, create_time, update_by, update_time
) VALUES
    (2001, 1001, 1, 'published', '开头先给出具体利益点，再说明适用人群。', JSON_OBJECT(),
     'k0-seed-20260803-01', 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (2002, 1002, 1, 'published', '正文必须使用用户提供且可验证的产品事实支撑卖点。', JSON_OBJECT(),
     'k0-seed-20260803-01', 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (2003, 1003, 1, 'published', '结尾给出一个明确、可执行且不过度承诺的行动。', JSON_OBJECT(),
     'k0-seed-20260803-01', 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (2004, 1004, 1, 'published', '不得虚构价格、活动、功效、销量或用户未提供的事实。', JSON_OBJECT(),
     'k0-seed-20260803-01', 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00');

INSERT IGNORE INTO av_knowledge_binding (
    knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
    knowledge_version_id, industry_code, purpose_code, video_type_code,
    angle_codes_json, angle_priorities_json, min_duration_seconds, max_duration_seconds,
    priority, required_flag, required_slot_codes_json, audience_tag_codes_json,
    exclusion_conditions_json, status,
    create_dept, create_by, create_time, update_by, update_time
) VALUES
    (3001, 'global_benefit_hook', 1, 1001, 2001, '*', '*', '*',
     JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 100, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (3002, 'global_product_proof', 1, 1002, 2002, '*', '*', '*',
     JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 80, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (3003, 'global_action_prompt', 1, 1003, 2003, '*', '*', '*',
     JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 60, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (3004, 'global_claim_boundary', 1, 1004, 2004, '*', '*', '*',
     JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 40, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published',
     0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00');

INSERT IGNORE INTO av_video_type_rule (
    video_type_rule_id, rule_code, version_no, video_type_code, industry_code, purpose_code,
    min_duration_seconds, max_duration_seconds, required_slot_codes_json,
    priority, copy_rules_json, status, published_at,
    create_dept, create_by, create_time, update_by, update_time
) VALUES
    (4001, 'short_20s_structure', 1, '*', '*', '*', 1, 20, JSON_ARRAY(), 100,
     JSON_ARRAY('20秒内：1句钩子+2句卖点+1句行动号召', '禁止虚构价格或效果'),
     'published', '2026-08-03 00:00:00', 0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00'),
    (4002, 'standard_60s_structure', 1, '*', '*', '*', 21, 60, JSON_ARRAY(), 90,
     JSON_ARRAY('21至60秒：钩子、痛点、卖点、证据、行动号召依次展开', '禁止虚构价格或效果'),
     'published', '2026-08-03 00:00:00', 0, 0, '2026-08-03 00:00:00', 0, '2026-08-03 00:00:00');
