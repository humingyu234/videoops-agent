# 用户自有声音直接删除实现计划

> **For Codex:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. 每个任务严格执行红灯—绿灯—重构；不要把当前工作树中与本计划无关的改动纳入提交。

**目标：** 在用户端声音库中实现单条永久删除。用户只能删除当前租户、当前工作区、当前账号下自己的 `origin|clone` 声音；公共声音和其他归属的声音不可删除；声音与独占音频资产在同一数据库事务内逻辑删除，事务提交后尝试物理清理存储对象。

**架构：** 保持 RuoYi-Vue-Plus 现有 Controller → Service → `BaseMapperPlus` 分层，不新增 BO、VO、DTO、Repository 或平行业务层。Controller 只声明路由、创作端权限、操作日志和 `R<Void>`；`VoiceServiceImpl` 负责归属校验、条件逻辑删除与事务编排；`AssetServiceImpl` 提供声音删除专用的“事务内资产墓碑 + after-commit 对象清理”方法。前端由模块 API 封装 DELETE，`VoiceLibraryView` 独占确认流、请求锁和本地 tombstone，`VoiceCard` 仅渲染入口。

**技术栈：** Java 17、Spring Boot、Sa-Token、MyBatis-Plus、RuoYi-Vue-Plus、MySQL、JUnit 5、Mockito；React 19、TypeScript、Ant Design 6 `Modal.confirm`、Vitest、Testing Library、Biome。

**规格输入：** `docs/superpowers/specs/2026-08-04-voice-owned-delete-design.md`

**风险和收口：** 红色高风险，涉及不可逆数据删除、文件资产、权限和公共 API。最多一名实现者；实现完成后仅进行一次独立安全/数据专项审查，修复后只做定向复核。不得扩展引用检查、回收站、恢复、批量删除、额度、任务中心或持久化清理任务。

**公共契约判断：** 本次不修改 `docs/API_CONTRACT.md` 或 `docs/DOMAIN_MODEL.md`。原因是没有改变公共响应 envelope、共享字段、领域状态或跨模块枚举；新增的模块路由、权限、错误和竞态语义已由上述声音删除规格完整冻结。若实现时发现必须新增共享字段或状态，立即停止并先更新公共契约，不得在代码中局部自造。

---

## Task 1：后端声音与资产原子删除

**任务卡**

- 目标：以单个事务逻辑删除自有声音及其声音资产，提交后尝试删除 OSS/本地对象。
- 允许修改：`ai-video-core` 的声音/资产 Service 及对应单元测试。
- 禁止修改：现有 `deleteOwnedAsset` 语义、其他素材删除流程、Worker 协议、状态枚举。
- 反向场景：无权限、非法 ID、公共声音、他人/跨工作区、重复删除、资产条件删除失败均不能产生越权或半提交。

**Files:**

- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IVoiceService.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImpl.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImplTest.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/service/impl/AssetServiceImplTest.java`

### Step 1：先写声音删除失败测试

在 `VoiceServiceImplTest` 的 principal 权限中加入 `aivideo:voice:delete`，并让测试声音明确带 `voiceType="origin"`、`delFlag="0"`。新增测试至少覆盖：

```java
@Test
void deleteOwnedVoiceDeletesVoiceThenTombstonesItsVoiceAsset() {
    Voice existing = ownedVoice("origin", "0", 91L);
    when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
    when(voiceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

    service.deleteOwnedVoice("42", principal);

    verify(voiceMapper).delete(argThat(this::isOwnedOriginOrCloneDelete));
    verify(assetService).tombstoneOwnedVoiceAssetAndPurgeAfterCommit("91", principal);
}

@Test
void deleteOwnedVoiceMasksPublicAndMissingRowsAsNotFound() {
    when(voiceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

    assertThatThrownBy(() -> service.deleteOwnedVoice("42", principal))
        .isInstanceOf(ServiceException.class)
        .extracting("code").isEqualTo(46401);
    verify(voiceMapper, never()).delete(any(Wrapper.class));
}

@Test
void deleteOwnedVoiceReturnsNotFoundWhenConditionalDeleteLosesRace() {
    when(voiceMapper.selectOne(any(LambdaQueryWrapper.class)))
        .thenReturn(ownedVoice("origin", "0", 91L));
    when(voiceMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

    assertThatThrownBy(() -> service.deleteOwnedVoice("42", principal))
        .isInstanceOf(ServiceException.class)
        .extracting("code").isEqualTo(46401);
    verifyNoInteractions(assetService);
}
```

再补三组独立断言：缺少删除权限时 Mapper 零调用；`""`、`"abc"`、溢出 Long 的 ID 均返回 `46401`；SQL wrapper 同时包含 `voice_id`、`tenant_id`、`workspace_id`、`owner_id`、`del_flag=0` 和 `voice_type IN ('origin','clone')`。

### Step 2：运行测试确认红灯

Run:

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -DskipITs -Dtest=VoiceServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，原因是 `deleteOwnedVoice`、删除权限或资产专用方法尚不存在；不得因测试未被发现而显示假绿。

### Step 3：添加最小 Service 契约和条件删除实现

在接口中增加：

```java
void deleteOwnedVoice(String voiceId, AppPrincipalSnapshotDTO principal);
```

在 `VoiceServiceImpl` 中使用项目现有的 ID 解析、权限检查和异常构造风格，实现等价于：

```java
private static final String VOICE_DELETE_PERMISSION = "aivideo:voice:delete";
private static final List<String> DELETABLE_VOICE_TYPES = List.of("origin", "clone");

@Override
@Transactional(rollbackFor = Exception.class)
public void deleteOwnedVoice(String voiceId, AppPrincipalSnapshotDTO principal) {
    requirePermission(principal, VOICE_DELETE_PERMISSION);
    Long id = parseVoiceIdOrNotFound(voiceId);
    LambdaQueryWrapper<Voice> scope = ownedDeletableWrapper(id, principal);
    Voice voice = voiceMapper.selectOne(scope);
    if (voice == null) {
        throw voiceNotFound();
    }
    if (voiceMapper.delete(ownedDeletableWrapper(id, principal)) != 1) {
        throw voiceNotFound();
    }
    assetService.tombstoneOwnedVoiceAssetAndPurgeAfterCommit(
        String.valueOf(voice.getAssetId()), principal);
}
```

注意事项：

- 查和删都必须带完整归属、`del_flag=0` 和可删类型；不能先查后 `deleteById`。
- `public` 与不存在/越权统一 `46401`，不可暴露存在性。
- 资产逻辑删除失败必须抛异常，使声音逻辑删除回滚。
- 不新增 `deleting` 状态，不等待转写租约结束。

### Step 4：先写资产 after-commit 失败测试

在 `AssetServiceImplTest` 新增：

```java
@Test
void tombstoneVoiceAssetDeletesDatabaseFirstAndObjectOnlyAfterCommit() {
    AssetFile asset = ownedVoiceAsset(91L, "voices/7/sample.mp3");
    when(assetMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(asset);
    when(assetMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
    TransactionSynchronizationManager.initSynchronization();
    try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
        factory.when(OssFactory::instance).thenReturn(ossClient);

        service.tombstoneOwnedVoiceAssetAndPurgeAfterCommit("91", principal);

        verify(assetMapper).delete(any(LambdaQueryWrapper.class));
        verify(ossClient, never()).delete(anyString());
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(TransactionSynchronization::afterCommit);
        verify(ossClient).delete("voices/7/sample.mp3");
    } finally {
        TransactionSynchronizationManager.clearSynchronization();
    }
}
```

再新增：无活动事务同步时直接失败且不改 DB；非 `voice` category 拒绝；条件删除影响 0 行时失败且不注册 callback；after-commit OSS 删除抛异常时 callback 吞掉异常且数据库事实不回滚。

### Step 5：运行资产测试确认红灯

Run:

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -DskipITs -Dtest=AssetServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，缺少声音删除专用资产方法。

### Step 6：实现事务内墓碑和提交后清理

在 `IAssetService` 添加：

```java
void tombstoneOwnedVoiceAssetAndPurgeAfterCommit(
    String assetId, AppPrincipalSnapshotDTO principal);
```

在 `AssetServiceImpl` 添加 `@Slf4j`，实现下列顺序：

```java
@Override
public void tombstoneOwnedVoiceAssetAndPurgeAfterCommit(
        String assetId, AppPrincipalSnapshotDTO principal) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()
            || !TransactionSynchronizationManager.isActualTransactionActive()) {
        throw new IllegalStateException("voice asset deletion requires an active transaction");
    }
    AssetFile asset = requireOwnedVoiceAssetForDelete(assetId, principal);
    if (assetMapper.delete(ownedVoiceAssetDeleteWrapper(asset.getAssetId(), principal)) != 1) {
        throw assetNotFound();
    }
    String objectKey = asset.getObjectKey();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            try {
                OssFactory.instance().delete(objectKey);
            } catch (RuntimeException ex) {
                log.error("voice asset object purge failed: assetId={}, tenantId={}, workspaceId={}, ownerId={}, errorType={}",
                    asset.getAssetId(), principal.tenantId(), principal.workspaceId(), principal.userId(),
                    ex.getClass().getSimpleName());
            }
        }
    });
}
```

具体异常和 getter 名称按现有类型调整，但必须满足：

- 资产查询和条件删均带 `asset_id + tenant + workspace + owner + category=voice_sample + del_flag=0`；该值与 `docs/DOMAIN_MODEL.md` 和现有上传写入契约一致。
- 保留逻辑删除资产行的 `objectKey`，它是人工清理证据。
- 日志只含稳定 ID 和异常类型，不含音频、文案、签名 URL、凭据或完整内部路径。
- 不改现有 `deleteOwnedAsset` 的“物理优先”语义，以免破坏上传失败清理和人物流程。
- 本版本没有崩溃后自动重试保证；不要新增异步任务。

### Step 7：补转写领取的删除条件回归测试

在 `VoiceServiceImplTest` 为 `claimNext` 增加 wrapper 捕获断言，确保候选查询和候选租约更新都显式带 `del_flag=0`。现有成功/失败写回已经带该条件，不改变它们，只保留回归测试。

实现时仅在缺失的候选租约 update wrapper 加：

```java
.eq(Voice::getDelFlag, "0")
```

### Step 8：运行后端核心测试并提交

Run:

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -DskipITs -Dtest=VoiceServiceImplTest,AssetServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；输出明确包含两个测试类且 0 failure/0 error。

Commit（只提交本任务文件；保留工作树其他改动）：

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/IVoiceService.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImpl.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/service/impl/VoiceServiceImplTest.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/service/impl/AssetServiceImplTest.java
git commit -m "feat(声音): 实现自有声音原子删除"
```

