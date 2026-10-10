package com.mysticat.roleplay.ui.screens

import androidx.compose.material.icons.filled.Archive
import androidx.compose.runtime.rememberCoroutineScope
import com.mysticat.roleplay.ShotOverrides
import com.mysticat.roleplay.data.Backup
import com.mysticat.roleplay.ui.UpdateFlow
import com.mysticat.roleplay.ui.rememberJsonFilePicker
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.isUsableFontFile
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleSkin
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.WhalePageTopBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.AppFont
import com.mysticat.roleplay.data.Profile
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.ThemeMode
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.data.UserSetting
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.CropDialog
import com.mysticat.roleplay.ui.DesktopDragDrop
import com.mysticat.roleplay.ui.DesktopFeatures
import com.mysticat.roleplay.ui.DesktopOnboarding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.unit.sp
import com.mysticat.roleplay.ui.components.WhaleActionSheet
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.components.WhaleFieldGroupLabel
import com.mysticat.roleplay.ui.components.WhaleFieldRow
import com.mysticat.roleplay.ui.components.WhaleMonoLabel
import com.mysticat.roleplay.ui.components.WhalePanelCard
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.DesktopMainHeader
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.DesktopRailRow
import com.mysticat.roleplay.ui.DesktopRailSectionLabel
import com.mysticat.roleplay.ui.ImagePreviewDialog
import com.mysticat.roleplay.ui.rememberFontFilePicker
import com.mysticat.roleplay.ui.rememberGallerySaver
import com.mysticat.roleplay.ui.rememberImageSourcePicker
import com.mysticat.roleplay.ui.SectionCard
import com.mysticat.roleplay.ui.theme.LocalWhalePalette

/**
 * 「用户」页设置栏目的身份。
 *
 * **分类与手机端那几张卡一一对应**（用户口径「设置的字段也得改吧，我感觉目前的分类也不太好，
 * 可以跟安卓一样」）——手机端 `SettingsGroupCard(...)` 的调用处就是分类来源，**改分类先看那里数一遍**。
 * 早先那套八节（含「模型与 API」「语音服务」「背景音乐」「使用手册」各占一节）是过渡形态：
 * 它把"另有整页形态的四个栏目"提拔成了与卡片栏目平级，于是桌面看上去和手机是两套分类。
 *
 * 枚举里后四个（[API] / [VOICE] / [BGM] / [MANUAL]）**不是节**，而是另有整页形态的栏目：
 * 桌面上它们是「AI 与语音」「帮助与关于」两张卡里的入口行（见 [DesktopPageGroups]），
 * 第二栏也能直达；手机端则是直接整页跳转的路由（`onOpenSettings` / `onOpenVoice` / …）。
 */
enum class ProfileGroup(val label: String) {
    // ── 总设置表里的节（与手机端的卡一一对应，顺序也一致）──
    ACCOUNT("账号"),
    PRESETS("我的设定"),
    APPEARANCE("外观"),
    AI_VOICE("AI 与语音"),
    DATA("数据与存储"),
    DESKTOP("桌面"),
    ABOUT("帮助与关于"),

    // ── 另有整页形态的栏目（桌面：各自只是某张卡里的一行入口）──
    API("模型与 API"),
    VOICE("语音服务"),
    BGM("背景音乐"),
    /** 素材库：AI 生成的图／文集中存放（手机端走 `Routes.MATERIALS`，桌面走这一栏内嵌） */
    MATERIALS("素材库"),
    /** 用量统计：请求次数与 token 用量按区间查（手机端走 `Routes.USAGE`，桌面走这一栏内嵌） */
    USAGE("用量统计"),
    MANUAL("使用手册")
}

/**
 * 桌面**页面型**栏目：各自是一整页，第二栏点它们在第三栏开整页，不参与「拉通流」。
 *
 * 它们在总设置表里**不单独成节**，只是父卡里的一行入口（[AI_VOICE] 卡里三行、[ABOUT] 卡里一行），
 * 点入口或由第二栏直达才整页打开——「模型与 API」尤其如此，它的四个子分段在表里从不展开。
 * 判据是"这一栏摊进设置流还读不读得下去"：语音 / 背景音乐 / 使用手册各自有独立分页、试听与
 * 文档目录等交互，手册更是整本文档——摊平会把总设置表拉成"一页设置 + 一本手册"，反而更难找。
 */
internal val DesktopPageGroups =
    setOf(
        ProfileGroup.API, ProfileGroup.VOICE, ProfileGroup.BGM, ProfileGroup.MATERIALS,
        ProfileGroup.USAGE, ProfileGroup.MANUAL, ProfileGroup.ABOUT
    )

/**
 * 页面型栏目 → 它的入口行在总设置表里**所在的那一节**（关页返回时滚回该节）。
 *
 * 用户 2026-10-10：「设置页子页返回要回入口行（现在回开头）」——返回时把第三栏定位回入口行
 * 所在的那一节（外壳据此设 `desktopGroup` 并抬一次定位信号）。入口行的分布见总表各节：
 * 模型与 API／语音服务／背景音乐／素材库四行都住在「AI 与语音」卡里，用量统计在「数据与存储」，
 * 使用手册与关于鲸鱼在「帮助与关于」。节本身原样返回。
 */
internal fun ProfileGroup.entrySectionOf(): ProfileGroup = when (this) {
    ProfileGroup.API, ProfileGroup.VOICE, ProfileGroup.BGM, ProfileGroup.MATERIALS -> ProfileGroup.AI_VOICE
    ProfileGroup.USAGE -> ProfileGroup.DATA
    else -> this
}

