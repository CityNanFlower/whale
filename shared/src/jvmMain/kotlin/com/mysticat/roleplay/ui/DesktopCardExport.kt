package com.mysticat.roleplay.ui

import com.mysticat.roleplay.data.CharacterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.Alignment
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.Paragraph
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.Shadow
import org.jetbrains.skia.paragraph.TextStyle
import java.io.File

/**
 * 桌面端的角色卡图片卡渲染（对齐 alpha.2 的"导出图片卡"）。
 *
 * 为什么是"再写一份"而不是抽到共享层：排版要**文字断行 + 截断省略号**，Android 靠
 * `StaticLayout`（android.text），桌面只有 Skia 的 `Paragraph`（skiko 的类型进不了
 * `jvmSharedMain`——`commonMain` 那条可见性硬约束的下游）。所以这里是**排版算法同构**的两份实现：
 * 画布尺寸、留白、字号、行高倍率、行数预算、配色全部逐值对应 [AndroidImages.renderCardImage]，
 * 改动其中一份时另一份要同步。
 *
 * 与 Android 侧的两处有意差异：
 * ① 字体显式给"Microsoft YaHei / Segoe UI"字体栈（Skia 不做 Android 那种系统级字族回退，
 *    中文靠雅黑兜住）；
 * ② 阴影用 Skia 的 Shadow（模糊 sigma 与 Android 的 shadowLayer radius 不是同一标度，取近似值）。
 *
 * 底图/头像解码仍走共享的 [loadSkiaImage]（与裁剪、生图预览同一套字节读取与降采样）。
 */

/** 画布尺寸与留白：1080×1920 = 9:16，与 Android 侧同一口径 */
private const val CARD_W = 1080
private const val CARD_H = 1920
private const val CARD_PAD = 72f

/** 小节标签前的竖条颜色（暖色，压深底图不刺眼） */
private const val ACCENT_COLOR = 0xFFF0C27B.toInt()

/** 纯色底（没配背景/头像时）与压暗罩的透明度，与 Android 一致 */
private const val FALLBACK_BG = 0xFF1B1D24.toInt()
private const val SCRIM_ALPHA = 110
private const val GRADIENT_BOTTOM_ALPHA = 215

/** 正文行高估算（字号 34 × 行距 1.35）：用来给每个小节分派行数预算——数值必须与 Android 侧一致 */
private const val BODY_LINE_PX = 46f
private const val LINE_SPACING_MULT = 1.35f

/** 中文字体栈：Windows 一定有雅黑，退到 Segoe UI / Arial 兜住拉丁字符 */
private val FONT_STACK = arrayOf("Microsoft YaHei", "Segoe UI", "Arial")

internal suspend fun desktopBuildCardImageFile(card: CharacterCard, cacheRoot: File): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(cacheRoot, "card-export").apply { mkdirs() }
            // 只留最近 3 份：连点几次导出不至于把缓存目录撑起来（与 Android 侧同一策略）
            runCatching {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(3)
                    ?.forEach { it.delete() }
            }
            val file = File(dir, "card-${System.currentTimeMillis()}.png")
            val bytes = renderCardImageSkia(card)?.encodeToData(EncodedImageFormat.PNG)?.bytes
            if (bytes == null || bytes.isEmpty()) return@runCatching null
            file.writeBytes(bytes)
            file
        }.getOrNull()
    }

/** 渲染成 PNG 字节（自检里也直接用它，不必经过文件）；失败返回 null */
fun renderCardImagePng(card: CharacterCard): ByteArray? = runCatching {
    renderCardImageSkia(card)?.encodeToData(EncodedImageFormat.PNG)?.bytes
}.getOrNull()

