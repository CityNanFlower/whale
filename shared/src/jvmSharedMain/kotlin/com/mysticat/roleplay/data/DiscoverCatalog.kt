package com.mysticat.roleplay.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 发现页：零账号、零自建服务器的内容进站口。
 *
 * 精选包——清单 + 卡文件都托管在 GitHub Release 附件（零自建服务器），
 * App 打开发现页拉最新清单即见效，内容更新不用发版。
 *
 * **接上 GitHub 镜像**。用户报"发现页刷新不出来（网络错误）"——`github.com` 被中间拦截，
 * 直连在 OkHttp / Android 上都过不了证书校验（`RemoteFetch` 里有取证）。于是清单与卡文件都按
 * **镜像优先 + 原始地址兜底**展开，与"检查更新"共用同一份镜像前缀；路径校验（[cardUrls]）对
 * 每个候选都生效，镜像不能把清单指向别处。
 *
 * 清单多两个**可选**字段——包封面 `cover` 与卡缩略图 `thumb`（老清单没有这两个键，
 * 有默认值、UI 缺图就退回纯文字形态）。这两张小图拉到就**落盘缓存**（[cachedImagePath]），
 * 卡片区反复重组不再走网络；清单本身也存一份，镜像全挂时用上一份渲染并标"离线缓存"
 * （[Featured.fromCache]）——这是对"公共镜像哪天挂了"最直接的一层缓冲。
 *
 * 清单再添两个**可选**字段 `kind`（类型：角色 / 世界书 / 故事，缺省＝角色）与 `form`（形态），
 * 发现页因此多一条"按类型发现精选"的轴（[packsForKind]，角色那一类再按形态分四张卡）；
 * 主题轴原样不动，仍是默认那一轴。
 */
object DiscoverCatalog {

    /**
     * 精选内容托管地址（2026-09-24 起与 version.json 一起收编进公开源码仓 `whale`）：直接取仓里的
     * `dist/discover/` 目录——清单 `discover-manifest.json` 与扁平化命名的 card-*.png / cover-*.jpg /
     * thumb-*.jpg 都在这一层（路径全 ASCII）。换内容 = 改仓里这些文件，App 下次拉清单即生效，
     * 不再需要往 Release 传附件。
     */
    private const val RELEASE_URL =
        "https://raw.githubusercontent.com/CityNanFlower/whale/main/dist/discover/"

    private const val MANIFEST_FILE = "discover-manifest.json"

    /** 清单候选源（镜像优先 + 原始兜底） */
    private val MANIFEST_URLS = RemoteFetch.githubCandidates(RELEASE_URL + MANIFEST_FILE)

    /** 清单文件较小，单独放宽超时没必要；解析失败按"清单格式不对"报 */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析清单文本。抽成公开函数是为了让**桌面自检**能直接验两件事：
     * ① 新版清单（带 cover/thumb）解析得出；② **老清单**（没有这两个键）照样能读——
     * 清单是热更新的，谁都不知道用户手里那台 App 会先看到哪一版清单。
     */
    fun parseManifest(text: String): DiscoverManifest = try {
        json.decodeFromString(DiscoverManifest.serializer(), text)
    } catch (t: Throwable) {
        throw RemoteFetch.FetchException("精选清单格式不对（内容可能更新到一半，稍后重试）", t)
    }

    @Serializable
    data class DiscoverManifest(
        val version: Int = 1,
        val updated: String = "",
        val packs: List<DiscoverPack> = emptyList()
    )

    @Serializable
    data class DiscoverPack(
        val id: String,
        val title: String,
        val tagline: String = "",
        /** 封面横幅文件名（空 = 这个包没有封面，UI 只显示标题） */
        val cover: String = "",
        val cards: List<DiscoverCardEntry> = emptyList()
    )

    /** file 是相对 RELEASE_PATH 的文件名（如 card-xxx.png），不允许绝对 URL（清单指向谁由我们说了算） */
    @Serializable
    data class DiscoverCardEntry(
        val id: String,
        val name: String,
        val tagline: String = "",
        val file: String,
        /** 缩略图文件名（空 = 没有缩略图，tile 退回纯文字） */
        val thumb: String = "",
        /**
         * 类型：角色 / 故事 / 模板。**缺省＝角色**——早先发出的清单一条都没写它，
         * 那些内容确实全是角色卡，于是"缺省"正好等于事实，用不着改历史附件。
         */
        val kind: String = "",
        /**
         * 形态：""=陪伴 / "experience"=多线 / "play"=玩法 / "tool"=工具（见 [Engines]）。
         * 主题包里混玩法/工具卡时，tile 上要能一眼看出它是哪一档。
         */
        val form: String = ""
    )

    /** 清单列表 + 它是不是"本地缓存兜底"来的 */
    data class Featured(val packs: List<DiscoverPack>, val fromCache: Boolean)

