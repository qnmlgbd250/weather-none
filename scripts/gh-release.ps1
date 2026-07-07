<#
.SYNOPSIS
    Create a GitHub Release with APK upload + integrity verification.
.PARAMETER Version
    Version string (e.g. "2.1.64"). If omitted, auto-detect from APK filename.
.PARAMETER Body
    Release description (markdown). Default: "修复已知问题".
.EXAMPLE
    .\scripts\gh-release.ps1 -Version "2.1.64"
#>
param(
    [string]$Version = "",
    [string]$Body = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Join-Path $PSScriptRoot ".."

# 1. Read GitHub token from local.properties
$localProps = Join-Path $projectRoot "local.properties"
if (-not (Test-Path $localProps)) {
    Write-Error "local.properties not found"
    exit 1
}
$tokenLine = Select-String -Path $localProps -Pattern '^github_token\s*=' | Select-Object -First 1
if (-not $tokenLine) {
    Write-Error "github_token not found in local.properties"
    exit 1
}
$token = ($tokenLine.Line -split '=', 2)[-1].Trim()
if ([string]::IsNullOrEmpty($token)) {
    Write-Error "GITHUB_TOKEN is empty"
    exit 1
}

# 2. Detect APK file
$releaseApkDir = Join-Path $projectRoot "app\build\outputs\apk\release"
$apkPattern = "skypulse-v*.apk"
$apkFiles = Get-ChildItem -Path $releaseApkDir -Filter $apkPattern | Sort-Object LastWriteTime -Descending
if ($apkFiles.Count -eq 0) {
    # Fallback to root
    $apkFiles = Get-ChildItem -Path $projectRoot -Filter $apkPattern | Sort-Object LastWriteTime -Descending
}
if ($apkFiles.Count -eq 0) {
    Write-Error "No skypulse-v*.apk found. Build first with release.ps1"
    exit 1
}
$apkPath = $apkFiles[0].FullName

# 3. Determine version
if (-not $Version) {
    $match = [regex]::Match($apkFiles[0].Name, 'skypulse-v(\d+\.\d+\.\d+)\.apk')
    if (-not $match.Success) {
        Write-Error "Cannot detect version from APK filename"
        exit 1
    }
    $Version = $match.Groups[1].Value
}

$tagName = "v$Version"

Write-Host "[1/5] APK: $($apkFiles[0].Name)" -ForegroundColor Cyan

# 4. Record local checksums
$localSize = (Get-Item -LiteralPath $apkPath).Length
$localHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
Write-Host "[2/5] Local APK: size=$localSize, SHA256=$localHash" -ForegroundColor Cyan

# 5. Always use fixed release body
$Body = "修复已知问题"

# 6. Create release (write JSON to temp file to guarantee UTF-8 without BOM)
Write-Host "[3/5] Creating release $tagName ..." -ForegroundColor Cyan
$jsonPayload = "{`"tag_name`":`"$tagName`",`"name`":`"$tagName`",`"body`":`"\u4fee\u590d\u5df2\u77e5\u95ee\u9898`",`"draft`":false}"
$tmpFile = Join-Path $env:TEMP "gh_release_payload_$([System.IO.Path]::GetRandomFileName()).json"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($tmpFile, $jsonPayload, $utf8NoBom)

try {
    $response = curl.exe -s -X POST `
        -H "Authorization: token $token" `
        -H "Content-Type: application/json; charset=utf-8" `
        --data-binary "@$tmpFile" `
        "https://api.github.com/repos/qnmlgbd250/weather-none/releases" 2>$null
    $release = $response | ConvertFrom-Json
    if (-not $release.id) {
        $errorMsg = if ($response) { $response } else { "Empty response" }
        Write-Error "Failed to create release: $errorMsg"
        exit 1
    }
} catch {
    Write-Error "Failed to create release: $_"
    exit 1
} finally {
    Remove-Item -LiteralPath $tmpFile -ErrorAction Ignore
}
Write-Host "  Created: $($release.html_url)" -ForegroundColor Green

# 7. Upload APK and verify integrity. If verification fails, delete the broken asset and retry once.
Write-Host "[4/5] Uploading APK ..." -ForegroundColor Cyan
$uploadUrl = "https://uploads.github.com/repos/qnmlgbd250/weather-none/releases/$($release.id)/assets?name=skypulse-v$Version.apk"
$tempPath = Join-Path $env:TEMP "skypulse-verify.apk"

function Upload-ApkAsset {
    Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers @{
        Authorization = "token $token"
        "Content-Type" = "application/vnd.android.package-archive"
    } -InFile $apkPath
}

function Test-ApkAssetIntegrity {
    param($Asset)
    Remove-Item -LiteralPath $tempPath -ErrorAction Ignore
    try {
        if (-not $Asset.url) {
            Write-Error "No asset URL found"
            return $false
        }
        Invoke-WebRequest -Uri $Asset.url -Headers @{
            Authorization = "token $token"
            Accept = "application/octet-stream"
        } -OutFile $tempPath -MaximumRedirection 10
        $dlSize = (Get-Item -LiteralPath $tempPath).Length
        $dlHash = (Get-FileHash -LiteralPath $tempPath -Algorithm SHA256).Hash

        if ($dlSize -eq $localSize -and $dlHash -eq $localHash) {
            Write-Host "  VERIFICATION PASSED" -ForegroundColor Green
            return $true
        }

        Write-Host "  VERIFICATION FAILED" -ForegroundColor Red
        Write-Host "  Local:  size=$localSize, SHA256=$localHash"
        Write-Host "  Remote: size=$dlSize, SHA256=$dlHash"
        return $false
    } finally {
        Remove-Item -LiteralPath $tempPath -ErrorAction Ignore
    }
}

$asset = $null
$verified = $false
for ($attempt = 1; $attempt -le 2; $attempt++) {
    try {
        $asset = Upload-ApkAsset
        Write-Host "  Uploaded: $($asset.name), size=$($asset.size), state=$($asset.state)" -ForegroundColor Green
    } catch {
        Write-Error "Upload failed: $_"
        exit 1
    }

    Write-Host "[5/5] Verifying integrity ..." -ForegroundColor Cyan
    $verified = Test-ApkAssetIntegrity -Asset $asset
    if ($verified) { break }

    if ($asset.id) {
        Write-Host "  Deleting corrupted asset and retrying upload ..." -ForegroundColor Yellow
        Invoke-RestMethod -Uri "https://api.github.com/repos/qnmlgbd250/weather-none/releases/assets/$($asset.id)" `
            -Method Delete `
            -Headers @{ Authorization = "token $token" } | Out-Null
    }
}

if (-not $verified) {
    Write-Error "Remote APK integrity verification failed after retry"
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Release created: $tagName" -ForegroundColor Green
Write-Host "  URL: $($release.html_url)" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
