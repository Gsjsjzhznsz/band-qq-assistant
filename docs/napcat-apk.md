# NapCat APK 集成说明

NapCatQQ 无法作为代码库内嵌进同步器 App 进程（Node + NTQQ 需要 Linux 用户态）。
本项目采用"一体化 APK 组合"：NapCat 跑在手机本机的 Termux + proot 容器内，同步器 App 通过
`ws://127.0.0.1:3001` 连接它。

## 1. 获取 NapCat APK

社区已有开箱即用方案（基于 Termux 封装，无需 root、无需电脑）：

- 项目：https://github.com/linger-su/astrbot-termux
- Releases 下载最新版 APK，安装后按提示等待首次初始化（约 5-15 分钟，需 WiFi）。
- 初始化完成后，NapCat WebUI 地址：http://localhost:6099 （扫码登录 QQ）

> 也可自行用 NapCat-Installer 脚本在 Termux 内安装：https://github.com/NapNeko/NapCat-Installer

## 2. OneBot 服务配置

1. 打开 NapCat WebUI（http://localhost:6099），扫码登录 QQ。
2. 进入"网络配置"，新建 WebSocket 服务，端口设为 **3001**。
3. 记录 access token（可选，建议开启）。
4. 保存后确认 OneBot 服务显示"在线"。

## 3. 与同步器对接

1. 在同步器 App 中确认 NapCat WS 地址为 `ws://127.0.0.1:3001`，HTTP 地址为 `http://127.0.0.1:3000`。
2. 如开启 token，将同一 token 填入同步器设置页。
3. 点击"探测 NapCat"确认在线。

## 4. 常见问题

- 端口不通：确认 NapCat WebUI 已配置并保存，且 QQ 已扫码登录。
- 杀后台：将 NapCat APK 加入电池优化白名单。
- 被封风险：NapCat 基于逆向协议，请使用 QQ 小号，避免高频操作。
