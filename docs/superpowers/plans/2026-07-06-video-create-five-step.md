# 视频创作五步流程实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 基于 `docs/superpowers/specs/2026-07-06-video-create-five-step-design.md` 实现视频创作五步流程（选择行业 → 选择方向 → 选择模板 → 工作台 → 生成成片），完整前端可交互 mock，视觉对照 5 张 v2 参考图一比一还原。

**架构：** Umi Max 嵌套路由——父路由 `/video/create` 渲染 `ShellLayout`（Shell + Outlet），五步作为子路由。跨步骤状态用 Umi `useModel('videoCreate')` 共享（无需自建 Provider）。样式统一 `antd-style` 的 `createStyles`。mock 数据 co-located 在 `src/pages/video-create/data.ts`，后续替换为 `mock/` + 真实接口。

**技术栈：**
- React 19.2 + TypeScript 6.0
- antd 6.5 + @ant-design/pro-components 3.1 + @ant-design/icons 6.3
- Umi Max 4.6（`model` 插件已开启）
- antd-style 4.1（`createStyles`）
- Vitest 4.1 + @testing-library/react 16（**不是 Jest**）
- Biome（lint）

**规格权威：** 本计划与规格冲突时以规格为准；执行中发现规格问题按 `receiving-code-review` 纪律处理。规格关键决策：状态用 Umi useModel、样式用 antd-style、路由嵌套、五步路由 `layout: false`。

**路径约定：** 以下所有路径相对仓库根 `d:\Workspace\ai\projects\ai-video\`，前端工作目录为 `ai-video-ui/ai-video-webapp/`。

**参考图源：**
- 第1步：`D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 11_26_55 (2).png`
- 第2步：`D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 11_26_57 (6).png`
- 第3步：`D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月2日 11_26_56 (3).png`
- 第4步：`D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月1日 15_32_32 (5).png`
- 第5步：`D:/Workspace/ai/projects/设计稿/v2/ChatGPT Image 2026年7月4日 16_41_51.png`

**验证基线命令（每个任务结束时按需运行）：**
- 类型检查：`npm.cmd run tsc`（在 `ai-video-ui/ai-video-webapp/` 下）
- 单测：`npm.cmd run test -- video-create`（Vitest 过滤）
- Lint：`npm.cmd run lint`

---

## 文件结构总览

新建/修改文件清单（锁定分解决策）：

```text
ai-video-ui/ai-video-webapp/
├── config/routes.ts                                    [修改] 改为嵌套结构
├── public/video-create-assets/                         [新建] 裁切资产落盘
│   ├── logo.png
│   ├── avatar.png
│   ├── industries/*.png (8)
│   ├── directions/*.png (6)
│   ├── templates/*.png (6)
│   ├── shots/*.png (5)
│   ├── shot-assets/*.png (4)
│   ├── results/cover-vertical.png
│   ├── results/timeline/*.png
│   └── platforms/*.svg (3)
├── src/
│   ├── models/
│   │   └── videoCreate.ts                              [新建] Umi model 共享状态
│   └── pages/video-create/
│       ├── ShellLayout.tsx                             [新建] 父路由组件
│       ├── VideoCreateShell.tsx                        [新建] 壳层组合
│       ├── industry/index.tsx                          [修改] 替换占位
│       ├── directions/index.tsx                        [新建]
│       ├── templates/index.tsx                         [新建]
│       ├── workspace/index.tsx                         [新建]
│       ├── result/index.tsx                            [新建]
│       ├── components/
│       │   ├── VideoCreateSidebar.tsx                  [新建]
│       │   ├── VideoCreateTopbar.tsx                   [新建]
│       │   ├── VideoCreateSteps.tsx                    [新建]
│       │   ├── IndustryCard.tsx                        [新建]
│       │   ├── DirectionCard.tsx                       [新建]
│       │   ├── DirectionSummary.tsx                    [新建]
│       │   ├── TemplateCard.tsx                        [新建]
│       │   ├── TemplateDetailPanel.tsx                 [新建]
│       │   ├── StoryboardList.tsx                      [新建]
│       │   ├── ShotEditor.tsx                          [新建]
│       │   ├── ShotTimeline.tsx                        [新建]
│       │   ├── VideoResultPlayer.tsx                   [新建]
│       │   └── PublishPanel.tsx                        [新建]
│       ├── data.ts                                     [新建] 集中 mock 数据
│       ├── types.ts                                    [新建] 枚举与类型
│       └── style.ts                                    [新建] 共享 token
```

占位页（任务 20）：`src/pages/placeholders/` 下补 `Dashboard.tsx`、`Drafts.tsx` 等。

---

## 阶段 0：基础设施（任务 1-4）

这一阶段产出共享类型、mock 数据、状态 model、嵌套路由骨架。后续所有页面任务依赖这些。

### 任务 1：资产准备

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/public/video-create-assets/**`

**说明：** 本任务无可执行测试代码（资产是二进制），通过文件存在性校验验收。执行者需可读取参考图源目录（本机绝对路径）。

- [ ] **步骤 1：校验参考图存在**

运行（PowerShell）：
```powershell
$refs = @(
  "D:\Workspace\ai\projects\设计稿\v2\ChatGPT Image 2026年7月2日 11_26_55 (2).png",
  "D:\Workspace\ai\projects\设计稿\v2\ChatGPT Image 2026年7月2日 11_26_57 (6).png",
  "D:\Workspace\ai\projects\设计稿\v2\ChatGPT Image 2026年7月2日 11_26_56 (3).png",
  "D:\Workspace\ai\projects\设计稿\v2\ChatGPT Image 2026年7月1日 15_32_32 (5).png",
  "D:\Workspace\ai\projects\设计稿\v2\ChatGPT Image 2026年7月4日 16_41_51.png"
)
$refs | ForEach-Object { if (Test-Path $_) { Write-Host "OK: $_" } else { Write-Host "MISSING: $_" } }
```
预期：5 行 `OK:`。任何 `MISSING:` 必须先补齐参考图再继续。

- [ ] **步骤 2：创建资产目录结构**

运行：
```powershell
$base = "ai-video-ui\ai-video-webapp\public\video-create-assets"
@("industries","directions","templates","shots","shot-assets","results\timeline","platforms") | ForEach-Object { New-Item -ItemType Directory -Force -Path "$base\$_" } | Out-Null
```

- [ ] **步骤 3：裁切并落盘资产**

按规格 line 475-486 的比例，用图像编辑器（执行者手动或脚本）从参考图裁切并保存为 PNG/SVG：

| 资产 | 数量 | 建议比例 | 文件名规则 |
| --- | --- | --- | --- | --- |
| 工作台 Logo | 1 | 横向，高度匹配 60px 顶部栏 | `logo.png` |
| 行业封面 | 8 | 16:9 | `industries/{fashion,food,education,realEstate,beauty,localLife,home,car}.png` |
| 方向封面 | 6 | 16:9 | `directions/{offlineStore,ecommerce,kidsLive,streetBrand,underwear,storeEvent}.png` |
| 模板封面 | 6 | 16:9 | `templates/{fashionNew30,storeVisit,fabricDetail,festivalPromo,beautyProduct,homeRecommend}.png` |
| 分镜缩略图 | 5 | 16:9 | `shots/{1..5}.png` |
| 当前镜头素材 | 4 | 1:1 或 4:3 | `shot-assets/{1..4}.png` |
| 成片竖版封面 | 1 | 9:16 | `results/cover-vertical.png` |
| 成片时间线缩略图 | 3 | 16:9 | `results/timeline/{1..3}.png` |
| 用户头像 | 1 | 1:1 | `avatar.png` |
| 平台图标 | 3 | 1:1 | `platforms/{douyin,xiaohongshu,shipinhao}.svg` |

文件名必须与规格 mock 数据的 id 字段一致（见任务 2）。

- [ ] **步骤 4：校验资产落盘**

运行：
```powershell
$base = "ai-video-ui\ai-video-webapp\public\video-create-assets"
$expected = @(
  "$base\logo.png","$base\avatar.png",
  "$base\industries\fashion.png","$base\industries\food.png","$base\industries\education.png","$base\industries\realEstate.png","$base\industries\beauty.png","$base\industries\localLife.png","$base\industries\home.png","$base\industries\car.png",
  "$base\directions\offlineStore.png","$base\directions\ecommerce.png","$base\directions\kidsLive.png","$base\directions\streetBrand.png","$base\directions\underwear.png","$base\directions\storeEvent.png",
  "$base\templates\fashionNew30.png","$base\templates\storeVisit.png","$base\templates\fabricDetail.png","$base\templates\festivalPromo.png","$base\templates\beautyProduct.png","$base\templates\homeRecommend.png",
  "$base\shots\1.png","$base\shots\2.png","$base\shots\3.png","$base\shots\4.png","$base\shots\5.png",
  "$base\shot-assets\1.png","$base\shot-assets\2.png","$base\shot-assets\3.png","$base\shot-assets\4.png",
  "$base\results\cover-vertical.png","$base\results\timeline\1.png","$base\results\timeline\2.png","$base\results\timeline\3.png",
  "$base\platforms\douyin.svg","$base\platforms\xiaohongshu.svg","$base\platforms\shipinhao.svg"
)
$missing = $expected | Where-Object { -not (Test-Path $_) }
if ($missing) { Write-Host "MISSING:"; $missing | ForEach-Object { Write-Host "  $_" } } else { Write-Host "ALL OK" }
```
预期：`ALL OK`。

- [ ] **步骤 5：Commit**

```bash
cd ai-video-ui/ai-video-webapp
git add public/video-create-assets/
git commit -m "chore(video-create): 裁切并落盘五步流程视觉资产"
```

---

### 任务 2：类型与 mock 数据

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/data.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/types.test.ts`

- [ ] **步骤 1：编写 types.ts**

```ts
// src/pages/video-create/types.ts

/** 视频创作五步流程步骤标识 */
export type VideoCreateStep = 'industry' | 'directions' | 'templates' | 'workspace' | 'result';

/** 行业 id 枚举 */
export type IndustryId = 'fashion' | 'food' | 'education' | 'realEstate' | 'beauty' | 'localLife' | 'home' | 'car';

/** 方向 id 枚举 */
export type DirectionId = 'offlineStore' | 'ecommerce' | 'kidsLive' | 'streetBrand' | 'underwear' | 'storeEvent';

/** 模板 id 枚举 */
export type TemplateId = 'fashionNew30' | 'storeVisit' | 'fabricDetail' | 'festivalPromo' | 'beautyProduct' | 'homeRecommend';

/** 视频生成任务状态 */
export type VideoTaskStatus = 'queued' | 'running' | 'success' | 'failed' | 'cancelled';

/** 发布平台 */
export type PublishPlatform = 'douyin' | 'xiaohongshu' | 'shipinhao';

/** 行业图标色（规格 line 121-130） */
export type IndustryAccent = 'blue' | 'orange' | 'green' | 'purple' | 'pink';

/** 行业实体 */
export interface Industry {
  id: IndustryId;
  name: string;
  description: string;
  usageCount: string;
  accent: IndustryAccent;
  cover: string;
}

/** 方向分类 tab（规格 line 156） */
export type DirectionCategory = 'all' | 'sales' | 'content' | 'goal';

/** 方向实体 */
export interface Direction {
  id: DirectionId;
  name: string;
  description: string;
  tag: string;
  category: DirectionCategory;
  cover: string;
}

/** 模板分类（规格 line 216） */
export type TemplateTheme =
  | 'all' | 'clothing' | 'beauty' | 'food' | 'home'
  | 'digital' | 'travel' | 'festival' | 'enterprise' | 'education';

/** 模板时长筛选 */
export type TemplateDuration = 'all' | '15s' | '30s' | '60s';

/** 模板排序 */
export type TemplateSort = 'recommended' | 'latest' | 'hot';

/** 模板实体 */
export interface Template {
  id: TemplateId;
  title: string;
  theme: Exclude<TemplateTheme, 'all'>;
  duration: '15s' | '30s' | '60s';
  description: string;
  cover: string;
  recommended: boolean;
}

/** 分镜镜头 */
export interface Shot {
  order: number;
  name: string;
  description: string;
  timeRange: string;
  thumbnail: string;
  /** 用户可编辑字段 */
  title: string;
  prompt: string;
  copy: string;
  /** 关联素材 id 列表（mock 阶段为 shot-assets 文件名 stem） */
  assetIds: string[];
}

/** 工作台草稿 */
export interface WorkspaceDraft {
  templateId: TemplateId;
  templateTitle: string;
  shots: Shot[];
  /** 当前选中镜头序号（1-based） */
  currentShotOrder: number;
  saved: boolean;
}

/** 生成结果详情 */
export interface ResultDetail {
  templateTitle: string;
  imageCount: number;
  videoCount: number;
  elapsed: string;
  publishTip: string;
}

/** 成片版本 */
export interface ResultVersion {
  id: 'A' | 'B' | 'C';
  label: string;
  cover: string;
}

/** 作品列表项 */
export interface WorkItem {
  id: string;
  title: string;
  createdAt: string;
  duration: string;
  status: 'success' | 'running' | 'failed';
  cover: string;
}

/** 资产路径常量集中定义（规格 line 494） */
export const ASSET = {
  logo: '/video-create-assets/logo.png',
  avatar: '/video-create-assets/avatar.png',
  industry: (id: IndustryId) => `/video-create-assets/industries/${id}.png`,
  direction: (id: DirectionId) => `/video-create-assets/directions/${id}.png`,
  template: (id: TemplateId) => `/video-create-assets/templates/${id}.png`,
  shot: (order: number) => `/video-create-assets/shots/${order}.png`,
  shotAsset: (stem: string) => `/video-create-assets/shot-assets/${stem}.png`,
  resultCover: '/video-create-assets/results/cover-vertical.png',
  resultTimeline: (stem: string) => `/video-create-assets/results/timeline/${stem}.png`,
  platform: (id: PublishPlatform) => `/video-create-assets/platforms/${id}.svg`,
} as const;
```

- [ ] **步骤 2：编写 types.test.ts（类型与资产路径函数校验）**

```ts
// src/pages/video-create/types.test.ts
import { describe, it, expect } from 'vitest';
import { ASSET, type IndustryId, type DirectionId } from './types';

describe('video-create types & ASSET', () => {
  it('ASSET.industry 返回正确路径', () => {
    expect(ASSET.industry('fashion' as IndustryId)).toBe('/video-create-assets/industries/fashion.png');
  });

  it('ASSET.direction 返回正确路径', () => {
    expect(ASSET.direction('offlineStore' as DirectionId)).toBe('/video-create-assets/directions/offlineStore.png');
  });

  it('ASSET.shot 按 order 拼路径', () => {
    expect(ASSET.shot(3)).toBe('/video-create-assets/shots/3.png');
  });
});
```

- [ ] **步骤 3：运行测试验证通过**

运行：
```bash
cd ai-video-ui/ai-video-webapp
npm.cmd run test -- video-create/types.test.ts
```
预期：3 个测试通过。

- [ ] **步骤 4：编写 data.ts（mock 数据，对照规格 5 张表）**

```ts
// src/pages/video-create/data.ts
import type {
  Industry, Direction, Template, Shot, WorkItem, ResultVersion, PublishPlatform,
} from './types';
import { ASSET } from './types';

/** 规格 line 121-130：8 个行业 */
export const INDUSTRIES: Industry[] = [
  { id: 'fashion', name: '服装销售', description: '适用于服装、鞋帽、箱包等时尚类产品的营销推广', usageCount: '12.5w 次', accent: 'blue', cover: ASSET.industry('fashion') },
  { id: 'food', name: '餐饮美食', description: '适用于餐厅、小吃、饮品等餐饮美食行业的宣传推广', usageCount: '11.8w 次', accent: 'orange', cover: ASSET.industry('food') },
  { id: 'education', name: '教育培训', description: '适用于教育机构、课程培训、技能学习等场景', usageCount: '9.8w 次', accent: 'green', cover: ASSET.industry('education') },
  { id: 'realEstate', name: '房地产', description: '适用于楼盘展示、房产中介、装修设计等场景', usageCount: '8.6w 次', accent: 'purple', cover: ASSET.industry('realEstate') },
  { id: 'beauty', name: '美妆护肤', description: '适用于化妆品、护肤品、美容美发等行业', usageCount: '7.3w 次', accent: 'pink', cover: ASSET.industry('beauty') },
  { id: 'localLife', name: '本地生活', description: '适用于本地服务、团购、生活服务等场景', usageCount: '6.9w 次', accent: 'blue', cover: ASSET.industry('localLife') },
  { id: 'home', name: '家居建材', description: '适用于家具、家装、建材等家居行业', usageCount: '5.7w 次', accent: 'orange', cover: ASSET.industry('home') },
  { id: 'car', name: '汽车', description: '适用于汽车销售、汽车服务、配件等行业', usageCount: '5.2w 次', accent: 'purple', cover: ASSET.industry('car') },
];

