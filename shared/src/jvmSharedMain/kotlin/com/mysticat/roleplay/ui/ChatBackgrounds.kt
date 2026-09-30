package com.mysticat.roleplay.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Conversation

/**
 * 聊天背景分端（A 批次，用户 2026-09-18 原话）：
 * "创建角色卡可以**同时导入/生成手机端和桌面端聊天背景**（会话中聊天背景设置也同理）"。
 *
 * 为什么必须分：桌面窗口是横的、手机是竖的，同一张图在两端不可能都对——竖图铺到横向三栏上被
 * `ContentScale.Crop` 放大到只剩中间一条（这正是用户报的"桌面端的聊天背景尺寸和手机端不一样"）。
 * 修的是同一句话背后的另一个独立 bug（桌面裁剪框比例取成了高/宽），那个已修；这里解决的是
 * "一张图伺候两种窗口形状"本身。
 *
 * **存储形态（老数据不迁移、打开即可用）**
 * - `backgroundUri`        手机端那份，同时也是唯一的历史字段；
 * - `backgroundUriDesktop` 桌面端那份：`null` = 没单独设过 ⇒ 回退用手机端那份；
 *                          `""` = 明确不要背景（所以「移除」写空串，写 null 会让那张图又冒出来）。
 *
 * 分层：`null`（没设过）与 `""`（设成了"不要"）必须能区分，否则"移除桌面端背景"在只有手机端背景的
 * 卡上会表现为"移不掉"。渲染方拿到空串按"没有背景"处理，这与既有代码里
 * `takeIf { it.isNotBlank() }` 的口径一致。
 */
enum class BgTarget(val kind: String, val label: String) {
    Phone("background", "手机端"),
    Desktop("backgroundDesktop", "桌面端");

    companion object {
        /** 本平台对应的那一份（两端"设置背景"入口的默认目标） */
        val current: BgTarget get() = if (isDesktopLayout) Desktop else Phone

        /**
         * 从"生图/预览的 kind 字符串"反查背景端；`"avatar"` 与其它值返回 null。
         * 编辑页与聊天页都用 kind 字符串当目标标识（它们要跟 `pendingKind` 这种老账本对齐），
         * 所以两端之间必须有且只有这一处转换。
         */
        fun ofKind(kind: String?): BgTarget? = entries.firstOrNull { it.kind == kind }
    }
}

/** 该端生效的背景；空串 = 明确不要背景（调用方按"没有"处理） */
fun CharacterCard.backgroundFor(target: BgTarget): String? =
    if (target == BgTarget.Desktop) backgroundUriDesktop ?: backgroundUri else backgroundUri

fun Conversation.backgroundFor(target: BgTarget): String? =
    if (target == BgTarget.Desktop) backgroundUriDesktop ?: backgroundUri else backgroundUri

/**
 * 会话覆盖优先、回退角色卡默认（分端）。会话那份写的是空串时**不回退**——用户在本会话里
 * 点过"移除背景图"，那张图就不该再冒出来。
 */
fun effectiveBackground(
    card: CharacterCard?,
    conv: Conversation?,
    target: BgTarget = BgTarget.current
): String? = conv?.backgroundFor(target) ?: card?.backgroundFor(target)

/** 本平台生效的角色背景（角色卡导出、列表缩略图等"没会话上下文"的地方用） */
fun CharacterCard.backgroundHere(): String? = backgroundFor(BgTarget.current)

/**
 * 会话级背景暗化强度：`Conversation.bgDim ?: 1f`。
 * 历史写死的那两组 scrim（顶栏 0xCC→0x73→0x00、消息区 0x66→0x33→0x99）就是强度 1f 的档位；
 * 0f = 图完全不暗（顶栏/气泡仍是半透明底，文字不至于压在原图上看不清）。
 */
fun Conversation.bgDimOrDefault(): Float = bgDim ?: 1f

/** 把一组 scrim 基色按暗化强度缩放透明度；强度≈1 时原样返回（省一次重组级的颜色计算） */
fun dimmedScrims(colors: List<Color>, dim: Float): List<Color> =
    if (dim >= 0.999f) colors
    else colors.map { it.copy(alpha = it.alpha * dim.coerceIn(0f, 1f)) }

/**
 * 消息区背景图上的那层竖直 scrim（顶栏、输入栏各有自己的一份）。
 *
 * 会话内的二级浮层要求"背景跟会话页一样"，铺的就得是同一组值——放这里当单一来源，
 * 别在第二个渲染点再抄一遍三色。
 */
val ChatAreaScrims: List<Color> = listOf(Color(0x66000000), Color(0x33000000), Color(0x99000000))

private const val PhoneBgAspectNominal = 0.75f
/** 桌面端名义比例 = 默认窗口 1440×900（desktopApp/Main.kt） */
private const val DesktopBgAspectNominal = 1.6f

/**
 * 某个背景槽位的裁剪框比例。
 *
 * 本平台取**真实**屏幕/窗口比例（手机上量不到桌面窗口尺寸，反之亦然），另一端给名义值——
 * 手机端竖屏按 3:4（正是历史写死的那个值），桌面端横屏按 16:10（默认窗口比例）。
 */
@Composable
fun rememberBgCropAspect(target: BgTarget): Float = when {
    target == BgTarget.current ->
        rememberScreenAspect(if (target == BgTarget.Desktop) DesktopBgAspectNominal else PhoneBgAspectNominal)
    target == BgTarget.Desktop -> DesktopBgAspectNominal
    else -> PhoneBgAspectNominal
}

/** 生图尺寸默认值：横图给桌面、竖图给手机（`ImageSizeOptions` 里两边都有现成档位） */
fun defaultBgImageSize(target: BgTarget): String =
    if (target == BgTarget.Desktop) "1024x768" else "768x1024"
