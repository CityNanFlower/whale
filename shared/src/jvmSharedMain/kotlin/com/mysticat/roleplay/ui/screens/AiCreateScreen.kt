package com.mysticat.roleplay.ui.screens

import com.mysticat.roleplay.ui.CrashNote
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.CharacterFormTags
import com.mysticat.roleplay.data.CharacterGenerator
import com.mysticat.roleplay.data.CustomTemplate
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookGenerator
import com.mysticat.roleplay.data.imageModel
import com.mysticat.roleplay.ui.AiActionButton
import com.mysticat.roleplay.ui.DesktopRailHeader
import com.mysticat.roleplay.ui.DesktopRailRow
import com.mysticat.roleplay.ui.DesktopRailSectionLabel
import com.mysticat.roleplay.ui.ErrorBanner
import com.mysticat.roleplay.ui.ImageSizeChips
import com.mysticat.roleplay.ui.noArgViewModelFactory
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.imageSizeLabel
import com.mysticat.roleplay.ui.rememberImagePicker
import com.mysticat.roleplay.ui.SectionCard
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.writeFailureToast
import kotlinx.coroutines.launch

/**
 * 模板：`mode` 决定填进哪一页的输入框（角色页 / 故事页），`form` 决定**用哪一种角色卡形态**
 * （空＝陪伴，取值与 [Engines] 的 formTag 一致）。
 *
 * **第 79 轮按用户口径重写**（"已有的模板和现在的功能定位都有出入"）：模板不再只分「角色 / 故事」
 * 两个桶，而是按**四种形态**分——形态是这个 App 现在的第一分类，模板却停在"角色卡只有一种"的年代，
 * 玩法与工具一个模板都没有，用户只能从空白开始描述。
 * 问法参考同类软件各取一处：Character.AI / Talkie 的**关系起点**（谁在敲门、旧识重逢）、
 * 星野与筑梦岛的场景开局、橙光的剧情向开局（故事模板）、SillyTavern 社区的卡原型（高岭之花、系统流）；
 * 每条都收敛成"填三句话就能生成"的字段。
 */
data class Template(
    val name: String,
    val mode: String, // "角色" | "故事"
    val fields: List<String>,
    /** 形态（[Engines] 的 formTag）：""陪伴 / "experience"多线 / "play"玩法 / "tool"工具 */
    val form: String = ""
)

// ── 模板分类筛选（桌面三栏第二栏用；顺序＝第二栏的排列顺序）＝「全部」＋四种形态＋故事＋我的 ──
internal const val TemplateFilterAll = "全部"
internal const val TemplateFilterStory = "故事"
internal const val TemplateFilterMine = "我的"
internal val TemplateFilters: List<String> =
    listOf(TemplateFilterAll) + Engines.all.map { it.label } +
        listOf(TemplateFilterStory, TemplateFilterMine)

/** 陪伴模板：字段只描述**要创建的那个角色（TA）**与关系起点——问题 #22，别把用户自己当主体 */
private val RpTemplates = listOf(
    Template("深夜来客", "角色", listOf("TA的身份", "TA深夜还醒着的原因", "你们的关系（选填）")),
    Template("高岭之花攻心计", "角色", listOf("TA的身份", "TA的性格", "难攻在哪（选填）")),
    Template("旧识重逢", "角色", listOf("TA是谁", "你们失联了多久", "重逢的场合")),
    Template("绑定系统", "角色", listOf("系统类型", "TA的宿主身份", "金手指（选填）"))
)

/** 多线模板：字段问的是**几条不同的开局方向**——多线卡的新会话会从多条开场白里随机抽一条 */
private val MultiTemplates = listOf(
    Template("三条岔路", "角色",
        listOf("TA的身份", "开局方向一", "开局方向二", "开局方向三（选填）"), "experience"),
    Template("同一个人 · 三种相遇", "角色",
        listOf("TA的身份与性格", "相遇场合A", "相遇场合B", "相遇场合C（选填）"), "experience"),
    Template("平行关系起点", "角色",
        listOf("TA的身份", "关系起点A", "关系起点B"), "experience")
)

/** 玩法模板：字段问的是**规则 / 这一局记什么 / 怎么开局**——照角色卡那套问会生成出一张没规则的卡 */
private val PlayTemplates = listOf(
    Template("猜人物", "角色",
        listOf("猜的是什么（人物 / 地点 / 词）", "规则（怎么算赢）", "每轮给几个选项"), "play"),
    Template("对弈一局", "角色",
        listOf("玩的是什么（棋 / 牌 / 对局）", "规则要点", "进度要记什么"), "play"),
    Template("情景问答", "角色",
        listOf("这一局的场景", "规则", "开局引导（选填）"), "play")
)

/** 工具模板：字段问的是**任务说明 / 输出格式 / 使用示例**——工具卡不扮演角色，只交付能拿走的成品 */
private val ToolTemplates = listOf(
    Template("会议纪要", "角色",
        listOf("任务说明（把什么变成什么）", "输出格式", "使用示例（选填）"), "tool"),
    Template("文案润色", "角色",
        listOf("任务说明", "输出格式", "风格参考（选填）"), "tool"),
    Template("资料整理", "角色",
        listOf("任务说明", "输出格式", "使用示例（选填）"), "tool")
)

