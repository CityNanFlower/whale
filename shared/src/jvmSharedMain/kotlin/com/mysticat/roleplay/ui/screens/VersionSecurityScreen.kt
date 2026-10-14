package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.RemoteFetch
import com.mysticat.roleplay.ui.NoticeLine
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.SectionCard
import com.mysticat.roleplay.ui.UpdateFlow
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhalePageTopBar
import com.mysticat.roleplay.ui.components.WhaleFieldGroup
import com.mysticat.roleplay.ui.components.WhaleFieldRow
import com.mysticat.roleplay.ui.components.WhaleRowCaret
import com.mysticat.roleplay.ui.components.WhaleRowChevron
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSpacing
import java.io.File

/**
 * 「关于鲸鱼」模块的对外信息。
 * 换链接 / 发新版只改这一处。
 *
 * ⚠️ 版本号不再在这里写死（曾因漏改这里的常量导致新包显示旧版本号、误报更新），
 * 一律运行时从 PackageManager 读真实值，见下方 currentVersionName/currentVersionCode。
 */
object AppLinks {

    /**
     * 意见反馈的两条路（用户 2026-10-14 定：应用内反馈既能去 GitHub、也能去飞书）。
     * 题目是同一套——GitHub 那边是仓库里的「问题反馈」表单，飞书那边是匿名表单。
     *
     * [FEEDBACK_ISSUE_URL]：GitHub 新开一条反馈单。**公开**，好处是能跟进、别人也搜得到同类问题；
     * 代价是要有 GitHub 账号。`?template=` 指的就是 `.github/ISSUE_TEMPLATE/feedback.yml`——
     * 改名要同步改这里（改错了点开是空白表单，不会报错，只能靠人发现）。
     */
    const val FEEDBACK_ISSUE_URL = "https://github.com/CityNanFlower/whale/issues/new?template=feedback.yml"

    /** [FEEDBACK_URL]：飞书多维表格表单，**匿名可填**（不用账号、不公开）。留空时页面会提示「尚未配置」。 */
    const val FEEDBACK_URL = "https://ncnsnhd97qtl.feishu.cn/share/base/shrcnSLW2NhCHINp7tsP88tYUWb"

    /**
     * 历史更新日志（飞书文档）。应用内只展示当前版本，更早的版本去这里看。
     * 两端链接不同（用户口径：更新日志的链接不用同一份）。
     * 两份均为「互联网上获得链接的任何人可阅读」的公开文档。
     */
    val CHANGELOG_DOC_URL: String
        get() = if (Platform.ui.platformId() == "desktop")
            "https://ncnsnhd97qtl.feishu.cn/docx/HePFdEAdPo6ZOCxlXhacQUYlnVh"
        else "https://ncnsnhd97qtl.feishu.cn/docx/UIUidiOqWoyd4MxlS2jcLAyOnOh"

    /**
     * 检查更新用的版本信息 JSON 地址。内容形如：
     * {"versionCode":6,"versionName":"0.1.0-alpha.4","url":"https://…/鲸鱼-0.1.0-alpha.4.apk","notes":"…"}
     *
     * ⚠️ 注意：这个地址必须能被**匿名 GET** 到。GitHub/Gitee 的**私有仓库** raw 与 Release
     * 附件都要求 token 认证，匿名请求会 404 —— 真要私有就得填下面的 token。
     * 留空 = 更新源未配置，点「检查更新」会提示。
     *
     * 更新通道收编进公开源码仓 `whale`——这里取的就是仓里的 `dist/发布用-version.json`
     * （发布流程同步过去的原件，与安装包 Release 同仓）。路径含中文，所以写成百分号编码形态：
     * 三家加速镜像都实测能原样代理（直连 raw 在国内不通属已知情况，它只作兜底）。
     * 万一哪家镜像把它编坏了，再在公开树里补一份 ASCII 别名的副本，改这一行即可。
     */
    const val UPDATE_JSON_RAW =
        "https://raw.githubusercontent.com/CityNanFlower/whale/main/dist/%E5%8F%91%E5%B8%83%E7%94%A8-version.json"

    /**
     * GitHub 加速镜像前缀（国内直连 GitHub 会超时）。检查更新与安装包下载共用同一份。
     * **改为引用 `RemoteFetch.GITHUB_MIRROR_PREFIXES`**：发现页的精选内容也走镜像，
     * 三处必须同一条顺序，否则"更新能下、发现页打不开"这种不一致会反复出现。
     */
    val GITHUB_MIRROR_PREFIXES = RemoteFetch.GITHUB_MIRROR_PREFIXES