---

## Task 2：用户端路由、权限字典和迁移安全

**任务卡**

- 目标：暴露唯一用户端 DELETE 路由并以幂等、冲突即失败的 SQL 授权个人创作者。
- 允许修改：声音 Controller、Controller/Schema 合约测试、单个新权限迁移和本地 IT。
- 禁止修改：运营端路由、现有四项声音权限、无关角色、全局 envelope。
- 反向场景：错误端 Token、缺权限、重复 DELETE、迁移 ID/编码冲突、迁移执行两次。

**Files:**

- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/controller/VoiceController.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/voice/controller/VoiceControllerContractTest.java`
- Modify: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceSchemaContractTest.java`
- Create: `docs/sql/ai-video/mysql/20260804_01_voice_delete_permission.sql`
- Create: `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceDeletePermissionMigrationIT.java`

### Step 1：写 Controller 契约红灯测试

通过反射定位删除方法并断言：

```java
Method method = VoiceController.class.getDeclaredMethod(
    "deleteVoice", String.class, HttpServletRequest.class);
assertThat(method.getAnnotation(DeleteMapping.class).value())
    .containsExactly("/api/voices/{voiceId}");
assertThat(method.getAnnotation(RepeatSubmit.class)).isNull();
assertThat(method.getAnnotation(SaCheckPermission.class).value())
    .containsExactly("aivideo:voice:delete");
assertThat(method.getAnnotation(SaCheckPermission.class).type()).isEqualTo("app");
assertThat(method.getAnnotation(Log.class).businessType()).isEqualTo(BusinessType.DELETE);
```

还要保持启动边界断言：该 Controller 只由 `ai-video-user-api` 扫描，`ruoyi-admin` 不暴露 `/api/voices/{voiceId}`。

Run:

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -DskipITs -Dtest=VoiceControllerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，路由尚不存在。

### Step 2：实现最薄 Controller

使用与现有声音接口相同的 principal snapshot 解析方式：

```java
@Log(title = "用户声音", businessType = BusinessType.DELETE)
@SaCheckPermission(value = "aivideo:voice:delete", type = "app")
@DeleteMapping("/api/voices/{voiceId}")
public R<Void> deleteVoice(
        @PathVariable String voiceId, HttpServletRequest request) {
    voiceService.deleteOwnedVoice(voiceId, appPrincipalResolver.require(request));
    return R.ok();
}
```

不要加 `@RepeatSubmit`；重复请求必须进入 Service，由条件删除使第一个成功、后续稳定返回 `46401`。

### Step 3：先写权限迁移静态契约和真实 IT

在 `VoiceSchemaContractTest` 断言新文件包含精确值：

```text
permission_id = 1000024
permission_code = aivideo:voice:delete
role_permission.id = 1000224
role_id = 1000101
role_code = personal_creator
```

