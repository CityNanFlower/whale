package com.mysticat.roleplay.ui.screens

/**
 * 聊天页的两个设置类对话框：重命名会话、「本会话设定」（世界观/用户设定/特别指示等）。
 *
 * * 2026-09-17（D10）从 `ChatScreen.kt` 拆出：纯结构拆分，逻辑未动。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.TextInputDialog

@Composable
internal fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    TextInputDialog(
        title = "重命名会话",
        initial = initial,
        label = "会话名称",
        singleLine = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * 本会话设定：**先把「当前生效的是什么」摆出来**，再给候选列表（选用预设 / 无设定 / 用默认 / 新建）。
 *
 * 用户反馈（2026-09-16）"看不出现在是什么设定"：原来这个弹窗只有一串可点的条目，
 * 当前生效的那条与其它条目长得一模一样，只能靠记住上次点了哪个。现在两处区分：
 * 顶部一张「当前生效」卡片（回查到的名字 + 设定内容），列表里命中当前内容的条目打勾并标「使用中」。
 */
@Composable
internal fun ChatSettingDialog(
    current: String,
    onDismiss: () -> Unit,
    onUse: (String) -> Unit,
    onCreate: (String, String) -> Unit
) {
    // **不能**在组合期裸读盘 —— 下面两个输入框每敲一个字都会重组，等于每键一次 JSON 解析。
    // 用 remember(rev) 缓存；rev 只在"新建了一条设定"时 +1，保证新建后列表立刻刷新（与原来行为一致）。
    var rev by remember { mutableStateOf(0) }
    val presets = remember(rev) { Repository.listUserSettings() }
    val def = remember(rev) { Repository.defaultUserSetting() }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    // 当前生效的设定：落盘的只有**内容**，名字要按内容回查（默认设定 / 我的预设 / 自己写的）
    val currentName = when {
        current.isBlank() -> "无设定"
        def != null && def.content == current -> "默认设定：${def.name}"
        else -> presets.firstOrNull { it.content == current }?.name ?: "自定义设定"
    }
    val defActive = def != null && def.content == current

    // 候选条目：命中当前内容的一条打勾加色，一眼能看出现在是哪条（内容相同则一起标，内容本来就是一回事）
    val optionRow: @Composable (String, String?, Boolean, () -> Unit) -> Unit =
        { label, sub, active, onClick ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (sub != null) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (active) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "使用中",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "使用中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本会话设定") },
        text = {
            // AlertDialog 的 text 槽不会自动滚动，设定条目一多下面的就既看不到也点不到
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "当前生效",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            currentName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (current.isBlank()) "角色只按角色卡与基础提示词回话，不带任何额外设定。"
                            else current,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "切换为",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 问题 #33：会话设定支持「无设定」——清空本会话的用户设定（不带走任何预设）
                optionRow("无设定（本会话不使用设定）", null, current.isBlank()) { onUse("") }
                if (def != null) {
                    optionRow("使用默认设定：${def.name}", def.content, defActive) { onUse(def.content) }
                }
                if (presets.isNotEmpty()) {
                    HorizontalDivider()
                    presets.forEach { s ->
                        // 与当前生效那条**同名同内容**的，才回显名字而不是"自定义设定"
                        optionRow(s.name, s.content, s.content == current) { onUse(s.content) }
                    }
                }
                HorizontalDivider()
                TextButton(onClick = { showCreate = !showCreate }) {
                    Text(if (showCreate) "收起新建设定" else "+ 新建设定")
                }
                if (showCreate) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("设定名称") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = content, onValueChange = { content = it },
                        label = { Text("设定内容（传达给角色）") },
                        minLines = 3
                    )
                    TextButton(
                        onClick = { onCreate(name, content); rev++ },
                        enabled = content.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("创建并应用") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
