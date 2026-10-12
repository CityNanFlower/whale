package com.mysticat.roleplay.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.tokens.WhaleShapes

/**
 * 动作类浮层的统一壳（R3 交互语法）：确认删除、清空这类"要用户拍板才执行"的弹层都从这里走。
 *
 * 两端各按自己的浮层语法落地：
 * - **手机＝底部 Sheet**（M3 `ModalBottomSheet`，一整块从底部升起，拇指够得着，拖把手或点外部关闭）；
 * - **桌面＝居中面板**（项目自己的浮层语言：surface 底 ＋ 发丝描边 ＋ [WhaleShapes.r3] 圆角，
 *   与右击菜单同族——不再是 M3 `AlertDialog` 那张灰卡）。
 *
 * 口径：
 * - 动作行里**确认排在取消之后**（与右击菜单"删除排末位"的次序约定一致）；
 * - 危险动作（[confirmDanger]）用 error 色，颜色全部来自主题令牌，不写字面色；
 * - **通知类**（导入成功/失败这类"告诉你结果"的单键浮层）走 [WhaleNoticeSheet]——关闭就是它的
 *   唯一动作；**信息类弹层**（说明、详情、更新日志）不走这里，仍用 `AlertDialog`——判断标准是
 *   "这个浮层的主操作是执行一个动作，还是阅读一段内容"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhaleActionSheet(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmDanger: Boolean = true,
    confirmEnabled: Boolean = true,
    // 为 null ＝ 不渲染取消钮（通知类单键浮层用，见 [WhaleNoticeSheet]）
    dismissLabel: String? = "取消",
    // 「取消」钮另有含义时给这个：手势关闭仍走 [onDismiss]（＝什么都不改），
    // 例如"保存更改？"的取消钮其实是「放弃更改」——点外/拖走应当留在页内。
    onDismissButton: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (isDesktopLayout) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = modifier.widthIn(min = 320.dp, max = 440.dp),
                shape = WhaleShapes.r3,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                WhaleSheetBody(
                    title = title,
                    confirmLabel = confirmLabel,
                    confirmDanger = confirmDanger,
                    confirmEnabled = confirmEnabled,
                    dismissLabel = dismissLabel,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    onDismissButton = onDismissButton,
                    stacked = false,
                    content = content
                )
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            WhaleSheetBody(
                title = title,
                confirmLabel = confirmLabel,
                confirmDanger = confirmDanger,
                confirmEnabled = confirmEnabled,
                dismissLabel = dismissLabel,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                onDismissButton = onDismissButton,
                stacked = true,
                modifier = Modifier.navigationBarsPadding(),
                content = content
            )
        }
    }
}

/** 简单确认：一句说明 ＋ 确认/取消。复杂内容（明细列表、表单）直接用 [WhaleActionSheet]。 */
@Composable
fun WhaleConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    // 默认按危险动作处理（R3 收编的都是删除/清空类）；"要花钱的动作"这类给 false 走主题主色
    confirmDanger: Boolean = true,
    // 默认「取消」；"保存更改？"这类取消钮另有含义的浮层改这里，并给 [onDismissButton]；
    // 为 null ＝ 不渲染取消钮（首启"重启以生效"这类强制二选一的浮层用）
    dismissLabel: String? = "取消",
    onDismissButton: (() -> Unit)? = null
) {
    WhaleActionSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmDanger = confirmDanger,
        dismissLabel = dismissLabel,
        onDismissButton = onDismissButton
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 通知类浮层（236②收编）：导入成功/失败这类"告诉你结果"的浮层——没有可取消的决策，关闭＝确认。 */
@Composable
fun WhaleNoticeSheet(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    confirmLabel: String = "好的"
) {
    WhaleActionSheet(
        title = title,
        onDismiss = onConfirm,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmDanger = false,
        dismissLabel = null
    ) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WhaleSheetBody(
    title: String,
    confirmLabel: String,
    confirmDanger: Boolean,
    confirmEnabled: Boolean,
    dismissLabel: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDismissButton: (() -> Unit)? = null,
    stacked: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
        val confirmColors = if (confirmDanger) {
            ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError)
        } else {
            ButtonDefaults.buttonColors()
        }
        if (stacked) {
            // 手机：动作行上下堆叠、全宽——底部 Sheet 的拇指语法
            Button(onClick = onConfirm, enabled = confirmEnabled, colors = confirmColors, modifier = Modifier.fillMaxWidth()) {
                Text(confirmLabel)
            }
            if (dismissLabel != null) {
                TextButton(onClick = onDismissButton ?: onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(dismissLabel, color = cs.onSurfaceVariant)
                }
            }
        } else {
            // 桌面：动作行右对齐（对话框惯例），确认仍排末位
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (dismissLabel != null) {
                    TextButton(onClick = onDismissButton ?: onDismiss) { Text(dismissLabel, color = cs.onSurfaceVariant) }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = onConfirm, enabled = confirmEnabled, colors = confirmColors) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
