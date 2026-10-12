package com.mysticat.roleplay.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * "#RRGGBB" / "#AARRGGBB" → Color。
 *
 * 皮肤令牌一律以十六进制**字符串**存取、在这里统一解析——`ui/` 整棵树不再出现
 * "Color 加十六进制"的字面量（R2 起的 grep 闸门：ui/ 下这类写法必须为 0）。
 * 八位串按带 alpha 处理（全透明的 `#00000000` 必须原样保留，不能被当 RGB 补成不透明黑）。
 */
internal fun whaleHex(s: String): Color {
    val body = s.removePrefix("#")
    val v = body.toLong(16)
    val argb = if (body.length >= 8) v else (0xFF000000L or v)
    return Color(argb.toInt())
}

/** 解析后的皮肤色板（L1 语义令牌的运行时形态；取值来源＝简约皮肤的令牌真值表）。 */
@Immutable
data class WhalePalette(
    val paper: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val hairline: Color,
    val hairline2: Color,
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val softLine: Color,
    val danger: Color,
    /** accent 派生档：作**文字**用时的保底色（对 paper/surface/surface2/soft 四面 ≥4.5:1）。CTA/徽标仍用 [accent]。 */
    val accentText: Color,
    /** danger 派生档：作**卡面文字**用时的保底色（同上四面）。破坏性按钮图标仍用 [danger]。 */
    val dangerText: Color,

    /**
     * 文本框框线：**比结构线明显一档**（"要动手的地方"才用它）。
     * 分组卡外框走 `outline`、卡内发丝走 `outlineVariant`，三者不许混用（v10 DESIGN §1）。
     * 皮肤表没给 `fieldLine` 时按 `ink` 18% 叠在卡面上推导——浅色皮肤得浅灰蓝、深色皮肤得浅灰，
     * 用户 2026-10-08：「文本框的框线可以再明显一点」。
     */
    val fieldLine: Color,
)

/**
 * 一套皮肤＝一份 [WhaleSkinSpec]：浅/深两档 L1 令牌表 + 字族 + 结构签名 + 贴纸预设。
 *
 * 键名（light/dark 两张表同构）：`paper / surface / surface2 / ink / ink2 / ink3 /
 * hairline / hairline2 / accent / onAccent / soft / softLine / danger`；
 * 另有两个派生键 `accentText / dangerText`（一般不必写——缺省时按 §一 不变量 4 现场推导）。
 *
 * 四条不变量（任何皮肤，包括 R5 起的社区/自定义主题，都不得绕开）：
 * 1. **缺键回落简约对应值**——[WhaleSkin.resolve] 保证皮肤永远掀不翻 App；
 * 2. `interactions` / `route` / `menu` 不在 schema 里（未知键解析时被忽略），
 *    导入入口的「仅支持视觉令牌」提示由皮肤中心（R5）负责；
 * 3. `motif` 只认封闭枚举 `none / sticker / postmark / filmstrip`（[WhaleSkin.motifOf]），
 *    贴纸是"物件"不是"底纹"：单页 ≤3 件、只落页边与卡片角、永不压正文。
 * 4. `accent` / `danger` 的冻结值不因对比度改写——文字可读性由 `accentText` / `dangerText`
 *    派生档兜底（向 `ink` 调混到对 paper/surface/surface2/soft 四面最差者 ≥4.5:1），
 *    CTA / 徽标等大字与图形场景仍用原值。
 */
@Serializable
data class WhaleSkinSpec(
    val id: String = "minimal",
    val name: String = "简约",
    val version: Int = 1,
    val light: Map<String, String> = emptyMap(),
    val dark: Map<String, String> = emptyMap(),
    /** 主题字体令牌（第 17 轮接进 typography）：ui/chat 两槽本版同值生效；meta 保留不接线。 */
    val fonts: WhaleSkinFonts = WhaleSkinFonts(),
    /** 结构签名（R4 起生效）：圆角三档 dp 与投影开关。 */
    val shape: WhaleSkinShape = WhaleSkinShape(),
    /** 主题贴纸预设名（封闭枚举，见类注 3）。 */
    val motif: String = "none",
    val motifSize: Float = 1f,
    /** 深色档贴纸比浅档约 +0.1（深色纸面反差小，提一档）。 */
    val motifOpacity: Float = 1f,
)

