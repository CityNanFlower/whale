package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.ui.CloneVoiceBar
import com.mysticat.roleplay.ui.VoiceKindChips
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.components.LedgerAction
import com.mysticat.roleplay.ui.components.LedgerNote
import com.mysticat.roleplay.ui.components.LedgerTone
import com.mysticat.roleplay.ui.components.WhaleConclusionBar
import com.mysticat.roleplay.ui.components.WhaleGroupLabel
import com.mysticat.roleplay.ui.components.WhaleHairField
import com.mysticat.roleplay.ui.components.WhaleLedgerRow
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import com.mysticat.roleplay.ui.uiInlineMarkdown
import com.mysticat.roleplay.ui.voiceDisplayName
import kotlin.math.roundToInt

/**
 * 「语音服务」页两个分页的行表骨架（2026-10-08 第 23 轮，与「模型与 API」同一套语法：
 * **结论条 → 分组 → 行（名｜值｜动作，就地展开）**；契约见 `.tools/ui-preview/v9-api-voice/DESIGN.md`）。
 *
 * 为什么换骨架：原来这一页是"分页胶囊 → 节标题 → 一长串说明文字与输入框"，用户的评价是
 * 找不到"要改的那一项"。现在每一项都在行上、值就在右边；说明文字收进展开层，不占扫读面。
 *
 * 保留口径（与旧实现一字不差）：朗读与识别共用 [SettingsViewModel] 一份内存态、
 * 保存/未保存退出确认仍由 [VoiceScreen] 携带；所有设置项**一项不丢**。
 */
internal fun LazyListScope.voiceTtsSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit,
    onRecord: () -> Unit
) {
    item { VoiceTtsLedger(vm, onEdit, onRecord) }
}

internal fun LazyListScope.voiceAsrSection(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit
) {
    item { VoiceAsrLedger(vm, onEdit) }
}

/* ────────────────────────────── 语音朗读 ────────────────────────────── */

