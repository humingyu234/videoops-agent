# 人物照片格式扩展实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。当前工作区包含用户既有修改，禁止覆盖无关差异；按用户要求不创建 commit。

**目标：** 让用户端人物照片统一支持大小写不敏感的 JPG/JPEG、PNG、WebP、GIF，并由服务端核对扩展名、MIME、文件头和有效宽高。

**架构：** 前端新增一个纯函数白名单模块，供“新增形象”和“形象空间”复用。后端扩展现有 `PortraitImageValidator`，JPEG/PNG/GIF 继续安全解码，WebP 在不引入第三方依赖的情况下校验 RIFF/WEBP 位流头并读取宽高；`PortraitImageMetadata` 提供规范 MIME 和对象后缀给现有 Service 使用。

**技术栈：** React 19、TypeScript 7、Ant Design 6、Vitest 4、Java 21、Spring Boot、JUnit 5、AssertJ、Maven。

**规格输入：** `docs/superpowers/specs/2026-08-04-portrait-image-format-expansion-design.md`

**风险与任务卡：** 本任务命中文件上传安全和共享格式规则，风险为红色。单一目标仅为扩展人物照片格式；不做图片转换、人脸/清晰度/构图/内容审核、批量上传或数据库变更。实施者为当前主代理，不并发写共享文件；完成后使用独立审查者做一次安全/契约审查，只允许一次定向复核。验证门禁是前端定向测试、TypeScript、Biome、后端定向测试及差异检查。本次不需要 MySQL/Redis 集成测试，因为不修改数据结构、查询、事务、缓存或 HTTP 契约结构。

---

## 文件结构

- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/utils/portraitImageFile.ts`：前端人物图片白名单、统一 `accept` 和提示文案。
- 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/utils/portraitImageFile.test.ts`：四类格式、大小写和错配测试。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.tsx`：复用白名单并扩展本地预览格式。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx`：更新上传范围和拒绝场景。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`：创建形象前执行同一前端格式校验。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`：验证 WebP/GIF 和错误提示。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/PortraitImageValidator.java`：后端四类格式一致性和文件头校验。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/PortraitImageMetadata.java`：输出规范 MIME 与文件后缀。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`：使用规范元数据生成对象键和保存 MIME。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/PortraitImageValidatorTest.java`：验证四类格式、大小写、截断和错配。
- 修改 `docs/superpowers/specs/2026-08-03-user-portrait-library-design.md`、`docs/superpowers/specs/2026-08-04-code-review-remediation-design.md`：同步人物照片格式范围；`docs/API_CONTRACT.md` 与 `docs/DOMAIN_MODEL.md` 不需要调整，因为请求结构、错误码和领域字段不变。

### 任务 1：冻结共享格式契约并编写后端失败测试

**任务卡：** 红色；只允许修改人物形象规格和 `PortraitImageValidatorTest`。成功场景覆盖四类格式和大小写；反向场景覆盖错配、截断、空 MIME、SVG/BMP。验证为单测先红后绿；此任务不并发。

**文件：**
- 修改：`docs/superpowers/specs/2026-08-03-user-portrait-library-design.md`
- 修改：`docs/superpowers/specs/2026-08-04-code-review-remediation-design.md`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/PortraitImageValidatorTest.java`

- [ ] **步骤 1：同步已确认格式契约**

把旧的 “JPG、JPEG、PNG” 白名单统一改为 “JPG、JPEG、PNG、WebP、GIF”，补充扩展名/MIME 大小写不敏感，文件头精确匹配；不改变 `46201`、`46202`、10MB、单文件和归属规则。

- [ ] **步骤 2：增加四类格式和大小写失败测试**

在 `PortraitImageValidatorTest` 增加：

```java
@Test
void acceptsGifWebpAndCaseInsensitiveMetadata() throws Exception {
    PortraitImageMetadata gif = validator.validate("PORTRAIT.GIF", "IMAGE/GIF", image("gif"));
    PortraitImageMetadata webp = validator.validate("PORTRAIT.WeBp", "IMAGE/WEBP", webpVp8x(2, 3));

    assertThat(gif.format()).isEqualTo("gif");
    assertThat(gif.contentType()).isEqualTo("image/gif");
    assertThat(gif.fileSuffix()).isEqualTo(".gif");
    assertThat(webp.format()).isEqualTo("webp");
    assertThat(webp.width()).isEqualTo(2);
    assertThat(webp.height()).isEqualTo(3);
    assertThat(webp.contentType()).isEqualTo("image/webp");
    assertThat(webp.fileSuffix()).isEqualTo(".webp");
}

