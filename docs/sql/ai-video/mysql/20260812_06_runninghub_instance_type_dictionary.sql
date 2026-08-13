-- RunningHub instanceType dictionary: default=24GB, plus=48GB.
SET NAMES utf8mb4;
INSERT IGNORE INTO sys_dict_type
    (dict_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES
    (202608120501, 'RunningHub 显存规格', 'aivideo_runninghub_instance_type', NULL, NULL, NOW(), NULL, NULL,
     'RunningHub AI App 和 ComfyUI 工作流的 instanceType 参数');

INSERT INTO sys_dict_data
    (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
     create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 202608120511, 0, '标准（24GB）', 'default', 'aivideo_runninghub_instance_type', '', 'default', 'Y',
       NULL, NULL, NOW(), NULL, NULL, 'RunningHub instanceType=default'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'aivideo_runninghub_instance_type' AND dict_value = 'default'
);

INSERT INTO sys_dict_data
    (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
     create_dept, create_by, create_time, update_by, update_time, remark)
SELECT 202608120512, 1, 'Plus（48GB）', 'plus', 'aivideo_runninghub_instance_type', '', 'default', 'N',
       NULL, NULL, NOW(), NULL, NULL, 'RunningHub instanceType=plus'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data
    WHERE dict_type = 'aivideo_runninghub_instance_type' AND dict_value = 'plus'
);
