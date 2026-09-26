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
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.snapshotFlow
import com.mysticat.roleplay.ui.isDesktopLayout
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.DesktopRailRow
import com.mysticat.roleplay.ui.ImagePreviewDialog
import com.mysticat.roleplay.ui.rememberFontFilePicker
import com.mysticat.roleplay.ui.rememberGallerySaver
import com.mysticat.roleplay.ui.rememberImagePicker
import com.mysticat.roleplay.ui.SectionCard

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
    setOf(ProfileGroup.API, ProfileGroup.VOICE, ProfileGroup.BGM, ProfileGroup.MANUAL, ProfileGroup.ABOUT)

/**
 * 拉通流里每个**节**所在的 LazyColumn 下标。
 *
 * 与 [ProfileScreen] 里那七个 `item {}` 的**出现顺序**一一对应：拉通流恒为全部节渲染，
 * 所以这个下标不会因为"只渲染某几节"而错位。⚠ 节数、顺序与下面两个映射**必须一起改**
 * （改了节就要同时动 `desktopStreamIndex` / `streamGroupAt` 与那七个 item）。
 *
 * 页面型栏目也要给得出下标：第二栏点「语音服务」这类入口时 `profilePage` 已是 `true`
 * （走整页提前返回，不滚动），但**从整页切回总表**的那一下要落到它所属的节上，所以映射到父节。
 */
private fun desktopStreamIndex(group: ProfileGroup?): Int = when (group) {
    ProfileGroup.PRESETS -> 1
    ProfileGroup.APPEARANCE -> 2
    ProfileGroup.AI_VOICE, ProfileGroup.API, ProfileGroup.VOICE, ProfileGroup.BGM -> 3
    ProfileGroup.DATA -> 4
    ProfileGroup.DESKTOP -> 5
    ProfileGroup.ABOUT, ProfileGroup.MANUAL -> 6
    else -> 0 // 账号
}

/** 拉通流下标 → 节（滚动跟随时反查；与 [desktopStreamIndex] 互为逆映射，只在桌面用） */
private fun streamGroupAt(index: Int): ProfileGroup = when (index) {
    1 -> ProfileGroup.PRESETS
    2 -> ProfileGroup.APPEARANCE
    3 -> ProfileGroup.AI_VOICE
    4 -> ProfileGroup.DATA
    5 -> ProfileGroup.DESKTOP
    6 -> ProfileGroup.ABOUT
    else -> ProfileGroup.ACCOUNT
}

