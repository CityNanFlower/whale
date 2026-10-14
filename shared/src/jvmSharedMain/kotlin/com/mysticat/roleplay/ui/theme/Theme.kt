package com.mysticat.roleplay.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import com.mysticat.roleplay.data.AppFont
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.isUsableFontFile
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSkin
import com.mysticat.roleplay.ui.tokens.scaleTextStyle
import com.mysticat.roleplay.ui.tokens.whaleHex
import com.mysticat.roleplay.ui.tokens.whaleTypeAt

/**
 * L1 皮肤色板 → M3 ColorScheme。**本函数不出现任何色值字面量**——取值全部来自
 * [WhalePalette]（简约皮肤的令牌真值表，见 `ui/tokens/WhaleSkin.kt`）。角色映射：
 * paper→background｜surface/surface2→surface 系｜ink→onSurface｜ink2→onSurfaceVariant｜
 * accent→primary/secondary/tertiary｜soft→各 Container｜hairline(2)→outline(Variant)｜danger→error 系。
 * 容器高低档按原型口径：悬停面＝hairline2（原型的 `.srow:hover` 就是 --hair2），卡面＝surface2。
 * 两个派生对（推导式写在这里，不是新调的色值）：
 * errorContainer＝danger 以 14% 叠在 surface 上；onError＝暗档落 paper、亮档落白。
 */
private fun WhalePalette.toMaterialScheme(dark: Boolean): ColorScheme {
    val onError = if (dark) paper else whaleHex("#FFFFFF")
    val dangerContainer = danger.copy(alpha = 0.14f).compositeOver(surface)
    // 悬停面两档都走 hairline2——原型 `.srow:hover` 的口径就是 --hair2（暗档 #33363B ≠ surface2，
    // 若映射成 surface2 会和选中行/卡面混同，评审 R2-7 抓过这条）
    val hoverTint = hairline2
    val lowTint = surface
    val lowestTint = if (dark) paper else surface
    return if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = soft, onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = accent, onSecondary = onAccent, secondaryContainer = soft, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = soft, onTertiaryContainer = ink,
        background = paper, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = surface2, onSurfaceVariant = ink2,
        surfaceTint = accent, inverseSurface = ink, inverseOnSurface = paper,
        error = danger, onError = onError, errorContainer = dangerContainer, onErrorContainer = danger,
        outline = hairline, outlineVariant = hairline2,
        scrim = whaleHex("#000000"),
        surfaceContainerLowest = lowestTint, surfaceContainerLow = lowTint, surfaceContainer = surface2,
        surfaceContainerHigh = hoverTint, surfaceContainerHighest = hoverTint,
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, primaryContainer = soft, onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = accent, onSecondary = onAccent, secondaryContainer = soft, onSecondaryContainer = ink,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = soft, onTertiaryContainer = ink,
        background = paper, onBackground = ink,
        surface = surface, onSurface = ink, surfaceVariant = surface2, onSurfaceVariant = ink2,
        surfaceTint = accent, inverseSurface = ink, inverseOnSurface = paper,
        error = danger, onError = onError, errorContainer = dangerContainer, onErrorContainer = danger,
        outline = hairline, outlineVariant = hairline2,
        scrim = whaleHex("#000000"),
        surfaceContainerLowest = lowestTint, surfaceContainerLow = lowTint, surfaceContainer = surface2,
        surfaceContainerHigh = hoverTint, surfaceContainerHighest = hoverTint,
    )
}

/**
 * 圆角三档进 M3（§7.4 对齐：原型只有三档；小件/卡面/浮层各有用途）。
 * 组合内从 [WhaleShapes] 现值构建——皮肤切换换档后由 remember key 重建。
 */
private fun whaleShapesAsM3(): Shapes = Shapes(
    extraSmall = WhaleShapes.r1,
    small = WhaleShapes.r1,
    medium = WhaleShapes.r2,
    large = WhaleShapes.r3,
    extraLarge = WhaleShapes.r3,
)

/** 当前皮肤的 L1 色板（R4）：深浅两档在 [MystiCatTheme] 里 provide；默认值＝简约浅色（预览/工具用）。 */
val LocalWhalePalette = staticCompositionLocalOf<WhalePalette> {
    WhaleSkin.resolve(WhaleSkin.Minimal, dark = false)
}

/** 当前**生效**的明暗（贴纸等皮肤外物按它调深色档；与 [LocalWhalePalette] 同键同源 provide）。 */
val LocalWhaleDark = staticCompositionLocalOf { false }

