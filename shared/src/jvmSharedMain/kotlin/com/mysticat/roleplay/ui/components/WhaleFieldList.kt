package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSizes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import com.mysticat.roleplay.ui.uiInlineMarkdown

/**
 * L2「字段表」：**分组卡 ＋ 字段行 ＋ 标签在上的文本框 ＋ 开关行**（v10 行表式设计合同）。
 *
 * 与 [WhaleLedgerRow] 的分工：Ledger 那一套是"一页一表"（名｜值｜动作，点行就地展开编辑器），
 * 本文件是**分组卡**形态——一级分组＝一张卡（标题在卡外），卡内一行＝标题（＋短说明）在左、
 * 控件常驻右贴；要填字的字段＝标签在框上方、框占整宽。参考物＝桌面设置页（分组卡）
 * ＋ 表单页（文本框），用户 2026-10-08 拍板。
 *
 * 三条线各归一个令牌（不许混用）：
 * ① 分组卡外框＝`outline`（皮肤 hairline）；② 卡内行间发丝＝`outlineVariant`（更浅一档）；
 * ③ 文本框框线＝`WhalePalette.fieldLine`（**最深**，因为它是"要动手的地方"）。
 * 卡的内边距写在**卡**上（左右 18），于是卡内发丝天然与内容对齐、不和外框连成框格。
 */

/**
 * 卡外分组标题（原型 `.ghead`）：普通粗体 14/600 黑，靠留白分组。
 *
 * 单独开放出来，是因为**有些分节没有一张卡**（条目清单、只有一行说明的小节），
 * 它们的标题必须是同一份取值——一页里出现两种"分节"就是没做完的样子。
 * 禁用清单 9：分组标题**不许用等宽小字**（旧的 `.lbl` 是卡内字段标签，不是标题）。
 *
 * [leading]＝标题前的图标槽（「用户」页总表把每个分组的图标带进卡外标题）；
 * 不传时走原路（单 Text），与从前逐像素一致。
 */
@Composable
fun WhaleFieldGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    val pad = Modifier.padding(top = WhaleSpacing.xl, bottom = WhaleSpacing.sm)
    if (leading == null) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.then(pad)
        )
        return
    }
    Row(modifier.then(pad), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(WhaleSpacing.sm))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 分组卡：卡外一行普通粗体标题（＋留白）＋ 一张卡。
 *
 * @param label 分组标题；null/空＝不画标题（如整页只有一组时）
 * @param leading 标题前的图标槽，透传给 [WhaleFieldGroupLabel]
 */
@Composable
fun WhaleFieldGroup(
    label: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            WhaleFieldGroupLabel(label, leading = leading)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(WhaleShapes.r2)
                .background(colors.surface)
                .border(1.dp, colors.outline, WhaleShapes.r2)
                .padding(horizontal = 18.dp),
            content = content
        )
    }
}

/**
 * 卡内只读框块：**标签在框上方 ＋ 框线 `fieldLine` 的一个框**（[WhaleFieldBlock] 的只读形态）。
 *
 * 用在"内容不是一行文字、得装在框里看"的地方（原型 §六 的音乐库、§七 的背景预览）：
 * 框内可以是列表（自带给滚动）、预览图或状态块。可编辑的字段一律走 [WhaleFieldBlock]。
 */
