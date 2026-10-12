# 第三方素材与许可（NOTICE）

本项目（鲸鱼）**代码**以 **AGPL-3.0** 发布，全文见 `LICENSE`。
仓库内的第三方**素材**各自独立授权，清单如下——**它们不因代码的 AGPL 而变成 AGPL**，
再分发或商用请照各自的许可办事。

## 内置环境音（6 档）

`shared/assets/bgm/*.wav`（雨 / 海浪 / 篝火 / 森林 / 溪流 / 夜虫）
来源 **OpenGameArt.org**，许可 **CC0 1.0（公共领域奉献）**：可随安装包自由分发、**无署名义务**。
逐档的作品页、作者、原文件名、采样率与加工记录（以及为什么没用 Freesound）见
**`shared/assets/SOURCES.txt`**。

## 发现页精选内容

- 角色卡的**头像与背景**（15 张）：来源 **Pixabay**，许可 **Pixabay Content License** —— 免费商用、无需署名、**禁止把原图当独立素材转售/再分发**。
- 分组**封面**（4 张）：来源 **Pexels**，许可 **Pexels License** —— 同口径。
- 逐张原始 URL 与许可说明见 **`dist/discover/credits.txt`**（该文件随精选包生成时一并产出，换图后重新生成即同步）。
- 卡片文案（卡名 / tagline / 人设 / 开场白）为本项目原创或经 AI 生成后人工校订，未使用现有 IP 的角色名。

## 应用图标

各尺寸启动图标（Android `app/src/main/res/` 与桌面 `desktopApp/src/main/resources/icon/`）：**由 AI 生成**，
不涉及任何第三方素材授权，无需署名。

## 字体

**程序与界面**：仓库不自带任何界面字体文件，一律使用系统字体（用户也可在设置页自行指定本机字体，
那是本机行为，不随本项目分发）。

**皮肤主题字体**（2026-10-07 起，作为独立文件挂在发布附件里、由 App 按需下载，不打进安装包；
四份均按 GB2312 常用字表子集化，许可协议都是 SIL Open Font License 1.1，OFL 附录的保留字体名条款以
上游原文件为准）：

| 文件 | 字体 | 上游 | 许可 |
|---|---|---|---|
| `whale-notoserif.ttf` | 思源宋体（Noto Serif SC Regular） | github.com/notofonts/noto-cjk | SIL OFL 1.1 |
| `whale-yozai.ttf` | Yozai Regular | github.com/lxgw/yozai-font | SIL OFL 1.1 |
| `whale-wenkai.ttf` | 霞鹜文楷（LXGW WenKai Regular） | github.com/lxgw/LxgwWenKai | SIL OFL 1.1 |
| `whale-notomono.ttf` | Noto Sans Mono CJK SC Regular | github.com/notofonts/noto-cjk | SIL OFL 1.1 |

## 角色卡格式

兼容 Tavern / SillyTavern 的 `chara_card_v2` 与 `chara_card_v3`（`ccv3` chunk）**开放格式**，
为自行实现，**不含**上述项目的代码。

## 代码依赖

Kotlin、Compose（Multiplatform / Material 3）、OkHttp、kotlinx-serialization 等经 Gradle 引入的依赖，
各自沿用其原许可（多为 Apache-2.0），版本见 `gradle/libs.versions.toml`。
