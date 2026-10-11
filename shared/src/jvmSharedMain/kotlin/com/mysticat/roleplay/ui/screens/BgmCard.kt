package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.BgmLibraryEntry
import com.mysticat.roleplay.data.BgmPlayer
import com.mysticat.roleplay.data.BgmSources
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.SessionBgm
import com.mysticat.roleplay.ui.SectionCard
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.components.WhaleFieldBox
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.components.WhaleFieldRow
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.rememberAudioFilePicker
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSizes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import kotlin.math.roundToInt

/**
 * 「背景音乐」设置页（会话级架构改造的设置半边）。
 *
 * 布局（用户 2026-09-21 口径：设置里保留 BGM 选项但**不单占一块**，与其他设置一样点进来）：
 * 1. **我的音乐**（音乐库）：上传 / 重命名（只改应用内显示名，不动源文件）/ 删除 / 试听；
 * 2. **全局默认**：开关 + 内置九档与库内曲目任选 + 音量——会话没有自己的配置时用它；
 * 3. 一行说明：BGM **只在会话中播放**，每个会话在聊天页菜单「背景音乐」里可单独配置
 *    （跟随全局 / 关闭 / 自选），朗读时音乐自动压低音量并行。
 *
 * 落盘走 [Repository.saveSettings]（内部会同步给 [BgmPlayer]）；音量滑条拖动中只改实时音量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgmScreen(
    onBack: () -> Unit,
    /** 桌面三栏：内嵌进第三栏（入口＝第二栏「背景音乐」分组），不套 Scaffold/顶栏 */
    embedded: Boolean = false
) {
    // 返回键（安卓）/ Esc（桌面）＝退回上一层；内嵌进第三栏时 onBack 由外壳给成"关掉整页"
    WhaleBackHandler { onBack() }
    // 旧全局单文件 → 音乐库的一次性迁移：进页面就做（迁移结果马上落盘，用户无感）
    remember {
        val s = Repository.loadSettings()
        val migrated = Repository.migrateLegacyBgmIntoLibrary(s)
        if (migrated != s) Repository.saveSettings(migrated)
        true
    }

    // 音乐库清单**在页面这一层读一次**，给下面两张卡共用。
    // 之前两张卡各自读 `Repository.listBgmLibrary()`，上传后只有「我的音乐」那张因为自己的 rev 变了而刷新，
    // 「全局默认音源」那张卡没观察到任何状态变化、**不重组**，于是刚传进去的曲目不出现在音源列表里，
    // 得再点一下别处才冒出来（用户 2026-09-21 反馈）。
    var libRev by remember { mutableStateOf(0) }
    val library = remember(libRev) { Repository.listBgmLibrary() }

    // 离开这一页就停掉试听：换音源会自动试听几秒（见 BgmGlobalCard），别让它跟到别的页面里继续响
    DisposableEffect(Unit) { onDispose { BgmPlayer.stopPreview() } }

    val body: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顺序按 v10 原型 §六：全局默认在前（它是"默认值"），我的音乐在后（它是素材库）。
            // 顶部那段长说明随行表式撤掉——"只在会话中播 / 会话可单独选曲"两句已分别落在
            // 状态行与「全局默认音源」的说明里（口径：说明句一句话短版）。
            item { BgmGlobalCard(library) }
            item { BgmLibraryCard(library) { libRev++ } }
        }
    }

    if (embedded) {
        // 桌面三栏内嵌：外层是 Column，列表必须给 weight —— 否则 LazyColumn 在 Column 里
        // 拿不到剩余高度，滚不到底部的「全局默认」卡
        Column(Modifier.fillMaxSize()) {
            EmbeddedPageHeader("背景音乐", onBack)
            Box(Modifier.weight(1f)) { body() }
        }
    } else {
        Scaffold(
            topBar = {
                WhalePageTopBar(
                    title = "背景音乐",
                    navigationIcon = { BackArrowButton(onBack) }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) { body() }
        }
    }
}

/**
 * 音乐库卡片：上传 / 重命名（应用内显示名）/ 删除 / 试听。
 *
 * [library] 由 [BgmScreen] 统一读并传进来（见那里的说明）；本卡自己改了库就调 [onLibraryChanged]，
 * 让**同一页的「全局默认音源」也跟着刷新**——这是那条"新传的曲目要再点一下才出现"的修法。
 */
