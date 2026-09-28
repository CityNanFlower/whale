package com.mysticat.roleplay.data

import kotlinx.serialization.json.add

/**
 * 系统提示词装配：角色设定分层注入、世界书命中与 @depth、开场白、玩法要求、宏展开。
 * 门面与公共管道见 `AiClient.kt`。
 */

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
 * 宏展开与排序，必然漂移。
 *
 * 角色设定走**分层 or 老口径**二选一（2026-09-22，判据 [CharacterCard.hasLayeredPersona]）：
 * 新字段有值就分开注入 description / personality / mesExample；全空则把合并块 [CharacterCard.persona]
 * 整块照旧注入 —— 库里已有的老卡因此行为一字不变（不做正则反向拆）。
 */
fun AiClient.buildSystemPrompt(
    card: CharacterCard,
    settings: AiSettings,
    userSetting: String = "",
    styleHint: String = "",
    memory: String = "",
    summary: String = "",
    /** 会话级**动态**处境（一行短句）。空 ＝ 还没整理出来，此时退回卡的 scenario（老会话因此行为不变） */
    situation: String = "",
    /** 玩法形态的**隐藏状态**（谜底 / 待定候选 / 判定策略）：注入但不给用户看，见 [Conversation.secretState] */
    secret: String = "",
    mode: PromptMode = PromptMode.RP,
    worldBookHits: List<WorldBookHit> = emptyList(),
    /**
     * 内容档位：默认＝直白，**不注入任何东西**（与加本机制之前的提示词逐字节一致）；
     * 只在"因内容拒答"下退之后才补一段约束，见 [ContentTier]。
     */
    contentTier: ContentTier = ContentTier.DEFAULT,
    /** 上下文查看器（台账 85）：把段表结果原样递出去 —— 与拼接走**同一次** [systemPromptSegments]，不会有两份 */
    onSegments: (List<Pair<String, String>>) -> Unit = {}
): String = systemPromptSegments(
    PromptContext(
        client = this,
        card = card,
        settings = settings,
        userSetting = userSetting,
        styleHint = styleHint,
        memory = memory,
        summary = summary,
        situation = situation,
        secret = secret,
        mode = mode,
        worldBookHits = worldBookHits,
        contentTier = contentTier
    )
).also { onSegments(it) }.joinToString("") { it.second }

/**
 * 装配一段所需的事实 —— 段表里每一段都**只从这里读**。
 *
 * 收成一个上下文（而不是让每段自己去读卡与设置）有两个好处：段与段之间没有隐式依赖；
 * 也才有可能把"这次到底发了哪几段、每段多长"原样摆给用户看（上下文查看器）。
 */
class PromptContext(
    /** 归属的客户端：宏展开（[AiClient.expandMacros]）挂在它上面 */
    val client: AiClient,
    val card: CharacterCard,
    val settings: AiSettings,
    val userSetting: String = "",
    val styleHint: String = "",
    val memory: String = "",
    val summary: String = "",
    val situation: String = "",
    val secret: String = "",
    val mode: PromptMode = PromptMode.RP,
    val worldBookHits: List<WorldBookHit> = emptyList(),
    val contentTier: ContentTier = ContentTier.DEFAULT
) {
    /**
     * 宏展开：只作用在**从卡里读出来的文本**上。用户自己写的东西
     * （本会话设定 / 全局补充要求 / 记忆 / 前情）不展开 —— 那是人话，不是模板。
     */
    fun m(text: String): String = client.expandMacros(text, card)

    val sug: Boolean = mode == PromptMode.SUGGEST
    val layered: Boolean = card.hasLayeredPersona()
    val engines: EngineSpec = Engines.of(card)

    /**
     * 只有陪伴 / 多线走"角色设定"那一套；工具与玩法的 description / personality 是**风格参考**。
     *
     * 两个判据都要满足，而且各自管一件事：
     * - `mode` 是**本次请求**在做什么（调用方传入，工具会话就是 `TOOL`）——它一直是这一段的主判据；
     * - [EngineSpec.situation] 是**这张卡**属于哪一侧（[Engines.of] 从卡的形态读），
     *   它与「更多」面板给不给处境编辑入口是同一个标志，免得出现"能改但不发"。
     *
     * 取交集而不是二选一：卡没写 `formTag`（老卡 / 自检里的构造）时形态会归到陪伴，
     * 但调用方若明说这次走工具契约，那就不该再发角色设定与世界观——旧行为正是这样。
     */
    val playsRole: Boolean = engines.situation && mode != PromptMode.TOOL && mode != PromptMode.PLAY

    /** 动态处境的去空白形态：[buildSystemPrompt] 的"拆段 or 退回老口径"只看它 */
    val dyn: String = situation.trim()

    /** 世界书命中的三个落点（before_char / after_char / @depth）；本章只装配前两个 */
    val book: WorldBookEngine.WorldBookParts = WorldBookEngine.partition(worldBookHits)
}

/**
 * 系统提示词里的**一段**。
 *
 * [name] **不进提示词**：它是给自检与上下文查看器用的"这一段叫什么"，
 * 让"哪一段长出来了 / 哪一段没了"有个名字可指，而不是只能比对整篇字符串。
 *
 * [enabled] ＝ 这一段在什么条件下出现（形态、字段有没有值），[render] 返回 null 或空串
 * ＝ 这一次不出现 —— 两者合起来正好是过去那串 `if (…) append(…)`。
 */
