-- VideoOps Agent T6 bounded quality repair and human approval control plane.
-- Adds only immutable evaluation/approval facts and narrow AgentRun state; execution facts stay in existing tables.

SET NAMES utf8mb4;

SET @videoops_120_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_120_target_assert_sql = IF(
    @videoops_120_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_120_wrong_target_database'
);
PREPARE videoops_120_target_assert_stmt FROM @videoops_120_target_assert_sql;
EXECUTE videoops_120_target_assert_stmt;
DEALLOCATE PREPARE videoops_120_target_assert_stmt;

SET @videoops_120_precondition_ok = (
    (SELECT COUNT(*) = 1
     FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'av_agent_run'
       AND TABLE_TYPE = 'BASE TABLE')
    AND (SELECT COUNT(*) = 5
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME IN (
               'ck_av_agent_run_status',
               'ck_av_agent_run_counters',
               'ck_av_agent_run_waiting_task',
               'ck_av_agent_run_result',
               'ck_av_agent_run_terminal_time'
           ))
);
SET @videoops_120_precondition_assert_sql = IF(
    @videoops_120_precondition_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_120_agent_run_precondition_failed'
);
PREPARE videoops_120_precondition_assert_stmt FROM @videoops_120_precondition_assert_sql;
EXECUTE videoops_120_precondition_assert_stmt;
DEALLOCATE PREPARE videoops_120_precondition_assert_stmt;

SET @videoops_120_add_quality_count_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_agent_run'
       AND COLUMN_NAME = 'quality_repair_count') = 0,
    'ALTER TABLE av_agent_run ADD COLUMN quality_repair_count BIGINT NOT NULL DEFAULT 0 AFTER retry_count',
    'SELECT 1'
);
PREPARE videoops_120_add_quality_count_stmt FROM @videoops_120_add_quality_count_sql;
EXECUTE videoops_120_add_quality_count_stmt;
DEALLOCATE PREPARE videoops_120_add_quality_count_stmt;

SET @videoops_120_add_pending_approval_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_agent_run'
       AND COLUMN_NAME = 'pending_approval_id') = 0,
    'ALTER TABLE av_agent_run ADD COLUMN pending_approval_id BIGINT NULL AFTER quality_repair_count',
    'SELECT 1'
);
PREPARE videoops_120_add_pending_approval_stmt FROM @videoops_120_add_pending_approval_sql;
EXECUTE videoops_120_add_pending_approval_stmt;
DEALLOCATE PREPARE videoops_120_add_pending_approval_stmt;

SET @videoops_120_add_approval_revision_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'av_agent_run'
       AND COLUMN_NAME = 'approval_revision') = 0,
    'ALTER TABLE av_agent_run ADD COLUMN approval_revision BIGINT NOT NULL DEFAULT 0 AFTER pending_approval_id',
    'SELECT 1'
);
PREPARE videoops_120_add_approval_revision_stmt FROM @videoops_120_add_approval_revision_sql;
EXECUTE videoops_120_add_approval_revision_stmt;
DEALLOCATE PREPARE videoops_120_add_approval_revision_stmt;

