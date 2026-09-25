package com.mysticat.roleplay.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.mysticat.roleplay.ui.isDesktopLayout
import androidx.compose.ui.unit.Density
import com.mysticat.roleplay.data.AppFont
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.isUsableFontFile

private val LightColors = lightColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFFEC4899),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF831843),
    background = Color(0xFFFBF7FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFF3EEFA),
    onSurface = Color(0xFF1F1B2E),
    onSurfaceVariant = Color(0xFF6B6478)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFF9A8D4),
    onSecondary = Color(0xFF500724),
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF14101E),
    surface = Color(0xFF1B1526),
    surfaceVariant = Color(0xFF2A2238),
    onSurface = Color(0xFFEDE8F6),
    onSurfaceVariant = Color(0xFFB7AEC7)
)

/**
 * 内置字族映射。中文下 sans-serif / monospace 都会回退成黑体，故不再提供。
 *
 * **两端都改走平台**（2026-09-17 修正）：
 * - 「衬线」：`FontFamily.Serif` 是"通用族"，桌面（Skia）拿它解析中文时并不会挑到中文衬线体，
 *   用户反馈"系统自带的'衬线'在 windows 中不对中文生效"就是这个原因。桌面实现改为显式挑一个
 *   系统中文字体文件（宋体等），挑不到才回退通用族。
 * - 「系统默认」：`FontFamily.Default` 同样是通用族，中文由 Skia 的 fallback 链决定。⚠ **本机实测
 *   （第 27 轮，离屏出图逐像素比对）：fallback 本来就落到微软雅黑，显式挑 msyh.ttc 后中文正文
 *   像素级一致**——所以"方案①"在中文上没有可见收益，它的价值是**别让别的机器/系统语言下 fallback
 *   跑到宋体这类细笔画字体上**（确定性）。用户报的 #9"字体不清晰"另有原因：见 `serifFontFamily`
 *   与「衬线＝宋体」那一档（细笔画 + 灰阶抗锯齿最吃亏），以及 Skia 无 hinting 这个既定事实。
 *
 * 两档都只影响**内置字体**；用户上传的字体文件优先级更高（见 [currentFontFamily]）。
 */
private fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> Platform.ui.sansFontFamily() ?: FontFamily.Default
    AppFont.SERIF -> Platform.ui.serifFontFamily() ?: FontFamily.Serif
}

/**
 * 当前生效的字体族：用户上传的字体优先，没上传就用内置字族。
 * 上传文件读不出来时静默回退到内置，避免一个坏文件把整个 App 拖崩。
 * M3 起字体加载走平台（桌面侧 CMP 1.11 的 Font 只有资源 ID 重载，没有 Font(ByteArray)，
 * 两端各自的实现都从本地文件构造），坏文件在 [isUsableFontFile]（魔数+平台引擎试加载）阶段就被拦在门外。
 */
private fun currentFontFamily(): FontFamily {
    val path = ThemeState.customFontPath
    if (!path.isNullOrBlank() && isUsableFontFile(path)) {
        Platform.ui.loadCustomFontFamily(path)?.let { return it }
    }
    return ThemeState.font.toFontFamily()
}

/**
 * 把默认 Material3 字体表整体换上指定字族。
 * 字号不在这里改——统一由 [MystiCatTheme] 里的 fontScale 缩放负责，
 * 这样用户调的「字号」能同时作用于所有页面。
 */
private fun typographyWith(family: FontFamily): Typography {
    if (family == FontFamily.Default) return Typography()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family),
        displayMedium = d.displayMedium.copy(fontFamily = family),
        displaySmall = d.displaySmall.copy(fontFamily = family),
        headlineLarge = d.headlineLarge.copy(fontFamily = family),
        headlineMedium = d.headlineMedium.copy(fontFamily = family),
        headlineSmall = d.headlineSmall.copy(fontFamily = family),
        titleLarge = d.titleLarge.copy(fontFamily = family),
        titleMedium = d.titleMedium.copy(fontFamily = family),
        titleSmall = d.titleSmall.copy(fontFamily = family),
        bodyLarge = d.bodyLarge.copy(fontFamily = family),
        bodyMedium = d.bodyMedium.copy(fontFamily = family),
        bodySmall = d.bodySmall.copy(fontFamily = family),
        labelLarge = d.labelLarge.copy(fontFamily = family),
        labelMedium = d.labelMedium.copy(fontFamily = family),
        labelSmall = d.labelSmall.copy(fontFamily = family)
    )
}

@Composable
fun MystiCatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 动态取色（Android 12 壁纸取色）走平台：桌面实现返回 null，回退既有固定配色
    val dynamic = if (dynamicColor) Platform.ui.dynamicColorScheme(darkTheme) else null
    val colorScheme = dynamic ?: if (darkTheme) DarkColors else LightColors

    // 问题 #4：用户可调字体与字号。只放大 sp（文字），不动 dp 布局，避免整页排版被撑坏。
    val density = LocalDensity.current
    val fontFamily = remember(ThemeState.customFontPath, ThemeState.font) { currentFontFamily() }
    val typography = remember(fontFamily) { typographyWith(fontFamily) }

    // P2-A23：Density 每次重组都 new 一个（provider 值变化会连带整棵树重组），按输入 remember
    val scaledDensity = remember(density.density, density.fontScale, ThemeState.fontScale) {
        Density(density.density, density.fontScale * ThemeState.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        // ── 桌面按下指示（第 26 轮改，第 27 轮修正结论）──
        // Compose Desktop 默认的 `LocalIndication` 是 **DefaultDebugIndication**：悬停/按下时给控件套
        // 两圈方框（调试件）。手机端走系统涟漪，只有桌面中招 ⇒ 这里显式换成 M3 的 `ripple()`。
        // ⚠ 这条只治"裸 `Modifier.clickable`"——M3 组件自己传 `ripple()`，不吃 `LocalIndication`。
        // ⚠ 用户报的"胶囊内框"**不是它**：第 27 轮把 `LocalIndication` 换成"什么都不画"的指示后
        // 方框照旧；`RippleConfiguration(color = Transparent)` 也照旧。真正的来源是 M3 `FilterChip`
        // 自己的悬停绘制 ⇒ 已改成自绘胶囊 `WhaleChip`（`ui/Common.kt`，桌面自绘、手机仍用 M3）。
        CompositionLocalProvider(
            LocalIndication provides if (isDesktopLayout) ripple() else LocalIndication.current
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                content = content
            )
        }
    }
}
