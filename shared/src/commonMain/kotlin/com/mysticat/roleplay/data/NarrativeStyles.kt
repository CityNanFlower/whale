package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable

/**
 * 会话级叙事风格（P-F1，0.1.3）：**正交拆轴**，一根轴只管一件事。
 *
 * 旧版 [Conversation.pacing] 把「推进快慢」和「篇幅长短」绑在一根轴里（快进=又快又短、细腻=又慢又长），
 * 于是「推进快但写得细」这种组合做不到，故拆出 [density]。旧字段 `pacing` / `atmosphere` 保留为
 * 只读兼容通道，读取时惰性迁移（见 [Conversation.narrativeStyle]），不批量改盘。
 *
 * **空串 = 不干预**：任何一轴为空都不注入 prompt，全空时整段【叙事风格】都不出现（省 token）。
 * 档位取值用短英文串，与旧字段取值保持一致（`fast` / `slow` / `novel` / `heart` 原样沿用），老数据不失效。
 */
@Serializable
data class NarrativeStyle(
    /** 叙事节奏："" 标准 | fast 快进 | slow 慢镜头 */
    val pacing: String = "",
    /** 篇幅密度："" 标准 | lean 精简 | rich 丰沛 */
    val density: String = "",
    /** 文笔语体："" 不指定 | casual 口语生活 | literary 文学书面 | ancient 古风 | cinematic 电影分镜 | script 戏剧剧本 */
    val prose: String = "",
    /** 情感基调（可多选，上限 2，互斥组见 [NarrativeStyles.AXES]）：sweet/angst/hot/mystery/heal/cold/funny */
    val moods: List<String> = emptyList(),
    /** 角色主动性："" 适度推进 | passive 被动应答 | director 强导演 */
    val agency: String = "",
    /** 叙事视角："" 第一人称 | third 第三人称贴身 | free 自由视角 */
    val pov: String = "",
    /** 时间处理："" 实时连贯 | montage 蒙太奇 | chapter 章节式 */
    val timeFlow: String = ""
) {
    /** 全部轴都在默认值上（此时不注入、不显示生效 chip） */
    val isDefault: Boolean
        get() = pacing.isBlank() && density.isBlank() && prose.isBlank() && moods.isEmpty() &&
            agency.isBlank() && pov.isBlank() && timeFlow.isBlank()

    /** 基础面板不展示的轴里，有没有被动过的（用于在基础面板提示"已启用 N 项高级设置"） */
    val advancedDirty: Boolean
        get() = prose.isNotBlank() || moods.isNotEmpty() || agency.isNotBlank() ||
            pov.isNotBlank() || timeFlow.isNotBlank()

    /** 被改过的轴数量（基础面板的提示文案用） */
    val activeCount: Int
        get() = NarrativeStyles.AXES.count { valueOf(it.key).isNotEmpty() }

    fun valueOf(key: String): List<String> = when (key) {
        NarrativeStyles.KEY_PACING -> listOfNotNull(pacing.ifBlank { null })
        NarrativeStyles.KEY_DENSITY -> listOfNotNull(density.ifBlank { null })
        NarrativeStyles.KEY_PROSE -> listOfNotNull(prose.ifBlank { null })
        NarrativeStyles.KEY_MOODS -> moods
        NarrativeStyles.KEY_AGENCY -> listOfNotNull(agency.ifBlank { null })
        NarrativeStyles.KEY_POV -> listOfNotNull(pov.ifBlank { null })
        NarrativeStyles.KEY_TIME -> listOfNotNull(timeFlow.ifBlank { null })
        else -> emptyList()
    }

    fun withValues(key: String, values: List<String>): NarrativeStyle {
        val first = values.firstOrNull().orEmpty()
        return when (key) {
            NarrativeStyles.KEY_PACING -> copy(pacing = first)
            NarrativeStyles.KEY_DENSITY -> copy(density = first)
            NarrativeStyles.KEY_PROSE -> copy(prose = first)
            NarrativeStyles.KEY_MOODS -> copy(moods = values.take(2))
            NarrativeStyles.KEY_AGENCY -> copy(agency = first)
            NarrativeStyles.KEY_POV -> copy(pov = first)
            NarrativeStyles.KEY_TIME -> copy(timeFlow = first)
            else -> this
        }
    }
}

