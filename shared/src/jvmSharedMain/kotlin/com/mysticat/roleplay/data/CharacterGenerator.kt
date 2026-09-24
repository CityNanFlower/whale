package com.mysticat.roleplay.data

import com.mysticat.roleplay.data.AiClient.forCreation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 用「创作模型」生成角色卡：让模型输出 JSON，再解析成 CharacterCard。
 *
 * **形态决定要哪些字段**（E4）：陪伴 / 多线要人设那一套；工具卡（`formTag = "tool"`）要的是
 * 任务说明 / 输出格式 / 风格参考 / 使用示例 —— 照角色卡那套要，生成出来的工具卡里
 * `taskBrief` 与 `outputFormat` 都是空的，而这两个字段正是装配器唯一会读的东西（见
 * `AiClient.buildSystemPrompt` 的 TOOL 分支），用户点开就是一张废卡。
 */
object CharacterGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(
        settings: AiSettings,
        prompt: String,
        categoryPrefs: List<String>,
        formTag: String = "",
        /**
         * 读超时（秒）。默认是**创作那一档**的长超时：整卡 JSON 一次吐 8192 max_tokens，
         * 开着深度思考时两三分钟很正常，用对话那档 120 秒会让用户在桌面端等到一句 "timeout"
         * （2026-09-23 反馈）。留参数是为了自检能用一个很短的超时验"超时真会走到友好提示那条路"。
         */
        readTimeoutSeconds: Int = AiClient.CREATION_READ_TIMEOUT_SECONDS
    ): CharacterCard {
        if (prompt.isBlank()) throw IllegalArgumentException("请先描述你想要的角色")
        // 换成创作供应商（URL/Key/模型/思考强度），三个模型页各自独立选供应商
        val creation = settings.forCreation()
        val model = creation.chatModel
        val allCats = CategoryManager.all().joinToString("/")
        val isExperience = formTag == "experience"
        // 工具形态（E4，第 75 轮）：**要的字段与角色卡不是同一套**。工具卡里 persona / scenario / greeting
        // 一个都不参与装配（见 `AiClient.buildSystemPrompt` 的 TOOL 分支：它只读 taskBrief / outputFormat，
        // 风格参考读 description / personality，开场白字段在这里是「使用示例」）。
        // 所以照角色卡那套要，用户拿到的是一张**任务说明与输出格式都空着**的工具卡——点开就是废卡。
        val isTool = formTag == "tool"
        // 玩法形态（E4 拆档，2026-09-23）：与工具一样**不吃角色卡那套字段**，但它要的是另一组 ——
        // 规则（硬约束）、状态项（这一局记什么）、开局引导、每轮几个选项。照角色卡那套要，
        // 生成出来的玩法卡既没有规则（模型于是随手放宽判定）也没有状态项（进度全靠即兴）。
        val isPlay = formTag == "play"
        val sys = buildString {
            if (isTool) {
                append("你是「工具型角色卡」创作专家。这类卡**不扮演角色**：它把模型当成一个完成任务的小工具，")
                append("用户发来待处理的素材或指令，它直接给出一份**可以直接拿走的成品**。\n")
                append("只返回一个 JSON 对象，不要任何解释、Markdown 代码块或多余文字。\n")
                append("JSON 字段（内容全部用中文）：\n")
                append("  name（工具名，例：会议纪要整理）\n")
                append("  tagline（一句话说明它能做什么）\n")
                append("  task_brief（任务说明：要它做什么、拿到素材后怎么处理，2~4 句。")
                append("只写职责与处理方式，不要写「你是一个……」这类扮演设定，也不要编造身世外貌）\n")
                append("  output_format（输出格式要求：成品长什么样，必须写得**可核对**——几条、每行的形状、")
                append("要不要标题与总结。例：每行「- [ ] 事项（负责人）」，不要标题、不要总结段）\n")
                append("  style（风格参考：成品的语气与用词习惯，一句。例：书面、简洁、只用短句）\n")
                append("  style_habit（风格参考：表达习惯，一句。例：直接给结论、不寒暄、不解释自己怎么做的）\n")
                append("  examples（使用示例数组，3 条：每条都是**用户会怎么对它说的一句话任务**，")
                append("不是工具的作答、更不是角色台词。例：「把这段会议记录整理成 5 条待办，每条不超过 15 字」）\n")
                append("  categories（类型/标签数组，从 $allCats 里选 1~3 个）\n")
            } else if (isPlay) {
                append("你是「玩法型角色卡」创作专家。这类卡**不扮演剧情角色**：它陪用户玩**一局**有规则的玩法")
                append("（猜谜、对弈、情景问答、心理测评），规则是硬约束、每轮推进一回合、这一局的进度它自己记。\n")
                append("只返回一个 JSON 对象，不要任何解释、Markdown 代码块或多余文字。\n")
                append("JSON 字段（内容全部用中文）：\n")
                append("  name（玩法名，例：二十问猜人）\n")
                append("  tagline（一句话说明怎么玩）\n")
                append("  play_rules（规则：**写成可判定的硬约束**，每条一个「必须…」或「不可…」，3~6 条。")
                append("要写清：玩家怎么算赢/输、你每轮只做什么、什么行为是违规的。")
                append("例：「只回答是或不是，不可报出具体名字」「答不是时必须排除该候选，此后不再提」）\n")
                append("  play_state（要记的状态项：这一局必须记住什么才能玩下去，2~4 条。")
                append("例：已排除的候选、当前第几问、比分、已确认的结论。只写「记什么」，不要写别的东西）\n")
                append("  opening（开局引导：新会话里你说的第一步，1~2 句，让用户知道怎么开始）\n")
                append("  play_options（每轮给几个编号选项，整数：0 表示自由作答不给选项；否则给 2~5）\n")
                append("  setup（玩法设定：这一局的前提与背景，一句话；不要写剧情开场）\n")
                append("  style（风格参考：主持时的语气与用词习惯，一句。例：轻松、爱吐槽）\n")
                append("  style_habit（风格参考：表达习惯，一句。例：一次只问一句、不解释自己为什么这么问）\n")
                append("  categories（类型/标签数组，从 $allCats 里选 1~3 个）\n")
            } else {
                append("你是角色卡创作专家。请根据用户描述，创作一个用于 AI 角色扮演的角色卡。\n")
                append("只返回一个 JSON 对象，不要任何解释、Markdown 代码块或多余文字。\n")
                append("JSON 字段（内容全部用中文）：\n")
                append("  name（角色名）\n")
                append("  tagline（一句话简介）\n")
                append("  persona（完整人设/性格/背景，一段中文；只描述角色自己，用角色视角或第三人称）\n")
                append("  scenario（世界观/当前场景，一段中文；写角色所处的世界与此刻情境。")
                append("若要交代用户的位置，必须写成「用户/对方」，不要用「我」指用户）\n")
                append("  greeting（开场白，一行，角色第一人称台词）\n")
                if (isExperience) {
                    append("  alternate_openings（额外开场白数组，至少 2 条；每条都是角色第一人称台词，")
                    append("开局方向必须彼此不同——例如不同的时间地点、不同的事件切入、不同的与用户的关系起点；")
                    append("排版：对白直接写普通文本，动作用（）括注）\n")
                }
            }
            append("  categories（类型/标签数组，从 $allCats 里选 1~3 个）\n")
        }
        val user = buildString {
            append(
                when {
                    isTool -> "请创作一个工具（不是角色扮演角色，是干活的工具）："
                    isPlay -> "请创作一局玩法（不是角色扮演角色，是一局可以玩起来的规则玩法）："
                    else -> "请创作一个角色："
                }
            )
            append(prompt.trim())
            if (categoryPrefs.isNotEmpty()) {
                append("\n类型/标签限定为：").append(categoryPrefs.joinToString("、"))
            }
            if (isExperience) {
                append("\n这是「体验向」角色：要有多种开局方向，alternate_openings 里给 2 条以上方向不同的开场白。")
            }
            if (isTool) {
                append("\n工具要处理的东西就是用户发来的素材，不必编造世界观或角色关系；")
                append("examples 写**用户下达的任务**（一句话即可），不是工具的作答。")
            }
            if (isPlay) {
                append("\n这一局必须是**能真的玩起来**的：规则可判定（别写「根据反馈逻辑排除选项」这种空话），")
                append("状态项是玩下去必须记住的东西；opening 只是开局第一步，不要写成剧情开场白。")
            }
        }
        val text = AiClient.chatCompletion(
            // max_tokens / 携带历史条数仅对对话生效：创作整卡 JSON（体验向还有多条开场白）
            // 输出量大、深度思考也吃输出 token，固定给足 8192，不跟随用户设置
            creation.copy(maxTokens = 8192),
            sys,
            listOf(ChatMessage("user", user)),
            readTimeoutSeconds = readTimeoutSeconds
        )
        return parseCard(text, categoryPrefs, formTag)
    }

    private fun parseCard(text: String, fallbackCats: List<String>, formTag: String = ""): CharacterCard {
        val obj = json.parseToJsonElement(extractJson(text)).jsonObject
        fun s(key: String) = jsonText(obj[key])
        val name = s("name").ifBlank { throw IllegalArgumentException("模型未返回角色名") }

        // 优先取模型返回的 categories 数组；取不到再退回旧的单个 category 字段
        val fromArray = arr(obj, "categories").filter { it in CategoryManager.all() }

        val single = s("category").takeIf { it in CategoryManager.all() }
        val finalCats = fromArray
            .ifEmpty { listOfNotNull(single) }
            .ifEmpty { fallbackCats }
            .ifEmpty { listOf("其他") }
            .distinct()

        // 工具形态（E4）：落卡的字段与角色卡不同 —— 这是**装配器真正会读的那几个**（见 parseCard 的注释）
        if (formTag == "tool") {
            val examples = arr(obj, "examples")
            return CharacterCard(
                id = Repository.newId(),
                name = name,
                tagline = s("tagline"),
                // 风格参考（工具形态下 description / personality 就是这个含义，见 Engines.TOOL）
                description = s("style"),
                personality = s("style_habit"),
                taskBrief = s("task_brief"),
                outputFormat = s("output_format"),
                // 使用示例复用开场白字段：工具形态下它们就是「点击即填入输入框」的任务样例
                greeting = examples.firstOrNull().orEmpty(),
                greetings = examples,
                formTag = formTag,
                categories = finalCats
            )
        }

        // 玩法形态（E4 拆档）：落卡的字段是**装配器真正会读的那几个** —— 规则 / 状态项 / 选项数
        // 加风格参考与开局引导（见 `AiClient.buildSystemPrompt` 的 PLAY 分支）。
        if (formTag == "play") {
            return CharacterCard(
                id = Repository.newId(),
                name = name,
                tagline = s("tagline"),
                // 风格参考（玩法形态下 description / personality 就是这个含义，见 Engines.PLAY）
                description = s("style"),
                personality = s("style_habit"),
                playRules = s("play_rules"),
                playState = s("play_state"),
                // 选项数只认 0 / 2~5：模型给了别的数（含"3 个"这种带字的）一律归 0，别把脏值送进提示词
                playOptions = s("play_options").toIntOrNull()?.takeIf { it == 0 || it in 2..5 } ?: 0,
                // 玩法设定：这一局的前提与背景（进系统提示词，不是剧情开场）
                scenario = s("setup"),
                // 开局引导复用开场白字段：新会话里铺成主持方的第一句
                greeting = s("opening"),
                formTag = formTag,
                categories = finalCats
            )
        }

        // 体验向：greeting + alternate_openings 组成完整开场白列表
        val greeting = s("greeting")
        val altOpenings = arr(obj, "alternate_openings")
        val greetings = (listOf(greeting) + altOpenings).filter { it.isNotBlank() }

        return CharacterCard(
            id = Repository.newId(),
            name = name,
            tagline = s("tagline"),
            persona = s("persona"),
            scenario = s("scenario"),
            greeting = greeting,
            greetings = greetings,
            formTag = formTag,
            categories = finalCats
        )
    }

    /**
     * 取某个字段的文本，**形状不照着我们要的来也认**。
     *
     * 为什么要归一：我们要"写成 3~6 条规则 / 2~4 条状态项"，模型就常常直接给一个 JSON 数组
     * （`"play_rules": ["只回答是或不是", "答不是时必须排除该候选"]`）。这时 `jsonPrimitive` 会抛
     * `Element class kotlinx.serialization.json.JsonArray ... is not a JsonPrimitive`——
     * 整张卡生成失败，用户看到的是一个写着这行类名的「出错了」弹窗（2026-09-23 反馈，玩法卡必现）。
     * 数组逐条换行拼成一段文本：规则 / 状态项本来就是"一条一行"读的，装配器要的也是一段文本。
     */
    private fun jsonText(el: JsonElement?): String = when (el) {
        null, is JsonNull -> ""
        is JsonPrimitive -> el.contentOrNull?.trim().orEmpty()
        is JsonArray -> el.map { jsonText(it) }.filter { it.isNotBlank() }.joinToString("\n")
        // JsonObject：没有对应的文本框，当空——宁可让这一栏空着，也别把裸 JSON 塞进卡里
        else -> ""
    }

    /**
     * 取一个"数组字段"，但**标量也认**（模型可能只给一条，就不套数组了）：
     * 数组逐条归一，单个标量当成只有一条的列表。这样"没套数组"不会让整批使用示例悄悄丢掉。
     */
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