/** 故事模板：字段描述**世界 + 用户自己在其中的位置**（问题 #22：与角色模板彻底分家） */
private val StoryTemplates = listOf(
    Template("弹幕系统", "故事", listOf("弹幕出现的场合", "你的身份")),
    Template("失踪案", "故事", listOf("案发地点", "失踪者身份", "你的身份")),
    Template("穿越异界", "故事", listOf("穿越到的世界", "你的身份")),
    Template("读心感应", "故事", listOf("你能听到谁的心声", "你与TA的关系"))
)

/** 形态标签 → 该形态的内置模板（模板页按它分区；顺序与 [TemplateFilters] 一致，取自同一份 [Engines]） */
private val FormTemplates: List<Pair<String, List<Template>>> = listOf(
    Engines.of("").label to RpTemplates,
    Engines.of("experience").label to MultiTemplates,
    Engines.of("play").label to PlayTemplates,
    Engines.of("tool").label to ToolTemplates
)

/** 某一形态的全部内置模板（筛选或分区用；认不出形态时返回空表） */
internal fun builtinTemplates(formLabel: String): List<Template> =
    FormTemplates.firstOrNull { it.first == formLabel }?.second.orEmpty()

private val CharacterSuggestions = listOf(
    "深夜来客", "高岭之花攻心计", "旧识重逢", "三条岔路", "猜人物", "会议纪要"
)
private val ImageSuggestions = listOf(
    "二次元美少女立绘", "古风将军", "赛博朋克少女", "森系少女", "Q版小人", "国风男神"
)

/**
 * 创作提示词的草稿键（第 63 轮）：三处输入分开存，退出应用后原样恢复；
 * **不自动删**（生成完也留着），只有输入框上的叉号会清（见 `AiCreateViewModel.clearPromptDraft`）。
 */
private const val DRAFT_CHARACTER = "create:prompt:character"
private const val DRAFT_IMAGE = "create:prompt:image"
private const val DRAFT_STORY = "create:prompt:story"
private const val DRAFT_WORLDBOOK = "create:prompt:worldbook"

