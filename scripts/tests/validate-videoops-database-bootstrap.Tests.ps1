$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'validate-videoops-database-bootstrap.ps1'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$sourceBootstrapRoot = Join-Path $repositoryRoot 'docs\sql\videoops-agent\mysql'
$pwshPath = (Get-Command pwsh -ErrorAction Stop).Source

function New-BootstrapFixture {
    $fixtureRoot = Join-Path $TestDrive ([Guid]::NewGuid().ToString('N'))
    $fixtureParent = Join-Path $fixtureRoot 'docs\sql\videoops-agent'
    New-Item -ItemType Directory -Path $fixtureParent -Force | Out-Null
    Copy-Item -LiteralPath $sourceBootstrapRoot -Destination (Join-Path $fixtureParent 'mysql') -Recurse
    return $fixtureRoot
}

function Read-FixtureManifest {
    param([Parameter(Mandatory)][string]$FixtureRoot)
    $path = Join-Path $FixtureRoot 'docs\sql\videoops-agent\mysql\bootstrap-manifest.json'
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $path | ConvertFrom-Json -Depth 30
}

function Save-FixtureManifest {
    param(
        [Parameter(Mandatory)][string]$FixtureRoot,
        [Parameter(Mandatory)][object]$Manifest
    )
    $path = Join-Path $FixtureRoot 'docs\sql\videoops-agent\mysql\bootstrap-manifest.json'
    $json = $Manifest | ConvertTo-Json -Depth 30
    [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
}

function Get-FixtureStepPath {
    param(
        [Parameter(Mandatory)][string]$FixtureRoot,
        [Parameter(Mandatory)][object]$Step
    )
    return Join-Path $FixtureRoot ([string]$Step.file).Replace('/', [IO.Path]::DirectorySeparatorChar)
}

function Sync-StepHash {
    param(
        [Parameter(Mandatory)][string]$FixtureRoot,
        [Parameter(Mandatory)][object]$Step
    )
    $Step.sha256 = (Get-FileHash -LiteralPath (Get-FixtureStepPath $FixtureRoot $Step) -Algorithm SHA256).Hash
}

function Invoke-BootstrapValidator {
    param([Parameter(Mandatory)][string]$FixtureRoot)
    $output = & $pwshPath -NoProfile -NonInteractive -File $scriptPath `
        -RepositoryRoot $FixtureRoot 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output | Out-String)
    }
}

Describe 'VideoOps database bootstrap static gate' {
    It 'accepts the current hash-frozen bootstrap artifact' {
        $fixtureRoot = New-BootstrapFixture

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 0
        $result.Output | Should Match 'VIDEOOPS_DATABASE_BOOTSTRAP_OK'
    }

    It 'normalizes MySQL 8.4 identifier quotes before checking approval nullability' {
        $sqlPath = Join-Path $sourceBootstrapRoot '120_agent_run_quality_control.sql'
        $sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $sqlPath
        $evaluationProbe = 'LOWER\(REPLACE\(CHECK_CLAUSE,\s*''`'',\s*''''\)\)\s+LIKE\s+''%evaluation_id is not null%'''
        $decisionProbe = 'LOWER\(REPLACE\(CHECK_CLAUSE,\s*''`'',\s*''''\)\)\s+LIKE\s+''%decided_by is not null%'''

        ([regex]::Matches($sql, $evaluationProbe)).Count | Should Be 2
        ([regex]::Matches($sql, $decisionProbe)).Count | Should Be 2

        $mysql84Clause = '((`approval_type` in (''conditional'',''final'')) and (`evaluation_id` is not null))'
        $missingPredicate = '((`approval_type` in (''conditional'',''final'')) and (`evaluation_id` is null))'
        $mysql84Clause.Replace('`', '').ToLowerInvariant().Contains('evaluation_id is not null') | Should Be $true
        $missingPredicate.Replace('`', '').ToLowerInvariant().Contains('evaluation_id is not null') | Should Be $false
    }

    It 'rejects USE ai_video even when the attacker synchronizes the file hash' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps)[0]
        Add-Content -LiteralPath (Get-FixtureStepPath $fixtureRoot $step) -Value "`nUSE ai_video;" -Encoding UTF8
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '公司库标识|数据库级语句'
    }

    It 'rejects a sys_oss_config seed insert even when its hash is synchronized' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps | Where-Object id -EQ '900')[0]
        Add-Content -LiteralPath (Get-FixtureStepPath $fixtureRoot $step) `
            -Value "`nINSERT IGNORE INTO sys_oss_config (oss_config_id) VALUES (1);" -Encoding UTF8
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '禁止写入表：sys_oss_config'
    }

    It 'rejects a reordered step list' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $first = $manifest.steps[0]
        $manifest.steps[0] = $manifest.steps[1]
        $manifest.steps[1] = $first
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '步骤顺序或 id 不正确'
    }

    It 'rejects an extra SQL file outside the manifest' {
        $fixtureRoot = New-BootstrapFixture
        $extraPath = Join-Path $fixtureRoot 'docs\sql\videoops-agent\mysql\999_extra.sql'
        Set-Content -LiteralPath $extraPath -Value 'SELECT 1;' -Encoding UTF8

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'manifest 外 SQL 文件'
    }

    It 'rejects a stale SHA-256' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps)[0]
        Add-Content -LiteralPath (Get-FixtureStepPath $fixtureRoot $step) -Value "`n-- drift" -Encoding UTF8

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'SHA-256 与 manifest 不一致'
    }

    It 'rejects exact target drift in <Field>' -TestCases @(
        @{ Field = 'host'; Value = 'localhost' }
        @{ Field = 'port'; Value = 3307 }
        @{ Field = 'schema'; Value = 'ai_video' }
        @{ Field = 'user'; Value = 'root' }
    ) {
        param($Field, $Value)
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $manifest.target.$Field = $Value
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match "manifest.target.$Field 必须精确"
    }

    It 'rejects schema DML even when its hash is synchronized' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps | Where-Object id -EQ '010')[0]
        Add-Content -LiteralPath (Get-FixtureStepPath $fixtureRoot $step) `
            -Value "`nUPDATE sys_user SET status = '1';" -Encoding UTF8
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'schema 步骤包含禁止'
    }

    It 'rejects a cross-database table reference with a synchronized hash' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps)[0]
        Add-Content -LiteralPath (Get-FixtureStepPath $fixtureRoot $step) `
            -Value "`nSELECT 1 FROM other_business.sys_user;" -Encoding UTF8
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '跨库限定引用'
    }

    It 'rejects replacing the creator password variable with a literal' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps | Where-Object id -EQ '900')[0]
        $stepPath = Get-FixtureStepPath $fixtureRoot $step
        $content = (Get-Content -Raw -Encoding UTF8 -LiteralPath $stepPath).Replace(
            '@videoops_creator_password_hash',
            "'literal-password'"
        )
        [IO.File]::WriteAllText($stepPath, $content, [Text.UTF8Encoding]::new($false))
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '必须使用 @videoops_creator_password_hash|疑似字面口令'
    }

    It 'rejects an embedded BCrypt literal while the required variable still exists' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps | Where-Object id -EQ '900')[0]
        Add-Content -LiteralPath (Get-FixtureStepPath $fixtureRoot $step) `
            -Value "`nSET @unsafe_hash = '`$2b`$12`$ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0';" `
            -Encoding UTF8
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '疑似字面口令'
    }

    It 'rejects changing the fail-closed guard insert to INSERT IGNORE' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps | Where-Object id -EQ '900')[0]
        $stepPath = Get-FixtureStepPath $fixtureRoot $step
        $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $stepPath
        $guardInsertPattern = [regex]::new(
            'INSERT\s+INTO\s+videoops_seed_guard',
            [Text.RegularExpressions.RegexOptions]::IgnoreCase
        )
        $content = $guardInsertPattern.Replace(
            $content,
            'INSERT IGNORE INTO videoops_seed_guard',
            1
        )
        [IO.File]::WriteAllText($stepPath, $content, [Text.UTF8Encoding]::new($false))
        Sync-StepHash $fixtureRoot $step
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match '必须使用普通 INSERT 触发 CHECK'
    }

    It 'rejects manifest table drift from the actual schema DDL' {
        $fixtureRoot = New-BootstrapFixture
        $manifest = Read-FixtureManifest $fixtureRoot
        $step = @($manifest.steps)[0]
        $step.tables[0] = 'undeclared_table'
        Save-FixtureManifest $fixtureRoot $manifest

        $result = Invoke-BootstrapValidator -FixtureRoot $fixtureRoot

        $result.ExitCode | Should Be 1
        $result.Output | Should Match 'DDL 目标未在 manifest.tables|没有对应 CREATE/ALTER'
    }
}
