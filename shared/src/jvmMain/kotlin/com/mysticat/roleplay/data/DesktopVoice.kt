package com.mysticat.roleplay.data

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import javax.sound.sampled.TargetDataLine
import javax.swing.SwingUtilities
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 桌面端的语音四件套（Android 语音链路的平台替身）：
 * 系统朗读（[DesktopTtsEngine]）、音频播放（[DesktopAudioPlayer]）、录音（[DesktopVoiceRecorder]）、
 * 系统识别（[DesktopSpeechRecognizer]）。宿主入口 `Main.kt` 调 [installDesktopVoice] 一次装配。
 *
 * **三处"桌面没有对应物"的替代方案**（都写在这一个文件里，便于对照 Android 的 AndroidVoice.kt）：
 * ① 系统朗读：JDK 无内置 TTS → 走 Windows PowerShell 5.1 + .NET `System.Speech`，**一句一个进程**
 *    （同步 Speak + 进程边界天然就是队列边界，"停止朗读"就是杀进程）；
 * ② 音频播放：Android 是 MediaPlayer → 桌面 `javax.sound.sampled.Clip`；供应商合成的音频是 MP3，
 *    靠 mp3spi（依赖已在 shared/build.gradle.kts 的 jvmMain 里声明）让 Java Sound 认识它；
 * ③ 录音：Android 是 MediaRecorder(m4a) → 桌面 `TargetDataLine` 采 16k/16bit/单声道 PCM，
 *    自己拼 WAV 头落盘（三家 ASR 都吃 wav；共享层上传时按扩展名给 MIME）。
 *
 * 线程约定沿用共享层 [TtsSpeaker] 的说明：**回调一律切回 UI 线程**（桌面＝Swing EDT）。
 */

// ─────────────────────────── 系统能力探测（音色 / 识别器） ───────────────────────────

/**
 * Windows 语音能力的探测结果：装了什么朗读音色、有没有系统识别器。
 *
 * 为什么要单独做：`TtsSpeaker.unavailableReason` 会被 UI **同步读**（聊天页、设置页试听），
 * 而探一次要起一次 PowerShell（几百毫秒）——所以探测在后台跑一次并缓存，读接口永远立即返回；
 * 探测完成前 [unavailableReason] 保持 null（"还不知道"），真出声失败时上层照样有 ERROR 提示路径。
 */
object DesktopSpeechCapability {

    data class VoiceInfo(val culture: String, val name: String)

    private val started = AtomicBoolean(false)
    private val done = CountDownLatch(1)

    @Volatile
    private var voices: List<VoiceInfo> = emptyList()

    @Volatile
    private var recognizers: List<String> = emptyList()

    /** PowerShell 本身能跑起来且 System.Speech 可用 */
    @Volatile
    private var speechAvailable = false

    @Volatile
    private var failure: String? = null

    private var toolsDir: File? = null

    /** 宿主启动时调一次（非阻塞）：把脚本落到磁盘并在后台探一次 */
    fun init(toolsDir: File) {
        this.toolsDir = toolsDir
        if (started.compareAndSet(false, true)) {
            Thread({ runProbe() }, "whale-speech-probe").apply { isDaemon = true }.start()
        } else {
            done.await(1, TimeUnit.MILLISECONDS)
        }
    }

    /** 探完没有（没探完就等 [timeoutMs]）；返回 PowerShell + System.Speech 是否整体可用 */
    fun await(timeoutMs: Long): Boolean {
        done.await(timeoutMs, TimeUnit.MILLISECONDS)
        return speechAvailable
    }

    fun voices(): List<VoiceInfo> = voices

    /** 有没有 culture 前缀匹配的系统识别器（如 "zh" 匹配 zh-CN） */
    fun hasRecognizer(culturePrefix: String): Boolean {
        if (!speechAvailable) return false
        if (culturePrefix.isBlank()) return recognizers.isNotEmpty()
        val p = culturePrefix.substringBefore('-').lowercase(Locale.ROOT)
        return recognizers.any { it.lowercase(Locale.ROOT).startsWith(p) }
    }

    fun failureReason(): String? = failure

