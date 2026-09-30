package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * 素材的**形态**：决定列表里怎么画（缩略图还是正文预览），不是分类。
 *
 * **没有音频**（用户 2026-09-27 口径）：素材库收的是"AI 生成、值得留着再用"的东西，
 * 而音频（朗读音、背景音乐）各有自己的落点（音乐库 / TTS 每次现生成），塞进来只是噪音。
 *
 * **落进 `materials.json` 的就是 [name]**（`"IMAGE"` / `"TEXT"`），改 [label] 只动界面文案，
 * 改枚举名会让老库里那些条目读不出来。
 */
enum class MaterialKind(val label: String) {
    IMAGE("图片"),
    TEXT("文字");

    companion object {
        /** 认不出来的值按"文字"处理：宁可显示成一条纯文本，也不要整条丢失 */
        fun of(value: String): MaterialKind = entries.firstOrNull { it.name == value } ?: TEXT
    }
}

/**
 * 素材的**项目分类**（用户 2026-09-27 口径）：按"它是哪一类创作项目的产出"分，
 * 与灵感创作的产出形态一一对应——形象（生图）、背景（会话/角色背景）、角色卡、世界书、故事。
 *
 * **这是素材库唯一的分类维度**（用户 2026-09-27 再定）：一条素材只属于一类，界面上是单选。
 * 此前还有一层"自由标签"当第二个筛选维度，已删——生成时自动打的那几个标签（"灵感创作""世界书"）
 * 与分类**同名不同源**，界面上就出现两颗"世界书"、还有"灵感创作"这种根本不是分类的胶囊。
 */
enum class MaterialCategory(val label: String) {
    AVATAR("形象"),
    BACKGROUND("背景"),
    CARD("角色卡"),
    WORLDBOOK("世界书"),
    STORY("故事");

    companion object {
        fun of(value: String): MaterialCategory? = entries.firstOrNull { it.name == value }

        /**
         * 老条目没有 [Material.category] 字段（加分类之前存的）：从 [Material.origin] 里认回来。
         *
         * `origin` 当初就是"这条从哪个创作项目来"（"灵感创作 · 形象" / "灵感创作 · 世界书"…），
         * 所以这一层推断是无损的；认不出的一律留空（界面显示"未分类"），不硬塞一个分类进去。
         */
        fun infer(origin: String): MaterialCategory? = when {
            origin.contains("形象") -> AVATAR
            origin.contains("背景") -> BACKGROUND
            origin.contains("世界书") -> WORLDBOOK
            origin.contains("故事") -> STORY
            origin.contains("角色") -> CARD
            else -> null
        }
    }
}

/**
 * 素材库里的一条。**素材是数据不是缓存**——生成品花过钱、用户也想留着，
 * 所以它住在 `accounts/<id>/materials/`（数据树）而不是缓存根，见 `Repository` 里那段说明。
 *
 * [title] 是**应用内显示名**（与音乐库同口径）：重命名只改它，[fileName] 一直不动。
 * 文字素材正文存在 [text] 里（不落文件：几 KB 的文本单独一个文件反而更难备份/迁移）。
 */
@Serializable
data class Material(
    val id: String,
    /** [MaterialKind.name]；用字符串存是为了"以后加类型"时不炸老库 */
    val kind: String,
    val title: String,
    /** [MaterialCategory.name]；空串＝未分类（老条目由 [categoryValue] 从 origin 兜底认出来） */
    val category: String = "",
    /** 文字素材的正文；图片为空 */
    val text: String = "",
    /** `materials/` 里的文件名（生成名）；文字素材为空 */
    val fileName: String = "",
    /** 这条从哪来（"灵感创作 · 形象" 之类），列表上给一行小字，用户可以据此判断要不要留 */
    val origin: String = "",
    val addedAt: Long = 0L
) {
    val kindValue: MaterialKind get() = MaterialKind.of(kind)

    /** 显式分类优先，没有就从 [origin] 推（老库不用做一次性迁移：读的时候认一次就够） */
    val categoryValue: MaterialCategory?
        get() = MaterialCategory.of(category) ?: MaterialCategory.infer(origin)

    /** 列表页要一句话看清这是什么：正文压缩空白后截断 */
    fun previewText(max: Int = 80): String =
        text.replace(Regex("\\s+"), " ").trim().let { if (it.length <= max) it else it.take(max) + "…" }
}

val MATERIALS_SERIALIZER = ListSerializer(Material.serializer())
