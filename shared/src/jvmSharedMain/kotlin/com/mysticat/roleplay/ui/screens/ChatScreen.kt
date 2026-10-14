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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.draw.clip
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
import com.mysticat.roleplay.data.ModelScene
import com.mysticat.roleplay.data.NarrativeStyles
import com.mysticat.roleplay.data.PromptMode
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.data.narrativeStyle
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.ArrowScrollRow
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.backgroundFor
import com.mysticat.roleplay.ui.bgDimOrDefault
import com.mysticat.roleplay.ui.components.WhaleBarGlass
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.components.WhaleStickerOverlay
import com.mysticat.roleplay.ui.WorldBookHitsDialog
import com.mysticat.roleplay.ui.CoverageGapsDialog
import com.mysticat.roleplay.ui.CropDialog
import com.mysticat.roleplay.ui.hasWorldBook
import com.mysticat.roleplay.ui.defaultBgImageSize
import com.mysticat.roleplay.ui.effectiveBackground
import com.mysticat.roleplay.ui.DesktopDragDrop
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.GeneratedImageDialog
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.ImageSizeOptions
import com.mysticat.roleplay.ui.PromptDialog
import com.mysticat.roleplay.ui.rememberBgCropAspect
import com.mysticat.roleplay.ui.rememberCameraCapture
import com.mysticat.roleplay.ui.rememberImagePicker
import com.mysticat.roleplay.ui.rememberImageSourcePicker
import com.mysticat.roleplay.ui.rememberMaterialPicker
import kotlinx.coroutines.launch

/**
 * 开发者模式的组合内镜像。`Repository.loadSettings()` 是普通快照读取——开关变了**不会触发
 * 重组**，聊天页会一直用旧值（0.2.4 批次 A 用户实测"开了开发者模式也看不到思考"）。
 * 聊天页只读这里的 Compose 状态；设置入口（用户页的「开发者模式」行）改完直写存档后调
 * [refresh]，聊天页立刻重组。聊天页每次进入组合也刷新一次（覆盖"从别处改了设置回来"）。
 */
object DeveloperModeMirror {
    var enabled by mutableStateOf(false)
    fun refresh() {
        enabled = Repository.loadSettings().developerMode
    }
}

/**
 * 预算三档（宽松 / 节省 / 自定义）的组合内镜像——道理同 [DeveloperModeMirror]：
 * 会话页「输出预算」行的状态字读这里；对话框拨动即存后调 [refresh]，行内状态立刻跟着变。
 */
object BudgetMirror {
    var mode by mutableStateOf(com.mysticat.roleplay.data.BUDGET_MODE_LOOSE)
    fun refresh() {
        mode = Repository.loadSettings().budgetMode
    }

