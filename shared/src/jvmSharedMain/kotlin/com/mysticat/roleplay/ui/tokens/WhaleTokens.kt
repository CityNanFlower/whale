package com.mysticat.roleplay.ui.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * L0 间距令牌：4pt 基数。
 *
 * 界面层（L2/L3）不再写裸 `12.dp`/`16.dp` 这类"看起来差不多"的数，一律从这里取——
 * 同一层里所有留白因此天然成比例，改一处刻度就能整体收紧或放松。
 * 例外：随字号缩放的量（气泡内边距、图标边长）仍按 `ThemeState.fontScale` 现算，
 * 它们属于"跟字走"的量，不是排版栅格。
 */
object WhaleSpacing {
    val none: Dp = 0.dp
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
}

/** L0 圆角刻度：一条尺度，越靠内层越小，避免"每处各圆各的"。取值＝**当前皮肤**的三档（R4 起随皮肤切换）。 */
object WhaleShapes {
    /** 小件：chip、状态胶囊、行内缩略图（声明为 CornerBasedShape，M3 Shapes 直接收得住） */
    var r1: RoundedCornerShape by mutableStateOf(RoundedCornerShape(6.dp))
        private set

    /** 卡片、输入区、弹层里的块 */
    var r2: RoundedCornerShape by mutableStateOf(RoundedCornerShape(12.dp))
        private set

    /** 浮层：长按菜单、下拉、Sheet（原 28dp 的"第四档"已并回这里——原型只有三档，交接书 §7.4） */
    var r3: RoundedCornerShape by mutableStateOf(RoundedCornerShape(18.dp))
        private set

    /** 完全圆角（圆形头像、圆形按钮） */
    val pill: CornerBasedShape = CircleShape

    /**
     * 皮肤切换时整体换档（`ThemeState.setSkin`/`ensureLoaded` 调用）。
     * 三档是 Compose 状态：组合里读它们的地方自动重组，切换即时生效、无需重启。
     */
    fun apply(r1dp: Int, r2dp: Int, r3dp: Int) {
        r1 = RoundedCornerShape(r1dp.dp)
        r2 = RoundedCornerShape(r2dp.dp)
        r3 = RoundedCornerShape(r3dp.dp)
    }
}

/** L0 尺寸令牌：热区与关键控件边长。 */
object WhaleSizes {
    /** 触控最小热区（评审基线：≥44×44） */
    val hit: Dp = 44.dp

    /** 顶栏高度 */
    val topBar: Dp = 56.dp

    /** 聊天页角色头像 */
    val avatar: Dp = 34.dp

    /** 桌面端聊天页角色头像 */
    val avatarDesktop: Dp = 44.dp
}

/** L0 动效令牌：150~300ms ＋ 一条标准减速曲线。慢放开关见 [WhaleMotion.scale]。 */
object WhaleMotion {
    /** 全局倍速：调试用（把动效放慢观察），正常恒为 1f */
    var scale: Float = 1f

    val standardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 微反馈（按压、hover、颜色切换） */
    fun <T> quick() = tween<T>(durationMillis = (150 * scale).toInt(), easing = standardEasing)

    /** 常规进出场 */
    fun <T> enter() = tween<T>(durationMillis = (220 * scale).toInt(), easing = standardEasing)

    /** 面板/页面级转场 */
    fun <T> panel() = tween<T>(durationMillis = (280 * scale).toInt(), easing = standardEasing)
}

/**
 * L0 玻璃（毛玻璃）令牌。
 *
 * 只有"透明度/模糊半径/描边"这一组数在这里，颜色仍从主题取——
 * 皮肤（L1）换色不许动这几个数，否则同一套玻璃在不同皮肤上厚薄不一。
 *
 * ⚠ 真模糊要 Android 12（API 31）或桌面 Skia 才生效，更低的系统上模糊是空操作，
 * 靠 [frost] 那层半透明底仍然读得出"浮起来"的质感（见 `ui/components/WhaleGlass.kt`）。
 */
object WhaleGlass {
    /** 顶栏/悬浮输入区：模糊到只剩色块（半径比整页更大＝局部对比更低，字才立得住） */
    val blurBar: Dp = 22.dp

    /** 整页背景（从聊天页进入的独立页面）：轻模糊，**要认得出是哪张图**，糊成一片反而更抢字 */
    val blurPage: Dp = 12.dp