创建 `VoiceDeletePermissionMigrationIT`，使用 `LocalIntegrationEnvironment.requireFromEnvironment()` 和本机专用 `ai_video_test` 数据库。测试流程：

1. `resetDedicatedMySqlSchema()`。
2. 用 Spring `ScriptUtils` 依次执行 `ry_vue.sql`、身份迁移、现有声音迁移、新删除权限迁移。
3. 第二次执行新迁移。
4. 断言权限和绑定各恰好 1 行；角色/有效用户 revision 只递增一次。
5. 重置后预置冲突的 permission ID 或 permission code，再执行迁移；断言迁移失败且未改写冲突行、未创建绑定。

```java
@Tag("dev")
class VoiceDeletePermissionMigrationIT {
    private static final LocalIntegrationEnvironment ENV =
        LocalIntegrationEnvironment.requireFromEnvironment();

    @BeforeEach
    void resetSchema() throws Exception {
        ENV.resetDedicatedMySqlSchema();
    }
}
```

### Step 4：运行迁移测试确认红灯

静态测试：

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -DskipITs -Dtest=VoiceSchemaContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，新迁移缺失。

真实 IT 默认读取用户端 `application-dev.yml`，本机 MySQL/Redis 可用时运行；七个 `AI_VIDEO_IT_*` 变量仅用于可选覆盖：

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Pdev,local-integration-test -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=VoiceDeletePermissionMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: 当前先 FAIL；严禁改用 Docker、容器、WSL、远程数据库或 `FLUSHALL` 规避本地测试约束。

### Step 5：实现幂等且 fail-closed 的 SQL

创建 `20260804_01_voice_delete_permission.sql`，使用事务和临时 guard 表，不依赖 `DELIMITER`，以保证 `ScriptUtils` 可执行：

```sql
START TRANSACTION;

CREATE TEMPORARY TABLE tmp_voice_delete_guard (
    guard_id TINYINT PRIMARY KEY,
    valid_value TINYINT NOT NULL,
    CONSTRAINT ck_voice_delete_guard CHECK (valid_value = 1)
);

INSERT INTO tmp_voice_delete_guard (guard_id, valid_value)
SELECT 1, CASE WHEN
    (
        SELECT COUNT(*) FROM app_role
        WHERE role_id = 1000101 OR role_code = 'personal_creator'
    ) = 1
    AND (
        SELECT COUNT(*) FROM app_role
        WHERE role_id = 1000101 AND role_code = 'personal_creator'
          AND scope_type = 'personal' AND status = 'active' AND del_flag = '0'
    ) = 1
    AND (
        (SELECT COUNT(*) FROM app_permission
         WHERE permission_id = 1000024 OR permission_code = 'aivideo:voice:delete') = 0
        OR (
            (SELECT COUNT(*) FROM app_permission
             WHERE permission_id = 1000024 OR permission_code = 'aivideo:voice:delete') = 1
            AND (SELECT COUNT(*) FROM app_permission
                 WHERE permission_id = 1000024
                   AND permission_code = 'aivideo:voice:delete'
                   AND permission_name = '声音删除'
                   AND resource_type = 'voice' AND action = 'delete'
                   AND permission_revision = 1 AND status = 'active') = 1
        )
    )
    AND (
        (SELECT COUNT(*) FROM app_role_permission
         WHERE id = 1000224 OR (role_id = 1000101 AND permission_id = 1000024)) = 0
        OR (
            (SELECT COUNT(*) FROM app_role_permission
             WHERE id = 1000224 OR (role_id = 1000101 AND permission_id = 1000024)) = 1
            AND (SELECT COUNT(*) FROM app_role_permission
                 WHERE id = 1000224 AND role_id = 1000101
                   AND permission_id = 1000024 AND status = 'active') = 1
        )
    )
THEN 1 ELSE 0 END;

INSERT INTO app_permission (
    permission_id, permission_code, permission_name, resource_type, action,
    permission_revision, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
)
SELECT 1000024, 'aivideo:voice:delete', '声音删除', 'voice', 'delete',
       1, 'active', 'sys_user', 1761100000000000001,
       'sys_user', 1761100000000000001, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM app_permission
    WHERE permission_id = 1000024 AND permission_code = 'aivideo:voice:delete'
);

INSERT INTO app_role_permission (
    id, role_id, permission_id, status, created_by_type, created_by_id,
    updated_by_type, updated_by_id, create_time, update_time
)
SELECT 1000224, 1000101, 1000024, 'active',
       'sys_user', 1761100000000000001,
       'sys_user', 1761100000000000001, NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM app_role_permission
    WHERE id = 1000224 AND role_id = 1000101 AND permission_id = 1000024
);

SET @voice_delete_binding_inserted = ROW_COUNT();

UPDATE app_role
SET role_revision = role_revision + 1
WHERE @voice_delete_binding_inserted = 1
  AND role_id = 1000101 AND role_code = 'personal_creator'
  AND status = 'active' AND del_flag = '0';

UPDATE app_user u
JOIN (
    SELECT DISTINCT user_id
    FROM app_user_role
    WHERE role_id = 1000101 AND status = 'active'
      AND (valid_from IS NULL OR valid_from <= NOW())
      AND (valid_until IS NULL OR valid_until > NOW())
) ur ON ur.user_id = u.user_id
SET u.permission_revision = u.permission_revision + 1
WHERE @voice_delete_binding_inserted = 1
  AND u.status = 'active' AND u.del_flag = '0';

INSERT INTO tmp_voice_delete_guard (guard_id, valid_value)
SELECT 2, CASE WHEN
    (SELECT COUNT(*) FROM app_permission
     WHERE permission_id = 1000024
       AND permission_code = 'aivideo:voice:delete'
       AND permission_name = '声音删除'
       AND resource_type = 'voice' AND action = 'delete'
       AND permission_revision = 1 AND status = 'active') = 1
    AND (SELECT COUNT(*) FROM app_role_permission
         WHERE id = 1000224 AND role_id = 1000101
           AND permission_id = 1000024 AND status = 'active') = 1
THEN 1 ELSE 0 END;

DROP TEMPORARY TABLE tmp_voice_delete_guard;
COMMIT;
```

