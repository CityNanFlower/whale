package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.DesktopKeyCombo
import com.mysticat.roleplay.ui.DesktopShortcutAction
import com.mysticat.roleplay.ui.DesktopShortcuts
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.SectionCard
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.theme.LocalWhalePalette

/**
 * 「快捷键」整页（桌面专属）：全局快捷键的查看与改键。
 *
 * - 逐条列出 [DesktopShortcutAction]（动作 / 当前生效键位；改过的动作带"恢复该条默认"）；
 * - 点一条进入**捕获态**：下一个按键即新键位，Esc 取消。捕获挂在窗口层的
 *   [DesktopShortcuts.captureHandler]（**先于**一切全局键与返回栈的模态口）——页内挂
 *   `onPreviewKeyEvent` 收不到无焦点时的按键，而捕获必须"按了就收"，不能要求先点输入框；
 * - 冲突 / 不合法键位当场拒绝并在顶部提示；改动**当场落盘** `settings.json`
 *   （`AiSettings.shortcuts`），重启仍在；改键后全局层每次按键现读，**当场生效**。
 *
 * 让位口径（与全局层同一份，见 `MainScreen.onShortcut`）：输入框聚焦时裸 Ctrl+B 永远是
 * "插「」"，与这里配了什么无关。
 */
@Composable
internal fun ShortcutsScreen(
    onBack: () -> Unit,
    /** 桌面三栏：内嵌进第三栏（入口＝用户页「桌面」节的「快捷键」行），不套 Scaffold/顶栏 */
    embedded: Boolean = false
) {
    // 用户已改的键位（动作 id → 键位串）。从 settings.json 读一份起步，之后每次改动当场落盘。
    // mutableStateMapOf 让行内的键位徽章跟着改键即时重组
    val userKeys = remember {
        mutableStateMapOf<String, String>().apply { putAll(Repository.loadSettings().shortcuts) }
    }
    /** 正在捕获改键的动作（null = 没在捕获） */
    var captureFor by remember { mutableStateOf<DesktopShortcutAction?>(null) }
    /** 顶部提示条（拒绝原因 / 恢复默认的回执）；下一次按键或操作会覆盖它 */
    var notice by remember { mutableStateOf<String?>(null) }

    fun persist() {
        Repository.saveSettings(Repository.loadSettings().copy(shortcuts = userKeys.toMap()))
    }

    // 捕获态是**模态**：挂进窗口层（全局桥），先于 Esc 返回栈与全部全局快捷键，
    // 捕获中的任何按键都吞掉——否则捕获"新建会话"的新键时按到 Ctrl+K 就先把搜索框唤出来了
    val capturing = captureFor
    if (capturing != null) {
        DisposableEffect(capturing) {
            DesktopShortcuts.captureHandler = { ev ->
                if (ev.type != KeyEventType.KeyDown) {
                    true
                } else if (ev.key in DesktopKeyCombo.MODIFIER_KEYS) {
                    true // 修饰键按着不算，等主键
                } else if (ev.key == Key.Escape) {
                    captureFor = null
                    true
                } else {
                    val combo = DesktopKeyCombo.fromEvent(ev)
                    when {
                        !DesktopShortcuts.captureAcceptable(combo) ->
                            notice = "「${combo.encode()}」不能用作快捷键：字母/数字/标点要带 Ctrl 或 Alt，Enter 与 F1–F12 可单独用"
                        else -> {
                            val clash = DesktopShortcuts.conflictOf(combo, userKeys, capturing)
                            if (clash != null) {
                                notice = "「${combo.encode()}」已用于「${clash.label}」，请换一个键（Esc 取消）"
                            } else {
                                userKeys[capturing.id] = combo.encode()
                                persist()
                                notice = "「${capturing.label}」已改为 ${combo.encode()}"
                                captureFor = null
                            }
                        }
                    }
                    true
                }
            }
            onDispose { DesktopShortcuts.captureHandler = null }
        }
    }

    // Esc：非捕获态 = 退回总设置表；捕获态被上面的模态口先收走（这里收不到，双保险）
    WhaleBackHandler(enabled = capturing == null) { onBack() }

    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "点一条改键：按下新的键位组合，Esc 取消。同一组内冲突的组合会被拒绝；" +
                        "输入框聚焦时输入框键位优先（全局键让位）。Esc 与 Ctrl+滚轮调字号不参与改键。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                notice?.let {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (captureFor != null) LocalWhalePalette.current.dangerText
                        else LocalWhalePalette.current.accentText
                    )
                }
            }
            SectionCard(Modifier.padding(horizontal = 16.dp), title = "全局键位（焦点在哪都生效）") {
                DesktopShortcutAction.entries.filter { it.scope == DesktopShortcutAction.Scope.GLOBAL }
                    .forEach { action ->
                        ShortcutRow(
                            action = action,
                            combo = DesktopShortcuts.comboFor(action, userKeys),
                            custom = userKeys.containsKey(action.id),
                            capturing = captureFor == action,
                            onStartCapture = {
                                notice = null
                                captureFor = action
                            },
                            onReset = {
                                userKeys.remove(action.id)
                                persist()
                                notice = "「${action.label}」已恢复默认键位"
                            }
                        )
                    }
            }
            SectionCard(Modifier.padding(horizontal = 16.dp), title = "输入框键位（输入框内优先）") {
                DesktopShortcutAction.entries.filter { it.scope == DesktopShortcutAction.Scope.INPUT }
                    .forEach { action ->
                        ShortcutRow(
                            action = action,
                            combo = DesktopShortcuts.comboFor(action, userKeys),
                            custom = userKeys.containsKey(action.id),
                            capturing = captureFor == action,
                            onStartCapture = {
                                notice = null
                                captureFor = action
                            },
                            onReset = {
                                userKeys.remove(action.id)
                                persist()
                                notice = "「${action.label}」已恢复默认键位"
                            }
                        )
                    }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(
                    enabled = userKeys.isNotEmpty(),
                    onClick = {
                        userKeys.clear()
                        persist()
                        notice = "已全部恢复默认键位"
                    }
                ) { Text("全部恢复默认") }
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            EmbeddedPageHeader("快捷键", onBack)
            body()
        }
    } else {
        // 调用面锁死在桌面整页分支（embedded 恒 true）；这里只是照页面惯例留一条手机形态的退路
        Scaffold(topBar = {
            WhalePageTopBar(
                title = "快捷键",
                navigationIcon = { BackArrowButton(onBack) }
            )
        }) { padding -> Column(Modifier.padding(padding)) { body() } }
    }
}

/** 一条快捷键：动作名 ＋ 说明（左）｜键位徽章（右）｜捕获态换成"按下新键位"＋ 重置 */
@Composable
private fun ShortcutRow(
    action: DesktopShortcutAction,
    combo: DesktopKeyCombo,
    custom: Boolean,
    capturing: Boolean,
    onStartCapture: () -> Unit,
    onReset: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onStartCapture)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(action.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                action.hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        if (capturing) {
            Text("按下新键位…", style = MaterialTheme.typography.bodyMedium, color = LocalWhalePalette.current.accentText)
            Text(
                "Esc 取消",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        } else {
            KeyBadge(combo.encode())
            // 恢复该条默认：改过才可用（重置 = 从用户表里移除，回落到注册表默认键位）
            IconButton(onClick = onReset, enabled = custom, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.RestartAlt,
                    contentDescription = "恢复默认键位",
                    modifier = Modifier.size(18.dp),
                    tint = if (custom) colors.onSurfaceVariant else colors.outlineVariant
                )
            }
        }
    }
}

/** 键位徽章：一枚"键帽"样式的当前键位（Ctrl+B / Alt+N…） */
@Composable
private fun KeyBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
