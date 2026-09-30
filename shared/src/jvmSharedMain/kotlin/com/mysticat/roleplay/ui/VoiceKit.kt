package com.mysticat.roleplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.CloneVoice
import com.mysticat.roleplay.data.MixPreset
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.TtsCache
import com.mysticat.roleplay.data.TtsSpeaker

/**
 * 语音界面的共用件：**「试听」与「声音类型」胶囊各只有一份实现**。
 *
 * 为什么抽出来：设置页「语音朗读」与角色编辑器里的「角色专属音色」是同一套字段、同一批坑
 * （混音要用 1.0 档、复刻要 ICL 档、试听必须拿**生效设置**去合成）——两处各写一份的话，
 * 一边修好了另一边还会照老样子出错（历史上"打开混音第一次试听必失败"就是这种双份口径造成的）。
 */
object TtsAudition {

    /**
     * 试听样句。**两处必须同一句**：用户比较的是音色，不是文本；同一句还能命中同一份缓存，
     * 反复试听不重复付费（缓存键里带着音色与混音，所以换了配置照样会重新合成）。
     */
    const val SAMPLE = "这是一句试听，用来确认音色和语速是否合适。"

    /**
     * 用 [settings] 读一句样例。返回 null = 成功走完，否则是**给用户看的一行原因**。
     *
     * [settings] 必须是**生效设置**（设置页＝表单当前值；角色编辑器＝`CharacterVoices.effective` 之后的那一份），
     * 否则会出现"界面上是卡里的音色、试听听到的是全局音色"这种最难查的错。
     * 三分支与聊天页同口径：供应商合成失败**不抛**，而是回退系统语音并把原因交出来。
     */
    suspend fun play(speaker: TtsSpeaker, settings: AiSettings, sample: String = SAMPLE): String? {
        speaker.speed = settings.ttsSpeed
        speaker.pitch = settings.ttsPitch
        speaker.skipActionText = settings.ttsSkipActionText
        // 与聊天页 `useBuiltinTts` 同一判据：选了供应商但没填地址/模型就回落到系统语音，不出声才是更大的问题
        val builtin = settings.ttsProvider == "builtin" &&
            settings.ttsBaseUrl.isNotBlank() && settings.ttsModel.isNotBlank()
        return try {
            if (builtin) {
                speaker.speakBuiltin(sample) { piece -> TtsCache.file(settings, piece) }
                speaker.lastFallbackReason?.let { "供应商合成失败，已改用系统语音：$it" }
            } else {
                speaker.speakMessage(sample)
                speaker.unavailableReason
            }
        } catch (t: Throwable) {
            t.message ?: "试听失败"
        }
    }
}