    /**
     * 清单里指向的卡文件的**全部候选 URL**（镜像优先 + 原始兜底）；
     * 相对路径越界（http 开头 / 带 .. / 以 / 开头）一律拒绝。
     */
    fun cardUrls(entry: DiscoverCardEntry): List<String> = fileUrls(entry.file, "卡文件")

    /** 封面：清单没写就是没有，返回空列表让调用方跳过 */
    fun coverUrls(pack: DiscoverPack): List<String> =
        if (pack.cover.isBlank()) emptyList() else fileUrls(pack.cover, "封面文件")

    /** 卡缩略图：同上 */
    fun thumbUrls(entry: DiscoverCardEntry): List<String> =
        if (entry.thumb.isBlank()) emptyList() else fileUrls(entry.thumb, "缩略图")

    // ── 类型轴─────────────────────────────────────────
    //
    // 用户 2026-09-23 口径：发现页**默认按主题推荐**（[DiscoverPack] 原样，不动），但也支持
    // **按类型发现精选**：角色 / 世界书 / 故事（2026-10-14 由「角色 / 故事 / 模板」改来——
    // 「模板」一直是空组、随这次一并收掉，世界书成为可发现的内容）。空组照样返回，界面才有地方
    // 说明"筹备中"。
    //
    // ⚠ **2026-10-04 撤过一次「皮肤」第四类**：那一版打算把那 5 套自有皮肤做成 `.whale-skin.json`
    // 放清单里让用户下载，但实测**用不着**——它们是我们自己的皮肤，直接进 `WhaleSkin.builtins()`
    // 就行（离线可用、跟着版本走、不用写映射脚本、不用先下载）。
    // 发现页那条链的价值是**分发别人的东西**，不是分发我们自己的。等真有用户投稿了再把这一类加回来，
    // 届时一并补上"皮肤是下载即套上、不进库"的导入分流（`DiscoverScreen` 的 `importCard` 入口）。

    const val KIND_CHARACTER = "角色"
    const val KIND_WORLDBOOK = "世界书"
    const val KIND_STORY = "故事"

    /** 类型在界面上的顺序（发现页顶部那一排 chip：主题 / 角色 / 世界书 / 故事 / 皮肤，头尾两颗不在这里） */
    val kinds: List<String> = listOf(KIND_CHARACTER, KIND_WORLDBOOK, KIND_STORY)

    /** 类型轴合成包的 id（界面按类型取组时用它——「kind-」这个前缀只在这一个地方出现） */
    fun kindPackId(kind: String): String = "kind-$kind"

    /** 条目属于哪一类内容；清单没写一律当角色（老清单兼容，与 [DiscoverCardEntry.kind] 的说明同源） */
    fun kindOf(entry: DiscoverCardEntry): String = entry.kind.ifBlank { KIND_CHARACTER }

    /**
     * 这条是不是**世界书**条目——它的 `file` 指向一份世界书 JSON（不是角色卡 PNG），
     * 导入走"下载 → 解析 → 落书库"那条路（见 [DiscoverScreen] 的 `downloadWorldBook`），
     * 与角色卡的 `CardImport` 完全不同。清单热更新，`kind` 缺省一律当角色，所以只有明确写了
     * `kind="世界书"` 的条目才走这条路。
     */
    fun isWorldBook(entry: DiscoverCardEntry): Boolean = kindOf(entry) == KIND_WORLDBOOK

    /** 角色按形态分的合成包 id（形态轴只在「角色」那一类里出现；formTag 空＝陪伴） */
    fun formPackId(formTag: String): String = "form-$formTag"

    /**
     * 「无主题」组：题材归属不明确的通用卡（起名先生、校对、二十问…）放在一个 `misc-` 前缀的包里
     * （用户 2026-10-14 口径）。它们**只按类型出现**——角色 / 世界书 / 故事各归各档（[packsForKind]
     * 照常把它们摊进类型轴）；**不进「主题」轴**（界面按 [isMiscPack] 过滤，主题轴只列真正的题材包）。
     */
    const val MISC_PACK_PREFIX = "misc-"

    fun isMiscPack(packId: String): Boolean = packId.startsWith(MISC_PACK_PREFIX)

