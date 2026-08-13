-- Each template upload is a new user material. A stable RunningHub file name must not deduplicate asset records.
SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'av_asset' AND index_name = 'uk_av_asset_object_key'
    ),
    'ALTER TABLE av_asset DROP INDEX uk_av_asset_object_key',
    'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'av_asset' AND index_name = 'uk_av_asset_owner_object_key'
    ),
    'ALTER TABLE av_asset DROP INDEX uk_av_asset_owner_object_key',
    'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;
