package com.mysticat.roleplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.ChatMessage
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEngine
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.WorldBookHit

/**
 * 世界书（E3 下半）：编辑器里的条目录入区 ＋ 聊天页的「命中可见性」面板。
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
 * （角色编辑器自台账 70 起不再内联改书，不再是它的实现方。）
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
 * 世界书编辑字段区（宝库页的独立书编辑用；角色编辑器自台账 70 起改为「世界书管理」分节，不再内联改书）。
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
        Text("世界书", style = MaterialTheme.typography.titleSmall)
        Text(
            "关键词触发的背景资料：聊天里出现（或角色卡里本来就写着）某个词，那一条才会被注入 —— " +
                "不占平时的字数。格式与 SillyTavern 的 character_book 互通，可以直接导入它的世界书文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            state.bookName,
            { state.bookName = it },
            Modifier.fillMaxWidth(),
            label = { Text("世界书名称（只用于展示）") },
            singleLine = true
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pickBook() }) { Text("导入世界书 JSON") }
            TextButton(
                onClick = {
                    val book = currentBook() ?: return@TextButton
                    saveBook(exportFileName(), WorldBookEngine.toStandaloneJson(book))
                },
                enabled = currentBook() != null
            ) { Text("导出世界书 JSON") }
            Spacer(Modifier.weight(1f))
            if (state.bookEntries.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onClear()
                        expanded = emptySet()
                    }
                ) { Text("清空") }
            }
        }
        Text(
            "导出的 JSON 与卡里那份 character_book 同一口径，可以直接给别的应用用；" +
                "导入会「整本替换」现有条目（两次导入的条目 id 会撞，合并只会越合越乱）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            state.bookDesc,
            { state.bookDesc = it },
            Modifier.fillMaxWidth(),
            label = { Text("世界书说明（可选，只用于展示）") },
            minLines = 2
        )

        OptionalNumberField(
            value = state.bookScanDepth,
            onValue = { state.bookScanDepth = it },
            label = "扫描深度（默认 4 条消息）",
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "条目 " + state.bookEntries.size + " 条",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (state.bookEntries.isNotEmpty()) {
                TextButton(
                    onClick = {
                        batchMode = !batchMode
                        selectedIds = emptySet()
                    }
                ) { Text(if (batchMode) "退出批量" else "批量管理") }
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

        Row {
            TextButton(onClick = state::addBookEntry) { Text("+ 添加条目") }
            TextButton(onClick = { showGenerate = true }) { Text("从文本生成条目") }
        }
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("删除所选条目") },
            text = { Text("删除选中的 ${selectedIds.size} 条条目？改动要保存后才会落盘。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.bookEntries = state.bookEntries.filter { it.id !in selectedIds }
                        selectedIds = emptySet()
                        confirmBatchDelete = false
                    }
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") } }
        )
    }
    if (showGenerate) {
        BulkGenerateEntriesDialog(state = state, onDismiss = { showGenerate = false })
    }
}

/**
 * 一条条目的编辑卡片。
 *
 * 主区放"几乎每条都要填"的四项（名称 / 关键词 / 内容 / 位置），次要项收进「更多设置」——
 * 全部摊开的话一条要十几行，十条条目滚都滚不完，而 ST 那些开关（正则、全词、扫描深度）
 * 绝大多数卡一辈子不会碰。
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
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 批量模式下选择框顶替启用开关：一行里塞两个勾选控件，谁也说不清哪个管哪件事
            if (batchMode) {
                Checkbox(checked = checked, onCheckedChange = { onToggleCheck() })
            } else {
                Switch(checked = entry.enabled, onCheckedChange = { v -> onChange { it.copy(enabled = v) } })
            }
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                entry.label(),
                { v -> onChange { it.copy(name = v) } },
                Modifier.weight(1f),
                label = { Text("名称（${index + 1}）") },
                singleLine = true
            )
            IconButton(onClick = { onMove(index - 1) }, enabled = !isFirst && !batchMode) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = { onMove(index + 1) }, enabled = !isLast && !batchMode) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = "下移")
            }
            IconButton(onClick = onRemove, enabled = !batchMode) {
                Icon(Icons.Filled.Delete, contentDescription = "删除该条目")
            }
        }

        OutlinedTextField(
            entry.keys.joinToString("，"),
            { v -> onChange { it.copy(keys = splitKeys(v)) } },
            Modifier.fillMaxWidth(),
            label = { Text("关键词（逗号分隔，任一个出现就注入）") },
            singleLine = true
        )
        OutlinedTextField(
            entry.content,
            { v -> onChange { it.copy(content = v) } },
            Modifier.fillMaxWidth(),
            label = { Text("内容（命中时注入的正文）") },
            minLines = 3
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val pos = chipPosition(entry.position)
            WhaleChip(
                selected = pos == WorldBookEntry.POSITION_BEFORE_CHAR,
                onClick = { onChange { it.copy(position = WorldBookEntry.POSITION_BEFORE_CHAR) } },
                label = { Text("设定前") }
            )
            Spacer(Modifier.width(6.dp))
            WhaleChip(
                selected = pos == WorldBookEntry.POSITION_AFTER_CHAR,
                onClick = { onChange { it.copy(position = WorldBookEntry.POSITION_AFTER_CHAR) } },
                label = { Text("设定后") }
            )
            Spacer(Modifier.width(6.dp))
            WhaleChip(
                selected = pos == WorldBookEntry.POSITION_AT_DEPTH,
                onClick = { onChange { it.copy(position = WorldBookEntry.POSITION_AT_DEPTH) } },
                label = { Text("插进消息里") }
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleExpand) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起更多设置" else "展开更多设置"
                )
            }
        }

        // 「永远不会触发」是最常见的白写：给了内容却没写关键词、又没勾常驻
        if (!canTrigger) {
            Text(
                if (entry.enabled) "⚠ 没有关键词又不是常驻 —— 这一条永远不会被注入"
                else "这一条已停用（不会注入）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (expanded) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                WhaleChip(
                    selected = entry.constant,
                    onClick = { onChange { it.copy(constant = !it.constant) } },
                    label = { Text("常驻（每轮都注入）") }
                )
                Spacer(Modifier.width(6.dp))
                WhaleChip(
                    selected = entry.selective,
                    onClick = { onChange { it.copy(selective = !it.selective) } },
                    label = { Text("还要命中副关键词") }
                )
            }
            OutlinedTextField(
                entry.secondaryKeys.joinToString("，"),
                { v -> onChange { it.copy(secondaryKeys = splitKeys(v)) } },
                Modifier.fillMaxWidth(),
                label = { Text("副关键词（仅上面勾选后起作用）") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = entry.insertionOrder,
                    onValue = { v -> onChange { it.copy(insertionOrder = v) } },
                    label = "插入顺序",
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = entry.depth,
                    onValue = { v -> onChange { it.copy(depth = v) } },
                    label = "倒数第几条之前",
                    modifier = Modifier.weight(1f)
                )
                OptionalNumberField(
                    value = entry.scanDepth,
                    onValue = { v -> onChange { it.copy(scanDepth = v) } },
                    label = "扫描深度",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WhaleChip(
                    selected = entry.caseSensitive,
                    onClick = { onChange { it.copy(caseSensitive = !it.caseSensitive) } },
                    label = { Text("区分大小写") }
                )
                Spacer(Modifier.width(6.dp))
                WhaleChip(
                    selected = entry.matchWholeWords == true,
                    onClick = { onChange { it.copy(matchWholeWords = it.matchWholeWords != true) } },
                    label = { Text("全词匹配") }
                )
                Spacer(Modifier.width(6.dp))
                WhaleChip(
                    selected = entry.useRegex,
                    onClick = { onChange { it.copy(useRegex = !it.useRegex) } },
                    label = { Text("关键词当正则") }
                )
            }
            Text(
                "位置选「插进消息里」时才看【倒数第几条之前】（0 = 追加到最后）；" +
                    "扫描深度留空＝跟随世界书那一档。全词匹配对中文无效（中文没有词边界），会按子串走。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 界面上的关键词一行文本 → 列表：中英文逗号与换行都当分隔符（用户会从各处粘贴进来） */
