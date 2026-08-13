# 前端编码规范

## 使用说明

本手册强制适用于 `ai-video-ui/ai-video-webapp` 与
`ai-video-ui/ai-video-platform-ui` 两个 React 包。包级目录、构建命令和检查工具
以各自工程的 `package.json`、锁文件及本地配置为准；不得把一个包的脚本或门禁
假定为另一个包已经具备。

技术基线以 major 版本表达：`ai-video-webapp` 使用 Node.js 22、TypeScript 7、
React 19、Umi Max 4、Ant Design 6、ProComponents 3、React Query 5、Biome 2
和 Vitest 4；`ai-video-platform-ui` 使用 Node.js 20、TypeScript 6、React 19、
Umi Max 4、Ant Design 6、ProComponents 3、React Query 5、Oxfmt 和 Oxlint。
精确版本始终以各包的 `package.json` 与锁文件为准。

- `【强制】`：违反会造成安全、契约、正确性或长期维护风险，必须遵守。
- `【推荐】`：原则上应遵守；偏离时在代码评审中说明理由和替代措施。
- `【参考】`：按场景采用的经验建议。

规则冲突时按以下优先级处理：安全和数据正确性 → 项目 API/领域契约与相邻已验证
代码 → React、Ant Design、ProComponents、Umi 官方规则 → 通用 TypeScript 规范。
线上字段、Header、分页和错误语义以 [API_CONTRACT.md](API_CONTRACT.md) 为唯一
来源；包结构、页面开发流程和 Electron bridge 以
[FRONTEND_GUIDE.md](FRONTEND_GUIDE.md) 为准。

当前 Biome、Vitest 与演示 API 协议仍有差距：`webapp` 的 `src/services` 被整体
排除，`noExplicitAny`、Hook 依赖和部分 a11y 规则处于关闭状态，Vitest 设置了
`passWithNoTests`；`app.tsx` 仍指向 Ant Design Pro 演示 API，
`requestErrorConfig.ts` 仍处理 `success/errorCode/errorMessage/showType` 演示协议。
这些事实不构成“已启用门禁”的声明；本文的强制规则仍然有效。

## 1. TypeScript 类型、命名和模块边界

【强制】保持 `strict` 与 `noImplicitReturns`，手写业务代码不得无理由使用 `any`、
非空断言或未经校验的双重类型断言。边界数据先用 `unknown` 接收，再用类型守卫或
解析函数收窄。

正例：

```ts
function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : '请求失败';
}
```

反例：

```ts
const message = (error: any).message;
const task = response as unknown as VideoTask;
```

说明：`any` 会跳过类型系统，双重断言会把未验证的线上数据伪装为可信数据；两者都
会把契约错误延迟到用户运行时。

检查方式：TypeScript `--noEmit`；对手写目录启用的 Biome/Oxlint；代码评审检查
`any`、`!` 与 `as unknown as` 的理由和收窄逻辑。

【强制】跨页面 DTO/VO、枚举、状态映射和请求/响应类型集中定义在领域类型、Service
或 adapter 中；组件内不得临时声明线上协议类型。页面不得散写 URL、状态字符串、
错误码或 envelope 解包逻辑。

【推荐】组件、Hook、Service 和类型按领域命名，例如 `VideoTaskTable`、
`useVideoTaskQuery`、`videoTaskService`、`VideoTask`；回调采用 `on<Action>` 形式。
生成代码目录必须明确隔离。手写 Service 与 RuoYi adapter 即使位于
`src/services`，也必须纳入类型检查、格式检查和测试范围。

## 2. React 组件、Props 和组合设计

【强制】组件只承担一项清晰职责。复杂页面拆为容器组件、展示组件和领域 Hook；Props
使用业务语义名称，事件回调采用 `on<Action>`。render 必须是纯函数：不修改
props/state，不在渲染期间发请求、写存储或触发通知。

【强制】可从 props、state 或查询结果计算出的值在渲染阶段派生，不用 Effect 镜像为
另一份 state。列表 key 使用稳定业务 ID；有可用 ID 时禁止使用随机数或数组下标。

