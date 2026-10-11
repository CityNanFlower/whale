package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.BUDGET_MODE_CUSTOM
import com.mysticat.roleplay.data.BUDGET_MODE_LOOSE
import com.mysticat.roleplay.data.BUDGET_MODE_SAVING
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Security
import com.mysticat.roleplay.ui.components.LedgerAction
import com.mysticat.roleplay.ui.components.LedgerNote
import com.mysticat.roleplay.ui.components.LedgerTone
import com.mysticat.roleplay.ui.components.WhaleConclusionBar
import com.mysticat.roleplay.ui.components.WhaleGroupLabel
import com.mysticat.roleplay.ui.components.WhaleHairField
import com.mysticat.roleplay.ui.components.WhaleLedgerRow
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import kotlin.math.roundToInt

/**
 * 「模型与 API」四个分段（2026-10-08 第 23 轮重排为**行表骨架**）。
 *
 * 为什么换骨架：原来四页共用"分段胶囊 → 节标题 → 白卡 → 成排 OutlinedTextField"，
 * 三十来个输入框长得一模一样——"要改的那一项"找不到，值也不可扫读。现在按 D2 拍板的
 * A 骨架（一页一表）＋ C 的结论条重排：**结论条 → 分组 → 行（名｜值｜动作，就地展开编辑器）**。
 * 组件在 `ui/components/WhaleLedger.kt`，契约＝行表式 DESIGN 合同。
 *
 * 三条保留口径（与旧实现一字不差）：
 * ① 一家一份 Key，加密存本机；该家没存 Key 时模型与参数置灰并给提示；
 * ② 三个模型页各自独立选供应商，选到哪家就用哪家的 Key；
 * ③ 全部写盘动作仍走本页 ViewModel，**没有新增/改变的存储语义**。
 */
internal fun LazyListScope.apiSettingsSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit,
    onAddCustom: () -> Unit
) {
    item { ApiConfigLedger(vm, onEdit, onAddCustom) }
}

/** 设置页 · 对话模型分段：结论条 ＋ 供应商行 ＋ 模型/参数/预算/提示词行表 ＋ 接入能力行。 */
internal fun LazyListScope.chatModelSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    item { ChatModelLedger(vm, onEdit) }
}

/** 设置页 · 创作模型分段：与对话页同构，只是字段换成创作那一套。 */
internal fun LazyListScope.creationModelSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    item { CreationModelLedger(vm, onEdit) }
}

/** 设置页 · 生图模型分段：总开关 +（启用时）供应商与模型 + 返回格式 + 接入能力。 */
internal fun LazyListScope.imageModelSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    item { ImageModelLedger(vm, onEdit) }
}

/* ────────────────────────────── API 配置 ────────────────────────────── */

