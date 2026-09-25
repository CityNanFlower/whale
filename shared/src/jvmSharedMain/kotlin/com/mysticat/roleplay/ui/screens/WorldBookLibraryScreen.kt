package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEngine
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.WorldBookFile
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.WorldBookEditFields
import com.mysticat.roleplay.ui.WorldBookEditState
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.rememberJsonFilePicker
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.standaloneBookFileName
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    modifier: Modifier = Modifier
) {
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    }
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
        extraRaw = bookExtraRaw
    )
}

/**
 * 宝库页的世界书段：账号级书列表 ＋ 独立导入导出 ＋ 删除（引用检查）＋ 点开改书。
 * 手机端与桌面端共用这一个 VM；书文件是唯一事实来源，界面列表只是它的内存镜像
 * （写走 [Repository] 的写入队列，落盘后靠下次 refresh 对齐）。
 */
class WorldBookLibraryViewModel : ViewModel() {
    var books by mutableStateOf<List<WorldBookFile>>(emptyList())
        private set
    /** bookId → 引用它的卡名列表（删除确认与行内"被 N 张卡使用"都用它） */
    var referring by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set

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
                list to list.associate { f -> f.id to Repository.worldBookReferringCards(f.id) }
            }
            books = pair.first
            referring = pair.second
        }
    }

    fun referringCards(bookId: String): List<String> = referring[bookId].orEmpty()

    fun openEditor(bookId: String) {
        val f = books.firstOrNull { it.id == bookId } ?: return
        draft = WorldBookDraft(bookId, f.book)
        editingId = bookId
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
        val book = WorldBook()
        Repository.saveWorldBook(id, book, onFailure)
        upsertInMemory(id, book)
        draft = WorldBookDraft(id, book)
        editingId = id
    }

    private fun removeInMemory(bookId: String) {
        books = books.filterNot { it.id == bookId }
        referring = referring - bookId
    }

    private fun upsertInMemory(bookId: String, book: WorldBook) {
        books = (books.filterNot { it.id == bookId } + WorldBookFile(bookId, book))
            .sortedBy { it.book.name }
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
    LaunchedEffect(Unit) { vm.refresh() }

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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.books, key = { it.id }) { f ->
                    WorldBookRow(
                        file = f,
                        referring = vm.referringCards(f.id),
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
@Composable
private fun WorldBookRow(
    file: WorldBookFile,
    referring: List<String>,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    file.book.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append("${file.book.entries.size} 条")
                        append(" · ")
                        append(
                            if (referring.isEmpty()) "没有被引用"
                            else "被 ${referring.size} 张卡使用"
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 桌面端第二栏：世界书段的书列表（与分类列表同一套行观感）＋ 导入 / 新建 */
@Composable
internal fun WorldBookRail(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { vm.refresh() }
    val pickBook = rememberJsonFilePicker { text -> text?.let { vm.importBook(it) } }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "世界书",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${vm.books.size} 本",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.books, key = { it.id }) { f ->
                CategoryRailRow(
                    label = f.book.name.ifBlank { "未命名" },
                    count = f.book.entries.size,
                    selected = vm.editingId == f.id,
                    onClick = { CrashNote.note("宝库 改书=${f.book.name}"); vm.openEditor(f.id) }
                )
            }
            item {
                RailActionRow(
                    label = "导入世界书 JSON",
                    icon = { Icon(Icons.Filled.UploadFile, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant) },
                    onClick = { pickBook() }
                )
            }
            item {
                RailActionRow(
                    label = "新建空书",
                    icon = { Icon(Icons.Filled.Add, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant) },
                    onClick = { CrashNote.note("宝库 新建书"); vm.createBook() }
                )
            }
        }
    }

    // 删除确认与手机端共用一份（列表行上不放删除钮，删书入口在第三栏编辑页里）
    WorldBookDeleteDialog(vm)
}

/** 第二栏里的一行动作（与「管理分类」行同款） */
@Composable
private fun RailActionRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
    AlertDialog(
        onDismissRequest = { vm.pendingDeleteId = null },
        title = { Text("删除世界书「${f.book.name.ifBlank { "未命名" }}」？") },
        text = {
            Text(
                if (refs.isEmpty()) "没有任何角色卡引用这本书，删除后不可恢复。"
                else "有 ${refs.size} 张角色卡正在使用它（${refs.take(3).joinToString("、")}${if (refs.size > 3) "等" else ""}），" +
                    "删除后这些卡将不再注入这本书的内容，操作不可恢复。"
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.pendingDeleteId = null
                vm.deleteBook(id)
            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { vm.pendingDeleteId = null }) { Text("取消") }
        }
    )
}

/**
 * 世界书编辑区：手机端整页内容 / 桌面端第三栏共用。
 * 「保存并返回」是唯一出口——书是账号级实体，改的就是文件本身，没有"放弃更改"的中间态。
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
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "编辑世界书",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.pendingDeleteId = d.bookId }) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { CrashNote.note("宝库 存书=${d.bookName}"); vm.closeEditor() }) {
                Text(if (isDesktopLayout) "保存" else "保存并返回")
            }
        }
        HorizontalDivider()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            WorldBookEditFields(
                state = d,
                currentBook = { d.toBook().takeUnless { it.isEmpty() } },
                onImportBook = { d.importFrom(it) },
                onClear = { d.clearBook() },
                exportFileName = { standaloneBookFileName(d.bookName) },
                modifier = Modifier.padding(horizontal = if (isDesktopLayout) 20.dp else 16.dp)
            )
        }
    }

    WorldBookDeleteDialog(vm)
}

/** 桌面端第三栏：世界书段（没选中书时给引导空态） */
@Composable
internal fun WorldBookLibraryPane(
    vm: WorldBookLibraryViewModel,
    modifier: Modifier = Modifier
) {
    if (vm.editingId == null) {
        Column(
            modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Text(
                "在左侧选择一本书开始编辑",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "世界书是账号级的背景资料库：一本书可以被多张角色卡引用；" +
                    "卡里带的世界书会在导入时自动抽出来存到这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        WorldBookEditorArea(vm, modifier)
    }
}
