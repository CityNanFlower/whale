package com.mysticat.roleplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.UpdateChecker
import com.mysticat.roleplay.data.UpdateInfo
import com.mysticat.roleplay.ui.screens.AppLinks
import com.mysticat.roleplay.ui.screens.currentVersionCode
import com.mysticat.roleplay.ui.screens.currentVersionName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新链路的**唯一状态源**，也是"检查更新的结果与提醒一律走弹窗"这条口径的落点。
 *
 * 为什么要有这个对象（而不是像早先那样把状态留在「版本与安全」那一个页面里）：
 *  ① 自动检查查到的新版本要**同时**喂三处——手机「关于鲸鱼」入口行的徽标、桌面第一栏的更新图标、
 *     自动提醒弹窗。三处各查各的，就会出现"手机说有新版、桌面说没有"这种自相矛盾；
 *  ② 提醒必须能盖在**任何**页面上（用户没进「关于鲸鱼」时也该被告知），而状态留在页面内部就做不到。
 * 徽标只承担"还有件事没处理"的静默残迹，出声一律由 [UpdateFlowHost] 的弹窗负责。
 *
 * 单次运行只静默检查一次（[autoCheck]），失败**不**出声——启动时告诉你"检查更新失败"是打扰，
 * 用户没主动要这个结果；用户主动点「检查更新」才把失败摆出来。
 */
object UpdateFlow {

    /** 自动检查查到的新版本；null＝还没查 / 已是最新 / 查失败。徽标与提醒都读它 */
    var info by mutableStateOf<UpdateInfo?>(null)
        private set

    /** 本次运行是否已经查过（自动检查只做一次，别每进一次页面就发一轮网络请求） */
    var autoChecked by mutableStateOf(false)
        private set

    /** 本次运行是否已经提醒过（用户点了「稍后」就不再弹；不持久化——"忽略一次"不该变成永久静音） */
    var autoPrompted by mutableStateOf(false)
        private set

    /** 递增信号：请宿主把更新弹窗摊开。0＝从未请求 */
    var openSignal by mutableStateOf(0)
        private set

    /** 摊开时直接摆进来的版本（自动检查已经查到的那一个，免得用户再点一次「检查更新」白等一个往返） */
    var preset by mutableStateOf<UpdateInfo?>(null)
        private set

    /** 摊开时是否真发一次网络请求（用户主动点「检查更新」＝是；摊开已知结果＝否） */
    var askCheck by mutableStateOf(false)
        private set

    /** 用户主动点「检查更新」：先真查一次，结果由弹窗呈现 */
    fun checkAndShow() {
        preset = null
        askCheck = true
        openSignal++
    }

    /** 摊开一个**已知**的新版本（自动提醒点「查看更新」、桌面第一栏图标、托盘那条链路都走它） */
    fun showFound(found: UpdateInfo) {
        autoPrompted = true
        preset = found
        askCheck = false
        openSignal++
    }

    /** 自动检查的落点：null（已是最新/解析失败/断网）＝静默，什么都不弹 */
    fun acceptAuto(found: UpdateInfo?) {
        autoChecked = true
        info = found
    }

    /** 用户已经知道这件事了（点了「稍后」）：本次运行不再自动弹提醒，徽标保留 */
    fun dismissAutoPrompt() {
        autoPrompted = true
    }

    /** 宿主消费掉一次摊开请求（弹窗已由自己接管） */
    fun consumeOpen() {
        askCheck = false
        preset = null
        openSignal = 0
    }

    fun clear() {
        info = null
        autoChecked = false
        autoPrompted = false
        consumeOpen()
    }

    /**
     * 启动静默检查：**一次运行只发一次请求**，结果只落 [acceptAuto]。
     * 失败静默（连日志都只是"没消息"），这就是它和"用户点了检查更新"的全部区别。
     */
    fun autoCheck() {
        if (autoChecked) return
        autoChecked = true
        if (AppLinks.UPDATE_JSON_RAW.isBlank()) return
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val info = runCatching { fetchUpdateInfo() }.getOrNull()
            val found = info?.takeIf { it.versionCode > currentVersionCode() }
            withContext(Dispatchers.Main) { acceptAuto(found) }
        }
    }
}

/** 更新弹窗的四种形态（检查中 / 发现新版本 / 已是最新 / 失败） */
private enum class UpdateStage { IDLE, CHECKING, FOUND, LATEST, FAILED }

