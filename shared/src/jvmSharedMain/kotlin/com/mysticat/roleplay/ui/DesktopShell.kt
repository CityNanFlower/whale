package com.mysticat.roleplay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.screens.CharacterEditorScreen
import com.mysticat.roleplay.ui.screens.ChatScreen

/**
 * 桌面三栏外壳（第 28 轮＝用户三栏重构方案的第一阶段）。
 *
 * ```
 * ┌──────┬────────────────┬──────────────────────────┐
 * │ 第一栏 │      第二栏       │          第三栏            │
 * │ 64dp  │ 可调宽 / 可收起   │       占满剩余宽度         │
 * │ 纯图标 │    页面级侧栏     │        当前内容           │
 * └──────┴────────────────┴──────────────────────────┘
 * ```
 *
 * 为什么改（2026-09-17 定的方案与取舍）：
 * 原来的"侧边栏 + 内容区"两栏把筛选/列表/内容全堆在一列里，桌面上既浪费横向空间，
 * 又要"点进去再返回"（手机上换会话退回列表页是必要的，桌面上很别扭）。三栏把
 * 「去哪一页（第一栏）/ 这一页里选哪个（第二栏）/ 看什么（第三栏）」拆开，
 * 是 IM/邮件类桌面软件的通用形态。
 *
 * 三条硬约束（沿用第 27 轮的教训）：
 * ① **悬停态一律自绘**——M3 组件（`NavigationRailItem`/`FilterChip`）会自己画悬停反馈，
 *    形状配色我们控制不了，所以第一栏不用 `NavigationRail`，一个个 Box 自己搭；
 * ② 收起 / 调宽要**记住**（落进 `window.properties`，见 desktopApp 的 `WindowPrefs`）；
 * ③ 手机端一行都不碰：本文件所有入口只在 `isDesktopLayout` 为真时被调用。
 */

/** 第二栏宽度与收起状态（进程内单例；持久化由 desktopApp 的 WindowPrefs 读写） */
object DesktopShellPrefs {    const val DEFAULT_RAIL_WIDTH = 288
    const val MIN_RAIL_WIDTH = 208
    const val MAX_RAIL_WIDTH = 460

    /** 第二栏宽度（dp）。拖动手柄改它，拖完/收起时经 [onPersistRequested] 即时落盘（不再等正常退出） */
    var railWidth: Int = DEFAULT_RAIL_WIDTH

    /** 第二栏是否收起（收起后第三栏占满） */
    var railCollapsed: Boolean = false

    /** 宿主注入的"把窗口状态落盘"回调（Main.kt 设）；拖完手柄与切换收起时调，崩溃也不丢调整 */
    var onPersistRequested: (() -> Unit)? = null

    fun clamp(width: Int): Int = width.coerceIn(MIN_RAIL_WIDTH, MAX_RAIL_WIDTH)
}

/**
 * 桌面第一栏底部的应用 logo（M11 ③）：共享层引用不到 desktopApp 的资源文件，
 * 由 Main.kt 用 painterResource 注入；默认 null（Android 不会走到第一栏，桌面忘注入时也不画）。
 */
val LocalDesktopLogo = staticCompositionLocalOf<androidx.compose.ui.graphics.painter.Painter?> { null }

/** 第一栏的一项（纯图标 + 悬停浮出的文字提示） */
data class DesktopNavEntry(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    /** 这一页的第二栏"有内容但收起了"——图标下方点一个小圆点（用户方案 2.5） */
    val railHidden: Boolean = false,
    val onClick: () -> Unit
)

/** 第三栏要开的会话（桌面不再"跳页"，直接在第三栏换内容） */
data class DesktopChatTarget(val characterId: String, val conversationId: String)

/** 第一栏宽度：固定 64dp，不支持调整（用户方案一） */
private val NavRailWidth = 64.dp

/**
 * 第一栏：常驻纯图标主导航。
 *
 * 与手机底部导航的差别不只是位置：桌面上**没有文字标签**（用户要求，参考微信），
 * 换成**悬停浮出的 tooltip**——鼠标能悬停正是桌面比手机多出来的信息通道。
 * 选中态＝「图标高亮 + 左侧 3dp 强调色竖条」，去掉手机端那种大圆角背景块。
 */
