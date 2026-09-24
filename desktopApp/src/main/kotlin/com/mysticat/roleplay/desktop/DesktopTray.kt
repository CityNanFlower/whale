package com.mysticat.roleplay.desktop

import com.mysticat.roleplay.data.UpdateChecker
import com.mysticat.roleplay.data.UpdateInfo
import com.mysticat.roleplay.ui.DesktopFeatures
import com.mysticat.roleplay.ui.DesktopLog
import com.mysticat.roleplay.ui.DesktopUpdateNotice
import com.mysticat.roleplay.ui.DesktopUpdatePrompt
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.screens.AppLinks
import java.awt.AWTException
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JWindow
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统托盘宿主（M11 ②）。全部 AWT 代码必须跑在 EDT 上——托盘回调本来就在 EDT，
 * 从 Compose 侧进来的调用一律 `invokeLater` 包一层。
 *
 * 行为口径：托盘图标随「关闭时缩小到托盘」设置出现/消失（不开托盘就没有图标，
 * 关窗即退出，与 1.0.0 行为一致）；图标左键单击＝恢复主窗口；菜单＝打开 / 检查更新 / 退出。
 *
 * **更新入口（台账 8）**：更新链路不能只住在「版本与安全」那一页——打包的图标裁剪一旦出错，
 * 那一页就是 `NoClassDefFoundError`，用户连"点检查更新"都做不到（1.0.3 就是这么被困住的）。
 * 托盘这条路不经过任何 Compose 页面：查更新、下载、暂存替换脚本都在这里做完，结果用气泡通知。
 * ⚠ 图标本身只在「关闭时缩小到托盘」开启时存在（默认关），所以它是**开了托盘的用户的**自救路径；
 * 没开托盘的用户的对应手段是**启动时静默检查**（台账 9(a)，第 89 轮已做，见 [startupSilentCheck]——
 * 它住在同一个对象里只是为了复用查更新的状态与气泡，**行为上完全不依赖托盘图标是否存在**）。
 */
internal object DesktopTray {
    private const val TOOLTIP = "鲸鱼 · 本地 AI 角色扮演"
    private var trayIcon: TrayIcon? = null
    private var windowRef: java.awt.Window? = null
    private var exitAction: (() -> Unit)? = null
    private var menuWindow: JWindow? = null

    /** 查更新/下载都在这里跑：托盘回调在 EDT 上，网络绝不能占着 EDT 等 */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── 更新链路的状态。菜单每次右键都按当下状态重建，所以这些只服务于"当前该显示哪几行" ──
    private var checking = false
    private var downloading = false

    /** 查到的、比本机新的版本；null＝没有（还没查 / 已是最新 / 查失败） */
    private var latest: UpdateInfo? = null

    /** staging 完成后待执行的替换脚本（与「版本与安全」页共用 [DesktopUpdatePrompt] 这份状态） */
    private var stagedScript: String? = null

    /** 窗口内容组合后挂进来（托盘要拿窗口引用才能恢复它）；已开着设置的话此刻把图标加上 */
    fun attach(window: java.awt.Window, exit: () -> Unit) {
        windowRef = window
        exitAction = exit
        SwingUtilities.invokeLater {
            if (DesktopFeatures.hideToTrayOnClose) addTray()
        }
    }

    /** 关闭窗口被拦截时走这里：只藏窗口，不退进程（数据由真实退出时统一落盘） */
    fun hideWindow() {
        SwingUtilities.invokeLater { windowRef?.isVisible = false }
    }

    fun restoreWindow() {
        SwingUtilities.invokeLater {
            windowRef?.let { w ->
                if (w is Frame && w.state == Frame.ICONIFIED) w.state = Frame.NORMAL
                w.isVisible = true
                w.toFront()
                w.requestFocus()
            }
        }
    }

    fun applySetting(enabled: Boolean) {
        SwingUtilities.invokeLater { if (enabled) addTray() else removeTray() }
    }