private fun splitKeys(text: String): List<String> =
    text.split(',', '，', '、', '\n').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun NumberField(value: Int, onValue: (Int) -> Unit, label: String, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            // 只收数字且限长：`insertion_order` 是个 32 位整数，用户手滑粘一串字符不该让保存炸掉
            if (s.length <= 4 && s.all { it.isDigit() }) {
                text = s
                s.toIntOrNull()?.let(onValue)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun OptionalNumberField(
    value: Int?,
    onValue: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            if (s.length <= 4 && s.all { it.isDigit() }) {
                text = s
                onValue(s.toIntOrNull())
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

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
    val hits = remember(book, messages) { WorldBookEngine.hits(book, card, messages) }
    val diagnoses = remember(book, messages) { WorldBookEngine.diagnoseMisses(book, card, messages) }
    val entries = book?.entries.orEmpty()
    val hitIds = hits.map { it.entry.id }.toSet()
    val missed = entries.filter { it.enabled && it.id !in hitIds }
    val offCount = entries.count { !it.enabled }
    val chars = hits.sumOf { it.entry.content.length }
    // 未命中与诊断默认收起（台账 68）：用户主要看"这轮命中了什么"；"为什么没触发"是排障时才要的
    var showMisses by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("世界书命中") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 先亮出是哪本书（台账 68）：命中列表挂在书的下面，别让用户猜这份资料从哪来
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
                if (hits.isEmpty()) {
                    Text(
                        if (entries.isEmpty()) "这本书还没有条目。"
                        else "这一轮没有任何条目命中 —— 最近的消息与角色卡里都没有出现它们的关键词。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                hits.forEach { h -> HitRow(h) }
                // 未命中与已停用整段默认收起（台账 68）：诊断明细（第 110 轮）保留，排障时点开看
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
                color = MaterialTheme.colorScheme.primary
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
 * 「从文本生成条目」弹窗（台账 60）：把一段设定文本拆成条目，拆法与认标题的口径全在
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
                    color = if (generated.isEmpty()) MaterialTheme.colorScheme.error
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

/** 世界书为空时不该出现「命中」入口（空书占着菜单只会让人以为功能没做） */
internal fun hasWorldBook(card: CharacterCard): Boolean =
    card.worldBook?.takeIf { !it.isEmpty() } != null

/** 便利：把书读成 [WorldBook]（界面里多处要名字/条数，统一走它避免各写判空） */
internal fun worldBookOf(card: CharacterCard): WorldBook? =
    card.worldBook?.takeIf { !it.isEmpty() }
