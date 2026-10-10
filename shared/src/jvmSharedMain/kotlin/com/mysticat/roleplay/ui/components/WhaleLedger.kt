package com.mysticat.roleplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleMotion
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSizes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing

/**
 * L2「行表件」：**结论条 ＋ 分组标题 ＋ 可展开的行**。
 *
 * 这一套是「模型与 API」「语音服务」两页的骨架（DESIGN 合同见 `.tools/ui-preview/v9-api-voice/`）——
 * 两页 80% 的时间在"核对一项值 / 改一项值"，而原来的骨架是"分段胶囊 → 节标题 → 白卡 → 成排输入框"：
 * 字段一多，三十来个输入框长得一模一样，"要改的那一项"找不到，值也不可扫读（只能逐个点开看）。
 *
 * 三条口径（改这一份等于改这两页的全部观感）：
 *  ① **名｜值同排**：行左是名字、行右是当前值、行尾是动作；值单行省略，点开行看全（长 URL 不许撑破列）。
 *  ② **编辑态就地展开**：点行 → 该行下方展开编辑器（高度＋透明度，200ms 可打断），不跳页、不开新弹层。
 *  ③ **一个平面**：行与行只靠发丝分隔，没有卡片；分组靠 [WhaleGroupLabel] 承担
 *     （2026-10-10 归一＝与卡片页的 [WhaleFieldGroupLabel] 同一份取值，14 SemiBold 黑）。
 *
 * 取值全部照抄原型 CSS 变量与既有 L1 令牌（soft／softLine／hairline／accentText／dangerText），
 * 不新增颜色——"未配置"与 NoKeyHint 同口径走 dangerText，不再引入第三个状态色。
 */

/** 结论条的三个色调：Ok＝可用／Alert＝要处理（与 [NoKeyHint] 同口径）／Idle＝未启用（中性空心点）。 */
enum class LedgerTone { Ok, Alert, Idle }

@Composable
private fun ToneDot(tone: LedgerTone, modifier: Modifier = Modifier) {
    val palette = LocalWhalePalette.current
    val color = when (tone) {
        LedgerTone.Ok -> MaterialTheme.colorScheme.primary
        LedgerTone.Alert -> palette.dangerText
        LedgerTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (tone == LedgerTone.Idle) Color.Transparent else color)
            .border(1.dp, color, CircleShape)
    )
}

/**
 * 顶部结论条（D1 对照板 C 的骨架，被 A 吸收）：**一行回答"现在接成了什么样"**。
 *
 * 只写算得出来的状态（已存 Key／上次测试通过／音色 N 个）；算不出的不编（无记录就写"未测试"）。
 *
 * @param subtitle 等宽小字副句（模型 id、测试状态这类标识符）
 * @param action 右槽：即时动作（[LedgerAction] 或一个进度指示器）
 */
@Composable
fun WhaleConclusionBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    tone: LedgerTone = LedgerTone.Ok,
    action: (@Composable () -> Unit)? = null
) {
    val palette = LocalWhalePalette.current
    Row(
        modifier
            .fillMaxWidth()
            .background(palette.soft, WhaleShapes.r2)
            .border(1.dp, palette.softLine, WhaleShapes.r2)
            .padding(horizontal = WhaleSpacing.md, vertical = WhaleSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToneDot(tone)
        Spacer(Modifier.width(WhaleSpacing.sm))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WhaleSpacing.xxs)) {
            Text(
                title,
                style = LocalWhaleType.current.label.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = LocalWhaleType.current.mono(LocalWhaleType.current.micro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        action?.let {
            Spacer(Modifier.width(WhaleSpacing.sm))
            it()
        }
    }
}

/**
 * 分组标题：**与卡片页的 [WhaleFieldGroupLabel] 同一份取值**（14 SemiBold 黑）。
 *
 * 2026-10-10 归一（用户拍板）：此前是 9sp 等宽灰、只此两页用——同一个"分节"角色在
 * 「用户」页/编辑器是 14 SemiBold 黑，差 5sp＋字族＋字重，切页就像两个 App。
 * 保留本名只是为了让调用点不必都改；**改取值请改 [WhaleFieldGroupLabel]**，这里不再有第二份实现。
 */
@Composable
fun WhaleGroupLabel(text: String, modifier: Modifier = Modifier) {
    WhaleFieldGroupLabel(text, modifier)
}

/**
 * 文本分段（页内导航）：选中＝正文色＋600 字重＋2dp accent 下划线（贴所在项底边）；
 * 未选＝次要色。**不用胶囊**——胶囊只允许出现在展开层的取值选择里（DESIGN §6 禁用 2）。
 */
@Composable
fun WhaleSegTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomStart),
            color = colors.outlineVariant
        )
        Row {
            tabs.forEachIndexed { i, label ->
                val on = i == selectedIndex
                // 下划线用 drawBehind 画在**本项自己的**宽上：
                // Row 里不能用 `Box(fillMaxWidth())` 铺线——Row 给子项的最大宽度是"剩余全部"，
                // 那一项会把整行吃满，后面的分段全被推出屏幕（第 23 轮冒烟实测踩到）。
                Column(
                    Modifier
                        .clickable { onSelect(i) }
                        .drawBehind {
                            if (on) {
                                val h = 2.dp.toPx()
                                drawRect(
                                    colors.primary,
                                    topLeft = Offset(0f, size.height - h),
                                    size = Size(size.width, h)
                                )
                            }
                        }
                        .padding(horizontal = WhaleSpacing.md, vertical = WhaleSpacing.sm)
                ) {
                    Text(
                        label,
                        style = LocalWhaleType.current.label,
                        color = if (on) colors.onSurface else colors.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                    // 给下划线留出落点（线画在 padding 区，这里只负责把文字抬起来）
                    Spacer(Modifier.height(WhaleSpacing.sm))
                }
            }
        }
    }
}

