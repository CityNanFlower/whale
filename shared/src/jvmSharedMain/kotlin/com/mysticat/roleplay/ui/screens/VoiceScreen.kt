package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.mysticat.roleplay.data.speechRecognizerErrorText
import com.mysticat.roleplay.data.transcribe
import com.mysticat.roleplay.ui.CloneVoiceBar
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.components.WhaleSegTabs
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
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.voiceDisplayName
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
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
                .padding(padding)
                // 行表**铺满第三栏**（与「模型与 API」同一口径，用户 2026-10-08：「桌面端没有顶到最边上」）。
                // 上限只留外层 `DesktopContentBox`（≤1280dp）那一道。
                .fillMaxHeight()
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            vm.cryptoNotice?.let { notice ->
                item {
                    Text(
                        "⚠️ $notice",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalWhalePalette.current.dangerText
                    )
                }
            }
            vm.autoConfigNotice?.let { notice ->
                item {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalWhalePalette.current.accentText
                    )
                }
            }

            // ── 分页：语音朗读 / 语音识别（用户 2026-09-17 要求把这一页分成两个分页）──
            item {
                WhaleSegTabs(
                    tabs = listOf("语音朗读", "语音识别"),
                    selectedIndex = if (voiceTab == "asr") 1 else 0,
                    onSelect = { voiceTab = if (it == 1) "asr" else "tts" }
                )
            }
            // 两个分页的行表正文在 VoiceSections.kt（2026-10-08 第 23 轮换骨架：
            // 结论条 → 分组 → 行（名｜值｜动作，就地展开））
            if (voiceTab == "asr") {
                voiceAsrSection(vm, onEdit = { editingCustom = it; showCustomEditor = true })
            } else {
                voiceTtsSection(
                    vm,
                    onEdit = { editingCustom = it; showCustomEditor = true },
                    onRecord = { showRecorder = true }
                )
            }
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
                WhalePageTopBar(
                    // 页名 = 「语音服务」：这一页现在既是朗读（TTS）也是识别（ASR）的配置入口，
                    // 进去再分「语音朗读 / 语音识别」两个分页（用户 2026-09-17 要求）
                    title = "语音服务",
                    navigationIcon = {
                        IconButton(onClick = ::requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 语音服务**没有**场景级模型胶囊（用户 2026-10-10「语音服务不需要加修改模型」）：
                        // 这一页本身就是 TTS 模型/音色的配置页，再加一层"场景覆盖"是重复入口。
                        IconButton(onClick = { vm.save(); onBack() }) {
                            Icon(Icons.Filled.Check, contentDescription = "保存")
                        }
                    }
                )
            }
        ) { padding -> body(padding) }
    }

    if (showDiscardConfirm) {
        // 与设置页同一处口径（241 收编）：点外/拖走/返回＝留在页内，只有「放弃更改」钮才不保存退出
        WhaleConfirmSheet(
            title = "保存更改？",
            body = "语音设置里有未保存的修改。",
            confirmLabel = "保存并退出",
            onConfirm = { showDiscardConfirm = false; vm.save(); onBack() },
            dismissLabel = "放弃更改",
            onDismissButton = { showDiscardConfirm = false; onBack() },
            onDismiss = { showDiscardConfirm = false },
            confirmDanger = false
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
                    color = LocalWhalePalette.current.accentText
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
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalWhalePalette.current.accentText)
                }
                // 复刻完成后官方会给一段**免费**试听片段（`demo_audio`，1 小时有效）：官方口径是
                // **首次「语音合成」才算转正收费**，所以"先听听像不像"这一步不该走合成（那是要花钱的那条）
                vm.cloneDemoFile?.let { f ->
                    TextButton(onClick = {
                        preview?.stopAndRelease()
                        val p = Voice.audioPlayerFactory()
                        preview = p
                        if (!p.start(f, onFinished = { })) {
                            preview = null
                            localErr = p.lastError ?: "官方试听片段放不出来（可以直接用一次「试听」）"
                        }
                    }) { Text("▶ 试听复刻结果（官方片段，免费）") }
                }
                vm.cloneDemoErr?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val err = localErr ?: vm.cloneError
                err?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalWhalePalette.current.dangerText)
                }
                Text(
                    "复刻按音色计费（官网口径：建一个音色收一次音色槽位费，之后每次合成再按字符算）；" +
                        "App 只在你自己点「开始复刻」时才发起上传。" +
                        "复刻完官方会回一段**免费**试听片段（上面那个按钮放的就是它）——" +
                        "首次「语音合成」才算转正收费，所以想先听像不像，优先点它。",
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
                            "（S_ 开头是官方保留前缀，要自建请留空让 App 命名）"
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
 * 「试识别」（Step 3）：**点一下开录、再点一下停止并识别**；系统识别分支（[systemEngine]）
 * 是"点一下开始听、说完自动出结果"——引擎是 [Voice.recognizer]（平台注入，与聊天页同一条链路），
 * 识别中再点一下＝「我说完了」，不说等几秒也会自己结束。
 *
 * 与聊天页的"按住说话"刻意不同：这一页是配置页，用户要一边看结果一边核对模型/语言，
 * 按住不放腾不出手。识别结果只显示在这里，不写进聊天页输入框。
 */
@Composable
internal fun AsrTrialButton(vm: SettingsViewModel, enabled: Boolean, systemEngine: Boolean = false) {
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
    androidx.compose.runtime.DisposableEffect(systemEngine) {
        onDispose {
            recorder?.cancel()
            // 系统识别的实例是 Voice 上的单例（聊天页共用）：只在自己启动过时收掉，别把别人的会话 cancel 掉
            if (systemEngine) Voice.recognizer.cancel()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                // 系统识别中保持可点＝「我说完了」；供应商识别的转写阶段没法取消，照旧置灰
                enabled = enabled && (!busy || systemEngine),
                onClick = {
                    err = null
                    result = null
                    if (systemEngine) {
                        val rec = Voice.recognizer
                        if (busy) {
                            rec.stopListening()
                            return@TextButton
                        }
                        if (!rec.isAvailable()) {
                            err = "这台设备没有可用的系统语音识别（可以在上面切到供应商识别）"
                            return@TextButton
                        }
                        if (!Platform.ui.hasMicPermission()) {
                            permission()
                            return@TextButton
                        }
                        busy = true
                        runCatching {
                            rec.listen(
                                vm.asrLanguage,
                                onResult = { text ->
                                    busy = false
                                    result = text.ifBlank { "（识别结果为空）" }
                                },
                                onError = { code ->
                                    busy = false
                                    err = "系统识别失败：" + speechRecognizerErrorText(code)
                                }
                            )
                        }.onFailure {
                            busy = false
                            err = "系统识别启动失败：${it.message ?: "未知错误"}"
                        }
                        return@TextButton
                    }
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
                        busy -> if (systemEngine) "停止收音" else "正在识别…"
                        recorder != null -> "停止并识别"
                        else -> "试识别"
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    systemEngine && busy -> "🎙 正在听，说完点「停止收音」，不说等几秒也会自动结束"
                    recorder != null -> "🎙 正在录音，说完点「停止并识别」"
                    systemEngine -> "点一下开始，用系统识别说一句话试试。"
                    else -> "点一下开始录音，再说一句看看能不能识别出来。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        result?.let {
            Text("识别结果：$it", style = MaterialTheme.typography.bodySmall, color = LocalWhalePalette.current.accentText)
        }
        err?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = LocalWhalePalette.current.dangerText)
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
internal fun MixSpeakerSection(vm: SettingsViewModel, enabled: Boolean) {
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
internal fun SpeechCredentialSection(vm: SettingsViewModel, url: String) {
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
                    color = if (hasOwn) LocalWhalePalette.current.accentText
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
