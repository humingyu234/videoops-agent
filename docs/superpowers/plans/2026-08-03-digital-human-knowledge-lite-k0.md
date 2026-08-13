# 数字人简化知识库 K0 四卡实现计划

> **2026-08-03 后续范围说明：** 本文件继续作为 `K0-1`～`K0-4` 的历史执行基线，正文中的“无 HTTP、无前端、无运营端、无真实模型”只描述 K0 candidate（候选版本）。K0 完成后新增的运营端知识管理与创作端问卷兼容入口不属于这四张任务卡，不能复用 K0 PASS 或 handoff（交接）证明其已验收，也不能据此声称完整 P1、P2 或 DeepSeek 已完成。后续运行时边界以 `docs/API_CONTRACT.md` 的“运营端知识库管理”、`docs/DOMAIN_MODEL.md` 的“K0 后续运营扩展边界”及完整 P1／P2 计划为准；进入正式模型调用前仍须完成 P0-B、P0-C 与 P2 reconciliation（计划协调）。

> **面向 AI 代理的工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务实现本计划。步骤使用复选框（`- [ ]`）跟踪进度。

**目标：** 用四张任务卡交付数字人文案生成可直接消费的最小知识上下文：MySQL 预置知识、确定性匹配、稳定摘要和可独立审查的 candidate（候选版本）。

**架构：** 只在 `ai-video-core` 的 `org.dromara.aivideo.knowledge` 聚合内使用 RuoYi 贫血 Entity（实体）+ Mapper（数据访问）+ Service（业务服务）编排；无 HTTP、无前端、无 provider（外部服务适配）。K0 使用完整 P1 已规划的四张核心表，但不伪造完整 P1 所需的方向目录版本、提示词版本、生成上下文修订或接受事实。

**技术栈：** Java 21、Spring Boot 4.1、RuoYi-Vue-Plus 6.x、MyBatis-Plus、MySQL 8、本机 `LocalIntegrationEnvironment`、JUnit 5、AssertJ、Mockito、Jackson、Maven Surefire/Failsafe。

---

## 0. 本计划的唯一范围

本文件是独立 K0 执行规格，只包含 `K0-1`～`K0-4` 四张任务卡。它不修改完整 P1 计划、主计划、P0-B 或 P0-C；完整 P1 Task 2～10 保留为 `DEFERRED`（延后），不是取消。

恢复完整 P1 前必须另开一次 reconciliation（计划协调）窗口：把完整 P1 Task 2 的四 Entity/Mapper 创建动作改为校验并接管 K0 结构，把 Task 10 的 DTO/Service 精确清单加入 K0 的 2 个 DTO 和 1 个 Service，并把完整迁移链登记为 `01 → 02 → 03 → 04 → 04a → knowledge-lite → 05 → 06 → 07`。该协调不属于当前四卡，也不阻塞 K0 candidate。

固定决定：

- 业务数据使用 RuoYi 后端和 MySQL；K0 不访问 Redis，但所有 `*IT` 仍通过 `LocalIntegrationEnvironment` 校验本机受控 MySQL/Redis 环境。
- 系统知识由迁移预置、全局只读；用户本次填写的产品名称、卖点、价格、活动和补充说明不写入知识表。
- 请求只有 `industryCode`、`purposeCode`、`targetDurationSeconds`、`tagCodes`。
- 结果只有 `knowledgeVersionIds`、`excerpts`、`copyRules`、`contentHash`。
- 无 HTTP Controller、BO、VO、前端 TypeScript 类型，因此 `docs/API_CONTRACT.md` 不变。
- 无真实模型、搜索、IndexTTS2、ComfyUI、任务、额度、流水、业务草稿、共享、组织或运营端能力。
- F1 前仍禁止真实模型和搜索调用；本计划仅授权不依赖 P0-C 的 K0 本机数据库迁移与查询。

开发 B 的完整 P1 Task 1 原始 candidate 固定为
`f968ca4364c570169fad080664459f47e6495b12`。该提交不得 squash（压缩）、amend（改写）或 rebase（变基）。K0 实施继续使用 `codex/p1-knowledge`：计划 MR 合入 `main` 后，开发 B 以普通 merge（合并）把最新 `origin/main` 合入该分支，从而保留原提交 SHA。

开始 K0-1 前执行：

```powershell
$ErrorActionPreference = 'Stop'
git fetch origin
if ((git branch --show-current).Trim() -ne 'codex/p1-knowledge') { throw '必须在 codex/p1-knowledge 实施 K0' }
if (@(git status --porcelain).Count -ne 0) { throw '实施前工作区必须干净' }
git cat-file -e 'f968ca4364c570169fad080664459f47e6495b12^{commit}'
if ($LASTEXITCODE -ne 0) { throw '缺少开发 B 原 P1 Task 1 candidate' }
git merge-base --is-ancestor f968ca4364c570169fad080664459f47e6495b12 HEAD
if ($LASTEXITCODE -ne 0) { throw '原 P1 Task 1 candidate 不是当前分支祖先' }
git merge --no-ff --no-edit origin/main
git push origin codex/p1-knowledge
```

该 merge 只表示 B 的功能分支吸收最新 `main`，不表示把 B 的分支合入 `main`。

## 1. 固定契约和选择语义

稳定 Service 签名：

```java
KnowledgeContextDTO resolve(KnowledgeContextRequestDTO request);
```

请求规范：

- `industryCode`、`purposeCode`：去除首尾空白后非空，最长 64 字符，禁止客户端使用内部通配值 `*`。
- `targetDurationSeconds`：非空且大于 0。
- `tagCodes`：非空集合；允许空列表，不允许空元素；每项去除首尾空白后最长 64 字符，去重并按 Java 自然顺序排序，结果不可变。

结果规范：