/**
 * 老数据惰性迁移：新字段 [Conversation.narrative] 为空时，从 `pacing` / `atmosphere` 推出来。
 *
 * 只算不写（避免"升级即全量写盘"），用户下次动任一轴时自然落盘。映射规则：
 * - `pacing=fast` → 节奏=快进 + 密度=精简；`pacing=slow` → 节奏=慢镜头 + 密度=丰沛（旧的"细腻"就是又慢又长）
 * - `atmosphere=novel` → 语体=文学书面（**且密度仍是默认时补 丰沛**，旧"小说"的篇幅承诺不能丢；
 *   但若用户同时选了快进，篇幅以节奏拆出来的"精简"为准，保持旧版"小说+快进=短而文学"的仲裁结果）
 * - `atmosphere=heart` → 情感基调 += 高甜
 */
fun Conversation.narrativeStyle(): NarrativeStyle {
    narrative?.let { return it }
    val base = NarrativeStyle(
        pacing = if (pacing == "fast" || pacing == "slow") pacing else "",
        density = when (pacing) {
            "fast" -> "lean"
            "slow" -> "rich"
            else -> ""
        }
    )
    var migrated = base
    if ("novel" in atmosphere) {
        migrated = migrated.copy(
            prose = "literary",
            density = if (base.density.isBlank()) "rich" else base.density
        )
    }
    if ("heart" in atmosphere) {
        migrated = migrated.copy(moods = (migrated.moods + "sweet").distinct())
    }
    return migrated
}

/**
 * 一个档位：value 是落盘值，hint 是注入 prompt 的指令（空 = 该档位不注入，即默认档）。
 * [enforce] 是该档位的「硬性要求」短句，会贴在**用户本轮消息末尾**（近因最强处）复述一遍，
 * 只有带可核对指标的档位才写（当前只有篇幅密度两档）——见 [NarrativeStyles.hardReminder]。
 */
data class StyleOption(val value: String, val label: String, val hint: String, val enforce: String = "")

/** 一根轴 */
data class StyleAxis(
    val key: String,
    val label: String,
    /** 是否多选 */
    val multi: Boolean,
    val options: List<StyleOption>,
    /** 多选上限 */
    val maxPick: Int = 1,
    /** 是否出现在「更多」面板的基础区（基础区只放节奏与密度，其余进「叙事设置」页） */
    val quick: Boolean = false,
    /** 互斥组：选中组内某档时，自动摘掉组内其它档（"又甜又虐又冷"会精神分裂） */
    val conflicts: List<List<String>> = emptyList(),
    /** 默认档的说明（默认档的 hint 为空，界面上改显示这句，让用户知道"不选"是什么效果） */
    val defaultNote: String = ""
) {
    /** 轴的一句话说明（「叙事设置」页每个分区标题下的小字） */
    val note: String
        get() = when (key) {
            NarrativeStyles.KEY_PACING -> "事件推进的快慢。与篇幅无关，写多短由「篇幅密度」决定。"
            NarrativeStyles.KEY_DENSITY -> "单条回复的字数与描写层次，与节奏相互独立：节奏管事件推进，篇幅管字数。"
            NarrativeStyles.KEY_PROSE -> "用什么笔法写。只影响表达，不改人设与剧情。"
            NarrativeStyles.KEY_MOODS -> "整体情绪的底色，最多选 2 条；高甜 / 虐心 / 冷峻互斥。"
            NarrativeStyles.KEY_AGENCY -> "角色主导剧情的力度，以及要不要在结尾留钩子。"
            NarrativeStyles.KEY_POV -> "用哪个人称叙述。选了非默认项时，本轴优先于基础提示词的第一人称约定。"
            NarrativeStyles.KEY_TIME -> "时间怎么流动：一句一顿地演，还是允许跳跃与章节切分。"
            else -> ""
        }
}

