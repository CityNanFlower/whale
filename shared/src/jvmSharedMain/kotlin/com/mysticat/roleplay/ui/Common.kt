package com.mysticat.roleplay.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.imageModel
import kotlinx.coroutines.launch

/**
 * 共享层 UI 工具箱。选择器类函数（图片/字体/JSON/相册/屏幕比例）只是**薄委托**，
 * 真正的平台实现在各端注入的 [PlatformUi] 里——调用点保持原签名不改。
 */

/** 从系统图库选择一张图片并复制到应用目录；返回本地路径，取消或失败返回 null。 */
@Composable
fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit =
    Platform.ui.rememberImagePicker(onPicked)

/**
 * 选择一份字体文件（.ttf/.otf/.ttc）并复制到应用目录。
 * 回调 (本地绝对路径, 显示名)；取消或校验失败回调 (null, "")。
 * 平台实现里含两级校验（魔数+引擎试加载、渲染探针），坏字体在选取阶段就被拦下（问题 #19）。
 */
@Composable
fun rememberFontFilePicker(onPicked: (String?, String) -> Unit): () -> Unit =
    Platform.ui.rememberFontFilePicker(onPicked)

/**
 * 选择一份**音频文件**（背景音乐用）并复制到应用目录。
 * 回调 (本地绝对路径, 原文件名)；取消、或格式不被本平台支持时回调 (null, "") 并已给出提示。
 */
@Composable
fun rememberAudioFilePicker(onPicked: (String?, String) -> Unit): () -> Unit =
    Platform.ui.rememberAudioFilePicker(onPicked)

/**
 * 背景图裁剪框比例 = 当前屏幕比例（问题 #12）。
 * 原来写死 0.75，生成图与屏幕不对版，铺到聊天窗上怎么弄都像是「没铺平」。
 */
@Composable
fun rememberScreenAspect(fallback: Float = 0.75f): Float =
    Platform.ui.rememberScreenAspect(fallback)

/** 点一下就存进系统相册，并 Toast 反馈（预览弹窗的「保存」用，问题 #12） */
@Composable
fun rememberGallerySaver(): (String) -> Unit = Platform.ui.rememberGallerySaver()

/** 从系统文件管理器选择一份 JSON/文本并读取其内容；取消或读取失败返回 null。 */
@Composable
fun rememberJsonFilePicker(onPicked: (String?) -> Unit): () -> Unit =
    Platform.ui.rememberJsonFilePicker(onPicked)

/**
 * 返回键拦截的共享入口（Android=系统返回键，桌面=Esc 由窗口壳转成同一回调）。
 * 各页面原来直接用 androidx.activity.compose.BackHandler，上移共享后统一改走这里。
 */
@Composable
fun WhaleBackHandler(enabled: Boolean = true, onBack: () -> Unit) =
    Platform.ui.BackHandler(enabled, onBack)

/** 字体文件可用性一级校验的共享入口（Theme 与用户页字体列表用） */
internal fun isUsableFontFile(path: String): Boolean = Platform.ui.isUsableFontFile(path)

/**
 * 生图尺寸选项：标准档 + 高清大档。
 * 大档（1920x1920 级别像素量 ≥3686400）用于 seedream-5.0 等有最小像素要求的新模型；
 * 小档给轻量模型用，遇到尺寸限制时 AiClient 会自动去掉 size 按服务商默认重试。
 */
val ImageSizeOptions = listOf(
    "1024x1024", "2048x2048",
    "768x1024", "1440x2560",
    "1024x768", "2560x1440"
)

/** 尺寸选项的中文说明 */
fun imageSizeLabel(size: String): String = when (size) {
    "768x1024", "1440x2560" -> "竖屏"
    "1024x768", "2560x1440" -> "横屏"
    else -> "方形"
}