@Composable
fun DesktopNavRail(
    entries: List<DesktopNavEntry>,
    onSettings: () -> Unit,
    /** 顶部的头像：点开「用户」页的「账号」分组（M11 ③ 用户拍板的入口改造） */
    onAccount: () -> Unit,
    /** 底部的应用 logo：点开「用户」页的「版本与安全」分组（原头像位，头像挪去了顶部） */
    onLogo: () -> Unit,
    avatarUri: String?,
    profileName: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .width(NavRailWidth)
            .background(colors.surfaceContainerLow),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        // 第一栏顶部（M11 ③ 用户原话）：原先的"鲸"字标记换成**账号头像**，点进去是「账号」设置页。
        // "这是鲸鱼"的锚点职责交给底部 logo 与自绘标题栏（窗口有标题文字了）
        DesktopNavAvatar(avatarUri, profileName, onAccount)
        Spacer(Modifier.height(12.dp))
        entries.forEach { entry -> DesktopNavIcon(entry) }
        Spacer(Modifier.weight(1f))
        // ⚠ 这里**必须**是 HorizontalDivider（横线）：早先用 VerticalDivider + width(24dp)，
        // 而 VerticalDivider 自带 `fillMaxHeight()`，在 Column 里会把自己撑到整列高，
        // 结果把下面的设置齿轮与头像整个挤出可视区（第 28 轮截图验收时才发现的）。
        HorizontalDivider(
            Modifier
                .width(28.dp)
                .padding(vertical = 8.dp),
            color = colors.outlineVariant.copy(alpha = 0.6f)
        )
        // 设置从角色卡页右上角搬到常驻第一栏（用户方案一）
        DesktopNavIcon(
            DesktopNavEntry(
                icon = Icons.Filled.Settings,
                label = "设置",
                selected = false,
                onClick = onSettings
            )
        )
        Spacer(Modifier.height(6.dp))
        // 原先头像的位置换**鲸鱼应用 logo**，点进去是「版本与安全」设置页（用户原话）
        DesktopNavLogo(onLogo)
        Spacer(Modifier.height(10.dp))
    }
}

/** 第一栏的一个图标位：48dp 命中区 + 圆角底 + 左侧 3dp 竖条 + 悬停 tooltip */
@Composable
private fun DesktopNavIcon(entry: DesktopNavEntry) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧强调色竖条（微信式选中指示）
            Box(
                Modifier
                    .width(3.dp)
                    .height(if (entry.selected) 26.dp else 0.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(if (entry.selected) colors.primary else Color.Transparent)
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 5.dp, horizontal = 7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            entry.selected -> colors.secondaryContainer
                            hovered -> colors.surfaceContainerHighest
                            else -> Color.Transparent
                        }
                    )
                    // indication = null：按下反馈由上面自绘的底色承担（M3 涟漪在自绘底上会画出多余形状）
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = entry.onClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    entry.icon,
                    contentDescription = entry.label,
                    modifier = Modifier.size(21.dp),
                    tint = when {
                        entry.selected -> colors.onSecondaryContainer
                        hovered -> colors.onSurface
                        else -> colors.onSurfaceVariant
                    }
                )
            }
        }
        if (entry.railHidden) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
        }
        // 这里**刻意不显示文字**（用户 2026-09-18 反馈）：悬停浮出的提示是独立 `Popup` 窗口，
        // 鼠标一停上去它就盖住图标、把悬停状态顶掉，于是提示忽隐忽现（闪烁）而且点击常常打在它身上。
        // 图标含义靠 `contentDescription`（读屏）与"与手机端底栏同一套图标、同一顺序"来承担。
    }
}

