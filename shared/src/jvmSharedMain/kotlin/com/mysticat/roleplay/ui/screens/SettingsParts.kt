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
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.Platform

/**
 * 桌面三栏内嵌形态的**窄头部**：退出键 + 分段名 + 未保存提示 + 保存按钮。
 *
 * 整页形态里这三样分别是"顶栏返回箭头""顶栏标题""顶栏右侧的对勾图标"；
 * 内嵌进第三栏后没有自己的顶栏，就需要一条横排把它们放下（设置页与语音页共用）。
 * [onBack] 走调用方的 `requestBack`（有未保存改动会先弹确认），不是直接关页。
 */
@Composable
internal fun EmbeddedSettingsHeader(
    title: String,
    dirty: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = colors.onSurfaceVariant
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            if (dirty) "有未保存的修改" else "已是最新",
            style = MaterialTheme.typography.labelSmall,
            color = if (dirty) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onSave, enabled = dirty) { Text("保存") }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 思考强度档位选择（2026-09-14 改造为自适应）：
 * 档位来自 [ProviderProfiles.thinkingLevels] —— 不同服务商/模型能提供的档位不同
 * （Kimi K3 关不掉、GLM-5.3 只能降强度、OpenAI 只有低/高），
 * 因此不再固定显示"默认/关闭/深度"三档，而是按当前供应商与模型渲染，并说明实际发什么参数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThinkingChips(
    label: String,
    value: String,
    levels: List<ProviderProfiles.ThinkingLevel>,
    onValue: (String) -> Unit,
    enabled: Boolean = true
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            levels.forEach { lv ->
                WhaleChip(
                    enabled = enabled,
                    selected = value == lv.mode,
                    onClick = { onValue(lv.mode) },
                    label = { Text(lv.label) }
                )
            }
        }
        levels.firstOrNull { it.mode == value }?.let {
            Text(
                it.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 「接入能力」卡片：把代码里已经固定好的适配显式告诉用户（2026-09-14 新增）。
 * 目的：用户换服务商后如果发现"返回格式被改了""温度不见了"，能在这里看到原因，
 * 而不是以为配置坏了；同时给出官方文档与价格页入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderCapabilityCard(
    chatBaseUrl: String,
    chatModel: String,
    imageBaseUrl: String,
    imageModel: String,
    custom: List<CustomProvider> = emptyList()
) {
    val chat = ProviderProfiles.resolve(chatBaseUrl, custom)
    val image = ProviderProfiles.resolve(imageBaseUrl, custom)
    Column {
        Text("当前接入能力（App 已自动适配）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
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
        color = MaterialTheme.colorScheme.error
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
        Text(value, style = MaterialTheme.typography.bodySmall)
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
 */
@Composable
internal fun EditableDropdown(
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean = true,
    presets: List<String>,
    label: String,
    helper: String
) {
    InputWithPresets(
        value = value,
        onValue = onValue,
        label = label,
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
 */
@Composable
internal fun InputWithPresets(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    presets: List<String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    presetLabel: (String) -> String = { it },
    trailing: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    // 下拉菜单宽度＝输入框宽度：菜单默认按最宽的项包住内容，长模型名/音色名会把菜单撑得
    // 远超输入框（桌面上尤其难看）；量出锚点宽度喂给菜单，菜单内超长文本自带省略。
    var fieldWidthPx by remember { mutableStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .weight(1f)
                .onGloballyPositioned { fieldWidthPx = it.size.width }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                trailingIcon = {
                    IconButton(onClick = { expanded = true }, enabled = enabled) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "选择$label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(with(LocalDensity.current) { fieldWidthPx.toDp() })
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
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    test?.err?.let {
        Text(
            "✗ $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
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
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chatUrl, onValueChange = { chatUrl = it },
                    label = { Text("对话 Base URL（不用对话可留空）") }, singleLine = true,
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = imageUrl, onValueChange = { imageUrl = it },
                    label = { Text("生图 Base URL（不用生图可留空）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（会显示在能力卡片里，可选）") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
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
