[CmdletBinding()]
param(
    [ValidateRange(1, 65535)]
    [int]$Port = 18081,

    [switch]$SkipBuild,

    [switch]$EnableGoldenPath,

    [string]$LocalConfigPath
)

if ($Port -ne 18081) {
    throw '本地创作端后端只允许监听 18081。'
}

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

function Protect-LocalSecretFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    $currentUser = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = [System.Security.AccessControl.FileSecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $acl.SetOwner($currentUser)
    $acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new(
        $currentUser,
        [System.Security.AccessControl.FileSystemRights]::FullControl,
        [System.Security.AccessControl.AccessControlType]::Allow
    ))
    Set-Acl -LiteralPath $Path -AclObject $acl
}

function Get-OrCreateLocalRuntimeSecrets {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Names,

        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (Test-Path -LiteralPath $Path -PathType Leaf) {
        Protect-LocalSecretFile -Path $Path
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
    Protect-LocalSecretFile -Path $Path
    return $result
}

function Import-LocalDpapiCredentialSecret {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$ExpectedUserName
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }

    Protect-LocalSecretFile -Path $Path
    try {
        $credential = Import-Clixml -LiteralPath $Path
    }
    catch {
        throw "本地 DPAPI 凭据无法读取：$Path"
    }
    if (($credential -isnot [System.Management.Automation.PSCredential]) -or
        ($credential.UserName -ne $ExpectedUserName)) {
        throw "本地 DPAPI 凭据身份无效：$Path"
    }

    $secret = $credential.GetNetworkCredential().Password
    if ([string]::IsNullOrWhiteSpace($secret)) {
        throw "本地 DPAPI 凭据为空：$Path"
    }
    return $secret
}

function Resolve-FailClosedBooleanEnvironmentVariable {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $rawValue = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return 'false'
    }
    if ([string]::Equals($rawValue, 'true', [System.StringComparison]::OrdinalIgnoreCase)) {
        return 'true'
    }
    if ([string]::Equals($rawValue, 'false', [System.StringComparison]::OrdinalIgnoreCase)) {
        return 'false'
    }
    throw "$Name 只允许设置为 true 或 false。"
}

function Assert-GoldenPathEnvironment {
    $requiredNames = @(
        'DIGITAL_HUMAN_INDEX_TTS2_BASE_URL',
        'DIGITAL_HUMAN_INDEX_TTS2_BASIC_USER',
        'VIDEOOPS_USER_DIGITAL_HUMAN_INDEX_TTS2_API_KEY',
        'VIDEOOPS_USER_DIGITAL_HUMAN_INDEX_TTS2_BASIC_PASSWORD',
        'DIGITAL_HUMAN_COMFY_UI_BASE_URL',
        'DIGITAL_HUMAN_COMFY_UI_BASIC_USER',
        'VIDEOOPS_USER_DIGITAL_HUMAN_COMFY_UI_BASIC_PASSWORD',
        'DIGITAL_HUMAN_COMFY_UI_INSECURE_HTTP_ALLOWED_HOSTS_0'
    )
    foreach ($name in $requiredNames) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            throw "GoldenPath 缺少必需的进程内配置：$name"
        }
    }
}