正例：

```tsx
type VideoTaskListProps = {
  tasks: VideoTask[];
  onRetry: (taskId: string) => void;
};

function VideoTaskList({ tasks, onRetry }: VideoTaskListProps) {
  const failedTasks = tasks.filter((task) => task.status === 'FAILED');

  return failedTasks.map((task) => (
    <VideoTaskCard key={task.id} task={task} onRetry={onRetry} />
  ));
}
```

反例：

```tsx
function VideoTaskList({ tasks }: { tasks: VideoTask[] }) {
  const [failedTasks, setFailedTasks] = useState<VideoTask[]>([]);
  useEffect(() => setFailedTasks(tasks.filter((task) => task.status === 'FAILED')), [tasks]);
  return failedTasks.map((task, index) => <VideoTaskCard key={index} task={task} />);
}
```

说明：派生状态会产生不同步窗口；不稳定 key 会导致表单、焦点和局部 state 错配。

检查方式：组件测试覆盖重渲染和列表更新；React DevTools/Review 检查职责、key 和
render 副作用。

## 3. Hooks、副作用和闭包安全

【强制】Hook 只能在函数组件或自定义 Hook 顶层调用。Effect 仅用于与外部系统同步，
必须声明完整依赖；禁止为压制告警而无理由关闭依赖检查。请求、订阅、定时器和监听器
必须在 cleanup 中取消或释放，旧请求不得覆盖新查询。

正例：

```tsx
useEffect(() => {
  const controller = new AbortController();

  void loadResource(resourceId, controller.signal).catch((error: unknown) => {
    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      reportError(error);
    }
  });

  return () => controller.abort();
}, [resourceId, reportError]);
```

反例：

```tsx
useEffect(() => {
  void loadResource(resourceId).then(setResource);
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, []);
```

说明：完整依赖使 Effect 对当前输入负责；取消和 `AbortError` 分支避免组件卸载后写入
状态及把正常取消提示成业务失败。

检查方式：Hook/组件测试验证切换 ID 与卸载；React Doctor、Review 和启用后对应的
Hook 依赖检查共同覆盖。当前 Biome 关闭该规则是自动化缺口，不是例外。

【推荐】事件处理器读取最新状态时，优先通过依赖明确的回调、函数式 state 更新或领域
Hook 消除陈旧闭包，而不是依赖偶然的渲染顺序。

## 4. 本地状态、服务端状态和请求管理

【强制】页面瞬时 UI 状态（展开、输入、局部选择）使用组件状态；服务端数据、缓存和
失效由 React Query 或 Umi 请求层管理。不得把同一服务端真相复制为多份可独立修改的
本地 state。mutation 成功后按领域 query key 失效缓存或按约定刷新表格。

【强制】前端不自行裁定任务终态、额度结算、文件授权或数据归属。异步页面对加载、空
数据、搜索无结果、失败、401、403、取消、操作中、成功和失败都必须有确定行为；网络
取消不显示为普通业务错误。

正例：

```ts
const mutation = useMutation({
  mutationFn: videoTaskService.retry,
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['video-task'] });
  },
});
```

反例：

```ts
await retryTask(id);
setTasks((tasks) => tasks.map((task) => ({ ...task, status: 'SUCCESS' })));
```

说明：服务端是任务和额度的权威来源；本地猜测终态会在并发、重试和权限变化时产生错误
展示。

检查方式：测试验证 mutation 后刷新与失败分支；Review 检查本地真相副本及状态裁定。

## 5. Ant Design 与 ProComponents 组件选型

【强制】优先复用 Ant Design、ProComponents 和项目既有业务组件，禁止仅因方便而复制
已有交互组件。管理类页面优先采用 `ProTable`、`ProForm`、`ProDescriptions`、
`ProList`；生产工作台可用 Ant Design 基础组件与领域组件组合。

【强制】不得通过 DOM 查询、内部类名或宽泛全局 CSS 控制 Ant Design 内部状态。颜色、
间距、圆角和主题通过 `ConfigProvider` Token 与组件公开 API 实现。组件 API、Token 或
Semantic 语义不确定时，先查 Ant Design 官方资料或 `@ant-design/cli`，再写代码。

