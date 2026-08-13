# 90 A：集成与最终验收计划

> **负责人：A。** 本文件保留原总计划第 5 节、第 7 节和 6.5 任务卡。只有 10、20、30 均提交 PR 后才进入最终集成；不得在冲突解决时重新设计公共契约。共享纪律见 [README](README.md)。

## 5. 集成负责人：合并、真实运行与交付

### 任务 35：执行一次主分支同步检查点并合入后端

**风险：** 红色。检查点之后不再自行引入主分支新变化。

- [ ] 在首个功能 PR 合入前执行 `git fetch origin --prune`，记录远程主分支提交号。
- [ ] 如果远程主分支前进，至多一次把它合入 `codex/step6-integration`，解决冲突后运行 C0 契约／迁移测试和文档校验。
- [ ] 三条功能分支只从更新后的集成分支同步，不直接同步主分支。
- [ ] 审查并合入后端 PR，确认没有修改 C0、infra 媒体路径、前端路径或冻结迁移。
- [ ] 运行后端任务 16 的模块测试与打包；此时允许测试作用域假媒体通过，但生产启动若缺真实 Bean 必须明确不可用。

### 任务 36：合入媒体并验证真实 Bean、完整用户端启动和补偿

**风险：** 红色。媒体合入后必须立即取消假边界，验证真实装配。

**文件：**

- 修改：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualStarterHttpFixture.java`
- 新建集成测试：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineRenderRecoveryIT.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/DualStarterHttpFixture.java
git add -- ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineRenderRecoveryIT.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 合入媒体 PR，确认基础 `application.yml` 保持 `aivideo.timeline.*` 占位和安全默认值；团队共享开发地址、账号、密码、Token 与密钥统一写入并提交在用户端和管理端 `application-dev.yml`，环境变量可选覆盖。
- [ ] 由集成负责人单写 `DualStarterHttpFixture`：把 `_01`（以及确实存在时的 `_02`）加入受控迁移序列，向用户端子进程透传媒体二进制／work root／font root 的本机测试环境，保持运营端不装配用户 creation 路由，并在 `finally` 精确清理本次进程、对象、数据库行和 Redis 前缀。
- [ ] 在用户端 `application-dev.yml` 设置本机媒体二进制、工作根、字体根和受控测试配置，环境变量仅可选覆盖；打包并启动完整 `ai-video-user-api`，通过 Spring 上下文测试证明注入真实媒体／AI Bean，不是 unavailable 或 fake。
- [ ] 写恢复 IT 验证任务 14 已交付的生产编排：上传成功但最终 DB 事务失败保持 `pending`；同确定性 key 与同 SHA 复用；不同 SHA 不覆盖；取消／失败只清无引用对象。若失败，按文件所有权退回后端负责人修复，集成负责人不在本任务补写业务实现。
- [ ] 验证 `ready` 素材、当前 execution success、根任务 success 和项目最新成品在一个条件事务内推进；迟到租约／CAS 影响 0 行时不得提交输出。
- [ ] Java 进程在执行中终止再启动，原根任务和执行通过租约恢复，新实际媒体调用创建新 attempt，不制造第二根任务或第二不可变输入版本。
- [ ] 运行：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ai-video-user-api -am -Pdev -DskipTests package
.\mvnw.cmd -pl ai-video-integration-tests -am '-Plocal-integration-test,external-http-it' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=TimelineRenderRecoveryIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

### 任务 37：合入前端、关闭 Mock 并完成第 5→6→7 步联调

**风险：** 红色。真实联调前不得用 Mock 响应掩盖接口差异。

**文件：**

- 新建集成测试：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineStep5To7IT.java`
- 新建集成测试：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineCrossAccountIT.java`
- 新建集成测试：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineDualStarterRouteIsolationIT.java`

**绿灯后的精确暂存命令：**

```powershell
git add -- ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineStep5To7IT.java
git add -- ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineCrossAccountIT.java
git add -- ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/TimelineDualStarterRouteIsolationIT.java
git diff --cached --name-only
git diff --cached --check
```

