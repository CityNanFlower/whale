package com.mysticat.roleplay.data

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * **声音复刻**（火山豆包语音，2026-09-22 用户要求"复刻音色那里做一下应用内录音与合成"）。
 *
 * 用户原话是"复刻音色那里做一下应用内录音与合成"——原来的复刻音色只能**去控制台建好、把 `S_` id 粘进来**，
 * App 里既不能录、也没有"合成一次听听像不像"的闭环。这里补上：
 * 录音 → 上传训练 → 轮询状态 → 拿到的音色直接进音色库（随后就能试听 / 朗读）。
 *
 * ### 接口口径（2026-09-22 联网核实，非猜测）
 * - 上传：`POST {base}/api/v1/mega_tts/audio/upload`，头 `Authorization: Bearer;{token}`（**分号**）
 *   ＋ `Resource-Id`；体 `{appid, speaker_id, audios:[{audio_bytes(base64), audio_format, text?}], source:2, model_type:4, language:0}`。
 *   与 [AiClient.volcV3Speech] 的 V3 合成接口是**两套**：复刻训练至今只有 v1/mega_tts 这套被多方 SDK 实现
 *   （yankeguo/volcvoice、doubao-speech-go、多个开源服务端），故照它写。
 * - 状态：`POST {base}/api/v1/mega_tts/status`，体 `{appid, speaker_id}`，
 *   返回 `status`：0 未找到 / 1 训练中 / **2 成功** / 3 失败 / **4 已激活**（2 与 4 都能用于合成）。
 * - 音频：官方支持 `wav / mp3 / ogg / m4a / aac / pcm`，单文件 ≤10MB、**一次只传 1 个**。
 *   App 自己录的两端正好都在里面（Android 是 m4a、桌面是 wav）。
 * - `speaker_id`：**`S_` 是官方保留前缀，客户端不许自己造**（官方命名规则里明确屏蔽）。
 *   于是有两条路，界面上是两种用法：
 *   ① **控制台给你的槽位**（`S_` 开头）——你在控制台建好音色，App 只负责往里灌录音；
 *   ② **App 自己命名**（后付费"声音复刻 2.0"，客户端直接给 id）——[newSpeakerId] 生成合规代号。
 *
 * ### 两处"自动退一步"（不是为了好看，是因为两条口径都有官方出处且无法在本机判定哪条对）
 * ① `Resource-Id` 发 [RES_MODERN]（配 `model_type=4`，与 App 合成侧用的 `seed-icl-2.0` 对齐），
 *    服务端说资源不对时退到旧文档写的 [RES_LEGACY]；
 * ② `text`（参考文本）能让复刻更像，但读得不准会被 WER 校验拒（`1109 WERError`）——
 *    被拒就**去掉文本重发一次**，而不是让用户反复重录。
 * 两者都只在"换一种发法可能就过"的错误上触发，其它错误原样抛给用户（不掩盖真问题）。
 */
object VoiceCloneService {

    // ────────────────────────── 常量 ──────────────────────────

    /** 新版（2.0）资源名，与 `model_type = 4` 配套，也是合成侧 `seed-icl-2.0` 的那一档 */
    const val RES_MODERN = "seed-icl-2.0"

    /** 旧版资源名（官方 v1 文档写的就是它）——只在服务端拒了 [RES_MODERN] 时退回来用 */
    const val RES_LEGACY = "volc.megatts.voiceclone"

    /** 复刻算法档位：4 = 声音复刻 2.0（ICL V2），与合成侧 `seed-icl-2.0` 同一代 */
    const val MODEL_TYPE = 4

    /** 语言：0 = 中文 */
    const val LANGUAGE_CN = 0

    const val MAX_AUDIO_BYTES = 10 * 1024 * 1024

    /** 录音时长口径：太短复刻不像，太长只是白传（官方建议 3~10 秒，这里取更稳的下限） */
    const val MIN_SECONDS = 5
    const val MAX_SECONDS = 30

    /**
     * 参考文本：录音时照着读，服务端拿它与音频做 WER 比对。
     * 挑的是**音素覆盖面广、没有生僻字、读起来不用想**的一句（避免"读错一个字就白录"）。
     */
    const val REFERENCE_TEXT =
        "今天天气很好，我打算出去走走，顺便买一点水果和牛奶回家，晚上再听一会儿音乐。"

    /** 轮询状态的总时长与间隔。1.0/2.0 的 ICL 通常几十秒内完成，超时就当"还没好"记账，不判失败 */
    private const val POLL_INTERVAL_MS = 3000L
    private const val POLL_TIMEOUT_MS = 150_000L

