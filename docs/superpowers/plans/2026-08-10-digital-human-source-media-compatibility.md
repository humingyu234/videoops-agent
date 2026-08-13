# 数字人底片建项兼容性实现计划

> **面向 AI 代理的工作者：** 在当前会话内按 TDD 小步执行；工作区已有用户改动，只修改本计划列出的文件，不创建提交。

**目标：** 允许尺寸或帧率不同于时间轴输出画布的有效数字人底片建项，同时继续生成固定 `1080×1920 / 30fps` 的时间轴项目。

**架构：** `DigitalHumanCreationSourceDTO` 中的宽高和帧率是源媒体探测事实，只用于判断媒体是否有效；`CreationProject` 和初始 `TimelineDocumentDTO` 的画布值继续来自 `TimelineContractLimits`。主视频沿用 `fitMode=cover` 适配固定竖屏画布，不更改 API 字段、权限、任务、额度或文件归属规则。

**技术栈：** Java 21、Spring Boot、JUnit 5、Mockito、AssertJ、Maven。

---

### 任务 1：用回归测试冻结兼容行为

**文件：**

- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImplTest.java`

- [x] **步骤 1：把成功来源夹具改为真实失败规格**

```java
private DigitalHumanCreationSourceDTO source() {
    return new DigitalHumanCreationSourceDTO(
        "99", "501", "502", "server snapshot", 3_000L, 768, 1024, 25, List.of());
}
```

现有成功用例继续断言持久化项目与初始草稿画布为 `1080×1920 / 30fps`。

- [x] **步骤 2：保留损坏媒体防线**

将原先以横竖屏不匹配为“畸形来源”的测试改为宽度为 `0` 的来源，并继续断言 `CREATION_SOURCE_INVALID` 且不写项目或草稿。

- [x] **步骤 3：运行单测确认红灯**

运行：

```powershell
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dtest=CreationProjectServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

工作目录：`ai-video-api`

预期：成功建项用例因当前固定规格比较返回 `CREATION_SOURCE_INVALID` 而失败。

### 任务 2：分离源媒体事实与时间轴画布

**文件：**

- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/impl/CreationProjectServiceImpl.java`

- [x] **步骤 1：改为有效性校验而非固定规格比较**

```java
|| source.width() <= 0
|| source.height() <= 0
|| source.frameRate() <= 0
```

- [x] **步骤 2：建项时使用固定输出画布**

```java
return new SourceSpec(source.sourceId(), source.baseVideoAssetId(), source.primaryAudioAssetId(),
    source.scriptTextSnapshot(), source.durationMs(),
    TimelineContractLimits.NUMERIC_LIMITS.get("canvasWidth").intValue(),
    TimelineContractLimits.NUMERIC_LIMITS.get("canvasHeight").intValue(),
    TimelineContractLimits.NUMERIC_LIMITS.get("canvasFrameRate").intValue());
```

这样只去掉源媒体必须与画布相等的限制，不放宽时间轴文档和最终成品的竖屏输出契约。

- [x] **步骤 3：运行专项单测确认绿灯**

运行任务 1 的 Maven 命令。

预期：`CreationProjectServiceImplTest` 全部通过。

### 任务 3：同步契约说明并完成验证

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 验证：`scripts/validate-development-standards.ps1`

- [x] **步骤 1：澄清建项媒体规则**

在 `POST /api/studio/creation-projects` 说明中补充：源视频探测宽高与帧率必须为正，但不要求等于项目画布；项目始终使用 `1080×1920 / 30fps`，主视频以 `cover` 适配。

- [x] **步骤 2：运行受影响模块测试和规范校验**

```powershell
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dtest=CreationProjectServiceImplTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
..\scripts\validate-development-standards.ps1
```

- [x] **步骤 3：构建用户 API 并做真实请求回归**

```powershell
.\mvnw.cmd -pl ai-video-user-api -am -DskipTests package
```

重启本地 `8081` 用户 API 后，使用原来源任务 `2086723866159394818` 与原幂等键重放 `POST /api/studio/creation-projects`。预期 `code=200`，返回项目画布 `1080×1920 / 30fps`，随后项目详情与草稿可读取。

- [x] **步骤 4：差异审查**

检查 `git diff --check`，并由现有只读审查代理只复核本次差异：源媒体固定规格限制已移除、非正媒体事实仍拒绝、画布契约未放宽、权限与文件归属未变化。