@Composable
private fun BgmLibraryCard(library: List<BgmLibraryEntry>, onLibraryChanged: () -> Unit) {
    var renameTarget by remember { mutableStateOf<BgmLibraryEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<BgmLibraryEntry?>(null) }

    val pickAudio = rememberAudioFilePicker { path, name ->
        if (path != null) {
            // 平台选择器内部已把文件收进库（Repository.saveBgmFile 现在就是入库）；最新一条即刚传的
            val entry = Repository.listBgmLibrary().lastOrNull()
            if (entry != null && name.isNotBlank()) {
                Repository.renameBgmLibraryEntry(entry.id, name)
            }
            onLibraryChanged()
            showToast("已加入音乐库：$name")
        }
    }

    // v10 原型 §六：库本身是"框"，所以走**小字段标签 ＋ 框**（[WhaleFieldBox]），行尾三个动作
    // 在窄屏竖排太挤 ⇒ 平铺三个文字按钮；框内最多看到 [LIB_VISIBLE_ROWS] 首，多了框内滚。
    WhaleFieldGroup(label = "我的音乐") {
        WhaleFieldBox(
            label = "音乐库",
            hint = when {
                library.isEmpty() -> null
                library.size > LIB_VISIBLE_ROWS ->
                    "共 ${library.size} 首 · 框内可滚（最多同时看到 $LIB_VISIBLE_ROWS 首）"
                else -> "共 ${library.size} 首"
            }
        ) {
            if (library.isEmpty()) {
                Text(
                    "还没有上传过音乐。上传后就能在会话与全局默认里选它们。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    Modifier
                        .heightIn(max = LIB_BOX_MAX)
                        .verticalScroll(rememberScrollState())
                ) {
                    library.forEach { entry ->
                        LibraryRow(
                            entry = entry,
                            onPreview = {
                                val f = Repository.bgmLibraryFileFor(entry.id)
                                if (f == null) {
                                    showToast("这个音频的文件不见了，删掉后重新上传吧", long = true)
                                } else if (BgmPlayer.previewPath == f.absolutePath) {
                                    BgmPlayer.stopPreview()
                                } else {
                                    BgmPlayer.preview(f)
                                }
                            },
                            onRename = { renameTarget = entry },
                            onDelete = { deleteTarget = entry }
                        )
                    }
                }
            }
        }
        WhaleFieldRow(
            title = "上传音乐",
            desc = "支持 " + com.mysticat.roleplay.data.Voice.supportedAudioExtensions.sorted().joinToString(" / ") +
                "；重命名只改应用里的显示名，不会改动源文件",
            trailing = { TextButton(onClick = pickAudio) { Text("选择文件") } },
            divider = false
        )
    }

    renameTarget?.let { entry ->
        com.mysticat.roleplay.ui.TextInputDialog(
            title = "重命名（只改应用内显示名）",
            initial = entry.displayName,
            label = "显示名",
            singleLine = true,
            onConfirm = { newName ->
                Repository.renameBgmLibraryEntry(entry.id, newName)
                renameTarget = null
                onLibraryChanged()
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { entry ->
        // 236 弹窗统一：删除类残留收编共享确认壳（手机底部浮层／桌面居中面板，与 241 同族）
        WhaleConfirmSheet(
            title = "删除「${entry.displayName}」？",
            body = "这个音频文件会从音乐库里删除，无法恢复。正在用它的会话会回退到全局默认音源。",
            confirmLabel = "删除",
            onConfirm = {
                deleteTarget = null
                Repository.deleteBgmLibraryEntry(entry.id)
                // 各处设置里指向它的引用回退到第一个内置音源，避免"开着开关指着已删除的文件"静默无声
                revertDeletedSource("lib:${entry.id}")
                onLibraryChanged()
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

/** 音乐库框内最多同时看到几首（原型 §六：5 首内滚） */
private const val LIB_VISIBLE_ROWS = 5

/** 音乐库框的最大高度≈5 行（每行 40：名字 20 ＋ 触控留白），多了框内滚 */
private val LIB_BOX_MAX = 200.dp

/**
 * 音乐库一行：名字（＋试听状态）在左，右侧平铺三个文字动作（试听／重命名／删除）。
 *
 * 试听＝单独一条播放路，不打断正式 BGM（起播失败当场给出原因）；删除一律要确认（口径）。
 */
@Composable
private fun LibraryRow(
    entry: BgmLibraryEntry,
    onPreview: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val file = Repository.bgmLibraryFileFor(entry.id)
    val previewing = BgmPlayer.previewPath != null && BgmPlayer.previewPath == file?.absolutePath
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (previewing) {
                Text(
                    BgmPlayer.previewError ?: "试听中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (BgmPlayer.previewError != null) LocalWhalePalette.current.dangerText
                    else LocalWhalePalette.current.accentText
                )
            }
        }
        LibraryAction(if (previewing) "停止" else "试听", onClick = onPreview)
        LibraryAction("重命名", onClick = onRename)
        LibraryAction("删除", onClick = onDelete)
    }
}

/** 音乐库行尾的紧凑文字动作：热区 44×44（触控基线），外壳留白收到 10。 */
@Composable
private fun LibraryAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalWhalePalette.current.accentText,
        modifier = Modifier
            .defaultMinSize(minWidth = WhaleSizes.hit, minHeight = WhaleSizes.hit)
            .clip(WhaleShapes.r1)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    )
}

/** 删除曲目后，把全局与会话里指向它的引用改回安全的内置音源（防止静默无声） */
private fun revertDeletedSource(deletedSourceId: String) {
    val s = Repository.loadSettings()
    if (s.bgmSource == deletedSourceId) {
        Repository.saveSettings(s.copy(bgmSource = BgmSources.builtin.first().id))
    }
    // 会话级引用：全账号扫一遍成本高（每会话一次读盘），改为惰性——
    // resolveDesired 拿不到文件时已经会给出明确提示，用户在会话里换个音源即可
}

/**
 * 全局默认卡：开关 + 音源（内置九档 + 音乐库）+ 音量 + 状态行（会话未自定义时的默认）。
 *
 * [library] 由 [BgmScreen] 传进来（不再自己读）——自己读的那版在上传后不会重组，见那里的说明。
 */
@Composable
private fun BgmGlobalCard(library: List<BgmLibraryEntry>) {
    var s by remember { mutableStateOf(Repository.loadSettings()) }
    var volume by remember(s.bgmVolume) { mutableStateOf(s.bgmVolume) }

    fun persist(next: AiSettings) {
        s = next
        Repository.saveSettings(next)
    }

    /**
     * 换音源：落盘 + **当场试听几秒**（用户口径「选择不同的声音时，需要给几秒钟的试听」）。
     *
     * 会不会跟正式 BGM 打架：不会——设置页占着屏幕时聊天页已离开组合，[BgmPlayer] 没有活跃会话，
     * 而试听走的是另一个引擎实例，本来就不打断正式播放。
     */
    fun selectSource(id: String) {
        persist(s.copy(bgmSource = id))
        BgmPlayer.previewSource(id)
    }

    // v10 原型 §六：三行＝开关行 / 音源（≥3 档单值 ⇒ 下拉，手机降到整宽）/ 音量（滑条在行右）。
    val dangling = isDanglingSource(s.bgmSource)
    val volumeSliderWidth = if (isDesktopLayout) 180.dp else 120.dp
    // 状态行：把"现在到底响没响"说清楚（BGM 只在会话中播，设置页里看到的"正在播放"
    // 意味着聊天页还开着；否则显示的是"下次进会话会用什么"）
    val status = when {
        BgmPlayer.lastError != null -> "播放失败：${BgmPlayer.lastError}"
        // 试听失败也要出声（m4a 这类"声明支持但个别文件解不了"的靠它现形）
        BgmPlayer.previewError != null -> "试听失败：${BgmPlayer.previewError}"
        BgmPlayer.previewPath != null -> "正在试听（音量 ${(volume * 100).roundToInt()}%）"
        BgmPlayer.currentPath != null -> "正在播放：${playingLabel(BgmPlayer.currentPath!!, s)}"
        !s.bgmEnabled -> "已关闭"
        else -> "已就绪（进入会话后播放）"
    }
    WhaleFieldGroup {
        WhaleFieldRow(
            title = "全局默认音源",
            desc = "会话没有单独配置背景音乐时用这里的",
            trailing = {
                Switch(
                    checked = s.bgmEnabled,
                    onCheckedChange = { on ->
                        // 只打开开关却没有任何音源 = "开了却没声"，所以顺手替用户选好第一个内置音源
                        val src = s.bgmSource.ifBlank { BgmSources.builtin.first().id }
                            .let {
                                if (it.isBlank() || isDanglingSource(it)) BgmSources.builtin.first().id else it
                            }
                        persist(s.copy(bgmEnabled = on, bgmSource = src))
                    }
                )
            }
        )
        WhaleFieldRow(
            title = "音源",
            desc = "内置九档或自己的音乐",
            trailingBelow = true,
            trailing = {
                SourcePicker(
                    current = s.bgmSource,
                    settings = s,
                    dangling = dangling,
                    library = library,
                    onSelect = { selectSource(it) },
                    modifier = if (isDesktopLayout) Modifier else Modifier.fillMaxWidth()
                )
            }
        )
        WhaleFieldRow(
            title = "音量",
            desc = "与朗读音量相互独立，聊天页里调的是同一个",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            // 拖动中：只改实时音量，不落盘（理由见 BgmPlayer.setVolumeLive）
                            BgmPlayer.setVolumeLive(it)
                        },
                        onValueChangeFinished = {
                            persist(s.copy(bgmVolume = volume))
                            // 松手就试听一遍：设置页里聊天页已离开组合（正式 BGM 不响），
                            // 不试听的话"拖了滑条什么都听不到"＝用户报的"调节不生效"。
                            // 已经在试听时不重启它（实时音量已跟着变，重启反而要断一下）。
                            if (s.bgmSource.isNotBlank() && !dangling && BgmPlayer.previewPath == null) {
                                BgmPlayer.previewSource(s.bgmSource)
                            }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.width(volumeSliderWidth)
                    )
                    Spacer(Modifier.width(WhaleSpacing.sm))
                    Text(
                        "${(volume * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            divider = false
        )
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = if (BgmPlayer.lastError != null || BgmPlayer.previewError != null) {
                LocalWhalePalette.current.dangerText
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 2.dp, bottom = 13.dp)
        )
    }
}

/**
 * 音源下拉：值＋箭头（点箭头或整块开列表），选中即试听几秒（[onSelect] 由调用方接）。
 *
 * 档位是"枚举长名字"（内置九档 ＋ 音乐库任意多首）⇒ 走原型 §十一 口径 ② 的下拉，
 * 不是开关行（[WhaleToggleRow] 的档位少而短才折成方框）。库曲目与内置档之间加一条分组线，
 * 免得两串名字混在一起看不出哪几首是自己传的。
 */
@Composable
private fun SourcePicker(
    current: String,
    settings: AiSettings,
    dangling: Boolean,
    library: List<BgmLibraryEntry>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val fieldLine = LocalWhalePalette.current.fieldLine
    val label = when {
        current.isBlank() -> "选一个音源"
        dangling -> "${BgmPlayer.labelFor(current, settings)}（文件不见了）"
        else -> BgmPlayer.labelFor(current, settings)
    }
    Box(modifier) {
        Row(
            Modifier
                .defaultMinSize(minHeight = WhaleSizes.hit)
                .clip(WhaleShapes.r1)
                .background(colors.surface, WhaleShapes.r1)
                .border(1.dp, fieldLine, WhaleShapes.r1)
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dangling) LocalWhalePalette.current.dangerText else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择音源",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // 限高（2026-10-10）：内置音源＋自传库曲目会越攒越多，菜单不许按条数一路拉长，
            // 上限 320dp、超出在菜单内滚动。
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            BgmSources.builtin.forEach { src ->
                DropdownMenuItem(
                    text = { Text(src.label) },
                    onClick = { onSelect(src.id); expanded = false }
                )
            }
            if (library.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant)
                library.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { onSelect("lib:${entry.id}"); expanded = false }
                    )
                }
            }
        }
    }
}

