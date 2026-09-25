package com.mysticat.roleplay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.imageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 图片预览（问题 #6）。
 * 点「用户」页头像、角色编辑页的头像 / 聊天背景时先看原图，再决定「更换」或「编辑（裁剪）」。
 * 右上角的 × 与点击周围空白都等于 [onDismiss]。
 */
@Composable
fun ImagePreviewDialog(
    title: String,
    uri: String,
    onReplace: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onApply: (() -> Unit)? = null,
    /** 保存到系统相册（问题 #12：AI 生成的背景图/头像此前只能应用，存不下来） */
    onSave: (() -> Unit)? = null,
    /** 问题 #28：返回上一级（如从"确认使用"回到生成结果预览），空则不显示返回箭头 */
    onBack: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级")
                        }
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    // 右上角关闭
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                Spacer(Modifier.height(10.dp))
                AsyncImage(
                    model = imageModel(uri),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSave != null) {
                        OutlinedButton(
                            onClick = onSave,
                            modifier = Modifier.weight(1f)
                        ) { Text("保存") }
                    }
                    if (onReplace != null) {
                        OutlinedButton(
                            onClick = onReplace,
                            modifier = Modifier.weight(1f)
                        ) { Text("更换") }
                    }
                    if (onEdit != null) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f)
                        ) { Text("编辑") }
                    }
                    if (onApply != null) {
                        Button(
                            onClick = onApply,
                            modifier = Modifier.weight(1f)
                        ) { Text("应用") }
                    }
                }
            }
        }
    }
}

/**
 * 裁剪编辑器（问题 #6）。
 * 图片可拖动、双指缩放；中间是固定比例的裁剪框，框外压暗。确定后把框内区域裁出来存成新的本地图片。
 *
 * @param aspect 裁剪框宽高比（头像 = 1，聊天背景 = 0.75 竖屏）
 * @param onCropped 回调裁剪后的本地文件路径
 */
