package com.mysticat.roleplay.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 桌面端的图片解码/编码与"保存到相册"替身（解码与 PNG 编码两步走平台）。
 *
 * 共享层的裁剪逻辑（`ui/ImageTools.kt`）只跟 [ImageBitmap] 打交道，两端同一套；
 * 这里只提供"字节 ↔ ImageBitmap"和"落盘"这几步桌面实现。**图片卡排版（1080×1920）
 * 仍在 Android 侧未上移**，桌面版由后续轮次补齐。
 */

private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024

/** 读图上限，与 Android 侧同一口径 */
private const val READ_CAP = MAX_IMAGE_BYTES

private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

/** 解码图片为共享的 [ImageBitmap]；[uri] 支持本地路径与 http(s) 直链；超 [maxDim] 等比缩小 */
internal suspend fun desktopDecodeImageBitmap(uri: String, maxDim: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readImageBytes(uri) ?: return@runCatching null
            val image = Image.makeFromEncoded(bytes)
            scaleToMax(image, maxDim).toComposeImageBitmap()
        }.getOrNull()
    }

/**
 * 解码成 Skia 的原生 [Image]（图片卡渲染要它才能上 Canvas —— `ImageBitmap` 还得再转一手）。
 * 走的是同一套字节读取与降采样：卡片底图按 2048 上限就够（成图只有 1080 宽）。
 */
internal fun loadSkiaImage(uri: String, maxDim: Int = 2048): Image? = runCatching {
    readImageBytes(uri)?.let { scaleToMax(Image.makeFromEncoded(it), maxDim) }
}.getOrNull()

/** 把 [ImageBitmap] 编码为 PNG 字节（图片卡落盘、头像写盘共用） */
internal suspend fun desktopEncodePng(bitmap: ImageBitmap): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val data = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
        data?.bytes
    }.getOrNull()
}

/**
 * 「保存到相册」的桌面替身：落到用户图片目录下的应用文件夹（桌面没有系统相册）。
 * [uri] 可以是本地路径（已在应用私有目录里的导出结果），也可以是 http(s) 直链（生图预览保存）。
 */
internal suspend fun desktopSaveImageToGallery(uri: String, exportDir: File): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            if (!exportDir.mkdirs() && !exportDir.isDirectory) return@runCatching false
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                val bytes = readImageBytes(uri) ?: return@runCatching false
                val (ext, _) = sniffImageType(bytes)
                uniqueFile(exportDir, "鲸鱼图片", ext).writeBytes(bytes)
                true
            } else {
                val src = File(uri)
                if (!src.isFile || src.length() == 0L) return@runCatching false
                val ext = src.extension.lowercase(Locale.ROOT).ifBlank { "png" }
                src.copyTo(uniqueFile(exportDir, src.nameWithoutExtension, ext), overwrite = false)
                true
            }
        }.getOrDefault(false)
    }

// ────────────────────── 内部 ──────────────────────

private fun readImageBytes(uri: String): ByteArray? = runCatching {
    if (uri.startsWith("http://") || uri.startsWith("https://")) {
        val request = Request.Builder().url(uri).build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.byteStream()?.use { readAtMost(it, READ_CAP) }
        }
    } else {
        val f = File(uri)
        if (!f.isFile) return@runCatching null
        if (f.length() > READ_CAP) return@runCatching null
        FileInputStream(f).use { readAtMost(it, READ_CAP) }
    }
}.getOrNull()

private fun scaleToMax(image: Image, maxDim: Int): Image {
    val w = image.width
    val h = image.height
    val longest = maxOf(w, h)
    if (longest <= maxDim || longest <= 0 || maxDim <= 0) return image
    val scale = maxDim.toFloat() / longest.toFloat()
    val nw = maxOf(1, (w * scale).toInt())
    val nh = maxOf(1, (h * scale).toInt())
    val surface = Surface.makeRasterN32Premul(nw, nh)
    surface.canvas.drawImageRect(
        image,
        Rect.makeWH(w.toFloat(), h.toFloat()),
        Rect.makeWH(nw.toFloat(), nh.toFloat())
    )
    return surface.makeImageSnapshot()
}

/** 重名就加 -1 / -2 后缀，不覆盖用户已有的文件 */
private fun uniqueFile(dir: File, baseName: String, ext: String): File {
    var candidate = File(dir, "$baseName.$ext")
    var i = 1
    while (candidate.exists() && i < 1000) {
        candidate = File(dir, "$baseName-$i.$ext")
        i++
    }
    return candidate
}