/**
 * 内置字族映射。中文下 sans-serif / monospace 都会回退成黑体，故不再提供。
 *
 * **两端都改走平台**（2026-09-17 修正）：
 * - 「衬线」：`FontFamily.Serif` 是"通用族"，桌面（Skia）拿它解析中文时并不会挑到中文衬线体，
 *   用户反馈"系统自带的'衬线'在 windows 中不对中文生效"就是这个原因。桌面实现改为显式挑一个
 *   系统中文字体文件（宋体等），挑不到才回退通用族。
 * - 「系统默认」：`FontFamily.Default` 同样是通用族，中文由 Skia 的 fallback 链决定。⚠ **本机实测
 *   （离屏出图逐像素比对）：fallback 本来就落到微软雅黑，显式挑 msyh.ttc 后中文正文
 *   像素级一致**——所以"方案①"在中文上没有可见收益，它的价值是**别让别的机器/系统语言下 fallback
 *   跑到宋体这类细笔画字体上**（确定性）。用户报的 #9"字体不清晰"另有原因：见 `serifFontFamily`
 *   与「衬线＝宋体」那一档（细笔画 + 灰阶抗锯齿最吃亏），以及 Skia 无 hinting 这个既定事实。
 *
 * 两档都只影响**内置字体**；用户上传的字体文件优先级更高（见 [currentFontFamily]）。
 * 皮肤自带的主题字族（米白＝思源宋等，R4）落点也在这里：字族令牌 → [typographyWith]。
 */
private fun AppFont.toFontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> Platform.ui.sansFontFamily() ?: FontFamily.Default
    AppFont.SERIF -> Platform.ui.serifFontFamily() ?: FontFamily.Serif
}

/**
 * 当前生效的字体族，三档优先级（第 17 轮起）：用户上传 ＞ 皮肤自带 ＞ 用户选的内置档。
 * 皮肤自带档两态：SkinFontRepo 的下载档（就绪路径）直接加载；皮肤点名平台衬线（`serif`，深海）
 * 接衬线族。文件读不出来时静默回退下一档，避免一个坏文件把整个 App 拖崩。
 * 跨平台拆分后字体加载走平台（桌面侧 CMP 1.11 的 Font 只有资源 ID 重载，没有 Font(ByteArray)，
 * 两端各自的实现都从本地文件构造），坏文件在 [isUsableFontFile]（魔数+平台引擎试加载）阶段就被拦在门外。
 */
private fun currentFontFamily(): FontFamily {
    val uploaded = ThemeState.customFontPath
    if (!uploaded.isNullOrBlank() && isUsableFontFile(uploaded)) {
        Platform.ui.loadCustomFontFamily(uploaded)?.let { return it }
    }
    val skinFont = ThemeState.skinFontPath
    if (!skinFont.isNullOrBlank() && isUsableFontFile(skinFont)) {
        Platform.ui.loadCustomFontFamily(skinFont)?.let { return it }
    }
    if (ThemeState.skinFontToken == "serif") {
        // 平台没给具体字族时**必须**退回通用衬线族：原来写 `?.let { return it }`，null 时静默落到
        // 下一档（`ThemeState.font`＝用户自己的内置档），表现成"深海（衬线）切到简约/潮汐再切回来，
        // 字体一动不动"——Android 的 `serifFontFamily()` 就是默认 null（见 `Platform.serifFontFamily`）。
        return Platform.ui.serifFontFamily() ?: FontFamily.Serif
    }
    return ThemeState.font.toFontFamily()
}

/**
 * 把默认 Material3 字体表整体换上指定字族。
 * 字号不在这里改基准——统一由 [MystiCatTheme] 里的 fontScale 缩放负责，
 * 这样用户调的「字号」能同时作用于所有页面。
 *
 * [typeScale]＝字表整体放大系数：桌面正文密度按手机 sp 直排会偏小
 * （用户 2026-10-03「100% 字体应该再大一点」），桌面档整体 ×1.1、手机档 1.0 不动；
 * 与 213 的整像素吸附同一段做（先乘再吸附，行高同比、字形基线仍整像素）。
 */