@Composable
fun CropDialog(
    sourceUri: String,
    aspect: Float,
    onCropped: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bitmap by produceState<ImageBitmap?>(initialValue = null, sourceUri) {
        value = Platform.ui.decodeImageBitmap(sourceUri)
    }

    // 用户的手势变换
    var userScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF101018),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "裁剪",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                    }
                }

                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val density = LocalDensity.current
                    val viewW = with(density) { maxWidth.toPx() }
                    val viewH = with(density) { maxHeight.toPx() }
                    // 裁剪框：占满短边的 86%
                    val frameW: Float
                    val frameH: Float
                    if (viewW / viewH > aspect) {
                        frameH = viewH * 0.86f
                        frameW = frameH * aspect
                    } else {
                        frameW = viewW * 0.86f
                        frameH = frameW / aspect
                    }

                    val bmp = bitmap
                    if (bmp == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else {
                        val base = minOf(viewW / bmp.width, viewH / bmp.height)

                        Box(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(bmp) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        // 下限 0.3：允许把图缩得比裁剪框还小（用户反馈初始只能放大不能缩小）；
                                        // 缩太小时裁剪会自动夹回图片实际范围，不会裁出黑边
                                        userScale = (userScale * zoom).coerceIn(0.3f, 8f)
                                        offset += pan
                                    }
                                }
                                // 桌面：滚轮缩放（2026-09-17 用户反馈"裁剪图片不能再用双指了"）。
                                // detectTransformGestures 的 zoom 来自多指间距变化，鼠标滚轮根本不产生这个事件，
                                // 所以桌面此前只能拖动、不能缩放。滚轮是与它并列的独立事件，只能另起一个 pointerInput。
                                // 手机端不挂（无滚轮事件，且保持 Android 行为零变化）。
                                .then(
                                    if (isDesktopLayout) {
                                        Modifier.pointerInput(bmp) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val e = awaitPointerEvent()
                                                    if (e.type != PointerEventType.Scroll) continue
                                                    val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                                    if (dy == 0f) continue
                                                    e.changes.forEach { it.consume() }
                                                    val step = if (dy < 0f) 1.1f else 1f / 1.1f
                                                    userScale = (userScale * step).coerceIn(0.3f, 8f)
                                                }
                                            }
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "待裁剪图片",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = userScale
                                        scaleY = userScale
                                        translationX = offset.x
                                        translationY = offset.y
                                        transformOrigin = TransformOrigin.Center
                                    }
                            )
                            // 框外压暗 + 白色边框
                            Canvas(Modifier.fillMaxSize()) {
                                val l = (size.width - frameW) / 2f
                                val t = (size.height - frameH) / 2f
                                val r = l + frameW
                                val b = t + frameH
                                val mask = Color(0xAA000000)
                                drawRect(mask, Offset.Zero, Size(size.width, t))
                                drawRect(mask, Offset(0f, b), Size(size.width, size.height - b))
                                drawRect(mask, Offset(0f, t), Size(l, frameH))
                                drawRect(mask, Offset(r, t), Size(size.width - r, frameH))
                                drawRect(
                                    Color.White,
                                    Offset(l, t),
                                    Size(frameW, frameH),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }

                        // 确定：把视图坐标的裁剪框映射回图片坐标
                        fun crop() {
                            val k = base * userScale
                            if (k <= 0f) return
                            // 图片以视图中心为原点缩放 k 倍、再整体平移了 offset，
                            // 所以换回原图坐标时要 **减去** offset。
                            // 原实现写成了加（cx = viewW/2 + offset.x），拖动越多裁得越偏 —— 问题 #13。
                            val srcLeft = (-frameW / 2f - offset.x) / k + bmp.width / 2f
                            val srcTop = (-frameH / 2f - offset.y) / k + bmp.height / 2f
                            val srcW = frameW / k
                            val srcH = frameH / k
                            val x = srcLeft.toInt().coerceIn(0, maxOf(0, bmp.width - 1))
                            val y = srcTop.toInt().coerceIn(0, maxOf(0, bmp.height - 1))
                            val w = srcW.toInt().coerceIn(1, bmp.width - x)
                            val h = srcH.toInt().coerceIn(1, bmp.height - y)
                            saving = true
                            scope.launch {
                                // 裁剪本体用共享的 Compose Canvas 完成（src 矩形 → 新位图），
                                // 只有 PNG 编码这最后一步走平台（解码/编码两步走平台）
                                val path = withContext(Dispatchers.IO) {
                                    runCatching {
                                        // 等价于原来的 Bitmap.createBitmap(bmp, x, y, w, h)：
                                        // 源图平移 (-x,-y) 后整体绘制，用 (0,0,w,h) 的裁剪窗口只留目标矩形。
                                        // 注意不能用 drawImage 的 srcSize 重载——IntSize 在 Android 住
                                        // geometry、桌面住 unit，共享层没有统一的 import 可用。
                                        val cut = ImageBitmap(w, h)
                                        CanvasDrawScope().draw(
                                            Density(1f),
                                            LayoutDirection.Ltr,
                                            Canvas(cut),
                                            Size(w.toFloat(), h.toFloat())
                                        ) {
                                            clipRect(0f, 0f, w.toFloat(), h.toFloat()) {
                                                translate(-x.toFloat(), -y.toFloat()) {
                                                    drawImage(bmp)
                                                }
                                            }
                                        }
                                        Platform.ui.encodePng(cut)?.let { Repository.saveImageBytes(it, "png") }
                                    }.getOrNull()
                                }
                                saving = false
                                if (path == null) error = "裁剪失败，请重试" else onCropped(path)
                            }
                        }

                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            error?.let {
                                Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(
                                if (isDesktopLayout) "拖动图片调整位置，滚轮缩放" else "拖动图片调整位置，双指缩放",
                                color = Color(0xFFBBBBD0),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TextButton(
                                    onClick = { userScale = 1f; offset = Offset.Zero },
                                    modifier = Modifier.weight(1f)
                                ) { Text("复位", color = Color.White) }
                                OutlinedButton(
                                    onClick = onDismiss,
                                    enabled = !saving,
                                    modifier = Modifier.weight(1f)
                                ) { Text("取消", color = Color.White) }
                                Button(
                                    onClick = { crop() },
                                    enabled = !saving,
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (saving) "处理中…" else "确定") }
                            }
                        }
                    }
                }
            }
        }
    }
}

