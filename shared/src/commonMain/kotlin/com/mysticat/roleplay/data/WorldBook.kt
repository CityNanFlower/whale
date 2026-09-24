package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable

/**
 * 世界书（lorebook / ST World Info）：关键词触发的背景资料条目集合。
 *
 * **为什么它是"卡的一部分"而不是 App 里另开一个库**：ST 的 `character_book` 本来就住在角色卡里，
 * 跟着卡走（导出送给别人、导进别家应用都带着它）。做成全局库会让"这张卡的世界观"找不到归属。
 *
 * 字段口径照抄 SillyTavern（兼容是入场券，自造格式会把这个生态的卡排除在外）：
 * - 命中＝**主关键词任一命中**（`keys`，OR）；`selective` 为真时**还要再命中一个副关键词**
 *   （`secondaryKeys`，OR）。`constant` 为真则无条件注入。
 * - `secondaryKeys` 为空**等于没有副关键词要求** —— 真卡实测（SillyTavern 自带
 *   `default_Seraphina.png`，`selective: true` 且 `secondary_keys: []`）就是这个形状，
 *   若按"必须命中副关键词"处理，那张卡一条都触发不了。
 * - 插入位置 [position]（0/1 在系统提示词的角色设定前/后，4 按 [depth] 插进消息数组）。
 */
@Serializable
data class WorldBook(
    /** 书的名字（ST 里就是 `name`，例如「Eldoria」）；纯展示，不进 prompt */
    val name: String = "",
    /** 书的说明（v2 规范有 `description`）；纯展示 */
    val description: String = "",
    val entries: List<WorldBookEntry> = emptyList(),
    /** 书级默认扫描深度（最近 N 条消息）；`null` = 用 [WorldBookEngine.DEFAULT_SCAN_DEPTH] */
    val scanDepth: Int? = null,
    /**
     * 书级的**未识别字段**（原样 JSON 对象，空串＝没有）。
     *
     * 与 [WorldBookEntry.extraRaw] 同一个理由：v2 规范要求"不认识的键不许销毁"，
     * 所以没解析的键原样存一份、导出时写回去。
     */
    val extraRaw: String = "",
) {
    fun isEmpty(): Boolean = entries.isEmpty() && name.isBlank() && description.isBlank()
}

/**
 * 世界书的一条条目。
 *
 * **字段来源有两套形状**（都得住在一个模型里）：
 * - v2 规范字段：`keys` / `content` / `enabled` / `insertion_order` / `position`（字符串
 *   `before_char` / `after_char`）…
 * - SillyTavern 自家的 `extensions` 镜像：`position`（整数 0/1/4）、`depth`、`scan_depth`、
 *   `match_whole_words`、`case_sensitive` 等。
 *
 * 真卡实测（`default_Seraphina.png`）**两套同时存在**，且 `position` 在规范里是字符串、
 * 在镜像里是整数 —— 读的时候两处都认（规范优先），写的时候两处都写、保持一致。
 */
@Serializable
data class WorldBookEntry(
    /** ST 的 `id`（同一本书内唯一，用于排序稳定与界面定位） */
    val id: Int = 0,
    /** 显示名：规范里有 `name`，ST 原生更常用 `comment`（真卡只有 `comment`） */
    val name: String = "",
    val comment: String = "",
    val content: String = "",
    val enabled: Boolean = true,
    /** 主关键词（任一命中即候选）；`keys` 与 `key` 两种拼写都认 */
    val keys: List<String> = emptyList(),
    /** 副关键词（`selective` 为真时要求再命中其一；为空＝没有这个要求） */
    val secondaryKeys: List<String> = emptyList(),
    /** 可选：只在命中副关键词时才注入（真卡 `selective: true` + 空副关键词） */
    val selective: Boolean = false,
    /** 常驻：不看关键词，每轮都注入 */
    val constant: Boolean = false,
    /** 关键词是否区分大小写（默认不区分——ST 默认亦如此） */
    val caseSensitive: Boolean = false,
    /**
     * 全词匹配。`null` = **用鲸鱼的默认：子串匹配**。
     *
     * ST 的默认是"全词匹配开"，但它对中日韩文字有害（中文没有词边界，开了几乎永远不命中）——
     * 中文场景必须按子串走。真卡这里是 `null`，所以走我们的默认。
     */
    val matchWholeWords: Boolean? = null,
    /** 关键词按正则解释（ST 的 `use_regex`；真卡四条全是 `true`） */
    val useRegex: Boolean = false,
    /** 同一位置内的插入顺序（小的在前；真卡全是 100） */
    val insertionOrder: Int = 100,
    /** 插入位置，见 [POSITION_BEFORE_CHAR] 等常量 */
    val position: Int = POSITION_BEFORE_CHAR,
    /** 只在 [POSITION_AT_DEPTH] 时有意义：从最后一条消息往前数，插在第 D 条之前 */
    val depth: Int = 4,
    /** 条目自己的扫描深度（覆盖书级）；`null` = 跟随书级 */
    val scanDepth: Int? = null,
    /**
     * 本条**未识别字段**的原样 JSON（空串＝没有）。
     *
     * 为什么要有它：ST 的条目有二十来个字段（`probability` / `group` / `sticky` / `cooldown` /
     * `automation_id`…），本轮只做"关键词触发 + 位置深度"，其余**既不实现也不销毁**——留在
     * 这里，导出时逐字写回。少了它，用户的卡被我们导一次就永远丢了那些设置。
     */
    val extraRaw: String = "",
    /**
     * 本条 `extensions` 镜像里**未识别键**的原样 JSON（空串＝没有）。
     *
     * 与 [extraRaw] 分两份存，因为导出时镜像要**重新生成**：我们改过的 `depth`/`position`
     * 必须以模型值为准，而 `probability`/`sticky` 这些没实现的又必须逐字保住。混在一起存
     * 就会出现"改了深度但镜像里还是旧值"——SillyTavern 读的正是镜像那一份。
     */
    val extensionsRaw: String = "",
) {
    companion object {
        /** 角色设定**之前**（ST `position: 0` / 规范 `before_char`） */
        const val POSITION_BEFORE_CHAR = 0

        /** 角色设定**之后**（ST `position: 1` / 规范 `after_char`） */
        const val POSITION_AFTER_CHAR = 1

        /** ST 的"作者注之前"——鲸鱼没有作者注，按"角色设定之前"处理 */
        const val POSITION_BEFORE_AN = 2

        /** ST 的"作者注之后"——同上，按"角色设定之后"处理 */
        const val POSITION_AFTER_AN = 3

        /** 插进消息数组（按 [depth]） */
        const val POSITION_AT_DEPTH = 4
    }

    /** 能不能命中（没关键词又非常驻的条目是死条目，界面该提示作者） */
    fun canTrigger(): Boolean = constant || keys.isNotEmpty()

    /** 界面上显示什么名字 */
    fun label(): String = name.ifBlank { comment }
}

/**
 * 一条命中的世界书条目 ＋ **它为什么命中**。
 *
 * [reason] 不是给模型看的，是给用户看的（BYOK 应用的记账本："这一轮我多花了这些字数，
 * 是因为哪个词触发的"）。命中可见性面板读它。
 */
data class WorldBookHit(
    val entry: WorldBookEntry,
    /** 命中的那个关键词；常驻条目为空串 */
    val matchedKey: String,
) {
    val isConstant: Boolean get() = entry.constant
}
