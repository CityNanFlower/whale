package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.Material
import com.mysticat.roleplay.data.MaterialCategory
import com.mysticat.roleplay.data.MaterialKind
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.DesktopCardContextMenu
import com.mysticat.roleplay.ui.DesktopMenuRow
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.desktopSecondaryClick
import com.mysticat.roleplay.ui.components.SwipeRevealDelete
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.ArrowScrollRow
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.Locale

/**
 * 素材库：AI 生成的图／文集中存放（存储见 `Repository` 的素材库段、模型见 `Material`）。
 *
 * 为什么要有这一页：生成品此前是"用完就散"的——图看两眼就没了、文字复制走以后在应用里再也找不回，
 * 想再拿同一张图当头像只能重新生成一次（再花钱）。这里给它一个固定落点。
 *
 * 只有一个筛选维度：**项目分类**（形象 / 背景 / 角色卡 / 世界书 / 故事），单选，跟着"它在哪个创作项目里生成"走。
 * 此前还有一层"自由标签"，已按用户 2026-09-27 口径**整个删掉**——那一层是自动打的（"灵感创作""世界书"），
 * 与分类同名不同源，界面上就冒出两颗"世界书"和"灵感创作"这种根本不是分类的胶囊。**没有音频**——
 * 音频各有各的落点（音乐库 / TTS），收进来只是噪音（用户 2026-09-27 口径）。
 *
 * 一页两用（同一个 composable，不加第二份）：
 * - **管理**（默认）：筛选、看内容、重命名、改分类、删除；
 * - **选择**（[onPick] 非 null，"从素材库选择"那类入口）：点一条就回调，点完自己关掉，管理动作一律不出现
 *   （选择场景下误删素材是真事故，藏起来比"弹确认框"更稳）。
 *
 * [pickKind] 在选择模式下把可选范围收到单一形态（挑图当背景＝只看图片）：那一层筛选**不画胶囊**
 * （用户正卡在"挑一张图"，给他"图片/文字"的开关没有意义），只在数据上生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialsScreen(
    onBack: () -> Unit,
    /** 桌面三栏：内嵌进第三栏（入口＝第二栏「素材库」分组），不套 Scaffold/顶栏 */
    embedded: Boolean = false,
    pickKind: MaterialKind? = null,
    onPick: ((Material) -> Unit)? = null
) {
    WhaleBackHandler { onBack() }

    // 库改动后靠 rev 重新读（同 BgmScreen 的口径：读一次给下面共用，别让每行各自读盘）
    var rev by remember { mutableStateOf(0) }
    val all = remember(rev) { Repository.listMaterials() }

    var categoryFilter by remember { mutableStateOf<MaterialCategory?>(null) }
    var detail by remember { mutableStateOf<Material?>(null) }
    // 横滑删除与一键清空都要"落到哪一条/哪一类"上才能执行：这里只存 id / 布尔，
    // 真删在弹窗的确认键上点（破坏性动作一律二次确认，与聊天记录页同一口径）。
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    // 胶囊**常驻**（用户 2026-09-27 口径）：空库也把"全部 + 五类"画出来，让用户一眼看到素材库是按什么分的，
    // 而不是对着一个空白页猜。此前是"哪类有素材才画哪颗"，空库一屏胶囊都没有（连"全部"也没有）。
    val shown = all.filter { m ->
        (pickKind == null || m.kindValue == pickKind) &&
            (categoryFilter == null || m.categoryValue == categoryFilter)
    }

    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            val subtitle = if (onPick != null) "点一条即选中" else "共 ${all.size} 条素材"
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (all.isEmpty()) {
                        // 空库时两用（管理/选择）要分开说：选择场景下用户正卡在"我要挑一张"，
                        // 给他一段"素材库是什么"的介绍没有用，得告诉他下一步在哪儿
                        if (onPick != null) {
                            "素材库里还没有图。灵感创作里生成的形象会自动存进来，回头再来这里取。"
                        } else {
                            "这里存放 AI 生成的东西：灵感创作里生成的图片与文字会自动存进来（不必特意保存，生成过就在）。" +
                                "挑图当头像/背景图时也能从这里取，存下来的一份可以反复用，按下面的分类找回去。"
                        }
                    } else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 「清空当前这一类」（用户 10-04）：清空范围**跟着上面那颗胶囊走**——
                // 停在「全部」就清整库，停在某一类就只清那一类，其余分类不受影响。
                // 选择模式（[onPick] 非 null）不给这个动作：那一路是"挑一张"，摆个清空按钮在边上
                // 就是误触的入口。同理删除按钮在选择模式下也一律不画（见列表行）。
                if (onPick == null && shown.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { confirmClear = true }) {
                        Text(
                            if (categoryFilter == null) "清空全部"
                            else "清空「${categoryFilter!!.label}」",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            // 胶囊筛选行：**只有一个维度**（项目分类，用户 2026-09-27 口径），且常驻——空库也画。
            ArrowScrollRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) {
                WhaleChip(
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null },
                    label = { Text("全部") }
                )
                MaterialCategory.entries.forEach { c ->
                    WhaleChip(
                        selected = categoryFilter == c,
                        onClick = { categoryFilter = if (categoryFilter == c) null else c },
                        label = { Text(c.label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "素材库还是空的"
                        else "没有符合当前筛选的素材（点上面的胶囊可以清掉筛选）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shown, key = { it.id }) { m ->
                        val open: () -> Unit = {
                            val pick = onPick
                            if (pick != null) {
                                pick(m); onBack()
                            } else detail = m
                        }
                        // 选择模式（[onPick] 非 null）**不套横滑删除**：那一路是"挑一张图当头像/背景"，
                        // 摆一个滑动即露的删除按钮在每条下面就是误触入口——挑图时手一滑就可能删掉素材。
                        // （素材丢了没法再生找回，是这一页最贵的失误；宁可不给这个动作。）
                        // 桌面（用户 10-04）不套横滑：鼠标拖动露删除不顺手，改成行尾一颗明摆的
                        // 垃圾桶，点它弹确认框——落点与横滑同一处（[pendingDelete]），口径不变。
                        // 右击行同样弹删除菜单（222 交互语法：右击＝破坏性动作；用户 10-04 追加），
                        // 与垃圾桶进同一个确认框。选择模式仍然不给任何删除入口。
                        if (onPick != null) {
                            MaterialRow(m = m, onClick = open)
                        } else if (isDesktopLayout) {
                            var menu by remember(m.id) { mutableStateOf(false) }
                            Box(Modifier.desktopSecondaryClick { menu = true }) {
                                MaterialRow(m = m, onClick = open, onDelete = { pendingDelete = m.id })
                                if (menu) {
                                    DesktopCardContextMenu(onDismiss = { menu = false }) {
                                        DesktopMenuRow(
                                            label = "删除素材…",
                                            danger = true,
                                            onClick = { menu = false; pendingDelete = m.id }
                                        )
                                    }
                                }
                            }
                        } else {
                            var revealed by remember(m.id) { mutableStateOf(false) }
                            SwipeRevealDelete(
                                revealed = revealed,
                                onRevealedChange = { revealed = it },
                                onArmedDelete = { revealed = false; pendingDelete = m.id },
                                deleteContentDescription = "删除素材",
                                // 与 [MaterialRow] 同一个形状常量：红带铺满整行，两层不同形就会从圆角缺口漏红
                                shape = MATERIAL_ROW_SHAPE
                            ) {
                                MaterialRow(m = m, onClick = { if (revealed) revealed = false else open() })
                            }
                        }
                    }
                }
            }
        }
    }

    val title = if (onPick != null) "从素材库选择" else "素材库"
    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            EmbeddedPageHeader(title, onBack)
            Box(Modifier.weight(1f)) { body() }
        }
    } else {
        Scaffold(
            topBar = {
                WhalePageTopBar(
                    title = title,
                    navigationIcon = { BackArrowButton(onBack) }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) { body() }
        }
    }

    detail?.let { m ->
        MaterialDetailDialog(
            m = m,
            onDismiss = { detail = null },
            onSaved = { detail = null; rev++ },
            onDeleted = {
                Repository.deleteMaterial(m.id)
                detail = null; rev++
                showToast("已从素材库删除")
            }
        )
    }

    // 横滑删除的落点：与详情弹窗里那颗垃圾桶共用一句人话口径（都不进回收站、不能找回）。
    pendingDelete?.let { id ->
        val target = all.firstOrNull { it.id == id }
        WhaleConfirmSheet(
            title = "删除这条素材？",
            body = if (target != null)
                "「${target.title}」会从素材库里消失，文件也一并删掉（不进回收站，不能找回）。"
            else
                "这条素材会从素材库里消失（不进回收站，不能找回）。",
            confirmLabel = "删除",
            onConfirm = {
                Repository.deleteMaterial(id)
                pendingDelete = null; rev++
                showToast("已从素材库删除")
            },
            onDismiss = { pendingDelete = null }
        )
    }

    // 「清空」：范围＝当前胶囊选中的那一类（"全部"＝整库）。文案必须写清**会失去什么**与
    // **不影响什么**——这是整页最不可逆的动作，只写"确定要清空吗"等于让人盲点。
    if (confirmClear) {
        val target = categoryFilter
        val count = shown.size
        val scope = if (target == null) "全部素材" else "「${target.label}」这一类"
        val tail = if (target == null) "整库清空后素材库归零。"
        else "其余 ${all.count { it.categoryValue != target }} 条其它分类的素材不受影响。"
        WhaleConfirmSheet(
            title = "清空$scope？",
            body = "当前这一类下有 $count 条素材，清空后会从素材库里消失，文件也一并删掉" +
                "（不进回收站，不能找回，也没有备份）。$tail",
            confirmLabel = "清空",
            onConfirm = {
                val n = Repository.clearMaterialsByCategory(target)
                confirmClear = false; rev++
                showToast("已清空 $n 条素材")
            },
            onDismiss = { confirmClear = false }
        )
    }
}

