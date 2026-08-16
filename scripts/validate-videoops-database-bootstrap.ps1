[CmdletBinding()]
param(
    [string]$ManifestPath,
    [string]$RepositoryRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$expectedStepIds = @('001', '010', '020', '030', '040', '050', '060', '070', '080', '090', '100', '110', '120', '900')
$bootstrapRelativeRoot = 'docs/sql/videoops-agent/mysql'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-ValidationError {
    param([Parameter(Mandatory)][string]$Message)
    [void]$script:errors.Add($Message)
}

function Get-PropertyValue {
    param(
        [AllowNull()][object]$InputObject,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Context
    )

    if ($null -eq $InputObject) {
        Add-ValidationError "$Context 缺少字段：$Name"
        return $null
    }
    $property = $InputObject.PSObject.Properties[$Name]
    if ($null -eq $property) {
        Add-ValidationError "$Context 缺少字段：$Name"
        return $null
    }
    return $property.Value
}

function ConvertTo-NormalizedSqlCode {
    param([Parameter(Mandatory)][string]$Sql)

    # MySQL version comments are executable, so unwrap them before removing ordinary comments.
    $code = [regex]::Replace(
        $Sql,
        '(?s)/\*!\d*\s*(?<body>.*?)\*/',
        { param($match) $match.Groups['body'].Value }
    )
    $code = [regex]::Replace($code, '(?s)/\*(?!\!).*?\*/', ' ')
    $code = [regex]::Replace($code, "'(?:''|\\.|[^'])*'", "''")
    $code = [regex]::Replace($code, '"(?:""|\\.|[^"])*"', '""')
    $code = [regex]::Replace($code, '(?m)--[^\r\n]*', ' ')
    $code = [regex]::Replace($code, '(?m)#[^\r\n]*', ' ')
    return $code
}

function Get-SqlStatements {
    param([Parameter(Mandatory)][string]$NormalizedSql)

    return @($NormalizedSql -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Get-InsertTarget {
    param([Parameter(Mandatory)][string]$Statement)

    $match = [regex]::Match(
        $Statement,
        '(?is)^INSERT(?:\s+IGNORE)?\s+INTO\s+`?(?<table>[A-Za-z_][A-Za-z0-9_]*)`?\b'
    )
    if (-not $match.Success) { return $null }
    return $match.Groups['table'].Value
}

function Test-ForbiddenSeedTable {
    param([Parameter(Mandatory)][string]$Table)

    return $Table -match '(?i)^(?:sys_oss(?:_config)?|.*(?:^|_)(?:asset|portrait|voice|task|job|provider|runninghub|oss)(?:_|$).*)$'
}

function Test-SqlStep {
    param(
        [Parameter(Mandatory)][string]$StepId,
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string[]]$DeclaredTables,
        [Parameter(Mandatory)][string[]]$AllowedSeedTables,
        [Parameter(Mandatory)][AllowEmptyCollection()]
        [System.Collections.Generic.HashSet[string]]$ObservedSeedTables
    )

    $sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $Path
    $code = ConvertTo-NormalizedSqlCode -Sql $sql
    $statements = Get-SqlStatements -NormalizedSql $code
    $displayName = [IO.Path]::GetFileName($Path)

    if ($sql -match '(?i)(?<![A-Za-z0-9_])ai_video(?![A-Za-z0-9_])') {
        Add-ValidationError "$displayName 引用了禁止的公司库标识"
    }
    if ($sql -match '(?is)(?:\A|;|[''"])\s*(?:USE\b|(?:CREATE|DROP)\s+DATABASE\b|GRANT\b|REVOKE\b|(?:CREATE|ALTER)\s+USER\b|SET\s+PASSWORD\b)') {
        Add-ValidationError "$displayName 包含禁止的数据库或账号级语句"
    }
    if ($code -match '(?is)(?:\A|;)\s*(?:USE\b|(?:CREATE|DROP)\s+DATABASE\b|GRANT\b|REVOKE\b|(?:CREATE|ALTER)\s+USER\b|SET\s+PASSWORD\b)') {
        Add-ValidationError "$displayName 包含禁止的数据库或账号级可执行语句"
    }
    $qualifiedTablePattern = '(?ix)\b(?<context>FROM|JOIN|INTO|UPDATE|TABLE|REFERENCES)\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(?<schema>[A-Za-z_][A-Za-z0-9_]*)`?\s*\.\s*`?[A-Za-z_][A-Za-z0-9_]*`?'
    $hasForbiddenQualifiedTable = $false
    foreach ($qualifiedSource in @($code, $sql)) {
        foreach ($qualifiedMatch in [regex]::Matches($qualifiedSource, $qualifiedTablePattern)) {
            $context = $qualifiedMatch.Groups['context'].Value
            $schema = $qualifiedMatch.Groups['schema'].Value
            if ($schema -ieq 'information_schema' -and $context -match '^(?i:FROM|JOIN)$') {
                continue
            }
            $hasForbiddenQualifiedTable = $true
            break
        }
        if ($hasForbiddenQualifiedTable) { break }
    }
    if ($hasForbiddenQualifiedTable) {
        Add-ValidationError "$displayName 包含跨库限定引用"
    }
    if ($sql -match '(?ix)\b(?:CREATE|ALTER|DROP)\s+(?:UNIQUE\s+)?INDEX\b.*?\bON\s+`?[A-Za-z_][A-Za-z0-9_]*`?\s*\.\s*`?[A-Za-z_][A-Za-z0-9_]*`?') {
        Add-ValidationError "$displayName 包含跨库限定索引引用"
    }

    if ($StepId -ne '900') {
        if ($sql -notmatch "(?i)DATABASE\(\)\s*=\s*'videoops_agent_dev'") {
            Add-ValidationError "$displayName 缺少精确目标 schema fail-closed guard"
        }
        foreach ($statement in $statements) {
            if ($statement -match '(?is)^(?:INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP\s+TABLE)\b') {
                Add-ValidationError "$displayName 的 schema 步骤包含禁止的数据或破坏性语句"
                break
            }
        }
        if ($sql -match '(?is)(?:\A|;|[''"])\s*(?:INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP\s+TABLE)\b') {
            Add-ValidationError "$displayName 包含动态或字面形式的禁止 schema 语句"
        }

        $actualDdlTables = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($ddlMatch in [regex]::Matches(
            $sql,
            '(?is)\b(?:CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?|ALTER\s+TABLE)\s+`?(?<table>[A-Za-z_][A-Za-z0-9_]*)`?'
        )) {
            $table = $ddlMatch.Groups['table'].Value
            if ($table -ine 'videoops_seed_guard') {
                [void]$actualDdlTables.Add($table)
            }
        }
        foreach ($actualTable in $actualDdlTables) {
            if ($DeclaredTables -notcontains $actualTable) {
                Add-ValidationError "$displayName 的 DDL 目标未在 manifest.tables 中声明：$actualTable"
            }
        }
        foreach ($declaredTable in $DeclaredTables) {
            if (-not $actualDdlTables.Contains($declaredTable)) {
                Add-ValidationError "$displayName 的 manifest.tables 没有对应 CREATE/ALTER：$declaredTable"
            }
        }
        return
    }

    if ($code -notmatch '(?i)(?<![A-Za-z0-9_])@videoops_creator_password_hash(?![A-Za-z0-9_])') {
        Add-ValidationError "$displayName 必须使用 @videoops_creator_password_hash"
    }

    foreach ($statement in $statements) {
        $insertTarget = Get-InsertTarget -Statement $statement
        if ($null -ne $insertTarget) {
            if (Test-ForbiddenSeedTable -Table $insertTarget) {
                Add-ValidationError "$displayName 禁止写入表：$insertTarget"
                continue
            }
            if ($insertTarget -ieq 'videoops_seed_guard') {
                if ($statement -notmatch '(?is)^INSERT\s+INTO\b') {
                    Add-ValidationError "$displayName 的 videoops_seed_guard 必须使用普通 INSERT 触发 CHECK"
                }
                continue
            }
            if ($AllowedSeedTables -notcontains $insertTarget) {
                Add-ValidationError "$displayName 写入了 manifest 白名单外的表：$insertTarget"
                continue
            }
            if ($statement -notmatch '(?is)^INSERT\s+IGNORE\s+INTO\b') {
                Add-ValidationError "$displayName 的白名单业务表必须使用 INSERT IGNORE：$insertTarget"
                continue
            }
            [void]$ObservedSeedTables.Add($insertTarget)
            continue
        }

        if ($statement -match '(?is)^CREATE\s+TEMPORARY\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?videoops_seed_guard`?\b') {
            continue
        }
        if ($statement -match '(?is)^DROP\s+TEMPORARY\s+TABLE\s+(?:IF\s+EXISTS\s+)?`?videoops_seed_guard`?\s*$') {
            continue
        }
        if ($statement -match '(?is)^(?:CREATE|ALTER|DROP|TRUNCATE|RENAME)\b') {
            Add-ValidationError "$displayName 包含禁止的持久 DDL"
            continue
        }
        if ($statement -match '(?is)^(?:UPDATE|DELETE|REPLACE|LOAD\s+DATA|CALL)\b' -or
            $statement -match '(?is)\bON\s+DUPLICATE\s+KEY\s+UPDATE\b') {
            Add-ValidationError "$displayName 包含禁止的 seed 写法"
        }
    }

    if ($sql -match '(?is)(?:\A|;|[''"])\s*(?:UPDATE|DELETE|REPLACE|LOAD\s+DATA|CALL|ALTER\s+TABLE|CREATE\s+TABLE|DROP\s+TABLE|TRUNCATE|RENAME)\b' -or
        $sql -match '(?is)\bON\s+DUPLICATE\s+KEY\s+UPDATE\b') {
        Add-ValidationError "$displayName 包含动态或字面形式的禁止 seed 语句"
    }

    if ($sql -match '(?i)\$2[aby]\$[0-3][0-9]\$[./A-Za-z0-9]{53}|\{bcrypt\}|literal[-_ ]?password') {
        Add-ValidationError "$displayName 包含疑似字面口令"
    }
}

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = Split-Path -Parent $PSScriptRoot
}
$RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
$expectedManifestPath = Join-Path $RepositoryRoot "$bootstrapRelativeRoot/bootstrap-manifest.json"
if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = $expectedManifestPath
}
$ManifestPath = [IO.Path]::GetFullPath($ManifestPath)

