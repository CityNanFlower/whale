package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.mysticat.roleplay.ui.DesktopCardContextMenu
import com.mysticat.roleplay.ui.DesktopMenuRow
import com.mysticat.roleplay.ui.DesktopMainHeader
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.desktopSecondaryClick
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.components.WhaleFieldBlock
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEngine
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.WorldBookFile
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.BookSectionJumper
import com.mysticat.roleplay.ui.WorldBookEditFields
import com.mysticat.roleplay.ui.wheelHorizontalScroll
import com.mysticat.roleplay.ui.WorldBookEditState
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.rememberJsonFilePicker
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.standaloneBookFileName
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 宝库页（「角色卡」tab 扩容）的段。三段壳＝角色｜世界书｜故事，故事段随故事功能落位时再加进枚举即可。
 */
enum class LibrarySegment(val label: String) {
    CHARACTERS("角色"),
    WORLD_BOOKS("世界书")
}

/** 顶部段切换：手机端在页内顶栏下方，桌面端在第二栏顶部——两端共用一份 */
@Composable
internal fun LibrarySegmentRow(
    selected: LibrarySegment,
    onSelect: (LibrarySegment) -> Unit,
    modifier: Modifier = Modifier,
    /** 段栏右端的挂点：角色卡段的「名册 / 柜格」切换器靠它推到右缘（原型 `.vsw` 的 `margin-left:auto`） */
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibrarySegment.entries.forEach { s ->
            WhaleChip(
                selected = selected == s,
                onClick = {
                    CrashNote.note("宝库 段=${s.label}")
                    onSelect(s)
                },
                label = { Text(s.label) }
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * 角色编辑器「世界书管理」跳宝库的跨页请求。
 * 编辑器把请求放进来再退回主界面；宝库页（手机 = 角色列表页的世界书段）重组时消费掉。
 * 值 = 待打开的书 id；null 串（空串）不允许——只切段不发请求的场景不设这个对象。
 */
object WorldBookJump {
    @Volatile
    var pendingBookId: String? = null

    /** 读走并清空（消费方一次性拿走） */
    fun take(): String? = pendingBookId.also { pendingBookId = null }
}

/**
 * 宝库页正在编辑的一本书：字段全是编辑副本，返回时整本写回书文件
 * （[WorldBookLibraryViewModel.closeEditor]）。界面件与角色编辑器的世界书分节共用
 * （[WorldBookEditFields]），这里只持状态。
 */
class WorldBookDraft(val bookId: String, book: WorldBook) : WorldBookEditState {
    override var bookName by mutableStateOf(book.name)
    override var bookDesc by mutableStateOf(book.description)
    override var bookScanDepth by mutableStateOf(book.scanDepth)
    override var bookEntries by mutableStateOf(book.entries)
    var bookExtraRaw by mutableStateOf(book.extraRaw)
    /** 书脊轨上的位次：编辑页整本写回时必须带上，漏了就把用户排好的顺序抹成 0 */
    var bookOrder by mutableStateOf(book.order)

    /** 导入整本替换（与角色编辑器同口径：条目 id 两家都从 0 编，合并必撞） */
    fun importFrom(book: WorldBook) {
        bookName = book.name
        bookDesc = book.description
        bookScanDepth = book.scanDepth
        bookEntries = book.entries
        bookExtraRaw = book.extraRaw
    }

    fun clearBook() {
        bookName = ""
        bookDesc = ""
        bookScanDepth = null
        bookEntries = emptyList()
        bookExtraRaw = ""
    }

    // 条目增删改移：与角色编辑器 VM 同一套逻辑（界面件共用 ⇒ 行为也必须共用）
    override fun addBookEntry() {
        val nextId = (bookEntries.maxOfOrNull { it.id } ?: -1) + 1
        bookEntries = bookEntries + WorldBookEntry(id = nextId)
    }

    override fun removeBookEntry(index: Int) {
        bookEntries = bookEntries.filterIndexed { i, _ -> i != index }
    }

    override fun moveBookEntry(from: Int, to: Int) {
        if (from !in bookEntries.indices || to !in bookEntries.indices || from == to) return
        bookEntries = bookEntries.toMutableList().apply { add(to, removeAt(from)) }
    }

    override fun updateBookEntry(index: Int, transform: (WorldBookEntry) -> WorldBookEntry) {
        bookEntries = bookEntries.mapIndexed { i, e -> if (i == index) transform(e) else e }
    }

    fun toBook(): WorldBook = WorldBook(
        name = bookName.trim(),
        description = bookDesc.trim(),
        entries = bookEntries,
        scanDepth = bookScanDepth,
        order = bookOrder,
        extraRaw = bookExtraRaw
    )
}

/**
 * 宝库页的世界书段：账号级书列表 ＋ 独立导入导出 ＋ 删除（引用检查）＋ 点开改书。
 * 手机端与桌面端共用这一个 VM；书文件是唯一事实来源，界面列表只是它的内存镜像
 * （写走 [Repository] 的写入队列，落盘后靠下次 refresh 对齐）。
 *
 * **内存镜像口径（双端统一约定，改这个页面前先照这四条）**：
 * ① 磁盘（[Repository]）是唯一事实来源，[books] / [referring] / [cards] 都只是缓存；
 * ② 刷新入口只挂**页面根 / 段入口**（手机端在世界书段入口、桌面端在段状态变化处），
 *    **不挂列表组件**——跳转直达书编辑器时列表并不组合，挂在那儿镜像会恒空；
 * ③ 外部请求直达子页面的入口（[openEditor]）必须**同步**保证镜像已含目标条目：
 *    [refresh] 是挂起的，只补一次 refresh 救不了同一拍里的读取；
 * ④ 自己的写操作按"写后自更新"改镜像（见 [upsertInMemory] / [setCardBook]），不必整段重读。
 */
class WorldBookLibraryViewModel : ViewModel() {
    var books by mutableStateOf<List<WorldBookFile>>(emptyList())
        private set
    /** bookId → 引用它的卡名列表（删除确认与行内"被 N 张卡使用"都用它） */
    var referring by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    /** 全部角色卡（编辑区「使用这本书的角色」逐卡开关用；卡已带解析后的 worldBookId） */
    var cards by mutableStateOf<List<CharacterCard>>(emptyList())
        private set

    // ── 逐卡开关列表的筛选（2026-09-25 用户反馈：卡多了要能搜、能按主题分类筛）──────
    var usageQuery by mutableStateOf("")
    var usageCategory by mutableStateOf("全部")

    /** 按分类＋角色名过滤后的逐卡开关列表（只过滤显示，不动 [cards] 的开关状态） */
    val usageCards: List<CharacterCard>
        get() {
            val q = usageQuery.trim()
            return cards.filter { c ->
                val matchCat = usageCategory == "全部" || usageCategory in c.categoriesOrDefault()
                val matchQ = q.isEmpty() || c.name.contains(q, true)
                matchCat && matchQ
            }
        }

    /** 正在编辑的书 id；null ＝ 列表态 */
    var editingId by mutableStateOf<String?>(null)
    var draft by mutableStateOf<WorldBookDraft?>(null)
        private set
    /** 待确认删除的书 id（确认弹窗由列表 / 编辑区弹出） */
    var pendingDeleteId by mutableStateOf<String?>(null)

    fun refresh() {
        viewModelScope.launch {
            // 写入走 Repository 的单线程队列：刚保存完立刻进来时等一小拍，别用旧盘面盖掉内存镜像
            delay(150)
            val pair = withContext(Dispatchers.IO) {
                val list = Repository.listWorldBooks()
                Triple(
                    list,
                    list.associate { f -> f.id to Repository.worldBookReferringCards(f.id) },
                    Repository.listCharacters()
                )
            }
            books = pair.first
            referring = pair.second
            cards = pair.third
        }
    }

    /**
     * 逐卡开关一本书：开＝卡引用本书、关＝解除引用。
     * 落盘走 [Repository.setCharacterWorldBook]，随后按 id 回取更新内存镜像（开关立刻反映），
     * 并把 [referring] 一起改掉——否则删除确认弹窗里"哪些卡正在用它"要等下次进段才对齐。
     */
    fun setCardBook(cardId: String, bookId: String?, onFailure: (Throwable) -> Unit = {}) {
        // 写之前先记下这张卡原来挂的是哪本（写完镜像里的卡对象就换了）
        val prev = cards.firstOrNull { it.id == cardId }?.worldBookId
        Repository.setCharacterWorldBook(cardId, bookId, onFailure)
        val idx = cards.indexOfFirst { it.id == cardId }
        if (idx < 0) return
        val c = Repository.getCharacter(cardId) ?: return
        cards = cards.toMutableList().apply { set(idx, c) }
        if (prev == bookId) return
        val name = c.name
        val m = referring.toMutableMap()
        if (prev != null) m[prev] = m[prev].orEmpty().filterNot { it == name }
        if (bookId != null) m[bookId] = (m[bookId].orEmpty() + name).distinct()
        referring = m
    }

    fun referringCards(bookId: String): List<String> = referring[bookId].orEmpty()

    fun openEditor(bookId: String) {
        // 列表没加载完（刚跳转进来就点开了）也能开：书文件兜底直读
        val known = books.firstOrNull { it.id == bookId }
        val f = known
            ?: Repository.getWorldBook(bookId)?.let { WorldBookFile(bookId, it) }
            ?: return
        if (known == null) {
            // 跳转直达编辑器（角色编辑器「编辑/新建」经 WorldBookJump 进来）时列表组件没组合过、
            // 内存镜像还是空的——把兜底读到的书补进去，否则删除确认弹窗会因"列表里找不到"静默消失
            // （2026-09-25 用户反馈"跳转后删除没法用"的根因）；refresh 再把引用表与卡列表补齐
            books = books + f
            refresh()
        }
        draft = WorldBookDraft(f.id, f.book)
        editingId = f.id
    }

    /** 保存并返回（save=false 只有"书已是空书"这一条路径会走：空书不值得占一个文件） */
    fun closeEditor(onFailure: (Throwable) -> Unit = {}) {
        val d = draft ?: return
        editingId = null
        draft = null
        val book = d.toBook()
        if (book.isEmpty()) {
            Repository.deleteWorldBook(d.bookId, onFailure)
            removeInMemory(d.bookId)
        } else {
            Repository.saveWorldBook(d.bookId, book, onFailure)
            upsertInMemory(d.bookId, book)
        }
    }

    /**
     * 放弃草稿、退回列表（返回键在"有未保存改动"时、以及新建后什么都没填就走的那一支）。
     * 只认**磁盘上那份也空**的书才顺手删文件（新建即落盘、什么都没填）——空书不值得占一个文件，
     * 与保存路径同一口径；用户把一本真书的内容清空再放弃，是"放弃改动"，不能把书删掉。
     */
    fun discardEditor() {
        val d = draft
        val onDisk = d?.let { dd -> books.firstOrNull { it.id == dd.bookId }?.book }
        editingId = null
        draft = null
        if (d != null && onDisk != null && onDisk.isEmpty()) {
            Repository.deleteWorldBook(d.bookId)
            removeInMemory(d.bookId)
        }
    }

    /**
     * 草稿相对磁盘上那本书有没有改动——返回键据此决定要不要先问一句"保存更改"。
     * 列表镜像里找不到原书（跳转直达、镜像还没热）时按"有改动"算，宁可多问一句。
     */
    fun draftDirty(): Boolean {
        val d = draft ?: return false
        val original = books.firstOrNull { it.id == d.bookId }?.book ?: return true
        return d.toBook() != original
    }

    fun deleteBook(bookId: String, onFailure: (Throwable) -> Unit = {}) {
        Repository.deleteWorldBook(bookId, onFailure)
        removeInMemory(bookId)
        if (editingId == bookId) {
            editingId = null
            draft = null
        }
        showToast("世界书已删除")
    }

    fun importBook(text: String, onFailure: (Throwable) -> Unit = {}) {
        val book = WorldBookEngine.fromStandaloneJson(text)
        if (book == null) {
            showToast("这个文件里没有世界书", long = true)
            return
        }
        val id = Repository.newId()
        Repository.saveWorldBook(id, book, onFailure)
        upsertInMemory(id, book)
        showToast("已导入世界书「${book.name.ifBlank { "未命名" }}」${book.entries.size} 条")
    }

    fun createBook(onFailure: (Throwable) -> Unit = {}) {
        val id = Repository.newId()
        // 新书排到最末：老书 order 都是 0，新书给 0 会插到字母序里去，拖出来的排列也就白排了
        val book = WorldBook(order = (books.maxOfOrNull { it.book.order } ?: -1) + 1)
        Repository.saveWorldBook(id, book, onFailure)
        upsertInMemory(id, book)
        draft = WorldBookDraft(id, book)
        editingId = id
    }

    /**
     * 书脊轨拖动排序落盘（用户 2026-10-02：「手机端要可以拖动」）。
     *
     * `permutation` 是**下标排列**（第 i 位放原来的第 permutation[i] 本），
     * 与 [BookSpineRail] 松手时交出来的那份同口径。先按它改内存镜像（界面上立刻就位），
     * 再把整排 id 交给 [Repository.setWorldBookOrder] 重编号落盘。
     */
    fun reorderBooks(permutation: List<Int>) {
        if (permutation.size != books.size || permutation.toSet() != books.indices.toSet()) return
        val reordered = permutation.map { books[it] }.mapIndexed { i, f -> f.copy(book = f.book.copy(order = i)) }
        books = reordered
        viewModelScope.launch {
            runCatching { Repository.setWorldBookOrder(reordered.map { it.id }) }
                .onFailure { refresh() }
        }
    }

    private fun removeInMemory(bookId: String) {
        books = books.filterNot { it.id == bookId }
        referring = referring - bookId
    }

    private fun upsertInMemory(bookId: String, book: WorldBook) {
        books = (books.filterNot { it.id == bookId } + WorldBookFile(bookId, book))
            .sortedWith(compareBy({ it.book.order }, { it.book.name }))
    }
}

/** 拿到（或创建）宝库页 VM 的统一入口——MainScreen 桌面侧与 CharacterListScreen 手机侧都用它 */
@Composable
internal fun rememberWorldBookLibraryViewModel(): WorldBookLibraryViewModel =
    viewModel(factory = noArgViewModelFactory { WorldBookLibraryViewModel() })

/**
 * 世界书段的列表区（手机端；桌面端列表住第二栏，见 [WorldBookRail]）。
 * 行内给书名 / 条目数 / 被几张卡引用；导入与新建在顶部一行。
 */
@Composable
internal fun WorldBookLibraryList(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val pickBook = rememberJsonFilePicker { text -> text?.let { vm.importBook(it) } }
    // 刷新由段入口负责（见 VM 头部的口径②）：本组件在"跳转直达编辑器"时不组合，不能在这里挂 load

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${vm.books.size} 本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { pickBook() }) { Text("导入 JSON") }
            TextButton(onClick = { CrashNote.note("宝库 新建书"); vm.createBook() }) { Text("新建") }
        }
        if (vm.books.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                Text(
                    "还没有世界书",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "世界书是账号级的背景资料库：一本书可以被多张角色卡引用。" +
                        "可以从 SillyTavern 的世界书 JSON 导入，或新建一本从零写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { pickBook() }) { Text("导入世界书 JSON") }
            }
        } else {
            // 柜格＋名册同屏（用户 2026-10-02 澄清：这里的"并存"就是指这两块，**没有视图切换器**）
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "spines") {
                    BookSpineRail(
                        books = vm.books.map { (it.book.name.ifBlank { "未命名" } to it.book.entries.size) },
                        onOpen = { idx ->
                            val f = vm.books.getOrNull(idx) ?: return@BookSpineRail
                            CrashNote.note("宝库 改书=${f.book.name}")
                            vm.openEditor(f.id)
                        },
                        reorderable = true,
                        onOrderChange = vm::reorderBooks
                    )
                }
                item(key = "rh") {
                    RosterSectionHeader(label = "名册", count = vm.books.size, countLabel = "· {n} 本")
                }
                items(vm.books, key = { it.id }) { f ->
                    WorldBookRosterRow(
                        title = f.book.name.ifBlank { "未命名" },
                        entryCount = f.book.entries.size,
                        usedBy = vm.referringCards(f.id),
                        onOpen = { CrashNote.note("宝库 改书=${f.book.name}"); vm.openEditor(f.id) },
                        onDelete = { vm.pendingDeleteId = f.id }
                    )
                }
            }
        }
    }

    WorldBookDeleteDialog(vm)
}

