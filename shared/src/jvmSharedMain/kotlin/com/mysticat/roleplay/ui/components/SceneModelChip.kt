package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ModelScene
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.effectiveSceneBaseUrl
import com.mysticat.roleplay.data.effectiveSceneModel
import com.mysticat.roleplay.data.effectiveSceneThinking
import com.mysticat.roleplay.data.providerPickerUrls
import com.mysticat.roleplay.data.sceneGlobalBaseUrl
import com.mysticat.roleplay.data.sceneGlobalModel
import com.mysticat.roleplay.data.sceneGlobalThinking
import com.mysticat.roleplay.data.sceneOverride
import com.mysticat.roleplay.data.sceneOverrideBaseUrl
import com.mysticat.roleplay.data.sceneOverrideThinking
import com.mysticat.roleplay.data.withSceneProvider
import com.mysticat.roleplay.data.withSceneThinking
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.screens.ThinkingSlider
import com.mysticat.roleplay.ui.tokens.WhaleShapes

/**
 * 场景级"供应商＋模型"胶囊（238 落地；49 轮按用户 2026-10-10 反馈升级）。
 *
 * 三层模型架构里的一枚读／写点：全局默认 → 场景级（本胶囊写 `AiSettings.scene*`）→ 会话级（仅聊天）。
 *
 * 49 轮的两条升级（用户原话「切换模型最好也能改供应商，并且也支持本次使用/全局默认的修改」）：
 * - **能改供应商**：菜单按供应商**分组**列模型（"能用的家"＝已配 Key 的预设家 ＋ 自定义家 ＋ 当前生效家，
 *   见 `AiSettings.providerPickerUrls`）——点某家的某个模型＝把"这家＋这个模型"一对写进去，
 *   所以不存在"只换家、模型名还是别家的"这种自相矛盾的中间态；Key 走一家一份自动跟随。
 * - **本次使用 / 全局默认**两档（与聊天页「模型与思考强度」的「本会话/全局默认」同构）：
 *   「本次使用」只改这个场合的覆盖（灵感创作 / 生图），「全局默认」直接改设置页那一份——不必再绕去设置页。
 *
 * 两条沿用 238 的口径：
 * - **切换当场落盘**：不掺进各页的"保存/未保存"表单状态；写的是磁盘上的设置 + 相关字段，
 *   页面里未保存的表单改动不受影响、也不会被冲掉（`collect()` 以存档为底）。
 * - **选中态＝被覆盖过**：胶囊高亮表示这一场景设了覆盖；跟随全局时是素色，菜单里"跟随全局默认"项可清。
 *
 * 思考强度（2026-10-12 新增，灵感创作场景专属）：菜单根页多一行「思考强度」（生图没有这一项），
 * 点进二级页是与设置页/聊天面板**同源的拉杆**；作用范围同样分「本次使用/全局默认」——
 * 本次档写 `AiSettings.sceneCreateThinking`（场景覆盖），全局档写设置页「创作」那份；
 * 拉杆松手才提交（`onValueChangeFinished`），与设置页逐次落盘的口径一致。
 */
