package com.mysticat.roleplay.data

import java.util.Locale
import java.io.File

/**
 * 语音朗读调度器（P-F2 v1：**系统 TTS**；v2：供应商合成）。
 *
 * 为什么 v1 先用系统 TTS：不需要任何新权限、免费、离线、任何供应商都能用；
 * 代价是音色取决于设备（中文语音包缺失时要明确提示去系统设置装，而不是静默不出声）。
 * v2 在这条调度链上加了"供应商合成"分支（OpenAI 兼容 `/audio/speech`），
 * 清洗 / 切句 / 生命周期逻辑两条路共用 —— 换引擎只换最后"怎么发声"那一步。
 *
 * 跨平台拆分：本类只剩**编排**（切句、攒句、队列、回退）；"怎么发声"走 [TtsEngine]
 * （Android=系统 TextToSpeech，见 androidMain）、"怎么放音频文件"走 [AudioPlayerEngine]
 * （Android=MediaPlayer），都由宿主入口经 [Voice] 注入。线程约定不变：公开方法都在主线程调用，
 * 引擎/播放器的回调也要求实现方切回主线程再调。
 *
 * 三个关键实现点（设计文档点名的坑）：
 * 1. **引擎就绪前的文本必须先缓存**：引擎初始化是异步的，此前的 speak() 会被直接丢掉，
 *    用户看到的就是"点了朗读没反应"（尤其第一条）。
 * 2. **按句切分后增量入队**：配合真流式边生成边读，首句开读的等待远短于整条生成完。
 *    流式喂进来的文本会停在句子中间，所以留一个 `tail` 缓冲，只有见到句末标点才切出去。
 * 3. **`stop()` 必须同时清队列**：切会话 / 离开页面 / 重新生成时不停就会串台朗读。
 *
 * 播放状态**不逐帧塞进 Compose 状态**：只在"开始 / 读完 / 出错"这几个低频事件上回调（见 [onState]），
 * 由调用方转成一个布尔状态给按钮用。
 */