    /**
     * 类型轴分组。把主题包里的卡按 [kindOf] 摊平成合成包，交给同一套渲染与导入链路
     * （[DiscoverScreen] 的 `PackCoverCard` / `importPack`）——于是"全部导入"对每一类都自然成立，
     * 不必为类型轴另写一份导入。
     *
     * **「角色」这一类再按形态分四张卡**（用户 2026-10-14 口径：陪伴 / 多线 / 玩法 / 工具各一张），
     * 所以它返回的是四个包而不是一个；其余类型（世界书 / 故事）仍是一整组。
     * 空的形态组照样返回——界面才有地方说明"这一档还没有内容"。
     *
     * 跨主题包出现两次的同一张卡只留第一张：主题轴允许把一张卡摆进多个主题（校园 / 恋爱都能摆它），
     * 类型轴再列两遍就成了重复项。
     */
    fun packsForKind(packs: List<DiscoverPack>, kind: String): List<DiscoverPack> {
        val all = packs.flatMap { it.cards }.filter { kindOf(it) == kind }.distinctBy { it.id }
        if (kind != KIND_CHARACTER) {
            return listOf(DiscoverPack(id = kindPackId(kind), title = "${kind}精选", cards = all))
        }
        // 形态顺序＝[Engines.all] 的注册顺序（陪伴 / 多线 / 玩法 / 工具），与编辑器里的形态标签一致
        return Engines.all.map { spec ->
            DiscoverPack(
                id = formPackId(spec.formTag),
                title = spec.label,
                cards = all.filter { it.form == spec.formTag }
            )
        }
    }

    /**
     * 相对文件名 → 全部候选 URL。三类文件（卡 / 封面 / 缩略图）共用同一套越界校验——
     * 镜像只能"换个前缀"，不能借清单把 App 指到别处去。
     */
    private fun fileUrls(name: String, what: String): List<String> {
        val f = name.trim()
        require(f.isNotEmpty() && !f.startsWith("http") && !f.contains("..") && !f.startsWith("/")) {
            "清单里的${what}路径不合法"
        }
        return RemoteFetch.githubCandidates(RELEASE_URL + f)
    }

    // ── 小图与清单的本地缓存 ─────────────────────────────────────

    /**
     * 缓存目录：`cacheRoot/discover/`。**刻意不放进 `images/`**——那里是"被角色卡引用的图片"，
     * 清理逻辑按引用集合回收孤图，封面缩略图放进去会被当成孤图删掉；放 cacheRoot 下则
     * 用户"清理缓存"能一并清掉（它就是缓存）。
     */
    private fun cacheDir(): File = Repository.cacheSubDir("discover")

    /**
     * 封面 / 缩略图的本地路径：命中缓存直接返回（**离线也出图**），否则按候选源拉一次再落盘。
     * 失败返回 null——一张小图拉不到不该影响清单、更不该影响导入，UI 只是不显示它。
     */
    suspend fun cachedImagePath(fileName: String, urls: List<String>): String? =
        withContext(Dispatchers.IO) {
            if (urls.isEmpty() || fileName.isBlank()) return@withContext null
            val cached = File(cacheDir(), fileName)
            if (cached.isFile && cached.length() > 0) return@withContext cached.absolutePath
            runCatching {
                Repository.writeCacheBytes(cacheDir(), fileName, RemoteFetch.fetchBytes(urls))
            }.getOrNull()?.absolutePath
        }

    /**
     * 拉取并解析精选清单（镜像优先、原始地址兜底）。清单失败不影响发现页其余功能（UI 有降级文案 + 重试）。
     *
     * 加一层兜底：全部候选源都挂时，退回**上次成功拉到的那份**（存在缓存里），
     * 用 [Featured.fromCache] 告诉 UI 该说明什么——否则用户看到的只是一句"加载失败"，明明之前是好的。
     */
    suspend fun fetchFeatured(): Featured = withContext(Dispatchers.IO) {
        val bytes = try {
            RemoteFetch.fetchBytes(MANIFEST_URLS)
        } catch (e: RemoteFetch.FetchException) {
            cachedPacks()?.let { return@withContext Featured(it, fromCache = true) }
            // 连缓存都没有：把"都试过了"写进文案，免得用户以为是单个地址写错
            throw RemoteFetch.FetchException("镜像与直连都没通 · ${e.message}", e)
        }
        val manifest = parseManifest(bytes.toString(Charsets.UTF_8))
        // 存一份给下次兜底；写失败不影响本次结果
        runCatching { Repository.writeCacheBytes(cacheDir(), MANIFEST_FILE, bytes) }
        Featured(manifest.packs, fromCache = false)
    }

    /** 上次成功拉到的清单（空/损坏都当没有——它只是缓冲，不该让页面变成"有内容但其实是坏的"） */
    private fun cachedPacks(): List<DiscoverPack>? {
        val f = File(cacheDir(), MANIFEST_FILE)
        if (!f.isFile) return null
        return runCatching {
            parseManifest(f.readText()).packs
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * 清掉封面/缩略图缓存（**清单缓存留着**，它才是离线兜底的那份）。
     * 用户手点刷新时调用：内容更新是覆盖同名附件，不清就一直显示旧图。
     */
    suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        cacheDir().listFiles()?.forEach { f ->
            if (f.name != MANIFEST_FILE) runCatching { f.delete() }
        }
        Unit
    }
}