/** 渲染竖版图片卡（纯绘制、不落盘，调用方负责在 IO 线程上跑）。逻辑对照 Android 的 renderCardImage */
private fun renderCardImageSkia(card: CharacterCard): Image? = runCatching {
    val surface = Surface.makeRasterN32Premul(CARD_W, CARD_H)
    val canvas = surface.canvas
    val w = CARD_W.toFloat()
    val hh = CARD_H.toFloat()
    val fonts = FontCollection().setDefaultFontManager(org.jetbrains.skia.FontMgr.default, FONT_STACK[0])

    // 底图：背景优先，退回头像；都没有就纯色。居中裁剪铺满，再压一层暗罩 + 底部渐变，
    // 这样不管底图多亮，"白字能读"这件事都是稳的（与 Android 侧逐行同构）。
    // 分端背景（A 批次）：同 AndroidImages —— 取本平台那份。
    val bg = card.backgroundHere()?.takeIf { it.isNotBlank() }?.let { loadSkiaImage(it) }
    val avatar = card.avatarUri?.takeIf { it.isNotBlank() }?.let { loadSkiaImage(it) }
    val source = bg ?: avatar
    if (source != null) {
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()
        val scale = maxOf(w / sw, hh / sh)
        val dw = sw * scale
        val dh = sh * scale
        canvas.drawImageRect(
            source,
            Rect.makeWH(sw, sh),
            Rect.makeXYWH((w - dw) / 2f, (hh - dh) / 2f, dw, dh),
            Paint().apply { isAntiAlias = true }
        )
    } else {
        canvas.drawRect(0f, 0f, w, hh, fill(FALLBACK_BG))
    }
    canvas.drawRect(0f, 0f, w, hh, fill(argb(SCRIM_ALPHA, 0, 0, 0)))
    // 底部渐变：Skia 的 gradient 要 GradientStyle（构造参数繁琐），这里用分段矩形逼近——
    // 64 段在 1344px 上每段 21px，肉眼与连续渐变无异，且不引入额外 API 依赖
    val gradTop = CARD_H * 0.30f
    val steps = 64
    val stepH = (hh - gradTop) / steps
    for (i in 0 until steps) {
        val a = (GRADIENT_BOTTOM_ALPHA * (i + 1) / steps.toFloat()).toInt()
        canvas.drawRect(0f, gradTop + stepH * i, w, gradTop + stepH * (i + 1), fill(argb(a, 0, 0, 0)))
    }

    // 头像圆（没有就跳过，名字位置上移一点点，不影响整体版式）
    val avatarR = 150f
    val avatarCy = CARD_PAD + avatarR + 20f
    avatar?.let { av ->
        val side = minOf(av.width, av.height)
        val sx = (av.width - side) / 2f
        val sy = (av.height - side) / 2f
        canvas.save()
        // Skia m144 起 Path 的"可变构造"API 挪到了 PathBuilder（Path.addCircle 已废弃）
        canvas.clipPath(
            PathBuilder().addCircle(w / 2f, avatarCy, avatarR, PathDirection.CLOCKWISE).detach(),
            ClipMode.INTERSECT,
            true
        )
        canvas.drawImageRect(
            av,
            Rect.makeXYWH(sx, sy, side.toFloat(), side.toFloat()),
            Rect.makeLTRB(w / 2f - avatarR, avatarCy - avatarR, w / 2f + avatarR, avatarCy + avatarR),
            Paint().apply { isAntiAlias = true }
        )
        canvas.restore()
        canvas.drawCircle(
            w / 2f, avatarCy, avatarR,
            fill(0).apply {
                mode = PaintMode.STROKE
                strokeWidth = 6f
                color = argb(150, 255, 255, 255)
            }
        )
    }

    var y = avatarCy + avatarR + 64f
    y = drawTextBlock(canvas, fonts, card.name, textStyle(76f, bold = true), y, CARD_W, 2, Alignment.CENTER, 0f)
    y += 16f
    y = drawTextBlock(canvas, fonts, card.tagline, textStyle(38f, alpha = 210), y, CARD_W, 2, Alignment.CENTER, 0f)
    y += 14f
    y = drawTextBlock(
        canvas, fonts,
        card.formTagLabel() + " · " + card.categoriesOrDefault().joinToString(" / "),
        textStyle(30f, alpha = 190), y, CARD_W, 1, Alignment.CENTER, 0f
    )

    // 正文区：人设 + 开场白。总高度写死，两块按"行数预算"分，超出的部分省略号收尾，
    // 保证长人设不会把开场白挤出画面（与 Android 侧的预算公式逐值一致）。
    val bodyTop = y + 52f
    val footerY = CARD_H - 118f
    val labelH = 34f + 30f
    val personaBudget = (((footerY - bodyTop - labelH * 2 - 24f) / BODY_LINE_PX).toInt() * 0.6f)
        .toInt().coerceIn(3, 26)

    canvas.drawRRect(
        RRect.makeLTRB(CARD_PAD - 24f, bodyTop - 24f, w - CARD_PAD + 24f, footerY, 28f),
        fill(argb(96, 0, 0, 0))
    )

    var bodyY = drawSectionLabel(canvas, fonts, "人设", bodyTop)
    bodyY = drawTextBlock(
        // 分层字段不并在 persona 里 ⇒ 卡图正文要取合并文本，否则新卡会导出一张没有人设的图
        canvas, fonts, card.personaDisplayText(), textStyle(34f, alpha = 235), bodyY,
        (CARD_W - CARD_PAD * 2).toInt(), personaBudget, Alignment.LEFT, CARD_PAD
    )
    bodyY += 40f
    bodyY = drawSectionLabel(canvas, fonts, "开场白", bodyY)
    val greetLines = ((footerY - bodyY) / BODY_LINE_PX).toInt().coerceIn(2, 26)
    drawTextBlock(
        canvas, fonts, card.effectiveGreetings().firstOrNull().orEmpty(), textStyle(34f, alpha = 235), bodyY,
        (CARD_W - CARD_PAD * 2).toInt(), greetLines, Alignment.LEFT, CARD_PAD
    )

    drawTextBlock(
        canvas, fonts, "鲸鱼 · 角色卡", textStyle(28f, alpha = 170), CARD_H - 96f,
        CARD_W, 1, Alignment.CENTER, 0f
    )
    surface.makeImageSnapshot()
}.getOrNull()

