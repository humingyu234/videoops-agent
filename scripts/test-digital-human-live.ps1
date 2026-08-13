[CmdletBinding()]
param(
    [ValidateRange(10, 1800)]
    [int]$TimeoutSeconds = 600,

    [ValidateRange(1, 30)]
    [int]$PollIntervalSeconds = 2,

    [string]$ReferenceAudioPath,

    [string]$PortraitImagePath,

    [switch]$KeepArtifacts
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not ('DigitalHumanLive.CancellationDisposal' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Threading;

namespace DigitalHumanLive
{
    public static class CancellationDisposal
    {
        public static CancellationTokenRegistration Register(
            CancellationToken token, IDisposable resource)
        {
            return token.Register(state => ((IDisposable)state).Dispose(), resource);
        }
    }
}
'@
}

$maxJsonBytes = 1MB
$maxInputBytes = 10MB
$maxWaveBytes = 32MB
$maxVideoBytes = 128MB
$uploadSubfolder = 'digital-human'

function Get-RequiredEnvironmentValue {
    param([Parameter(Mandatory)][string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required environment variable is missing: $Name"
    }
    return $value.Trim()
}

function Get-OptionalEnvironmentValue {
    param([Parameter(Mandatory)][string]$Name)

    return [Environment]::GetEnvironmentVariable($Name, 'Process')
}

function Import-PemCertificates {
    param([Parameter(Mandatory)][string]$Path)

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $certificateFile = Get-Item -LiteralPath $resolvedPath
    if ($certificateFile.Length -le 0 -or $certificateFile.Length -gt $maxJsonBytes) {
        throw 'IndexTTS2 CA certificate file must be between 1 byte and 1 MiB'
    }
    $pem = Get-Content -Raw -Encoding utf8 -LiteralPath $resolvedPath
    $matches = [regex]::Matches(
        $pem,
        '-----BEGIN CERTIFICATE-----\s*(?<body>[A-Za-z0-9+/=\r\n]+?)\s*-----END CERTIFICATE-----')
    if ($matches.Count -eq 0) {
        throw 'IndexTTS2 CA certificate file does not contain a PEM certificate'
    }

    $certificates = [Collections.Generic.List[Security.Cryptography.X509Certificates.X509Certificate2]]::new()
    try {
        foreach ($match in $matches) {
            $der = [Convert]::FromBase64String(($match.Groups['body'].Value -replace '\s', ''))
            $certificates.Add([Security.Cryptography.X509Certificates.X509Certificate2]::new($der))
        }
        if (-not ($certificates | Where-Object { $_.Subject -eq $_.Issuer })) {
            throw 'IndexTTS2 CA certificate file must contain a self-signed root certificate'
        }
        return $certificates.ToArray()
    }
    catch {
        foreach ($certificate in $certificates) {
            $certificate.Dispose()
        }
        throw
    }
}

function New-StrictCustomTrustHandler {
    param(
        [Parameter(Mandatory)]
        [Security.Cryptography.X509Certificates.X509Certificate2[]]$Certificates
    )

    if (-not ('DigitalHumanLive.StrictCustomRootTrust' -as [type])) {
        Add-Type -ReferencedAssemblies 'System.Net.Http.dll' -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Security;
using System.Reflection;
using System.Security.Cryptography.X509Certificates;

namespace DigitalHumanLive
{
    public sealed class StrictCustomRootTrust
    {
        private readonly X509Certificate2[] roots;
        private readonly X509Certificate2[] intermediates;

        public StrictCustomRootTrust(IEnumerable<X509Certificate2> certificates)
        {
            X509Certificate2[] values = certificates == null
                ? Array.Empty<X509Certificate2>()
                : certificates.ToArray();
            roots = values.Where(value => value.Subject == value.Issuer).ToArray();
            intermediates = values.Where(value => value.Subject != value.Issuer).ToArray();
            if (roots.Length == 0)
            {
                throw new ArgumentException("At least one self-signed root certificate is required.");
            }
        }

        public Func<HttpRequestMessage, X509Certificate2, X509Chain, SslPolicyErrors, bool> Callback
        {
            get { return Validate; }
        }

        private bool Validate(HttpRequestMessage request, X509Certificate2 certificate,
            X509Chain suppliedChain, SslPolicyErrors errors)
        {
            if (certificate == null
                || (errors & SslPolicyErrors.RemoteCertificateNotAvailable) != 0
                || (errors & SslPolicyErrors.RemoteCertificateNameMismatch) != 0)
            {
                return false;
            }

            using (X509Chain chain = new X509Chain())
            {
                chain.ChainPolicy.RevocationMode = X509RevocationMode.NoCheck;
                chain.ChainPolicy.VerificationFlags = X509VerificationFlags.NoFlag;
                foreach (X509Certificate2 intermediate in intermediates)
                {
                    chain.ChainPolicy.ExtraStore.Add(intermediate);
                }

                PropertyInfo trustMode = chain.ChainPolicy.GetType().GetProperty("TrustMode");
                PropertyInfo customTrustStore = chain.ChainPolicy.GetType().GetProperty("CustomTrustStore");
                bool hasCustomRootTrust = trustMode != null && customTrustStore != null;
                if (hasCustomRootTrust)
                {
                    trustMode.SetValue(chain.ChainPolicy,
                        Enum.Parse(trustMode.PropertyType, "CustomRootTrust"), null);
                    X509Certificate2Collection store =
                        (X509Certificate2Collection)customTrustStore.GetValue(chain.ChainPolicy, null);
                    store.AddRange(roots);
                }
                else
                {
                    chain.ChainPolicy.ExtraStore.AddRange(roots);
                    chain.ChainPolicy.VerificationFlags =
                        X509VerificationFlags.AllowUnknownCertificateAuthority;
                }

                if (!chain.Build(certificate) || chain.ChainElements.Count == 0)
                {
                    return false;
                }
                byte[] chainRoot = chain.ChainElements[chain.ChainElements.Count - 1].Certificate.RawData;
                return roots.Any(root => root.RawData.SequenceEqual(chainRoot));
            }
        }
    }
}
'@
    }

    $handler = [Net.Http.HttpClientHandler]::new()
    $validator = [DigitalHumanLive.StrictCustomRootTrust]::new($Certificates)
    $handler.ServerCertificateCustomValidationCallback = $validator.Callback
    return [pscustomobject]@{
        Handler = $handler
        Validator = $validator
    }
}

function Assert-SafeProviderUri {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Name
    )

    $uri = [Uri]$Value
    if (-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo) {
        throw "$Name must be a valid HTTP(S) service URL"
    }
    if ($uri.Scheme -eq 'http' -and $uri.Host -notin @('127.0.0.1', 'localhost', '::1')) {
        throw "$Name must use HTTPS; HTTP is restricted to loopback hosts"
    }
    return $uri
}

function Add-BasicAuthorization {
    param(
        [Parameter(Mandatory)][System.Net.Http.HttpRequestMessage]$Request,
        [string]$User,
        [string]$Password
    )

    if ([string]::IsNullOrWhiteSpace($User) -and [string]::IsNullOrWhiteSpace($Password)) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($User) -or [string]::IsNullOrWhiteSpace($Password)) {
        throw 'Basic Auth user and password must be configured together'
    }
    $raw = [Text.Encoding]::UTF8.GetBytes("${User}:$Password")
    $Request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new(
        'Basic', [Convert]::ToBase64String($raw))
}

