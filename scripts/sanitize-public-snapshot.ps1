[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string] $RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$resolvedRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$scriptRepositoryRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
if ($resolvedRoot -ne $scriptRepositoryRoot) {
    throw "Refusing to sanitize an unexpected directory: $resolvedRoot"
}
foreach ($marker in @('.git', 'AGENTS.md', 'docs\PROJECT.md')) {
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedRoot $marker))) {
        throw "Refusing to sanitize a directory without repository marker '$marker': $resolvedRoot"
    }
}

$sensitiveKeys = @(
    'password', 'passwd', 'userpassword', 'pass',
    'token', 'internal-token', 'jwt-secret', 'jwt-secret-key',
    'workspace-key-secret', 'hmac-secret', 'api-key', 'master-key',
    'access-key', 'secret-key', 'access-key-id', 'access-key-secret',
    'client-secret', 'basic-password', 'privatekey',
    'sa_password', 'postgres_password', 'mysql_root_password',
    'minio_root_password', 'gh_token', 'github_token', 'surge_token',
    'pagespeed_api_key', 'aivideo_whisper_internal_token',
    'vite_app_rsa_private_key'
)

function Test-SensitiveKey {
    param([string] $Key)
    return $sensitiveKeys -contains $Key.ToLowerInvariant()
}

function Get-EnvironmentPrefix {
    param([string] $RelativePath)

    switch -Regex ($RelativePath.Replace('/', '\')) {
        'ai-video-user-api' { return 'VIDEOOPS_USER' }
        'ruoyi-admin' { return 'VIDEOOPS_ADMIN' }
        'ruoyi-monitor-admin' { return 'VIDEOOPS_MONITOR' }
        'ruoyi-snailai-server' { return 'VIDEOOPS_SNAILAI' }
        'ruoyi-snailjob-server' { return 'VIDEOOPS_SNAILJOB' }
        'ai-video-platform-ui' { return 'VIDEOOPS_PLATFORM_UI' }
        'ai-video-webapp' { return 'VIDEOOPS_WEBAPP' }
        'ai-video-worker' { return 'VIDEOOPS_WORKER' }
        'script\\docker' { return 'VIDEOOPS_DOCKER' }
        default { return 'VIDEOOPS' }
    }
}

function Convert-ToEnvironmentName {
    param(
        [string] $Prefix,
        [string[]] $Segments
    )

    $normalized = $Segments |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Last 5 |
        ForEach-Object { ($_ -replace '[^A-Za-z0-9]+', '_').Trim('_').ToUpperInvariant() }
    return (@($Prefix) + @($normalized)) -join '_'
}

function Protect-YamlFile {
    param([System.IO.FileInfo] $File)

    $relativePath = $File.FullName.Substring($resolvedRoot.Length + 1)
    $prefix = Get-EnvironmentPrefix -RelativePath $relativePath
    $raw = [System.IO.File]::ReadAllText($File.FullName)
    $newline = if ($raw.Contains("`r`n")) { "`r`n" } else { "`n" }
    $hasTrailingNewline = $raw.EndsWith("`n")
    $lines = $raw -split "`r?`n"
    $stack = @()
    $changed = $false

    for ($index = 0; $index -lt $lines.Count; $index++) {
        $line = $lines[$index]
        if ($line -notmatch '^(\s*)([A-Za-z0-9_.-]+)(\s*:\s*)(.*)$') {
            continue
        }

        $leadingWhitespace = $Matches[1]
        $indent = $leadingWhitespace.Length
        $key = $Matches[2]
        $separator = $Matches[3]
        $value = $Matches[4]
        $stack = @($stack | Where-Object { $_.Indent -lt $indent })

        if ([string]::IsNullOrWhiteSpace($value) -or $value.TrimStart().StartsWith('#')) {
            $stack += [pscustomobject]@{ Indent = $indent; Key = $key }
            continue
        }

        if (Test-SensitiveKey -Key $key) {
            if ($value -match '^\s*\$\{([A-Za-z_][A-Za-z0-9_]*):[^}]+\}\s*$') {
                $referenceName = $Matches[1]
                $lines[$index] = $leadingWhitespace + $key + $separator + '${' + $referenceName + ':}'
                $changed = $true
            }
            elseif ($value -notmatch '^\s*\$\{[^}]+\}\s*$') {
                $segments = @($stack | ForEach-Object { $_.Key }) + @($key)
                $environmentName = Convert-ToEnvironmentName -Prefix $prefix -Segments $segments
                $lines[$index] = $leadingWhitespace + $key + $separator + '${' + $environmentName + ':}'
                $changed = $true
            }
        }
    }

    if ($changed) {
        $output = $lines -join $newline
        if ($hasTrailingNewline -and -not $output.EndsWith($newline)) {
            $output += $newline
        }
        [System.IO.File]::WriteAllText($File.FullName, $output, [System.Text.UTF8Encoding]::new($false))
    }
}

function Protect-DotEnvFile {
    param([System.IO.FileInfo] $File)

    $raw = [System.IO.File]::ReadAllText($File.FullName)
    $newline = if ($raw.Contains("`r`n")) { "`r`n" } else { "`n" }
    $hasTrailingNewline = $raw.EndsWith("`n")
    $lines = $raw -split "`r?`n"
    $changed = $false

    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=') {
            $key = $Matches[1]
            if (Test-SensitiveKey -Key $key) {
                $lines[$index] = "$key="
                $changed = $true
            }
        }
    }

    if ($changed) {
        $output = $lines -join $newline
        if ($hasTrailingNewline -and -not $output.EndsWith($newline)) {
            $output += $newline
        }
        [System.IO.File]::WriteAllText($File.FullName, $output, [System.Text.UTF8Encoding]::new($false))
    }
}

