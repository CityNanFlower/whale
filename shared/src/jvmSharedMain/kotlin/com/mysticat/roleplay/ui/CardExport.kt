package com.mysticat.roleplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.PngCardCodec
import kotlinx.coroutines.launch
import java.io.File

/**
 * 角色卡导出（图片卡 / 纯文本 / JSON 文件）。
 *
 * JSON 走 [CardImport.toTavernJson]，与角色编辑器顶栏那个分享图标同一条链路（这里只是换个入口，
 * 不重写转换）；图片卡是自己渲染的 1080×1920 竖图，纯文本见 [CardImport.toPlainText]。
 *
 * 跨平台拆分：本文件只留**共享的对话框编排与文件名规则**；渲染落盘（buildCardImageFile） * 系统分享、剪贴板、JSON 保存位置全部走 [PlatformUi]（Android 实现在 androidMain，桌面补齐）。
 */

/** 导出用的文件名（去掉文件系统不认的字符） */
internal fun cardFileName(card: CharacterCard, ext: String): String =
    card.name.ifBlank { "character" }.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".$ext"

/**
 * PNG 角色卡的字节：头像图作载体——已是 PNG 的头像直接嵌数据（原图零损耗），
 * 其它格式（jpg/webp）解码后转 PNG；没有头像就用渲染的竖版长图兜底（图片卡能出就一定能出 PNG 卡）。
 * 编解码本身见 data.PngCardCodec；失败返回 null。
 */
internal suspend fun pngCardBytes(card: CharacterCard, json: String): ByteArray? {
    val avatar = card.avatarUri?.takeIf { it.isNotBlank() && !it.startsWith("http") }
    val carrier = runCatching { avatar?.let { java.io.File(it).readBytes() } }.getOrNull()
        ?.takeIf { PngCardCodec.isPng(it) }
        ?: avatar?.let { Platform.ui.decodeImageBitmap(it) }?.let { Platform.ui.encodePng(it) }
        ?: Platform.ui.buildCardImageFile(card)?.readBytes()
        ?: return null
    return PngCardCodec.embed(carrier, json)
}

/**
 * 导出选择框：图片卡 / 纯文本 / JSON 文件。
 * 每个格式下面直接摆动作按钮（保存到相册、分享、复制…），不藏二级菜单——三条链路一眼能选全。
 * 角色卡列表页和角色编辑器都能调它，样式与行为只有这一份。
 */
@Composable
fun CardExportDialog(card: CharacterCard, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saveGallery = rememberGallerySaver()
    val saveJson = Platform.ui.rememberJsonFileSaver { ok ->
        showToast(if (ok) "角色卡已导出" else "导出失败")
    }
    var busy by remember { mutableStateOf(false) }

    // 分享是 startActivity + FileProvider，配置不对会抛异常；协程里没人接就是崩应用，统一兜住（与备份分享一致）
    fun shareSafely(block: () -> Unit) {
        runCatching(block).onFailure { showToast("分享失败：${it.message}") }
    }

    // 图片卡：先渲染落盘再决定是存相册还是分享（渲染是磁盘 + 解码，放 IO，期间禁用按钮）
    fun withCardImage(after: (File) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val file = Platform.ui.buildCardImageFile(card)
            busy = false
            if (file == null) showToast("生成图片失败，请重试") else after(file)
        }
    }

    // PNG 角色卡：同一条"先落盘再交出去"的链路，载体是头像（没头像用长图兜底）
    fun withPngCard(after: (File) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val file = Platform.ui.buildPngCardFile(card, CardImport.toTavernJson(card))
            busy = false
            if (file == null) showToast("生成 PNG 卡失败，请重试") else after(file)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出「${card.name}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExportSection(
                    title = "图片卡",
                    subtitle = if (busy) "正在生成图片…" else "渲染成竖版长图，适合直接发图",
                    actions = listOf(
                        "保存到相册" to {
                            withCardImage { saveGallery(it.absolutePath) }
                        },
                        "分享" to { withCardImage { file -> shareSafely { Platform.ui.shareImageFile(file) } } }
                    ),
                    enabled = !busy
                )
                HorizontalDivider()
                ExportSection(
                    title = "纯文本",
                    subtitle = "人设 + 世界观 + 开场白，可直接粘贴",
                    actions = listOf(
                        "复制" to {
                            runCatching {
                                Platform.ui.copyToClipboard(card.name, CardImport.toPlainText(card), "已复制角色卡文本")
                            }
                        },
                        "分享" to { shareSafely { Platform.ui.shareText("分享角色卡文本", CardImport.toPlainText(card)) } }
                    ),
                    enabled = !busy
                )
                HorizontalDivider()
                ExportSection(
                    title = "角色卡文件",
                    subtitle = "chara_card_v2 JSON，可导入其他角色扮演应用",
                    actions = listOf(
                        "保存为文件" to { saveJson(cardFileName(card, "json"), CardImport.toTavernJson(card)) }
                    ),
                    enabled = !busy
                )
                HorizontalDivider()
                ExportSection(
                    title = "PNG 角色卡",
                    subtitle = if (busy) "正在生成…" else
                        "头像图内嵌卡数据，发出去就是一张图，酒馆系应用可直接导入（无头像用长图代替）",
                    actions = listOf(
                        "保存到相册" to { withPngCard { saveGallery(it.absolutePath) } },
                        "分享" to { withPngCard { file -> shareSafely { Platform.ui.shareImageFile(file) } } }
                    ),
                    enabled = !busy
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun ExportSection(
    title: String,
    subtitle: String,
    actions: List<Pair<String, () -> Unit>>,
    enabled: Boolean
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            actions.forEach { (label, onClick) ->
                TextButton(onClick = onClick, enabled = enabled) { Text(label) }
            }
        }
    }
}
