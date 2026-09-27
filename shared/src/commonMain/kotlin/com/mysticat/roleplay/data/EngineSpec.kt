package com.mysticat.roleplay.data

/**
 * 引擎形态注册表。
 *
 * **为什么要有它**：工具形态要在编辑器、聊天页、装配器三处各分一次叉。如果这些分叉写成散在各页面的
 * `if (formTag == "tool")`，那么加第四个形态（game / sim）时就得在十几处同步改，必然漏掉一两处
 * ——漏掉的表现还是**静默的**（比如"工具会话里出现了角色记忆"，不会崩、只会让模型跑偏）。
 * 这里把"这个形态长什么样"收成一份声明，界面照它渲染，与 [NarrativeStyles] 让界面与 prompt
 * 共用同一份事实来源是同一个套路。
 *
 * **边界**：只承载"形态差异"，不承载业务分支（别让它变成万能开关箱）。住 `commonMain`：
 * UI 与 `AiClient` 都要读它，而 `commonMain` 是唯一两边都看得见的源集。
 */
data class EngineSpec(
    /** 存储值（`CharacterCard.formTag`）：""=陪伴 / "experience"=多线 / "tool"=工具 / "play"=玩法 */
    val formTag: String,
    val label: String,
    /** 系统提示词按哪套契约装配（见 [PromptMode]） */
    val promptMode: PromptMode,

    // ── 编辑器 ────────────────────────────────────────────────────────────
    /** 可见的设定字段组（顺序由编辑器自己排，这里只管"有没有"） */
    val editorFields: Set<EditorField>,
    /**
     * 前两个字段的叫法。工具形态下它们是**风格参考**（人格降级为风格滤镜）——
     * 沿用"人设"会让工具作者以为要写一个角色。
     */
    val personaLabel: String,
    val personalityLabel: String,
    /** 开场白那一栏的叫法。「陪伴 / 多线」是"角色先说的第一句"；工具是**使用示例**（点击当任务填入输入框） */
    val greetingLabel: String,
    val greetingHint: String,
    /** 点击开场白/示例是否等于"填入输入框"（工具＝是，陪伴＝否，它已经在新会话里了） */
    val greetingFillsInput: Boolean,
    /**
     * 灵感创作页「形态标签」下面那行说明：选了这一档会生成出什么。
     * 放在这里而不是写死在页面上：它必须与 [promptMode] / [editorFields] 说的是同一件事 ——
     * 页面写"生成角色"、[CharacterGenerator] 却按工具要字段，用户就会拿到一张自己看不懂的卡。
     */
    val createHint: String,

    // ── 会话生命周期 ──────────────────────────────────────────────────────
    /** 新会话是否自动铺场景卡与开场白。**工具会话不该有 assistant 开场**：它是任务，不是剧情 */
    val seedsOpening: Boolean,

    // ── 聊天页 ────────────────────────────────────────────────────────────
    /** 叙事风格 7 轴（含「高级」入口）：纯虚构语法，工具里无对应物 */
    val narrativeAxes: Boolean,
    /** 角色记忆 / 前情提要 / 本会话用户设定：都是关系与剧情连续性设备 */
    val relationshipMemory: Boolean,
    /** 会话级背景音乐入口 */
    val sessionBgm: Boolean,
    /** 新回复自动朗读（顶栏喇叭）。**与形态无关**：它是输出通道，不是剧情/关系设备 */
    val autoTts: Boolean,
    /** 灵感回复入口：它回答的是"戏里我该说什么" */
    val inspiration: Boolean,
    /** 受控逐字揭示（打字机）。关掉＝delta 直接进草稿，短成品整体出现、更早可复制 */
    val typewriter: Boolean,
    /** 多版本气泡上的版本切换条。「换一批」仍会追加版本，只是不给"切回上一版"的 UI */
    val variantSwitcher: Boolean,
    /** 「重新生成」在这个形态下叫什么 */
    val regenerateLabel: String,
    /** 输入框占位文案 */
    val inputPlaceholder: String,
    /** 气泡末尾的常驻复制按钮（成品是要拿走的，不该藏在长按菜单里） */
    val bubbleCopyButton: Boolean,
    /**
     * 菜单里的「按格式重排」：成品形状不对时，用户点一下把上一条按 [CharacterCard.outputFormat] 重排
     * （见 [OutputFormats.reformatHistory]）。只有"输出是成品"的形态才有这件事——剧情没有格式可言。
     */
    val formatRewrite: Boolean,
    /** 首条用户消息＝待处理素材 ⇒ 钉住不参与裁剪（见 `AiClient.trimHistory`） */
    val pinFirstMessage: Boolean,

    // ── 状态承载（注入段名与整理节奏）──────────────────────────────────────
    /**
     * 注入段的段名。陪伴 / 多线的语义是**关系向**的（【共同记忆】：长期设定与共同经历）；
     * 玩法型要的是**进度向**的（【当前进度】：已确认的结论、计分、进行到第几步）——
     * 段名说错，模型会把"排除名单"当成"我们的关系"来对待。
     */
    val memorySection: String = "共同记忆",
    val memoryHint: String = "你与用户共同确认的长期设定/事件，请保持一致",
    /** 「更多」面板里那个入口的叫法（玩法型是"本局进度"，用户才知道点进去能改什么） */
    val memoryEntryLabel: String = "角色记忆",
    /**
     * 每累计多少条新消息才后台合并一次这份状态。陪伴 / 多线 10 条够用（关系变化慢），
     * 玩法型要勤（一局才十几轮，10 条一次等于半局不更新——用户看到的是"它忘了刚才说过什么"）。
     */
    val memoryMergeEvery: Int = 10,

    // ── 引擎级默认参数（用户显式设过以用户为准）────────────────────────────
    /**
     * 该形态的默认温度。只在**用户没动过全局温度**（仍是出厂值）时生效——
     * 成品要稳，工具默认比陪伴低；用户自己调过就一律听用户的。
     *
     * `null` ＝ **不接管**。玩法型故意留 null：猜谜/推理恰恰需要探索，压到 0.3 会把候选
     * 收敛到最显著的几个（二十问"逐个报人名"的第一号根因）。
     */
    val defaultTemperature: Double? = null,

    /**
     * 「更多」面板里给一句**显式建议**：这个形态打开思考强度会明显更好。
     * 只对玩法型为真——它跟 [defaultTemperature]＝null 是同一件事的两半：温度交还给用户，
     * 但得**说出来**该配什么，否则"参数装反"的坑只是从默认值挪到了用户手上。
     */
    val suggestThinking: Boolean = false
)

