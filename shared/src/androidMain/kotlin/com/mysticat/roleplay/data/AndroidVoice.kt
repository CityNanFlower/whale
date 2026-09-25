package com.mysticat.roleplay.data

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale

/**
 * 语音链路的 Android 实现：系统 TextToSpeech + MediaPlayer +
 * MediaRecorder + SpeechRecognizer，经 [Voice] 注入（App.kt 装配）。
 * 逻辑与拆分前逐行对应，只有"从类变成接口实现"这一层变化。
 */

/** 系统 TTS 引擎（原 TtsSpeaker 里的 TextToSpeech 部分，逐行迁移） */
class AndroidTtsEngine(context: Context) : TtsEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var readyFlag = false

    override var unavailableReason: String? = null
        private set
    override val ready: Boolean get() = readyFlag

    /** 引擎就绪时通知共享层把"初始化期间攒下的句子"吐出来（见 TtsEngine.onReady） */
    override var onReady: (() -> Unit)? = null

    /** 片段回调：utteranceId → (onStarted, done)；读完/出错各触发 done 一次 */
    private val pieceCallbacks = HashMap<String, Pair<() -> Unit, (Boolean) -> Unit>>()
    private var utteranceSeq = 0

    var speed: Float = 1.0f
    var pitch: Float = 1.0f

    init {
        tts = runCatching {
            TextToSpeech(appContext) { status ->
                readyFlag = status == TextToSpeech.SUCCESS
                if (!readyFlag) {
                    unavailableReason = "系统语音引擎初始化失败"
                    return@TextToSpeech
                }
                val t = tts ?: return@TextToSpeech
                // 中文优先；装不到中文时仍然提示（用英文音色念中文更难听，不如让用户去装）
                val res = runCatching { t.setLanguage(Locale.CHINESE) }
                    .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    unavailableReason = "系统没有可用的中文语音包，请到「系统设置 → 语言与输入 → 文字转语音」里安装"
                }
                runCatching {
                    t.setSpeechRate(speed)
                    t.setPitch(pitch)
                    t.setOnUtteranceProgressListener(listener())
                }
                readyFlag = true
                // 攒在共享层的首句这会儿才读得到（引擎初始化期间的 speak 会被丢掉）
                onReady?.invoke()
            }
        }.getOrNull()
        if (tts == null) unavailableReason = "本机不支持系统语音合成"
    }

    /** 系统 TTS 的回调在 binder 线程，统一 post 回主线程（调度器的线程约定见 TtsSpeaker） */
    private fun listener() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post {
                pieceCallbacks.remove(utteranceId)?.let { (onStarted, _) -> onStarted() }
            }
        }

        override fun onDone(utteranceId: String?) {
            main.post {
                pieceCallbacks.remove(utteranceId)?.let { (_, done) -> done(true) }
            }
        }

        @Deprecated("兼容旧 API")
        override fun onError(utteranceId: String?) {
            main.post {
                pieceCallbacks.remove(utteranceId)?.let { (_, done) -> done(false) }
            }
        }
    }

    override fun applyParams(speed: Float, pitch: Float) {
        this.speed = speed
        this.pitch = pitch
        val t = tts ?: return
        runCatching {
            t.setSpeechRate(speed)
            t.setPitch(pitch)
        }
    }

    override fun speak(sentence: String, onStarted: () -> Unit, done: (Boolean) -> Unit): Boolean {
        val t = tts ?: return false
        if (!readyFlag) return false
        return runCatching {
            val id = "whale-tts:${utteranceSeq++}:${sentence.length}"
            pieceCallbacks[id] = onStarted to done
            if (t.speak(sentence, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS) {
                true
            } else {
                pieceCallbacks.remove(id)
                false
            }
        }.getOrElse { pieceCallbacks.clear(); false }
    }

    override fun stopEngine() {
        runCatching { tts?.stop() }
        main.post { pieceCallbacks.clear() }
    }

    override fun shutdown() {
        runCatching { tts?.shutdown() }
        tts = null
        readyFlag = false
    }
}

/** MediaPlayer 播放器（原 TtsSpeaker.playAndWait 的 Android 半边） */
class AndroidAudioPlayer : AudioPlayerEngine {
    private var player: MediaPlayer? = null

