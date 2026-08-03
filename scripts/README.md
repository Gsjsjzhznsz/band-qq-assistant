# 构建脚本

## rpk-pack.ps1 — 手环端 rpk 打包

```powershell
.\scripts\rpk-pack.ps1 [-NoSign] [-OutDir <输出目录>]
```

- 打包 `band-qq/` 为 `dist/bandqq.rpk`。
- 默认生成/复用调试证书 `keystore.jks`（`keytool`，JDK 自带，密码 `bandqq123`）。
- 自动从 `keystore.jks` 提取 AIoT-IDE 签名文件到 `band-qq/sign/debug/`（`private.pem` + `certificate.pem`），
  与 Android release APK 使用同一证书。
- 正式安装 rpk 请在 AIoT-IDE 中打包，签名指向 `band-qq/sign/debug/`。

> 依赖：`keytool`（JDK）、`openssl`（Windows 上可用 Git 自带 `C:\Program Files\Git\usr\bin\openssl.exe`）。

## build-android.ps1 — Android 同步器构建

```powershell
.\scripts\build-android.ps1 [-Task assembleDebug|assembleRelease|test] [-OutDir <输出目录>]
```

- 本机工具链（已安装到 D 盘）：Android SDK `D:\android-sdk`、JDK 17 `D:\android-build\jdk17`、
  Gradle 8.7 `D:\android-build\gradle-8.7`、Gradle 缓存 `D:\android-build\gradle-home`。
- 若系统已配置 `ANDROID_HOME`/`JAVA_HOME` 则优先使用环境变量。
- 产物复制到 `dist/`（默认 `assembleDebug`）。

## 产物

构建完成后 `dist/` 下应有：
- `app-debug.apk` / `app-release.apk`：Android 同步器
- `bandqq.rpk`：手环端（正式版需 AIoT-IDE 重新签名打包）

三端使用同一证书（`keystore.jks`，指纹 `62:c7:81:8b...a1:90`），满足互联前提。
