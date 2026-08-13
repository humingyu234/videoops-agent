# 人物照片格式扩展设计

## 目标与范围

将用户端“新增形象”和“形象空间”人物照片白名单从 JPG/JPEG、PNG 扩展为 JPG/JPEG、PNG、WebP、GIF，前后端使用同一组格式规则。扩展名与 MIME 比较均忽略大小写；文件头按二进制签名判断，不存在大小写概念。

本轮只扩展文件类型安全校验和对应提示，不做人脸、清晰度、构图或内容审核，不支持批量上传、图片转换、版本记录，也不接受 BMP、SVG、HEIC/HEIF、AVIF。

## 格式契约

| 规范格式 | 允许扩展名 | 允许 MIME | 文件头要求 |
| --- | --- | --- | --- |
| `jpeg` | `.jpg`、`.jpeg` | `image/jpeg` | `FF D8 FF` |
| `png` | `.png` | `image/png` | PNG 8 字节签名 |
| `webp` | `.webp` | `image/webp` | RIFF 容器、`WEBP` 标识及合法 `VP8 `、`VP8L` 或 `VP8X` 图像块 |
| `gif` | `.gif` | `image/gif` | `GIF87a` 或 `GIF89a` |

服务端只有在扩展名、MIME、文件头格式三者一致且能得到正整数宽高时才接受文件。扩展名与 MIME 先 `trim` 并按固定语言环境转小写，因此 `.JPG`、`.JPEG`、`.PNG`、`.WEBP`、`.GIF` 和大小写混合 MIME 均按同一规则处理。文件头是字节序列，必须精确匹配。

## 前端行为

- 两个上传入口的 `accept` 统一为 `.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif`。
- 选择文件后先按忽略大小写的扩展名与 MIME 对照表校验；不匹配时保留原图片并提示“仅支持 JPG、JPEG、PNG、WebP、GIF”。
- “新增形象”仍由服务端执行最终文件头校验；“形象空间”的本地图片仅用于当前页面预览，不显示服务端安全校验成功结论。
- 单文件和 10MB 上限不变。

## 后端行为

- `PortraitImageValidator` 扩展 GIF 与 WebP 文件头识别和宽高读取，保留现有错误码 `46201`、大小上限及账号/权限边界。
- JPEG、PNG、GIF 继续使用现有安全解码路径；WebP 不新增第三方解码依赖，校验 RIFF/WebP 块结构并读取位流头中的宽高。
- `AssetServiceImpl` 按规范格式生成 `.jpg`、`.png`、`.webp`、`.gif` 对象键，并保存规范 MIME，避免原始大小写或别名污染元数据。
- 不修改接口路径、请求结构、数据库结构、权限标识或异步任务。

## 错误处理与反向场景

- 扩展名、MIME、文件头任一不一致：返回 `46201`，不上传 OSS、不创建资产记录。
- 伪造 RIFF 但缺少 `WEBP`、未知 WebP 图像块、截断的 GIF/WebP、无法取得正整数宽高：返回 `46201`。
- SVG、BMP、HEIC/HEIF、AVIF 以及空 MIME：返回 `46201`。
- 大于 10MB：继续返回 `46202`。

## 测试与验收

- 前端测试覆盖四类格式、大小写扩展名、不匹配 MIME、SVG/BMP 拒绝、统一 `accept` 与提示文案。
- 后端测试覆盖 JPG/JPEG、PNG、GIF、WebP 的一致组合与大小写；覆盖扩展名/MIME/文件头错配、截断文件和未支持格式。
- `PortraitImageValidatorTest` 同时确认 WebP/GIF 输出正确后缀和规范 MIME，`AssetServiceImpl` 只消费该已验证元数据。
- 运行相关 Vitest、TypeScript、Biome，以及 `ai-video-core`、`ai-video-user` 定向 Maven 测试。

## 风险与协作

本任务涉及上传安全和共享格式契约，按红色高风险处理。单一目标是扩展人物照片白名单；实施只影响用户端前端、`ai-video-core` 文件校验/存储映射、对应测试和人物形象规格。实施阶段不并发修改共享文件；完成后进行一次独立安全/契约审查和一次定向复核，禁止扩展到图片转换或内容审核。