- [ ] 合入前端 PR，解决冲突时保持 URL 中稳定 `projectId + step`、服务端状态 React Query 单一事实源和生产 Mock 双门禁。
- [ ] 合入前端 PR 后以合入前 SHA 执行 `git diff <frontend_merge_base>..HEAD --`，确认第 0.2 节列出的共享文件只来自前端 PR，集成负责人没有在冲突解决中重写它们；`package-lock.json` 必须无变化，否则退回走所有权变更卡。
- [ ] 在干净 `ai_video_test` 执行全部迁移，启动 MySQL、Redis、用户端 Java、运营端 Java 和 `npm run start:no-mock`。
- [ ] 账号 A 完成真实链路：第 5 步成功视频 → 幂等项目 → 刷新恢复 → 上传图片／视频／音乐／音效 → 七类元素 → 字幕与花字 → 保存／版本／恢复 → AI 建议接受／拒绝 → 重合成 → 任务中心 → 第 7 步预览下载。
- [ ] 验证草稿编辑不改变执行中的固定版本；图片 contain／cover、裁剪与淡入淡出；画中画从源裁剪起点静音循环；背景音乐 ducking；花字预览拖动；字幕不换行／无标点／不少字／不溢出；六模板语义一致。
- [ ] 对 C0、前端 public 和后端 resources 的三个 `font-registry.json` 计算 SHA-256 并断言一致；分别校验前后端两个 OTF 的固定摘要及逐字节一致。删除或损坏任一字体时，前端必须阻止保存／合成，后端必须返回字体不可用，不能回退系统字体。
- [ ] 账号 B 枚举账号 A 的项目、素材、草稿、版本、任务和成品编号，全部返回统一拒绝／不存在；运营 token、错误 client、无凭据和缺权限均拒绝。
- [ ] 验证运营端不暴露用户端 creation 路由，用户端不接受 sys token；401 清会话并停止轮询，403 保持登录且显示权限状态。
- [ ] 运行双启动与全链路 IT：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ai-video-integration-tests -am '-Plocal-integration-test,external-http-it' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=TimelineStep5To7IT,TimelineCrossAccountIT,TimelineRenderRecoveryIT,TimelineDualStarterRouteIsolationIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

### 任务 38：全量验证、最终独立审查和主分支交付

**风险：** 红色。只有全部证据齐全且无未关闭必须修复项才能声明完成。

