package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.RemoteFetch
import com.mysticat.roleplay.data.UpdateChecker
import com.mysticat.roleplay.data.UpdateInfo
import com.mysticat.roleplay.ui.Changelogs
import com.mysticat.roleplay.ui.DesktopExit
import com.mysticat.roleplay.ui.DesktopFeatures
import com.mysticat.roleplay.ui.DesktopUpdatePrompt
import com.mysticat.roleplay.ui.NoticeLine
import com.mysticat.roleplay.ui.Platform
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「版本与安全」模块的对外信息（问题 #7）。
 * 换链接 / 发新版只改这一处。
 *
 * ⚠️ 版本号不再在这里写死（alpha.4 曾因漏改这里的常量导致新包显示旧版本号、误报更新），
 * 一律运行时从 PackageManager 读真实值，见下方 currentVersionName/currentVersionCode。
 */
object AppLinks {

    /** 意见反馈入口（WPS 表单 / 问卷）。留空时弹窗会提示「尚未配置」。 */
    const val FEEDBACK_URL = "https://f.kdocs.cn/ksform/h/write/a86qrIBN/"

    /**
     * 历史更新日志（金山文档）。应用内只展示当前版本，更早的版本去这里看。
     * 两端链接不同（用户 2026-09-17 口径：更新日志的链接不用同一份；链接由用户 2026-09-19 提供）：
     * Android 保持 cu6dJDvevjbM，桌面用 ckUx5yQpoCcP（鲸鱼更新日志（Windows））。
     */
    val CHANGELOG_DOC_URL: String
        get() = if (com.mysticat.roleplay.ui.Platform.ui.platformId() == "desktop")
            "https://www.kdocs.cn/l/ckUx5yQpoCcP"
        else "https://www.kdocs.cn/l/cu6dJDvevjbM"

    /**
     * 检查更新用的版本信息 JSON 地址。内容形如：
     * {"versionCode":6,"versionName":"0.1.0-alpha.4","url":"https://…/鲸鱼-0.1.0-alpha.4.apk","notes":"…"}
     *
     * ⚠️ 注意：这个地址必须能被**匿名 GET** 到。GitHub/Gitee 的**私有仓库** raw 与 Release
     * 附件都要求 token 认证，匿名请求会 404 —— 真要私有就得填下面的 TOKEN（见风险说明）。
     * 留空 = 更新源未配置，点「检查更新」会提示。
     *
     * 2026-09-24：更新通道收编进公开源码仓 `whale`——这里取的就是仓里的 `dist/发布用-version.json`
     * （发布流程同步过去的原件，与安装包 Release 同仓）。路径含中文，所以写成百分号编码形态：
     * 三家加速镜像都实测能原样代理（直连 raw 在国内不通属已知情况，它只作兜底）。
     * 万一哪家镜像把它编坏了，再在公开树里补一份 ASCII 别名的副本，改这一行即可。
     */
    const val UPDATE_JSON_RAW =
        "https://raw.githubusercontent.com/CityNanFlower/whale/main/dist/%E5%8F%91%E5%B8%83%E7%94%A8-version.json"

