package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.CharacterVoice
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.CloneVoice
import com.mysticat.roleplay.data.MixPreset
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.CharacterFormTags
import com.mysticat.roleplay.data.EditorField
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.OutputFormats
import com.mysticat.roleplay.data.PlayOptionChoices
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.TtsSpeaker
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEngine
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.AiActionButton
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.writeFailureToast
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.CropDialog
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.GeneratedImageDialog
import com.mysticat.roleplay.ui.ImagePreviewDialog
import com.mysticat.roleplay.ui.ImageSizeOptions
import com.mysticat.roleplay.ui.PromptDialog
import com.mysticat.roleplay.ui.TtsAudition
import com.mysticat.roleplay.ui.MixPresetBar
import com.mysticat.roleplay.ui.VoiceKindChips
import com.mysticat.roleplay.ui.mixPresetLabel
import com.mysticat.roleplay.ui.voiceDisplayName
import com.mysticat.roleplay.ui.CardExportDialog
import com.mysticat.roleplay.ui.WorldBookEditorSection
import com.mysticat.roleplay.ui.DesktopDragDrop
import com.mysticat.roleplay.ui.defaultBgImageSize
import com.mysticat.roleplay.ui.rememberBgCropAspect
import com.mysticat.roleplay.ui.rememberGallerySaver
import com.mysticat.roleplay.ui.rememberImagePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class CharacterEditorViewModel(private val characterId: String) : ViewModel() {

    var name by mutableStateOf("")
    var tagline by mutableStateOf("")
    var persona by mutableStateOf("")
    var scenario by mutableStateOf("")

    // ── v2 规范字段（E1 角色卡引擎，2026-09-22）：各自独立编辑 ────────────────────
    // 取用口径（别在别处重推）：这三个**任一非空就走分层装配**，全空才把 [persona] 整块注入
    // （`CharacterCard.hasLayeredPersona()`）。老卡因此一字不变。
    var description by mutableStateOf("")
    var personality by mutableStateOf("")
    var mesExample by mutableStateOf("")
    /** 展示字段：不进 prompt，导出时写回标准字段 */
    var creatorNotes by mutableStateOf("")
    var creator by mutableStateOf("")
    var characterVersion by mutableStateOf("")
    /** v2 `system_prompt`：只保真不注入（理由见 `CharacterCard.systemPrompt`） */
    var systemPrompt by mutableStateOf("")
    /** v2 `post_history_instructions`：注入到最后一条用户消息末尾 */
    var postHistory by mutableStateOf("")
    /** 工具形态（E4）的两栏：本轮只有字段与装配，编辑器 UI 随 E4 一起做 */
    var taskBrief by mutableStateOf("")
    var outputFormat by mutableStateOf("")
    /** 玩法形态（E4 拆档）的三栏：规则（硬约束）/ 状态项 / 每轮选项数 */
    var playRules by mutableStateOf("")
    var playState by mutableStateOf("")
    var playOptions by mutableStateOf(0)
    /**
     * 别人的 `extensions` 原文：**编辑器里没有界面**，但保存时必须原样带回去——
     * 不然"导入别人的卡 → 改个名字 → 保存"就把人家扩展里的数据清空了。
     */
    var extensionsRaw by mutableStateOf("")
    var greeting by mutableStateOf("")
    // 其他开场白（多开场白：新会话随机抽一条；第一个开场白就是 greeting）
    var extraGreetings by mutableStateOf(listOf(""))

    // ── 世界书（E3 下半，第 99 轮）─────────────────────────────────────────────
    // 一条卡内的书，字段口径全在 `data/WorldBook.kt`；这里只管编辑态。
    // `bookExtraRaw` 没有界面：它是导入时留下的**未识别字段原文**（v2 规范要求不许销毁），
    // 保存时必须原样带回去——否则"导入别人的书 → 改个名字 → 保存"就把人家没实现的设置抹了。
    var bookName by mutableStateOf("")
    var bookDesc by mutableStateOf("")
    /** 书级默认扫描深度；`null` = 跟随引擎默认（4 条消息） */
    var bookScanDepth by mutableStateOf<Int?>(null)
    var bookEntries by mutableStateOf<List<WorldBookEntry>>(emptyList())
    var bookExtraRaw by mutableStateOf("")
    // 形态标签：""陪伴（默认）| "experience"体验
    var formTag by mutableStateOf("")
    var categories by mutableStateOf(listOf("其他"))
    var avatarUri by mutableStateOf<String?>(null)
    /** 手机端默认背景（＝历史字段 `CharacterCard.backgroundUri`） */
    var backgroundUri by mutableStateOf<String?>(null)
    /** 桌面端默认背景（`null` = 未单独设过 → 继承手机端那份；`""` = 明确不要，见 ui/ChatBackgrounds.kt） */
    var backgroundUriDesktop by mutableStateOf<String?>(null)

    // ── 角色专属音色（E 批次，2026-09-21）───────────────────────────────────────
    // 字段与设置页「语音朗读」那一套同名同口径（provider/baseUrl/model/voice/speed/pitch），
    // 落库形状见 CharacterVoice；"生效"的判定一律交给 CharacterVoices，这里只管编辑。
    var voiceEnabled by mutableStateOf(false)
    /** 供应商关键词（siliconflow / volcengine …）——"认家"用，随地址一起写 */
    var voiceProvider by mutableStateOf("")
    var voiceBaseUrl by mutableStateOf("")
    var voiceModel by mutableStateOf("")
    var voiceVoice by mutableStateOf("")
    var voiceSpeed by mutableStateOf(1.0f)
    var voicePitch by mutableStateOf(1.0f)
    /** 混出来的人格（火山，2026-09-22 用户反馈"角色专属音色也要支持混合音色"） */
    var voiceMixEnabled by mutableStateOf(false)
    var voiceMixSpeakers by mutableStateOf(listOf<MixSpeaker>())

    /**
     * **本机复刻音色库**（第 70 轮）。判定"这个 id 是不是复刻音色"、定 Resource-Id、
     * 以及「音色」栏的预设清单都要它 —— 录音复刻出来的 id 没有 `S_` 前缀，不查库就会配错档位。
     *
     * 一次性读进内存（页面创建时读一次）：`loadSettings()` 要读盘、还要解一次凭据，
     * 不能每帧调。本页只读不写这份库（新建在设置页「语音服务」里做）。
     */
    var cloneVoices by mutableStateOf<List<CloneVoice>>(emptyList())
        private set

    fun cloneIds(): List<String> = cloneVoices.map { it.id }

    fun cloneVoiceOf(id: String): CloneVoice? =
        cloneVoices.firstOrNull { it.id.trim() == id.trim() }

    /** 全局混音预设库（**存在全局设置里、两个页面共用一份**，见 `MixPresetBar`） */
    var mixPresets by mutableStateOf<List<MixPreset>>(emptyList())
        private set

    /**
     * 当前编辑态有没有吃到 v2 分层字段（判据与 `CharacterCard.hasLayeredPersona()` 一致）。
     * 编辑页那一行提示用它——"这段老合并人设还在不在生效"必须与装配器说同一句话。
     */
    fun hasLayeredFields(): Boolean =
        description.isNotBlank() || personality.isNotBlank() || mesExample.isNotBlank()

    /**
     * 某一端**实际生效**的默认背景（含"桌面端未单独设过 → 用手机端那张"）。
     * 槽位预览用它：用户要看到的是"这一端会拿哪张图"，而不是"这个字段里存了什么"。
     */
    fun slotBg(target: BgTarget): String? =
        if (target == BgTarget.Desktop) backgroundUriDesktop ?: backgroundUri else backgroundUri

    /** 写某一端的默认背景（写的是字段本身，不是生效值） */
    fun setBg(target: BgTarget, uri: String?) {
        if (target == BgTarget.Desktop) backgroundUriDesktop = uri else backgroundUri = uri
    }

    /**
     * 当前编辑态的音色（保存与导出都用它）。`null` = 连一个字段都没填过 ——
     * 这种卡不该在文件里多一段空节点，也不该让"从没设过"和"设过但关着"混为一谈。
     */
    fun currentVoice(): CharacterVoice? = CharacterVoice(
        enabled = voiceEnabled,
        // 家从地址现算；算不出来（自定义 / 本地部署）才沿用导入时带进来的那个关键词——
        // 卡的 provider 是给"换机器后认家"用的，所以能写准就写准，写不准也别抹掉对方的
        provider = ModelCatalog.speechProviderKeyword(voiceBaseUrl).orEmpty().ifBlank { voiceProvider },
        baseUrl = voiceBaseUrl.trim(),
        model = voiceModel.trim(),
        voice = voiceVoice.trim(),
        speed = voiceSpeed,
        pitch = voicePitch,
        mixEnabled = voiceMixEnabled,
        // 存的时候就把空条目丢掉（与导出/生效判定同一份口径 `mixSources()`），
        // 免得"空行"被当成一个源、界面上显示 3 个源却只有 2 个在响
        mixSpeakers = voiceMixSpeakers.filter { it.voice.isNotBlank() }.take(3)
    ).takeIf { !it.isEmpty() }

    /**
     * 音色开关：打开时**把设置页当前的音色抄一份当起点**（已经填过就不动）。
     * 不这么做的话，用户开了开关还得从供应商开始重配一遍——而九成场景只是想"给这个角色换个声音"。
     *
     * ⚠ 名字不能叫 `setVoiceEnabled`：它和 `var voiceEnabled` 生成的 setter **JVM 签名相同**，
     * Kotlin 会以 "Platform declaration clash" 直接编译失败（不是重载，是同一个签名）。
     */
    fun updateVoiceEnabled(on: Boolean) {
        voiceEnabled = on
        if (!on || voiceVoice.isNotBlank() || voiceBaseUrl.isNotBlank()) return
        val g = Repository.loadSettings()
        voiceBaseUrl = g.ttsBaseUrl
        voiceProvider = ModelCatalog.speechProviderKeyword(g.ttsBaseUrl).orEmpty()
        voiceModel = g.ttsModel
        voiceVoice = g.ttsVoice
        voiceSpeed = g.ttsSpeed
        voicePitch = g.ttsPitch
        // 混音也一起抄：全局正混着的时候开这个角色的音色，抄过来的若是"单音色"，
        // 一开开关声音就变了——用户会以为"这个角色没继承我刚才的配置"
        voiceMixEnabled = g.ttsMixEnabled
        voiceMixSpeakers = g.ttsMixSpeakers
    }

    /**
     * 换合成地址（胶囊点选或手填都走它）：**只有换了家才重置模型/音色**。
     * 留着上一家的 id 发过去必然 400（各家音色池不通用），所以跨家要换；
     * 同一家内改域名片段（手打地址时会经过若干中间态）不动用户已经选好的东西。
     */
    fun updateVoiceBaseUrl(url: String) {
        val before = ModelCatalog.speechProviderKeyword(voiceBaseUrl)
        voiceBaseUrl = url
        val after = ModelCatalog.speechProviderKeyword(url) ?: return
        if (after == before) return
        ModelCatalog.speechPresets(url).firstOrNull()?.let { m ->
            voiceModel = m
            voiceVoice = ModelCatalog.speechVoices(url, m).firstOrNull().orEmpty()
        }
    }

    /** 换模型：音色跟着"模型 / 档位"走（各家的规则不一样，口径与设置页逐条对齐） */
    fun updateVoiceModel(m: String) {
        voiceModel = m
        // 硅基流动的音色写成「模型名:音色名」，换模型要**重写前缀**（只换回音色名服务端认不出）
        if (voiceKeyword() == "siliconflow") {
            val bare = voiceVoice.substringAfterLast(':')
            if (bare.isNotBlank()) voiceVoice = "$m:$bare"
            return
        }
        val presets = ModelCatalog.speechVoices(voiceBaseUrl, m)
        if (presets.isNotEmpty() && voiceVoice !in presets) voiceVoice = presets.first()
    }

    /** 换音色：腾讯按音色定 ModelType 档位、火山按音色定 Resource-Id，顺手改对（与设置页同一口径） */
    fun updateVoiceVoice(v: String) {
        voiceVoice = v
        ModelCatalog.tencentModelTypeFor(v)?.let { voiceModel = it }
        if (ProviderProfiles.speechProtocol(voiceBaseUrl) == ProviderProfiles.SpeechProtocol.VOLC) {
            // 本机复刻音色库要一起传：录音复刻生成的 id 没有前缀特征（见 `CloneVoice`）
            voiceModel = ModelCatalog.volcResourceIdForVoice(v, cloneIds())
        }
    }

    /** 当前这家是谁（地址现算，算不出才用导入带进来的关键词） */
    private fun voiceKeyword(): String =
        ModelCatalog.speechProviderKeyword(voiceBaseUrl).orEmpty().ifBlank { voiceProvider.trim() }

    /**
     * 当前编辑态的音色在本机能不能落地（页面那一行提示用）。
     * 只在开关打开时才算；`derivedStateOf` 缓存住 —— 它要读一遍设置（磁盘），不能每帧重跑。
     */
    private val voiceStatusState = derivedStateOf {
        if (!voiceEnabled) null else currentVoice()?.let { CharacterVoices.availability(Repository.loadSettings(), it) }
    }

    val voiceStatus: CharacterVoices.Availability? get() = voiceStatusState.value

    // ── 声音类型与试听（第 69 轮）──────────────────────────────────────────────
    // 类型不落库：由上面那几栏的数据推（`CharacterVoices.kindOf`）。点胶囊＝把数据改成这种类型该有的样子。

    /** 点选「声音类型」胶囊：与设置页逐条同口径（混音补够源 + 档位纠到 1.0；复刻切 ICL；单一按音色回推） */
    fun pickVoiceKind(kind: CharacterVoices.VoiceKind) {
        when (kind) {
            CharacterVoices.VoiceKind.MIX -> {
                voiceMixSpeakers = ModelCatalog.volcMixSeedSources(voiceMixSpeakers)
                voiceMixEnabled = true
            }
            CharacterVoices.VoiceKind.CLONE -> voiceMixEnabled = false
            CharacterVoices.VoiceKind.SINGLE -> voiceMixEnabled = false
        }
        voiceModel = ModelCatalog.volcModelForKind(kind, voiceVoice, voiceModel, voiceMixSpeakers, cloneIds())
    }

    // ── 混音预设：用 / 存 / 删（第 70 轮，用户反馈"角色专属音色也要能够用预设和存预设"）────
    // 预设库是**全局共用的那一份**（`AiSettings.ttsMixPresets`）：配方本来就该跨角色复用，
    // 在设置页存的和在这里存的必须是同一个库，否则"我在A卡下调好的组合，换B卡就找不到了"。
    // 写盘走 Repository.saveSettings（以磁盘那份为底 copy），所以不会碰到本页未保存的卡内容。

    /** 收据：一行非模态小字（复用试听那条提示位，与设置页的 `noteMix` 同一用途） */
    var voiceNotice by mutableStateOf<String?>(null)
        private set

    fun clearVoiceNotice() {
        voiceNotice = null
    }

    /** 存当前混音源为预设（名字自动编号，用户不用为取名停下来） */
    fun saveMixPreset() {
        val src = voiceMixSpeakers.filter { it.voice.isNotBlank() }
        if (src.size < 2) {
            voiceNotice = "混合音色至少要 2 个源音色，先点「＋ 加一个音色」"
            return
        }
        var n = mixPresets.size + 1
        while (mixPresets.any { it.name == "混合 $n" }) n++
        val added = MixPreset("混合 $n", src)
        writeMixPresets(mixPresets + added)
        voiceNotice = "已存为「混合 $n」（在下面点这个标签即可套用）"
    }

    /** 套用一条预设：源与档位一起改对（混音只能混 1.0 音色与复刻音色，档位要跟着源走） */
    fun applyMixPreset(p: MixPreset) {
        voiceMixSpeakers = p.speakers
        voiceMixEnabled = true
        voiceModel = ModelCatalog.volcModelForKind(
            CharacterVoices.VoiceKind.MIX, voiceVoice, voiceModel, p.speakers, cloneIds()
        )
        voiceNotice = "已套用「${p.name}」：${mixPresetLabel(p) { voiceDisplayName(it, ::cloneVoiceOf) }}"
    }

    fun deleteMixPreset(p: MixPreset) {
        writeMixPresets(mixPresets - p)
    }

    /** 预设库写盘 + 同步内存态；失败也不静默（否则用户以为存住了、下次换卡就没有） */
    private fun writeMixPresets(list: List<MixPreset>) {
        mixPresets = list
        Repository.saveSettings(Repository.loadSettings().copy(ttsMixPresets = list)) { err ->
            if (err != null) viewModelScope.launch { voiceNotice = "预设保存失败：${err.message ?: "未知错误"}" }
        }
    }

    /** 这张卡的音色会不会真的生效（`effective` 会返回卡那一份，而不是原样退回全局） */
    fun voiceApplies(): Boolean = currentVoice()?.let {
        CharacterVoices.applies(Repository.loadSettings(), previewCard(it))
    } == true

    /** 试听用的临时卡：只带音色块，别的字段不参与（音色解析只吃 `voice`） */
    private fun previewCard(v: CharacterVoice) = CharacterCard(id = characterId, name = name, voice = v)

    /** 试听是否在合成/播放中（按钮据此禁用与显示"正在合成…"） */
    var voicePreviewing by mutableStateOf(false)
        private set

    /** 试听结果提示（非模态一行小字；与设置页同一个口径，失败不当模态弹窗） */
    var voicePreviewNotice by mutableStateOf<String?>(null)
        private set

    /** 试听用的朗读器（本页一个实例；离开页面时释放） */
    private var voicePreviewSpeaker: TtsSpeaker? = null

    fun clearVoicePreviewNotice() {
        voicePreviewNotice = null
    }

    /**
     * 试听**这一张卡的**音色：走的是朗读链路同一份解析（[CharacterVoices.effective]），
     * 所以"试听是这把声音"就等于"聊天里朗读也是这把声音"。
     *
     * ⚠ 与设置页那个试听**互不影响**：这里只读全局凭据、绝不写全局设置 —— 卡里配的音色是本卡专属，
     * 试听也只是把它合出来听一遍（合成用的临时设置不会落库）。
     */
    fun previewVoice() {
        if (voicePreviewing) return
        val v = currentVoice()
        if (v == null) {
            voicePreviewNotice = "还没有填这个角色的音色：先选供应商，再挑音色（或混合音色 / 复刻音色）"
            return
        }
        val global = Repository.loadSettings()
        val card = previewCard(v)
        val eff = CharacterVoices.effective(global, card)
        // 解析后仍等于全局 ⇒ 卡的音色没生效（缺地址 / 只有 1 个混音源）：这时试听会放出**全局音色**，
        // 不说清就会变成"设了没生效"的假象，所以宁可先拦下来并说明原因
        if (eff === global) {
            voicePreviewNotice = "这张卡的音色还不足以生效（常见原因：没选供应商，或混合音色只有 1 个源）；" +
                "现在试听会放出设置页那套全局音色，先补齐上面的配置。"
            return
        }
        val speaker = voicePreviewSpeaker ?: TtsSpeaker { }
            .also { voicePreviewSpeaker = it }
        voicePreviewNotice = null
        voicePreviewing = true
        viewModelScope.launch {
            try {
                TtsAudition.play(speaker, eff)?.let { voicePreviewNotice = it }
            } finally {
                voicePreviewing = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 试听中的音频要跟着页面一起停：不然退出编辑页后那几秒钟还在响（且没有入口再停它）
        runCatching { voicePreviewSpeaker?.shutdown() }
        voicePreviewSpeaker = null
    }

    var isNew by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var generating by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var showPromptFor by mutableStateOf<String?>(null) // "avatar" | "background"
    var showDeleteConfirm by mutableStateOf(false)

    /** 0.1.2：AI 按角色卡设定起草生图提示词 */
    var draftingPrompt by mutableStateOf(false)
        private set
    var promptDraft by mutableStateOf<String?>(null)

    /**
     * 按当前角色卡设定起草头像 / 背景提示词（走创作模型），结果经 [promptDraft] 回填到描述弹窗。
     * 每次起草前清空旧值，保证「改了角色设定再起草」能拿到新结果（LaunchedEffect 依赖值变化）。
     */
    fun draftPrompt(kind: String) {
        if (draftingPrompt) return
        draftingPrompt = true
        promptDraft = null
        error = null
        viewModelScope.launch {
            try {
                val settings = Repository.loadSettings()
                promptDraft = AiClient.draftImagePrompt(
                    settings,
                    kind,
                    collectCard(),
                    // 桌面端背景是给横窗口用的（A 批次）
                    landscape = BgTarget.ofKind(kind) == BgTarget.Desktop
                )
            } catch (t: Throwable) {
                error = t.message ?: "起草失败，请检查创作模型配置"
            } finally {
                draftingPrompt = false
            }
        }
    }

    /** 已生成、等待用户确认的图片（问题 #8） */
    var pendingUri by mutableStateOf<String?>(null)
        private set

    /** 裁剪基准原图（问题 #13）：反复进「编辑」始终基于最初原图重裁；
     *  只有换上全新图片（本地上传 / AI 生成 / 加载角色卡）时才重置 */
    var avatarCropSource: String? = null
    /** 背景的裁剪基准原图，**两端各一份**（A 批次分端后，两端是两张不同的图） */
    var backgroundCropSource: String? = null
    var backgroundCropSourceDesktop: String? = null

    /** 读某一端的裁剪基准原图 */
    fun cropSourceOf(target: BgTarget): String? =
        if (target == BgTarget.Desktop) backgroundCropSourceDesktop else backgroundCropSource

    /** 写某一端的裁剪基准原图（null = 清空，下次改从当前图重新起裁） */
    fun setCropSource(target: BgTarget, uri: String?) {
        if (target == BgTarget.Desktop) backgroundCropSourceDesktop = uri else backgroundCropSource = uri
    }

    private var pendingKind = "avatar"
    /** 待确认的图属于哪一端（[pendingKind] 不是 "avatar" 时有效） */
    private var pendingBgTarget = BgTarget.Phone
    var lastPrompt = ""
    private var lastSize = ""

    /**
     * 生图提示词的草稿键（第 63 轮，用户口径：退出应用后未保存的输入要还在）。
     *
     * 按用途分开存：头像 / 手机端背景 / 桌面端背景的提示词本来就不同，
     * 混成一个键会让"给桌面端写了一半的提示词"覆盖掉头像那一条。
     * 草稿只增不减，**用完不自动删**——要清就点提示词框上的叉号（见 [clearPromptDraft]）。
     */
    private fun promptDraftKey(kind: String) = "editor:prompt:$kind"

    /** 取某用途上次的提示词（弹窗预填；没写过就是空串） */
    fun promptFor(kind: String): String = Repository.loadDraft(promptDraftKey(kind))

    /** 记下某用途的提示词（输入即存：用户可能写完就退出应用） */
    fun savePromptDraft(kind: String, prompt: String) {
        Repository.saveDraft(promptDraftKey(kind), prompt)
    }

    /** 叉号：明确清空某用途的提示词（"不自动删"的另一半） */
    fun clearPromptDraft(kind: String) {
        Repository.saveDraft(promptDraftKey(kind), "")
    }

    /** 预览弹窗的标题用：当前待确认的是头像还是背景 */
    val pendingIsAvatar: Boolean get() = pendingKind == "avatar"

    /** 预览弹窗的标题 / 裁剪比例用：当前待确认的背景属于哪一端 */
    val pendingTarget: BgTarget get() = pendingBgTarget

    // 问题 #27：进入编辑页时的初始快照，用于判断"有未保存的修改"
    private var snapshot: String = ""

    /**
     * 原卡的创建时间（P1-1）。
     * `collectCard()` 是**逐字段重建** CharacterCard，以前唯独漏了 createdAt → 落到默认值"现在"，
     * 而 `Repository.listCharacters()` 按 createdAt 倒序，于是"编辑一张老卡"会永久丢掉它的创建时间、
     * 并把它顶到列表最前。这里在加载时记下来，保存时原样带回。
     */
    private var loadedCreatedAt: Long = 0L

    /**
     * P2-A7：签名由 11 个字段拼成（persona/scenario 可能几千字），而它被组合期读 3 次、
     * 每次重组都要重拼一遍。字段都是 Compose 状态，改用 `derivedStateOf` 缓存：
     * 只有这些字段真的变了才重算。
     */
    private val signatureState = derivedStateOf { computeSignature() }

    private fun currentSignature(): String = signatureState.value

    private fun computeSignature(): String = listOf(
        name, tagline, persona, scenario, greeting,
        extraGreetings.joinToString("\u0001"), formTag,
        categories.joinToString("\u0001"),
        avatarUri ?: "", backgroundUri ?: "", backgroundUriDesktop ?: "",
        // v2 分层字段（E1）：漏了它们会被判成"没改过"，退出不提醒、保存按钮还是灰的。
        // extensionsRaw 不算改动：它没有界面，用户不可能动它。
        description, personality, mesExample,
        creatorNotes, creator, characterVersion, systemPrompt, postHistory,
        taskBrief, outputFormat,
        // 玩法三栏同理：只改了规则或选项数也是改动
        playRules, playState, playOptions.toString(),
        // 音色也算改动：不然"只调了音色"会被判成没改过，退出时不提醒、保存按钮还是灰的
        voiceEnabled.toString(), voiceProvider, voiceBaseUrl, voiceModel, voiceVoice,
        voiceSpeed.toString(), voicePitch.toString(),
        // 混音同理：只动混音源与权重也是改动（源用 id:权重拼，避免 List.toString 的格式歧义）
        voiceMixEnabled.toString(),
        voiceMixSpeakers.joinToString("\u0001") { "${it.voice}:${it.factor}" },
        // 世界书（E3 下半）整本书编码成 JSON 比：条目字段多，手拼列表漏一个就是"改了却不提示保存"
        WorldBookEngine.signature(currentWorldBook())
    ).joinToString("\u0002")

    /** 有未保存的修改（与进入编辑页时的快照比较） */
    val isDirty: Boolean get() = currentSignature() != snapshot

    init {
        // 音色库与混音预设库来自**全局设置**（不在角色卡里）：复刻音色是"这台机器上你的声音"，
        // 混音预设是跨角色复用的配方 —— 两者都只读一次（loadSettings 要读盘 + 解凭据）
        runCatching {
            val g = Repository.loadSettings()
            cloneVoices = g.ttsCloneVoices
            mixPresets = g.ttsMixPresets
        }
        if (characterId == "new") {
            isNew = true
        } else {
            val c = Repository.getCharacter(characterId)
            if (c == null) {
                isNew = true
            } else {
                name = c.name
                tagline = c.tagline
                persona = c.persona
                scenario = c.scenario
                description = c.description
                personality = c.personality
                mesExample = c.mesExample
                creatorNotes = c.creatorNotes
                creator = c.creator
                characterVersion = c.characterVersion
                systemPrompt = c.systemPrompt
                postHistory = c.postHistory
                taskBrief = c.taskBrief
                outputFormat = c.outputFormat
                playRules = c.playRules
                playState = c.playState
                playOptions = c.playOptions
                extensionsRaw = c.extensionsRaw
                c.worldBook?.let { b ->
                    bookName = b.name
                    bookDesc = b.description
                    bookScanDepth = b.scanDepth
                    bookEntries = b.entries
                    bookExtraRaw = b.extraRaw
                }
                val gs = c.effectiveGreetings()
                greeting = gs.firstOrNull() ?: ""
                extraGreetings = if (gs.size > 1) gs.drop(1) else emptyList()
                formTag = c.formTag
                categories = c.categoriesOrDefault()
                avatarUri = c.avatarUri
                backgroundUri = c.backgroundUri
                backgroundUriDesktop = c.backgroundUriDesktop
                // 角色专属音色：卡里没带过就是关着 + 空字段（不预填全局，免得"看起来像设过了"）
                c.voice?.let { v ->
                    voiceEnabled = v.enabled
                    voiceProvider = v.provider
                    voiceBaseUrl = v.baseUrl
                    voiceModel = v.model
                    voiceVoice = v.voice
                    voiceSpeed = v.speed
                    voicePitch = v.pitch
                    voiceMixEnabled = v.mixEnabled
                    voiceMixSpeakers = v.mixSpeakers
                }
                // P1-1：保存时要带回原始创建时间，别让它被默认值覆盖
                loadedCreatedAt = c.createdAt
            }
        }
        // 问题 #27：记下加载/新建时的初始快照
        snapshot = currentSignature()
    }

    private fun collectCard(): CharacterCard {
        val id = if (isNew) Repository.newId() else characterId
        return CharacterCard(
            id = id,
            name = name.trim(),
            tagline = tagline.trim(),
            persona = persona.trim(),
            scenario = scenario.trim(),
            description = description.trim(),
            personality = personality.trim(),
            mesExample = mesExample.trim(),
            creatorNotes = creatorNotes.trim(),
            creator = creator.trim(),
            characterVersion = characterVersion.trim(),
            systemPrompt = systemPrompt.trim(),
            postHistory = postHistory.trim(),
            taskBrief = taskBrief.trim(),
            outputFormat = outputFormat.trim(),
            playRules = playRules.trim(),
            playState = playState.trim(),
            // 只认手册里给的那几档：手改过的 JSON / 极端值一律归 0（不给选项），避免提示词里出现 "-3 个选项"
            playOptions = playOptions.takeIf { it in PlayOptionChoices } ?: 0,
            // 原样带回（见字段声明处的说明：编辑器没有这块界面，但抹掉它等于删了别人的数据）
            extensionsRaw = extensionsRaw,
            greeting = greeting.trim(),
            // 完整开场白列表 = 主开场白 + 其他开场白（去空）
            greetings = (listOf(greeting.trim()) + extraGreetings.map { it.trim() })
                .filter { it.isNotBlank() },
            worldBook = currentWorldBook(),
            formTag = formTag,
            categories = categories.filter { it.isNotBlank() }.ifEmpty { listOf("其他") },
            avatarUri = avatarUri,
            backgroundUri = backgroundUri,
            backgroundUriDesktop = backgroundUriDesktop,
            voice = currentVoice(),
            // P1-1：编辑时保留原创建时间（新建才用当前时间）；
            // 兜底条件处理"加载异常但被判为编辑"的极端情况，避免写进 0 导致排序垫底
            createdAt = if (isNew || loadedCreatedAt <= 0L) System.currentTimeMillis() else loadedCreatedAt
        )
    }

    /**
     * 保存并退出。[onFailure] 在**写入线程**上被调用（写已不在调用线程完成，见 P1-B），
     * 调用方负责提示（`writeFailureToast`）——不接的话，用户看到的是"保存成功"而数据没落盘。
     */
    fun save(onDone: () -> Unit, onFailure: (Throwable) -> Unit = {}) {
        if (name.isBlank()) {
            error = "角色名不能为空"
            return
        }
        saving = true
        Repository.saveCharacter(collectCard(), onFailure)
        saving = false
        onDone()
    }

    fun delete(onDone: () -> Unit, onFailure: (Throwable) -> Unit = {}) {
        if (!isNew) Repository.deleteCharacter(characterId, onFailure)
        onDone()
    }

    fun addExtraGreeting() {
        extraGreetings = extraGreetings + ""
    }

    /** 形态切换：体验 = 多开局（自动补一条空开场白）；陪伴 = 单开场白（清掉其他开场白） */
    fun updateFormTag(tag: String) {
        formTag = tag
        if (tag == "experience") {
            if (extraGreetings.none { it.isNotBlank() }) extraGreetings = listOf("")
        } else {
            extraGreetings = emptyList()
        }
    }

    fun updateExtraGreeting(index: Int, text: String) {
        extraGreetings = extraGreetings.mapIndexed { i, s -> if (i == index) text else s }
    }

    fun removeExtraGreeting(index: Int) {
        extraGreetings = extraGreetings.filterIndexed { i, _ -> i != index }
    }

    // ── 世界书（E3 下半，第 99 轮）─────────────────────────────────────────────

    /** 当前编辑态的世界书；**空书返回 null**（与 `CardImport` 的判据一致：空的不写进卡、不占位置） */
    fun currentWorldBook(): WorldBook? = WorldBook(
        name = bookName.trim(),
        description = bookDesc.trim(),
        entries = bookEntries,
        scanDepth = bookScanDepth,
        extraRaw = bookExtraRaw
    ).takeIf { !it.isEmpty() }

    fun addBookEntry() {
        // id 只在书内唯一（ST 同口径）：取现有最大值 +1，删掉中间条目也不会撞上
        val nextId = (bookEntries.maxOfOrNull { it.id } ?: -1) + 1
        bookEntries = bookEntries + WorldBookEntry(id = nextId)
    }

    fun removeBookEntry(index: Int) {
        bookEntries = bookEntries.filterIndexed { i, _ -> i != index }
    }

    /** 上移 / 下移：**顺序就是卡里"插入顺序"并列时的先后**（同 [WorldBookEntry.insertionOrder] 时按此序） */
    fun moveBookEntry(from: Int, to: Int) {
        if (from !in bookEntries.indices || to !in bookEntries.indices || from == to) return
        bookEntries = bookEntries.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun updateBookEntry(index: Int, transform: (WorldBookEntry) -> WorldBookEntry) {
        bookEntries = bookEntries.mapIndexed { i, e -> if (i == index) transform(e) else e }
    }

    /**
     * 导入一整本书（编辑器「导入世界书」）。
     *
     * 用**替换**而不是追加：两条来源的条目 id 会互相撞（两家都是从 0 开始编），
     * 合并后 `matches` 与界面定位都会指向错的那条。用户想合并就把两份书分别导进两次、自己挑。
     * 书级未识别字段（`extraRaw`）跟着一起来，导出时原样写回。
     */
    fun importWorldBook(book: WorldBook) {
        bookName = book.name
        bookDesc = book.description
        bookScanDepth = book.scanDepth
        bookEntries = book.entries
        bookExtraRaw = book.extraRaw
    }

    /**
     * 当前编辑态的卡（含**未保存**的改动）：导出与生图提示词都用它。
     * 导出的应该是"眼前这张卡"，不是上次保存的版本。
     */
    fun draftCard(): CharacterCard = collectCard()

    /** 参考生图：先选一张参考图（视觉模型转译画风/人物特征），再走生图 */
    var pendingRef by mutableStateOf<String?>(null)
        private set

    fun setPendingRef(uri: String?, kind: String) {
        pendingRef = uri
        if (uri != null) showPromptFor = kind
    }

    fun generate(kind: String, prompt: String, size: String = "") {
        if (generating) return
        pendingKind = kind
        // 背景分端：这次生成归属哪一端（"avatar" 时保持原值，不影响后续判断）
        BgTarget.ofKind(kind)?.let { pendingBgTarget = it }
        lastPrompt = prompt
        lastSize = size
        // 生成不删草稿：下次打开弹窗还预填这段（用户 2026-09-21："提示词用完后不自动删，还是通过叉号删"）
        savePromptDraft(kind, prompt)
        generating = true
        showPromptFor = null
        val ref = pendingRef
        pendingRef = null
        viewModelScope.launch {
            try {
                val settings = Repository.loadSettings()
                // 带参考图：先让视觉模型把参考图转写成画图提示词，再拼接用户意图去生图
                val finalPrompt = if (ref != null) {
                    try {
                        val described = AiClient.visionText(settings, prompt, ref)
                        "$described。补充要求：$prompt"
                    } catch (t: Throwable) {
                        error = "参考图转译失败（${t.message}），已改用原描述生成"
                        prompt
                    }
                } else prompt
                pendingUri = AiClient.generateImage(settings, finalPrompt, size)
            } catch (t: Throwable) {
                error = t.message ?: "生成失败，请检查生图服务配置"
            } finally {
                generating = false
            }
        }
    }

    /** 问题 #8 / #6：把预览中的图真正应用到角色卡（final 可能是裁剪后的新文件） */
    fun applyPendingImage(final: String) {
        if (pendingKind == "avatar") {
            avatarUri = final
            avatarCropSource = null // 全新生成的图 = 新基准
        } else {
            setBg(pendingBgTarget, final)
            setCropSource(pendingBgTarget, null)
        }
        pendingUri = null
    }

    /** 用同一个提示词、同一尺寸重新生成 */
    fun retryPendingImage() {
        val kind = pendingKind
        val prompt = lastPrompt
        val size = lastSize
        pendingUri = null
        if (prompt.isNotBlank()) generate(kind, prompt, size)
    }

    fun discardPendingImage() {
        pendingUri = null
    }

    fun clearError() {
        error = null
    }

    /** 分类多选：点选/取消；至少保留一个分类 */
    fun toggleCategory(c: String) {
        categories = if (c in categories) {
            if (categories.size <= 1) categories else categories - c
        } else {
            categories + c
        }
    }

    companion object {
        fun factory(id: String) = viewModelFactory {
            initializer { CharacterEditorViewModel(id) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterEditorScreen(
    characterId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    /**
     * 桌面三栏：**内嵌进第三栏**（第 34 轮）。为真时顶栏换成"窄头部"（返回 / 标题 / 未保存提示 /
     * 导出 / 保存），正文一字不差。手机端恒为 false，行为零变化。
     */
    embedded: Boolean = false,
    /**
     * 外壳请求关闭（桌面内嵌形态专用，手机端恒为 0）：递增即走一次 [requestBack]。
     * 切页/点会话时由外壳发信号而不是自己清状态——脏不脏只有这里知道，直接清会静默丢改动。
     */
    closeSignal: Int = 0,
    vm: CharacterEditorViewModel = viewModel(factory = CharacterEditorViewModel.factory(characterId))
) {
    // 导出（图片卡 / 纯文本 / JSON）走统一的导出选择框：顶栏分享图标 → CardExportDialog
    var showExport by remember { mutableStateOf(false) }
    val pickAvatar = rememberImagePicker { uri ->
        uri?.let { vm.avatarUri = it; vm.avatarCropSource = null }
    }
    // M11 ④：拖图到窗口 / Ctrl+V 贴截图 → 换这个角色的头像（与 pickAvatar 同一落点）。
    // 编辑器收进第三栏（第 34 轮）后页面级注册会跟着编辑器走：开着才接收，关了即撤。
    val editorImageSink: (String) -> Boolean = { uri ->
        vm.avatarUri = uri
        vm.avatarCropSource = null
        true
    }
    SideEffect { DesktopDragDrop.imageSink = editorImageSink }
    DisposableEffect(Unit) {
        onDispose { if (DesktopDragDrop.imageSink === editorImageSink) DesktopDragDrop.imageSink = null }
    }
    // 参考生图：先选参考图（视觉模型转译画风），随后弹出 AI 生成描述框
    val pickAvatarRef = rememberImagePicker { uri -> vm.setPendingRef(uri, "avatar") }
    // 背景分端：两端各一对「本地上传 / 参考图」选择器
    val pickBgPhone = rememberImagePicker { uri ->
        uri?.let { vm.setBg(BgTarget.Phone, it); vm.setCropSource(BgTarget.Phone, null) }
    }
    val pickBgDesktop = rememberImagePicker { uri ->
        uri?.let { vm.setBg(BgTarget.Desktop, it); vm.setCropSource(BgTarget.Desktop, null) }
    }
    val pickBgRefPhone = rememberImagePicker { uri -> vm.setPendingRef(uri, BgTarget.Phone.kind) }
    val pickBgRefDesktop = rememberImagePicker { uri -> vm.setPendingRef(uri, BgTarget.Desktop.kind) }
    // #6：点图片先预览原图 → 可「更换」或「编辑（裁剪）」。取值 "avatar" 或 BgTarget.kind
    var previewWhat by remember { mutableStateOf<String?>(null) }
    var cropWhat by remember { mutableStateOf<String?>(null) }

    // 问题 #27：有未保存的修改时，返回先弹「保存更改？」
    var showDiscardConfirm by remember { mutableStateOf(false) }
    fun requestBack() {
        if (vm.isDirty) showDiscardConfirm = true else onBack()
    }
    WhaleBackHandler(enabled = vm.isDirty) { showDiscardConfirm = true }
    // 外壳请求关闭：与上面的返回键走同一条路（有未保存改动就弹确认）
    LaunchedEffect(closeSignal) { if (closeSignal > 0) requestBack() }
    if (showDiscardConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("保存更改？") },
            text = { Text("当前角色卡有未保存的修改。") },
            confirmButton = {
                TextButton(onClick = { vm.save(onSaved, writeFailureToast(null, "保存角色")) }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("放弃更改") }
            }
        )
    }

    // 导出选择框：图片卡（竖图）/ 纯文本 / chara_card_v2 JSON。
    // 传的是 vm.draftCard()——导出眼前这张卡（含未保存改动），与"保存"按钮无关。
    if (showExport) {
        CardExportDialog(card = vm.draftCard(), onDismiss = { showExport = false })
    }

    val doSave: () -> Unit = { vm.save(onSaved, writeFailureToast(null, "保存角色")) }

    Scaffold(
        // 内嵌形态下第三栏已经在窗口之内，系统栏 inset 不能再吃一次（否则正文顶部凭空多一条）
        contentWindowInsets = if (embedded) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            if (embedded) {
                // 窄头部：第三栏里没有整页顶栏的位置，标题、未保存提示与动作压成一行
                EditorPaneHeader(
                    title = if (vm.isNew) "新建角色" else "编辑角色",
                    dirty = vm.isDirty,
                    saving = vm.saving,
                    canExport = !vm.isNew,
                    onBack = ::requestBack,
                    onExport = { showExport = true },
                    onSave = doSave
                )
            } else {
                TopAppBar(
                    title = { Text(if (vm.isNew) "新建角色" else "编辑角色") },
                    navigationIcon = {
                        IconButton(onClick = ::requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showExport = true },
                            enabled = !vm.isNew
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "导出角色卡")
                        }
                        IconButton(onClick = doSave, enabled = !vm.saving) {
                            if (vm.saving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = "保存")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("头像", style = MaterialTheme.typography.titleSmall) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = imageModel(vm.avatarUri),
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .clickable(enabled = vm.avatarUri != null) { previewWhat = "avatar" }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageSourceButtons(
                                busy = vm.generating,
                                generateLabel = "AI 生成",
                                onPickLocal = pickAvatar,
                                onGenerate = { vm.showPromptFor = "avatar" },
                                onPickReference = pickAvatarRef
                            )
                        }
                        if (vm.avatarUri != null) {
                            TextButton(
                                onClick = { vm.avatarUri = null; vm.avatarCropSource = null },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) { Text("移除") }
                        }
                    }
                }
            }

            item { Text("基础信息", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text("角色名 *") })
            }
            item {
                OutlinedTextField(vm.tagline, { vm.tagline = it }, Modifier.fillMaxWidth(), label = { Text("一句话简介") })
            }
            item {
                Text("分类（可多选）", style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 固定 + 自定义 + 其他(最后)；新增/删除/排序在「角色卡」页顶部的管理里做
                    CategoryManager.all().forEach { c ->
                        WhaleChip(
                            selected = c in vm.categories,
                            onClick = { vm.toggleCategory(c) },
                            label = { Text(c) }
                        )
                    }
                }
            }
            // ── v2 分层角色设定（E1，2026-09-22）─────────────────────────────────────
            // 三个字段任一非空就走分层注入；全空则下面那段「人设（老版合并内容）」整块照旧注入。
            // 分开的理由：description 管事实、personality 管气质、示例管语气，混成一段会互相复读，
            // 模型也分不清"哪些是必须遵守的事实、哪些是语气示范"（见 docs/调研-角色卡写作范式-2026-09.md §一）。
            //
            // 形态裁剪（E4）：工具形态下这两个字段的含义变成**风格参考**（人格是滤镜、不是身份），
            // 标签也跟着换（见 Engines.TOOL）；世界观与对话示例在工具形态下根本不出现
            //（TOOL 装配不用它们，留着只会让人以为写了就生效）。
            val spec = Engines.of(vm.formTag)
            if (EditorField.Persona in spec.editorFields) {
                item { Text("角色设定", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        vm.description,
                        { vm.description = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(spec.personaLabel) },
                        minLines = 3,
                        placeholder = { Text("例：帝国第三军团将军，银发，左眼有一道旧疤；对陌生人冷淡。") }
                    )
                }
                item {
                    OutlinedTextField(
                        vm.personality,
                        { vm.personality = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(spec.personalityLabel) },
                        minLines = 2,
                        placeholder = { Text("例：外冷内热；认定的事不轻易改口；讨厌被人怜悯。") }
                    )
                }
                // 对话示例（E2 新增层）：工具形态不用它 —— 工具的"示例"是任务样例（见下面的使用示例）
                if (EditorField.DialogueExamples in spec.editorFields) {
                    item {
                        OutlinedTextField(
                            vm.mesExample,
                            { vm.mesExample = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("对话示例（可留空；用 <START> 分块，写「角色名:」「你:」）") },
                            minLines = 3,
                            placeholder = { Text("例：\n<START>\n将军: 站住。\n你: 我……\n将军: 别说话，跟我走。") }
                        )
                    }
                }
                // 老卡的人设是当年**导入时拼好的整块文本**（含【背景故事】【性格特点】小标题），
                // 不做正则反向拆（拆坏用户手改过的内容）。这里原样展示，并说明它什么时候失效。
                if (vm.persona.isNotBlank()) {
                    item {
                        OutlinedTextField(
                            vm.persona,
                            { vm.persona = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("人设（老版合并内容）") },
                            minLines = 4,
                            supportingText = {
                                Text(
                                    "这是老版本拼在一起的整块人设。" +
                                        (if (vm.hasLayeredFields()) "上面填了「角色设定」的分层字段，所以**这一段不再注入**，可以整段清空。"
                                        else "上面的分层字段都为空，所以**它仍按原样注入**。")
                                )
                            }
                        )
                    }
                }
            }
            // ── 工具形态专属两栏（E4）──────────────────────────────────────────────
            // 不复用"人设 / 世界观"：工具作者看到那两个词会困惑，而且 persona 是以"这是你本人的设定"
            // 注入的，复用会主动诱发扮演（台账 §5.2）。这栏写进 extensions.whale 保真往返。
            if (EditorField.TaskBrief in spec.editorFields) {
                item { Text("任务说明", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        vm.taskBrief,
                        { vm.taskBrief = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("要它做什么（工具本身的职责）") },
                        minLines = 3,
                        placeholder = { Text("例：把用户发来的会议记录整理成待办清单，一条一事、去掉寒暄与重复。") },
                        supportingText = { Text("这段进系统提示词，作为这个工具的职责说明。") }
                    )
                }
                item {
                    OutlinedTextField(
                        vm.outputFormat,
                        { vm.outputFormat = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("输出格式要求（会贴到本轮消息末尾强制遵守）") },
                        minLines = 3,
                        placeholder = { Text("例：每行「- [ ] 事项（负责人）」，不要标题、不要总结段。") },
                        supportingText = { Text("写得越可核对越管用（「只给 10 个编号名字、不要别的字」这类）。") }
                    )
                }
                // 预设（E4 收尾）：最常用的四个形状，点一下填进上面那一栏，填完还能手改。
                // 放在输入框**下面**而不是里面：它是"帮你起个头"的东西，不是那一栏的当前值展示。
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "常用格式（点一下填入，会替换本栏现有内容）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutputFormats.presets.forEach { preset ->
                                WhaleChip(
                                    selected = OutputFormats.selectedLabel(vm.outputFormat) == preset.label,
                                    onClick = { vm.outputFormat = preset.spec },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                    }
                }
            }
            // ── 玩法形态专属三栏（E4 拆档，2026-09-23）────────────────────────────
            // 规则是这一局唯一的"对错来源"（报告 §四：没有它，模型会为了顺着用户而放宽判定）；
            // 状态项告诉模型这一局要记住什么 —— 它进系统提示词，会话里那份【当前进度】也按它整理。
            if (EditorField.PlayRules in spec.editorFields) {
                item { Text("玩法规则与状态", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        vm.playRules,
                        { vm.playRules = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("规则（硬约束：必须… / 不可…）") },
                        minLines = 4,
                        placeholder = {
                            Text(
                                "例：\n只回答「是」或「不是」，不要报具体名字。\n答「不是」时必须把该候选排除，之后不再提。\n20 问内没猜中就算用户赢。"
                            )
                        },
                        supportingText = { Text("这是这一局唯一的对错来源：写得越可判定，模型越不会为了顺着用户而放宽规则。") }
                    )
                }
                item {
                    OutlinedTextField(
                        vm.playState,
                        { vm.playState = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("要记的状态（进度 / 计分 / 已确认结论）") },
                        minLines = 3,
                        placeholder = { Text("例：已排除的候选、当前第几问、谁领先、还剩下什么没确认。") },
                        supportingText = { Text("它进系统提示词；聊天中 AI 也会按它自动更新会话里的「本局进度」（每 4 条一次）。") }
                    )
                }
                item {
                    Column {
                        Text(
                            "每轮给几个编号选项",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PlayOptionChoices.forEach { n ->
                                WhaleChip(
                                    selected = vm.playOptions == n,
                                    onClick = { vm.playOptions = n },
                                    label = { Text(if (n == 0) "不给选项" else "$n 个") }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "「不给选项」适合自由作答的玩法（例如「千万不要说水」）；给了选项，用户回一个数字就能继续。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (EditorField.Scenario in spec.editorFields) {
                item {
                    OutlinedTextField(
                        vm.scenario,
                        { vm.scenario = it },
                        Modifier.fillMaxWidth(),
                        // 玩法形态下它不是"此刻的处境"，而是这局玩法的前提（进了系统提示词，
                        // 所以会话里**不铺场景气泡**，见 ChatViewModel.newConversation）
                        label = { Text(if (vm.formTag == "play") "玩法设定（这一局的前提与背景）" else "世界观 / 当前场景") },
                        minLines = 3,
                        placeholder = {
                            Text(
                                if (vm.formTag == "play") "例：这是一局「二十问猜人」，你想一个人物，我只用是 / 不是的问题来猜。"
                                else "可选：故事发生在哪里，此刻是什么情境"
                            )
                        }
                    )
                }
            }
            if (EditorField.Greeting in spec.editorFields) {
                item {
                    OutlinedTextField(
                        vm.greeting,
                        { vm.greeting = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(spec.greetingLabel) },
                        minLines = 3,
                        placeholder = { Text(spec.greetingHint) }
                    )
                }
            }
            // 多条开场白 / 多条使用示例：「多线」下新会话随机抽一条；工具下它们只是并列的任务样例
            //（点击即填入输入框），所以**多条示例不得触发「多线」徽标**（台账 §5.3 修订 3）；
            // 玩法下它们是并列的**开局引导**（随机抽一条＝这一局从哪里开始）
            if (vm.formTag == "experience" || vm.formTag == "tool" || vm.formTag == "play") {
                val tool = vm.formTag == "tool"
                val play = vm.formTag == "play"
                item {
                    Text(
                        when {
                            tool -> "其他使用示例（各是一条任务样例，点击即填入输入框）"
                            play -> "其他开局引导（开局方向不同，新会话随机抽一条）"
                            else -> "其他开场白（开局方向不同，新会话随机抽一条）"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                itemsIndexed(vm.extraGreetings) { i, g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            g,
                            { vm.updateExtraGreeting(i, it) },
                            Modifier.weight(1f),
                            label = {
                                Text(
                                    when {
                                        tool -> "使用示例 ${i + 2}"
                                        play -> "开局引导 ${i + 2}"
                                        else -> "开场白 ${i + 2}"
                                    }
                                )
                            },
                            minLines = 2
                        )
                        IconButton(onClick = { vm.removeExtraGreeting(i) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除该开场白")
                        }
                    }
                }
                item {
                    TextButton(onClick = vm::addExtraGreeting) {
                        Text(
                            when {
                                tool -> "+ 添加一条使用示例"
                                play -> "+ 添加一条开局引导"
                                else -> "+ 添加一条开场白"
                            }
                        )
                    }
                }
            }
            // 形态标签：陪伴（默认）/ 体验（多开局、剧情玩法）
            item {
                Column {
                    Text("形态标签", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CharacterFormTags.forEach { (label, value) ->
                            WhaleChip(
                                selected = vm.formTag == value,
                                onClick = { vm.updateFormTag(value) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        when (vm.formTag) {
                            "tool" -> "工具：用户发来的是待处理素材、你产出的是可直接拿走的成品（不扮演）。"
                            "play" -> "玩法：陪玩一局有规则的玩法（猜谜 / 对弈 / 问答）。规则是硬约束、" +
                                "每轮可给编号选项、这局的进度它自己记；不做主线与存档（那是「故事」）。"
                            "experience" -> "多线：多种开局方向，新会话随机抽一条开场白。"
                            else -> "陪伴：单开场白的沉浸式角色扮演。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 世界书（E3 下半，第 99 轮）：放在角色设定/形态之后、背景之前 ——
            // 它和上面几段同属"这个角色带什么资料"，而背景/音色是呈现层。
            // 整块塞进**一个 item**：区块内部自己用 Column 排（见 WorldBookEditorSection 的说明）。
            item { WorldBookEditorSection(vm) }

            item { Text("默认聊天背景", style = MaterialTheme.typography.titleSmall) }
            // A 批次（用户 2026-09-18）：两端窗口形状不同（手机竖屏 / 桌面横屏窗口），各存一份，
            // 端点各自的"设置背景"只动本端那份。只设了一份时两端都用它（老卡即此形态）。
            item {
                Text(
                    "手机与桌面的窗口形状不同，各存一份；只设一份时两端都用它。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                BackgroundSlotEditor(
                    target = BgTarget.Phone,
                    uri = vm.slotBg(BgTarget.Phone),
                    inherited = false,
                    busy = vm.generating,
                    onPreview = { previewWhat = BgTarget.Phone.kind },
                    onPickLocal = pickBgPhone,
                    onGenerate = { vm.showPromptFor = BgTarget.Phone.kind },
                    onPickReference = pickBgRefPhone,
                    onRemove = {
                        vm.setBg(BgTarget.Phone, "")
                        vm.setCropSource(BgTarget.Phone, null)
                    }
                )
            }
            item {
                BackgroundSlotEditor(
                    target = BgTarget.Desktop,
                    uri = vm.slotBg(BgTarget.Desktop),
                    // 桌面端那份没单独设过、正用着手机端那张 —— 必须说出来，否则用户看到的图和
                    // "桌面上显示的是哪张"对不上，也不知道「移除」到底移掉了什么
                    inherited = vm.backgroundUriDesktop == null && !vm.backgroundUri.isNullOrBlank(),
                    busy = vm.generating,
                    onPreview = { previewWhat = BgTarget.Desktop.kind },
                    onPickLocal = pickBgDesktop,
                    onGenerate = { vm.showPromptFor = BgTarget.Desktop.kind },
                    onPickReference = pickBgRefDesktop,
                    onRemove = {
                        // 写空串而不是 null：null 是"没设过"，那样又会回退到手机端那张
                        vm.setBg(BgTarget.Desktop, "")
                        vm.setCropSource(BgTarget.Desktop, null)
                    }
                )
            }

            // ── 卡信息 + v2 原样字段（E1，2026-09-22）────────────────────────────────
            item { Text("卡信息", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(
                    vm.creatorNotes,
                    { vm.creatorNotes = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("说明（给玩的人看，不进提示词）") },
                    minLines = 2,
                    placeholder = { Text("例：适合慢慢聊的日常向角色；开局从深夜电台开始。") }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        vm.creator,
                        { vm.creator = it },
                        Modifier.weight(1f),
                        label = { Text("作者") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        vm.characterVersion,
                        { vm.characterVersion = it },
                        Modifier.weight(1f),
                        label = { Text("卡版本") },
                        singleLine = true
                    )
                }
            }
            item { Text("后置指令", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(
                    vm.postHistory,
                    { vm.postHistory = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("每轮都要遵守的补充要求（v2 post_history_instructions）") },
                    minLines = 3,
                    supportingText = {
                        Text("贴在本轮最后一条消息末尾——这是最贴近生成点的位置，比写在人设里更容易被遵守。可留空。")
                    }
                )
            }
            item {
                OutlinedTextField(
                    vm.systemPrompt,
                    { vm.systemPrompt = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("原卡自带的系统提示词（只保存，不生效）") },
                    minLines = 2,
                    supportingText = {
                        Text("别人的卡常把系统提示词写在这里。鲸鱼的角色扮演提示词由 App 统一控制（视角约定等），" +
                            "所以这一段**只原样保存与导出、不参与对话**，避免两套规则互相打架。")
                    }
                )
            }

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
                // 声音类型（第 69 轮）：单一 / 混合 / 复刻 —— 与设置页**同一排胶囊、同一套判据**
                // （`CharacterVoices.kindOf`；类型从数据推、不落库，所以卡往返与导出都不用改口径）。
                // 后两个只在火山出现：混音是火山专属协议、复刻是火山的 ICL 资源。
                val isVolc = ModelCatalog.speechProviderKeyword(vm.voiceBaseUrl) == "volcengine" ||
                    vm.voiceProvider == "volcengine"
                val voiceKind = CharacterVoices.kindOf(
                    vm.voiceVoice, vm.voiceModel, vm.voiceMixEnabled, vm.voiceMixSpeakers,
                    mixSupported = isVolc,
                    // 本机复刻音色库要进判定（第 70 轮）：录音复刻出来的 id 没有 `S_` 前缀，
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
                        // 复刻音色那一档把「我的复刻音色」并进预设清单（第 70 轮）：自己命名的音色
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
                            if (vm.cloneVoices.isEmpty()) {
                                "还没录过自己的声音：到「设置 → 外观与设置 → 语音服务 → 语音朗读」选「复刻音色」，" +
                                    "那里可以**在 App 里录一段**直接复刻（火山），复刻好的声音也会出现在上面这一栏的下拉里。"
                            } else {
                                "上面「音色」栏的下拉里有你复刻好的声音（${vm.cloneVoices.joinToString("、") { it.name }}）；" +
                                    "要再录一个，去「设置 → 语音服务 → 语音朗读 → 复刻音色」。"
                            },
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
                // ── 混合音色（火山专属，2026-09-22 用户反馈；第 69 轮起由「声音类型」胶囊选入）──────
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
                    // "混合音色生效中吗"统一问类型（第 69 轮）：它已经把"只有 1 个源""别家不支持混音"
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
                                "用**这个角色**的音色读一句样例。走的是朗读那条链路（同一份解析、同一份缓存），" +
                                    "所以试听能出声、聊天里就一定能出声。",
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
                        "音色随角色卡导出 / 导入（`extensions.whale.voice`）：卡里写的供应商、模型、音色、语速音高" +
                            "以及混合音色的源与权重都原样带着，换台机器导入后按「家」自动对上。\n" +
                            "**这一页与设置页「语音朗读」互不影响**：这里改的是本卡专属音色，不会动全局那套，" +
                            "反过来改全局也不会改已经设过音色的卡（开着开关的卡一律用自己的）。\n" +
                            "只有**凭据**是全局的、一页填一次；合成按字符计费，同一句命中本地缓存不重复付费。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!vm.isNew) {
                item {
                    Button(
                        onClick = { vm.showDeleteConfirm = true },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除这个角色（连同全部会话）")
                    }
                }
            }
        }
    }

    // #6：点图片先看原图 → 更换 / 编辑（裁剪）
    previewWhat?.let { what ->
        val target = BgTarget.ofKind(what)
        val cur = if (what == "avatar") vm.avatarUri else target?.let { vm.slotBg(it) }
        if (!cur.isNullOrBlank()) {
            val saveGallery = rememberGallerySaver()
            if (cropWhat == what) {
                // 问题 #13：裁剪基准始终是最初原图——裁完显示新图，但再进「编辑」仍从原图重裁
                val cropSource = if (what == "avatar") {
                    (vm.avatarCropSource ?: cur).also { vm.avatarCropSource = it }
                } else {
                    val t = target!!
                    (vm.cropSourceOf(t) ?: cur).also { vm.setCropSource(t, it) }
                }
                CropDialog(
                    sourceUri = cropSource,
                    // 分端后裁剪框按**该端**的窗口形状：本端取真实屏幕/窗口比例，另一端给名义值
                    aspect = if (what == "avatar") 1f else rememberBgCropAspect(target!!),
                    onCropped = { newUri ->
                        if (what == "avatar") vm.avatarUri = newUri else vm.setBg(target!!, newUri)
                        cropWhat = null
                    },
                    onDismiss = { cropWhat = null }
                )
            } else {
                ImagePreviewDialog(
                    title = if (what == "avatar") "头像" else "${target!!.label}聊天背景",
                    uri = cur,
                    onReplace = {
                        previewWhat = null
                        if (what == "avatar") pickAvatar() else if (target == BgTarget.Desktop) pickBgDesktop() else pickBgPhone()
                    },
                    onEdit = { cropWhat = what },
                    onSave = { saveGallery(cur) },
                    onDismiss = { previewWhat = null }
                )
            }
        }
    }

    if (vm.showPromptFor != null) {
        val kind = vm.showPromptFor!!
        val target = BgTarget.ofKind(kind)
        PromptDialog(
            title = if (kind == "avatar") "用 AI 生成头像" else "用 AI 生成${target!!.label}聊天背景",
            // 问题 #28：预填上次的提示词，返回来调整时不用重写；
            // 第 63 轮起这份草稿**落盘**（`drafts.json`）——退出应用再进来还在这里
            initial = vm.promptFor(kind),
            placeholder = if (kind == "avatar") {
                "描述你想要的立绘风格，例如：唯美二次元，紫色长发少女，微笑，胸像，浅色背景"
            } else if (target == BgTarget.Desktop) {
                "描述聊天背景场景，例如：黄昏的旧书店窗外，暖色光线，横屏构图"
            } else {
                "描述聊天背景场景，例如：黄昏的旧书店窗外，暖色光线，竖屏构图"
            },
            onConfirm = { prompt, size -> vm.generate(kind, prompt, size) },
            onDismiss = { vm.showPromptFor = null; vm.promptDraft = null },
            // 输入即存：用户写完提示词可能直接退出应用，下次打开要原样看到
            onTextChange = { vm.savePromptDraft(kind, it) },
            // 叉号：明确清空（提示词不自动删，只有这里能删）
            onClear = { vm.clearPromptDraft(kind) },
            // #6：生图尺寸在这里选（头像默认方形，背景按端：手机竖屏 / 桌面横屏）
            sizeOptions = ImageSizeOptions,
            defaultSize = if (kind == "avatar") "1024x1024" else defaultBgImageSize(target!!),
            // 0.1.2：懒人路径——不用自己写描述，让 AI 按角色卡设定起草一版
            draftLabel = if (kind == "avatar") "按角色设定起草提示词" else "按世界观起草背景提示词",
            drafting = vm.draftingPrompt,
            draftText = vm.promptDraft,
            onDraft = { vm.draftPrompt(kind) }
        )
    }

    if (vm.showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.showDeleteConfirm = false },
            title = { Text("删除角色？") },
            text = { Text("该角色的所有会话记录、头像与背景图也会一并删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.showDeleteConfirm = false; vm.delete(onSaved, writeFailureToast(null, "删除角色")) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { vm.showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    // 问题 #8：AI 生图结果先预览，再由用户选 保存 / 应用 / 重试
    vm.pendingUri?.let { uri ->
        GeneratedImageDialog(
            title = if (vm.pendingIsAvatar) "头像生成结果" else "${vm.pendingTarget.label}聊天背景生成结果",
            uri = uri,
            cropAspect = if (vm.pendingIsAvatar) 1f else rememberBgCropAspect(vm.pendingTarget),
            onApply = { final -> vm.applyPendingImage(final) },
            onRetry = { vm.retryPendingImage() },
            onDismiss = { vm.discardPendingImage() }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

/**
 * 桌面第三栏的窄头部（第 34 轮）：整页顶栏那三个动作——返回、导出、保存——压成一行。
 *
 * 脏标记用文字明说（整页顶栏是靠"保存图标一直可点"暗示的），因为第三栏里没有别的地方
 * 能看出这张卡改没改过；保存按钮也就跟着脏标记走（同「模型与 API」内嵌形态的口径）。
 */
@Composable
private fun EditorPaneHeader(
    title: String,
    dirty: Boolean,
    saving: Boolean,
    canExport: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            if (dirty) "有未保存的修改" else "已是最新",
            style = MaterialTheme.typography.labelSmall,
            color = if (dirty) colors.error else colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onExport, enabled = canExport) {
            Icon(Icons.Filled.Share, contentDescription = "导出角色卡")
        }
        Button(onClick = onSave, enabled = !saving && dirty) {
            if (saving) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("保存")
            }
        }
    }
}

/**
 * 一个"端"的默认聊天背景槽位（A 批次，用户 2026-09-18）。
 *
 * 手机端与桌面端各一个，UI 完全相同（各自的预览、本地上传 / AI 生成 / 参考图、移除），
 * 差别只在数据落到哪个字段与裁剪框用哪个比例——所以抽成一个组件，别写两份。
 *
 * @param inherited 这一端没单独设过、正用着另一端那张。要说出来（见调用处注释），
 *   否则"点进来看到的图"和"这个槽位到底存了什么"对不上。
 */
@Composable
private fun BackgroundSlotEditor(
    target: BgTarget,
    uri: String?,
    inherited: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onPickLocal: () -> Unit,
    onGenerate: () -> Unit,
    onPickReference: () -> Unit,
    onRemove: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${target.label}聊天背景", style = MaterialTheme.typography.titleSmall)
            if (target == BgTarget.current) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "本机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (inherited) {
            Spacer(Modifier.height(2.dp))
            Text(
                "未单独设置，正在用手机端那张",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = imageModel(uri),
                contentDescription = "${target.label}背景预览",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    // 桌面端是横图、手机端是竖图，预览框也按比例给，别把横图塞进竖框里看不出问题
                    .height(if (target == BgTarget.Desktop) 100.dp else 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    // #6：已设置背景时点击先看原图（原来是直接又弹 AI 描述框）
                    .clickable { onPreview() }
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onGenerate() },
                contentAlignment = Alignment.Center
            ) {
                Text("点击设置背景", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 与头像区保持同一套观感（同一个 ImageSourceButtons）
            ImageSourceButtons(
                busy = busy,
                generateLabel = "AI 生成场景",
                onPickLocal = onPickLocal,
                onGenerate = onGenerate,
                onPickReference = onPickReference
            )
            if (!uri.isNullOrBlank()) {
                TextButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("移除") }
            }
        }
    }
}

/**
 * 「本地图片 / AI 生成 / 参考图」按钮行（P2-D2）。
 *
 * 头像区与背景区原本各有一份完整拷贝，差别只有生成文案与生成目标。
 * 三条观感约定（问题 #5）：两个主操作统一成描边按钮、**强制等宽**（否则「本地图片」比
 * 「AI 生成」宽一截，看着不齐）、收窄内容内边距（等宽并排后文字不被挤断）。
 */
@Composable
private fun RowScope.ImageSourceButtons(
    busy: Boolean,
    generateLabel: String,
    onPickLocal: () -> Unit,
    onGenerate: () -> Unit,
    onPickReference: () -> Unit
) {
    val padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    OutlinedButton(
        onClick = onPickLocal,
        modifier = Modifier.weight(1f),
        contentPadding = padding
    ) {
        Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("本地图片")
    }
    AiActionButton(
        label = generateLabel,
        busyLabel = "生成中…",
        onClick = onGenerate,
        busy = busy,
        enabled = !busy,
        filled = false,
        starSize = 16.dp,
        gap = 4.dp,
        modifier = Modifier.weight(1f),
        contentPadding = padding
    )
    OutlinedButton(
        onClick = onPickReference,
        enabled = !busy,
        modifier = Modifier.weight(1f),
        contentPadding = padding
    ) {
        Text("参考图")
    }
}


