# 视频创作五步流程一比一还原设计规格

## 背景

本规格用于实现用户端 Web 的视频创作五步流程，要求严格按照用户提供的 5 张 v2 参考图完成布局、样式、图标、内容密度和交互状态的一比一还原。

参考视觉源：

- `D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 11_26_55 (2).png`：第 1 步，选择行业。
- `D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 11_26_57 (6).png`：第 2 步，选择方向。
- `D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 11_26_56 (3).png`：第 3 步，选择模板。
- `D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月1日 15_32_32 (5).png`：第 4 步，工作台。
- `D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月4日 16_41_51.png`：第 5 步，生成成片。

当前工程：

- 前端包：`ai-video-ui/ai-video-webapp`
- 技术栈：React + TypeScript + Ant Design + Ant Design Pro / ProComponents + Umi Max
- 当前状态：首页工作台和基础业务路由骨架已部分落地，视频创作五步流程尚未完整实现。

## 已确认方案

采用方案 A：全量自定义视频创作流程壳层。

- 五步流程页不沿用 ProLayout 的默认侧边栏和顶部栏视觉。
- 在视频创作流程路由内自定义页面壳层，包含左侧导航、顶部品牌区、五步步骤条、帮助、通知、头像和用户店铺入口。
- 主内容区、侧边栏、顶部栏、按钮、图标、卡片、图片、间距和字号均按参考图实现。
- 首页工作台旧规格中“保留 ProLayout 外壳”的限制只适用于首页规格，不适用于本规格。

## 范围

本次实现覆盖完整前端可交互 mock 流程：

| 步骤 | 路由 | 参考图 | 实现目标 |
| --- | --- | --- | --- |
| 1 | `/video/create/industry` | 选择行业 | 行业搜索、行业卡片、单选状态 |
| 2 | `/video/create/directions` | 选择方向 | 已选行业、方向多选、右侧摘要 |
| 3 | `/video/create/templates` | 选择模板 | 筛选、模板列表、右侧详情、分页 |
| 4 | `/video/create/workspace` | 工作台 | 分镜列表、镜头编辑、素材替换、AI 助手、镜头顺序 |
| 5 | `/video/create/result` | 生成成片 | 视频预览、进度、输出信息、一键发布、作品列表 |

不在本次范围：

- 不新增真实后端接口。
- 不实现 Electron 壳层。
- 不实现真实视频播放、上传、生成或发布能力。
- 不实现草稿箱、任务中心、素材管理等完整业务页；仅保证导航入口不 404 或进入占位页。

## 视觉还原目标

目标是参考图级别的一比一还原，而不是“相似风格”。

- 设计稿基准尺寸为 `1680 x 946`。
- 验收视口固定为 `1680 x 946`。
- 五步流程页整页均纳入视觉验收范围，包括左侧导航、顶部栏、步骤条、主内容区、右侧面板和底部区域。
- 页面背景、左侧栏宽度、顶部栏高度、内容起始坐标、卡片尺寸、网格列数、边框、圆角、阴影、按钮、选中态、标签、图标尺寸和文案层级必须对照参考图调校。
- 图标优先使用 `@ant-design/icons` 或项目可用图标库；若官方图标与参考图差异明显，使用局部自定义 SVG 组件，但统一封装，不在页面中散落。
- 图片资产必须使用参考图裁切资产或同构位图，不允许空白占位、纯渐变占位或随机网络图片。
- 移动端只要求可访问、不重叠、不横向失控，不纳入本次像素级验收。

## 全局流程壳层

五步页面共享 `VideoCreateShell`：

- 左侧导航宽度约 `248px`，白色背景，右边界细线。
- 顶部栏高度约 `60px`，白色背景，底部细线。
- 品牌区包含渐变播放图标和标题。第 1-4 步标题为 `AI视频工作台`，第 5 步参考图为 `成片工作台`。
- 左侧菜单分组：
  - 首页。
  - 视频创作：视频创作、草稿箱、模板中心。
  - 数字人：图生数字人、视频数字人、克隆声音。
  - 素材管理。
  - 系统功能：任务中心。
