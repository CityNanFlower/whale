package com.mysticat.roleplay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.Frame

/**
 * M11 ③ 自定义标题栏（第 40 轮）：`Window(undecorated = true)` 去掉系统标题栏后的自绘窗口壳。
 *
 * 为什么是"自绘命中区"而不是 JNA（`WM_NCHITTEST`）：台账 3 动手前先验证的成本提醒——
 * Compose Desktop 不给无边框窗口的 resize 手柄，网上通行做法是引 JNA 改原生命中测试；
 * 这里走纯 Compose 退路（台账原文列出的另一条）：窗口四边/四角放几条透明的"缩放条"，
 * 拖动直接改 `WindowState.size/position`。代价是边缘命中区比原生略厚、Win11 贴靠布局
 * （悬停最大化键弹出）拿不到——先不为此引原生依赖，用户嫌不好用再升级 JNA。
 *
 * 结构（都在 desktopApp，因为要摸 `java.awt.Window`，进不了共享层）：
 *  - [DesktopTitleBar]：顶栏＝拖动移动 + 双击最大化/还原 + 最小化/最大化/关闭三钮；
 *  - [DesktopWindowResizeOverlay]：八方向缩放命中条（最大化时整层不挂）。
 *
 * 几何计算（[applyResize]/[applyMove]）是**纯函数**，`--smoke` 直接单测；
 * Composable 层只做"起点快照 + 累计增量 + 提交"，不掺几何逻辑。
 */

/** 标题栏高度：常见桌面应用的 30~40dp 区间取中，别太高挤内容 */
internal val TitleBarHeight = 34.dp

/** 边缘命中条厚度 / 角部尺寸：太薄难点中，太厚会吃掉贴边内容（滚动条、输入框底）的点击 */
private val EdgeHit = 5.dp
private val CornerHit = 14.dp

/** 窗口几何快照（Compose dp 口径，与 `WindowState.size/position` 同一单位） */
internal data class WindowRect(val x: Dp, val y: Dp, val w: Dp, val h: Dp)

internal enum class ResizeEdge { N, S, E, W, NE, NW, SE, SW }

/**
 * 边缘缩放的纯几何：从拖动起点的窗口矩形出发，按**累计**位移 (dx, dy) 算新矩形。
 * 拖过最小尺寸时不"甩"：把活动边钉在最小值上（对边保持原位，窗口不再跟着手走）。
 */
internal fun applyResize(start: WindowRect, edge: ResizeEdge, dx: Dp, dy: Dp, minW: Dp, minH: Dp): WindowRect {
    var x = start.x
    var y = start.y
    var w = start.w
    var h = start.h
    when (edge) {
        ResizeEdge.E, ResizeEdge.NE, ResizeEdge.SE -> w = (start.w + dx).coerceAtLeast(minW)
        ResizeEdge.W, ResizeEdge.NW, ResizeEdge.SW -> {
            w = (start.w - dx).coerceAtLeast(minW)
            x = start.x + (start.w - w)   // 右缘钉住
        }
        else -> {}
    }
    when (edge) {
        ResizeEdge.S, ResizeEdge.SE, ResizeEdge.SW -> h = (start.h + dy).coerceAtLeast(minH)
        ResizeEdge.N, ResizeEdge.NE, ResizeEdge.NW -> {
            h = (start.h - dy).coerceAtLeast(minH)
            y = start.y + (start.h - h)   // 下缘钉住
        }
        else -> {}
    }
    return WindowRect(x, y, w, h)
}

internal fun applyMove(start: WindowRect, dx: Dp, dy: Dp) =
    WindowRect(start.x + dx, start.y + dy, start.w, start.h)

/**
 * 读当前窗口几何。[WindowState.position] 首次启动（没存过坐标）是 `Aligned`（居中）——
 * 它没有数值可加减，用 AWT 的实际屏幕位置换算成 dp 再开工。
 */
private fun currentRect(state: WindowState, window: java.awt.Window, scale: Float): WindowRect {
    val pos = state.position
    return if (pos is WindowPosition.Absolute) {
        WindowRect(pos.x, pos.y, state.size.width, state.size.height)
    } else {
        val loc = window.locationOnScreen
        WindowRect((loc.x / scale).dp, (loc.y / scale).dp, state.size.width, state.size.height)
    }
}

