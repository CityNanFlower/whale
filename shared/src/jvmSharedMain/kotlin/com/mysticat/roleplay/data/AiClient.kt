package com.mysticat.roleplay.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * `AiClient` 的门面与公共管道：共享的 HTTP 客户端、地址校验、URL 规范化，
 * 以及两个跨端公开的读超时常量。
 *
 * 其余实现按职责分在 `AiSpeech.kt`（语音合成与识别）、`AiPrompt.kt`（系统提示词装配）、
 * `AiChat.kt`（对话与流式）、`AiAssist.kt`（看图/建议/创作提示词）、`AiImage.kt`（生图）里，
 * 都是本 object 的**顶层扩展函数**——调用点写法 `AiClient.xxx(...)` 不变。
 */

/**
 * @param soft true = **不是"出错了"**，而是模型这次没吐正文（空回复/只思考没说话）。
 *   界面上要当非模态提示处理：原来一律进 `error` → 弹模态「出错了」对话框把整页盖住，
 *   用户连「灵感回复」入口都点不到（2026-09-16 用户反馈"模型没返回内容时没有灵感回复"）。
 */
class AiException(message: String, val soft: Boolean = false) : Exception(message)

/**
 * OpenAI 兼容客户端：/chat/completions（对话）与 /images/generations（生图）。
 * Base URL / Key / 模型均来自设置页，任意兼容服务都可用。
 */
object AiClient {
    /** 对话/摘要/连通性测试这类请求的读超时：模型正常几秒到几十秒就答完了 */
    const val DEFAULT_READ_TIMEOUT_SECONDS = 120

    /**
     * **创作整卡**（灵感创作生成角色 / 工具 / 玩法卡）的读超时。
     *
     * 为什么单独有这一档：整卡 JSON 一次要吐 8192 max_tokens（多线还有多条开场白），用户把
     * 「创作思考强度」开到深度时，两分钟以上很常见。用对话那档 120 秒，用户看到的是**一句光秃秃的
     * "timeout"** —— 桌面端就是这个现象（2026-09-23 反馈），而 Android 那次只是模型答完了、
     * 落在了另一个解析缺陷上。给到 5 分钟，超时的文案也一并说清"该换模型"。
     */
    const val CREATION_READ_TIMEOUT_SECONDS = 300

    /**
     * 把服务端返回的**远端资源链接**一律升到 https 再下载。
     *
     * 为什么必须做（2026-09-17 用户实测踩到）：阿里返回的 OSS 预签名链接是 **http** 的
     * （`dashscope-result-bj.oss-cn-beijing.aliyuncs.com/...`），而 targetSdk 34 默认禁止明文，
     * OkHttp 直接抛 `CLEARTEXT communication to … not permitted by network security policy` ——
     * 合成明明成功，用户看到的却是"供应商合成失败"。
     *
     * 换 scheme **不会破坏签名**：OSS 的预签名覆盖的是 path + query（以及少数头部），与 scheme 无关，
     * 同一路径 https 一样能过（实测该域名 https 正常）。自己拼接的私有网段地址也会命中这里，
     * 那种情况本来就该走 http，故只对 http **公网**地址升级（私网/回环保持原样）。
     */
    fun httpsUrl(url: String): String {
        if (!url.startsWith("http://")) return url
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        val privateHost = host == "localhost" || host == "127.0.0.1" || host == "::1" ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host)
        return if (privateHost) url else "https://" + url.removePrefix("http://")
    }

    /**
     * 是否放行「本机 http 假服务端」（**只在调试包**由 `App.onCreate` 打开，
     * release 恒为 false）。用途：在没有余额、不发真实请求的前提下验证网络链路
     * （真流式、429 重试、非流式兜底解析等）——本地假服务端不需要 https 证书。
     */
    var allowInsecureLoopback = false
}

internal val json = Json { ignoreUnknownKeys = true }

internal val http by lazy {
    OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(AiClient.DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}

internal fun endpoint(base: String, path: String): String {
    val b = base.trim().trimEnd('/')
    return "$b/${path.trimStart('/')}"
}

internal fun errorMessage(body: String?, code: Int): String {
    val parsed = body?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject }
            .getOrNull()?.get("error")?.jsonObject
    }
    parsed?.get("message")?.jsonPrimitive?.contentOrNull?.let { return it }
    return "HTTP $code${body?.take(200)?.let { ": $it" } ?: ""}"
}

/**
 * 语音合成专用的地址校验（TTS v2）：除 https 外，**额外放行回环与私有网段**。
 *
 * 为什么破例：本地部署的语音服务（Kokoro-FastAPI / Speaches / Xinverse+CosyVoice / GPT-SoVITS 包装等）
 * 基本都跑在 `http://192.168.x.x:8880/v1` 这类地址上，一律要求 https 等于把本地部署这条路堵死。
 * 放行范围**写死**在回环与私有网段（10/8、172.16/12、192.168/16），公网 http 仍然拒绝 ——
 * 语音请求带的是**回复正文**（角色扮演内容隐私性高），所以设置页必须写明"明文只在你自己的网络里"。
 */
internal fun requireHttpsOrPrivate(url: String, label: String) {
    val u = url.trim()
    if (u.isBlank() || u.startsWith("https://")) return
    val host = runCatching { java.net.URI(u).host.orEmpty() }.getOrDefault("")
    val privateHost = host == "localhost" || host == "127.0.0.1" || host.startsWith("10.") ||
        host.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host) ||
        host == "::1"
    if (u.startsWith("http://") && privateHost) return
    throw AiException("$label 需要 https://（本地部署的服务可以用 http:// + 内网地址，当前：$u）")
}

/** 强制 Base URL 使用 https，避免 API Key/内容被明文传输 */
internal fun AiClient.requireHttps(url: String, label: String) {
    val u = url.trim()
    if (u.isBlank() || u.startsWith("https://")) return
    // 调试包 + 回环地址才放行（见 allowInsecureLoopback）；只认这三个回环写法，
    // 局域网/公网地址在调试包里同样被拦，避免"调试开关"变成事实上的降级
    if (allowInsecureLoopback &&
        (u.startsWith("http://10.0.2.2") || u.startsWith("http://localhost") ||
            u.startsWith("http://127.0.0.1"))
    ) return
    throw AiException("$label 必须使用 https://（当前：$u），请到「设置」修改")
}
