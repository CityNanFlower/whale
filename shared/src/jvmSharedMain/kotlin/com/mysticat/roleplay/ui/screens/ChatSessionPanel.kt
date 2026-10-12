package com.mysticat.roleplay.ui.screens


import com.mysticat.roleplay.ui.tokens.WhaleOverlays
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import com.mysticat.roleplay.ui.DesktopSessionRailWidth
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleGlass
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.ChatAreaScrims
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.dimmedScrims
import com.mysticat.roleplay.ui.theme.LocalWhalePalette

/** 二级整页压在会话背景图上时，主题底色那层的透明度：透出图、又保得住页内主题色文字的对比度 */
private const val PanelVeilAlpha = 0.45f  // 与 WhaleGlass.pageVeil 同档：图要露得出来

/**
 * 会话页的二级**整页**（用户拍板：不要拉取窗口/浮层，要独立页面）：
 * 同一份内容两端同用，铺满聊天页所在区域（手机＝整屏；桌面＝第三栏，左栏会话列表不受影响）。
 * 自带顶栏（返回 + 标题）与滚动内容，返回走调用方的关闭回调。
 * **返回键/Esc 由容器自己接**（[WhaleBackHandler]）——这样每个用它的整页都自动"返回关页"，
 * 不必在调用处各注册一次（漏一处就是"按返回直接退出聊天"）。
 *
 * [background]/[bgDim] 给它"背景跟会话页一样"（用户 2026-09-26 反馈：独立页不该是一整块突兀的主题底色）：
 * 有背景图时铺图 + 与会话页同款的 [ChatAreaScrims] 暗化层，**再盖一层半透明主题底色**——
 * 页内内容（分区标题、行、chip、滑杆）用的仍是主题色，不压这层的话在图上读不出来。
 * 桌面端传 null（第三栏是并排的侧栏、不是"覆盖聊天区的页面"，铺竖图会被裁成中间一条），是否跟随由调用方决定。
 */
@Composable
internal fun ChatOverlayPage(
    title: String,
    onBack: () -> Unit,
    background: String? = null,
    bgDim: Float = 1f,
    content: @Composable () -> Unit
) {
    WhaleBackHandler(onBack = onBack)
    // ⚠ 整页不经过 Scaffold，必须自己避开系统栏：否则手机上顶栏画进状态栏触摸区，
    // 「返回」怎么点都没反应（实测：状态栏触摸区 132px，按钮 bounds 全在其中）。
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val uri = background
        if (uri != null) {
            imageModel(uri)?.let { model ->
                // 独立页背景＝聊天背景**模糊一点**（用户 2026-10-01：所有从会话界面进入的独立页面都这样）。
                // 模糊掉之后图只剩明暗与色块，页内的行和分区标题不再跟图抢注意力。
                AsyncImage(
                    model = model,
                    contentDescription = "聊天背景",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(WhaleGlass.blurPage)
                )
            }
            // ⚠ 这里**不再铺 [ChatAreaScrims]**（v11 设计合同 §1，评审改法 1）：scrim 是给聊天文字层压暗用的，
            // 铺到设置面底下会把合成底压到够不着对比度门槛（实测浅色 182 / 结构 6 级）。
            // 关掉它之后本页＝"原图（模糊）× veil .45"，与面板/右栏/编辑面同一套栈。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = PanelVeilAlpha))
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                content()
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/**
 * 分组标题（A 场景与氛围 / B 本会话 / C 会话管理 …）。
 *
 * 透层墨阶（v11 合同 §2）：会话页有背景图时这一层是**半透明的**，中间调墨算不出稳定的对比度，
 * 所以组名与行名一样用 onSurface 全墨——层级由字号/字重承担，不再由色阶承担。
 */
@Composable
internal fun ChatPanelSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
internal fun ChatPanelDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/**
 * 页面行项：图标 + 名称 + 右侧"当前值"。
 * 右侧状态让页面兼作状态总览（背景/音乐/设定现在是什么，不用点进去才知道）；
 * [enabled] = false 时整行降透明且不可点（47c 口径：按能力隐藏改置灰，但保留位置与解释）。
 */
@Composable
internal fun ChatPanelRow(
    icon: ImageVector,
    label: String,
    status: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    /** 手机：行名下的副标题（来源/现状），桌面工具带不显示（见 [compact]） */
    subtitle: String? = null,
    /** 桌面常驻右栏的工具带：行高 40 的单行，不显示副标题 */
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val accent = if (danger) LocalWhalePalette.current.dangerText else MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (compact) 0.dp else 13.dp)
            .then(if (compact) Modifier.height(40.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            // 全墨：半透明底上 onSurfaceVariant 这类中间调算不出稳定对比度（v11 合同 §2）
            tint = accent.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = accent.copy(alpha = alpha))
            if (subtitle != null && !compact) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent.copy(alpha = alpha)
                )
            }
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = accent.copy(alpha = alpha)
            )
        }
    }
}

