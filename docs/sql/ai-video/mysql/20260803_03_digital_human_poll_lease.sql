ALTER TABLE av_dh_generation_job
    ADD COLUMN poll_token VARCHAR(64) DEFAULT NULL COMMENT '视频轮询租约令牌' AFTER provider_job_id,
    ADD COLUMN poll_lease_until DATETIME DEFAULT NULL COMMENT '视频轮询租约到期时间' AFTER poll_token,
    ADD COLUMN poll_error_count INT NOT NULL DEFAULT 0 COMMENT '连续轮询异常次数' AFTER poll_lease_until;
