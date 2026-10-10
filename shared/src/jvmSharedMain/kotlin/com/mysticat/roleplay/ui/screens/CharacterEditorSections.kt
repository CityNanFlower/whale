package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterFormTags
import com.mysticat.roleplay.data.EditorField
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.OutputFormats
import com.mysticat.roleplay.data.PlayOptionChoices
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.components.WhaleFieldBlock
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.components.WhaleToggleRow
import com.mysticat.roleplay.ui.tokens.LocalWhaleType
import kotlinx.coroutines.launch

/** 桌面两段式左栏的分节清单：顺序＝右栏堆叠顺序，key 是导航与选中态的契约。
 *  "book" 一节按形态裁剪（工具形态不注入世界书，见 [CharacterEditorTwoPane]）。 */
internal val EditorSections = listOf(
    "basic" to "头像与基础信息",
    "spec" to "角色设定",
    "book" to "世界书",
    "background" to "聊天背景",
    "voice" to "专属音色",
    "cardinfo" to "卡信息"
)

/**
 * 桌面两段式编辑器（＝「编辑世界书」223/224 同一套语法）：左窄栏＝分节导航，右宽栏＝全部分节
 * 按导航顺序堆叠，一节一格 LazyColumn item。手机分支不用这里——它保持"基础信息常驻＋分页胶囊"。
 *
 * 选中态是**显式 state**而不是派生值（224 轮教训：首帧锚点没量到时派生值零依赖、永远不更新）；
 * 点导航先选中再滚，动画期间用 jumping 暂停跟随，免得中途闪回上一节。
 * 每节一整格 item，所以选中态跟着 firstVisibleItemIndex 走即可：只要某节还顶着视口，就还在这一节。
 */
@Composable
internal fun CharacterEditorTwoPane(
    vm: CharacterEditorViewModel,
    /** Scaffold 的 content padding：窄头部（topBar）占掉的高度从这里补，不吃它整栏会顶到头部底下 */
    contentPadding: PaddingValues,
    onPreview: (String) -> Unit,
    onPickAvatarLocal: () -> Unit,
    onGenerateAvatar: () -> Unit,
    onPickAvatarRef: () -> Unit,
    onPickBgLocalPhone: () -> Unit,
    onPickBgLocalDesktop: () -> Unit,
    onPickBgRefPhone: () -> Unit,
    onPickBgRefDesktop: () -> Unit,
    onOpenWorldBook: (String?) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val navSections = remember(vm.formTag) {
        EditorSections.filter { it.first != "book" || Engines.of(vm.formTag).worldBook }
    }
    val sectionKeys = remember(navSections) { navSections.map { it.first } }
    var activeSection by remember { mutableStateOf(EditorSections.first().first) }
    var jumping by remember { mutableStateOf(false) }
    fun jump(key: String) {
        // 先选中再滚：内容滚没滚是另一回事，导航必须先反映出"点的是哪一节"
        activeSection = key
        val index = sectionKeys.indexOf(key)
        if (index < 0) return
        jumping = true
        scope.launch {
            listState.animateScrollToItem(index)
            jumping = false
        }
    }
    LaunchedEffect(sectionKeys) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { idx ->
            if (!jumping && sectionKeys.isNotEmpty()) {
                activeSection = sectionKeys[idx.coerceIn(0, sectionKeys.lastIndex)]
            }
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.width(216.dp)) {
            navSections.forEach { (key, label) ->
                WhaleChip(
                    selected = activeSection == key,
                    onClick = { jump(key) },
                    label = { Text(label, style = LocalWhaleType.current.caption) },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                EditorIdentitySection(
                    vm = vm,
                    onPreviewAvatar = { onPreview("avatar") },
                    onPickLocal = onPickAvatarLocal,
                    onGenerate = onGenerateAvatar,
                    onPickReference = onPickAvatarRef
                )
            }
            item { EditorSpecSection(vm) }
            if (sectionKeys.contains("book")) {
                item { WorldBookManageSection(vm, onOpenWorldBook) }
            }
            item {
                EditorBackgroundSection(
                    vm = vm,
                    onPreview = onPreview,
                    onPickLocalPhone = onPickBgLocalPhone,
                    onPickLocalDesktop = onPickBgLocalDesktop,
                    onPickRefPhone = onPickBgRefPhone,
                    onPickRefDesktop = onPickBgRefDesktop
                )
            }
            item { CharacterVoiceSectionBody(vm) }
            item { EditorCardInfoSection(vm) }
            if (!vm.isNew) {
                item { EditorDeleteButton(vm) }
            }
        }
    }
}