/**
 * 主题字族令牌（三槽：ui / chat / meta）。
 *
 * 取值两族：`sans`＝平台黑体、`serif`＝平台衬线（见 Theme 的衬线档）——不下载；
 * `notoserif` / `yozai` / `wenkai` / `notomono`＝皮肤字体仓库的下载档
 * （[com.mysticat.roleplay.data.SkinFontRepo]，按需拉取、sha256 校验后缓存本地）。
 * 未知令牌一律回落 `sans`。meta 槽（mono）中文本就回落黑体，字段保留、本版不接线。
 */
@Serializable
data class WhaleSkinFonts(val ui: String = "sans", val chat: String = "sans", val meta: String = "mono")

/** 结构签名：三档圆角各有用途（小件 / 卡片与面板 / 浮层），shadow = none 表示皮肤不自带投影。 */
@Serializable
data class WhaleSkinShape(val r1: Int = 6, val r2: Int = 12, val r3: Int = 18, val shadow: String = "none")

/**
 * 一套**自定义**皮肤（用户导入的 `.whale-skin.json`，或从皮肤中心下载的那一份）。
 *
 * 2026-10-04 加，配合 `AppSettings.customSkins`（存的是**原文列表**而不是这个类型——
 * `AppSettings` 在 `commonMain`，`WhaleSkinSpec` 在 `jvmSharedMain`，源集可见性够不着，
 * 只能存字符串。这不是绕路，是那条硬约束逼出来的唯一形态）。
 *
 * 同时留 [json] 原文：导出／复制到剪贴板要的就是原文（用户粘给别人、或者日后自己再导入），
 * 有了它不必把 spec 重新序列化一遍（序列化会丢掉未知键，也会重排顺序）。
 */
@Immutable
data class CustomSkin(val spec: WhaleSkinSpec, val json: String) {
    val id: String get() = spec.id
    val name: String get() = spec.name
}

/** L1 皮肤装载与回落。 */
object WhaleSkin {

    /**
     * 解析一份 `.whale-skin.json`。**坏 JSON / 空文本返回 null**，由调用处决定怎么报错
     * （发现页那侧报「不是有效的皮肤」，导入那侧提示重选文件——不给静默回落，
     * 皮肤是装饰性的但坏文件属于**内容问题**，静默套简约会让用户以为"下了个没变化的东西"）。
     */
    fun parseCustom(json: String): CustomSkin? =
        json.takeIf { it.isNotBlank() }
            ?.let { runCatching { skinJson.decodeFromString<WhaleSkinSpec>(it) }.getOrNull() }
            ?.let { CustomSkin(it, json) }

    private val skinJson = Json { ignoreUnknownKeys = true }

    /**
     * 内置默认皮肤「简约」＝中性灰白 + 一个蓝（用户 2026-10-01 拍板：参考 ChatGPT/DeepSeek 风格，
     * 不能纯白且要带蓝）。**这张表是令牌真值，照抄不许自调**；潮汐在 R4 落第二档。
     */
    val Minimal = WhaleSkinSpec(
        id = "minimal",
        name = "简约",
        light = mapOf(
            "paper" to "#F5F6F4", "surface" to "#FFFFFF", "surface2" to "#FAFBFA",
            "ink" to "#15171C", "ink2" to "#6B7078", "ink3" to "#656A72",
            "hairline" to "#E5E7E4", "hairline2" to "#EFF1EE",
            "accent" to "#2E6BE6", "onAccent" to "#FFFFFF",
            "soft" to "#EAF0FE", "softLine" to "#D6E2FB", "danger" to "#CE3B33",
        ),
        dark = mapOf(
            "paper" to "#17181B", "surface" to "#1E2023", "surface2" to "#24272B",
            "ink" to "#ECEDEF", "ink2" to "#9BA1A8", "ink3" to "#A6ABB1",
            "hairline" to "#2B2E32", "hairline2" to "#33363B",
            "accent" to "#6E9CFF", "onAccent" to "#0E1116",
            "soft" to "#1E2A3E", "softLine" to "#2A3A54", "danger" to "#E2564B",
        ),
    )

