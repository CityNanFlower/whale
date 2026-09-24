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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
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

/** 轴名胶囊（只作标签、不可点）：基础面板里「叙事节奏 / 篇幅密度」各一个胶囊，后面跟该轴的选项 */
@Composable
internal fun AxisPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.padding(end = 2.dp)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/** 基础面板的一行：轴名胶囊 + 该轴选项（窄屏/大字体下 FlowRow 自动换行） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BasicAxisRow(axis: StyleAxis, style: NarrativeStyle, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AxisPill(axis.label)
        axis.options.forEach { opt ->
            WhaleChip(
                selected = NarrativeStyles.isPicked(style, axis, opt.value),
                onClick = { onPick(opt.value) },
                label = { Text(opt.label) }
            )
        }
    }
}

/** 「高级」胶囊：打开「叙事设置」全屏页（其余叙事轴都在那里） */
@Composable
internal fun AdvancedPill(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "高级",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** 「更多」面板：叙事风格基础档（节奏 + 密度，其余轴进「高级」页）+ 对话思考强度（与设置页读写同一份数据）+ 本会话设定入口 */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolPanel(
    vm: ChatViewModel,
    /** 引擎形态（E4）：工具形态下风格轴 / 角色记忆 / 本会话设定都不出现 */
    engine: EngineSpec = Engines.of(""),
    onDismiss: () -> Unit
) {
    val conv = vm.conversation
    // 思考强度与设置页互通：直接读写 AiSettings.chatThinking
    var thinking by remember { mutableStateOf(Repository.loadSettings().chatThinking) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 手指往下拉会抖（2026-09-21 用户反馈「更多面板向下拉出现抖动」）：material3 1.4.0 的已知回归
        // ——sheet 默认自己吃系统栏 inset，拖动时 inset 随位置重算，与里面这份滚动容器互相顶
        // （官方 issue 384959324 / 285847707）。两条规避一起上：
        // ① 关掉 sheet 自带的 window inset（内容本来就自己挂了 navigationBarsPadding()，视觉不变）；
        // ② 跳过「半展开」档，滚动到顶时的下拉不再被锚点抢走。
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0.dp) }
    ) {
        // P2-A9：底部面板在小屏/字体放大后会超出屏幕，且没有滚动容器 → 下面按钮点不到
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("更多", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            // 叙事风格整块（7 轴 + 高级入口）只在陪伴/多线出现：它是纯虚构语法，工具里没有对应物
            // （台账 §5.1）。工具下这一块整段不渲染，连标题都不留 —— 留个空标题比不显示更让人困惑。
            if (engine.narrativeAxes) {
                Text(
                    "叙事风格（仅本会话，改完立即生效）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                // 基础档：只给节奏 + 密度两根最常用的轴（各一个胶囊 + 选项），其余轴收进「高级」
                val style = conv?.narrativeStyle() ?: NarrativeStyle()
                NarrativeStyles.AXES.filter { it.quick }.forEach { axis ->
                    BasicAxisRow(axis = axis, style = style) { value ->
                        vm.applyNarrative(NarrativeStyles.pick(style, axis, value))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                // 高级轴被动过时要明说，否则用户在基础面板里看不到"还有别的设置正在生效"
                val advancedCount = NarrativeStyles.AXES.count { !it.quick && style.valueOf(it.key).isNotEmpty() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AdvancedPill { onDismiss(); vm.showStyleScreen = true }
                    if (advancedCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已启用 $advancedCount 项高级设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(
                "本轮特别指示（只影响下一条回复）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            var hintDraft by remember { mutableStateOf(vm.oneShotHint) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (hintDraft.isEmpty()) {
                            Text(
                                "例：这一条先写环境，别推进剧情",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = hintDraft,
                            onValueChange = { hintDraft = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                TextButton(
                    onClick = { vm.oneShotHint = hintDraft.trim() },
                    enabled = hintDraft.isNotBlank()
                ) { Text("应用") }
            }
            if (vm.oneShotHint.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "已生效：${vm.oneShotHint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { vm.oneShotHint = ""; hintDraft = "" }) { Text("清除") }
                }
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                "对话思考强度（与设置页互通）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhaleChip(
                    selected = thinking.isBlank(),
                    onClick = {
                        thinking = ""
                        Repository.saveSettings(Repository.loadSettings().copy(chatThinking = ""))
                    },
                    label = { Text("默认") }
                )
                WhaleChip(
                    selected = thinking == "off",
                    onClick = {
                        thinking = "off"
                        Repository.saveSettings(Repository.loadSettings().copy(chatThinking = "off"))
                    },
                    label = { Text("关闭") }
                )
                WhaleChip(
                    selected = thinking == "high",
                    onClick = {
                        thinking = "high"
                        Repository.saveSettings(Repository.loadSettings().copy(chatThinking = "high"))
                    },
                    label = { Text("深度") }
                )
            }
            Text(
                "关闭可降低延迟；仅支持的供应商生效（DeepSeek/智谱/火山/通义/OpenAI/Gemini）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 玩法形态的显式建议（E4 拆档）：玩法型**不接管温度**（要探索，0.3 会把候选压到最显著的
            // 几个），代价是"参数该配什么"落到了用户手上 —— 所以必须在这里说出来，否则只是把
            // 二十问"逐个报人名"的坑从默认值挪到了用户看不见的地方。
            if (engine.suggestThinking) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "这一形态建议打开「深度」：猜谜、推理、对弈都需要多步排除，关掉思考会退化成" +
                        "只报最显著的答案（例如挨个报人名）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            // 角色记忆 / 前情提要 / 本会话设定都是"关系与剧情连续性"设备，工具形态一律不出现
            // （台账 §5.1）。工具下这一组整段不渲染，面板里只留本轮特别指示与思考强度。
            if (engine.relationshipMemory) {
                TextButton(onClick = { onDismiss(); vm.showMemoryDialog = true }) {
                    Icon(Icons.Filled.AutoStories, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    // 叫法跟形态走：陪伴 / 多线是「角色记忆」（关系向），玩法是「本局进度」（进度向）
                    Text(engine.memoryEntryLabel)
                }
                Text(
                    "手动维护，聊天中 AI 也会自动整理更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onDismiss(); vm.showSettingDialog = true }) {
                    Icon(Icons.Filled.Person, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("本会话设定")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 桌面输入习惯：**Enter 发送 / Shift+Enter 换行**。
 *
 * 手机上不挂这段——Android 软键盘的回车键就是「换行」，用户在手机上的期待与桌面相反，
 * 改了会让"想换行却发出去了"。所以整段按 [isDesktopLayout] 短路。
 *
 * Enter 一律 consume：不消费的话它还会往输入框里插一个换行（发送后输入框里留个空行）。
 * 发送中被（sending）或内容为空时只吞掉、不发送——与发送键的 disabled 口径一致。
 */
private fun Modifier.desktopEnterToSend(
    value: String,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
): Modifier = if (!isDesktopLayout) this else this.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    if (e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
    if (e.isShiftPressed) {
        onValueChange(value + "\n")
    } else if (!sending && value.isNotBlank()) {
        onSend()
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
    /** 语音输入（Step 3）：状态机决定提示语，两个回调是"按住/松手" */
    voicePhase: ChatViewModel.VoicePhase,
    onVoiceStart: () -> Unit,
    onVoiceEnd: () -> Unit,
    /**
     * 第 32 轮：麦克风是「按住说话」还是「点按说话」。
     * 点按时**同一对回调复用**（进去＝start、出来＝end），界面自己看 [voicePhase] 决定这次是哪个方向；
     * 并多一个「×」取消（按住模式下手指还压着麦克风、按不到取消键，所以只在点按模式渲染）。
     */
    tapToTalk: Boolean,
    onVoiceCancel: () -> Unit,
    /** 引擎形态给的占位文案（工具形态：「输入要处理的内容，或补充要求…」）；空则用默认那两句 */
    placeholder: String = ""
) {
    // 手势处理器用 pointerInput(Unit) 保持稳定（**不能**以 voicePhase 为 key：
    // 状态一变就会重启手势协程，松手事件随之丢失，录音会一直卡到页面销毁）
    val startNow by androidx.compose.runtime.rememberUpdatedState(onVoiceStart)
    val endNow by androidx.compose.runtime.rememberUpdatedState(onVoiceEnd)
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
                IconButton(onClick = onToolClick, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = "更多",
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
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp)
                            .desktopEnterToSend(value, sending, onValueChange, onSend),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        decorationBox = { inner ->
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                if (value.isEmpty()) {
                                    Text(
                                        when {
                                            placeholder.isNotBlank() -> placeholder
                                            isDesktopLayout -> "输入消息，Enter 发送 / Shift+Enter 换行"
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
                // 语音输入（Step 3）：按住说话、松手识别；第 32 轮起可切成"点按说话"（桌面固定点按）
                // —— 放在输入框右侧（靠近发送键），左侧仍然只留「更多」工具入口
                val voiceBusy = voicePhase == ChatViewModel.VoicePhase.RECORDING ||
                    voicePhase == ChatViewModel.VoicePhase.LISTENING
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
                        .clip(androidx.compose.foundation.shape.CircleShape)
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
                // 发图：放在输入框右侧、发送键左边（左侧只留工具入口）
                if (canPickImage) {
                    IconButton(onClick = onPickImage, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = "发送图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
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
                        tint = if (sending || value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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
        // P2-A9：会话列表是 Column + forEach（不是 LazyColumn），会话一多就超出面板高度，
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
