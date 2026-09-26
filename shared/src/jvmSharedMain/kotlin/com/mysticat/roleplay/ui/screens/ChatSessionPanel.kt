package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.dimmedScrims

/**
 * 会话页的二级**整页**（用户拍板：不要拉取窗口/浮层，要独立页面）：
 * 同一份内容两端同用，铺满聊天页所在区域（手机＝整屏；桌面＝第三栏，左栏会话列表不受影响）。
 * 自带顶栏（返回 + 标题）与滚动内容，返回走调用方的关闭回调。
 * **返回键/Esc 由容器自己接**（[WhaleBackHandler]）——这样每个用它的整页都自动"返回关页"，
 * 不必在调用处各注册一次（漏一处就是"按返回直接退出聊天"）。
 */
@Composable
internal fun ChatOverlayPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    WhaleBackHandler(onBack = onBack)
    // ⚠ 整页不经过 Scaffold，必须自己避开系统栏：否则手机上顶栏画进状态栏触摸区，
    // 「返回」怎么点都没反应（实测：状态栏触摸区 132px，按钮 bounds 全在其中）。
    Surface(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
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

/** 分组标题（A 场景与氛围 / B 本会话 / C 会话管理 …） */
@Composable
internal fun ChatPanelSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp)
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
    onClick: () -> Unit
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val alpha = if (enabled) 1f else 0.38f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = (if (danger) accent else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = accent.copy(alpha = alpha), modifier = Modifier.weight(1f))
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

/**
 * 「聊天背景」区：分端 chips + 状态行（缩略图＋来源）+ 实时预览 + 暗化滑杆 +
 * 动作行（AI 生成 / 本地选择 / 移除）+ 一键复制到另一端。
 * 以前是抽屉里的内联展开行，现在是会话页的一节，常开不折叠。
 */
@Composable
internal fun ChatBackgroundSection(
    target: BgTarget,
    onPickTarget: (BgTarget) -> Unit,
    /** 会话里这一端自己的字段：null = 没设过（回退角色卡），"" = 明确不要 */
    convField: String?,
    /** 角色卡里这一端的字段：null = 没设过，"" = 明确不要 */
    cardField: String?,
    /** 预览与聊天区共用的暗化强度（0f~1f） */
    dim: Float,
    onDimChange: (Float) -> Unit,
    onDimCommit: () -> Unit,
    onCopyOtherEnd: () -> Unit,
    onAiBackground: () -> Unit,
    onLocalBackground: () -> Unit,
    onRemoveBackground: () -> Unit
) {
    // 取值语义与 ui/ChatBackgrounds.kt 的 effectiveBackground 完全一致（会话覆盖优先，"" 也是明确值）
    val effective = convField ?: cardField
    val hasImage = !effective.isNullOrBlank()
    val source = when {
        convField != null && convField.isNotBlank() -> "本会话设置的图"
        convField != null -> "本会话已设为无背景"
        cardField != null && cardField.isNotBlank() -> "继承自角色卡"
        cardField != null -> "角色卡设为无背景"
        else -> "未设置背景"
    }
    val other = BgTarget.entries.first { it != target }
    Column(Modifier.padding(horizontal = 20.dp)) {
        // 分端：选"改哪一端"；切端时下面的状态/预览立刻跟着换
        Row(verticalAlignment = Alignment.CenterVertically) {
            BgTarget.entries.forEach { t ->
                WhaleChip(
                    selected = target == t,
                    onClick = { onPickTarget(t) },
                    label = { Text(t.label) }
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.size(8.dp))
        // 状态行：这一端现在是什么图、图是谁设的——以前是固定文案，看不出状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            BgThumbnail(effective, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    source,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    if (hasImage) "${target.label} · 已设" else "${target.label} · 无背景",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (hasImage) {
            Spacer(Modifier.size(10.dp))
            // 实时预览：暗化滑杆拖动时这里跟手变，不用关页面对着聊天区猜效果
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
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
                                    listOf(Color(0xCC000000), Color(0x73000000), Color(0x00000000)),
                                    dim
                                )
                            )
                        )
                )
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "暗化强度",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = dim.coerceIn(0f, 1f),
                    onValueChange = onDimChange,
                    onValueChangeFinished = onDimCommit,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Text(
                    "${(dim.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    ChatPanelRow(Icons.Filled.AutoAwesome, "AI 生成背景", onClick = onAiBackground)
    ChatPanelRow(Icons.Filled.PhotoLibrary, "选择本地背景图", onClick = onLocalBackground)
    // 两张图都在本机，复制只是改字段；没图可复制时置灰
    ChatPanelRow(
        Icons.Filled.SwapHoriz,
        "复制到另一端",
        status = "同步到${other.label}",
        enabled = hasImage,
        onClick = onCopyOtherEnd
    )
    ChatPanelRow(Icons.Filled.Wallpaper, "移除背景图", danger = true, enabled = hasImage, onClick = onRemoveBackground)
}

/** 缩略图：有图放图（圆角小方），没图给占位底 + 人形/壁纸图标 */
@Composable
private fun BgThumbnail(uri: String?, size: Dp) {
    if (uri.isNullOrBlank()) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size / 2)
                )
            }
        }
    } else {
        AsyncImage(
            model = imageModel(uri),
            contentDescription = "当前背景缩略图",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
        )
    }
}

/**
 * 角色信息页内容（顶栏角色名旁的「‹」入口）：首行＝头像 + 名字 + 形态 chip（用户 2026-09-26 拍板），
 * 下面是文本设定总览，「编辑角色卡」在这里归口——顶栏铅笔保留，别处不再重复挂同一入口。
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
        if (avatarUri.isNullOrBlank()) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "角色头像",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            AsyncImage(
                model = imageModel(avatarUri),
                contentDescription = "角色头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape)
            )
        }
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
    ChatPanelRow(Icons.Filled.Edit, "编辑角色卡", onClick = onEditCharacter)
}

@Composable
private fun InfoBlock(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(2.dp))
        Text(
            value.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
