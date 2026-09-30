package com.mysticat.roleplay.data

/**
 * 一次真实请求的装配快照 —— 上下文查看器（「上次请求」）的数据侧。
 *
 * 在 `chatStream` 组装消息数组的地方采集：系统提示词的段表结果、最终消息数组
 * （已并入 tailHint 与 `@depth` 世界书条目）、命中的世界书条目与请求参数。
 * 快照与实发**同源**（同一次装配的产物，不是各算各的），桌面自检里有逐字段比对闸门。
 */
class RequestSnapshot(
    val at: Long,
    val characterName: String,
    val mode: PromptMode,
    /** 内容档位（回落之后才不是默认档） */
    val tierLabel: String,
    val model: String,
    val temperature: Double?,
    val maxTokens: Int,
    /** 系统提示词按段表拆好的 (段名, 正文)，顺序即段序 */
    val segments: List<Pair<String, String>>,
    /** 最终消息数组 (role, content)，即发出去的 messages */
    val messages: List<Pair<String, String>>,
    /** 本轮注入的世界书条目（条目名 ＋ 命中方式），无命中为空 */
    val bookHits: List<String>,
) {
    val systemChars: Int get() = segments.sumOf { it.second.length }
    val totalChars: Int get() = systemChars + messages.sumOf { it.second.length }

    /** 粗估 token（CJK ≈ 0.6 token/字，其余 ≈ 0.25/字符）：只给量级感，不是计费口径 */
    val approxTokens: Int
        get() {
            fun est(s: String): Int {
                var cjk = 0
                for (ch in s) if (ch.code > 0x2E7F) cjk++
                return (cjk * 0.6 + (s.length - cjk) * 0.25).toInt()
            }
            return messages.sumOf { est(it.second) } + segments.sumOf { est(it.second) }
        }

    companion object {
        /**
         * 自检闸门用：从**实发请求体 JSON**里拆出 (model, messages)。
         * 解析放在 data 层而不是桌面自检里——桌面模块没有 serialization-json 依赖。
         */
        fun modelAndMessagesFromBody(body: String): Pair<String, List<Pair<String, String>>>? =
            runCatching {
                val root = kotlinx.serialization.json.Json.parseToJsonElement(body)
                    as kotlinx.serialization.json.JsonObject
                val model = (root.getValue("model") as kotlinx.serialization.json.JsonPrimitive).content
                val msgs = (root.getValue("messages") as kotlinx.serialization.json.JsonArray).map { m ->
                    val o = m as kotlinx.serialization.json.JsonObject
                    (o.getValue("role") as kotlinx.serialization.json.JsonPrimitive).content to
                        (o.getValue("content") as kotlinx.serialization.json.JsonPrimitive).content
                }
                model to msgs
            }.getOrNull()
    }
}

/**
 * 本轮真实用量（用量查询 112 的展示侧）：`chatStream` 收尾时按会话留存，
 * 查看器「本轮真实用量」行的数据源。持久台账在 [UsageLog]，这里只留最近一次。
 */
class UsageNote(
    val promptTokens: Int?,
    val completionTokens: Int?,
    /** 思考字符量（reasoning_content / `<think>` 收集）：多数端点不报 reasoning token，字符占比只是量级感 */
    val reasoningChars: Int,
    /** 正文（可见回复）字符量 */
    val replyChars: Int,
    val finishReason: String?,
    /** 输出被预算截断（finish_reason=length）时的旋钮提示；正常结束为 null */
    val budgetHint: String?,
) {
    /** 服务商有没有回报真实 token（没报就只剩字符量级） */
    val reported: Boolean get() = promptTokens != null || completionTokens != null

    /**
     * 结束原因的对外口径（2026-09-29 用户反馈"正常输出写 stop 看不懂"）：raw 值是
     * OpenAI 兼容协议术语，直接透传用户看不懂——常见值翻成中文，认不出的原样透传（别猜）。
     */
    val finishReasonLabel: String?
        get() = when (finishReason) {
            null -> null
            "stop" -> "正常结束"
            "length" -> "输出预算截断"
            "content_filter" -> "内容安全拦截"
            "tool_calls" -> "工具调用"
            else -> finishReason
        }
}

/**
 * 按会话留**最近一次**请求的快照与用量。内存态：进程退出即清空，不落盘（下一轮请求马上就有新的）。
 * 快照与用量成对：新快照记录（＝新请求发出）时旧用量作废，避免"上一次的用量配这一次的装配"。
 */
object RequestViewer {
    private val lock = Any()
    private val last = LinkedHashMap<String, RequestSnapshot>()
    private val lastUsage = LinkedHashMap<String, UsageNote>()

    fun record(conversationId: String, snap: RequestSnapshot) {
        synchronized(lock) {
            last[conversationId] = snap
            lastUsage.remove(conversationId)
        }
    }

    fun recordUsage(conversationId: String, note: UsageNote) {
        synchronized(lock) { lastUsage[conversationId] = note }
    }

    fun lastOf(conversationId: String): RequestSnapshot? =
        synchronized(lock) { last[conversationId] }

    /** 最近一次请求的真实用量；响应还没回来（或该会话还没发过）为 null */
    fun usageOf(conversationId: String): UsageNote? =
        synchronized(lock) { lastUsage[conversationId] }
}
