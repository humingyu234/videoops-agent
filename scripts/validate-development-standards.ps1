[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$docsRoot = Join-Path $projectRoot 'docs'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError {
  param([Parameter(Mandatory)][string]$Message)
  [void]$script:errors.Add($Message)
}

function Read-Utf8File {
  param([Parameter(Mandatory)][string]$Path)
  Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
}

function Assert-FileExists {
  param([Parameter(Mandatory)][string]$Path)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    Add-ValidationError "缺少文件：$Path"
  }
}

function Assert-ContainsAll {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string[]]$Terms)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
  $content = Read-Utf8File -Path $Path
  foreach ($term in $Terms) {
    if (-not $content.Contains($term)) {
      Add-ValidationError "$Path 缺少：$term"
    }
  }
}

function Assert-NotMatch {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string[]]$Patterns)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
  $content = Read-Utf8File -Path $Path
  foreach ($pattern in $Patterns) {
    if ($content -match $pattern) {
      Add-ValidationError "$Path 仍匹配禁用模式：$pattern"
    }
  }
}

$paths = @{
  Agents = Join-Path $projectRoot 'AGENTS.md'
  Claude = Join-Path $projectRoot 'CLAUDE.md'
  Rules = Join-Path $projectRoot 'RULES.md'
  Readme = Join-Path $projectRoot 'README.md'
  Architecture = Join-Path $docsRoot 'ARCHITECTURE.md'
  ApiContract = Join-Path $docsRoot 'API_CONTRACT.md'
  BackendGuide = Join-Path $docsRoot 'BACKEND_GUIDE.md'
  FrontendGuide = Join-Path $docsRoot 'FRONTEND_GUIDE.md'
  BackendStandards = Join-Path $docsRoot 'BACKEND_CODING_STANDARDS.md'
  FrontendStandards = Join-Path $docsRoot 'FRONTEND_CODING_STANDARDS.md'
  AiRules = Join-Path $docsRoot 'AI_CODING_RULES.md'
  DocumentMap = Join-Path $docsRoot 'DOCUMENT_MAP.md'
  Project = Join-Path $docsRoot 'PROJECT.md'
  Decisions = Join-Path $docsRoot 'DECISIONS.md'
  Plan = Join-Path $docsRoot 'PLAN.md'
  Execution = Join-Path $docsRoot 'EXECUTION.md'
  Baseline = Join-Path $docsRoot 'BASELINE.md'
  WebAgents = Join-Path $projectRoot 'ai-video-ui\ai-video-webapp\AGENTS.md'
  WebClaude = Join-Path $projectRoot 'ai-video-ui\ai-video-webapp\CLAUDE.md'
  ClaudeAntdSkill = Join-Path $projectRoot 'ai-video-ui\ai-video-webapp\.claude\skills\antd\SKILL.md'
  ClaudeProUpgradeSkill = Join-Path $projectRoot 'ai-video-ui\ai-video-webapp\.claude\skills\pro-upgrade\SKILL.md'
  ApiAgents = Join-Path $projectRoot 'ai-video-api\AGENTS.md'
  LegacyStandards = Join-Path $docsRoot 'CODING_STANDARDS.md'
}

$requiredPaths = @(
  $paths.Agents, $paths.Claude, $paths.Rules, $paths.Readme, $paths.Architecture,
  $paths.ApiContract, $paths.BackendGuide, $paths.FrontendGuide,
  $paths.BackendStandards, $paths.FrontendStandards, $paths.AiRules,
  $paths.DocumentMap, $paths.Project, $paths.Decisions, $paths.Plan,
  $paths.Execution, $paths.Baseline, $paths.WebAgents, $paths.WebClaude,
  $paths.ClaudeAntdSkill, $paths.ClaudeProUpgradeSkill, $paths.ApiAgents
)
foreach ($path in $requiredPaths) { Assert-FileExists -Path $path }
if (Test-Path -LiteralPath $paths.LegacyStandards) {
  Add-ValidationError "旧规范仍存在：$($paths.LegacyStandards)"
}

