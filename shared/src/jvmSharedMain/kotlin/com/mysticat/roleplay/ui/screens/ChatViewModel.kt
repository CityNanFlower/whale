package com.mysticat.roleplay.ui.screens

/**
 * 聊天页 ViewModel：会话状态与持久化、发送与流式接收、前情提要/记忆整理、灵感回复、
 * 背景图、消息多版本与叙事风格写入。
 *
 * * 2026-09-17（D10）从 `ChatScreen.kt` 拆出：原文件 3451 行，纯结构拆分，逻辑未动。
 */

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.AiException
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.BgmPlayer
import com.mysticat.roleplay.data.ChatMessage
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.EngineSpec
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.NO_AUDIO_INPUT_NOTICE
import com.mysticat.roleplay.data.NarrativeStyle
import com.mysticat.roleplay.data.NarrativeStyles
import com.mysticat.roleplay.data.OutputFormats
import com.mysticat.roleplay.data.PromptMode
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.SessionBgm
import com.mysticat.roleplay.data.TtsCache
import com.mysticat.roleplay.data.TtsSpeaker
import com.mysticat.roleplay.data.WhaleLog
import com.mysticat.roleplay.data.Voice
import com.mysticat.roleplay.data.SpeechRecognizerErrors
import com.mysticat.roleplay.data.narrativeStyle
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.ErrorBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val characterId: String,
    private val conversationIdArg: String
) : ViewModel() {

    var character by mutableStateOf<CharacterCard?>(null)
        private set
    var conversation by mutableStateOf<Conversation?>(null)
        private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    /**
     * 本会话所属的引擎形态（E4）：卡的形态决定装配契约、编辑器字段裁剪与聊天页各开关。
     * 做成派生属性而不是状态：卡一换（或角色被编辑）立刻跟着变，不会留下两份要同步的事实。
     */
    val engine: EngineSpec get() = Engines.of(character)

    /**
     * 工具形态的「使用示例」（E4）＝卡里的开场白字段，点击即当任务填入输入框。
     * 非工具形态返回空列表（那时开场白已经在新会话里了，不需要 chip）。
     */
    val greetingExamples: List<String>
        get() {
            if (!engine.greetingFillsInput) return emptyList()
            val c = character ?: return emptyList()
            return c.effectiveGreetings().map { AiClient.expandMacros(it, c) }
        }

    var input by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var generatingBg by mutableStateOf(false)
        private set
    /** 已生成、等待用户确认的聊天背景（问题 #8） */
    var pendingBg by mutableStateOf<String?>(null)
        private set
    /**
     * 会话背景"改哪一端"（A 批次分端）：聊天菜单里选，默认本平台那份。
     * 是状态而不是普通字段——生成结果弹窗的裁剪比例要跟着它重组。
     */
    var bgMenuTarget by mutableStateOf(BgTarget.current)
    /** 待确认的生成结果归属哪一端：**生成时定下**，应用时不再看菜单当前选的是谁 */
    var pendingBgTarget by mutableStateOf(BgTarget.Phone)
        private set
    /** 最近一次生图提示词，供「重试」复用 */
    var lastBgPrompt by mutableStateOf("")
    /** 最近一次生图尺寸，供「重试」复用 */
    private var lastBgSize by mutableStateOf("")
    /** 流式回复尚未完成时的可见增量文本 */
    var draft by mutableStateOf("")
        private set

    // ---- 打字机缓冲（问题 #3：即使模型一次把整段吐完，也按受控速率逐步显示）----
    /** 已从模型收到、但还没展示给用户的全部文本 */
    private val recvBuf = StringBuilder()
    /** recvBuf 中已经展示出去的长度 */
    private var revealed = 0

    private fun clearRecv() = synchronized(recvBuf) {
        recvBuf.setLength(0)
        revealed = 0
    }

    private fun appendRecv(s: String) = synchronized(recvBuf) { recvBuf.append(s) }

    private fun recvLength() = synchronized(recvBuf) { recvBuf.length }

    private fun recvSlice(n: Int) = synchronized(recvBuf) { recvBuf.substring(0, n) }

    /**
     * 打字机：把 recvBuf 里已收到但未展示的文本按受控速率揭示到 [draft]。
     * 每拍至少 1 字（约 40 字/秒），剩余越多追得越快，最长约 1.5 秒追平，
     * 这样无论上游是逐 token 推送还是一次吐完，观感都是一致的逐字输出。
     */
    private suspend fun runTypewriter(isDone: () -> Boolean) {
        while (true) {
            val total = recvLength()
            if (revealed < total) {
                val remain = total - revealed
                val step = maxOf(1, (remain + TYPER_CATCHUP_TICKS - 1) / TYPER_CATCHUP_TICKS)
                revealed = minOf(total, revealed + step)
                draft = recvSlice(revealed)
            } else if (isDone()) {
                break
            }
            delay(TYPER_TICK_MS)
        }
    }

    var showHistory by mutableStateOf(false)
    var showBgMenu by mutableStateOf(false)
    var showBgPrompt by mutableStateOf(false)
    var showRenameDialog by mutableStateOf(false)
    var showSettingDialog by mutableStateOf(false)
    var showMemoryDialog by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // 灵感回复（0.1.2）：不会接话时给几条可以直接发的话
    var showInspiration by mutableStateOf(false)
    var inspirationLoading by mutableStateOf(false)
        private set
    var inspirationError by mutableStateOf<String?>(null)
        private set

    /** 灵感结果对应的会话 id；换会话后旧结果不可跨会话展示 */
    private var inspirationsConvId: String? = null

    /** 灵感结果生成时的消息条数；来了一条新消息（用户发送 / 助手回复 / 回溯编辑）后即失效 */
    private var inspirationsMsgCount: Int = -1

    /** 灵感结果对应的角色（同一会话内角色不会变，这里主要防切会话时角色一起换掉的边界） */
    private var inspirationsCharacterId: String? = null

    /** 当前缓存的灵感结果（只在 [isInspirationsCurrent] 为真时展示） */
    var inspirations by mutableStateOf<List<String>>(emptyList())
        private set

    /**
     * 缓存的灵感是否仍对应当前会话与当前剧情进度。
     * 保存机制（用户 2026-09-15 口径）：**最新一批一直保留**——点选其中一条填入、关掉弹窗、
     * 退出会话再回来，都还能看到同一批；但**来了一条新消息后剧情已推进，旧灵感即失效**，
     * 下次打开要重新生成。会话之间互不影响。
     */
    private val isInspirationsCurrent: Boolean
        get() {
            val conv = conversation ?: return false
            return inspirations.isNotEmpty() &&
                inspirationsConvId == conv.id &&
                inspirationsCharacterId == characterId &&
                inspirationsMsgCount == conv.messages.size
        }

    /**
     * 拉取灵感回复：复用当轮上下文与角色设定，历史与聊天同口径
     * （[AiClient.requestHistory]：按 `historyLimit` 与字符预算裁剪 + 单图模型只留最近一张）。
     * 「打开面板」不自动调这个——入口先查 [isInspirationsCurrent]，有有效缓存就直接展示。
     */
    fun loadInspirations() {
        if (!engine.inspiration) return
        if (inspirationLoading) return
        val conv = conversation ?: return
        val card = character ?: return
        // 记下"为哪个会话、哪条进度"生成：异步返回时即使剧情又变了，也按发起时的状态缓存
        val convId = conv.id
        val msgCount = conv.messages.size
        val charId = characterId
        inspirationLoading = true
        inspirationError = null
        viewModelScope.launch {
            val r = runCatching {
                val settings = Repository.loadSettings()
                AiClient.suggestReplies(
                    settings = settings,
                    card = card,
                    history = conv.messages,
                    userSetting = conv.userSetting,
                    // 刻意不传叙事风格：灵感回复是"用户该说什么"，风格指令是写给角色的（见 AiClient.suggestReplies）
                    memory = conv.memory,
                    summary = conv.summary
                )
            }
            inspirationLoading = false
            // 结果只在「还停留在发起时那个会话」时才写入：异步返回时用户可能已经切走，
            // 否则会把上个会话的建议弹到新会话里（P1-14）
            if (conversation?.id != convId) return@launch
            r.onSuccess {
                inspirations = it
                inspirationsConvId = convId
                inspirationsMsgCount = msgCount
                inspirationsCharacterId = charId
            }.onFailure { inspirationError = it.message ?: "生成灵感失败，请稍后再试" }
        }
    }

    /** 打开灵感面板的统一入口：有对应当前进度的缓存就展示，没有（或已因新消息失效）才重新生成 */
    fun openInspirations() {
        // 工具形态（E4）整个禁掉：灵感回答的是"戏里我该说什么"，而工具里用户要发的是任务素材
        // （台账 §5.4 第 3 条）。入口也不渲染，这里再兜一道，免得别处的调用漏进来。
        if (!engine.inspiration) return
        showInspiration = true
        if (!isInspirationsCurrent) loadInspirations()
    }

    /** 点选一条灵感后**不清缓存**（用户口径：这批一直保留，回来还能看） */
    fun pickInspiration(text: String) {
        updateInput(text)
        showInspiration = false
    }

    /** 当前对话模型是否支持视觉（决定聊天里是否显示发图按钮） */
    var visionChat by mutableStateOf(false)
        private set

    /**
     * 重算"当前模型能不能看图"。
     *
     * 模型是**全局设置**，而这个 ViewModel 会活过"去设置页换模型再回来"这一趟
     * （导航返回栈里的条目还在），所以只在 init 里算一次不够：
     * 换成纯文本模型后发图按钮仍然在，用户发的图会被对方模型静默忽略，界面上看不出任何异常
     * ——2026-09-16 用户实测踩到（同一条图第一次被无视、换带 👁 的模型后才正常）。
     * 现在**每次进入聊天页**与**每次发送前**都重算。
     */
    fun refreshVision() {
        visionChat = ModelCatalog.isVisionModel(Repository.loadSettings().chatModel)
    }

    /**
     * 麦克风是"按住说话"还是"点按说话"（读设置里的 [AiSettings.asrTapToTalk]）。
     *
     * 与 [visionChat] 同理：设置是全局的，而本 ViewModel 活过"去设置页改一下再回来"这一趟，
     * 所以每次进聊天页都重算。**桌面端不看这个值**——界面层再或上 `isDesktopLayout`（鼠标按住说话难受）。
     */
    var voiceTapToTalk by mutableStateOf(false)
        private set

    fun refreshVoiceMode() {
        voiceTapToTalk = Repository.loadSettings().asrTapToTalk
    }

    /**
     * 从磁盘重读角色卡（每次进聊天页都调）。
     *
     * 为什么必须重读：本 ViewModel **活过"去编辑页改一下再回来"这一趟**，而 `init` 只在最初读了一次卡。
     * 卡就是发消息时用的那一份（见 `send` 里的 `val card = character`），不重读的症状是
     * **"在世界书里加了条目、回来发消息却没生效"**——界面看不出任何异常，用户只会以为功能坏了。
     * 世界书命中面板读的也是这一份，同样会被旧卡骗（第 99 轮实测踩到）。
     *
     * 读不到（卡在编辑页被删了）就保留原值：删除流程自己会导航离开，这里不该顺手把界面清空。
     */
    fun reloadCharacter() {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { Repository.getCharacter(characterId) }
            if (fresh != null) character = fresh
        }
    }

    var deletingId by mutableStateOf<String?>(null)

    /**
     * 本会话的 BGM 句柄（[BgmPlayer.enterSession] 发的号）。
     * 只有它交回去的那次 [BgmPlayer.leaveSession] 才真的停音乐——迟到的离开不生效。
     */
    private var bgmHandle: Long = 0L

    /**
     * 进聊天页那趟读盘（角色卡 + 本角色的会话列表）**读完了没有**。
     *
     * 第 64 轮把 `init` 里的同步读搬到后台：`listConversations` 要在界面线程上把账号目录下
     * **全部**会话 JSON 扫一遍（合成 1600 个会话冷读实测 4.4s，量法见桌面 `--smoke` 的
     * 「列表冷读计时」），而"点角色卡开始聊"正是最常走的一趟——会话一多就卡在那一下。
     *
     * 界面在此之前必须显示"载入中"而**不是**"角色不存在或已被删除"（[ChatScreen] 就是这么排的）：
     * 后者在这段时间里是**错的**，会把"还在读盘"说成"数据没了"。
     */
    var ready by mutableStateOf(false)
        private set

    init {
        // 用 viewModelScope：切走 / 换会话时 ViewModel 被清掉，协程随之取消——
        // 下面那几行（尤其是 enterSession）就不会在"这个 VM 已经没人要了"之后才跑。
        viewModelScope.launch {
            val (char, existing) = withContext(Dispatchers.IO) {
                val c = Repository.getCharacter(characterId)
                val conv = when (conversationIdArg) {
                    // 聊天记录页的「+」：强制开一个全新的空会话（原来会错误地恢复最近一次会话）
                    "blank" -> null
                    // 角色卡点卡片：优先恢复「最近一次会话」，没有历史才新建
                    "new" -> Repository.listConversations(characterId).firstOrNull()
                    else -> Repository.getConversation(conversationIdArg)
                }
                c to conv
            }
            character = char
            conversation = existing ?: newConversation(null, withGreeting = true)
            refreshVision()
            refreshVoiceMode()
            // 背景音乐只在会话中生效（第 63 轮架构）：进聊天页按本会话的配置起播
            // （Conversation.bgm 为 null＝从没配过 ⇒ 跟随全局默认）。
            // 句柄要留着：桌面三栏切会话时"新的先进、旧的后离开"，离开不带句柄会把新会话的音乐停掉（第 58 轮）
            bgmHandle = BgmPlayer.enterSession(conversation?.bgm)
            refreshList()
            ready = true
        }
    }

    private fun refreshList() {
        // 会话列表同样搬后台：它挂在"每轮回复结束"这条热路径上（原来每答完一条都要在界面线程上重扫一遍）
        viewModelScope.launch {
            conversations = withContext(Dispatchers.IO) { Repository.listConversations(characterId) }
        }
    }

    private fun newConversation(id: String?, withGreeting: Boolean): Conversation {
        val convId = id ?: Repository.newId()
        // 新会话默认采用「我的」里设置的默认设定
        val defaultSetting = Repository.defaultUserSetting()?.content ?: ""
        val base = Conversation(
            id = convId,
            characterId = characterId,
            title = "新会话",
            userSetting = defaultSetting,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        // 预置开场（问题 #16：场景卡 + 首句仍由 TA 说）：铺什么**由形态与卡共同决定** ——
        // 工具什么都不铺、玩法只铺开局引导且视卡而定、陪伴/多线铺场景卡与随机开场白。
        // 口径收在 `AiClient.seedOpening`（纯函数：聊天页与桌面自检共用一份，别再写回这里）。
        val seeded = if (withGreeting) AiClient.seedOpening(character, engine) else emptyList()
        return if (seeded.isEmpty()) base else base.copy(messages = seeded)
    }

    /**
     * 把结果写回**指定 id** 的会话，而不是"当前"会话。
     *
     * 流式回复、后台前情提要、记忆整理都可能在用户切走/新建/删除会话之后才完成。
     * 以前这些路径统一写 `conversation`（＝当前会话），于是结果会被写进**另一个会话**。
     * 现在一律按发起时锁定的 id 回取：
     * - 会话已被删除 → 丢弃结果（[silent] 为假时提示）；
     * - 用户已切到别的会话 → 只落盘 + 刷新列表，**不抢占当前界面**。
     *
     * [transform] 返回 null 表示"这次没有可写的内容"，直接跳过。
     */
    private fun updateConversation(
        convId: String,
        silent: Boolean = false,
        transform: (Conversation) -> Conversation?
    ) {
        val stored = Repository.getConversation(convId)
        if (stored == null) {
            if (!silent) error = "该会话已被删除，本次结果未保存"
            return
        }
        val updated = transform(stored) ?: return
        // P1-B：落盘已不在主线程（Repository 走串行写入通道），失败回调被转回主线程再提示
        Repository.saveConversation(updated) { t ->
            if (!silent) viewModelScope.launch { error = "保存失败：${t.message ?: "磁盘写入异常"}" }
        }
        if (conversation?.id == convId) conversation = updated
        refreshList()
    }

    /**
     * 落盘并同步内存态。
     *
     * P1-B（2026-09-15）：落盘不再阻塞主线程，改为交给 `Repository` 的串行写入通道；
     * 内存态先更新（用户改动立刻可见），失败回调转回主线程再提示 ——
     * 既不会崩在 onClick 里，也不再让"写磁盘"卡住这一帧。
     */
    private fun persist(c: Conversation) {
        conversation = c
        Repository.saveConversation(c) { t ->
            viewModelScope.launch { error = "保存失败：${t.message ?: "磁盘写入异常"}" }
        }
        refreshList()
    }

    /**
     * 本会话背景音乐（第 63 轮）：写进会话并立即生效。
     * [bgm] = null 表示"从未设置过"（等价于跟随全局）；音乐库里曲目的引用格式是 `lib:<id>`。
     * 拖动音量的实时档走 [BgmPlayer.setVolumeLive]（不落盘），松手才经这里写全会话配置。
     */
    fun setSessionBgm(bgm: SessionBgm?) {
        val conv = conversation ?: return
        persist(conv.copy(bgm = bgm))
        BgmPlayer.updateSession(bgm)
    }

    fun updateInput(v: String) {
        input = v
    }

    /** 待发送的图片附件：选图后先挂在输入栏，可继续打字，随下一条消息一起发送 */
    var pendingImage by mutableStateOf<String?>(null)

    fun attachPendingImage(uri: String) {
        pendingImage = uri
    }

    fun clearPendingImage() {
        pendingImage = null
    }

    fun send() {
        val text = input.trim()
        val conv = conversation ?: return
        if ((text.isEmpty() && pendingImage == null) || sending) return
        // 发送前重算：模型可能在设置页刚被换过（见 refreshVision 的说明）
        refreshVision()
        val image = pendingImage
        if (image != null && !visionChat) {
            // 绝不静默发出去：对方看不见图，只会回一段"答非所问"，用户还以为图发成功了
            error = "当前模型（${Repository.loadSettings().chatModel}）不支持看图，这张图不会发送。" +
                "请到「模型与 API」换成带 👁 的模型，或点掉图片只发文字。"
            return
        }
        if (image != null && runCatching { java.io.File(image).length() }.getOrDefault(0L) <= 0L) {
            error = "这张图读不到了（文件可能已被清理），请重新选一张。"
            return
        }
        val userMsg = ChatMessage(role = "user", content = text, imageUri = image)
        val hasUserMessage = conv.messages.any { it.role == "user" }
        val withUser = conv.copy(
            messages = conv.messages + userMsg,
            // 用第一条用户消息自动命名（即使有开场白也能生效）。
            // 工具形态（E4）下首条消息是**待处理的长素材**，直接截前 18 字会截在句子中间，
            // 所以取第一行、放宽到 24 字 —— 会话列表里要能认出"这是哪一单"（台账 §5.5 第 3 条）。
            title = if (!hasUserMessage) autoTitleFor(text) else conv.title,
            updatedAt = System.currentTimeMillis()
        )
        persist(withUser)
        pendingImage = null
        input = ""
        // 素材本身超了单轮预算：它会被钉住留下（不会被裁掉，见 AiClient.trimHistory），
        // 但上下文里其余消息会被挤光 —— 不静默，明确说一声（台账 §5.1 修订 1 ③）
        if (engine.pinFirstMessage && AiClient.exceedsContextBudget(text)) {
            softNotice = "这段素材约 ${text.length} 字，已超出单轮上下文预算，" +
                "本轮只会带上素材本身与最近几条补充要求。建议拆成几次处理。"
        }
        requestReply(withUser.messages)
    }

    /** 会话标题：陪伴/多线沿用"前 18 字"，工具取第一行前 24 字（见 send 里的说明） */
    private fun autoTitleFor(text: String): String {
        val fallback = text.ifBlank { "图片" }
        if (!engine.pinFirstMessage) return fallback.take(18)
        val firstLine = fallback.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: fallback
        return firstLine.take(24).ifBlank { "新任务" }
    }

    /** 更新本会话的角色记忆（更多面板里编辑；自动记忆也会覆盖更新它） */
    fun applyMemory(text: String) {
        val conv = conversation ?: return
        persist(conv.copy(memory = text.trim()))
        showMemoryDialog = false
    }

    /**
     * 自动前情提要：被裁掉、尚未摘要的消息超过 4 条时，后台把它们与旧摘要合并成新的前情提要。
     * 完成后写回 conversation.summary / summarizedUntil，不阻塞当前回复。
     * [convId] 为发起时的会话 id——摘要是异步任务，完成时用户可能已经切走，必须写回原会话。
     */
    private fun maybeUpdateSummary(convId: String, history: List<ChatMessage>, historyLimit: Int) {
        val conv = conversation?.takeIf { it.id == convId } ?: Repository.getConversation(convId) ?: return
        val trimmed = AiClient.trimHistory(history, historyLimit)
        val prefixSize = history.size - trimmed.size
        if (prefixSize <= conv.summarizedUntil + 4) return
        val prefix = history.take(prefixSize)
        val oldSummary = conv.summary
        viewModelScope.launch {
            runCatching {
                val settings = Repository.loadSettings()
                val fragment = prefix.joinToString("\n") { m ->
                    "${if (m.role == "user") "用户" else "TA"}：" + m.content.take(400)
                }.take(6000)
                val result = AiClient.chatCompletion(
                    settings.copy(maxTokens = 1024),
                    "把【已有前情提要】和【新剧情片段】合并成一份简洁的前情提要：要点式、300 字以内、" +
                        "保留关键事件/人物关系变化/约定。只输出提要本身。",
                    listOf(
                        ChatMessage("user", "【已有前情提要】\n${oldSummary.ifBlank { "（无）" }}\n\n【新剧情片段】\n$fragment")
                    ),
                    // 后台任务：固定关闭思考，别让深度推理拖慢/变贵（用户要求）
                    thinking = "off"
                )
                // 完成时**按 id 回取**，以最新内容为底写回，避免覆盖期间的回复 / 写错会话
                updateConversation(convId, silent = true) { latest ->
                    latest.copy(summary = result.trim(), summarizedUntil = prefixSize)
                }
            }
        }
    }

    /** 自动记忆是否正在更新（防止并发重复整理） */
    private var memoryUpdating = false

    /**
     * 自动角色记忆：每累计 [EngineSpec.memoryMergeEvery] 条新消息，后台让模型把【现有记忆】与【新对话】合并更新。
     * 记忆是"长期事实/约定/关系状态"的要点列表，会随对话内容变化被增删改，
     * 与用户手动编辑的是同一份数据（conversation.memory），每轮注入 system prompt。
     *
     * **节奏与口径按形态走**（E4 拆档）：陪伴 / 多线 10 条一次（关系变化慢，够用且省后台请求）；
     * 玩法型 4 条一次，且合并的是"这一局的进度"而不是"我们之间的关系"——一局才十几轮，
     * 10 条一次等于半局不更新，用户看到的就是"它忘了刚才确认过什么"。
     *
     * [convId] 为发起时的会话 id（同 [maybeUpdateSummary]，异步完成时必须写回原会话）。
     */
    private fun maybeUpdateMemory(convId: String, messages: List<ChatMessage>) {
        val conv = conversation?.takeIf { it.id == convId } ?: Repository.getConversation(convId) ?: return
        if (messages.size < conv.memoryUntil + engine.memoryMergeEvery) return
        if (memoryUpdating) return
        memoryUpdating = true
        val recent = messages.drop(conv.memoryUntil).takeLast(12)
        val play = engine.promptMode == PromptMode.PLAY
        val section = if (play) "现有进度" else "现有记忆"
        val sys = if (play) {
            "你是进度管理器。把【现有进度】与【新对话】合并为更新后的一局玩法进度：\n" +
                "1. 只记这一局玩下去必须记住的东西：规则判定结果、已经确认或排除的结论、计分、进行到第几步；\n" +
                "2. 不要记寒暄、吐槽与情绪，也不要记卡里已有的规则与设定（AI 本来就知道）；\n" +
                "3. 被新对话推翻的条目要删掉或改写（例如某候选从「待定」变成「已排除」），合并重复、补上新结论；\n" +
                "输出更新后的进度全文（要点式，每行一条，300 字以内）。只输出内容，不要解释。"
        } else {
            "你是记忆管理器。把【现有记忆】与【新对话】合并为更新后的角色记忆：\n" +
                "1. 只记录聊天中产生的新信息：事实、约定、关系变化、用户偏好、重要转折；\n" +
                "2. 不要包含角色卡里已有的基础设定（人设、世界观、场景）——那些 AI 本来就知道，写进来纯属浪费；\n" +
                "3. 删除已被新对话推翻或过时的条目，合并重复，补上新事实；\n" +
                "输出更新后的记忆全文（要点式，每行一条，400 字以内）。只输出记忆内容，不要解释。"
        }
        viewModelScope.launch {
            try {
                val settings = Repository.loadSettings()
                val fragment = recent.joinToString("\n") { m ->
                    "${if (m.role == "user") "用户" else "TA"}：" + m.content.take(300)
                }.take(5000)
                val result = AiClient.chatCompletion(
                    settings.copy(maxTokens = 1024),
                    sys,
                    listOf(
                        ChatMessage("user", "【$section】\n${conv.memory.ifBlank { "（空）" }}\n\n【新对话】\n$fragment")
                    ),
                    // 后台任务：固定关闭思考，别让深度推理拖慢/变贵（用户要求）
                    thinking = "off"
                )
                if (result.isNotBlank()) {
                    // 同上：按 id 回取写回，别写进用户已经切过去的那个会话
                    updateConversation(convId, silent = true) { latest ->
                        latest.copy(memory = result.trim(), memoryUntil = messages.size)
                    }
                }
            } catch (_: Throwable) {
                // 自动记忆更新失败静默，不打扰用户
            } finally {
                memoryUpdating = false
            }
        }
    }

    /**
     * 当前进行中的流式请求；离开聊天页（onCleared）时取消，不再等超时。
     * P2-A21：写入发生在协程（IO 线程的 onCall 回调），读取发生在主线程 onCleared，
     * 加 `@Volatile` 保证可见性（此前是良性竞态，但没理由留着）。
     */
    @Volatile
    private var activeCall: okhttp3.Call? = null

    override fun onCleared() {
        activeCall?.cancel()
        // 离开聊天页立即停读，别在别的页面继续念上一个会话的回复（设计口径 3）
        ttsSpeaker?.stop()
        // 语音输入同理：正在录音就丢弃半截文件，别把麦克风留着占着（系统识别也要销毁）
        runCatching { voiceInput.cancel() }
        runCatching { Voice.recognizer.destroy() }
        // 背景音乐只在会话中生效（第 63 轮架构）：离开聊天页（含桌面切走第三栏的聊天窗）就停。
        // 交回自己的句柄：桌面切会话时本方法可能**晚于**新会话的 enterSession 才跑（第 58 轮）
        BgmPlayer.leaveSession(bgmHandle)
        super.onCleared()
    }

    /**
     * 语音朗读（P-F2 v1，系统 TTS）。惰性创建：不朗读就不碰系统 TTS 引擎。
     *
     * 播放状态只在"开读 / 读完 / 出错"这几个低频事件上变（[TtsSpeaker] 的回调已切回主线程），
     * 不做逐帧驱动 —— 逐帧状态会让整页每 16ms 重组一次。
     */
    private var ttsSpeaker: TtsSpeaker? = null
    private val tts: TtsSpeaker
        get() = ttsSpeaker ?: TtsSpeaker { st ->
            ttsSpeaking = st == TtsSpeaker.State.STARTED
            // 合成失败（语音包缺失、或引擎需要联网下载语音数据）要说出来，
            // 否则用户看到的是"点了朗读没声音、界面也没解释"（模拟器上就是这个现象）
            if (st == TtsSpeaker.State.ERROR) {
                ttsNotice = ttsSpeaker?.unavailableReason
                    ?: "系统语音合成失败：可能是语音包缺失，或该引擎需要联网下载语音数据"
            }
        }.also { ttsSpeaker = it }

    /** 正在朗读（气泡菜单据此显示「停止朗读」） */
    var ttsSpeaking by mutableStateOf(false)
        private set

    /** 系统语音包缺失之类的提示（非模态，一行小字） */
    var ttsNotice by mutableStateOf<String?>(null)
        private set

    /**
     * 用户刚按过「停止朗读」、或把顶栏喇叭关掉了：**这一条消息**剩下的部分不再朗读。
     *
     * 为什么需要它：自动朗读是**边生成边喂**的（`tts.feed(delta)`），只停掉此刻的队列没用 ——
     * 模型的下一个增量进来又会接着念，表现成"点了停止还在念"（第 69 轮）。
     * 只作用一条消息：下一轮 `requestReply` 与手动「朗读」都会把它清掉。
     */
    private var ttsUserStopped = false

    private fun syncTtsParams(s: AiSettings) {
        tts.speed = s.ttsSpeed
        tts.pitch = s.ttsPitch
        tts.skipActionText = s.ttsSkipActionText
    }

    /** 长按气泡菜单「朗读」：整条读完（先停掉正在读的，避免两条叠着） */
    fun readAloud(text: String) {
        // 手动朗读是一次明确的新意图：把"这条别念了"的状态清掉，否则点了朗读反而不出声
        ttsUserStopped = false
        // 角色专属音色（E 批次）：这一条链路上"拿哪套设置去合成"一律过一遍解析——
        // 结果同时决定了引擎（系统/供应商）、音色参数与缓存键（见 TtsCache 的说明）
        val s = CharacterVoices.effective(Repository.loadSettings(), character)
        syncTtsParams(s)
        ttsNotice = null
        if (!useBuiltinTts(s)) {
            tts.speakMessage(text)
            ttsNotice = tts.unavailableReason
            if (ttsNotice == null) ttsSpeaking = true
            return
        }
        // v2：走供应商合成。合成是网络请求，交给协程；失败会在这条消息内部回退系统 TTS
        ttsSpeaking = true
        viewModelScope.launch {
            try {
                tts.speakBuiltin(text) { piece -> synthToFile(s, piece) }
                tts.lastFallbackReason?.let { ttsNotice = it }
            } catch (t: Throwable) {
                ttsNotice = t.message ?: "语音合成失败"
            } finally {
                ttsSpeaking = false
            }
        }
    }

    /**
     * 朗读是走供应商合成（v2）还是系统 TTS（v1）。
     * **自动回退**：选了 builtin 但没填 Base URL / 模型 → 用系统 TTS，功能不会因为没配而不出声。
     */
    private fun useBuiltinTts(s: AiSettings): Boolean =
        s.ttsProvider == "builtin" && s.ttsBaseUrl.isNotBlank() && s.ttsModel.isNotBlank()

    /** v2：把一段文本合成成**本地文件**（命中缓存直接返回；实现见 [TtsCache]，与设置页「试听」共用） */
    private suspend fun synthToFile(s: AiSettings, text: String): java.io.File =
        TtsCache.file(s, text)

    /**
     * 顶栏喇叭开关：**新回复自动朗读**（全局设置，与「更多」面板里的思考强度同一类做法）。
     * 关掉时立刻停读 —— 否则"点了关还在念"会让人以为没生效。
     */
    var ttsAutoRead by mutableStateOf(Repository.loadSettings().ttsAutoRead)
        private set

    fun toggleTtsAutoRead() {
        val v = !ttsAutoRead
        ttsAutoRead = v
        Repository.saveSettings(Repository.loadSettings().copy(ttsAutoRead = v))
        if (!v) stopSpeaking()
    }

    /** 长按气泡菜单「停止朗读」/ 切会话 / 离开页面 */
    fun stopSpeaking() {
        ttsUserStopped = true
        ttsSpeaker?.stop()
        ttsSpeaking = false
    }

    fun clearTtsNotice() {
        ttsNotice = null
    }

    // ────────────────────── 语音输入（ASR，Step 3，2026-09-16）──────────────────────

    /**
     * 录音/识别状态机：界面据此显示"按住说话 / 正在录音（松手结束）/ 正在识别…/ 请说话…"。
     * 注意 [LISTENING] 是**系统识别**在听（松手结束），与 [RECORDING]（我们自己录音）不是一回事。
     */
    enum class VoicePhase { IDLE, RECORDING, TRANSCRIBING, LISTENING }

    var voicePhase by mutableStateOf(VoicePhase.IDLE)
        private set

    /** 语音输入的提示（非模态一行小字）：没权限、供应商失败已回退、没识别到内容… */
    var voiceNotice by mutableStateOf<String?>(null)
        private set

    private val voiceInput = Voice.recorderFactory()

    fun noteVoice(msg: String) {
        voiceNotice = msg
    }

    fun clearVoiceNotice() {
        voiceNotice = null
    }

    /** 识别结果**写进输入框但不自动发送**（用户要能看一眼、改两个字再发） */
    private fun applyVoiceText(text: String) {
        input = if (input.isBlank()) text else input + text
    }

    /**
     * 按住说话：按下时调用。
     * 供应商识别可用 → 自己录音；否则（选了系统识别 / 没配地址模型 / 那家没凭据）→ 系统识别。
     * 两者都在同一颗按钮上，用户不需要先理解"引擎"这件事。
     */
    fun startVoiceInput() {
        voiceNotice = null
        val s = Repository.loadSettings()
        val builtin = useBuiltinAsr(s)
        // 语音输入是"点了没反应"投诉最多的一条：按下时的路由选择、失败原因都留一行日志，
        // 免得只有界面上一闪而过的小字（用户截不到、我们也复现不了）
        WhaleLog.i("WhaleVoice", "按下麦克风 → 走${if (builtin) "供应商识别（先录音）" else "系统识别"}（asrProvider=${s.asrProvider}）")
        if (!builtin) {
            if (s.asrProvider == "builtin") voiceNotice = "供应商识别还没配好，已改用系统识别"
            startSystemListening()
            return
        }
        runCatching { voiceInput.start() }
            .onSuccess {
                voicePhase = VoicePhase.RECORDING
                WhaleLog.i("WhaleVoice", "录音已开始")
            }
            .onFailure {
                voicePhase = VoicePhase.IDLE
                voiceNotice = "无法开始录音：${it.message ?: "麦克风可能被其它应用占用"}"
                WhaleLog.w("WhaleVoice", "录音启动失败：${it.message}")
            }
    }

    /** 松手时调用：录音模式下停止并上传识别；系统识别模式下只是"我说完了" */
    fun finishVoiceInput() {
        when (voicePhase) {
            VoicePhase.RECORDING -> {
                val file = voiceInput.stop()
                if (file == null) {
                    voicePhase = VoicePhase.IDLE
                    // 设备一个字节都没给时不能再说"说得太短"（那是另一回事，见 VoiceRecorder 注释）
                    voiceNotice = if (voiceInput.lastStopHadNoAudio) NO_AUDIO_INPUT_NOTICE
                    else "说话时间太短，按住麦克风再说一次"
                    WhaleLog.i("WhaleVoice", "松手：没录到内容（${if (voiceInput.lastStopHadNoAudio) "设备无数据" else "太短或音频为空"}），未送识别")
                    return
                }
                voicePhase = VoicePhase.TRANSCRIBING
                WhaleLog.i("WhaleVoice", "松手：录音 ${file.length() / 1024}KB，送供应商识别")
                viewModelScope.launch {
                    try {
                        val text = AiClient.transcribe(Repository.loadSettings(), file)
                        if (text.isBlank()) voiceNotice = "没有识别到内容" else applyVoiceText(text)
                        voicePhase = VoicePhase.IDLE
                        WhaleLog.i("WhaleVoice", "供应商识别返回：${if (text.isBlank()) "空" else "${text.length} 字"}")
                    } catch (t: Throwable) {
                        // 与朗读同一套口径：失败不静默，也不把用户在输入框里已有的字弄丢
                        voiceNotice = "供应商识别失败：${t.message ?: "未知错误"}；已改用系统识别"
                        WhaleLog.w("WhaleVoice", "供应商识别失败：${t.message}")
                        startSystemListening()
                    } finally {
                        file.delete()
                    }
                }
            }

            // 系统识别：松手＝"我说完了"，接下来等引擎把结果送回来。切到"正在识别…"——
            // 桌面上这条是"松手后离线认一段 WAV"（约 1~2 秒），停在"请说话…"会让人以为没反应。
            // 两个平台都保证这次 stopListening 之后必有一次结果或错误回调。
            VoicePhase.LISTENING -> {
                voicePhase = VoicePhase.TRANSCRIBING
                Voice.recognizer.stopListening()
            }
            else -> voicePhase = VoicePhase.IDLE
        }
    }

    fun cancelVoiceInput() {
        voiceInput.cancel()
        runCatching { Voice.recognizer.cancel() }
        voicePhase = VoicePhase.IDLE
    }

    /**
     * 朗读是走供应商识别还是系统识别。**自动回退**：选了 builtin 但没填地址/模型/凭据 → 系统识别，
     * 所以"点麦克风永远有反应"，不会因为没配好而变成哑巴按钮。
     */
    private fun useBuiltinAsr(s: AiSettings): Boolean =
        s.asrProvider == "builtin" && s.asrBaseUrl.isNotBlank() && s.asrModel.isNotBlank() &&
            (s.hasSpeechCredential(s.asrBaseUrl) || !s.asrBaseUrl.startsWith("https://"))

    /**
     * 系统识别（平台注入的 [Voice.recognizer]，Android=系统 SpeechRecognizer）。
     *
     * 两个细节归属：① 创建与 startListening 必须在主线程——由平台实现内部负责；
     * ② Android 11+ 要在 manifest 里声明 `<queries><intent>RecognitionService`，否则
     * `isRecognitionAvailable` 恒为 false（表现为"点了没反应"）。
     */
    private fun startSystemListening() {
        val rec = Voice.recognizer
        if (!rec.isAvailable()) {
            voicePhase = VoicePhase.IDLE
            voiceNotice = "这台设备没有可用的系统语音识别，请在「语音朗读 → 语音输入」里改用供应商识别"
            WhaleLog.w("WhaleVoice", "系统识别不可用（没有装识别语言包或没有麦克风）")
            return
        }
        voicePhase = VoicePhase.LISTENING
        val lang = Repository.loadSettings().asrLanguage.trim()
        WhaleLog.i("WhaleVoice", "系统识别开始听（语言 ${lang.ifBlank { "系统默认" }}）")
        runCatching {
            rec.listen(
                lang,
                onResult = { text ->
                    voicePhase = VoicePhase.IDLE
                    if (text.isBlank()) voiceNotice = "没有识别到内容" else applyVoiceText(text)
                    WhaleLog.i("WhaleVoice", "系统识别返回：${if (text.isBlank()) "空" else "${text.length} 字"}")
                },
                onError = { code ->
                    voicePhase = VoicePhase.IDLE
                    voiceNotice = "系统识别失败：${systemRecognizerError(code)}"
                    WhaleLog.w("WhaleVoice", "系统识别失败：错误码 $code")
                }
            )
        }.onFailure {
            voicePhase = VoicePhase.IDLE
            voiceNotice = "系统识别启动失败：${it.message ?: "未知错误"}"
            WhaleLog.w("WhaleVoice", "系统识别启动失败：${it.message}")
        }
    }

    /** 系统识别的错误码 → 人话（只翻常见的几个，其余带码显示便于排查） */
    private fun systemRecognizerError(code: Int): String = when (code) {
        SpeechRecognizerErrors.ERROR_NO_MATCH -> "没听清，请再说一次"
        SpeechRecognizerErrors.ERROR_SPEECH_TIMEOUT -> "没有听到声音"
        SpeechRecognizerErrors.ERROR_NO_AUDIO_INPUT -> NO_AUDIO_INPUT_NOTICE
        SpeechRecognizerErrors.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
        SpeechRecognizerErrors.ERROR_NETWORK, SpeechRecognizerErrors.ERROR_NETWORK_TIMEOUT ->
            "系统识别的网络不可用（有些机型的识别要联网）"
        SpeechRecognizerErrors.ERROR_RECOGNIZER_BUSY -> "识别服务忙，稍后再试"
        SpeechRecognizerErrors.ERROR_AUDIO -> "录音出错"
        else -> "错误码 $code"
    }

    /**
     * 发送一轮请求：把 history 作为上下文调模型，并把回复追加到当前会话。
     * [variantOf] >= 0 时为 swipe 重新生成：新回复作为该 assistant 消息的新版本追加，不另起新气泡。
     *
     * ⚠️ **目标会话在开头就锁定**：流式期间用户可能切走/新建/删除会话（历史面板、删除确认），
     * 完成时必须按这个 id 写回原会话，绝不能写进"当时看起来是当前"的那个会话。
     */
    private fun requestReply(history: List<ChatMessage>, variantOf: Int = -1) {
        val targetId = conversation?.id ?: return
        sending = true
        draft = ""
        clearRecv()
        // 新一轮请求开始：把上一条的朗读停掉（不然会在生成新回复时还在念旧回复）
        stopSpeaking()
        // …上面这个 stopSpeaking 是"换一条了"的程序性动作，不是用户想静音：新的一条照读
        ttsUserStopped = false
        var streamDone = false
        // 自动朗读开关（默认关）：**只读本轮正在生成的这条**，流式边生成边读。
        // 在 try 之外声明，catch（流中断）里也要用它把已经拿到的部分读完。
        var autoTts = false
        // v2 的自动朗读：整条回复落地后再合成（见 useBuiltinTts 附近的说明）
        var autoTtsBuiltin = false
        // 流式检查点基线：最终保存时以这批消息为底，覆盖掉中途的静默检查点
        val messagesBeforeReply = conversation?.messages ?: emptyList()
        val received = StringBuilder()
        var sinceCheckpoint = 0
        viewModelScope.launch {
            // 打字机与网络请求并发跑：网络往 recvBuf 里灌，打字机按速率揭示。
            // 工具形态（E4）关掉受控揭示：成品要立刻能复制走，不该被 16ms/拍往后推（台账 §5.4 第 2 条）。
            val typer = if (engine.typewriter) launch { runTypewriter { streamDone } } else null
            try {
                val settings = Repository.loadSettings()
                val card = character ?: throw IllegalStateException("角色不存在")
                val conv = conversation?.takeIf { it.id == targetId }
                    ?: Repository.getConversation(targetId)
                    ?: throw IllegalStateException("会话不存在")
                // 长会话：溢出消息后台自动合并为前情提要。
                // 工具形态跳过：前情提要是关系/剧情连续性设备，而工具会话里那些消息是**素材与成品**
                // （台账 §5.1：它们被整个隐藏），拿去做摘要既没意义又要花钱。
                // 玩法形态也跳过（E4 拆档）：一局要延续的是**进度**而不是"更早剧情的摘要"，
                // 那份进度已经由【当前进度】承载；再叠一份摘要只会两处口径打架、还多花一次请求。
                if (engine.relationshipMemory && engine.promptMode != PromptMode.PLAY) {
                    maybeUpdateSummary(targetId, history, settings.historyLimit)
                }
                // 角色专属音色（E 批次）：对话本身仍用全局设置，**只有朗读这条链路**换成
                // "卡覆盖全局"之后的那一份（换角色即换声音；卡没开个性化时这个值 === settings）
                val ttsSettings = CharacterVoices.effective(settings, card)
                // v1（系统 TTS）边生成边读；v2（供应商合成，按字符计费）等整条落地再按句合成 ——
                // 否则一条长回复会切成几十次小额请求（钱与延迟都不划算）。
                // 工具形态（E4）不自动朗读：成品是拿来复制/落地的，念一遍不是它的用途（台账 §5.1）。
                autoTts = engine.autoTts && settings.ttsAutoRead && !useBuiltinTts(ttsSettings)
                autoTtsBuiltin = engine.autoTts && settings.ttsAutoRead && useBuiltinTts(ttsSettings)
                if (engine.autoTts && settings.ttsAutoRead) {
                    syncTtsParams(ttsSettings)
                    ttsNotice = tts.unavailableReason
                }
                val reply = AiClient.chatStream(
                    settings = settings,
                    card = card,
                    history = history,
                    userSetting = conv.userSetting,
                    styleHint = styleHint(conv.narrativeStyle()),
                    tailHint = tailHint(conv.narrativeStyle(), card),
                    memory = conv.memory,
                    summary = conv.summary,
                    onDelta = { delta ->
                        received.append(delta)
                        // 打字机开：灌进缓冲、由 runTypewriter 按速率揭示；
                        // 关（工具形态）：增量直接进草稿 —— 流本身是逐字来的，所以观感仍是逐字
                        if (engine.typewriter) appendRecv(delta) else draft = received.toString()
                        // 自动朗读：把增量喂给朗读器，攒够一句就入队（首句开读远早于整条生成完）
                        // ⚠️ 用户按过停止 / 关掉了自动朗读就**不再喂**：停止只清队列的话，
                        // 下一个增量进来又会接着念（第 69 轮）
                        if (autoTts && !ttsUserStopped) tts.feed(delta)
                        // 进程被杀前的保险丝：每积累约 500 字把已生成部分静默落盘。
                        // 只写文件不更新 conversation 状态，UI 不会提前出现半截气泡；
                        // 进程若真的被杀，下次进来会看到这条已保存的部分回复，不再白聊。
                        // swipe 模式不加检查点：旧版本还在，最多丢本次部分回复。
                        sinceCheckpoint += delta.length
                        if (variantOf < 0 && sinceCheckpoint >= 500) {
                            sinceCheckpoint = 0
                            runCatching {
                                Repository.saveConversation(
                                conv.copy(
                                    messages = messagesBeforeReply + ChatMessage(role = "assistant", content = received.toString()),
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                },
                    onCall = { activeCall = it }
                )
                // 等打字机把剩余内容吐完，避免最后一下整段蹦出来
                streamDone = true
                typer?.join()
                // 自动朗读：把尾巴（最后一句没说满的）也读掉
                if (autoTts && !ttsUserStopped) tts.finish()
                val trimmed = reply.trim()
                if (trimmed.isNotBlank()) {
                    softNotice = null // 这次正常拿到正文了，撤掉上次的空回复提示
                    oneShotHint = ""  // 临时导演已兑现，撤下来（只作用一条）
                    if (variantOf >= 0) {
                        // swipe：追加为新版本并切换过去（目标消息不在了就整个跳过）
                        updateConversation(targetId) { stored ->
                            val target = stored.messages.getOrNull(variantOf)
                            if (target != null && target.role == "assistant") {
                                val updated = stored.messages.toMutableList()
                                updated[variantOf] = target.withVariant(trimmed)
                                stored.copy(messages = updated, updatedAt = System.currentTimeMillis())
                            } else null
                        }
                    } else {
                        // ⚠️ **必须以 messagesBeforeReply 为底，不能往 `stored.messages` 上追加**（2026-09-15 第 15 轮查到）：
                        // 超过 500 字的回复，生成本身已经通过上面的"静默检查点"把半截内容写成了一条 assistant 消息；
                        // 再追加一条完整回复，聊天记录里就会**同时出现一条半截和一条完整**（用户看到的是回复重复两遍）。
                        // 用同一份基线覆盖，检查点写下的那条就被完整回复顶掉了。
                        // 并发安全性与检查点一致：生成期间禁止切换会话，且这里按 id 写回原会话。
                        updateConversation(targetId) { stored ->
                            stored.copy(
                                messages = messagesBeforeReply + ChatMessage(role = "assistant", content = trimmed),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    }
                    // v2 自动朗读：整条落地后再合成（见上面 autoTtsBuiltin 的说明）
                    if (autoTtsBuiltin && !ttsUserStopped && trimmed.isNotBlank()) {
                        ttsSpeaking = true
                        viewModelScope.launch {
                            runCatching { tts.speakBuiltin(trimmed) { piece -> synthToFile(ttsSettings, piece) } }
                            tts.lastFallbackReason?.let { ttsNotice = it }
                            ttsSpeaking = false
                        }
                    }
                    // 回复落库后：长会话自动更新前情提要 + 本局状态（都按 id 写回原会话）。
                    // 玩法型不做前情提要（理由同上：一局延续的是进度，不是剧情摘要）
                    Repository.getConversation(targetId)?.let { latest ->
                        if (engine.promptMode != PromptMode.PLAY) {
                            maybeUpdateSummary(targetId, latest.messages, settings.historyLimit)
                        }
                        maybeUpdateMemory(targetId, latest.messages)
                    }
                    draft = ""
                } else {
                    // 空回复：非模态提示，聊天页保持可用（见 softNotice 的说明）
                    softNotice = "模型这次没有返回内容，可以再发一次试试"
                }
            } catch (t: Throwable) {
                // 流中途断掉（网络错、用户离开页面）也把已生成的部分保存下来，避免白聊。
                // ⚠️ 同上面的落库修正（2026-09-15 第 15 轮）：
                // ① 以 messagesBeforeReply 为底覆盖，**不能往 stored.messages 追加** ——
                //    否则超过 500 字的回复会在"检查点写了半截"之外再追加一条，聊天记录里出现两条残缺回复；
                // ② 取 `received`（模型实际发来的全部）而不是 `draft`（打字机已揭示的），
                //    后者通常落后于前者，用它会把已经拿到手的内容写少。
                streamDone = true
                runCatching { typer?.join() } // 作用域已取消（离开页面）时 join 会立刻抛，不该挡住下面的保存
                // 自动朗读：中断/失败时把已经拿到的部分读完（用户听到的就是屏幕上留下的那段）
                if (autoTts && !ttsUserStopped) tts.finish()
                val partial = received.toString().trim().ifBlank { draft.trim() }
                if (partial.isNotBlank() && variantOf < 0) {
                    updateConversation(targetId) { stored ->
                        stored.copy(
                            messages = messagesBeforeReply + ChatMessage(role = "assistant", content = partial),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
                draft = ""
                // 空回复（soft）不当"出错了"：走非模态提示，聊天页照常可点（见 softNotice）
                if (t is AiException && t.soft) {
                    softNotice = t.message ?: "模型没有返回内容"
                } else {
                    error = t.message ?: "请求失败"
                }
                // 作用域取消（用户离开页面）走正常取消语义，不要在这里被当成"处理完了"
                if (t is kotlinx.coroutines.CancellationException) throw t
            } finally {
                streamDone = true
                typer?.cancel()
                sending = false
                draft = ""
                clearRecv()
            }
        }
    }

    /**
     * 重新生成某条助手回复。
     * 最后一条：swipe——保留现有版本，新回复追加为下一个版本；
     * 中间的：维持原行为——回滚到触发它的那条用户消息重请求（会删掉后续内容）。
     */
    fun regenerate(index: Int) {
        val conv = conversation ?: return
        if (sending) return
        val msg = conv.messages.getOrNull(index) ?: return
        if (msg.role != "assistant") return
        if (index == conv.messages.lastIndex) {
            requestReply(conv.messages.subList(0, index), variantOf = index)
        } else {
            var u = -1
            for (i in index - 1 downTo 0) {
                if (conv.messages[i].role == "user") { u = i; break }
            }
            if (u < 0) return
            val newMessages = conv.messages.take(u + 1)
            persist(conv.copy(messages = newMessages, updatedAt = System.currentTimeMillis()))
            requestReply(newMessages)
        }
    }

    /**
     * 「按格式重排」（E4 收尾）：把上一条成品按卡的「输出格式」重排一遍。
     *
     * 与 [regenerate] 分开的原因在 [OutputFormats.reformatHistory] 的注释里：重排**必须让待重排的成品
     * 留在上下文里**（regenerate 会把它摘掉，那就成了"照素材重做一遍"，实测多半给出同一份排版）。
     *
     * 结果落成**新的一条消息、不覆盖原来那版**：工具形态里成品是要拿走的，替换掉等于让用户丢东西
     * （与「换一批」追加而非替换同一口径）。
     */
    fun reformat(index: Int) {
        val conv = conversation ?: return
        if (sending) return
        val card = character ?: return
        // 没写格式要求就没有"重排"可言（菜单那时也不会显示这一项，这里是第二道）
        if (card.outputFormat.isBlank()) return
        val history = OutputFormats.reformatHistory(conv.messages, index) ?: return
        requestReply(history)
    }

    /** swipe：切换某条消息的生效版本 */
    fun switchVariant(index: Int, variant: Int) {
        val conv = conversation ?: return
        val msg = conv.messages.getOrNull(index) ?: return
        val updated = conv.messages.toMutableList()
        updated[index] = msg.switchedTo(variant)
        persist(conv.copy(messages = updated, updatedAt = System.currentTimeMillis()))
    }

    /** swipe：删除某个版本（至少保留一个） */
    fun deleteVariant(index: Int, variant: Int) {
        val conv = conversation ?: return
        val msg = conv.messages.getOrNull(index) ?: return
        val updated = conv.messages.toMutableList()
        updated[index] = msg.withoutVariant(variant)
        persist(conv.copy(messages = updated, updatedAt = System.currentTimeMillis()))
    }

    /** 编辑任意一条消息（含场景卡），同步 swipe 版本 */
    fun editMessage(index: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val conv = conversation ?: return
        val msg = conv.messages.getOrNull(index) ?: return
        val updated = conv.messages.toMutableList()
        updated[index] = msg.edited(trimmed)
        persist(conv.copy(messages = updated, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 回溯（创建分支）：复制当前会话为「原名 · 分支」，截断到 [index] 之前（不含该条），
     * 原会话原样保留，并切换到分支继续。
     */
    fun branchAt(index: Int) {
        val conv = conversation ?: return
        if (sending) return
        val branch = conv.copy(
            id = Repository.newId(),
            title = if (conv.title.isBlank()) "分支会话" else "${conv.title} · 分支",
            messages = conv.messages.take(index),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        Repository.saveConversation(branch)
        refreshList()
        conversation = branch
    }

    /** 回溯（直接）：保留 index 之前的所有消息，删除 index 及其后的消息 */
    fun rollback(index: Int) {
        val conv = conversation ?: return
        if (sending) return
        val keep = conv.messages.take(index)
        persist(conv.copy(messages = keep, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 清掉只属于"上一个会话"的临时 UI 状态（P1-14）。
     * 灵感回复列表、打字机草稿都是 ViewModel 级（非按会话存）的，
     * 换会话不清就会在 B 会话里看到 A 会话的灵感建议 / 半截气泡。
     * 只在**非流式**时调用（切会话/新建在 sending 期间已被拦下）。
     */
    private fun resetTransientUi() {
        draft = ""
        inspirations = emptyList()
        inspirationError = null
        showInspiration = false
        // 切会话 / 新建 / 删除后立刻停读：否则会把上一个会话的回复念到新会话里（设计口径 3）
        stopSpeaking()
    }

    fun startNewConversation() {
        // 回复生成中不允许换会话：打字机气泡与 draft 状态是全局的，切过去会看到"幽灵气泡"，
        // 与 regenerate / rollback / branchAt 的守卫保持一致
        if (sending) {
            showHistory = false
            error = "回复生成中，请等这条回复结束再新建会话"
            return
        }
        val c = newConversation(null, withGreeting = true)
        persist(c)
        resetTransientUi()
        showHistory = false
    }

    fun switchTo(convId: String) {
        if (sending) {
            showHistory = false
            error = "回复生成中，请等这条回复结束再切换会话"
            return
        }
        Repository.getConversation(convId)?.let {
            conversation = it
            resetTransientUi()
            showHistory = false
        }
    }

    fun requestDelete(convId: String) {
        deletingId = convId
    }

    fun confirmDelete() {
        val id = deletingId ?: return
        if (sending) {
            deletingId = null
            showHistory = false
            error = "回复生成中，请等这条回复结束再删除会话"
            return
        }
        deletingId = null
        showHistory = false
        Repository.deleteConversation(id)
        refreshList()
        if (conversation?.id == id) {
            conversation = newConversation(null, withGreeting = true)
            persist(conversation!!)
        }
    }

    fun cancelDelete() {
        deletingId = null
    }

    /**
     * 设置会话自己的背景（本地选图），覆盖角色默认背景。
     *
     * A 批次分端：只写 [target] 那一端的字段（默认本平台那份），另一端不动。
     * 回收的是**被替换的那个字段的原值**，不是"当前生效值"——桌面端那份没单独设过时，
     * 生效值是继承来的手机端那张，回收它等于把手机端背景删掉（好在引用检查会拦住，
     * 但语义上就不该这么写）。
     */
    fun setBackground(uri: String?, target: BgTarget = bgMenuTarget) {
        val conv = conversation ?: return
        val old = if (target == BgTarget.Desktop) conv.backgroundUriDesktop else conv.backgroundUri
        if (old == uri) return
        persist(
            if (target == BgTarget.Desktop) conv.copy(backgroundUriDesktop = uri)
            else conv.copy(backgroundUri = uri)
        )
        // 换掉/移除旧背景后回收它（引用检查在 Repository 里做，角色默认背景不会被误删）（P1-10）
        Repository.reclaimImages(listOf(old))
    }

    /** 重命名当前会话 */
    fun renameCurrent(title: String) {
        val trimmed = title.trim()
        val conv = conversation ?: return
        if (trimmed.isNotBlank()) {
            persist(conv.copy(title = trimmed))
        }
        showRenameDialog = false
    }

    /** 为本会话应用一段用户设定文本（改变会持久化到该会话） */
    fun applyUserSetting(content: String) {
        val conv = conversation ?: return
        persist(conv.copy(userSetting = content.trim()))
        showSettingDialog = false
    }

    var showToolPanel by mutableStateOf(false)

    /** 「叙事设置」高级页：全屏覆盖在聊天页之上（基础面板只给节奏 + 密度，其余轴进这里） */
    var showStyleScreen by mutableStateOf(false)

    /**
     * 写入叙事风格（P-F1）：所有轴统一走这一个入口（基础面板与「叙事设置」页共用）。
     *
     * 老会话首次落盘即完成迁移：[narrativeStyle] 会从旧的 `pacing` / `atmosphere` 推出等效风格，
     * 这里把"推出来的 + 这次改的"整份写进 `narrative`，此后旧字段不再参与。
     */
    fun applyNarrative(style: NarrativeStyle) {
        val conv = conversation ?: return
        persist(conv.copy(narrative = style, updatedAt = System.currentTimeMillis()))
    }

    /**
     * 上一次请求实际用过的风格（**只在内存里**，不落盘）：用来判断"这次是不是刚改过风格"。
     * 历史里几十条短回复是极强的 few-shot 锚点，光把新要求写进系统提示词，模型常常继续按老习惯写
     * （用户 2026-09-16 实测反馈"选了沉浸小说但篇幅还是不够"），所以要额外给一句告知。
     */
    private var lastSentStyle: NarrativeStyle? = null

    /** 风格刚改过 / 本会话第一次发送就带一句"按新风格写"的告知（只进本次请求，不入库） */
    private fun styleNotice(style: NarrativeStyle): String {
        val prev = lastSentStyle
        lastSentStyle = style
        if (prev == style) return ""
        val first = prev == null
        // 点名这次**真正生效**的档位（动态拼）：写死"篇幅密度与叙事节奏"的话，只开「古风」时会文不对题
        val labels = NarrativeStyles.labelsOf(style).joinToString(" · ")
        return when {
            style.isDefault && !first ->
                "【风格已重置】不再约束节奏、篇幅、笔法、情绪、视角与时间处理，按基础提示词自然生成。"
            style.isDefault -> ""
            first ->
                "【风格提醒】严格按上面【叙事风格】的设置书写（$labels），不要沿用上文（含你自己历史回复）形成的篇幅与笔法习惯。"
            else ->
                "【风格刚修改】从本条起按新设置书写（$labels），不要沿用此前回复的篇幅与笔法。"
        }
    }

    /** 注入用的风格段 = 「刚改过风格」的告知 + 轴表组装出的【叙事风格】（全默认且无需告知时为空串，不注入） */
    private fun styleHint(style: NarrativeStyle): String {
        val base = NarrativeStyles.buildHint(style, styleNotice(style))
        return if (base.isNotBlank()) base + "\n" else ""
    }

    /**
     * 贴到**用户本轮消息末尾**的本轮指令（角色后置指令 + 硬性要求 + 单条特别指示）——近因最强处。
     *
     * 2026-09-16 真 key 实测（详见 `NarrativeStyles.hardReminder` 与 `AiClient.withTailHint`）：
     * 这些"这一条必须怎样"的话放系统提示词里几乎不生效，贴着生成点才生效；
     * 单条临时导演也从系统提示词最末挪到了这里（同一处只留一份，不重复占字）。
     *
     * 角色卡的 `post_history_instructions`（E2）排在最前：它是**长期**要求（作者设定的始终要遵守的规则），
     * 而硬性要求与本轮特别指示是**这一条**的事 —— 越是本条的事越要贴近生成点（顺序即优先级）。
     */
    private fun tailHint(style: NarrativeStyle, card: CharacterCard?): String = buildString {
        val post = card?.postHistory?.trim().orEmpty()
        if (post.isNotBlank() && card != null) {
            append("【角色后置指令（本卡作者设定，始终遵守；与视角边界冲突时以视角边界为准）】\n")
            append(AiClient.expandMacros(post, card)).append('\n')
        }
        // 工具形态（E4）：同一套机制（贴着生成点）、**不同数据源** —— 7 轴已隐藏，
        // 改由「输出格式」的可核对硬要求产出。实测有效的正是这种"可直接核对"的话
        // （"只给 10 个编号名字、不要别的字"），而不是抽象的文学风格（台账 §5.2 修订 2）。
        val fmt = card?.let { AiClient.expandMacros(it.outputFormat, it).trim() }.orEmpty()
        if (engine.promptMode == PromptMode.TOOL) {
            if (fmt.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("【输出格式（必须逐条满足，格式不对就重做）】\n")
                append(fmt)
            }
        } else if (engine.promptMode == PromptMode.PLAY) {
            // 玩法形态（E4 拆档）：这里**必须换掉**叙事风格的硬性要求 —— 它会要求"本回合写满 250~500 字"，
            // 而玩法正好相反（一次一回合、要短）。文本收在 AiClient（纯函数：自检也断言这一段），
            // 卡里的长篇规则仍留在系统提示词，不逐条重贴（那会每轮多花一份 token）。
            if (isNotEmpty()) append('\n')
            append(AiClient.playTurnRequirement(card))
        } else {
            val reminder = NarrativeStyles.hardReminder(style)
            if (reminder.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(reminder)
            }
        }
        if (oneShotHint.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append("【本轮特别指示（仅本条生效，优先级高于上面所有风格与默认设定，不改变长期设定）】")
            append(oneShotHint.trim())
            append(" 只在本条满足，下一条自动回到会话既定风格；仍遵守视角边界与排版，不替用户说话。")
        }
    }

    /**
     * 单条临时导演的文本（**只在内存里**，不落盘也不进聊天记录）：
     * 一次性指令要"用完即弃"，跨重启还留着反而会让人莫名其妙；
     * 出错/空回复时**不清**（用户重试就是想要同一条指令再生一次）。
     */
    var oneShotHint by mutableStateOf("")

    /** AI 生成聊天背景：先生成待确认，由用户选 保存/应用/重试（问题 #8）。尺寸在生图时选（#6） */
    fun generateBackground(prompt: String, size: String = "", target: BgTarget = bgMenuTarget) {
        if (generatingBg) return
        lastBgPrompt = prompt
        lastBgSize = size
        pendingBgTarget = target
        generatingBg = true
        showBgPrompt = false
        showBgMenu = false
        viewModelScope.launch {
            try {
                val settings = Repository.loadSettings()
                pendingBg = AiClient.generateImage(settings, prompt, size)
            } catch (t: Throwable) {
                error = t.message ?: "背景生成失败"
            } finally {
                generatingBg = false
            }
        }
    }

    /** 把预览中的背景正式应用到当前会话（final 可能是裁剪后的新文件） */
    fun applyPendingBackground(final: String) {
        // 用生成时定下的那一端，而不是菜单现在选的是谁（用户可能在等待期间又去点了选端）
        setBackground(final, pendingBgTarget)
        pendingBg = null
    }

    /** 用同一个提示词、同一尺寸重新生成 */
    fun retryBackground() {
        val target = pendingBgTarget
        pendingBg = null
        if (lastBgPrompt.isNotBlank()) generateBackground(lastBgPrompt, lastBgSize, target)
    }

    /** 放弃这次生成结果（仅不入库，文件会随缓存清理回收） */
    fun discardPendingBackground() {
        pendingBg = null
    }

    fun clearError() {
        error = null
    }

    /**
     * 非模态提示：给"模型这次没吐正文"这类**不是出错**的情况用（`AiException.soft`）。
     *
     * 原来这类也走 [error] → `ErrorBanner` 是个**模态** AlertDialog，整页被盖住，
     * 用户既看不到自己的消息、也点不到「灵感回复」（2026-09-16 用户反馈"模型没返回内容时没有灵感回复"）。
     * 现在改成输入框上方一行可点掉的小提示：聊天页照常可用，重试与「灵感回复」都够得着。
     */
    var softNotice by mutableStateOf<String?>(null)

    companion object {
        /** 打字机每拍间隔（毫秒）：16ms ≈ 60fps，越小越顺 */
        private const val TYPER_TICK_MS = 16L
        /** 剩余内容按多少拍追平：36 拍 × 16ms ≈ 0.58 秒（原 60×25≈1.5s，偏慢） */
        private const val TYPER_CATCHUP_TICKS = 36

        fun factory(characterId: String, conversationId: String) =
            viewModelFactory {
                initializer { ChatViewModel(characterId, conversationId) }
            }
    }
}

/**
 * 造聊天页 ViewModel。原来单独抽出来是因为系统 TTS 朗读器需要应用 Context；
 * M3 起语音能力走 [Voice]/[Platform] 注入，ViewModel 不再需要 Context，
 * 这里保留这层薄壳是为了默认参数形态不变（也方便以后再加依赖）。
 */
@Composable
internal fun chatViewModel(characterId: String, conversationId: String): ChatViewModel =
    viewModel(factory = ChatViewModel.factory(characterId, conversationId))
