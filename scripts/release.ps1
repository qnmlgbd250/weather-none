<#
.SYNOPSIS
    Release: bump version -> build release APK -> rename APK -> upload to cloud clipboard
.PARAMETER Entry
    Optional changelog entry.
.EXAMPLE
    .\scripts\release.ps1 -Entry "Fix city list crash"
#>
param(
    [string]$Entry = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Join-Path $PSScriptRoot ".."
$buildFile   = Join-Path $projectRoot "app\build.gradle.kts"
$changelog   = Join-Path $projectRoot "CHANGELOG.md"

# Project constants
$env:JAVA_HOME   = "C:\Program Files\Android\Android Studio\jbr"
$gradleWrapper   = Join-Path $projectRoot "gradlew.bat"
$releaseApkDir   = Join-Path $projectRoot "app\build\outputs\apk\release"
$releaseApkSrc   = Join-Path $releaseApkDir "app-release.apk"
$uploadUrl       = "http://114.132.226.161:5000/api/files?room=sky"

function Set-Utf8NoBom {
    param(
        [string]$Path,
        [string]$Value
    )
    $encoding = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Value, $encoding)
}

# 1. Read current version
$content = Get-Content $buildFile -Raw -Encoding UTF8

$versionMatch = [regex]::Match($content, 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"')
$codeMatch    = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')

if (-not $versionMatch.Success -or -not $codeMatch.Success) {
    Write-Error "Failed to parse version from build.gradle.kts"
    exit 1
}

$major = [int]$versionMatch.Groups[1].Value
$minor = [int]$versionMatch.Groups[2].Value
$patch = [int]$versionMatch.Groups[3].Value
$code  = [int]$codeMatch.Groups[1].Value

$oldVersion = "$major.$minor.$patch"

# 2. Bump version (patch +1, code +1)
$patch++
if ($patch -gt 100) {
    $minor++
    $patch = 0
}
$code++
$newVersion = "$major.$minor.$patch"

$content = $content -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$newVersion`""
$content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $code"
Set-Utf8NoBom -Path $buildFile -Value $content

Write-Host "[1/4] Version: $oldVersion -> $newVersion (code $code)" -ForegroundColor Cyan

# 3. Update CHANGELOG
if ($Entry -and (Test-Path $changelog)) {
    $date    = Get-Date -Format "yyyy-MM-dd"
    $section = "`n## [$newVersion] - $date`n`n- $Entry`n"
    $clText  = Get-Content $changelog -Raw -Encoding UTF8
    $idx     = $clText.IndexOf("---")
    if ($idx -ge 0) {
        $next = $clText.IndexOf("---", $idx + 3)
        if ($next -ge 0) {
            $clText = $clText.Insert($next + 3, $section)
        } else {
            $clText += $section
        }
    } else {
        $clText += $section
    }
    Set-Utf8NoBom -Path $changelog -Value $clText
    Write-Host "  CHANGELOG.md updated" -ForegroundColor DarkGray
}

# 4. Build release
Write-Host "[2/4] Running assembleRelease ..." -ForegroundColor Cyan
Set-Location $projectRoot
$output = & cmd /c "`"$gradleWrapper`" assembleRelease 2>&1" | Out-String
$outputLines = $output -split "`n"
$gradleExit  = $LASTEXITCODE

foreach ($line in $outputLines) {
    if ($line -match '^\s*>?\s*Task\s' -or $line -match 'BUILD\s' -or $line -match '^\s*$') {
        Write-Host $line
    }
}

if ($gradleExit -ne 0) {
    Write-Error "Build failed (exit code $gradleExit)"
    exit $gradleExit
}
Write-Host "  Build succeeded" -ForegroundColor Green

# 5. Rename APK
$apkDst = Join-Path $releaseApkDir "skypulse-v$newVersion.apk"
if (Test-Path $releaseApkSrc) {
    Move-Item -Path $releaseApkSrc -Destination $apkDst -Force
    Write-Host "[3/4] APK renamed: skypulse-v$newVersion.apk" -ForegroundColor Cyan
} else {
    Write-Error "Missing build artifact $releaseApkSrc"
    exit 1
}

# 6. Upload to cloud clipboard
Write-Host "[4/4] Uploading to cloud clipboard ..." -ForegroundColor Cyan
try {
    $uploadResult = & cmd /c "curl.exe -s -X POST -H `"x-room-password: 888`" -F `"file=@$apkDst`" `"$uploadUrl`" 2>&1"
    Write-Host "  Upload complete: $uploadResult" -ForegroundColor Green
} catch {
    Write-Warning "Upload failed: $_"
}

# Done
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Release complete: SkyPulse v$newVersion" -ForegroundColor Green
Write-Host "  APK: $apkDst" -ForegroundColor Green
Write-Host "  Download: http://114.132.226.161:5000/r/sky" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
