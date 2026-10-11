package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import com.mysticat.roleplay.data.providerPickerUrls
import com.mysticat.roleplay.data.sceneGlobalBaseUrl
import com.mysticat.roleplay.data.sceneGlobalModel
import com.mysticat.roleplay.data.sceneOverride
import com.mysticat.roleplay.data.sceneOverrideBaseUrl
import com.mysticat.roleplay.data.withSceneProvider
import com.mysticat.roleplay.ui.WhaleChip
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
 */
@Composable
fun SceneModelChip(scene: ModelScene, modifier: Modifier = Modifier) {
    // 只读一次设置、本地维护：切完重新读回（saveSettings 先更新内存缓存，立刻能拿到新值）
    var settings by remember { mutableStateOf(Repository.loadSettings()) }
    var open by remember { mutableStateOf(false) }
    // false＝本次使用（默认，写场景覆盖）；true＝全局默认（写设置页那份）
    var globalScope by remember { mutableStateOf(false) }
    val overrideModel = settings.sceneOverride(scene)
    val overrideUrl = settings.sceneOverrideBaseUrl(scene)
    // 生效组合：本次档看"覆盖优先"的生效值；全局档看设置页那份（与聊天面板"二级列表的勾＝生效值"同一口径）
    val effUrl = settings.effectiveSceneBaseUrl(scene)
    val effModel = settings.effectiveSceneModel(scene)
    val globalUrl = settings.sceneGlobalBaseUrl(scene)
    val globalModel = settings.sceneGlobalModel(scene)
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
    val pick: (String?, String?) -> Unit = { url, model ->
        open = false
        val next = if (!globalScope) {
            settings.withSceneProvider(scene, url, model)
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
            selected = overrideModel != null || overrideUrl != null,
            // 每次打开都回到「本次使用」档（49 轮）：这一档改的是本场景、影响面小；
            // 若保留上次的「全局默认」档，用户下次可能没注意就改了全局默认（本菜单没有副标题提示档位）。
            onClick = { open = true; globalScope = false },
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
            // 作用范围两格（49 轮，用户原话「本次使用/全局默认」）：与聊天面板的范围格同构、小一号
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
            // 清覆盖（只在"本次使用"档、且确实覆盖过时出现）：回到跟随全局默认
            if (!globalScope && (overrideModel != null || overrideUrl != null)) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (globalModel.isBlank()) "跟随全局默认" else "跟随全局默认（$globalModel）") },
                    onClick = { pick(null, null) }
                )
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
