package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.tokens.WhaleGlass
import com.mysticat.roleplay.ui.tokens.WhaleShapes

/**
 * 毛玻璃原语（交互语法里的"浮层"这一类都从这里走）。
 *
 * **为什么不是真·背景模糊**：Compose 的 `Modifier.blur` 模糊的是**自己这一层的内容**，
 * 拿不到"底下已经画好的那一层"（要做真 backdrop-filter 得引 haze 那类第三方库，
 * 而它在桌面端与 Android 12 以下的行为都不稳）。所以这里的做法是：
 * **把同一张聊天背景图再画一遍、画在浮层自己身上并模糊掉**——
 * 视觉结果与 backdrop-filter 一致，代价只是多一次图片解码（coil 有内存缓存，不重复读盘）。
 *
 * ⚠ 真模糊只在 **Android 12（API 31）以上与桌面端**生效；更低的系统上 `blur` 是空操作，
 * 此时靠 [WhaleGlass.frost] 那层半透明底仍然读得出"浮起来"的质感——
 * 所以**不许**把 tint 透明度调得很高来"省一次绘制"，那样低版本上就成了一块实心板。
 */

/** 一层压在玻璃底下的聊天背景（模糊过）。[uri] 为空时什么都不画，由调用方的 tint 兜底。 */
@Composable
fun WhaleFrostedImage(
    uri: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = WhaleGlass.blurBar
) {
    // 按图归一：玻璃底下的图与聊天区同一份归一，blur 之前的色调就已在主题带里
    com.mysticat.roleplay.ui.BgNormalizedImage(
        uri, null, modifier, blurRadius = blurRadius
    )
}

/**
 * 毛玻璃浮层容器：底下垫一层模糊的聊天背景，上面盖主题色 tint ＋ 发丝描边。
 *
 * [tintAlpha] 默认取 [WhaleGlass.frost]；要"更透"（悬浮输入区）就调小，
 * 要"更实"（长按菜单）就调大——**别在调用处写字面色值**（十六进制字面量），颜色永远来自主题。
 */
@Composable
fun WhaleFrostedPanel(
    backgroundUri: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = WhaleShapes.r3,
    tintAlpha: Float = WhaleGlass.frost,
    blurRadius: Dp = WhaleGlass.blurBar,
    /**
     * 投影。默认 6dp（"浮在图上"该有的重量感）；**悬浮输入卡片传 0**——用户 2026-10-01：
     * 「输入框这个卡片不要加阴影」，那张卡靠圆角＋发丝线＋模糊自己立得住，投影反而是一圈脏边。
     */
    shadowElevation: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = shape,
        // 这里必须是**实心**底色：它只负责"底下没有聊天背景图时"的样子。
        // 有图时真正压色的那层画在下面 Box 里、画在图的**上面**——见下面那条层序注释。
        color = cs.surface,
        contentColor = cs.onSurface,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = WhaleGlass.hairline)),
        tonalElevation = 0.dp,
        shadowElevation = shadowElevation
    ) {
        Box {
            // 模糊背景只在真的"浮在图上"时才有意义：没背景图时画了也是空的
            if (backgroundUri != null) {
                WhaleFrostedImage(
                    uri = backgroundUri,
                    blurRadius = blurRadius,
                    modifier = Modifier.matchParentSize()
                )
                // ⚠ **层序就是可读性的全部**（10-01 实测翻车：薄纱画在 Surface 的 containerColor 上，
                //   而图是 Surface 的**内容**、画在它上面 ⇒ 薄纱被图整个盖住，顶栏变成一块"只有模糊图"的板子，
                //   字直接糊进图里）。薄纱必须画在图的**上面**、内容的**下面**，所以它是 Box 的第二个孩子。
                Box(
                    Modifier
                        .matchParentSize()
                        .background(cs.surface.copy(alpha = tintAlpha))
                )
            }
            content()
        }
    }
}