    /** 检查更新依次尝试的 JSON 源：镜像 + 原始地址兜底 */
    val UPDATE_JSON_URLS = RemoteFetch.githubCandidates(UPDATE_JSON_RAW)

    /**
     * 安装包下载候选地址（用户要求：不必挂 VPN 也能下到新包）。
     * GitHub 链接一律展开成「镜像优先 + 原始兜底」，非 GitHub 链接（将来换国内直连存储）原样返回。
     */
    fun downloadCandidates(url: String): List<String> = RemoteFetch.githubCandidates(url)

    /**
     * 可选：访问上面 JSON 用的只读 token（走私有仓库时才需要，留空 = 公开链接）。
     * ⚠️ token 会被打包进 APK，有被逆向提取的风险；只授予「单仓库只读」权限。
     */
    const val UPDATE_JSON_TOKEN = ""

    /** 新版本下载页。留空时用 JSON 里返回的 url。 */
    const val DOWNLOAD_URL = ""
}

/**
 * 内置更新日志：**只保留当前版本**，两个平台各一份常量，见 `Changelogs`。
 * 历史版本一律走 [AppLinks.CHANGELOG_DOC_URL] 的飞书文档，避免应用内越积越长。
 */
private val CHANGELOG: String get() = com.mysticat.roleplay.ui.Changelogs.current

/**
 * 运行时读取真实版本号（唯一可信来源 = 打包时的 Manifest，永不与 build.gradle.kts 失配）。
 */
internal fun currentVersionName(): String = Platform.ui.appVersionName()

internal fun currentVersionCode(): Int = Platform.ui.appVersionCode()

