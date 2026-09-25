package com.mysticat.roleplay.data

import java.io.File
import java.security.MessageDigest

/**
 * 语音合成的本地缓存（TTS v2）。
 *
 * 同一句话**不重复付费**：缓存键 = `地址|模型|音色|混音|语速|音高|文本` 的 MD5，落在 `cacheDir/tts/`。
 * 第 44 轮起「清除缓存」**保留** tts（用户拍板：一键清理只清 tts 之外的全部，见 Repository.clearCachesIn）；
 * 早期它随清除缓存一起删，语义改为"缓存占用里可见、但不清"。
 *
 * ⚠️ 键里**必须**带上"会影响听感"的全部设置（2026-09-17 补）：早期只有「模型|音色|语速|文本」，
 * 于是改了**音高**、换了**供应商地址**、或**刚打开混合音色**时，试听用的那句样例文本没变 ⇒ 直接命中旧缓存，
 * 用户听到的还是上一个音色，会判定"配了没生效"。混合音色尤其明显：开关打开后声音应当立刻变。
 *
 * ⚠️ 传进来的 [settings] 是**生效设置**（`CharacterVoices.effective()` 的结果，E 批次起带角色专属音色），
 * 所以"换角色就换声音"是键里天然带出来的，不必在缓存之外另加判断 —— 反过来说，
 * **谁把调用点换回 `Repository.loadSettings()` 的全局设置，个性化就会整条命中旧缓存**（表现成"设了没用"）。
 */
object TtsCache {

    /**
     * 缓存键（纯函数，无副作用）：`地址|模型|音色|混音|语速|音高|文本` 的 MD5。
     *
     * 单独抽出来是为了让桌面自检能守住"**换音色必须换键**"这条：破了它的表现是
     * "个性化音色没生效"（听起来还是上一个声音），界面上**完全看不出来**，只有自检拦得住。
     */
    fun cacheKey(settings: AiSettings, text: String): String {
        val mixPart = if (settings.ttsMixEnabled) {
            settings.ttsMixSpeakers.joinToString(",") { "${it.voice}:${it.factor}" }
        } else ""
        val key = "${settings.ttsBaseUrl}|${settings.ttsModel}|${settings.ttsVoice}|$mixPart|" +
            "${settings.ttsSpeed}|${settings.ttsPitch}|$text"
        return MessageDigest.getInstance("MD5").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** 命中缓存直接返回文件；没有就调 [AiClient.textToSpeech] 合成后落盘再返回 */
    suspend fun file(settings: AiSettings, text: String): File {
        val dir = File(Repository.cacheRootDir, "tts").apply { mkdirs() }
        val name = cacheKey(settings, text) + ".mp3"
        val f = File(dir, name)
        if (f.exists() && f.length() > 0) return f
        val bytes = AiClient.textToSpeech(settings, text)
        // 原子落盘：先生成临时文件再改名，避免"半截文件被当成缓存命中"
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            tmp.delete()
            throw java.io.IOException("语音缓存写入失败")
        }
        return f
    }
}
