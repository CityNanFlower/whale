package com.mysticat.roleplay.data

/**
 * 思考段过滤器（2026-09-15，P1-A「思考内容被当成正文回出来」）。
 *
 * **背景**：MiniMax 一类的推理模型有个"默认行为"——不传 `reasoning_split` 时，思维链会被
 * **包在 `<think>…</think>` 里直接塞进 `content` 字段**（官方文档如此说明：该参数不控制要不要思考，
 * 只控制思考返回到 `reasoning_content` 还是 `content`）。于是思考文本就跟着正文一起显示进了聊天气泡。
 *
 * 两头都做了处理：
 * 1. 请求侧按供应商能力补上 `reasoning_split`（见 `AiClient.reasoningSplitBody`），让服务端把思考拆走；
 * 2. 这里再兜一道：万一服务端没拆（或换成了别的聚合网关），**成对标记包裹的内容一律不显示**。
 *
 * **为什么不用一句正则替换**：真流式是**逐 delta** 到达的，`<thi` 与 `nk>` 完全可能分属两个 delta——
 * 对单个 delta 跑正则，两头都会漏（既漏剥、又可能把正文切坏）。所以这里保留一个尾巴缓冲：
 * 收到"可能是标记开头"的片段就先按住，等下一段到齐再判断，确定不是标记才放行。
 *
 * **只剥离成对标记包裹的段落**，不做任何"哪段看起来像思考"的启发式猜测 ——
 * 角色说"我想……"、正文里出现"思考"两个字，都不会被误伤。
 *
 * 顺带一个好处：`suggestReplies` 要解析模型返回的 JSON，思考块混在前面本来就会把 JSON 解析打挂。
 */
class ThinkingFilter {

    private val buf = StringBuilder()
    private var inside = false

    /** 喂一段增量，返回**现在就能显示**的部分（可能为空串，表示都在等下一段） */
    fun feed(chunk: String): String {
        if (chunk.isEmpty()) return ""
        buf.append(chunk)
        return drain()
    }

    /**
     * 流结束：把还按在缓冲里的内容按当前状态决定去留。
     *
     * 仍在标记内 ⇒ 丢弃（未闭合的思考块 = 思考没写完）；否则照常放行 ——
     * 它只是"还没等到下一段来判断是不是标记"，不是要丢的内容。
     */
    fun finish(): String {
        if (inside) {
            buf.setLength(0)
            return ""
        }
        val rest = buf.toString()
        buf.setLength(0)
        return rest
    }

    private fun drain(): String {
        val out = StringBuilder()
        while (true) {
            if (inside) {
                val hit = findComplete(CLOSE_TAGS)
                if (hit == null) {
                    // 还没看到结束标记：把"已经确定是思考"的部分丢掉，只留可能拼出标记的尾巴
                    holdBack(CLOSE_TAGS)
                    return out.toString()
                }
                buf.delete(0, hit.second)   // 丢掉标记及其前面的全部思考内容
                inside = false
                continue
            }
            val hit = findComplete(OPEN_TAGS)
            if (hit == null) {
                emitExceptTail(out, OPEN_TAGS)
                return out.toString()
            }
            val (start, end) = hit
            if (start > 0) {
                out.append(buf, 0, start)
                buf.delete(0, start)
            }
            // 删掉标记本身：前面的 start 个字符已删，所以标记现在占 [0, end-start)
            buf.delete(0, end - start)
            inside = true
        }
    }

    /**
     * 找最早的**完整**标记（`<think>` / `<think >` / `<think 任意短属性>`）。
     * 返回 (起始下标, 结束下标+1)；找不到、或标记迟迟不收尾（超过 [MAX_TAG_LEN]）都返回 null
     * —— 后者是防呆：真正的标记很短，一个没有 '>' 的长串应当当普通文本。
     */
    private fun findComplete(tags: List<String>): Pair<Int, Int>? {
        val lower = buf.toString().lowercase()
        var bestStart = -1
        var bestLen = 0
        for (t in tags) {
            val i = lower.indexOf(t)
            if (i >= 0 && (bestStart < 0 || i < bestStart)) {
                bestStart = i
                bestLen = t.length
            }
        }
        if (bestStart < 0) return null
        val gt = lower.indexOf('>', bestStart + bestLen)
        if (gt < 0 || gt - bestStart > MAX_TAG_LEN) return null
        return bestStart to (gt + 1)
    }

    /** 只保留末尾"可能正在拼出标记"的一小段，其余全丢（用于标记内部） */
    private fun holdBack(tags: List<String>) {
        val keep = tailTagPrefixLen(tags)
        if (buf.length > keep) buf.delete(0, buf.length - keep)
    }

    /** 除末尾"可能正在拼出标记"的尾巴外，其余都放行（用于标记外部） */
    private fun emitExceptTail(out: StringBuilder, tags: List<String>) {
        val keep = tailTagPrefixLen(tags)
        val emit = buf.length - keep
        if (emit > 0) {
            out.append(buf, 0, emit)
            buf.delete(0, emit)
        }
    }

    /** 末尾有多少个字符，可能是某个标记的开头（例如尾部的 "<thi" 可能是 "<think>" 的前半截） */
    private fun tailTagPrefixLen(tags: List<String>): Int {
        val max = tags.maxOf { it.length }
        val floor = maxOf(0, buf.length - max)
        for (len in (buf.length - floor) downTo 1) {
            val tail = buf.substring(buf.length - len).lowercase()
            if (tags.any { it.startsWith(tail) }) return len
        }
        return 0
    }

    companion object {
        /** 标记名（都小写、不带 '>'）；判断时大小写不敏感 */
        private val OPEN_TAGS = listOf("<think", "<thinking", "<reasoning")
        private val CLOSE_TAGS = listOf("</think", "</thinking", "</reasoning")

        /** 标记本身（含可能的短属性）不应该超过这个长度，超过就当普通文本 */
        private const val MAX_TAG_LEN = 24

        /** 一次性清洗（非流式响应，或已经拼好的整段文本） */
        fun strip(text: String?): String {
            if (text.isNullOrEmpty()) return text.orEmpty()
            val f = ThinkingFilter()
            return f.feed(text) + f.finish()
        }
    }
}