SET @videoops_120_constraints_current = (
    SELECT COUNT(*) = 4
       AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_status'
               AND LOWER(CHECK_CLAUSE) LIKE '%waiting_approval%') = 1
       AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_counters'
               AND LOWER(CHECK_CLAUSE) LIKE '%quality_repair_count%') = 1
       AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_waiting_task'
               AND LOWER(CHECK_CLAUSE) LIKE '%waiting_approval%'
               AND LOWER(CHECK_CLAUSE) LIKE '%waiting_task_source is not null%') = 1
       AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_result'
               AND LOWER(CHECK_CLAUSE) LIKE '%waiting_approval%'
               AND LOWER(CHECK_CLAUSE) LIKE '%result_digest is not null%') = 1
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME IN (
          'ck_av_agent_run_status',
          'ck_av_agent_run_counters',
          'ck_av_agent_run_waiting_task',
          'ck_av_agent_run_result'
      )
);
SET @videoops_120_upgrade_constraints_sql = IF(
    @videoops_120_constraints_current,
    'SELECT 1',
    'ALTER TABLE av_agent_run
        DROP CHECK ck_av_agent_run_status,
        DROP CHECK ck_av_agent_run_counters,
        DROP CHECK ck_av_agent_run_waiting_task,
        DROP CHECK ck_av_agent_run_result,
        ADD CONSTRAINT ck_av_agent_run_status CHECK (
            run_status IN (
                ''queued'', ''running'', ''waiting_input'', ''waiting_approval'',
                ''waiting_external_task'', ''completed'', ''failed'', ''cancelled''
            )
        ),
        ADD CONSTRAINT ck_av_agent_run_counters CHECK (
            row_version >= 0
            AND lease_generation >= 0
            AND retry_count >= 0
            AND quality_repair_count BETWEEN 0 AND 2
            AND approval_revision >= 0
        ),
        ADD CONSTRAINT ck_av_agent_run_waiting_task CHECK (
            (
                run_status = ''waiting_external_task''
                AND waiting_task_source IS NOT NULL
                AND waiting_task_source IN (''digital_human_generation'', ''ai_task'')
                AND waiting_task_id IS NOT NULL
                AND waiting_task_id > 0
                AND waiting_contract_revision IS NOT NULL
                AND waiting_contract_revision = contract_revision
                AND resume_after IS NOT NULL
            )
            OR
            (
                run_status = ''waiting_approval''
                AND resume_after IS NULL
                AND (
                    (
                        waiting_task_source IS NULL
                        AND waiting_task_id IS NULL
                        AND waiting_contract_revision IS NULL
                        AND candidate_asset_id IS NULL
                    )
                    OR
                    (
                        waiting_task_source IS NOT NULL
                        AND waiting_task_source = ''ai_task''
                        AND waiting_task_id IS NOT NULL
                        AND waiting_task_id > 0
                        AND waiting_contract_revision IS NOT NULL
                        AND waiting_contract_revision = contract_revision
                        AND candidate_asset_id IS NOT NULL
                        AND candidate_asset_id > 0
                    )
                )
            )
            OR
            (
                run_status NOT IN (''waiting_external_task'', ''waiting_approval'')
                AND waiting_task_source IS NULL
                AND waiting_task_id IS NULL
                AND waiting_contract_revision IS NULL
            )
        ),
        ADD CONSTRAINT ck_av_agent_run_result CHECK (
            (
                run_status = ''completed''
                AND candidate_asset_id IS NOT NULL
                AND candidate_asset_id > 0
                AND result_summary_json IS NOT NULL
                AND result_digest IS NOT NULL
                AND result_digest REGEXP ''^[0-9a-f]{64}$''
                AND error_code IS NULL
                AND error_summary IS NULL
            )
            OR
            (
                run_status = ''failed''
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NOT NULL
                AND error_summary IS NOT NULL
            )
            OR
            (
                run_status = ''cancelled''
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
            )
            OR
            (
                run_status = ''waiting_input''
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NOT NULL
                AND error_summary IS NOT NULL
            )
            OR
            (
                run_status = ''waiting_approval''
                AND (candidate_asset_id IS NULL OR candidate_asset_id > 0)
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NULL
                AND error_summary IS NULL
            )
            OR
            (
                run_status IN (''queued'', ''running'', ''waiting_external_task'')
                AND candidate_asset_id IS NULL
                AND result_summary_json IS NULL
                AND result_digest IS NULL
                AND error_code IS NULL
                AND error_summary IS NULL
            )
        )'
);
PREPARE videoops_120_upgrade_constraints_stmt FROM @videoops_120_upgrade_constraints_sql;
EXECUTE videoops_120_upgrade_constraints_stmt;
DEALLOCATE PREPARE videoops_120_upgrade_constraints_stmt;

SET @videoops_120_approval_check_exists = (
    SELECT COUNT(*) = 1
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = 'ck_av_agent_run_approval'
);
SET @videoops_120_approval_check_current = (
    SELECT COUNT(*) = 1
       AND SUM(LOWER(CHECK_CLAUSE) LIKE '%pending_approval_id is not null%') = 1
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME = 'ck_av_agent_run_approval'
);
SET @videoops_120_add_approval_check_sql = IF(
    @videoops_120_approval_check_current,
    'SELECT 1',
    IF(
        @videoops_120_approval_check_exists,
        'ALTER TABLE av_agent_run
            DROP CHECK ck_av_agent_run_approval,
            ADD CONSTRAINT ck_av_agent_run_approval CHECK (
                (run_status = ''waiting_approval''
                    AND pending_approval_id IS NOT NULL
                    AND pending_approval_id > 0
                    AND approval_revision > 0)
                OR (run_status <> ''waiting_approval'' AND pending_approval_id IS NULL)
            )',
        'ALTER TABLE av_agent_run ADD CONSTRAINT ck_av_agent_run_approval CHECK (
            (run_status = ''waiting_approval''
                AND pending_approval_id IS NOT NULL
                AND pending_approval_id > 0
                AND approval_revision > 0)
            OR (run_status <> ''waiting_approval'' AND pending_approval_id IS NULL)
        )'
    )
);
PREPARE videoops_120_add_approval_check_stmt FROM @videoops_120_add_approval_check_sql;
EXECUTE videoops_120_add_approval_check_stmt;
DEALLOCATE PREPARE videoops_120_add_approval_check_stmt;