/**
 * 「聊天背景」弹窗的内容（用户 10-09 口径：**不做成行表式**）——语法与同页兄弟弹窗
 * `BgmCard.SessionBgmDialog`（本会话背景音乐）一致：标签 ＋ [WhaleChip] 选端、预览直接铺、
 * 滑条带一行值、动作是文字键。行表式（分组卡 ＋ 行标题/副说明/尾值）是给**整页**排的，
 * 搬进小弹窗会把一句话就能改完的事排成一张表。
 *
 * 文案即冒烟锚点（`chat-panel-page`／`chat-bg-remove`／`chat-bg-dim`／`chat-bg-follow`）：
 * 作用端／当前背景／选择本地背景图／复制到另一端／移除背景图／本会话设置的图——改文案要同步改场景。
 */
/**
 * 这一端的背景来源（会话覆盖优先；空串＝用户明确设成"不要"）。
 * 抽成公开函数是为了**列表页的导航行**与**弹窗的「当前背景」**用同一句话——
 * 两处各写一遍等于以后改一处漏一处。
 */
internal fun bgSourceLabel(convField: String?, cardField: String?): String = when {
    convField != null && convField.isNotBlank() -> "本会话设置的图"
    convField != null -> "本会话已设为无背景"
    cardField != null && cardField.isNotBlank() -> "继承自角色卡"
    cardField != null -> "角色卡设为无背景"
    else -> "未设置背景"
}

