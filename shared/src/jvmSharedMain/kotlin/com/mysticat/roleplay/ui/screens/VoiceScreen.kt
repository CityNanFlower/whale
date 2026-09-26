package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.data.transcribe
import com.mysticat.roleplay.ui.CloneVoiceBar
import com.mysticat.roleplay.ui.MixPresetBar
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.uiInlineMarkdown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.MixPreset
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.Voice
import com.mysticat.roleplay.data.VoiceCloneService
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.VoiceKindChips
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.voiceDisplayName
import kotlin.math.roundToInt

/**
 * 「语音服务」页（TTS v2 的 Step 2，2026-09-16 建；**2026-09-17 改名并分页**）。
 *
 * 页内两个分页：**语音朗读**（引擎 / 音色 / 供应商 / 凭据 / 混合音色 / 试听）与
 * **语音识别**（系统识别 vs 供应商识别 / 模型 / 语言 / 试识别）。用户 2026-09-17 要求这么分。
 *
 * **为什么从「模型与API」搬出来**：那一页是纯 API 配置（供应商 Key + 模型参数），而
 * "用什么声音读、朗读偏好、语音输入"属于**使用偏好**，与「外观与设置」里的主题 / 字体同类。
 * 用户 2026-09-16 拍了这一刀（音色选型依据）。
 *
 * **状态复用 [SettingsViewModel]**（所有语音字段都在它身上、`init` 从存档载入），所以这一页与
 * 「模型与API」写的是同一份 settings.json —— 不引入第二份内存态，也就不会互相覆盖。
 * 代价是这一页必须自己带「保存」与"未保存退出"确认（设置页那套原样搬来）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    /**
     * 桌面三栏：**内嵌进第三栏**（入口＝第二栏「语音服务」）。为真时不套 Scaffold/顶栏，
     * 改画一条窄头部（页名 + 未保存提示 + 保存）；内部分页胶囊保留（第二栏只到"语音服务"这一级）。
     * 手机端恒为 false，行为零变化。
     */
    embedded: Boolean = false,
    vm: SettingsViewModel = viewModel(factory = noArgViewModelFactory { SettingsViewModel() })
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf<com.mysticat.roleplay.data.CustomProvider?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }
    // 录音复刻弹层。**状态放在页面这一层、不放 LazyColumn 的 item 里**：item 滚出屏幕会被回收，
    // 状态跟着没了 = 用户滑一下就"弹层自己关了"（录音这种要举着手机的场景最容易碰到）
    var showRecorder by remember { mutableStateOf(false) }
    // 分页：tts = 语音朗读 / asr = 语音识别。用 rememberSaveable，转屏后不跳回第一页
    var voiceTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("tts") }

    fun requestBack() {
        if (vm.isDirty) showDiscardConfirm = true else onBack()
    }
    // 返回键（安卓）/ Esc（桌面）都走这条：有未保存修改先问，干净就直接退回上一层
    WhaleBackHandler { requestBack() }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            vm.cryptoNotice?.let { notice ->
                item {
                    Text(
                        "⚠️ $notice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            vm.autoConfigNotice?.let { notice ->
                item {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ── 分页：语音朗读 / 语音识别（用户 2026-09-17 要求把这一页分成两个分页）──
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhaleChip(
                        selected = voiceTab == "tts",
                        onClick = { voiceTab = "tts" },
                        label = { Text("语音朗读") }
                    )
                    WhaleChip(
                        selected = voiceTab == "asr",
                        onClick = { voiceTab = "asr" },
                        label = { Text("语音识别") }
                    )
                }
            }

            if (voiceTab == "asr") {
            // ────────────────────────── 语音输入（ASR，Step 3）──────────────────────────
            item { SectionTitle("语音输入") }
            item {
                // 引子跟着**当前用法**说话：切成"点按"后这句话还写"按住"就自相矛盾了
                Text(
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
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 麦克风用法：桌面固定点按，所以只在手机端给这个选择，免得桌面出现一个不起作用的开关
            if (!isDesktopLayout) {
                item {
                    Column {
                        Text("麦克风用法", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (vm.asrTapToTalk)
                                "点一下麦克风开始录音，再点一下结束并识别；录音中旁边会出现「×」用来取消这一段。"
                            else
                                "按住麦克风说话、松手开始识别；单手操作更顺手，录到一半想放弃只需把手指移开。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Column {
                    Text("识别引擎", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
            if (vm.asrProvider == "builtin") {
                item {
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
                        onEdit = { editingCustom = it; showCustomEditor = true }
                    )
                }
                val asrKeyOk = vm.asrBaseUrl.isNotBlank() &&
                    (vm.hasSpeechCredentialFor(vm.asrBaseUrl) || !vm.asrBaseUrl.startsWith("https://"))
                // 与朗读用同一家时凭据是同一份，不重复渲染（下面那句小字就是说明）
                if (vm.asrBaseUrl.isNotBlank() &&
                    (vm.asrBaseUrl != vm.ttsBaseUrl || vm.ttsProvider != "builtin")
                ) {
                    item { SpeechCredentialSection(vm, vm.asrBaseUrl) }
                } else if (vm.asrBaseUrl.isNotBlank()) {
                    item {
                        Text(
                            "凭据与上面的「语音凭据」共用一份（同一家只填一次）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (vm.asrBaseUrl.isNotBlank() && !asrKeyOk) {
                    item { NoKeyHint(vm.providerLabel(vm.asrBaseUrl)) }
                }
                item {
                    InputWithPresets(
                        value = vm.asrModel,
                        onValue = { vm.asrModel = it },
                        label = "识别模型",
                        presets = ModelCatalog.asrPresets(vm.asrBaseUrl),
                        enabled = asrKeyOk
                    )
                }
                item {
                    SettingsTextField(
                        value = vm.asrLanguage,
                        onValue = { vm.asrLanguage = it },
                        label = "识别语言（选填）",
                        placeholder = "留空＝自动判断；中文可填 zh，英文 en"
                    )
                }
                item { AsrTrialButton(vm, asrKeyOk) }
                item {
                    Text(
                        uiInlineMarkdown(
                            "供应商识别把这段录音发给你选的那家做转写（硅基流动的星辰系列计费项是 free-asr-model、单价 0）；" +
                                "选了「供应商识别」但没配好时，麦克风**自动回退系统识别**并在聊天页说明原因。\n" +
                                "系统识别免费、不用 Key，但识别率与是否需要联网取决于手机厂商的识别服务。"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        "系统识别：调手机自带的语音识别（免费、不要 Key）。识别率取决于厂商服务，" +
                            "有些机型需要联网。想要更稳的中文识别就切到「供应商识别」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            }  // ← 语音识别分页到此结束
            else {
            // ────────────────────────── 朗读方式 ──────────────────────────
            item { SectionTitle("朗读方式") }
            item {
                Text(
                    uiInlineMarkdown(
                        "把 AI 回复读出来。长按任意一条角色回复 →「朗读」即可听；" +
                            "聊天页顶栏的喇叭图标控制**新回复自动朗读**（斜杠＝关闭）。"
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 「自动朗读」开关（用户 2026-09-24 桌面反馈：这一页没有这个设置项）。
            // 与聊天页顶栏喇叭写的是**同一个** settings.ttsAutoRead：这里改动要走本页「保存」落盘，
            // 喇叭是即时生效；两边互相同步（聊天页进页面重读，见 ChatViewModel.reloadFromDisk）。
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("新回复自动朗读", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后新回复边生成边读（攒够一句就开始念）；关掉则只在长按「朗读」时读。" +
                                "与聊天页顶栏的喇叭是同一个开关。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vm.ttsAutoRead,
                        onCheckedChange = { vm.ttsAutoRead = it }
                    )
                }
            }
            item {
                Column {
                    Text(
                        "语速：${String.format("%.2f", vm.ttsSpeed)}×",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = vm.ttsSpeed,
                        onValueChange = { vm.ttsSpeed = (it * 100).roundToInt() / 100f },
                        valueRange = 0.5f..2f
                    )
                }
            }
            item {
                Column {
                    Text(
                        "音高：${String.format("%.2f", vm.ttsPitch)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = vm.ttsPitch,
                        onValueChange = { vm.ttsPitch = (it * 100).roundToInt() / 100f },
                        valueRange = 0.5f..2f
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("跳过（）里的动作描写", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "本 App 的动作 / 神态 / 心理都写在（）里，默认跳过、只读对白；关掉则连描写一起读。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vm.ttsSkipActionText,
                        onCheckedChange = { vm.ttsSkipActionText = it }
                    )
                }
            }

            // ────────────────────────── 朗读引擎 ──────────────────────────
            item { SectionTitle("朗读引擎") }
            item {
                Column {
                    Text("引擎", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
            }
            if (vm.ttsProvider == "builtin") {
                item {
                    SectionTitle("语音服务")
                }
                item {
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
                        onEdit = { editingCustom = it; showCustomEditor = true }
                    )
                }
                // 这家没凭据（且不是本地 http 服务）→ 模型/音色/试听全部置灰，与该页其它模型分段同一口径
                val speechKeyOk = vm.ttsBaseUrl.isNotBlank() &&
                    (vm.hasSpeechCredentialFor(vm.ttsBaseUrl) || !vm.ttsBaseUrl.startsWith("https://"))
                if (vm.ttsBaseUrl.isNotBlank()) {
                    item { SpeechCredentialSection(vm, vm.ttsBaseUrl) }
                }
                if (vm.ttsBaseUrl.isNotBlank() && !speechKeyOk) {
                    item { NoKeyHint(vm.providerLabel(vm.ttsBaseUrl)) }
                }
                item {
                    InputWithPresets(
                        value = vm.ttsModel,
                        onValue = { m ->
                            vm.ttsModel = m
                            // 音色要跟着"模型/档位"走，但**各家的规则不一样**：
                            // - 硅基流动：音色写成「模型名:音色名」，换模型要**重写前缀**（只换回音色名会让服务端认不出）；
                            // - 其它（OpenAI / 火山 / 腾讯 / 阿里）：把音色换成本档的第一条预设，避免留着别档的 id
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
                        label = "语音合成模型",
                        presets = ModelCatalog.speechPresets(vm.ttsBaseUrl),
                        // 火山/腾讯这两栏是技术参数（Resource-Id / ModelType），下拉里给可读说明
                        presetLabel = { ModelCatalog.speechModelLabel(vm.ttsBaseUrl, it) },
                        enabled = speechKeyOk
                    )
                }
                // 声音类型：单一 / 混合 / 复刻。**类型不落库**，由下面几栏的数据推出来
                // （`CharacterVoices.kindOf`），点胶囊＝把数据改成这种类型该有的样子（vm.pickVoiceKind）。
                // 只在这家是火山时出现后两个：混音是火山专属协议、复刻是 ICL 资源。
                val volcSpeech = ProviderProfiles.speechProtocol(vm.ttsBaseUrl) ==
                    ProviderProfiles.SpeechProtocol.VOLC
                // 本机复刻音色库要进判定：录音复刻出来的 id 没有 `S_` 前缀，
                // 不查库就会被当成普通 2.0 音色 —— 档位发错，合成必 mismatch
                val cloneIds = vm.cloneIds()
                val voiceKind = CharacterVoices.kindOf(
                    vm.ttsVoice, vm.ttsModel, vm.ttsMixEnabled, vm.ttsMixSpeakers,
                    mixSupported = volcSpeech, cloneIds = cloneIds
                )
                if (volcSpeech) {
                    item {
                        VoiceKindChips(
                            kind = voiceKind,
                            allowMix = true,
                            allowClone = true,
                            enabled = speechKeyOk,
                            onPick = { vm.pickVoiceKind(it) }
                        )
                    }
                }
                item {
                    InputWithPresets(
                        value = vm.ttsVoice,
                        onValue = { v ->
                            vm.ttsVoice = v
                            // 腾讯云的音色与 ModelType 档位绑定（大模型=3 / 精品=2）：选音色时顺手把档位改对，
                            // 否则会出现"音色对了、档位不匹配"这种看不出原因的失败
                            ModelCatalog.tencentModelTypeFor(v)?.let { vm.ttsModel = it }
                            // 火山同理：音色决定 V3 的 Resource-Id（2.0→seed-tts-2.0 / 1.0→seed-tts-1.0 /
                            // 复刻 S_→seed-icl-2.0）。复刻音色就是**粘一个 id 进来**，所以这条自动配对最要紧。
                            if (ProviderProfiles.speechProtocol(vm.ttsBaseUrl) ==
                                ProviderProfiles.SpeechProtocol.VOLC
                            ) {
                                vm.ttsModel = ModelCatalog.volcResourceIdForVoice(v, cloneIds)
                            }
                        },
                        // 混合音色下 speaker 固定 `custom_mix_bigtts`，这一栏根本不参与合成 ⇒ 换成它自己的
                        // 标签，别让用户以为"填了音色就是它在响"（复刻音色是相反的：那一栏就是它的家）
                        label = if (voiceKind == CharacterVoices.VoiceKind.MIX) "音色（混合音色下不使用）" else "音色",
                        // 复刻音色那一档把「我的复刻音色」并进预设清单：自己命名的音色
                        // 前缀毫无特征，光看 id 认不出来，下拉里得显示本机名字
                        presets = if (volcSpeech) {
                            (ModelCatalog.speechVoices(vm.ttsBaseUrl, vm.ttsModel) + cloneIds).distinct()
                        } else {
                            ModelCatalog.speechVoices(vm.ttsBaseUrl, vm.ttsModel)
                        },
                        // 显示可读标签（"御姐（female-yujie）"），别让用户对着一串 id 猜
                        presetLabel = { v ->
                            vm.cloneVoiceOf(v)?.name
                                ?: ModelCatalog.speechVoiceLabel(vm.ttsBaseUrl, v)
                        },
                        enabled = speechKeyOk && voiceKind != CharacterVoices.VoiceKind.MIX
                    )
                }
                // 混音源与权重：只在这一类型下出现（原来是一个独立开关，现在由上面的胶囊承担）
                if (voiceKind == CharacterVoices.VoiceKind.MIX) {
                    item { MixSpeakerSection(vm, speechKeyOk) }
                }
                // ── 复刻音色：录音复刻 + 我的音色库──────────────────────────
                // 只在点到「复刻音色」时出现（这一档才有"录一段"这件事的意义），火山专属。
                if (volcSpeech && voiceKind == CharacterVoices.VoiceKind.CLONE) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = speechKeyOk && vm.cloneProgress == null,
                                    onClick = { showRecorder = true }
                                ) { Text("🎙 录音复刻一个新音色") }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "在 App 里录一段自己的声音（读屏幕上那句话），复刻完就能直接朗读。" +
                                        "也可以把控制台建好的音色 ID 粘在「音色」栏。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CloneVoiceBar(
                                voices = vm.ttsCloneVoices,
                                enabled = speechKeyOk && vm.cloneProgress == null,
                                onUse = { v ->
                                    vm.ttsVoice = v.id
                                    vm.ttsModel = "seed-icl-2.0"
                                    vm.noteMix("已切到「${v.name}」，点「试听」听听像不像")
                                },
                                onDelete = { vm.removeCloneVoice(it) },
                                onCheck = { vm.refreshCloneVoice(it) }
                            )
                            vm.cloneProgress?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            vm.cloneError?.let {
                                Text(
                                    "$it  ✕",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { vm.clearCloneError() }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { vm.previewTts() },
                            enabled = speechKeyOk && !vm.ttsPreviewing
                        ) {
                            Text(if (vm.ttsPreviewing) "正在合成…" else "试听")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "用当前的模型/音色读一句样例，能立刻听出音色与语速是否合适。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    vm.ttsPreviewNotice?.let { n ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$n  ✕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { vm.clearTtsPreviewNotice() }
                        )
                    }
                }
                item {
                    Text(
                        uiInlineMarkdown(
                            "合成按字符计费（单价见各家官网；硅基流动 CosyVoice2 实测 ¥0.05/千字符，一条 300 字的回复约 1.5 分）；" +
                                "同一句会命中本地缓存、不重复计费。\n" +
                                "本地部署的语音服务（Kokoro-FastAPI / Speaches / Xinverse+CosyVoice / GPT-SoVITS 包装等）" +
                                "填它的 OpenAI 兼容地址即可，可以留空 Key；**内网 http 地址仅在私有网段放行**，" +
                                "回复正文会以明文走你自己的局域网，不要把云端 Key 填在这种地址上。\n" +
                                "阿里百炼走原生接口（音色与模型版本绑定：`longxiaochun_v2` 这类要配 cosyvoice-v2 系），" +
                                "音色可以照官方《CosyVoice 音色列表》直接粘贴。\n" +
                                "火山豆包语音与腾讯云要**各自控制台的凭据**（火山新版控制台填 API Key，旧版填 AppID+AccessToken；" +
                                "腾讯用 CAM 的 SecretId+SecretKey），在「语音凭据 → 单独填一把」里填；音色 id 也都要从各自控制台复制 ——" +
                                "火山是 speaker id（如 `zh_female_xiaohe_uranus_bigtts`，Resource-Id 会按音色自动判），" +
                                "腾讯是**数字**（VoiceType，档位会自动配对：大模型 3 / 精品 2）。"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Text(
                        "系统语音合成：免费、离线、不需要任何权限，音色取决于手机" +
                            "（系统里没有中文语音包时，聊天页会提示去「系统设置 → 语言与输入 → 文字转语音」安装）。" +
                            "想要更好的音色就切到「供应商合成」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            }  // ← 闭 else（语音朗读分页）
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            EmbeddedSettingsHeader(
                title = "语音服务",
                dirty = vm.isDirty,
                onSave = { vm.save() },
                onBack = ::requestBack
            )
            body(PaddingValues(0.dp))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    // 页名 = 「语音服务」：这一页现在既是朗读（TTS）也是识别（ASR）的配置入口，
                    // 进去再分「语音朗读 / 语音识别」两个分页（用户 2026-09-17 要求）
                    title = { Text("语音服务") },
                    navigationIcon = {
                        IconButton(onClick = ::requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.save(); onBack() }) {
                            Icon(Icons.Filled.Check, contentDescription = "保存")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding -> body(padding) }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("保存更改？") },
            text = { Text("语音设置里有未保存的修改。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; vm.save(); onBack() }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃更改") }
            }
        )
    }

    if (showCustomEditor) {
        CustomProviderDialog(
            initial = editingCustom,
            onSave = { vm.upsertCustomProvider(it); showCustomEditor = false },
            onDelete = { vm.deleteCustomProvider(it); showCustomEditor = false },
            onDismiss = { showCustomEditor = false }
        )
    }

    if (showRecorder) {
        CloneRecordDialog(vm = vm, onDismiss = { showRecorder = false })
    }
}

/**
 * **录音复刻**弹层：录一段 → 上传训练 → 进音色库。
 *
 * 三处口径值得写下来（都是"用户会怎么用错"推出来的）：
 * 1. **录音本身归弹层**（麦克风是它的），**上传与训练归 ViewModel**：关掉弹层不打断训练，
 *    进度改在页面上继续显示。录音要举着手机，中途想看别的东西是很自然的动作。
 * 2. 参考文本**照着读**才发 `text`（服务端拿它做 WER 校验）：读准了复刻更像，读错整条被拒 ——
 *    被拒时服务端那边会自动去掉文本重发一次（见 `VoiceCloneService`），不让用户反复重录。
 * 3. 音色代号默认**留空＝App 自己命名**（后付费 2.0 的用法）；控制台买了槽位的用户
 *    可以把 `S_` 开头的 id 填进去，App 就只负责往里灌录音。
 */
@Composable
private fun CloneRecordDialog(vm: SettingsViewModel, onDismiss: () -> Unit) {
    var recorder by remember { mutableStateOf<com.mysticat.roleplay.data.VoiceRecorder?>(null) }
    /** 录音时长（毫秒）。按 500ms 累加，显示时取整秒 —— 不做插值动画，秒数是给用户的判断依据 */
    var elapsedMs by remember { mutableStateOf(0L) }
    var recorded by remember { mutableStateOf<java.io.File?>(null) }
    /** 试听自己那段录音用的播放器（录完先听一遍，复刻像不像一半取决于录音本身） */
    var preview by remember { mutableStateOf<com.mysticat.roleplay.data.AudioPlayerEngine?>(null) }
    var name by remember { mutableStateOf("") }
    var slotId by remember { mutableStateOf("") }
    var localErr by remember { mutableStateOf<String?>(null) }
    val seconds = (elapsedMs / 1000).toInt()

    val permission = Platform.ui.rememberMicPermissionLauncher { granted ->
        localErr = if (granted) "已获得麦克风权限，再点一次「开始录音」" else "没有麦克风权限，无法录音"
    }
    // 离开/关闭时**必须放开麦克风**：否则录音器一直占着 MIC，其它应用录不了音
    // （`AsrTrialButton` 的同一处教训，这里不能漏）
    DisposableEffect(Unit) {
        onDispose {
            recorder?.cancel()
            preview?.stopAndRelease()
        }
    }
    // 计时 + 到上限自动停：让用户一直举着手机等没有意义，也省得传一段超长音频
    LaunchedEffect(recorder) {
        val r = recorder ?: return@LaunchedEffect
        elapsedMs = 0
        while (recorder === r) {
            delay(500)
            elapsedMs += 500
            if (elapsedMs / 1000 >= VoiceCloneService.MAX_SECONDS) {
                recorder = null
                recorded = r.stop()
                break
            }
        }
    }

    fun startRecord() {
        localErr = null
        recorded = null
        elapsedMs = 0
        preview?.stopAndRelease()
        preview = null
        // 实例必须留着 —— 只有它能 stop（`start()` 返回的文件只是顺手给出来，stop() 会再给一次）
        runCatching {
            val r = Voice.recorderFactory()
            r to r.start()
        }.onSuccess { (r, _) ->
            recorder = r
            vm.clearCloneError()
        }.onFailure { localErr = "打不开麦克风：${it.message ?: "设备被占用或没有权限"}" }
    }

    fun stopRecord() {
        val r = recorder ?: return
        recorder = null
        val f = r.stop()
        if (f == null) {
            localErr = "这段太短了（至少要 ${VoiceCloneService.MIN_SECONDS} 秒），再录一次"
        } else {
            recorded = f
            if (seconds < VoiceCloneService.MIN_SECONDS) {
                localErr = "只录了约 $seconds 秒，建议至少 ${VoiceCloneService.MIN_SECONDS} 秒（越长越像）"
            }
        }
    }

    AlertDialog(
        onDismissRequest = { recorder?.cancel(); preview?.stopAndRelease(); onDismiss() },
        title = { Text("录音复刻一个新音色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "照着下面这句话自然读一遍（${VoiceCloneService.MIN_SECONDS}~${VoiceCloneService.MAX_SECONDS} 秒）。" +
                        "读得越清楚，复刻出来的声音越像你。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    VoiceCloneService.REFERENCE_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    when {
                        recorder != null -> "🔴 正在录音… $seconds 秒（点「停止」结束）"
                        recorded != null -> "已录 $seconds 秒，可以开始复刻了"
                        else -> "还没开始录"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (recorder == null) {
                        Button(
                            enabled = vm.cloneProgress == null,
                            onClick = {
                                if (Platform.ui.hasMicPermission()) {
                                    startRecord()
                                } else {
                                    localErr = "录音需要麦克风权限，请在弹窗里允许"
                                    permission()
                                }
                            }
                        ) { Text(if (recorded == null) "开始录音" else "重录") }
                    } else {
                        Button(onClick = { stopRecord() }) { Text("停止") }
                    }
                    if (recorded != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = vm.cloneProgress == null,
                            onClick = {
                                val f = recorded ?: return@TextButton
                                preview?.stopAndRelease()
                                val p = Voice.audioPlayerFactory()
                                preview = p
                                // 起播失败原本是完全静默的（界面既没声音也没提示），这里照样要说一句
                                if (!p.start(f, onFinished = { })) {
                                    preview = null
                                    localErr = p.lastError ?: "这段录音放不出来，但它仍然可以拿去复刻"
                                }
                            }
                        ) { Text("▶ 听一下") }
                    }
                }
                SettingsTextField(
                    value = name,
                    onValue = { name = it },
                    label = "音色名字（本机显示用，选填）",
                    placeholder = "留空叫「我的声音 N」"
                )
                SettingsTextField(
                    value = slotId,
                    onValue = { slotId = it },
                    label = "音色 ID（选填）",
                    placeholder = "留空＝App 新建一个；控制台买过槽位就填 S_ 开头的那个"
                )
                vm.cloneProgress?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                val err = localErr ?: vm.cloneError
                err?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "复刻按音色计费（官网口径：建一个音色收一次音色槽位费，之后每次合成再按字符算）；" +
                        "App 只在你自己点「开始复刻」时才发起上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = recorded != null && vm.cloneProgress == null,
                onClick = {
                    val f = recorded ?: return@Button
                    // 代号校验：要么是控制台的 `S_`/`icl_` 槽位，要么符合官方的自定义命名规则
                    val sid = slotId.trim()
                    if (sid.isNotEmpty() && !sid.startsWith("S_") && !sid.startsWith("icl_") &&
                        !VoiceCloneService.isValidSpeakerId(sid)
                    ) {
                        localErr = "这个音色 ID 不合规：官方要求 8~256 位、字母开头、只用字母数字与 - _" +
                            "（`S_` 开头是官方保留前缀，要自建请留空让 App 命名）"
                        return@Button
                    }
                    vm.startCloneVoice(sid, f, name)
                    // 弹层不关：上传与训练要在这里看进度；工作跑在 VM 上，关掉弹层也不会白录
                }
            ) { Text(if (vm.cloneProgress != null) "复刻中…" else "开始复刻") }
        },
        dismissButton = {
            TextButton(onClick = { recorder?.cancel(); preview?.stopAndRelease(); onDismiss() }) { Text("关闭") }
        }
    )
}

/**
 * 「试识别」（Step 3）：**点一下开录、再点一下停止并识别**。
 *
 * 与聊天页的"按住说话"刻意不同：这一页是配置页，用户要一边看结果一边核对模型/语言，
 * 按住不放腾不出手。识别结果只显示在这里，不写进聊天页输入框。
 */
@Composable
private fun AsrTrialButton(vm: SettingsViewModel, enabled: Boolean) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var recorder by remember { mutableStateOf<com.mysticat.roleplay.data.VoiceRecorder?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    val permission = Platform.ui.rememberMicPermissionLauncher { granted ->
        err = if (granted) "已获得麦克风权限，再点一次「试识别」" else "没有麦克风权限，无法试识别"
    }

    // 离开这一页（或录音中被切走）必须放开麦克风：`VoiceInput` 的类注释就是这么要求的，
    // 否则录音器一直占着 MIC，其它应用录不了音（聊天页的 VM 在 onCleared 里做了，这一页早先漏了）
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recorder?.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = enabled && !busy,
                onClick = {
                    err = null
                    result = null
                    val rec = recorder
                    if (rec == null) {
                        if (!Platform.ui.hasMicPermission()) {
                            permission()
                            return@TextButton
                        }
                        // start() 麦克风被占用等会抛 IOException：不能裸抛崩掉整页（聊天页同能力有 runCatching，见 ChatViewModel.startVoiceInput）
                        val rec = Voice.recorderFactory()
                        runCatching { rec.start() }
                            .onSuccess { recorder = rec }
                            .onFailure {
                                runCatching { rec.cancel() }
                                err = "无法开始录音：${it.message ?: "麦克风可能被其它应用占用"}"
                            }
                    } else {
                        recorder = null
                        val file = rec.stop()
                        if (file == null) {
                            err = "说话时间太短，再试一次"
                        } else {
                            busy = true
                            scope.launch {
                                try {
                                    // 与「试听」同口径：用**表单当前值**（含刚填还没保存的语音凭据），
                                    // 而不是存档 —— 否则"填了凭据没点保存就试识别"会报"还没有填 API Key"
                                    val text = AiClient.transcribe(
                                        vm.collect().copy(
                                            asrBaseUrl = vm.asrBaseUrl,
                                            asrModel = vm.asrModel,
                                            asrLanguage = vm.asrLanguage
                                        ),
                                        file
                                    )
                                    result = text.ifBlank { "（识别结果为空）" }
                                } catch (t: Throwable) {
                                    err = t.message ?: "识别失败"
                                } finally {
                                    busy = false
                                    file.delete()
                                }
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        busy -> "正在识别…"
                        recorder != null -> "停止并识别"
                        else -> "试识别"
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (recorder != null) "🎙 正在录音，说完点「停止并识别」"
                else "点一下开始录音，再说一句看看能不能识别出来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        result?.let {
            Text("识别结果：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        err?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * 「混合音色」（火山专属，2026-09-17）：把 2~3 个音色按权重混成一个人格。
 *
 * 官方口径（豆包语音"超强混音"）：`speaker=custom_mix_bigtts` + `mix_speaker.speakers[]`，
 * **最多 3 个源、权重之和为 1**；能当源的是 **1.0 系列官方音色与复刻音色**（2.0 官方不支持混音）——
 * 所以这里给的预设是 1.0 那一批（`*_moon/_mars_bigtts`），2.0 的音色混不了。
 * 权重不要求用户凑成 1，发送前按比例归一化（`AiClient.volcV3Speech`）。
 *
 * 这一段**不再自带开关**——"是不是混合音色"由上面的「声音类型」胶囊决定
 * （类型从数据推、不落库），这里只管源与权重，所以进来就一定是混合音色。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixSpeakerSection(vm: SettingsViewModel, enabled: Boolean) {
    val list = vm.ttsMixSpeakers
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("混音源与权重", style = MaterialTheme.typography.bodyMedium)
        val total = list.sumOf { it.factor.toDouble() }.takeIf { it > 0.0 } ?: 1.0
        list.forEachIndexed { i, s ->
            key(i) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    InputWithPresets(
                        value = s.voice,
                        onValue = { v ->
                            vm.ttsMixSpeakers = list.toMutableList().also { it[i] = s.copy(voice = v) }
                        },
                        label = "音色 ${i + 1}",
                        // 可混的源**也包括本机的复刻音色**（官方口径：1.0 音色与复刻音色都能混），
                        // 所以自己录的那些也要出现在下拉里 —— 否则用户只能手打那串无特征的 id
                        presets = (ModelCatalog.volcMixSourceVoices() + vm.cloneIds()).distinct(),
                        presetLabel = { voiceDisplayName(it, vm::cloneVoiceOf) },
                        enabled = enabled
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = s.factor,
                            onValueChange = { f ->
                                vm.ttsMixSpeakers = list.toMutableList()
                                    .also { it[i] = s.copy(factor = (f * 20).roundToInt() / 20f) }
                            },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        // 显示**实际占比**（已归一化），用户不必自己把权重凑成 1
                        Text(
                            "${((s.factor / total) * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = enabled && list.size < 3,
                onClick = {
                    vm.ttsMixSpeakers = list + MixSpeaker(ModelCatalog.volcMixSourceVoices().getOrElse(list.size) { "" }, 0.3f)
                }
            ) { Text("＋ 加一个音色（最多 3 个）") }
            if (list.size > 2) {
                TextButton(onClick = { vm.ttsMixSpeakers = list.dropLast(1) }) { Text("－ 去掉最后一个") }
            }
        }
        // 预设（套用 / 存 / 删）：与角色编辑器**同一份实现**（`MixPresetBar`）。
        // 抽出：用户反馈"角色专属音色也要能够用预设和存预设"——那边正是缺了这一段。
        MixPresetBar(
            presets = vm.ttsMixPresets,
            canSave = list.count { it.voice.isNotBlank() } >= 2,
            enabled = enabled,
            onApply = { vm.applyMixPreset(it) },
            onDelete = { vm.deleteMixPreset(it) },
            onSave = { vm.saveMixPreset() },
            // 预设标签里的音色名要认本机复刻音色（自己命名的 id 显示成"我的声音 1"）
            voiceLabel = { voiceDisplayName(it, vm::cloneVoiceOf) }
        )
        Text(
            "权重会按比例归一化（上面显示的百分比就是实际占比）；调好的组合可以「★ 存为预设」留着反复用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 语音凭据（Step 1，2026-09-16）：**默认共用「模型与API」里这家的 Key**，
 * 只有"想给语音单独一把"或"这家要 AppID+Token 这类多字段凭据"时才展开填。
 *
 * 字段由协议声明（`ProviderProfiles.speechCredentialFields`），所以将来接火山/腾讯
 * 只需加协议分支 + 一组字段，这个界面不用改。
 */
@Composable
private fun SpeechCredentialSection(vm: SettingsViewModel, url: String) {
    val fields = ProviderProfiles.speechCredentialFields(url)
    val hasOwn = vm.hasOwnSpeechCredential(url)
    var expanded by remember(url) { mutableStateOf(false) }
    // 多字段凭据的家（火山 AppID+Access Token、腾讯 SecretId/SecretKey）**直接展开、不提供"共用"**：
    // 「模型与API」里那套"一家一把 Key"结构上就装不下它们，显示成"正在共用那把 Key"是误导。
    val multiField = fields.any { it.key != "apiKey" }
    val showFields = expanded || hasOwn || multiField

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("语音凭据", style = MaterialTheme.typography.bodyMedium)
        if (!showFields) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "正在用「模型与API」里「${vm.providerLabel(url)}」的那把 Key。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = true }) { Text("单独填一把") }
            }
            if (fields.size > 1) {
                Text(
                    "这家的语音接口需要 ${fields.size} 个字段（${fields.joinToString(" / ") { it.label }}），" +
                        "「模型与API」里装不下，请单独填。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            fields.forEach { f ->
                // 用 key(...) 让输入框在换家时整体重建（与「API 配置」同一个理由：避免旧回调写错家）
                key(url, f.key) {
                    SettingsTextField(
                        value = vm.speechCredentialValue(url, f.key),
                        onValue = { vm.updateSpeechCredential(url, f.key, it) },
                        label = f.label,
                        secret = true
                    )
                }
                if (f.hint.isNotBlank()) {
                    Text(
                        f.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hasOwn) "已为语音单独保存（加密存在本机）"
                    else if (multiField) "还没填齐 —— 这家用的是它自己的凭据，不是「模型与API」里那把 Key"
                    else "还没填 —— 留空则继续共用「模型与API」那把 Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasOwn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (hasOwn) {
                    TextButton(onClick = { vm.clearSpeechCredential(url); expanded = false }) {
                        Text("改回共用")
                    }
                }
            }
        }
    }
}