/** 音源引用是否已经失效（库曲目被删 / 全局单文件路径不存在） */
private fun isDanglingSource(sourceId: String): Boolean {
    if (sourceId.isBlank()) return false
    return if (sourceId.startsWith("lib:")) {
        Repository.bgmLibraryFileFor(sourceId.removePrefix("lib:")) == null
    } else {
        false
    }
}

private fun playingLabel(path: String, s: AiSettings): String = when {
    path == s.bgmUserPath && s.bgmUserPath.isNotBlank() -> s.bgmUserName.ifBlank { "我上传的音乐" }
    else -> Repository.listBgmLibrary()
        .firstOrNull { Repository.bgmLibraryFileFor(it.id)?.absolutePath == path }?.displayName
        ?: BgmSources.builtin.firstOrNull { path.endsWith("${it.id}.wav") }?.label
        ?: "背景音乐"
}

/**
 * 本会话背景音乐弹窗（聊天页菜单「背景音乐」）。**会话内调音量的唯一入口**。
 *
 * 三种模式：跟随全局 / 本会话关闭 / 本会话自定义（内置音源或音乐库曲目）。
 * 每次点选**立即生效并落盘**（写回 `Conversation.bgm`），不用单独的保存按钮。
 *
 * **音量滑条在这一页都能用**（除"本会话不播放"），它改的是与设置页共享的那一个音量。
 * 之前它只在"本会话自定义"里出现、且拖动期间不生效，用户反馈"找不到地方调 / 调了不生效"。
 */
