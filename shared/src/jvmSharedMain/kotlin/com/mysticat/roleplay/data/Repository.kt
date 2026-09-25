package com.mysticat.roleplay.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID

/**
 * 被引用到的图片路径集合（[Repository] 孤儿图判定的纯逻辑部分，抽出来是为了让桌面自检能直接验）。
 *
 * ⚠️ `messages[].imageUri` 必须一起收：漏了它，用户随消息发的图会被当成孤儿删掉
 * （「清除缓存」副标题当时写的是"不影响角色与会话"，用户看到的是聊天图片凭空消失）。
 * ⚠️ 新加一个"存图片路径的字段"就必须同步加到这里 —— 漏一个，用户那张图会在下一次清理/替换时被删。
 * ⚠️ 判据必须是 [isLocalFilePath] 而不是 `startsWith("/")`：后者是 Android 专属形态，在桌面（盘符路径）
 * 上**恒假** ⇒ 本集合恒为空 ⇒ 全部图片都被判成孤儿被删（第 59 轮修的桌面端事故，见 [isLocalFilePath]）。
 */
fun collectReferencedImagePaths(
    characters: List<CharacterCard>,
    conversations: List<Conversation>,
    profiles: List<Profile>
): Set<String> = buildSet {
    characters.forEach { c ->
        c.avatarUri?.takeIf(::isLocalFilePath)?.let { add(it) }
        c.backgroundUri?.takeIf(::isLocalFilePath)?.let { add(it) }
        c.backgroundUriDesktop?.takeIf(::isLocalFilePath)?.let { add(it) }
    }
    conversations.forEach { c ->
        c.backgroundUri?.takeIf(::isLocalFilePath)?.let { add(it) }
        c.backgroundUriDesktop?.takeIf(::isLocalFilePath)?.let { add(it) }
        c.messages.forEach { m -> m.imageUri?.takeIf(::isLocalFilePath)?.let { add(it) } }
    }
    profiles.forEach { p -> p.avatarUri?.takeIf(::isLocalFilePath)?.let { add(it) } }
}

/**
 * 极简本地 JSON 仓库：无需 Room/数据库。
 * 目录结构：files/roleplay/
 *   profiles.json, current_account.txt（全局账号列表）
 *   accounts/<id>/{settings.json, characters/, conversations/, worldbooks/, user_settings.json, images/}（每账号数据）
 */
object Repository {

    private lateinit var root: File

    // 宿主目录注入（M3 去 Context）：
    // filesRootDir = Android filesDir / 桌面 AppData 数据根（更新包残留目录挂在它下面）
    private lateinit var filesRootDir: File
    // cacheRootDir = Android cacheDir / 桌面缓存根（缓存统计清理 + TtsCache 共用）
    internal lateinit var cacheRootDir: File

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /** 音乐库清单的序列化器（显式传，仓库惯例；见 listBgmLibrary） */
    private val BGM_LIBRARY_SERIALIZER = kotlinx.serialization.builtins.ListSerializer(BgmLibraryEntry.serializer())

    fun init(filesRoot: File, cacheRoot: File) {
        if (!::root.isInitialized) {
            root = File(filesRoot, "roleplay").apply { mkdirs() }
            filesRootDir = filesRoot
            cacheRootDir = cacheRoot
        }
    }

    // ---------- 通用读写原语 ----------

    private const val TAG = "WhaleRepo"

    /** 临时文件序号：让并发写同一个文件的两个执行流各写各的临时文件（见 writeAtomically） */
    private val tmpSeq = java.util.concurrent.atomic.AtomicLong()

    /**
     * 原子写入：先写同目录临时文件再 rename 覆盖，避免写入中途被杀留下截断 JSON
     * （rename 在同一分区上是原子的）。
     */
    private fun writeAtomically(f: File, text: String) = writeAtomically(f) { it.writeText(text) }

