# 小米手环9 QQ 消息助手（band-qq）

在小米手环 9 上收发 QQ 消息的三端小程序：

- **手环端**：Vela 快应用，展示联系人、收发文字消息、快捷回复、查看聊天记录
- **手机端**：Android 同步器，通过蓝牙 interconnect 桥接手环与 OneBot 协议端
- **协议端**：[SnowLuma](https://github.com/SnowLuma/SnowLuma)，把 QQ 原生会话转换为 OneBot v11 服务

全部可运行在一部 **Android 手机 + 小米手环 9** 上（SnowLuma 可跑在手机本机或局域网内的任何设备），消息不经过任何第三方服务器。

## 功能特性

- **手环收发 QQ**：联系人列表、聊天记录、文字输入与快捷回复、清空聊天
- **蓝牙互联通道**：手机与手环通过小米 interconnect 通道通信，自动重连
- **OneBot v11**：通过 WebSocket + HTTP 对接 SnowLuma，发送消息、拉取联系人、接收实时推送
- **离线可用**：联系人与聊天记录本地持久化缓存，手环未连接也能查看历史
- **状态与日志**：手机端实时状态面板与日志窗口，便于排查问题

## 架构

```
小米手环9 (Vela 快应用) ⇄ Bluetooth interconnect ⇄ 手机
                                               │
                              同步器 App（互联 + OneBot 客户端）
                                │ OneBot WS (如 127.0.0.1:3001) + HTTP (如 127.0.0.1:3000)
                          SnowLuma（自带 Node，OneBot v11 协议端）
                                │
                               QQ 服务器
```

## 目录结构

| 目录 | 说明 |
|---|---|
| `band-qq/` | 手环端 Vela 快应用（用小米 AIoT-IDE 打开） |
| `android-sync/` | Android 同步器（用 Android Studio 打开） |
| `scripts/` | Windows 构建脚本：`rpk-pack.ps1`、`build-android.ps1` |
| `docs/` | 使用文档：SnowLuma 安装、签名、APK 方案 |
| `dist/` | 构建产物（已 gitignore） |

## 使用教程

### 1. 部署 SnowLuma（协议端）

> SnowLuma 项目地址：<https://github.com/SnowLuma/SnowLuma>

SnowLuma 是面向 QQ 客户端的 TypeScript 互操作运行时，将 QQ 原生会话转换为 [OneBot v11](https://github.com/botuniverse/onebot-11)
动作与事件，并通过 WebSocket / HTTP / WebUI 提供统一入口。它自带 Node.js 运行时，无需额外安装 QQNT 或 Node 环境
（Lite 版需 Node.js 22.13+）。

安装步骤：

1. 到 [SnowLuma Releases](https://github.com/SnowLuma/SnowLuma/releases) 下载对应平台（Windows / macOS / Linux）的发行包并解压。
2. 启动：Windows 运行 `launcher.bat`；Linux 执行 `chmod +x launcher.sh && ./launcher.sh`。
3. 浏览器打开 `http://localhost:5099`，使用启动日志中的初始密码登录 **WebUI**，用 **QQ 扫码登录**（务必使用小号）。
4. 在 WebUI 中开启 **HTTP API** 与 **WS 服务端** 两个独立端点，监听 host 改为 `0.0.0.0`（局域网 / 手机可访问），
   并开启「上报自身消息」、使用**数组**消息格式。改动后保存并重启。
5. 详细配置与常见问题见 [`docs/napcat-usage.md`](docs/napcat-usage.md)。

> ⚠️ **风控提示**：SnowLuma 基于逆向协议，请务必使用 QQ 小号，避免高频群发、频繁加群等异常行为，否则可能触发风控封禁。

### 2. 构建并安装 Android 同步器

- `applicationId` 必须为 `com.example.bandqq`（与手环端 `manifest.package` 一致，互联硬性要求）。
- 安装到真手环时，release 包需用 `.jks` 证书签名，且与手环 rpk 使用**同一证书**（见 [`docs/signing.md`](docs/signing.md)）。

**方式 A：命令行构建（Windows）**

```powershell
.\scripts\build-android.ps1 -Task assembleRelease
# 产物：dist\app-release.apk
```

脚本使用本机 D 盘工具链（Android SDK / JDK 17 / Gradle 8.13）。若用 Android Studio 构建：

**方式 B：Android Studio**

1. 用 Android Studio 打开 `android-sync/`（Gradle 8.13 由 wrapper 自动下载）。
2. 等待同步完成，配置签名后点击 Run / Build APK。
3. 单测：`./gradlew :app:testDebugUnitTest`。

### 3. 构建并安装手环端 rpk

1. 安装小米 AIoT-IDE（官方 Vela 快应用开发工具），导入 `band-qq/` 目录。
2. 确认 `manifest.json`：`package` 为 `com.example.bandqq`、`config.designWidth` 为 `192`。
3. 连接小米手环 9，点击「运行到设备」，AIoT-IDE 自动构建并签名安装 rpk（首次需在小米运动健康中开启快应用调试）。
4. 命令行打包签名：`.\scripts\rpk-pack.ps1`（产物 `dist\bandqq.release.rpk`，使用与 APK 同一 `keystore.jks` 证书）。

### 4. 联调

1. 启动 SnowLuma，手机打开同步器 App，配置 WS / HTTP 地址后「自动探测」并「开始同步」，状态显示「SnowLuma 在线」。
2. 手环端打开本应用，首页显示「已连接 · QQ助手」即代表互联通道就绪。
3. 用另一 QQ 号发消息 → 手环收到并展示；从手环输入回复 → 对方收到。

## 常见问题

| 现象 | 处理 |
|---|---|
| 探测不到 SnowLuma | 确认已启动、host 已改为 `0.0.0.0`、QQ 已登录 |
| HTTP 可连但 WS 失败 | WS 与 HTTP 端口须独立、token 一致 |
| 收到 401 | Access Token 不一致 |
| 连接成功但收不到消息 | 开启「上报自身消息」、消息格式为数组、WS 端口正确 |
| 被系统杀后台 | 把同步器 App 加入电池优化白名单 |

更多排错见 [`docs/napcat-usage.md`](docs/napcat-usage.md)。

## 致谢

- 本项目代码由 [opencode](https://opencode.ai) 辅助编写。
- 协议端使用 [SnowLuma](https://github.com/SnowLuma/SnowLuma)，项目参考了 [LagrangeV2](https://github.com/LagrangeDev/LagrangeV2)
  与 [NapCatQQ](https://github.com/NapNeko/NapCatQQ) 的实现思路。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

## 免责声明

本项目仅用于学习与技术研究，请遵守《QQ 用户协议》及适用法律；使用风险自负，开发者不对因使用本项目造成的任何损失负责。