@Composable
fun SceneModelChip(scene: ModelScene, modifier: Modifier = Modifier) {
    // 只读一次设置、本地维护：切完重新读回（saveSettings 先更新内存缓存，立刻能拿到新值）
    var settings by remember { mutableStateOf(Repository.loadSettings()) }
    var open by remember { mutableStateOf(false) }
    // false＝本次使用（默认，写场景覆盖）；true＝全局默认（写设置页那份）
    var globalScope by remember { mutableStateOf(false) }
    // 0＝模型列表页；1＝思考强度页（仅创作场景可达）
    var page by remember { mutableStateOf(0) }
    val overrideModel = settings.sceneOverride(scene)
    val overrideUrl = settings.sceneOverrideBaseUrl(scene)
    val overrideThinking = settings.sceneOverrideThinking(scene)
    // 生效组合：本次档看"覆盖优先"的生效值；全局档看设置页那份（与聊天面板"二级列表的勾＝生效值"同一口径）
    val effUrl = settings.effectiveSceneBaseUrl(scene)
    val effModel = settings.effectiveSceneModel(scene)
    val effThinking = settings.effectiveSceneThinking(scene)
    val globalUrl = settings.sceneGlobalBaseUrl(scene)
    val globalModel = settings.sceneGlobalModel(scene)
    val globalThinking = settings.sceneGlobalThinking(scene)
    // 能用的家：预设家过滤"有 Key 的"＋自定义家＋当前生效家兜底（免得清单为空）
    val providerUrls = remember(settings, scene) {
        val presets = if (scene == ModelScene.IMAGE) ModelCatalog.imageBaseUrlPresets()
        else ModelCatalog.baseUrlPresets()
        val customUrls = settings.customProviders.mapNotNull { cp ->
            (if (scene == ModelScene.IMAGE) cp.imageBaseUrl else cp.chatBaseUrl)
                .trim().ifBlank { null }
        }
        settings.providerPickerUrls(presets, customUrls, effUrl)
    }
    // 思考强度（仅创作场景）：档位表跟着**生效模型**走（不同模型可用档不同）；
    // 本次档看覆盖优先的生效值，全局档看设置页那份（与模型的勾同一口径）
    val thinkUrl = if (globalScope) globalUrl else effUrl
    val thinkModel = if (globalScope) globalModel else effModel
    val thinkLevels = remember(thinkUrl, thinkModel) {
        ProviderProfiles.thinkingLevels(thinkUrl, thinkModel)
    }
    val curThinking = if (globalScope) globalThinking else effThinking
    // 行上显示的档位名：空档位表＝未适配；空值＝模型默认（与聊天面板根页同一口径）
    val thinkLabel = thinkLevels.firstOrNull { it.mode == curThinking }?.label
        ?: if (thinkLevels.isEmpty()) "未适配" else if (curThinking.isBlank()) "模型默认" else curThinking
    val pick: (String?, String?) -> Unit = { url, model ->
        open = false
        val next = if (!globalScope) {
            var s = settings.withSceneProvider(scene, url, model)
            // 清覆盖（"跟随全局默认"项）时思考强度覆盖一起清——"回到跟随全局"是一件事
            if (url == null && model == null) s = s.withSceneThinking(scene, null)
            s
        } else if (url != null && model != null) {
            when (scene) {
                ModelScene.CREATE -> settings.copy(creationBaseUrl = url, creationModel = model)
                ModelScene.IMAGE -> settings.copy(imageBaseUrl = url, imageModel = model)
            }
        } else settings
        if (next != settings) {
            Repository.saveSettings(next)
            settings = Repository.loadSettings()
        }
    }
    /** 思考强度拉杆提交：本次档写场景覆盖、全局档写设置页那份（与模型同口径：当场落盘） */
    fun commitThinking(mode: String) {
        val next = if (globalScope) settings.copy(creationThinking = mode)
        else settings.withSceneThinking(scene, mode)
        if (next != settings) {
            Repository.saveSettings(next)
            settings = Repository.loadSettings()
        }
    }
    /** 某一家的可选项：内置预设 ＋ 当前生效值/全局值（手填的模型不在预设清单里，也要能看到、能选中它） */
    fun modelsFor(url: String): List<String> {
        val base = if (scene == ModelScene.IMAGE) ModelCatalog.imagePresets(url)
        else ModelCatalog.creationPresets(url)
        val extra = buildList {
            if (effUrl == url) add(effModel)
            if (globalUrl == url) add(globalModel)
        }
        return (base + extra).map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    Box(modifier) {
        WhaleChip(
            selected = overrideModel != null || overrideUrl != null || overrideThinking != null,
            // 每次打开都回到「本次使用」档与模型列表页（49 轮/本轮）：这一档改的是本场景、影响面小；
            // 若保留上次的「全局默认」档，用户下次可能没注意就改了全局默认（本菜单没有副标题提示档位）。
            onClick = { open = true; globalScope = false; page = 0 },
            label = {
                Text(
                    effModel.ifBlank { "未配模型" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp)
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "切换模型",
                    modifier = Modifier.size(16.dp)
                )
            }
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 340.dp)
        ) {
            if (page == 1) {
                // 二级页·思考强度（仅创作场景可达）：返回头 ＋ 与设置页同源的拉杆——档位名标在刻度上，
                // 空值/未知值吸附到"模型默认"那档（ThinkingSlider 内部兜底）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { page = 0 }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("思考强度", style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (thinkLevels.isEmpty()) {
                    Text(
                        "${ProviderProfiles.nameFor(thinkUrl)} 未适配思考参数，保持模型默认行为。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                    )
                } else {
                    ThinkingSlider(
                        value = curThinking,
                        levels = thinkLevels,
                        onValue = { commitThinking(it) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                // 根页：范围格 ＋ 思考强度入口（仅创作场景）＋ 分组模型列表 ＋ 清覆盖
                // 作用范围两格（49 轮）：与聊天面板的范围格同构、小一号
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScopeCell("本次使用", selected = !globalScope, onClick = { globalScope = false }, modifier = Modifier.weight(1f))
                    ScopeCell("全局默认", selected = globalScope, onClick = { globalScope = true }, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
                // 思考强度入口（仅创作；生图没有这一项）：进二级页调档，作用范围跟着上面的范围格
                if (scene == ModelScene.CREATE) {
                    DropdownMenuItem(
                        text = { Text("思考强度") },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    thinkLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = { page = 1 }
                    )
                    HorizontalDivider()
                }
                // 分组列表限高自滚（49 轮实测）：配 5 家的机器上 19 行会把菜单撑到整屏（2290/2400px），
                // 范围格固定在上、清覆盖固定在下，只有中间这批家/模型滚
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    providerUrls.forEach { url ->
                        // 分组标题：供应商名（选中状态在具体模型行上，标题只管"这批是那家的"）
                        Text(
                            ProviderProfiles.nameFor(url),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp)
                        )
                        modelsFor(url).forEach { m ->
                            val sel = if (globalScope) (globalUrl == url && globalModel == m)
                            else (effUrl == url && effModel == m)
                            DropdownMenuItem(
                                text = {
                                    Text(m, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                onClick = { pick(url, m) },
                                trailingIcon = { if (sel) CheckMark() }
                            )
                        }
                    }
                }
                // 清覆盖（只在"本次使用"档、且确实覆盖过时出现）：回到跟随全局默认（思考强度覆盖一起清）
                if (!globalScope && (overrideModel != null || overrideUrl != null || overrideThinking != null)) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (globalModel.isBlank()) "跟随全局默认" else "跟随全局默认（$globalModel）") },
                        onClick = { pick(null, null) }
                    )
                }
            }
        }
    }
}

/** 范围格（49 轮）：一对等宽可点块——与聊天面板 `ScopeSwitch` 同款、字号小一号（菜单里放不下大格子） */
@Composable
private fun ScopeCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = WhaleShapes.r2,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )
    }
}

@Composable
private fun CheckMark() {
    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
}
