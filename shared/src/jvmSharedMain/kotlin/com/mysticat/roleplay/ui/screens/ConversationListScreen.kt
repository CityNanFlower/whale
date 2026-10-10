package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.DesktopCardContextMenu
import com.mysticat.roleplay.ui.DesktopMainHeader
import com.mysticat.roleplay.ui.DesktopMenuRow
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.components.SwipeRevealDelete
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.desktopSecondaryClick
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ConversationListViewModel : ViewModel() {
    data class ConvItem(val conversation: Conversation, val character: CharacterCard?)

    var items by mutableStateOf<List<ConvItem>>(emptyList())
        private set

    /**
     * 读盘失败（会话目录打不开 / 坏 JSON 解析炸了）。**空列表与"读不出来"是两回事**——
     * 以前两者都画成同一个空态，用户以为记录没了，实际重读一遍就好。
     */
    var loadError by mutableStateOf<String?>(null)
        private set

    init {
        refresh()
    }

    /**
     * 会话统计 + 角色回查都搬到后台。
     *
     * 原来这里是 `init{}` 里的**同步**调用：进「聊天记录」tab 那一下要在界面线程上把**全部**
     * 会话 JSON 扫一遍（合成的 1600 个会话冷读实测 4.4s，量法见桌面 `--smoke` 的「列表冷读计时」；
     * 会话少时只有几毫秒，但首帧不该赌用户有多少会话）。
     * 赋值回到主线程：Compose snapshot 状态只能在主线程写（0.1.1-alpha.2 在 IO 里直接赋值翻过车）。
     */
    fun refresh() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Repository.listConversationsForAll()
                        .filter { it.messages.isNotEmpty() }
                        .sortedByDescending { it.updatedAt }
                        .map { ConvItem(it, Repository.getCharacter(it.characterId)) }
                }
            }.onSuccess { list ->
                items = list
                loadError = null
            }.onFailure { e ->
                loadError = e.message ?: e::class.java.simpleName
            }
        }
    }

    fun delete(convId: String) {
        Repository.deleteConversation(convId)
        refresh()
    }
}

// ── 时间刻度（编年长卷的骨架）───────────────────────────────────────────────
// 原型口径（`.tools/ui-preview/v6-sections/index.html` §编年长卷）：时间**不再是行里的小字，
// 而是版式骨架**——左列刻度＝真实更新时间，加节点圆点与发丝竖线，正文按四段分组。

// 原型初版是四档（今天/昨天/本周/更早），用户 2026-10-02 看图后**砍成三档**：
// 「本周」在长卷里是废信息——周几既当段名又当行内刻度，同一件事说两遍；而"本周"的会话按天
// 排开，本来就该一眼看出是周几。三档＝今天／昨天／更早。
internal enum class RecordSeg(val label: String) {
    Today("今天"), Yesterday("昨天"), Earlier("更早")
}

private val WEEK_CN = listOf("日", "一", "二", "三", "四", "五", "六")

private fun startOfDay(ts: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** 会话落在今天／昨天／更早哪一档。差几天也带出来——行内副标题的"周X HH:mm"要用它。 */
private fun dayDiffOf(ts: Long): Int =
    ((startOfDay(System.currentTimeMillis()) - startOfDay(ts)) / 86_400_000L).toInt()

internal fun segOf(ts: Long): RecordSeg {
    // 按"当天零点"相减算天数：夏令时切换那天自然少/多一小时，取整后仍是对的自然日。
    val d = dayDiffOf(ts)
    return when {
        d <= 0 -> RecordSeg.Today
        d == 1 -> RecordSeg.Yesterday
        else -> RecordSeg.Earlier
    }
}

/**
 * 手机长卷的刻度字。
 *
 * 今天/昨天给**时刻**（「21:35」）；**更早只留日期**（「9/24」）——用户 2026-10-02 原话
 * "更早只留日期，不标具体时间"：三天前的会话没人关心它是几点，写出来只会把刻度列撑宽、
 * 把右边的正文挤掉。
 */
private fun tickText(ts: Long): String = when (segOf(ts)) {
    RecordSeg.Today, RecordSeg.Yesterday ->
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    RecordSeg.Earlier ->
        SimpleDateFormat("M/d", Locale.getDefault()).format(Date(ts))
}

/** 行内相对时间：N 分钟前 / 今天 HH:mm / 昨天 HH:mm / 周X HH:mm / M 月 d 日。
 *  这里仍保留"周X HH:mm"——它是**行自己的**副标题，不参与分段，用户看得懂"这是上周三"就行。 */
internal fun relTimeText(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L} 分钟前"
    val days = dayDiffOf(ts)
    val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
    val c = Calendar.getInstance()
    c.timeInMillis = ts
    return when {
        days <= 0 -> "今天 $hm"
        days == 1 -> "昨天 $hm"
        days < 7 -> "周" + WEEK_CN[c.get(Calendar.DAY_OF_WEEK) - 1] + " $hm"
        else -> SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(Date(ts))
    }
}

/** 桌面第二栏的**通栏日期刻度**：与长卷同口径（今天／昨天／M 月 d 日），不再冒「周X」出来。 */
private fun scaleText(ts: Long): String = when (segOf(ts)) {
    RecordSeg.Today -> "今天"
    RecordSeg.Yesterday -> "昨天"
    RecordSeg.Earlier -> SimpleDateFormat("M 月 d 日", Locale.getDefault()).format(Date(ts))
}