@Composable
private fun ApiConfigLedger(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit,
    onAddCustom: () -> Unit
) {
    var open by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) { open = if (open == key) "" else key }

    val provider = vm.providerLabel(vm.apiKeyUrl)
    val hasKey = vm.hasSavedKeyForCurrentApiProvider
    Column {
        WhaleConclusionBar(
            title = if (hasKey) "Key 已存 · $provider" else "尚未填 Key · $provider",
            subtitle = (if (Security.encryptedStorageAvailable) "加密存储在本机 · 不进备份" else "本机明文存储 · 不进备份"),
            tone = if (hasKey) LedgerTone.Ok else LedgerTone.Alert
        )
        WhaleGroupLabel("供应商与 KEY")
        // 内置供应商：选中哪家就在下面填哪家的 Key（一家一份）
        WhaleLedgerRow(
            label = "供应商",
            value = provider,
            valueMono = false,
            actionText = if (open == "prov") "收起" else "更换",
            expanded = open == "prov",
            onToggle = { toggle("prov") },
            content = {
                ProviderChips(
                    current = vm.apiKeyUrl,
                    presets = ModelCatalog.allProviderPresets(),
                    // 自定义供应商在下一行单独管理，这里只列内置清单（两处都列会重复一份）
                    custom = emptyList(),
                    anyCapability = true,
                    onPick = vm::selectApiKeyProvider,
                    onEdit = onEdit
                )
            }
        )
        // API Key：值只显示打码形态；点开才是输入框（原页那一套加密/清除/说明一字不差）
        WhaleLedgerRow(
            label = "API Key",
            value = when {
                hasKey -> maskKey(vm.apiKey)
                vm.apiKey.isNotBlank() -> "未保存"
                else -> "未填"
            },
            valueMono = true,
            valueDim = !hasKey,
            actionText = if (open == "key") "收起" else if (hasKey || vm.apiKey.isNotBlank()) "更换" else "填入",
            expanded = open == "key",
            onToggle = { toggle("key") },
            content = {
                // ⚠️ 用 key(...) 让输入框在切换供应商时**整体重建**：
                // 否则 Compose 会在 value 被程序改掉后回调一次旧文本，
                // 把上一家的 Key 写进新选中的供应商名下（查起来极隐蔽）
                key(vm.apiKeyUrl) {
                    WhaleHairField(
                        value = vm.apiKey,
                        onValueChange = vm::updateApiKey,
                        label = "${vm.providerLabel(vm.apiKeyUrl)} 的 API Key",
                        placeholder = "粘贴这家的 Key",
                        secret = true
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LedgerNote(
                        // 文案按实况说话：密钥库可用才说"加密存储"，明文回退不得谎称
                        when {
                            vm.hasSavedKeyForCurrentApiProvider && Security.encryptedStorageAvailable ->
                                "已保存（加密存储，切回来仍在）"
                            vm.hasSavedKeyForCurrentApiProvider -> "已保存（本机明文存储，功能不受影响）"
                            else -> "尚未保存 Key —— 这家在下面三个模型页里会置灰"
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (vm.hasSavedKeyForCurrentApiProvider || vm.apiKey.isNotBlank()) {
                        LedgerAction("清除这家的 Key") { vm.clearKeyForCurrentProvider() }
                    }
                }
                LedgerNote(
                    "点上面的供应商切换要填写的家；" +
                        if (Security.encryptedStorageAvailable) {
                            "Key 用 ${Security.keyProviderName ?: "本机安全模块"} 加密后保存在本机，不进备份文件。"
                        } else {
                            "本机安全模块暂不可用，Key 将以明文保存在本机（不进备份文件），功能不受影响。"
                        } +
                        "对话 / 创作 / 生图三个页面各自选供应商，选到哪家就用哪家的 Key。"
                )
            }
        )
        // 自定义供应商：管理面（新增/编辑/删除）集中在行内，不再散在胶囊列表里
        WhaleLedgerRow(
            label = "自定义供应商",
            value = "${vm.customProviders.size} 家",
            actionText = if (open == "custom") "收起" else "管理",
            expanded = open == "custom",
            onToggle = { toggle("custom") },
            content = {
                ProviderChips(
                    current = "",
                    presets = emptyList(),
                    custom = vm.customProviders,
                    anyCapability = true,
                    onPick = vm::selectApiKeyProvider,
                    onEdit = onEdit,
                    onAddCustom = onAddCustom
                )
                LedgerNote("内置清单之外的家加在这里：自建代理、本地部署、以及要自己填 Base URL 的服务商。")
            }
        )
    }
}

/** Key 的打码形态：保留前缀与末 4 位（够认出是哪一把），中间全打点。 */
private fun maskKey(key: String): String = when {
    key.isBlank() -> "已存"
    key.length <= 8 -> "••••"
    else -> key.take(3) + "••••" + key.takeLast(4)
}

/* ─────────────────────────── 对话 / 创作模型 ─────────────────────────── */

@Composable
private fun ChatModelLedger(vm: SettingsViewModel, onEdit: (CustomProvider) -> Unit) {
    var open by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) { open = if (open == key) "" else key }

    val hasKey = vm.hasKeyFor(vm.chatBaseUrl)
    val provider = vm.providerLabel(vm.chatBaseUrl)
    val test = vm.chatTest
    Column {
        WhaleConclusionBar(
            title = if (hasKey) "对话可用 · $provider" else "对话未配置 · $provider",
            subtitle = vm.chatModel.ifBlank { "未选模型" } + " · " + testWord(test?.loading == true, test?.ok, test?.err),
            tone = if (!hasKey || test?.err != null) LedgerTone.Alert else LedgerTone.Ok,
            action = {
                LedgerAction(
                    if (test?.loading == true) "测试中" else "测试",
                    enabled = hasKey && test?.loading != true
                ) { vm.testChatModel() }
            }
        )
        WhaleGroupLabel("对话模型与参数")
        WhaleLedgerRow(
            label = "供应商",
            value = provider,
            valueMono = false,
            actionText = if (open == "prov") "收起" else "更换",
            expanded = open == "prov",
            onToggle = { toggle("prov") },
            content = {
                ProviderChips(
                    current = vm.chatBaseUrl,
                    presets = ModelCatalog.baseUrlPresets(),
                    custom = vm.customProviders,
                    forChat = true,
                    onPick = vm::switchChatProvider,
                    onEdit = onEdit
                )
            }
        )
        if (!hasKey) {
            WhaleLedgerRow(label = "缺少 Key", value = "这一页暂时不能用", valueMono = false, enabled = false)
            LedgerNote(NoKeyHintText(vm.providerLabel(vm.chatBaseUrl)), modifier = Modifier.padding(WhaleSpacing.md))
        }
        WhaleLedgerRow(
            label = "模型",
            value = vm.chatModel.ifBlank { "未选" },
            enabled = hasKey,
            actionText = if (open == "model") "收起" else "更换",
            expanded = open == "model",
            onToggle = { toggle("model") },
            content = {
                // 展开层不画标签：行名「模型」就在正上方，再写一遍是重复（用户 2026-10-10）
                InputWithPresets(
                    value = vm.chatModel,
                    onValue = { vm.chatModel = it },
                    presets = ModelCatalog.chatPresets(vm.chatBaseUrl),
                    enabled = hasKey,
                    presetLabel = { p -> if (ModelCatalog.isVisionModel(p)) "👁 $p" else p }
                )
                inputResultNote(test?.ok, test?.err)
            }
        )
        val levels = ProviderProfiles.thinkingLevels(vm.chatBaseUrl, vm.chatModel)
        val profile = ProviderProfiles.resolve(vm.chatBaseUrl, vm.customProviders)
        WhaleLedgerRow(
            label = "思考强度",
            value = levels.firstOrNull { it.mode == vm.chatThinking }?.label ?: if (levels.isEmpty()) "未适配" else vm.chatThinking,
            valueMono = levels.isEmpty(),
            valueDim = levels.isEmpty(),
            enabled = hasKey,
            actionText = if (open == "think") "收起" else "调整",
            expanded = open == "think",
            onToggle = { toggle("think") },
            content = {
                if (levels.isEmpty()) {
                    LedgerNote(
                        profile?.let { "${it.name} 未适配思考参数，保持模型默认行为（不发送任何思考字段）。" }
                            ?: "当前 Base URL 未匹配到已知服务商，保持模型默认行为。"
                    )
                } else {
                    ThinkingChips(vm.chatThinking, levels, onValue = { vm.chatThinking = it }, enabled = hasKey)
                    LedgerNote("实际参数：${profile?.thinking?.paramLabel ?: "—"}")
                }
            }
        )
        WhaleLedgerRow(
            label = "内容拒答自动回落",
            value = if (vm.contentFallback) "开" else "关",
            valueMono = false,
            actionText = if (open == "fallback") "收起" else "说明",
            expanded = open == "fallback",
            onToggle = { toggle("fallback") },
            trailing = {
                Switch(checked = vm.contentFallback, onCheckedChange = { vm.contentFallback = it })
            },
            content = {
                LedgerNote(
                    "模型因内容拒答（空回复 / 审核报错）时，自动换更含蓄的写法重试一次，最多 2 级。" +
                        "回落过会在聊天页给一行提示。关掉即保持原样：只提示、不重试。"
                )
            }
        )
        tempRow(vm, hasKey, open, ::toggle)
        budgetRows(vm, hasKey, open, ::toggle)
        WhaleLedgerRow(
            label = "全局补充系统提示词",
            value = if (vm.extraSystemPrompt.isBlank()) "未填" else "已填 ${vm.extraSystemPrompt.length} 字",
            valueMono = false,
            valueDim = vm.extraSystemPrompt.isBlank(),
            enabled = hasKey,
            actionText = if (open == "prompt") "收起" else "编辑",
            expanded = open == "prompt",
            onToggle = { toggle("prompt") },
            content = {
                WhaleHairField(
                    value = vm.extraSystemPrompt,
                    onValueChange = { vm.extraSystemPrompt = it },
                    placeholder = "例如：所有角色都使用中文回复；允许适度擦边但不含露骨描写",
                    singleLine = false,
                    minLines = 2
                )
                LedgerNote(
                    // 原来这句「（可选）」挂在输入框标签上，标签撤掉后挪进说明里（用户 2026-10-10）
                    "（可选）这段内容会追加在每次对话系统提示词的最后（标记为【补充要求】），对所有角色、所有会话生效，" +
                        "适合写通用的说话风格与边界要求；想针对某个角色单独要求，写进角色卡的人设里更合适。"
                )
            }
        )
        capabilityRow(
            fallbackLabel = "接入能力",
            summary = { ProviderProfiles.resolve(vm.chatBaseUrl, vm.customProviders)?.name },
            enabled = true,
            open = open == "cap",
            onToggle = { toggle("cap") },
            body = { ProviderCapabilityBody(vm.chatBaseUrl, vm.chatModel, "", "", vm.customProviders) }
        )
    }
}

@Composable
private fun CreationModelLedger(vm: SettingsViewModel, onEdit: (CustomProvider) -> Unit) {
    var open by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) { open = if (open == key) "" else key }

    val hasKey = vm.hasKeyFor(vm.creationBaseUrl)
    val provider = vm.providerLabel(vm.creationBaseUrl)
    val test = vm.creationTest
    Column {
        WhaleConclusionBar(
            title = if (hasKey) "创作可用 · $provider" else "创作未配置 · $provider",
            subtitle = vm.creationModel.ifBlank { "未选模型" } + " · " + testWord(test?.loading == true, test?.ok, test?.err),
            tone = if (!hasKey || test?.err != null) LedgerTone.Alert else LedgerTone.Ok,
            action = {
                LedgerAction(
                    if (test?.loading == true) "测试中" else "测试",
                    enabled = hasKey && test?.loading != true
                ) { vm.testCreationModel() }
            }
        )
        WhaleGroupLabel("创作模型（AI 生成角色卡、起草提示词用）")
        WhaleLedgerRow(
            label = "供应商",
            value = provider,
            valueMono = false,
            actionText = if (open == "prov") "收起" else "更换",
            expanded = open == "prov",
            onToggle = { toggle("prov") },
            content = {
                ProviderChips(
                    current = vm.creationBaseUrl,
                    presets = ModelCatalog.baseUrlPresets(),
                    custom = vm.customProviders,
                    forChat = true,
                    onPick = vm::switchCreationProvider,
                    onEdit = onEdit
                )
            }
        )
        if (!hasKey) {
            LedgerNote(NoKeyHintText(provider), modifier = Modifier.padding(WhaleSpacing.md))
        }
        WhaleLedgerRow(
            label = "模型",
            value = vm.creationModel.ifBlank { "未选" },
            enabled = hasKey,
            actionText = if (open == "model") "收起" else "更换",
            expanded = open == "model",
            onToggle = { toggle("model") },
            content = {
                InputWithPresets(
                    value = vm.creationModel,
                    onValue = { vm.creationModel = it },
                    presets = ModelCatalog.creationPresets(vm.creationBaseUrl),
                    enabled = hasKey,
                    presetLabel = { p -> if (ModelCatalog.isVisionModel(p)) "👁 $p" else p }
                )
                inputResultNote(test?.ok, test?.err)
                LedgerNote(
                    "创作模型只用于「AI 生成角色卡」与「起草生图提示词」；生成角色卡时输出上限固定为 8192，" +
                        "且起草/扩写会强制关闭思考以避免等待过久。"
                )
            }
        )
        val creationLevels = ProviderProfiles.thinkingLevels(vm.creationBaseUrl, vm.creationModel)
        val profile = ProviderProfiles.resolve(vm.creationBaseUrl, vm.customProviders)
        WhaleLedgerRow(
            label = "思考强度",
            value = creationLevels.firstOrNull { it.mode == vm.creationThinking }?.label
                ?: if (creationLevels.isEmpty()) "未适配" else vm.creationThinking,
            valueMono = creationLevels.isEmpty(),
            valueDim = creationLevels.isEmpty(),
            enabled = hasKey,
            actionText = if (open == "think") "收起" else "调整",
            expanded = open == "think",
            onToggle = { toggle("think") },
            content = {
                if (creationLevels.isEmpty()) {
                    LedgerNote(
                        profile?.let { "${it.name} 未适配思考参数，保持模型默认行为。" }
                            ?: "当前 Base URL 未匹配到已知服务商，保持模型默认行为。"
                    )
                } else {
                    ThinkingChips(
                        vm.creationThinking, creationLevels,
                        onValue = { vm.creationThinking = it }, enabled = hasKey
                    )
                    LedgerNote("实际参数：${profile?.thinking?.paramLabel ?: "—"}（与对话思考强度是两组独立设置）")
                }
            }
        )
        capabilityRow(
            fallbackLabel = "接入能力",
            summary = { ProviderProfiles.resolve(vm.creationBaseUrl, vm.customProviders)?.name },
            enabled = true,
            open = open == "cap",
            onToggle = { toggle("cap") },
            body = { ProviderCapabilityBody(vm.creationBaseUrl, vm.creationModel, "", "", vm.customProviders) }
        )
    }
}

