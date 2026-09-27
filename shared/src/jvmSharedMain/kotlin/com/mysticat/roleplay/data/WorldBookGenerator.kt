package com.mysticat.roleplay.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 用「创作模型」生成一本世界书：让模型输出 JSON，解析成账号级 [WorldBook]——
 * 保存后进宝库（`worldbooks/<id>.json`），再由各角色卡以 `worldBookId` 引用。
 *
 * 提示词口径有两个关键：
 * ① **关键词必须选"聊天里真的会自然出现的词"**：世界书是字面子串匹配（见 `WorldBookEngine.hits`），
 *    模型造生僻缩写当键，条目就永远是死的。
 * ② **正文要写成"角色当场用得上的具体资料"，不是百科词条**：注入段头明说了"不要复述原文"
 *    （`WorldBookEngine.SECTION_HINT`），通篇风貌介绍等于给模型一段既不许它复述、也没法拿来用的文字。
 *    实测症状是模型转头去现编 NPC 与往事 —— 尤其当**人物没单独成条**时，人名从不成为关键词，
 *    聊天里提到某人也触发不了任何设定，模型只能自己造一个（口径与 `CharacterGenerator` 的"具体优于空泛"同源）。
 */
object WorldBookGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(
        settings: AiSettings,
        prompt: String,
        readTimeoutSeconds: Int = AiClient.CREATION_READ_TIMEOUT_SECONDS
    ): WorldBook {
        if (prompt.isBlank()) throw IllegalArgumentException("请先描述你想要的世界书")
        val creation = settings.forCreation()
        val sys = buildString {
            append("你是「世界书」（lorebook / World Info）创作专家。世界书是一组**关键词触发的背景资料条目**：")
            append("聊天里出现某个关键词，那条的正文才会被注入上下文——平时不占字数。\n")
            append("只返回一个 JSON 对象，不要任何解释、Markdown 代码块或多余文字。\n")
            append("JSON 字段（内容全部用中文）：\n")
            append("  name（世界书名称）\n")
            append("  description（一句话说明这本书记录的是什么世界或主题）\n")
            append("  entries（条目数组，8~15 条），每条：\n")
            append("    name（条目名，例：银月城 / 教廷 / 灵石）\n")
            append("    keys（关键词数组，1~3 个：必须是**聊天里真的会自然出现的词**，不要造生僻缩写）\n")
            append("    content（命中时注入的设定正文，50~150 字。写成**角色当场用得上**的具体资料，")
            append("不是百科词条：人物写他与谁有什么关系、怎么称呼别人、什么话题会让他变脸；")
            append("地点写到了那儿会发生什么、谁在管、有什么禁忌与代价；势力写它要什么、靠什么立身、与谁有恩怨；")
            append("物件写谁能用、怎么得、用了会怎样；规则写怎么运作、违反会怎样、谁执行。")
            append("每条至少给一个**能被人说出口的具体细节**（人名、称谓、地名、规矩、代价）；")
            append("不要写「大陆第一」「公认正统」这类空泛最高级，也不要写成剧情、台词或分点列表）\n")
            append("    constant（是否常驻：每轮都注入，布尔。最多 1 条为 true——通常是世界观总纲，其余全部 false）\n")
            append("覆盖面：地理、势力或组织、**重要人物**、物品或资源、规则与常识各几条。")
            append("其中**人物必须单独成条、2~4 条**，不要把人物塞进势力条目里当一个从句——")
            append("人物的 name 写姓名，keys 放这个名字以及它在聊天里可能被叫到的别称")
            append("（如'剑尊姜寒山'与'姜寒山'）。人物名成为关键词，聊天里提到他时设定才会触发，")
            append("模型也不必现编一个陌生人来填空。\n")
        }
        val text = AiClient.chatCompletion(
            // 整本书 JSON 输出量大、深度思考也吃输出 token，与整卡生成同一档：固定 8192
            creation.copy(maxTokens = 8192),
            sys,
            listOf(ChatMessage("user", "请创作一本世界书：" + prompt.trim())),
            readTimeoutSeconds = readTimeoutSeconds
        )
        return parseBook(text)
    }

    private fun parseBook(text: String): WorldBook {
        val obj = json.parseToJsonElement(extractJson(text)).jsonObject
        fun s(key: String) = jsonText(obj[key])
        val name = s("name")
        val description = s("description")
        val entriesEl = obj["entries"] ?: throw IllegalArgumentException("模型没有返回任何条目")
        val items = if (entriesEl is JsonArray) entriesEl else listOf(entriesEl)
        val entries = items.mapIndexedNotNull { i, el ->
            val e = runCatching { el.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val content = jsonText(e["content"])
            val keys = arr(e, "keys")
            if (content.isBlank() && keys.isEmpty()) return@mapIndexedNotNull null
            WorldBookEntry(
                id = i,
                name = jsonText(e["name"]),
                keys = keys,
                content = content,
                constant = jsonText(e["constant"]).let { it == "true" || it == "是" }
            )
        }.let { list ->
            // 没有关键词又不是常驻的条目永远不会被注入（编辑器里会标红）——生成就别产出这种
            list.filter { it.constant || it.keys.isNotEmpty() }
        }
        if (entries.isEmpty()) throw IllegalArgumentException("模型没有返回任何条目")
        return WorldBook(name = name, description = description, entries = entries)
    }

    /** 与 `CharacterGenerator` 同一套宽容解析：字段形状不照着来也认，别让整本书因一条脏值报废 */
    private fun jsonText(el: JsonElement?): String = when (el) {
        null, is JsonNull -> ""
        is JsonPrimitive -> el.contentOrNull?.trim().orEmpty()
        is JsonArray -> el.map { jsonText(it) }.filter { it.isNotBlank() }.joinToString("\n")
        else -> ""
    }

    private fun arr(obj: kotlinx.serialization.json.JsonObject, key: String): List<String> {
        val el = obj[key] ?: return emptyList()
        val items = if (el is JsonArray) el.map { jsonText(it) } else listOf(jsonText(el))
        return items.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun extractJson(text: String): String {
        val t = text.trim()
        if (t.startsWith("```")) {
            val m = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(t)
            if (m != null) return m.groupValues[1].trim()
        }
        if (t.startsWith("{")) return t
        val m = Regex("\\{.*?\\}", RegexOption.DOT_MATCHES_ALL).find(t)
        return m?.value ?: t
    }
}