@Composable
internal fun ChatBackgroundSection(
    target: BgTarget,
    onPickTarget: (BgTarget) -> Unit,
    /** 会话里这一端自己的字段：null = 没设过（回退角色卡），"" = 明确不要 */
    convField: String?,
    /** 角色卡里这一端的字段：null = 没设过，"" = 明确不要 */
    cardField: String?,
    /** 另一端**已经是同一张图**：动作置灰并把原因写在键下（用户 10-08 反馈④"点了没反应"） */
    otherSame: Boolean = false,
    /** 预览与聊天区共用的暗化强度（0f~1f） */
    dim: Float,
    onDimChange: (Float) -> Unit,
    onDimCommit: () -> Unit,
    onCopyOtherEnd: () -> Unit,
    onAiBackground: () -> Unit,
    onLocalBackground: () -> Unit,
    /** 反馈 5：把本端这张图裁一下（复用角色编辑器的 CropDialog） */
    onEditBackground: () -> Unit,
    onRemoveBackground: () -> Unit,
    /** 内容两侧留白：独立页/自绘浮层宿主＝20dp；宿主自带内边距（如 `AlertDialog` 的 text 槽）时传 0.dp */
    horizontalPadding: Dp = 20.dp
) {
    // 取值语义与 ui/ChatBackgrounds.kt 的 effectiveBackground 完全一致（会话覆盖优先，"" 也是明确值）
    val effective = convField ?: cardField
    val hasImage = !effective.isNullOrBlank()
    val source = bgSourceLabel(convField, cardField)
    val dimValue = dim.coerceIn(0f, 1f)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 作用端：两档 chip 选一（与兄弟弹窗「本会话背景音乐」的"音源"同一语法）
        Text("作用端", style = MaterialTheme.typography.labelMedium, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BgTarget.entries.forEach { t ->
                WhaleChip(
                    selected = t == target,
                    onClick = { onPickTarget(t) },
                    label = { Text(t.label) }
                )
            }
        }
        Text("手机与桌面窗口形状不同，各存一份", style = MaterialTheme.typography.bodySmall, color = muted)
        HorizontalDivider()
        // 预览：图直接铺满内容宽；暗化实时叠在上面（拖滑条这里跟手变，不用关页对着聊天区猜效果）
        Text("当前背景", style = MaterialTheme.typography.labelMedium, color = muted)
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(WhaleShapes.r1),
            contentAlignment = Alignment.Center
        ) {
            if (hasImage) {
                AsyncImage(
                    model = imageModel(effective),
                    contentDescription = "背景预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                dimmedScrims(
                                    WhaleOverlays.sessionScrims,
                                    dimValue
                                )
                            )
                        )
                )
            } else {
                Text("无背景图", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Text(source, style = MaterialTheme.typography.bodySmall, color = muted)
        if (hasImage) {
            Text("预览按窗口裁切；下面可以调暗化强度", style = MaterialTheme.typography.bodySmall, color = muted)
            Text("暗化强度：${(dimValue * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = muted)
            Slider(
                value = dimValue,
                onValueChange = onDimChange,
                onValueChangeFinished = onDimCommit,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        HorizontalDivider()
        // 动作＝正文色文字行（用户 10-10 口径：这几个字段改黑色、行距收紧；10-10 续：字再小一档、
        // 间距再收）——与「本会话背景音乐」的选项行同一节奏：纯 Row ＋ clickable，不套 M3 按钮
        // （按钮 48dp 的最小高度会把行距撑到 54dp，且内容色默认是主题强调色）
        BgActionKey(Icons.Filled.AutoAwesome, "AI 生成背景", onClick = onAiBackground)
        BgActionKey(Icons.Filled.PhotoLibrary, "选择本地背景图", onClick = onLocalBackground)
        // 反馈 5：本端这张图要能裁剪——与角色编辑器的「编辑裁剪」共用 CropDialog 与分端比例
        BgActionKey(Icons.Filled.Crop, "编辑（裁剪）", enabled = hasImage, onClick = onEditBackground)
        // 两张图都在本机，复制只是改字段；没图可复制 / 另一端已是同一张 ⇒ 置灰并把原因写在键下
        // （用户 10-08 反馈④："复制到另一端实际不能用"——原来点了没反应也没提示）
        BgActionKey(
            Icons.Filled.SwapHoriz,
            "复制到另一端",
            enabled = hasImage && !otherSame,
            onClick = onCopyOtherEnd
        )
        if (otherSame) {
            Text("另一端已是同一张", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        // 危险动作保持 danger 色（用户 10-10 续：'这几个字段改黑色'不含它，移除仍用红字）
        BgActionKey(null, "移除背景图", enabled = hasImage, danger = true, onClick = onRemoveBackground)
    }
}

/**
 * 弹窗内的动作行：15dp 图标（可省）＋ 文案，行高＝一行 15sp ＋ 上下 4dp。
 * [danger]＝破坏性动作的红字（只此一处，别扩散）；禁用＝整行压到 0.38 并保留位置，
 * "为什么点不动"由调用方在行下写一句。
 */
@Composable
private fun BgActionKey(
    icon: ImageVector?,
    text: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(WhaleShapes.r1)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LocalWhaleType.current.strong, color = content)
    }
}

/**
 * 角色信息页内容（顶栏角色名旁的「‹」入口）：首行＝头像 + 名字 + 形态 chip + 编辑键
 * （用户 2026-10-03：编辑入口从页尾上移到名字行右侧），下面是文本设定总览——
 * 顶栏铅笔保留，别处不再重复挂同一入口。
 */
@Composable
internal fun CharacterInfoPanelContent(
    avatarUri: String?,
    name: String,
    formLabel: String,
    tagline: String,
    persona: String,
    greeting: String,
    hasBook: Boolean,
    onWorldBookHits: () -> Unit,
    onEditCharacter: () -> Unit
) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 全局头像（211）：没图＝名字第一个字，与宝库/聊天记录同一份组件。
        // 外层语义单独给一句「角色头像」：组件有图时只报角色名、无图时零描述，
        // 本页首行是「头像＋名字」两格，读屏与冒烟都需要能单独指认头像这一格。
        AvatarOrInitial(
            name = name,
            avatarUri = avatarUri,
            modifier = Modifier
                .size(52.dp)
                .semantics { contentDescription = "角色头像" },
            fontSize = 22.sp,
            shape = CircleShape
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(3.dp))
            Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    formLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        // 编辑键在名字行右侧（用户 2026-10-03）；页尾那行「编辑角色卡」同步撤掉
        IconButton(onClick = onEditCharacter, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑角色卡",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    InfoBlock("简介", tagline)
    InfoBlock("人设", persona)
    InfoBlock("开场白", greeting)
    if (hasBook) {
        ChatPanelRow(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            "世界书命中",
            status = "这一轮注入了什么",
            onClick = onWorldBookHits
        )
    }
}

@Composable
private fun InfoBlock(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        // 字段名蓝字（用户 2026-10-03：标签从灰字里出来、一眼分节；accentText 各皮肤随色且保对比度）
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalWhalePalette.current.accentText
        )
        Spacer(Modifier.size(2.dp))
        Text(
            value.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 桌面**常驻右栏**（第 194 轮）：角色卡抬头 ＋ 会话项。
 *
 * 它取代的是手机端那颗「更多」⋮ 整页——那一页在桌面端原本是**覆盖在聊天区上的整页**，
 * 一打开就看不见聊天（用户 2026-10-01 要求整合进侧边栏）。这里改成常驻在第三栏右侧，
 * 聊天区与它同时可见，所以桌面端顶栏的 ⋮ 可以撤掉；手机端保留（它仍是手机唯一入口）。
 *
 * 宽度 [DesktopSessionRailWidth]（与设计原型 `.dright` 的 300px 同值）——它只把会话项
 * 摆出来，不承载长文本。
 *
 * **收展按原型 `.dright`**（用户 2026-10-08）：宽度 300→0、随宽度一起淡出，200ms
 * `cubic-bezier(.2,0,0,1)`；**收起后不留任何残件**（既没有满高竖条，也没有窄把手）——
 * 回来的路是聊天列顶栏右上那颗面板键（`ChatTopBar` 的 `onToggleRail`，对应原型 `.dbar`
 * 的 `data-act="right"`），它不随本栏收展、任何时候都在，所以不存在"找不到回来的路"。
 *
 * @param content 会话项本体——由聊天页传入**与手机端整页同一份**的行
 *   （抄两遍等于以后修一处漏一处，见工单）
 */
@Composable
internal fun DesktopSessionRail(
    open: Boolean,
    avatarUri: String?,
    name: String,
    formLabel: String,
    onOpenCharacter: () -> Unit,
    onEditCharacter: () -> Unit,
    content: @Composable () -> Unit
) {
    // 栏底与二级页/编辑面**同一配方**（v11 合同 §1：面板＝栏底＝编辑面）：同一枚反算出的 α，
    // 不再各写一个常量（旧值 0.72 是"栏底透的是主题底色"年代的数）
    val railColor = MaterialTheme.colorScheme.background.copy(alpha = PanelVeilAlpha)
    // 原型 `.dright` 的 transition 参数：`--dur:200ms`、`--ease:cubic-bezier(.2,0,0,1)`，width 与 opacity 同参
    val railEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val railWidth by animateDpAsState(
        targetValue = if (open) DesktopSessionRailWidth else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = railEase),
        label = "railWidth"
    )
    val railAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = railEase),
        label = "railAlpha"
    )
    Surface(
        color = railColor,
        modifier = Modifier
            .width(railWidth)
            .fillMaxSize()
            .alpha(railAlpha)
            // 内层固定 300dp（下一行）＋ 这层裁剪：不给内层限宽的话，收展途中整栏文字会一路重排（抖）；
            // 裁剪同时保证 0 宽之后本栏不再接收指针事件。
            .clipToBounds()
    ) {
        Column(Modifier.width(DesktopSessionRailWidth).fillMaxHeight()) {
            // 抬头＝角色卡（用户 2026-10-01 要求右栏把角色信息一并放出来）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val avatar = imageModel(avatarUri)
                    if (avatar != null) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            name.take(1),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            // 桌面右栏保留「编辑」（用户 10-09 明确：只撤手机端那一行）——手机端点角色名就能查看+编辑，
            // 桌面端没有那条路（顶栏不显示角色名），所以这两颗键是它唯一的入口
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RailAction("查看角色卡", onOpenCharacter, Modifier.weight(1f))
                RailAction("编辑", onEditCharacter, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) { content() }
        }
    }
}

/** 右栏抬头那颗小实底键（查看角色卡／编辑）：与浮层里的作用范围两格同一套自绘样式 */
@Composable
private fun RailAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = WhaleShapes.r2,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp)
        )
    }
}