@Composable
private fun DesktopNavAvatar(avatarUri: String?, name: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // 名字首字永远先画在底层：头像**加载失败**（路径失效、图被删、跨平台迁过来的旧路径）
            // 时不至于留一个空圈——AsyncImage 加载成功会盖住它
            Text(
                name.take(1).ifBlank { "我" },
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant
            )
            if (!avatarUri.isNullOrBlank()) {
                AsyncImage(
                    model = imageModel(avatarUri),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
    }
}

/** 第一栏底部的应用 logo 位（M11 ③）：画 [LocalDesktopLogo] 注入的鲸鱼图标，点开「版本与安全」 */
@Composable
private fun DesktopNavLogo(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val logo = LocalDesktopLogo.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (hovered) colors.surfaceContainerHighest else Color.Transparent)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (logo != null) {
                Image(
                    painter = logo,
                    contentDescription = "版本与安全",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(27.dp)
                )
            } else {
                // 没注入 logo（不该发生）：退回"鲸"字，保住入口不消失
                Text("鲸", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * 第二栏 + 第三栏（含中间的可拖拽手柄）。
 *
 * [collapsed] 为真时第二栏整列隐藏，第三栏占满——左栏对应图标下的小圆点负责提示"这里还有内容"。
 */
@Composable
fun DesktopRailPane(
    rail: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    railWidth: Dp = DesktopShellPrefs.railWidth.dp,
    collapsed: Boolean = false,
    onResize: (Float) -> Unit = {},
    onResizeEnd: () -> Unit = {},
    onToggleCollapse: () -> Unit = {}
) {
    Row(modifier.fillMaxSize()) {
        if (!collapsed) {
            Box(
                Modifier
                    .width(railWidth)
                    .fillMaxHeight()
            ) { rail() }
            RailResizeHandle(onResize, onResizeEnd, onToggleCollapse)
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) { content() }
    }
}

/**
 * 第二栏右缘的拖拽手柄：命中区 12dp（视觉上仍只有悬停高亮）。
 *
 * 鼠标进来时显一条强调色细线 + 浮出一个折叠箭头（« ）；拖动改宽，点箭头收起，
 * 双击同一条手柄也收起/拉出（桌面上很顺手的组合）。
 *
 * 为什么不把手柄放进第二栏内部：那样"拖到最小宽度"时手柄跟着变窄、手感发散；
 * 独立成兄弟节点，命中区恒定。原先 6dp 太细——用户反馈"没有改宽度的功能"（2026-09-21），
 * 实际是手柄悬停很难停留住；加宽命中区后拖动好找得多。
 *
 * **第 58 轮补上两件事**（用户同日反馈"鼠标放上去光标要变成左右箭头，否则不知道能拖"）：
 * ① 指针变成水平双向箭头（[PlatformUi.horizontalResizeCursorModifier]，Android 无鼠标＝不改）；
 * ② **悬停真的能被看见了**——第 57 轮那版把 `hovered` 只挂在 `onDragStart` 上，
 * 于是高亮与折叠箭头只在"已经拖起来之后"才出现、松手就没了；纯点击（没到拖拽阈值）
 * 根本触发不了 `onDragStart`，那个 → 箭头**一次都点不到**。现在改用 `hoverable` 的悬停态，
 * 拖动中用 `dragging` 顶着，两者取或。
 */
@Composable
private fun RailResizeHandle(
    onResize: (Float) -> Unit,
    onResizeEnd: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    Box(
        Modifier
            .width(12.dp)
            .fillMaxHeight()
            .background(if (active) colors.primary.copy(alpha = 0.22f) else Color.Transparent)
            .hoverable(interaction)
            .then(Platform.ui.horizontalResizeCursorModifier())
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false; onResizeEnd() },
                    onDragCancel = { dragging = false }
                ) { change, drag ->
                    change.consume()
                    onResize(drag.x)
                }
            }
    ) {
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = onToggleCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "收起侧栏",
                    modifier = Modifier.size(14.dp),
                    tint = colors.onPrimary
                )
            }
        }
    }
}

/**
 * 第二栏的一行（第 31 轮抽出，供三栏各页共用同一套观感）。
 *
 * 为什么抽出来：第一阶段里角色卡页自己写了一份 `CategoryRailRow`、会话栏自己写了一份 `RailRow`，
 * 再给灵感创作与「用户」页各写一份就会变成四份近似实现——选中/悬停配色会慢慢漂移。
 * 这里统一「选中＝secondaryContainer + 半粗、悬停＝surfaceContainerHighest、按下无涟漪（自绘底色）」，
 * **悬停态必须自绘**（M3 组件自己的悬停绘制形状配色我们控制不了，第 27 轮的教训）。
 *
 * [count] 是右侧的灰色数字（分类/条数），[trailing] 给需要放按钮的行用。
 */