    /**
     * 原子写入（字节版）：先写同目录临时文件再 rename 覆盖。
     *
     * 临时名**必须唯一**。以前用固定的 `f.name + ".tmp"`，两个执行流同时写**同一个**文件时：
     * A 的 rename 会把 B 正在写的临时文件搬走，B 随后执行 `f.delete()`（删掉 A 刚写好的成品）、
     * 自己的 rename 又失败 → 抛 IOException，**同时目标文件已被删除**（既崩又丢数据）。
     * 触发链真实可达：流式回复在 IO 线程上写会话文件（`onDelta`），主线程同时点「快进/细腻」
     * chip 或重命名（`persist`），写的是同一个 `conversations/<id>.json`。
     *
     * 失败分支也**不再删目标文件**：改用 NIO 的 replace 语义重试，两次都失败就抛异常，
     * 但原文件保持完好（宁可这次没存上，也不能把已有数据删掉）。
     */
    private inline fun writeAtomically(f: File, writer: (File) -> Unit) {
        val tmp = File(f.parentFile, f.name + "." + tmpSeq.incrementAndGet() + ".tmp")
        try {
            writer(tmp)
            if (!tmp.renameTo(f)) {
                val moved = runCatching {
                    java.nio.file.Files.move(
                        tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                    true
                }.getOrDefault(false)
                if (!moved) {
                    WhaleLog.e(TAG, "原子写入失败：${f.name}")
                    throw java.io.IOException("无法写入 ${f.name}")
                }
            }
        } finally {
            // 异常路径别留下垃圾临时文件；成功路径上 tmp 已被 rename 走，这里是 no-op
            if (tmp.exists()) runCatching { tmp.delete() }
        }
    }

    /** 解码单个数据文件；损坏时改名保留现场（.corrupt-时间戳）并返回 null，不静默吞也不反复解析坏文件 */
    private fun <T> decodeFile(f: File, serializer: KSerializer<T>): T? =
        runCatching { json.decodeFromString(serializer, f.readText()) }
            .onFailure { e ->
                WhaleLog.w(TAG, "JSON 损坏：${f.absolutePath}（${e.message}）")
                val stamp = System.currentTimeMillis()
                runCatching { f.renameTo(File(f.parentFile, f.name + ".corrupt-$stamp")) }
            }
            .getOrNull()

    // ---------- 当前账号的数据目录（每账号隔离） ----------

    private fun accountsDir(): File = File(root, "accounts").apply { mkdirs() }

    /** 当前账号的数据目录；未登录时用 root（兼容旧数据/兜底） */
    private fun activeDir(): File {
        val id = currentAccountId()
        return if (id.isNotBlank()) File(accountsDir(), id).apply { mkdirs() }
        else root
    }

    private fun charactersDir(): File =
        File(activeDir(), "characters").apply { mkdirs() }

    private fun conversationsDir(): File =
        File(activeDir(), "conversations").apply { mkdirs() }

    private fun imagesDir(): File =
        File(activeDir(), "images").apply { mkdirs() }

    // ---------- 设置 ----------

    /**
     * settings 内存缓存（2026-09-15，P1-4 第 2 步）。
     *
     * `loadSettings()` 在**主线程**被调用 28 处，其中两处是热路径：设置页 `isDirty` 每次求值都读一遍
     * （拖温度滑杆＝每帧一次），聊天页点发送的第一条语句也是它。每次都要「读盘 + JSON 解析 + (N+2) 次
     * Keystore 解密」，纯属重复劳动。
     *
     * 失效策略很简单，因为**只有一个写入口**（`saveSettings`）：
     * - 缓存按**文件路径**为键 —— 切换账号时 `activeDir()` 变了，路径自然不同，自动未命中，不用额外失效；
     * - 写入成功后把缓存更新成刚写的那份明文值。
     *
     * 返回的是同一个不可变实例（`AiSettings` 是 data class，各处 `providerKeys` 也都是 `+`/`-` 造新 Map，
     * 没有就地修改），所以共享实例是安全的。
     */
    private var cachedSettingsFile: File? = null

    @Volatile
    private var cachedSettings: AiSettings? = null

    /**
     * 设置对象的 **JSON 往返**（自检要的钩子，第 70 轮）。
     *
     * 为什么不让自检直接 `saveSettings` 再 `loadSettings`：`--smoke` 跑在**用户真实的数据目录**上，
     * 改写 settings.json 会把用户的凭据与配置搅进去，一次中断就是事故。而"新字段存不存得住"
     * 这个问题，序列化往返就能给出答案（同一个 `AiSettings.serializer()`，与落盘走的是同一条路）。
     */
    fun settingsJsonRoundTrip(s: AiSettings): AiSettings =
        json.decodeFromString(AiSettings.serializer(), json.encodeToString(AiSettings.serializer(), s))

    @Synchronized
    fun loadSettings(): AiSettings {
        val f = File(activeDir(), "settings.json")
        cachedSettings?.takeIf { cachedSettingsFile == f }?.let { return it }
        val s = if (f.exists()) decodeFile(f, AiSettings.serializer()) ?: AiSettings() else AiSettings()
        // ① 先把所有 Key 解密出来（providerKeys / imageProviderKeys / speechCredentials 逐条，加上两个旧的全局字段）
        val plain = s.copy(
            chatApiKey = Security.decrypt(s.chatApiKey).orEmpty(),
            imageApiKey = Security.decrypt(s.imageApiKey).orEmpty(),
            providerKeys = s.providerKeys.mapValues { Security.decrypt(it.value).orEmpty() },
            imageProviderKeys = s.imageProviderKeys.mapValues { Security.decrypt(it.value).orEmpty() },
            speechCredentials = s.speechCredentials.mapValues { (_, fields) ->
                fields.mapValues { Security.decrypt(it.value).orEmpty() }
            }
        )
        // ② 再按「一家一份 Key」解析出各分段实际要用的 Key（必须在解密之后做，否则会拿到密文）
        val decrypted = plain.copy(
            chatApiKey = plain.keyFor(plain.chatBaseUrl),
            imageApiKey = plain.imageKey()
        )
        cachedSettingsFile = f
        cachedSettings = decrypted
        // 明文自愈（第 63 轮安全审计）：磁盘上还留着明文 Key（当年加密不可用时存的，或从未重存过），
        // 而现在密钥库可用 —— 静默重写一次把它升级成密文。不这么做的话，明文会一直躺着，
        // 只在"用户又点了一次保存"时才可能被覆盖（很多用户根本不会再动设置）。
        if (Security.encryptedStorageAvailable && s.hasPlaintextSecret()) {
            enqueueWrite("API Key 升级为加密存储") {
                writeAtomically(f, json.encodeToString(AiSettings.serializer(), encryptSecrets(s)))
            }
        }
        return decrypted
    }

    /**
     * 把一份设置里所有明文 Key 换成密文（[Security.encrypt] 幂等：已是 `enc1:` 密文的原样返回）。
     * 抽成一个函数是因为**有两处**要用它：正常保存与上面的明文自愈。
     */
    private fun encryptSecrets(s: AiSettings): AiSettings = s.copy(
        chatApiKey = Security.encrypt(s.chatApiKey),
        imageApiKey = Security.encrypt(s.imageApiKey),
        providerKeys = s.providerKeys.mapValues { Security.encrypt(it.value) },
        imageProviderKeys = s.imageProviderKeys.mapValues { Security.encrypt(it.value) },
        speechCredentials = s.speechCredentials.mapValues { (_, fields) ->
            fields.mapValues { Security.encrypt(it.value) }
        }
    )

    /**
     * 保存设置（P2 批2，2026-09-15）：**不阻塞调用线程**，加密与落盘交给串行写入通道。
     *
     * 原来这里是同步的，于是设置页点「保存」那一下要在主线程上做完
     * 「逐条 Keystore 加密 providerKeys + 写整份 JSON」——一家一份 Key，7 条 Key 就是 7 次
     * Keystore 往返，是设置页最重的一次卡顿。
     *
     * 缓存**先更新**（存明文版，与 [loadSettings] 的返回值一致），所以写完立刻读不会拿到旧设置；
     * 代价是写失败时缓存里是"没落盘的那份"——与 P1-B 会话写路径的处理一致。
     *
     * [onDone] 在**写入线程**上被调用（成功时参数为 null）。它存在的另一个理由：
     * `Security.lastCryptoIssue`（Keystore 不可用降级明文之类的告警）只有在加密真正跑完之后读才有意义，
     * 调用方据此回到主线程更新提示。
     */
    fun saveSettings(s: AiSettings, onDone: ((Throwable?) -> Unit)? = null) {
        val f = File(activeDir(), "settings.json")
        cachedSettingsFile = f
        cachedSettings = s
        // 背景音乐（第 59 轮）：挂在这个**唯一落库口**上，界面就不必在每个改设置的地方手抄一次
        // "同步给播放器"。幂等的：音源/开关没变时它只更新音量（见 BgmPlayer.sync）。
        runCatching { BgmPlayer.sync(s) }
        enqueueWrite("设置", onDone = onDone) {
            writeAtomically(f, json.encodeToString(AiSettings.serializer(), encryptSecrets(s)))
        }
    }

    // ---------- 背景音乐（第 59 轮；第 63 轮起单文件升级为「音乐库」）----------

    /**
     * 用户上传的 BGM 目录 `accounts/<id>/bgm/`。
     *
     * ⚠ 两个目录都**不能**用，这是"加一个存路径的字段"那个老坑的最新一处：
     * ① `images/`——它的清理规则是"按引用集合回收孤图"（10 分钟缓冲后删掉没人引用的），
     *    用户上传的 BGM 放进去会被当成孤图删掉；
     * ② `cacheRoot`（内置白噪音就放那儿）——那是"可以被清掉的缓存"，而用户上传的音乐是**他的文件**，
     *    「清除缓存」不该动它。
     *
     * 第 63 轮起目录里是**多首曲目**（音乐库，清单落 `bgm_library.json`）；「只留一份」的旧规矩
     * 随全局单文件一起作废——删除一律走 [deleteBgmLibraryEntry]（用户显式删，带确认）。
     */
    private fun bgmDir(): File = File(activeDir(), "bgm").apply { mkdirs() }

    /** 兼容旧调用（BgmCard 老上传路）：现在只是往音乐库里加一首，返回落盘绝对路径 */
    fun saveBgmFile(bytes: ByteArray, extension: String): String {
        val entry = addBgmLibraryEntry(bytes, extension, "")
        return File(bgmDir(), entry.fileName).absolutePath
    }

    /** 删掉用户上传的背景音乐（旧入口；保留以免遗漏调用点，语义=清空音乐库） */
    fun deleteBgmFiles() {
        bgmDir().listFiles()?.forEach { runCatching { it.delete() } }
        saveBgmLibrary(emptyList())
    }

    // ---------- 编辑草稿（第 63 轮，用户要求：退出应用后未保存的输入要还在）----------

    /**
     * 轻量草稿：只存"用户正在写、还没提交"的文本（AI 起草的生图提示词、创作页的提示词框…）。
     *
     * 为什么不复用 settings.json / conversations：这些草稿的生命周期与它们都不同——
     * 它不是配置也不是会话内容，退出应用时**必须还在**（用户口径："退出下次重新开启还能看到离开前状态的原样"），
     * 但也不该污染备份与回收链路。所以自成一份 `drafts.json`：键值对，键是稳定的字符串常量。
     *
     * 清空某键 = 传空串（写盘时删掉该键）；这与"提示词用完后不自动删，还是通过叉号删"一致。
     * 缓存同 [loadSettings]：按文件路径为键，切账号自然失效。
     */
    private fun draftsFile(): File = File(activeDir(), "drafts.json")

    private var draftsCacheFile: File? = null

    @Volatile
    private var draftsCache: Map<String, String>? = null

    @Synchronized
    fun loadDrafts(): Map<String, String> {
        val f = draftsFile()
        draftsCache?.takeIf { draftsCacheFile == f }?.let { return it }
        // 用 JsonObject 而不是 Map 序列化器：草稿的键是自由的字符串，
        // 手工建 JsonObject 比配一套 MapSerializer 更省事，也不会踩泛型推断的坑
        val m = runCatching {
            json.parseToJsonElement(f.readText()).jsonObject
                .mapValues { (_, v) -> v.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
        draftsCacheFile = f
        draftsCache = m
        return m
    }

    fun loadDraft(key: String): String = loadDrafts()[key].orEmpty()

    /**
     * drafts.json 的落点（**自检要的钩子**）。
     * 为什么要有它：落点随"当前有没有账号"变（有账号在 `accounts/<id>/` 下，没有就在 `roleplay/` 根），
     * 自检**不要自己拼这个路径** —— 第 59 轮就是这么暴露的：冷数据根（沙箱/全新目录）下没有当前账号，
     * 自检按 `accounts/<id>/drafts.json` 找文件必然落空，于是"退出后草稿还在"这项在冷根上恒 FAIL。
     */
    fun draftsFilePath(): String = draftsFile().absolutePath

    /** 写一条草稿（空串 = 删除该键，见本段说明）；落盘是原子的 */
    fun saveDraft(key: String, value: String) {
        val next = loadDrafts().toMutableMap()
        if (value.isBlank()) next.remove(key) else next[key] = value
        val obj = kotlinx.serialization.json.buildJsonObject {
            next.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
        }
        val f = draftsFile()
        draftsCacheFile = f
        draftsCache = next
        writeAtomically(f) {
            it.writeText(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj))
        }
    }

    /** 清掉全部草稿（登出/切账号时的收尾；正常退出**不**清） */
    fun clearDrafts() {
        val f = draftsFile()
        draftsCacheFile = f
        draftsCache = emptyMap()
        runCatching { f.delete() }
    }

    // ---- 音乐库（第 63 轮）：多曲目 + 应用内显示名（重命名**不改**源文件名，用户口径）----

    private fun bgmLibraryFile(): File = File(activeDir(), "bgm_library.json")

    /**
     * 内存缓存：曲目清单很小、UI 每次重组都要读，走 [loadSettings] 同款"按文件路径为键"的缓存
     * ——切账号时 `activeDir()` 变了、路径不同，自动未命中，不用额外失效。
     */
    private var bgmLibraryCacheFile: File? = null

    @Volatile
    private var bgmLibraryCache: List<BgmLibraryEntry>? = null

    fun listBgmLibrary(): List<BgmLibraryEntry> {
        val f = bgmLibraryFile()
        bgmLibraryCache?.takeIf { bgmLibraryCacheFile == f }?.let { return it }
        val list = runCatching {
            json.decodeFromString(BGM_LIBRARY_SERIALIZER, f.readText())
        }.getOrDefault(emptyList())
        bgmLibraryCacheFile = f
        bgmLibraryCache = list
        return list
    }

    private fun saveBgmLibrary(list: List<BgmLibraryEntry>) {
        val f = bgmLibraryFile()
        bgmLibraryCacheFile = f
        bgmLibraryCache = list
        writeAtomically(f) { it.writeText(json.encodeToString(BGM_LIBRARY_SERIALIZER, list)) }
    }

    /**
     * 收录一首上传的音乐：文件落 `bgm/` 目录（生成名，**不改用户的源文件名**——
     * 显示名独立存在 [BgmLibraryEntry.displayName] 里，重命名只动它），并写进清单。
     */
    fun addBgmLibraryEntry(bytes: ByteArray, extension: String, displayName: String): BgmLibraryEntry {
        val ext = extension.trimStart('.').lowercase().ifBlank { "mp3" }
        val name = "bgm_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        writeAtomically(File(bgmDir(), name)) { it.writeBytes(bytes) }
        val entry = BgmLibraryEntry(
            id = "m${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            displayName = displayName.ifBlank { name },
            fileName = name,
            addedAt = System.currentTimeMillis()
        )
        saveBgmLibrary(listBgmLibrary() + entry)
        return entry
    }

    /** 重命名：只改应用内显示名（用户 2026-09-21 口径），文件本体不动 */
    fun renameBgmLibraryEntry(id: String, newName: String): Boolean {
        val list = listBgmLibrary()
        val target = list.firstOrNull { it.id == id } ?: return false
        saveBgmLibrary(list.map { if (it.id == id) it.copy(displayName = newName.ifBlank { target.displayName }) else it })
        return true
    }

    /** 删除一首（界面带确认）：清单移除 + 文件删除；返回是否真删了东西 */
    fun deleteBgmLibraryEntry(id: String): Boolean {
        val list = listBgmLibrary()
        val target = list.firstOrNull { it.id == id } ?: return false
        saveBgmLibrary(list.filter { it.id != id })
        runCatching { File(bgmDir(), target.fileName).delete() }
        return true
    }

    /** 某首曲目的本地文件（不存在=被手动删过/迁移丢了；调用方要给用户明确提示而不是静默无声） */
    fun bgmLibraryFileFor(id: String): File? {
        val entry = listBgmLibrary().firstOrNull { it.id == id } ?: return null
        val f = File(bgmDir(), entry.fileName)
        return f.takeIf { it.isFile }
    }

    /** 旧全局单文件（`AiSettings.bgmUserPath`）导入音乐库的一次性迁移；由设置页打开时调用 */
    fun migrateLegacyBgmIntoLibrary(settings: AiSettings): AiSettings {
        if (settings.bgmSource != BgmSources.SOURCE_USER || settings.bgmUserPath.isBlank()) return settings
        if (listBgmLibrary().isNotEmpty()) return settings // 已有库：不动老字段，避免重复导入
        val src = File(settings.bgmUserPath)
        if (!src.isFile) return settings
        return runCatching {
            val entry = addBgmLibraryEntry(src.readBytes(), src.extension.ifBlank { "mp3" }, settings.bgmUserName)
            // 老字段清空；全局默认指到库里的这首（开关保持原样）
            settings.copy(
                bgmSource = "lib:${entry.id}",
                bgmUserPath = "",
                bgmUserName = ""
            )
        }.getOrDefault(settings)
    }

    // ---------- 角色 ----------

    /**
     * 角色的**写穿缓存**（P2 第 4 轮，2026-09-16）。
     *
     * 与会话同一套理由：写改成"记内存 + 排队落盘"之后，**已排队还没落盘的改动必须立刻读得到**，
     * 否则"编辑角色 → 返回列表"会看到旧数据，`referencedImagePaths()` 也会按旧引用判断，
     * 把刚换上的新图当成孤儿删掉。
     */
    private val charCache = java.util.concurrent.ConcurrentHashMap<String, CharacterCard>()

    /** 已排队未落盘的角色删除：读与列表都当"不存在"（否则文件还在时角色会"复活"） */
    private val pendingCharDeletes: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * 角色文件扫描缓存（第 64 轮补上，逻辑在 [DirScanCache]）。
     *
     * 会话那边一直有、角色这边**一直缺**：`listCharacters()` 原来每次调用都把角色目录下
     * **所有** JSON 全量解析一遍，谁调谁付费。而它的调用方里既有后台（列表页 `refresh()`），
     * 也有**界面线程**（聊天记录页的角色选择框、发现页组合期）——角色一多就是"点一下卡一下"。
     * `charCache`（写穿缓存）在外层再叠加一次，掩盖"已排队未落盘"的改动。
     */
    private val charScan = DirScanCache { f -> decodeFile(f, CharacterCard.serializer()) }

    /** 真的解析了几份角色 JSON（缓存命中不计数）——自检用，见 [DirScanCache.parseCount] */
    val characterParseCount: Int get() = charScan.parseCount

    fun listCharacters(): List<CharacterCard> {
        val byId = LinkedHashMap<String, CharacterCard>()
        charScan.read(charactersDir()).forEach { byId[it.id] = it }
        charCache.forEach { (id, c) -> byId[id] = c }
        return byId.values.filterNot { it.id in pendingCharDeletes }
            .map { resolveWorldBook(it) }
            .sortedByDescending { it.createdAt }
    }

    // ---------- 目录全量解析（第 64 轮：计时用）----------
    // 与缓存路径读的是同一批文件，但**不带任何缓存、不改任何全局状态**，
    // 所以可以安全地对着一个临时目录跑：量出来的就是这条读取链路"冷读一次"的代价
    // （＝搬到后台之前，界面线程每次进列表页要付的钱）。

    fun parseCharactersIn(dir: File): List<CharacterCard> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { decodeFile(it, CharacterCard.serializer()) }
            ?: emptyList()

    fun parseConversationsIn(dir: File): List<Conversation> =
        dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { decodeFile(it, Conversation.serializer()) }
            ?: emptyList()

    fun getCharacter(id: String): CharacterCard? {
        if (id in pendingCharDeletes) return null
        val raw = charCache[id] ?: run {
            val f = File(charactersDir(), "$id.json")
            if (!f.exists()) return null
            decodeFile(f, CharacterCard.serializer())?.also { charCache[id] = it } ?: return null
        }
        return resolveWorldBook(raw)
    }

    // ---------- 世界书（账号级实体，第 108 轮）----------
    // 书升为账号级：`worldbooks/<bookId>.json`，卡里只存 `worldBookId` 引用（多卡可共享一本）。
    // 读取路径统一在 [resolveWorldBook] 把引用解析回 `card.worldBook`——提示词装配 / 命中面板 /
    // 卡导出全都吃解析后的卡，不需要知道"引用"这回事；落盘时 [saveCharacter] 把内嵌正文剥掉
    // 只留引用，所以**书文件是唯一事实来源**（改一本共享的书，所有引用它的卡下一轮即生效）。

    private val bookMemoLock = Any()
    /** 书文件指纹缓存：路径 → (mtime+size 指纹, 书)。同一本书被多张卡解析时只读一次盘，改过即失效 */
    private val bookMemo = HashMap<String, Pair<Long, WorldBook?>>()

    private fun loadBook(f: File): WorldBook? {
        if (!f.exists()) return null
        val stamp = f.lastModified() + (f.length() shl 20)
        synchronized(bookMemoLock) {
            bookMemo[f.absolutePath]?.takeIf { it.first == stamp }?.let { return it.second }
        }
        val book = decodeFile(f, WorldBook.serializer())
        synchronized(bookMemoLock) { bookMemo[f.absolutePath] = stamp to book }
        return book
    }

    private fun worldbooksDir(): File =
        File(activeDir(), "worldbooks").apply { mkdirs() }

    /** bookId 会从卡文件 / 备份 JSON 里进来，挡一层路径穿越（id 只该是我们自己生成的 UUID） */
    private fun validBookId(id: String?): Boolean =
        !id.isNullOrBlank() && !id.contains('/') && !id.contains('\\') && !id.contains("..")

    /**
     * 把"引用"解析回"正文"，顺路做一次性迁移：
     * - 有引用 → 读到书文件就注入 [CharacterCard.worldBook]；书文件丢了但卡里还内嵌着正文
     *   （老备份恢复不全）→ 按原引用 id 自气回写，引用不断链；
     * - 没引用但内嵌着正文（老卡 / 刚导入的 ST 卡）→ **抽出成账号级书**并同步落盘
     *   （幂等：抽出后卡文件里内嵌被清掉，这条迁移路只走一次）。
     */
    private fun resolveWorldBook(c: CharacterCard): CharacterCard {
        val ref = c.worldBookId
        if (validBookId(ref)) {
            loadBook(File(worldbooksDir(), "$ref.json"))?.let { return c.copy(worldBook = it) }
            val embedded = c.worldBook?.takeIf { !it.isEmpty() } ?: return c
            runCatching {
                writeAtomically(File(worldbooksDir(), "$ref.json"), json.encodeToString(WorldBook.serializer(), embedded))
            }.onFailure { e -> WhaleLog.w(TAG, "世界书自愈失败：${e.message}") }
            return c
        }
        val book = c.worldBook?.takeIf { !it.isEmpty() } ?: return c
        val newId = newId()
        val extracted = runCatching {
            writeAtomically(File(worldbooksDir(), "$newId.json"), json.encodeToString(WorldBook.serializer(), book))
            val fixed = c.copy(worldBookId = newId, worldBook = null)
            writeAtomically(File(charactersDir(), "${c.id}.json"), json.encodeToString(CharacterCard.serializer(), fixed))
            charCache[c.id] = fixed
            true
        }.onFailure { e ->
            // 抽出失败就保持内嵌原样（下次读卡再试），不能因为迁移挡住读卡
            WhaleLog.w(TAG, "世界书抽出失败：${e.message}")
        }.getOrDefault(false)
        return if (extracted) c.copy(worldBookId = newId, worldBook = book) else c
    }

    fun getWorldBook(id: String): WorldBook? =
        if (validBookId(id)) loadBook(File(worldbooksDir(), "$id.json")) else null

    fun listWorldBooks(): List<WorldBookFile> =
        worldbooksDir().listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { f -> loadBook(f)?.let { WorldBookFile(f.name.removeSuffix(".json"), it) } }
            ?.sortedBy { it.book.name }
            ?: emptyList()

    fun saveWorldBook(id: String, book: WorldBook, onFailure: ((Throwable) -> Unit)? = null) {
        if (!validBookId(id)) return
        enqueueWrite("世界书 $id", onFailure) {
            writeAtomically(File(worldbooksDir(), "$id.json"), json.encodeToString(WorldBook.serializer(), book))
        }
    }

    /** 删书文件（不查引用——调用方先用 [worldBookReferringCards] 决定要不要删） */
    fun deleteWorldBook(id: String, onFailure: ((Throwable) -> Unit)? = null) {
        if (!validBookId(id)) return
        val f = File(worldbooksDir(), "$id.json")
        enqueueWrite("删除世界书 $id", onFailure) { f.delete() }
    }

    /** 恢复备份的"重建"语义用：清掉本账号全部书（书是数据不是缓存，不进 CLEARABLE_CACHE_DIRS） */
    fun clearWorldBooks() {
        val dir = worldbooksDir()
        enqueueWrite("清空世界书") {
            dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.delete() }
        }
    }

    /** 还有哪些卡引用这本书（[excludeCharacterId] 给"本卡正在解绑"的场景排除自己） */
    fun worldBookReferringCards(bookId: String, excludeCharacterId: String? = null): List<String> =
        listCharacters().filter { it.worldBookId == bookId && it.id != excludeCharacterId }
            .map { it.name.ifBlank { it.id } }

    /**
     * 存角色：**不阻塞调用线程**（P2 第 4 轮），加密无关、但角色 JSON 可能带着长人设与开场白，
     * 而保存动作发生在编辑页返回的路径上。
     *
     * 删除旧图的回收放到写入线程（要在新数据落盘之后才安全），替换前的值在**更新缓存之前**取。
     */
    fun saveCharacter(c: CharacterCard, onFailure: ((Throwable) -> Unit)? = null) {
        // 卡片带来的新类型自动进类型列表（导入的每条路都从这儿落库：发现页精选 / 文件 / 网址 /
        // 桌面拖入 / 备份恢复）。不登记的话，卡虽然进库了，按类型筛永远看不到它——2026-09-21 用户反馈。
        CategoryManager.ensure(c.categoriesOrDefault())
        val f = File(charactersDir(), "${c.id}.json")
        // 世界书独立化：卡里有引用就把内嵌正文剥掉——正文住书文件，留两份迟早打架
        // （改共享书后旧内嵌就是过期副本）。这里是所有卡落盘的唯一入口，剥在这里刚好；
        // 读回来时 resolveWorldBook 会把引用解析回正文，下游无感。
        val toStore = if (validBookId(c.worldBookId)) c.copy(worldBook = null) else c
        val prev = getCharacter(c.id)
        charCache[c.id] = toStore
        pendingCharDeletes.remove(c.id)
        enqueueWrite("角色 ${c.id}", onFailure) {
            writeAtomically(f, json.encodeToString(CharacterCard.serializer(), toStore))
            if (prev != null) {
                reclaimImages(
                    listOf(
                        if (prev.avatarUri != c.avatarUri) prev.avatarUri else null,
                        if (prev.backgroundUri != c.backgroundUri) prev.backgroundUri else null,
                        // 分端背景（A 批次）：漏了它就等于"换掉桌面端背景"永远不会回收旧图
                        if (prev.backgroundUriDesktop != c.backgroundUriDesktop) prev.backgroundUriDesktop else null
                    )
                )
            }
        }
    }

    /**
     * 删角色：连它的头像、角色背景、以及**它全部会话**的背景图与聊天图一起回收（P1-10）。
     * 引用集合在回收时按**内存缓存**算（已删的角色/会话都已不在里面），所以文件删除排队执行也安全。
     */
    fun deleteCharacter(id: String, onFailure: ((Throwable) -> Unit)? = null) {
        val old = getCharacter(id)
        val convs = listConversations(id)
        val candidates = buildList {
            add(old?.avatarUri)
            add(old?.backgroundUri)
            add(old?.backgroundUriDesktop)
            convs.forEach { c ->
                add(c.backgroundUri)
                add(c.backgroundUriDesktop)
                c.messages.forEach { m -> add(m.imageUri) }
            }
        }
        val charFile = File(charactersDir(), "$id.json")
        val convFiles = convs.map { File(conversationsDir(), "${it.id}.json") }
        // 先让缓存层"看不见"它们（列表、引用集合都按缓存走），再排队删文件
        pendingCharDeletes.add(id)
        charCache.remove(id)
        convs.forEach { pendingDeletes.add(it.id); convCache.remove(it.id) }
        enqueueWrite("删除角色 $id", onFailure) {
            charFile.delete()
            convFiles.forEach { it.delete() }
            // 文件真的删完才解除"隐藏"：期间列表不会显示它们，引用集合也不会把它们算成仍被引用
            pendingCharDeletes.remove(id)
            convs.forEach { pendingDeletes.remove(it.id) }
        }
        // 回收排队在删除之后：那时引用集合已把这一批排除在外，它们（且只有它们）会被删掉
        enqueueWrite("回收角色图片") { reclaimImages(candidates) }
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---------- 发现页精选（台账 12 第二批）----------
    // 精选卡在导入时每次都会拿到新本地 id（fromTavernJson 用 newId），所以"已导入"要
    // 记一份 manifest 卡 id → 本地角色 id 的映射（每账号一份 discover.json）。
    // 本地角色被删掉后映射仍在，但 getCharacter 返回 null ⇒ 下次点按自动重新下载导入。

    /** 读导入映射；文件不存在 / JSON 损坏都按空表处理（损坏不改名留现场——它只是个加速标记，丢了最多重新下载一次） */
    fun discoverImportMap(): Map<String, String> {
        val f = File(activeDir(), "discover.json")
        if (!f.exists()) return emptyMap()
        return runCatching {
            json.decodeFromString<Map<String, String>>(f.readText())
        }.getOrDefault(emptyMap())
    }

    /** 记一条导入映射（小文件，调用方在 IO 协程里调即可） */
    fun recordDiscoverImport(manifestCardId: String, characterId: String) {
        val f = File(activeDir(), "discover.json")
        val map = discoverImportMap() + (manifestCardId to characterId)
        writeAtomically(f, json.encodeToString(
            kotlinx.serialization.serializer<Map<String, String>>(), map
        ))
    }

    // ---------- 会话 ----------

    /**
     * 会话的**写穿缓存**（P1-B，2026-09-15）。
     *
     * 写从"同步落盘"改成"记内存 + 排队落盘"之后，"已排队但还没落盘"的改动也必须立刻能被读到 ——
     * 否则紧接着的读会拿到旧内容（重命名后列表还显示旧标题、后台摘要按旧内容覆盖掉期间的回复）。
     * 这里存的是**不可变的 `Conversation` 实例**（全项目都是 copy 出新对象，没有就地修改），
     * 所以共享同一份是安全的。
     */
    private val convCache = java.util.concurrent.ConcurrentHashMap<String, Conversation>()

    /**
     * 已排队但还没落盘的删除。
     *
     * 读与列表都要把它们当成"不存在"：否则删除后到落盘前的那几毫秒里，
     * 磁盘上还躺着的旧文件会让已删会话"复活"（列表里一闪、图片回收算成"仍被引用"）。
     */
    private val pendingDeletes: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /**
     * 串行写入通道（P1-B，2026-09-15）。
     *
     * **为什么必须串行**：一次写是「读回整份 → 改 → 原子替换」，两条写流并发会互相覆盖 ——
     * 流式回复在 IO 线程写静默检查点，主线程同时点「细腻」chip 或重命名，写的是同一个
     * `conversations/<id>.json`。串行通道 + 唯一临时名 + rename 三者一起，才能保证"最后一次改动赢"。
     *
     * **为什么用单线程 executor 而不是协程**：`Repository` 是 object、没有自己的作用域，
     * 而调用方遍布 UI 与后台任务；要求每个调用方自带 scope 更容易漏掉某一处。
     */
    private val writeQueue: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "whale-write") }

