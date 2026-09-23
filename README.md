# 🐳 鲸鱼 · 本地 AI 角色扮演（Android + Windows 桌面）

**完全本地、BYOK（Bring Your Own Key）的 AI 角色扮演应用。**
没有后端、没有数据库、没有任何账号体系——角色、会话、图片全部存在你自己的设备上，
对话直连你自己选择的模型服务。数据不出机器，key 是你的，卡是你的。

> ⚠️ **本项目只是一个客户端**：不内置、不转售、不提供任何模型或语音额度。
> 使用时需要自备 OpenAI 兼容的对话服务 API Key；语音合成（火山/腾讯/阿里）与音色复刻是各家的**付费服务**，
> 需在对应控制台开通并保证余额。未配置 key 时应用照常可以管理与阅读本地数据，只是不能对话。

> 🚧 当前处于 Alpha 内测阶段（Android `0.2.x` / Windows `1.1.0`）：核心功能已可用，
> 界面与数据结构仍可能变动。安装包在本仓库 **Releases** 页下载（Android 为 `.apk`，桌面为便携 `zip` 与安装器）。

## 功能一览

- **角色卡四种形态**：陪伴（常规角色扮演）/ 多线（一卡多条开场随机抽）/ 玩法（有规则的互动：二十问、海龟汤这类，
  进度自动记录）/ 工具（给素材出成品：总结、翻译、润色…）。列表按形态筛选，灵感创作可按形态生成新卡。
- **酒馆（SillyTavern）角色卡导入**：兼容 `chara_card_v2`，各字段分别保真（作者/版本/留言不丢），
  没写头像的卡直接用卡图立绘，支持 `{{char}}` / `{{user}}` 占位符。
- **多会话聊天**：流式逐字输出、Markdown 渲染、气泡可分享；每个角色可开任意多个会话，历史自动保存。
- **语音**：多家供应商 TTS 朗读（内置音色 / 混合音色 / 复刻音色，音色复刻可在应用内直接录音训练）、
  角色专属音色与音色预设、语音输入、背景音乐（CC0 曲库）。
- **AI 生图**：头像与聊天背景接入任意 OpenAI 兼容的 `/images/generations` 服务，也可用本地相册图。
- **发现页**：内置精选主题卡包（全部素材来源与许可见 [THIRD-PARTY.md](THIRD-PARTY.md)），一键导入。
- **数据备份**：设置页可把全部角色与会话导出成 JSON 随时恢复（不含 API Key），格式开放、不锁数据。
- **桌面版是正经桌面应用**：大窗口 + 侧边栏导航 + 左右分栏，Enter 发送 / Shift+Enter 换行，右键菜单，
  关闭窗口可缩到托盘、托盘内直接检查并安装更新；启动时也会静默检查更新。
- 多模型供应商预设与价格信息、自定义提示词与携带历史条数、崩溃自愈与现场记录。

## 技术栈与架构

- Kotlin + Compose：Android 用 Jetpack Compose（Material 3），桌面用 Compose Multiplatform（JVM）。
- 无数据库：kotlinx-serialization 读写本地 JSON；OkHttp 调 OpenAI 兼容接口；Coil 加载图片。
- 模块结构（KMP 共享层 + 双端入口）：

```
app/         # Android 薄壳（入口、权限、系统集成）
shared/      # KMP 共享模块
 ├── commonMain      # 纯 Kotlin：模型、提示词装配等（看不到 java.io）
 ├── jvmSharedMain   # 全部 UI 与 JVM 数据层
 ├── androidMain     # Android 侧实现
 └── jvmMain         # 桌面侧实现
desktopApp/  # 桌面入口与打包（jpackage）
```

- 版本线两条、互相独立：Android（`0.2.x`）与桌面（`1.x`），靠更新清单里的 `alignedAndroid` 字段追溯对齐关系。
- 更多设计文档：[代码结构总览](docs/鲸鱼-代码结构总览.md) ·
  [提示词与上下文](docs/鲸鱼-提示词与上下文总览.md) ·
  [模型接入与价格梳理](docs/鲸鱼-API与模型接入梳理.md)

## 从源码构建

### Android

1. 安装 [Android Studio](https://developer.android.com/studio)（或仅命令行 + Android SDK）。
2. 仓库自带 Gradle wrapper 的 jar 与配置（Gradle 8.13），但**未附带 `gradlew` 启动脚本**
   （首次用命令行构建可在根目录执行一次 `gradle wrapper` 补全，或直接用下面这条等价命令）。
3. 构建 debug APK（`JAVA_HOME` 指向 JDK 21）：

```bash
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

minSdk 26 / targetSdk 34 / compileSdk 36。首次运行只需在设置页填入自己的 Base URL 与 API Key。

### Windows 桌面

```bash
# 需要 JDK 21（须自带 jpackage/jlink/jmods，如 JetBrains JBR 21；新版 Android Studio 的 JBR 25 不含 jpackage）
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :desktopApp:run
# 打包可分发目录：同上一条命令，任务换成 :desktopApp:createDistributable
```

桌面数据默认在 `%APPDATA%\MysticatRoleplay\`（开发态直跑用 `-dev` 目录，与安装版互不干扰）。

## 首次使用（不写代码）

1. 安装应用（APK / 便携 zip 解压即用），打开右上角 ⚙️ 设置。
2. 填对话接口（以 DeepSeek 为例）：Base URL `https://api.deepseek.com/v1`、API Key `sk-...`、
   模型 `deepseek-chat`，点「测试对话接口」应看到成功提示。
3. 返回首页新建角色（或导入酒馆 JSON 卡、从发现页导入精选卡），进入聊天即可。
4. 可选：打开 AI 生图（头像/背景）、配置 TTS 语音（付费服务，需自备额度）。

## 隐私与数据位置

- 对话请求只从你的设备发往**你自己填写**的接口地址，本应用没有任何自有服务器。
- API Key 在 Android 侧经系统 Keystore 加密后存储；备份 JSON 不含密钥。
- 数据目录：Android 在应用私有目录（`files/roleplay/`）；桌面在 `%APPDATA%\MysticatRoleplay\roleplay\`。
  卸载前请先在设置页导出备份。

## 许可证与素材

- 代码以 **AGPL-3.0** 授权（见 [LICENSE](LICENSE)）：你获得的自由同样适用于改动之后。
- 随仓库分发的美术与音频素材来源及许可逐条列在 [THIRD-PARTY.md](THIRD-PARTY.md)
  （BGM 全部 CC0；精选卡配图来自 Pixabay/Pexels；启动图标为 AI 生成）。

## 已知边界（Alpha 阶段）

- **世界书（酒馆卡的 `character_book` 世界观词条）暂不支持**，导入时会被丢弃（不会破坏卡的其他内容），正在开发。
- 卡自带的「系统提示词」目前只保存与导出、不参与对话；占位符只认 `{{char}}` / `{{user}}` 等少数几个。
- 包名 `com.mysticat.roleplay` 与桌面数据目录名属历史遗留，**保留不改**
  （包名＝应用身份，换名会让老用户覆盖安装失败、已存 API Key 解不开）。

## 反馈

问题与建议欢迎开 Issue。遇到闪退请到「我的 → 版本与安全 → 异常记录」复制内容附上，能直接定位。