/**
 * 头像＋基础信息：这是谁的三件套（头像 / 角色名·简介·分类）常驻最前，手机端与桌面端同一份。
 * 头像与来源按钮竖排（用户 2026-09-26）：按钮独占整行后每颗约三分之一宽，多一个来源也有地方放。
 * 基础信息走 v10 分组卡：角色名/简介文本框 ＋ 分类、形态标签两组勾选行（形态标签 2026-10-08
 * 从「角色设定」挪进来并改勾选框，用户反馈④）；头像槽保持原样不进卡（档 2 口径）。
 */
@Composable
internal fun EditorIdentitySection(
    vm: CharacterEditorViewModel,
    onPreviewAvatar: () -> Unit,
    onPickLocal: () -> Unit,
    onGenerate: () -> Unit,
    onPickReference: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    // CategoryManager 不是 Compose 状态：新建后靠版本号变化让 remember 重读选项
    var categoryRev by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "头像",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(
                model = imageModel(vm.avatarUri),
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .clickable(enabled = vm.avatarUri != null) { onPreviewAvatar() }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageSourceButtons(
                    busy = vm.generating,
                    generateLabel = "AI 生成",
                    onPickLocal = onPickLocal,
                    onGenerate = onGenerate,
                    onPickReference = onPickReference
                )
            }
            if (vm.avatarUri != null) {
                TextButton(
                    onClick = { vm.avatarUri = null; vm.avatarCropSource = null },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("移除") }
            }
        }
        WhaleFieldGroup(label = "基础信息") {
            WhaleFieldBlock(
                label = "角色名 *",
                value = vm.name,
                onValueChange = { vm.name = it }
            )
            WhaleFieldBlock(
                label = "一句话简介",
                value = vm.tagline,
                onValueChange = { vm.tagline = it },
                placeholder = "展示在名册上的那一句"
            )
            WhaleToggleRow(
                title = "分类（可多选）",
                desc = "最多影响名册的一级筛选",
                // 固定 + 自定义 + 其他(最后)；删/排序仍在「角色卡」页顶部的管理里，行内补"新建"（反馈④）
                options = remember(categoryRev) { CategoryManager.all() },
                isSelected = { it in vm.categories },
                onToggle = { vm.toggleCategory(it) },
                onAdd = { newCategory = ""; showNewCategory = true },
                addLabel = "新建分类"
            )
            WhaleToggleRow(
                title = "形态标签",
                desc = when (vm.formTag) {
                    "tool" -> "工具：用户发来的是待处理素材、你产出的是可直接拿走的成品（不扮演）。"
                    "play" -> "玩法：陪玩一局有规则的玩法；规则是硬约束、进度自己记，不做主线存档。"
                    "experience" -> "多线：多种开局方向，新会话随机抽一条开场白。"
                    else -> "陪伴：单开场白的沉浸式角色扮演。"
                },
                options = CharacterFormTags.map { it.first },
                isSelected = { label -> CharacterFormTags.first { it.first == label }.second == vm.formTag },
                onToggle = { label -> vm.updateFormTag(CharacterFormTags.first { it.first == label }.second) },
                divider = false
            )
        }
    }
    if (showNewCategory) {
        val typed = newCategory.trim()
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("新建分类") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        singleLine = true,
                        placeholder = { Text("分类名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (typed.isNotEmpty() && typed in Engines.formLabels) {
                        Text(
                            "「$typed」是形态标签，不能再当分类。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.isNotBlank() && typed !in Engines.formLabels,
                    onClick = {
                        CategoryManager.add(typed)
                        categoryRev++
                        // 建完顺手勾上——新建通常就是为这张卡建的
                        if (typed !in vm.categories) vm.toggleCategory(typed)
                        showNewCategory = false
                    }
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewCategory = false }) { Text("取消") } }
        )
    }
}