    private fun enqueueWrite(
        tag: String,
        onFailure: ((Throwable) -> Unit)? = null,
        onDone: ((Throwable?) -> Unit)? = null,
        block: () -> Unit
    ) {
        writeQueue.execute {
            val err = runCatching { block() }.onFailure {
                    WhaleLog.e(TAG, "写入失败（$tag）：${it.message}")
                onFailure?.invoke(it)
            }.exceptionOrNull()
            onDone?.invoke(err)
        }
    }

    /**
     * 会话文件扫描缓存（P2-A6，2026-09-15；第 64 轮把实现挪进 [DirScanCache]，与角色共用一份）。
     *
     * `listConversations` 被聊天页 5 处高频调用（每轮回复完成、切会话、删会话、改设置都要刷列表），
     * 而它原来每次都把账号目录下**所有**会话 JSON 全量解析一遍 —— 会话一多，这几处全是纯浪费。
     */
    private val convScan = DirScanCache { f -> decodeFile(f, Conversation.serializer()) }

    private fun readConversationsFromDisk(): List<Conversation> = convScan.read(conversationsDir())

    /**
     * 磁盘扫描结果叠加内存里的（较新）版本。
     *
     * 叠加是必要的：写是排队的，**已排队未落盘的新会话**在磁盘上还不存在，
     * 只按磁盘算的话刚建的会话要等落盘才出现在历史列表里。
     * 删除侧由 [pendingDeletes] 兜住，不会复活。
     */
    private fun allConversations(): List<Conversation> {
        val byId = LinkedHashMap<String, Conversation>()
        readConversationsFromDisk().forEach { byId[it.id] = it }
        convCache.forEach { (id, c) -> byId[id] = c }
        return byId.values.toList()
    }

