package com.mysticat.roleplay.ui

import androidx.compose.foundation.border
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysticat.roleplay.Routes
import com.mysticat.roleplay.ShotOverrides
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.CrashNote
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.screens.AiCreateRail
import com.mysticat.roleplay.ui.screens.AiCreateScreen
import com.mysticat.roleplay.ui.screens.AiCreateViewModel
import com.mysticat.roleplay.ui.screens.CharacterCategoryRail
import com.mysticat.roleplay.ui.screens.CharacterGridPane
import com.mysticat.roleplay.ui.screens.CharacterListScreen
import com.mysticat.roleplay.ui.screens.CharacterListViewModel
import com.mysticat.roleplay.ui.screens.ConversationListScreen
import com.mysticat.roleplay.ui.screens.DiscoverScreen
import com.mysticat.roleplay.ui.screens.DiscoverViewModel
import com.mysticat.roleplay.ui.screens.DesktopConversationRail
import com.mysticat.roleplay.ui.screens.DesktopDiscoverRail
import com.mysticat.roleplay.ui.screens.DesktopProfileRail
import com.mysticat.roleplay.ui.screens.ProfileGroup
import com.mysticat.roleplay.ui.screens.ProfileScreen
import com.mysticat.roleplay.ui.screens.SettingsViewModel
import com.mysticat.roleplay.ui.noArgViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/**
 * 主界面（五个 tab 的容器）。
 *
 * M6（第 23 轮）按平台分支；第 28 轮把桌面分支升级成**三栏**（[DesktopMainScreen]）：
 *  * **手机**＝底部 [NavigationBar]（图标不带文字，64dp 固定高，见下方注释）+ 整页内容
 *  * **桌面**＝第一栏纯图标导航（64dp）＋ 第二栏页面级侧栏（可调宽/可收起）＋ 第三栏内容
 *
 * tab 的**内容**两端共用同一批页面组件（[MainTabContent]）；桌面上其中两个 tab 会被拆成
 * 「第二栏 + 第三栏」两半（会话列表/聊天、分类/角色网格），拆法见各自的桌面 composable。
 */
