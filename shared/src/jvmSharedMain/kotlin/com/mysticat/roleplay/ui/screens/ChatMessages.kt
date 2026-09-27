package com.mysticat.roleplay.ui.screens

/**
 * 消息列表与气泡：列表渲染、长按图标菜单、气泡本体、多版本（swipe）、编辑/回溯对话框、
 * Markdown 渲染与括号/旁白着色。
 *
 * * 2026-09-17（D10）从 `ChatScreen.kt` 拆出：纯结构拆分，逻辑未动。
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.ChatMessage
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.EngineSpec
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.TextInputDialog
import com.mysticat.roleplay.ui.ChatAreaScrims
import com.mysticat.roleplay.ui.chatAvatarSize
import com.mysticat.roleplay.ui.chatBubbleMaxWidth
import com.mysticat.roleplay.ui.desktopSecondaryClick
import com.mysticat.roleplay.ui.dimmedScrims
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.showToast

@Composable
internal fun ChatMessages(
    modifier: Modifier,
    char: CharacterCard,
    messages: List<ChatMessage>,
    sending: Boolean,
    draft: String,
    backgroundUri: String?,
    /** 背景暗化强度（会话级b）：消息区 scrim 的透明度系数 */
    bgDim: Float = 1f,
    showAll: Boolean,
    insets: PaddingValues = PaddingValues(),
    /** 消息动作束：这里只负责往下传，不拆开 */
    actions: MessageActions,
    /** 灵感回复：紧跟在最新一条气泡右下方的入口（0.1.2） */
    onInspiration: () -> Unit,
    inspirationLoading: Boolean,
    /** 正在朗读（P-F2 v1）：气泡长按菜单据此多出一项「停止朗读」 */
    ttsSpeaking: Boolean,
    /**
     * 本会话的引擎形态：气泡末尾的常驻复制按钮、版本切换条、「换一批」的叫法都由它决定。
     * 默认陪伴形态 ⇒ 老行为一字不变。
     */
    engine: EngineSpec = Engines.of(""),
    /** 工具形态的「使用示例」：点击即当任务填入输入框 */
    examples: List<String> = emptyList(),
    onUseExample: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()

    // 问题 #2：默认「精简模式」只渲染最新一条，让聊天背景露出来
    // 精简模式只留最新一条；「世界观 / 当前场景」只在完整对话里显示（它就是第一条）
    // 例外：对话还没开始（没有用户消息）时全显示——新会话要能同时看到场景卡与开场白（问题 #16）
    val visible: List<Pair<Int, ChatMessage>> = remember(messages, showAll) {
        if (showAll || messages.size <= 1 || messages.none { it.role == "user" }) {
            messages.mapIndexed { i, m -> i to m }
        } else {
            listOf(messages.lastIndex to messages.last())
        }
    }
    val visibleCount = visible.size + if (sending) 1 else 0

    // 问题 #1：键盘弹出/收起会改变列表视口高度，而 LazyColumn 不会因此重新定位，
    // 结果是刚发出的消息被顶出屏幕。这里跟踪视口高度，一变就重新贴底。
    var viewportHeight by remember { mutableStateOf(0) }

    // 新消息 / 展开-收起 / 键盘开合 → 贴底。
    // 大 scrollOffset：单条长消息（高于视口）也能贴到列表底部，否则 scrollToItem
    // 会把长消息的顶端对齐视口顶，最新的结尾内容被输入栏挡住（唤起键盘后"显示不全"）
    // 问题 #32：视口高度变化（编辑弹窗唤起键盘等）只在用户本就在底部附近时才跟随贴底，
    // 编辑/查看历史消息时不把位置拽到底部
    var prevVisibleCount by remember { mutableStateOf(0) }

    /**
     * LazyColumn 的 item key。
     *
     * 原来直接用**下标**作 key：消息被删除/回滚/重生成后下标整体前移，Compose 会把"上一条消息"的
     * 组合状态复用到下一条上（气泡里那几个 `remember` 的弹窗开关会串位）。
     * 改成「时间戳 + 角色」这种跟着消息走的值；再对同值项加 `#n` 后缀，保证同一时刻键**唯一**——
     * key 重复会让 LazyColumn 直接抛异常崩掉，不能赌"时间戳不会撞"。
     */
    /**
     * `canRegenerate` 原本对每个可见 item 每帧做一次 `subList + any{}`（O(n)），
     * 而打字机每 16ms 就重组一次 —— 复杂度是「可见条数 × 消息条数」。
     * 这里只跟 messages 绑定算一次：第一条用户消息的下标。
     */
    val firstUserIndex = remember(messages) { messages.indexOfFirst { it.role == "user" } }

    val itemKeys = remember(visible) {
        val seen = HashMap<String, Int>()
        visible.map { (_, m) ->
            val base = "${m.timestamp}-${m.role}"
            val nth = (seen[base] ?: 0) + 1
            seen[base] = nth
            if (nth == 1) base else "$base#$nth"
        }
    }
    LaunchedEffect(visibleCount, viewportHeight) {
        if (visibleCount <= 0) return@LaunchedEffect
        if (visibleCount != prevVisibleCount) {
            prevVisibleCount = visibleCount
            listState.scrollToItem(visibleCount - 1, 10000)
        } else {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            if (lastVisible != null && lastVisible >= visibleCount - 2) {
                listState.scrollToItem(visibleCount - 1, 10000)
            }
        }
    }
    // 问题 #3：流式回复不断变长时也要贴底；用 scrollToItem（瞬时）避免反复打断上面的动画
    LaunchedEffect(draft) {
        if (visibleCount > 0) listState.scrollToItem(visibleCount - 1, 10000)
    }

    Box(modifier = modifier) {
        // 聊天背景
        imageModel(backgroundUri)?.let { model ->
            AsyncImage(
                model = model,
                contentDescription = "聊天背景",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = dimmedScrims(ChatAreaScrims, bgDim)
                        )
                    )
            )
        }
        // 背景遮罩，保证文字可读
        if (backgroundUri == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .onSizeChanged { viewportHeight = it.height },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            // 底部对齐：精简模式下只有一条消息时，它会贴着输入框上方显示，
            // 而不是孤零零挂在消息区顶部（问题 #2 的第二点）。
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)
        ) {
            // 工具形态的「使用示例」：它们是卡里的开场白字段，在这里变成"点击即填入输入框"的
            // 任务样例。只在**还没发过任何消息**时出现——任务一旦开始，示例就该让位给真实内容。
            if (examples.isNotEmpty() && messages.none { it.role == "user" }) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "使用示例（点一下填入输入框，改成你自己的内容）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        examples.forEach { ex ->
                            Surface(
                                onClick = { onUseExample(ex) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    ex,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            // key 跟着消息走（见 itemKeys），不跟着下标走
            itemsIndexed(visible, key = { i, _ -> itemKeys[i] }) { i, (index, msg) ->
                MessageBubble(
                    char = char,
                    msg = msg,
                    index = index,
                    canRollback = index > 0,
                    canEdit = true,
                    // swipe 重新生成只作用于最后一条 assistant 消息；
                    // 中途消息重新生成会截断后续内容，只留「回溯」更可预期
                    canRegenerate = msg.role == "assistant" && index == messages.lastIndex &&
                        firstUserIndex in 0 until index,
                    actions = actions,
                    ttsSpeaking = ttsSpeaking,
                    engine = engine
                )
            }
            if (sending) {
                if (draft.isNotBlank()) {
                    item {
                        MessageBubble(
                            char = char,
                            // 打字机每 16ms 会重组一次，这里原本每帧 new 一个 ChatMessage
                            // （等于每帧都让气泡"变成了另一条消息"）；按 draft 记忆即可
                            msg = remember(draft) { ChatMessage(role = "assistant", content = draft) },
                            index = messages.size,
                            canRollback = false,
                            canEdit = false,
                            canRegenerate = false,
                            plain = true,
                            actions = actions
                        )
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("TA 正在输入…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (messages.isEmpty() && !sending) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "和 ${char.name} 开始聊天吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (backgroundUri == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            // 0.1.2：灵感回复入口跟在最新一条气泡的右下方（原在「更多」工具栏里，位置太深）。
            // 只在**最新一条是角色回复**时才出现：它回答的是"用户接下来能说什么"，
            // 最新一条还是用户自己的消息时（刚发出去还没回、或模型这次没吐内容）不该出现（用户 2026-09-16 口径）；
            // 生成中也先收起，等回复落地再出现。
            if (engine.inspiration && !sending && messages.lastOrNull { it.role != "scene" }?.role == "assistant") {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (inspirationLoading) {
                            Text(
                                "正在想…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                        Surface(
                            onClick = onInspiration,
                            enabled = !inspirationLoading,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            tonalElevation = 3.dp
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (inspirationLoading) {
                                    CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = "灵感回复",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "灵感回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 图标化长按菜单的一项 */
internal data class IconMenuAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val action: () -> Unit
)

/**
 * 「最近一次量到的窗口位置」的**非状态**容器。
 * 布局回调（onGloballyPositioned）每趟都会跑：写 Compose 状态会把整张卡片标脏并触发重组，
 * 写这个普通字段则没有任何副作用；真正需要位置时（长按打开菜单）再快照进状态。
 */
internal class RectHolder {
    var value: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero
}

/** 长按菜单：图标横向排列，只留图标不留文字（图标带 contentDescription 供无障碍）。
 *  显示位置自适应：默认在锚点（气泡）上方居中，太靠屏幕顶部时改为下方，尽量不遮挡气泡内容。
 *  [anchor] 为气泡在窗口中的位置（调用方用 onGloballyPositioned 实测）。 */
@Composable
internal fun IconMenu(
    expanded: Boolean,
    anchor: androidx.compose.ui.geometry.Rect,
    onDismiss: () -> Unit,
    items: List<IconMenuAction>
) {
    if (!expanded) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    var menuHeightPx by remember { mutableStateOf(0) }
    val marginPx = with(density) { 6.dp.roundToPx() }
    val placeAbove = anchor.top >= menuHeightPx + marginPx + 120f
    androidx.compose.ui.window.Popup(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        alignment = Alignment.TopCenter,
        offset = if (placeAbove) {
            androidx.compose.ui.unit.IntOffset(0, -(menuHeightPx + marginPx))
        } else {
            androidx.compose.ui.unit.IntOffset(0, anchor.height.toInt() + marginPx)
        }
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .padding(vertical = 2.dp)
                .onSizeChanged { menuHeightPx = it.height }
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { i, item ->
                    IconButton(onClick = { onDismiss(); item.action() }) {
                        Icon(
                            item.icon,
                            contentDescription = item.description,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (i != items.lastIndex) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}

/** 问题 #16：新会话顶部的「世界观 / 当前场景」卡片（不参与对话上下文，只做展示，可复制/编辑） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SceneCard(
    text: String,
    onCopy: () -> Unit,
    onEdit: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var anchorRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // 每趟布局只更新这个**非状态**字段（写它不会触发重组），
    // 真正喂给菜单的状态只在"打开菜单"那一刻快照一次
    val bounds = remember { RectHolder() }
    // 右键重开菜单时忽略迟到的旧 dismiss，同 MessageBubble
    var menuDismissGuardUntil by remember { mutableStateOf(0L) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            // 不在这里写 Compose 状态（每趟布局都写 = 键盘开合时所有卡片一起重组）
            Modifier.onGloballyPositioned { bounds.value = it.boundsInWindow() }
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    // 桌面：右键＝长按（场景卡的复制/编辑菜单）
                    .desktopSecondaryClick {
                        anchorRect = bounds.value
                        menuDismissGuardUntil = System.currentTimeMillis() + 250
                        menuOpen = true
                    }
                    .combinedClickable(
                        onClick = { },
                        onLongClick = {
                            anchorRect = bounds.value // 打开前把当前位置快照进状态
                            menuOpen = true
                        }
                    )
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoStories, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "世界观 / 当前场景",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconMenu(
                expanded = menuOpen,
                anchor = anchorRect,
                onDismiss = { if (System.currentTimeMillis() >= menuDismissGuardUntil) menuOpen = false },
                items = listOf(
                    IconMenuAction(Icons.Filled.ContentCopy, "复制", onCopy),
                    IconMenuAction(Icons.Filled.Edit, "编辑", onEdit)
                )
            )
        }
    }
}

/**
 * 消息气泡的动作回调束。
 *
 * 这 7 个回调从 ChatScreen 一路原样透传到 [MessageBubble]，中间没人改过它们；
 * 之前两个调用点（正常消息 + 流式草稿）各抄一遍名字，加一个动作要改三处。打包成一个对象。
 */
@androidx.compose.runtime.Immutable
internal data class MessageActions(
    val onCopy: (String) -> Unit,
    val onRegenerate: (Int) -> Unit,
    /** 「按格式重排」：把这条成品按卡的「输出格式」重排一遍 */
    val onReformat: (Int) -> Unit,
    val onRollback: (Int) -> Unit,
    val onBranch: (Int) -> Unit,
    val onEdit: (Int, String) -> Unit,
    val onSwitchVariant: (Int, Int) -> Unit,
    val onDeleteVariant: (Int, Int) -> Unit,
    /** 朗读这条气泡的正文（P-F2 v1，系统 TTS） */
    val onSpeak: (String) -> Unit,
    /** 停止当前朗读 */
    val onStopSpeak: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    char: CharacterCard,
    msg: ChatMessage,
    index: Int,
    canRollback: Boolean,
    canEdit: Boolean,
    canRegenerate: Boolean,
    /** 有朗读在播：菜单里多一项「停止朗读」 */
    ttsSpeaking: Boolean = false,
    // 流式打字机中的草稿气泡：纯文本渲染
    plain: Boolean = false,
    /** 引擎形态：末尾常驻复制按钮 / 版本切换条 / 「换一批」的叫法都由它决定 */
    engine: EngineSpec = Engines.of(""),
    actions: MessageActions
) {
    // 问题 #16：世界观 / 当前场景不是对话气泡，单独渲染成场景卡（长按可复制/编辑）
    if (msg.role == "scene") {
        var sceneEdit by remember { mutableStateOf(false) }
        SceneCard(
            text = msg.content,
            onCopy = { actions.onCopy(msg.content) },
            onEdit = { if (canEdit) sceneEdit = true }
        )
        if (sceneEdit) {
            EditMessageDialog(
                initial = msg.content,
                onConfirm = { actions.onEdit(index, it); sceneEdit = false },
                onDismiss = { sceneEdit = false }
            )
        }
        return
    }
    val isUser = msg.role == "user"
    var menuOpen by remember { mutableStateOf(false) }
    var anchorRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    // 同 SceneCard —— 布局回调只写非状态字段，状态在打开菜单时快照一次
    val bounds = remember { RectHolder() }
    // 「选择复制」模式：该气泡临时开启系统文字选择，点气泡退出
    var selectMode by remember { mutableStateOf(false) }
    // 桌面：鼠标停在这一行上就浮出快捷操作条。**悬停态不许用 Popup 做**：
    // Popup 是独立 AWT 窗口，高频进出会闪烁、还会吃掉点击（第一栏提示因此删掉）。
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    // 菜单已开着再右键时，旧 Popup 的 dismiss 事件会**迟到**——
    // 到达顺序通常是「desktopSecondaryClick 重开 → 旧 dismiss」，菜单就被拉回 false，看起来闪一下。
    // 打开菜单时记下时刻，250ms 内的 dismiss 一律忽略：第二次右键＝菜单保持不动（不再闪）。
    var menuDismissGuardUntil by remember { mutableStateOf(0L) }
    val openMenu: () -> Unit = {
        anchorRect = bounds.value
        menuDismissGuardUntil = System.currentTimeMillis() + 250
        menuOpen = true
    }
    var showEdit by remember { mutableStateOf(false) }
    var showRollback by remember { mutableStateOf(false) }
    var showVariants by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AsyncImage(
                model = imageModel(char.avatarUri),
                contentDescription = char.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(chatAvatarSize)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        // 气泡 ＋ 悬停操作条包在同一层：hoverable 覆盖两者，鼠标从气泡移到操作条上不会"离开"，
        // 否则按上按钮的前一刻它就消失了（操作条在气泡外侧，本来就靠这一层把它们连起来）
        Row(
            modifier = if (isDesktopLayout) Modifier.hoverable(hoverSource) else Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDesktopLayout && isUser) {
                BubbleHoverBar(
                    visible = hovered,
                    speaking = ttsSpeaking,
                    isAssistant = false,
                    onCopy = { actions.onCopy(msg.content) },
                    onSpeak = { actions.onSpeak(msg.content) },
                    onStopSpeak = { actions.onStopSpeak() },
                    onMore = openMenu
                )
            }
            Box(
                // 同上——布局回调只写非状态字段，避免键盘开合时所有气泡整体重组
                Modifier.onGloballyPositioned { bounds.value = it.boundsInWindow() }
            ) {
                Column(
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    // swipe 版本切换条：只在有多个版本时出现。
                    // 工具形态不给它（「换一批」的语义是**追加**一条新结果，
                    // "切回上一版"对成品没意义），但版本本身照旧留着，导出的记录不丢东西。
                    if (engine.variantSwitcher && msg.variantCount > 1) {
                        SwipeBar(
                            msg = msg,
                            onSwitch = { actions.onSwitchVariant(index, it) },
                            onShowList = { showVariants = true }
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        ),
                        color = if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .widthIn(max = chatBubbleMaxWidth)
                            .padding(vertical = 2.dp)
                            // 桌面：右键＝长按（同一张菜单，锚点同一份快照）
                            .desktopSecondaryClick(onSecondary = openMenu)
                            .combinedClickable(
                                onClick = { if (selectMode) selectMode = false },
                                // 桌面上不挂长按：鼠标拖选文字必然"按住一会儿再移动"，
                                // 长按计时器会在选区拉出来之前先把菜单弹出来（右键已经能开同一张菜单）
                                onLongClick = if (isDesktopLayout) null else ({
                                    anchorRect = bounds.value // 打开前把当前位置快照进状态
                                    menuOpen = true
                                })
                            )
                    ) {
                    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                    Column {
                        // 用户随消息发的图片
                        if (msg.imageUri != null) {
                            AsyncImage(
                                model = imageModel(msg.imageUri),
                                contentDescription = "随消息图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(start = 14.dp, end = 14.dp, top = 10.dp)
                                    .heightIn(max = 220.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        MarkdownBody(
                            content = msg.content,
                            color = textColor,
                            // 桌面：气泡文字**默认就能拖选**——鼠标拖选是桌面阅读的基本预期，
                            // 藏在菜单里没人找得到（手机端不变，仍是长按菜单里的「选择文字复制」）
                            selectable = selectMode || isDesktopLayout,
                            plain = plain,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                        // 工具形态：成品是要**拿走**的，所以复制做成可见按钮，而不是藏在长按菜单里
                        // 草稿气泡（plain）不给：它还在流式生成，复制到的是半截东西。
                        // ⚠ 必须和文字**同在一个 Column 里**：`Surface` 的 content 是 `Box`
                        //   （`propagateMinConstraints = true`），直接写在 Surface 里会与文字**重叠**在同一层
                        //   左上角——覆盖正文、还挡住第一行的点击（用户截图报的就是这个）。
                        if (engine.bubbleCopyButton && !isUser && !plain && msg.content.isNotBlank()) {
                            TextButton(
                                onClick = { actions.onCopy(msg.content) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, null, Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            IconMenu(
                expanded = menuOpen,
                anchor = anchorRect,
                onDismiss = { if (System.currentTimeMillis() >= menuDismissGuardUntil) menuOpen = false },
                items = buildList {
                    add(IconMenuAction(Icons.Filled.ContentCopy, "复制整段") { actions.onCopy(msg.content) })
                    // 气泡里的成品也要能发给别人（手机＝系统分享面板，桌面＝写剪贴板 + 提示，
                    // 见 DesktopPlatformUi.shareText）。草稿（plain）不给：那还是半截东西。
                    if (!plain && msg.content.isNotBlank()) {
                        add(IconMenuAction(Icons.Filled.Share, "分享") {
                            runCatching { Platform.ui.shareText("分享消息", msg.content) }
                                .onFailure { showToast("分享失败：${it.message ?: "系统分享不可用"}") }
                        })
                    }
                    // P-F2 v1：朗读（用系统 TTS；没有中文语音包会在聊天页顶部提示去系统设置装）
                    if (msg.role == "assistant" && msg.content.isNotBlank()) {
                        add(IconMenuAction(Icons.Filled.VolumeUp, "朗读") { actions.onSpeak(msg.content) })
                    }
                    if (ttsSpeaking) {
                        add(IconMenuAction(Icons.Filled.Close, "停止朗读") { actions.onStopSpeak() })
                    }
                    // 桌面气泡本来就默认可拖选，这一项只在手机端有意义
                    if (!isDesktopLayout) {
                        add(IconMenuAction(Icons.Filled.TextFields, "选择文字复制") {
                            selectMode = true
                            showToast("长按文字选择复制，点气泡退出")
                        })
                    }
                    if (canEdit) {
                        add(IconMenuAction(Icons.Filled.Edit, "编辑") { showEdit = true })
                    }
                    if (!isUser && canRegenerate) {
                        // 工具形态：成品形状不对时点一下重排。**只有卡里真写了「输出格式」
                        // 才给这一项** —— 没写格式要求的话它点了什么都不会变，属于"看起来有用其实没用"的入口。
                        if (engine.formatRewrite && char.outputFormat.isNotBlank()) {
                            add(IconMenuAction(Icons.Filled.FormatListBulleted, "按格式重排") {
                                actions.onReformat(index)
                            })
                        }
                        // 工具形态叫「换一批」（语义＝再给一份新结果，见 Engines.TOOL）
                        add(IconMenuAction(Icons.Filled.Refresh, engine.regenerateLabel) { actions.onRegenerate(index) })
                    }
                    if (canRollback) {
                        add(IconMenuAction(Icons.Filled.History, "回溯") { showRollback = true })
                    }
                }
            )
            }
            if (isDesktopLayout && !isUser) {
                BubbleHoverBar(
                    visible = hovered,
                    speaking = ttsSpeaking,
                    isAssistant = msg.role == "assistant" && msg.content.isNotBlank(),
                    onCopy = { actions.onCopy(msg.content) },
                    onSpeak = { actions.onSpeak(msg.content) },
                    onStopSpeak = { actions.onStopSpeak() },
                    onMore = openMenu
                )
            }
        }
    }

    if (showEdit) {
        EditMessageDialog(
            initial = msg.content,
            onConfirm = { actions.onEdit(index, it); showEdit = false },
            onDismiss = { showEdit = false }
        )
    }
    if (showRollback) {
        RollbackDialog(
            onBranch = { actions.onBranch(index) },
            onRollback = { actions.onRollback(index) },
            onDismiss = { showRollback = false }
        )
    }
    if (showVariants) {
        VariantsDialog(
            msg = msg,
            onSelect = { actions.onSwitchVariant(index, it); showVariants = false },
            onDelete = { actions.onDeleteVariant(index, it) },
            onDismiss = { showVariants = false }
        )
    }
}

/**
 * 操作条一颗图标的边长（用户反馈"操作条偏小，且字号调大后不跟着变"）：
 * 基准 26→30dp，并随 [ThemeState.fontScale] 缩放；占位宽度/圆角/图标尺寸全从它推导，不留写死的 dp。
 */
private fun hoverBarIconSize() = (30f * ThemeState.fontScale).dp

/**
 * 气泡悬停浮出的快捷操作条（桌面）。
 *
 * **常驻占位、只切内容**：不悬停时这里是一个等宽 [Spacer]。让它"出现/消失"会有两个后果——
 * ① 一行的宽度变了，鼠标刚进气泡就把按钮挤出去、指针随即落到气泡外 ⇒ 出现→消失→出现的死循环；
 * ② 消息列表的行宽随之变化，视觉上整列在抖。
 *
 * 位置在气泡**外侧**（自己发的在左、对方的在右），和微信桌面版一致；
 * 完整菜单仍在（右键 / 长按 / 这一条的「⋯」），这里只放最高频的三件事。
 */
@Composable
private fun BubbleHoverBar(
    visible: Boolean,
    speaking: Boolean,
    isAssistant: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onMore: () -> Unit
) {
    // 占位宽度 = 3 颗图标：常驻不随按钮增减变化（防"出现→消失"死循环），数值随字号缩放
    val slot = hoverBarIconSize() * 3
    if (!visible) {
        Spacer(Modifier.width(slot))
        return
    }
    Box(Modifier.width(slot), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(hoverBarIconSize() / 2),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HoverBarIcon(Icons.Filled.ContentCopy, "复制整段", onCopy)
                if (isAssistant) {
                    // 正在播的时候把「朗读」换成「停止」，与右键菜单同一套口径
                    if (speaking) HoverBarIcon(Icons.Filled.Close, "停止朗读", onStopSpeak)
                    else HoverBarIcon(Icons.Filled.VolumeUp, "朗读", onSpeak)
                }
                HoverBarIcon(Icons.Filled.MoreVert, "更多", onMore)
            }
        }
    }
}

/** 操作条上的一颗图标。不用 IconButton：它自带 48dp 最小交互尺寸，会把操作条撑成两倍高 */
@Composable
private fun HoverBarIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val s = hoverBarIconSize()
    Box(
        Modifier
            .size(s)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, Modifier.size(s * 0.6f))
    }
}

/** swipe 版本切换条：‹ 2/3 ›，点计数打开全部版本列表 */
@Composable
internal fun SwipeBar(
    msg: ChatMessage,
    onSwitch: (Int) -> Unit,
    onShowList: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onSwitch(msg.activeIndex - 1) },
            enabled = msg.activeIndex > 0,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(Icons.Filled.KeyboardArrowLeft, "上一版本", Modifier.size(18.dp))
        }
        Text(
            "${msg.activeIndex + 1}/${msg.variantCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onShowList)
                .padding(horizontal = 2.dp)
        )
        IconButton(
            onClick = { onSwitch(msg.activeIndex + 1) },
            enabled = msg.activeIndex < msg.variantCount - 1,
            modifier = Modifier.size(22.dp)
        ) {
            Icon(Icons.Filled.KeyboardArrowRight, "下一版本", Modifier.size(18.dp))
        }
    }
}

/** 编辑消息（任意 role） */
@Composable
internal fun EditMessageDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TextInputDialog(
        title = "编辑消息",
        initial = initial,
        minLines = 4,
        maxLines = 12,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 编辑会话级状态（每轮注入 system prompt）。
 *
 * 叫法与说明**跟形态走**：陪伴 / 多线是关系向的「角色记忆」，玩法型是进度向的
 * 「本局进度」——同一个字段，两种语义，写错会让用户以为要在这里维护"人物关系"。
 */
@Composable
internal fun MemoryEditDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "角色记忆",
    hint: String = "TA 每轮都会看到的长期设定与关键事件（如约定、身份、重要转折）。会话级，只属于本次会话。",
    placeholder: String = "例：用户答应过带 TA 去看海；TA 的左肩有旧伤；两人已经和好过一次…"
) {
    TextInputDialog(
        title = title,
        initial = initial,
        hint = hint,
        placeholder = placeholder,
        clearLabel = "清空",
        clearConfirmText = "清空后这段记忆就没了，无法恢复（保存后立刻生效）。",
        minLines = 6,
        maxLines = 12,
        // 记忆允许被清空，所以「保存」不要求非空白
        confirmEnabled = { true },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** 回溯：创建分支会话（原会话保留）/ 直接回溯（就地删除） */
@Composable
internal fun RollbackDialog(
    onBranch: () -> Unit,
    onRollback: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("回溯到这里") },
        text = {
            Column {
                TextButton(onClick = onBranch, modifier = Modifier.fillMaxWidth()) {
                    Text("创建分支会话")
                }
                Text(
                    "复制当前会话并截断为分支继续，原会话原样保留",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRollback, modifier = Modifier.fillMaxWidth()) {
                    Text("直接回溯")
                }
                Text(
                    "在当前会话中删除该消息及其后的内容，不可恢复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** swipe 全部版本列表：点选切换，右侧删除（至少保留一个） */
@Composable
internal fun VariantsDialog(
    msg: ChatMessage,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val variants = msg.variants ?: listOf(msg.content)
    // 删版本是删掉一整条已生成的回复（生图/长回复都要重新花钱花时间），先确认再删
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全部回复版本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                variants.forEachIndexed { i, v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (i == msg.activeIndex) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelect(i) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "版本 ${i + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                v.replace("\n", " "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { pendingDelete = i }, enabled = variants.size > 1) {
                            Icon(Icons.Filled.Delete, "删除该版本", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
    pendingDelete?.let { index ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除版本 ${index + 1}？") },
            text = { Text("这一条回复会被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(index) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

/**
 * 气泡正文渲染（跨平台重写点）。
 *
 * 原来是 Markwon 渲染进 AndroidView 的 TextView：加粗/斜体/列表等由 Markwon 的 Spannable 完成，
 * 再叠加两条自定义样式（括号浅色、旁白加粗），字体/字号还得手工喂给 TextView。
 * 现在改为共享的 **AnnotatedString + Text**：
 * - 一个 ~80 行的内联解析器接住聊天里实际出现的 markdown（加粗/斜体/行内码/删除线/标题/列表/引用），
 *   Android 一并受益（少一层 AndroidView，Theme 的 Typography/Density 自动管到气泡）；
 * - 两条自定义样式改为 SpanStyle 叠加（同一 range 上后加的属性覆盖先加的，正合"旁白加粗压过正文"的语义）；
 * - 流式打字机阶段仍走纯文本（每 16ms 重跑解析太费）；
 * - 「选择复制」模式用 SelectionContainer（原来靠 TextView 的 setTextIsSelectable）。
 */
@Composable
internal fun MarkdownBody(
    content: String,
    color: Color,
    modifier: Modifier = Modifier,
    // 默认关闭系统文字选择（长按交给应用内菜单）；「选择复制」模式临时开启
    selectable: Boolean = false,
    // 流式打字机阶段用纯文本渲染：每 16ms 重跑 markdown + 正则太费
    plain: Boolean = false
) {
    // 字号与行距沿用原来喂给 TextView 的口径：15sp（字号设置经 Theme 的 Density 缩放自动生效）、行距约 1.35
    val fontScale = ThemeState.fontScale
    val textStyle = LocalTextStyle.current.copy(
        color = color,
        fontSize = (15f * fontScale).sp,
        lineHeight = (20f * fontScale).sp
    )
    val body = remember(content, color, plain, fontScale) {
        if (plain) AnnotatedString(content) else buildMessageBody(content, color)
    }
    if (selectable) {
        SelectionContainer(modifier = modifier) {
            Text(body, style = textStyle)
        }
    } else {
        Text(body, style = textStyle, modifier = modifier)
    }
}

/**
 * 行内 markdown 标记：`***粗斜***` / `**粗**` / `*斜*` / `__粗__` / `_斜_` / `~~删~~` / `` `码` ``。
 * 旧实现的粗体分支写作 `\*\*[^*\n]+\*\*`（"中间不许有任何星号"），于是**嵌套强调**（`**她说*你*好**`）
 * 与 `***粗斜***` 全部匹配失败 ⇒ `**` 原样露在气泡里（2026-09-24 用户报"经常显示异常"）。
 * 改法：粗体用惰性 `(.+?)`（`.` 仍不跨行），并在**前面**补一个 `***` 整段分支（分支顺序＝优先级）；
 * 另补单下划线斜体分支，守卫 `(?<!\w)_(?!_)…_(?!\w)`——免得把 `snake_case` 的下划线误判成斜体。
 */
private val InlineMarkdown = Regex(
    "\\*\\*\\*([^*\\n]+)\\*\\*\\*|\\*\\*(.+?)\\*\\*|\\*([^*\\n]+)\\*|__(.+?)__|" +
        "(?<!\\w)_(?!_)([^_\\n]+?)_(?!\\w)|~~([^~\\n]+)~~|`([^`\\n]+)`"
)
/** 标题：模型常写 `##标题` 不带空格（尤其中文输出），所以这里不强制空格（去井号＋整行加粗）。 */
private val HeadingPrefix = Regex("^\\s*#{1,6}\\s*")
private val BulletPrefix = Regex("^[-*•]\\s+")
private val QuotePrefix = Regex("^>\\s?")
private val NumberedPrefix = Regex("^\\d+[.、)]\\s+")

/** 括号注浅色（同 Markwon 时代的口径：（）【】[] ()） */
private val BracketRegex = Regex("（[^）]*）|【[^】]*】|\\[[^\\]]*\\]|\\([^)]*\\)")

/**
 * 把一条消息构建成 AnnotatedString：markdown 内联样式 + 块级变换（标题/列表/引用）
 * + 括号注浅色 + 旁白行加粗。纯文本处理、无平台依赖。
 */
internal fun buildMessageBody(content: String, base: Color): AnnotatedString {
    val bracketColor = base.copy(alpha = 0.66f)
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val boldItalic = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    val strike = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val code = SpanStyle(fontFamily = FontFamily.Monospace)

    data class Seg(val start: Int, val end: Int, val style: SpanStyle)

    val segments = ArrayList<Seg>()
    /** (行起, 行止, 块级加粗[标题行]) */
    val lineRanges = ArrayList<Triple<Int, Int, Boolean>>()
    val plain = StringBuilder()
    /** ``` 围栏代码块：标记行不显示；块内原样等宽、不跑内联 markdown、不做旁白加粗 */
    var inFence = false
    val codeRanges = ArrayList<Pair<Int, Int>>()

    content.split("\n").forEachIndexed { i, rawLine ->
        if (i > 0) plain.append('\n')
        val lineStart = plain.length
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("```")) {
            inFence = !inFence
            lineRanges.add(Triple(lineStart, plain.length, false)) // 空行 ⇒ isNotBlank=false，不会被加粗
            return@forEachIndexed
        }
        if (inFence) {
            plain.append(rawLine)
            segments.add(Seg(lineStart, plain.length, code))
            codeRanges.add(lineStart to plain.length)
            lineRanges.add(Triple(lineStart, plain.length, false))
            return@forEachIndexed
        }
        var work = rawLine
        var blockBold = false
        // 块级标记：标题加粗去井号、列表换圆点、引用换竖标（Ordered 列表保数字，只去"点"后的空格由原样保留）
        when {
            HeadingPrefix.containsMatchIn(trimmed) -> {
                work = trimmed.replaceFirst(HeadingPrefix, "")
                blockBold = true
            }
            BulletPrefix.containsMatchIn(trimmed) -> {
                work = work.trimStart().replaceFirst(BulletPrefix, "• ")
            }
            QuotePrefix.containsMatchIn(trimmed) -> {
                work = work.trimStart().replaceFirst(QuotePrefix, "▎ ")
            }
        }
        var last = 0
        for (m in InlineMarkdown.findAll(work)) {
            plain.append(work, last, m.range.first)
            val g = m.groups
            val (text, style) = when {
                g[1] != null -> g[1]!!.value to boldItalic
                g[2] != null -> g[2]!!.value to bold
                g[3] != null -> g[3]!!.value to italic
                g[4] != null -> g[4]!!.value to bold
                g[5] != null -> g[5]!!.value to italic
                g[6] != null -> g[6]!!.value to strike
                else -> g[7]!!.value to code
            }
            segments.add(Seg(plain.length, plain.length + text.length, style))
            plain.append(text)
            last = m.range.last + 1
        }
        plain.append(work, last, work.length)
        lineRanges.add(Triple(lineStart, plain.length, blockBold))
    }

    return buildAnnotatedString {
        append(plain.toString())
        // ① markdown 内联样式
        segments.forEach { addStyle(it.style, it.start, it.end) }
        // ② 旁白行整行加粗（被引号完整包裹的对话行不加粗）+ 标题行加粗
        lineRanges.forEach { (start, end, blockBold) ->
            val line = plain.substring(start, end)
            if (line.isNotBlank() && (blockBold || !isDialogueLine(line))) {
                addStyle(bold, start, end)
            }
        }
        // ③ 括号注浅色（叠在上述样式上，只改颜色）
        BracketRegex.findAll(plain).forEach { m ->
            addStyle(SpanStyle(color = bracketColor), m.range.first, m.range.last + 1)
        }
    }
}

internal fun isDialogueLine(line: String): Boolean {
    val t = line.trim()
    if (t.length < 2) return false
    val open = t.first()
    val close = t.last()
    return (open == '“' || open == '"' || open == '「' || open == '\'') &&
        (close == '”' || close == '"' || close == '」' || close == '\'')
}

internal fun copyToClipboard(text: String) {
    Platform.ui.copyToClipboard("roleplay", text, "已复制")
}

/**
 * 输入栏上方的「历史」开关：图标 + 文字胶囊，展开中反色提示。
 */
@Composable
internal fun MessageHistoryToggle(
    total: Int,
    showAll: Boolean,
    onToggle: () -> Unit
) {
    if (total <= 1) return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (showAll) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (showAll) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(5.dp))
        Text(
            if (showAll) "查看最新消息" else "历史 · $total 条",
            style = MaterialTheme.typography.labelMedium,
            color = if (showAll) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