    fun listConversations(characterId: String): List<Conversation> =
        allConversations()
            .filter { it.characterId == characterId && it.id !in pendingDeletes }
            .sortedByDescending { it.updatedAt }

    /** 所有角色的全部会话（用于全量备份） */
    fun listConversationsForAll(): List<Conversation> =
        allConversations().filterNot { it.id in pendingDeletes }

    fun getConversation(id: String): Conversation? {
        if (id in pendingDeletes) return null
        convCache[id]?.let { return it }
        val f = File(conversationsDir(), "$id.json")
        return if (f.exists()) decodeFile(f, Conversation.serializer())?.also { convCache[id] = it } else null
    }

    /**
     * 保存会话：**不阻塞调用线程**，落盘交给串行写入通道（P1-B）。
     *
     * [onFailure] 在**写入线程**上被调用。调用方若要据此更新界面状态，
     * 必须自己切回主线程（`ChatViewModel` 用 `viewModelScope.launch`）。
     */
    fun saveConversation(c: Conversation, onFailure: ((Throwable) -> Unit)? = null) {
        convCache[c.id] = c
        pendingDeletes.remove(c.id)
        // ⚠️ 目标文件在**排队时**就定下来：排队期间用户可能切换账号，
        // 那样 `conversationsDir()` 会指向新账号，这份数据就会写进别人的账号里。
        val target = File(conversationsDir(), "${c.id}.json")
        enqueueWrite("会话 ${c.id}", onFailure) {
            writeAtomically(target, json.encodeToString(Conversation.serializer(), c))
        }
    }

