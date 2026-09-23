package com.mysticat.roleplay.ui

import androidx.compose.ui.input.key.KeyEvent

/**
 * 桌面全局快捷键的桥（M11 ①）。
 *
 * 按键在窗口层（desktopApp/Main.kt 的 `Window.onPreviewKeyEvent`）最先到达，但"切页/收起侧栏/
 * 新建会话"这些状态全住在 [DesktopMainScreen] 的组合里，窗口层够不着——所以用与
 * [DesktopShellPrefs] / [Platform.ui] 同款的全局桥：外壳每帧把自己当前的处理函数挂进来，
 * 窗口层收到按键先问它。
 *
 * Android 侧不挂 handler，`onPreviewKeyEvent` 也不存在，零影响。
 */
object DesktopShortcuts {
    /** 返回 true = 已消费，窗口层不要再处理。由 [DesktopMainScreen] 用 SideEffect 挂最新闭包 */
    var handler: ((KeyEvent) -> Boolean)? = null

    /**
     * Ctrl 是否按着——给"Ctrl+滚轮调字号"用。滚轮走 pointer 事件，**拿不到键盘修饰符**
     * （PointerEvent 的修饰符 API 不是双端都有），只能由窗口层把每个按键事件里 Ctrl 的
     * 按下状态记在这里。
     */
    var ctrlPressed: Boolean = false
}
