package com.mysticat.roleplay.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * 一条键位组合（桌面全局快捷键）：修饰键 ＋ 主键。
 *
 * 落盘为人读得懂的串（`"Ctrl+B"` / `"Alt+Shift+S"`），存在 `AiSettings.shortcuts`
 * （键为 [DesktopShortcutAction.id]）。主键取值走**白名单**（[DesktopKeyCombo.KEY_NAMES]）：
 * 方向键 / Enter / Tab / 空格这些在界面里有专属意义的键一律不可配，防止改一次键就把
 * 打字或导航弄坏。
 */
data class DesktopKeyCombo(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val key: Key
) {
    /** 人读形态：`Ctrl+Alt+N`。键名表与解析表同一份（[KEY_NAMES]） */
    fun encode(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (alt) append("Alt+")
        if (shift) append("Shift+")
        append(KEY_NAMES[key] ?: key.keyCode.toString())
    }

    fun matches(e: KeyEvent): Boolean =
        e.isCtrlPressed == ctrl && e.isAltPressed == alt && e.isShiftPressed == shift && e.key == key

    companion object {
        /** 从落盘串解析回来；null = 无效串（正常保存不会产生，只防手改 JSON） */
        fun parse(s: String): DesktopKeyCombo? {
            if (s.isBlank()) return null
            val parts = s.split("+")
            val key = KEY_NAMES.entries.firstOrNull { it.value == parts.last() }?.key ?: return null
            val mods = parts.dropLast(1).toSet()
            return DesktopKeyCombo(
                ctrl = "Ctrl" in mods, alt = "Alt" in mods, shift = "Shift" in mods, key = key
            )
        }

        /** 从按键事件提取（修饰键状态就在事件字段里） */
        fun fromEvent(e: KeyEvent): DesktopKeyCombo = DesktopKeyCombo(
            ctrl = e.isCtrlPressed, alt = e.isAltPressed, shift = e.isShiftPressed, key = e.key
        )

        /** 主键白名单（键 → 键名，双向同表）。字母/数字/F 键/常用标点；反向查用 value 匹配 */
        internal val KEY_NAMES: Map<Key, String> = buildMap {
            listOf(
                Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J,
                Key.K, Key.L, Key.M, Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T,
                Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z
            ).forEachIndexed { i, k -> put(k, ('A' + i).toString()) }
            listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
                .forEachIndexed { i, k -> put(k, i.toString()) }
            listOf(Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)
                .forEachIndexed { i, k -> put(k, "F${i + 1}") }
            put(Key.Comma, ","); put(Key.Period, "."); put(Key.Minus, "-"); put(Key.Equals, "=")
            put(Key.Semicolon, ";"); put(Key.Slash, "/"); put(Key.Backslash, "\\")
            put(Key.LeftBracket, "["); put(Key.RightBracket, "]")
            put(Key.Enter, "Enter")
        }

        /** F 键（唯二允许裸按的：F 键不参与打字） */
        internal val FUNCTION_KEYS: Set<Key> = setOf(
            Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12
        )

        /** 修饰键本身（按下它们是"还在组合中"，不算主键） */
        internal val MODIFIER_KEYS: Set<Key> = setOf(
            Key.CtrlLeft, Key.CtrlRight, Key.AltLeft, Key.AltRight,
            Key.ShiftLeft, Key.ShiftRight, Key.MetaLeft, Key.MetaRight
        )
    }
}

/**
 * 可配置的桌面动作（注册表）：设置页「快捷键」逐条列出与改键的依据。分两组：
 *
 * - [Scope.GLOBAL]：窗口层处理（`desktopApp/Main.kt` 的 preview → `MainScreen.onShortcut`），
 *   焦点在哪都生效；[Scope.INPUT]：输入框本地的输入行为（`ChatPanels.desktopKeys`）或
 *   输入相关的窗口层分支（粘贴图片）。**输入框聚焦时输入组优先、全局层让位**——所以
 *   允许"全局/输入两组同键"（默认的收栏与插「」同为 Ctrl+B 就靠这条语义共存）；
 *   **同组内互斥**（一个键不能在同一组里干两件事）。
 *
 * **不进表**：`Esc`（系统返回语义，捕获时=取消改键）；`Ctrl+滚轮` 调字号（滚轮是 pointer
 * 事件，匹配模型与按键不同）。
 */