- 当前菜单项按参考图使用浅蓝底、蓝色图标和蓝色文字。
- 第 5 步侧边栏底部增加积分使用情况卡片：`1,240 / 5,000 积分`，进度 `24.8%`。
- 顶部步骤条居中展示 5 步，当前步骤为蓝底白字圆点，已完成或未激活步骤为白底黑字圆点，步骤之间用浅灰连线。
- 右上角包含帮助、通知、头像、店铺名。通知红点数量为 `2`。

共享状态：

- `industryId`
- `directionIds`
- `templateId`
- `workspaceDraft`
- `resultId`

状态存储首期方案定死：使用 Umi Max 内置的 `useModel` 数据流方案，集中放在 `src/models/videoCreate.ts`，导出一个自定义 hook（如 `useVideoCreate`），任意组件通过 `useModel('videoCreate')` 读写共享状态。不引入自建 React Context、不引入 zustand / redux / mobx 等外部状态库，与项目零自建 Context 的现状保持一致。Provider 由 Umi `model` 插件在应用根节点自动挂载（`config/config.ts` 已开启 `model: {}`），跨步骤路由切换时状态自动保留。刷新后从默认 mock 状态恢复。后续接后端时以草稿接口和任务接口为准，仅需替换 model 内的初始化与持久化逻辑，不动 hook 对外签名。

跨步骤路由守卫使用确定性 mock 种子数据，不阻断设计稿验收：

- 直接访问 `/video/create/directions` 时，如果没有 `industryId`，默认使用 `fashion`。
- 直接访问 `/video/create/templates` 时，如果没有 `industryId` 或 `directionIds`，默认使用 `fashion` 和前三个方向。
- 直接访问 `/video/create/workspace` 时，如果没有 `templateId`，默认使用 `fashionNew30`。
- 直接访问 `/video/create/result` 时，如果没有 `resultId`，默认使用成功态 mock 成片。
- 点击“修改行业”仍需清空方向和模板，回到真实流程起点。

## 第 1 步：选择行业

页面目标：帮助用户选择所属行业，用于推荐创作方向、模板和素材。

布局：

- 标题：`选择行业`。
- 副标题：`选择行业，获取更精准的模板推荐和创作内容`。
- 搜索框宽度约 `642px`，占位文案：`搜索行业，如：服装、美食、教育等`，右侧搜索图标。
- 分区标题：`推荐行业`。
- 行业卡片 4 列 x 2 行。
- 底部居中提示：`没有找到合适的行业？ 联系客服 获取更多行业解决方案`。

行业卡片字段：

- 封面图。
- 左下角行业图标。
- 行业名称。
- 行业描述。
- 使用次数。
- 选中态。

固定 mock 行业：

| id | 名称 | 描述 | 使用次数 | 图标色 |
| --- | --- | --- | --- | --- |
| fashion | 服装销售 | 适用于服装、鞋帽、箱包等时尚类产品的营销推广 | 12.5w 次 | 蓝 |
| food | 餐饮美食 | 适用于餐厅、小吃、饮品等餐饮美食行业的宣传推广 | 11.8w 次 | 橙 |
| education | 教育培训 | 适用于教育机构、课程培训、技能学习等场景 | 9.8w 次 | 绿 |
| realEstate | 房地产 | 适用于楼盘展示、房产中介、装修设计等场景 | 8.6w 次 | 紫 |
| beauty | 美妆护肤 | 适用于化妆品、护肤品、美容美发等行业 | 7.3w 次 | 粉 |
| localLife | 本地生活 | 适用于本地服务、团购、生活服务等场景 | 6.9w 次 | 蓝 |
| home | 家居建材 | 适用于家具、家装、建材等家居行业 | 5.7w 次 | 橙 |
| car | 汽车 | 适用于汽车销售、汽车服务、配件等行业 | 5.2w 次 | 紫 |

交互：

