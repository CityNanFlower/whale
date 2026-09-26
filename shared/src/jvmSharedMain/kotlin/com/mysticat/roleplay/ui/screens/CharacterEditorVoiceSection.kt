package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
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
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.ui.MixPresetBar
import com.mysticat.roleplay.ui.VoiceKindChips
import com.mysticat.roleplay.ui.uiInlineMarkdown
import com.mysticat.roleplay.ui.voiceDisplayName
import kotlin.math.roundToInt

/** 角色专属音色（E 批次）：开关 + 全部音色配置，作为编辑器 LazyColumn 的若干项。 */
internal fun LazyListScope.characterVoiceSection(vm: CharacterEditorViewModel) {
    // ── 角色专属音色（E 批次，用户 2026-09-21：开关控制展开）────────────────────
    item { Text("角色专属音色", style = MaterialTheme.typography.titleSmall) }
    item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("这个角色换一个声音", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "开启后，长按回复「朗读」与顶栏喇叭的自动朗读都用这里配的音色；" +
                        "关闭＝跟着设置页「语音朗读」那一套走。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = vm.voiceEnabled,
                onCheckedChange = { vm.updateVoiceEnabled(it) }
            )
        }
    }
    if (vm.voiceEnabled) {
        // 声音类型：单一 / 混合 / 复刻 —— 与设置页**同一排胶囊、同一套判据**
        // （`CharacterVoices.kindOf`；类型从数据推、不落库，所以卡往返与导出都不用改口径）。
        // 后两个只在火山出现：混音是火山专属协议、复刻是火山的 ICL 资源。
        val isVolc = ModelCatalog.speechProviderKeyword(vm.voiceBaseUrl) == "volcengine" ||
            vm.voiceProvider == "volcengine"
        val voiceKind = CharacterVoices.kindOf(
            vm.voiceVoice, vm.voiceModel, vm.voiceMixEnabled, vm.voiceMixSpeakers,
            mixSupported = isVolc,
            // 本机复刻音色库要进判定：录音复刻出来的 id 没有 `S_` 前缀，
            // 不查库就会被当成普通 2.0 音色 —— 档位配错，合成必 mismatch
            cloneIds = vm.cloneIds()
        )
        item {
            VoiceKindChips(
                kind = voiceKind,
                allowMix = isVolc,
                allowClone = isVolc,
                enabled = true,
                onPick = { vm.pickVoiceKind(it) }
            )
        }
        // 三栏都与设置页「语音朗读」同一套组件、同一套预设（胶囊点选与手填都行），
        // 区别只在这里不重复渲染"凭据"——那是全局的事，一页只该填一次
        item {
            InputWithPresets(
                value = vm.voiceBaseUrl,
                onValue = { vm.updateVoiceBaseUrl(it) },
                label = "语音供应商",
                presets = ProviderProfiles.speechProviderUrls(),
                presetLabel = { ProviderProfiles.nameFor(it) },
                enabled = true
            )
        }
        item {
            InputWithPresets(
                value = vm.voiceModel,
                onValue = { vm.updateVoiceModel(it) },
                label = "语音合成模型",
                presets = ModelCatalog.speechPresets(vm.voiceBaseUrl),
                presetLabel = { ModelCatalog.speechModelLabel(vm.voiceBaseUrl, it) },
                enabled = true
            )
        }
        item {
            InputWithPresets(
                value = vm.voiceVoice,
                onValue = { vm.updateVoiceVoice(it) },
                // 混合音色下 speaker 固定 `custom_mix_bigtts`，这一栏不参与合成 ⇒ 换个标签说清；
                // 复刻音色正相反：这一栏就是它的家（粘一个 `S_` 开头的 id 进来即可）
                label = if (voiceKind == CharacterVoices.VoiceKind.MIX) "音色（混合音色下不使用）" else "音色",
                // 复刻音色那一档把「我的复刻音色」并进预设清单：自己命名的音色
                // id 前缀毫无特征，光看 id 认不出来，下拉里要显示本机名字
                // （本页只读不建：新建录音在设置页「语音服务 → 复刻音色」里做）
                presets = if (isVolc) {
                    (ModelCatalog.speechVoices(vm.voiceBaseUrl, vm.voiceModel) + vm.cloneIds()).distinct()
                } else {
                    ModelCatalog.speechVoices(vm.voiceBaseUrl, vm.voiceModel)
                },
                presetLabel = { v ->
                    vm.cloneVoiceOf(v)?.name
                        ?: ModelCatalog.speechVoiceLabel(vm.voiceBaseUrl, v)
                },
                enabled = voiceKind != CharacterVoices.VoiceKind.MIX
            )
        }
        // 复刻音色：这一页只"挑"不"建"（录音要占着麦克风、还要等训练，属于语音服务页的活），
        // 所以要给一句指路 —— 否则用户在这一档看到空空的音色栏，不知道下一步该去哪
        if (isVolc && voiceKind == CharacterVoices.VoiceKind.CLONE) {
            item {
                Text(
                    uiInlineMarkdown(
                        if (vm.cloneVoices.isEmpty()) {
                            "还没录过自己的声音：到「设置 → 外观与设置 → 语音服务 → 语音朗读」选「复刻音色」，" +
                                "那里可以**在 App 里录一段**直接复刻（火山），复刻好的声音也会出现在上面这一栏的下拉里。"
                        } else {
                            "上面「音色」栏的下拉里有你复刻好的声音（${vm.cloneVoices.joinToString("、") { it.name }}）；" +
                                "要再录一个，去「设置 → 语音服务 → 语音朗读 → 复刻音色」。"
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Column {
                Text(
                    "语速：${String.format("%.2f", vm.voiceSpeed)}×",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = vm.voiceSpeed,
                    onValueChange = { vm.voiceSpeed = (it * 100).toInt() / 100f },
                    valueRange = 0.5f..2f
                )
            }
        }
        item {
            Column {
                Text(
                    "音高：${String.format("%.2f", vm.voicePitch)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = vm.voicePitch,
                    onValueChange = { vm.voicePitch = (it * 100).toInt() / 100f },
                    valueRange = 0.5f..2f
                )
            }
        }
        // ── 混合音色（火山专属，2026-09-22 用户反馈；由「声音类型」胶囊选入）──────
        // 只在这张卡指向火山时出现：混音是火山的 `custom_mix_bigtts` 协议，别家没有这个概念。
        // 开关的职责归上面那排胶囊（类型从数据推、不落库），这里只管源与权重。
        if (isVolc) {
            item { Text("混音源与权重", style = MaterialTheme.typography.bodyMedium) }
            if (voiceKind == CharacterVoices.VoiceKind.MIX) {
                val mixList = vm.voiceMixSpeakers
                val mixTotal = mixList.sumOf { it.factor.toDouble() }.takeIf { it > 0.0 } ?: 1.0
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        mixList.forEachIndexed { i, s ->
                            key(i) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    InputWithPresets(
                                        value = s.voice,
                                        onValue = { v ->
                                            vm.voiceMixSpeakers = mixList.toMutableList()
                                                .also { it[i] = s.copy(voice = v) }
                                        },
                                        label = "音色 ${i + 1}",
                                        // 可混的源**也包括本机的复刻音色**（官方口径：1.0 音色与复刻音色都能混）
                                        presets = (ModelCatalog.volcMixSourceVoices() + vm.cloneIds()).distinct(),
                                        presetLabel = { voiceDisplayName(it, vm::cloneVoiceOf) },
                                        enabled = true
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Slider(
                                            value = s.factor,
                                            onValueChange = { f ->
                                                vm.voiceMixSpeakers = mixList.toMutableList()
                                                    .also { it[i] = s.copy(factor = (f * 20).roundToInt() / 20f) }
                                            },
                                            valueRange = 0.05f..1f,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        // 显示**实际占比**（已归一化），用户不必自己把权重凑成 1
                                        Text(
                                            "${((s.factor / mixTotal) * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = mixList.size < 3,
                                onClick = {
                                    vm.voiceMixSpeakers = mixList + MixSpeaker(
                                        ModelCatalog.volcMixSourceVoices().getOrElse(mixList.size) { "" },
                                        0.3f
                                    )
                                }
                            ) { Text("＋ 加一个音色（最多 3 个）") }
                            if (mixList.size > 2) {
                                TextButton(onClick = { vm.voiceMixSpeakers = mixList.dropLast(1) }) {
                                    Text("－ 去掉最后一个")
                                }
                            }
                        }
                        // 预设（套用 / 存 / 删）：与设置页**同一份实现**（`MixPresetBar`）
                        // —— 用户 2026-09-22 的反馈正是"这里也要能用预设、存预设"
                        MixPresetBar(
                            presets = vm.mixPresets,
                            canSave = mixList.count { it.voice.isNotBlank() } >= 2,
                            enabled = true,
                            onApply = { vm.applyMixPreset(it) },
                            onDelete = { vm.deleteMixPreset(it) },
                            onSave = { vm.saveMixPreset() },
                            voiceLabel = { voiceDisplayName(it, vm::cloneVoiceOf) }
                        )
                        vm.voiceNotice?.let { n ->
                            Text(
                                "$n  ✕",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { vm.clearVoiceNotice() }
                            )
                        }
                    }
                }
            }
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
                item {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { vm.previewVoice() },
                        enabled = !vm.voicePreviewing
                    ) { Text(if (vm.voicePreviewing) "正在合成…" else "试听") }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        uiInlineMarkdown(
                            "用**这个角色**的音色读一句样例。走的是朗读那条链路（同一份解析、同一份缓存），" +
                                "所以试听能出声、聊天里就一定能出声。"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                vm.voicePreviewNotice?.let { n ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$n  ✕",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { vm.clearVoicePreviewNotice() }
                    )
                }
            }
        }
        item {
            Text(
                uiInlineMarkdown(
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