private fun typographyWith(
    family: FontFamily,
    base: TextStyle?,
    pxPerSp: Float,
    typeScale: Float = 1f
): Typography {
    val d = Typography()
    // base＝桌面侧文字栅格化设置（platformStyle 一项有值）；Android 侧为 null，样式原样返回。
    // 实算（字族 + 桌面 ×1.1 + 整像素吸附）搬去了 [scaleTextStyle]——M3 字表与 L0 的 [WhaleType]
    // 必须共用同一条管线，否则小字档又会掉出整表之外（216 修的正是这个）。
    fun s(ts: TextStyle): TextStyle = scaleTextStyle(ts, family, base, pxPerSp, typeScale)
    return Typography(
        displayLarge = s(d.displayLarge),
        displayMedium = s(d.displayMedium),
        displaySmall = s(d.displaySmall),
        headlineLarge = s(d.headlineLarge),
        headlineMedium = s(d.headlineMedium),
        headlineSmall = s(d.headlineSmall),
        titleLarge = s(d.titleLarge),
        titleMedium = s(d.titleMedium),
        titleSmall = s(d.titleSmall),
        bodyLarge = s(d.bodyLarge),
        bodyMedium = s(d.bodyMedium),
        bodySmall = s(d.bodySmall),
        labelLarge = s(d.labelLarge),
        labelMedium = s(d.labelMedium),
        labelSmall = s(d.labelSmall)
    )
}

@Composable
fun MystiCatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // 动态取色（Android 12 壁纸取色）走平台：桌面实现返回 null，回退皮肤令牌配色
    val dynamic = if (dynamicColor) Platform.ui.dynamicColorScheme(darkTheme) else null
    // L1 皮肤令牌（R4 接线）：ThemeState 的当前皮肤驱动解析，缺键回落简约的口径不变。
    // key 用 skinSpec——spec 对象变了必然伴随 WhaleShapes 已换档（applySkin 内先 spec 后 apply）
    val palette = remember(ThemeState.skinSpec, darkTheme) {
        WhaleSkin.resolve(ThemeState.skinSpec, darkTheme)
    }
    val shapes = remember(ThemeState.skinSpec) { whaleShapesAsM3() }
    val colorScheme = dynamic ?: palette.toMaterialScheme(darkTheme)

    // 问题 #4：用户可调字体与字号。只放大 sp（文字），不动 dp 布局，避免整页排版被撑坏。
    val density = LocalDensity.current
    val fontFamily = remember(
        ThemeState.customFontPath, ThemeState.font,
        ThemeState.skinFontPath, ThemeState.skinFontToken,
    ) { currentFontFamily() }
    // 桌面：文字栅格化设置（亚像素＋hinting，治"字体糊"）从 Platform.ui 取，整表挂上；Android 侧为 null
    val baseStyle = remember { Platform.ui.baseTextStyle() }

    // Density 每次重组都 new 一个（provider 值变化会连带整棵树重组），按输入 remember
    val scaledDensity = remember(density.density, density.fontScale, ThemeState.fontScale) {
        Density(density.density, density.fontScale * ThemeState.fontScale)
    }
    // 每 sp 的真实像素数（显示缩放 × 用户字号档）：桌面据此把排版吸附到整像素
    val pxPerSp = scaledDensity.density * scaledDensity.fontScale
    // 桌面档整表放大一档（2026-10-03「100% 再大一点」）：手机 1.0 逐点不动
    val typeScale = if (isDesktopLayout) 1.1f else 1f
    val typography = remember(fontFamily, baseStyle, pxPerSp, typeScale) {
        typographyWith(fontFamily, baseStyle, pxPerSp, typeScale)
    }
    // L0 小字档（9~15sp，M3 字表装不下的那几档）：与 typography 同参数、同管线实算，
    // 保证页面里不再有绕过整表 ×1.1 与整像素吸附的裸字号（216）。
    val whaleType = remember(fontFamily, baseStyle, pxPerSp, typeScale) {
        whaleTypeAt(fontFamily, baseStyle, pxPerSp, typeScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        // ── 桌面按下指示 ──
        // Compose Desktop 默认的 `LocalIndication` 是 **DefaultDebugIndication**：悬停/按下时给控件套
        // 两圈方框（调试件）。手机端走系统涟漪，只有桌面中招 ⇒ 这里显式换成 M3 的 `ripple()`。
        // ⚠ 这条只治"裸 `Modifier.clickable`"——M3 组件自己传 `ripple()`，不吃 `LocalIndication`。
        // ⚠ 用户报的"胶囊内框"**不是它**：把 `LocalIndication` 换成"什么都不画"的指示后
        // 方框照旧；`RippleConfiguration(color = Transparent)` 也照旧。真正的来源是 M3 `FilterChip`
        // 自己的悬停绘制 ⇒ 已改成自绘胶囊 `WhaleChip`（`ui/Common.kt`，桌面自绘、手机仍用 Material 3）。
        CompositionLocalProvider(
            LocalIndication provides if (isDesktopLayout) ripple() else LocalIndication.current,
            LocalWhalePalette provides palette,
            LocalWhaleDark provides darkTheme,
            LocalWhaleType provides whaleType,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
                shapes = shapes,
                content = content
            )
        }
    }
}
