package com.mysticat.roleplay.data

/**
 * 「输出格式」预设与「按格式重排」（E4 收尾，2026-09-22）。
 *
 * **为什么要有预设**：实测有效的格式要求长这样——"每行「- [ ] 事项（负责人）」"、"只给 10 个编号名字、
 * 不要别的字"（台账 §5.2 修订 2）。这种话**可核对**，模型才照办；而工具卡的作者第一次打开
 * 「输出格式」那一栏时，最容易写的是"输出得清楚一点"这类没法核对的话。于是把四个最常用的形状
 * 写成预设，点一下就填进去（还能接着手改）。
 *
 * **为什么重排要用户点一下**：格式要求已经在系统提示词与本轮硬要求里，但长素材下模型常会自己
 * "优化"排版。这时得有一个**用户能点的动作**把结果按格式重排，而不是让用户手动重发素材
 * （台账 §5.5：输出格式做预设 ＋ 用户点一下才"重排"）。
 *
 * 住 `commonMain`：预设是编辑器（`jvmSharedMain`）与自检（desktopApp）都要读的纯数据。
 */
data class OutputFormatPreset(val label: String, val spec: String)

object OutputFormats {

    /**
     * 四个预设。措辞刻意**只写"要什么形状"**、不写"请务必""一定要"这类语气词——
     * 模型对可核对的结构敏感，对形容词不敏感。
     */
    val presets: List<OutputFormatPreset> = listOf(
        OutputFormatPreset(
            label = "纯文本",
            spec = "只输出一段连续的文字：不要标题、不要列表符号、不要表格、不要加粗等 markdown 标记。"
        ),
        OutputFormatPreset(
            label = "JSON",
            spec = "只输出一个合法的 JSON（对象或数组）：不要代码块标记、不要任何解释文字、不要注释。" +
                "键名用英文小写下划线。"
        ),
        OutputFormatPreset(
            label = "表格",
            spec = "用 Markdown 表格输出：第一行是表头，其后每行一条记录；表格外不写任何说明文字，不要总结段。"
        ),
        OutputFormatPreset(
            label = "编号",
            spec = "用有序列表输出：每行「1. 内容」，一行一条；不要标题、不要总结段、不要额外解释。"
        )
    )

    /**
     * 这一栏现在的内容对应哪个预设（编辑器据此把那个 chip 标成选中）。
     * 用户手改过就不再匹配——那时四个都不选中，正是"你自己写的要求"的如实呈现。
     */
    fun selectedLabel(current: String): String? =
        presets.firstOrNull { it.spec == current.trim() }?.label

    /**
     * 用户点「按格式重排」时，追加到**对话末尾**的那条指令（不入库，见 [reformatHistory]）。
     *
     * 刻意**不重复**格式要求本身：它就在系统提示词的【输出格式】里，而且 `ChatViewModel.tailHint`
     * 会把它贴着本轮生成点再贴一遍（同一段文字不占三份字）。这里只负责说清"这一轮只做重排"。
     */
    fun reformatInstruction(): String =
        "【按格式重排（本轮只做这一件事）】把上一条结果严格按上面的【输出格式】重新排一遍：" +
            "内容要点不变、不增删事实、不改结论，只改排版；只给出重排后的成品，不要解释、不要寒暄、" +
            "不要重复用户的素材。"

    /**
     * 「按格式重排」要发出去的历史：原对话（含待重排的那一版成品）＋ 末尾一条**不入库**的重排指令。
     *
     * 为什么不是"重新生成"那条路：`regenerate` 走的是 `subList(0, index)`，**待重排的成品不在上下文里**，
     * 模型只能照素材从头再做一遍——那叫重做，不叫重排（实测下它多半还会给出同一份排版）。
     * 所以重排必须让成品留在上下文里，并在生成点正前方追加一句"把上一条按格式重排"。
     *
     * 那条指令**只进本次请求**（`requestReply` 的 history 不入库，落库的是原对话 + 新成品），
     * 用户看到的因此是多了一版成品，而不是聊天记录里多了一句自言自语。
     *
     * 只允许对**最后一条** assistant 消息重排：否则新成品会被追加到对话末尾，位置就错了
     * （它回复的是中间那句话）。返回 null 表示"这个位置不做重排"，调用方据此不显示入口。
     */
    fun reformatHistory(messages: List<ChatMessage>, index: Int): List<ChatMessage>? {
        if (index != messages.lastIndex) return null
        if (messages.getOrNull(index)?.role != "assistant") return null
        return messages + ChatMessage(role = "user", content = reformatInstruction())
    }
}