    private fun runProbe() {
        try {
            val dir = toolsDir ?: return
            val script = DesktopPowerShell.extractScript("whale-speech-probe.ps1", dir)
            if (script == null) {
                failure = "缺少语音探测脚本（安装包不完整）"
                return
            }
            val collected = ArrayList<String>()
            val errs = ArrayList<String>()
            val code = DesktopPowerShell.run(
                script = script,
                args = emptyList(),
                timeoutMs = 30_000,
                onStdout = { line -> synchronized(collected) { collected.add(line) } },
                onStderr = { line -> synchronized(errs) { errs.add(line) } }
            )
            if (code != 0) {
                // stderr 第一行通常是 PowerShell 的解析/加载错误，直接带给用户，别只说"退出码"
                val detail = synchronized(errs) { errs.firstOrNull { it.isNotBlank() } }
                failure = if (detail != null) {
                    "系统语音组件不可用（$detail）"
                } else {
                    "系统语音组件不可用（PowerShell 退出码 $code）"
                }
                return
            }
            val parsedVoices = ArrayList<VoiceInfo>()
            val parsedRecog = ArrayList<String>()
            synchronized(collected) { collected.toList() }.forEach { line ->
                val parts = line.split("|")
                when {
                    parts.size >= 3 && parts[0] == "SPEAK" -> parsedVoices.add(VoiceInfo(parts[1], parts[2]))
                    parts.size >= 3 && parts[0] == "RECOG" -> parsedRecog.add(parts[1])
                    parts.firstOrNull() == "ERRSPEAK" -> failure = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        ?: "系统朗读不可用"
                    parts.firstOrNull() == "ERRRECOG" ->
                        WhaleLog.w(TAG, "系统识别不可用：${parts.getOrNull(1)}")
                }
            }
            voices = parsedVoices
            recognizers = parsedRecog
            speechAvailable = parsedVoices.isNotEmpty()
            if (!speechAvailable && failure == null) {
                failure = "系统没有可用的朗读音色，请到「设置 → 时间和语言 → 语音」里添加"
            }
        } catch (t: Throwable) {
            failure = "系统语音探测失败：${t.message}"
            WhaleLog.w(TAG, "语音探测失败：${t.message}")
        } finally {
            done.countDown()
        }
    }

    private const val TAG = "WhaleSpeech"
}

/** 挑一条中文音色：优先 zh-CN，其次任何 zh；没有就返回 null（由调用方给出"去装语音包"的提示） */
internal fun List<DesktopSpeechCapability.VoiceInfo>.pickChinese(): DesktopSpeechCapability.VoiceInfo? =
    firstOrNull { it.culture.equals("zh-CN", ignoreCase = true) }
        ?: firstOrNull { it.culture.startsWith("zh", ignoreCase = true) }

// ─────────────────────────── PowerShell 脚本宿主 ───────────────────────────

/**
 * 跑 PowerShell 脚本的小工具。
 *
 * 两个必须这么做的细节：
 * ① 脚本以**资源**形式打进 jar，运行时复制到数据目录再 `-File` 执行（`-File` 只认文件系统路径）；
 * ② 必须显式用 `powershell.exe`（Windows PowerShell 5.1）——`System.Speech` 在 .NET Framework 里，
 *    PowerShell 7（pwsh）默认加载不到。
 */
internal object DesktopPowerShell {

    /**
     * 把资源里的脚本取到 [toolsDir]，内容有变化就覆盖（升级后脚本能跟着更新）；失败返回 null。
     *
     * ⚠ **必须带 UTF-8 BOM**：Windows PowerShell 5.1 对没有 BOM 的 `.ps1` 按系统 ANSI 代码页
     * （中文机器是 GBK）解码，脚本里的中文注释会变成乱码并**直接把脚本解析崩掉**
     * （实测报 `MissingEndParenthesisInMethodCall`）。加上 BOM 后 5.1 才按 UTF-8 读。
     * 这条与本机踩坑文档里"PowerShell 5.1 的 UTF-8 BOM"是同一个坑。
     */
    fun extractScript(resourceName: String, toolsDir: File): File? = runCatching {
        val target = File(toolsDir, resourceName)
        val raw = DesktopPowerShell::class.java.getResourceAsStream("/$resourceName")?.use { it.readBytes() }
            ?: return null
        val bytes = if (raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()) {
            raw
        } else {
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + raw
        }
        if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        target
    }.getOrNull()

    /**
     * 同步跑完一个脚本：写 [stdinText]（非空时以 UTF-8 写入并**关闭 stdin**——ASR 用它当停止信号），
     * 逐行回调 stdout（UTF-8）。返回退出码；超时或被中断返回 -1。
     */
    fun run(
        script: File,
        args: List<String>,
        timeoutMs: Long,
        stdinText: String? = null,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {}
    ): Int {
        val cmd = mutableListOf(
            "powershell.exe", "-NoProfile", "-NonInteractive",
            "-ExecutionPolicy", "Bypass", "-File", script.absolutePath
        )
        cmd.addAll(args)
        val process = ProcessBuilder(cmd).redirectErrorStream(false).start()
        try {
            if (stdinText != null) {
                process.outputStream.use { os ->
                    os.write(stdinText.toByteArray(StandardCharsets.UTF_8))
                    os.flush()
                }
            } else {
                // 不需要输入的脚本也要把 stdin 关掉，免得子进程等它
                runCatching { process.outputStream.close() }
            }
            // stdout/stderr 各自读干（放在本线程读 stdout，另开线程读 stderr，避免管道写满互锁）
            val errLines = ArrayList<String>()
            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { line ->
                        synchronized(errLines) { errLines.add(line) }
                    }
                }
            }.apply { isDaemon = true; start() }
            runCatching {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { onStdout(it) }
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                return -1
            }
            errThread.join(500)
            synchronized(errLines) { errLines }.forEach(onStderr)
            return process.exitValue()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

// ─────────────────────────── 系统朗读（TtsEngine） ───────────────────────────

/**
 * 桌面系统朗读：PowerShell + `System.Speech.Synthesis`，**一句一个进程**。
 *
 * 队列语义与 Android 的 `TextToSpeech.QUEUE_ADD` 对齐：这里自己排一个队列，一个工作线程顺序
 * 取任务起进程，读得慢也不会"两句叠着读"。停止 = 清队列 + 杀掉正在出声的那个进程。
 */
class DesktopTtsEngine(private val toolsDir: File) : TtsEngine {

