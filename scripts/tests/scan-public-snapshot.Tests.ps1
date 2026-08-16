$scriptPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'scan-public-snapshot.ps1'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$pwshPath = (Get-Command pwsh -ErrorAction Stop).Source

function Invoke-PublicSnapshotScanner {
    $output = & $pwshPath -NoProfile -NonInteractive -File $scriptPath `
        -RepositoryRoot $repositoryRoot 2>&1
    return [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = ($output | Out-String)
    }
}

Describe 'Public snapshot Git-visible carrier gate' {
    It 'does not read an ignored runtime carrier' {
        $runtimeRoot = Join-Path $repositoryRoot '.runtime'
        $runtimeRootExisted = Test-Path -LiteralPath $runtimeRoot
        New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
        $marker = 'IGNORED_CARRIER_CONTENT_' + [Guid]::NewGuid().ToString('N')
        $carrierPath = Join-Path $runtimeRoot ('scan-public-snapshot-' + [Guid]::NewGuid().ToString('N') + '.p12')
        [System.IO.File]::WriteAllText($carrierPath, $marker, [System.Text.UTF8Encoding]::new($false))
        $handle = [System.IO.File]::Open($carrierPath, 'Open', 'ReadWrite', 'None')

        try {
            & git -C $repositoryRoot check-ignore --quiet -- $carrierPath
            $LASTEXITCODE | Should Be 0

            $result = Invoke-PublicSnapshotScanner

            $result.ExitCode | Should Be 0
            $result.Output | Should Match 'Public snapshot scan passed'
            $result.Output | Should Not Match ([Regex]::Escape($marker))
        }
        finally {
            $handle.Dispose()
            Remove-Item -LiteralPath $carrierPath -Force
            if (-not $runtimeRootExisted) {
                Remove-Item -LiteralPath $runtimeRoot -Force
            }
        }
    }

    It 'rejects a Git-visible dangerous carrier without reading its content' {
        $marker = 'VISIBLE_CARRIER_CONTENT_' + [Guid]::NewGuid().ToString('N')
        $carrierName = 'scan-public-snapshot-' + [Guid]::NewGuid().ToString('N') + '.clixml'
        $carrierRelativePath = 'scripts/tests/' + $carrierName
        $carrierPath = Join-Path $PSScriptRoot $carrierName
        [System.IO.File]::WriteAllText($carrierPath, $marker, [System.Text.UTF8Encoding]::new($false))
        $handle = [System.IO.File]::Open($carrierPath, 'Open', 'ReadWrite', 'None')

        try {
            $visiblePaths = @(& git -C $repositoryRoot ls-files --others --exclude-standard -- $carrierRelativePath)
            ($visiblePaths -contains $carrierRelativePath) | Should Be $true

            $result = Invoke-PublicSnapshotScanner

            $result.ExitCode | Should Be 1
            $result.Output | Should Match 'dangerous-secret-carrier'
            $result.Output | Should Match ([Regex]::Escape($carrierName))
            $result.Output | Should Not Match ([Regex]::Escape($marker))
        }
        finally {
            $handle.Dispose()
            Remove-Item -LiteralPath $carrierPath -Force
        }
    }
}