@Composable
private fun VoiceTtsLedger(
    vm: SettingsViewModel,
    onEdit: (CustomProvider) -> Unit,
    onRecord: () -> Unit
) {
    var open by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) { open = if (open == key) "" else key }

    val builtin = vm.ttsProvider == "builtin"
    val provider = vm.providerLabel(vm.ttsBaseUrl)
    val keyOk = vm.ttsBaseUrl.isNotBlank() &&
        (vm.hasSpeechCredentialFor(vm.ttsBaseUrl) || !vm.ttsBaseUrl.startsWith("https://"))
    val volcSpeech = ProviderProfiles.speechProtocol(vm.ttsBaseUrl) ==
        ProviderProfiles.SpeechProtocol.VOLC
    // 本机复刻音色库要进判定：录音复刻出来的 id 没有 `S_` 前缀，不查库会被当成普通 2.0 音色
    val cloneIds = vm.cloneIds()
    val voiceKind = CharacterVoices.kindOf(
        vm.ttsVoice, vm.ttsModel, vm.ttsMixEnabled, vm.ttsMixSpeakers,
        mixSupported = volcSpeech, cloneIds = cloneIds
    )
    val voiceLabel = vm.cloneVoiceOf(vm.ttsVoice)?.name
        ?: ModelCatalog.speechVoiceLabel(vm.ttsBaseUrl, vm.ttsVoice).ifBlank { "未选" }

    Column {
        WhaleConclusionBar(
            title = when {
                !builtin -> "朗读可用 · 系统语音"
                keyOk -> "朗读可用 · $provider"
                else -> "朗读未配置 · $provider"
            },
            subtitle = if (!builtin) {
                "免费离线 · 语速 ${String.format("%.2f", vm.ttsSpeed)}× · 音色取决于系统"
            } else {
                vm.ttsModel.ifBlank { "未选模型" } + " · " + voiceLabel + if (!keyOk) " · 缺少凭据" else ""
            },
            tone = if (builtin && !keyOk) LedgerTone.Alert else LedgerTone.Ok,
            // 试听**两个分支都有**（2026-10-10 用户报"试听没了"）：此前只在供应商分支渲染，
            // 而默认值就是系统语音（`ttsProvider = "system"`）——不切到供应商就看不到按钮。
            // 系统分支走同一条试听实现（`TtsAudition.play` 里的 `speakMessage`，样例句与云端同一句）。
            action = {
                LedgerAction(
                    if (vm.ttsPreviewing) "合成中" else "试听",
                    // 云端没凭据置灰（同页其它分段口径）；系统语音不需要凭据
                    enabled = (!builtin || keyOk) && !vm.ttsPreviewing
                ) { vm.previewTts() }
            }
        )
        vm.ttsPreviewNotice?.let { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedgerNote(n, Modifier.weight(1f), danger = true)
                LedgerAction("清除") { vm.clearTtsPreviewNotice() }
            }
        }
        WhaleGroupLabel("朗读方式")
        WhaleLedgerRow(
            label = "新回复自动朗读",
            value = if (vm.ttsAutoRead) "开" else "关",
            valueMono = false,
            actionText = if (open == "auto") "收起" else "说明",
            expanded = open == "auto",
            onToggle = { toggle("auto") },
            trailing = { Switch(checked = vm.ttsAutoRead, onCheckedChange = { vm.ttsAutoRead = it }) },
            content = {
                LedgerNote(
                    "开启后新回复边生成边读（攒够一句就开始念）；关掉则只在长按「朗读」时读。" +
                        "与聊天页顶栏的喇叭是同一个开关。长按任意一条角色回复 →「朗读」即可听；" +
                        "聊天页顶栏的喇叭图标控制新回复自动朗读（斜杠＝关闭）。"
                )
            }
        )
        WhaleLedgerRow(
            label = "语速",
            value = String.format("%.2f×", vm.ttsSpeed),
            actionText = if (open == "speed") "收起" else "调整",
            expanded = open == "speed",
            onToggle = { toggle("speed") },
            content = {
                Slider(
                    value = vm.ttsSpeed,
                    onValueChange = { vm.ttsSpeed = (it * 100).roundToInt() / 100f },
                    valueRange = 0.5f..2f
                )
            }
        )
        WhaleLedgerRow(
            label = "音高",
            value = String.format("%.2f", vm.ttsPitch),
            actionText = if (open == "pitch") "收起" else "调整",
            expanded = open == "pitch",
            onToggle = { toggle("pitch") },
            content = {
                Slider(
                    value = vm.ttsPitch,
                    onValueChange = { vm.ttsPitch = (it * 100).roundToInt() / 100f },
                    valueRange = 0.5f..2f
                )
            }
        )
        WhaleLedgerRow(
            label = "跳过（）里的动作描写",
            value = if (vm.ttsSkipActionText) "开" else "关",
            valueMono = false,
            actionText = if (open == "skip") "收起" else "说明",
            expanded = open == "skip",
            onToggle = { toggle("skip") },
            trailing = { Switch(checked = vm.ttsSkipActionText, onCheckedChange = { vm.ttsSkipActionText = it }) },
            content = {
                LedgerNote("本 App 的动作 / 神态 / 心理都写在（）里，默认跳过、只读对白；关掉则连描写一起读。")
            }
        )
        WhaleGroupLabel("朗读引擎")
        WhaleLedgerRow(
            label = "引擎",
            value = if (builtin) "供应商合成" else "系统语音",
            valueMono = false,
            actionText = if (open == "engine") "收起" else "更换",
            expanded = open == "engine",
            onToggle = { toggle("engine") },
            content = {
                Row(horizontalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)) {
                    WhaleChip(
                        selected = vm.ttsProvider.ifBlank { "system" } == "system",
                        onClick = { vm.ttsProvider = "system" },
                        label = { Text("系统语音（免费离线）") }
                    )
                    WhaleChip(
                        selected = vm.ttsProvider == "builtin",
                        onClick = { vm.ttsProvider = "builtin" },
                        label = { Text("供应商合成（音色更好）") }
                    )
                }
                LedgerNote(
                    if (builtin) {
                        "合成按字符计费（单价见各家官网；硅基流动 CosyVoice2 实测 ¥0.05/千字符，一条 300 字的回复约 1.5 分）；" +
                            "同一句会命中本地缓存、不重复计费。"
                    } else {
                        "系统语音合成：免费、离线、不需要任何权限，音色取决于系统" +
                            "（系统里没有中文语音包时，聊天页会提示去「系统设置 → 语言与输入 → 文字转语音」安装）。" +
                            "想要更好的音色就切到「供应商合成」。"
                    }
                )
            }
        )
        if (builtin) {
            WhaleLedgerRow(
                label = "供应商",
                value = provider,
                valueMono = false,
                actionText = if (open == "prov") "收起" else "更换",
                expanded = open == "prov",
                onToggle = { toggle("prov") },
                content = {
                    ProviderChips(
                        current = vm.ttsBaseUrl,
                        presets = ProviderProfiles.speechProviderUrls(),
                        custom = vm.customProviders,
                        anyCapability = true,
                        onPick = { url ->
                            vm.ttsBaseUrl = url
                            // 换家时把模型/音色换成该家的第一条预设，避免留着上一家的 id
                            ModelCatalog.speechPresets(url).firstOrNull()?.let { m ->
                                vm.ttsModel = m
                                vm.ttsVoice = ModelCatalog.speechVoices(url, m).firstOrNull().orEmpty()
                            }
                        },
                        onEdit = onEdit
                    )
                }
            )
            WhaleLedgerRow(
                label = "凭据",
                value = when {
                    vm.hasOwnSpeechCredential(vm.ttsBaseUrl) -> "已单独填"
                    keyOk -> "共用「模型与 API」的 Key"
                    else -> "未填"
                },
                valueMono = false,
                valueDim = !keyOk,
                actionText = if (open == "cred") "收起" else "管理",
                expanded = open == "cred",
                onToggle = { toggle("cred") },
                content = { if (vm.ttsBaseUrl.isNotBlank()) SpeechCredentialSection(vm, vm.ttsBaseUrl) }
            )
            if (vm.ttsBaseUrl.isNotBlank() && !keyOk) {
                LedgerNote(
                    NoKeyHintForSpeech(provider),
                    modifier = Modifier.padding(horizontal = WhaleSpacing.md, vertical = WhaleSpacing.xs)
                )
            }
            WhaleLedgerRow(
                label = "语音合成模型",
                value = vm.ttsModel.ifBlank { "未选" },
                enabled = keyOk,
                actionText = if (open == "model") "收起" else "更换",
                expanded = open == "model",
                onToggle = { toggle("model") },
                content = {
                    InputWithPresets(
                        value = vm.ttsModel,
                        onValue = { m ->
                            vm.ttsModel = m
                            // 音色要跟着"模型/档位"走，但各家的规则不一样：
                            // - 硅基流动：音色写成「模型名:音色名」，换模型要重写前缀；
                            // - 其它：把音色换成本档的第一条预设，避免留着别档的 id
                            val kw = ModelCatalog.speechProviderKeyword(vm.ttsBaseUrl)
                            if (kw == "siliconflow") {
                                val bare = vm.ttsVoice.substringAfterLast(':')
                                if (bare.isNotBlank()) vm.ttsVoice = "$m:$bare"
                            } else if (kw != null && ModelCatalog.speechVoices(vm.ttsBaseUrl, m).isNotEmpty() &&
                                vm.ttsVoice !in ModelCatalog.speechVoices(vm.ttsBaseUrl, m)
                            ) {
                                vm.ttsVoice = ModelCatalog.speechVoices(vm.ttsBaseUrl, m).first()
                            }
                        },
                        presets = ModelCatalog.speechPresets(vm.ttsBaseUrl),
                        // 火山/腾讯这两栏是技术参数（Resource-Id / ModelType），下拉里给可读说明
                        presetLabel = { ModelCatalog.speechModelLabel(vm.ttsBaseUrl, it) },
                        enabled = keyOk
                    )
                }
            )
            if (volcSpeech) {
                // 声音类型：**类型不落库**，由下面几栏的数据推出来（CharacterVoices.kindOf）；
                // 点胶囊＝把数据改成这种类型该有的样子（vm.pickVoiceKind）。混音/复刻是火山专属。
                WhaleLedgerRow(
                    label = "声音类型",
                    value = when (voiceKind) {
                        CharacterVoices.VoiceKind.MIX -> "混合音色"
                        CharacterVoices.VoiceKind.CLONE -> "复刻音色"
                        else -> "单一音色"
                    },
                    valueMono = false,
                    enabled = keyOk,
                    actionText = if (open == "kind") "收起" else "更换",
                    expanded = open == "kind",
                    onToggle = { toggle("kind") },
                    content = {
                        VoiceKindChips(
                            kind = voiceKind,
                            allowMix = true,
                            allowClone = true,
                            enabled = keyOk,
                            // 这类胶囊是单选：点一个就把编辑器收起（选完即走，不留一屏展开态）
                            onPick = { vm.pickVoiceKind(it); toggle("kind") }
                        )
                    }
                )
            }
            WhaleLedgerRow(
                label = if (voiceKind == CharacterVoices.VoiceKind.MIX) "音色（混合音色下不使用）" else "音色",
                value = if (voiceKind == CharacterVoices.VoiceKind.MIX) "由混音源决定" else voiceLabel,
                valueMono = voiceKind == CharacterVoices.VoiceKind.MIX,
                valueDim = voiceKind == CharacterVoices.VoiceKind.MIX,
                enabled = keyOk && voiceKind != CharacterVoices.VoiceKind.MIX,
                actionText = if (open == "voice") "收起" else "更换",
                expanded = open == "voice",
                onToggle = { toggle("voice") },
                content = {
                    InputWithPresets(
                        value = vm.ttsVoice,
                        onValue = { v ->
                            vm.ttsVoice = v
                            // 腾讯云的音色与 ModelType 档位绑定（大模型=3 / 精品=2）：选音色时顺手把档位改对
                            ModelCatalog.tencentModelTypeFor(v)?.let { vm.ttsModel = it }
                            // 火山同理：音色决定 V3 的 Resource-Id（2.0/1.0/复刻 S_ 各不同）
                            if (ProviderProfiles.speechProtocol(vm.ttsBaseUrl) ==
                                ProviderProfiles.SpeechProtocol.VOLC
                            ) {
                                vm.ttsModel = ModelCatalog.volcResourceIdForVoice(v, cloneIds)
                            }
                        },
                        // 复刻音色那一档把「我的复刻音色」并进预设清单（自己命名的 id 认不出来，下拉里得显示本机名字）
                        presets = if (volcSpeech) {
                            (ModelCatalog.speechVoices(vm.ttsBaseUrl, vm.ttsModel) + cloneIds).distinct()
                        } else {
                            ModelCatalog.speechVoices(vm.ttsBaseUrl, vm.ttsModel)
                        },
                        presetLabel = { v ->
                            vm.cloneVoiceOf(v)?.name
                                ?: ModelCatalog.speechVoiceLabel(vm.ttsBaseUrl, v)
                        },
                        enabled = keyOk && voiceKind != CharacterVoices.VoiceKind.MIX
                    )
                    LedgerNote("用当前的模型/音色读一句样例，能立刻听出音色与语速是否合适（右上角「试听」）。")
                }
            )
            if (voiceKind == CharacterVoices.VoiceKind.MIX) {
                WhaleLedgerRow(
                    label = "混音源与权重",
                    value = "${vm.ttsMixSpeakers.size} 个音色",
                    enabled = keyOk,
                    actionText = if (open == "mix") "收起" else "调整",
                    expanded = open == "mix",
                    onToggle = { toggle("mix") },
                    content = { MixSpeakerSection(vm, keyOk) }
                )
            }
            if (volcSpeech && voiceKind == CharacterVoices.VoiceKind.CLONE) {
                WhaleLedgerRow(
                    // 行名带「库」：上一行「声音类型」的值就叫「复刻音色」，同名会让人（和自动化）分不清哪颗是行
                    label = "复刻音色库",
                    value = "${vm.ttsCloneVoices.size} 个",
                    enabled = keyOk,
                    actionText = if (open == "clone") "收起" else "管理",
                    expanded = open == "clone",
                    onToggle = { toggle("clone") },
                    content = {
                        Column(verticalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = keyOk && vm.cloneProgress == null,
                                    onClick = onRecord
                                ) { Text("🎙 录音复刻一个新音色") }
                                Spacer(Modifier.width(WhaleSpacing.sm))
                                LedgerNote(
                                    "在 App 里录一段自己的声音（读屏幕上那句话），复刻完就能直接朗读。" +
                                        "也可以把控制台建好的音色 ID 粘在「音色」栏。",
                                    Modifier.weight(1f)
                                )
                            }
                            CloneVoiceBar(
                                voices = vm.ttsCloneVoices,
                                enabled = keyOk && vm.cloneProgress == null,
                                onUse = { v ->
                                    vm.ttsVoice = v.id
                                    vm.ttsModel = "seed-icl-2.0"
                                    vm.noteMix("已切到「${v.name}」，点「试听」听听像不像")
                                },
                                onDelete = { vm.removeCloneVoice(it) },
                                onCheck = { vm.refreshCloneVoice(it) }
                            )
                            vm.cloneProgress?.let { LedgerNote(it) }
                            vm.cloneError?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LedgerNote(it, Modifier.weight(1f), danger = true)
                                    LedgerAction("清除") { vm.clearCloneError() }
                                }
                            }
                        }
                    }
                )
            }
            WhaleLedgerRow(
                label = "计费与本地部署",
                value = "按字符计费 · 支持本地部署",
                valueMono = false,
                actionText = if (open == "billing") "收起" else "说明",
                expanded = open == "billing",
                onToggle = { toggle("billing") },
                content = {
                    LedgerNote(
                        uiInlineMarkdown(
                            "本地部署的语音服务（Kokoro-FastAPI / Speaches / Xinverse+CosyVoice / GPT-SoVITS 包装等）" +
                                "填它的 OpenAI 兼容地址即可，可以留空 Key；**内网 http 地址仅在私有网段放行**，" +
                                "回复正文会以明文走你自己的局域网，不要把云端 Key 填在这种地址上。\n" +
                                "阿里百炼走原生接口（音色与模型版本绑定：`longxiaochun_v2` 这类要配 cosyvoice-v2 系），" +
                                "音色可以照官方《CosyVoice 音色列表》直接粘贴。\n" +
                                "火山豆包语音与腾讯云要**各自控制台的凭据**（火山新版控制台填 API Key，旧版填 AppID+AccessToken；" +
                                "腾讯用 CAM 的 SecretId+SecretKey），在「凭据」行里填；音色 id 也都要从各自控制台复制 ——" +
                                "火山是 speaker id（如 `zh_female_xiaohe_uranus_bigtts`，Resource-Id 会按音色自动判），" +
                                "腾讯是**数字**（VoiceType，档位会自动配对：大模型 3 / 精品 2）。"
                        )
                    )
                }
            )
        }
    }
}

