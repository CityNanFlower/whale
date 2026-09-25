package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.AiClient.forCreation
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
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.TtsAudition
import com.mysticat.roleplay.ui.mixPresetLabel
import com.mysticat.roleplay.ui.voiceDisplayName
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.noArgViewModelFactory
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * API 设置页（2026-09-15 重构后的结构）：
 *
 * - **API 配置**：只管"供应商 + Key"（一家一份 Key，加密存本机）。胶囊末尾可新增自定义供应商。
 * - **对话模型 / 创作模型 / 生图模型**：三页结构一致 —— 供应商胶囊 + 选模型与参数 + 接入能力卡片；
 *   每页**各自独立选供应商**，选到哪家就用哪家的 Key。
 *   该供应商没存 Key 时，该页的模型与参数全部置灰并给出提示（用户 2026-09-15 要求）。
 */
/**
 * 设置页的四个分段。
 *
 * P2-C5：原来 tab 是裸字符串，页面用 `when (tab) { "api" -> …; "chat" -> …; "creation" -> …; else -> 生图页 }`，
 * 于是**任何写错/未知的值都会被当成生图页渲染**（改动一处拼写就会静默串页，且编译器不报错）。
 * 改成枚举后 `when` 是穷尽的，漏写一个分支直接编译失败。
 */