    /**
     * 内置第二档皮肤「潮汐」（用户 2026-10-01 拍板：内置＝简约＋潮汐）。
     * 九键真值照抄原型 v6（交接书 §2.2），**色值一个字不动**；
     * ink3/hairline2/onAccent/softLine 缺键回落简约＝既有不变量。
     * 结构签名：圆角三档 8/14/20（比简约大一档）；场景分隔双线与头像描边由调用侧按 id 分支。
     */
    val Tidal = WhaleSkinSpec(
        id = "tidal",
        name = "潮汐",
        light = mapOf(
            "paper" to "#EDF4F9", "surface" to "#FFFFFF", "surface2" to "#F6FAFC",
            "ink" to "#0A2434", "ink2" to "#4E7086",
            "hairline" to "#D7E5EE",
            "accent" to "#1F8AC6",
            "soft" to "#E2F1FA",
            "danger" to "#BF3B2E",
        ),
        dark = mapOf(
            "paper" to "#071522", "surface" to "#0D2233", "surface2" to "#123044",
            "ink" to "#E6F2F9", "ink2" to "#A3BFD0",
            "hairline" to "#17364B",
            "accent" to "#5EB4E5",
            "soft" to "#10344A",
            "danger" to "#E4594A",
        ),
        shape = WhaleSkinShape(r1 = 8, r2 = 14, r3 = 20),
    )

    private val KEYS = setOf(
        "paper", "surface", "surface2", "ink", "ink2", "ink3",
        "hairline", "hairline2", "accent", "onAccent", "soft", "softLine", "danger",
        "accentText", "dangerText", "fieldLine",
    )

    /**
     * 解析出运行时色板：皮肤表缺哪个键就回落简约对应值，多余/未知键忽略
     * （`interactions` 等非视觉键因此天然被丢掉）。
     *
     * `accentText` / `dangerText`：皮肤表给了就直接用；没给则现场推导——
     * 向 `ink` 按 5% 步进调混，直到对 paper/surface/surface2/soft 四面最差者 ≥4.5:1。
     * 推导只产出派生档，`accent`/`danger` 原值不动（不变量 4）。
     */
    fun resolve(spec: WhaleSkinSpec, dark: Boolean): WhalePalette {
        val base = if (dark) Minimal.dark else Minimal.light
        val over = (if (dark) spec.dark else spec.light).filterKeys { it in KEYS }
        val m = base + over
        val paper = whaleHex(m.getValue("paper"))
        val surface = whaleHex(m.getValue("surface"))
        val surface2 = whaleHex(m.getValue("surface2"))
        val ink = whaleHex(m.getValue("ink"))
        val accent = whaleHex(m.getValue("accent"))
        val danger = whaleHex(m.getValue("danger"))
        val soft = whaleHex(m.getValue("soft"))
        val faces = listOf(paper, surface, surface2, soft)
        val accentText = m["accentText"]?.let { whaleHex(it) } ?: deriveTextOnFaces(accent, ink, faces)
        val dangerText = m["dangerText"]?.let { whaleHex(it) } ?: deriveTextOnFaces(danger, ink, faces)
        return WhalePalette(
            paper = paper,
            surface = surface,
            surface2 = surface2,
            ink = ink,
            ink2 = whaleHex(m.getValue("ink2")),
            ink3 = whaleHex(m.getValue("ink3")),
            hairline = whaleHex(m.getValue("hairline")),
            hairline2 = whaleHex(m.getValue("hairline2")),
            accent = accent,
            onAccent = whaleHex(m.getValue("onAccent")),
            soft = soft,
            softLine = whaleHex(m.getValue("softLine")),
            danger = danger,
            accentText = accentText,
            dangerText = dangerText,
            fieldLine = m["fieldLine"]?.let { whaleHex(it) } ?: ink.copy(alpha = 0.18f),
        )
    }

    /** 贴纸预设名的封闭校验：只认 4 个预设名，其余（含社区 JSON 里的自由值）一律回落 `none`。 */
    fun motifOf(raw: String?): String = when (raw) {
        "sticker", "postmark", "filmstrip" -> raw
        else -> "none"
    }

    /** 内置皮肤注册表（R4：简约＋潮汐起步；R5 皮肤中心再接社区 JSON）。 */
    /** 深海（2026-10-04 进内置：皮肤原型 v7-themes 第 ① 块，配色／圆角／贴纸照抄原型） */
    val Deepsea = WhaleSkinSpec(
        id = "deepsea",
        name = "深海",
        light = mapOf(
            "paper" to "#F1F6F8", "surface" to "#FFFFFF", "surface2" to "#E6EFF2",
            "ink" to "#0C2A33", "ink2" to "#4A6672", "ink3" to "#415D69",
            "hairline" to "#D5E3E8", "hairline2" to "#E3ECF0", "accent" to "#0E8A99",
            "onAccent" to "#FFFFFF", "soft" to "#E2F1F2", "softLine" to "#BFDEE0",
            "danger" to "#B8382C",
        ),
        dark = mapOf(
            "paper" to "#03101A", "surface" to "#0A1F2B", "surface2" to "#102C3A",
            "ink" to "#D9EBF1", "ink2" to "#86A6B4", "ink3" to "#92B0BD",
            "hairline" to "#14374A", "hairline2" to "#0C2432", "accent" to "#45D8C4",
            "onAccent" to "#02161A", "soft" to "#0D2E33", "softLine" to "#17454B",
            "danger" to "#E2564B",
        ),
        shape = WhaleSkinShape(6, 10, 16),
        fonts = WhaleSkinFonts(ui = "serif", chat = "serif"),
    )

