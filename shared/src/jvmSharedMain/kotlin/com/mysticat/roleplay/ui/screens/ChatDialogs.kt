package com.mysticat.roleplay.ui.screens

/**
 * 聊天页的两个设置类对话框：重命名会话、「本会话设定」（世界观/用户设定/特别指示等）。
 *
 * * 2026-09-17（D10）从 `ChatScreen.kt` 拆出：纯结构拆分，逻辑未动。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.TextInputDialog
import com.mysticat.roleplay.ui.WhaleChip

@Composable
internal fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TextInputDialog(
        title = "重命名会话",
        initial = initial,
        label = "会话名称",
        singleLine = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 输出预算（0.2.4 批次 B）：会话页「生成 · 输出预算」的对话框。
 * 与设置页「对话预算」是**同一份全局设置**（`AiSettings.budgetMode` 等三个字段），这里**拨动即存**——
 * 不挂"按保存才生效"的表单：对话中途调预算，调完下一条消息就该用上（口径同用户页的开发者模式开关）。
 * 每次改动都以**当时的磁盘存档**为底做 copy，再调 [BudgetMirror.refresh] 让会话页那行的状态字跟着变。
 */
@Composable
internal fun BudgetDialog(onDismiss: () -> Unit) {
    val initial = Repository.loadSettings()
    var mode by remember { mutableStateOf(initial.budgetMode) }
    var customMax by remember { mutableStateOf(initial.customMaxTokens) }
    var customCtx by remember { mutableStateOf(initial.customContextChars) }
    var thinking by remember { mutableStateOf(initial.chatThinking) }

    fun save(m: String = mode, mx: Int = customMax, ctx: Int = customCtx, th: String = thinking) {
        mode = m; customMax = mx; customCtx = ctx; thinking = th
        Repository.saveSettings(
            Repository.loadSettings().copy(
                budgetMode = m,
                customMaxTokens = mx,
                customContextChars = ctx,
                chatThinking = th
            )
        )
        BudgetMirror.refresh()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输出预算") },
        text = {
            Column(
                // heightIn 必须在 verticalScroll 外面（链序反了＝内容被钉死裁掉且滚动范围为 0，
                // 2026-09-29 用户反馈"自定义没法拖动"）；写法对齐 RequestViewerDialog
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "宽松＝默认行为；节省＝关思考＋最低总输出档＋低上下文（按当前模型生成）；自定义＝总输出 / 思考强度 / 上下文三项旋钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhaleChip(
                        selected = mode == com.mysticat.roleplay.data.BUDGET_MODE_LOOSE,
                        onClick = { save(m = com.mysticat.roleplay.data.BUDGET_MODE_LOOSE) },
                        label = { Text("宽松") }
                    )
                    WhaleChip(
                        selected = mode == com.mysticat.roleplay.data.BUDGET_MODE_SAVING,
                        onClick = {
                            // 节省档＝预设值一次填好（台账 113）：拨动即存，跟另外两颗胶囊同一套语义。
                            // 档位按**当前模型**取，所以关不掉思考的模型会拿到它自己的 8192 下限，不会空回复。
                            val p = ProviderProfiles.savingPreset(
                                initial.chatBaseUrl, initial.chatModel, initial.customProviders
                            )
                            save(
                                m = com.mysticat.roleplay.data.BUDGET_MODE_SAVING,
                                mx = p.maxTokens, ctx = p.contextChars, th = p.thinking
                            )
                        },
                        label = { Text("节省") }
                    )
                    WhaleChip(
                        selected = mode == com.mysticat.roleplay.data.BUDGET_MODE_CUSTOM,
                        onClick = { save(m = com.mysticat.roleplay.data.BUDGET_MODE_CUSTOM) },
                        label = { Text("自定义") }
                    )
                }
                if (mode != com.mysticat.roleplay.data.BUDGET_MODE_LOOSE) {
                    BudgetKnobChips(
                        label = "单条回复总输出上限",
                        options = ProviderProfiles
                            .budgetLevels(initial.chatBaseUrl, initial.chatModel, initial.customProviders)
                            .map { it.toString() },
                        selected = customMax.toString(),
                        onSelect = { save(mx = it.toIntOrNull() ?: customMax) },
                        hint = "总是思考的模型不给 8192 以下档位：思考与正文共用这份额度，低档位会被推理吃光、正文为空。"
                    )
                    ThinkingChips(
                        label = "思考强度",
                        value = thinking,
                        levels = ProviderProfiles.thinkingLevels(initial.chatBaseUrl, initial.chatModel),
                        onValue = { save(th = it) }
                    )
                    BudgetKnobChips(
                        label = "上下文字符预算",
                        options = listOf("8000", "12000", "16000", "24000", "32000", "48000"),
                        selected = customCtx.toString(),
                        onSelect = { save(ctx = it.toIntOrNull() ?: customCtx) },
                        hint = "历史总量超过该值就从最早的消息开始丢弃（宽松档固定 24000）。"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/**
 * 本会话设定：**先把「当前生效的是什么」摆出来**，再给候选列表（选用预设 / 无设定 / 用默认 / 新建）。
 *
 * 用户反馈（2026-09-16）"看不出现在是什么设定"：原来这个弹窗只有一串可点的条目，
 * 当前生效的那条与其它条目长得一模一样，只能靠记住上次点了哪个。现在两处区分：
 * 顶部一张「当前生效」卡片（回查到的名字 + 设定内容），列表里命中当前内容的条目打勾并标「使用中」。
 */
@Composable
internal fun ChatSettingDialog(
    current: String,
    onDismiss: () -> Unit,
    onUse: (String) -> Unit,
    onCreate: (String, String) -> Unit
) {
    // **不能**在组合期裸读盘 —— 下面两个输入框每敲一个字都会重组，等于每键一次 JSON 解析。
    // 用 remember(rev) 缓存；rev 只在"新建了一条设定"时 +1，保证新建后列表立刻刷新（与原来行为一致）。
    var rev by remember { mutableStateOf(0) }
    val presets = remember(rev) { Repository.listUserSettings() }
    val def = remember(rev) { Repository.defaultUserSetting() }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    // 当前生效的设定：落盘的只有**内容**，名字要按内容回查（默认设定 / 我的预设 / 自己写的）
    val currentName = when {
        current.isBlank() -> "无设定"
        def != null && def.content == current -> "默认设定：${def.name}"
        else -> presets.firstOrNull { it.content == current }?.name ?: "自定义设定"
    }
    val defActive = def != null && def.content == current

    // 候选条目：命中当前内容的一条打勾加色，一眼能看出现在是哪条（内容相同则一起标，内容本来就是一回事）
    val optionRow: @Composable (String, String?, Boolean, () -> Unit) -> Unit =
        { label, sub, active, onClick ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (active) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "使用中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "使用中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本会话设定") },
        text = {
            // AlertDialog 的 text 槽不会自动滚动，设定条目一多下面的就既看不到也点不到
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "当前生效",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            currentName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (current.isBlank()) "角色只按角色卡与基础提示词回话，不带任何额外设定。"
                            else current,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "切换为",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 问题 #33：会话设定支持「无设定」——清空本会话的用户设定（不带走任何预设）
                optionRow("无设定（本会话不使用设定）", null, current.isBlank()) { onUse("") }
                if (def != null) {
                    optionRow("使用默认设定：${def.name}", def.content, defActive) { onUse(def.content) }
                }
                if (presets.isNotEmpty()) {
                    HorizontalDivider()
                    presets.forEach { s ->
                        // 与当前生效那条**同名同内容**的，才回显名字而不是"自定义设定"
                        optionRow(s.name, s.content, s.content == current) { onUse(s.content) }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { showCreate = !showCreate }) {
                    Text(if (showCreate) "收起新建设定" else "+ 新建设定")
                }
                if (showCreate) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("设定名称") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = content, onValueChange = { content = it },
                        label = { Text("设定内容（传达给角色）") },
                        minLines = 3
                    )
                    TextButton(
                        onClick = { onCreate(name, content); rev++ },
                        enabled = content.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("创建并应用") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
