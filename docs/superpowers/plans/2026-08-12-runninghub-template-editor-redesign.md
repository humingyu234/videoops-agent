# RunningHub 模板全页编辑器实现计划

> **面向 AI 代理的工作者：** 使用 `subagent-driven-development` 执行本计划；每项生产代码先有失败测试，并按规格审查、代码质量审查顺序收口。

**目标：** 将运营端工作流模板长抽屉重构为 RunningHub 风格的全页新增、修改和查看编辑器，同时保持当前 API、保存恢复、权限和序列化行为。

**架构：** 列表页继续拥有数据加载与保存编排，通过本地页面状态打开专用 `WorkflowTemplateEditor`。编辑器拆成基本资料、RunningHub 资源、参数构建器、用户预览、输出运行设置和候选参数弹窗；表单模型仍是唯一 wire 转换边界。

**技术栈：** React 19、TypeScript、Ant Design 6、ProComponents 3、Vitest、Testing Library、Less。

---

## 文件职责

- 修改 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/index.tsx`：列表与全页编辑器切换、数据加载和两阶段保存。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowTemplateEditor.tsx`：全页表单状态与布局编排。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/TemplateBasicSection.tsx`：模板资料与运营设置。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/RunningHubResourceSection.tsx`：模式、账号、资源 ID 与读取参数入口。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/ParameterBuilder.tsx`：紧凑参数列表与参数编辑面板。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/UserInputPreview.tsx`：用户可见字段预览。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/OutputRuntimeSection.tsx`：输出规则、安全与运行设置。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/ParameterCandidateModal.tsx`：候选参数选择。
- 创建 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/index.less`：仅以 `.workflow-template-editor-*` 为前缀的局部布局。
- 删除 `ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowTemplateDrawer.tsx`：旧抽屉不再作为运行入口。
- 修改/创建工作流模板页面测试：覆盖页面切换、模式、参数、预览、只读和保存恢复。

### 任务 1：先建立全页编辑器红灯测试

**文件：**

- 修改：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowTemplateEditor.test.tsx`

- [ ] 将列表测试中的抽屉 mock 改为编辑器 mock，断言新增、修改、查看切换到编辑页，返回恢复列表。
- [ ] 写组件测试，断言页面存在 `基本信息`、`RunningHub 应用`、`用户输入参数`、`输出与运行设置` 和 `用户输入预览`。
- [ ] 写模式测试，初始只显示 Web App ID；切换 ComfyUI 工作流后只显示 Workflow ID，并触发清空参数。
- [ ] 写参数测试，断言参数以紧凑行呈现、技术映射默认收起，用户预览不含节点 ID。
- [ ] 运行 `pnpm.cmd test -- src/pages/aivideo/workflow-template/components/WorkflowTemplateEditor.test.tsx src/pages/aivideo/workflow-template/index.test.tsx`，预期因组件不存在或旧抽屉行为而失败。

### 任务 2：实现编辑器视觉骨架与分区

**文件：**

- 创建：`components/WorkflowTemplateEditor.tsx`
- 创建：`components/TemplateBasicSection.tsx`
- 创建：`components/RunningHubResourceSection.tsx`
- 创建：`components/UserInputPreview.tsx`
- 创建：`components/OutputRuntimeSection.tsx`
- 创建：`index.less`

- [ ] 使用一个 `Form<WorkflowTemplateFormValues>` 承载所有字段；打开时以 `buildWorkflowTemplateFormValues()` 初始化，返回时 `resetFields()`。
- [ ] 用两个可点击类型卡片表达 AI App 与 ComfyUI 工作流，使用现有 `runningHubExecutionModeOptions` 值，不新增模式。
- [ ] 构建双栏卡片布局和窄屏回落；右栏预览只消费 `Form.useWatch('parameters', form)` 的用户字段。
- [ ] 将密码、超时、最大输出大小与启用开关放入高级设置 Collapse；只读模式禁用字段并隐藏保存操作。
- [ ] 运行任务 1 测试，预期页面骨架、默认模式和只读断言通过。

### 任务 3：实现参数构建器与 RunningHub 候选读取