function New-LinkedDeadlineCancellation {
    param(
        [Parameter(Mandatory)][Threading.CancellationToken]$ParentToken,
        [Parameter(Mandatory)][int]$Seconds
    )

    $source = [Threading.CancellationTokenSource]::CreateLinkedTokenSource($ParentToken)
    $source.CancelAfter([TimeSpan]::FromSeconds($Seconds))
    return $source
}

function Copy-LimitedResponse {
    param(
        [Parameter(Mandatory)][System.Net.Http.HttpResponseMessage]$Response,
        [Parameter(Mandatory)][string]$Destination,
        [Parameter(Mandatory)][long]$MaximumBytes,
        [Parameter(Mandatory)][string]$FailureMessage,
        [Parameter(Mandatory)][Threading.CancellationToken]$CancellationToken
    )

    if (-not $Response.IsSuccessStatusCode) {
        throw "$FailureMessage; HTTP status: $([int]$Response.StatusCode)"
    }
    $declaredLength = $Response.Content.Headers.ContentLength
    if ($null -ne $declaredLength -and $declaredLength -gt $MaximumBytes) {
        throw "$FailureMessage; response exceeds the size limit"
    }

    $source = $Response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $sourceCancellation = $null
    $responseCancellation = $null
    $target = $null
    try {
        $sourceCancellation = [DigitalHumanLive.CancellationDisposal]::Register(
            $CancellationToken, $source)
        $responseCancellation = [DigitalHumanLive.CancellationDisposal]::Register(
            $CancellationToken, $Response)
        $CancellationToken.ThrowIfCancellationRequested()
        $target = [IO.File]::Open(
            $Destination, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $buffer = New-Object byte[] 81920
        [long]$total = 0
        while ($true) {
            $remainingWithSentinel = [Math]::Min(
                [long]$buffer.Length, ($MaximumBytes - $total) + 1L)
            $read = $source.ReadAsync(
                $buffer, 0, [int]$remainingWithSentinel, $CancellationToken).GetAwaiter().GetResult()
            if ($read -le 0) { break }
            $total += $read
            if ($total -gt $MaximumBytes) {
                throw "$FailureMessage; response exceeds the size limit"
            }
            $target.WriteAsync(
                $buffer, 0, $read, $CancellationToken).GetAwaiter().GetResult()
        }
        if ($total -eq 0) {
            throw "$FailureMessage; response is empty"
        }
    }
    catch {
        if ($CancellationToken.IsCancellationRequested) {
            throw [OperationCanceledException]::new(
                "$FailureMessage; response deadline exceeded", $_.Exception, $CancellationToken)
        }
        throw
    }
    finally {
        if ($null -ne $responseCancellation) { $responseCancellation.Dispose() }
        if ($null -ne $sourceCancellation) { $sourceCancellation.Dispose() }
        if ($null -ne $target) { $target.Dispose() }
        $source.Dispose()
    }
}

function Read-LimitedJsonResponse {
    param(
        [Parameter(Mandatory)][System.Net.Http.HttpResponseMessage]$Response,
        [Parameter(Mandatory)][string]$TemporaryPath,
        [Parameter(Mandatory)][string]$FailureMessage,
        [Parameter(Mandatory)][Threading.CancellationToken]$CancellationToken
    )

    try {
        Copy-LimitedResponse -Response $Response -Destination $TemporaryPath `
            -MaximumBytes $maxJsonBytes -FailureMessage $FailureMessage `
            -CancellationToken $CancellationToken
        try {
            return Get-Content -Raw -Encoding utf8 -LiteralPath $TemporaryPath | ConvertFrom-Json
        }
        catch {
            throw "$FailureMessage; response is not valid JSON"
        }
    }
    finally {
        Remove-Item -LiteralPath $TemporaryPath -Force -ErrorAction SilentlyContinue
    }
}

function New-TestWave {
    param([Parameter(Mandatory)][string]$Path)

    $sampleRate = 16000
    $sampleCount = $sampleRate
    $dataLength = $sampleCount * 2
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew)
    $writer = [IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write([int](36 + $dataLength))
        $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write([int]16)
        $writer.Write([int16]1)
        $writer.Write([int16]1)
        $writer.Write([int]$sampleRate)
        $writer.Write([int]($sampleRate * 2))
        $writer.Write([int16]2)
        $writer.Write([int16]16)
        $writer.Write([Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write([int]$dataLength)
        for ($index = 0; $index -lt $sampleCount; $index++) {
            $sample = [int16](1200 * [Math]::Sin(2 * [Math]::PI * 220 * $index / $sampleRate))
            $writer.Write($sample)
        }
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

function New-TestPortrait {
    param([Parameter(Mandatory)][string]$Path)

    Add-Type -AssemblyName System.Drawing
    $bitmap = [Drawing.Bitmap]::new(512, 512)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([Drawing.Color]::FromArgb(238, 244, 252))
        $skin = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(244, 196, 156))
        $dark = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(45, 55, 72))
        $mouth = [Drawing.Pen]::new([Drawing.Color]::FromArgb(170, 70, 75), 8)
        try {
            $graphics.FillEllipse($skin, 116, 70, 280, 350)
            $graphics.FillEllipse($dark, 188, 205, 24, 30)
            $graphics.FillEllipse($dark, 300, 205, 24, 30)
            $graphics.DrawArc($mouth, 200, 275, 112, 70, 10, 160)
        }
        finally {
            $skin.Dispose()
            $dark.Dispose()
            $mouth.Dispose()
        }
        $bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
    }
    finally {
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Assert-WaveFile {
    param([Parameter(Mandatory)][string]$Path)

    $stream = [IO.File]::OpenRead($Path)
    try {
        $header = New-Object byte[] 12
        if ($stream.Read($header, 0, $header.Length) -ne $header.Length `
            -or [Text.Encoding]::ASCII.GetString($header, 0, 4) -ne 'RIFF' `
            -or [Text.Encoding]::ASCII.GetString($header, 8, 4) -ne 'WAVE') {
            throw 'IndexTTS2 output is not a valid WAV file'
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Assert-InputFileSize {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Label
    )

    $length = (Get-Item -LiteralPath $Path).Length
    if ($length -le 0 -or $length -gt $maxInputBytes) {
        throw "$Label must be between 1 byte and 10 MiB"
    }
}

function Assert-PngFile {
    param([Parameter(Mandatory)][string]$Path)

    $stream = [IO.File]::OpenRead($Path)
    try {
        $header = New-Object byte[] 8
        $expected = [byte[]](0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        if ($stream.Read($header, 0, $header.Length) -ne $header.Length) {
            throw 'Portrait input is not a valid PNG file'
        }
        for ($index = 0; $index -lt $expected.Length; $index++) {
            if ($header[$index] -ne $expected[$index]) {
                throw 'Portrait input is not a valid PNG file'
            }
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Assert-Mp4File {
    param([Parameter(Mandatory)][string]$Path)

    $stream = [IO.File]::OpenRead($Path)
    try {
        $header = New-Object byte[] 12
        if ($stream.Read($header, 0, $header.Length) -ne $header.Length `
            -or [Text.Encoding]::ASCII.GetString($header, 4, 4) -ne 'ftyp') {
            throw 'ComfyUI output is not a valid MP4 file'
        }
    }
    finally {
        $stream.Dispose()
    }
}

function Find-Mp4Output {
    param($Node)

    if ($null -eq $Node) {
        return $null
    }
    if ($Node -is [Array]) {
        foreach ($child in $Node) {
            $found = Find-Mp4Output $child
            if ($null -ne $found) { return $found }
        }
        return $null
    }
    if ($Node -is [pscustomobject]) {
        $filenameProperty = $Node.PSObject.Properties['filename']
        $formatProperty = $Node.PSObject.Properties['format']
        $filename = if ($null -eq $filenameProperty) { '' } else { [string]$filenameProperty.Value }
        $format = if ($null -eq $formatProperty) { '' } else { [string]$formatProperty.Value }
        if ($filename.EndsWith('.mp4', [StringComparison]::OrdinalIgnoreCase) -or $format -match '(?i)mp4') {
            $subfolder = $Node.PSObject.Properties['subfolder']
            $type = $Node.PSObject.Properties['type']
            return [pscustomobject]@{
                filename = $filename
                subfolder = if ($null -eq $subfolder) { '' } else { [string]$subfolder.Value }
                type = if ($null -eq $type) { 'output' } else { [string]$type.Value }
            }
        }
        foreach ($property in $Node.PSObject.Properties) {
            $found = Find-Mp4Output $property.Value
            if ($null -ne $found) { return $found }
        }
    }
    return $null
}

function Get-JsonPropertyValue {
    param(
        $Object,
        [Parameter(Mandatory)][string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }
    if ($Object -is [Collections.IDictionary]) {
        if ($Object.Contains($Name)) {
            Write-Output -NoEnumerate $Object[$Name]
            return
        }
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    Write-Output -NoEnumerate $property.Value
}

function Convert-ComfyWorkflow {
    param([Parameter(Mandatory)]$Workflow)

    $nodes = Get-JsonPropertyValue $Workflow 'nodes'
    $links = Get-JsonPropertyValue $Workflow 'links'
    if ($nodes -isnot [Collections.IList] -or $links -isnot [Collections.IList]) {
        throw 'ComfyUI workflow must contain node and link arrays'
    }

    $linkSources = @{}
    foreach ($link in $links) {
        if ($link -isnot [Collections.IList] -or $link.Count -lt 3) {
            throw 'ComfyUI workflow contains an invalid link'
        }
        [long]$linkId = 0
        [long]$originNodeId = 0
        [int]$originOutputIndex = 0
        if (-not [long]::TryParse([string]$link[0], [ref]$linkId) `
            -or -not [long]::TryParse([string]$link[1], [ref]$originNodeId) `
            -or -not [int]::TryParse([string]$link[2], [ref]$originOutputIndex)) {
            throw 'ComfyUI workflow contains an invalid link'
        }
        $linkSources[[string]$linkId] = @([string]$originNodeId, $originOutputIndex)
    }

    $prompt = [ordered]@{}
    foreach ($node in $nodes) {
        $nodeId = [string](Get-JsonPropertyValue $node 'id')
        $classType = [string](Get-JsonPropertyValue $node 'type')
        $modeValue = Get-JsonPropertyValue $node 'mode'
        [int]$mode = 0
        if ($null -ne $modeValue -and -not [int]::TryParse([string]$modeValue, [ref]$mode)) {
            throw 'ComfyUI workflow contains an invalid node mode'
        }
        $inputs = Get-JsonPropertyValue $node 'inputs'
        if ($nodeId -notmatch '^[0-9]{1,20}$' -or [string]::IsNullOrWhiteSpace($classType) `
            -or $mode -ne 0 -or $inputs -isnot [Collections.IList] -or $prompt.Contains($nodeId)) {
            throw 'ComfyUI workflow contains an invalid node'
        }

        $apiInputs = [ordered]@{}
        $widgetValues = Get-JsonPropertyValue $node 'widgets_values'
        $widgetIndex = 0
        foreach ($input in $inputs) {
            $inputName = [string](Get-JsonPropertyValue $input 'name')
            if ([string]::IsNullOrWhiteSpace($inputName) -or $apiInputs.Contains($inputName)) {
                throw 'ComfyUI workflow contains an invalid node input'
            }

            $widgetValue = $null
            $widget = Get-JsonPropertyValue $input 'widget'
            if ($null -ne $widget) {
                if ($widgetValues -is [Collections.IList]) {
                    if ($widgetIndex -lt $widgetValues.Count) {
                        $widgetValue = $widgetValues[$widgetIndex]
                    }
                    $widgetIndex++
                    if ($inputName -eq 'seed' -and $widgetIndex -lt $widgetValues.Count `
                        -and [string]$widgetValues[$widgetIndex] -in @(
                            'fixed', 'increment', 'decrement', 'randomize')) {
                        $widgetIndex++
                    }
                }
                elseif ($null -ne $widgetValues) {
                    $widgetValue = Get-JsonPropertyValue $widgetValues $inputName
                }
            }

            $linkIdValue = Get-JsonPropertyValue $input 'link'
            if ($null -ne $linkIdValue) {
                [long]$linkedId = 0
                if (-not [long]::TryParse([string]$linkIdValue, [ref]$linkedId)) {
                    throw 'ComfyUI workflow contains an invalid node link'
                }
                $source = $linkSources[[string]$linkedId]
                if ($null -eq $source) {
                    throw 'ComfyUI workflow references a missing link'
                }
                $apiInputs[$inputName] = @($source[0], [int]$source[1])
            }
            elseif ($inputName -notin @('upload', 'audioUI') -and $null -ne $widgetValue) {
                $apiInputs[$inputName] = $widgetValue
            }
        }

        $title = [string](Get-JsonPropertyValue $node 'title')
        if ([string]::IsNullOrWhiteSpace($title)) { $title = $classType }
        $prompt[$nodeId] = [ordered]@{
            class_type = $classType
            inputs = $apiInputs
            _meta = [ordered]@{ title = $title }
        }
    }
    return $prompt
}

function Get-UniqueComfyInputs {
    param(
        [Parameter(Mandatory)][Collections.IDictionary]$Prompt,
        [Parameter(Mandatory)][string]$ClassType
    )

    $selected = $null
    foreach ($entry in $Prompt.GetEnumerator()) {
        $node = $entry.Value
        if ([string](Get-JsonPropertyValue $node 'class_type') -eq $ClassType) {
            if ($null -ne $selected) {
                throw "ComfyUI workflow contains multiple $ClassType nodes"
            }
            $selected = Get-JsonPropertyValue $node 'inputs'
        }
    }
    if ($selected -isnot [Collections.IDictionary]) {
        throw "ComfyUI workflow does not contain a usable $ClassType node"
    }
    Write-Output -NoEnumerate $selected
}

function Set-UniqueComfyInput {
    param(
        [Parameter(Mandatory)][Collections.IDictionary]$Prompt,
        [Parameter(Mandatory)][string]$ClassType,
        [Parameter(Mandatory)][string]$InputName,
        [Parameter(Mandatory)]$Value
    )

    $selected = Get-UniqueComfyInputs $Prompt $ClassType
    $selected[$InputName] = $Value
}

function Get-WaveFrameCount {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][ValidateRange(1, 120)][double]$FrameRate
    )

    $bytes = [IO.File]::ReadAllBytes($Path)
    if ($bytes.Length -lt 44 -or [Text.Encoding]::ASCII.GetString($bytes, 0, 4) -ne 'RIFF' `
        -or [Text.Encoding]::ASCII.GetString($bytes, 8, 4) -ne 'WAVE') {
        throw 'IndexTTS2 output is not a valid WAV file'
    }
    [long]$byteRate = -1
    [long]$dataSize = -1
    [long]$offset = 12
    while ($offset -le $bytes.Length - 8) {
        $tag = [Text.Encoding]::ASCII.GetString($bytes, [int]$offset, 4)
        [long]$chunkSize = [BitConverter]::ToUInt32($bytes, [int]$offset + 4)
        $dataOffset = $offset + 8
        $nextOffset = $dataOffset + $chunkSize + ($chunkSize -band 1)
        if ($chunkSize -gt [int]::MaxValue -or $nextOffset -gt $bytes.Length) {
            throw 'IndexTTS2 output is not a valid WAV file'
        }
        if ($tag -eq 'fmt ') {
            if ($chunkSize -lt 12) { throw 'IndexTTS2 output is not a valid WAV file' }
            $byteRate = [BitConverter]::ToUInt32($bytes, [int]$offset + 16)
        }
        elseif ($tag -eq 'data') {
            $dataSize = $chunkSize
        }
        $offset = $nextOffset
    }
    if ($byteRate -le 0 -or $dataSize -le 0) {
        throw 'IndexTTS2 output is not a valid WAV file'
    }
    [long]$rawFrames = [Math]::Ceiling($dataSize * $FrameRate / $byteRate)
    if ($rawFrames -le 0 -or $rawFrames -gt 432000) {
        throw 'IndexTTS2 output duration is invalid'
    }
    return [int](([Math]::Floor(($rawFrames - 1 + 3) / 4)) * 4 + 1)
}

function Invoke-ComfyUpload {
    param(
        [Parameter(Mandatory)][Net.Http.HttpClient]$Client,
        [Parameter(Mandatory)][Uri]$BaseUri,
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Role,
        [Parameter(Mandatory)][string]$ContentType,
        [string]$BasicUser,
        [string]$BasicPassword,
        [Parameter(Mandatory)][string]$TemporaryRoot,
        [Parameter(Mandatory)][Threading.CancellationToken]$ParentToken,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )

    $extension = if ($Role -eq 'portrait') { '.png' } else { '.wav' }
    $fileName = "digital-human-$Role-$([Guid]::NewGuid().ToString('N'))$extension"
    $request = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Post, [Uri]::new($BaseUri, '/upload/image'))
    $request.Headers.Accept.Add([Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new('application/json'))
    Add-BasicAuthorization $request $BasicUser $BasicPassword
    $multipart = [Net.Http.MultipartFormDataContent]::new()
    $stream = [IO.File]::OpenRead($Path)
    $content = [Net.Http.StreamContent]::new($stream)
    $content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new($ContentType)
    $multipart.Add($content, 'image', $fileName)
    $multipart.Add([Net.Http.StringContent]::new($uploadSubfolder), 'subfolder')
    $multipart.Add([Net.Http.StringContent]::new('input'), 'type')
    $multipart.Add([Net.Http.StringContent]::new('false'), 'overwrite')
    $request.Content = $multipart
    $deadline = New-LinkedDeadlineCancellation -ParentToken $ParentToken -Seconds $TimeoutSeconds
    try {
        $response = $Client.SendAsync(
            $request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
            $deadline.Token).GetAwaiter().GetResult()
        try {
            $result = Read-LimitedJsonResponse -Response $response `
                -TemporaryPath (Join-Path $TemporaryRoot "upload-$Role.json") `
                -FailureMessage 'ComfyUI input upload failed' -CancellationToken $deadline.Token
        }
        finally {
            $response.Dispose()
        }
    }
    finally {
        $deadline.Dispose()
        $request.Dispose()
        $stream.Dispose()
    }

    $name = [string](Get-JsonPropertyValue $result 'name')
    $subfolder = [string](Get-JsonPropertyValue $result 'subfolder')
    $type = [string](Get-JsonPropertyValue $result 'type')
    if ([string]::IsNullOrWhiteSpace($name) -or $name.Contains('/') -or $name.Contains('\') `
        -or $name.Contains('..') -or $subfolder -ne $uploadSubfolder -or $type -ne 'input') {
        throw 'ComfyUI returned an invalid upload result'
    }
    return "$subfolder/$name"
}

function Invoke-IndexTtsWithPython {
    param(
        [Parameter(Mandatory)][Uri]$BaseUri,
        [Parameter(Mandatory)][string]$ReferencePath,
        [Parameter(Mandatory)][string]$OutputPath,
        [Parameter(Mandatory)][string]$CaCertificatePath,
        [Parameter(Mandatory)][string]$TemporaryRoot,
        [Parameter(Mandatory)][int]$TimeoutSeconds,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($null -eq $python) {
        throw 'Python is required for strict custom CA validation on Windows PowerShell'
    }
    $helperPath = Join-Path $TemporaryRoot 'strict-index-request.py'
    $helper = @'
import argparse
import base64
import os
import ssl
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def append_field(body, boundary, name, value):
    body.extend((f"--{boundary}\r\n"
                 f"Content-Disposition: form-data; name=\"{name}\"\r\n\r\n"
                 f"{value}\r\n").encode("utf-8"))


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--ca", required=True)
    parser.add_argument("--timeout", required=True, type=int)
    parser.add_argument("--maximum-bytes", required=True, type=int)
    args = parser.parse_args()
    api_key = os.environ.get("DEMO_INDEXTTS_API_KEY", "")
    basic_user = os.environ.get("DEMO_INDEXTTS_BASIC_USER", "")
    basic_password = os.environ.get("DEMO_INDEXTTS_BASIC_PASSWORD", "")
    if not api_key or bool(basic_user) != bool(basic_password):
        raise RuntimeError("IndexTTS2 authentication environment is invalid")

    reference = Path(args.reference).read_bytes()
    boundary = "----AiVideo" + uuid.uuid4().hex
    body = bytearray()
    append_field(body, boundary, "text", "Test.")
    body.extend((f"--{boundary}\r\n"
                 "Content-Disposition: form-data; name=\"reference_audio\"; filename=\"reference.wav\"\r\n"
                 "Content-Type: audio/wav\r\n\r\n").encode("ascii"))
    body.extend(reference)
    body.extend(f"\r\n--{boundary}--\r\n".encode("ascii"))

    request = urllib.request.Request(
        args.base_url.rstrip("/") + "/v1/indextts2/clone", data=bytes(body), method="POST")
    request.add_header("Accept", "audio/wav")
    request.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    request.add_header("X-API-Key", api_key)
    if basic_user:
        credentials = base64.b64encode(f"{basic_user}:{basic_password}".encode("utf-8")).decode("ascii")
        request.add_header("Authorization", "Basic " + credentials)
    context = ssl.create_default_context(cafile=args.ca)
    opener = urllib.request.build_opener(
        urllib.request.HTTPSHandler(context=context), NoRedirectHandler())
    try:
        with opener.open(request, timeout=args.timeout) as response:
            media_type = response.headers.get_content_type()
            declared = response.headers.get("Content-Length")
            if response.status != 200 or media_type != "audio/wav":
                raise RuntimeError("IndexTTS2 response status or media type is invalid")
            if declared is not None and int(declared) > args.maximum_bytes:
                raise RuntimeError("IndexTTS2 response exceeds the size limit")
            content = response.read(args.maximum_bytes + 1)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"IndexTTS2 request failed with HTTP {error.code}") from None
    if not content or len(content) > args.maximum_bytes:
        raise RuntimeError("IndexTTS2 response is empty or exceeds the size limit")
    Path(args.output).write_bytes(content)


try:
    main()
except Exception as error:
    print(f"IndexTTS2 strict request failed: {error}", file=sys.stderr)
    raise SystemExit(1)
'@
    [IO.File]::WriteAllText($helperPath, $helper, [Text.UTF8Encoding]::new($false))
    & $python.Source $helperPath --base-url $BaseUri.AbsoluteUri --reference $ReferencePath `
        --output $OutputPath --ca $CaCertificatePath --timeout $TimeoutSeconds `
        --maximum-bytes $MaximumBytes
    if ($LASTEXITCODE -ne 0) {
        throw 'IndexTTS2 strict request failed'
    }
}

Add-Type -AssemblyName System.Net.Http

$indexBaseUri = Assert-SafeProviderUri `
    -Value (Get-RequiredEnvironmentValue 'DEMO_INDEXTTS_BASE_URL') -Name 'DEMO_INDEXTTS_BASE_URL'
$comfyBaseUri = Assert-SafeProviderUri `
    -Value (Get-RequiredEnvironmentValue 'DEMO_COMFY_BASE_URL') -Name 'DEMO_COMFY_BASE_URL'
$indexApiKey = Get-RequiredEnvironmentValue 'DEMO_INDEXTTS_API_KEY'
$indexUser = Get-OptionalEnvironmentValue 'DEMO_INDEXTTS_BASIC_USER'
$indexPassword = Get-OptionalEnvironmentValue 'DEMO_INDEXTTS_BASIC_PASSWORD'
$indexCaCertificatePath = Get-OptionalEnvironmentValue 'DEMO_INDEXTTS_CA_CERTIFICATE'
$comfyUser = Get-OptionalEnvironmentValue 'DEMO_COMFY_BASIC_USER'
$comfyPassword = Get-OptionalEnvironmentValue 'DEMO_COMFY_BASIC_PASSWORD'
$comfyWorkflowFile = Get-OptionalEnvironmentValue 'DEMO_COMFY_WORKFLOW_FILE'
if ([string]::IsNullOrWhiteSpace($comfyWorkflowFile)) {
    $comfyWorkflowFile = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String('5pWw5a2X5Lq65Y+j5pKtLmpzb24='))
}
else {
    $comfyWorkflowFile = $comfyWorkflowFile.Trim()
}
$comfyWorkflowId = Get-OptionalEnvironmentValue 'DEMO_COMFY_WORKFLOW_ID'
if ([string]::IsNullOrWhiteSpace($comfyWorkflowId)) {
    $comfyWorkflowId = '8b7a9a57-2303-4ef5-9fc2-bf41713bd1fc'
}
else {
    $comfyWorkflowId = $comfyWorkflowId.Trim()
}
if ($comfyWorkflowFile.Length -gt 255 -or $comfyWorkflowFile -match '[/\\]' `
    -or $comfyWorkflowFile.Contains('..') -or -not $comfyWorkflowFile.EndsWith('.json')) {
    throw 'DEMO_COMFY_WORKFLOW_FILE must be a simple JSON filename'
}
if ($comfyWorkflowId -notmatch '^[A-Fa-f0-9-]{36}$') {
    throw 'DEMO_COMFY_WORKFLOW_ID must be a UUID'
}
if (-not [string]::IsNullOrWhiteSpace($indexCaCertificatePath) -and $indexBaseUri.Scheme -ne 'https') {
    throw 'DEMO_INDEXTTS_CA_CERTIFICATE is only valid for an HTTPS IndexTTS2 URL'
}

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gitCommonDirectory = (& git -C $repositoryRoot rev-parse --git-common-dir).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitCommonDirectory)) {
    throw 'Unable to locate the Git metadata directory'
}
if (-not [IO.Path]::IsPathRooted($gitCommonDirectory)) {
    $gitCommonDirectory = [IO.Path]::GetFullPath((Join-Path $repositoryRoot $gitCommonDirectory))
}
$temporaryRoot = Join-Path $gitCommonDirectory ('digital-human-live\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temporaryRoot | Out-Null

$referenceAudio = if ($ReferenceAudioPath) {
    (Resolve-Path -LiteralPath $ReferenceAudioPath).Path
} else {
    $generated = Join-Path $temporaryRoot 'reference.wav'
    New-TestWave $generated
    $generated
}
$portraitImage = if ($PortraitImagePath) {
    (Resolve-Path -LiteralPath $PortraitImagePath).Path
} else {
    $generated = Join-Path $temporaryRoot 'portrait.png'
    New-TestPortrait $generated
    $generated
}

Assert-InputFileSize $referenceAudio 'Reference audio'
Assert-WaveFile $referenceAudio
Assert-InputFileSize $portraitImage 'Portrait input'
Assert-PngFile $portraitImage

$indexCaCertificates = @()
$usePythonIndexClient = -not [string]::IsNullOrWhiteSpace($indexCaCertificatePath)
if ([string]::IsNullOrWhiteSpace($indexCaCertificatePath)) {
    $indexHandler = [Net.Http.HttpClientHandler]::new()
    $indexHandler.AllowAutoRedirect = $false
    $indexHttpClient = [Net.Http.HttpClient]::new($indexHandler, $true)
    $indexHttpClient.Timeout = [Threading.Timeout]::InfiniteTimeSpan
}
else {
    $indexCaCertificatePath = (Resolve-Path -LiteralPath $indexCaCertificatePath.Trim()).Path
    $indexCaCertificates = @(Import-PemCertificates $indexCaCertificatePath)
    $indexHttpClient = $null
}
$comfyHandler = [Net.Http.HttpClientHandler]::new()
$comfyHandler.AllowAutoRedirect = $false
$comfyHttpClient = [Net.Http.HttpClient]::new($comfyHandler, $true)
$comfyHttpClient.Timeout = [Threading.Timeout]::InfiniteTimeSpan
$voiceOutput = Join-Path $temporaryRoot 'voice.wav'
$videoOutput = Join-Path $temporaryRoot 'video.mp4'
$runCancellation = [Threading.CancellationTokenSource]::new()

try {
    if ($usePythonIndexClient) {
        Invoke-IndexTtsWithPython -BaseUri $indexBaseUri -ReferencePath $referenceAudio `
            -OutputPath $voiceOutput -CaCertificatePath $indexCaCertificatePath `
            -TemporaryRoot $temporaryRoot -TimeoutSeconds $TimeoutSeconds -MaximumBytes $maxWaveBytes
    }
    else {
        $voiceRequest = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Post, [Uri]::new($indexBaseUri, '/v1/indextts2/clone'))
        $voiceRequest.Headers.Accept.Add([Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new('audio/wav'))
        $voiceRequest.Headers.Add('X-API-Key', $indexApiKey)
        Add-BasicAuthorization $voiceRequest $indexUser $indexPassword
        $voiceMultipart = [Net.Http.MultipartFormDataContent]::new()
        $voiceMultipart.Add([Net.Http.StringContent]::new('Test.'), 'text')
        $referenceStream = [IO.File]::OpenRead($referenceAudio)
        $referenceContent = [Net.Http.StreamContent]::new($referenceStream)
        $referenceContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('audio/wav')
        $voiceMultipart.Add($referenceContent, 'reference_audio', [IO.Path]::GetFileName($referenceAudio))
        $voiceRequest.Content = $voiceMultipart
        $voiceDeadline = New-LinkedDeadlineCancellation `
            -ParentToken $runCancellation.Token -Seconds $TimeoutSeconds
        try {
            $voiceResponse = $indexHttpClient.SendAsync(
                $voiceRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
                $voiceDeadline.Token).GetAwaiter().GetResult()
            try {
                $voiceType = [string]$voiceResponse.Content.Headers.ContentType.MediaType
                if ($voiceType -ne 'audio/wav') { throw 'IndexTTS2 Content-Type is not audio/wav' }
                Copy-LimitedResponse -Response $voiceResponse -Destination $voiceOutput `
                    -MaximumBytes $maxWaveBytes -FailureMessage 'IndexTTS2 request failed' `
                    -CancellationToken $voiceDeadline.Token
            }
            finally {
                $voiceResponse.Dispose()
            }
        }
        finally {
            $voiceDeadline.Dispose()
            $voiceRequest.Dispose()
            $referenceStream.Dispose()
        }
    }
    Assert-WaveFile $voiceOutput
    Write-Host "INDEXTTS2_LIVE_OK bytes=$((Get-Item -LiteralPath $voiceOutput).Length)"

    $encodedWorkflowPath = [Uri]::EscapeDataString("workflows/$comfyWorkflowFile")
    $workflowRequest = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Get, [Uri]::new($comfyBaseUri, "/api/userdata/$encodedWorkflowPath"))
    $workflowRequest.Headers.Accept.Add(
        [Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new('application/json'))
    Add-BasicAuthorization $workflowRequest $comfyUser $comfyPassword
    $workflowDeadline = New-LinkedDeadlineCancellation `
        -ParentToken $runCancellation.Token -Seconds $TimeoutSeconds
    try {
        $workflowResponse = $comfyHttpClient.SendAsync(
            $workflowRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
            $workflowDeadline.Token).GetAwaiter().GetResult()
        try {
            $workflow = Read-LimitedJsonResponse -Response $workflowResponse `
                -TemporaryPath (Join-Path $temporaryRoot 'workflow.json') `
                -FailureMessage 'ComfyUI workflow request failed' `
                -CancellationToken $workflowDeadline.Token
        }
        finally {
            $workflowResponse.Dispose()
        }
    }
    finally {
        $workflowDeadline.Dispose()
        $workflowRequest.Dispose()
    }
    if ([string](Get-JsonPropertyValue $workflow 'id') -ne $comfyWorkflowId) {
        throw 'ComfyUI workflow ID does not match DEMO_COMFY_WORKFLOW_ID'
    }
    $prompt = Convert-ComfyWorkflow $workflow

    $portraitWorkflowPath = Invoke-ComfyUpload -Client $comfyHttpClient -BaseUri $comfyBaseUri `
        -Path $portraitImage -Role 'portrait' -ContentType 'image/png' `
        -BasicUser $comfyUser -BasicPassword $comfyPassword -TemporaryRoot $temporaryRoot `
        -ParentToken $runCancellation.Token -TimeoutSeconds $TimeoutSeconds
    $audioWorkflowPath = Invoke-ComfyUpload -Client $comfyHttpClient -BaseUri $comfyBaseUri `
        -Path $voiceOutput -Role 'audio' -ContentType 'audio/wav' `
        -BasicUser $comfyUser -BasicPassword $comfyPassword -TemporaryRoot $temporaryRoot `
        -ParentToken $runCancellation.Token -TimeoutSeconds $TimeoutSeconds
    Set-UniqueComfyInput $prompt 'LoadImage' 'image' $portraitWorkflowPath
    Set-UniqueComfyInput $prompt 'LoadAudio' 'audio' $audioWorkflowPath
    $videoInputs = Get-UniqueComfyInputs $prompt 'VHS_VideoCombine'
    $frameRate = [double](Get-JsonPropertyValue $videoInputs 'frame_rate')
    $frameCount = Get-WaveFrameCount -Path $voiceOutput -FrameRate $frameRate
    Set-UniqueComfyInput $prompt 'JWInteger' 'value' $frameCount

    $payloadBytes = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json `
        -InputObject ([ordered]@{ prompt = $prompt }) -Depth 100 -Compress))
    if ($payloadBytes.Length -gt $maxJsonBytes) {
        throw 'ComfyUI prompt exceeds the size limit'
    }
    $submitRequest = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Post, [Uri]::new($comfyBaseUri, '/prompt'))
    $submitRequest.Headers.Accept.Add([Net.Http.Headers.MediaTypeWithQualityHeaderValue]::new('application/json'))
    Add-BasicAuthorization $submitRequest $comfyUser $comfyPassword
    $submitRequest.Content = [Net.Http.ByteArrayContent]::new($payloadBytes)
    $submitRequest.Content.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/json')
    $submitDeadline = New-LinkedDeadlineCancellation `
        -ParentToken $runCancellation.Token -Seconds $TimeoutSeconds
    try {
        $submitResponse = $comfyHttpClient.SendAsync(
            $submitRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
            $submitDeadline.Token).GetAwaiter().GetResult()
        try {
            $submitJson = Read-LimitedJsonResponse -Response $submitResponse `
                -TemporaryPath (Join-Path $temporaryRoot 'submit.json') `
                -FailureMessage 'ComfyUI submission failed' `
                -CancellationToken $submitDeadline.Token
        }
        finally {
            $submitResponse.Dispose()
        }
    }
    finally {
        $submitDeadline.Dispose()
        $submitRequest.Dispose()
    }
    $promptId = [string](Get-JsonPropertyValue $submitJson 'prompt_id')
    if ($promptId -notmatch '^[A-Za-z0-9_-]{1,128}$') {
        throw 'ComfyUI returned an invalid prompt_id'
    }

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $pollDeadline = New-LinkedDeadlineCancellation `
        -ParentToken $runCancellation.Token -Seconds $TimeoutSeconds
    $output = $null
    try {
        while ([DateTimeOffset]::UtcNow -lt $deadline -and -not $pollDeadline.IsCancellationRequested) {
            $historyRequest = [Net.Http.HttpRequestMessage]::new(
                [Net.Http.HttpMethod]::Get, [Uri]::new($comfyBaseUri, "/history/$promptId"))
            Add-BasicAuthorization $historyRequest $comfyUser $comfyPassword
            try {
                $historyResponse = $comfyHttpClient.SendAsync(
                    $historyRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
                    $pollDeadline.Token).GetAwaiter().GetResult()
                try {
                    $history = Read-LimitedJsonResponse -Response $historyResponse `
                        -TemporaryPath (Join-Path $temporaryRoot 'history.json') `
                        -FailureMessage 'ComfyUI polling failed' `
                        -CancellationToken $pollDeadline.Token
                }
                finally {
                    $historyResponse.Dispose()
                }
            }
            finally {
                $historyRequest.Dispose()
            }
            $jobProperty = $history.PSObject.Properties[$promptId]
            if ($null -ne $jobProperty) {
                $job = $jobProperty.Value
                $statusValue = [string]$job.status.status_str
                if ($statusValue -in @('error', 'failed')) {
                    throw 'ComfyUI task reached a failed terminal state'
                }
                if ([bool]$job.status.completed -and $statusValue -eq 'success') {
                    $output = Find-Mp4Output $job.outputs
                    if ($null -eq $output) { throw 'ComfyUI completed without an MP4 output' }
                    break
                }
            }
            if ($pollDeadline.Token.WaitHandle.WaitOne($PollIntervalSeconds * 1000)) {
                $pollDeadline.Token.ThrowIfCancellationRequested()
            }
        }
    }
    finally {
        $pollDeadline.Dispose()
    }
    if ($null -eq $output) { throw 'ComfyUI task did not complete before the deadline' }

    $query = '?filename=' + [Uri]::EscapeDataString($output.filename) `
        + '&subfolder=' + [Uri]::EscapeDataString($output.subfolder) `
        + '&type=' + [Uri]::EscapeDataString($output.type)
    $downloadRequest = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::Get, [Uri]::new($comfyBaseUri, '/view' + $query))
    Add-BasicAuthorization $downloadRequest $comfyUser $comfyPassword
    $downloadDeadline = New-LinkedDeadlineCancellation `
        -ParentToken $runCancellation.Token -Seconds $TimeoutSeconds
    try {
        $downloadResponse = $comfyHttpClient.SendAsync(
            $downloadRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead,
            $downloadDeadline.Token).GetAwaiter().GetResult()
        try {
            $videoType = [string]$downloadResponse.Content.Headers.ContentType.MediaType
            if ($videoType -ne 'video/mp4') { throw 'ComfyUI Content-Type is not video/mp4' }
            Copy-LimitedResponse -Response $downloadResponse -Destination $videoOutput `
                -MaximumBytes $maxVideoBytes -FailureMessage 'ComfyUI video download failed' `
                -CancellationToken $downloadDeadline.Token
        }
        finally {
            $downloadResponse.Dispose()
        }
    }
    finally {
        $downloadDeadline.Dispose()
        $downloadRequest.Dispose()
    }
    Assert-Mp4File $videoOutput
    $videoHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $videoOutput).Hash.ToLowerInvariant()
    Write-Host "COMFYUI_LIVE_OK bytes=$((Get-Item -LiteralPath $videoOutput).Length) sha256=$videoHash"
    Write-Host 'DIGITAL_HUMAN_LIVE_OK'
}
finally {
    $runCancellation.Cancel()
    $runCancellation.Dispose()
    if ($null -ne $indexHttpClient) {
        $indexHttpClient.Dispose()
    }
    $comfyHttpClient.Dispose()
    foreach ($certificate in $indexCaCertificates) {
        $certificate.Dispose()
    }
    if ($KeepArtifacts) {
        Write-Host "Live-test artifacts retained in the Git metadata directory: $temporaryRoot"
    }
    else {
        Remove-Item -LiteralPath $temporaryRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