- 点击卡片选中行业，默认选中 `服装销售`。
- 选中卡片显示蓝色描边和右上角蓝色勾选圆点。
- 搜索实时过滤行业；无结果展示 Empty 和联系客服入口。
- 单击卡片只负责选中，不立即跳转，确保用户能看到选中态。
- 进入下一步使用可访问但不改变参考图视觉的规则：选中行业后按 `Enter`，或点击顶部步骤条中的第 2 步“选择方向”进入 `/video/create/directions`。
- 不允许使用不可见点击热区或隐式业务事件作为主要跳转入口。

状态：

- 加载：行业卡片区域骨架屏。
- 空：搜索无结果。
- 失败：Result 错误态和重试按钮。
- 权限不足：沿用登录拦截或展示无权限 Result。

## 第 2 步：选择方向

页面目标：在已选行业下选择 1-3 个具体创作方向。

布局：

- 标题：`选择方向`。
- 已选行业行：`已选择行业： 服装销售`，后接蓝色 `修改行业`。
- 分类 tabs：`全部方向`、`销售渠道`、`内容场景`、`经营目标`。
- 左侧主区两列方向卡片。
- 右侧固定摘要卡片，标题 `选择摘要`。
- 底部提示：`选择方向后，系统将为你推荐更贴合业务场景的模板和素材`。

方向卡片字段：

- 缩略图。
- 方向名称。
- 描述。
- 场景标签。
- 选中态。

固定 mock 方向：

| id | 名称 | 描述 | 标签 |
| --- | --- | --- | --- |
| offlineStore | 线下女装买手店 | 适合定位中高端的女装买手店，突出店铺风格、穿搭推荐与会员服务。 | 线下门店 |
| ecommerce | 线上女装电商 | 适合在主流电商平台经营的女装商家，突出爆款推荐、上新种草与优惠促销。 | 线上店铺 |
| kidsLive | 童装直播带货 | 聚焦童装直播场景，适合通过直播展示产品细节、互动答疑与限时秒杀。 | 直播带货 |
| streetBrand | 潮牌街头 | 适合潮牌、原创设计品牌，突出品牌调性、穿搭展示与潮流文化内容。 | 品牌宣传 |
| underwear | 内衣家居服 | 适合内衣、家居服商家，突出舒适体验、面料质感与生活方式。 | 品类种草 |
| storeEvent | 门店活动宣传 | 适合门店开业、周年庆、季末清仓等活动宣传，吸引到店消费。 | 活动促销 |

右侧摘要：

- 已选行业：服装销售，右侧 `修改`。
- 推荐方向标题：`推荐方向（可多选）`。
- 已选择计数：随选择数实时更新，展示 `已选择 N/3`，N ∈ 0..3。
- 默认选中前 3 个方向，与参考图一致；初始进入页面展示 `已选择 3/3`。
- 用户主动取消方向后计数递减，`0/3` 时主按钮禁用（与下方交互项一致）。
- 已选方向列表：缩略图、名称、单行省略描述、删除按钮。
- 次按钮：`清空选择`。
- 主按钮：`下一步：选择模板`。

交互：

- 默认选中前 3 个方向，与参考图一致。
- 最多选择 3 个方向，超过时提示 `最多选择 3 个方向`。
- 点击已选卡片或摘要删除按钮可取消。
- 点击清空选择后主按钮禁用。
- 点击修改行业返回 `/video/create/industry`，并清空方向和模板。
- 点击下一步进入 `/video/create/templates`。

状态：

- 未选择方向：摘要为空列表，主按钮禁用。
- 分类无结果：左侧展示 Empty，摘要保持。
- 失败：主区错误态，摘要仍展示当前本地选择。

## 第 3 步：选择模板

页面目标：基于行业和方向推荐模板，并展示模板详情和素材要求。

布局：

- 标题：`选择模板`。
- 副标题：`选择合适的模板，快速生成高质量视频内容`。
- 主体左侧为筛选和模板网格，右侧为模板详情面板。
- 顶部筛选行包含：`推荐`、`最新`、`热门`、`15s`、`30s`、`60s`。
- 主题筛选行包含：`全部`、`服饰穿搭`、`美妆护肤`、`食品饮料`、`家居生活`、`3C数码`、`文旅探店`、`节日热点`、`企业宣传`、`教育培训`。
- 模板网格为 3 列 x 2 行。
- 底部分页按参考图展示上一页、页码、省略号、下一页。mock 阶段仅 6 条数据放满一页，分页控件按参考图静态展示视觉，翻页交互禁用（按钮置灰）或循环展示同 6 条；后续接后端时再实现真实分页。