/**
 * 「角色设定」分节（v10 行表式，2026-10-08 反馈①）：分层字段（事实 / 气质 / 语气分开——混成一段
 * 会互相复读）＋ 形态专属字段 ＋ 开场白，全部改走分组卡；形态标签已挪进「基础信息」卡（反馈④）。
 * 形态裁剪读 [Engines.of]，与装配器是同一份判据；每组末块按"后面还有没有内容"算 divider。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorSpecSection(vm: CharacterEditorViewModel) {
    val spec = Engines.of(vm.formTag)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (EditorField.Persona in spec.editorFields) {
            // 末块才收 divider：对话示例与老版人设都是条件块，谁是最后一项跟着形态与数据变
            val hasExamples = EditorField.DialogueExamples in spec.editorFields
            val hasLegacy = vm.persona.isNotBlank()
            WhaleFieldGroup(label = "角色设定") {
                WhaleFieldBlock(
                    label = spec.personaLabel,
                    value = vm.description,
                    onValueChange = { vm.description = it },
                    minLines = 3,
                    placeholder = "例：帝国第三军团将军，银发，左眼有一道旧疤；对陌生人冷淡。"
                )
                WhaleFieldBlock(
                    label = spec.personalityLabel,
                    value = vm.personality,
                    onValueChange = { vm.personality = it },
                    minLines = 2,
                    placeholder = "例：外冷内热；认定的事不轻易改口；讨厌被人怜悯。",
                    divider = hasExamples || hasLegacy
                )
                // 对话示例：工具形态不用它 —— 工具的"示例"是任务样例（见下面的使用示例）
                if (hasExamples) {
                    WhaleFieldBlock(
                        label = "对话示例（可留空；用 <START> 分块，写「角色名:」「你:」）",
                        value = vm.mesExample,
                        onValueChange = { vm.mesExample = it },
                        minLines = 3,
                        placeholder = "例：\n<START>\n将军: 站住。\n你: 我……\n将军: 别说话，跟我走。",
                        divider = hasLegacy
                    )
                }
                // 老卡的人设是当年**导入时拼好的整块文本**（含【背景故事】【性格特点】小标题），
                // 不做正则反向拆（拆坏用户手改过的内容）。这里原样展示，并说明它什么时候失效。
                if (hasLegacy) {
                    WhaleFieldBlock(
                        label = "人设（老版合并内容）",
                        value = vm.persona,
                        onValueChange = { vm.persona = it },
                        minLines = 4,
                        hint = "这是老版本拼在一起的整块人设。" +
                            (if (vm.hasLayeredFields()) "上面填了「角色设定」的分层字段，所以**这一段不再注入**，可以整段清空。"
                            else "上面的分层字段都为空，所以**它仍按原样注入**。"),
                        divider = false
                    )
                }
            }
        }
        // ── 工具形态专属两栏──────────────────────────────────────────────
        // 不复用"人设 / 世界观"：工具作者看到那两个词会困惑，而且 persona 是以"这是你本人的设定"
        // 注入的，复用会主动诱发扮演。这栏写进 extensions.whale 保真往返。
        if (EditorField.TaskBrief in spec.editorFields) {
            WhaleFieldGroup(label = "任务说明") {
                WhaleFieldBlock(
                    label = "要它做什么（工具本身的职责）",
                    value = vm.taskBrief,
                    onValueChange = { vm.taskBrief = it },
                    minLines = 3,
                    placeholder = "例：把用户发来的会议记录整理成待办清单，一条一事、去掉寒暄与重复。",
                    hint = "这段进系统提示词，作为这个工具的职责说明。"
                )
                WhaleFieldBlock(
                    label = "输出格式要求（会贴到本轮消息末尾强制遵守）",
                    value = vm.outputFormat,
                    onValueChange = { vm.outputFormat = it },
                    minLines = 3,
                    placeholder = "例：每行「- [ ] 事项（负责人）」，不要标题、不要总结段。",
                    hint = "写得越可核对越管用（「只给 10 个编号名字、不要别的字」这类）。",
                    divider = false
                )
            }
            // 预设：最常用的四个形状，点一下填进上面那一栏，填完还能手改。
            // 放在输入框**下面**而不是里面：它是"帮你起个头"的东西，不是那一栏的当前值展示。
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
        // ── 玩法形态专属三栏────────────────────────────
        // 规则是这一局唯一的"对错来源"（没有它，模型会为了顺着用户而放宽判定）；
        // 状态项告诉模型这一局要记住什么 —— 它进系统提示词，会话里那份【当前进度】也按它整理。
        if (EditorField.PlayRules in spec.editorFields) {
            WhaleFieldGroup(label = "玩法规则与状态") {
                WhaleFieldBlock(
                    label = "规则（硬约束：必须… / 不可…）",
                    value = vm.playRules,
                    onValueChange = { vm.playRules = it },
                    minLines = 4,
                    placeholder = "例：\n只回答「是」或「不是」，不要报具体名字。\n答「不是」时必须把该候选排除，之后不再提。\n20 问内没猜中就算用户赢。",
                    hint = "这是这一局唯一的对错来源：写得越可判定，模型越不会为了顺着用户而放宽规则。"
                )
                WhaleFieldBlock(
                    label = "要记的状态（进度 / 计分 / 已确认结论）",
                    value = vm.playState,
                    onValueChange = { vm.playState = it },
                    minLines = 3,
                    placeholder = "例：已排除的候选、当前第几问、谁领先、还剩下什么没确认。",
                    hint = "它进系统提示词；聊天中 AI 也会按它自动更新会话里的「本局进度」（每 4 条一次）。",
                    divider = false
                )
            }
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
        // ── 场景与开场白（按形态换组名与字段名）────────────────────────
        // 玩法形态下 scenario 不是"此刻的处境"，而是这局玩法的前提（进了系统提示词，
        // 所以会话里**不铺场景气泡**，见 ChatViewModel.newConversation）；
        // 多条开场白 / 多条使用示例：「多线」下新会话随机抽一条；工具下它们只是并列的任务样例
        //（点击即填入输入框），所以**多条示例不得触发「多线」徽标**；
        // 玩法下它们是并列的**开局引导**（随机抽一条＝这一局从哪里开始）
        val hasScenario = EditorField.Scenario in spec.editorFields
        val hasGreeting = EditorField.Greeting in spec.editorFields
        val extrasUi = vm.formTag == "experience" || vm.formTag == "tool" || vm.formTag == "play"
        if (hasScenario || hasGreeting || extrasUi) {
            WhaleFieldGroup(
                label = when (vm.formTag) {
                    "tool" -> "使用示例"
                    "play" -> "玩法与开局"
                    else -> "场景与开场白"
                }
            ) {
                if (hasScenario) {
                    WhaleFieldBlock(
                        label = if (vm.formTag == "play") "玩法设定（这一局的前提与背景）" else "世界观 / 当前场景",
                        value = vm.scenario,
                        onValueChange = { vm.scenario = it },
                        minLines = 3,
                        placeholder = if (vm.formTag == "play") {
                            "例：这是一局「二十问猜人」，你想一个人物，我只用是 / 不是的问题来猜。"
                        } else {
                            "可选：故事发生在哪里，此刻是什么情境"
                        },
                        divider = hasGreeting || (extrasUi && vm.extraGreetings.isNotEmpty())
                    )
                }
                if (hasGreeting) {
                    WhaleFieldBlock(
                        label = spec.greetingLabel,
                        value = vm.greeting,
                        onValueChange = { vm.greeting = it },
                        minLines = 3,
                        placeholder = spec.greetingHint,
                        divider = extrasUi && vm.extraGreetings.isNotEmpty()
                    )
                }
                if (extrasUi) {
                    vm.extraGreetings.forEachIndexed { i, g ->
                        WhaleFieldBlock(
                            label = when (vm.formTag) {
                                "tool" -> "使用示例 ${i + 2}"
                                "play" -> "开局引导 ${i + 2}"
                                else -> "开场白 ${i + 2}"
                            },
                            value = g,
                            onValueChange = { vm.updateExtraGreeting(i, it) },
                            minLines = 2,
                            onDelete = { vm.removeExtraGreeting(i) },
                            divider = i < vm.extraGreetings.lastIndex
                        )
                    }
                }
            }
            if (extrasUi) {
                // 添加按钮放卡外（与「常用格式」同口径：动作不进字段卡）
                TextButton(onClick = vm::addExtraGreeting) {
                    Text(
                        when (vm.formTag) {
                            "tool" -> "+ 添加一条使用示例"
                            "play" -> "+ 添加一条开局引导"
                            else -> "+ 添加一条开场白"
                        }
                    )
                }
            }
        }
        // 形态标签已挪进「基础信息」卡（2026-10-08 用户反馈④：挪到基础信息卡片内、改成勾选框）
    }
}

/**
 * 「聊天背景」分节：手机端与桌面端各存一份（两端窗口形状不同，A 批次口径）；
 * 桌面那份没单独设过、正用着手机端那张时必须说出来，否则用户看到的图和"桌上显示的是哪张"对不上。
 */