/**
 * 更新链路的**唯一宿主**：两端都在顶层各挂一份（手机＝AppRoot，桌面＝窗口壳），
 * 于是提醒能盖住任何页面，而不必要求用户正好站在「关于鲸鱼」里。
 *
 * 它自己不发任何自动请求（那是 [UpdateFlow.autoCheck] 的事），只负责：
 *  ① 把 [UpdateFlow.openSignal] 请求的弹窗摊开（要么真查一次，要么摆开已有结果）；
 *  ② 自动检查查到新版时的**提醒弹窗**（用户点「稍后」即止，徽标留着）；
 *  ③ 下载完成后的「更新已就绪」确认弹窗（桌面便携包链路）。
 */
@Composable
fun UpdateFlowHost() {
    val scope = rememberCoroutineScope()
    val isDesktop = Platform.ui.platformId() == "desktop"
    var dialogOpen by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(UpdateStage.IDLE) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var readyApk by remember { mutableStateOf<File?>(null) }
    var updateHint by remember { mutableStateOf<String?>(null) }
    // 桌面安装程序形态：程序目录不可写时本次更新改走"下载新安装器"（checkUpdate 之后才定得下来）
    var updateIsInstaller by remember { mutableStateOf(false) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var expectedVersionCode by remember { mutableStateOf(0) }
    var expectedVersionName by remember { mutableStateOf("") }
    var expectedSha256 by remember { mutableStateOf("") }

    val updateVerb = when {
        !isDesktop -> "下载并安装"
        updateIsInstaller -> "下载安装程序"
        else -> "下载更新包"
    }
    val updateScriptName = Platform.ui.updateScriptName()

    /** 唤起安装器；未授予「安装未知应用」时引导去系统设置 */
    fun installApk(apk: File) {
        if (Platform.ui.installUpdate(apk)) {
            updateHint = null
            return
        }
        if (Platform.ui.needsInstallPermission()) {
            updateHint = "已下载完成。请先允许「安装未知应用」，回来后再点「安装已下载的包」。"
            if (!Platform.ui.requestInstallPermission()) {
                updateHint = "请在系统设置中允许本应用安装未知应用后重试。"
            }
        } else {
            updateHint = "安装包无法打开，请重新下载。"
        }
    }

    /**
     * 进入「发现新版本」这一态：记下目标版本与校验值、定下这次该下载哪个包。
     * 更新分流（安装程序形态 vs 便携包）走 [UpdateChecker.target] 这一份口径，托盘链路吃的是同一份。
     */
    fun applyFound(info: UpdateInfo) {
        found = info
        stage = UpdateStage.FOUND
        errorText = null
        expectedVersionCode = info.versionCode
        expectedVersionName = info.versionName
        val target = UpdateChecker.target(
            info, Platform.ui.updatesViaInstaller(), AppLinks.DOWNLOAD_URL
        )
        updateIsInstaller = target?.isInstaller == true
        expectedSha256 = target?.sha256.orEmpty()
        downloadUrl = target?.url
    }

    fun checkUpdate() {
        if (AppLinks.UPDATE_JSON_RAW.isBlank()) {
            stage = UpdateStage.FAILED
            errorText = "更新源尚未配置，无法检查更新。"
            downloadUrl = null
            return
        }
        stage = UpdateStage.CHECKING
        errorText = null
        downloadUrl = null
        found = null
        updateIsInstaller = false
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fetchUpdateInfo() } }
            r.onSuccess { info ->
                when {
                    info == null -> {
                        stage = UpdateStage.FAILED
                        errorText = "更新信息解析失败，请稍后再试。"
                    }
                    info.versionCode > currentVersionCode() -> {
                        applyFound(info)
                        // 用户主动查到的结果与自动检查是同一个事实，写回同一份状态：徽标随之亮起/熄灭
                        UpdateFlow.acceptAuto(info)
                        UpdateFlow.dismissAutoPrompt()
                    }
                    else -> {
                        stage = UpdateStage.LATEST
                        UpdateFlow.acceptAuto(null)
                    }
                }
            }.onFailure {
                stage = UpdateStage.FAILED
                errorText = "检查更新失败：${it.message ?: "网络不可达"}" +
                    "（已依次尝试 ${AppLinks.UPDATE_JSON_URLS.size} 个更新源）\n" +
                    "可稍后重试，或到「更新日志」里手动下载。"
            }
        }
    }

    /** 应用内下载（镜像优先，失败自动换源），下完直接唤起安装器 */
    fun downloadAndInstall(url: String) {
        if (downloading) return
        val candidates = AppLinks.downloadCandidates(url)
        if (candidates.isEmpty()) {
            updateHint = "下载地址尚未配置。"
            return
        }
        downloading = true
        progressText = "开始下载…"
        updateHint = null
        scope.launch {
            val r = Platform.ui.downloadUpdate(candidates, expectedSha256) { done, total ->
                progressText = if (total > 0) {
                    "下载中 ${done * 100 / total}%（${done / 1024 / 1024}MB / ${total / 1024 / 1024}MB）"
                } else {
                    "下载中 ${done / 1024 / 1024}MB"
                }
            }
            downloading = false
            r.onSuccess { apk ->
                readyApk = apk
                // 安装器形态：exe 里读不出 build-info，完整性已由 SHA-256 兜底，跳过包内版本核对
                if (updateIsInstaller) {
                    progressText = "下载完成（${apk.length() / 1024 / 1024}MB）"
                    installApk(apk)
                    return@onSuccess
                }
                // 下载完先核对包内版本：JSON 改了但安装包没传/传错版本时当场提示，别等装完才发现
                val actual = Platform.ui.readUpdatePackageVersion(apk)
                val mismatch = when {
                    actual == null -> true
                    expectedVersionCode > 0 && actual.second != expectedVersionCode -> true
                    actual.first != Platform.ui.appPackageName() -> true
                    else -> false
                }
                if (mismatch) {
                    // 版本对不上时不自动唤起安装器：先让用户看清提示，真要装可用「安装已下载的包」
                    val got = actual?.let { "${it.third}（versionCode ${it.second}）" } ?: "无法读取版本信息"
                    progressText = "下载完成，但包版本不符：实际 $got"
                    updateHint = "提示的版本是 $expectedVersionName，下载到的却是 $got。" +
                        "多半是发布页的安装包还没更新或传错了，建议改用「浏览器打开」确认，或联系反馈。"
                } else {
                    progressText = "下载完成（${apk.length() / 1024 / 1024}MB · ${actual?.third ?: ""}）"
                    installApk(apk)
                }
            }.onFailure {
                progressText = null
                updateHint = "下载失败：${it.message ?: "网络不可达"}。" +
                    "已尝试 ${candidates.size} 个下载源，可稍后重试或改用「浏览器打开」。"
            }
        }
    }

    // 本机安装包：用户自己从网盘 / QQ 等渠道拿到包时，直接选文件安装（桌面＝选便携包 zip）
    val pickApk = Platform.ui.rememberApkPicker { ok ->
        if (!ok) {
            updateHint = if (isDesktop) "无法使用所选便携包，请换一个 zip 试试。"
            else "无法读取所选安装包，请换一个文件试试。"
        }
    }

    fun openUrl(url: String, emptyHint: String) {
        if (url.isBlank()) {
            updateHint = emptyHint
            return
        }
        updateHint = null
        if (!Platform.ui.openUrl(url)) updateHint = "无法打开链接：$url"
    }

    // 摊开请求：`openSignal` 变化即触发（0＝从未请求过，不动）
    LaunchedEffect(UpdateFlow.openSignal) {
        if (UpdateFlow.openSignal == 0) return@LaunchedEffect
        val presetInfo = UpdateFlow.preset
        val wantCheck = UpdateFlow.askCheck
        UpdateFlow.consumeOpen()
        dialogOpen = true
        progressText = null
        updateHint = null
        readyApk = null
        if (presetInfo != null && !wantCheck) applyFound(presetInfo) else checkUpdate()
    }

    if (dialogOpen) {
        UpdateDialog(
            stage = stage,
            found = found,
            errorText = errorText,
            downloadUrl = downloadUrl,
            updateVerb = updateVerb,
            updateHint = updateHint,
            progressText = progressText,
            downloading = downloading,
            readyApk = readyApk,
            isDesktop = isDesktop,
            updateIsInstaller = updateIsInstaller,
            updateScriptName = updateScriptName,
            onCheckAgain = { checkUpdate() },
            onDownload = { downloadUrl?.let { downloadAndInstall(it) } },
            onInstallReady = { readyApk?.let { installApk(it) } },
            onOpenBrowser = { downloadUrl?.let { openUrl(it, "下载链接尚未配置。") } },
            onPickLocal = { pickApk() },
            onDismiss = { dialogOpen = false }
        )
    }

    // ── 自动检查查到新版时的**提醒弹窗** ──
    // 只出声一次（「稍后」＝本次运行不再弹），徽标继续留在入口上，不会因为点了稍后就"这件事没了"。
    val autoInfo = UpdateFlow.info
    if (!dialogOpen && autoInfo != null && !UpdateFlow.autoPrompted) {
        UpdateReminderDialog(
            info = autoInfo,
            latestVersion = currentVersionName(),
            onOpen = { UpdateFlow.showFound(autoInfo) },
            onLater = { UpdateFlow.dismissAutoPrompt() }
        )
    }

    // ── 便携包更新"就绪待确认"层 ──
    // staging 完成（DesktopPlatformUi.installUpdate / 本机便携包选择）后 pendingScript 非空。
    // 已勾过「以后自动安装」的不再询问，直接拉脚本退出；脚本自己会等本进程退出后替换并重启。
    DesktopUpdatePrompt.pendingScript?.let { script ->
        if (DesktopFeatures.autoInstallUpdate) {
            LaunchedEffect(script) {
                val launched = Platform.ui.launchUpdateScript(script)
                DesktopUpdatePrompt.clear()
                if (launched) DesktopExit.requestExit?.invoke()
                else updateHint = "无法自动开始安装，请关闭鲸鱼后手动双击：$script"
            }
        } else {
            UpdateReadyDialog(
                versionName = expectedVersionName.ifBlank { DesktopUpdatePrompt.pendingVersion ?: "新版本" },
                onConfirm = { rememberChoice ->
                    if (rememberChoice) DesktopFeatures.setAutoUpdateInstall(true)
                    val launched = Platform.ui.launchUpdateScript(script)
                    DesktopUpdatePrompt.clear()
                    if (launched) {
                        updateHint = "正在退出并安装 $expectedVersionName，替换完成后鲸鱼会自动重新打开。"
                        DesktopExit.requestExit?.invoke()
                    } else {
                        updateHint = "无法自动开始安装，请关闭鲸鱼后手动双击：$script"
                    }
                },
                onLater = {
                    Platform.ui.revealFile(script)
                    DesktopUpdatePrompt.clear()
                    updateHint = "更新已就绪：关闭鲸鱼后双击「${script.substringAfterLast('\\')}」即可完成替换。"
                }
            )
        }
    }
}

