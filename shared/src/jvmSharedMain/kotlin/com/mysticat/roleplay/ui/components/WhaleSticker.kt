package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysticat.roleplay.ui.theme.LocalWhaleDark
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleSkin
import com.mysticat.roleplay.ui.tokens.WhaleSkinSpec
import com.mysticat.roleplay.ui.tokens.whaleHex

/**
 * 主题贴纸（交接书 §2.2.1 二次修订口径）：图案＝手账那种小贴纸/图钉/胶带，**不是底纹**。
 *
 * 挂载分两层（用户 2026-10-02 反馈定下的口径：贴纸贴在气泡上，页面标记留在会话界面）——
 * - [WhaleBubbleStickers]：**气泡层**＝图钉＋和纸胶带，钉/贴在**第一条 AI 气泡**（开场白）的本体上
 *   （图钉压着气泡顶边、胶带压左上角，"像钉住一张卡片"；宿主＝气泡的包裹 Box，聊天气泡随它滚动）；
 * - [WhaleStickerOverlay]：**页面层**＝朱印（手账）/邮戳＋火漆（书信）/胶片条＋片基标签（暗房），
 *   挂聊天区容器最后一个孩子，只落**页边**，永不压正文。
 *
 * 硬规则（任何皮肤都绕不开）：
 * - `motif` 只认封闭枚举 `none / sticker / postmark / filmstrip`（[WhaleSkin.motifOf]，坏值回落 none）；
 * - 单页 ≤3 件、组内件数固定（[WhaleStickers.GROUP_SIZES]），不给用户加件；
 * - 全部由预设名驱动 Compose 原语画出，**零图片素材、零体积**；胶片齿孔用画布循环，不用背景图；
 * - 不注册任何 pointerInput ⇒ 不抢点击（对应原型 `.deco{pointer-events:none}`）；
 * - 深色档只调透明度：[WhaleSkinSpec.motifOpacity] ＋0.1 封顶 1；[WhaleSkinSpec.motifSize] 等比缩放全部几何。
 *
 * 几何与配色逐值照抄原型 v7 `.deco`（`.pin/.tape/.seal/.postmark/.wax/.film/.slug`）。
 * 胶片/火漆是**实物**（真实胶片恒黑），其配色明暗两档同值，走 [whaleHex] 字符串——与 [WhaleSkin]
 * 同一解析口径，`ui/` 下不出现"Color 加十六进制"的字面量（R2 起的 grep 闸门）。
 */
object WhaleStickers {
    /** 每个预设的贴纸件数（封闭枚举；组内固定、单页 ≤3 件）。 */
    val GROUP_SIZES: Map<String, Int> = mapOf("none" to 0, "sticker" to 3, "postmark" to 2, "filmstrip" to 2)
}

// ── 实物色（不随皮肤换档；取值照抄原型 v7） ──
private val FILM_STRIP = whaleHex("#141210")
private val FILM_FRAME = whaleHex("#2A2620")
private val FILM_FRAME_EDGE = whaleHex("#4C443A")
private val FILM_HOLE = whaleHex("#EFEAE0")
private val WAX_GLYPH = whaleHex("#FFFFFF")

/** 图钉/火漆的内阴影与投影（CSS rgba(0,0,0,α) 实物阴影，非皮肤令牌）。 */
private val SHADOW_INK = Color.Black.copy(alpha = 0.24f)
private val HIGHLIGHT = Color.White.copy(alpha = 0.8f)
private val TAPE_LINE = Color.White.copy(alpha = 0.75f)

/** 贴纸几何统一乘 [WhaleSkinSpec.motifSize]：原型 v7 的 px 值即 dp 值。 */
private fun stickerDp(s: Float): (Float) -> Dp = { v -> (v * s).dp }

/**
 * 气泡层贴纸：图钉＋和纸胶带（仅 `sticker` 预设，其余 motif 不画）。
 * 宿主＝气泡的包裹 Box（[Modifier.matchParentSize] 传入）；图钉压着宿主顶边居中、胶带压左上角。
 */