    private fun addTray() {
        if (!SystemTray.isSupported() || trayIcon != null) return
        try {
            val tray = SystemTray.getSystemTray()
            val size = tray.trayIconSize
            val master: Image = ImageIO.read(
                DesktopTray::class.java.getResourceAsStream("/icon/whale-icon.png")
            ) ?: run {
                DesktopLog.mark("托盘：图标资源读不出来，托盘未创建")
                return
            }
            // 托盘图标按系统托盘实际尺寸缩（Windows 一般 16×16），不缩会被裁成左上角一块
            val img = master.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH)
            // ⚠ AWT 菜单在 Windows 上走 GDI 原生绘制，逻辑字体名"Dialog"不是 GDI 认识的物理字体，
            // 回退到的字体不含中文字形 ⇒ 菜单文字变方块（应用内 Compose/Skia 走自己的字体栈，不受影响）。
            // 所以必须显式给物理中文字体；系统没有雅黑时 Font() 自动回退 Dialog（= 维持原状，不会更糟）。
            // ⚠ 不用 AWT PopupMenu：它是原生 GDI 菜单，在部分环境（Win11 25H2 实测、
            // JetBrains/compose-multiplatform#4486）中文渲染成方块，且 setFont 救不回来。
            // 改成右键弹 Swing 自绘菜单——Swing 走 Java2D 字体管线，中文正常（探针实证），
            // 观感与原生菜单几乎一致。左键单击＝恢复主窗口。
            val icon = TrayIcon(img, TOOLTIP).apply {
                addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        when {
                            e.button == MouseEvent.BUTTON1 && e.clickCount == 1 -> restoreWindow()
                            e.button == MouseEvent.BUTTON3 -> showTrayMenu(Point(e.xOnScreen, e.yOnScreen))
                        }
                    }
                })
            }
            tray.add(icon)
            trayIcon = icon
            DesktopLog.mark("托盘图标已创建（关闭时缩小到托盘已开启）")
        } catch (e: AWTException) {
            DesktopLog.mark("托盘：系统拒绝添加图标（${e.message}），本次会话没有托盘")
        } catch (e: Exception) {
            DesktopLog.mark("托盘：创建失败（${e.javaClass.simpleName}: ${e.message}）")
        }
    }

    private fun removeTray() {
        hideTrayMenu()
        val icon = trayIcon ?: return
        trayIcon = null
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }

    // ────────────────────── 更新链路（台账 8） ──────────────────────

    /** 气泡通知（Windows 上是右下角通知）。托盘图标已移除时不做事 */
    private fun balloon(title: String, text: String, warn: Boolean = false) {
        val icon = trayIcon ?: return
        runCatching {
            icon.displayMessage(
                title,
                text,
                if (warn) TrayIcon.MessageType.WARNING else TrayIcon.MessageType.INFO
            )
        }
        DesktopLog.mark("托盘提示：$title｜${text.lineSequence().firstOrNull().orEmpty()}")
    }

    /** 查更新：网络在 IO 线程上跑，结果回 EDT 落状态并弹气泡 */
    private fun checkUpdates() {
        if (checking) return
        checking = true
        DesktopLog.mark("托盘：开始检查更新")
        io.launch {
            val r = runCatching {
                UpdateChecker.fetch(
                    AppLinks.UPDATE_JSON_URLS,
                    AppLinks.UPDATE_JSON_TOKEN,
                    Platform.ui.updateJsonNode()
                )
            }
            SwingUtilities.invokeLater { applyCheckResult(r) }
        }
    }

    private fun applyCheckResult(r: Result<UpdateInfo?>) {
        checking = false
        val info = r.getOrNull()
        when {
            r.isFailure -> {
                latest = null
                balloon("检查更新失败", r.exceptionOrNull()?.message ?: "网络不可达", warn = true)
            }
            info == null -> {
                latest = null
                balloon("检查更新", "更新信息解析失败，请稍后再试。", warn = true)
            }
            !info.isNewerThan(Platform.ui.appVersionCode()) -> {
                latest = null
                balloon("检查更新", "已是最新版本 ${Platform.ui.appVersionName()}")
            }
            else -> {
                latest = info
                val tail = "右键托盘图标 → 「下载并安装」。"
                val lead = info.notes.lineSequence().firstOrNull { it.isNotBlank() }?.take(100)
                balloon("发现新版本 ${info.versionName}", if (lead == null) tail else "$lead\n$tail")
            }
        }
    }

    // ────────────────────── 启动静默检查（台账 9(a)，第 89 轮） ──────────────────────

    /**
     * 启动时静默检查一次更新。**它与托盘图标是否存在无关**——这正是它存在的全部理由：
     * 图标只在「关闭时缩小到托盘」开着时才创建（默认 false，且 Win11 会把新注册的图标塞进
     * 「显示隐藏的图标」浮层），而气泡通知也依赖那个图标 ⇒ 只看托盘的话，**没开托盘的用户
     * 永远收不到更新提示**（这就是台账 9 那条"覆盖面比设想窄"）。
     *
     * 出声口径见 [applySilentCheckResult]：只有真查到更新的版本才提示，其余一律只写日志。
     * 调用点在窗口装配时（见 Main.kt），不阻塞启动——网络跑在 IO 线程上，超时 6 秒。
     */
    fun startupSilentCheck() {
        if (checking) return
        checking = true
        DesktopLog.mark("启动：静默检查更新")
        io.launch {
            val r = runCatching {
                UpdateChecker.fetch(
                    AppLinks.UPDATE_JSON_URLS,
                    AppLinks.UPDATE_JSON_TOKEN,
                    Platform.ui.updateJsonNode()
                )
            }
            SwingUtilities.invokeLater { applySilentCheckResult(r, Platform.ui.appVersionCode()) }
        }
    }

    /**
     * 静默检查的落状态。**与网络解耦**，好让 `--smoke` 直接喂三种结果进来验"出不出声"这条口径
     * ——它失效的样子是"每次启动都弹一句检查更新失败"，靠肉眼点界面很难覆盖到每种输入。
     *
     * 返回本次要提示的版本；null ＝ 不出声。不出声的三种（已是最新 / 解析失败 / 网络失败）都留一行
     * 日志：事后能查，但绝不弹东西——启动时告诉你"检查更新失败"是打扰，用户没主动要这个结果。
     */
    internal fun applySilentCheckResult(r: Result<UpdateInfo?>, currentCode: Int): UpdateInfo? {
        checking = false
        val failure = r.exceptionOrNull()
        if (failure != null) {
            DesktopLog.mark("启动静默检查：失败（${failure.message ?: "网络不可达"}），不提示")
            return null
        }
        val info = r.getOrNull()
        if (info == null) {
            DesktopLog.mark("启动静默检查：清单里没有本平台节点或解析失败，不提示")
            return null
        }
        if (!info.isNewerThan(currentCode)) {
            DesktopLog.mark("启动静默检查：已是最新 ${info.versionName}（本机 code $currentCode），不提示")
            return null
        }
        DesktopLog.mark("启动静默检查：发现新版本 ${info.versionName}（本机 code $currentCode），提示用户")
        latest = info
        // 应用内提示条：不依赖托盘图标，对所有人生效（desktopApp 的主题 Box 里渲染）
        DesktopUpdateNotice.show(info)
        // 开了托盘的用户多一条自助路径（气泡 + 菜单里的「下载并安装」）；图标不在时 balloon 自己不做事
        balloon("发现新版本 ${info.versionName}", "点窗口顶部的「去更新」可直接下载安装。")
        return info
    }

    /** 下载并安装：整条链路（下载 → 校验 → 暂存替换脚本）都不碰任何 Compose 页面 */
    private fun downloadAndInstall(info: UpdateInfo) {
        if (downloading) return
        downloading = true
        DesktopLog.mark("托盘：开始下载 ${info.versionName}")
        balloon("开始下载 ${info.versionName}", "下载完成后会提示是否立即安装。")
        io.launch {
            val r = runCatching {
                val target = UpdateChecker.target(
                    info, Platform.ui.updatesViaInstaller(), AppLinks.DOWNLOAD_URL
                ) ?: throw IllegalStateException("下载地址尚未配置")
                val candidates = AppLinks.downloadCandidates(target.url)
                var lastPct = -1
                val file = Platform.ui.downloadUpdate(candidates, target.sha256) { done, total ->
                    // 进度写在 tooltip 上（悬停可见），不进气泡——那会把通知刷成刷屏
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    if (pct != lastPct) {
                        lastPct = pct
                        SwingUtilities.invokeLater {
                            trayIcon?.toolTip = if (pct >= 0) "$TOOLTIP · 正在下载 $pct%" else "$TOOLTIP · 正在下载"
                        }
                    }
                }.getOrThrow()
                // 安装器形态（M13）：exe 里读不出 build-info，完整性已由 SHA-256 兜底，跳过包内版本核对
                if (!target.isInstaller) {
                    val actual = Platform.ui.readUpdatePackageVersion(file)
                    val mismatch = actual == null ||
                        actual.first != Platform.ui.appPackageName() ||
                        (info.versionCode > 0 && actual.second != info.versionCode)
                    if (mismatch) {
                        val got = actual?.let { "${it.third}（versionCode ${it.second}）" } ?: "无法读取版本信息"
                        throw IllegalStateException("包版本不符：清单写的是 ${info.versionName}，下到的是 $got")
                    }
                }
                file to target.isInstaller
            }
            SwingUtilities.invokeLater { applyDownloadResult(info, r) }
        }
    }

    private fun applyDownloadResult(info: UpdateInfo, r: Result<Pair<File, Boolean>>) {
        downloading = false
        trayIcon?.toolTip = TOOLTIP
        r.fold(
            onSuccess = { (file, isInstaller) ->
                if (!Platform.ui.installUpdate(file)) {
                    balloon(
                        "更新未就绪",
                        "包已下载到 ${file.absolutePath}，但没能自动替换程序目录，请手动处理。",
                        warn = true
                    )
                    return@fold
                }
                if (isInstaller) {
                    // 安装器形态已经拉起系统向导，剩下的是用户点向导（口径同「版本与安全」页）
                    balloon("安装器已启动", "按向导完成安装；安装前请先退出本程序。")
                    return@fold
                }
                stagedScript = DesktopUpdatePrompt.pendingScript
                if (DesktopFeatures.autoInstallUpdate) {
                    DesktopLog.mark("托盘：已勾「以后自动安装」，直接开始替换")
                    startStagedInstall()
                } else {
                    balloon("更新已就绪 ${info.versionName}", "右键托盘图标 → 「立即安装更新并重启」。")
                }
            },
            onFailure = { balloon("下载失败", it.message ?: "网络不可达", warn = true) }
        )
    }

    /** 拉起替换脚本并退出（脚本等本进程退出后覆盖文件、重启应用）；失败则退回手动双击 */
    private fun startStagedInstall() {
        val script = stagedScript ?: return
        val launched = Platform.ui.launchUpdateScript(script)
        DesktopUpdatePrompt.clear()
        stagedScript = null
        if (launched) {
            DesktopLog.mark("托盘：更新脚本已拉起，应用准备退出")
            removeTray()
            exitAction?.invoke()
        } else {
            balloon("无法自动安装", "请关闭鲸鱼后手动双击：$script", warn = true)
        }
    }

    /** 菜单行：白底黑字、悬停高亮，点击＝执行动作并收起 */
    private fun menuItem(label: String, action: () -> Unit): JLabel = JLabel(label).apply {
        font = Font("Microsoft YaHei", Font.PLAIN, 12)
        isOpaque = true
        background = Color.WHITE
        foreground = Color(0x1B, 0x1B, 0x1B)
        border = BorderFactory.createEmptyBorder(7, 20, 7, 28)
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { background = Color(0xEA, 0xF0, 0xF9) }
            override fun mouseExited(e: MouseEvent) { background = Color.WHITE }
            override fun mousePressed(e: MouseEvent) { action() }
        })
    }

    private fun hideTrayMenu() {
        menuWindow?.dispose()
        menuWindow = null
    }

    /** 右键托盘弹出的菜单（Swing 自绘，位置贴鼠标、按所在屏幕夹取不出屏） */
    private fun showTrayMenu(anchor: Point) {
        hideTrayMenu()
        val win = JWindow().apply {
            setType(java.awt.Window.Type.UTILITY)   // 不进任务栏
            isAlwaysOnTop = true
            focusableWindowState = true             // 可聚焦才能收到失焦事件（失焦即收起）
            layout = BorderLayout()
            val column = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = true
                background = Color.WHITE
                border = BorderFactory.createLineBorder(Color(0xC9, 0xC9, 0xC9))
                add(menuItem("打开鲸鱼") {
                    hideTrayMenu()
                    restoreWindow()
                })
                add(JSeparator())
                // 台账 8：更新入口挂在这里。这一行走的是纯 AWT/OkHttp，不经过任何 Compose 页面——
                // 「版本与安全」那页在打包图标裁剪出错的版本里会 NoClassDefFoundError，
                // 那条路走不通时，这里是用户还能点到的更新入口（查、下载、暂存替换脚本都在这做完）。
                add(menuItem(if (checking) "正在检查更新…" else "检查更新") {
                    hideTrayMenu()
                    if (!checking) {
                        balloon("正在检查更新", "结果会以通知形式告诉你。")
                        checkUpdates()
                    }
                })
                val pending = stagedScript
                val found = latest
                if (pending != null) {
                    add(menuItem("立即安装更新并重启") {
                        hideTrayMenu()
                        startStagedInstall()
                    })
                } else if (found != null) {
                    add(menuItem(if (downloading) "正在下载 ${found.versionName}…" else "下载并安装 ${found.versionName}") {
                        hideTrayMenu()
                        if (!downloading) downloadAndInstall(found)
                    })
                }
                add(JSeparator())
                add(menuItem("退出") {
                    hideTrayMenu()
                    // 真退出：落窗口状态（藏窗口时跳过了）→ exitApplication
                    removeTray()
                    exitAction?.invoke()
                })
            }
            add(column, BorderLayout.CENTER)
            addWindowFocusListener(object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent) {}
                override fun windowLostFocus(e: WindowEvent) { hideTrayMenu() }
            })
        }
        win.pack()
        // 多屏：用鼠标所在屏幕的边界夹取（坐标可能为负，不能按单屏 0 起点 max）
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val device = ge.screenDevices.firstOrNull { it.defaultConfiguration.bounds.contains(anchor) }
            ?: ge.defaultScreenDevice
        val b = device.defaultConfiguration.bounds
        val x = (anchor.x).coerceIn(b.x, b.x + b.width - win.width)
        val y = (anchor.y).coerceIn(b.y, b.y + b.height - win.height)
        win.setLocation(x, y)
        win.isVisible = true
        menuWindow = win
        // 把这次**真实渲染出来**的行记进流水日志（遍历组件树，而不是另拼一份"我以为加了什么"）。
        // 为什么要记：Win11 上右键落点常常在"隐藏图标"浮层里，菜单到底弹没弹、弹的是哪几行，
        // 自动化截图会跟终端抢焦点（失焦即收起），只有这条日志能给准话。
        val rendered = buildList {
            fun walk(c: java.awt.Container) {
                for (comp in c.components) {
                    when (comp) {
                        is JLabel -> add(comp.text)
                        is java.awt.Container -> walk(comp)
                    }
                }
            }
            walk(win)
        }
        DesktopLog.mark("托盘菜单已弹出（${win.width}×${win.height} @$x,$y）：${rendered.joinToString(" / ")}")
    }
}

