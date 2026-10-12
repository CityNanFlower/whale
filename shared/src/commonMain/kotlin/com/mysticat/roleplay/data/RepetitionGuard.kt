package com.mysticat.roleplay.data

/**
 * 流式复读检测（0.2.4 第 173 轮，用户实测）：`dots3-note-prev` 在一次回复里把同一段 714 字的剧情
 * **逐字**循环了 13 遍、直到输出预算耗尽被掐断（结尾断在半句）——模型掉进了复读死循环。
 * 上游管不了模型，这里在收流侧检测：结尾出现同一段单元 ≥[MIN_REPEATS] 次逐字重复 ⇒ 判定循环，
 * 返回"该保留到哪"（第一遍单元的末尾），调用方掐断流、按这个点截断保存。
 *
 * 判据刻意从严（两遍逐字对齐验证＋单元有最小长度）：正常写作里呼应、复述、排比很常见，
 * **宁可漏检**（循环继续，用户可手动停止）也不能把正常回复误截。
 */
object RepetitionGuard {
    /** 判定循环的最小单元长度：更短的逐字重复在正常文本里太常见 */
    const val MIN_UNIT = 50

    /** 判定循环需要的最少重复遍数（两遍可能是刻意呼应，三遍逐字对齐基本只有死循环） */
    const val MIN_REPEATS = 3

    /**
     * 检查文本结尾是否处于逐字复读循环中。
     * @return 建议保留的长度（截到第一遍循环单元结束）；null＝未检出。
     *
     * 做法：取结尾固定长的一小段作锚点，往回找它上一次出现的位置 ⇒ 两个起点的距离就是候选单元长
     * （锚点可能在单元内出现多次，逐个候选验证，最多试 3 个；验证不过就等下一个增量再查）。
     */
    fun loopKeepEnd(
        s: String,
        minUnit: Int = MIN_UNIT,
        minRepeats: Int = MIN_REPEATS
    ): Int? {
        val n = s.length
        if (minUnit * minRepeats > n) return null
        val anchorLen = 24
        if (n < anchorLen * 2 + minUnit) return null
        val anchor = s.substring(n - anchorLen)
        var searchEnd = n - anchorLen - 1
        repeat(3) {
            val p = s.lastIndexOf(anchor, searchEnd)
            if (p < 0) return null
            searchEnd = p - 1
            val unit = (n - anchorLen) - p
            if (unit < minUnit) return@repeat
            // 后面的候选只会更大，更凑不够遍数，直接放弃
            if (unit * minRepeats > n) return null
            // 验证：末尾 unit 字与前一两遍逐字相同（对齐按结尾，流式收半截单元也不影响）
            var matched = true
            for (r in 2..minRepeats) {
                if (!s.regionMatches(n - unit, s, n - unit * r, unit)) { matched = false; break }
            }
            if (matched) {
                // 循环起点：从末尾第 minRepeats 遍处继续向前扩同单元
                var start = n - unit * minRepeats
                while (start - unit >= 0 && s.regionMatches(start - unit, s, start, unit)) start -= unit
                return start + unit
            }
        }
        return null
    }
}

/**
 * 复读循环的中断信号：从 `chatStream` 的 onDelta 回调里抛出（它会原样冲出流式读取循环），
 * 由发送方（ChatViewModel 的重试环）接住——**不是** [AiException]，不参与档位回落与抬预算重发：
 * 复读时正文已经在源源不断地来，任何形式的"再发一次"都只会再循环一遍、多花一笔。
 *
 * @property keepEnd 建议保留的字符数（[RepetitionGuard.loopKeepEnd] 的结果）
 */
class RepetitionLoopException(val keepEnd: Int) :
    IllegalStateException("模型陷入复读循环，已在第 $keepEnd 字处截断")
