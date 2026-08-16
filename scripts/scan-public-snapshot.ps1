[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string] $RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$resolvedRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$scriptRepositoryRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
if ($resolvedRoot -ne $scriptRepositoryRoot) {
    throw "Refusing to scan an unexpected directory: $resolvedRoot"
}
foreach ($marker in @('.git', 'AGENTS.md', 'docs\PROJECT.md')) {
    if (-not (Test-Path -LiteralPath (Join-Path $resolvedRoot $marker))) {
        throw "Refusing to scan a directory without repository marker '$marker': $resolvedRoot"
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

$findings = [System.Collections.Generic.List[object]]::new()

function Add-Finding {
    param(
        [string] $Rule,
        [string] $Path,
        [int] $Line,
        [string] $Key = ''
    )

    $findings.Add([pscustomobject]@{
        Rule = $Rule
        Path = $Path
        Line = $Line
        Key = $Key
    })
}

function Test-SensitiveKey {
    param([string] $Key)
    return $sensitiveKeys -contains $Key.ToLowerInvariant()
}

function Test-SafePlaceholder {
    param([string] $Value)
    return $Value -match '(?i)^(example|sample|placeholder|fake|test|dummy|change[-_]?me|x{4,}|your[_-].*)$'
}

$textExtensions = @(
    '.yml', '.yaml', '.json', '.xml', '.properties', '.env', '.md', '.txt',
    '.java', '.kt', '.ts', '.tsx', '.js', '.jsx', '.cjs', '.mjs', '.py',
    '.ps1', '.cmd', '.sh', '.sql', '.toml', '.ini', '.conf'
)
$dangerousCarrierExtensions = @(
    '.key', '.pem', '.ppk', '.p8', '.pk8', '.p12', '.pfx',
    '.jks', '.keystore', '.kdbx', '.cer', '.crt', '.der', '.p7b', '.p7c',
    '.clixml'
)
$dangerousCarrierNames = @('id_rsa', 'id_dsa', 'id_ecdsa', 'id_ed25519')

$gitVisiblePaths = @(& git -C $resolvedRoot -c core.quotepath=false ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to enumerate Git-visible files for the public snapshot scan.'
}

foreach ($relativePath in $gitVisiblePaths) {
    $normalizedPath = $relativePath.Replace('\', '/')
    $pathSegments = @($normalizedPath -split '/')
    $extension = [System.IO.Path]::GetExtension($normalizedPath).ToLowerInvariant()
    $leafName = [System.IO.Path]::GetFileName($normalizedPath).ToLowerInvariant()

    if (($pathSegments -contains '.local') -or ($pathSegments -contains '.runtime')) {
        Add-Finding -Rule 'local-runtime-carrier' -Path $normalizedPath -Line 0
        continue
    }
    if (($dangerousCarrierExtensions -contains $extension) -or ($dangerousCarrierNames -contains $leafName)) {
        Add-Finding -Rule 'dangerous-secret-carrier' -Path $normalizedPath -Line 0
    }
}

if ($findings.Count -gt 0) {
    $findings |
        Sort-Object Rule, Path, Line |
        ForEach-Object { Write-Output ("{0}|{1}|{2}|{3}" -f $_.Rule, $_.Path, $_.Line, $_.Key) }
    throw "Public snapshot scan found $($findings.Count) forbidden local or secret carrier(s)."
}

$files = $gitVisiblePaths |
    ForEach-Object { Join-Path $resolvedRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
    ForEach-Object { Get-Item -LiteralPath $_ } |
    Where-Object {
        $textExtensions -contains $_.Extension.ToLowerInvariant() -or $_.Name -like '.env*'
    }

foreach ($file in $files) {
    $relativePath = $file.FullName.Substring($resolvedRoot.Length + 1)
    $lineNumber = 0

    foreach ($line in [System.IO.File]::ReadLines($file.FullName)) {
        $lineNumber++

        if ($line -match '(AKIA[0-9A-Z]{16}|LTAI[A-Za-z0-9]{12,}|gh[pousr]_[A-Za-z0-9_]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)') {
            Add-Finding -Rule 'known-secret-signature' -Path $relativePath -Line $lineNumber
        }

        if ($line -match '(?i)://([^\s/:]+):([^\s/@]+)@') {
            $isSafeFixture = $Matches[1] -eq 'user' -and $Matches[2] -eq 'password'
            if (-not $isSafeFixture) {
                Add-Finding -Rule 'credential-in-url' -Path $relativePath -Line $lineNumber
            }
        }

        if ($file.Extension -in @('.yml', '.yaml') -and $line -match '^\s*([A-Za-z0-9_.-]+)\s*:\s*(.*?)\s*(?:#.*)?$') {
            $key = $Matches[1]
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            if (Test-SensitiveKey -Key $key) {
                $isSafeReference = $value -match '^\$\{[^}:]+(?::)?\}$'
                if ($value -and -not $isSafeReference -and -not (Test-SafePlaceholder -Value $value)) {
                    Add-Finding -Rule 'sensitive-yaml-value' -Path $relativePath -Line $lineNumber -Key $key
                }
            }
        }

        if ($file.Name -like '.env*' -and $line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $key = $Matches[1]
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            if ((Test-SensitiveKey -Key $key) -and $value -and -not (Test-SafePlaceholder -Value $value)) {
                Add-Finding -Rule 'sensitive-dotenv-value' -Path $relativePath -Line $lineNumber -Key $key
            }
        }

        if (($file.Extension -in @('.yml', '.yaml') -or $file.Name -like '.env*') -and
            $line -match '(?<![0-9])(?:10\.(?:[0-9]{1,3}\.){2}[0-9]{1,3}|192\.168\.(?:[0-9]{1,3}\.)[0-9]{1,3}|172\.(?:1[6-9]|2[0-9]|3[01])\.(?:[0-9]{1,3}\.)[0-9]{1,3})(?![0-9])') {
            Add-Finding -Rule 'private-ip-in-config' -Path $relativePath -Line $lineNumber
        }
    }
}

if ($findings.Count -gt 0) {
    $findings |
        Sort-Object Rule, Path, Line |
        ForEach-Object { Write-Output ("{0}|{1}|{2}|{3}" -f $_.Rule, $_.Path, $_.Line, $_.Key) }
    throw "Public snapshot scan found $($findings.Count) potential secret or private configuration item(s)."
}

Write-Output "Public snapshot scan passed: no high-confidence secret signatures or private config addresses found."
