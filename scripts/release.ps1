<#
.SYNOPSIS
    一键发布：自增版本号 → 构建发行版 → 重命名 APK → 上传云剪贴板
.PARAMETER Entry
    可选的变更说明，会写入 CHANGELOG.md
.EXAMPLE
    .\scripts\release.ps1 -Entry "修复城市列表崩溃"
#>
param(
    [string]$Entry = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Join-Path $PSScriptRoot ".."
$buildFile   = Join-Path $projectRoot "app\build.gradle.kts"
$changelog   = Join-Path $projectRoot "CHANGELOG.md"

# ── 项目固定参数 ──────────────────────────────────────────────
$env:JAVA_HOME   = "C:\Program Files\Android\Android Studio\jbr"
$gradleWrapper   = Join-Path $projectRoot "gradlew.bat"
$releaseApkDir   = Join-Path $projectRoot "app\build\outputs\apk\release"
$releaseApkSrc   = Join-Path $releaseApkDir "app-release.apk"
$uploadUrl       = "http://114.132.226.161:5000/api/files?room=sky"

# ── 1. 读取当前版本 ──────────────────────────────────────────
$content = Get-Content $buildFile -Raw

$versionMatch = [regex]::Match($content, 'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"')
$codeMatch    = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')

if (-not $versionMatch.Success -or -not $codeMatch.Success) {
    Write-Error "无法从 build.gradle.kts 解析版本号"
    exit 1
}

$major = [int]$versionMatch.Groups[1].Value
$minor = [int]$versionMatch.Groups[2].Value
$patch = [int]$versionMatch.Groups[3].Value
$code  = [int]$codeMatch.Groups[1].Value

$oldVersion = "$major.$minor.$patch"

# ── 2. 递增版本号 (patch +1, code +1) ───────────────────────
$patch++
$code++
$newVersion = "$major.$minor.$patch"

$content = $content -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$newVersion`""
$content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $code"
Set-Content $buildFile -Value $content -Encoding UTF8 -NoNewline

Write-Host "[1/4] 版本号: $oldVersion -> $newVersion (code $code)" -ForegroundColor Cyan

# ── 3. 写入 CHANGELOG ────────────────────────────────────────
if ($Entry -and (Test-Path $changelog)) {
    $date    = Get-Date -Format "yyyy-MM-dd"
    $section = "`n## [$newVersion] - $date`n`n- $Entry`n"
    $clText  = Get-Content $changelog -Raw
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
    Set-Content $changelog -Value $clText -Encoding UTF8 -NoNewline
    Write-Host "  CHANGELOG.md 已更新" -ForegroundColor DarkGray
}

# ── 4. 构建发行版 ────────────────────────────────────────────
Write-Host "[2/4] 开始构建 assembleRelease ..." -ForegroundColor Cyan
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
    Write-Error "构建失败 (exit code $gradleExit)"
    exit $gradleExit
}
Write-Host "  构建成功" -ForegroundColor Green

# ── 5. 重命名 APK ───────────────────────────────────────────
$apkDst = Join-Path $releaseApkDir "南风天气_$newVersion.apk"
if (Test-Path $releaseApkSrc) {
    Move-Item -Path $releaseApkSrc -Destination $apkDst -Force
    Write-Host "[3/4] APK 已重命名: 南风天气_$newVersion.apk" -ForegroundColor Cyan
} else {
    Write-Error "找不到构建产物 $releaseApkSrc"
    exit 1
}

# ── 6. 上传到云剪贴板 ───────────────────────────────────────
Write-Host "[4/4] 上传到云剪贴板 ..." -ForegroundColor Cyan
try {
    $uploadResult = & cmd /c "curl.exe -s -X POST -F `"file=@$apkDst`" `"$uploadUrl`" 2>&1"
    Write-Host "  上传完成: $uploadResult" -ForegroundColor Green
} catch {
    Write-Warning "上传失败: $_"
}

# ── 完成 ─────────────────────────────────────────────────────
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  发布完成: 南风天气 v$newVersion" -ForegroundColor Green
Write-Host "  APK: $apkDst" -ForegroundColor Green
Write-Host "  下载: http://114.132.226.161:5000/r/sky" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green