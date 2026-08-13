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
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { Add-ValidationError "缺少文件：$Path" }
}

function Assert-ContainsAll {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string[]]$Terms)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
  $content = Read-Utf8File -Path $Path
  foreach ($term in $Terms) {
    if (-not $content.Contains($term)) { Add-ValidationError "$Path 缺少：$term" }
  }
}

function Assert-NotMatch {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string[]]$Patterns)
  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return }
  $content = Read-Utf8File -Path $Path
  foreach ($pattern in $Patterns) {
    if ($content -match $pattern) { Add-ValidationError "$Path 仍匹配禁用模式：$pattern" }
  }
}

$paths = @{
  Agents = Join-Path $projectRoot 'AGENTS.md'; Rules = Join-Path $projectRoot 'RULES.md'; Readme = Join-Path $projectRoot 'README.md'
  Architecture = Join-Path $docsRoot 'ARCHITECTURE.md'; ApiContract = Join-Path $docsRoot 'API_CONTRACT.md'
  BackendGuide = Join-Path $docsRoot 'BACKEND_GUIDE.md'; FrontendGuide = Join-Path $docsRoot 'FRONTEND_GUIDE.md'
  BackendStandards = Join-Path $docsRoot 'BACKEND_CODING_STANDARDS.md'; FrontendStandards = Join-Path $docsRoot 'FRONTEND_CODING_STANDARDS.md'
  AiRules = Join-Path $docsRoot 'AI_CODING_RULES.md'; DocumentMap = Join-Path $docsRoot 'DOCUMENT_MAP.md'
  LegacyStandards = Join-Path $docsRoot 'CODING_STANDARDS.md'
}

foreach ($path in $paths.Values) { if ($path -ne $paths.LegacyStandards) { Assert-FileExists -Path $path } }
if (Test-Path -LiteralPath $paths.LegacyStandards) { Add-ValidationError "旧规范仍存在：$($paths.LegacyStandards)" }

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
  'clientAccessPath', 'clientIpWhitelist', 'excludeParamNames', 'ruoyi-admin', 'ai-video-user-api', '/api/snail/chat/**'
))

Assert-ContainsAll -Path $paths.FrontendStandards -Terms ($ruleShape + @(
  '## 1. TypeScript 类型、命名和模块边界', '## 2. React 组件、Props 和组合设计', '## 3. Hooks、副作用和闭包安全',
  '## 4. 本地状态、服务端状态和请求管理', '## 5. Ant Design 与 ProComponents 组件选型',
  '## 6. 表单、表格、弹窗、抽屉和反馈', '## 7. 路由、菜单、前端权限和国际化',
  '## 8. 样式、主题、响应式和无障碍', '## 9. RuoYi API 前端使用边界', '## 10. 性能、错误边界和测试',
  'TypeScript 6', 'TypeScript 7', 'React 19', 'Umi Max 4', 'Ant Design 6', 'ProComponents 3',
  'React Query 5', 'Biome 2', 'Vitest 4', 'Oxfmt', 'Oxlint', 'ai-video-webapp',
  'ai-video-platform-ui', 'src/services', 'passWithNoTests', '/api/snail/chat/**'
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

foreach ($path in @($paths.Agents, $paths.Rules, $paths.Readme, $paths.DocumentMap)) {
  Assert-ContainsAll -Path $path -Terms @('docs/BACKEND_CODING_STANDARDS.md', 'docs/FRONTEND_CODING_STANDARDS.md')
}
Assert-ContainsAll -Path $paths.BackendGuide -Terms @('BACKEND_CODING_STANDARDS.md', 'ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md')
Assert-ContainsAll -Path $paths.FrontendGuide -Terms @('FRONTEND_CODING_STANDARDS.md', 'API_CONTRACT.md')
Assert-NotMatch -Path $paths.BackendGuide -Patterns @(
  'R<PageResult', 'PageResult\.build\(', 'PageQuery\(', '\borderByColumn\b', '@DataPermission',
  '@Transactional', '\bclientAccessPath\b', '\bclientIpWhitelist\b', '\bexcludeParamNames\b'
)
Assert-NotMatch -Path $paths.FrontendGuide -Patterns @(
  'R<PageResult', 'data\.records', 'data\.rows', '\bpageNum\b', '\bpageSize\b', '\borderByColumn\b',
  '\bisAsc\b', '\bclientid\b', 'content-language', '\bBigDecimal\b',
  'antd\s+(info|doc|demo|token|semantic|lint|doctor)\b', 'ProTable\s*/\s*ProForm\s*约定',
  '通用组件与状态', '枚举、类型和 API', '状态管理', 'Ant Design AI 辅助'
)
Assert-ContainsAll -Path $paths.AiRules -Terms @(
  'docs/BACKEND_CODING_STANDARDS.md', 'docs/FRONTEND_CODING_STANDARDS.md',
  'scripts/validate-development-standards.ps1', 'ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.Agents -Terms @(
  'docs/BACKEND_CODING_STANDARDS.md', 'docs/FRONTEND_CODING_STANDARDS.md',
  'scripts/validate-development-standards.ps1', 'ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md'
)
Assert-ContainsAll -Path $paths.Architecture -Terms @('BACKEND_CODING_STANDARDS.md', 'FRONTEND_CODING_STANDARDS.md')

$activeRootFiles = @($paths.Agents, $paths.Rules, $paths.Readme) | ForEach-Object { Get-Item -LiteralPath $_ }
$activeDocs = Get-ChildItem -LiteralPath $docsRoot -Recurse -Filter '*.md' -File |
  Where-Object { $_.FullName -notmatch '[\\/]docs[\\/]superpowers[\\/](specs|plans)[\\/]' }
$activeFiles = @($activeRootFiles) + @($activeDocs)
foreach ($file in $activeFiles) {
  $content = Read-Utf8File -Path $file.FullName
  if ($content -match '(?<![A-Za-z0-9_])(?:docs/)?CODING_STANDARDS\.md') { Add-ValidationError "活动文档仍引用旧规范：$($file.FullName)" }
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
      if (-not (Test-Path -LiteralPath $resolved)) { Add-ValidationError "本地链接不存在：$($file.FullName) -> $target" }
    } catch { Add-ValidationError "本地链接无效：$($file.FullName) -> $target" }
  }
}

if ($errors.Count -gt 0) {
  foreach ($validationError in $errors) { [Console]::Error.WriteLine("ERROR: $validationError") }
  exit 1
}
Write-Output 'DEVELOPMENT_STANDARDS_OK'
