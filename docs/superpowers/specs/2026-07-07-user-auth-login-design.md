# 用户端登录接口设计规格

> **已废止，禁止执行。** 本规格中的 `ai_user + userType + 默认 StpUtil/LoginHelper`（用户表加用户类型加默认登录工具）方案，已被 `docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md` 的 `P0-A-identity-security`（账号与安全底座）完整替代；本文仅供历史追溯，不得作为实现依据。

## 背景

本规格用于在 `ai-video-api` 中为用户端开发登录接口。用户端接口由 `ai-video-user-api` 暴露，用户端账号体系必须与 RuoYi-Vue-Plus 的运营管理端用户体系分离。

已确认边界：

- `ai-video-user-api` 是用户端 API 服务入口，只负责暴露用户端 Controller、启动配置和路由装配。
- 用户端页面位于 `ai-video-ui/ai-video-webapp`，本规格只提供接口参考，不包含前端改造任务。
- RuoYi 的 `sys_user`、用户管理、角色、部门、菜单权限继续定位为运营管理端能力，不作为用户端登录主体。
- 运营管理端需要管理用户端账号 `ai_user`，但管理对象必须是用户端账号表 `ai_user`，不是 `sys_user`。
- 运营管理端前端项目位于 `ai-video-ui/ai-video-platform-ui`，对应前端 CRUD skill 位于 `ai-video-ui/ai-video-platform-ui/.codex/skills/frontend-crud-coding/SKILL.md`。
- 运营端 `ai_user` 管理后端接口和运营端 `ai_user` 管理页面均纳入本规格范围。
- 本次不实现短信验证码登录。
- 本次不包含店铺功能，不新增 `shop`、`shopId`、`shopName` 等字段或接口。

## 目标

实现用户端第一版登录能力：

- 支持账号或手机号 + 密码 + 图形验证码登录。
- 登录成功后返回 Sa-Token 访问令牌和用户端基础信息。
- 支持退出登录。
- 支持获取当前登录用户信息。
- 支持用户端强制修改密码流程。
- 支持运营端新增、查询、详情、编辑、启停、重置用户端账号。
- 支持运营端用户端账号管理页面。
- 用户端业务数据归属后续统一从登录态用户 ID 派生，前端不得传入或覆盖 `ownerId`。

## 非目标

本规格不处理：

- 短信验证码登录。
- 第三方登录。
- 用户端自助注册、忘记密码、自助重置密码。
- 多成员、店铺、团队或组织切换。
- 用户端 Web 页面改造。
- 运营端删除用户端账号；本期只做启停，不提供删除接口和删除页面操作。

## 方案

采用独立用户端账号体系，复用 RuoYi-Vue-Plus 的基础安全能力。

后端业务能力落在 AI 视频业务模块中。目标 Maven module 固定为 `ai-video-api/ruoyi-modules/ai-video`。如果当前代码仍存在旧的 `ruoyi-ai` module，实现计划必须先完成 Maven module 调整，包括目录、artifactId、父 POM modules、启动模块依赖和包扫描约定。`ai-video-user-api` 不堆业务逻辑，只暴露用户端接口入口。

不采用 `sys_user + userType` 的共表方案。原因是运营管理端的用户、角色、部门、菜单权限和用户端账号的产品语义不同，共表会模糊账号边界并增加后续越权风险。

## 后端模块边界

### 用户端 API 入口

位置：

- `ai-video-api/ai-video-user-api`

职责：

- 暴露用户端认证 Controller。
- 接收请求参数、执行参数校验、调用用户端认证 Service。
- 使用 RuoYi 统一响应 `R<T>`。
- 不直接查询数据库、不直接校验密码、不组装复杂登录上下文。

建议包路径：

- `org.dromara.web.controller.user.UserAuthController`

### AI 视频业务模块

位置：

- `ai-video-api/ruoyi-modules/ai-video`

职责：

- 承载用户端账号 Entity、BO、VO、Mapper、Service。
- 承载用户端登录校验、密码校验、登录失败锁定、当前用户信息查询。
- 为后续素材、任务、草稿、额度等业务提供用户端账号 ID 作为 `ownerId` 来源。

建议聚合：

- `org.dromara.ai.user`
- 或 `org.dromara.ai.auth`

实现时按仓库实际包结构选择，不在 Controller 中临时自造分层。

### 运营管理端 ai_user 管理

