package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import kotlinx.coroutines.launch

/**
 * 宝库（角色卡 / 世界书）的两种查看方式。
 *
 * **拍板来源**：交接书 §4.2「角色卡段＝段内切换器切名册 / 柜格——**保留**（用户原话"不用去掉，可以支持切换查看方式"）；
 * 默认**手机名册 / 桌面柜格**」。世界书段**没有**这个切换器：柜格与名册同屏（见 [BookSpineRail]）。
 */
enum class TreasureView(val label: String) {
    ROSTER("名册"),
    CABINET("柜格")
}

/**
 * 段内视图切换器（原型 `.vsw`）：两颗小 chip，靠右（外层用 `Modifier.weight(1f)` 的兄弟占位把它推到右缘）。
 * 造型照抄原型 `.chip`（`font-size:11.5px; padding:4px 11px`），色走 L1 令牌。
 */
@Composable
internal fun ViewSwitchChips(
    selected: TreasureView,
    onSelect: (TreasureView) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TreasureView.entries.forEach { v ->
            val on = v == selected
            Text(
                text = v.label,
                style = type.caption,
                color = if (on) pal.onAccent else pal.ink2,
                modifier = Modifier
                    .clip(WhaleShapes.r1)
                    .background(if (on) pal.accent else Color.Transparent)
                    .then(if (on) Modifier else Modifier.border(1.dp, pal.hairline, WhaleShapes.r1))
                    .clickable { onSelect(v) }
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * 名册的一行（原型 `.rrow`）：62dp 圆头像 ＋ 名字/形态徽标 ＋ 分类小字 ＋ 一句话简介 ＋ 开场白两行 ＋ 行尾铅笔。
 *
 * 通栏无卡（发丝分隔线当行界，`.rrow` 的 `border-bottom`），这是 205~207 聊天记录页同一条纪律。
 * 长按＝删除、点头像＝看该角色的全部会话、行尾铅笔＝编辑，三条入口与现状一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RosterRow(
    avatar: @Composable () -> Unit,
    name: String,
    meta: String,
    tagline: String,
    greeting: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    greetingColor: Color? = null,
    greetingMaxLines: Int = 2,
    onAvatarClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    // 行尾自定义动作（原型 `.rrow` 的行尾按钮位）。`onEdit` 是"铅笔＝编辑"这一种固定语义，
    // 世界书名册要的是"垃圾桶＝删除"，所以这里出槽位而不是再加一个 onDelete 开关。
    trailing: (@Composable () -> Unit)? = null
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier
            .fillMaxWidth()
            .background(if (hovered) pal.surface2 else pal.paper)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onOpen,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onOpen
                    )
                }
            )
            .padding(start = WhaleSpacing.lg, end = WhaleSpacing.lg, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(62.dp)
                .then(if (onAvatarClick != null) Modifier.clickable(onClick = onAvatarClick) else Modifier)
        ) { avatar() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = type.strong,
                    fontWeight = FontWeight.Bold,
                    color = pal.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        badge,
                        style = type.tiny,
                        letterSpacing = 0.06.sp,
                        // 实心小牌（用户 2026-10-03：「名册的类型没有填充」）＝与柜格卡同款：
                        // 描边款在小字号下读起来像没做完的占位，accent 底才算一枚看得见的标签
                        color = pal.onAccent,
                        modifier = Modifier
                            .clip(WhaleShapes.r1)
                            .background(pal.accent)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = type.tiny,
                    letterSpacing = 0.06.sp,
                    color = pal.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            if (tagline.isNotBlank()) {
                Text(
                    tagline,
                    style = type.small,
                    color = pal.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
            if (greeting.isNotBlank()) {
                Text(
                    greeting,
                    style = type.small,
                    lineHeight = 19.sp,
                    color = greetingColor ?: pal.accent,
                    maxLines = greetingMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        if (onEdit != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(WhaleShapes.r1)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = pal.ink2,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/**
 * 没有图时的占位字：**名字的第一个字**。
 *
 * 原来各处各写一份 `name.take(2)`，44×56 的小书封槽里两个字挤成一团、看着像残字
 * （用户 2026-10-02：「没有头像的就用第一个字」）。一字占位在三档槽宽下都稳，
 * 所以这里出**一份**，调用方只管给名字。
 */
@Composable
internal fun InitialGlyph(
    name: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = name.trim().take(1).ifBlank { "未" },
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier
    )
}

/**
 * 头像槽：有图走图，**没图就走名字的第一个字**（用户 2026-10-02）。
 *
 * 原来 `AsyncImage(model = imageModel(null))` 只画出一块 `soft` 底色，一排卡里哪张是谁
 * 全靠读下面的名字，扫一眼认不出人——而宝库正是用来"一眼认人"的地方。
 * 2026-10-03 起这也是**全局头像**：聊天记录、角色信息、宝库弹窗同用这一份——
 * 一个角色在任何页面都是同一张脸（同一个字＋同一块 soft 底）。
 * 形状与描边收进这里（`shape`/`borderColor`）：描边必须画在底色**之后**，
 * 挂在调用方 modifier 里会被同尺寸的底色盖住。
 */
@Composable
internal fun AvatarOrInitial(
    name: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    shape: Shape? = null,
    borderColor: Color? = null
) {
    val pal = LocalWhalePalette.current
    Box(
        modifier
            .then(shape?.let { Modifier.clip(it) } ?: Modifier)
            .background(pal.soft)
            .then(
                borderColor?.let { c -> Modifier.border(1.dp, c, shape ?: RectangleShape) }
                    ?: Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri.isNullOrBlank()) {
            InitialGlyph(name, fontSize, pal.accent)
        } else {
            AsyncImage(
                model = imageModel(avatarUri),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 柜格的一张卡：**封面里**写角色名与类型，**卡下**只留两行简介。
 *
 * 版式改了（用户 2026-10-02）：原型 `.cabc` 是「封面占位字 → 徽标 → 名字 → 简介」四层竖排，
 * 名字在封面**外面**重复出现一次、类型是描边小字（没有填充，看着像没做完）。现在把
 * 名字与类型收进封面底部的压暗条里，类型改**实心 accent 底 ＋ onAccent 字**（填色＝看得见的标签），
 * 卡下只留简介——一个格子只讲两件事：这是谁、这是什么。
 *
 * 封面字底那道压暗渐变只为让白字读得清（与发现页封面同一处理），不是装饰。
 * 简介槽固定两行高（`min-height:2.9em`），两列卡底边才对得齐——原型 §12 第 3 条踩过的坑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CabinetCard(
    cover: @Composable () -> Unit,
    name: String,
    tagline: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    typeLabel: String? = null,
    onLongPress: (() -> Unit)? = null
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(
        modifier
            .fillMaxWidth()
            .clip(WhaleShapes.r1)
            .then(
                if (onLongPress != null) {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onOpen,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onOpen
                    )
                }
            ),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(WhaleShapes.r1)
                .background(pal.soft)
                .then(
                    if (hovered) Modifier.border(2.dp, pal.accent, WhaleShapes.r1) else Modifier
                )
        ) {
            cover()
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color(0x9E0F101C),
                            1f to Color(0x9E0F101C)
                        )
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 26.dp, bottom = 8.dp)
            ) {
                Text(
                    name,
                    style = type.small,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5F6F4),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (typeLabel != null) {
                    Text(
                        typeLabel,
                        style = type.tiny,
                        letterSpacing = 0.06.sp,
                        color = pal.onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 5.dp)
                            // 实心小牌＝"这是一个有含义的标签"；描边款在小尺寸下会读成没做完的占位
                            .clip(WhaleShapes.r1)
                            .background(pal.accent)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Text(
            tagline,
            style = type.caption,
            color = pal.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // 两行槽固定高 ⇒ 两列卡底边对齐（原型 §12 第 3 条）；两行 ×17sp 行高
            modifier = Modifier
                .padding(top = 6.dp)
                .height(34.dp)
        )
    }
}

/**
 * 世界书的书脊（原型 `.spin`）：56×190，顶上一道 3dp 主色，书名竖排，底下一条数。
 *
 * **世界书没有封面图**，所以不用柜格封面、也不画插画——书脊就是它自己的封面（交接书 §4.2 明令）。
 *
 * ⚠ 顶那道线**必须钉死在顶上**：原来整列用 `Arrangement.Center` 垂直居中，书名越长列越高、
 * 线就跟着往上浮，一排书脊的线于是高低错落（用户 2026-10-02 截图：短书名那道线明显低一截）。
 * 现在改成顶对齐、中段 `weight(1f)` 居中、条数落底——线齐、条数也与原型 `.spin b` 的
 * `position:absolute; bottom` 一致。
 */
@Composable
internal fun BookSpine(
    title: String,
    count: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(
        modifier
            .width(SPINE_W)
            .height(190.dp)
            .clip(WhaleShapes.r1)
            .background(pal.surface)
            .border(
                width = if (dragging) 2.dp else 1.dp,
                color = if (hovered || dragging) pal.accent else pal.hairline,
                shape = WhaleShapes.r1
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                // 顶上那道主色：外层已被 r1 裁过，这里只把自己下角收一点，避免被父级圆角削成透镜形
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(pal.accent)
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
                Text(
                    // 竖排＝把槽压到一字宽让中文逐字换行（原型 `writing-mode:vertical-rl` 的 Compose 近似）
                    text = title.take(8).ifBlank { "未" },
                    style = type.caption,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.sp,
                    color = pal.ink,
                    lineHeight = 15.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Clip
                )
            }
        }
        Text(
            "$count",
            style = type.micro,
            color = pal.ink3,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/** 柜格横向轨两侧的翻页箭头（原型 `.cabn`，32×52）；`forward=true` 是向右那一颗 */
@Composable
internal fun CabinetNavButton(
    forward: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = LocalWhalePalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .size(width = 32.dp, height = 52.dp)
            .clip(WhaleShapes.r1)
            .background(if (hovered) pal.soft else pal.surface)
            .border(1.dp, if (hovered) pal.accent else pal.hairline, WhaleShapes.r1)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (forward) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
            contentDescription = if (forward) "下一格" else "上一格",
            tint = if (hovered) pal.accent else pal.ink2,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 书脊宽（原型 `.spin` 的 `width:56px`）；书脊之间恒定留 10dp 缝 */
private val SPINE_W = 56.dp

/** 一条轨里最多露几颗（用户 2026-10-02：「最多显示五个吧，多出来就滚动」） */
private const val MAX_SPINES = 5

/** 书脊的固定节距＝宽＋缝。拖动换位就按它算"拖过几格" */
private val SpinePitch = SPINE_W + 10.dp

/**
 * 书脊横轨（原型 `.cabwrap` ＋ `.cabtrack`）：左右各一颗翻页箭头 ＋ 一条 scroll-snap 的书脊轨。
 *
 * **世界书段没有视图切换器**——柜格与名册同屏，这是用户 2026-10-02 澄清的那句"并存"（交接书 §4.2）。
 * 箭头只在桌面出现（原型 `isD` 条件）；手机只有横滑，少两个可点的东西更干净。
 * 箭头的语义＝**翻页浏览**（与全仓横排 `ArrowScrollRow` 同一套：溢出才显形、点一下走一屏），
 * **不是排序**——桌面不做拖动排序（用户 2026-10-03：书脊上的左右箭头是翻页用的，别拿它改顺序）。
 *
 * **长按拖动排序＝手机专属**（用户 2026-10-02：「手机端要可以拖动」）：按书脊 180ms 起拖，跟着手指横移，
 * 每越过一格（[SpinePitch]）就就地换一次显示顺序，松手才把最终排列交给 [onOrderChange] 落盘。
 * 拖动期间那一颗抬手抬一层并描主色边——否则用户不知道自己拖的是谁、也不知道松在哪。
 * 用 `detectDragGesturesAfterLongPress` 而非 `detectDragGestures`：它**先让横滑照常滚**，
 * 静止按住到时才接管手势，所以"滑着看"与"拖着排"不会互相抢。
 */
@Composable
internal fun BookSpineRail(
    books: List<Pair<String, Int>>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    withArrows: Boolean = false,
    reorderable: Boolean = false,
    onOrderChange: (List<Int>) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 翻页步长＝一整屏露出的书脊数（与分类标签行的左右箭头同语义：点一下走一大截，两三下到头），
    // 不是一次挪一颗——书一多那样点起来太熬人
    fun nudge(dir: Int) {
        val target = (listState.firstVisibleItemIndex + dir * MAX_SPINES)
            .coerceIn(0, (books.size - 1).coerceAtLeast(0))
        scope.launch { listState.animateScrollToItem(target) }
    }
    // 一次最多露 5 颗，多出来的横滑（用户 2026-10-02：「最多显示五个吧，多出来就滚动」）。
    // 轨宽按"要露的那几颗"算死：≤5 本时整条轨比容器窄，于是它连同两颗翻页箭头**居中**，
    // 不再像从前那样一路贴着左缘（用户原话「书脊太靠左了」）。
    val visibleSpines = minOf(books.size, MAX_SPINES)
    val railWidth = remember(visibleSpines) {
        SPINE_W * visibleSpines + 10.dp * (visibleSpines - 1).coerceAtLeast(0)
    }

    // 拖动期间的显示顺序：下标数组。拖动中只动它，落盘交给 onOrderChange（避免每换一格就写一次盘）
    val order = remember { mutableStateListOf<Int>() }
    var orderSig by remember { mutableStateOf("") }
    val sig = remember(books) { books.joinToString("") { it.first } }
    if (sig != orderSig) {
        orderSig = sig
        // 书增删后下标会平移：先按旧顺序认名，认不出的（新书）追加到末尾，别把整排顺序打乱
        val prev = order.mapNotNull { i -> books.getOrNull(i)?.first }
        order.clear()
        order += books.indices
        if (prev.size == books.size && prev.toSet() == books.map { it.first }.toSet()) {
            order.clear()
            order += books.indices.sortedBy { books.indexOfFirst { b -> b.first == prev[it] } }
        }
    }
    var dragSlot by remember { mutableStateOf(-1) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    // 溢出＝**轨的实际宽度**超过容器宽（与 ArrowScrollRow 同口径），不是"书超过 5 本"：
    // 第二栏收窄到 253dp 时 5 本（轨 320dp）也已经溢出，第 5 颗被窗缘裁掉却无箭头可点——
    // 旧判据漏掉的正是这种"本数不多、栏不够宽"的局面（用户 2026-10-03 报的没箭头就是它）。
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val avail = maxWidth
        val overflow = railWidth > avail
        // 箭头各占 32dp ＋ 8dp 缝：溢出时轨让出这段宽，才不会右箭头一出现书脊反而更挤
        val arrowRoom = if (withArrows) 40.dp * 2 else 0.dp
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            // 整组（箭头＋书脊轨）居中：书少装得下时它们是一排小书封，不该贴着左缘站
            horizontalArrangement = if (overflow) Arrangement.Start else Arrangement.Center
        ) {
            if (overflow && withArrows) {
                CabinetNavButton(forward = false, onClick = { nudge(-1) })
                Spacer(Modifier.width(8.dp))
            }
            LazyRow(
                state = listState,
                modifier = Modifier
                    .width(if (overflow) avail - arrowRoom else railWidth)
                    .then(
                    if (!reorderable) Modifier else Modifier.pointerInput(order.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    pos.x.toInt() >= it.offset && pos.x.toInt() < it.offset + it.size
                                }?.let { dragSlot = order.getOrNull(it.index) ?: it.index }
                            },
                            onDragEnd = {
                                if (dragSlot >= 0) onOrderChange(order.toList())
                                dragSlot = -1
                                dragDx = 0f
                            },
                            onDragCancel = { dragSlot = -1; dragDx = 0f },
                            // dragAmount 是整段位移（Offset），取 .x 才是横向那一维
                            onDrag = { change, drag ->
                                change.consume()
                                if (dragSlot < 0) return@detectDragGesturesAfterLongPress
                                dragDx = dragDx + drag.x
                                val pitchPx = with(density) { SpinePitch.toPx() }
                                val shift = Math.round(dragDx / pitchPx).toInt()
                                if (shift != 0) {
                                    val to = (dragSlot + shift).coerceIn(0, order.lastIndex)
                                    order.add(to, order.removeAt(dragSlot))
                                    dragDx = dragDx - (to - dragSlot) * pitchPx
                                    dragSlot = to
                                }
                            }
                        )
                    }
                ),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(order.size, key = { order[it] }) { slot ->
                val idx = order[slot]
                val book = books.getOrNull(idx)
                if (book != null) {
                    val dragging = slot == dragSlot
                    BookSpine(
                        title = book.first,
                        count = book.second,
                        onOpen = { onOpen(idx) },
                        dragging = dragging,
                        modifier = if (dragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer { translationX = dragDx }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
        if (overflow && withArrows) {
            Spacer(Modifier.width(8.dp))
            CabinetNavButton(forward = true, onClick = { nudge(1) })
        }
        }
    }
}

/** 宝库里的小节抬头（原型 `.gh`）：标题 ＋ 计数 ＋ 延伸的细线 */
@Composable
internal fun RosterSectionHeader(
    label: String,
    count: Int,
    countLabel: String,
    modifier: Modifier = Modifier
) {
    val pal = LocalWhalePalette.current
val type = LocalWhaleType.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = WhaleSpacing.lg, end = WhaleSpacing.lg, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = type.label, fontWeight = FontWeight.Bold, color = pal.ink)
        Text(
            countLabel.replace("{n}", count.toString()),
            style = type.tiny,
            letterSpacing = 0.1.sp,
            color = pal.ink3,
            modifier = Modifier.padding(start = WhaleSpacing.sm)
        )
        Box(
            Modifier
                .weight(1f)
                .padding(start = WhaleSpacing.sm)
                .height(1.dp)
                .background(pal.hairline)
        )
    }
}