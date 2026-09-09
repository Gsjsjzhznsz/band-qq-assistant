# worklog（追加式工作日志）

> 格式：每个任务一节，以 `---` 开头。新会话先读 MEMORY.md 再读本文件尾部。

---
Task ID: 1
Agent: main (Super Z)
Task: 建立记忆持久化 + 排查"APP 图标不见"与"MIUIx 未使用"两个用户反馈

Work Log:
- 容器第 3 次重置后重新 clone 仓库（Astroptis/band-qq-assistant，main @ e2f5787）
- 发现 MEMORY.md/worklog.md 从未建立（此前计划未落地），本任务补建
- 排查 APP 图标：`android-sync/app/src/main/AndroidManifest.xml` 的 `<application>` 无 `android:icon` 属性，`res/` 仅有 drawable/ic_launcher_foreground.xml，无 mipmap → 桌面图标消失。修复：添加自适应图标（mipmap-anydpi-v26 + 背景渐变 + 前景白气泡三圆点）
- 排查 MIUIx：build.gradle.kts 依赖 `top.yukonga.miuix.kmp:miuix-ui-android:0.9.3` + `miuix-icons-android:0.9.3` —— **坐标在 Maven Central 真实存在**（0.9.x 主模块改名 miuix-ui）；全部 UI 文件（BandQQApp/Theme/Home/Settings/Contact/History/LogPanel）已使用 miuix 组件。结论：代码已 MIUIx 化，用户手机上是旧 APK → 上次构建未交付。残留问题：HistoryDetailSheet.kt 混用 material3 AlertDialog/Text；Settings 页未用 Hyperceiler 式 preference 组件
- 用 Maven Central API + miuix v0.9.3 源码验证 API：SwitchPreference/ArrowPreference（preference 模块）、WindowDialog（window 模块）、icon.extended.{Home,Contacts,Messages,Settings}
- 计划：加 miuix-preference-android:0.9.3 依赖；Settings 页重构为 Hyperceiler 分组风格（SwitchPreference/ArrowPreference）；HistoryDetailSheet 去 material3；重建 Android SDK；编译验证；push

Stage Summary:
- 图标根因：manifest 缺 android:icon（已修，待编译验证）
- MIUIx 根因：代码已用 miuix 0.9.3，用户未拿到新 APK；补 preference 组件与对话框替换后重新构建交付
- miuix 0.9.x 模块/包名映射已写入 MEMORY.md

---
Task ID: 2
Agent: main (Super Z)
Task: 修复构建环境并产出 v1.2.0 APK（图标修复 + Hyperceiler 风格 UI 收尾）

Work Log:
- 重建构建环境：OpenJDK 系统自带为 JRE 缺 javac → 下载 Temurin JDK 17 到 /home/z/tools/jdk-17.0.20.1+1；Gradle 8.13 → /home/z/tools/gradle-8.13；Android SDK → /home/z/android-sdk（cmdline-tools + build-tools;37.0.0 + platform-tools + platforms;android-37.0）
- **新发现（修正 MEMORY.md 旧配方）**：AGP 8.13.2 解析平台基于 package.xml 的 path + source.properties 的 ApiLevel，symlink android-37 → android-37.0 会因 "inconsistent location" 被拒。**正确修复**：目录直接 mv android-37.0 → android-37，且 package.xml 内 path="platforms;android-37.0" 改 "platforms;android-37"、source.properties 内 AndroidVersion.ApiLevel=37.0 改 37。gradle.properties 保留 android.suppressUnsupportedCompileSdk=37.0
- 编译错误修复 ×2：ConfigManager.save 中 AUTO_START 行误插在 edit{} 闭包外；WindowDialog 0.9.3 签名为 (show: Boolean, title, ..., onDismissRequest, content)，LocalDismissState 在 theme 包（非 window）
- miuix 0.9.3 AAR metadata 要求 minCompileSdk 37 → compileSdk 必须为 37（36 + disableAutomaticAarMetadataCheck 属性在 AGP 8.13 已失效）
- 图标修复验证：aapt2 dump badging 显示 application icon 已挂载自适应图标（mipmap-anydpi-v26/ic_launcher.xml + 蓝渐变背景 + 白气泡三圆点前景 + monochrome）
- Android 端 Hyperceiler 风格收尾：SettingsScreen 重构为「SmallTitle 分组 + Card」形态（SnowLuma 服务器 / 通用 / 操作 三组）；新增「启动时自动同步」SwitchPreference（miuix-preference 0.9.3）+ ConfigManager.autoStart 字段 + MainActivity AutoStartLauncher；HistoryDetailSheet 的 material3 AlertDialog/Text 全部替换为 miuix WindowDialog/Text/Card
- keystore 随容器重置丢失 → 重新生成，新 SHA-256: 0F:C5:F2:F3:15:F2:38:C0:AC:45:1E:F8:3E:CA:B1:D1:D5:95:02:7E:30:31:92:BB:E5:BA:C9:B3:E0:AC:B2:1C（**旧版 APK 需卸载后才能装新版**）
- 产出：bandqq-sync-release-1.2.0.apk（versionCode 2，11.3MB）已交付 download/

Stage Summary:
- v1.2.0 APK 构建成功并交付；APP 图标恢复；UI 全 miuix 化（material3 残留清零）+ Hyperceiler 分组式设置页 + 自动同步开关
- 构建配方已修正（平台目录重命名方案），下轮容器重建时按此执行
