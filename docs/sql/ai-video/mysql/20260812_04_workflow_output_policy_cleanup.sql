-- RunningHub may return any number of valid results with mixed media types.
-- Remove retired operator-defined output restrictions while keeping the JSON
-- column for backward-compatible API and database contracts.
UPDATE av_workflow_execution_config
SET output_policy_json = JSON_REMOVE(
        output_policy_json,
        '$.allowedOutputTypes',
        '$.primaryOutputType',
        '$.requireUniquePrimary',
        '$.maxResultCount',
        '$.maxBytesPerResult'
    ),
    update_time = CURRENT_TIMESTAMP
WHERE JSON_CONTAINS_PATH(
        output_policy_json,
        'one',
        '$.allowedOutputTypes',
        '$.primaryOutputType',
        '$.requireUniquePrimary',
        '$.maxResultCount',
        '$.maxBytesPerResult'
    ) = 1;
