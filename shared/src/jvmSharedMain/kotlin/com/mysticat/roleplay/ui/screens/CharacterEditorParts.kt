package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.WorldBookFile
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.AiActionButton
import com.mysticat.roleplay.ui.writeFailureToast
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.CrashNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面第三栏的窄头部：整页顶栏那三个动作——返回、导出、保存——压成一行。
 *
 * 脏标记用文字明说（整页顶栏是靠"保存图标一直可点"暗示的），因为第三栏里没有别的地方
 * 能看出这张卡改没改过；保存按钮也就跟着脏标记走（同「模型与 API」内嵌形态的口径）。
 */
@Composable
internal fun EditorPaneHeader(
    title: String,
    dirty: Boolean,
    saving: Boolean,
    canExport: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            if (dirty) "有未保存的修改" else "已是最新",
            style = MaterialTheme.typography.labelSmall,
            color = if (dirty) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onExport, enabled = canExport) {
            Icon(Icons.Filled.Share, contentDescription = "导出角色卡")
        }
        Button(onClick = onSave, enabled = !saving && dirty) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("保存")
            }
        }
    }
}

/**
 * 「世界书管理」分节：一个状态行（书名 · 条目数 ＋ 启用开关）＋ 选已有书 / 编辑 / 新建 三个动作。
 * 书内容编辑与"别的卡挂不挂这本书"都在宝库的世界书段——书文件是唯一事实来源，
 * 编辑器这里不再持内联编辑副本（第 99～110 轮的整块内联编辑已随本条撤下）。
 * "选已有书"（2026-09-25 用户反馈）：宝库书列表弹窗，选哪本挂哪本——编辑器此前只有
 * 启用 / 编辑 / 新建，没挂书的卡（或书在宝库被摘掉后）在这里无路可走。
 */
