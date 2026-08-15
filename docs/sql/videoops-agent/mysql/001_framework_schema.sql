-- VideoOps Agent T1 framework schema baseline.
-- Source: docs/sql/ry_vue.sql; structure only.

SET NAMES utf8mb4;

-- Fail closed before any DDL if the selected schema is not the dedicated project database.
SET @videoops_001_target_ok = (DATABASE() = 'videoops_agent_dev');
SET @videoops_001_target_assert_sql = IF(
    @videoops_001_target_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_001_wrong_target_database'
);
PREPARE videoops_001_target_assert_stmt FROM @videoops_001_target_assert_sql;
EXECUTE videoops_001_target_assert_stmt;
DEALLOCATE PREPARE videoops_001_target_assert_stmt;

-- ----------------------------
-- 菜单权限表
-- ----------------------------
create table sys_menu (
    menu_id           bigint(20)      not null                   comment '菜单ID',
    menu_name         varchar(50)     not null                   comment '菜单名称',
    parent_id         bigint(20)      default 0                  comment '父菜单ID',
    order_num         int(4)          default 0                  comment '显示顺序',
    path              varchar(200)    default ''                 comment '路由地址',
    component         varchar(255)    default null               comment '组件路径',
    query_param       varchar(255)    default null               comment '路由参数',
    is_frame          char(1)         default 'N'                comment '是否为外链（Y是 N否）',
    is_cache          char(1)         default 'Y'                comment '是否缓存（Y缓存 N不缓存）',
    menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
    visible           char(1)         default 0                  comment '显示状态（0显示 1隐藏）',
    status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
    perms             varchar(100)    default null               comment '权限标识',
    icon              varchar(100)    default '#'                comment '菜单图标',
    active_menu       varchar(255)    default ''                 comment '激活菜单路径',
    ext               varchar(2000)   default ''                 comment '扩展字段',
    create_dept       bigint(20)      default null               comment '创建部门',
    create_by         bigint(20)      default null               comment '创建者',
    create_time       datetime                                   comment '创建时间',
    update_by         bigint(20)      default null               comment '更新者',
    update_time       datetime                                   comment '更新时间',
    remark            varchar(500)    default ''                 comment '备注',
    primary key (menu_id)
) engine=innodb comment = '菜单权限表';

