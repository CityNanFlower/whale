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
    const val ANDROID = """0.3.0-alpha.4（2026-10-12）
· 会话「模型与思考强度」浮层改完不再自动收起：换模型 / 调档位后可接着改，点浮层外或按 Esc 才收起
· 灵感创作页的模型胶囊补「思考强度」二级页，可直接调本次使用 / 全局默认的思考档位
· 输出预算弹窗精简为两项旋钮：思考强度统一到「模型与思考强度」里调（「节省」档的联动保留）"""

    /** Windows 桌面版当前版本的内置更新日志（发版时替换成对外日志的精简版） */
    const val DESKTOP = """1.7.3（2026-10-12）· 功能对齐 Android 0.3.0-alpha.4
· 会话「模型与思考强度」浮层改完不再自动收起：换模型 / 调档位后可接着改，点浮层外或按 Esc 才收起
· 灵感创作页的模型胶囊补「思考强度」二级页，可直接调本次使用 / 全局默认的思考档位
· 输出预算弹窗精简为两项旋钮：思考强度统一到「模型与思考强度」里调（「节省」档的联动保留）"""


    /** 按平台取当前版本的日志文本 */
    val current: String
        get() = if (com.mysticat.roleplay.ui.Platform.ui.platformId() == "desktop") DESKTOP else ANDROID
}