    /**
     * 米白（2026-10-04 进内置：皮肤原型 v7-themes 第 ① 块，配色／圆角／贴纸照抄原型）。
     * 2026-10-08 用户改名：显示名由「羊皮纸」改为**「米白」**——**id 仍是 `parchment`**
     * （它是落盘键，改它会把已存设置与备份里的这套皮肤读丢）。
     */
    val Parchment = WhaleSkinSpec(
        id = "parchment",
        name = "米白",
        light = mapOf(
            "paper" to "#F0EEE6", "surface" to "#FFFFFF", "surface2" to "#E8E4D9",
            "ink" to "#1F1E1D", "ink2" to "#6B6659", "ink3" to "#605B50",
            "hairline" to "#DED9CC", "hairline2" to "#E7E4D9", "accent" to "#BD5D3A",
            "onAccent" to "#FFFFFF", "soft" to "#F2E7DF", "softLine" to "#E2CBBC",
            "danger" to "#B33A2B",
        ),
        dark = mapOf(
            "paper" to "#1F1E1D", "surface" to "#262624", "surface2" to "#30302E",
            "ink" to "#F0EEE6", "ink2" to "#A8A296", "ink3" to "#B3ADA2",
            "hairline" to "#3A3936", "hairline2" to "#2C2C2A", "accent" to "#D97757",
            "onAccent" to "#1A0F0A", "soft" to "#33261F", "softLine" to "#4A342A",
            "danger" to "#E2564B",
        ),
        shape = WhaleSkinShape(4, 8, 14),
        fonts = WhaleSkinFonts(ui = "notoserif", chat = "notoserif"),
    )

    /** 手账（2026-10-04 进内置：皮肤原型 v7-themes 第 ① 块，配色／圆角／贴纸照抄原型） */
    val Journal = WhaleSkinSpec(
        id = "journal",
        name = "手账",
        light = mapOf(
            "paper" to "#EFE7D8", "surface" to "#FBF6EA", "surface2" to "#E7DCC6",
            "ink" to "#241E17", "ink2" to "#6A5B49", "ink3" to "#605242",
            "hairline" to "#DDD0B8", "hairline2" to "#E6DCC8", "accent" to "#2F5D57",
            "onAccent" to "#FFFFFF", "soft" to "#E8E0CC", "softLine" to "#CFC3A8",
            "danger" to "#B23A2C",
        ),
        dark = mapOf(
            "paper" to "#17130E", "surface" to "#201A14", "surface2" to "#2A231A",
            "ink" to "#EDE2CE", "ink2" to "#A99A80", "ink3" to "#B3A58C",
            "hairline" to "#3A3126", "hairline2" to "#28221A", "accent" to "#8FBFA6",
            "onAccent" to "#10201C", "soft" to "#22302A", "softLine" to "#35483F",
            "danger" to "#E2564B",
        ),
        shape = WhaleSkinShape(3, 6, 10),
        fonts = WhaleSkinFonts(ui = "yozai", chat = "yozai"),
        motif = "sticker",
    )

    /** 书信（2026-10-04 进内置：皮肤原型 v7-themes 第 ① 块，配色／圆角／贴纸照抄原型） */
    val Letter = WhaleSkinSpec(
        id = "letter",
        name = "书信",
        light = mapOf(
            "paper" to "#F4EDDF", "surface" to "#FBF6EA", "surface2" to "#EAE0CD",
            "ink" to "#26201A", "ink2" to "#6B5D4C", "ink3" to "#615444",
            "hairline" to "#DCD0BA", "hairline2" to "#E8DECC", "accent" to "#B23A2C",
            "onAccent" to "#FFFFFF", "soft" to "#F1E4DC", "softLine" to "#DCC4B8",
            "danger" to "#A32F22",
        ),
        dark = mapOf(
            "paper" to "#14110D", "surface" to "#221C17", "surface2" to "#2C241D",
            "ink" to "#EFE6D2", "ink2" to "#A79680", "ink3" to "#B2A28C",
            "hairline" to "#3C332A", "hairline2" to "#28221C", "accent" to "#C8593F",
            "onAccent" to "#160C08", "soft" to "#33241D", "softLine" to "#4A3229",
            "danger" to "#E2564B",
        ),
        shape = WhaleSkinShape(2, 4, 8),
        fonts = WhaleSkinFonts(ui = "wenkai", chat = "wenkai"),
        motif = "postmark",
    )

