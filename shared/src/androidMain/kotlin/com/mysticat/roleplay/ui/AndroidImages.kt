package com.mysticat.roleplay.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mysticat.roleplay.data.CharacterCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Android 侧的图片编解码与角色卡图片渲染（解码/编码两步走平台，出图卡平台实现）。
 * 逻辑与拆分前（ImageTools.loadBitmap / CardExport.renderCardImage）逐行对应。
 */

/** 裁剪/出图用的共享 http 客户端（远端图先过 httpsUrl，避免被明文策略拦下） */
internal val androidImageHttp by lazy { OkHttpClient() }

/** 裁剪编辑器解码上限：最长边 ≤ 2048px（裁剪框坐标映射按解码后的位图算，降采样不影响正确性） */
internal const val MAX_DECODE_DIM = 2048

/** 本地文件或 http(s) 都能读成 Bitmap；先量边界再按 inSampleSize 降采样，超大图不整图进内存 */
internal fun loadBitmap(uri: String, maxDim: Int = MAX_DECODE_DIM): Bitmap? = runCatching {
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        val req = Request.Builder().url(com.mysticat.roleplay.data.AiClient.httpsUrl(uri)).build()
        androidImageHttp.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: return null
            decodeSampled(bytes, maxDim)
        }
    } else {
        val f = File(uri).absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val bmp = BitmapFactory.decodeFile(f, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxDim) })
        // 预览走 Coil（自动应用 EXIF 方向），裁剪走 BitmapFactory（不应用）——
        // 同一张竖拍照片预览是正的、进裁剪框却躺倒。这里按 EXIF 摆正（P1-9）。
        bmp?.let { applyExifOrientation(f, it) }
    }
}.getOrNull()

/** 按 EXIF 方向把位图摆正；读不到 EXIF 或方向就是"正常"时原样返回 */
private fun applyExifOrientation(path: String, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        else -> return bitmap
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds, maxDim) })
}

private fun sampleFor(bounds: BitmapFactory.Options, maxDim: Int): Int {
    var sample = 1
    val dim = maxOf(bounds.outWidth, bounds.outHeight)
    while (dim / sample > maxDim) sample *= 2
    return sample
}

/** PNG 编码（裁剪后的新图、图片卡落盘共用） */
internal fun encodePngBitmap(bitmap: Bitmap): ByteArray? = runCatching {
    val bos = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
    bos.toByteArray()
}.getOrNull()

// ─────────────────────── 角色卡图片卡渲染（原 CardExport 上半部，逐行迁移） ───────────────────────

/** 画布尺寸与留白：1080×1920 = 9:16，微信/小红书发图的默认竖幅 */
private const val CARD_W = 1080
private const val CARD_H = 1920
private const val CARD_PAD = 72f

/** 小节标签前的竖条颜色（暖色，压深底图不刺眼） */
private val AccentColor = Color.parseColor("#F0C27B")

/** 正文行高估算（字号 34 × 行距 1.35）：用来给每个小节分派行数预算 */
private const val BODY_LINE_PX = 46f

/**
 * 渲染竖版图片卡。纯绘制、不落盘，调用方负责在 IO 线程上跑（解码底图是磁盘/网络操作）。
 * 没配背景/头像也能出图（纯色底），不会因为缺图失败。
 */