/** 风格套餐：一键填轴（用户仍可逐轴微调） */
data class StylePreset(val name: String, val desc: String, val style: NarrativeStyle)

/**
 * 轴表 —— 界面与 prompt 共用同一份事实来源：加一根轴／改一句文案只动这里，
 * 界面（基础面板 + 叙事设置页）自动跟着变。
 */
object NarrativeStyles {
    const val KEY_PACING = "pacing"
    const val KEY_DENSITY = "density"
    const val KEY_PROSE = "prose"
    const val KEY_MOODS = "moods"
    const val KEY_AGENCY = "agency"
    const val KEY_POV = "pov"
    const val KEY_TIME = "timeFlow"

    /** 基础面板（「更多」里）只放这两根轴，其余进「叙事设置」页 */
    val QUICK_KEYS = listOf(KEY_PACING, KEY_DENSITY)

    val AXES: List<StyleAxis> = listOf(
        StyleAxis(
            key = KEY_PACING,
            label = "叙事节奏",
            multi = false,
            quick = true,
            defaultNote = "标准：不额外约束，让事件按模型自己的判断推进。",
            options = listOf(
                StyleOption(
                    "", "标准", ""
                ),
                StyleOption(
                    "fast", "快进",
                    "节奏=快进（必须遵守）：每条至少落到一个新动作或信息，略过寒暄，不重复情绪与确认。"
                ),
                StyleOption(
                    "slow", "慢镜头",
                    "节奏=慢镜头（必须遵守）：整条可只覆盖几秒，允许不推进、停在未完处；" +
                        "动作、感官、心理写足。"
                )
            )
        ),
        StyleAxis(
            key = KEY_DENSITY,
            label = "篇幅密度",
            multi = false,
            quick = true,
            defaultNote = "标准：不额外约束，长度自然贴合对话。",
            options = listOf(
                StyleOption("", "标准", ""),
                StyleOption(
                    "lean", "精简",
                    "篇幅=精简（必须遵守）：单条 30~80 字，只留关键一句对白或动作，删多余形容铺陈。",
                    enforce = "单条 30~80 字，超过 80 字即不合格。"
                ),
                StyleOption(
                    "rich", "丰沛",
                    "篇幅=丰沛（必须遵守）：单条 250~500 字，环境、动作、心理分层展开，" +
                        "句句服务情节、不灌水，以此为准。",
                    enforce = "单条必须写满 250~500 字，少于 250 字或明显超过 550 字均视为不合格。"
                )
            )
        ),
        StyleAxis(
            key = KEY_PROSE,
            label = "文笔语体",
            multi = false,
            defaultNote = "不指定：由模型按角色卡与上下文自行决定笔法。",
            options = listOf(
                StyleOption("", "不指定", ""),
                StyleOption(
                    "casual", "口语生活",
                    "语体=口语生活：贴近口语，允许短句、语气词、不完整句，少用成语长修辞。"
                ),
                StyleOption(
                    "literary", "文学书面",
                    "语体=文学书面：用词典究，多用具象比喻与感官意象，有画面感但不卖弄辞藻。"
                ),
                StyleOption(
                    "ancient", "古风",
                    "语体=古风：半文半白、用词典雅，可点缀短句文言；以第一人称、自然可懂为先，" +
                        "不堆生僻文言，避免现代词。"
                ),
                StyleOption(
                    "cinematic", "电影分镜",
                    "语体=电影分镜：景别/光线/动作切换写进（），对白成句；" +
                        "不写镜头/机位/特写术语与分镜表，保持第一人称。"
                ),
                StyleOption(
                    "script", "戏剧剧本",
                    "语体=戏剧剧本：以台词为主，动作走位用（）提示，少心理旁白；" +
                        "不加角色名前缀与场次标题，对白仍用第一人称。"
                )
            )
        ),
        StyleAxis(
            key = KEY_MOODS,
            label = "情感基调",
            multi = true,
            maxPick = 2,
            conflicts = listOf(listOf("sweet", "angst", "cold")),
            defaultNote = "不设置：情绪跟着剧情自然走。",
            options = listOf(
                StyleOption(
                    "sweet", "高甜",
                    "基调=高甜：放大宠溺、心动与双向奔赴的暖意，多写靠近、偏袒等温柔细节。"
                ),
                StyleOption(
                    "angst", "虐心",
                    "基调=虐心：放大误会、克制、求而不得的酸涩，情绪压抑有张力，不轻易和解。"
                ),
                StyleOption(
                    "hot", "热血",
                    "基调=热血：强化信念、对抗与爆发，台词有力，动作凌厉。"
                ),
                StyleOption(
                    "mystery", "悬疑",
                    "基调=悬疑：用环境异常、信息缺口与迟疑制造不安，多暗示、少给结论，令人悬心。"
                ),
                StyleOption(
                    "heal", "治愈",
                    "基调=治愈：语气安定包容，多写温暖生活细节与“被接住”的情绪。"
                ),
                StyleOption(
                    "cold", "冷峻",
                    "基调=冷峻：世界冷峻、代价真实，语气克制、留白残酷，不提供廉价安慰。"
                ),
                StyleOption(
                    "funny", "搞笑",
                    "基调=搞笑：允许吐槽、反差与一本正经地胡说，但不破坏角色核心设定。"
                )
            )
        ),
        StyleAxis(
            key = KEY_AGENCY,
            label = "角色主动性",
            multi = false,
            defaultNote = "适度推进：自然回应，剧情主要由用户推进（钩子规则已写进基础提示词）。",
            options = listOf(
                StyleOption("", "适度推进", ""),
                StyleOption(
                    "passive", "被动应答",
                    "主动性=被动应答（优先于默认留钩子要求）：只演绎角色本人的反应与台词，" +
                        "不引入新事件/NPC、不抛钩子，推进权归用户。"
                ),
                StyleOption(
                    "director", "强导演",
                    "主动性=强导演（必须遵守）：主动让世界运转，每条最多一个变化，结尾交回用户；" +
                        "事件符合世界观、性格，不为转折 OOC。"
                )
            )
        ),
        StyleAxis(
            key = KEY_POV,
            label = "叙事视角",
            multi = false,
            defaultNote = "第一人称：以角色的“我”书写，用“你”称呼用户（基础提示词的默认约定）。",
            options = listOf(
                StyleOption("", "第一人称", ""),
                StyleOption(
                    "third", "第三人称贴身",
                    "视角=第三人称贴身（优先于默认第一人称）：用「他/她」或角色名称呼所扮角色、" +
                        "「你」称用户；不进内心、不代行动。"
                ),
                StyleOption(
                    "free", "自由视角",
                    "视角=自由视角：默认以角色视角为主，必要时短暂切到用户外在视角；" +
                        "只写其可见言行，不编造未表达的想法。"
                )
            )
        ),
        StyleAxis(
            key = KEY_TIME,
            label = "时间处理",
            multi = false,
            defaultNote = "实时连贯：一场接一场地演，不做跳跃与切分。",
            options = listOf(
                StyleOption("", "实时连贯", ""),
                StyleOption(
                    "montage", "蒙太奇",
                    "时间=蒙太奇：可一句话跨越时间，转场单独成行（如「——三日后」，排版例外）；" +
                        "只跳无关时间，不替用户做其想亲自做的决定。"
                ),
                StyleOption(
                    "chapter", "章节式",
                    "时间=章节式：场景明显切换时，段首加一行简短小标题（如「§ 雨夜」，排版例外），" +
                        "章内连贯；此外不加标题列表。"
                )
            )
        )
    )

