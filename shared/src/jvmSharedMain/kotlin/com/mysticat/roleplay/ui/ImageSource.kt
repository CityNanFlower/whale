package com.mysticat.roleplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mysticat.roleplay.data.MaterialKind
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.screens.MaterialsScreen

/**
 * 「选一张图」的两条来源。素材库此前只有"存"没有"取"——想拿库里那张生成图当头像，
 * 用户得先「保存到相册」、再走系统相册选一遍，等于绕了个圈（图还得在相册里多留一份）。
 *
 * 这里补上第二来源，并且**回调签名与 [rememberImagePicker] 一致**（本地路径 / null＝取消），
 * 所以既有调用点只是换个函数名，落点、闸门、后续处理一概不动。
 *
 * 素材那条走**复制**（[Repository.copyMaterialToImages]）：素材与使用者各活各的，
 * 之后删素材不会抽掉正在用的头像／背景图。
 */

/**
 * 只开"从素材库选择"这一条来源（[onPicked] 收到的是复制进 `images/` 的本地路径）。
 *
 * 用在已经有明确"素材库"入口的地方（会话加号面板里那一格），不必再问一次来源。
 * 返回触发函数，调用时机与 [rememberImagePicker] 相同（组合期创建、点击时调用）。
 */
@Composable
fun rememberMaterialPicker(onPicked: (String?) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                MaterialsScreen(
                    onBack = { open = false },
                    pickKind = MaterialKind.IMAGE,
                    onPick = { m ->
                        val path = Repository.copyMaterialToImages(m.id)
                        open = false
                        if (path != null) onPicked(path) else showToast("这条素材的文件不见了，换一条试试", long = true)
                    }
                )
            }
        }
    }
    return { open = true }
}

/**
 * 「换图」的统一入口：先挑**来源**（相册 / 素材库），再各走各的路。
 *
 * 两处刻意与 [rememberImagePicker] 保持同构，方便既有调用点原地替换：
 * 返回触发函数、回调 `(本地路径 / null＝取消)`、都在组合期创建。
 */
@Composable
fun rememberImageSourcePicker(onPicked: (String?) -> Unit): () -> Unit {
    var showSources by remember { mutableStateOf(false) }
    // 两个选择器都在组合期创建（Android 侧要挂 ActivityResult 启动器），不能等到点了才建
    val pickFromGallery = rememberImagePicker(onPicked)
    val pickFromLibrary = rememberMaterialPicker(onPicked)
    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text("选择图片来源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SourceRow(
                        icon = Icons.Filled.Image,
                        label = "从相册选图",
                        hint = "本机相册 / 文件里的图片"
                    ) {
                        showSources = false
                        pickFromGallery()
                    }
                    SourceRow(
                        icon = Icons.Filled.PhotoLibrary,
                        label = "从素材库选择",
                        hint = "AI 生成过、存下来的图，可反复取用"
                    ) {
                        showSources = false
                        pickFromLibrary()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSources = false }) { Text("取消") } }
        )
    }
    return { showSources = true }
}

@Composable
private fun SourceRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
