package com.mysticat.roleplay.ui.screens

/**
 * 输入区与面板：输入栏、叙事风格基础档面板（「更多」）、灵感回复弹窗、历史记录 sheet、
 * 生效风格 chip 与各轴胶囊。
 *
 * * 2026-09-17（D10）从 `ChatScreen.kt` 拆出：纯结构拆分，逻辑未动。
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.mysticat.roleplay.ui.DesktopAnchoredPanel
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.EngineSpec
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.NarrativeStyle
import com.mysticat.roleplay.data.NarrativeStyles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.StyleAxis
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.data.narrativeStyle
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.timeText

/** 生效中的叙事风格小 chip（只展示，点输入栏左侧工具按钮修改） */
@Composable
internal fun StyleChip(text: String) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * 面板网格的一格：方框内「图标 ＋ 文字（居中）」。互动面板与工具卡片共用一份。
 *
 * [status] 是卡片底下的状态行（工具卡片用；空＝不显示）；[disabled] **置灰但保留位置**，口径同发图按钮
 * （不支持的项不整只消失，由 [PanelGrid] 在网格下挂一行解释）。
 */
internal data class PanelCell(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val status: String = "",
    val disabled: Boolean = false,
    val hint: String? = null,
    val onClick: () -> Unit = {}
)

