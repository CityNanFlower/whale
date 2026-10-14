package com.mysticat.roleplay.ui.tokens

import androidx.compose.ui.graphics.Color

/**
 * 非皮肤的固定覆盖色（L0 原始令牌）：压暗 scrim、工具暗室、图表分类色板。
 *
 * 它们不随皮肤换——图上压字的可读性 scrim、"编辑器恒暗的暗室底"、
 * "同一根色恒指同一个模型"的图表色相，都与皮肤无关；
 * 但也不许再散在屏幕里写字面色（R2 起的 grep 闸门：`ui/` 里 `Color(` 加十六进制必须为 0）。
 */
object WhaleOverlays {

    /** 会话区背景图上的竖直 scrim 三档（顶栏那份；历史档位原值保留）。 */
    val chatScrims: List<Color> = listOf(whaleHex("#66000000"), whaleHex("#33000000"), whaleHex("#99000000"))

    /** 会话面板背景图的 scrim 三档（0xCC → 0x73 → 透明）。 */
    val sessionScrims: List<Color> = listOf(whaleHex("#CC000000"), whaleHex("#73000000"), whaleHex("#00000000"))

    /** 裁剪/贴图编辑器的暗室底：两端恒暗、不随主题，图是主角。 */
    val studioBackdrop: Color = whaleHex("#101018")

    /** 暗室底上的错误文字（红相提亮到在近黑底上可读）。 */
    val studioDanger: Color = whaleHex("#FF8A80")

    /** 暗室底上的操作提示文字。 */
    val studioHint: Color = whaleHex("#BBBBD0")

    /** 裁剪取景框四周的压暗遮罩。 */
    val cropMask: Color = whaleHex("#AA000000")

    /**
     * 用量图表的模型分类色板（10 相）。**长度必须等于 `UsageLog.MODEL_PALETTE_SIZE`**
     * ——色号＝`modelColorIndex(模型名) % 长度`，两张对不上就会数组越界（桌面自检断言这条）。
     * 十个色相彼此拉开、跨皮肤恒定：同一根色恒指同一个模型。
     */
    val chartPalette: List<Color> = listOf(
        whaleHex("#4C8DFF"), whaleHex("#F2994A"), whaleHex("#27AE60"), whaleHex("#EB5757"),
        whaleHex("#9B51E0"), whaleHex("#2D9CDB"), whaleHex("#B8860B"), whaleHex("#56A6CC"),
        whaleHex("#BB6BD9"), whaleHex("#6FCF97"),
    )
}