/**
 * 自动检查查到新版本时的提醒层（**弹窗**，不是顶部提示条）。
 *
 * 桌面早先用的是一条"不拦指针"的提示条——那是当时只有一种出声方式的折中；现在手机与桌面
 * 共用这一份提醒，两端形态一致，也让"点了稍后 ≠ 这件事消失了"（徽标照旧挂在入口上）看得更清楚。
 */
@Composable
private fun UpdateReminderDialog(
    info: UpdateInfo,
    latestVersion: String,
    onOpen: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Column {
                Text("你现在用的是 $latestVersion。")
                val lead = info.notes.lineSequence().firstOrNull { it.isNotBlank() }
                if (lead != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        lead,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "点「查看更新」选择更新方式（应用内下载 / 浏览器打开 / 本机安装包）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("查看更新") } },
        dismissButton = { TextButton(onClick = onLater) { Text("稍后") } }
    )
}

/**
 * 「检查更新」的结果弹窗：检查中 / 发现新版本（含**更新方式**选择）/ 已是最新 / 失败。
 *
 * 为什么这些一律留在弹窗里、不摊进「关于鲸鱼」页：它们都是**一件事的通知**——查完就该有结论、
 * 选完就该开始下；摊进页面会变成"页面上多了一段字"，用户点完不知道下一步该干什么，
 * 换页回来也分不清那段字是新结果还是上一轮的残留。
 */
@Composable
private fun UpdateDialog(
    stage: UpdateStage,
    found: UpdateInfo?,
    errorText: String?,
    downloadUrl: String?,
    updateVerb: String,
    updateHint: String?,
    progressText: String?,
    downloading: Boolean,
    readyApk: File?,
    isDesktop: Boolean,
    updateIsInstaller: Boolean,
    updateScriptName: String,
    onCheckAgain: () -> Unit,
    onDownload: () -> Unit,
    onInstallReady: () -> Unit,
    onOpenBrowser: () -> Unit,
    onPickLocal: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (stage) {
        UpdateStage.CHECKING -> "检查更新"
        UpdateStage.FOUND -> "发现新版本 ${found?.versionName.orEmpty()}"
        UpdateStage.LATEST -> "已是最新版本"
        UpdateStage.FAILED -> "检查更新失败"
        UpdateStage.IDLE -> "检查更新"
    }
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                when (stage) {
                    UpdateStage.CHECKING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在获取最新版本信息…")
                    }
                    UpdateStage.LATEST -> Text("您已是最新版本 ${currentVersionName()}")
                    UpdateStage.FAILED -> Text(errorText ?: "检查更新失败，请稍后再试。")
                    UpdateStage.FOUND -> {
                        found?.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    UpdateStage.IDLE -> Unit
                }

                if (stage == UpdateStage.FOUND && downloadUrl != null) {
                    // ── 选择更新方式 ──（用户要求：这一段始终是弹窗里的选择，不摊进页面）
                    Text(
                        "选择更新方式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onDownload,
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (downloading) "下载中…" else updateVerb)
                    }
                    // 已下载但当时没授予安装权限（桌面＝还没生成替换脚本）：给一个再次触发的入口
                    readyApk?.let { apk ->
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onInstallReady, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (isDesktop) {
                                    if (updateIsInstaller) "运行安装器" else "替换程序目录"
                                } else "安装已下载的包"
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenBrowser, modifier = Modifier.weight(1f)) {
                            Text("浏览器打开")
                        }
                        OutlinedButton(onClick = onPickLocal, modifier = Modifier.weight(1f)) {
                            Text(if (isDesktop) "本机便携包" else "本机安装包")
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isDesktop && updateIsInstaller) {
                            "「$updateVerb」会通过国内镜像下载新的安装程序，无需自行访问 GitHub；下载完成后" +
                                "会自动启动安装向导（弹出管理员确认时选「是」）。安装前请先退出鲸鱼，" +
                                "会话与设置不受影响。"
                        } else if (isDesktop) {
                            "「$updateVerb」会通过国内镜像下载便携包，无需自行访问 GitHub；下载完成后" +
                                "鲸鱼会询问是否立即重启安装：确认后自动退出、完成替换并重新打开" +
                                "（会话与设置不受影响）。也可以选稍后，去程序目录双击「$updateScriptName」手动替换。"
                        } else {
                            "「$updateVerb」会通过国内镜像下载，无需自行访问 GitHub。" +
                                "若已有别人发来的安装包，用「本机安装包」直接选文件安装。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                progressText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                updateHint?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            when (stage) {
                UpdateStage.FAILED -> TextButton(onClick = onCheckAgain) { Text("重试") }
                else -> TextButton(onClick = onDismiss, enabled = !downloading) { Text("关闭") }
            }
        },
        dismissButton = {
            if (stage == UpdateStage.FAILED) TextButton(onClick = onDismiss) { Text("关闭") }
            else if (stage == UpdateStage.FOUND) {
                TextButton(onClick = onDismiss, enabled = !downloading) { Text("稍后") }
            }
        }
    )
}

