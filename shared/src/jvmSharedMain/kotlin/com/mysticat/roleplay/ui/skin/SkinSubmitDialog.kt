package com.mysticat.roleplay.ui.skin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.components.WhaleHairField
import com.mysticat.roleplay.ui.tokens.WhaleSkinSpec

/**
 * 投稿皮肤弹窗：把当前生效的皮肤交到 GitHub Issue。
 *
 * 2026-10-02 用户退回过第一版：原来给的是「复制投稿内容 / 导出皮肤包」两条退路，原话
 * "**我复制什么，导出什么？意义不明**"——那两条要用户自己判断把什么发给谁，是把内部的双轨
 * 实现漏到了界面上，已撤。
 *
 * 2026-10-03 用户第二次退回（截图复核）：原版还留了个「逛逛皮肤中心」，点它实际只是
 * `showSkinSubmit=false; showSkinDialog=true` —— **关掉本弹窗、打回皮肤设置**，一圈回到原点。
 * 而皮肤设置里那句「更多皮肤将在皮肤中心（发现页）开放」指向的页面**根本没做**
 * （`DiscoverScreen.kt` 零个「皮肤」字样；主题设置里给皮肤中心留的那个 `"other"` 占位项
 * 也已在 2026-10-10 撤掉）。两个入口一起撤，等皮肤中心真上线再加回来。
 *
 * 同一轮还修掉一个假提示：正文超 `URL_LIMIT` 时的分支原来 toast「已复制」，但**全文件从来没有
 * 调过剪贴板**（`Prepared.body` 在 UI 层一次都没被读过）——用户粘出来是空的。现在真复制。
 */
@Composable
fun SkinSubmitDialog(
    spec: WhaleSkinSpec,
    onDismiss: () -> Unit
) {
    var oneLiner by remember(spec) { mutableStateOf("") }
    val version = remember { Platform.ui.appVersionName() }
    val prepared = remember(spec, oneLiner, version) {
        SkinSubmission.prepare(spec, oneLiner, version)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("投稿皮肤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "投稿的是当前生效的「${spec.name}」。我们审过后会合并上架，" +
                        "在此之前它不会出现在皮肤列表里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WhaleHairField(
                    value = oneLiner,
                    onValueChange = { oneLiner = it },
                    label = "想营造什么感觉（选填）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "点「去 GitHub 投稿」会打开一个**已经填好正文和标签**的新建 Issue 页面，" +
                        "你只要写几句为什么做它、提交就行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val opened = Platform.ui.openUrl(prepared.url)
                when {
                    !opened -> Platform.ui.toast("打不开链接，检查一下默认浏览器")
                    // 正文超长时 URL 里塞不下，改走剪贴板——**必须真的复制**，否则用户粘出来是空的
                    !prepared.bodyInUrl -> Platform.ui.copyToClipboard(
                        "皮肤投稿",
                        prepared.body,
                        "正文太长没法预填，已复制，粘进 Issue 即可"
                    )
                    else -> onDismiss()
                }
            }) { Text("去 GitHub 投稿") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}