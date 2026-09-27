package com.mysticat.roleplay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 桌面布局层。
 *
 * 用户 2026-09-17 的口径：Windows 版**做成一般桌面应用程序**，不模拟手机端的样子——
 * 共享的是**组件与逻辑**，**布局按平台分支**。所以这里是"分支开关 + 桌面专用外壳组件"，
 * 各页面在自己的布局里读 [isDesktopLayout] 决定怎么排。
 *
 * 判定为什么不用 `expect/actual`：这是**运行时**事实（同一个 JVM 二进制也可能跑在不同壳里），
 * 而且两端包名相同（`com.mysticat.roleplay`），只能靠宿主注入的平台标识——[PlatformUi.platformId]。
 */

/**
 * 是否按桌面形态排版。宿主（desktopApp main）在启动时注入 `platformId()=="desktop"`。
 *
 * 兜底实现返回 `"unknown"`，所以"没注入"会走手机形态（保守方向：窄布局不会挤爆）。
 */
val isDesktopLayout: Boolean
    get() = Platform.ui.platformId() == "desktop"

/** 聊天页左侧会话栏宽度（左右分栏的左栏，已并入三栏外壳的第二栏） */
internal val DesktopConversationRailWidth = 292.dp

/**
 * 气泡最大宽度：手机上 300dp 是"一屏一行半"的舒服值，桌面上太窄（截图/代码块全被挤扁），
 * 放到 560dp——长段落仍然是正常阅读宽度，不会拉成一整行。
 */
internal val chatBubbleMaxWidth get() = if (isDesktopLayout) 560.dp else 300.dp

/**
 * 气泡旁的 bot 头像尺寸。手机上 34dp 与 300dp 宽的气泡是配套的；
 * 桌面上气泡放宽到 560dp、窗口又大一截，34dp 显得过小（用户 2026-09-17 反馈"bot 头像尺寸太小"）。
 */
internal val chatAvatarSize get() = if (isDesktopLayout) 44.dp else 34.dp

/** 侧边栏一项 */


/**
 * 右键（次要键）点击 → 触发上下文菜单，桌面上与"长按"等价。
 *
 * 用 [PointerEventPass.Initial] 且必须挂在链路的**最外层**（`.desktopSecondaryClick{}.combinedClickable{}`）：
 * Initial 是「外→内」派发，我们在这里把事件 consume 掉，内层的 `combinedClickable` 就看不到这次按下——
 * 否则右键会被它当成一次普通 tap（`awaitFirstDown` 不区分按键），松手时会误触 `onClick`。
 *
 * 手机端一律不挂（`isDesktopLayout=false` 时直接返回原 Modifier），Android 行为零变化。
 */
@Composable
fun Modifier.desktopSecondaryClick(onSecondary: () -> Unit): Modifier {
    val callback by rememberUpdatedState(onSecondary)
    if (!isDesktopLayout) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    callback()
                }
            }
        }
    }
}

/**
 * 桌面端把鼠标滚轮映射到横向列表（分类胶囊、灵感创作的类型/标签栏等）。
 *
 * 触摸屏上横滑是天生的；桌面上滚轮默认只滚纵向，横排要么得按住拖、嵌在竖滚容器里甚至拖不动。
 * 滚轮悬停在横排上时把纵向增量转成横向滚动：用 [ScrollableState.dispatchRawDelta]（非挂起、
 * 边界内收敛），按返回值判断是否真滚动了——到头就留给外层竖滚容器继续滚。
 * `ScrollState`（horizontalScroll 的 Row）与 `LazyListState`（LazyRow）都是 [ScrollableState]，一行接一态两用。
 * 手机端一律不挂（`isDesktopLayout=false` 返回原 Modifier），Android 行为零变化。
 */
@Composable
fun Modifier.wheelHorizontalScroll(state: ScrollableState): Modifier {
    if (!isDesktopLayout) return this
    return this.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                val dy = change.scrollDelta.y
                if (dy == 0f) continue
                if (state.dispatchRawDelta(dy) != 0f) change.consume()
            }
        }
    }
}

/** 左右分栏：左窄栏（列表）+ 竖分隔线 + 右侧内容区（内容自己滚） */
@Composable
fun DesktopSplitPane(
    list: @Composable () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listWidth: Dp = DesktopConversationRailWidth
) {
    Row(modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(listWidth)
                .fillMaxHeight()
        ) { list() }
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) { content() }
    }
}

/**
 * 桌面**三栏形态下第三栏**的正文宽度上限。
 *
 * 比整页形态的 1080dp 窄：左右两栏已经各占一截，第三栏再拉满就成了"横着扫一行字"。
 * 720dp ≈ 90 个西文字符/36 个汉字一行的舒服宽度，与手机端的阅读宽度接近但更宽。
 */
internal val DesktopPaneMaxWidth = 720.dp

/**
 * 桌面**网格/看板型页面**的宽度上限。
 *
 * 与 [DesktopPaneMaxWidth] 的分工按**内容形态**分：表单与长文用 720dp（一行 36 个汉字是舒服的阅读宽度，
 * 再宽就要横着扫），而"一排卡片/封面"的看板页正相反——收在 720dp 里只剩一两列，看起来就是手机端。
 * 所以看板页给到 1200dp（≈ 四列 260dp 的最小列宽），窗口再宽也只是不再增长，不会无限拉散。
 */
internal val DesktopGridPaneMaxWidth = 1200.dp

