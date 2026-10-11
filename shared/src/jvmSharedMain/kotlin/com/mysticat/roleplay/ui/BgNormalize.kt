package com.mysticat.roleplay.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.WhaleLog
import com.mysticat.roleplay.data.imageModel

/**
 * 聊天背景「按图归一」（v11 设计合同定稿配方）：背景图先按**本图**的亮度极值做仿射压缩，
 * 压进当前主题的亮度带，再叠各处既有的暗化层——这样"图可能是纯黑、也可能是纯白"不再
 * 影响页面的可读性底账：合成底永远落在同一档亮度上，透层文字的对比度才算得出来。
 *
 * 配方（v11 评审 r4 实测定案，别凭手感改数）：
 * - 定标取**全幅** min/max（不是窄分位）：仿射对整幅生效，按分位定标时尾巴会被同倍放大、
 *   溢出主题带（原型实测浅色极差 59.6 > 40 的教训）；
 * - 平涂图（极差≈0）的极差按下限 0.05 兜底，保证仿射可逆；
 * - 主题带：浅色 0.71~0.96、深色 0.01~0.225（sRGB 编码值上的 Rec.709 亮度）。
 *
 * **实现取像素级预归一而不是渲染期 ColorFilter**：仿射在共享层对每个像素算好、落成新位图
 * （Android=Bitmap.createBitmap，桌面=skia Bitmap.installPixels，见 PlatformUi 注入点）。
 * 同一份纯 Kotlin 数学在两端产出同一张归一图——"两端铺层色调一致"由构造保证；
 * 也不依赖渲染器的 filter 通路（桌面实测 AsyncImage 挂 ColorMatrix 不上屏，tint 才上屏，
 * 离屏 --shot 更是两类都不吃——这条已写进轮次详情，别再用 ColorFilter 走这条路）。
 */

/** 主题亮度带（sRGB 0..1）：归一后图的 min/max 落在这里 */
private val BAND_LIGHT = 0.71f to 0.96f
private val BAND_DARK = 0.01f to 0.225f

/** 平涂图（极差≈0）的兜底跨度：极差低于它按它算，防止除零与无穷放大 */
private const val MIN_LUMA_RANGE = 0.05f

/** 归一前解码上限：背景铺层是全屏 Crop，2048 足够 */
private const val DECODE_MAX_DIM = 2048

/** 一张图的亮度极值（0..1，Rec.709） */
class BgLumaExtrema(val min: Float, val max: Float)

private fun extremaOf(px: PixelMap): BgLumaExtrema {
    var mn = 1f
    var mx = 0f
    for (y in 0 until px.height) {
        for (x in 0 until px.width) {
            val c = px[x, y]
            val l = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            if (l < mn) mn = l
            if (l > mx) mx = l
        }
    }
    return BgLumaExtrema(mn, mx)
}

/** 仿射归一到 ARGB 整数数组（0xFFRRGGBB，不透明）：out = slope·in + offset，三通道同系数。 */
private fun normalizedArgb(px: PixelMap, ext: BgLumaExtrema, darkTheme: Boolean): IntArray {
    val (lo, hi) = if (darkTheme) BAND_DARK else BAND_LIGHT
    val slope = (hi - lo) / maxOf(ext.max - ext.min, MIN_LUMA_RANGE)
    val offset = lo - slope * ext.min
    val out = IntArray(px.width * px.height)
    var i = 0
    for (y in 0 until px.height) {
        for (x in 0 until px.width) {
            val c = px[x, y]
            val r = ((slope * c.red + offset).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val g = ((slope * c.green + offset).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val b = ((slope * c.blue + offset).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            out[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return out
}

/**
 * 归一位图缓存：`uri#深浅` → ImageBitmap。背景图由 [com.mysticat.roleplay.data.Repository]
 * 落盘为唯一文件名（img_*.png），路径即内容，按 uri 缓存不会失效；LRU 封顶防长会话累积
 * （每张全屏位图数 MB，6 张封顶）。
 */
object BgNormalizedBitmaps {
    private const val CACHE_MAX = 6
    private val cache = object : LinkedHashMap<String, ImageBitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) =
            size > CACHE_MAX
    }

    fun peek(uri: String, darkTheme: Boolean): ImageBitmap? =
        synchronized(cache) { cache[key(uri, darkTheme)] }

    suspend fun load(uri: String, darkTheme: Boolean): ImageBitmap? {
        peek(uri, darkTheme)?.let { return it }
        val out = runCatching {
            val src = Platform.ui.decodeImageBitmap(uri, DECODE_MAX_DIM) ?: return@runCatching null
            val px = src.toPixelMap()
            Platform.ui.imageBitmapFromArgb(
                src.width, src.height, normalizedArgb(px, extremaOf(px), darkTheme)
            )
        }.onFailure {
            WhaleLog.w(
                "BgNormalize",
                "归一失败 ${uri.substringAfterLast('/').substringAfterLast('\\')}: ${it.message}"
            )
        }.getOrNull()
        if (out != null) synchronized(cache) { cache[key(uri, darkTheme)] = out }
        return out
    }

    private fun key(uri: String, darkTheme: Boolean) = "$uri#${if (darkTheme) "d" else "l"}"
}

/**
 * 聊天背景铺层统一入口（聊天区／二级页／毛玻璃原语三处共用）。
 * [uri] 为空不画（调用方的主题色兜底层接管）；统计/归一失败时退回原图铺层（与旧行为一致）。
 * 深浅按**主题底色亮度**判定——带宽必须与"veil 用哪个底色"配对，自定义皮肤自动归位。
 * 归一位图走后台加载：首帧没到手前不画这一层（与 Coil 异步出图的旧节奏一致）。
 */
@Composable
fun BgNormalizedImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    blurRadius: Dp? = null,
) {
    if (uri.isNullOrBlank()) return
    val model = imageModel(uri) ?: return
    if (model !is java.io.File) {
        // 归一只对本地文件生效；外链按原图铺（Coil 直出，与旧行为一致）
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = if (blurRadius != null) modifier.blur(blurRadius) else modifier
        )
        return
    }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var bmp by remember(uri, darkTheme) { mutableStateOf(BgNormalizedBitmaps.peek(uri, darkTheme)) }
    LaunchedEffect(uri, darkTheme) { if (bmp == null) bmp = BgNormalizedBitmaps.load(uri, darkTheme) }
    val bitmap = bmp ?: return
    Image(
        painter = BitmapPainter(bitmap),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = if (blurRadius != null) modifier.blur(blurRadius) else modifier
    )
}