【推荐】将上传、任务状态、额度、空态和错误提示封装为领域组件/Hook，避免不同页面出现
相互矛盾的反馈文案和状态表现。

## 6. 表单、表格、弹窗、抽屉和反馈

【强制】表单在客户端做可即时判断的校验，并防止重复提交；提交期间显示 loading 并禁用
危险重复操作。只有服务端确认成功后才能关闭或重置表单。服务端字段错误应映射到对应
字段或给出统一、可理解的提示。

【强制】`ProTable` 的 `request` 只接收 adapter 转换后的 `{ data, total, success }`。
排序列使用“前端列键 → 后端允许字段”的显式映射；未知列不得透传。前端限制可选
`pageSize`，后端仍独立兜底。

正例：

```tsx
<ProTable<VideoTask>
  request={(params, sorter) => videoTaskService.page(params, sorter)}
/>
```

反例：

```tsx
<ProTable
  request={async (params) => {
    const response = await request(`/video/task?pageNum=${params.current}`);
    return { data: response.data.rows, total: response.data.total, success: true };
  }}
/>
```

说明：页面直连协议会复制分页、鉴权和错误处理，随接口演进而漂移；adapter 是唯一转换点。

检查方式：Service/adapter 单元测试及 ProTable 集成测试；Review 检查页面没有解包响应。

【强制】Modal/Drawer 的打开、提交、关闭、销毁和重置状态必须可预测。删除、覆盖、取消
任务、重试及额度消耗需要明确二次确认；上传显示类型、大小、数量、进度和失败原因；
成功提示只能在服务端确认后出现。

## 7. 路由、菜单、前端权限和国际化

【强制】路由和菜单由 Umi 配置维护；页面不得自造第二套路由。`access.ts` 只控制界面
可见性，不能替代后端授权、资源归属或数据权限检查。角色、权限码和状态映射集中维护，
不散写在组件中。

【强制】401 或业务 `code === 401` 由 adapter 一次性清理登录态并跳转；403 或业务
`code === 403` 显示无权限，不盲目跳登录。用户可见文案纳入既有国际化体系，
`content-language` 由请求层根据当前语言统一设置。

【强制】前端权限只作界面提示，任何隐藏菜单、禁用按钮或 `access.ts` 判断都不得被当作
授权结论；受保护操作仍必须由后端依据当前会话、权限和资源归属裁决。

正例：

```tsx
const canRetry = access.canRetryVideoTask;

return (
  <Button disabled={!canRetry} onClick={() => videoTaskService.retry(task.id)}>
    重试
  </Button>
);
```

反例：

```tsx
if (access.canRetryVideoTask) {
  // 前端显示过按钮即视为后端已授权
  await request(`/video/task/${task.id}/retry`);
}
```

说明：路由守卫和可见性仅改善体验；旧页面、篡改后的请求或权限变更仍可直接到达后端，
因此前端永远不是授权边界。

检查方式：页面测试覆盖无权限显示；接口集成测试覆盖后端拒绝；Review 检查权限判断没有
替代 adapter 与后端授权。

【强制】Electron 主进程只承载窗口和本地能力，不保存业务 Token、不调用业务 API；Web
端仅通过有 TypeScript 类型的受控 bridge 使用本地能力。

## 8. 样式、主题、响应式和无障碍

【强制】颜色、间距和主题使用 Token，避免硬编码；关键操作在项目支持视口下保持可达，
交互元素可键盘操作、焦点可见，并具有可访问名称或替代文本。不得仅用颜色表达状态。

正例：

```tsx
<Button aria-label={intl.formatMessage({ id: 'task.retry' })} onClick={onRetry}>
  {intl.formatMessage({ id: 'task.retry' })}
</Button>
```

反例：

```tsx
<div className="green-dot" onClick={onRetry}>●</div>
```

说明：仅依赖颜色或鼠标会排除键盘、屏幕阅读器和小屏用户；Token 能保持主题切换一致。

