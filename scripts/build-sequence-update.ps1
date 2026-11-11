param(
    [Parameter(Mandatory = $true)][string]$ReferenceApk,
    [string]$SdkPath = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$OutputDirectory
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
    $match = [regex]::Match(($cert -join "`n"), '(?m)^Signer #1 certificate SHA-256 digest: ([0-9a-f]+)\r?$')
    if (!$match.Success -or $match.Groups[1].Value -ne $expected) {
        throw 'Certificate mismatch: do not install or uninstall anything.'
    }
    $badging = & $aapt dump badging $Apk
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read APK identity.' }
    $package = [regex]::Match(($badging -join "`n"), "package: name='fr.neamar.kiss.sequence.debug' versionCode='(\d+)'")
    if (!$package.Success) { throw 'Unexpected applicationId.' }
    return [long]$package.Groups[1].Value
}

$oldVersion = Get-VerifiedIdentity $ReferenceApk
$androidUserDirectory = if ($env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME } else { Join-Path $env:USERPROFILE '.android' }
$key = Join-Path $androidUserDirectory 'debug.keystore'
if (!(Test-Path -LiteralPath $key -PathType Leaf)) {
    throw 'Existing debug key is missing. Restore its offline backup; do not generate a replacement key.'
}
$keyHash = (Get-FileHash -LiteralPath $key -Algorithm SHA256).Hash
Push-Location $repo
try {
    & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed.' }
    if ((Get-FileHash -LiteralPath $key -Algorithm SHA256).Hash -ne $keyHash) { throw 'Signing key changed during build.' }
    $apk = Join-Path $repo 'app/build/outputs/apk/debug/app-debug.apk'
    $newVersion = Get-VerifiedIdentity $apk
    if ($newVersion -lt $oldVersion) { throw 'Refusing a versionCode downgrade.' }
    $commit = (& git rev-parse HEAD).Trim()
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $destination = Join-Path $OutputDirectory "KISS-sequence-$($commit.Substring(0, 12)).apk"
    Copy-Item -LiteralPath $apk -Destination $destination
    @(
        "source_commit=$commit"
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