@Composable
internal fun EditorBackgroundSection(
    vm: CharacterEditorViewModel,
    onPreview: (String) -> Unit,
    onPickLocalPhone: () -> Unit,
    onPickLocalDesktop: () -> Unit,
    onPickRefPhone: () -> Unit,
    onPickRefDesktop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("默认聊天背景", style = MaterialTheme.typography.titleSmall)
        Text(
            "手机与桌面的窗口形状不同，各存一份；只设一份时两端都用它。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BackgroundSlotEditor(
            target = BgTarget.Phone,
            uri = vm.slotBg(BgTarget.Phone),
            inherited = false,
            busy = vm.generating,
            onPreview = { onPreview(BgTarget.Phone.kind) },
            onPickLocal = onPickLocalPhone,
            onGenerate = { vm.showPromptFor = BgTarget.Phone.kind },
            onPickReference = onPickRefPhone,
            onRemove = {
                vm.setBg(BgTarget.Phone, "")
                vm.setCropSource(BgTarget.Phone, null)
            }
        )
        BackgroundSlotEditor(
            target = BgTarget.Desktop,
            uri = vm.slotBg(BgTarget.Desktop),
            inherited = vm.backgroundUriDesktop == null && !vm.backgroundUri.isNullOrBlank(),
            busy = vm.generating,
            onPreview = { onPreview(BgTarget.Desktop.kind) },
            onPickLocal = onPickLocalDesktop,
            onGenerate = { vm.showPromptFor = BgTarget.Desktop.kind },
            onPickReference = onPickRefDesktop,
            onRemove = {
                // 写空串而不是 null：null 是"没设过"，那样又会回退到手机端那张
                vm.setBg(BgTarget.Desktop, "")
                vm.setCropSource(BgTarget.Desktop, null)
            }
        )
    }
}