    private data class Job(val sentence: String, val onStarted: () -> Unit, val done: (Boolean) -> Unit)

    private val queue = LinkedBlockingQueue<Job>()

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var current: Process? = null

    @Volatile
    private var closed = false

    @Volatile
    private var readyFlag = false

    @Volatile
    private var unavailable: String? = null

    @Volatile
    private var zhVoice: String? = null

    override var onReady: (() -> Unit)? = null

    private var rateSteps = 0
    private var pitchSemitones = 0

    init {
        DesktopSpeechCapability.init(toolsDir)
        Thread({
            DesktopSpeechCapability.await(30_000)
            val voice = DesktopSpeechCapability.voices().pickChinese()
            if (voice == null) {
                unavailable = DesktopSpeechCapability.failureReason()
                    ?: "系统没有可用的中文语音包，请到「设置 → 时间和语言 → 语音」里安装"
            } else {
                zhVoice = voice.name
                readyFlag = true
                ui { onReady?.invoke() }
            }
        }, "whale-tts-ready").apply { isDaemon = true }.start()
    }

    override val unavailableReason: String?
        get() = if (readyFlag) null else unavailable

    override val ready: Boolean
        get() = readyFlag

    override fun applyParams(speed: Float, pitch: Float) {
        // System.Speech 的 Rate 是 -10..10 的档位（每档约 1.25 倍），音高没有属性、只能走 SSML 半音
        rateSteps = (ln(speed.coerceIn(0.1f, 4f).toDouble()) / ln(SPEED_STEP))
            .roundToInt().coerceIn(-10, 10)
        pitchSemitones = (12.0 * ln(pitch.coerceIn(0.25f, 4f).toDouble()) / ln(2.0))
            .roundToInt().coerceIn(-12, 12)
    }

    override fun speak(sentence: String, onStarted: () -> Unit, done: (Boolean) -> Unit): Boolean {
        if (closed || !readyFlag) return false
        if (sentence.isBlank()) return false
        val job = Job(sentence, onStarted, done)
        queue.offer(job)
        ensureWorker()
        return true
    }

    private fun ensureWorker() {
        if (worker?.isAlive == true) return
        synchronized(this) {
            if (worker?.isAlive == true) return
            worker = Thread({ drain() }, "whale-tts").apply { isDaemon = true; start() }
        }
    }

    private fun drain() {
        while (!closed) {
            val job = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (closed) return
            ui { job.onStarted() }
            val ok = speakOnce(job.sentence)
            if (closed) return
            ui { job.done(ok) }
        }
    }

    private fun speakOnce(sentence: String): Boolean = runCatching {
        val script = DesktopPowerShell.extractScript("whale-tts.ps1", toolsDir) ?: return false
        val cmd = listOf(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
            "-Rate", rateSteps.toString(),
            "-PitchSt", pitchSemitones.toString(),
            "-Culture", "zh-CN"
        )
        val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
        current = process
        // 文本走 stdin（UTF-8）：正文里什么标点都有，走命令行参数会被引号与编码坑死
        process.outputStream.use { os ->
            os.write(sentence.toByteArray(StandardCharsets.UTF_8))
            os.flush()
        }
        // 拿干输出，避免子进程写满管道卡住；日志只留前 200 字（可能是异常信息）
        val output = StringBuilder()
        runCatching {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).forEachLine { line ->
                if (output.length < 400) output.append(line).append(' ')
            }
        }
        val finished = process.waitFor(TTS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            WhaleLog.w(TAG, "朗读超时（${TTS_TIMEOUT_SECONDS}s），已中止该句")
        }
        val code = if (finished) process.exitValue() else -1
        if (code != 0) WhaleLog.w(TAG, "朗读失败（退出码 $code）：${output.take(200)}")
        code == 0
    }.getOrElse {
        WhaleLog.w(TAG, "朗读异常：${it.message}")
        false
    }

