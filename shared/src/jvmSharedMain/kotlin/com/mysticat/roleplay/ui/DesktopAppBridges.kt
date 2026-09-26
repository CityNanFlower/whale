package com.mysticat.roleplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mysticat.roleplay.data.UpdateInfo
/**
 * 桌面端三个轻量状态桥（1.0.2），模式同 [DesktopFeatures]：
 * 真正摸进程/文件系统的代码在 jvmMain 与 desktopApp，共享层 UI 只观察这些状态。
 */

/**
 * "优雅退出"请求桥：更新脚本/改目录自重启都要"应用自己正常收尾后退出"——
 * desktopApp 在窗口装配时把真退出函数（落窗口状态 → exitApplication）挂进来。
 * null＝没挂上（--smoke/--shot 等早退分支），调用方自行降级。
 */
object DesktopExit {
    var requestExit: (() -> Unit)? = null
}

/**
 * 便携包更新"就绪待确认"状态：DesktopPlatformUi 完成 staging 后填这里，
 * 更新弹窗观察它弹确认层——不再走"toast + 让用户翻资源管理器找脚本"的旧口径。
 */
object DesktopUpdatePrompt {
    /** 待安装的替换脚本全路径；null＝没有待确认的更新 */
    var pendingScript by mutableStateOf<String?>(null)

    /** 包内读到的版本名（本机便携包链路才有；自动下载链路由界面用 expectedVersionName） */
    var pendingVersion by mutableStateOf<String?>(null)

    fun clear() {
        pendingScript = null
        pendingVersion = null
    }
}

/**
 * 旧数据目录提示：启动检查发现旧默认目录还有用户数据时非空。
 * 判定与清理在 jvmMain 的 DesktopOldDataCleanup；这里只承载弹层状态——**绝不自动删**。
 */
object DesktopOldDirNotice {
    var dirWithData by mutableStateOf<String?>(null)
}

/**
 * 启动静默检查查到的新版本：它是 [UpdateFlow] 的一层**薄别名**，只为不改动桌面侧既有的调用点
 * （desktopApp 的托盘链路与 `--smoke` 检查都按这个名字读写）。
 *
 * 状态**只有一份**（[UpdateFlow.info]）：手机入口行右侧那枚小字、桌面第一栏的更新图标、
 * 自动提醒弹窗全读它。早先这里有一份独立的 `info/dismissed`，那样两端就可能各查各的、
 * 出现"手机说有新版、桌面说没有"。
 *
 * 提醒面从"窗口顶部一条不拦指针的提示条"改成了**弹窗**（用户要求：通知/提醒一律弹窗），
 * 所以 `dialogOpen` 这个中间态没有了——点提醒上的「查看更新」直接摊开更新弹窗。
 */
object DesktopUpdateNotice {
    /** 查到的新版本；null＝本次运行没得提示（还没查 / 已是最新 / 查失败 / 用户点了稍后） */
    val info: com.mysticat.roleplay.data.UpdateInfo? get() = UpdateFlow.info

    /** 用户点过「稍后」：本次运行不再自动弹提醒（不持久化，下次启动重新查——"忽略一次"不该变成永久静音） */
    val dismissed: Boolean get() = UpdateFlow.autoPrompted

    fun show(info: com.mysticat.roleplay.data.UpdateInfo) = UpdateFlow.acceptAuto(info)

    fun dismiss() = UpdateFlow.dismissAutoPrompt()

    fun clear() = UpdateFlow.clear()
}