/**
 * 「声音类型」胶囊：**单一音色 / 混合音色 / 复刻音色**。
 *
 * 三个取值都从已有数据推出来（[CharacterVoices.kindOf]），不新增存储字段——所以卡与设置的往返、
 * 导出导入的保真都不用跟着改口径。混合与复刻只有火山有（混音是 `custom_mix_bigtts` 协议、
 * 复刻是 ICL 资源），不是火山的家这两个胶囊不出现（`allowMix` / `allowClone` 传假）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceKindChips(
    kind: CharacterVoices.VoiceKind,
    allowMix: Boolean,
    allowClone: Boolean,
    enabled: Boolean,
    onPick: (CharacterVoices.VoiceKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text("声音类型", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhaleChip(
                selected = kind == CharacterVoices.VoiceKind.SINGLE,
                enabled = enabled,
                onClick = { onPick(CharacterVoices.VoiceKind.SINGLE) },
                label = { Text("单一音色") }
            )
            if (allowMix) {
                WhaleChip(
                    selected = kind == CharacterVoices.VoiceKind.MIX,
                    enabled = enabled,
                    onClick = { onPick(CharacterVoices.VoiceKind.MIX) },
                    label = { Text("混合音色") }
                )
            }
            if (allowClone) {
                WhaleChip(
                    selected = kind == CharacterVoices.VoiceKind.CLONE,
                    enabled = enabled,
                    onClick = { onPick(CharacterVoices.VoiceKind.CLONE) },
                    label = { Text("复刻音色") }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            uiInlineMarkdown(
                when (kind) {
                    CharacterVoices.VoiceKind.MIX ->
                        "把 2~3 个音色按比例混成一把新声音（火山的「超强混音」）。可混的源只有 1.0 系列音色" +
                            "（moon / mars 那批）与已在控制台建好的复刻音色，2.0 音色官方不支持混音。"
                    CharacterVoices.VoiceKind.CLONE ->
                        "用你自己复刻出来的声音：可以**在下面录一段**让 App 去复刻，也可以把控制台" +
                            "「声音复刻」建好的音色 ID（`S_` 开头）粘到「音色」栏；资源档会自动切到 ICL。"
                    else ->
                        "这家音色库里挑一个现成的音色（也可以直接手填 id）。"
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * **混合音色预设条**：点标签套用、`★ 存为预设` 保存当前这一组、`✕` 删除。
 *
 * 抽出来的理由与 [VoiceKindChips] 同一件事——设置页与角色编辑器是同一套字段、同一批坑，
 * 两处各写一份就会一边有、一边没有（用户反馈的原话就是这件事：
 * "角色专属音色也要能够用预设和存预设"）。所以**只留一份实现**，两边都渲染它。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixPresetBar(
    presets: List<MixPreset>,
    /** 保存按钮可不可点：至少要 2 个填好的源（1 个源那叫单一音色，不该存成"混合"预设） */
    canSave: Boolean,
    enabled: Boolean,
    onApply: (MixPreset) -> Unit,
    onDelete: (MixPreset) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 音色 id → 显示名。默认走火山混音源清单；调用方**要再查一次本机复刻音色库**
     * （自己命名的音色 id 前缀毫无特征，不查库就只能显示一串裸 id）。
     */
    voiceLabel: (String) -> String = { ModelCatalog.volcMixVoiceLabel(it) }
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = enabled && canSave, onClick = onSave) { Text("★ 存为预设") }
            Text(
                "调好的组合可以存成预设，换角色时点一下就能套用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presets.forEach { p ->
                    AssistChip(
                        onClick = { onApply(p) },
                        enabled = enabled,
                        label = { Text("${p.name}：${mixPresetLabel(p, voiceLabel)}") },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "删除预设 ${p.name}",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(enabled = enabled) { onDelete(p) }
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * 预设标签上的那串音色名（"御姐 + 少女"）。
 *
 * ⚠ 必须能显示**本机复刻音色**：录音复刻出来的 id 是一串无特征的字母数字（`whale…`），
 * 直接显示 id 用户根本认不出那是自己的声音（[voiceLabel] 因此可注入，见 [MixPresetBar]）。
 */
fun mixPresetLabel(
    p: MixPreset,
    voiceLabel: (String) -> String = { ModelCatalog.volcMixVoiceLabel(it) }
): String = p.speakers.joinToString(" + ") { voiceLabel(it.voice).substringBefore('（') }

/** 音色选择处的显示名：**先查本机复刻音色库**（有本机名字就用本机名字），再退回内置清单 */
fun voiceDisplayName(id: String, cloneVoiceOf: (String) -> CloneVoice?): String =
    cloneVoiceOf(id)?.name ?: ModelCatalog.volcMixVoiceLabel(id)

/**
 * **我的复刻音色**：录音复刻出来的音色库。
 *
 * 说清两件事，否则用户会以为"点了没反应"：
 * - `ready = false` 的是**训练中**（上传成功、服务端还没熟）——给它「查状态」，别让它看着像坏了；
 * - 点标签＝把这个音色套到当前配置（音色栏 + ICL 档位），这正是"录它"的目的。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneVoiceBar(
    voices: List<CloneVoice>,
    enabled: Boolean,
    onUse: (CloneVoice) -> Unit,
    onDelete: (CloneVoice) -> Unit,
    onCheck: (CloneVoice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (voices.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("我的复刻音色", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            voices.forEach { v ->
                AssistChip(
                    onClick = { onUse(v) },
                    enabled = enabled,
                    label = { Text(if (v.ready) v.name else "${v.name}（训练中）") },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "删除音色 ${v.name}",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(enabled = enabled) { onDelete(v) }
                        )
                    }
                )
            }
        }
        if (voices.any { !it.ready }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = enabled,
                    onClick = { voices.firstOrNull { !it.ready }?.let(onCheck) }
                ) { Text("查状态") }
                Text(
                    "「训练中」的那些还在服务端复刻，过一会儿点这里看看好了没。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