    override fun stopEngine() {
        queue.clear()
        runCatching { current?.destroyForcibly() }
        current = null
    }

    override fun shutdown() {
        closed = true
        stopEngine()
        worker?.interrupt()
        worker = null
    }

    private companion object {
        const val TAG = "WhaleTts"
        const val SPEED_STEP = 1.25
        const val TTS_TIMEOUT_SECONDS = 120L
    }
}

// ─────────────────────────── 音频播放（供应商合成路径） ───────────────────────────

/**
 * 桌面音频播放：`javax.sound.sampled.Clip`（整段载入内存后播——朗读片段都是秒级，代价可忽略）。
 * 供应商合成返回的是 MP3，Java Sound 原生不认，靠 mp3spi 注册的 SPI 读进来（依赖见 jvmMain）。
 *
 * ## ⚠ 2026-09-17：#6「配好供应商后朗读不发声」的真根因就在这里
 *
 * 旧实现是 `AudioSystem.getAudioInputStream(file)` 拿到流就 `isLineSupported(Clip, 流的格式)`——
 * 但 **mp3spi 这条路给的是"压缩格式"流**（实测格式串 `MPEG2L3 24000Hz, mono`），
 * 压缩格式永远开不出播放线（`isLineSupported` 恒为 false），于是每次都走进那个 `return false`；
 * 而它偏偏写在 `runCatching` 的 lambda 里，**连一行日志都没有** → 界面上彻底静默。
 * 用用户机器上真实缓存的 9 个供应商 MP3 实测：**0/9 可播**；加上"显式要 PCM 转换"这一步后 **9/9 可播**。
 *
 * mp3spi 的标称用法就是两步（见其官方 example）：先拿压缩流，再向 `AudioSystem` 要一个
 * **PCM 目标格式**的转换流。目标格式取**源声道数**（不强制降混成单声道——实测记录：
 * 让 mp3spi 顺手做声道转换会踩 `DecodedMpegAudioInputStream$DMAISObuffer.append` 的越界）。
 *
 * 已经的 PCM 流（系统朗读路径产出的 WAV）走原路直接开线，不再多绕一次转换。
 */
class DesktopAudioPlayer : AudioPlayerEngine {

    @Volatile
    private var clip: javax.sound.sampled.Clip? = null

    @Volatile
    private var closed = false

    /** 起播失败的原因（界面据此提示，见 [AudioPlayerEngine.lastError]） */
    @Volatile
    override var lastError: String? = null
        private set

    override fun start(file: File, loop: Boolean, volume: Float, onFinished: () -> Unit): Boolean {
        try {
            val src = AudioSystem.getAudioInputStream(file)
            val decoded = toPcmIfNeeded(file, src) ?: return false
            val format = decoded.format
            val info = DataLine.Info(javax.sound.sampled.Clip::class.java, format)
            if (!AudioSystem.isLineSupported(info)) {
                decoded.close()
                return fail(file, "本机音频设备不支持这种声音格式（$format）")
            }
            val c = AudioSystem.getLine(info) as javax.sound.sampled.Clip
            c.open(decoded)
            // open() 已经把整段读进内存，之后的播放不再依赖这个流；关掉它才算把文件句柄还回去。
            // 关的是**转换流**（它自己持有源流）——别去关源流，理由见 toPcmIfNeeded 的注释。
            runCatching { decoded.close() }
            clip = c
            // 音量：必须在 open() 之后（控件随数据线一起建立）
            runCatching { applyGain(c, volume) }
            // ⚠ 循环播放用 `loop()` 而不是"STOP 事件里再 start()"：后者在两次播放之间有一小段
            // 静音，背景音乐是**连续环境音**，那点断点听得很清楚。`loop(LOOP_CONTINUOUSLY)` 由
            // 数据线自己接续，没有断点；代价是**不会产生 STOP 事件**，所以下面那个
            // onFinished 在 loop 模式下永远不会触发（BGM 也不需要它）。
            if (loop) {
                c.loop(javax.sound.sampled.Clip.LOOP_CONTINUOUSLY)
            } else {
                c.addLineListener { event ->
                    if (event.type == LineEvent.Type.STOP && !closed) {
                        runCatching { c.close() }
                        if (clip === c) clip = null
                        ui { onFinished() }
                    }
                }
                c.start()
            }
            lastError = null
            return true
        } catch (t: Throwable) {
            return fail(file, "${t.javaClass.simpleName}：${t.message ?: "无详细信息"}")
        }
    }

