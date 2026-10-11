package com.mysticat.roleplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.worldBookHitsFor
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.ChatMessage
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEngine
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.WorldBookHit
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.components.WhaleFieldBlock
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.components.WhaleFieldGroupLabel
import com.mysticat.roleplay.ui.components.WhaleFieldRow
import com.mysticat.roleplay.ui.components.WhaleInlineField
import com.mysticat.roleplay.ui.components.WhaleToggleRow
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import kotlin.math.roundToInt

/**
 * 世界书：编辑器里的条目录入区 ＋ 聊天页的「命中可见性」面板。
 *
 * 字段口径、命中判定、位置与深度的装配全在 `data/WorldBook.kt` / `data/WorldBookEngine.kt`；
 * 这里只做界面，**一处都不重算**——面板一旦自己算一遍命中，"面板说命中了、模型却没收到"这类
 * 分歧就再也没人能查出来（界面上写的位置/深度必须与引擎判定的那几个值同源）。
 */

/** 位置的中文说法（编辑器 chip 与命中面板共用一份，别各写一套） */
internal fun worldBookPositionLabel(entry: WorldBookEntry): String = when (entry.position) {
    WorldBookEntry.POSITION_AT_DEPTH -> "消息内 · 倒数第 ${entry.depth} 条"
    WorldBookEntry.POSITION_AFTER_CHAR, WorldBookEntry.POSITION_AFTER_AN -> "角色设定后"
    else -> "角色设定前"
}

/** 编辑器 chip 用：ST 的"作者注前/后"（2/3）没有对应锚点，按既定的降级口径显示成设定前/后 */
private fun chipPosition(p: Int): Int = when (p) {
    WorldBookEntry.POSITION_BEFORE_AN -> WorldBookEntry.POSITION_BEFORE_CHAR
    WorldBookEntry.POSITION_AFTER_AN -> WorldBookEntry.POSITION_AFTER_CHAR
    else -> p
}