@Composable
fun DesktopRailRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** 缩进（二级项用） */
    indent: Dp = 0.dp
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> colors.secondaryContainer
                    hovered -> colors.surfaceContainerHighest
                    else -> Color.Transparent
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp + indent, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(9.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
            )
        }
        if (trailing != null) trailing()
    }
}

/** 第二栏里的小节标题（分组之间用；不是可点项） */
@Composable
fun DesktopRailSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

/** 第二栏顶部的页面标题（左侧页名 + 右侧可选动作） */
@Composable
fun DesktopRailHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/**
 * 第三栏里的聊天窗口。
 *
 * 两件事值得说明：
 * 1. **不跳页**——[target] 为 null 时给一段空态提示（左侧会话栏还在，选了就出内容）；
 * 2. **每个会话一个独立的 ViewModelStore**：`ChatViewModel` 是按会话加载的，若共用外层
 *    store，`viewModel()` 会按同一个 key 命中**上一个会话的实例**（切会话切不掉内容）。
 *    这里用 `remember(key)` 建一个随身 store，并在 target 变化时把它 clear 掉
 *    （不然每切一次会话就漏一个 VM：它握着协程、TtsSpeaker 与朗读引擎）。
 */
@Composable
fun DesktopChatHost(
    target: DesktopChatTarget?,
    onEditCharacter: (String) -> Unit,
    onSessionChanged: () -> Unit,
    modifier: Modifier = Modifier,
    /** 第二栏是不是收起了——空态提示照实说，收起时别让用户去左边找会话列表 */
    railCollapsed: Boolean = false
) {
    if (target == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("选一个会话开始聊天", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (railCollapsed) {
                        "点第一栏的「聊天记录」图标可以拉出会话列表"
                    } else {
                        "左侧是会话列表；点标题栏的「+」可以新建一个会话"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    val key = target.characterId + "/" + target.conversationId
    val owner = remember(key) { DesktopViewModelStoreOwner() }
    DisposableEffect(key) {
        onDispose { owner.viewModelStore.clear() }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        ChatScreen(
            characterId = target.characterId,
            conversationId = target.conversationId,
            // 桌面三栏下没有"返回"这一说（左栏随时可切），返回箭头也已在顶栏藏掉
            onBack = {},
            onEditCharacter = { onEditCharacter(target.characterId) },
            onSessionChanged = onSessionChanged
        )
    }
}

/** 只为一个会话而生的 ViewModelStore（见 [DesktopChatHost] 的说明） */
internal class DesktopViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * 第三栏里的角色编辑页（第 34 轮）。
 *
 * 在这之前，"编辑/新建角色"是 `nav.navigate(Routes.EDITOR)` —— 一点就整页跳走、三栏全没了，
 * 正是用户方案 §十 点名的那种"点一下就离开三栏"。现在它与「模型与 API」「语音服务」一样
 * 住在第三栏里：第二栏（分类/搜索）随时可切，返回只是把第三栏换回来。
 *
 * **一张卡一个 ViewModelStore**，同 [DesktopChatHost] 的道理：`CharacterEditorViewModel` 按
 * characterId 加载，而 `viewModel()` 默认拿类名当 key——共用外层 store 就会出现"点乙角色
 * 却显示甲的内容"，而且关掉再打开还会命中上一份**未保存的草稿**。
 *
 * @param closeSignal 外壳请求关闭（切页/点会话）：递增即让编辑器自己走一遍 [requestBack]。
 *        脏不脏只有编辑器知道，所以关闭做成"发个信号"而不是外壳直接清状态——那会静默丢改动。
 */
@Composable
fun DesktopEditorHost(
    characterId: String,
    closeSignal: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val owner = remember(characterId) { DesktopViewModelStoreOwner() }
    DisposableEffect(characterId) {
        onDispose { owner.viewModelStore.clear() }
    }
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            CharacterEditorScreen(
                characterId = characterId,
                onBack = onClose,
                onSaved = onClose,
                embedded = true,
                closeSignal = closeSignal
            )
        }
    }
}