class PromptSegment(
    val name: String,
    private val enabled: (PromptContext) -> Boolean = { true },
    val render: (PromptContext) -> String?
) {
    /** 这一段这一次该不该出现（形态、字段有没有值）；[enabled] 只管条件、不管文案 */
    fun enabledIn(ctx: PromptContext): Boolean = enabled(ctx)
}

/**
 * **段表**：系统提示词由哪些段、按什么顺序拼出来。
 *
 * 表内顺序**就是**段序（也就是"插入位"）—— `before_char` 的世界书排在角色设定之前、
 * `after_char` 的排在其后，靠的就是它们俩在表里的位置，不需要额外声明锚点。
 * 加一个段＝在表里加一行，而不是钻进一长串 `if/append` 里找地方插
 * （改表前就是那样：四种形态的契约、工具块、玩法块、角色设定、世界观、处境……各管一段，散在两百行里）。
 * 将来故事形态要加的【章节】这类段，也是在这里加一行。
 *
 * ⚠ **段表只管"段序与开关"，不管文案**：改措辞是改 [PromptContext] 的 render 实现，
 * 不要顺手调整表内顺序 —— 顺序即优先级（越靠后越贴近生成点，近因更强），
 * 挪一行就是一次行为变更，得有理由。
 *
 * `@depth` 的世界书条目**不在表里**：它们要插进消息数组（见 [withWorldBookDepth]），
 * 那是"贴近生成点"的另一种实现，跟系统提示词内部的段序无关。
 */
val PROMPT_SEGMENTS: List<PromptSegment> = listOf(
    PromptSegment("形态契约") { it.contract() },
    PromptSegment("世界书·角色设定之前") { it.worldBookBeforeChar() },
    PromptSegment("工具：任务说明与输出格式", enabled = { it.mode == PromptMode.TOOL }) { it.toolBlock() },
    PromptSegment("玩法：规则/状态/设定", enabled = { it.mode == PromptMode.PLAY }) { it.playBlock() },
    PromptSegment("角色设定", enabled = { it.playsRole }) { it.personaBlock() },
    PromptSegment("世界观", enabled = { it.playsRole }) { it.scenarioBlock() },
    PromptSegment("当前处境", enabled = { it.playsRole }) { it.situationBlock() },
    PromptSegment("对话示例", enabled = { it.playsRole }) { it.exampleBlock() },
    PromptSegment("世界书·角色设定之后") { it.worldBookAfterChar() },
    PromptSegment("用户的设定") { it.userSettingBlock() },
    PromptSegment("前情提要") { it.summaryBlock() },
    PromptSegment("记忆/进度") { it.memoryBlock() },
    PromptSegment("隐藏状态", enabled = { it.mode == PromptMode.PLAY }) { it.secretBlock() },
    PromptSegment("补充要求") { it.extraBlock() },
    PromptSegment("叙事风格") { it.styleBlock() },
    // 排在最后＝近因最强：档位是"模型上一轮没肯按这个写法答"之后才补的约束，
    // 它必须压得住【叙事风格】里"高甜 / 丰沛"那类推高尺度的要求
    PromptSegment("内容档位") { it.contentTierBlock() }
)

/**
 * 按段表装配，返回 **(段名, 段正文)** 的有序清单，这次不出现的段直接跳过。
 *
 * **唯一入口**：装配（[buildSystemPrompt]）与"这次发了哪几段"的展示走同一个函数，
 * 否则查看器算一份、真正发出去的是另一份，就成了查不出来的假账
 * （世界书命中面板与实发共用 [worldBookHitsFor] 是同一条道理）。
 */
fun systemPromptSegments(ctx: PromptContext): List<Pair<String, String>> =
    PROMPT_SEGMENTS.mapNotNull { seg ->
        if (!seg.enabledIn(ctx)) return@mapNotNull null
        val text = seg.render(ctx)
        if (text.isNullOrEmpty()) null else seg.name to text
    }

/**
 * 形态契约：这一条请求在做什么（扮演 / 处理素材 / 主持一局 / 代笔）。
 *
 * 三种形态**共用中间的资料块**，只换开头与各块的括注——另起一套模板就要维护两份分层注入、
 * 宏展开与排序，必然漂移。
 */