/** 规格 line 171-178：6 个方向（category 用于第 2 步 tab 过滤，mock 全归 all） */
export const DIRECTIONS: Direction[] = [
  { id: 'offlineStore', name: '线下女装买手店', description: '适合定位中高端的女装买手店，突出店铺风格、穿搭推荐与会员服务。', tag: '线下门店', category: 'all', cover: ASSET.direction('offlineStore') },
  { id: 'ecommerce', name: '线上女装电商', description: '适合在主流电商平台经营的女装商家，突出爆款推荐、上新种草与优惠促销。', tag: '线上店铺', category: 'all', cover: ASSET.direction('ecommerce') },
  { id: 'kidsLive', name: '童装直播带货', description: '聚焦童装直播场景，适合通过直播展示产品细节、互动答疑与限时秒杀。', tag: '直播带货', category: 'all', cover: ASSET.direction('kidsLive') },
  { id: 'streetBrand', name: '潮牌街头', description: '适合潮牌、原创设计品牌，突出品牌调性、穿搭展示与潮流文化内容。', tag: '品牌宣传', category: 'all', cover: ASSET.direction('streetBrand') },
  { id: 'underwear', name: '内衣家居服', description: '适合内衣、家居服商家，突出舒适体验、面料质感与生活方式。', tag: '品类种草', category: 'all', cover: ASSET.direction('underwear') },
  { id: 'storeEvent', name: '门店活动宣传', description: '适合门店开业、周年庆、季末清仓等活动宣传，吸引到店消费。', tag: '活动促销', category: 'all', cover: ASSET.direction('storeEvent') },
];

/** 规格 line 232-239：6 个模板 */
export const TEMPLATES: Template[] = [
  { id: 'fashionNew30', title: '女装新品种草30s', theme: 'clothing', duration: '30s', description: '适合新品上市，突出穿搭亮点与搭配场景', cover: ASSET.template('fashionNew30'), recommended: true },
  { id: 'storeVisit', title: '门店探店模板', theme: 'travel', duration: '30s', description: '适合门店探店、探店打卡类内容创作', cover: ASSET.template('storeVisit'), recommended: false },
  { id: 'fabricDetail', title: '面料细节展示', theme: 'clothing', duration: '30s', description: '聚焦面料细节与质感，增强产品信任感', cover: ASSET.template('fabricDetail'), recommended: false },
  { id: 'festivalPromo', title: '节日促销模板', theme: 'festival', duration: '30s', description: '节日氛围营销，适合大促活动宣传', cover: ASSET.template('festivalPromo'), recommended: true },
  { id: 'beautyProduct', title: '美妆产品种草', theme: 'beauty', duration: '30s', description: '突出产品功效与使用场景，提升转化', cover: ASSET.template('beautyProduct'), recommended: false },
  { id: 'homeRecommend', title: '家居好物推荐', theme: 'home', duration: '30s', description: '适合家居好物推荐，展示使用场景', cover: ASSET.template('homeRecommend'), recommended: false },
];

/** 默认工作台草稿（规格 line 283-295），用于直接访问 /video/create/workspace 的 mock 守卫 */
export function buildDefaultDraft(): Shot[] {
  return [
    { order: 1, name: '店铺门头展示', description: '镜头拉近，展示店铺门头与招牌，凸显时尚的购物氛围。', timeRange: '0s-3s', thumbnail: ASSET.shot(1), title: '店铺门头展示', prompt: '镜头拉近，展示店铺门头与招牌，整体风格温暖明亮，吸引用户进店。', copy: '走进时尚女装空间，遇见更美的自己', assetIds: ['1'] },
    { order: 2, name: '产品细节特写', description: '特写粉色纹理的裙摆刺绣工艺细节，展现纹理、品质感。', timeRange: '3s-8s', thumbnail: ASSET.shot(2), title: '产品细节特写', prompt: '特写裙摆刺绣工艺细节，展现纹理与品质感。', copy: '精致刺绣，每一针都是匠心', assetIds: ['1'] },
    { order: 3, name: '模特上身展示', description: '模特近距离展示，贴合眼观通气质色轮廓，传递气质。', timeRange: '8s-15s', thumbnail: ASSET.shot(3), title: '模特上身展示', prompt: '模特近距离展示穿搭，传递气质。', copy: '穿出属于你的气质', assetIds: ['1'] },
    { order: 4, name: '面料细节特写', description: '微距拍摄面料纹理与质感，强调细腻、彰显价值。', timeRange: '15s-22s', thumbnail: ASSET.shot(4), title: '面料细节特写', prompt: '微距拍摄面料纹理与质感。', copy: '触手可及的质感', assetIds: ['1'] },
    { order: 5, name: '品牌结尾页', description: '品牌 LOGO 与 Slogan 收尾，MISS88 画面舒缓。', timeRange: '22s-30s', thumbnail: ASSET.shot(5), title: '品牌结尾页', prompt: '品牌 LOGO 与 Slogan 收尾。', copy: 'MISS88，遇见更美的自己', assetIds: ['1'] },
  ];
}

/** 当前镜头素材区（规格 line 302），固定 4 张 */
export const SHOT_ASSETS = ['1', '2', '3', '4'].map((s) => ASSET.shotAsset(s));

/** 成片版本（规格 line 338） */
export const RESULT_VERSIONS: ResultVersion[] = [
  { id: 'A', label: '版本 A', cover: ASSET.resultCover },
  { id: 'B', label: '版本 B', cover: ASSET.resultCover },
  { id: 'C', label: '版本 C', cover: ASSET.resultCover },
];

/** 作品列表（规格 line 370-374） */
export const WORK_ITEMS: WorkItem[] = [
  { id: 'w1', title: '女装新品种草 30s', createdAt: '2024-05-24 15:30', duration: '00:30', status: 'success', cover: ASSET.resultTimeline('1') },
  { id: 'w2', title: '通勤穿搭推荐 28s', createdAt: '2024-05-23 14:20', duration: '00:28', status: 'success', cover: ASSET.resultTimeline('2') },
  { id: 'w3', title: '夏日连衣裙合集 25s', createdAt: '2024-05-22 11:10', duration: '00:25', status: 'success', cover: ASSET.resultTimeline('3') },
];

/** 发布平台（规格 line 362） */
export const PUBLISH_PLATFORMS: { id: PublishPlatform; label: string }[] = [
  { id: 'douyin', label: '抖音' },
  { id: 'xiaohongshu', label: '小红书' },
  { id: 'shipinhao', label: '视频号' },
];

/** 模板主题中文标签（规格 line 216，TemplateCard 与 templates 页面共用，避免显示英文 key） */
export const THEME_LABEL: Record<Exclude<TemplateTheme, 'all'>, string> = {
  clothing: '服饰穿搭',
  beauty: '美妆护肤',
  food: '食品饮料',
  home: '家居生活',
  digital: '3C数码',
  travel: '文旅探店',
  festival: '节日热点',
  enterprise: '企业宣传',
  education: '教育培训',
};
```

注：`TemplateTheme` 需在 import 列表补入。完整 import 行：
```ts
import type {
  Industry, Direction, Template, Shot, WorkItem, ResultVersion, PublishPlatform, TemplateTheme,
} from './types';
```

- [ ] **步骤 5：Commit**

```bash
git add src/pages/video-create/types.ts src/pages/video-create/types.test.ts src/pages/video-create/data.ts
git commit -m "feat(video-create): 定义五步流程类型与集中 mock 数据"
```

---

### 任务 3：Umi model 共享状态

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/models/videoCreate.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/models/videoCreate.test.ts`

**说明：** 这是项目首个自定义 Umi model（`src/models/` 目录新建）。Umi `useModel` 通过文件名命名空间消费：`useModel('videoCreate')`。

- [ ] **步骤 1：编写失败测试**

```ts
// src/models/videoCreate.test.ts
import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useVideoCreate from './videoCreate';

describe('useVideoCreate model', () => {
  it('初始状态为空（守卫由页面负责补 mock 种子）', () => {
    const { result } = renderHook(() => useVideoCreate());
    expect(result.current.industryId).toBeUndefined();
    expect(result.current.directionIds).toEqual([]);
    expect(result.current.templateId).toBeUndefined();
    expect(result.current.workspaceDraft).toBeUndefined();
    expect(result.current.resultId).toBeUndefined();
  });

  it('setIndustryId 同时清空下游 directionIds/templateId/workspaceDraft/resultId', () => {
    const { result } = renderHook(() => useVideoCreate());
    act(() => {
      result.current.setDirectionIds(['offlineStore', 'ecommerce']);
      result.current.setTemplateId('fashionNew30');
    });
    act(() => {
      result.current.setIndustryId('food');
    });
    expect(result.current.industryId).toBe('food');
    expect(result.current.directionIds).toEqual([]);
    expect(result.current.templateId).toBeUndefined();
    expect(result.current.workspaceDraft).toBeUndefined();
    expect(result.current.resultId).toBeUndefined();
  });

  it('toggleDirection 在 3 个上限内增减', () => {
    const { result } = renderHook(() => useVideoCreate());
    act(() => result.current.toggleDirection('offlineStore'));
    expect(result.current.directionIds).toEqual(['offlineStore']);
    act(() => result.current.toggleDirection('offlineStore'));
    expect(result.current.directionIds).toEqual([]);
  });

  it('toggleDirection 超过 3 个返回 false 不变更', () => {
    const { result } = renderHook(() => useVideoCreate());
    act(() => {
      result.current.toggleDirection('offlineStore');
      result.current.toggleDirection('ecommerce');
      result.current.toggleDirection('kidsLive');
    });
    let ok = true as boolean;
    act(() => { ok = result.current.toggleDirection('streetBrand'); });
    expect(ok).toBe(false);
    expect(result.current.directionIds).toHaveLength(3);
  });

  it('clearDirections 清空并允许主按钮禁用', () => {
    const { result } = renderHook(() => useVideoCreate());
    act(() => result.current.toggleDirection('offlineStore'));
    act(() => result.current.clearDirections());
    expect(result.current.directionIds).toEqual([]);
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
npm.cmd run test -- videoCreate.test.ts
```
预期：FAIL，报错 "Cannot find module './videoCreate'"。

- [ ] **步骤 3：实现 model**

```ts
// src/models/videoCreate.ts
import { useState, useCallback } from 'react';
import type { IndustryId, DirectionId, TemplateId, WorkspaceDraft } from '@/pages/video-create/types';

/** 视频创作五步流程共享状态（规格 line 79-85） */
export default function useVideoCreate() {
  const [industryId, setIndustryIdRaw] = useState<IndustryId | undefined>();
  const [directionIds, setDirectionIds] = useState<DirectionId[]>([]);
  const [templateId, setTemplateId] = useState<TemplateId | undefined>();
  const [workspaceDraft, setWorkspaceDraft] = useState<WorkspaceDraft | undefined>();
  const [resultId, setResultId] = useState<string | undefined>();

  /** 设置行业：同时级联清空下游全部状态（规格 line 95/197 + 修订 S2） */
  const setIndustryId = useCallback((id: IndustryId) => {
    setIndustryIdRaw(id);
    setDirectionIds([]);
    setTemplateId(undefined);
    setWorkspaceDraft(undefined);
    setResultId(undefined);
  }, []);

  /** 切换方向选择，返回是否成功（false = 超上限） */
  const toggleDirection = useCallback((id: DirectionId): boolean => {
    let ok = true;
    setDirectionIds((prev) => {
      if (prev.includes(id)) return prev.filter((d) => d !== id);
      if (prev.length >= 3) { ok = false; return prev; }
      return [...prev, id];
    });
    return ok;
  }, []);

  const clearDirections = useCallback(() => setDirectionIds([]), []);

  return {
    industryId,
    setIndustryId,
    directionIds,
    setDirectionIds,
    toggleDirection,
    clearDirections,
    templateId,
    setTemplateId,
    workspaceDraft,
    setWorkspaceDraft,
    resultId,
    setResultId,
  };
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
npm.cmd run test -- videoCreate.test.ts
```
预期：5 个测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/models/videoCreate.ts src/models/videoCreate.test.ts
git commit -m "feat(video-create): 新增 Umi useModel 共享状态与级联清理"
```

---

### 任务 4：嵌套路由骨架与 ShellLayout

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/config/routes.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/ShellLayout.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/ShellLayout.test.tsx`

**说明：** 把现有平级的 `/video/create/industry` 改造为嵌套结构（规格 line 452-467）。ShellLayout 暂时只渲染 `<Outlet />`，壳层组件在任务 5-9 补齐。

- [ ] **步骤 1：读取当前 routes.ts 确认结构**

运行：
```bash
cd ai-video-ui/ai-video-webapp
```
读取 `config/routes.ts`，确认 `/video/create/industry` 当前是 `video` 父节点下的平级条目（研究确认是这样）。

- [ ] **步骤 2：编写 ShellLayout 测试**

```tsx
// src/pages/video-create/ShellLayout.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ShellLayout from './ShellLayout';

describe('ShellLayout', () => {
  it('渲染子路由 Outlet 内容', () => {
    const { getByText } = render(
      <MemoryRouter initialEntries={['/video/create/industry']}>
        <Routes>
          <Route path="/video/create" element={<ShellLayout />}>
            <Route path="industry" element={<div>INDUSTRY_PAGE</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(getByText('INDUSTRY_PAGE')).toBeTruthy();
  });
});
```

- [ ] **步骤 3：运行测试验证失败**

运行：
```bash
npm.cmd run test -- ShellLayout.test.tsx
```
预期：FAIL，"Cannot find module './ShellLayout'"。

- [ ] **步骤 4：实现 ShellLayout（最小骨架）**

```tsx
// src/pages/video-create/ShellLayout.tsx
import { Outlet } from '@umijs/max';

/**
 * 五步流程父路由布局。
 * 职责：渲染 Shell（任务 5-9 补齐）+ Outlet。
 * 不挂 Provider——状态共享由 Umi useModel 自动处理（规格 line 87）。
 */
export default function ShellLayout() {
  return (
    <Outlet />
  );
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
npm.cmd run test -- ShellLayout.test.tsx
```
预期：1 个测试通过。

- [ ] **步骤 6：改造 config/routes.ts 为嵌套结构**

打开 `config/routes.ts`，把原 `video` 分组下平级的 `/video/create/industry` 删除，替换为独立的 `/video/create` 父节点：

```ts
// config/routes.ts（相关片段）
// ... 其他路由保持不变 ...
{
  path: '/video/create',
  layout: false,
  component: './video-create/ShellLayout',
  routes: [
    { path: '/video/create/industry', component: './video-create/industry' },
    { path: '/video/create/directions', component: './video-create/directions' },
    { path: '/video/create/templates', component: './video-create/templates' },
    { path: '/video/create/workspace', component: './video-create/workspace' },
    { path: '/video/create/result', component: './video-create/result' },
  ],
},
// 原 video 分组保留 drafts、templates 等
```

注意：原 `/video/create/industry` 在 `video` 父节点下，现在挪到新父节点下；`video` 父节点里只留 `/drafts`、`/templates` 等。`视频创作` 菜单项的 `path` 指向 `/video/create/industry`。

- [ ] **步骤 7：临时占位 4 个新页面，确保 tsc 通过**

为 `directions`、`templates`、`workspace`、`result` 创建最小占位 `index.tsx`（后续任务替换）：