class TtsSpeaker(
    /** 状态回调（已在主线程）：STARTED 开读、DONE 队列读完、ERROR 出错 */
    private val onState: (State) -> Unit = {}
) {
    enum class State { STARTED, DONE, ERROR }

    private val engine: TtsEngine? = Voice.ttsEngineFactory()

    init {
        // 引擎就绪时把"初始化期间攒下的句子"接手读掉（坑 1 的另一半：只攒不吐＝首句静默丢失）
        engine?.onReady = { flushEarlyQueue() }
    }

    /** 系统语音包不可用的原因（null = 可用）。UI 据此提示用户去系统设置装语音包 */
    val unavailableReason: String?
        get() = engine?.unavailableReason ?: "本机不支持系统语音合成".takeIf { engine == null }

    /** 引擎就绪前攒下的片段 */
    private val earlyQueue = ArrayDeque<String>()

    /** 流式输入里"还不成句"的尾巴 */
    private val tail = StringBuilder()

    /**
     * 太短的片段**先攒着**，与下一片合并成一句再读。
     *
     * 实测（2026-09-16 模拟器）：「（…）疯了？！（…）攻后颈！」这种排版先被切成 "疯了？" / "！" / "，攻后颈！"
     * 三片，逐片送进引擎听起来是一顿一顿的碎句、还夹一个只有"！"的空片。
     * 攒到 [MIN_PIECE] 字再读，既不碎也不会拖太久。
     */
    private val carry = StringBuilder()

    /** 已交给引擎、还没读完的片段数（读完归零才算真的读完） */
    private var pendingPieces = 0

    var speaking = false
        private set

    /**
     * 朗读状态位：**所有**赋值都走这里，因为它是"背景音乐让路"的唯一触发点。
     *
     * 系统 TTS 与供应商合成都经过它（两条路都会把 `speaking` 置真/置假），所以不必各自埋钩子。
     * 恢复不是立刻的——句与句之间引擎会短暂空闲、状态会掉一下再升回来，
     * 立即恢复会让 BGM 在一句话中间反复开合（延迟的做法与理由在 [BgmPlayer.unduckAfterSpeech]）。
     */
    private fun setSpeaking(v: Boolean) {
        if (speaking == v) return
        speaking = v
        if (v) BgmPlayer.duckForSpeech() else BgmPlayer.unduckAfterSpeech()
    }

    /** 语速 / 音高 / 是否跳过（）动作描写；调用方在朗读前同步一次 */
    var speed: Float = 1.0f
    var pitch: Float = 1.0f
    var skipActionText: Boolean = true

    /** 上一次真正写进引擎的参数：相同的值不再重复下发（原因见 [applyParamsOnce]） */
    private var appliedRate = Float.NaN
    private var appliedPitch = Float.NaN

    /** 手动朗读一条完整消息：先清掉正在读的（两条叠着读会串台），再整条入队 */
    fun speakMessage(text: String) {
        stop()
        val cleaned = clean(text, skipActionText)
        if (cleaned.isBlank()) return
        splitSentences(cleaned, flushAll = true).first.forEach { emit(it) }
        flushCarry()
    }

    /** 流式喂入（自动朗读用）：攒够一句就入队，读到一半的尾巴留在 [tail] 等下一块 */
    fun feed(delta: String) {
        if (delta.isBlank()) return
        tail.append(delta)
        val (sentences, rest) = splitSentences(tail.toString(), flushAll = false)
        tail.setLength(0)
        tail.append(rest)
        sentences.forEach { s ->
            val c = clean(s, skipActionText)
            if (c.isNotBlank()) emit(c)
        }
    }

    /** 流式结束：把尾巴与攒着的短句都读掉 */
    fun finish() {
        val rest = clean(tail.toString(), skipActionText)
        tail.setLength(0)
        if (rest.isNotBlank()) splitSentences(rest, flushAll = true).first.forEach { emit(it) }
        flushCarry()
    }

    /** 停止朗读并清空队列（切会话 / 离开页面 / 重新生成 / 用户点停止）：系统引擎与播放器一起停 */
    fun stop() {
        epoch++ // 先作废在跑的那一代（下一次检查就退出），再清队列——顺序反了会让它多念一句
        tail.setLength(0)
        carry.setLength(0)
        earlyQueue.clear()
        pendingPieces = 0
        setSpeaking(false)
        stopPlayer()
        runCatching { engine?.stopEngine() }
    }

    /** 收下一片：去掉句首标点，够长了（或流结束）才真的送进引擎 */
    private fun emit(piece: String) {
        // 句首标点要去干净：上一句的"！"会被切到下一片的开头（实测出现过"！瞥见它后颈…"）
        val p = piece.trim().trimStart('，', '、', '。', '；', ';', ',', ' ', '！', '？', '!', '?', '…')
        if (p.isBlank()) return
        carry.append(p)
        if (carry.length >= MIN_PIECE) flushCarry()
    }

    private fun flushCarry() {
        val s = carry.toString().trim()
        carry.setLength(0)
        if (s.isNotBlank()) enqueueRaw(s)
    }

    /** 页面销毁时调用：停播 + 释放引擎 */
    fun shutdown() {
        stop()
        runCatching { engine?.shutdown() }
    }

    // ─────────────────────── v2：供应商合成（OpenAI 兼容 /audio/speech）───────────────────────

    /** 供应商合成的取音频函数：拿到某段文本对应的**本地音频文件**（命中缓存就直接返回已有文件） */
    fun interface Synth {
        suspend fun file(text: String): File
    }

    /** v2 播放用的播放器（逐个文件顺序播）。跨线程读写（IO 上起播、主线程停止），故 @Volatile */
    @Volatile
    private var player: AudioPlayerEngine? = null

    /**
     * 朗读代次：**每次 [stop] 与每一次新的朗读都 +1**，只有"当前代次"的循环能继续跑。
     *
     * 为什么原来那个布尔停止位不够：`speakBuiltin` 进门的顺序是 `stop()` → `builtinStopped = false`，
     * 于是**老循环只要当时停在"合成请求"这种挂起点上，就会被新一次朗读原地复活**——它醒来时代次标志
     * 已被新调用重新置假，两个循环并行播放同一串句子：用户听到同一条回复念了两遍，而且 [stop] 只能停掉
     * 后一个循环（`player` 字段被覆盖），前一个会自顾自念完。
     * **混合音色恰好把这条路径放大**：开启后整条回复都走"按句合成"（每句一个网络往返），
     * 挂起窗口比系统 TTS 那条路长得多，于是"念多遍"只在供应商合成 / 混合音色下被看见。
     * 代次只增不减，老代次**再也无法被重新激活**——这正是它与布尔位的本质区别。
     */
    @Volatile
    private var epoch = 0

    /** 这一代朗读是否已经作废（用户点了停止 / 切了会话 / 又有了一次新朗读） */
    private fun aborted(my: Int): Boolean = epoch != my

    /**
     * v2：整条消息走**供应商合成**（按句合成、边合成边播）。
     *
     * 与 v1 的分工：清洗 / 切句 / 生命周期仍然共用，只把"怎么发声"从系统引擎换成 `synth` + 播放器。
     * 一段合成失败就**这一段起回退系统 TTS**（用户至少能听到），失败原因通过 [lastFallbackReason] 交给调用方提示。
     * 调用方需要在协程作用域里调用（合成是网络请求）。
     */
    suspend fun speakBuiltin(text: String, synth: Synth) {
        stop()
        val my = epoch // 领下当前这一代（stop() 刚 +1，所以这必然是新一代；老一代再也回不来）
        lastFallbackReason = null
        val cleaned = clean(text, skipActionText)
        if (cleaned.isBlank()) return
        val pieces = splitSentences(cleaned, flushAll = true).first.filter { hasReadableChar(it) }
        if (pieces.isEmpty()) return
        onState(State.STARTED)
        setSpeaking(true)
        for ((index, piece) in pieces.withIndex()) {
            if (aborted(my)) return
            // 瞬时失败（并发超限/网络抖动）先重试一次再回退：一条长消息按句要发十几次请求，
            // 任何一次抖动都让"语音明明在放"的同时弹出"合成失败"（用户报的通病）
            var file: File? = null
            var failure: Throwable? = null
            for (attempt in 1..SYNTH_ATTEMPTS) {
                try {
                    file = synth.file(piece)
                    break
                } catch (t: Throwable) {
                    failure = t
                    if (aborted(my)) return
                    if (attempt < SYNTH_ATTEMPTS) kotlinx.coroutines.delay(SYNTH_RETRY_MS)
                }
            }
            // 合成是**挂起点**（网络往返），中途随时可能被新一次朗读 / 用户停止作废——
            // 作废了就直接退出，绝不"接着把这一代念完"
            if (aborted(my)) return
            if (file == null) {
                // 真失败：从这一段起改用系统 TTS，别让用户面对静音。失败原因要落日志——
                // 此前只存进 lastFallbackReason，日志里全是成功记录，"已出声仍报失败"根本无从排查
                val reason = failure?.message ?: "语音合成失败，已改用系统语音"
                lastFallbackReason = reason
                WhaleLog.w("WhaleTts", "第 ${index + 1}/${pieces.size} 段合成失败，回退系统语音：$reason")
                enqueueRaw(piece)
                continue
            }
            // 合成成功但**播不出来**同样要留痕 + 回退（2026-09-17 #6：桌面这条路上曾经整条静默）
            if (!playAndWait(file, my)) {
                lastFallbackReason = playbackError ?: "音频播放失败，已改用系统语音"
                WhaleLog.w("WhaleTts", "第 ${index + 1}/${pieces.size} 段起播失败，回退系统语音：$lastFallbackReason")
                enqueueRaw(piece)
            }
        }
        if (aborted(my)) return
        setSpeaking(false)
        onState(State.DONE)
    }

    private fun hasReadableChar(s: String): Boolean = s.any { it.isLetterOrDigit() }

    /** 最近一次"回退系统 TTS"的原因（调用方读它给一行提示；null = 没回退） */
    var lastFallbackReason: String? = null
        private set

    /** 最近一次起播失败的原因（播放器给的，见 [AudioPlayerEngine.lastError]） */
    @Volatile
    var playbackError: String? = null
        private set

    /**
     * 正在"等这一段播完"的完成信号（[stopPlayer] 主动 complete 它来打断等待）。
     *
     * 为什么必须有这个字段：播放器的 stop/release **不会**触发完成回调，
     * 光靠监听器的话，每次"停止朗读 / 切会话 / 重新生成"都会留下一个**永久挂起**的协程
     * （直到 VM 被清才以 CancellationException 收尾，还会被上层当成"合成失败"）。
     *
     * 用 [kotlinx.coroutines.CompletableDeferred] 而不是 `CancellableContinuation`：
     * 起播被挪到 IO 线程后，"播完回调"与"主线程挂起"之间**没有先后保证**
     * （很短的音频可能在主线程还没挂起时就已经播完了），`complete()` 天然幂等且线程安全，
     * 不会出现"重复 resume"或"回调时 continuation 还没登记"这两类竞态。
     */
    @Volatile
    private var pendingDone: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /**
     * 播一个文件并等它播完（stop() 会打断等待）。
     * 返回 false = **起播即失败**（此时调用方应回退系统 TTS，别让用户面对静音）。
     *
     * ⚠️ **`p.start()` 必须离开主线程**（踩出来的第二条）：它要解码整段音频
     * （`Clip.open` 同步读完整个流），在主线程上跑就是"点一下朗读，界面卡住"。
     * 实测撞上过一次真正的死循环：源流被提前关闭时 mp3spi 会一直重试读到 EOF，
     * EDT 空烧 **56 秒 CPU 且永不返回**，界面完全点不动（用户报的"点语音播放就崩了/哪里都点不了"）。
     * 挪到 IO 之后多了一个交接窗口（起播期间用户可以点停止），所以回来时要再确认一次
     * 自己还是当前播放器，不是就立刻收尾（否则这段音频会在"已停止"之后继续响）。
     */
    private suspend fun playAndWait(file: File, my: Int): Boolean {
        // 这一代在排队等播的时候已经被作废（用户点了停止 / 又点了一次朗读）→ 根本不要起播。
        // 返回 true 是"没失败"的意思：调用方据此不再走"回退系统语音"，而它自己会在下一轮代次检查里退出。
        if (aborted(my)) return true
        val p = Voice.audioPlayerFactory()
        player = p
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        pendingDone = done
        val started = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                p.start(file) {
                    if (player === p) player = null
                    done.complete(Unit)
                }
            }.getOrDefault(false)
        }
        if (!started) {
            // 起播即失败：记下播放器给的原因（它一定要有——见 #6 的教训）
            playbackError = p.lastError ?: "播放器起播失败（未给出原因）"
            if (player === p) player = null
            if (pendingDone === done) pendingDone = null
            return false
        }
        playbackError = null
        if (aborted(my) || player !== p) {
            // 起播期间用户已经点了停止、或有了一次新朗读（player 被清掉/换掉）→ 立刻收尾，别让这段继续响
            runCatching { p.stopAndRelease() }
            if (pendingDone === done) pendingDone = null
            return true
        }
        try {
            done.await()
        } catch (t: kotlinx.coroutines.CancellationException) {
            runCatching { p.stopAndRelease() }
            throw t
        } finally {
            if (pendingDone === done) pendingDone = null
        }
        return true
    }

    /** v2：停掉播放器（与 v1 的 stop() 同一个入口；作废代次由 [stop] 负责） */
    private fun stopPlayer() {
        // 唤醒"等播完"的协程（见 pendingDone 的说明）
        pendingDone?.complete(Unit)
        pendingDone = null
        runCatching { player?.stopAndRelease() }
        player = null
    }

    /** 引擎没就绪就先攒着，就绪后由 [flushEarlyQueue] 统一吐出去（坑 1） */
    private fun enqueueRaw(sentence: String) {
        if (sentence.isBlank()) return
        val e = engine
        if (e == null || !e.ready) {
            earlyQueue.addLast(sentence)
            if (earlyQueue.size > 60) earlyQueue.removeFirst() // 初始化失败时不无限攒
            return
        }
        speakNow(sentence)
    }

    /**
     * 引擎就绪（[TtsEngine.onReady]）时把攒下的句子按原顺序读出去。
     * 期间用户点了停止（[stop] 会清空队列）就自然没得吐；只读快照，避免回调里再改队列。
     */
    private fun flushEarlyQueue() {
        if (earlyQueue.isEmpty()) return
        val pending = earlyQueue.toList()
        earlyQueue.clear()
        pending.forEach { if (engine?.ready == true) speakNow(it) }
    }

    private fun speakNow(sentence: String) {
        val e = engine ?: return
        runCatching {
            applyParamsOnce()
            // 只记字数、**不记正文**：角色扮演的回复内容隐私性高（与 WhaleVision"只记 base64 长度"一个口径），
            // 排查时看"有没有走到引擎"看这行就够了
            val accepted = e.speak(
                sentence,
                onStarted = {
                    setSpeaking(true)
                    onState(State.STARTED)
                },
                done = { success ->
                    pendingPieces = (pendingPieces - 1).coerceAtLeast(0)
                    if (!success) {
                        // 失败在真机上的常见原因：语音包缺失、或引擎的语音需要联网下载
                        if (pendingPieces == 0) setSpeaking(false)
                        onState(State.ERROR)
                    } else if (pendingPieces == 0) {
                        setSpeaking(false)
                        onState(State.DONE)
                    }
                }
            )
            if (accepted) {
                pendingPieces++
                setSpeaking(true)
            }
        }
    }

    /**
     * 语速 / 音高**只在真正变化时下发一次**。
     *
     * 为什么不每句都设（2026-09-16 用户反馈"朗读越读越快、好像无视了语速设置"）：
     * `TextToSpeech.setSpeechRate()` 在 AOSP 里是写进一个**共享的 params Bundle**，
     * 每次 `speak()` 都会带上它 —— 逐句重复下发同一个值，在不同引擎上表现不一致
     * （有的引擎把重复下发当成一次新的"相对调整"，听起来就是一句比一句快）。
     * 本机模拟器实测（1.5×，三句 44/51/53 字）每字耗时 112/86/89 ms，没有逐句加速（首句偏慢是引擎预热），
     * 所以这条按"不做无意义的重复下发"来防：值不变就不发，值变了（用户拖了滑杆）才发。
     */
    private fun applyParamsOnce() {
        val e = engine ?: return
        if (appliedRate == speed && appliedPitch == pitch) return
        runCatching {
            e.applyParams(speed, pitch)
            appliedRate = speed
            appliedPitch = pitch
        }
    }

    companion object {
        /**
         * 朗读前的文本清洗：去掉 markdown 标记与朗读没意义的符号。
         *
         * 本项目排版约定的两个特例：
         * - `§` 是"章节式"风格的小标题前缀，读出来只是噪音，去掉符号、保留标题文字；
         * - `（）/()` 是动作神态描写的括注，[skipAction] 为真时整段跳过（只读对白更顺耳）。
         *   流式切片可能把一对括注从中间切开，所以最后**把残留的括号也去掉**，不让引擎去念"括号"。
         */
        fun clean(text: String, skipAction: Boolean): String {
            var s = text
            s = s.replace(Regex("```[\\s\\S]*?```"), " ")          // 代码块整体去掉
            s = s.replace(Regex("`([^`]*)`"), "$1")                 // 行内代码
            s = s.replace(Regex("\\*\\*([^*]*)\\*\\*"), "$1")       // **加粗**
            s = s.replace(Regex("\\*([^*]*)\\*"), "$1")             // *斜体*
            s = s.replace(Regex("__([^_]*)__"), "$1")               // __加粗__
            s = s.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1") // [文字](链接) → 文字
            s = s.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")          // 标题 #
            s = s.replace(Regex("(?m)^\\s*[-*•]\\s+"), "")           // 列表项
            s = s.replace(Regex("(?m)^\\s*\\d+[.、)]\\s+"), "")      // 有序列表
            s = s.replace("§", " ")                                  // 章节式小标题前缀
            s = if (skipAction) {
                s.replace(Regex("[（(][^）)]*[）)]"), "，")
            } else {
                s.replace(Regex("[（(]([^）)]*)[）)]"), "$1")
            }
            s = s.replace(Regex("[（）()]"), "")                      // 残留的半个括号
            s = s.replace("*", "")
            s = s.replace(Regex("\\s+"), " ")
            // 标点收敛：连续逗号只留一个；去掉" ，"" 。"这种被删段留下的悬空标点；句首标点去掉
            s = s.replace(Regex("，{2,}"), "，")
                .replace(Regex(" ([，。、；;,.])"), "$1")
                .replace(Regex("^[，、。；;,.！？!?…]+"), "")
            return s.trim()
        }

        /**
         * 按句切分；[flushAll] = false 时末尾"还没写完"的部分作为尾巴返回，不切出去
         * （否则会把半句话读出去、下半句又读一遍）。返回 (可直接朗读的句子, 尾部残留)。
         *
         * 长句没有标点时（模型偶尔写很长一段）到逗号就断、仍没有就硬断，别攒着不读。
         */
        fun splitSentences(text: String, flushAll: Boolean): Pair<List<String>, String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            for (c in text) {
                sb.append(c)
                val isEnd = c in "。！？!?；;…\n"
                if (isEnd) {
                    val piece = sb.toString().trim()
                    if (piece.isNotBlank()) out.add(piece)
                    sb.setLength(0)
                } else if (sb.length >= MAX_CHUNK) {
                    val cut = sb.indexOfLast { it == '，' || it == ',' }
                    if (cut > 0) {
                        out.add(sb.substring(0, cut + 1).trim())
                        val rest = sb.substring(cut + 1)
                        sb.setLength(0)
                        sb.append(rest)
                    } else {
                        out.add(sb.toString().trim())
                        sb.setLength(0)
                    }
                }
            }
            val rest = sb.toString()
            if (flushAll) {
                if (rest.isNotBlank()) out.add(rest.trim())
                return out to ""
            }
            return out to rest
        }

        /** 单次入队片段的长度上限（各引擎普遍在 4000 字上下，取小一点更保险） */
        private const val MAX_CHUNK = 120

        /** 短于这个字数的片段先攒着与下一片合并，避免"一顿一顿"的碎句 */
        private const val MIN_PIECE = 8

        /** 单段合成失败先重试的次数与间隔（并发超限一类的瞬时错，隔一下再发就过了） */
        private const val SYNTH_ATTEMPTS = 2
        private const val SYNTH_RETRY_MS = 600L
    }
}