实现文件按上述完整字段和条件落地。精确行已存在时第二次执行必须是 no-op，不能再次增加 revision。冲突必须触发 CHECK 并整体回滚，禁止 `ON DUPLICATE KEY UPDATE` 偷偷改写。

在迁移文件末尾写明只针对精确 ID/编码的人工回滚顺序：先删 `1000224` 的精确绑定，再删 `1000024` 的精确权限；发现漂移立即失败，不处理其他权限/角色。

### Step 6：运行 Controller、Schema 和迁移 IT

Run:

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -DskipITs -Dtest=VoiceControllerContractTest,VoiceSchemaContractTest -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Pdev,local-integration-test -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=VoiceDeletePermissionMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: PASS；迁移执行两次无重复和二次 revision bump，冲突用例 fail-closed。

Commit:

```powershell
git add -- ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/voice/controller/VoiceController.java ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/voice/controller/VoiceControllerContractTest.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceSchemaContractTest.java ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/voice/VoiceDeletePermissionMigrationIT.java docs/sql/ai-video/mysql/20260804_01_voice_delete_permission.sql
git commit -m "feat(声音): 增加删除接口与权限"
```

---

## Task 3：前端 API 与声音卡片删除入口

**任务卡**

- 目标：模块 API 提供 DELETE，非公共声音卡在非编辑状态显示危险删除按钮。
- 允许修改：声音 API、卡片、局部样式及测试。
- 禁止修改：页面路由、公共请求 adapter、播放和时间轴协议。
- 反向场景：公共卡无入口，按钮点击不展开/播放，删除中不可重复点击。

**Files:**

- Modify: `ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.ts`
- Modify: `ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.test.ts`
- Modify: `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx`
- Modify: `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.test.tsx`
- Modify: `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

### Step 1：写 API 红灯测试

```ts
it('deletes one voice through the encoded user route', async () => {
  mockRequest.mockResolvedValue(undefined);

  await voiceApi.delete('voice/id');

  expect(mockRequest).toHaveBeenCalledWith('/api/voices/voice%2Fid', {
    method: 'DELETE',
  });
});
```

Run:

```powershell
cd ai-video-ui/ai-video-webapp
npx vitest run src/services/ai-video/voice/api.test.ts
```

Expected: FAIL，`delete` 不存在。

### Step 2：实现 API 最小封装

```ts
export interface VoiceApi {
  // existing methods
  delete(voiceId: string): Promise<void>;
}

