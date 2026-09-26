package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.DiscoverCatalog
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.RemoteFetch
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.DesktopRailRow
import com.mysticat.roleplay.ui.DesktopRailSectionLabel
import com.mysticat.roleplay.ui.wheelHorizontalScroll
import com.mysticat.roleplay.ui.ListItemCard
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.noArgViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发现页：精选包。手机端整页（自带顶栏）/ 桌面三栏第三栏（embedded=true）。
 * 精选：打开页面拉 GitHub Release 上的清单（直连不通自动换镜像）→ 列表页封面卡 → 详情页卡列表 →
 * **点卡片弹简介、卡片右侧那颗键才是导入**（用户 2026-09-23「点模板的卡片应该弹出简介，
 * 在卡片右侧添加导入键」）。⚠ 这里推翻了 09-20 那版口径（"点卡即导入落卡＋直接开新会话；
 * 不做试聊不落卡"）：卡片多了以后，点一下就被下载＋推进聊天页，反而没机会先看清这是张什么卡。
 *
 * **网址导入 / 文件导入**：按用户口径挪到「角色卡 → 添加角色卡」，
 * 本页不再放这两个入口（本页只做"挑内容"，做卡入口统一在角色卡页）。
 *
 * **包封面横幅** + 卡缩略图（清单里的 `cover` / `thumb`，拉到就落盘缓存）、
 * 清单离线兜底时明确告知"显示的是上次缓存的内容"。装饰图拉不到一律退回纯文字形态，
 * 不挡清单与导入——发现页的可用性不该由一张图决定。
 */
class DiscoverViewModel : ViewModel() {
    var notice by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** 精选清单；null = 还在加载（首帧不挡发现页其余功能） */
    var packs by mutableStateOf<List<DiscoverCatalog.DiscoverPack>?>(null)
        private set
    var manifestError by mutableStateOf<String?>(null)
        private set

    /** 清单是"本地缓存兜底"来的（镜像与直连都挂了）：页面要说清楚，否则用户以为这就是最新的 */
    var manifestFromCache by mutableStateOf(false)
        private set

    /** 封面 / 缩略图：清单里的文件名 → 本地缓存路径。拉不到就没有这个键，UI 退回纯文字形态 */
    var images by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** 正在下载导入的精选卡（清单 id），tile 上据此显示进度并禁点 */
    var busyCardId by mutableStateOf<String?>(null)
        private set
    var importingPack by mutableStateOf(false)
        private set

    /**
     * 正在看简介的那张卡：点卡片＝**先看简介**，不再是"点一下就直接下载导入并推开聊页"。
     * 导入交给卡片右侧那颗「导入」键，或弹窗里的按钮。
     */
    var introEntry by mutableStateOf<DiscoverCatalog.DiscoverCardEntry?>(null)
        private set

    fun openIntro(entry: DiscoverCatalog.DiscoverCardEntry) {
        introEntry = entry
    }

    fun closeIntro() {
        introEntry = null
    }

    /** 视图轴：默认「主题」＝按主题推荐；点「角色 / 故事 / 模板」＝按类型发现精选 */
    var scope by mutableStateOf(ScopeTheme)
        private set

    /**
     * 当前轴上要列的"包"。主题轴＝清单原样；类型轴＝[DiscoverCatalog.packsByKind] 里选中那一类。
     * 两条轴共用同一套渲染与导入（含"全部导入"），所以类型轴不必另写一份导入链路。
     */
    val groups: List<DiscoverCatalog.DiscoverPack>
        get() = if (scope == ScopeTheme) {
            packs.orEmpty()
        } else {
            DiscoverCatalog.packsByKind(packs.orEmpty())
                .filter { it.id == DiscoverCatalog.kindPackId(scope) }
        }

    /** 切视图轴：不清已加载的清单，也不打断正在进行的导入 */
    fun switchScope(next: String) {
        scope = next
        // 顺手退出详情页：另一个轴上没有这个包，留在详情里就是一张指向空气的页面
        openedPackId = null
    }