- `knowledgeVersionIds` 与 `excerpts` 数量一致、顺序一致；版本编号为正数且不重复，正文非空。
- `copyRules` 是有序、去重、非空字符串列表。
- 三个集合都进行防御性复制并保持不可变。
- `contentHash` 是 64 位小写十六进制 SHA-256。
- 无匹配是成功：三个集合为空，规范 JSON 固定为
  `{"knowledgeVersionIds":[],"excerpts":[],"copyRules":[]}`，摘要固定为
  `62dffd7d09a50ad03b651edf697d9ab42a09c9607973ab89036bc2b6abb67e34`。

知识排序固定为：匹配层级升序 → 标签命中数降序 → 绑定优先级降序 → 条目稳定代码升序 → 知识版本号降序 → 知识版本编号升序 → 绑定编号升序。匹配层级固定为：

1. 行业精确且用途精确；
2. 行业精确且用途为 `*`；
3. 行业为 `*` 且用途精确；
4. 行业和用途均为 `*`。

同一 `knowledgeVersionId` 被多个绑定命中时，只保留排序后的第一次出现。规则排序固定为：匹配层级升序 → 优先级降序 → 规则稳定代码升序 → 版本号降序 → 规则编号升序；同一规则内 `copy_rules_json` 的数组顺序不变，跨规则重复文案只保留第一次出现。

规范摘要仅覆盖以下固定字段顺序：

```json
{"knowledgeVersionIds":[2001,2002],"excerpts":["先给利益点。","用事实支撑卖点。"],"copyRules":["15秒：1句钩子+2句卖点+1句行动号召","禁止虚构价格或效果"]}
```

上述金丝雀的 SHA-256 固定为
`0e29ebac61ef88a724b0365743f9bed2db9aef7bbe40c94f8f92b79ae9863346`。

## 2. 文件总表和所有权

| 任务卡 | 唯一 writer | 独立 reviewer | 允许修改 |
| --- | --- | --- | --- |
| K0-1 | 开发 B | 开发 C | K0 DTO/Service 契约、契约测试、`docs/DOMAIN_MODEL.md` 的 K0 小节；为兼容原 Task 1，仅允许扩展 `KnowledgeContractTest` 的顶层 DTO 清单 |
| K0-2 | 开发 B | 开发 C | 四个 Entity、三个枚举、四个 Mapper、K0 迁移、领域测试、迁移 IT |
| K0-3 | 开发 B | 开发 C | `ai-video-core/pom.xml` 的 JSON 依赖、Service 实现、单元测试、查询 IT |
| K0-4 | 开发 B | 开发 C | K0 边界测试；候选 handoff 只写当前 worktree 的 Git metadata，不进入仓库 |

风险等级统一为红色：命中共享 Service 契约、数据库结构、迁移和下游消费边界。每张卡同一时间最多 2 人：B 实施，C 只读独立审查；C 不代写实现或 writer 证据。A 保留计划/共享文件 owner 身份，并在本计划合入后把 `docs/DOMAIN_MODEL.md` 的 K0 小节写窗口授予 B，仅限 K0-1 的精确文件和一次小提交。

任务必须严格串行 `K0-1 → K0-2 → K0-3 → K0-4`。每张卡都执行 RED → 最小 GREEN → 精确验证 → 精确暂存 → 小提交 → push。禁止 `git add .`、禁止直接 push `main`、禁止改写已进入 handoff 的提交。

---

### K0-1：冻结最小知识上下文契约

**最小任务卡**

- **单一目标：** 新增两个 K0 DTO 和一个只读 Service 接口，并使原 P1 Task 1 的五个 DTO 与两个 Service 保持字节和签名不变。
- **不做：** 不建表、不写 Mapper/实现类、不增加 HTTP 或前端类型、不加入完整 P1 revision 字段。
- **权威来源：** 本文件第 0～2 节、`AGENTS.md`、`RULES.md`、`docs/DOMAIN_MODEL.md`、RuoYi Plus AI Coding skill。
- **风险：** 红；新增稳定跨模块 Service 契约及共享领域文档。
- **依赖：** 本计划 MR 已由独立 reviewer 通过并实际合入 `main`；开始门禁已完成普通 merge。
- **允许影响：** 仅下方 6 个文件。原 `f968ca4` 不改写，新变化必须是新的 K0-1 commit。
- **成功/反向验收：** 精确字段、签名、不可变集合和非法输入均由测试证明；`*`、空白、超长代码、非正时长、空白标签必须拒绝；原五 DTO 及两个 Service 的源码 SHA 不因 K0 改变。
- **并发/审查：** B 单 writer；C 在 commit/push 后只读审查契约和原 Task 1 SHA。
- **固定输出：** 完成项、风险、验证证据、阻塞项、分支和 commit SHA。

**文件：**

- 修改：`docs/DOMAIN_MODEL.md`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeContextRequestDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeContextDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeContextService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContractTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextContractTest.java`

- [ ] **步骤 1：保存原 Task 1 稳定源码 SHA**

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
$stable = @(
  'KnowledgePlanDTO.java','KnowledgeRouteRequestDTO.java','KnowledgeRouteResultDTO.java',
  'KnowledgeSnapshotRequestDTO.java','KnowledgeSnapshotDTO.java'
) | ForEach-Object { Join-Path $root "ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/$_" }
$stable += @('IKnowledgeRoutingService.java','IKnowledgeSnapshotService.java') |
  ForEach-Object { Join-Path $root "ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/$_" }
$before = [ordered]@{}
foreach ($file in $stable) { $before[$file] = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() }
$before | ConvertTo-Json | Set-Content -LiteralPath (git rev-parse --git-path p1-k0-task1-before.json) -Encoding utf8
```

- [ ] **步骤 2：先写会失败的契约测试**

`KnowledgeContextContractTest` 使用 `Class.forName` 读取尚不存在的三个类型，并断言两个 record 的组件名称、Java 类型和顺序以及 `resolve` 的参数/返回类型；此时失败必须是 `ClassNotFoundException`。同时把非法输入和集合防御性复制场景写入同一测试，类型出现后这些断言才参与 GREEN。