    /** 浮层底色的不透明度：0.90＝"看得出底下有图、但图不参与阅读" */
    val frost: Float = 0.90f

    /** 独立页那层主题薄纱：比浮层更透，让背景图多露一点（用户 2026-10-01：糊得太厉害） */
    val pageVeil: Float = 0.45f

    /** 弹窗底色透明度（比浮层更实一点，正文可读优先） */
    val dialog: Float = 0.94f

    /**
     * 悬浮输入卡片：比顶栏再透一档（用户 2026-10-01 要求"悬浮＋带一点透明度"）。
     * ⚠ 只把透明度调低、不上 [com.mysticat.roleplay.ui.components.WhaleFrostedPanel]，
     * 得到的只是块**半透明白板**——用户一眼看穿：「你现在这也不是毛玻璃啊」。模糊才是主角。
     */
    val card: Float = 0.85f

    /** 长按菜单这类小浮层：更实，图标要准 */
    val menu: Float = 0.92f

    /** 玻璃边缘那道发丝线 */
    val hairline: Float = 0.38f

    /**
     * 会话顶栏那一条玻璃的底色不透明度（用户 2026-10-03："还是做成透卡"）。
     * 比 [frost] 明显低：0.90 的半透明底实到看不出是"透卡"，背景图基本透不出来。
     * 也别再往下压——顶栏里坐着角色名与会话名，压过头字就压进图里（10-01 那次就是这么翻车的）。
     */
    val barTint: Float = 0.62f
}

/**
 * L0 字号令牌：M3 字表装不下的那几档小字。
 *
 * M3 的 15 个槽位最小只到 `labelSmall`＝11sp，可界面里真正的"最小一档"是 9~10sp
 * （角标计数、时刻刻度、类型牌），13/15sp 同样没有对应槽位。这些以前是散在页面里的裸
 * `fontSize = 10.sp`，**绕开了 `Theme.kt · typographyWith`**——于是桌面的整表 ×1.1 与
 * 整像素吸附（213/214 两轮定的口径）都落不到它们身上，用户看到的正是"同一级文字这页
 * 10sp、那页大一号"。这里补齐 9~15sp 这一段，并让它们**与 M3 字表走同一条
 * [scaleTextStyle] 管线**：桌面整表 ×1.1、字号与行高吸附到整数像素，手机档 1.0 逐点不动。
 *
 * 取值口径：行高按 ≈1.4 配（213 定的行高同比跟着字号一起吸附，基线仍需整像素）。
 * 字距不进这里——各站点的 tracking 是逐处设计决定（0.03~0.18sp），仍在调用点声明。
 *
 * ⚠ **不进这一套的**（都是几何而不是排版，别顺手"归并"它们）：
 * ① `AvatarOrInitial` / `InitialGlyph` 的首字（16~44sp）——按所在圆或槽的直径配比
 *    （34dp→16sp、40dp→18sp、50dp→22sp、62dp→26sp、铺满封面→44sp），跟着字表放大一号会顶出圆外；
 * ② `WhaleSticker` 的字号——按贴纸倍率现算，属于贴纸自己的缩放；
 * ③ `SkinCenterSection` 皮肤样张里的字号——卡片是**缩略图**，随样张缩放，不代表应用排版。
 * 这三类之外，页面里不该再有裸 `fontSize`：裸值会绕开桌面 ×1.1 与整像素吸附（216 的教训），
 * 也会让用户调字号时这处跟那处不成比例（218 复查：`ChatMessages`/`ManualScreen` 曾把字号档乘了两遍）。
 */