/* ────────────────────────────── 语音识别 ────────────────────────────── */

@Composable
private fun VoiceAsrLedger(vm: SettingsViewModel, onEdit: (CustomProvider) -> Unit) {
    var open by rememberSaveable { mutableStateOf("") }
    fun toggle(key: String) { open = if (open == key) "" else key }

    val builtin = vm.asrProvider == "builtin"
    val provider = vm.providerLabel(vm.asrBaseUrl)
    val asrKeyOk = vm.asrBaseUrl.isNotBlank() &&
        (vm.hasSpeechCredentialFor(vm.asrBaseUrl) || !vm.asrBaseUrl.startsWith("https://"))
    val sharedCredential = vm.asrBaseUrl.isNotBlank() && vm.asrBaseUrl == vm.ttsBaseUrl &&
        vm.ttsProvider == "builtin"

    Column {
        WhaleConclusionBar(
            title = when {
                !builtin -> "识别可用 · 系统识别"
                asrKeyOk -> "识别可用 · $provider"
                else -> "识别未配置 · $provider"
            },
            subtitle = when {
                !builtin -> "免费 · 不用 Key · 识别率取决于手机厂商"
                asrKeyOk -> vm.asrModel.ifBlank { "未选模型" }
                else -> "缺少凭据"
            },
            tone = if (builtin && !asrKeyOk) LedgerTone.Alert else LedgerTone.Ok
        )
        WhaleGroupLabel("语音输入")
        WhaleLedgerRow(
            label = "麦克风用法",
            value = if (isDesktopLayout || vm.asrTapToTalk) "点按说话" else "按住说话",
            valueMono = false,
            actionText = if (open == "mic") "收起" else "说明",
            expanded = open == "mic",
            onToggle = { toggle("mic") },
            content = {
                if (!isDesktopLayout) {
                    Row(horizontalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)) {
                        WhaleChip(
                            selected = !vm.asrTapToTalk,
                            onClick = { vm.asrTapToTalk = false },
                            label = { Text("按住说话（默认）") }
                        )
                        WhaleChip(
                            selected = vm.asrTapToTalk,
                            onClick = { vm.asrTapToTalk = true },
                            label = { Text("点按说话") }
                        )
                    }
                }
                LedgerNote(
                    buildString {
                        append("聊天页输入框右侧的麦克风：")
                        append(
                            if (isDesktopLayout || vm.asrTapToTalk) "点一下开始录音、再点一下结束并识别（录音中旁边会出现「×」取消这一段）"
                            else "按住说话、松手识别"
                        )
                        append("，识别结果填进输入框（不会自动发送）。")
                        if (isDesktopLayout) {
                            append("桌面固定这个用法——鼠标「按住不放」不好按，而且按下时手指占着键、点不到取消。")
                        }
                        append("首次使用会申请麦克风权限。")
                        if (!isDesktopLayout) {
                            append("\n")
                            append(
                                if (vm.asrTapToTalk)
                                    "点一下麦克风开始录音，再点一下结束并识别；录音中旁边会出现「×」用来取消这一段。"
                                else
                                    "按住麦克风说话、松手开始识别；单手操作更顺手，录到一半想放弃只需把手指移开。"
                            )
                        }
                    }
                )
            }
        )
        WhaleLedgerRow(
            label = "识别引擎",
            value = if (builtin) "供应商识别" else "系统识别",
            valueMono = false,
            actionText = if (open == "engine") "收起" else "更换",
            expanded = open == "engine",
            onToggle = { toggle("engine") },
            content = {
                Row(horizontalArrangement = Arrangement.spacedBy(WhaleSpacing.sm)) {
                    WhaleChip(
                        selected = vm.asrProvider.ifBlank { "system" } == "system",
                        onClick = { vm.asrProvider = "system" },
                        label = { Text("系统识别（免费）") }
                    )
                    WhaleChip(
                        selected = vm.asrProvider == "builtin",
                        onClick = { vm.asrProvider = "builtin" },
                        label = { Text("供应商识别") }
                    )
                }
                if (!builtin) {
                    LedgerNote(
                        "系统识别：调手机自带的语音识别（免费、不要 Key）。识别率取决于厂商服务，" +
                            "有些机型需要联网。想要更稳的中文识别就切到「供应商识别」。"
                    )
                }
            }
        )
        if (builtin) {
            WhaleLedgerRow(
                label = "供应商",
                value = provider,
                valueMono = false,
                actionText = if (open == "prov") "收起" else "更换",
                expanded = open == "prov",
                onToggle = { toggle("prov") },
                content = {
                    ProviderChips(
                        current = vm.asrBaseUrl,
                        presets = ProviderProfiles.asrProviderUrls(),
                        custom = vm.customProviders,
                        anyCapability = true,
                        onPick = { url ->
                            vm.asrBaseUrl = url
                            // 换家时换成该家的第一条识别模型，避免留着上一家的 id（会 400）
                            ModelCatalog.asrPresets(url).firstOrNull()?.let { vm.asrModel = it }
                        },
                        onEdit = onEdit
                    )
                }
            )
            WhaleLedgerRow(
                label = "凭据",
                value = when {
                    vm.hasOwnSpeechCredential(vm.asrBaseUrl) -> "已单独填"
                    sharedCredential -> "与朗读共用一份"
                    asrKeyOk -> "共用「模型与 API」的 Key"
                    else -> "未填"
                },
                valueMono = false,
                valueDim = !asrKeyOk,
                actionText = if (open == "cred") "收起" else "管理",
                expanded = open == "cred",
                onToggle = { toggle("cred") },
                content = {
                    if (sharedCredential) {
                        // 与朗读用同一家时凭据是同一份，不重复渲染（说一句就够）
                        LedgerNote("凭据与朗读用的「语音凭据」共用一份（同一家只填一次）。")
                    } else if (vm.asrBaseUrl.isNotBlank()) {
                        SpeechCredentialSection(vm, vm.asrBaseUrl)
                    }
                }
            )
            if (vm.asrBaseUrl.isNotBlank() && !asrKeyOk) {
                LedgerNote(
                    NoKeyHintForSpeech(provider),
                    modifier = Modifier.padding(horizontal = WhaleSpacing.md, vertical = WhaleSpacing.xs)
                )
            }
            WhaleLedgerRow(
                label = "识别模型",
                value = vm.asrModel.ifBlank { "未选" },
                enabled = asrKeyOk,
                actionText = if (open == "model") "收起" else "更换",
                expanded = open == "model",
                onToggle = { toggle("model") },
                content = {
                    InputWithPresets(
                        value = vm.asrModel,
                        onValue = { vm.asrModel = it },
                        presets = ModelCatalog.asrPresets(vm.asrBaseUrl),
                        enabled = asrKeyOk
                    )
                }
            )
            WhaleLedgerRow(
                label = "识别语言",
                value = vm.asrLanguage.ifBlank { "自动判断" },
                valueMono = false,
                valueDim = vm.asrLanguage.isBlank(),
                enabled = asrKeyOk,
                actionText = if (open == "lang") "收起" else "编辑",
                expanded = open == "lang",
                onToggle = { toggle("lang") },
                content = {
                    WhaleHairField(
                        value = vm.asrLanguage,
                        onValueChange = { vm.asrLanguage = it }
                    )
                    // 原来「（选填）」挂在输入框标签上、填法提示挂在 placeholder 里；
                    // 标签撤掉后两句并进说明（用户 2026-10-10）
                    LedgerNote("（选填）留空＝自动判断；中文可填 zh，英文 en。")
                }
            )
            WhaleLedgerRow(
                label = "试识别",
                value = "点一下开始录音，说完点停止",
                valueMono = false,
                enabled = asrKeyOk,
                actionText = if (open == "trial") "收起" else "开始",
                expanded = open == "trial",
                onToggle = { toggle("trial") },
                content = { AsrTrialButton(vm, asrKeyOk) }
            )
            WhaleLedgerRow(
                label = "计费与回退",
                value = "供应商计费 · 失败自动回退系统识别",
                valueMono = false,
                actionText = if (open == "note") "收起" else "说明",
                expanded = open == "note",
                onToggle = { toggle("note") },
                content = {
                    LedgerNote(
                        uiInlineMarkdown(
                            "供应商识别把这段录音发给你选的那家做转写（硅基流动的星辰系列计费项是 free-asr-model、单价 0）；" +
                                "选了「供应商识别」但没配好时，麦克风**自动回退系统识别**并在聊天页说明原因。\n" +
                                "系统识别免费、不用 Key，但识别率与是否需要联网取决于手机厂商的识别服务。"
                        )
                    )
                }
            )
        }
    }
}

/** 语音凭据缺失提示（与 ModelDropdown 的 NoKeyHint 同口径，只换一个"去哪填"的指路）。 */
private fun NoKeyHintForSpeech(providerName: String): String =
    "「$providerName」还没有可用的凭据：在上面的「凭据」行里单独填一把，或去「模型与 API」给这家存 Key。"