delete: (voiceId) => adapter.request<void>(
  `/api/voices/${encodeURIComponent(voiceId)}`,
  { method: 'DELETE' },
),
```

保留现有 runtime proxy/export 方式；页面不得散写 URL、Header 或 envelope 解包。

### Step 3：写卡片入口红灯测试

新增三组用户可观察断言：

```tsx
it('shows delete for an owned expanded voice without toggling the card', async () => {
  const onDelete = vi.fn();
  const onToggle = vi.fn();
  render(<VoiceCard {...props} expanded voice={ownedVoice} onDelete={onDelete} onToggle={onToggle} />);

  await userEvent.click(screen.getByRole('button', { name: '删除声音' }));

  expect(onDelete).toHaveBeenCalledOnce();
  expect(onToggle).not.toHaveBeenCalled();
});
```

- `voice.type === 'public'` 时查询不到删除按钮。
- `deleting` 时按钮 disabled，并显示操作中状态。
- `editing` 时不同时显示删除入口。

Run:

```powershell
cd ai-video-ui/ai-video-webapp
npx vitest run src/pages/digital-human-studio/voices/VoiceCard.test.tsx
```

Expected: FAIL，缺少 props/按钮。

### Step 4：实现卡片和局部样式

新增 props：

```ts
onDelete?: () => void;
deleting?: boolean;
```

在展开、非编辑、非公共声音的现有操作区增加：

```tsx
<button
  aria-label="删除声音"
  className="voice-delete-action"
  disabled={deleting}
  type="button"
  onClick={(event) => {
    event.stopPropagation();
    onDelete?.();
  }}
>
  <StudioIcon name="delete" />
  {deleting ? '删除中…' : '删除'}
</button>
```

在 `style.css` 只新增 `.voice-delete-action` 及其 hover/disabled 样式，复用当前 CSS token/变量，避免全局 `button` 规则和硬编码主题色扩散。

### Step 5：运行聚焦测试并提交

```powershell
cd ai-video-ui/ai-video-webapp
npx vitest run src/services/ai-video/voice/api.test.ts src/pages/digital-human-studio/voices/VoiceCard.test.tsx
npx biome check src/services/ai-video/voice/api.ts src/services/ai-video/voice/api.test.ts src/pages/digital-human-studio/voices/VoiceCard.tsx src/pages/digital-human-studio/voices/VoiceCard.test.tsx
```

Expected: PASS，Biome 无 error。

Commit:

```powershell
git add -- ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.ts ai-video-ui/ai-video-webapp/src/services/ai-video/voice/api.test.ts ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.test.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css
git commit -m "feat(声音): 增加删除操作入口"
```

---

## Task 4：确认流程、错误分流与旧响应防复活

**任务卡**

- 目标：页面只允许一个确认流和一个 DELETE 请求；成功后完整收口本地状态，所有旧列表响应经过 tombstone 过滤。
- 允许修改：`VoiceLibraryView` 和聚焦测试。
- 禁止修改：全局请求错误策略、鉴权跳转、轮询间隔、上传事件协议。
- 反向场景：连续点击、多卡点击、失败重试、401、403、abort、卸载、旧请求晚返回。

**Files:**

- Modify: `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.tsx`
- Modify: `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx`
- Create: `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.server.test.tsx`

### Step 1：写确认流与错误行为红灯测试

在现有 test 环境的 mock `voiceApi` 加 `delete: vi.fn()`，覆盖：

1. 点击自有卡删除，只出现一个标题 `删除声音“{name}”？` 的确认框。
2. 取消不调用 API，卡片保留。
3. 确认只调用一次 `voiceApi.delete(id)`；Promise pending 时重复确认/其他卡触发不产生第二次调用。
4. 成功后停止播放，卡片消失，展开/编辑草稿清理，提示 `声音已删除`。
5. `ApiError({code: 403})` 只提示一次 `没有删除声音的权限`，卡片保留。
6. `ApiError({code: 401})` 不显示页面通用删除失败；会话处理继续由 adapter 负责。
7. abort/cancel 不显示业务错误；其他错误只提示 `声音删除失败，请刷新后重试`，Promise reject 使确认框保留以便重试。

### Step 2：写生产加载竞态红灯测试

`VoiceLibraryView.server.test.tsx` 不静态 import 页面；先设置 production env，再动态 import：

```ts
vi.resetModules();
vi.stubEnv('NODE_ENV', 'production');
vi.doMock('@/services/ai-video/voice/api', () => ({ voiceApi: mockVoiceApi }));
const { default: VoiceLibraryView } = await import('./VoiceLibraryView');
```

使用 deferred Promise 模拟：

1. 第一次 list 返回自有声音并渲染卡片。
2. 派发 `aivideo:voice-changed`，第二次 list 保持 pending，响应内容仍含该声音。
3. 删除成功，卡片消失。
4. 第二次旧 list 后返回，断言卡片仍不出现。
5. 卸载后 resolve/reject 不触发 state 更新告警，页面创建的确认框被销毁。

Run:

```powershell
cd ai-video-ui/ai-video-webapp
npx vitest run src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.server.test.tsx
```

Expected: FAIL，当前没有删除流和 tombstone。

### Step 3：实现同步锁、tombstone 和确认框

导入：

```ts
import { ApiError, isAbortError } from '@/services/ai-video/core/errors';
```

新增：

```ts
const deleteFlowRef = useRef<string | null>(null);
const deleteRequestRef = useRef(false);
const deletedVoiceIdsRef = useRef(new Set<string>());
const deleteModalRef = useRef<{ destroy: () => void } | null>(null);
const mountedRef = useRef(true);
const [deletingVoiceId, setDeletingVoiceId] = useState<string | null>(null);

