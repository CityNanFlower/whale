package com.mysticat.roleplay.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleMotion
import kotlinx.coroutines.launch

/**
 * 横滑露出删除（用户 2026-10-02 拍板）。**聊天记录与素材库共用同一份**（10-04 抽出，
 * 此前只长在 [com.mysticat.roleplay.ui.screens.ConversationListScreen] 里）。
 *
 * 四条铁律：**滑出来的就是一块红底＋一个垃圾桶图标**（不是"删除"两个字——用户看图说
 * 滑动后右侧应该是红色填充＋图标）；**不做出手即删**——滑到底只是把按钮露出来，还得再点一次
 * 那图标才进确认；松手没过阈值一律回弹；执行走 R6（danger 色 ＋ 二次确认，由调用方弹）。
 *
 * ⚠ **第 207 轮返工：前两版这块"看着改过、实物上什么都没变"。** 根因是删除带用了
 * `Modifier.fillMaxHeight()`——LazyColumn 的 item 槽给的是**无界高度**（`maxHeight = Infinity`），
 * `fillMaxHeight` 在无界约束下退化成 `minHeight`（0），于是那个 92dp 宽的删除带量出来是
 * **0 高**：垃圾桶图标连同它的点击区一起消失，红底也只剩一条 0 像素的边。
 * 冒烟 `assert 删除会话` 一直绿着，因为那断言命中的是 `⋯` 菜单里同名的文字节点，不是图标。
 *
 * 改法两条：① 整条红带改成 `matchParentSize()`——它按父级（行）已定下的尺寸铺满行高，
 * 不参与定尺、也就不受无界高度影响；② 图标的透明度与横向位置一律在 `graphicsLayer` 里按
 * `offset` 现算，**只重画不重组**，手指拖到一半就有半条红带＋一个正在显形的图标。
 *
 * ⚠ 抽成共享组件时**没有**顺手"改进"这四条手势规则——两处页面的手感必须一致，
 * 任何一侧调参都会让另一侧变样；要改连同两端一起改，并回写这里。
 *
 * @param revealed 是否停在"删除已露出来"的位置（唯一驱动位移的开关：手势、点行收起、
 *   删除后取消全走这一条；`onRevealedChange` 由调用方自己 `remember` 持有）。
 * @param onArmedDelete 点了露出来的垃圾桶——调用方在这里收起并弹确认框。
 * @param deleteContentDescription 图标的 content-desc。⚠ **别与弹出确认框里的按钮文案同名**：
 *   冒烟断言按 text／content-desc 匹配，两处同名时断言会命中确认框里的文字节点，
 *   图标坏掉它照样绿（第 205 轮踩过）。
 * @param shape 行本身的形状（10-04 加）。**红带铺满整行，所以它必须与前景行同形**——
 *   素材库的行是 12dp 圆角，会话记录的行是方的；不给这个参数时红带会从四个圆角缺口透出来，
 *   变成每行四颗红三角（用户 10-04 报「透出背后的红了」）。取值＝**前景行自己 clip 的那个形状**。
 * @param content 前景那一行（**自带不透明背景**，否则会透出下面的红带）。
 */
@Composable
fun SwipeRevealDelete(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    onArmedDelete: () -> Unit,
    deleteContentDescription: String = "删除",
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit
) {
    val pal = LocalWhalePalette.current
    val scope = rememberCoroutineScope()
    val revealWidth = 92.dp
    val maxPx = with(LocalDensity.current) { revealWidth.toPx() }
    val offset = remember { Animatable(0f) }

    // 位移只由 `revealed` 这一个开关驱动：手势、点行收起、删除后取消，全走这一条。
    // （第 205 轮修：点「删除」进确认又取消时只清了开关、没复位 offset ⇒ 行永远停在偏移位置。）
    LaunchedEffect(revealed) {
        offset.animateTo(if (revealed) -maxPx else 0f, WhaleMotion.quick())
    }

    Box(
        Modifier
            .fillMaxWidth()
            // ⚠ 纸色底与红带都要按 [shape] 裁：素材库的行是 12dp 圆角，只裁红带不裁纸色底
            // 会留下四个"纸色方角＋红带圆角"的错位缺口（反过来只裁纸色底则四角直接漏红）。
            // 会话记录的行是方的 ⇒ [RectangleShape] ＝ 零效果，那边一行没变。
            .clip(shape)
            .background(pal.paper)
    ) {
        // 底层＝整条红带。前景是不透明的纸色行且只往左滑，所以"红带铺满行宽"看上去就是
        // 右边露出的一条——而且宽度跟着手指实时变，不需要另算裁剪。
        Box(
            Modifier
                .matchParentSize()
                .background(pal.danger)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(revealWidth)
                    .fillMaxHeight()
                    .clickable(enabled = revealed, onClick = onArmedDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = deleteContentDescription,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val shown = (-offset.value).coerceIn(0f, maxPx)
                            // 跟着可见红带走：红带只有一半时图标右移回带心，别悬在纸面上
                            translationX = (maxPx - shown) / 2f
                            // 滑开约 1/6 就开始显形、到 45% 完全显形（不"等落位才出现"）
                            alpha = ((shown / maxPx - 0.16f) / 0.29f).coerceIn(0f, 1f)
                        }
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offset.snapTo((offset.value + delta).coerceIn(-maxPx, 0f))
                        }
                    },
                    onDragStopped = {
                        // 没过阈值一律回弹：横滑只是"把破坏性动作露出来"，不是"准备执行"。
                        // 这里只翻开关，落位交给上面的 LaunchedEffect —— 两处各动一次位移就会打架。
                        onRevealedChange(offset.value < -maxPx * 0.45f)
                    }
                )
        ) { content() }
    }
}