运营管理端需要提供用户端账号管理能力，管理对象是 `ai_user`，不是 `sys_user`。

后端入口：

- 管理端 API 通过 `ruoyi-admin` 暴露。
- 业务能力仍落在 `ai-video-api/ruoyi-modules/ai-video`。
- `ruoyi-admin` 必须包含 `ai_user` 管理后端接口，不能复用或改造 `sys_user` 管理接口承载用户端账号。
- 权限标识按 `${module}:${business}:${action}`，建议使用 `aivideo:user:add`、`aivideo:user:query`、`aivideo:user:view`、`aivideo:user:edit`、`aivideo:user:changeStatus`、`aivideo:user:resetPwd`、`aivideo:user:viewPhone`。

前端入口：

- 项目：`ai-video-ui/ai-video-platform-ui`
- Skill：`ai-video-ui/ai-video-platform-ui/.codex/skills/frontend-crud-coding/SKILL.md`

运营端管理范围包括：

- 新增用户端账号。
- 查询用户端账号列表。
- 查看用户端账号详情。
- 启用、停用用户端账号。
- 重置用户端账号密码。
- 查看最近登录 IP 和最近登录时间。

运营端后端接口建议：

- `GET /api/ai-users`：分页查询用户端账号列表，入参 `AiUserQueryBo + PageQuery`，响应 `R<PageResult<AiUserPageVo>>`。
- `GET /api/ai-users/{userId}`：查看用户端账号详情，响应 `R<AiUserDetailVo>`。
- `GET /api/ai-users/{userId}/phone`：查看完整手机号，必须校验 `aivideo:user:viewPhone`，响应 `R<AiUserPhoneVo>`。
- `POST /api/ai-users`：新增用户端账号，入参 `AiUserAddBo`，后端生成初始密码，响应 `R<AiUserCreateVo>`。
- `PUT /api/ai-users/{userId}`：编辑用户端账号基础信息，入参 `AiUserEditBo`，响应 `R<Void>`。
- `POST /api/ai-users/{userId}/change-status`：启用或停用用户端账号，入参 `AiUserChangeStatusBo`，响应 `R<Void>`。
- `POST /api/ai-users/{userId}/reset-password`：重置用户端账号密码，后端生成新密码，响应 `R<AiUserResetPasswordVo>`。

运营端查询入参 `AiUserQueryBo`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 否 | 用户名，支持模糊查询。 |
| `phone` | string | 否 | 手机号，支持精确或按项目既有规则查询，返回时仍默认脱敏。 |
| `nickname` | string | 否 | 昵称，支持模糊查询。 |
| `status` | string | 否 | 账号状态：`0` 正常，`1` 停用。 |
| `beginTime` | string | 否 | 创建时间起始，格式沿用 RuoYi 列表查询约定。 |
| `endTime` | string | 否 | 创建时间结束，格式沿用 RuoYi 列表查询约定。 |

运营端列表响应 `AiUserPageVo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID，前端按字符串兼容大数。 |
| `username` | string | 用户名。 |
| `phoneMasked` | string | 脱敏手机号，不返回完整手机号。 |
| `nickname` | string | 昵称。 |
| `avatarUrl` | string | 头像地址或资源引用。 |
| `status` | string | 账号状态：`0` 正常，`1` 停用。 |
| `passwordResetRequired` | boolean | 是否需要强制修改密码。 |
| `lastLoginIp` | string | 最近登录 IP。 |
| `lastLoginTime` | string | 最近登录时间。 |
| `createTime` | string | 创建时间。 |

运营端详情响应 `AiUserDetailVo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID，前端按字符串兼容大数。 |
| `username` | string | 用户名。 |
| `phoneMasked` | string | 脱敏手机号，不返回完整手机号。 |
| `nickname` | string | 昵称。 |
| `avatarUrl` | string | 头像地址或资源引用。 |
| `status` | string | 账号状态：`0` 正常，`1` 停用。 |
| `passwordResetRequired` | boolean | 是否需要强制修改密码。 |
| `lastLoginIp` | string | 最近登录 IP。 |
| `lastLoginTime` | string | 最近登录时间。 |
| `createTime` | string | 创建时间。 |
| `updateTime` | string | 更新时间。 |
| `remark` | string | 备注。 |