const filterDeletedVoices = useCallback(
  (items: VoiceItem[]) => items.filter((item) => !deletedVoiceIdsRef.current.has(item.id)),
  [],
);
```

所有 `loadVoices` 的响应必须在写入前过滤：

```ts
const rows = response.rows.map(toVoiceItem);
if (mountedRef.current) setVoices(filterDeletedVoices(rows));
```

删除处理采用以下结构；具体 `ModalFunc` 类型按 Ant Design 当前声明调整：

```ts
const confirmDelete = (voice: VoiceItem) => {
  if (deleteFlowRef.current) return;
  deleteFlowRef.current = voice.id;
  deleteModalRef.current = modal.confirm({
    title: `删除声音“${voice.name}”？`,
    content: '删除后无法恢复，音频文件和文案将同时删除。',
    okText: '确认删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    afterClose: () => {
      if (deleteFlowRef.current === voice.id) deleteFlowRef.current = null;
      deleteModalRef.current = null;
    },
    onOk: async () => {
      if (deleteRequestRef.current) return Promise.reject(new Error('delete already in progress'));
      deleteRequestRef.current = true;
      if (mountedRef.current) setDeletingVoiceId(voice.id);
      playback.stop();
      try {
        await voiceApi.delete(voice.id);
        deletedVoiceIdsRef.current.add(voice.id);
        if (mountedRef.current) {
          setVoices((current) => current.filter((item) => item.id !== voice.id));
          setExpandedIds((current) => {
            const next = new Set(current);
            next.delete(voice.id);
            return next;
          });
          if (editingId === voice.id) {
            setEditingId(null);
            setDraft('');
          }
          onToast('声音已删除', 'success');
        }
      } catch (error) {
        if (mountedRef.current && !isAbortError(error)) {
          if (error instanceof ApiError && error.code === 403) {
            onToast('没有删除声音的权限', 'error');
          } else if (!(error instanceof ApiError && error.code === 401)) {
            onToast('声音删除失败，请刷新后重试', 'error');
          }
        }
        throw error;
      } finally {
        deleteRequestRef.current = false;
        if (mountedRef.current) setDeletingVoiceId(null);
      }
    },
  });
};
```

不要用 React state 代替同步 ref 锁；同一事件循环连续点击必须被挡住。不要在请求成功前乐观移除。`onOk` reject 保持确认框开启，用户可再次确认或取消。对内部“重复调用”分支不得弹业务错误。

卸载 effect：设置 `mountedRef.current=false`，destroy 本页面创建的 modal，清理 flow/request refs；不要 `Modal.destroyAll()` 误伤其他页面。

将卡片接线：

```tsx
deleting={deletingVoiceId === voice.id}
onDelete={() => confirmDelete(voice)}
```

### Step 4：运行页面竞态测试并提交

```powershell
cd ai-video-ui/ai-video-webapp
npx vitest run src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.server.test.tsx
npx biome check src/pages/digital-human-studio/voices/VoiceLibraryView.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.server.test.tsx
```

Expected: PASS；晚返回的 list 不复活已删除卡片，错误提示次数准确。

Commit:

```powershell
git add -- ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.server.test.tsx
git commit -m "feat(声音): 完成删除确认与竞态收口"
```

---

## Task 5：全链路验证、路由边界和独立审查

**任务卡**

- 目标：以新鲜命令证明删除功能、现有声音能力和部署边界均通过。
- 允许修改：仅修复本计划直接导致的失败；任何范围扩张先停下确认。
- 审查：一名独立安全/数据 reviewer；修复后只复核命中的文件和测试。

**Files:**

- Verify only: 上述所有实现和测试文件
- Verify: `scripts/validate-development-standards.ps1`

### Step 1：后端聚焦回归

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am -DskipITs -Dtest=VoiceServiceImplTest,AssetServiceImplTest,VoiceSchemaContractTest,VoiceControllerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS，所有指定测试实际执行且 0 failure/0 error。

### Step 2：本机真实集成测试

```powershell
cd ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Pdev,local-integration-test -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.test=VoiceDeletePermissionMigrationIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Expected: PASS。若开发配置不完整、不安全或本机依赖缺失，记录为“未运行及具体原因”，绝不能写成通过。