模板卡片字段：

- 封面图。
- 左下时长标签，如 `30s`。
- 标题。
- 分类标签。
- 描述。
- 推荐使用标识。
- 选中态。

固定 mock 模板：

| id | 标题 | 分类 | 时长 | 描述 |
| --- | --- | --- | --- | --- |
| fashionNew30 | 女装新品种草30s | 服饰穿搭 | 30s | 适合新品上市，突出穿搭亮点与搭配场景 |
| storeVisit | 门店探店模板 | 文旅探店 | 30s | 适合门店探店、探店打卡类内容创作 |
| fabricDetail | 面料细节展示 | 服饰穿搭 | 30s | 聚焦面料细节与质感，增强产品信任感 |
| festivalPromo | 节日促销模板 | 节日热点 | 30s | 节日氛围营销，适合大促活动宣传 |
| beautyProduct | 美妆产品种草 | 美妆护肤 | 30s | 突出产品功效与使用场景，提升转化 |
| homeRecommend | 家居好物推荐 | 家居生活 | 30s | 适合家居好物推荐，展示使用场景 |

右侧模板详情：

- 标题：当前模板标题。
- 关闭图标。
- 大封面预览，中央播放按钮。
- 模板描述。
- 适用场景标签：`新品上市`、`产品种草`、`穿搭推荐`、`门店宣传`。
- 所需素材列表：
  - 店铺门头展示，建议时长 3s 左右，数量 1 个。
  - 产品细节特写，建议时长 3-8s，数量 1-2 个。
  - 模特上身展示，建议时长 8-15s，数量 1 个。
- `查看全部素材要求` 链接。
- 主按钮：`使用该模板`。
- 次按钮：`预览模板效果`。

交互：

- 默认选中 `女装新品种草30s`。
- 点击模板更新选中态和右侧详情。
- 筛选条件变化后列表本地过滤并默认选中第一项；无结果显示 Empty。
- 点击播放按钮展示播放中状态或打开预览弹窗。
- 点击使用该模板进入 `/video/create/workspace`。
- 点击预览模板效果打开 Modal，展示模板封面和描述。

## 第 4 步：工作台

页面目标：基于模板编辑分镜、镜头素材和镜头文案，保存草稿或提交生成。

布局按参考图实现：

- 顶部标题：`模板：女装新品种草 30s`，带下拉箭头。
- 保存状态：绿色图标 + `已保存`。
- 右上按钮：`保存草稿`、蓝色主按钮 `生成成片`。
- 内容 tabs：`基础`、`分镜`，当前参考图高亮 `分镜`。
- 主体三栏：
  - 左侧：分镜列表。
  - 中间：当前镜头预览、素材替换、当前镜头素材。
  - 右侧：镜头标题、画面提示词、文案内容、AI 镜头助手。
- 底部：镜头顺序横向时间线和 `全部预览`。

分镜列表固定 mock：

| 序号 | 名称 | 说明 | 时间 |
| --- | --- | --- | --- |
| 1 | 店铺门头展示 | 镜头拉近，展示店铺门头与招牌，凸显时尚的购物氛围。 | 0s-3s |
| 2 | 产品细节特写 | 特写粉色纹理的裙摆刺绣工艺细节，展现纹理、品质感。 | 3s-8s |
| 3 | 模特上身展示 | 模特近距离展示，贴合眼观通气质色轮廓，传递气质。 | 8s-15s |
| 4 | 面料细节特写 | 微距拍摄面料纹理与质感，强调细腻、彰显价值。 | 15s-22s |
| 5 | 品牌结尾页 | 品牌 LOGO 与 Slogan 收尾，MISS88 画面舒缓。 | 22s-30s |

当前镜头编辑字段：

