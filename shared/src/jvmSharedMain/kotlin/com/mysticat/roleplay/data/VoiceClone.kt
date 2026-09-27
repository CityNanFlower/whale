package com.mysticat.roleplay.data

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
 * App 里既不能录、也没有"录一段听听像不像"的闭环。这里补上：
 * 录音 → 上传训练 → 轮询状态 → 拿到的音色直接进音色库（随后就能试听 / 朗读）。
 *
 * ### 接口口径（2026-09-25 迁到 V3，官方两篇文档交叉核对；非猜测）
 * - 上传：`POST {base}/api/v3/tts/voice_clone`（**训练即上传，一体的**）；
 *   查询：`POST {base}/api/v3/tts/get_voice`。
 * - 鉴权**两代都支持**：新版控制台 `X-Api-Key: <API Key>`（主路，App 的「API Key」栏直接用）；
 *   旧版控制台 `X-Api-App-Key: <appid>` ＋ `X-Api-Access-Key: <token>`（兼容回退）。
 *   两者都带 `X-Api-Request-Id`。**V1 那套 `Authorization: Bearer;{token}` ＋ `Resource-Id` 在 V3 里完全不用**
 *   ——训练接口没有"资源名"这一维了（合成侧才有）。
 * - 请求体：`speaker_id`（**自建音色固定传字面量 `custom_speaker_id`**）、`custom_speaker_id`（真实代号）、
 *   `audio: {data: <base64>, format: <wav|mp3|ogg|m4a|aac|pcm>}`、`text`（可选参考文本）、`language`（0 = 中文）。
 *   **没有 `model_type` / `source` 字段了**（V1 才要）。
 * - 音频：单文件 ≤10MB、**一次只传 1 个**。App 自己录的两端正好都在官方列表里（Android m4a、桌面 wav）。
 * - 音色代号：**`S_` / `icl_` 是官方保留前缀，客户端不许自己造** ⇒ 界面上是两种用法：
 *   ① **控制台给你的槽位**（`S_` 开头）——控制台建好音色、App 只负责往里灌录音（这种直接把它当 `speaker_id` 发）；
 *   ② **App 自己命名**（后付费音色）——[newSpeakerId] 生成合规代号，走 `custom_speaker_id` 字段。
 *
 * ### 计费（官方口径，直接影响界面措辞）
 * **后付费音色试听不收费；首次「语音合成」才视为转正、收一次音色槽位费。**
 * ⇒ 复刻成功后优先播训练/查询回体里的 `demo_audio`（免费、现成、1 小时有效，见 [fetchDemo] /
 * [Status.demoAudio]），不要一上来就调合成（那一下就开收钱）——界面文案也得跟着说清（见 `SettingsViewModel`）。
 *
 * ### 保留的"自动退一步"（只为一类错误：参考文本被 WER 拒）
 * `text`（参考文本）能让复刻更像，但读得不准会被服务端 WER 校验拒（**错误码 `45001109`**，V1 时代是 1109）——
 * 被拒就**去掉文本重发一次**，而不是让用户反复重录。其它错误（参数/权限/网络）一律原样抛给用户，不掩盖真问题。
 */
object VoiceCloneService {

    // ────────────────────────── 常量 ──────────────────────────

    /** 训练（上传）路径。V3 起与旧的 `api/v1/mega_tts/audio/upload` 是两套 */
    const val PATH_CLONE = "api/v3/tts/voice_clone"

    /** 状态查询路径（旧的 `api/v1/mega_tts/status` 已停用） */
    const val PATH_GET = "api/v3/tts/get_voice"

    /**
     * 自建音色时 `speaker_id` 要填的**字面量**（官方口径：真实代号放 `custom_speaker_id`）。
     * `S_`/`icl_` 那种控制台槽位不用它——那种直接把槽位 id 当 `speaker_id` 发。
     */
    const val CUSTOM_SLOT = "custom_speaker_id"

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

    /** 轮询状态的总时长与间隔。ICL 通常几十秒内完成，超时就当"还没好"记账，不判失败 */
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

    /**
     * 复刻要的凭据。**两代控制台各一组**，填哪组都行（V3 接口同时认这两套鉴权头）：
     * 新版 = [apiKey]；旧版 = [appId] + [accessToken]。空的一组字段是空串。
     */
    data class Creds(
        val baseUrl: String,
        val apiKey: String = "",
        val appId: String = "",
        val accessToken: String = ""
    ) {
        /** 有新版的 API Key 就走新版单头；否则退回旧版双头 */
        val modern: Boolean get() = apiKey.isNotBlank()
    }

    /**
     * 从设置里取复刻凭据。**新版 API Key 优先**（新版控制台只有 Key，也是官方推荐的那条）；
     * 没有 Key 时要求旧版的 AppID + Access Token 都在。两者都没有返回 null，
     * 由调用方给用户一句**能动手**的话（见 [missingCredsHint]）。
     */
    fun creds(settings: AiSettings, baseUrl: String): Creds? {
        val c = settings.speechCredential(baseUrl)
        val apiKey = c["apiKey"].orEmpty().trim()
        val appId = c["appId"].orEmpty().trim()
        val token = c["accessToken"].orEmpty().trim()
        if (apiKey.isBlank() && (appId.isBlank() || token.isBlank())) return null
        return Creds(baseUrl.trim(), apiKey, appId, token)
    }

