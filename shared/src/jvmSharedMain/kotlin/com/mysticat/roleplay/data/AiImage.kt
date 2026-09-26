package com.mysticat.roleplay.data

import java.util.Base64
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 生图：请求体按供应商分支、响应里图片的落盘与尺寸换算。门面与公共管道见 `AiClient.kt`。
 */

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

suspend fun AiClient.generateImage(settingsIn: AiSettings, prompt: String, size: String? = null): String =
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
 * 把生图服务返回的**远端图片 URL 下载到本地**再返回本地路径。
 *
 * 以前只有 DashScope 分支这么做，MiniMax 的 `image_urls` 与 OpenAI 兼容的 `data[0].url`
 * 会把远端 URL 原样返回，而它会被写进 `avatarUri / backgroundUri` 并持久化 ——
 * 这类链接大多短期有效（DashScope 官方 24 小时），过期后角色头像/聊天背景就变空白；
 * 若该 URL 需要鉴权头，Coil 裸取还会 401。
 * 下载失败**直接报错让用户重试**，而不是留一个将来必然失效的 URL。
 */
internal suspend fun AiClient.downloadToStorage(imageUrl: String, ext: String): String =
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
internal fun AiClient.dashScopeImageEndpoint(baseUrl: String): String {
    val origin = runCatching {
        val u = java.net.URI(baseUrl.trim())
        "${u.scheme}://${u.host}"
    }.getOrNull() ?: "https://dashscope.aliyuncs.com"
    return "$origin/api/v1/services/aigc/multimodal-generation/generation"
}

/** DashScope 尺寸用星号分隔（"1024*1024"），不是 "1024x1024"；无法解析时返回 null（不发该字段） */
internal fun AiClient.dashScopeSize(size: String): String? {
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
internal fun AiClient.effectiveImageFormat(
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
internal fun AiClient.aspectRatioFor(size: String): String? {
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