internal fun renderCardImage(card: CharacterCard): Bitmap {
    val out = Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val w = CARD_W.toFloat()

    // 底图：背景优先，退回头像；都没有就纯色。居中裁剪铺满，再压一层暗罩 + 底部渐变，
    // 这样不管底图多亮，"白字能读"这件事都是稳的（不需要逐图调参）。
    // 分端背景（A 批次）：取**本平台**那份。Android 侧恒等于原来的 backgroundUri（零回归），
    // 桌面上导出用的就是用户在桌面端看到的那张。
    val bg = card.backgroundHere()?.takeIf { it.isNotBlank() }?.let { loadBitmap(it) }
    val avatar = card.avatarUri?.takeIf { it.isNotBlank() }?.let { loadBitmap(it) }
    val source = bg ?: avatar
    if (source != null) {
        val scale = maxOf(w / source.width, CARD_H.toFloat() / source.height)
        val dw = source.width * scale
        val dh = source.height * scale
        canvas.save()
        canvas.translate((w - dw) / 2f, (CARD_H - dh) / 2f)
        canvas.scale(scale, scale)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        canvas.drawColor(Color.parseColor("#1B1D24"))
    }
    canvas.drawColor(Color.argb(110, 0, 0, 0))
    canvas.drawRect(
        0f, 0f, w, CARD_H.toFloat(),
        Paint().apply {
            shader = LinearGradient(
                0f, CARD_H * 0.30f, 0f, CARD_H.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(215, 0, 0, 0)),
                null, Shader.TileMode.CLAMP
            )
        }
    )

    // 头像圆（没有就跳过，名字位置上移一点点，不影响整体版式）
    val avatarR = 150f
    val avatarCy = CARD_PAD + avatarR + 20f
    avatar?.let { av ->
        // 从头像里取最大的居中正方形，画进圆里（drawBitmap 的 src 只收整型 Rect）
        val side = minOf(av.width, av.height)
        val sx = (av.width - side) / 2
        val sy = (av.height - side) / 2
        canvas.save()
        canvas.clipPath(
            Path().apply {
                addCircle(w / 2f, avatarCy, avatarR, Path.Direction.CW)
            }
        )
        canvas.drawBitmap(
            av,
            Rect(sx, sy, sx + side, sy + side),
            RectF(w / 2f - avatarR, avatarCy - avatarR, w / 2f + avatarR, avatarCy + avatarR),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        canvas.restore()
        canvas.drawCircle(
            w / 2f, avatarCy, avatarR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = Color.argb(150, 255, 255, 255)
            }
        )
    }

    var y = avatarCy + avatarR + 64f
    y = drawTextBlock(
        canvas, card.name, textPaint(76f, bold = true), y,
        CARD_W, maxLines = 2, align = Layout.Alignment.ALIGN_CENTER, x = 0f
    )
    y += 16f
    y = drawTextBlock(
        canvas, card.tagline, textPaint(38f, alpha = 210), y,
        CARD_W, maxLines = 2, align = Layout.Alignment.ALIGN_CENTER, x = 0f
    )
    y += 14f
    y = drawTextBlock(
        canvas, card.formTagLabel() + " · " + card.categoriesOrDefault().joinToString(" / "),
        textPaint(30f, alpha = 190), y,
        CARD_W, maxLines = 1, align = Layout.Alignment.ALIGN_CENTER, x = 0f
    )

    // 正文区：人设 + 开场白。总高度写死，两块按"行数预算"分，超出的部分省略号收尾，
    // 保证长人设不会把开场白挤出画面（宁可截断，也不要版式崩掉）。
    val bodyTop = y + 52f
    val footerY = CARD_H - 118f
    val labelH = 34f + 30f
    // 人设最多占六成行数（至少 3 行、至多 26 行），剩下的留给开场白
    val personaBudget = (((footerY - bodyTop - labelH * 2 - 24f) / BODY_LINE_PX).toInt() * 0.6f)
        .toInt().coerceIn(3, 26)

    canvas.drawRoundRect(
        RectF(CARD_PAD - 24f, bodyTop - 24f, w - CARD_PAD + 24f, footerY),
        28f, 28f,
        Paint().apply { color = Color.argb(96, 0, 0, 0) }
    )

    var bodyY = drawSectionLabel(canvas, "人设", bodyTop)
    bodyY = drawTextBlock(
        // 分层字段（E1 起）不并在 persona 里 ⇒ 卡图正文要取合并文本，否则新卡会导出一张没有人设的图
        canvas, card.personaDisplayText(), textPaint(34f, alpha = 235), bodyY,
        (CARD_W - CARD_PAD * 2).toInt(), maxLines = personaBudget
    )
    bodyY += 40f
    bodyY = drawSectionLabel(canvas, "开场白", bodyY)
    // 开场白拿"人设画完后剩下的空间"折行数，最后一块永远不会压到页脚
    val greetLines = ((footerY - bodyY) / BODY_LINE_PX).toInt().coerceIn(2, 26)
    drawTextBlock(
        canvas, card.effectiveGreetings().firstOrNull().orEmpty(), textPaint(34f, alpha = 235), bodyY,
        (CARD_W - CARD_PAD * 2).toInt(), maxLines = greetLines
    )

    drawTextBlock(
        canvas, "鲸鱼 · 角色卡", textPaint(28f, alpha = 170), CARD_H - 96f,
        CARD_W, maxLines = 1, align = Layout.Alignment.ALIGN_CENTER, x = 0f
    )
    return out
}