/** 列表里一本书的行（手机端卡片；桌面端不用这个，走第二栏行） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/**
 * 名册里一本书的一行（原型 `wbrow`）。头像槽不是圆而是**竖着的小书封**（44×56、r1）——
 * 与角色卡段的 62dp 圆头像区分开，一看就知道这一段是书不是人。
 *
 * 行内三层与原型一致：书名 → 「N 条 · 被 N 张卡使用 / 没有被引用」→ 使用它的卡名（` / ` 分隔，没有引用则不画）。
 *
 * **删除改右击**（用户 2026-10-03 反馈 ⑥，与宝库角色卡同一条语法）：原来桌面第二栏给每行挂一枚
 * 常驻垃圾桶钮，一列书就是一列红图标，视觉噪声大且和「编辑」那类动作抢注意力。
 * 现在常驻钮撤掉——手机上长按仍然可以删，桌面上右键弹菜单（菜单本体由调用方在**这一行自己**里声明）。
 */
@Composable
private fun WorldBookRosterRow(
    title: String,
    entryCount: Int,
    usedBy: List<String>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    /** 桌面第二栏：接右键，删除从行尾常驻钮挪进菜单 */
    onMenu: (() -> Unit)? = null
) {
    val pal = LocalWhalePalette.current
    RosterRow(
        avatar = {
            Box(
                Modifier
                    .size(width = 44.dp, height = 56.dp)
                    .clip(WhaleShapes.r1)
                    .background(pal.soft),
                contentAlignment = Alignment.Center
            ) {
                // 一个字（不是前两个字）：44dp 的小书封槽里两个字挤成一团，看着像残字
                InitialGlyph(title, 20.sp, pal.accent)
            }
        },
        name = title,
        meta = buildString {
            append("$entryCount 条")
            append(" · ")
            append(if (usedBy.isEmpty()) "没有被引用" else "被 ${usedBy.size} 张卡使用")
        },
        tagline = usedBy.joinToString(" / "),
        greeting = "",
        onOpen = onOpen,
        onLongPress = onDelete,
        // 右键菜单挂最外层：Initial 阶段消费掉右键，内层 clickable 就看不到它（与角色卡同理）
        modifier = if (onMenu != null) Modifier.desktopSecondaryClick(onMenu) else Modifier
    )
}