    /**
     * 排列：**竖排＝默认**（用户 2026-09-23：卡片条横着翻"太不舒服了"）；
     * 横排＝原来那个形式，照用户"也支持现在这个形式"保留。切它只影响详情页怎么列卡，
     * 不动内容、不动导入（两种排列点下去都是同一个开聊动作）。
     */
    var stackMode by mutableStateOf(StackVertical)
        private set

    /** 已经点进去的那个包（null＝还在列表页）。主题轴与类型轴共用同一个详情页 */
    var openedPackId by mutableStateOf<String?>(null)
        private set

    /** 详情页要显示的包；切过轴之后它可能已不在当前轴上——那就当没打开过（[openedPack] 返回 null） */
    val openedPack: DiscoverCatalog.DiscoverPack?
        get() = openedPackId?.let { id -> groups.firstOrNull { it.id == id } }

    fun openPack(pack: DiscoverCatalog.DiscoverPack) {
        openedPackId = pack.id
    }

    fun closePack() {
        openedPackId = null
    }

    fun switchStack(next: String) {
        stackMode = next
    }

    /** manifest 卡 id → 本地角色 id（Repository 里还有落盘的一份，切账号自然隔离） */
    var importedMap by mutableStateOf(Repository.discoverImportMap())
        private set

    /** 首次进入拉清单；失败后可重试（重试会把状态拨回"加载中"） */
    fun loadFeatured() = loadFeatured(force = false)

    /**
     * 拉清单 + 小图。[force] = 用户手点刷新：**先清掉封面/缩略图缓存**——内容更新是覆盖同名附件，
     * 不清缓存的话"刷新"只会换来同一批旧图（清单本身也重新拉，不走缓存兜底）。
     */
    private fun loadFeatured(force: Boolean) {
        if (!force && packs != null && manifestError == null) return
        packs = null
        manifestError = null
        manifestFromCache = false
        if (force) images = emptyMap()
        viewModelScope.launch {
            if (force) DiscoverCatalog.clearImageCache()
            try {
                val featured = DiscoverCatalog.fetchFeatured()
                packs = featured.packs
                manifestFromCache = featured.fromCache
                loadImages(featured.packs)
            } catch (t: Throwable) {
                manifestError = t.message ?: "网络错误"
            }
        }
    }

    fun refreshFeatured() = loadFeatured(force = true)

    /**
     * 封面与缩略图：清单落地后并发拉一批小图，命中缓存的不再走网络。
     * 单张失败只跳过它自己——发现页的可用性不该被一张装饰图绑住。
     */
    private fun loadImages(packs: List<DiscoverCatalog.DiscoverPack>) {
        val jobs = buildList {
            packs.forEach { pack ->
                DiscoverCatalog.coverUrls(pack).takeIf { it.isNotEmpty() }?.let { add(pack.cover to it) }
                pack.cards.forEach { entry ->
                    DiscoverCatalog.thumbUrls(entry).takeIf { it.isNotEmpty() }?.let { add(entry.thumb to it) }
                }
            }
        }
        if (jobs.isEmpty()) return
        viewModelScope.launch {
            jobs.map { (name, urls) ->
                async {
                    if (images.containsKey(name)) return@async
                    val path = DiscoverCatalog.cachedImagePath(name, urls) ?: return@async
                    images = images + (name to path)
                }
            }.awaitAll()
        }
    }

    /**
     * 卡片右侧那颗「导入」键：**只下载落卡，不开聊**——开聊交给「开聊」键或简介弹窗里的按钮。
     * 落卡成功不用另弹提示：卡片自己会变成"已导入"（[importedMap] 参与渲染），那就是反馈。
     */
    fun importCard(entry: DiscoverCatalog.DiscoverCardEntry) {
        if (busyCardId != null || importingPack) return
        busyCardId = entry.id
        viewModelScope.launch {
            try {
                downloadCard(entry)
            } catch (t: Throwable) {
                error = t.message ?: "导入失败"
            } finally {
                busyCardId = null
            }
        }
    }

