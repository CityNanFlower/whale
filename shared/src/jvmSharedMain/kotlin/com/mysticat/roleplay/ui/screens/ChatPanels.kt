package com.mysticat.roleplay.ui.screens

/**
 * 输入区与面板：输入栏、叙事风格基础档面板（「更多」）、灵感回复弹窗、历史记录 sheet、
 * 生效风格 chip 与各轴胶囊。
 *
 * * 2026-09-17（D10）从 `ChatScreen.kt` 拆出：纯结构拆分，逻辑未动。
 */

import com.mysticat.roleplay.ui.components.WhaleFrostedPanel
import com.mysticat.roleplay.ui.components.whaleDialogContainer
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.mysticat.roleplay.ui.DesktopAnchoredPanel
import com.mysticat.roleplay.ui.DesktopShortcutAction
import com.mysticat.roleplay.ui.DesktopShortcuts
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.EngineSpec
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.NarrativeStyle
import com.mysticat.roleplay.data.NarrativeStyles
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.StyleAxis
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.data.narrativeStyle
import com.mysticat.roleplay.data.providerPickerUrls
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.timeText
import com.mysticat.roleplay.ui.tokens.WhaleGlass
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import com.mysticat.roleplay.ui.theme.LocalWhalePalette

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
 * [status] 是卡片底下的状态行（工具卡片用；空＝本卡不写文案，但**整格统一留位**，
 * 见 [PanelGridCell] 的 `reserveStatus`）；[disabled] **置灰但保留位置**，口径同发图按钮
 * （不支持的项不整只消失，由 [PanelGrid] 在网格下挂一行解释）。
 */
internal data class PanelCell(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val status: String = "",
    val disabled: Boolean = false,
    val hint: String? = null,
    /**
     * 分组标题（第 194 轮「工具栏分块」）：只在**桌面列表** [ToolListPanel] 里画成一个小节标题，
     * 手机端卡片墙不读它（手机仍是卡片墙，按用户口径不分块）。同一个 section 只画一次，
     * 所以顺序即分组，cell 列表本身仍是唯一的数据源。
     */
    val section: String? = null,
    val onClick: () -> Unit = {}
)