### Step 3：前端完整声音回归、类型和构建

```powershell
cd ai-video-ui/ai-video-webapp
npx vitest run src/services/ai-video/voice/api.test.ts src/pages/digital-human-studio/voices/VoiceCard.test.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx src/pages/digital-human-studio/voices/VoiceLibraryView.server.test.tsx src/pages/digital-human-studio/voices/useVoicePlayback.test.ts src/pages/digital-human-studio/voices/voiceTimeline.test.ts
npm run tsc
npm run biome:lint
npm run build
```

Expected: 全部 PASS；构建产物可生成，无 TypeScript/Biome error。

### Step 4：开发标准和差异卫生

从工作树根运行：

```powershell
.\scripts\validate-development-standards.ps1
git diff --check
git status --short
```

Expected: `DEVELOPMENT_STANDARDS_OK`；`git diff --check` 无输出。检查 `git status`，确保没有把用户原有的 MP3、转写同步或其他未提交改动误认成本功能产物。

### Step 5：启动边界 Smoke Test

在两个独立本地端口启动 `ai-video-user-api` 与 `ruoyi-admin`，使用匿名、创作端有效 Token、错误端 Token 依次探测：

```text
DELETE <user-api>/api/voices/{ownedVoiceId}
DELETE <admin-api>/api/voices/{ownedVoiceId}
```

验收：

- 用户端匿名/错误端 Token 按现有认证隔离拒绝。
- 用户端缺权限为 403。
- 用户端合法账号删除自己声音为 200；第二次同 ID 为 `46401`。
- 运营端不暴露该用户 Controller（404/既有未映射结果），而不是执行删除。
- 不使用生产数据；测试声音必须是非敏感临时数据。

### Step 6：人工 UI 验收

1. 上传一条非敏感测试声音，展开卡片并播放。
2. 点删除，验证只弹一个确认框；取消后卡片仍在。
3. 再次删除并确认，音频立即停止、按钮 loading、成功后卡片消失。
4. 刷新页面后卡片不再出现；旧轮询响应不能将其恢复。
5. 公共声音没有删除按钮。
6. 用另一账号、另一工作区、缺权限账号直接请求该 ID，分别验证 `46401`/403。
7. 删除 `transcribing` 声音，后续 Worker 结果不得写回或恢复卡片。
8. 查看数据库：`av_voice` 与对应 `av_asset` 已逻辑删除；对象清理成功时存储对象不可达；清理故障时只有脱敏日志且声音仍不可见。

### Step 7：独立安全/数据专项审查

审查只覆盖：

- SQL 是否在最终写条件中同时限制 tenant/workspace/owner/type/delFlag。
- voice/asset 是否同事务，rollback 时不触发外部删除，after-commit 失败是否不复活业务数据。
- 权限迁移是否冲突即失败、执行两次幂等、revision 仅一次失效。
- 用户端/运营端路由和 Token 隔离是否正确。
- 前端同步锁、错误分流、unmount 和 stale response tombstone 是否有竞态漏洞。

若有必须修复项，只修改命中范围并重跑对应聚焦测试；不得递归启动新一轮全量审查。

### Step 8：最终提交与交付证据

```powershell
git status --short
git diff --check
```

若修复产生最后一组尚未提交的本功能文件，精确 `git add -- <files>` 后提交：

```powershell
git commit -m "fix(声音): 收口删除安全与竞态"
```

最终交付必须列出：提交号、实际运行的命令及结果、未运行项及原因、已知剩余风险（明确写出“进程在 after-commit 回调前崩溃时没有自动物理清理重试”），以及人工验收使用的非敏感声音 ID。没有实际证据时不得宣称完成。

---

## 计划自检

- [x] 需求输入、用户确认边界、风险等级和不做范围已记录。
- [x] 前端、后端、SQL、权限、资产和转写竞态均有明确文件清单。
- [x] 每个实现任务先写失败测试，再写最小实现，再运行聚焦验证。
- [x] 删除成功、重复、匿名、缺权限、跨账号、跨工作区、公共声音和 stale response 均有反向验收。
- [x] 本地 IT 明确限制为 `LocalIntegrationEnvironment`、`ai_video_test`、独立 Redis 与用户端 `application-dev.yml`；七个 `AI_VIDEO_IT_*` 变量仅可选覆盖。
- [x] 未引入引用检查、回收站、恢复、批量、额度、任务追踪或持久化清理任务。
- [x] 所有路径、代码片段和产品选择均已明确，无悬而未决内容。