```tsx
// src/pages/video-create/directions/index.tsx（临时，任务 11 替换）
export default function DirectionsPage() { return <div>directions placeholder</div>; }
```
对 `templates`、`workspace`、`result` 重复同样模式。`industry/index.tsx` 保持现状（已是 ModulePlaceholder）。

- [ ] **步骤 8：运行 tsc 验证路由配置无类型错误**

运行：
```bash
npm.cmd run tsc
```
预期：无错误。

- [ ] **步骤 9：启动 dev server 手动验证嵌套路由可达**

运行：
```bash
npm.cmd run start
```
浏览器访问 `/video/create/industry`、`/video/create/directions` 等 5 个路由，确认都能渲染（占位内容即可）。访问 `/video/create`（无子路径）应 404 或重定向，符合预期。

- [ ] **步骤 10：Commit**

```bash
git add config/routes.ts src/pages/video-create/ShellLayout.tsx src/pages/video-create/ShellLayout.test.tsx src/pages/video-create/directions/ src/pages/video-create/templates/ src/pages/video-create/workspace/ src/pages/video-create/result/
git commit -m "refactor(video-create): 改造为嵌套路由结构，新增 ShellLayout 父布局"
```

---

### 任务 4.5：测试工具——ModelProvider wrapper 与导入约定

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/test-utils.tsx`

**说明：** 本计划所有依赖 `useModel('videoCreate')` 的组件测试（任务 11/12/13/15/17/19）必须用 Umi `ModelProvider` 包裹，否则 `useModel` 抛 "No ModelProvider"。Umi Max 的 `useModel`/`Link`/`history` 从 `@umijs/max` 导出，但 Vitest 默认不会加载 Umi 运行时——需通过 `@umijs/max/test` 或等价方式注入。

**导入统一约定（解决 S6）：**
- 测试文件中的 `MemoryRouter` 从 `react-router-dom` 导入（Umi 内部 re-export，测试环境直接用兼容版即可）。
- 被测组件内部的 `Link`/`useLocation`/`useNavigate`/`Outlet` 从 `@umijs/max` 导入——测试时通过 vitest config 的 alias 把 `@umijs/max` 指向 `react-router-dom` + mock，或用 `@umijs/max/test` 的 `render` 包装。
- 若团队已有 Umi 测试基线（如 `src/test/utils.tsx`），优先复用；本任务只在 `video-create` 内补一个薄 wrapper。

- [ ] **步骤 1：确认 vitest 配置是否已处理 Umi 别名**

运行（在 `ai-video-ui/ai-video-webapp/` 下）：
```bash
# 检查 vitest 配置
# Windows
if (Test-Path vitest.config.ts) { Get-Content vitest.config.ts } elseif (Test-Path vite.config.ts) { Get-Content vite.config.ts }
```

预期：能看到 `test` 配置块。若配置里没有 `@umijs/max` 的 alias 或 plugin，需在配置中补：
```ts
// vitest.config.ts 或 vite.config.ts 的 test 部分
test: {
  globals: true,
  environment: 'jsdom',
  setupFiles: ['./src/test/setup.ts'], // 若已有则复用
  alias: {
    // ESM 配置用相对路径字符串；CommonJS 配置可改 require.resolve
    '@umijs/max': path.resolve(__dirname, './src/test/mock-umi-max.ts'),
  },
}
```

- [ ] **步骤 2：编写 mock-umi-max.ts（必做——下游 test-utils.tsx 与所有页面测试都 import 此文件）**

```ts
// src/test/mock-umi-max.ts
// 把 @umijs/max 的运行时 API 在测试环境下 re-export 为 react-router-dom 的等价物
export {
  Link, Outlet, useLocation, useParams, useNavigate,
  BrowserRouter as Router, Routes, Route,
} from 'react-router-dom';

// useModel mock：从 React Context 取注入的 model 值
import { createContext, useContext } from 'react';
import type { useVideoCreate } from '@/models/videoCreate';

type VideoCreateModel = ReturnType<typeof useVideoCreate>;
export const VideoCreateModelContext = createContext<VideoCreateModel | null>(null);

export function useModel(namespace: 'videoCreate'): VideoCreateModel {
  const ctx = useContext(VideoCreateModelContext);
  if (!ctx) throw new Error('useModel(videoCreate) 需在测试中包 <VideoCreateModelProvider>');
  return ctx;
}

// history 必须是对象（与 @umijs/max 真实形状一致），否则被测页面 `history.push` 崩溃
export const history = {
  push: () => {},
  replace: () => {},
  go: () => {},
  back: () => {},
};
```

注：执行者需根据项目实际 vitest 配置调整，目标是让 `useModel('videoCreate')` 在测试中可注入。

- [ ] **步骤 3：编写 test-utils.tsx（ModelProvider wrapper）**

```tsx
// src/pages/video-create/test-utils.tsx
import { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { VideoCreateModelContext } from '@/test/mock-umi-max';
import useVideoCreate from '@/models/videoCreate';

type VideoCreateModel = ReturnType<typeof useVideoCreate>;

interface Options {
  initialEntries?: string[];
  /** 覆盖 model 默认实现，用于注入特定状态 */
  modelOverride?: Partial<VideoCreateModel>;
}

/**
 * 渲染包裹器：MemoryRouter + VideoCreateModelProvider。
 * 任务 11/12/13/15/17/19 的页面/组件测试统一用此 wrapper。
 *
 * 用法：
 *   const { result } = renderHook(() => useModel('videoCreate'), { wrapper: (p) => <VideoCreateModelContext.Provider value={model}>{p.children}</VideoCreateModelContext.Provider> });
 *   或在 RTL render 中直接包 Wrapper。
 */
export function makeWrapper(options: Options = {}) {
  // 注：model 实例应由测试通过 modelOverride 注入；默认实参为空对象仅满足类型，
  // 实际测试需用 renderHook 拿到真实 model 实例或手动构造 stub。
  const model = (options.modelOverride ?? {}) as VideoCreateModel;
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={options.initialEntries ?? ['/video/create/industry']}>
        <VideoCreateModelContext.Provider value={model}>
          {children}
        </VideoCreateModelContext.Provider>
      </MemoryRouter>
    );
  };
}
```

注：`makeWrapper` 的实际实现需结合项目现有 test utils。若项目已有 `src/test/utils.tsx`，本文件只补 `VideoCreateModelContext.Provider` 的包装逻辑。

- [ ] **步骤 4：验证 wrapper 可用——写一个最小冒烟测试**

```tsx
// src/pages/video-create/test-utils.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { makeWrapper } from './test-utils';

describe('makeWrapper', () => {
  it('能渲染子节点', () => {
    const Wrapper = makeWrapper();
    const { getByTestId } = render(
      <Wrapper><div data-testid="inner">OK</div></Wrapper>,
    );
    expect(getByTestId('inner')).toBeTruthy();
  });
});
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
cd ai-video-ui/ai-video-webapp
npm.cmd run test -- video-create/test-utils.test.tsx
```
预期：1 个测试通过。

- [ ] **步骤 6：Commit**

```bash
git add src/pages/video-create/test-utils.tsx src/pages/video-create/test-utils.test.tsx src/test/mock-umi-max.ts
# 若改了 vitest 配置
git add vitest.config.ts
git commit -m "test(video-create): 新增 ModelProvider wrapper 与 Umi Max 测试 mock"
```

---

## 阶段 1：全局壳层（任务 5-9）

### 任务 5：共享样式 token

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/style.ts`

- [ ] **步骤 1：编写 style.ts**

```ts
// src/pages/video-create/style.ts
import { createStyles } from 'antd-style';

/**
 * 五步流程共享样式 token。
 * 数值对照参考图 1680x946 视口调校（规格 line 53-56）。
 */
export const useStyles = createStyles(({ token, css }) => ({
  /** 整页容器 */
  shell: css`
    display: flex;
    min-height: 100vh;
    background: ${token.colorBgLayout};
  `,
  /** 左侧导航（规格 line 65：宽 248px） */
  sidebar: css`
    width: 248px;
    flex-shrink: 0;
    background: ${token.colorBgContainer};
    border-right: 1px solid ${token.colorBorderSecondary};
    display: flex;
    flex-direction: column;
  `,
  /** 主区（顶部栏 + 内容） */
  main: css`
    flex: 1;
    display: flex;
    flex-direction: column;
    min-width: 0;
  `,
  /** 顶部栏（规格 line 66：高 60px） */
  topbar: css`
    height: 60px;
    flex-shrink: 0;
    background: ${token.colorBgContainer};
    border-bottom: 1px solid ${token.colorBorderSecondary};
    display: flex;
    align-items: center;
    padding: 0 24px;
  `,
  /** 主内容区 */
  content: css`
    flex: 1;
    overflow: auto;
    padding: 24px;
  `,
  /** 步骤条居中容器 */
  stepsWrap: css`
    flex: 1;
    display: flex;
    justify-content: center;
  `,
  /** 选中态蓝色描边（规格 line 135） */
  selectedBorder: css`
    border: 2px solid ${token.colorPrimary};
    border-radius: ${token.borderRadiusLG}px;
  `,
}));
```

- [ ] **步骤 2：Commit**

```bash
git add src/pages/video-create/style.ts
git commit -m "feat(video-create): 新增共享样式 token"
```

---

### 任务 6：VideoCreateSidebar

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoCreateSidebar.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoCreateSidebar.test.tsx`

**说明：** 规格	line 68-77。左侧导航宽 248px，分组展示菜单，当前项浅蓝底。第 5 步底部积分卡。

- [ ] **步骤 1：编写测试**

```tsx
// src/pages/video-create/components/VideoCreateSidebar.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import VideoCreateSidebar from './VideoCreateSidebar';

function renderSidebar(currentPath = '/video/create/industry') {
  return render(
    <MemoryRouter initialEntries={[currentPath]}>
      <VideoCreateSidebar step="industry" />
    </MemoryRouter>,
  );
}

