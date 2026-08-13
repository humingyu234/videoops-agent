# 模板驱动的长口播台词生成实现计划

> **已被新文案计划替代，禁止执行冲突任务。** 文案知识、生成、版本、确认和用户文案库改为执行 `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md` 与 `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md`。

> **面向 AI 代理的工作者：** 使用 subagent-driven-development 或 executing-plans，按复选框逐步执行。

**目标：** 让 GLM-5.2 根据 T01–T11 自动生成 45–120 秒的完整口播台词，稳定返回并允许无限制编辑。

**架构：** 后端新增模板目录读取与确定性匹配器；GLM 只返回正文，服务端构造严格 `ScriptResult`；通用模型调用对无效结构重试一次。前端移除台词输入框字符限制，并提供明确的重新生成入口。

**技术栈：** FastAPI、Pydantic、httpx、React、TypeScript、Ant Design、pytest、Vitest。

---

### 任务 1：长台词契约和服务端脚本构造

**文件：**
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_contracts.py`
- 创建：`digital-human-studio/apps/api/src/dh_api/services/demo_script_builder.py`
- 测试：`digital-human-studio/tests/demo/test_demo_contracts.py`
- 测试：`digital-human-studio/tests/demo/test_demo_script_builder.py`

- [ ] 写入超过 120 字仍合法、长句按 120 字以内连续切分的失败测试。
- [ ] 运行目标 pytest，确认失败来自现有 `max_length=120`。
- [ ] 移除各脚本正文的 120 字上限，实现服务端 ID、哈希、时长和分段构造。
- [ ] 再次运行目标 pytest，确认通过。

### 任务 2：模板自动匹配与 GLM 异常重试

**文件：**
- 创建：`digital-human-studio/apps/api/src/dh_api/services/demo_copy_templates.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_bigmodel.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_ai_store.py`
- 测试：`digital-human-studio/tests/demo/test_demo_bigmodel.py`
- 测试：`digital-human-studio/tests/demo/test_demo_copy_templates.py`

- [ ] 写入产品口播匹配 T07、模板片段进入请求、正文由服务端构造以及首个非法响应重试的失败测试。
- [ ] 运行目标 pytest 并记录失败。
- [ ] 实现可配置模板目录、白名单文件映射、模板片段提取和场景匹配。
- [ ] 将 GLM 脚本输出缩减为 `scriptText`，请求失败结构只自动重试一次。
- [ ] 运行目标 pytest，确认模板、重试、隐私和错误映射全部通过。

### 任务 3：确认台词页面取消限制并支持重新生成

**文件：**
- 修改：`digital-human-studio/apps/web/src/App.tsx`
- 修改：`digital-human-studio/apps/web/src/App.test.tsx`

- [ ] 写入文本框无 `maxlength`/计数器、点击“重新生成”返回需求页的失败测试。
- [ ] 运行目标 Vitest，确认失败。
- [ ] 移除 `maxLength` 与 `showCount`，增加重新生成入口且保留需求流程语义。
- [ ] 运行 Web 测试、类型检查和构建。

### 任务 4：真实 GLM 联调

**文件：**
- 验证：`digital-human-studio/apps/api/src/dh_api/main.py`
- 验证：`digital-human-studio/apps/web/vite.config.ts`

- [ ] 重启本地 API，默认从两端 `application-dev.yml` 加载共享开发 Key，环境变量可选覆盖。
- [ ] 真实提交“电商零售 + 产品口播”问卷并生成台词。
- [ ] 验证台词超过 120 字、模板为 T07 结构、页面正常显示且无字符上限。
- [ ] 运行相关后端测试与全部 Web 测试，记录实际结果。
