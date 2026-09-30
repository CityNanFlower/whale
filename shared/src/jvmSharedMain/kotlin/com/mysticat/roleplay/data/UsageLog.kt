package com.mysticat.roleplay.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.Calendar

/**
 * 用量台账（用量查询 112 的数据源）：每次**真实 API 请求**一行 JSONL，落
 * `accounts/<id>/usage/log.jsonl`（落点见 [Repository.usageLogFile]）。
 *
 * 记账口径：**一次 HTTP 请求一行**——空回复的抬预算重发、内容档位回落都各自成行，
 * 因为每一次都真实烧了钱。端点没回报 usage 时 token 字段缺省（请求次数照记）。
 *
 * **覆盖范围**（台账 114 起）：不只主对话，每条花钱的链路都记，靠 [Event.kind] 区分——
 * 主对话（对话模型）／对话辅助（灵感、前情提要、角色记忆、参考图转译，都用对话模型）／
 * 创作（角色卡、工具卡、世界书、起草扩写，走创作模型）／生图（走生图模型）／
 * 语音朗读与语音输入（116-A 补上；走 [KIND_SPEECH]／[KIND_ASR]）。
 * **生图端点不回 token**，只记张数（[Event.images]）；**语音两端点也不回**（直接给音频字节/文本），
 * 所以那两条链路只计次数——分得清"协议本来就不给"与"采集漏了"，见 [kindHasTokens]。
 * 语音链路虽然拿不到 token，但**量得出字数**（朗读＝送进去的文本、识别＝转出来的文本，见 [Event.chars]）——
 * 用户口径（2026-09-30）："不计 token 就记字符数"，不另开表，就补这一组数。
 *
 * 这是用户花了多少钱的凭据＝**数据**，随账号备份走，绝不进 CLEARABLE_CACHE_DIRS。
 * 聚合与筛选由用量查询屏在内存里做（见 [bucketsOf] / [summarize]），这里只管追加与按时间区间读。
 */
object UsageLog {
    private const val TAG = "UsageLog"

    /** 主对话（对话模型，流式） */
    const val KIND_CHAT = "chat"

    /** 对话辅助：灵感回复 / 前情提要 / 角色记忆 / 参考图转译——都用**对话模型** */
    const val KIND_ASSIST = "assist"

    /** 创作：角色卡、工具卡、世界书、起草扩写——走**创作模型** */
    const val KIND_CREATION = "creation"

    /** 生图——走**生图模型**；端点不回 token，只记张数 */
    const val KIND_IMAGE = "image"

    /** 连通性自检（用户主动在设置里点的那一下） */
    const val KIND_PROBE = "probe"

    /** 语音朗读（TTS）：端点直接回音频字节、**不回 token**，所以这条链路只计次数与字符数 */
    const val KIND_SPEECH = "speech"

    /** 语音输入（ASR/STT）：上传音频换文本，同样拿不到 token，只计次数与字符数 */
    const val KIND_ASR = "asr"

    /** 链路的中文口径（查询屏按 kind 分组/筛选；旧行没有 kind 字段，读出来按主对话算） */
    fun kindLabel(kind: String): String = when (kind) {
        KIND_CHAT -> "主对话"
        KIND_ASSIST -> "对话辅助"
        KIND_CREATION -> "创作"
        KIND_IMAGE -> "生图"
        KIND_PROBE -> "连通性自检"
        KIND_SPEECH -> "语音朗读"
        KIND_ASR -> "语音输入"
        else -> kind
    }

    /**
     * 链路**记不记得到 token**（页面要按这个写口径，别笼统说"端点没回报"）：
     * 图像与语音两类端点按协议就不返回 usage，拿不到就是拿不到，不是采集漏了。
     */
    fun kindHasTokens(kind: String): Boolean = when (kind) {
        KIND_IMAGE, KIND_SPEECH, KIND_ASR -> false
        else -> true
    }