    /**
     * 删会话：连它的背景图与聊天消息图一起回收（P1-10）。
     * 会话背景可能是「角色默认背景」（那属于角色，会被引用检查保住），所以这里只按引用判断。
     *
     * 图片回收**紧接着在删除之前**算引用：那时会话已被记进 [pendingDeletes]，
     * 于是 `referencedImagePaths()` 不会再把这批图算成"仍被引用"，它们（且只有它们）会被回收。
     */
    fun deleteConversation(id: String) {
        val conv = getConversation(id)
        pendingDeletes.add(id)
        convCache.remove(id)
        // 同上：删除目标也在排队时定下来，免得排队期间切账号删错目录
        val target = File(conversationsDir(), "$id.json")
        enqueueWrite("删除会话 $id") {
            target.delete()
            pendingDeletes.remove(id)
        }
        reclaimImages(
            buildList {
                add(conv?.backgroundUri)
                add(conv?.backgroundUriDesktop)
                conv?.messages?.forEach { m -> add(m.imageUri) }
            }
        )
    }

    /**
     * 回收一批"刚失去引用"的本地图片（P1-10）。
     *
     * 关键点：**引用集合只算一次**（`referencedImagePaths()` 要遍历全部角色 + 全部会话），
     * 所以调用方必须成批传候选路径，不要每张图调一次。
     * 只删**本机文件路径**（[isLocalFilePath]，两端形态都认）；不在引用集合里的（＝已是孤儿）才会被删。
     *
     * P2 第 4 轮：整个动作放到写入线程上（它可能要遍历全部数据算引用集合、再删掉一批图）。
     * 顺序无关紧要——引用集合读的是**内存缓存**而不是文件，晚一点算反而更准（排在其间的改动都已生效）。
     * 只负责"排队"，不在调用线程上同步干活，所以在别的排队写任务内部调用它也是安全的。
     */
    fun reclaimImages(candidates: Collection<String?>) {
        val paths = candidates.mapNotNull { it?.takeIf(::isLocalFilePath) }.distinct()
        if (paths.isEmpty()) return
        enqueueWrite("回收图片") {
            val referenced = referencedImagePaths()
            paths.filterNot { it in referenced }.forEach { runCatching { File(it).delete() } }
        }
    }