private fun safeFileName(raw: String): String =
    raw.trim().ifBlank { "世界书" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")

/** 宝库页独立导出的文件名：书是账号级实体，没有角色名可拼，就书名一个来源 */
internal fun standaloneBookFileName(bookName: String): String =
    safeFileName(bookName.trim().ifBlank { "世界书" }) + ".json"

/**
 * 世界书编辑态的最小接口：**宝库页的独立书编辑**用这份界面件（[WorldBookEditFields]）。
 * 实现方只需提供这些可变状态；何时落盘、落到哪由调用方自己决定。
 * （角色编辑器已不再内联改书，不再是它的实现方。）
 */
interface WorldBookEditState {
    var bookName: String
    var bookDesc: String
    /** 书级默认扫描深度；`null` = 跟随引擎默认（4 条消息） */
    var bookScanDepth: Int?
    var bookEntries: List<WorldBookEntry>
    fun addBookEntry()
    fun removeBookEntry(index: Int)
    fun moveBookEntry(from: Int, to: Int)
    fun updateBookEntry(index: Int, transform: (WorldBookEntry) -> WorldBookEntry)
}

/**
 * 分组跳转抓手（编辑页那排胶囊用）：`jumper.offsetOf("entries")` 拿到内容偏移，
 * `animateScrollTo` 滚过去，那一组就进可视区。
 *
 * ⚠ 2026-10-02 用户反馈「双端点『条目』都不跳转，要先点『使用角色』再点『条目』才生效」。
 * 原来的 `BringIntoViewRequester` 把"目标此刻算不算已可见"交给了滚动容器自己判断，
 * 锚点落在视口下沿之外时它判不必滚、请求空转；而先跳到最末的「使用角色」把整页推到底之后
 * 同一个请求又能触发——正是这个症状的形状。所以改成自己量、自己滚。
 *
 * ## 零点为什么是内容首元素，而不是滚动容器
 *
 * 第一版拿"滚动容器的 `positionInRoot()`"当零点，实测**那个值会跟着滚动一起变**
 * （scroll=1174 时量到 −381 ＝ 793−1174，容器明明没动）。原因在 modifier 链的挂点：
 * `verticalScroll(...).onGloballyPositioned{}` 的回调落在**随内容移动的那个节点**上，不是视口。
 * 于是换算式 `内容偏移 = 锚点y − 容器顶 + 滚动量` 里两项一正一负抵消，算出来的目标
 * 永远等于当前滚动量——点哪儿都不动（点「基本信息」看着像"跳到最底"就是这个）。
 *
 * 改成**零点也取自内容里**（滚动内容第一个元素，key = `top`），两边一起做差：
 * `内容偏移 = (锚点y − 记它时的滚动量) − (零点y − 记零点的滚动量)`。
 * 括号里那两项**与滚动无关**（滚动多少它们就同减多少），所以什么时候记的都准，
 * 也不需要任何"容器在哪"的外部信息。
 */
class BookSectionJumper {
    /** key →（锚点在窗口里的 y，摆位那一刻；那一刻的滚动量） */
    private val marks = mutableMapOf<String, Pair<Int, Int>>()

    /** 锚点修饰符工厂：挂到各组自己那一段上 */
    @Composable
    internal fun anchor(key: String, scroll: ScrollState): Modifier =
        Modifier.onGloballyPositioned { coords ->
            marks[key] = coords.positionInRoot().y.roundToInt() to scroll.value
        }

    /**
     * 该组在**滚动内容**里的 y（没量到／零点还没量＝null，跳转按不动处理，不猜）。
     * 量的是相对内容首元素的距离，比实际位置小一个滚动容器上内边距（8dp），肉眼无差。
     */
    fun offsetOf(key: String): Int? {
        val zero = marks[TOP] ?: return null
        val (rawY, scrollAtMark) = marks[key] ?: return null
        return (rawY - scrollAtMark) - (zero.first - zero.second)
    }

    companion object {
        /** 零点：滚动内容第一个元素（编辑页在滚动容器里塞一个 0 高的 [Spacer] 顶上它） */
        const val TOP = "top"

        /** 条目逐条锚点的键：桌面左栏的条目清单按它跳到右栏那一张卡（与 `anchor("entries")` 同口径） */
        fun entryKey(index: Int): String = "entry:$index"
    }
}

/**
 * 世界书编辑字段区（宝库页的独立书编辑用；角色编辑器已改为「世界书管理」分节，不再内联改书）。
 *
 * 导入：三种文件都吃（整张卡 / 平铺卡 / 书本身），解析口径在引擎里，这里只报"没有书"这一类失败。
 */
@Composable
fun WorldBookEditFields(
    state: WorldBookEditState,
    currentBook: () -> WorldBook?,
    onImportBook: (WorldBook) -> Unit,
    onClear: () -> Unit,
    exportFileName: () -> String,
    /**
     * 分组锚点修饰符工厂：`anchor("entries")` 挂到各组自己那一段上（口径见 [BookSectionJumper]）。
     * 滚动状态由调用方持有，所以工厂也由调用方给——本组件不知道容器在哪。
     */
    anchor: @Composable (String) -> Modifier,
    modifier: Modifier = Modifier
) {
    val pickBook = rememberJsonFilePicker { text ->
        if (text == null) return@rememberJsonFilePicker
        val book = WorldBookEngine.fromStandaloneJson(text)
        if (book == null) {
            showToast("这个文件里没有世界书", long = true)
        } else {
            onImportBook(book)
            showToast("已导入世界书「${book.name.ifBlank { "未命名" }}」${book.entries.size} 条")
        }
    }
    val saveBook = Platform.ui.rememberJsonFileSaver { ok ->
        showToast(if (ok) "世界书已导出" else "导出失败")
    }
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Int>()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // v10 行表式（双端同一份，原型 §三）：分组标题在卡外，卡内＝名称／说明／扫描深度三块
        // ＋ 导入导出、清空两行。此前两端各一副骨架（桌面 WhalePanelCard ＋ 手机 M3 浮起标签），
        // 两副都不属于行表式，用户 2026-10-08 点名要统一。
        WhaleFieldGroup(label = "基本信息", modifier = Modifier.then(anchor("basic"))) {
            WhaleFieldBlock(
                label = "世界书名称（仅用于展示）",
                value = state.bookName,
                onValueChange = { state.bookName = it }
            )
            WhaleFieldBlock(
                label = "世界书说明（可选，仅用于展示）",
                value = state.bookDesc,
                onValueChange = { state.bookDesc = it },
                minLines = 2
            )
            WhaleFieldBlock(
                label = "扫描深度",
                value = state.bookScanDepth?.toString().orEmpty(),
                onValueChange = { s ->
                    // 只收数字且限长：手滑粘一串字符不该让保存炸掉（与条目那一档同一口径）
                    if (s.length <= 4 && s.all { it.isDigit() }) state.bookScanDepth = s.toIntOrNull()
                },
                placeholder = "默认 4 条消息",
                hint = "注入时从最新一条往前扫几条消息；留空＝默认 4 条。",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            WhaleFieldRow(
                title = "导入 / 导出",
                desc = "导入会「整本替换」现有条目；格式与 SillyTavern 的世界书互通",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickBook() }) { Text("导入") }
                        TextButton(
                            onClick = {
                                val book = currentBook() ?: return@TextButton
                                saveBook(exportFileName(), WorldBookEngine.toStandaloneJson(book))
                            },
                            enabled = currentBook() != null
                        ) { Text("导出") }
                    }
                },
                divider = state.bookEntries.isNotEmpty()
            )
            // 清空只在这本书有条目时才有意义（空书的按钮点了也不会有变化）
            if (state.bookEntries.isNotEmpty()) {
                WhaleFieldRow(
                    title = "清空条目",
                    desc = "删掉这本书里的全部条目（保存后生效）",
                    trailing = {
                        TextButton(onClick = { onClear(); expanded = emptySet() }) {
                            Text("清空", color = LocalWhalePalette.current.dangerText)
                        }
                    },
                    divider = false
                )
            }
        }

        // 分节标题在卡外。旧口径是「桌面等宽小字／手机 titleSmall」，用户 2026-10-08 否掉：
        // 它必须与角色卡的分组标题（bodyMedium/600 黑）同一份取值 —— 所以两端都走
        // WhaleFieldGroupLabel（＝ WhaleFieldGroup 的标题本体），页面里不再有两种"分节"。
        // 锚点也从旧的那条分隔线挪到标题行：跳「条目」该落在标题上，不是标题上面一条线。
        Row(Modifier.then(anchor("entries")), verticalAlignment = Alignment.CenterVertically) {
            WhaleFieldGroupLabel("条目 ${state.bookEntries.size} 条", Modifier.weight(1f))
            if (state.bookEntries.isNotEmpty()) {
                TextButton(onClick = {
                    batchMode = !batchMode
                    selectedIds = emptySet()
                }) { Text(if (batchMode) "退出批量" else "批量管理") }
            }
        }

        if (batchMode && state.bookEntries.isNotEmpty()) {
            val allSelected = selectedIds.size == state.bookEntries.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        selectedIds = if (allSelected) emptySet() else state.bookEntries.map { it.id }.toSet()
                    }
                ) { Text(if (allSelected) "全不选" else "全选") }
                TextButton(
                    onClick = {
                        state.bookEntries =
                            state.bookEntries.map { if (it.id in selectedIds) it.copy(enabled = true) else it }
                    },
                    enabled = selectedIds.isNotEmpty()
                ) { Text("启用所选") }
                TextButton(
                    onClick = {
                        state.bookEntries =
                            state.bookEntries.map { if (it.id in selectedIds) it.copy(enabled = false) else it }
                    },
                    enabled = selectedIds.isNotEmpty()
                ) { Text("停用所选") }
                TextButton(
                    onClick = { confirmBatchDelete = true },
                    enabled = selectedIds.isNotEmpty()
                ) { Text("删除所选") }
            }
        }

        state.bookEntries.forEachIndexed { i, e ->
            // 桌面左栏的条目清单点某一条要能跳到这里（锚点口径见 [BookSectionJumper]）
            Box(Modifier.then(anchor(BookSectionJumper.entryKey(i)))) {
                BookEntryCard(
                    index = i,
                    entry = e,
                    isFirst = i == 0,
                    isLast = i == state.bookEntries.lastIndex,
                    canTrigger = e.canTrigger(),
                    batchMode = batchMode,
                    checked = e.id in selectedIds,
                    onToggleCheck = {
                        selectedIds = if (e.id in selectedIds) selectedIds - e.id else selectedIds + e.id
                    },
                    expanded = e.id in expanded,
                    onToggleExpand = {
                        expanded = if (e.id in expanded) expanded - e.id else expanded + e.id
                    },
                    onChange = { f -> state.updateBookEntry(i) { f(it) } },
                    onRemove = { state.removeBookEntry(i) },
                    onMove = { to -> state.moveBookEntry(i, to) }
                )
            }
        }

        Row {
            TextButton(onClick = state::addBookEntry) { Text("+ 添加条目") }
            TextButton(onClick = { showGenerate = true }) { Text("从文本生成条目") }
        }
    }

    if (confirmBatchDelete) {
        // 236 弹窗统一：删除类残留收编共享确认壳（手机底部浮层／桌面居中面板，与 241 同族）
        WhaleConfirmSheet(
            title = "删除所选条目",
            body = "删除选中的 ${selectedIds.size} 条条目？改动要保存后才会落盘。",
            confirmLabel = "删除",
            onConfirm = {
                state.bookEntries = state.bookEntries.filter { it.id !in selectedIds }
                selectedIds = emptySet()
                confirmBatchDelete = false
            },
            onDismiss = { confirmBatchDelete = false }
        )
    }
    if (showGenerate) {
        BulkGenerateEntriesDialog(state = state, onDismiss = { showGenerate = false })
    }
}