    /**
     * 「开聊」键与简介弹窗里的「开始聊」：角色还在就直接开新会话。
     * 映射还在但角色被删了＝当作没导入，退回下载路径——否则这颗键点了什么都不会发生。
     */
    fun startChat(entry: DiscoverCatalog.DiscoverCardEntry, onChat: (CharacterCard) -> Unit) {
        importedMap[entry.id]?.let { rid ->
            Repository.getCharacter(rid)?.let { onChat(it); return }
        }
        importCard(entry)
    }

    /** 整包导入：跳过已导入且仍存在的卡，其余逐张下载；结果用成功弹窗反馈 */
    fun importPack(pack: DiscoverCatalog.DiscoverPack) {
        if (importingPack || busyCardId != null) return
        importingPack = true
        viewModelScope.launch {
            var fresh = 0
            var skipped = 0
            var failed = 0
            var lastError: String? = null
            try {
                for (entry in pack.cards) {
                    if (importedMap[entry.id]?.let { Repository.getCharacter(it) } != null) {
                        skipped++
                        continue
                    }
                    try {
                        downloadCard(entry)
                        fresh++
                    } catch (t: Throwable) {
                        failed++
                        lastError = t.message
                    }
                }
                notice = when {
                    fresh == 0 && skipped > 0 && failed == 0 ->
                        "「${pack.title}」的角色卡都已在卡库中"
                    failed == 0 -> "已导入「${pack.title}」${fresh}张角色卡"
                    else -> "已导入${fresh}张，${failed}张失败：${lastError}"
                }
            } finally {
                importingPack = false
            }
        }
    }

    /** 下载 → 抽卡 JSON → 落卡 → 记导入映射。调用方已持有 busyCardId 锁 */
    private suspend fun downloadCard(entry: DiscoverCatalog.DiscoverCardEntry): CharacterCard =
        withContext(Dispatchers.IO) {
            // 直连 + 镜像依次试：github.com 在原用户网络被 TLS 拦截，直连必失败
            val bytes = RemoteFetch.fetchBytes(DiscoverCatalog.cardUrls(entry))
            val text = CardImport.cardJsonFromBytes(bytes)
                ?: throw IllegalStateException("下载到的文件不是角色卡（PNG 里没有卡数据，或不是 JSON）")
            val card = CardImport.fromTavernJson(text)
            Repository.saveCharacter(card) { err ->
                viewModelScope.launch { error = "导入保存失败：${err.message ?: "磁盘写入异常"}" }
            }
            Repository.recordDiscoverImport(entry.id, card.id)
            importedMap = importedMap + (entry.id to card.id)
            card
        }

    fun clearNotice() {
        notice = null
    }

    fun clearError() {
        error = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    embedded: Boolean = false,
    onGoCharacters: () -> Unit = {},
    /** 开聊：点卡右侧的「开聊」键或简介弹窗里的「开始聊」（手机=跳聊天页；桌面=切到聊天 tab 并在第三栏渲染） */
    onStartChat: (CharacterCard) -> Unit = {},
    /**
     * 桌面三栏：第三栏内容**由第二栏 [DesktopDiscoverRail] 导航**——
     * 视图轴那排胶囊不再画（两套一模一样的导航并存很蠢），列表改成按宽度铺开的多列网格。
     * 手机端恒为 false，行为零变化。
     */
    desktopPane: Boolean = false,
    vm: DiscoverViewModel = viewModel(factory = noArgViewModelFactory { DiscoverViewModel() })
) {
    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            DiscoverBody(vm, onGoCharacters, onStartChat, desktopPane)
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("发现") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                DiscoverBody(vm, onGoCharacters, onStartChat, desktopPane)
            }
        }
    }
}

/**
 * 桌面第二栏（发现页）：**视图轴 + 本轴的分组**。
 *
 * 上半段＝四颗视图轴（主题 / 角色 / 故事 / 模板），读写的是与手机端那排胶囊**同一个** [DiscoverViewModel.scope]
 * （所以桌面不必再画一排胶囊，切轴的状态天然一致）；下半段＝当前轴里的分组，一行一个。
 *
 * 为什么下面那些行是"整页打开"而不是"页面内滚动定位"：一个分组自带封面、返回键与「全部导入」，
 * 本来就是**一整页**（手机端点封面进的就是这一页）。所以它对应的是「用户」页里那种**整页型栏目**
 * （点了换第三栏），而不是设置页那种"同一条流里滚过去"。若写成滚动定位，第三栏就得把 N 个分组的
 * 封面堆成一条流、卡片另开一页——等于把"页"拆成两半，反倒更绕。
 */
