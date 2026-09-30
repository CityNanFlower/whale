package com.mysticat.roleplay.ui.screens

/**
 * 上下文查看器（「上次请求」，台账 85）：把**最近一次**真实请求的装配产物摆给用户看——
 * 系统提示词按段表逐段、最终消息数组（含 tailHint 与 @depth 条目的最终形态）、命中的世界书、
 * 模型与参数、字数与 token 粗估。只展示、不发请求（零花钱）。
 *
 * 数据侧在 [com.mysticat.roleplay.data.RequestViewer]：快照与实发**同源**（同一次装配采集），
 * 桌面自检有"快照与请求体逐字段一致"的闸门兜着；内存态，进程退出即清空。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.PromptMode
import com.mysticat.roleplay.data.RequestSnapshot
import com.mysticat.roleplay.data.UsageNote
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun modeLabel(mode: PromptMode): String = when (mode) {
    PromptMode.RP -> "扮演"
    PromptMode.TOOL -> "工具"
    PromptMode.PLAY -> "玩法"
    PromptMode.SUGGEST -> "代笔"
}

@Composable
internal fun RequestViewerDialog(
    snap: RequestSnapshot,
    /** 本轮真实用量（批次 C）：响应 usage 报了就展示；与快照同源（同一次请求收尾时成对记录） */
    usage: com.mysticat.roleplay.data.UsageNote? = null,
    onDismiss: () -> Unit
) {
    // 段与消息都按"点一下展开全文"看：默认全收起，列表才扫得快。
    // key 用段名/序号（段名唯一；消息用序号前缀防撞段名）
    var expanded by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val time = remember(snap.at) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(snap.at))
    }
    val fullText = remember(snap) {
        buildString {
            snap.segments.forEach { (name, text) -> appendLine("【$name】").appendLine(text) }
            snap.messages.forEach { (role, text) -> appendLine("[$role]").appendLine(text) }
        }
    }

    val row: @Composable (String, String, String) -> Unit = { key, title, body ->
        val open = expanded == key
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { expanded = if (open) null else key }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${body.length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (open) {
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上次请求") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            "${snap.characterName} · ${modeLabel(snap.mode)} · ${time}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "${snap.model}｜温度 ${snap.temperature ?: "未发送"}｜输出上限 ${snap.maxTokens}｜档位 ${snap.tierLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "系统提示词 ${snap.systemChars} 字 · 消息 ${snap.messages.size} 条 · 全文约 ${snap.totalChars} 字 ≈ ${snap.approxTokens} tokens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                usage?.let { u ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                "本轮真实用量" + if (u.reported) "" else "（端点未回报 token，只有字符量级）",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                buildString {
                                    if (u.promptTokens != null) append("输入 ${u.promptTokens}")
                                    if (u.completionTokens != null) {
                                        if (isNotEmpty()) append(" · ")
                                        append("输出 ${u.completionTokens}")
                                    }
                                    if (isEmpty()) append("token 以端点实际回报为准")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val total = u.reasoningChars + u.replyChars
                            if (u.reasoningChars > 0 && total > 0) {
                                Text(
                                    "思考 ${u.reasoningChars} 字 ≈ ${u.reasoningChars * 100 / total}% · 正文 ${u.replyChars} 字",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            u.finishReason?.let {
                                Text(
                                    "结束原因：${u.finishReasonLabel ?: it}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            u.budgetHint?.let {
                                Text(
                                    "⚠ $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                if (snap.bookHits.isNotEmpty()) {
                    Text(
                        "世界书注入（${snap.bookHits.size} 条）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    snap.bookHits.forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    "系统提示词（${snap.segments.size} 段，点开看全文）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                snap.segments.forEach { (name, text) -> row("seg:$name", name, text) }
                HorizontalDivider()
                Text(
                    "消息数组（${snap.messages.size} 条，含贴在末条用户消息上的本轮要求）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                snap.messages.forEachIndexed { i, (role, text) ->
                    row("msg:$i", "[$role] ${text.take(24)}", text)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(fullText)) }) {
                    Text("复制全文")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