    // ---------- 图片 ----------

    /**
     * 保存二进制图片，返回本地文件绝对路径。
     *
     * **刻意保持同步**：返回值马上会被拿去渲染（Coil 按路径加载），改成排队落盘会跟图片加载器
     * 抢时间——文件还没写出来就先被读，界面显示成空白。
     * 调用方基本都在 IO 线程上（生图、导入、备份恢复）。同理见 [saveFontBytes]。
     */
    fun saveImageBytes(bytes: ByteArray, extension: String): String {
        val name = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
        val f = File(imagesDir(), name)
        writeAtomically(f) { it.writeBytes(bytes) }
        return f.absolutePath
    }

    fun deleteImageFile(uri: String) {
        if (isLocalFilePath(uri)) {
            runCatching { File(uri).delete() }
        }
    }

    // ---------- 远程资源缓存（发现页封面/缩略图与清单，第 55 轮）----------

    /**
     * `cacheRoot` 下的一级子目录（不存在则建）。
     *
     * 为什么**不能**用 `images/`：那是"被角色卡引用的图片"，清理逻辑按引用集合回收孤立生图，
     * 发现页的封面缩略图放进去会被当作孤图删掉（10 分钟缓冲过后）。放 cacheRoot 下还有个好处：
     * 用户"清理缓存"能把它一并清掉——它本来就是缓存。
     */
    fun cacheSubDir(name: String): File = File(cacheRootDir, name).apply { mkdirs() }

    /** 原子写入缓存文件并返回；Coil 可能立刻按路径读它，写一半会被渲染成空白（同 [saveImageBytes]） */
    fun writeCacheBytes(dir: File, fileName: String, bytes: ByteArray): File {
        val f = File(dir, fileName)
        writeAtomically(f) { it.writeBytes(bytes) }
        return f
    }

    // ---------- 字体 ----------