/** 正文用的画笔：统一加一点阴影，压在亮底图上也能读 */
private fun textPaint(sizePx: Float, bold: Boolean = false, alpha: Int = 255): TextPaint =
    TextPaint().apply {
        textSize = sizePx
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        color = Color.argb(alpha, 255, 255, 255)
        isAntiAlias = true
        setShadowLayer(8f, 0f, 3f, Color.argb(170, 0, 0, 0))
    }

/** 小节标题：一条暖色竖条 + 标题字，返回画完后的 y */
private fun drawSectionLabel(canvas: Canvas, label: String, y: Float): Float {
    canvas.drawRoundRect(
        RectF(CARD_PAD, y + 6f, CARD_PAD + 8f, y + 40f), 4f, 4f,
        Paint().apply { color = AccentColor }
    )
    return drawTextBlock(
        canvas, label, textPaint(34f, bold = true), y,
        (CARD_W - CARD_PAD * 2).toInt(), maxLines = 1, x = CARD_PAD + 22f
    ) + 18f
}

/** 画一段可换行文本（超出行数省略号收尾），返回画完后的 y 坐标 */
private fun drawTextBlock(
    canvas: Canvas,
    text: String,
    paint: TextPaint,
    y: Float,
    width: Int,
    maxLines: Int,
    align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    x: Float = CARD_PAD
): Float {
    if (text.isBlank() || maxLines < 1) return y
    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setLineSpacing(0f, 1.35f)
        .setAlignment(align)
        .build()
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
    return y + layout.height
}

/**
 * 渲染图片卡并落盘到 `cacheDir/card-export/`（FileProvider 已暴露该目录，分享用）。
 * 只留最近 3 份：连点几次导出不至于把 cacheDir 撑起来（与备份导出同理，P2-A13）。
 */
internal suspend fun buildCardImageFile(context: Context, card: CharacterCard): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "card-export").apply { mkdirs() }
            runCatching {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(3)
                    ?.forEach { it.delete() }
            }
            val file = File(dir, "card-${System.currentTimeMillis()}.png")
            val bmp = renderCardImage(card)
            FileOutputStream(file).use { os -> bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
            bmp.recycle()
            file
        }.getOrNull()
    }

/** 共享层接口落点：ImageBitmap → PNG 字节 */
internal suspend fun encodeImageBitmapPng(bitmap: ImageBitmap): ByteArray? =
    withContext(Dispatchers.IO) { encodePngBitmap(bitmap.asAndroidBitmap()) }

/** 共享层接口落点：路径/直链 → ImageBitmap（降采样 + EXIF 摆正） */
internal suspend fun decodeImageBitmapOnAndroid(uri: String, maxDim: Int): ImageBitmap? =
    withContext(Dispatchers.IO) { loadBitmap(uri, maxDim)?.asImageBitmap() }
