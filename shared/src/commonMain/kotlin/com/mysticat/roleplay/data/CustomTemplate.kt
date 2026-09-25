package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable

/** 用户自建模板（角色/故事），随账号持久化，可删除 */
@Serializable
data class CustomTemplate(
    val id: String,
    val name: String,
    val mode: String, // "角色" | "故事"
    val fields: List<String> = emptyList(),
    /**
     * 形态（[Engines] 的 formTag：""陪伴 / "experience"多线 / "play"玩法 / "tool"工具）。
     * 第 79 轮新增，默认空＝陪伴——**老数据不用迁移**：反序列化时缺这个键就是 ""，行为与之前一致。
     * 自建模板也要认形态：否则用户按「工具」存一个模板，下次套用时形态不跟着切，
     * 生成出来的仍是一张陪伴卡（与内置模板同一条口径）。
     */
    val form: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** templates.json 包装 */
@Serializable
data class CustomTemplateList(
    val templates: List<CustomTemplate> = emptyList()
)