function Resolve-LocalExecutablePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command -or [string]::IsNullOrWhiteSpace([string]$command.Source)) {
        throw "GoldenPath 未找到本机可执行文件：$Name"
    }

    $resolvedPath = [string]$command.Source
    $item = Get-Item -LiteralPath $resolvedPath -Force -ErrorAction SilentlyContinue
    if ($null -ne $item -and -not [string]::IsNullOrWhiteSpace([string]$item.LinkType)) {
        $targets = @($item.Target | Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
        if ($targets.Count -ne 1) {
            throw "GoldenPath 可执行文件链接必须唯一指向一个本机文件：$Name"
        }

        $targetPath = [string]$targets[0]
        if (-not [System.IO.Path]::IsPathRooted($targetPath)) {
            $targetPath = Join-Path $item.Directory.FullName $targetPath
        }
        $resolvedPath = [System.IO.Path]::GetFullPath($targetPath)
    }

    if (-not (Test-Path -LiteralPath $resolvedPath -PathType Leaf)) {
        throw "GoldenPath 本机可执行文件无效：$Name"
    }
    return $resolvedPath
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$apiRoot = Join-Path $repositoryRoot 'ai-video-api'
$jarPath = Join-Path $apiRoot 'ai-video-user-api\target\ai-video-user-api.jar'
$localConfigRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot '.local\videoops-agent'))
$ossEnabled = Resolve-FailClosedBooleanEnvironmentVariable -Name 'VIDEOOPS_AIVIDEO_OSS_ENABLED'
$timelineFfmpegExecutable = $null
$timelineFfprobeExecutable = $null
if ($EnableGoldenPath) {
    if ($ossEnabled -ne 'true') {
        throw 'GoldenPath 只允许在 VIDEOOPS_AIVIDEO_OSS_ENABLED=true 时启动。'
    }
    Assert-GoldenPathEnvironment
    $timelineFfmpegExecutable = Resolve-LocalExecutablePath -Name 'ffmpeg'
    $timelineFfprobeExecutable = Resolve-LocalExecutablePath -Name 'ffprobe'
}

if ([string]::IsNullOrWhiteSpace($LocalConfigPath)) {
    $LocalConfigPath = Join-Path $repositoryRoot '.local\videoops-agent\user-api.local.yml'
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
    'VIDEOOPS_USER_SA_TOKEN_JWT_SECRET_KEY',
    'VIDEOOPS_USER_APP_SECURITY_TOKEN_JWT_SECRET',
    'VIDEOOPS_USER_APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET'
)
$mediaRootName = 'AI_VIDEO_DH_MEDIA_ROOT'
$previousMediaRoot = [Environment]::GetEnvironmentVariable($mediaRootName, 'Process')
$timelineWorkRootName = 'AIVIDEO_TIMELINE_WORK_ROOT'
$previousTimelineWorkRoot = [Environment]::GetEnvironmentVariable($timelineWorkRootName, 'Process')
$actuatorPasswordName = 'ACTUATOR_BASIC_PASSWORD'
$previousActuatorPassword = [Environment]::GetEnvironmentVariable($actuatorPasswordName, 'Process')
$runtimeMediaRoot = Join-Path $repositoryRoot '.runtime\videoops-agent\digital-human-media'
$runtimeTimelineWorkRoot = Join-Path $repositoryRoot '.runtime\videoops-agent\timeline-work'
$runtimeUserApiRoot = Join-Path $repositoryRoot '.runtime\videoops-agent\user-api'
$runtimeSecretsPath = Join-Path $repositoryRoot '.local\videoops-agent\local-runtime-secrets.json'
$questionnaireCredentialPath = Join-Path $repositoryRoot '.local\videoops-agent\questionnaire.credentials.clixml'
$questionnaireCredentialUserName = 'videoops-questionnaire-deepseek'
$questionnaireSecretName = 'VIDEOOPS_USER_QUESTIONNAIRE_DEEPSEEK_API_KEY'
$runtimeSecrets = Get-OrCreateLocalRuntimeSecrets -Names $secretNames -Path $runtimeSecretsPath
$questionnaireSecret = Import-LocalDpapiCredentialSecret `
    -Path $questionnaireCredentialPath `
    -ExpectedUserName $questionnaireCredentialUserName
New-Item -ItemType Directory -Path $runtimeUserApiRoot -Force | Out-Null

$previousSecrets = @{}
foreach ($secretName in $secretNames) {
    $previousSecrets[$secretName] = [Environment]::GetEnvironmentVariable($secretName, 'Process')
    Set-Item -Path "Env:$secretName" -Value $runtimeSecrets[$secretName]
}

Set-Item -Path "Env:$mediaRootName" -Value $runtimeMediaRoot
Set-Item -Path "Env:$timelineWorkRootName" -Value $runtimeTimelineWorkRoot
if ([string]::IsNullOrWhiteSpace($previousActuatorPassword)) {
    [Environment]::SetEnvironmentVariable($actuatorPasswordName, (New-LocalRuntimeSecret), 'Process')
}

