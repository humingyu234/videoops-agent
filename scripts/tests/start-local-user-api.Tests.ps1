$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'start-local-user-api.ps1'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$defaultMediaRoot = Join-Path $repositoryRoot '.runtime\videoops-agent\digital-human-media'
$defaultTimelineWorkRoot = Join-Path $repositoryRoot '.runtime\videoops-agent\timeline-work'
$defaultUserApiRoot = Join-Path $repositoryRoot '.runtime\videoops-agent\user-api'
$captureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_MEDIA_ROOT'
$timelineCaptureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_TIMELINE_WORK_ROOT'
$argumentsCaptureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_JAVA_ARGUMENTS'
$secretStateCaptureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_SECRET_STATE'
$actuatorStateCaptureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_ACTUATOR_STATE'
$workingDirectoryCaptureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_WORKING_DIRECTORY'
$ossEnabledVariableName = 'VIDEOOPS_AIVIDEO_OSS_ENABLED'
$actuatorPasswordVariableName = 'ACTUATOR_BASIC_PASSWORD'
$resolvedFfmpegPath = 'C:\tools\ffmpeg-real.exe'
$resolvedFfprobePath = 'C:\tools\ffprobe-real.exe'
$localConfigRoot = Join-Path $repositoryRoot '.local\videoops-agent'
$runtimeSecretNames = @(
    'VIDEOOPS_USER_SA_TOKEN_JWT_SECRET_KEY',
    'VIDEOOPS_USER_APP_SECURITY_TOKEN_JWT_SECRET',
    'VIDEOOPS_USER_APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET'
)

function Invoke-LocalUserApiLauncherForTest {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,

        [string]$LocalConfigPath,

        [switch]$EnableGoldenPath
    )

    $testSecretValue = [Convert]::ToBase64String((New-Object byte[] 48))
    $testSecretsJson = [ordered]@{
        VIDEOOPS_USER_SA_TOKEN_JWT_SECRET_KEY = $testSecretValue
        VIDEOOPS_USER_APP_SECURITY_TOKEN_JWT_SECRET = $testSecretValue
        VIDEOOPS_USER_APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET = $testSecretValue
    } | ConvertTo-Json

    function Get-Command {
        param(
            [string]$Name,
            [object]$ErrorAction
        )
        return [PSCustomObject]@{ Source = $Name }
    }

    function Get-Content {
        param(
            [string]$LiteralPath,
            [switch]$Raw
        )
        return $testSecretsJson
    }

    function Get-Item {
        param(
            [string]$LiteralPath,
            [switch]$Force,
            [object]$ErrorAction
        )
        if ($LiteralPath -eq 'ffmpeg') {
            return [PSCustomObject]@{
                LinkType = 'SymbolicLink'
                Target = $resolvedFfmpegPath
                Directory = [PSCustomObject]@{ FullName = 'C:\tools' }
            }
        }
        if ($LiteralPath -eq 'ffprobe') {
            return [PSCustomObject]@{
                LinkType = 'SymbolicLink'
                Target = $resolvedFfprobePath
                Directory = [PSCustomObject]@{ FullName = 'C:\tools' }
            }
        }
        return $null
    }

    function Set-Acl {
        param(
            [string]$LiteralPath,
            [object]$AclObject
        )
    }

    function Test-NetConnection {
        [CmdletBinding()]
        param(
            [string]$ComputerName,
            [int]$Port,
            [string]$InformationLevel
        )
        return $false
    }

    function Test-Path {
        [CmdletBinding()]
        param(
            [string]$LiteralPath,
            [string]$PathType
        )
        return $true
    }

    function java {
        param(
            [switch]$Jar,
            [string]$ArchivePath,
            [Parameter(ValueFromRemainingArguments = $true)]
            [object[]]$RemainingArguments
        )

        [Environment]::SetEnvironmentVariable(
            $captureVariableName,
            [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process'),
            'Process'
        )
        [Environment]::SetEnvironmentVariable(
            $argumentsCaptureVariableName,
            ($RemainingArguments -join "`n"),
            'Process'
        )
        [Environment]::SetEnvironmentVariable(
            $timelineCaptureVariableName,
            [Environment]::GetEnvironmentVariable('AIVIDEO_TIMELINE_WORK_ROOT', 'Process'),
            'Process'
        )
        $secretStates = $runtimeSecretNames | ForEach-Object {
            if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, 'Process'))) {
                'missing'
            }
            else {
                'present'
            }
        }
        [Environment]::SetEnvironmentVariable(
            $secretStateCaptureVariableName,
            ($secretStates -join ','),
            'Process'
        )
        $actuatorState = if ([string]::IsNullOrWhiteSpace(
            [Environment]::GetEnvironmentVariable($actuatorPasswordVariableName, 'Process')
        )) { 'missing' } else { 'present' }
        [Environment]::SetEnvironmentVariable(
            $actuatorStateCaptureVariableName,
            $actuatorState,
            'Process'
        )
        [Environment]::SetEnvironmentVariable(
            $workingDirectoryCaptureVariableName,
            (Get-Location).Path,
            'Process'
        )
        $global:LASTEXITCODE = 0
    }

    if ([string]::IsNullOrWhiteSpace($LocalConfigPath)) {
        . $scriptPath -SkipBuild -Port $Port -EnableGoldenPath:$EnableGoldenPath
    }
    else {
        . $scriptPath -SkipBuild -Port $Port -LocalConfigPath $LocalConfigPath -EnableGoldenPath:$EnableGoldenPath
    }

    return [Environment]::GetEnvironmentVariable($captureVariableName, 'Process')
}