```java
@Test
void freezesK0RecordLayoutsAndServiceSignature() throws Exception {
    Class<?> requestType = Class.forName(
        "org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO");
    Class<?> resultType = Class.forName(
        "org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO");
    Class<?> serviceType = Class.forName(
        "org.dromara.aivideo.knowledge.service.IKnowledgeContextService");

    assertThat(requestType.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("industryCode", "purposeCode", "targetDurationSeconds", "tagCodes");
    assertThat(resultType.getRecordComponents())
        .extracting(RecordComponent::getName)
        .containsExactly("knowledgeVersionIds", "excerpts", "copyRules", "contentHash");
    Method resolve = serviceType.getDeclaredMethod("resolve", requestType);
    assertThat(resolve.getReturnType()).isEqualTo(resultType);
}
```

- [ ] **步骤 3：运行 RED 并保存 fresh XML**

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
& (Join-Path $root 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeContextContractTest' test
if ($LASTEXITCODE -eq 0) { throw 'K0-1 RED 必须失败' }
```

- [ ] **步骤 4：写最小 GREEN 契约并同步领域文档**

实现以下精确声明；构造器按第 1 节规范校验、规范化并使用 `List.copyOf`。`KnowledgeContextDTO` 还必须校验版本/正文同序同数量、正数且唯一的版本编号、非空规则以及 64 位小写十六进制摘要。

```java
public record KnowledgeContextRequestDTO(
    String industryCode,
    String purposeCode,
    Integer targetDurationSeconds,
    List<String> tagCodes
) {}

public record KnowledgeContextDTO(
    List<Long> knowledgeVersionIds,
    List<String> excerpts,
    List<String> copyRules,
    String contentHash
) {}

public interface IKnowledgeContextService {
    KnowledgeContextDTO resolve(KnowledgeContextRequestDTO request);
}
```

在 `docs/DOMAIN_MODEL.md` 新增“数字人简化知识上下文 K0”小节，只登记本文件第 0～1 节的四表、只读种子、请求/结果、通配与摘要语义；明确无 HTTP、无 owner/tenant、无用户私有知识。`docs/API_CONTRACT.md` 保持不变。

原 `KnowledgeContractTest` 仅把顶层 DTO 文件清单从 5 个扩展为 7 个，加入 `KnowledgeContextRequestDTO.java`、`KnowledgeContextDTO.java`；原五 DTO 的布局断言和两个完整 P1 Service 的断言不得修改。

- [ ] **步骤 5：运行 GREEN 并证明原稳定源码未漂移**

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
& (Join-Path $root 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dtest=KnowledgeContractTest,KnowledgeContextContractTest' test
if ($LASTEXITCODE -ne 0) { throw 'K0-1 GREEN 失败' }
$before = Get-Content -LiteralPath (git rev-parse --git-path p1-k0-task1-before.json) -Raw | ConvertFrom-Json
foreach ($property in $before.PSObject.Properties) {
  $actual = (Get-FileHash -LiteralPath $property.Name -Algorithm SHA256).Hash.ToLowerInvariant()
  if ($actual -ne $property.Value) { throw "原 P1 稳定源码发生漂移：$($property.Name)" }
}
```

- [ ] **步骤 6：精确暂存、提交并 push**

```powershell
git add -- docs/DOMAIN_MODEL.md `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeContextRequestDTO.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeContextDTO.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeContextService.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContractTest.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextContractTest.java
git diff --cached --check
git commit -m "feat(p1): 冻结 K0 知识上下文契约"
git push origin codex/p1-knowledge
```

---

### K0-2：建立四表、只读种子和 Mapper

**最小任务卡**

- **单一目标：** 建立 K0 四个贫血 Entity、Mapper、正式幂等迁移和可恢复的本机 MySQL 证据。
- **不做：** 不新增写 Service、Controller、菜单、权限、导入表、快照表、P0-C 表或 Mapper XML。
- **权威来源：** 本文件第 0～2 节、K0-1 契约、generator Entity/Mapper 模板、`LocalIntegrationEnvironment`。
- **风险：** 红；正式数据库结构、约束、种子和前向迁移。
- **依赖：** K0-1 commit 已 push 且工作区干净。
- **允许影响：** 仅下列 14 个文件；禁止修改 `20260728_01_p0a_identity_security.sql` 和任何其他迁移。
- **成功/反向验收：** 四表、外键、唯一键、检查约束、索引、固定种子、重复执行和专用库重建全部通过；非本机或非 `ai_video_test` 立即失败。
- **恢复：** 不提供会破坏已发布知识的 down migration（向下迁移）。恢复证据固定为：测试夹具重建专用 `ai_video_test`，重新执行 `ry_vue.sql → 01 → knowledge-lite` 后得到相同种子摘要；生产纠错只能追加新的前向迁移。
- **并发/审查：** B 单 writer；C 独立审查 DDL、种子幂等、恢复和 Entity 映射。
- **固定输出：** 完成项、风险、验证证据、阻塞项、迁移 SHA、commit SHA。

**文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeDomainCode.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeTypeCode.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeVersionStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeItem.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeVersion.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeBinding.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/VideoTypeRule.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeItemMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeVersionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeBindingMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/VideoTypeRuleMapper.java`
- 创建：`docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteDomainRulesTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteMigrationIT.java`

- [ ] **步骤 1：先写反射领域测试和缺迁移 IT**

`KnowledgeLiteDomainRulesTest` 通过类名字符串断言四 Entity 的 `@TableName`、`BaseEntity` 父类、`@TableId` 和四 Mapper 的 `BaseMapperPlus<Entity, Entity>` 泛型；断言枚举值精确为：

```java
KnowledgeDomainCode: COPYWRITING("copywriting")
KnowledgeTypeCode: PRIMARY_TEMPLATE("primary_template"), WRITING_TECHNIQUE("writing_technique"),
    PSYCHOLOGY("psychology"), CASE("case"), MANDATORY_RULE("mandatory_rule")
KnowledgeVersionStatus: DRAFT("draft"), REVIEWING("reviewing"),
    PUBLISHED("published"), RETIRED("retired")
```

`KnowledgeLiteMigrationIT` 使用 `LocalIntegrationEnvironment.requireFromEnvironment()`，每个测试重建专用 schema、执行 `ry_vue.sql` 和 `20260728_01_p0a_identity_security.sql`，然后查找尚不存在的 K0 migration。首个 RED 必须因迁移文件或四表缺失失败，不得因环境绕过或测试编译失败。

- [ ] **步骤 2：运行两个 RED**

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
$mvn = Join-Path $root 'ai-video-api/mvnw.cmd'
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeLiteDomainRulesTest' test
if ($LASTEXITCODE -eq 0) { throw 'K0-2 领域 RED 必须失败' }
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' `
  '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' `
  '-Dit.test=KnowledgeLiteMigrationIT' verify
if ($LASTEXITCODE -eq 0) { throw 'K0-2 迁移 RED 必须失败' }
```

- [ ] **步骤 3：实现最小 Entity、枚举和 Mapper**

四个 Entity 均使用 `@Data`、`@EqualsAndHashCode(callSuper = true)`、`@TableName`、`@TableId(type = IdType.ASSIGN_ID)` 并继承 `BaseEntity`。Entity 只含下表字段，不放选择、摘要或写入业务方法：

| Entity | 精确业务字段（不重复列 BaseEntity 审计字段） |
| --- | --- |
| `KnowledgeItem` | `knowledgeItemId`, `domainCode`, `knowledgeTypeCode`, `stableCode`, `name`, `summary`, `tagsJson`, `currentPublishedVersionId`, `sourceType`, `sourceRef` |
| `KnowledgeVersion` | `knowledgeVersionId`, `knowledgeItemId`, `versionNo`, `status`, `content`, `structureJson`, `sourceSummary`, `reviewedBy`, `reviewedAt`, `publishedBy`, `publishedAt` |
| `KnowledgeBinding` | `knowledgeBindingId`, `bindingGroupCode`, `versionNo`, `knowledgeItemId`, `knowledgeVersionId`, `industryCode`, `purposeCode`, `videoTypeCode`, `angleCodesJson`, `anglePrioritiesJson`, `minDurationSeconds`, `maxDurationSeconds`, `priority`, `requiredFlag`, `requiredSlotCodesJson`, `audienceTagCodesJson`, `exclusionConditionsJson`, `status` |
| `VideoTypeRule` | `videoTypeRuleId`, `ruleCode`, `versionNo`, `videoTypeCode`, `industryCode`, `purposeCode`, `minDurationSeconds`, `maxDurationSeconds`, `requiredSlotCodesJson`, `priority`, `copyRulesJson`, `status`, `publishedAt` |

四个 Mapper 采用精确形式：

```java
public interface KnowledgeItemMapper extends BaseMapperPlus<KnowledgeItem, KnowledgeItem> {}
public interface KnowledgeVersionMapper extends BaseMapperPlus<KnowledgeVersion, KnowledgeVersion> {}
public interface KnowledgeBindingMapper extends BaseMapperPlus<KnowledgeBinding, KnowledgeBinding> {}
public interface VideoTypeRuleMapper extends BaseMapperPlus<VideoTypeRule, VideoTypeRule> {}
```

- [ ] **步骤 4：实现正式迁移和固定种子**

`20260803_01_p1_knowledge_lite.sql` 只创建 `av_knowledge_item`、`av_knowledge_version`、`av_knowledge_binding`、`av_video_type_rule`。字段与 Entity 一一对应；JSON 列使用 MySQL `JSON`，正文使用 `LONGTEXT`，状态/代码使用 `VARCHAR`，时间使用 `DATETIME`。必须包含：

- 条目 `stable_code` 唯一；版本 `(knowledge_item_id, version_no)` 唯一；绑定 `(binding_group_code, version_no)` 唯一；规则 `(rule_code, version_no)` 唯一。
- 版本、绑定到条目以及绑定到版本的外键。
- 绑定索引 `(status, industry_code, purpose_code, video_type_code, min_duration_seconds, max_duration_seconds, priority)`。
- 规则索引 `(status, industry_code, purpose_code, min_duration_seconds, max_duration_seconds, priority)`。
- `version_no > 0`、优先级 `-1000..1000`、时长同时为空或同时非空且 `0 < min <= max`、状态只允许四个固定值的检查约束。
- `current_published_version_id` 不建循环外键，但 IT 必须证明其指向同条目的 `published` 版本。
- 迁移不得包含 `DROP`、`TRUNCATE`，不得引用 02～04a、任务、额度、流水、草稿或工作区表。

固定种子版本为 `k0-seed-20260803-01`，使用固定 ID、固定 `published_at='2026-08-03 00:00:00'` 和不改写内容/更新时间的幂等插入。K0 不伪造尚未发布的行业/用途目录，所以生产种子全部使用显式 `*/*`；精确行业/用途的四层排序由 IT 专用 fixture 验证。四条知识与绑定精确为：

| 稳定代码 | tags JSON | 行业/用途 | 优先级 | 正文 |
| --- | --- | --- | ---: | --- |
| `global_benefit_hook` | `["hook"]` | `*/*` | 100 | `开头先给出具体利益点，再说明适用人群。` |
| `global_product_proof` | `["proof"]` | `*/*` | 80 | `正文必须使用用户提供且可验证的产品事实支撑卖点。` |
| `global_action_prompt` | `["cta"]` | `*/*` | 60 | `结尾给出一个明确、可执行且不过度承诺的行动。` |
| `global_claim_boundary` | `["compliance"]` | `*/*` | 40 | `不得虚构价格、活动、功效、销量或用户未提供的事实。` |

规则固定为：

| 规则代码 | 行业/用途 | 时长 | 优先级 | `copy_rules_json` |
| --- | --- | --- | ---: | --- |
| `short_20s_structure` | `*/*` | 1～20 秒 | 100 | `["20秒内：1句钩子+2句卖点+1句行动号召","禁止虚构价格或效果"]` |
| `standard_60s_structure` | `*/*` | 21～60 秒 | 90 | `["21至60秒：钩子、痛点、卖点、证据、行动号召依次展开","禁止虚构价格或效果"]` |

- [ ] **步骤 5：运行 GREEN、幂等和恢复验证**

`KnowledgeLiteMigrationIT` 必须覆盖：四表/列/索引/外键/检查约束；四知识、四版本、四绑定、两规则；二次执行记录数、正文、`update_time` 与规范种子 SHA 不变；删除并由夹具重建专用 schema 后，规范种子 SHA 与第一次相同；非本机地址、非 `ai_video_test`、非隔离 Redis 配置由 `LocalIntegrationEnvironmentTest` 拒绝。

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
$mvn = Join-Path $root 'ai-video-api/mvnw.cmd'
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dtest=KnowledgeLiteDomainRulesTest,LocalIntegrationEnvironmentTest' test
if ($LASTEXITCODE -ne 0) { throw 'K0-2 单元 GREEN 失败' }
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' `
  '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' `
  '-Dit.test=KnowledgeLiteMigrationIT' verify
if ($LASTEXITCODE -ne 0) { throw 'K0-2 迁移 GREEN 失败' }
```

- [ ] **步骤 6：精确暂存、提交并 push**

```powershell
git add -- `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeDomainCode.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeTypeCode.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeVersionStatus.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeItem.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeVersion.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeBinding.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/VideoTypeRule.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeItemMapper.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeVersionMapper.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeBindingMapper.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/VideoTypeRuleMapper.java `
  docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteDomainRulesTest.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteMigrationIT.java
git diff --cached --check
git commit -m "feat(p1): 建立 K0 知识表与只读种子"
git push origin codex/p1-knowledge
```

---

### K0-3：实现确定性查询和规范摘要

**最小任务卡**

- **单一目标：** 实现 `IKnowledgeContextService.resolve`，用四个 Mapper 返回确定性知识、规则和摘要。
- **不做：** 不写数据库、不加缓存、不调用 HTTP/模型/搜索、不读取登录态、不返回完整 P1 route/snapshot。
- **权威来源：** 本文件第 1 节、K0-1/K0-2 已审阅代码、RuoYi Mapper/Service 规则。
- **风险：** 红；确定性排序、内容摘要和下游生成输入会形成稳定事实。
- **依赖：** K0-2 commit 已 push 且工作区干净。
- **允许影响：** 仅下列 5 个文件；Mapper 接口和迁移不得在本卡改动。
- **成功/反向验收：** 精确/通用匹配、标签/优先级/稳定键排序、版本去重、规则排序、未发布排除、空结果、异常阻断和 SHA 金丝雀全部通过。
- **并发/审查：** B 单 writer；C 独立审查排序、摘要、失败语义和 N+1 风险。
- **固定输出：** 完成项、风险、验证证据、阻塞项、commit SHA。

**文件：**

- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/impl/KnowledgeContextServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextHashCanaryTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextQueryIT.java`

- [ ] **步骤 1：先写排序、摘要和失败 RED**

`KnowledgeContextServiceTest` 用 Mockito 构造四个 Mapper 返回的无序 fixture，至少包含：四层匹配、同层不同标签命中、同优先级稳定代码、同条目多版本、重复绑定、未发布版本、退役绑定、规则重复文案、空结果、损坏 JSON 和 Mapper 异常。`KnowledgeContextHashCanaryTest` 独立冻结下面的规范 JSON bytes、非空 SHA 和第 1 节空结果 SHA，禁止通过 Service 测试的 mock 排序间接替代摘要金丝雀。

```java
@Test
void producesTheFrozenCanonicalHash() {
    KnowledgeContextDTO result = serviceFromFixture().resolve(
        new KnowledgeContextRequestDTO("food", "store_traffic", 15, List.of("hook")));
    assertThat(result.knowledgeVersionIds()).containsExactly(2001L, 2002L);
    assertThat(result.excerpts()).containsExactly("先给利益点。", "用事实支撑卖点。");
    assertThat(result.copyRules()).containsExactly(
        "15秒：1句钩子+2句卖点+1句行动号召", "禁止虚构价格或效果");
    assertThat(result.contentHash())
        .isEqualTo("0e29ebac61ef88a724b0365743f9bed2db9aef7bbe40c94f8f92b79ae9863346");
}
```

首个 RED 通过仅含构造器和 `throw new UnsupportedOperationException("K0-3 RED")` 的可编译实现触发；不得用改错期望值制造 RED。

- [ ] **步骤 2：运行 RED**

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
& (Join-Path $root 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeContextServiceTest' test
if ($LASTEXITCODE -eq 0) { throw 'K0-3 RED 必须失败' }
```

- [ ] **步骤 3：实现最小 GREEN Service**

在 `pom.xml` 增加直接依赖 `ruoyi-common-json`，生产代码注入 Jackson `JsonMapper`；不要使用依赖 Spring 全局静态状态的 JSON helper。实现类固定为：

```java
@Service
public class KnowledgeContextServiceImpl implements IKnowledgeContextService {
    private final KnowledgeItemMapper itemMapper;
    private final KnowledgeVersionMapper versionMapper;
    private final KnowledgeBindingMapper bindingMapper;
    private final VideoTypeRuleMapper ruleMapper;
    private final JsonMapper jsonMapper;

    @Override
    public KnowledgeContextDTO resolve(KnowledgeContextRequestDTO request) {
        // 先批量读取可匹配 published 绑定/规则，再按 ID 批量读取条目和版本，
        // 在 Service 内按本计划固定 comparator 排序、去重并计算规范摘要。
    }
}
```

实现必须满足：

1. 只查 `published`；K0 绑定只接受 `video_type_code='*'`、`required_flag=0`、`angle_codes_json=[]`、`angle_priorities_json={}`、`required_slot_codes_json=[]`、`audience_tag_codes_json=[]`、`exclusion_conditions_json=[]`；K0 规则只接受 `required_slot_codes_json=[]`。时长为空表示全时长，否则必须包含目标时长。因为 K0 请求没有 videoType、slot、audience 或 exclusion 输入，任何带这些条件的记录都必须排除，不能忽略条件后误命中。
2. 绑定、条目、版本最多各一次批量查询，禁止按结果逐条查询。
3. 绑定的 `knowledgeVersionId` 必须属于同一 `knowledgeItemId`，且等于条目的 `currentPublishedVersionId`；不一致视为数据完整性错误并抛 `ServiceException`，不得静默选其他版本。
4. `tags_json` 与 `copy_rules_json` 必须是字符串数组；损坏、含空白元素或 published 正文为空时抛 `ServiceException`，错误消息不包含完整正文或用户产品资料。
5. 规范 JSON 使用 `LinkedHashMap` 固定三个键顺序、Jackson 紧凑 UTF-8 bytes、SHA-256 小写十六进制；hash 失败阻止返回。
6. 无匹配返回第 1 节固定空结果与摘要；数据库异常不得降级到硬编码内容。
7. 不记录完整正文；本实现无需业务日志。

- [ ] **步骤 4：写并运行本机查询 IT**

`KnowledgeContextQueryIT` 复用 `AppIdentityIsolationIT` 的最小 `MybatisSqlSessionFactoryBean + @MapperScan + AnnotationConfigApplicationContext` 模式，数据源只来自 `LocalIntegrationEnvironment`。每个测试重建专用 schema 并执行 `ry_vue.sql → 01 → knowledge-lite`，通过真实四 Mapper 和真实 Service 覆盖：

- `food/store_traffic` 的四层顺序；
- 同层标签命中、优先级和稳定代码排序；
- 15 秒与 30 秒规则；
- 未发布/退役排除；
- 重复绑定去重；
- 无匹配空结果；
- 相同请求连续两次字节等价和 hash 相同；
- published 指针错配、损坏 JSON 时 fail-closed（失败关闭）。

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
$mvn = Join-Path $root 'ai-video-api/mvnw.cmd'
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dtest=KnowledgeContextServiceTest,KnowledgeContextHashCanaryTest' test
if ($LASTEXITCODE -ne 0) { throw 'K0-3 单元 GREEN 失败' }
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' `
  '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' `
  '-Dit.test=KnowledgeContextQueryIT' verify
if ($LASTEXITCODE -ne 0) { throw 'K0-3 查询 IT GREEN 失败' }
```

- [ ] **步骤 5：精确暂存、提交并 push**

```powershell
git add -- `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/impl/KnowledgeContextServiceImpl.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextServiceTest.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextHashCanaryTest.java `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextQueryIT.java
git diff --cached --check
git commit -m "feat(p1): 实现 K0 确定性知识查询"
git push origin codex/p1-knowledge
```

---

### K0-4：边界门禁、fresh 证据和 candidate handoff

**最小任务卡**

- **单一目标：** 固定 K0 禁止边界，完成 fresh 单元/IT/XML/标准验证并形成经 C 独立 review 的 candidate handoff。
- **不做：** 不扩展运行时代码，不修建议项，不合并 `main`，不宣布完整 P1/F2 PASS。
- **权威来源：** 本文件全部验收、本分支三个 K0 commit、AI 协作治理规范。
- **风险：** 红；最终证据、独立审查和下游可消费声明。
- **依赖：** K0-3 commit 已 push 且工作区干净。
- **允许影响：** 仓库内只新增 1 个测试文件；handoff 写入当前 worktree Git metadata。
- **成功/反向验收：** 8 份 fresh XML 全绿，标准/范围/静态扫描通过，C 对同一 HEAD 给出独立 PASS 后才创建 handoff；`fullP1Ready` 必须是 JSON `false`。
- **并发/审查：** B 写测试和 candidate；C 对精确 HEAD 独立 review。无变更时只进行一轮完整 review；修复后只复核修复差异。
- **固定输出：** 完成项、风险、验证证据、阻塞项、远程分支、candidate SHA、C 需 review 的精确范围。

**文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteBoundaryTest.java`
- Git metadata：`git rev-parse --git-path p1-k0-candidate-handoff.json`（不 stage、不 commit、不 push）

- [ ] **步骤 1：先写边界 RED**

`KnowledgeLiteBoundaryTest` 扫描 `src/main/java/org/dromara/aivideo/knowledge` 和 K0 migration，断言：

- K0 三个稳定契约存在且只位于 core `knowledge/dto`、`knowledge/service`。
- K0 生产源码不导入 `LoginHelper`、`AppLoginHelper`、`StpUtil`、`RestClient`、`WebClient`、Feign、Spring AI 或供应商 SDK。
- K0 生产源码不引用 `av_ai_task`、quota、ledger、usage、script draft、workspace；迁移只出现四张 K0 表。
- `knowledge` 聚合不存在 `application`、`port`、`adapter`、`command`、`model` 平行业务包，不存在 Controller、BO、VO。
- K0 不导入或构造完整 P1 的 route/snapshot DTO，不伪造 revision/version/fact。

先写 `findForbiddenReferences(List<String> sources)` 的测试，传入含网络客户端和登录助手 import 的内存 fixture，扫描器未实现时明确 RED；随后在同一测试文件内实现扫描器，再用它扫描真实生产目录。不得为了 RED 向生产源码加入禁止依赖。

- [ ] **步骤 2：运行边界 GREEN，精确提交并 push**

```powershell
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
& (Join-Path $root 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeLiteBoundaryTest' test
if ($LASTEXITCODE -ne 0) { throw 'K0-4 边界 GREEN 失败' }
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteBoundaryTest.java
git diff --cached --check
git commit -m "test(p1): 固定 K0 边界与交付门禁"
git push origin codex/p1-knowledge
```

- [ ] **步骤 3：执行一次 final fresh 验证并保留 XML**

```powershell
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
$module = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$mvn = Join-Path $root 'ai-video-api/mvnw.cmd'
$started = [DateTime]::UtcNow
& $mvn -pl 'ruoyi-modules/ai-video/ai-video-core' -am clean verify `
  '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dfailsafe.failIfNoSpecifiedTests=false' `
  '-Dtest=KnowledgeContractTest,KnowledgeContextContractTest,KnowledgeLiteDomainRulesTest,KnowledgeContextServiceTest,KnowledgeContextHashCanaryTest,KnowledgeLiteBoundaryTest' `
  '-Dit.test=KnowledgeLiteMigrationIT,KnowledgeContextQueryIT' `
  '-Pdev,local-integration-test'
if ($LASTEXITCODE -ne 0) { throw 'K0 final Maven 验证失败' }

$reports = @(
  'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContractTest.xml',
  'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextContractTest.xml',
  'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeLiteDomainRulesTest.xml',
  'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextServiceTest.xml',
  'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextHashCanaryTest.xml',
  'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeLiteBoundaryTest.xml',
  'target/failsafe-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeLiteMigrationIT.xml',
  'target/failsafe-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextQueryIT.xml'
)
foreach ($relative in $reports) {
  $path = Join-Path $module $relative
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "缺少 XML：$relative" }
  if ((Get-Item -LiteralPath $path).LastWriteTimeUtc -lt $started.AddSeconds(-2)) { throw "XML 不 fresh：$relative" }
  [xml]$xml = Get-Content -LiteralPath $path -Raw
  $suite = $xml.testsuite
  if ([int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0) {
    throw "XML 非全绿：$relative"
  }
}

powershell -ExecutionPolicy Bypass -File (Join-Path $root 'scripts/validate-development-standards.ps1')
if ($LASTEXITCODE -ne 0) { throw '开发规范验证失败' }
git diff --check origin/main...HEAD
if ($LASTEXITCODE -ne 0) { throw 'diff 格式验证失败' }
if (@(git status --porcelain).Count -ne 0) { throw 'candidate 工作区不干净' }
```

预期：Maven exit 0；8 份 XML 均 fresh 且 `failures=0/errors=0/skipped=0`；输出 `DEVELOPMENT_STANDARDS_OK`；工作区干净。不得在此命令之后执行 `mvn clean` 或删除报告。

- [ ] **步骤 4：核对精确变更范围和原 Task 1 SHA**

```powershell
$allowed = @(
  'docs/DOMAIN_MODEL.md',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml',
  'docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgePlanDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeRouteRequestDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeRouteResultDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeSnapshotRequestDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeSnapshotDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeRoutingService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeSnapshotService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContractTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeContextRequestDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeContextDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeContextService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextContractTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeDomainCode.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeTypeCode.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/KnowledgeVersionStatus.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeItem.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeVersion.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/KnowledgeBinding.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/domain/VideoTypeRule.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeItemMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeVersionMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/KnowledgeBindingMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/mapper/VideoTypeRuleMapper.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteDomainRulesTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteMigrationIT.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/impl/KnowledgeContextServiceImpl.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextServiceTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextHashCanaryTest.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeContextQueryIT.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeLiteBoundaryTest.java'
)
$actual = @(git diff --name-only origin/main...HEAD)
$outside = @($actual | Where-Object { $_ -notin $allowed })
if ($outside.Count -ne 0) { throw "存在越界文件：$($outside -join ', ')" }
$missing = @($allowed | Where-Object { $_ -notin $actual })
if ($missing.Count -ne 0) { throw "缺少计划文件：$($missing -join ', ')" }

$original = 'f968ca4364c570169fad080664459f47e6495b12'
$stableOriginalFiles = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgePlanDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeRouteRequestDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeRouteResultDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeSnapshotRequestDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto/KnowledgeSnapshotDTO.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeRoutingService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/service/IKnowledgeSnapshotService.java'
)
foreach ($path in $stableOriginalFiles) {
  $originalBlob = (git rev-parse "${original}:$path").Trim()
  $liveBlob = (git hash-object -- $path).Trim()
  if ($originalBlob -ne $liveBlob) { throw "原 P1 稳定源码发生字节漂移：$path" }
}
git cat-file -e "${original}^{commit}"
git merge-base --is-ancestor $original HEAD
if ($LASTEXITCODE -ne 0) { throw '原 P1 Task 1 candidate 未保留为祖先' }
$mainHead = (git rev-parse origin/main).Trim()
git merge-base --is-ancestor $mainHead HEAD
if ($LASTEXITCODE -ne 0) { throw '最新 origin/main 不是 candidate 祖先' }
$candidateHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse origin/codex/p1-knowledge).Trim()
if ($candidateHead -ne $remoteHead) { throw '远程功能分支不是当前 candidate HEAD' }
```

- [ ] **步骤 5：开发 C 独立 review**

C 只审查并验证同一远程 HEAD：

1. K0-1 两个新 DTO、一个新 Service、原五 DTO/两个 Service 零漂移；
2. K0-2 四表、种子、幂等、恢复和迁移不依赖 P0-C；
3. K0-3 四层匹配、排序、去重、空结果、异常阻断和两枚固定 SHA；
4. K0-4 禁止网络/模型/登录/P0-C/HTTP/前端边界和 8 份 fresh XML；
5. 原 Task 1 SHA 仍为祖先，所有 K0 变化均为新提交；
6. `fullP1Ready=false`，K0 PASS 不等于完整 P1/F2 PASS。

C 的结论只能是 `[必须修复]`、`[建议修改]`、`[仅供参考]`，并给出 reviewed HEAD、命令、XML 和 PASS/FAIL。B 只能报告 candidate；没有 C 的同 HEAD 独立 PASS 时，handoff 保持阻塞。

- [ ] **步骤 6：在 C PASS 后创建不可变 candidate handoff**

执行者先把 C 评审原文中的 HEAD 和评审记录地址分别放入环境变量 `P1_K0_REVIEWED_HEAD`、`P1_K0_REVIEW_REFERENCE`，再运行下列脚本。脚本从 live 文件重算所有 SHA，验证 `reviewedHead == candidateHead == origin/codex/p1-knowledge`，以 `CreateNew` 创建或幂等回读，绝不覆盖不同内容。

```powershell
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((git rev-parse --show-toplevel).Trim())
$candidateHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse origin/codex/p1-knowledge).Trim()
$baseMainHead = (git rev-parse origin/main).Trim()
$reviewedHead = [Environment]::GetEnvironmentVariable('P1_K0_REVIEWED_HEAD')
$reviewReference = [Environment]::GetEnvironmentVariable('P1_K0_REVIEW_REFERENCE')
if ($reviewedHead -notmatch '^[0-9a-f]{40}$' -or $reviewedHead -ne $candidateHead -or $remoteHead -ne $candidateHead) {
  throw 'C reviewed HEAD、当前 HEAD 和远程 HEAD 必须完全相同'
}
if ([string]::IsNullOrWhiteSpace($reviewReference)) { throw '缺少开发 C 的实际评审记录引用' }
git merge-base --is-ancestor $baseMainHead $candidateHead
if ($LASTEXITCODE -ne 0) { throw 'baseMainHead 不是 candidate 祖先' }