$ruleShape = @('【强制】', '【推荐】', '【参考】', '正例：', '反例：', '检查方式：')
Assert-ContainsAll -Path $paths.BackendStandards -Terms ($ruleShape + @(
  '## 1. Java 基础、命名和代码格式', '## 2. 面向对象、集合、日期和精度', '## 3. 异常、日志和敏感数据',
  '## 4. Controller、Service、Mapper 分层', '## 5. Entity、BO、VO、跨模块 DTO 与 MapStruct Plus',
  '## 6. MyBatis-Plus、MPJ、查询、分页和排序', '## 7. 事务、并发、幂等和外部副作用',
  '## 8. Sa-Token、权限码、数据归属和数据权限', '## 9. Redis、Spring Cache、Redisson 和 Lock4j',
  '## 10. SnailJob、事件和异步任务', '## 11. 文件、OSS、导入和导出',
  '## 12. Spring AI、Snail AI 和外部服务适配', '## 13. 配置、密钥和双启动应用边界',
  '## 14. 单元、Web、数据访问和集成测试', 'Java 21', 'Spring Boot 4.1.0',
  'RuoYi-Vue-Plus 6.0.0-BETA', 'BaseEntity', 'R.ok(String)', 'PageResult.build()',
  'PageQuery(Integer pageSize, Integer pageNum)', 'orderByColumn', '@DataPermission',
  'clientAccessPath', 'clientIpWhitelist', 'excludeParamNames', 'ruoyi-admin',
  'ai-video-user-api', '/api/snail/chat/**'
))

Assert-ContainsAll -Path $paths.FrontendStandards -Terms ($ruleShape + @(
  '## 1. TypeScript 类型、命名和模块边界', '## 2. React 组件、Props 和组合设计',
  '## 3. Hooks、副作用和闭包安全', '## 4. 本地状态、服务端状态和请求管理',
  '## 5. Ant Design 与 ProComponents 组件选型', '## 6. 表单、表格、弹窗、抽屉和反馈',
  '## 7. 路由、菜单、前端权限和国际化', '## 8. 样式、主题、响应式和无障碍',
  '## 9. RuoYi API 前端使用边界', '## 10. 性能、错误边界和测试',
  'TypeScript 6', 'TypeScript 7', 'React 19', 'Umi Max 4', 'Ant Design 6',
  'ProComponents 3', 'React Query 5', 'Biome 2', 'Vitest 4', 'Oxfmt',
  'Oxlint', 'ai-video-webapp', 'ai-video-platform-ui', 'src/services',
  'passWithNoTests', '/api/snail/chat/**'
))

Assert-ContainsAll -Path $paths.ApiContract -Terms @(
  '"rows"', '"total"', 'orderByColumn', 'isAsc', 'page.rows ?? []', 'Authorization',
  'clientid', 'content-language', '当前国际化语言', '携带 `code`/`msg` 的标准业务异常',
  'clientAccessPath', 'clientIpWhitelist', 'BigDecimal', 'Blob', 'SSE', '/api/snail/chat/**'
)
Assert-NotMatch -Path $paths.ApiContract -Patterns @(
  '"records"\s*:', 'data\.records', '禁止返回\s*`?rows`?', '\bsortField\b', '\bsortOrder\b',
  'PageResult\.build\(', 'R\.ok\(String\)'
)