$yamlFiles = Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File |
    Where-Object {
        $_.FullName -notmatch '\\.git\\' -and
        ($_.Extension -in @('.yml', '.yaml'))
    }
foreach ($file in $yamlFiles) {
    Protect-YamlFile -File $file
}

$dotEnvFiles = Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File |
    Where-Object {
        $_.FullName -notmatch '\\.git\\' -and
        $_.Name -like '.env*'
    }
foreach ($file in $dotEnvFiles) {
    Protect-DotEnvFile -File $file
}

# The imported development snapshot referenced a private runtime host directly.
# Public snapshots must make these endpoints explicit local configuration instead.
$configFiles = @($yamlFiles) + @($dotEnvFiles)
foreach ($file in $configFiles) {
    $raw = [System.IO.File]::ReadAllText($file.FullName)
    $sanitized = [regex]::Replace(
        $raw,
        '(?<![0-9])(?!(?:127\.0\.0\.1|0\.0\.0\.0|255\.255\.255\.255))(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?![0-9])',
        '127.0.0.1'
    )
    if ($sanitized -ne $raw) {
        [System.IO.File]::WriteAllText($file.FullName, $sanitized, [System.Text.UTF8Encoding]::new($false))
    }
}

# Replace embedded URL credentials everywhere they can be represented as text.
# This also normalizes security-test fixtures to an obviously fake pair.
$textExtensions = @(
    '.yml', '.yaml', '.json', '.xml', '.properties', '.env', '.md', '.txt',
    '.java', '.kt', '.ts', '.tsx', '.js', '.jsx', '.cjs', '.mjs', '.py',
    '.ps1', '.cmd', '.sh', '.sql', '.toml', '.ini', '.conf'
)
$textFiles = Get-ChildItem -LiteralPath $resolvedRoot -Recurse -File |
    Where-Object {
        $_.FullName -notmatch '\\.git\\|\\node_modules\\|\\target\\|\\dist\\|\\release\\' -and
        ($textExtensions -contains $_.Extension.ToLowerInvariant() -or $_.Name -like '.env*')
    }
foreach ($file in $textFiles) {
    $raw = [System.IO.File]::ReadAllText($file.FullName)
    $sanitized = [regex]::Replace($raw, '(?i)(://)[^\s/:]+:[^\s/@]+@', '${1}user:password@')
    if ($sanitized -ne $raw) {
        [System.IO.File]::WriteAllText($file.FullName, $sanitized, [System.Text.UTF8Encoding]::new($false))
    }
}

Write-Output "Sanitized public snapshot configuration under $resolvedRoot"
