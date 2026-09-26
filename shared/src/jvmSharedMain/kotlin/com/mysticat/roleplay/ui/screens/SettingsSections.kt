package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Security
import kotlin.math.roundToInt

/** 设置页 · API 配置分段：供应商胶囊 + Key 输入（管理所有供应商的 Key）。 */
internal fun LazyListScope.apiSettingsSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit,
    onAddCustom: () -> Unit
) {
    item { SectionTitle("供应商与 Key（一家一份，加密存在本机）") }
    item {
        ProviderChips(
            current = vm.apiKeyUrl,
            presets = ModelCatalog.allProviderPresets(),
            custom = vm.customProviders,
            // API 配置要能管理**所有**供应商（对话能力、生图能力，或两者都有）
            anyCapability = true,
            onPick = vm::selectApiKeyProvider,
            onEdit = onEdit,
            onAddCustom = onAddCustom
        )
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ⚠️ 用 key(...) 让输入框在切换供应商时**整体重建**：
            // 否则 Compose 会在 value 被程序改掉后回调一次旧文本，
            // 把上一家的 Key 写进新选中的供应商名下（查起来极隐蔽）
            key(vm.apiKeyUrl) {
                SettingsTextField(
                    value = vm.apiKey,
                    onValue = vm::updateApiKey,
                    label = "${vm.providerLabel(vm.apiKeyUrl)} 的 API Key",
                    secret = true
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // 文案按实况说话：密钥库可用才说"加密存储"，
                    // 明文回退（桌面 DPAPI 不可用 / Android Keystore 异常）不得谎称
                    when {
                        vm.hasSavedKeyForCurrentApiProvider && Security.encryptedStorageAvailable ->
                            "已保存（加密存储，切回来仍在）"
                        vm.hasSavedKeyForCurrentApiProvider -> "已保存（本机明文存储，功能不受影响）"
                        else -> "尚未保存 Key —— 这家在下面三个模型页里会置灰"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (vm.hasSavedKeyForCurrentApiProvider) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (vm.hasSavedKeyForCurrentApiProvider || vm.apiKey.isNotBlank()) {
                    TextButton(onClick = vm::clearKeyForCurrentProvider) {
                        Text("清除这家的 Key")
                    }
                }
            }
            Text(
                "点上面的胶囊切换要填写的供应商；" +
                    if (Security.encryptedStorageAvailable) {
                        "Key 用 ${Security.keyProviderName ?: "本机安全模块"} 加密后保存在本机，不进备份文件。"
                    } else {
                        "本机安全模块暂不可用，Key 将以明文保存在本机（不进备份文件），功能不受影响。"
                    } +
                    "对话 / 创作 / 生图三个页面各自选供应商，选到哪家就用哪家的 Key。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 设置页 · 对话模型分段：供应商胶囊 + 模型与参数 + 接入能力卡片。 */
internal fun LazyListScope.chatModelSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    // ────────────────────────── 对话模型 ──────────────────────────
    // 这家没存 Key → 模型与参数全部置灰（2026-09-15 用户要求）
    val hasKey = vm.hasKeyFor(vm.chatBaseUrl)
    item { SectionTitle("对话模型与参数") }
    item {
        ProviderChips(
            current = vm.chatBaseUrl,
            presets = ModelCatalog.baseUrlPresets(),
            custom = vm.customProviders,
            forChat = true,
            onPick = vm::switchChatProvider,
            onEdit = onEdit
        )
    }
    if (!vm.hasKeyFor(vm.chatBaseUrl)) {
        item { NoKeyHint(vm.providerLabel(vm.chatBaseUrl)) }
    }
    item {
        ModelDropdown(
            value = vm.chatModel,
            onValue = { vm.chatModel = it },
            presets = ModelCatalog.chatPresets(vm.chatBaseUrl),
            label = "对话模型",
            test = vm.chatTest,
            enabled = hasKey,
            onTest = vm::testChatModel
        )
    }
    item {
        Column {
            val profile = ProviderProfiles.resolve(vm.chatBaseUrl, vm.customProviders)
            val levels = ProviderProfiles.thinkingLevels(vm.chatBaseUrl, vm.chatModel)
            if (levels.isEmpty()) {
                Text("对话思考强度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    profile?.let { "${it.name} 未适配思考参数，保持模型默认行为（不发送任何思考字段）。" }
                        ?: "当前 Base URL 未匹配到已知服务商，保持模型默认行为。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ThinkingChips("对话思考强度", vm.chatThinking, levels, onValue = { vm.chatThinking = it }, enabled = hasKey)
                Spacer(Modifier.height(4.dp))
                Text(
                    "实际参数：${profile?.thinking?.paramLabel ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    item {
        Column {
            val tempSupported = ProviderProfiles
                .resolve(vm.chatBaseUrl, vm.customProviders)
                ?.temperatureSupported ?: true
            Text(
                "温度 Temperature：${String.format("%.2f", vm.temperature)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (tempSupported) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Slider(
                value = vm.temperature.toFloat(),
                // 保留两位小数：滑杆是 Float，直接 toDouble() 会产生
                // 0.8500000238418579 这类长尾值，智谱会报「temperature 参数非法」
                // （2026-09-15 用户实测）；这里从源头存成干净值
                onValueChange = { vm.temperature = (it * 100).roundToInt() / 100.0 },
                valueRange = 0f..2f,
                // Kimi 会返回 invalid temperature：该供应商下不支持调温度（App 也不会发该参数）
                enabled = tempSupported && hasKey
            )
            if (!tempSupported) {
                Text(
                    "当前供应商不支持 temperature 参数（如 Kimi 会报 invalid temperature），" +
                        "App 不会发送它，此项已停用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val configured = vm.maxTokensText.toIntOrNull() ?: 2048
            val effective = ProviderProfiles.effectiveMaxTokens(
                vm.chatBaseUrl, vm.chatThinking, vm.chatModel, configured, vm.customProviders
            )
            EditableDropdown(
                value = vm.maxTokensText,
            enabled = hasKey,
                onValue = { vm.maxTokensText = it },
                presets = listOf("256", "512", "1024", "2048", "4096", "8192", "16384"),
                label = "单条回复长度上限（仅对话）",
                helper = "默认 2048。思考 token 也占这份额度，所以「设置值 < 8192 且当前会思考」时实际按 8192 发送" +
                    "（否则会出现正文为空）；你的设置值本身会保留，切回不思考的模型/档位后自动恢复。\n" +
                    if (effective != configured) "当前实际发送：$effective（你的设置：$configured）"
                    else "当前实际发送：$configured"
            )
            EditableDropdown(
                value = vm.historyLimitText,
            enabled = hasKey,
                onValue = { vm.historyLimitText = it },
                presets = listOf("10", "20", "40", "60", "80", "100"),
                label = "携带历史条数（仅对话）",
                helper = "聊天时每次请求带上最近多少条对话，默认 40；上下文总量超 24000 字符时还会自动从最早丢弃。"
            )
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = vm.extraSystemPrompt,
            enabled = hasKey,
                onValueChange = { vm.extraSystemPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("全局补充系统提示词（可选）") },
                minLines = 2,
                placeholder = { Text("例如：所有角色都使用中文回复；允许适度擦边但不含露骨描写") }
            )
            Text(
                "这段内容会追加在每次对话系统提示词的最后（标记为【补充要求】），对所有角色、所有会话生效，" +
                    "适合写通用的说话风格与边界要求；想针对某个角色单独要求，写进角色卡的人设里更合适。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // 接入能力卡片：这一页所用供应商的适配情况 + 官方「使用文档」「价格与模型页」入口
    item { ProviderCapabilityCard(vm.chatBaseUrl, vm.chatModel, "", "", vm.customProviders) }
}

/** 设置页 · 创作模型分段：供应商胶囊 + 模型与参数 + 接入能力卡片。 */
internal fun LazyListScope.creationModelSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    // ────────────────────────── 创作模型 ──────────────────────────
    // 同上：创作供应商没存 Key 就置灰
    val hasKey = vm.hasKeyFor(vm.creationBaseUrl)
    item { SectionTitle("创作模型（AI 生成角色卡、起草提示词用）") }
    item {
        ProviderChips(
            current = vm.creationBaseUrl,
            presets = ModelCatalog.baseUrlPresets(),
            custom = vm.customProviders,
            forChat = true,
            onPick = vm::switchCreationProvider,
            onEdit = onEdit
        )
    }
    if (!hasKey) item { NoKeyHint(vm.providerLabel(vm.creationBaseUrl)) }
    item {
        ModelDropdown(
            value = vm.creationModel,
            onValue = { vm.creationModel = it },
            presets = ModelCatalog.creationPresets(vm.creationBaseUrl),
            label = "创作模型",
            test = vm.creationTest,
            enabled = hasKey,
            onTest = vm::testCreationModel
        )
    }
    item {
        Column {
            val profile = ProviderProfiles.resolve(vm.creationBaseUrl, vm.customProviders)
            // 创作档位按「创作模型」算：同一供应商下对话/创作模型可能不同（如 glm-5.3 与 GLM-4-Flash）
            val creationLevels = ProviderProfiles.thinkingLevels(vm.creationBaseUrl, vm.creationModel)
            if (creationLevels.isEmpty()) {
                Text("创作思考强度", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    profile?.let { "${it.name} 未适配思考参数，保持模型默认行为。" }
                        ?: "当前 Base URL 未匹配到已知服务商，保持模型默认行为。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ThinkingChips(
                    "创作思考强度", vm.creationThinking, creationLevels,
                    onValue = { vm.creationThinking = it }, enabled = hasKey
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "实际参数：${profile?.thinking?.paramLabel ?: "—"}（与对话思考强度是两组独立设置）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    item {
        Text(
            "创作模型只用于「AI 生成角色卡」与「起草生图提示词」；生成角色卡时输出上限固定为 8192，" +
                "且起草/扩写会强制关闭思考以避免等待过久。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // 接入能力卡片：这一页所用供应商的适配情况 + 官方「使用文档」「价格与模型页」入口
    item { ProviderCapabilityCard(vm.creationBaseUrl, vm.creationModel, "", "", vm.customProviders) }
}

/** 设置页 · 生图模型分段：生图供应商胶囊 + 模型与参数 + 接入能力卡片。 */
internal fun LazyListScope.imageModelSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    // ────────────────────────── 生图模型 ──────────────────────────
    val hasKey = vm.hasKeyFor(vm.imageBaseUrl)
    item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AI 生图服务", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "用于生成角色头像与聊天背景图",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = vm.imageEnabled, onCheckedChange = { vm.imageEnabled = it })
                }
            }
        }
    }
    if (vm.imageEnabled) {
        item { SectionTitle("生图供应商与模型") }
        item {
            ProviderChips(
                current = vm.imageBaseUrl,
                presets = ModelCatalog.imageBaseUrlPresets(),
                custom = vm.customProviders,
                forChat = false,
                onPick = vm::switchImageProvider,
                onEdit = onEdit
            )
        }
        if (!hasKey) {
            item { NoKeyHint(vm.providerLabel(vm.imageBaseUrl)) }
        }
        item {
            ModelDropdown(
                value = vm.imageModel,
                onValue = { vm.imageModel = it },
                presets = ModelCatalog.imagePresets(vm.imageBaseUrl),
                label = "生图模型",
                test = vm.imageTest,
                enabled = hasKey,
                onTest = vm::requestImageTest
            )
        }
        item {
            Text("返回格式", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            val imageProfile = ProviderProfiles.resolve(vm.imageBaseUrl, vm.customProviders)
            val forced = imageProfile?.imageFormatForced
            // MiniMax 的枚举是 base64/url（没有 b64_json），标签按其真实取值显示
            val isMiniMax = imageProfile?.imageProtocol == ProviderProfiles.ImageProtocol.MINIMAX
            val b64Label = if (isMiniMax) "base64" else "b64_json"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhaleChip(
                    selected = (forced ?: vm.imageFormat) == "b64_json",
                    enabled = forced == null || forced == "b64_json",
                    onClick = { vm.imageFormat = "b64_json" },
                    label = { Text(b64Label) }
                )
                WhaleChip(
                    selected = (forced ?: vm.imageFormat) == "url",
                    enabled = forced == null || forced == "url",
                    onClick = { vm.imageFormat = "url" },
                    label = { Text("url") }
                )
            }
            Text(
                if (forced != null) {
                    "${imageProfile?.name} 只支持 $forced，此项已锁定；你的偏好设置不会被改写，换到其他服务商会自动恢复。"
                } else {
                    "$b64Label：直接返回图片数据，本 App 保存到本地，推荐；url：返回图片链接，" +
                        "需服务商允许外网访问。" +
                        (if (isMiniMax) "（MiniMax 的 base64 即 b64_json）" else "当前服务商两者都可选。")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (forced != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    // 接入能力卡片：这一页所用供应商的适配情况 + 官方「使用文档」「价格与模型页」入口
    item { ProviderCapabilityCard("", "", vm.imageBaseUrl, vm.imageModel, vm.customProviders) }
}