@Composable
fun MainScreen(
    onOpenChat: (CharacterCard) -> Unit,
    onStartNewChat: (CharacterCard) -> Unit,
    onEditCharacter: (String) -> Unit,
    onNewCharacter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenBgm: () -> Unit = {},
    onOpenManual: () -> Unit = {},
    onOpenConversation: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
    /** 起始 tab，只为离屏截图而设（见 [com.mysticat.roleplay.AppRoot]）；正常启动传 null */
    initialTab: Int? = null
) {
    var tab by rememberSaveable { mutableStateOf(initialTab ?: 2) }
    // tab 说明：0发现 1聊天记录 2角色卡 3AI创作 4我的
    val setTab: (Int) -> Unit = { tab = it }

    if (isDesktopLayout) {
        DesktopMainScreen(
            tab = tab,
            onTabChange = setTab,
            onOpenSettings = onOpenSettings,
            onOpenVoice = onOpenVoice,
            onLogout = onLogout,
            onSwitchAccount = onSwitchAccount
        )
        return
    }

    // 导航页状态保存（用户 2026-09-21：切换走再切换回来要保存离开前的状态）：
    // SaveableStateHolder 让每个 tab 离开组合时把**可保存**状态（LazyList 滚动位置、rememberSaveable
    // 的输入框等）按 key 收起来，回来时恢复。普通 remember 的瞬时状态救不了，但那两类是最直观的。
    val tabStateHolder = rememberSaveableStateHolder()
    val content: @Composable () -> Unit = {
        tabStateHolder.SaveableStateProvider("tab-$tab") {
            MainTabContent(
                tab = tab,
                onTabChange = setTab,
                onOpenChat = onOpenChat,
                onStartNewChat = onStartNewChat,
                onEditCharacter = onEditCharacter,
                onNewCharacter = onNewCharacter,
                onOpenSettings = onOpenSettings,
                onOpenVoice = onOpenVoice,
                onOpenBgm = onOpenBgm,
                onOpenManual = onOpenManual,
                onOpenConversation = onOpenConversation,
                onLogout = onLogout,
                onSwitchAccount = onSwitchAccount
            )
        }
    }

    Scaffold(
        // edge-to-edge 下顶部 insets 由各 tab 页自己的 TopAppBar 消费（否则会加两次、顶部过高）；
        // 底部 insets 由 NavigationBar 自带处理
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 问题 #4：底部导航只用图标，去掉文字——「聊天记录」「灵感创作」四个字会换行挤成两行。
            // 文字改挂在 contentDescription 上，无障碍读屏仍能念出名称。
            //
            // 高度与黑边（2026-09-14 二次修正）：
            // - 高度固定 64dp 是**总高**（NavigationBar 自带的 systemBars 底部 inset 在里面当内边距），
            //   这样既不额外变高、图标区又真的扁（此前给外层加 navigationBarsPadding 会让总高变成 64+inset）。
            // - 底部那条黑边不是这里的布局问题，而是系统的导航栏对比度蒙层（见 MainActivity 里
            //   isNavigationBarContrastEnforced = false）。
            NavigationBar(modifier = Modifier.height(64.dp)) {
                // ⚠️ 每次切 tab 先记一条操作轨迹（CrashGuard）：崩溃栈只说明炸在哪，
                // 「炸之前在点哪一栏」才是定位偶发闪退（#14）的关键线索
                mainTabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { CrashNote.note("首页 tab=${t.second}"); tab = i },
                        icon = { Icon(t.first, contentDescription = t.second) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) { content() }
    }
}

/**
 * `--open=editor`（截图开关）要的是"随便挑一张已有的角色卡"，但参数解析发生在 `bootstrap`
 * 之前——那时仓储还是空的，取不到角色列表。所以参数只写一个哨兵 [ShotOverrides.EDITOR_FIRST]，
 * 到这里（组合期，启动早已完成）再换成真 id；一张卡都没有就退化成「新建角色」。
 * 正常启动 [ShotOverrides.editorTarget] 恒为 null，这里直接返回 null。
 */
private fun shotEditorTarget(): String? {
    val t = ShotOverrides.editorTarget ?: return null
    if (t != ShotOverrides.EDITOR_FIRST) return t
    return runCatching { Repository.listCharacters().firstOrNull()?.id }.getOrNull() ?: "new"
}

/** 五个 tab 的图标与名字（手机＝底部导航，桌面＝侧边栏，**同一份定义**） */
private val mainTabs: List<Pair<ImageVector, String>> = listOf(
    Icons.Filled.Explore to "发现",
    Icons.Filled.ChatBubble to "聊天记录",
    Icons.Filled.Person to "角色卡",
    Icons.Filled.AutoAwesome to "灵感创作",
    Icons.Filled.AccountCircle to "用户"
)

/** 各 tab 的内容（两端共用） */
@Composable
private fun MainTabContent(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenChat: (CharacterCard) -> Unit,
    onStartNewChat: (CharacterCard) -> Unit,
    onEditCharacter: (String) -> Unit,
    onNewCharacter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenBgm: () -> Unit = {},
    onOpenManual: () -> Unit = {},
    onOpenConversation: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    when (tab) {
        0 -> DiscoverScreen(
            onGoCharacters = { onTabChange(2) },
            // 点精选卡即聊（第 53 轮）：导入落卡后直接开新会话，与聊天记录页「+」同一条路
            onStartChat = onStartNewChat
        )
        1 -> ConversationListScreen(
            onOpenConversation = onOpenConversation,
            onNewConversation = onStartNewChat
        )
        2 -> CharacterListScreen(
            onOpenChat = onOpenChat,
            onStartNewChat = onStartNewChat,
            onOpenConversation = onOpenConversation,
            onEdit = onEditCharacter,
            onNew = onNewCharacter,
            // 问题 #36：添加角色卡弹窗的「AI 生成」直达灵感创作页
            onAiCreate = { onTabChange(3) },
            onSettings = onOpenSettings
        )
        3 -> AiCreateScreen(onCreated = onOpenChat, onEdit = onEditCharacter)
        else -> ProfileScreen(
            onOpenSettings = onOpenSettings,
            onOpenVoice = onOpenVoice,
            onOpenBgm = onOpenBgm,
            onOpenManual = onOpenManual,
            onLogout = onLogout,
            onSwitchAccount = onSwitchAccount
        )
    }
}

/**
 * 桌面主界面＝三栏外壳（第 28 轮）。
 *
 * 分工：
 *  * **第一栏**：[DesktopNavRail] 常驻纯图标导航（含底部设置齿轮与用户头像）
 *  * **第二栏**：[DesktopRailPane] 的 rail 槽——内容随 tab 变（会话列表 / 分类列表），可拖宽、可收起
 *  * **第三栏**：当前选中的具体内容（聊天窗口 / 角色网格 / 其余 tab 的整页）
 *
 * 「点会话不跳页」是怎么做到的：桌面上不再 `nav.navigate(chat)`，而是把 (角色, 会话) 存进
 * [DesktopChatTarget]，由第三栏直接渲染聊天页（见 [DesktopChatHost]）。手机的返回栈逻辑
 * 一行没动——它仍然走 `Routes.CHAT`。
 */
@Composable
private fun DesktopMainScreen(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    // 第三栏要开的会话（null = 还没选）。桌面不跳页，所以这份状态就住在这里。
    var chatTarget by remember { mutableStateOf<DesktopChatTarget?>(null) }
    // 聊天页读/写了会话之后通知第二栏重读列表（新会话是"发第一条消息才落盘"的）
    var railRefreshKey by remember { mutableIntStateOf(0) }
    var railWidth by remember { mutableStateOf(DesktopShellPrefs.clamp(DesktopShellPrefs.railWidth).dp) }
    var railCollapsed by remember { mutableStateOf(DesktopShellPrefs.railCollapsed) }
    // 灵感创作页：创作分段与模板分类都住在这个 VM 里，第二栏与第三栏共用它
    val aiVm: AiCreateViewModel = viewModel(factory = noArgViewModelFactory { AiCreateViewModel() })
    // 发现页：视图轴与当前轴的分组住在这个 VM 里，第二栏（第 90 轮新增）与第三栏共用它
    val discoverVm: DiscoverViewModel = viewModel(factory = noArgViewModelFactory { DiscoverViewModel() })
    // 「用户」页：选中的设置分组 + 那一组里的设置分段；设置 VM 由外壳持有（见 ProfileScreen 的说明）
    var profileGroup by remember { mutableStateOf(ProfileGroup.ACCOUNT) }
    // 桌面设置页的「拉通流」定位信号（第 87 轮，用户反馈 ③）：第二栏点**卡片型**栏目时递增一次，
    // 第三栏据此滚到那一节。用递增信号而非"当前选中栏目"做触发条件——连点同一项也要能滚回去。
    var profileAnchor by remember { mutableIntStateOf(0) }
    // 第三栏是否在显示"整页形态"的栏目（模型与 API / 语音服务 / 背景音乐 / 使用手册）。
    // 总设置表里这四栏也各占一节、给一行入口，点了才整页打开（用户 2026-09-23 补充口径）。
    var profilePage by remember { mutableStateOf(false) }
    val settingsVm: SettingsViewModel = viewModel(factory = noArgViewModelFactory { SettingsViewModel() })
    val density = LocalDensity.current

    // 角色卡那一页的 VM 由外壳持有：第二栏（分类/搜索）与第三栏（网格）共用同一份筛选状态
    val charVm: CharacterListViewModel = viewModel(
        factory = noArgViewModelFactory { CharacterListViewModel() }
    )

    // M11 ④拖拽导入：拖进窗口的 JSON 角色卡由 desktopApp 的 DropTarget 落库（全局，任何页都收），
    // 这里只负责让角色列表跟着重读——不主动切页，去不去看由用户自己决定。
    LaunchedEffect(DesktopDragDrop.importedRevision) {
        if (DesktopDragDrop.importedRevision > 0) charVm.refresh()
    }

    // 左栏底部的头像：进「用户」页前刷新一次（用户可能刚换过头像）
    var profile by remember { mutableStateOf(Repository.currentProfile()) }
    LaunchedEffect(tab) {
        if (tab != TAB_PROFILE) profile = Repository.currentProfile()
    }

    /** 这一页有没有第二栏（没有的页面第三栏直接占满）——第 90 轮起「发现」也有（台账 12 ①） */
    fun hasRail(t: Int): Boolean = t == TAB_DISCOVER || t == TAB_CHAT || t == TAB_CHARACTER ||
        t == TAB_AI_CREATE || t == TAB_PROFILE

    val openChatTo: (String, String) -> Unit = { charId, convId ->
        chatTarget = DesktopChatTarget(charId, convId)
    }

    // ── 角色编辑（第 34 轮）：第三栏里的编辑器，点"编辑/新建"不再整页跳走 ──
    // target = 正在编辑的 characterId（"new" = 新建）；null = 没在编辑，第三栏给当前 tab 的内容
    var editorTarget by remember { mutableStateOf(shotEditorTarget()) }
    // 外壳请求关闭的信号：递增一次 = 让编辑器走一遍它自己的"保存更改？"确认
    var editorClose by remember { mutableIntStateOf(0) }
    // 编辑器开着时又点了别处：那一下不能立刻执行（第三栏会被抢走），先记下来，关掉之后再补上
    var pendingSwitch by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** 打开编辑器。关闭信号归零——否则上一轮攒下的信号会把刚打开的这个一次性关掉 */
    fun openEditor(id: String) {
        editorTarget = id
        editorClose = 0
    }

    // ── 全局快捷键（M11 ①）的信号源：快捷键在窗口层收到，但效果要在对应的第二栏里发生 ──
    // 用"递增信号 + 外壳过一会儿归零"而不是事件流：第二栏重组后 LaunchedEffect 只看当前值，
    // 归零防止"离开再回到该页时旧信号又触发一次"。
    var newSessionSignal by remember { mutableIntStateOf(0) }
    var focusSearchSignal by remember { mutableIntStateOf(0) }
    LaunchedEffect(newSessionSignal) {
        if (newSessionSignal > 0) { delay(150); newSessionSignal = 0 }
    }
    LaunchedEffect(focusSearchSignal) {
        if (focusSearchSignal > 0) { delay(150); focusSearchSignal = 0 }
    }
    // Ctrl+滚轮调字号：滚动时只改内存值（即时生效），停下约 1 秒后落盘，别一格一动写一次 settings.json
    LaunchedEffect(Unit) {
        snapshotFlow { ThemeState.fontScale }
            .drop(1)
            .collectLatest {
                kotlinx.coroutines.delay(900)
                ThemeState.setFontScale(ThemeState.fontScale, persist = true)
            }
    }

    /**
     * 编辑器开着时想干别的（切页 / 点会话 / 点齿轮）：**不能直接清状态**——那会静默丢掉
     * 未保存的修改。改成"发关闭信号 + 记下待办"：脏不脏只有编辑器知道，让它自己弹确认框，
     * 关掉之后再把这一下补上。没在编辑时就是原样执行。
     */
    fun guarded(action: () -> Unit) {
        if (editorTarget == null) {
            action()
        } else {
            pendingSwitch = action
            editorClose++
        }
    }

    val closeEditor: () -> Unit = {
        editorTarget = null
        val next = pendingSwitch
        pendingSwitch = null
        next?.invoke()
    }

    // ── 全局快捷键处理（M11 ①）：窗口层 onPreviewKeyEvent 会把按键送进来（见 DesktopShortcuts）。
    // 只认 Ctrl+单键；Esc 不在这管（它走共享返回栈 DesktopBack，在窗口层更早处理）。
    // handler 里读的 tab / railCollapsed 都是每帧最新的（SideEffect 每次重组后重挂）。
    val onShortcut: (KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
            when (event.key) {
                Key.B -> {
                    // Ctrl+B = 收起/拉出第二栏（与"点当前页图标"同一个开关，落盘同一份）
                    if (hasRail(tab)) {
                        railCollapsed = !railCollapsed
                        DesktopShellPrefs.railCollapsed = railCollapsed
                    }
                    true
                }
                Key.N -> {
                    // Ctrl+N = 新建会话：切到聊天页，拉出第二栏，让会话栏自己弹"选角色"
                    guarded {
                        onTabChange(TAB_CHAT)
                        if (railCollapsed) {
                            railCollapsed = false
                            DesktopShellPrefs.railCollapsed = false
                        }
                        newSessionSignal++
                    }
                    true
                }
                Key.Comma -> {
                    // Ctrl+, = 设置：用户页的「外观」节（齿轮那个入口落「模型与 API」，这里给常规设置）
                    // 与第二栏点节同一套动作：先回总设置表、再滚过去（少了这两行只会停在表里当前滚动位置）
                    guarded {
                        onTabChange(TAB_PROFILE)
                        profileGroup = ProfileGroup.APPEARANCE
                        profilePage = false
                        profileAnchor++
                    }
                    true
                }
                Key.K -> {
                    // Ctrl+K = 全局搜索：聊天页第二栏的搜索框（角色名/会话名/聊天原文三维度）
                    guarded {
                        onTabChange(TAB_CHAT)
                        if (railCollapsed) {
                            railCollapsed = false
                            DesktopShellPrefs.railCollapsed = false
                        }
                        focusSearchSignal++
                    }
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }
    SideEffect { DesktopShortcuts.handler = onShortcut }
    // 离开组合（切手机布局/登出）时别留一个指向旧界面的 handler
    DisposableEffect(Unit) {
        onDispose { DesktopShortcuts.handler = null }
    }

    Row(
        Modifier
            .fillMaxSize()
            // Ctrl+滚轮调字号：在 Initial 趟拦截并消费，滚动容器（LazyColumn 等）就不会同时滚动。
            // 修饰符状态拿不到（PointerEvent.keyboardModifiers 非双端 API），Ctrl 的按下状态由
            // 窗口层按键事件记进 DesktopShortcuts.ctrlPressed。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val pe = awaitPointerEvent(PointerEventPass.Initial)
                        if (pe.type == PointerEventType.Scroll && DesktopShortcuts.ctrlPressed) {
                            val dy = pe.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (dy != 0f) {
                                val next = (ThemeState.fontScale + if (dy < 0) 0.05f else -0.05f)
                                    .coerceIn(ThemeState.MIN_FONT_SCALE, ThemeState.MAX_FONT_SCALE)
                                if (next != ThemeState.fontScale) {
                                    ThemeState.setFontScale(next)
                                    showToast("字号 ${(next * 100).roundToInt()}%")
                                }
                            }
                            pe.changes.forEach { it.consume() }
                        }
                    }
                }
            }
    ) {
        DesktopNavRail(
            entries = mainTabs.mapIndexed { i, t ->
                DesktopNavEntry(
                    icon = t.first,
                    label = t.second,
                    selected = tab == i,
                    // 有内容但收起了：图标下方点一个小圆点提示"这里还有东西"
                    railHidden = hasRail(i) && railCollapsed,
                    onClick = {
                        CrashNote.note("首页 侧栏=${t.second}")
                        when {
                            // 点当前页 = 收起/拉出第二栏（收起后也能用同一个图标拉回来）。
                            // 编辑中也不打断：这一下只动第二栏，第三栏的编辑器不受影响
                            i == tab && hasRail(i) -> railCollapsed = !railCollapsed
                            // 编辑中切页：先让编辑器走一遍「保存更改？」，关掉之后再切
                            else -> guarded {
                                onTabChange(i)
                                if (hasRail(i)) railCollapsed = false
                            }
                        }
                    }
                )
            },
            // 第一栏底部的齿轮：桌面不再"跳去整页设置"（那是手机端的形态），
            // 而是落回「用户」页的「模型与 API」分组——第三栏内嵌，第二栏随时能切到语音/外观/备份。
            onSettings = {
                CrashNote.note("首页 侧栏=设置")
                guarded {
                    onTabChange(TAB_PROFILE)
                    profileGroup = ProfileGroup.API
                    profilePage = true
                }
            },
            // M11 ③ 入口改造（用户 2026-09-18 原话）：顶部头像 →「账号」；底部 logo →「版本与安全」
            onAccount = {
                CrashNote.note("首页 侧栏=账号")
                guarded {
                    onTabChange(TAB_PROFILE)
                    profileGroup = ProfileGroup.ACCOUNT
                    profilePage = false
                    profileAnchor++
                }
            },
            onLogo = {
                CrashNote.note("首页 侧栏=版本与安全")
                guarded {
                    onTabChange(TAB_PROFILE)
                    profileGroup = ProfileGroup.ABOUT
                    profilePage = false
                    profileAnchor++
                }
            },
            avatarUri = profile?.avatarUri,
            profileName = profile?.nickname.orEmpty()
        )
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        val rail: (@Composable () -> Unit)? = when (tab) {
            TAB_DISCOVER -> {
                { DesktopDiscoverRail(discoverVm) }
            }
            TAB_CHAT -> {
                {
                    DesktopConversationRail(
                        selectedConversationId = chatTarget?.conversationId.orEmpty(),
                        refreshKey = railRefreshKey,
                        // Ctrl+N / Ctrl+K 的落点：让会话栏弹"选角色"/聚焦搜索框（见 DesktopShortcuts）
                        newSignal = newSessionSignal,
                        focusSearchSignal = focusSearchSignal,
                        // 编辑中换会话同样先过编辑器那道确认（第三栏会被抢走）
                        onOpen = { charId, convId -> guarded { openChatTo(charId, convId) } },
                        onNew = { card -> guarded { openChatTo(card.id, Routes.CONV_BLANK) } }
                    )
                }
            }
            TAB_CHARACTER -> {
                { CharacterCategoryRail(charVm) }
            }
            TAB_AI_CREATE -> {
                { AiCreateRail(aiVm) }
            }
            TAB_PROFILE -> {
                {
                    DesktopProfileRail(
                        selected = profileGroup,
                        settingsTab = settingsVm.tab,
                        onSelect = { g ->
                            profileGroup = g
                            // 第二栏点**节**＝第三栏页内**快速定位**（用户 ③）：每个节都在总设置表里，
                            // 所以每一下都是"回到表里、滚到那一节"，不再整页替换。
                            profilePage = false
                            profileAnchor++
                        },
                        onSelectSettingsTab = { t ->
                            // 例外：「模型与 API」那四个子分段**直接整页打开**到对应分段（用户 ③ 括号里
                            // 点名的那条）。它上面那一下 onSelect(API) 刚把 profilePage 置回 false，
                            // 这里再置 true ⇒ 最终是"整页、停在所选分段"。
                            settingsVm.selectTab(t)
                            profilePage = true
                        },
                        // 二级行里的整页栏目（语音服务 / 背景音乐 / 使用手册）：直接开整页，不滚动
                        onOpenPage = { g ->
                            profileGroup = g
                            profilePage = true
                        }
                    )
                }
            }
            else -> null
        }

        // 编辑中时第三栏整块换成编辑器：第二栏（分类 / 搜索 / 会话列表）照旧，随时能切
        val editing = editorTarget
        val tabContent: @Composable () -> Unit = when (tab) {
            TAB_CHAT -> {
                {
                    DesktopChatHost(
                        target = chatTarget,
                        // 聊天页顶栏的「编辑角色」：同样进第三栏，不再整页跳走
                        onEditCharacter = { openEditor(it) },
                        onSessionChanged = { railRefreshKey++ },
                        railCollapsed = railCollapsed
                    )
                }
            }
            TAB_CHARACTER -> {
                {
                    CharacterGridPane(
                        vm = charVm,
                        // 开聊（含新建会话）要连 tab 一起切过去（用户 2026-09-21：原来只设第三栏目标，
                        // 停在角色页的点看起来"毫无反应"，得自己再点一次聊天记录 tab）
                        onOpenChat = { card ->
                            onTabChange(TAB_CHAT)
                            openChatTo(card.id, Routes.CONV_RESUME)
                        },
                        onStartNewChat = { card ->
                            onTabChange(TAB_CHAT)
                            openChatTo(card.id, Routes.CONV_BLANK)
                        },
                        onOpenConversation = { charId, convId ->
                            onTabChange(TAB_CHAT)
                            openChatTo(charId, convId)
                        },
                        // 双击卡片开聊仍是"换内容"；编辑/新建则进第三栏（第 34 轮）
                        onEdit = { openEditor(it) },
                        onNew = { openEditor("new") },
                        onAiCreate = { onTabChange(TAB_AI_CREATE) }
                    )
                }
            }
            TAB_AI_CREATE -> {
                {
                    // 宽度按**当前分段的内容形态**给（第 90 轮 台账 12 ②）：模板板是一排卡片 ⇒ 看板宽度；
                    // 角色 / 形象 / 故事是三张表单 ⇒ 保持 720dp 的阅读宽度（拉满整宽的话输入框会横成一条）。
                    DesktopContentBox(
                        maxWidth = if (aiVm.tab == "模板") DesktopGridPaneMaxWidth else DesktopPaneMaxWidth
                    ) {
                        AiCreateScreen(
                            // 桌面：创作完成后同样**在第三栏开聊**，不要退回"整页聊天"的老路
                            onCreated = { card ->
                                onTabChange(TAB_CHAT)
                                openChatTo(card.id, Routes.CONV_RESUME)
                            },
                            onEdit = { openEditor(it) },
                            // 第三栏内嵌：分段与模板分类都在第二栏，这里不再画顶栏与胶囊
                            embedded = true,
                            vm = aiVm
                        )
                    }
                }
            }
            TAB_PROFILE -> {
                {
                    DesktopContentBox(maxWidth = DesktopPaneMaxWidth) {
                        ProfileScreen(
                            // 桌面三栏下这两个入口已被第二栏取代（只有手机整页还会用到）
                            onOpenSettings = onOpenSettings,
                            onOpenVoice = onOpenVoice,
                            onLogout = onLogout,
                            onSwitchAccount = onSwitchAccount,
                            desktopGroup = profileGroup,
                            settingsVm = settingsVm,
                            profileAnchorSignal = profileAnchor,
                            // 滚动跟随（用户要的"快速定位"的另一半）：滚到哪一节，第二栏就高亮哪一行
                            onStreamSection = { profileGroup = it },
                            desktopPage = profilePage,
                            // 总设置表里那四节的入口行：整页打开（含模型与 API 的四个子分段）
                            onOpenPage = { g ->
                                profileGroup = g
                                profilePage = true
                            }
                        )
                    }
                }
            }
            else -> {
                // 「发现」（TAB_DISCOVER）：导入聚合页（台账 12，第 52 轮第一批）＋精选（53 轮）。
                // 第 90 轮起第二栏是它自己的目录（视图轴 + 分组），所以第三栏按**网格页**给宽度：
                // 720dp 的窄栏是给"表单/长文"定的，多列网格用那个宽度只会剩一两列。
                {
                    DesktopContentBox(maxWidth = DesktopGridPaneMaxWidth) {
                        DiscoverScreen(
                            embedded = true,
                            desktopPane = true,
                            vm = discoverVm,
                            onGoCharacters = { onTabChange(TAB_CHARACTER) },
                            // 点精选卡即聊：切到聊天 tab，第三栏直接渲染新会话
                            onStartChat = { card ->
                                onTabChange(TAB_CHAT)
                                openChatTo(card.id, Routes.CONV_BLANK)
                            }
                        )
                    }
                }
            }
        }

        val content: @Composable () -> Unit = if (editing != null) {
            {
                DesktopContentBox(maxWidth = DesktopPaneMaxWidth) {
                    DesktopEditorHost(
                        characterId = editing,
                        closeSignal = editorClose,
                        onClose = closeEditor
                    )
                }
            }
        } else {
            tabContent
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (rail == null) {
                content()
            } else {
                DesktopRailPane(
                    rail = rail,
                    content = content,
                    railWidth = railWidth,
                    collapsed = railCollapsed,
                    onResize = { dx ->
                        // 拖动增量按当前密度换算成 dp 再夹住上下限；顺手同步到进程内单例
                        val next = railWidth + with(density) { dx.toDp() }
                        val clamped = DesktopShellPrefs.clamp(next.value.toInt())
                        railWidth = clamped.dp
                        DesktopShellPrefs.railWidth = clamped
                    },
                    onResizeEnd = {
                        // 变更即写（用户 2026-09-21 + 台账 4）：拖完立刻落一份窗口状态，不等正常退出
                        DesktopShellPrefs.onPersistRequested?.invoke()
                    },
                    onToggleCollapse = {
                        railCollapsed = !railCollapsed
                        DesktopShellPrefs.railCollapsed = railCollapsed
                        DesktopShellPrefs.onPersistRequested?.invoke()
                    }
                )
            }
        }
    }
}

/** tab 序号（与 [mainTabs] 的顺序一致，避免各处散落魔法数字） */
private const val TAB_DISCOVER = 0
private const val TAB_CHAT = 1
private const val TAB_CHARACTER = 2
private const val TAB_AI_CREATE = 3
private const val TAB_PROFILE = 4

/**
 * 拖拽提示遮罩（M11 ④）：有文件拖在窗口上方时给一圈高亮边框 + 一行说明，
 * 让"现在松手会发生什么"可预期。挂在 desktopApp 的主题 Box 里（与 DesktopToastHost 同层）。
 * **不拦指针也不消费事件**——真正的拖放由窗口层的 AWT DropTarget 接（拖拽期间指针本来
 * 就被 OLE 拖放抓着，Compose 收不到点击），这里纯展示。
 */
@Composable
fun DesktopDragOverlay() {
    if (!DesktopDragDrop.dragOver) return
    Box(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large
            )
            .padding(8.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 4.dp,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                "松开导入角色卡（JSON）",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}
