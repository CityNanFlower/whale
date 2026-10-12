package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.ui.MixPresetBar
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.uiInlineMarkdown
import com.mysticat.roleplay.ui.voiceDisplayName
import com.mysticat.roleplay.ui.components.WhaleFieldRow
import com.mysticat.roleplay.ui.components.WhaleFieldSelect
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.components.WhaleToggleRow
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import kotlin.math.roundToInt

/** 角色专属音色（E 批次）：开关 + 全部音色配置。手机端编辑页仍是 LazyColumn（本适配器收进一个 item），桌面两段式右栏直接用 [CharacterVoiceSectionBody]——两端共用这一份字段区。 */
internal fun LazyListScope.characterVoiceSection(vm: CharacterEditorViewModel) {
    item { CharacterVoiceSectionBody(vm) }
}

/**
 * 音色配置本体（Column 版，v10 行表式）：手机端＝编辑页 LazyColumn 的一格，桌面端＝两段式右栏的一节。
 *
 * 骨架＝分组卡「专属音色」：开关行 → 声音类型（≥2 档折成开关行）→ 三个下拉字段 → 语速/音高滑条行
 * → 混音源（火山且类型为混合时才出现）。试听并进语速行；提示与导出说明落卡尾。
 * 业务口径（类型从数据推、混音只认火山、试听走朗读链路）与设置页共用，一处没改。
 */