/**
 * 桌面端第二栏：世界书段＝**索引**（抬头＋一行一本书：书本图标 / 名称 / 条数，当前编辑的那本高亮）。
 *
 * 口径来自 v6 板块原型桌面 col2：分组小标题 ＋ 一行一本书的清单。
 * 柜格与名册在**第三栏**（[WorldBookLibraryPane]），这一栏不重复画。
 */
@Composable
internal fun WorldBookRail(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    // 刷新由外壳的段入口负责（同手机端口径），本栏只渲染
    val colors = MaterialTheme.colorScheme
    val type = LocalWhaleType.current
    // 被右键的那本书（菜单锚在这一行自己里，见名册行那段说明）
    var menuBookId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
    ) {
        // 栏头统一走 DesktopRailHeader（原手写版 top=10，比其余四栏高 2dp——218 复查抓出）
        DesktopRailHeader("世界书") {
            Text(
                "${vm.books.size} 本",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        // 这一栏只放**索引**（原型 col2：`gh 世界书` ＋ 每本一行）；柜格与名册在第三栏。
        // 两栏都摆一遍就是同一个列表出现两次——用户 2026-10-03 反馈「没选书时主栏目空着」时一并对齐。
        if (vm.books.isEmpty()) {
            Text(
                "还没有世界书。第三栏可以新建一本，或从 SillyTavern 的 JSON 导入。",
                style = type.meta,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.books, key = { it.id }) { f ->
                    val bookName = f.book.name.ifBlank { "未命名" }
                    Box {
                        WorldBookIndexRow(
                            name = bookName,
                            entryCount = f.book.entries.size,
                            selected = vm.editingId == f.id,
                            onClick = { CrashNote.note("宝库 改书=$bookName"); vm.openEditor(f.id) },
                            onMenu = { menuBookId = f.id }
                        )
                        // 菜单锚在这一行自己里（2026-10-03 反馈 ⑤/⑥，与名册行同一条语法）
                        if (menuBookId == f.id) {
                            DesktopCardContextMenu(
                                onDismiss = { menuBookId = null },
                                width = 150.dp
                            ) {
                                DesktopMenuRow(
                                    label = "编辑世界书",
                                    onClick = {
                                        menuBookId = null
                                        CrashNote.note("宝库 改书=$bookName")
                                        vm.openEditor(f.id)
                                    }
                                )
                                DesktopMenuRow(
                                    label = "删除世界书…",
                                    danger = true,
                                    onClick = {
                                        menuBookId = null
                                        vm.pendingDeleteId = f.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除确认与手机端共用一份（名册行的垃圾桶钮 ＋ 第三栏编辑页里的删除都走它）
    WorldBookDeleteDialog(vm)
}

/** 删除确认弹窗（手机 / 桌面共用；引用了这本书的卡会先被告知后果） */
@Composable
internal fun WorldBookDeleteDialog(vm: WorldBookLibraryViewModel) {
    val id = vm.pendingDeleteId ?: return
    val f = vm.books.firstOrNull { it.id == id }
    if (f == null) {
        vm.pendingDeleteId = null
        return
    }
    val refs = vm.referringCards(id)
    // 236 弹窗统一：删除类残留收编共享确认壳（手机底部浮层／桌面居中面板，与 241 同族）
    WhaleConfirmSheet(
        title = "删除世界书「${f.book.name.ifBlank { "未命名" }}」？",
        body = if (refs.isEmpty()) "没有任何角色卡引用这本书，删除后不可恢复。"
        else "有 ${refs.size} 张角色卡正在使用它（${refs.take(3).joinToString("、")}${if (refs.size > 3) "等" else ""}），" +
            "删除后这些卡将不再注入这本书的内容，操作不可恢复。",
        confirmLabel = "删除",
        onConfirm = {
            vm.pendingDeleteId = null
            vm.deleteBook(id)
        },
        onDismiss = { vm.pendingDeleteId = null }
    )
}

/** 世界书编辑页的分组胶囊；键＝[WorldBookEditFields] 的 `onAnchor` 上报的锚点名 */
private val BookSectionPills = listOf(
    "basic" to "基本信息",
    "entries" to "条目",
    "usage" to "使用角色"
)

/**
 * 世界书编辑区：手机端整页内容 / 桌面端第三栏共用。
 * 出口两个：顶栏「保存并返回」按钮，以及返回键/Esc（[WhaleBackHandler]）——有未保存改动先问一句，
 * 干净就直接退回书列表（此前没有返回接线：手机上按返回键会一路退到"再按一次退出鲸鱼"）。
 */
@Composable
internal fun WorldBookEditorArea(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val d = vm.draft
    if (d == null) {
        vm.editingId = null
        return
    }
    var confirmBack by remember { mutableStateOf(false) }
    WhaleBackHandler {
        if (vm.draftDirty()) confirmBack = true else vm.discardEditor()
    }
    // 分组胶囊（用户 2026-09-26：世界书编辑也要像"模型与 API"那样能跳到字段）。
    // 跳转交给 `BookSectionJumper`：锚点偏移由容器自己量、跳转＝直接滚到那个偏移（口径见那里）。
    val jumper = remember { BookSectionJumper() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()

    // 分节胶囊的选中态是一份**显式 state**，不是派生值（用户 2026-10-03 反馈：点「条目」「使用角色」不变蓝）。
    //
    // 旧写法是 `derivedStateOf`：它唯一的快照读是 `scroll.value`，而那行在 `offsetOf` 返回 null 时会被
    // `return@forEach` 跳过——首帧锚点还没量到，三个都 null，于是这一版派生值**一个依赖都没读到**，
    // 之后一直缓存着第一帧算出的「基本信息」。内容确实滚过去了，胶囊还蓝在原地。
    var activeSection by remember { mutableStateOf(BookSectionPills.first().first) }
    // 按点击跳转的那段动画里不跟着滚动换，免得中途闪回上一段
    var jumping by remember { mutableStateOf(false) }

    fun jump(key: String) {
        // 先选中再滚：内容滚没滚动是另一回事，胶囊必须先反映出"点的是哪一段"
        activeSection = key
        val target = jumper.offsetOf(key) ?: return
        jumping = true
        scope.launch {
            scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue))
            jumping = false
        }
    }

    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { v ->
            if (!jumping) {
                var best = activeSection
                var bestGap = Int.MAX_VALUE
                BookSectionPills.forEach { (k, _) ->
                    val off = jumper.offsetOf(k) ?: return@forEach
                    val gap = abs(off - v)
                    if (gap < bestGap) {
                        bestGap = gap
                        best = k
                    }
                }
                activeSection = best
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        val exitEditor = { if (vm.draftDirty()) confirmBack = true else vm.discardEditor() }
        val headerActions: @Composable () -> Unit = {
            TextButton(onClick = { vm.pendingDeleteId = d.bookId }) {
                Text("删除", color = LocalWhalePalette.current.dangerText)
            }
            Button(onClick = { CrashNote.note("宝库 存书=${d.bookName}"); vm.closeEditor() }) {
                Text("保存并返回")
            }
        }
        // 页头照**内嵌页那一套**：桌面第三栏走 [DesktopMainHeader]——标题字号/字重/分割线与「编辑角色」「模型与 API」同源，
        // 且桌面不再画返回箭头（交互语言统一 Esc；出口另有「保存并返回」与左栏切页）；
        // 手机端仍是一整页，顶栏照整页形态（[WhalePageTopBar]），那边的箭头留着。
        if (isDesktopLayout) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                DesktopMainHeader("编辑世界书") { headerActions() }
            }
        } else {
            WhalePageTopBar(
                title = "编辑世界书",
                navigationIcon = { BackArrowButton(onBack = exitEditor) },
                actions = { headerActions() }
            )
        }
        if (isDesktopLayout) {
            // 桌面两段式（原型 col3 的世界书编辑分支）：左窄栏＝导航（分节 ＋ 条目清单 ＋ 添加条目），
            // 右宽栏＝表单 ＋ 每条一张卡 ＋ 使用角色。手机端仍是一整页，别把那条长页改坏。
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.width(216.dp)) {
                    BookSectionPills.forEach { (key, label) ->
                        WhaleChip(
                            selected = activeSection == key,
                            onClick = { jump(key) },
                            label = { Text(label, style = LocalWhaleType.current.caption) },
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (d.bookEntries.isEmpty()) {
                            Text(
                                "还没有条目。加一条，聊天里出现它的关键词时才会注入内容。",
                                style = LocalWhaleType.current.meta,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        d.bookEntries.forEachIndexed { i, e ->
                            BookNavRow(
                                name = e.label().ifBlank { "条目 ${i + 1}" },
                                on = e.enabled,
                                canUp = i > 0,
                                canDown = i < d.bookEntries.lastIndex,
                                onClick = { jump(BookSectionJumper.entryKey(i)) },
                                onMoveUp = { d.moveBookEntry(i, i - 1) },
                                onMoveDown = { d.moveBookEntry(i, i + 1) },
                                onDelete = { d.removeBookEntry(i) }
                            )
                        }
                    }
                    // 添加条目：加完直接滚到新那一条（锚点是布局之后才量到的，等一帧再跳；量不到就退到「条目」段）
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                d.addBookEntry()
                                val last = d.bookEntries.lastIndex
                                scope.launch {
                                    delay(80)
                                    val key = BookSectionJumper.entryKey(last)
                                    if (jumper.offsetOf(key) != null) jump(key) else jump("entries")
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("添加条目", style = LocalWhaleType.current.caption)
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(scroll)
                ) {
                    WorldBookEditorBody(vm, d, jumper, scroll)
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BookSectionPills.forEach { (key, label) ->
                    WhaleChip(
                        // 手机端同样标出"当前停在哪一段"（原来恒为 false，三颗都不亮）
                        selected = activeSection == key,
                        onClick = { jump(key) },
                        label = { Text(label) }
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(vertical = 8.dp)
            ) {
                WorldBookEditorBody(vm, d, jumper, scroll)
            }
        }
    }

    WorldBookDeleteDialog(vm)

    // 有未保存改动时的返回确认（口径与设置页 / 语音页 / 角色编辑页一致）
    if (confirmBack) {
        // 236 弹窗统一：这一处与 241 收编的三处同族，一并走共享确认壳；
        // 手势关闭＝留在页内（同旧 onDismissRequest），只有「放弃更改」钮才不保存退出
        WhaleConfirmSheet(
            title = "保存更改？",
            body = "世界书「${d.bookName.ifBlank { "未命名" }}」有未保存的修改。",
            confirmLabel = "保存并返回",
            onConfirm = { confirmBack = false; vm.closeEditor() },
            dismissLabel = "放弃更改",
            onDismissButton = { confirmBack = false; vm.discardEditor() },
            onDismiss = { confirmBack = false }
        )
    }
}

/**
 * 编辑区的内容本体：基本信息 ＋ 条目 ＋ 使用角色（锚点全在这条链上）。
 * 手机端＝整页单列，桌面端＝两段式的右半——两端共用这一份，不复制第二套字段区。
 */
@Composable
private fun WorldBookEditorBody(
    vm: WorldBookLibraryViewModel,
    d: WorldBookDraft,
    jumper: BookSectionJumper,
    scroll: ScrollState
) {
    // 跳转的零点（口径见 BookSectionJumper）：0 高的占位，量到"内容第一个元素在哪"
    Spacer(Modifier.size(0.dp).then(jumper.anchor(BookSectionJumper.TOP, scroll)))
    WorldBookEditFields(
        state = d,
        currentBook = { d.toBook().takeUnless { it.isEmpty() } },
        onImportBook = { d.importFrom(it) },
        onClear = { d.clearBook() },
        exportFileName = { standaloneBookFileName(d.bookName) },
        anchor = { key -> jumper.anchor(key, scroll) },
        // 桌面端外层那一行已经给了左右留白，这里不再叠一层
        modifier = Modifier.padding(horizontal = if (isDesktopLayout) 0.dp else 16.dp)
    )
    Box(Modifier.then(jumper.anchor("usage", scroll))) {
        WorldBookUsageSection(vm, d)
    }
}

/**
 * 桌面左栏条目清单的一行：书本图标 ＋ 名称 ＋ 开/关。
 *
 * 单击＝跳到右栏那一条（R1：点击只做前进，不开菜单）；右击＝上移／下移／删除，
 * 与宝库那批右击菜单同口径（挂最外层，Initial 阶段就把右键吃掉，内层 clickable 看不到它），
 * 破坏性的删除排在末位（R6）。
 */
@Composable
private fun BookNavRow(
    name: String,
    on: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .desktopSecondaryClick { menu = true }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ⚠ 用 `Filled.AutoStories` 而不是 `AutoMirrored.Filled.MenuBook`：桌面端 material-icons-extended
        // 整支 automirrored 都不存在，打包前的图标裁剪只留"扫得到引用"的类 ⇒ 用后者会 NoClassDefFoundError
        Icon(
            Icons.Filled.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = LocalWhaleType.current.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (on) "开" else "关",
            style = LocalWhaleType.current.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (menu) {
        DesktopCardContextMenu(onDismiss = { menu = false }) {
            if (canUp) {
                DesktopMenuRow("上移", onClick = { menu = false; onMoveUp() })
            }
            if (canDown) {
                DesktopMenuRow("下移", onClick = { menu = false; onMoveDown() })
            }
            DesktopMenuRow("删除条目", danger = true, onClick = { menu = false; onDelete() })
        }
    }
}

/**
 * 「使用这本书的角色」：逐卡开关启用 / 禁用这本书的引用。
 * 反馈原文＝「给任何角色使用」这个功能不完善——此前挂书只能进各卡的编辑器里操作，
 * 现在从书这一侧也能管理。一张卡同时只能挂一本书：给已挂了别的书的卡打开开关＝换书，
 * 所以那行要把"当前挂的是哪本"标出来，别让用户糊里糊涂换掉。
 *
 * 分节形状（2026-10-08 用户反馈后统一）：标题在卡外、卡内是逐卡开关，两端同一份——
 * 旧写法桌面是「等宽小标签 ＋ `WhalePanelCard`」、手机是「裸页 ＋ titleSmall 标题」，
 * 两者都不是行表式，也与上一段（基本信息）长得不一样。
 */
@Composable
private fun WorldBookUsageSection(vm: WorldBookLibraryViewModel, d: WorldBookDraft) {
    Column(Modifier.padding(horizontal = if (isDesktopLayout) 0.dp else 16.dp)) {
        WhaleFieldGroup(label = "使用角色") {
            UsageCardBody(vm, d)
        }
    }
}

/**
 * 「使用角色」的本体：搜索 ＋ 分类 ＋ 逐卡开关（桌面卡内 / 手机摊开，两端共用这一份）。
 *
 * 桌面端把搜索框换成描边输入区、把每行开关缩到 38×22 那档——原型的 `.sw` 就是这个尺寸，
 * M3 的 `Switch` 是 52×32，一列五行时宽度差得很显眼。
 */
@Composable
private fun UsageCardBody(vm: WorldBookLibraryViewModel, d: WorldBookDraft) {
    if (vm.cards.isEmpty()) {
        Text(
            "还没有角色卡。先去「角色」段导入或创建一张。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 13.dp)
        )
    } else {
        // 搜索＋分类（2026-09-25 用户反馈）：卡多了以后逐卡开关要能定位到人。
        // 框走 v10 §三「标签在框上方」——旧的两副（桌面 WhaleHairField 的 hairline 、
        // 手机 M3 浮起标签）都不属于这一页的语法，也是两段分节长得不一样的最后一处。
        WhaleFieldBlock(
            label = "搜索",
            value = vm.usageQuery,
            onValueChange = { vm.usageQuery = it },
            placeholder = "角色名"
        )
        val usageCatScroll = rememberLazyListState()
        LazyRow(
            state = usageCatScroll,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .wheelHorizontalScroll(usageCatScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                WhaleChip(
                    selected = vm.usageCategory == "全部",
                    onClick = { vm.usageCategory = "全部" },
                    label = { Text("全部") }
                )
            }
            items(CategoryManager.all()) { cat ->
                WhaleChip(
                    selected = vm.usageCategory == cat,
                    onClick = { vm.usageCategory = cat },
                    label = { Text(cat) }
                )
            }
        }
        if (vm.usageCards.isEmpty()) {
            Text(
                "没有匹配「${vm.usageQuery.trim()}」的角色卡",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 13.dp)
            )
        }
    }
    vm.usageCards.forEachIndexed { i, c ->
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        c.name.ifBlank { "未命名" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (c.worldBookId != null && c.worldBookId != d.bookId) {
                        Text(
                            "当前挂的是「" + (vm.books.firstOrNull { it.id == c.worldBookId }
                                ?.book?.name?.ifBlank { "未命名" } ?: "另一本世界书") + "」，打开开关会换成这本书",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Switch(
                    checked = c.worldBookId == d.bookId,
                    onCheckedChange = { on ->
                        CrashNote.note("宝库 卡书=${c.name}→$on")
                        vm.setCardBook(c.id, if (on) d.bookId else null)
                    },
                    modifier = if (isDesktopLayout) Modifier.scale(0.72f) else Modifier
                )
            }
            // 行间发丝两端一致（卡内末行不发，卡底不留悬空线）
            if (i < vm.usageCards.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * 桌面端第三栏：世界书段。
 *
 * **没选中书时不是空态，而是世界书页**（原型 `wbPageD`）：标题 ＋ 一句说明 ＋ 柜格 ＋ 名册 ＋ 底部双钮。
 * 用户 2026-10-03 反馈「没选书时主栏目还是空着的」——原型这一栏本来就摆书架，没书可选时给人看的就是书架本身；
 * 选中书之后整块换成编辑器（[WorldBookEditorArea]），与原型 `S.tbook ? 编辑器 : wbPageD()` 一一对应。
 */
@Composable
internal fun WorldBookLibraryPane(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    if (vm.editingId == null) WorldBookDesktopPage(vm, modifier) else WorldBookEditorArea(vm, modifier)
}

/**
 * 桌面第三栏的世界书页（原型 `wbPageD`）：标题 ＋ 说明 ＋ 柜格（带翻页箭头）＋「名册 · N 本」书行 ＋ 导入 / 新建。
 *
 * 手机端同一块在 [WorldBookLibraryList]（头是「共 N 本 / 导入 / 新建」、柜格不带箭头），
 * 两份只差头尾排布，柜格与名册行本身共用。
 */
@Composable
private fun WorldBookDesktopPage(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    val pickBook = rememberJsonFilePicker { text -> text?.let { vm.importBook(it) } }
    val colors = MaterialTheme.colorScheme
    val type = LocalWhaleType.current
    // 被右键的那本书（菜单锚在这一行自己里，理由见名册行那段说明）
    var menuBookId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
    ) {
        // 主栏标题（236 形式统一）：字体/内距/分割线全按发现页；右侧字段＝本页的书数
        DesktopMainHeader("世界书") {
            Text(
                "${vm.books.size} 本",
                style = type.meta,
                color = colors.onSurfaceVariant
            )
        }
        // 说明行（原型 `wbPageD` 的 `.st`）：这一页是"柜格＋名册"两块，写清楚怎么翻、怎么开。
        // ⚠ 文案按**本项目的真实模型**写：原型那句"一本书同时只能挂一张卡"是旧口径，
        // 与名册行的"被 N 张卡使用"（一本书可被多张卡引用）自相矛盾，不照抄。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "上面是柜格，左右箭头翻页；点书脊或下面名册都能打开编辑。" +
                    "一本书可以被多张角色卡引用，没有任何卡引用的书不会注入内容。",
                style = type.tiny,
                letterSpacing = 0.06.sp,
                color = colors.onSurfaceVariant
            )
        }
        if (vm.books.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "还没有世界书",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "世界书是账号级的背景资料库：一本书可以被多张角色卡引用。" +
                        "可以从 SillyTavern 的世界书 JSON 导入，或新建一本从零写。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { pickBook() }) { Text("导入世界书 JSON") }
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "spines") {
                BookSpineRail(
                    books = vm.books.map { (it.book.name.ifBlank { "未命名" } to it.book.entries.size) },
                    onOpen = { idx ->
                        val f = vm.books.getOrNull(idx) ?: return@BookSpineRail
                        CrashNote.note("宝库 改书=${f.book.name}")
                        vm.openEditor(f.id)
                    },
                    withArrows = true,
                    // 桌面不做拖动排序（用户 2026-10-03：箭头＝翻页浏览，与分类标签行同款语义）；
                    // 长按拖动排序只留给手机
                    reorderable = false
                )
            }
            item(key = "rh") {
                RosterSectionHeader(label = "名册", count = vm.books.size, countLabel = "· {n} 本")
            }
            items(vm.books, key = { it.id }) { f ->
                val bookName = f.book.name.ifBlank { "未命名" }
                Box {
                    WorldBookRosterRow(
                        title = bookName,
                        entryCount = f.book.entries.size,
                        usedBy = vm.referringCards(f.id),
                        onOpen = { CrashNote.note("宝库 改书=$bookName"); vm.openEditor(f.id) },
                        onDelete = { vm.pendingDeleteId = f.id },
                        onMenu = { menuBookId = f.id }
                    )
                    // 菜单锚在这一行自己里（用户 2026-10-03 反馈 ⑤/⑥）：贴着被右击的那本书弹
                    if (menuBookId == f.id) {
                        DesktopCardContextMenu(
                            onDismiss = { menuBookId = null },
                            width = 150.dp
                        ) {
                            DesktopMenuRow(
                                label = "编辑世界书",
                                onClick = {
                                    menuBookId = null
                                    CrashNote.note("宝库 改书=$bookName")
                                    vm.openEditor(f.id)
                                }
                            )
                            DesktopMenuRow(
                                label = "删除世界书…",
                                danger = true,
                                onClick = {
                                    menuBookId = null
                                    vm.pendingDeleteId = f.id
                                }
                            )
                        }
                    }
                }
            }
            // 底部并排双钮（原型 `.btn` / `.btn pri`）：导入是次动作、新建是主动作，右边那个才给主色
            item(key = "acts") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { pickBook() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.UploadFile, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导入 JSON")
                    }
                    Button(
                        onClick = { CrashNote.note("宝库 新建书"); vm.createBook() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("新建世界书")
                    }
                }
            }
        }
    }

    // 删除确认与第二栏索引、第三栏编辑页共用一份
    WorldBookDeleteDialog(vm)
}

/**
 * 第二栏索引的一行（原型 col2 的 `srow`）：一枚书本图标 ＋ 名称 ＋ 条数。
 *
 * 选中态＝正在第三栏编辑的那本（`secondaryContainer` 底 ＋ 半粗），写法与分类栏 [CategoryRailRow] 同一套；
 * 右击出菜单（编辑 / 删除），常驻不放垃圾桶钮——理由见 [WorldBookRosterRow]。
 */
@Composable
private fun WorldBookIndexRow(
    name: String,
    entryCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> colors.secondaryContainer
                    hovered -> colors.surfaceContainerHighest
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            // 右键菜单挂最外层：Initial 阶段消费掉右键，内层 clickable 就看不到它（与名册行同理）
            .desktopSecondaryClick(onMenu)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ⚠ 用 `Filled.AutoStories` 而不是 `AutoMirrored.Filled.MenuBook`：桌面端 material-icons-extended
        // 整支 automirrored 都不存在，打包前的图标裁剪只留"扫得到引用"的类 ⇒ 用后者会 NoClassDefFoundError
        Icon(
            Icons.Filled.AutoStories,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (selected) colors.onSecondaryContainer.copy(alpha = 0.85f)
            else colors.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$entryCount",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }
}