/**
 * 筛选胶囊 —— M3 `FilterChip` 的替身（**桌面自绘，手机原样交给 Material 3**）。
 *
 * 为什么要自绘（用户反馈 #3"选中胶囊出现内部框选"→"现在还有内框"）：
 * 桌面上鼠标停在**未选中**的 M3 `FilterChip` 上时，除了胶囊底变暗，标签外面还会多出一个
 * **直角方框**。三轮实测把嫌疑人排除干净了——
 *   ① 把桌面 `LocalIndication` 换成"什么都不画"的指示：方框照旧（⇒ 不是 Compose Desktop 的调试指示的结论被推翻）；
 *   ② `RippleConfiguration(color = Transparent)` 把涟漪与状态层全清零：方框照旧（⇒ 也不是涟漪）；
 *   ③ 只有**未选中**（带描边）的胶囊有、选中的没有（⇒ 是 M3 chip 自己的 hover 绘制）。
 * 结论：那是 M3 组件内部画的，想根除只能不用它的 chip。手机端没有 hover，不存在这个问题，
 * 所以 [isDesktopLayout] 为 false 时**原样调用 M3 组件**（像素不变、Android 零回归）。
 *
 * 自绘版刻意对齐 M3 的版式（形状 8dp 圆角、最小高度 32dp、左右内边距 16dp、标签 labelLarge），
 * 换掉以后各页排版不会跳；悬停反馈改由"胶囊底色变浅"承担（**形状正确**，不会再出现方框）。
 */
@Composable
fun WhaleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    if (!isDesktopLayout) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon
        )
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme
    val container = when {
        !enabled -> colors.onSurface.copy(alpha = 0.12f)
        selected -> colors.secondaryContainer
        hovered -> colors.surfaceContainerHighest
        else -> colors.surface
    }
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onSecondaryContainer
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier
            .clip(shape)
            .background(container)
            .then(
                if (selected || !enabled) Modifier
                else Modifier.border(1.dp, colors.outline, shape)
            )
            // indication = null：按下/悬停反馈由上面的底色承担，不让任何"指示"再在胶囊里画东西
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 32.dp)
            // 4.5dp（不是 6dp）：实测 M3 胶囊总高 32dp，labelLarge 的文字行盒约 22.7dp，
            // 上下各 4.5dp 才对得上——不留这个差，换成自绘后胶囊会比原来高 2.7dp，整页内容跟着往下移。
            .padding(horizontal = 16.dp, vertical = 4.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                leadingIcon?.let { it(); Spacer(Modifier.width(8.dp)) }
                label()
                trailingIcon?.let { Spacer(Modifier.width(8.dp)); it() }
            }
        }
    }
}

/**
 * 生图尺寸 chip 行。
 *
 * 原来「灵感创作 → 形象」与 [PromptDialog] 各写一份循环。容器由调用方给
 * （一处 FlowRow 换行、一处横向滚动 Row），这里只负责这一排 chip 本身。
 */
@Composable
fun ImageSizeChips(selected: String, onSelect: (String) -> Unit) {
    ImageSizeOptions.forEach { s ->
        WhaleChip(
            selected = selected == s,
            onClick = { onSelect(s) },
            label = { Text("${imageSizeLabel(s)} $s") }
        )
    }
}

