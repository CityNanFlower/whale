package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.wheelHorizontalScroll
import com.mysticat.roleplay.ui.uiInlineMarkdown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.CharacterFormTags
import com.mysticat.roleplay.data.EditorField
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.OutputFormats
import com.mysticat.roleplay.data.PlayOptionChoices
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.writeFailureToast
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.CropDialog
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.GeneratedImageDialog
import com.mysticat.roleplay.ui.ImagePreviewDialog
import com.mysticat.roleplay.ui.ImageSizeOptions
import com.mysticat.roleplay.ui.PromptDialog
import com.mysticat.roleplay.ui.MixPresetBar
import com.mysticat.roleplay.ui.VoiceKindChips
import com.mysticat.roleplay.ui.voiceDisplayName
import com.mysticat.roleplay.ui.CardExportDialog
import com.mysticat.roleplay.ui.DesktopDragDrop
import com.mysticat.roleplay.ui.defaultBgImageSize
import com.mysticat.roleplay.ui.rememberBgCropAspect
import com.mysticat.roleplay.ui.rememberGallerySaver
import com.mysticat.roleplay.ui.rememberImagePicker
import kotlin.math.roundToInt

/**
 * 编辑页的四个分页（用户 2026-09-26 口径）：**基础信息常驻在页签之上**，其余内容按页签切换。
 *
 * 为什么基础信息不跟着分页：角色名 / 简介 / 分类是"这是谁"的三件套，改设定、换背景、挑音色时
 * 都可能顺手要改，收进某一页就得来回切。头像同一道理，跟基础信息并排常驻。
 */
private enum class EditorTab(val label: String) {
    SPEC("角色设定"),
    BOOK("世界书"),
    BACKGROUND("聊天背景"),
    VOICE("专属音色")
}

/**
 * 分页容器：只有当前页的内容会进列表。
 *
 * 同一个页可以出现多次调用（角色设定就分了三段——设定与开场白在世界书之前、卡信息与后置指令
 * 在背景之后、删除按钮收尾），它们的项按调用顺序拼成同一页，不会互相打乱。
 */