/** 提交一份几何到窗口状态；position 顺手从 Aligned 落成 Absolute（拖动/缩放都以它为基准） */
private fun WindowState.commit(rect: WindowRect) {
    position = WindowPosition.Absolute(rect.x, rect.y)
    size = DpSize(rect.w, rect.h)
}

/** 一次拖动会话：起点快照 + 累计位移（detectDragGestures 给的是每事件增量） */
private class DragSession {
    var rect: WindowRect? = null
    var accX = 0f
    var accY = 0f
}

/**
 * 自绘标题栏：一条 34dp 顶栏 + 右端三钮。
 * 拖空白处移动窗口；双击切换最大化/还原；最大化时拖动不动（"拖动即还原"要先存还原矩形，暂不做）。
 */
@Composable
fun DesktopTitleBar(
    windowState: WindowState,
    window: java.awt.Window,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val maximized = windowState.placement == WindowPlacement.Maximized
    val session = remember { DragSession() }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TitleBarHeight)
                .background(colors.surfaceContainerLow)
                // 拖动移动窗口：起点先把 Aligned 落成 Absolute，之后每事件在起点上累计
                .pointerInput(windowState) {
                    detectDragGestures(
                        onDragStart = {
                            session.accX = 0f
                            session.accY = 0f
                            if (windowState.placement == WindowPlacement.Floating) {
                                session.rect = currentRect(windowState, window, density.density)
                                windowState.commit(session.rect!!)
                            } else {
                                session.rect = null
                            }
                            com.mysticat.roleplay.ui.DesktopLog.mark("移动开始 start=${session.rect}")
                        },
                        onDragEnd = { session.rect = null },
                        onDragCancel = { session.rect = null }
                    ) { change, drag ->
                        change.consume()
                        val start = session.rect ?: return@detectDragGestures
                        session.accX += drag.x
                        session.accY += drag.y
                        with(density) {
                            windowState.commit(applyMove(start, session.accX.toDp(), session.accY.toDp()))
                        }
                    }
                }
                .pointerInput(windowState) {
                    detectTapGestures(onDoubleTap = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                    })
                }
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 12.dp)
                    .weight(1f)
            )
            CaptionButton("最小化", onClick = {
                (window as? Frame)?.state = Frame.ICONIFIED
            }) { tint ->
                Box(Modifier.width(11.dp).height(1.dp).background(tint))
            }
            CaptionButton(if (maximized) "还原" else "最大化", onClick = {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                    WindowPlacement.Floating
                } else {
                    WindowPlacement.Maximized
                }
            }) { tint ->
                if (maximized) {
                    // 还原钮：前后两个错位的方框（Windows 惯例）
                    Canvas(Modifier.size(11.dp)) {
                        val sw = 1.dp.toPx()
                        drawRect(
                            tint,
                            style = Stroke(sw),
                            topLeft = Offset(size.width * 0.3f, 0f),
                            size = Size(size.width * 0.7f, size.height * 0.7f)
                        )
                        drawRect(
                            tint,
                            style = Stroke(sw),
                            topLeft = Offset(0f, size.height * 0.3f),
                            size = Size(size.width * 0.7f, size.height * 0.7f)
                        )
                    }
                } else {
                    Box(Modifier.size(10.dp).border(1.dp, tint, RectangleShape))
                }
            }
            CaptionButton("关闭", danger = true, onClick = onClose) { tint ->
                Canvas(Modifier.size(11.dp)) {
                    val sw = 1.dp.toPx()
                    drawLine(tint, Offset(1f, 1f), Offset(size.width - 1f, size.height - 1f), strokeWidth = sw)
                    drawLine(tint, Offset(1f, size.height - 1f), Offset(size.width - 1f, 1f), strokeWidth = sw)
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
    }
}