- 镜头标题：`店铺门头展示`，计数 `7/30`。
- 画面提示词：`镜头拉近，展示店铺门头与招牌，整体风格温暖明亮，吸引用户进店。`，计数 `29/200`。
- 文案内容：`走进时尚女装空间，遇见更美的自己`，计数 `18/50`。

素材操作：

- 主按钮：`替换图片`。
- 次按钮：`上传素材`。
- 次按钮：`从素材库选择`。
- 当前镜头素材展示 4 张缩略图，选中第一张。
- 只做 mock 交互：点击替换、上传、素材库按钮展示对应 Modal 或 message，不真实上传。

AI 镜头助手：

- 卡片 1：`重生成的镜头`，说明和按钮 `重新生成`。
- 卡片 2：`优化镜头文案`，说明和按钮 `优化文案`。
- 点击按钮更新当前镜头文案或提示词，并将保存状态改为未保存。

交互：

- 点击左侧分镜或底部镜头顺序切换当前镜头。
- 修改标题、提示词、文案后保存状态变为 `未保存`。
- 点击保存草稿后保存状态恢复 `已保存`，展示成功 message。
- 点击生成成片前校验 5 个分镜均有标题、提示词、文案和素材。素材校验口径：每个镜头至少关联 1 张素材即通过，不要求选满参考图展示的 4 张。
- 校验通过后创建本地 mock 任务并进入 `/video/create/result`。
- 校验失败时展示缺失项提示，不进入下一步。
- `全部预览` 打开预览 Modal，按镜头顺序展示分镜。

状态：

- 加载：主体三栏 skeleton。
- 空：无分镜时展示 Empty 和重新加载模板按钮。
- 失败：Result 错误态和重试按钮。
- 权限不足：无权限 Result。

## 第 5 步：生成成片

页面目标：展示生成结果、输出参数、发布入口和我的作品。

布局按参考图实现：

- 顶部标题区：缩略图、标题 `女装新品种草 30s`、绿色状态 `已生成`、生成时间 `生成于 2024-05-24 15:30`。
- 右上按钮：`返回修改`、`保存为我的模板`。
- 左侧大视频播放器区域，比例按参考图横向展示。
- 视频画面中央播放按钮，底部控制条包含播放、时间、进度、音量、设置、全屏。
- 播放器下方版本切换：`版本 A`、`版本 B`、`版本 C`。
- 时间线缩略图：`00:00` 到 `00:30`。
- 操作按钮：`下载 1080P`、`导出`、`重新生成`。
- 下方：`我的作品` 横向列表。
- 右侧卡片 1：生成进度。
- 右侧卡片 2：一键发布。

生成进度卡：

- 标题：`生成进度`。
- 进度条 `100%`。
- 状态：绿色图标 + `视频生成完成`。
- 输出信息：
  - `9:16` 比例。
  - `30s` 时长。
  - `1080P` 分辨率。
- 详情：
  - 使用模板：女装新品种草 30s。
  - 素材数量：图片 18 张、视频 6 段。
  - 生成耗时：2 分 18 秒。
  - 发布时间建议：工作日 18:00-21:00。

一键发布卡：

- 平台：抖音、小红书、视频号。
- 默认三项选中。
- 主按钮：`发布到平台`。
- `如何发布?` 链接。
- 首期仅 mock，不真实发布；点击发布展示成功或引导配置账号的 message。

我的作品列表：

| 标题 | 时间 | 时长 | 状态 |
| --- | --- | --- | --- |
| 女装新品种草 30s | 2024-05-24 15:30 | 00:30 | 已生成 |
| 通勤穿搭推荐 28s | 2024-05-23 14:20 | 00:28 | 已生成 |
| 夏日连衣裙合集 25s | 2024-05-22 11:10 | 00:25 | 已生成 |

交互：

- 点击播放按钮切换播放态。
- 版本切换更新播放器缩略图和时间线。
- 下载、导出、重新生成均使用 mock message；重新生成可将进度短暂重置再回到 100%。
- 返回修改进入 `/video/create/workspace`。
- 保存为我的模板展示成功 message。
- 我的作品卡片点击切换当前作品。

状态：

