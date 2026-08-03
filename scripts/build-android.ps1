# Android 同步器构建脚本
# 用法: .\scripts\build-android.ps1 [-Task test|assembleDebug|assembleRelease] [-OutDir <输出目录>]
#
# 说明:
#   - 本机若无 Android SDK，请在装有 Android Studio 的机器上运行。
#   - 需要先配置 local.properties (sdk.dir=...) 或 ANDROID_HOME 环境变量。
#   - 默认执行 assembleDebug，产物复制到 dist/。

param(
    [string]$Task = "assembleDebug",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$proj = Join-Path $root "android-sync"
$outDir = if ($OutDir) { $OutDir } else { Join-Path $root "dist" }

if (-not (Test-Path -LiteralPath $proj)) {
    Write-Error "未找到 Android 工程: $proj"
    exit 1
}

if (-not $env:ANDROID_HOME -and -not (Test-Path (Join-Path $proj "local.properties"))) {
    Write-Warning "未检测到 ANDROID_HOME 且缺少 local.properties，可能无法构建。"
    Write-Warning "请在有 Android Studio 的机器上打开 android-sync/ 构建，或将 sdk.dir 写入 local.properties。"
}

$gradlew = Join-Path $proj "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradlew)) {
    Write-Warning "缺少 gradlew.bat，请在 Android Studio 中打开工程生成 wrapper 后重试。"
}

Write-Host "运行: gradlew $Task (工作目录: $proj)"
Push-Location $proj
try {
    & ".\gradlew.bat" $Task
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle 构建失败（exit=$LASTEXITCODE）。"
        exit 1
    }
} finally {
    Pop-Location
}

# 复制 APK 产物到 dist/
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
$apkDir = Join-Path $proj "app\build\outputs\apk"
if (Test-Path -LiteralPath $apkDir) {
    Get-ChildItem -Recurse -Filter "*.apk" -Path $apkDir | ForEach-Object {
        Copy-Item -Force $_.FullName (Join-Path $outDir $_.Name)
        Write-Host "产物: $(Join-Path $outDir $_.Name)"
    }
}

Write-Host "完成。dist 目录: $outDir"
