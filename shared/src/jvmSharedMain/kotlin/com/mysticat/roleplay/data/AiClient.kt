package com.mysticat.roleplay.data

import java.util.Base64
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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

    private val json = Json { ignoreUnknownKeys = true }

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

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_READ_TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private fun endpoint(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return "$b/${path.trimStart('/')}"
    }

    private fun errorMessage(body: String?, code: Int): String {
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
    private fun requireHttpsOrPrivate(url: String, label: String) {
        val u = url.trim()
        if (u.isBlank() || u.startsWith("https://")) return
        val host = runCatching { java.net.URI(u).host.orEmpty() }.getOrDefault("")
        val privateHost = host == "localhost" || host == "127.0.0.1" || host.startsWith("10.") ||
            host.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2[0-9]|3[01])\\.").containsMatchIn(host) ||
            host == "::1"
        if (u.startsWith("http://") && privateHost) return
        throw AiException("$label 需要 https://（本地部署的服务可以用 http:// + 内网地址，当前：$u）")
    }

    /**
     * 语音合成（TTS v2）：OpenAI 兼容 `POST /audio/speech`，返回音频字节（默认 mp3）。
     *
     * 口径（见 `docs/鲸鱼-TTS-v2-调研与设计.md`）：
     * - Key 走 [AiSettings.speechCredential]（一家一份：优先"语音专用凭据"，没有则回退「模型与API」里那家那把，
     *   所以界面**不强制**新增 Key 入口）；本地服务留空 Key 也放行；
     * - `speed` 取「语音朗读」页的语速（各家的取值范围不同，这里统一夹到 0.25~4.0）；
     * - 不做流式：按句切分后逐段请求（天然满足各家 4096 字符之类的上限），播放在 TtsSpeaker 里排队。
     */
    suspend fun textToSpeech(settings: AiSettings, text: String): ByteArray = withContext(Dispatchers.IO) {
        val base = settings.ttsBaseUrl.trim()
        if (base.isBlank()) throw AiException("请先在「语音朗读」里选择语音供应商")
        requireHttpsOrPrivate(base, "语音合成 Base URL")
        // Step 1（2026-09-16）：凭据走结构化入口 —— 按家优先取「语音专用凭据」，
        // 没有就回退到「模型与API」里那家的 Key（＝改动前的行为，老数据零破坏）。
        // 2026-09-17：火山/腾讯是**多字段凭据**（AppID+Token / SecretId+SecretKey），
        // 所以这里把整组凭据交给各协议分支，由分支自己取需要的字段。
        val cred = settings.speechCredential(base)
        val local = !base.startsWith("https://")
        val proto = ProviderProfiles.speechProtocol(base)
        // 火山 v1 没有"模型"这个概念（音色 id 自带模型家族）；腾讯把"模型"当 ModelType 档位用 ⇒ 都不强制
        val needsModel = proto != ProviderProfiles.SpeechProtocol.VOLC &&
            proto != ProviderProfiles.SpeechProtocol.TENCENT
        if (needsModel && settings.ttsModel.isBlank()) throw AiException("请先在「语音朗读」里选择语音模型")
        when (proto) {
            ProviderProfiles.SpeechProtocol.OPENAI,
            ProviderProfiles.SpeechProtocol.MINIMAX,
            ProviderProfiles.SpeechProtocol.DASHSCOPE -> {
                if (cred["apiKey"].orEmpty().isBlank() && !local) {
                    throw AiException("这家还没有填 API Key，请到「API 配置」里补上（本地服务可以留空）")
                }
            }

            ProviderProfiles.SpeechProtocol.VOLC -> {
                // 两代接入：填了 API Key 走 V3（新版控制台），否则要 AppID + Access Token（旧版）
                val hasKey = !cred["apiKey"].orEmpty().isBlank()
                val hasLegacy = !cred["appId"].orEmpty().isBlank() && !cred["accessToken"].orEmpty().isBlank()
                if (!hasKey && !hasLegacy) {
                    throw AiException(
                        "火山豆包语音要填凭据：新版控制台填 **API Key**（推荐）；旧版控制台填 AppID + Access Token" +
                            "（在「语音凭据 → 单独填一把」里填，方舟的 ark- Key 不适用）"
                    )
                }
            }

            ProviderProfiles.SpeechProtocol.TENCENT -> {
                if (cred["secretId"].orEmpty().isBlank() || cred["secretKey"].orEmpty().isBlank()) {
                    throw AiException("腾讯云语音要填 SecretId 与 SecretKey（在「语音凭据」里填，CAM 里创建）")
                }
            }
        }

        // 协议分派：形状不同的家各自适配（与图片那边的 ImageProtocol 同一个思路）
        when (proto) {
            ProviderProfiles.SpeechProtocol.OPENAI -> openAiSpeech(base, cred["apiKey"].orEmpty(), settings, text)
            ProviderProfiles.SpeechProtocol.MINIMAX -> miniMaxSpeech(base, cred["apiKey"].orEmpty(), settings, text)
            ProviderProfiles.SpeechProtocol.DASHSCOPE -> dashScopeSpeech(base, cred["apiKey"].orEmpty(), settings, text)
            ProviderProfiles.SpeechProtocol.VOLC -> {
                if (cred["apiKey"].orEmpty().isNotBlank()) volcV3Speech(base, cred, settings, text)
                else volcSpeech(base, cred, settings, text)
            }
            ProviderProfiles.SpeechProtocol.TENCENT -> tencentSpeech(base, cred, settings, text)
        }
    }

    /** OpenAI 兼容：`POST {base}/audio/speech` → 直接返回音频字节 */
    private fun openAiSpeech(base: String, key: String, settings: AiSettings, text: String): ByteArray {
        // 语速：各家范围不同（OpenAI/硅基流动都是 0.25~4.0），统一夹到该区间并取两位小数
        // ⚠️ 不能借用 safeTemperature —— 它会先夹到 0~2（那是温度的语义），会把 2.0 以上的语速吃掉
        val speed = ((settings.ttsSpeed.coerceIn(0.25f, 4.0f) * 100).roundToInt() / 100f).toDouble()
        val body = buildJsonObject {
            put("model", settings.ttsModel.trim())
            put("input", text)
            if (settings.ttsVoice.isNotBlank()) put("voice", settings.ttsVoice.trim())
            put("response_format", "mp3")
            put("speed", speed)
        }
        val builder = Request.Builder()
            .url(endpoint(base, "audio/speech"))
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        http.newCall(builder.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) throw AiException(errorMessage(String(bytes), resp.code))
            if (bytes.isEmpty()) throw AiException("语音服务没有返回音频（HTTP ${resp.code}）")
            WhaleLog.i("WhaleTts", "合成音频 ${bytes.size} 字节（OpenAI 协议）")
            return bytes
        }
    }

    /**
     * MiniMax 原生：`POST {base}/t2a_v2` → JSON 里 **hex** 编码的音频（官方文档的三种形态之一）。
     *
     * ⚠️ 形状取自多方一致的公开资料（官方文档站从本机反复超时，见 TTS v2 调研文档），
     * 所以失败信息**原样带上服务端的 `status_msg`**，用户一试听就能看到到底哪不对。
     */
    private fun miniMaxSpeech(base: String, key: String, settings: AiSettings, text: String): ByteArray {
        if (settings.ttsVoice.isBlank()) throw AiException("MiniMax 需要填音色 ID（形如 male-qn-qingse，从控制台复制）")
        // 语速 0.5~2.0；音高 MiniMax 用整数半音（约 -12~12），把 App 的 0.5~2.0 映射过去
        val speed = ((settings.ttsSpeed.coerceIn(0.5f, 2.0f) * 100).roundToInt() / 100f).toDouble()
        val pitch = ((settings.ttsPitch - 1.0f) * 12).roundToInt().coerceIn(-12, 12)
        val body = buildJsonObject {
            put("model", settings.ttsModel.trim())
            put("text", text)
            put("stream", false)
            put("output_format", "hex")
            putJsonObject("voice_setting") {
                put("voice_id", settings.ttsVoice.trim())
                put("speed", speed)
                put("vol", 1.0)
                put("pitch", pitch)
            }
            putJsonObject("audio_setting") {
                put("sample_rate", 32000)
                put("bitrate", 128000)
                put("format", "mp3")
                put("channel", 1)
            }
        }
        val request = Request.Builder()
            .url(endpoint(base, "t2a_v2"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException(errorMessage(raw, resp.code))
            val root = runCatching { json.parseToJsonElement(raw).jsonObject }
                .getOrElse { throw AiException("MiniMax 返回的不是 JSON：${raw.take(200)}") }
            val status = root["base_resp"]?.jsonObject?.get("status_code")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val statusMsg = root["base_resp"]?.jsonObject?.get("status_msg")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (status != 0) throw AiException("MiniMax 语音合成失败（$status）：$statusMsg")
            val hex = root["data"]?.jsonObject?.get("audio")?.jsonPrimitive?.contentOrNull
                ?: throw AiException("MiniMax 没有返回音频字段：${raw.take(200)}")
            val bytes = hexToBytes(hex)
            if (bytes.isEmpty()) throw AiException("MiniMax 返回的音频为空")
            WhaleLog.i("WhaleTts", "合成音频 ${bytes.size} 字节（MiniMax 协议）")
            return bytes
        }
    }

    /**
     * 阿里百炼原生（DASHSCOPE 协议）：`POST {base}/api/v1/services/audio/tts/SpeechSynthesizer`。
     *
     * 2026-09-17 一手核实官方《非实时语音合成 Qwen-Audio-TTS/CosyVoice HTTP API 参考》，
     * 与 OpenAI 兼容那套有**三处形状不同**：
     * ① 请求体是 `{model, input:{text, voice, format, sample_rate, rate}}` —— 音色在 `input` 里，
     *    且**音色 id 与模型版本绑定**（`longxiaochun_v2` 只配 cosyvoice-v2 系，混用直接 400）；
     * ② 非流式响应**不返回音频字节**，只给 `output.audio.url`（OSS 预签名链接、24 小时有效）
     *    ⇒ 必须**再下载一次**（下载不带 Authorization，签名在 URL 里）；
     * ③ 鉴权头仍是 `Authorization: Bearer <apiKey>`（与兼容模式同一把 Key，所以不用另填）。
     */
    private fun dashScopeSpeech(base: String, key: String, settings: AiSettings, text: String): ByteArray {
        if (settings.ttsVoice.isBlank()) {
            throw AiException("阿里百炼要指定音色（如 longxiaochun_v2，见官方《CosyVoice 音色列表》）")
        }
        // 官方 rate 范围 0.5~2.0（与 App 的语速滑杆一致）；**不发 pitch/volume**：官方文档里它们属 input 的可选参数，
        // 但社区实测部分版本对这两个字段直接 400，能不冒险就不冒险
        val rate = ((settings.ttsSpeed.coerceIn(0.5f, 2.0f) * 100).roundToInt() / 100f).toDouble()
        val body = buildJsonObject {
            put("model", settings.ttsModel.trim())
            putJsonObject("input") {
                put("text", text)
                put("voice", settings.ttsVoice.trim())
                put("format", "mp3")
                put("sample_rate", 22050)
                put("rate", rate)
            }
        }
        val request = Request.Builder()
            .url(endpoint(base, "api/v1/services/audio/tts/SpeechSynthesizer"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val raw = http.newCall(request).execute().use { resp ->
            val text2 = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException(dashScopeError(text2, resp.code))
            text2
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AiException("阿里百炼返回的不是 JSON：${raw.take(200)}") }
        // 官方出错时也会用 200 + `code`/`message` 的形态（不是 HTTP 错误码），见到就要当错误报，
        // 否则下面会一路走到"没有音频链接"这种看不出原因的报错
        root["code"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { code ->
            val msg = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            throw AiException("阿里百炼语音合成失败（$code）：$msg")
        }
        val url = root["output"]?.jsonObject?.get("audio")?.jsonObject
            ?.get("url")?.jsonPrimitive?.contentOrNull
            ?: throw AiException("阿里百炼没有返回音频链接：${raw.take(200)}")
        // 二次下载（预签名 OSS 链接）：**不要带 Authorization**，签名全在 URL 里
        val bytes = http.newCall(Request.Builder().url(httpsUrl(url)).get().build()).execute().use { resp ->
            val b = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful || b.isEmpty()) {
                throw AiException("音频下载失败（HTTP ${resp.code}）")
            }
            b
        }
        WhaleLog.i(
            "WhaleTts",
            "合成音频 ${bytes.size} 字节（DashScope 协议，二次下载）"
        )
        return bytes
    }

    /** DashScope 的错误体是 `{code, message, request_id}`，优先把服务端那句中文原话给用户 */
    private fun dashScopeError(body: String, code: Int): String {
        val msg = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return if (!msg.isNullOrBlank()) "阿里百炼：$msg" else errorMessage(body, code)
    }

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
     * 火山引擎豆包语音 **V3（新版控制台 API Key）**：
     * `POST {base}/api/v3/tts/unidirectional`，2026-09-17 核实官方《HTTP Chunked/SSE 单向流式-V3》。
     *
     * 与旧版 v1 的四处不同：
     * ① 鉴权是头部 **`X-Api-Key`**（新版单头；**不能**再带 AppID/Access-Key，会 `load grant not found`）
     *    + `X-Api-Resource-Id` + `X-Api-Request-Id`(UUID)；
     * ② `Resource-Id` **必须与音色家族匹配**（2.0 音色 `*_uranus_bigtts` → `seed-tts-2.0`、
     *    1.0 音色 `*_moon/_mars_bigtts` → `seed-tts-1.0`、复刻音色 `S_xxx` → `seed-icl-2.0`），
     *    不匹配会报 `resource ID is mismatched with speaker`；用户没填就按音色 id 自动判；
     * ③ 语速是 `speech_rate ∈ [-50,100]`（100 = 2.0×，-50 = 0.5×），不是倍率；
     * ④ 响应是 **NDJSON 分片**：每行一个 JSON，`data` 是 base64 音频块，要**逐行累加**；
     *    `code` 为 `0` / `20000000` 表示成功。
     */
    private fun volcV3Speech(
        base: String,
        cred: Map<String, String>,
        settings: AiSettings,
        text: String
    ): ByteArray {
        val apiKey = cred["apiKey"].orEmpty().trim()
        val voice = settings.ttsVoice.trim()
        // 混合音色（火山"超强混音"）：speaker 固定为 custom_mix_bigtts，真正的源在 mix_speaker.speakers[]
        val mix = settings.ttsMixSpeakers.filter { it.voice.isNotBlank() }
        val useMix = settings.ttsMixEnabled && mix.size >= 2
        if (!useMix && voice.isBlank()) {
            throw AiException("火山要填音色（speaker id，如 zh_female_xiaohe_uranus_bigtts，从控制台「音色库」复制）")
        }
        if (useMix && mix.size > 3) throw AiException("混合音色最多支持 3 个源音色（官方限制）")
        // Resource-Id：混音只能用 1.0 音色与复刻音色 ⇒ 全为复刻源时用复刻资源，否则按 1.0
        // （"是不是复刻源"要连本机音色库一起查：录音复刻出来的 id 没有前缀特征，见 `CloneVoice`）
        val cloneIds = VoiceCloneService.knownIds(settings)
        val isCloneSrc = { v: String ->
            val t = v.trim()
            t.startsWith("S_") || t.startsWith("icl_") || cloneIds.any { it.trim() == t }
        }
        val autoResource = if (useMix) {
            if (mix.all { isCloneSrc(it.voice) }) "seed-icl-2.0" else "seed-tts-1.0"
        } else volcResourceIdFor(voice, settings)
        // Resource-Id 优先级：凭据里手填的 > **混音时的自动判定** > 「语音模型」那栏选的 > 按音色自动判。
        // ⚠️ 混音时自动判定要排在「语音模型」之前：那一栏默认是 `seed-tts-2.0`（载入自愈填的），
        // 而 2.0 音色**不能**参与混音 —— 不改就会让"打开混音直接失败"（2026-09-17 检视发现）
        val fromModel = settings.ttsModel.trim().takeIf { it.startsWith("seed-") }
        val resourceId = cred["resourceId"].orEmpty().trim()
            .ifBlank { if (useMix) autoResource else (fromModel ?: autoResource) }
        // 我们的 0.5~2.0 倍率 → 官方 [-50,100]（1.0× = 0）
        val speechRate = ((settings.ttsSpeed - 1f) * 100f).roundToInt().coerceIn(-50, 100)
        val body = buildJsonObject {
            putJsonObject("user") { put("uid", "whale-android") }
            putJsonObject("req_params") {
                put("text", text)
                if (useMix) {
                    // 官方口径：混音时 speaker 固定 `custom_mix_bigtts`，源与权重放 mix_speaker.speakers[]
                    put("speaker", "custom_mix_bigtts")
                    val total = mix.sumOf { it.factor.toDouble() }.takeIf { it > 0.0 } ?: 1.0
                    putJsonObject("mix_speaker") {
                        putJsonArray("speakers") {
                            val src = mix.take(3)
                            var acc = 0.0
                            src.forEachIndexed { idx, m ->
                                // 权重按比例归一化；**最后一条取余数**，保证之和恰好为 1
                                // （三条 0.333 相加是 0.999，官方要求"之和为 1"，严格校验时会被拒）
                                val f = if (idx == src.lastIndex) {
                                    ((1.0 - acc) * 1000).roundToInt() / 1000.0
                                } else {
                                    ((m.factor / total) * 1000).roundToInt() / 1000.0
                                }
                                acc += f
                                addJsonObject {
                                    put("source_speaker", m.voice.trim())
                                    put("mix_factor", f)
                                }
                            }
                        }
                    }
                } else {
                    put("speaker", voice)
                }
                putJsonObject("audio_params") {
                    put("format", "mp3")
                    put("sample_rate", 24000)
                    put("speech_rate", speechRate)
                }
            }
        }
        val request = Request.Builder()
            .url(endpoint(base, "api/v3/tts/unidirectional"))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Request-Id", java.util.UUID.randomUUID().toString())
            // 让服务端把计费字符数一起回给我们，便于在日志里核对用量
            .header("X-Control-Require-Usage-Tokens-Return", "*")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val raw = http.newCall(request).execute().use { resp ->
            val s = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // 2026-09-17 实测常见两种：`resource ... not granted`（账号没开通/Key 没授权该资源）
                // 与 `resource ID is mismatched with speaker`（Resource-Id 与音色家族不配）——
                // 两者都是"能看懂但要动手"的错误，直接把下一步动作写进提示里
                val hint = when {
                    "not granted" in s ->
                        "（服务端说这个资源没授权：到豆包语音控制台「开通管理」确认已开通「语音合成大模型」，" +
                            "并确认这把 API Key 对该服务有权限；若只开通了 1.0，可把「语音合成模型」改成 " +
                            "`seed-tts-1.0` 并选用 1.0 音色再试）"
                    "mismatched with speaker" in s ->
                        "（Resource-Id 与音色不匹配：「语音合成模型」那一栏应与音色同代 —— " +
                            "2.0 音色→seed-tts-2.0、1.0 音色→seed-tts-1.0、复刻音色→seed-icl-2.0）"
                    "mix" in s.lowercase() || "factor" in s.lowercase() ->
                        "（混音失败：官方限制最多 3 个源音色、权重之和为 1，且源只能是 1.0 官方音色或复刻音色 S_xxx）"
                    else -> ""
                }
                throw AiException("火山 V3 合成失败（HTTP ${resp.code}）：${s.take(300)}$hint")
            }
            s
        }
        val out = java.io.ByteArrayOutputStream()
        var words = 0
        var lastMsg = ""
        for (line in raw.split("\n")) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val obj = runCatching { json.parseToJsonElement(t).jsonObject }.getOrNull() ?: continue
            val code = obj["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val msg = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (msg.isNotBlank()) lastMsg = msg
            // 0 与 20000000 都是成功（官方两种口径都出现过）；55000000 那类是参数/资源不匹配
            if (code != 0 && code != 20000000) {
                throw AiException("火山 V3 合成失败（$code）：${msg.ifBlank { t.take(200) }}")
            }
            obj["data"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { b64 ->
                out.write(java.util.Base64.getMimeDecoder().decode(b64))
            }
            obj["usage"]?.jsonObject?.get("text_words")?.jsonPrimitive?.contentOrNull
                ?.toIntOrNull()?.let { words = it }
        }
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) {
            throw AiException("火山 V3 没有返回音频${if (lastMsg.isNotBlank()) "：$lastMsg" else ""}")
        }
        WhaleLog.i(
            "WhaleTts",
            "合成音频 ${bytes.size} 字节（火山 V3${if (useMix) "·混合音色 ${mix.size} 源" else ""}" +
                "，resourceId=$resourceId，计费 ${words} 字）"
        )
        return bytes
    }

    /**
     * 火山豆包语音识别（**录音文件识别极速版**，2026-09-17 加）：
     * `POST {base}/api/v3/auc/bigmodel/recognize/flash`，
     * 头 `X-Api-Key`（新版控制台同一把 Key）+ `X-Api-Resource-Id: volc.bigasr.auc_turbo` + `X-Api-Request-Id`；
     * 音频走 **base64**（`audio.data`）—— 这是手机端唯一可行的形状（官方标准版只收公网 URL）。
     *
     * ⚠️ 该端点的请求体字段取自多份一致的社区实现（官方页那一版是旧 v1、只写 URL），
     * 所以失败时把服务端原话原样抛出来（`X-Api-Message` / body），照它改即可。
     */
    private fun volcFlashTranscribe(
        base: String,
        cred: Map<String, String>,
        settings: AiSettings,
        audio: java.io.File
    ): String {
        val apiKey = cred["apiKey"].orEmpty().trim()
        if (apiKey.isBlank()) {
            throw AiException("火山语音识别要填凭据：新版控制台填 API Key（在「语音凭据 → 单独填一把」里填）")
        }
        if (!audio.exists() || audio.length() == 0L) throw AiException("没有录到声音，请按住麦克风说话")
        val resourceId = settings.asrModel.trim().ifBlank { "volc.bigasr.auc_turbo" }
        val b64 = java.util.Base64.getEncoder().encodeToString(audio.readBytes())
        val body = buildJsonObject {
            putJsonObject("user") { put("uid", "whale-android") }
            putJsonObject("audio") {
                // 录制端是 m4a(AAC)，识别端按 mp3 容器族处理；格式不对时服务端会明确报出来
                put("format", if (audio.name.endsWith(".wav")) "wav" else "mp3")
                put("data", b64)
            }
            putJsonObject("request") {
                put("model_name", "bigmodel")
                put("show_utterances", false)
            }
        }
        val request = Request.Builder()
            .url(endpoint(base, "api/v3/auc/bigmodel/recognize/flash"))
            .header("Content-Type", "application/json")
            .header("X-Api-Key", apiKey)
            .header("X-Api-Resource-Id", resourceId)
            .header("X-Api-Request-Id", java.util.UUID.randomUUID().toString())
            .header("X-Api-Sequence", "-1")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val raw = http.newCall(request).execute().use { resp ->
            val s = resp.body?.string().orEmpty()
            val statusCode = resp.header("X-Api-Status-Code").orEmpty()
            val statusMsg = resp.header("X-Api-Message").orEmpty()
            // 火山的风格是"HTTP 200 + 头部状态码"：20000000 才算成功
            if (!resp.isSuccessful || (statusCode.isNotBlank() && statusCode != "20000000")) {
                val hint = when {
                    "grant" in statusMsg || "not granted" in s ->
                        "（该资源未授权：到豆包语音控制台「开通管理」确认已开通录音文件识别 2.0 / 极速版）"
                    "resource" in statusMsg && "match" in statusMsg ->
                        "（Resource-Id 与音色/服务不匹配：极速版是 volc.bigasr.auc_turbo）"
                    else -> ""
                }
                throw AiException(
                    "火山语音识别失败（HTTP ${resp.code}${if (statusCode.isNotBlank()) " / $statusCode" else ""}）：" +
                        "${statusMsg.ifBlank { s.take(200) }}$hint"
                )
            }
            s
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw AiException("火山返回的不是 JSON：${raw.take(200)}")
        // 结果位置有两种口径（顶层 text / result.text），都认一下
        val text = root["result"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            ?: root["text"]?.jsonPrimitive?.contentOrNull
            ?: root["result"]?.jsonObject?.get("utterances")?.jsonArray
                ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            ?: throw AiException("火山没有返回识别文本：${raw.take(200)}")
        WhaleLog.i("WhaleAsr", "火山识别到 ${text.length} 字（${audio.length()} 字节音频）")
        return text.trim()
    }

    /**
     * 按音色 id 推断 V3 的 Resource-Id。**实现只有一处**（`ModelCatalog.volcResourceIdForVoice`）：
     * 界面选音色时也要用同一条规则去自动填「语音模型」栏，两处各写一份迟早走偏。
     * 这里多带一个「本机复刻音色库」——录音复刻生成的 id 没有 `S_` 前缀，不查库会被当成 2.0 音色
     * （发 `seed-tts-2.0` 必报 `resource ID is mismatched with speaker`，见 `CloneVoice`）。
     */
    private fun volcResourceIdFor(voice: String, settings: AiSettings): String =
        ModelCatalog.volcResourceIdForVoice(voice, VoiceCloneService.knownIds(settings))

    /**
     * 火山引擎豆包语音（VOLC 协议）：`POST {base}/api/v1/tts`。
     *
     * 2026-09-17 核实官方《大模型HTTP非流式接口-V1》，与前三家的**四处不同**：
     * ① 凭据（`appid`/`token`/`cluster`）**放在请求体里**，头部只有 `Authorization: Bearer;<token>` ——
     *    **分号分隔**，写成 `Bearer <token>`（空格）会 401，这是最常见的踩坑点；
     * ② 没有"模型"字段，音色 id（`voice_type`）自带模型家族（`*_bigtts` 是大模型）；
     * ③ `reqid` **每次必须唯一**（官方要求，重复会失败），这里用 UUID；
     * ④ 响应 `data` 是 **base64** 音频，成功码是 `code == 3000`。
     */
    private fun volcSpeech(
        base: String,
        cred: Map<String, String>,
        settings: AiSettings,
        text: String
    ): ByteArray {
        val appId = cred["appId"].orEmpty().trim()
        val token = cred["accessToken"].orEmpty().trim()
        val cluster = cred["cluster"].orEmpty().trim().ifBlank { "volcano_tts" }
        if (settings.ttsVoice.isBlank()) {
            throw AiException("火山要填音色（voice_type，如 zh_female_xxx_bigtts，从控制台音色列表复制）")
        }
        // 官方 speed_ratio 范围 0.2~3.0
        val speed = ((settings.ttsSpeed.coerceIn(0.2f, 3.0f) * 100).roundToInt() / 100f).toDouble()
        val body = buildJsonObject {
            putJsonObject("app") {
                put("appid", appId)
                put("token", token)
                put("cluster", cluster)
            }
            // uid 只是调用方标识（官方示例里的"用户唯一标识"），本地 App 无需真实用户体系
            putJsonObject("user") { put("uid", "whale-android") }
            putJsonObject("audio") {
                put("voice_type", settings.ttsVoice.trim())
                put("encoding", "mp3")
                put("speed_ratio", speed)
            }
            putJsonObject("request") {
                put("reqid", java.util.UUID.randomUUID().toString())
                put("text", text)
                put("text_type", "plain")
                put("operation", "query")
            }
        }
        val request = Request.Builder()
            .url(endpoint(base, "api/v1/tts"))
            .header("Content-Type", "application/json")
            // ⚠️ 分号！官方文档原文如此（Bearer;token），不是 Bearer token
            .header("Authorization", "Bearer;$token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val raw = http.newCall(request).execute().use { resp ->
            val s = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException("火山语音合成失败（HTTP ${resp.code}）：${s.take(200)}")
            s
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AiException("火山返回的不是 JSON：${raw.take(200)}") }
        val code = root["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
        val message = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        // 官方口径：code == 3000 为成功；其余（含 3001 参数错、3003 并发超限、3010 文本过长…）都当失败报原话
        if (code != 3000) throw AiException("火山语音合成失败（$code）：$message")
        val b64 = root["data"]?.jsonPrimitive?.contentOrNull
            ?: throw AiException("火山没有返回音频字段：${raw.take(200)}")
        val bytes = java.util.Base64.getMimeDecoder().decode(b64)
        if (bytes.isEmpty()) throw AiException("火山返回的音频为空")
        WhaleLog.i("WhaleTts", "合成音频 ${bytes.size} 字节（火山协议）")
        return bytes
    }

    /**
     * 腾讯云语音合成（TENCENT 协议）：`POST https://tts.tencentcloudapi.com`，
     * Action `TextToVoice` / Version `2019-08-23`，鉴权是 **TC3-HMAC-SHA256**（2026-09-17 核实官方接口文档）。
     *
     * 与本项目其它家都不同的一点：**签名要自己算**（SecretKey 不直接发出去）——
     * 派生链 `TC3+SecretKey → 日期 → 服务(tts) → tc3_request`，最后对 stringToSign 做 HMAC-SHA256。
     * 签名头只签 `content-type;host` 两个头，签名串里的值必须与服务端收到的**逐字一致**
     * （Content-Type 的值含 `; charset=utf-8`，少一个空格都会 SignatureFailure）。
     */
    private fun tencentSpeech(
        base: String,
        cred: Map<String, String>,
        settings: AiSettings,
        text: String
    ): ByteArray {
        val secretId = cred["secretId"].orEmpty().trim()
        val secretKey = cred["secretKey"].orEmpty().trim()
        val region = cred["region"].orEmpty().trim().ifBlank { "ap-guangzhou" }
        val voiceType = settings.ttsVoice.trim().toIntOrNull()
            ?: throw AiException("腾讯云要填**数字**音色 id（VoiceType，如 101001，从控制台音色列表复制）")
        // 借用"语音模型"这个字段当 ModelType 档位：3 = 大模型音色 / 2 = 精品 / 1 = 基础（官方口径）
        val modelType = settings.ttsModel.trim().toIntOrNull() ?: 3
        // 官方 Speed 是"档位"（范围 [-2, 6]、0 = 正常语速），不是倍率 ⇒ 把 App 的 0.5~2.0 线性映射到 [-1, 2]。
        // ⚠️ 别再写 `coerceIn(-2, 6)`：映射结果本来就在区间内，那是死代码（2026-09-17 检视指出）
        val speed = ((settings.ttsSpeed - 1f) * 2f).roundToInt()
        val payload = buildJsonObject {
            put("Text", text)
            put("SessionId", java.util.UUID.randomUUID().toString())
            put("VoiceType", voiceType)
            put("Codec", "mp3")
            put("Speed", speed)
            put("ModelType", modelType)
        }.toString()
        val host = runCatching { java.net.URI(base.trim()).host.orEmpty() }.getOrDefault("")
            .ifBlank { "tts.tencentcloudapi.com" }
        val service = "tts"
        val action = "TextToVoice"
        val version = "2019-08-23"
        val timestamp = System.currentTimeMillis() / 1000
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(java.util.Date(timestamp * 1000))
        val contentType = "application/json; charset=utf-8"
        val canonicalRequest = listOf(
            "POST",
            "/",
            "",
            "content-type:$contentType\nhost:$host\n",
            "content-type;host",
            sha256Hex(payload)
        ).joinToString("\n")
        val credentialScope = "$date/$service/tc3_request"
        val stringToSign = listOf(
            "TC3-HMAC-SHA256",
            timestamp.toString(),
            credentialScope,
            sha256Hex(canonicalRequest)
        ).joinToString("\n")
        val secretDate = hmacSha256(("TC3$secretKey").toByteArray(Charsets.UTF_8), date)
        val secretService = hmacSha256(secretDate, service)
        val secretSigning = hmacSha256(secretService, "tc3_request")
        val signature = hmacSha256(secretSigning, stringToSign).joinToString("") { "%02x".format(it) }
        val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, " +
            "SignedHeaders=content-type;host, Signature=$signature"
        val request = Request.Builder()
            .url(endpoint(base.trim().ifBlank { "https://tts.tencentcloudapi.com" }, ""))
            .header("Content-Type", contentType)
            .header("Host", host)
            .header("X-TC-Action", action)
            .header("X-TC-Version", version)
            .header("X-TC-Timestamp", timestamp.toString())
            .header("X-TC-Region", region)
            .header("Authorization", authorization)
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val raw = http.newCall(request).execute().use { resp ->
            val s = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException("腾讯云语音合成失败（HTTP ${resp.code}）：${s.take(200)}")
            s
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw AiException("腾讯云返回的不是 JSON：${raw.take(200)}") }
        val respObj = root["Response"]?.jsonObject
            ?: throw AiException("腾讯云返回缺少 Response：${raw.take(200)}")
        respObj["Error"]?.jsonObject?.let { err ->
            val code = err["Code"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val message = err["Message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            throw AiException("腾讯云语音合成失败（$code）：$message")
        }
        val b64 = respObj["Audio"]?.jsonPrimitive?.contentOrNull
            ?: throw AiException("腾讯云没有返回 Audio 字段：${raw.take(200)}")
        val bytes = java.util.Base64.getMimeDecoder().decode(b64)
        if (bytes.isEmpty()) throw AiException("腾讯云返回的音频为空")
        WhaleLog.i("WhaleTts", "合成音频 ${bytes.size} 字节（腾讯云 TC3 协议）")
        return bytes
    }

    private fun sha256Hex(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun hmacSha256(key: ByteArray, msg: String): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
    }

    /** MiniMax 的音频是 hex 字符串，两字符一字节 */
    private fun hexToBytes(hex: String): ByteArray {
        // 长度/字符都要校验：`Character.digit` 对非法字符返回 -1，会被当成字节拼进音频，
        // 结果是"能播但全是噪音"的坏数据（静默失败比报错难查得多）
        val h = hex.trim()
        if (h.length < 2 || h.length % 2 != 0) throw AiException("音频数据长度不对（${h.length} 位十六进制）")
        val n = h.length / 2
        val out = ByteArray(n)
        for (i in 0 until n) {
            val hi = Character.digit(h[i * 2], 16)
            val lo = Character.digit(h[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) throw AiException("音频数据不是合法的十六进制（第 ${i * 2} 位）")
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /**
     * 语音识别（ASR / 语音输入，Step 3）：OpenAI 兼容 `POST /audio/transcriptions`，**multipart**。
     *
     * 一手核实（2026-09-16，硅基流动官方文档）：表单字段就是 `file`（音频，≤1h/≤50MB）+ `model`（必填），
     * 响应 `{"text": "…"}`。`language` 是选填项，**默认不发**（文档没写，用户填了才带）。
     * 凭据与合成**共用同一把**（`speechCredential`），所以硅基流动不需要再填一次。
     */
    suspend fun transcribe(settings: AiSettings, audio: java.io.File): String = withContext(Dispatchers.IO) {
        val base = settings.asrBaseUrl.trim()
        if (base.isBlank()) throw AiException("请先在「语音朗读」页的「语音输入」里选择识别供应商")
        requireHttpsOrPrivate(base, "语音识别 Base URL")
        // 凭据与合成共享同一组（硅基流动一把 Key 两用；火山同理用同一把 API Key）
        val cred = settings.speechCredential(base)
        val proto = ProviderProfiles.asrProtocol(base)
        if (proto == ProviderProfiles.AsrProtocol.VOLC_FLASH) {
            // ⚠️ 火山的"识别模型"其实是 X-Api-Resource-Id，且分支里已有默认值 ⇒ 不在这里强求非空
            return@withContext volcFlashTranscribe(base, cred, settings, audio)
        }
        if (settings.asrModel.isBlank()) throw AiException("请先选择识别模型")
        val key = cred["apiKey"].orEmpty()
        val local = !base.startsWith("https://")
        if (key.isBlank() && !local) throw AiException("这家还没有填 API Key，请到「模型与API」里补上（本地服务可以留空）")
        if (!audio.exists() || audio.length() == 0L) throw AiException("没有录到声音，请按住麦克风说话")

        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            // 字段名与文件名都照官方文档；扩展名别改（服务端按它判断容器格式）。
            // MIME 按扩展名给：Android 录的是 m4a，桌面录的是 wav（TargetDataLine→WAV），
            // 早先这里写死 audio/mp4 —— 桌面上传 wav 会带着错的类型头，服务端按类型判就会挑错解码器。
            .addFormDataPart(
                "file", audio.name,
                audio.asRequestBody(audioMimeFor(audio).toMediaType())
            )
            .addFormDataPart("model", settings.asrModel.trim())
        if (settings.asrLanguage.isNotBlank()) form.addFormDataPart("language", settings.asrLanguage.trim())

        val builder = Request.Builder()
            .url(endpoint(base, "audio/transcriptions"))
            .post(form.build())
        if (key.isNotBlank()) builder.header("Authorization", "Bearer $key")
        // ⚠️ 这里不能直接 return（在 withContext 的 lambda 里，非内联 → 不许非局部返回），
        // 所以把结果取到变量里，最后再返回
        val text = http.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException(errorMessage(raw, resp.code))
            runCatching {
                json.parseToJsonElement(raw).jsonObject["text"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: throw AiException("识别服务没有返回 text 字段：${raw.take(200)}")
        }
        WhaleLog.i("WhaleAsr", "识别到 ${text.length} 字（${audio.length()} 字节音频）")
        text.trim()
    }

    /**
     * 按扩展名给音频上传的 MIME（见 [transcribe] 里的说明）：Android 录 m4a、桌面录 wav，
     * 服务端按它挑解码器，写错类型会被判成"格式不支持"。认不出就退回 application/octet-stream。
     */
    private fun audioMimeFor(file: java.io.File): String =
        when (file.extension.lowercase(java.util.Locale.ROOT)) {
            "wav", "wave" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "webm" -> "audio/webm"
            "amr" -> "audio/amr"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }

    /** 强制 Base URL 使用 https，避免 API Key/内容被明文传输 */
    /**
     * 是否放行「本机 http 假服务端」（**只在调试包**由 `App.onCreate` 打开，
     * release 恒为 false）。用途：在没有余额、不发真实请求的前提下验证网络链路
     * （真流式、429 重试、非流式兜底解析等）——本地假服务端不需要 https 证书。
     */
    var allowInsecureLoopback = false

    private fun requireHttps(url: String, label: String) {
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

    /**
     * 组装系统提示词（角色设定 + 世界观 + 用户设定 + 自定义系统提示词）。
     *
     * ⚠️ 视角约定必须写死：角色卡的「世界观 / 当前场景」是用角色的第一人称写的
     * （例如「我是一个穿越者」），不声明「这里的我 = 角色本人」的话，
     * 模型很容易把它当成用户的身份，反过来管用户叫穿越者。
     * 用户设定为空时也要明确交代「用户没给设定」，否则模型会自己替用户编身份。
     *
     * [mode] 决定这一条请求在做什么（RP / TOOL / PLAY / SUGGEST），见 [PromptMode]：
     * 三种模式**共用中间的资料块**，只换开头与各块的括注——另起一套模板就要维护两份分层注入、
     * 宏展开与排序，必然漂移（台账 §5.2）。
     *
     * 角色设定走**分层 or 老口径**二选一（E2，2026-09-22，判据 [CharacterCard.hasLayeredPersona]）：
     * 新字段有值就分开注入 description / personality / mesExample；全空则把合并块 [CharacterCard.persona]
     * 整块照旧注入 —— 库里已有的老卡因此行为一字不变（不做正则反向拆，见第 66 轮定稿）。
     */
    fun buildSystemPrompt(
        card: CharacterCard,
        settings: AiSettings,
        userSetting: String = "",
        styleHint: String = "",
        memory: String = "",
        summary: String = "",
        mode: PromptMode = PromptMode.RP
    ): String = buildString {
        // 宏展开（E2）：只作用在**从卡里读出来的文本**上。用户自己写的东西
        // （本会话设定 / 全局补充要求 / 记忆 / 前情）不展开 —— 那是人话，不是模板。
        fun m(text: String): String = expandMacros(text, card)

        val sug = mode == PromptMode.SUGGEST
        val layered = card.hasLayeredPersona()

        when (mode) {
            PromptMode.SUGGEST -> {
                append("下面是一段角色扮演对话的场景资料。本次任务**不扮演任何角色**：")
                append("你要做的是替**用户**代笔写台词，所以下面的资料只是背景，不要续写剧情。\n")
                append("资料里【角色设定】【世界观 / 当前场景】中的「我 / 我的」都指角色（")
                append(card.name).append("）本人，【用户的设定】中的「我」指用户。\n\n")
            }
            PromptMode.TOOL -> {
                // 工具形态（E4）：与陪伴的分野不是"有没有人格"，而是**交互契约不同**——
                // 用户消息是待处理素材、输出是可直接拿走的成品、评判标准是"完成没/对不对"（台账 §5.1）。
                append("你是一个**任务处理工具**，不是在扮演中的角色：用户发来的是**待处理的素材或指令**，")
                append("你要给出**可以直接拿去用的成品**，而不是虚构故事里的一回合。\n")
                append("对话双方：你 = 完成任务的工具；对方 = 用户（下达任务的人）。\n\n")
                append("【任务契约（必须遵守）】\n")
                append("1. 不扮演任何角色、不用第一人称叙述场景、不替用户说话，也不写「（动作描写）」。\n")
                append("2. 只输出任务要求的结果本身：不寒暄、不解释你怎么做的、不加「以下是……」之类的前后缀")
                append("（除非【输出格式】明确要求）。\n")
                append("3. 用户后续消息是对同一件事的补充要求（「再短一点」「换一批」）：按补充要求重做，")
                append("或另给一份新的结果。\n")
                append("4. 素材里出现的人格、语气、世界观只当**风格参考**，不构成一段需要延续的剧情。\n\n")
            }
            PromptMode.PLAY -> {
                // 玩法形态（E4 拆档，2026-09-23）：与工具同属"不扮演虚构角色"，但**评判标准不同**——
                // 工具问"成品能不能直接用"，玩法问"规则守得住、进度记得住、这一局好不好玩"（报告 §三）。
                // 用户发来的是**这一回合**（一个编号、一句回答）而不是素材，所以要回来的也不是"成品"。
                append("你正在陪用户玩一局**有规则的互动玩法**（猜谜、对弈、情景问答这一类）：你不是剧情里的角色，")
                append("也不是处理素材的工具，而是**主持并参与这一局**的一方。\n")
                append("对话双方：你 = 出题/主持这局玩法的一方；对方 = 用户（正在玩的人）。\n\n")
                append("【玩法契约（必须遵守）】\n")
                append("1. 严格遵守【玩法规则】——它是这一局的硬约束：不因为用户要求、不为让用户开心，")
                append("就改规则、放宽判定或跳过环节。\n")
                append("2. 用户发来的是**这一回合**（一个选项编号、一次回答或一个动作），不是待加工的素材、")
                append("也不是要你续写的剧情：回应这一回合，然后把局面往前推一步。\n")
                append("3. 轮到你出手时要**挑信息量最大的那一步**：先在心里排除已经排除过的，不要把否掉过的答案")
                append("再摆一遍，也不要罗列一眼就不合的候选——一个回合只出一个动作。\n")
                append("4. 输出要短：一次一回合，不总结、不复述规则、不做长篇叙述与动作描写（最多一句情绪性的话）。\n")
                if (card.playOptions > 0) {
                    append("5. 每轮结尾给 ").append(card.playOptions)
                    append(" 个编号选项（「1. …」，各占一行）：用户回一个数字就能继续。")
                    append("选项要彼此**有意义地不同**，别给凑数的重复项。\n")
                }
                append("\n")
            }
            PromptMode.RP -> {
                append("你正在扮演角色「").append(card.name).append("」，与用户进行沉浸式角色扮演对话。\n")
                append("对话双方：你 = 「").append(card.name)
                append("」（角色本人）；对方 = 用户（另一个真实的人，由用户自己扮演）。\n\n")

                append("【视角约定（必须遵守）】\n")
                append("1. 下面的「角色设定」「世界观 / 当前场景」是**你自己**的设定；其中出现的「我 / 我的 / 自己」都指你（")
                append(card.name).append("）本人，不是用户。\n")
                append("2. 用户只等于「用户」。除非「用户的设定」里明确写了，否则不要给用户安排身份、经历、关系或心理活动，")
                append("也不要把自己的设定说成是用户的，更不要反问用户「你才是……」来把设定扣到用户头上。\n")
                append("3. 称呼上：用「我」指自己、用「你」指用户，两者绝不混用（除非【叙事风格】另行指定视角）。\n")
                append("4. 始终以角色的第一人称、口吻与性格回应（除非【叙事风格】另行指定视角或角色主动性，届时以【叙事风格】为准）；")
                append("不要替用户说话；不要暴露你是 AI；回应要有画面感与情绪，可适当推动剧情——")
                append("每条结尾可自然留一个钩子（未完成的动作、开放问题或新出现的小变化），但绝不替用户决定怎么接；")
                append("长度与篇幅默认贴合对话，【叙事风格】另有要求时以其为准。\n")
                append("5. 排版：对白直接写普通文本，不要用「」或引号包裹；动作、神态、心理等描写用（）括注；")
                append("重点词语可少量 **加粗**；不要输出列表、标题等 markdown 结构。\n\n")
            }
        }

        // ── 任务说明 / 输出格式（工具形态专属，E4）──────────────────────────────
        // 不复用 persona / scenario：编辑器标签语义不对（工具作者看到"人设/世界观"会困惑），
        // 而 persona 在以"这是你本人的设定"注入时会**主动诱发扮演**（台账 §5.2）。
        if (mode == PromptMode.TOOL) {
            val brief = m(card.taskBrief).trim()
            if (brief.isNotBlank()) append("【任务说明】\n").append(brief).append("\n\n")
            val fmt = m(card.outputFormat).trim()
            append("【输出格式】\n")
            if (fmt.isNotBlank()) append(fmt).append("\n")
            append("硬性要求：只给出结果本身，不寒暄、不解释、不重复用户的素材、不加任何前后缀。\n\n")
            // 人格在这里降级为**风格滤镜**（台账 §5.1）：说明它是风格参考，而不是"你是这个人"
            val styleRef = listOf(m(card.description).trim(), m(card.personality).trim())
                .filter { it.isNotBlank() }.joinToString("\n")
            if (styleRef.isNotBlank()) {
                append("【风格参考】（只借用它的语气与用词习惯，不要扮演其中的角色、不要续写它的剧情）\n")
                append(styleRef).append("\n\n")
            }
        }

        // ── 玩法形态专属块（E4 拆档，2026-09-23）────────────────────────────────
        // 三样东西：规则（硬约束，玩法唯一的"对错来源"）、状态项（这一局要记什么）、玩法设定（背景前提）。
        // 都不复用 persona / scenario 的原本语义：那两块是以"这是你本人的设定"注入的，会主动诱发扮演，
        // 而玩法型的 description / personality 在这里只是**风格参考**（同工具，报告 §四）。
        if (mode == PromptMode.PLAY) {
            val rules = m(card.playRules).trim()
            if (rules.isNotBlank()) {
                append("【玩法规则】（这一局的硬约束，必须遵守；用户提出修改也要先守住它）\n")
                append(rules).append("\n\n")
            }
            val state = m(card.playState).trim()
            if (state.isNotBlank()) {
                append("【本局要记的状态】（每一轮都要与它保持一致，别忘了已经确认过的结论）\n")
                append(state).append("\n\n")
            }
            val setup = m(card.scenario).trim()
            if (setup.isNotBlank()) {
                append("【玩法设定】（这一局的前提与背景；它是规则的一部分，不是要续写的剧情）\n")
                append(setup).append("\n\n")
            }
            val styleRef = listOf(m(card.description).trim(), m(card.personality).trim())
                .filter { it.isNotBlank() }.joinToString("\n")
            if (styleRef.isNotBlank()) {
                append("【风格参考】（只借用它的语气与用词习惯，不要扮演其中的角色、不要续写它的剧情）\n")
                append(styleRef).append("\n\n")
            }
        }

        // ── 角色设定：分层 or 老口径 ─────────────────────────────────────────
        // 工具与玩法**都不走这一段**：它们的 description / personality 是风格参考（上面各自注入了），
        // 而"这是你本人的设定"这个身份框架会把它变成一个人在扮演（报告 §三）。
        val playsRole = mode != PromptMode.TOOL && mode != PromptMode.PLAY
        if (playsRole) {
            if (layered) {
                val desc = m(card.description).trim()
                val pers = m(card.personality).trim()
                if (desc.isNotBlank() || pers.isNotBlank()) {
                    append("【角色设定】").append(if (sug) "（角色本身的设定，「我」= 角色）" else "（你本人）").append("\n")
                    // 分开写而不是揉成一段：description 管事实、personality 管气质，
                    // 混在一起模型会互相复读（调研 §一 personality 条）。
                    if (desc.isNotBlank()) append("【角色描述】\n").append(desc).append("\n")
                    if (pers.isNotBlank()) append("【性格特点】\n").append(pers).append("\n")
                    append("\n")
                }
            } else if (card.persona.isNotBlank()) {
                // 老口径：库里已有的卡 persona 是当年拼好的整块文本（含【背景故事】【性格特点】小标题），
                // 不反向拆，整块注入 —— 老卡行为与本轮之前完全一致。
                append("【角色设定】")
                    .append(if (sug) "（角色本身的设定，「我」= 角色）" else "（你本人）")
                    .append("\n").append(m(card.persona).trim()).append("\n\n")
            }
        }
        if (playsRole && card.scenario.isNotBlank()) {
            append("【世界观 / 当前场景】")
                .append(if (sug) "（里面的「我」= 角色）" else "（以你自己的视角书写，里面的「我」= 你）")
                .append("\n").append(m(card.scenario).trim()).append("\n\n")
        }
        // ── 对话示例（E2 新增层）────────────────────────────────────────────
        // 位置在场景之后：它是**语气示范**，离生成点近一些；同时明确"这是示范，不是剧情"，
        // 否则模型会把示例里的情节当成已经发生过的事接着往下写（社区最常见的烂卡症状之一）。
        if (playsRole) {
            val ex = m(card.mesExample).trim()
            if (ex.isNotBlank()) {
                append("【对话示例】（照着这个语气、节奏与长短来回复；示例只是示范，其中的情节没有发生过）\n")
                append(ex).append("\n\n")
            }
        }
        if (userSetting.isNotBlank()) {
            append("【用户的设定】")
                .append(if (sug) "（只描述用户自己，里面的「我」= 用户）" else "（只描述用户自己，不适用于你）")
                .append("\n").append(userSetting.trim()).append("\n\n")
        } else if (mode == PromptMode.RP) {
            // 工具形态**不加**这段：它会把用户发来的任务素材误读成"交互关系设定"（台账 §5.2）
            append("【用户的设定】\n")
            append("（用户没有提供身份设定：把用户当作刚认识的陌生人，")
            append("不要假设任何过往、称呼、关系或身份；用户说了什么你才知道什么。）\n\n")
        }
        if (summary.isNotBlank()) {
            append("【前情提要】（更早剧情的摘要，请当作你已经知道）\n").append(summary.trim()).append("\n\n")
        }
        if (memory.isNotBlank()) {
            // 段名与括注**跟形态走**（E4 拆档）：陪伴/多线是关系向的【共同记忆】，玩法型要的是
            // 进度向的【当前进度】——段名说错，模型会把"已排除的名单"当成"我们的共同经历"来对待。
            val mem = Engines.of(card)
            append("【").append(mem.memorySection).append("】（").append(mem.memoryHint).append("）\n")
            append(memory.trim()).append("\n\n")
        }
        if (settings.extraSystemPrompt.isNotBlank()) {
            append("【补充要求】\n").append(settings.extraSystemPrompt.trim()).append("\n\n")
        }
        // 【叙事风格】放在系统提示词的**最末**（2026-09-16 第 9 轮调整）：
        // 原来它排在【补充要求】之前，而系统提示词光"角色设定 + 世界观 + 用户设定 + 前情提要 + 共同记忆"
        // 就有一两千字，风格段埋在中段会被稀释 —— 用户实测"选了沉浸小说（丰沛 250~500 字）但回复还是百来字"。
        // 挪到最末取近因效应，让风格要求离生成点最近；配合"风格刚切换"的一次性告知抵消历史短回复的锚定。
        if (styleHint.isNotBlank()) {
            append(styleHint.trim()).append("\n")
        }
    }

    /**
     * 新会话要预置的消息（E4 拆档，2026-09-23）。
     *
     * **纯函数**：聊天页与桌面自检共用同一份口径 —— 这段规则以前写在 `ChatViewModel.newConversation`
     * 里，而它是 ViewModel 的私有逻辑，自检碰不到，于是"开场到底铺不铺"只能靠人眼看。
     * 形态在这件事上是三个不同的口径：
     * - 陪伴 / 多线：场景卡（此刻的处境）+ 开场白；
     * - 工具：**什么都不铺** —— 第一条必须是用户的任务素材，铺了开场就不是工具会话了；
     * - 玩法：**铺**开局引导（那是这一局的第一步），但**不铺场景卡**（玩法设定已进系统提示词，
     *   再摆一条场景气泡就成了剧情旁白）。卡里没写开局引导就什么都不铺，直接等用户开始
     *   —— 用户 2026-09-23 拍板："允许，但不是必须，视角色卡而定"。
     *
     * 落库的是**展开过宏**的成品文本（E2）：聊天页与气泡因此不会显示生宏。
     */
    fun seedOpening(card: CharacterCard?, engine: EngineSpec): List<ChatMessage> {
        if (card == null || !engine.seedsOpening) return emptyList()
        return buildList {
            if (engine.promptMode != PromptMode.PLAY) {
                val scene = card.scenario.trim()
                if (scene.isNotBlank()) add(ChatMessage("scene", expandMacros(scene, card)))
            }
            card.effectiveGreetings().randomOrNull()
                ?.let { add(ChatMessage("assistant", expandMacros(it, card))) }
        }
    }

    /**
     * 玩法会话的**每轮硬性要求**（贴在本轮用户消息末尾，见 `ChatViewModel.tailHint`）。
     *
     * 为什么不是卡里的规则原文：规则是长期约束，已经在系统提示词里；这里放的是**每一条都要满足**
     * 的纪律，而且必须**换掉叙事风格那份硬性要求**——那份会要求"本回合写满 250~500 字"，
     * 与玩法"一次一回合、要短"正好相反。
     *
     * 三条对应二十问那次的三个失败点（报告 §四）：放宽判定 / 把已否掉的答案再报一遍 / 一回合铺太长。
     */
    fun playTurnRequirement(card: CharacterCard?): String = buildString {
        append("【玩法硬性要求（这一条必须满足）】\n")
        append("1. 按卡里的玩法规则走，不因为用户催促或抱怨就放宽判定、改规则或跳过环节。\n")
        append("2. 只推进这一回合：回复要短，不复述规则、不写长段叙述、不做总结。\n")
        append("3. 有候选或可能性时先在内部排除，不要把已经否掉的答案再报一遍。\n")
        val opts = card?.playOptions ?: 0
        if (opts > 0) {
            append("4. 结尾给 ").append(opts).append(" 个编号选项（「1. …」各占一行），彼此有意义地不同。\n")
        }
    }

    /** `{{char}}` / `{{user}}` 宏：卡主的文本里可能带着它们（见 [expandMacros]） */
    private val charMacro = Regex("\\{\\{\\s*char\\s*\\}\\}")
    private val userMacro = Regex("\\{\\{\\s*user\\s*\\}\\}")
    private val botMacro = Regex("<BOT>")

    /** `{{user}}` 在这里展开成什么。我们没有"用户的名字"（本会话设定是一段自由文本），用「你」最自然 */
    const val USER_MACRO_WORD = "你"

    /**
     * 展开角色卡里的宏（E2，2026-09-22）：`{{char}}` → 角色名、`{{user}}` → 「你」、`<BOT>` → 角色名。
     *
     * 为什么要做：这是酒馆系卡的**通用写法**，别人的卡导进来几乎都带宏。不展开的话，
     * 模型会看到字面的 `{{char}}:`（`<START>{{char}}: 早` 是 mes_example 的标配格式），
     * 既浪费 token 又让示例失去示范作用。
     *
     * 只认这三个：`{{original}}`（只对卡自带的 system_prompt 有意义，而我们**不注入**它）、
     * 以及 `{{description}}` / `{{personality}}` 这类自引用宏一概不实现 —— 支持一半比不支持更让人困惑，
     * 遇到不认识的宏原样留着（作者能一眼看出是哪一句没展开）。
     *
     * 用 `replace` 的 lambda 形式而不是字符串替换：**替换文本里的 `$` 不会被当成组引用** ——
     * 卡名里带 `$` 是合法输入，字符串形式会直接抛异常或吃掉字符。
     */
    fun expandMacros(text: String, card: CharacterCard): String {
        if (text.isBlank()) return text
        var out = text
        if (out.contains("{{")) {
            out = userMacro.replace(out) { USER_MACRO_WORD }
            out = charMacro.replace(out) { card.name }
        }
        if (out.contains("<BOT>")) out = botMacro.replace(out) { card.name }
        return out
    }

    /**
     * 该供应商是否接受 temperature 参数 —— 读 [ProviderProfiles]（单一事实来源，
     * 设置界面显示的能力说明与这里保持一致）。Kimi 实测返回 `invalid temperature`，故不发。
     */
    private fun supportsTemperature(
        baseUrl: String,
        custom: List<CustomProvider> = emptyList()
    ): Boolean = ProviderProfiles.resolve(baseUrl, custom)?.temperatureSupported ?: true

    /**
     * temperature 规范化：限制在 0.00–2.00，且**最多两位小数**。
     *
     * 设置页的温度滑杆是 Float，`onValueChange` 里 `it.toDouble()` 会得到
     * `0.8500000238418579` 这类长尾值（Float→Double 的精度残渣），落盘后每次请求都原样发出；
     * 智谱会直接拒绝：「temperature 参数非法：限制小数点[2]位」
     * （2026-09-15 用户实测 GLM-4-Flash-250414 / GLM-4.6V-Flash 均失败）。
     * 这里统一兜底，**老存档里的脏值也能被修正**，不必要求用户重设。
     */
    private fun safeTemperature(v: Double): Double =
        (v.coerceIn(0.0, 2.0) * 100).roundToInt() / 100.0

    /**
     * 旧模型名兜底：静默映射到现行模型，老存档不改也能继续用。
     * - `deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 弃用 → `deepseek-flash`
     * - `doubao-seed-2-1-pro-260628` 已更名为 `doubao-seed-2-1-pro-260915`（火山 2026-09-16 公告，
     *   旧 id 会直接报错）→ 显式映射，避免"设置里存着旧名、聊天就报错"
     */
    private fun normalizeModel(model: String): String = when (model) {
        "deepseek-chat", "deepseek-reasoner" -> "deepseek-flash"
        "doubao-seed-2-1-pro-260628" -> "doubao-seed-2-1-pro-260915"
        else -> model
    }

    /**
     * 思考强度参数映射。mode："off"=关闭 / "high"=深度；空 = 模型默认（不发参数）。
     * 2026-09-14 联网核实的官方映射（来源见各条注释末尾）：
     * - DeepSeek：thinking {"type": disabled/enabled}（默认开思考、effort 默认 high）— api-docs.deepseek.com
     * - 智谱：thinking {"type": disabled/enabled}；**GLM-5.3 / 5.3-FLASH 强制思考，传 disabled 会报错**，
     *   故这两款在"关闭"档改用 reasoning_effort=low 降强度 — docs.bigmodel.cn
     * - Kimi：**K3 总是思考、不接受 thinking/enable_thinking**，只能用顶层 reasoning_effort
     *   （low/high/max，默认 max）— platform.kimi.com
     * - 火山方舟：thinking {"type": disabled/enabled/auto}（disabled 不可与 reasoning_effort 同时传）— volcengine
     * - 通义百炼 / 硅基流动：enable_thinking true/false（硅基流动传 thinking{type} 会报 JSON 错误）— siliconflow
     * - 点点（dots3-note-prev）：chat_template_kwargs {"enable_thinking": bool}，且不传也思考 ⇒ 空 mode 兜底为 off — dots.ai（2026-09-18 核录）
     * - OpenAI：reasoning_effort low/high
     * - Gemini OpenAI 兼容层：reasoning_effort none/high
     */
    private fun thinkingBody(
        baseUrl: String,
        mode: String,
        model: String,
        custom: List<CustomProvider> = emptyList()
    ): Pair<String, kotlinx.serialization.json.JsonElement>? {
        // 供应商用哪套参数由 ProviderProfiles 声明（与设置界面显示的能力说明同源）
        val profile = ProviderProfiles.resolve(baseUrl, custom) ?: return null
        // 点点（dots3-note-prev）不传任何思考参数也会思考（白耗 token 且本项目不显示思考内容）：
        // 空 mode（用户没动过档位）对它兜底为显式关闭——dotsLevels 因此不设"不传参数"档
        val effMode = if (mode.isBlank() && profile.keyword == "askdiandian") "off" else mode
        if (effMode.isBlank()) return null
        val name = model.trim().lowercase()
        // 智谱 GLM-5.3 系强制思考：关闭档只能降强度，传 disabled 会报错
        val glm53ForceOff = profile.keyword == "bigmodel" && "glm-5.3" in name && effMode == "off"
        val thinkingObj = kotlinx.serialization.json.buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive(if (effMode == "off") "disabled" else "enabled"))
        }
        fun prim(v: String) = kotlinx.serialization.json.JsonPrimitive(v)
        return when (profile.thinking) {
            ProviderProfiles.ThinkingKind.NONE -> null
            ProviderProfiles.ThinkingKind.ENABLE_THINKING ->
                "enable_thinking" to kotlinx.serialization.json.JsonPrimitive(effMode == "high")
            // 点点：chat_template_kwargs 包一层（与硅基流动的顶层 enable_thinking 不同）
            ProviderProfiles.ThinkingKind.CHAT_TEMPLATE_ENABLE_THINKING ->
                "chat_template_kwargs" to kotlinx.serialization.json.buildJsonObject {
                    put("enable_thinking", kotlinx.serialization.json.JsonPrimitive(effMode == "high"))
                }
            ProviderProfiles.ThinkingKind.THINKING_TYPE ->
                if (glm53ForceOff) "reasoning_effort" to prim("low") else "thinking" to thinkingObj
            // Kimi：失败时给 low，深度给 max
            ProviderProfiles.ThinkingKind.EFFORT_LOW_HIGH ->
                if (profile.keyword == "moonshot") {
                    "reasoning_effort" to prim(if (effMode == "off") "low" else "max")
                } else {
                    "reasoning_effort" to prim(if (effMode == "high") "high" else "low")
                }
            // Gemini：none 才是关闭
            ProviderProfiles.ThinkingKind.EFFORT_NONE_HIGH ->
                "reasoning_effort" to prim(if (effMode == "high") "high" else "none")
        }
    }

    /**
     * MiniMax 一类的请求字段：显式要求**把思考拆到 `reasoning_content`**（P1-A，2026-09-15）。
     *
     * 官方说明：`reasoning_split` 不控制"要不要思考"，只控制思考返回到哪个字段 ——
     * 不传时思考会被 `<think>…</think>` 包着塞进 `content`，于是跟着正文显示进了聊天气泡。
     * App 不读 `reasoning_content`，拆过去就等于不显示。响应侧另有 [ThinkingFilter] 兜底。
     */
    private fun reasoningSplitBody(
        baseUrl: String,
        custom: List<CustomProvider> = emptyList()
    ): Pair<String, kotlinx.serialization.json.JsonElement>? =
        if (ProviderProfiles.splitsReasoning(baseUrl, custom)) {
            "reasoning_split" to kotlinx.serialization.json.JsonPrimitive(true)
        } else {
            null
        }

    /** 发送一次完整对话（非流式，简单可靠；返回助手回复文本） */
    suspend fun chat(
        settings: AiSettings,
        card: CharacterCard,
        history: List<ChatMessage>,
        userSetting: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (settings.chatBaseUrl.isBlank() || settings.chatApiKey.isBlank() || settings.chatModel.isBlank()) {
            throw AiException("请先在「设置」中填写对话服务的 Base URL、API Key 与模型")
        }
        requireHttps(settings.chatBaseUrl, "对话 Base URL")
        val spec = Engines.of(card)
        val mode = spec.promptMode
        val body = buildJsonObject {
            put("model", normalizeModel(settings.chatModel))
            if (supportsTemperature(settings.chatBaseUrl, settings.customProviders)) put("temperature", safeTemperature(effectiveTemperature(settings, spec)))
            put("max_tokens", ProviderProfiles.effectiveMaxTokens(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.maxTokens, settings.customProviders))
            put("stream", false)
            thinkingBody(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.customProviders)?.let { (k, v) -> put(k, v) }
            reasoningSplitBody(settings.chatBaseUrl, settings.customProviders)?.let { (k, v) -> put(k, v) }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", buildSystemPrompt(card, settings, userSetting, mode = mode))
                }
                // 按条数 + 字符预算裁剪，避免长会话爆上下文
                requestHistory(history, settings, mode).forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", contentOf(m, settings))
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(endpoint(settings.chatBaseUrl, "chat/completions"))
            .header("Authorization", "Bearer ${settings.chatApiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw AiException(errorMessage(text, resp.code))
            try {
                val root = json.parseToJsonElement(text).jsonObject
                val rawContent = root["choices"]?.jsonArray
                    ?.getOrNull(0)?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull
                // P1-A：模型可能把思考包在 <think>…</think> 里混进正文，先剥掉再判断"有没有内容"
                val content = if (rawContent.isNullOrBlank()) null else ThinkingFilter.strip(rawContent)
                if (content.isNullOrBlank()) {
                    // 全被剥光 = 模型只思考没说话，这个提示比"没有返回内容"有用得多
                    if (!rawContent.isNullOrBlank()) {
                        throw AiException(
                            "模型只返回了思考内容、没有输出正文。可在「API 配置」换用不思考的模型，或调低思考强度。",
                            soft = true
                        )
                    }
                    val finish = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("finish_reason")?.jsonPrimitive?.contentOrNull
                    throw AiException("模型没有返回内容（finish_reason=$finish）。可尝试调低 max_tokens 或更换模型。", soft = true)
                }
                content
            } catch (e: AiException) {
                throw e
            } catch (e: Exception) {
                throw AiException("响应解析失败：${text.take(200)}")
            }
        }
    }

    /**
     * 通用非流式对话：给定 system prompt 与消息列表，返回助手文本。
     * 使用 settings.chatModel（调用方要换模型就自己 copy settings）。
     *
     * 调用方：AI 生成角色卡（创作）、前情提要摘要与角色记忆整理（后台任务）。
     * @param thinking 思考档覆盖：null = 跟随「创作思考强度」（角色卡生成要跟随）；
     *   传 "off" = 强制关闭思考——后台摘要/记忆整理是短任务，深度思考只会更慢更贵。
     * @param readTimeoutSeconds 读超时覆盖：默认 [DEFAULT_READ_TIMEOUT_SECONDS]（对话那档）。
     *   创作整卡要传 [CREATION_READ_TIMEOUT_SECONDS]——见那个常量的说明。
     */
    suspend fun chatCompletion(
        settings: AiSettings,
        systemPrompt: String,
        messages: List<ChatMessage>,
        thinking: String? = null,
        readTimeoutSeconds: Int = DEFAULT_READ_TIMEOUT_SECONDS
    ): String = withContext(Dispatchers.IO) {
        if (settings.chatBaseUrl.isBlank() || settings.chatApiKey.isBlank() || settings.chatModel.isBlank()) {
            throw AiException("请先在「设置」中填写对话服务的 Base URL、API Key 与模型")
        }
        requireHttps(settings.chatBaseUrl, "对话 Base URL")
        val mode = thinking ?: settings.creationThinking
        val body = buildJsonObject {
            put("model", normalizeModel(settings.chatModel))
            if (supportsTemperature(settings.chatBaseUrl, settings.customProviders)) put("temperature", safeTemperature(settings.temperature))
            put("max_tokens", ProviderProfiles.effectiveMaxTokens(settings.chatBaseUrl, mode, settings.chatModel, settings.maxTokens, settings.customProviders))
            put("stream", false)
            thinkingBody(settings.chatBaseUrl, mode, settings.chatModel, settings.customProviders)?.let { (k, v) -> put(k, v) }
            reasoningSplitBody(settings.chatBaseUrl, settings.customProviders)?.let { (k, v) -> put(k, v) }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                messages.filter { it.role != "scene" }.forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", contentOf(m, settings))
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(endpoint(settings.chatBaseUrl, "chat/completions"))
            .header("Authorization", "Bearer ${settings.chatApiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        // 超时按调用方那一档走：创作整卡要 5 分钟，其余沿用全局客户端（省掉一次多余的对象构建）
        val client = if (readTimeoutSeconds == DEFAULT_READ_TIMEOUT_SECONDS) http
        else http.newBuilder().readTimeout(readTimeoutSeconds.toLong(), TimeUnit.SECONDS).build()

        try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw AiException(errorMessage(text, resp.code))
                try {
                    val content = ThinkingFilter.strip(
                        json.parseToJsonElement(text).jsonObject["choices"]
                            ?.jsonArray?.getOrNull(0)?.jsonObject
                            ?.get("message")?.jsonObject
                            ?.get("content")?.jsonPrimitive?.contentOrNull
                    )
                    if (content.isNullOrBlank()) throw AiException("模型没有返回内容")
                    content
                } catch (e: AiException) {
                    throw e
                } catch (e: Exception) {
                    throw AiException("响应解析失败：${text.take(200)}")
                }
            }
        } catch (e: java.io.InterruptedIOException) {
            // 读超时/连接超时：OkHttp 抛的是消息为 "timeout" 的 IOException，直接抛给用户看不懂
            // （桌面端那句光秃秃的 "timeout" 就是从这里出去的，2026-09-23 反馈）
            throw AiException(
                "请求超时（模型响应太慢）。可稍后重试，或在设置里把创作模型换成更快的型号、" +
                    "把创作思考强度从「深度」调低。"
            )
        } catch (e: java.io.IOException) {
            throw AiException("网络请求失败：${e.message ?: "连接异常"}")
        }
    }

    /** 上下文字符预算：超过即从最早的消息开始丢弃（24000 字 ≈ 1.6 万 token，防长会话爆上下文） */
    private const val CONTEXT_CHAR_BUDGET = 24000

    /** 单张随消息图片计入预算的上限（见 [weightOf]）：约等于 4.5KB 图片的 base64 长度 */
    private const val IMAGE_WEIGHT_CAP = 6000

    /** 携带历史：先按条数截断，再按字符预算从最早丢弃（scene 卡不参与）。公开供 VM 计算摘要范围 */
    /**
     * 一条消息在请求里的"字符当量"：正文 + 随消息图片的 base64 体积。
     *
     * P2-E6（2026-09-15）：原来只算 `content.length`，而图片到 `contentOf` 才展开成
     * `data:image/jpeg;base64,…`，**一张图就有几万～几十万字符却完全不计入预算**，
     * 于是「上下文字符预算」在视觉会话里形同虚设。
     *
     * 单张图最多计 [IMAGE_WEIGHT_CAP]：如实按真实值算的话，一张普通手机照片就能把整段文字历史一次挤光，
     * 那属于"把预算从一个极端推到另一个极端"。这里只求消除"图片零成本"这个漏洞。
     */
    private fun weightOf(m: ChatMessage): Int {
        val bytes = m.imageUri?.takeIf { it.isNotBlank() }
            ?.let { runCatching { java.io.File(it).length() }.getOrDefault(0L) } ?: 0L
        val imageWeight = (bytes * 4 / 3).coerceAtMost(IMAGE_WEIGHT_CAP.toLong()).toInt()
        return m.content.length + imageWeight
    }

    /**
     * [pinFirstUserMessage]＝把**首条用户消息钉住**、不参与裁剪（工具形态，E4）。
     *
     * 这条不是"顺手加的开关"：裁剪是**从最早一条开始丢**的，而工具会话的第一条用户消息往往
     * 就是那份要处理的**长素材**——一旦超预算，先被丢掉的恰好是任务本身，模型于是对着空气输出
     * （台账 §5.1 修订 1，代码里早就存在这个坑，是 E4 才踩到）。钉住的那条**不占预算**：
     * 它必须留下，所以预算只能花在其余消息上。
     */
    fun trimHistory(
        history: List<ChatMessage>,
        historyLimit: Int,
        pinFirstUserMessage: Boolean = false
    ): List<ChatMessage> {
        // 先摘出来再 takeLast：**条数上限也会把它挤掉**（41 条历史、上限 40 时，takeLast 丢的正是第一条），
        // 所以钉住必须发生在取最近 N 条**之前**，否则"钉住"只是句空话。
        val pinned = if (pinFirstUserMessage) history.firstOrNull { it.role == "user" } else null
        val recent = (if (pinned == null) history else history.filterNot { it === pinned })
            .takeLast(historyLimit).filter { it.role != "scene" }
        val rest = recent
        var total = rest.sumOf { weightOf(it) }
        if (total <= CONTEXT_CHAR_BUDGET) return if (pinned == null) recent else listOf(pinned) + recent
        var drop = 0
        while (drop < rest.size && total > CONTEXT_CHAR_BUDGET) {
            total -= weightOf(rest[drop])
            drop++
        }
        val kept = rest.drop(drop)
        return if (pinned == null) kept else listOf(pinned) + kept
    }

    /**
     * 这条素材**自己**就超了单轮预算——钉住它仍然成立，但上下文里其余消息会被挤光，
     * 值得明确告诉用户（"不静默截断"，台账 §5.1 修订 1 ③）。
     */
    fun exceedsContextBudget(text: String): Boolean = text.length > CONTEXT_CHAR_BUDGET

    /**
     * 只保留**最近一张**图，其余带图消息退化成纯文本（2026-09-16）。
     *
     * 给"一次只吃一张图"的模型用（[ModelCatalog.singleImageOnly]）：不这么做的话，
     * 每轮请求都会把历史里**每一张**图重新 base64 上传一遍——用户只是多聊几句，
     * 请求体就涨到几 MB，而对方模型本来也只能看一张。
     */
    private fun collapseToLatestImage(history: List<ChatMessage>): List<ChatMessage> {
        val lastImageAt = history.indexOfLast { !it.imageUri.isNullOrBlank() }
        if (lastImageAt < 0) return history
        return history.mapIndexed { i, m ->
            if (i == lastImageAt || m.imageUri.isNullOrBlank()) m else m.copy(imageUri = null)
        }
    }

    /** 该请求实际要发的历史：按条数/预算裁剪 + 单图模型只留最近一张图 */
    private fun requestHistory(
        history: List<ChatMessage>,
        settings: AiSettings,
        mode: PromptMode = PromptMode.RP
    ): List<ChatMessage> {
        // 工具形态钉住首条素材（E4）：见 trimHistory 的说明
        val trimmed = trimHistory(history, settings.historyLimit, pinFirstUserMessage = mode == PromptMode.TOOL)
        return if (ModelCatalog.singleImageOnly(settings.chatModel)) collapseToLatestImage(trimmed) else trimmed
    }

    /**
     * 生效温度：引擎级默认**只在用户没动过全局温度**（仍是出厂值）时接管。
     * 用户自己调过就一律听用户的——不拿引擎偏好去覆盖人的显式选择（台账 §5.5 第 4 条）。
     */
    private fun effectiveTemperature(settings: AiSettings, spec: EngineSpec): Double {
        val engineDefault = spec.defaultTemperature ?: return settings.temperature
        return if (settings.temperature == AiSettings().temperature) engineDefault else settings.temperature
    }

    /**
     * 把一条消息转成 OpenAI content：纯文本 → 字符串；带图 → 数组（text + image_url data URL）。
     * 图片仅随用户消息出现，且只在视觉模型会话里产生。
     *
     * [settings] 用来判**当前**模型能不能看图。不能看时只发文字：
     * 图照发会被对方静默忽略，用户看到的是"模型答非所问"，甚至"我记得你发的是一张空白图"
     * ——2026-09-16 用户实测踩到（换了模型但界面还留着发图按钮时），而且白烧一份 base64 的输入 token。
     *
     * 图片读取失败时同样退化成纯文本：以前这条是静默的，宁可少发一张图，也不发坏数据。
     */
    private fun contentOf(m: ChatMessage, settings: AiSettings): kotlinx.serialization.json.JsonElement {
        val uri = m.imageUri
        if (uri.isNullOrBlank()) return kotlinx.serialization.json.JsonPrimitive(m.content)
        if (!ModelCatalog.isVisionModel(settings.chatModel)) {
            return kotlinx.serialization.json.JsonPrimitive(
                m.content.ifBlank { "（用户发来一张图片，但当前模型不支持看图）" }
            )
        }
        val b64 = runCatching {
            java.util.Base64.getEncoder().encodeToString(java.io.File(uri).readBytes())
        }.getOrNull()
        if (b64 == null) {
            // 读失败会静默退化成"纯文本消息"，界面上完全看不出来——留一行日志便于事后对账
            WhaleLog.w("WhaleVision", "图片读取失败，本条按纯文本发送：$uri")
            return kotlinx.serialization.json.JsonPrimitive(m.content)
        }
        // 给"模型说没看见图"这类反馈留凭据：能证明图确实随请求发出去了、有多大
        WhaleLog.i("WhaleVision", "随消息发送图片：${b64.length} 字符 base64（${m.role}）")
        val ext = uri.substringAfterLast('.', "jpg").lowercase()
        val mime = if (ext == "png") "image/png" else "image/jpeg"
        return kotlinx.serialization.json.buildJsonArray {
            addJsonObject {
                put("type", "text")
                put("text", m.content.ifBlank { "（用户发来一张图片）" })
            }
            addJsonObject {
                put("type", "image_url")
                put("image_url", kotlinx.serialization.json.buildJsonObject {
                    put("url", "data:$mime;base64,$b64")
                })
            }
        }
    }

    /**
     * 参考图转译：用视觉模型把用户上传的参考图 + 生图意图，转写成一段详细的画图提示词。
     * 返回的提示词直接喂给 /images/generations。
     */
    suspend fun visionText(settings: AiSettings, userIntent: String, imageUri: String): String =
        withContext(Dispatchers.IO) {
            val model = settings.chatModel
            if (!ModelCatalog.isVisionModel(model)) {
                throw AiException("当前对话模型不支持看图，请在设置里换一个带 👁 标记的模型")
            }
            val b64 = java.io.File(imageUri).readBytes().let {
                java.util.Base64.getEncoder().encodeToString(it)
            }
            val ext = imageUri.substringAfterLast('.', "jpg").lowercase()
            val mime = if (ext == "png") "image/png" else "image/jpeg"
            val sys = "你是画图提示词专家。用户会给你一张参考图和生图意图，请输出一段可直接用于文生图模型的详细中文提示词：" +
                "描述画风、主体特征（外貌/服饰/表情/姿势）、构图与光影。只输出提示词本身，不要解释。"
            val body = buildJsonObject {
                put("model", normalizeModel(model))
                put("max_tokens", 1024)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", sys)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", buildJsonArray {
                            addJsonObject {
                                put("type", "text")
                                put("text", "生图意图：${userIntent.ifBlank { "生成一张与参考图同风格同人物的图" }}")
                            }
                            addJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", "data:$mime;base64,$b64") })
                            }
                        })
                    }
                }
            }
            val request = Request.Builder()
                .url(endpoint(settings.chatBaseUrl, "chat/completions"))
                .header("Authorization", "Bearer ${settings.chatApiKey}")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw AiException(errorMessage(text, resp.code))
                val raw = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray
                    ?.getOrNull(0)?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull
                    ?: throw AiException("参考图转译失败：模型没有返回内容")
                ThinkingFilter.strip(raw)
            }.trim()
        }

    /**
     * 灵感回复（0.1.2）：用户"不会接话"时，站在**用户**立场给几条可以直接发出去的话。
     *
     * 复用角色卡的资料块（人设 / 世界观 / 用户设定 / 前情 / 记忆），建议才和当前剧情对得上。
     * 返回条数不固定：解析出几条就返回几条，全部解析失败时抛异常由调用方提示。
     *
     * **2026-09-16 第 2 轮（真 key 实测，同一段真实历史各 8 次）——两处结构性改动**：
     *
     * ① **本次调用不再"扮演角色"**（`buildSystemPrompt(mode = PromptMode.SUGGEST)`）。原来的做法是沿用
     *    角色扮演的系统提示词、再在**末尾**加一句"请暂时放下角色扮演（本任务优先于上面的【视角约定】）"——
     *    这与开头的"你＝角色、始终以角色第一人称回应"正面对撞，模型时不时就把**角色的道具/伤势安到用户身上**
     *    （实测出一条"我攥着碎石砸向鬼修后颈"，而碎石正是角色上一条刚扔出去的）。现在从源头去掉矛盾：
     *    同一份资料、换成"你在替用户代笔"的口径，不再要求它当任何人。
     * ② **任务段（含"必须恰好 3 条"）从系统提示词末尾挪到末尾的用户消息里**：实测两者 3 条达成率接近
     *    （12/13 对 18/20），但身份框定更稳，且用户消息里的要求不会被前面的剧本口吻稀释。
     *    另外**不能**只挪任务段而保留角色扮演的系统提示词：那种组合实测 6 次里 4 次直接续写剧情
     *    （系统提示词说"你是角色"，最靠近生成点的地方又摆着"该用户说话了"，模型就去演了）。
     */
    suspend fun suggestReplies(
        settings: AiSettings,
        card: CharacterCard,
        history: List<ChatMessage>,
        userSetting: String = "",
        memory: String = "",
        summary: String = "",
        count: Int = 3
    ): List<String> = withContext(Dispatchers.IO) {
        if (settings.chatBaseUrl.isBlank() || settings.chatApiKey.isBlank() || settings.chatModel.isBlank()) {
            throw AiException("请先在「设置」中填写对话服务的 Base URL、API Key 与模型")
        }
        requireHttps(settings.chatBaseUrl, "对话 Base URL")

        // ⚠️ 刻意**不传 styleHint**（0.1.3 P-F1 之后的口径）：叙事风格轴（尤其 强导演 / 第三人称贴身 /
        // 文学书面 / 丰沛）全是写给"角色"的，套到"用户该说什么"上会把灵感回复带跑成角色台词，
        // 也是角色/用户混淆的一个来源。灵感回复的风格参照改成**用户自己的历史发言**（见任务段第 2 条）。
        val sys = buildSystemPrompt(
            card, settings, userSetting, memory = memory, summary = summary, mode = PromptMode.SUGGEST
        )
        val body = buildJsonObject {
            put("model", normalizeModel(settings.chatModel))
            if (supportsTemperature(settings.chatBaseUrl, settings.customProviders)) put("temperature", safeTemperature(settings.temperature))
            // 灵感回复属于对话辅助：用对话模型 + 对话思考强度（与聊天本身保持一致）
            put("max_tokens", ProviderProfiles.effectiveMaxTokens(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.maxTokens, settings.customProviders))
            put("stream", false)
            thinkingBody(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.customProviders)?.let { (k, v) -> put(k, v) }
            reasoningSplitBody(settings.chatBaseUrl, settings.customProviders)?.let { (k, v) -> put(k, v) }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", sys)
                }
                // 只带最近若干轮：灵感回复看重"当前情境"，历史太长反而稀释
                // （工具形态下这条路径整个关掉，见 Engines.TOOL.inspiration；这里传 SUGGEST 即"不钉素材"）
                requestHistory(history, settings, PromptMode.SUGGEST).forEach { m ->
                    addJsonObject {
                        put("role", m.role)
                        put("content", contentOf(m, settings))
                    }
                }
                // 任务段放**最末的用户消息**（近因最强）：见 suggestReplies 的注释②
                addJsonObject {
                    put("role", "user")
                    put("content", suggestionTask(card.name, count))
                }
            }
        }
        // 请求 + 解析抽成局部函数：模型方差偶尔只回 1 条建议时可以自动重试一次
        suspend fun callOnce(): List<String> {
            val request = Request.Builder()
                .url(endpoint(settings.chatBaseUrl, "chat/completions"))
                .header("Authorization", "Bearer ${settings.chatApiKey}")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val raw = http.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) throw AiException(errorMessage(text, resp.code))
                val body = json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray
                    ?.getOrNull(0)?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("content")?.jsonPrimitive?.contentOrNull
                    ?: throw AiException("模型没有返回内容")
                // P1-A：思考混在前面会把下面的 JSON 解析直接打挂，先剥掉
                ThinkingFilter.strip(body)
            }
            return parseSuggestionList(raw).take(count)
        }
        var list = callOnce()
        // 角色扮演模型偶尔无视"3 条"只回 1 条。**只补一次、且只在真的太少（0~1 条）时补**：
        // 用户口径（2026-09-16）"偶发少于三条就认了，不要过多浪费 token"——所以 2 条照收，
        // 不为凑数再发一次请求。合并去重（而不是整批替换）是为了"这次 1 条 + 补到 2 条"不白花。
        if (list.size < 2) {
            val more = runCatching { callOnce() }.getOrNull()
            if (more != null) list = (list + more).distinct().take(count)
        }
        if (list.isEmpty()) throw AiException("没有解析出可用的回复建议，可再试一次")
        list
    }

    /**
     * 灵感回复的任务段 —— 放在**最后一条用户消息**里（不是系统提示词）。
     *
     * 措辞要点都是踩过坑的：写清"你输出的是用户的话"（否则角色/用户混淆）、
     * 点名"照抄用户自己上一条发言的写法"（否则跟着剧本口吻写成文学腔）、
     * "必须恰好 N 条"（模型少给就直接少了）、"不要输出空的（）"与"不要连成一段"
     * （实测的两种坏形态，见 [parseSuggestionList]）。
     */
    internal fun suggestionTask(name: String, count: Int): String = buildString {
        append("【本次任务：替「用户」写 ").append(count).append(" 条可以直接发出去的消息】\n")
        append("现在轮到用户说话，他一时想不出怎么接——请你替**用户**代笔。\n")
        append("你不是").append(name).append("，也不需要回复任何人：")
        append("**禁止**出现").append(name).append("的台词、动作、神态或心理，")
        append("不要描写场景、不要替").append(name).append("推进剧情。\n")
        append("1. 写的是**用户**要说的话 / 要做的举动（「我」= 用户本人，").append(name)
        append("的事不要写进来）；\n")
        append("2. **语言风格照抄用户自己上一条发言**（用词、句子长短、语气、是否用（）写动作）；")
        append("看不出风格就用自然平实的口语，不要文学化、不要堆修辞；\n")
        append("3. **必须恰好 ").append(count).append(" 条**，每条 20–50 字，切入角度明显不同")
        append("（例如：推进剧情的行动、情感上的正面回应、抛出新问题引导、轻松俏皮的玩笑、试探性的反问……）；\n")
        append("4. 符合当前剧情进度与用户的设定，不要凭空引入无关背景；\n")
        append("5. 排版与聊天一致：对白直接写文本，动作神态用（）括注且括号里必须有内容，")
        append("**不要输出空的（）**，几条之间也不要连成一段；\n")
        append("只输出 JSON 字符串数组，例如 [\"第一条\",\"第二条\",\"第三条\"]，不要任何解释或 markdown 代码块。")
    }

    /**
     * 宽松解析模型返回的建议列表：优先按 JSON 字符串数组解析；
     * 模型经常无视要求套代码块或写成编号列表，所以依次回退到"行解析"。
     *
     * 角色扮演模型（doubao-seed-character 等）实测还有两种坏形态（2026-09-15 用户截图）：
     * ① 无视 JSON 要求，把 3 条建议**连成一段**输出 → 行解析只得 1 条；
     * ② 模仿"动作神态用（）括注"时吐出**空的「（）」**散落在句间。
     * 对策：每条先过 [cleanupSuggestion] 清洗；若清洗后只剩 1 条且足够长，
     * 用 [splitMergedSuggestion] 按「句末标点 + 下一个动作括注」的边界拆开。
     */
    internal fun parseSuggestionList(raw: String): List<String> {
        val text = raw.trim().removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        // ① 标准 JSON 数组（允许前后有多余文字）
        runCatching {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start >= 0 && end > start) {
                val arr = json.parseToJsonElement(text.substring(start, end + 1)).jsonArray
                val items = arr.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                    .filter { it.isNotBlank() }
                if (items.isNotEmpty()) return finalizeSuggestions(items)
            }
        }
        // ② 行解析：去掉编号 / 项目符号 / 包裹引号
        val byLines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") }
            .map { line ->
                line.removePrefix("- ").removePrefix("* ")
                    .replace(Regex("^\\d+[.、)]\\s*"), "")
                    .trim()
                    .trim('"', '“', '”', '\'', '[', ']')
                    .trim()
            }
            .filter { it.isNotBlank() }
        return finalizeSuggestions(byLines)
    }

    /** 清洗单条建议：去掉模型偶尔吐出的空括注（）/()、把连出的括号压掉、压缩空白与包裹引号 */
    private fun cleanupSuggestion(s: String): String =
        s.replace("（）", "")
            .replace("()", "")
            // 实测（2026-09-16）：偶发退化输出会连吐上百个「（」——先压成一个，交给 looksBroken 判死
            .replace(Regex("[（(]{2,}"), "（")
            .replace(Regex("[）)]{2,}"), "）")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('"', '“', '”', '\'', '「', '」')
            .trim()

    /**
     * 明显的坏输出，直接丢掉（2026-09-16 实测到的退化形态：一条里连出上百个「（」，长度五百多字）。
     * 判据只取"客观离谱"的两条，避免误杀 —— 要求是 20~50 字，但不拦"只写了动作没写台词"这种合法建议，
     * 也不拦偶尔写长了的正常建议（300 字以上才算离谱）。
     */
    private fun looksBroken(s: String): Boolean {
        if (s.length > 300) return true                 // 远超"一条能发出去的话"
        return Regex("(.)\\1{3,}").containsMatchIn(s)   // 同一字符连出 4 次以上
    }

    /**
     * 把"连成一段"的建议拆开：每条建议都以动作括注（开头），
     * 所以「句末标点后紧跟（」就是条与条的边界。切分用**后行 + 前行双零宽断言**，
     * 标点和括号都留在原文里（此前用字符类匹配会把"？"吃掉）。
     * 只在"只有 1 条且 ≥40 字"时尝试，且要求拆出 ≥2 段、其中 ≥2 段像样（≥6 字），
     * 避免把"跑吗？（拉你）快走。"这种本来就短的正常单条误拆。
     */
    private fun splitMergedSuggestion(item: String): List<String>? {
        if (item.length < 40) return null
        val parts = Regex("""(?<=[？?!！。])\s*(?=（)""").split(item)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val meaningful = parts.count { it.length >= 6 }
        return if (parts.size >= 2 && meaningful >= 2) parts else null
    }

    /** 解析收尾：统一清洗 + 丢坏输出 + 去重；只剩一条且像"三条连写"时尝试拆开 */
    private fun finalizeSuggestions(items: List<String>): List<String> {
        val cleaned = items.map(::cleanupSuggestion)
            .filter { it.isNotBlank() && !looksBroken(it) }
            .distinct()
        if (cleaned.size == 1) return splitMergedSuggestion(cleaned[0]) ?: cleaned
        return cleaned
    }

    /**
     * 自动扩写生图提示词（0.1.2）：把用户一句话描述扩写成可直接喂给文生图模型的详细中文提示词。
     * 产出结构与 [visionText] 对齐（画风 / 主体 / 服饰表情姿势 / 构图光影），两者可互相替代。
     *
     * 走**创作模型**（[draftImagePrompt] 同）：扩写属于创作类任务，与「AI 生成角色卡」同类，
     * 也遵循「创作思考强度」。创作模型留空时回落到对话模型（与 CharacterGenerator 一致）。
     */
    suspend fun expandImagePrompt(settings: AiSettings, brief: String): String =
        askCreationModel(
            settings,
            system = "你是画图提示词专家。用户会给你一句简短的画面描述，请把它扩写成一段可直接用于" +
                "文生图模型的详细中文提示词：依次覆盖画风、主体特征（外貌 / 服饰 / 表情 / 姿势）、" +
                "构图与镜头、光影与氛围、画质。只输出提示词本身（一段连续文字，不要分行、不要编号、不要解释）。",
            user = brief.trim()
        )

    /**
     * 按角色卡设定草拟头像 / 背景图提示词（0.1.2，角色编辑页用）。
     *
     * @param kind "avatar" = 头像立绘（突出人物外貌气质）/"background" = 聊天背景（场景氛围，可无人物）
     * @param landscape 背景分端（A 批次）：桌面的聊天背景是给**横向窗口**用的，提示词里的构图方向
     *   得跟着变，否则起草出来是一张竖图、再拿去填横窗口。头像忽略本参数。
     * 走创作模型 + 创作思考强度；产出同样是一段可直接出图的中文提示词。
     */
    suspend fun draftImagePrompt(
        settings: AiSettings,
        kind: String,
        card: CharacterCard,
        landscape: Boolean = false
    ): String {
        val facts = buildString {
            append("角色名：").append(card.name.ifBlank { "（未填）" }).append("\n")
            if (card.tagline.isNotBlank()) append("一句话简介：").append(card.tagline.trim()).append("\n")
            if (card.persona.isNotBlank()) append("人设 / 性格 / 背景：").append(card.persona.trim()).append("\n")
            if (card.scenario.isNotBlank()) append("世界观 / 当前场景：").append(card.scenario.trim()).append("\n")
        }
        val want = if (kind == "avatar") {
            "请据此写一段用于生成**人物头像立绘**的中文提示词：突出外貌、发型发色、服饰、神态与气质，" +
                "半身像或胸像构图，适合裁成圆形头像；不要出现文字、水印或多人。"
        } else {
            "请据此写一段用于生成**聊天背景图**的中文提示词：描绘与该角色设定相符的场景与氛围，" +
                "${if (landscape) "横屏" else "竖屏"}构图，可只画环境不出现人物；不要出现文字或水印。"
        }
        return askCreationModel(
            settings,
            system = "你是画图提示词专家。用户会给你一张角色卡的设定信息，请把它转写成一段可直接用于" +
                "文生图模型的详细中文提示词：依次覆盖画风、主体特征、构图与镜头、光影与氛围、画质。" +
                "只输出提示词本身（一段连续文字，不要分行、不要编号、不要解释）。",
            user = facts + "\n" + want
        )
    }

    /** 创作类调用的公共实现：**创作供应商**的 URL / Key + 创作模型 + 创作思考强度 + 每轮现读设置 */
    private suspend fun askCreationModel(settingsIn: AiSettings, system: String, user: String): String =
        withContext(Dispatchers.IO) {
            // 换成创作供应商（三个模型页各自独立选供应商；留空则沿用对话那家）
            val settings = settingsIn.forCreation()
            if (settings.chatBaseUrl.isBlank() || settings.chatApiKey.isBlank()) {
                throw AiException("请先在「设置」中填写创作服务的 Base URL 与 API Key")
            }
            requireHttps(settings.chatBaseUrl, "创作 Base URL")
            // forCreation() 已把 chatModel 换成"创作模型留空则回落对话模型"，这里直接读
            val model = settings.chatModel
            if (model.isBlank()) throw AiException("请先在「设置」中配置创作模型")
            if (user.isBlank()) throw AiException("内容为空，先填点信息再让 AI 写")

            val body = buildJsonObject {
                put("model", normalizeModel(model))
                // 起草/扩写是"短任务"：实测创作模型（v4-pro）在「深度」思考下会超过 120s 读超时，
                // 用户等不起。这里固定关闭思考——模型仍用创作模型，但不让推理拖慢出结果。
                // 注意：Kimi K3 / GLM-5.3 这类"总是思考"的模型关不掉，预算仍要走抬高逻辑
                put("max_tokens", ProviderProfiles.effectiveMaxTokens(settings.chatBaseUrl, "off", model, settings.maxTokens, settings.customProviders))
                put("stream", false)
                thinkingBody(settings.chatBaseUrl, "off", model, settings.customProviders)?.let { (k, v) -> put(k, v) }
                reasoningSplitBody(settings.chatBaseUrl, settings.customProviders)?.let { (k, v) -> put(k, v) }
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", user)
                    }
                }
            }
            val request = Request.Builder()
                .url(endpoint(settings.chatBaseUrl, "chat/completions"))
                .header("Authorization", "Bearer ${settings.chatApiKey}")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val content = try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) throw AiException(errorMessage(text, resp.code))
                    ThinkingFilter.strip(
                        json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray
                            ?.getOrNull(0)?.jsonObject
                            ?.get("message")?.jsonObject
                            ?.get("content")?.jsonPrimitive?.contentOrNull
                            ?: throw AiException("模型没有返回内容")
                    )
                }
            } catch (e: java.io.InterruptedIOException) {
                // 读超时/连接超时：OkHttp 抛的是消息为 "timeout" 的 IOException，直接抛给用户看不懂
                throw AiException("请求超时（模型响应太慢）。可稍后重试，或在设置里把创作模型换成更快的型号。")
            } catch (e: java.io.IOException) {
                throw AiException("网络请求失败：${e.message ?: "连接异常"}")
            }
            // 模型偶尔仍会分行/加编号，压成一行再返回
            val cleaned = content.trim().lines().map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("")
                .replace(Regex("^\\d+[.、)]\\s*"), "")
                .trim()
            // 空正文当失败处理：不能让上层拿到空串后"静默什么都不做"
            if (cleaned.isBlank()) {
                throw AiException("模型没有返回内容（若创作思考强度为「深度」，可试着调低或更换创作模型）")
            }
            cleaned
        }

    /**
     * 把本轮的「硬性要求 / 特别指示」追加到最后一条用户消息末尾。
     *
     * 内容可能是纯文本，也可能是「文字 + 图片」的数组（视觉会话），两种都要照顾到。
     */
    private fun withTailHint(
        content: kotlinx.serialization.json.JsonElement,
        hint: String
    ): kotlinx.serialization.json.JsonElement {
        val arr = content as? kotlinx.serialization.json.JsonArray
            ?: return kotlinx.serialization.json.JsonPrimitive(
                (content as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty() +
                    "\n\n" + hint
            )
        return kotlinx.serialization.json.buildJsonArray {
            var done = false
            arr.forEach { el ->
                val type = el.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                if (!done && type == "text") {
                    done = true
                    addJsonObject {
                        put("type", "text")
                        put(
                            "text",
                            (el.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "") + "\n\n" + hint
                        )
                    }
                } else add(el)
            }
            if (!done) addJsonObject { put("type", "text"); put("text", hint) }
        }
    }

    /**
     * 流式对话（SSE）：每收到一个增量就回调 onDelta；返回完整回复文本。
     * [onCall] 用于请求生命周期管理（调用方可持有 Call 在离开页面时 cancel）。
     * 429/5xx/网络错误指数退避重试（最多 2 次，尊重 Retry-After）。
     *
     * [styleHint] 进系统提示词（整段风格约定，长期背景）；
     * [tailHint] 贴到最后一条用户消息末尾（本轮的硬性要求 / 单条特别指示）。
     * 两者分家的理由见 [withTailHint] 与 `NarrativeStyles.hardReminder` 的注释：实测同样的文字
     * 放系统提示词里几乎无效（它后面还压着几十条历史），贴近生成点才真正被遵循。
     */
    suspend fun chatStream(
        settings: AiSettings,
        card: CharacterCard,
        history: List<ChatMessage>,
        userSetting: String = "",
        styleHint: String = "",
        tailHint: String = "",
        memory: String = "",
        summary: String = "",
        onDelta: (String) -> Unit,
        onCall: (okhttp3.Call) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (settings.chatBaseUrl.isBlank() || settings.chatApiKey.isBlank() || settings.chatModel.isBlank()) {
            throw AiException("请先在「设置」中填写对话服务的 Base URL、API Key 与模型")
        }
        requireHttps(settings.chatBaseUrl, "对话 Base URL")
        // 装配契约由卡的形态决定（E4）：工具卡走 TOOL，陪伴/多线走 RP；风格与历史口径跟着一起分叉
        val spec = Engines.of(card)
        val mode = spec.promptMode
        val body = buildJsonObject {
            put("model", normalizeModel(settings.chatModel))
            if (supportsTemperature(settings.chatBaseUrl, settings.customProviders)) put("temperature", safeTemperature(effectiveTemperature(settings, spec)))
            put("max_tokens", ProviderProfiles.effectiveMaxTokens(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.maxTokens, settings.customProviders))
            put("stream", true)
            thinkingBody(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.customProviders)?.let { (k, v) -> put(k, v) }
            reasoningSplitBody(settings.chatBaseUrl, settings.customProviders)?.let { (k, v) -> put(k, v) }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", buildSystemPrompt(card, settings, userSetting, styleHint, memory, summary, mode))
                }
                val sent = requestHistory(history, settings, mode)
                val hintAt = if (tailHint.isNotBlank()) sent.indexOfLast { it.role == "user" } else -1
                sent.forEachIndexed { i, m ->
                    addJsonObject {
                        put("role", m.role)
                        val c = contentOf(m, settings)
                        put("content", if (i == hintAt) withTailHint(c, tailHint) else c)
                    }
                }
                // 历史里没有用户消息（例如对最后一条 assistant 重发）：退化成最末一条 system
                if (tailHint.isNotBlank() && hintAt < 0) {
                    addJsonObject {
                        put("role", "system")
                        put("content", tailHint)
                    }
                }
            }
        }
        val url = endpoint(settings.chatBaseUrl, "chat/completions")

        var attempt = 0
        while (true) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${settings.chatApiKey}")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val call = http.newCall(request)
            onCall(call)
            val resp = try {
                call.execute()
            } catch (e: IOException) {
                if (call.isCanceled()) throw AiException("请求已取消")
                if (attempt < 2) { attempt++; delay(1000L * attempt); continue }
                throw AiException("网络请求失败：${e.message ?: "连接异常"}")
            }

            // 非 2xx：先把（很小的）错误体读完再决定重试还是报错。
            // ⚠️ 必须在开始消费流之前判断——真流式一旦读起来就没法回头重试了。
            if (!resp.isSuccessful) {
                val code = resp.code
                val retryAfter = resp.header("Retry-After")?.toLongOrNull()
                val errText = resp.use { r -> runCatching { r.body?.string() }.getOrNull() ?: "" }
                if ((code == 429 || code >= 500) && attempt < 2) {
                    attempt++
                    delay(retryAfter?.times(1000) ?: 1000L * attempt)
                    continue
                }
                throw AiException(errorMessage(errText, code))
            }

            // ---- 真流式：边到边回调（P1-3，2026-09-15）----
            // 以前是 `resp.body.string()` 把整段读完、再解析 SSE 里的增量：
            // 网络其实早就吐完了，界面还在按打字机速率慢放，首字延迟 ≈ 整段生成时间。
            // 现在每读到一行就回调 onDelta —— 模型吐第一个 token 时首字就出现。
            val full = StringBuilder()
            var sawSse = false
            // P1-A：思考块（<think>…</think>）既不进界面、也不进聊天记录。
            // 必须逐 delta 过滤：标记会被切成两半跨 delta 到达，整段正则两头都漏（见 ThinkingFilter）
            val thinking = ThinkingFilter()
            var rawSeen = false
            try {
                resp.use { r ->
                    val source = r.body?.source() ?: throw AiException("模型没有返回内容（响应为空）")
                    while (true) {
                        // 阻塞到这一行到齐或流结束：这是真流式与"整段读"的唯一区别
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        if (!line.startsWith("data:")) {
                            // 兜底：有的服务商不吃 stream=true，整段回一个普通 JSON。
                            // 只认"首行就是 JSON"这种形态（`event:`/`id:`/注释行继续跳过），
                            // 否则会把本来能用的配置改坏。
                            if (!sawSse && (line.startsWith("{") || line.startsWith("["))) {
                                val whole = line + "\n" + source.readUtf8()
                                val content = extractAssistantContent(whole)
                                    ?: throw AiException("模型没有返回内容（响应既不是 SSE 也不是标准 JSON）")
                                if (content.isNotBlank()) onDelta(content)
                                return@withContext content
                            }
                            continue
                        }
                        sawSse = true
                        val data = line.substring(5).trim()
                        if (data == "[DONE]") break
                        if (!data.startsWith("{")) continue
                        val delta = runCatching {
                            json.parseToJsonElement(data).jsonObject
                                .get("choices")?.jsonArray
                                ?.getOrNull(0)?.jsonObject
                                ?.get("delta")?.jsonObject
                                ?.get("content")?.jsonPrimitive?.contentOrNull
                        }.getOrNull()
                        if (!delta.isNullOrEmpty()) {
                            rawSeen = true
                            val visible = thinking.feed(delta)
                            if (visible.isNotEmpty()) {
                                full.append(visible)
                                onDelta(visible)
                            }
                        }
                    }
                    // 流结束：放行被按住、但已确认不是标记的内容（未闭合的思考块在这里被丢弃）
                    val tail = thinking.finish()
                    if (tail.isNotEmpty()) {
                        full.append(tail)
                        onDelta(tail)
                    }
                }
            } catch (e: IOException) {
                // 流式读取途中的 IOException：绝大多数是用户主动取消（离开页面 / 切会话）
                if (call.isCanceled()) throw AiException("请求已取消")
                throw AiException("网络中断：${e.message ?: "连接异常"}")
            }
            val result = full.toString()
            if (result.isBlank()) throw AiException(
                // 收到过内容却全没了 = 只思考没说话，别让用户对着"响应为空"猜
                if (rawSeen) "模型只返回了思考内容、没有输出正文。可在「API 配置」换用不思考的模型，或调低思考强度。"
                else "模型没有返回内容（流式响应为空）",
                soft = true
            )
            return@withContext result
        }
        // 不可达：上面的循环只会通过 return / throw 退出（Kotlin 流分析需要这句保证返回类型）
        @Suppress("UNREACHABLE_CODE") throw AiException("unreachable")
    }

    /** 非流式响应里取助手正文（服务商忽略 stream=true 时的兜底解析） */
    private fun extractAssistantContent(text: String): String? = runCatching {
        json.parseToJsonElement(text).jsonObject
            .get("choices")?.jsonArray
            ?.getOrNull(0)?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.let { ThinkingFilter.strip(it) }

    /**
     * 生成图片并落盘，返回本地文件路径或可直连 URL。
     * 优先尝试把图片保存到本地（b64_json）；若服务只回 URL 则保留 URL。
     *
     * @param size 本次生图尺寸；null/空串时回退设置默认值；传 "" 可完全不发 size 字段
     *   （部分模型有最小尺寸限制，测试连通性时不限尺寸最稳）
     */
    /**
     * 创作类任务（生成角色卡、起草/扩写提示词）实际使用的设置（2026-09-15 重构）：
     * 整体换成**创作供应商**的 URL / Key / 模型 / 思考强度，这样下面沿用聊天那套请求逻辑，
     * 就自动获得该供应商适配好的温度/思考参数形态（不必在请求层到处判断"这是创作还是对话"）。
     */
    fun AiSettings.forCreation(): AiSettings {
        val url = creationBaseUrlEffective
        return copy(
            chatBaseUrl = url,
            // 一家一份 Key：keyFor 内部已处理"老数据只有一个全局 Key"的情况
            // （那时只对"它当初那家"生效，不同家返回空 → 会提示去填，而不是把 A 家的 Key 发给 B 家）
            chatApiKey = keyFor(url),
            chatModel = creationModel.ifBlank { chatModel },
            chatThinking = creationThinking
        )
    }

    /**
     * 生图请求实际使用的设置（2026-09-15 重构：Key 改为**一家一份**，跟随开关已取消）。
     *
     * - Base URL 就是生图分段自己选的供应商；
     * - Key 取该家在「API 配置」里存的那份（`Repository.loadSettings()` 已解析进 `imageApiKey`），
     *   老数据（生图专用 Key / 全局 Key）由 [AiSettings.imageKey] 兼容读取；
     * - 模型为空时用该供应商的第一个生图预设顶上 —— 用户"点完供应商就保存"会留下空模型，
     *   之后生图只报"请先配置生图服务"，界面上却看不出缺哪一项（2026-09-15 实际踩到）。
     */
    internal fun AiSettings.forImageRequest(): AiSettings =
        copy(imageModel = imageModel.ifBlank { ModelCatalog.imagePresets(imageBaseUrl).firstOrNull().orEmpty() })

    suspend fun generateImage(settingsIn: AiSettings, prompt: String, size: String? = null): String =
        withContext(Dispatchers.IO) {
            // 跟随 API 配置时换成对话的 Base URL 与该供应商的 Key，下面逻辑无需感知差异
            val settings = settingsIn.forImageRequest()
            if (!settings.imageEnabled || settings.imageBaseUrl.isBlank() || settings.imageModel.isBlank()) {
                throw AiException("请在「设置」中启用并配置生图服务（Base URL、API Key、生图模型）")
            }
            requireHttps(settings.imageBaseUrl, "生图 Base URL")
            // MiniMax 生图不是 OpenAI 兼容协议：端点是 /image_generation、尺寸用 aspect_ratio、
            // 响应是 data.image_urls / data.image_base64。单独走一条分支。
            // 生图协议由 ProviderProfiles 声明（OpenAI 兼容 / MiniMax 私有 / 通义原生）
            val profile = ProviderProfiles.resolve(settings.imageBaseUrl, settings.customProviders)
            val protocol = profile?.imageProtocol
                ?: ProviderProfiles.ImageProtocol.OPENAI
            val isMiniMax = protocol == ProviderProfiles.ImageProtocol.MINIMAX
            // 通义/千问（DashScope）的兼容模式**不支持生图**（只支持 chat/embeddings，实测 404），
            // 必须走原生 multimodal-generation 端点，结构也是 input.messages / 星号尺寸。
            val isDashScope = protocol == ProviderProfiles.ImageProtocol.DASHSCOPE
            var body: kotlinx.serialization.json.JsonObject = when {
                isDashScope -> buildJsonObject {
                    put("model", settings.imageModel)
                    putJsonObject("input") {
                        putJsonArray("messages") {
                            addJsonObject {
                                put("role", "user")
                                putJsonArray("content") {
                                    addJsonObject { put("text", prompt) }
                                }
                            }
                        }
                    }
                    putJsonObject("parameters") {
                        put("n", 1)
                        put("watermark", false)
                        val s = when {
                            size == null -> settings.imageSize
                            size.isBlank() -> ""
                            else -> size
                        }
                        dashScopeSize(s)?.let { put("size", it) }
                    }
                }

                isMiniMax -> buildJsonObject {
                    put("model", settings.imageModel)
                    put("prompt", prompt)
                    put("n", 1)
                    val effectiveSize = when {
                        size == null -> settings.imageSize
                        size.isBlank() -> ""
                        else -> size
                    }
                    // MiniMax 只接受官方宽高比，按尺寸换算
                    aspectRatioFor(effectiveSize)?.let { put("aspect_ratio", it) }
                    put("response_format", if (settings.imageResponseFormat == "b64_json") "base64" else "url")
                }

                else -> buildJsonObject {
                    put("model", settings.imageModel)
                    put("prompt", prompt)
                    put("n", 1)
                    // size 语义：null=用设置默认；空串=完全不发尺寸字段（部分模型有最小尺寸限制，
                    // 如 seedream-5.0 要求 ≥3686400 像素，测试连通性时不限尺寸最稳）
                    val effectiveSize = when {
                        size == null -> settings.imageSize
                        size.isBlank() -> ""
                        else -> size
                    }
                    if (effectiveSize.isNotBlank()) put("size", effectiveSize)
                    // OpenAI 的 GPT image 系官方标注"不支持 response_format"（永远返回 base64），
                    // 这类画像直接不发该字段，避免被当未知参数拒绝；其余家（火山/腾讯云/自定义）照发
                    if (profile?.imageFormatUnsupported != true) {
                        put(
                            "response_format",
                            effectiveImageFormat(settings.imageBaseUrl, settings.imageResponseFormat, settings.customProviders)
                        )
                    }
                }
            }
            val url = when {
                isDashScope -> dashScopeImageEndpoint(settings.imageBaseUrl)
                isMiniMax -> endpoint(settings.imageBaseUrl, "image_generation")
                else -> endpoint(settings.imageBaseUrl, "images/generations")
            }

            /** 执行一次生图请求，返回 (HTTP 状态码, 响应文本) */
            suspend fun post(bodyJson: kotlinx.serialization.json.JsonObject): Pair<Int, String> =
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer ${settings.imageApiKey.ifBlank { settings.chatApiKey }}")
                        .header("Content-Type", "application/json")
                        .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    http.newCall(request).execute().use { resp ->
                        resp.code to (resp.body?.string() ?: "")
                    }
                }

            var result = post(body)
            var code = result.first
            var text = result.second
            // 降级重试：部分模型有最小尺寸限制（如 seedream-5.0 要求 ≥3686400 像素），
            // 带 size 报 400 且提到 size 时，去掉 size 字段按服务商默认尺寸重试一次
            if (code == 400 && text.contains("size", ignoreCase = true) && body.containsKey("size")) {
                body = kotlinx.serialization.json.JsonObject(
                    body.toMutableMap().apply { remove("size") }
                )
                result = post(body)
                code = result.first
                text = result.second
            }
            if (code !in 200..299) {
                throw AiException(
                    "生图失败（接口 $url，模型=${settings.imageModel}，HTTP $code）：${errorMessage(text, code)}"
                )
            }
            try {
                val root = json.parseToJsonElement(text).jsonObject
                val data = root["data"]
                // ① 通义/千问（DashScope 原生）：output.choices[0].message.content[0].image → URL（约 24 小时有效）
                val dsImage = runCatching {
                    root["output"]?.jsonObject
                        ?.get("choices")?.jsonArray?.getOrNull(0)?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonArray?.getOrNull(0)?.jsonObject
                        ?.get("image")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!dsImage.isNullOrBlank() && dsImage.startsWith("http")) {
                    return@withContext downloadToStorage(dsImage, "png")
                }
                // ② MiniMax：data.image_urls / data.image_base64（数组）
                val mmUrls = runCatching {
                    data?.jsonObject?.get("image_urls")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!mmUrls.isNullOrBlank() && mmUrls.startsWith("http")) {
                    return@withContext downloadToStorage(mmUrls, "png")
                }
                val mmB64 = runCatching {
                    data?.jsonObject?.get("image_base64")?.jsonArray?.getOrNull(0)?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                if (!mmB64.isNullOrBlank()) {
                    return@withContext Repository.saveImageBytes(
                        java.util.Base64.getMimeDecoder().decode(mmB64.substringAfter("base64,", mmB64)),
                        "png"
                    )
                }
                // ② OpenAI 兼容：data[0].url / data[0].b64_json
                val item = data?.jsonArray?.getOrNull(0)?.jsonObject
                    ?: throw AiException("生图服务返回为空：${text.take(200)}")
                val itemUrl = item["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") }
                if (itemUrl != null) return@withContext downloadToStorage(itemUrl, "png")

                val raw = item["b64_json"]?.jsonPrimitive?.contentOrNull
                    ?: item["url"]?.jsonPrimitive?.contentOrNull
                    ?: throw AiException("生图服务没有返回 url 或 b64_json：${text.take(200)}")
                val pure = raw.substringAfter("base64,", raw)
                val bytes = java.util.Base64.getMimeDecoder().decode(pure)
                val ext = if (raw.startsWith("data:image/jpeg") || raw.startsWith("data:image/jpg")) "jpg" else "png"
                Repository.saveImageBytes(bytes, ext)
            } catch (e: AiException) {
                throw e
            } catch (e: Exception) {
                throw AiException("生图响应解析失败：${text.take(200)}")
            }
        }

    /**
     * 把生图服务返回的**远端图片 URL 下载到本地**再返回本地路径（P1-5）。
     *
     * 以前只有 DashScope 分支这么做，MiniMax 的 `image_urls` 与 OpenAI 兼容的 `data[0].url`
     * 会把远端 URL 原样返回，而它会被写进 `avatarUri / backgroundUri` 并持久化 ——
     * 这类链接大多短期有效（DashScope 官方 24 小时），过期后角色头像/聊天背景就变空白；
     * 若该 URL 需要鉴权头，Coil 裸取还会 401。
     * 下载失败**直接报错让用户重试**，而不是留一个将来必然失效的 URL。
     */
    private suspend fun downloadToStorage(imageUrl: String, ext: String): String =
        withContext(Dispatchers.IO) {
            val bytes = runCatching {
                http.newCall(Request.Builder().url(httpsUrl(imageUrl)).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw AiException("图片下载失败（HTTP ${resp.code}）")
                    resp.body?.bytes()
                }
            }.getOrElse { throw AiException("生图已成功，但图片下载失败（${it.message ?: "网络异常"}），请重试") }
                ?: throw AiException("生图已成功，但图片下载为空，请重试")
            Repository.saveImageBytes(bytes, ext)
        }

    /**
     * 是否为通义/千问（DashScope）域名。它的兼容模式不支持生图（实测 404），
     * 生图必须走原生 multimodal-generation 端点。
     */
    /** DashScope 原生生图端点：取 base 的 scheme+host，拼官方路径 */
    private fun dashScopeImageEndpoint(baseUrl: String): String {
        val origin = runCatching {
            val u = java.net.URI(baseUrl.trim())
            "${u.scheme}://${u.host}"
        }.getOrNull() ?: "https://dashscope.aliyuncs.com"
        return "$origin/api/v1/services/aigc/multimodal-generation/generation"
    }

    /** DashScope 尺寸用星号分隔（"1024*1024"），不是 "1024x1024"；无法解析时返回 null（不发该字段） */
    private fun dashScopeSize(size: String): String? {
        val parts = size.trim().lowercase().split("x", "*")
        if (parts.size != 2) return null
        val w = parts[0].trim().toIntOrNull() ?: return null
        val h = parts[1].trim().toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        return "$w*$h"
    }

    /**
     * 生图返回格式按服务商适配（2026-09-14 核实）：
     * - 智谱 CogView：只返回 URL，不支持 b64_json → 强制 url
     * - OpenAI gpt-image 系：官方文档明确"永远返回 base64，没有 url 选项" → 强制 b64_json
     * - 其余（火山 / MiniMax / 千问 / 硅基流动）：两者都支持，按用户设置发
     * MiniMax 走单独分支（它的枚举是 base64/url，不是 b64_json）。
     */
    private fun effectiveImageFormat(
        baseUrl: String,
        configured: String,
        custom: List<CustomProvider> = emptyList()
    ): String {
        // 服务商固定了返回格式就用它的（智谱只给 url、OpenAI 图像只给 base64），否则听用户的
        return ProviderProfiles.resolve(baseUrl, custom)?.imageFormatForced ?: configured
    }

    /**
     * 把 "1024x768" 这类尺寸换算成官方宽高比（MiniMax 只接受 aspect_ratio）。
     * 无法解析（含空串=不限尺寸）时返回 null（不发该字段，用服务商默认）。
     */
    internal fun aspectRatioFor(size: String): String? {
        val parts = size.trim().lowercase().split("x", "*")
        if (parts.size != 2) return null
        val w = parts[0].trim().toDoubleOrNull() ?: return null
        val h = parts[1].trim().toDoubleOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        val target = w / h
        val options = listOf(
            "1:1" to 1.0,
            "4:3" to 4.0 / 3, "3:4" to 3.0 / 4,
            "3:2" to 1.5, "2:3" to 2.0 / 3,
            "16:9" to 16.0 / 9, "9:16" to 9.0 / 16,
            "21:9" to 21.0 / 9
        )
        return options.minByOrNull { kotlin.math.abs(it.second - target) }?.first
    }
}