/**
 * 一条素材的形状。**必须与横滑删除那层的 [shape] 同值**（用户 10-04 报「透出背后的红了」）：
 * 删除红带用 `matchParentSize()` 铺满整行，前景行 clip 圆角而底层不 clip 时，
 * 四个圆角缺口会直接露出红带，每行看着像挂了四颗红三角。
 */
private val MATERIAL_ROW_SHAPE = RoundedCornerShape(12.dp)

/**
 * 一条素材：左边缩略图/形态块，右边标题、分类与标签。
 * [onDelete] 非 null 时行尾画一颗垃圾桶（**只有桌面传**：删除改明摆按钮，见列表分支的注释；
 * 手机走横滑不传，行尾不多一颗常驻的破坏性按钮）。contentDescription 用「删除素材」
 * 而不是「删除」——与确认框按钮文案错开，冒烟断言才不会命中弹窗文字把图标问题放过（第 7 轮的坑）。
 */
@Composable
private fun MaterialRow(m: Material, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MATERIAL_ROW_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (m.kindValue) {
            MaterialKind.IMAGE -> {
                val f = Repository.materialFileFor(m.id)
                if (f == null) TypeBadge("图") else AsyncImage(
                    model = imageModel(f.absolutePath),
                    contentDescription = m.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            MaterialKind.TEXT -> TypeBadge("文")
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = when (m.kindValue) {
                MaterialKind.TEXT -> m.previewText(46)
                else -> listOf(m.categoryValue?.label ?: "未分类", m.origin, shortDate(m.addedAt))
                    .filter { it.isNotBlank() }.joinToString(" · ")
            }
            if (line.isNotBlank()) {
                Text(
                    line, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除素材",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(text: String) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** 详情：图片先预览；重命名 / 改分类 / 删除；「拿走」动作按分类给（形象/背景→相册、角色卡→新卡、世界书→新书） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialDetailDialog(
    m: Material,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    var title by remember(m.id) { mutableStateOf(m.title) }
    var category by remember(m.id) { mutableStateOf(m.categoryValue) }
    var body by remember(m.id) { mutableStateOf(m.text) }
    var confirmDelete by remember(m.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 「拿走」动作**按分类区分**（用户 2026-09-30 二次口径：形象→存至相册、角色卡→存为新角色卡、
    // 世界书→存为新世界书，别把所有动作都堆给每条素材）。分类没认出来的老条目按形态兜底
    // （图→相册、文→世界书）；故事类暂无拿走动作（故事落位未建）。分类胶囊是活的：
    // 在弹窗里改分类，底部那颗键的标签跟着换。
    //
    // ⚠ **整个弹窗只有一颗动作键**（用户 10-04：正文里那颗「存到相册／存为角色卡」与底部「保存」
    // 并排，看着像重复，两颗都在「保存」）。点它＝先落盘这条素材的改动（重命名/分类/正文）、
    // 再做「拿走」——两者本来就是同一次提交，不该让用户点两下。
    val showGallery = when (category) {
        MaterialCategory.AVATAR, MaterialCategory.BACKGROUND -> m.kindValue == MaterialKind.IMAGE
        null -> m.kindValue == MaterialKind.IMAGE
        else -> false
    }
    val showCard = category == MaterialCategory.CARD
    val showBook = when (category) {
        MaterialCategory.WORLDBOOK -> m.kindValue == MaterialKind.TEXT
        null -> m.kindValue == MaterialKind.TEXT
        else -> false
    }

    if (confirmDelete) {
        WhaleConfirmSheet(
            title = "删除这条素材？",
            body = "「${m.title}」会从素材库里消失，文件也一并删掉（不进回收站，不能找回）。",
            confirmLabel = "删除",
            onConfirm = onDeleted,
            onDismiss = { confirmDelete = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // 标题就是素材名（用户 2026-09-30：点开应当有名称）——下面的「名称」框负责改它
        title = { Text(m.title.ifBlank { "未命名素材" }) },
        text = {
            Column {
                if (m.kindValue == MaterialKind.IMAGE) {
                    // 图片素材点开先看图（用户 2026-09-30：能预览图片）；文件丢了就只剩字段，不占位
                    val f = Repository.materialFileFor(m.id)
                    if (f != null) {
                        AsyncImage(
                            model = imageModel(f.absolutePath),
                            contentDescription = m.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (m.kindValue == MaterialKind.TEXT) {
                    // 正文**可看全文也可编辑**（用户 09-30：素材库不能编辑/查看具体字段）——
                    // 原来只有 300 字只读预览；编辑随下面的「保存」一起落盘
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("正文（可直接修改）") },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("分类（这条是哪一类创作项目的产出）", style = MaterialTheme.typography.labelMedium)
                // 五颗换行排（[FlowRow]）而不是横滑（用户 2026-09-27 口径的收尾）：
                // 五颗横排**刚好差十几像素**排不下，第 5 颗「故事」被裁在框外——
                // 不滑一下根本不知道有这一类（本机实测：第 146 轮量到 世界书 右边缘 893、对话框右边界 897）。
                // 筛选行可以不滑，这里藏着等于"没有"，所以换行。
                FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MaterialCategory.entries.forEach { c ->
                        WhaleChip(
                            selected = category == c,
                            // 再点一次＝取消（老条目推断出来的分类认错了也能清掉）
                            onClick = { category = if (category == c) null else c },
                            label = { Text(c.label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        },
        confirmButton = {
            // 落盘 + 拿走（同一次提交，见上面那段注释）；标签跟着分类走，用户点之前就知道会发生什么
            TextButton(onClick = {
                Repository.renameMaterial(m.id, title)
                Repository.setMaterialCategory(m.id, category)
                if (m.kindValue == MaterialKind.TEXT) Repository.setMaterialText(m.id, body)
                when {
                    showGallery -> scope.launch {
                        val f = Repository.materialFileFor(m.id)
                        val ok = f != null && Platform.ui.saveImageToGallery(f.absolutePath)
                        showToast(if (ok) "已保存，并存到系统相册" else "已保存，但存相册失败")
                    }
                    showCard -> scope.launch {
                        val card = withContext(Dispatchers.IO) {
                            // 图片＝图作头像（复制进 images/，别让卡直接引用 materials/ 的文件，
                            // 删素材不能抽掉在用的图）；文字＝整段进角色设定（persona），进编辑器接着写
                            val name = title.ifBlank { "新角色" }
                            if (m.kindValue == MaterialKind.IMAGE) {
                                val f = Repository.materialFileFor(m.id)
                                    ?: return@withContext null
                                val ext = f.extension.ifBlank { "png" }
                                val avatar = Repository.saveImageBytes(f.readBytes(), ext)
                                CharacterCard(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    avatarUri = avatar
                                )
                            } else {
                                CharacterCard(
                                    id = UUID.randomUUID().toString(),
                                    name = name,
                                    persona = body
                                )
                            }
                        } ?: run { showToast("已保存，但素材文件已丢失、建不了角色卡"); return@launch }
                        Repository.saveCharacter(card)
                        showToast("已保存，并创建角色卡「${card.name}」，进角色卡页接着写")
                    }
                    showBook -> scope.launch {
                        // 整段正文存成**一本新书**（单条目、常开编辑器接着改关键词）
                        val bookName = title.ifBlank { m.title }
                        Repository.saveWorldBook(
                            UUID.randomUUID().toString(),
                            WorldBook(
                                name = bookName,
                                entries = listOf(
                                    WorldBookEntry(id = 0, name = bookName, comment = bookName, content = body)
                                )
                            )
                        )
                        showToast("已保存，并创建世界书「$bookName」，进世界书库接着整理")
                    }
                }
                onSaved()
            }) {
                Text(
                    when {
                        showGallery -> "存到相册"
                        showCard -> "存为角色卡"
                        showBook -> "保存为新世界书"
                        else -> "保存"
                    }
                )
            }
        },
        dismissButton = {
            Row {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

private fun shortDate(at: Long): String =
    if (at <= 0L) "" else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(at))