**文件：**

- 创建：`components/ParameterBuilder.tsx`
- 创建：`components/ParameterCandidateModal.tsx`
- 修改：`components/WorkflowTemplateEditor.tsx`
- 保持：`components/templateFormModel.ts`

- [ ] 参数主视图只显示顺序、用户名称、控件、必填和来源摘要，并提供编辑、删除、上移、下移。
- [ ] 参数编辑区维护 `inputKey/label/control/description/placeholder/optionsText/required`；高级映射维护 `nodeId/fieldName/remoteValueType`。
- [ ] 候选读取前验证账号、模式及当前资源 ID；沿用 `inspectRunningHubParameterCandidates`、`candidateKey` 与 `mergeRunningHubCandidates`，空结果和失败分别提示。
- [ ] 类型卡片或资源 ID 改变时调用同一 `clearExecutionParameters`，避免旧节点映射污染新资源。
- [ ] 运行任务 1 定向测试，预期模式切换、参数列表、预览和候选合并通过。

### 任务 4：接入列表页并保留保存恢复

**文件：**

- 修改：`index.tsx`
- 修改：`index.test.tsx`
- 删除：`components/WorkflowTemplateDrawer.tsx`

- [ ] 把 `drawerOpen/drawerReadonly/resetDrawerState` 改为 `editorOpen/editorReadonly/resetEditorState`，数据加载和权限判断不变。
- [ ] 编辑器打开时隐藏 ProTable；关闭或保存成功后恢复 ProTable，成功后 reload。
- [ ] 保持基础模板和执行配置两阶段保存。基础资料成功、配置失败后保存 `pendingConfigTemplateId`，错误留在编辑页，重试不再次创建/更新模板。
- [ ] 只读查看不加载 RunningHub 账号列表、不显示保存；详情加载失败不进入编辑页。
- [ ] 运行定向测试，预期原有权限、启停、删除、保存失败恢复和新全页切换全部通过。

### 任务 5：验证与单次独立审查

**文件：**

- 检查上述前端文件与本规格/计划。

- [ ] 运行 `pnpm.cmd test -- src/pages/aivideo/workflow-template/components/WorkflowTemplateEditor.test.tsx src/pages/aivideo/workflow-template/index.test.tsx src/api/aivideo/workflow-template/index.test.ts`。
- [ ] 运行 `pnpm.cmd lint`，确认类型与 lint 无错误；如仓库既有无关错误，记录精确证据并运行直接受影响文件的等价检查。
- [ ] 运行 `pnpm.cmd build:prod`，确认生产构建成功。
- [ ] 运行 `git diff --check`。
- [ ] 在 1440×900 浏览器检查新增、修改、AI App/Workflow 切换、读取参数、参数编辑、用户预览、只读和配置失败提示；窄屏确认单栏无横向溢出。
- [ ] 由一名未参与实现的子智能体依次完成规格合规和代码质量审查；只修复必须项并复跑直接受影响测试，不扩展范围。

## 自检

- 规格每项行为分别落在任务 2、3、4 和 5。
- 无后端、API、数据库、版本、故障切换或用户端任务。
- 所有新增生产组件均由任务 1 的失败测试驱动。
- 类型名、字段名和执行模式完全复用现有 `templateFormModel.ts`。

## 2026-08-12 精简修订执行项

1. 先调整编辑器测试：断言封面上传、富文本详情和访问安全存在；断言标签编号、封面素材编号、摘要、输出与运行设置、用户输入预览均不存在。
2. 编辑器改用项目共享 `ImageUpload` 和 `RichTextEditor`，抽离访问密码区，删除输出/运行区和预览区。
3. 表单模型不再向 UI 暴露输出类型、数量、大小、超时和启用字段；保存时从隐藏兼容字段取得已有内部值，或用固定默认值。
4. 为 RunningHub Workflow 参数检查客户端增加非敏感失败原因测试与实现，然后使用本地服务复测实际模板。
5. 仅运行受影响的前后端测试、类型检查、构建、`git diff --check` 和一次浏览器验收；不做数据库迁移或扩展重构。
