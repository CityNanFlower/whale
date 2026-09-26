package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysticat.roleplay.data.chatCompletion
import com.mysticat.roleplay.data.generateImage
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.forCreation
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.ChatMessage
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.CloneVoice
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.data.MixPreset
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.Security
import com.mysticat.roleplay.data.TtsCache
import com.mysticat.roleplay.data.TtsSpeaker
import com.mysticat.roleplay.data.VoiceCloneService
import com.mysticat.roleplay.ui.TtsAudition
import com.mysticat.roleplay.ui.mixPresetLabel
import com.mysticat.roleplay.ui.voiceDisplayName
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    /** 设置页分段（见 [SettingsTab]） */
    var tab by mutableStateOf(SettingsTab.API)
        private set

    fun selectTab(t: SettingsTab) {
        // 操作轨迹（CrashGuard）：这一栏是"偶发闪退"最常被点的地方，先留一笔现场
        CrashNote.note("设置页 分段=${t.label}")
        tab = t
    }

    var chatBaseUrl by mutableStateOf("")
    var chatApiKey by mutableStateOf("")
    var chatModel by mutableStateOf("")
    var temperature by mutableStateOf(0.85)
    var maxTokensText by mutableStateOf("2048")
    var historyLimitText by mutableStateOf("40")
    var extraSystemPrompt by mutableStateOf("")

    // ---- 语音朗读 TTS（P-F2 v1：系统 TTS）----
    var ttsAutoRead by mutableStateOf(false)
    var ttsSpeed by mutableStateOf(1.0f)
    var ttsPitch by mutableStateOf(1.0f)
    var ttsSkipActionText by mutableStateOf(true)
    // v2：供应商合成
    var ttsProvider by mutableStateOf("system")
    var ttsBaseUrl by mutableStateOf("")
    var ttsModel by mutableStateOf("")
    var ttsVoice by mutableStateOf("")

    // 语音输入 ASR（Step 3）
    var asrProvider by mutableStateOf("system")
    var asrBaseUrl by mutableStateOf("")
    var asrModel by mutableStateOf("")
    var asrLanguage by mutableStateOf("")
    // 麦克风用法：false＝按住说话（手机默认）/ true＝点按说话（桌面固定点按，不读这个值）
    var asrTapToTalk by mutableStateOf(false)

    // 混合音色（火山，2026-09-17）
    var ttsMixEnabled by mutableStateOf(false)
    var ttsMixSpeakers by mutableStateOf<List<MixSpeaker>>(emptyList())
    var ttsMixPresets by mutableStateOf<List<MixPreset>>(emptyList())

    // ── 声音复刻（应用内录音 → 上传训练 → 进音色库）────────────────────────
    /**
     * 本机复刻出来的音色库。**不进 [Form] / 不算未保存修改**，改了就立刻落盘——
     * 上传训练可能要几十秒，用户中途离开页面/关掉弹层是常事，把结果挂在"要按保存才生效"的表单里
     * 就是白录一次（落盘走 [saveCloneVoices]，以磁盘那份为底 copy，与 [collect] 同一套口径）。
     */
    var ttsCloneVoices by mutableStateOf<List<CloneVoice>>(emptyList())
        private set

    /** 复刻进行中的一行进度字（null = 没在跑）。弹层与页面共用它，弹层关掉也照跑 */
    var cloneProgress by mutableStateOf<String?>(null)
        private set

    /** 复刻失败的一行原因（给用户看服务端原话） */
    var cloneError by mutableStateOf<String?>(null)
        private set

    /**
     * 官方返回的**免费试听片段**（`demo_audio`）已下载到本机的临时文件；null = 还没取到/这次没给。
     * 弹层拿它放给用户听——**不用合成、不花钱**（见 `VoiceCloneService.fetchDemo`）。
     */
    var cloneDemoFile by mutableStateOf<java.io.File?>(null)
        private set

    /** 试听片段没取到时的一行原因（不影响音色本身可用） */
    var cloneDemoErr by mutableStateOf<String?>(null)
        private set

    fun clearCloneError() {
        cloneError = null
    }

    /** 在库里的音色（用来显示本机名字；不在库里返回 null） */
    fun cloneVoiceOf(id: String): CloneVoice? =
        ttsCloneVoices.firstOrNull { it.id.trim() == id.trim() }

    /** 音色库的 id 清单：判"是不是复刻音色"、定 Resource-Id 都要它（见 `CloneVoice`） */
    fun cloneIds(): List<String> = ttsCloneVoices.map { it.id }

    /** 音色库改名 / 改状态 / 增删都走这里：读磁盘那份 settings → 改一个字段 → 写回 */
    private fun saveCloneVoices(list: List<CloneVoice>) {
        ttsCloneVoices = list
        Repository.saveSettings(Repository.loadSettings().copy(ttsCloneVoices = list)) { err ->
            if (err != null) viewModelScope.launch { cloneError = "音色库保存失败：${err.message ?: "未知错误"}" }
        }
    }

    /** 新复刻出来的音色入库（同名同 id 的替换，不重复堆） */
    fun putCloneVoice(v: CloneVoice) {
        val rest = ttsCloneVoices.filterNot { it.id.trim() == v.id.trim() }
        saveCloneVoices(rest + v)
    }

    fun removeCloneVoice(v: CloneVoice) {
        saveCloneVoices(ttsCloneVoices.filterNot { it.id.trim() == v.id.trim() })
    }

    /** 把库里的某条标成"能用了"（用户在稍后点「查状态」查到训练完成时用） */
    fun markCloneReady(id: String, demoAudio: String = "") {
        val now = System.currentTimeMillis()
        saveCloneVoices(ttsCloneVoices.map {
            if (it.id.trim() != id.trim()) {
                it
            } else {
                it.copy(
                    ready = true,
                    // 官方试听片段（免费）：这次没给就留旧的（1 小时有效，见 `CloneVoice.demoFresh`）
                    demoAudio = demoAudio.ifBlank { it.demoAudio },
                    demoAt = if (demoAudio.isNotBlank()) now else it.demoAt
                )
            }
        })
    }

    /**
     * 录音复刻：上传 + 等训练。**跑在 viewModelScope**，所以弹层关掉不影响它；
     * 完成/失败都只改状态（页面与弹层读同一份），不弹模态框打断用户。
     *
     * [speakerId] 留空＝App 自己命名一个新代号（后付费 2.0 的用法）；填了＝用控制台给你的槽位。
     */
    fun startCloneVoice(speakerId: String, audio: java.io.File, name: String) {
        val settings = collect()
        val baseUrl = settings.ttsBaseUrl.trim()
        val creds = VoiceCloneService.creds(settings, baseUrl)
        if (creds == null) {
            cloneError = VoiceCloneService.missingCredsHint
            return
        }
        val format = VoiceCloneService.audioFormatOf(audio.name)
        if (format == null) {
            cloneError = "这段录音的格式（${audio.name.substringAfterLast('.', "无后缀")}）复刻接口不认，" +
                "支持 wav / mp3 / ogg / m4a / aac / pcm"
            return
        }
        val id = speakerId.trim().ifBlank { VoiceCloneService.newSpeakerId(System.currentTimeMillis()) }
        val finalName = name.trim().ifBlank { "我的声音 ${ttsCloneVoices.size + 1}" }
        val bytes = runCatching { audio.readBytes() }.getOrElse {
            cloneError = "读不到录音文件：${it.message ?: "未知错误"}"
            return
        }
        cloneError = null
        cloneProgress = "正在上传录音…"
        cloneDemoFile = null
        cloneDemoErr = null
        // 先记一条"训练中"，万一下面等超时或用户离开，这次录音也不算白录（列表里能点「查状态」接着看）
        putCloneVoice(CloneVoice(id = id, name = finalName, createdAt = System.currentTimeMillis(), ready = false))
        viewModelScope.launch {
            val r = runCatching {
                VoiceCloneService.create(creds, id, bytes, format) { cloneProgress = it }
            }
            r.onSuccess { outcome ->
                when (outcome) {
                    is VoiceCloneService.Outcome.Ready -> {
                        markCloneReady(id, outcome.demoAudio)
                        cloneProgress = null
                        // ⚠️ 措辞要区分两条路：官方口径"首次语音合成才算转正收费"，而下面那个
                        // 「试听」按钮走的就是正式合成 ⇒ 先引导去点**免费**的官方试听片段
                        noteMix(
                            "「$finalName」复刻好了，已经切到这个音色 —— " +
                                "先点「▶ 试听（官方片段，免费）」听听像不像；" +
                                "另一个「试听」走正式合成，首次会开始计费"
                        )
                        // 复刻完就直接用上：这才是用户录这段的目的，别让他再去找一遍音色栏
                        ttsProvider = "builtin"
                        ttsBaseUrl = settings.ttsBaseUrl
                        ttsModel = "seed-icl-2.0"
                        ttsVoice = id
                        if (outcome.demoAudio.isNotBlank()) fetchCloneDemo(outcome.demoAudio)
                    }
                    is VoiceCloneService.Outcome.Pending -> {
                        cloneProgress = null
                        cloneError = "${outcome.reason}——音色已经记在下面了，过一会儿点它旁边的「查状态」就行"
                    }
                }
            }.onFailure { t ->
                cloneProgress = null
                cloneError = t.message ?: "复刻失败"
            }
        }
    }

    /**
     * 下载官方返回的**免费试听片段**（`demo_audio`），供复刻完成弹层的「▶ 试听」用。
     *
     * 为什么自动取一次：这段 URL 官方只保 1 小时，而它是**唯一一条不花钱就能听到复刻结果**的路子
     * （首次「语音合成」即视为转正收费）—— 晚一步去取就可能过期了。
     */
    private fun fetchCloneDemo(url: String) {
        viewModelScope.launch {
            runCatching { VoiceCloneService.fetchDemo(url) }
                .onSuccess { bytes ->
                    runCatching {
                        val f = java.io.File.createTempFile("whale-clone-demo", ".mp3")
                        f.writeBytes(bytes)
                        cloneDemoFile = f
                    }.onFailure { cloneDemoErr = "试听片段存不下来：${it.message ?: "未知错误"}" }
                }
                .onFailure { cloneDemoErr = it.message ?: "试听片段下载失败（可以直接用一次「试听」）" }
        }
    }

    /** 查一条"训练中"的音色现在好了没（页面上那个「查状态」） */
    fun refreshCloneVoice(v: CloneVoice) {
        val settings = collect()
        val creds = VoiceCloneService.creds(settings, settings.ttsBaseUrl) ?: run {
            cloneError = VoiceCloneService.missingCredsHint
            return
        }
        cloneProgress = "正在查「${v.name}」的状态…"
        viewModelScope.launch {
            val st = runCatching { VoiceCloneService.query(creds, v.id) }.getOrNull()
            cloneProgress = null
            when {
                st == null -> cloneError = "状态没查出来（网络或凭据问题），稍后再试"
                st.ready -> {
                    markCloneReady(v.id, st.demoAudio)
                    noteMix("「${v.name}」训练完成，现在可以用了")
                }
                st.failed -> cloneError = "「${v.name}」训练失败：${st.message.ifBlank { "换一段更清晰的录音重录" }}"
                else -> cloneError = "「${v.name}」还在训练中，过一会儿再查"
            }
        }
    }

    var imageEnabled by mutableStateOf(false)
    var imageBaseUrl by mutableStateOf("")
    var imageApiKey by mutableStateOf("")
    var imageModel by mutableStateOf("")
    var imageSize by mutableStateOf("1024x1024")
    var imageFormat by mutableStateOf("b64_json")

    /** 创作供应商（2026-09-15 重构：三个模型页各自独立选供应商；留空 = 沿用对话那家） */
    var creationBaseUrl by mutableStateOf("")
    var creationModel by mutableStateOf("")

    /**
     * 「API 配置」分段的 Key 编辑状态：`apiKeyUrl` = 正在编辑哪一家的 Key，`apiKey` = 该家的 Key。
     * 重构后 API 配置**只管"供应商 + Key"**，所以这里不是"当前对话供应商"，而是独立的编辑目标；
     * 改动会即时折进 [providerKeys]（一家一份 Key），三个模型页据此判断"这家能不能用"。
     */
    var apiKeyUrl by mutableStateOf("")
    var apiKey by mutableStateOf("")

    // 思考强度：""模型默认 | "off"关闭 | "high"深度
    var chatThinking by mutableStateOf("")
    var creationThinking by mutableStateOf("")

    var error by mutableStateOf<String?>(null)
        private set

    // Keystore 降级/失效提示（Security.lastCryptoIssue 的 UI 镜像）
    var cryptoNotice by mutableStateOf<String?>(null)
        private set

    /** 切换供应商时的"已自动调整…"提示 */
    var autoConfigNotice by mutableStateOf<String?>(null)
        private set

    /** 语音页用的"一句话回执"（存了混音预设 / 套用了预设…）——复用同一条非模态提示位 */
    fun noteMix(msg: String) {
        autoConfigNotice = msg
    }

    // ── 混音预设：套用 / 存 / 删（从页面里搬到这里，与角色编辑器同一套动作）──────

    /** 把当前混音源存成一条预设。名字自动编号（"混合 1"…），用户不用为取名停下来 */
    fun saveMixPreset() {
        val src = ttsMixSpeakers.filter { it.voice.isNotBlank() }
        if (src.size < 2) {
            noteMix("混合音色至少要 2 个源音色，先点「＋ 加一个音色」")
            return
        }
        var n = ttsMixPresets.size + 1
        while (ttsMixPresets.any { it.name == "混合 $n" }) n++
        ttsMixPresets = ttsMixPresets + MixPreset("混合 $n", src)
        noteMix("已存为「混合 $n」（在下面点这个标签即可套用）")
    }

    /** 套用一条预设：源与档位一起改对（混音只能混 1.0 音色与复刻音色，档位要跟着源走） */
    fun applyMixPreset(p: MixPreset) {
        ttsMixSpeakers = p.speakers
        ttsMixEnabled = true
        ttsModel = ModelCatalog.volcModelForKind(
            CharacterVoices.VoiceKind.MIX, ttsVoice, ttsModel, p.speakers, cloneIds()
        )
        noteMix("已套用「${p.name}」：${mixPresetLabel(p) { voiceDisplayName(it, ::cloneVoiceOf) }}")
    }

    fun deleteMixPreset(p: MixPreset) {
        ttsMixPresets = ttsMixPresets - p
    }

    /** 按供应商记忆的 API Key（key = 画像 keyword 或自定义供应商 id；落盘前由 Repository 加密） */
    var providerKeys by mutableStateOf<Map<String, String>>(emptyMap())

    /**
     * 语音专用凭据（Step 1，2026-09-16）：`key = 家`、`value = 字段名→值`。
     *
     * 该家没有条目（或整组都空）= **用「模型与API」里这把 Key**，所以绝大多数用户
     * 根本不用碰这里；只有"语音想用另一把 Key"或"这家要 AppID+Token 这类多字段凭据"时才填。
     * 与 [providerKeys] 不同，这里**不做折叠**：写入直接按"家"的 id 定位（[updateSpeechCredential]），
     * 所以不会出现「切换供应商后残留回调把 A 家的 Key 写到 B 家」那个老坑。
     */
    var speechCredentials by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())

    /** 老数据的"生图专用 Key"，只读兼容（重构后一家一份 Key） */
    var imageProviderKeys by mutableStateOf<Map<String, String>>(emptyMap())

    /** 用户自定义供应商（与设置一起保存） */
    var customProviders by mutableStateOf<List<CustomProvider>>(emptyList())


    /** 当前基址对应的供应商标识（用于 Key 存取） */
    private fun keyId(url: String): String = ProviderProfiles.keyIdFor(url, customProviders)

    /**
     * 供应商显示名：**自定义供应商也要显示用户起的名字**。
     * `ProviderProfiles.nameFor` 只认内置清单，自定义供应商会退回原始 URL——
     * 在「XX 的 API Key」这种文案里露一长串网址很难看，也没法确认自己选对了哪家。
     */
    fun providerLabel(url: String): String =
        ProviderProfiles.resolve(url, customProviders)?.name ?: url.trim()

    // ---- 「API 配置」分段的 Key 管理（一家一份 Key，2026-09-15 重构）----

    /**
     * 把「API 配置」里**正在编辑**的 Key 折进映射后的结果（空 = 移除该家的 Key）。
     *
     * ⚠️ 刻意做成"按需计算"而不是"每次按键就写 map"：后者曾被一个残留的
     * onValueChange 回调把上一家的 Key 写到新选中的供应商名下（内存态被污染、磁盘却干净，
     * 查起来非常隐蔽）。现在只有真正要读的时候才折叠。
     */
    private fun foldedKeys(): Map<String, String> {
        if (apiKeyUrl.isBlank()) return providerKeys
        val id = keyId(apiKeyUrl)
        return if (apiKey.isBlank()) providerKeys - id else providerKeys + (id to apiKey.trim())
    }

    /** 当前正在编辑的那家是否已存过 Key（含输入框里还没保存的那份） */
    val hasSavedKeyForCurrentApiProvider: Boolean
        get() = foldedKeys()[keyId(apiKeyUrl)].orEmpty().isNotBlank()

    /** 在 API 配置里换一家来编辑 Key：先把当前编辑提交，再取回新那家已存的 Key（没有就置空） */
    fun selectApiKeyProvider(url: String) {
        providerKeys = foldedKeys()
        apiKeyUrl = url
        apiKey = providerKeys[keyId(url)].orEmpty().ifBlank {
            // 老数据：只有一个全局 Key，属于"它当初那家"
            if (keyId(url) == keyId(chatBaseUrl)) chatApiKey else ""
        }
    }

    /** Key 输入框的值变化：只改输入框本身，不写映射（见 [foldedKeys] 的说明） */
    fun updateApiKey(v: String) {
        apiKey = v
    }

    /** 清除当前这家保存的 Key */
    fun clearKeyForCurrentProvider() {
        providerKeys = foldedKeys() - keyId(apiKeyUrl)
        apiKey = ""
        autoConfigNotice = "已清除「${providerLabel(apiKeyUrl)}」的 Key"
    }

    /**
     * 某家供应商是否可用（三个模型页据此决定模型与参数是否置灰）：
     * `providerKeys` 非空时严格按供应商看；老数据（只有一份全局 Key）只认它当初那家。
     */
    fun hasKeyFor(url: String): Boolean {
        if (url.isBlank()) return false
        val keys = foldedKeys()
        val id = keyId(url)
        // providerKeys 非空 ⇒ 严格按供应商看（重构后"一家一份 Key"）；
        // 只有老数据（一份全局 Key）才回落到它当初那家
        if (keys.isNotEmpty()) return !keys[id].isNullOrBlank()
        return id == keyId(chatBaseUrl) && chatApiKey.isNotBlank()
    }

    // ---- 语音凭据（Step 1，2026-09-16）：按协议声明的字段读写，该家没填则回退「模型与API」----

    /** 该家是否已单独指定过语音凭据（有一个非空字段就算） */
    fun hasOwnSpeechCredential(url: String): Boolean =
        speechCredentials[keyId(url)].orEmpty().values.any { it.isNotBlank() }

    /** 写一个凭据字段：**按家的 id 定位**（不是"当前选中的那家"），切供应商不会串台 */
    fun updateSpeechCredential(url: String, field: String, value: String) {
        val id = keyId(url)
        speechCredentials = speechCredentials + (id to (speechCredentials[id].orEmpty() + (field to value)))
    }

    /** 读一个凭据字段的当前值（界面渲染输入框用；没填过返回空串） */
    fun speechCredentialValue(url: String, field: String): String =
        speechCredentials[keyId(url)].orEmpty()[field].orEmpty()

    /** 丢弃这家单独指定的语音凭据，改回用「模型与API」那把 Key */
    fun clearSpeechCredential(url: String) {
        speechCredentials = speechCredentials - keyId(url)
        autoConfigNotice = "「${providerLabel(url)}」的语音凭据已改回共用「模型与API」的 Key"
    }

    /**
     * 该家的语音接口现在能不能用（模型/音色/试听据此置灰）。
     * 结构化凭据填了就用它判断；没填则回落到 [hasKeyFor]（＝改动前口径）。
     * ⚠️ **口径必须与 `AiSettings.hasSpeechCredential` 一致**（那边是请求侧的门禁，这边是界面的置灰）：
     * 火山/腾讯是**多字段凭据**，不能套用"必填字段都填了"那条规则（火山字段全选填 ⇒ 空集恒真），
     * 也不能回退到「模型与API」里那把 Key（方舟的 ark- Key 不是豆包语音的凭据）。
     */
    fun hasSpeechCredentialFor(url: String): Boolean {
        if (url.isBlank()) return false
        val own = speechCredentials[keyId(url)].orEmpty().filterValues { it.isNotBlank() }
        when (ProviderProfiles.speechProtocol(url)) {
            ProviderProfiles.SpeechProtocol.VOLC ->
                return !own["apiKey"].isNullOrBlank() ||
                    (!own["appId"].isNullOrBlank() && !own["accessToken"].isNullOrBlank())
            ProviderProfiles.SpeechProtocol.TENCENT ->
                return !own["secretId"].isNullOrBlank() && !own["secretKey"].isNullOrBlank()
            else -> Unit
        }
        if (own.isNotEmpty()) {
            return ProviderProfiles.speechCredentialFields(url)
                .filter { it.required }
                .all { !own[it.key].isNullOrBlank() }
        }
        return hasKeyFor(url)
    }

    /**
     * 切换对话供应商：填 Base URL、按供应商取回 Key、自动匹配对话模型。
     * ⚠️ 创作/生图有各自的供应商，这里**不再连带改它们**（2026-09-15 重构：三个模型页各自独立）。
     */
    fun switchChatProvider(url: String) {
        val changes = mutableListOf<String>()
        chatBaseUrl = url
        if (hasKeyFor(url)) changes += "该供应商已存过 Key"
        val presets = ModelCatalog.chatPresets(url)
        // 空模型也填上默认值（新账号最容易遇到"没填模型就保存"）
        if (presets.isNotEmpty() && (chatModel.isBlank() || chatModel !in presets)) {
            chatModel = presets.first()
            changes += "对话模型 → ${presets.first()}"
        }
        autoConfigNotice = if (changes.isEmpty()) null else "已自动调整：" + changes.joinToString("、")
    }

    /** 切换创作供应商（独立于对话那家） */
    fun switchCreationProvider(url: String) {
        val changes = mutableListOf<String>()
        creationBaseUrl = url
        if (hasKeyFor(url)) changes += "该供应商已存过 Key"
        val presets = ModelCatalog.creationPresets(url)
        if (presets.isNotEmpty() && (creationModel.isBlank() || creationModel !in presets)) {
            creationModel = presets.first()
            changes += "创作模型 → ${presets.first()}"
        }
        autoConfigNotice = if (changes.isEmpty()) null else "已自动调整：" + changes.joinToString("、")
    }

    /**
     * 切换生图供应商：自动匹配生图模型（Key 走"一家一份"，不再有生图专用 Key 那一层）。
     *
     * ⚠️ **不改写用户的返回格式偏好**——服务商的硬性格式由界面按「强制值」显示，
     * 换到无强制要求的供应商时应恢复用户原来的选择（2026-09-14 用户反馈）。
     */
    fun switchImageProvider(url: String) {
        val changes = mutableListOf<String>()
        imageBaseUrl = url
        if (hasKeyFor(url)) changes += "该供应商已存过 Key"
        val presets = ModelCatalog.imagePresets(url)
        // 模型为空也要填上默认值：否则"点完供应商→保存"会留下空模型，
        // 生图报"请先配置生图服务"，用户看不出哪里没配（2026-09-15 实际踩到）
        if (presets.isNotEmpty() && (imageModel.isBlank() || imageModel !in presets)) {
            imageModel = presets.first()
            changes += "生图模型 → ${presets.first()}"
        }
        ProviderProfiles.resolve(url, customProviders)?.imageFormatForced?.let { forced ->
            changes += "该服务商固定使用 $forced（不会改写你的偏好设置）"
        }
        autoConfigNotice = if (changes.isEmpty()) null else "已自动调整：" + changes.joinToString("、")
    }

    fun clearAutoConfigNotice() { autoConfigNotice = null }

    /** 新增/更新/删除自定义供应商 */
    fun upsertCustomProvider(p: CustomProvider) {
        customProviders = if (customProviders.any { it.id == p.id }) {
            customProviders.map { if (it.id == p.id) p else it }
        } else customProviders + p
    }

    fun deleteCustomProvider(id: String) {
        customProviders = customProviders.filterNot { it.id == id }
    }

    // 问题 #27：进入设置页时的快照，用于"有未保存的修改就先提醒"（只比较会写入存档的字段）
    // 必须是 Compose 状态：save() 里推进它要能触发重组——普通字段的话，内嵌头部的
    // "保存"按钮和"有未保存的修改"要等别的状态变化才消失（2026-09-19 用户实测）。
    private var snapshot by mutableIntStateOf(0)

    /**
     * 表单字段的不可变快照。
     *
     * 用途：`isDirty` 要比较"表单是否被改过"，但 `BackHandler(enabled = vm.isDirty)` 是**组合期读取**，
     * 拖温度滑杆时每帧都会求值。原来实现是 `collect().hashCode()` —— 每帧都要做一次
     * `loadSettings() + 整份 AiSettings.copy() + 整对象 hashCode()`（含分类列表等非表单字段），纯浪费。
     * 改成只比较表单字段本身，派生值统一在 [form] 里算，[collect] 复用同一份。
     */
    private data class Form(
        val chatBaseUrl: String,
        val chatModel: String,
        val temperature: Double,
        val maxTokens: Int,
        val historyLimit: Int,
        val extraSystemPrompt: String,
        val imageEnabled: Boolean,
        val imageBaseUrl: String,
        val imageModel: String,
        val imageSize: String,
        val imageFormat: String,
        val creationBaseUrl: String,
        val creationModel: String,
        val chatThinking: String,
        val creationThinking: String,
        // 语音朗读（P-F2 v1）
        val ttsAutoRead: Boolean,
        val ttsSpeed: Float,
        val ttsPitch: Float,
        val ttsSkipActionText: Boolean,
        val ttsProvider: String,
        val ttsBaseUrl: String,
        val ttsModel: String,
        val ttsVoice: String,
        // 语音输入（ASR，Step 3）
        val asrProvider: String,
        val asrBaseUrl: String,
        val asrModel: String,
        val asrLanguage: String,
        val asrTapToTalk: Boolean,
        // 混合音色（火山）
        val ttsMixEnabled: Boolean,
        val ttsMixSpeakers: List<MixSpeaker>,
        val ttsMixPresets: List<MixPreset>,
        /** 已把「API 配置」里正在编辑的 Key 折进来（一家一份 Key 的唯一权威来源） */
        val providerKeys: Map<String, String>,
        /** 语音专用凭据（Step 1）：key = 供应商标识，value = 协议声明的字段组 */
        val speechCredentials: Map<String, Map<String, String>>,
        val customProviders: List<CustomProvider>
    )

    /** 当前表单 → 快照（所有派生/归一化都在这里做，[collect] 与 [isDirty] 共用） */
    private fun form(): Form = Form(
        chatBaseUrl = chatBaseUrl.trim(),
        chatModel = chatModel.trim(),
        // 温度在这里就限幅 + 取两位小数。
        // 之前只在 load 时 coerce、发送时 safeTemperature，界面显示的却是存档原值——
        // 手改过存档（或有旧版本写入的长尾值）时会出现"界面显示 5.00、实际发送 2.00"的不一致。
        temperature = (temperature.coerceIn(0.0, 2.0) * 100).roundToInt() / 100.0,
        maxTokens = maxTokensText.toIntOrNull()?.coerceIn(1, 32000) ?: 2048,
        historyLimit = historyLimitText.toIntOrNull()?.coerceIn(1, 200) ?: 40,
        extraSystemPrompt = extraSystemPrompt.trim(),
        imageEnabled = imageEnabled,
        imageBaseUrl = imageBaseUrl.trim(),
        imageModel = imageModel.trim(),
        imageSize = imageSize,
        imageFormat = imageFormat,
        creationBaseUrl = creationBaseUrl.trim(),
        creationModel = creationModel.trim(),
        chatThinking = chatThinking,
        creationThinking = creationThinking,
        ttsAutoRead = ttsAutoRead,
        ttsSpeed = ttsSpeed,
        ttsPitch = ttsPitch,
        ttsSkipActionText = ttsSkipActionText,
        ttsProvider = ttsProvider,
        ttsBaseUrl = ttsBaseUrl.trim(),
        ttsModel = ttsModel.trim(),
        ttsVoice = ttsVoice.trim(),
        asrProvider = asrProvider.ifBlank { "system" },
        asrBaseUrl = asrBaseUrl.trim(),
        asrModel = asrModel.trim(),
        asrLanguage = asrLanguage.trim(),
        asrTapToTalk = asrTapToTalk,
        ttsMixEnabled = ttsMixEnabled,
        ttsMixSpeakers = ttsMixSpeakers.map { MixSpeaker(it.voice.trim(), it.factor) },
        ttsMixPresets = ttsMixPresets.map { p -> MixPreset(p.name, p.speakers.map { MixSpeaker(it.voice.trim(), it.factor) }) },
        // 把「API 配置」里正在编辑的 Key 折进来（唯一权威来源）
        providerKeys = foldedKeys(),
        // 语音凭据：输入即写、没有中间编辑态（写入按"家"的 id 定位，不会串台，见 [form] 同名说明）
        speechCredentials = speechCredentials.mapValues { (_, fields) ->
            fields.mapValues { it.value.trim() }
        },
        customProviders = customProviders
    )

    /** 有未保存的修改（当前表单与进入页面时的快照比较） */
    val isDirty: Boolean get() = form().hashCode() != snapshot

    init {
        load(Repository.loadSettings())
        cryptoNotice = Security.lastCryptoIssue
    }

    fun load(s: AiSettings) {
        chatBaseUrl = s.chatBaseUrl
        chatApiKey = s.chatApiKey
        // 一次性迁移：deepseek-chat / deepseek-reasoner 已弃用，界面与存档统一显示现行模型名
        chatModel = when (s.chatModel) {
            "deepseek-chat", "deepseek-reasoner" -> "deepseek-flash"
            // 火山 2026-09-16 更名：界面与存档统一显示现行 id（旧 id 发出去会被服务端拒）
            "doubao-seed-2-1-pro-260628" -> "doubao-seed-2-1-pro-260915"
            else -> s.chatModel
        }
        temperature = s.temperature.coerceIn(0.0, 2.0)
        maxTokensText = s.maxTokens.toString()
        historyLimitText = s.historyLimit.toString()
        extraSystemPrompt = s.extraSystemPrompt
        imageEnabled = s.imageEnabled
        imageBaseUrl = s.imageBaseUrl
        imageModel = s.imageModel
        imageSize = s.imageSize
        imageFormat = s.imageResponseFormat
        // 生图模型为空时补一个默认值：用户在"点完供应商就保存"的情况下会留下空模型，
        // 之后生图只会报"请先配置生图服务"，而界面上看不出到底缺哪一项（2026-09-15 用户踩到）。
        // 放在 snapshot 之前，所以这种"自动补全"不会被算成未保存的修改。
        if (imageModel.isBlank()) {
            ModelCatalog.imagePresets(s.imageBaseUrl).firstOrNull()?.let { imageModel = it }
        }
        creationModel = when (s.creationModel) {
            "deepseek-chat", "deepseek-reasoner" -> "deepseek-flash"
            else -> s.creationModel
        }
        chatThinking = s.chatThinking
        creationThinking = s.creationThinking
        ttsAutoRead = s.ttsAutoRead
        ttsSpeed = s.ttsSpeed
        ttsPitch = s.ttsPitch
        ttsSkipActionText = s.ttsSkipActionText
        ttsProvider = s.ttsProvider.ifBlank { "system" }
        ttsBaseUrl = s.ttsBaseUrl
        ttsModel = s.ttsModel
        ttsVoice = s.ttsVoice
        // 载入时自愈"模型/音色栏存着别家格式的值"（换供应商时留下的脏值会被保存下来，
        // 下次进设置页照样显示：比如豆包选中却写着 FunAudioLLM/CosyVoice2-0.5B）。
        // 放在 snapshot 之前，所以自愈不会被算成"未保存的修改"
        ModelCatalog.healSpeechSelection(ttsBaseUrl, ttsModel, ttsVoice, s.ttsCloneVoices.map { it.id })?.let { (m, v) ->
            ttsModel = m
            ttsVoice = v
        }
        asrProvider = s.asrProvider.ifBlank { "system" }
        asrBaseUrl = s.asrBaseUrl
        asrModel = s.asrModel
        asrLanguage = s.asrLanguage
        asrTapToTalk = s.asrTapToTalk
        ttsMixEnabled = s.ttsMixEnabled
        ttsMixSpeakers = s.ttsMixSpeakers
        ttsMixPresets = s.ttsMixPresets
        // 复刻音色库：只读进来当界面状态（写回走 saveCloneVoices，不进 form / 不影响 isDirty）
        ttsCloneVoices = s.ttsCloneVoices
        speechCredentials = s.speechCredentials
        providerKeys = s.providerKeys
        imageProviderKeys = s.imageProviderKeys
        customProviders = s.customProviders
        // **老数据迁移（关键）**：升级前只有一个全局 chatApiKey，而重构后 collect() 是从
        // providerKeys 取 Key 的 —— 不迁移的话，老用户进一次设置页点保存就会把 Key 写成空。
        // 这里把它归到"当前对话供应商"名下，作为"一家一份 Key"的起点。
        if (providerKeys.isEmpty() && s.chatApiKey.isNotBlank()) {
            providerKeys = mapOf(keyId(s.chatBaseUrl) to s.chatApiKey)
        }
        // 创作供应商：老数据没有这个字段，用"生效值"（沿用对话那家）填进表单，
        // 这样胶囊能正确显示选中项，保存时也就把它固化下来
        creationBaseUrl = s.creationBaseUrlEffective
        // 「API 配置」默认编辑"对话那家"的 Key（多数人只配一家）
        apiKeyUrl = s.chatBaseUrl
        apiKey = s.providerKeys[keyId(s.chatBaseUrl)].orEmpty()
            .ifBlank { if (s.providerKeys.isEmpty()) s.chatApiKey else "" }
        // 问题 #27：加载完记快照（只含表单字段）
        snapshot = form().hashCode()
    }

    /**
     * 进设置页时从磁盘重读一遍（页面入口调）。
     *
     * 为什么需要：本 ViewModel 挂在 MainScreen 上、**活过整场会话**，而设置是**多处可写**的
     * ——聊天页的思考强度/自动朗读、角色编辑器的混合音色预设、控制面板的音源与音量都能改。
     * 不重读有两个症状：① 界面念旧值（在别处改完回设置页，看到的还是改之前的样子）；
     * ② 更糟——`collect()` 虽然以**存档**为底，但**表单自己拥有的字段一律覆盖**，
     * 所以下一次保存会把别处刚改的那一项**静默回滚**（用户只会看到"改了没生效"）。
     *
     * 有未保存的修改就**不读**：那等于把用户正在编辑的内容冲掉，脏值该由他自己决定存或弃。
     */
    fun reloadFromDisk() {
        if (isDirty) return
        load(Repository.loadSettings())
    }

    /**
     * 收集表单 → AiSettings。
     * ⚠️ 关键（2026-09-14 修）：以**已存档的设置**为底做 copy，只覆盖表单字段。
     * 原实现是 `AiSettings(...)` 显式构造，会把主题/字体/分类/自定义供应商等非表单字段重置为默认值
     * （从设置页点一次保存就丢），也让"按供应商记忆的 Key"存不住。
     */
    fun collect(): AiSettings {
        val f = form()
        // 注意：chatApiKey / imageApiKey 是"解析结果"，由 Repository.loadSettings() 按 providerKeys 算出来。
        // 这里把当前那家的 Key 同步进去，保证"存完立刻生效"，不必等下次重读。
        val chatId = keyId(f.chatBaseUrl)
        return Repository.loadSettings().copy(
            chatBaseUrl = f.chatBaseUrl,
            chatApiKey = f.providerKeys[chatId].orEmpty(),
            chatModel = f.chatModel,
            temperature = f.temperature,
            maxTokens = f.maxTokens,
            historyLimit = f.historyLimit,
            extraSystemPrompt = f.extraSystemPrompt,
            imageEnabled = f.imageEnabled,
            imageBaseUrl = f.imageBaseUrl,
            imageApiKey = f.providerKeys[keyId(f.imageBaseUrl)].orEmpty(),
            imageModel = f.imageModel,
            imageSize = f.imageSize,
            imageResponseFormat = f.imageFormat,
            creationBaseUrl = f.creationBaseUrl,
            creationModel = f.creationModel,
            chatThinking = f.chatThinking,
            creationThinking = f.creationThinking,
            ttsAutoRead = f.ttsAutoRead,
            ttsSpeed = f.ttsSpeed,
            ttsPitch = f.ttsPitch,
            ttsSkipActionText = f.ttsSkipActionText,
            ttsProvider = f.ttsProvider,
            ttsBaseUrl = f.ttsBaseUrl,
            ttsModel = f.ttsModel,
            ttsVoice = f.ttsVoice,
            asrProvider = f.asrProvider,
            asrBaseUrl = f.asrBaseUrl,
            asrModel = f.asrModel,
            asrLanguage = f.asrLanguage,
            asrTapToTalk = f.asrTapToTalk,
            ttsMixEnabled = f.ttsMixEnabled,
            ttsMixSpeakers = f.ttsMixSpeakers,
            ttsMixPresets = f.ttsMixPresets,
            providerKeys = f.providerKeys,
            speechCredentials = f.speechCredentials,
            customProviders = f.customProviders
        )
    }

    fun save() {
        // 加密与落盘已经搬到写入线程，所以
        // ① Keystore 的告警要等加密跑完再读；② 失败也在同一个回调里回主线程提示。
        Repository.saveSettings(collect()) { err ->
            viewModelScope.launch {
                cryptoNotice = if (err != null) {
                    "设置保存失败：${err.message ?: "未知错误"}"
                } else {
                    Security.lastCryptoIssue
                }
            }
        }
        // 快照推进到"刚保存的这份表单"：不推进的话 isDirty 永远是 true——
        // 手机端保存后立刻返回上一页看不出来，但桌面三栏是**内嵌**的（保存后页面不离开），
        // 头部就会一直挂着"有未保存的修改"。
        snapshot = form().hashCode()
    }

    // ---- 模型连接测试（对话 / 创作 / 生图 三组，各自独立）----

    /** 单个模型的测试状态 */
    data class ModelTest(val loading: Boolean = false, val ok: String? = null, val err: String? = null)

    var chatTest by mutableStateOf(ModelTest())
        private set
    var creationTest by mutableStateOf(ModelTest())
        private set
    var imageTest by mutableStateOf(ModelTest())
        private set
    var showImageTestConfirm by mutableStateOf(false)
        private set

    /** 统一的测试执行壳：置 loading → 跑 block → 写入成功/失败信息 */
    private fun runTest(set: (ModelTest) -> Unit, block: suspend () -> String) {
        set(ModelTest(loading = true))
        viewModelScope.launch {
            val r = runCatching { block() }
            r.onSuccess { set(ModelTest(ok = it)) }
                .onFailure { set(ModelTest(err = it.message ?: "测试失败")) }
        }
    }

    /** 连通性测试专用：极短的系统提示 + 关闭思考 + 少量输出，只为验证"通不通"，不烧 token */
    private val pingPrompt = "你是连通性测试助手。收到任何内容都只回复两个字：连通。"

    fun testChatModel() {
        if (chatTest.loading) return
        runTest({ chatTest = it }) {
            // 固定关闭思考 + 限输出：默认档下模型会自行思考，测试要等上百秒（2026-09-15 用户反馈）
            val s = collect().copy(maxTokens = 512, chatThinking = "off")
            AiClient.chatCompletion(s, pingPrompt, listOf(ChatMessage("user", "ping")))
            "对话模型连通（${s.chatModel}）"
        }
    }

    fun testCreationModel() {
        if (creationTest.loading) return
        runTest({ creationTest = it }) {
            // 与真实创作完全一致：走创作供应商 + 创作模型（chatCompletion 读的是 chatThinking，这里一并换掉）
            val s = collect().forCreation().copy(maxTokens = 512, chatThinking = "off")
            AiClient.chatCompletion(s, pingPrompt, listOf(ChatMessage("user", "ping")))
            "创作模型连通（${s.chatModel}）"
        }
    }

    /** 生图测试：先弹确认（会实际生成一张图，按张计费） */
    fun requestImageTest() { showImageTestConfirm = true }

    fun cancelImageTest() { showImageTestConfirm = false }

    fun testImageModel() {
        showImageTestConfirm = false
        if (imageTest.loading) return
        runTest({ imageTest = it }) {
            val settings = collect()
            // 不限尺寸（size 传空串不发字段）：部分模型有最小尺寸限制，测试连通性以不限为准；按张计费
            AiClient.generateImage(settings, "a simple blue circle on white background", "")
            "生图模型连通（已实际生成一张测试图）"
        }
    }

    fun clearError() {
        error = null
    }

    fun clearTtsPreviewNotice() {
        ttsPreviewNotice = null
    }

    // ────────────────────── 语音朗读：试听（TTS v2）──────────────────────

    /** 试听是否在合成/播放中（按钮据此禁用与显示"正在合成…"） */
    var ttsPreviewing by mutableStateOf(false)
        private set

    /** 试听用的朗读器（与聊天页各一个实例，互不干扰；设置页离开时 stop 即可） */
    private var previewSpeaker: TtsSpeaker? = null

    /**
     * 试听结果提示（非模态一行小字）。
     * 原来回退/失败走 [error] —— 那是**模态**弹窗：只是"回退到系统语音"这种提示弹一个框太重
     * （2026-09-16 实测 MiniMax 音色不对时就被弹了一次），聊天页早就改成非模态了，这里对齐。
     */
    var ttsPreviewNotice by mutableStateOf<String?>(null)
        private set

    /**
     * 试听：用当前表单里的引擎/模型/音色读一句样例。
     * 走的是与聊天页同一套 [TtsCache] 与 [AiClient.textToSpeech]，所以"试听能出声"就等于"聊天里能出声"。
     * 实现只有一份（[com.mysticat.roleplay.ui.TtsAudition]）：角色编辑器那个试听按钮走的是同一段代码。
     */
    fun previewTts() {
        if (ttsPreviewing) return
        val s = collect()
        val speaker = previewSpeaker ?: TtsSpeaker { }
            .also { previewSpeaker = it }
        ttsPreviewing = true
        viewModelScope.launch {
            try {
                TtsAudition.play(speaker, s)?.let { ttsPreviewNotice = it }
            } finally {
                ttsPreviewing = false
            }
        }
    }

    // ────────────────────── 声音类型：单一 / 混合 / 复刻──────────────────────

    /**
     * 点选「声音类型」胶囊。**类型不落库**（由数据推，见 `CharacterVoices.kindOf`），
     * 所以"点选"这件事＝把数据改成这种类型该有的样子：
     * ① 混合 → 补够 2 个源 ＋ Resource-Id 纠到 1.0 档（混音只能用 1.0 音色，留着 2.0 必报 mismatch）；
     * ② 复刻 → 关掉混音 ＋ 档位切 ICL（id 由用户粘，`S_` 开头）；
     * ③ 单一 → 关掉混音 ＋ 档位按音色回推（从混音切回来时把档位还原成与音色匹配的那一档）。
     */
    fun pickVoiceKind(kind: CharacterVoices.VoiceKind) {
        when (kind) {
            CharacterVoices.VoiceKind.MIX -> {
                ttsMixSpeakers = ModelCatalog.volcMixSeedSources(ttsMixSpeakers)
                ttsMixEnabled = true
            }
            CharacterVoices.VoiceKind.CLONE -> ttsMixEnabled = false
            CharacterVoices.VoiceKind.SINGLE -> ttsMixEnabled = false
        }
        ttsModel = ModelCatalog.volcModelForKind(kind, ttsVoice, ttsModel, ttsMixSpeakers)
    }

    override fun onCleared() {
        previewSpeaker?.stop()
        super.onCleared()
    }

}
