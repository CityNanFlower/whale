package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.DesktopMainHeader
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.uiInlineMarkdown
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.components.WhaleHairField

/**
 * 内嵌形态的页面标题（设置页/语音页共用）：**主栏标题（236 形式统一）**＝字体/高度/分割线全按发现页，
 * 右侧字段＝未保存提示 + 保存按钮（这两样原来挂在整页形态的顶栏右侧）。
 *
 * [onBack] 由调用方的 `requestBack`（有未保存改动会先弹确认）在返回键/Esc 里触发
 * （用户 2026-10-10 口径 234：桌面去掉可点回退箭头、交互语言统一 Esc——第二栏与 Esc 就是退路），
 * 这里不再画可点回退件。
 */
@Composable
internal fun EmbeddedSettingsHeader(
    title: String,
    dirty: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
    /**
     * 保存组**之前**的额外页头字段（238：语音服务页放场景模型胶囊）。
     * 调用方自己带与右侧那组文字的间距；不传时与其他页一模一样的排版。
     */
    extra: @Composable () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.padding(horizontal = 16.dp)) {
        DesktopMainHeader(title) {
            extra()
            Text(
                if (dirty) "有未保存的修改" else "已是最新",
                style = MaterialTheme.typography.labelSmall,
                color = if (dirty) colors.error else colors.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Button(onClick = onSave, enabled = dirty) { Text("保存") }
        }
    }
}

/**
 * 内嵌形态的页面标题（只读页用）——**主栏标题（236 形式统一）**：字体/高度/分割线全按发现页，
 * 右侧字段这几页没有（背景音乐 / 素材库 / 使用手册 / 用量统计）。
 *
 * 与 [EmbeddedSettingsHeader] 的差别是**没有保存按钮**——背景音乐 / 素材库 / 使用手册这三页
 * 每一处改动都当场落盘（上传曲目、重命名、改分类），没有"未保存的修改"这种状态，
 * 硬加一颗恒灰的保存键只会让人以为"还得多点一下才存上"。
 *
 * 出口＝Esc（各页都注册了 `WhaleBackHandler`）与第二栏换页（用户 2026-10-10 口径 234：
 * 桌面去掉可点回退箭头、交互语言统一 Esc）。[onBack] 保留在签名里由调用方继续传（与设置页同款），
 * 但这里不再画任何可点回退件。
 */
@Composable
internal fun EmbeddedPageHeader(title: String, onBack: () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) {
        DesktopMainHeader(title)
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalWhalePalette.current.accentText
    )
}

/**
 * 思考强度**拉杆**（0.3.0-alpha.3 批，替换原档位胶囊 ThinkingChips）：
 * 离散档位吸附（Slider 停靠点），档位名直接标在刻度下方——左/中/右对齐贴合刻度位置。
 * 档位仍来自 [ProviderProfiles.thinkingLevels]（不同服务商/模型能给的档不同），
 * value 仍是持久化的 mode 字符串，与胶囊时代同一份落盘。
 */