运营端查看完整手机号响应 `AiUserPhoneVo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID。 |
| `phone` | string | 完整手机号，仅具备 `aivideo:user:viewPhone` 时返回。 |

运营端新增入参 `AiUserAddBo`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 条件必填 | 用户名，`username` 与 `phone` 至少填写一个；填写时必须唯一。 |
| `phone` | string | 条件必填 | 手机号，`username` 与 `phone` 至少填写一个；填写时必须唯一，并按项目手机号规则校验。 |
| `nickname` | string | 否 | 昵称。 |
| `avatarUrl` | string | 否 | 头像地址或资源引用。 |
| `status` | string | 否 | 账号状态：`0` 正常，`1` 停用；不传默认 `0`。 |
| `remark` | string | 否 | 备注。 |

运营端编辑入参 `AiUserEditBo`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 条件必填 | 用户名，编辑后 `username` 与 `phone` 至少保留一个；填写时必须唯一。 |
| `phone` | string | 条件必填 | 手机号，编辑后 `username` 与 `phone` 至少保留一个；填写时必须唯一，并按项目手机号规则校验。 |
| `nickname` | string | 否 | 昵称。 |
| `avatarUrl` | string | 否 | 头像地址或资源引用。 |
| `remark` | string | 否 | 备注。 |

运营端启停入参 `AiUserChangeStatusBo`：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | string | 是 | 账号状态：`0` 正常，`1` 停用。 |

运营端字段校验规则：

- `username` 长度建议为 4 到 32 位，允许字符由后端常量统一定义，默认建议允许字母、数字、下划线和短横线。
- `phone` 按项目既有手机号校验器校验；如果项目尚无统一校验器，实现计划中必须先补公共校验规则。
- `username`、`phone` 唯一冲突必须返回可识别的业务错误，不得落到数据库唯一索引异常直接外抛。
- 新增和编辑均不得接收明文密码字段；密码只能通过新增或重置密码流程由后端生成。
- 停用 `ai_user` 时必须主动踢出该用户所有 token；启用时不恢复历史 token，也不自动登录。
- 查看完整手机号接口必须记录操作日志，日志不得记录完整手机号。

运营端新增账号响应 `AiUserCreateVo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID。 |
| `username` | string | 用户名。 |
| `initialPassword` | string | 后端生成的初始密码，仅本次响应返回。 |
| `passwordResetRequired` | boolean | 固定为 `true`。 |

运营端重置密码响应 `AiUserResetPasswordVo`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID。 |
| `username` | string | 用户名。 |
| `newPassword` | string | 后端生成的新密码，仅本次响应返回。 |
| `passwordResetRequired` | boolean | 固定为 `true`。 |

新增和重置密码规则：

- 密码由后端生成，不允许运营端前端提交明文自定义密码。
- 密码长度不少于 8 位。
- 密码必须至少包含 1 个数字、1 个大写字母、1 个小写字母和 1 个允许的常用标点符号。
- 允许的标点符号由后端常量统一定义，建议仅使用程序易处理且不易产生转义歧义的字符，例如 `!@#$%^&*()-_=+[]{}:,.?`。
- 生成算法使用安全随机数，生成后打乱字符顺序。
- 明文初始密码只在新增账号或重置密码成功响应中一次性返回，后端只保存 BCrypt 摘要，不记录明文日志。

运营端管理页面纳入本规格实现范围，页面、后端 API、账号模型、权限和字段必须保持一致。

运营端管理页面要求：

- 页面位于 `ai-video-ui/ai-video-platform-ui`。
- 实现前必须读取 `ai-video-ui/ai-video-platform-ui/.codex/skills/frontend-crud-coding/SKILL.md`。
- 页面管理对象为 `ai_user`，不得调用或改造 `sys_user` 管理页面来承载用户端账号。
- 页面至少包含列表、查询筛选、详情、新增、编辑、启停、重置密码操作。
- 新增和重置密码成功后必须展示一次性密码，并提示运营人员及时复制；关闭弹窗后不再显示明文密码。
- 列表和详情默认展示 `phoneMasked`；查看完整手机号必须调用 `GET /api/ai-users/{userId}/phone` 并依赖 `aivideo:user:viewPhone` 独立权限。
- 页面必须具备加载、空、失败、权限不足、分页、提交中、成功和失败状态。

## 数据模型

### 用户端账号

建议表名：`ai_user`

核心字段：

