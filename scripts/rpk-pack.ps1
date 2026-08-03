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
        --exclude="sign" `
        manifest.json app.ux common i18n pages
    if ($LASTEXITCODE -ne 0) {
        Write-Error "打包失败（tar 命令异常）。"
        exit 1
    }
} finally {
    Pop-Location
}

Move-Item -Force $tmpZip $target

# 3. 提取 AIoT-IDE 签名用的 private.pem / certificate.pem（供手环正式打包）
$signDir = Join-Path $bandDir "sign\debug"
if (-not $NoSign -and (Test-Path -LiteralPath $keystore)) {
    New-Item -ItemType Directory -Path $signDir -Force | Out-Null
    $openssl = "$env:ProgramFiles\Git\usr\bin\openssl.exe"
    $p12 = Join-Path $signDir "bandqq.p12"
    $pem = Join-Path $signDir "bandqq.pem"
    # jks -> p12（keytool stderr 重定向到文件避免误报）
    $errFile = Join-Path $outDir "_keytool2.err"
    cmd /c "keytool -importkeystore -srckeystore `"$keystore`" -destkeystore `"$p12`" -srcstoretype jks -deststoretype pkcs12 -storepass $storePass -srcstorepass $storePass -noprompt 2>`"$errFile`"" | Out-Null
    Remove-Item -Force $errFile -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $openssl) {
        & $openssl pkcs12 -in $p12 -nodes -out $pem -passin pass:$storePass 2>&1 | Out-Null
        $pemContent = Get-Content -Raw $pem
        $priv = [regex]::Match($pemContent, '(?s)-----BEGIN PRIVATE KEY-----.*?-----END PRIVATE KEY-----').Value
        $cert = [regex]::Match($pemContent, '(?s)-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----').Value
        Set-Content -LiteralPath (Join-Path $signDir "private.pem") -Value $priv -Encoding ASCII
        Set-Content -LiteralPath (Join-Path $signDir "certificate.pem") -Value $cert -Encoding ASCII
        Write-Host "AIoT-IDE 签名文件已生成: $signDir\private.pem / certificate.pem"
    }
}

if ($NoSign) {
    Write-Host "已生成（未签名）: $target"
} else {
    Write-Host "已生成调试包（证书 $keystore）: $target"
    Write-Host "提示: 正式安装请在 AIoT-IDE 中打包，将签名指向 band-qq/sign/debug/ 下的"
    Write-Host "       private.pem 与 certificate.pem（与 Android release 证书一致）。"
}

Write-Host "证书信息:"
Write-Host "  store: $keystore"
Write-Host "  storepass/keypass: $storePass"
Write-Host "  alias: $keyAlias"
Write-Host "Android release 签名参考: android-sync/keystore.example.properties"