    /**
     * 起播失败的原因（界面据此提示，见 [AudioPlayerEngine.lastError]）。
     *
     * 这条与桌面（`DesktopAudioPlayer`）对齐是**通用的**：共享层的 [TtsSpeaker] 在"起播即失败"时
     * 会回退系统 TTS 并把原因写进 `lastFallbackReason`，播放器不给原因的话，手机上用户看到的
     * 就是一句没法排查的"未给出原因"。
     */
    @Volatile
    override var lastError: String? = null
        private set

    override fun start(file: File, loop: Boolean, volume: Float, onFinished: () -> Unit): Boolean {
        val mp = MediaPlayer()
        player = mp
        return try {
            mp.setDataSource(file.absolutePath)
            // BGM 用循环：MediaPlayer 自己负责接续，不靠完成回调重启（重启会有可闻的断点）
            mp.isLooping = loop
            mp.setOnCompletionListener {
                runCatching { it.release() }
                if (player === it) player = null
                onFinished()
            }
            mp.setOnErrorListener { _, _, _ ->
                runCatching { mp.release() }
                if (player === mp) player = null
                onFinished()
                true
            }
            mp.prepare()
            runCatching { mp.setVolume(volume, volume) }
            mp.start()
            lastError = null
            true
        } catch (t: Throwable) {
            runCatching { mp.release() }
            if (player === mp) player = null
            lastError = "${t.javaClass.simpleName}：${t.message ?: "无详细信息"}"
            false
        }
    }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
    }

    override fun stopAndRelease() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}

/** MediaRecorder 录音器（原 VoiceInput，逐行迁移到接口实现） */
class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    /** 开始录音，返回落盘文件；正在录则先丢弃上一段 */
    override fun start(): File {
        cancel()
        val dir = File(context.cacheDir, "voice-input").apply { mkdirs() }
        val file = File(dir, "rec-${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        target = file
        startedAt = System.currentTimeMillis()
        return file
    }

    /**
     * 停止录音并返回文件：**太短（<0.6 秒）当作没说话**，返回 null 并删掉空文件。
     * 录过的临时文件由调用方（或下次 [start]）负责清理，缓存目录本身也会被"清除缓存"清掉。
     */
    override fun stop(): File? {
        val rec = recorder ?: return null
        val file = target
        recorder = null
        target = null
        val tooShort = System.currentTimeMillis() - startedAt < 600
        return try {
            rec.stop()
            rec.release()
            if (tooShort) {
                file?.delete()
                null
            } else {
                file?.takeIf { it.exists() && it.length() > 0 }
            }
        } catch (t: Throwable) {
            // stop() 在"刚 start 就 stop"或麦克风被抢占时会抛（RuntimeException），
            // 这属于可预期的用户操作，静默清理即可
            rec.runCatching { release() }
            file?.delete()
            null
        }
    }

    /** 放弃当前录音（页面销毁、切走、被其它录音抢占） */
    override fun cancel() {
        val rec = recorder ?: return
        recorder = null
        runCatching { rec.stop() }
        runCatching { rec.release() }
        target?.delete()
        target = null
    }
}

/** 系统语音识别（原 ChatViewModel 里的 SpeechRecognizer 段，逐行迁移到接口实现） */
class AndroidSpeechRecognizer(private val context: Context) : SpeechRecognizerPlatform {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun listen(languageTag: String, onResult: (String) -> Unit, onError: (Int) -> Unit) {
        // 创建与 startListening 必须在主线程（官方要求），所以 post 过去
        main.post {
            val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    onResult(text)
                }

                override fun onError(error: Int) = onError(error)

                // 其余回调用不上（不做波形动画、不用部分结果）：保持空实现，避免默认实现里再兜一次错
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                if (languageTag.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
            runCatching { r.startListening(intent) }.onFailure { onError(Int.MAX_VALUE) }
        }
    }

    override fun stopListening() {
        main.post { runCatching { recognizer?.stopListening() } }
    }

    override fun cancel() {
        main.post { runCatching { recognizer?.cancel() } }
    }

    override fun destroy() {
        main.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }
}
