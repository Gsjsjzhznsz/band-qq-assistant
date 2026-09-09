# band-qq 项目记忆（MEMORY.md）

> 每个新会话开始时，AI 助手必须先读本文件与 `worklog.md` 恢复上下文。
> 本文件记录稳定不变的项目事实；过程性进展记录在 `worklog.md`。

## 项目是什么

小米手环 9 Vela 快应用（手环 QQ 客户端）+ Android 同步器。
链路：手环 ⇄ 蓝牙 interconnect ⇄ Android 同步器 ⇄ OneBot WS(:3001)/HTTP(:3000) ⇄ SnowLuma（QQ 协议端，https://github.com/SnowLuma/SnowLuma，OneBot v11 实现，自带 Node，WebUI 端口 5099）。

- 仓库：https://github.com/Astroptis/band-qq-assistant（main 分支 = 唯一事实基线）
- fork 小号：Gsjsjzhznsz（PAT 在本地构建脚本/此前会话使用）
- 目录：`band-qq/`（手环快应用，aiot-toolkit 2.0.5）、`android-sync/`（Android 同步器）、`docs/`、`scripts/`（Windows 构建脚本）、`dist/`（产物，已 gitignore）

## 用户需求栈（按优先级，持续维护）

1. 【P0】手环性能：不能重启死机。根因 = 渲染负载（长列表全量 DOM、高频 setState 引发抖动）+ 大数据帧传输。原则：**不影响体验的处理全部放 Android APP 端，手环只收精简数据**（CQ 码剥离、消息格式化、头像色计算、分页都在手机端做）。
2. 手环端图标白边（rpk manifest 图标配置/资源透明边距）。
3. Android APP 图标消失（根因已查明：manifest 无 android:icon 且无 mipmap 资源 → 已修）。
4. APP UI 必须使用 MIUIx（miuix 库），参考 **com.sevtinge.hyperceiler**（Hyperceiler，HyperOS 风格标杆）。
5. 手机离线缓冲：快应用关闭 → 停 interconnect 发送，OneBot 保持在线，消息落盘缓存，冷启动回放推送。
6. 快捷回复按钮剥离原始 CQ 码（OneBotParser.kt 手机端处理）。
7. Stapxs-QQ-Lite-X 调研（数据管理 + 多账号）；SnowLuma 集成形态确认（它是外部协议端程序，非 UI 库）。
8. 无障碍服务易用性；手环字库扩至 ~20902 CJK 字；历史翻页 before_time 修正；构建交付 rpk+APK；README 上游说明+SEO。

## 技术事实（已验证）

### 构建配方（Android）
- AGP 8.13.2 + Gradle 8.13 + compileSdk 37 + minSdk 26 + targetSdk 34 + Kotlin 2.4.10 + JDK 17+（容器现成 OpenJDK 21 可用）
- **目录陷阱**：sdkmanager 装出 `platforms/android-37.0`，AGP 找 `android-37` → 需 `ln -sfn android-37.0 platforms/android-37`，并在 gradle.properties 加 `android.suppressUnsupportedCompileSdk=37.0`
- xms-wearable-lib：`android-sync/app/libs/xms-wearable-lib_1.4_release.aar`（已入库）
- 签名：keystore.jks（bandqq/bandqq123），rpk 与 APK 同证书 SHA-256 `af8819e27a6ec8d84537ec86937cf016e780376305328abaa4eb79e8c626b004`
- 容器无 /dev/kvm；无模拟器。UI 验证替代 = aiot-toolkit 浏览器预览 + Playwright 截图

### miuix 库（MIUIx for Compose，Hyperceiler 同源）
- 坐标（Maven Central，groupId `top.yukonga.miuix.kmp`）：**0.9.3 正式版**在 `miuix-ui-android` + `miuix-icons-android` + `miuix-preference-android` + `miuix-core-android`（0.8.x 时代主模块叫 `miuix-android`，0.9.x 拆分改名）
- 0.9.3 关键 API：`theme.MiuixTheme`、`basic.{Scaffold, SmallTopAppBar, NavigationBar, NavigationBarItem, Card, Button, TextButton, Text, TextField, SmallTitle, Checkbox}`、`icon.MiuixIcons` + `icon.extended.{Home, Contacts, Messages, Settings}`（icons 模块）
- Hyperceiler 的 SuperSwitch/SuperArrow 在 0.9.x 改名为 **preference 模块**：`preference.SwitchPreference(checked, onCheckedChange, title, summary)`、`preference.ArrowPreference(title, summary, onClick, holdDownState)`
- 对话框：`window.WindowDialog(title, summary, onDismissRequest, content)`（旧 SuperDialog 的继任）
- miuix 依赖 jetbrains compose（KMP），Android 端自动解析到 androidx.compose，与项目 BOM 共存无冲突

### OneBot 时间坑
- `before_time` 需与 message_seq 锚点配合；OneBot time = Unix 秒(10位)，本地毫秒(13位)，用 normalizeTime 统一并保持升序

## 环境与恢复

- 容器会周期性重置（已 3 次）：/opt/jdk、/opt/android-sdk 丢失，仓库需重新 clone
- 恢复步骤：clone 仓库 → 装 Android SDK（cmdline-tools + platforms;android-37 + build-tools;37.0.0）→ 符号链接修复 → 编译
- 记忆协议：本文件 + worklog.md 每里程碑 push，新会话克隆即恢复

## GitHub 操作

- PAT（ghp_ 开头的个人令牌，属 Gsjsjzhznsz 小号）**不写入本文件**（GitHub secret scanning 会拒绝 push），由会话上下文/本地凭据提供
- push 目标：小号 fork `Gsjsjzhznsz/band-qq-assistant`；主账号 Astroptis 仓库只读
