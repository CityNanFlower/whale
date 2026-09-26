package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysticat.roleplay.data.CustomProvider
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.noArgViewModelFactory

/**
 * API 设置页（2026-09-15 重构后的结构）：
 *
 * - **API 配置**：只管"供应商 + Key"（一家一份 Key，加密存本机）。胶囊末尾可新增自定义供应商。
 * - **对话模型 / 创作模型 / 生图模型**：三页结构一致 —— 供应商胶囊 + 选模型与参数 + 接入能力卡片；
 *   每页**各自独立选供应商**，选到哪家就用哪家的 Key。
 *   该供应商没存 Key 时，该页的模型与参数全部置灰并给出提示（用户 2026-09-15 要求）。
 */
/**
 * 设置页的四个分段。
 *
 * 原来 tab 是裸字符串，页面用 `when (tab) { "api" -> …; "chat" -> …; "creation" -> …; else -> 生图页 }`，
 * 于是**任何写错/未知的值都会被当成生图页渲染**（改动一处拼写就会静默串页，且编译器不报错）。
 * 改成枚举后 `when` 是穷尽的，漏写一个分支直接编译失败。
 */
enum class SettingsTab(val label: String) {
    API("API 配置"),
    CHAT("对话模型"),
    CREATION("创作模型"),
    IMAGE("生图模型")
    // 语音朗读（v1 的系统语音参数 + v2 的供应商合成）2026-09-16 **搬去独立的
    // [VoiceScreen]**：那一页是纯 API 配置，而"用什么声音读、朗读偏好"是使用偏好 ——
    // 入口在「我的 → 外观与设置 → 语音朗读与语音输入」。
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /**
     * 桌面三栏：**内嵌进第三栏**。为真时不套 Scaffold/顶栏、也不画四个分段胶囊——
     * 分段由第二栏（[DesktopProfileRail]）驱动（`vm.selectTab`），这里只画内容 + 一个窄头部（保存按钮）。
     * 手机端恒为 false，行为零变化。
     */
    embedded: Boolean = false,
    vm: SettingsViewModel = viewModel(factory = noArgViewModelFactory { SettingsViewModel() })
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var editingCustom by remember { mutableStateOf<CustomProvider?>(null) }
    var showCustomEditor by remember { mutableStateOf(false) }

    // 进页面重读一次磁盘：本 VM 活过整场会话，而设置别的页面也能改（见 reloadFromDisk 的说明）。
    // 脏着就不读，免得把用户正在编辑的内容冲掉。
    LaunchedEffect(Unit) { vm.reloadFromDisk() }

    fun requestBack() {
        if (vm.isDirty) showDiscardConfirm = true else onBack()
    }
    // 返回键（安卓）/ Esc（桌面）都走这条：有未保存修改先问，干净就直接退回上一层。
    // ⚠ 必须常开——只在脏时注册的话，桌面上 Esc 没有任何人接（桌面没有导航图兜底）。
    WhaleBackHandler { requestBack() }

    // 正文：手机/整页形态套在 Scaffold 里，桌面内嵌形态套在窄头部下面（同一份，不复制）
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 分段导航：API 配置 / 对话模型 / 创作模型 / 生图模型 ──
            // 桌面内嵌时由第二栏承担（那里是"设置分组"列表），这里不再重复画一排胶囊
            if (!embedded) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SettingsTab.entries.forEach { t ->
                            WhaleChip(
                                selected = vm.tab == t,
                                onClick = { vm.selectTab(t) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            }
            vm.cryptoNotice?.let { notice ->
                item {
                    Text(
                        "⚠️ $notice",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            vm.autoConfigNotice?.let { notice ->
                item {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when (vm.tab) {
                SettingsTab.API -> apiSettingsSection(
                    vm = vm,
                    onEdit = { editingCustom = it; showCustomEditor = true },
                    onAddCustom = { editingCustom = null; showCustomEditor = true }
                )
                SettingsTab.CHAT -> chatModelSection(vm, onEdit = { editingCustom = it; showCustomEditor = true })
                SettingsTab.CREATION -> creationModelSection(vm, onEdit = { editingCustom = it; showCustomEditor = true })
                SettingsTab.IMAGE -> imageModelSection(vm, onEdit = { editingCustom = it; showCustomEditor = true })
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            // 窄头部：退出键 + 分段名 + 未保存提示 + 保存按钮（原来挂在整页顶栏右侧）
            EmbeddedSettingsHeader(
                title = vm.tab.label,
                dirty = vm.isDirty,
                onSave = { vm.save() },
                onBack = ::requestBack
            )
            body(PaddingValues(0.dp))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    // 标题跟随当前分段（000037）：这一页是"API 配置 + 三个模型页"的合集，
                    // 原来顶栏写死「API 设置」、而「我的」入口又写「模型设置」，看起来像进错了页
                    title = { Text(vm.tab.label) },
                    navigationIcon = {
                        IconButton(onClick = ::requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.save(); onBack() }) {
                            Icon(Icons.Filled.Check, contentDescription = "保存")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding -> body(padding) }
    }

    // 有未保存的修改时退出：与角色编辑页统一口径（保存并退出 / 放弃更改）
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("保存更改？") },
            // 四个分段共用同一份编辑态，用户可能只改了其中一处，所以不点名具体分段
            text = { Text("设置里有未保存的修改。") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; vm.save(); onBack() }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("放弃更改") }
            }
        )
    }

    // 自定义供应商编辑（新增/编辑/删除）
    if (showCustomEditor) {
        CustomProviderDialog(
            initial = editingCustom,
            onSave = { vm.upsertCustomProvider(it); showCustomEditor = false },
            onDelete = { vm.deleteCustomProvider(it); showCustomEditor = false },
            onDismiss = { showCustomEditor = false }
        )
    }

    // 生图连接测试确认（按张计费）
    if (vm.showImageTestConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = vm::cancelImageTest,
            title = { Text("测试生图模型？") },
            text = { Text("将实际生成一张测试图（按张计费，不限尺寸）。继续吗？") },
            confirmButton = {
                TextButton(onClick = vm::testImageModel) { Text("生成测试图") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelImageTest) { Text("取消") }
            }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}
