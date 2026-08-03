# 手环端 rpk 打包脚本
# 用法: .\scripts\rpk-pack.ps1 [-NoSign] [-OutDir <输出目录>]
#
# 说明:
#   - 打包 band-qq/ 目录为 rpk（Vela 快应用包）。
#   - 默认生成/复用调试证书（keystore.jks），用于调试安装。
#   - 真正的 Vela rpk 签名需在 AIoT-IDE 中配置（signing.md），
#     本脚本生成的证书与包用于快速验证 / 走 AIoT-IDE 签名。
#   - 要求: keytool（JDK）、tar（zip 打包，Windows 10+ 自带 tar 可打 zip）。

param(
    [switch]$NoSign,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$bandDir = Join-Path $root "band-qq"
$outDir = if ($OutDir) { $OutDir } else { Join-Path $root "dist" }

if (-not (Test-Path -LiteralPath $bandDir)) {
    Write-Error "未找到手环端目录: $bandDir"
    exit 1
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$keystore = Join-Path $root "keystore.jks"
$storePass = "bandqq123"
$keyAlias = "bandqq"

# 1. 生成调试证书（如不存在）
if (-not $NoSign -and -not (Test-Path -LiteralPath $keystore)) {
    Write-Host "生成调试证书 $keystore ..."
    # 用 cmd /c 包装并将 stderr 重定向到文件，避免触发 PowerShell 5.1 的 NativeCommandError
    $errFile = Join-Path $outDir "_keytool.err"
    cmd /c "keytool -genkeypair -v -keystore `"$keystore`" -alias $keyAlias -keyalg RSA -keysize 2048 -validity 3650 -storepass $storePass -keypass $storePass -dname `"CN=BandQQ, OU=Dev, O=BandQQ, L=City, ST=State, C=CN`" 2>`"$errFile`"" | Out-Null
    Remove-Item -Force $errFile -ErrorAction SilentlyContinue
    if (-not (Test-Path -LiteralPath $keystore)) {
        Write-Error "keytool 生成证书失败，请确认已安装 JDK 并在 PATH 中。"
        exit 1
    }
}

# 2. 打包 band-qq/ 为 zip（跳过 node_modules / 测试）
$tmpZip = Join-Path $outDir "_bandqq_src.zip"
$target = Join-Path $outDir "bandqq.rpk"

Push-Location $bandDir
try {
    # tar 的 -a 自动按扩展名压缩；用 zip 输出需要先打成 zip 再改名
    & tar -a -c -f $tmpZip `
        --exclude="test" `
        --exclude="node_modules" `
        manifest.json app.ux common i18n pages
    if ($LASTEXITCODE -ne 0) {
        Write-Error "打包失败（tar 命令异常）。"
        exit 1
    }
} finally {
    Pop-Location
}

Move-Item -Force $tmpZip $target

if ($NoSign) {
    Write-Host "已生成（未签名）: $target"
} else {
    Write-Host "已生成调试包（证书 $keystore）: $target"
    Write-Host "提示: 正式安装请用 AIoT-IDE 按 docs/signing.md 配签名重新打包，"
    Write-Host "       确保证书与 Android 同步器 App 一致。"
}

Write-Host "证书信息:"
Write-Host "  store: $keystore"
Write-Host "  storepass/keypass: $storePass"
Write-Host "  alias: $keyAlias"
Write-Host "Android release 签名参考: android-sync/keystore.example.properties"