    /** 一条用量记录：一次真实 API 请求 */
    class Event(
        val at: Long,
        /** 链路：见 [KIND_CHAT] 等常量 */
        val kind: String,
        /** 服务的 Base URL（查询屏按 host 展示/筛选） */
        val provider: String,
        val model: String,
        /** 会话 id；后台链与生图没有会话，为空串 */
        val sessionId: String,
        val characterName: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        /** 生图张数（图像端点不回 token，只有这个数得出来） */
        val images: Int? = null,
        /** 结束原因原始值（stop / length / …）；后台链多数不回，为 null */
        val finishReason: String? = null,
        /**
         * **字符数**：语音两端点拿不到 token 时的替代量——朗读＝送进 TTS 的文本长度，
         * 识别＝转出来的文本长度（台账 116-M，用户 2026-09-30：「不计 token 就记字符数」）。
         * 对话类行留 null：那边有 token，字符数没有意义（`RequestViewer` 自己另存）。
         */
        val chars: Int? = null,
    ) {
        /** 该行 token 合计；两端点都没回报时为 null（不是 0——0 会被读成"没花钱"） */
        val totalTokens: Int?
            get() = if (promptTokens == null && completionTokens == null) null else (promptTokens ?: 0) + (completionTokens ?: 0)
    }

    /** 追加一行；失败只记日志不炸对话链路（台账少一行好过回复发不出去） */
    fun record(e: Event) {
        runCatching {
            val line = buildJsonObject {
                put("at", e.at)
                put("kind", e.kind)
                put("provider", e.provider)
                put("model", e.model)
                put("sessionId", e.sessionId)
                put("character", e.characterName)
                e.promptTokens?.let { put("prompt", it) }
                e.completionTokens?.let { put("completion", it) }
                e.images?.let { put("images", it) }
                e.finishReason?.let { put("finish", it) }
                e.chars?.let { put("chars", it) }
            }.toString()
            Repository.usageLogFile().appendText(line + "\n")
        }.onFailure { WhaleLog.w(TAG, "用量落盘失败：${it.message}") }
    }

    /**
     * 非对话链路的简写记法（对话辅助 / 创作 / 生图 / 自检都是"发一次、拿结果"，没有会话级快照，
     * 所以给一个不传 [Event] 的入口，避免每个调用点都写一遍命名参数）。
     */
    fun recordCall(
        kind: String,
        provider: String,
        model: String,
        sessionId: String? = null,
        characterName: String = "",
        promptTokens: Int? = null,
        completionTokens: Int? = null,
        images: Int? = null,
        finishReason: String? = null,
        chars: Int? = null,
    ) = record(
        Event(
            at = System.currentTimeMillis(),
            kind = kind,
            provider = provider,
            model = model,
            sessionId = sessionId.orEmpty(),
            characterName = characterName,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            images = images,
            finishReason = finishReason,
            chars = chars,
        )
    )