@Composable
internal fun DesktopDiscoverRail(
    vm: DiscoverViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    // 四个图标都取"手机端已经在用"的那几个：新图标会牵动打包时的图标精简闸门（漏删/删错都会让那页直接崩）
    val axes = listOf(
        ScopeTheme to Icons.Filled.Explore,
        DiscoverCatalog.KIND_CHARACTER to Icons.Filled.Person,
        DiscoverCatalog.KIND_STORY to Icons.Filled.AutoStories,
        DiscoverCatalog.KIND_TEMPLATE to Icons.Filled.Category
    )
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
    ) {
        DesktopRailHeader("发现")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item { DesktopRailSectionLabel("视图") }
            items(axes) { (label, icon) ->
                val selected = vm.scope == label
                DesktopRailRow(
                    label = label,
                    selected = selected,
                    onClick = { vm.switchScope(label) },
                    leading = {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                        )
                    }
                )
            }
            // 清单还没到手（或这一轴是空的）时不摆分组标题：一个空标题比没有标题更让人怀疑"是不是坏了"
            val groups = vm.groups
            if (groups.isNotEmpty()) {
                item { DesktopRailSectionLabel("精选分组") }
                items(groups, key = { it.id }) { pack ->
                    DesktopRailRow(
                        label = pack.title,
                        selected = vm.openedPackId == pack.id,
                        count = pack.cards.size.takeIf { it > 0 },
                        indent = 6.dp,
                        onClick = { vm.openPack(pack) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverBody(
    vm: DiscoverViewModel,
    onGoCharacters: () -> Unit,
    onStartChat: (CharacterCard) -> Unit,
    desktopPane: Boolean = false
) {
    // 点进某个包＝换一页。两条轴共用这套详情：主题轴给主题的封面与主题名，
    // 类型轴给合成的"角色精选"那一组（它没有封面，就只显示标题行）。
    val opened = vm.openedPack
    if (opened != null) {
        PackDetail(vm, opened, onStartChat, desktopPane)
    } else if (desktopPane) {
        FeaturedDesktop(vm)
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "从这里把角色卡带进鲸鱼——全部数据保存在本机，无需注册登录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 精选推荐（第二批）：清单来自 GitHub Release 附件（直连不通自动换镜像） ──
            FeaturedSection(vm)
        }
    }

    // 卡片简介：点精选卡先看清楚这是什么卡，导入与否由用户自己按
    vm.introEntry?.let { entry ->
        CardIntroDialog(entry, vm, onStartChat)
    }

    // 整包导入完成：给了去角色卡页的快捷出口（导入动作本身已完成）
    vm.notice?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearNotice,
            title = { Text("导入成功") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { vm.clearNotice(); onGoCharacters() }) { Text("去角色卡页") }
            },
            dismissButton = {
                TextButton(onClick = vm::clearNotice) { Text("留在本页") }
            }
        )
    }
    vm.error?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearError,
            title = { Text("导入失败") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearError) { Text("好的") } }
        )
    }
}

// ── 精选区（第二批）─────────────────────────────────────────────

/** 视图轴：主题＝默认（按主题推荐）；角色 / 故事 / 模板＝按类型发现精选 */
private const val ScopeTheme = "主题"

/** 排列：竖排＝默认；横排＝保留的老形式（用户口径「也支持现在这个形式」） */
private const val StackVertical = "竖排"
private const val StackHorizontal = "横排"

/**
 * 视图轴 chip。四颗**并列**而不是"主题 ↔ 类型"两段开关，是照用户 2026-09-23 的原话来的：
 * 「默认按主题推荐，但也支持按类型发现精选：角色、故事、模板」——主题在最前即默认，其余三颗是类型。
 *
 * 用横向 LazyRow 而不是 Row：桌面第三栏可以被拖到 208dp，四颗胶囊在那里一定放不下（会顶出界）。
 */