/**
 * 会话顶栏那一条玻璃：**透卡 + 柔边**（用户 2026-10-03：「还是做成透卡，唯一一点就是希望可以
 * 让边界模糊一点，不要过渡得太生硬」；角色名与会话名那一层毛玻璃「做不好就不要做了」）。
 *
 * 与 [WhaleFrostedPanel] 的差别有两条，都是从那张反馈照片倒推出来的：
 * ① **不描边、不投影**。顶栏原来走的是卡片原语：四周 1dp 发丝线 ＋ 6dp 投影压在聊天区上，
 *    投影那一圈脏边就是"生硬"的来源，底下再齐刷刷切一刀，图与玻璃像两张纸叠着。
 *    原型那边（`.mtop` / `.dbar`）本来也只有一根 `border-bottom`。
 * ② **tint 从 [WhaleGlass.frost]（0.90）降到 [WhaleGlass.barTint]（0.62）**——0.90 的半透明底
 *    实到看不出是"透卡"，图基本透不出来。也只降到这儿：顶栏里坐着角色名与会话名。
 *
 * ⚠ **下缘不做熔接带**（用户 2026-10-03："会话顶栏不用加过渡的模糊边界了"）：
 * 218 轮曾在这里挂一段 18dp 的渐隐带（tint → 透明，令牌 `barMelt`）想消掉那条硬边，实物看过后被否——
 * 渐隐带自己就成了一条**看得见的边界**（没背景图时最明显：它把主题底色又涂淡了一层，比原来的直边更难看）。
 * 令牌已一并删除，别再拿回来；那条硬边要治就治玻璃本身，不靠外挂色带。
 *
 * 主题底色一律取 `colorScheme.background` 而不是 `surface`：没背景图时聊天区铺的就是它
 * （`ChatMessages` 的兜底层），两边同色才无缝——这也正是「无聊天背景默认用主题色」那条口径。
 */
@Composable
fun WhaleBarGlass(
    backgroundUri: String?,
    modifier: Modifier = Modifier,
    tintAlpha: Float = WhaleGlass.barTint,
    blurRadius: Dp = WhaleGlass.blurBar,
    content: @Composable () -> Unit
) {
    val tint = MaterialTheme.colorScheme.background.copy(alpha = tintAlpha)
    Box(modifier.fillMaxWidth()) {
        // 没背景图时**先铺实主题底色**再叠 tint：Scaffold 的 containerColor 是透明的，
        // 底下那层不保证就是背景色，半透明 tint 会跟它混出第三种颜色。
        if (backgroundUri.isNullOrBlank()) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
        } else {
            WhaleFrostedImage(
                uri = backgroundUri,
                blurRadius = blurRadius,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(Modifier.matchParentSize().background(tint))
        content()
    }
}

/** 顶栏那一横条的玻璃：铺满宽度、下方两角不圆（与聊天区相接处不留缝）。 */
@Composable
fun WhaleFrostedBar(
    backgroundUri: String?,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    Box(modifier.height(height)) {
        WhaleFrostedImage(
            uri = backgroundUri,
            blurRadius = WhaleGlass.blurBar,
            modifier = Modifier.matchParentSize()
        )
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = WhaleGlass.frost))
        )
        content()
    }
}

/**
 * 弹窗底色：主题 surface 加一点点透明度（用户 2026-10-01 要求弹窗带一点透明度、别是实心板）。
 * 用在 `AlertDialog(containerColor = …)` 上；正文可读优先，所以只到 [WhaleGlass.dialog]。
 */
@Composable
fun whaleDialogContainer(): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = WhaleGlass.dialog)

/** 整页（从聊天页进入的独立页面）背景：把聊天图模糊后铺满，再盖主题底色薄纱。 */
@Composable
fun WhaleBlurredPageBackground(
    backgroundUri: String?,
    veil: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        if (backgroundUri != null) {
            WhaleFrostedImage(
                uri = backgroundUri,
                blurRadius = WhaleGlass.blurPage,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(Modifier.fillMaxSize().background(veil))
    }
}

/** 玻璃上常用的"轻描边卡片"圆角，避免调用处裸写 `RoundedCornerShape(N.dp)`。 */
val WhaleGlassShape: RoundedCornerShape = RoundedCornerShape(18.dp)