/** 编辑器里的设定字段组（引擎决定可见性，编辑器决定顺序与措辞） */
enum class EditorField {
    /** 角色描述 / 性格特点（工具形态下作为"风格参考"） */
    Persona,
    /** 对话示例（工具形态不用它，见 `AiClient.buildSystemPrompt` 的 TOOL 分支） */
    DialogueExamples,
    /** 世界观 / 当前场景 */
    Scenario,
    /** 开场白（工具下叫「使用示例」） */
    Greeting,
    /** 任务说明（工具专属） */
    TaskBrief,
    /** 输出格式（工具专属） */
    OutputFormat,
    /** 玩法规则：硬约束（玩法专属） */
    PlayRules,
    /** 玩法状态项：这一局要记什么（玩法专属） */
    PlayState,
    /** 每轮给几个编号选项（玩法专属） */
    PlayOptions
}

object Engines {

    /** 陪伴（默认形态）：全功能，行为与改造之前一字不变 */
    private val RP = EngineSpec(
        formTag = "",
        label = "陪伴",
        promptMode = PromptMode.RP,
        editorFields = setOf(EditorField.Persona, EditorField.DialogueExamples, EditorField.Scenario, EditorField.Greeting),
        personaLabel = "角色描述（外貌 / 身份 / 背景等硬事实）",
        personalityLabel = "性格特点（3~5 个能在对话里体现的特质）",
        greetingLabel = "开场白（进入新会话时角色说的第一句）",
        greetingHint = "例：'殿下，这么晚了，你果然还是来了。'",
        greetingFillsInput = false,
        createHint = "陪伴：常规角色扮演，生成人设 / 世界观与一句开场白。",
        seedsOpening = true,
        narrativeAxes = true,
        relationshipMemory = true,
        sessionBgm = true,
        autoTts = true,
        inspiration = true,
        typewriter = true,
        variantSwitcher = true,
        regenerateLabel = "重新生成",
        inputPlaceholder = "",
        bubbleCopyButton = false,
        formatRewrite = false,
        pinFirstMessage = false
    )

    /** 多线：与陪伴同一套引擎，只是新会话从多条开场白里随机抽一条 */
    private val EXPERIENCE = RP.copy(
        formTag = "experience",
        label = "多线",
        createHint = "多线：与陪伴同款，但会生成 2 条以上开局方向不同的开场白，新会话随机抽一条。"
    )

