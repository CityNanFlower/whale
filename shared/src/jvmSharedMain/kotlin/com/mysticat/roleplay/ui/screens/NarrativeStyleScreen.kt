package com.mysticat.roleplay.ui.screens

import com.mysticat.roleplay.ui.WhaleBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.NarrativeStyle
import com.mysticat.roleplay.data.NarrativeStyles
import com.mysticat.roleplay.data.StyleAxis

/**
 * 「叙事设置」页（P-F1 高级档）：把全部叙事轴一次性铺开。
 *
 * 基础档（聊天页「更多」面板）只给节奏 + 密度两根轴，其余轴都在这里改——
 * 所以这一页必须**自己解释每一档是干什么的**（选中档的指令文案直接显示出来，
 * 用户看到的和发给模型的是同一句话，这是 [NarrativeStyles.AXES] 单表驱动的意义）。
 *
 * 状态不自己存：风格归通会话所有（`Conversation.narrative`），本页只负责把改好的
 * [NarrativeStyle] 通过 [onChange] 交回 ChatViewModel 落盘——避免出现"两份内存态互相覆盖"。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NarrativeStyleScreen(
    style: NarrativeStyle,
    onChange: (NarrativeStyle) -> Unit,
    onBack: () -> Unit
) {
    var showHintText by remember { mutableStateOf(false) }
    // 系统返回键等同左上角返回（本页是覆盖在聊天页之上的全屏层，返回键默认会退出聊天页）
    WhaleBackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("叙事设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { onChange(NarrativeStyle()) }) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "只影响本会话的表达方式，改完立即生效，下一条回复就按新设置写。人设、世界观、剧情走向都不受这里影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "当前已启用 ${style.activeCount} 项。轴开得越多越容易互相稀释，建议同时启用 4~6 项以内。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 套餐（一键填轴，之后仍可逐轴微调）──
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("风格套餐", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "一键套用一整组轴，点完可以继续在下面微调。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NarrativeStyles.PRESETS.forEach { p ->
                        WhaleChip(
                            selected = p.style == style,
                            onClick = { onChange(p.style) },
                            label = { Text(p.name) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    NarrativeStyles.PRESETS.firstOrNull { it.style == style }?.desc
                        ?: if (style.isDefault) "当前全是默认档：节奏与密度都是「标准」，其余轴未启用。"
                        else "当前是自定义组合。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── 各轴 ──
            NarrativeStyles.AXES.forEach { axis ->
                item(key = axis.key) {
                    AxisSection(
                        axis = axis,
                        style = style,
                        onPick = { value -> onChange(NarrativeStyles.change(style, axis, value)) }
                    )
                }
            }

            // ── 生效指令预览：界面上看到的和真正发出去的必须是同一句话 ──
            item {
                val hint = NarrativeStyles.buildHint(style)
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showHintText = !showHintText }) {
                    Icon(
                        if (showHintText) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (hint.isBlank()) "本轮不注入风格指令" else "本轮注入的风格指令（${hint.length} 字）")
                }
                if (showHintText && hint.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            hint,
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AxisSection(
    axis: StyleAxis,
    style: NarrativeStyle,
    onPick: (String) -> Unit
) {
    val picked = style.valueOf(axis.key)
    // 选中档的指令文案：用户看到的 == 发给模型的（多选轴逐条列，默认档没有指令，改显示"不选是什么效果"）
    val shown = axis.options.filter { it.value in picked }
        .joinToString("\n") { it.hint.ifBlank { axis.defaultNote } }
        .ifBlank { axis.defaultNote }
    Column {
        Text(axis.label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            axis.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            axis.options.forEach { opt ->
                WhaleChip(
                    selected = NarrativeStyles.isPicked(style, axis, opt.value),
                    onClick = { onPick(opt.value) },
                    label = { Text(opt.label) }
                )
            }
            // 多选轴也给一个"全不选"档并标出选中态：默认状态（一条都没选）要看得见，不然像没设置
            if (axis.multi) {
                WhaleChip(
                    selected = picked.isEmpty(),
                    onClick = { onPick("") },
                    label = { Text("全不选") }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            shown,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