/**
 * 一条条目的编辑卡片（v10 行表式：**双端同一份骨架**）。
 *
 * 卡外不再挂「条目」抬头（列表页的「条目 N 条」已经是这一组的分节），卡内自上而下：
 * ① 名称行——名字黑字（点它就地改名、不弹窗不跳页）＋ 一句副信息（关键词与常驻状态）；
 * ② 关键词／内容——文本字段按 §三（标签在框上方、框线 `fieldLine`）；
 * ③ 位置（三选）与 匹配（五布尔）——≥2 档一律折成开关行（禁用清单 3）；
 * ④「更多设置」——只留插入顺序／倒数第几条／扫描深度与长解释（禁用清单 6：长解释不默认展开）。
 */
@Composable
private fun BookEntryCard(
    index: Int,
    entry: WorldBookEntry,
    isFirst: Boolean,
    isLast: Boolean,
    canTrigger: Boolean,
    batchMode: Boolean,
    checked: Boolean,
    onToggleCheck: () -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onChange: ((WorldBookEntry) -> WorldBookEntry) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit
) {
    WhaleFieldGroup {
        // ── ① 名称行：名字可改、副信息一句；右侧排序/删除 ＋ 启用开关（批量模式下换勾选框）──
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    WhaleInlineField(
                        value = entry.label(),
                        onValueChange = { v -> onChange { it.copy(name = v) } },
                        placeholder = "条目 ${index + 1}",
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        entryTriggerSummary(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                EntryCardIconButton(Icons.Filled.ArrowUpward, "上移", index - 1, !isFirst && !batchMode, onMove)
                EntryCardIconButton(Icons.Filled.ArrowDownward, "下移", index + 1, !isLast && !batchMode, onMove)
                EntryCardIconButton(Icons.Filled.Delete, "删除该条目", 0, !batchMode) { onRemove() }
                Spacer(Modifier.width(4.dp))
                // 批量模式下选择框顶替启用开关：一行里塞两个勾选控件，谁也说不清哪个管哪件事
                if (batchMode) {
                    Checkbox(checked = checked, onCheckedChange = { onToggleCheck() })
                } else {
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { v -> onChange { it.copy(enabled = v) } },
                        modifier = Modifier.scale(0.72f)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // 触发不了的红字：最常见的白写——给了内容却没写关键词、又没勾常驻
        if (!canTrigger) {
            Text(
                if (entry.enabled) "⚠ 没有关键词又不是常驻 —— 这一条永远不会被注入"
                else "这一条已停用（不会注入）",
                style = MaterialTheme.typography.bodySmall,
                color = LocalWhalePalette.current.dangerText,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // ── ② 文本字段（关键词与内容正文保持整块输入区，只按 §三 的标签与框线规则对齐）──
        WhaleFieldBlock(
            label = "关键词",
            value = entry.keys.joinToString("，"),
            onValueChange = { v -> onChange { it.copy(keys = splitKeys(v)) } },
            placeholder = "任一个出现就注入",
            hint = "中英文逗号 / 顿号 / 换行分隔"
        )
        WhaleFieldBlock(
            label = "内容",
            value = entry.content,
            onValueChange = { v -> onChange { it.copy(content = v) } },
            minLines = 3,
            hint = "命中时注入的正文"
        )

        // ── ③ 位置三选：说明写"这一条现在插在哪"（与命中面板同一份口径，不在这里重算）──
        WhaleToggleRow(
            title = "位置",
            desc = worldBookPositionLabel(entry),
            options = entryPositionOptions,
            isSelected = { it == positionOptionOf(entry.position) },
            onToggle = { label -> onChange { it.copy(position = positionOfOption(label)) } }
        )

        // ── ③ 匹配五布尔：常驻与副关键词原本各占一行，折进来后"这一块有哪些开关"一眼可见 ──
        WhaleToggleRow(
            title = "匹配",
            desc = "副关键词、大小写、全词、正则",
            options = entryMatchOptions,
            isSelected = { label -> entryMatchFlag(entry, label) },
            onToggle = { label -> onChange { e -> e.withMatchFlagToggled(label) } },
            divider = entry.selective || expanded
        )

        // 副关键词只在「还要命中副关键词」勾上后出现——不勾时它是死字段，不占地方
        if (entry.selective) {
            WhaleFieldBlock(
                label = "副关键词",
                value = entry.secondaryKeys.joinToString("，"),
                onValueChange = { v -> onChange { it.copy(secondaryKeys = splitKeys(v)) } },
                hint = "主关键词与副关键词都命中才注入",
                divider = expanded
            )
        }

        // ── ④ 更多设置：数字项与长解释（默认不展开，禁用清单 6）──
        WhaleFieldRow(
            title = "更多设置",
            desc = "插入顺序、倒数第几条、扫描深度",
            onClick = onToggleExpand,
            trailing = {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起更多设置" else "展开更多设置",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            divider = expanded
        )
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberBlock(
                    value = entry.insertionOrder,
                    onValue = { v -> onChange { it.copy(insertionOrder = v) } },
                    label = "插入顺序",
                    modifier = Modifier.weight(1f),
                    divider = false
                )
                NumberBlock(
                    value = entry.depth,
                    onValue = { v -> onChange { it.copy(depth = v) } },
                    label = "倒数第几条",
                    modifier = Modifier.weight(1f),
                    divider = false
                )
                OptionalNumberBlock(
                    value = entry.scanDepth,
                    onValue = { v -> onChange { it.copy(scanDepth = v) } },
                    label = "扫描深度",
                    modifier = Modifier.weight(1f),
                    divider = false
                )
            }
            Text(
                "位置选「插进消息里」时才看【倒数第几条之前】（0 = 追加到最后）；" +
                    "扫描深度留空＝跟随世界书那一档。全词匹配对中文无效（中文没有词边界），会按子串走。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 13.dp)
            )
        }
    }
}

/** 位置三选的选项文案（与原 chips 一字不差，改文案要连场景锚点一起想） */
private val entryPositionOptions = listOf("设定前", "设定后", "插进消息里")

/** 匹配的五个布尔（同上） */
private val entryMatchOptions = listOf("常驻", "还要命中副关键词", "区分大小写", "全词匹配", "关键词当正则")

/** 条目现在落在哪一档（走 [chipPosition] 的降级口径：ST 的作者注前/后显示成设定前/后） */
private fun positionOptionOf(p: Int): String = when (chipPosition(p)) {
    WorldBookEntry.POSITION_AFTER_CHAR -> "设定后"
    WorldBookEntry.POSITION_AT_DEPTH -> "插进消息里"
    else -> "设定前"
}

private fun positionOfOption(label: String): Int = when (label) {
    "设定后" -> WorldBookEntry.POSITION_AFTER_CHAR
    "插进消息里" -> WorldBookEntry.POSITION_AT_DEPTH
    else -> WorldBookEntry.POSITION_BEFORE_CHAR
}

private fun entryMatchFlag(entry: WorldBookEntry, label: String): Boolean = when (label) {
    "常驻" -> entry.constant
    "还要命中副关键词" -> entry.selective
    "区分大小写" -> entry.caseSensitive
    "全词匹配" -> entry.matchWholeWords == true
    else -> entry.useRegex
}

private fun WorldBookEntry.withMatchFlagToggled(label: String): WorldBookEntry = when (label) {
    "常驻" -> copy(constant = !constant)
    "还要命中副关键词" -> copy(selective = !selective)
    "区分大小写" -> copy(caseSensitive = !caseSensitive)
    "全词匹配" -> copy(matchWholeWords = matchWholeWords != true)
    else -> copy(useRegex = !useRegex)
}

/** 名字下面那句副信息：关键词与常驻状态（原型 §四 的"不用标签，用一句话"） */
private fun entryTriggerSummary(entry: WorldBookEntry): String {
    val keys = if (entry.keys.isEmpty()) "无关键词" else entry.keys.joinToString(" / ")
    return "关键词 · " + keys + "　常驻 · " + (if (entry.constant) "是" else "否")
}

/**
 * 数字块（v10 块形态）：只收数字且限长——`insertion_order` 是 32 位整数，
 * 用户手滑粘一串字符不该让保存炸掉。
 */
@Composable
private fun NumberBlock(
    value: Int,
    onValue: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    divider: Boolean = true
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    WhaleFieldBlock(
        label = label,
        value = text,
        onValueChange = { s ->
            if (s.length <= 4 && s.all { it.isDigit() }) {
                text = s
                s.toIntOrNull()?.let(onValue)
            }
        },
        modifier = modifier,
        divider = divider
    )
}

/** 可空数字块：留空＝跟随默认（扫描深度留空跟随世界书那一档） */
@Composable
private fun OptionalNumberBlock(
    value: Int?,
    onValue: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    divider: Boolean = true
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    WhaleFieldBlock(
        label = label,
        value = text,
        onValueChange = { s ->
            if (s.length <= 4 && s.all { it.isDigit() }) {
                text = s
                onValue(s.toIntOrNull())
            }
        },
        placeholder = "跟随世界书",
        modifier = modifier,
        divider = divider
    )
}

/** 条目卡右上角那枚小图标钮（原型的 ↑ ↓ 🗑）：24dp 见方，比 M3 的 48dp 图标钮小一半 */
@Composable
private fun EntryCardIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    arg: Int,
    enabled: Boolean,
    onMove: (Int) -> Unit
) {
    IconButton(onClick = { onMove(arg) }, enabled = enabled, modifier = Modifier.size(24.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 界面上的关键词一行文本 → 列表：中英文逗号与换行都当分隔符（用户会从各处粘贴进来） */
private fun splitKeys(text: String): List<String> =
    text.split(',', '，', '、', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * 聊天页的「命中可见性」面板：**这一轮注入了哪些条目、被哪个词触发的**。
 *
 * 为什么要有它：世界书是"看不见的上下文"——用户多花了字数却不知道花在哪，作者写了条目不触发
 * 也不知道为什么。BYOK 应用的记账本就该摊开给用户看。未命中与已停用的条目也列出来，
 * 因为"我以为它会触发"正是最需要被回答的那类问题。
 *
 * 口径：与 `AiClient` 发送时**同一个纯函数**（`WorldBookEngine.hits`），只是拿当前聊天记录重算，
 * 所以它是"此刻再发一条会注入什么"，不是"上一条实际注入了什么"。
 */
@Composable
fun WorldBookHitsDialog(card: CharacterCard, messages: List<ChatMessage>, onDismiss: () -> Unit) {
    val book = card.worldBook
    // 与装配器同一道门、同一个函数（见 AiClient.worldBookHitsFor）：形态不注入世界书时（工具形态），
    // 面板也必须算成"本轮什么都没注入"——否则它会显示一堆"命中"，而实际请求里一个字都没有
    val enabled = Engines.of(card).worldBook
    val hits = remember(card, messages, enabled) { AiClient.worldBookHitsFor(card, messages) }
    // 没注入就不谈"为什么没触发"：那一节回答的是注入侧的问题，此时它整节都是伪命题
    val diagnoses = remember(card, messages, enabled) {
        if (enabled) WorldBookEngine.diagnoseMisses(book, card, messages) else emptyList()
    }
    val entries = book?.entries.orEmpty()
    val hitIds = hits.map { it.entry.id }.toSet()
    val missed = entries.filter { it.enabled && it.id !in hitIds }
    val offCount = entries.count { !it.enabled }
    val chars = hits.sumOf { it.entry.content.length }
    // 关键词覆盖检查（启发式，见 WorldBookEngine.coverageGaps）：书里没有、模型却在反复用的名字。
    // 不注入的形态（工具）不算：那种卡连段头都不发，报了也无所指
    val gaps = remember(card, messages, enabled) {
        if (enabled) WorldBookEngine.coverageGaps(book, card, messages) else emptyList()
    }
    // 未命中与诊断默认收起：用户主要看"这轮命中了什么"；"为什么没触发"是排障时才要的
    var showMisses by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("世界书命中") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 先亮出是哪本书：命中列表挂在书的下面，别让用户猜这份资料从哪来
                Text(
                    "世界书「" + (book?.name?.ifBlank { "未命名" } ?: "未命名") + "」",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "按当前聊天记录重算：此刻再发一条，下面这些条目会被注入。" +
                        "共 ${entries.size} 条 · 命中 ${hits.size} 条 · 正文约 $chars 字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                if (!enabled) {
                    // 空的原因先于"命中与否"说清楚：这个形态根本不发世界书，
                    // 让它去找"哪个关键词没出现"是白费功夫（旧文案正是这么误导的）
                    Text(
                        "这个形态不注入世界书，所以这里恒为空 —— 不是关键词没出现。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (hits.isEmpty()) {
                    Text(
                        if (entries.isEmpty()) "这本书还没有条目。"
                        else "这一轮没有任何条目命中 —— 最近的消息与角色卡里都没有出现它们的关键词。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                hits.forEach { h -> HitRow(h) }
                // 覆盖检查：回答的是第三类问题——"它写了什么、我书里没有的东西"。
                // 排在命中与未命中之间：命中是记账、未命中是排障，这一节是**止损**（趁没被记忆固化）。
                // 正文抽去 CoverageGapsBody：无书卡看不到这个面板，那一侧走 CoverageGapsDialog
                if (gaps.isNotEmpty()) {
                    HorizontalDivider()
                    CoverageGapsBody(gaps)
                }
                // 未命中与已停用整段默认收起：诊断明细保留，排障时点开看
                if (diagnoses.isNotEmpty() || offCount > 0) {
                    HorizontalDivider()
                    TextButton(onClick = { showMisses = !showMisses }) {
                        Text(
                            buildString {
                                if (showMisses) {
                                    append("收起未命中（${missed.size} 条）")
                                } else {
                                    append("未命中 ${missed.size} 条")
                                    if (offCount > 0) append(" · 已停用 $offCount 条")
                                    append(" ▸ 点开看原因")
                                }
                            }
                        )
                    }
                    if (showMisses) {
                        // 逐条说清"为什么没触发"：能查出毛病的（正则写错/大小写挡住/近似键…）给出原因，
                        // 查不出的给通用解释 —— 词没出现本身就是答案
                        diagnoses.forEach { d ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    d.entry.label().ifBlank { "未命名条目" },
                                    style = MaterialTheme.typography.labelLarge
                                )
                                if (d.reasons.isEmpty()) {
                                    Text(
                                        "最近的消息与角色卡里没有出现它的关键词（${d.entry.keys.joinToString("、")}）。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    d.reasons.forEach { reason ->
                                        Text(
                                            "· $reason",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                        }
                        if (offCount > 0) {
                            Text(
                                "已停用 $offCount 条（不参与注入）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/**
 * 覆盖检查的正文：命中面板里那一节与独立入口 [CoverageGapsDialog] **共用这一份**。
 *
 * 两个入口是同一件事的两个去处（书非空时它在命中面板里，无书时只剩独立入口），
 * 列表、警告、建议各写一遍必然漂移 —— 所以都只住这里。
 */
@Composable
private fun CoverageGapsBody(gaps: List<WorldBookEngine.NameGap>) {
    Text(
        "最近几轮里出现、但这个角色和这本书里都没有的名字（自动挑的，可能会看错）：",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        gaps.joinToString("、") { "${it.name}（${it.count} 次）" },
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "若其中哪个是它自己起的：补一条条目，或在「角色记忆」里把它记成「待确认」，" +
            "别让它顺手变成既有设定。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 「关键词覆盖检查」的独立入口：**无世界书的卡也看得到**（台账 103）。
 *
 * 为什么单独开一个：这一节原先只住在 [WorldBookHitsDialog] 里，而那个入口要求书非空 ——
 * 无书卡（恰恰是最容易现编的情形，见 `WorldBookEngine.coverageGaps`）反而看不到它。
 * 所以入口的门只看形态（[hasCoverageCheck]），与这本书有没有内容无关。
 */
@Composable
fun CoverageGapsDialog(card: CharacterCard, messages: List<ChatMessage>, onDismiss: () -> Unit) {
    // 与命中面板同一道门、同一个函数（口径见那里的注释）：形态不注入世界书时这一项**不适用**，
    // 不能说"没发现"——那是"查过、干净"的意思，而这里根本没查
    val enabled = Engines.of(card).worldBook
    val gaps = remember(card, messages, enabled) {
        if (enabled) WorldBookEngine.coverageGaps(card.worldBook, card, messages) else emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关键词覆盖检查") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "查的是：最近 ${WorldBookEngine.COVERAGE_WINDOW} 条回复里反复出现、" +
                        "而角色卡和世界书里都没有的名字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                when {
                    !enabled -> Text(
                        "这个形态不注入世界书，所以这一项对它不适用。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    gaps.isEmpty() -> Text(
                        "没挑出这样的名字 —— 最近几轮里没有反复出现、又不在卡和书里的候选。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> CoverageGapsBody(gaps)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun HitRow(h: WorldBookHit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                h.entry.label().ifBlank { "未命名条目" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (h.isConstant) "常驻" else "命中「${h.matchedKey}」",
                style = MaterialTheme.typography.labelSmall,
                color = LocalWhalePalette.current.accentText
            )
            Spacer(Modifier.width(6.dp))
            Text(
                worldBookPositionLabel(h.entry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            h.entry.content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 「从文本生成条目」弹窗：把一段设定文本拆成条目，拆法与认标题的口径全在
 * `WorldBookEngine.entriesFromText`，这里只收集输入与展示预览。
 */
@Composable
private fun BulkGenerateEntriesDialog(state: WorldBookEditState, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(WorldBookEngine.WorldBookSplitMode.PARAGRAPH) }
    var titleAsKey by remember { mutableStateOf(true) }
    val generated = remember(text, mode, titleAsKey) {
        WorldBookEngine.entriesFromText(text, mode, titleAsKey, startId = 0)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从文本生成条目") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "把设定文本粘进来，自动拆成一条条资料。段首的【标题】、# 标题、「标题：」会被认出来" +
                        "当条目名；正文为空的段不会生成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    text,
                    { text = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("设定文本") },
                    minLines = 6
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WorldBookEngine.WorldBookSplitMode.entries.forEach { m ->
                        WhaleChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = titleAsKey, onCheckedChange = { titleAsKey = it })
                    Text("把标题作为关键词", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (generated.isEmpty()) "没有可用的分段。"
                    else "将生成 ${generated.size} 条条目，追加到现有条目后面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (generated.isEmpty()) LocalWhalePalette.current.dangerText
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val nextId = (state.bookEntries.maxOfOrNull { it.id } ?: -1) + 1
                    state.bookEntries =
                        state.bookEntries + WorldBookEngine.entriesFromText(text, mode, titleAsKey, nextId)
                    onDismiss()
                },
                enabled = generated.isNotEmpty()
            ) { Text(if (generated.isEmpty()) "生成" else "生成 ${generated.size} 条") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 世界书为空时不该出现「命中」入口（空书占着菜单只会让人以为功能没做）。
 * 形态不注入世界书时（工具，见 `EngineSpec.worldBook`）**同样不给入口**：那种卡上挂的书
 * 一个字都不会发出去，点进去只会看到"一条都没命中"，而真实原因是这个形态根本不发世界书
 * —— 那是误导，不是信息。判据与装配器同一个（`Engines.of(card)`），不在这里另写一份。
 */
internal fun hasWorldBook(card: CharacterCard): Boolean =
    Engines.of(card).worldBook && card.worldBook?.takeIf { !it.isEmpty() } != null

/**
 * 覆盖检查入口的门：**只看形态，不看这本书有没有内容**。
 *
 * 与 [hasWorldBook] 的这一处差别正是要点：书是空的时候，"它现编了什么名字"才最容易发生，
 * 而「世界书命中」那块面板此时整个都不给 —— 所以这一项得自己站住（入口见聊天页的「更多」）。
 */
fun hasCoverageCheck(card: CharacterCard): Boolean = Engines.of(card).worldBook

/** 便利：把书读成 [WorldBook]（界面里多处要名字/条数，统一走它避免各写判空） */
internal fun worldBookOf(card: CharacterCard): WorldBook? =
    card.worldBook?.takeIf { !it.isEmpty() }
