package com.mysticat.roleplay.data

import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * 辅助请求：看图描述、追答建议、生图提示词改写与整卡创作的系统提示词装配。
 * 门面与公共管道见 `AiClient.kt`。
 */

/**
 * 参考图转译：用视觉模型把用户上传的参考图 + 生图意图，转写成一段详细的画图提示词。
 * 返回的提示词直接喂给 /images/generations。
 */
suspend fun AiClient.visionText(settings: AiSettings, userIntent: String, imageUri: String): String =
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
 * **2026-09-16（真 key 实测，同一段真实历史各 8 次）——两处结构性改动**：
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
suspend fun AiClient.suggestReplies(
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
    // 世界书：灵感回复与聊天**同一套资料**——少了它，代笔出来的台词会说出与已确立设定
    // 相冲突的内容（"这是你第一次来这里"而世界书里写着你们是老相识）。
    val sugHistory = requestHistory(history, settings, PromptMode.SUGGEST)
    val sugHits = WorldBookEngine.hits(card.worldBook, card, sugHistory)
    val sys = buildSystemPrompt(
        card, settings, userSetting, memory = memory, summary = summary,
        mode = PromptMode.SUGGEST, worldBookHits = sugHits
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
            // 只带最近若干轮：灵感回复看重"当前情境"，历史太长反而稀释
            // （工具形态下这条路径整个关掉，见 Engines.TOOL.inspiration；这里传 SUGGEST 即"不钉素材"）
            val list = ArrayList<Pair<String, kotlinx.serialization.json.JsonElement>>(sugHistory.size + 3)
            list += "system" to kotlinx.serialization.json.JsonPrimitive(sys)
            sugHistory.forEach { m -> list += m.role to contentOf(m, settings) }
            withWorldBookDepth(list, WorldBookEngine.partition(sugHits), card).forEach { (role, content) ->
                addJsonObject {
                    put("role", role)
                    put("content", content)
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
            // 思考混在前面会把下面的 JSON 解析直接打挂，先剥掉
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
internal fun AiClient.suggestionTask(name: String, count: Int): String = buildString {
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
internal fun AiClient.parseSuggestionList(raw: String): List<String> {
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
internal fun AiClient.cleanupSuggestion(s: String): String =
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
internal fun AiClient.looksBroken(s: String): Boolean {
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
internal fun AiClient.splitMergedSuggestion(item: String): List<String>? {
    if (item.length < 40) return null
    val parts = Regex("""(?<=[？?!！。])\s*(?=（)""").split(item)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val meaningful = parts.count { it.length >= 6 }
    return if (parts.size >= 2 && meaningful >= 2) parts else null
}

/** 解析收尾：统一清洗 + 丢坏输出 + 去重；只剩一条且像"三条连写"时尝试拆开 */
internal fun AiClient.finalizeSuggestions(items: List<String>): List<String> {
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
suspend fun AiClient.expandImagePrompt(settings: AiSettings, brief: String): String =
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
suspend fun AiClient.draftImagePrompt(
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
internal suspend fun AiClient.askCreationModel(settingsIn: AiSettings, system: String, user: String): String =
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
