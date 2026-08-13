# 创作端个人信息与个人积分查询实现计划

> **面向 AI 代理的工作者：** 使用 `subagent-driven-development` 或在同一会话按任务逐项执行；每个生产改动先写并运行失败测试，再做最小实现。

**目标：** 让创作端左下角展示当前登录用户信息，并通过只读接口展示该用户自己的积分账户。

**架构：** 个人信息复用 `/api/auth/me` 已写入的全局状态；`av_quota_account` 永久收敛为 `app_user` 个人积分账户事实源，不支持组织额度主体；积分由 `ai-video-core` 的 RuoYi Entity/Mapper/Service 查询，`ai-video-user` 暴露无参数的 `GET /api/quota/account`，前端通过 React Query 管理独立加载和错误状态。

**技术栈：** Spring Boot、MyBatis-Plus、Sa-Token、JUnit 5、React、Umi、React Query、Vitest/Jest。

**增量约束（2026-08-03）：** 新增 `used_balance` / `usedBalance`，默认 `0` 并由查询接口原样返回；本轮不实现累计、扣减或统计逻辑。前端以该字段显示“已用积分”，不再展示冻结积分。

---

## 文件边界

- 创建 `docs/sql/ai-video/mysql/20260803_02_personal_quota_account.sql`：建立积分账户表和唯一查询约束，不插入账户数据。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/{domain,dto,mapper,service,service/impl}` 下的个人积分只读模型与服务。
- 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/quota/{controller,domain/vo}` 下的创作端接口。
- 创建后端对应单元测试，修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`。
- 创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/quota/{types,api}.ts` 和测试。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`、`components/StudioSider.tsx` 及现有测试；保留这些文件中已有的未提交修改。

### 任务 1：后端积分账户查询

- [ ] 编写 `QuotaAccountServiceImplTest`：命中返回精确字符串余额；未命中抛 `46135`；验证 Mapper 只有查询、无写入。
- [ ] 运行定向测试，确认因类/方法尚不存在而失败。
- [ ] 增加迁移、Entity、Mapper、DTO、Service 接口与实现；Service 先按当前用户读取 `AppUser.personalTenantId`，再固定查询 `app_user`、当前用户、`ai_text_credit`。
- [ ] 运行定向测试并保持绿灯。

### 任务 2：创作端积分接口

- [ ] 编写 `QuotaControllerTest`：无请求参数、只从 app 登录快照取用户编号、返回四字段、保留 `46135`。
- [ ] 运行定向测试，确认因接口尚不存在而失败。
- [ ] 增加 `QuotaController` 与 `PersonalQuotaAccountVo`；权限固定为 `aivideo:quota:query`，Controller 不包含查询业务。
- [ ] 运行 `ai-video-user` 定向测试并保持绿灯。

### 任务 3：前端请求与展示

- [ ] 先扩展/新增测试，覆盖请求路径、无 userId 参数、字符串余额、加载、成功、`46135`、`403`、通用失败和手动重试。
- [ ] 运行定向测试，确认静态姓名/积分或缺失服务导致失败。
- [ ] 增加 quota service；页面复用 `@@initialState.currentUser` 并发起积分查询；`StudioSider` 仅渲染传入状态。
- [ ] 删除静态姓名、静态积分和组织行；主名称 `displayName ?? username`，必要时第二行显示 username。
- [ ] 使用字符串/`BigInt` 格式化余额与百分比，不轮询、不把账户缺失渲染成 0。
- [ ] 运行前端定向测试并保持绿灯。

### 任务 4：契约、验证与审查

- [ ] 更新 `docs/API_CONTRACT.md` 和 `docs/DOMAIN_MODEL.md`，写明无参数、自账户、四字段和 `46135` 零写入语义。
- [ ] 运行后端定向测试、前端定向测试、前端类型检查/构建和 `git diff --check`。
- [ ] 核对变更清单：没有组织路径、没有账户初始化、没有 userId 请求参数、没有 JavaScript `number` 余额。
- [ ] 进行一次规格符合性审查和一次身份/积分资产边界审查，只处理阻塞问题。

### 任务 5：已用积分字段增量

- [ ] 先更新 `QuotaAccountServiceImplTest`、`QuotaControllerTest`、`quota/api.test.ts` 和 `StudioSider.test.tsx`，断言 `usedBalance` 为十进制字符串且页面显示“已用积分”；运行并确认因生产契约缺字段而失败。
- [ ] 新增独立 MySQL 迁移，为 `av_quota_account` 增加非负 `used_balance BIGINT NOT NULL DEFAULT 0`，不得插入或更新账户余额数据。
- [ ] 部署顺序固定为：先备份并验证目标库，再执行 `used_balance` 迁移并完成字段、默认值、非负约束和存量数据回查；迁移成功后才发布读取该字段的后端，最后发布前端。
- [ ] MySQL DDL 可能隐式提交；若迁移在新增字段后、约束校验或约束创建阶段失败，保留已新增字段，先修正不符合契约的字段定义、存量负值或同名错误约束，再幂等重跑同一迁移，禁止直接删除字段作为回滚。若应用发布后需要回退，只回退应用到不读取 `used_balance` 的上一版本，保留兼容的新增字段，待问题修复后重新执行迁移并按顺序发布。
- [ ] 在 `AvQuotaAccount`、`QuotaAccountSnapshotDTO`、`QuotaAccountVo` 和 Service 映射中透传 `usedBalance`，不增加写入或统计逻辑。
- [ ] 更新前端 DTO 校验和积分卡展示，使用 `usedBalance` 替代 `lockedBalance` 的展示文案和值。
- [ ] 运行后端定向测试、前端定向测试、TypeScript 检查，并在本地 MySQL 应用迁移后回查 `creator.used_balance=0`。

### 任务 6：评审修复——个人账户契约与权限会话

**文件：**

- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md`
- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md`
- 修改：`docs/sql/ai-video/mysql/20260803_02_personal_quota_account.sql`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/PersonalQuotaSchemaIT.java`