Describe 'start-local-user-api runtime configuration' {
    BeforeEach {
        $script:previousMediaRoot = [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process')
        $script:previousTimelineWorkRoot = [Environment]::GetEnvironmentVariable('AIVIDEO_TIMELINE_WORK_ROOT', 'Process')
        $script:previousRuntimeSecrets = @{}
        foreach ($secretName in $runtimeSecretNames) {
            $script:previousRuntimeSecrets[$secretName] = [Environment]::GetEnvironmentVariable($secretName, 'Process')
        }
        $script:previousOssEnabled = [Environment]::GetEnvironmentVariable($ossEnabledVariableName, 'Process')
        $script:previousActuatorPassword = [Environment]::GetEnvironmentVariable($actuatorPasswordVariableName, 'Process')
        [Environment]::SetEnvironmentVariable($actuatorPasswordVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($ossEnabledVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($captureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($timelineCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($argumentsCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($secretStateCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($actuatorStateCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($workingDirectoryCaptureVariableName, $null, 'Process')
        $script:testLocalConfigPath = $null
    }

    AfterEach {
        [Environment]::SetEnvironmentVariable($captureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($timelineCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($argumentsCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($secretStateCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($actuatorStateCaptureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($workingDirectoryCaptureVariableName, $null, 'Process')
        if ($null -ne $script:testLocalConfigPath -and (Test-Path -LiteralPath $script:testLocalConfigPath -PathType Leaf)) {
            Remove-Item -LiteralPath $script:testLocalConfigPath -Force
        }
        if ($null -eq $script:previousMediaRoot) {
            [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $null, 'Process')
        }
        else {
            [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $script:previousMediaRoot, 'Process')
        }
        if ($null -eq $script:previousTimelineWorkRoot) {
            [Environment]::SetEnvironmentVariable('AIVIDEO_TIMELINE_WORK_ROOT', $null, 'Process')
        }
        else {
            [Environment]::SetEnvironmentVariable('AIVIDEO_TIMELINE_WORK_ROOT', $script:previousTimelineWorkRoot, 'Process')
        }
        foreach ($secretName in $runtimeSecretNames) {
            [Environment]::SetEnvironmentVariable($secretName, $script:previousRuntimeSecrets[$secretName], 'Process')
        }
        [Environment]::SetEnvironmentVariable($ossEnabledVariableName, $script:previousOssEnabled, 'Process')
        [Environment]::SetEnvironmentVariable(
            $actuatorPasswordVariableName,
            $script:previousActuatorPassword,
            'Process'
        )
    }

    It 'provides a repository-local media root when the variable is unset' {
        [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $null, 'Process')

        $capturedMediaRoot = Invoke-LocalUserApiLauncherForTest -Port 18081

        $capturedMediaRoot | Should Be $defaultMediaRoot
        [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process') | Should BeNullOrEmpty
    }

    It 'overrides an inherited media root with the repository namespace' {
        $inheritedMediaRoot = Join-Path $TestDrive 'source-project-media'
        [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $inheritedMediaRoot, 'Process')

        $capturedMediaRoot = Invoke-LocalUserApiLauncherForTest -Port 18081

        $capturedMediaRoot | Should Be $defaultMediaRoot
        [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process') | Should Be $inheritedMediaRoot
    }

    It 'provides a repository-local timeline work root when the variable is unset' {
        [Environment]::SetEnvironmentVariable('AIVIDEO_TIMELINE_WORK_ROOT', $null, 'Process')

        Invoke-LocalUserApiLauncherForTest -Port 18081 | Out-Null

        [Environment]::GetEnvironmentVariable($timelineCaptureVariableName, 'Process') | Should Be $defaultTimelineWorkRoot
        [Environment]::GetEnvironmentVariable('AIVIDEO_TIMELINE_WORK_ROOT', 'Process') | Should BeNullOrEmpty
    }

    It 'injects the project-specific runtime secrets only for the child process window' {
        foreach ($secretName in $runtimeSecretNames) {
            [Environment]::SetEnvironmentVariable($secretName, $null, 'Process')
        }

        Invoke-LocalUserApiLauncherForTest -Port 18081 | Out-Null

        [Environment]::GetEnvironmentVariable($secretStateCaptureVariableName, 'Process') |
            Should Be 'present,present,present'
        foreach ($secretName in $runtimeSecretNames) {
            [Environment]::GetEnvironmentVariable($secretName, 'Process') | Should BeNullOrEmpty
        }
    }

    It 'injects an ephemeral actuator password only for the child process window' {
        Invoke-LocalUserApiLauncherForTest -Port 18081 | Out-Null

        [Environment]::GetEnvironmentVariable($actuatorStateCaptureVariableName, 'Process') | Should Be 'present'
        [Environment]::GetEnvironmentVariable($actuatorPasswordVariableName, 'Process') | Should BeNullOrEmpty
    }

    It 'uses the shared fail-closed OSS switch for the child process' {
        [Environment]::SetEnvironmentVariable($ossEnabledVariableName, 'true', 'Process')

        Invoke-LocalUserApiLauncherForTest -Port 18081 | Out-Null

        $capturedArguments = [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process')
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.oss.enabled=true'))
        $capturedArguments | Should Not Match ([Regex]::Escape('--aivideo.oss.enabled=false'))
    }

    It 'rejects an invalid shared OSS switch before invoking Java' {
        [Environment]::SetEnvironmentVariable($ossEnabledVariableName, 'enabled', 'Process')

        { Invoke-LocalUserApiLauncherForTest -Port 18081 } |
            Should Throw 'VIDEOOPS_AIVIDEO_OSS_ENABLED 只允许设置为 true 或 false。'
        [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process') | Should BeNullOrEmpty
    }

    It 'enables only the provider and timeline gates for an explicit GoldenPath run' {
        $goldenEnvironment = @{
            DIGITAL_HUMAN_INDEX_TTS2_BASE_URL = 'https://provider.invalid:39000'
            DIGITAL_HUMAN_INDEX_TTS2_BASIC_USER = 'provider-user'
            VIDEOOPS_USER_DIGITAL_HUMAN_INDEX_TTS2_API_KEY = 'test-api-key'
            VIDEOOPS_USER_DIGITAL_HUMAN_INDEX_TTS2_BASIC_PASSWORD = 'test-password'
            DIGITAL_HUMAN_COMFY_UI_BASE_URL = 'http://provider.invalid:8189'
            DIGITAL_HUMAN_COMFY_UI_BASIC_USER = 'provider-user'
            VIDEOOPS_USER_DIGITAL_HUMAN_COMFY_UI_BASIC_PASSWORD = 'test-password'
            DIGITAL_HUMAN_COMFY_UI_INSECURE_HTTP_ALLOWED_HOSTS_0 = 'provider.invalid'
        }
        $previous = @{}
        foreach ($entry in $goldenEnvironment.GetEnumerator()) {
            $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
        }
        [Environment]::SetEnvironmentVariable($ossEnabledVariableName, 'true', 'Process')
        try {
            Invoke-LocalUserApiLauncherForTest -Port 18081 -EnableGoldenPath | Out-Null
        }
        finally {
            foreach ($entry in $goldenEnvironment.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable($entry.Key, $previous[$entry.Key], 'Process')
            }
        }

        $capturedArguments = [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process')
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.timeline.enabled=true'))
        $capturedArguments | Should Match ([Regex]::Escape("--aivideo.timeline.ffmpeg-path=$resolvedFfmpegPath"))
        $capturedArguments | Should Match ([Regex]::Escape("--aivideo.timeline.ffprobe-path=$resolvedFfprobePath"))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.oss.enabled=true'))
        $capturedArguments | Should Not Match ([Regex]::Escape('--digital-human.index-tts2.base-url='))
        $capturedArguments | Should Not Match ([Regex]::Escape('--digital-human.comfy-ui.base-url='))
        $capturedArguments | Should Match ([Regex]::Escape('--questionnaire.deepseek.api-key='))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.whisper.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.runninghub.workflow-dispatch.enabled=false'))
    }

    It 'rejects GoldenPath before Java when OSS is not explicitly enabled' {
        { Invoke-LocalUserApiLauncherForTest -Port 18081 -EnableGoldenPath } |
            Should Throw 'GoldenPath 只允许在 VIDEOOPS_AIVIDEO_OSS_ENABLED=true 时启动。'
        [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process') | Should BeNullOrEmpty
    }

    It 'rejects GoldenPath before Java when provider process configuration is incomplete' {
        $name = 'DIGITAL_HUMAN_INDEX_TTS2_BASE_URL'
        $previous = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($ossEnabledVariableName, 'true', 'Process')
        [Environment]::SetEnvironmentVariable($name, $null, 'Process')
        try {
            { Invoke-LocalUserApiLauncherForTest -Port 18081 -EnableGoldenPath } |
                Should Throw "GoldenPath 缺少必需的进程内配置：$name"
        }
        finally {
            [Environment]::SetEnvironmentVariable($name, $previous, 'Process')
        }
        [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process') | Should BeNullOrEmpty
    }

    It 'passes an existing local config file to Spring Boot' {
        New-Item -ItemType Directory -Path $localConfigRoot -Force | Out-Null
        $configPath = Join-Path $localConfigRoot ("test-user-api-{0}.local.yml" -f [Guid]::NewGuid().ToString('N'))
        $script:testLocalConfigPath = $configPath
        Set-Content -LiteralPath $configPath -Value "digital-human:`n  index-tts2:`n    base-url: https://example.invalid"
        $expectedConfigUri = ([Uri]::new((Resolve-Path -LiteralPath $configPath).Path)).AbsoluteUri

        Invoke-LocalUserApiLauncherForTest -Port 18081 -LocalConfigPath $configPath | Out-Null

        $capturedArguments = [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process')
        $capturedArguments | Should Match ([Regex]::Escape("--spring.config.additional-location=$expectedConfigUri"))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.profiles.active=dev'))
        $capturedArguments | Should Match ([Regex]::Escape('--server.address=127.0.0.1'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.datasource.dynamic.primary=master'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.datasource.dynamic.datasource.master.url=jdbc:mysql://127.0.0.1:3306/videoops_agent_dev?'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.datasource.dynamic.datasource.master.username=videoops_agent'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.data.redis.host=127.0.0.1'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.data.redis.port=6379'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.data.redis.database=14'))
        $capturedArguments | Should Match ([Regex]::Escape('--redisson.keyPrefix=videoops-agent:dev'))
        $capturedArguments | Should Match ([Regex]::Escape('--redisson.singleServerConfig.clientName=VideoOps-Agent-Dev'))
        $capturedArguments | Should Match ([Regex]::Escape('--sa-token.redis-key-prefix=videoops-agent:dev:'))
        $capturedArguments | Should Match ([Regex]::Escape("--digital-human.media-root=$defaultMediaRoot"))
        $capturedArguments | Should Match ([Regex]::Escape("--aivideo.timeline.work-root=$defaultTimelineWorkRoot"))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.timeline.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.whisper.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.runninghub.workflow-dispatch.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.oss.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.oss.config-key=videoops-agent-dev'))
        $capturedArguments | Should Match ([Regex]::Escape('--aivideo.oss.prefix=videoops-agent/dev'))
        $capturedArguments | Should Match ([Regex]::Escape('--questionnaire.deepseek.api-key='))
        $capturedArguments | Should Match ([Regex]::Escape('--digital-human.index-tts2.base-url='))
        $capturedArguments | Should Match ([Regex]::Escape('--digital-human.comfy-ui.base-url='))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.boot.admin.client.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--spring.boot.admin.client.username=local-actuator'))
        $capturedArguments | Should Match ([Regex]::Escape('--snail-job.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--snail-ai.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--mail.enabled=false'))
        $capturedArguments | Should Match ([Regex]::Escape('--api-decrypt.enabled=false'))
        [Environment]::GetEnvironmentVariable($workingDirectoryCaptureVariableName, 'Process') |
            Should Be $defaultUserApiRoot
    }

    It 'rejects an additional config outside the project-local namespace' {
        $externalConfigPath = Join-Path $TestDrive 'source-project.yml'
        Set-Content -LiteralPath $externalConfigPath -Value "spring:`n  data:`n    redis:`n      database: 0"

        { Invoke-LocalUserApiLauncherForTest -Port 18081 -LocalConfigPath $externalConfigPath } |
            Should Throw '本地运行配置必须位于当前仓库的 .local\videoops-agent 目录。'
    }

    It 'rejects a local API port outside the project allocation' {
        { Invoke-LocalUserApiLauncherForTest -Port 8080 } |
            Should Throw '本地创作端后端只允许监听 18081。'
        [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process') | Should BeNullOrEmpty
    }
}
