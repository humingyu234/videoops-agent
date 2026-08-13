# 数字人工作台声音菜单一比一复刻实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不新增后端、真实音频或公共契约的前提下，把数字人工作台内部“声音”菜单完整替换为 `D:\AI\digital-human-studio.html` 中可到达的声音列表视觉与交互。

**架构：** `LibraryView` 只负责按路由委托，声音菜单进入独立 `VoiceLibraryView`；单卡展示和交互由 `VoiceCard` 承担；唯一播放状态由 `useVoicePlayback` 通过 `requestAnimationFrame` 管理；字符时间轴、分句、格式化和刻度全部放入无浏览器副作用的纯函数。模拟数据只在页面内复制和编辑，现有认证门禁、上传 Modal、其他资产页和公共 API 保持不变。

**技术栈：** React 19、TypeScript 7、Ant Design 6、Ant Design Icons、Umi Max 4、CSS、Vitest 4、Testing Library、happy-dom、npm。

---

## 输入、事实源与不可越界项

- 已批准规格：`docs/superpowers/specs/2026-08-03-digital-human-studio-voice-library-design.md`
- 唯一参考页面：`D:\AI\digital-human-studio.html`
- 参考页面 SHA256：`0C688AB960764FE25638B55FC5B31C5E2BAFE84C3141FD236922739A0EF29694`
- 用户选择：独立声音组件方案；要求可观察视觉与交互完全一比一。
- 只改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio` 下的声音页面局部代码和测试。
- 不修改后端、数据库、真实文件、网络请求、任务、额度、账号归属、权限字典和公共 API 契约。
- 不修改 `StudioSider.tsx`，不得覆盖当前工作区中用户已有的“素造智能体”品牌改动。
- 不实现参考源码中不可到达的 `openVoiceDrawer`；声音行只展开/收起播放器。
- 不新增排序、下载、删除、重命名、批量操作或“选为当前”等参考声音菜单没有的入口。

## 文件结构与职责

### 创建

- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.ts`：字符时间轴、分句、秒数格式和刻度纯函数。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.test.ts`：纯函数边界与参考算法测试。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.ts`：全列表唯一 rAF 模拟播放 Hook。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.test.ts`：起播、切换、停止保留、自然结束复位和清理测试。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx`：声音卡片收起、展开、时间轴、字符高亮和原位编辑视图。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.test.tsx`：卡片事件隔离、跳转、拖动和编辑视图测试。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.tsx`：搜索、筛选、分页、展开集合、本地编辑和播放编排。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx`：7 条数据、分页、组合筛选、空态、播放切换和保存反馈测试。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.test.tsx`：声音路由委托和不打开详情抽屉的回归测试。

### 修改

- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`：增加页面本地 `VoiceItem` 类型，把 7 条声音更新为参考页精确值。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx`：外层按 `voices` 路由委托给 `VoiceLibraryView`；旧资产页实现只服务其余路由。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`：让既有 Toast 支持可选 `error` 语义，Modal 结构和文案保持不变。
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`：加入参考页声音卡片、时间轴、字符状态、编辑、空态与分页样式。

### 明确保持逐字节不变

- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/StudioAuthGate.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/**`
- `docs/API_CONTRACT.md`
- `docs/DOMAIN_MODEL.md`
- `docs/ASYNC_TASKS.md`
- `ai-video-api/**`

## 风险、工作区与审查安排

- 本任务为黄色中风险，但当前 `main` 有大量用户未提交登录页和品牌改动；实施必须先使用 `using-git-worktrees` 创建隔离工作树，分支建议为 `codex/voice-library-replica`。
- 工作树从包含本计划的提交创建；不要复制、暂存、清理或重置主工作区的未提交文件。
- 只有一个前端写入单元，任务串行执行；不并行修改 `model.ts`、`LibraryView.tsx`、`index.tsx` 或 `style.css`。
- 代码完成后使用 `requesting-code-review` 做一轮独立只读审查，覆盖视觉/交互、React 生命周期/无障碍、其他资产页/认证回归；只允许一轮必要修复和一次定向复核。
- 宣称完成前必须使用 `verification-before-completion`，以最新命令输出为证据。

---

### 任务 0：创建隔离工作树并固定基线

**文件：**
- 读取：`.agents/skills/antd/SKILL.md`
- 读取：`docs/superpowers/specs/2026-08-03-digital-human-studio-voice-library-design.md`
- 读取：`D:\AI\digital-human-studio.html`
- 基线测试：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`

- [ ] **步骤 1：使用工作树技能隔离当前脏工作区**

完整读取并执行 `.codex/skills/using-git-worktrees/SKILL.md`。从仓库根目录先确认 `.worktrees` 已忽略：

```powershell
git check-ignore -q .worktrees
git status --short
git branch --show-current
```

预期：第一条退出码为 `0`；当前分支为 `main`；状态中只出现用户既有登录页、品牌和 `StudioSider.tsx` 改动。

随后按技能创建：

```powershell
git worktree add '.worktrees\voice-library-replica' -b 'codex/voice-library-replica'
```

预期：新工作树创建成功，且新工作树 `git status --short` 为空。后续所有实现命令都从新工作树运行。

- [ ] **步骤 2：验证参考事实源未漂移**

```powershell
Get-FileHash -LiteralPath 'D:\AI\digital-human-studio.html' -Algorithm SHA256
```

预期：哈希严格等于 `0C688AB960764FE25638B55FC5B31C5E2BAFE84C3141FD236922739A0EF29694`。不一致时停止实现并报告参考页漂移。

- [ ] **步骤 3：读取 Ant Design 技能并查询本次保留的公开 Modal/Message API**

完整读取 `.agents/skills/antd/SKILL.md`，随后从用户端包目录运行：

```powershell
npx antd --lang zh info Modal
npx antd --lang zh info App
```

预期：确认既有 `Modal` 的 `open`、`title`、`okText`、`cancelText`、`onOk`、`onCancel` 仍为公开 API；本任务不覆盖 Ant Design 内部类名。

- [ ] **步骤 4：记录禁止修改文件哈希**

```powershell
Get-FileHash @(
  'ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\StudioAuthGate.tsx',
  'ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\StudioSider.tsx',
  'docs\API_CONTRACT.md',
  'docs\DOMAIN_MODEL.md',
  'docs\ASYNC_TASKS.md'
) -Algorithm SHA256 | Format-Table Path,Hash -AutoSize
```

预期：五行均有非空哈希；保留输出供任务 6 复核。

- [ ] **步骤 5：运行声音页面相关基线测试和工程检查**

从 `ai-video-ui/ai-video-webapp` 运行：

```powershell
npm test -- src/pages/digital-human-studio/index.test.tsx
npm run tsc
```

预期：现有认证门禁测试 PASS，TypeScript 退出码为 `0`。若基线失败，只记录为现有失败，不把它归因于本任务。

---

### 任务 1：用测试驱动参考声音数据与字符时间轴

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.test.ts`

- [ ] **步骤 1：先写纯函数失败测试**

创建 `voiceTimeline.test.ts`，至少写入以下完整用例：

```ts
import { describe, expect, it } from 'vitest';
import {
  buildVoiceTicks,
  formatVoiceSeconds,
  getVoiceWords,
  splitVoiceSentences,
} from './voiceTimeline';

describe('voiceTimeline', () => {
  it('按字符数连续分配整段时长并标记中文标点', () => {
    const words = getVoiceWords(['你好，', '世界。'], 12);

    expect(words.map((item) => item.word).join('')).toBe('你好，世界。');
    expect(words[0].start).toBe(0);
    expect(words.at(-1)?.start + (words.at(-1)?.dur ?? 0)).toBeCloseTo(12);
    expect(words.filter((item) => item.isPunct).map((item) => item.word)).toEqual([
      '，',
      '。',
    ]);
    words.slice(1).forEach((item, index) => {
      expect(item.start).toBeCloseTo(words[index].start + words[index].dur);
    });
  });

  it('格式化秒数并裁剪负值', () => {
    expect(formatVoiceSeconds(-1)).toBe('0:00');
    expect(formatVoiceSeconds(0)).toBe('0:00');
    expect(formatVoiceSeconds(62.9)).toBe('1:02');
  });

  it('按参考中文标点重新分句并移除空白', () => {
    expect(splitVoiceSentences(' 你好，\n 世界！ 再见。 ')).toEqual([
      '你好，',
      '世界！',
      '再见。',
    ]);
  });

  it('40 秒内使用 5 秒刻度，超过 40 秒使用 10 秒刻度', () => {
    expect(buildVoiceTicks(31).map((item) => item.seconds)).toEqual([
      0, 5, 10, 15, 20, 25, 30,
    ]);
    expect(buildVoiceTicks(31).filter((item) => item.major).map((item) => item.seconds)).toEqual([
      0, 10, 20, 30,
    ]);
    expect(buildVoiceTicks(62).map((item) => item.seconds)).toEqual([
      0, 10, 20, 30, 40, 50, 60,
    ]);
  });
});
```

运行：

```powershell
npm test -- src/pages/digital-human-studio/voices/voiceTimeline.test.ts
```

预期：FAIL，原因是 `voiceTimeline` 模块尚不存在。

- [ ] **步骤 2：实现参考算法的纯函数**

创建 `voiceTimeline.ts`，实现以下公开类型与函数：

```ts
export interface VoiceWord {
  dur: number;
  isPunct: boolean;
  start: number;
  word: string;
}

export interface VoiceTick {
  label?: string;
  leftPercent: number;
  major: boolean;
  seconds: number;
}

const PUNCTUATION = /[，。！？、；：]/;

export const clampVoicePercent = (value: number) =>
  Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0));

export const formatVoiceSeconds = (value: number) => {
  const seconds = Math.max(0, Math.floor(value));
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
};

export const getVoiceWords = (sentences: string[], seconds: number): VoiceWord[] => {
  const totalCharacters = sentences.reduce((sum, sentence) => sum + [...sentence].length, 0);
  if (totalCharacters === 0 || seconds <= 0) return [];

  let sentenceStart = 0;
  return sentences.flatMap((sentence) => {
    const characters = [...sentence];
    const sentenceDuration = (characters.length / totalCharacters) * seconds;
    const characterDuration = sentenceDuration / Math.max(1, characters.length);
    const words = characters.map((word, index) => ({
      dur: characterDuration,
      isPunct: PUNCTUATION.test(word),
      start: sentenceStart + index * characterDuration,
      word,
    }));
    sentenceStart += sentenceDuration;
    return words;
  });
};

export const splitVoiceSentences = (value: string) => {
  const compact = value.replace(/\s+/g, '');
  return compact.match(/[^，。！？、；：\n]+[，。！？、；：]?/g) ?? (compact ? [compact] : []);
};

export const buildVoiceTicks = (seconds: number): VoiceTick[] => {
  if (seconds <= 0) return [];
  const step = seconds <= 40 ? 5 : 10;
  const majorEvery = step * 2;
  const ticks: VoiceTick[] = [];
  for (let current = 0; current <= seconds; current += step) {
    const major = current % majorEvery === 0;
    ticks.push({
      label: major ? formatVoiceSeconds(current) : undefined,
      leftPercent: (current / seconds) * 100,
      major,
      seconds: current,
    });
  }
  return ticks;
};
```

- [ ] **步骤 3：增加显式 `VoiceItem` 并替换 7 条数据**

在 `model.ts` 的资产类型附近加入：

```ts
export interface VoiceItem {
  dur: string;
  id: string;
  meta: string;
  name: string;
  owner: AssetOwner;
  script: string;
  secs: number;
  sents: string[];
  status: AssetStatus;
  type: VoiceType;
}
```

将 `VOICES` 替换为参考页精确数据；必须逐字保留以下 7 个 id、时长、大小、文案和分句：

```ts
export const VOICES: readonly VoiceItem[] = [
  {
    id: 'vs-003', name: '夏季新品 60秒 克隆', type: 'clone', owner: 'custom',
    meta: '源自：亲切女声（参考） · 4.8MB', dur: '01:02', secs: 62, status: 'verified',
    script: '夏季新品正式上线，限时三天全场八折。这次我们准备了超多惊喜，更有隐藏福利等你来发现。',
    sents: ['夏季新品正式上线，', '限时三天全场八折。', '这次我们准备了超多惊喜，', '更有隐藏福利等你来发现。'],
  },
  {
    id: 'vs-004', name: '门店引流 30秒 克隆', type: 'clone', owner: 'custom',
    meta: '源自：商务原声 A · 2.6MB', dur: '00:31', secs: 31, status: 'verified',
    script: '本周末门店引流活动开启，到店即送精美礼品一份，数量有限先到先得。',
    sents: ['本周末门店引流活动开启，', '到店即送精美礼品一份，', '数量有限先到先得。'],
  },
  {
    id: 'vs-001', name: '亲切女声（参考）', type: 'origin', owner: 'custom',
    meta: '3.6MB', dur: '00:45', secs: 45, status: 'verified',
    script: '大家好，欢迎来到我们的直播间。今天给大家带来一款非常好用的清洁产品，记得点赞收藏哦。',
    sents: ['大家好，欢迎来到我们的直播间。', '今天给大家带来一款非常好用的清洁产品，', '记得点赞收藏哦。'],
  },
  {
    id: 'vs-002', name: '商务原声 A', type: 'origin', owner: 'custom',
    meta: '2.4MB', dur: '00:32', secs: 32, status: 'verified',
    script: '专注于企业级解决方案，我们致力于为客户提供最优质的服务体验。',
    sents: ['专注于企业级解决方案，', '我们致力于为客户提供最优质的服务体验。'],
  },
  {
    id: 'vs-005', name: '温柔讲书声', type: 'origin', owner: 'custom',
    meta: '5.8MB', dur: '01:12', secs: 72, status: 'pending',
    script: '夜深了，城市的喧嚣渐渐平息。在这个安静的时刻，让我为你读一段温暖的故事，陪你度过这个美好的夜晚。',
    sents: ['夜深了，城市的喧嚣渐渐平息。', '在这个安静的时刻，', '让我为你读一段温暖的故事，', '陪你度过这个美好的夜晚。'],
  },
  {
    id: 'vs-201', name: '清亮女声', type: 'public', owner: 'public',
    meta: '3.9MB', dur: '00:47', secs: 47, status: 'verified',
    script: '探店打卡新地标，这家店真的太出片了。每一处角落都是绝佳拍照点，快约上闺蜜一起冲。',
    sents: ['探店打卡新地标，', '这家店真的太出片了。', '每一处角落都是绝佳拍照点，', '快约上闺蜜一起冲。'],
  },
  {
    id: 'vs-202', name: '磁性男声', type: 'public', owner: 'public',
    meta: '3.1MB', dur: '00:38', secs: 38, status: 'verified',
    script: '失眠困扰着很多人，今天分享三个调理小方法，帮你找回安稳睡眠。',
    sents: ['失眠困扰着很多人，', '今天分享三个调理小方法，', '帮你找回安稳睡眠。'],
  },
];
```

- [ ] **步骤 4：运行测试和类型检查**

```powershell
npm test -- src/pages/digital-human-studio/voices/voiceTimeline.test.ts
npm run tsc
```

预期：纯函数测试全部 PASS，TypeScript 退出码为 `0`。

- [ ] **步骤 5：提交数据与纯函数**

```powershell
git add -- 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.ts' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/voiceTimeline.test.ts'
git diff --cached --check
git commit -m 'feat(studio): add exact voice timeline data'
```

预期：提交只包含上述 3 个文件。

---

### 任务 2：用测试驱动唯一模拟播放 Hook

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.test.ts`

- [ ] **步骤 1：先写播放生命周期失败测试**

测试使用 `renderHook`，固定 `performance.now`、`requestAnimationFrame` 和 `cancelAnimationFrame`，覆盖以下断言：

```ts
import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VOICES } from '../model';
import { useVoicePlayback } from './useVoicePlayback';

describe('useVoicePlayback', () => {
  let frame: FrameRequestCallback | undefined;
  let now = 1_000;

  beforeEach(() => {
    frame = undefined;
    now = 1_000;
    vi.spyOn(performance, 'now').mockImplementation(() => now);
    vi.stubGlobal('requestAnimationFrame', vi.fn((callback: FrameRequestCallback) => {
      frame = callback;
      return 7;
    }));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('从指定位置推进，手动停止保留位置', () => {
    const { result } = renderHook(() => useVoicePlayback());
    act(() => result.current.play(VOICES[0], 0.5));
    now += 6_200;
    act(() => frame?.(now));

    expect(result.current.playingVoiceId).toBe('vs-003');
    expect(result.current.progressByVoice['vs-003']).toBeCloseTo(0.6);

    act(() => result.current.stop());
    expect(result.current.playingVoiceId).toBeNull();
    expect(result.current.progressByVoice['vs-003']).toBeCloseTo(0.6);
  });

  it('开始另一条时取消上一帧但保留上一条位置', () => {
    const { result } = renderHook(() => useVoicePlayback());
    act(() => result.current.play(VOICES[0], 0.25));
    act(() => result.current.play(VOICES[1], 0));

    expect(cancelAnimationFrame).toHaveBeenCalledWith(7);
    expect(result.current.playingVoiceId).toBe('vs-004');
    expect(result.current.progressByVoice['vs-003']).toBe(0.25);
  });

  it('自然结束时复位当前声音并在卸载时清理动画帧', () => {
    const { result, unmount } = renderHook(() => useVoicePlayback());
    act(() => result.current.play(VOICES[1], 0.9));
    now += 3_200;
    act(() => frame?.(now));

    expect(result.current.playingVoiceId).toBeNull();
    expect(result.current.progressByVoice['vs-004']).toBe(0);

    act(() => result.current.play(VOICES[0], 0));
    unmount();
    expect(cancelAnimationFrame).toHaveBeenCalled();
  });
});
```

运行：

```powershell
npm test -- src/pages/digital-human-studio/voices/useVoicePlayback.test.ts
```

预期：FAIL，原因是 Hook 模块尚不存在。

- [ ] **步骤 2：实现唯一播放引擎**

实现以下 API；手动 `stop` 不改进度，`play` 先取消旧帧，自然结束才把当前声音进度复位为 `0`：

```ts
import { useCallback, useEffect, useRef, useState } from 'react';
import type { VoiceItem } from '../model';
import { clampVoicePercent } from './voiceTimeline';

export const useVoicePlayback = () => {
  const [playingVoiceId, setPlayingVoiceId] = useState<string | null>(null);
  const [progressByVoice, setProgressByVoice] = useState<Record<string, number>>({});
  const frameRef = useRef<number | undefined>(undefined);
  const generationRef = useRef(0);

  const cancelCurrentFrame = useCallback(() => {
    generationRef.current += 1;
    if (frameRef.current !== undefined) cancelAnimationFrame(frameRef.current);
    frameRef.current = undefined;
  }, []);

  const stop = useCallback(() => {
    cancelCurrentFrame();
    setPlayingVoiceId(null);
  }, [cancelCurrentFrame]);

  const play = useCallback((voice: VoiceItem, startPercent = 0) => {
    cancelCurrentFrame();
    if (voice.secs <= 0) {
      setPlayingVoiceId(null);
      return;
    }

    const start = clampVoicePercent(startPercent);
    const generation = generationRef.current;
    const startedAt = performance.now();
    const startedAtSeconds = voice.secs * start;
    setProgressByVoice((current) => ({ ...current, [voice.id]: start }));
    setPlayingVoiceId(voice.id);

    const loop = () => {
      if (generationRef.current !== generation) return;
      const elapsed = (performance.now() - startedAt) / 1_000 + startedAtSeconds;
      const percent = clampVoicePercent(elapsed / voice.secs);
      if (percent >= 1) {
        frameRef.current = undefined;
        setProgressByVoice((current) => ({ ...current, [voice.id]: 0 }));
        setPlayingVoiceId(null);
        return;
      }
      setProgressByVoice((current) => ({ ...current, [voice.id]: percent }));
      frameRef.current = requestAnimationFrame(loop);
    };

    frameRef.current = requestAnimationFrame(loop);
  }, [cancelCurrentFrame]);

  const toggle = useCallback((voice: VoiceItem) => {
    if (playingVoiceId === voice.id) stop();
    else play(voice, 0);
  }, [play, playingVoiceId, stop]);

  useEffect(() => () => cancelCurrentFrame(), [cancelCurrentFrame]);

  return { play, playingVoiceId, progressByVoice, stop, toggle };
};
```

- [ ] **步骤 3：运行定向测试和类型检查**

```powershell
npm test -- src/pages/digital-human-studio/voices/useVoicePlayback.test.ts
npm run tsc
```

预期：Hook 测试全部 PASS；TypeScript 退出码为 `0`。

- [ ] **步骤 4：提交播放 Hook**

```powershell
git add -- 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.ts' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/useVoicePlayback.test.ts'
git diff --cached --check
git commit -m 'feat(studio): add single voice playback engine'
```

预期：提交只包含 2 个 Hook 文件。

---

### 任务 3：用测试驱动声音卡片的展开、跳转与编辑视图

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.test.tsx`

- [ ] **步骤 1：先写卡片失败测试**

测试以 `VOICES[0]` 为固定输入，显式验证：

```tsx
const defaultProps = {
  draft: '',
  editing: false,
  expanded: false,
  onCancelEdit: vi.fn(),
  onDraftChange: vi.fn(),
  onEdit: vi.fn(),
  onPlayToggle: vi.fn(),
  onSaveEdit: vi.fn(),
  onSeek: vi.fn(),
  onToggle: vi.fn(),
  playing: false,
  progress: 0,
  voice: VOICES[0],
};

it('点击行展开，但播放按钮只触发播放', () => {
  render(<VoiceCard {...defaultProps} />);
  fireEvent.click(screen.getByRole('button', { name: '展开 夏季新品 60秒 克隆' }));
  expect(defaultProps.onToggle).toHaveBeenCalledTimes(1);

  fireEvent.click(screen.getByRole('button', { name: '播放 夏季新品 60秒 克隆' }));
  expect(defaultProps.onPlayToggle).toHaveBeenCalledTimes(1);
  expect(defaultProps.onToggle).toHaveBeenCalledTimes(1);
});

it('展开后按字符和时间轴位置请求跳转', () => {
  render(<VoiceCard {...defaultProps} expanded />);
  fireEvent.click(screen.getByText('夏', { selector: '.vw' }));
  expect(defaultProps.onSeek).toHaveBeenCalledWith(0);

  const track = screen.getByRole('slider', { name: '夏季新品 60秒 克隆播放进度' });
  vi.spyOn(track, 'getBoundingClientRect').mockReturnValue({
    bottom: 28, height: 28, left: 10, right: 210, top: 0, width: 200, x: 10, y: 0,
    toJSON: () => ({}),
  });
  fireEvent.pointerDown(track, { clientX: 110, pointerId: 1 });
  expect(defaultProps.onSeek).toHaveBeenLastCalledWith(0.5);
});

it('编辑态显示原位文案、取消和保存按钮', () => {
  render(<VoiceCard {...defaultProps} draft="新的示范文案。" editing expanded />);
  expect(screen.getByText('编辑中…')).toBeVisible();
  expect(screen.getByRole('textbox', { name: '编辑示范文案' })).toHaveTextContent('新的示范文案。');
  fireEvent.click(screen.getByRole('button', { name: '取消' }));
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  expect(defaultProps.onCancelEdit).toHaveBeenCalled();
  expect(defaultProps.onSaveEdit).toHaveBeenCalled();
});
```

运行：

```powershell
npm test -- src/pages/digital-human-studio/voices/VoiceCard.test.tsx
```

预期：FAIL，原因是 `VoiceCard` 尚不存在。

- [ ] **步骤 2：实现稳定 Props 与交互边界**

`VoiceCard.tsx` 使用以下完整 Props，不自行管理全局播放或数据集合：

```ts
interface VoiceCardProps {
  draft: string;
  editing: boolean;
  expanded: boolean;
  onCancelEdit: () => void;
  onDraftChange: (value: string) => void;
  onEdit: () => void;
  onPlayToggle: () => void;
  onSaveEdit: () => void;
  onSeek: (percent: number) => void;
  onToggle: () => void;
  playing: boolean;
  progress: number;
  voice: VoiceItem;
}
```

实现必须遵守以下精确 JSX 结构和事件规则：

```tsx
<article className={`vcard${expanded ? ' expanded' : ''}${playing ? ' playing' : ''}`} data-od-id={`voice-${voice.id}`}>
  <div
    aria-controls={`voice-player-${voice.id}`}
    aria-expanded={expanded}
    aria-label={`${expanded ? '收起' : '展开'} ${voice.name}`}
    className="vrow"
    onClick={onToggle}
    onKeyDown={(event) => {
      if (event.currentTarget !== event.target) return;
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        onToggle();
      }
    }}
    role="button"
    tabIndex={0}
  >
    <button
      aria-label={`${playing ? '暂停' : '播放'} ${voice.name}`}
      className="voice-play"
      disabled={voice.secs <= 0}
      onClick={(event) => {
        event.stopPropagation();
        onPlayToggle();
      }}
      type="button"
    >
      <StudioIcon name={playing ? 'pause' : 'play'} />
    </button>
    <div className="vinfo">
      <div className="vname">
        {voice.name}
        <span className={`tag ${voice.status === 'verified' ? 'tag-success' : 'tag-warn'}`}>
          {voice.status === 'verified' ? '已校验' : '校验中'}
        </span>
        <span className={`tag ${voice.type === 'clone' ? 'tag-accent' : 'tag-soft'}`}>
          {voice.type === 'clone' ? '克隆' : voice.type === 'origin' ? '原声' : '公共'}
        </span>
      </div>
      <div className="vmeta">{voice.meta} · {voice.script.slice(0, 22)}…</div>
    </div>
    <span className="vdur">{voice.dur}</span>
    <span className="vchev">
      <StudioIcon name="right" />
    </span>
  </div>
  <div className="vplayer" id={`voice-player-${voice.id}`} />
</article>
```

本步骤先固定卡片头部和事件隔离；紧接着的步骤 3、4 把空的 `.vplayer` 替换为完整时间轴与编辑内容，并在同一任务提交。

- [ ] **步骤 3：实现时间轴、字符状态和无全局监听拖动**

- 用 `getVoiceWords(voice.sents, voice.secs)` 和 `buildVoiceTicks(voice.secs)` 派生视图，不放入 state。
- 当前秒数固定为 `progress * voice.secs`；填充宽度和播放头 `left` 固定为 `${progress * 100}%`。
- 字符结束时间不大于当前秒数时为 `done`；字符开始时间不大于当前秒数时为 `now`；`progress === 0` 时不标首字。
- 时间轴使用 `role="slider"`、`aria-valuemin={0}`、`aria-valuemax={voice.secs}`、`aria-valuenow={currentSeconds}`。
- 指针按下时保存 `pointerId` 并调用 `setPointerCapture`；移动期间只处理同一指针；抬起/取消时释放。不得向 `window` 或 `document` 挂监听器。
- 位置换算严格使用：

```ts
const seekFromClientX = (element: HTMLElement, clientX: number) => {
  const rect = element.getBoundingClientRect();
  return clampVoicePercent(rect.width > 0 ? (clientX - rect.left) / rect.width : 0);
};
```

- 每个字符渲染为可点击 `button` 风格的 `span`：`role="button"`、`tabIndex={0}`，点击或 Enter/Space 调用 `onSeek(word.start / voice.secs)`。

- [ ] **步骤 4：实现参考原位编辑视图**

- 非编辑态显示字符 span、“示范文案”“编辑”和固定提示文案。
- 编辑态显示 `contentEditable` 的 `.vptext`、`aria-label="编辑示范文案"`、`role="textbox"`、`aria-multiline="true"`，`onInput` 把 `currentTarget.textContent ?? ''` 传给 `onDraftChange`。
- 编辑态工具条严格显示“编辑中…”、“取消”、“保存”；三个操作都不得冒泡到卡片行。
- `VoiceCard` 不校验空值、不修改 `voice`，保存和取消只调用传入回调。

- [ ] **步骤 5：运行卡片测试和类型检查**

```powershell
npm test -- src/pages/digital-human-studio/voices/VoiceCard.test.tsx
npm run tsc
```

预期：卡片测试全部 PASS；TypeScript 退出码为 `0`。

- [ ] **步骤 6：提交声音卡片**

```powershell
git add -- 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.tsx' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceCard.test.tsx'
git diff --cached --check
git commit -m 'feat(studio): add interactive voice cards'
```

预期：提交只包含 2 个卡片文件。

---

### 任务 4：用测试驱动声音列表搜索、筛选、分页和编辑编排

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx`

- [ ] **步骤 1：先写列表容器失败测试**

使用 fake timers 覆盖以下完整用户路径：

```tsx
const renderView = () => {
  const onAddVoice = vi.fn();
  const onToast = vi.fn();
  const result = render(<VoiceLibraryView onAddVoice={onAddVoice} onToast={onToast} />);
  return { ...result, onAddVoice, onToast };
};

it('初始显示前 6 条、过滤总数和第 2 页最后一条', () => {
  renderView();
  expect(document.querySelectorAll('.vcard')).toHaveLength(6);
  expect(screen.getByText('共 7 条')).toBeVisible();
  expect(screen.getByText('7', { selector: '.library-list-title .tag' })).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '2' }));
  expect(screen.getByText('磁性男声')).toBeVisible();
  expect(screen.getByText('7-7 / 7')).toBeVisible();
});

it('筛选重置页码，搜索在 250ms 后与筛选组合生效', () => {
  vi.useFakeTimers();
  renderView();
  fireEvent.click(screen.getByRole('button', { name: '2' }));
  fireEvent.click(screen.getByRole('button', { name: '原声' }));
  fireEvent.click(screen.getByRole('button', { name: '校验中' }));
  expect(screen.getByText('温柔讲书声')).toBeVisible();
  expect(screen.getByText('共 1 条')).toBeVisible();

  fireEvent.change(screen.getByPlaceholderText('搜索声音名称'), { target: { value: '不存在' } });
  act(() => vi.advanceTimersByTime(249));
  expect(screen.getByText('温柔讲书声')).toBeVisible();
  act(() => vi.advanceTimersByTime(1));
  expect(screen.getByText('没有匹配的声音')).toBeVisible();
  expect(screen.getByText('共 0 条')).toBeVisible();
  vi.useRealTimers();
});

it('没有结果时显示参考空态，上传按钮调用现有回调', () => {
  const { onAddVoice } = renderView();
  fireEvent.change(screen.getByPlaceholderText('搜索声音名称'), { target: { value: '不存在' } });
  act(() => vi.advanceTimersByTime(250));
  expect(screen.getByText('没有匹配的声音')).toBeVisible();
  expect(screen.getByText('试试调整筛选或上传原声音')).toBeVisible();
  fireEvent.click(screen.getByRole('button', { name: '上传原声' }));
  expect(onAddVoice).toHaveBeenCalledTimes(1);
});

it('空文案报错，有效保存更新当前声音并保持展开', () => {
  const { onToast } = renderView();
  fireEvent.click(screen.getByRole('button', { name: '展开 夏季新品 60秒 克隆' }));
  fireEvent.click(screen.getByRole('button', { name: '编辑' }));
  const editor = screen.getByRole('textbox', { name: '编辑示范文案' });
  fireEvent.input(editor, { target: { textContent: '   ' } });
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  expect(onToast).toHaveBeenLastCalledWith('文案不能为空', 'error');
  expect(screen.getByText('编辑中…')).toBeVisible();

  fireEvent.input(editor, { target: { textContent: ' 新的  示范文案。 ' } });
  fireEvent.click(screen.getByRole('button', { name: '保存' }));
  expect(onToast).toHaveBeenLastCalledWith('文案已保存', 'success');
  expect(document.querySelector('[data-vtext="vs-003"]')).toHaveTextContent('新的示范文案。');
  expect(screen.getByRole('button', { name: '收起 夏季新品 60秒 克隆' })).toBeVisible();
});
```

在测试文件 `beforeEach` 中调用 `vi.useFakeTimers()`，在 `afterEach` 中调用 `vi.useRealTimers()` 和 `vi.restoreAllMocks()`，避免计时器泄漏。

运行：

```powershell
npm test -- src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx
```

预期：FAIL，原因是容器模块尚不存在。

- [ ] **步骤 2：实现固定状态和筛选派生**

组件 Props 固定为：

```ts
interface VoiceLibraryViewProps {
  onAddVoice: () => void;
  onToast: (message: string, type?: 'success' | 'error') => void;
}
```

组件状态固定为：

```ts
const PAGE_SIZE = 6;
const [voices, setVoices] = useState<VoiceItem[]>(() =>
  VOICES.map((voice) => ({ ...voice, sents: [...voice.sents] })),
);
const [searchInput, setSearchInput] = useState('');
const [search, setSearch] = useState('');
const [type, setType] = useState<'all' | VoiceType>('all');
const [status, setStatus] = useState<'all' | AssetStatus>('all');
const [page, setPage] = useState(1);
const [expandedIds, setExpandedIds] = useState<Set<string>>(() => new Set());
const [editingId, setEditingId] = useState<string | null>(null);
const [draft, setDraft] = useState('');
```

搜索 effect 必须在 250ms 后同时更新 `search` 和把页码设回 1，并在依赖变化或卸载时 `clearTimeout`。筛选按钮点击立即设置筛选并回到第 1 页。

过滤条件严格为：类型匹配、状态匹配，且小写名称包含小写搜索词或原始 `meta` 包含搜索词。`totalPages` 使用 `Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))`；页码超界时用 effect 收敛；当前页只做 `slice`，不复制为 state。

- [ ] **步骤 3：实现播放、展开、编辑和不可见停止编排**

事件实现使用以下规则：

```ts
const setExpanded = (id: string, expanded: boolean) => {
  setExpandedIds((current) => {
    const next = new Set(current);
    if (expanded) next.add(id);
    else next.delete(id);
    return next;
  });
};

const handlePlayToggle = (voice: VoiceItem) => {
  setExpanded(voice.id, true);
  playback.toggle(voice);
};

const handleSeek = (voice: VoiceItem, percent: number) => {
  setExpanded(voice.id, true);
  playback.play(voice, percent);
};

const handleEdit = (voice: VoiceItem) => {
  playback.stop();
  setExpanded(voice.id, true);
  setEditingId(voice.id);
  setDraft(voice.script);
};

const handleSave = (voice: VoiceItem) => {
  const normalized = draft.replace(/\s+/g, ' ').trim();
  if (!normalized) {
    onToast('文案不能为空', 'error');
    return;
  }
  setVoices((current) => current.map((item) =>
    item.id === voice.id
      ? { ...item, script: normalized, sents: splitVoiceSentences(normalized) }
      : item,
  ));
  setEditingId(null);
  setDraft('');
  setExpanded(voice.id, true);
  onToast('文案已保存', 'success');
};
```

当前页 `pageData` 的 id 集合变化后，如果 `playback.playingVoiceId` 不在集合中，立即 `playback.stop()`。取消编辑只清空 `editingId`/`draft` 并保持展开，不写 `voices`。

- [ ] **步骤 4：渲染参考工具栏、空态和分页**

- 工具栏顺序固定为：搜索、类型分段、状态分段、上传原声、spacer、`共 N 条`。
- 类型按钮固定为“全部类型 / 克隆 / 原声 / 公共”；状态固定为“全部状态 / 已校验 / 校验中”。
- 标题固定为“声音列表”，旁边徽标显示过滤后数量。
- `pageData` 为空时只显示参考空态。
- 总页数大于 1 才显示 `.pagination`；第 1 页不显示向左按钮，第 2 页显示向左按钮；页码按钮显示全部页；区间为 `1-6 / 7` 或 `7-7 / 7`。
- 每个 `VoiceCard` 的 `playing` 为 `playback.playingVoiceId === voice.id`，`progress` 为 `playback.progressByVoice[voice.id] ?? 0`。

- [ ] **步骤 5：运行容器与全部声音单元测试**

```powershell
npm test -- src/pages/digital-human-studio/voices
npm run tsc
```

预期：4 个声音测试文件全部 PASS；TypeScript 退出码为 `0`。

- [ ] **步骤 6：提交声音列表容器**

```powershell
git add -- 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.tsx' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/voices/VoiceLibraryView.test.tsx'
git diff --cached --check
git commit -m 'feat(studio): add exact voice library behavior'
```

预期：提交只包含 2 个容器文件。

---

### 任务 5：接入声音路由、错误 Toast 与参考 CSS

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

- [ ] **步骤 1：先写声音路由委托失败测试**

在 `LibraryView.test.tsx` 渲染真实组件并验证展开声音不会触发抽屉：

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import LibraryView from './LibraryView';

describe('LibraryView voices route', () => {
  it('委托声音列表并用行内展开替代详情抽屉', () => {
    const onDetail = vi.fn();
    render(
      <LibraryView
        onAddAvatar={vi.fn()}
        onAddVoice={vi.fn()}
        onDetail={onDetail}
        onNavigateCreate={vi.fn()}
        onToast={vi.fn()}
        route="voices"
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '展开 夏季新品 60秒 克隆' }));
    expect(screen.getByText('示范文案')).toBeVisible();
    expect(onDetail).not.toHaveBeenCalled();
  });
});
```

运行：

```powershell
npm test -- src/pages/digital-human-studio/components/LibraryView.test.tsx
```

预期：FAIL，因为当前 `LibraryView` 仍渲染静态波形卡并打开抽屉。

- [ ] **步骤 2：用无 Hook 的外层组件委托 voices 路由**

把当前 700 行组件函数重命名为 `AssetLibraryView`，不要改变其函数体；在文件顶部导入 `VoiceLibraryView`，并在文件底部增加：

```tsx
const LibraryView: React.FC<LibraryViewProps> = (props) => {
  if (props.route === 'voices') {
    return (
      <VoiceLibraryView
        onAddVoice={props.onAddVoice}
        onToast={props.onToast}
      />
    );
  }
  return <AssetLibraryView {...props} />;
};
```

外层不调用 Hook，因此在 `voices` 与其他路由之间切换时不会违反 Hook 调用顺序；声音分支不会挂载旧 `openVoice`，也就不会触发详情抽屉。`LibraryViewProps.onToast` 更新为：

```ts
onToast: (message: string, type?: 'success' | 'error') => void;
```

- [ ] **步骤 3：让现有 Toast 接受可选错误语义**

只把 `index.tsx` 的回调改为：

```ts
const toast = useCallback(
  (content: string, type: 'success' | 'error' = 'success') => {
    void messageApi.open({ content, type });
  },
  [messageApi],
);
```

不得改 Modal JSX；“新增原声音”、上传区、格式/时长、声音名称、备注、警告、取消和“上传并保存”全部保持现状。

- [ ] **步骤 4：移植参考 CSS，并隔离共享 `.voice-play`**

在 `.studio-shell` 变量区增加：

```css
--ease: cubic-bezier(0.28, 0, 0.22, 1);
--focus-ring: 0 0 0 4px color-mix(in oklab, var(--accent), transparent 65%);
```

移除旧 `.voice-card`、`.voice-main`、`.voice-title`、`.voice-bars`、`.voice-side` 列表样式；详情抽屉若仍使用 `.voice-bars`，保留它需要的波形规则并限定到 `.detail-audio-card .voice-bars`。新增声音页样式必须使用参考值：

```css
.voice-list { display: flex; flex-direction: column; gap: 8px; }
.vcard { overflow: hidden; border: 1px solid var(--border-soft); border-radius: 14px; background: var(--bg); transition: border-color .15s var(--ease); }
.vcard.playing { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.vcard.expanded { border-color: var(--accent); }
.vrow { display: flex; align-items: center; gap: 11px; padding: 11px 15px; cursor: pointer; transition: background .15s var(--ease); }
.vrow:hover { background: var(--surface-warm); }
.voice-library .voice-play { display: grid; width: 38px; height: 38px; flex-shrink: 0; place-items: center; border: 0; border-radius: 50%; background: var(--accent); color: var(--accent-on); transition: background 150ms var(--ease); }
.voice-library .voice-play:hover { background: var(--accent-hover); }
.voice-library .voice-play svg { width: 16px; height: 16px; }
.vinfo { min-width: 0; flex: 1; }
.vname { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 13.5px; font-weight: 600; }
.vmeta { overflow: hidden; margin-top: 2px; color: var(--muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.vdur { flex-shrink: 0; color: var(--fg-2); font-family: var(--font-mono); font-size: 11px; font-weight: 600; }
.vchev { flex-shrink: 0; color: var(--meta); transition: transform .2s var(--ease); }
.vcard.expanded .vchev { transform: rotate(90deg); }
.vplayer { max-height: 0; overflow: hidden; border-top: 1px solid transparent; padding: 0 15px; transition: max-height .25s var(--ease), padding .25s var(--ease); }
.vcard.expanded .vplayer { max-height: 520px; border-top-color: var(--border-soft); padding: 6px 15px 15px; }
.vtimeline { margin: 4px 0 14px; padding: 9px 0 4px; user-select: none; }
.vtl-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.vtl-cur { color: var(--accent); font-family: var(--font-mono); font-size: 11px; font-weight: 600; letter-spacing: .02em; }
.vtl-dur { color: var(--meta); font-family: var(--font-mono); font-size: 10.5px; }
.vtl-track { position: relative; height: 28px; border-radius: 6px; cursor: pointer; }
.vtl-rail { position: absolute; top: 12px; right: 0; left: 0; height: 4px; border-radius: 2px; background: var(--border-soft); }
.vtl-fill { position: absolute; top: 12px; left: 0; width: 0; height: 4px; border-radius: 2px; background: var(--accent); transition: width .08s linear; }
.vtl-head { position: absolute; top: 4px; left: 0; width: 3px; height: 20px; border-radius: 1.5px; background: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); pointer-events: none; transform: translateX(-1.5px); transition: left .08s linear; }
.vtl-ticks { position: absolute; top: 0; right: 0; left: 0; height: 28px; pointer-events: none; }
.vtl-tick { position: absolute; top: 18px; width: 1px; height: 4px; background: var(--border); }
.vtl-tick-major { top: 15px; height: 7px; background: var(--meta); }
.vtl-ticklab { position: absolute; top: 0; color: var(--meta); font-family: var(--font-mono); font-size: 8.5px; line-height: 1; transform: translateX(-50%); }
.vtl-track:hover .vtl-head { box-shadow: 0 0 0 5px var(--accent-soft); }
.vptext { color: var(--meta); font-size: 15px; line-height: 2.1; letter-spacing: .01em; }
.vptext .vw { display: inline-block; border: 0; border-radius: 3px; background: transparent; padding: 1px; cursor: pointer; transition: color .1s var(--ease), background .1s; }
.vptext .vw.punct { color: var(--border); }
.vptext .vw:hover { background: var(--surface); }
.vptext .vw.done { color: var(--fg-2); }
.vptext .vw.done.punct { color: var(--meta); }
.vptext .vw.now { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
.vhint { margin-top: 10px; color: var(--meta); font-family: var(--font-mono); font-size: 10.5px; letter-spacing: .02em; }
.vptext-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.vptext-bar-lab { color: var(--fg-2); font-size: 11px; font-weight: 600; letter-spacing: .02em; }
.vptext-bar-lab.editing { color: var(--accent); }
.vedit-actions { display: flex; gap: 4px; }
.vedit-btn { border: 0; border-radius: 6px; background: transparent; padding: 3px 8px; color: var(--accent); font-family: var(--font-body); font-size: 11px; font-weight: 600; cursor: pointer; transition: background .15s var(--ease); }
.vedit-btn:hover { background: var(--accent-soft); }
.vedit-btn.ghost { color: var(--meta); }
.vedit-btn.ghost:hover { background: var(--surface); }
.vptext[contenteditable="true"] { border: 1px solid var(--accent); border-radius: 8px; outline: 0; background: var(--surface-warm); padding: 10px 12px; line-height: 2; letter-spacing: .01em; }
.vptext[contenteditable="true"]:focus { box-shadow: var(--focus-ring); }
.pagination { display: flex; align-items: center; justify-content: center; gap: 4px; margin-top: 24px; }
.pagination button { min-width: 32px; height: 32px; border: 1px solid var(--border); border-radius: 8px; background: var(--bg); padding: 0 8px; color: var(--fg-2); font-size: 13px; transition: all 150ms var(--ease); }
.pagination button:hover:not(:disabled) { border-color: var(--fg-2); }
.pagination button.active { border-color: var(--accent); background: var(--accent); color: var(--accent-on); }
.pagination .pg-info { margin-left: 8px; color: var(--muted); font-size: 12px; }
.empty-state { padding: 48px 24px; text-align: center; }
.empty-state svg { width: 40px; height: 40px; margin: 0 auto 12px; color: var(--border); }
.empty-state-title { color: var(--fg-2); font-size: 15px; font-weight: 600; }
.empty-state-desc { margin-top: 4px; color: var(--muted); font-size: 13px; }
```

`VoiceLibraryView` 根节点必须同时带 `library-page voice-library`，使播放按钮覆盖只作用于声音菜单。箭头图标元素必须带 `.vchev`。

- [ ] **步骤 5：运行接入测试和认证回归**

```powershell
npm test -- src/pages/digital-human-studio/components/LibraryView.test.tsx src/pages/digital-human-studio/voices src/pages/digital-human-studio/index.test.tsx
npm run tsc
npm run biome:lint
```

预期：声音、路由委托和现有认证门禁测试全部 PASS；TypeScript 和 Biome 退出码均为 `0`。

- [ ] **步骤 6：提交路由和样式接入**

```powershell
git add -- 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.test.tsx' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx' 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css'
git diff --cached --check
git commit -m 'feat(studio): integrate exact voice library replica'
```

预期：提交只包含上述 4 个文件。

---

### 任务 6：全量验证、浏览器一比一验收与独立审查

**文件：**
- 验证：所有本计划创建和修改的文件
- 复核：任务 0 的禁止修改文件哈希

- [ ] **步骤 1：运行完整自动验证**

从 `ai-video-ui/ai-video-webapp` 依次运行：

```powershell
npm test
npm run tsc
npm run biome:lint
npm run build
```

预期：所有命令退出码为 `0`；测试文件数和测试数均大于 0；构建生成 `dist`。任何失败都必须先按 `systematic-debugging` 找到根因，不能删除或放宽测试来通过。

- [ ] **步骤 2：复核禁止修改范围和提交内容**

```powershell
git status --short
git diff main...HEAD --name-only
Get-FileHash @(
  'ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\StudioAuthGate.tsx',
  'ai-video-ui\ai-video-webapp\src\pages\digital-human-studio\components\StudioSider.tsx',
  'docs\API_CONTRACT.md',
  'docs\DOMAIN_MODEL.md',
  'docs\ASYNC_TASKS.md'
) -Algorithm SHA256 | Format-Table Path,Hash -AutoSize
```

预期：工作树干净；差异只包含本计划列出的声音文件；五个哈希与任务 0 完全一致。

- [ ] **步骤 3：启动用户端并用内置浏览器逐项验收**

```powershell
npm run dev
```

使用 `browser:control-in-app-browser` 打开开发服务器，登录后进入数字人工作台“声音”。桌面视口逐项确认：

1. 工具栏顺序、全部中文文案、总数 7、标题徽标 7。
2. 第 1 页 6 条、第 2 页 `vs-202`、区间 `1-6 / 7` 与 `7-7 / 7`。
3. 7 条名称、类型、状态、大小、时长和示范文案逐字对应参考源。
4. 默认、悬停、展开、播放、编辑、空态的边框、圆角、间距、字号、标签、箭头和主色状态。
5. 行点击只展开；播放自动展开；手停保留位置；新声音停止上一条；自然结束复位。
6. 时间轴点击/拖动、字符/标点点击、当前时间、填充、播放头和字符高亮同步。
7. 编辑取消不写数据；空值错误；有效保存压缩空白并保持展开。
8. 上传原声仍打开现有 Modal，点击上传区和确认反馈正确。
9. 搜索 250ms 后生效；类型/状态/组合筛选和空态正确。
10. 声音行从不打开右侧详情抽屉。

随后切到窄视口，确认工具栏纵向排列、卡片可展开、时间轴可操作、编辑与分页按钮可达，没有横向截断关键操作。保留桌面和窄视口截图作为验收证据。

- [ ] **步骤 4：回归其他资产页与认证状态**

在同一浏览器会话中切换“形象 / 文案 / 作品 / 创作”，确认现有列表、详情抽屉和步骤页仍可用；退出登录或使用无权限状态时，确认 `StudioAuthGate` 的加载、匿名跳转、403 和临时失败没有被声音组件绕过。

- [ ] **步骤 5：执行一轮独立代码审查**

完整读取并使用 `.codex/skills/requesting-code-review/SKILL.md`。审查范围固定为 `main...HEAD`，审查清单固定为：

- 参考页 1707–1906 行的可到达行为是否全部覆盖。
- `requestAnimationFrame`、pointer capture、timeout 是否在切换/卸载时清理。
- 不存在嵌套 button、无键盘入口、DOM 查询/修改或未清理全局监听器。
- 声音路由不调用详情抽屉，其他路由不因包装组件回归。
- 未引入真实音频、网络、后端或公共契约。
- CSS 是否使用参考值且没有污染创作步骤和详情抽屉的共享 `.voice-play`/`.voice-bars`。

只修复“必须修复”项；修复后只运行受影响测试和一次定向复核，禁止递归全量审查。

- [ ] **步骤 6：用完成前验证技能收口**

完整读取并使用 `.codex/skills/verification-before-completion/SKILL.md`，重新运行受修复影响的定向测试；若有代码修复，再运行一次 `npm test`、`npm run tsc`、`npm run biome:lint`、`npm run build`。最终报告必须列出：完成项、参考哈希、测试/类型/静态检查/构建证据、桌面/窄视口证据、审查结论、残余风险和明确未做范围。

若任务 6 没有代码变更，不创建空提交；若有必要修复，只暂存本计划文件并提交：

```powershell
git add -- 'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio'
git diff --cached --check
git commit -m 'fix(studio): close voice replica review findings'
```

---

## 完成定义

- 参考声音菜单全部可到达视觉和交互均已实现，且没有声音详情抽屉旧行为。
- 7 条数据、250ms 搜索、双筛选、6 条分页、空态、唯一播放、拖动跳转、逐字高亮和原位编辑均有自动测试。
- 现有上传 Modal、认证门禁、形象/文案/作品/创作路由无回归。
- 完整测试、TypeScript、Biome 和生产构建通过，浏览器桌面与窄视口验收通过。
- 独立审查必须修复项关闭，禁止修改文件哈希不变，工作树干净。
- 未接入真实音频、文件上传、后端、任务、额度或公共契约。
