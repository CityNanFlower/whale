package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.ui.desktopSecondaryClick
import com.mysticat.roleplay.ui.wheelHorizontalScroll
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.FormFilter
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.RemoteFetch
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.rememberJsonFilePicker
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.positionChange

class CharacterListViewModel : ViewModel() {
    var allCharacters by mutableStateOf<List<CharacterCard>>(emptyList())
        private set
    var query by mutableStateOf("")
    var category by mutableStateOf("全部")

    /**
     * 形态筛选：[FormFilter.ALL] ＝ 全部；其余取值＝[EngineSpec.formTag]。
     * 口径与"引擎怎么认这张卡"共用一份（见 [FormFilter]）——不认识的取值归陪伴，不会"卡不见了"。
     */
    var formFilter by mutableStateOf(FormFilter.ALL)

    /** 库里存在非陪伴形态的卡时，筛选条才出现（只有一种形态时它是纯噪声） */
    val showFormFilter: Boolean
        get() = FormFilter.worthShowing(allCharacters)
    var showSearch by mutableStateOf(false)
    var showAddDialog by mutableStateOf(false)
    /**
     * 导入前的合规确认弹窗（只在**还没勾选过年龄声明**时出现一次）。
     * 待导入的卡文本暂存在 [pendingCardText]，用户同意后接着走完同一条落库路径。
     */
    var showCompliance by mutableStateOf(false)
        private set
    private var pendingCardText: String? = null
    var notice by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * 分类 chips 的数据源。放在 VM 而不是页面里的 `remember`：**导入会把卡带的新类型自动登记**
     * （`Repository.saveCharacter` 里的 `CategoryManager.ensure`），页面那层记不住这件事——
     * 导入完成后不重读，新类型要等下次进页面才出现（用户 2026-09-21 反馈的正是"类型没进列表"）。
     */
    var categories by mutableStateOf(CategoryManager.all())
        private set

    /** 分类增删或自动登记之后重读一次（管理分类弹窗关闭时、导入落库后） */
    fun reloadCategories() {
        categories = CategoryManager.all()
    }

    /**
     * 按分类 + 搜索（名称/人设/场景/简介 关键词）过滤后的角色。
     *
     * 原来是 getter，每次访问都全量 filter 一遍，而组合期一次重组里要读它 4 次
     * （空态判断、列表、计数、快照）。改用 `derivedStateOf` + 私有状态缓存，
     * 只有 allCharacters / query / category 变化才重算。
     */
    private val filtered = derivedStateOf { computeFiltered() }

    val characters: List<CharacterCard>
        get() = filtered.value

    private fun computeFiltered(): List<CharacterCard> = allCharacters.filter { ch ->
            val q = query.trim()
            val matchCat = category == "全部" || category in ch.categoriesOrDefault()
            // 形态筛选：库里只有一种形态时**整条筛选不生效** —— 否则一旦那个值留在状态里
            // （比如工具卡都被删了），列表会莫名其妙空掉
            val matchForm = !showFormFilter || FormFilter.matches(ch, formFilter)
            val matchQ = q.isEmpty() ||
                ch.name.contains(q, true) ||
                // 人设检索走合并文本：分层字段不并在 persona 里，只查 persona 会"搜不到自己的卡"
                ch.personaDisplayText().contains(q, true) ||
                ch.scenario.contains(q, true) ||
                ch.tagline.contains(q, true)
            matchCat && matchForm && matchQ
        }

    /**
     * 列表空时的解释文案（三种筛选各说各的）。
     * 原来一律写"该分类下还没有角色"——加了形态筛选之后那句话会**指错原因**，
     * 用户会去翻分类，而真正挡住卡的是形态。
     */
    fun emptyHint(): String = when {
        query.isNotBlank() -> "没有匹配「${query.trim()}」的角色"
        showFormFilter && formFilter != FormFilter.ALL ->
            "还没有「${Engines.of(formFilter).label}」形态的角色卡"
        else -> "该分类下还没有角色"
    }

    // 原来这里 `init { refresh() }`，而页面里还有一个 `LaunchedEffect(Unit) { vm.refresh() }`
    // （用途是"每次回到角色列表都重新读"）——首次进入于是**读两遍磁盘**。
    // refresh() 本身就是异步的（扫描在 IO 线程），页面首次组合时 LaunchedEffect 一定会跑到，
    // 所以只保留页面那一处即可（顺带覆盖"从别处回到列表"的刷新）。