    /**
     * 播放中调音量。桌面有**两套**控件可用，按优先级试：
     * ① `MASTER_GAIN`（分贝，Windows 的 DirectAudioDevice 都支持）—— 注意它是 dB，
     *    线性 0 对应 -Infinity，直接写会被 `IllegalArgumentException` 拒（或被静默夹到最小值），
     *    所以 0 特判成控件的最小值；② 退回 `VOLUME`（线性 0~1，支持它的线较少）。
     * 两个都没有就不动（不报错：音量控件缺失不是故障，声音照常放）。
     */
    override fun setVolume(volume: Float) {
        val c = clip ?: return
        runCatching { applyGain(c, volume) }
    }

    private fun applyGain(c: javax.sound.sampled.Clip, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        val gain = runCatching {
            c.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
        }.getOrNull()
        if (gain != null) {
            val db = if (v <= 0.0001f) gain.minimum
            else (20.0 * kotlin.math.log10(v.toDouble())).toFloat().coerceIn(gain.minimum, gain.maximum)
            if (gain.value != db) gain.value = db
            return
        }
        val lin = runCatching { c.getControl(FloatControl.Type.VOLUME) as? FloatControl }.getOrNull() ?: return
        val target = v.coerceIn(lin.minimum, lin.maximum)
        if (lin.value != target) lin.value = target
    }

    /**
     * 拿到一个**PCM**流：源本身是 PCM（WAV）就原样返回；是压缩格式（MP3/M4A/FLAC/OGG）就显式转成 PCM。
     * 转换失败返回 null（原因已打日志，调用方直接按失败处理）。
     *
     * ⚠️ **绝对不要在这里 close(src)**（第 28 轮用 jstack 抓到的真 bug）：
     * `AudioSystem.getAudioInputStream(target, src)` 返回的转换流是**惰性**读 src 的，
     * 提前关源流之后，mp3spi 的解码循环会把"读到 IOException(Stream closed)"当成
     * "这一拍还没数据"，于是**在调用线程上无限空转**——而调用线程就是 AWT EDT。
     * 实测：点一次朗读，EDT 卡死、空烧 56 秒 CPU 且永不返回，界面完全点不动
     *（用户两次报的"哪里都点不了了 / 点语音播放就崩了"都是这一条）。
     * 源流的生命周期交给转换流持有：随转换流一起关。
     */
    private fun toPcmIfNeeded(file: File, src: AudioInputStream): AudioInputStream? {
        val fmt = src.format
        val enc = fmt.encoding
        if (enc == AudioFormat.Encoding.PCM_SIGNED || enc == AudioFormat.Encoding.PCM_UNSIGNED) return src
        // 单声道源就按单声道解（见类注释：不要让 mp3spi 顺手做声道转换）
        val channels = if (fmt.channels > 0) fmt.channels else 1
        val rate = if (fmt.sampleRate > 0f) fmt.sampleRate else 24000f
        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            rate,
            16,
            channels,
            channels * 2,
            rate,
            false
        )
        try {
            return AudioSystem.getAudioInputStream(target, src)
        } catch (t: Throwable) {
            // 有些 SPI 只提供**大端**目标，小端会被直接拒（第 60 轮实测：m4a/aac 的 jaad SPI 就是这样——
            // "Unsupported conversion: ...little-endian from AAC ..."）。这时退回"让 SPI 自己挑格式"
            // 的那个重载：它给什么就收什么。大端 PCM 在 Windows 的数据线上照样能起播
            //（ClipProbe 实测：isLineSupported=true、Clip.open 正常）。
            // 为什么不一上来就用它：那个重载会让 mp3spi 顺手把单声道升混成双声道，
            // 朗读那条路的声音会平白多一份内存与处理（下面 channels 那一行的由来）。
            // 失败的那一次不会吃掉源流的数据（RetryProbe 实测：同一流重试与重开文件逐字节相同），
            // 所以可以直接在同一个 src 上重试。
            try {
                return AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, src)
            } catch (t2: Throwable) {
                runCatching { src.close() }
                WhaleLog.w(
                    TAG,
                    "解码失败（${file.name}）源格式 $fmt → ${t2.javaClass.simpleName}：${t2.message}"
                )
                lastError = "音频解码失败（${t2.javaClass.simpleName}）"
                return null
            }
        }
    }

    /** 统一的失败出口：**一定要留下痕迹**（旧实现就是在这里一声不吭） */
    private fun fail(file: File, reason: String): Boolean {
        lastError = reason
        WhaleLog.w(TAG, "播放失败（${file.name}）：$reason")
        return false
    }

    override fun stopAndRelease() {
        closed = true
        runCatching { clip?.stop() }
        runCatching { clip?.close() }
        clip = null
        closed = false
    }

    private companion object {
        const val TAG = "WhaleAudio"
    }
}

// ─────────────────────────── 录音（ASR 第一段） ───────────────────────────