/**
 * 「关于鲸鱼」整页：把**合规与安全**（年龄与自愿声明 / 免责 / 来源 / 安全协议）与
 * **版本与更新**（当前版本 / 检查更新 / 更新日志 / 异常记录 / 意见反馈）收在**一整页**里。
 *
 * 为什么并成一页：这两块此前各有一个入口（「版本与安全」弹窗＋「合规与安全」页），
 * 而它们回答的是同一个问题——"这个应用是什么、它对我承诺了什么、现在是什么版本"。
 *
 * 形态口径（用户要求）：
 *  - 本页自身＝**整页**（手机走路由，桌面第三栏内嵌），不做弹窗；
 *  - 但**检查更新的结果、更新方式的选择、以及一切通知/提醒＝弹窗**，由 [UpdateFlow] 提供，
 *    宿主挂在两端顶层（见 `UpdateFlowHost`），所以人不在这一页也收得到提醒。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutWhaleScreen(onBack: () -> Unit, embedded: Boolean = false) {
    // 返回键（安卓）/ Esc（桌面）＝退回上一层；与手册、背景音乐同一口径
    WhaleBackHandler { onBack() }
    val versionName = remember { currentVersionName() }
    val versionCode = remember { currentVersionCode() }
    val isDesktop = Platform.ui.platformId() == "desktop"
    // 本页自己的轻提示（复制成功之类），与更新链路那份提示无关（那份在弹窗里）
    var hint by remember { mutableStateOf<String?>(null) }
    // 异常记录（CrashGuard 落的现场）：默认折叠，有记录时把条数写在标题上，让人一眼知道有没有。
    // 文件读取放 IO 线程，不在组合期读盘（本版口径）
    var showCrash by remember { mutableStateOf(false) }
    var showAppLog by remember { mutableStateOf(false) }
    // 更新日志与合规内容：行表式后都收进各自的行里，默认收起（口径 ④）
    var showChangelog by remember { mutableStateOf(false) }
    var showCompliance by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf<String?>(null) }
    // 应用流水日志（桌面专属）：与上面那份"崩溃现场"不同——它记的是全过程，
    // "卡死但没崩"只能靠它。Android 的 readAppLog() 返回 null，这一栏在手机上不出现。
    var appLog by remember { mutableStateOf<String?>(null) }
    var appLogPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashLog = Platform.ui.readCrashLog()
        appLog = Platform.ui.readAppLog()
        appLogPath = Platform.ui.appLogPath()
    }
    // 自动检查的结果（与徽标、提醒弹窗**同一份**状态）
    val autoFound = UpdateFlow.info

    /** 打开外链；打不开就把原因写进本页的轻提示 */
    fun openUrl(url: String, emptyHint: String) {
        if (url.isBlank()) {
            hint = emptyHint
            return
        }
        hint = null
        if (!Platform.ui.openUrl(url)) hint = "无法打开链接：$url"
    }

    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ══════════ 版本（原型 §八 第一组：不写组标题，整页第一张卡就是它） ══════════
            WhaleFieldGroup {
                WhaleFieldRow(
                    title = "当前版本",
                    desc = "鲸鱼 · " + if (isDesktop) "Windows" else "Android",
                    trailingBelow = true,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "$versionName（$versionCode）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                // 自测入口（**仅调试包**，release 用户看不到也点不到）：
                                // 长按版本号抛一次异常，用来验证「记录现场 → 自动回首页 → 异常记录里可复制」这条链路真的通。
                                // 崩溃守卫这种东西不能只靠读代码相信它——必须在真机上真崩一次。
                                modifier = Modifier.combinedClickable(
                                    onLongClick = {
                                        if (Platform.ui.isDebuggableBuild()) {
                                            Platform.ui.markCrashSelfTest()
                                            throw RuntimeException("CrashGuard self-test：这是调试包故意抛出的异常")
                                        }
                                    },
                                    onClick = {}
                                )
                            )
                            Spacer(Modifier.width(WhaleSpacing.sm))
                            TextButton(onClick = { UpdateFlow.checkAndShow() }) { Text("检查更新") }
                        }
                    }
                )
                if (autoFound != null) {
                    // 自动检查已经查到新版：这一行只说结论，真正的通知与"怎么更新"都在弹窗里
                    WhaleFieldRow(
                        title = "发现新版本",
                        desc = listOf(
                            autoFound.versionName,
                            autoFound.notes.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        trailingBelow = true,
                        trailing = {
                            TextButton(onClick = { UpdateFlow.showFound(autoFound) }) { Text("下载并安装") }
                        }
                    )
                }
                // 更新日志：默认收起（口径 ④），点行就地展开当前版本那一份
                WhaleFieldRow(
                    title = "更新日志",
                    desc = "每个版本改了什么",
                    divider = false,
                    onClick = { showChangelog = !showChangelog },
                    trailing = { WhaleRowCaret(showChangelog) }
                )
                if (showChangelog) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            CHANGELOG,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        // 应用内只放当前版本，历史版本跳飞书文档
                        Text(
                            "查看历史更新日志（飞书文档）›",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalWhalePalette.current.accentText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(WhaleShapes.r1)
                                .clickable {
                                    openUrl(AppLinks.CHANGELOG_DOC_URL, "历史更新日志链接尚未配置。")
                                }
                                .padding(vertical = 6.dp)
                        )
                    }
                }
                if (autoFound == null) {
                    Text(
                        "只在点「检查更新」时联网，结果与更新方式都在弹窗里给你。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 13.dp)
                    )
                }
            }

            // ══════════ 诊断（异常记录 ＋ 桌面专属的应用日志） ══════════
            val crashSummary = remember(crashLog) { crashSummaryOf(crashLog) }
            val logLines = remember(appLog) {
                appLog?.lineSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()
            }
            WhaleFieldGroup(label = "诊断") {
                val hasAppLogRow = appLogPath != null
                WhaleFieldRow(
                    title = "异常记录",
                    desc = crashSummary ?: "本机还没有异常记录；闪退会自动记下现场",
                    // 下面还有「应用日志」那一行时才是中间行，才画线（末行留悬空线是没做完的样子）
                    divider = hasAppLogRow && !(showCrash && crashSummary != null),
                    onClick = if (crashSummary != null) ({ showCrash = !showCrash }) else null,
                    trailing = if (crashSummary == null) null else {
                        {
                            val log = crashLog.orEmpty()
                            TextButton(onClick = {
                                Platform.ui.copyToClipboard("whale-crash", log)
                                hint = "异常记录已复制，可直接粘贴反馈。"
                            }) { Text("复制") }
                            TextButton(onClick = {
                                Platform.ui.clearCrashLog()
                                crashLog = null
                                showCrash = false
                                hint = "异常记录已清空。"
                            }) { Text("清空") }
                            WhaleRowCaret(showCrash)
                        }
                    }
                )
                if (showCrash && crashSummary != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            crashLog.orEmpty().take(4000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        NoticeLine(
                            if (isDesktop) "闪退时应用会直接退出（数据不受影响），重新打开即可继续。如果反复闪退，把这份记录发给我们即可定位。"
                            else "闪退后 App 会自动回到首页（数据不受影响）。如果反复闪退，把这份记录发给我们即可定位。"
                        )
                    }
                    if (hasAppLogRow) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                // 应用日志（桌面专属）：与上面那份"崩溃现场"分工不同——
                // 闪退有现场可查，但**卡死、点了没反应**不崩也不报错，只有这份全过程流水能定位。
                // Android 端 readAppLog() 返回 null，这一段整块不渲染。
                val logPath = appLogPath
                if (logPath != null) {
                    val hasLines = logLines.isNotEmpty()
                    WhaleFieldRow(
                        title = "应用日志",
                        desc = if (hasLines) "只存在本机 · 共 ${logLines.size} 行" else "只存在本机 · 本次运行还没有写日志",
                        divider = false,
                        onClick = if (hasLines) ({ showAppLog = !showAppLog }) else null,
                        trailing = if (!hasLines) null else {
                            {
                                TextButton(onClick = {
                                    Platform.ui.copyToClipboard("whale-applog", appLog.orEmpty())
                                    hint = "应用日志已复制（共 ${logLines.size} 行），可直接粘贴反馈。"
                                }) { Text("复制") }
                                TextButton(onClick = {
                                    Platform.ui.shareFile(File(logPath), "text/plain", "应用日志")
                                }) { Text("打开") }
                                WhaleRowCaret(showAppLog)
                            }
                        }
                    )
                    if (showAppLog && hasLines) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                "日志文件：$logPath（每条运行记录都追加在里面，超过 2MB 自动轮转、最多留 3 份；" +
                                    "「复制」拿到的是全文。程序卡住不动时，先复制这份日志再发给我们。）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            // 预览只给**最后几行**——排查卡死时关心的正是"出事前最后发生了什么"
                            Text(
                                logLines.takeLast(8).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ══════════ 反馈（含合规与安全：免责声明、年龄声明、来源、安全协议） ══════════
            WhaleFieldGroup(label = "反馈") {
                // 两条路（用户 2026-10-14 定）：想起跟进就走 GitHub，只是想说一句就走飞书。
                // 两边题目是同一套（GitHub 那张表单与飞书表单一一对应），选哪条都不缺信息。
                // 渠道名写在**标题**里、不留 desc：上一版那行 desc 说的和实际打开的入口不是一家
                // （写"去 GitHub 提一条"、开的却是飞书），用户那次的意见就是这行别写说明性文字。
                WhaleFieldRow(
                    title = "问题反馈（GitHub）",
                    onClick = {
                        openUrl(
                            AppLinks.FEEDBACK_ISSUE_URL,
                            "GitHub 打不开的话，用下面那条飞书表单，题目一样。"
                        )
                    },
                    trailing = { WhaleRowChevron() }
                )
                WhaleFieldRow(
                    title = "问题反馈（飞书 · 匿名）",
                    onClick = {
                        openUrl(
                            AppLinks.FEEDBACK_URL,
                            "反馈入口尚未配置，可先通过项目看板的「问题追踪」表提交。"
                        )
                    },
                    trailing = { WhaleRowChevron() }
                )
                // 合规内容原先平铺在页尾（四张卡）：行表式后收进这一行，点开就地看（默认收起，口径 ④）
                WhaleFieldRow(
                    title = "免责声明与安全协议",
                    desc = "年龄声明、导入内容来源、安全协议",
                    divider = false,
                    onClick = { showCompliance = !showCompliance },
                    trailing = { WhaleRowCaret(showCompliance) }
                )
                if (showCompliance) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 13.dp)
                    ) {
                        ComplianceContent()
                    }
                }
            }

            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalWhalePalette.current.accentText
                )
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 桌面撤可点回退箭头、统一 Esc（用户 2026-10-10 口径 234）；退出走 WhaleBackHandler。
                Text(
                    "关于鲸鱼",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(Modifier.weight(1f)) { body() }
        }
    } else {
        Scaffold(
            topBar = {
                WhalePageTopBar(
                    title = "关于鲸鱼",
                    navigationIcon = { BackArrowButton(onBack) }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) { body() }
        }
    }
}

/**
 * 异常记录的一句话摘要（原型 §八：值写"最近一次 … · 3 条"就够，默认不展开）。
 *
 * 条目按 [CrashGuard] 落的 `==== yyyy-MM-dd HH:mm:ss ====` 头行数；没有可读头行时返回 null，
 * 由调用方回落成"本机还没有异常记录"。
 */
private fun crashSummaryOf(log: String?): String? {
    if (log.isNullOrBlank()) return null
    val stamps = log.lineSequence().filter { it.trimStart().startsWith("====") }.toList()
    if (stamps.isEmpty()) return null
    val raw = stamps.last().trim().removeSurrounding("====").trim().take(16)
    // 只留"MM-dd HH:mm"：年份对"最近一次"没有信息量，挤掉它值列才放得下
    val whenText = if (raw.length >= 16) raw.substring(5) else raw
    return "最近一次 $whenText · 共 ${stamps.size} 条"
}