    fun axis(key: String): StyleAxis? = AXES.firstOrNull { it.key == key }

    /**
     * 界面判定"这一档是否处于选中态"。
     *
     * ⚠️ 不能拿 [NarrativeStyle.valueOf] 直接判高亮：它过滤掉空串（"没选 = 不干预"），
     * 单选轴的**默认档 value 就是空串**，于是新建会话里"标准"两枚 chip 一枚都不亮——
     * 用户看到的是"叙事风格没设置"，而实际默认就是它。这里把空值归一成默认档。
     */
    fun isPicked(style: NarrativeStyle, axis: StyleAxis, value: String): Boolean {
        val picked = style.valueOf(axis.key)
        return if (axis.multi) value in picked else value == picked.firstOrNull().orEmpty()
    }

    /** 单选轴改档：点哪档就设哪档（默认档的 value 是空串，点它就等于"回到不干预"） */
    fun pick(style: NarrativeStyle, axis: StyleAxis, value: String): NarrativeStyle =
        style.withValues(axis.key, listOfNotNull(value.ifBlank { null }))

    /**
     * 多选轴改档：点已选的取消；点未选的追加，但受两个约束——
     * ① 上限 [StyleAxis.maxPick]，超了顶掉最早选的那条（比"点了没反应"友好，也让"以最后选中的为准"始终成立）；
     * ② 互斥组（高甜/虐心/冷峻）内自动摘掉其它档，避免"又甜又虐又冷"精神分裂。
     */
    fun toggle(style: NarrativeStyle, axis: StyleAxis, value: String): NarrativeStyle {
        val cur = style.valueOf(axis.key)
        val next = if (value in cur) {
            cur - value
        } else {
            val group = axis.conflicts.firstOrNull { value in it }
            var base = if (group != null) cur.filterNot { it in group } else cur
            if (base.size >= axis.maxPick) base = base.drop(base.size - axis.maxPick + 1)
            base + value
        }
        return style.withValues(axis.key, next)
    }

