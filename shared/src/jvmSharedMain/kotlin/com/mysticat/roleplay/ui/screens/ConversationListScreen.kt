package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.timeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationListViewModel : ViewModel() {
    data class ConvItem(val conversation: Conversation, val character: CharacterCard?)

    var items by mutableStateOf<List<ConvItem>>(emptyList())
        private set

    init {
        refresh()
    }

    /**
     * 会话统计 + 角色回查都搬到后台（第 64 轮）。
     *
     * 原来这里是 `init{}` 里的**同步**调用：进「聊天记录」tab 那一下要在界面线程上把**全部**
     * 会话 JSON 扫一遍（合成的 1600 个会话冷读实测 4.4s，量法见桌面 `--smoke` 的「列表冷读计时」；
     * 会话少时只有几毫秒，但首帧不该赌用户有多少会话）。
     * 赋值回到主线程：Compose snapshot 状态只能在主线程写（0.1.1-alpha.2 在 IO 里直接赋值翻过车）。
     */
    fun refresh() {
        viewModelScope.launch {
            items = withContext(Dispatchers.IO) {
                Repository.listConversationsForAll()
                    .filter { it.messages.isNotEmpty() }
                    .sortedByDescending { it.updatedAt }
                    .map { ConvItem(it, Repository.getCharacter(it.characterId)) }
            }
        }
    }

    fun delete(convId: String) {
        Repository.deleteConversation(convId)
        refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenConversation: (characterId: String, conversationId: String) -> Unit,
    onNewConversation: (CharacterCard) -> Unit,
    vm: ConversationListViewModel = viewModel(factory = noArgViewModelFactory { ConversationListViewModel() })
) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var showPickChar by remember { mutableStateOf(false) }
    // rememberSaveable：切 tab 离开再回来，搜索词保留（用户 2026-09-21 导航状态保存）
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPickChar = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建对话")
            }
        }
    ) { padding ->
        val items = vm.items
        // 搜索：角色名 / 会话名 / 聊天原文 三个维度都匹配
        val q = query.trim()
        val filtered = remember(items, q) {
            if (q.isBlank()) items
            else items.filter { item ->
                item.character?.name?.contains(q, ignoreCase = true) == true ||
                    item.conversation.title.contains(q, ignoreCase = true) ||
                    item.conversation.messages.any { it.content.contains(q, ignoreCase = true) }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索角色 / 会话名 / 聊天内容") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            when {
                items.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有聊天记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "到「角色卡」选择一个角色开聊，记录会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                filtered.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("没有匹配的聊天记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "换个关键词试试——角色名、会话名、聊天原文都能搜",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.conversation.id }) { item ->
                        ConversationRow(
                            item = item,
                            onOpen = { onOpenConversation(item.conversation.characterId, item.conversation.id) },
                            onDelete = { pendingDelete = item.conversation.id }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话？") },
            text = { Text("该会话记录删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.delete(id); pendingDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (showPickChar) {
        CharacterPickerDialog(
            onDismiss = { showPickChar = false },
            onPick = { card -> showPickChar = false; onNewConversation(card) }
        )
    }
}

@Composable
private fun CharacterPickerDialog(
    onDismiss: () -> Unit,
    onPick: (CharacterCard) -> Unit
) {
    // P1-4：不能组合期裸读盘 —— 每次重组都会全量重读角色目录。
    // 第 64 轮：`remember` 只挡住了"反复读"，**没挡住"在界面线程上读"**——角色一多，
    // 打开这个框仍要卡一下（`listCharacters()` 现在有扫描缓存兜底，但冷启动那一次仍要全量解析）。
    // 改成组合期先渲染空列表、读盘在后台补上。
    // 初值用 null 而不是空列表：**空列表会先闪一句"还没有角色可对话"**——读盘还没回来就说这话是撒谎，
    // 与 ChatScreen 那道 ready 门同一个道理（那边是"角色不存在或已被删除"）。
    val loaded by produceState(initialValue = null as List<CharacterCard>?) {
        value = withContext(Dispatchers.IO) { Repository.listCharacters() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择一个角色开始对话") },
        text = {
            val chars = loaded
            if (chars == null) {
                Text("正在读取角色卡…")
            } else if (chars.isEmpty()) {
                Text("还没有角色可对话，先去「角色卡」创建一个。")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(chars, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPick(c) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = imageModel(c.avatarUri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(c.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ConversationRow(
    item: ConversationListViewModel.ConvItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val conv = item.conversation
    val char = item.character
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = imageModel(char?.avatarUri),
                contentDescription = char?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    conv.title.ifBlank { "新会话" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(char?.name ?: "未知角色")
                        append(" · ")
                        append(timeText(conv.updatedAt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                conv.messages.lastOrNull()?.let { last ->
                    Text(
                        last.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * 桌面聊天页左侧的**会话栏**（M6 左右分栏的左栏）。
 *
 * 为什么放在聊天页而不是复用「聊天记录」tab：桌面上切换会话不该离开聊天页（典型 IM 形态），
 * 而点 tab 再点会话是两次跳转。数据仍走 [ConversationListViewModel]（同一套读盘与排序口径），
 * 只是排版换成桌面口径——紧凑行、选中高亮、不要卡片阴影。
 */
@Composable
internal fun DesktopConversationRail(
    selectedConversationId: String,
    /** 每次变化就重读会话列表（聊天页发完第一条消息会新建会话，左栏要跟着出现） */
    refreshKey: Int,
    /** Ctrl+N（全局快捷键，M11 ①）：>0 = 弹出"选角色开新会话"；外壳发完会自行归零 */
    newSignal: Int = 0,
    /** Ctrl+K（全局快捷键）：>0 = 聚焦搜索框；外壳发完会自行归零 */
    focusSearchSignal: Int = 0,
    onOpen: (characterId: String, conversationId: String) -> Unit,
    onNew: (CharacterCard) -> Unit,
    vm: ConversationListViewModel = viewModel(factory = noArgViewModelFactory { ConversationListViewModel() })
) {
    // rememberSaveable：切到别的第一栏图标再回来，搜索词与滚动位置都在（用户 2026-09-21）
    var query by rememberSaveable { mutableStateOf("") }
    var showPickChar by remember { mutableStateOf(false) }
    LaunchedEffect(refreshKey) { vm.refresh() }
    LaunchedEffect(newSignal) { if (newSignal > 0) showPickChar = true }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearchSignal) { if (focusSearchSignal > 0) searchFocus.requestFocus() }

    val q = query.trim()
    val items = remember(vm.items, q) {
        if (q.isBlank()) vm.items
        else vm.items.filter { item ->
            item.character?.name?.contains(q, ignoreCase = true) == true ||
                item.conversation.title.contains(q, ignoreCase = true) ||
                item.conversation.messages.any { it.content.contains(q, ignoreCase = true) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "会话",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showPickChar = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "新建会话", Modifier.size(18.dp))
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .focusRequester(searchFocus),
            placeholder = { Text("搜索会话", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(16.dp)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = RoundedCornerShape(10.dp)
        )
        if (items.isEmpty()) {
            Text(
                if (q.isBlank()) "还没有聊天记录" else "没有匹配的会话",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp)
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(items, key = { it.conversation.id }) { item ->
                    RailRow(
                        item = item,
                        selected = item.conversation.id == selectedConversationId,
                        onOpen = { onOpen(item.conversation.characterId, item.conversation.id) }
                    )
                }
            }
        }
    }

    if (showPickChar) {
        CharacterPickerDialog(
            onDismiss = { showPickChar = false },
            onPick = { card -> showPickChar = false; onNew(card) }
        )
    }
}

@Composable
private fun RailRow(
    item: ConversationListViewModel.ConvItem,
    selected: Boolean,
    onOpen: () -> Unit
) {
    val conv = item.conversation
    val char = item.character
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageModel(char?.avatarUri),
            contentDescription = char?.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                conv.title.ifBlank { "新会话" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${char?.name ?: "未知角色"} · ${timeText(conv.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

