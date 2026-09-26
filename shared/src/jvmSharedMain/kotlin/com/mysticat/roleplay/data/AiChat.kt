package com.mysticat.roleplay.data

import java.util.Base64
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 对话请求：请求体构造（思考档、温度、上下文预算与裁剪）、非流式与流式两档，
 * 以及创作/生图两套设置的实际取值。门面与公共管道见 `AiClient.kt`。
 */

/**
 * 该供应商是否接受 temperature 参数 —— 读 [ProviderProfiles]（单一事实来源，
 * 设置界面显示的能力说明与这里保持一致）。Kimi 实测返回 `invalid temperature`，故不发。
 */
internal fun AiClient.supportsTemperature(
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
internal fun AiClient.safeTemperature(v: Double): Double =
    (v.coerceIn(0.0, 2.0) * 100).roundToInt() / 100.0

/**
 * 旧模型名兜底：静默映射到现行模型，老存档不改也能继续用。
 * - `deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 弃用 → `deepseek-flash`
 * - `doubao-seed-2-1-pro-260628` 已更名为 `doubao-seed-2-1-pro-260915`（火山 2026-09-16 公告，
 *   旧 id 会直接报错）→ 显式映射，避免"设置里存着旧名、聊天就报错"
 */
internal fun AiClient.normalizeModel(model: String): String = when (model) {
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
internal fun AiClient.thinkingBody(
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
 * MiniMax 一类的请求字段：显式要求**把思考拆到 `reasoning_content`**。
 *
 * 官方说明：`reasoning_split` 不控制"要不要思考"，只控制思考返回到哪个字段 ——
 * 不传时思考会被 `<think>…</think>` 包着塞进 `content`，于是跟着正文显示进了聊天气泡。
 * App 不读 `reasoning_content`，拆过去就等于不显示。响应侧另有 [ThinkingFilter] 兜底。
 */
internal fun AiClient.reasoningSplitBody(
    baseUrl: String,
    custom: List<CustomProvider> = emptyList()
): Pair<String, kotlinx.serialization.json.JsonElement>? =
    if (ProviderProfiles.splitsReasoning(baseUrl, custom)) {
        "reasoning_split" to kotlinx.serialization.json.JsonPrimitive(true)
    } else {
        null
    }

/** 发送一次完整对话（非流式，简单可靠；返回助手回复文本） */
suspend fun AiClient.chat(
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
        // 采样惩罚（实验定稿）：按「模型×思考模式」白名单发，不支持的家不发
        ProviderProfiles.penalties(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.customProviders)?.let { (f, p) ->
            put("frequency_penalty", f)
            put("presence_penalty", p)
        }
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
            // 模型可能把思考包在 <think>…</think> 里混进正文，先剥掉再判断"有没有内容"
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
suspend fun AiClient.chatCompletion(
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
internal const val CONTEXT_CHAR_BUDGET = 24000

/** 单张随消息图片计入预算的上限（见 [weightOf]）：约等于 4.5KB 图片的 base64 长度 */
internal const val IMAGE_WEIGHT_CAP = 6000

/** 携带历史：先按条数截断，再按字符预算从最早丢弃（scene 卡不参与）。公开供 VM 计算摘要范围 */
/**
 * 一条消息在请求里的"字符当量"：正文 + 随消息图片的 base64 体积。
 *
 * 原来只算 `content.length`，而图片到 `contentOf` 才展开成
 * `data:image/jpeg;base64,…`，**一张图就有几万～几十万字符却完全不计入预算**，
 * 于是「上下文字符预算」在视觉会话里形同虚设。
 *
 * 单张图最多计 [IMAGE_WEIGHT_CAP]：如实按真实值算的话，一张普通手机照片就能把整段文字历史一次挤光，
 * 那属于"把预算从一个极端推到另一个极端"。这里只求消除"图片零成本"这个漏洞。
 */
internal fun AiClient.weightOf(m: ChatMessage): Int {
    val bytes = m.imageUri?.takeIf { it.isNotBlank() }
        ?.let { runCatching { java.io.File(it).length() }.getOrDefault(0L) } ?: 0L
    val imageWeight = (bytes * 4 / 3).coerceAtMost(IMAGE_WEIGHT_CAP.toLong()).toInt()
    return m.content.length + imageWeight
}

/**
 * [pinFirstUserMessage]＝把**首条用户消息钉住**、不参与裁剪（工具形态）。
 *
 * 这条不是"顺手加的开关"：裁剪是**从最早一条开始丢**的，而工具会话的第一条用户消息往往
 * 就是那份要处理的**长素材**——一旦超预算，先被丢掉的恰好是任务本身，模型于是对着空气输出
 * （这个坑代码里早就存在）。钉住的那条**不占预算**：
 * 它必须留下，所以预算只能花在其余消息上。
 */
fun AiClient.trimHistory(
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
 * 值得明确告诉用户（"不静默截断"）。
 */
fun AiClient.exceedsContextBudget(text: String): Boolean = text.length > CONTEXT_CHAR_BUDGET

/**
 * 只保留**最近一张**图，其余带图消息退化成纯文本（2026-09-16）。
 *
 * 给"一次只吃一张图"的模型用（[ModelCatalog.singleImageOnly]）：不这么做的话，
 * 每轮请求都会把历史里**每一张**图重新 base64 上传一遍——用户只是多聊几句，
 * 请求体就涨到几 MB，而对方模型本来也只能看一张。
 */
internal fun AiClient.collapseToLatestImage(history: List<ChatMessage>): List<ChatMessage> {
    val lastImageAt = history.indexOfLast { !it.imageUri.isNullOrBlank() }
    if (lastImageAt < 0) return history
    return history.mapIndexed { i, m ->
        if (i == lastImageAt || m.imageUri.isNullOrBlank()) m else m.copy(imageUri = null)
    }
}

/** 该请求实际要发的历史：按条数/预算裁剪 + 单图模型只留最近一张图 */
internal fun AiClient.requestHistory(
    history: List<ChatMessage>,
    settings: AiSettings,
    mode: PromptMode = PromptMode.RP
): List<ChatMessage> {
    // 工具形态钉住首条素材：见 trimHistory 的说明
    val trimmed = trimHistory(history, settings.historyLimit, pinFirstUserMessage = mode == PromptMode.TOOL)
    return if (ModelCatalog.singleImageOnly(settings.chatModel)) collapseToLatestImage(trimmed) else trimmed
}

/**
 * 生效温度：引擎级默认**只在用户没动过全局温度**（仍是出厂值）时接管。
 * 用户自己调过就一律听用户的——不拿引擎偏好去覆盖人的显式选择。
 */
internal fun AiClient.effectiveTemperature(settings: AiSettings, spec: EngineSpec): Double {
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
internal fun AiClient.contentOf(m: ChatMessage, settings: AiSettings): kotlinx.serialization.json.JsonElement {
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
 * 把本轮的「硬性要求 / 特别指示」追加到最后一条用户消息末尾。
 *
 * 内容可能是纯文本，也可能是「文字 + 图片」的数组（视觉会话），两种都要照顾到。
 */
internal fun AiClient.withTailHint(
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
suspend fun AiClient.chatStream(
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
    // 装配契约由卡的形态决定：工具卡走 TOOL，陪伴/多线走 RP；风格与历史口径跟着一起分叉
    val spec = Engines.of(card)
    val mode = spec.promptMode
    val body = buildJsonObject {
        put("model", normalizeModel(settings.chatModel))
        if (supportsTemperature(settings.chatBaseUrl, settings.customProviders)) put("temperature", safeTemperature(effectiveTemperature(settings, spec)))
        put("max_tokens", ProviderProfiles.effectiveMaxTokens(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.maxTokens, settings.customProviders))
        // 采样惩罚（实验定稿）：按「模型×思考模式」白名单发，不支持的家不发
        ProviderProfiles.penalties(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.customProviders)?.let { (f, p) ->
            put("frequency_penalty", f)
            put("presence_penalty", p)
        }
        put("stream", true)
        thinkingBody(settings.chatBaseUrl, settings.chatThinking, settings.chatModel, settings.customProviders)?.let { (k, v) -> put(k, v) }
        reasoningSplitBody(settings.chatBaseUrl, settings.customProviders)?.let { (k, v) -> put(k, v) }
        putJsonArray("messages") {
            // 世界书命中判断在这里做**一次**：系统段与 `@depth` 插入共用同一份结果。
            // 扫描的是 `requestHistory` 裁剪后的那批消息 —— 与真正发出去的一致，
            // 而不是"用户界面里看着有那么长"的完整历史。
            val sent = requestHistory(history, settings, mode)
            val bookHits = WorldBookEngine.hits(card.worldBook, card, sent)
            val bookParts = WorldBookEngine.partition(bookHits)
            // 先拼成列表再落 JSON：`@depth` 的条目要插进消息数组**中间**，下标得等整条列表
            // 成型才算得出来（depth 是从最后一条往前数的）。
            val list = ArrayList<Pair<String, kotlinx.serialization.json.JsonElement>>(sent.size + 3)
            list += "system" to kotlinx.serialization.json.JsonPrimitive(
                buildSystemPrompt(card, settings, userSetting, styleHint, memory, summary, mode, bookHits)
            )
            val hintAt = if (tailHint.isNotBlank()) sent.indexOfLast { it.role == "user" } else -1
            sent.forEachIndexed { i, m ->
                val c = contentOf(m, settings)
                list += m.role to (if (i == hintAt) withTailHint(c, tailHint) else c)
            }
            // 历史里没有用户消息（例如对最后一条 assistant 重发）：退化成最末一条 system
            if (tailHint.isNotBlank() && hintAt < 0) {
                list += "system" to kotlinx.serialization.json.JsonPrimitive(tailHint)
            }
            withWorldBookDepth(list, bookParts, card).forEach { (role, content) ->
                addJsonObject {
                    put("role", role)
                    put("content", content)
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

        // ---- 真流式：边到边回调----
        // 以前是 `resp.body.string()` 把整段读完、再解析 SSE 里的增量：
        // 网络其实早就吐完了，界面还在按打字机速率慢放，首字延迟 ≈ 整段生成时间。
        // 现在每读到一行就回调 onDelta —— 模型吐第一个 token 时首字就出现。
        val full = StringBuilder()
        var sawSse = false
        // 思考块（<think>…</think>）既不进界面、也不进聊天记录。
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
internal fun AiClient.extractAssistantContent(text: String): String? = runCatching {
    json.parseToJsonElement(text).jsonObject
        .get("choices")?.jsonArray
        ?.getOrNull(0)?.jsonObject
        ?.get("message")?.jsonObject
        ?.get("content")?.jsonPrimitive?.contentOrNull
}.getOrNull()?.let { ThinkingFilter.strip(it) }
