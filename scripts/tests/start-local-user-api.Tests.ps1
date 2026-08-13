$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'start-local-user-api.ps1'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$defaultMediaRoot = Join-Path $repositoryRoot '.runtime\digital-human-media'
$captureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_MEDIA_ROOT'
$argumentsCaptureVariableName = 'AI_VIDEO_DH_TEST_CAPTURED_JAVA_ARGUMENTS'

function Invoke-LocalUserApiLauncherForTest {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,

        [string]$LocalConfigPath
    )

    $testSecretValue = [Convert]::ToBase64String((New-Object byte[] 48))
    $testSecretsJson = [ordered]@{
        SYS_SA_TOKEN_JWT_SECRET = $testSecretValue
        APP_SA_TOKEN_JWT_SECRET = $testSecretValue
        APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET = $testSecretValue
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
        $global:LASTEXITCODE = 0
    }

    if ([string]::IsNullOrWhiteSpace($LocalConfigPath)) {
        . $scriptPath -SkipBuild -Port $Port
    }
    else {
        . $scriptPath -SkipBuild -Port $Port -LocalConfigPath $LocalConfigPath
    }

    return [Environment]::GetEnvironmentVariable($captureVariableName, 'Process')
}

Describe 'start-local-user-api runtime configuration' {
    BeforeEach {
        $script:previousMediaRoot = [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process')
        [Environment]::SetEnvironmentVariable($captureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($argumentsCaptureVariableName, $null, 'Process')
    }

    AfterEach {
        [Environment]::SetEnvironmentVariable($captureVariableName, $null, 'Process')
        [Environment]::SetEnvironmentVariable($argumentsCaptureVariableName, $null, 'Process')
        if ($null -eq $script:previousMediaRoot) {
            [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $null, 'Process')
        }
        else {
            [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $script:previousMediaRoot, 'Process')
        }
    }

    It 'provides a repository-local media root when the variable is unset' {
        [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $null, 'Process')

        $capturedMediaRoot = Invoke-LocalUserApiLauncherForTest -Port 65531

        $capturedMediaRoot | Should Be $defaultMediaRoot
        [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process') | Should BeNullOrEmpty
    }

    It 'preserves an explicitly configured media root' {
        $configuredMediaRoot = Join-Path $TestDrive 'configured-media'
        [Environment]::SetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', $configuredMediaRoot, 'Process')

        $capturedMediaRoot = Invoke-LocalUserApiLauncherForTest -Port 65532

        $capturedMediaRoot | Should Be $configuredMediaRoot
        [Environment]::GetEnvironmentVariable('AI_VIDEO_DH_MEDIA_ROOT', 'Process') | Should Be $configuredMediaRoot
    }

    It 'passes an existing local config file to Spring Boot' {
        $configPath = Join-Path $TestDrive 'user-api.local.yml'
        Set-Content -LiteralPath $configPath -Value "digital-human:`n  index-tts2:`n    base-url: https://example.invalid"
        $expectedConfigUri = ([Uri]::new((Resolve-Path -LiteralPath $configPath).Path)).AbsoluteUri

        Invoke-LocalUserApiLauncherForTest -Port 65533 -LocalConfigPath $configPath | Out-Null

        $capturedArguments = [Environment]::GetEnvironmentVariable($argumentsCaptureVariableName, 'Process')
        $capturedArguments | Should Match ([Regex]::Escape("--spring.config.additional-location=$expectedConfigUri"))
    }
}
