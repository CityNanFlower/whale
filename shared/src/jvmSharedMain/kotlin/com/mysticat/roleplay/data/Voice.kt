package com.mysticat.roleplay.data

import java.io.File

/**
 * 语音链路的平台接口（朗读 / 录音 / 系统识别）。
 *
 * 编排逻辑（清洗、切句、队列生命周期）留在共享层 [TtsSpeaker]；"怎么发声/怎么收音"
 * 由宿主注入：Android=系统 TextToSpeech + MediaPlayer + MediaRecorder + SpeechRecognizer
 * （见 androidMain 的实现与 App.kt 的装配），桌面接 javax.sound 与系统语音。
 * 宿主忘注入时用安全兜底：朗读不可用（给原因）、录音抛异常、识别不可用——UI 全部有提示路径。
 */
interface TtsEngine {
    /** null = 可用；不可用时给出原因（UI 提示用户去装语音包等） */
    val unavailableReason: String?

    /** 引擎就绪；未就绪时 [speak] 会先攒着（初始化是异步的） */
    val ready: Boolean

    /**
     * 引擎**从"未就绪"变成"就绪"**时回调一次（主线程约定见 [TtsSpeaker]）。
     *
     * 为什么需要这个钩子：初始化是异步的，就绪前进来的句子由 [TtsSpeaker] 攒着；
     * 没有这个回调，那些句子永远没人来取——用户看到的就是"点朗读没反应"（尤其第一条）。
     * Android 的 TextToSpeech.init 回调与桌面的 PowerShell 探测都走它。
     */
    var onReady: (() -> Unit)?

    /** 语速/音量参数；实现侧保证"值不变不重复下发"由共享层负责去重，这里只管透传 */
    fun applyParams(speed: Float, pitch: Float)

    /**
     * 增量入队一句话（QUEUE_ADD 语义）。返回 false = 没受理（当次没进队列）。
     * [onStarted] 在这一片真的开读时回调一次；[done] 在读完(true)/出错(false)时回调一次——均在主线程。
     */
    fun speak(sentence: String, onStarted: () -> Unit, done: (success: Boolean) -> Unit): Boolean

    /** 停止当前朗读并清空引擎侧队列 */
    fun stopEngine()

    /** 释放引擎（页面销毁后不再使用） */
    fun shutdown()
}

/**
 * 音频文件播放器（供应商合成路径用；逐文件顺序播）。
 *
 * 也承担**背景音乐**：[loop] 循环播放、[start] 带初始音量、[setVolume] 播放中调音量
 * （朗读那条路一律用默认值，行为与本轮之前完全一致）。
 * 两件事都放在同一个接口上，是因为"怎么把一段音频放出声"两端各只有一套实现（Android MediaPlayer /
 * 桌面 Clip），BGM 另起一套等于把同一份踩坑史（见 `DesktopAudioPlayer` 的注释）再抄一遍。
 */
interface AudioPlayerEngine {
    /**
     * 同步 prepare + start；播完或起播失败都回调一次 [onFinished]（主线程）；返回 false = 起播即失败。
     *
     * [loop] = true 时**永不回调 [onFinished]**（循环播放没有"播完"这一说），要停只能 [stopAndRelease]。
     * ⚠ [onFinished] 必须是**最后一个参数**：调用方普遍用尾随 lambda 写法（`start(file) { ... }`）。
     */
    fun start(file: File, loop: Boolean = false, volume: Float = 1f, onFinished: () -> Unit): Boolean

    /** 停播并释放资源（不会触发 onFinished，由调用方负责唤醒等待的协程） */
    fun stopAndRelease()

    /**
     * 播放中调整音量（0~1，线性）。没在播、或本平台不支持音量控制时是**空操作**。
     *
     * 为什么按"线性 0~1"而不是直接暴露平台控件：界面上的滑条就是这个口径，
     * 桌面那侧的 MASTER_GAIN 是**分贝**、还要处理"-Infinity 会被拒"的边界（见 DesktopAudioPlayer）。
     */
    fun setVolume(volume: Float) {}

    /**
     * 上一次 [start] 失败的原因（null = 没失败过）。
     *
     * 为什么要有它：起播失败原本是**完全静默**的——界面上既没声音也没任何提示，
     * 用户和排查者都拿不到线索（2026-09-17 用户反馈"配好供应商后朗读不发声"就栽在这里）。
     * 调用方读它转成一行面向用户的提示。
     */
    val lastError: String? get() = null
}

/** 录音器（语音输入第一段：录出音频文件再交给供应商识别） */
interface VoiceRecorder {
    /** 开始录音到应用私有目录；失败抛异常（麦克风被占用/无权限） */
    fun start(): File

    /** 停止并返回录音文件；说话时间太短返回 null */
    fun stop(): File?

    /** 放弃本次录音 */
    fun cancel()

