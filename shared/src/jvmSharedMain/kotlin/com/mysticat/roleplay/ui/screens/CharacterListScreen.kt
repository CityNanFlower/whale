package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.DesktopMainHeader
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.ui.DesktopCardContextMenu
import com.mysticat.roleplay.ui.DesktopMenuRow
import com.mysticat.roleplay.ui.desktopSecondaryClick
import com.mysticat.roleplay.ui.wheelHorizontalScroll
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.components.WhaleHairField
import com.mysticat.roleplay.ui.components.WhaleNoticeSheet
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.EditorField
import com.mysticat.roleplay.data.FormFilter
import com.mysticat.roleplay.data.PromptMode
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.RemoteFetch
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.rememberJsonFilePicker
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
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

    /**
     * 角色卡段的查看方式（名册 / 柜格）。默认**手机名册 / 桌面柜格**（交接书 §4.2 拍板）。
     *
     * 放VM 而不是页面 `remember`：桌面第三栏（`CharacterGridPane`）与手机页（`CharacterListScreen`）
     * 共用同一个 VM，切一次两端都记得住。
     */
    var view by mutableStateOf(
        if (com.mysticat.roleplay.ui.isDesktopLayout) TreasureView.CABINET else TreasureView.ROSTER
    )
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

    /**
     * 删角色卡（0.3.0 反馈 7：宝库长按弹删除）——删卡会连它的全部会话、头像与背景图一起回收，
     * 不可恢复，所以调用方必须先弹确认。写盘走 [Repository.deleteCharacter] 的单线程队列，
     * 删完按内存镜像重读一次列表（别拿删前的镜像做减法，那会漏掉刚导入的卡）。
     */
    fun deleteCharacter(card: CharacterCard) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                Repository.deleteCharacter(card.id) { t -> error = t.message ?: "删除失败" }
            }
            refresh()
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
    /** 世界书管理：编辑器跳来的待消费请求（= 书 id，空串 = 只切段） */
    pendingWorldBook: String? = null,
    onWorldBookOpenDone: () -> Unit = {},
    vm: CharacterListViewModel = viewModel(factory = noArgViewModelFactory { CharacterListViewModel() })
) {
val type = LocalWhaleType.current
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
    // 长按角色卡＝删除（0.3.0 反馈 7）
    var deleteTarget by remember { mutableStateOf<CharacterCard?>(null) }

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
        // 手机导航页顶部去填充（236 形式统一 · 照 v5 原型）：纸底 ＋ 底发丝线
        topBar = {
            WhalePageTopBar(
                title = "宝库",
                actions = {
                    if (segment == LibrarySegment.CHARACTERS) {
                        IconButton(onClick = { CrashNote.note("角色卡页 搜索"); vm.toggleSearch() }) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { CrashNote.note("角色卡页 管理分类"); showManageCategories = true }) {
                            Icon(Icons.Filled.Category, contentDescription = "管理分类")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (segment == LibrarySegment.CHARACTERS) {
                // 蓝底白线，与聊天记录页「新建对话」同一颗（用户 2026-10-03：加号全 App 统一）
                FloatingActionButton(
                    onClick = { vm.showAddDialog = true },
                    containerColor = LocalWhalePalette.current.accent,
                    contentColor = LocalWhalePalette.current.onAccent
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加角色卡")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LibrarySegmentRow(
                selected = segment,
                onSelect = { segment = it },
                // 段内切换器只挂在角色卡段——世界书段是柜格＋名册同屏，没有可切的两态
                trailing = if (segment == LibrarySegment.CHARACTERS) {
                    {
                        ViewSwitchChips(
                            selected = vm.view,
                            onSelect = { v -> CrashNote.note("宝库 角色卡 视图=${v.label}"); vm.view = v }
                        )
                    }
                } else {
                    null
                }
            )

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
                else -> if (vm.view == TreasureView.ROSTER) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Text(
                                "共 ${cards.size} 个角色",
                                style = type.tiny,
                                letterSpacing = 0.1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = WhaleSpacing.lg, end = WhaleSpacing.lg, top = 10.dp, bottom = 4.dp
                                )
                            )
                        }
                        items(cards, key = { it.id }) { card ->
                            CharacterRosterRow(
                                card = card,
                                onEdit = { onEdit(card.id) },
                                onShowConversations = { convTarget = card },
                                onShowStory = { storyTarget = card },
                                onLongPress = { deleteTarget = card }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = WhaleSpacing.lg, end = WhaleSpacing.lg, top = 14.dp, bottom = 88.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        gridItems(cards, key = { it.id }) { card ->
                            CharacterCabinetItem(
                                card = card,
                                onStory = { CrashNote.note("宝库 柜格 看故事=${card.name}"); storyTarget = card },
                                onLongPress = { deleteTarget = card }
                            )
                        }
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

    // 长按删除（0.3.0 反馈 7）：删卡会连它的会话与图片一起回收，先确认再动
    deleteTarget?.let { card ->
        DeleteCharacterDialog(
            card = card,
            onConfirm = {
                deleteTarget = null
                vm.deleteCharacter(card)
            },
            onDismiss = { deleteTarget = null }
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
        // 栏头统一走 DesktopRailHeader（top 12 / bottom 6 / titleSmall+SemiBold 一处定义）
        DesktopRailHeader("角色卡") {
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
                        // 堆叠层＝"这里管的是一摞东西"（用户 2026-10-03：照原型改图标）。
                        // 原来那个铅笔读起来是"编辑分类名"，与真正点进去做的事（增删改排序）不是一回事。
                        Icons.Outlined.Layers,
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
        // 分类行前一枚立方体（用户 2026-10-03：照原型加图标）——原来只有字与数字，
        // 选中态除了底色没有第二个可认的特征，加一枚图标后"当前在哪个分类"一眼就分得开。
        Icon(
            Icons.Outlined.ViewInAr,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (selected) colors.onSecondaryContainer.copy(alpha = 0.85f)
            else colors.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Spacer(Modifier.width(9.dp))
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
 * 桌面三栏版角色卡：**第三栏**。
 *
 * 抬头一行的口径＝**段标题 ＋ 总数 ＋ 视图切换 ＋ 新建**（原型 `treTopM` 那一行）。
 * 默认**柜格**（交接书 §4.2：手机名册 / 桌面柜格），段内可切回名册；两种视图共用同一个 VM 状态。
 * 右键＝操作菜单（与长按等价），单击＝角色故事；**双击开聊已退役**（交接书 §6 R1）。
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
    // 删除确认的目标卡片（0.3.0 反馈 7）
    var deleteTarget by remember { mutableStateOf<CharacterCard?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    val cards = vm.characters
    Column(Modifier.fillMaxSize()) {
        // 主栏标题（236 形式统一）：字体/内距/分割线全按发现页；右侧字段＝本页的个数、视图切换与新建
        Box(Modifier.padding(start = 20.dp, end = 20.dp)) {
            DesktopMainHeader(if (vm.category == "全部") "全部角色" else vm.category) {
                Text(
                    "${cards.size} 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                ViewSwitchChips(
                    selected = vm.view,
                    onSelect = { v -> CrashNote.note("宝库 角色卡 视图=${v.label}"); vm.view = v }
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = { vm.showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建角色")
                }
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
else -> if (vm.view == TreasureView.ROSTER) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(cards, key = { it.id }) { card ->
                            // 名册行与柜格卡同一条语法：菜单锚在这一行自己里（反馈 ⑤）
                            Box {
                                CharacterRosterRow(
                                    card = card,
                                    onEdit = { onEdit(card.id) },
                                    onShowConversations = { convTarget = card },
                                    onShowStory = { storyTarget = card },
                                    onLongPress = { deleteTarget = card },
                                    onOpenRow = { CrashNote.note("宝库 名册 编辑=${card.name}"); onEdit(card.id) },
                                    onMenu = { menuTarget = card }
                                )
                                if (menuTarget?.id == card.id) {
                                    CharacterCardMenu(
                                        card = card,
                                        onDismiss = { menuTarget = null },
                                        onStory = { menuTarget = null; storyTarget = card },
                                        onOpenChat = { menuTarget = null; onOpenChat(card) },
                                        onStartNewChat = { menuTarget = null; onStartNewChat(card) },
                                        onAllConversations = { menuTarget = null; convTarget = card },
                                        onEditCard = { menuTarget = null; onEdit(card.id) },
                                        onDelete = { menuTarget = null; deleteTarget = card }
                                    )
                                }
                            }
                        }
                    }
                } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp)
                ) {
                    gridItems(cards, key = { it.id }) { card ->
                        Box(Modifier.padding(end = 14.dp, bottom = 14.dp)) {
                            CharacterCabinetDesktopItem(
                                card = card,
                                onEdit = { CrashNote.note("角色卡 编辑=${card.name}"); onEdit(card.id) },
                                onMenu = { menuTarget = card }
                            )
                            // 菜单锚在**这张卡自己**里（反馈 ⑤）：声明在页面底部的话，
                            // DropdownMenu 永远弹在窗口左下角，跟被右击的卡八竿子打不着
                            if (menuTarget?.id == card.id) {
                                CharacterCardMenu(
                                    card = card,
                                    onDismiss = { menuTarget = null },
                                    onStory = { menuTarget = null; storyTarget = card },
                                    onOpenChat = { menuTarget = null; onOpenChat(card) },
                                    onStartNewChat = { menuTarget = null; onStartNewChat(card) },
                                    onAllConversations = { menuTarget = null; convTarget = card },
                                    onEditCard = { menuTarget = null; onEdit(card.id) },
                                    onDelete = { menuTarget = null; deleteTarget = card }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 右键／长按菜单（桌面右键、触屏长按，两条入口等价）：双击开聊之外的常用操作都放这里，不占卡片面积。
    // 菜单本体由每张卡自己声明（见 [CharacterCardMenu]），这里只保留删除确认等跨卡的落点。

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

    // 长按（或右键菜单里点「删除」）后的确认（0.3.0 反馈 7）
    deleteTarget?.let { card ->
        DeleteCharacterDialog(
            card = card,
            onConfirm = { deleteTarget = null; vm.deleteCharacter(card) },
            onDismiss = { deleteTarget = null }
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
        WhaleNoticeSheet(title = "导入成功", body = msg, onConfirm = vm::clearNotice)
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

/**
 * 桌面柜格里的一张卡：与手机柜格**同一套 `.cabc` 版式**，只多两样桌面才有的东西——
 * 悬停时的主色描边（原型 `.tile:hover .cv{outline:2px solid var(--accent)}`）与右键菜单。
 *
 * **单击＝直接进编辑页**（用户 2026-10-03：「桌面端宝库中点击角色卡片直接进入编辑页，
 * 不需要有手机端那样的弹窗」）。原来单击弹的是"角色故事"那个对话框——桌面上有第三栏、
 * 有整页编辑器，为一个只读的故事先弹一次窗再点一次才能进编辑，纯属多绕一道。
 * 故事与"继续上次会话"都还在右键菜单里（长按同一条），一处都没丢。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CharacterCabinetDesktopItem(
    card: CharacterCard,
    onEdit: () -> Unit,
    onMenu: () -> Unit
) {
    CabinetCard(
        cover = {
            AvatarOrInitial(
                name = card.name,
                avatarUri = card.avatarUri,
                fontSize = 44.sp,
                modifier = Modifier.fillMaxSize()
            )
        },
        name = card.name,
        typeLabel = card.formTagLabel(),
        tagline = card.tagline.ifBlank { card.description.take(40) },
        onOpen = onEdit,
        modifier = Modifier
            // 右键菜单挂在最外层：Initial 阶段消费掉右键，内层 clickable 就看不到它。
            // 悬停描边由 [CabinetCard] 自己管（它在封面那层画 outline），不在这里重复一套
            .desktopSecondaryClick(onMenu)
    )
}

/**
 * 角色卡的右键菜单（用户 2026-10-03 反馈 ⑤：菜单要出现在**那张卡的旁边**）。
 *
 * 原来这张菜单用 `DropdownMenu` 声明在**页面底部**（整页只声明一次），而它的锚点是"声明处"——
 * 于是无论右击哪张卡，菜单都固定弹在窗口左下角，离被右击的卡十万八千里（用户截图即如此）。
 * 现在由每张卡/每一行在自己的 Box 里声明，`Popup` 的锚点就是那张卡 ⇒ 菜单贴着它弹。
 *
 * 条目与改造前逐条一致，一项没删：故事／继续上次会话／新建会话／全部会话／编辑／删除。
 */
@Composable
private fun CharacterCardMenu(
    card: CharacterCard,
    onDismiss: () -> Unit,
    onStory: () -> Unit,
    onOpenChat: () -> Unit,
    onStartNewChat: () -> Unit,
    onAllConversations: () -> Unit,
    onEditCard: () -> Unit,
    onDelete: () -> Unit
) {
    DesktopCardContextMenu(onDismiss = onDismiss) {
        DesktopMenuRow("查看角色故事", onClick = onStory)
        DesktopMenuRow("继续上次会话", onClick = onOpenChat)
        DesktopMenuRow("新建会话", onClick = onStartNewChat)
        DesktopMenuRow("全部会话…", onClick = onAllConversations)
        DesktopMenuRow("编辑角色卡", onClick = onEditCard)
        DesktopMenuRow("删除角色卡…", danger = true, onClick = onDelete)
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

    // 居中卡片形态（2026-10-10 口径 239）：原来是无边框全屏页，与其它设置浮层不同族；
    // 内容随卡片收窄，分类多了由列表自己滚。
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理分类") },
        text = {
            Column(Modifier.fillMaxWidth()) {
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
                            .heightIn(max = 320.dp)
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
    pendingDelete?.let { name ->
        WhaleConfirmSheet(
            title = "删除分类「$name」？",
            body = "它不会再出现在分类条里；角色卡上写着这个分类的仍保留原样，重加同名分类即可恢复。",
            confirmLabel = "删除",
            onConfirm = {
                pendingDelete = null
                CategoryManager.remove(name)
                list = CategoryManager.base()
                onChanged()
            },
            onDismiss = { pendingDelete = null }
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
                    WhaleHairField(
                        value = urlText,
                        onValueChange = onUrlTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = "https://…",
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CharacterRosterRow(
    card: CharacterCard,
    onEdit: () -> Unit,
    onShowConversations: () -> Unit,
    onShowStory: () -> Unit,
    onLongPress: () -> Unit,
    /**
     * 桌面端覆盖「点行主体」的动作（用户 2026-10-03：桌面上点卡片直接进编辑页，不要先弹一次窗）。
     * 为空＝沿用手机端口径（点主体看故事）。桌面把故事挪进了右键菜单，所以这条覆盖不会让人找不到它。
     */
    onOpenRow: (() -> Unit)? = null,
    /** 桌面端右键菜单（与柜格卡同一条菜单）；为空则这一行不接右键 */
    onMenu: (() -> Unit)? = null
) {
    RosterRow(
        avatar = {
            AvatarOrInitial(
                name = card.name,
                avatarUri = card.avatarUri,
                fontSize = 26.sp,
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
            )
        },
        name = card.name,
        badge = card.formTagBadge(),
        meta = categoryLabel(card.categoriesOrDefault()),
        tagline = card.tagline,
        greeting = card.greeting,
        // 长按＝删除（手机端）、右键＝菜单（桌面端）；桌面上不再挂常驻删除钮（用户 2026-10-03 反馈 ⑥）
        onOpen = onOpenRow ?: onShowStory,
        onAvatarClick = onShowConversations,
        onEdit = onEdit,
        onLongPress = onLongPress,
        // 右键菜单挂最外层（与柜格卡同理）：Initial 阶段消费掉右键，内层 clickable 就看不到它
        modifier = if (onMenu != null) Modifier.desktopSecondaryClick(onMenu) else Modifier
    )
}

/**
 * 柜格里的一个角色（原型 `.cabc`）。单击＝角色故事（与名册同一入口，交互语法 R1「点击＝前进」）。
 * 长按＝删除；**双击开聊已退役**（交接书 §6 R1：双击不可发现，动作入口只剩长按/右键，
 * 因此行尾铅笔与故事弹窗里的「继续会话」是留给它的退路）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CharacterCabinetItem(
    card: CharacterCard,
    onStory: () -> Unit,
    onLongPress: () -> Unit
) {
    CabinetCard(
        cover = {
            AvatarOrInitial(
                name = card.name,
                avatarUri = card.avatarUri,
                fontSize = 44.sp,
                modifier = Modifier.fillMaxSize()
            )
        },
        name = card.name,
        typeLabel = card.formTagLabel(),
        tagline = card.tagline.ifBlank { card.description.take(40) },
        onOpen = onStory,
        onLongPress = onLongPress
    )
}

/**
 * 长按角色卡的删除确认（0.3.0 反馈 7），手机列表与桌面网格共用。
 * 删卡会连它的**全部会话**、头像与背景图一起回收（[Repository.deleteCharacter]），不可恢复 ⇒ 必须确认。
 */
@Composable
internal fun DeleteCharacterDialog(
    card: CharacterCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    WhaleConfirmSheet(
        title = "删除角色卡？",
        body = "「${card.name}」及其全部会话将被删除（含头像与背景图），不可恢复。",
        confirmLabel = "删除",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 问题 #11：点角色卡主体不再是「直接续聊」，而是先看角色故事——
 * 角色设定（分层三栏）+ 场景 + 开场白 + 会话数，从这里再决定继续、选会话、新建或编辑。
 *
 * 字段口径**跟着卡走**：新卡分栏、工具卡说任务说明与输出格式、玩法卡说玩法设定，
 * 老卡才落回那一整块「人设」。用户 10-04 反馈过这里还在显示老字段名。
 */
@OptIn(ExperimentalLayoutApi::class)
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
                // 全局头像（211）：没图＝名字第一个字，与宝库格/聊天记录同一份组件
                AvatarOrInitial(
                    name = card.name,
                    avatarUri = card.avatarUri,
                    modifier = Modifier.size(44.dp),
                    fontSize = 20.sp,
                    shape = CircleShape
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        // 问题 #30：详情弹窗显示全部标签（列表行的 +N 只是缩写，点进来应能看到完整分类）
                        card.categoriesOrDefault().joinToString(" · ") +
                            if (convs.isEmpty()) " · 还没有会话" else " · 会话 ${convs.size}",
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
            // 原型（用户 2026-10-03）：内容区限高滚动，动作行**钉在滚动区外**——
            // 选会话/新建会话/编辑/继续上次聊天不用滚到底也够得着
            Column {
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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 字段与**编辑器、与装配器**同一套（用户 10-04：这个弹窗还在用「世界观 / 开场白 /
                    // 人设」那套老字段，卡早就按酒馆兼容改成 description / personality / mes_example 了）。
                    // 分层三栏任一非空就分栏展示；全是老卡才回退那个合并块 persona。
                    // 展示顺序＝注入顺序（角色设定 → 世界观 → 对话示例 → 开场白），照着读一遍就是提示词。
                    val spec = Engines.of(card)
                    if (card.hasLayeredPersona()) {
                        StorySection(
                            shortFieldLabel(spec.personaLabel),
                            card.description,
                            "还没写${shortFieldLabel(spec.personaLabel)}——编辑角色卡可以补上"
                        )
                        StorySection(
                            shortFieldLabel(spec.personalityLabel),
                            card.personality,
                            "还没写${shortFieldLabel(spec.personalityLabel)}——编辑角色卡可以补上"
                        )
                        // 对话示例在工具形态下不存在：装配器不注入、编辑器也不给这一栏
                        if (EditorField.DialogueExamples in spec.editorFields) {
                            StorySection("对话示例", card.mesExample, "还没写对话示例（可留空，只影响语气像不像）")
                        }
                    } else {
                        // 老卡：只有一个当年导入时拼好的整块人设。分层字段一填它就不再注入
                        // （见 CharacterEditorScreen 的说明），那时候这里也就不再显示它。
                        StorySection("人设", card.persona, "还没写人设")
                    }
                    // 工具形态该说的是它自己的字段：任务说明与输出格式。
                    // 「还没写世界观——编辑角色卡可以补上」在工具卡上是**误导**：工具契约不注入世界观，
                    // 编辑器里也没有这一栏（见 EngineSpec.editorFields），照它去写一个字都不会发出去。
                    if (spec.promptMode == PromptMode.TOOL) {
                        StorySection("任务说明", card.taskBrief, "还没写任务说明——编辑角色卡可以补上")
                        StorySection("输出格式", card.outputFormat, "还没写输出格式")
                        StorySection("使用示例", card.greeting, "还没写使用示例（填一句任务，点它即填入输入框）")
                    } else if (card.isPlay()) {
                        StorySection("玩法设定", card.scenario, "还没写玩法设定——编辑角色卡可以补上")
                        StorySection("开局引导", card.greeting, "还没写开局引导（留空就直接等用户开始）")
                    } else {
                        StorySection("世界观 / 当前场景", card.scenario, "还没写世界观——编辑角色卡可以补上")
                        StorySection("开场白", card.greeting, "还没写开场白，进会话后 TA 会直接接你的话")
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StoryAction(onConversations, "选会话")
                    StoryAction(onNew, "新建会话")
                    StoryAction(onEdit, "编辑")
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalWhalePalette.current.accent,
                            contentColor = LocalWhalePalette.current.onAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            "继续上次聊天",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("关闭", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/** 原型动作行里的描边小键（选会话／新建会话／编辑）：hairline 描边＋正常字色，高与主行动键齐平 */
@Composable
private fun StoryAction(onClick: () -> Unit, label: String) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, LocalWhalePalette.current.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * 编辑器里的字段标签带一句括号里的长说明（"角色描述（外貌 / 身份 / 背景等硬事实）"）。
 * 弹窗里那是小节标题，留前半句就够——长说明在编辑器那一栏旁边已经写着。
 * 切在这里而不是各写一份字面量：形态一改措辞，两边自动一起改。
 */
private fun shortFieldLabel(raw: String): String = raw.substringBefore("（").trim()

@Composable
private fun StorySection(title: String, body: String, emptyHint: String) {
    val pal = LocalWhalePalette.current
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = pal.ink3)
        Spacer(Modifier.height(5.dp))
        Surface(shape = WhaleShapes.r2, color = Color.Transparent, border = BorderStroke(1.dp, pal.hairline)) {
            Text(
                body.ifBlank { emptyHint },
                style = MaterialTheme.typography.bodyMedium,
                color = if (body.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
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