CREATE TABLE IF NOT EXISTS av_agent_run_evaluation (
    evaluation_id BIGINT NOT NULL,
    agent_run_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    candidate_no BIGINT NOT NULL,
    render_task_id BIGINT NOT NULL,
    result_asset_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    rule_set_version VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quality_json JSON NOT NULL,
    quality_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    decision VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    repair_scope VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (evaluation_id),
    UNIQUE KEY uk_av_agent_run_evaluation_candidate (agent_run_id, candidate_no),
    UNIQUE KEY uk_av_agent_run_evaluation_task (agent_run_id, render_task_id),
    KEY idx_av_agent_run_evaluation_owner (owner_user_id, agent_run_id, candidate_no),
    CONSTRAINT ck_av_agent_run_evaluation_candidate CHECK (candidate_no BETWEEN 0 AND 2),
    CONSTRAINT ck_av_agent_run_evaluation_rule CHECK (
        rule_set_version REGEXP '^[A-Za-z0-9._:-]{1,32}$'
        AND quality_digest REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_av_agent_run_evaluation_size CHECK (
        OCTET_LENGTH(CAST(quality_json AS CHAR)) <= 65536
    ),
    CONSTRAINT ck_av_agent_run_evaluation_decision CHECK (
        (decision = 'repair' AND repair_scope IN ('render', 'timeline_render'))
        OR (decision = 'conditional' AND repair_scope IN (
            'render', 'timeline_render', 'video_downstream', 'voice_downstream', 'script_downstream', 'manual'
        ))
        OR (decision = 'final' AND repair_scope = 'none')
        OR (decision = 'manual' AND repair_scope = 'manual')
    ),
    CONSTRAINT ck_av_agent_run_evaluation_actor CHECK (
        actor_type = 'app_user' AND actor_id = owner_user_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AgentRun 候选质量事实';

CREATE TABLE IF NOT EXISTS av_agent_run_approval (
    approval_id BIGINT NOT NULL,
    agent_run_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    evaluation_id BIGINT NULL,
    approval_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approval_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT NOT NULL,
    request_summary VARCHAR(512) NOT NULL,
    decision_summary VARCHAR(512) NULL,
    decided_by BIGINT NULL,
    decided_at DATETIME NULL,
    actor_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    create_dept BIGINT NULL,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by BIGINT NOT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_av_agent_run_approval_revision (agent_run_id, revision),
    KEY idx_av_agent_run_approval_owner (owner_user_id, agent_run_id, approval_status),
    KEY idx_av_agent_run_approval_evaluation (owner_user_id, evaluation_id),
    CONSTRAINT ck_av_agent_run_approval_type CHECK (
        approval_type IN ('initial', 'conditional', 'final')
    ),
    CONSTRAINT ck_av_agent_run_approval_status CHECK (
        approval_status IN ('pending', 'approved', 'rejected')
    ),
    CONSTRAINT ck_av_agent_run_approval_evaluation CHECK (
        (approval_type = 'initial' AND evaluation_id IS NULL)
        OR (approval_type IN ('conditional', 'final')
            AND evaluation_id IS NOT NULL AND evaluation_id > 0)
    ),
    CONSTRAINT ck_av_agent_run_approval_subject CHECK (
        subject_digest REGEXP '^[0-9a-f]{64}$' AND revision > 0
    ),
    CONSTRAINT ck_av_agent_run_approval_decision CHECK (
        (approval_status = 'pending' AND decision_summary IS NULL
            AND decided_by IS NULL AND decided_at IS NULL)
        OR (approval_status IN ('approved', 'rejected') AND decision_summary IS NOT NULL
            AND decided_by IS NOT NULL AND decided_by = owner_user_id AND decided_at IS NOT NULL)
    ),
    CONSTRAINT ck_av_agent_run_approval_actor CHECK (
        actor_type = 'app_user' AND actor_id = owner_user_id
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AgentRun 人工批准审计';

SET @videoops_120_approval_constraints_current = (
    SELECT COUNT(*) = 2
       AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_approval_evaluation'
               AND LOWER(CHECK_CLAUSE) LIKE '%evaluation_id is not null%') = 1
       AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_approval_decision'
               AND LOWER(CHECK_CLAUSE) LIKE '%decided_by is not null%') = 1
    FROM information_schema.CHECK_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND CONSTRAINT_NAME IN (
          'ck_av_agent_run_approval_evaluation',
          'ck_av_agent_run_approval_decision'
      )
);
SET @videoops_120_upgrade_approval_constraints_sql = IF(
    @videoops_120_approval_constraints_current,
    'SELECT 1',
    'ALTER TABLE av_agent_run_approval
        DROP CHECK ck_av_agent_run_approval_evaluation,
        DROP CHECK ck_av_agent_run_approval_decision,
        ADD CONSTRAINT ck_av_agent_run_approval_evaluation CHECK (
            (approval_type = ''initial'' AND evaluation_id IS NULL)
            OR (approval_type IN (''conditional'', ''final'')
                AND evaluation_id IS NOT NULL AND evaluation_id > 0)
        ),
        ADD CONSTRAINT ck_av_agent_run_approval_decision CHECK (
            (approval_status = ''pending'' AND decision_summary IS NULL
                AND decided_by IS NULL AND decided_at IS NULL)
            OR (approval_status IN (''approved'', ''rejected'') AND decision_summary IS NOT NULL
                AND decided_by IS NOT NULL AND decided_by = owner_user_id AND decided_at IS NOT NULL)
        )'
);
PREPARE videoops_120_upgrade_approval_constraints_stmt
    FROM @videoops_120_upgrade_approval_constraints_sql;
EXECUTE videoops_120_upgrade_approval_constraints_stmt;
DEALLOCATE PREPARE videoops_120_upgrade_approval_constraints_stmt;

SET @videoops_120_schema_contract_ok = (
    DATABASE() = 'videoops_agent_dev'
    AND (SELECT COUNT(*) = 3
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'av_agent_run'
           AND COLUMN_NAME IN ('quality_repair_count', 'pending_approval_id', 'approval_revision'))
    AND (SELECT COUNT(*) = 2
         FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_TYPE = 'BASE TABLE'
           AND TABLE_NAME IN ('av_agent_run_evaluation', 'av_agent_run_approval'))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 5
         FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND (TABLE_NAME, INDEX_NAME) IN (
               ('av_agent_run_evaluation', 'uk_av_agent_run_evaluation_candidate'),
               ('av_agent_run_evaluation', 'uk_av_agent_run_evaluation_task'),
               ('av_agent_run_evaluation', 'idx_av_agent_run_evaluation_owner'),
               ('av_agent_run_approval', 'uk_av_agent_run_approval_revision'),
               ('av_agent_run_approval', 'idx_av_agent_run_approval_owner')
           ))
    AND (SELECT COUNT(*) = 12
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME IN (
               'ck_av_agent_run_approval',
               'ck_av_agent_run_evaluation_candidate',
               'ck_av_agent_run_evaluation_rule',
               'ck_av_agent_run_evaluation_size',
               'ck_av_agent_run_evaluation_decision',
               'ck_av_agent_run_evaluation_actor',
               'ck_av_agent_run_approval_type',
               'ck_av_agent_run_approval_status',
               'ck_av_agent_run_approval_evaluation',
               'ck_av_agent_run_approval_subject',
               'ck_av_agent_run_approval_decision',
                'ck_av_agent_run_approval_actor'
            ))
    AND (SELECT COUNT(*) = 2
              AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_approval_evaluation'
                      AND LOWER(CHECK_CLAUSE) LIKE '%evaluation_id is not null%') = 1
              AND SUM(CONSTRAINT_NAME = 'ck_av_agent_run_approval_decision'
                      AND LOWER(CHECK_CLAUSE) LIKE '%decided_by is not null%') = 1
         FROM information_schema.CHECK_CONSTRAINTS
         WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND CONSTRAINT_NAME IN (
               'ck_av_agent_run_approval_evaluation',
               'ck_av_agent_run_approval_decision'
           ))
);
SET @videoops_120_schema_assert_sql = IF(
    @videoops_120_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_120_schema_contract_failed'
);
PREPARE videoops_120_schema_assert_stmt FROM @videoops_120_schema_assert_sql;
EXECUTE videoops_120_schema_assert_stmt;
DEALLOCATE PREPARE videoops_120_schema_assert_stmt;