/**
 * 开机自启（M11 ②）：写 `HKCU\...\CurrentVersion\Run`，指向当前启动器 exe。
 * 只认 jpackage 启动器给出的 `jpackage.app-path`——dev（gradle run）与 --smoke 没有这个属性，
 * supported=false，开关置灰，绝不把 java.exe 写进注册表。
 * 增删都以「之后再查一次」为准（reg delete 一个不存在的值也返回 0，不能只看退出码）。
 */
internal object WindowsAutostart {
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE_NAME = "MysticatRoleplay"
    private const val TIMEOUT_S = 8L

    private fun exePath(): String? =
        System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }

    val supported: Boolean get() = exePath() != null

    fun isEnabled(): Boolean {
        val p = ProcessBuilder("reg", "query", RUN_KEY, "/v", VALUE_NAME)
            .redirectErrorStream(true).start()
        p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)
        // 输出编码随系统（中文 Win 是 GBK），但我们只看退出码：查得到＝0
        return p.exitValue() == 0
    }

    fun setEnabled(on: Boolean): Boolean {
        val exe = exePath() ?: return false
        try {
            val pb = if (on) {
                ProcessBuilder("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", exe, "/f")
            } else {
                ProcessBuilder("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
            }
            val p = pb.redirectErrorStream(true).start()
            p.waitFor(TIMEOUT_S, TimeUnit.SECONDS)
            if (p.exitValue() != 0) return false
            return isEnabled() == on
        } catch (e: Exception) {
            DesktopLog.mark("开机自启：写入失败（${e.javaClass.simpleName}: ${e.message}）")
            return false
        }
    }
}

/** `DesktopFeatures.Impl` 的桌面实现：bootstrap 时安装进共享桥 */
internal object DesktopFeaturesBridge : DesktopFeatures.Impl {
    override val traySupported: Boolean
        get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)
    override val launchAtLoginSupported: Boolean
        get() = WindowsAutostart.supported
    override fun applyTraySetting(enabled: Boolean) = DesktopTray.applySetting(enabled)
    override fun applyLaunchAtLogin(enabled: Boolean): Boolean = WindowsAutostart.setEnabled(enabled)
    override fun queryLaunchAtLogin(): Boolean = runCatching { WindowsAutostart.isEnabled() }.getOrDefault(false)
}