/**
 * 桌面录音：`TargetDataLine` 采 **16kHz / 16bit / 单声道 PCM**，停止时自己拼 WAV 头落盘。
 *
 * 参数与 Android 侧对齐（那边是 AAC@16k 单声道）：三家供应商识别都收 wav，且 16k 单声道是它们
 * 的推荐输入。不直接调 `AudioSystem.write(..., WAVE, file)` 是因为数据线给出的流长度未知
 * （WAV 头里的长度字段要先知道总字节数），自己拼头更可控。
 */
class DesktopVoiceRecorder(private val cacheRoot: File) : VoiceRecorder {

    private var line: TargetDataLine? = null
    private var pump: Thread? = null
    private var buffer: java.io.ByteArrayOutputStream? = null
    private var target: File? = null
    private var startedAt = 0L
    /** 实际采集格式：设备不支持 16k 时回退 44.1/48k（WAV 头按它写，供应商侧认普通采样率的 wav） */
    private var activeFormat: AudioFormat = FORMAT

    /** [VoiceRecorder.lastStopHadNoAudio]：设备一个字节都没给（见接口注释） */
    @Volatile
    override var lastStopHadNoAudio = false

    override fun start(): File {
        cancel()
        // 三步探测把"没有设备 / 不支持16k / 被占用"拆开（混成一句曾误报：
        // 本机 mic 与 16k 采集都正常，真报错其实是瞬时占用——"line supported"≠"此刻可用"，
        // 占用要到 getLine/open 才炸 LineUnavailableException）
        if (!AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, null))) {
            throw IOException("这台电脑没有可用的录音设备（或麦克风被禁用）")
        }
        val fmt: AudioFormat = if (AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, FORMAT))) {
            FORMAT
        } else {
            val rate = FALLBACK_RATES.firstOrNull {
                AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, AudioFormat(it, 16, 1, true, false)))
            } ?: throw IOException("录音设备不支持可用的采样格式（16k/44.1k/48kHz）")
            WhaleLog.i("WhaleRec", "设备不支持 16k，回退 ${rate / 1000}kHz 采集")
            AudioFormat(rate, 16, 1, true, false)
        }
        val l = try {
            AudioSystem.getLine(DataLine.Info(TargetDataLine::class.java, fmt)) as TargetDataLine
        } catch (e: Exception) {
            throw IOException("麦克风被其它应用占用，请关闭占用它的程序后重试", e)
        }
        try {
            l.open(fmt)
        } catch (e: Exception) {
            runCatching { l.close() }
            throw IOException("麦克风被其它应用占用，请关闭占用它的程序后重试", e)
        }
        activeFormat = fmt
        l.start()
        val buf = java.io.ByteArrayOutputStream()
        val file = File(File(cacheRoot, "voice-input").apply { mkdirs() }, "rec-${System.currentTimeMillis()}.wav")
        line = l
        buffer = buf
        target = file
        startedAt = System.currentTimeMillis()
        pump = Thread({
            val chunk = ByteArray(4096)
            var failures = 0
            while (l.isOpen) {
                val n = runCatching { l.read(chunk, 0, chunk.size) }.getOrElse {
                    if (failures++ == 0) WhaleLog.w("WhaleRec", "录音读取失败：${it.message}")
                    -1
                }
                // n == 0 只是"这一拍还没数据"（蓝牙耳机没就绪时很常见），**不能当成录完**：
                // 早期写法在这里 break，实际表现是"按住好几秒却一个字节都没录到"（第 24 轮实测）
                if (n < 0) break
                if (n == 0) {
                    Thread.sleep(10)
                    continue
                }
                synchronized(buf) { buf.write(chunk, 0, n) }
            }
        }, "whale-recorder").apply { isDaemon = true; start() }
        // 此时文件还没落盘（长度要等停录才知道），返回的是即将写入的落点
        return file
    }

    override fun stop(): File? {
        val l = line ?: return null
        val tooShort = System.currentTimeMillis() - startedAt < MIN_RECORD_MS
        val file = target
        line = null
        target = null
        runCatching { l.stop() }
        runCatching { l.close() }
        runCatching { pump?.join(1500) }
        pump = null
        val pcm = synchronized(buffer ?: java.io.ByteArrayOutputStream()) {
            (buffer ?: java.io.ByteArrayOutputStream()).toByteArray()
        }
        buffer = null
        WhaleLog.i("WhaleRec", "录音结束：${pcm.size / 1024}KB / ${System.currentTimeMillis() - startedAt}ms")
        // 区分"按得太短"与"设备一个字节都没给"（后者＝默认录音设备选错了，提示语完全不同）
        lastStopHadNoAudio = pcm.isEmpty() && !tooShort
        if (tooShort || pcm.isEmpty() || file == null) return null
        return runCatching {
            file.parentFile?.mkdirs()
            writeWav(file, pcm)
            file.takeIf { it.isFile && it.length() > 0 }
        }.getOrNull()
    }

    override fun cancel() {
        val l = line
        line = null
        runCatching { l?.stop() }
        runCatching { l?.close() }
        runCatching { pump?.join(500) }
        pump = null
        buffer = null
        runCatching { target?.delete() }
        target = null
    }

    /** 拼 44 字节标准 WAV 头（PCM）再跟数据 */
    private fun writeWav(file: File, pcm: ByteArray) {
        val channels = 1
        val bits = 16
        val sampleRate = activeFormat.sampleRate.toInt()
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        file.outputStream().buffered().use { os ->
            fun le32(v: Int) = byteArrayOf(
                (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
                ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte()
            )
            fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
            os.write("RIFF".toByteArray(StandardCharsets.US_ASCII))
            os.write(le32(36 + pcm.size))
            os.write("WAVE".toByteArray(StandardCharsets.US_ASCII))
            os.write("fmt ".toByteArray(StandardCharsets.US_ASCII))
            os.write(le32(16))
            os.write(le16(1))
            os.write(le16(channels))
            os.write(le32(sampleRate))
            os.write(le32(byteRate))
            os.write(le16(blockAlign))
            os.write(le16(bits))
            os.write("data".toByteArray(StandardCharsets.US_ASCII))
            os.write(le32(pcm.size))
            os.write(pcm)
        }
    }

    private companion object {
        val FORMAT = AudioFormat(16_000f, 16, 1, true, false)
        /** 设备不支持 16k 时按此顺序找替代采样率（仍是 16bit 单声道） */
        val FALLBACK_RATES = floatArrayOf(44_100f, 48_000f, 22_050f)
        const val MIN_RECORD_MS = 600L
    }
}