- [ ] 后端运行：

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user,ruoyi-modules/ai-video/ai-video-infra,ai-video-user-api,ruoyi-admin -am -Dmaven.test.skip=false -DskipTests=false test
.\mvnw.cmd -pl ai-video-user-api,ruoyi-admin -am -DskipTests package
```

- [ ] 前端运行：

```powershell
Set-Location ../ai-video-ui/ai-video-webapp
npm test
npm run tsc
npm run lint
npm run build
```

- [ ] 仓库根运行 `scripts/validate-development-standards.ps1`、`git diff --check`，并扫描提交中不存在内部绝对路径、完整媒体命令或供应商原始响应；同时确认凭据仅出现在两端 `application-dev.yml`，未进入日志、API 响应、前端、任务清单或测试报告。
- [ ] 汇总空库迁移、权限、两个账号隔离、双启动、响应丢失幂等、CAS／租约恢复、素材删除保护、无外键巡检、真实媒体、ASS／路径／协议安全、生产 Mock 关闭和第 5→6→7 步证据。
- [ ] 三条功能分支全部合入后并发例外立即结束。在任意账号中新建一个从未修改本需求代码的只读 Codex 任务，以干净只读工作目录审查集成分支相对 `BASE_SHA` 的整体差异。
- [ ] 最终审查至少覆盖：前端契约／交互／定时器、后端归属／权限／事务／状态机、媒体文件／进程／外部服务安全、迁移与回滚、测试缺口。
- [ ] 必须修复项由原文件负责人在原功能分支修复并重新合入；独立审查任务只对报告项做一次定向复核，不开启第二轮全量审查。
- [ ] 确认无未完成任务、无未解释跳过测试、无生产 Mock、无 C0 文件漂移后，通过 PR 将 `codex/step6-integration` 合入主分支；禁止强推。

## 6. 本角色最小任务卡

### 6.5 集成任务卡

- **单一目标：** 完成任务 35 至任务 38，按固定顺序合入三条线并证明真实第 5→6→7 步闭环。
- **禁止事项：** 不在解决冲突时重写 C0，不以 Mock 代替联调，不顺带改造其他模块，不省略反向验收或独立审查。
- **权威输入：** 集成分支、C0／C1、本计划第 5 节、三个 PR 的验证证据。
- **独占路径：** 集成测试、根／启动 POM 的必要接线、冲突解决和最终验证记录。
- **交付证据：** 干净库迁移、双 Java 启动、真实用户端、两个账号、重启恢复、真实媒体、生产 Mock 关闭、全量构建和最终独立审查。
- **停止条件：** 出现第二次公共契约返工、无法恢复的数据迁移或未关闭安全问题时停止交付并请项目负责人确认。

## 7. 规格覆盖与计划自检

### 7.1 正向能力覆盖

| 规格能力 | 实施任务 | 最终证据 |
| --- | --- | --- |
| 第 5 步成功视频幂等进入项目 | 10、11、15、19、37 | 同源同键只产生一个项目，刷新保留项目 ID |
| owner-only 新表与无物理外键 | 3、4、9、13、16 | DDL／information_schema、owner SQL、巡检报告 |
| 草稿恢复、乐观修订与写回执 | 12、13、19 | 冲突、响应丢失重放、superseded 拉取最新 |
| 图片与画中画 | 10、20 至 22、31、32 | 上传／选择／预览／四角边距／循环／成品 |
| 字幕完整性与样式 | 2、12、23、30、34 | 同一规范夹具、字体测量、安全区、ASS 成品 |
| 花字拖拽与六模板 | 2、20、21、24、30、34 | 预览拖拽、轨道时长、模板语义与真实帧证据 |
| 音乐、音效与画面特效 | 20、24、31、34 | 轨道编辑、白名单计划、成品音视频事实 |
| AI 图片提示词、花字建议、字幕对齐 | 14、15、25、33 | 建议任务、显式确认、失败不改草稿 |
| 不可变版本、恢复和固定合成输入 | 13 至 15、19、36 | 旧版本不变，执行中编辑不改变输入版本 |
| 统一任务、任务中心、取消与重试 | 4 至 6、14、15、25、36 | 根／执行／尝试、CAS、租约、轮询、恢复 |
| 成品登记和第 7 步 | 10、15、25、32、36、37 | pending→ready、最新成品预览和下载 |
| Java 重启恢复 | 14、16、36、37 | 原根／执行恢复、新 attempt、无重复版本／成品 |
| 三设备并行与一次 C1 | 1、7、35 至 38 | 所有权清单、同一 C0_SHA、PR 顺序和审查记录 |

### 7.2 反向与安全覆盖

| 反向场景 | 实施任务 | 期望结果 |
| --- | --- | --- |
| 未登录、错误 client、sys token、缺权限 | 15、16、25、37 | 用户端统一拒绝，运营端不暴露路由 |
| 猜测其他账号 ID | 10 至 16、37 | 项目／素材／版本／任务／成品均拒绝或不存在 |
| 请求伪造归属、路径、URL、FFmpeg 参数 | 2、11、15、17、31、37 | 严格 BO／adapter／Schema 拒绝 |
| 过期修订和旧响应覆盖 | 12、13、19 | `R.code=46603`／稳定错误，旧响应不能覆盖新草稿 |
| 同键异请求或并发重复 | 10 至 14、16 | 稳定幂等冲突或回读唯一赢家，事务无残留 |
| 被引用、删除或损坏素材 | 10、12、13、22、29、37 | 删除保护或合成失败，不展示虚假成功 |
| 迟到 Worker、过期租约、终态回退 | 14、16、36 | CAS 影响 0 行并停止外部副作用 |
| AI 非法结构或项目外文字 | 15、25、33 | 任务安全失败，草稿修订不变 |
| 成功任务缺成品 | 13、15、25、36 | 第 7 步 fail closed，一致性巡检报告 |
| ASS／路径／协议／参数注入 | 28 至 34 | 攻击输入拒绝或安全编码，不能越权读取／联网 |
| 超时、取消、非零、磁盘不足和重启 | 28、32、34、36 | 任务受控收口，临时资源清理，可恢复重试 |
| 外部对象成功但数据库终态失败 | 10、14、32、36 | 保留可补偿 pending 事实，确定性重放不重复上传 |

### 7.3 计划文件自检

- [x] 每个实现任务都有精确负责人、文件、先失败的测试、最小实现、通过命令和提交边界。
- [x] C0、后端、前端、媒体和集成路径没有双写；所有例外先进入 `ownership-manifest.md`。
- [x] 新 Entity、DTO、BO、HTTP 和时间轴 JSON 不含租户／工作区归属；旧来源兼容只封装在通用创作素材 Service 内。
- [x] 后端没有 `application`、`port`、`adapter`、`command`、`model`、`aggregate` 或 `repository` 平行业务层。
- [x] 迁移只有 `_01`；共享后不修改，唯一自动追加为 `_02_creation_timeline_c1.sql`。
- [x] 前端所有 ID 都是字符串，服务端事实不复制进页面状态，生产 Mock 自动门禁完整。
- [x] 媒体调用没有 shell、网络协议输入、任意路径、raw ASS 或用户可控 FFmpeg 表达式。
- [x] 全部本机 IT 使用 `LocalIntegrationEnvironment` 和隔离测试数据，不使用容器、WSL 或全库 Redis 清理。
- [x] 最终交付包含一次独立全量审查和一次定向复核上限，不形成递归审查或无限返工。
