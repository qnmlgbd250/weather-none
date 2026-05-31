<#
.SYNOPSIS
    Bumps the patch version in app/build.gradle.kts and logs to CHANGELOG.md.
.PARAMETER Entry
    A short description of what changed (added to CHANGELOG.md).
.EXAMPLE
    .\bump-version.ps1 -Entry "Fixed crash on city list screen"
#>
param(
    [string]$Entry = ""
)

$ErrorActionPreference = "Stop"
$buildFile = Join-Path $PSScriptRoot "..\app\build.gradle.kts"
$changelog = Join-Path $PSScriptRoot "..\CHANGELOG.md"

if (-not (Test-Path $buildFile)) {
    Write-Error "build.gradle.kts not found at $buildFile"
    exit 1
}

$content = Get-Content $buildFile -Raw

# --- Parse current version ---
$versionMatch = [regex]::Match($content, 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"')
$codeMatch   = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')

if (-not $versionMatch.Success -or -not $codeMatch.Success) {
    Write-Error "Could not parse version from build.gradle.kts"
    exit 1
}

$major = [int]$versionMatch.Groups[1].Value
$minor = [int]$versionMatch.Groups[2].Value
$patch = [int]$versionMatch.Groups[3].Value
$code  = [int]$codeMatch.Groups[1].Value

$oldVersion = "$major.$minor.$patch"

# --- Bump ---
$patch++
$code++
$newVersion = "$major.$minor.$patch"

# --- Write back ---
$content = $content -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$newVersion`""
$content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $code"
Set-Content $buildFile -Value $content -Encoding UTF8 -NoNewline

Write-Host "Version bumped: $oldVersion -> $newVersion (code $code)"

# --- Append to CHANGELOG ---
if ($Entry -and (Test-Path $changelog)) {
    $date = Get-Date -Format "yyyy-MM-dd"
    $section = "`n## [$newVersion] — $date`n`n- $Entry`n"
    $changelogContent = Get-Content $changelog -Raw
    # Insert after the first triple-dash separator (after header)
    $firstSep = $changelogContent.IndexOf("---")
    if ($firstSep -ge 0) {
        $afterFirstSep = $firstSep + 3
        $secondSep = $changelogContent.IndexOf("---", $afterFirstSep)
        if ($secondSep -ge 0) {
            $changelogContent = $changelogContent.Insert($secondSep + 3, $section)
        } else {
            $changelogContent += $section
        }
    } else {
        $changelogContent += $section
    }
    Set-Content $changelog -Value $changelogContent -Encoding UTF8 -NoNewline
    Write-Host "CHANGELOG.md updated."
}

# --- Rename APK with version ---
$apkDir = Join-Path $PSScriptRoot "..\app\build\outputs\apk\release"
$apkSrc = Join-Path $apkDir "app-release.apk"
$apkDst = Join-Path $apkDir "SkyPulse-v$newVersion.apk"
if (Test-Path $apkSrc) {
    Move-Item -Path $apkSrc -Destination $apkDst -Force
    Write-Host "APK renamed: SkyPulse-v$newVersion.apk"

    # --- Upload to cloud clipboard ---
    $uploadUrl = "http://114.132.226.161:5000/api/files?room=2027"
    $result = curl.exe -X POST -F "file=@$apkDst" $uploadUrl 2>&1
    Write-Host "Upload result: $result"
} else {
    Write-Host "APK not found at $apkSrc — skip rename/upload."
}