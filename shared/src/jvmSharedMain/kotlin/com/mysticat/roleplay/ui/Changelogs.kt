package com.mysticat.roleplay.ui

/**
 * 应用内更新日志：**只保留当前版本**，历史版本走
 * [AppLinks.CHANGELOG_DOC_URL] 的金山文档，避免应用内越积越长。
 *
 * 为什么两端各一份常量：两个平台**版本号独立编**（Android 与桌面各走各的版本线），
 * 各自发的版、各自记的账；且桌面首版的内容里有"桌面特有"的条目（便携包替换、系统朗读走
 * Windows 语音等），混在一份里会误导用户。运行时按 [Platform.ui] 的 `platformId` 取其一。
 *
 * 更新方式：发版时改写对应那份（桌面发版改 [DESKTOP]，Android 发版改 [ANDROID]）。
 */
internal object Changelogs {

    /** Android 当前版本的内置更新日志（对外日志的精简版，`· ` 前缀） */
    const val ANDROID = """0.3.0-alpha.3（2026-10-12）
· 新增 OpenRouter / xAI（Grok）/ 小米 MiMo 三家 API 供应商（对话与创作模型齐配，语音 / 生图按各家能力配齐）
· 模型答空自动重试：预算不足先抬预算、截断时翻倍预算再发，单轮最多 2 次；提示区分截断与漏答
· 思考强度档位按各家官方参数补齐，设置与聊天页统一滑杆控件（DeepSeek 四档：关闭 / 较低 / 默认 / 深度）
· 对话风格默认「对话为主」（台词为主、动作括号穿插），默认短段落 ≤150 字；显式选档仍可覆盖
· 聊天背景恢复原图直铺（回退上一版「按图自动适配主题」）
· 用量热力图修复：末列不再被挤扁、格子撑大到约 40dp、点选与取消更顺手、明细卡移到图下"""

    /** Windows 桌面版当前版本的内置更新日志（发版时替换成对外日志的精简版） */
    const val DESKTOP = """1.7.2（2026-10-12）· 功能对齐 Android 0.3.0-alpha.3
· 新增 OpenRouter / xAI（Grok）/ 小米 MiMo 三家 API 供应商
· 模型答空自动重试（区分截断与漏答）；思考强度档位补齐并统一为滑杆控件
· 对话风格默认「对话为主」＋默认短段落 ≤150 字
· 聊天背景恢复原图直铺（回退上一版「按图自动适配主题」）
· 用量热力图修复：末列不再被挤扁、格子约 40dp、滚轮 + 两侧箭头、点选 / 取消、明细卡移到图下"""


    /** 按平台取当前版本的日志文本 */
    val current: String
        get() = if (com.mysticat.roleplay.ui.Platform.ui.platformId() == "desktop") DESKTOP else ANDROID
}