enum class SettingsTab(val label: String) {
    API("API 配置"),
    CHAT("对话模型"),
    CREATION("创作模型"),
    IMAGE("生图模型")
    // 语音朗读（v1 的系统语音参数 + v2 的供应商合成）2026-09-16 **搬去独立的
    // [VoiceScreen]**：那一页是纯 API 配置，而"用什么声音读、朗读偏好"是使用偏好 ——
    // 入口在「我的 → 外观与设置 → 语音朗读与语音输入」。
}

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

    // ── 声音复刻（第 70 轮：应用内录音 → 上传训练 → 进音色库）────────────────────────
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
    fun markCloneReady(id: String) {
        saveCloneVoices(ttsCloneVoices.map { if (it.id.trim() == id.trim()) it.copy(ready = true) else it })
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
        // 先记一条"训练中"，万一下面等超时或用户离开，这次录音也不算白录（列表里能点「查状态」接着看）
        putCloneVoice(CloneVoice(id = id, name = finalName, createdAt = System.currentTimeMillis(), ready = false))
        viewModelScope.launch {
            val r = runCatching {
                VoiceCloneService.create(creds, id, bytes, format) { cloneProgress = it }
            }
            r.onSuccess { outcome ->
                when (outcome) {
                    is VoiceCloneService.Outcome.Ready -> {
                        markCloneReady(id)
                        cloneProgress = null
                        noteMix("「$finalName」复刻好了，已经切到这个音色 —— 点下面的「试听」听听像不像")
                        // 复刻完就直接用上：这才是用户录这段的目的，别让他再去找一遍音色栏
                        ttsProvider = "builtin"
                        ttsBaseUrl = settings.ttsBaseUrl
                        ttsModel = "seed-icl-2.0"
                        ttsVoice = id
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
                    markCloneReady(v.id)
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

    // ── 混音预设：套用 / 存 / 删（第 70 轮从页面里搬到这里，与角色编辑器同一套动作）──────

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
        // P2-E4：温度在这里就限幅 + 取两位小数。
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
        ModelCatalog.healSpeechSelection(ttsBaseUrl, ttsModel, ttsVoice)?.let { (m, v) ->
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
        // P2 批2：加密与落盘已经搬到写入线程，所以
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
        // 头部就会一直挂着"有未保存的修改"（第 31 轮）。
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

    // ────────────────────── 声音类型：单一 / 混合 / 复刻（第 69 轮）──────────────────────

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /**
     * 桌面三栏：**内嵌进第三栏**。为真时不套 Scaffold/顶栏、也不画四个分段胶囊——
     * 分段由第二栏（[DesktopProfileRail]）驱动（`vm.selectTab`），这里只画内容 + 一个窄头部（保存按钮）。
     * 手机端恒为 false，行为零变化。
     */
    embedded: Boolean = false,
    vm: SettingsViewModel = viewModel(factory = noArgViewModelFactory { SettingsViewModel() })
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf<CustomProvider?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }

    // 进页面重读一次磁盘：本 VM 活过整场会话，而设置别的页面也能改（见 reloadFromDisk 的说明）。
    // 脏着就不读，免得把用户正在编辑的内容冲掉。
    LaunchedEffect(Unit) { vm.reloadFromDisk() }

    fun requestBack() {
        if (vm.isDirty) showDiscardConfirm = true else onBack()
    }
    // 有未保存修改时，系统返回键也要走确认，避免一次误触丢掉整页配置
    WhaleBackHandler(enabled = vm.isDirty) { showDiscardConfirm = true }

    // 正文：手机/整页形态套在 Scaffold 里，桌面内嵌形态套在窄头部下面（同一份，不复制）
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 分段导航：API 配置 / 对话模型 / 创作模型 / 生图模型 ──
            // 桌面内嵌时由第二栏承担（那里是"设置分组"列表），这里不再重复画一排胶囊
            if (!embedded) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SettingsTab.entries.forEach { t ->
                            WhaleChip(
                                selected = vm.tab == t,
                                onClick = { vm.selectTab(t) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            }
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

            when (vm.tab) {
                SettingsTab.API -> {
                    item { SectionTitle("供应商与 Key（一家一份，加密存在本机）") }
                    item {
                        ProviderChips(
                            current = vm.apiKeyUrl,
                            presets = ModelCatalog.allProviderPresets(),
                            custom = vm.customProviders,
                            // API 配置要能管理**所有**供应商（对话能力、生图能力，或两者都有）
                            anyCapability = true,
                            onPick = vm::selectApiKeyProvider,
                            onEdit = { editingCustom = it; showCustomEditor = true },
                            onAddCustom = { editingCustom = null; showCustomEditor = true }
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
                                    // 文案按实况说话（台账 11）：密钥库可用才说"加密存储"，
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

                // ────────────────────────── 对话模型 ──────────────────────────
                SettingsTab.CHAT -> {
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
                            onEdit = { editingCustom = it; showCustomEditor = true }
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

                // ────────────────────────── 创作模型 ──────────────────────────
                SettingsTab.CREATION -> {
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
                            onEdit = { editingCustom = it; showCustomEditor = true }
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

                // ────────────────────────── 生图模型 ──────────────────────────
                SettingsTab.IMAGE -> {
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
                                onEdit = { editingCustom = it; showCustomEditor = true }
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
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            // 窄头部：分段名 + 未保存提示 + 保存按钮（原来挂在整页顶栏右侧）
            EmbeddedSettingsHeader(
                title = vm.tab.label,
                dirty = vm.isDirty,
                onSave = { vm.save() }
            )
            body(PaddingValues(0.dp))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    // 标题跟随当前分段（000037）：这一页是"API 配置 + 三个模型页"的合集，
                    // 原来顶栏写死「API 设置」、而「我的」入口又写「模型设置」，看起来像进错了页
                    title = { Text(vm.tab.label) },
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

    // 有未保存的修改时退出：与角色编辑页统一口径（保存并退出 / 放弃更改）
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("保存更改？") },
            // 四个分段共用同一份编辑态，用户可能只改了其中一处，所以不点名具体分段
            text = { Text("设置里有未保存的修改。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; vm.save(); onBack() }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃更改") }
            }
        )
    }

    // 自定义供应商编辑（新增/编辑/删除）
    if (showCustomEditor) {
        CustomProviderDialog(
            initial = editingCustom,
            onSave = { vm.upsertCustomProvider(it); showCustomEditor = false },
            onDelete = { vm.deleteCustomProvider(it); showCustomEditor = false },
            onDismiss = { showCustomEditor = false }
        )
    }

    // 生图连接测试确认（按张计费）
    if (vm.showImageTestConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::cancelImageTest,
            title = { Text("测试生图模型？") },
            text = { Text("将实际生成一张测试图（按张计费，不限尺寸）。继续吗？") },
            confirmButton = {
                TextButton(onClick = vm::testImageModel) { Text("生成测试图") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelImageTest) { Text("取消") }
            }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

/**
 * 桌面三栏内嵌形态的**窄头部**：分段名 + 未保存提示 + 保存按钮。
 *
 * 整页形态里这三样分别是"顶栏标题""无处安放的脏标记""顶栏右侧的对勾图标"；
 * 内嵌进第三栏后没有自己的顶栏，就需要一条横排把它们放下（设置页与语音页共用）。
 */
@Composable
internal fun EmbeddedSettingsHeader(
    title: String,
    dirty: Boolean,
    onSave: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            if (dirty) "有未保存的修改" else "已是最新",
            style = MaterialTheme.typography.labelSmall,
            color = if (dirty) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Button(onClick = onSave, enabled = dirty) { Text("保存") }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * 思考强度档位选择（2026-09-14 改造为自适应）：
 * 档位来自 [ProviderProfiles.thinkingLevels] —— 不同服务商/模型能提供的档位不同
 * （Kimi K3 关不掉、GLM-5.3 只能降强度、OpenAI 只有低/高），
 * 因此不再固定显示"默认/关闭/深度"三档，而是按当前供应商与模型渲染，并说明实际发什么参数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThinkingChips(
    label: String,
    value: String,
    levels: List<ProviderProfiles.ThinkingLevel>,
    onValue: (String) -> Unit,
    enabled: Boolean = true
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            levels.forEach { lv ->
                WhaleChip(
                    enabled = enabled,
                    selected = value == lv.mode,
                    onClick = { onValue(lv.mode) },
                    label = { Text(lv.label) }
                )
            }
        }
        levels.firstOrNull { it.mode == value }?.let {
            Text(
                it.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 「接入能力」卡片：把代码里已经固定好的适配显式告诉用户（2026-09-14 新增）。
 * 目的：用户换服务商后如果发现"返回格式被改了""温度不见了"，能在这里看到原因，
 * 而不是以为配置坏了；同时给出官方文档与价格页入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderCapabilityCard(
    chatBaseUrl: String,
    chatModel: String,
    imageBaseUrl: String,
    imageModel: String,
    custom: List<CustomProvider> = emptyList()
) {
    val chat = ProviderProfiles.resolve(chatBaseUrl, custom)
    val image = ProviderProfiles.resolve(imageBaseUrl, custom)
    Column {
        Text("当前接入能力（App 已自动适配）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        if (chat == null && image == null) {
            Text(
                "尚未匹配到已知服务商：将按 OpenAI 兼容协议通用处理（对话 /chat/completions、生图 /images/generations）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        // P2-B3：自定义供应商弹窗写着「备注会显示在能力卡片里」，此前 note 全项目无处渲染（承诺没兑现）
        (chat ?: image)?.note?.takeIf { it.isNotBlank() }?.let { CapabilityLine("备注", it) }
        chat?.let {
            CapabilityLine("对话供应商", it.name)
            CapabilityLine("思考参数", it.thinking.paramLabel)
            CapabilityLine("温度参数", if (it.temperatureSupported) "支持" else "不支持（App 已自动不发送）")
        }
        image?.let {
            CapabilityLine("生图供应商", it.name)
            CapabilityLine("生图协议", it.imageProtocol.cardText)
            CapabilityLine("尺寸格式", it.imageSizeFormat)
            CapabilityLine("返回格式", it.imageFormatForced?.let { v -> "$v（服务商固定）" } ?: "b64_json / url 均可选")
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // P2-B10：原来 `listOfNotNull(chat ?: image).firstOrNull()` 一旦 chat 命中就再也不用 image 的链接，
            // 而"对话供应商没有文档链接、生图供应商有"是常见组合（例如自定义对话端点 + 内置生图）→ 按钮整个消失。
            // 现在各取各的：文档优先 chat、没有再取 image；价格同理。
            val docs = chat?.docsUrl ?: image?.docsUrl
            val pricing = chat?.pricingUrl ?: image?.pricingUrl
            // 打不开时说一句：静默失败在用户眼里就是"按钮坏了"（2026-09-21 反馈的现象）
            val openLink: (String) -> Unit = { url ->
                if (!Platform.ui.openUrl(url)) Platform.ui.toast("无法打开链接：$url")
            }
            docs?.let { url -> TextButton(onClick = { openLink(url) }) { Text("使用文档") } }
            pricing?.let { url -> TextButton(onClick = { openLink(url) }) { Text("价格与模型页") } }
        }
        Text(
            "说明：思考参数、返回格式、尺寸写法等按服务商要求由 App 自动处理，不需要手动改；" +
                "换供应商时相关配置会自动跟随切换。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 三个模型页共用：「这家还没存 Key」的置灰提示（2026-09-15 用户要求） */
@Composable
internal fun NoKeyHint(providerName: String) {
    Text(
        "「$providerName」还没有 Key：去「API 配置」选中它、填入 Key 之后，这一页的模型与参数才能用。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun CapabilityLine(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}



/**
 * 可编辑下拉：点右侧箭头选预设，也可以直接在上面输入框手写；下方带引导说明。
 *
 * P2-E5（2026-09-15）：原来用 `ExposedDropdownMenuBox` + `menuAnchor()`。
 * 那个写法在本文件里早被判过死刑——`ModelDropdown` 上方的注释记着
 * 「menuAnchor 会让整个输入框都变成点击开关菜单，反复点输入框会与键盘/焦点打架（用户反馈：多次点击输入框会崩溃）」。
 * 而 max_tokens / 历史条数恰恰是设置页里被点最多的两个输入框，却还留着这个写法（当时只改了模型下拉）。
 * 现在与 `ModelDropdown` 统一（P2-D7 把两者共用的部分抽成 [InputWithPresets]）。
 */
@Composable
private fun EditableDropdown(
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean = true,
    presets: List<String>,
    label: String,
    helper: String
) {
    InputWithPresets(
        value = value,
        onValue = onValue,
        label = label,
        presets = presets,
        enabled = enabled
    )
    Text(
        helper,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 「输入框 + 可下拉预设」的共用基座（P2-D7）。
 *
 * [EditableDropdown]（max_tokens / 历史条数）与 [ModelDropdown]（模型）原本各写一份拷贝：
 * 输入框、独立箭头按钮、预设列表、底部"可自定义"提示完全一样，只有模型那侧多一个 👁 前缀
 * 与一个 ⚡ 连接测试按钮。
 *
 * **不要改回 `ExposedDropdownMenuBox`**：`menuAnchor()` 会让整个输入框变成"点击开关菜单"，
 * 反复点输入框会与键盘/焦点打架（用户反馈过崩溃）。输入框只负责输入，箭头只负责开列表。
 *
 * @param presetLabel 预设项的显示文案（模型列表用它给视觉模型加 👁 标记）
 * @param trailing 箭头右侧的附加控件（模型下拉放 ⚡ 连接测试）
 */
@Composable
internal fun InputWithPresets(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    presets: List<String>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    presetLabel: (String) -> String = { it },
    trailing: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValue,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                trailingIcon = {
                    IconButton(onClick = { expanded = true }, enabled = enabled) {
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "选择$label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                presets.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(presetLabel(p)) },
                        onClick = { onValue(p); expanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("可自定义：直接在上方输入框里改") },
                    enabled = false,
                    onClick = { expanded = false }
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun SettingsTextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    placeholder: String = "",
    secret: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        singleLine = true,
        enabled = enabled
    )
}

/** 对话/创作/生图模型：下拉选当前服务商常见模型，也能直接上方输入自定义模型名；
 *  [test] 与 [onTest] 非空时在右侧显示 ⚡ 连接测试按钮，结果显示在下方一行 */
@Composable
private fun ModelDropdown(
    value: String,
    onValue: (String) -> Unit,
    presets: List<String>,
    label: String = "模型",
    test: SettingsViewModel.ModelTest? = null,
    enabled: Boolean = true,
    onTest: (() -> Unit)? = null
) {
    val testButton: (@Composable () -> Unit)? = onTest?.let { run ->
        {
            IconButton(onClick = run, enabled = enabled && test?.loading != true) {
                if (test?.loading == true) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = "$label 连接测试",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    InputWithPresets(
        value = value,
        onValue = onValue,
        label = label,
        presets = presets,
        enabled = enabled,
        presetLabel = { p -> if (ModelCatalog.isVisionModel(p)) "👁 $p" else p },
        trailing = testButton
    )
    test?.ok?.let {
        Text(
            "✓ $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    test?.err?.let {
        Text(
            "✗ $it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * 供应商胶囊（2026-09-14）：内置预设 + 用户自定义，用 FlowRow 换行展示（不再横向滚动，
 * 空间充足时一眼看全）；每个自定义项可点标签右侧的「编辑」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderChips(
    current: String,
    presets: List<String>,
    custom: List<CustomProvider>,
    /** true = 对话/创作分段（只看 chatBaseUrl），false = 生图分段（只看 imageBaseUrl） */
    forChat: Boolean = true,
    /**
     * 跟随模式：要求该供应商**同时有对话与生图 URL**才列出（2026-09-15 用户要求）。
     * 跟随是"一套供应商同时管两边"，只填了对话入口的家（DeepSeek/Kimi/Gemini）列出来只会选到打不通的配置。
     */
    requireImage: Boolean = false,
    /** API 配置分段：任何一侧有 URL 就列出（要能管理所有供应商的 Key） */
    anyCapability: Boolean = false,
    onPick: (String) -> Unit,
    onEdit: (CustomProvider) -> Unit,
    /** 非空时在胶囊末尾追加「＋ 自定义供应商」（2026-09-15 用户要求把该按钮放进胶囊里） */
    onAddCustom: (() -> Unit)? = null
) {
    val cur = current.trim()
    // P1-13：只在该分段有对应 URL 的自定义供应商才渲染成胶囊（跟随模式下再加"两边都要有"）。
    // 以前两个分段共用全量列表，且点选取 `chatBaseUrl.ifBlank { imageBaseUrl }`：
    // 一个"只填了生图 URL"的供应商会出现在**对话**分段里，点它就把 chatBaseUrl 写成生图端点，
    // 而且没有自动配置提示，用户根本看不出自己配错了。
    fun urlFor(cp: CustomProvider): String = when {
        anyCapability -> cp.chatBaseUrl.trim().ifBlank { cp.imageBaseUrl.trim() }
        forChat -> cp.chatBaseUrl.trim()
        else -> cp.imageBaseUrl.trim()
    }
    val visible = custom.filter {
        urlFor(it).isNotBlank() && (!requireImage || it.imageBaseUrl.trim().isNotBlank())
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        presets.forEach { url ->
            WhaleChip(
                selected = cur == url,
                onClick = { onPick(url) },
                label = { Text(ProviderProfiles.nameFor(url), maxLines = 1) }
            )
        }
        visible.forEach { cp ->
            val url = urlFor(cp)
            WhaleChip(
                selected = cur == url,
                onClick = { onPick(url) },
                label = { Text(cp.name.ifBlank { "自定义" }, maxLines = 1) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑 ${cp.name}",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onEdit(cp) }
                    )
                }
            )
        }
        // 「＋ 自定义供应商」放在胶囊末尾（新增的供应商总是排在最后，即 OpenAI 之后）
        onAddCustom?.let { add ->
            WhaleChip(
                selected = false,
                onClick = add,
                label = { Text("＋ 自定义供应商", maxLines = 1) }
            )
        }
    }
}

/**
 * 自定义供应商编辑弹窗（2026-09-14）：让非内置平台也能被自动适配。
 * 只需要名称 + URL，能力项按平台实际协议选（不确定就保持默认）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CustomProviderDialog(
    initial: CustomProvider?,
    onSave: (CustomProvider) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var chatUrl by remember { mutableStateOf(initial?.chatBaseUrl ?: "") }
    var imageUrl by remember { mutableStateOf(initial?.imageBaseUrl ?: "") }
    var thinkingKind by remember { mutableStateOf(initial?.thinkingKind ?: "NONE") }
    var tempSupported by remember { mutableStateOf(initial?.temperatureSupported ?: true) }
    var imageFormatForced by remember { mutableStateOf(initial?.imageFormatForced ?: "") }
    var imageProtocol by remember { mutableStateOf(initial?.imageProtocol ?: "OPENAI") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    // 删供应商会丢掉用户填的 Base URL / 备注等配置（2026-09-21 统一补确认）
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete && initial != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("删除「${initial.name}」？") },
            text = { Text("这家供应商的 Base URL、备注与协议设置会被移除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete(initial.id) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } }
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增自定义供应商" else "编辑自定义供应商") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chatUrl, onValueChange = { chatUrl = it },
                    label = { Text("对话 Base URL（不用对话可留空）") }, singleLine = true,
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = imageUrl, onValueChange = { imageUrl = it },
                    label = { Text("生图 Base URL（不用生图可留空）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("思考参数（该平台用哪种开关）", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "NONE" to "不支持",
                        "THINKING_TYPE" to "thinking.type",
                        "ENABLE_THINKING" to "enable_thinking",
                        "EFFORT_LOW_HIGH" to "reasoning_effort(低/高)",
                        "EFFORT_NONE_HIGH" to "reasoning_effort(关/高)"
                    ).forEach { (v, label) ->
                        WhaleChip(
                            selected = thinkingKind == v,
                            onClick = { thinkingKind = v },
                            label = { Text(label) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tempSupported, onCheckedChange = { tempSupported = it })
                    Text("该平台接受 temperature 参数", style = MaterialTheme.typography.bodySmall)
                }
                Text("生图协议", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProviderProfiles.ImageProtocol.values().forEach { p ->
                        WhaleChip(
                            selected = imageProtocol == p.name,
                            onClick = { imageProtocol = p.name },
                            label = { Text(p.shortName) }
                        )
                    }
                }
                Text("生图返回格式", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to "跟随用户设置", "url" to "固定 url", "b64_json" to "固定 base64").forEach { (v, label) ->
                        WhaleChip(
                            selected = imageFormatForced == v,
                            onClick = { imageFormatForced = v },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("备注（会显示在能力卡片里，可选）") },
                    minLines = 2, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (chatUrl.isNotBlank() || imageUrl.isNotBlank()),
                onClick = {
                    onSave(
                        CustomProvider(
                            id = initial?.id ?: Repository.newId(),
                            name = name.trim(),
                            chatBaseUrl = chatUrl.trim(),
                            imageBaseUrl = imageUrl.trim(),
                            thinkingKind = thinkingKind,
                            temperatureSupported = tempSupported,
                            imageFormatForced = imageFormatForced,
                            imageProtocol = imageProtocol,
                            note = note.trim()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { confirmingDelete = true }) { Text("删除") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