    /** 缺凭据时给用户的一句话（说清"去哪一栏填什么"，别只说失败） */
    const val missingCredsHint =
        "声音复刻要火山语音凭据：到「语音服务 → 语音凭据 → 单独填一把」里填**新版控制台的 API Key**" +
            "（控制台「API Key 管理」建一把，推荐）；旧版控制台的 AppID + Access Token 也兼容。"

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
     * 控制台给的那种**槽位 id**（`S_` / `icl_`）——这种不用 `custom_speaker_id` 字段，
     * 直接把 id 当 `speaker_id` 发。判据只用前缀：本机复刻库里的 id 是我们自己命名的那一类。
     */
    fun isConsoleSlot(id: String): Boolean {
        val v = id.trim()
        return v.startsWith("S_") || v.startsWith("icl_")
    }

    /**
     * 本机记不认得这个音色（在复刻音色库里）——判定"是不是复刻音色"要用它，
     * 只看 `S_` / `icl_` 前缀会漏掉 App 自己命名的那些。
     */
    fun knownIds(settings: AiSettings): List<String> = settings.ttsCloneVoices.map { it.id }

    // ────────────────────────── 请求体 / 响应解析（纯函数，自检直接验这些） ──────────────────────────

    /**
     * 训练请求体（V3）。**纯函数**：自检里对着它逐字段断言，不必真有网络。
     *
     * 槽位与自建音色的**两种发法**都收在这里（[isConsoleSlot] 分流）——把这条规则关在一个函数里，
     * 界面上那条"留空＝App 命名 / 填了＝用控制台槽位"的说明才有唯一对应的实现。
     */
    fun uploadBody(
        speakerId: String,
        audio: ByteArray,
        format: String,
        text: String?
    ): String {
        val sid = speakerId.trim()
        return buildJsonObject {
            put("speaker_id", if (isConsoleSlot(sid)) sid else CUSTOM_SLOT)
            // 自建音色的真实代号；控制台槽位没有这个字段
            if (!isConsoleSlot(sid)) put("custom_speaker_id", sid)
            putJsonObject("audio") {
                put("data", Base64.getEncoder().encodeToString(audio))
                put("format", format.trim().lowercase())
            }
            // text 可空：读得准能提升相似度，读错整条被 WER 拒 —— 所以由调用方决定发不发
            if (!text.isNullOrBlank()) put("text", text)
            put("language", LANGUAGE_CN)
        }.toString()
    }

    /** 状态查询请求体（与训练同一套字段口径） */
    fun statusBody(speakerId: String): String {
        val sid = speakerId.trim()
        return buildJsonObject {
            put("speaker_id", if (isConsoleSlot(sid)) sid else CUSTOM_SLOT)
            if (!isConsoleSlot(sid)) put("custom_speaker_id", sid)
        }.toString()
    }

    /**
     * 状态查询的结论。[demoAudio] 是官方给的**免费试听片段 URL**（1 小时有效，见类注释的计费一节）。
     */
    data class Status(val code: Int, val message: String, val demoAudio: String = "") {
        /** 2 成功 / 4 已激活 —— 官方口径：这两个都能拿去合成 */
        val ready: Boolean get() = code == 2 || code == 4

        /** 0 未找到（刚上传时可能还没登记，也可能 id 填错了） */
        val notFound: Boolean get() = code == 0

        val failed: Boolean get() = code == 3
    }

    /**
     * 解析接口回体里的**训练状态**。
     *
     * ⚠️ **顶层 `code` 不是状态**：V3 的 `code` 是业务错误码（`0` = 成功），V1 才把状态塞在 `BaseResp.StatusCode`。
     * 早期实现把顶层 `code` 也当状态读，会把 V3 的成功回体（`code=0`）读成"未找到"——所以这里只认
     * `status`（V3 与部分网关的摊平写法）与 `BaseResp.StatusCode`（V1 遗留）。
     */
    fun parseStatus(body: String): Status {
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return Status(-1, "")
        val resp = obj["BaseResp"]?.jsonObject
        val code = (obj["status"]?.jsonPrimitive?.intOrNull)
            ?: (resp?.get("StatusCode")?.jsonPrimitive?.intOrNull)
            ?: -1
        val msg = (resp?.get("StatusMessage")?.jsonPrimitive?.contentOrNull)
            ?: (obj["message"]?.jsonPrimitive?.contentOrNull).orEmpty()
        return Status(code, msg, demoAudioOf(obj))
    }