/**
 * 通用提示输入弹窗（用于描述生图提示词）。
 * 传入 [sizeOptions] 时会在输入框下面多出一排尺寸选择——生图尺寸在生图时决定，
 * 不再放在「模型设置」里（用户要求 #6）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String = "生成",
    loading: Boolean = false,
    sizeOptions: List<String> = emptyList(),
    defaultSize: String = "",
    /** 由 AI 起草提示词（0.1.2）：非空时显示按钮，点击后由调用方异步算好内容 */
    draftLabel: String? = null,
    drafting: Boolean = false,
    /** AI 起草结果：变化时自动填入输入框，用户可继续手改 */
    draftText: String? = null,
    onDraft: (() -> Unit)? = null,
    /**
     * 文本每次变化时回调：调用方据此把草稿落盘 —— 用户可能写完提示词
     * 不点「生成」就直接退出应用，下次打开要原样看到（"退出自动保存未保存的更改"）。
     */
    onTextChange: ((String) -> Unit)? = null,
    /**
     * 非空时在输入框右上角显示一个叉号（清空提示词）。用户口径：
     * **提示词用完后不自动删，还是通过叉号删** —— 所以清除必须是显式动作。
     */
    onClear: (() -> Unit)? = null,
    onConfirm: (prompt: String, size: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var size by remember {
        mutableStateOf(defaultSize.takeIf { it.isNotBlank() } ?: sizeOptions.firstOrNull().orEmpty())
    }
    // 起草结果回填：只在拿到非空内容时覆盖，用户仍可继续手改
    androidx.compose.runtime.LaunchedEffect(draftText) {
        if (!draftText.isNullOrBlank()) {
            text = draftText
            onTextChange?.invoke(draftText)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onTextChange?.invoke(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(placeholder) },
                    minLines = 2,
                    maxLines = 5,
                    trailingIcon = if (onClear != null && text.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                text = ""
                                onClear()
                                onTextChange?.invoke("")
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "清空提示词")
                            }
                        }
                    } else null
                )
                if (sizeOptions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("尺寸", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    // 用 FlowRow 自动换行：三个尺寸 chip 在窄弹窗里不会被挤成竖条
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageSizeChips(selected = size, onSelect = { size = it })
                    }
                }
                // 0.1.2：不想自己写提示词时，让 AI 按上下文（如角色卡设定）起草一版
                if (draftLabel != null && onDraft != null) {
                    Spacer(Modifier.height(10.dp))
                    AiActionButton(
                        label = draftLabel,
                        busyLabel = "正在起草…",
                        onClick = onDraft,
                        busy = drafting,
                        enabled = !drafting && !loading,
                        filled = false,
                        starSize = 16.dp,
                        gap = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading && text.isNotBlank(),
                onClick = { onConfirm(text.trim(), size) }
            ) {
                Text(if (loading) "生成中…" else confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") }
        }
    )
}

/**
 * 通用「单字段文本输入」弹窗。
 *
 * ChatScreen 里「重命名会话 / 编辑消息 / 角色记忆」三个弹窗骨架本来各写一份，
 * 差别只在标题、行数和有没有说明行。这里统一。
 *
 * @param hint 输入框上方的说明行（角色记忆用来解释这个字段的用途）
 * @param clearLabel 非空时在「取消」左边多一个清空按钮（角色记忆用；点它等同提交空串）
 * @param clearConfirmText 清空前的二次确认文案（2026-09-21：清空是"一键提交空串"的破坏性动作，
 *   角色记忆可能积累了很久，直接执行太容易误触，所以点「清空」先进一层确认）
 * @param confirmEnabled 提交按钮的可用条件，默认「非空白」
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    label: String? = null,
    placeholder: String? = null,
    confirmLabel: String = "保存",
    clearLabel: String? = null,
    clearConfirmText: String = "清空后无法恢复。",
    singleLine: Boolean = false,
    minLines: Int = 4,
    maxLines: Int = 12,
    confirmEnabled: (String) -> Boolean = { it.isNotBlank() }
) {
    var text by remember { mutableStateOf(initial) }
    var confirmingClear by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (confirmingClear) confirmingClear = false else onDismiss() },
        title = { Text(if (confirmingClear) "清空$title？" else title) },
        text = {
            if (confirmingClear) {
                Text(clearConfirmText)
            } else {
                Column {
                    if (hint != null) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = if (label == null) null else { { Text(label) } },
                        placeholder = if (placeholder == null) null else { { Text(placeholder) } },
                        singleLine = singleLine,
                        minLines = if (singleLine) 1 else minLines,
                        maxLines = if (singleLine) 1 else maxLines
                    )
                }
            }
        },
        confirmButton = {
            if (confirmingClear) {
                TextButton(onClick = { onConfirm("") }) { Text(clearLabel ?: "清空") }
            } else {
                TextButton(onClick = { onConfirm(text) }, enabled = confirmEnabled(text)) {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            Row {
                // 清空按钮只留在输入态；确认态里它已经变成右侧的"执行"按钮，避免同一动作两个入口
                if (clearLabel != null && !confirmingClear) {
                    TextButton(onClick = { confirmingClear = true }) { Text(clearLabel) }
                }
                TextButton(onClick = { if (confirmingClear) confirmingClear = false else onDismiss() }) {
                    Text("取消")
                }
            }
        }
    )
}

/**
 * 把 Repository 的**写入失败**回调转成一句主线程 Toast。
 *
 * 写入搬进串行通道之后，"保存失败"不再抛给调用方 —— 不接这个回调的话，
 * 用户看到的是"保存成功"的样子，而数据其实没落盘。回调在**写入线程**上触发，
 * 平台的 toast 实现负责切回主线程。context 参数保留只为调用点零改动（不再使用）。
 */