class AiCreateViewModel : ViewModel() {
    var tab by mutableStateOf("模板") // 模板 / 角色 / 形象 / 世界书 / 故事
        private set
    var prompt by mutableStateOf("")
        private set
    // 问题 #34：形象页输入框与角色页解耦，各用各的
    var imagePrompt by mutableStateOf("")
        private set
    /** 类型/标签：多选。空 = 交给模型自动判断 */
    var categories by mutableStateOf<List<String>>(emptyList())
        private set
    /** 形态标签：""陪伴（默认）| "experience"体验（多开局） */
    var formTag by mutableStateOf("")
    var generating by mutableStateOf(false)
        private set
    /** 0.1.2：AI 扩写生图提示词进行中 */
    var expanding by mutableStateOf(false)
        private set
    var result by mutableStateOf<CharacterCard?>(null)
        private set
    var imageUri by mutableStateOf<String?>(null)
        private set
    /** #6：本次生图尺寸（形象默认方形），不再读「模型设置」里的全局值 */
    var imageSize by mutableStateOf("1024x1024")
    /** 形象生成的参考图（视觉模型转译画风/人物特征） */
    var imageRef by mutableStateOf<String?>(null)
    var storyPrompt by mutableStateOf("")
        private set
    /** 世界书提示词（台账 69）：与另三处输入一样输入即存草稿 */
    var worldBookPrompt by mutableStateOf("")
        private set
    /** 世界书生成结果：预览后由用户点「保存到宝库」落库 */
    var worldBookResult by mutableStateOf<WorldBook?>(null)
        private set
    var customTemplates by mutableStateOf<List<CustomTemplate>>(emptyList())
        private set
    /**
     * 模板分类筛选：`全部` / `角色` / `故事` / `我的`。**桌面三栏专用**（第二栏的模板分类点它），
     * 手机端没有入口、恒为「全部」，板面内容与以前完全一致。
     */
    var templateFilter by mutableStateOf(TemplateFilterAll)
        private set
    /** 每个模板已填的字段值（key=模板名），点击模板时复用 */
    var templateValues by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        customTemplates = Repository.listCustomTemplates()
        // 第 63 轮（用户口径）：创作中的提示词退出应用后要还在 —— 进页面先把草稿读回来。
        // 键按用途分开（角色 / 形象 / 故事），三处输入互不覆盖。
        prompt = Repository.loadDraft(DRAFT_CHARACTER)
        imagePrompt = Repository.loadDraft(DRAFT_IMAGE)
        storyPrompt = Repository.loadDraft(DRAFT_STORY)
        worldBookPrompt = Repository.loadDraft(DRAFT_WORLDBOOK)
    }

    /** 保存自定义模板并刷新（已持久化到账号本地，可删除） */
    fun addTemplate(c: CustomTemplate) {
        Repository.saveCustomTemplate(c)
        customTemplates = Repository.listCustomTemplates()
    }

    fun deleteTemplate(id: String) {
        Repository.deleteCustomTemplate(id)
        customTemplates = Repository.listCustomTemplates()
    }

    fun updateTab(t: String) {
        // 操作轨迹（CrashGuard）：顶部四栏是偶发闪退的高频点击处，先留一笔现场
        CrashNote.note("灵感创作 分段=$t")
        tab = t; result = null; imageUri = null; worldBookResult = null; error = null
    }

    /**
     * 桌面三栏：第二栏点模板分类。**只改筛选、不改分段**——第二栏的分类只在「模板」分段下出现，
     * 点它的时候分段本来就是模板（手机端没有这个入口）。
     */
    fun updateTemplateFilter(f: String) {
        if (f == templateFilter) return
        CrashNote.note("灵感创作 模板分类=$f")
        templateFilter = f
    }
    fun updatePrompt(p: String) { prompt = p; result = null; Repository.saveDraft(DRAFT_CHARACTER, p) }

    // 问题 #34：形象输入框独立更新，只清形象生成结果
    fun updateImagePrompt(p: String) { imagePrompt = p; imageUri = null; Repository.saveDraft(DRAFT_IMAGE, p) }

    /** 故事提示词：同样输入即存（退出应用后原样还在） */
    fun updateStoryPrompt(p: String) { storyPrompt = p; Repository.saveDraft(DRAFT_STORY, p) }

    /** 世界书提示词：同样输入即存；清结果——描述变了旧结果就没意义了 */
    fun updateWorldBookPrompt(p: String) { worldBookPrompt = p; worldBookResult = null; Repository.saveDraft(DRAFT_WORLDBOOK, p) }

    /** 叉号：明确清空某一处提示词（草稿不会被自动删除，只有这个动作会清） */
    fun clearPromptDraft(which: String) {
        when (which) {
            DRAFT_CHARACTER -> prompt = ""
            DRAFT_IMAGE -> imagePrompt = ""
            DRAFT_STORY -> storyPrompt = ""
            DRAFT_WORLDBOOK -> worldBookPrompt = ""
        }
        Repository.saveDraft(which, "")
    }

    /**
     * 0.1.2：把一句话描述扩写成可直接出图的详细提示词，结果写回形象输入框（用户可继续手改）。
     * 走对话模型（chatModel）与创作思考强度，与参考图转译 visionText 同一条链路。
     */
    fun expandImagePrompt() {
        if (expanding || generating || imagePrompt.isBlank()) return
        expanding = true
        error = null
        viewModelScope.launch {
            try {
                val settings = Repository.loadSettings()
                imagePrompt = AiClient.expandImagePrompt(settings, imagePrompt)
                // 扩写结果也是"用户正在写的东西"：落草稿，退出应用后还在（不自动删）
                Repository.saveDraft(DRAFT_IMAGE, imagePrompt)
            } catch (t: Throwable) {
                error = t.message ?: "扩写失败，请检查对话模型配置"
            } finally {
                expanding = false
            }
        }
    }
    /** 类型/标签多选：点选或取消；清空即为「自动」 */
    fun toggleCategory(c: String) {
        categories = if (c in categories) categories - c else categories + c
        result = null
    }

    fun clearCategories() {
        categories = emptyList()
        result = null
    }

    /** 推荐快捷入口（角色/故事页模板 chip）：直接把模板 + 已存字段填进输入框，不弹字段框 */
    fun applyTemplate(tpl: Template) {
        val current = if (tpl.mode == "角色") prompt else storyPrompt
        val captured = captureFields(current, tpl.fields)
        val stored = (templateValues[tpl.name] ?: emptyMap()).toMutableMap().apply { putAll(captured) }
        applyTemplateValues(tpl, stored)
    }

    /** 模板弹窗点「用这个模板」：保存字段值并填入输入框 */
    fun applyTemplateValues(tpl: Template, values: Map<String, String>) {
        templateValues = templateValues + (tpl.name to values)
        val text = buildString {
            append("【模板：").append(tpl.name).append("】\n")
            append(
                if (tpl.mode == "角色") "（下面描述的是这个角色 TA 本身，不是用户自己）\n"
                else "（下面描述的是故事世界，以及用户自己在那里的位置）\n"
            )
            tpl.fields.forEach { f ->
                val v = values[f]?.trim().orEmpty()
                if (v.isNotBlank()) append(f).append("：").append(v).append("\n")
                else append(f).append("：（待填）").append("\n")
            }
        }.trim()
        if (tpl.mode == "角色") {
            prompt = text
            tab = "角色"
            // 第 79 轮：模板带形态就**顺带把形态切过去**——不然用户从「工具」模板填完字段，
            // 生成出来的仍是陪伴卡（生成器按形态要的字段完全不同，模板也就白填了）。
            formTag = tpl.form
            Repository.saveDraft(DRAFT_CHARACTER, text)
        } else {
            storyPrompt = text
            tab = "故事"
            Repository.saveDraft(DRAFT_STORY, text)
        }
        result = null
        imageUri = null
    }

    /** 只保存模板字段值（不跳转、不填入），供推荐 chip 直接复用 */
    fun saveTemplateValues(tpl: Template, values: Map<String, String>) {
        templateValues = templateValues + (tpl.name to values)
    }

    /** 从已有输入里解析某模板各字段已填的值（跳过“（待填）”占位） */
    private fun captureFields(text: String, fields: List<String>): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val lines = text.lines()
        val result = mutableMapOf<String, String>()
        for (f in fields) {
            val line = lines.firstOrNull { it.trim().startsWith("$f：") || it.trim().startsWith("$f:") }
            if (line != null) {
                val v = line.substringAfter("：", "").substringAfter(":", "").trim()
                if (v.isNotBlank() && v != "（待填）") result[f] = v
            }
        }
        return result
    }

    fun generate() {
        // 各分段校验各自的输入框；模板 tab 无直接生成入口，故事创作引擎未落地也拦
        val input = when (tab) {
            "形象" -> imagePrompt
            "世界书" -> worldBookPrompt
            else -> prompt
        }
        if (generating || input.isBlank() || tab == "故事") return
        generating = true
        error = null
        viewModelScope.launch {
            try {
                val settings = Repository.loadSettings()
                if (tab == "形象") {
                    // 问题 #34：形象页用独立输入框 imagePrompt
                    // 带参考图：视觉模型先把参考图转写成画图提示词，再拼接用户意图
                    val finalPrompt = if (imageRef != null) {
                        try {
                            AiClient.visionText(settings, imagePrompt, imageRef!!) + "。补充要求：$imagePrompt"
                        } catch (t: Throwable) {
                            error = "参考图转译失败（${t.message}），已改用原描述生成"
                            imagePrompt
                        }
                    } else imagePrompt
                    imageUri = AiClient.generateImage(settings, finalPrompt, imageSize)
                    imageRef = null
                } else if (tab == "世界书") {
                    // 台账 69：创作模型产一本世界书，预览后由用户保存到宝库
                    worldBookResult = WorldBookGenerator.generate(settings, worldBookPrompt)
                } else {
                    result = CharacterGenerator.generate(settings, prompt, categories, formTag)
                }
            } catch (t: Throwable) {
                error = t.message ?: "生成失败"
            } finally {
                generating = false
            }
        }
    }

    /**
     * 形象页「重新生成」：**保留提示词**、只清掉旧结果并直接重发（P1-2）。
     *
     * 以前这里点的是 `reset()`，会把 `imagePrompt` 一起清空 —— 而「生成形象」按钮的可用条件
     * 正是 `imagePrompt.isNotBlank()`，于是点完按钮立刻变灰，用户必须重新手打提示词。
     * 写法对齐 `CharacterEditorScreen.retryPendingImage()`：只清结果，复用原输入重发。
     */
    fun regenerateImage() {
        if (generating || imagePrompt.isBlank()) return
        imageUri = null
        error = null
        generate()
    }

    /**
     * 清掉**本分段**的生成结果，让用户回到输入区。
     * ⚠️ 只清结果，不要连输入一起清、更不要跨分段清：以前它顺带清掉
     * `imagePrompt` / `storyPrompt`，在形象页误触就会连带丢掉角色页与故事页的输入（P1-2）。
     */
    fun reset() {
        result = null
    }

    /** 世界书分段：清掉生成结果回到输入区（输入与草稿不动） */
    fun resetWorldBook() {
        worldBookResult = null
    }

    /**
     * 世界书结果存进宝库（台账 69）：存完不清结果，用户看完提示还能自己决定去留。
     * 挂到哪张卡在宝库的书编辑区逐卡开关（台账 67），这里只负责入库。
     */
    fun saveWorldBookToLibrary() {
        val b = worldBookResult ?: return
        Repository.saveWorldBook(Repository.newId(), b) { t ->
            showToast("保存失败：${t.message}", long = true)
        }
        showToast("已保存到宝库 · 世界书，可在「宝库 → 世界书」里挂到角色卡上")
    }

    fun clearError() { error = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCreateScreen(
    onCreated: (CharacterCard) -> Unit,
    onEdit: (String) -> Unit,
    /**
     * 桌面三栏：**第三栏内嵌**。为真时不再自带顶栏与四个分段胶囊——那些由第一/第二栏承担，
     * 这里只画内容（否则第三栏里会同时出现"顶栏 + 第二栏模式列表"两套一模一样的导航）。
     * 手机端恒为 false，行为零变化。
     */
    embedded: Boolean = false,
    vm: AiCreateViewModel = viewModel(factory = noArgViewModelFactory { AiCreateViewModel() })
) {
    // 第 90 轮（台账 12 ②）：原来这里有一行"当前分段名"的小标题（"模板""形象"…），用户 2026-09-23
    // 的原话是「"模板"二字都顶到头了，不好看，主栏目不要再写小标题」——第二栏已经把当前分段高亮着，
    // 第三栏再写一遍是重复的导航噪音，直接去掉，正文从最上方开始。
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            // 顶部 Tab：手机端与"整页"形态靠它切分段；桌面三栏由第二栏切，不再重复画一排
            if (!embedded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("模板", "角色", "形象", "世界书", "故事").forEach { t ->
                        WhaleChip(selected = vm.tab == t, onClick = { vm.updateTab(t) }, label = { Text(t) })
                    }
                }
            }

            when (vm.tab) {
                "模板" -> TemplateBoard(vm, hideTitle = embedded)
                "形象" -> ImageMode(vm)
                "世界书" -> WorldBookMode(vm)
                "故事" -> StoryMode(vm)
                else -> RoleMode(vm, onCreated, onEdit)
            }
        }
    }

    if (embedded) {
        // 内嵌时不套 Scaffold：第三栏不是"一整页"，再套一层背景与顶栏会与外壳打架
        body(PaddingValues(0.dp))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("灵感创作") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding -> body(padding) }
    }

    ErrorBanner(message = vm.error, onDismiss = { vm.clearError() })
}