/**
 * 拉通流里每个**节**所在的 LazyColumn 下标（已随拉通流一起退役，保留占位说明防误考古）。
 *
 * 原型 v6 · 用户 B（2026-10-02 拍板）落地后，桌面总表从「全部节连续渲染＋滚动定位」改成
 * 「第二栏选组、第三栏就地展开那一节」——不再有滚动跟随与锚点信号，这两个映射随之删除。
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    /** 「背景音乐」设置页（手机端走独立路由；桌面三栏走 [ProfileGroup.BGM] 内嵌，不用这个回调） */
    onOpenBgm: () -> Unit = {},
    /** 「素材库」（手机端走独立路由；桌面三栏走 [ProfileGroup.MATERIALS] 内嵌，不用这个回调） */
    onOpenMaterials: () -> Unit = {},
    /** 「用量统计」（手机端走独立路由；桌面三栏走 [ProfileGroup.USAGE] 内嵌，不用这个回调） */
    onOpenUsage: () -> Unit = {},
    /** 「使用手册」（手机端走独立路由；桌面三栏走 [ProfileGroup.MANUAL] 内嵌，不用这个回调） */
    onOpenManual: () -> Unit = {},
    /**
     * 「关于鲸鱼」（合规与安全 ＋ 版本与更新并成的一整页）：手机端走独立路由；
     * 桌面三栏走 [ProfileGroup.ABOUT] 内嵌（整页形态），不用这个回调。
     */
    onOpenAbout: () -> Unit = {},
    /** 「皮肤中心」（第 17 轮起＝发现页的皮肤轴）：设置里的皮肤行跳过去，SkinDialog 已退役 */
    onOpenSkinCenter: () -> Unit = {},
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
    onBack: (() -> Unit)? = null,
    /**
     * 桌面三栏：**非空＝只渲染这一组**（第三栏内容），分组切换由第二栏
     * [DesktopProfileRail] 承担；`null`＝手机端整页（各分组竖着排一列，与本轮之前完全一致）。
     */
    desktopGroup: ProfileGroup? = null,
    /**
     * 桌面三栏：**外壳持有的**设置 VM。「模型与 API」「语音服务」两组各内嵌一整页，
     * 两页读写的都是同一份 `settings.json` —— 必须共用同一个 VM，各建一个会互相覆盖。
     */
    settingsVm: SettingsViewModel? = null,
    /**
     * 桌面：第三栏是否**整页显示** [desktopGroup] 对应的那一栏。
     * 总设置表里每个栏目都占一节，其中这四个另有整页形态：表里给一行入口，点了整页打开；
     * 第二栏也能直达（模型与 API 的四个子分段就是直达的）。
     */
    desktopPage: Boolean = false,
    /** 桌面：点了总设置表里某一节的入口行（外壳负责把它切进整页） */
    onOpenPage: (ProfileGroup) -> Unit = {},
    /**
     * 桌面：第二栏（或 Ctrl+, / 头像入口）每次点名一个组就 +1。
     * 总表已改「全部节连续渲染」——这个信号＝"滚到那一节"的触发；同一节连点也要能重新定位，
     * 所以不能只看 [desktopGroup] 变没变。
     */
    scrollTick: Int = 0,
    /**
     * 桌面：第三栏滚动时**回头报告**当前顶在视口里的那一节，外壳据此把第二栏的选中态跟过去
     * （用户 2026-10-10：滚动时侧边栏要跟随字段，此前只在点击时同步）。实现方**不得**顺手抬
     * [scrollTick]——那会触发一次"滚到这一节"，变成自己跟自己对滚。
     */
    onVisibleSection: (ProfileGroup) -> Unit = {},
    /**
     * 桌面：整页形态里的"退回上一层"——页内返回键与 Esc 都走它，由外壳关掉整页。
     * 此前恒传 `onBack = {}`，于是这四页在桌面上**没有任何返回出口**（连顶栏返回键都是死的）。
     */
    onClosePage: () -> Unit = {}
) {
    // 桌面「整页形态」的四栏：提前返回，后面那一整套设置页状态（缓存扫描、账号弹窗、备份…）就不必白跑。
    if (isDesktopLayout && desktopPage && settingsVm != null &&
        desktopGroup != null && desktopGroup in DesktopPageGroups
    ) {
        when (desktopGroup) {
            ProfileGroup.API -> SettingsScreen(onBack = onClosePage, embedded = true, vm = settingsVm)
            ProfileGroup.VOICE -> VoiceScreen(onBack = onClosePage, embedded = true, vm = settingsVm)
            ProfileGroup.BGM -> BgmScreen(onBack = onClosePage, embedded = true)
            ProfileGroup.MATERIALS -> MaterialsScreen(onBack = onClosePage, embedded = true)
            ProfileGroup.USAGE -> UsageQueryScreen(onBack = onClosePage, embedded = true)
            ProfileGroup.MANUAL -> ManualScreen(onBack = onClosePage, embedded = true)
            // 「关于鲸鱼」＝合规与安全＋版本与更新并成的一整页（手机端走 Routes.ABOUT 同一份内容）
            ProfileGroup.ABOUT -> AboutWhaleScreen(onBack = onClosePage, embedded = true)
            else -> Unit
        }
        return
    }
    val versionLabel = remember { Platform.ui.appVersionName() }
    // 自动检查查到的新版本（与桌面第一栏的更新图标、自动提醒弹窗**同一份**状态）
    val autoUpdateInfo = UpdateFlow.info
    // ── 桌面「拉通流」（用户反馈 ③）──────────────────────────────────────
    // 用户原话：「设置页把所有栏目拉通，侧边栏起到快速定位的作用（除模型与 API 那里的子栏目
    // 是直接跳转页面）」。⇒ 卡片型栏目（账号 / 我的设定 / 外观与设置 / 版本与安全）不再
    // "选一组看一组"，第三栏恒为**全部节连续渲染**，第二栏退化成**目录**、点击只是滚动定位。
    // [DesktopPageGroups] 里的栏目是**页面型**（各自是一整页，含内部小目录），仍整页内嵌。
    // 桌面走到这里就是「总设置表」形态（整页形态已在函数开头 return 掉了）；手机端 desktopGroup
    // 恒为 null。两者共用的分派语义（整页 or 路由）仍靠它区分，骨架差异见 body。
    val desktopStream = isDesktopLayout
    // 手机手风琴（原型 v6 · 用户 B）：展开的那一组；同一行点第二次收起，默认展开 AI 节。
    // 桌面不用它——桌面是 col2 选组、col3 就地展开，选中组由外壳经 [desktopGroup] 传进来。
    var openGroup by remember { mutableStateOf<ProfileGroup?>(ProfileGroup.AI_VOICE) }
    // 状态带（band）读数：对话模型 / 语音朗读 / 已存 Key 家数。进页读一次——
    // 改这些值都发生在设置整页或弹窗里，返回时外壳切 tab 会重组本页（与 defaultSettingId 同口径）。
    // （238 曾在语音那格过一遍 VOICE 场景覆盖；49 轮撤销——语音服务不再有场景覆盖，见 Models.kt 字段注释）
    val bandSettings = remember { Repository.loadSettings() }
    val keyCount = remember { bandSettings.providerKeys.count { it.value.isNotBlank() } }
    val ttsLabel = when {
        bandSettings.ttsProvider == "system" -> "系统语音"
        bandSettings.ttsModel.isNotBlank() -> bandSettings.ttsModel
        else -> "自定义服务"
    }
    var profile by remember { mutableStateOf(Repository.currentProfile()) }
    var showAccounts by remember { mutableStateOf(false) }
    var cacheMsg by memoryState<String?>(null)
    // 问题 #21：先扫描并显示缓存大小，清完再报释放量，别让用户怀疑「这按钮到底有没有用」
    var cacheBytes by memoryState<Long>(0L)
    // 2026-09-15：清之前先列明细（孤立生图的删除不可逆，用户该知道要删什么）
    var showCacheDialog by memoryState(false)
    var cacheDetail by memoryState<Repository.CacheBreakdown?>(null)
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { Repository.cacheBytes() }
    }
    var settings by remember { mutableStateOf(Repository.listUserSettings()) }
    var defaultSettingId by remember { mutableStateOf(Repository.loadSettings().defaultUserSettingId) }
    var showSettingEditor by remember { mutableStateOf(false) }
    var editingSetting by remember { mutableStateOf<UserSetting?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    // #6：头像预览 / 裁剪
    var showAvatarPreview by remember { mutableStateOf(false) }
    var croppingAvatar by remember { mutableStateOf(false) }
    // 问题 #13：反复进「编辑」始终基于最初原图重裁；换新头像时重置
    var avatarCropSource by remember { mutableStateOf<String?>(null) }

    val pickAvatar = rememberImageSourcePicker { uri ->
        uri?.let {
            val cur = Repository.currentProfile() ?: return@let
            Repository.updateProfile(cur.copy(avatarUri = it))
            profile = Repository.currentProfile()
            avatarCropSource = null
        }
    }

    // 拖图到窗口 / Ctrl+V 贴截图 → 换用户头像（与「编辑头像」同一落点，桌面专属入口）
    val profileImageSink: (String) -> Boolean = { uri ->
        val cur = Repository.currentProfile()
        if (cur != null) {
            Repository.updateProfile(cur.copy(avatarUri = uri))
            profile = Repository.currentProfile()
            avatarCropSource = null
        }
        cur != null
    }
    SideEffect { DesktopDragDrop.imageSink = profileImageSink }
    DisposableEffect(Unit) {
        onDispose { if (DesktopDragDrop.imageSink === profileImageSink) DesktopDragDrop.imageSink = null }
    }

    // 正文一份、两种外壳：手机＝Scaffold（顶栏「用户」+ 底部无导航），桌面＝第三栏内嵌（无顶栏）。
    // 「帮助与关于」卡里的两行：使用手册 ＋「关于鲸鱼」（后者原先是「版本与安全」弹窗与
    // 「合规与安全」两行——用户要求并成一个入口、并且点开是整页，不做弹窗）。
    // 桌面拉通流把它单列成一节；手机端两行都是整页路由。
    // 「使用手册」手机端走整页路由，桌面端是 [DesktopPageGroups] 里的整页栏目 ⇒ 同一行、两种落点。
    val helpRows = buildList {
        add(
            AppearanceRowItem(
                // ⚠ 用 `Filled.AutoStories` 而不是 `AutoMirrored.Filled.MenuBook`：桌面端
                // material-icons-extended（CMP 1.7.3）**整支 automirrored 都不存在**，
                // 而打包前的图标裁剪只保留"扫得到引用"的类 ⇒ MenuBook 被删、点「用户」页
                // NoClassDefFoundError（崩过一回，见 desktopApp/build.gradle.kts 的 iconRef）
                icon = Icons.Filled.AutoStories,
                title = "使用手册",
                subtitle = "怎么填 Key、怎么用角色卡与语音 · 离线可看",
                onClick = {
                    if (desktopStream) onOpenPage(ProfileGroup.MANUAL) else onOpenManual()
                }
            )
        )
        add(
            AppearanceRowItem(
                icon = Icons.Filled.Info,
                title = "关于鲸鱼",
                subtitle = "检查更新 / 更新日志 / 异常记录 / 免责与安全协议",
                // 右侧常驻一枚小字：**平常就是版本号**，自动查到新版本时换成「发现新版本 x.y.z」。
                // 徽标与桌面第一栏的更新图标读的是**同一份**状态（都走 UpdateFlow），
                // 所以不会出现"手机说有新版、桌面说没有"。
                trailing = {
                    val found = autoUpdateInfo
                    Text(
                        if (found == null) versionLabel else "发现新版本 ${found.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (found == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else LocalWhalePalette.current.accentText
                    )
                },
                onClick = {
                    if (desktopStream) onOpenPage(ProfileGroup.ABOUT) else onOpenAbout()
                }
            )
        )
    }

    // ── 卡片型栏目的"行"（从下面那个 `item {}` 里提出来）─────────────────────
    // 理由：分类对齐手机端之后，这几组行要**分落到四个不同的节**（外观 / AI 与语音 /
    // 数据与存储 / 桌面），而 LazyColumn 的每个 `item {}` 各有各的作用域 ⇒ 行定义必须挂在
    // 函数体上，四个 item 才都引用得到。手机端仍把其中三组挤在一个 item 里（间距与手机端一致）。
    val isDesktopPlatform = Platform.ui.platformId() == "desktop"
    // 开发者模式（思考折叠行的唯一开关，0.2.4）：从「模型与 API → 对话模型」挪到设置页
    // 单独一行（用户反馈：原来埋在模型参数里）。拨动即存档并刷新聊天页镜像，没有"保存"按钮可忘。
    var developerMode by remember { mutableStateOf(Repository.loadSettings().developerMode) }
    val toggleDeveloperMode: (Boolean) -> Unit = { on ->
        Repository.saveSettings(Repository.loadSettings().copy(developerMode = on))
        developerMode = on
        DeveloperModeMirror.refresh()
    }
    val developerModeRow = AppearanceRowItem(
        icon = Icons.Filled.Tune,
        title = "开发者模式",
        subtitle = if (developerMode)
            "已开启：AI 气泡上方显示「思考过程」折叠条（对开启后的新回复生效，更早的消息没存思考；模型返回了思考才显示）"
        else
            "在聊天气泡里显示模型的思考过程 · 思考只存本机，从不发回模型",
        arrow = false,
        trailing = {
            Switch(checked = developerMode, onCheckedChange = toggleDeveloperMode)
        },
        onClick = { toggleDeveloperMode(!developerMode) }
    )
    var loginEnabled by remember { mutableStateOf(false) }
    var loginBusy by remember { mutableStateOf(false) }
    LaunchedEffect(isDesktopPlatform) {
        if (isDesktopPlatform) {
            loginEnabled = withContext(Dispatchers.IO) { DesktopFeatures.queryLaunchAtLogin() }
        }
    }
    val toggleLogin: (Boolean) -> Unit = { on ->
        if (DesktopFeatures.launchAtLoginSupported && !loginBusy) {
            loginBusy = true
            scope.launch {
                val ok = withContext(Dispatchers.IO) { DesktopFeatures.setLaunchAtLogin(on) }
                loginEnabled = on
                loginBusy = false
                if (!ok) showToast("开机自启设置失败，请重试或检查权限", long = true)
            }
        }
    }
    // 分区（用户口径"选项分布有点乱"）：按**用途**分卡，一行只管一件事。
    // 之前 8 行挤在一张「外观与设置」里：使用手册夹在语音服务与数据备份中间、
    // 主题字体（纯外观）与 Key 和备份（配置与数据）同列——顺序不是按用途排的。
    val appearanceRows = listOf(
        AppearanceRowItem(
            icon = Icons.Filled.Palette,
            title = "主题设置",
            subtitle = "当前：${ThemeState.mode.label}",
            onClick = { showThemeDialog = true }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.Style,
            title = "皮肤",
            subtitle = "当前：${ThemeState.skinSpec.name}（到发现页换）",
            onClick = onOpenSkinCenter
        ),
        AppearanceRowItem(
            icon = Icons.Filled.FormatSize,
            title = "字体设置",
            // 用自定义字体时显示它的名字（用户 2026-09-24："换了仿宋但仍显示'衬线（宋体风格）'"——
            // 旧口径只读内置枚举 label，customFontPath 生效时 label 仍是上一次选的内置族）
            subtitle = "字体：${
                if (ThemeState.customFontPath != null) ThemeState.customFontName.ifBlank { "自定义字体" }
                else ThemeState.font.label
            } · 字号 ${(ThemeState.fontScale * 100).roundToInt()}%",
            onClick = { showFontDialog = true }
        )
    )
    // 手机端：「AI 与语音」卡里三行入口，点了整页跳走（`onOpenSettings` / `onOpenVoice` / `onOpenBgm`）
    val serviceRows = if (desktopGroup == null) listOf(
        AppearanceRowItem(
            icon = Icons.Filled.Settings,
            title = "模型与 API",
            subtitle = "供应商 Key 与 对话 / 创作 / 生图 模型参数",
            onClick = { onOpenSettings() }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.VolumeUp,
            title = "语音服务",
            subtitle = "语音朗读（音色 / 语速 / 混合音色）· 语音识别",
            onClick = { onOpenVoice() }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.MusicNote,
            title = "背景音乐",
            subtitle = "音乐库与全局默认 · 每个会话可单独选曲",
            onClick = { onOpenBgm() }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.PhotoLibrary,
            title = "素材库",
            subtitle = "AI 生成的图片与文字集中存放 · 可打标签筛选",
            onClick = { onOpenMaterials() }
        ),
        developerModeRow
    ) else emptyList()
    val dataRows = listOf(
        AppearanceRowItem(
            icon = Icons.Filled.QueryStats,
            title = "用量统计",
            subtitle = "请求次数与 token 用量 · 按今天 / 近七天查",
            // dataRows 手机端与桌面拉通流共用（桌面见 [desktopStream] 那条分支），落点得分派
            onClick = { if (desktopStream) onOpenPage(ProfileGroup.USAGE) else onOpenUsage() }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.Archive,
            title = "数据备份",
            subtitle = "导出 / 恢复全部角色与会话记录",
            onClick = { showBackup = true }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.Delete,
            title = "清除缓存",
            subtitle = cacheMsg
                ?: "可清理 ${formatBytes(cacheBytes)} · 不影响角色、会话与聊天记录里的图片",
            arrow = false,
            onClick = {
                scope.launch {
                    // 先扫描明细再弹确认框：告诉用户具体要删什么，而不是直接动手
                    cacheDetail = withContext(Dispatchers.IO) { Repository.cacheBreakdown() }
                    showCacheDialog = true
                }
            }
        )
    )
    // 桌面专属：窗口行为两开关。手机端没有托盘/自启概念，不画。
    val desktopRows = if (isDesktopPlatform) buildList {
        add(
            AppearanceRowItem(
                icon = Icons.Filled.PictureInPictureAlt,
                title = "关闭时缩小到托盘",
                subtitle = when {
                    !DesktopFeatures.traySupported -> "本机不支持系统托盘"
                    DesktopFeatures.hideToTrayOnClose ->
                        "已开启：关闭窗口后从托盘图标恢复或退出"
                    else -> "关闭窗口后保留在系统托盘，可随时恢复"
                },
                arrow = false,
                trailing = {
                    Switch(
                        checked = DesktopFeatures.hideToTrayOnClose,
                        enabled = DesktopFeatures.traySupported,
                        onCheckedChange = { DesktopFeatures.setHideToTray(it) }
                    )
                },
                onClick = {
                    if (DesktopFeatures.traySupported) {
                        DesktopFeatures.setHideToTray(!DesktopFeatures.hideToTrayOnClose)
                    }
                }
            )
        )
        add(
            AppearanceRowItem(
                icon = Icons.Filled.PowerSettingsNew,
                title = "开机自动启动",
                subtitle = when {
                    !DesktopFeatures.launchAtLoginSupported ->
                        "仅安装版（通过启动器运行）可配置"
                    loginEnabled -> "已开启：登录 Windows 后自动启动鲸鱼"
                    else -> "登录 Windows 后自动启动鲸鱼"
                },
                arrow = false,
                trailing = {
                    Switch(
                        checked = loginEnabled,
                        enabled = DesktopFeatures.launchAtLoginSupported && !loginBusy,
                        onCheckedChange = { toggleLogin(it) }
                    )
                },
                onClick = { toggleLogin(!loginEnabled) }
            )
        )
        // 首启引导页之后再进数据/缓存目录的入口
        // （桥只在 desktopApp 正常窗口/--shot 路径安装；Android 与裸 --smoke 不出现）
        if (DesktopOnboarding.installed) {
            add(
                AppearanceRowItem(
                    icon = Icons.Filled.Folder,
                    title = "数据与缓存目录",
                    subtitle = "查看或更改数据、缓存的保存位置（更改下次启动生效）",
                    onClick = { DesktopOnboarding.reopen() }
                )
            )
        }
    } else emptyList()
    // 桌面端同样是那三栏，只是落点改成"三栏内整页"（[onOpenPage]）——用户 ③ 的口径：
    // 每个栏目都在总设置表里有一行，「模型与 API」的四个子分段在表里不展开（第二栏直达）。
    val aiPageRows = if (desktopStream) listOf(
        AppearanceRowItem(
            icon = Icons.Filled.Settings,
            title = "模型与 API",
            subtitle = "供应商 Key 与 对话 / 创作 / 生图 模型参数",
            onClick = { onOpenPage(ProfileGroup.API) }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.VolumeUp,
            title = "语音服务",
            subtitle = "语音朗读（音色 / 语速 / 混合音色）· 语音识别",
            onClick = { onOpenPage(ProfileGroup.VOICE) }
        ),
        AppearanceRowItem(
            icon = Icons.Filled.MusicNote,
            title = "背景音乐",
            subtitle = "音乐库与全局默认 · 每个会话可单独选曲",
            onClick = { onOpenPage(ProfileGroup.BGM) }
        ),
        // 素材库：原先只有第二栏有入口、总设置表里没有（用户 2026-09-27 反馈"素材库没有进设置页"）
        // ——总设置表是"扫一眼就知道这个 App 有哪些设置"的地方，漏一行就等于那一栏不存在
        AppearanceRowItem(
            icon = Icons.Filled.PhotoLibrary,
            title = "素材库",
            subtitle = "AI 生成的图片与文字集中存放 · 按形象 / 背景 / 角色卡 / 世界书 / 故事分类",
            onClick = { onOpenPage(ProfileGroup.MATERIALS) }
        ),
        developerModeRow
    ) else emptyList()

    // 手风琴 / 面板共用的组清单（原型 v6 · 用户 B 的 PG，顺序照抄）：桌面专属组在
    // 非桌面平台没有行，不进清单（桌面窄窗仍进——那些开关在窄窗下照样成立）。
    val consoleGroups = buildList {
        add(ProfileGroup.ACCOUNT)
        add(ProfileGroup.PRESETS)
        add(ProfileGroup.APPEARANCE)
        add(ProfileGroup.AI_VOICE)
        add(ProfileGroup.DATA)
        if (desktopRows.isNotEmpty()) add(ProfileGroup.DESKTOP)
        add(ProfileGroup.ABOUT)
    }

    /** 组摘要（原型 B 的核心特征：行右侧挂"当前值"）——手风琴行尾与桌面清单行共用 */
    fun groupSummary(g: ProfileGroup): String = when (g) {
        ProfileGroup.ACCOUNT -> profile?.nickname ?: "未登录"
        ProfileGroup.PRESETS ->
            settings.firstOrNull { it.id == defaultSettingId }?.name ?: "共 ${settings.size} 份"
        ProfileGroup.APPEARANCE ->
            "${ThemeState.mode.label} · ${(ThemeState.fontScale * 100).roundToInt()}%"
        ProfileGroup.AI_VOICE -> bandSettings.chatModel.ifBlank { "未配置" }
        ProfileGroup.DATA -> "${formatBytes(cacheBytes)} 可清理"
        ProfileGroup.DESKTOP -> "${desktopRows.size} 项"
        else -> versionLabel
    }

    fun bandCells() = listOf(
        ProfileBandCell(
            if (keyCount > 0) "$keyCount 家已存" else "未配置",
            "KEY 就绪", ok = keyCount > 0
        ),
        ProfileBandCell(bandSettings.chatModel.ifBlank { "未配置" }, "对话模型"),
        ProfileBandCell(ttsLabel, "语音朗读"),
        ProfileBandCell(formatBytes(cacheBytes), "缓存可清理")
    )

    /** 一组的面板内容（选中组就地展开的那一块）。ACCOUNT / PRESETS 是自带外壳的既有组件。 */
    @Composable
    fun GroupPanelContent(group: ProfileGroup) {
        when (group) {
            ProfileGroup.ACCOUNT -> AccountCard(
                profile,
                onPickAvatar = pickAvatar,
                onPreviewAvatar = { showAvatarPreview = true },
                onManage = { showAccounts = true }
            )
            ProfileGroup.PRESETS -> SettingSection(
                settings = settings,
                defaultId = defaultSettingId,
                onNew = { editingSetting = null; showSettingEditor = true },
                onEdit = { s -> editingSetting = s; showSettingEditor = true },
                onDelete = { s ->
                    Repository.deleteUserSetting(s.id)
                    settings = Repository.listUserSettings()
                    defaultSettingId = Repository.loadSettings().defaultUserSettingId
                },
                onSetDefault = { id ->
                    Repository.setDefaultUserSetting(id)
                    defaultSettingId = id
                    settings = Repository.listUserSettings()
                }
            )
            ProfileGroup.APPEARANCE -> PanelRows(appearanceRows)
            // AI 与语音的落点随形态分派：三栏桌面＝整页入口行（onOpenPage）；手机与窄窗＝整页路由
            ProfileGroup.AI_VOICE ->
                if (desktopStream) PanelRows(aiPageRows) else PanelRows(serviceRows)
            ProfileGroup.DATA -> PanelRows(dataRows)
            ProfileGroup.DESKTOP -> PanelRows(desktopRows)
            else -> PanelRows(helpRows)
        }
    }

    /**
     * 桌面总表的一节：卡外标题（**带该组图标**——用户 10-10「要保留对应图标」，
     * 图标从旧摘要行/面板头上承接）＋ 内容。
     * ACCOUNT／PRESETS 的内容自带外壳（AccountCard／SettingSection 的卡片），
     * 不套行表卡（卡中卡＝v10 DESIGN §6 禁用 1）；其余组＝`WhaleFieldGroup` 行表卡。
     */
    @Composable
    fun GroupSection(g: ProfileGroup) {
        val leading: @Composable () -> Unit = {
            Icon(
                ProfileGroupIcon[g] ?: Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (g == ProfileGroup.ACCOUNT || g == ProfileGroup.PRESETS) {
            WhaleFieldGroupLabel(g.label, leading = leading)
            GroupPanelContent(g)
        } else {
            WhaleFieldGroup(label = g.label, leading = leading) { GroupPanelContent(g) }
        }
    }

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (desktopStream) {
                // ── 桌面总表（用户 10-10 口径：**去手风琴**）＝全部节连续渲染的行表式一页到底。
                // 每节＝卡外标题（带该组图标）＋ 内容；第二栏点组＝**直接滚动定位**到那一节
                // （LazyColumn 原生 scrollToItem，与编辑页两段式的锚点口径一致）。
                // 旧形态（选中组就地展开＋其余组摘要清单）退役——值不再另列摘要行，各节的行自己带着。
                val listState = rememberLazyListState()
                val sections = consoleGroups
                // 列表前两项＝抬头与状态带，节从第 3 项（下标 2）起
                val sectionOffset = 2
                // 程序化定位期间暂停"跟随"（与编辑器两段式同一套：先选中再滚、动画期间不回头改选中）
                var jumping by remember { mutableStateOf(false) }
                // ⚠ 键只有 [scrollTick]，**不能带 [desktopGroup]**（2026-10-10 加跟随时的坑）：
                // 跟随会把"当前可见的节"写回 desktopGroup，若它也是键，回写当即触发一次定位动画，
                // 滚动就跟用户较劲了。所有点名定位的入口（第二栏/快捷键/关整页返回）本来就都抬 tick。
                LaunchedEffect(scrollTick) {
                    val g = desktopGroup ?: return@LaunchedEffect
                    val idx = sections.indexOf(g)
                    if (idx < 0) return@LaunchedEffect
                    // 首节（账号）＝回页顶（抬头与状态带一起进视野，别把它们顶出去）；
                    // 其余节＝对齐到该节标题（标题自带 20dp 上留白，不会贴边）。
                    val target = if (idx == 0) 0 else idx + sectionOffset
                    // 首次组合那一帧列表还没量过，scrollToItem 会空转——先等布局跑过一遍
                    var frames = 0
                    while (listState.layoutInfo.totalItemsCount == 0 && frames < 10) {
                        withFrameNanos { }
                        frames++
                    }
                    jumping = true
                    // try/finally：连点两行时本 effect 会被重启（协程取消），不 finally 复位的话
                    // `jumping` 会永久卡在 true，跟随就再也不亮了
                    try {
                        listState.animateScrollToItem(target)
                    } finally {
                        jumping = false
                    }
                }
                // 反向（用户 2026-10-10「滚动时侧边栏跟随字段」）：第三栏滚到哪一节，
                // 第二栏就高亮哪一节。每节一整格 item，所以跟 `firstVisibleItemIndex` 走即可
                // （同编辑器两段式的口径）；抬头/状态带那两格归首节。
                val reportVisible by rememberUpdatedState(onVisibleSection)
                LaunchedEffect(listState, sections) {
                    snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
                        if (!jumping && sections.isNotEmpty()) {
                            reportVisible(sections[(idx - sectionOffset).coerceIn(0, sections.lastIndex)])
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // 顶部不留 16dp：主栏标题的纵向位置全族统一（用户 10-10：各页标题高度不一致）。
                    // 标题自己那条 48dp 带就是页顶留白，与会话记录／全部角色同一条。
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    item(key = "head") {
                        // 主栏标题（236 形式统一 · 用户 10-10 收编）：此前这里自写一行 titleMedium，
                        // 字号比其余页小一档、位置也偏 7px——收进同一份 `DesktopMainHeader`。
                        DesktopMainHeader("用户") {
                            Text(
                                "Ctrl+, 直达外观节",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item(key = "band") {
                        ProfileBand(bandCells(), wrap = false)
                    }
                    items(sections, key = { it.name }) { g ->
                        GroupSection(g)
                    }
                    item(key = "tail") { Spacer(Modifier.height(20.dp)) }
                }
            } else {
                // ── 手机（原型 v6 · 用户 B 手风琴）：状态带折行两列 ＋ 组行（右侧当前值），
                // 点组行就地展开、同一行再点一次收起；默认展开 AI 节。各节不再同时全摊开。
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "band") {
                        ProfileBand(bandCells(), wrap = true)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(consoleGroups, key = { it }) { g ->
                        val open = openGroup == g
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { openGroup = if (open) null else g }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    ProfileGroupIcon[g] ?: Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    g.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // 同 GroupDigestRow：值贴右缘，别被"半路平分"的余白带走
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                SummaryValue(groupSummary(g))
                                Spacer(Modifier.width(6.dp))
                                val rotation by animateFloatAsState(
                                    if (open) 90f else 0f, label = "chev"
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .graphicsLayer { rotationZ = rotation },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (open) {
                                Spacer(Modifier.height(6.dp))
                                if (g == ProfileGroup.ACCOUNT || g == ProfileGroup.PRESETS) {
                                    GroupPanelContent(g)
                                } else {
                                    WhalePanelCard { GroupPanelContent(g) }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (desktopGroup != null) {
        // 第三栏内嵌：不套 Scaffold（外壳已提供整窗背景与三栏骨架）
        body(PaddingValues(0.dp))
    } else {
    Scaffold(
        // 手机导航页顶部去填充（236 形式统一 · 照 v5 原型）：纸底 ＋ 底发丝线。
        // ⚠ 10-08 那轮曾统一成主色块，10-10 口径 236 推翻：顶部不铺色（见 `WhalePageTopBar`）。
        topBar = {
            WhalePageTopBar(
                title = "用户",
                navigationIcon = {
                    if (onBack != null) {
                        BackArrowButton(onBack = { onBack() })
                    }
                }
            )
        }
    ) { padding -> body(padding) }
    }

    if (showAccounts) {
        AccountsDialog(
            currentId = profile?.id,
            onDismiss = { showAccounts = false },
            onSwitched = { profile = Repository.currentProfile() },
            onRegister = { showAccounts = false; onLogout() },
            onSwitchAccount = { showAccounts = false; onSwitchAccount() },
            onLogout = { showAccounts = false; onLogout() }
        )
    }

    if (showSettingEditor) {
        SettingEditorDialog(
            setting = editingSetting,
            onDismiss = { showSettingEditor = false; editingSetting = null },
            onSave = { name, content ->
                val existing = editingSetting
                if (existing != null) Repository.updateUserSetting(existing.copy(name = name, content = content))
                else Repository.addUserSetting(name, content)
                settings = Repository.listUserSettings()
                showSettingEditor = false
                editingSetting = null
            }
        )
    }

    if (showBackup) {
        BackupDialog(onDismiss = { showBackup = false })
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            current = ThemeState.mode,
            onPick = { ThemeState.setMode(it, persist = true); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showFontDialog) {
        FontDialog(onDismiss = { showFontDialog = false })
    }

    // 清除缓存：先列明细再确认（2026-09-15）
    if (showCacheDialog) {
        CacheCleanupDialog(
            breakdown = cacheDetail,
            onDismiss = { showCacheDialog = false },
            onConfirm = {
                showCacheDialog = false
                scope.launch {
                    val freed = withContext(Dispatchers.IO) { Repository.clearCaches() }
                    cacheBytes = withContext(Dispatchers.IO) { Repository.cacheBytes() }
                    cacheDetail = null
                    cacheMsg = if (freed > 0) "已释放 ${formatBytes(freed)}" else "没有可清理的内容"
                }
            }
        )
    }

    // #6：点头像先看原图，可「更换」或「编辑（裁剪）」
    val avatarUri = profile?.avatarUri
    val saveGallery = rememberGallerySaver()
    // 首次进入裁剪时把当前头像记为"裁剪源图"（原来在组合期写状态，改为一次性副作用）
    LaunchedEffect(avatarCropSource, avatarUri) {
        if (avatarCropSource == null && !avatarUri.isNullOrBlank()) avatarCropSource = avatarUri
    }
    if (showAvatarPreview && !avatarUri.isNullOrBlank()) {
        if (croppingAvatar) {
            CropDialog(
                // 原来在组合期用 `.also { avatarCropSource = it }` 写状态（Compose 不推荐的副作用写，
                // 会让同一帧内后续读取到"刚写进去的值"，也可能引发额外重组）。改写进 LaunchedEffect。
                sourceUri = avatarCropSource ?: avatarUri,
                aspect = 1f,
                onCropped = { newUri ->
                    Repository.currentProfile()?.let { p ->
                        Repository.updateProfile(p.copy(avatarUri = newUri))
                    }
                    profile = Repository.currentProfile()
                    croppingAvatar = false
                },
                onDismiss = { croppingAvatar = false }
            )
        } else {
            ImagePreviewDialog(
                title = "头像",
                uri = avatarUri,
                onReplace = {
                    showAvatarPreview = false
                    pickAvatar()
                },
                onEdit = { croppingAvatar = true },
                onSave = { saveGallery(avatarUri) },
                onDismiss = { showAvatarPreview = false }
            )
        }
    }

}

/** 第二栏里每个栏目的图标（节与整页栏目都在这里；顺序与含义见 [ProfileGroup]） */
private val ProfileGroupIcon = mapOf(
    ProfileGroup.ACCOUNT to Icons.Filled.AccountCircle,
    ProfileGroup.PRESETS to Icons.Filled.Tune,
    ProfileGroup.APPEARANCE to Icons.Filled.Palette,
    ProfileGroup.AI_VOICE to Icons.Filled.AutoAwesome,
    ProfileGroup.DATA to Icons.Filled.Archive,
    // ⚠ `DesktopWindows` 是**桌面版专有**的槽位：手机端画不到这一节（`desktopRows` 为空），
    // 但图标引用在共享层，Android 侧打包同样要带上它——别当"用不到"删掉。
    ProfileGroup.DESKTOP to Icons.Filled.DesktopWindows,
    ProfileGroup.ABOUT to Icons.Filled.Security,
    // 整页栏目：第二栏里是缩进子项，图标只在"一级行"用得上，这里给全是为了不怕将来漏
    ProfileGroup.API to Icons.Filled.Settings,
    ProfileGroup.VOICE to Icons.Filled.VolumeUp,
    ProfileGroup.BGM to Icons.Filled.MusicNote,
    ProfileGroup.MATERIALS to Icons.Filled.PhotoLibrary,
    ProfileGroup.USAGE to Icons.Filled.QueryStats,
    ProfileGroup.MANUAL to Icons.Filled.AutoStories
)

/**
 * 桌面第二栏（「用户」页）：**分组索引**（原型 v6 · 用户 B 的 col2，两段）。
 *
 * 上段「用户」＝七个组行，点了第三栏就地展开那一组（永不跳页）；下段「整页栏目」＝
 * 各自是一整页的六个栏目（模型与 API / 语音服务 / 背景音乐 / 素材库 / 用量统计 / 使用手册），
 * 点了第三栏直接开整页。「模型与 API」不再展开四个子分段——分段切换在整页里的胶囊做
 * （原型拍板 col2 只列整页栏目；第二栏直达分段的入口随之撤销，动线没变长：整页内一步就到）。
 * ⚠ 第 40 轮撤入口时整页里的那一排胶囊还是 `if (!embedded)` 藏着的 ⇒ 四个分段一度都点不到，
 * 第 41 轮补上（`SettingsScreen` 里不再按 embedded 分流），此后"切换器在页内"才算真的成立。
 */
@Composable
internal fun DesktopProfileRail(
    selected: ProfileGroup,
    onSelect: (ProfileGroup) -> Unit,
    /** 点「整页栏目」段（模型与 API / 语音服务 / …）：第三栏直接开整页 */
    onOpenPage: (ProfileGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    // 上段＝节行：带图标、不缩进
    val sectionRow: @Composable (ProfileGroup) -> Unit = { g ->
        val isSel = selected == g
        DesktopRailRow(
            label = g.label,
            selected = isSel,
            onClick = { onSelect(g) },
            leading = {
                Icon(
                    ProfileGroupIcon[g] ?: Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSel) colors.onSecondaryContainer else colors.onSurfaceVariant
                )
            }
        )
    }
    // 下段＝整页栏目：缩进、不带图标（层级一眼分得开，也不必为它再挑一批图标）
    val pageRow: @Composable (ProfileGroup) -> Unit = { g ->
        DesktopRailRow(
            label = g.label,
            selected = selected == g,
            indent = 10.dp,
            onClick = { onOpenPage(g) }
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
    ) {
        DesktopRailHeader("用户")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(
                listOf(
                    ProfileGroup.ACCOUNT, ProfileGroup.PRESETS, ProfileGroup.APPEARANCE,
                    ProfileGroup.AI_VOICE, ProfileGroup.DATA, ProfileGroup.DESKTOP,
                    ProfileGroup.ABOUT
                )
            ) { sectionRow(it) }
            item { DesktopRailSectionLabel("整页栏目") }
            items(
                listOf(
                    ProfileGroup.API, ProfileGroup.VOICE, ProfileGroup.BGM,
                    ProfileGroup.MATERIALS, ProfileGroup.USAGE, ProfileGroup.MANUAL
                )
            ) { pageRow(it) }
        }
    }
}

/**
 * 问题 #9：主题设置弹窗。
 * 之前只有一个「深色主题」开关，文案永远不变；现在改成明确的三个选项
 * （浅色 / 深色 / 跟随系统）。
 * 原来第四个「其他」是给皮肤中心留的占位，皮肤中心始终没接进来，
 * 用户 2026-10-10 要求撤掉——[ThemeMode] 里已无此项，这里也不再特判。
 */
@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onPick: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题设置") },
        text = {
            Column {
                ThemeMode.entries.forEach { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(m) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = m == current, onClick = { onPick(m) })
                        Spacer(Modifier.width(4.dp))
                        Text(m.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 字体设置弹窗：内置字体族 + 上传自定义字体 + 字号缩放，改动即时全局预览 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontDialog(onDismiss: () -> Unit) {
    // 问题反馈 #2：字号先只在弹窗内预览，关掉弹窗才写进全局——
    // 否则每拖一下整棵主题都重组、弹窗窗口跟着重建，看着就是「来回弹出」。
    var scale by remember { mutableStateOf(ThemeState.fontScale) }
    fun commitScale() {
        if (scale != ThemeState.fontScale) ThemeState.setFontScale(scale, persist = true)
    }
    val pickFont = rememberFontFilePicker { path, name ->
        if (path != null) ThemeState.setCustomFont(path, name, persist = true)
    }
    val customPath = ThemeState.customFontPath

    AlertDialog(
        onDismissRequest = { commitScale(); onDismiss() },
        title = { Text("字体设置") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("内置字体", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                // 这里原先用普通 Row：4 个选项在弹窗宽度里排不下，
                // 最右边的「等宽」被挤到弹窗外面（用户反馈"显示不出来"）。
                // 改用 FlowRow 自动换行，选项再多也不会丢。
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppFont.entries.forEach { f ->
                        WhaleChip(
                            selected = customPath == null && ThemeState.font == f,
                            onClick = {
                                ThemeState.setCustomFont(null, "", persist = true)
                                ThemeState.setFont(f, persist = true)
                            },
                            label = { Text(f.label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("自定义字体", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                if (ThemeState.customFonts.isEmpty()) {
                    Text(
                        "还没上传。支持 .ttf / .otf / .ttc，宋体、仿宋、黑体都能用——从手机或网盘里选一个；" +
                            "上传过的都会留在下面，可以随时切换或删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
    // 问题 #20：已上传的字体全部列出，点一下就切过去
    val fontDialogScope = rememberCoroutineScope()
    ThemeState.customFonts.forEach { f ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            // LocalContext 原本在 forEach 的每一项里取（每项一次查找），提到循环外
            RadioButton(
                selected = customPath == f.path,
                onClick = {
                    // 文件可能已被系统清理或本身是坏文件（老版本没拦住）：点了给提示，别无声失败
                    if (isUsableFontFile(f.path)) {
                        // 再过一遍渲染探针（独立进程）：能渲染才切换，避免选中即崩溃的老字体
                        fontDialogScope.launch {
                            val renderable = runCatching { Platform.ui.probeFontRenderable(f.path) }.getOrDefault(false)
                            if (renderable) {
                                ThemeState.setCustomFont(f.path, f.name, persist = true)
                            } else {
                                showToast("这个字体文件已失效，建议点右侧「删除」清掉", long = true)
                            }
                        }
                    } else {
                        showToast("这个字体文件已失效，建议点右侧「删除」清掉", long = true)
                    }
                }
            )
                            Text(
                                f.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { ThemeState.removeCustomFont(f) }) { Text("删除") }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { pickFont() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Upload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("上传字体文件")
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "字号  ${(scale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = ThemeState.MIN_FONT_SCALE..ThemeState.MAX_FONT_SCALE,
                    // 问题 #20：按 5% 一档，别让拖动停在不想要的小数上
                    steps = 10
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scale = (scale - 0.05f)
                                .coerceIn(ThemeState.MIN_FONT_SCALE, ThemeState.MAX_FONT_SCALE)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("小一点 −") }
                    OutlinedButton(
                        onClick = {
                            scale = (scale + 0.05f)
                                .coerceIn(ThemeState.MIN_FONT_SCALE, ThemeState.MAX_FONT_SCALE)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("大一点 ＋") }
                }
                Spacer(Modifier.height(6.dp))
                // 预览按「相对当前全局字号」的比例算：拖动立即变，又不会被 density 二次放大
                val previewSize = MaterialTheme.typography.bodyMedium.fontSize
                    .div(ThemeState.fontScale).times(scale)
                Text(
                    "预览：这是一段示例文字，The quick brown fox 1234。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = previewSize
                )
            }
        },
        confirmButton = { TextButton(onClick = { commitScale(); onDismiss() }) { Text("完成") } },
        dismissButton = {
            TextButton(
                onClick = {
                    scale = 1f
                    ThemeState.setCustomFont(null, "", persist = true)
                    ThemeState.setFont(AppFont.SYSTEM, persist = true)
                    ThemeState.setFontScale(1f, persist = true)
                }
            ) { Text("恢复默认") }
        }
    )
}

@Composable
private fun AccountCard(
    profile: Profile?,
    onPickAvatar: () -> Unit,
    onPreviewAvatar: () -> Unit,
    onManage: () -> Unit
) {
    // 内容自带 18dp 内边距（在下面那行 Row 上），所以这里 contentPadding 给 0，免得两头都留
    SectionCard(
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        bordered = true,
        contentPadding = 0.dp,
        spacing = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    // 有头像时先弹预览（可 更换 / 编辑），没头像就直接去选图
                    .clickable {
                        if (profile?.avatarUri.isNullOrBlank()) onPickAvatar() else onPreviewAvatar()
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageModel(profile?.avatarUri),
                    contentDescription = "头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (profile?.avatarUri.isNullOrBlank()) {
                    Icon(
                        Icons.Filled.Person,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile?.nickname ?: "未登录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (profile == null) "还没有账号" else "本地账号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onManage) { Text("账号") }
        }
    }
}

@Composable
private fun AccountsDialog(
    currentId: String?,
    onDismiss: () -> Unit,
    onSwitched: () -> Unit,
    onRegister: () -> Unit,
    onSwitchAccount: () -> Unit,
    onLogout: () -> Unit
) {
    val profiles = Repository.listProfiles()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账号管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("注册新账号")
                }
                if (profiles.isEmpty()) {
                    Text("还没有账号，点上面注册一个。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    profiles.forEach { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    Repository.setCurrentAccount(p.id)
                                    onSwitchAccount()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = imageModel(p.avatarUri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.nickname, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (p.id == currentId) "当前账号" else "点击切换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.id == currentId) LocalWhalePalette.current.accentText
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (currentId != null) {
                    TextButton(
                        onClick = { onLogout() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("退出登录") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 「外观与设置」里的一行：`arrow = false` 时不画右侧箭头；`trailing` 优先于箭头（开关行用） */
private data class AppearanceRowItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val arrow: Boolean = true,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit
)

// 设置条目：圆形图标 + 标题/副标题 + 可选尾部控件
@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)?,
    onClick: () -> Unit
    ) {
        val type = LocalWhaleType.current
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.label, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing?.invoke()
    }
}

/** 状态带的一格（原型 `.led` 的 div）：大等宽值 ＋ 小等宽标签；[ok] 非空时值旁带状态点 */
private data class ProfileBandCell(val value: String, val label: String, val ok: Boolean? = null)

/**
 * 状态带（原型 v6 · 用户 B 的 `band`）：四格常驻读数——Key 就绪 / 对话模型 / 语音朗读 / 缓存可清理。
 * "一眼看出系统现在接成了什么样"。[wrap]＝手机折行两列（原型 `.led.w`），桌面单行四格。
 */
@Composable
private fun ProfileBand(cells: List<ProfileBandCell>, wrap: Boolean) {
    val colors = MaterialTheme.colorScheme
    val type = LocalWhaleType.current
    val rows: List<List<ProfileBandCell>> = if (wrap) cells.chunked(2) else listOf(cells)
    WhalePanelCard {
        rows.forEachIndexed { ri, rowCells ->
            if (ri > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                rowCells.forEachIndexed { ci, c ->
                    if (ci > 0) {
                        Box(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .width(1.dp)
                                .height(26.dp)
                                .background(colors.outlineVariant)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.value,
                                style = type.mono(type.small).copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.2).sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (c.ok == true) {
                                Spacer(Modifier.width(5.dp))
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.primary)
                                )
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        WhaleMonoLabel(c.label)
                    }
                }
            }
        }
    }
}

/** 原型 `.srowv`：行右侧的"当前值"——等宽小字、次要色、超长省略（2026-10-10 由 meta(11) 升 caption(12)） */
@Composable
private fun SummaryValue(text: String) {
    val type = LocalWhaleType.current
    Text(
        text,
        style = type.mono(type.caption),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 面板里的行集。**桌面＝v10 字段表**（卡由 [WhaleFieldGroup] 提供：行无行首图标、标题＋一句短说明
 * 在左、控件右贴、行下发丝）；**手机＝原样**（手风琴展开层里的 [SettingRow]，本轮零改动）。
 */
@Composable
private fun PanelRows(rows: List<AppearanceRowItem>) {
    if (rows.isEmpty()) return
    if (isDesktopLayout) {
        Column {
            rows.forEachIndexed { i, row ->
                // 显式变量而不是把 if 塞进实参：`?:` 与 if 混写时 Kotlin 推不出
                // `@Composable () -> Unit`（实参位报 "actual type is Nothing"）
                val arrow: (@Composable () -> Unit)? = if (row.arrow) {
                    {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else null
                WhaleFieldRow(
                    title = row.title,
                    desc = row.subtitle,
                    divider = i < rows.lastIndex,
                    trailing = row.trailing ?: arrow,
                    onClick = row.onClick
                )
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SettingRow(
                icon = row.icon,
                title = row.title,
                subtitle = row.subtitle,
                trailing = row.trailing ?: if (row.arrow) {
                    {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else null,
                onClick = row.onClick
            )
        }
    }
}

// 预设设定卡片
@Composable
private fun SettingSection(
    settings: List<UserSetting>,
    defaultId: String,
    onNew: () -> Unit,
    onEdit: (UserSetting) -> Unit,
    onDelete: (UserSetting) -> Unit,
    onSetDefault: (String) -> Unit
) {
    // 删预设 = 用户手写的 persona 文本从磁盘消失，先过一道确认（2026-09-21 统一补）
    var pendingDelete by remember { mutableStateOf<UserSetting?>(null) }
    /**
     * 展开 / 收起（用户 2026-09-23 反馈：设定一多，这一栏把下面五张分区卡顶到很远，"不好翻"）。
     * **默认收起**；开关本身写回设置（[AiSettings.presetsExpanded]），用户自己展开过就一直展开——
     * 那是他的选择，不该每次进页面替他复位。写盘失败只影响"下次还记不记得"，界面照常切。
     */
    var expanded by remember { mutableStateOf(Repository.loadSettings().presetsExpanded) }
    val defaultName = settings.firstOrNull { it.id == defaultId }?.name
    // 标题行整行可点（展开/收起）、不是纯标题，所以标题留在内容里、只摘外壳；内边距仍是那句 18dp
    SectionCard(
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        bordered = true,
        contentPadding = 0.dp,
        spacing = 0.dp
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 标题行整行可点＝展开区藏在标题里。收起时这行**必须**带摘要：一收起就什么都不显示的话，
            // 用户得先点开才知道里面有没有东西（"当前默认是谁、一共几份"是这栏最常看的两件事）。
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        Repository.saveSettings(Repository.loadSettings().copy(presetsExpanded = expanded))
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("我的设定（预设）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            expanded -> "新会话默认使用下面选中的设定；不同角色可在会话里单独换。"
                            settings.isEmpty() -> "还没有设定，点这里展开新建。"
                            else -> "当前默认：${defaultName ?: "无设定"} · 共 ${settings.size} 份"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起我的设定" else "展开我的设定",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "当前默认：" + (if (defaultName != null) defaultName else "无设定"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (defaultName != null) LocalWhalePalette.current.accentText
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (defaultName != null) {
                        TextButton(onClick = { onSetDefault("") }) { Text("改为默认无设定") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (settings.isEmpty()) {
                    Text("还没有预设设定，点下面新建一个。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    settings.sortedByDescending { it.id == defaultId }.forEach { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (s.id == defaultId) {
                                        Text(
                                            "默认",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                }
                                Text(
                                    s.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = { onSetDefault(s.id) }) {
                                Text(if (s.id == defaultId) "默认" else "设为默认")
                            }
                            IconButton(onClick = { onEdit(s) }) { Icon(Icons.Filled.Edit, "编辑") }
                            IconButton(onClick = { pendingDelete = s }) {
                                Icon(Icons.Filled.Close, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                TextButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建设定")
                }
            }
        }
    }
    pendingDelete?.let { target ->
        WhaleConfirmSheet(
            title = "删除「${target.name}」？",
            body = "这份设定的内容会被永久删除，无法恢复。",
            confirmLabel = "删除",
            onConfirm = { pendingDelete = null; onDelete(target) },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun SettingEditorDialog(
    setting: UserSetting?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(setting?.name ?: "") }
    var content by remember { mutableStateOf(setting?.content ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setting == null) "新建设定" else "编辑设定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("设定内容") },
                    minLines = 4,
                    // #18：给出可照抄的占位示例，别让人对着空框发呆
                    placeholder = {
                        Text(
                            "例：我叫林渡，25 岁，程序员，说话直接、怕麻烦；和 TA 是大学同学，暗恋了三年。\n\n" +
                                "这里写的是「你」——之后 AI 会一直按这个身份跟你对话。也可以写称呼、性格、背景、说话习惯。"
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, content) },
                enabled = name.isNotBlank() && content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// 方便统一处理 remember mutableStateOf（避免重复 import 结构）
@Composable
private fun <T> memoryState(initial: T) =
    androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial) }

/** 字节数转成人看的单位（问题 #21：清除缓存要能看见大小） */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/**
 * 清除缓存的确认弹窗（2026-09-15）：先把"要删什么"列清楚，再让用户按下清除。
 *
 * 之前点一下就直接删，用户既不知道删的是什么，也不知道**未被引用的生图删掉就找不回来了**。
 * [breakdown] 为 null 表示还在扫描。
 */
@Composable
private fun CacheCleanupDialog(
    breakdown: Repository.CacheBreakdown?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WhaleActionSheet(
        onDismiss = onDismiss,
        title = "清除缓存",
        confirmLabel = "清除",
        confirmEnabled = (breakdown?.totalBytes ?: 0L) > 0L,
        onConfirm = onConfirm
    ) {
        val b = breakdown
        if (b == null) {
            Text("正在统计…", style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (b.totalBytes <= 0L && b.ttsBytes <= 0L) {
                    Text("没有可清理的内容。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    CacheLine("临时文件（备份导出等）", formatBytes(b.cacheDirBytes))
                    CacheLine("未使用的生图（${b.orphanImages.size} 张）", formatBytes(b.orphanBytes))
                    CacheLine("更新包残留（${b.staleApks.size} 个）", formatBytes(b.apkBytes))
                    // tts 语音缓存保留不清（清了同句朗读要重新付费合成），只在这里报个占用
                    if (b.ttsBytes > 0L) {
                        CacheLine("语音朗读缓存（保留不清）", formatBytes(b.ttsBytes))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    CacheLine("可清理合计", formatBytes(b.totalBytes), emphasize = true)
                }
                Text(
                    "不会动角色、会话、设置与聊天记录里用到的图片。" +
                        "语音朗读缓存保留（同句朗读不重新合成）。" +
                        if (b.orphanImages.isNotEmpty()) "未被引用的生图删除后无法找回。" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 缓存明细里的一行：名称在左、大小在右 */
@Composable
private fun CacheLine(label: String, size: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasize) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            size,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else null
        )
    }
}

/** 生成备份 JSON 并交给系统分享面板（微信/文件/网盘等），失败时提示。 */
private suspend fun shareBackup() {
    // 导出要把全部角色 + 全部会话（含内嵌 base64 图片）拼成一个几十 MB 的字符串再落盘。
    // 2026-09-16 之前这些全在主线程上做：真机上点一下「导出备份」就是几秒白屏（ANR 风险）。
    // 不需要"等排队写入落盘"的屏障 —— exportJson 读的是 Repository 的写穿缓存版本，
    // 已排队未落盘的改动照样在返回值里；只有图片文件是直读磁盘的，而图片写入本来就是同步的。
    val exported = withContext(Dispatchers.IO) {
        runCatching {
            val json = Backup.exportJson()
            val dir = File(Repository.cacheRootDir, "backups").apply { mkdirs() }
            // 每次导出都在 cacheDir 留一份完整备份 JSON（含 base64 图片，可能几十 MB），
            // 而 cacheDir 只有用户主动"清除缓存"才会被清 —— 连续导出几次就能把应用占地撑起来。
            // 这里只留最近 1 份（排掉比"本次"更早的），本次那份照常写。
            runCatching {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(1)
                    ?.forEach { it.delete() }
            }
            val file = File(dir, "mysticat-backup-${System.currentTimeMillis()}.json")
            file.writeText(json)
            file
        }
    }
    exported.onSuccess { file ->
        runCatching { Platform.ui.shareFile(file, "application/json", "导出 MystiCat 备份") }
            .onFailure { showToast("分享失败：${it.message}", long = true) }
    }.onFailure {
        showToast("导出失败：${it.message}", long = true)
    }
}

/** 数据备份弹窗：导出 / 从备份恢复（恢复前需二次确认） */
@Composable
private fun BackupDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }
    /** 导出中（含 base64 图片的 JSON 可能几十 MB，要占几秒，期间禁用按钮并显示进度） */
    var exporting by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val pickRestore = rememberJsonFilePicker { text ->
        if (!text.isNullOrBlank()) pendingRestore = text
    }

    val doRestore = {
        val text = pendingRestore
        if (text != null && !restoring) {
            restoring = true
            msg = null
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { Backup.importJson(text) }
                    msg = "恢复完成：${result.characters} 个角色、${result.conversations} 个会话、" +
                        "${result.books} 本世界书、${result.materials} 条素材"
                } catch (t: Throwable) {
                    msg = t.message ?: "恢复失败"
                } finally {
                    restoring = false
                    pendingRestore = null
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!restoring) onDismiss() },
        title = { Text("数据备份") },
        text = {
            if (pendingRestore != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前所有角色、会话与素材库将被备份中的内容覆盖，此操作不可撤销。")
                    msg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = LocalWhalePalette.current.accentText)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        // "图片不随 JSON 迁移"是 formatVersion 1 时代的说法：备份会把这些图片
                        // 以 base64 内嵌进 assets、恢复时重新落盘并改写引用（Backup.kt 的 remap），
                        // 所以换机恢复后头像/背景（含分端的两份背景）都还在。
                        // 2026-09-18 实测：导出的 JSON 里能找到本机背景图的文件名键，文案据此更正。
                        "备份包含全部角色卡与会话记录、世界书、素材库，以及其中用到的头像/背景/素材图片（不含 API Key）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (!exporting) {
                                    exporting = true
                                    // 导出已经搬出主线程（见 shareBackup），这里只负责按钮状态与收尾
                                    scope.launch {
                                        try {
                                            shareBackup()
                                        } finally {
                                            exporting = false
                                        }
                                    }
                                }
                            },
                            enabled = !exporting && !restoring
                        ) {
                            if (exporting) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (exporting) "导出中…" else "导出备份")
                        }
                        OutlinedButton(onClick = pickRestore, enabled = !restoring) {
                            if (restoring) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (restoring) "恢复中…" else "从备份恢复")
                        }
                    }
                    msg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = LocalWhalePalette.current.accentText)
                    }
                }
            }
        },
        confirmButton = {
            if (pendingRestore != null) {
                TextButton(onClick = { doRestore() }, enabled = !restoring) { Text("覆盖恢复") }
            } else {
                TextButton(onClick = onDismiss, enabled = !restoring) { Text("关闭") }
            }
        },
        dismissButton = {
            if (pendingRestore != null) {
                TextButton(onClick = { pendingRestore = null }, enabled = !restoring) { Text("取消") }
            }
        }
    )
}
