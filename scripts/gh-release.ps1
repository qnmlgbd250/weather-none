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
$tokenLine = Select-String -Path $localProps -Pattern '^GITHUB_TOKEN\s*=' | Select-Object -First 1
if (-not $tokenLine) {
    Write-Error "GITHUB_TOKEN not found in local.properties"
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

# 7. Upload APK
Write-Host "[4/5] Uploading APK ..." -ForegroundColor Cyan
$uploadUrl = "https://uploads.github.com/repos/qnmlgbd250/weather-none/releases/$($release.id)/assets?name=skypulse-v$Version.apk"
try {
    $asset = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers @{
        Authorization = "token $token"
        "Content-Type" = "application/vnd.android.package-archive"
    } -InFile $apkPath
    Write-Host "  Uploaded: $($asset.name), size=$($asset.size), state=$($asset.state)" -ForegroundColor Green
} catch {
    Write-Error "Upload failed: $_"
    exit 1
}

# 8. Download and verify (via API to support draft releases)
Write-Host "[5/5] Verifying integrity ..." -ForegroundColor Cyan
$tempPath = Join-Path $env:TEMP "skypulse-verify.apk"
Remove-Item -LiteralPath $tempPath -ErrorAction Ignore

try {
    $assetUrl = $asset.url
    if (-not $assetUrl) {
        Write-Error "No asset URL found"
        exit 1
    }
    Invoke-WebRequest -Uri $assetUrl -Headers @{
        Authorization = "token $token"
        Accept = "application/octet-stream"
    } -OutFile $tempPath -MaximumRedirection 10
    $dlSize = (Get-Item -LiteralPath $tempPath).Length
    $dlHash = (Get-FileHash -LiteralPath $tempPath -Algorithm SHA256).Hash

    if ($dlSize -eq $localSize -and $dlHash -eq $localHash) {
        Write-Host "  VERIFICATION PASSED" -ForegroundColor Green
    } else {
        Write-Host "  VERIFICATION FAILED" -ForegroundColor Red
        Write-Host "  Local:  size=$localSize, SHA256=$localHash"
        Write-Host "  Remote: size=$dlSize, SHA256=$dlHash"
        exit 1
    }
} finally {
    Remove-Item -LiteralPath $tempPath -ErrorAction Ignore
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Release created: $tagName" -ForegroundColor Green
Write-Host "  URL: $($release.html_url)" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
