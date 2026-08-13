-- RunningHub advanced ComfyUI workflow API supports instanceType=plus (48GB).
-- AI App API does not support this field; the application layer rejects it for AI App configs.
SET @workflow_instance_type_exists = (
    SELECT COUNT(*) > 0
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'av_workflow_execution_config'
      AND column_name = 'instance_type'
);
SET @workflow_instance_type_ddl = IF(
    @workflow_instance_type_exists,
    'SELECT 1',
    'ALTER TABLE av_workflow_execution_config ADD COLUMN instance_type VARCHAR(16) NULL AFTER webapp_id'
);
PREPARE workflow_instance_type_stmt FROM @workflow_instance_type_ddl;
EXECUTE workflow_instance_type_stmt;
DEALLOCATE PREPARE workflow_instance_type_stmt;

UPDATE av_workflow_execution_config
SET instance_type = NULL
WHERE execution_mode = 'runninghub_ai_app' AND instance_type IS NOT NULL;