/** 拉通流里每节上方的节标题（桌面专属：手机上各节是"一屏一组"，不需要目录感） */
@Composable
private fun StreamSectionTitle(text: String) {
    Text(
        text,
        // 比卡内标题（titleSmall）大一级：节标题与卡标题同款的话，层级看不出来
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenVoice: () -> Unit,
    /** 「背景音乐」设置页（手机端走独立路由；桌面三栏走 [ProfileGroup.BGM] 内嵌，不用这个回调） */
    onOpenBgm: () -> Unit = {},
    /** 「使用手册」（手机端走独立路由；桌面三栏走 [ProfileGroup.MANUAL] 内嵌，不用这个回调） */
    onOpenManual: () -> Unit = {},
    /**
     * 「关于鲸鱼」（合规与安全 ＋ 版本与更新并成的一整页）：手机端走独立路由；
     * 桌面三栏走 [ProfileGroup.ABOUT] 内嵌（整页形态），不用这个回调。
     */
    onOpenAbout: () -> Unit = {},
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
     * 桌面拉通流：第二栏点栏目时递增一次 ⇒ 本页滚到对应节（用户要的"快速定位"）。
     * 用递增信号而不是直接传目标栏目：连点同一个栏目也要能重新滚回去（值不变触发不了）。
     */
    profileAnchorSignal: Int = 0,
    /**
     * 拉通流滚动时上报"现在看得见哪一节"：第二栏据此高亮，用户才知道自己滚到了哪。
     * **只在跨节时回调**（见下面的 distinctUntilChanged），滚动中不会每帧回调外壳。
     */
    onStreamSection: (ProfileGroup) -> Unit = {},
    /**
     * 桌面：第三栏是否**整页显示** [desktopGroup] 对应的那一栏。
     * 总设置表里每个栏目都占一节，其中这四个另有整页形态：表里给一行入口，点了整页打开；
     * 第二栏也能直达（模型与 API 的四个子分段就是直达的）。
     */
    desktopPage: Boolean = false,
    /** 桌面：点了总设置表里某一节的入口行（外壳负责把它切进整页） */
    onOpenPage: (ProfileGroup) -> Unit = {},
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
    // 桌面走到这里就是「总设置表」形态（整页形态已在函数开头 return 掉了）；手机端 desktopGroup 恒为
    // null 且走同一条流，只是不画节标题、不做定位。两种情形都是"全部节都渲染"。
    val desktopStream = isDesktopLayout
    val showAll = desktopGroup == null || desktopStream
    val streamState = rememberLazyListState()
    LaunchedEffect(profileAnchorSignal) {
        // 只在拉通流里定位；首次组合（信号仍为 0）不滚，免得一进「用户」页就跳到底部
        if (desktopStream && profileAnchorSignal > 0) {
            streamState.animateScrollToItem(desktopStreamIndex(desktopGroup))
        }
    }
    LaunchedEffect(desktopStream) {
        if (!desktopStream) return@LaunchedEffect
        // `drop(1)`：组合那一刻的下标不算"用户滚到了这里"——否则一进页面就先闪一下顶部那一行
        snapshotFlow { streamState.firstVisibleItemIndex }
            .drop(1)
            .distinctUntilChanged()
            .collect { onStreamSection(streamGroupAt(it)) }
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

    val pickAvatar = rememberImagePicker { uri ->
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
                        else MaterialTheme.colorScheme.primary
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
        )
    ) else emptyList()
    val dataRows = listOf(
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
        )
    ) else emptyList()

    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (desktopGroup) {
                // 语音 / 背景音乐 / 使用手册 / 模型与 API 这四栏的**整页形态**已在本函数开头
                // 提前返回掉了（[desktopPage]）；走到这里就是总设置表——每个栏目都占一节。
                else -> LazyColumn(
                    state = streamState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
            // 拉通流（桌面）恒渲染全部节——第二栏点哪一节由 [profileAnchorSignal] 滚过去，
            // 而不是把别的节从树里摘掉：摘掉就回到"一屏一组"，也就谈不上页内定位了。
            if (showAll || desktopGroup == ProfileGroup.ACCOUNT) {
            item {
                if (desktopStream) StreamSectionTitle("账号")
                AccountCard(
                    profile,
                    onPickAvatar = pickAvatar,
                    onPreviewAvatar = { showAvatarPreview = true },
                    onManage = { showAccounts = true }
                )
            }
            }

            if (showAll || desktopGroup == ProfileGroup.PRESETS) {
            item {
                if (desktopStream) StreamSectionTitle("我的设定")
                SettingSection(
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
            }
            }

            // ── 总设置表（桌面）：七节，与手机端那几张卡一一对应──────────────
            // 桌面恒渲染全部七节（不是"选一节渲染一节"）：第二栏点哪一节由 [profileAnchorSignal]
            // 滚过去，节本身始终在树里。⚠ 七节的**数量与顺序**就是 [desktopStreamIndex] 的下标。
            if (desktopStream) {
                // 手机端把外观 / AI 与语音 / 数据与存储三组挤在同一个 item 里（间距 10dp），
                // 桌面端必须**一节一个 item**——快速定位要滚到"某一节"，挤在一起就只滚得到头一节。
                item {
                    StreamSectionTitle(ProfileGroup.APPEARANCE.label)
                    SettingsGroupCard(null, appearanceRows)
                }
                item {
                    StreamSectionTitle(ProfileGroup.AI_VOICE.label)
                    SettingsGroupCard(null, aiPageRows)
                }
                item {
                    StreamSectionTitle(ProfileGroup.DATA.label)
                    SettingsGroupCard(null, dataRows)
                }
                item {
                    // 桌面行在非桌面平台为空（那些开关没有对应物）——节仍占位，保住七节下标恒定
                    if (desktopRows.isNotEmpty()) {
                        StreamSectionTitle(ProfileGroup.DESKTOP.label)
                        SettingsGroupCard(null, desktopRows)
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsGroupCard("外观", appearanceRows)
                        if (serviceRows.isNotEmpty()) SettingsGroupCard("AI 与语音", serviceRows)
                        SettingsGroupCard("数据与存储", dataRows)
                    }
                }
            }

            // ── 「帮助与关于」节 ──────────────────────────────────────────────────
            // 它独立成节（原先躲在「外观与设置」里 ⇒ 点第二栏那一行会拿到**空白的第三栏**）；
            // 与手机端**同名同义**：使用手册一行（桌面＝整页栏目）+ 关于鲸鱼一行。
            if (showAll || desktopGroup == ProfileGroup.APPEARANCE || desktopGroup == ProfileGroup.ABOUT) {
            item {
                if (desktopStream) StreamSectionTitle(ProfileGroup.ABOUT.label)
                SettingsGroupCard(if (desktopStream) null else "帮助与关于", helpRows)
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
        topBar = {
            TopAppBar(
                title = { Text("用户") },
                // 与角色卡/聊天记录/灵感创作/设置页统一成主色调顶栏。
                // 之前这里是默认样式（surface 灰白底），和其他页放一起明显不是一套。
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "返回")
                        }
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
    ProfileGroup.MANUAL to Icons.Filled.AutoStories
)

/**
 * 桌面第二栏（「用户」页）：**设置目录**（按新分类重排）。
 *
 * 两级：一级行＝**节**（账号 / 我的设定 / 外观 / AI 与语音 / 数据与存储 / 桌面 / 帮助与关于，
 * 与手机端那几张卡一一对应，点了滚到表里那一节）；二级行＝节里的**整页入口**（缩进、不带图标，
 * 与「模型与 API」那四个分段同款）——「AI 与语音」下挂模型与 API / 语音服务 / 背景音乐，
 * 「帮助与关于」下挂使用手册。选中态体现两件事：所在行高亮（页面型栏目高亮那一行，
 * 卡片型栏目由滚动跟随上报的节来高亮），或在 API 段里那一条分段高亮。
 */
@Composable
internal fun DesktopProfileRail(
    selected: ProfileGroup,
    settingsTab: SettingsTab,
    onSelect: (ProfileGroup) -> Unit,
    onSelectSettingsTab: (SettingsTab) -> Unit,
    /** 点二级行里的整页栏目（语音服务 / 背景音乐 / 使用手册 / 关于鲸鱼）：第三栏直接开整页 */
    onOpenPage: (ProfileGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    // 一级行＝节：带图标、不缩进
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
    // 二级行＝整页入口：缩进、不带图标（层级一眼分得开，也不必为它再挑一批图标）
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
            items(listOf(ProfileGroup.ACCOUNT, ProfileGroup.PRESETS, ProfileGroup.APPEARANCE)) { sectionRow(it) }
            // 「AI 与语音」＝节行 + 六个整页入口：模型与 API 的四个分段**直跳整页**（用户 ③ 点名的那条
            // 例外：不必先进去再点胶囊）＋ 语音服务 / 背景音乐。四个分段**不挂「模型与 API」小标题**
            // ——用户 2026-09-23：那一行灰字在目录里像坏掉的一行，去掉后与语音/背景音乐同级缩进即可。
            item { sectionRow(ProfileGroup.AI_VOICE) }
            items(SettingsTab.entries) { t ->
                DesktopRailRow(
                    label = t.label,
                    selected = selected == ProfileGroup.API && settingsTab == t,
                    indent = 10.dp,
                    onClick = { onSelect(ProfileGroup.API); onSelectSettingsTab(t) }
                )
            }
            items(listOf(ProfileGroup.VOICE, ProfileGroup.BGM)) { pageRow(it) }
            items(listOf(ProfileGroup.DATA, ProfileGroup.DESKTOP, ProfileGroup.ABOUT)) { sectionRow(it) }
            item { pageRow(ProfileGroup.MANUAL) }
        }
    }
}

/**
 * 问题 #9：主题设置弹窗。
 * 之前只有一个「深色主题」开关，文案永远不变；现在改成明确的四个选项，
 * 「其他」预留给后续皮肤中心。
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
                        Column {
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                            if (m == ThemeMode.OTHER) {
                                Text(
                                    "皮肤中心，后续版本开放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                                    color = if (p.id == currentId) MaterialTheme.colorScheme.primary
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
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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

/**
 * 一张设置分区卡：标题 + 若干行 + 行间分隔线。
 *
 * 抽出来的直接原因：「用户」页从"一张大卡塞 8 行"改成"按用途分四张卡"，每张骨架一模一样；
 * 顺带修掉用户点名的**左对齐问题**——「版本与安全」原来直接挂在 Card 上（少了这层 18.dp 内边距），
 * 图标比上面每一行都靠左。凡是"一行一入口"都走这张卡，免得再出现第二个单挂 Card 的行。
 */
@Composable
private fun SettingsGroupCard(title: String?, rows: List<AppearanceRowItem>) {
    if (rows.isEmpty()) return
    // 桌面：卡片外壳（底色 + 描边 + 阴影）由 SectionCard 在桌面上摘掉，
    // 标题改成卡外的分区标题 + 分隔线。桌面态的调用点仍传 `null`——那一层的标题由
    // `StreamSectionTitle` 提供，再补一个就成了两层标题。
    SectionCard(
        title = title,
        shape = RoundedCornerShape(20.dp),
        elevation = 3.dp,
        bordered = true,
        contentPadding = 18.dp,
        spacing = 4.dp
    ) {
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
                        color = if (defaultName != null) MaterialTheme.colorScheme.primary
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("这份设定的内容会被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(target) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除缓存") },
        text = {
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
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = (breakdown?.totalBytes ?: 0L) > 0L
            ) { Text("清除") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
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
                    msg = "恢复完成：${result.characters} 个角色、${result.conversations} 个会话"
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
                    Text("当前所有角色与会话将被备份中的内容覆盖，此操作不可撤销。")
                    msg?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        // "图片不随 JSON 迁移"是 formatVersion 1 时代的说法：备份会把这些图片
                        // 以 base64 内嵌进 assets、恢复时重新落盘并改写引用（Backup.kt 的 remap），
                        // 所以换机恢复后头像/背景（含分端的两份背景）都还在。
                        // 2026-09-18 实测：导出的 JSON 里能找到本机背景图的文件名键，文案据此更正。
                        "备份包含全部角色卡与会话记录，以及其中用到的头像/背景图片（不含 API Key）。",
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
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