@Composable
fun SessionBgmDialog(
    current: SessionBgm?,
    onApply: (SessionBgm?) -> Unit,
    onDismiss: () -> Unit
) {
    val settings = remember { Repository.loadSettings() }
    val mode = current?.mode ?: SessionBgm.MODE_GLOBAL
    // 音量＝**与设置页共享的那一个数**（用户口径"优先共享"）：会话不再有自己的音量，
    // 所以初值直接取设置里的值，改完写回设置（松手走 [BgmPlayer.commitVolume]）。
    var volume by remember { mutableStateOf(settings.bgmVolume) }
    // rev 变化＝库刚被改过（弹窗里也能上传），强制重读
    var rev by remember { mutableStateOf(0) }
    val library = remember(rev) { Repository.listBgmLibrary() }

    val pickAudio = rememberAudioFilePicker { path, name ->
        if (path != null) {
            val entry = Repository.listBgmLibrary().lastOrNull()
            if (entry != null) {
                if (name.isNotBlank()) Repository.renameBgmLibraryEntry(entry.id, name)
                onApply(SessionBgm(SessionBgm.MODE_ON, "lib:${entry.id}"))
                rev++
                showToast("已加入音乐库并用于本会话：$name")
            }
        }
    }

    val optionRow: @Composable (String, String?, Boolean, () -> Unit) -> Unit =
        { label, sub, active, onClick ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else androidx.compose.ui.graphics.Color.Transparent
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
                        color = if (active) LocalWhalePalette.current.accentText
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本会话背景音乐") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                optionRow("跟随全局默认", "用「设置 → 背景音乐」里的全局配置", mode == SessionBgm.MODE_GLOBAL) {
                    onApply(SessionBgm(SessionBgm.MODE_GLOBAL))
                }
                optionRow("本会话不播放", "安静模式：这个会话里不出背景音乐", mode == SessionBgm.MODE_OFF) {
                    onApply(SessionBgm(SessionBgm.MODE_OFF))
                }
                optionRow("本会话自定义", "只在当前这个会话里放选中的音乐", mode == SessionBgm.MODE_ON) {
                    // 还没选过音源就先给第一档，避免"选了自定义却无声"
                    val src = current?.sourceId?.takeIf { it.isNotBlank() }
                        ?: BgmSources.builtin.first().id
                    onApply(SessionBgm(SessionBgm.MODE_ON, src))
                }
                if (mode == SessionBgm.MODE_ON) {
                    HorizontalDivider()
                    Text("音源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BgmSources.builtin.forEach { src ->
                            WhaleChip(
                                selected = current?.sourceId == src.id,
                                onClick = { onApply(SessionBgm(SessionBgm.MODE_ON, src.id)) },
                                label = { Text(src.label) }
                            )
                        }
                        library.forEach { entry ->
                            WhaleChip(
                                selected = current?.sourceId == "lib:${entry.id}",
                                onClick = { onApply(SessionBgm(SessionBgm.MODE_ON, "lib:${entry.id}")) },
                                label = { Text("🎵 " + entry.displayName) }
                            )
                        }
                    }
                    TextButton(onClick = pickAudio) {
                        Icon(Icons.Filled.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("上传音乐到音乐库")
                    }
                }
                // 音量滑条：**与设置页共用同一个音量**，所以"跟随全局默认"时也给出来——
                // 以前只在"本会话自定义"里出现，用户在会话里想调音量根本找不到入口；
                // 又因为那时只有会话音量说话，拖动期间还不生效（松手才写回）。
                // "本会话不播放"时不给（这一刻调了也听不见，徒增困惑）。
                if (mode != SessionBgm.MODE_OFF) {
                    HorizontalDivider()
                    Text(
                        "音量：${(volume * 100).roundToInt()}%（与「设置 → 背景音乐」共用）",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            // 拖动中只改实时音量（不落盘）：此刻音乐就在响，松手前也该听得见变化
                            BgmPlayer.setVolumeLive(it)
                        },
                        onValueChangeFinished = { BgmPlayer.commitVolume(volume) },
                        valueRange = 0f..1f
                    )
                }
                HorizontalDivider()
                val status = when {
                    BgmPlayer.lastError != null -> "播放失败：${BgmPlayer.lastError}"
                    BgmPlayer.currentPath != null -> "正在播放"
                    else -> "（音乐只在聊天页开着时播放；朗读时会自动压低音量继续放）"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (BgmPlayer.lastError != null) LocalWhalePalette.current.dangerText
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
