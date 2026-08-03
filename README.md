# 小米手环9 QQ 消息收发

手环端 Vela 快应用 + Android 同步器 + NapCatQQ 的三端 QQ 消息小程序。
全部可运行在一部 Android 手机 + 小米手环 9 上（NapCat 跑在手机 Termux 容器内）。

## 架构

```
小米手环9 (Vela 快应用) ⇄ Bluetooth interconnect ⇄ 手机
                                               │
                                    同步器 App（自研，互联+OneBot 客户端）
                                      │  OneBot WS (127.0.0.1:3001)
                                NapCat APK（Termux+proot+NapCat）
                                      │
                                     QQ 服务器
```

## 目录

| 目录 | 说明 |
|---|---|
| `band-qq/` | 手环端 Vela 快应用（AIoT-IDE 打开） |
| `android-sync/` | Android 同步器（Android Studio 打开） |
| `docs/` | 设计文档、NapCat 集成、签名说明 |

## 快速开始

1. **NapCat**：安装 NapCat APK（见 `docs/napcat-apk.md`），扫码登录 QQ，WebUI 配置 OneBot WS 端口 3001。
2. **同步器**：Android Studio 打开 `android-sync/`，改 applicationId/签名与你手环一致，安装到手机。
3. **手环端**：AIoT-IDE 打开 `band-qq/`，按 `docs/signing.md` 配签名打包 rpk，安装到手环。
4. **联调**：手机开同步器 → 手环打开应用 → 另一 QQ 号发消息 → 手环收到 → 手环发快捷回复 → 对方收到。

## 设计文档

完整设计见 `docs/superpowers/specs/2026-08-03-band-qq-design.md`，实现计划见 `docs/superpowers/plans/`。
