package com.mysticat.roleplay.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * 麦克风录音（Step 3，2026-09-16）：按住说话 → 录一段 m4a → 交给 [AiClient.transcribe]。
 *
 * 参数口径：**16kHz / 单声道 / AAC(m4a)** —— 这是语音识别各家都吃的格式，
 * 也是手机端最省流量的组合（1 分钟约 120KB，硅基流动上限 50MB 够说很久）。
 *
 * 生命周期（三条都实测过坑）：
 * - [stop] 必须能在"还没说出话"时安全调用（点一下就松手）：录制时长太短时返回 null，
 *   由调用方给一句提示，**不要把空文件发出去**（服务端会 400，白花一次请求）；
 * - 页面被销毁/切走时调 [cancel] 直接丢弃，不产生半截文件；
 * - 同一实例重复 start 先 cancel 上一段（按住期间又按一次）。
 */
class VoiceInput(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var target: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** 开始录音，返回落盘文件；正在录则先丢弃上一段 */
    fun start(): File {
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
    fun stop(): File? {
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
    fun cancel() {
        val rec = recorder ?: return
        recorder = null
        runCatching { rec.stop() }
        runCatching { rec.release() }
        target?.delete()
        target = null
    }
}