// ─────────────────────────── 系统识别（无需 Key 的兜底） ───────────────────────────

/**
 * 桌面系统识别：Windows 自带识别器（PowerShell + `System.Speech.Recognition` 的听写语法）。
 *
 * **实现方式＝按住期间自己录 WAV，松手后离线识别这一段文件**（第 24 轮改）。
 * 早期做法是"起一个 PowerShell 常驻进程边听边识别，父进程关 stdin 当作停止信号"，
 * 实测**在应用里不成立**：这个子进程的 stdin 一开始就是 EOF，脚本启动约 1.8 秒后自己走到
 * `RecognizeAsyncStop` + 等 1.2 秒，然后以"没结果"退出；应用收到 ERROR_NO_MATCH，
 * 用户看到的就是**"按住还没说话、三秒就自动结束"**（第 24 轮实测 2.96 秒，与用户反馈一致）。
 * 改成离线识别后不再有"停止信号"这回事（进程起来 → 认一段文件 → 退出），既确定，
 * 也与 Android 侧同一口径（那边同样是"录一段再交给识别引擎"），还省掉一个常驻进程。
 *
 * 与 Android 的 SpeechRecognizer 的差异（会写进对外日志的已知差异）：
 * ① **必须在系统里装过对应语言的"语音识别"语言包**（Windows 设置 → 时间和语言 → 语音）；
 *    没装时 [isAvailable] 为 false，UI 会引导用户改用供应商识别——不会静默失败；
 * ② 识别文本按"整句"回来（不做部分结果），与 Android 侧同一口径（那边也关掉了 partial）。
 */