Assert-ContainsAll -Path $paths.BackendGuide -Terms @(
  'BACKEND_CODING_STANDARDS.md', '.agents/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.FrontendGuide -Terms @('FRONTEND_CODING_STANDARDS.md', 'API_CONTRACT.md')
Assert-ContainsAll -Path $paths.AiRules -Terms @(
  'docs/BACKEND_CODING_STANDARDS.md', 'docs/FRONTEND_CODING_STANDARDS.md',
  'scripts/validate-development-standards.ps1', '.agents/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.Architecture -Terms @('BACKEND_CODING_STANDARDS.md', 'FRONTEND_CODING_STANDARDS.md')

Assert-ContainsAll -Path $paths.Agents -Terms @(
  'docs/EXECUTION.md', 'docs/BACKEND_CODING_STANDARDS.md',
  'docs/FRONTEND_CODING_STANDARDS.md', 'scripts/validate-development-standards.ps1',
  '.agents/skills/ruoyi-plus-ai-coding/SKILL.md', '.agents/skills/antd/SKILL.md'
)
Assert-NotMatch -Path $paths.Agents -Patterns @(
  'docs/tasks/', 'ai-video-api/\.codex/skills/', '## Superpowers 项目模板'
)
Assert-NotMatch -Path $paths.Rules -Patterns @('docs/tasks/', 'ai-video-api/\.codex/skills/')

if (Test-Path -LiteralPath $paths.Claude -PathType Leaf) {
  $rootClaude = (Read-Utf8File -Path $paths.Claude).Trim()
  if ($rootClaude -ne '@AGENTS.md') {
    Add-ValidationError '根 CLAUDE.md 必须是仅导入 @AGENTS.md 的薄入口'
  }
}
if (Test-Path -LiteralPath $paths.WebClaude -PathType Leaf) {
  $webClaude = ((Read-Utf8File -Path $paths.WebClaude).Trim() -replace "`r`n", "`n")
  if ($webClaude -ne "@../../AGENTS.md`n@AGENTS.md") {
    Add-ValidationError 'Webapp CLAUDE.md 必须只导入根与局部 AGENTS.md'
  }
}
if (Test-Path -LiteralPath $paths.WebAgents -PathType Leaf) {
  $webAgents = (Read-Utf8File -Path $paths.WebAgents).Trim()
  if ($webAgents -eq 'CLAUDE.md' -or $webAgents.Length -lt 80) {
    Add-ValidationError 'Webapp AGENTS.md 仍是无效裸转发或缺少局部规则'
  }
}

$skillRoot = Join-Path $projectRoot '.agents\skills'
$requiredSkills = @('brainstorming', 'writing-plans', 'ruoyi-plus-ai-coding', 'frontend-crud-coding', 'antd')
foreach ($skillName in $requiredSkills) {
  $skillPath = Join-Path (Join-Path $skillRoot $skillName) 'SKILL.md'
  Assert-FileExists -Path $skillPath
  Assert-ContainsAll -Path $skillPath -Terms @('---', 'name:', 'description:')
  Assert-NotMatch -Path $skillPath -Patterns @('\b[A-Za-z]:\\')
}
Assert-FileExists -Path (Join-Path $skillRoot 'brainstorming\visual-companion.md')
$codexAntdSkill = Join-Path $skillRoot 'antd\SKILL.md'
if ((Test-Path -LiteralPath $codexAntdSkill -PathType Leaf) -and
    (Test-Path -LiteralPath $paths.ClaudeAntdSkill -PathType Leaf)) {
  $codexAntdHash = (Get-FileHash -LiteralPath $codexAntdSkill -Algorithm SHA256).Hash
  $claudeAntdHash = (Get-FileHash -LiteralPath $paths.ClaudeAntdSkill -Algorithm SHA256).Hash
  if ($codexAntdHash -ne $claudeAntdHash) {
    Add-ValidationError 'Codex 与 Claude 的 antd Skill 镜像已漂移'
  }
}
Assert-ContainsAll -Path $paths.ClaudeProUpgradeSkill -Terms @(
  'Use only when the user explicitly asks',
  'Do not use for ordinary business migrations'
)
if (Test-Path -LiteralPath (Join-Path $projectRoot '.codex\skills')) {
  $legacySkillFiles = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot '.codex\skills') -Recurse -File -Force)
  if ($legacySkillFiles.Count -gt 0) {
    Add-ValidationError "根 .codex/skills 仍含 $($legacySkillFiles.Count) 个文件，应迁移到 .agents/skills 或删除"
  }
}

