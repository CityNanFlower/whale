package com.mysticat.roleplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

/**
 * 桌面专属「窗口之外的行为」桥：系统托盘 / 关闭时缩小到托盘 / 开机自启。
 *
 * 为什么需要这层桥：真正摸 AWT `SystemTray` 与 Windows 注册表（reg.exe）的代码只能住在
 * desktopApp——jvmSharedMain 会被 Android 一起编译，那边没有 java.awt。而开关在共享 UI 的
 * 「外观与设置」里，所以这里只放**状态与接口**，实现由 desktopApp 在启动时 [install]。
 * 模式与 `DesktopShortcuts` 相同：共享层留桥，桌面端挂实现。
 *
 * 未 install（--shot 离屏渲染 / Android）时全部状态是安全默认值：托盘不支持、开关全关。
 */
object DesktopFeatures {
    interface Impl {
        /** 系统托盘可用（`SystemTray.isSupported()`）；不可用时「关闭时缩小到托盘」置灰 */
        val traySupported: Boolean

        /** 开机自启可配置——只有经 jpackage 启动器（exe）跑起来才知道该往注册表写谁 */
        val launchAtLoginSupported: Boolean

        /** 「关闭时缩小到托盘」变化：加/移除托盘图标（实现须幂等） */
        fun applyTraySetting(enabled: Boolean)

        /** 写/删 HKCU Run；返回是否生效（失败时 UI 保持原状态并提示） */
        fun applyLaunchAtLogin(enabled: Boolean): Boolean

        /** 只读查询当前自启状态（注册表本身是持久层，开关初值从这里来） */
        fun queryLaunchAtLogin(): Boolean
    }

    var impl: Impl? = null
        private set

    /** 关闭窗口时缩小到托盘（false＝维持原行为：直接退出）。随改随落盘，不等正常退出 */
    var hideToTrayOnClose by mutableStateOf(false)
        private set

    var traySupported by mutableStateOf(false)
        private set

    var launchAtLoginSupported by mutableStateOf(false)
        private set

    /**
     * 「以后自动安装更新」（1.0.2）：true＝便携包 staging 完成后不再弹确认层，
     * 直接拉替换脚本并退出。勾选入口在更新确认弹层里，随改随落 desktop.properties。
     */
    var autoInstallUpdate by mutableStateOf(false)
        private set

    private lateinit var prefsFile: File

    /**
     * desktopApp 在**正常窗口路径**安装（--smoke/--shot 不碰托盘与注册表）。
     * [prefs] 是「不随数据目录走」级别的本机偏好文件（见定位讨论），
     * 与 window.properties 分开——那两份只在正常退出时写，开关要求**随改随落**。
     */
    fun install(i: Impl, prefs: File) {
        impl = i
        prefsFile = prefs
        hideToTrayOnClose = runCatching {
            val p = Properties()
            if (prefs.isFile) prefs.inputStream().use { p.load(it) }
            p.getProperty("hideToTray")?.toBoolean() ?: false
        }.getOrDefault(false)
        autoInstallUpdate = runCatching {
            val p = Properties()
            if (prefs.isFile) prefs.inputStream().use { p.load(it) }
            p.getProperty("autoInstallUpdate")?.toBoolean() ?: false
        }.getOrDefault(false)
        traySupported = runCatching { i.traySupported }.getOrDefault(false)
        launchAtLoginSupported = runCatching { i.launchAtLoginSupported }.getOrDefault(false)
    }

    fun setHideToTray(enabled: Boolean) {
        hideToTrayOnClose = enabled
        runCatching {
            prefsFile.parentFile?.mkdirs()
            val p = Properties()
            if (prefsFile.isFile) prefsFile.inputStream().use { p.load(it) }
            p.setProperty("hideToTray", enabled.toString())
            p.store(prefsFile.outputStream(), "鲸鱼桌面应用行为设置（托盘/自启）")
        }
        runCatching { impl?.applyTraySetting(enabled) }
    }

    fun setAutoUpdateInstall(enabled: Boolean) {
        autoInstallUpdate = enabled
        runCatching {
            prefsFile.parentFile?.mkdirs()
            val p = Properties()
            if (prefsFile.isFile) prefsFile.inputStream().use { p.load(it) }
            p.setProperty("autoInstallUpdate", enabled.toString())
            p.store(prefsFile.outputStream(), "鲸鱼桌面应用行为设置（托盘/自启/自动更新）")
        }
    }

    fun setLaunchAtLogin(enabled: Boolean): Boolean =
        impl?.let { runCatching { it.applyLaunchAtLogin(enabled) }.getOrDefault(false) } ?: false

    fun queryLaunchAtLogin(): Boolean =
        impl?.let { runCatching { it.queryLaunchAtLogin() }.getOrDefault(false) } ?: false
}
