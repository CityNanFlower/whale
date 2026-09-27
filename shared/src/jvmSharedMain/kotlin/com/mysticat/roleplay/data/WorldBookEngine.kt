package com.mysticat.roleplay.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * 世界书（lorebook）引擎：SillyTavern `character_book` 的**读、写、命中判断**。
 *
 * 三件事都在这一个文件里，因为它们共享同一套字段口径：口径一旦分家，就会出现
 * "导入时认得 depth、命中时按另一个默认值算"这种只有用户能发现的错位。
 *
 * **兼容优先**（入场券）：能读 ST 的卡、能写回 ST 认的卡。自造的格式会把整个卡生态挡在门外，
 * 所以宁可照抄它的怪癖（例如 `position` 在规范里是字符串、在 `extensions` 镜像里是整数，两套都写）。
 *
 * **不实现的字段不销毁**：ST 条目有二十来个字段（`probability` / `group` / `sticky` / `cooldown`
 * / `automation_id`…），本轮只做"关键词触发 ＋ 插入位置与深度"，其余原样留在 `extraRaw` 里、
 * 导出时逐字写回 —— 这是 v2 规范的硬要求（未知键不许销毁），也是"用户的卡被我们导一次就掉设置"
 * 这类事故的唯一防线。
 *
 * ⚠ **刻意不实现的三项**（与 ST 的已知差异，对外要说清）：
 * 1. `probability` 随机命中 —— 纯本地 RP 里"同一句话有时记得有时不记得"是 bug 观感；
 * 2. `group` / `group_weight` 互斥分组与 `selectiveLogic` 的非 AND_ANY 档（副关键词只做"任一命中"）；
 * 3. `prevent_recursion` / `exclude_recursion` / `delay_until_recursion` —— 我们不递归扫描
 *    （注入的条目不再参与命中判断），所以它们天然无副作用，字段照保真。
 */
object WorldBookEngine {

    private val json = Json { ignoreUnknownKeys = true }

    /** 书里没写扫描深度时的默认值：ST 的默认也是 4 条消息 */
    const val DEFAULT_SCAN_DEPTH = 4

    /** 关键词当正则解释时的长度上限（超长一律退回子串匹配：用户手写的东西不该把我们卡死） */
    private const val MAX_REGEX_KEY_LEN = 200

    /** 注入段的名字（装配器与命中面板共用同一个字面量，别各写一份） */
    const val SECTION = "世界书"

    /** 段的括注：说明"这是资料、不是你该复述的台词" */
    const val SECTION_HINT = "（与本轮相关的背景资料，当作你已经知道；不要直接复述原文）"

    // ── 读：ST character_book → 模型 ─────────────────────────────────────────

    /**
     * 解析一份 `character_book`（可能为 null / 不是对象）。
     * 空书（没有条目又没有名字）返回 null —— 让调用方拿到的要么是本有用的书，要么是"没有"。
     *
     * `entries` 两种形状都收：v2 的**数组**，以及老版 World Info 导出的 **`uid → 条目` 对象**
     * （SillyTavern 世界书面板导出的就是这个形状）。不收后者，"导入世界书 JSON"就只在自家文件上能用。
     */
    fun fromTavernJson(el: JsonElement?): WorldBook? {
        val o = el as? JsonObject ?: return null
        val entries = when (val e = o["entries"]) {
            is JsonArray -> e.mapNotNull { entry(it as? JsonObject) }
            is JsonObject -> e.values.mapNotNull { entry(it as? JsonObject) }
            else -> emptyList()
        }
        val known = setOf("name", "description", "entries", "scan_depth")
        val book = WorldBook(
            name = str(o["name"]),
            description = str(o["description"]),
            entries = entries,
            scanDepth = intOrNull(o["scan_depth"]),
            extraRaw = leftover(o, known)
        )
        return book.takeIf { !it.isEmpty() }
    }