private fun LazyListScope.editorTabPage(
    page: EditorTab,
    current: EditorTab,
    content: LazyListScope.() -> Unit
) {
    if (page == current) content()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterEditorScreen(
    characterId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    /**
     * 桌面三栏：**内嵌进第三栏**。为真时顶栏换成"窄头部"（返回 / 标题 / 未保存提示 /
     * 导出 / 保存），正文一字不差。手机端恒为 false，行为零变化。
     */
    embedded: Boolean = false,
    /**
     * 外壳请求关闭（桌面内嵌形态专用，手机端恒为 0）：递增即走一次 [requestBack]。
     * 切页/点会话时由外壳发信号而不是自己清状态——脏不脏只有这里知道，直接清会静默丢改动。
     */
    closeSignal: Int = 0,
    vm: CharacterEditorViewModel = viewModel(factory = CharacterEditorViewModel.factory(characterId))
) {
    // 导出（图片卡 / 纯文本 / JSON）走统一的导出选择框：顶栏分享图标 → CardExportDialog
    var showExport by remember { mutableStateOf(false) }
    // 当前分页（存序号：枚举要自己写 Saver，序号不必）。默认停在「角色设定」。
    var editorTabIndex by remember { mutableStateOf(0) }
    val editorTab = EditorTab.entries[editorTabIndex]
    val pickAvatar = rememberImagePicker { uri ->
        uri?.let { vm.avatarUri = it; vm.avatarCropSource = null }
    }
    // 拖图到窗口 / Ctrl+V 贴截图 → 换这个角色的头像（与 pickAvatar 同一落点）。
    // 编辑器收进第三栏后页面级注册会跟着编辑器走：开着才接收，关了即撤。
    val editorImageSink: (String) -> Boolean = { uri ->
        vm.avatarUri = uri
        vm.avatarCropSource = null
        true
    }
    SideEffect { DesktopDragDrop.imageSink = editorImageSink }
    DisposableEffect(Unit) {
        onDispose { if (DesktopDragDrop.imageSink === editorImageSink) DesktopDragDrop.imageSink = null }
    }
    // 参考生图：先选参考图（视觉模型转译画风），随后弹出 AI 生成描述框
    val pickAvatarRef = rememberImagePicker { uri -> vm.setPendingRef(uri, "avatar") }
    // 背景分端：两端各一对「本地上传 / 参考图」选择器
    val pickBgPhone = rememberImagePicker { uri ->
        uri?.let { vm.setBg(BgTarget.Phone, it); vm.setCropSource(BgTarget.Phone, null) }
    }
    val pickBgDesktop = rememberImagePicker { uri ->
        uri?.let { vm.setBg(BgTarget.Desktop, it); vm.setCropSource(BgTarget.Desktop, null) }
    }
    val pickBgRefPhone = rememberImagePicker { uri -> vm.setPendingRef(uri, BgTarget.Phone.kind) }
    val pickBgRefDesktop = rememberImagePicker { uri -> vm.setPendingRef(uri, BgTarget.Desktop.kind) }
    // #6：点图片先预览原图 → 可「更换」或「编辑（裁剪）」。取值 "avatar" 或 BgTarget.kind
    var previewWhat by remember { mutableStateOf<String?>(null) }
    var cropWhat by remember { mutableStateOf<String?>(null) }

    // 问题 #27：有未保存的修改时，返回先弹「保存更改？」
    var showDiscardConfirm by remember { mutableStateOf(false) }
    fun requestBack() {
        if (vm.isDirty) showDiscardConfirm = true else onBack()
    }
    // 世界书管理：编辑/新建跳宝库。有未保存改动先走"放弃更改？"确认，
    // 确认后带着跳转请求退回（WorldBookJump 由宝库页/桌面外壳消费）；干净就直接退回。
    var pendingJump by remember { mutableStateOf<String?>(null) }
    val jumpToBook: (String?) -> Unit = { bookId ->
        pendingJump = bookId
        if (vm.isDirty) showDiscardConfirm = true
        else {
            WorldBookJump.pendingBookId = bookId
            onBack()
        }
    }
    // 返回键（安卓）/ Esc（桌面）都走这条：有未保存改动先问，干净就直接退回上一层
    WhaleBackHandler { requestBack() }
    // 外壳请求关闭：与上面的返回键走同一条路（有未保存改动就弹确认）
    LaunchedEffect(closeSignal) { if (closeSignal > 0) requestBack() }
    if (showDiscardConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showDiscardConfirm = false
                pendingJump = null  // 取消确认就撤掉跳转请求，别让下次"放弃更改"带上它
            },
            title = { Text("保存更改？") },
            text = { Text("当前角色卡有未保存的修改。") },
            confirmButton = {
                TextButton(onClick = { vm.save(onSaved, writeFailureToast(null, "保存角色")) }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(onClick = {
                    WorldBookJump.pendingBookId = pendingJump
                    onBack()
                }) { Text(if (pendingJump != null) "放弃更改并前往" else "放弃更改") }
            }
        )
    }

    // 导出选择框：图片卡（竖图）/ 纯文本 / chara_card_v2 JSON。
    // 传的是 vm.draftCard()——导出眼前这张卡（含未保存改动），与"保存"按钮无关。
    if (showExport) {
        CardExportDialog(card = vm.draftCard(), onDismiss = { showExport = false })
    }

    val doSave: () -> Unit = { vm.save(onSaved, writeFailureToast(null, "保存角色")) }

    Scaffold(
        // 内嵌形态下第三栏已经在窗口之内，系统栏 inset 不能再吃一次（否则正文顶部凭空多一条）
        contentWindowInsets = if (embedded) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            if (embedded) {
                // 窄头部：第三栏里没有整页顶栏的位置，标题、未保存提示与动作压成一行
                EditorPaneHeader(
                    title = if (vm.isNew) "新建角色" else "编辑角色",
                    dirty = vm.isDirty,
                    saving = vm.saving,
                    canExport = !vm.isNew,
                    onBack = ::requestBack,
                    onExport = { showExport = true },
                    onSave = doSave
                )
            } else {
                TopAppBar(
                    title = { Text(if (vm.isNew) "新建角色" else "编辑角色") },
                    navigationIcon = {
                        IconButton(onClick = ::requestBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showExport = true },
                            enabled = !vm.isNew
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "导出角色卡")
                        }
                        IconButton(onClick = doSave, enabled = !vm.saving) {
                            if (vm.saving) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = "保存")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("头像", style = MaterialTheme.typography.titleSmall) }
            // 头像与来源按钮**竖排**（用户 2026-09-26）：原来是按钮挤在头像右侧那一列里，
            // 三个等宽按钮分到的宽度太窄（手机上「本地图片」几乎贴着边），再加一个来源就没地方放。
            // 改成按钮独占整行后每颗约三分之一屏宽，以后「从素材库选择」落地直接多一颗即可。
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AsyncImage(
                        model = imageModel(vm.avatarUri),
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .clickable(enabled = vm.avatarUri != null) { previewWhat = "avatar" }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageSourceButtons(
                            busy = vm.generating,
                            generateLabel = "AI 生成",
                            onPickLocal = pickAvatar,
                            onGenerate = { vm.showPromptFor = "avatar" },
                            onPickReference = pickAvatarRef
                        )
                    }
                    if (vm.avatarUri != null) {
                        TextButton(
                            onClick = { vm.avatarUri = null; vm.avatarCropSource = null },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("移除") }
                    }
                }
            }

            item { Text("基础信息", style = MaterialTheme.typography.titleSmall) }
            item {
                OutlinedTextField(vm.name, { vm.name = it }, Modifier.fillMaxWidth(), label = { Text("角色名 *") })
            }
            item {
                OutlinedTextField(vm.tagline, { vm.tagline = it }, Modifier.fillMaxWidth(), label = { Text("一句话简介") })
            }
            item {
                Text("分类（可多选）", style = MaterialTheme.typography.bodySmall)
                val categoryScroll = rememberScrollState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .wheelHorizontalScroll(categoryScroll)
                        .horizontalScroll(categoryScroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 固定 + 自定义 + 其他(最后)；新增/删除/排序在「角色卡」页顶部的管理里做
                    CategoryManager.all().forEach { c ->
                        WhaleChip(
                            selected = c in vm.categories,
                            onClick = { vm.toggleCategory(c) },
                            label = { Text(c) }
                        )
                    }
                }
            }
            // ── v2 分层角色设定─────────────────────────────────────
            // 三个字段任一非空就走分层注入；全空则下面那段「人设（老版合并内容）」整块照旧注入。
            // 分开的理由：description 管事实、personality 管气质、示例管语气，混成一段会互相复读，
            // 模型也分不清"哪些是必须遵守的事实、哪些是语气示范"。
            //
            // 形态裁剪：工具形态下这两个字段的含义变成**风格参考**（人格是滤镜、不是身份），
            // 标签也跟着换（见 Engines.TOOL）；世界观与对话示例在工具形态下根本不出现
            //（TOOL 装配不用它们，留着只会让人以为写了就生效）。
            // 分页页签（用户 2026-09-26）：基础信息在上面常驻，这里切换其余内容。
            // 用胶囊而不是 M3 TabRow：两端本来就是这个观感（桌面 WhaleChip 还专门自绘过 hover）。
            item {
                val tabScroll = rememberScrollState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .wheelHorizontalScroll(tabScroll)
                        .horizontalScroll(tabScroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorTab.entries.forEach { t ->
                        WhaleChip(
                            selected = t == editorTab,
                            onClick = { editorTabIndex = t.ordinal },
                            label = { Text(t.label) }
                        )
                    }
                }
            }

            editorTabPage(EditorTab.SPEC, editorTab) {
                val spec = Engines.of(vm.formTag)
                if (EditorField.Persona in spec.editorFields) {
                    item { Text("角色设定", style = MaterialTheme.typography.titleSmall) }
                    item {
                        OutlinedTextField(
                            vm.description,
                            { vm.description = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(spec.personaLabel) },
                            minLines = 3,
                            placeholder = { Text("例：帝国第三军团将军，银发，左眼有一道旧疤；对陌生人冷淡。") }
                        )
                    }
                    item {
                        OutlinedTextField(
                            vm.personality,
                            { vm.personality = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(spec.personalityLabel) },
                            minLines = 2,
                            placeholder = { Text("例：外冷内热；认定的事不轻易改口；讨厌被人怜悯。") }
                        )
                    }
                    // 对话示例：工具形态不用它 —— 工具的"示例"是任务样例（见下面的使用示例）
                    if (EditorField.DialogueExamples in spec.editorFields) {
                        item {
                            OutlinedTextField(
                                vm.mesExample,
                                { vm.mesExample = it },
                                Modifier.fillMaxWidth(),
                                label = { Text("对话示例（可留空；用 <START> 分块，写「角色名:」「你:」）") },
                                minLines = 3,
                                placeholder = { Text("例：\n<START>\n将军: 站住。\n你: 我……\n将军: 别说话，跟我走。") }
                            )
                        }
                    }
                    // 老卡的人设是当年**导入时拼好的整块文本**（含【背景故事】【性格特点】小标题），
                    // 不做正则反向拆（拆坏用户手改过的内容）。这里原样展示，并说明它什么时候失效。
                    if (vm.persona.isNotBlank()) {
                        item {
                            OutlinedTextField(
                                vm.persona,
                                { vm.persona = it },
                                Modifier.fillMaxWidth(),
                                label = { Text("人设（老版合并内容）") },
                                minLines = 4,
                                supportingText = {
                                    Text(
                                        uiInlineMarkdown(
                                            "这是老版本拼在一起的整块人设。" +
                                                (if (vm.hasLayeredFields()) "上面填了「角色设定」的分层字段，所以**这一段不再注入**，可以整段清空。"
                                                else "上面的分层字段都为空，所以**它仍按原样注入**。")
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
                // ── 工具形态专属两栏──────────────────────────────────────────────
                // 不复用"人设 / 世界观"：工具作者看到那两个词会困惑，而且 persona 是以"这是你本人的设定"
                // 注入的，复用会主动诱发扮演。这栏写进 extensions.whale 保真往返。
                if (EditorField.TaskBrief in spec.editorFields) {
                    item { Text("任务说明", style = MaterialTheme.typography.titleSmall) }
                    item {
                        OutlinedTextField(
                            vm.taskBrief,
                            { vm.taskBrief = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("要它做什么（工具本身的职责）") },
                            minLines = 3,
                            placeholder = { Text("例：把用户发来的会议记录整理成待办清单，一条一事、去掉寒暄与重复。") },
                            supportingText = { Text("这段进系统提示词，作为这个工具的职责说明。") }
                        )
                    }
                    item {
                        OutlinedTextField(
                            vm.outputFormat,
                            { vm.outputFormat = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("输出格式要求（会贴到本轮消息末尾强制遵守）") },
                            minLines = 3,
                            placeholder = { Text("例：每行「- [ ] 事项（负责人）」，不要标题、不要总结段。") },
                            supportingText = { Text("写得越可核对越管用（「只给 10 个编号名字、不要别的字」这类）。") }
                        )
                    }
                    // 预设：最常用的四个形状，点一下填进上面那一栏，填完还能手改。
                    // 放在输入框**下面**而不是里面：它是"帮你起个头"的东西，不是那一栏的当前值展示。
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "常用格式（点一下填入，会替换本栏现有内容）",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutputFormats.presets.forEach { preset ->
                                    WhaleChip(
                                        selected = OutputFormats.selectedLabel(vm.outputFormat) == preset.label,
                                        onClick = { vm.outputFormat = preset.spec },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                        }
                    }
                }
                // ── 玩法形态专属三栏────────────────────────────
                // 规则是这一局唯一的"对错来源"（没有它，模型会为了顺着用户而放宽判定）；
                // 状态项告诉模型这一局要记住什么 —— 它进系统提示词，会话里那份【当前进度】也按它整理。
                if (EditorField.PlayRules in spec.editorFields) {
                    item { Text("玩法规则与状态", style = MaterialTheme.typography.titleSmall) }
                    item {
                        OutlinedTextField(
                            vm.playRules,
                            { vm.playRules = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("规则（硬约束：必须… / 不可…）") },
                            minLines = 4,
                            placeholder = {
                                Text(
                                    "例：\n只回答「是」或「不是」，不要报具体名字。\n答「不是」时必须把该候选排除，之后不再提。\n20 问内没猜中就算用户赢。"
                                )
                            },
                            supportingText = { Text("这是这一局唯一的对错来源：写得越可判定，模型越不会为了顺着用户而放宽规则。") }
                        )
                    }
                    item {
                        OutlinedTextField(
                            vm.playState,
                            { vm.playState = it },
                            Modifier.fillMaxWidth(),
                            label = { Text("要记的状态（进度 / 计分 / 已确认结论）") },
                            minLines = 3,
                            placeholder = { Text("例：已排除的候选、当前第几问、谁领先、还剩下什么没确认。") },
                            supportingText = { Text("它进系统提示词；聊天中 AI 也会按它自动更新会话里的「本局进度」（每 4 条一次）。") }
                        )
                    }
                    item {
                        Column {
                            Text(
                                "每轮给几个编号选项",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlayOptionChoices.forEach { n ->
                                    WhaleChip(
                                        selected = vm.playOptions == n,
                                        onClick = { vm.playOptions = n },
                                        label = { Text(if (n == 0) "不给选项" else "$n 个") }
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "「不给选项」适合自由作答的玩法（例如「千万不要说水」）；给了选项，用户回一个数字就能继续。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (EditorField.Scenario in spec.editorFields) {
                    item {
                        OutlinedTextField(
                            vm.scenario,
                            { vm.scenario = it },
                            Modifier.fillMaxWidth(),
                            // 玩法形态下它不是"此刻的处境"，而是这局玩法的前提（进了系统提示词，
                            // 所以会话里**不铺场景气泡**，见 ChatViewModel.newConversation）
                            label = { Text(if (vm.formTag == "play") "玩法设定（这一局的前提与背景）" else "世界观 / 当前场景") },
                            minLines = 3,
                            placeholder = {
                                Text(
                                    if (vm.formTag == "play") "例：这是一局「二十问猜人」，你想一个人物，我只用是 / 不是的问题来猜。"
                                    else "可选：故事发生在哪里，此刻是什么情境"
                                )
                            }
                        )
                    }
                }
                if (EditorField.Greeting in spec.editorFields) {
                    item {
                        OutlinedTextField(
                            vm.greeting,
                            { vm.greeting = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(spec.greetingLabel) },
                            minLines = 3,
                            placeholder = { Text(spec.greetingHint) }
                        )
                    }
                }
                // 多条开场白 / 多条使用示例：「多线」下新会话随机抽一条；工具下它们只是并列的任务样例
                //（点击即填入输入框），所以**多条示例不得触发「多线」徽标**；
                // 玩法下它们是并列的**开局引导**（随机抽一条＝这一局从哪里开始）
                if (vm.formTag == "experience" || vm.formTag == "tool" || vm.formTag == "play") {
                    val tool = vm.formTag == "tool"
                    val play = vm.formTag == "play"
                    item {
                        Text(
                            when {
                                tool -> "其他使用示例（各是一条任务样例，点击即填入输入框）"
                                play -> "其他开局引导（开局方向不同，新会话随机抽一条）"
                                else -> "其他开场白（开局方向不同，新会话随机抽一条）"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    itemsIndexed(vm.extraGreetings) { i, g ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                g,
                                { vm.updateExtraGreeting(i, it) },
                                Modifier.weight(1f),
                                label = {
                                    Text(
                                        when {
                                            tool -> "使用示例 ${i + 2}"
                                            play -> "开局引导 ${i + 2}"
                                            else -> "开场白 ${i + 2}"
                                        }
                                    )
                                },
                                minLines = 2
                            )
                            IconButton(onClick = { vm.removeExtraGreeting(i) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除该开场白")
                            }
                        }
                    }
                    item {
                        TextButton(onClick = vm::addExtraGreeting) {
                            Text(
                                when {
                                    tool -> "+ 添加一条使用示例"
                                    play -> "+ 添加一条开局引导"
                                    else -> "+ 添加一条开场白"
                                }
                            )
                        }
                    }
                }
                // 形态标签：陪伴（默认）/ 体验（多开局、剧情玩法）
                item {
                    Column {
                        Text("形态标签", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CharacterFormTags.forEach { (label, value) ->
                                WhaleChip(
                                    selected = vm.formTag == value,
                                    onClick = { vm.updateFormTag(value) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(
                            when (vm.formTag) {
                                "tool" -> "工具：用户发来的是待处理素材、你产出的是可直接拿走的成品（不扮演）。"
                                "play" -> "玩法：陪玩一局有规则的玩法（猜谜 / 对弈 / 问答）。规则是硬约束、" +
                                    "每轮可给编号选项、这局的进度它自己记；不做主线与存档（那是「故事」）。"
                                "experience" -> "多线：多种开局方向，新会话随机抽一条开场白。"
                                else -> "陪伴：单开场白的沉浸式角色扮演。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

            }

            // 世界书：与上面几段同属"这个角色带什么资料"，单独一页。
            // 编辑器不再内联改书，只留 启用 / 编辑 / 新建；跳转经 jumpToBook（脏确认＋WorldBookJump 请求），宝库侧消费
            editorTabPage(EditorTab.BOOK, editorTab) {
                item { WorldBookManageSection(vm, jumpToBook) }
            }

            // 聊天背景：手机端与桌面端各一份（见下面那段说明）
            editorTabPage(EditorTab.BACKGROUND, editorTab) {
                item { Text("默认聊天背景", style = MaterialTheme.typography.titleSmall) }
                // A 批次（用户 2026-09-18）：两端窗口形状不同（手机竖屏 / 桌面横屏窗口），各存一份，
                // 端点各自的"设置背景"只动本端那份。只设了一份时两端都用它（老卡即此形态）。
                item {
                    Text(
                        "手机与桌面的窗口形状不同，各存一份；只设一份时两端都用它。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    BackgroundSlotEditor(
                        target = BgTarget.Phone,
                        uri = vm.slotBg(BgTarget.Phone),
                        inherited = false,
                        busy = vm.generating,
                        onPreview = { previewWhat = BgTarget.Phone.kind },
                        onPickLocal = pickBgPhone,
                        onGenerate = { vm.showPromptFor = BgTarget.Phone.kind },
                        onPickReference = pickBgRefPhone,
                        onRemove = {
                            vm.setBg(BgTarget.Phone, "")
                            vm.setCropSource(BgTarget.Phone, null)
                        }
                    )
                }
                item {
                    BackgroundSlotEditor(
                        target = BgTarget.Desktop,
                        uri = vm.slotBg(BgTarget.Desktop),
                        // 桌面端那份没单独设过、正用着手机端那张 —— 必须说出来，否则用户看到的图和
                        // "桌面上显示的是哪张"对不上，也不知道「移除」到底移掉了什么
                        inherited = vm.backgroundUriDesktop == null && !vm.backgroundUri.isNullOrBlank(),
                        busy = vm.generating,
                        onPreview = { previewWhat = BgTarget.Desktop.kind },
                        onPickLocal = pickBgDesktop,
                        onGenerate = { vm.showPromptFor = BgTarget.Desktop.kind },
                        onPickReference = pickBgRefDesktop,
                        onRemove = {
                            // 写空串而不是 null：null 是"没设过"，那样又会回退到手机端那张
                            vm.setBg(BgTarget.Desktop, "")
                            vm.setCropSource(BgTarget.Desktop, null)
                        }
                    )
                }
            }

            // ── 卡信息 + v2 原样字段（仍属「角色设定」页）────────────
            editorTabPage(EditorTab.SPEC, editorTab) {
                item { Text("卡信息", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        vm.creatorNotes,
                        { vm.creatorNotes = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("说明（给玩的人看，不进提示词）") },
                        minLines = 2,
                        placeholder = { Text("例：适合慢慢聊的日常向角色；开局从深夜电台开始。") }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            vm.creator,
                            { vm.creator = it },
                            Modifier.weight(1f),
                            label = { Text("作者") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            vm.characterVersion,
                            { vm.characterVersion = it },
                            Modifier.weight(1f),
                            label = { Text("卡版本") },
                            singleLine = true
                        )
                    }
                }
                item { Text("后置指令", style = MaterialTheme.typography.titleSmall) }
                item {
                    OutlinedTextField(
                        vm.postHistory,
                        { vm.postHistory = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("每轮都要遵守的补充要求（v2 post_history_instructions）") },
                        minLines = 3,
                        supportingText = {
                            Text("贴在本轮最后一条消息末尾——这是最贴近生成点的位置，比写在人设里更容易被遵守。可留空。")
                        }
                    )
                }
                item {
                    OutlinedTextField(
                        vm.systemPrompt,
                        { vm.systemPrompt = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("原卡自带的系统提示词（只保存，不生效）") },
                        minLines = 2,
                        supportingText = {
                            Text(uiInlineMarkdown("别人的卡常把系统提示词写在这里。鲸鱼的角色扮演提示词由 App 统一控制（视角约定等），" +
                                "所以这一段**只原样保存与导出、不参与对话**，避免两套规则互相打架。"))
                        }
                    )
                }
            }

            // 角色专属音色：单独一页
            editorTabPage(EditorTab.VOICE, editorTab) { characterVoiceSection(vm) }

            editorTabPage(EditorTab.SPEC, editorTab) {
                if (!vm.isNew) {
                    item {
                        Button(
                            onClick = { vm.showDeleteConfirm = true },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("删除这个角色（连同全部会话）")
                        }
                    }
                }
            }
        }
    }

    // #6：点图片先看原图 → 更换 / 编辑（裁剪）
    previewWhat?.let { what ->
        val target = BgTarget.ofKind(what)
        val cur = if (what == "avatar") vm.avatarUri else target?.let { vm.slotBg(it) }
        if (!cur.isNullOrBlank()) {
            val saveGallery = rememberGallerySaver()
            if (cropWhat == what) {
                // 问题 #13：裁剪基准始终是最初原图——裁完显示新图，但再进「编辑」仍从原图重裁
                val cropSource = if (what == "avatar") {
                    (vm.avatarCropSource ?: cur).also { vm.avatarCropSource = it }
                } else {
                    val t = target!!
                    (vm.cropSourceOf(t) ?: cur).also { vm.setCropSource(t, it) }
                }
                CropDialog(
                    sourceUri = cropSource,
                    // 分端后裁剪框按**该端**的窗口形状：本端取真实屏幕/窗口比例，另一端给名义值
                    aspect = if (what == "avatar") 1f else rememberBgCropAspect(target!!),
                    onCropped = { newUri ->
                        if (what == "avatar") vm.avatarUri = newUri else vm.setBg(target!!, newUri)
                        cropWhat = null
                    },
                    onDismiss = { cropWhat = null }
                )
            } else {
                ImagePreviewDialog(
                    title = if (what == "avatar") "头像" else "${target!!.label}聊天背景",
                    uri = cur,
                    onReplace = {
                        previewWhat = null
                        if (what == "avatar") pickAvatar() else if (target == BgTarget.Desktop) pickBgDesktop() else pickBgPhone()
                    },
                    onEdit = { cropWhat = what },
                    onSave = { saveGallery(cur) },
                    onDismiss = { previewWhat = null }
                )
            }
        }
    }

    if (vm.showPromptFor != null) {
        val kind = vm.showPromptFor!!
        val target = BgTarget.ofKind(kind)
        PromptDialog(
            title = if (kind == "avatar") "用 AI 生成头像" else "用 AI 生成${target!!.label}聊天背景",
            // 问题 #28：预填上次的提示词，返回来调整时不用重写；
            // 这份草稿**落盘**（`drafts.json`）——退出应用再进来还在这里
            initial = vm.promptFor(kind),
            placeholder = if (kind == "avatar") {
                "描述你想要的立绘风格，例如：唯美二次元，紫色长发少女，微笑，胸像，浅色背景"
            } else if (target == BgTarget.Desktop) {
                "描述聊天背景场景，例如：黄昏的旧书店窗外，暖色光线，横屏构图"
            } else {
                "描述聊天背景场景，例如：黄昏的旧书店窗外，暖色光线，竖屏构图"
            },
            onConfirm = { prompt, size -> vm.generate(kind, prompt, size) },
            onDismiss = { vm.showPromptFor = null; vm.promptDraft = null },
            // 输入即存：用户写完提示词可能直接退出应用，下次打开要原样看到
            onTextChange = { vm.savePromptDraft(kind, it) },
            // 叉号：明确清空（提示词不自动删，只有这里能删）
            onClear = { vm.clearPromptDraft(kind) },
            // #6：生图尺寸在这里选（头像默认方形，背景按端：手机竖屏 / 桌面横屏）
            sizeOptions = ImageSizeOptions,
            defaultSize = if (kind == "avatar") "1024x1024" else defaultBgImageSize(target!!),
            // 0.1.2：懒人路径——不用自己写描述，让 AI 按角色卡设定起草一版
            draftLabel = if (kind == "avatar") "按角色设定起草提示词" else "按世界观起草背景提示词",
            drafting = vm.draftingPrompt,
            draftText = vm.promptDraft,
            onDraft = { vm.draftPrompt(kind) }
        )
    }

    if (vm.showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.showDeleteConfirm = false },
            title = { Text("删除角色？") },
            text = { Text("该角色的所有会话记录、头像与背景图也会一并删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.showDeleteConfirm = false; vm.delete(onSaved, writeFailureToast(null, "删除角色")) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { vm.showDeleteConfirm = false }) { Text("取消") } }
        )
    }

    // 问题 #8：AI 生图结果先预览，再由用户选 保存 / 应用 / 重试
    vm.pendingUri?.let { uri ->
        GeneratedImageDialog(
            title = if (vm.pendingIsAvatar) "头像生成结果" else "${vm.pendingTarget.label}聊天背景生成结果",
            uri = uri,
            cropAspect = if (vm.pendingIsAvatar) 1f else rememberBgCropAspect(vm.pendingTarget),
            onApply = { final -> vm.applyPendingImage(final) },
            onRetry = { vm.retryPendingImage() },
            onDismiss = { vm.discardPendingImage() }
        )
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}
