package com.mysticat.roleplay.ui.screens

/**
 * 聊天页外壳：屏幕骨架、顶栏、会话菜单。
 *
 * 2026-09-17（D10）纯结构拆分：原 3451 行的单文件按职责拆成 ——
 *  * `ChatViewModel.kt`：ViewModel 本体（会话状态、发送、流式、灵感、背景图…）
 *  * `ChatMessages.kt`：消息列表与气泡（长按菜单、多版本、编辑/回溯、Markdown）
 *  * `ChatPanels.kt`：输入栏、「更多」面板、灵感弹窗、历史 sheet
 *  * `ChatDialogs.kt`：重命名、「本会话设定」
 * 逻辑一行未动，只有文件边界与跨文件可见性（private → internal）变了。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.NarrativeStyles
import com.mysticat.roleplay.data.PromptMode
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.narrativeStyle
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.backgroundFor
import com.mysticat.roleplay.ui.bgDimOrDefault
import com.mysticat.roleplay.ui.dimmedScrims
import com.mysticat.roleplay.ui.wheelHorizontalScroll
import com.mysticat.roleplay.ui.WorldBookHitsDialog
import com.mysticat.roleplay.ui.hasWorldBook
import com.mysticat.roleplay.ui.defaultBgImageSize
import com.mysticat.roleplay.ui.effectiveBackground
import com.mysticat.roleplay.ui.DesktopDragDrop
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.GeneratedImageDialog
import com.mysticat.roleplay.ui.ImageSizeOptions
import com.mysticat.roleplay.ui.PromptDialog
import com.mysticat.roleplay.ui.rememberBgCropAspect
import com.mysticat.roleplay.ui.rememberImagePicker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    characterId: String,
    conversationId: String,
    onBack: () -> Unit,
    onEditCharacter: () -> Unit,
    /**
     * 桌面：左侧会话栏需要知道"聊天内容变了"才好重读列表。
     * 新会话是**发第一条消息时才落盘**的，聊天页自己不重读左栏就永远看不到它。
     * 手机端不传（默认空实现），行为零变化。
     */
    onSessionChanged: () -> Unit = {},
    /** 47c：发图置灰后点击的解释动作（手机跳整页设置，桌面切到「模型与API」分组；不传则只给提示） */
    onOpenSettings: () -> Unit = {},
    vm: ChatViewModel = chatViewModel(characterId, conversationId)
) {
    val char = vm.character
    // 进聊天页的读盘（角色卡 + 本角色会话列表）搬到后台了，读完前 [ChatViewModel.ready] 为假。
    // 这一段必须先于"角色不存在"判断——否则读盘那几十~几百毫秒里界面会撒谎说角色被删了。
    // 早退同时意味着下面那些 LaunchedEffect/DisposableEffect 要等真正就绪才开始跑，这正是我们要的
    // （它们会去读 vm.conversation，还没值时读了没意义）。
    if (!vm.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (char == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("角色不存在或已被删除")
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
        return
    }

    // 麦克风权限（Step 3）：本项目第一次用。授权结果只给提示，**不自动开录** ——
    // 弹窗刚关就自动录音会让人措手不及（而且此时手指早已离开按钮）
    val micPermission = Platform.ui.rememberMicPermissionLauncher { granted ->
        vm.noteVoice(
            if (granted) "已获得麦克风权限，现在可以用麦克风说话了"
            else "没有麦克风权限，语音输入不可用（可在系统设置 → 应用 → 鲸鱼 → 权限里开启）"
        )
    }
    // 从设置页/编辑页再回到聊天页时重算：ViewModel 活过这一趟，init 不会再跑。
    // 角色卡也要**重读一遍**：卡是发消息时真正用的那一份，在编辑页改完世界书/人设回来不重读，
    // 表现就是"改了没生效"（实测：世界书命中面板还在念旧书）。
    LaunchedEffect(Unit) {
        vm.refreshVision(); vm.refreshVoiceMode(); vm.refreshAutoRead(); vm.reloadCharacter()
    }
    // 麦克风用法：桌面固定点按（鼠标按住不放很难受，且按下时手指占着键、点不到「×」取消），
    // 手机读设置里的「按住 / 点按」
    val tapToTalk = isDesktopLayout || vm.voiceTapToTalk
    // 桌面左栏的刷新信号（键是"会话 id + 消息条数"，只有真的变了才通知，流式打字时每拍都变的不算）
    LaunchedEffect(vm.conversation?.id, vm.conversation?.messages?.size) { onSessionChanged() }
    val pickChatImage = rememberImagePicker { uri -> uri?.let { vm.attachPendingImage(it) } }
    // 拖图到窗口 / Ctrl+V 贴截图 → 与「添加图片」同一个入口（挂到待发送附件）。
    // 只在聊天页活着时注册；离开组合即撤，别的页再注册自己的。
    val chatImageSink: (String) -> Boolean = { uri ->
        vm.attachPendingImage(uri); true
    }
    SideEffect { DesktopDragDrop.imageSink = chatImageSink }
    DisposableEffect(Unit) {
        onDispose { if (DesktopDragDrop.imageSink === chatImageSink) DesktopDragDrop.imageSink = null }
    }
    val pickLocalBg = rememberImagePicker { uri ->
        uri?.let { vm.setBackground(it, vm.bgMenuTarget) }
    }
    // 问题 #2：默认精简（只显示最新一条），点「历史」展开全部
    var showAllMessages by rememberSaveable { mutableStateOf(false) }
    // 移除背景图会顺手清理图片文件（Repository.reclaimImages），不可恢复 ⇒ 先确认（2026-09-21 统一补）
    var confirmRemoveBg by remember { mutableStateOf(false) }
    // 本会话背景音乐弹窗（会话级架构）
    var showSessionBgm by remember { mutableStateOf(false) }
    // 世界书命中面板
    var showBookHits by remember { mutableStateOf(false) }
    // 圆形加号展开的互动面板：本地状态即可，它只影响界面不落盘
    var showPlusPanel by remember { mutableStateOf(false) }
    // 面板项（列表驱动：加一项只加数据、不改布局——预留的玩法槽位靠这条）。
    // 「拍摄」本轮先占位置灰：系统相机的取图链路另做，与发图按钮同一口径（不静默消失，给一句解释）。
    val plusCells = listOf(
        PanelCell(
            key = "album",
            label = "从相册选图",
            icon = Icons.Filled.Image,
            disabled = !vm.visionChat,
            hint = if (vm.visionChat) null else "当前模型不支持图片输入，可在「模型与API」里换支持视觉的模型",
            onClick = { showPlusPanel = false; pickChatImage() }
        ),
        PanelCell(
            key = "camera",
            label = "拍摄",
            icon = Icons.Filled.PhotoCamera,
            disabled = true,
            hint = "拍摄暂未接入，先用「从相册选图」"
        )
    )
    // 角色信息面板（顶栏角色名旁「‹」）：本地状态即可，它只影响界面不落盘
    var showCharacterInfo by rememberSaveable { mutableStateOf(false) }
    // 背景图铺满整屏（含顶栏/输入栏底下），顶栏改用白色内容 + scrim 保证可读。
    // A 批次分端：取**本平台**那份（会话覆盖优先，回退角色卡），老数据只有一份时两端都用它。
    // 菜单开着时改成**预览菜单里选中的那一端**：用户 2026-09-21 反馈"切换手机端/桌面端背景没有发生变化"
    // ——原来这一行写死取本平台那份，chip 切了聊天页却纹丝不动，看不出那端到底存了哪张图。
    // 关掉菜单就回到本平台那一端（手机上不该一直显示桌面端那张）。
    val background = effectiveBackground(
        char,
        vm.conversation,
        if (vm.showSessionPanel) vm.bgMenuTarget else BgTarget.current
    )
    // 会话级暗化强度：聊天区与顶栏两层 scrim 的透明度系数。
    // 页面里拖滑杆只动草稿，松手才落盘（见 setBgDim 的说明）
    var bgDimDraft by remember { mutableStateOf(1f) }
    LaunchedEffect(vm.conversation?.id, vm.conversation?.bgDim) {
        bgDimDraft = vm.conversation?.bgDimOrDefault() ?: 1f
    }

    // Scaffold 之上挂二级整页（会话设置/角色信息）：外层 Box 保证覆盖层真的铺满本页区域
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
                ChatTopBar(
                    char = char,
                    title = vm.conversation?.title ?: "新会话",
                    hasBackground = background != null,
                    bgDim = bgDimDraft,
                ttsAutoRead = vm.ttsAutoRead,
                // 四种形态都显示：朗读是输出通道，与形态无关（见 `EngineSpec.autoTts` 的注释）
                showTts = vm.engine.autoTts,
                onToggleTts = { vm.toggleTtsAutoRead() },
                onBack = onBack,
                onMore = { vm.showSessionPanel = true },
                onEdit = onEditCharacter,
                onCharacterInfo = { showCharacterInfo = true }
            )
        },
        bottomBar = {
            Column {
                // 生效风格 chip（左）+ 历史（图标，右）；工具按钮已融进输入栏
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 生效中的风格 chip：每根被启用过的轴一个（多选轴合成一个），一眼看得出"设置真的生效了"。
                    // 轴多时可横向滚动，不再挤掉右侧的历史开关。
                    val styleLabels = vm.conversation
                        ?.let { NarrativeStyles.labelsOf(it.narrativeStyle()) } ?: emptyList()
                    val styleChipsScroll = rememberScrollState()
                    Row(
                        Modifier
                            .weight(1f)
                            .wheelHorizontalScroll(styleChipsScroll)
                            .horizontalScroll(styleChipsScroll),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        styleLabels.forEach { StyleChip(it) }
                        // 临时导演已装备时也给一枚 chip（它会立刻用在下一轮，别让人忘了）
                        if (vm.oneShotHint.isNotBlank()) {
                            StyleChip("导演：" + vm.oneShotHint.take(10) + if (vm.oneShotHint.length > 10) "…" else "")
                        }
                    }
                    MessageHistoryToggle(
                        total = vm.conversation?.messages?.count { it.role != "scene" } ?: 0,
                        showAll = showAllMessages,
                        onToggle = { showAllMessages = !showAllMessages }
                    )
                }
                // 非模态提示（空回复等）：一行小胶囊，点一下消掉；不挡聊天页，重试与「灵感回复」都够得着
                vm.softNotice?.let { notice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                            modifier = Modifier.clickable { vm.softNotice = null }
                        ) {
                            Text(
                                "$notice  ✕",
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                // 语音朗读的提示（P-F2 v1）：系统没有中文语音包这类"不是报错但必须让用户知道"的情况，
                // 与 softNotice 一样是非模态一行，点一下消掉
                vm.ttsNotice?.let { notice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                            modifier = Modifier.clickable { vm.clearTtsNotice() }
                        ) {
                            Text(
                                "$notice  ✕",
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                // 语音输入进行中的状态一行（录音中点一下会中断，所以这里不给"✕"，只报状态）
                if (vm.voicePhase != ChatViewModel.VoicePhase.IDLE) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when {
                                vm.voicePhase == ChatViewModel.VoicePhase.RECORDING ->
                                    if (tapToTalk) "🎙 正在录音…再点一下结束（× 取消）" else "🎙 正在录音…松开手指结束"

                                vm.voicePhase == ChatViewModel.VoicePhase.TRANSCRIBING -> "正在识别…"
                                tapToTalk -> "🎙 请说话…说完再点一下麦克风"
                                else -> "🎙 请说话…说完松开手指"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // 语音输入的提示（Step 3）：与上面两条同一口径，非模态一行、点一下消掉
                vm.voiceNotice?.let { notice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                            modifier = Modifier.clickable { vm.clearVoiceNotice() }
                        ) {
                            Text(
                                "$notice  ✕",
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                ChatInputBar(
                    value = vm.input,
                    sending = vm.sending,
                    pendingImage = vm.pendingImage,
                    onClearImage = vm::clearPendingImage,
                    onValueChange = vm::updateInput,
                    onSend = vm::send,
                    onToolClick = { vm.showToolPanel = true },
                    canPickImage = vm.visionChat,
                    onPickImage = { pickChatImage() },
                    // 47c：不支持视觉时按钮置灰但保留位置，点一下给非模态解释并跳设置页
                    onPickImageUnavailable = {
                        vm.softNotice = "当前模型不支持图片输入，可在「模型与API」里换支持视觉的模型"
                        onOpenSettings()
                    },
                    voicePhase = vm.voicePhase,
                    onVoiceStart = {
                        // 权限在这一层处理（要 Activity 的 ResultLauncher）：没有权限先申请，
                        // 授权成功后用户再点一次即可 —— 不去猜"授权后要不要自动开录"
                        if (Platform.ui.hasMicPermission()) {
                            vm.startVoiceInput()
                        } else {
                            vm.noteVoice("语音输入需要麦克风权限，请在弹窗里允许")
                            micPermission()
                        }
                    },
                    onVoiceEnd = vm::finishVoiceInput,
                    tapToTalk = tapToTalk,
                    onVoiceCancel = vm::cancelVoiceInput,
                    placeholder = vm.engine.inputPlaceholder,
                    // 圆形加号 → 互动面板（项位列表驱动）
                    plusOpen = showPlusPanel,
                    onPlusToggle = { showPlusPanel = !showPlusPanel },
                    plusCells = plusCells
                )
            }
        }
    ) { padding ->
        ChatMessages(
            modifier = Modifier.fillMaxSize(),
            insets = padding,
            char = char,
            messages = vm.conversation?.messages ?: emptyList(),
            sending = vm.sending,
            draft = vm.draft,
            backgroundUri = background,
            bgDim = bgDimDraft,
            showAll = showAllMessages,
            // 气泡要的回调在这里打成一束，往下只传这一个参数
            actions = MessageActions(
                onCopy = { text -> copyToClipboard(text) },
                onRegenerate = { idx -> vm.regenerate(idx) },
                onReformat = { idx -> vm.reformat(idx) },
                onRollback = { idx -> vm.rollback(idx) },
                onBranch = { idx -> vm.branchAt(idx) },
                onEdit = { idx, text -> vm.editMessage(idx, text) },
                onSwitchVariant = { idx, v -> vm.switchVariant(idx, v) },
                onDeleteVariant = { idx, v -> vm.deleteVariant(idx, v) },
                onSpeak = { text -> vm.readAloud(text) },
                onStopSpeak = { vm.stopSpeaking() }
            ),
            ttsSpeaking = vm.ttsSpeaking,
            onInspiration = { vm.openInspirations() },
            inspirationLoading = vm.inspirationLoading,
            engine = vm.engine,
            // 工具形态的「使用示例」（＝卡里的开场白）点击即当任务填入输入框
            examples = vm.greetingExamples,
            onUseExample = { vm.updateInput(it) }
        )
    }

    // 会话设置页：⋮ 进来的二级整页（用户 2026-09-26 拍板：不要拉取窗口，要独立页面）。
    // 「先关页面，再执行动作」沿用旧菜单的统一口径，避免新弹层叠在页面上。
    if (vm.showSessionPanel) {
        ChatOverlayPage(title = "会话", onBack = { vm.showSessionPanel = false }) {
            ChatPanelSection("场景与氛围")
            ChatBackgroundSection(
                target = vm.bgMenuTarget,
                onPickTarget = { vm.bgMenuTarget = it },
                convField = if (vm.bgMenuTarget == BgTarget.Desktop) vm.conversation?.backgroundUriDesktop
                else vm.conversation?.backgroundUri,
                cardField = char.backgroundFor(vm.bgMenuTarget),
                dim = bgDimDraft,
                onDimChange = { bgDimDraft = it },
                onDimCommit = { vm.setBgDim(if (bgDimDraft >= 0.999f) 1f else bgDimDraft) },
                onCopyOtherEnd = { vm.copyBackgroundToOtherEnd(vm.bgMenuTarget) },
                onAiBackground = { vm.showSessionPanel = false; vm.showBgPrompt = true },
                onLocalBackground = { vm.showSessionPanel = false; pickLocalBg() },
                onRemoveBackground = { vm.showSessionPanel = false; confirmRemoveBg = true }
            )
            ChatPanelDivider()
            if (vm.engine.sessionBgm) {
                ChatPanelRow(
                    Icons.Filled.LibraryMusic, "背景音乐",
                    status = if (vm.conversation?.bgm != null) "已设" else "未设"
                ) { vm.showSessionPanel = false; showSessionBgm = true }
            }
            ChatPanelDivider()
            ChatPanelSection("本会话")
            if (vm.engine.relationshipMemory) {
                ChatPanelRow(
                    Icons.Filled.Person, "本会话设定",
                    status = if (vm.conversation?.userSetting.isNullOrBlank()) "未填" else "已填"
                ) { vm.showSessionPanel = false; vm.showSettingDialog = true }
                ChatPanelRow(
                    Icons.Filled.EditNote, vm.engine.memoryEntryLabel,
                    status = if (vm.conversation?.memory.isNullOrBlank()) "未填" else "已填"
                ) { vm.showSessionPanel = false; vm.showMemoryDialog = true }
            }
            // 世界书命中只在**这张卡真有书**时给入口：空书点进去是空面板，
            // 用户会以为功能没做完（口径与 BGM/本会话设定一致：按形态隐藏）
            if (hasWorldBook(char)) {
                ChatPanelRow(Icons.Filled.MenuBook, "世界书命中") {
                    vm.showSessionPanel = false; showBookHits = true
                }
            }
            ChatPanelDivider()
            ChatPanelSection("会话管理")
            ChatPanelRow(
                Icons.Filled.History, "会话历史",
                status = if (vm.conversations.isEmpty()) "空" else "${vm.conversations.size} 个"
            ) { vm.showSessionPanel = false; vm.showHistory = true }
            ChatPanelRow(Icons.Filled.Add, "新建会话") { vm.startNewConversation() }
            ChatPanelRow(Icons.Filled.Edit, "重命名当前会话") {
                vm.showSessionPanel = false; vm.showRenameDialog = true
            }
            // 删除放最后并带二次确认（confirmDelete 弹窗在下方）
            ChatPanelRow(Icons.Filled.Delete, "删除当前会话", danger = true) {
                vm.conversation?.let { c -> vm.requestDelete(c.id) }
            }
        }
    }

    // 角色信息页（顶栏角色名旁的「‹」入口）：卡级设置的归口，首行＝头像 + 名字。
    // 顶栏铅笔保留；旧下拉里的「编辑角色卡」重复项由这里收编。
    if (showCharacterInfo) {
        ChatOverlayPage(title = "角色信息", onBack = { showCharacterInfo = false }) {
            CharacterInfoPanelContent(
                avatarUri = char.avatarUri,
                name = char.name,
                formLabel = vm.engine.label,
                tagline = char.tagline,
                persona = char.personaDisplayText(),
                greeting = char.greeting,
                hasBook = hasWorldBook(char),
                onWorldBookHits = { showCharacterInfo = false; showBookHits = true },
                onEditCharacter = { showCharacterInfo = false; onEditCharacter() }
            )
        }
    }
    }

    // 会话历史
    if (vm.showHistory) {
        HistorySheet(
            conversations = vm.conversations,
            currentId = vm.conversation?.id,
            onNew = vm::startNewConversation,
            onOpen = vm::switchTo,
            onDelete = vm::requestDelete,
            onDismiss = { vm.showHistory = false }
        )
    }

    // 删除确认
    vm.deletingId?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("删除这个会话？") },
                    text = { Text("会话记录与其中的图片删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("取消") } }
        )
    }

    // 移除背景图确认（分端：只影响菜单里选中的那一端）
    if (confirmRemoveBg) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemoveBg = false },
            title = { Text("移除${vm.bgMenuTarget.label}背景图？") },
            text = { Text("本会话不再使用它；这张图若没有别处引用会被清理掉，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveBg = false
                    vm.setBackground(null, vm.bgMenuTarget)
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { confirmRemoveBg = false }) { Text("取消") } }
        )
    }

    // AI 背景描述
    if (vm.showBgPrompt) {
        val target = vm.bgMenuTarget
        PromptDialog(
            title = "AI 生成${target.label}聊天背景",
            // 问题 #28：预填上次的提示词，返回来调整时不用重写
            initial = vm.lastBgPrompt,
            placeholder = if (target == BgTarget.Desktop) {
                "描述背景场景，例：雨夜霓虹的赛博城市街道，横屏，电影感"
            } else {
                "描述背景场景，例：雨夜霓虹的赛博城市街道，竖屏，电影感"
            },
            loading = vm.generatingBg,
            sizeOptions = ImageSizeOptions,
            defaultSize = defaultBgImageSize(target),
            onConfirm = { prompt, size -> vm.generateBackground(prompt, size, target) },
            onDismiss = { vm.showBgPrompt = false }
        )
    }

    // 问题 #8：生成结果先预览，再决定 保存 / 应用 / 重试
    vm.pendingBg?.let { uri ->
        GeneratedImageDialog(
            title = "${vm.pendingBgTarget.label}聊天背景生成结果",
            uri = uri,
            // 按生成时定下的那一端取裁剪比例（桌面端给横框、手机端给竖框）
            cropAspect = rememberBgCropAspect(vm.pendingBgTarget),
            onApply = { final -> vm.applyPendingBackground(final) },
            onRetry = { vm.retryBackground() },
            onDismiss = { vm.discardPendingBackground() }
        )
    }

    // 重命名当前会话
    if (vm.showRenameDialog) {
        RenameDialog(
            initial = vm.conversation?.title ?: "新会话",
            onConfirm = vm::renameCurrent,
            onDismiss = { vm.showRenameDialog = false }
        )
    }

    // 本会话设定
    if (vm.showSettingDialog) {
        ChatSettingDialog(
            current = vm.conversation?.userSetting ?: "",
            onDismiss = { vm.showSettingDialog = false },
            onUse = vm::applyUserSetting,
            onCreate = { n, c ->
                Repository.addUserSetting(n, c)
                vm.applyUserSetting(c)
            }
        )
    }

    // 本会话背景音乐（会话级架构；立即生效，配置跟会话走）
    if (showSessionBgm) {
        SessionBgmDialog(
            current = vm.conversation?.bgm,
            onApply = { vm.setSessionBgm(it) },
            onDismiss = { showSessionBgm = false }
        )
    }

    // 世界书命中可见性：这一轮会注入哪些条目、各被哪个词触发。
    // 只有卡里真有书时才给入口（见 ChatMenu 的 showWorldBook），这里是它的落点。
    if (showBookHits) {
        WorldBookHitsDialog(
            card = char,
            messages = vm.conversation?.messages.orEmpty(),
            onDismiss = { showBookHits = false }
        )
    }

    // 工具页：点 Tune 不再是长清单，而是一墙卡片，每张卡进各自的设置页/弹窗。
    // 「叙事设置 / 角色记忆 / 本会话设定」沿用既有落点；原先内联在长清单里的「思考强度」「本轮特别指示」
    // 各自独立成对话框（卡片只报状态，点开改）。
    if (vm.showToolPanel) {
        // 与设置页互通的那份值：面板打开时读一次，改动由对话框回写（别在每次重组里读文件）
        var thinking by remember { mutableStateOf(Repository.loadSettings().chatThinking) }
        var showThinkingDialog by remember { mutableStateOf(false) }
        var showToolHint by remember { mutableStateOf(false) }
        val advancedCount = vm.conversation?.let { c ->
            val style = c.narrativeStyle()
            NarrativeStyles.AXES.count { !it.quick && style.valueOf(it.key).isNotEmpty() }
        } ?: 0
        val cards = buildList {
            if (vm.engine.narrativeAxes) add(
                PanelCell(
                    key = "narrative",
                    label = "叙事设置",
                    icon = Icons.Filled.AutoAwesome,
                    status = if (advancedCount > 0) "已启用 $advancedCount 项" else "节奏 / 篇幅"
                ) { vm.showToolPanel = false; vm.showStyleScreen = true }
            )
            add(
                PanelCell(
                    key = "hint",
                    label = "本轮特别指示",
                    icon = Icons.Filled.EditNote,
                    status = if (vm.oneShotHint.isNotBlank()) "已生效" else "未设"
                ) { showToolHint = true }
            )
            add(
                PanelCell(
                    key = "thinking",
                    label = "思考强度",
                    icon = Icons.Filled.Psychology,
                    status = when (thinking) { "off" -> "关闭"; "high" -> "深度"; else -> "默认" }
                ) { showThinkingDialog = true }
            )
            if (vm.engine.relationshipMemory) {
                add(
                    PanelCell(
                        key = "setting",
                        label = "本会话设定",
                        icon = Icons.Filled.Person,
                        status = if (vm.conversation?.userSetting.isNullOrBlank()) "未填" else "已填"
                    ) { vm.showToolPanel = false; vm.showSettingDialog = true }
                )
                add(
                    PanelCell(
                        key = "memory",
                        label = vm.engine.memoryEntryLabel,
                        icon = Icons.Filled.AutoStories,
                        status = if (vm.conversation?.memory.isNullOrBlank()) "未填" else "已填"
                    ) { vm.showToolPanel = false; vm.showMemoryDialog = true }
                )
            }
            // 世界书命中只在**这张卡真有书**时给入口（空书点进去是空面板，会让人以为功能没做完）
            if (hasWorldBook(char)) add(
                PanelCell(key = "book", label = "世界书命中", icon = Icons.Filled.MenuBook) {
                    vm.showToolPanel = false; showBookHits = true
                }
            )
        }
        ToolCardsPage(cards = cards, onBack = { vm.showToolPanel = false })
        if (showThinkingDialog) {
            ToolThinkingDialog(
                current = thinking,
                suggest = vm.engine.suggestThinking,
                onChange = { v ->
                    thinking = v
                    Repository.saveSettings(Repository.loadSettings().copy(chatThinking = v))
                },
                onDismiss = { showThinkingDialog = false }
            )
        }
        if (showToolHint) {
            ToolHintDialog(
                current = vm.oneShotHint,
                onApply = { vm.oneShotHint = it; showToolHint = false },
                onClear = { vm.oneShotHint = ""; showToolHint = false },
                onDismiss = { showToolHint = false }
            )
        }
    }

    // 「叙事设置」高级页（P-F1）：全屏覆盖。用 Dialog 拿窗口层，才能稳稳盖住顶栏、输入栏与底部面板；
    // 状态仍归 ChatViewModel（只回写 Conversation.narrative），不另存一份，避免两个内存态互相覆盖。
    if (vm.showStyleScreen) {
        vm.conversation?.let { c ->
            Dialog(
                onDismissRequest = { vm.showStyleScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NarrativeStyleScreen(
                        style = c.narrativeStyle(),
                        onChange = vm::applyNarrative,
                        onBack = { vm.showStyleScreen = false }
                    )
                }
            }
        }
    }

    // 会话级状态编辑（每轮注入）：陪伴 / 多线叫「角色记忆」，玩法叫「本局进度」（同一字段，两种语义）
    if (vm.showMemoryDialog) {
        val play = vm.engine.promptMode == PromptMode.PLAY
        MemoryEditDialog(
            initial = vm.conversation?.memory ?: "",
            onConfirm = vm::applyMemory,
            onDismiss = { vm.showMemoryDialog = false },
            title = vm.engine.memoryEntryLabel,
            hint = if (play) "这一局每轮都会看到的进度（已确认的结论、计分、进行到哪一步）。会话级，只属于这一局。" +
                "聊几句之后 AI 也会自己整理更新它。"
            else "TA 每轮都会看到的长期设定与关键事件（如约定、身份、重要转折）。会话级，只属于本次会话。",
            placeholder = if (play) "例：已排除：蒋介石、孙中山；当前在第 9 问；用户一直在回答「不是」…"
            else "例：用户答应过带 TA 去看海；TA 的左肩有旧伤；两人已经和好过一次…"
        )
    }

    // 灵感回复（0.1.2）：给几条可以直接发的话，点选填入输入框（不直接发送）
    if (vm.showInspiration) {
        InspirationDialog(
            loading = vm.inspirationLoading,
            error = vm.inspirationError,
            items = vm.inspirations,
            onReload = { vm.loadInspirations() },
            onPick = vm::pickInspiration,
            onDismiss = { vm.showInspiration = false }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    char: CharacterCard,
    title: String,
    hasBackground: Boolean,
    /** 背景暗化强度（会话级）：顶栏 scrim 的透明度系数 */
    bgDim: Float = 1f,
    /** 新回复自动朗读（顶栏喇叭：开＝喇叭，关＝带斜杠的喇叭） */
    ttsAutoRead: Boolean,
    /** 这个形态要不要显示自动朗读开关（引擎声明，见 `EngineSpec.autoTts`；四种形态当前都为真） */
    showTts: Boolean = true,
    onToggleTts: () -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onEdit: () -> Unit,
    onCharacterInfo: () -> Unit
) {
    // 背景图铺满整屏后顶栏直接压在图上：加一层由上到下的 scrim，字与图标转白，保证清楚
    val fg = if (hasBackground) Color.White else MaterialTheme.colorScheme.onSurface
    val sub = if (hasBackground) Color.White.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (hasBackground) {
                    Modifier.background(
                        Brush.verticalGradient(
                            dimmedScrims(
                                listOf(Color(0xCC000000), Color(0x73000000), Color(0x00000000)),
                                bgDim
                            )
                        )
                    )
                } else Modifier
            )
    ) {
        TopAppBar(
            title = {
                Column {
                    // 角色信息入口（两端都做）：角色名右侧一枚浅灰「‹」，表示名字能点进去；
                    // 点开是角色信息面板（卡形态、文本设定与编辑入口的归口）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onCharacterInfo)
                    ) {
                        Text(
                            char.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                            maxLines = 1
                        )
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = "查看角色信息",
                            tint = sub,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(18.dp)
                        )
                    }
                    Text(
                        if (title.isBlank()) "新会话" else title,
                        style = MaterialTheme.typography.bodySmall,
                        color = sub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                // 桌面（三栏）没有"返回"这一说：会话在第三栏里直接换，左栏随时可切。
                // 手机端原样保留返回箭头（返回栈仍然是它的主要导航手段）。
                if (!isDesktopLayout) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = fg)
                    }
                }
            },
            actions = {
                // 自动朗读开关（用户 2026-09-16 要求：放顶栏、关掉就是给喇叭打个斜杠）
                if (showTts) {
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            if (ttsAutoRead) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (ttsAutoRead) "新回复自动朗读：开" else "新回复自动朗读：关",
                            tint = fg
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑角色卡", tint = fg)
                }
                IconButton(onClick = onMore) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = fg)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}