    /** 读区间 [fromMs, toMs] 的记录（文件序，即时间序）；单行损坏跳过不炸 */
    fun events(fromMs: Long = 0, toMs: Long = Long.MAX_VALUE): List<Event> =
        runCatching {
            val f: File = Repository.usageLogFile()
            if (!f.exists()) emptyList()
            else f.readLines().mapNotNull { line ->
                runCatching {
                    val o = Json.parseToJsonElement(line).jsonObject
                    val at = o["at"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@mapNotNull null
                    if (at < fromMs || at > toMs) return@mapNotNull null
                    Event(
                        at = at,
                        // 旧行（批次 C 只有主对话时写的）没有 kind 字段，按主对话算
                        kind = o["kind"]?.jsonPrimitive?.content ?: KIND_CHAT,
                        provider = o["provider"]?.jsonPrimitive?.content ?: "",
                        model = o["model"]?.jsonPrimitive?.content ?: "",
                        sessionId = o["sessionId"]?.jsonPrimitive?.content ?: "",
                        characterName = o["character"]?.jsonPrimitive?.content ?: "",
                        promptTokens = o["prompt"]?.jsonPrimitive?.content?.toIntOrNull(),
                        completionTokens = o["completion"]?.jsonPrimitive?.content?.toIntOrNull(),
                        images = o["images"]?.jsonPrimitive?.content?.toIntOrNull(),
                        finishReason = o["finish"]?.jsonPrimitive?.content,
                        chars = o["chars"]?.jsonPrimitive?.content?.toIntOrNull(),
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

    // ── 聚合（纯函数，查询屏只负责画；桌面自检直接断言这些）────────────────────

    /** 查询区间。**WEEK/MONTH 含今天**（用户口径"近七天"就是今天＋往前六天） */
    enum class Range(val label: String) {
        TODAY("今天"),
        YESTERDAY("昨天"),
        WEEK("近七天"),
        MONTH("近三十天"),

        /** 自定义起止（台账 116-C）：两端都是**整天**、含首含尾，上限见 [MAX_CUSTOM_DAYS] */
        CUSTOM("自定义")
    }

    /** 自定义区间的上限：一年（用 366 天兜住闰年，用户口径"≤ 一年"） */
    const val MAX_CUSTOM_DAYS = 366

    /** 图表的一根柱子：标签 ＋ 左闭右开区间 [fromMs, toMs) */
    class Bucket(val label: String, val fromMs: Long, val toMs: Long)

    /** 横轴上的一格刻度：[pos] 是**桶边界**（第 pos 根柱子的左沿；pos == 桶数 ＝ 这段的结束），[label] 是那个时刻 */
    class AxisTick(val pos: Int, val label: String)

    /**
     * 横轴刻度（纯函数，桌面自检直接断言）：位置＝**桶边界** `[0, 步长, …, n]`。
     *
     * 刻度落在边界而不是柱子正中——柱子画的是"该时刻之后那一段"，刻度是"某个时刻这个点"：
     * 压在正中时左端会缩进半格（`00:00` 不贴图的左沿），右端又缺"这一天结束"那一格，
     * 一天里最后一格（23:00→24:00）看起来落在轴外（用户 2026-09-30 反馈"横轴的时间点还有问题"）。
     * 现在右端恒有一个刻度：按小时是 `24:00`，按天是次日（末桶的终点即次日零点）。
     */
    fun axisTicks(buckets: List<Bucket>): List<AxisTick> {
        val n = buckets.size
        if (n == 0) return emptyList()
        val span = buckets[0].toMs - buckets[0].fromMs
        val pos = (0 until n step tickStep(n)).toMutableList()
        if (pos.last() != n) pos += n
        return pos.map { i ->
            val at = if (i == n) buckets.last().toMs else buckets[0].fromMs + span * i
            // 按小时的轴用 `xx:xx`（末格＝24:00）；按天的轴用"月-日"（末格＝次日）
            AxisTick(i, if (span <= 2 * HOUR_MS) "%02d:00".format((at - buckets[0].fromMs) / HOUR_MS) else monthDayLabel(at))
        }
    }

    /**
     * 刻度步长：取 n 不超过 n/4 的**最大约数**（24→6 小时一格、30→6 天一格、12→3、7→1）。
     * 必须是约数——否则末格缩水，刻度疏密不匀（`0/6/12/18/23` 那种最后一格只跨 5 格）。
     */
    private fun tickStep(n: Int): Int {
        val want = maxOf(1, n / 4)
        var best = 1
        for (d in 2..want) if (n % d == 0) best = d
        return best
    }

    /** 一段筛选后的小计。token 都从 0 累加——"没回报 usage"那一行不加任何 token，但计一次请求 */
    class Summary(
        val requests: Int,
        val promptTokens: Int,
        val completionTokens: Int,
        /** 生图张数（生图行没有 token，只有这个） */
        val images: Int,
        /** 因输出预算被截断的次数（finish_reason=length） */
        val truncated: Int,
        /** 端点没回报 usage 的请求次数（token 栏会小于请求数，页面上要讲清） */
        val unreported: Int,
    ) {
        val totalTokens: Int get() = promptTokens + completionTokens
    }

    /**
     * 区间的柱子：今天/昨天按**小时**分（各 24 根），近七天/近三十天按**天**分。
     * 时间边界都走本机时区（用户看的是自己的今天，不是 UTC 的）。
     *
     * ⚠ **今天也给足 24 格、不截到当前小时**（用户 09-30 定）：横轴是"一天里的钟点"这个固定刻度，
     * 上午看也应该是一条完整的 00:00→23:00——随当前时刻伸缩的轴每刷新一次形状都不一样，
     * 两张截图没法比，也看不出"我今天还有多少小时没用"。
     */
    fun bucketsOf(
        range: Range,
        now: Long = System.currentTimeMillis(),
        /** 只在 [Range.CUSTOM] 用；默认＝近七天（与 WEEK 同口径，免得切过去是一张空图） */
        customFrom: Long = dayStart(now) - 6 * DAY_MS,
        customTo: Long = now,
    ): List<Bucket> = when (range) {
        Range.TODAY -> hourlyBuckets(dayStart(now), 24)
        Range.YESTERDAY -> hourlyBuckets(dayStart(now) - DAY_MS, 24)
        Range.WEEK -> dailyBuckets(dayStart(now) - 6 * DAY_MS, 7)
        Range.MONTH -> dailyBuckets(dayStart(now) - 29 * DAY_MS, 30)
        Range.CUSTOM -> customDailyBuckets(customFrom, customTo)
    }

    /**
     * 自定义区间按**天**分桶（含首含尾）。上限 [MAX_CUSTOM_DAYS]——超出就截到上限为止，
     * 免得用户手输两个相差十年的日期时画出一张四千格的图。
     */
    private fun customDailyBuckets(fromMs: Long, toMs: Long): List<Bucket> {
        val from = dayStart(minOf(fromMs, toMs))
        val to = dayStart(maxOf(fromMs, toMs))
        // 含首含尾：同一天选起止也至少 1 格，别画出一张空图
        val days = (((to - from) / DAY_MS) + 1).coerceIn(1L, MAX_CUSTOM_DAYS.toLong()).toInt()
        return dailyBuckets(from, days)
    }

    private fun hourlyBuckets(fromDayStart: Long, hours: Int): List<Bucket> =
        (0 until hours).map { h ->
            val from = fromDayStart + h * HOUR_MS
            // 标签按台账 116-I 用 `xx:xx`（`"$h 时"`看不出是几点，只有个数）
            Bucket("%02d:00".format(h), from, from + HOUR_MS)
        }

    private fun dailyBuckets(fromDayStart: Long, days: Int): List<Bucket> =
        (0 until days).map { d ->
            val from = fromDayStart + d * DAY_MS
            Bucket(monthDayLabel(from), from, from + DAY_MS)
        }

    /** 小计；[events] 应当已经过时间与筛选条件过滤 */
    fun summarize(events: List<Event>): Summary = Summary(
        requests = events.size,
        promptTokens = events.sumOf { it.promptTokens ?: 0 },
        completionTokens = events.sumOf { it.completionTokens ?: 0 },
        images = events.sumOf { it.images ?: 0 },
        truncated = events.count { it.finishReason == "length" },
        unreported = events.count { it.totalTokens == null && (it.images ?: 0) == 0 },
    )

    // ── 热力图（长区间：近七天/近三十天/自定义）与分色图（116-C／116-F）────────

    /** 热力图上的一格：[week]＝第几列（周序，从 0 起）、[weekday]＝第几行（周一＝0 … 周日＝6），[index]＝桶下标；< 0 ＝对齐用的空位 */
    class HeatCell(val week: Int, val weekday: Int, val index: Int)

    /** 热力图顶部的月份标签：第 [week] 列上方写 [label]（`9月`） */
    class MonthLabel(val week: Int, val label: String)

    /**
     * 热力图网格（纯函数，桌面自检直接断言）：**按真实日历排**——列＝周（左到右按时间）、
     * 行＝星期一到星期日。
     *
     * 方向取自 GitHub 的贡献图：**一年就是 53 列 × 7 行**，横向铺开、横着滚；
     * 反过来（行＝周）在自定义区间选满一年时会变成 53 行高的长条，一屏根本放不下（用户 09-30 反馈）。
     * 顺次铺成"一行 7 格"也不行——那样读者看不出某格是周几，日历的意义就没了。
     * 起始日不是周一、或者末日不是周日时，用 [HeatCell.index] = -1 补齐成**完整矩形**
     * （缺角看起来像"那天没数据"）。**真实天数＝index ≥ 0 的格数**，不写死 30（台账 116-C 口径）。
     */
    fun heatmapGrid(buckets: List<Bucket>): List<HeatCell> {
        if (buckets.isEmpty()) return emptyList()
        val lead = weekdayRow(buckets[0].fromMs)
        val total = lead + buckets.size
        return buildList {
            // 首日之前补空位（对齐到周一）
            for (i in 0 until lead) add(HeatCell(0, i, -1))
            buckets.forEachIndexed { i, _ ->
                val pos = lead + i
                add(HeatCell(pos / 7, pos % 7, i))
            }
            // 末周补齐到周日：整块是一个矩形，右侧边缘才是直的
            for (pos in total until heatmapWeeks(buckets) * 7) add(HeatCell(pos / 7, pos % 7, -1))
        }
    }

    /** 网格的列数（＝周数）；GitHub 那种横向排法只关心宽度，高度恒为 7 行 */
    fun heatmapWeeks(buckets: List<Bucket>): Int {
        if (buckets.isEmpty()) return 0
        return (weekdayRow(buckets[0].fromMs) + buckets.size + 6) / 7
    }

    /**
     * 月份标签（纯函数，桌面自检直接断言）：**标在"该月 1 号所在的那一列"**——
     * 逐列都写月份会把图上塞满字，只有列头才有位置。区间起点不在 1 号时首列补上起始月
     * （否则开头那几天没有归属，看起来像漏了一列）。
     *
     * 同一列挤了两个月（区间从 28 号这种月末开始）时**把后一个右移一列**：
     * 标签偏一列只是不够精确，整个月没名字则是信息丢了。
     */
    fun heatmapMonthLabels(buckets: List<Bucket>): List<MonthLabel> {
        if (buckets.isEmpty()) return emptyList()
        val lead = weekdayRow(buckets[0].fromMs)
        // 候选：区间起始月 ＋ 落在区间内的每个 1 号所属月（起点就在 1 号时两者重合，key 相同）
        val cands = LinkedHashMap<String, Pair<Int, String>>()
        buckets.forEachIndexed { i, b ->
            val c = Calendar.getInstance().apply { timeInMillis = b.fromMs }
            val month = c.get(Calendar.MONTH) + 1
            if (i == 0 || c.get(Calendar.DAY_OF_MONTH) == 1) {
                cands.putIfAbsent("${c.get(Calendar.YEAR)}-$month", ((lead + i) / 7) to "${month}月")
            }
        }
        val taken = mutableSetOf<Int>()
        return cands.values.map { (week0, label) ->
            var w = week0
            while (w in taken) w++
            taken += w
            MonthLabel(w, label)
        }
    }

    /** 星期几在热力图里的行序（周一＝0 … 周日＝6） */
    private fun weekdayRow(ms: Long): Int {
        // Calendar：SUNDAY=1 … SATURDAY=7 ⇒ 周一(2) → 0
        val dow = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)
        return (dow + 5) % 7
    }

    /** 星期几的名字（行序同 [weekdayRow]，热力图行首与按天的弹框都用它） */
    val WEEKDAY_NAMES = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 分色图的调色板大小（UI 那份色表必须同长；自检断言这条对应关系） */
    const val MODEL_PALETTE_SIZE = 10

    /**
     * 模型名 → 调色板**起点**号（纯函数、稳定）：同一个名字永远给同一个起点。
     *
     * 它只是起点，**不是最终色号**——十个色相看着够用，实际几个常用模型就能撞上
     * （实测 `deepseek-flash` 与 `kimi-k3` 都落 1 号，图上两段一模一样，看起来像"没分色"）。
     * 真正分配见 [modelColorSlots]。
     *
     * 不用 `String.hashCode()` 的绝对值——它在 JVM 上有确定契约、别的平台实现方式不同；
     * 这里按 31 进制手算一遍，几行代码换"跨端同色"。
     */
    fun modelColorIndex(model: String): Int {
        if (model.isEmpty()) return 0
        var h = 0
        for (ch in model) h = h * 31 + ch.code
        return ((h % MODEL_PALETTE_SIZE) + MODEL_PALETTE_SIZE) % MODEL_PALETTE_SIZE
    }

    /**
     * 一组模型 → 色号（**同一批里绝不撞色**，且跨区间稳定）：按模型名升序依次落位，
     * 起点已被前面的模型占了就往后挪一位（线性探测）。
     *
     * 为什么不做"名字直接取模当色号"：撞了就丢了这个功能的意义（见 [modelColorIndex]）。
     * 为什么排序：顺序确定 ⇒ 同一份数据两次渲染同色。
     * 稳定性：**只增模型（名字排在后面的）不改前面模型的色号**——所以换区间、加筛选看来看去，
     * 老模型的颜色是一致的；只有把中间某个模型筛掉时，排在它后面的会前移一位。
     * 模型数超过调色板长度时必然有重复，此时退化为撞色（十个色相是上限，先到先得）。
     */
    fun modelColorSlots(models: List<String>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        val used = mutableSetOf<Int>()
        models.distinct().sorted().forEach { m ->
            var slot = modelColorIndex(m)
            var guard = 0
            while (slot in used && guard < MODEL_PALETTE_SIZE) {
                slot = (slot + 1) % MODEL_PALETTE_SIZE
                guard++
            }
            used += slot
            out[m] = slot
        }
        return out
    }

    /** 分色图里"未记模型"那一档的显示名（与 UI 明细行同口径） */
    const val UNKNOWN_MODEL = "（未记模型）"

    /**
     * 分色图的模型顺序：**按名字升序**，不是按量降序——顺序一变，同一张图里堆叠段与图例的
     * 上下关系就跟着变，跨区间对不上。只收 token 拿得到的行（生图/语音那些没有 token）。
     */
    fun modelOrder(events: List<Event>): List<String> =
        events.filter { it.totalTokens != null }
            .map { it.model.ifBlank { UNKNOWN_MODEL } }
            .distinct()
            .sorted()

    /** 一组事件的 token 按模型拆开（堆叠段与图例的数值；模型名口径同 [modelOrder]） */
    fun tokensByModel(events: List<Event>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        events.forEach { e ->
            val t = e.totalTokens ?: return@forEach
            val m = e.model.ifBlank { UNKNOWN_MODEL }
            out[m] = (out[m] ?: 0) + t
        }
        return out
    }

    /** Base URL → 供应商 host（台账存的是整条 URL，展示与筛选都只该看 host） */
    fun providerHost(baseUrl: String): String {
        val s = baseUrl.trim()
        if (s.isEmpty()) return ""
        return s.substringAfter("://", s).takeWhile { it != '/' && it != '?' && it != '#' }.ifBlank { s }
    }

    /**
     * 供应商的**显示名**（台账 116-K）：认得的家给中文/官方名（DeepSeek、火山方舟…），
     * 认不出的（用户自填的地址、本地部署）退回 host——**宁可显示 `192.168.1.7:11434`，
     * 也不要显示一个猜出来的错名字**。筛选的键仍然是 host（见 [providerHost]），显示名只管好看。
     */
    fun providerLabel(baseUrl: String): String =
        ProviderProfiles.forUrl(baseUrl)?.name?.takeIf { it.isNotBlank() } ?: providerHost(baseUrl)

    /** 会话筛选的键：后台链与生图没有会话，一律为空串（归到「后台任务」那一档） */
    fun sessionKey(e: Event): String = e.sessionId

    /** 会话筛选的显示名：会话 id 对用户没有意义，用「角色名 · id 前四位」区分同角色的多个会话 */
    fun sessionLabel(e: Event): String = when {
        e.sessionId.isBlank() -> e.characterName.ifBlank { "后台任务" }
        e.characterName.isBlank() -> e.sessionId.take(4)
        else -> "${e.characterName} · ${e.sessionId.take(4)}"
    }

    // ── 时间工具（本机时区）──────────────────────────────────────────────

    /** 本机时区的当天零点 */
    fun dayStart(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * 相对今天往前 [n] 天的**零点**（n = 0 就是今天零点）。UI 的自定义区间默认值与"整段"的
     * 边界都走这里——直接减毫秒会把夏令时那天算偏，取零点则永远落在日界上。
     */
    fun dayStartDaysAgo(n: Int, now: Long = System.currentTimeMillis()): Long = dayStart(now) - n * DAY_MS

    /**
     * 日历组件（DatePicker）回的是 **UTC 午夜**，不是本机时区的零点——直接拿去当区间端点，
     * 在西半球会整整偏一天（本地 00:00 还是 UTC 的昨天）。这里按"那个日期"换算成本机时区的当天零点。
     */
    fun dayStartFromUtcMidnight(utcMs: Long): Long {
        val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMs }
        return Calendar.getInstance().apply {
            clear()
            set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }

    /** 自定义区间两端允许的最大间隔（天数，含首含尾）＝[MAX_CUSTOM_DAYS] */
    fun customSpanLimitMs(): Long = (MAX_CUSTOM_DAYS - 1).toLong() * DAY_MS

    /** "月-日"标签（两位补零，图表横轴用） */
    fun monthDayLabel(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d-%02d".format(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** 一天的完整标签（热力图选中某天时弹框的抬头）：`9月15日 周一`——按天看图，读者要知道是周几 */
    fun dayLabel(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%d月%d日 %s".format(
            c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), WEEKDAY_NAMES[weekdayRow(ms)]
        )
    }

    /** 一行的"时:分"标签（列表用） */
    fun timeLabel(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private const val HOUR_MS = 3_600_000L

    /** 一天。**只用于与本机零点对齐做加减**——夏令时地区会出现 23/25 小时的那一天，取整在这里的误差可忽略 */
    const val DAY_MS = 86_400_000L
}