@Composable
internal fun PanelGridCell(cell: PanelCell, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .then(if (cell.disabled) Modifier else Modifier.clickable(onClick = cell.onClick))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (cell.disabled) 0.22f else 0.55f)
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), shape)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            cell.icon,
            null,
            Modifier.size(22.dp),
            tint = if (cell.disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            cell.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (cell.disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (cell.status.isNotBlank()) {
            Text(
                cell.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 网格容器：**列表驱动**——加一项只加数据、不改布局。预留的玩法槽位（朋友圈/微信聊天/生成手账…）
 * 以后接进来靠的就是这条，所以这里不许出现"写死第几格"。
 */
@Composable
internal fun PanelGrid(cells: List<PanelCell>, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { PanelGridCell(it, Modifier.weight(1f)) }
                // 末行补空位：不补的话最后一行剩下的格子会被拉宽，与上面几行对不齐
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        cells.mapNotNull { it.hint }.distinct().forEach { hint ->
            Text(
                "· $hint",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

/**
 * 互动面板：输入栏「圆形加号」展开的微信式面板，每项＝方框内图标＋文字。
 *
 * 手机＝挂在输入栏下方，**容器顶起输入框、约占 1/3 屏**（见 [ChatInputBar]）；
 * 桌面＝由 [DesktopAnchoredPanel] 托管在锚定浮层里，不顶起输入框、也不盖住聊天区。
 */
@Composable
internal fun ChatPlusPanel(cells: List<PanelCell>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(14.dp))
        PanelGrid(cells, columns = 4)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 工具页：点 Tune 不再是长清单，而是一墙卡片，每张卡进各自的设置页/弹窗。
 *
 * 手机＝整页（[ChatOverlayPage]，与「会话」「角色信息」同一容器，桌面＝同一个组件铺第三栏）；
 * 桌面＝锚定输入栏上方的浮层（桌面窗口大，调参要能看见聊天区）。
 */
@Composable
internal fun ToolCardsPage(cards: List<PanelCell>, onBack: () -> Unit) {
    if (isDesktopLayout) {
        DesktopAnchoredPanel(onDismiss = onBack) {
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text(
                    "工具",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
                PanelGrid(cards, columns = 2)
            }
        }
    } else {
        ChatOverlayPage(title = "工具", onBack = onBack) {
            Text(
                "点一张卡片进对应设置，改完立即生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp)
            )
            Spacer(Modifier.height(6.dp))
            PanelGrid(cards, columns = 2)
        }
    }
}

/**
 * 「本轮特别指示」对话框：原先内联在工具长清单里，卡片化后它自己一个家。
 * 应用＝写给下一条回复（[ChatViewModel.oneShotHint]），清除＝撤销。
 */
@Composable
internal fun ToolHintDialog(
    current: String,
    onApply: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本轮特别指示") },
        text = {
            Column {
                Text(
                    "只影响下一条回复。例：这一条先写环境，别推进剧情。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (draft.isEmpty()) {
                            Text(
                                "要临时调整什么？",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (current.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onClear) { Text("清除已生效的指示") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft.trim()) }, enabled = draft.isNotBlank()) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/**
 * 「对话思考强度」对话框：与设置页读写同一份 [AiSettings.chatThinking]，点一下即生效。
 * [suggest]＝这一形态建议打开「深度」（玩法型不接管温度，参数该配什么得说出来）。
 */
@Composable
internal fun ToolThinkingDialog(
    current: String,
    suggest: Boolean,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对话思考强度") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhaleChip(selected = current.isBlank(), onClick = { onChange("") }, label = { Text("默认") })
                    WhaleChip(selected = current == "off", onClick = { onChange("off") }, label = { Text("关闭") })
                    WhaleChip(selected = current == "high", onClick = { onChange("high") }, label = { Text("深度") })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "关闭可降低延迟；仅支持的供应商生效（DeepSeek/智谱/火山/通义/OpenAI/Gemini）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (suggest) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "这一形态建议打开「深度」：猜谜、推理、对弈都需要多步排除，关掉思考会退化成" +
                            "只报最显著的答案（例如挨个报人名）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)

/**
 * 灵感回复（0.1.2）：不会接话时给几条可以直接发的话。
 * 点某一条只**填入输入框**、不直接发送——用户可以先改两个字再发，避免手滑发出去。
 */
@Composable
internal fun InspirationDialog(
    loading: Boolean,
    error: String?,
    items: List<String>,
    onReload: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("灵感回复")
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "想不出怎么接话？挑一条填进输入框，改完再发。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    loading -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在想…", style = MaterialTheme.typography.bodySmall)
                    }

                    error != null -> Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    else -> items.forEach { s ->
                        Card(
                            onClick = { onPick(s) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                s,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onReload, enabled = !loading) {
                Text(if (items.isEmpty()) "生成" else "换一批")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("关闭") }
        }
    )
}

/**
 * 桌面输入习惯：**Enter 发送 / Shift+Enter 换行 / Ctrl+B 插括号**。
 *
 * 手机上不挂这段——Android 软键盘的回车键就是「换行」，用户在手机上的期待与桌面相反，
 * 改了会让"想换行却发出去了"。所以整段按 [isDesktopLayout] 短路。
 *
 * 按键一律 consume：不消费的话 Enter 还会往输入框里插一个换行（发送后输入框里留个空行）。
 * 发送中被（sending）或内容为空时只吞掉、不发送——与发送键的 disabled 口径一致。
 */
private fun Modifier.desktopKeys(
    sending: Boolean,
    canSend: Boolean,
    onNewline: () -> Unit,
    onBracket: () -> Unit,
    onSend: () -> Unit
): Modifier = if (!isDesktopLayout) this else this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    if (e.isCtrlPressed && e.key == Key.B) {
        onBracket()
    } else if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
        if (e.isShiftPressed) onNewline() else if (!sending && canSend) onSend()
    } else {
        return@onPreviewKeyEvent false
    }
    true
}

@Composable
internal fun ChatInputBar(
    value: String,
    sending: Boolean,
    pendingImage: String?,
    onClearImage: () -> Unit,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onToolClick: () -> Unit,
    canPickImage: Boolean,
    onPickImage: () -> Unit,
    /**
     * 47c（2026-09-26）：不支持视觉时的点击动作——按钮**置灰但保留位置**，不再整只消失，
     * 点一下给非模态解释（跳设置页由调用方决定）。置灰的判定与 [canPickImage] 同源。
     */
    onPickImageUnavailable: () -> Unit = {},
    /** 语音输入（Step 3）：状态机决定提示语，两个回调是"按住/松手" */
    voicePhase: ChatViewModel.VoicePhase,
    onVoiceStart: () -> Unit,
    onVoiceEnd: () -> Unit,
    /**
     * 麦克风是「按住说话」还是「点按说话」。
     * 点按时**同一对回调复用**（进去＝start、出来＝end），界面自己看 [voicePhase] 决定这次是哪个方向；
     * 并多一个「×」取消（按住模式下手指还压着麦克风、按不到取消键，所以只在点按模式渲染）。
     */
    tapToTalk: Boolean,
    onVoiceCancel: () -> Unit,
    /** 引擎形态给的占位文案（工具形态：「输入要处理的内容，或补充要求…」）；空则用默认那两句 */
    placeholder: String = "",
    /**
     * 圆形加号：面板是否展开 ＋ 切换回调 ＋ 面板项。
     * 面板项由调用方给（[PanelCell] 列表驱动：加一项只加数据、不改布局——预留的玩法槽位靠这条）。
     */
    plusOpen: Boolean = false,
    onPlusToggle: () -> Unit = {},
    plusCells: List<PanelCell> = emptyList()
) {
    // 手势处理器用 pointerInput(Unit) 保持稳定（**不能**以 voicePhase 为 key：
    // 状态一变就会重启手势协程，松手事件随之丢失，录音会一直卡到页面销毁）
    val startNow by androidx.compose.runtime.rememberUpdatedState(onVoiceStart)
    val endNow by androidx.compose.runtime.rememberUpdatedState(onVoiceEnd)
    // 输入框改由 TextFieldValue 承载——「填括号」要插在**光标处**、并把光标落回括号中间，
    // 这是 String 给不了的信息（它只有文本、没有选区）。外部改动（灵感填入／语音识别结果／发送后清空）
    // 由下面这行同步进来，光标落到末尾。
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val hasText = field.text.isNotEmpty()
    val keyboard = LocalSoftwareKeyboardController.current

    fun applyText(next: String, cursor: Int) {
        field = TextFieldValue(next, TextRange(cursor))
        onValueChange(next)
    }

    /** 光标处插入「（）」；有选区时把它包起来（选中「走过去」→「（走过去）」），两种都让光标留在括号内 */
    fun insertBrackets() {
        val t = field.text
        val s = field.selection.min.coerceIn(0, t.length)
        val e = field.selection.max.coerceIn(s, t.length)
        if (s == e) applyText(t.substring(0, s) + "（）" + t.substring(e), s + 1)
        else applyText(t.substring(0, s) + "（" + t.substring(s, e) + "）" + t.substring(e), e + 1)
    }

    BoxWithConstraints {
        // 互动面板高度＝可用高度的三分之一（手机＝微信式顶起输入框；桌面走锚定浮层，用不到它）
        val panelHeight = if (maxHeight.value.isFinite()) maxHeight * 0.34f else 300.dp
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp
        ) {
            Column {
                // 已附加的图片（可移除，随下一条消息一起发送）
                if (pendingImage != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = imageModel(pendingImage),
                            contentDescription = "已附加图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "图片已附加，输入文字一起发送",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearImage, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "移除图片", Modifier.size(16.dp))
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 0.1.2 布局调整：左侧只留「更多」工具入口，发图按钮移到输入框右侧（靠近发送键）
                    // 点它不再弹长清单，而是「工具」卡片页（卡片进各自设置）；
                    // 无障碍名跟着改成「工具」——与页标题一致，也不再与顶栏那个「更多」（⋮）撞名
                    IconButton(onClick = onToolClick, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "工具",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicTextField(
                            value = field,
                            onValueChange = { field = it; onValueChange(it.text) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp)
                                .desktopKeys(
                                    sending = sending,
                                    canSend = field.text.isNotBlank(),
                                    onNewline = { applyText(field.text + "\n", field.text.length + 1) },
                                    onBracket = { insertBrackets() },
                                    onSend = onSend
                                ),
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 4,
                            decorationBox = { inner ->
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    if (field.text.isEmpty()) {
                                        Text(
                                            when {
                                                placeholder.isNotBlank() -> placeholder
                                                isDesktopLayout ->
                                                    "输入消息，Enter 发送 / Shift+Enter 换行 / Ctrl+B 插括号"
                                                else -> "请输入你想说的话…"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // 语音输入（Step 3）：按住说话、松手识别；可切成"点按说话"（桌面固定点按）
                    val voiceBusy = voicePhase == ChatViewModel.VoicePhase.RECORDING ||
                        voicePhase == ChatViewModel.VoicePhase.LISTENING
                    // 按键按输入态分流——**有文本时右侧只留「填括号」＋「发送」**，
                    // 麦克风/发图/加号让位；**空输入时不渲染发送键**（微信同款，也让右下角不再三键挤一排）。
                    if (hasText) {
                        // 填括号：Material 图标库里没有括号，直接用「（）」文本——也免了为一只按钮引入图标依赖
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { insertBrackets() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "（）",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 发送键：只要飞机图标，不带圆形底
                        IconButton(
                            onClick = onSend,
                            enabled = !sending && value.isNotBlank(),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (sending || value.isBlank())
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        // 录音中的「×」取消：只给点按模式（按住模式下启用它的那根手指还在麦克风上）
                        if (tapToTalk && voiceBusy) {
                            IconButton(onClick = onVoiceCancel, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "取消录音",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voiceBusy) MaterialTheme.colorScheme.errorContainer
                                    else Color.Transparent
                                )
                                .then(
                                    if (tapToTalk) Modifier.clickable {
                                        // 点一下开录、再点一下结束（同一次点击里的方向由当前状态决定）
                                        if (voiceBusy) endNow() else startNow()
                                    } else Modifier.pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                startNow()
                                                tryAwaitRelease()
                                                endNow()
                                            }
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (voicePhase) {
                                ChatViewModel.VoicePhase.TRANSCRIBING -> CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )

                                else -> Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = when {
                                        voiceBusy && tapToTalk -> "正在录音，点一下结束"
                                        voiceBusy -> "正在录音，松手结束"
                                        tapToTalk -> "点按说话"
                                        else -> "按住说话"
                                    },
                                    tint = if (voiceBusy) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        // 发图：放在输入框右侧、加号左边
                        // 47c：不支持视觉时置灰保留位置，点击给非模态解释——不再"整只按钮消失"让用户找不到
                        IconButton(
                            onClick = { if (canPickImage) onPickImage() else onPickImageUnavailable() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = if (canPickImage) "发送图片" else "当前模型不支持图片输入",
                                tint = if (canPickImage) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        // 圆形加号：微信式互动面板入口，收在输入框最右
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (plusOpen) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .clickable {
                                    // 点开面板先把键盘收掉：否则面板被键盘顶在半空（微信同款手感）
                                    keyboard?.hide()
                                    onPlusToggle()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "互动面板",
                                tint = if (plusOpen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                // 手机：面板挂在输入栏下方＝容器顶起输入框（桌面走下面的锚定浮层，不占聊天区）
                if (!isDesktopLayout && plusOpen) {
                    ChatPlusPanel(plusCells, Modifier.height(panelHeight))
                }
            }
        }
        // 桌面：锚定输入栏上方、右对齐的浮层——调参要能边看聊天区边改
        if (isDesktopLayout && plusOpen) {
            DesktopAnchoredPanel(onDismiss = onPlusToggle) { ChatPlusPanel(plusCells) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(
    conversations: List<Conversation>,
    currentId: String?,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 同 ToolPanel：m3 1.4.0 面板拖动抖动（issue 384959324）的两条规避
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0.dp) }
    ) {
        // 会话列表是 Column + forEach（不是 LazyColumn），会话一多就超出面板高度，
        // 下面的条目既看不到也点不到；加滚动容器兜底
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("会话历史", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新会话")
                }
            }
            val sorted = conversations.sortedByDescending { it.updatedAt }
            if (sorted.isEmpty()) {
                Text(
                    "还没有历史会话",
                    modifier = Modifier.padding(vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sorted.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (c.id == currentId) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onOpen(c.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(
                                c.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                timeText(c.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (c.id != currentId) {
                            IconButton(onClick = { onDelete(c.id) }) {
                                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