/* ────────────────────────────── 生图模型 ────────────────────────────── */

@Composable
private fun ImageModelLedger(vm: SettingsViewModel, onEdit: (CustomProvider) -> Unit) {
    var open by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) { open = if (open == key) "" else key }

    val hasKey = vm.hasKeyFor(vm.imageBaseUrl)
    val provider = vm.providerLabel(vm.imageBaseUrl)
    val test = vm.imageTest
    val imageProfile = ProviderProfiles.resolve(vm.imageBaseUrl, vm.customProviders)
    val forced = imageProfile?.imageFormatForced
    val isMiniMax = imageProfile?.imageProtocol == ProviderProfiles.ImageProtocol.MINIMAX
    val b64Label = if (isMiniMax) "base64" else "b64_json"
    Column {
        WhaleConclusionBar(
            title = when {
                !vm.imageEnabled -> "生图未启用"
                hasKey -> "生图可用 · $provider"
                else -> "生图未配置 · $provider"
            },
            subtitle = if (!vm.imageEnabled) "打开下面的开关后可用"
            else vm.imageModel.ifBlank { "未选模型" } + " · " + testWord(test?.loading == true, test?.ok, test?.err),
            tone = when {
                !vm.imageEnabled -> LedgerTone.Idle
                !hasKey || test?.err != null -> LedgerTone.Alert
                else -> LedgerTone.Ok
            },
            action = if (vm.imageEnabled && hasKey) {
                {
                    LedgerAction(
                        if (test?.loading == true) "生成中" else "测试生图",
                        enabled = test?.loading != true
                    ) { vm.requestImageTest() }
                }
            } else null
        )
        WhaleGroupLabel("AI 生图服务")
        WhaleLedgerRow(
            label = "生图服务",
            value = if (vm.imageEnabled) "开" else "关",
            valueMono = false,
            actionText = if (open == "on") "收起" else "说明",
            expanded = open == "on",
            onToggle = { toggle("on") },
            trailing = {
                Switch(checked = vm.imageEnabled, onCheckedChange = { vm.imageEnabled = it })
            },
            content = {
                LedgerNote("用于生成角色头像与聊天背景图；生成按张计费。")
            }
        )
        if (vm.imageEnabled) {
            WhaleGroupLabel("生图供应商与模型")
            WhaleLedgerRow(
                label = "供应商",
                value = provider,
                valueMono = false,
                actionText = if (open == "prov") "收起" else "更换",
                expanded = open == "prov",
                onToggle = { toggle("prov") },
                content = {
                    ProviderChips(
                        current = vm.imageBaseUrl,
                        presets = ModelCatalog.imageBaseUrlPresets(),
                        custom = vm.customProviders,
                        forChat = false,
                        onPick = vm::switchImageProvider,
                        onEdit = onEdit
                    )
                }
            )
            if (!hasKey) {
                LedgerNote(NoKeyHintText(provider), modifier = Modifier.padding(WhaleSpacing.md))
            }
            WhaleLedgerRow(
                label = "生图模型",
                value = vm.imageModel.ifBlank { "未选" },
                enabled = hasKey,
                actionText = if (open == "model") "收起" else "更换",
                expanded = open == "model",
                onToggle = { toggle("model") },
                content = {
                    InputWithPresets(
                        value = vm.imageModel,
                        onValue = { vm.imageModel = it },
                        presets = ModelCatalog.imagePresets(vm.imageBaseUrl),
                        enabled = hasKey
                    )
                    inputResultNote(test?.ok, test?.err)
                }
            )
            WhaleLedgerRow(
                label = "返回格式",
                value = forced ?: vm.imageFormat,
                valueMono = true,
                enabled = hasKey,
                actionText = if (open == "fmt") "收起" else "调整",
                expanded = open == "fmt",
                onToggle = { toggle("fmt") },
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)) {
                        com.mysticat.roleplay.ui.WhaleChip(
                            selected = (forced ?: vm.imageFormat) == "b64_json",
                            enabled = forced == null || forced == "b64_json",
                            onClick = { vm.imageFormat = "b64_json" },
                            label = { Text(b64Label) }
                        )
                        com.mysticat.roleplay.ui.WhaleChip(
                            selected = (forced ?: vm.imageFormat) == "url",
                            enabled = forced == null || forced == "url",
                            onClick = { vm.imageFormat = "url" },
                            label = { Text("url") }
                        )
                    }
                    LedgerNote(
                        if (forced != null) {
                            "${imageProfile?.name} 只支持 $forced，此项已锁定；你的偏好设置不会被改写，换到其他服务商会自动恢复。"
                        } else {
                            "$b64Label：直接返回图片数据，本 App 保存到本地，推荐；url：返回图片链接，" +
                                "需服务商允许外网访问。" +
                                (if (isMiniMax) "（MiniMax 的 base64 即 b64_json）" else "当前服务商两者都可选。")
                        },
                        danger = forced != null
                    )
                }
            )
        }
        capabilityRow(
            fallbackLabel = "接入能力",
            summary = { ProviderProfiles.resolve(vm.imageBaseUrl, vm.customProviders)?.name },
            enabled = true,
            open = open == "cap",
            onToggle = { toggle("cap") },
            body = { ProviderCapabilityBody("", "", vm.imageBaseUrl, vm.imageModel, vm.customProviders) }
        )
    }
}

