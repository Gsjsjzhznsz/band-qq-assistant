# NapCat 安装与使用详细教程

NapCat 是 QQ 机器人协议端：它把 QQ 账号变成可编程的 OneBot 服务，供同步器 App 收发消息。
本项目里它跑在**同一台手机的 Termux 环境**内，同步器通过 `ws://127.0.0.1:3001` 连接它。

> 环境要求：一台 Android 手机（系统 Android 8+）、一个 QQ 小号、可上网的 WiFi。

---

## 0. 总体流程

```
手机装 Termux（F-Droid 版）
   └─ Termux 内安装 NapCat
        └─ 打开 NapCat WebUI 扫码登录 QQ
             └─ 新建 WebSocket 服务（端口 3001）
                  └─ 同步器 App「自动探测」→ 连接成功
```

---

## 1. 只装 Termux（关键：用 F-Droid 版，别用 Google Play 版）

> **为什么必须 F-Droid 版？** Google Play 版 Termux 已停止迭代、包与签名常有兼容问题，
> 会报 `Failed to get package context for the "com.termux" package`。
> 项目文档早前推荐的 astrbot-termux APK 也是同类封装问题，现已弃用。

1. 手机浏览器访问 **F-Droid 官方 Termux 页面**：`https://f-droid.org/packages/com.termux/`
   - 也可先装 F-Droid 客户端（`https://f-droid.org`），在客户端内搜索 `Termux` 安装。
2. 下载并安装 `Termux` APK。
3. 打开 Termux，等待基础初始化完成（自动更新软件源）。
4. 依次执行（更新软件源与系统包）：
   ```
   pkg update
   pkg upgrade -y
   ```
   > 首次运行可能较慢，需联网。

---

## 2. 用官方脚本安装 NapCat

在 Termux 终端内粘贴并执行（一行）：

```sh
curl -o install.sh https://ncat.wiki/binary/install_script/install.sh && bash install.sh
```

安装脚本会自动：
- 识别 Termux 环境（基于 `proot` 的 Linux 用户态）
- 下载 NapCat 并配置启动

> 若提示需要镜像加速或挂 NetCat，按脚本提示即可。安装失败的常见修复：
> - `curl` 网络问题：`pkg install curl -y` 后重试
> - proot 相关报错：`pkg install proot -y && pkg upgrade -y` 后重试

---

## 3. 启动 NapCat & 扫码登录

1. 安装脚本跑完后启动 NapCat。
2. 在手机浏览器打开 **WebUI**：`http://127.0.0.1:6099`
   - 界面内会展示一个二维码，用 **QQ 扫码登录**（推荐用小号）。
3. 登录成功后 WebUI 会显示账号在线。

---

## 4. 配置 OneBot 服务（同步器需要）

1. 仍在 NapCat WebUI 中，进入「**网络配置**」。
2. 新建一个 **WebSocket 服务**（反向或正向均可，本项目用**反向 WebSocket**）：
   - 地址留空（本机）或填 `127.0.0.1`
   - **端口填 `3000`（HTTP）与 `3001`（WebSocket）**，或按需取一个不冲突的端口
3. （可选、建议）设置 **Access Token** 并记录。
4. 保存后确认该服务显示 **在线**。

---

## 5. 同步器 App 对接

1. 安装并打开本项目的**同步器 App**（`dist/app-release.apk`）。
2. 点击「**自动探测 NapCat**」：
   - 成功：自动扫描并回填 `ws://…:3001` 与 `http://…:3000`，状态显示「NapCat 在线」，配置已保存。
   - 失败：弹窗提示，可按提示检查 Termux/NapCat 是否启动与登录。
3. 如探测到 HTTP 端口不是 3000，App 会自动推导同主机的 WebSocket 端口（3000→3001）。
4. （可选）若开启了 token，在「Access Token」栏填入相同 token。
5. 点击「保存配置」，再点「启动同步」。

> 探测的判定：会向候选地址发送 `GET /api/get_version`（OneBot 标准端点），
> 只有返回合法 JSON 才认定为 NapCat，避免误连到其它 HTTP 服务。

---

## 6. 常见问题

| 现象 | 处理 |
| --- | --- |
| 报 `Failed to get package context` | Termux 换回 F-Droid 官方版，勿用 Play/第三方封装 |
| 装了标准 Termux 但不会装 NapCat | 执行第 2 步官方 Installer 一行命令 |
| WebUI 打不开 | 确认 NapCat 已启动；浏览器用 `127.0.0.1:6099` |
| 探测不到 | 确认 NapCat 网络服务已「在线」、QQ 已登录，再点自动探测 |
| 端口冲突 | 在 WebUI 改为其它端口，App 会自动扫描发现 |
| 被系统杀后台 | 把 Termux 加入电池优化白名单 |

---

## 7. 风控提示

- NapCat 基于逆向协议，**请务必使用 QQ 小号**，避免主号触发风控封禁。
- 避免高频群发、频繁加群等异常行为。
- 如账号被冻结/限制，建议换号重试。