@Composable
internal fun WorldBookManageSection(vm: CharacterEditorViewModel, onOpenWorldBook: (String?) -> Unit) {
    var showBookPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("世界书", style = MaterialTheme.typography.titleSmall)
        Text(
            "关键词触发的背景资料库，账号级共享。这里管本卡用不用、用哪本；" +
                "写内容、给别的卡挂书，都在「宝库 → 世界书」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val attached = vm.bookId != null
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (attached) {
                    val (bookName, entryCount) = vm.bookSummary ?: ("" to 0)
                    Text(
                        "世界书「${bookName.ifBlank { "未命名" }}」· $entryCount 条",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text("未挂世界书", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Switch(
                checked = attached,
                // 没挂着、页面里也没刚摘下的，就没有可"启用"的东西——选一本或点新建
                enabled = attached || vm.canReenableBook,
                onCheckedChange = { on ->
                    CrashNote.note("编辑器 世界书启用=$on")
                    vm.setWorldBookEnabled(on, writeFailureToast(null, "更新世界书引用"))
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { CrashNote.note("编辑器 世界书选已有"); showBookPicker = true }) { Text("选已有书") }
            TextButton(
                onClick = { CrashNote.note("编辑器 世界书编辑"); onOpenWorldBook(vm.bookId) },
                enabled = attached
            ) { Text("编辑") }
            TextButton(onClick = {
                CrashNote.note("编辑器 世界书新建")
                vm.createNewBook(onReady = { onOpenWorldBook(it) }, onFailure = writeFailureToast(null, "新建世界书"))
            }) { Text("新建") }
        }
    }
    if (showBookPicker) WorldBookPickerDialog(vm) { showBookPicker = false }
}

/**
 * 宝库已有书选择弹窗（读盘走 IO 线程，写法同 [CharacterPickerDialog] 的角色选择框）：
 * 点一行即挂上那本书（已挂着的会替换），显示书名与条目数，当前挂着的标"当前"。
 * 搜索（2026-09-25 用户反馈）按 书名 / 说明 / 条目关键词与条目名 过滤——书没有分类字段，
 * "按主题找"就落在关键词检索上（真要书级分类得动数据模型，先不做）。
 */
@Composable
internal fun WorldBookPickerDialog(vm: CharacterEditorViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val loaded by produceState(initialValue = null as List<WorldBookFile>?) {
        value = withContext(Dispatchers.IO) { Repository.listWorldBooks() }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择世界书") },
        text = {
            val books = loaded
            if (books == null) {
                Text("正在读取世界书…")
            } else if (books.isEmpty()) {
                Text("宝库还没有世界书。点「新建」建一本，或去「宝库 → 世界书」导入。")
            } else {
                val q = query.trim()
                val filtered = if (q.isEmpty()) books else books.filter { f ->
                    f.book.name.contains(q, true) ||
                        f.book.description.contains(q, true) ||
                        f.book.entries.any { e ->
                            e.keys.any { it.contains(q, true) } ||
                                e.secondaryKeys.any { it.contains(q, true) } ||
                                e.comment.contains(q, true) ||
                                e.name.contains(q, true)
                        }
                }
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索书名或条目关键词…") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            "没有匹配「$q」的世界书",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    } else {
                        LazyColumn(Modifier.padding(top = 4.dp).heightIn(max = 360.dp)) {
                            items(filtered, key = { it.id }) { f ->
                                val current = f.id == vm.bookId
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            onDismiss()
                                            CrashNote.note("编辑器 世界书挂已有 id=${f.id}")
                                            vm.attachExistingBook(f.id, writeFailureToast(null, "挂载世界书"))
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        f.book.name.ifBlank { "未命名" } + if (current) "（当前）" else "",
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${f.book.entries.size} 条",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 一个"端"的默认聊天背景槽位（A 批次，用户 2026-09-18）。
 *
 * 手机端与桌面端各一个，UI 完全相同（各自的预览、本地上传 / AI 生成 / 参考图、移除），
 * 差别只在数据落到哪个字段与裁剪框用哪个比例——所以抽成一个组件，别写两份。
 *
 * @param inherited 这一端没单独设过、正用着另一端那张。要说出来（见调用处注释），
 *   否则"点进来看到的图"和"这个槽位到底存了什么"对不上。
 */
@Composable
internal fun BackgroundSlotEditor(
    target: BgTarget,
    uri: String?,
    inherited: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onPickLocal: () -> Unit,
    onGenerate: () -> Unit,
    onPickReference: () -> Unit,
    onRemove: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${target.label}聊天背景", style = MaterialTheme.typography.titleSmall)
            if (target == BgTarget.current) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "本机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (inherited) {
            Spacer(Modifier.height(2.dp))
            Text(
                "未单独设置，正在用手机端那张",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = imageModel(uri),
                contentDescription = "${target.label}背景预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // 桌面端是横图、手机端是竖图，预览框也按比例给，别把横图塞进竖框里看不出问题
                    .height(if (target == BgTarget.Desktop) 100.dp else 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    // #6：已设置背景时点击先看原图（原来是直接又弹 AI 描述框）
                    .clickable { onPreview() }
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onGenerate() },
                contentAlignment = Alignment.Center
            ) {
                Text("点击设置背景", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 与头像区保持同一套观感（同一个 ImageSourceButtons）
            ImageSourceButtons(
                busy = busy,
                generateLabel = "AI 生成场景",
                onPickLocal = onPickLocal,
                onGenerate = onGenerate,
                onPickReference = onPickReference
            )
            if (!uri.isNullOrBlank()) {
                TextButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("移除") }
            }
        }
    }
}

/**
 * 「本地图片 / AI 生成 / 参考图」按钮行。
 *
 * 头像区与背景区原本各有一份完整拷贝，差别只有生成文案与生成目标。
 * 三条观感约定（问题 #5）：两个主操作统一成描边按钮、**强制等宽**（否则「本地图片」比
 * 「AI 生成」宽一截，看着不齐）、收窄内容内边距（等宽并排后文字不被挤断）。
 */
@Composable
internal fun RowScope.ImageSourceButtons(
    busy: Boolean,
    generateLabel: String,
    onPickLocal: () -> Unit,
    onGenerate: () -> Unit,
    onPickReference: () -> Unit
) {
    val padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    OutlinedButton(
        onClick = onPickLocal,
        modifier = Modifier.weight(1f),
        contentPadding = padding
    ) {
        Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("本地图片")
    }
    AiActionButton(
        label = generateLabel,
        busyLabel = "生成中…",
        onClick = onGenerate,
        busy = busy,
        enabled = !busy,
        filled = false,
        starSize = 16.dp,
        gap = 4.dp,
        modifier = Modifier.weight(1f),
        contentPadding = padding
    )
    OutlinedButton(
        onClick = onPickReference,
        enabled = !busy,
        modifier = Modifier.weight(1f),
        contentPadding = padding
    ) {
        Text("参考图")
    }
}