/** 标题栏右端的窗控钮：46×34dp 命中区，关闭钮悬停红底白叉（Windows 惯例）。图标用 Canvas 自绘（不引图标库） */
@Composable
private fun CaptionButton(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable BoxScope.(Color) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var hovered by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxHeight()
            .width(46.dp)
            .semantics { contentDescription = label }
            .background(
                when {
                    hovered && danger -> Color(0xFFC42B1C)
                    hovered -> colors.onSurface.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon(if (hovered && danger) Color.White else colors.onSurfaceVariant)
    }
}

/**
 * 八方向缩放命中条：盖在整个窗口内容之上（四边 5dp + 四角 14dp，角在后画压过边）。
 * 最大化时整层不挂（直接 return），不留"贴边拖不动"的困惑。
 * 空手悬停时给系统缩放光标——用户得知道"这里能缩"。
 */
@Composable
fun DesktopWindowResizeOverlay(
    windowState: WindowState,
    window: java.awt.Window,
    modifier: Modifier = Modifier
) {
    if (windowState.placement == WindowPlacement.Maximized) return
    Box(modifier.fillMaxSize()) {
        ResizeHandle(
            ResizeEdge.N,
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(EdgeHit),
            windowState, window
        )
        ResizeHandle(
            ResizeEdge.S,
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(EdgeHit),
            windowState, window
        )
        ResizeHandle(
            ResizeEdge.W,
            Modifier.align(Alignment.CenterStart).fillMaxHeight().width(EdgeHit),
            windowState, window
        )
        ResizeHandle(
            ResizeEdge.E,
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(EdgeHit),
            windowState, window
        )
        ResizeHandle(ResizeEdge.NW, Modifier.align(Alignment.TopStart).size(CornerHit), windowState, window)
        ResizeHandle(ResizeEdge.NE, Modifier.align(Alignment.TopEnd).size(CornerHit), windowState, window)
        ResizeHandle(ResizeEdge.SW, Modifier.align(Alignment.BottomStart).size(CornerHit), windowState, window)
        ResizeHandle(ResizeEdge.SE, Modifier.align(Alignment.BottomEnd).size(CornerHit), windowState, window)
    }
}

@Composable
private fun ResizeHandle(
    edge: ResizeEdge,
    modifier: Modifier,
    windowState: WindowState,
    window: java.awt.Window
) {
    val density = LocalDensity.current
    val session = remember { DragSession() }
    val cursor = remember(edge) {
        Cursor.getPredefinedCursor(
            when (edge) {
                ResizeEdge.N -> Cursor.N_RESIZE_CURSOR
                ResizeEdge.S -> Cursor.S_RESIZE_CURSOR
                ResizeEdge.E -> Cursor.E_RESIZE_CURSOR
                ResizeEdge.W -> Cursor.W_RESIZE_CURSOR
                ResizeEdge.NE -> Cursor.NE_RESIZE_CURSOR
                ResizeEdge.NW -> Cursor.NW_RESIZE_CURSOR
                ResizeEdge.SE -> Cursor.SE_RESIZE_CURSOR
                ResizeEdge.SW -> Cursor.SW_RESIZE_CURSOR
            }
        )
    }
    modifier
        .pointerHoverIcon(PointerIcon(cursor))
        .pointerInput(edge, windowState) {
            detectDragGestures(
                onDragStart = {
                    session.accX = 0f
                    session.accY = 0f
                    session.rect = currentRect(windowState, window, density.density)
                    windowState.commit(session.rect!!)
                    com.mysticat.roleplay.ui.DesktopLog.mark("缩放开始 $edge start=${session.rect}")
                },
                onDragEnd = {
                    session.rect = null
                    com.mysticat.roleplay.ui.DesktopLog.mark("缩放结束 $edge")
                },
                onDragCancel = {
                    session.rect = null
                    com.mysticat.roleplay.ui.DesktopLog.mark("缩放取消 $edge")
                }
            ) { change, drag ->
                change.consume()
                val start = session.rect ?: return@detectDragGestures
                session.accX += drag.x
                session.accY += drag.y
                with(density) {
                    windowState.commit(
                        applyResize(
                            start, edge,
                            session.accX.toDp(), session.accY.toDp(),
                            WindowPrefs.MIN_W.dp, WindowPrefs.MIN_H.dp
                        )
                    )
                }
            }
        }
}