    // 已知问题修复：角色文件扫描移出主线程（角色/会话多了之后进列表页不再卡顿）。
    // 注意 Compose snapshot 状态只能在主线程写：IO 里只读取，赋值回 Main
    fun refresh() {
        viewModelScope.launch {
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Repository.listCharacters()
            }
            allCharacters = list
            // 顺手补齐库里旧卡带的类型（自动登记之前导入的卡没登记过，见 CategoryManager.ensureFromCards）——
            // 列表已经在内存里，这一步不额外读盘；没有新类型就不落盘
            CategoryManager.ensureFromCards(list)
            reloadCategories()
        }
    }

    fun toggleSearch() {
        showSearch = !showSearch
        if (!showSearch) query = ""
    }

    /** 导入 Tavern/SillyTavern 角色卡（JSON 或嵌了卡数据的 PNG，见 CardImport.cardJsonFromBytes） */
    fun importTavernJson(text: String?) {
        if (text.isNullOrBlank()) {
            error = "无法读取所选文件"
            return
        }
        saveCard(text)
    }

    /**
     * 网址导入（从发现页挪到"添加角色卡"，用户口径：做卡入口统一在角色卡页）：
     * 拉字节 → 抽卡 JSON → 与文件导入同一条落库路径。直链由本机直接下载，不经过中间服务器。
     */
    var urlText by mutableStateOf("")
    var urlImporting by mutableStateOf(false)
        private set

    fun importFromUrl() {
        if (urlImporting) return
        val target = urlText.trim()
        if (target.isBlank()) {
            error = "先粘贴一个角色卡直链（PNG 或 JSON）"
            return
        }
        urlImporting = true
        viewModelScope.launch {
            try {
                val text = CardImport.cardJsonFromBytes(RemoteFetch.fetchBytes(target))
                    ?: throw IllegalStateException("链接指向的不是角色卡（PNG 里没有卡数据，或不是 JSON）")
                showAddDialog = false
                urlText = ""
                saveCard(text)
            } catch (t: Throwable) {
                error = t.message ?: "导入失败"
            } finally {
                urlImporting = false
            }
        }
    }

    private fun saveCard(text: String) {
        // 合规确认：还没勾过年龄声明就先弹一次（文案与「用户 › 关于鲸鱼」同源），
        // 同意后落盘再接着导入。**文件与网址两条入口都汇到这里**，所以门只把这一处即可。
        if (!Repository.complianceAccepted()) {
            pendingCardText = text
            showCompliance = true
            return
        }
        saveCardNow(text)
    }

    /** 同意年龄与自愿声明：记下来，并把刚才拦住的那张卡接着导完 */
    fun acceptCompliance() {
        Repository.setComplianceAccepted(true)
        showCompliance = false
        val pending = pendingCardText
        pendingCardText = null
        if (pending != null) saveCardNow(pending)
    }

    /** 取消导入：丢掉暂存的卡文本（用户没同意就什么都不做） */
    fun dismissCompliance() {
        showCompliance = false
        pendingCardText = null
    }

    private fun saveCardNow(text: String) {
        viewModelScope.launch {
            try {
                val card = CardImport.fromTavernJson(text)
                // 写入是异步的：失败回调在写入线程上触发，切回主线程提示
                Repository.saveCharacter(card) { err ->
                    viewModelScope.launch { error = "导入保存失败：${err.message ?: "磁盘写入异常"}" }
                }
                refresh()
                reloadCategories()
                notice = "已导入「${card.name}」，点击即可开始聊天"
            } catch (t: Throwable) {
                error = t.message ?: "导入失败"
            }
        }
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
fun CharacterListScreen(
    onOpenChat: (CharacterCard) -> Unit,
    onStartNewChat: (CharacterCard) -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onAiCreate: () -> Unit = {},
    onSettings: () -> Unit,
    /** 世界书管理：编辑器跳来的待消费请求（= 书 id，空串 = 只切段） */
    pendingWorldBook: String? = null,
    onWorldBookOpenDone: () -> Unit = {},
    vm: CharacterListViewModel = viewModel(factory = noArgViewModelFactory { CharacterListViewModel() })
) {
    val pickImport = rememberJsonFilePicker { text -> vm.importTavernJson(text) }
    // 三段壳：「角色卡」tab 扩容为 角色｜世界书（｜故事），页内顶部切换
    val bookVm = rememberWorldBookLibraryViewModel()
    var segment by remember { mutableStateOf(LibrarySegment.CHARACTERS) }

    // 世界书管理：消费编辑器跳来的请求——切段到世界书，带了书 id 就直接打开编辑
    LaunchedEffect(pendingWorldBook) {
        if (pendingWorldBook != null) {
            segment = LibrarySegment.WORLD_BOOKS
            if (pendingWorldBook.isNotBlank()) bookVm.openEditor(pendingWorldBook)
            onWorldBookOpenDone()
        }
    }

    var showManageCategories by remember { mutableStateOf(false) }
    // 点角色头像 → 弹出该角色的全部会话
    var convTarget by remember { mutableStateOf<CharacterCard?>(null) }
    // 问题 #11：点卡片主体先看角色故事，而不是直接续聊
    var storyTarget by remember { mutableStateOf<CharacterCard?>(null) }

    // 每次回到角色列表都重新读取（refresh 里连分类一起重读：发现页那边导入的卡可能刚带进新类型）
    LaunchedEffect(Unit) {
        vm.refresh()
    }

    // 世界书镜像的刷新只挂段入口：跳转直达书编辑器时列表组件不组合，
    // 把 load 挂在它上面会让镜像恒空（删除确认弹窗读镜像、找不到书就静默消失）
    LaunchedEffect(segment) {
        if (segment == LibrarySegment.WORLD_BOOKS) bookVm.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("宝库") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (segment == LibrarySegment.CHARACTERS) {
                        IconButton(onClick = { CrashNote.note("角色卡页 搜索"); vm.toggleSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { CrashNote.note("角色卡页 管理分类"); showManageCategories = true }) {
                            Icon(Icons.Filled.Category, contentDescription = "管理分类")
                        }
                    }
                    IconButton(onClick = { CrashNote.note("角色卡页 设置"); onSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            if (segment == LibrarySegment.CHARACTERS) {
                FloatingActionButton(onClick = { vm.showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加角色卡")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LibrarySegmentRow(selected = segment, onSelect = { segment = it })

            if (segment == LibrarySegment.WORLD_BOOKS) {
                // 世界书段：列表与编辑器同区切换（段内"前进"，不走路由）
                if (bookVm.editingId == null) {
                    WorldBookLibraryList(bookVm, Modifier.fillMaxSize())
                } else {
                    WorldBookEditorArea(bookVm, Modifier.fillMaxSize())
                }
                return@Column
            }

            if (vm.showSearch) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索名称或描述…") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { vm.query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                )
            }

            CategoryChips(
                selected = vm.category,
                onSelect = { CrashNote.note("角色卡页 分类=$it"); vm.category = it },
                categories = vm.categories
            )

            // 形态筛选：与分类是两个正交维度，所以另起一行、不塞进分类那条
            FormFilterChips(
                vm = vm,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )

            val cards = vm.characters
            when {
                vm.allCharacters.isEmpty() -> EmptyCharacters(
                    onAdd = { vm.showAddDialog = true },
                    modifier = Modifier.fillMaxSize()
                )
                cards.isEmpty() -> NotFound(
                    modifier = Modifier.fillMaxSize(),
                    text = vm.emptyHint()
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "共 ${cards.size} 个角色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(cards, key = { it.id }) { card ->
                        CharacterRow(
                            card = card,
                            onEdit = { onEdit(card.id) },
                            onShowConversations = { convTarget = card },
                            onShowStory = { storyTarget = card }
                        )
                    }
                }
            }
        }
    }

    // 点卡片主体：角色故事（问题 #11）
    storyTarget?.let { card ->
        CharacterStoryDialog(
            card = card,
            onContinue = {
                storyTarget = null
                onOpenChat(card)
            },
            onConversations = {
                storyTarget = null
                convTarget = card
            },
            onNew = {
                storyTarget = null
                onStartNewChat(card)
            },
            onEdit = {
                storyTarget = null
                onEdit(card.id)
            },
            onDismiss = { storyTarget = null }
        )
    }

    // 点头像：该角色的全部会话
    convTarget?.let { card ->
        CharacterConversationsDialog(
            card = card,
            onOpen = { convId ->
                convTarget = null
                onOpenConversation(card.id, convId)
            },
            onNew = {
                convTarget = null
                onStartNewChat(card)
            },
            onDismiss = { convTarget = null }
        )
    }

    // 导入前的合规确认（只在还没勾选过年龄声明时出现；文案与「关于鲸鱼」同源）
    if (vm.showCompliance) {
        ComplianceImportDialog(onAccept = vm::acceptCompliance, onDismiss = vm::dismissCompliance)
    }

    // 添加方式选择
    if (vm.showAddDialog) {
        AddCharacterDialog(
            onDismiss = { vm.showAddDialog = false },
            onCreate = {
                vm.showAddDialog = false
                onNew()
            },
            onAiCreate = {
                vm.showAddDialog = false
                onAiCreate()
            },
            onImport = {
                vm.showAddDialog = false
                pickImport()
            },
            urlText = vm.urlText,
            urlImporting = vm.urlImporting,
            onUrlTextChange = { vm.urlText = it },
            onImportUrl = vm::importFromUrl
        )
    }

    // 管理自定义分类（增/删/排序）
    if (showManageCategories) {
        ManageCategoriesDialog(
            onDismiss = { showManageCategories = false },
            onChanged = { vm.reloadCategories() }
        )
    }

    // 导入成功提示
    vm.notice?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearNotice,
            title = { Text("导入成功") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = vm::clearNotice) { Text("好的") }
            }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

@Composable
private fun CategoryChips(
    selected: String,
    onSelect: (String) -> Unit,
    categories: List<String>
) {
    val categoryScroll = rememberLazyListState()
    LazyRow(
        state = categoryScroll,
        modifier = Modifier.wheelHorizontalScroll(categoryScroll),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            WhaleChip(selected = selected == "全部", onClick = { onSelect("全部") }, label = { Text("全部") })
        }
        items(categories) { c ->
            WhaleChip(selected = selected == c, onClick = { onSelect(c) }, label = { Text(c) })
        }
    }
}

/**
 * 形态筛选条：全部 / 陪伴 / 多线 / 工具。
 *
 * 用 `FlowRow` 而不是分类那条 `LazyRow`：取值固定四个，但**桌面第二栏能拖窄到 208dp**，
 * 四颗胶囊在窄栏里要能折行（分类条是可滑动的一行，这里不是）。
 * 库里只有一种形态时整条不渲染（见 [CharacterListViewModel.showFormFilter]）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FormFilterChips(vm: CharacterListViewModel, modifier: Modifier = Modifier) {
    if (!vm.showFormFilter) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FormFilter.options.forEach { (tag, label) ->
            WhaleChip(
                selected = vm.formFilter == tag,
                onClick = { CrashNote.note("角色卡页 形态=$label"); vm.formFilter = tag },
                label = { Text(label) }
            )
        }
    }
}

/**
 * 桌面三栏版角色卡：**第二栏**（分类 + 搜索 + 管理分类入口）。
 *
 * 与手机端 [CategoryChips] 的差别：手机是横向 chip 条（手指横向滑），桌面是纵向列表
 * （鼠标扫一列比横着找一个还快的多），且每个分类带**角色数量**——这是桌面多出来的空间
 * 该承担的信息（用户方案 2.2）。
 *
 * 状态来源与第三栏的网格**共用同一个 ViewModel**：搜索框绑 `vm.query`、分类绑 `vm.category`，
 * 所以这一栏改了条件，右边网格立刻跟着变，不需要返回值回调。
 */
@Composable
internal fun CharacterCategoryRail(
    vm: CharacterListViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var showManage by remember { mutableStateOf(false) }
    val all = vm.allCharacters
    // 桌面这一栏会随 tab 切换重新组合，但 VM 比它活得久（分类状态在 VM 里）⇒ 进来时重读一次
    LaunchedEffect(Unit) { vm.reloadCategories() }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "角色卡",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${all.size} 张",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            placeholder = { Text("搜索角色名 / 人设…", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(16.dp)) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.query = "" }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "清空", Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.size(6.dp))
        // 形态筛选：桌面这一栏窄（能拖到 208dp），所以与搜索框之间留一点间距、让它自己折行
        FormFilterChips(vm = vm, modifier = Modifier.padding(horizontal = 10.dp))
        Spacer(Modifier.size(6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                CategoryRailRow(
                    label = "全部",
                    count = all.size,
                    selected = vm.category == "全部",
                    onClick = { CrashNote.note("角色卡页 分类=全部"); vm.category = "全部" }
                )
            }
            items(vm.categories) { c ->
                CategoryRailRow(
                    label = c,
                    count = all.count { c in it.categoriesOrDefault() },
                    selected = vm.category == c,
                    onClick = { CrashNote.note("角色卡页 分类=$c"); vm.category = c }
                )
            }
            // 分类的增删改排序收进这一栏（原来在角色卡页右上角的图标里）
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showManage = true }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "管理分类",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showManage) {
        ManageCategoriesDialog(
            onDismiss = { showManage = false },
            onChanged = { vm.reloadCategories() }
        )
    }
}

/** 第二栏里的一行分类（名称 + 数量，选中高亮）；世界书段的书列表也用它 */
@Composable
internal fun CategoryRailRow(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
        )
    }
}