    /**
     * GitHub 加速镜像前缀（问题 #24：国内直连 GitHub 会超时）。检查更新与安装包下载共用同一份。
     * **第 54 轮起改为引用 `RemoteFetch.GITHUB_MIRROR_PREFIXES`**：发现页的精选内容也走镜像，
     * 三处必须同一条顺序，否则"更新能下、发现页打不开"这种不一致会反复出现。清单与实测口径见那边。
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
 * 内置更新日志：**只保留当前版本**（问题 #3），两个平台各一份常量，见 [Changelogs]。
 * 历史版本一律走 [AppLinks.CHANGELOG_DOC_URL] 的金山文档，避免应用内越积越长。
 */
private val CHANGELOG: String get() = Changelogs.current

/**
 * 运行时读取真实版本号（唯一可信来源 = 打包时的 Manifest，永不与 build.gradle.kts 失配）。
 */
internal fun currentVersionName(): String = Platform.ui.appVersionName()

internal fun currentVersionCode(): Int = Platform.ui.appVersionCode()

/**
 * 问题 #7：版本与安全。
 * 当前版本 / 更新日志 / 安全须知 / 异常记录 / 意见反馈 收在一个弹窗里。
 * 「检查更新」已接入自动比对（多源 version.json + 应用内下载安装），见下方 checkUpdate()。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun VersionSecurityDialog(onDismiss: () -> Unit, initialInfo: UpdateInfo? = null) {
    val scope = rememberCoroutineScope()
    val versionName = remember { currentVersionName() }
    val versionCode = remember { currentVersionCode() }
    // 更新链路的平台口径（M5）：Android＝下载 APK 交系统安装器；桌面＝下载便携包后生成替换脚本
    val isDesktop = Platform.ui.platformId() == "desktop"
    // 桌面安装程序形态（M13）：程序目录不可写时，本次更新改走"下载新安装器"（checkUpdate 后才定，可变）
    var updateIsInstaller by remember { mutableStateOf(false) }
    val updateVerb = when {
        !isDesktop -> "下载并安装"
        updateIsInstaller -> "下载安装程序"
        else -> "下载更新包"
    }
    val updateScriptName = Platform.ui.updateScriptName()
    var hint by remember { mutableStateOf<String?>(null) }
    var showChangelog by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    // 异常记录（CrashGuard 落的现场）：默认折叠，有记录时把条数写在标题上，让人一眼知道有没有。
    // 文件读取放 IO 线程，不在组合期读盘（本版 P1-4 的口径）
    var showCrash by remember { mutableStateOf(false) }
    var showAppLog by remember { mutableStateOf(false) }
    var crashLog by remember { mutableStateOf<String?>(null) }
    // 应用流水日志（桌面专属，M9）：与上面那份"崩溃现场"不同——它记的是全过程，
    // "卡死但没崩"只能靠它。Android 的 readAppLog() 返回 null，这一栏在手机上不出现。
    var appLog by remember { mutableStateOf<String?>(null) }
    var appLogPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        crashLog = Platform.ui.readCrashLog()
        appLog = Platform.ui.readAppLog()
        appLogPath = Platform.ui.appLogPath()
    }

    // 检查更新状态
    var checking by remember { mutableStateOf(false) }
    var checkMsg by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }

    // 应用内下载安装包状态
    var downloading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var readyApk by remember { mutableStateOf<File?>(null) }
    // 远端 JSON 声明的目标版本，用于下载后核对包对不对
    var expectedVersionCode by remember { mutableStateOf(0) }
    var expectedVersionName by remember { mutableStateOf("") }
    /** 远端声明的安装包 SHA-256（非空时下载后校验） */
    var expectedSha256 by remember { mutableStateOf("") }

    /** 唤起安装器；未授予「安装未知应用」时引导去系统设置 */
    fun installApk(apk: File) {
        if (Platform.ui.installUpdate(apk)) {
            hint = null
            return
        }
        if (Platform.ui.needsInstallPermission()) {
            hint = "已下载完成。请先允许「安装未知应用」，回来后再点「安装已下载的包」。"
            if (!Platform.ui.requestInstallPermission()) {
                hint = "请在系统设置中允许本应用安装未知应用后重试。"
            }
        } else {
            hint = "安装包无法打开，请重新下载。"
        }
    }

