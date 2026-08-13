-- The app session owns an opaque workspaceKey (for example "personal-9"), not a numeric ID.
-- Preserve existing values while making every workflow table use that canonical ownership key.
SET NAMES utf8mb4;

ALTER TABLE av_workflow_order MODIFY COLUMN workspace_id VARCHAR(128) NOT NULL;
ALTER TABLE av_workflow_order_asset MODIFY COLUMN workspace_id VARCHAR(128) NOT NULL;
ALTER TABLE av_file_object MODIFY COLUMN workspace_id VARCHAR(128) NULL;
ALTER TABLE av_upload_session MODIFY COLUMN workspace_id VARCHAR(128) NOT NULL;