/** 「更新已就绪」确认层：立即安装（可勾"以后自动"）或稍后手动替换 */
@Composable
private fun UpdateReadyDialog(
    versionName: String,
    onConfirm: (rememberChoice: Boolean) -> Unit,
    onLater: () -> Unit
) {
    var rememberChoice by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("更新已就绪") },
        text = {
            Column {
                Text("已下载并校验完成：$versionName。")
                Spacer(Modifier.height(6.dp))
                Text(
                    "点「立即安装」后鲸鱼会自动退出、完成替换并重新打开，会话与设置不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text("以后自动安装，不再询问", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rememberChoice) }) { Text("立即安装") } },
        dismissButton = { TextButton(onClick = onLater) { Text("稍后手动安装") } }
    )
}

// ---- 拉取远端版本 JSON ----

// 更新清单的读取与比对已抽到共享层 `UpdateChecker`：托盘菜单也要能查更新，
// 而托盘住在 desktopApp、拿不到页面里的私有实现。这条链路与托盘共用同一份口径
// ——包括"追加时间戳穿透 CDN 缓存"和"桌面读 windows 节点"这两条容易漏的细节。
internal fun fetchUpdateInfo(): UpdateInfo? = UpdateChecker.fetch(
    AppLinks.UPDATE_JSON_URLS,
    AppLinks.UPDATE_JSON_TOKEN,
    Platform.ui.updateJsonNode()
)