    /** 暗房（2026-10-04 进内置：皮肤原型 v7-themes 第 ① 块，配色／圆角／贴纸照抄原型） */
    val Darkroom = WhaleSkinSpec(
        id = "darkroom",
        name = "暗房",
        light = mapOf(
            "paper" to "#EFEAE0", "surface" to "#F7F3EA", "surface2" to "#E4DED1",
            "ink" to "#20190F", "ink2" to "#6A6152", "ink3" to "#5F5648",
            "hairline" to "#D8D1C2", "hairline2" to "#E4DED1", "accent" to "#B0762A",
            "onAccent" to "#FFFFFF", "soft" to "#F0E6D6", "softLine" to "#DCC9A8",
            "danger" to "#A8352A",
        ),
        dark = mapOf(
            "paper" to "#0B0A08", "surface" to "#141210", "surface2" to "#1D1A16",
            "ink" to "#E8E0CE", "ink2" to "#A69C88", "ink3" to "#B0A692",
            "hairline" to "#302B24", "hairline2" to "#1E1A16", "accent" to "#E8A33D",
            "onAccent" to "#14100A", "soft" to "#2A2115", "softLine" to "#45351C",
            "danger" to "#E2564B",
        ),
        shape = WhaleSkinShape(2, 6, 10),
        fonts = WhaleSkinFonts(ui = "notomono", chat = "notomono"),
        motif = "filmstrip",
    )

    /**
     * 内置皮肤注册表。
     *
     * 2026-10-04 起除简约／潮汐外另有五套（深海／米白／手账／书信／暗房），取值照抄皮肤原型 v7-themes 的 `[data-skin=…]` 块。原型的 CSS 变量名与本表的键名
     * 不同源（`--hair`/`--onaccent`/`--softline` vs `hairline`/`onAccent`/`softLine`），映射时改了名；
     * 原型没给的 `ink3`／`hairline2` 由 `ink2`→`ink` 与 `hairline`→`paper` 各混一段派生
     * （见 `轮次详情` 第 ① 块那段记录），不是拍脑袋填的。
     *
     * 字体绑定（2026-10-07 第 17 轮）：五套里四套绑下载档（米白→思源宋、手账→Yozai、
     * 书信→文楷、暗房→Noto 等宽），深海绑平台衬线（原型口径：系统宋体回落，不下载）。
     * 下载档不进包，套用时经 `SkinFontRepo` 拉取；未就绪先按内置字族渲染，到位后全局换字。
     * （原型/`SkinFontRepo` 里出现的旧名「羊皮纸」＝这套皮肤，显示名已于 2026-10-08 改「米白」。）
     */
    fun builtins(): List<WhaleSkinSpec> =
        listOf(Minimal, Tidal, Deepsea, Parchment, Journal, Letter, Darkroom)

    /** 按 id 取内置皮肤；未知 id 返回 null（调用侧回落简约）。 */
    fun builtinById(id: String?): WhaleSkinSpec? = builtins().firstOrNull { it.id == id }

    /**
     * WCAG 相对亮度（sRGB 线性化；系数与阈值取自 W3C 对比度算法）。
     */
    private fun relativeLuminance(c: Color): Float {
        fun channel(v: Float): Float =
            if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    }

    /** WCAG 对比度（≥1；两个方向的较亮者做分子）。 */
    private fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb) + 0.05f
        val lo = minOf(la, lb) + 0.05f
        return hi / lo
    }

    /** sRGB 线性插值（不透明；调混方向 from → to）。 */
    private fun mix(from: Color, to: Color, t: Float): Color = Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
    )

    /**
     * 从 [raw] 向 [ink] 按 5% 步进调混，取**第一个**对全部 [faces] ≥4.5:1 的值；
     * 原值已达标就原样返回，全程不达标（理论不可达：ink 是正文色）回落 ink。
     */
    private fun deriveTextOnFaces(raw: Color, ink: Color, faces: List<Color>): Color {
        if (faces.all { contrastRatio(raw, it) >= 4.5f }) return raw
        var t = 0.05f
        while (t <= 1f) {
            val mixed = mix(raw, ink, t)
            if (faces.all { contrastRatio(mixed, it) >= 4.5f }) return mixed
            t += 0.05f
        }
        return ink
    }
}