/* ────────────────────────────── 共用小件 ────────────────────────────── */

/** 结论条副句里的测试状态词：**只写算得出来的**（没测过就写"未测试"）。 */
private fun testWord(loading: Boolean, ok: String?, err: String?): String = when {
    loading -> "正在测试…"
    ok != null -> "上次测试通过"
    err != null -> "上次测试失败"
    else -> "未测试"
}

/** 展开层里的连接测试结果（原文案保留：✓ / ✗ 两行）。 */
@Composable
private fun inputResultNote(ok: String?, err: String?) {
    ok?.let { LedgerNote("✓ $it") }
    err?.let { LedgerNote("✗ $it", danger = true) }
}

/** `NoKeyHint` 的纯文本形态（行表里嵌不进它原来的块级排版）。 */
private fun NoKeyHintText(providerName: String): String =
    "「$providerName」还没有 Key：去「API 配置」选中它、填入 Key 之后，这一页的模型与参数才能用。"

/** 温度行（对话页专属）：值＋滑杆就地展开；不支持温度的家置灰并说明。 */
@Composable
private fun tempRow(
    vm: SettingsViewModel,
    hasKey: Boolean,
    open: String,
    toggle: (String) -> Unit
) {
    val tempSupported = ProviderProfiles
        .resolve(vm.chatBaseUrl, vm.customProviders)
        ?.temperatureSupported ?: true
    WhaleLedgerRow(
        label = "温度 Temperature",
        value = String.format("%.2f", vm.temperature),
        enabled = tempSupported && hasKey,
        actionText = if (open == "temp") "收起" else "调整",
        expanded = open == "temp",
        onToggle = { toggle("temp") },
        content = {
            Slider(
                value = vm.temperature.toFloat(),
                // 保留两位小数：滑杆是 Float，直接 toDouble() 会产生
                // 0.8500000238418579 这类长尾值，智谱会报「temperature 参数非法」
                onValueChange = { vm.temperature = (it * 100).roundToInt() / 100.0 },
                valueRange = 0f..2f,
                enabled = tempSupported && hasKey
            )
            if (!tempSupported) {
                LedgerNote(
                    "当前供应商不支持 temperature 参数（如 Kimi 会报 invalid temperature），" +
                        "App 不会发送它，此项已停用。"
                )
            } else {
                LedgerNote("范围 0~2：越高越发散，写剧情对话常用 0.8~1.2。")
            }
        }
    )
}

