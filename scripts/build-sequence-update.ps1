param(
    [Parameter(Mandatory = $true)][string]$ReferenceApk,
    [string]$SdkPath = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$OutputDirectory,
    [string]$VerifyApk
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$ReferenceApk = (Resolve-Path -LiteralPath $ReferenceApk).Path
if (!$OutputDirectory) { $OutputDirectory = Join-Path $repo 'app/build/sequence-update' }
$expected = (Get-Content -LiteralPath (Join-Path $repo '.github/sequence-certificate.sha256') -Raw).Trim()
$buildTools = Get-ChildItem -LiteralPath (Join-Path $SdkPath 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (!$buildTools) { throw 'Android SDK build-tools not found.' }
$signer = Join-Path $buildTools.FullName 'apksigner.bat'
$aapt = Join-Path $buildTools.FullName 'aapt.exe'

function Get-VerifiedIdentity([string]$Apk) {
    $cert = & $signer verify --print-certs $Apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $signers = [regex]::Matches(($cert -join "`n"), '(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)\r?$')
    if ($signers.Count -ne 1 -or $signers[0].Groups[1].Value -ne $expected) {
        throw 'Certificate mismatch: do not install or uninstall anything.'
    }
    $badging = & $aapt dump badging $Apk
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read APK identity.' }
    $package = [regex]::Match(($badging -join "`n"), "package: name='fr.neamar.kiss.sequence.debug' versionCode='(\d+)'")
    if (!$package.Success) { throw 'Unexpected applicationId.' }
    return [long]$package.Groups[1].Value
}

$oldVersion = Get-VerifiedIdentity $ReferenceApk
function Assert-NewerVersion([long]$Version) {
    if ($Version -le $oldVersion) { throw 'versionCode must be strictly greater than the reference APK. Increase app/build.gradle versionCode before publishing an update.' }
}
if ($VerifyApk) {
    $newVersion = Get-VerifiedIdentity (Resolve-Path -LiteralPath $VerifyApk).Path
    Assert-NewerVersion $newVersion
    Write-Output "Verified update: applicationId=fr.neamar.kiss.sequence.debug versionCode=$newVersion previous=$oldVersion certificate_sha256=$expected"
    return
}
$androidUserDirectory = if ($env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME } else { Join-Path $env:USERPROFILE '.android' }
$key = Join-Path $androidUserDirectory 'debug.keystore'
if (!(Test-Path -LiteralPath $key -PathType Leaf)) {
    throw 'Existing debug key is missing. Restore its offline backup; do not generate a replacement key.'
}
$keyHash = (Get-FileHash -LiteralPath $key -Algorithm SHA256).Hash
Push-Location $repo
try {
    # Inspect only public signing metadata. Never print environment variables or key contents.
    # This also rejects a different Gradle-selected store before compiling/distributing an APK.
    $report = & .\gradlew.bat signingReport --console=plain --no-configuration-cache 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Unable to verify the configured signing identity.' }
    $debug = [regex]::Match(($report -join "`n"), '(?ms)^Variant: debug\r?\n(.*?)(?=^----------|\z)')
    $digest = [regex]::Match($debug.Value, '(?m)^SHA-256: ([0-9A-Fa-f:]+)\r?$')
    $store = [regex]::Match($debug.Value, '(?m)^Store: (.+?)\r?$')
    if (!$digest.Success -or $digest.Groups[1].Value.Replace(':', '').ToLowerInvariant() -ne $expected -or
        !$store.Success -or [IO.Path]::GetFullPath($store.Groups[1].Value) -ne [IO.Path]::GetFullPath($key)) {
        throw 'Configured Debug signing key does not match the existing KISS identity. No update APK was published.'
    }
    & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed.' }
    if ((Get-FileHash -LiteralPath $key -Algorithm SHA256).Hash -ne $keyHash) { throw 'Signing key changed during build.' }
    $apk = Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk'
    $newVersion = Get-VerifiedIdentity $apk
    Assert-NewerVersion $newVersion
    $commit = (& git rev-parse HEAD).Trim()
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $destination = Join-Path $OutputDirectory "KISS-sequence-$($commit.Substring(0, 12)).apk"
    Copy-Item -LiteralPath $apk -Destination $destination
    @(
        "source_commit=$commit"
        "source_branch=$((& git branch --show-current).Trim())"
        "working_tree_dirty=$([bool](& git status --porcelain))"
        'applicationId=fr.neamar.kiss.sequence.debug'
        "previous_versionCode=$oldVersion"
        "versionCode=$newVersion"
        "certificate_sha256=$expected"
        "apk_sha256=$((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant())"
    ) | Set-Content -LiteralPath "$destination.txt" -Encoding utf8
    Write-Output "Verified update APK: $destination"
} finally {
    Pop-Location
}