    /** 会话页那行的状态字 */
    val label: String
        get() = when (mode) {
            com.mysticat.roleplay.data.BUDGET_MODE_SAVING -> "节省"
            com.mysticat.roleplay.data.BUDGET_MODE_CUSTOM -> "自定义"
            else -> "宽松"
        }
}

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
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
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
        DeveloperModeMirror.refresh()
        BudgetMirror.refresh()
        vm.refreshVision(); vm.refreshVoiceMode(); vm.refreshAutoRead(); vm.reloadCharacter()
    }
    // 麦克风用法：桌面固定点按（鼠标按住不放很难受，且按下时手指占着键、点不到「×」取消），
    // 手机读设置里的「按住 / 点按」
    val tapToTalk = isDesktopLayout || vm.voiceTapToTalk
    // 桌面左栏的刷新信号（键是"会话 id + 消息条数"，只有真的变了才通知，流式打字时每拍都变的不算）
    LaunchedEffect(vm.conversation?.id, vm.conversation?.messages?.size) { onSessionChanged() }
    val pickChatImage = rememberImagePicker { uri -> uri?.let { vm.attachPendingImage(it) } }
    // 「从素材库选择」：与相册/拍摄**同一个落点**（都进待发送附件），只是来源不同——
    // 生成过的图直接取回来用，不必先存到相册再选一遍
    val pickFromMaterials = rememberMaterialPicker { uri -> uri?.let { vm.attachPendingImage(it) } }
    // 拍摄与相册**同一个落点**（都进待发送附件）：来源不同、之后一视同仁
    val shootChatImage = rememberCameraCapture { uri -> uri?.let { vm.attachPendingImage(it) } }
    // 拖图到窗口 / Ctrl+V 贴截图 → 与「添加图片」同一个入口（挂到待发送附件）。
    // 只在聊天页活着时注册；离开组合即撤，别的页再注册自己的。
    val chatImageSink: (String) -> Boolean = { uri ->
        vm.attachPendingImage(uri); true
    }
    SideEffect { DesktopDragDrop.imageSink = chatImageSink }
    DisposableEffect(Unit) {
        onDispose { if (DesktopDragDrop.imageSink === chatImageSink) DesktopDragDrop.imageSink = null }
    }
    val pickLocalBg = rememberImageSourcePicker { uri ->
        uri?.let { vm.setBackground(it, vm.bgMenuTarget) }
    }
    // 问题 #2：默认精简（只显示最新一条），点「历史」展开全部
    var showAllMessages by rememberSaveable { mutableStateOf(false) }
    // 移除背景图会顺手清理图片文件（Repository.reclaimImages），不可恢复 ⇒ 先确认（2026-09-21 统一补）
    var confirmRemoveBg by remember { mutableStateOf(false) }
    // 背景裁剪（0.3.0 反馈 5）：开着＝正在裁哪一端那张图
    var bgCropTarget by remember { mutableStateOf<BgTarget?>(null) }
    // 本会话背景音乐弹窗（会话级架构）
    var showSessionBgm by remember { mutableStateOf(false) }
    // 世界书命中面板
    var showBookHits by remember { mutableStateOf(false) }
    // 关键词覆盖检查（独立入口：它的门不看这张卡有没有书）
    var showCoverage by remember { mutableStateOf(false) }
    // 上下文查看器（台账 85）：最近一次请求的装配产物，只展示不发请求
    var showRequest by remember { mutableStateOf(false) }
    // 圆形加号展开的互动面板：本地状态即可，它只影响界面不落盘
    var showPlusPanel by remember { mutableStateOf(false) }
    // 面板项（列表驱动：加一项只加数据、不改布局——预留的玩法槽位靠这条）。
    // 「拍摄」＝系统相机（台账 79 接线）：与「从相册选图」同一道尺寸闸门、同一落点，只有来源不同。
    // 桌面端不调相机 ⇒ 与"模型不支持视觉"一样置灰留位置，各给一句自己的解释（不静默消失）。
    val canShoot = !isDesktopLayout && vm.visionChat
    val plusCells = listOf(
        PanelCell(
            key = "album",
            label = "从相册选图",
            icon = Icons.Outlined.Image,
            disabled = !vm.visionChat,
            hint = if (vm.visionChat) null else "当前模型不支持图片输入，可在「模型与API」里换支持视觉的模型",
            onClick = { showPlusPanel = false; pickChatImage() }
        ),
        PanelCell(
            key = "camera",
            label = "拍摄",
            icon = Icons.Outlined.PhotoCamera,
            disabled = !canShoot,
            hint = when {
                isDesktopLayout -> "桌面端不调相机，先用「从相册选图」"
                !vm.visionChat -> "当前模型不支持图片输入，可在「模型与API」里换支持视觉的模型"
                else -> null
            },
            onClick = { showPlusPanel = false; shootChatImage() }
        ),
        PanelCell(
            key = "materials",
            label = "从素材库选择",
            icon = Icons.Outlined.PhotoLibrary,
            disabled = !vm.visionChat,
            hint = if (vm.visionChat) null else "当前模型不支持图片输入，可在「模型与API」里换支持视觉的模型",
            onClick = { showPlusPanel = false; pickFromMaterials() }
        )
    )
    // 工具抽屉（点 Tune 展开，用户 2026-09-26 反馈：不再是整页，与加号面板同款）。
    // **第 214 轮归口**（用户点名"工具和侧边栏/更多重复"）：工具面板只留「这条会话怎么生成」——
    // 叙事设置／本轮特别指示／输出预算／上次请求；「会话里装了什么」一律住「更多」页
    // （手机 ⋮ 整页＝桌面常驻右栏，同一份 [SessionRows]）：本会话设定／角色记忆／当前处境／
    // 隐藏状态／世界书命中／关键词覆盖检查；「模型与思考强度」唯一入口＝输入条 chip（194 起）。
    var showToolHint by remember { mutableStateOf(false) }
    val toolCards = if (vm.showToolPanel) {
        val advancedCount = vm.conversation?.let { c ->
            val style = c.narrativeStyle()
            NarrativeStyles.AXES.count { !it.quick && style.valueOf(it.key).isNotEmpty() }
        } ?: 0
        buildList {
            if (vm.engine.narrativeAxes) add(
                PanelCell(
                    key = "narrative",
                    label = "叙事设置",
                    icon = Icons.Outlined.AutoAwesome,
                    section = "叙事风格",
                    status = if (advancedCount > 0) "已启用 $advancedCount 项" else "节奏 / 篇幅"
                ) { vm.showToolPanel = false; vm.showStyleScreen = true }
            )
            add(
                PanelCell(
                    key = "hint",
                    label = "本轮特别指示",
                    icon = Icons.Outlined.EditNote,
                    section = "叙事风格",
                    status = if (vm.oneShotHint.isNotBlank()) "已生效" else "未设"
                ) { showToolHint = true }
            )
            // 模型与思考强度（49 轮，用户 10-10「手机端会话还没有修改模型的入口，应该在工具栏里」）：
            // 桌面输入条上有常驻 chip，手机端在 214 归口后两端工具列表都没有它 ⇒ 手机完全没有入口（缺口）。
            // 这一格与桌面 chip 开的是**同一块面板**（vm.showModelPanel：桌面锚定浮层/手机抽屉），字段两端一致。
            add(
                PanelCell(
                    key = "model",
                    label = "模型与思考强度",
                    icon = Icons.Outlined.SmartToy,
                    section = "模型与预算",
                    status = vm.effectiveAiSettings().chatModel
                ) { vm.showToolPanel = false; vm.showModelPanel = true }
            )
            // 输出预算（0.2.4 批次 B ＋ 台账 113）：生成参数归工具面板（214 归口）——
            // 「更多/会话页」里那份同款行已同步撤掉，别两处都能改同一件事。
            add(
                PanelCell(
                    key = "budget",
                    label = "输出预算",
                    icon = Icons.Outlined.Tune,
                    section = "模型与预算",
                    status = BudgetMirror.label
                ) { vm.showToolPanel = false; vm.showBudgetDialog = true }
            )
            // 上下文查看器（台账 85）：与形态无关、任何卡都给入口。内存态快照——
            // 本进程还没发过消息时置灰留位置并写明原因（不静默消失，与「拍摄」同一口径）
            add(
                PanelCell(
                    key = "request", label = "上次请求", icon = Icons.Outlined.Description,
                    section = "检查",
                    disabled = vm.lastRequestSnapshot == null,
                    hint = if (vm.lastRequestSnapshot == null) "发一条消息后可查看" else null
                ) {
                    vm.showToolPanel = false; showRequest = true
                }
            )
        }
    } else {
        emptyList()
    }
    // 角色信息面板（顶栏角色名旁「‹」）：本地状态即可，它只影响界面不落盘
    var showCharacterInfo by rememberSaveable { mutableStateOf(false) }
    // 「模型与思考强度」已随第 193 轮收进输入条自己（chip ＋ 输入卡上方的锚定浮层，
    // 状态在 ChatInputBar 内部）——这里不再挂 showModelThinking/弹窗。
    // 背景图铺满整屏（含顶栏/输入栏底下），顶栏改用白色内容 + scrim 保证可读。
    // A 批次分端：取**本平台**那份（会话覆盖优先，回退角色卡），老数据只有一份时两端都用它。
    // 菜单/背景浮层开着时改成**预览菜单里选中的那一端**：用户 2026-09-21 反馈"切换手机端/桌面端背景没有发生变化"
    // ——原来这一行写死取本平台那份，chip 切了聊天页却纹丝不动，看不出那端到底存了哪张图。
    // 关掉菜单就回到本平台那一端（手机上不该一直显示桌面端那张）。
    // 聊天背景弹窗（用户 10-09 口径）：**和「本会话设定」等选项同一个形式**（M3 `AlertDialog`：
    // 标题＋可滚内容＋「关闭」），不再是自绘浮窗外壳、也不是二级整页；开着时聊天区继续预览选中的那一端
    // （列表行与弹窗同一份内容，两端共用）。
    var showBgDialog by remember { mutableStateOf(false) }
    val background = effectiveBackground(
        char,
        vm.conversation,
        if (vm.showSessionPanel || showBgDialog) vm.bgMenuTarget else BgTarget.current
    )
    // 会话级暗化强度：聊天区与顶栏两层 scrim 的透明度系数。
    // 页面里拖滑杆只动草稿，松手才落盘（见 setBgDim 的说明）
    var bgDimDraft by remember { mutableStateOf(1f) }
    LaunchedEffect(vm.conversation?.id, vm.conversation?.bgDim) {
        bgDimDraft = vm.conversation?.bgDimOrDefault() ?: 1f
    }

    // 桌面常驻右栏是否展开（第 194 轮）：只影响界面、不落盘，跨重进保留一次会话内的选择
    var sessionRailOpen by rememberSaveable { mutableStateOf(true) }

    // ══════════ 会话项本体（第 194 轮抽出一份，两处共用） ══════════
    // 手机端＝⋮ 进来的「会话」整页；桌面端＝常驻右栏（见下面的 `DesktopSessionRail`）。
    // **同一份行**：抄两遍等于以后修一处漏一处（见工单）。
    // 行内不再提前关页面；只有会话管理里真正"换了页面语义"的两项（新建会话、切到别的会话）才关。
    // 本端这一份背景字段（会话覆盖优先；`""`＝明确不要）与它的来源一句话：列表行与二级页共用，
    // 拆两处写迟早会不一致（v11 合同 §5：值只写状态、"为什么"只写一行）。
    val bgFieldConv = if (vm.bgMenuTarget == BgTarget.Desktop) vm.conversation?.backgroundUriDesktop
    else vm.conversation?.backgroundUri
    val bgSource = bgSourceLabel(bgFieldConv, char.backgroundFor(vm.bgMenuTarget))
    val bgShortStatus = when (bgSource) {
        "本会话设置的图" -> "本会话"
        "继承自角色卡" -> "继承"
        else -> "未设"
    }
    // ④（用户 10-08）：另一端已经是同一张时，行内置灰并写明原因
    val bgOtherTarget = if (vm.bgMenuTarget == BgTarget.Desktop) BgTarget.Phone else BgTarget.Desktop
    val bgOtherSame = !(bgFieldConv ?: char.backgroundFor(vm.bgMenuTarget)).isNullOrBlank() &&
        effectiveBackground(char, vm.conversation, bgOtherTarget) ==
        effectiveBackground(char, vm.conversation, vm.bgMenuTarget)

    @Composable
    fun SessionRows() {
        // 角色归口区（用户 2026-10-01：桌面端顶栏只留自动朗读，编辑与角色信息都并进这一页）。
        // 手机端顶栏也去掉了铅笔——两端同一份归口，不再"这里一个入口那里一个入口"。
        // ⑦（用户 10-08）：开发者模式下「上下文」置顶——输出预算与上次请求是排错最常看的两行。
        // 非开发者模式整段不出现（同一份行，两端共用）。
        if (DeveloperModeMirror.enabled) {
            ChatPanelSection("上下文")
            ChatPanelRow(
                Icons.Outlined.Tune, "输出预算",
                status = BudgetMirror.label,
                compact = isDesktopLayout
            ) { vm.showBudgetDialog = true }
            ChatPanelRow(
                Icons.Outlined.Description, "上次请求",
                status = if (vm.lastRequestSnapshot == null) "发一条后可看" else "可查看",
                enabled = vm.lastRequestSnapshot != null,
                compact = isDesktopLayout
            ) { showRequest = true }
        }
        ChatPanelSection("场景与氛围")
        // 聊天背景＝弹窗入口：副标题＝来源＋暗化（两端都点行开浮层）
        ChatPanelRow(
            Icons.Outlined.Image, "聊天背景",
            status = if (isDesktopLayout) bgShortStatus else null,
            subtitle = if (isDesktopLayout) null else "$bgSource · 暗化 ${(bgDimDraft * 100).toInt()}%",
            compact = isDesktopLayout
        ) { showBgDialog = true }
        // ⑧（用户 10-08）：背景音乐并进「场景与氛围」（撤掉它前面那条分隔，它们是同一件事的两半）
        if (vm.engine.sessionBgm) {
            ChatPanelRow(
                Icons.Outlined.LibraryMusic, "背景音乐",
                status = if (vm.conversation?.bgm != null) "已设" else "未设",
                compact = isDesktopLayout
            ) { showSessionBgm = true }
        }
        ChatPanelDivider()
        ChatPanelSection("本会话")
        if (vm.engine.relationshipMemory) {
            ChatPanelRow(
                Icons.Outlined.Person, "本会话设定",
                status = if (vm.conversation?.userSetting.isNullOrBlank()) "未填" else "已填"
            ) { vm.showSettingDialog = true }
            ChatPanelRow(
                Icons.Outlined.EditNote, vm.engine.memoryEntryLabel,
                status = if (vm.conversation?.memory.isNullOrBlank()) "未填" else "已填"
            ) { vm.showMemoryDialog = true }
            // 与手机端那一格同源（见 EngineSpec.situation）：只在扮演虚构角色的形态出现
            if (vm.engine.situation) {
                ChatPanelRow(
                    Icons.Outlined.Explore, "当前处境",
                    status = if (vm.conversation?.situation.isNullOrBlank()) "未填" else "已填"
                ) { vm.showSituationDialog = true }
            }
            // 隐藏状态（玩法专属，谜底一类）：行上**只报"空/有"、不显示内容**——
            // 它出现在面板上就已经算剧透，点进去才看得到（对话框里有二次确认的语气）。
            // 214 归口：本会话内容只住这一页，工具面板不再重复。
            if (vm.engine.promptMode == PromptMode.PLAY) {
                ChatPanelRow(
                    Icons.Outlined.VisibilityOff, "隐藏状态",
                    status = if (vm.conversation?.secretState.isNullOrBlank()) "空" else "有"
                ) { vm.showSecretDialog = true }
            }
        }
        // 世界书两行**只在携带世界书时**出现（用户 2026-10-01 反馈 1 明确口径）：
        // 「世界书命中」点进空书是空面板；「关键词覆盖检查」原本按台账 103 挂在形态上（无书也能开），
        // 但用户要的是"这两项只属于有书的卡"——按用户口径收紧，形态判据（Engines.of）仍在里面。
        if (hasWorldBook(char)) {
            ChatPanelRow(Icons.Outlined.MenuBook, "世界书命中") {
                showBookHits = true
            }
            ChatPanelRow(Icons.Outlined.Search, "关键词覆盖检查") {
                showCoverage = true
            }
        }
        // ⑧（用户 10-08）：撤掉「角色信息」行（两端顶栏 ‹ 常驻）。
        // 10-09 再撤手机端的「编辑角色卡」行：手机端点顶栏角色名进去就能查看＋编辑，
        // 面板里再挂一行是同一个入口摆两处；**桌面端右栏那两颗键保留**（顶栏不显示角色名，那是它唯一的路）。
        ChatPanelDivider()
        ChatPanelSection("会话管理")
        ChatPanelRow(
            Icons.Outlined.History, "会话历史",
            status = if (vm.conversations.isEmpty()) "空" else "${vm.conversations.size} 个"
        ) { vm.showHistory = true }
        ChatPanelRow(Icons.Outlined.Add, "新建会话") {
            vm.showSessionPanel = false; vm.startNewConversation()
        }
        ChatPanelRow(Icons.Outlined.Edit, "重命名当前会话") {
            vm.showRenameDialog = true
        }
        // 删除放最后并带二次确认（confirmDelete 弹窗在下方）
        ChatPanelRow(Icons.Outlined.Delete, "删除当前会话", danger = true) {
            vm.conversation?.let { c -> vm.requestDelete(c.id) }
        }
    }

    // Scaffold 之上挂二级整页（会话设置/角色信息）：外层 Box 保证覆盖层真的铺满本页区域
    Box(Modifier.fillMaxSize()) {
    // 桌面第 3.5 栏（第 194 轮）：聊天区（weight 1）＋ 常驻右栏（固定宽）。
    // 手机端 `isDesktopLayout` 为假 ⇒ Row 里只有聊天区那一个孩子，像素与从前逐点一致。
    Row(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f)) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
                ChatTopBar(
                    char = char,
                    title = vm.conversation?.title ?: "新会话",
                    backgroundUri = background,
                    bgDim = bgDimDraft,
                ttsAutoRead = vm.ttsAutoRead,
                // 四种形态都显示：朗读是输出通道，与形态无关（见 `EngineSpec.autoTts` 的注释）
                showTts = vm.engine.autoTts,
                railOpen = sessionRailOpen,
                onToggleTts = { vm.toggleTtsAutoRead() },
                onToggleRail = { sessionRailOpen = !sessionRailOpen },
                onBack = onBack,
                onMore = { vm.showSessionPanel = true },
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
                    ArrowScrollRow(
                        Modifier.weight(1f),
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
                            // accent 作正文小字走派生档（R4 对比度拍板：accent 冻结值留给 CTA/徽标）
                            color = LocalWhalePalette.current.accentText
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
                    // 三块抽屉互斥：开工具/互动时把模型面板的状态也清掉——否则"关掉工具面板后
                    // 模型面板又自己冒出来"（渲染分支本来就互斥，状态也要跟着一致）
                    onToolClick = { showPlusPanel = false; vm.showModelPanel = false; vm.showToolPanel = !vm.showToolPanel },
                    // 输入卡片要真毛玻璃：把当前会话的背景图给它（原语靠重画这张图来模糊）
                    backgroundUri = background,
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
                    // 圆形加号 → 互动面板（项位列表驱动）；工具抽屉与它互斥（同一块位置顶起输入框）
                    plusOpen = showPlusPanel,
                    onPlusToggle = {
                        showPlusPanel = !showPlusPanel
                        if (showPlusPanel) { vm.showToolPanel = false; vm.showModelPanel = false }
                    },
                    plusCells = plusCells,
                    toolOpen = vm.showToolPanel,
                    toolCells = toolCards,
                    onToolClose = { vm.showToolPanel = false },
                    // 桌面宽条右下：灵感回复键／模型 chip（193：chip ＋锚定浮层收进 ChatInputBar 自管）／文字发送键
                    // 发送图片＝直接进相册/文件选择
                    onInspiration = { vm.toggleInspirations() },
                    inspirationOpen = vm.showInspiration,
                    // 形态门：陪伴/多线才有灵感回复，工具/玩法那只键整只不出现（EngineSpec.inspiration）
                    inspirationEnabled = vm.engine.inspiration,
                    onPickImage = { pickChatImage() },
                    // 会话级供应商/模型/思考强度覆盖（第 194 轮；49 轮起含供应商）：chip 显示生效值，浮层可写它
                    sessionBaseUrl = vm.conversation?.chatBaseUrl,
                    sessionModel = vm.conversation?.chatModel,
                    sessionThinking = vm.conversation?.chatThinking,
                    onSessionOverride = { b, m, t -> vm.setSessionOverride(b, m, t) },
                    // 浮层开关在 VM 里：工具列表那一格（toolCards 的 model）也要能打开同一个浮层
                    modelPanelOpen = vm.showModelPanel,
                    onModelPanelOpenChange = { vm.showModelPanel = it }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        ChatMessages(
            modifier = Modifier.fillMaxSize(),
            insets = padding,
            char = char,
            messages = vm.conversation?.messages ?: emptyList(),
            sending = vm.sending,
            draft = vm.draft,
            draftReasoning = vm.draftReasoning,
            // 响应式镜像（见 [DeveloperModeMirror]）：进聊天页刷一次，设置里改完立即生效
            developerMode = DeveloperModeMirror.enabled,
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
                onStopSpeak = { vm.stopSpeaking() },
                onPickOption = { number -> vm.pickOption(number) }
            ),
            ttsSpeaking = vm.ttsSpeaking,
            // 点头像 / 点气泡上那行角色名 = 看角色卡（会话页 v5.1 意见 2）：与顶栏那个入口是同一个页面。
            // 桌面端角色信息住在右栏里 ⇒ 顺手把右栏展开（否则右栏收着时点了像没反应）。
            onOpenCharacter = { showCharacterInfo = true; if (isDesktopLayout) sessionRailOpen = true },
            onInspiration = { vm.toggleInspirations() },
            inspirationLoading = vm.inspirationLoading,
            // 候选内联在消息流里（不再弹窗）：状态从 VM 直给 ChatMessages
            inspirationOpen = vm.showInspiration,
            inspirationItems = vm.inspirations,
            inspirationError = vm.inspirationError,
            onReloadInspirations = { vm.loadInspirations() },
            onPickInspiration = vm::pickInspiration,
            engine = vm.engine,
            // 工具形态的「使用示例」（＝卡里的开场白）点击即当任务填入输入框
            examples = vm.greetingExamples,
            onUseExample = { vm.updateInput(it) }
        )
        // 「模型与思考强度」收起＝点空白处（第 50 轮；用户 2026-10-10「不用右上角的叉号，
        // 统一用点击空白处关闭」——撤掉 210 轮那颗 ✕，与桌面"点浮层外"对齐）。
        // 收口层只盖**聊天区**：随 Scaffold 的 insets 让开顶栏与输入卡，顶栏/输入条/抽屉照常可点；
        // 不画暗幕（与桌面锚定浮层同观感）。点按被它吃掉（第一下只关面板），
        // 拖动仍交给下层消息列表滚动——`clickable` 在拖动时自行取消，不吞 move。
        if (!isDesktopLayout && vm.showModelPanel) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(padding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.showModelPanel = false }
            )
        }
        }
    }

    // 主题贴纸·页面层（交接书 §2.2.1＋用户 2026-10-02 口径）：朱印/邮戳/火漆/胶片条等**页面标记**，
    // 只落聊天区页边、永不压正文；不注册 pointerInput ⇒ 不抢点击。图钉/胶带在**气泡层**（ChatMessages）。
    // 内置简约/潮汐 motif=none ⇒ 整层不渲染。
    WhaleStickerOverlay(spec = ThemeState.skinSpec, modifier = Modifier.matchParentSize())
    }

    // 桌面**常驻右栏**（第 194 轮）：角色卡抬头 ＋ 会话项。
    // 它取代的正是手机端那颗「更多」⋮ 整页——桌面端 ⋮ 因此撤掉（见 ChatTopBar 的 actions）。
    // 收展＝宽/透明度动画（原型 `.dright`），**收起后不留残件**；开关是顶栏右上那颗面板键，
    // 由 `ChatTopBar` 渲染（它不随本栏收展，所以不存在"找不到回来的路"）。
    if (isDesktopLayout) {
        DesktopSessionRail(
            open = sessionRailOpen,
            avatarUri = char.avatarUri,
            name = char.name,
            formLabel = vm.engine.label,
            showCharacterInfo = showCharacterInfo,
            // 桌面这枚键＝在右栏里展开／收起角色信息（不再打开手机端那套整页，见下方 overlay 的门）
            onOpenCharacter = { showCharacterInfo = !showCharacterInfo },
            onEditCharacter = { onEditCharacter() },
            characterInfo = {
                CharacterInfoPanelContent(
                    avatarUri = char.avatarUri,
                    name = char.name,
                    formLabel = vm.engine.label,
                    tagline = char.tagline,
                    persona = char.personaDisplayText(),
                    greeting = char.greeting,
                    hasBook = hasWorldBook(char),
                    // 世界书命中会换成另一页：桌面同样先收起本页（右栏没有"整页"可关）
                    onWorldBookHits = { showCharacterInfo = false; showBookHits = true },
                    onEditCharacter = { onEditCharacter() }
                )
            }
        ) { SessionRows() }
    }
    }

    // 会话设置页：⋮ 进来的二级整页（用户 2026-09-26 拍板：不要拉取窗口，要独立页面）。
    // 背景跟会话页一致（用户 2026-09-26 反馈：独立页不该是一整块突兀的主题底色）——
    // 手机上取的是**正在编辑的那一端**，改背景时本页也跟着变。桌面端同样跟随（用户 2026-10-01 补）：
    // 那一层现在是**轻模糊**的，只剩明暗与色块，旧口径"竖图会被裁成中间一条"不再成立。
    // 页内点开的设置（背景音乐／本会话设定／世界书命中／重命名…）走完**回到本页**（用户反馈 ④），
    // 所以行里不再提前关页面；只有会话管理里真正"换了页面语义"的两项（新建会话、切到别的会话）才关。
    if (vm.showSessionPanel) {
        ChatOverlayPage(
            title = "会话",
            onBack = { vm.showSessionPanel = false },
            background = background,
            bgDim = bgDimDraft
        ) {
            SessionRows()
        }
    }

    // 聊天背景弹窗（用户 10-09 口径「和其他选项形式一样」）：容器＝`ChatBackgroundDialog`（M3 `AlertDialog`，
    // 与「本会话设定」「本会话背景音乐」同族），列表行点进来的还是**同一份内容**。
    // 关闭路径＝点浮层外 / 系统返回 / 「关闭」键（桌面另有 Esc，走共享返回栈）。
    if (showBgDialog) {
        ChatBackgroundDialog(onDismiss = { showBgDialog = false }) {
            ChatBackgroundSection(
                target = vm.bgMenuTarget,
                onPickTarget = { vm.bgMenuTarget = it },
                convField = bgFieldConv,
                cardField = char.backgroundFor(vm.bgMenuTarget),
                otherSame = bgOtherSame,
                dim = bgDimDraft,
                onDimChange = { bgDimDraft = it },
                onDimCommit = { vm.setBgDim(if (bgDimDraft >= 0.999f) 1f else bgDimDraft) },
                onCopyOtherEnd = { vm.copyBackgroundToOtherEnd(vm.bgMenuTarget) },
                onAiBackground = { vm.showBgPrompt = true },
                onLocalBackground = { pickLocalBg() },
                // 反馈 5：裁剪基准＝当前生效的那张图（会话覆盖优先，否则角色卡那份）；
                // 结果写回**本端**会话字段（与"选择本地背景图"同一落点）
                onEditBackground = {
                    if (!(bgFieldConv ?: char.backgroundFor(vm.bgMenuTarget)).isNullOrBlank()) {
                        bgCropTarget = vm.bgMenuTarget
                    }
                },
                onRemoveBackground = { confirmRemoveBg = true },
                // 弹窗的 text 槽自带内边距，内容不再叠一层
                horizontalPadding = 0.dp
            )
        }
    }

    // 角色信息页（顶栏角色名旁的「‹」入口）：卡级设置的归口，首行＝头像 + 名字。
    // 顶栏铅笔保留；旧下拉里的「编辑角色卡」重复项由这里收编。
    // ⚠ 桌面端**不渲染这层**：它顶栏就是「← 角色信息」，那是手机端形态（用户 2026-10-14 反馈"残留 ←"）。
    //   桌面改走右栏内嵌——见上面 `DesktopSessionRail(characterInfo = …)`。
    if (showCharacterInfo && !isDesktopLayout) {
        ChatOverlayPage(
            title = "角色信息",
            onBack = { showCharacterInfo = false },
            background = background,
            bgDim = bgDimDraft
        ) {
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

    // 输出预算（0.2.4 批次 B）：会话页「生成」分组的就近入口；全局设置、拨动即存
    if (vm.showBudgetDialog) {
        BudgetDialog(onDismiss = { vm.showBudgetDialog = false })
    }

    // 模型与思考强度（193 起）：弹窗已退役——入口是输入条上的模型 chip，面板锚定在输入卡上方

    // 会话历史（弹窗）：选另一个会话＝真的换了页面语义，连「会话」页一起关，落回聊天
    if (vm.showHistory) {
        HistoryDialog(
            conversations = vm.conversations,
            currentId = vm.conversation?.id,
            onNew = { vm.showHistory = false; vm.showSessionPanel = false; vm.startNewConversation() },
            onOpen = { id -> vm.showHistory = false; vm.showSessionPanel = false; vm.switchTo(id) },
            onDelete = vm::requestDelete,
            onDismiss = { vm.showHistory = false }
        )
    }

    // 删除确认
    vm.deletingId?.let {
        // 236 弹窗统一：删除类残留收编共享确认壳（手机底部浮层／桌面居中面板，与 241 同族）
        WhaleConfirmSheet(
            title = "删除这个会话？",
            body = "会话记录与其中的图片删除后无法恢复。",
            confirmLabel = "删除",
            onConfirm = vm::confirmDelete,
            onDismiss = vm::cancelDelete
        )
    }

    // 移除背景图确认（分端：只影响菜单里选中的那一端）。
    // ⑤（用户 10-08）：它曾是 R3 第二刀漏掉的一处 M3 AlertDialog —— 动作类确认一律走 WhaleConfirmSheet。
    if (confirmRemoveBg) {
        WhaleConfirmSheet(
            title = "移除${vm.bgMenuTarget.label}背景图？",
            body = "本会话不再使用它；这张图若没有别处引用会被清理掉，无法恢复。",
            confirmLabel = "移除",
            onConfirm = {
                confirmRemoveBg = false
                vm.removeBackground(vm.bgMenuTarget)
            },
            onDismiss = { confirmRemoveBg = false }
        )
    }

    // 背景裁剪（0.3.0 反馈 5）：裁完写回**本端**会话字段——与「选择本地背景图」同一个落点，
    // 旧图照样走 setBackground 里的回收检查
    val cropT = bgCropTarget
    val cropSrc = cropT?.let { t ->
        (if (t == BgTarget.Desktop) vm.conversation?.backgroundUriDesktop else vm.conversation?.backgroundUri)
            ?: char.backgroundFor(t)
    }
    if (cropT != null && !cropSrc.isNullOrBlank()) {
        CropDialog(
            sourceUri = cropSrc,
            aspect = rememberBgCropAspect(cropT),
            onCropped = { newUri ->
                vm.setBackground(newUri, cropT)
                bgCropTarget = null
            },
            onDismiss = { bgCropTarget = null }
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
            // 238 场景级模型：弹窗顶部＝生图模型胶囊（就地可切换、落盘）
            modelScene = ModelScene.IMAGE,
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

    // 覆盖检查的独立入口（台账 103）：无书卡从这里也能看到"它现编了什么"——
    // 上面那个面板要求书非空，最容易现编的形态恰恰打不开它
    if (showCoverage) {
        CoverageGapsDialog(
            card = char,
            messages = vm.conversation?.messages.orEmpty(),
            onDismiss = { showCoverage = false }
        )
    }

    // 上下文查看器（台账 85）：点开时现取最新快照（发过新消息就是新内容，不缓存旧份）
    if (showRequest) {
        vm.lastRequestSnapshot?.let { snap ->
            RequestViewerDialog(
                snap = snap,
                usage = vm.lastRequestUsage,
                onDismiss = { showRequest = false }
            )
        }
    }

    // 工具抽屉的对话框（抽屉本体＝输入栏下方那块，见 ChatInputBar 的 toolOpen；
    // 卡片只报状态、点开改，所以对话生命比抽屉长，放这一层）。
    // 「思考强度」对话框第 194 轮退役：那一格改成「模型与思考强度」＝同一个锚定浮层（`vm.showModelPanel`），
    // 三个入口（输入条 chip / 工具列表那一格）点开的都是它，不再有第二份写全局的口径。
    if (showToolHint) {
        ToolHintDialog(
            current = vm.oneShotHint,
            onApply = { vm.oneShotHint = it; showToolHint = false },
            onClear = { vm.oneShotHint = ""; showToolHint = false },
            onDismiss = { showToolHint = false }
        )
    }

    // 「叙事设置」高级页（P-F1）：状态仍归 ChatViewModel（只回写 Conversation.narrative），
    // 不另存一份，避免两个内存态互相覆盖。
    // 桌面（236 收尾①）：**就地整页**——挂在根 Box 这层与「会话」二级整页同处，占主栏＋常驻右栏，
    // 第一栏导航与②栏会话列表保持可见；出口＝Esc（页内 WhaleBackHandler）。页面自带背景与标题族。
    // 手机：仍是全屏覆盖，用 Dialog 拿窗口层，才能稳稳盖住顶栏、输入栏与底部面板。
    if (vm.showStyleScreen) {
        vm.conversation?.let { c ->
            if (isDesktopLayout) {
                NarrativeStyleScreen(
                    style = c.narrativeStyle(),
                    onChange = vm::applyNarrative,
                    onBack = { vm.showStyleScreen = false }
                )
            } else {
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

    // 【当前处境】（扮演形态专属）：一行短句——此刻在哪、正在做什么、进行到哪一步。
    // 它每轮覆盖卡的 scenario，所以是**剧情会不会被拉回开场**的那个变量；由记忆整理搭车产出，
    // 于是"整理歪了"必须能就地改（与记忆 / 隐藏状态同一条理由）。清空 = 退回卡的 scenario。
    if (vm.showSituationDialog) {
        MemoryEditDialog(
            initial = vm.conversation?.situation ?: "",
            onConfirm = vm::applySituation,
            onDismiss = { vm.showSituationDialog = false },
            title = "当前处境",
            hint = "TA 每轮都会看到的**此刻**：位置 · 时间 · 正在做的事 · 进行到哪一步。" +
                "它由「" + vm.engine.memoryEntryLabel + "」的后台整理顺带写出，写得不对（时间线跳了、" +
                "地点还停在上一次）就会把剧情拉回上一幕，所以这里可以手改。留空则退回角色卡里的开局场景。",
            placeholder = "例：深夜 · 客栈二楼 · 刚交还断剑，正在等对方表态",
            // 默认那句说的是"这段记忆没了"，在这一格上不对：处境空掉不等于什么都没发，
            // 装配会退回角色卡里的开局场景（老会话一直就是这个行为）
            clearConfirmText = "清空后 TA 就按角色卡里的开局场景理解了（不是没有场景，而是回到了开场那一刻）。" +
                "下一次记忆整理会重新写一份。"
        )
    }

    // 玩法的隐藏状态（谜底一类）：**有意进来才看得到的地方**——它不在对话里、也不在上一格里。
    // 给手改入口的理由同台账 97②：这份状态是后台整理出来的，整理歪了（或用户想换个谜底）要能就地改。
    if (vm.showSecretDialog) {
        MemoryEditDialog(
            initial = vm.conversation?.secretState ?: "",
            onConfirm = vm::applySecretState,
            onDismiss = { vm.showSecretDialog = false },
            title = "隐藏状态（只有 AI 会看到）",
            hint = "这一局的内部真相：谜底、待定候选、判定策略。它每轮都会注入给 AI，但**不会出现在对话里**——" +
                "所以看不到它才是正常的，打开这一格就等于剧透自己。留空则 AI 只按「本局进度」判定。",
            placeholder = "例：谜底＝周芷若；已排除：赵敏、小龙女；还剩 3 次提问机会",
            clearConfirmText = "清空后 AI 就不再知道谜底了（下一步它会重新按玩法规则处理，可能自相矛盾）。"
        )
    }

    // 灵感回复（0.1.2）：给几条可以直接发的话，点选填入输入框（不直接发送）
    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    char: CharacterCard,
    title: String,
    /** 聊天背景图（可空）：非空时顶栏自己垫一层它的模糊副本＝毛玻璃 */
    backgroundUri: String?,
    /** 背景暗化强度（会话级）：目前只作用于聊天区 scrim，顶栏保留参数以便统一调档 */
    bgDim: Float = 1f,
    /** 新回复自动朗读（顶栏喇叭：开＝喇叭，关＝带斜杠的喇叭） */
    ttsAutoRead: Boolean,
    /** 这个形态要不要显示自动朗读开关（引擎声明，见 `EngineSpec.autoTts`；四种形态当前都为真） */
    showTts: Boolean = true,
    /** 桌面：右栏是否展开——面板键的选中态（原型 `.btn-ic.on`）；手机端不使用 */
    railOpen: Boolean = true,
    onToggleTts: () -> Unit,
    /** 桌面：右栏收展（右上角面板键）。手机端没有右栏，不传 */
    onToggleRail: (() -> Unit)? = null,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onCharacterInfo: () -> Unit
) {
    // 顶栏＝「透卡 + 柔边」（用户 2026-10-03）：排布保持最新那版（角色名＋会话名居中，
    // 手机端右上只留喇叭与更多），玻璃自己做成半透、下缘带一段渐隐熔接带，边界不再生硬。
    // 玻璃自己有底色，就不再靠"把字刷白"保可读：前景一律走主题色，深浅两套都读得清。
    val fg = MaterialTheme.colorScheme.onSurface
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val type = LocalWhaleType.current
    // 角色名/会话名居中＝手机端（宽度紧、两侧只剩图标，居中最好认）；
    // 桌面端顶栏在第三栏内、左边缘对齐正文更顺
    val centerTitle = !isDesktopLayout
    WhaleBarGlass(
        backgroundUri = backgroundUri,
        modifier = Modifier.fillMaxWidth()
    ) {
        TopAppBar(
            title = {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (centerTitle) Alignment.Center else Alignment.CenterStart
                ) {
                    Column(horizontalAlignment = if (centerTitle) Alignment.CenterHorizontally else Alignment.Start) {
                        // 角色信息入口（手机端）：角色名右侧一枚浅灰「‹」，表示名字能点进去。
                        // 桌面端整行撤掉（v6.7 第 4 轮评审①「抬头去重」）：第二栏那一行已经写着
                        // 「角色名 · N 条」、每条 AI 气泡上方也有角色名，顶栏再复述一遍就是重复。
                        // 会话名因此升格成这一栏唯一的抬头。
                        if (!isDesktopLayout) {
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
                                    Icons.Outlined.ChevronLeft,
                                    contentDescription = "查看角色信息",
                                    tint = sub,
                                    modifier = Modifier
                                        .padding(start = 2.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                        Text(
                            if (title.isBlank()) "新会话" else title,
                            // 桌面：抬头升格成「强调标题」档（会话名是这一栏的标题）
                            // 手机：仍压在角色名下面当副标题（2026-10-01 拍板，顶栏两级是既有形态）
                            // 字号走 L0 字阶：桌面 ×1.1 后≈17px（与旧裸值同量级），但不再绕开整像素吸附
                            style = if (isDesktopLayout) type.strong else type.small,
                            fontWeight = if (isDesktopLayout) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isDesktopLayout) fg else sub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            navigationIcon = {
                // 桌面（三栏）没有"返回"这一说：会话在第三栏里直接换，左栏随时可切。
                // 手机端原样保留返回箭头（返回栈仍然是它的主要导航手段）。
                if (!isDesktopLayout) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = fg)
                    }
                }
            },
            actions = {
                // 自动朗读开关（用户 2026-09-16 要求：放顶栏、关掉就是给喇叭打个斜杠）
                if (showTts) {
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            if (ttsAutoRead) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                            contentDescription = if (ttsAutoRead) "新回复自动朗读：开" else "新回复自动朗读：关",
                            tint = fg
                        )
                    }
                }
                // 右栏收展（用户 2026-10-08「收起展开要和原型对齐」）：原型 `.dbar` 右上角那颗面板键
                // （`data-act="right"`）。**它不随右栏收展、任何时候都在**——右栏收起后不留任何残件，
                // 这颗键就是唯一的路。选中态照原型 `.btn-ic.on`（soft 底 ＋ accent 字色）。
                if (isDesktopLayout && onToggleRail != null) {
                    IconButton(
                        onClick = onToggleRail,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(36.dp)
                            .clip(WhaleShapes.r1)
                            .background(
                                if (railOpen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ViewSidebar,
                            contentDescription = if (railOpen) "收起侧栏" else "展开侧栏",
                            tint = if (railOpen) MaterialTheme.colorScheme.primary else fg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // 铅笔（编辑角色卡）两端都从顶栏撤掉（用户 2026-10-01）：
                // 它和「更多」页里新加的「角色信息 / 编辑角色卡」两行是同一件事，不必占两个位置。
                // 「更多」⋮ 桌面端也撤掉（第 194 轮）：那一页现在**常驻在第三栏右侧**
                // （`DesktopSessionRail`），整页功能一个不少，入口不再需要；
                // 手机端保留——它仍是手机端「会话」页的唯一入口。
                if (!isDesktopLayout) {
                    IconButton(onClick = onMore) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = fg)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }
}

