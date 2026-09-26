package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.WhaleBackHandler
import kotlinx.coroutines.launch

/**
 * 使用手册：内容随包走、**离线可读**，手机端与桌面端共用同一份。
 *
 * 内容源是 `shared/assets/manual/manual.md`——与内置环境音同一个读取口（Gradle 把 `shared/assets/`
 * 同时挂给 APK 与桌面 jar 的 JVM 资源，见 `shared/build.gradle.kts` 的两处 `resources.srcDir`），
 * 所以两个宿主都不必另加注入口。**不走网络是硬要求**：本项目是纯本地 BYOK，把说明书只挂在网址上，
 * 断网或镜像不通时用户就什么也查不到。
 *
 * 渲染**刻意不复用** `buildMessageBody`：那份是给聊天气泡写的（括号注浅色、旁白行加粗），而手册的格式
 * 是我们自己定的（`#` 标题 / `##` 节 / `###` 小节 / `-` 列表 / `>` 提示），逐行渲染反而更准，节标题也
 * 才能真正长成标题的样子、目录才能按节跳转。行内只认 `**加粗**` 与 `` `等宽` ``。
 */
private const val MANUAL_ASSET = "manual/manual.md"

/**
 * 读包内手册正文（读不到时给一句兜底，绝不留白屏）。
 *
 * **公开给自检**（与 `DirScanCache` 同规矩）：手册是"随包资源"，最容易出的静默故障就是**资源没打进包**——
 * 界面照样打开、只是空白，兜底文案也不崩。桌面 `--smoke` 直接断言正文，才盖得住这一类。
 */
fun manualText(): String = runCatching {
    // 类加载器从本文件的**数据类**上取（不能写 `ManualScreen::class`——那是 @Composable 函数）
    ManualDoc::class.java.classLoader
        ?.getResourceAsStream(MANUAL_ASSET)
        ?.use { it.readBytes().decodeToString() }
}.getOrNull()?.takeIf { it.isNotBlank() }
    ?: "手册内容缺失：这个安装包没有打入手册资源，请重新下载安装包。"

/** 手册的一节（`## ` 标题 + 其下全部行）；标题即目录项。 */
class ManualSection(val title: String, val lines: List<String>)

/** 手册解析结果：开头说明（第一个 `##` 之前的部分，含一级标题与引言）＋ 各节。 */
class ManualDoc(val preamble: List<String>, val sections: List<ManualSection>)

/**
 * 按行解析手册（**公开给自检**）。**只认 `## ` 作为节边界**（`### ` 是小节，`#` 是文首标题），
 * 其余行原样落到当前节里、由渲染端按前缀决定长相——解析与排版分开，加一节内容不用改代码。
 */
fun parseManual(raw: String): ManualDoc {
    val preamble = ArrayList<String>()
    val sections = ArrayList<ManualSection>()
    var title: String? = null
    var buf = ArrayList<String>()

    fun flush() {
        val t = title
        if (t != null) {
            sections += ManualSection(t, buf.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() })
        }
        buf = ArrayList()
    }

    raw.replace("\r\n", "\n").split("\n").forEach { line ->
        val head = line.trimStart()
        if (head.startsWith("## ") && !head.startsWith("### ")) {
            flush()
            title = head.removePrefix("## ").trim()
        } else if (title == null) {
            preamble += line
        } else {
            buf += line
        }
    }
    flush()
    return ManualDoc(preamble.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }, sections)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualScreen(onBack: () -> Unit, embedded: Boolean = false) {
    // 返回键（安卓）/ Esc（桌面）＝退回上一层；内嵌进第三栏时 onBack 由外壳给成"关掉整页"
    WhaleBackHandler { onBack() }
    val doc = remember { parseManual(manualText()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val body: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ManualLines(doc.preamble) }
            item {
                ManualToc(doc.sections) { index ->
                    // 目录项 → 该节：0 开头说明、1 目录，节从 2 起
                    scope.launch { listState.animateScrollToItem(index + 2) }
                }
            }
            itemsIndexed(doc.sections) { _, section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ManualHeading(section.title, 2)
                    ManualLines(section.lines)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "使用手册",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
            body()
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("使用手册") },
                    navigationIcon = { BackArrowButton(onBack) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) { body() }
        }
    }
}

/**
 * 目录卡片：一眼看清手册有哪些节，点一下就跳过去。
 *
 * 用 [FlowRow] 的标签而不是一列行——手册有十几节，竖排会把整屏占满、正文要滚很久才看得到
 * （桌面窗口本来就宽，横着排更省地方）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualToc(sections: List<ManualSection>, onJump: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("目录", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEachIndexed { index, section ->
                Text(
                    "${index + 1}. ${section.title}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { onJump(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** 逐行渲染一段手册文本（节标题也走这里，所以标题与小节用同一套口径）。 */
@Composable
private fun ManualLines(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            when {
                line.isBlank() -> Spacer(Modifier.height(2.dp))
                line.trim() == "---" -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                line.trimStart().startsWith("# ") -> ManualHeading(line.trimStart().removePrefix("# "), 1)
                line.trimStart().startsWith("### ") -> ManualHeading(line.trimStart().removePrefix("### "), 3)
                line.trimStart().startsWith("## ") -> ManualHeading(line.trimStart().removePrefix("## "), 2)
                line.trimStart().startsWith("> ") -> ManualCallout(line.trimStart().removePrefix("> "))
                else -> ManualParagraph(line)
            }
        }
    }
}

@Composable
private fun ManualHeading(text: String, level: Int) {
    Text(
        text,
        style = when (level) {
            1 -> MaterialTheme.typography.headlineSmall
            2 -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleSmall
        },
        fontWeight = FontWeight.Bold,
        color = if (level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = if (level == 1) 4.dp else 8.dp)
    )
}

/** `>` 提示块：浅底一行，用来放"注意 / 前提"这类不该被略过的话。 */
@Composable
private fun ManualCallout(text: String) {
    Text(
        manualInline(text, MaterialTheme.colorScheme.primary),
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = (13f * ThemeState.fontScale).sp,
            lineHeight = (19f * ThemeState.fontScale).sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/**
 * 正文一行：`- ` 列表（含两级缩进，子项按原文前导空格数判断）或普通段落（**编号行原样保留序号**，
 * 手写手册里序号本身就是内容）。缩进按原文的空格数决定，渲染端不再猜结构。
 */
@Composable
private fun ManualParagraph(raw: String) {
    val indentSteps = ((raw.length - raw.trimStart().length) / 2).coerceIn(0, 2)
    val trimmed = raw.trimStart()
    val bullet = trimmed.startsWith("- ") || trimmed.startsWith("* ")
    val content = if (bullet) trimmed.drop(2) else trimmed
    Row(Modifier.padding(start = (indentSteps * 14).dp)) {
        if (bullet) {
            Text(
                "·",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (15f * ThemeState.fontScale).sp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            manualInline(content, MaterialTheme.colorScheme.primary),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = (15f * ThemeState.fontScale).sp,
                lineHeight = (21f * ThemeState.fontScale).sp
            )
        )
    }
}

/** 行内标记：**加粗** 与 `等宽`（等宽用主色，代码/字段名一眼可辨）。 */
private val ManualInline = Regex("\\*\\*([^*\\n]+)\\*\\*|`([^`\\n]+)`")

internal fun manualInline(text: String, codeColor: Color): AnnotatedString {
    val out = buildAnnotatedString {
        var cursor = 0
        ManualInline.findAll(text).forEach { m ->
            if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
            val bold = m.groupValues[1]
            if (bold.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            } else {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) {
                    append(m.groupValues[2])
                }
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
    return out
}