@Composable
private fun ScopeChips(vm: DiscoverViewModel) {
    val scopeScroll = rememberLazyListState()
    LazyRow(
        state = scopeScroll,
        modifier = Modifier.wheelHorizontalScroll(scopeScroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(listOf(ScopeTheme) + DiscoverCatalog.kinds) { s ->
            WhaleChip(selected = vm.scope == s, onClick = { vm.switchScope(s) }, label = { Text(s) })
        }
    }
}

/**
 * 空组文案。用户口径是「可以暂时先不填充内容」，所以故事 / 模板这两组现在必然是空的——
 * 那就得说清"是还没做，不是坏了"，否则一个空卡片看起来像加载失败。
 */
private fun emptyHintFor(pack: DiscoverCatalog.DiscoverPack): String = when (pack.id) {
    DiscoverCatalog.kindPackId(DiscoverCatalog.KIND_STORY) -> "故事精选还在筹备中，以后这里放按主题挑好的故事"
    DiscoverCatalog.kindPackId(DiscoverCatalog.KIND_TEMPLATE) -> "模板精选还在筹备中，以后这里放现成的创作模板"
    else -> "这个包暂时还没有内容"
}

/**
 * 桌面第三栏的精选列表（"这个页面还是很像手机端"）。
 *
 * 数据、卡片、点击落点与手机端完全同一套，只有**排版**分端：手机端是一条窄流（一句话说明 + 一组
 * 满宽封面卡），照搬到桌面就是"很手机"——所以这里换成**自适应多列网格**（每列 ≥260dp，第三栏宽了
 * 就自动多摆几列）。视图轴那排胶囊不在这里画：第二栏 [DesktopDiscoverRail] 就是它的常驻形态。
 */
@Composable
private fun FeaturedDesktop(vm: DiscoverViewModel) {
    LaunchedEffect(Unit) { vm.loadFeatured() }
    val packs = vm.packs
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { DiscoverIntroText() }
        item(span = { GridItemSpan(maxLineSpan) }) { FeaturedHeader(vm) }
        when {
            packs == null && vm.manifestError == null ->
                item(span = { GridItemSpan(maxLineSpan) }) { FeaturedLoading() }
            vm.manifestError != null ->
                item(span = { GridItemSpan(maxLineSpan) }) { FeaturedError(vm) }
            else -> {
                if (vm.manifestFromCache) {
                    item(span = { GridItemSpan(maxLineSpan) }) { FeaturedCacheHint() }
                }
                // 用 forEach + item(key) 而不是 grid 的 items()：本文件已经 import 了 lazy 的 items
                // （给 LazyRow 用），再 import 一个同名的会两头都不好读。
                vm.groups.forEach { pack ->
                    item(key = pack.id) {
                        PackCoverCard(pack, vm.images[pack.cover], emptyHintFor(pack)) { vm.openPack(pack) }
                    }
                }
            }
        }
    }
}

/** 发现页顶部那句说明（两端同一句抽出来给网格版复用） */
@Composable
private fun DiscoverIntroText() {
    Text(
        "从这里把角色卡带进鲸鱼——全部数据保存在本机，无需注册登录。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 「精选推荐」那一行：标题 + 刷新（两端共用） */
@Composable
private fun FeaturedHeader(vm: DiscoverViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "精选推荐",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        // 加载中禁刷新（状态在拨回"加载中"，连点没意义）
        IconButton(
            onClick = vm::refreshFeatured,
            enabled = vm.packs != null || vm.manifestError != null
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "刷新精选")
        }
    }
}

/** 清单还没到手：转圈 + 一句"加载中"（两端共用） */
@Composable
private fun FeaturedLoading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            "精选内容加载中…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeaturedError(vm: DiscoverViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "精选内容加载失败：${vm.manifestError}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = vm::refreshFeatured) { Text("重试") }
    }
}