    private fun fontsDir(): File =
        File(activeDir(), "fonts").apply { mkdirs() }

    /** 保存用户上传的字体文件，返回本地文件绝对路径 */
    fun saveFontBytes(bytes: ByteArray, extension: String): String {
        val ext = extension.trimStart('.').ifBlank { "ttf" }
        val name = "font_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        val f = File(fontsDir(), name)
        writeAtomically(f) { it.writeBytes(bytes) }
        return f.absolutePath
    }

    fun deleteFontFile(path: String) {
        if (isLocalFilePath(path)) {
            runCatching { File(path).delete() }
        }
    }

    // ---------- 缓存统计与清理（问题 #21；2026-09-15 改成"先列明细再清"）----------

    /**
     * 可清理内容的明细。
     *
     * 原来只报一个总字节数，用户根本不知道要删的是什么 —— 而其中**孤立生图的删除不可逆**，
     * 所以先把三类内容分开报出来，再让用户确认。
     */
    data class CacheBreakdown(
        /** `cacheDir` 下**除 tts 外**的文件（应用主动写入的有导出的备份 JSON、语音输入录音、导出的卡，内含 base64 图片） */
        val cacheDirBytes: Long,
        /** `tts/` 语音朗读缓存（第 44 轮用户拍板：一键清理**保留**它，否则同句朗读要重新付费合成） */
        val ttsBytes: Long,
        /** 没被任何角色/会话/账号引用、且已过 10 分钟缓冲的生图 */
        val orphanImages: List<File>,
        /** 更新包残留：下载中断的半成品（.part）与更早的完整包 */
        val staleApks: List<File>
    ) {
        val orphanBytes: Long get() = orphanImages.sumOf { it.length() }
        val apkBytes: Long get() = staleApks.sumOf { it.length() }
        /** 可清理合计——**不含 tts**（tts 保留，见上） */
        val totalBytes: Long get() = cacheDirBytes + orphanBytes + apkBytes
    }