- queued：展示排队进度和等待文案。
- running：展示动态进度和当前生成阶段。
- success：展示参考图完成态。
- failed：展示失败原因和重新生成按钮。
- cancelled：展示已取消状态和返回工作台按钮。

## 前端结构

建议新增：

```text
src/pages/video-create/
  ShellLayout.tsx
  industry.tsx
  directions.tsx
  templates.tsx
  workspace.tsx
  result.tsx
  components/
    VideoCreateShell.tsx
    VideoCreateSteps.tsx
    VideoCreateSidebar.tsx
    VideoCreateTopbar.tsx
    IndustryCard.tsx
    DirectionCard.tsx
    DirectionSummary.tsx
    TemplateCard.tsx
    TemplateDetailPanel.tsx
    StoryboardList.tsx
    ShotEditor.tsx
    ShotTimeline.tsx
    VideoResultPlayer.tsx
    PublishPanel.tsx
  data.ts
  types.ts
  style.ts
```

状态管理文件（Umi model）：

```text
src/models/videoCreate.ts
```

占位页：

```text
src/pages/placeholders/
  ModulePlaceholder.tsx
  Dashboard.tsx
  Drafts.tsx
  TemplateCenter.tsx
  DigitalHumanImage.tsx
  DigitalHumanVideo.tsx
  VoiceClone.tsx
  Assets.tsx
  Tasks.tsx
  Help.tsx
  Notifications.tsx
  Account.tsx
```

路由：

- 根路径 `/` 可继续按首页规格重定向到 `/dashboard`，但若首页尚未实现，至少不能影响五步流程直达。
- 视频创作菜单默认指向 `/video/create/industry`。
- 五步路由必须在 `config/routes.ts` 中显式关闭默认 ProLayout 外壳，例如每个五步路由配置 `layout: false`。
- 五步路由均由 `VideoCreateShell` 自行渲染左侧导航、顶部栏、步骤条和内容区，避免出现双层侧栏或双层顶部栏。
- 其他首页、草稿箱、模板中心、任务中心等非五步路由仍可继续使用项目默认 ProLayout。

嵌套路由与状态保留：

- 五步路由必须采用嵌套路由结构，而非 5 个平级路由。父路由 `/video/create`（本身 `layout: false`）渲染 `<VideoCreateShell><Outlet /></VideoCreateShell>`，五步作为子路由通过 `<Outlet />` 渲染。状态共享由 Umi `useModel('videoCreate')` 自动处理，无需在 ShellLayout 手动挂 Provider。
- 这样跨步骤导航（如 industry → directions）时，`VideoCreateShell` 不随子路由切换卸载重建，`useModel('videoCreate')` 的共享状态（`industryId`、`directionIds`、`templateId`、`workspaceDraft`、`resultId`）也会话内保留。
- 刷新整个页面时仍从默认 mock 状态恢复（与 line 87 一致），不走持久化。
- `config/routes.ts` 示例结构：
  ```text
  { path: '/video/create', layout: false, component: './video-create/ShellLayout', routes: [
    { path: '/video/create/industry', component: './video-create/industry' },
    { path: '/video/create/directions', component: './video-create/directions' },
    { path: '/video/create/templates', component: './video-create/templates' },
    { path: '/video/create/workspace', component: './video-create/workspace' },
    { path: '/video/create/result', component: './video-create/result' },
  ]}
  ```
- `ShellLayout` 组件职责：渲染 Shell、通过 `<Outlet />` 让子路由内容流入主内容区。Shell 自身（左侧导航、顶部栏、步骤条）不随子路由变化重新渲染，仅步骤条的当前步高亮随路由变化更新。

样式：

- 统一使用 `antd-style` 的 `createStyles` 管理样式，与项目现有 11 处 `createStyles` 使用保持一致。
- 颜色、字号、间距和阴影通过 `createStyles` 的 `({ token, css })` 注入 antd token，抽成局部 token。
- 禁止在组件内散写大量 inline style。
- 对固定格式元素设置稳定尺寸，避免 hover、选中、标签变化造成布局抖动。

## 数据与枚举

集中定义业务类型：