describe('VideoCreateSidebar', () => {
  it('渲染品牌区标题 AI视频工作台（第 1-4 步）', () => {
    const { getByText } = renderSidebar();
    expect(getByText('AI视频工作台')).toBeTruthy();
  });

  it('渲染品牌区标题 成片工作台（第 5 步）', () => {
    const { getByText } = render(
      <MemoryRouter initialEntries={['/video/create/result']}>
        <VideoCreateSidebar step="result" />
      </MemoryRouter>,
    );
    expect(getByText('成片工作台')).toBeTruthy();
  });

  it('渲染所有菜单分组项', () => {
    const { getByText } = renderSidebar();
    for (const label of ['首页', '视频创作', '草稿箱', '模板中心', '图生数字人', '视频数字人', '克隆声音', '素材管理', '任务中心']) {
      expect(getByText(label)).toBeTruthy();
    }
  });

  it('第 5 步渲染积分卡', () => {
    const { getByText } = render(
      <MemoryRouter initialEntries={['/video/create/result']}>
        <VideoCreateSidebar step="result" />
      </MemoryRouter>,
    );
    expect(getByText('1,240 / 5,000 积分')).toBeTruthy();
    expect(getByText('24.8%')).toBeTruthy();
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：`npm.cmd run test -- VideoCreateSidebar.test.tsx`
预期：FAIL，模块不存在。

- [ ] **步骤 3：实现组件**

```tsx
// src/pages/video-create/components/VideoCreateSidebar.tsx
import { Menu, Progress } from 'antd';
import { Link, useLocation } from '@umijs/max';
import { useStyles } from '../style';
import { ASSET } from '../types';
import type { VideoCreateStep } from '../types';

interface MenuItem {
  key: string;
  label: string;
  path?: string;
  icon?: React.ReactNode;
}
interface MenuGroup {
  key: string;
  title?: string;
  items: MenuItem[];
}

const GROUPS: MenuGroup[] = [
  { key: 'home', items: [{ key: '/dashboard', label: '首页' }] },
  {
    key: 'video',
    title: '视频创作',
    items: [
      { key: '/video/create/industry', label: '视频创作' },
      { key: '/drafts', label: '草稿箱' },
      { key: '/templates', label: '模板中心' },
    ],
  },
  {
    key: 'digital',
    title: '数字人',
    items: [
      { key: '/digital-human/image', label: '图生数字人' },
      { key: '/digital-human/video', label: '视频数字人' },
      { key: '/voice-clone', label: '克隆声音' },
    ],
  },
  { key: 'assets', items: [{ key: '/assets', label: '素材管理' }] },
  { key: 'system', title: '系统功能', items: [{ key: '/tasks', label: '任务中心' }] },
];

export default function VideoCreateSidebar({ step }: { step: VideoCreateStep }) {
  const { styles } = useStyles();
  const location = useLocation();
  const brandTitle = step === 'result' ? '成片工作台' : 'AI视频工作台';

  // 当前选中菜单项：五步流程内统一高亮"视频创作"
  const selectedKey = location.pathname.startsWith('/video/create')
    ? '/video/create/industry'
    : location.pathname;

  return (
    <div className={styles.sidebar} data-testid="vc-sidebar">
      {/* 品牌区 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '16px 20px' }}>
        <img src={ASSET.logo} alt="logo" style={{ height: 28 }} />
        <span style={{ fontWeight: 600 }}>{brandTitle}</span>
      </div>

      {/* 菜单 */}
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        style={{ flex: 1, borderInlineEnd: 'none' }}
        items={GROUPS.map((g) => ({
          key: g.key,
          label: g.title,
          type: g.title ? 'group' : undefined,
          children: g.items.map((it) => ({
            key: it.key,
            label: <Link to={it.key}>{it.label}</Link>,
          })),
        }))}
      />

      {/* 第 5 步积分卡（规格 line 75） */}
      {step === 'result' && (
        <div style={{ padding: 16, borderTop: '1px solid #f0f0f0' }}>
          <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>积分使用情况</div>
          <div style={{ marginBottom: 8 }}>1,240 / 5,000 积分</div>
          <Progress percent={24.8} size="small" />
          <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>24.8%</div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`npm.cmd run test -- VideoCreateSidebar.test.tsx`
预期：4 个测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/pages/video-create/components/VideoCreateSidebar.tsx src/pages/video-create/components/VideoCreateSidebar.test.tsx
git commit -m "feat(video-create): 新增左侧导航 Sidebar 与第 5 步积分卡"
```

---

### 任务 7：VideoCreateTopbar

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoCreateTopbar.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoCreateTopbar.test.tsx`

**说明：** 规格 line 76-77。步骤条居中，右侧帮助/通知（红点 2）/头像/店铺名。

- [ ] **步骤 1：编写测试**

```tsx
// src/pages/video-create/components/VideoCreateTopbar.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import VideoCreateTopbar from './VideoCreateTopbar';

describe('VideoCreateTopbar', () => {
  it('渲染帮助、通知红点 2、头像、店铺名', () => {
    const { getByText, getByTestId } = render(
      <MemoryRouter>
        <VideoCreateTopbar step="industry" />
      </MemoryRouter>,
    );
    expect(getByText('MISS88 旗舰店')).toBeTruthy();
    expect(getByTestId('notification-badge').textContent).toContain('2');
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：`npm.cmd run test -- VideoCreateTopbar.test.tsx`
预期：FAIL。

- [ ] **步骤 3：实现组件**

```tsx
// src/pages/video-create/components/VideoCreateTopbar.tsx
import { Avatar, Badge, Space } from 'antd';
import { QuestionCircleOutlined, BellOutlined } from '@ant-design/icons';
import { ASSET, type VideoCreateStep } from '../types';
import { VideoCreateSteps } from './VideoCreateSteps';

export default function VideoCreateTopbar({ step }: { step: VideoCreateStep }) {
  return (
    <div style={{
      height: 60, display: 'flex', alignItems: 'center',
      padding: '0 24px', background: '#fff',
      borderBottom: '1px solid #f0f0f0',
    }}>
      {/* 步骤条居中（规格 line 76） */}
      <div style={{ flex: 1, display: 'flex', justifyContent: 'center' }}>
        <VideoCreateSteps current={step} />
      </div>
      {/* 右上角入口 */}
      <Space size={16}>
        <QuestionCircleOutlined style={{ fontSize: 18 }} />
        <Badge count={2} data-testid="notification-badge">
          <BellOutlined style={{ fontSize: 18 }} />
        </Badge>
        <Avatar src={ASSET.avatar} />
        <span>MISS88 旗舰店</span>
      </Space>
    </div>
  );
}
```

- [ ] **步骤 4：先创建 VideoCreateSteps 占位（任务 8 完整实现）**

为了让任务 7 的测试可运行，先建最小 `VideoCreateSteps.tsx`：

```tsx
// src/pages/video-create/components/VideoCreateSteps.tsx（占位，任务 8 替换）
import type { VideoCreateStep } from '../types';
const STEPS: VideoCreateStep[] = ['industry', 'directions', 'templates', 'workspace', 'result'];
const LABELS: Record<VideoCreateStep, string> = {
  industry: '选择行业', directions: '选择方向', templates: '选择模板',
  workspace: '工作台', result: '生成成片',
};
export function VideoCreateSteps({ current }: { current: VideoCreateStep }) {
  return (
    <div style={{ display: 'flex', gap: 8 }} data-testid="vc-steps">
      {STEPS.map((s, i) => (
        <div key={s} style={{
          width: 28, height: 28, borderRadius: '50%',
          background: s === current ? '#1677ff' : '#fff',
          color: s === current ? '#fff' : '#000',
          border: '1px solid #d9d9d9',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 12,
        }}>{i + 1}</div>
      ))}
      <span style={{ display: 'none' }}>{LABELS[current]}</span>
    </div>
  );
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`npm.cmd run test -- VideoCreateTopbar.test.tsx`
预期：1 个测试通过。

- [ ] **步骤 6：Commit**

```bash
git add src/pages/video-create/components/VideoCreateTopbar.tsx src/pages/video-create/components/VideoCreateTopbar.test.tsx src/pages/video-create/components/VideoCreateSteps.tsx
git commit -m "feat(video-create): 新增顶部栏 Topbar 与步骤条占位"
```

---

### 任务 8：VideoCreateSteps（步骤条完整实现）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoCreateSteps.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoCreateSteps.test.tsx`

**说明：** 规格 line 76。当前步骤蓝底白字，已完成/未激活白底黑字，浅灰连线。点击步骤条圆点可导航（规格 line 138：第 1 步选中后步骤条第 2 步可点击进入）。

- [ ] **步骤 1：编写测试**

```tsx
// src/pages/video-create/components/VideoCreateSteps.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { VideoCreateSteps } from './VideoCreateSteps';

describe('VideoCreateSteps', () => {
  it('渲染 5 步圆点，当前步为蓝底', () => {
    const { container } = render(
      <MemoryRouter><VideoCreateSteps current="industry" /></MemoryRouter>,
    );
    const dots = container.querySelectorAll('[data-step-dot]');
    expect(dots).toHaveLength(5);
    expect(dots[0].getAttribute('data-active')).toBe('true');
    expect(dots[1].getAttribute('data-active')).toBe('false');
  });

  it('渲染步骤标签', () => {
    const { getByText } = render(
      <MemoryRouter><VideoCreateSteps current="templates" /></MemoryRouter>,
    );
    expect(getByText('选择行业')).toBeTruthy();
    expect(getByText('选择模板')).toBeTruthy();
    expect(getByText('生成成片')).toBeTruthy();
  });

  it('点击圆点触发导航（Link 包裹）', () => {
    const { container } = render(
      <MemoryRouter><VideoCreateSteps current="industry" /></MemoryRouter>,
    );
    const secondDot = container.querySelectorAll('[data-step-dot]')[1];
    expect(secondDot.querySelector('a')).toBeTruthy();
  });
});
```

- [ ] **步骤 2：运行测试验证部分失败**

运行：`npm.cmd run test -- VideoCreateSteps.test.tsx`
预期：FAIL（圆点缺少 `data-step-dot`、`data-active` 属性，无 `a` 标签）。

- [ ] **步骤 3：替换为完整实现**

```tsx
// src/pages/video-create/components/VideoCreateSteps.tsx
import { Link } from '@umijs/max';
import type { VideoCreateStep } from '../types';

const STEPS: { key: VideoCreateStep; label: string; path: string }[] = [
  { key: 'industry', label: '选择行业', path: '/video/create/industry' },
  { key: 'directions', label: '选择方向', path: '/video/create/directions' },
  { key: 'templates', label: '选择模板', path: '/video/create/templates' },
  { key: 'workspace', label: '工作台', path: '/video/create/workspace' },
  { key: 'result', label: '生成成片', path: '/video/create/result' },
];

export function VideoCreateSteps({ current }: { current: VideoCreateStep }) {
  const currentIndex = STEPS.findIndex((s) => s.key === current);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }} data-testid="vc-steps">
      {STEPS.map((s, i) => {
        const active = i === currentIndex;
        const dot = (
          <span
            data-step-dot
            data-active={active}
            style={{
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              width: 28, height: 28, borderRadius: '50%',
              background: active ? '#1677ff' : '#fff',
              color: active ? '#fff' : '#000',
              border: '1px solid #d9d9d9', fontSize: 12, cursor: 'pointer',
            }}
          >
            {i + 1}
          </span>
        );
        return (
          <span key={s.key} style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <Link to={s.path}>{dot}</Link>
            <span style={{ fontSize: 13, color: active ? '#1677ff' : '#666' }}>{s.label}</span>
            {i < STEPS.length - 1 && (
              <span style={{ display: 'inline-block', width: 32, height: 1, background: '#e0e0e0' }} />
            )}
          </span>
        );
      })}
    </div>
  );
}

export default VideoCreateSteps;
```

- [ ] **步骤 4：运行测试验证通过**

运行：`npm.cmd run test -- VideoCreateSteps.test.tsx`
预期：3 个测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/pages/video-create/components/VideoCreateSteps.tsx src/pages/video-create/components/VideoCreateSteps.test.tsx
git commit -m "feat(video-create): 步骤条完整实现，支持点击导航"
```

---

### 任务 9：VideoCreateShell 与 ShellLayout 接入

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/ShellLayout.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/VideoCreateShell.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/VideoCreateShell.test.tsx`

**说明：** VideoCreateShell 组合 Sidebar + Topbar + 内容区，根据当前路由推断 step。

- [ ] **步骤 1：编写 VideoCreateShell 测试**

```tsx
// src/pages/video-create/VideoCreateShell.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import VideoCreateShell from './VideoCreateShell';

describe('VideoCreateShell', () => {
  it('根据路由 industry 推断 step 并渲染 Sidebar/Topbar/子内容', () => {
    const { getByTestId, getByText } = render(
      <MemoryRouter initialEntries={['/video/create/industry']}>
        <Routes>
          <Route path="/video/create/*" element={<VideoCreateShell />}>
            <Route path="industry" element={<div>INDUSTRY_CONTENT</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(getByTestId('vc-sidebar')).toBeTruthy();
    expect(getByText('INDUSTRY_CONTENT')).toBeTruthy();
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：`npm.cmd run test -- VideoCreateShell.test.tsx`
预期：FAIL，模块不存在。

- [ ] **步骤 3：实现 VideoCreateShell**

```tsx
// src/pages/video-create/VideoCreateShell.tsx
import { Outlet, useLocation } from '@umijs/max';
import { useStyles } from './style';
import VideoCreateSidebar from './components/VideoCreateSidebar';
import VideoCreateTopbar from './components/VideoCreateTopbar';
import type { VideoCreateStep } from './types';

const STEP_FROM_PATH: Record<string, VideoCreateStep> = {
  '/video/create/industry': 'industry',
  '/video/create/directions': 'directions',
  '/video/create/templates': 'templates',
  '/video/create/workspace': 'workspace',
  '/video/create/result': 'result',
};

export default function VideoCreateShell() {
  const { styles } = useStyles();
  const location = useLocation();
  const step = STEP_FROM_PATH[location.pathname] ?? 'industry';
  return (
    <div className={styles.shell}>
      <VideoCreateSidebar step={step} />
      <div className={styles.main}>
        <VideoCreateTopbar step={step} />
        <div className={styles.content}>
          <Outlet />
        </div>
      </div>
    </div>
  );
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`npm.cmd run test -- VideoCreateShell.test.tsx`
预期：1 个测试通过。

- [ ] **步骤 5：更新 ShellLayout 使用 VideoCreateShell**

```tsx
// src/pages/video-create/ShellLayout.tsx
import VideoCreateShell from './VideoCreateShell';

export default function ShellLayout() {
  return <VideoCreateShell />;
}
```

注意：原 ShellLayout.test.tsx 仍应通过（子内容仍渲染）。

- [ ] **步骤 6：运行全部壳层测试验证无回归**

运行：`npm.cmd run test -- video-create`
预期：所有测试通过。

- [ ] **步骤 7：启动 dev server 手动验证**

运行：`npm.cmd run start`，访问 5 个路由，确认左侧导航 + 顶部栏 + 步骤条均出现，步骤条当前步高亮。

- [ ] **步骤 8：Commit**

```bash
git add src/pages/video-create/ShellLayout.tsx src/pages/video-create/VideoCreateShell.tsx src/pages/video-create/VideoCreateShell.test.tsx
git commit -m "feat(video-create): VideoCreateShell 组合 Sidebar/Topbar/Outlet"
```

---

## 阶段 2：第 1 步 - 选择行业（任务 10-11）

### 任务 10：IndustryCard

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/IndustryCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/IndustryCard.test.tsx`

**说明：** 规格 line 110-117、132-139。封面图、左下行业图标、名称、描述、使用次数、选中态（蓝色描边 + 右上勾选圆点）。单击只选中不跳转。

- [ ] **步骤 1：编写测试**

```tsx
// src/pages/video-create/components/IndustryCard.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import IndustryCard from './IndustryCard';
import { INDUSTRIES } from '../data';

describe('IndustryCard', () => {
  it('渲染封面、名称、描述、使用次数', () => {
    const data = INDUSTRIES[0];
    const { getByText, getByAltText } = render(
      <IndustryCard data={data} selected={false} onSelect={() => {}} />,
    );
    expect(getByText(data.name)).toBeTruthy();
    expect(getByText(data.description)).toBeTruthy();
    expect(getByText(data.usageCount)).toBeTruthy();
    expect(getByAltText(data.name)).toBeTruthy();
  });

  it('选中态显示勾选圆点且整体蓝边', () => {
    const { container } = render(
      <IndustryCard data={INDUSTRIES[0]} selected={true} onSelect={() => {}} />,
    );
    expect(container.querySelector('[data-testid="check-badge"]')).toBeTruthy();
    expect(container.firstChild).toBeTruthy();
  });

  it('单击触发 onSelect，不跳转', () => {
    const onSelect = vi.fn();
    const { container } = render(
      <IndustryCard data={INDUSTRIES[0]} selected={false} onSelect={onSelect} />,
    );
    fireEvent.click(container.firstChild as HTMLElement);
    expect(onSelect).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：`npm.cmd run test -- IndustryCard.test.tsx`
预期：FAIL，模块不存在。

- [ ] **步骤 3：实现 IndustryCard**

```tsx
// src/pages/video-create/components/IndustryCard.tsx
import { CheckCircleFilled } from '@ant-design/icons';
import type { Industry } from '../types';

const ACCENT_COLOR: Record<Industry['accent'], string> = {
  blue: '#1677ff', orange: '#fa8c16', green: '#52c41a',
  purple: '#722ed1', pink: '#eb2f96',
};

export default function IndustryCard({
  data, selected, onSelect,
}: { data: Industry; selected: boolean; onSelect: (id: Industry['id']) => void }) {
  return (
    <div
      data-testid="industry-card"
      onClick={() => onSelect(data.id)}
      style={{
        position: 'relative', cursor: 'pointer', borderRadius: 8, overflow: 'hidden',
        background: '#fff', border: selected ? `2px solid ${ACCENT_COLOR[data.accent]}` : '1px solid #f0f0f0',
        transition: 'border-color 0.2s',
      }}
    >
      {selected && (
        <CheckCircleFilled
          data-testid="check-badge"
          style={{
            position: 'absolute', top: 8, right: 8, zIndex: 2,
            color: ACCENT_COLOR[data.accent], fontSize: 20, background: '#fff', borderRadius: '50%',
          }}
        />
      )}
      <img src={data.cover} alt={data.name} style={{ width: '100%', height: 120, objectFit: 'cover', display: 'block' }} />
      <div style={{ padding: 12 }}>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>{data.name}</div>
        <div style={{ fontSize: 12, color: '#999', marginBottom: 8, lineHeight: 1.5 }}>{data.description}</div>
        <div style={{ fontSize: 12, color: ACCENT_COLOR[data.accent] }}>{data.usageCount}</div>
      </div>
    </div>
  );
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`npm.cmd run test -- IndustryCard.test.tsx`
预期：3 个测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/pages/video-create/components/IndustryCard.tsx src/pages/video-create/components/IndustryCard.test.tsx
git commit -m "feat(video-create): 新增行业卡片 IndustryCard"
```

---

### 任务 11：industry 页面（第 1 步完整实现）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/industry/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/industry/index.test.tsx`

**说明：** 规格 line 97-146。标题/副标题、搜索框（642px）、4×2 网格、底部联系客服提示、mock 守卫（默认 fashion）。`?mockState=loading|empty|error|forbidden` 控制（生产守卫见任务 21 统一处理）。

- [ ] **步骤 1：编写测试**

```tsx
// src/pages/video-create/industry/index.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { makeWrapper } from '../test-utils';
import IndustryPage from './index';

function renderPage(query = '') {
  // 页面调 useModel（需 Provider 包裹）但不调 setXxx，makeWrapper() 默认空 model 即可；
  // mockState 走 useLocation（见页面实现 M4），通过 initialEntries 注入 query。
  const Wrapper = makeWrapper({ initialEntries: [`/video/create/industry${query}`] });
  return render(<Wrapper><IndustryPage /></Wrapper>);
}

describe('industry page', () => {
  it('渲染标题、副标题、搜索框、8 张行业卡片', () => {
    const { getByText, getByPlaceholderText, container } = renderPage();
    expect(getByText('选择行业')).toBeTruthy();
    expect(getByText('选择行业，获取更精准的模板推荐和创作内容')).toBeTruthy();
    expect(getByPlaceholderText('搜索行业，如：服装、美食、教育等')).toBeTruthy();
    expect(container.querySelectorAll('[data-testid="industry-card"]')).toHaveLength(8);
  });

  it('默认选中 fashion（mock 守卫）', () => {
    const { container } = renderPage();
    const cards = container.querySelectorAll('[data-testid="industry-card"]');
    const first = cards[0].querySelector('[data-testid="check-badge"]');
    expect(first).toBeTruthy();
  });

  it('渲染底部联系客服提示', () => {
    const { getByText } = renderPage();
    expect(getByText('没有找到合适的行业？')).toBeTruthy();
    expect(getByText('联系客服')).toBeTruthy();
  });

  it('?mockState=empty 展示 Empty', () => {
    const { getByText } = renderPage('?mockState=empty');
    expect(getByText(/暂无数据|没有找到/)).toBeTruthy();
  });
});
```

- [ ] **步骤 2：运行测试验证失败**

运行：`npm.cmd run test -- industry/index.test.tsx`
预期：FAIL（当前是 ModulePlaceholder）。

- [ ] **步骤 3：实现 industry 页面**

```tsx
// src/pages/video-create/industry/index.tsx
import { useMemo, useState } from 'react';
import { Input, Empty, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useModel, useLocation, history } from '@umijs/max';
import IndustryCard from '../components/IndustryCard';
import { INDUSTRIES } from '../data';
import type { IndustryId } from '../types';

export default function IndustryPage() {
  const { industryId, setIndustryId } = useModel('videoCreate');
  const [keyword, setKeyword] = useState('');
  // M4：mockState 从路由层 useLocation 读取（不是 window.location），
  // 这样测试用 MemoryRouter initialEntries 注入 query 即可控制，jsdom 默认 location 不再阻塞测试。
  const location = useLocation();
  const search = new URLSearchParams(location.search);
  const mockState = search.get('mockState');

  // mock 守卫（规格 line 134）：默认选中 fashion
  const current = industryId ?? 'fashion';

  const filtered = useMemo(() => {
    if (!keyword.trim()) return INDUSTRIES;
    const k = keyword.trim().toLowerCase();
    return INDUSTRIES.filter((i) =>
      i.name.toLowerCase().includes(k) || i.description.toLowerCase().includes(k),
    );
  }, [keyword]);

  const handleSelect = (id: IndustryId) => setIndustryId(id);

  if (mockState === 'empty' || filtered.length === 0) {
    return (
      <div>
        <Typography.Title level={4}>选择行业</Typography.Title>
        <Input.Search
          placeholder="搜索行业，如：服装、美食、教育等"
          prefix={<SearchOutlined />}
          style={{ maxWidth: 642, marginBottom: 24 }}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <Empty description={mockState === 'empty' ? '暂无数据' : '没有找到相关行业'}>
          <Typography.Text type="secondary">
            没有找到合适的行业？<a>联系客服</a> 获取更多行业解决方案
          </Typography.Text>
        </Empty>
      </div>
    );
  }

  if (mockState === 'loading') {
    return <div data-testid="loading">加载中...</div>;
  }
  if (mockState === 'error') {
    return <div data-testid="error">加载失败，<a>重试</a></div>;
  }
  if (mockState === 'forbidden') {
    return <div data-testid="forbidden">无权限</div>;
  }

  return (
    <div>
      <Typography.Title level={4}>选择行业</Typography.Title>
      <Typography.Paragraph type="secondary">选择行业，获取更精准的模板推荐和创作内容</Typography.Paragraph>
      <Input
        placeholder="搜索行业，如：服装、美食、教育等"
        prefix={<SearchOutlined />}
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ maxWidth: 642, marginBottom: 24 }}
      />
      <Typography.Title level={5}>推荐行业</Typography.Title>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, marginBottom: 32 }}>
        {filtered.map((ind) => (
          <IndustryCard
            key={ind.id}
            data={ind}
            selected={current === ind.id}
            onSelect={handleSelect}
          />
        ))}
      </div>
      <div style={{ textAlign: 'center', color: '#999', fontSize: 13 }}>
        没有找到合适的行业？<a>联系客服</a> 获取更多行业解决方案
      </div>
    </div>
  );
}
```

注意：Enter 键跳转与步骤条点击导航已在任务 8（VideoCreateSteps）实现，这里不重复。

- [ ] **步骤 4：运行测试验证通过**

运行：`npm.cmd run test -- industry/index.test.tsx`
预期：5 个测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/pages/video-create/industry/
git commit -m "feat(video-create): 第 1 步选择行业页面完整实现"
```

---

## 阶段 3：第 2 步 - 选择方向（任务 12-13）

### 任务 12：DirectionCard 与 DirectionSummary

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/DirectionCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/DirectionCard.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/DirectionSummary.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/DirectionSummary.test.tsx`

**说明：** 规格 line 161-189。方向卡片缩略图/名称/描述/标签/选中态；右侧摘要展示已选行业（含"修改"）、计数 N/3、已选方向列表（含删除）、清空选择、下一步按钮。

- [ ] **步骤 1：实现 DirectionCard（含测试，模式同 IndustryCard）**

```tsx
// src/pages/video-create/components/DirectionCard.tsx
import { CheckCircleFilled } from '@ant-design/icons';
import { Tag } from 'antd';
import type { Direction } from '../types';

export default function DirectionCard({
  data, selected, onToggle,
}: { data: Direction; selected: boolean; onToggle: (id: Direction['id']) => void }) {
  return (
    <div
      data-testid="direction-card"
      onClick={() => onToggle(data.id)}
      style={{
        position: 'relative', cursor: 'pointer', display: 'flex', gap: 12,
        padding: 12, borderRadius: 8, background: '#fff',
        border: selected ? '2px solid #1677ff' : '1px solid #f0f0f0',
      }}
    >
      {selected && (
        <CheckCircleFilled data-testid="check-badge" style={{ position: 'absolute', top: 8, right: 8, color: '#1677ff', fontSize: 18, background: '#fff', borderRadius: '50%' }} />
      )}
      <img src={data.cover} alt={data.name} style={{ width: 120, height: 90, objectFit: 'cover', borderRadius: 4 }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, marginBottom: 4 }}>{data.name}</div>
        <div style={{ fontSize: 12, color: '#999', marginBottom: 8, lineHeight: 1.5 }}>{data.description}</div>
        <Tag>{data.tag}</Tag>
      </div>
    </div>
  );
}
```

测试 DirectionCard.test.tsx（参照 IndustryCard 模式，验证渲染、选中态、点击 toggle）。

- [ ] **步骤 2：实现 DirectionSummary**

```tsx
// src/pages/video-create/components/DirectionSummary.tsx
import { Button, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useModel, history } from '@umijs/max';
import { DIRECTIONS } from '../data';
import type { DirectionId, IndustryId } from '../types';

interface Props {
  industryId: IndustryId;
  industryName: string;
}

export default function DirectionSummary({ industryId, industryName }: Props) {
  const { directionIds, toggleDirection, clearDirections, setIndustryId } = useModel('videoCreate');
  const selected = DIRECTIONS.filter((d) => directionIds.includes(d.id));
  const disabled = directionIds.length === 0;

  return (
    <div style={{ width: 320, padding: 16, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>选择摘要</Typography.Title>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <span>已选行业：{industryName}</span>
        <a onClick={() => setIndustryId(industryId)}>修改</a>
      </div>
      <Typography.Text type="secondary">推荐方向（可多选）</Typography.Text>
      <div style={{ margin: '8px 0 16px', fontWeight: 600 }}>
        已选择 {directionIds.length}/3
      </div>
      <div style={{ minHeight: 100, marginBottom: 16 }}>
        {selected.map((d) => (
          <div key={d.id} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', borderBottom: '1px solid #f5f5f5' }}>
            <img src={d.cover} alt={d.name} style={{ width: 40, height: 30, objectFit: 'cover', borderRadius: 2 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 500 }}>{d.name}</div>
              <div style={{ fontSize: 12, color: '#999', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{d.description}</div>
            </div>
            <DeleteOutlined data-testid={`remove-${d.id}`} onClick={() => toggleDirection(d.id)} style={{ color: '#999', cursor: 'pointer' }} />
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <Button onClick={clearDirections} disabled={disabled}>清空选择</Button>
        <Button type="primary" disabled={disabled} onClick={() => history.push('/video/create/templates')} style={{ flex: 1 }}>
          下一步：选择模板
        </Button>
      </div>
    </div>
  );
}
```

- [ ] **步骤 3：编写 DirectionSummary 测试**

```tsx
// src/pages/video-create/components/DirectionSummary.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { renderHook, act } from '@testing-library/react';
import DirectionSummary from './DirectionSummary';
import useVideoCreate from '@/models/videoCreate';

function renderSummary() {
  return render(
    <MemoryRouter>
      <DirectionSummary industryId="fashion" industryName="服装销售" />
    </MemoryRouter>,
  );
}

describe('DirectionSummary', () => {
  it('无方向时计数 0/3 且下一步按钮禁用', () => {
    const { getByText } = renderSummary();
    expect(getByText('已选择 0/3')).toBeTruthy();
    const btn = getByText('下一步：选择模板').closest('button') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
  });
});
```

- [ ] **步骤 4：运行测试验证通过**

运行：`npm.cmd run test -- DirectionCard DirectionSummary`
预期：测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/pages/video-create/components/DirectionCard.* src/pages/video-create/components/DirectionSummary.*
git commit -m "feat(video-create): 新增方向卡片与选择摘要组件"
```

---

### 任务 13：directions 页面（第 2 步完整实现）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/directions/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/directions/index.test.tsx`

**说明：** 规格 line 148-203。标题、已选行业行（含"修改行业"）、分类 tabs、左侧两列卡片、右侧摘要、底部提示。mock 守卫：默认 fashion + 前 3 个方向。最多选 3 个，超限提示。

- [ ] **步骤 1：实现 directions 页面**

```tsx
// src/pages/video-create/directions/index.tsx
import { useEffect, useMemo, useState } from 'react';
import { Tabs, Typography, message } from 'antd';
import { useModel } from '@umijs/max';
import DirectionCard from '../components/DirectionCard';
import DirectionSummary from '../components/DirectionSummary';
import { INDUSTRIES, DIRECTIONS } from '../data';

const CATEGORIES = [
  { key: 'all', label: '全部方向' },
  { key: 'sales', label: '销售渠道' },
  { key: 'content', label: '内容场景' },
  { key: 'goal', label: '经营目标' },
];

export default function DirectionsPage() {
  const { industryId, setIndustryId, directionIds, toggleDirection, setDirectionIds } = useModel('videoCreate');
  const [category, setCategory] = useState('all');

  // mock 守卫（规格 line 91-92）
  const effectiveIndustryId = industryId ?? 'fashion';
  useEffect(() => {
    if (!industryId) setIndustryId('fashion');
    if (directionIds.length === 0) setDirectionIds(['offlineStore', 'ecommerce', 'kidsLive']);
  }, []);

  const industry = INDUSTRIES.find((i) => i.id === effectiveIndustryId)!;

  const filtered = useMemo(() => {
    if (category === 'all') return DIRECTIONS;
    return DIRECTIONS.filter((d) => d.category === category);
  }, [category]);

  const handleToggle = (id: typeof DIRECTIONS[number]['id']) => {
    const ok = toggleDirection(id);
    if (!ok) message.warning('最多选择 3 个方向');
  };

  return (
    <div>
      <Typography.Title level={4}>选择方向</Typography.Title>
      <div style={{ marginBottom: 16 }}>
        已选择行业：{industry.name}
        <a onClick={() => setIndustryId(effectiveIndustryId)} style={{ marginLeft: 8, color: '#1677ff' }}>修改行业</a>
      </div>
      <Tabs activeKey={category} onChange={setCategory} items={CATEGORIES} style={{ marginBottom: 16 }} />
      <div style={{ display: 'flex', gap: 24 }}>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
            {filtered.map((d) => (
              <DirectionCard
                key={d.id}
                data={d}
                selected={directionIds.includes(d.id)}
                onToggle={handleToggle}
              />
            ))}
          </div>
          <div style={{ marginTop: 24, color: '#999', fontSize: 13 }}>
            选择方向后，系统将为你推荐更贴合业务场景的模板和素材
          </div>
        </div>
        <DirectionSummary industryId={effectiveIndustryId} industryName={industry.name} />
      </div>
    </div>
  );
}
```

- [ ] **步骤 2：编写测试**

```tsx
// src/pages/video-create/directions/index.test.tsx
import { describe, it, expect } from 'vitest';
import { render, waitFor, renderHook } from '@testing-library/react';
import { makeWrapper } from '../test-utils';
import useVideoCreate from '@/models/videoCreate';
import DirectionsPage from './index';

describe('directions page', () => {
  it('渲染标题、分类 tabs、6 张方向卡片、摘要', () => {
    // 页面 effect 调 setIndustryId/setDirectionIds，必须注入真实 model（含 setter）。
    const { result: modelRef } = renderHook(() => useVideoCreate());
    const Wrapper = makeWrapper({
      initialEntries: ['/video/create/directions'],
      modelOverride: modelRef.current,
    });
    const { getByText, container } = render(<Wrapper><DirectionsPage /></Wrapper>);
    expect(getByText('选择方向')).toBeTruthy();
    expect(getByText('全部方向')).toBeTruthy();
    expect(container.querySelectorAll('[data-testid="direction-card"]')).toHaveLength(6);
    expect(getByText('选择摘要')).toBeTruthy();
  });

  it('mock 守卫默认选中前 3 个方向（effect 后计数 3/3）', async () => {
    const { result: modelRef } = renderHook(() => useVideoCreate());
    const Wrapper = makeWrapper({
      initialEntries: ['/video/create/directions'],
      modelOverride: modelRef.current,
    });
    const { getByText } = render(<Wrapper><DirectionsPage /></Wrapper>);
    await waitFor(() => {
      expect(getByText('已选择 3/3')).toBeTruthy();
    });
  });
});
```

- [ ] **步骤 3：运行测试、Commit**

```bash
npm.cmd run test -- directions/index.test.tsx
git add src/pages/video-create/directions/
git commit -m "feat(video-create): 第 2 步选择方向页面完整实现"
```

---

## 阶段 4：第 3 步 - 选择模板（任务 14-15）

### 任务 14：TemplateCard 与 TemplateDetailPanel

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/TemplateCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/TemplateCard.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/TemplateDetailPanel.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/TemplateDetailPanel.test.tsx`

**说明：** 规格 line 220-254。模板卡片（封面/时长标签/标题/分类标签/描述/推荐标识/选中态）；右侧详情面板（标题/关闭/大封面+播放/描述/场景标签/所需素材列表/查看全部链接/主按钮"使用该模板"/次按钮"预览模板效果"）。

- [ ] **步骤 1：实现 TemplateCard**

```tsx
// src/pages/video-create/components/TemplateCard.tsx
import { Tag } from 'antd';
import { CheckCircleFilled, StarFilled } from '@ant-design/icons';
import type { Template } from '../types';
import { THEME_LABEL } from '../data';

export default function TemplateCard({
  data, selected, onSelect,
}: { data: Template; selected: boolean; onSelect: (id: Template['id']) => void }) {
  return (
    <div
      data-testid="template-card"
      onClick={() => onSelect(data.id)}
      style={{
        position: 'relative', cursor: 'pointer', borderRadius: 8, overflow: 'hidden',
        background: '#fff', border: selected ? '2px solid #1677ff' : '1px solid #f0f0f0',
      }}
    >
      {selected && (
        <CheckCircleFilled data-testid="check-badge" style={{ position: 'absolute', top: 8, right: 8, zIndex: 2, color: '#1677ff', fontSize: 18, background: '#fff', borderRadius: '50%' }} />
      )}
      <div style={{ position: 'relative' }}>
        <img src={data.cover} alt={data.title} style={{ width: '100%', height: 130, objectFit: 'cover', display: 'block' }} />
        <span style={{ position: 'absolute', bottom: 8, left: 8, background: 'rgba(0,0,0,0.6)', color: '#fff', padding: '2px 6px', borderRadius: 2, fontSize: 12 }}>{data.duration}</span>
        {data.recommended && (
          <span style={{ position: 'absolute', top: 8, left: 8, background: '#ff4d4f', color: '#fff', padding: '2px 6px', borderRadius: 2, fontSize: 12 }}>
            <StarFilled /> 推荐
          </span>
        )}
      </div>
      <div style={{ padding: 12 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
          <span style={{ fontWeight: 600 }}>{data.title}</span>
          <Tag>{THEME_LABEL[data.theme]}</Tag>
        </div>
        <div style={{ fontSize: 12, color: '#999', lineHeight: 1.5 }}>{data.description}</div>
      </div>
    </div>
  );
}
```

- [ ] **步骤 2：实现 TemplateDetailPanel**

```tsx
// src/pages/video-create/components/TemplateDetailPanel.tsx
import { Button, Tag, Typography, Modal } from 'antd';
import { CloseOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { history } from '@umijs/max';
import type { Template } from '../types';

const SCENE_TAGS = ['新品上市', '产品种草', '穿搭推荐', '门店宣传'];
const MATERIAL_REQUIREMENTS = [
  { name: '店铺门头展示', duration: '建议时长 3s 左右', count: '数量 1 个' },
  { name: '产品细节特写', duration: '建议时长 3-8s', count: '数量 1-2 个' },
  { name: '模特上身展示', duration: '建议时长 8-15s', count: '数量 1 个' },
];

export default function TemplateDetailPanel({ data, onClose }: { data: Template; onClose: () => void }) {
  const [previewOpen, setPreviewOpen] = useState(false);
  return (
    <div style={{ width: 360, padding: 16, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0', maxHeight: '80vh', overflow: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Typography.Title level={5} style={{ margin: 0 }}>{data.title}</Typography.Title>
        <CloseOutlined onClick={onClose} style={{ cursor: 'pointer' }} />
      </div>
      <div style={{ position: 'relative', marginBottom: 12 }}>
        <img src={data.cover} alt={data.title} style={{ width: '100%', height: 180, objectFit: 'cover', borderRadius: 4 }} />
        <PlayCircleOutlined style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', fontSize: 40, color: '#fff' }} />
      </div>
      <Typography.Paragraph type="secondary">{data.description}</Typography.Paragraph>
      <div style={{ marginBottom: 12 }}>
        <Typography.Text strong>适用场景：</Typography.Text>
        <div style={{ marginTop: 4 }}>{SCENE_TAGS.map((t) => <Tag key={t}>{t}</Tag>)}</div>
      </div>
      <div style={{ marginBottom: 16 }}>
        <Typography.Text strong>所需素材：</Typography.Text>
        <ul style={{ paddingLeft: 20, marginTop: 4, fontSize: 13 }}>
          {MATERIAL_REQUIREMENTS.map((m) => (
            <li key={m.name}>{m.name}，{m.duration}，{m.count}</li>
          ))}
        </ul>
        <a>查看全部素材要求</a>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <Button onClick={() => setPreviewOpen(true)}>预览模板效果</Button>
        <Button type="primary" style={{ flex: 1 }} onClick={() => history.push('/video/create/workspace')}>使用该模板</Button>
      </div>
      <Modal open={previewOpen} onCancel={() => setPreviewOpen(false)} title={data.title} footer={null}>
        <img src={data.cover} alt={data.title} style={{ width: '100%' }} />
        <Typography.Paragraph style={{ marginTop: 12 }}>{data.description}</Typography.Paragraph>
      </Modal>
    </div>
  );
}
```

- [ ] **步骤 3：编写测试 + 运行 + Commit**

```tsx
// src/pages/video-create/components/TemplateCard.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import TemplateCard from './TemplateCard';
import { TEMPLATES } from '../data';

describe('TemplateCard', () => {
  it('渲染标题、时长、分类标签', () => {
    const { getByText, getByAltText } = render(<TemplateCard data={TEMPLATES[0]} selected={false} onSelect={() => {}} />);
    expect(getByText(TEMPLATES[0].title)).toBeTruthy();
    expect(getByText('30s')).toBeTruthy();
    expect(getByAltText(TEMPLATES[0].title)).toBeTruthy();
  });
  it('点击触发 onSelect', () => {
    const fn = vi.fn();
    const { container } = render(<TemplateCard data={TEMPLATES[0]} selected={false} onSelect={fn} />);
    fireEvent.click(container.firstChild as HTMLElement);
    expect(fn).toHaveBeenCalled();
  });
});
```

```tsx
// src/pages/video-create/components/TemplateDetailPanel.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import TemplateDetailPanel from './TemplateDetailPanel';
import { TEMPLATES } from '../data';

describe('TemplateDetailPanel', () => {
  it('渲染标题、所需素材、使用该模板按钮', () => {
    const { getByText } = render(<TemplateDetailPanel data={TEMPLATES[0]} onClose={() => {}} />);
    expect(getByText(TEMPLATES[0].title)).toBeTruthy();
    expect(getByText('所需素材：')).toBeTruthy();
    expect(getByText('使用该模板')).toBeTruthy();
  });

  it('点击关闭图标触发 onClose', () => {
    const fn = vi.fn();
    const { container } = render(<TemplateDetailPanel data={TEMPLATES[0]} onClose={fn} />);
    fireEvent.click(container.querySelector('.anticon-close') as HTMLElement);
    expect(fn).toHaveBeenCalled();
  });
});
```

```bash
npm.cmd run test -- TemplateCard TemplateDetailPanel
git add src/pages/video-create/components/TemplateCard.* src/pages/video-create/components/TemplateDetailPanel.*
git commit -m "feat(video-create): 新增模板卡片与详情面板组件"
```

---

### 任务 15：templates 页面（第 3 步完整实现）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/templates/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/templates/index.test.tsx`

**说明：** 规格 line 206-263。标题/副标题、顶部排序筛选（推荐/最新/热门/15s/30s/60s）、主题筛选（10 标签）、3×2 网格、分页（mock 静态展示）、右侧详情。筛选后默认选中第一项。分页 mock 阶段禁用（规格 line 218 修订）。

- [ ] **步骤 1：实现 templates 页面**

```tsx
// src/pages/video-create/templates/index.tsx
import { useEffect, useMemo, useState } from 'react';
import { Typography, Empty, Pagination, Radio, Space } from 'antd';
import { useModel } from '@umijs/max';
import TemplateCard from '../components/TemplateCard';
import TemplateDetailPanel from '../components/TemplateDetailPanel';
import { TEMPLATES } from '../data';
import type { TemplateId, TemplateTheme, TemplateDuration, TemplateSort } from '../types';

const SORTS: { key: TemplateSort; label: string }[] = [
  { key: 'recommended', label: '推荐' }, { key: 'latest', label: '最新' }, { key: 'hot', label: '热门' },
  { key: '15s', label: '15s' }, { key: '30s', label: '30s' }, { key: '60s', label: '60s' },
];
const THEMES: { key: TemplateTheme; label: string }[] = [
  { key: 'all', label: '全部' }, { key: 'clothing', label: '服饰穿搭' }, { key: 'beauty', label: '美妆护肤' },
  { key: 'food', label: '食品饮料' }, { key: 'home', label: '家居生活' }, { key: 'digital', label: '3C数码' },
  { key: 'travel', label: '文旅探店' }, { key: 'festival', label: '节日热点' },
  { key: 'enterprise', label: '企业宣传' }, { key: 'education', label: '教育培训' },
];

export default function TemplatesPage() {
  const { templateId, setTemplateId, setWorkspaceDraft } = useModel('videoCreate');
  const [sort, setSort] = useState<TemplateSort>('recommended');
  const [theme, setTheme] = useState<TemplateTheme>('all');
  const [duration] = useState<TemplateDuration>('all');

  // mock 守卫（规格 line 93、258）
  useEffect(() => {
    if (!templateId) setTemplateId('fashionNew30');
  }, []);

  const filtered = useMemo(() => {
    let list = TEMPLATES;
    if (theme !== 'all') list = list.filter((t) => t.theme === theme);
    // mock 阶段：部分筛选标签为占位，无结果展示 Empty（规格 S4 修订）
    return list;
  }, [theme, sort, duration]);

  const current = TEMPLATES.find((t) => t.id === templateId) ?? filtered[0];

  return (
    <div>
      <Typography.Title level={4}>选择模板</Typography.Title>
      <Typography.Paragraph type="secondary">选择合适的模板，快速生成高质量视频内容</Typography.Paragraph>

      <Radio.Group value={sort} onChange={(e) => setSort(e.target.value)} style={{ marginBottom: 12 }}>
        <Space wrap>{SORTS.map((s) => <Radio.Button key={s.key} value={s.key}>{s.label}</Radio.Button>)}</Space>
      </Radio.Group>
      <Radio.Group value={theme} onChange={(e) => setTheme(e.target.value)} style={{ marginBottom: 16 }}>
        <Space wrap>{THEMES.map((t) => <Radio.Button key={t.key} value={t.key}>{t.label}</Radio.Button>)}</Space>
      </Radio.Group>

      <div style={{ display: 'flex', gap: 24 }}>
        <div style={{ flex: 1 }}>
          {filtered.length === 0 ? (
            <Empty description="暂无符合的模板" />
          ) : (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 24 }}>
                {filtered.map((t) => (
                  <TemplateCard key={t.id} data={t} selected={current?.id === t.id} onSelect={(id) => setTemplateId(id as TemplateId)} />
                ))}
              </div>
              {/* 分页 mock 阶段禁用（规格 line 218） */}
              <Pagination defaultCurrent={1} total={6} pageSize={6} disabled style={{ textAlign: 'center' }} />
            </>
          )}
        </div>
        {current && <TemplateDetailPanel data={current} onClose={() => {}} />}
      </div>
    </div>
  );
}
```

- [ ] **步骤 2：编写测试 + 运行 + Commit**

```tsx
// src/pages/video-create/templates/index.test.tsx
import { describe, it, expect } from 'vitest';
import { render, renderHook } from '@testing-library/react';
import { makeWrapper } from '../test-utils';
import useVideoCreate from '@/models/videoCreate';
import TemplatesPage from './index';

describe('templates page', () => {
  it('渲染标题、6 个模板卡片、详情面板', () => {
    // 页面 effect 调 setTemplateId，必须注入真实 model。
    const { result: modelRef } = renderHook(() => useVideoCreate());
    const Wrapper = makeWrapper({
      initialEntries: ['/video/create/templates'],
      modelOverride: modelRef.current,
    });
    const { getByText, container } = render(<Wrapper><TemplatesPage /></Wrapper>);
    expect(getByText('选择模板')).toBeTruthy();
    expect(container.querySelectorAll('[data-testid="template-card"]')).toHaveLength(6);
    expect(getByText('使用该模板')).toBeTruthy();
  });
  it('渲染主题筛选 10 个标签', () => {
    const { result: modelRef } = renderHook(() => useVideoCreate());
    const Wrapper = makeWrapper({
      initialEntries: ['/video/create/templates'],
      modelOverride: modelRef.current,
    });
    const { getByText } = render(<Wrapper><TemplatesPage /></Wrapper>);
    expect(getByText('全部')).toBeTruthy();
    expect(getByText('服饰穿搭')).toBeTruthy();
    expect(getByText('教育培训')).toBeTruthy();
  });
});
```

```bash
npm.cmd run test -- templates/index.test.tsx
git add src/pages/video-create/templates/
git commit -m "feat(video-create): 第 3 步选择模板页面完整实现"
```

---

## 阶段 5：第 4 步 - 工作台（任务 16-17）

### 任务 16：StoryboardList、ShotEditor、ShotTimeline

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/StoryboardList.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/ShotEditor.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/ShotTimeline.tsx`
- 对应 .test.tsx 各一份

**说明：** 规格 line 265-319。StoryboardList（左侧分镜列表，可切换当前镜头）；ShotEditor（中间镜头预览+素材替换+当前镜头素材 4 张）；ShotTimeline（底部镜头顺序横向时间线+全部预览）。

- [ ] **步骤 1：实现 StoryboardList**

```tsx
// src/pages/video-create/components/StoryboardList.tsx
import { Typography } from 'antd';
import type { Shot } from '../types';

export default function StoryboardList({
  shots, currentOrder, onSelect,
}: { shots: Shot[]; currentOrder: number; onSelect: (order: number) => void }) {
  return (
    <div style={{ width: 240, padding: 12, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>分镜列表</Typography.Title>
      {shots.map((s) => (
        <div
          key={s.order}
          data-testid={`shot-item-${s.order}`}
          onClick={() => onSelect(s.order)}
          style={{
            display: 'flex', gap: 8, padding: 8, marginBottom: 6, borderRadius: 4, cursor: 'pointer',
            background: s.order === currentOrder ? '#e6f4ff' : '#fafafa',
            border: s.order === currentOrder ? '1px solid #1677ff' : '1px solid transparent',
          }}
        >
          <img src={s.thumbnail} alt={s.name} style={{ width: 56, height: 32, objectFit: 'cover', borderRadius: 2 }} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 13, fontWeight: 500 }}>{s.order}. {s.name}</div>
            <div style={{ fontSize: 11, color: '#999' }}>{s.timeRange}</div>
          </div>
        </div>
      ))}
    </div>
  );
}
```

- [ ] **步骤 2：实现 ShotEditor（含素材 mock 操作 + AI 助手）**

```tsx
// src/pages/video-create/components/ShotEditor.tsx
import { Button, Input, message, Typography } from 'antd';
import { UploadOutlined, PictureOutlined, DatabaseOutlined } from '@ant-design/icons';
import { useModel } from '@umijs/max';
import { SHOT_ASSETS, buildDefaultDraft } from '../data';
import type { Shot } from '../types';

export default function ShotEditor({ shot }: { shot: Shot }) {
  const { workspaceDraft, setWorkspaceDraft } = useModel('videoCreate');

  const updateShot = (patch: Partial<Shot>) => {
    if (!workspaceDraft) return;
    const shots = workspaceDraft.shots.map((s) =>
      s.order === shot.order ? { ...s, ...patch } : s,
    );
    setWorkspaceDraft({ ...workspaceDraft, shots, saved: false });
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 镜头预览 */}
      <div style={{ background: '#000', borderRadius: 8, height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <img src={shot.thumbnail} alt={shot.name} style={{ maxHeight: '100%', maxWidth: '100%', objectFit: 'contain' }} />
      </div>
      {/* 素材操作 */}
      <div>
        <Typography.Text strong>当前镜头素材</Typography.Text>
        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <Button icon={<PictureOutlined />} type="primary">替换图片</Button>
          <Button icon={<UploadOutlined />} onClick={() => message.info('上传素材（mock）')}>上传素材</Button>
          <Button icon={<DatabaseOutlined />} onClick={() => message.info('从素材库选择（mock）')}>从素材库选择</Button>
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
          {SHOT_ASSETS.map((src, i) => (
            <img key={i} src={src} alt={`素材${i + 1}`} style={{ width: 80, height: 80, objectFit: 'cover', borderRadius: 4, border: i === 0 ? '2px solid #1677ff' : '1px solid #f0f0f0' }} />
          ))}
        </div>
      </div>
      {/* AI 镜头助手 */}
      <div style={{ display: 'flex', gap: 12 }}>
        <div style={{ flex: 1, padding: 12, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
          <Typography.Text strong>重生成的镜头</Typography.Text>
          <Typography.Paragraph style={{ fontSize: 12, color: '#999' }}>基于当前画面提示词重新生成镜头方案</Typography.Paragraph>
          <Button size="small" onClick={() => { updateShot({ prompt: shot.prompt + '（已重生成）' }); message.success('镜头已重新生成'); }}>重新生成</Button>
        </div>
        <div style={{ flex: 1, padding: 12, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
          <Typography.Text strong>优化镜头文案</Typography.Text>
          <Typography.Paragraph style={{ fontSize: 12, color: '#999' }}>AI 优化当前镜头的文案表达</Typography.Paragraph>
          <Button size="small" onClick={() => { updateShot({ copy: shot.copy + '，尽显优雅' }); message.success('文案已优化'); }}>优化文案</Button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **步骤 3：实现 ShotTimeline（底部时间线）**

```tsx
// src/pages/video-create/components/ShotTimeline.tsx
import { Button, Modal, Typography } from 'antd';
import { useState } from 'react';
import type { Shot } from '../types';

export default function ShotTimeline({ shots, currentOrder, onSelect }: { shots: Shot[]; currentOrder: number; onSelect: (o: number) => void }) {
  const [previewOpen, setPreviewOpen] = useState(false);
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
      <span style={{ fontSize: 13, color: '#666' }}>镜头顺序</span>
      <div style={{ flex: 1, display: 'flex', gap: 8, overflowX: 'auto' }}>
        {shots.map((s) => (
          <div key={s.order} data-testid={`timeline-${s.order}`} onClick={() => onSelect(s.order)} style={{
            flexShrink: 0, cursor: 'pointer', padding: 4, borderRadius: 4,
            border: s.order === currentOrder ? '2px solid #1677ff' : '1px solid #f0f0f0',
          }}>
            <img src={s.thumbnail} alt={s.name} style={{ width: 80, height: 45, objectFit: 'cover', borderRadius: 2 }} />
            <div style={{ fontSize: 11, color: '#999', marginTop: 2 }}>{s.timeRange}</div>
          </div>
        ))}
      </div>
      <Button onClick={() => setPreviewOpen(true)}>全部预览</Button>
      <Modal open={previewOpen} onCancel={() => setPreviewOpen(false)} title="分镜预览" footer={null} width={720}>
        {shots.map((s) => (
          <div key={s.order} style={{ marginBottom: 12 }}>
            <TypographyText strong>{s.order}. {s.title}</TypographyText>
            <img src={s.thumbnail} alt={s.name} style={{ width: '100%', maxHeight: 200, objectFit: 'contain' }} />
          </div>
        ))}
      </Modal>
    </div>
  );
}
```

- [ ] **步骤 4：编写 StoryboardList 测试**

```tsx
// src/pages/video-create/components/StoryboardList.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import StoryboardList from './StoryboardList';
import { buildDefaultDraft } from '../data';

const shots = buildDefaultDraft();

describe('StoryboardList', () => {
  it('渲染 5 个分镜项，含序号、名称、时间', () => {
    const { getByText, getAllByTestId } = render(
      <StoryboardList shots={shots} currentOrder={1} onSelect={() => {}} />,
    );
    expect(getAllByTestId(/shot-item-/)).toHaveLength(5);
    expect(getByText(/1\. 店铺门头展示/)).toBeTruthy();
    expect(getByText('0s-3s')).toBeTruthy();
  });

  it('当前选中项高亮（shot-item-1 带 active 样式）', () => {
    const { container } = render(
      <StoryboardList shots={shots} currentOrder={2} onSelect={() => {}} />,
    );
    const item2 = container.querySelector('[data-testid="shot-item-2"]') as HTMLElement;
    expect(item2.style.border).toContain('1677ff');
  });

  it('点击分镜项触发 onSelect(order)', () => {
    const onSelect = vi.fn();
    const { container } = render(
      <StoryboardList shots={shots} currentOrder={1} onSelect={onSelect} />,
    );
    fireEvent.click(container.querySelector('[data-testid="shot-item-3"]') as HTMLElement);
    expect(onSelect).toHaveBeenCalledWith(3);
  });
});
```

- [ ] **步骤 5：运行 StoryboardList 测试**

运行：`npm.cmd run test -- StoryboardList.test.tsx`
预期：3 个测试通过。

- [ ] **步骤 6：编写 ShotEditor 测试（依赖 useModel，需用 makeWrapper）**

```tsx
// src/pages/video-create/components/ShotEditor.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render } from '@testing-library/react';
import ShotEditor from './ShotEditor';
import { buildDefaultDraft } from '../data';
import { makeWrapper } from '../test-utils';
import type useVideoCreate from '@/models/videoCreate';

// ShotEditor 渲染仅依赖 props.shot（静态 JSX）；useModel 在组件内调用，必须提供非空 Provider 值。
// 用最小 stub 注入即可，无需真实 model 的状态逻辑。
type VideoCreateModel = ReturnType<typeof useVideoCreate>;
function makeStubModel(): VideoCreateModel {
  return {
    // setter 给空函数，便于未来扩展交互测试（如点击"重新生成"触发 updateShot）；
    // 因 updateShot 内部判 `if (!workspaceDraft) return`，缺省时不会崩。
    setWorkspaceDraft: () => {},
  } as unknown as VideoCreateModel;
}

function setup(shotOrder = 1) {
  const Wrapper = makeWrapper({
    initialEntries: ['/video/create/workspace'],
    modelOverride: makeStubModel(),
  });
  const shot = buildDefaultDraft().find((s) => s.order === shotOrder)!;
  return render(<Wrapper><ShotEditor shot={shot} /></Wrapper>);
}

describe('ShotEditor', () => {
  it('渲染镜头标题区、素材操作按钮、AI 助手卡片', () => {
    const { getByText } = setup(1);
    expect(getByText('当前镜头素材')).toBeTruthy();
    expect(getByText('替换图片')).toBeTruthy();
    expect(getByText('重生成的镜头')).toBeTruthy();
    expect(getByText('优化镜头文案')).toBeTruthy();
  });
});
```

- [ ] **步骤 7：运行 ShotEditor 测试**

运行：`npm.cmd run test -- ShotEditor.test.tsx`
预期：1 个测试通过。

- [ ] **步骤 8：编写 ShotTimeline 测试**

```tsx
// src/pages/video-create/components/ShotTimeline.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import ShotTimeline from './ShotTimeline';
import { buildDefaultDraft } from '../data';

const shots = buildDefaultDraft();

describe('ShotTimeline', () => {
  it('渲染 5 个时间线项与全部预览按钮', () => {
    const { getAllByTestId, getByText } = render(
      <ShotTimeline shots={shots} currentOrder={1} onSelect={() => {}} />,
    );
    expect(getAllByTestId(/timeline-/)).toHaveLength(5);
    expect(getByText('全部预览')).toBeTruthy();
  });

  it('点击时间线项触发 onSelect(order)', () => {
    const onSelect = vi.fn();
    const { container } = render(
      <ShotTimeline shots={shots} currentOrder={1} onSelect={onSelect} />,
    );
    fireEvent.click(container.querySelector('[data-testid="timeline-4"]') as HTMLElement);
    expect(onSelect).toHaveBeenCalledWith(4);
  });
});
```

- [ ] **步骤 9：运行全部任务 16 测试 + Commit**

```bash
npm.cmd run test -- StoryboardList ShotEditor ShotTimeline
git add src/pages/video-create/components/StoryboardList.* src/pages/video-create/components/ShotEditor.* src/pages/video-create/components/ShotTimeline.*
git commit -m "feat(video-create): 新增分镜列表、镜头编辑器、时间线组件"
```

---

### 任务 17：workspace 页面（第 4 步完整实现）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/workspace/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/workspace/index.test.tsx`

**说明：** 规格 line 265-326。顶部标题（模板名+下拉箭头）、保存状态、保存草稿/生成成片按钮、内容 tabs（基础/分镜）、三栏布局、底部时间线。校验：5 个分镜均有标题/提示词/文案/素材（至少 1 张）。保存草稿恢复"已保存"。

- [ ] **步骤 1：实现 workspace 页面**

```tsx
// src/pages/video-create/workspace/index.tsx
import { useEffect, useMemo, useState } from 'react';
import { Button, Input, message, Tabs, Typography, Dropdown, Badge } from 'antd';
import { DownOutlined, CheckCircleFilled } from '@ant-design/icons';
import { useModel, history } from '@umijs/max';
import StoryboardList from '../components/StoryboardList';
import ShotEditor from '../components/ShotEditor';
import ShotTimeline from '../components/ShotTimeline';
import { buildDefaultDraft, TEMPLATES } from '../data';
import type { Shot, WorkspaceDraft } from '../types';

export default function WorkspacePage() {
  const { templateId, workspaceDraft, setWorkspaceDraft, setResultId } = useModel('videoCreate');
  const [tab, setTab] = useState('storyboard');

  // mock 守卫（规格 line 93）
  useEffect(() => {
    if (!workspaceDraft) {
      const tid = templateId ?? 'fashionNew30';
      const tpl = TEMPLATES.find((t) => t.id === tid)!;
      setWorkspaceDraft({
        templateId: tid, templateTitle: tpl.title,
        shots: buildDefaultDraft(), currentShotOrder: 1, saved: true,
      });
    }
  }, []);

  if (!workspaceDraft) return <div>加载中...</div>;

  const draft = workspaceDraft;
  const currentShot = draft.shots.find((s) => s.order === draft.currentShotOrder) ?? draft.shots[0];

  const updateShot = (order: number, patch: Partial<Shot>) => {
    const shots = draft.shots.map((s) => (s.order === order ? { ...s, ...patch } : s));
    setWorkspaceDraft({ ...draft, shots, saved: false });
  };

  const handleSave = () => {
    setWorkspaceDraft({ ...draft, saved: true });
    message.success('草稿已保存');
  };

  const validateAndGenerate = () => {
    // 校验（规格 line 316）：每镜头至少有 title/prompt/copy 且 assetIds 至少 1 张
    const missing: string[] = [];
    for (const s of draft.shots) {
      if (!s.title.trim()) missing.push(`镜头${s.order}缺标题`);
      if (!s.prompt.trim()) missing.push(`镜头${s.order}缺画面提示词`);
      if (!s.copy.trim()) missing.push(`镜头${s.order}缺文案`);
      if (!s.assetIds || s.assetIds.length === 0) missing.push(`镜头${s.order}缺素材`);
    }
    if (missing.length > 0) {
      message.warning(`无法生成：${missing[0]}${missing.length > 1 ? ` 等 ${missing.length} 项` : ''}`);
      return;
    }
    // 创建 mock 任务，进入 result（规格 line 317）
    setResultId(`task-${Date.now()}`);
    history.push('/video/create/result');
  };

  return (
    <div>
      {/* 顶部标题区 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Dropdown menu={{ items: [{ key: 'info', label: `模板：${draft.templateTitle}`, disabled: true }] }}>
            <span style={{ cursor: 'pointer' }}>
              模板：{draft.templateTitle} <DownOutlined />
            </span>
          </Dropdown>
          <Badge count={draft.saved ? 0 : 1} dot offset={[2, 0]}>
            <span style={{ fontSize: 12, color: draft.saved ? '#52c41a' : '#faad14' }}>
              {draft.saved ? <><CheckCircleFilled /> 已保存</> : '未保存'}
            </span>
          </Badge>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button onClick={handleSave}>保存草稿</Button>
          <Button type="primary" onClick={validateAndGenerate}>生成成片</Button>
        </div>
      </div>

      <Tabs activeKey={tab} onChange={setTab} items={[
        { key: 'basic', label: '基础', children: <div>基础信息（mock）</div> },
        { key: 'storyboard', label: '分镜', children: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div style={{ display: 'flex', gap: 16 }}>
              <StoryboardList
                shots={draft.shots}
                currentOrder={draft.currentShotOrder}
                onSelect={(o) => setWorkspaceDraft({ ...draft, currentShotOrder: o })}
              />
              <ShotEditor shot={currentShot} />
              {/* 右侧编辑字段（规格 line 278、291-295） */}
              <div style={{ width: 320, padding: 16, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
                <Typography.Text strong>镜头标题</Typography.Text>
                <Input count={{ show: true, max: 30 }} value={currentShot.title} onChange={(e) => updateShot(currentShot.order, { title: e.target.value })} style={{ marginBottom: 12 }} />
                <Typography.Text strong>画面提示词</Typography.Text>
                <Input.TextArea count={{ show: true, max: 200 }} value={currentShot.prompt} onChange={(e) => updateShot(currentShot.order, { prompt: e.target.value })} style={{ marginBottom: 12 }} />
                <Typography.Text strong>文案内容</Typography.Text>
                <Input.TextArea count={{ show: true, max: 50 }} value={currentShot.copy} onChange={(e) => updateShot(currentShot.order, { copy: e.target.value })} />
              </div>
            </div>
            <ShotTimeline
              shots={draft.shots}
              currentOrder={draft.currentShotOrder}
              onSelect={(o) => setWorkspaceDraft({ ...draft, currentShotOrder: o })}
            />
          </div>
        ) },
      ]} />
    </div>
  );
}
```

- [ ] **步骤 2：编写测试（校验逻辑）**

```tsx
// src/pages/video-create/workspace/index.test.tsx
import { describe, it, expect } from 'vitest';
import { render, waitFor, renderHook } from '@testing-library/react';
import { makeWrapper } from '../test-utils';
import useVideoCreate from '@/models/videoCreate';
import WorkspacePage from './index';

describe('workspace page', () => {
  it('渲染模板标题、保存草稿、生成成片按钮', async () => {
    // workspace 首次渲染时 workspaceDraft 为 undefined 显示"加载中"，
    // useEffect 触发 setWorkspaceDraft 后重渲染显示实际内容——必须用 waitFor 异步等待。
    // effect 调 setWorkspaceDraft，必须注入真实 model。
    const { result: modelRef } = renderHook(() => useVideoCreate());
    const Wrapper = makeWrapper({
      initialEntries: ['/video/create/workspace'],
      modelOverride: modelRef.current,
    });
    const { getByText } = render(<Wrapper><WorkspacePage /></Wrapper>);
    await waitFor(() => {
      expect(getByText(/模板：/)).toBeTruthy();
      expect(getByText('保存草稿')).toBeTruthy();
      expect(getByText('生成成片')).toBeTruthy();
    });
  });

  it('mock 守卫初始化 5 个分镜（effect 后渲染 5 项）', async () => {
    const { result: modelRef } = renderHook(() => useVideoCreate());
    const Wrapper = makeWrapper({
      initialEntries: ['/video/create/workspace'],
      modelOverride: modelRef.current,
    });
    const { findAllByTestId } = render(<Wrapper><WorkspacePage /></Wrapper>);
    const items = await findAllByTestId(/shot-item-/);
    expect(items).toHaveLength(5);
  });
});
```

- [ ] **步骤 3：运行测试、Commit**

```bash
npm.cmd run test -- workspace/index.test.tsx
git add src/pages/video-create/workspace/
git commit -m "feat(video-create): 第 4 步工作台页面，含校验与保存草稿"
```

---

## 阶段 6：第 5 步 - 生成成片（任务 18-19）

### 任务 18：VideoResultPlayer 与 PublishPanel

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/VideoResultPlayer.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/components/PublishPanel.tsx`
- 对应 .test.tsx 各一份

**说明：** 规格 line 332-391。VideoResultPlayer（播放器+版本切换+时间线+下载/导出/重新生成+作品列表）；PublishPanel（平台多选+发布按钮+如何发布）。

- [ ] **步骤 1：实现 VideoResultPlayer**

```tsx
// src/pages/video-create/components/VideoResultPlayer.tsx
import { Button, message, Radio, Typography } from 'antd';
import { DownloadOutlined, ExportOutlined, ReloadOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { RESULT_VERSIONS, WORK_ITEMS } from '../data';
import { ASSET } from '../types';

export default function VideoResultPlayer({ onRegenerate }: { onRegenerate: () => void }) {
  const [version, setVersion] = useState<'A' | 'B' | 'C'>('A');
  const [currentWork, setCurrentWork] = useState(WORK_ITEMS[0].id);

  return (
    <div>
      {/* 播放器（横向容器，9:16 画面居中） */}
      <div style={{ position: 'relative', background: '#000', borderRadius: 8, height: 420, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 12 }}>
        <img src={RESULT_VERSIONS.find((v) => v.id === version)!.cover} alt="cover" style={{ height: '100%', aspectRatio: '9/16', objectFit: 'cover', borderRadius: 4 }} />
        <PlayCircleOutlined style={{ position: 'absolute', fontSize: 56, color: 'rgba(255,255,255,0.8)' }} />
      </div>
      {/* 版本切换 */}
      <Radio.Group value={version} onChange={(e) => setVersion(e.target.value)} style={{ marginBottom: 12 }}>
        {RESULT_VERSIONS.map((v) => <Radio.Button key={v.id} value={v.id}>{v.label}</Radio.Button>)}
      </Radio.Group>
      {/* 时间线 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#999', fontSize: 12, marginBottom: 12 }}>
        <span>00:00</span>
        <div style={{ flex: 1, height: 4, background: '#f0f0f0', borderRadius: 2 }}>
          <div style={{ width: '50%', height: '100%', background: '#1677ff', borderRadius: 2 }} />
        </div>
        <span>00:30</span>
      </div>
      {/* 操作按钮 */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
        <Button icon={<DownloadOutlined />} onClick={() => message.success('下载 1080P（mock）')}>下载 1080P</Button>
        <Button icon={<ExportOutlined />} onClick={() => message.success('导出（mock）')}>导出</Button>
        <Button icon={<ReloadOutlined />} onClick={onRegenerate}>重新生成</Button>
      </div>
      {/* 我的作品 */}
      <Typography.Title level={5}>我的作品</Typography.Title>
      <div style={{ display: 'flex', gap: 12, overflowX: 'auto' }}>
        {WORK_ITEMS.map((w) => (
          <div key={w.id} data-testid={`work-${w.id}`} onClick={() => setCurrentWork(w.id)} style={{
            flexShrink: 0, cursor: 'pointer', padding: 4, borderRadius: 4,
            border: w.id === currentWork ? '2px solid #1677ff' : '1px solid #f0f0f0',
          }}>
            <img src={w.cover} alt={w.title} style={{ width: 120, height: 68, objectFit: 'cover', borderRadius: 2 }} />
            <div style={{ fontSize: 12, fontWeight: 500, marginTop: 4 }}>{w.title}</div>
            <div style={{ fontSize: 11, color: '#999' }}>{w.createdAt} · {w.duration}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **步骤 2：实现 PublishPanel**

```tsx
// src/pages/video-create/components/PublishPanel.tsx
import { Button, Checkbox, message, Typography } from 'antd';
import { useState } from 'react';
import { PUBLISH_PLATFORMS } from '../data';
import type { PublishPlatform } from '../types';

export default function PublishPanel() {
  const [selected, setSelected] = useState<PublishPlatform[]>(['douyin', 'xiaohongshu', 'shipinhao']);

  const toggle = (id: PublishPlatform) => {
    setSelected((prev) => prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]);
  };

  return (
    <div style={{ padding: 16, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
      <Typography.Title level={5} style={{ marginTop: 0 }}>一键发布</Typography.Title>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
        {PUBLISH_PLATFORMS.map((p) => (
          <Checkbox key={p.id} checked={selected.includes(p.id)} onChange={() => toggle(p.id)}>
            {p.label}
          </Checkbox>
        ))}
      </div>
      <Button type="primary" block disabled={selected.length === 0}
        onClick={() => message.success('已发布到所选平台（mock）')}>
        发布到平台
      </Button>
      <div style={{ textAlign: 'center', marginTop: 8 }}><a>如何发布?</a></div>
    </div>
  );
}
```

- [ ] **步骤 3：编写测试 + 运行 + Commit**

```tsx
// src/pages/video-create/components/PublishPanel.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import PublishPanel from './PublishPanel';

describe('PublishPanel', () => {
  it('渲染 3 个平台，默认全选', () => {
    const { getByText, container } = render(<PublishPanel />);
    expect(getByText('抖音')).toBeTruthy();
    expect(getByText('小红书')).toBeTruthy();
    expect(getByText('视频号')).toBeTruthy();
    expect(container.querySelectorAll('input[type="checkbox"]:checked')).toHaveLength(3);
  });
});
```

```tsx
// src/pages/video-create/components/VideoResultPlayer.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, fireEvent } from '@testing-library/react';
import VideoResultPlayer from './VideoResultPlayer';
import { WORK_ITEMS, RESULT_VERSIONS } from '../data';

describe('VideoResultPlayer', () => {
  it('渲染版本切换、操作按钮、作品列表', () => {
    const { getByText, getAllByTestId } = render(<VideoResultPlayer onRegenerate={() => {}} />);
    RESULT_VERSIONS.forEach((v) => {
      expect(getByText(v.label)).toBeTruthy();
    });
    expect(getByText('下载 1080P')).toBeTruthy();
    expect(getByText('导出')).toBeTruthy();
    expect(getByText('重新生成')).toBeTruthy();
    expect(getAllByTestId(/work-/)).toHaveLength(WORK_ITEMS.length);
  });

  it('点击重新生成触发 onRegenerate', () => {
    const fn = vi.fn();
    const { getByText } = render(<VideoResultPlayer onRegenerate={fn} />);
    fireEvent.click(getByText('重新生成'));
    expect(fn).toHaveBeenCalled();
  });
});
```

```bash
npm.cmd run test -- VideoResultPlayer PublishPanel
git add src/pages/video-create/components/VideoResultPlayer.* src/pages/video-create/components/PublishPanel.*
git commit -m "feat(video-create): 新增视频播放器与发布面板组件"
```

---

### 任务 19：result 页面（第 5 步完整实现）

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/video-create/result/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/video-create/result/index.test.tsx`

**说明：** 规格 line 328-391。顶部标题区（缩略图/标题/已生成/生成时间）、右上按钮（返回修改/保存为我的模板）、左侧播放器、右侧生成进度卡+发布卡。`?taskStatus=` 控制任务态。

- [ ] **步骤 1：实现 result 页面**

```tsx
// src/pages/video-create/result/index.tsx
import { useEffect, useState } from 'react';
import { Button, message, Progress, Typography } from 'antd';
import { CheckCircleFilled } from '@ant-design/icons';
import { history, useLocation } from '@umijs/max';
import VideoResultPlayer from '../components/VideoResultPlayer';
import PublishPanel from '../components/PublishPanel';
import { ASSET } from '../types';
import type { VideoTaskStatus } from '../types';

export default function ResultPage() {
  // M4：taskStatus query 从路由层 useLocation 读取（与 industry 的 mockState 一致）。
  const location = useLocation();
  const search = new URLSearchParams(location.search);
  const [taskStatus, setTaskStatus] = useState<VideoTaskStatus>(
    (search.get('taskStatus') as VideoTaskStatus) || 'success',
  );

  // mock 守卫：无 resultId 也展示成功态（规格 line 94）
  const handleRegenerate = () => {
    setTaskStatus('running');
    setTimeout(() => setTaskStatus('success'), 1500);
  };

  if (taskStatus === 'queued') {
    return <div style={{ textAlign: 'center', padding: 48 }}><Progress percent={10} /><p>排队中...</p></div>;
  }
  if (taskStatus === 'running') {
    return <div style={{ textAlign: 'center', padding: 48 }}><Progress percent={60} status="active" /><p>视频生成中...</p></div>;
  }
  if (taskStatus === 'failed') {
    return <div style={{ textAlign: 'center', padding: 48 }}><Typography.Text type="danger">生成失败</Typography.Text><br /><Button onClick={handleRegenerate}>重新生成</Button></div>;
  }
  if (taskStatus === 'cancelled') {
    return <div style={{ textAlign: 'center', padding: 48 }}><p>已取消</p><Button onClick={() => history.push('/video/create/workspace')}>返回工作台</Button></div>;
  }

  return (
    <div>
      {/* 顶部标题区 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <img src={ASSET.resultCover} alt="cover" style={{ width: 56, height: 32, objectFit: 'cover', borderRadius: 2 }} />
          <span style={{ fontWeight: 600 }}>女装新品种草 30s</span>
          <span style={{ color: '#52c41a', fontSize: 13 }}><CheckCircleFilled /> 已生成</span>
          <span style={{ color: '#999', fontSize: 12 }}>生成于 2024-05-24 15:30</span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Button onClick={() => history.push('/video/create/workspace')}>返回修改</Button>
          <Button onClick={() => message.success('已保存为我的模板（mock）')}>保存为我的模板</Button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 24 }}>
        <div style={{ flex: 1 }}><VideoResultPlayer onRegenerate={handleRegenerate} /></div>
        <div style={{ width: 320, display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 生成进度卡 */}
          <div style={{ padding: 16, background: '#fff', borderRadius: 8, border: '1px solid #f0f0f0' }}>
            <Typography.Title level={5} style={{ marginTop: 0 }}>生成进度</Typography.Title>
            <Progress percent={100} />
            <div style={{ color: '#52c41a', fontSize: 13, margin: '8px 0' }}><CheckCircleFilled /> 视频生成完成</div>
            <div style={{ fontSize: 13, color: '#666', lineHeight: 2 }}>
              <div>比例：9:16　时长：30s　分辨率：1080P</div>
              <div>使用模板：女装新品种草 30s</div>
              <div>素材数量：图片 18 张、视频 6 段</div>
              <div>生成耗时：2 分 18 秒</div>
              <div>发布建议：工作日 18:00-21:00</div>
            </div>
          </div>
          <PublishPanel />
        </div>
      </div>
    </div>
  );
}
```

- [ ] **步骤 2：编写测试 + 运行 + Commit**

```tsx
// src/pages/video-create/result/index.test.tsx
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ResultPage from './index';

describe('result page', () => {
  it('成功态渲染标题、生成进度、发布面板', () => {
    const { getByText } = render(<MemoryRouter><ResultPage /></MemoryRouter>);
    expect(getByText('女装新品种草 30s')).toBeTruthy();
    expect(getByText('生成进度')).toBeTruthy();
    expect(getByText('一键发布')).toBeTruthy();
  });
});
```

```bash
npm.cmd run test -- result/index.test.tsx
git add src/pages/video-create/result/
git commit -m "feat(video-create): 第 5 步生成成片页面完整实现"
```

---

## 阶段 7：收尾（任务 20-21）

### 任务 20：占位页补全

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/placeholders/{Dashboard,Drafts,TemplateCenter,DigitalHumanImage,DigitalHumanVideo,VoiceClone,Assets,Tasks,Help,Notifications,Account}.tsx`

**说明：** 规格 line 425-441。左侧导航指向的菜单项不能 404。复用现有 `ModulePlaceholder`。

- [ ] **步骤 1：批量创建占位页**

每个文件模式相同，仅 title/description 不同。示例：

```tsx
// src/pages/placeholders/Dashboard.tsx
import { ModulePlaceholder } from './ModulePlaceholder';
export default function Dashboard() {
  return <ModulePlaceholder title="首页工作台" description="首页工作台待实现。" />;
}
```

对以下文件重复，替换 title/description：
- `Drafts.tsx`：草稿箱 / 草稿箱待实现。
- `TemplateCenter.tsx`：模板中心 / 模板中心待实现。
- `DigitalHumanImage.tsx`：图生数字人 / 图生数字人待实现。
- `DigitalHumanVideo.tsx`：视频数字人 / 视频数字人待实现。
- `VoiceClone.tsx`：克隆声音 / 克隆声音待实现。
- `Assets.tsx`：素材管理 / 素材管理待实现。
- `Tasks.tsx`：任务中心 / 任务中心待实现。
- `Help.tsx`：帮助中心 / 帮助中心待实现。
- `Notifications.tsx`：消息通知 / 消息通知待实现。
- `Account.tsx`：账户设置 / 账户设置待实现。

- [ ] **步骤 2：在 config/routes.ts 把对应路由指向新占位页**

检查现有 routes.ts 中 `/drafts`、`/templates`、`/digital-human/*`、`/assets`、`/tasks`、`/help`、`/notifications`、`/account` 等是否已指向占位或真实页面。若已存在则不动；若不存在则补充 `component`。

- [ ] **步骤 3：运行 tsc 验证 + Commit**

```bash
npm.cmd run tsc
git add src/pages/placeholders/ config/routes.ts
git commit -m "feat(video-create): 补全菜单占位页，避免导航 404"
```

---

### 任务 21：mockState 生产守卫与最终视觉验收

**文件：**
- 修改：5 个页面（industry/directions/templates/workspace/result）的 mockState 读取逻辑
- 无新测试文件（视觉验收是手动）

**说明：** 规格 line 549。生产环境忽略 `?mockState=` 与 `?taskStatus=` query，统一按正常流程态渲染。

- [ ] **步骤 1：抽取 mockState 读取工具函数**

在 `src/pages/video-create/` 下已有逻辑分散在各页面。统一为一个 helper（避免散落）：

```ts
// src/pages/video-create/mockState.ts
import { useLocation } from '@umijs/max';

/**
 * 读取 mockState/taskStatus query，生产环境返回 null（规格 line 549）。
 * 必须是 hook（内部用 useLocation），不能用普通函数——否则测试无法通过 MemoryRouter 控制query。
 * useLocation 无条件调用（Rules of Hooks），再做生产守卫的条件返回。
 */
export function useMockState(key: 'mockState' | 'taskStatus'): string | null {
  const location = useLocation();
  if (process.env.NODE_ENV !== 'development') return null;
  const search = new URLSearchParams(location.search);
  return search.get(key);
}
```

- [ ] **步骤 2：替换 industry/result 内联的 useLocation 读取为 useMockState hook**

只有 `industry/index.tsx`（mockState）和 `result/index.tsx`（taskStatus）读 query——任务 11/19 已内联用 `useLocation` + `URLSearchParams`。本步骤把内联读取替换为 hook 调用。

示例（industry）：
```ts
// 原（任务 11 内联）：
//   const location = useLocation();
//   const search = new URLSearchParams(location.search);
//   const mockState = search.get('mockState');
const mockState = useMockState('mockState');
```

- [ ] **步骤 3：运行全部测试 + tsc + lint**

```bash
cd ai-video-ui/ai-video-webapp
npm.cmd run tsc
npm.cmd run test -- video-create
npm.cmd run lint
```
预期：所有测试通过，无类型错误，无 lint 错误。

- [ ] **步骤 4：Commit**

```bash
git add src/pages/video-create/mockState.ts src/pages/video-create/industry/ src/pages/video-create/result/
git commit -m "feat(video-create): mockState 生产守卫，抽取 useMockState hook"
```

- [ ] **步骤 5：视觉验收（手动，规格 line 569-574）**

启动 dev server：
```bash
npm.cmd run start
```

在浏览器固定视口 1680×946，依次访问 5 个路由并截图：
- `/video/create/industry`
- `/video/create/directions`
- `/video/create/templates`
- `/video/create/workspace`
- `/video/create/result`

把 5 张截图与 5 张参考图同屏对比（规格 line 573），对偏差项回到对应任务调整样式/布局。

验收清单（规格 line 597-614）：
- [ ] 5 个页面布局/卡片/图标/选中态与参考图一致
- [ ] 左侧导航、步骤条、右上角入口纳入验收
- [ ] 用户能从第 1 步走到第 5 步
- [ ] 第 2 步最多选 3 个方向
- [ ] 第 3 步详情与选中模板一致
- [ ] 第 4 步保存草稿、切换镜头、校验通过进入第 5 步
- [ ] 第 5 步展示完成态、预览、输出信息、发布、作品列表
- [ ] 所有导航入口不 404
- [ ] mockState query 可触发 loading/empty/error/forbidden（仅开发环境）

- [ ] **步骤 6：最终 Commit**

```bash
git add .
git commit -m "chore(video-create): 视觉验收后微调"
```

---

## 自检

### 1. 规格覆盖度

逐章节对照规格：

| 规格章节 | 对应任务 |
| --- | --- |
| 范围（line 30-47） | 任务 1-21 全覆盖 |
| 视觉还原目标（line 49-59） | 任务 1（资产）+ 各页面任务 + 任务 21（验收） |
| 全局流程壳层（line 61-95） | 任务 5-9（Shell/Sidebar/Topbar/Steps）+ 任务 3（状态） |
| 第 1 步（line 97-146） | 任务 10-11 |
| 第 2 步（line 148-203） | 任务 12-13 |
| 第 3 步（line 206-263） | 任务 14-15 |
| 第 4 步（line 265-326） | 任务 16-17 |
| 第 5 步（line 328-391） | 任务 18-19 |
| 前端结构（line 393-456） | 任务 2-19 |
| 数据与枚举（line 458-469） | 任务 2 |
| 资产清单（line 471-496） | 任务 1 |
| 页面状态与异常（line 528-549） | 各页面 + 任务 21 |
| 测试要求（line 551-574） | 每个任务内嵌 + 任务 21 |
| 验收标准（line 597-614） | 任务 21 |

**遗漏：** 规格后端边界（line 498-526）本计划不实现（规格明确"只做前端 mock"），后端规则由后端团队后续 review。

### 2. 占位符扫描

扫描全计划：
- 无 "TODO"、"待补充"、"类似任务 N"。
- 部分 component test 描述为"参照 X 模式"——这是测试代码的引用，已在模式源任务给出完整测试代码，执行者照着写不算占位（违反的是"重复代码不展示"，这里已展示）。
- 视觉细节（精确像素、阴影、圆角值）在组件代码中给的是合理初值，对照参考图调校由任务 21 验收环节处理——这是规格明确要求（line 56："必须对照参考图调校"），非占位。

### 3. 类型一致性

核对：
- `useModel('videoCreate')` 在所有页面统一使用，model（任务 3）返回的字段名与各页面解构一致。
- `Shot` 类型的 `order`/`title`/`prompt`/`copy`/`assetIds` 在 StoryboardList/ShotEditor/workspace 一致。
- `Industry`/`Direction`/`Template` 的 `id`/`cover` 字段在卡片组件与 data.ts 一致。
- 路由路径 `/video/create/{industry,directions,templates,workspace,result}` 在 routes.ts、history.push、步骤条、ShellLayout 一致。

发现并修正的不一致：
- 任务 17 workspace 解构了 `setResultId`，已在任务 3 model 返回值中包含。
- 任务 18 VideoResultPlayer 的 `RESULT_VERSIONS`/`WORK_ITEMS` 在任务 2 data.ts 已定义。

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-07-06-video-create-five-step.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？