-- ----------------------------
-- 角色和菜单关联表
-- ----------------------------
create table sys_role_menu (
    role_id   bigint(20) not null comment '角色ID',
    menu_id   bigint(20) not null comment '菜单ID',
    primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 操作日志记录
-- ----------------------------
create table sys_oper_log (
    oper_id           bigint(20)      not null                   comment '日志主键',
    title             varchar(50)     default ''                 comment '模块标题',
    business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
    method            varchar(100)    default ''                 comment '方法名称',
    request_method    varchar(10)     default ''                 comment '请求方式',
    operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
    oper_name         varchar(50)     default ''                 comment '操作人员',
    user_id           bigint(20)      default null               comment '操作用户ID',
    dept_id           bigint(20)      default null               comment '操作部门ID',
    dept_name         varchar(50)     default ''                 comment '部门名称',
    client_key        varchar(32)     default ''                 comment '客户端',
    device_type       varchar(32)     default ''                 comment '设备类型',
    browser           varchar(50)     default ''                 comment '浏览器类型',
    os                varchar(50)     default ''                 comment '操作系统',
    oper_url          varchar(255)    default ''                 comment '请求URL',
    oper_ip           varchar(128)    default ''                 comment '主机地址',
    oper_location     varchar(255)    default ''                 comment '操作地点',
    oper_param        varchar(4000)   default ''                 comment '请求参数',
    json_result       varchar(4000)   default ''                 comment '返回参数',
    status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
    error_msg         varchar(4000)   default ''                 comment '错误消息',
    oper_time         datetime                                   comment '操作时间',
    cost_time         bigint(20)      default 0                  comment '消耗时间',
    primary key (oper_id),
    key idx_sys_oper_log_bt (business_type),
    key idx_sys_oper_log_uid (user_id),
    key idx_sys_oper_log_s  (status),
    key idx_sys_oper_log_ot (oper_time)
) engine=innodb comment = '操作日志记录';

-- ----------------------------
-- 字典类型表
-- ----------------------------
create table sys_dict_type
(
    dict_id          bigint(20)      not null                   comment '字典主键',
    dict_name        varchar(100)    default ''                 comment '字典名称',
    dict_type        varchar(100)    default ''                 comment '字典类型',
    create_dept      bigint(20)      default null               comment '创建部门',
    create_by        bigint(20)      default null               comment '创建者',
    create_time      datetime                                   comment '创建时间',
    update_by        bigint(20)      default null               comment '更新者',
    update_time      datetime                                   comment '更新时间',
    remark           varchar(500)    default null               comment '备注',
    primary key (dict_id),
    unique (dict_type)
) engine=innodb comment = '字典类型表';

-- ----------------------------
-- 字典数据表
-- ----------------------------
create table sys_dict_data
(
    dict_code        bigint(20)      not null                   comment '字典编码',
    dict_sort        int(4)          default 0                  comment '字典排序',
    dict_label       varchar(100)    default ''                 comment '字典标签',
    dict_value       varchar(100)    default ''                 comment '字典键值',
    dict_type        varchar(100)    default ''                 comment '字典类型',
    css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
    list_class       varchar(100)    default null               comment '表格回显样式',
    is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
    create_dept      bigint(20)      default null               comment '创建部门',
    create_by        bigint(20)      default null               comment '创建者',
    create_time      datetime                                   comment '创建时间',
    update_by        bigint(20)      default null               comment '更新者',
    update_time      datetime                                   comment '更新时间',
    remark           varchar(500)    default null               comment '备注',
    primary key (dict_code),
    key idx_sys_dict_data_type (dict_type)
) engine=innodb comment = '字典数据表';

-- ----------------------------
-- 对象存储配置表
-- ----------------------------
create table sys_oss_config (
    oss_config_id   bigint(20)    not null                  comment '主键',
    config_key      varchar(20)   not null  default ''      comment '配置key',
    access_key      varchar(255)            default ''      comment 'accessKey',
    secret_key      varchar(255)            default ''      comment '秘钥',
    bucket_name     varchar(255)            default ''      comment '桶名称',
    prefix          varchar(255)            default ''      comment '前缀',
    endpoint        varchar(255)            default ''      comment '访问站点',
    domain_url      varchar(255)            default ''      comment '自定义域名',
    is_https        char(1)                 default 'N'     comment '是否https（Y=是,N=否）',
    region          varchar(255)            default ''      comment '域',
    access_policy   char(1)       not null  default '1'     comment '桶权限类型(0=private 1=public 2=custom)',
    status          char(1)                 default 'N'     comment '是否默认（Y=是,N=否）',
    ext1            varchar(255)            default ''      comment '扩展字段',
    create_dept     bigint(20)              default null    comment '创建部门',
    create_by       bigint(20)              default null    comment '创建者',
    create_time     datetime                default null    comment '创建时间',
    update_by       bigint(20)              default null    comment '更新者',
    update_time     datetime                default null    comment '更新时间',
    remark          varchar(500)            default null    comment '备注',
    primary key (oss_config_id)
) engine=innodb comment='对象存储配置表';

SET @videoops_001_schema_contract_ok = (
    (SELECT COUNT(*) = 6
       FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_TYPE = 'BASE TABLE'
        AND TABLE_NAME IN (
            'sys_menu', 'sys_role_menu', 'sys_oper_log',
            'sys_dict_type', 'sys_dict_data', 'sys_oss_config'
        ))
    AND (SELECT COUNT(DISTINCT TABLE_NAME) = 6
           FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE()
            AND INDEX_NAME = 'PRIMARY'
            AND TABLE_NAME IN (
                'sys_menu', 'sys_role_menu', 'sys_oper_log',
                'sys_dict_type', 'sys_dict_data', 'sys_oss_config'
            ))
    AND (SELECT COUNT(DISTINCT CONCAT(TABLE_NAME, ':', INDEX_NAME)) = 5
           FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE()
            AND (TABLE_NAME, INDEX_NAME) IN (
                ('sys_oper_log', 'idx_sys_oper_log_bt'),
                ('sys_oper_log', 'idx_sys_oper_log_uid'),
                ('sys_oper_log', 'idx_sys_oper_log_s'),
                ('sys_oper_log', 'idx_sys_oper_log_ot'),
                ('sys_dict_data', 'idx_sys_dict_data_type')
            ))
);
SET @videoops_001_schema_assert_sql = IF(
    @videoops_001_schema_contract_ok,
    'SELECT 1',
    'SELECT * FROM __videoops_001_schema_contract_failed'
);
PREPARE videoops_001_schema_assert_stmt FROM @videoops_001_schema_assert_sql;
EXECUTE videoops_001_schema_assert_stmt;
DEALLOCATE PREPARE videoops_001_schema_assert_stmt;