$previousQuestionnaireSecret = [Environment]::GetEnvironmentVariable($questionnaireSecretName, 'Process')
if ($null -eq $questionnaireSecret) {
    Remove-Item -Path "Env:$questionnaireSecretName" -ErrorAction SilentlyContinue
}
else {
    Set-Item -Path "Env:$questionnaireSecretName" -Value $questionnaireSecret
}

Write-Host "正在启动创作端后端：http://localhost:$Port"
Write-Host "本地安全密钥已持久化并复用：$runtimeSecretsPath"

Push-Location $runtimeUserApiRoot
$applicationExitCode = 0
try {
    # These command-line values have higher Spring precedence than inherited
    # environment variables and the optional additional-location file. Keep the
    # local launcher fail-closed against the source project's runtime namespaces.
    $applicationArguments = @(
        '--spring.profiles.active=dev'
        '--server.address=127.0.0.1'
        "--server.port=$Port"
        '--spring.datasource.dynamic.primary=master'
        '--spring.datasource.dynamic.datasource.master.url=jdbc:mysql://127.0.0.1:3306/videoops_agent_dev?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8&autoReconnect=true&rewriteBatchedStatements=true&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true'
        '--spring.datasource.dynamic.datasource.master.username=videoops_agent'
        '--spring.data.redis.host=127.0.0.1'
        '--spring.data.redis.port=6379'
        '--spring.data.redis.database=14'
        '--redisson.keyPrefix=videoops-agent:dev'
        '--redisson.singleServerConfig.clientName=VideoOps-Agent-Dev'
        '--sa-token.redis-key-prefix=videoops-agent:dev:'
        "--digital-human.media-root=$runtimeMediaRoot"
        "--aivideo.timeline.work-root=$runtimeTimelineWorkRoot"
        "--aivideo.timeline.enabled=$($EnableGoldenPath.ToString().ToLowerInvariant())"
        '--aivideo.whisper.enabled=false'
        '--aivideo.runninghub.workflow-dispatch.enabled=false'
        "--aivideo.oss.enabled=$ossEnabled"
        '--aivideo.oss.config-key=videoops-agent-dev'
        '--aivideo.oss.prefix=videoops-agent/dev'
        '--spring.boot.admin.client.enabled=false'
        '--spring.boot.admin.client.username=local-actuator'
        '--snail-job.enabled=false'
        '--snail-ai.enabled=false'
        '--mail.enabled=false'
        '--api-decrypt.enabled=false'
    )
    if (-not $EnableGoldenPath) {
        $applicationArguments += '--digital-human.index-tts2.base-url='
        $applicationArguments += '--digital-human.comfy-ui.base-url='
    }
    else {
        $applicationArguments += "--aivideo.timeline.ffmpeg-path=$timelineFfmpegExecutable"
        $applicationArguments += "--aivideo.timeline.ffprobe-path=$timelineFfprobeExecutable"
    }
    if ([System.IO.File]::Exists($LocalConfigPath)) {
        $resolvedLocalConfig = (Resolve-Path -LiteralPath $LocalConfigPath).Path
        $requiredPrefix = $localConfigRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        if (-not $resolvedLocalConfig.StartsWith($requiredPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "本地运行配置必须位于当前仓库的 .local\videoops-agent 目录。"
        }
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
    if ($null -eq $previousQuestionnaireSecret) {
        Remove-Item -Path "Env:$questionnaireSecretName" -ErrorAction SilentlyContinue
    }
    else {
        Set-Item -Path "Env:$questionnaireSecretName" -Value $previousQuestionnaireSecret
    }
    if ($null -eq $previousTimelineWorkRoot) {
        Remove-Item -Path "Env:$timelineWorkRootName" -ErrorAction SilentlyContinue
    }
    else {
        Set-Item -Path "Env:$timelineWorkRootName" -Value $previousTimelineWorkRoot
    }
    [Environment]::SetEnvironmentVariable($actuatorPasswordName, $previousActuatorPassword, 'Process')
}

if ($applicationExitCode -ne 0) {
    exit $applicationExitCode
}