/**
 * 桌面端页面内容的**最大宽度 + 居中**外壳（2026-09-17）。
 *
 * 为什么需要：1440 宽下把一张角色卡/一行设置项拉满整行，读起来要横着扫很久，
 * 也不像桌面软件（桌面应用的正文列通常都有个舒服的宽度上限）。这里统一收窄并居中，
 * 页面自己不用各写一份。
 *
 * **手机端原样返回**（`isDesktopLayout=false` 时不套任何东西），Android 行为零变化。
 *
 * 聊天页不走这里：它有左右分栏与全宽背景图，气泡自己按 [chatBubbleMaxWidth] 收窄。
 */
@Composable
fun DesktopContentBox(
    maxWidth: Dp = 1080.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isDesktopLayout) {
        content()
        return
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .widthIn(max = maxWidth)
                .fillMaxHeight()
        ) { content() }
    }
}

/**
 * 分区卡外壳——桌面上**把"框"去掉**。
 *
 * 用户 2026-09-23 的口径：「桌面端就尽量不要用手机上那种框框了，桌面上简洁一点」，
 * 并自己点出了关键约束——这些卡**全是共享层直写**（不是桌面分支），所以**不能改默认值**
 * （改了 Android 跟着变），只能在这里按 [isDesktopLayout] 分叉：
 * 手机端＝原来那张 M3 卡（surface 底 + 圆角 + 阴影 + 可选描边）；
 * 桌面端＝**分区标题 + 分隔线 + 内容**，不要卡片底色/描边/阴影，靠留白切分（一般桌面软件的做法）。
 *
 * **Android 侧逐像素不变**：调用点把
 * `Card(colors = …, shape = …, elevation = …) { Column(Modifier.padding(P), spacedBy(S)) { … } }`
 * 换成 `SectionCard(shape = …, elevation = …, contentPadding = P, spacing = S) { … }`，
 * 卡内那句标题 `Text(…, titleSmall, SemiBold)` 改为传 `title = "…"`（间距差由 [titleGap] 对齐）。
 *
 * @param title 分区标题。手机端画在卡内首行；桌面端画成卡外的分区标题（下面跟一条分隔线）。
 *   传 `null` ＝这一节在外面已经有标题了（如「用户」页桌面态由 `StreamSectionTitle` 提供），
 *   桌面端**不会**再补一个，免得两层标题叠着。
 * @param titleGap 标题与首行之间的额外间距：设置页那几张卡原来是 `Text` + `Spacer(6.dp)`，
 *   灵感创作页的卡是标题直接接内容（只有 `spacing`）。传 0 即后者。
 * @param bordered 手机端要不要 1dp 描边（设置页的卡有，灵感创作页的没有）。
 * @param contentPadding 卡内边距。桌面端**照给**：这一版只摘"装饰"（底色/描边/阴影/圆角），
 *   内容的落点一个像素都不动——否则整页内容会平移 18dp，与外侧的分区标题也不再对齐。
 *   所以改一处调用点时，原来那句 `Column(Modifier.padding(P), …)` 的 P 要挪到这里，
 *   **不能两头都留**（那会在手机端叠成 2P）。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    elevation: Dp = 3.dp,
    bordered: Boolean = false,
    contentPadding: Dp = 18.dp,
    spacing: Dp = 4.dp,
    titleGap: Dp = 6.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!isDesktopLayout) {
        Card(
            modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            border = if (bordered) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
        ) {
            Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (titleGap > 0.dp) Spacer(Modifier.height(titleGap))
                }
                content()
            }
        }
        return
    }
    Column(modifier.padding(contentPadding)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(
                Modifier.padding(top = 6.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/**
 * 列表项外壳：`OutlinedCard` 那种"一项一个描边框"的列表行。
 *
 * 桌面端**整块壳都不要**（描边 + 圆角 + 卡片底），内容平铺在页面上——列表靠行间距切分，
 * 与桌面软件的文件列表一致。（发现页第三栏是自适应多列网格，一项一个描边框就是那个"手机味"。）
 *
 * Android 侧**直接委派给 [OutlinedCard]**（不是拿 `Card` 重建它的参数），所以逐像素一致、零回归风险。
 */
@Composable
fun ListItemCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isDesktopLayout) {
        OutlinedCard(modifier) { content() }
        return
    }
    Box(modifier) { content() }
}

/**
 * 桌面端「锚定浮层」：贴着调用处所在 composable 的**上方**弹出、右边缘对齐。
 *
 * 聊天页的调参面板（工具卡片、互动面板）在桌面上**不顶起输入框、也不整页盖住聊天区**——
 * 桌面窗口大，调参时要能边看聊天区边改；手机那边才是"顶起输入框的三分之一屏"。
 * 锚点是 Popup 的父节点（谁调用它，谁就是锚点）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopAnchoredPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 380.dp,
    content: @Composable () -> Unit
) {
    val gapPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(gapPx) { AboveEndPositionProvider(gapPx) },
        onDismissRequest = onDismiss,
        // focusable：点浮层外面能关掉（Esc 也走这里）
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = modifier.widthIn(min = 260.dp, max = maxWidth),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) { content() }
    }
}

/** 浮层放在锚点上方、右边缘对齐（上方不够就贴窗口顶边）；横向不越出窗口 */
private class AboveEndPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        val above = anchorBounds.top - popupContentSize.height - gapPx
        return IntOffset(x, above.coerceAtLeast(0))
    }
}
