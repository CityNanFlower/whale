package com.mysticat.roleplay.data

/**
 * 内容档位：模型**因内容拒答**时逐级回落的一道兜底（调研「内容退化机制」）。
 *
 * 口径是**回落**而不是绕过：请求原样发给供应商、每次拒绝都被尊重；我们只把同一条请求
 * 换成更含蓄的措辞再试一次，不替换词表、不改写正文、不切模型。
 *
 * [EXPLICIT] 是现状（**不往提示词里加任何东西**，逐字节与今天一致）——只有回落之后的那两档
 * 才在系统提示词末尾补一段，所以"没触发回落"的请求与加这个机制之前完全一样。
 *
 * 档位名只是内部标识（[label] 给日志与自检看）：它对用户不可见，
 * 用户看到的只有"已按更含蓄的方式重试"这一行提示（见 `ChatViewModel` 的回落提示）。
 */
enum class ContentTier(val label: String) {
    /** 直白（默认档）：不额外约束，等同于加本机制之前的提示词 */
    EXPLICIT("直白"),
    /** 暗示：写到关键处点到为止 */
    SUGGESTIVE("暗示"),
    /** 淡出：只写前后与结果，不写过程 */
    FADE("淡出");

    /** 再退一级；已经是最含蓄的一档时返回 null（＝没有可退的了） */
    val next: ContentTier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        val DEFAULT: ContentTier = EXPLICIT

        /** 一轮最多回落几次（＝档位数 - 1）；每回落一级都是一次完整计费请求，必须封顶 */
        val MAX_STEPS: Int = entries.size - 1
    }
}

/**
 * 这次失败算不算"模型因内容拒答"—— 要不要回落一级**只看这里**。
 *
 * 为什么必须分类：空回复的成因至少六种（内容策略拦截、上下文超长、输出预算被思考吃光、
 * 只思考没说话、供应商 5xx / 限流、用户主动取消）。把网络抖动当成"被拒"去回落，
 * 是白烧钱又白降体验。所以只认**明确指向内容层**的两种信号：
 *
 * 1. 空回复，且不是"只思考没说话"、也不是输出被截断 —— 干净的流一条增量都没有，
 *    既可能是静默拒答，也可能是服务商闷声不给内容（国内多家把拒答写成空 body 而不是
 *    `content_filter`）。两者对用户是同一件事：这条没答出来，所以允许换更含蓄的说法再试。
 * 2. HTTP 4xx 的**错误体**命中审核措辞（把拒答写成错误体的那些家）。
 *
 * 明确**不算**的：`length`（输出预算被截断）、只思考没说话（`rawSeen` / 收到过
 * `reasoning_content`）、5xx 与 429（那是"再试一次"的另一条路，且与内容无关）、用户取消。
 */
object ContentRefusal {

    private val MARKERS = listOf(
        "content_filter", "content filter", "content policy", "content_policy",
        "moderation", "safety", "blocked", "prohibited", "inappropriate",
        "违规", "审核", "敏感", "内容政策", "拒绝回答"
    )

    private fun hit(text: String?): Boolean =
        text != null && MARKERS.any { text.contains(it, ignoreCase = true) }

    /**
     * 空回复要不要回落。
     *
     * @param finishReason 流最后一个 chunk 给的结束原因（没有则为 null）
     * @param onlyThinking 只思考没说话（正文增量一个没有、但收到过 thinking / `reasoning_content`）
     */
    fun fromBlankReply(finishReason: String?, onlyThinking: Boolean): Boolean = when {
        onlyThinking -> false
        finishReason == "length" -> false
        else -> true
    }

    /** HTTP 错误体是不是拒答（只在 4xx 上认——5xx 是服务端故障，与内容无关） */
    fun fromErrorBody(code: Int, body: String?): Boolean = code in 400..499 && hit(body)
}

/** 一次按档位重放的产出：值 ＋ **实际退了几档**（0 ＝ 第一次就成了） */
class TierReplay<T>(val value: T, val downgrades: Int)

/**
 * 按档位重放一次请求，直到成功或退到最含蓄那档 —— **后台链路**（灵感 / 前情提要 / 记忆整理）的入口。
 *
 * 为什么后台链也要它：这三条链各自独立发请求，原来撞上内容拒答就是**这一次直接没了**——
 * 用户看到的是"它没更新"，而模型下一轮还会用同样的写法再拒一次。判据与封顶因此与主对话
 * （`ChatViewModel.requestReply` 的回落循环）完全一致，差别只有一处：**这里不产界面提示**，
 * 用户可见出口由各调用方自己决定（灵感那条是面板里的错误行，摘要是记忆是后台静默维护）。
 *
 * 三条边界（与主对话同源，缺一条就变成本钱陷阱）：
 * ① 只认 [ContentRefusal] 认定的拒答 —— 网络抖动 / 限流 / 只思考没说话 / 被预算截断一律原样抛出；
 * ② 每退一级＝一次完整计费请求，所以退到最含蓄那档就停（＝最多 [ContentTier.MAX_STEPS] 次额外请求）；
 * ③ [enabled] 跟着设置页「内容拒答自动回落」走：关掉＝一次都不重放，原样抛出。
 *
 * ⚠️ 判据**绝不在这里另写一份**：两处判据必然漂移——一处把限流当拒答（白花钱又白降体验），
 * 另一处把拒答放过（又变回"静默丢一次"）。
 */
suspend fun <T> replayByTier(enabled: Boolean, run: suspend (ContentTier) -> T): TierReplay<T> {
    var tier = ContentTier.DEFAULT
    var downgrades = 0
    while (true) {
        try {
            return TierReplay(run(tier), downgrades)
        } catch (e: AiException) {
            val lower = tier.next
            if (!enabled || !e.refused || lower == null) throw e
            tier = lower
            downgrades++
        }
    }
}
