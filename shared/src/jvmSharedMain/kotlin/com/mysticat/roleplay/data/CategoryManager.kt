package com.mysticat.roleplay.data

/**
 * 角色卡分类：全部（含预设）可增删/排序；「其他」固定最后不可删除。
 */
object CategoryManager {

    private val defaultPresets = listOf("古风", "现代", "奇幻", "科幻", "二次元", "恋爱", "悬疑")
    const val OTHER = "其他"

    /** 导入卡片时自动登记的类型上限（见 [ensure]；与 [categoryLabel] 的显示口径一致） */
    private const val MAX_AUTO_PER_CARD = 3

    /**
     * 非「其他」分类的有序列表（未自定义时用默认预设）。
     *
     * **过滤掉形态名**（用户 2026-09-23 反馈：「工具」既然已经是形态，就别再作为类型出现）：
     * 卡站的 tags 里常带 Tool / 工具 这类词，[ensure] 会把它自动登记进来，于是「工具」既是一个
     * 形态（筛选用 [FormFilter]）、又能当类型/标签选 —— 同一件事两个入口，用户会以为选类型=选形态。
     * 过滤放在这里（读写两端都必经）：**库里已有的数据不必手改就自愈**，用户下次编辑分类时
     * [persist] 落盘的就是过滤后的列表。
     */
    fun base(): List<String> {
        val s = Repository.loadSettings()
        val raw = if (s.categoriesInitialized) s.categories else defaultPresets
        return raw.filterNot { it.trim() in Engines.formLabels }
    }

    /** 完整分类：base + 其他(最后) */
    fun all(): List<String> = (base() + OTHER).distinct()

    /** 筛选 chips：全部 + all() */
    fun filterOptions(): List<String> = listOf("全部") + all()

    private fun persist(list: List<String>, backfilled: Boolean? = null) {
        val s = Repository.loadSettings()
        Repository.saveSettings(
            s.copy(
                categories = list.distinct(),
                categoriesInitialized = true,
                categoriesBackfilled = backfilled ?: s.categoriesBackfilled
            )
        )
    }

    fun add(name: String) {
        val t = name.trim()
        if (t.isNotBlank() && t != OTHER) persist(base() + t)
    }

    /**
     * **角色卡带来的新类型自动进列表**（2026-09-21 用户反馈：导入发现页的卡，类型列表里没有它的类型，
     * 于是按类型筛永远看不到这张卡——卡片被迫归到只有「全部」能看见的夹缝里）。
     *
     * 调用点在 [Repository.saveCharacter]：导入的**所有**入口（发现页精选 / 文件 / 网址 / 桌面拖入 /
     * 备份恢复）都从那里落库，放这一处比在五个入口各写一遍不容易漏。
     *
     * 一张卡最多自动补 [MAX_AUTO_PER_CARD] 个：卡站的 `tags` 动辄五六个（还常夹着 `sfw`/`OC` 这类），
     * 全塞进类型列表会把 chips 冲垮；取前几个与 [categoryLabel] 的"最多显示 3 个"是同一口径。
     * 用户仍可在「管理分类」里删掉不想要的。
     */
    fun ensure(names: List<String>) {
        val known = base()
        val missing = missing(names, known, MAX_AUTO_PER_CARD)
        if (missing.isNotEmpty()) persist(known + missing)
    }

    /**
     * 补齐**库里已有**的卡带的类型。自动登记是 2026-09-21 才有的，那之前导入的卡一颗类型都没登记过，
     * 只修导入路径的话用户得"重新导一次"才看得到（反馈里那几张卡正是这种情况）——所以进角色卡页时
     * 拿已经读进内存的列表顺手补一遍，**一次写盘**，没有新类型就完全不落盘。
     *
     * **只补一次**（[AiSettings.categoriesBackfilled]）：补过之后再进页面不再扫卡，否则用户删掉某个分类、
     * 只要还有卡带着它就会被重新加回来——"删了又回来"比没有这个功能更糟。
     */
    fun ensureFromCards(cards: List<CharacterCard>) {
        if (Repository.loadSettings().categoriesBackfilled) return
        val known = base().toMutableList()
        cards.forEach { card -> known += missing(card.categoriesOrDefault(), known, MAX_AUTO_PER_CARD) }
        persist(known, backfilled = true)
    }

    /** 本地还没有的类型（去重、跳过空串、「其他」与**形态名**、最多取前 [max] 个） */
    private fun missing(names: List<String>, known: List<String>, max: Int): List<String> =
        names.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != OTHER && it !in Engines.formLabels && it !in known }
            .distinct()
            .take(max)
            .toList()

    fun remove(name: String) {
        if (name == OTHER) return
        persist(base().filter { it != name })
    }

    fun move(from: Int, to: Int) {
        val list = base().toMutableList()
        if (from in list.indices && to in list.indices) {
            val item = list.removeAt(from)
            list.add(to, item)
            persist(list)
        }
    }
}