@Composable
internal fun PanelGridCell(
    cell: PanelCell,
    modifier: Modifier = Modifier,
    /**
     * **状态行留位**（网格级，由 [PanelGrid] 统一传下来）：只要本格里有一张卡带状态行，
     * 其余卡也得占住那一行——否则同一行里"没状态"的卡会矮一截，底边参差不齐
     * （用户 2026-10-08 反馈「上次请求卡片和其他卡片没有对齐」）。
     * 留位是**网格级**而不是每张卡自己决定的：以后往网格里加卡只加数据，不必记得补占位行。
     */
    reserveStatus: Boolean = false
) {
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
        if (cell.status.isNotBlank() || reserveStatus) {
            // 空状态用不换行空格占位：与真状态行**同款同高**（换空串高度会塌），底边因此对齐
            Text(
                cell.status.ifBlank { "\u00A0" },
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
    // 一格里只要有人带状态行，全格统一留位（见 PanelGridCell.reserveStatus）
    val reserveStatus = cells.any { it.status.isNotBlank() }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { PanelGridCell(it, Modifier.weight(1f), reserveStatus = reserveStatus) }
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
internal fun ChatPlusPanel(
    cells: List<PanelCell>,
    modifier: Modifier = Modifier,
    /** 与输入行同处一块悬浮卡片时传 false：中间那道线会把"一块"切成"两块" */
    divider: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(14.dp))
        PanelGrid(cells, columns = 4)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 工具抽屉：点 Tune 不再是长清单，而是一墙卡片，每张卡进各自的设置页/弹窗。
 *
 * 形态与互动面板同款（[ChatPlusPanel]）——手机＝挂在输入栏下方的抽屉（顶起输入框）、
 * 桌面＝由 [DesktopAnchoredPanel] 托管在锚定浮层里。卡片比互动面板多，所以自带纵向滚动。
 */
@Composable
internal fun ToolCardsPanel(
    cards: List<PanelCell>,
    modifier: Modifier = Modifier,
    /** 同 [ChatPlusPanel.divider]：与输入行合成一块卡片时不画分隔线 */
    divider: Boolean = true
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            "工具",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp)
        )
        Text(
            "点一张卡片进对应设置，改完立即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )
        PanelGrid(cards, columns = 2)
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 桌面工具面板（用户 2026-10-01 参考图定的形态）：**一行一个工具的淡色列表卡片**，
 * 由 [DesktopAnchoredPanel] 托管在输入栏**上方**——不要两列卡片墙（那是手机抽屉的形态）。
 * 行结构＝左「图标 ＋ 名称」、右「状态/说明」右对齐；置灰行保留位置、右侧写明原因。
 */
@Composable
internal fun ToolListPanel(
    cards: List<PanelCell>,
    modifier: Modifier = Modifier
) {
    // 限高（2026-10-10）：卡片批次只会越加越多，上限不能再是 460dp 这样的大数——它锚在输入栏
    // **上方**，条数一多就一路长到盖住大半聊天区（用户报的就是这个"无限制增高"）。现取 320dp
    // （与聊天页模型菜单同一档），矮窗口再按「窗口高 − 220dp（输入卡与上下留白）」收，最低 200dp；
    // 超出部分在面板内滚动。
    val windowHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val maxPanelHeight = if (windowHeight > 0.dp) {
        (windowHeight - 220.dp).coerceIn(200.dp, 320.dp)
    } else {
        320.dp
    }
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxPanelHeight)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            "工具",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "点一行进对应设置，改完立即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(4.dp))
        // 分块（第 194 轮）：与前一项的 section 不同就画一次标题——列表顺序即分组顺序，
        // 不另建数据结构（用一个纯比较，别在组合里攒可变局部变量）
        cards.forEachIndexed { index, cell ->
            val prevSection = cards.getOrNull(index - 1)?.section
            if (cell.section != null && cell.section != prevSection) {
                Text(
                    cell.section,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                )
            }
            // 行右＝状态（hint 是网格底注口径，只挂到列表末尾那行，别两处重复）
            val trailing = cell.status
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (cell.disabled) Modifier else Modifier.clickable(onClick = cell.onClick))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    cell.icon,
                    null,
                    Modifier.size(20.dp),
                    tint = if (cell.disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    cell.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (cell.disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (trailing.isNotBlank()) {
                    Text(
                        trailing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
        // 置灰行的解释（如「发一条消息后可查看」）：桌面列表没有网格底注那块位置，单独挂一行
        cards.mapNotNull { it.hint }.distinct().forEach { hint ->
            Text(
                "· $hint",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
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
        containerColor = whaleDialogContainer(),
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
 * 「模型与思考强度」**锚定浮层**（第 193 轮；用户 2026-10-01 参考图）：
 * 不再是 AlertDialog——由 [DesktopAnchoredPanel] 托管在输入卡**上方**开合，
 * 不遮输入框、没有全屏灰幕，调参时聊天区照常可见。
 *
 * 结构是**两级**：根页三行（供应商／对话模型／推理等级，各带当前值），点行进二级列表，
 * **选中即生效并整体收起**（不做"选完还要点完成"的两段确认）。
 *
 * 第 194 轮加**作用范围**（反馈 2 的三层架构：全局默认 → 会话级 → 场景级）：
 * 根页顶部两格「本会话 / 全局默认」，默认停在**本会话**（用户是在某个会话里点开这颗 chip 的）——
 *  * 本会话＝写 [Conversation.chatBaseUrl] / [Conversation.chatModel] / [Conversation.chatThinking]，只影响这一局；
 *  * 全局默认＝写 `AiSettings`，与设置页同一份，影响所有没单独设过的会话。
 * 会话已有覆盖时多一行「恢复跟随全局默认」；底部如实写明**当前改的是哪一份**。
 *
 * 供应商行（49 轮，用户 2026-10-10「切换模型最好也能改供应商」）：列"能用的家"
 * （已配 Key 的预设家 ＋ 自定义家 ＋ 当前生效家，见 `AiSettings.providerPickerUrls`），
 * 选中它**连带把模型换到这家**（原模型名不在新家清单里时落到第一个预设）——
 * 与设置页同名动作同口径，免得"换了家、模型名还是别家的"打 404。
 *
 * 收起（第 50 轮）：撤掉 210 轮那颗右上角 ✕（用户 2026-10-10「不用右上角的叉号，
 * 统一用点击空白处关闭」）——桌面＝点浮层外/Esc（`DesktopAnchoredPanel` 的 focusable Popup），
 * 手机＝点聊天区空白（宿主在内容区盖一层透明收口层，见 `ChatScreen`）。
 *
 * @param settings 宿主持有的当前设置快照（与输入条 chip 同源，保证两处文案一致）
 * @param sessionBaseUrl / [sessionModel] / [sessionThinking] 本会话的覆盖值（null＝没设过＝跟随全局）
 * @param onChanged 全局默认提交后的完整设置——宿主据此刷新 chip
 * @param onSessionChanged 会话级提交后的（供应商, 模型, 思考强度）覆盖三元组；全 null＝恢复跟随全局
 */
@Composable
internal fun ModelThinkingPanel(
    settings: AiSettings,
    sessionBaseUrl: String?,
    sessionModel: String?,
    sessionThinking: String?,
    onChanged: (AiSettings) -> Unit,
    onSessionChanged: (baseUrl: String?, model: String?, thinking: String?) -> Unit,
    onDismiss: () -> Unit
) {
    // 0＝根页；1＝对话模型二级列表；2＝推理等级二级列表；3＝供应商二级列表
    var page by remember { mutableStateOf(0) }
    // false＝本会话（默认）；true＝全局默认
    var globalScope by remember { mutableStateOf(false) }
    val sessBase = sessionBaseUrl?.takeIf { it.isNotBlank() }
    val sessModel = sessionModel?.takeIf { it.isNotBlank() }
    val sessThinking = sessionThinking?.takeIf { it.isNotBlank() }
    // 生效值：本会话＝覆盖优先、缺省回退全局；全局默认＝就是设置里那一份。
    // 二级列表的勾与 chip 文案都用它，两处才不会各说各话。
    val effBase = if (globalScope) settings.chatBaseUrl else sessBase ?: settings.chatBaseUrl
    val effModel = if (globalScope) settings.chatModel else sessModel ?: settings.chatModel
    val effThinking = if (globalScope) settings.chatThinking else sessThinking ?: settings.chatThinking
    val presets = remember(effBase) { ModelCatalog.chatPresets(effBase) }
    // 档位表跟着**生效模型**走（不同模型的可用档不同，用全局模型算会给出选不中的档）
    val levels = remember(effBase, effModel) {
        ProviderProfiles.thinkingLevels(effBase, effModel)
    }
    val provider = remember(effBase, settings.customProviders) {
        ProviderProfiles.resolve(effBase, settings.customProviders)?.name ?: effBase
    }
    // 供应商列表（49 轮）：预设家过滤"有 Key 的"＋自定义家＋当前生效家兜底（免得清单为空）
    val providerUrls = remember(effBase, settings) {
        settings.providerPickerUrls(
            ModelCatalog.baseUrlPresets(),
            settings.customProviders.mapNotNull { cp ->
                cp.chatBaseUrl.trim().ifBlank { cp.imageBaseUrl.trim() }.ifBlank { null }
            },
            effBase
        )
    }
    val levelLabel = levels.firstOrNull { it.mode == effThinking }?.label

    /** 切供应商的**连带**（与设置页 `switchChatProvider` 同口径）：原模型不在新家清单里就落到第一个预设 */
    fun linkedModel(url: String, current: String): String {
        val ps = ModelCatalog.chatPresets(url)
        return if (ps.isNotEmpty() && (current.isBlank() || current !in ps)) ps.first() else current
    }

    // 会话级：只动被点的那一项，其余项保持原覆盖（可能仍是"跟随全局"）
    fun commitSession(
        baseUrl: String? = sessBase,
        model: String? = sessModel,
        thinking: String? = sessThinking
    ) {
        onSessionChanged(baseUrl, model, thinking)
        onDismiss()
    }

    // 全局默认：从磁盘重读最新设置（别盖掉别处刚改的字段），只动这三个字段
    fun commitGlobal(
        baseUrl: String = settings.chatBaseUrl,
        model: String = settings.chatModel,
        thinking: String = settings.chatThinking
    ) {
        val next = Repository.loadSettings()
            .copy(chatBaseUrl = baseUrl, chatModel = model, chatThinking = thinking)
        Repository.saveSettings(next)
        onChanged(next)
        onDismiss()
    }

    /** 点了某个供应商：连带模型一起写（本会话档里，模型无需变时保留原覆盖不动） */
    fun pickProvider(url: String) {
        val linked = linkedModel(url, effModel)
        if (globalScope) commitGlobal(baseUrl = url, model = linked)
        else commitSession(baseUrl = url, model = if (linked != effModel) linked else sessModel)
    }

    Column(Modifier.fillMaxWidth()) {
        when (page) {
            0 -> {
                Row(
                    Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("模型与思考强度", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            // 49 轮：供应商有了自己的一行（可点进二级列表改），这行只当"改的是哪一份"的注脚
                            if (globalScope) "改全局默认：$provider" else "本会话：$provider",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 作用范围（第 194 轮）：默认「本会话」——用户是在会话里点开的
                ScopeSwitch(global = globalScope, onChange = { globalScope = it })
                // 供应商行（49 轮）：与设置页"供应商"同一动作，选中即换家（连带模型）
                ModelPickRow(title = "供应商", value = provider, onClick = { page = 3 })
                ModelPickRow(title = "对话模型", value = effModel, onClick = { page = 1 })
                ModelPickRow(
                    title = "推理等级",
                    value = levelLabel ?: effThinking.ifBlank { "模型默认" },
                    onClick = { page = 2 }
                )
                if (!globalScope && (sessBase != null || sessModel != null || sessThinking != null)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { commitSession(null, null, null) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "恢复跟随全局默认",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalWhalePalette.current.accentText
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    if (globalScope) {
                        "改的是全局默认，所有会话跟着变；已单独设过的会话不受影响。"
                    } else {
                        "改的是本会话，别的会话仍用全局默认。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            1 -> {
                // 二级列表：返回头固定，列表自己滚（服务商清单可能十几项）
                PanelPageHeader(title = "对话模型", onBack = { page = 0 })
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    if (presets.isEmpty()) {
                        Text(
                            "这家服务商没有内置清单，去「设置 · 对话模型」里手填模型名。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    presets.forEach { p ->
                        ModelOptionRow(
                            // 勾＝**生效值**（本会话覆盖优先），不是全局那份：否则用户设了会话覆盖后
                            // 回来一看勾跑到了别的行上，会以为设置没生效
                            selected = effModel == p,
                            label = if (ModelCatalog.isVisionModel(p)) "$p（支持图片）" else p,
                            onClick = { if (globalScope) commitGlobal(model = p) else commitSession(model = p) }
                        )
                    }
                }
            }

            2 -> {
                PanelPageHeader(title = "推理等级", onBack = { page = 0 })
                if (levels.isEmpty()) {
                    Text(
                        "$provider 未适配思考参数，保持模型默认行为（不发送任何思考字段）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                } else {
                    // 会话级覆盖也用同一只拉杆（与设置页同源组件）：档位名标在刻度上，
                    // 空值/未知值吸附到「不传参数」那档（ ThinkingSlider 内部兜底）
                    ThinkingSlider(
                        value = effThinking,
                        levels = levels,
                        enabled = true,
                        onValue = { if (globalScope) commitGlobal(thinking = it) else commitSession(thinking = it) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            else -> {
                // 供应商二级列表（49 轮）：只列"能用/在用的家"——没配 Key 的家选过去也打不通，
                // 不如不列（要临时加一家去设置 · API 配置填 Key 之后它就会出现在这里）
                PanelPageHeader(title = "供应商", onBack = { page = 0 })
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp)
                ) {
                    providerUrls.forEach { url ->
                        ModelOptionRow(
                            selected = effBase == url,
                            label = ProviderProfiles.nameFor(url),
                            onClick = { pickProvider(url) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 作用范围两格（第 194 轮）：本会话 / 全局默认。
 * 做成一对等宽可点块而不是 M3 的 SegmentedButton——浮层里已经有一套自绘的行样式，
 * 混进一个 M3 组件会带它自己的高度与配色，跟上下两行对不齐。
 */
@Composable
private fun ScopeSwitch(global: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(false to "本会话", true to "全局默认").forEach { (g, label) ->
            val selected = g == global
            Surface(
                onClick = { onChange(g) },
                shape = WhaleShapes.r2,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp)
                )
            }
        }
    }
}

/** 浮层根页的一行：左标题、右当前值＋`›`（点进二级列表） */
@Composable
private fun ModelPickRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 二级列表的返回头：`‹` ＋ 标题，整行点按返回根页 */
@Composable
private fun PanelPageHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onBack)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ChevronLeft,
            contentDescription = "返回",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * 二级列表的一行选项：整行可点＝**选中即生效并收起**（radio 只作选中态展示，点击同样提交）。
 * [hint] 只在选中行展示（与设置页 `ThinkingSlider` 同口径：档位说明跟着当前档走）。
 */
@Composable
private fun ModelOptionRow(
    selected: Boolean,
    label: String,
    hint: String? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

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
): Modifier = if (!isDesktopLayout) this else this
    .onFocusChanged { DesktopShortcuts.textFieldFocused = it.isFocused }
    .onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        // 输入行为按注册表分派（用户页「快捷键」可改；「发送消息」以外的 Enter/Shift+Enter 都是换行）
        val userKeys = Repository.loadSettings().shortcuts
        when {
            DesktopShortcuts.matchesAction(e, DesktopShortcutAction.INSERT_BRACKETS, userKeys) -> onBracket()
            DesktopShortcuts.matchesAction(e, DesktopShortcutAction.SEND_MESSAGE, userKeys) -> {
                if (!sending && canSend) onSend()
            }
            e.key == Key.Enter || e.key == Key.NumPadEnter -> onNewline()
            else -> return@onPreviewKeyEvent false
        }
        true
    }

/**
 * 输入框本体（手机窄条与桌面宽条**同一只**）：
 * 本身**不画任何底板**（不套 Surface、不描边、不填色）——它坐在宿主给的卡片上，整张卡是一个整体。
 *
 * 抽出来是因为 2026-10-01 之后它有两个宿主——手机的窄条与桌面的宽条，
 * 两边都要求"字宽/行数不同、其余完全一致"，复制一份就等于以后修一处漏一处。
 */
@Composable
private fun ChatInputField(
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    placeholder: String,
    maxLines: Int,
    modifier: Modifier = Modifier,
    keys: Modifier = Modifier,
    /** 起始高度：手机窄条 40dp（一行）／桌面大框 120dp（约五行，用户 2026-10-01「要高」）。 */
    minHeight: Dp = 40.dp
) {
    // ⚠ **不再套一层 Surface**（用户 2026-10-01：「输入文本的框不用刻意区分开，整个是一个整体」）。
    //   原来那层 r2＋surfaceVariant 0.45＋发丝线，在毛玻璃卡片里就是一块**卡中卡**：文字被框成
    //   一个小盒子、和卡片分成两层看。现在文字直接坐在卡片上。
    BasicTextField(
        value = field,
        onValueChange = onFieldChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .then(keys),
        textStyle = LocalTextStyle.current.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        maxLines = maxLines,
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

/**
 * 桌面输入条上的**模型 chip**（第 193 轮）：浅色胶囊「<模型> <思考档>」＋`⌃`，
 * 取代原「模型与思考强度」图标键（用户 2026-10-01 参考图：当前值就摆在输入条上，
 * 点开就地切换，不必去设置页）。`open` 时胶囊加深，与展开中的浮层对上。
 */
@Composable
private fun ModelThinkingChip(
    model: String,
    thinking: String,
    open: Boolean,
    onClick: () -> Unit,
    /** 这一局被单独设过模型/档位（第 194 轮）：胶囊上标出来，否则用户看不出它跟全局不一样 */
    sessionScoped: Boolean = false
) {
    val text = buildString {
        append(if (thinking.isBlank()) model else "$model $thinking")
        if (sessionScoped) append(" · 本会话")
    }
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        Modifier
            .height(34.dp)
            .widthIn(max = 280.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (open) 1f else 0.85f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "模型与思考强度",
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 桌面宽条上的一只键：列宽够时「图标＋文字」、窄了只留图标（用户 2026-10-01）。
 *
 * 与手机那套键位状态机不同，这里的键**常驻**——鼠标端没有"够不着"的问题，
 * 忽隐忽现反而让一排键左右跳。热区恒 ≥38dp（指针设备，不必按触屏的 44dp 走）。
 */
@Composable
private fun InputBarKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    tint: Color? = null,
    busy: Boolean = false
) {
    val fg = tint
        ?: if (selected) LocalWhalePalette.current.accentText
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .height(38.dp)
            .defaultMinSize(minWidth = 38.dp)
            .clip(WhaleShapes.r1)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatInputBar(
    value: String,
    sending: Boolean,
    pendingImage: String?,
    onClearImage: () -> Unit,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onToolClick: () -> Unit,
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
    plusCells: List<PanelCell> = emptyList(),
    /**
     * 「工具」抽屉：与加号面板同款（手机＝顶起输入框、桌面＝锚定浮层），两者互斥——
     * 调用方在打开其一时关掉另一个。卡片比互动面板多，[ToolCardsPanel] 自带纵向滚动。
     */
    toolOpen: Boolean = false,
    toolCells: List<PanelCell> = emptyList(),
    onToolClose: () -> Unit = {},
    /** 桌面宽条左下「发送图片」：直接调相册/文件选择（手机走 ＋ 面板里的三格） */
    onPickImage: () -> Unit = {},
    /** 「灵感回复」回调：**桌面输入条上那只键**（只在 [inspirationEnabled] 的形态＝陪伴/多线 出现）。 */
    onInspiration: () -> Unit = {},
    /** 灵感回复当前是否展开——键上要能看出开/关（手机端那颗胶囊同理） */
    inspirationOpen: Boolean = false,
    /**
     * 当前形态有没有「灵感回复」这件事（＝`EngineSpec.inspiration`）：**陪伴/多线 true，工具/玩法 false**。
     * 为 false 时那只键**整只不渲染**——否则工具/玩法会话里会多出一只点了没反应的死键
     * （VM 的 `openInspirations()` 同样会直接 return）。入口与呈现必须成对，别只关一半。
     */
    inspirationEnabled: Boolean = true,
    /**
     * 当前会话的聊天背景图。悬浮输入卡片要跟顶栏一样**真毛玻璃**：
     * Compose 的 blur 糊不到"背后已经画好的东西"，[WhaleFrostedPanel] 的做法是在浮层自己身上
     * 把同一张背景图重画一遍再模糊——所以卡片需要拿到这个 uri。
     */
    backgroundUri: String? = null,
    /**
     * 本会话的供应商/模型/思考强度覆盖（第 194 轮，`Conversation.chatBaseUrl` / `chatModel` / `chatThinking`，49 轮起含供应商）。
     * null/空 = 跟随全局。chip 显示的是**生效值**（覆盖优先），面板可写它。
     */
    sessionBaseUrl: String? = null,
    sessionModel: String? = null,
    sessionThinking: String? = null,
    /** 会话级覆盖提交口（三个都 null = 恢复跟随全局） */
    onSessionOverride: (baseUrl: String?, model: String?, thinking: String?) -> Unit = { _, _, _ -> },
    /**
     * 「模型与思考强度」锚定浮层的开合（第 194 轮提到 VM）：工具列表里也有一格同名入口，
     * 两处必须开**同一个**浮层，所以开关由宿主持有（见 `ChatViewModel.showModelPanel`）。
     */
    modelPanelOpen: Boolean = false,
    onModelPanelOpenChange: (Boolean) -> Unit = {}
) {
    // 桌面：输入框可能**带着焦点被移出组合**（切页/退出会话）——onFocusChanged 不保证在
    // 销毁时回调，这里兜底清掉让位标志，否则切走后全局 Ctrl+B 会一直让位收不了栏。
    if (isDesktopLayout) {
        DisposableEffect(Unit) { onDispose { DesktopShortcuts.textFieldFocused = false } }
    }
    // 手势处理器用 pointerInput(Unit) 保持稳定（**不能**以 voicePhase 为 key：
    // 状态一变就会重启手势协程，松手事件随之丢失，录音会一直卡到页面销毁）
    val startNow by androidx.compose.runtime.rememberUpdatedState(onVoiceStart)
    val endNow by androidx.compose.runtime.rememberUpdatedState(onVoiceEnd)
    // 输入框改由 TextFieldValue 承载——「填括号」要插在**光标处**、并把光标落回括号中间，
    // 这是 String 给不了的信息（它只有文本、没有选区）。外部改动（灵感填入／语音识别结果／发送后清空）
    // 由下面这行同步进来，光标落到末尾。
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    // 「模型与思考强度」＝输入条自带的**锚定浮层**（第 193 轮）。开关第 194 轮提到 VM：
    // 工具列表里也放了一格同名入口，两处必须开同一个浮层（局部状态做不到"从别处打开它"）。
    // chip 与面板同源的设置快照：打开时重读（设置页可能在别处改过），面板提交后回调刷新
    var aiSettings by remember { mutableStateOf(Repository.loadSettings()) }
    // 从工具列表那一格打开时 chip 没被点过 ⇒ 快照要跟着开合再刷一次，否则面板里显示的是进页面时那份
    LaunchedEffect(modelPanelOpen) {
        if (modelPanelOpen) aiSettings = Repository.loadSettings()
    }
    // 生效值（第 194 轮）：会话覆盖优先，缺省回退全局。chip 与面板两处都读它，
    // 否则会出现"chip 写着全局模型、面板里勾在另一个模型上"这种自相矛盾。
    val effModel = sessionModel?.takeIf { it.isNotBlank() } ?: aiSettings.chatModel
    val effThinking = sessionThinking?.takeIf { it.isNotBlank() } ?: aiSettings.chatThinking
    // 这一局是否被单独设过（chip 上给个标记，用户才知道"这条会话跟别人不一样"）
    val sessionScoped = !sessionModel.isNullOrBlank() || !sessionThinking.isNullOrBlank()
    // chip 上的思考档显示名：档位表里有就用中文标签，没有（服务商没适配）就照原值，空则不显示这一段
    val thinkingLabel = remember(aiSettings, effModel, effThinking) {
        ProviderProfiles.thinkingLevels(aiSettings.chatBaseUrl, effModel)
            .firstOrNull { it.mode == effThinking }?.label
            ?: effThinking
    }
    val hasText = field.text.isNotEmpty()
    val keyboard = LocalSoftwareKeyboardController.current
    // 键入模式＝软键盘在屏上。判定由各端给（`Platform.ui.rememberKeyboardVisible`，见那里的说明：
    // `WindowInsets.ime` / `isImeVisible` 在共享层看不到）；桌面恒 false ⇒ 桌面「（）」跟着"有没有文字"走。
    val imeVisible = Platform.ui.rememberKeyboardVisible()
    val typing = imeVisible || hasText
    val focusManager = LocalFocusManager.current
    // 键盘收起（系统返回键 / 点空白 / 点加号先收键盘）后立刻把焦点也放掉：
    // 否则光标继续闪在框里，再点一下字又把键盘弹回来——用户原话「关掉键盘时，光标不要停留在输入框」。
    // 只在"先亮过再灭"时才清，避免进页面时误清（首次组合 imeVisible 就是 false）。
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasVisible = true
        } else if (imeWasVisible) {
            imeWasVisible = false
            focusManager.clearFocus()
        }
    }

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
        // 底排键**只有图标、不带文字**（用户 10-09：有侧边栏时那一排本来就被挤成纯图标，
        // 索性统一成纯图标；文字说明走 contentDescription，读屏与冒烟照旧能指认）
        val voiceBusy = voicePhase == ChatViewModel.VoicePhase.RECORDING ||
            voicePhase == ChatViewModel.VoicePhase.LISTENING
        // 两端都是**悬浮卡片**（用户 2026-10-01：手机先改，桌面「也做悬浮的」）：
        // 外圈透明、卡片自己带圆角＋发丝线＋一点透明度、四周留气口；
        // 工具/加号面板仍挂在卡片**内部**（＝容器顶起输入框、与输入行合成同一块、中间不画线）。
        Surface(color = Color.Transparent, tonalElevation = 0.dp) {
            // 键盘/导航栏的留白加在**整块**（图片条＋输入栏＋抽屉）上，不是只加给输入栏那一行：
            // 抽屉是输入栏的**下一个兄弟**，留白要是只加在输入栏上，抽屉就落在键盘背后——
            // 用户点了「工具」却什么都点不到（点到的其实是键盘），这是台账 84 追到的真身。
            Column(Modifier.navigationBarsPadding().imePadding()) {
                // 卡片＝**真毛玻璃**（`WhaleFrostedPanel`：圆角＋发丝线＋6dp 阴影＋模糊背景＋薄纱）。
                // 上一版只写了 `surface.copy(alpha = 0.92f)`——那只是块半透明白板、一点模糊都没有，
                // 用户一眼看穿：「你现在这也不是毛玻璃啊」（2026-10-01）。Compose 的 blur 糊不到
                // "背后已经画好的东西"，所以必须走这个原语：它在浮层自己身上把同一张背景图重画一遍再糊。
                WhaleFrostedPanel(
                    backgroundUri = backgroundUri,
                    shape = WhaleShapes.r3,   /* r4 已退役：原型只有 6/12/18 三档（交接书 §7.4） */
                    tintAlpha = WhaleGlass.card,
                    // 不投影（用户 2026-10-01「输入框这个卡片不要加阴影」）；
                    // 左右各 20dp＝"再窄一点"（觉得还不够窄/太窄，动这一个数）
                    shadowElevation = 0.dp,
                    modifier = Modifier.padding(
                        start = WhaleSpacing.xl, end = WhaleSpacing.xl,
                        top = WhaleSpacing.sm, bottom = WhaleSpacing.sm
                    )
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
                                        .clip(WhaleShapes.r1)
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
                        if (isDesktopLayout) {
                            // ── 桌面大输入框（用户 2026-10-01 二次反馈：参考微信桌面版）──────
                            // 上面一大片留给输入、功能键**全部沉到下面一行**——上一版把键与输入框
                            // 挤在同一行（输入区只剩中间一小段），被用户当场否掉。参考图的形状就是：
                            // 高输入区 ＋ 底排图标 ＋ 右侧一颗灰「发送」。
                            // 手机那套「键位状态机」不搬过来：宽条一直显示发送键（空输入置灰），
                            // 否则键位忽隐忽现，桌面上一排键会左右跳。
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                ChatInputField(
                                    field = field,
                                    onFieldChange = { field = it; onValueChange(it.text) },
                                    placeholder = placeholder,
                                    maxLines = 8,
                                    // 用户 2026-10-03「这里的工具栏低一些」：120dp 的留白太空，
                                    // 收到 72dp——两行舒服放下，多行输入时仍可长高（maxLines=8 上限不变）。
                                    minHeight = 72.dp,
                                    modifier = Modifier.fillMaxWidth(),
                                    keys = Modifier.desktopKeys(
                                        sending = sending,
                                        canSend = field.text.isNotBlank(),
                                        onNewline = { applyText(field.text + "\n", field.text.length + 1) },
                                        onBracket = { insertBrackets() },
                                        onSend = onSend
                                    )
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    InputBarKey(
                                        icon = Icons.Filled.Tune,
                                        label = "工具",
                                        selected = toolOpen,
                                        onClick = onToolClick
                                    )
                                    InputBarKey(
                                        icon = Icons.Filled.Mic,
                                        label = "语音输入",
                                        selected = voiceBusy,
                                        tint = if (voiceBusy) MaterialTheme.colorScheme.error else null,
                                        busy = voicePhase == ChatViewModel.VoicePhase.TRANSCRIBING,
                                        onClick = { if (voiceBusy) endNow() else startNow() }
                                    )
                                    InputBarKey(
                                        icon = Icons.Filled.Image,
                                        label = "发送图片",
                                        onClick = onPickImage
                                    )
                                    // 右半边：灵感回复／模型与思考强度／发送（与参考图同侧）
                                    Spacer(Modifier.weight(1f))
                                    // 形态开关：只有陪伴/多线有灵感回复；工具/玩法整只键不出现。
                                    if (inspirationEnabled) {
                                        InputBarKey(
                                            icon = Icons.Filled.AutoAwesome,
                                            label = "灵感回复",
                                            selected = inspirationOpen,
                                            onClick = onInspiration
                                        )
                                    }
                                    // 模型 chip（193）：浅色胶囊「<模型> <思考档>」＋⌃，取代原那只
                                    // 「模型与思考强度」图标键（用户 2026-10-01 参考图）；
                                    // 点它开的是输入卡**上方**的锚定浮层（见 BoxWithConstraints 末尾那段），
                                    // 不是 AlertDialog——不遮输入框、无灰幕，调参时聊天区照常可见。
                                    ModelThinkingChip(
                                        model = effModel,
                                        thinking = thinkingLabel,
                                        // 会话级覆盖时给个标记：否则"这条会话用的是别的模型"完全看不出来
                                        sessionScoped = sessionScoped,
                                        open = modelPanelOpen,
                                        onClick = {
                                            aiSettings = Repository.loadSettings()
                                            onModelPanelOpenChange(!modelPanelOpen)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    // 发送＝**图标键**（用户 10-09：只要图标、不要文字，与左边那排键同一套语法）。
                                    // 实底只在"现在发得出去"时亮起（空输入/生成中＝灰底灰图标），
                                    // 一眼看出这键此刻按不按得动——比文字键更好认，也不再随语言变宽窄。
                                    // 无障碍名仍是「发送」（contentDescription），冒烟/读屏照旧指认得它。
                                    val canSend = !sending && field.text.isNotBlank()
                                    Surface(
                                        onClick = { if (canSend) onSend() },
                                        enabled = canSend,
                                        shape = WhaleShapes.r1,
                                        color = if (canSend) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    ) {
                                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "发送",
                                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                Modifier
                                    .fillMaxWidth()
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
                    ChatInputField(
                        field = field,
                        onFieldChange = { field = it; onValueChange(it.text) },
                        placeholder = placeholder,
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                        keys = Modifier.desktopKeys(
                            sending = sending,
                            canSend = field.text.isNotBlank(),
                            onNewline = { applyText(field.text + "\n", field.text.length + 1) },
                            onBracket = { insertBrackets() },
                            onSend = onSend
                        )
                    )
                    Spacer(Modifier.width(6.dp))
                    // 右侧键位（用户 2026-09-27 口径，覆盖 09-26 那版）：
                    // · **键盘一唤起就只剩「（）」与发送**——麦克风与圆形加号让位，收起键盘（点空白/系统返回）
                    //   才回来；理由是敲字时这两只键够不着也占地方，而"按住说话"本就该在没键盘时用；
                    // · **录音中例外**：手指还压在麦克风上（按住说话），这一格一撤，松手事件就随组合一起没了、
                    //   录音会一直卡到页面销毁（见上面 startNow/endNow 那段注释），所以 [voiceBusy] 时照常渲染；
                    // · **「（）」进键入模式就出现**（软键盘在屏上，或已有文字），不再要求先打一个字；
                    // · **发送键只在有文字时渲染**（空输入不渲染发送键＝微信同款）；
                    // · **发图键去掉**——图片一律走加号里的「从相册选图」，那里本来就带"模型不支持视觉"的置灰口径。
                    val micAndPlus = !imeVisible || voiceBusy
                    if (typing) {
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
                    }
                    if (micAndPlus) {
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
                        // 圆形加号：微信式互动面板入口
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
                    if (hasText) {
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
                    }
                }
                // 手机：抽屉挂在输入栏下方＝容器顶起输入框（桌面走下面的锚定浮层，不占聊天区）；
                // 抽屉与输入行同属**一块**悬浮卡片，所以面板不再自己画分隔线（divider = false）
                if (!isDesktopLayout) {
                    when {
                        plusOpen -> ChatPlusPanel(plusCells, Modifier.height(panelHeight), divider = false)
                        toolOpen -> ToolCardsPanel(toolCells, Modifier.height(panelHeight), divider = false)
                        // 「模型与思考强度」手机同款抽屉（210 轮补）：桌面走上面的锚定浮层，
                        // 手机只有工具列表那一格入口，但抽屉分支一直漏了它 ⇒ 点了没有任何反应。
                        // 与工具卡同容器（顶起输入框、约占 1/3 屏）；二级模型清单自带滚动，
                        // 外面再包一层滚——根页比抽屉矮时整块居上，清单页超高时才滚。
                        // 50 轮：撤 210 那颗 ✕，收起改点聊天区空白（收口层在 `ChatScreen` 内容区）
                        modelPanelOpen && !plusOpen && !toolOpen -> Column(
                            Modifier
                                .fillMaxWidth()
                                .height(panelHeight)
                                .verticalScroll(rememberScrollState())
                        ) {
                            ModelThinkingPanel(
                                settings = aiSettings,
                                sessionBaseUrl = sessionBaseUrl,
                                sessionModel = sessionModel,
                                sessionThinking = sessionThinking,
                                onChanged = { aiSettings = it },
                                onSessionChanged = { b, m, t -> onSessionOverride(b, m, t) },
                                onDismiss = { onModelPanelOpenChange(false) }
                            )
                        }
                    }
                }
                        }
                    }
                }
            }
        }
        // 桌面：锚定输入栏上方、右对齐的浮层——调参要能边看聊天区边改
        if (isDesktopLayout) {
            // 桌面：锚定输入栏上方、右对齐的浮层——调参要能边看聊天区边改。
            // 宽度与**输入卡**对齐（用户 2026-10-01「宽度要和输入框保持一致」）：
            // 卡在 BoxWithConstraints 里左右各缩 WhaleSpacing.xl，面板同口径（定宽 + 右缘同缩）
            if (plusOpen) DesktopAnchoredPanel(
                onDismiss = onPlusToggle,
                width = maxWidth - WhaleSpacing.xl * 2,
                endInset = WhaleSpacing.xl
            ) { ChatPlusPanel(plusCells) }
            if (toolOpen) DesktopAnchoredPanel(
                onDismiss = onToolClose,
                width = maxWidth - WhaleSpacing.xl * 2,
                endInset = WhaleSpacing.xl
            ) { ToolListPanel(toolCells) }
            // 「模型与思考强度」同款锚定浮层：与工具/互动面板互斥（都锚在同一位置），
            // 选中即 `commit` 收起；点外面/Esc 由 DesktopAnchoredPanel 的 focusable Popup 关闭
            if (modelPanelOpen && !plusOpen && !toolOpen) {
                DesktopAnchoredPanel(
                    onDismiss = { onModelPanelOpenChange(false) },
                    width = maxWidth - WhaleSpacing.xl * 2,
                    endInset = WhaleSpacing.xl
                ) {
                    ModelThinkingPanel(
                        settings = aiSettings,
                        sessionBaseUrl = sessionBaseUrl,
                        sessionModel = sessionModel,
                        sessionThinking = sessionThinking,
                        onChanged = { aiSettings = it },
                        onSessionChanged = { b, m, t -> onSessionOverride(b, m, t) },
                        onDismiss = { onModelPanelOpenChange(false) }
                    )
                }
            }
        }
    }
}

/**
 * 会话历史：**居中卡片**（用户 2026-10-10 口径 240：容器换成与「本会话设定」「聊天背景」同族的
 * M3 `AlertDialog`；2026-09-26 的"不要从底部拉起的抽屉"口径不变）。标题与「新会话」固定，
 * 列表自己滚；返回键与桌面 Esc 由容器的 `onDismissRequest` 接。
 */
@Composable
internal fun HistoryDialog(
    conversations: List<Conversation>,
    currentId: String?,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("会话历史")
                TextButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新会话")
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