@Composable
fun WhaleFieldBox(
    label: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    hint: String? = null,
    boxModifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fieldLine = LocalWhalePalette.current.fieldLine
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp)
        ) {
            FieldLabel(label)
            if (!desc.isNullOrBlank()) {
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
            Column(
                boxModifier
                    .fillMaxWidth()
                    .background(colors.surface, WhaleShapes.r1)
                    .border(1.dp, fieldLine, WhaleShapes.r1)
                    .padding(contentPadding),
                content = content
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    uiInlineMarkdown(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (divider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * 一行事实：**标题（＋一句短说明）在左、控件右贴**，行下发丝。
 *
 * [desc] 是"这件事是什么／会怎样"的一句话（v10 口径：≤2 行，不许默认展开长解释）。
 * [onClick] 非空＝整行一个热区（一个热区，不许行尾动作与行抢点击——行尾的真控件走 [trailing]）。
 * [divider] 末行传 false（卡底不留一条悬空的线）。
 * [trailingBelow] ＝手机端把控件**降到行下方整宽**（原型 §十一 口径 ②③：下拉与"值＋按钮"
 * 在窄屏塞不进行右、又不许把值省略）；桌面端不受影响，照旧右贴。
 */
@Composable
fun WhaleFieldRow(
    title: String,
    modifier: Modifier = Modifier,
    desc: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingBelow: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        val rowSkin = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (isDesktopLayout) 44.dp else WhaleSizes.hit)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(vertical = 13.dp)
        if (trailingBelow && !isDesktopLayout && trailing != null) {
            Column(rowSkin) {
                FieldRowMain(title, desc)
                Spacer(Modifier.height(9.dp))
                Box(Modifier.fillMaxWidth()) { trailing() }
            }
        } else {
            Row(rowSkin, verticalAlignment = Alignment.CenterVertically) {
                FieldRowMain(title, desc, Modifier.weight(1f))
                trailing?.let {
                    Spacer(Modifier.width(WhaleSpacing.md))
                    it()
                }
            }
        }
        if (divider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * 行尾入口箭头（口径 ④：入口右侧只留箭头，点行主体生效）。
 *
 * 与「展开」的 [WhaleRowCaret] 分开：箭头＝**去别处**（跳页/开弹层），朝下箭头＝**就地展开**，
 * 两者混用会让"点下去会发生什么"变得不可预期。
 */
@Composable
fun WhaleRowChevron() {
    Text(
        "›",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 行尾展开箭头：默认朝下，展开后翻上来（点行主体生效）。 */
@Composable
fun WhaleRowCaret(expanded: Boolean) {
    Text(
        "▾",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.rotate(if (expanded) 180f else 0f)
    )
}

/** [WhaleFieldRow] 左侧那一列（标题＋短说明）——两种排布共用同一份取值。 */
@Composable
private fun FieldRowMain(title: String, desc: String?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            // 行名＝黑、说明＝灰（用户 2026-10-10 口径）。27 轮那句「卡内行名一律灰」是**扩大解释**
            // ——当时反馈针对的只是表单标签，行名跟着变灰属误伤，本条把它翻回来
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!desc.isNullOrBlank()) {
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 字段标签（框上方那一行）：**卡内一律黑、14 号**（2026-10-10 口径：名黑、说明灰）。
 * 历史：2026-10-08 反馈③先把"黑大标签与灰标签混用"统一成了灰；2026-10-10 拍板再翻黑
 * ——同页行名也是黑，标签与行名同一份取值，只有真正的说明句才走灰。
 * 行内 markdown（**粗体**／反引号）照渲染：工具形态的 greetingLabel 源码里就带着星号。
 */
@Composable
private fun FieldLabel(label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        uiInlineMarkdown(label),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = 7.dp)
    )
}

/**
 * 文本框块：**标签在框上方**、框线＝[LocalWhalePalette] 的 `fieldLine`、框下可带一句 hint。
 *
 * 不用 M3 `OutlinedTextField` 的两条理由（禁用清单 4／5）：浮起标签在中文长标签下会被截断、
 * 也和括注打架；框线只认 `fieldLine`——它比结构线深，因为它是"要动手的地方"。
 * [onDelete] 非空＝标签行右尾带一枚删除（多条目字段用，例如「其他开场白」）。
 * [divider] 与 [WhaleFieldRow] 同一口径：卡内末块传 false，卡底不留悬空线。
 * [keyboardOptions] 数字类字段用（扫描深度那类只收数字的框：手机端要弹数字键盘）。
 */
@Composable
fun WhaleFieldBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    minLines: Int = 1,
    hint: String? = null,
    enabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    divider: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val colors = MaterialTheme.colorScheme
    val fieldLine = LocalWhalePalette.current.fieldLine
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp)
                .alpha(if (enabled) 1f else 0.55f)
        ) {
            if (onDelete == null) {
                FieldLabel(label)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FieldLabel(label, Modifier.weight(1f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除这一条",
                            modifier = Modifier.size(18.dp),
                            tint = colors.onSurfaceVariant
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface, WhaleShapes.r1)
                    .border(
                        if (focused) 2.dp else 1.dp,
                        if (focused) colors.primary else fieldLine,
                        WhaleShapes.r1
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = if (minLines > 1) Alignment.TopStart else Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    enabled = enabled,
                    singleLine = minLines <= 1,
                    minLines = minLines,
                    keyboardOptions = keyboardOptions,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            if (!hint.isNullOrBlank()) {
                Text(
                    uiInlineMarkdown(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (divider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * 下拉预设字段：与 [WhaleFieldBlock] 同一形态，框尾多一个箭头按钮开预设清单。
 *
 * 输入框与箭头**各管各的**（框只管输入、箭头只管开列表）——不要改回"点框开菜单"，
 * 反复点输入框会与键盘/焦点打架（历史上出过崩溃）。菜单宽度＝量到的框宽。
 */
@Composable
fun WhaleFieldSelect(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    presets: List<String>,
    modifier: Modifier = Modifier,
    presetLabel: (String) -> String = { it },
    hint: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val fieldLine = LocalWhalePalette.current.fieldLine
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp)
                .alpha(if (enabled) 1f else 0.55f)
        ) {
            FieldLabel(label)
            Box(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { fieldWidthPx = it.size.width }
                    .background(colors.surface, WhaleShapes.r1)
                    .border(1.dp, fieldLine, WhaleShapes.r1)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValue,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = enabled,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                            cursorBrush = SolidColor(colors.primary),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth()) { inner() }
                            }
                        )
                    }
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "选择$label",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = enabled) { expanded = true }
                            .padding(4.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .width(with(LocalDensity.current) { fieldWidthPx.toDp() })
                        // 限高（2026-10-10）：预设清单是几十条起步的（模型名 / 音色 id），
                        // 菜单不能按条数一路拉长——上限 320dp，超出在菜单内滚动。
                        .heightIn(max = 320.dp)
                ) {
                    presets.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(presetLabel(p)) },
                            onClick = { onValue(p); expanded = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("可自定义：直接在上方输入框里改") },
                        enabled = false,
                        onClick = { expanded = false }
                    )
                }
            }
            if (!hint.isNullOrBlank()) {
                Text(
                    uiInlineMarkdown(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (divider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/**
 * 开关行：**≥2 档的选择一律折成标签下方一行方框勾选**（单选/多选同构，禁用清单 3）。
 *
 * [isSelected]/[onToggle] 两个回调即可覆盖两种语义——多选传"在不在集合里／翻转"，
 * 单选传"等不等于当前值／设值"，组件本身不分叉。一行排不下时折成**等宽网格**：
 * 列宽按最宽的一枚量出、列数按可用宽度取整——自由流式换行会让「二次元／赛博朋克」
 * 这类宽选项把后面几列整体推右，各行列头对不上（手机端实测，宽屏只占一行时无差别）。
 * [onAdd] 非空＝选项末尾追加一枚「＋[addLabel]」（点它开新建弹窗，新建入口就在选择处）。
 */
@Composable
fun WhaleToggleRow(
    title: String,
    options: List<String>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    desc: String? = null,
    enabled: Boolean = true,
    onAdd: (() -> Unit)? = null,
    addLabel: String = "新建",
    divider: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp)
                .alpha(if (enabled) 1f else 0.55f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                // 行名＝黑（2026-10-10 口径，同 FieldRowMain：27 轮的「一律灰」已推翻）
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!desc.isNullOrBlank()) {
                Text(
                    uiInlineMarkdown(desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
            ) {
                val gap = 18.dp
                val labelStyle = MaterialTheme.typography.bodyMedium
                val density = LocalDensity.current
                val measurer = rememberTextMeasurer()
                val cells = options.map { it to false } +
                    if (onAdd != null) listOf(addLabel to true) else emptyList()
                // 单格最小宽＝最宽的一枚（方框 16 ＋ 字距 7 ＋ 文字 ＋ 2 呼吸），没有测量就无法定列数
                val cellMin = remember(options, addLabel, onAdd != null, labelStyle, density) {
                    val labels = options + if (onAdd != null) listOf(addLabel) else emptyList()
                    val widest = labels.maxOfOrNull {
                        measurer.measure(AnnotatedString(it), labelStyle, maxLines = 1, density = density)
                            .size.width
                    } ?: 0
                    with(density) { widest.toDp() } + 16.dp + 7.dp + 2.dp
                }
                val cols = if (maxWidth.value.isFinite()) {
                    ((maxWidth + gap) / (cellMin + gap)).toInt().coerceAtLeast(1)
                } else {
                    1
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    cells.chunked(cols).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap)
                        ) {
                            row.forEach { (label, add) ->
                                ToggleCell(
                                    label = label,
                                    add = add,
                                    on = !add && isSelected(label),
                                    enabled = enabled,
                                    modifier = Modifier.weight(1f),
                                    onClick = { if (add) onAdd?.invoke() else onToggle(label) }
                                )
                            }
                            // 末行补空位：不补的话剩下的格子被拉宽，列头与上面几行错开
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (divider) HorizontalDivider(color = colors.outlineVariant)
    }
}

/** 网格里的一格：方框 ＋ 文字；[add] 换成"＋"，其余同构——两处共用同一套尺寸，列头才对得齐。 */
@Composable
private fun ToggleCell(
    label: String,
    add: Boolean,
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fieldLine = LocalWhalePalette.current.fieldLine
    Row(
        modifier
            .defaultMinSize(minHeight = WhaleSizes.hit)
            .clip(WhaleShapes.r1)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val boxSkin = when {
            on -> Modifier.background(colors.primary, WhaleShapes.r1)
            else -> Modifier.border(1.5.dp, fieldLine, WhaleShapes.r1)
        }
        Box(
            Modifier
                .size(16.dp)
                .then(boxSkin),
            contentAlignment = Alignment.Center
        ) {
            when {
                add -> Icon(Icons.Filled.Add, null, Modifier.size(11.dp), tint = fieldLine)
                on -> Icon(Icons.Filled.Check, null, Modifier.size(11.dp), tint = Color.White)
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (on) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