class DesktopSpeechRecognizer(
    private val toolsDir: File,
    private val cacheRoot: File
) : SpeechRecognizerPlatform {

    /** 按住期间用的录音器：与供应商识别那条路同一个实现（16k / 16bit / 单声道 PCM WAV） */
    private val recorder = DesktopVoiceRecorder(cacheRoot)

    /** 每次 listen/cancel 自增；回调前比对，丢掉过期结果（用户快速连按两次） */
    @Volatile
    private var generation = 0

    @Volatile
    private var resultCb: ((String) -> Unit)? = null

    @Volatile
    private var errorCb: ((Int) -> Unit)? = null

    @Volatile
    private var language = ""

    init {
        DesktopSpeechCapability.init(toolsDir)
    }

    override fun isAvailable(): Boolean {
        // 探测没跑完时乐观返回 true（首次点麦克风就用系统识别的场景），真起不来会走 onError 提示
        if (!DesktopSpeechCapability.await(0)) return true
        return DesktopSpeechCapability.hasRecognizer("")
    }

    override fun listen(languageTag: String, onResult: (String) -> Unit, onError: (Int) -> Unit) {
        cancel()
        resultCb = onResult
        errorCb = onError
        language = languageTag.trim()
        runCatching { recorder.start() }.onFailure {
            // 没有录音设备 / 被其它应用独占：给 ERROR_AUDIO，界面会说"录音出错"
            WhaleLog.w("WhaleAsr", "系统识别无法开始录音：${it.message}")
            generation++
            ui { onError(SpeechRecognizerErrors.ERROR_AUDIO) }
            return
        }
        WhaleLog.i("WhaleAsr", "开始录音（系统识别，语言 ${language.ifBlank { "系统默认" }}）")
    }

    /** "我说完了"：把刚录的那段交给离线识别（跑在后台线程，别卡住界面） */
    override fun stopListening() {
        val file = runCatching { recorder.stop() }.getOrNull()
        val gen = generation
        if (file == null) {
            val silent = recorder.lastStopHadNoAudio
            WhaleLog.w(
                "WhaleAsr",
                if (silent) "系统识别：一个字节都没录到——默认录音设备没给数据（Windows 声音设置 → 输入，别选成扬声器）"
                else "系统识别：没录到音频（按得太短）"
            )
            ui {
                errorCb?.invoke(
                    if (silent) SpeechRecognizerErrors.ERROR_NO_AUDIO_INPUT
                    else SpeechRecognizerErrors.ERROR_SPEECH_TIMEOUT
                )
            }
            return
        }
        val culture = language.ifBlank { "zh-CN" }
        WhaleLog.i("WhaleAsr", "松手：录音 ${file.length() / 1024}KB，开始离线识别")
        Thread({ recognize(file, culture, gen) }, "whale-asr").apply { isDaemon = true; start() }
    }

    private fun recognize(file: File, culture: String, gen: Int) {
        var text: String? = null
        var reason: String? = null
        val script = DesktopPowerShell.extractScript("whale-asr.ps1", toolsDir)
        val code = if (script == null) {
            reason = "脚本释放失败"
            -1
        } else {
            runCatching {
                DesktopPowerShell.run(
                    script,
                    listOf("-Wav", file.absolutePath, "-Culture", culture),
                    timeoutMs = 30_000,
                    onStdout = { line -> if (line.startsWith("R|")) text = line.substring(2).trim() },
                    onStderr = { line -> if (line.startsWith("E|")) reason = line.substring(2).trim() }
                )
            }.getOrElse {
                reason = it.message ?: "脚本执行失败"
                -1
            }
        }
        runCatching { file.delete() }
        if (gen != generation) {
            WhaleLog.i("WhaleAsr", "识别结果已过期（期间又按了一次），丢弃")
            return
        }
        val got = text
        if (!got.isNullOrBlank()) {
            WhaleLog.i("WhaleAsr", "离线识别成功：${got.length} 字")
            ui { resultCb?.invoke(got) }
        } else {
            WhaleLog.w("WhaleAsr", "离线识别没结果（退出码 $code：${reason ?: "无说明"}）")
            ui {
                errorCb?.invoke(
                    // 退出码 2 = 脚本明确说"整段没听出词"（没说话 / 声音太小 / 麦克风没声）
                    if (code == 2) SpeechRecognizerErrors.ERROR_NO_MATCH
                    else SpeechRecognizerErrors.ERROR_CLIENT
                )
            }
        }
    }

    override fun cancel() {
        generation++
        resultCb = null
        errorCb = null
        runCatching { recorder.cancel() }
    }

    override fun destroy() = cancel()
}

// ─────────────────────────── 装配 ───────────────────────────

/**
 * 把桌面四件套注入共享层（与 Android `App.onCreate` 里那段一一对应）。
 * [toolsDir] 用来放解出来的 PowerShell 脚本，[cacheRoot] 放录音临时文件。
 */
fun installDesktopVoice(toolsDir: File, cacheRoot: File) {
    Voice.ttsEngineFactory = { DesktopTtsEngine(toolsDir) }
    Voice.audioPlayerFactory = { DesktopAudioPlayer() }
    Voice.recorderFactory = { DesktopVoiceRecorder(cacheRoot) }
    Voice.recognizer = DesktopSpeechRecognizer(toolsDir, cacheRoot)
    // Java Sound 原生认 WAV/AIFF/AU，MP3 靠 mp3spi；第 60 轮又装了三个 SPI
    // （javasound-aac / -flac / -vorbis，见 shared/build.gradle.kts），于是 **m4a/aac、flac、ogg 也直接能读**。
    // 这份集合同时充当"用户上传的第一道闸门"（bgmFormatRejection 与文件框的类型过滤都用它）——
    // **声明出去就必须真能放**，所以只列这里验过的扩展名：多写一个只会把
    // "选了、开关也开着、却一声不响"这种最难查的故障重新引进来。
    Voice.supportedAudioExtensions =
        setOf("mp3", "wav", "m4a", "m4b", "aac", "flac", "ogg", "oga")
}

/** 把回调切到 UI 线程（桌面＝Swing EDT），与 Android 侧 Handler(Looper.getMainLooper()) 同一作用 */
private fun ui(block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
}