function Get-LowerSha256([string]$path) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "证据文件不存在：$path" }
  return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
}

$core = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$source = Join-Path $core 'src/main/java/org/dromara/aivideo/knowledge'
$migrationRelative = 'docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql'
$reports = [ordered]@{
  KnowledgeContractTest = 'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContractTest.xml'
  KnowledgeContextContractTest = 'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextContractTest.xml'
  KnowledgeLiteDomainRulesTest = 'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeLiteDomainRulesTest.xml'
  KnowledgeContextServiceTest = 'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextServiceTest.xml'
  KnowledgeContextHashCanaryTest = 'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextHashCanaryTest.xml'
  KnowledgeLiteBoundaryTest = 'target/surefire-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeLiteBoundaryTest.xml'
  KnowledgeLiteMigrationIT = 'target/failsafe-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeLiteMigrationIT.xml'
  KnowledgeContextQueryIT = 'target/failsafe-reports/TEST-org.dromara.aivideo.knowledge.KnowledgeContextQueryIT.xml'
}
$evidence = [ordered]@{}
foreach ($entry in $reports.GetEnumerator()) {
  $evidence[$entry.Key] = Get-LowerSha256 (Join-Path $core $entry.Value)
}

$rawPath = (git rev-parse --git-path p1-k0-candidate-handoff.json).Trim()
$handoff = if ([IO.Path]::IsPathRooted($rawPath)) {
  [IO.Path]::GetFullPath($rawPath)
} else {
  [IO.Path]::GetFullPath((Join-Path $root $rawPath))
}
$capturedAtUtc = [DateTime]::UtcNow.ToString('o')
$existingJson = $null
if (Test-Path -LiteralPath $handoff -PathType Leaf) {
  $existingJson = Get-Content -LiteralPath $handoff -Raw
  try {
    $existingPayload = $existingJson | ConvertFrom-Json -ErrorAction Stop
  } catch {
    throw '已有 handoff 不是有效 JSON，禁止覆盖'
  }
  if ([string]::IsNullOrWhiteSpace([string]$existingPayload.capturedAtUtc)) {
    throw '已有 handoff 缺少 capturedAtUtc，禁止覆盖'
  }
  $capturedAtUtc = [string]$existingPayload.capturedAtUtc
}