    /**
     * [stop] 返回 null 时用它区分两种情况：**"说得太短"** 与 **"录音设备一个字节都没给"**。
     *
     * 后者在桌面上真的出现过：Windows 的默认录音设备被设成了"本机扬声器"（不是采集端点），
     * 线路能打开、却读不出任何数据——这时说"说话时间太短"会把用户引向完全错误的方向。
     * 默认 false：Android 的 MediaRecorder 不会出现"开着却没数据"这种状态。
     */
    val lastStopHadNoAudio: Boolean get() = false
}

/** "录音设备没给数据"的统一提示（两个平台共用一句，别在界面各处抄第二份） */
const val NO_AUDIO_INPUT_NOTICE = "没收到麦克风声音，请检查系统的默认录音设备（声音设置 → 输入）"

/** 系统语音识别（无需配钥匙的兜底识别；错误码沿用 Android SpeechRecognizer 口径） */
interface SpeechRecognizerPlatform {
    fun isAvailable(): Boolean

    /**
     * 开始识别。[languageTag] 为空串用系统默认。onResult 识别文本（可能空串）、
     * onError 错误码——各回调一次，主线程；实现自行保证主线程安全（Android 要求创建与启动都在主线程）。
     */
    fun listen(languageTag: String, onResult: (String) -> Unit, onError: (Int) -> Unit)

    /** 让识别服务停止收音（"我说完了"）；结果/错误回调照常送达 */
    fun stopListening()

    /** 丢弃本次识别 */
    fun cancel()

    /** 页面销毁后释放 */
    fun destroy()
}

/** 语音能力注入点：宿主入口装配（App.kt / desktopApp main） */
object Voice {
    /** 每次调用返回一个新引擎实例（聊天页与设置页各持一个，互不干扰）；返回 null = 本平台不支持 */
    var ttsEngineFactory: () -> TtsEngine? = { null }

    var audioPlayerFactory: () -> AudioPlayerEngine = { object : AudioPlayerEngine {
        override fun start(file: File, loop: Boolean, volume: Float, onFinished: () -> Unit): Boolean {
            onFinished(); return false
        }
        override fun stopAndRelease() {}
    } }

    /**
     * 本平台播放器**认哪些扩展名**（小写、不含点）——用户上传背景音乐前的第一道闸门。
     *
     * 为什么必须显式声明而不是"传上去试试"：用户塞一个本平台放不了的格式进来，结果是
     * "开关开着、音源也选着，却一点声音都没有"，而且**没有任何报错**——这正是本项目反复
     * 踩过的那类坑（起播失败静默）。宁可当场说"这个格式放不了，请换 mp3 / m4a"。
     *
     * ⚠ **声明出去就必须真能放**（用户要求支持 m4a 之后这条更要紧：多写一个扩展名     * 就是把上面那种静默故障重新引进来）。两端的取值分别是：
     * - Android（`App.onCreate`）：MediaPlayer 支持面宽——mp3/wav/ogg/m4a/aac/flac/opus/amr 等；
     * - 桌面（`installDesktopVoice`）：Java Sound 原生认 wav，mp3 靠 mp3spi，
     *   m4a/aac、flac、ogg 靠加的 `com.tianscar.javasound` 三个 SPI。
     *   每个取值都在桌面 `--smoke` 里被真解码一遍（自带 19KB 小样本）。
     */
    var supportedAudioExtensions: Set<String> = setOf("mp3", "wav")

    var recorderFactory: () -> VoiceRecorder = { object : VoiceRecorder {
        override fun start(): File = throw java.io.IOException("此平台暂不支持录音")
        override fun stop(): File? = null
        override fun cancel() {}
    } }

    var recognizer: SpeechRecognizerPlatform = object : SpeechRecognizerPlatform {
        override fun isAvailable(): Boolean = false
        override fun listen(languageTag: String, onResult: (String) -> Unit, onError: (Int) -> Unit) {}
        override fun stopListening() {}
        override fun cancel() {}
        override fun destroy() {}
    }
}

/**
 * Android SpeechRecognizer 错误码（共享层引用，避免平台类型进 UI；1–9 取值与 android.speech 一致）。
 * **100 起是平台扩展码**，Android 侧不会产生。
 */
object SpeechRecognizerErrors {
    const val ERROR_NETWORK_TIMEOUT = 1
    const val ERROR_NETWORK = 2
    const val ERROR_AUDIO = 3
    const val ERROR_SERVER = 4
    const val ERROR_CLIENT = 5
    const val ERROR_INSUFFICIENT_PERMISSIONS = 6
    const val ERROR_RECOGNIZER_BUSY = 7
    const val ERROR_NO_MATCH = 8
    const val ERROR_SPEECH_TIMEOUT = 9

    /**
     * 桌面扩展：**录音设备一个字节都没给**（线路开着读不出数据）。
     * 实测成因是 Windows 的默认录音设备被设成了"本机扬声器"之类的非采集端点。
     */
    const val ERROR_NO_AUDIO_INPUT = 100
}