if (-not $ManifestPath.Equals($expectedManifestPath, [StringComparison]::OrdinalIgnoreCase)) {
    Add-ValidationError 'manifest 必须位于固定的 VideoOps bootstrap 目录'
}
if (-not (Test-Path -LiteralPath $ManifestPath -PathType Leaf)) {
    Add-ValidationError "缺少 bootstrap manifest：$ManifestPath"
}

$manifest = $null
if ($errors.Count -eq 0) {
    try {
        $manifest = Get-Content -Raw -Encoding UTF8 -LiteralPath $ManifestPath | ConvertFrom-Json -Depth 30
    }
    catch {
        Add-ValidationError 'bootstrap manifest 不是有效 JSON'
    }
}

$listedFiles = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$allowedSeedTables = @()
$observedSeedTables = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)

if ($null -ne $manifest) {
    $contractVersion = Get-PropertyValue -InputObject $manifest -Name 'contractVersion' -Context 'manifest'
    if ($contractVersion -isnot [int] -and $contractVersion -isnot [long]) {
        Add-ValidationError 'manifest.contractVersion 必须是整数 1'
    }
    elseif ([long]$contractVersion -ne 1) {
        Add-ValidationError 'manifest.contractVersion 必须为 1'
    }

    $target = Get-PropertyValue -InputObject $manifest -Name 'target' -Context 'manifest'
    $expectedTarget = [ordered]@{
        host = '127.0.0.1'
        port = 3306
        schema = 'videoops_agent_dev'
        user = 'videoops_agent'
        charset = 'utf8mb4'
        collation = 'utf8mb4_0900_ai_ci'
    }
    foreach ($entry in $expectedTarget.GetEnumerator()) {
        $actual = Get-PropertyValue -InputObject $target -Name $entry.Key -Context 'manifest.target'
        if ($entry.Key -eq 'port') {
            if (($actual -isnot [int] -and $actual -isnot [long]) -or [long]$actual -ne $entry.Value) {
                Add-ValidationError "manifest.target.port 必须精确为 $($entry.Value)"
            }
        }
        elseif ($actual -cne $entry.Value) {
            Add-ValidationError "manifest.target.$($entry.Key) 必须精确为 $($entry.Value)"
        }
    }

    $seed = Get-PropertyValue -InputObject $manifest -Name 'seed' -Context 'manifest'
    $rawAllowedSeedTables = @(Get-PropertyValue -InputObject $seed -Name 'allowedInsertTables' -Context 'manifest.seed')
    $requiredSessionVariables = @(Get-PropertyValue -InputObject $seed -Name 'requiredSessionVariables' -Context 'manifest.seed')
    if ($requiredSessionVariables.Count -ne 1 -or $requiredSessionVariables[0] -cne 'videoops_creator_password_hash') {
        Add-ValidationError 'manifest.seed.requiredSessionVariables 必须精确冻结 videoops_creator_password_hash'
    }
    foreach ($table in $rawAllowedSeedTables) {
        if ($table -isnot [string] -or $table -notmatch '^[a-z][a-z0-9_]*$') {
            Add-ValidationError 'manifest.seed.allowedInsertTables 包含无效表名'
            continue
        }
        if (Test-ForbiddenSeedTable -Table $table) {
            Add-ValidationError "manifest seed 白名单包含禁止表：$table"
            continue
        }
        if ($allowedSeedTables -ccontains $table) {
            Add-ValidationError "manifest seed 白名单包含重复表：$table"
            continue
        }
        $allowedSeedTables += $table
    }

    $steps = @(Get-PropertyValue -InputObject $manifest -Name 'steps' -Context 'manifest')
    if ($steps.Count -ne $expectedStepIds.Count) {
        Add-ValidationError "manifest.steps 必须精确包含 $($expectedStepIds.Count) 个步骤"
    }

    for ($index = 0; $index -lt $steps.Count; $index++) {
        $step = $steps[$index]
        $context = "manifest.steps[$index]"
        $id = [string](Get-PropertyValue -InputObject $step -Name 'id' -Context $context)
        if ($index -ge $expectedStepIds.Count -or $id -cne $expectedStepIds[$index]) {
            Add-ValidationError "$context 的步骤顺序或 id 不正确"
        }
        $expectedKind = if ($id -eq '900') { 'seed' } else { 'schema' }
        $kind = Get-PropertyValue -InputObject $step -Name 'kind' -Context $context
        if ($kind -cne $expectedKind) {
            Add-ValidationError "$context.kind 必须为 $expectedKind"
        }

        $file = [string](Get-PropertyValue -InputObject $step -Name 'file' -Context $context)
        if ($file -notmatch "^$([regex]::Escape($bootstrapRelativeRoot))/$([regex]::Escape($id))_[a-z0-9][a-z0-9_-]*\.sql$") {
            Add-ValidationError "$context.file 不是固定目录下与步骤 id 对应的 SQL 文件"
            continue
        }
        if (-not $listedFiles.Add($file)) {
            Add-ValidationError "manifest 重复列出 SQL 文件：$file"
        }

        $sha256 = [string](Get-PropertyValue -InputObject $step -Name 'sha256' -Context $context)
        if ($sha256 -notmatch '^[A-F0-9]{64}$') {
            Add-ValidationError "$context.sha256 必须是大写 SHA-256"
        }
        $purpose = Get-PropertyValue -InputObject $step -Name 'purpose' -Context $context
        if ($purpose -isnot [string] -or [string]::IsNullOrWhiteSpace($purpose)) {
            Add-ValidationError "$context.purpose 不能为空"
        }
        $tables = @(Get-PropertyValue -InputObject $step -Name 'tables' -Context $context)
        if ($tables.Count -eq 0) {
            Add-ValidationError "$context.tables 不能为空"
        }
        foreach ($table in $tables) {
            if ($table -isnot [string] -or $table -notmatch '^[a-z][a-z0-9_]*$') {
                Add-ValidationError "$context.tables 包含无效表名"
            }
        }

        $sqlPath = Join-Path $RepositoryRoot ($file.Replace('/', [IO.Path]::DirectorySeparatorChar))
        if (-not (Test-Path -LiteralPath $sqlPath -PathType Leaf)) {
            Add-ValidationError "manifest 列出的 SQL 文件不存在：$file"
            continue
        }
        $actualHash = (Get-FileHash -LiteralPath $sqlPath -Algorithm SHA256).Hash
        if ($actualHash -cne $sha256) {
            Add-ValidationError "SQL 文件 SHA-256 与 manifest 不一致：$file"
        }
        Test-SqlStep -StepId $id -Path $sqlPath -DeclaredTables @($tables) `
            -AllowedSeedTables $allowedSeedTables `
            -ObservedSeedTables $observedSeedTables
    }

    $bootstrapRoot = Join-Path $RepositoryRoot $bootstrapRelativeRoot.Replace('/', [IO.Path]::DirectorySeparatorChar)
    if (Test-Path -LiteralPath $bootstrapRoot -PathType Container) {
        $actualSqlFiles = @(Get-ChildItem -LiteralPath $bootstrapRoot -Recurse -File -Filter '*.sql' | ForEach-Object {
            [IO.Path]::GetRelativePath($RepositoryRoot, $_.FullName).Replace('\', '/')
        })
        foreach ($actualFile in $actualSqlFiles) {
            if (-not $listedFiles.Contains($actualFile)) {
                Add-ValidationError "bootstrap 目录存在 manifest 外 SQL 文件：$actualFile"
            }
        }
        foreach ($listedFile in $listedFiles) {
            if ($actualSqlFiles -cnotcontains $listedFile) {
                Add-ValidationError "manifest SQL 文件不在 bootstrap 目录清单中：$listedFile"
            }
        }
    }

    foreach ($allowedTable in $allowedSeedTables) {
        if (-not $observedSeedTables.Contains($allowedTable)) {
            Add-ValidationError "manifest seed 白名单表没有对应 INSERT：$allowedTable"
        }
    }
}

if ($errors.Count -gt 0) {
    foreach ($validationError in $errors) {
        [Console]::Error.WriteLine("ERROR: $validationError")
    }
    exit 1
}

Write-Output 'VIDEOOPS_DATABASE_BOOTSTRAP_OK'