@Composable
internal fun ThinkingSlider(
    value: String,
    levels: List<ProviderProfiles.ThinkingLevel>,
    onValue: (String) -> Unit,
    enabled: Boolean = true,
    // 行表展开层里**不传标签**：上面那一行的行名就是它，再写一遍是重复（用户 2026-10-10）；
    // 弹窗等独立场景仍传（那里的上下文没有行名）。放末位＝不给人"位置参数传错位"的机会。
    label: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
        }
        if (levels.isEmpty()) return@Column
        // 当前值不在档位表里（从未设置过的空值 / 老版本落盘值）：吸附到「不传参数」那档——
        // 约定各家档位表都有一档 mode 为空（"默认"），没有就取第一档。
        val defaultIdx = levels.indexOfFirst { it.mode.isBlank() }.takeIf { it >= 0 } ?: 0
        val selectedIdx = levels.indexOfFirst { it.mode == value }.takeIf { it >= 0 } ?: defaultIdx
        // 拖动期间本地跟随（显示最近停靠档），松手才提交——设置页逐字落盘的次数压到一次
        var dragIdx by remember(selectedIdx) { mutableFloatStateOf(selectedIdx.toFloat()) }
        val activeIdx = dragIdx.roundToInt().coerceIn(0, levels.lastIndex)
        Slider(
            value = dragIdx,
            onValueChange = { dragIdx = it },
            onValueChangeFinished = { onValue(levels[activeIdx].mode) },
            valueRange = 0f..levels.lastIndex.toFloat(),
            steps = (levels.size - 2).coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            levels.forEachIndexed { i, lv ->
                Text(
                    lv.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (i == activeIdx) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        levels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        levels.getOrNull(activeIdx)?.let {
            Text(
                it.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 预算两档·自定义档的单个旋钮（总输出 / 上下文）：档位胶囊 + 一行说明。
 * 档位清单由调用方按当前模型生成（`ProviderProfiles.budgetLevels`——总是思考的模型不给 8192 以下）。
 * 设置页与会话页「输出预算」对话框共用，保证两处口径一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BudgetKnobChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
    hint: String,
    // 同 ThinkingSlider：行表展开层不传（行名已在上方），对话框里照传；放末位同上
    label: String? = null
) {
    Column {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { opt ->
                WhaleChip(
                    enabled = enabled,
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 「接入能力」明细：把代码里已经固定好的适配显式告诉用户（2026-09-14 新增）。
 * 目的：用户换服务商后如果发现"返回格式被改了""温度不见了"，能在这里看到原因，
 * 而不是以为配置坏了；同时给出官方文档与价格页入口。
 *
 * 2026-10-08 起**不带标题、不自带卡片**——它渲染在行表「接入能力」那一行的展开层里
 * （标题由行本身承担），所以这里只剩下明细行与两个外链入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderCapabilityBody(
    chatBaseUrl: String,
    chatModel: String,
    imageBaseUrl: String,
    imageModel: String,
    custom: List<CustomProvider> = emptyList()
) {
    val chat = ProviderProfiles.resolve(chatBaseUrl, custom)
    val image = ProviderProfiles.resolve(imageBaseUrl, custom)
    Column {
        if (chat == null && image == null) {
            Text(
                "尚未匹配到已知服务商：将按 OpenAI 兼容协议通用处理（对话 /chat/completions、生图 /images/generations）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        // 自定义供应商弹窗写着「备注会显示在能力卡片里」，此前 note 全项目无处渲染（承诺没兑现）
        (chat ?: image)?.note?.takeIf { it.isNotBlank() }?.let { CapabilityLine("备注", it) }
        chat?.let {
            CapabilityLine("对话供应商", it.name)
            CapabilityLine("思考参数", it.thinking.paramLabel)
            CapabilityLine("温度参数", if (it.temperatureSupported) "支持" else "不支持（App 已自动不发送）")
        }
        image?.let {
            CapabilityLine("生图供应商", it.name)
            CapabilityLine("生图协议", it.imageProtocol.cardText)
            CapabilityLine("尺寸格式", it.imageSizeFormat)
            CapabilityLine("返回格式", it.imageFormatForced?.let { v -> "$v（服务商固定）" } ?: "b64_json / url 均可选")
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 原来 `listOfNotNull(chat ?: image).firstOrNull()` 一旦 chat 命中就再也不用 image 的链接，
            // 而"对话供应商没有文档链接、生图供应商有"是常见组合（例如自定义对话端点 + 内置生图）→ 按钮整个消失。
            // 现在各取各的：文档优先 chat、没有再取 image；价格同理。
            val docs = chat?.docsUrl ?: image?.docsUrl
            val pricing = chat?.pricingUrl ?: image?.pricingUrl
            // 打不开时说一句：静默失败在用户眼里就是"按钮坏了"（2026-09-21 反馈的现象）
            val openLink: (String) -> Unit = { url ->
                if (!Platform.ui.openUrl(url)) Platform.ui.toast("无法打开链接：$url")
            }
            docs?.let { url -> TextButton(onClick = { openLink(url) }) { Text("使用文档") } }
            pricing?.let { url -> TextButton(onClick = { openLink(url) }) { Text("价格与模型页") } }
        }
        Text(
            "说明：思考参数、返回格式、尺寸写法等按服务商要求由 App 自动处理，不需要手动改；" +
                "换供应商时相关配置会自动跟随切换。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 三个模型页共用：「这家还没存 Key」的置灰提示（2026-09-15 用户要求） */
@Composable
internal fun NoKeyHint(providerName: String) {
    Text(
        "「$providerName」还没有 Key：去「API 配置」选中它、填入 Key 之后，这一页的模型与参数才能用。",
        style = MaterialTheme.typography.bodySmall,
        // danger 作卡面文字走派生档（R4 对比度拍板），三个模型页共用这一份
        color = LocalWhalePalette.current.dangerText
    )
}

@Composable
internal fun CapabilityLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        // 备注等取值源码里就写着 `**粗体**`（ProviderProfiles 的 note），裸 Text 会把星号原样亮出来
        Text(uiInlineMarkdown(value), style = MaterialTheme.typography.bodySmall)
    }
}



/**
 * 可编辑下拉：点右侧箭头选预设，也可以直接在上面输入框手写；下方带引导说明。
 *
 * 原来用 `ExposedDropdownMenuBox` + `menuAnchor()`。
 * 那个写法在本文件里早被判过死刑——`ModelDropdown` 上方的注释记着
 * 「menuAnchor 会让整个输入框都变成点击开关菜单，反复点输入框会与键盘/焦点打架（用户反馈：多次点击输入框会崩溃）」。
 * 而 max_tokens / 历史条数恰恰是设置页里被点最多的两个输入框，却还留着这个写法（当时只改了模型下拉）。
 * 现在与 `ModelDropdown` 统一（两者共用的部分抽成 [InputWithPresets]）。
 *
 * 不带标签：它只出现在行表展开层里（「单条回复总输出上限」「携带历史条数」），上面那一行的行名
 * 已经写明了这是什么，再写一遍是重复（用户 2026-10-10）。
 */
@Composable
internal fun EditableDropdown(
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean = true,
    presets: List<String>,
    helper: String
) {
    InputWithPresets(
        value = value,
        onValue = onValue,
        presets = presets,
        enabled = enabled
    )
    Text(
        helper,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 「输入框 + 可下拉预设」的共用基座。
 *
 * [EditableDropdown]（max_tokens / 历史条数）与 [ModelDropdown]（模型）原本各写一份拷贝：
 * 输入框、独立箭头按钮、预设列表、底部"可自定义"提示完全一样，只有模型那侧多一个 👁 前缀
 * 与一个 ⚡ 连接测试按钮。
 *
 * **不要改回 `ExposedDropdownMenuBox`**：`menuAnchor()` 会让整个输入框变成"点击开关菜单"，
 * 反复点输入框会与键盘/焦点打架（用户反馈过崩溃）。输入框只负责输入，箭头只负责开列表。
 *
 * @param presetLabel 预设项的显示文案（模型列表用它给视觉模型加 👁 标记）
 * @param trailing 箭头右侧的附加控件（模型下拉放 ⚡ 连接测试）
 * @param label 输入框上方的灰标签；**行表展开层里传 null**——行名就在正上方，再画一遍是重复
 */
@Composable
internal fun InputWithPresets(
    value: String,
    onValue: (String) -> Unit,
    presets: List<String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    presetLabel: (String) -> String = { it },
    trailing: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    // 下拉菜单宽度＝输入框宽度：菜单默认按最宽的项包住内容，长模型名/音色名会把菜单撑得
    // 远超输入框（桌面上尤其难看）；量出锚点宽度喂给菜单，菜单内超长文本自带省略。
    var fieldWidthPx by remember { mutableStateOf(0) }
    // 底对齐：标签在框上方之后，箭头键要对齐的是**框**那一段（居中对齐会跑到标签高度上去）
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
        Box(
            Modifier
                .weight(1f)
                .onGloballyPositioned { fieldWidthPx = it.size.width }
        ) {
            // 输入框换 `WhaleHairField`（标签在框上方、框线 fieldLine）——M3 的浮起标签是
            // v10 合同点名的禁用项（禁用 4），而且它的标签会被中文长标签截断
            WhaleHairField(
                value = value,
                onValueChange = onValue,
                label = label,
                singleLine = true,
                enabled = enabled
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(with(LocalDensity.current) { fieldWidthPx.toDp() })
                    // 限高（2026-10-10）：模型/音色预设动辄几十条，菜单不许按条数一路拉长，
                    // 上限 320dp、超出在菜单内滚动（与聊天页模型菜单同一口径）。
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
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                Icons.Filled.ArrowDropDown,
                // 无标签时给通用描述：无障碍/冒烟脚本都靠它认这个箭头
                contentDescription = label?.let { "选择$it" } ?: "选择预设",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SettingsTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String = "",
    secret: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 与「模型与 API」同一口径（标签在框上方、框线 fieldLine）——M3 浮起标签是 v10 合同禁用 4
    WhaleHairField(
        value = value,
        onValueChange = onValue,
        modifier = modifier,
        label = label.ifBlank { null },
        placeholder = placeholder.ifBlank { null },
        singleLine = true,
        secret = secret,
        enabled = enabled
    )
}

/** 对话/创作/生图模型：下拉选当前服务商常见模型，也能直接上方输入自定义模型名；
 *  [test] 与 [onTest] 非空时在右侧显示 ⚡ 连接测试按钮，结果显示在下方一行 */
@Composable
internal fun ModelDropdown(
    value: String,
    onValue: (String) -> Unit,
    presets: List<String>,
    label: String = "模型",
    test: SettingsViewModel.ModelTest? = null,
    enabled: Boolean = true,
    onTest: (() -> Unit)? = null
) {
    val testButton: (@Composable () -> Unit)? = onTest?.let { run ->
        {
            IconButton(onClick = run, enabled = enabled && test?.loading != true) {
                if (test?.loading == true) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = "$label 连接测试",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    InputWithPresets(
        value = value,
        onValue = onValue,
        label = label,
        presets = presets,
        enabled = enabled,
        presetLabel = { p -> if (ModelCatalog.isVisionModel(p)) "👁 $p" else p },
        trailing = testButton
    )
    test?.ok?.let {
        Text(
            "✓ $it",
            style = MaterialTheme.typography.bodySmall,
            color = LocalWhalePalette.current.accentText,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    test?.err?.let {
        Text(
            "✗ $it",
            style = MaterialTheme.typography.bodySmall,
            color = LocalWhalePalette.current.dangerText,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 供应商胶囊（2026-09-14）：内置预设 + 用户自定义，用 FlowRow 换行展示（不再横向滚动，
 * 空间充足时一眼看全）；每个自定义项可点标签右侧的「编辑」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderChips(
    current: String,
    presets: List<String>,
    custom: List<CustomProvider>,
    /** true = 对话/创作分段（只看 chatBaseUrl），false = 生图分段（只看 imageBaseUrl） */
    forChat: Boolean = true,
    /**
     * 跟随模式：要求该供应商**同时有对话与生图 URL**才列出（2026-09-15 用户要求）。
     * 跟随是"一套供应商同时管两边"，只填了对话入口的家（DeepSeek/Kimi/Gemini）列出来只会选到打不通的配置。
     */
    requireImage: Boolean = false,
    /** API 配置分段：任何一侧有 URL 就列出（要能管理所有供应商的 Key） */
    anyCapability: Boolean = false,
    onPick: (String) -> Unit,
    onEdit: (CustomProvider) -> Unit,
    /** 非空时在胶囊末尾追加「＋ 自定义供应商」（2026-09-15 用户要求把该按钮放进胶囊里） */
    onAddCustom: (() -> Unit)? = null
) {
    val cur = current.trim()
    // 只在该分段有对应 URL 的自定义供应商才渲染成胶囊（跟随模式下再加"两边都要有"）。
    // 以前两个分段共用全量列表，且点选取 `chatBaseUrl.ifBlank { imageBaseUrl }`：
    // 一个"只填了生图 URL"的供应商会出现在**对话**分段里，点它就把 chatBaseUrl 写成生图端点，
    // 而且没有自动配置提示，用户根本看不出自己配错了。
    fun urlFor(cp: CustomProvider): String = when {
        anyCapability -> cp.chatBaseUrl.trim().ifBlank { cp.imageBaseUrl.trim() }
        forChat -> cp.chatBaseUrl.trim()
        else -> cp.imageBaseUrl.trim()
    }
    val visible = custom.filter {
        urlFor(it).isNotBlank() && (!requireImage || it.imageBaseUrl.trim().isNotBlank())
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        presets.forEach { url ->
            WhaleChip(
                selected = cur == url,
                onClick = { onPick(url) },
                label = { Text(ProviderProfiles.nameFor(url), maxLines = 1) }
            )
        }
        visible.forEach { cp ->
            val url = urlFor(cp)
            WhaleChip(
                selected = cur == url,
                onClick = { onPick(url) },
                label = { Text(cp.name.ifBlank { "自定义" }, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑 ${cp.name}",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onEdit(cp) }
                    )
                }
            )
        }
        // 「＋ 自定义供应商」放在胶囊末尾（新增的供应商总是排在最后，即 OpenAI 之后）
        onAddCustom?.let { add ->
            WhaleChip(
                selected = false,
                onClick = add,
                label = { Text("＋ 自定义供应商", maxLines = 1) }
            )
        }
    }
}

/**
 * 自定义供应商编辑弹窗（2026-09-14）：让非内置平台也能被自动适配。
 * 只需要名称 + URL，能力项按平台实际协议选（不确定就保持默认）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CustomProviderDialog(
    initial: CustomProvider?,
    onSave: (CustomProvider) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var chatUrl by remember { mutableStateOf(initial?.chatBaseUrl ?: "") }
    var imageUrl by remember { mutableStateOf(initial?.imageBaseUrl ?: "") }
    var thinkingKind by remember { mutableStateOf(initial?.thinkingKind ?: "NONE") }
    var tempSupported by remember { mutableStateOf(initial?.temperatureSupported ?: true) }
    var imageFormatForced by remember { mutableStateOf(initial?.imageFormatForced ?: "") }
    var imageProtocol by remember { mutableStateOf(initial?.imageProtocol ?: "OPENAI") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    // 删供应商会丢掉用户填的 Base URL / 备注等配置（2026-09-21 统一补确认）
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete && initial != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除「${initial.name}」？") },
            text = { Text("这家供应商的 Base URL、备注与协议设置会被移除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete(initial.id) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } }
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增自定义供应商" else "编辑自定义供应商") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 弹窗里的字段同样"标签在框上方、灰字"（与整页同一份 WhaleHairField，见其注释）
                WhaleHairField(
                    value = name, onValueChange = { name = it },
                    label = "名称", singleLine = true
                )
                WhaleHairField(
                    value = chatUrl, onValueChange = { chatUrl = it },
                    label = "对话 Base URL（不用对话可留空）", singleLine = true,
                    placeholder = "https://api.example.com/v1"
                )
                WhaleHairField(
                    value = imageUrl, onValueChange = { imageUrl = it },
                    label = "生图 Base URL（不用生图可留空）", singleLine = true
                )
                Text("思考参数（该平台用哪种开关）", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "NONE" to "不支持",
                        "THINKING_TYPE" to "thinking.type",
                        "ENABLE_THINKING" to "enable_thinking",
                        "EFFORT_LOW_HIGH" to "reasoning_effort(低/高)",
                        "EFFORT_NONE_HIGH" to "reasoning_effort(关/高)"
                    ).forEach { (v, label) ->
                        WhaleChip(
                            selected = thinkingKind == v,
                            onClick = { thinkingKind = v },
                            label = { Text(label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tempSupported, onCheckedChange = { tempSupported = it })
                    Text("该平台接受 temperature 参数", style = MaterialTheme.typography.bodySmall)
                }
                Text("生图协议", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderProfiles.ImageProtocol.values().forEach { p ->
                        WhaleChip(
                            selected = imageProtocol == p.name,
                            onClick = { imageProtocol = p.name },
                            label = { Text(p.shortName) }
                        )
                    }
                }
                Text("生图返回格式", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "跟随用户设置", "url" to "固定 url", "b64_json" to "固定 base64").forEach { (v, label) ->
                        WhaleChip(
                            selected = imageFormatForced == v,
                            onClick = { imageFormatForced = v },
                            label = { Text(label) }
                        )
                    }
                }
                WhaleHairField(
                    value = note, onValueChange = { note = it },
                    label = "备注（会显示在能力卡片里，可选）",
                    singleLine = false, minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (chatUrl.isNotBlank() || imageUrl.isNotBlank()),
                onClick = {
                    onSave(
                        CustomProvider(
                            id = initial?.id ?: Repository.newId(),
                            name = name.trim(),
                            chatBaseUrl = chatUrl.trim(),
                            imageBaseUrl = imageUrl.trim(),
                            thinkingKind = thinkingKind,
                            temperatureSupported = tempSupported,
                            imageFormatForced = imageFormatForced,
                            imageProtocol = imageProtocol,
                            note = note.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { confirmingDelete = true }) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