    /**
     * 工具：交互契约不同 —— 用户消息是**待处理素材**、输出是**可直接拿走的成品**。
     * 人格降级为风格滤镜（由 [CharacterCard.taskBrief] 承载），所有剧情/关系设备一律不出现。
     */
    private val TOOL = EngineSpec(
        formTag = "tool",
        label = "工具",
        promptMode = PromptMode.TOOL,
        editorFields = setOf(EditorField.Persona, EditorField.TaskBrief, EditorField.OutputFormat, EditorField.Greeting),
        personaLabel = "风格参考 · 语气与用词（例：书面、简洁、公文腔）",
        personalityLabel = "风格参考 · 表达习惯（例：直接给结论、不寒暄）",
        greetingLabel = "使用示例（填**任务**：一句你要它做什么，点击即填入输入框）",
        greetingHint = "例：把这段会议记录整理成 5 条待办，每条不超过 15 字",
        greetingFillsInput = true,
        createHint = "工具：不扮演角色，而是给它一份职责（任务说明 / 输出格式 / 使用示例）。" +
            "你发素材、它给能直接拿走的成品。",
        // 新会话不带 assistant 开场：工具会话的第一条必须是用户的任务素材
        seedsOpening = false,
        narrativeAxes = false,
        relationshipMemory = false,
        sessionBgm = false,
        // 自动朗读留着：它是**输出通道**（听成品 / 校对 / 免看屏），不是剧情或关系设备，
        // 与形态无关。工具形态一度关掉它，用户 2026-09-24 反馈"语音朗读没同步到新形态"后恢复。
        autoTts = true,
        inspiration = false,
        // 成品往往要立刻复制走：不让打字机把"可复制"的时间往后推
        typewriter = false,
        variantSwitcher = false,
        regenerateLabel = "换一批",
        inputPlaceholder = "输入要处理的内容，或补充要求…",
        bubbleCopyButton = true,
        formatRewrite = true,
        pinFirstMessage = true,
        defaultTemperature = 0.3
    )

    /**
     * 玩法：陪用户玩**一局**有规则的玩法。
     *
     * 与 [TOOL] 同属"不扮演虚构角色"那一侧，但评判标准完全不同：工具问"成品能不能直接用"，
     * 玩法问"规则守得住、进度记得住、这一局好不好玩"。三条派生差异：
     * ① **温度不接管**（玩法要探索，0.3 会让它只会报最显著的候选）+ 面板提示打开思考强度；
     * ② **允许开场引导**（用卡里的开局那一步，地球Online 那种"选模式 → 建角色 → 开局"的头）；
     * ③ **保留状态承载**（进度靠记忆，段名叫【当前进度】、合得更勤——一局才十几轮）。
     */
    private val PLAY = EngineSpec(
        formTag = "play",
        label = "玩法",
        promptMode = PromptMode.PLAY,
        editorFields = setOf(
            EditorField.Persona, EditorField.PlayRules, EditorField.PlayState,
            EditorField.PlayOptions, EditorField.Scenario, EditorField.Greeting
        ),
        personaLabel = "风格参考 · 语气与用词（例：轻松、爱吐槽、口语化）",
        personalityLabel = "风格参考 · 表达习惯（例：一次只问一句、不解释自己为什么这么问）",
        greetingLabel = "开局引导（新会话里你先说的第一步；留空就直接等用户开始）",
        greetingHint = "例：我已经在心里想好了一个人，你来提问吧 —— 一次问一个，我只会答是或不是。",
        greetingFillsInput = false,
        createHint = "玩法：陪玩一局有规则的玩法（猜谜 / 对弈 / 情景问答）。规则是硬约束、" +
            "每轮结尾给编号选项、这局的进度它自己记。一局结束重开，不做主线与存档。",
        // 与工具相反：玩法**允许**开场引导——那是"这一局怎么开始"，不是"待处理的素材"
        seedsOpening = true,
        narrativeAxes = false,
        // 状态承载留着：玩法要记的是**这一局的进度**（段名与节奏由 memory* 三项改）
        relationshipMemory = true,
        sessionBgm = false,
        // 同工具：朗读是输出通道，与形态无关。而且玩法**尤其**用它——猜谜这类玩法用户是"听着玩"的
        // （用户 2026-09-24 反馈"语音朗读没同步到新形态"后恢复）
        autoTts = true,
        inspiration = false,
        // 一局的回复本来就短，整段出现比逐字更合用（同工具：早点能读到题与选项）
        typewriter = false,
        variantSwitcher = false,
        regenerateLabel = "重新生成",
        inputPlaceholder = "回答，或输入选项编号…",
        bubbleCopyButton = false,
        formatRewrite = false,
        // 首条不是"待处理素材"，是这一局的开场设定；钉住它会挤掉后面真正要用的历史
        pinFirstMessage = false,
        memorySection = "当前进度",
        memoryHint = "这一局已经确认的结论、计分与进行到的进度，请保持一致",
        memoryEntryLabel = "本局进度",
        memoryMergeEvery = 4,
        defaultTemperature = null,
        suggestThinking = true
    )