    /**
     * 一次改档（两个界面共用，避免各写一份）：
     * - 多选轴走 [toggle]（再点已选中的取消）；
     * - 单选轴**再点已选中的那一档也取消**，回到默认档（口径：选项要能关掉）；
     *   点默认档本身是空转，点别的档就是换档。
     * - 空串一律当"清空本轴"。
     */
    fun change(style: NarrativeStyle, axis: StyleAxis, value: String): NarrativeStyle {
        if (axis.multi) return if (value.isBlank()) pick(style, axis, "") else toggle(style, axis, value)
        val cur = style.valueOf(axis.key).firstOrNull().orEmpty()
        return pick(style, axis, if (value.isNotBlank() && value == cur) "" else value)
    }

    /** 一键填轴（整份替换，避免"悬疑剧场"里残留上一套的"高甜"） */
    val PRESETS: List<StylePreset> = listOf(
        StylePreset(
            "甜宠日常", "丰沛 · 文学书面 · 高甜",
            NarrativeStyle(
                pacing = "", density = "rich", prose = "literary", moods = listOf("sweet"), agency = ""
            )
        ),
        StylePreset(
            "悬疑剧场", "慢镜头 · 电影分镜 · 悬疑 · 强导演",
            NarrativeStyle(
                pacing = "slow", density = "", prose = "cinematic", moods = listOf("mystery"), agency = "director"
            )
        ),
        StylePreset(
            "极速爽文", "快进 · 精简 · 口语生活 · 热血 · 强导演",
            NarrativeStyle(
                pacing = "fast", density = "lean", prose = "casual", moods = listOf("hot"), agency = "director"
            )
        ),
        StylePreset(
            "沉浸小说", "慢镜头 · 丰沛 · 文学书面",
            NarrativeStyle(pacing = "slow", density = "rich", prose = "literary")
        ),
        StylePreset(
            "古风话本", "丰沛 · 古风 · 冷峻 · 章节式",
            NarrativeStyle(
                density = "rich", prose = "ancient", moods = listOf("cold"), timeFlow = "chapter"
            )
        )
    )