enum class DesktopShortcutAction(
    val id: String,
    val scope: Scope,
    val label: String,
    val hint: String,
    val defaultCombo: DesktopKeyCombo,
    /** 切页动作的 tab 序号（= MainScreen 的 TAB_*，与 mainTabs 顺序一致）；非切页动作 = -1 */
    val tabIndex: Int = -1
) {
    // ── 全局：焦点在哪都生效 ──
    TOGGLE_RAIL("toggle_rail", Scope.GLOBAL, "收起 / 拉出第二栏", "聊天页左侧的会话列表栏", DesktopKeyCombo(ctrl = true, key = Key.B)),
    NEW_SESSION("new_session", Scope.GLOBAL, "新建会话", "切到聊天页并弹出「选角色」", DesktopKeyCombo(ctrl = true, key = Key.N)),
    OPEN_SETTINGS("open_settings", Scope.GLOBAL, "打开设置", "跳到用户页的「外观」节", DesktopKeyCombo(ctrl = true, key = Key.Comma)),
    FOCUS_SEARCH("focus_search", Scope.GLOBAL, "全局搜索", "聊天页第二栏的搜索框", DesktopKeyCombo(ctrl = true, key = Key.K)),
    // 切页五条（第一栏页签，顺序与 mainTabs 一致）。带 Ctrl 的数字不会往输入框打字，
    // 所以输入框聚焦时照常生效、不需要让位
    TAB_DISCOVER("tab_discover", Scope.GLOBAL, "切到发现页", "第一栏第 1 页（分组与皮肤）", DesktopKeyCombo(ctrl = true, key = Key.One), 0),
    TAB_CHAT("tab_chat", Scope.GLOBAL, "切到聊天页", "第一栏第 2 页", DesktopKeyCombo(ctrl = true, key = Key.Two), 1),
    TAB_CHARACTER("tab_character", Scope.GLOBAL, "切到角色页", "第一栏第 3 页（全部角色）", DesktopKeyCombo(ctrl = true, key = Key.Three), 2),
    TAB_AI_CREATE("tab_ai_create", Scope.GLOBAL, "切到灵感创作页", "第一栏第 4 页", DesktopKeyCombo(ctrl = true, key = Key.Four), 3),
    TAB_PROFILE("tab_profile", Scope.GLOBAL, "切到用户页", "第一栏第 5 页（设置入口）", DesktopKeyCombo(ctrl = true, key = Key.Five), 4),

    // ── 输入框：输入框聚焦时优先（改了键，全局层的同键让位）──
    SEND_MESSAGE("send_message", Scope.INPUT, "发送消息", "输入框里发送；发送键以外的 Enter / Shift+Enter 都是换行", DesktopKeyCombo(key = Key.Enter)),
    INSERT_BRACKETS("insert_brackets", Scope.INPUT, "插入「」", "在光标处插一对书名括号", DesktopKeyCombo(ctrl = true, key = Key.B)),
    PASTE_IMAGE("paste_image", Scope.INPUT, "粘贴图片", "把剪贴板里的纯图片（截图等）贴进输入框；剪贴板有文字时正常粘贴文字", DesktopKeyCombo(ctrl = true, key = Key.V));

    enum class Scope { GLOBAL, INPUT }

    companion object {
        fun byId(id: String): DesktopShortcutAction? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 桌面全局快捷键的桥。
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
     * 改键捕获口（非 null = 捕获态）：快捷键设置页进入"按下新键位"时挂上。
     * 窗口层把它排在**一切**全局键与 Esc 返回栈之前——捕获是模态，捕获中的按键（含修饰键、
     * Esc）全由它处理并吞掉，否则捕"新建会话"的键时按到 Ctrl+K 就先把搜索框唤出来了。
     * 返回 true = 已消费（捕获口恒消费）。
     */
    var captureHandler: ((KeyEvent) -> Boolean)? = null

    /**
     * Ctrl 是否按着——给"Ctrl+滚轮调字号"用。滚轮走 pointer 事件，**拿不到键盘修饰符**
     * （PointerEvent 的修饰符 API 不是双端都有），只能由窗口层把每个按键事件里 Ctrl 的
     * 按下状态记在这里。
     */
    var ctrlPressed: Boolean = false

    /**
     * 聊天输入框此刻是否聚焦——**让位规则**：输入框聚焦时，裸 `Ctrl+B` 永远属于"插「」"
     * （输入框本地行为，`ChatPanels.desktopKeys`），全局层一律放行不消费；其余全局键
     * （Ctrl+N/K/,，或用户改过的任何键位）不受影响。只挂在聊天输入框上（它有与全局键
     * 相撞的本地行为）；搜索框/改名框等不设这个标志——它们没有这种本地行为。
     */
    var textFieldFocused: Boolean = false

    /** 用户键位表:无保留键位——Ctrl+V 本身是 [DesktopShortcutAction.PASTE_IMAGE] 的默认键,进表可改 */

    /** 动作的生效键位：用户改过用改的，否则默认（老数据 Map 为空，逐键与可配置化之前一致） */
    fun comboFor(action: DesktopShortcutAction, userKeys: Map<String, String>): DesktopKeyCombo =
        userKeys[action.id]?.let { DesktopKeyCombo.parse(it) } ?: action.defaultCombo

    /**
     * 事件 → **全局组**动作（按当前生效键位表）；null = 不是任何全局快捷键，窗口层放行。
     * 输入组动作不在这查（发送/插括号归 `desktopKeys`，粘贴图片是窗口层自己的独立分支）。
     */
    fun match(event: KeyEvent, userKeys: Map<String, String>): DesktopShortcutAction? {
        if (event.type != KeyEventType.KeyDown) return null
        val combo = DesktopKeyCombo.fromEvent(event)
        return DesktopShortcutAction.entries.firstOrNull {
            it.scope == DesktopShortcutAction.Scope.GLOBAL && comboFor(it, userKeys) == combo
        }
    }

    /**
     * 事件 → **输入组**动作（发送 / 插「」/ 粘贴图片）。两个用处：
     * `desktopKeys` 按它分派输入行为；`MainScreen.onShortcut` 用它做**让位判断**——
     * 输入框聚焦时命中输入组键位就整体放行（输入框内优先，含粘贴图片）。
     */
    fun matchInput(event: KeyEvent, userKeys: Map<String, String>): DesktopShortcutAction? {
        if (event.type != KeyEventType.KeyDown) return null
        val combo = DesktopKeyCombo.fromEvent(event)
        return DesktopShortcutAction.entries.firstOrNull {
            it.scope == DesktopShortcutAction.Scope.INPUT && comboFor(it, userKeys) == combo
        }
    }

    /** 事件是否就是 [action] 的当前生效键位（窗口层单点查"粘贴图片"用） */
    fun matchesAction(
        event: KeyEvent,
        action: DesktopShortcutAction,
        userKeys: Map<String, String>
    ): Boolean = event.type == KeyEventType.KeyDown && comboFor(action, userKeys) == DesktopKeyCombo.fromEvent(event)

    /** 新键位与**同组**其它动作的冲突（null = 无冲突；跨组同键允许，语义=输入框内优先）；[ignore] 传正在改的那条 */
    fun conflictOf(
        combo: DesktopKeyCombo,
        userKeys: Map<String, String>,
        ignore: DesktopShortcutAction?
    ): DesktopShortcutAction? =
        DesktopShortcutAction.entries.firstOrNull {
            it != ignore && it.scope == ignore?.scope && comboFor(it, userKeys) == combo
        }

    /**
     * 捕获改键时这个组合能不能收下：主键必须在白名单里；字母/数字/标点**必须带 Ctrl 或 Alt**
     * （裸按它们是打字）；F 键与 Enter 不参与打字、允许裸按。
     */
    fun captureAcceptable(combo: DesktopKeyCombo): Boolean {
        if (!DesktopKeyCombo.KEY_NAMES.containsKey(combo.key)) return false
        return combo.ctrl || combo.alt || combo.key in DesktopKeyCombo.FUNCTION_KEYS || combo.key == Key.Enter
    }
}
