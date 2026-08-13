ALTER TABLE av_voice
    ADD COLUMN transcript_timeline_json JSON DEFAULT NULL COMMENT 'Whisper 词元时间轴 JSON'
    AFTER transcript_text;
