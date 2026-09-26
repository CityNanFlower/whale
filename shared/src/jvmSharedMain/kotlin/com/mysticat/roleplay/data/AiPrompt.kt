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
    mode: PromptMode = PromptMode.RP,
    worldBookHits: List<WorldBookHit> = emptyList()
): String = buildString {
    // 宏展开：只作用在**从卡里读出来的文本**上。用户自己写的东西
    // （本会话设定 / 全局补充要求 / 记忆 / 前情）不展开 —— 那是人话，不是模板。
    fun m(text: String): String = expandMacros(text, card)

    val sug = mode == PromptMode.SUGGEST
    val layered = card.hasLayeredPersona()

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

    // ── 世界书·角色设定**之前**那一段────────────────────────────────
    // ST 的 `position: before_char`：资料排在角色定义之前（先给世界，再说人）。
    // 命中判断**不在这里**做 —— 它要读最近几条消息，而这里只看得到卡的设定；
    // 调用方（chatStream / suggestReplies）算好后传进来，笔记本式的记账与「本轮注入」面板共用同一份结果。
    val bookBefore = WorldBookEngine.partition(worldBookHits).before
    worldBookBlock(bookBefore, card)?.let { append(it) }

    // ── 任务说明 / 输出格式（工具形态专属）──────────────────────────────
    // 不复用 persona / scenario：编辑器标签语义不对（工具作者看到"人设/世界观"会困惑），
    // 而 persona 在以"这是你本人的设定"注入时会**主动诱发扮演**。
    if (mode == PromptMode.TOOL) {
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

    // ── 玩法形态专属块────────────────────────────────
    // 三样东西：规则（硬约束，玩法唯一的"对错来源"）、状态项（这一局要记什么）、玩法设定（背景前提）。
    // 都不复用 persona / scenario 的原本语义：那两块是以"这是你本人的设定"注入的，会主动诱发扮演，
    // 而玩法型的 description / personality 在这里只是**风格参考**（同工具）。
    if (mode == PromptMode.PLAY) {
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

    // ── 角色设定：分层 or 老口径 ─────────────────────────────────────────
    // 工具与玩法**都不走这一段**：它们的 description / personality 是风格参考（上面各自注入了），
    // 而"这是你本人的设定"这个身份框架会把它变成一个人在扮演。
    val playsRole = mode != PromptMode.TOOL && mode != PromptMode.PLAY
    if (playsRole) {
        if (layered) {
            val desc = m(card.description).trim()
            val pers = m(card.personality).trim()
            if (desc.isNotBlank() || pers.isNotBlank()) {
                append("【角色设定】").append(if (sug) "（角色本身的设定，「我」= 角色）" else "（你本人）").append("\n")
                // 分开写而不是揉成一段：description 管事实、personality 管气质，
                // 混在一起模型会互相复读（调研结论）。
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
    if (playsRole && card.scenario.isNotBlank()) {
        append("【世界观 / 当前场景】")
            .append(if (sug) "（里面的「我」= 角色）" else "（以你自己的视角书写，里面的「我」= 你）")
            .append("\n").append(m(card.scenario).trim()).append("\n\n")
    }
    // ── 对话示例────────────────────────────────────────────
    // 位置在场景之后：它是**语气示范**，离生成点近一些；同时明确"这是示范，不是剧情"，
    // 否则模型会把示例里的情节当成已经发生过的事接着往下写（社区最常见的烂卡症状之一）。
    if (playsRole) {
        val ex = m(card.mesExample).trim()
        if (ex.isNotBlank()) {
            append("【对话示例】（照着这个语气、节奏与长短来回复；示例只是示范，其中的情节没有发生过）\n")
            append(ex).append("\n\n")
        }
    }
    // ── 世界书·角色设定**之后**那一段（ST `position: after_char`）─────────
    // 放在角色定义（含世界观与对话示例）之后、用户设定之前：这是 ST 的 after_char 位，
    // 也是"世界资料已经交代完、接下来是用户这一侧"的自然分界。
    // 位置在 `@depth` 的那种条目**不在这里** —— 它们要插进消息数组（见 chatStream）。
    worldBookBlock(WorldBookEngine.partition(worldBookHits).after, card)?.let { append(it) }
    if (userSetting.isNotBlank()) {
        append("【用户的设定】")
            .append(if (sug) "（只描述用户自己，里面的「我」= 用户）" else "（只描述用户自己，不适用于你）")
            .append("\n").append(userSetting.trim()).append("\n\n")
    } else if (mode == PromptMode.RP) {
        // 工具形态**不加**这段：它会把用户发来的任务素材误读成"交互关系设定"
        append("【用户的设定】\n")
        append("（用户没有提供身份设定：随着对话推进，根据TA的言行与说话方式自然形成对TA身份与性格的印象，")
        append("让称呼与态度随之变化——这只是你的推测：不要替用户补充TA没说过的经历、动机或心理活动，")
        append("TA否认时立即接受。若【角色设定】或【世界观】已写明你与用户的关系，以那个为准。）\n\n")
    }
    if (summary.isNotBlank()) {
        append("【前情提要】（更早剧情的摘要，请当作你已经知道）\n").append(summary.trim()).append("\n\n")
    }
    if (memory.isNotBlank()) {
        // 段名与括注**跟形态走**：陪伴/多线是关系向的【共同记忆】，玩法型要的是
        // 进度向的【当前进度】——段名说错，模型会把"已排除的名单"当成"我们的共同经历"来对待。
        val mem = Engines.of(card)
        append("【").append(mem.memorySection).append("】（").append(mem.memoryHint).append("）\n")
        append(memory.trim()).append("\n\n")
    }
    if (settings.extraSystemPrompt.isNotBlank()) {
        append("【补充要求】\n").append(settings.extraSystemPrompt.trim()).append("\n\n")
    }
    // 【叙事风格】放在系统提示词的**最末**（2026-09-16 调整）：
    // 原来它排在【补充要求】之前，而系统提示词光"角色设定 + 世界观 + 用户设定 + 前情提要 + 共同记忆"
    // 就有一两千字，风格段埋在中段会被稀释 —— 用户实测"选了沉浸小说（丰沛 250~500 字）但回复还是百来字"。
    // 挪到最末取近因效应，让风格要求离生成点最近；配合"风格刚切换"的一次性告知抵消历史短回复的锚定。
    if (styleHint.isNotBlank()) {
        append(styleHint.trim()).append("\n")
    }
}

/**
 * 把一组世界书条目拼成一段（宏已展开）；没有条目返回 null。
 *
 * **段头是必要的**：书里的正文是作者写给模型看的资料（真卡实测是
 * `{{user}}: "What is Eldoria?"` 这种问答体），不声明"这是资料、不要复述"，
 * 模型很容易把那段问答当成"刚刚发生过的对话"照抄一遍。
 */
internal fun AiClient.worldBookBlock(entries: List<WorldBookEntry>, card: CharacterCard): String? {
    if (entries.isEmpty()) return null
    return buildString {
        append("【").append(WorldBookEngine.SECTION).append("】")
            .append(WorldBookEngine.SECTION_HINT).append("\n")
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
 * 三条对应二十问那次的三个失败点：放宽判定 / 把已否掉的答案再报一遍 / 一回合铺太长。
 */
fun AiClient.playTurnRequirement(card: CharacterCard?): String = buildString {
    append("【玩法硬性要求（这一条必须满足）】\n")
    append("1. 按卡里的玩法规则走，不因为用户催促或抱怨就放宽判定、改规则或跳过环节。\n")
    append("2. 只推进这一回合：回复要短，不复述规则、不写长段叙述、不做总结。\n")
    append("3. 有候选或可能性时先在内部排除，不要把已经否掉的答案再报一遍。\n")
    val opts = card?.playOptions ?: 0
    if (opts > 0) {
        append("4. 结尾给 ").append(opts).append(" 个编号选项（「1. …」各占一行），彼此有意义地不同。\n")
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