@Composable
fun WhaleBubbleStickers(spec: WhaleSkinSpec, modifier: Modifier = Modifier) {
    if (WhaleSkin.motifOf(spec.motif) != "sticker") return
    val palette = LocalWhalePalette.current
    val dark = LocalWhaleDark.current
    val groupAlpha = (spec.motifOpacity + if (dark) 0.1f else 0f).coerceIn(0f, 1f)
    val s = spec.motifSize
    Box(modifier) {
        // 图钉中心压在气泡顶边线上（下缘吃进气泡自己的顶部留白，不碰正文）
        StickerPin(s, palette.accent, groupAlpha, Modifier.align(Alignment.TopCenter).offset(y = stickerDp(s)(-8f)))
        StickerTape(s, palette.accent, groupAlpha, Modifier.align(Alignment.TopStart))
    }
}

/**
 * 页面层贴纸：朱印（手账）/邮戳＋火漆（书信）/胶片条＋片基标签（暗房）。
 * 挂聊天区容器最后一个孩子（画在卡片之上、只落页边）；`none` 与 `sticker` 的页面件（手账页面层只有朱印）按预设出。
 */
@Composable
fun WhaleStickerOverlay(spec: WhaleSkinSpec, modifier: Modifier = Modifier) {
    val motif = WhaleSkin.motifOf(spec.motif)
    if (motif == "none") return
    val palette = LocalWhalePalette.current
    val dark = LocalWhaleDark.current
    // 深色纸面反差小，贴纸提一档（交接书 §2.2.1 规则 6）
    val groupAlpha = (spec.motifOpacity + if (dark) 0.1f else 0f).coerceIn(0f, 1f)
    Box(modifier) {
        when (motif) {
            "sticker" -> StickerSeal(spec.motifSize, palette.danger, groupAlpha, Modifier.align(Alignment.BottomEnd))
            "postmark" -> {
                StickerPostmark(spec.motifSize, palette.accent, groupAlpha, Modifier.align(Alignment.TopEnd))
                StickerWax(spec.motifSize, palette.danger, groupAlpha, Modifier.align(Alignment.BottomEnd))
            }
            "filmstrip" -> {
                StickerFilm(spec.motifSize, groupAlpha, Modifier.align(Alignment.TopEnd))
                StickerSlug(spec.motifSize, palette, groupAlpha, Modifier.align(Alignment.BottomStart))
            }
        }
    }
}

/** 图钉：19dp accent 圆＋右下内阴影＋左上高光点（位置由调用方锚定：气泡＝压顶边居中）。 */
@Composable
private fun BoxScope.StickerPin(s: Float, accent: Color, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Canvas(
        anchor
            .size(d(19f))
            .alpha(groupAlpha)
            .shadow(d(3f), CircleShape)
    ) {
        drawCircle(accent)
        // 内阴影：右下半圈暗描边（CSS inset -3 -3 0 rgba(0,0,0,.22) 的画布等价）
        val inset = d(2f).toPx()
        drawArc(
            color = SHADOW_INK, startAngle = -30f, sweepAngle = 240f, useCenter = false,
            topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = d(3f).toPx(), cap = StrokeCap.Round),
        )
        drawCircle(HIGHLIGHT, radius = d(2.5f).toPx(), center = Offset(d(6.5f).toPx(), d(5.5f).toPx()))
    }
}

/** 和纸胶带：96×23 accent 30% 斜条（-8°）＋两条白线（位置由调用方锚定：气泡＝压左上角）。 */
@Composable
private fun BoxScope.StickerTape(s: Float, accent: Color, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Box(
        anchor
            .offset(x = d(-16f), y = d(-7f))
            .rotate(-8f)
            .alpha(0.3f * groupAlpha)
            .shadow(d(1f))
            .size(d(96f), d(23f))
            .background(accent)
    ) {
        Box(Modifier.offset(y = d(6f)).fillMaxWidth().height(1.dp).background(TAPE_LINE))
        Box(Modifier.align(Alignment.BottomStart).offset(y = d(-6f)).fillMaxWidth().height(1.dp).background(TAPE_LINE))
    }
}

/** 朱印：40dp danger 描边圆章，内写「记」，-9° 斜放（页面右下角页边）。 */
@Composable
private fun BoxScope.StickerSeal(s: Float, danger: Color, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Box(
        anchor
            .offset(x = d(-12f), y = d(-56f))
            .rotate(-9f)
            .alpha(0.82f * groupAlpha)
            .size(d(40f))
            .border(d(2f), danger, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "记",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = (15 * s).sp, fontWeight = FontWeight.SemiBold, color = danger,
            ),
        )
    }
}

