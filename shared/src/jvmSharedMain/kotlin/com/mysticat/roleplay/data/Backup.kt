package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 全量备份 / 恢复：角色卡 + 会话记录 + 被引用的图片（formatVersion ≥ 2）。
 * 出于安全考虑，API Key 不进备份文件。
 *
 * 图片以 base64 内嵌在 assets 里（键 = 导出时的本地绝对路径），恢复时重新落盘并改写引用，
 * 这样换机恢复后头像 / 背景不再是断链；旧格式（无 assets）照常可导入。
 */
object Backup {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Serializable
    data class BackupBundle(
        val app: String = "mysticat-roleplay",
        val formatVersion: Int = 2,
        val exportedAt: Long = System.currentTimeMillis(),
        val characters: List<CharacterCard> = emptyList(),
        val conversations: List<Conversation> = emptyList(),
        /** 本地图片内嵌：键 = 原文件绝对路径，值 = base64(文件内容) */
        val assets: Map<String, String> = emptyMap()
    )

    data class RestoreResult(val characters: Int, val conversations: Int, val images: Int)

    /**
     * 备份要内嵌的本机图片路径：头像 / 背景（**含分端的桌面端那份**）/ **消息里的图**。
     *
     * 抽成公开纯函数是为了让桌面自检能直接验它（第 59 轮）：判据曾是 Android 专属的 `startsWith("/")`，
     * 在桌面（盘符路径）上恒假 ⇒ 备份的 `assets` 恒为空 ⇒ **换机恢复后所有图片断链**，而这件事
     * 在导出时完全看不出异常（导出的 JSON 照样"成功"）。缺 `backgroundUriDesktop` 的后果同理由此守住。
     */
    fun collectAssetPaths(characters: List<CharacterCard>, conversations: List<Conversation>): Set<String> = buildSet {
        characters.forEach {
            it.avatarUri?.takeIf(::isLocalFilePath)?.let(::add)
            it.backgroundUri?.takeIf(::isLocalFilePath)?.let(::add)
            // 分端背景的桌面端那份：漏了它，换机恢复后桌面端背景会断链（A 批次）
            it.backgroundUriDesktop?.takeIf(::isLocalFilePath)?.let(::add)
        }
        conversations.forEach { c ->
            c.backgroundUri?.takeIf(::isLocalFilePath)?.let(::add)
            c.backgroundUriDesktop?.takeIf(::isLocalFilePath)?.let(::add)
            // 用户随消息发的图：漏了它，备份里就没有聊天图片，换机后全部断链
            c.messages.forEach { m -> m.imageUri?.takeIf(::isLocalFilePath)?.let(::add) }
        }
    }

    /**
     * 把备份里的图片引用改写成恢复后的新路径（`remap` = 旧路径 → 新落盘路径；查不到的原样保留）。
     *
     * 纯函数，理由同 [collectAssetPaths]：**两份背景各自落回正确的端**（手机端那份不能写进桌面端字段）、
     * 以及"某端明确不要背景"的空串**不能复活成 null**（复活＝恢复后那张背景又冒出来），
     * 这两件事都在这里，而它们在界面上都表现为"图片位置不对"，只有自检拦得住。
     */
    fun remapBundleRefs(bundle: BackupBundle, remap: Map<String, String>): BackupBundle {
        // 空串（"该端明确不要背景"）不是 null ⇒ 走不到 remap 里，原样保留
        fun fix(uri: String?): String? = uri?.let { remap[it] ?: it }
        return bundle.copy(
            characters = bundle.characters.map {
                it.copy(
                    avatarUri = fix(it.avatarUri),
                    backgroundUri = fix(it.backgroundUri),
                    backgroundUriDesktop = fix(it.backgroundUriDesktop)
                )
            },
            conversations = bundle.conversations.map {
                // 消息里的图片也要改写成本机的新路径（否则恢复后聊天图指向旧机器的文件）
                it.copy(
                    backgroundUri = fix(it.backgroundUri),
                    backgroundUriDesktop = fix(it.backgroundUriDesktop),
                    messages = it.messages.map { m -> if (m.imageUri == null) m else m.copy(imageUri = fix(m.imageUri)) }
                )
            }
        )
    }

    /** 解析备份文本（**自检要的钩子**：验"导出到底内嵌了哪些图"必须能读到 `assets`） */
    fun parseBundle(text: String): BackupBundle = json.decodeFromString(BackupBundle.serializer(), text)

    fun exportJson(): String {
        val chars = Repository.listCharacters()
        val convs = Repository.listConversationsForAll()
        // 收集被这套数据引用的本地图片（头像 / 背景 / **消息里的图**），换机恢复后不断链
        val paths = collectAssetPaths(chars, convs)
        val assets = paths.mapNotNull { path ->
            runCatching { java.util.Base64.getEncoder().encodeToString(File(path).readBytes()) }
                .getOrNull()
                ?.let { path to it }
        }.toMap()
        return json.encodeToString(
            BackupBundle.serializer(),
            BackupBundle(characters = chars, conversations = convs, assets = assets)
        )
    }

    /** 解析成功后才落盘：先清空现有数据，再按备份内容重建（等于“恢复”语义）。 */
    fun importJson(text: String): RestoreResult {
        if (text.isBlank()) throw IllegalArgumentException("备份文件内容为空")
        val bundle = runCatching { json.decodeFromString(BackupBundle.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("不是有效的备份文件，已取消恢复") }

        // 内嵌图片先落盘，建立「旧路径 → 新路径」映射（同一路径多次引用只落一次盘）
        val remap = bundle.assets.mapValues { (path, b64) ->
            runCatching {
                val ext = path.substringAfterLast('.', "png")
                Repository.saveImageBytes(java.util.Base64.getDecoder().decode(b64), ext)
            }.getOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }

        // 清空当前数据（P2-B14：deleteCharacter 内部已经删掉该角色的全部会话，
        // 原来额外那趟 listConversationsForAll().forEach { deleteConversation } 是重复劳动）
        Repository.listCharacters().forEach { Repository.deleteCharacter(it.id) }
        // 兜底：清掉"没有任何角色对应"的孤儿会话（历史遗留数据）
        Repository.listConversationsForAll().forEach { Repository.deleteConversation(it.id) }

        val restored = remapBundleRefs(bundle, remap)
        restored.characters.forEach { Repository.saveCharacter(it) }
        restored.conversations.forEach { Repository.saveConversation(it) }
        return RestoreResult(bundle.characters.size, bundle.conversations.size, remap.size)
    }
}
