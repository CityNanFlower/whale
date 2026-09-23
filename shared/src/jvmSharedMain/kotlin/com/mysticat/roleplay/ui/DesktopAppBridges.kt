package com.mysticat.roleplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mysticat.roleplay.data.UpdateInfo

/**
 * 桌面端三个轻量状态桥（1.0.2 台账 1/2/3），模式同 [DesktopFeatures]：
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
 * 便携包更新"就绪待确认"状态（台账 2）：DesktopPlatformUi 完成 staging 后填这里，
 * 「版本与安全」弹窗观察它弹确认层——不再走"toast + 让用户翻资源管理器找脚本"的旧口径。
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
 * 旧数据目录提示（台账 3）：启动检查发现旧默认目录还有用户数据时非空。
 * 判定与清理在 jvmMain 的 DesktopOldDataCleanup；这里只承载弹层状态——**绝不自动删**。
 */
object DesktopOldDirNotice {
    var dirWithData by mutableStateOf<String?>(null)
}

/**
 * 启动静默检查查到的新版本（台账 9(a)）：desktopApp 启动后查一次更新，只有**真查到更新的版本**
 * 才把结果填进来，界面据此显示一条可关闭的提示条（[com.mysticat.roleplay.ui.DesktopUpdateNoticeHost]）。
 *
 * 为什么要这条提示面：托盘的更新入口只在「关闭时缩小到托盘」开着时才存在（默认关，且 Win11 会把
 * 新注册的图标塞进「显示隐藏的图标」浮层），而**托盘气泡也依赖那个图标**——只靠托盘，
 * 没开托盘的用户永远收不到更新提示。提示条挂在窗口顶层，与托盘设置无关，对所有人成立。
 */
object DesktopUpdateNotice {
    /** 查到的新版本；null＝本次运行没得提示（还没查 / 已是最新 / 查失败 / 用户忽略了） */
    var info by mutableStateOf<UpdateInfo?>(null)
        private set

    /** 用户点了「忽略」：本次运行不再提示（不持久化，下次启动重新查——"忽略一次"不该变成永久静音） */
    var dismissed by mutableStateOf(false)
        private set

    /** 「去更新」摊开的「版本与安全」弹窗状态 */
    var dialogOpen by mutableStateOf(false)

    fun show(info: UpdateInfo) {
        this.info = info
        dismissed = false
    }

    fun dismiss() {
        dismissed = true
    }

    fun clear() {
        info = null
        dismissed = false
        dialogOpen = false
    }
}