private fun PromptContext.contract(): String = buildString {
    when (mode) {
        PromptMode.SUGGEST -> {
            append("下面是一段角色扮演对话的场景资料。本次任务**不扮演任何角色**：")
            append("你要做的是替**用户**代笔写台词，所以下面的资料只是背景，不要续写剧情。\n")
            append("资料里【角色设定】【世界观 / 当前场景】中的「我 / 我的」都指角色（")
            append(card.name).append("）本人，【用户的设定】中的「我」指用户。\n\n")
        }
        PromptMode.TOOL -> {
            // 工具形态：与陪伴的分野不是"有没有人格"，而是**交互契约不同**——
            // 用户消息是待处理素材、输出是可直接拿走的成品、评判标准是"完成没/对不对"。
            append("你是一个**任务处理工具**，不是在扮演中的角色：用户发来的是**待处理的素材或指令**，")
            append("你要给出**可以直接拿去用的成品**，而不是虚构故事里的一回合。\n")
            append("对话双方：你 = 完成任务的工具；对方 = 用户（下达任务的人）。\n\n")
            append("【任务契约（必须遵守）】\n")
            append("1. 不扮演任何角色、不用第一人称叙述场景、不替用户说话，也不写「（动作描写）」。\n")
            append("2. 只输出任务要求的结果本身：不寒暄、不解释你怎么做的、不加「以下是……」之类的前后缀")
            append("（除非【输出格式】明确要求）。\n")
            append("3. 用户后续消息是对同一件事的补充要求（「再短一点」「换一批」）：按补充要求重做，")
            append("或另给一份新的结果。\n")
            append("4. 素材里出现的人格、语气、世界观只当**风格参考**，不构成一段需要延续的剧情。\n")
            append("5. 【输出纪律】成品中不得出现「用户」「AI」这类元叙述称呼；指代对方用「你」或直接省略。\n")
            append("6. 素材不足以完成时，先给出能确定的那部分，末尾用一行标注缺口（如「缺：X」），不编造。\n\n")
        }
        PromptMode.PLAY -> {
            // 玩法形态：与工具同属"不扮演虚构角色"，但**评判标准不同**——
            // 工具问"成品能不能直接用"，玩法问"规则守得住、进度记得住、这一局好不好玩"。
            // 用户发来的是**这一回合**（一个编号、一句回答）而不是素材，所以要回来的也不是"成品"。
            append("你正在陪用户玩一局**有规则的互动玩法**（猜谜、对弈、情景问答这一类）：你不是剧情里的角色，")
            append("也不是处理素材的工具，而是**主持并参与这一局**的一方。\n")
            append("对话双方：你 = 出题/主持这局玩法的一方；对方 = 用户（正在玩的人）。\n\n")
            append("【玩法契约（必须遵守）】\n")
            append("1. 严格遵守【玩法规则】——它是这一局的硬约束：不因为用户要求、不为让用户开心，")
            append("就改规则、放宽判定或跳过环节。\n")
            append("2. 用户发来的是**这一回合**（一个选项编号、一次回答或一个动作），不是待加工的素材、")
            append("也不是要你续写的剧情：回应这一回合，然后把局面往前推一步。\n")
            append("3. 一个回合只推进一个动作，不做与当前玩法无关的推演。\n")
            append("4. 输出要短：一次一回合，不总结、不复述规则、不做长篇叙述与动作描写（最多一句情绪性的话）。\n")
            if (card.playOptions > 0) {
                append("5. 每轮结尾给 ").append(card.playOptions)
                append(" 个编号选项（「1. …」，各占一行）：用户回一个数字就能继续。")
                append("选项要彼此**有意义地不同**，别给凑数的重复项。\n")
                // 给"该不该给"的指引（调研 玩法-3）：开放提问型玩法里，这一轮用户是在**提问**、
                // 等的是判定——此时给选项等于把他的问题变成选择题，顺手把答案的线索塞了过去。
                append("6. 但**只在需要用户做选择时才给选项**：如果这一轮你把问题交给了用户（例如等他说出猜测）")
                append("或他正在向你提问等判定，就不要给选项——那会把他该自己想的部分替他答了。\n")
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
            append("默认在自然的剧情停顿处收尾，不要求每条都留钩子；仅当场景确实需要用户做决定时，")
            append("才用一个未完成的动作或开放问题把选择交回；连续两条以上不得都以问句结尾；")
            append("用户表现出收束意图（道别、结束、总结）时应当收束，不得新起话题；")
            append("结尾优先落在动作或对白上，不用总结句。")
            append("长度与篇幅默认贴合对话，【叙事风格】另有要求时以其为准。\n")
            append("5. 排版：对白直接写普通文本，不要用「」或引号包裹；动作、神态、心理等描写用（）括注；")
            append("重点词语可少量 **加粗**；不要输出列表、标题等 markdown 结构。\n\n")
        }
    }
}

// ── 世界书·角色设定**之前**那一段────────────────────────────────
// ST 的 `position: before_char`：资料排在角色定义之前（先给世界，再说人）。
// 命中判断**不在这里**做 —— 它要读最近几条消息，而这里只看得到卡的设定；
// 调用方（chatStream / suggestReplies）算好后传进来，笔记本式的记账与「本轮注入」面板共用同一份结果。
// 段头（【世界书】＋"当作你已经知道、不要复述"那一句）挂在**这一处**。
private fun PromptContext.worldBookBeforeChar(): String? = client.worldBookBlock(book.before, card)

// ── 世界书·角色设定**之后**那一段（ST `position: after_char`）─────────
// 排在角色定义（含世界观与对话示例）之后、用户设定之前：这是 ST 的 after_char 位，
// 也是"世界资料已经交代完、接下来是用户这一侧"的自然分界。
//
// 段头只在**两处都没段头**时补（＝前面那处没有条目）：两处都有条目时它会被原样拼第二遍，
// 模型收到的是同一句"这是背景资料、不要复述"说了两次 —— 既挤注意力，又把"这是两本书"的错觉带进来。
private fun PromptContext.worldBookAfterChar(): String? =
    client.worldBookBlock(book.after, card, withHeader = book.before.isEmpty())

/**
 * 工具形态专属块：任务说明 / 输出格式 / 风格参考。
 *
 * 不复用 persona / scenario：编辑器标签语义不对（工具作者看到"人设/世界观"会困惑），
 * 而 persona 在以"这是你本人的设定"注入时会**主动诱发扮演**。
 */
private fun PromptContext.toolBlock(): String? {
    if (mode != PromptMode.TOOL) return null
    return buildString {
        val brief = m(card.taskBrief).trim()
        if (brief.isNotBlank()) append("【任务说明】\n").append(brief).append("\n\n")
        val fmt = m(card.outputFormat).trim()
        append("【输出格式】\n")
        if (fmt.isNotBlank()) append(fmt).append("\n")
        append("硬性要求：只给出结果本身，不寒暄、不解释、不重复用户的素材、不加任何前后缀。\n\n")
        // 人格在这里降级为**风格滤镜**：说明它是风格参考，而不是"你是这个人"
        val styleRef = listOf(m(card.description).trim(), m(card.personality).trim())
            .filter { it.isNotBlank() }.joinToString("\n")
        if (styleRef.isNotBlank()) {
            append("【风格参考】（只借用它的语气与用词习惯，不要扮演其中的角色、不要续写它的剧情）\n")
            append(styleRef).append("\n\n")
        }
    }
}

/**
 * 玩法形态专属块：规则（硬约束，玩法唯一的"对错来源"）、状态项（这一局要记什么）、
 * 玩法设定（背景前提）、风格参考。
 *
 * 同样都不复用 persona / scenario 的原本语义：那两块是以"这是你本人的设定"注入的，会主动诱发扮演，
 * 而玩法型的 description / personality 在这里只是**风格参考**（同工具）。
 */
private fun PromptContext.playBlock(): String? {
    if (mode != PromptMode.PLAY) return null
    return buildString {
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
}

/**
 * 角色设定：分层 or 老口径，判据 [CharacterCard.hasLayeredPersona]。
 *
 * 新字段有值就分开注入 description / personality / mesExample；全空则把合并块 [CharacterCard.persona]
 * 整块照旧注入 —— 库里已有的老卡因此行为一字不变（不做正则反向拆）。
 * 分开写而不是揉成一段：description 管事实、personality 管气质，混在一起模型会互相复读（调研结论）。
 */
private fun PromptContext.personaBlock(): String? = buildString {
    if (layered) {
        val desc = m(card.description).trim()
        val pers = m(card.personality).trim()
        if (desc.isNotBlank() || pers.isNotBlank()) {
            append("【角色设定】").append(if (sug) "（角色本身的设定，「我」= 角色）" else "（你本人）").append("\n")
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

/**
 * 世界观（静态）。
 *
 * `card.scenario` 是**静态的开局设定**，而"此刻在哪、到哪一步了"每轮都在变（见 [situationBlock]）。
 * 过去两者共用一段、每轮原样重发，模型只能靠猜维持时空连续性——表现就是告别循环、时序矛盾
 * 与场景重述（剧情被反复拉回开场）。（调研 P0-1，治 A1）
 * 卡侧字段零改动：导入导出与编辑器一概不动，变的只是装配。
 * 拆段只在**有动态处境**时生效：`situation` 为空（还没整理出过 / 老会话）时完全退回原口径，
 * 只发一段【世界观 / 当前场景】。
 */
private fun PromptContext.scenarioBlock(): String? {
    if (card.scenario.isBlank()) return null
    return buildString {
        if (dyn.isBlank()) {
            append("【世界观 / 当前场景】")
                .append(if (sug) "（里面的「我」= 角色）" else "（以你自己的视角书写，里面的「我」= 你）")
                .append("\n").append(m(card.scenario).trim()).append("\n\n")
        } else {
            append("【世界观】")
                .append(
                    if (sug) "（静态设定与开局背景，「我」= 角色；剧情推进见【当前处境】）"
                    else "（静态设定与开局背景；以你自己的视角书写，里面的「我」= 你）"
                )
                .append("\n").append(m(card.scenario).trim()).append("\n\n")
        }
    }
}

/**
 * 当前处境（动态）：排在【世界观】**之后**——离生成点更近、近因更强。
 *
 * 与静态世界观共用一段时，模型会把"开场那一刻"当成现状。单独一段、且明确要求"以它为准"，
 * 才能压住"退回开场、重新交代场景"。
 */
private fun PromptContext.situationBlock(): String? {
    if (dyn.isBlank()) return null
    return buildString {
        append("【当前处境】（此刻的位置、时间、正在做的事与进行到哪一步；**以它为准**——")
            .append("剧情已经推进到这里了，不要退回开场那一刻，也不要重新交代已经交代过的场景）\n")
            .append(dyn).append("\n\n")
    }
}

/**
 * 对话示例。位置在场景之后：它是**语气示范**，离生成点近一些；同时明确"这是示范，不是剧情"，
 * 否则模型会把示例里的情节当成已经发生过的事接着往下写（社区最常见的烂卡症状之一）。
 */
private fun PromptContext.exampleBlock(): String? {
    val ex = m(card.mesExample).trim()
    if (ex.isBlank()) return null
    return buildString {
        append("【对话示例】（照着这个语气、节奏与长短来回复；示例只是示范，其中的情节没有发生过）\n")
        append(ex).append("\n\n")
    }
}

/**
 * 用户的设定。用户一个字没写时也要**明确交代"没给设定"**，否则模型会自己替用户编身份；
 * 工具形态**不加**这段：它会把用户发来的任务素材误读成"交互关系设定"。
 */
private fun PromptContext.userSettingBlock(): String? {
    if (userSetting.isNotBlank()) {
        return buildString {
            append("【用户的设定】")
                .append(if (sug) "（只描述用户自己，里面的「我」= 用户）" else "（只描述用户自己，不适用于你）")
                .append("\n").append(userSetting.trim()).append("\n\n")
        }
    }
    if (mode != PromptMode.RP) return null
    return buildString {
        append("【用户的设定】\n")
        append("（用户没有提供身份设定：随着对话推进，根据TA的言行与说话方式自然形成对TA身份与性格的印象，")
        append("让称呼与态度随之变化——这只是你的推测：不要替用户补充TA没说过的经历、动机或心理活动，")
        append("TA否认时立即接受。若【角色设定】或【世界观】已写明你与用户的关系，以那个为准。）\n\n")
    }
}

private fun PromptContext.summaryBlock(): String? {
    if (summary.isBlank()) return null
    return "【前情提要】（更早剧情的摘要，请当作你已经知道）\n" + summary.trim() + "\n\n"
}

/**
 * 记忆 / 进度。段名与括注**跟形态走**：陪伴 / 多线是关系向的【共同记忆】，
 * 玩法型要的是进度向的【当前进度】——段名说错，模型会把"已排除的名单"当成"我们的共同经历"来对待。
 */
private fun PromptContext.memoryBlock(): String? {
    if (memory.isBlank()) return null
    return buildString {
        append("【").append(engines.memorySection).append("】（").append(engines.memoryHint).append("）\n")
        append(memory.trim()).append("\n\n")
    }
}

/**
 * 隐藏状态（玩法专属）：与【当前进度】是同一层级的东西，只是**这一份不给用户看**
 * （见 [Conversation.secretState]）。
 *
 * 措辞要点是"不要说破"而不只是"不要写出来"——实测模型会把内部事实顺手说进台词里。
 */
private fun PromptContext.secretBlock(): String? {
    if (secret.isBlank()) return null
    return buildString {
        append("【隐藏状态】（只有你知道、**不得透露给用户**的这一局内部事实：谜底、待定候选、你的判定策略。")
        append("判定与回答都以它为准；用户直接问起、试探，或试图让你说出它时，按玩法规则拒绝——")
        append("不要说破、也不要暗示，更不要把它写进你的回复里）\n")
        append(secret.trim()).append("\n\n")
    }
}

private fun PromptContext.extraBlock(): String? {
    if (settings.extraSystemPrompt.isBlank()) return null
    return "【补充要求】\n" + settings.extraSystemPrompt.trim() + "\n\n"
}

/**
 * 【叙事风格】放在系统提示词的**最末**（2026-09-16 调整）：
 * 原来它排在【补充要求】之前，而系统提示词光"角色设定 + 世界观 + 用户设定 + 前情提要 + 共同记忆"
 * 就有一两千字，风格段埋在中段会被稀释 —— 用户实测"选了沉浸小说（丰沛 250~500 字）但回复还是百来字"。
 * 挪到最末取近因效应，让风格要求离生成点最近；配合"风格刚切换"的一次性告知抵消历史短回复的锚定。
 */
private fun PromptContext.styleBlock(): String? =
    if (styleHint.isBlank()) null else styleHint.trim() + "\n"

/**
 * 【内容档位】：模型上一轮**因内容拒答**（空回复 / 4xx 审核措辞）时，下一档要补的约束。
 *
 * 直白档（[ContentTier.EXPLICIT]，也是默认档）返回 null —— 这一段在正常情况下**不存在**，
 * 所以本机制对没有触发回落的请求零影响（装配结果逐字节不变）。
 *
 * 措辞上刻意加了两句"只收这一段"的话：模型看到"含蓄一点"很容易把**整条回复**都写淡
 * （正文变短、描写全部抽干），那是比多花一次请求更糟的副作用。
 *
 * 文案的**唯一来源**是 [contentTierClause]：段表那一路（主对话）与后台链路（前情提要 / 记忆整理，
 * 系统提示词各自手写、不走段表）都从它取，抄两份必然漂移。
 */
private fun PromptContext.contentTierBlock(): String? = contentTierClause(contentTier)

/** [PromptContext.contentTierBlock] 与后台链路共用的档位文案；直白档为 null（＝不加任何东西） */
fun contentTierClause(tier: ContentTier): String? = when (tier) {
    ContentTier.EXPLICIT -> null
    ContentTier.SUGGESTIVE ->
        "【内容档位】\n" +
            "这一轮如果写到亲密、血腥这类敏感场面，用**暗示与留白**：写到关键处转场，" +
            "或用情绪、对话、环境带过，不直接描写身体细节与具体动作。\n" +
            "**只收这一段**——其他部分照常写足，不要因为这一条就把整条回复写短、写淡。\n\n"
    ContentTier.FADE ->
        "【内容档位】\n" +
            "这一轮如果写到亲密、血腥这类敏感场面，**直接淡出**：只交代「发生过」与结果" +
            "（前后情绪、关系变化），过程一句带过，不写细节。\n" +
            "**只收这一段**——其他部分照常写足，不要因为这一条就把整条回复写短、写淡。\n\n"
}

/**
 * 把【内容档位】追加到**任意**系统提示词末尾 —— 后台链路（前情提要 / 记忆整理）的口子。
 *
 * 它们的系统提示词是"记忆管理器 / 进度管理器"那种手写文案，不经过段表，所以不能靠
 * [buildSystemPrompt] 补这一档；共用 [contentTierClause] 就保证了两条路发出去的是同一段话。
 * 直白档原样返回（逐字节不变）；回落档**追加在末尾**——与段表同一条口径：近因最强。
 */
fun String.withContentTier(tier: ContentTier): String =
    contentTierClause(tier)?.let { this + it } ?: this

/**
 * 本轮该注入哪些世界书条目 —— **唯一入口**。
 *
 * 装配器（[chatStream] / [suggestReplies]）与命中面板（`WorldBookHitsDialog`）必须走同一个函数：
 * 面板回答的是"此刻再发一条会注入什么"，它若自己算一份，就会出现"面板显示中了三条、请求里一个字都没有"
 * 这种查不出来的假账（形态开关见 [EngineSpec.worldBook]：工具形态不注入）。
 */
fun AiClient.worldBookHitsFor(card: CharacterCard, messages: List<ChatMessage>): List<WorldBookHit> =
    if (Engines.of(card).worldBook) WorldBookEngine.hits(card.worldBook, card, messages) else emptyList()

/**
 * 把一组世界书条目拼成一段（宏已展开）；没有条目返回 null。
 *
 * **段头是必要的**：书里的正文是作者写给模型看的资料（真卡实测是
 * `{{user}}: "What is Eldoria?"` 这种问答体），不声明"这是资料、不要复述"，
 * 模型很容易把那段问答当成"刚刚发生过的对话"照抄一遍。
 *
 * [withHeader] = false 时只出正文：系统提示词里前后两处世界书段共用**一个**段头
 * （见 [worldBookAfterChar]）。`@depth` 的插入是另一个落点、离系统提示词很远，一律自带段头。
 */
internal fun AiClient.worldBookBlock(
    entries: List<WorldBookEntry>,
    card: CharacterCard,
    withHeader: Boolean = true
): String? {
    if (entries.isEmpty()) return null
    return buildString {
        if (withHeader) {
            append("【").append(WorldBookEngine.SECTION).append("】")
                .append(WorldBookEngine.SECTION_HINT).append("\n")
        }
        entries.forEach { append(expandMacros(it.content, card).trim()).append("\n\n") }
    }
}

/**
 * 把 `@depth` 的世界书条目插进消息数组。
 *
 * ST 的语义：`depth = D` 表示**从最后一条消息往前数第 D 条之前**（D=0 即追加到最后）。
 * 这里两条约束：
 * 1. 不能插到下标 0（那是系统提示词本身）之前 —— 钳到 1，最坏情况退化成"紧跟在系统提示词后"；
 * 2. 多个深度同时存在时**从后往前插**（先算好的下标才不会被后续插入顶偏）。
 *
 * 为什么不放进系统提示词：放进系统提示词就等于"离生成点几千字"——而 ST 设这个位置的用意
 * 恰恰是"贴近生成点"。实测口径见 `docs/鲸鱼-提示词与上下文总览.md`（风格段挪到最末那次的同一条理由）。
 */
internal fun AiClient.withWorldBookDepth(
    messages: List<Pair<String, kotlinx.serialization.json.JsonElement>>,
    parts: WorldBookEngine.WorldBookParts,
    card: CharacterCard
): List<Pair<String, kotlinx.serialization.json.JsonElement>> {
    if (parts.depth.isEmpty()) return messages
    val list = ArrayList(messages)
    val inserts = ArrayList<Pair<Int, kotlinx.serialization.json.JsonElement>>()
    parts.depth.forEach { (depth, entries) ->
        val block = worldBookBlock(entries, card) ?: return@forEach
        inserts += WorldBookEngine.depthInsertIndex(list.size, depth) to
            kotlinx.serialization.json.JsonPrimitive(block)
    }
    inserts.sortedByDescending { it.first }.forEach { (at, el) -> list.add(at, "system" to el) }
    return list
}

/**
 * 新会话要预置的消息。
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
 * 落库的是**展开过宏**的成品文本：聊天页与气泡因此不会显示生宏。
 */
fun AiClient.seedOpening(card: CharacterCard?, engine: EngineSpec): List<ChatMessage> {
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
 * 五条对应实测过的五个失败点：放宽判定 / 把已否掉的答案再报一遍 / 一回合铺太长 /
 * 开放提问时乱给选项（等于递提示）/ 一局结束后仍按旧进度接着玩。
 */
fun AiClient.playTurnRequirement(card: CharacterCard?): String = buildString {
    append("【玩法硬性要求（这一条必须满足）】\n")
    append("1. 按卡里的玩法规则走，不因为用户催促或抱怨就放宽判定、改规则或跳过环节。\n")
    append("2. 只推进这一回合：回复要短，不复述规则、不写长段叙述、不做总结。\n")
    append("3. 有候选或可能性时先在内部排除，不要把已经否掉的答案再报一遍。\n")
    val opts = card?.playOptions ?: 0
    if (opts > 0) {
        append("4. 结尾给 ").append(opts).append(" 个编号选项（「1. …」各占一行），彼此有意义地不同；")
        append("但用户正在提问、等你判定时不要给选项（那是他该自己想的部分）。\n")
    }
    append("5. 这一局分出胜负或走到终点时，用一句话宣布结果并问要不要再来一局；")
    append("之后**不要再按旧进度接着玩**——用户说重开就是全新一局，从规则的第一步重来。\n")
}

/**
 * 记忆整理任务的系统提示词 —— **纯函数**。
 *
 * 收出来的理由与 [playTurnRequirement] / [tailHint] 相同：它是**发出去的提示词的一部分**，
 * 却一直锁在 `ChatViewModel.maybeUpdateMemory` 里（私有方法），自检与实验台都拿不到，
 * 于是"改了 App 而实验台没跟上"这种事没人能发现。
 *
 * 两个形态的分野在**段名**上：陪伴/多线要的是关系向的【共同记忆】，玩法要的是进度向的【当前进度】
 * 外加**用户看不到**的【隐藏状态】。三个段头必须原样写出，产出才切得开（见 [parseMemoryUpdate]）。
 */
fun memorySystemPrompt(play: Boolean): String {
    val heading = if (play) "当前进度" else "共同记忆"
    val sit = "【当前处境】\n用一行短句写\"此刻在哪、什么时候、正在做什么、进行到哪一步\""
    return if (play) {
        "你是进度管理器。把【现有状态】与【新对话】合并，按下面三个段头分段输出：\n" +
            sit + "（这一局玩到哪一步了）。只写时空与进度，不写剧情细节。\n" +
            "【$heading】\n" +
            "要点式，每行一条（**这一份用户看得到、也能自己改**）：规则判定结果、已经确认或排除的结论、" +
            "计分、进行到第几步；不要记寒暄、吐槽与情绪，也不要记卡里已有的规则与设定（AI 本来就知道）；" +
            "被新对话推翻的条目要删掉或改写（例如某候选从「待定」变成「已排除」），合并重复、补上新结论；" +
            "300 字以内。\n" +
            "【隐藏状态】\n" +
            "要点式（**这一份用户看不到**）：这一局的内部真相——谜底/答案本身、你还没说出口的待定候选与" +
            "排除过程、你的判定策略。**谜底与答案一律写在这里**，不要写进【$heading】。\n" +
            "三个段头都必须原样写出来。只输出内容，不要解释。"
    } else {
        "你是记忆管理器。把【现有状态】与【新对话】合并，按下面两个段头分段输出：\n" +
            sit + "（例：深夜的茶馆二楼，刚把断剑交还，正等他解释来历）。只写时空与进度，不写剧情细节。\n" +
            "【$heading】\n" +
            "要点式，每行一条：只记录聊天中产生的新信息：事实、约定、关系变化、用户偏好、重要转折；\n" +
            "不要包含角色卡里已有的基础设定（人设、世界观、场景）——那些 AI 本来就知道，写进来纯属浪费；\n" +
            "删除已被新对话推翻或过时的条目，合并重复，补上新事实；\n" +
            "只收对话中双方明确说出或确认的内容；角色自述的来历、身世、往事若没有对话佐证，" +
            "整行不写——也不要写「用户已指出/已纠错」这类没有发生过的事；\n" +
            "出现新的专有名词（人名/地名/组织）而设定资料里没有对应条目时，记成「待确认：X」；\n" +
            "保持正常要点行，不要用「［…］」批注体；400 字以内。\n" +
            "两个段头都必须原样写出来。只输出内容，不要解释。"
    }
}

/** 前情提要任务的系统提示词（就一句话，但它是发出去的提示词的一部分，同 [memorySystemPrompt]） */
const val SUMMARY_SYSTEM_PROMPT: String =
    "把【已有前情提要】和【新剧情片段】合并成一份简洁的前情提要：要点式、300 字以内、" +
        "保留关键事件/人物关系变化/约定。只输出提要本身。"

/**
 * 贴着生成点的「本轮硬性要求（tailHint）」全文 —— **纯函数**，四条形态各走一条分支。
 *
 * 为什么收在这里而不是留在 `ChatViewModel` 里：它是**提示词的一部分**，却被 ViewModel 的私有方法
 * 挡在自检外面（同 [playTurnRequirement] 当初搬出来的理由）。而这里是"复刻台／实验台"必须逐字复制的
 * 一处 —— 放不进来就没法被断言，只能靠人眼比对，改了 App 也不会有人发现。
 *
 * 顺序即优先级（越靠后越贴近生成点）：
 * ① 角色卡的 `post_history_instructions`：**长期**要求，作者设定的始终要遵守的规则，排最前；
 * ② 形态专属的硬要求：工具＝输出格式的可核对硬指标；玩法＝[playTurnRequirement]；
 *    陪伴/代笔＝风格硬性要求（有可核对指标时）＋常驻的防重复与视角边界；
 * ③ 单条临时导演（`oneShot`）：这一条的事，排最后。
 *
 * 三处刻意的口径：
 * - 角色后置指令**不分形态**都发（工具卡也会写"始终别用敬语"这类长期要求）；
 * - 防重复与视角边界是**常驻**的：实测同类话放系统提示词里会被长上下文稀释，
 *   必须每轮再贴一遍（视角用卡名指代、人称中立）；
 * - 各块之间的空行由"前面非空才补一个换行"决定，不无条件拼接 —— 空块不留空行。
 */
fun AiClient.tailHint(
    mode: PromptMode,
    card: CharacterCard?,
    style: NarrativeStyle,
    oneShot: String = ""
): String = buildString {
    val post = card?.postHistory?.trim().orEmpty()
    if (post.isNotBlank() && card != null) {
        append("【角色后置指令（本卡作者设定，始终遵守；与视角边界冲突时以视角边界为准）】\n")
        append(expandMacros(post, card)).append('\n')
    }
    val fmt = card?.let { expandMacros(it.outputFormat, it).trim() }.orEmpty()
    if (mode == PromptMode.TOOL) {
        if (fmt.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append("【输出格式（必须逐条满足，格式不对就重做）】\n")
            append(fmt)
        }
    } else if (mode == PromptMode.PLAY) {
        if (isNotEmpty()) append('\n')
        append(playTurnRequirement(card))
    } else {
        val reminder = NarrativeStyles.hardReminder(style)
        if (reminder.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(reminder)
        }
        if (card != null) {
            if (isNotEmpty()) append('\n')
            append("【防重复】不得重复之前任何一轮已经用过的动作描写、收尾方式与比喻；")
            append("若本轮仍在同一场景，必须引入新信息、新动作或新的关系推进。\n")
            append("【本条硬性要求】视角：只写").append(card.name)
            append("本人的言行与所见；不得出现用户的动作、台词或心理活动。")
        }
    }
    if (oneShot.isNotBlank()) {
        if (isNotEmpty()) append('\n')
        append("【本轮特别指示（仅本条生效，优先级高于上面所有风格与默认设定，不改变长期设定）】")
        append(oneShot.trim())
        append(" 只在本条满足，下一条自动回到会话既定风格；仍遵守视角边界与排版，不替用户说话。")
    }
}

/** `{{char}}` / `{{user}}` 宏：卡主的文本里可能带着它们（见 [expandMacros]） */
internal val charMacro = Regex("\\{\\{\\s*char\\s*\\}\\}")
private val userMacro = Regex("\\{\\{\\s*user\\s*\\}\\}")
private val botMacro = Regex("<BOT>")

/** `{{user}}` 在这里展开成什么。我们没有"用户的名字"（本会话设定是一段自由文本），用「你」最自然 */
const val USER_MACRO_WORD = "你"

/**
 * 展开角色卡里的宏：`{{char}}` → 角色名、`{{user}}` → 「你」、`<BOT>` → 角色名。
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
fun AiClient.expandMacros(text: String, card: CharacterCard): String {
    if (text.isBlank()) return text
    var out = text
    if (out.contains("{{")) {
        out = userMacro.replace(out) { USER_MACRO_WORD }
        out = charMacro.replace(out) { card.name }
    }
    if (out.contains("<BOT>")) out = botMacro.replace(out) { card.name }
    return out
}

// ── 记忆整理的产物解析 ──────────────────────────────────────────────────────

/** 记忆整理任务要模型按段头分段输出，这三组就是认得的段头（顺序＝匹配优先级，长前缀在前） */
private val SITUATION_HEADERS = listOf("【当前处境】", "【处境】")
private val SECRET_HEADERS = listOf("【隐藏状态】", "【隐藏】")
private val MEMORY_HEADERS =
    listOf("【共同记忆】", "【当前进度】", "【本局进度】", "【进度】", "【记忆】")

/**
 * 记忆整理的结果：三份状态各归各位。
 *
 * [memory] 沿用旧字段（用户可见可改）。[situation] 与 [secret] 是本轮新分的两层：
 * 前者是"此刻在哪、到哪一步"，后者是玩法型的内部真相（用户看不到）。
 */
data class MemoryUpdate(
    /** 【当前处境】一行短句；空 ＝ 这次没拿到，调用方**保留旧值**（别把它清成空） */
    val situation: String = "",
    val memory: String = "",
    /** 【隐藏状态】要点行（玩法专属）；空同上 */
    val secret: String = ""
)

/**
 * 解析记忆整理任务返回的全文（提示词见 `ChatViewModel.maybeUpdateMemory`）。
 *
 * **按段头切而不是按顺序切**：模型偶尔会调换次序、漏写一段、或把内容写在段头同一行
 * （实测「【当前处境】深夜的茶馆」这种写法很常见），按顺序切就会整段错位。
 *
 * 一个段头都没认出来时（老格式、模型没照做、或它只回了要点行）**整段当记忆**：
 * 此时行为与改造前一字不差，最坏情况是"这次没拿到处境"，而不是把记忆写坏。
 */
fun parseMemoryUpdate(raw: String): MemoryUpdate {
    val text = raw.trim()
    if (text.isEmpty()) return MemoryUpdate()
    val sit = StringBuilder()
    val mem = StringBuilder()
    val sec = StringBuilder()
    var cur = 0 // 0=记忆 1=处境 2=隐藏
    var sawHeader = false
    fun put(line: String) {
        when (cur) {
            1 -> sit.appendLine(line)
            2 -> sec.appendLine(line)
            else -> mem.appendLine(line)
        }
    }
    text.lines().forEach { line0 ->
        val line = line0.trim()
        if (line.isEmpty()) return@forEach
        val header = SITUATION_HEADERS.firstOrNull { line.startsWith(it) }
            ?: SECRET_HEADERS.firstOrNull { line.startsWith(it) }
            ?: MEMORY_HEADERS.firstOrNull { line.startsWith(it) }
        if (header == null) {
            put(line)
            return@forEach
        }
        sawHeader = true
        cur = when (header) {
            in SITUATION_HEADERS -> 1
            in SECRET_HEADERS -> 2
            else -> 0
        }
        // 段头同一行还带着正文（「【当前处境】深夜的茶馆」）也要收下，否则那一行就丢了
        val inline = line.removePrefix(header).trim()
        if (inline.isNotEmpty()) put(inline)
    }
    if (!sawHeader) return MemoryUpdate(memory = text)
    return MemoryUpdate(
        situation = sit.toString().trim(),
        memory = mem.toString().trim(),
        secret = sec.toString().trim()
    )
}