fun writeFailureToast(context: Any?, what: String): (Throwable) -> Unit = { err ->
    Platform.ui.toast("${what}失败：${err.message ?: "磁盘写入异常"}", long = true)
}

/**
 * 「· 一句话」的须知行。
 * AuthScreen 与 VersionSecurityScreen 里各自有一份**字节级相同**的私有实现，这里统一。
 */
@Composable
fun NoticeLine(text: String) {
    Text(
        "· $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 会话列表/气泡上的时间文案（`MM-dd HH:mm`）。
 * ChatScreen 与 ConversationListScreen 里各有一份完全相同的私有实现，这里统一。
 */
fun timeText(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

/** 简单错误提示条，供各页面复用 */
@Composable
fun ErrorBanner(message: String?, onDismiss: () -> Unit) {
    if (message.isNullOrBlank()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("出错了") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

/**
 * 问题 #8 / #6：AI 生图结果流程，两步。
 *
 * 第一步「结果预览」：看缩略图，选 保存 / 应用 / 重试。
 * 第二步「确认使用」（点应用后）：**先看全图**，下方给「编辑（裁剪）」和「应用」，
 * 右上角 × 或点周围空白都关闭。
 *
 * @param cropAspect 裁剪框宽高比（头像 1f，聊天背景 0.75f）
 * @param onApply 把**最终**图片（可能是裁剪后的新文件）真正用到角色卡 / 会话上
 * @param onRetry 用同一个提示词重新生成
 */
@Composable
fun GeneratedImageDialog(
    title: String,
    uri: String,
    cropAspect: Float = 1f,
    onApply: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    // 0 = 结果预览；1 = 确认使用（全图）
    var stage by remember { mutableStateOf(0) }
    var cropping by remember { mutableStateOf(false) }
    // 可能被裁剪过的新文件路径
    var workingUri by remember(uri) { mutableStateOf(uri) }
    // 问题 #13：裁剪永远以**原图**为基准，裁完不满意可以再裁一次，不会在上次结果上二次损失
    val originalUri = uri

    fun saveToGallery(target: String) {
        if (saving) return
        saving = true
        scope.launch {
            val ok = Platform.ui.saveImageToGallery(target)
            saving = false
            showToast(if (ok) "已保存到相册（Pictures/MystiCat）" else "保存失败，请重试")
        }
    }

    if (cropping) {
        CropDialog(
            sourceUri = originalUri,
            aspect = cropAspect,
            onCropped = {
                workingUri = it
                cropping = false
            },
            onDismiss = { cropping = false }
        )
        return
    }

    if (stage == 1) {
        ImagePreviewDialog(
            title = "确认使用这张图",
            uri = workingUri,
            onEdit = { cropping = true },
            onSave = { saveToGallery(workingUri) },
            onApply = { onApply(workingUri) },
            // 问题 #28：应用界面（全图确认）可返回结果预览，不丢这次生成
            onBack = { stage = 0 },
            onDismiss = onDismiss
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = imageModel(uri),
                    contentDescription = "生成结果",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "「保存」只是存入系统相册留档；点「应用」会先看全图，可以再裁剪。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                // 三个操作等宽同排（原来「保存」独占 confirmButton 会歪）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                        onClick = { saveToGallery(uri) }
                    ) { Text(if (saving) "保存中…" else "保存") }

                    Button(
                        onClick = {
                            workingUri = uri
                            stage = 1
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("应用") }

                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f)
                    ) { Text("重试") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}


/**
 * AI 生成进行中的星星图标——alpha 呼吸闪烁，生成结束恢复原状。
 * 角色卡编辑页（头像/背景）与灵感创作页（角色/形象）共用，保证同一套加载观感。
 */
@Composable
fun BlinkingStar(modifier: Modifier = Modifier) {
    val alpha = rememberInfiniteTransition(label = "aiStar").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "aiStarAlpha"
    ).value
    Icon(
        Icons.Filled.AutoAwesome,
        contentDescription = null,
        modifier = modifier.alpha(alpha)
    )
}

/**
 * 「AI 生成中」动作按钮。
 *
 * 生成期间图标换成闪烁星星、文案换成进行时，生成结束恢复原状。这套观感原本在
 * 角色卡编辑页（头像/背景）与灵感创作页（形象/角色卡/扩写/起草）各写了一份，共 6 处。
 *
 * @param filled true = 实心 Button（主操作），false = OutlinedButton（次要操作）
 * @param starSize 星星尺寸；与 [gap] 一起控制小按钮的紧凑排布
 */
@Composable
fun AiActionButton(
    label: String,
    onClick: () -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busyLabel: String = "正在生成…",
    filled: Boolean = true,
    starSize: Dp = 18.dp,
    gap: Dp = 8.dp,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    val content: @Composable RowScope.() -> Unit = {
        if (busy) BlinkingStar(Modifier.size(starSize))
        else Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(starSize))
        Spacer(Modifier.width(gap))
        Text(if (busy) busyLabel else label)
    }
    if (filled) {
        Button(onClick, modifier, enabled, contentPadding = contentPadding, content = content)
    } else {
        OutlinedButton(onClick, modifier, enabled, contentPadding = contentPadding, content = content)
    }
}

/**
 * 「点空白处退出键入态」（用户 2026-09-24 安卓真机反馈）：
 * Compose 与旧 View 体系不同——点**非焦点区域**（空白、标题、说明文字）不会让输入框失焦，
 * 键盘要按系统返回才能收起，收起后焦点还留在框里、点字又出选字光标。
 *
 * 挂在内容区**最外层**、在冒泡（Main）相观察每次按下：控件（输入框 / 按钮 / 开关…）会先消费掉
 * down，所以"点到真控件"一律不碰；只有全程无人消费（点在空白 / 纯文本上）才在松手时释放焦点。
 * 拖动中途被滚动容器接管时 `waitForUpOrCancellation` 返回 null ⇒ 滚动不误伤焦点。
 *
 * 接线只有一处（`AppRoot` 的 NavHost），全部页面、两端（安卓 + 桌面）同时生效。
 */
@Composable
fun Modifier.blurOnEmptyTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed) return@awaitEachGesture
            val up = waitForUpOrCancellation()
            if (up != null && !up.isConsumed) focusManager.clearFocus()
        }
    }
}

/**
 * 界面文案的行内 markdown：设置页 / 编辑页的说明文字**源码里就写着 `**粗体**` 与反引号**，
 * 但这些位置一直是裸 `Text` ⇒ 星号原样显示成"渲染异常"（用户 2026-09-24 报的"语音朗读底下的注释"）。
 * 只处理行内这两样（气泡那套带块级/旁白语义，不适用静态文案），其余字符原样保留；
 * 没有标记的字符串走它也没有任何变化。口径与手册页的 manualInline 一致。
 */
fun uiInlineMarkdown(text: String): AnnotatedString {
    if (!text.contains("**") && !text.contains('`')) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        UiInlineMarkers.findAll(text).forEach { m ->
            if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
            val g = m.groups
            if (g[1] != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]!!.value) }
            } else {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(g[2]!!.value) }
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

private val UiInlineMarkers = Regex("\\*\\*([^*\\n]+)\\*\\*|`([^`\\n]+)`")