/** 搜索：角色名 / 会话名 / 聊天原文 三个维度都匹配（手机与桌面同一份口径）。 */
private fun filterItems(all: List<ConversationListViewModel.ConvItem>, q: String) =
    if (q.isBlank()) all else all.filter { item ->
        item.character?.name?.contains(q, ignoreCase = true) == true ||
            item.conversation.title.contains(q, ignoreCase = true) ||
            item.conversation.messages.any { it.content.contains(q, ignoreCase = true) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenConversation: (characterId: String, conversationId: String) -> Unit,
    onNewConversation: (CharacterCard) -> Unit,
    /** 读取失败态的次动作「打开数据与备份」：落「我的」页（数据与备份那一组就在那儿） */
    onOpenDataBackup: () -> Unit = {},
    vm: ConversationListViewModel = viewModel(factory = noArgViewModelFactory { ConversationListViewModel() })
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showPickChar by remember { mutableStateOf(false) }
    // rememberSaveable：切 tab 离开再回来，搜索词保留（用户 2026-09-21 导航状态保存）
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }

    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val items = vm.items
    val filtered = remember(items, query) { filterItems(items, query.trim()) }
    // 已经是按 updatedAt 倒序的，过滤保持顺序 ⇒ 按段分组后每段内部仍是从新到旧
    val grouped = remember(filtered) {
        RecordSeg.entries.map { seg -> seg to filtered.filter { segOf(it.conversation.updatedAt) == seg } }
    }

    Scaffold(
        // 手机导航页顶部去填充（236 形式统一 · 照 v5 原型）：纸底 ＋ 底发丝线。
        // ⚠ 10-08 那轮曾把本页顶栏统一成主色块（primaryContainer），10-10 口径 236 推翻：
        // 顶部不铺色，全 App 导航页与聊天页同款（见 `WhalePageTopBar`）。
        topBar = {
            WhalePageTopBar(
                title = "聊天记录",
                actions = {
                    // 总数贴右（同 `.mtop`：抬头只报一句「一共几个」，别再堆按钮）
                    Text(
                        "${filtered.size} 个会话",
                        style = type.tiny,
                        letterSpacing = 0.18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = WhaleSpacing.lg)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPickChar = true },
                // 蓝底白线（用户 2026-10-03：记录页蓝色元素太少）——走 accent，各皮肤自动随色
                containerColor = pal.accent,
                contentColor = pal.onAccent
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建对话")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WhaleSpacing.lg, vertical = WhaleSpacing.sm),
                placeholder = { Text("搜角色名 / 会话标题 / 消息原文…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = WhaleShapes.r1
            )

            when {
                // 读取失败与"没有记录"分开画：左对齐（与所在栏同一页边），并给两个可点动作
                vm.loadError != null && items.isEmpty() -> LoadErrorState(
                    detail = vm.loadError.orEmpty(),
                    onRetry = { vm.refresh() },
                    onOpenDataBackup = onOpenDataBackup
                )

                items.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有聊天记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(WhaleSpacing.sm))
                    Text(
                        "到「角色卡」选择一个角色开聊，记录会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                filtered.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("没有匹配的聊天记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(WhaleSpacing.sm))
                    Text(
                        "换个关键词试试——角色名、会话名、聊天原文都能搜",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = WhaleSpacing.xxxl)
                ) {
                    grouped.forEach { (seg, rows) ->
                        if (rows.isNotEmpty()) {
                            item(key = "seg-${seg.name}") { SegmentHeader(seg.label, rows.size) }
                            items(rows, key = { it.conversation.id }) { item ->
                                ChronicleRow(
                                    item = item,
                                    onOpen = {
                                        onOpenConversation(item.conversation.characterId, item.conversation.id)
                                    },
                                    onDelete = { pendingDelete = item.conversation.id }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        WhaleConfirmSheet(
            title = "删除会话？",
            body = "该会话记录删除后无法恢复。",
            confirmLabel = "删除",
            onConfirm = { vm.delete(id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    if (showPickChar) {
        CharacterPickerDialog(
            onDismiss = { showPickChar = false },
            onPick = { card -> showPickChar = false; onNewConversation(card) }
        )
    }
}

/**
 * 列表侧的读取失败态（原型 `.errWrap`）。
 *
 * 三条口径：**左对齐**（与所在栏同一页边，不居中——居中会读成"页面空了"而不是"这一步坏了"）、
 * 说清**已经发生了什么**而不是只说"出错了"、并给**两个可点动作**（重读是实心主动作，
 * "去数据与备份看一眼"是描边次动作）。不许只留一句提示就让人干等。
 */
@Composable
private fun LoadErrorState(
    detail: String,
    onRetry: () -> Unit,
    /** null＝不画「打开数据与备份」次动作（桌面第三栏：那句话已经在下方文案里指过路了，入口不必重复） */
    onOpenDataBackup: (() -> Unit)?
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    Column(Modifier.fillMaxSize().padding(horizontal = WhaleSpacing.lg, vertical = WhaleSpacing.xl)) {
        Text(
            "聊天记录没能读出来",
            style = type.strong,
            fontWeight = FontWeight.Bold,
            color = pal.dangerText
        )
        Text(
            "本机这次没打开会话文件。重试只会重新读一遍，不会改动或删除任何会话。",
            style = type.small,
            color = pal.ink2,
            modifier = Modifier.padding(top = WhaleSpacing.sm)
        )
        Row(
            Modifier.padding(top = WhaleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)
        ) {
            Button(
                onClick = onRetry,
                shape = WhaleShapes.r1,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = pal.accent,
                    contentColor = pal.onAccent
                )
            ) { Text("重新读取") }
            if (onOpenDataBackup != null) {
                OutlinedButton(onClick = onOpenDataBackup, shape = WhaleShapes.r1) { Text("打开数据与备份") }
            }
        }
        Text(
            "连续两次都失败：去「用户 → 数据与备份」确认数据目录还能打开，再回来重试。（$detail）",
            style = type.meta,
            color = pal.ink3,
            modifier = Modifier.padding(top = WhaleSpacing.md)
        )
    }
}

/** 段头：「今天」＋「N 个会话」＋一条通栏发丝线（原型 `.cgrp`）。 */
@Composable
private fun SegmentHeader(label: String, count: Int) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = WhaleSpacing.lg, end = WhaleSpacing.lg, top = 14.dp, bottom = WhaleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = type.label,
            fontWeight = FontWeight.Bold,
            color = pal.ink
        )
        Text(
            "$count 个会话",
            style = type.tiny,
            letterSpacing = 0.1.sp,
            color = pal.ink3,
            modifier = Modifier.padding(start = WhaleSpacing.sm)
        )
        HorizontalDivider(
            color = pal.hairline,
            modifier = Modifier
                .weight(1f)
                .padding(start = WhaleSpacing.sm)
        )
    }
}

/**
 * 编年长卷的一行：**时间刻度当左栏边注**（时刻字 ＋ 发丝竖线 ＋ 节点圆点）＋ 50dp 圆头像 ＋
 * 标题/副标题/末条 ＋ 行尾 `⋯`。
 *
 * 行尾**不再常驻红色删除图标**（用户 2026-10-02 拍板）：整页只有删除一个动作时常驻红图标的
 * 视觉重量过大、误触率高，破坏性动作收进横滑手势后面。`⋯` 是无障碍退路——横滑不可发现，
 * 键盘与读屏用户（以及手滑）不必依赖手势。
 */
@Composable
private fun ChronicleRow(
    item: ConversationListViewModel.ConvItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val conv = item.conversation
    val char = item.character
    var menuOpen by remember { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }

    SwipeRevealDelete(
        revealed = revealed,
        onRevealedChange = { revealed = it },
        onArmedDelete = { revealed = false; onDelete() },
        deleteContentDescription = "删除会话"
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(pal.paper)
                .clickable { if (revealed) revealed = false else onOpen() }
                // 刻度的两根线：贯穿整行的发丝竖线 ＋ 走在那条线上的节点圆点。
                // ⚠ 几何按原型 `.crow` 量：刻度字列 40dp（右边留 6dp）→ 字止于 38dp，
                // 竖线在 **47dp**（列外侧的沟里，不是列内）、圆点半径 3.5dp ⇒ 占 43.5~50.5dp。
                // 第 205 轮把线画在 37dp（列内），圆点左缘 33.5dp 顶到字尾——用户看图报"周几和
                // 空心圆重叠了"。头像从 52dp 起，让开圆点。
                .drawBehind {
                    val lineX = 47.dp.toPx()
                    drawLine(
                        color = pal.hairline,
                        start = Offset(lineX, 0f),
                        end = Offset(lineX, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                    val c = Offset(lineX, 25.dp.toPx())
                    drawCircle(color = pal.paper, radius = 3.5.dp.toPx(), center = c)
                    drawCircle(
                        color = pal.ink3,
                        radius = 3.5.dp.toPx(),
                        center = c,
                        style = Stroke(1.dp.toPx())
                    )
                }
                .padding(start = 4.dp, end = WhaleSpacing.xxs, top = WhaleSpacing.md, bottom = WhaleSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                tickText(conv.updatedAt),
                modifier = Modifier
                    .width(40.dp)
                    .padding(end = 6.dp),
                style = type.meta,
                letterSpacing = 0.03.sp,
                textAlign = TextAlign.End,
                color = pal.ink3,
                maxLines = 1
            )
            // 让开刻度列外侧那条沟（竖线 47dp、圆点右缘 50.5dp），头像从 52dp 起
            Spacer(Modifier.width(8.dp))
            // 全局头像（211）：没图也画「名字第一个字」——与宝库/角色信息同一份组件，
            // 一个角色在任何页面都是同一张脸，不再是认不出人的白圆
            AvatarOrInitial(
                name = char?.name ?: "未",
                avatarUri = char?.avatarUri,
                modifier = Modifier.size(50.dp),
                fontSize = 22.sp,
                shape = CircleShape,
                borderColor = pal.hairline
            )
            Spacer(Modifier.width(WhaleSpacing.sm))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conv.title.ifBlank { "新会话" },
                        style = type.label,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.01).sp,
                        color = pal.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        "${conv.messages.size} 条",
                        style = type.tiny,
                        letterSpacing = 0.06.sp,
                        color = pal.ink3,
                        modifier = Modifier.padding(start = WhaleSpacing.xs)
                    )
                }
                Text(
                    "${char?.name ?: "未知角色"} · ${relTimeText(conv.updatedAt)}",
                    style = type.caption,
                    color = pal.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                conv.messages.lastOrNull()?.let { last ->
                    Text(
                        last.content,
                        style = type.small,
                        color = pal.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = WhaleSpacing.xs)
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "会话操作",
                        tint = pal.ink3,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        // 菜单项文案刻意与垃圾桶图标的 content-desc 错开（"删除**这条**会话"）——
                        // 冒烟 `assert 删除会话` 是按 text／content-desc 匹配的，两处同名就等于
                        // 断言命中了菜单文字、图标坏掉它照样绿（见 [SwipeRevealDelete] 的坑）。
                        text = { Text("删除这条会话") },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterPickerDialog(
    onDismiss: () -> Unit,
    onPick: (CharacterCard) -> Unit
) {
    // 不能组合期裸读盘 —— 每次重组都会全量重读角色目录。
    // `remember` 只挡住了"反复读"，**没挡住"在界面线程上读"**——角色一多，
    // 打开这个框仍要卡一下（`listCharacters()` 现在有扫描缓存兜底，但冷启动那一次仍要全量解析）。
    // 改成组合期先渲染空列表、读盘在后台补上。
    // 初值用 null 而不是空列表：**空列表会先闪一句"还没有角色可对话"**——读盘还没回来就说这话是撒谎，
    // 与 ChatScreen 那道 ready 门同一个道理（那边是"角色不存在或已被删除"）。
    val loaded by produceState(initialValue = null as List<CharacterCard>?) {
        value = withContext(Dispatchers.IO) { Repository.listCharacters() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择一个角色开始对话") },
        text = {
            val chars = loaded
            if (chars == null) {
                Text("正在读取角色卡…")
            } else if (chars.isEmpty()) {
                Text("还没有角色可对话，先去「角色卡」创建一个。")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(chars, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(WhaleShapes.r1)
                                .clickable { onPick(c) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarOrInitial(
                                name = c.name,
                                avatarUri = c.avatarUri,
                                modifier = Modifier.size(40.dp),
                                fontSize = 18.sp,
                                shape = CircleShape
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(c.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 桌面第二栏的一个条目：日期刻度（通栏）或一条会话行。 */
private sealed interface RailNode {
    data class Scale(val label: String) : RailNode
    data class Item(val item: ConversationListViewModel.ConvItem) : RailNode
}

/**
 * 桌面聊天记录页的**跨栏筛选状态**（第 220 轮 · 218-1）。
 *
 * 原型「会话栅」把这一页拆成**第二栏角色索引 ＋ 第三栏会话卡片格**，两栏看的是同一份会话，
 * 所以「选中哪个角色」和「选哪个时间段」必须活在两栏之上——以前第二栏直接列会话、第三栏只出
 * 正文，没��跨栏状态可共享；现在第二栏点角色要就地筛第三栏的格子，就得有个共同的对象。
 * 提到 MainScreen 里 `remember` 一次，两栏拿同一个实例。
 */
@Stable
internal class DesktopConversationFilter {
    /** 空串＝全部角色 */
    var characterName by mutableStateOf("")
    /** null＝全部时间；否则只列这一档（今天／昨天／更早） */
    var seg: RecordSeg? by mutableStateOf(null)

    /**
     * 搜索词（用户 2026-10-03 反馈 ③）。
     *
     * 原来这个 `query` 是第二栏自己的局部状态，第三栏看不到：命中只在**角色索引**里收窄，
     * 想看命中的会话非得先点中那个角色再等索引把它筛出来——搜索的真正结果（哪些会话命中了）
     * 反而一个字也没露。现在它与角色、时段一样住在两栏之上的这份对象里，
     * 第三栏的卡片格也过一遍 [filterItems]，搜索结果直接铺在主栏。
     */
    var query by mutableStateOf("")

    val roleLabel: String get() = characterName.ifBlank { "全部会话" }

    /** 抬头上的补充说明：有搜索词时把命中的口径写出来，别让"为什么只剩这几张"变成猜。 */
    val scopeNote: String
        get() = when {
            query.isNotBlank() && characterName.isNotBlank() -> "搜索「${query.trim()}」· $characterName"
            query.isNotBlank() -> "搜索「${query.trim()}」"
            characterName.isNotBlank() -> characterName
            else -> "全部会话"
        }
}

/** 一个角色在会话里的统计：头像字、角色名、会话数、最近一次相对时间。 */
private data class RoleIndexRow(
    val name: String,
    val avatarUri: String?,
    val count: Int,
    val lastAt: Long
)

/** 按角色聚合：会话数降序，同数按最近一次时间降序（谁最近聊过排前面）。 */
private fun roleIndexOf(items: List<ConversationListViewModel.ConvItem>): List<RoleIndexRow> {
    val acc = LinkedHashMap<String, RoleIndexRow>()
    // items 已按 updatedAt 倒序 ⇒ 首次遇到即最近一次
    for (it in items) {
        val name = it.character?.name ?: "未知角色"
        val cur = acc[name]
        acc[name] = if (cur == null) {
            RoleIndexRow(name, it.character?.avatarUri, 1, it.conversation.updatedAt)
        } else {
            cur.copy(count = cur.count + 1)
        }
    }
    return acc.values.sortedWith(
        compareByDescending<RoleIndexRow> { it.count }.thenByDescending { it.lastAt }
    )
}

/** 桌面第二栏＝**角色索引**（第 220 轮 · 218-1，按原型 `.chIdx`）。
 *
 * 与改版前最大的不同：**这里不再列会话**。原型给第二栏定的语义是「谁跟我聊过」——
 * 一行一个角色，带会话数与最近时间；把第三栏已经铺成卡片的会话再抄一遍，
 * 第三栏就没有存在意义了（这正是第 196 轮桌面去重想解决的问题，改成索引后不用再维护两套视图）。
 *
 * 选中角色就地筛第三栏的会话栅，不跳页；行尾不挂删除（删除在卡片右下角与会话页）。
 */
@Composable
internal fun DesktopRoleIndexRail(
    filter: DesktopConversationFilter,
    /** 每次变化就重读会话列表（聊天页发完第一条消息会新建会话，索引要跟着出现） */
    refreshKey: Int,
    /** Ctrl+N：>0 = 弹出"选角色开新会话"；外壳发完会自行归零 */
    newSignal: Int = 0,
    /** Ctrl+K：>0 = 聚焦搜索框；外壳发完会自行归零 */
    focusSearchSignal: Int = 0,
    /**
     * "退回全部会话索引"（用户 2026-10-03 反馈 ②）。
     *
     * 进了会话页之后第三栏整块换成聊天正文，第二栏还在，于是索引本身成了唯一的退路——
     * 但原来点索引只是**改筛选**，会话正文不会让位，用户就再也回不到那页卡片格。
     * 这里把退路做成第二栏的一条显式动作：选中角色时同时把第三栏换回索引格。
     */
    onBackToIndex: () -> Unit,
    onNew: (CharacterCard) -> Unit,
    vm: ConversationListViewModel = viewModel(factory = noArgViewModelFactory { ConversationListViewModel() })
) {
    var showPickChar by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) { vm.refresh() }
    LaunchedEffect(newSignal) { if (newSignal > 0) showPickChar = true }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearchSignal) { if (focusSearchSignal > 0) searchFocus.requestFocus() }

    val pal = LocalWhalePalette.current
    val type = LocalWhaleType.current
    // 搜索与角色筛选共用一条链：先按字搜，再按选中的角色与时段切。
    // query 住在 [DesktopConversationFilter] 上（与第三栏同一份），所以命中结果两栏同时收窄。
    val query = filter.query
    val searched = remember(vm.items, query) { filterItems(vm.items, query.trim()) }
    val roles = remember(searched) { roleIndexOf(searched) }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.surface)
    ) {
        // 栏头与其余第二栏同源（用户 10-10 口径 237）：手写版的 end 6 与统一版 end 8 差 2dp，
        // 摆在一起就是"这栏右边空一点"；换 `DesktopRailHeader` 后四栏同一份取值。
        DesktopRailHeader(title = "会话") {
            IconButton(onClick = { showPickChar = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "新建会话",
                    tint = pal.ink2,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 「全部会话」是索引的零档，点它清掉角色筛选（原型同款）
        IndexRow(
            avatarUri = null,
            name = "全部会话",
            meta = "${searched.size} 个",
            selected = filter.characterName.isBlank(),
            leadingGlyph = true,
            onClick = {
                filter.characterName = ""
                onBackToIndex()
            }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { filter.query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .focusRequester(searchFocus),
            placeholder = { Text("搜索会话", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, null, tint = pal.ink3, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = WhaleShapes.r1
        )
        GroupLabel("角色索引")
        if (roles.isEmpty()) {
            Text(
                if (query.isBlank()) "还没有聊天记录" else "没有匹配的角色",
                style = MaterialTheme.typography.bodySmall,
                color = pal.ink3,
                modifier = Modifier.padding(14.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(roles, key = { it.name }) { row ->
                    IndexRow(
                        avatarUri = row.avatarUri,
                        name = row.name,
                        meta = "${row.count} · ${relTimeText(row.lastAt)}",
                        selected = filter.characterName == row.name,
                        onClick = {
                            filter.characterName = if (filter.characterName == row.name) "" else row.name
                            // 反馈 ②：在会话正文里点索引＝"我要回去看卡片"，所以一并把第三栏换回索引格
                            onBackToIndex()
                        }
                    )
                }
            }
        }
    }

    if (showPickChar) {
        CharacterPickerDialog(
            onDismiss = { showPickChar = false },
            onPick = { card -> showPickChar = false; onNew(card) }
        )
    }
}

/** 角色索引里的一行：26dp 首字头像 ＋ 角色名 ＋ 行尾「N · 相对时间」。 */
@Composable
private fun IndexRow(
    avatarUri: String?,
    name: String,
    meta: String,
    selected: Boolean,
    onClick: () -> Unit,
    /** 「全部会话」那行没有角色头像，用会话图标占位 */
    leadingGlyph: Boolean = false
) {
    val pal = LocalWhalePalette.current
    val type = LocalWhaleType.current
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(WhaleShapes.r1)
            // 选中＝**实底**（用户 10-10 口径 237：会话栏与其余栏对齐，撤「浅面＋accent 描边」）。
            // 悬停也自绘：桌面第二栏的行语言由 `DesktopRailRow` 定（M3 组件自带的悬停口径不跟我们的令牌）。
            .background(
                when {
                    selected -> colors.secondaryContainer
                    hovered -> colors.surfaceContainerHighest
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingGlyph) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(1.dp, pal.hairline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.MoreVert,
                    null,
                    tint = pal.ink3,
                    modifier = Modifier.size(13.dp)
                )
            }
        } else {
            AvatarOrInitial(
                name = name,
                avatarUri = avatarUri,
                modifier = Modifier.size(26.dp),
                fontSize = 13.sp,
                shape = CircleShape,
                borderColor = pal.hairline
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            name,
            style = type.small,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.onSecondaryContainer else pal.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            meta,
            style = type.meta,
            color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.8f) else pal.ink3,
            maxLines = 1
        )
    }
}

/** 索引分组抬头（原型 `.gh`：小字 ＋ 下缘发丝线）。 */
@Composable
private fun GroupLabel(label: String) {
    val pal = LocalWhalePalette.current
    val type = LocalWhaleType.current
    Text(
        label,
        style = type.tiny,
        letterSpacing = 0.1.sp,
        color = pal.ink3,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = pal.hairline2,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f
                )
            }
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 5.dp)
    )
}

/**
 * 桌面聊天页左侧的**会话栏**（左右分栏的左栏）。
 *
 * 为什么放在聊天页而不是复用「聊天记录」tab：桌面上切换会话不该离开聊天页（典型 IM 形态），
 * 而点 tab 再点会话是两次跳转。数据仍走 [ConversationListViewModel]（同一套读盘与排序口径），
 * 只是排版换成桌面口径——紧凑行、选中高亮、不要卡片阴影。
 *
 * 2026-10-02（v6.7）：抬头改「聊天记录」＋总数；行与行之间插**通栏日期刻度**（四种时间写法归四档）；
 * 行内副标题只留「角色名 · 条数」（时间已由刻度承担，不必在每行再写一遍）；
 * 选中行＝浅面 ＋ 1px accent 内描边，**不再是实心蓝底**——头像的浅底只表示"我的气泡／封面占位"这一件事。
 */
@Composable
internal fun DesktopConversationRail(
    selectedConversationId: String,
    /** 每次变化就重读会话列表（聊天页发完第一条消息会新建会话，左栏要跟着出现） */
    refreshKey: Int,
    /** Ctrl+N（全局快捷键 ①）：>0 = 弹出"选角色开新会话"；外壳发完会自行归零 */
    newSignal: Int = 0,
    /** Ctrl+K（全局快捷键）：>0 = 聚焦搜索框；外壳发完会自行归零 */
    focusSearchSignal: Int = 0,
    onOpen: (characterId: String, conversationId: String) -> Unit,
    onNew: (CharacterCard) -> Unit,
    vm: ConversationListViewModel = viewModel(factory = noArgViewModelFactory { ConversationListViewModel() })
) {
    // rememberSaveable：切到别的第一栏图标再回来，搜索词与滚动位置都在（用户 2026-09-21）
    var query by rememberSaveable { mutableStateOf("") }
    var showPickChar by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) { vm.refresh() }
    LaunchedEffect(newSignal) { if (newSignal > 0) showPickChar = true }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearchSignal) { if (focusSearchSignal > 0) searchFocus.requestFocus() }

    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val items = remember(vm.items, query) { filterItems(vm.items, query.trim()) }
    // 刻度插在"分组值变化"的地方，所以相邻同刻度的会话不会每行都顶一条
    val nodes = remember(items) {
        val out = ArrayList<RailNode>(items.size * 2)
        var last = ""
        for (it in items) {
            val s = scaleText(it.conversation.updatedAt)
            if (s != last) {
                out += RailNode.Scale(s)
                last = s
            }
            out += RailNode.Item(it)
        }
        out
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "聊天记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = pal.ink
            )
            Text(
                if (items.size == vm.items.size) "${items.size} 个会话"
                else "${items.size} / ${vm.items.size} 个会话",
                style = type.tiny,
                letterSpacing = 0.18.sp,
                color = pal.ink3,
                modifier = Modifier.padding(start = WhaleSpacing.sm)
            )
            IconButton(onClick = { showPickChar = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "新建会话",
                    tint = pal.ink2,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .focusRequester(searchFocus),
            placeholder = { Text("搜索会话", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, null, tint = pal.ink3, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = WhaleShapes.r1
        )
        if (items.isEmpty()) {
            Text(
                if (query.isBlank()) "还没有聊天记录" else "没有匹配的会话",
                style = MaterialTheme.typography.bodySmall,
                color = pal.ink3,
                modifier = Modifier.padding(14.dp)
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = WhaleSpacing.xs)
            ) {
                items(nodes) { node ->
                    when (node) {
                        is RailNode.Scale -> ScaleHeader(node.label)
                        is RailNode.Item -> RailRow(
                            item = node.item,
                            selected = node.item.conversation.id == selectedConversationId,
                            onOpen = { onOpen(node.item.conversation.characterId, node.item.conversation.id) }
                        )
                    }
                }
            }
        }
    }

    if (showPickChar) {
        CharacterPickerDialog(
            onDismiss = { showPickChar = false },
            onPick = { card -> showPickChar = false; onNew(card) }
        )
    }
}

/**
 * 桌面聊天记录页的**第三栏＝会话卡片格**（第 220 轮 · 218-1，原型 `.cwall.d`）。
 *
 * 这块只在**没选中任何会话**时出现（选中后第三栏换成聊天正文）——它替掉了改版前
 * 「选一个会话开始聊天」那段空态：一整页空着、什么也选不着，而会话其实就在同一份数据里。
 *
 * 版式照原型：抬头（角色名/「全部会话」＋ N 个 · 更新时间倒序 ＋ 时间 chips ＋ 新建按钮）
 * → 按今天／昨天／更早分段，每段一格等宽卡片（首字头像 ＋ 右上时刻 ＋ 标题 ＋ 角色·时间 N 条
 * ＋ 两行末条预览 ＋ 右下删除钮）。点卡片 → 直进会话页（不弹窗、不换页）。
 */
@Composable
internal fun DesktopConversationGrid(
    filter: DesktopConversationFilter,
    refreshKey: Int,
    onOpen: (characterId: String, conversationId: String) -> Unit,
    /** 「新建会话」：弹角色选择器，与第二栏那枚「+」是同一份 [CharacterPickerDialog] */
    onNew: (CharacterCard) -> Unit,
    modifier: Modifier = Modifier,
    vm: ConversationListViewModel = viewModel(factory = noArgViewModelFactory { ConversationListViewModel() })
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showPickChar by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) { vm.refresh() }

    val pal = LocalWhalePalette.current
    val type = LocalWhaleType.current
    // 第二栏的搜索词住在 filter 上，这里也过一遍：命中结果直接铺在主栏（用户 2026-10-03 反馈 ③）
    val searched = remember(vm.items, filter.query) { filterItems(vm.items, filter.query.trim()) }
    // 第二栏选中的角色与第三栏选中的时段是**与**关系，两条都过一遍
    val rows = remember(searched, filter.characterName, filter.seg) {
        searched.filter {
            (filter.characterName.isBlank() || it.character?.name == filter.characterName) &&
                (filter.seg == null || segOf(it.conversation.updatedAt) == filter.seg)
        }
    }
    val grouped = remember(rows) {
        RecordSeg.entries.map { seg -> seg to rows.filter { segOf(it.conversation.updatedAt) == seg } }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(pal.paper)
    ) {
        // 主栏标题（236 形式统一）：字体/内距/分割线全按发现页；右侧字段＝个数读数、时间段筛选与新建会话
        Box(Modifier.padding(horizontal = WhaleSpacing.lg)) {
            DesktopMainHeader(filter.roleLabel) {
                Text(
                    "${rows.size} 个 · 更新时间倒序",
                    style = type.meta,
                    color = pal.ink3
                )
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { filter.seg = null },
                    shape = WhaleShapes.r1
                ) {
                    Text("全部时间", style = type.small, color = if (filter.seg == null) pal.accent else pal.ink2)
                }
                RecordSeg.entries.forEach { seg ->
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = { filter.seg = if (filter.seg == seg) null else seg },
                        shape = WhaleShapes.r1
                    ) {
                        Text(
                            seg.label,
                            style = type.small,
                            color = if (filter.seg == seg) pal.accent else pal.ink2
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { showPickChar = true }, shape = WhaleShapes.r1) {
                    Icon(Icons.Outlined.Add, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建会话", style = type.small)
                }
            }
        }

        when {
            vm.loadError != null && searched.isEmpty() -> LoadErrorState(
                detail = vm.loadError.orEmpty(),
                onRetry = { vm.refresh() },
                onOpenDataBackup = null
            )

            rows.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("这个范围里没有会话", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(WhaleSpacing.sm))
                Text(
                    // 搜索无命中时说清"是没匹配上"还是"这个范围里没有"——两者的下一步不一样
                    when {
                        filter.query.isNotBlank() -> "没有会话匹配「${filter.query.trim()}」，换个词试试"
                        filter.roleLabel != "全部会话" -> "${filter.roleLabel}在这个时间段还没有会话，换个时间或看全部"
                        else -> "换个时间段，或从宝库挑一张卡开始"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // 等宽棋盘（用户 2026-10-03 反馈 ①「卡片没对齐」）。
                //
                // 原来是 `FlowRow` ＋ 每张卡写死 240.dp：卡片宽度与栏宽无关，一行放不满时右边留一截
                // 空档、末行也贴左不齐，标题下沿和卡底沿各走各的。现在按**可用宽度**算列数与卡宽，
                // 逐行 `Row` ＋ `weight(1f)` 均分，卡片宽高都一致 ⇒ 任意栏宽下都是一张对齐的棋盘。
                val gap = 12.dp
                val hPad = WhaleSpacing.lg
                val avail = maxWidth - hPad * 2
                val cols = ((avail + gap) / (CARD_MIN_W + gap)).toInt().coerceIn(1, 4)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = WhaleSpacing.xxxl)
                ) {
                    grouped.forEach { (seg, items) ->
                        if (items.isNotEmpty()) {
                            item(key = "seg-${seg.name}") { SegmentHeader(seg.label, items.size) }
                            items.chunked(cols).forEachIndexed { rowIdx, rowItems ->
                                item(key = "row-${seg.name}-$rowIdx") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = hPad),
                                        horizontalArrangement = Arrangement.spacedBy(gap)
                                    ) {
                                        rowItems.forEach { item ->
                                            ConversationCard(
                                                item = item,
                                                modifier = Modifier.weight(1f),
                                                onOpen = {
                                                    onOpen(item.conversation.characterId, item.conversation.id)
                                                },
                                                onDelete = { pendingDelete = item.conversation.id }
                                            )
                                        }
                                        // 末行不足cols 张时用空位补齐，卡片才不会被拉宽
                                        repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        WhaleConfirmSheet(
            title = "删除会话？",
            body = "该会话记录删除后无法恢复。",
            confirmLabel = "删除",
            onConfirm = { vm.delete(id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    if (showPickChar) {
        CharacterPickerDialog(
            onDismiss = { showPickChar = false },
            onPick = { card -> showPickChar = false; onNew(card) }
        )
    }
}

/** 会话卡的最小宽度：低于它就再挤一列（与原型 `.cwall` 的 auto-fill 口径同源） */
private val CARD_MIN_W = 236.dp

/**
 * 会话卡的固定高度（与 [CARD_MIN_W] 一起保证棋盘对齐）：
 * 12 上 ＋ 34 头像行 ＋ 9 ＋ 标题 ＋ 3 ＋ meta ＋ 6 ＋ 两行预览 ＋ 12 下。
 * 定高是"卡片没对齐"的另一半——只统一宽度、让高度随内容浮动的话，同一行的卡底边仍会参差。
 */
private val CARD_H = 152.dp

/**
 * 一格会话卡片（原型 `.ccard`）：首字头像 ＋ 右上时刻 ＋ 标题 ＋ 角色·时间·条数 ＋ 两行预览。
 *
 * **删除改右击**（用户 2026-10-03 反馈 ④）：原来卡右下角常驻一枚垃圾桶 IconButton，
 * 它压在两行预览上面（预览只能靠 `padding(bottom = 40.dp)` 给它让位，看着就是卡片下部空一块），
 * 而且满屏几十张卡就是几十枚红图标。现在常驻钮撤掉，删除走右键菜单，与宝库角色卡同一条语法。
 */
@Composable
private fun ConversationCard(
    item: ConversationListViewModel.ConvItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalWhalePalette.current
    val type = LocalWhaleType.current
    val conv = item.conversation
    val char = item.character
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(CARD_H)
            .clip(WhaleShapes.r2)
            .border(1.dp, pal.hairline, WhaleShapes.r2)
            .background(pal.surface)
            .desktopSecondaryClick { menuOpen = true }
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarOrInitial(
                    name = char?.name ?: "未",
                    avatarUri = char?.avatarUri,
                    modifier = Modifier.size(34.dp),
                    fontSize = 16.sp,
                    shape = CircleShape,
                    borderColor = pal.hairline
                )
                Spacer(Modifier.weight(1f))
                // 右上角＝时刻（等宽小字，时段已经由分段承担，不重复写"今天"）
                Text(
                    tickText(conv.updatedAt),
                    style = type.micro,
                    letterSpacing = 0.06.sp,
                    color = pal.ink3
                )
            }
            Text(
                conv.title.ifBlank { "新会话" },
                style = type.label,
                fontWeight = FontWeight.SemiBold,
                color = pal.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 9.dp)
            )
            Text(
                "${char?.name ?: "未知角色"} · ${relTimeText(conv.updatedAt)} · ${conv.messages.size} 条",
                style = type.meta,
                color = pal.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                conv.messages.lastOrNull()?.content.orEmpty(),
                style = type.meta,
                color = pal.ink2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    if (menuOpen) {
        // 锚点＝这张卡自己（Popup 声明在卡的 Box 里），所以菜单一定贴着这张卡弹，
        // 不会再像原来那样飘到窗口左下角（用户 2026-10-03 反馈 ⑤）
        DesktopCardContextMenu(
            onDismiss = { menuOpen = false },
            width = 148.dp
        ) {
            DesktopMenuRow(
                label = "删除会话",
                danger = true,
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

/** 通栏日期刻度：等宽小字 ＋ 字距 ＋ 下缘发丝线（原型 `.cscale`）。 */
@Composable
private fun ScaleHeader(label: String) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    Text(
        label,
        style = type.tiny,
        letterSpacing = 0.08.sp,
        color = pal.ink3,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = pal.hairline2,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f
                )
            }
            .padding(start = 10.dp, end = 10.dp, top = 11.dp, bottom = 5.dp)
    )
}

@Composable
private fun RailRow(
    item: ConversationListViewModel.ConvItem,
    selected: Boolean,
    onOpen: () -> Unit
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val conv = item.conversation
    val char = item.character
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(WhaleShapes.r1)
            .background(if (selected) pal.surface2 else pal.surface)
            // 选中＝浅面 ＋ 1px accent 内描边。原来的实心主色底会和头像的浅底抢同一个含义
            // （第 4 轮评审③"一色三义"），头像的浅底从此只表示"我的气泡／封面占位"。
            .then(
                if (selected) Modifier.border(1.dp, pal.accent, WhaleShapes.r1)
                else Modifier
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarOrInitial(
            name = char?.name ?: "未",
            avatarUri = char?.avatarUri,
            modifier = Modifier.size(34.dp),
            fontSize = 16.sp,
            shape = CircleShape,
            borderColor = pal.hairline
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conv.title.ifBlank { "新会话" },
                style = type.small,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) pal.ink else pal.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // 时间已由上方的通栏刻度承担，行内不重复（原型第 4 轮评审④）
                "${char?.name ?: "未知角色"} · ${conv.messages.size} 条",
                style = type.meta,
                color = pal.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}