@Immutable
class WhaleType(
    /** 9sp：最小一档，只出现在书脊格下方的条目计数 */
    val micro: TextStyle,
    /** 10sp：角标。计数（"N 个会话"/"共 N 个角色"）、类型牌、日期刻度 */
    val tiny: TextStyle,
    /** 11sp：边注。时刻刻度、次要说明、错误态末尾那行提示 */
    val meta: TextStyle,
    /** 12sp：副行。会话行副标题、分段控件标签、竖排书脊的槽内文字 */
    val caption: TextStyle,
    /** 13sp：摘要。末条预览、名册简介与问候语 */
    val small: TextStyle,
    /** 14sp：行标题。会话名、段头标签 */
    val label: TextStyle,
    /** 15sp：强调标题。错误态标题、名册行名 */
    val strong: TextStyle
) {
    /**
     * 等宽小字：尺寸取字阶的某一档（于是同样吃桌面 ×1.1 与整像素吸附），字族换成等宽。
     * 站点里要等宽就写 `type.mono(type.tiny)`——别再手拼 `TextStyle(Monospace, fontSize = 10.5.sp)`：
     * 那种半像素值在桌面上必落分数像素（字发虚），而且与同页的字阶不同步。
     */
    fun mono(base: TextStyle): TextStyle = base.copy(fontFamily = FontFamily.Monospace)
}

/**
 * 字号/行高/字距的实算：M3 字表与 [WhaleType] 共用这一条管线。
 *
 * ⚠ 桌面侧（`base != null`）额外做两件事，**都在这里，不许在别处再实现一遍**：
 * ① 整表乘 [typeScale]（桌面档 ×1.1，用户 2026-10-03「100% 字体应该再大一点」）；
 * ② 字号/行高/字距**吸附到整数像素**（先乘再吸附）——用户字号档非整倍率时（如 1.1×1.5）
 * 不吸附会让全页文字落分数 x，"所有页面字体都发虚"（213 实测）。
 * 手机侧 `base` 为 null，样式原样返回。
 */
internal fun scaleTextStyle(
    ts: TextStyle,
    family: FontFamily,
    base: TextStyle?,
    pxPerSp: Float,
    typeScale: Float
): TextStyle {
    var out = if (family == FontFamily.Default) ts else ts.copy(fontFamily = family)
    if (base != null && pxPerSp > 0f) {
        out = out.copy(platformStyle = base.platformStyle)
        val fs = out.fontSize
        if (fs != TextUnit.Unspecified && fs.value > 0f) {
            val px = (fs.value * typeScale * pxPerSp).roundToInt().coerceAtLeast(1)
            out = out.copy(fontSize = (px / pxPerSp).sp)
        }
        val lh = out.lineHeight
        if (lh != TextUnit.Unspecified && lh.value > 0f) {
            val px = (lh.value * typeScale * pxPerSp).roundToInt().coerceAtLeast(1)
            out = out.copy(lineHeight = (px / pxPerSp).sp)
        }
        val ls = out.letterSpacing
        if (ls != TextUnit.Unspecified) {
            out = out.copy(letterSpacing = (ls.value * typeScale * pxPerSp).roundToInt().sp)
        }
    }
    return out
}

/** 按当前密度管线实算出一套 [WhaleType]（正常组合里由 [LocalWhaleType] 提供，别在页面里直接调）。 */
internal fun whaleTypeAt(
    family: FontFamily,
    base: TextStyle?,
    pxPerSp: Float,
    typeScale: Float
): WhaleType = WhaleType(
    micro = scaleTextStyle(TextStyle(fontSize = 9.sp, lineHeight = 13.sp), family, base, pxPerSp, typeScale),
    tiny = scaleTextStyle(TextStyle(fontSize = 10.sp, lineHeight = 15.sp), family, base, pxPerSp, typeScale),
    meta = scaleTextStyle(TextStyle(fontSize = 11.sp, lineHeight = 17.sp), family, base, pxPerSp, typeScale),
    caption = scaleTextStyle(TextStyle(fontSize = 12.sp, lineHeight = 17.sp), family, base, pxPerSp, typeScale),
    small = scaleTextStyle(TextStyle(fontSize = 13.sp, lineHeight = 19.sp), family, base, pxPerSp, typeScale),
    label = scaleTextStyle(TextStyle(fontSize = 14.sp, lineHeight = 20.sp), family, base, pxPerSp, typeScale),
    strong = scaleTextStyle(TextStyle(fontSize = 15.sp, lineHeight = 21.sp), family, base, pxPerSp, typeScale)
)

/**
 * 当前生效的小字档。缺省值＝未经密度管线的基准档（只在 `MystiCatTheme` 之外读到时兜底，
 * 那条路径不存在），真正生效的值由主题按 ×1.1 与整像素吸附实算后提供。
 */
val LocalWhaleType = staticCompositionLocalOf { whaleTypeAt(FontFamily.Default, null, 0f, 1f) }