$payload = [ordered]@{
  schemaVersion = 'p1-k0-candidate-handoff-1'
  candidateHead = $candidateHead
  baseMainHead = $baseMainHead
  originalP1Task1CandidateHead = 'f968ca4364c570169fad080664459f47e6495b12'
  migrationPath = $migrationRelative
  migrationSha256 = Get-LowerSha256 (Join-Path $root $migrationRelative)
  stableContractSourceSha256 = [ordered]@{
    KnowledgeContextRequestDTO = Get-LowerSha256 (Join-Path $source 'dto/KnowledgeContextRequestDTO.java')
    KnowledgeContextDTO = Get-LowerSha256 (Join-Path $source 'dto/KnowledgeContextDTO.java')
    IKnowledgeContextService = Get-LowerSha256 (Join-Path $source 'service/IKnowledgeContextService.java')
  }
  seedVersion = 'k0-seed-20260803-01'
  evidenceXmlSha256 = $evidence
  owner = 'developer-b'
  reviewer = 'developer-c'
  reviewStatus = 'PASS'
  reviewedHead = $reviewedHead
  reviewReference = $reviewReference.Trim()
  fullP1Ready = $false
  capturedAtUtc = $capturedAtUtc
}
$json = $payload | ConvertTo-Json -Depth 6
if ($null -ne $existingJson) {
  if ($existingJson -ne $json) { throw '已有 handoff 内容不同，禁止覆盖' }
} else {
  $stream = [IO.FileStream]::new($handoff, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try {
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try { $writer.Write($json); $writer.Flush() } finally { $writer.Dispose() }
  } finally {
    if ($null -ne $stream) { $stream.Dispose() }
  }
}
if ((Get-Content -LiteralPath $handoff -Raw) -ne $json) { throw 'handoff 回读不一致' }
Write-Output 'P1_K0_CANDIDATE_HANDOFF_OK'
```

## 3. K0 退出条件

只有以下条件全部满足，K0 才能作为数字人文案模块的候选依赖：

- 四张任务卡顺序完成，每卡有独立小提交并已 push 到 `origin/codex/p1-knowledge`；
- 原完整 P1 Task 1 commit `f968ca4` 未改写且仍为 candidate 祖先；
- 本机 `ai_video_test` 完成 `ry_vue.sql → 01 → knowledge-lite`、重复迁移和重建恢复验证；
- 相同请求/数据得到相同顺序、规范 JSON 和 SHA；空结果摘要固定；
- 无模型、搜索、IndexTTS2、ComfyUI、HTTP、前端、任务、额度、流水、草稿、共享或组织代码；
- 开发 C 对精确 candidate HEAD 独立 PASS；
- `p1-k0-candidate-handoff.json` 回读一致且 `fullP1Ready=false`；
- 项目负责人另行批准合并前，任何人不得把该分支直接 push 或 merge 到 `main`。