- [ ] 先编写迁移集成测试：断言 `av_quota_account.subject_type` 只接受 `app_user`；首次新增或恢复 `personal_creator -> aivideo:quota:query` 时，`app_role.role_revision` 和当前有效用户的 `permission_revision` 各递增一次；重复执行不再次递增。
- [ ] 运行迁移测试并确认因现有脚本未推进权限修订而失败。
- [ ] 修改迁移：按 `role_code` 和 `permission_code` 解析目标主键，固定种子主键冲突时失败关闭；仅在映射新增或恢复时幂等推进角色和有效用户权限修订。
- [ ] 将既有额度模型契约统一为个人账户：`subject_type=app_user`、`create_time/update_time`，删除 `av_quota_account` 的组织主体定义；组织身份与工作区本身不在本任务修改范围。
- [ ] 重跑迁移测试，确认首次、恢复、重复三种场景通过。

### 任务 7：评审修复——真实鉴权链

**文件：**

- 修改：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualTokenIsolationIT.java`
- 保留：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/quota/controller/QuotaControllerTest.java` 作为 Controller 映射单元测试

- [ ] 先增加真实用户端安全链测试，分别请求 `GET /api/quota/account`：无凭据、伪造/过期 `app` 凭据、有效运营端凭据、缺少权限的 `app` 会话必须被拒绝；持有权限的合法 `app` 会话成功。
- [ ] 运行测试并确认现有测试夹具或安全覆盖缺失导致红灯，而不是测试代码语法错误。
- [ ] 只补测试所需的最小启动配置/夹具，不在 Controller 增加重复鉴权逻辑。
- [ ] 重跑真实安全链测试和现有 `QuotaControllerTest`，确认全部通过。

### 任务 8：评审修复验证

- [ ] 在专用测试库运行个人账户迁移首次、重复和恢复场景，确认账户行数及余额不变。
- [ ] 运行 `QuotaAccountServiceImplTest`、`QuotaControllerTest`、`PersonalQuotaSchemaIT`、`DualTokenIsolationIT`。
- [ ] 运行 `mvnw -pl :ai-video-user-api -am -DskipTests package`、前端 16 项定向测试、`tsc --noEmit` 与 `scripts/validate-development-standards.ps1`。
- [ ] 只对上述评审发现和直接相关测试做一次独立定向复核，不重新发起全量审查。