    fun cacheBreakdown(): CacheBreakdown {
        val b = cacheBreakdownIn(cacheRootDir)
        val cacheDirBytes = b.cacheDirBytes
        // 只清「早就没人引用」的孤立生图：给刚生成、还没点应用的图留 10 分钟缓冲，别误删
        val safeBefore = System.currentTimeMillis() - 10 * 60 * 1000L
        val referenced = referencedImagePaths()
        val orphans = imagesDir().listFiles()
            ?.filter { it.isFile && it.absolutePath !in referenced && it.lastModified() < safeBefore }
            .orEmpty()
        // 更新包残留（原来完全没人管，装几次就堆几十 MB）：
        // 半成品一律可清；完整包只留最新那一份 —— 免得删掉"刚下好、正要装"的包
        val apkDir = File(filesRootDir, "apk")
        val parts = apkDir.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }.orEmpty()
        val olderCompletes = apkDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && it.lastModified() < safeBefore }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(1)
            .orEmpty()
        return CacheBreakdown(cacheDirBytes, b.ttsBytes, orphans, parts + olderCompletes)
    }

    /** 可清理的缓存总量（兼容旧调用） */
    fun cacheBytes(): Long = cacheBreakdown().totalBytes

    /** 清一遍并把结果目录建回来，返回实际释放的字节数 */
    fun clearCaches(): Long {
        val breakdown = cacheBreakdown()
        val freed = clearCachesIn(cacheRootDir) +
            breakdown.orphanImages.sumOf { f -> if (runCatching { f.delete() }.getOrDefault(false)) f.length() else 0L } +
            breakdown.staleApks.sumOf { f -> if (runCatching { f.delete() }.getOrDefault(false)) f.length() else 0L }
        runCatching { File(cacheRootDir, "backups").mkdirs() }
        return freed
    }

    /**
     * 缓存占用统计（`tts` 与其余**分开两笔**，界面按两笔显示）。
     * 与 [clearCachesIn] 共用同一份白名单 [CLEARABLE_CACHE_DIRS] —— 两边口径必须一致，
     * 否则会出现"显示可清理 3 GB（其实是数据）"这种更吓人的谎报。纯目录操作、不碰 Repository 状态。
     */
    fun cacheBreakdownIn(cacheRoot: File): CacheBreakdown {
        var tts = 0L
        var other = 0L
        CLEARABLE_CACHE_DIRS.forEach { name ->
            File(cacheRoot, name).takeIf { it.isDirectory }?.walkTopDown()
                ?.filter { it.isFile }?.forEach { other += it.length() }
        }
        File(cacheRoot, "tts").takeIf { it.isDirectory }?.walkTopDown()
            ?.filter { it.isFile }?.forEach { tts += it.length() }
        return CacheBreakdown(other, tts, emptyList(), emptyList())
    }

    /**
     * 第 44 轮（用户拍板）：一键清理＝清缓存，**`tts/` 整棵保留**（删它不影响正确性，但同句重听要重新付费合成）。
     *
     * ⚠ **2026-09-21 第 63 轮改成白名单**（用户实测：桌面端「清除缓存」把角色卡也清了，数据丢失 P0）：
     * 旧口径是"**缓存根下除 `tts/` 之外的文件全删**"，它默认了"缓存根里只有缓存"。
     * 但桌面端的数据目录与缓存目录**都能由用户自己指定**（引导页两个输入框，只校验"不许设在安装目录里"），
     * 用户把两者填成同一个目录（很自然的做法：都放 `D:\鲸鱼数据`）之后，**数据树 `roleplay/` 就落在缓存根里面**
     * ⇒ 点一下「清除缓存」，角色卡 / 会话 / 图片 / 各账号 settings 全部消失（`File.delete()` 不进回收站）。
     * 同一个"全删"口径还会波及 `<缓存根>/backups`——那是「数据备份」导出的 JSON，**也是用户数据**——
     * 以及用户自己放在该目录下的任何文件。
     *
     * 现在只清 [CLEARABLE_CACHE_DIRS] 里那几个**应用自己生成、随时可重建**的目录；
     * 统计口径 [cacheBreakdownIn] 用同一份白名单，保证"显示多少就清掉多少"。
     * 白名单之外的任何东西（尤其数据树、backups/、以及别人放进来的文件）一概不碰。
     */
    fun clearCachesIn(cacheRoot: File): Long {
        var freed = 0L
        CLEARABLE_CACHE_DIRS.forEach { name ->
            val dir = File(cacheRoot, name)
            if (!dir.isDirectory) return@forEach
            // 只删文件、保留目录本身（与原口径一致，省得下次用到时还要 mkdirs）
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) freed += len
            }
        }
        return freed
    }

    /**
     * 缓存根下**属于应用、随时可重建**的目录白名单——统计与清理共用这一份。
     *
     * 为什么是白名单而不是"除 tts 全删"：见 [clearCachesIn] 的说明（那条 P0）。
     * 注：`tts/` 不在表里＝按第 44 轮的口径**保留**；`backups/` 也不在＝那是用户的导出备份，不能当缓存清。
     */
    private val CLEARABLE_CACHE_DIRS = listOf(
        "bgm",          // 内置环境音的素材缓存（打包资源里有一份，用到时重新解出来）
        "card-export",  // 角色卡竖图导出（随时可重画）
        "discover",     // 发现页清单与封面缩略图
        "tools",        // 从 jar 里解出的 PowerShell 脚本（下次用到会重新解）
        "update",       // 下载的更新包与暂存目录（需要时重新下）
        "voice-input"   // 录音识别用的临时 WAV
    )

    /**
     * 被引用到的图片（当前账号的全部角色 + 会话 + 账号头像）。
     * 纯逻辑部分在 [collectReferencedImagePaths]——**抽出去是为了让桌面自检能直接验**
     * （它算错的后果是"用户的图片被当成孤儿删掉"，而这件事在界面上表现为"图片凭空消失"，
     * 只有自检拦得住；第 59 轮它确实算错过：判据是 Android 专属的 `startsWith("/")`）。
     */
    private fun referencedImagePaths(): Set<String> =
        collectReferencedImagePaths(listCharacters(), listConversationsForAll(), listProfiles())

    // ---------- 本地账号 ----------

    private fun profilesFile(): File = File(root, "profiles.json")
    private fun currentAccountFile(): File = File(root, "current_account.txt")

    fun listProfiles(): List<Profile> {
        val f = profilesFile()
        return if (f.exists()) {
            decodeFile(f, ProfileList.serializer())?.profiles ?: emptyList()
        } else emptyList()
    }

    fun saveProfiles(list: List<Profile>) {
        writeAtomically(profilesFile(), json.encodeToString(ProfileList.serializer(), ProfileList(list)))
    }

    fun currentAccountId(): String =
        currentAccountFile().takeIf { it.exists() }?.readText()?.trim().orEmpty()

    /**
     * 切换当前账号。
     *
     * ⚠️ 这个写**必须同步**：`activeDir()` 靠它决定数据目录，如果排队，
     * 紧随其后的读会拿到旧账号的数据。文件只有 36 字节，不值得为它引入这个风险。
     * 但会话的写穿缓存**必须清掉** —— 否则切号后 `listConversations` 会把上一个账号的会话混进新列表。
     */
    fun setCurrentAccount(id: String) {
        writeAtomically(currentAccountFile(), id)
        // 缓存全部按账号隔离：不清的话切号后会读到上一个账号的会话/角色
        convCache.clear()
        pendingDeletes.clear()
        charCache.clear()
        pendingCharDeletes.clear()
    }

    fun currentProfile(): Profile? =
        listProfiles().firstOrNull { it.id == currentAccountId() }

    /** 创建账号并设为当前账号 */
    fun createAccount(nickname: String, account: String, password: String): Profile {
        val acc = account.trim()
        val name = nickname.trim().ifBlank { acc }
        if (acc.isBlank()) throw IllegalArgumentException("账号不能为空")
        if (password.isBlank()) throw IllegalArgumentException("密码不能为空")
        if (listProfiles().any { it.account.equals(acc, ignoreCase = true) }) {
            throw IllegalArgumentException("该账号已存在")
        }
        val p = Profile(id = newId(), nickname = name, account = acc, passwordHash = Security.hashPassword(password))
        saveProfiles(listProfiles() + p)
        setCurrentAccount(p.id)
        return p
    }

    fun login(account: String, password: String): Profile {
        val acc = account.trim()
        if (acc.isBlank()) throw IllegalArgumentException("账号不能为空")
        if (password.isBlank()) throw IllegalArgumentException("密码不能为空")
        val p = listProfiles().firstOrNull { it.account.equals(acc, ignoreCase = true) }
            // 用户 2026-09-15 反馈：拿昵称当账号登录，看到"账号不存在"以为账号丢了。
            // 本机账号名不是秘密（profiles.json 明文可读），直接列出来省得反复猜。
            ?: throw IllegalArgumentException(
                listProfiles().takeIf { it.isNotEmpty() }
                    ?.let { "账号不存在。本机已有账号：" + it.joinToString("、") { pr -> pr.account } }
                    ?: "账号不存在（本机还没有任何账号，请先注册）"
            )
        if (!Security.verifyPassword(password, p.passwordHash)) throw IllegalArgumentException("密码错误")
        // 旧的无盐 SHA-256 哈希迁移到带盐 PBKDF2
        if (!p.passwordHash.startsWith("pbkdf2$")) {
            updateProfile(p.copy(passwordHash = Security.hashPassword(password)))
        }
        setCurrentAccount(p.id)
        return p
    }

    fun updateProfile(p: Profile) {
        saveProfiles(listProfiles().map { if (it.id == p.id) p else it })
    }

    /**
     * 删除账号。
     *
     * P2-E8（2026-09-15）：原来只删 `profiles.json` 里的记录与 current 指针，
     * **`accounts/<id>/` 整个目录（角色、会话、图片、设定）原封不动留在磁盘上**——
     * 用户以为"删掉这个账号了"，其实数据还在本机、还能被恢复工具翻出来。
     * 现在连同目录一起删；删目录失败（占用等）也不阻塞账号删除，返回是否彻底清干净。
     */
    fun deleteProfile(id: String): Boolean {
        val dir = File(accountsDir(), id)
        val purged = runCatching {
            dir.walkBottomUp().forEach { it.delete() }
            !dir.exists()
        }.getOrDefault(false)
        saveProfiles(listProfiles().filter { it.id != id })
        if (currentAccountId() == id) setCurrentAccount("")
        return purged
    }

    // ---------- 用户预设设定 ----------

    fun listUserSettings(): List<UserSetting> {
        val f = File(activeDir(), "user_settings.json")
        return if (f.exists()) {
            decodeFile(f, UserSettingList.serializer())?.settings ?: emptyList()
        } else emptyList()
    }

    fun saveUserSettings(list: List<UserSetting>) {
        writeAtomically(File(activeDir(), "user_settings.json"), json.encodeToString(UserSettingList.serializer(), UserSettingList(list)))
    }

    fun getUserSetting(id: String): UserSetting? =
        listUserSettings().firstOrNull { it.id == id }

    /** 当前默认设定（settings.defaultUserSettingId 对应） */
    fun defaultUserSetting(): UserSetting? =
        getUserSetting(Repository.loadSettings().defaultUserSettingId)

    fun addUserSetting(name: String, content: String): UserSetting {
        val s = UserSetting(id = newId(), name = name.trim().ifBlank { "我的设定" }, content = content.trim())
        saveUserSettings(listUserSettings() + s)
        return s
    }

    fun updateUserSetting(u: UserSetting) {
        saveUserSettings(listUserSettings().map { if (it.id == u.id) u else it })
    }

    fun deleteUserSetting(id: String) {
        saveUserSettings(listUserSettings().filter { it.id != id })
        if (loadSettings().defaultUserSettingId == id) {
            saveSettings(loadSettings().copy(defaultUserSettingId = ""))
        }
    }

    fun setDefaultUserSetting(id: String) {
        saveSettings(loadSettings().copy(defaultUserSettingId = id))
    }

    // ---------- 用户自建模板 ----------

    private fun templatesFile(): File = File(activeDir(), "templates.json")

    fun listCustomTemplates(): List<CustomTemplate> {
        val f = templatesFile()
        return if (f.exists()) {
            decodeFile(f, CustomTemplateList.serializer())?.templates ?: emptyList()
        } else emptyList()
    }

    private fun saveTemplates(list: List<CustomTemplate>) {
        writeAtomically(templatesFile(), json.encodeToString(CustomTemplateList.serializer(), CustomTemplateList(list)))
    }

    fun saveCustomTemplate(t: CustomTemplate) {
        saveTemplates(listCustomTemplates() + t)
    }

    fun deleteCustomTemplate(id: String) {
        saveTemplates(listCustomTemplates().filter { it.id != id })
    }
}