/** 行尾/结论条的动作文字（accnText；禁用走次要色）。点击热区含内边距。 */
@Composable
fun LedgerAction(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        text,
        style = LocalWhaleType.current.meta,
        color = if (enabled) LocalWhalePalette.current.accentText
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = WhaleSpacing.sm, vertical = WhaleSpacing.xs)
    )
}

/** 展开层里的说明小字（不是行、不进值列）。 */
@Composable
fun LedgerNote(text: String, modifier: Modifier = Modifier, danger: Boolean = false) {
    LedgerNoteText(AnnotatedString(text), modifier, danger)
}

/** 同上，但允许行内 Markdown（`**粗体**`／`` `代码` ``）——原页面那几段说明就是这么渲染的。 */
@Composable
fun LedgerNote(text: AnnotatedString, modifier: Modifier = Modifier, danger: Boolean = false) {
    LedgerNoteText(text, modifier, danger)
}

@Composable
private fun LedgerNoteText(text: AnnotatedString, modifier: Modifier, danger: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (danger) LocalWhalePalette.current.dangerText
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * 一行事实：**名（左）｜值（右贴）｜动作（行尾）**，行下发丝；[onToggle] 非空时整行可点、
 * 就地展开 [content] 成编辑态（同一页同时只开一行由调用方保证）。
 *
 * @param valueMono 值是不是标识符（模型 id／音色 id／URL 走等宽；"开"/"已存"这类散文值走 sans）
 * @param valueDim 值比行名更次要时（如"端点自报"这种旁注）用次要色
 * @param trailing 即时动作控件（开关、连接测试按钮）——它自己吃点击，不触发展开
 * @param content 展开层；为 null 时本行是纯只读行（不可展开、不显示动作箭头）
 */
@Composable
fun WhaleLedgerRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String = "",
    valueMono: Boolean = true,
    valueDim: Boolean = false,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
    actionText: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val type = LocalWhaleType.current
    val canToggle = onToggle != null && content != null && enabled
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (isDesktopLayout) 36.dp else WhaleSizes.hit)
                .clickable(enabled = canToggle) { onToggle?.invoke() }
                .alpha(if (enabled) 1f else 0.55f)
                .padding(horizontal = WhaleSpacing.md, vertical = WhaleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                // 行名＝黑（2026-10-10 口径：名黑、说明灰；值走下方自己的色）
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 行名**不设权重**：权重只给中间那根弹性空条，值列才贴得住右缘。
                // （两侧都写 weight 时，行名在桌面上会被量成 0 宽而整条消失，值也贴不到右边——
                //   第 23 轮桌面 --shot 实测踩到，手机端同样贴不到右。）
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Spacer(Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Text(
                    value,
                    // 值走 caption（12）而不是 micro/tiny：它是**这一行的载荷**，不能比节标题还小
                    // （第 23 轮独立评审：值落在最小一档且是灰字 ⇒ 眼睛只落在行名与动词上，"值可扫读"落空）
                    style = if (valueMono) type.mono(type.caption) else MaterialTheme.typography.bodyMedium,
                    color = if (valueDim) colors.onSurfaceVariant else colors.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }
            if (actionText != null) {
                Spacer(Modifier.width(WhaleSpacing.sm))
                Text(actionText, style = type.meta, color = LocalWhalePalette.current.accentText, maxLines = 1)
            }
            trailing?.let {
                Spacer(Modifier.width(WhaleSpacing.sm))
                it()
            }
        }
        if (content != null) {
            AnimatedVisibility(
                visible = expanded && enabled,
                enter = expandVertically(WhaleMotion.enter(), Alignment.Top) + fadeIn(WhaleMotion.enter()),
                exit = shrinkVertically(WhaleMotion.enter(), Alignment.Top) +
                    fadeOut(tween(durationMillis = (90 * WhaleMotion.scale).toInt()))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = WhaleSpacing.md,
                            end = WhaleSpacing.md,
                            bottom = WhaleSpacing.md
                        ),
                    verticalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)
                ) {
                    content()
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
    }
}

@Composable
private fun palette(key: String): Color = when (key) {
    "accentText" -> LocalWhalePalette.current.accentText
    else -> MaterialTheme.colorScheme.onSurface
}