| 字段 | 说明 |
| --- | --- |
| `user_id` | 用户端账号主键，Java 使用 `Long`。 |
| `username` | 登录账号，可选唯一。 |
| `phone` | 手机号，可选唯一。 |
| `password` | BCrypt 密码摘要。 |
| `nickname` | 昵称。 |
| `avatar_url` | 头像地址或资源引用。 |
| `status` | 账号状态：`0` 正常，`1` 停用。 |
| `password_reset_required` | 是否要求下次登录后修改密码：`0` 否，`1` 是。 |
| `last_login_ip` | 最近登录 IP。 |
| `last_login_time` | 最近登录时间。 |
| `del_flag` | 逻辑删除标识，按 RuoYi 基础字段预留；本期不提供运营端删除接口。 |

基础审计字段遵循 RuoYi-Vue-Plus 约定，默认继承 `BaseEntity`。

唯一性建议：

- `username` 唯一，允许为空时需按数据库能力处理空值唯一策略。
- `phone` 唯一，允许为空时需按数据库能力处理空值唯一策略。

本次登录要求至少一个账号标识可匹配：

- 优先按 `username` 精确匹配。
- 如果未匹配，再按 `phone` 精确匹配。

### 登录上下文

用户端登录态必须能区分运营管理端登录态。

建议：

- `LoginUser.userType = "ai_user"`。
- `LoginUser.userId = ai_user.user_id`。
- `LoginUser.username = username 或 phone`。
- `LoginUser.nickname = nickname`。
- `LoginUser.clientKey`、`deviceType` 继续来自 `sys_client` 或用户端客户端配置。

不向用户端登录态写入运营端角色、部门、菜单权限。

## 接口契约

API 前缀遵循项目公共契约，固定为 `/api`。

### 登录

`POST /api/auth/login`

公开访问。

入参 BO：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `account` | string | 是 | 用户名或手机号。 |
| `password` | string | 是 | 登录密码。 |
| `code` | string | 按验证码开关 | 图形验证码。 |
| `uuid` | string | 按验证码开关 | 图形验证码标识。 |
| `clientId` | string | 是 | 客户端 ID。 |
| `grantType` | string | 是 | 固定为 `password`。 |