    private fun entry(o: JsonObject?): WorldBookEntry? {
        if (o == null) return null
        val ext = o["extensions"] as? JsonObject
        // position 两套形状：规范 `"before_char"` / 镜像 `0`。
        // **规范优先，但"按深度插入"必须以镜像为准**：规范那个字符串只有 before/after 两个值、
        // 装不下 `at_depth`（我们写出去的就是"规范 after_char ＋ 镜像 4"），若一律信规范，
        // 自己导出的书再导回来位置就悄悄从"插进消息里"退化成"角色设定后"。
        // SillyTavern 自己的导入也是优先读 `extensions.position`，这个优先级与它一致。
        val specPos = positionSpec(o["position"])
        val mirrorPos = positionSpec(ext?.get("position"))
        val position = if (mirrorPos == WorldBookEntry.POSITION_AT_DEPTH) mirrorPos
        else specPos ?: mirrorPos ?: WorldBookEntry.POSITION_BEFORE_CHAR
        // 键名有三套来源：v2 规范（`keys` / `secondary_keys` / `insertion_order` / `enabled`）、
        // ST 的 extensions 镜像、老版 World Info 导出的驼峰（`key` / `keysecondary` / `order` / `disable` /
        // `scanDepth`…）。三套都认，否则"同一本书从卡里导入能用、从世界书面板导出再导入就空"。
        val known = setOf(
            "id", "uid", "name", "comment", "content", "enabled", "disable", "keys", "key", "secondary_keys",
            "keysecondary", "selective", "constant", "case_sensitive", "caseSensitive", "match_whole_words",
            "matchWholeWords", "use_regex", "useRegex", "insertion_order", "order", "position", "depth",
            "scan_depth", "scanDepth", "extensions"
        )
        // 镜像里我们认识的键；剩下的（probability / group / sticky…）原样留在 extraRaw 里
        val extKnown = setOf(
            "position", "depth", "scan_depth", "match_whole_words", "case_sensitive"
        )
        return WorldBookEntry(
            id = pickInt(o, "id", "uid") ?: 0,
            name = str(o["name"]),
            comment = str(o["comment"]),
            content = str(o["content"]),
            // 老版的 `disable: true` 与 v2 的 `enabled: false` 是同一件事的两面
            enabled = pickBool(o, "enabled") ?: pickBool(o, "disable")?.let { !it } ?: true,
            keys = pickList(o, "keys", "key"),
            secondaryKeys = pickList(o, "secondary_keys", "keysecondary"),
            selective = bool(o["selective"], false),
            constant = bool(o["constant"], false),
            caseSensitive = pickBool(o, "case_sensitive", "caseSensitive")
                ?: boolOrNull(ext?.get("case_sensitive")) ?: false,
            matchWholeWords = pickBool(o, "match_whole_words", "matchWholeWords")
                ?: boolOrNull(ext?.get("match_whole_words")),
            useRegex = pickBool(o, "use_regex", "useRegex") ?: false,
            insertionOrder = pickInt(o, "insertion_order", "order") ?: 100,
            position = position,
            depth = intOrNull(ext?.get("depth")) ?: intOrNull(o["depth"]) ?: 4,
            scanDepth = pickInt(o, "scan_depth", "scanDepth") ?: intOrNull(ext?.get("scan_depth")),
            extraRaw = leftover(o, known),
            // 镜像的原样副本另存：我们把认识的键重新生成、不认识的写回，所以这里只留"不认识的"
            // —— 见 toTavernJson。这里先按"整个镜像"存，导出时靠 extKnown 再摘一遍。
            extensionsRaw = ext?.let { leftover(it, extKnown) }.orEmpty()
        )
    }