    /**
     * 注册表顺序＝**界面上形态出现的顺序**（灵感创作的形态标签、编辑器的形态标签、列表页的形态筛选）。
     * 玩法排在工具前面（用户 2026-09-23 口径）：两者是同一侧的两档，而玩法是"陪玩一局"、
     * 工具是"交付成品"—— 用户按使用频率说的顺序，不是按实现先后。
     */
    val all: List<EngineSpec> = listOf(RP, EXPERIENCE, PLAY, TOOL)

    /**
     * 形态名（陪伴 / 多线 / 玩法 / 工具）。
     *
     * **它们不该出现在「类型/标签」里**（用户 2026-09-23 反馈）：形态一旦定下来，它就已经是
     * 一个正交维度（筛选用 [FormFilter]），再混进类型列表就是同一件事有两个入口，
     * 而且用户会以为"选了类型=选了形态"。真实来源是导入的卡带的 tags（卡站常把 Tool 当标签），
     * 被 `CategoryManager` 自动登记进了类型表 —— 那里用它做过滤。
     */
    val formLabels: Set<String> = all.map { it.label }.toSet()

    /** 按存储值取配置；不认识的取值一律当陪伴（老数据 / 手改过的 JSON 都不该让聊天页崩） */
    fun of(formTag: String): EngineSpec = all.firstOrNull { it.formTag == formTag } ?: RP

    /**
     * 形态名的**可空**版本，给"徽标"这类展示用（发现页的精选卡 tile）：
     * 空值与不认识的取值都返回 null，界面因此什么都不画。
     *
     * 与 [of] 的区别是刻意的：会话照 [of] 兜底成陪伴（宁可当陪伴跑，也不能崩），但**徽标不能兜底**——
     * 发现页的清单是热更新的，作者把形态写错一个词，tile 上显示"陪伴"就等于替作者认领了一个他没选的形态，
     * 用户会以为这张卡是陪伴卡。看不见比看错好。
     */
    fun labelOrNull(formTag: String): String? =
        if (formTag.isBlank()) null else all.firstOrNull { it.formTag == formTag }?.label

    /** 按卡取配置 */
    fun of(card: CharacterCard?): EngineSpec = of(card?.formTag.orEmpty())
}

/**
 * 列表页的**形态筛选**口径。
 *
 * 口径**必须与 [Engines.of] 同一份事实来源**：不认识的 `formTag`（老数据、手改过的 JSON、将来某个
 * 还没实现的形态）在引擎那一侧一律当**陪伴**，筛选这一侧就必须把它归进「陪伴」桶——否则那张卡会在
 * `全部` 里看得见、点「陪伴」和「工具」却都查不到，用户看到的是**自己的卡不见了**（不是"筛掉了"）。
 */
object FormFilter {

    /**
     * "全部"的取值。**不能借陪伴的空串**：陪伴自己的 `formTag` 就是 `""`，两者共用一个值的话
     * "点陪伴 chip" 等于"清除筛选"，而用户以为自己在筛。
     */
    const val ALL = "*"

    /**
     * 这一条筛选值不值得显示：库里存在**非陪伴**形态的卡才有意义。
     * 只有一种形态时它只是一行噪声，而且会让人以为"必须选一个"。
     */
    fun worthShowing(cards: List<CharacterCard>): Boolean =
        cards.any { Engines.of(it).formTag.isNotBlank() }

    /** 该卡是否落在选中的形态里（[tag] ＝ [ALL] 时一律通过） */
    fun matches(card: CharacterCard, tag: String): Boolean =
        tag == ALL || Engines.of(card).formTag == tag

    /** 筛选条要显示的取值与叫法（顺序＝[Engines.all]：陪伴 / 多线 / 玩法 / 工具） */
    val options: List<Pair<String, String>> =
        listOf(ALL to "全部") + Engines.all.map { it.formTag to it.label }
}