/**
 * 桌面三栏版角色卡：**第三栏**（角色网格）。
 *
 * 用户方案 3.2：卡片改网格（宽度自适应 2–3 列）、「+ 新建角色」放内容区右上角（替代 FAB）、
 * 双击卡片直接开聊、右键弹操作菜单。手机端仍是原来的纵向列表 + FAB。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun CharacterGridPane(
    vm: CharacterListViewModel,
    onOpenChat: (CharacterCard) -> Unit,
    onStartNewChat: (CharacterCard) -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onAiCreate: () -> Unit
) {
    val pickImport = rememberJsonFilePicker { text -> vm.importTavernJson(text) }
    var storyTarget by remember { mutableStateOf<CharacterCard?>(null) }
    var convTarget by remember { mutableStateOf<CharacterCard?>(null) }
    // 右键菜单的目标卡片（桌面独有：右键 = 操作菜单，与长按等价）
    var menuTarget by remember { mutableStateOf<CharacterCard?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    val cards = vm.characters
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (vm.category == "全部") "全部角色" else vm.category,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${cards.size} 个",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建角色")
            }
        }

        when {
            vm.allCharacters.isEmpty() -> EmptyCharacters(
                onAdd = { vm.showAddDialog = true },
                modifier = Modifier.fillMaxSize()
            )
            cards.isEmpty() -> NotFound(
                modifier = Modifier.fillMaxSize(),
                text = vm.emptyHint()
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)
            ) {
                gridItems(cards, key = { it.id }) { card ->
                    Box(Modifier.padding(end = 14.dp, bottom = 14.dp)) {
                        CharacterGridCard(
                            card = card,
                            onStory = { CrashNote.note("角色卡 看故事=${card.name}"); storyTarget = card },
                            onOpenChat = { onOpenChat(card) },
                            onMenu = { menuTarget = card }
                        )
                    }
                }
            }
        }
    }

    // 右键菜单（桌面）：双击开聊之外的常用操作都放这里，不占卡片面积
    menuTarget?.let { card ->
        DropdownMenu(expanded = true, onDismissRequest = { menuTarget = null }) {
            DropdownMenuItem(
                text = { Text("查看角色故事") },
                onClick = { menuTarget = null; storyTarget = card }
            )
            DropdownMenuItem(
                text = { Text("继续上次会话") },
                onClick = { menuTarget = null; onOpenChat(card) }
            )
            DropdownMenuItem(
                text = { Text("新建会话") },
                onClick = { menuTarget = null; onStartNewChat(card) }
            )
            DropdownMenuItem(
                text = { Text("全部会话…") },
                onClick = { menuTarget = null; convTarget = card }
            )
            DropdownMenuItem(
                text = { Text("编辑角色卡") },
                onClick = { menuTarget = null; onEdit(card.id) }
            )
        }
    }

    storyTarget?.let { card ->
        CharacterStoryDialog(
            card = card,
            onContinue = { storyTarget = null; onOpenChat(card) },
            onConversations = { storyTarget = null; convTarget = card },
            onNew = { storyTarget = null; onStartNewChat(card) },
            onEdit = { storyTarget = null; onEdit(card.id) },
            onDismiss = { storyTarget = null }
        )
    }

    convTarget?.let { card ->
        CharacterConversationsDialog(
            card = card,
            onOpen = { convId -> convTarget = null; onOpenConversation(card.id, convId) },
            onNew = { convTarget = null; onStartNewChat(card) },
            onDismiss = { convTarget = null }
        )
    }

    if (vm.showCompliance) {
        ComplianceImportDialog(onAccept = vm::acceptCompliance, onDismiss = vm::dismissCompliance)
    }

    if (vm.showAddDialog) {
        AddCharacterDialog(
            onDismiss = { vm.showAddDialog = false },
            onCreate = { vm.showAddDialog = false; onNew() },
            onAiCreate = { vm.showAddDialog = false; onAiCreate() },
            onImport = { vm.showAddDialog = false; pickImport() },
            urlText = vm.urlText,
            urlImporting = vm.urlImporting,
            onUrlTextChange = { vm.urlText = it },
            onImportUrl = vm::importFromUrl
        )
    }

    vm.notice?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::clearNotice,
            title = { Text("导入成功") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::clearNotice) { Text("好的") } }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

/** 网格里的一张角色卡：头像在上，名字 + 一行标签在下（用户方案 3.2） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CharacterGridCard(
    card: CharacterCard,
    onStory: () -> Unit,
    onOpenChat: () -> Unit,
    onMenu: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (hovered) colors.surfaceContainerHighest else colors.surface)
            .border(
                1.dp,
                if (hovered) colors.primary.copy(alpha = 0.45f) else colors.outlineVariant.copy(alpha = 0.7f),
                shape
            )
            // 右键菜单挂在最外层：Initial 阶段消费掉右键，内层 combinedClickable 就看不到它
            .desktopSecondaryClick(onMenu)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onStory,
                onDoubleClick = onOpenChat
            )
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceContainer)
        ) {
            AsyncImage(
                model = imageModel(card.avatarUri),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            card.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            card.tagline.ifBlank { categoryLabel(card.categoriesOrDefault()) },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 管理自定义分类：新增 / 删除 / 排序（预设分类不可改）。2026-09-17 起灵感创作页也复用（故改 internal）。 */