    /**
     * 从一份**独立 JSON 文本**里取出世界书（编辑器的「导入世界书」入口）。
     *
     * 三种文件都能吃：
     * 1. 整张角色卡（读 v2 的 `data.character_book`，与卡导入读的是同一处）；
     * 2. 平铺的卡（顶层就有 `character_book`）；
     * 3. **书本身** —— SillyTavern 世界书面板导出的文件（老版 `{entries:{uid:…}}`、新版 book 形状）。
     *
     * 顺序是"先卡后书"：卡里的书藏在 `character_book` 那一层，先试它不会误判；反过来先当书读，
     * 一张平铺的卡会被当成本书（卡名落在书名上、条目为空）。兜底那步**要求必须有条目**，
     * 否则任何一份 JSON 都能变成一本空书、把用户手上的书覆盖掉。
     */
    fun fromStandaloneJson(text: String): WorldBook? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val cardBook = (root["data"] as? JsonObject)?.get("character_book") ?: root["character_book"]
        fromTavernJson(cardBook)?.let { return it }
        return fromTavernJson(root)?.takeIf { it.entries.isNotEmpty() }
    }

    /**
     * 独立导出：一整本书写成 JSON 文本（编辑器的「导出世界书」入口）。
     *
     * 写的就是卡里 `data.character_book` 那一份内容（同一个 [toTavernJson]），所以导出的文件
     * 既能当"备份这本书"，也能直接贴进别的应用；导回来走 [fromStandaloneJson]。
     */
    fun toStandaloneJson(book: WorldBook): String =
        json.encodeToString(JsonObject.serializer(), toTavernJson(book))

    /**
     * 编辑器的"改没改过"判据：整本书编码成 JSON 比较。
     *
     * 用它而不是手拼字段列表：以后给条目加字段时若忘了同步判据，症状是"改了却不提示未保存、
     * 保存按钮一直是灰的"——这个 bug 只在用户那儿出现，编译和自检都拦不住。
     */
    fun signature(book: WorldBook?): String =
        if (book == null) "" else json.encodeToString(WorldBook.serializer(), book)

    // ── 写：模型 → ST character_book ─────────────────────────────────────────

    /** 把一本书写成 ST 认的 `character_book` 对象（`data.character_book` 直接放它）。 */
    fun toTavernJson(book: WorldBook): JsonObject = buildJsonObject {
        if (book.name.isNotBlank()) put("name", book.name)
        if (book.description.isNotBlank()) put("description", book.description)
        book.scanDepth?.let { put("scan_depth", it) }
        // 书级未识别字段原样写回（不覆盖我们写的键）
        merge(book.extraRaw, skip = setOf("name", "description", "entries", "scan_depth"))
        put("entries", JsonArray(book.entries.map { entryJson(it) }))
    }

    private fun entryJson(e: WorldBookEntry): JsonObject {
        // 一套值写两处：规范字段（别的应用读它）＋ ST 的 extensions 镜像（SillyTavern 读它）。
        // 两处不一致是"这张卡在别家能用、在 ST 里定位错"的根源，所以都从同一个模型值生成。
        return buildJsonObject {
            put("id", e.id)
            if (e.name.isNotBlank()) put("name", e.name)
            if (e.comment.isNotBlank()) put("comment", e.comment)
            put("keys", JsonArray(e.keys.map { JsonPrimitive(it) }))
            put("secondary_keys", JsonArray(e.secondaryKeys.map { JsonPrimitive(it) }))
            put("content", e.content)
            put("enabled", e.enabled)
            put("constant", e.constant)
            put("selective", e.selective)
            put("insertion_order", e.insertionOrder)
            put("position", positionName(e.position))
            // 默认值不写：v2 卡里 `use_regex` / `case_sensitive` 缺省就是 false，
            // 每张卡都挂一串 false 是纯噪声（真卡也只在真的用到时才写 use_regex）
            if (e.useRegex) put("use_regex", true)
            if (e.caseSensitive) put("case_sensitive", true)
            // 条目级未识别字段原样写回
            merge(e.extraRaw, skip = SPEC_KEYS)
            put(
                "extensions", buildJsonObject {
                    put("position", mirrorPosition(e.position))
                    put("depth", e.depth)
                    if (e.caseSensitive) put("case_sensitive", true)
                    e.matchWholeWords?.let { put("match_whole_words", it) }
                    e.scanDepth?.let { put("scan_depth", it) }
                    merge(e.extensionsRaw, skip = MIRROR_KEYS)
                }
            )
        }
    }

    private val SPEC_KEYS = setOf(
        "id", "name", "comment", "keys", "key", "secondary_keys", "content", "enabled", "constant",
        "selective", "insertion_order", "position", "use_regex", "case_sensitive", "extensions"
    )
    private val MIRROR_KEYS = setOf("position", "depth", "scan_depth", "match_whole_words", "case_sensitive")

    /** 规范里的 position 是字符串；只有"角色设定前/后"两个合法值，按深度插的那种写 `after_char` */
    private fun positionName(p: Int): String =
        if (p == WorldBookEntry.POSITION_BEFORE_CHAR || p == WorldBookEntry.POSITION_BEFORE_AN) "before_char"
        else "after_char"

    /** 镜像里的 position 是整数：0 前 / 1 后 / 4 按深度 */
    private fun mirrorPosition(p: Int): Int = when (p) {
        WorldBookEntry.POSITION_BEFORE_AN -> WorldBookEntry.POSITION_BEFORE_CHAR
        WorldBookEntry.POSITION_AFTER_AN -> WorldBookEntry.POSITION_AFTER_CHAR
        else -> p
    }

    // ── 命中 ────────────────────────────────────────────────────────────────

    /**
     * 扫描文本 = **角色卡自己的设定** ＋ **最近的消息**。
     *
     * 卡自己的文本也要参与扫描（这点容易漏）：作者写「Eldoria 的森林」条目，而世界观里本来
     * 就提过 Eldoria —— 只扫聊天的话，卡片自带的设定永远触发不了自己书里的条目。
     * 刻意**不含** `mesExample`（对话示例是语气示范，把它当资料会让示例里提过的词每轮都命中）。
     */
    fun cardScanText(card: CharacterCard): String = listOf(
        card.description, card.personality, card.scenario, card.persona
    ).filter { it.isNotBlank() }.joinToString("\n")

    /** 便利入口：直接吃消息列表（聊天链路用这个；扫描文本 = 卡设定 ＋ 最近 N 条） */
    fun hits(book: WorldBook?, card: CharacterCard, recentMessages: List<ChatMessage>): List<WorldBookHit> =
        hits(book, cardScanText(card), recentMessages.map { it.content })

    /**
     * 纯函数核心：给出命中的条目（已按插入顺序排好、已按 [WorldBookEntry.enabled] 过滤）。
     *
     * [recentTexts] 按时间正序（最后一条是最近的）；每条消息的扫描深度可不同，所以这里吃整个列表。
     */
    fun hits(book: WorldBook?, cardText: String, recentTexts: List<String>): List<WorldBookHit> {
        val entries = book?.entries.orEmpty()
        if (entries.isEmpty()) return emptyList()
        val out = ArrayList<WorldBookHit>(entries.size)
        for (e in entries) {
            if (!e.enabled) continue
            val depth = e.scanDepth ?: book?.scanDepth ?: DEFAULT_SCAN_DEPTH
            // 扫描深度按"最近 N 条"算；depth <= 0 表示只看卡片自身的设定文本
            val text = if (depth <= 0) cardText
            else cardText + "\n" + recentTexts.takeLast(depth).joinToString("\n")
            val key = matchedKey(e, text) ?: continue
            out += WorldBookHit(e, key)
        }
        // 插入顺序：小的在前（ST 同口径）；同序保持卡里的原有先后（sortedBy 是稳定排序）
        return out.sortedBy { it.entry.insertionOrder }
    }

    /** 返回命中的关键词；常驻条目返回空串；没命中返回 null */
    private fun matchedKey(e: WorldBookEntry, text: String): String? {
        if (e.constant) return ""
        if (text.isEmpty()) return null
        val primary = e.keys.firstOrNull { keyMatches(e, it, text) } ?: return null
        // 副关键词：`selective` 且**真的写了副关键词**才要求。副关键词为空却要求命中，
        // 是"这张卡一条都不触发"的经典原因（真卡就是 `selective: true` + 空副关键词）。
        if (!e.selective || e.secondaryKeys.isEmpty()) return primary
        val secondary = e.secondaryKeys.firstOrNull { keyMatches(e, it, text) }
        return if (secondary == null) null else "$primary + $secondary"
    }

    private fun keyMatches(e: WorldBookEntry, rawKey: String, text: String): Boolean {
        val key = rawKey.trim()
        if (key.isEmpty()) return false
        if (e.useRegex && key.length <= MAX_REGEX_KEY_LEN) {
            val opts = if (e.caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            // 正则写坏了（用户手写的东西什么形状都有）就退化成子串，绝不让一条坏正则废掉整本书
            runCatching { Regex(key, opts) }.getOrNull()?.let { return it.containsMatchIn(text) }
        }
        val hay = if (e.caseSensitive) text else text.lowercase()
        val needle = if (e.caseSensitive) key else key.lowercase()
        // 全词匹配只对拉丁词有意义：中文没有词边界，开了几乎永远不命中（ST 官方文档也提醒这点），
        // 所以**关键词含非 ASCII 字符时一律按子串**——这是刻意的、对中文更友好的偏离。
        if (e.matchWholeWords == true && key.all { it.code < 128 }) {
            val pattern = "(?<![\\p{L}\\p{N}])" + Regex.escape(needle) + "(?![\\p{L}\\p{N}])"
            val opts = if (e.caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
            return runCatching { Regex(pattern, opts).containsMatchIn(hay) }.getOrDefault(false)
        }
        return hay.contains(needle)
    }

    // ── 未命中诊断（命中可见性面板用）───────────────────────────────────────

    /**
     * 一条未命中条目的诊断。命中面板用它回答"这条为什么没触发"。
     * [reasons] 为空 ＝ 没查出毛病（最常见就是关键词压根没出现）——这种情况面板给通用解释。
     */
    data class MissDiagnosis(val entry: WorldBookEntry, val reasons: List<String>)

    /**
     * 对书里**没命中**的启用条目逐条找原因：死条目 / 正则写错 / 大小写挡住 / 全词匹配挡住 /
     * 副关键词没凑齐 / 近似键（变体或笔误 的「艾瑟兰/艾瑟林」就是这类）。
     *
     * 判定口径与 [hits] 完全同源（同一个 [keyMatches]、同一条扫描文本），面板不许自己再算一遍。
     */
    fun diagnoseMisses(book: WorldBook, cardText: String, recentTexts: List<String>): List<MissDiagnosis> {
        val hitIds = hits(book, cardText, recentTexts).mapTo(HashSet()) { it.entry.id }
        return book.entries.filter { it.enabled && it.id !in hitIds }.map { e ->
            MissDiagnosis(e, missReasons(e, scanTextFor(e, book, cardText, recentTexts)))
        }
    }

    /** 便利入口：直接吃消息列表（命中面板用这个；口径与上面的 [hits] 卡便利入口一致） */
    fun diagnoseMisses(book: WorldBook?, card: CharacterCard, recentMessages: List<ChatMessage>): List<MissDiagnosis> =
        if (book == null) emptyList()
        else diagnoseMisses(book, cardScanText(card), recentMessages.map { it.content })

    /** 与 [hits] 同一条扫描文本（每条自己的扫描深度；depth<=0 只看卡片设定） */
    private fun scanTextFor(e: WorldBookEntry, book: WorldBook, cardText: String, recentTexts: List<String>): String {
        val depth = e.scanDepth ?: book.scanDepth ?: DEFAULT_SCAN_DEPTH
        return if (depth <= 0) cardText
        else cardText + "\n" + recentTexts.takeLast(depth).joinToString("\n")
    }

    private fun missReasons(e: WorldBookEntry, text: String): List<String> {
        if (!e.canTrigger()) return listOf("没有关键词、也不是常驻 —— 这一条永远不会触发")
        val reasons = ArrayList<String>(2)
        val primary = e.keys.firstOrNull { keyMatches(e, it, text) }
        if (e.selective && e.secondaryKeys.isNotEmpty()) {
            val secondary = e.secondaryKeys.firstOrNull { keyMatches(e, it, text) }
            when {
                primary != null ->
                    reasons += "主关键词「$primary」出现了，但副关键词一个都没出现（这一条要求两者同时命中）"
                secondary != null ->
                    reasons += "副关键词「$secondary」出现了，但主关键词一个都没出现（这一条要求两者同时命中）"
            }
        }
        if (primary == null) {
            if (e.caseSensitive) {
                val loose = e.keys.firstOrNull { keyMatches(e.copy(caseSensitive = false), it, text) }
                if (loose != null) reasons += "文本里有「$loose」，只是大小写不同 —— 关掉「区分大小写」就能命中"
            }
            if (e.matchWholeWords == true && !e.useRegex) {
                val plain = e.keys.firstOrNull { keyMatches(e.copy(matchWholeWords = false), it, text) }
                if (plain != null) reasons += "文本里有「$plain」，但它前后紧挨着别的字词 —— 关掉「全词匹配」就能命中"
            }
            if (e.useRegex) {
                val bad = e.keys.filter { it.isNotBlank() && runCatching { Regex(it) }.isFailure }
                if (bad.isNotEmpty())
                    reasons += "正则写错了：${bad.joinToString("、")}（坏正则会退回普通文本匹配，基本等于不命中）"
            }
            if (reasons.isEmpty() && !e.useRegex) {
                e.keys.firstNotNullOfOrNull { nearMiss(it, text) }?.let { (window, dist) ->
                    reasons += "没出现完整的关键词，但文本里有近似的「$window」（与关键词只差 $dist 个字，可能是变体或笔误）"
                }
            }
        }
        return reasons
    }

    /**
     * 近似键：在扫描文本里找一个与关键词**编辑距离 ≤ 上限**的等长窗口。
     * 关键词太短（<3 字）误报率太高、太长（>24 字）扫不动也不值得，都不查。
     */
    private fun nearMiss(rawKey: String, text: String): Pair<String, Int>? {
        val key = rawKey.trim()
        if (key.length < 3 || key.length > 24) return null
        val cap = if (key.length >= 6) 2 else 1
        var best: Pair<String, Int>? = null
        var i = 0
        val last = text.length - key.length
        while (i <= last) {
            val window = text.substring(i, i + key.length)
            val d = boundedLevenshtein(window, key, cap)
            if (d in 1..cap && (best == null || d < best.second)) {
                best = window to d
                if (d == 1) return best
            }
            i++
        }
        return best
    }

    /** 编辑距离，超过 [cap] 提前放弃返回 cap+1（窗口是几个字的短串，够用了） */
    private fun boundedLevenshtein(a: String, b: String, cap: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > cap) return cap + 1
        var prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > cap) return cap + 1
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    // ── 从文本批量生成条目（编辑器「从文本生成条目」用）─────────────────────

    /** 分段方式；界面 chip 与 [entriesFromText] 共用这一份口径 */
    enum class WorldBookSplitMode(val label: String) {
        /** 连续的非空行算一段（设定文本最常见的形状） */
        PARAGRAPH("按空行分段"),
        /** 一行一条 */
        LINE("按行拆分"),
        /** 标题行（`【】`/`#`/`标题：`）开新段，标题后的行都归它 */
        HEADING("按标题分段"),
    }

    private val MARKDOWN_TITLE = Regex("^#{1,4}\\s*(.+?)\\s*#*\\s*$")
    private val BRACKET_TITLE = Regex("^【(.{1,24}?)】\\s*(.*)$")
    private val COLON_TITLE = Regex("^(.{1,16}?)\\s*[：:]\\s*$")

    /** 一段资料：[title] 为 null 表示首行没认出标题，整段都是正文 */
    private data class TextBlock(val title: String?, val body: String)

    /**
     * 把一段设定文本拆成世界书条目（纯本地启发式，不花钱 —— 花真钱的"自动拆书"是另一条待办）。
     *
     * 认标题的三种形状：markdown `# 标题`、`【标题】`（后面可以跟同行的正文）、整行只有 `标题：`。
     * [titleAsKey] 为真时标题同时作为关键词 —— 命中面板诊断的"变体"问题要靠作者自己补键。
     * 正文为空的段（纯标题没有内容）不生成。
     */
    fun entriesFromText(
        text: String,
        mode: WorldBookSplitMode,
        titleAsKey: Boolean,
        startId: Int,
    ): List<WorldBookEntry> {
        val blocks: List<TextBlock> = when (mode) {
            WorldBookSplitMode.LINE -> text.lines().mapNotNull { line ->
                line.trim().takeIf { it.isNotEmpty() }?.let { extractTitle(it).let { (t, b) -> TextBlock(t, b) } }
            }
            WorldBookSplitMode.PARAGRAPH -> text.split(Regex("\\r?\\n[ \\t]*\\r?\\n+"))
                .map { it.trim() }.filter { it.isNotEmpty() }
                .map { extractTitle(it).let { (t, b) -> TextBlock(t, b) } }
            WorldBookSplitMode.HEADING -> splitByHeading(text)
        }
        return blocks.mapIndexedNotNull { i, block ->
            val body = block.body.trim()
            if (body.isEmpty()) return@mapIndexedNotNull null
            WorldBookEntry(
                id = startId + i,
                name = block.title.orEmpty(),
                content = body,
                keys = if (titleAsKey && block.title != null) listOf(block.title) else emptyList(),
            )
        }
    }

    private fun splitByHeading(text: String): List<TextBlock> {
        val blocks = ArrayList<TextBlock>()
        var curTitle: String? = null
        val curBody = StringBuilder()
        fun flush() {
            val body = curBody.toString().trim()
            if (body.isNotEmpty() || curTitle != null) blocks += TextBlock(curTitle, body)
            curBody.clear()
        }
        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) {
                if (curBody.isNotEmpty()) curBody.append('\n')
                return@forEach
            }
            val bracket = BRACKET_TITLE.matchEntire(line)
            when {
                MARKDOWN_TITLE.matches(line) || COLON_TITLE.matches(line) -> {
                    flush()
                    curTitle = (MARKDOWN_TITLE.find(line)?.groupValues?.get(1)
                        ?: COLON_TITLE.matchEntire(line)!!.groupValues[1]).trim()
                }
                bracket != null -> {
                    flush()
                    curTitle = bracket.groupValues[1].trim()
                    val rest = bracket.groupValues[2].trim()
                    if (rest.isNotEmpty()) curBody.append(rest)
                }
                else -> curBody.append(if (curBody.isEmpty()) line else "\n$line")
            }
        }
        flush()
        return blocks
    }

    /** 从块的首行提取标题；返回 (标题或 null, 剩余正文) */
    private fun extractTitle(block: String): Pair<String?, String> {
        val lines = block.lines()
        val first = lines.first().trim()
        MARKDOWN_TITLE.matchEntire(first)?.let {
            return it.groupValues[1].trim() to lines.drop(1).joinToString("\n").trim()
        }
        BRACKET_TITLE.matchEntire(first)?.let { m ->
            val rest = m.groupValues[2].trim()
            val body = (if (rest.isEmpty()) lines.drop(1) else listOf(rest) + lines.drop(1))
                .joinToString("\n").trim()
            return m.groupValues[1].trim() to body
        }
        COLON_TITLE.matchEntire(first)?.let {
            return it.groupValues[1].trim() to lines.drop(1).joinToString("\n").trim()
        }
        return null to block
    }

    // ── 分组（装配器用）────────────────────────────────────────────────────

    /**
     * 命中的条目按插入位置分三段。同一段内的顺序＝[hits] 的顺序（已按 insertion_order 排好）。
     *
     * [position] 为 ST 的"作者注前/后"（2/3）时归入前/后 —— 鲸鱼没有作者注这一层，
     * 这是**明确的降级**：位置大致对，但精确锚点不存在。
     */
    fun partition(hits: List<WorldBookHit>): WorldBookParts {
        val before = ArrayList<WorldBookEntry>()
        val after = ArrayList<WorldBookEntry>()
        val depth = LinkedHashMap<Int, MutableList<WorldBookEntry>>()
        for (h in hits) {
            when (h.entry.position) {
                WorldBookEntry.POSITION_BEFORE_CHAR, WorldBookEntry.POSITION_BEFORE_AN -> before += h.entry
                WorldBookEntry.POSITION_AT_DEPTH -> depth.getOrPut(h.entry.depth) { ArrayList() } += h.entry
                else -> after += h.entry
            }
        }
        return WorldBookParts(before, after, depth)
    }

    /** 命中结果的三个落点；[depth] 按深度升序（插入时从后往前算，顺序不影响结果） */
    data class WorldBookParts(
        val before: List<WorldBookEntry>,
        val after: List<WorldBookEntry>,
        val depth: Map<Int, List<WorldBookEntry>>
    ) {
        val isEmpty: Boolean get() = before.isEmpty() && after.isEmpty() && depth.isEmpty()
        val all: List<WorldBookEntry> get() = before + after + depth.values.flatten()
    }

    /**
     * `@depth` 条目该插到消息数组的哪个下标：`depth = D` 表示**从最后一条往前数第 D 条之前**
     * （D=0 = 追加到最后）。
     *
     * **下限钳到 1**：下标 0 是系统提示词本身，世界书不该挡在它前面（最坏退化成紧跟系统提示词）。
     * 抽成公开纯函数是为了能被自检直接断言 —— 这段索引算术以前是几个 `-` 和 `+` 混在一起，
     * 错一位只会表现为"模型偶尔没记住设定"，靠人眼永远看不出来。
     */
    fun depthInsertIndex(messageCount: Int, depth: Int): Int = (messageCount - depth).coerceIn(1, messageCount)

    // ── 小工具 ──────────────────────────────────────────────────────────────

    /** 未识别字段打包：把 [o] 里不在 [known] 中的键原样存成 JSON 串（空对象→空串） */
    private fun leftover(o: JsonObject, known: Set<String>): String {
        val rest = o.filterKeys { it !in known }
        if (rest.isEmpty()) return ""
        return json.encodeToString(JsonObject.serializer(), JsonObject(rest))
    }

    /** 把原样存下来的 [raw] 逐字写回，跳过 [skip]（我们已经用模型值写过的那几个键） */
    private fun JsonObjectBuilder.merge(raw: String, skip: Set<String>) {
        if (raw.isBlank()) return
        val o = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return
        o.forEach { (k, v) -> if (k !in skip) put(k, v) }
    }

    private fun str(el: JsonElement?): String = when (el) {
        is JsonPrimitive -> el.contentOrNull?.trim().orEmpty()
        else -> ""
    }

    /** 数字字段两套形状都认：`100` 与 `"100"`（ST 自己的导出里两种都出现过） */
    private fun intOrNull(el: JsonElement?): Int? {
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.contentOrNull?.trim()?.toIntOrNull()
    }

    /** 布尔两套形状都认：`true` 与 `"True"`（ST 的镜像里是后者） */
    private fun bool(el: JsonElement?, default: Boolean): Boolean = boolOrNull(el) ?: default

    /**
     * `null` / 字符串 "None" 都是**"没设过"**（真卡的 `scan_depth: null`、`match_whole_words: null`
     * 就是这个形状），必须返回 null 而不是 false —— 否则"全词匹配"会被我们标成"用户明确关了"，
     * 导出时又把 null 写回成 false（卡被我们改了一遍）。
     */
    private fun boolOrNull(el: JsonElement?): Boolean? {
        val p = el as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return when (p.contentOrNull?.trim()?.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    /** 同一个字段的多个键名挨个试（v2 规范 / ST 镜像 / 老版驼峰，见 [entry]） */
    private fun pickInt(o: JsonObject, vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { intOrNull(o[it]) }

    private fun pickBool(o: JsonObject, vararg keys: String): Boolean? =
        keys.firstNotNullOfOrNull { boolOrNull(o[it]) }

    private fun pickList(o: JsonObject, vararg keys: String): List<String> =
        keys.firstNotNullOfOrNull { strList(o[it]).takeIf { l -> l.isNotEmpty() } }.orEmpty()

    private fun strList(el: JsonElement?): List<String> = when (el) {
        is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        // 单个字符串也认：手写与老格式里出现过 `"key": "王城"`，数组是常态但不是唯一形状
        is JsonPrimitive -> listOfNotNull(el.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
        else -> emptyList()
    }.filter { it.isNotEmpty() }

    private fun positionSpec(el: JsonElement?): Int? {
        val p = el as? JsonPrimitive ?: return null
        val s = p.contentOrNull?.trim()?.lowercase() ?: return null
        return when (s) {
            "before_char" -> WorldBookEntry.POSITION_BEFORE_CHAR
            "after_char" -> WorldBookEntry.POSITION_AFTER_CHAR
            "before_an" -> WorldBookEntry.POSITION_BEFORE_AN
            "after_an" -> WorldBookEntry.POSITION_AFTER_AN
            "at_depth" -> WorldBookEntry.POSITION_AT_DEPTH
            else -> s.toIntOrNull()
        }
    }

    /** 给界面/自检用的一句话摘要：这本书有多少条、几条能用 */
    fun describe(book: WorldBook?): String {
        if (book == null) return "无世界书"
        val entries = book.entries
        val dead = entries.count { !it.canTrigger() }
        return buildString {
            append("世界书「").append(book.name.ifBlank { "未命名" }).append("」")
            append(entries.size).append(" 条")
            if (dead > 0) append("（其中 ").append(dead).append(" 条没有关键词、永远不会触发）")
        }
    }
}
