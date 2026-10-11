package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing

/**
 * L2「面板件」——**一张能被眼睛当成卡片的块**，以及卡里那几样反复出现的小东西。
 *
 * 为什么单独开一份（2026-10-03，用户反馈"做出来的视觉不如 HTML 预览"）：
 * 落地前对着原型逐条量过，发现差得最远的不是配色而是**这三处**，它们让页面看着"没做完"——
 *  ① **卡底色用错了槽**：条目卡原来填 `surfaceVariant`（＝皮肤的 `--surface2`）且**没有描边**，
 *     而页面底是 `background`（＝`--paper`）。两个色只差 5 个灰阶 ⇒ 卡片在页面上根本"浮不起来"。
 *     原型的 `.box`/`.ent` 是 **`--surface` 纯白底 ＋ `--hair` 一像素描边**，靠这两样才读得出卡。
 *  ② **标签的挂法不同**：原型是行内小号等宽标签（`.lbl`）贴在框的左边，不是 M3 `OutlinedTextField`
 *     那种浮上来／切进去的动画标签。后者在长表单里会切出一片参差的缺口。
 *  ③ **键值行（`.kv`）没有对应物**：原型里"标签在左、值在右、下面一条发丝线"是主力排版，
 *     M3 没有这个件，只能拿 TextField 顶，顶出来的就是又宽又高的空框。
 *
 * 取值全部**照抄原型 CSS 变量**（`.box` / `.ent` / `.kv` / `.lbl` / `.field` / `.ta`），
 * 改这一份等于改原型与代码两侧，不会再漂成两套。
 * 圆形刻度不写死：圆角取 [WhaleShapes.r2]／[WhaleShapes.r1]（随皮肤切换，见 WhaleTokens）。
 */

/**
 * 一张面板卡：1dp 描边 ＋ `--surface` 底 ＋ r2 圆角（原型 `.box` / `.ent`）。
 *
 * @param padding 卡内边距。原型两种：`.box.pad` 是 14dp（基本信息／使用角色），`.ent` 是
 *   横向 12dp／纵向 11dp（条目卡，密度更高、一屏要放下十几张）。默认给 `.box.pad` 那档。
 * @param spacing 卡内纵向间距，原型是 0（子件自己带 margin）——这里默认 0，要竖排就给值。
 */
@Composable
fun WhalePanelCard(
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    horizontalPadding: Dp = padding,
    verticalPadding: Dp = padding,
    spacing: Dp = WhaleSpacing.none,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, WhaleShapes.r2)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, WhaleShapes.r2)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        content()
    }
}

/**
 * 行内小标签（原型 `.lbl`）：等宽、9.5sp、字距 .18em、次要色。
 *
 * 原型用它标"这一格是什么"（世界书名称／关键词／扫描深度）。等宽＋大字距是刻意的——
 * 它是**刻度**不是文案，得一眼和正文分开；换成无衬线小字就会混进正文里。
 */
@Composable
fun WhaleMonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val type = LocalWhaleType.current
    Text(
        text = text,
        modifier = modifier,
        // 字号取字阶最小档（吃 ×1.1 与整像素吸附）；字距仍在这里给——.18em 的 tracking 是这一档的
        // 刻意设计，进不了字阶（吸附会把 0.18sp 抹成 0，横向信息全失）
        style = type.mono(type.micro).copy(letterSpacing = 1.7.sp, color = color),
        maxLines = 1
    )
}

/**
 * 键值行（原型 `.kv`）：标签在左（固定宽 118dp）、值在右、下面一条发丝线。
 *
 * @param valueMono 值走等宽小字（原型 `.srowv`）——那几格放的是计数与时间戳，等宽才对得齐。
 *   2026-10-10 由 tiny(10) 升 meta(11)：10sp 落在全页最小档、与说明句同号，读起来不像"值"。
 * @param last 最后一行的不发丝线，否则卡底多一条线和卡边框叠在一起。
 */
@Composable
fun WhalePanelKvRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String = "",
    valueMono: Boolean = false,
    last: Boolean = false,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val type = LocalWhaleType.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                // 行名＝黑（2026-10-10 口径：名黑、说明灰）
                color = colors.onSurface,
                modifier = Modifier.defaultMinSize(minWidth = 118.dp)
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.weight(1f))
                Text(
                    value,
                    style = if (valueMono) {
                        type.mono(type.meta).copy(color = colors.onSurfaceVariant)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        if (!last) {
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

/**
 * 描边输入区（原型 `.field` / `.ta`）：**标签在框上方**（与框内文字同色系、同号）＋ 1dp `fieldLine` 描边
 * ＋ r1 圆角 ＋ `--surface` 底。
 *
 * 为什么不用 M3 的 `OutlinedTextField`：那个组件的标签会浮起／切入，一屏里标签高度参差；
 * 而且它自带 56dp 的最小高度，一页放十几条时纵向拉得很长。原型是 40dp（行内）/ 自适应（多行）。
 *
 * 标签 2026-10-10 从**框内**移到**框上方**（用户反馈："类似这种文字在输入框里的都放到输入框上"）：
 * 行表里"名｜值"都在框外，标签缩在框内会让输入框看起来像另一套骨架；框外标签与 `WhaleFieldBlock`
 * 同一口径（`bodyMedium` ＋ 标签→框 7dp）、**同为黑**（2026-10-10 口径：名黑、说明灰），
 * 框线也只认 `fieldLine`。
 *
 * @param secret 密钥类取值：输入时显示为圆点（与旧页面的 Key 输入框同一行为）
 */
@Composable
fun WhaleHairField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    secret: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = if (singleLine) 40.dp else 0.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    textStyle: TextStyle? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = WhaleShapes.r1
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                // 标签＝黑（2026-10-10 口径：名黑、说明灰）
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = minHeight)
                .background(colors.surface, shape)
                .border(1.dp, LocalWhalePalette.current.fieldLine, shape)
                .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 10.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = keyboardOptions,
                visualTransformation = if (secret) PasswordVisualTransformation()
                else VisualTransformation.None,
                textStyle = (textStyle ?: MaterialTheme.typography.bodyLarge).copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                style = (textStyle ?: MaterialTheme.typography.bodyLarge)
                                    .copy(color = colors.onSurfaceVariant.copy(alpha = 0.7f))
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/**
 * 无框行内可编辑文字（原型 `.ent` 里的条目名那格）。
 *
 * 原型那条就是一行 13.5px 的粗体文字，看不出是输入框——但它得能改。
 * 这里给 M3 描边框只会在一屏十几张卡里拉出十几条竖线，所以做成无框：
 * 靠 [WhaleMonoLabel] 那样的 hover/焦点提示不够（桌面没有 hover 反馈的输入框会像只读），
 * 折中办法是**聚焦时给一条底下的发丝线**，没聚焦时保持纯文字。
 */
@Composable
fun WhaleInlineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle? = null
) {
    val colors = MaterialTheme.colorScheme
    val style = textStyle ?: MaterialTheme.typography.bodyLarge
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .then(if (focused) Modifier.border(1.dp, colors.primary, WhaleShapes.r1) else Modifier)
            .padding(if (focused) 6.dp else 0.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            textStyle = style.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = style.copy(color = colors.onSurfaceVariant))
                    }
                    inner()
                }
            }
        )
    }
}

/**
 * 面板件里的正文文字：统一走默认档字阶，不用调用点各处挑。
 *
 * 存在的意义只有一个——让卡内所有文字落在同一张字表上。
 */
@Composable
fun PanelBodyText(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    )
}