/** 正文用的字色：统一半透明白 + 一点阴影，压在亮底图上也能读（对应 Android 的 textPaint） */
private fun textStyle(sizePx: Float, bold: Boolean = false, alpha: Int = 255): TextStyle =
    TextStyle().apply {
        fontSize = sizePx
        color = argb(alpha, 255, 255, 255)
        fontFamilies = FONT_STACK
        if (bold) fontStyle = org.jetbrains.skia.FontStyle.BOLD
        addShadow(Shadow(argb(170, 0, 0, 0), 0f, 3f, 3.0))
    }

/** 小节标题：一条暖色竖条 + 标题字，返回画完后的 y（与 Android 的 drawSectionLabel 同构） */
private fun drawSectionLabel(canvas: Canvas, fonts: FontCollection, label: String, y: Float): Float {
    canvas.drawRRect(
        RRect.makeLTRB(CARD_PAD, y + 6f, CARD_PAD + 8f, y + 40f, 4f),
        fill(ACCENT_COLOR)
    )
    return drawTextBlock(
        canvas, fonts, label, textStyle(34f, bold = true), y,
        (CARD_W - CARD_PAD * 2).toInt(), 1, Alignment.LEFT, CARD_PAD + 22f
    ) + 18f
}

/** 画一段可换行文本（超出行数省略号收尾），返回画完后的 y 坐标 */
private fun drawTextBlock(
    canvas: Canvas,
    fonts: FontCollection,
    text: String,
    style: TextStyle,
    y: Float,
    width: Int,
    maxLines: Int,
    align: Alignment,
    x: Float
): Float {
    if (text.isBlank() || maxLines < 1) return y
    val paragraph = buildParagraph(text, style, fonts, maxLines, align, width) ?: return y
    paragraph.paint(canvas, x, y)
    return y + paragraph.height
}

/** 组装一个已 layout 的段落（Skia 的 Paragraph 用完即弃，每块文本新建一个） */
private fun buildParagraph(
    text: String,
    style: TextStyle,
    fonts: FontCollection,
    maxLines: Int,
    align: Alignment,
    width: Int
): Paragraph? = runCatching {
    val ps = ParagraphStyle().apply {
        alignment = align
        maxLinesCount = maxLines
        ellipsis = "…"
        height = LINE_SPACING_MULT
        textStyle = style
    }
    ParagraphBuilder(ps, fonts).addText(text).build().also { it.layout(width.toFloat()) }
}.getOrNull()

private fun fill(color: Int): Paint = Paint().apply {
    isAntiAlias = true
    this.color = color
}

private fun argb(alpha: Int, r: Int, g: Int, b: Int): Int =
    (alpha shl 24) or (r shl 16) or (g shl 8) or b