/**
 * 桌面第二栏（灵感创作页）：**创作分段 + 模板分类**（用户三栏方案 §十 第二阶段 ①）。
 *
 * 上半段是四个创作分段（模板/角色/形象/故事），下半段只在「模板」分段下出现——即"模板分类"。
 * 分段本来就住在这个 VM 里，所以第二栏与第三栏天然共用同一份状态（`vm.tab` / `vm.templateFilter`）。
 */
@Composable
internal fun AiCreateRail(
    vm: AiCreateViewModel,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val modes = listOf(
        "模板" to Icons.Filled.Category,
        "角色" to Icons.Filled.Person,
        "形象" to Icons.Filled.Image,
        "世界书" to Icons.AutoMirrored.Filled.MenuBook,
        "故事" to Icons.Filled.AutoStories
    )
    val roleCustom = vm.customTemplates.count { it.mode == "角色" }
    val storyCustom = vm.customTemplates.count { it.mode == "故事" }
    // 计数按**形态**分（第 79 轮）：模板分区与 [TemplateFilters] 用的是同一份 [Engines] 顺序，
    // 这里若再自己写一遍"角色/故事"就会立刻与分区不一致（老代码就是这么腐化的）。
    val counts = buildMap {
        put(
            TemplateFilterAll,
            FormTemplates.sumOf { it.second.size } + StoryTemplates.size + vm.customTemplates.size
        )
        FormTemplates.forEach { (label, list) ->
            put(label, list.size + vm.customTemplates.count { Engines.of(it.form).label == label })
        }
        put(TemplateFilterStory, StoryTemplates.size + storyCustom)
        put(TemplateFilterMine, roleCustom + storyCustom)
    }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow)
    ) {
        DesktopRailHeader("灵感创作")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(modes) { (label, icon) ->
                val selected = vm.tab == label
                DesktopRailRow(
                    label = label,
                    selected = selected,
                    onClick = { vm.updateTab(label) },
                    leading = {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
                        )
                    }
                )
            }
            if (vm.tab == "模板") {
                item { DesktopRailSectionLabel("模板分类") }
                items(TemplateFilters) { f ->
                    DesktopRailRow(
                        label = f,
                        selected = vm.templateFilter == f,
                        count = counts[f],
                        indent = 6.dp,
                        onClick = { vm.updateTemplateFilter(f) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateBoard(vm: AiCreateViewModel, hideTitle: Boolean = false) {
    var openTemplate by remember { mutableStateOf<Template?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    // 分类筛选（桌面三栏第二栏）：手机端恒为「全部」，等价于不筛
    val f = vm.templateFilter
    // 分类即**形态**（第 79 轮）：筛「陪伴」只出陪伴模板，筛「全部」时逐形态分区列出。
    // 「我的」只列自建（角色/故事都在），「全部」时自建跟在各自的内置模板后面。
    val showMine = f == TemplateFilterMine
    val showStory = f == TemplateFilterAll || f == TemplateFilterStory
    val roleCustom = vm.customTemplates.filter { it.mode == "角色" }
    val storyCustom = vm.customTemplates.filter { it.mode == "故事" }
    // 第 90 轮（台账 12 ②）：模板卡从"一行一张拉满整宽"改成**自适应多列网格**——第三栏宽了就自动
    // 多摆几列，这才像桌面（手机端可用宽度 < 340dp，网格自动退化成 1 列，观感与原来一致）。
    // 分区标题、说明句、底部那颗「＋ 创建模板」占**整行**（span = maxLineSpan），只有卡片进格子。
    val fullRow: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!hideTitle) {
            item(span = fullRow) {
                // 注意：网格 item 里的多个子节点会被**叠在同一原点**（行高只按最高的那个算），
                // 所以标题与说明必须显式包 Column 分两行——直接并排放会印成一行重影。
                Column {
                    Text("模板", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "点模板会先弹出字段填写框，填完再应用到对应页；已填过的字段会记住。自建模板可删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (showMine) {
            if (vm.customTemplates.isEmpty()) {
                item(span = fullRow) {
                    Text(
                        "还没有自建模板。点下面的「＋ 创建模板」新建一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(vm.customTemplates, key = { it.id }) { c ->
                TemplateCard(
                    c.toUiTemplate(),
                    onClick = { openTemplate = c.toUiTemplate() },
                    onDelete = { deleteTarget = c.id }
                )
            }
        }
        FormTemplates.forEach { (label, list) ->
            if (f == TemplateFilterAll || f == label) {
                item(span = fullRow) {
                    Column {
                        Text("$label 模板", style = MaterialTheme.typography.titleSmall)
                        // createHint 自带「陪伴：」这样的前缀（手机端单独一行显示时需要它），
                        // 这里标签就在正上方，去掉前缀免得读成重复。
                        Text(
                            Engines.of(list.firstOrNull()?.form.orEmpty()).createHint.removePrefix("$label："),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(list) { t -> TemplateCard(t, onClick = { openTemplate = t }) }
                if (!showMine) {
                    items(roleCustom.filter { Engines.of(it.form).label == label }, key = { it.id }) { c ->
                        TemplateCard(
                            c.toUiTemplate(),
                            onClick = { openTemplate = c.toUiTemplate() },
                            onDelete = { deleteTarget = c.id }
                        )
                    }
                }
            }
        }
        if (showStory) {
            item(span = fullRow) {
                Column {
                    Text("故事模板", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "字段问的是「世界」和「你」——你要进入的场景、你在其中的身份。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(StoryTemplates) { t -> TemplateCard(t, onClick = { openTemplate = t }) }
            if (!showMine) {
                items(storyCustom, key = { it.id }) { c ->
                    TemplateCard(
                        c.toUiTemplate(),
                        onClick = { openTemplate = c.toUiTemplate() },
                        onDelete = { deleteTarget = c.id }
                    )
                }
            }
        }
        item(span = fullRow) {
            OutlinedButton(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("＋ 创建模板")
            }
        }
    }

    openTemplate?.let { t ->
        TemplateDialog(
            template = t,
            initialValues = vm.templateValues[t.name] ?: emptyMap(),
            onDismiss = { openTemplate = null },
            onUse = { values -> vm.applyTemplateValues(t, values); openTemplate = null },
            onSave = { values -> vm.saveTemplateValues(t, values); openTemplate = null }
        )
    }

    if (showCreate) {
        CreateTemplateDialog(
            onDismiss = { showCreate = false },
            onSave = { c ->
                vm.addTemplate(c)
                showCreate = false
            }
        )
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个模板？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTemplate(id); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

private fun CustomTemplate.toUiTemplate(): Template =
    Template(name = name, mode = mode, fields = fields, form = form)

@Composable
private fun TemplateCard(t: Template, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "需填写：" + t.fields.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            onDelete?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Close, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 模板字段填写框：预填已存字段；「用这个模板」应用并跳转，「保存」只存字段供推荐 chip 复用 */
@Composable
private fun TemplateDialog(
    template: Template,
    initialValues: Map<String, String>,
    onDismiss: () -> Unit,
    onUse: (Map<String, String>) -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    var values by remember { mutableStateOf(template.fields.associateWith { initialValues[it].orEmpty() }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(template.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                template.fields.forEach { f ->
                    OutlinedTextField(
                        value = values[f].orEmpty(),
                        onValueChange = { values = values + (f to it) },
                        label = { Text(f) },
                        singleLine = true
                    )
                }
                TextButton(onClick = { onSave(values) }, modifier = Modifier.fillMaxWidth()) {
                    Text("保存（不跳转）")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onUse(values) }) { Text("用这个模板") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 创建模板：先选 角色/故事，再填模板名与字段，保存后进对应分类 */
@Composable
private fun CreateTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (CustomTemplate) -> Unit
) {
    var mode by remember { mutableStateOf("角色") }
    var form by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var fieldsText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("先选模板类型", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("角色", "故事").forEach { m ->
                        WhaleChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                    }
                }
                // 形态（第 79 轮）：角色模板要认形态，套用时会一并把形态切过去
                if (mode == "角色") {
                    Text("用哪种形态", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Engines.all.forEach { e ->
                            WhaleChip(
                                selected = form == e.formTag,
                                onClick = { form = e.formTag },
                                label = { Text(e.label) }
                            )
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("模板名称") }, singleLine = true)
                OutlinedTextField(
                    value = fieldsText,
                    onValueChange = { fieldsText = it },
                    label = { Text("需填字段（每行一个）") },
                    minLines = 3,
                    placeholder = { Text("例：\nTA的身份\n你的身份\n攻略目标（选填）") }
                )
                Text(
                    "保存后模板出现在「${if (mode == "角色") Engines.of(form).label else mode} 模板」区，点它可填写字段再生成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fields = fieldsText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
                    onSave(
                        CustomTemplate(
                            id = Repository.newId(),
                            name = name.trim(),
                            mode = mode,
                            fields = fields,
                            form = if (mode == "角色") form else ""
                        )
                    )
                },
                enabled = name.isNotBlank() && fieldsText.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 问题 #17：生图尺寸与「生成形象」按钮合并成一块，按钮紧跟在选项下面。
 * chip 用 FlowRow 自动换行，窄屏也不会被挤成竖条。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageSizeAndGenerate(vm: AiCreateViewModel) {
    val pickRef = rememberImagePicker { uri -> vm.imageRef = uri }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 参考图：视觉模型把参考图转写成画图提示词后再生图
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WhaleChip(
                selected = vm.imageRef != null,
                onClick = pickRef,
                label = { Text(if (vm.imageRef != null) "参考图 ✓（点击更换）" else "+ 参考图") }
            )
            if (vm.imageRef != null) {
                WhaleChip(selected = false, onClick = { vm.imageRef = null }, label = { Text("清除") })
            }
        }
        // #6：生图尺寸在生图时选，不再藏在「模型设置」里；形象默认方形
        Text("尺寸", style = MaterialTheme.typography.bodySmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ImageSizeChips(selected = vm.imageSize, onSelect = { vm.imageSize = it })
        }
        AiActionButton(
            label = "生成形象",
            onClick = { vm.generate() },
            busy = vm.generating,
            enabled = !vm.generating && vm.imagePrompt.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoleMode(vm: AiCreateViewModel, onCreated: (CharacterCard) -> Unit, onEdit: (String) -> Unit) {
    // 类型列表不是 State（CategoryManager 是个普通 object），新增后不会自动重组——
    // 所以这里存一份，管理弹窗回调时刷新（2026-09-17：本页也能新建类型，见下方 chip）
    var allCategories by remember { mutableStateOf(CategoryManager.all()) }
    var showManageCategories by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PromptField(
                value = vm.prompt,
                onValueChange = { vm.updatePrompt(it) },
                label = "输入你想创建的角色",
                placeholder = "例：赛博朋克世界的冷面女黑客，表面孤僻内心重情"
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CharacterSuggestions) { s ->
                    // 形态模板也算在这里（第 79 轮）：模板名先在四种形态里找，找不到才当普通提示词
                    val tpl = FormTemplates.asSequence().flatMap { it.second.asSequence() }
                        .firstOrNull { it.name == s }
                    WhaleChip(
                        selected = false,
                        onClick = { if (tpl != null) vm.applyTemplate(tpl) else vm.updatePrompt(s) },
                        label = { Text(s) }
                    )
                }
            }
        }
        item {
            Text("类型 / 标签（可多选，不选则交给模型判断）", style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WhaleChip(
                    selected = vm.categories.isEmpty(),
                    onClick = { vm.clearCategories() },
                    label = { Text("自动") }
                )
                allCategories.forEach { c ->
                    WhaleChip(
                        selected = c in vm.categories,
                        onClick = { vm.toggleCategory(c) },
                        label = { Text(c) }
                    )
                }
                // 新建/管理类型（2026-09-17 用户口径：这一页也要能新建，不必绕到「角色卡」页顶部去管理）。
                // 复用同一张弹窗，不新写一套增删排序。
                WhaleChip(
                    selected = false,
                    onClick = { showManageCategories = true },
                    label = { Text("＋ 新建类型") }
                )
            }
        }
        item {
            Text("形态标签", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CharacterFormTags.forEach { (label, value) ->
                    WhaleChip(
                        selected = vm.formTag == value,
                        onClick = { vm.formTag = value },
                        label = { Text(label) }
                    )
                }
            }
            // 说明文字随形态走（第 75 轮）：它得与**生成器真正会要的字段**说同一件事 ——
            // 写死在页面上（原来只有多线那一句）就会出现"选了工具、说明还讲开场白"。
            Text(
                Engines.of(vm.formTag).createHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            AiActionButton(
                label = "生成角色卡",
                onClick = { vm.generate() },
                busy = vm.generating,
                enabled = !vm.generating && vm.prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        vm.result?.let { item { GeneratedCard(it, onCreated, onEdit, onRetry = { vm.reset() }) } }
    }

    // 类型管理弹窗必须放在 LazyColumn **外面**：塞进 item 里的话，滚不到那一项就不会被组合，
    // 弹窗自然也不显示（LazyColumn 只组合可见项）。
    if (showManageCategories) {
        ManageCategoriesDialog(
            onDismiss = { showManageCategories = false },
            onChanged = { allCategories = CategoryManager.all() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageMode(vm: AiCreateViewModel) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PromptField(
                value = vm.imagePrompt,
                onValueChange = { vm.updateImagePrompt(it) },
                label = "输入你想创建的形象",
                placeholder = "例：赛博朋克世界的冷面女黑客，立绘，半身像"
            )
        }
        item {
            // 0.1.2：一句话描述也能出图——先让 AI 扩写成详细提示词，再手动微调
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiActionButton(
                    label = "AI 扩写",
                    busyLabel = "扩写中…",
                    onClick = { vm.expandImagePrompt() },
                    busy = vm.expanding,
                    enabled = !vm.expanding && !vm.generating && vm.imagePrompt.isNotBlank(),
                    filled = false,
                    starSize = 16.dp,
                    gap = 6.dp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                )
                Text(
                    "一句话也能出图：AI 补全画风、构图与光影",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ImageSuggestions) { s -> WhaleChip(selected = false, onClick = { vm.updateImagePrompt(s) }, label = { Text(s) }) }
            }
        }
        item { ImageSizeAndGenerate(vm) }
        vm.imageUri?.let { uri ->
            item {
                SectionCard(title = "生成的形象", titleGap = 0.dp, contentPadding = 12.dp, spacing = 8.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(model = imageModel(uri), contentDescription = "生成的形象", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (!saving) {
                                        saving = true
                                        scope.launch {
                                            val ok = Platform.ui.saveImageToGallery(uri)
                                            saving = false
                                            showToast(
                                                // P2-A14：文案与共享实现 rememberGallerySaver() 保持一致
                                                if (ok) "已保存到相册（Pictures/MystiCat）" else "保存失败，请重试"
                                            )
                                        }
                                    }
                                },
                                enabled = !saving,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (saving) "保存中…" else "保存到相册") }
                            OutlinedButton(
                                onClick = { vm.regenerateImage() },
                                enabled = !vm.generating && vm.imagePrompt.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("重新生成") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 问题 #34：灵感创作 prompt 输入框，右下角带清空叉号。
 * Box 包裹让叉号 align 到 BottomEnd——随文本行数（框高度）贴在右下，适应文本框长度。
 */
@Composable
private fun PromptField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            minLines = 3,
            placeholder = { Text(placeholder) }
        )
        if (value.isNotEmpty()) {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 世界书创作（台账 69）：描述一个世界 / 主题 → 创作模型产一本世界书 → 预览 → 存进宝库。
 * 保存后不自动挂到任何卡：挂书在宝库的书编辑区逐卡开关（台账 67），这里只负责入库。
 */
@Composable
private fun WorldBookMode(vm: AiCreateViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PromptField(
                value = vm.worldBookPrompt,
                onValueChange = { vm.updateWorldBookPrompt(it) },
                label = "输入你想创建的世界书",
                placeholder = "例：修真仙侠世界的门派、地理、灵物与修炼体系"
            )
        }
        item {
            Text(
                "生成的是一组「关键词触发的背景资料」：聊天里出现条目的关键词，那条设定才会被注入。" +
                    "生成后保存到宝库，再到「宝库 → 世界书」把它挂到角色卡上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            AiActionButton(
                label = "生成世界书",
                onClick = { vm.generate() },
                busy = vm.generating,
                enabled = !vm.generating && vm.worldBookPrompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        vm.worldBookResult?.let { book ->
            item {
                GeneratedWorldBookCard(
                    book,
                    onSave = { vm.saveWorldBookToLibrary() },
                    onRetry = { vm.resetWorldBook() }
                )
            }
        }
    }
}

@Composable
private fun GeneratedWorldBookCard(book: WorldBook, onSave: () -> Unit, onRetry: () -> Unit) {
    SectionCard(title = "生成结果", titleGap = 0.dp, contentPadding = 16.dp, spacing = 8.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                book.name.ifBlank { "未命名世界书" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (book.description.isNotBlank()) Text(book.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "共 ${book.entries.size} 条条目 · 常驻 ${book.entries.count { it.constant }} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            book.entries.forEach { e ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.name.ifBlank { "未命名条目" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (e.constant) {
                            Text(
                                "常驻",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (e.keys.isNotEmpty()) {
                        Text(
                            "关键词：" + e.keys.joinToString("、"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        e.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("保存到宝库") }
                OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("重新创作") }
            }
        }
    }
}

@Composable
private fun StoryMode(vm: AiCreateViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("故事", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "故事：基于较完整世界观 + 初始剧情 + 多角色，用户可改变走向但受世界观看似约束。\n" +
                "· 剧情对话：相对完整，每次给 2~3 个选项（橙光式）\n" +
                "· 开放对话：高自由度，自由对话\n" +
                "· 创作需世界观/剧情，可选角色卡\n（故事创作+游玩引擎下一步开发）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("推荐故事模板（点击直接填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(StoryTemplates) { t ->
                WhaleChip(selected = false, onClick = { vm.applyTemplate(t) }, label = { Text(t.name) })
            }
        }
        if (vm.storyPrompt.isNotBlank()) {
            // 标题行带叉号（不是纯标题），所以标题留在内容里，只把外面的卡壳交给 SectionCard 摘
            SectionCard(shape = RoundedCornerShape(14.dp), elevation = 0.dp, contentPadding = 0.dp, spacing = 0.dp) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已从模板填入",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        // 叉号：提示词不自动删，只有这个动作会清（第 63 轮，与另两处输入框一致）
                        IconButton(onClick = { vm.clearPromptDraft(DRAFT_STORY) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空故事提示词",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(vm.storyPrompt, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun GeneratedCard(
    card: CharacterCard,
    onCreated: (CharacterCard) -> Unit,
    onEdit: (String) -> Unit,
    onRetry: () -> Unit
) {
    SectionCard(title = "生成结果", titleGap = 0.dp, contentPadding = 16.dp, spacing = 8.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(categoryLabel(card.categoriesOrDefault()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (card.tagline.isNotBlank()) Text(card.tagline, style = MaterialTheme.typography.bodyMedium)
            // 形态决定这里显示哪几个字段（第 75 轮）：工具卡的字段与角色卡不是同一套，
            // 照角色卡显示的话用户看到的是"人设：书面、简洁"这种读不通的条目，
            // 而真正要看的「任务说明 / 输出格式」一个字都不显示。
            if (card.isTool()) {
                if (card.taskBrief.isNotBlank()) Labeled("任务说明", card.taskBrief)
                if (card.outputFormat.isNotBlank()) Labeled("输出格式", card.outputFormat)
                val styleRef = listOf(card.description, card.personality)
                    .filter { it.isNotBlank() }.joinToString("\n")
                if (styleRef.isNotBlank()) Labeled("风格参考", styleRef)
                val examples = card.effectiveGreetings()
                if (examples.isNotEmpty()) Labeled("使用示例", examples.joinToString("\n"))
            } else if (card.isPlay()) {
                // 玩法形态（E4 拆档）：要看的同样是它真正会生效的那几个字段 —— 规则与状态项
                // 决定这一局好不好玩；照角色卡显示"人设 / 场景 / 开场白"，用户根本判断不出能不能玩。
                if (card.playRules.isNotBlank()) Labeled("玩法规则", card.playRules)
                if (card.playState.isNotBlank()) Labeled("要记的状态", card.playState)
                Labeled("每轮选项", if (card.playOptions == 0) "不给选项（自由作答）" else "${card.playOptions} 个")
                if (card.scenario.isNotBlank()) Labeled("玩法设定", card.scenario)
                if (card.greeting.isNotBlank()) Labeled("开局引导", card.greeting)
                val styleRef = listOf(card.description, card.personality)
                    .filter { it.isNotBlank() }.joinToString("\n")
                if (styleRef.isNotBlank()) Labeled("风格参考", styleRef)
            } else {
                if (card.persona.isNotBlank()) Labeled("人设", card.persona)
                if (card.scenario.isNotBlank()) Labeled("场景", card.scenario)
                if (card.greeting.isNotBlank()) Labeled("开场白", card.greeting)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 保存是异步的（第 4 轮）：失败必须接住并提示，否则"保存并开聊"看着成功、角色却没落盘
                val onFailure = writeFailureToast(null, "保存角色")
                Button(
                    onClick = { Repository.saveCharacter(card, onFailure); onCreated(card) },
                    modifier = Modifier.weight(1f)
                ) { Text("保存并开聊") }
                OutlinedButton(
                    onClick = { Repository.saveCharacter(card, onFailure); onEdit(card.id) },
                    modifier = Modifier.weight(1f)
                ) { Text("编辑") }
            }
            TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重新创作") }
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
