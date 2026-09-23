package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.rememberAudioFilePicker
import com.mysticat.roleplay.ui.showToast
import kotlin.math.roundToInt

/**
 * 「背景音乐」设置页（第 63 轮会话级架构改造的设置半边）。
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
    // 旧全局单文件 → 音乐库的一次性迁移：进页面就做（迁移结果马上落盘，用户无感）
    remember {
        val s = Repository.loadSettings()
        val migrated = Repository.migrateLegacyBgmIntoLibrary(s)
        if (migrated != s) Repository.saveSettings(migrated)
        true
    }

    // 音乐库清单**在页面这一层读一次**，给下面两张卡共用（第 58 轮）。
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
            item {
                Text(
                    "背景音乐只在会话中播放：进入聊天页响、离开就停。每个会话都可以在聊天页菜单的" +
                        "「背景音乐」里单独选曲（默认跟随这里的全局设置）；朗读回复时音乐会自动压低音量继续放。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { BgmLibraryCard(library) { libRev++ } }
            item { BgmGlobalCard(library) }
        }
    }

    if (embedded) {
        // 桌面三栏内嵌：外层是 Column，列表必须给 weight —— 否则 LazyColumn 在 Column 里
        // 拿不到剩余高度，滚不到底部的「全局默认」卡
        Column(Modifier.fillMaxSize()) {
            Text(
                "背景音乐",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            Box(Modifier.weight(1f)) { body() }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("背景音乐") },
                    navigationIcon = {
                        TextButton(onClick = onBack) { Text("返回") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
 * 让**同一页的「全局默认音源」也跟着刷新**——这是第 58 轮那条"新传的曲目要再点一下才出现"的修法。
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

    // 桌面（第 93 轮，台账 12 ④）：卡片外壳由 [SectionCard] 在桌面上摘掉；标题行带图标与「上传」按钮，
    // 不是纯标题，所以留在内容里；内边距仍是那句 18dp（contentPadding 给 0，免得叠成两遍）。
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        bordered = true,
        contentPadding = 0.dp,
        spacing = 0.dp
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LibraryMusic,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "我的音乐",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = pickAudio) {
                    Icon(Icons.Filled.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("上传")
                }
            }
            Text(
                "支持 " + com.mysticat.roleplay.data.Voice.supportedAudioExtensions.sorted().joinToString(" / ") +
                    "。重命名只改应用里的显示名，不会改动源文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (library.isEmpty()) {
                Text(
                    "还没有上传过音乐。上传后就能在会话与全局默认里选它们。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                library.forEach { entry ->
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
                            if (BgmPlayer.previewPath != null &&
                                BgmPlayer.previewPath == Repository.bgmLibraryFileFor(entry.id)?.absolutePath
                            ) {
                                Text(
                                    BgmPlayer.previewError ?: "试听中…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (BgmPlayer.previewError != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        // 试听：单独一条播放路，不打断正式 BGM（起播失败当场给出原因）
                        IconButton(onClick = {
                            val f = Repository.bgmLibraryFileFor(entry.id)
                            if (f == null) {
                                showToast("这个音频的文件不见了，删掉后重新上传吧", long = true)
                            } else if (BgmPlayer.previewPath == f.absolutePath) {
                                BgmPlayer.stopPreview()
                            } else {
                                BgmPlayer.preview(f)
                            }
                        }) {
                            Icon(
                                if (BgmPlayer.previewPath != null &&
                                    BgmPlayer.previewPath == Repository.bgmLibraryFileFor(entry.id)?.absolutePath
                                ) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = "试听"
                            )
                        }
                        IconButton(onClick = { renameTarget = entry }) {
                            Icon(Icons.Filled.Edit, contentDescription = "重命名")
                        }
                        // 删除一律要确认（第 58 轮的口径）：删掉的是用户自己传进来的文件，找不回来
                        IconButton(onClick = { deleteTarget = entry }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
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
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${entry.displayName}」？") },
            text = { Text("这个音频文件会从音乐库里删除，无法恢复。正在用它的会话会回退到全局默认音源。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    Repository.deleteBgmLibraryEntry(entry.id)
                    // 各处设置里指向它的引用回退到第一个内置音源，避免"开着开关指着已删除的文件"静默无声
                    revertDeletedSource("lib:${entry.id}")
                    onLibraryChanged()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
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
     * 换音源：落盘 + **当场试听几秒**（第 58 轮，用户口径「选择不同的声音时，需要给几秒钟的试听」）。
     *
     * 会不会跟正式 BGM 打架：不会——设置页占着屏幕时聊天页已离开组合，[BgmPlayer] 没有活跃会话，
     * 而试听走的是另一个引擎实例，本来就不打断正式播放。
     */
    fun selectSource(id: String) {
        persist(s.copy(bgmSource = id))
        BgmPlayer.previewSource(id)
    }

    // 桌面（第 93 轮，台账 12 ④）：同上一张卡——外壳交给 [SectionCard]，桌面端摘掉；标题行带开关，
    // 留在内容里（卡内那句 `HorizontalDivider` 是分节线，与外壳无关，桌面端照旧）。
    SectionCard(
        modifier = Modifier.fillMaxWidth(),
        bordered = true,
        contentPadding = 0.dp,
        spacing = 0.dp
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "全局默认音源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
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
            Text(
                "会话没有单独配置背景音乐时，用这里的默认。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text("音源（点一下就能试听几秒）", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BgmSources.builtin.forEach { src ->
                    WhaleChip(
                        selected = s.bgmSource == src.id,
                        onClick = { selectSource(src.id) },
                        label = { Text(src.label) }
                    )
                }
                library.forEach { entry ->
                    WhaleChip(
                        selected = s.bgmSource == "lib:${entry.id}",
                        onClick = { selectSource("lib:${entry.id}") },
                        label = { Text("🎵 " + entry.displayName) }
                    )
                }
            }
            val dangling = isDanglingSource(s.bgmSource)
            val hint = when {
                s.bgmSource.isBlank() -> "还没选音源（打开开关后会自动选第一档）"
                dangling -> "当前选的音乐文件不见了（可能已被删除），换一个吧"
                else -> BgmPlayer.labelFor(s.bgmSource, s).let { "正在用：$it" }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dangling) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 手动再听一遍当前这一档（点 chip 会自动试听，这里给"刚才没听清"留一条路）
                if (s.bgmSource.isNotBlank() && !dangling) {
                    val previewing = BgmPlayer.previewPath != null
                    TextButton(onClick = {
                        if (previewing) BgmPlayer.stopPreview() else BgmPlayer.previewSource(s.bgmSource)
                    }) {
                        Icon(
                            if (previewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (previewing) "停止试听" else "试听 6 秒")
                    }
                }
            }

            HorizontalDivider()

            Text(
                "音量：${(volume * 100).roundToInt()}%（与聊天页「背景音乐」共用）",
                style = MaterialTheme.typography.labelLarge
            )
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
                valueRange = 0f..1f
            )

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
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (BgmPlayer.lastError != null || BgmPlayer.previewError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
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
 * 本会话背景音乐弹窗（第 63 轮；聊天页菜单「背景音乐」）。**会话内调音量的唯一入口**。
 *
 * 三种模式：跟随全局 / 本会话关闭 / 本会话自定义（内置音源或音乐库曲目）。
 * 每次点选**立即生效并落盘**（写回 `Conversation.bgm`），不用单独的保存按钮。
 *
 * 第 72 轮：**音量滑条在这一页都能用**（除"本会话不播放"），它改的是与设置页共享的那一个音量。
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
    // 音量＝**与设置页共享的那一个数**（第 72 轮，用户口径"优先共享"）：会话不再有自己的音量，
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
                        color = if (active) MaterialTheme.colorScheme.primary
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
                // 音量滑条（第 72 轮）：**与设置页共用同一个音量**，所以"跟随全局默认"时也给出来——
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
                    color = if (BgmPlayer.lastError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