    private val json = Json { ignoreUnknownKeys = true }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // 上传要带 base64 音频（几十 KB~几 MB），读超时给宽一点，与合成侧同量级
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    // ────────────────────────── 凭据 / 代号 ──────────────────────────

    /** 复刻要的凭据：**AppID + Token**（`Authorization: Bearer;{token}`）。缺一个就不能复刻 */
    data class Creds(val baseUrl: String, val appId: String, val token: String)

    /**
     * 从设置里取复刻凭据。两代的字段名不同但要的是同一件事：
     * 旧版控制台的 `accessToken` 优先，新版控制台的 `apiKey` 兜底（新版 Key 在 mega_tts 这套里
     * 也当 Bearer token 用，社区实现就是这么发的）；AppID 两代都要。
     *
     * 取不到返回 null，由调用方给用户一句**能动手**的话（见 [missingCredsHint]）。
     */
    fun creds(settings: AiSettings, baseUrl: String): Creds? {
        val c = settings.speechCredential(baseUrl)
        val appId = c["appId"].orEmpty().trim()
        val token = c["accessToken"].orEmpty().trim().ifBlank { c["apiKey"].orEmpty().trim() }
        if (appId.isBlank() || token.isBlank()) return null
        return Creds(baseUrl.trim(), appId, token)
    }

    /** 缺凭据时给用户的一句话（说清"去哪一栏填什么"，别只说失败） */
    const val missingCredsHint =
        "声音复刻要火山旧版控制台的 **AppID + Access Token**（新版控制台的 API Key 只有一半情况能用）：" +
            "在这家「语音凭据 → 单独填一把」里把两个都填上。"

    /**
     * 生成一个**合规的新音色代号**（后付费模式：客户端自己命名）。
     *
     * 命名规则摘自官方（音色代号校验）：8~256 字符、只允许字母数字与 `-` `_`、**必须以字母开头**、
     * 首尾不能是 `-`/`_`、且不能撞官方保留前缀（`S_` / `ICL_` / `MIX_` / `DiT_` / `BV` /
     * **两个小写字母加下划线** / 行星名）与保留后缀（`_bigtts` / `_tob` / `_streaming` 等）。
     * `whale` + 10 位十六进制**不含下划线**，处处避开这些坑；[isValidSpeakerId] 守着这条。
     */
    fun newSpeakerId(seed: Long): String {
        // 取 seed 的低 40 位当十六进制补到 10 位：同 seed 恒等（自检与"同一段录音重试"都能复现），
        // 且**不含下划线**、恒以字母 w 开头 —— 保留前缀/后缀一条都撞不上
        val hex = (seed and 0xFFFFFFFFFFL).toString(16).padStart(10, '0').takeLast(10)
        return "whale$hex"
    }