检查方式：键盘手工验收、Ant Design CLI、React Doctor、组件测试和 Review。当前 Biome
关闭的部分 a11y 规则属于自动化缺口，必须由上述检查显式承担。

## 9. RuoYi API 前端使用边界

【强制】依赖方向为 Page → 模块 Service → RuoYi adapter → Umi Request。页面只消费
领域结果和标准表格结果，不处理 Token、`clientid`、envelope、`rows` 或业务错误码。
线上 wire schema、认证 Header、分页、排序和错误码只链接并遵从
[API_CONTRACT.md](API_CONTRACT.md)，不得在此复制第二份权威契约。

【强制】普通 JSON、Blob、SSE 与 `/api/snail/chat/**` 分别使用清晰的 adapter；后者是
Snail AI Chat SDK 专用协议，不能让全局 JSON 拦截器按普通 `R<T>` 解包。ProTable 页面
只消费标准 adapter 结果。

【强制】adapter 将 HTTP 状态与 `R<T>` 的 `code/msg/data` 归一化：200 表示成功，401
清理登录态并一次性跳转，403 显示无权限；其他非成功业务码抛出包含 `code` 与 `msg` 的
标准业务异常。网络、超时、取消和 HTTP 5xx 与业务异常分开建模，禁止依据中文 `msg`
分支业务流程。

【强制】业务 ID 在前端统一为字符串，禁止对 ID 使用 `Number`、`parseInt` 或算术运算。
金额、额度等精度值按字符串建模；确需计算时使用十进制库或交给后端。

正例：

```ts
const task = await videoTaskService.get(String(taskId));
const amount = decimal(totalAmount).minus(consumedAmount);
```

反例：

```ts
const taskId = Number(params.id);
const rows = response.data.rows;
const headers = { Authorization: `Bearer ${token}` };
if (response.msg.includes('成功')) finish();
```

说明：adapter 集中处理 `code/msg/data` 与响应专用格式；页面拼接 Authorization、读取
`rows` 或判断中文 `msg` 会破坏认证与错误语义的一致性。

检查方式：adapter 契约测试覆盖普通 JSON、Blob、SSE、Chat、401、403、其他业务码和
分页转换；Review 搜索页面中的 `response.data.rows`、`Authorization` 和中文消息判断。

## 10. 性能、错误边界和测试

【推荐】性能优化以测量为依据，禁止无证据地滥用 memo。根级和关键页面设置可恢复的错误
边界；错误边界不得吞掉可诊断信息或让用户停在不可恢复页面。

【强制】Vitest/Testing Library 测试用户可观察行为。高风险流程至少覆盖成功、失败、
权限、取消和重复提交。测试排除必须最小化并说明原因；生成声明文件可明确排除，手写
业务目录不得整体排除。零测试执行不构成成功状态。

正例：

```ts
it('取消请求时不展示业务失败提示', async () => {
  render(<TaskPanel taskId="task-1" />);
  await userEvent.click(screen.getByRole('button', { name: '取消' }));
  expect(await screen.findByText('已取消')).toBeVisible();
  expect(screen.queryByText('请求失败')).not.toBeInTheDocument();
});
```

反例：

```ts
// 以临时方便为由，跳过整个手写业务目录
exclude: ['src/services/**']
```

说明：测试应保护用户路径与契约边界，而非只验证实现细节。排除整个手写目录会让回归无处
被发现。

检查方式：运行受影响包的测试、类型检查和构建；检查 Vitest include/exclude 与实际执行
数量；在 CI 中记录失败与零测试情况。

当前质量差距必须持续可见：`webapp` 的 `src/services` 整体排除、Biome 的
`noExplicitAny`/Hook 依赖/a11y 规则关闭、登录测试排除、`passWithNoTests`、演示 API
协议以及格式无写入检查缺口都不能被当作已解决。`platform-ui` 的 Oxfmt/Oxlint 规则和
脚本也应按其自身配置验证，不能借用 webapp 的 Biome/Vitest 结论。