/** 离线兜底：这份内容来自上次成功拉到的缓存（网络不通），先说清楚再列内容 */
@Composable
private fun FeaturedCacheHint() {
    Text(
        "网络不通，显示的是上次缓存的内容；点右上角刷新可重试。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FeaturedSection(vm: DiscoverViewModel) {
    LaunchedEffect(Unit) { vm.loadFeatured() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeaturedHeader(vm)
        val packs = vm.packs
        when {
            packs == null && vm.manifestError == null -> FeaturedLoading()
            vm.manifestError != null -> FeaturedError(vm)
            else -> {
                // 离线兜底：这份内容来自上次成功拉到的缓存（网络不通），先说清楚再列内容
                if (vm.manifestFromCache) FeaturedCacheHint()
                // 视图轴：切轴只换下面列什么，清单本身不重拉
                ScopeChips(vm)
                // 列表页只摆「封面卡」：点封面进详情页看卡。卡片条不再在这里横着铺——
                // 用户 2026-09-23：「横着太不舒服了」。原来那个横排形式留在详情页的排列开关里。
                vm.groups.forEach { pack ->
                    PackCoverCard(pack, vm.images[pack.cover], emptyHintFor(pack)) { vm.openPack(pack) }
                }
            }
        }
    }
}

/**
 * 列表页的一张「封面卡」。用户 2026-09-23 两条口径的落点：
 * ① 卡片条**不再横着铺**（"横着太不舒服了"）——列表页只给封面，卡都在详情页里；
 * ② "分组封面要用相关的图片"——封面就是主题的场景图（清单里就有这个键）。
 *
 * 空组（故事 / 模板）不摆"点开看全部"、整天也不可点：一张点了什么都不会发生的卡，
 * 比没有这张卡更让人困惑。
 */
@Composable
private fun PackCoverCard(
    pack: DiscoverCatalog.DiscoverPack,
    cover: String?,
    emptyHint: String,
    onOpen: () -> Unit
) {
    val hasCards = pack.cards.isNotEmpty()
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = hasCards) { onOpen() }
    ) {
        Column {
            if (cover != null) {
                AsyncImage(
                    model = imageModel(cover),
                    contentDescription = pack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(pack.title, style = MaterialTheme.typography.titleMedium)
                if (pack.tagline.isNotBlank()) {
                    Text(
                        pack.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasCards) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${pack.cards.size} 张卡",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "点开看全部 ›",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        emptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 主题 / 分组详情：**顶部有封面与主题名**（用户原话「点进去以后也有封面和主题名」），
 * 下面是这个分组里的卡。默认**竖排**；上面那排「竖排 / 横排」胶囊可以切回原来的横排形式。
 */
@Composable
private fun PackDetail(
    vm: DiscoverViewModel,
    pack: DiscoverCatalog.DiscoverPack,
    onChat: (CharacterCard) -> Unit,
    desktop: Boolean = false
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::closePack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回精选")
            }
            Text(
                pack.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 封面（类型轴的合成组没有封面：那就只留上面那行标题，不占空位）
        vm.images[pack.cover]?.let { cover ->
            AsyncImage(
                model = imageModel(cover),
                contentDescription = pack.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (pack.tagline.isNotBlank()) {
                Text(
                    pack.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${pack.cards.size} 张卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 空组不摆「全部导入」：一颗点了什么都不会发生的按钮比没有按钮更让人困惑
                if (pack.cards.isNotEmpty()) {
                    TextButton(
                        onClick = { vm.importPack(pack) },
                        enabled = !vm.importingPack && vm.busyCardId == null
                    ) {
                        if (vm.importingPack) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (vm.importingPack) "导入中" else "全部导入")
                    }
                }
            }
            if (pack.cards.isEmpty()) {
                Text(
                    emptyHintFor(pack),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 排列开关**独占一行**（而不是挤进上面那行）：桌面第三栏能被拖到 208dp，
                // 两颗胶囊＋「全部导入」放同一行在那里会互相顶出界。
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(StackVertical, StackHorizontal)) { m ->
                        WhaleChip(
                            selected = vm.stackMode == m,
                            onClick = { vm.switchStack(m) },
                            label = { Text(m) }
                        )
                    }
                }
                if (vm.stackMode == StackHorizontal) {
                    // 老形式（用户"也支持现在这个形式"）：一排卡片条横着翻
                    val featuredScroll = rememberLazyListState()
                    LazyRow(
                        state = featuredScroll,
                        modifier = Modifier.wheelHorizontalScroll(featuredScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(pack.cards, key = { it.id }) { entry ->
                            FeaturedCardTile(entry, vm, onChat)
                        }
                    }
                } else if (desktop) {
                    // 桌面：手机端那种"一行一张卡拉满整宽"在 1300dp 的第三栏里
                    // 横着扫得难受，改成按可用宽度摆几列（每列 ≥360dp）。这里**不能**套 LazyVerticalGrid
                    // ——外层已经在 verticalScroll 里，竖向懒列表会被测成无限高（同下面那句注释的坑），
                    // 所以自己算列数、按行分组（一组最多四五张卡，本来也不需要懒加载）。
                    BoxWithConstraints {
                        val cols = maxOf(1, (maxWidth / 360.dp).toInt())
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            pack.cards.chunked(cols).forEach { rowCards ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowCards.forEach { entry ->
                                        Box(Modifier.weight(1f)) { PackCardRow(entry, vm, onChat) }
                                    }
                                    // 最后一行不满时补齐空位，免得剩下那一张被拉成整行宽
                                    repeat(cols - rowCards.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                } else {
                    // 手机端：一行一张，竖着读。用普通 Column 而不是 LazyColumn——外层已经在
                    // verticalScroll 里，再套一个竖向懒列表会被测成无限高（Compose 直接崩）；
                    // 一个分组最多四五张卡，本来也不需要懒加载。
                    pack.cards.forEach { entry -> PackCardRow(entry, vm, onChat) }
                }
            }
        }
    }
}

/** 竖排的一张卡：缩略图在左、名字与简介在右，比横条里 160dp 的小卡好读得多 */
@Composable
private fun PackCardRow(
    entry: DiscoverCatalog.DiscoverCardEntry,
    vm: DiscoverViewModel,
    onChat: (CharacterCard) -> Unit
) {
    // 已导入 = 映射还在且本地角色没被删（删了就当作没导入，点了会重新下载）
    val mappedId = vm.importedMap[entry.id]
    val imported = mappedId != null && Repository.getCharacter(mappedId) != null
    val thumb = vm.images[entry.thumb]
    // 点卡片＝看简介：这一步不下载、不落卡，所以别的卡在导入时它也照样能点开
    // 外壳见 [ListItemCard]：手机端仍是这张描边卡，桌面端**整块壳摘掉**——
    // 第三栏是多列网格，一项一个描边框正是用户说的那个"手机味"。
    ListItemCard(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openIntro(entry) }
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                AsyncImage(
                    model = imageModel(thumb),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    FormBadge(entry.form)
                }
                Text(
                    entry.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                CardStateHint(entry, vm, imported)
            }
            // 卡片右侧那颗键（用户："在卡片右侧添加导入键"）
            CardActionKey(entry, vm, imported, onChat)
        }
    }
}

/**
 * 卡片右下角那行状态：拉取中 / 已导入 / 点开看简介。
 * 「点开看简介」是新的提示——点卡片不再直接开聊了，得先让用户知道点下去会发生什么。
 */
@Composable
private fun CardStateHint(
    entry: DiscoverCatalog.DiscoverCardEntry,
    vm: DiscoverViewModel,
    imported: Boolean
) {
    if (vm.busyCardId == entry.id) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            Text("拉取中", style = MaterialTheme.typography.labelSmall)
        }
    } else {
        Text(
            if (imported) "已导入" else "点开看简介",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 卡片右侧那颗键（用户 2026-09-23：「在卡片右侧添加导入键」）：
 * **未导入＝「导入」**（只落卡、不开聊），**已导入＝「开聊」**（省掉再点一次卡片的往返）。
 * 拉取中或正在整包导入时禁用——两种导入链路抢同一个 [DiscoverViewModel.busyCardId] 锁。
 */
@Composable
private fun CardActionKey(
    entry: DiscoverCatalog.DiscoverCardEntry,
    vm: DiscoverViewModel,
    imported: Boolean,
    onChat: (CharacterCard) -> Unit
) {
    OutlinedButton(
        onClick = { if (imported) vm.startChat(entry, onChat) else vm.importCard(entry) },
        enabled = vm.busyCardId == null && !vm.importingPack,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(if (imported) "开聊" else "导入", style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 精选卡的简介：点什么卡都先弹这个，看清楚了再决定导不导。
 * 内容只用清单里已有的东西（缩略图 / 卡名 / 形态 / 一句话简介）＋一句"导入会发生什么"。
 */
@Composable
private fun CardIntroDialog(
    entry: DiscoverCatalog.DiscoverCardEntry,
    vm: DiscoverViewModel,
    onChat: (CharacterCard) -> Unit
) {
    val mappedId = vm.importedMap[entry.id]
    val imported = mappedId != null && Repository.getCharacter(mappedId) != null
    val busy = vm.busyCardId == entry.id
    AlertDialog(
        onDismissRequest = vm::closeIntro,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                FormBadge(entry.form)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                vm.images[entry.thumb]?.let { path ->
                    AsyncImage(
                        model = imageModel(path),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                if (entry.tagline.isNotBlank()) {
                    Text(
                        entry.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    if (imported) "已经在你的角色卡里了，随时可以开聊。"
                    else "导入后会存进「角色卡」，随时可以开聊。内容只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                // 导入后弹窗**不自动关**：卡片状态就地变成"已导入"，这颗键随即变成「开始聊」——
                // 用户看得见"导入成功了"，也顺手就能进聊天
                onClick = {
                    if (imported) {
                        vm.closeIntro()
                        vm.startChat(entry, onChat)
                    } else {
                        vm.importCard(entry)
                    }
                },
                enabled = !busy && vm.busyCardId == null && !vm.importingPack
            ) {
                Text(if (busy) "拉取中" else if (imported) "开始聊" else "导入")
            }
        },
        dismissButton = { TextButton(onClick = vm::closeIntro) { Text("关闭") } }
    )
}

/**
 * 形态徽标：主题包里混玩法/工具卡时一眼看出是哪一档。
 * 陪伴（form 为空）不画——它是多数派、又是缺省，画出来只是噪音；
 * 认不出的取值也不画（[Engines.labelOrNull] 的口径：看不见比看错好）。
 */
@Composable
private fun FormBadge(form: String) {
    Engines.labelOrNull(form)?.let { label ->
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun FeaturedCardTile(
    entry: DiscoverCatalog.DiscoverCardEntry,
    vm: DiscoverViewModel,
    onChat: (CharacterCard) -> Unit
) {
    // 已导入 = 映射还在且本地角色没被删（删了就当作没导入，点了会重新下载）
    val mappedId = vm.importedMap[entry.id]
    val imported = mappedId != null && Repository.getCharacter(mappedId) != null
    OutlinedCard(
        Modifier
            .width(160.dp)
            .clickable { vm.openIntro(entry) }
    ) {
        Column {
            // 缩略图：清单里的 224×224 小图，拉不到就退回原来的纯文字 tile
            val thumb = vm.images[entry.thumb]
            if (thumb != null) {
                AsyncImage(
                    model = imageModel(thumb),
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Column(
                Modifier
                    .padding(10.dp)
                    .heightIn(min = 96.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // 形态徽标：主题包里混玩法/工具卡时一眼看出是哪一档
                    FormBadge(entry.form)
                }
                Text(
                    entry.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (vm.busyCardId == entry.id) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text("拉取中", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    // 提示与那颗键同一行：卡片只有 160dp 宽，所以提示**必须**能省略号收尾
                    // （maxLines = 1），否则大字号下它会把右边的键顶出卡片
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            if (imported) "已导入" else "点开看简介",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        CardActionKey(entry, vm, imported, onChat)
                    }
                }
            }
        }
    }
}