/** 邮戳：66dp accent 虚线圆（+9°），内写日期/地名，左侧两条波纹线（页面右上角页边）。 */
@Composable
private fun BoxScope.StickerPostmark(s: Float, accent: Color, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Box(
        anchor
            .offset(x = d(-10f), y = d(8f))
            .rotate(9f)
            .alpha(0.62f * groupAlpha)
    ) {
        // 波纹线（CSS .postmark b：左 -30、上 29，两条 1.5 横线）
        Canvas(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = d(-30f), y = d(24f))
                .size(d(30f), d(9f))
        ) {
            val w = d(1.5f).toPx()
            drawLine(accent, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = w)
            drawLine(accent, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = w)
        }
        Canvas(Modifier.size(d(66f))) {
            drawCircle(
                color = accent,
                style = Stroke(
                    width = d(1.5f).toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(d(6f).toPx(), d(4f).toPx())),
                ),
            )
        }
        Text(
            "10.02\n上海",
            Modifier
                .size(d(66f))
                .padding(top = d(22f)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (8.5f * s).sp, lineHeight = (11.5f * s).sp,
                fontWeight = FontWeight.SemiBold, color = accent, textAlign = TextAlign.Center,
            ),
        )
    }
}

/** 火漆印：46dp danger 实心圆＋底部内阴影，内写「印」，-6° 斜放（页面右下角页边）。 */
@Composable
private fun BoxScope.StickerWax(s: Float, danger: Color, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Box(
        anchor
            .offset(x = d(-16f), y = d(-52f))
            .rotate(-6f)
            .shadow(d(3f), CircleShape)
    ) {
        Canvas(Modifier.size(d(46f))) {
            drawCircle(danger)
            val inset = d(2f).toPx()
            drawArc(
                color = SHADOW_INK, startAngle = 15f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = d(4f).toPx(), cap = StrokeCap.Round),
            )
        }
        Box(Modifier.size(d(46f)), contentAlignment = Alignment.Center) {
            Text(
                "印",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (16 * s).sp, fontWeight = FontWeight.SemiBold, color = WAX_GLYPH,
                ),
            )
        }
    }
}

/** 胶片条：33×158 黑条（+3°）＋左右 5dp 齿孔（11dp 周期）＋三格画面（页面右侧页边）。 */
@Composable
private fun BoxScope.StickerFilm(s: Float, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Canvas(
        anchor
            .offset(x = d(-8f), y = d(40f))
            .rotate(3f)
            .alpha(groupAlpha)
            .shadow(d(2f))
            .size(d(33f), d(158f))
    ) {
        drawRect(FILM_STRIP)
        // 齿孔（CSS repeating-linear-gradient 5 暗 6 亮的画布等价）：亮段从 y=5 起、每 11dp 一孔
        val holeTop = 5f * s
        val holeH = 6f * s
        val period = 11f * s
        var y = holeTop
        while (y + holeH <= 158f * s) {
            val yy = d(y).toPx()
            drawRect(FILM_HOLE, topLeft = Offset(d(1f).toPx(), yy), size = Size(d(5f).toPx(), d(holeH).toPx()))
            drawRect(FILM_HOLE, topLeft = Offset(d(27f).toPx(), yy), size = Size(d(5f).toPx(), d(holeH).toPx()))
            y += period
        }
        // 三格画面（原型 top 12/62/112，17×30）
        listOf(12f, 62f, 112f).forEach { top ->
            drawRect(FILM_FRAME, topLeft = Offset(d(8f).toPx(), d(top).toPx()), size = Size(d(17f).toPx(), d(30f).toPx()))
            drawRect(
                FILM_FRAME_EDGE,
                topLeft = Offset(d(8f).toPx(), d(top).toPx()),
                size = Size(d(17f).toPx(), d(30f).toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** 片基标签：小字「400TX · 12A」带 1dp 描边、纸面底（页面左下角页边）。 */
@Composable
private fun BoxScope.StickerSlug(s: Float, palette: WhalePalette, groupAlpha: Float, anchor: Modifier) {
    val d = stickerDp(s)
    Box(
        anchor
            .offset(x = d(14f), y = d(-44f))
            .alpha(groupAlpha)
            .background(palette.surface)
            .border(1.dp, palette.hairline)
    ) {
        Text(
            "400TX · 12A",
            Modifier.padding(horizontal = d(7f), vertical = d(3f)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (9 * s).sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (0.8f * s).sp, color = palette.ink2,
            ),
        )
    }
}
