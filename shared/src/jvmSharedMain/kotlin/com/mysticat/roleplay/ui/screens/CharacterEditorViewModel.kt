package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.CharacterVoice
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.CloneVoice
import com.mysticat.roleplay.data.MixPreset
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.PlayOptionChoices
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.TtsSpeaker
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.draftImagePrompt
import com.mysticat.roleplay.data.generateImage
import com.mysticat.roleplay.data.visionText
import com.mysticat.roleplay.ui.writeFailureToast
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.TtsAudition
import com.mysticat.roleplay.ui.MixPresetBar
import com.mysticat.roleplay.ui.mixPresetLabel
import com.mysticat.roleplay.ui.voiceDisplayName
import kotlinx.coroutines.launch

class CharacterEditorViewModel(private val characterId: String) : ViewModel() {

    var name by mutableStateOf("")
    var tagline by mutableStateOf("")
    var persona by mutableStateOf("")
    var scenario by mutableStateOf("")

    // ── v2 规范字段（角色卡引擎，2026-09-22）：各自独立编辑 ────────────────────
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
    /** 工具形态的两栏：本轮只有字段与装配，编辑器 UI 随后续批次一起做 */
    var taskBrief by mutableStateOf("")
    var outputFormat by mutableStateOf("")
    /** 玩法形态的三栏：规则（硬约束）/ 状态项 / 每轮选项数 */
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

    // ── 世界书管理（编辑器不再内联改书）──────────
    // 书实体住 `accounts/<id>/worldbooks/<bookId>.json`（Repository 管理），内容编辑与
    // 引用管理都归宝库的世界书段（那边逐卡开关 / 独立编辑）。编辑器只保留三件事：
    // 启用（本卡是否引用）/ 编辑（跳宝库打开这本书）/ 新建（建一本空书挂上并跳过去）。
    var bookId by mutableStateOf<String?>(null)
    /** 展示用：书名 + 条目数（打开页面时从书文件读一次，编辑器不持书的编辑态） */
    var bookSummary by mutableStateOf<Pair<String, Int>?>(null)
        private set
    /** 本页面里"启用"关掉的那本书：再打开开关时原样挂回（跨页面重新挂书走宝库的逐卡开关） */
    private var detachedBookId: String? = null
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
     * **本机复刻音色库**。判定"这个 id 是不是复刻音色"、定 Resource-Id、
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

    // ── 声音类型与试听──────────────────────────────────────────────
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

    // ── 混音预设：用 / 存 / 删（用户反馈"角色专属音色也要能够用预设和存预设"）────
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
     * 生图提示词的草稿键（用户口径：退出应用后未保存的输入要还在）。
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
     * 原卡的创建时间。
     * `collectCard()` 是**逐字段重建** CharacterCard，以前唯独漏了 createdAt → 落到默认值"现在"，
     * 而 `Repository.listCharacters()` 按 createdAt 倒序，于是"编辑一张老卡"会永久丢掉它的创建时间、
     * 并把它顶到列表最前。这里在加载时记下来，保存时原样带回。
     */
    private var loadedCreatedAt: Long = 0L

    /**
     * 签名由 11 个字段拼成（persona/scenario 可能几千字），而它被组合期读 3 次、
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
        // v2 分层字段：漏了它们会被判成"没改过"，退出不提醒、保存按钮还是灰的。
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
        voiceMixSpeakers.joinToString("\u0001") { "${it.voice}:${it.factor}" }
        // 世界书不进签名：编辑器不再改书内容，引用的挂/摘即时落库（宝库同款口径），
        // 不该再让"挂书了"挡住用户的"放弃更改"
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
                    bookId = c.worldBookId
                    bookSummary = b.name to b.entries.size
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
                // 保存时要带回原始创建时间，别让它被默认值覆盖
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
            // 世界书管理化：卡不再持内嵌编辑副本，正文从书文件现读——
            // 导出卡要带上书；saveCharacter 落盘时会把内嵌剥掉、只留引用
            worldBook = bookId?.let { Repository.getWorldBook(it) },
            worldBookId = bookId,
            formTag = formTag,
            categories = categories.filter { it.isNotBlank() }.ifEmpty { listOf("其他") },
            avatarUri = avatarUri,
            backgroundUri = backgroundUri,
            backgroundUriDesktop = backgroundUriDesktop,
            voice = currentVoice(),
            // 编辑时保留原创建时间（新建才用当前时间）；
            // 兜底条件处理"加载异常但被判为编辑"的极端情况，避免写进 0 导致排序垫底
            createdAt = if (isNew || loadedCreatedAt <= 0L) System.currentTimeMillis() else loadedCreatedAt
        )
    }

    /**
     * 保存并退出。[onFailure] 在**写入线程**上被调用（写已不在调用线程完成），
     * 调用方负责提示（`writeFailureToast`）——不接的话，用户看到的是"保存成功"而数据没落盘。
     */
    fun save(onDone: () -> Unit, onFailure: (Throwable) -> Unit = {}) {
        if (name.isBlank()) {
            error = "角色名不能为空"
            return
        }
        saving = true
        // 世界书管理化：书内容不经过编辑器（编辑/新建都跳宝库），卡里只带引用 id；
        // 挂/摘在操作发生时即时落库（与宝库的逐卡开关同口径），这里不再替书落盘或删书——
        // 删书入口在宝库，那边有引用检查。
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

    // ── 世界书管理──────────────────────────────────────────────────

    /**
     * 启用 / 停用：本卡是否引用当前书。停用先记住书 id（本页面里再打开开关就原样挂回，
     * 跨页面重新挂书走宝库的逐卡开关）；已落卡的卡即时写库，新建卡只改 VM、保存时随卡落库。
     */
    fun setWorldBookEnabled(on: Boolean, onFailure: (Throwable) -> Unit = {}) {
        if (on) {
            val id = detachedBookId ?: return
            detachedBookId = null
            bookId = id
            if (!isNew) Repository.setCharacterWorldBook(characterId, id, onFailure)
        } else {
            val id = bookId ?: return
            detachedBookId = id
            bookId = null
            bookSummary = null
            if (!isNew) Repository.setCharacterWorldBook(characterId, null, onFailure)
        }
    }

    /** 有没有可挂回的书（本页面里刚摘下来的才有；开着开关就是挂着的，不需要这个） */
    val canReenableBook: Boolean get() = detachedBookId != null

    /**
     * 新建一本空书挂到本卡上：书立即落账号库（内容在宝库里写），已落卡的卡同时写上引用；
     * [onReady] 带书 id 给界面跳宝库打开它。
     */
    fun createNewBook(onReady: (String) -> Unit, onFailure: (Throwable) -> Unit = {}) {
        val id = Repository.newId()
        Repository.saveWorldBook(id, WorldBook(), onFailure)
        detachedBookId = null
        bookId = id
        bookSummary = "" to 0
        if (!isNew) Repository.setCharacterWorldBook(characterId, id, onFailure)
        onReady(id)
    }

    /** 从宝库挑一本已有的书挂到本卡（[WorldBookManageSection] 的选择弹窗）：替换当前引用，即时落库（新建卡随保存落库） */
    fun attachExistingBook(id: String, onFailure: (Throwable) -> Unit = {}) {
        val book = Repository.getWorldBook(id) ?: return
        detachedBookId = null
        bookId = id
        bookSummary = book.name to book.entries.size
        if (!isNew) Repository.setCharacterWorldBook(characterId, id, onFailure)
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