    /** 应用内下载（镜像优先，失败自动换源），下完直接唤起安装器 */
    fun downloadAndInstall(url: String) {
        if (downloading) return
        val candidates = AppLinks.downloadCandidates(url)
        if (candidates.isEmpty()) {
            hint = "下载地址尚未配置。"
            return
        }
        downloading = true
        progressText = "开始下载…"
        hint = null
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
                // 安装器形态（M13）：exe 里读不出 build-info，完整性已由 SHA-256 兜底，跳过包内版本核对
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
                    hint = "提示的版本是 $expectedVersionName，下载到的却是 $got。" +
                        "多半是发布页的安装包还没更新或传错了，建议改用「浏览器打开」确认，或联系反馈。"
                } else {
                    progressText = "下载完成（${apk.length() / 1024 / 1024}MB · ${actual?.third ?: ""}）"
                    installApk(apk)
                }
            }.onFailure {
                progressText = null
                hint = "下载失败：${it.message ?: "网络不可达"}。" +
                    "已尝试 ${candidates.size} 个下载源，可稍后重试或改用「浏览器打开」。"
            }
        }
    }

    // 本机安装包：用户自己从网盘 / QQ 等渠道拿到包时，直接选文件安装（桌面＝选便携包 zip）
    val pickApk = Platform.ui.rememberApkPicker { ok ->
        if (!ok) {
            hint = if (isDesktop) "无法使用所选便携包，请换一个 zip 试试。"
            else "无法读取所选安装包，请换一个文件试试。"
        }
    }

    fun openUrl(url: String, emptyHint: String) {
        if (url.isBlank()) {
            hint = emptyHint
            return
        }
        hint = null
        if (!Platform.ui.openUrl(url)) hint = "无法打开链接：${url}"
    }

    /**
     * 进入「发现新版本」这一态：写提示文案、记下目标版本与校验值、定下这次该下载哪个包。
     * 抽出来是因为有两个入口——用户点「检查更新」，以及桌面启动静默检查**已经查到了**新版
     * （台账 9(a)：启动检查的提示条点「去更新」时直接把这一态摊开，不让用户再点一次"检查更新"）。
     */
    fun applyFound(info: UpdateInfo) {
        checkMsg = buildString {
            append("发现新版本 ").append(info.versionName)
            if (info.notes.isNotBlank()) append("\n").append(info.notes)
        }
        expectedVersionCode = info.versionCode
        expectedVersionName = info.versionName
        // M13 分流：安装程序形态（程序目录不可写）且远端给了安装器地址 → 下载 exe；
        // 字段缺失时退回 zip 链路（老版 version.json / 便携包兼容，两头都不空转）
        // 判别口径已收到 UpdateChecker.target，托盘菜单那条链路吃的是同一份。
        val target = UpdateChecker.target(
            info, Platform.ui.updatesViaInstaller(), AppLinks.DOWNLOAD_URL
        )
        updateIsInstaller = target?.isInstaller == true
        expectedSha256 = target?.sha256.orEmpty()
        downloadUrl = target?.url
    }

    fun checkUpdate() {
        // P2-B11：原来判的是 UPDATE_JSON_URLS.isEmpty()，而它是"常量清单拼接"出来的、永远非空（不可达分支）。
        // 真正会"没配置"的是原始地址本身，改判它才有意义。
        if (AppLinks.UPDATE_JSON_RAW.isBlank()) {
            checkMsg = "更新源尚未配置（AppLinks.UPDATE_JSON_URLS 为空）。"
            downloadUrl = null
            return
        }
            checking = true
            checkMsg = null
            downloadUrl = null
            updateIsInstaller = false
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { fetchUpdateInfo() } }
            checking = false
            r.onSuccess { info ->
                when {
                    info == null -> checkMsg = "更新信息解析失败，请稍后再试。"
                    info.versionCode > versionCode -> applyFound(info)
                    else -> checkMsg = "您已是最新版本 $versionName"
                }
            }.onFailure {
                checkMsg = "检查更新失败：${it.message ?: "网络不可达"}" +
                    "（已依次尝试 ${AppLinks.UPDATE_JSON_URLS.size} 个更新源）\n" +
                    "可稍后重试，或到「更新日志」里手动下载。"
            }
        }
    }

    // 台账 9(a)：桌面启动时已经静默查过一次，有结果就直接摊开"发现新版本"——
    // 否则用户从提示条点进来还得再点一次「检查更新」，白等一个网络往返。
    LaunchedEffect(Unit) { initialInfo?.let { applyFound(it) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("版本与安全") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "鲸鱼 $versionName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "versionCode $versionCode · 本地测试版",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 自测入口（**仅调试包**，release 用户看不到也点不到）：
                    // 长按版本号抛一次异常，用来验证「记录现场 → 自动回首页 → 异常记录里可复制」这条链路真的通。
                    // 崩溃守卫这种东西不能只靠读代码相信它——必须在真机上真崩一次。
                    modifier = Modifier.combinedClickable(
                        onLongClick = {
                            if (Platform.ui.isDebuggableBuild()) {
                                Platform.ui.markCrashSelfTest()
                                throw RuntimeException("CrashGuard self-test：这是调试包故意抛的异常")
                            }
                        },
                        onClick = {}
                    )
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { checkUpdate() },
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("检查中…")
                    } else {
                        Text("检查更新")
                    }
                }
                checkMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                downloadUrl?.let { url ->
                    Spacer(Modifier.height(8.dp))
                    // 应用内下载：走国内可达镜像，下完直接唤起安装器（桌面＝便携包替换），不用跳浏览器
                    Button(
                        onClick = { downloadAndInstall(url) },
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
                        OutlinedButton(
                            onClick = { installApk(apk) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isDesktop) (if (updateIsInstaller) "运行安装器" else "替换程序目录") else "安装已下载的包") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openUrl(url, "下载链接尚未配置。") },
                            modifier = Modifier.weight(1f)
                        ) { Text("浏览器打开") }
                        OutlinedButton(
                            onClick = {
                                pickApk()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (isDesktop) "本机便携包" else "本机安装包") }
                    }
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                progressText?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                SectionToggle("更新日志", showChangelog) { showChangelog = !showChangelog }
                if (showChangelog) {
                    Text(
                        CHANGELOG,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    // 问题 #3：应用内只放当前版本，历史版本跳金山文档
                    Text(
                        "查看历史更新日志（金山文档）›",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                openUrl(AppLinks.CHANGELOG_DOC_URL, "历史更新日志链接尚未配置。")
                            }
                            .padding(vertical = 8.dp)
                    )
                }

                HorizontalDivider()
                SectionToggle("安全须知", showSecurity) { showSecurity = !showSecurity }
                if (showSecurity) {
                    SecurityNotice()
                }

                HorizontalDivider()
                // 异常记录（2026-09-15）：崩溃现场由 CrashGuard 落盘，用户在这里复制发给我们。
                // 之所以要有这一栏——"偶发闪退"在别人的手机上抓不到栈，只能靠用户带出来。
                SectionToggle(
                    if (crashLog.isNullOrBlank()) "异常记录（无）" else "异常记录（有，可复制）",
                    showCrash
                ) { showCrash = !showCrash }
                if (showCrash) {
                    val log = crashLog
                    if (log.isNullOrBlank()) {
                        NoticeLine(
                            if (isDesktop) "本机还没有异常记录。若应用崩溃退出，这里会自动记下现场（版本、系统、崩溃栈与点击轨迹）。"
                            else "本机还没有异常记录。若 App 出现闪退，这里会自动记下现场（版本、机型、崩溃栈与点击轨迹）。"
                        )
                    } else {
                        Text(
                            log.take(4000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                Platform.ui.copyToClipboard("whale-crash", log)
                                hint = "异常记录已复制，可直接粘贴反馈。"
                            }) { Text("复制全部") }
                            OutlinedButton(onClick = {
                                Platform.ui.clearCrashLog()
                                crashLog = null
                                hint = "异常记录已清空。"
                            }) { Text("清空") }
                        }
                        Spacer(Modifier.height(4.dp))
                        NoticeLine(
                            if (isDesktop) "闪退时应用会直接退出（数据不受影响），重新打开即可继续。如果反复闪退，把这份记录发给我们即可定位。"
                            else "闪退后 App 会自动回到首页（数据不受影响）。如果反复闪退，把这份记录发给我们即可定位。"
                        )
                    }
                }

                HorizontalDivider()
                // 应用日志（桌面专属，M9 第 30 轮）：与上面那份"崩溃现场"分工不同——
                // 闪退有现场可查，但**卡死、点了没反应**（第 28 轮那次 EDT 死循环）不崩也不报错，
                // 只有这份全过程流水能定位。Android 端 readAppLog() 返回 null，这段整块不渲染。
                val logPath = appLogPath
                if (logPath != null) {
                    val log = appLog
                    SectionToggle(
                        if (log.isNullOrBlank()) "应用日志（桌面）" else "应用日志（桌面，可复制）",
                        showAppLog
                    ) { showAppLog = !showAppLog }
                    if (showAppLog) {
                        if (log.isNullOrBlank()) {
                            NoticeLine("本次运行还没有写日志。")
                        } else {
                            // 弹窗正文区是固定高度 + 滚动（`heightIn(max=460.dp)`），所以**按钮要排在
                            // 最前面**：排在长文本后面会被顶出可视区（实测就是这样，等于没有按钮）。
                            // 预览只给**最后几行**——排查卡死时关心的正是"出事前最后发生了什么"。
                            val lines = log.lineSequence().filter { it.isNotBlank() }.toList()
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    Platform.ui.copyToClipboard("whale-applog", log)
                                    hint = "应用日志已复制（共 ${lines.size} 行），可直接粘贴反馈。"
                                }) { Text("复制日志") }
                                OutlinedButton(onClick = {
                                    Platform.ui.shareFile(File(logPath), "text/plain", "应用日志")
                                }) { Text("打开所在文件夹") }
                            }
                            Spacer(Modifier.height(6.dp))
                            NoticeLine(
                                "日志文件：$logPath（每条运行记录都追加在里面，超过 2MB 自动轮转、最多留 3 份；" +
                                    "「复制日志」拿到的是全文。程序卡住不动时，先复制这份日志再发给我们。）"
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "日志末尾（共 ${lines.size} 行）：\n" + lines.takeLast(8).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()
                SectionToggle("意见与反馈", false) {
                    openUrl(
                        AppLinks.FEEDBACK_URL,
                        "反馈入口尚未配置，可先通过项目看板的「问题追踪」表提交。"
                    )
                }

                hint?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    // ── 台账 2：便携包更新"就绪待确认"层 ──
    // staging 完成（DesktopPlatformUi.installUpdate / 本机便携包选择）后 pendingScript 非空。
    // 已勾过「以后自动安装」的不再询问，直接拉脚本退出；脚本自己会等本进程退出后替换并重启。
    DesktopUpdatePrompt.pendingScript?.let { script ->
        if (DesktopFeatures.autoInstallUpdate) {
            LaunchedEffect(script) {
                val launched = Platform.ui.launchUpdateScript(script)
                DesktopUpdatePrompt.clear()
                if (launched) DesktopExit.requestExit?.invoke()
                else hint = "无法自动开始安装，请关闭鲸鱼后手动双击：$script"
            }
        } else {
            UpdateReadyDialog(
                versionName = expectedVersionName.ifBlank { DesktopUpdatePrompt.pendingVersion ?: "新版本" },
                onConfirm = { rememberChoice ->
                    if (rememberChoice) DesktopFeatures.setAutoUpdateInstall(true)
                    val launched = Platform.ui.launchUpdateScript(script)
                    DesktopUpdatePrompt.clear()
                    if (launched) {
                        hint = "正在退出并安装 $expectedVersionName，替换完成后鲸鱼会自动重新打开。"
                        DesktopExit.requestExit?.invoke()
                    } else {
                        hint = "无法自动开始安装，请关闭鲸鱼后手动双击：$script"
                    }
                },
                onLater = {
                    Platform.ui.revealFile(script)
                    DesktopUpdatePrompt.clear()
                    hint = "更新已就绪：关闭鲸鱼后双击「${script.substringAfterLast('\\')}」即可完成替换。"
                }
            )
        }
    }
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

/** 整行可点的展开行 */
@Composable
private fun SectionToggle(title: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (expanded) "收起" else "查看",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** 安全须知正文（与登录页那份口径一致，这里展开成完整说明）
 *
 *  ⚠️ 这里是**对用户的承诺**，必须与代码实际行为一致（P2-C1/C2，2026-09-15）：
 *  - 旧文案写「导出的备份不含本地图片」，而 `Backup` 从 formatVersion 2 起就把头像/背景/聊天图
 *    **以 base64 内嵌进备份文件**（换机才能不断链）——一个专门讲安全的页面说反了，必须改；
 *  - 旧文案写「仅申请 INTERNET 权限」，而清单里还有 `REQUEST_INSTALL_PACKAGES`（应用内装更新包要用），
 *    少写一条会让用户误判风险面。
 */
@Composable
private fun SecurityNotice() {
    // 分平台（用户 2026-09-17 反馈"安全须知给 windows 写一份"）：权限、加密与卸载三段两端不同——
    // 桌面无 Android 权限概念、加密走 DPAPI（WindowsDpapiKeyProvider）、MSI 卸载会连根清空安装目录。
    val isDesktop = Platform.ui.platformId() == "desktop"
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        NoticeLine("本应用没有云端服务器：账号、角色卡、聊天记录都以 JSON 存在本机应用目录。")
        if (isDesktop) {
            NoticeLine("应用不申请任何系统权限；联网仅用于调用你自己配置的模型接口与检查更新。")
        } else {
            NoticeLine("仅申请两项权限：INTERNET（调用你自己配置的模型接口）、REQUEST_INSTALL_PACKAGES（应用内下载后唤起安装器）。")
        }
        NoticeLine("登录密码使用带盐 PBKDF2（12 万次）哈希存储，不保存明文。")
        if (isDesktop) {
            NoticeLine("API Key 使用 Windows DPAPI 加密后落盘（密钥由你的 Windows 账号托管），不进备份文件；加密不可用时回退明文并在设置页明确提示。")
        } else {
            NoticeLine("API Key 使用 Android Keystore 的 AES/GCM 加密后落盘，不进备份文件。")
        }
        NoticeLine("接口地址强制 https，降低明文传输风险。")
        NoticeLine("对话内容、角色设定与 API Key 会发送到你填写的模型服务商，请注意其隐私政策。")
        if (isDesktop) {
            NoticeLine("卸载程序会清空安装目录（连同放在里面的任何文件）；数据默认存在用户目录下不受影响——请勿把数据目录设到安装目录内。")
        } else {
            NoticeLine("卸载应用会销毁 Keystore 密钥，重装后需重新填写 API Key。")
        }
        NoticeLine("「数据备份」导出的文件包含角色卡、会话记录与本地图片（base64 内嵌），请勿随意外发；API Key 不在其中。")
        NoticeLine("更新包校验只防传输损坏：sha256 与安装包走同一条下载通道，不构成对镜像的信任边界。")
    }
}


// ---- 检查更新：拉取远端版本 JSON ----

// 更新清单的读取与比对已抽到共享层 `UpdateChecker`（台账 8）：托盘菜单也要能查更新，
// 而托盘住在 desktopApp、拿不到这个文件里的私有实现。本页与托盘共用同一份口径
// ——包括"追加时间戳穿透 CDN 缓存"和"桌面读 windows 节点"这两条容易漏的细节。
private fun fetchUpdateInfo(): UpdateInfo? = UpdateChecker.fetch(
    AppLinks.UPDATE_JSON_URLS,
    AppLinks.UPDATE_JSON_TOKEN,
    Platform.ui.updateJsonNode()
)
