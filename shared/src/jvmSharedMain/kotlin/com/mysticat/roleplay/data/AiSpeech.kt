package com.mysticat.roleplay.data

import java.util.Base64
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.addJsonObject
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 语音合成与识别（TTS / STT）的各供应商实现：地址、鉴权、请求体与响应解析都在这里。
 * 门面与公共管道见 `AiClient.kt`。
 */

/**
 * 语音合成（TTS v2）：OpenAI 兼容 `POST /audio/speech`，返回音频字节（默认 mp3）。
 *
 * 口径（见 `docs/鲸鱼-TTS-v2-调研与设计.md`）：
 * - Key 走 [AiSettings.speechCredential]（一家一份：优先"语音专用凭据"，没有则回退「模型与API」里那家那把，
 *   所以界面**不强制**新增 Key 入口）；本地服务留空 Key 也放行；
 * - `speed` 取「语音朗读」页的语速（各家的取值范围不同，这里统一夹到 0.25~4.0）；
 * - 不做流式：按句切分后逐段请求（天然满足各家 4096 字符之类的上限），播放在 TtsSpeaker 里排队。
 */
suspend fun AiClient.textToSpeech(settings: AiSettings, text: String): ByteArray = withContext(Dispatchers.IO) {
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
internal fun AiClient.openAiSpeech(base: String, key: String, settings: AiSettings, text: String): ByteArray {
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
internal fun AiClient.miniMaxSpeech(base: String, key: String, settings: AiSettings, text: String): ByteArray {
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
internal fun AiClient.dashScopeSpeech(base: String, key: String, settings: AiSettings, text: String): ByteArray {
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
internal fun AiClient.dashScopeError(body: String, code: Int): String {
    val msg = runCatching {
        json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    return if (!msg.isNullOrBlank()) "阿里百炼：$msg" else errorMessage(body, code)
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
internal fun AiClient.volcV3Speech(
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
internal fun AiClient.volcFlashTranscribe(
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
internal fun AiClient.volcResourceIdFor(voice: String, settings: AiSettings): String =
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
internal fun AiClient.volcSpeech(
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
internal fun AiClient.tencentSpeech(
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

internal fun AiClient.sha256Hex(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

internal fun AiClient.hmacSha256(key: ByteArray, msg: String): ByteArray {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
}

/** MiniMax 的音频是 hex 字符串，两字符一字节 */
internal fun AiClient.hexToBytes(hex: String): ByteArray {
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
suspend fun AiClient.transcribe(settings: AiSettings, audio: java.io.File): String = withContext(Dispatchers.IO) {
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
internal fun AiClient.audioMimeFor(file: java.io.File): String =
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