出参 VO：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accessToken` | string | Sa-Token 访问令牌。 |
| `expireIn` | number | token 剩余有效期，单位按 Sa-Token 返回值。 |
| `clientId` | string | 客户端 ID。 |
| `user` | object | 当前用户基础信息。 |

`user` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID，前端按字符串兼容大数。 |
| `username` | string | 用户名。 |
| `phone` | string | 脱敏手机号。 |
| `nickname` | string | 昵称。 |
| `avatarUrl` | string | 头像地址或资源引用。 |
| `passwordResetRequired` | boolean | 是否需要强制修改密码。 |

响应使用 `R<UserLoginVo>`。

### 退出登录

`POST /api/auth/logout`

需要登录态。

行为：

- 注销当前 token。
- 记录用户端退出日志。
- 返回 `R<Void>`。

### 当前用户

`GET /api/auth/current-user`

需要登录态。

出参 VO：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string | 用户端账号 ID。 |
| `username` | string | 用户名。 |
| `phone` | string | 脱敏手机号。 |
| `nickname` | string | 昵称。 |
| `avatarUrl` | string | 头像地址或资源引用。 |
| `passwordResetRequired` | boolean | 是否需要强制修改密码。 |
| `notificationUnreadCount` | number | 未读通知数，暂无真实实现时可返回 `0`。 |

响应使用 `R<UserCurrentVo>`。

### 修改密码

`POST /api/auth/change-password`

需要用户端登录态。该接口允许在 `password_reset_required = 1` 的强制改密状态下访问。

入参 BO：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `oldPassword` | string | 是 | 当前密码。 |
| `newPassword` | string | 是 | 新密码。 |
| `confirmPassword` | string | 是 | 确认新密码，必须与 `newPassword` 一致。 |

行为：

- 校验当前登录态必须为 `ai_user`。
- 校验 `oldPassword` 与当前密码摘要匹配。
- 校验 `newPassword` 满足与后端生成密码相同的复杂度要求：不少于 8 位，且至少包含 1 个数字、1 个大写字母、1 个小写字母和 1 个允许的常用标点符号。
- 更新密码 BCrypt 摘要。
- 将 `password_reset_required` 置为 `0`。
- 修改密码成功后主动踢出该账号所有 token，包括当前 token。
- 前端收到成功响应后必须跳转登录页并要求重新登录。
- 不记录明文密码。

响应使用 `R<Void>`。

### 图形验证码

如果现有 RuoYi 图形验证码接口可直接被用户端复用，本规格不新增验证码接口。

如果现有路径不适合用户端，应新增：

`GET /api/auth/captcha`

返回结构沿用 RuoYi-Vue-Plus 现有验证码 VO，不自造另一套字段。

## 安全与校验

登录校验顺序：

1. 校验 `clientId`、`grantType` 是否有效。
2. 校验图形验证码。
3. 按 `account` 查询用户端账号。
4. 校验账号存在、未删除、未停用。
5. 校验账号是否处于登录失败锁定期；锁定期间直接拒绝，不继续校验密码。
6. 使用 BCrypt 校验密码。
7. 密码校验失败时累计失败次数；达到阈值后写入锁定状态。
8. 密码校验成功后清理该账号登录失败计数和锁定状态。
9. 构造用户端登录上下文并写入 Sa-Token。
10. 更新最近登录 IP 和最近登录时间。
11. 记录登录成功日志。

失败锁定：

- 复用 Redis。
- key 必须区分用户端，建议 `ai_user_login_err:{account}`。
- 最大失败次数和锁定时间可先复用 `user.password.maxRetryCount`、`user.password.lockTime`。
- 锁定状态必须在 BCrypt 密码校验前检查，避免锁定期间继续消耗密码校验资源。
- 登录成功后必须清理对应账号的失败计数和锁定状态。

日志：

- 登录成功、登录失败、退出登录必须记录。
- 可以复用 RuoYi 登录日志事件能力，但日志内容需要能区分用户端账号和运营管理端账号。
- 运营端新增用户端账号和重置密码必须记录操作日志，但日志不得记录明文密码。

权限：

- 登录和验证码接口公开访问。
- 退出登录和当前用户接口只校验登录态，不使用运营端菜单权限。
- 后续用户端业务接口默认从登录态派生 `ownerId`，不得信任前端传入的 `ownerId`。
- 用户端接口必须通过统一鉴权入口校验 `userType = "ai_user"`、账号状态和强制改密状态，例如 Sa-Token 路由校验、拦截器、统一注解或认证 helper，不得依赖每个 Controller 手写散落判断。
- 当 `password_reset_required = 1` 时，用户端统一鉴权入口只允许访问 `GET /api/auth/current-user`、`POST /api/auth/logout`、`POST /api/auth/change-password`，其他用户端业务接口必须拒绝访问。
- 运营端接口必须通过 RuoYi 既有权限体系校验后台登录态，并叠加后台用户类型校验；`ruoyi-admin` 的 `ai_user` 管理接口必须使用 `aivideo:user:*` 独立权限。

## 安全性需求

### 登录态双向隔离

- 用户端登录态和运营端登录态必须双向隔离。
- 用户端 `ai_user` token 只能访问用户端接口。
- 运营端 `sys_user` token 只能访问运营端接口。
- `ai_user` token 不能访问运营端接口。
- `sys_user` token 不能访问用户端业务接口。
- 即使 token 有效，只要 `userType` 不匹配当前 API 端，也必须返回未授权或无权限。
- 双向隔离必须落在统一鉴权入口，避免每个业务接口重复手写导致遗漏。

### userType 可信来源

- `LoginUser.userType` 只能由后端登录成功时写入 Sa-Token 登录上下文。
- 用户端登录接口固定写入 `userType = "ai_user"`。
- 运营端登录接口继续使用 RuoYi 后台用户类型，例如 `sys_user` 或当前框架已有后台用户类型。
- 登录请求体、Header、Query 中不得接收或信任 `userType`。
- 如果请求中出现 `userType` 字段，后端必须忽略或拒绝，不得用它决定登录身份。
- 后续接口只能从服务端可信登录上下文读取 `LoginUser.userType`。
- 如 token 使用 JWT，必须校验签名和过期时间；业务身份仍以可信登录上下文或已签名声明为准。

### 防冒充规则

- 用户端登录 Service 只能查询 `ai_user` 表。
- 用户端登录 Service 不得调用运营端 `SysLoginService.buildLoginUser` 组装身份。
- 用户端登录 Service 必须构造用户端专用登录上下文，并固定 `userType = "ai_user"`。
- 运营端接口不能只判断 `userType`，还必须继续叠加 RuoYi 权限校验，例如 `@SaCheckPermission("aivideo:user:query")`。
- 用户端接口不能只判断 token 存在，必须校验 `userType = "ai_user"` 和账号状态。

### 密码与初始密码

- 密码只保存 BCrypt 摘要，禁止保存、记录或返回历史明文密码。
- 运营端新增用户端账号或重置密码后，`password_reset_required` 必须置为 `1`。
- 用户使用运营端生成的初始密码或重置密码登录后，应进入强制修改密码流程；在该流程完成前，除当前用户信息、退出登录和修改密码接口外，不应访问其他用户端业务接口。
- 登录和当前用户接口必须返回 `passwordResetRequired`，供用户端识别强制改密状态。
- 明文初始密码和重置密码只在新增或重置成功响应中一次性返回。
- 日志、异常、审计备注、接口错误信息不得包含明文密码。

### 暴力破解防护

- 登录失败至少按 `account` 维度累计，建议同时纳入 IP 维度。
- 达到失败阈值后锁定一段时间，锁定期间不得继续校验密码。
- 图形验证码开启时，必须先校验验证码，再校验账号密码。
- 登录接口应接入限流能力，优先复用 RuoYi / Redis 已有能力。
- 对外错误提示不得区分“账号不存在”和“密码错误”，统一返回账号或密码错误；内部日志可以记录具体原因。

### Token 生命周期

- token 只通过 `Authorization` 请求头传递。
- 退出登录必须使当前 token 失效。
- 停用 `ai_user` 时，必须主动踢出该用户所有 token；如后续新增删除能力，删除时也必须执行相同 token 踢出规则。
- 用户端接口鉴权时仍必须校验账号状态，作为主动踢出失败或缓存延迟时的兜底。
- 用户端 token 超时时间使用用户端 client 配置，不复用运营端后台 client。

### 敏感信息与传输

- 登录、重置密码、修改密码等敏感接口在生产环境必须通过 HTTPS 访问。
- 密码不得出现在 URL、访问日志、异常消息、操作日志或审计备注中。
- 当前用户、列表和详情接口中的手机号默认脱敏；运营端如需查看完整手机号，必须有 `aivideo:user:viewPhone` 独立权限，并通过独立接口获取。

## 前端参考契约

用户端 Web 前端本次由其他人修改，本规格不包含用户端 Web 前端实现任务；运营端 `ai_user` 管理页面属于本规格实现范围。

为便于对接，用户端 Web 后续应参考：

- 登录页只保留账号密码登录，不展示短信验证码登录。
- 登录接口调用 `POST /api/auth/login`。
- 当前用户信息调用 `GET /api/auth/current-user`。
- 退出登录调用 `POST /api/auth/logout`。
- 请求拦截器携带 `Authorization` token。

这些内容仅作为接口契约参考，不进入本次后端实现验收范围。

## 协作切分

后端开发：

- 新增用户端账号领域模型、BO、VO、Mapper、Service。
- 新增用户端认证 Controller。
- 在 `ruoyi-admin` 新增 `ai_user` 管理后端接口。
- 接入 Sa-Token、验证码、密码校验、失败锁定和登录日志。
- 接入用户端与运营端 token 双向隔离的统一鉴权入口。
- 补充初始化 SQL 或迁移脚本，至少提供一个可用于本地联调的用户端账号数据。
- 若当前工程仍使用旧 module 名称，先将 AI 视频业务 Maven module 调整为 `ai-video-api/ruoyi-modules/ai-video`。

用户端前端开发：

- 由其他开发者基于本规格调整 `ai-video-ui/ai-video-webapp`。
- 前端可先 mock `POST /api/auth/login`、`GET /api/auth/current-user`、`POST /api/auth/logout`。

运营端前端开发：

- 在 `ai-video-ui/ai-video-platform-ui` 中实现用户端账号管理页。
- 实现前必须读取 `ai-video-ui/ai-video-platform-ui/.codex/skills/frontend-crud-coding/SKILL.md`。
- 页面管理对象为 `ai_user`，不得调用或改造 `sys_user` 管理页面来承载用户端账号。

联调顺序：

1. 后端先提供 OpenAPI 或接口说明。
2. 用户端前端可由其他开发者以 mock 适配登录流程。
3. 运营端前端在 `ai-video-platform-ui` 以真实或 mock API 实现管理页面。
4. 后端完成后切换真实接口。
5. 联调确认登录、强制改密、刷新获取当前用户、退出登录、token 失效跳转，以及运营端新增、编辑、启停、重置密码、一次性密码展示和完整手机号查看权限。

## 验收标准

- `ai-video-user-api` 能暴露用户端登录、退出、当前用户接口。
- `ruoyi-admin` 能暴露 `ai_user` 新增、查询、详情、编辑、启停、重置密码和完整手机号查看后端接口。
- `ai-video-platform-ui` 能提供 `ai_user` 管理页面，并覆盖列表、查询筛选、详情、新增、编辑、启停、重置密码和完整手机号查看操作。
- 用户端登录不查询 `sys_user`。
- 用户端账号数据不出现在 RuoYi 运营管理端用户管理表中。
- 运营管理端如管理用户端账号，必须管理 `ai_user`，不得混入 `sys_user` 用户管理。
- 运营端新增用户端账号时，后端生成 8 位以上初始密码，且包含数字、大小写字母和允许的常用标点符号。
- 初始密码和重置密码只在成功响应中一次性返回，入库只保存 BCrypt 摘要。
- 停用的用户端账号不能登录；本期不提供运营端删除用户端账号能力。
- 密码错误会累计失败次数，达到阈值后锁定。
- 登录失败锁定状态必须在密码校验前检查，锁定期间不得继续执行 BCrypt 密码校验。
- 登录成功后必须清理该账号登录失败计数和锁定状态。
- 图形验证码开启时，验证码错误或过期会阻止登录。
- 登录成功返回 `accessToken`、`expireIn` 和用户端基础信息。
- 登录和当前用户响应返回 `passwordResetRequired`。
- `GET /api/auth/current-user` 能基于 token 返回当前用户端账号信息。
- `POST /api/auth/logout` 后当前 token 失效。
- `POST /api/auth/change-password` 能校验旧密码、更新新密码、清除 `password_reset_required`，并踢出该账号所有 token。
- 用户端接口拒绝 `sys_user` token，运营端接口拒绝 `ai_user` token。
- token 双向隔离通过统一鉴权入口完成，不依赖各 Controller 分散手写。
- 用户端统一鉴权入口会校验强制改密状态；`password_reset_required = 1` 时只放行当前用户、退出登录和修改密码接口。
- 用户端登录请求中的 `userType` 字段不会影响最终登录身份。
- 用户端登录上下文固定由后端写入 `userType = "ai_user"`。
- 运营端新增账号或重置密码后，`password_reset_required` 置为 `1`。
- 使用初始密码或重置密码登录后，除当前用户信息、退出登录和修改密码接口外，其他用户端业务接口应被限制。
- 运营端新增和重置密码响应只在本次响应返回 `initialPassword` 或 `newPassword`，后续详情或列表不返回明文密码。
- 运营端新增或重置密码成功弹窗关闭后，不再展示明文密码。
- 运营端列表和详情只返回 `phoneMasked`，完整手机号只能由具备 `aivideo:user:viewPhone` 权限的用户通过独立接口查看。
- 运营端启停接口只接受 `0` 正常、`1` 停用；停用后会立即踢出该 `ai_user` 所有 token，启用不恢复历史 token。
- 停用的 `ai_user` 即使持有未过期 token，也不能继续访问用户端业务接口。
- 停用 `ai_user` 时会主动踢出该用户所有 token。
- 后续业务可从登录态获取用户端账号 ID 作为 `ownerId` 来源。

## 规格自检

- 无 `shop`、`shopId`、`shopName` 功能进入本次范围。
- 无短信验证码登录进入本次范围。
- 用户端账号与 `sys_user` 明确分离。
- `ai-video-user-api` 与业务模块职责边界明确。
- `ruoyi-admin` 包含 `ai_user` 管理后端接口，运营端 `ai_user` 管理页面纳入本规格。
- 运营端管理接口已定义查询、新增、编辑、启停、详情、列表、重置密码和查看完整手机号的 BO/VO 契约。
- 安全性需求覆盖 token 双向隔离、userType 防冒充、密码安全、暴力破解防护和敏感信息处理。
- 本期不提供运营端删除用户端账号能力，删除仅作为 `del_flag` 基础字段预留和后续扩展约束。
- 用户端前端只作为接口参考，不包含实现任务。
- 运营端 ai_user 管理边界已明确。
- Maven module 目标路径明确为 `ai-video-api/ruoyi-modules/ai-video`。
- 用户端登录后端、`ruoyi-admin` 的 `ai_user` 管理后端和运营端 `ai_user` 管理页面可由一个实现计划覆盖。