    /**
     * 从回体里找 `demo_audio`。官方把它放在 `speaker_status[]` 里（有的网关会摊平到顶层），两处都认。
     * 这是**唯一一条不用花钱就能听到复刻结果**的路子，所以宁可多认一种写法。
     */
    private fun demoAudioOf(obj: kotlinx.serialization.json.JsonObject): String {
        val top = obj["demo_audio"]?.jsonPrimitive?.contentOrNull
        if (!top.isNullOrBlank()) return top
        val arr = obj["speaker_status"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return ""
        return arr.firstNotNullOfOrNull { e ->
            runCatching { e.jsonObject["demo_audio"]?.jsonPrimitive?.contentOrNull }.getOrNull()?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    /**
     * 从回体里抠出**服务端说的话**（给用户看的一行）。
     * 成功时 `BaseResp.StatusCode`（V1 写法）或顶层 `code`（V3 写法）是 0，非 0 就是失败原因 —— 原样带出来，
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
     * 这个错误值不值得"去掉参考文本再试一次"（见类注释的退一步）。
     * 只认 WER 那一类（`45001109` = V3 的 WER 码、`1109` = V1 的同一件事）；
     * 参数/权限/网络错误一律不重试——重试只会把真相拖慢，还可能白花一次训练额度。
     */
    fun retryable(message: String): Boolean {
        val m = message.lowercase()
        return listOf("wer", "1109", "45001109", "text is not match", "text not match").any { it in m }
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

    /**
     * 统一 POST。**鉴权两代分流**：新版只有 `X-Api-Key`（同时带 AppID/Access-Key 会报
     * `load grant: requested grant not found`），旧版是 `X-Api-App-Key` + `X-Api-Access-Key`。
     */
    private fun post(creds: Creds, path: String, body: String): Pair<Int, String> {
        val b = Request.Builder()
            .url(url(creds.baseUrl, path))
            .header("Content-Type", "application/json")
            .header("X-Api-Request-Id", java.util.UUID.randomUUID().toString())
        if (creds.modern) {
            b.header("X-Api-Key", creds.apiKey)
        } else {
            b.header("X-Api-App-Key", creds.appId)
            b.header("X-Api-Access-Key", creds.accessToken)
        }
        return http.newCall(b.post(body.toRequestBody("application/json".toMediaType())).build())
            .execute().use { resp -> resp.code to resp.body?.string().orEmpty() }
    }

    private fun url(baseUrl: String, path: String): String =
        "${baseUrl.trim().trimEnd('/')}/${path.trimStart('/')}"

    /**
     * 上传一段录音去训练 [speakerId]。**"去掉参考文本再试一次"就在这里**（见类注释）。
     * 成功返回（没有返回值）；失败抛 [AiException]，消息里带服务端原文。
     */
    fun upload(creds: Creds, speakerId: String, audio: ByteArray, format: String) {
        if (audio.isEmpty()) throw AiException("录音是空的，请重新录一段")
        if (audio.size > MAX_AUDIO_BYTES) {
            throw AiException("录音超过 ${MAX_AUDIO_BYTES / 1024 / 1024}MB 了（官方上限），请录短一点")
        }
        // 顺序＝先按"带参考文本"发（效果最好的一条），被 WER 拒再去掉文本重发
        var last: AiException? = null
        for (text in listOf(REFERENCE_TEXT, null)) {
            try {
                val body = uploadBody(speakerId, audio, format, text)
                val (code, raw) = post(creds, PATH_CLONE, body)
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
                // 网络层错误不重试：去掉文本也救不了断掉的连接，直接告诉用户
                throw AiException("复刻上传失败：${t.message ?: "网络错误"}")
            }
        }
        throw last ?: AiException("复刻上传失败")
    }

    /** 查一次状态。返回 null = 连不上/回体不是 JSON/回体里没有状态字段（调用方当"这次没问出来"，继续轮询） */
    fun query(creds: Creds, speakerId: String): Status? {
        val (code, raw) = post(creds, PATH_GET, statusBody(speakerId))
        if (code !in 200..299) return null
        val st = parseStatus(raw)
        return if (st.code == -1) null else st
    }

    /**
     * 下载官方返回的**试听片段**（`demo_audio`）。**不带鉴权**——签名在 URL 里（1 小时有效）。
     *
     * 为什么值得为此写一个网络调用：后付费音色"首次合成即转正收费"，而这个 URL 是**免费**的
     * ——用户想先听听像不像，走这里，不必先付一次合成费。
     */
    fun fetchDemo(url: String): ByteArray {
        val u = url.trim()
        if (u.isBlank()) throw AiException("这条音色没有官方试听片段")
        val (code, bytes) = http.newCall(Request.Builder().url(u).get().build()).execute().use { resp ->
            resp.code to (resp.body?.bytes() ?: ByteArray(0))
        }
        if (code !in 200..299 || bytes.isEmpty()) throw AiException("试听片段下载失败（HTTP $code）")
        return bytes
    }

    /** 复刻的结果：好了（带官方试听片段）；或"传上去了但没等到训练完"（这种要记账，不能丢） */
    sealed interface Outcome {
        /** [demoAudio] 官方试听片段 URL，空串＝这次回体里没给（不是错误） */
        data class Ready(val demoAudio: String = "") : Outcome

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
        var demo = ""
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val st = query(creds, speakerId)
            if (st != null && st.demoAudio.isNotBlank()) demo = st.demoAudio
            when {
                st == null -> onProgress("正在复刻…（状态查询暂时没回，重试中）")
                st.ready -> return Outcome.Ready(demo)
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