/** 预算三件（对话页专属）：对话预算档 ＋ 长度上限 ＋ 上下文预算 ＋ 历史条数，逐行就地展开。 */
@Composable
private fun budgetRows(
    vm: SettingsViewModel,
    hasKey: Boolean,
    open: String,
    toggle: (String) -> Unit
) {
    WhaleGroupLabel("预算")
    WhaleLedgerRow(
        label = "对话预算",
        value = when (vm.budgetMode) {
            BUDGET_MODE_SAVING -> "节省"
            BUDGET_MODE_CUSTOM -> "自定义"
            else -> "宽松"
        },
        valueMono = false,
        enabled = hasKey,
        actionText = if (open == "budget") "收起" else "调整",
        expanded = open == "budget",
        onToggle = { toggle("budget") },
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)) {
                com.mysticat.roleplay.ui.WhaleChip(
                    selected = vm.budgetMode == BUDGET_MODE_LOOSE,
                    onClick = { vm.budgetMode = BUDGET_MODE_LOOSE },
                    label = { Text("宽松") }
                )
                com.mysticat.roleplay.ui.WhaleChip(
                    selected = vm.budgetMode == BUDGET_MODE_SAVING,
                    onClick = { vm.applySavingPreset() },
                    label = { Text("节省") }
                )
                com.mysticat.roleplay.ui.WhaleChip(
                    selected = vm.budgetMode == BUDGET_MODE_CUSTOM,
                    onClick = { vm.budgetMode = BUDGET_MODE_CUSTOM },
                    label = { Text("自定义") }
                )
            }
            LedgerNote(
                when (vm.budgetMode) {
                    BUDGET_MODE_CUSTOM -> "自定义＝三项旋钮：总输出（下面）＋ 思考强度（上方）＋ 上下文预算；档位与下限按当前模型生成。"
                    BUDGET_MODE_SAVING -> "节省＝关思考 ＋ 该模型允许的最低总输出档 ＋ 12000 字上下文（一次填好下面三项，之后仍可微调）。" +
                        "思考关不掉的模型会按住它自己的最低档走，正文不会因为推理吃光配额而变空。"
                    else -> "宽松＝默认行为：按下面的长度上限发送，上下文超 24000 字符自动从最早丢弃。"
                }
            )
        }
    )
    val loose = vm.budgetMode == BUDGET_MODE_LOOSE
    WhaleLedgerRow(
        label = "单条回复总输出上限",
        value = if (loose) vm.maxTokensText else vm.customMaxTokensText,
        enabled = hasKey,
        actionText = if (open == "maxtok") "收起" else "调整",
        expanded = open == "maxtok",
        onToggle = { toggle("maxtok") },
        content = {
            if (loose) {
                val configured = vm.maxTokensText.toIntOrNull() ?: 2048
                val effective = ProviderProfiles.effectiveMaxTokens(
                    vm.chatBaseUrl, vm.chatThinking, vm.chatModel, configured, vm.customProviders
                )
                EditableDropdown(
                    value = vm.maxTokensText,
                    onValue = { vm.maxTokensText = it },
                    presets = listOf("256", "512", "1024", "2048", "4096", "8192", "16384"),
                    // 原来「（仅对话）」挂在输入框标签上，标签撤掉后挪进说明里（用户 2026-10-10）
                    helper = "（仅对话）默认 2048。思考 token 也占这份额度，所以「设置值 < 8192 且当前会思考」时实际按 8192 发送" +
                        "（否则会出现正文为空）；你的设置值本身会保留，切回不思考的模型/档位后自动恢复。\n" +
                        if (effective != configured) "当前实际发送：$effective（你的设置：$configured）"
                        else "当前实际发送：$configured",
                    enabled = hasKey
                )
            } else {
                BudgetKnobChips(
                    options = ProviderProfiles.budgetLevels(vm.chatBaseUrl, vm.chatModel, vm.customProviders)
                        .map { it.toString() },
                    selected = vm.customMaxTokensText,
                    onSelect = { vm.customMaxTokensText = it },
                    enabled = hasKey,
                    hint = "总是思考的模型不给 8192 以下档位：思考与正文共用这份额度，低档位会被推理吃光、正文为空。" +
                        "当前实际发送按思考强度自动抬升（与宽松档同一套规则）。"
                )
            }
        }
    )
    if (!loose) {
        WhaleLedgerRow(
            label = "上下文字符预算",
            value = vm.customContextCharsText,
            enabled = hasKey,
            actionText = if (open == "ctx") "收起" else "调整",
            expanded = open == "ctx",
            onToggle = { toggle("ctx") },
            content = {
                BudgetKnobChips(
                    options = listOf("8000", "12000", "16000", "24000", "32000", "48000"),
                    selected = vm.customContextCharsText,
                    onSelect = { vm.customContextCharsText = it },
                    enabled = hasKey,
                    hint = "历史总量超过该值就从最早的消息开始丢弃（宽松档固定 24000）；「携带历史条数」仍同时生效。"
                )
            }
        )
    }
    WhaleLedgerRow(
        label = "携带历史条数",
        value = vm.historyLimitText,
        enabled = hasKey,
        actionText = if (open == "hist") "收起" else "调整",
        expanded = open == "hist",
        onToggle = { toggle("hist") },
            content = {
                EditableDropdown(
                    value = vm.historyLimitText,
                    onValue = { vm.historyLimitText = it },
                    presets = listOf("10", "20", "40", "60", "80", "100"),
                    // 原来「（仅对话）」挂在输入框标签上，标签撤掉后挪进说明里（用户 2026-10-10）
                    helper = "（仅对话）聊天时每次请求带上最近多少条对话，默认 40；上下文总量超预算时还会自动从最早丢弃" +
                        "（预算两档：宽松固定 24000 字符，自定义在「对话预算」里调）。",
                    enabled = hasKey
                )
            }
    )
}

/** 「接入能力」行：值＝匹配到的服务商名，展开层＝适配明细＋官方文档/价格入口。 */
@Composable
private fun capabilityRow(
    fallbackLabel: String,
    summary: () -> String?,
    enabled: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    body: @Composable () -> Unit
) {
    val name = summary()
    WhaleLedgerRow(
        label = if (name.isNullOrBlank()) fallbackLabel else "${name} 适配",
        value = if (name.isNullOrBlank()) "未匹配 · 按 OpenAI 兼容处理" else "思考 · 温度 · 返回格式",
        valueMono = false,
        valueDim = name.isNullOrBlank(),
        enabled = enabled,
        actionText = if (open) "收起" else "展开",
        expanded = open,
        onToggle = onToggle,
        content = { body() }
    )
}
