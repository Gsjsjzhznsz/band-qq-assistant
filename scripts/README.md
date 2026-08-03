# 构建脚本

## rpk-pack.ps1 — 手环端 rpk 打包

```powershell
.\scripts\rpk-pack.ps1 [-NoSign] [-OutDir <输出目录>]
```

- 打包 `band-qq/` 为 `dist/bandqq.rpk`。
- 默认生成/复用调试证书 `keystore.jks`（`keytool`，JDK 自带）。
- 正式安装请用 AIoT-IDE 按 `docs/signing.md` 配置签名（证书与 Android 同步器一致）。

## build-android.ps1 — Android 同步器构建

```powershell
.\scripts\build-android.ps1 [-Task assembleDebug|assembleRelease|test] [-OutDir <输出目录>]
```

- 需要本机有 Android SDK（Android Studio 环境），产物复制到 `dist/`。
- 默认 `assembleDebug`。

> 本机无 Android SDK 时，请在装有 Android Studio 的机器上运行 build-android.ps1，
> 或直接打开 `android-sync/` 构建。