@Composable
internal fun ManageCategoriesDialog(
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    val density = LocalDensity.current
    var list by remember { mutableStateOf(CategoryManager.base()) }
    var newName by remember { mutableStateOf("") }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    // 删分类只改列表（卡上的分类名还在，重加同名即可恢复），但仍是"删除"入口 ⇒ 补一道确认（2026-09-21 统一补）
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val cardPitchPx = with(density) { 70.dp.toPx() }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("管理分类", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新增分类") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            CategoryManager.add(newName)
                            list = CategoryManager.base()
                            onChanged()
                            newName = ""
                        }
                    },
                    enabled = newName.isNotBlank()
                ) { Text("添加") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "按住右侧三条杠拖动排序，拖动时其它分类会实时让位；其余区域上下滑动浏览，点 ✕ 删除；「其他」固定在最末。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            if (list.isEmpty()) {
                Text("还没有分类，可添加一个。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // 问题 #23：原来用不可滚动的 Column，分类一多就看不全；
                // 换成 LazyColumn，拖拽手势交给卡片右侧的手柄，滚动不再被抢。
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 拖动中的预测落点：让中间卡片实时让出位置（用户反馈的换位预览）
                    // `coerceIn(0, lastIndex)` 在空列表会抛 IllegalArgumentException
                    // （当前走不到——空列表渲染的是上面那句提示——但这是"改一行就会炸"的写法，先堵上）
                    val targetIndex = if (dragIndex >= 0 && list.isNotEmpty()) {
                        (dragIndex + (dragOffset / cardPitchPx).roundToInt())
                            .coerceIn(0, list.lastIndex)
                    } else -1
                    // 补上稳定 key（分类名唯一）。没有 key 时 LazyColumn 按索引复用组合状态，
                    // 拖拽重排后"让位动画/拖动态"会跟着索引落到别的分类上。
                    itemsIndexed(list, key = { _, name -> name }) { index, name ->
                        DragCategoryCard(
                            name = name,
                            index = index,
                            dragIndex = dragIndex,
                            targetIndex = targetIndex,
                            dragOffset = dragOffset,
                            cardPitchPx = cardPitchPx,
                            onDragStart = { dragIndex = index; dragOffset = 0f },
                            onDrag = { dy -> dragOffset += dy },
                            onDragEnd = {
                                val target = (dragIndex + (dragOffset / cardPitchPx).roundToInt())
                                    .coerceIn(0, list.lastIndex)
                                if (dragIndex >= 0 && target != dragIndex) {
                                    CategoryManager.move(dragIndex, target)
                                    list = CategoryManager.base()
                                    onChanged()
                                }
                                dragIndex = -1
                                dragOffset = 0f
                            },
                            onDelete = { pendingDelete = name }
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除分类「$name」？") },
            text = { Text("它不会再出现在分类条里；角色卡上写着这个分类的仍保留原样，重加同名分类即可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    CategoryManager.remove(name)
                    list = CategoryManager.base()
                    onChanged()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

/**
 * 分类管理里那一行的外壳。
 *
 * 手机端＝原来那张 60dp 的 M3 `Card`（填色 + 3dp 阴影），桌面端＝不套壳、直接平铺：
 * 这是一栏"一行一条"的管理列表，桌面上靠行距与拖动位移辨识，卡片框就是那个"手机味"。
 *
 * **不复用 [ListItemCard]**：那张壳在手机端委派的是 `OutlinedCard`（描边、无阴影、无填色），
 * 与这里的外观不同，混用会让 Android 侧变样；`SectionCard` 也不合适（这是列表行，不是分区）。
 */
@Composable
private fun CategoryRowShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (isDesktopLayout) {
        Box(modifier) { content() }
        return
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) { content() }
}

@Composable
private fun DragCategoryCard(
    name: String,
    index: Int,
    dragIndex: Int,
    targetIndex: Int,
    dragOffset: Float,
    cardPitchPx: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit
) {
    // 换位预览：被拖的卡跟手；夹在起点与落点之间的卡带动画让出一个卡位
    val previewY = when {
        index == dragIndex -> dragOffset
        dragIndex < targetIndex && index > dragIndex && index <= targetIndex -> -cardPitchPx
        targetIndex < dragIndex && index < dragIndex && index >= targetIndex -> cardPitchPx
        else -> 0f
    }
    val animatedY by animateFloatAsState(
        targetValue = previewY,
        // 被拖的卡必须 0 延迟跟手；其余卡用弹簧动画滑到让位位置
        animationSpec = if (index == dragIndex) snap() else spring(
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = 0.5f
        ),
        label = "categoryReorderPreview"
    )
    // 桌面：这一行也不再套卡壳——分类管理是"一行一条"的列表。
    CategoryRowShell(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .zIndex(if (index == dragIndex) 1f else 0f)
            .graphicsLayer { translationY = animatedY }
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // 只有这块「三条杠」响应拖拽，且必须长按；其余区域留给列表滚动
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .pointerInput(Unit) {
                        // 自定义短长按：系统默认 ~400ms 太钝（用户反馈），180ms 即触发拖拽；
                        // 180ms 内抬起视为普通点击，不做事
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val liftedBeforeTimeout = withTimeoutOrNull(180L) {
                                waitForUpOrCancellation()
                            }
                            if (liftedBeforeTimeout != null) return@awaitEachGesture
                            onDragStart()
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    // 必须跟着**按下的那一根手指**（down.id）。
                                    // 原来取 changes.firstOrNull()，第二根手指一碰就会换成它，
                                    // 拖拽位置于是突然跳到另一根手指的位置。
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    onDrag(change.positionChange().y)
                                    change.consume()
                                }
                            } finally {
                                onDragEnd()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                GripLines(tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Close, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 三条横杠的拖动柄 */
@Composable
private fun GripLines(
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(width = 18.dp, height = 2.dp)
                    .background(tint, RoundedCornerShape(1.dp))
            )
        }
    }
}

// (下面的 AddCharacterDialog 保持原样)
@Composable
private fun AddCharacterDialog(
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onAiCreate: () -> Unit = {},
    onImport: () -> Unit,
    urlText: String = "",
    urlImporting: Boolean = false,
    onUrlTextChange: (String) -> Unit = {},
    onImportUrl: () -> Unit = {}
) {
    // 网址导入的参数区默认收起（对话框别一上来就很高），点一下展开
    var urlOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加角色卡") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text("手动创建")
                }
                // 问题 #36：增加 AI 生成入口，直达灵感创作页
                OutlinedButton(onClick = onAiCreate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI 生成")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("导入文件（JSON / PNG 卡）")
                }
                // 从发现页挪来
                OutlinedButton(onClick = { urlOpen = !urlOpen }, modifier = Modifier.fillMaxWidth()) {
                    Text("从网址导入")
                }
                if (urlOpen) {
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = onUrlTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("https://…") },
                        enabled = !urlImporting,
                        // Uri 键盘：URL 字段不出联想/自动替换，冒号斜杠不会被 IME 转全角
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Text(
                        "粘贴指向角色卡文件的直链（PNG 卡或 JSON），由本机直接下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onImportUrl,
                        enabled = !urlImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (urlImporting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (urlImporting) "拉取中" else "导入")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EmptyCharacters(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有角色卡", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "点右下角 + ，手动创建 / AI 生成 / 导入文件或从网址导入角色卡",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAdd) { Text("添加角色") }
        }
    }
}

@Composable
private fun NotFound(modifier: Modifier = Modifier, text: String) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 把角色分类列表压成一行小字，多于 3 个用 +N 缩写 */
internal fun categoryLabel(cats: List<String>): String {
    if (cats.isEmpty()) return "其他"
    return if (cats.size > 3) cats.take(3).joinToString(" · ") + "  +${cats.size - 3}"
    else cats.joinToString(" · ")
}

@Composable
private fun CharacterRow(
    card: CharacterCard,
    onEdit: () -> Unit,
    onShowConversations: () -> Unit,
    onShowStory: () -> Unit
) {
    Card(
        onClick = onShowStory,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageModel(card.avatarUri),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    // 点头像 = 看这个角色的全部会话；点卡片主体 = 看角色故事（问题 #11）
                    .clickable(onClick = onShowConversations)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    // 形态徽标（三个形态共用一处）：陪伴是默认形态、不挂徽标，所以由卡自己给文案
                    card.formTagBadge()?.let { badge ->
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        categoryLabel(card.categoriesOrDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (card.tagline.isNotBlank()) {
                    Text(
                        card.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (card.greeting.isNotBlank()) {
                    Text(
                        card.greeting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
        }
    }
}

/**
 * 问题 #11：点角色卡主体不再是「直接续聊」，而是先看角色故事——
 * 世界观 / 开场白 / 人设 + 会话数，从这里再决定继续、选会话、新建或编辑。
 */
@Composable
private fun CharacterStoryDialog(
    card: CharacterCard,
    onContinue: () -> Unit,
    onConversations: () -> Unit,
    onNew: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    // 已知问题修复：会话统计移出主线程（弹窗先渲染，统计异步补上）
    val convs by androidx.compose.runtime.produceState(initialValue = emptyList(), key1 = card.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Repository.listConversations(card.id)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = imageModel(card.avatarUri),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        // 问题 #30：详情弹窗显示全部标签（列表行的 +N 只是缩写，点进来应能看到完整分类）
                        card.categoriesOrDefault().joinToString(" · ") +
                            if (convs.isEmpty()) " · 还没有会话" else " · ${convs.size} 个会话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 编辑入口提到标题行（用户 2026-09-21：原来要点进正文拉到最底下才有「编辑」）
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑角色卡")
                }
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (card.tagline.isNotBlank()) {
                    Text(
                        card.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                StorySection("世界观 / 当前场景", card.scenario, "还没写世界观——编辑角色卡可以补上")
                StorySection("开场白", card.greeting, "还没写开场白，进会话后 TA 会直接接你的话")
                StorySection("人设", card.personaDisplayText(), "还没写人设")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onConversations) { Text("选会话") }
                    TextButton(onClick = onNew) { Text("新建会话") }
                    TextButton(onClick = onEdit) { Text("编辑") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text("继续上次聊天") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun StorySection(title: String, body: String, emptyHint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            body.ifBlank { emptyHint },
            style = MaterialTheme.typography.bodyMedium,
            color = if (body.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 点角色头像后弹出：该角色的全部会话（卡片主体现在打开的是角色故事，见 [CharacterStoryDialog]）。
 */
@Composable
private fun CharacterConversationsDialog(
    card: CharacterCard,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    // 已知问题修复：会话列表读取移出主线程（弹窗先渲染，列表异步补上）
    val convs by androidx.compose.runtime.produceState(initialValue = emptyList(), key1 = card.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Repository.listConversations(card.id).sortedByDescending { it.updatedAt }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(card.name)
                Text(
                    if (convs.isEmpty()) "还没有会话" else "共 ${convs.size} 个会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (convs.isEmpty()) {
                Text(
                    "点下面的「新建会话」开始聊天，之后所有会话都会出现在这里。",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(convs, key = { it.id }) { c ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpen(c.id) }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                c.title.ifBlank { "新会话" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${c.messages.size} 条消息",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("新建会话") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
