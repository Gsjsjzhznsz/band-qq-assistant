# 小米手环9 QQ 消息收发

手环端 Vela 快应用 + Android 同步器 + SnowLuma 的三端 QQ 消息小程序。
全部可运行在一部 Android 手机 + 小米手环 9 上（SnowLuma 跑在手机本机或局域网设备）。

## 架构

```
小米手环9 (Vela 快应用) ⇄ Bluetooth interconnect ⇄ 手机
                                               │
                                    同步器 App（自研，互联+OneBot 客户端）
                                      │  OneBot WS (如 127.0.0.1:3001) + HTTP (如 127.0.0.1:3000)
                                SnowLuma（自带 Node，OneBot v11 协议端）
                                      │
                                     QQ 服务器
```

## 目录

| 目录 | 说明 |
|---|---|
| `band-qq/` | 手环端 Vela 快应用（AIoT-IDE 打开） |
| `android-sync/` | Android 同步器（Android Studio 打开） |
| `docs/` | 设计文档、SnowLuma 集成、签名说明 |

## 快速开始

1. **SnowLuma**：下载启动 SnowLuma，扫码登录 QQ，配置 HTTP 与 WS 两个独立端点、host 改 0.0.0.0（见 `docs/napcat-usage.md`）。
2. **同步器**：Android Studio 打开 `android-sync/`，改 applicationId/签名与你手环一致，安装到手机。
3. **手环端**：AIoT-IDE 打开 `band-qq/`，按 `docs/signing.md` 配签名打包 rpk，安装到手环。
4. **联调**：手机开同步器 → 连接 SnowLuma（自动缓存联系人）→ 手环打开应用 → 另一 QQ 号发消息 → 手环收到 → 手环输入回复 → 对方收到。

## 设计文档

完整设计见 `docs/superpowers/specs/2026-08-03-band-qq-design.md`，实现计划见 `docs/superpowers/plans/`。
