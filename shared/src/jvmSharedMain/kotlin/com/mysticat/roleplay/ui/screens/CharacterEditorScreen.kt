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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.ArrowScrollRow
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.WhalePageTopBar
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.mysticat.roleplay.data.ModelScene
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.ProviderProfiles
import com.mysticat.roleplay.data.CharacterFormTags
import com.mysticat.roleplay.data.EditorField
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.OutputFormats
import com.mysticat.roleplay.data.PlayOptionChoices
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.writeFailureToast
import kotlinx.coroutines.launch
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
import com.mysticat.roleplay.ui.rememberImageSourcePicker
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
    // 形态不注入世界书时不列这一页（工具：挂了也不会生效，列出来就是让人白挂）。
    // 判据取自引擎（`EngineSpec.worldBook`），与命中入口、装配器是同一份。
    val tabs = remember(vm.formTag) {
        EditorTab.entries.filter { it != EditorTab.BOOK || Engines.of(vm.formTag).worldBook }
    }
    val editorTab = tabs.getOrElse(editorTabIndex) { tabs.first() }
    val pickAvatar = rememberImageSourcePicker { uri ->
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
    val pickAvatarRef = rememberImageSourcePicker { uri -> vm.setPendingRef(uri, "avatar") }
    // 背景分端：两端各一对「本地上传 / 参考图」选择器
    val pickBgPhone = rememberImageSourcePicker { uri ->
        uri?.let { vm.setBg(BgTarget.Phone, it); vm.setCropSource(BgTarget.Phone, null) }
    }
    val pickBgDesktop = rememberImageSourcePicker { uri ->
        uri?.let { vm.setBg(BgTarget.Desktop, it); vm.setCropSource(BgTarget.Desktop, null) }
    }
    val pickBgRefPhone = rememberImageSourcePicker { uri -> vm.setPendingRef(uri, BgTarget.Phone.kind) }
    val pickBgRefDesktop = rememberImageSourcePicker { uri -> vm.setPendingRef(uri, BgTarget.Desktop.kind) }
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
        // 与设置页同一处口径（241 收编）：点外/拖走/返回＝留在页内（顺带撤掉跳转请求），
        // 只有「放弃更改」钮才不保存退出
        WhaleConfirmSheet(
            title = "保存更改？",
            body = "当前角色卡有未保存的修改。",
            confirmLabel = "保存并退出",
            onConfirm = { vm.save(onSaved, writeFailureToast(null, "保存角色")) },
            dismissLabel = if (pendingJump != null) "放弃更改并前往" else "放弃更改",
            onDismissButton = {
                WorldBookJump.pendingBookId = pendingJump
                onBack()
            },
            onDismiss = {
                showDiscardConfirm = false
                pendingJump = null  // 取消确认就撤掉跳转请求，别让下次"放弃更改"带上它
            },
            confirmDanger = false
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
                WhalePageTopBar(
                    title = if (vm.isNew) "新建角色" else "编辑角色",
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
                    }
                )
            }
        }
    ) { padding ->
        // 桌面两段式（＝「编辑世界书」223/224 同一套语法）：左窄栏分节导航＋右宽栏全部内容一列滚，
        // 分节清单与两段式本体在 CharacterEditorSections.kt。手机分支原样保留在下面：
        // 基础信息常驻 ＋ 分页胶囊切换（分页容器 editorTabPage 不动）。
        if (isDesktopLayout) {
            CharacterEditorTwoPane(
                vm = vm,
                contentPadding = padding,
                onPreview = { previewWhat = it },
                onPickAvatarLocal = pickAvatar,
                onGenerateAvatar = { vm.showPromptFor = "avatar" },
                onPickAvatarRef = pickAvatarRef,
                onPickBgLocalPhone = pickBgPhone,
                onPickBgLocalDesktop = pickBgDesktop,
                onPickBgRefPhone = pickBgRefPhone,
                onPickBgRefDesktop = pickBgRefDesktop,
                onOpenWorldBook = jumpToBook
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                EditorIdentitySection(
                    vm = vm,
                    onPreviewAvatar = { previewWhat = "avatar" },
                    onPickLocal = pickAvatar,
                    onGenerate = { vm.showPromptFor = "avatar" },
                    onPickReference = pickAvatarRef
                )
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
                ArrowScrollRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 序号取**过滤后**列表的下标：工具形态没有「世界书」那一页，
                    // 用它写 enum 的 ordinal 会把后面几页整体错位一格
                    tabs.forEachIndexed { i, t ->
                        WhaleChip(
                            selected = t == editorTab,
                            onClick = { editorTabIndex = i },
                            label = { Text(t.label) }
                        )
                    }
                }
            }

            editorTabPage(EditorTab.SPEC, editorTab) { item { EditorSpecSection(vm) } }

            // 世界书：与上面几段同属"这个角色带什么资料"，单独一页。
            // 编辑器不再内联改书，只留 启用 / 编辑 / 新建；跳转经 jumpToBook（脏确认＋WorldBookJump 请求），宝库侧消费
            editorTabPage(EditorTab.BOOK, editorTab) {
                item { WorldBookManageSection(vm, jumpToBook) }
            }

            // 聊天背景：手机端与桌面端各一份（见下面那段说明）
            editorTabPage(EditorTab.BACKGROUND, editorTab) {
                item {
                    EditorBackgroundSection(
                        vm = vm,
                        onPreview = { previewWhat = it },
                        onPickLocalPhone = pickBgPhone,
                        onPickLocalDesktop = pickBgDesktop,
                        onPickRefPhone = pickBgRefPhone,
                        onPickRefDesktop = pickBgRefDesktop
                    )
                }
            }

            // ── 卡信息 + v2 原样字段（仍属「角色设定」页）────────────
            editorTabPage(EditorTab.SPEC, editorTab) { item { EditorCardInfoSection(vm) } }

            // 角色专属音色：单独一页
            editorTabPage(EditorTab.VOICE, editorTab) { characterVoiceSection(vm) }

            editorTabPage(EditorTab.SPEC, editorTab) {
                if (!vm.isNew) item { EditorDeleteButton(vm) }
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
            // 238 场景级模型：弹窗顶部＝生图模型胶囊（就地可切换、落盘）
            modelScene = ModelScene.IMAGE,
            // 0.1.2：懒人路径——不用自己写描述，让 AI 按角色卡设定起草一版
            draftLabel = if (kind == "avatar") "按角色设定起草提示词" else "按世界观起草背景提示词",
            drafting = vm.draftingPrompt,
            draftText = vm.promptDraft,
            onDraft = { vm.draftPrompt(kind) }
        )
    }

    if (vm.showDeleteConfirm) {
        // 236 弹窗统一：删除类残留收编共享确认壳（手机底部浮层／桌面居中面板，与 241 同族）
        WhaleConfirmSheet(
            title = "删除角色？",
            body = "该角色的所有会话记录、头像与背景图也会一并删除，此操作不可恢复。",
            confirmLabel = "删除",
            onConfirm = {
                vm.showDeleteConfirm = false
                vm.delete(onSaved, writeFailureToast(null, "删除角色"))
            },
            onDismiss = { vm.showDeleteConfirm = false }
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