@Composable
internal fun CharacterVoiceSectionBody(vm: CharacterEditorViewModel) {
    val isVolc = ModelCatalog.speechProviderKeyword(vm.voiceBaseUrl) == "volcengine" ||
        vm.voiceProvider == "volcengine"
    // 三个取值都从已有数据推出来（`CharacterVoices.kindOf`），不新增存储字段——
    // 卡与设置的往返、导出导入的保真都不用跟着改口径。
    val voiceKind = CharacterVoices.kindOf(
        vm.voiceVoice, vm.voiceModel, vm.voiceMixEnabled, vm.voiceMixSpeakers,
        mixSupported = isVolc,
        // 本机复刻音色库要进判定：录音复刻出来的 id 没有 `S_` 前缀，
        // 不查库就会被当成普通 2.0 音色 —— 档位配错，合成必 mismatch
        cloneIds = vm.cloneIds()
    )
    val sliderWidth = if (isDesktopLayout) 150.dp else 110.dp
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WhaleFieldGroup(label = "专属音色") {
            // ── 开关控制展开（用户 2026-09-21）────────────────────────
            WhaleFieldRow(
                title = "角色专属音色",
                desc = "开＝长按回复朗读与顶栏喇叭都用这里的音色；关＝跟着设置页「语音朗读」走",
                trailing = {
                    Switch(
                        checked = vm.voiceEnabled,
                        onCheckedChange = { vm.updateVoiceEnabled(it) }
                    )
                }
            )
            if (vm.voiceEnabled) {
                // 声音类型：单一 / 混合 / 复刻——与设置页**同一排档位、同一套判据**。
                // 后两个只在火山出现：混音是火山专属协议、复刻是火山的 ICL 资源。
                val kindOptions = buildList {
                    add(CharacterVoices.VoiceKind.SINGLE to "单一音色")
                    if (isVolc) add(CharacterVoices.VoiceKind.MIX to "混合音色")
                    if (isVolc) add(CharacterVoices.VoiceKind.CLONE to "复刻音色")
                }
                WhaleToggleRow(
                    title = "声音类型",
                    desc = when (voiceKind) {
                        CharacterVoices.VoiceKind.MIX ->
                            "把 2~3 个源音色按比例混成一把新声音（火山专属）"
                        CharacterVoices.VoiceKind.CLONE ->
                            "用你自己复刻出来的声音；`S_` 开头的 id 也能粘进「音色」"
                        else -> "在这家的音色库里挑一个，也能直接手填 id"
                    },
                    options = kindOptions.map { it.second },
                    isSelected = { label -> kindOptions.firstOrNull { it.second == label }?.first == voiceKind },
                    onToggle = { label ->
                        kindOptions.firstOrNull { it.second == label }?.let { (k, _) -> vm.pickVoiceKind(k) }
                    }
                )
                // 三栏都与设置页「语音朗读」同一套组件、同一套预设（下拉选与手填都行），
                // 区别只在这里不重复渲染"凭据"——那是全局的事，一页只该填一次
                WhaleFieldSelect(
                    label = "语音供应商",
                    value = vm.voiceBaseUrl,
                    onValue = { vm.updateVoiceBaseUrl(it) },
                    presets = ProviderProfiles.speechProviderUrls(),
                    presetLabel = { ProviderProfiles.nameFor(it) }
                )
                WhaleFieldSelect(
                    label = "语音合成模型",
                    value = vm.voiceModel,
                    onValue = { vm.updateVoiceModel(it) },
                    presets = ModelCatalog.speechPresets(vm.voiceBaseUrl),
                    presetLabel = { ModelCatalog.speechModelLabel(vm.voiceBaseUrl, it) }
                )
                WhaleFieldSelect(
                    // 混合音色下 speaker 固定 `custom_mix_bigtts`，这一栏不参与合成 ⇒ 换个标签说清；
                    // 复刻音色正相反：这一栏就是它的家（粘一个 `S_` 开头的 id 进来即可）
                    label = if (voiceKind == CharacterVoices.VoiceKind.MIX) "音色（混合音色下不使用）" else "音色",
                    value = vm.voiceVoice,
                    onValue = { vm.updateVoiceVoice(it) },
                    // 复刻音色那一档把「我的复刻音色」并进预设清单：自己命名的音色
                    // id 前缀毫无特征，光看 id 认不出来，下拉里要显示本机名字
                    //（本页只读不建：新建录音在设置页「语音服务 → 复刻音色」里做）
                    presets = if (isVolc) {
                        (ModelCatalog.speechVoices(vm.voiceBaseUrl, vm.voiceModel) + vm.cloneIds()).distinct()
                    } else {
                        ModelCatalog.speechVoices(vm.voiceBaseUrl, vm.voiceModel)
                    },
                    presetLabel = { v ->
                        vm.cloneVoiceOf(v)?.name
                            ?: ModelCatalog.speechVoiceLabel(vm.voiceBaseUrl, v)
                    },
                    enabled = voiceKind != CharacterVoices.VoiceKind.MIX,
                    hint = if (isVolc && voiceKind == CharacterVoices.VoiceKind.CLONE) {
                        if (vm.cloneVoices.isEmpty()) {
                            "还没录过自己的声音：到「设置 → 语音服务 → 语音朗读」选「复刻音色」，" +
                                "那里可以**在 App 里录一段**直接复刻（火山），复刻好的声音会出现在本栏下拉里。"
                        } else {
                            "本栏下拉里有你复刻好的声音（${vm.cloneVoices.joinToString("、") { it.name }}）；" +
                                "要再录一个，去「设置 → 语音服务 → 语音朗读 → 复刻音色」。"
                        }
                    } else null
                )
                WhaleFieldRow(
                    title = "语速",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = vm.voiceSpeed,
                                onValueChange = { vm.voiceSpeed = (it * 100).toInt() / 100f },
                                valueRange = 0.5f..2f,
                                modifier = Modifier.width(sliderWidth)
                            )
                            Spacer(Modifier.width(WhaleSpacing.sm))
                            Text(
                                String.format("%.2f×", vm.voiceSpeed),
                                style = LocalWhaleType.current.mono(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(WhaleSpacing.sm))
                            TextButton(
                                onClick = { vm.previewVoice() },
                                enabled = !vm.voicePreviewing
                            ) { Text(if (vm.voicePreviewing) "合成…" else "试听") }
                        }
                    }
                )
                WhaleFieldRow(
                    title = "音高",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = vm.voicePitch,
                                onValueChange = { vm.voicePitch = (it * 100).toInt() / 100f },
                                valueRange = 0.5f..2f,
                                modifier = Modifier.width(sliderWidth)
                            )
                            Spacer(Modifier.width(WhaleSpacing.sm))
                            Text(
                                String.format("%.2f", vm.voicePitch),
                                style = LocalWhaleType.current.mono(MaterialTheme.typography.bodySmall),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    divider = isVolc && voiceKind == CharacterVoices.VoiceKind.MIX
                )
                // ── 混合音色（火山专属，2026-09-22 用户反馈；由「声音类型」开关行选入）──────
                // 只在这张卡指向火山时出现：混音是火山的 `custom_mix_bigtts` 协议，别家没有这个概念。
                // 开关的职责归上面那排档位（类型从数据推、不落库），这里只管源与权重。
                if (isVolc && voiceKind == CharacterVoices.VoiceKind.MIX) {
                    val mixList = vm.voiceMixSpeakers
                    val mixTotal = mixList.sumOf { it.factor.toDouble() }.takeIf { it > 0.0 } ?: 1.0
                    mixList.forEachIndexed { i, s ->
                        WhaleFieldSelect(
                            label = "音色 ${i + 1}",
                            value = s.voice,
                            onValue = { v ->
                                vm.voiceMixSpeakers = mixList.toMutableList()
                                    .also { it[i] = s.copy(voice = v) }
                            },
                            // 可混的源**也包括本机的复刻音色**（官方口径：1.0 音色与复刻音色都能混）
                            presets = (ModelCatalog.volcMixSourceVoices() + vm.cloneIds()).distinct(),
                            presetLabel = { voiceDisplayName(it, vm::cloneVoiceOf) }
                        )
                        WhaleFieldRow(
                            title = "权重 ${i + 1}",
                            // 显示**实际占比**（已归一化），用户不必自己把权重凑成 1
                            desc = "混好后实际占比 ${(s.factor / mixTotal * 100).roundToInt()}%",
                            trailing = {
                                Slider(
                                    value = s.factor,
                                    onValueChange = { f ->
                                        vm.voiceMixSpeakers = mixList.toMutableList()
                                            .also { it[i] = s.copy(factor = (f * 20).roundToInt() / 20f) }
                                    },
                                    valueRange = 0.05f..1f,
                                    modifier = Modifier.width(sliderWidth)
                                )
                            }
                        )
                    }
                    WhaleFieldRow(
                        title = "源音色数量",
                        desc = "最多 3 个，按权重混成一把声音",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    enabled = mixList.size < 3,
                                    onClick = {
                                        vm.voiceMixSpeakers = mixList + MixSpeaker(
                                            ModelCatalog.volcMixSourceVoices().getOrElse(mixList.size) { "" },
                                            0.3f
                                        )
                                    }
                                ) { Text("＋ 加一个") }
                                if (mixList.size > 2) {
                                    TextButton(onClick = { vm.voiceMixSpeakers = mixList.dropLast(1) }) {
                                        Text("－ 去掉")
                                    }
                                }
                            }
                        },
                        divider = false
                    )
                }
            }
        }
        if (vm.voiceEnabled) {
            // 预设（套用 / 存 / 删）：与设置页**同一份实现**（`MixPresetBar`）
            // —— 用户 2026-09-22 的反馈正是"这里也要能用预设、存预设"；它是一排胶囊＋按钮，
            // 不是"名—值行"，所以落在卡外当工具条
            if (isVolc && voiceKind == CharacterVoices.VoiceKind.MIX) {
                MixPresetBar(
                    presets = vm.mixPresets,
                    canSave = vm.voiceMixSpeakers.count { it.voice.isNotBlank() } >= 2,
                    enabled = true,
                    onApply = { vm.applyMixPreset(it) },
                    onDelete = { vm.deleteMixPreset(it) },
                    onSave = { vm.saveMixPreset() },
                    voiceLabel = { voiceDisplayName(it, vm::cloneVoiceOf) }
                )
            }
            vm.voiceNotice?.let { n ->
                Text(
                    "$n  ✕",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalWhalePalette.current.accentText,
                    modifier = Modifier.clickable { vm.clearVoiceNotice() }
                )
            }
            // 卡里的音色在本机能不能落地：**只提示，绝不改数据**（换台机器可能就配上了）
            vm.voiceStatus?.let { st ->
                // "混合音色生效中吗"统一问类型：它已经把"只有 1 个源""别家不支持混音"
                // 两种情况算成单一音色了，与 `CharacterVoices.effective` 同一份判断
                val mixOn = voiceKind == CharacterVoices.VoiceKind.MIX
                val notice = when {
                    st.baseUrl.isBlank() ->
                        "还没选供应商：点上面「语音供应商」那一栏选一家（本地部署的服务也可以直接把地址填进去），" +
                            "再挑模型与音色。"
                    // 混音源不够：这时走的是单音色那一栏，不说清楚用户会以为混音生效了
                    isVolc && vm.voiceMixEnabled && !mixOn ->
                        "混合音色至少要 2 个源音色（最多 3 个），现在会按上面「音色」那一栏合成。"
                    !st.homeFound ->
                        "这个合成地址本机没配过（常见于别人分享的卡）：朗读时按卡里的地址发，" +
                            "不通会自动回退系统语音。想用这家请在设置页「语音服务」里配上。"
                    !st.credentialOk ->
                        "这家还没有可用凭据，朗读时会回退系统语音——去设置页「语音服务 → 语音凭据」填一把即可。"
                    !st.voiceKnown && mixOn ->
                        "混音里有源音色不在火山的可混清单里（只有 1.0 系列音色能混），仍会按卡里的值试一次。"
                    !st.voiceKnown ->
                        "「${vm.voiceVoice}」在这家的预设音色里没有找到，仍会按卡里的值试一次" +
                            "（手填 / 复刻音色常见，能出声就没问题）。"
                    else -> null
                }
                if (notice != null) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalWhalePalette.current.dangerText
                    )
                }
            }
            vm.voicePreviewNotice?.let { n ->
                Text(
                    "$n  ✕",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalWhalePalette.current.dangerText,
                    modifier = Modifier.clickable { vm.clearVoicePreviewNotice() }
                )
            }
            Text(
                uiInlineMarkdown(
                    "试听与朗读走同一条链路（同一份解析、同一份缓存），所以试听能出声、聊天里就一定能出声。\n" +
                        "音色随角色卡导出 / 导入（`extensions.whale.voice`）：卡里写的供应商、模型、音色、语速音高" +
                        "以及混合音色的源与权重都原样带着，换台机器导入后按「家」自动对上。\n" +
                        "**这一页与设置页「语音朗读」互不影响**：这里改的是本卡专属音色，不会动全局那套，" +
                        "反过来改全局也不会改已经设过音色的卡（开着开关的卡一律用自己的）。\n" +
                        "只有**凭据**是全局的、一页填一次；合成按字符计费，同一句命中本地缓存不重复付费。"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