Assert-ContainsAll -Path $paths.Plan -Terms @('T0', 'T1', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7')
Assert-NotMatch -Path $paths.Plan -Patterns @('docs/tasks/')
Assert-ContainsAll -Path $paths.Execution -Terms @(
  '当前阶段', '当前详细计划', '状态', '证据索引', '下一条准确动作', 'PASS', 'FAIL', 'NOT_RUN'
)
Assert-NotMatch -Path $paths.Execution -Patterns @(
  'docs/tasks/', '完成后重新记录', '稍后补充', 'TODO：更新当前'
)

if (Test-Path -LiteralPath $paths.Execution -PathType Leaf) {
  $executionContent = Read-Utf8File -Path $paths.Execution
  $nextActionCount = [regex]::Matches($executionContent, '下一条准确动作').Count
  if ($nextActionCount -ne 1) {
    Add-ValidationError "$($paths.Execution) 必须恰好包含一个“下一条准确动作”，当前为 $nextActionCount 个"
  }

  $currentStageMatch = [regex]::Match($executionContent, '(?m)^\|\s*当前阶段\s*\|\s*(?<stage>T[0-9]+)(?:\s*[:：][^|]*)?\|\s*$')
  $executionStatusMatch = [regex]::Match($executionContent, '(?m)^\|\s*状态\s*\|\s*`(?<status>[A-Z_]+)`\s*\|\s*$')
  $currentPlanMatches = [regex]::Matches(
    $executionContent,
    '(?m)^\|\s*当前详细计划\s*\|\s*`(?<plan>docs/superpowers/plans/[A-Za-z0-9._/-]+\.md)`[^|]*\|\s*$'
  )
  $allPlanReferences = [regex]::Matches(
    $executionContent,
    'docs/superpowers/plans/[A-Za-z0-9._/-]+\.md'
  )
  if (-not $currentStageMatch.Success) { Add-ValidationError "$($paths.Execution) 未声明当前 Tn 阶段" }
  $allowedExecutionStatuses = @(
    'NOT_STARTED', 'IN_PROGRESS', 'VERIFYING', 'BLOCKED',
    'DONE', 'NEEDS_REVALIDATION', 'DEFERRED', 'PAUSED'
  )
  if (-not $executionStatusMatch.Success -or
      $allowedExecutionStatuses -notcontains $executionStatusMatch.Groups['status'].Value) {
    Add-ValidationError "$($paths.Execution) 未声明允许的当前状态"
  }
  if ($currentPlanMatches.Count -ne 1 -or $allPlanReferences.Count -ne 1) {
    Add-ValidationError "$($paths.Execution) 必须在“当前详细计划”字段中唯一绑定一个 runbook"
  }
  else {
    $currentPlanRelativePath = $currentPlanMatches[0].Groups['plan'].Value.Replace('/', '\')
    $currentPlanPath = Join-Path $projectRoot $currentPlanRelativePath
    Assert-FileExists -Path $currentPlanPath
    Assert-ContainsAll -Path $currentPlanPath -Terms @('目标', '非目标', '验收', '停止条件')
    if ($currentStageMatch.Success -and $currentStageMatch.Groups['stage'].Value -eq 'T1') {
      foreach ($index in 0..9) {
        $stepId = "T1.$index"
        Assert-ContainsAll -Path $paths.Execution -Terms @($stepId)
        Assert-ContainsAll -Path $currentPlanPath -Terms @($stepId)
      }
    }
  }
}

$activeRootFiles = @($paths.Agents, $paths.Rules, $paths.Readme) |
  ForEach-Object { Get-Item -LiteralPath $_ }
$activeDocs = Get-ChildItem -LiteralPath $docsRoot -Recurse -Filter '*.md' -File |
  Where-Object { $_.FullName -notmatch '[\\/]docs[\\/]superpowers[\\/](specs|plans)[\\/]' }
$activeFiles = @($activeRootFiles) + @($activeDocs)
foreach ($file in $activeFiles) {
  $content = Read-Utf8File -Path $file.FullName
  if ($content -match '(?<![A-Za-z0-9_])(?:docs/)?CODING_STANDARDS\.md') {
    Add-ValidationError "活动文档仍引用旧规范：$($file.FullName)"
  }
  if ($content -match 'docs/tasks/') {
    Add-ValidationError "活动文档仍引用已删除阶段卡：$($file.FullName)"
  }
  if ($content -match '(?:^|[\\/])\.codex[\\/]skills[\\/]') {
    Add-ValidationError "活动文档仍引用旧 .codex Skill 路径：$($file.FullName)"
  }
}

$linkPattern = '(?<!\!)\[[^\]]+\]\((?<target>[^)]+)\)'
foreach ($file in $activeFiles) {
  $content = Read-Utf8File -Path $file.FullName
  foreach ($match in [regex]::Matches($content, $linkPattern)) {
    $target = $match.Groups['target'].Value.Trim().Trim('<', '>')
    if ($target -match '^(https?://|mailto:|file:|#)') { continue }
    $pathPart = ($target -split '#', 2)[0]
    if ([string]::IsNullOrWhiteSpace($pathPart)) { continue }
    try {
      $resolved = [System.IO.Path]::GetFullPath((Join-Path $file.DirectoryName $pathPart))
      if (-not (Test-Path -LiteralPath $resolved)) {
        Add-ValidationError "本地链接不存在：$($file.FullName) -> $target"
      }
    }
    catch {
      Add-ValidationError "本地链接无效：$($file.FullName) -> $target"
    }
  }
}

if ($errors.Count -gt 0) {
  foreach ($validationError in $errors) {
    [Console]::Error.WriteLine("ERROR: $validationError")
  }
  exit 1
}

Write-Output 'DEVELOPMENT_STANDARDS_OK'