@Test
void rejectsUnsupportedMismatchedAndTruncatedFormats() {
    assertTypeError("portrait.webp", "image/png", webpVp8x(2, 3));
    assertTypeError("portrait.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII));
    assertTypeError("portrait.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8));
    assertTypeError("portrait.bmp", "image/bmp", new byte[]{'B', 'M'});
}
```

测试辅助方法构造完整 RIFF 长度和 `VP8X` 10 字节头，宽高按 24 位 little-endian 的 `value - 1` 写入；`assertTypeError` 固定断言错误码 `46201`。

- [ ] **步骤 3：运行后端测试确认失败**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
.\mvnw.cmd '-Dmaven.repo.local=D:\AI\ai-video\.m2\repository' -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dtest=PortraitImageValidatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false' test
```

工作目录：`ai-video-api`。预期：FAIL，缺少 `contentType()`、`fileSuffix()`，且 WebP/GIF 尚未被白名单接受。

### 任务 2：实现后端文件头校验与规范存储映射

**任务卡：** 红色；只修改 `ai-video-core` 现有校验、元数据和 Service 映射。不得新增依赖、接口、表或业务层。反向场景必须在触达 OSS/数据库前失败。验证为 `PortraitImageValidatorTest` 和现有资产/形象回归；此任务不并发。

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/PortraitImageValidator.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/PortraitImageMetadata.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/PortraitImageValidatorTest.java`

- [ ] **步骤 1：为规范格式提供 MIME 与后缀**

在 `PortraitImageMetadata` 中增加纯映射方法：

```java
public String contentType() {
    return switch (format) {
        case "jpeg" -> "image/jpeg";
        case "png" -> "image/png";
        case "webp" -> "image/webp";
        case "gif" -> "image/gif";
        default -> throw new IllegalStateException("Unsupported portrait image format: " + format);
    };
}

public String fileSuffix() {
    return "." + ("jpeg".equals(format) ? "jpg" : format);
}
```

- [ ] **步骤 2：扩展格式一致性和 WebP 头解析**

`validate` 先把扩展名与 MIME 使用 `Locale.ROOT` 转小写，然后使用固定映射判断三者一致：

```java
boolean supported = switch (magicFormat) {
    case "jpeg" -> ("jpg".equals(extension) || "jpeg".equals(extension)) && "image/jpeg".equals(mime);
    case "png" -> "png".equals(extension) && "image/png".equals(mime);
    case "webp" -> "webp".equals(extension) && "image/webp".equals(mime);
    case "gif" -> "gif".equals(extension) && "image/gif".equals(mime);
    default -> false;
};
```

JPEG、PNG、GIF 继续用 `ImageIO.read` 获取宽高。WebP 使用以下严格边界：

```java
private ImageDimensions webpDimensions(byte[] content) {
    if (content.length < 30 || !ascii(content, 0, "RIFF") || !ascii(content, 8, "WEBP")) throw typeError();
    long riffSize = littleEndianUnsigned(content, 4, 4);
    if (riffSize != content.length - 8L) throw typeError();
    String chunk = asciiValue(content, 12, 4);
    long chunkSize = littleEndianUnsigned(content, 16, 4);
    if (chunkSize > content.length - 20L) throw typeError();
    return switch (chunk) {
        case "VP8X" -> vp8xDimensions(content, chunkSize);
        case "VP8L" -> vp8lDimensions(content, chunkSize);
        case "VP8 " -> vp8Dimensions(content, chunkSize);
        default -> throw typeError();
    };
}
```

三种位流头都要求最小长度、固定签名和宽高大于零；所有移位前使用 `& 0xff`，避免有符号字节污染。

- [ ] **步骤 3：使用规范元数据存储**

在 `AssetServiceImpl.uploadPortraitImage` 替换现有二选一后缀和原始 MIME：

```java
String key = client.buildPathKey(
    "portraits/" + principal.appUserId(),
    UUID.randomUUID() + metadata.fileSuffix()
);
asset.setContentType(metadata.contentType());
```

其他上传、归属、事务和删除逻辑不变。

- [ ] **步骤 4：运行后端测试确认通过**

重复任务 1 步骤 3 命令。预期：`PortraitImageValidatorTest` 全部 PASS，无失败和错误。

### 任务 3：前端共享白名单与两个上传入口

**任务卡：** 红色；只修改用户端人物图片选择逻辑和对应测试。前端校验用于即时反馈，不能替代服务端校验。成功场景覆盖四类格式和大小写；失败保持原选择/原形象。验证为 Vitest 红绿循环、TypeScript 和 Biome；此任务不并发。

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/utils/portraitImageFile.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/utils/portraitImageFile.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`

- [ ] **步骤 1：编写共享白名单失败测试**

新测试覆盖：

```ts
expect(isSupportedPortraitImage(new File(['x'], 'A.JPG', { type: 'IMAGE/JPEG' }))).toBe(true);
expect(isSupportedPortraitImage(new File(['x'], 'A.PnG', { type: 'image/png' }))).toBe(true);
expect(isSupportedPortraitImage(new File(['x'], 'A.WeBp', { type: 'image/webp' }))).toBe(true);
expect(isSupportedPortraitImage(new File(['x'], 'A.GIF', { type: 'image/gif' }))).toBe(true);
expect(isSupportedPortraitImage(new File(['x'], 'A.webp', { type: 'image/png' }))).toBe(false);
expect(isSupportedPortraitImage(new File(['x'], 'A.svg', { type: 'image/svg+xml' }))).toBe(false);
```

同时断言 `PORTRAIT_IMAGE_ACCEPT` 精确等于 `.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif`。

- [ ] **步骤 2：更新两个组件测试并确认失败**

`AvatarSpaceView.test.tsx` 将 GIF/WebP 从拒绝集合移入接受集合，并保留 SVG/BMP/错配拒绝；`PortraitLibraryView.test.tsx` 选择 WebP 后出现预览，选择 SVG 时显示格式警告且不出现缩略图。

运行：

```powershell
$taskNode='C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
& $taskNode node_modules/vitest/vitest.mjs run src/pages/digital-human-studio/utils/portraitImageFile.test.ts src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx
```

工作目录：`ai-video-ui/ai-video-webapp`。预期：FAIL，共享模块不存在且组件仍只允许 JPEG/PNG。

- [ ] **步骤 3：实现共享纯函数**

```ts
export const PORTRAIT_IMAGE_ACCEPT =
  '.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif';
export const PORTRAIT_IMAGE_FORMAT_MESSAGE = '仅支持 JPG、JPEG、PNG、WebP、GIF';

const MIME_BY_EXTENSION: Record<string, string> = {
  gif: 'image/gif',
  jpeg: 'image/jpeg',
  jpg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
};

export const isSupportedPortraitImage = (file: File) => {
  const extension = file.name.trim().split('.').pop()?.toLowerCase() ?? '';
  const mime = file.type.trim().toLowerCase();
  return MIME_BY_EXTENSION[extension] === mime;
};
```

- [ ] **步骤 4：接入两个组件**

删除 `AvatarSpaceView` 内部重复的 `isSupportedAvatarImage`，两个组件都导入共享常量与函数。`accept` 使用常量；失败时不覆盖当前文件，统一提示格式列表。`PortraitLibraryView.selectFile` 在大小校验前执行类型校验。

- [ ] **步骤 5：运行前端测试确认通过**

重复步骤 2 命令。预期：3 个测试文件全部 PASS。

### 任务 4：完整回归、独立审查与收口

**任务卡：** 红色验证任务；不新增功能。审查只覆盖本次格式差异、上传安全边界和测试证据；发现非阻塞问题进入 backlog。允许 1 名独立审查者只读审查，不允许派生子代理。修复后只进行一次定向复核。

**文件：**
- 验证：本计划列出的所有实现、测试和规格文件

- [ ] **步骤 1：运行前端完整相关回归**

```powershell
$taskNode='C:\Users\Administrator\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
& $taskNode node_modules/vitest/vitest.mjs run src/pages/digital-human-studio/utils/portraitImageFile.test.ts src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx src/pages/digital-human-studio/index.test.tsx src/services/ai-video/portrait/api.test.ts
& $taskNode node_modules/typescript/bin/tsc --noEmit
& $taskNode node_modules/@biomejs/biome/bin/biome lint src/pages/digital-human-studio/utils/portraitImageFile.ts src/pages/digital-human-studio/utils/portraitImageFile.test.ts src/pages/digital-human-studio/avatar-space/AvatarSpaceView.tsx src/pages/digital-human-studio/avatar-space/AvatarSpaceView.test.tsx src/pages/digital-human-studio/components/PortraitLibraryView.tsx src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx
```

预期：所有命令退出码 0。

- [ ] **步骤 2：运行后端完整相关回归**

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot'
.\mvnw.cmd '-Dmaven.repo.local=D:\AI\ai-video\.m2\repository' -pl 'ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user' -am '-Dtest=PortraitImageValidatorTest,PortraitServiceImplTest,PortraitControllerTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false' test
```

工作目录：`ai-video-api`。预期：BUILD SUCCESS，所有指定测试零失败零错误。

- [ ] **步骤 3：执行一次独立安全/契约审查**

审查输入限制为本规格、本计划和以下差异：共享前端白名单、两个上传入口、`PortraitImageValidator`、`PortraitImageMetadata`、`AssetServiceImpl`、对应测试。审查必须确认大小写归一、三要素一致、WebP 边界检查、错误码、OSS/数据库前置拒绝和不支持格式；输出只允许 `[必须修复]`、`[建议修改]`、`[仅供参考]`。

- [ ] **步骤 4：差异边界和格式规范验证**

```powershell
git diff --check
git status --short
rg -n "JPG、JPEG、PNG|\.jpg,.jpeg,.png" ai-video-ui/ai-video-webapp/src/pages/digital-human-studio ai-video-api/ruoyi-modules/ai-video/ai-video-core docs/superpowers/specs/2026-08-03-user-portrait-library-design.md docs/superpowers/specs/2026-08-04-code-review-remediation-design.md
```

预期：无空白错误；所有人物上传提示和白名单都包含 WebP、GIF；没有修改数据库、权限、接口路径或无关用户文件。