/**
 * 「卡信息」分节：给玩的人看的说明与署名（不进提示词）＋ 后置指令 ＋ 原卡自带的系统提示词。
 * v10 行表式：说明/后置指令/系统提示词是正文⇒大字段标签，作者/卡版本是配置⇒小字段上下叠
 * （原来同一行两个输入框，手机上并排会窄到看不清值）。
 */
@Composable
internal fun EditorCardInfoSection(vm: CharacterEditorViewModel) {
    WhaleFieldGroup(label = "卡信息") {
        WhaleFieldBlock(
            label = "说明（给玩的人看，不进提示词）",
            value = vm.creatorNotes,
            onValueChange = { vm.creatorNotes = it },
            placeholder = "例：适合慢慢聊的日常向角色；开局从深夜电台开始。",
            minLines = 2
        )
        WhaleFieldBlock(
            label = "作者",
            value = vm.creator,
            onValueChange = { vm.creator = it }
        )
        WhaleFieldBlock(
            label = "卡版本",
            value = vm.characterVersion,
            onValueChange = { vm.characterVersion = it }
        )
        WhaleFieldBlock(
            label = "后置指令",
            value = vm.postHistory,
            onValueChange = { vm.postHistory = it },
            minLines = 3,
            hint = "贴在本轮最后一条消息末尾——这是最贴近生成点的位置，比写在人设里更容易被遵守。可留空。"
        )
        WhaleFieldBlock(
            label = "原卡自带的系统提示词（只保存，不生效）",
            value = vm.systemPrompt,
            onValueChange = { vm.systemPrompt = it },
            minLines = 2,
            hint = "别人的卡常把系统提示词写在这里。鲸鱼的角色扮演提示词由 App 统一控制（视角约定等），" +
                "所以这一段**只原样保存与导出、不参与对话**，避免两套规则互相打架。",
            divider = false
        )
    }
}

/** 删除入口收尾（不可恢复，连同全部会话）；确认框在主屏幕里。 */
@Composable
internal fun EditorDeleteButton(vm: CharacterEditorViewModel) {
    Button(
        onClick = { vm.showDeleteConfirm = true },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("删除这个角色（连同全部会话）")
    }
}
