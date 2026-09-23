package com.mysticat.roleplay.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 角色卡导入：支持 Tavern chara_card_v2（{spec,data:{...}}）、朴素平铺，
 * 以及「自定义人设/世界观嵌套」格式（character_name / persona{...} / world_setting{...} / opening_line）。
 */
object CardImport {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 导出为 Tavern chara_card_v2 JSON（与 fromTavernJson 对称）。
     * 鲸鱼特有字段放 data.extensions.whale，其他酒馆系应用会忽略、导回不丢。
     * 头像（标准字段 `data.avatar`）与**两份聊天背景**（自家扩展，chara_card_v2 没有背景字段）
     * 若为本地文件则内嵌 base64。
     */
    fun toTavernJson(card: CharacterCard): String {
        val greetings = card.effectiveGreetings()
        // v2 规范字段各自独立写出去（E1，2026-09-22）。三条口径：
        // ① `description` 是**标准字段**，别家应用只认它 —— 所以老卡（只有合并块 persona）把 persona
        //    写进 description 保住兼容（与改动前一致），新卡写各自的字段。
        // ② 两者的**值**都还要原样往返：persona 有值时另在自家扩展里留一份（见下），导入时优先取它。
        // ③ creator_notes / creator / character_version 是**展示字段**，不进 prompt（规范明令禁止）。
        val data = buildJsonObject {
            put("name", card.name)
            put("description", card.description.ifBlank { card.persona })
            put("personality", card.personality)
            put("scenario", card.scenario)
            put("first_mes", greetings.firstOrNull() ?: "")
            put("mes_example", card.mesExample)
            put("creator_notes", card.creatorNotes.ifBlank { DEFAULT_CREATOR_NOTES })
            put("system_prompt", card.systemPrompt)
            put("post_history_instructions", card.postHistory)
            put("alternate_greetings", JsonArray(greetings.drop(1).map { JsonPrimitive(it) }))
            put("tags", JsonArray(card.categoriesOrDefault().map { JsonPrimitive(it) }))
            put("creator", card.creator.ifBlank { DEFAULT_CREATOR })
            put("character_version", card.characterVersion.ifBlank { DEFAULT_CARD_VERSION })
            put(
                "extensions", buildJsonObject {
                    // **别人的扩展原样写回**（E1）：对方扩展里的数据是人家的，不该因为我们导过一次就没了。
                    // 自家 `whale` 节点最后写，同名键以我们为准。
                    runCatching { json.parseToJsonElement(card.extensionsRaw) as? JsonObject }
                        .getOrNull()?.forEach { (k, v) -> if (k != "whale") put(k, v) }
                    put(
                        "whale", buildJsonObject {
                            put("tagline", card.tagline)
                            put("form_tag", card.formTag)
                            put("categories", JsonArray(card.categoriesOrDefault().map { JsonPrimitive(it) }))
                            // 合并块 persona：老卡的全部内容都在它里面，而它在标准字段里没有对应物
                            // （description 只装得下我们挑出来的那部分）。有值就留一份，导入时原样取回。
                            if (card.persona.isNotBlank()) put("persona", card.persona)
                            // 工具形态（E4）的两栏：RP 卡为空就不写，免得每张卡多两段噪声
                            if (card.taskBrief.isNotBlank()) put("task_brief", card.taskBrief)
                            if (card.outputFormat.isNotBlank()) put("output_format", card.outputFormat)
                            // 玩法形态（E4 拆档）的三项：同理，空的不写；选项数只写非 0 的档位
                            if (card.playRules.isNotBlank()) put("play_rules", card.playRules)
                            if (card.playState.isNotBlank()) put("play_state", card.playState)
                            if (card.playOptions > 0) put("play_options", card.playOptions)
                            // 聊天背景（A 批次后一张卡最多两份）：**不带的话，把手机端导出的卡拿到桌面端
                            // 导入就只剩一张竖图**，而这正是用户 2026-09-18 要的"导到桌面端也能优先适配
                            // 桌面端背景"。空串（明确"不要背景"）不写进文件 —— 导回来仍是 null，语义一致。
                            embedLocalImage(card.backgroundUri)?.let { put("background", it) }
                            embedLocalImage(card.backgroundUriDesktop)?.let { put("background_desktop", it) }
                            // 角色专属音色（E 批次）：**没有就整个节点不写**——每张卡都挂一段空对象
                            // 只是噪声，别的酒馆系应用也不认识它。存的是"听起来会变"的那几项；
                            // 混合音色（2026-09-22 起）也是其中一项：卡可以自己是个混出来的人格。
                            card.voice?.takeIf { !it.isEmpty() }?.let { v ->
                                put(
                                    "voice", buildJsonObject {
                                        put("enabled", v.enabled)
                                        put("provider", v.provider)
                                        put("base_url", v.baseUrl)
                                        put("model", v.model)
                                        put("voice", v.voice)
                                        put("speed", v.speed)
                                        put("pitch", v.pitch)
                                        if (v.mixEnabled) {
                                            put("mix_enabled", true)
                                            put(
                                                "mix_speakers",
                                                JsonArray(v.mixSources().take(3).map { s ->
                                                    buildJsonObject {
                                                        put("voice", s.voice)
                                                        put("factor", s.factor)
                                                    }
                                                })
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            )
            // 头像内嵌（仅本地文件）
            embedLocalImage(card.avatarUri)?.let { put("avatar", it) }
        }
        val root = buildJsonObject {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            put("data", data)
        }
        return json.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * 纯文本形式（人设 + 开场白），给"复制粘贴到别处"用——不追求能被导回来，
     * 所以只保留可读内容，不写字段名、不写转义。空小节整段省略，避免满屏占位。
     */
    fun toPlainText(card: CharacterCard): String {
        val greetings = card.effectiveGreetings()
        return buildString {
            append(card.name)
            val sub = listOfNotNull(
                card.tagline.trim().takeIf { it.isNotBlank() },
                card.formTagLabel() + " · " + card.categoriesOrDefault().joinToString("/")
            )
            append("\n").append(sub.joinToString("  |  ")).append("\n")
            // 角色设定：用 `personaDisplayText()`（分层 or 老合并块，判据与装配器同一套）。
            // ⚠ 漏了这一步，新格式的卡"复制为文本"就是一份没有人设的文本（第 68 轮补）。
            card.personaDisplayText().takeIf { it.isNotBlank() }?.let {
                append("\n【人设】\n").append(it).append("\n")
            }
            if (card.scenario.isNotBlank()) append("\n【世界观 / 当前场景】\n").append(card.scenario.trim()).append("\n")
            if (card.postHistory.isNotBlank()) {
                append("\n【后置指令】\n").append(card.postHistory.trim()).append("\n")
            }
            if (greetings.isNotEmpty()) {
                append("\n【开场白】\n").append(greetings.first()).append("\n")
                if (greetings.size > 1) {
                    append("\n【其他开场白】\n")
                    greetings.drop(1).forEachIndexed { i, g -> append("${i + 1}. ").append(g).append("\n") }
                }
            }
        }.trimEnd() + "\n"
    }

    /**
     * 导入的字节入口（台账 11 PNG 卡）：PNG 优先——签名命中就抽 `chara` tEXt chunk 里的 JSON
     * （见 [PngCardCodec]），抽不到返回 null；不是 PNG 则按 UTF-8 文本（JSON）原样给出去。
     * 两端文件选择器读到的字节都先过这里，导入入口就一张、不用分"选 JSON"还是"选 PNG"。
     *
     * **PNG 立绘当头像**（E1，2026-09-22）：从角色卡站下载来的卡，**图片本身就是立绘**，
     * 而 chunk 里的 JSON 没有 `avatar` 字段 —— 于是导入后是一张没头像的卡（老的"下载来的 ST 卡
     * 导入后没有头像"就是这么来的）。这里把整张 PNG 补成 `data.avatar`（与自家导出同一种
     * data URL 形状），下游 `fromTavernJson` 的解码路径**一行都不用改**就把头像落盘了。
     * 已有 `avatar` 的卡（自家导出的）一个字不动 —— 那张内嵌头像通常是裁过的方形，比整张立绘更合适。
     */
    fun cardJsonFromBytes(bytes: ByteArray): String? {
        if (PngCardCodec.isPng(bytes)) {
            val cardJson = PngCardCodec.extractCardJson(bytes) ?: return null
            return withPngAvatar(cardJson, bytes) ?: cardJson
        }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        return text.takeIf { it.isNotBlank() }
    }

    /** 一张立绘的上限：超过就放弃当头像（避免把 50 MB 的图读进内存再 base64 一次） */
    private const val MAX_PNG_AVATAR_BYTES = 8 * 1024 * 1024

    // 自家导出时那三个展示字段的默认值。导入时**按原值还原成空**，
    // 否则"我们写进去的占位文字"会被当成作者真的填过，每张卡都显示作者 = Whale。
    private const val DEFAULT_CREATOR_NOTES = "Exported from 鲸鱼 (Whale) roleplay"
    private const val DEFAULT_CREATOR = "Whale"
    private const val DEFAULT_CARD_VERSION = "1.0"

    /**
     * 给没有 `avatar` 的 PNG 卡补上"立绘即头像"。任何一步不成立就返回 null（调用方回退原 JSON）——
     * 这条路是**锦上添花**，绝不该让一张本来能导入的卡因为补头像而导不进来。
     */
    private fun withPngAvatar(cardJson: String, png: ByteArray): String? {
        if (png.size > MAX_PNG_AVATAR_BYTES) return null
        val root = runCatching { json.parseToJsonElement(cardJson) as? JsonObject }.getOrNull() ?: return null
        // v2/v3 都在 `data` 下；朴素平铺就写在根上（与 fromTavernJson 同一套判法）
        val holderKey = if (root["data"] is JsonObject) "data" else null
        val holder = holderKey?.let { root[it] as? JsonObject } ?: root
        val existing = (holder["avatar"] as? JsonPrimitive)?.contentOrNull
        if (!existing.isNullOrBlank()) return null
        val avatar = "data:image/png;base64,${java.util.Base64.getEncoder().encodeToString(png)}"
        val patched = JsonObject(holder.toMutableMap().apply { put("avatar", JsonPrimitive(avatar)) })
        val out = if (holderKey != null) JsonObject(root.toMutableMap().apply { put(holderKey, patched) }) else patched
        return runCatching { json.encodeToString(JsonObject.serializer(), out) }.getOrNull()
    }

    fun fromTavernJson(text: String): CharacterCard {
        if (text.isBlank()) throw IllegalArgumentException("文件内容为空")
        val data = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw IllegalArgumentException("不是有效的 JSON 角色卡文件") }
            .let { it["data"]?.jsonObject ?: it }

        fun s(key: String): String {
            val el = data[key] ?: return ""
            return when (el) {
                is JsonPrimitive -> el.contentOrNull?.trim().orEmpty()
                is JsonObject -> el["content"]?.let { c -> if (c is JsonPrimitive) c.contentOrNull.orEmpty() else "" }.orEmpty()
                is JsonArray -> el.joinToString(" ") { if (it is JsonPrimitive) it.contentOrNull.orEmpty() else "" }.trim()
                else -> ""
            }
        }
        fun obj(key: String): JsonObject? = data[key] as? JsonObject

        // 自家扩展 data.extensions.whale：导出时写进去的鲸鱼特有字段，导回来必须认（P1-6）
        val whale = (data["extensions"] as? JsonObject)?.get("whale") as? JsonObject

        /** 把 JsonArray 读成去空格去空串后的字符串列表（非数组或元素非字符串则跳过） */
        fun strArr(el: JsonElement?): List<String> =
            (el as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }.orEmpty()

        fun whaleStr(key: String): String =
            ((whale?.get(key)) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

        val name = s("character_name").ifBlank { s("name") }
            .ifBlank { throw IllegalArgumentException("角色卡里没有找到 name / character_name 字段") }

        // 一句话简介：自家扩展优先且**不截断**（那是我们自己写的长度），
        // 外来卡（one_line_intro / tagline，酒馆系常写成长文本）才按 40 字收一下
        val ownTagline = whaleStr("tagline")
        val tagline = ownTagline.ifBlank {
            s("one_line_intro").ifBlank { s("tagline") }.take(40)
        }

        // 形态标签（陪伴 / 多线）：只有自家扩展里有
        val formTag = whaleStr("form_tag")

        // ── v2 规范字段各自独立落库（E1，2026-09-22）────────────────────────────
        // 改动前这里把 description / personality / mes_example **揉成 persona 一个块**
        // （自己加【背景故事】【性格特点】小标题），于是卡主写的分层信息一进库就没了结构，
        // 装配时也只能整块塞进去。现在三个字段各回各家，由 E2 的分层装配器分别注入。
        //
        // ⚠ 库里**已有的**老卡不受影响：它们的 persona 是当年拼好的文本，正则反向拆会把用户手改过的拆坏，
        // 所以不拆（第 66 轮定稿）——装配口径是"新字段为空时仍整块注入 persona"。
        val legacyPersona = obj("persona")
        val personaBg = (legacyPersona?.get("background") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val personaAppearance = (legacyPersona?.get("appearance") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        // 合并块 persona：**自家扩展里那一份**（新导出的卡都带），或那种老嵌套格式拼出来的。
        // 新卡（有分层字段）这里是空的 —— 那就该是空的，装配器会走分层。
        val ownPersona = whaleStr("persona")
        val descriptionText = buildString {
            // 导出时我们把老卡的 persona 抄进了标准字段 `description`（为了别家应用能读到人设）。
            // 那是一次**兼容副本**，不是卡主写的 description —— 认出来就丢掉，否则老卡"导出再导入"
            // 会凭空多出一个分层字段，口径从老式整块注入变成分层注入（内容虽在，段头层级却变了）。
            val rawDesc = s("description").ifBlank { s("background") }
            val desc = if (ownPersona.isNotBlank() && rawDesc.trim() == ownPersona) "" else rawDesc
            if (desc.isNotBlank()) append(desc)
            if (personaBg.isNotBlank()) append(if (isNotEmpty()) "\n\n" else "").append(personaBg)
            // 「外貌」是那种老嵌套格式的独立键，没有对应的 v2 字段 —— 并进 description 并留个小标题，
            // 免得一段外貌描写失去了它是什么的线索
            if (personaAppearance.isNotBlank()) {
                append(if (isNotEmpty()) "\n\n" else "").append("【外貌】\n").append(personaAppearance)
            }
        }.trim()
        val personalityText = s("personality")
            .ifBlank { (legacyPersona?.get("personality") as? JsonPrimitive)?.contentOrNull?.trim().orEmpty() }
        val mesExampleText = s("mes_example")
        val personaText = ownPersona.ifBlank {
            // 只有**老嵌套格式**（persona{background/personality/appearance}）才拼回合并块 ——
            // 那才是"没有分层字段的年代"的载体。⚠ 新格式的三个字段**绝不能**塞回这里：
            // 否则一张干净的 v2 卡会凭空多出一个 persona 块，装配口径又退回老式整块注入。
            if (legacyPersona == null) ""
            else buildString {
                if (personaBg.isNotBlank()) append("【背景故事】\n").append(personaBg).append("\n\n")
                val per = (legacyPersona["personality"] as? JsonPrimitive)?.contentOrNull
                if (!per.isNullOrBlank()) append("【性格特点】\n").append(per).append("\n\n")
                if (personaAppearance.isNotBlank()) append("【外貌】\n").append(personaAppearance)
            }.trim()
        }

        // 展示字段：唯独我们自家导出的默认值要还原成空，否则每张卡的"作者"栏都会变成「Whale」
        val creatorNotes = s("creator_notes").takeIf { it != DEFAULT_CREATOR_NOTES }.orEmpty()
        val creator = s("creator").takeIf { it != DEFAULT_CREATOR }.orEmpty()
        val characterVersion = s("character_version").takeIf { it != DEFAULT_CARD_VERSION }.orEmpty()

        // 别人的 extensions（`whale` 之外）原样留一份，导出时写回去
        val extensionsRaw = (data["extensions"] as? JsonObject)?.let { obj ->
            JsonObject(obj.toMutableMap().apply { remove("whale") })
        }?.takeIf { it.isNotEmpty() }?.let { json.encodeToString(JsonObject.serializer(), it) }.orEmpty()

        val scenario = buildString {
            val sc = s("scenario")
            if (sc.isNotBlank()) append(sc)
            obj("world_setting")?.let { w ->
                val wv = (w["world_view"] as? JsonPrimitive)?.contentOrNull
                if (!wv.isNullOrBlank()) append(if (isNotEmpty()) "\n\n" else "").append(wv)
                val cs = (w["current_scene"] as? JsonPrimitive)?.contentOrNull
                if (!cs.isNullOrBlank()) append("\n\n【当前场景】\n").append(cs)
            }
        }.trim()

        // 开场白：first_mes 是主开场白、alternate_greetings 是其余（与 toTavernJson 的写法对称，P1-6）
        val greeting = s("first_mes").ifBlank { s("opening_line") }
        val greetings = (listOf(greeting) + strArr(data["alternate_greetings"]))
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()

        val categories = pickCategories(data, whale)

        // 头像：自家导出会把本地头像内嵌成 data URL，导回来要解码落盘（P1-6）
        val avatarUri = decodeDataUrl(data["avatar"])

        // 背景：自家扩展里的两份 data URL（A 批次后一张卡最多两份：手机端 / 桌面端）。
        // 外来卡没有这两个键 → 两张都是 null，与改动前一致。
        val backgroundUri = decodeDataUrl(whale?.get("background"))
        val backgroundUriDesktop = decodeDataUrl(whale?.get("background_desktop"))

        // 角色专属音色（E 批次）：整块读、**一个字都不改写**——本机没有这个家、清单里没有这个音色
        // 都照存不误（用户口径"不好实现就算了"），能不能用由编辑页提示、由 CharacterVoices 在朗读时判定。
        // 外来卡没有这个节点 → null，行为与本轮之前完全一致。
        val voice = (whale?.get("voice") as? JsonObject)?.let { obj ->
            fun vStr(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            // 语速/音高解析不出（外来卡写成字符串或非法值）就退回 1.0：宁可不动听感，也不要写进 0 变成静音
            fun vRate(key: String) = (obj[key] as? JsonPrimitive)?.floatOrNull?.takeIf { it > 0f } ?: 1f
            // 混合音色（2026-09-22）：权重解析不出就退回 0.5（与新建时同一个默认），源为空串的条目丢掉
            val mixSpeakers = (obj["mix_speakers"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = (o["voice"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (id.isBlank()) null
                else MixSpeaker(id, (o["factor"] as? JsonPrimitive)?.floatOrNull?.takeIf { it > 0f } ?: 0.5f)
            }
            CharacterVoice(
                enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
                provider = vStr("provider"),
                baseUrl = vStr("base_url"),
                model = vStr("model"),
                voice = vStr("voice"),
                speed = vRate("speed"),
                pitch = vRate("pitch"),
                mixEnabled = (obj["mix_enabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
                mixSpeakers = mixSpeakers
            ).takeIf { !it.isEmpty() }
        }

        return CharacterCard(
            id = Repository.newId(),
            name = name,
            tagline = tagline,
            persona = personaText,
            scenario = scenario,
            greeting = greeting,
            greetings = greetings,
            formTag = formTag,
            categories = categories,
            avatarUri = avatarUri,
            backgroundUri = backgroundUri,
            backgroundUriDesktop = backgroundUriDesktop,
            voice = voice,
            // v2 规范字段（E1）：各自独立落库 —— 只是"搬进来"，怎么装配由 AiClient 决定
            description = descriptionText,
            personality = personalityText,
            mesExample = mesExampleText,
            creatorNotes = creatorNotes,
            creator = creator,
            characterVersion = characterVersion,
            systemPrompt = s("system_prompt"),
            postHistory = s("post_history_instructions"),
            taskBrief = whaleStr("task_brief"),
            outputFormat = whaleStr("output_format"),
            playRules = whaleStr("play_rules"),
            playState = whaleStr("play_state"),
            // 选项数：只认手册里那几档。别人的卡 / 手改过的 JSON 给了 7、-1、"三个" 这类值一律归 0
            //（不给选项），别把脏值送进提示词（"每轮结尾给 7 个编号选项"是能真的把模型带偏的）
            playOptions = whaleStr("play_options").toIntOrNull()?.takeIf { it in PlayOptionChoices } ?: 0,
            extensionsRaw = extensionsRaw,
            createdAt = System.currentTimeMillis()
        )
    }

    /**
     * 本地图片 → `data:image/png;base64,xxx`；外链（http/https）、空串与读取失败一律返回 null。
     *
     * 三处共用（头像 + 两份背景）：**空串要当"没有"处理**——A 批次里空串是"该端明确不要背景"的哨兵，
     * 把它写成 data URL 会让用户"移除背景"的选择在导出/导入后变成"又回来了"。
     */
    private fun embedLocalImage(uri: String?): String? {
        val path = uri?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: return null
        return runCatching {
            val bytes = java.io.File(path).readBytes()
            val ext = path.substringAfterLast('.', "png").lowercase()
            val mime = if (ext == "jpg" || ext == "jpeg") "image/jpeg" else "image/png"
            "data:$mime;base64,${java.util.Base64.getEncoder().encodeToString(bytes)}"
        }.getOrNull()
    }

    /**
     * 把 `data:image/png;base64,xxx` 形式的内嵌图片解码后落盘，返回本地文件路径
     * （头像与两份聊天背景共用；导入时字段名不同、格式一样）。
     * 非 data URL（例如外链 http 图片）或解码失败时返回 null —— 导入不该因为一张图坏掉而整体失败。
     */
    private fun decodeDataUrl(el: JsonElement?): String? {
        val v = ((el as? JsonPrimitive)?.contentOrNull ?: return null).trim()
        if (!v.startsWith("data:")) return null
        val comma = v.indexOf(',')
        if (comma <= 0) return null
        val header = v.substring(5, comma) // 形如 image/png;base64
        if (!header.contains("base64", ignoreCase = true)) return null
        val mime = header.substringBefore(';').lowercase()
        val ext = when {
            "jpeg" in mime || "jpg" in mime -> "jpg"
            "webp" in mime -> "webp"
            "gif" in mime -> "gif"
            else -> "png"
        }
        return runCatching {
            val bytes = java.util.Base64.getMimeDecoder().decode(v.substring(comma + 1))
            Repository.saveImageBytes(bytes, ext)
        }.getOrNull()
    }

    /**
     * 读取分类（可能为数组或单值）。优先级：自家扩展 `extensions.whale.categories` → 酒馆 `tags`
     * → 旧版单分类字段 `category`；完全不认时兜底「其他」。
     * `tags` 以前被完全忽略，导致自家导出的卡导回来分类全变「其他」（P1-6）。
     */
    private fun pickCategories(data: JsonObject, whale: JsonObject?): List<String> {
        val own = (whale?.get("categories") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }.orEmpty()
        if (own.isNotEmpty()) return own.distinct()

        val tags = (data["tags"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() }.orEmpty()
        if (tags.isNotEmpty()) return tags.distinct()

        val raw = data["category"] ?: return listOf("其他")
        val names = when (raw) {
            is JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }
            is JsonPrimitive -> listOf(raw.contentOrNull.orEmpty()).filter { it.isNotEmpty() }
            else -> emptyList()
        }
        return if (names.isNotEmpty()) names.distinct() else listOf("其他")
    }
}