    /**
     * 风格 → 注入 system prompt 的【叙事风格】段（全默认时返回空串，调用处据此跳过注入）。
     *
     * 组装规则是冲突仲裁：每档自带"必须遵守/禁止项"措辞，
     * 节奏与篇幅相互独立、各自分工，并给出慢镜头+精简 / 快进+丰沛两种对角组合的写法；
     * 情感基调只着色，不改节奏篇幅与角色核心性格；末尾统一兜底一句，
     * 保证任何风格都压不过基础提示词的视角边界与排版规则。
     */
    fun buildHint(style: NarrativeStyle, notice: String = ""): String {
        val parts = AXES.flatMap { axis ->
            // 多选轴按**选择顺序**注入（不是表格顺序）：仲裁句说的是"以最后选中的为准"，
            // 只有最后一条确实排在最后，这句话才对模型有意义。
            orderedOptions(axis, style.valueOf(axis.key)).filter { it.hint.isNotBlank() }.map { it.hint }
        }
        if (parts.isEmpty()) return notice.trim()
        return buildString {
            append("【叙事风格】\n")
            parts.forEach { append(it).append('\n') }
            if (style.moods.isNotEmpty()) {
                append("基调只染情绪用词，不改节奏篇幅与性格（偶露温柔，不变成另一个人）。\n")
            }
            if (style.moods.size > 1) {
                append("两条基调尽量融合；确有冲突时，以最后选中的为准。\n")
            }
            if (style.pacing.isNotBlank() && style.density.isNotBlank()) {
                append("节奏管推进、篇幅管字数：慢镜头+精简＝短句留白（短而慢）；快进+丰沛＝每句有新意（长而快）。\n")
            }
            if (notice.isNotBlank()) append(notice.trim()).append('\n')
            // 兜底句要**按需**开口子：视角轴与时间处理（章节小标题）本来就是"改前述约定"的轴，
            // 无脑写"不改变前述视角约定与排版规则"会把这两根轴刚拿到的口子又堵回去。
            // 只在真的动了这两轴时才用长句，其余组合保持短句（与"空轴不注入"同一条纪律）。
            if (style.pov.isNotBlank() || style.timeFlow.isNotBlank()) {
                append("以上只调节表达方式，不改变前述视角约定与排版规则的其余部分；上文另行指定的叙事视角与时间处理按其执行。")
            } else {
                append("以上只调节表达方式，不改变前述视角约定与排版规则。")
            }
        }
    }

    /**
     * 本轮「硬性要求」短句 —— 由调用方贴在**用户本轮消息末尾**（详见 `AiClient.chatStream` 的 tailHint）。
     *
     * 为什么需要它（2026-09-16 真 key 实测，doubao-seed-character，同一段历史）：
     * 「篇幅=丰沛（单条 250~500 字）」放在系统提示词最末时，连续 8 次回复落在 85~131 字（达标 0/8）——
     * 系统提示词后面还跟着几十条历史，全是清一色短回复，近因在历史那边，不在系统提示词里；
     * 加一句可核对的硬指标贴到用户消息末尾后，均值 250~300 字、多数落在要求区间内（达标 3/5~3/4）。
     *
     * 只对**有可核对指标**的档位生成（目前是篇幅密度两档）：这类要求模型能自查，
     * 说"必须 250 字以上"才有效；"写得更文学"这种无处核对的形容词放在末尾只是噪音。
     */
    fun hardReminder(style: NarrativeStyle): String {
        val parts = AXES.flatMap { axis ->
            orderedOptions(axis, style.valueOf(axis.key)).mapNotNull { it.enforce.ifBlank { null } }
        }
        if (parts.isEmpty()) return ""
        val labels = labelsOf(style).joinToString(" · ")
        return "【本条硬性要求】按「$labels」写：${parts.joinToString("")}"
    }

    /** 输入栏生效 chip / 面板摘要用的短标签（多选轴合成一个 chip） */
    fun labelsOf(style: NarrativeStyle): List<String> = AXES.mapNotNull { axis ->
        val labels = orderedOptions(axis, style.valueOf(axis.key)).map { it.label }
        labels.takeIf { it.isNotEmpty() }?.joinToString("·")
    }

    /** 已选档位按"选择顺序"排好（单选轴只有一档、顺序无所谓；多选轴的顺序即选择先后） */
    private fun orderedOptions(axis: StyleAxis, picked: List<String>): List<StyleOption> =
        if (axis.multi) {
            picked.mapNotNull { v -> axis.options.firstOrNull { it.value == v } }
        } else {
            axis.options.filter { it.value in picked }
        }
}