    /** 是不是一个合法的**自定义**音色代号（官方那套命名规则；`S_` 开头的控制台槽位不走这条） */
    fun isValidSpeakerId(id: String): Boolean {
        val v = id.trim()
        if (v.length !in 8..256) return false
        if (!v[0].isLetter()) return false
        if (v.last() == '-' || v.last() == '_') return false
        if (!v.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return false
        val lower = v.lowercase()
        val reservedPrefix = lower.startsWith("s_") || lower.startsWith("icl_") || lower.startsWith("mix_") ||
            lower.startsWith("dit_") || lower.startsWith("bv") ||
            // 两个小写字母 + 下划线也是保留前缀（`[a-z]{2}_`）
            (v.length > 2 && v[0].isLowerCase() && v[1].isLowerCase() && v[2] == '_') ||
            listOf("wvae_", "moon_", "mercury_", "venus_", "earth_", "mars_", "jupiter_", "saturn_",
                "uranus_", "neptune_", "pluto_").any { lower.startsWith(it) }
        val reservedSuffix = listOf("_bigtts", "_bigtts_cc", "_tob", "_cs_tob", "_streaming").any { lower.endsWith(it) }
        return !reservedPrefix && !reservedSuffix
    }

    /**
     * 本机记不认得这个音色（在复刻音色库里）——判定"是不是复刻音色"要用它，
     * 只看 `S_` / `icl_` 前缀会漏掉 App 自己命名的那些。
     */
    fun knownIds(settings: AiSettings): List<String> = settings.ttsCloneVoices.map { it.id }

    // ────────────────────────── 请求体 / 响应解析（纯函数，自检直接验这些） ──────────────────────────

    /** 上传请求体。**纯函数**：自检里对着它逐字段断言，不必真有网络 */
    fun uploadBody(
        appId: String,
        speakerId: String,
        audio: ByteArray,
        format: String,
        text: String?
    ): String = buildJsonObject {
        put("appid", appId)
        put("speaker_id", speakerId.trim())
        put("audios", buildJsonArray {
            add(buildJsonObject {
                put("audio_bytes", Base64.getEncoder().encodeToString(audio))
                put("audio_format", format.trim().lowercase())
                // text 可空：读得准能提升相似度，读错整条被 WER 拒 —— 所以由调用方决定发不发
                if (!text.isNullOrBlank()) put("text", text)
            })
        })
        // 官方 SDK 与多个开源实现都带这个字段（音频来源：2 = 上传），照发
        put("source", 2)
        put("model_type", MODEL_TYPE)
        put("language", LANGUAGE_CN)
    }.toString()

    /** 状态查询请求体 */
    fun statusBody(appId: String, speakerId: String): String = buildJsonObject {
        put("appid", appId)
        put("speaker_id", speakerId.trim())
    }.toString()

    /** 状态查询的结论 */
    data class Status(val code: Int, val message: String) {
        /** 2 成功 / 4 已激活 —— 官方口径：这两个都能拿去合成 */
        val ready: Boolean get() = code == 2 || code == 4

        /** 0 未找到（刚上传时可能还没登记，也可能 id 填错了） */
        val notFound: Boolean get() = code == 0

        val failed: Boolean get() = code == 3
    }

    /**
     * 解析接口回体。两个字段位置都要认：业务码在 `BaseResp`（v1 这套的写法），
     * 有些网关会把它摊平到顶层 `status` / `code`（社区实现里两种都见过）。
     */
    fun parseStatus(body: String): Status {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return Status(-1, "")
        val resp = obj["BaseResp"]?.jsonObject
        val code = (obj["status"]?.jsonPrimitive?.intOrNull)
            ?: (resp?.get("StatusCode")?.jsonPrimitive?.intOrNull)
            ?: (obj["code"]?.jsonPrimitive?.intOrNull)
            ?: -1
        val msg = (resp?.get("StatusMessage")?.jsonPrimitive?.contentOrNull)
            ?: (obj["message"]?.jsonPrimitive?.contentOrNull).orEmpty()
        return Status(code, msg)
    }

    /**
     * 从回体里抠出**服务端说的话**（给用户看的一行）。
     * 上传接口成功时 `BaseResp.StatusCode` 是 0，非 0 就是失败原因 —— 原样带出来，
     * 别翻译成"上传失败"这种看不出下一步的废话。
     */
    fun serverError(body: String): String? {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val resp = obj["BaseResp"]?.jsonObject
        val code = resp?.get("StatusCode")?.jsonPrimitive?.intOrNull
            ?: obj["code"]?.jsonPrimitive?.intOrNull
            ?: return null
        if (code == 0) return null
        val msg = resp?.get("StatusMessage")?.jsonPrimitive?.contentOrNull
            ?: obj["message"]?.jsonPrimitive?.contentOrNull
        return "服务端返回 $code${if (!msg.isNullOrBlank()) "：$msg" else ""}"
    }

    /**
     * 这个错误值不值得"换一种发法再试一次"（见类注释的两处退一步）。
     * 只认资源不匹配与 WER 两类；网络/参数/权限错误一律不重试——重试只会把真相拖慢。
     */
    fun retryable(message: String): Boolean {
        val m = message.lowercase()
        return listOf("wer", "1109", "resource", "not granted", "licen", "mismatch", "speaker_id invalid")
            .any { it in m }
    }

    /** 录音文件 → 官方认的音频格式名。认不出返回 null（调用方给用户一句"换一种录制方式"） */
    fun audioFormatOf(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "m4a" -> "m4a"
        "wav", "wave" -> "wav"
        "mp3" -> "mp3"
        "ogg", "oga" -> "ogg"
        "aac" -> "aac"
        "pcm" -> "pcm"
        else -> null
    }

    // ────────────────────────── 网络 ──────────────────────────

    private fun post(url: String, token: String, resourceId: String, body: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            // ⚠ `Bearer;` 后面是**分号**（官方口径），写成空格会被判未授权
            .header("Authorization", "Bearer;$token")
            .header("Resource-Id", resourceId)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { resp ->
            resp.code to resp.body?.string().orEmpty()
        }
    }

    private fun url(baseUrl: String, path: String): String =
        "${baseUrl.trim().trimEnd('/')}/${path.trimStart('/')}"

    /**
     * 上传一段录音去训练 [speakerId]。**两处"退一步"都在这里**（见类注释）：换发法只在这两类的错误上做。
     * 成功返回（没有返回值）；失败抛 [AiException]，消息里带服务端原文。
     */
    fun upload(creds: Creds, speakerId: String, audio: ByteArray, format: String) {
        if (audio.isEmpty()) throw AiException("录音是空的，请重新录一段")
        if (audio.size > MAX_AUDIO_BYTES) {
            throw AiException("录音超过 ${MAX_AUDIO_BYTES / 1024 / 1024}MB 了（官方上限），请录短一点")
        }
        // 顺序＝先按"2.0 + 带参考文本"发（效果最好的一条），失败再按两类错误各退一步
        val attempts = listOf(
            Triple(RES_MODERN, true, REFERENCE_TEXT),
            Triple(RES_MODERN, false, null),
            Triple(RES_LEGACY, true, REFERENCE_TEXT),
            Triple(RES_LEGACY, false, null),
        )
        var last: AiException? = null
        for ((resourceId, withText, text) in attempts) {
            try {
                val body = uploadBody(creds.appId, speakerId, audio, format, if (withText) text else null)
                val (code, raw) = post(url(creds.baseUrl, "api/v1/mega_tts/audio/upload"), creds.token, resourceId, body)
                if (code !in 200..299) {
                    val e = AiException("复刻上传失败（HTTP $code）：${raw.take(300)}")
                    last = e
                    if (!retryable(raw)) throw e
                    continue
                }
                val err = serverError(raw)
                if (err == null) return  // 成功
                val e = AiException("复刻上传失败：$err")
                last = e
                if (!retryable(err)) throw e
            } catch (t: java.io.IOException) {
                // 网络层错误不重试：换资源名也救不了断掉的连接，直接告诉用户
                throw AiException("复刻上传失败：${t.message ?: "网络错误"}")
            }
        }
        throw last ?: AiException("复刻上传失败")
    }

    /** 查一次状态。返回 null = 连不上/回体不是 JSON（调用方当"这次没问出来"，继续轮询） */
    fun query(creds: Creds, speakerId: String): Status? {
        val (code, raw) = post(
            url(creds.baseUrl, "api/v1/mega_tts/status"),
            creds.token, RES_MODERN, statusBody(creds.appId, speakerId)
        )
        if (code !in 200..299) return null
        val st = parseStatus(raw)
        return if (st.code == -1) null else st
    }

    /** 复刻的结果：好了；或"传上去了但没等到训练完"（这种要记账，不能丢） */
    sealed interface Outcome {
        data object Ready : Outcome
        data class Pending(val reason: String) : Outcome
    }

    /**
     * 上传 + 等训练完成。给界面一行进度字（[onProgress]），返回 [Outcome]。
     * 上传失败会抛异常；**训练没等到不算失败**（音色已经建好了，只是还没熟，
     * 记账成"训练中"让用户稍后点「查状态」接着看，比丢掉这次录音诚实）。
     */
    suspend fun create(
        creds: Creds,
        speakerId: String,
        audio: ByteArray,
        format: String,
        onProgress: (String) -> Unit = {}
    ): Outcome {
        onProgress("正在上传录音…")
        upload(creds, speakerId, audio, format)
        onProgress("已上传，正在复刻…（通常几十秒）")
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var notFoundCount = 0
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val st = query(creds, speakerId)
            when {
                st == null -> onProgress("正在复刻…（状态查询暂时没回，重试中）")
                st.ready -> return Outcome.Ready
                st.failed -> throw AiException("复刻失败：${st.message.ifBlank { "服务端说这条录音不合格，换一段更清晰的录音再试" }}")
                st.notFound -> {
                    notFoundCount++
                    // 连续查不到：多半是 id 不对（手填的槽位填错了 / 没有这个音色），别让用户干等两分半
                    if (notFoundCount >= 5) {
                        throw AiException("服务端说没有「$speakerId」这个音色（id 可能不对，或这个账号还没开通声音复刻）")
                    }
                    onProgress("正在复刻…（刚上传，服务端还没登记）")
                }
                else -> onProgress("正在复刻…（第 ${st.code} 步）")
            }
        }
        return Outcome.Pending("已经上传成功，但没等到训练完成（服务端还在处理）")
    }
}
