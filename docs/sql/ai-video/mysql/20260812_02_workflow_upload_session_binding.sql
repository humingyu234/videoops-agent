-- Bind a private upload session to the exact visible template form field it was issued for.
ALTER TABLE av_upload_session ADD COLUMN template_id BIGINT NULL AFTER file_id;
ALTER TABLE av_upload_session ADD COLUMN schema_hash VARCHAR(71) NULL AFTER template_id;
ALTER TABLE av_upload_session ADD COLUMN input_key VARCHAR(128) NULL AFTER schema_hash;
