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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.showToast
import java.text.SimpleDateFormat
import java.util.Date
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

    // 胶囊**常驻**（用户 2026-09-27 口径）：空库也把"全部 + 五类"画出来，让用户一眼看到素材库是按什么分的，
    // 而不是对着一个空白页猜。此前是"哪类有素材才画哪颗"，空库一屏胶囊都没有（连"全部"也没有）。
    val shown = all.filter { m ->
        (pickKind == null || m.kindValue == pickKind) &&
            (categoryFilter == null || m.categoryValue == categoryFilter)
    }

    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            val subtitle = if (onPick != null) "点一条即选中" else "共 ${all.size} 条素材"
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // 胶囊筛选行：**只有一个维度**（项目分类，用户 2026-09-27 口径），且常驻——空库也画。
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        MaterialRow(
                            m = m,
                            onClick = {
                                val pick = onPick
                                if (pick != null) {
                                    pick(m); onBack()
                                } else detail = m
                            }
                        )
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
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { BackArrowButton(onBack) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
}

/** 一条素材：左边缩略图/形态块，右边标题、分类与标签 */
@Composable
private fun MaterialRow(m: Material, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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

/** 详情：重命名 / 改分类 / 删除。分类是单选（一条素材只属于一类） */
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
    var confirmDelete by remember(m.id) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条素材？") },
            text = { Text("「${m.title}」会从素材库里消失，文件也一并删掉（不进回收站，不能找回）。") },
            confirmButton = { TextButton(onClick = onDeleted) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("素材详情") },
        text = {
            Column {
                if (m.kindValue == MaterialKind.TEXT) {
                    Text(
                        m.previewText(300),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            TextButton(onClick = {
                Repository.renameMaterial(m.id, title)
                Repository.setMaterialCategory(m.id, category)
                onSaved()
            }) { Text("保存") }
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
