[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,

    [switch]$SkipBuild,

    [string]$LocalConfigPath
)

$ErrorActionPreference = 'Stop'

function New-LocalRuntimeSecret {
    $bytes = New-Object byte[] 48
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $generator.GetBytes($bytes)
        return [Convert]::ToBase64String($bytes)
    }
    finally {
        $generator.Dispose()
    }
}

function Get-OrCreateLocalRuntimeSecrets {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Names,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        try {
            $savedSecrets = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        }
        catch {
            throw "本地运行密钥文件无法读取：$Path。请修复或删除该文件后重试。"
        }

        $result = @{}
        foreach ($name in $Names) {
            $value = [string]$savedSecrets.$name
            try {
                $decodedValue = [Convert]::FromBase64String($value)
            }
            catch {
                throw "本地运行密钥 $name 格式无效：$Path"
            }

            if ($decodedValue.Length -lt 32) {
                throw "本地运行密钥 $name 长度不足：$Path"
            }
            $result[$name] = $value
        }
        return $result
    }

    $directory = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $directory -Force | Out-Null

    $result = [ordered]@{}
    foreach ($name in $Names) {
        $result[$name] = New-LocalRuntimeSecret
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath $Path -Encoding UTF8
    return $result
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$apiRoot = Join-Path $repositoryRoot 'ai-video-api'
$jarPath = Join-Path $apiRoot 'ai-video-user-api\target\ai-video-user-api.jar'

if ([string]::IsNullOrWhiteSpace($LocalConfigPath)) {
    $LocalConfigPath = Join-Path $repositoryRoot '.runtime\user-api.local.yml'
}

if (-not $SkipBuild) {
    Push-Location $apiRoot
    try {
        & .\mvnw.cmd -pl ai-video-user-api -am -DskipTests package
        if ($LASTEXITCODE -ne 0) {
            throw "后端构建失败，退出码：$LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "未找到后端包：$jarPath。请先去掉 -SkipBuild 参数重新执行。"
}

$javaCommand = Get-Command 'java' -ErrorAction SilentlyContinue
if ($null -ne $javaCommand) {
    $javaExecutable = $javaCommand.Source
}
else {
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
    }
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $javaExecutable = $null
    }
    else {
        $javaExecutable = Join-Path $javaHome 'bin\java.exe'
    }
}

if ([string]::IsNullOrWhiteSpace($javaExecutable) -or -not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw '未找到 Java。请配置 PATH 或 JAVA_HOME 后重试。'
}

if (Test-NetConnection -ComputerName '127.0.0.1' -Port $Port -InformationLevel Quiet -WarningAction SilentlyContinue) {
    throw "端口 $Port 已被占用。请先停止占用进程，或使用 -Port 指定其他端口。"
}

$secretNames = @(
    'SYS_SA_TOKEN_JWT_SECRET',
    'APP_SA_TOKEN_JWT_SECRET',
    'APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET'
)
$mediaRootName = 'AI_VIDEO_DH_MEDIA_ROOT'
$previousMediaRoot = [Environment]::GetEnvironmentVariable($mediaRootName, 'Process')
$runtimeSecretsPath = Join-Path $repositoryRoot '.local\local-runtime-secrets.json'
$runtimeSecrets = Get-OrCreateLocalRuntimeSecrets -Names $secretNames -Path $runtimeSecretsPath

$previousSecrets = @{}
foreach ($secretName in $secretNames) {
    $previousSecrets[$secretName] = [Environment]::GetEnvironmentVariable($secretName, 'Process')
    Set-Item -Path "Env:$secretName" -Value $runtimeSecrets[$secretName]
}

if ([string]::IsNullOrWhiteSpace($previousMediaRoot)) {
    Set-Item -Path "Env:$mediaRootName" -Value (Join-Path $repositoryRoot '.runtime\digital-human-media')
}

Write-Host "正在启动创作端后端：http://localhost:$Port"
Write-Host "本地安全密钥已持久化并复用：$runtimeSecretsPath"

Push-Location $apiRoot
$applicationExitCode = 0
try {
    $applicationArguments = @("--server.port=$Port")
    if ([System.IO.File]::Exists($LocalConfigPath)) {
        $resolvedLocalConfig = (Resolve-Path -LiteralPath $LocalConfigPath).Path
        $localConfigUri = [Uri]::new($resolvedLocalConfig).AbsoluteUri
        $applicationArguments += "--spring.config.additional-location=$localConfigUri"
        Write-Host "已加载本地运行配置：$LocalConfigPath"
    }

    & $javaExecutable -jar $jarPath @applicationArguments
    $applicationExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
    foreach ($secretName in $secretNames) {
        $previousValue = $previousSecrets[$secretName]
        if ($null -eq $previousValue) {
            Remove-Item -Path "Env:$secretName" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item -Path "Env:$secretName" -Value $previousValue
        }
    }
    if ($null -eq $previousMediaRoot) {
        Remove-Item -Path "Env:$mediaRootName" -ErrorAction SilentlyContinue
    }
    else {
        Set-Item -Path "Env:$mediaRootName" -Value $previousMediaRoot
    }
}

if ($applicationExitCode -ne 0) {
    exit $applicationExitCode
}