- `VideoCreateStep`：`industry`、`directions`、`templates`、`workspace`、`result`。
- `IndustryId`：`fashion`、`food`、`education`、`realEstate`、`beauty`、`localLife`、`home`、`car`。
- `DirectionId`：`offlineStore`、`ecommerce`、`kidsLive`、`streetBrand`、`underwear`、`storeEvent`。
- `TemplateId`：`fashionNew30`、`storeVisit`、`fabricDetail`、`festivalPromo`、`beautyProduct`、`homeRecommend`。
- `VideoTaskStatus`：`queued`、`running`、`success`、`failed`、`cancelled`。
- `PublishPlatform`：`douyin`、`xiaohongshu`、`shipinhao`。

组件不得直接散写状态字符串、颜色映射和文案映射。

## 资产清单

实现计划必须先完成资产准备，再进入布局实现。

| 资产 | 用途 | 建议比例 | 首选来源 | 落盘建议 |
| --- | --- | --- | --- | --- |
| 工作台 Logo | 顶部品牌 | 横向，高度匹配顶部栏 60px | 参考图裁切或重绘为 SVG | `public/video-create-assets/logo.png` |
| 行业封面 8 张 | 第 1 步行业卡片（4 列网格） | 16:9 | 参考图裁切 | `public/video-create-assets/industries/*.png` |
| 方向封面 6 张 | 第 2 步方向卡片和摘要（2 列网格） | 16:9 | 参考图裁切 | `public/video-create-assets/directions/*.png` |
| 模板封面 6 张 | 第 3 步模板卡片（3 列网格） | 16:9 | 参考图裁切 | `public/video-create-assets/templates/*.png` |
| 分镜缩略图 5 张 | 第 4 步分镜列表和时间线 | 16:9 | 参考图裁切 | `public/video-create-assets/shots/*.png` |
| 当前镜头素材 4 张 | 第 4 步素材区 | 1:1 或 4:3 | 参考图裁切 | `public/video-create-assets/shot-assets/*.png` |
| 成片竖版封面 | 第 5 步播放器主画面 | 9:16（与 line 349 视频比例一致） | 参考图裁切 | `public/video-create-assets/results/cover-vertical.png` |
| 成片时间线缩略图 | 第 5 步作品列表、时间线 | 16:9 | 参考图裁切 | `public/video-create-assets/results/timeline/*.png` |
| 用户头像 | 顶部右上角 | 1:1 | 参考图裁切或统一 mock 头像 | `public/video-create-assets/avatar.png` |
| 平台图标 | 一键发布 | 1:1 | 可用品牌近似图标或本地 SVG | `public/video-create-assets/platforms/*.svg` |

比例以参考图裁切为准；具体像素值由开发在裁切阶段按参考图反推，规格不强制绝对像素。

要求：

- 实现计划第一步必须校验参考图存在，裁切所需资产并提交到 `public/video-create-assets/`。
- 页面不得引用 `D:/Workspace/.../设计稿` 这类本机绝对路径，也不得运行时读取未跟踪设计稿目录。
- 资产引用路径集中定义，不散落在组件中。
- 裁切资产不得拉伸变形。
- 若裁切清晰度不足，使用同构生成图替换，但构图、颜色和视觉密度必须接近参考图。

## API 与后端边界

本次只做前端静态 mock，不新增后端接口。

后续真实接口方向：

- `GET /api/industries`
- `GET /api/industries/{industryId}/directions`
- `GET /api/templates`
- `GET /api/templates/{templateId}`
- `POST /api/drafts`
- `PUT /api/drafts/{draftId}`
- `POST /api/video-generation/tasks`
- `GET /api/tasks/{taskId}`
- `POST /api/outputs/{outputId}/download`

后端实现前必须确认或更新：

- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`

后端规则：

- 不由前端传入 `ownerId`。
- 任务创建必须携带 `idempotencyKey`。
- 生成任务必须进入任务中心可追踪。
- 素材预览、下载和任务结果读取必须校验账号归属。
- 额度校验、冻结、扣减、退回以后端为准。

## 页面状态与异常

每一步至少覆盖：

- 首次加载。
- 空数据。
- 搜索或筛选无结果。
- mock 接口失败。
- 权限不足。
- 操作中。
- 操作成功。
- 操作失败。

本地状态触发建议：

- `?mockState=loading`：停留加载态。
- `?mockState=empty`：当前步骤空态。
- `?mockState=error`：当前步骤失败态。
- `?mockState=forbidden`：权限不足态。
- `?taskStatus=queued|running|success|failed|cancelled`：第 5 步任务状态。

这些 query 仅用于本地开发和测试，不进入后端契约。生产构建必须以 `process.env.NODE_ENV === 'development'` 守卫：生产环境下忽略上述 query，统一按正常流程态渲染，避免线上有人手敲 `?mockState=error` 触发错误页。

## 测试要求

最小测试清单：

- 第 1 步行业搜索和行业单选。
- 第 2 步方向最多选择 3 个、摘要同步、清空选择后按钮禁用。
- 第 3 步模板筛选、选中模板后详情同步、使用模板跳转工作台。
- 第 4 步切换分镜、编辑字段后保存状态变化、保存草稿恢复已保存、生成前校验。
- 第 5 步任务状态渲染、版本切换、返回修改跳转、发布平台选择。
- `VideoCreateShell` 在 5 个步骤下正确高亮步骤条和菜单。
- mock 状态 query 可触发加载、空、失败、权限不足。

验证命令：

- `npm.cmd run tsc`
- `npm.cmd run test -- video-create`
- `npm.cmd run lint`

视觉验证：

- 启动本地 dev server。
- 使用 in-app browser 固定 `1680 x 946` 视口分别截图 5 个路由。
- 与 5 张参考图做同屏对比。
- 对明显偏差继续调整，不以“差不多”作为完成。

## 协作切分

前后端并行策略：

- 前端先用集中 mock 数据完成五步可交互流程和视觉还原。
- 后端暂不开发真实接口，但需参与 review 数据字段、任务状态、额度和素材归属边界。
- 若后续接真实接口，先更新公共契约，再替换 mock service。

建议任务拆分：

- 开发 A：流程壳层、路由、左侧导航、顶部栏、步骤条。
- 开发 B：第 1-3 步选择行业、方向、模板。
- 开发 C：第 4-5 步工作台、生成成片、视觉资产和截图验证。

必须 review 的契约变化：

- 行业、方向、模板、分镜、成片结果字段。
- 任务状态枚举。
- 素材引用和文件访问规则。
- 额度校验和生成任务创建规则。

## 验收标准

视觉验收：

- 5 个页面在 `1680 x 946` 视口下与对应参考图布局、样式、图标、卡片、图片和内容密度一致。
- 左侧导航、顶部步骤条、右上角入口均纳入一比一验收。
- 选中态、按钮态、标签、边框、阴影、圆角和间距与参考图匹配。
- 不出现 Ant Design Pro 模板欢迎页、模板菜单或默认顶部栏视觉。

功能验收：

- 用户能从第 1 步顺畅走到第 5 步。
- 第 2 步最多选 3 个方向。
- 第 3 步模板详情与选中模板一致。
- 第 4 步能保存草稿、切换镜头、编辑镜头字段、校验并进入生成结果。
- 第 5 步能展示完成态、预览区、输出信息、一键发布和作品列表。
- 所有导航入口不 404。
- 所有 mock 异常状态可触发并有明确视觉反馈。

## 规格自检

- 无待定占位符或 TODO。
- 范围聚焦在视频创作五步流程，可由一份实现计划覆盖。
- 已明确旧首页规格的 ProLayout 限制不适用于本规格。
- 已明确五步路由关闭默认 ProLayout，由 `VideoCreateShell` 承担完整页面壳层。
- 已明确跨步骤直达路由的 mock 守卫规则。
- 已明确第 1 步不使用不可见跳转入口。
- 已明确裁切资产必须落盘到 `public/video-create-assets/` 并由页面引用版本化资产。
- 已覆盖前端、后端边界和协作契约。
- 已明确视觉资产、测试、状态和验收要求。
