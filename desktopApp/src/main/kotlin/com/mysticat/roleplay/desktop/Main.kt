package com.mysticat.roleplay.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mysticat.roleplay.AppRoot
import com.mysticat.roleplay.ShotOverrides
import com.mysticat.roleplay.ui.DesktopOnboarding
import com.mysticat.roleplay.ui.DesktopOnboardingOverlay
import com.mysticat.roleplay.ui.DesktopOldDirDialog
import com.mysticat.roleplay.ui.DesktopExit
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.AiSettings
import com.mysticat.roleplay.data.Backup
import com.mysticat.roleplay.ui.screens.manualText
import com.mysticat.roleplay.ui.screens.parseManual
import com.mysticat.roleplay.data.BgmPlayer
import com.mysticat.roleplay.data.BgmSources
import com.mysticat.roleplay.data.bgmFormatRejection
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.CategoryManager
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.CharacterFormTags
import com.mysticat.roleplay.data.CharacterVoice
import com.mysticat.roleplay.data.CharacterVoices
import com.mysticat.roleplay.data.CloneVoice
import com.mysticat.roleplay.data.MixSpeaker
import com.mysticat.roleplay.data.VoiceCloneService
import com.mysticat.roleplay.data.AudioPlayerEngine
import com.mysticat.roleplay.data.TtsSpeaker
import com.mysticat.roleplay.data.ModelCatalog
import com.mysticat.roleplay.data.ChatMessage
import com.mysticat.roleplay.data.Conversation
import com.mysticat.roleplay.data.DesktopSpeechCapability
import com.mysticat.roleplay.data.DesktopAudioPlayer
import com.mysticat.roleplay.data.DesktopOldDataCleanup
import com.mysticat.roleplay.data.DesktopUpdater
import com.mysticat.roleplay.data.UpdateChecker
import com.mysticat.roleplay.data.UpdateInfo
import com.mysticat.roleplay.data.UpdateTarget
import com.mysticat.roleplay.data.DirScanCache
import com.mysticat.roleplay.data.DiscoverCatalog
import com.mysticat.roleplay.data.Engines
import com.mysticat.roleplay.data.FormFilter
import com.mysticat.roleplay.data.OutputFormats
import com.mysticat.roleplay.data.PngCardCodec
import com.mysticat.roleplay.data.PromptMode
import com.mysticat.roleplay.data.Profile
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.Security
import com.mysticat.roleplay.data.SessionBgm
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.data.TtsCache
import com.mysticat.roleplay.data.Voice
import com.mysticat.roleplay.data.WindowsDpapiKeyProvider
import com.mysticat.roleplay.data.WorldBook
import com.mysticat.roleplay.data.WorldBookEngine
import com.mysticat.roleplay.data.WorldBookEntry
import com.mysticat.roleplay.data.installDesktopVoice
import com.mysticat.roleplay.data.collectReferencedImagePaths
import com.mysticat.roleplay.data.isLocalFilePath
import com.mysticat.roleplay.ui.DesktopBack
import com.mysticat.roleplay.ui.DesktopShortcuts
import com.mysticat.roleplay.ui.BgTarget
import com.mysticat.roleplay.ui.backgroundFor
import com.mysticat.roleplay.ui.effectiveBackground
import com.mysticat.roleplay.ui.DesktopCrashLog
import com.mysticat.roleplay.ui.DesktopDragDrop
import com.mysticat.roleplay.ui.DesktopDragOverlay
import com.mysticat.roleplay.ui.DesktopFeatures
import com.mysticat.roleplay.ui.LocalDesktopLogo
import com.mysticat.roleplay.ui.DesktopLog
import com.mysticat.roleplay.ui.DesktopPlatformUi
import com.mysticat.roleplay.ui.DesktopShellPrefs
import com.mysticat.roleplay.ui.DesktopToastHost
import com.mysticat.roleplay.ui.DesktopUpdateNotice
import com.mysticat.roleplay.ui.DesktopUpdateNoticeHost
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.renderCardImagePng
import com.mysticat.roleplay.ui.theme.MystiCatTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import java.util.Properties
import kotlin.system.exitProcess

/**
 * 桌面端入口（M4 第 21 轮）。
 *
 * 职责与 Android 的 `App.kt` + `MainActivity.kt` 一一对应：注入数据根目录与平台实现、
 * 装配崩溃记录、装窗口壳。**业务 UI 一行都不在这里**——全在 :shared 的 jvmSharedMain。
 *
 * 启动参数：
 *   `--smoke`  无窗口自检：初始化 + 平台层逐项检查后退出（CI/无人值守验证用，退出码 0/1）
 *   `--shot=<png 路径>`  离屏渲染一张界面截图后退出（**不开窗口**）：布局验收用，
 *                        配合 `--route=<路由>` 可直接渲染某个页面（如 `chat/<id>/<会话id>`）、
 *                        `--rclick=x,y` 注入右键、`--hold=x,y,ms` 按住不放（验长按类手势）、
 *                        `--click=x,y` 注入一次左键单击（验"点了之后界面变成什么样"，
 *                        例如三栏里点会话是否真的不跳页；**可以给多次**，按顺序依次注入）、
 *                        `--tab=N` 指定起始 tab（0发现…4用户）。
 *                        为什么不用屏幕截图：开发机锁屏/有窗口遮挡时抓不到内容，离屏渲染不受影响。
 *   `--open=<名>`  直接摊开"只在页面深处点得到"的界面；**离屏截图与真窗口启动都认这个参数**。
 *                        可用值：`about`＝「版本与安全」弹窗（在「用户」页最下方，滚不过去）、
 *                        `editor`＝第三栏的角色编辑器（挑第一张角色卡，一张都没有就开新建）、
 *                        `newchar`＝同上但直接开「新建角色」、
 *                        `onboarding`＝首启引导页覆盖层（M13 ③；开发机标记已存在，只有靠它才看得到）。
 *   `--dev`    开发运行：允许本机明文 http（假服务端联调），并放开崩溃自测
 */

private const val APP_TITLE = "鲸鱼 · 本地 AI 角色扮演"

/** 自检时写进崩溃日志的标记串（用来验证日志真的落盘并能读回） */
private const val CRASH_SMOKE_MARKER = "鲸鱼自检标记：这条异常只用于验证崩溃日志链路"

fun main(args: Array<String>) {
    val smoke = args.contains("--smoke")
    val dev = args.contains("--dev") || System.getProperty("whale.debug") == "true"

    val paths = DesktopPaths.resolve()
    if (smoke) {
        val code = runSmoke(paths, dev)
        exitProcess(code)
    }
    // --open=…：把"只在页面深处点得到"的界面直接摊开。**离屏截图与真窗口启动都认这个参数**
    // （原来只在 `--shot` 分支里解析，真窗口就没法落这些界面）。
    //  * about   = 「版本与安全」弹窗（在「用户」页最下方，离屏画布滚不到）
    //  * editor  = 第三栏的角色编辑器，随便挑一张已有角色卡（一张都没有就退化成新建）
    //  * newchar = 同上，但直接开「新建角色」
    when (args.firstOrNull { it.startsWith("--open=") }?.substringAfter("=")) {
        null -> {}
        "about" -> com.mysticat.roleplay.ShotOverrides.openVersionDialog = true
        "onboarding" -> com.mysticat.roleplay.ShotOverrides.openOnboarding = true
        "editor" -> com.mysticat.roleplay.ShotOverrides.editorTarget =
            com.mysticat.roleplay.ShotOverrides.EDITOR_FIRST
        "newchar" -> com.mysticat.roleplay.ShotOverrides.editorTarget = "new"
        // update = 假装启动静默检查查到了新版本，摊开窗口顶部那条更新提示条（台账 9(a)）。
        // 它只在**真查到更新的版本**时出现，而开发机版本（1.1.0 / code 7）高于线上（1.0.5 / code 6）
        // ⇒ 正常启动永远看不到它，只能靠这个开关看样式。真窗口与 --shot 都认。
        "update" -> com.mysticat.roleplay.ui.DesktopUpdateNotice.show(
            UpdateInfo(
                versionCode = 99,
                versionName = "9.9.9",
                url = "https://example.invalid/whale.zip",
                notes = "（--open=update 造出来的假更新说明，只为了看提示条的样式）"
            )
        )
        else -> System.err.println("--open= 不认识的取值（可用：about / editor / newchar / onboarding / update）")
    }
    val shotTo = args.firstOrNull { it.startsWith("--shot=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
    if (shotTo != null) {
        val route = args.firstOrNull { it.startsWith("--route=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
        // --rclick=x,y：先在那个位置发一次右键再截图（验"右键菜单"这条 M6 特性的唯一手段——
        // 锁屏时没法用手点，而离屏渲染支持注入指针事件）
        val rclick = args.firstOrNull { it.startsWith("--rclick=") }?.substringAfter("=")
            ?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 2 }
        // --hold=x,y,ms：在 (x,y) 按住指针 ms 毫秒不松（进程内注入指针事件再渲染），
        // 用来验"按住说话/长按"这类**持续按住**的手势——窗口焦点、锁屏、遮挡都不影响。
        val hold = args.firstOrNull { it.startsWith("--hold=") }?.substringAfter("=")
            ?.split(",")?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 3 }
        // --click=x,y：注入一次左键单击再截图。**可以给多次**，按给的顺序依次注入——
        // "先点一下把界面改成某个状态、再点别处看反应"这类两步交互，一次点击够不到。
        // 三栏布局的关键交互（点会话→第三栏出聊天、点分类→第三栏换列表、编辑中切页）靠它自动验收。
        val clicks = args.filter { it.startsWith("--click=") }.mapNotNull { a ->
            a.substringAfter("=").split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
                .takeIf { it.size == 2 }
        }
        // --tab=N：起始 tab（0发现 1聊天记录 2角色卡 3灵感创作 4用户）。只影响截图，正常启动不走这里。
        val tabArg = args.firstOrNull { it.startsWith("--tab=") }?.substringAfter("=")?.toIntOrNull()
        exitProcess(runShot(paths, dev, File(shotTo), route, rclick, hold, clicks, tabArg))
    }

    // 单实例锁：必须在**第一次写数据之前**拿到，否则双击两次图标就是两个进程
    // 互写同一份会话 JSON（原子写只保证单次写不撕裂，救不了并发写）——单实例锁是硬要求。
    val lock = SingleInstanceLock(File(paths.filesRoot, "app.lock"))
    if (!lock.tryAcquire()) {
        System.err.println("鲸鱼已在运行（数据目录 $paths.filesRoot 被另一个实例锁定），本次启动退出")
        // AWT 对话框必须在事件分发线程上弹（从 main 线程直接弹可能死锁）
        javax.swing.SwingUtilities.invokeLater {
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "鲸鱼已经在运行了。\n\n（同一份数据目录只允许一个实例，避免会话记录被两个进程同时改写）",
                "鲸鱼",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            exitProcess(0)
        }
        return
    }
    Runtime.getRuntime().addShutdownHook(Thread { lock.release() })

    bootstrap(paths, dev)

    // M11 ②托盘/自启：桥实现只在正常窗口路径安装（--smoke/--shot 的早退分支走不到这里），
    // 行为偏好随改随落到独立文件（window.properties 只在正常退出时写，跟不上开关节奏）
    DesktopFeatures.install(DesktopFeaturesBridge, File(paths.filesRoot, "desktop.properties"))

    // M13 ③首启引导页：锚点目录没有完成标记 = 首次启动（1.0.0 老用户升级后也会看到一次，
    // 顺势补建桌面快捷方式）。--open=onboarding 强制摊开（开发机的标记早就有了）。
    val onboardingFirst = !File(paths.anchorDir, DesktopPaths.ONBOARDING_MARKER).isFile
    DesktopOnboarding.install(
        config = File(paths.anchorDir, DesktopPaths.CONFIG_FILE),
        marker = File(paths.anchorDir, DesktopPaths.ONBOARDING_MARKER),
        effectiveDataDir = paths.filesRoot.absolutePath,
        effectiveCacheDir = paths.cacheRoot.absolutePath,
        firstLaunchNow = onboardingFirst,
        implNow = DesktopOnboardingBridge
    )
    if (ShotOverrides.openOnboarding) DesktopOnboarding.reopen()

    // 台账 3：新数据/缓存目录已生效（DesktopPaths.resolve 按 paths.properties 走）——
    // 检查旧默认目录：只剩可再生残留就自动清（toast 告知），还有用户数据就弹层等用户决定，绝不默认删。
    // defaultAnchorName 必须跟锚点名走（dev 是 -dev 后缀）：否则 dev 运行会把正式版的默认缓存目录
    // （%LOCALAPPDATA%\MysticatRoleplay\cache）误判成"旧缓存"无条件清掉。
    runCatching {
        DesktopOldDataCleanup.check(paths.anchorDir, paths.filesRoot, paths.cacheRoot, defaultAnchorName = paths.anchorDir.name)
    }

    // 真窗口也能落指定页面（M10 第 32 轮新增）：`--route=` / `--tab=` 原来只在 `--shot` 里生效，
    // 于是"真窗口验证"必须先有人点进去——而**悬停、右键这类交互只有真窗口才能复现**
    // （离屏渲染没有指针，`--click` 也是进程内注入）。窗口启动时把这两个参数当初始状态用。
    val startRoute = args.firstOrNull { it.startsWith("--route=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
    val startTab = args.firstOrNull { it.startsWith("--tab=") }?.substringAfter("=")?.toIntOrNull()

    application {
        val prefs = remember { WindowPrefs.load(paths.windowStateFile) }
        val windowState = rememberWindowState(
            position = prefs.position(),
            size = prefs.size(),
            placement = if (prefs.maximized) WindowPlacement.Maximized else WindowPlacement.Floating
        )
        // M11 ④：拖放/粘贴的异步收尾要用协程（读剪贴板图片、落盘都不能在事件回调线程上做）
        val appScope = rememberCoroutineScope()
        // 真退出（托盘「退出」与关闭窗口共用）：落窗口状态 → 退应用。
        // 缩到托盘的"关闭"不走这里（只藏窗口），所以窗口状态由这一份统一落。
        val exitApp: () -> Unit = {
            WindowPrefs.from(windowState).save(paths.windowStateFile)
            exitApplication()
        }
        // 台账 1/2：更新脚本与改目录自重启的"优雅退出"请求都走这个桥（落窗口状态 → 正常收尾）
        DesktopExit.requestExit = exitApp
        // 第二栏宽度/收起"变更即写"（用户 2026-09-21 + 台账 4）：拖完手柄或点收起时由外壳回调，
        // 当场落一份窗口状态——崩溃/强杀也不丢这次的调整（以前只在正常退出时写）
        com.mysticat.roleplay.ui.DesktopShellPrefs.onPersistRequested = {
            runCatching { WindowPrefs.from(windowState).save(paths.windowStateFile) }
        }
        // 真退出（托盘「退出」、标题栏「关闭」与窗口系统关闭共用同一份逻辑）
        val handleClose: () -> Unit = {
            // M11 ②：开了「关闭时缩小到托盘」就不退，只藏窗口（托盘图标恢复/退出）
            if (DesktopFeatures.hideToTrayOnClose) {
                DesktopTray.hideWindow()
            } else {
                exitApp()
            }
        }
        Window(
            onCloseRequest = handleClose,
            title = APP_TITLE,
            state = windowState,
            // M11 ③（第 40 轮）：去掉系统标题栏，换自绘标题栏（DesktopTitleBar）＋八方向自绘
            // 缩放命中条（DesktopWindowResizeOverlay）——台账 3 的纯 Compose 退路，不引 JNA。
            undecorated = true,
            // 窗口/任务栏图标：从 classpath 读同一份母图产物（tools/make-launcher-icon.py 生成）。
            // 出包后的 exe 图标是另一条路（build.gradle.kts 的 windows.iconFile），两者要一起改。
            icon = painterResource("icon/whale-icon.png"),
            // 桌面没有系统返回键：Esc 交给共享层的返回栈（各页面的 WhaleBackHandler 注册在其中）
            // 全局快捷键（M11 ①，Ctrl+B/N/K/, + Ctrl+滚轮）也从这个唯一入口进：外壳把处理函数
            // 挂在 DesktopShortcuts 上（状态住在 DesktopMainScreen 的组合里，窗口层够不着）。
            // ctrlPressed 要对每个按键事件都记（滚轮事件拿不到键盘修饰符，只能这样旁路）。
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown || event.type == KeyEventType.KeyUp) {
                    DesktopShortcuts.ctrlPressed = event.isCtrlPressed
                }
                event.type == KeyEventType.KeyDown && (
                    (event.key == Key.Escape && DesktopBack.dispatch()) ||
                        DesktopShortcuts.handler?.invoke(event) == true ||
                        // M11 ④：Ctrl+V 粘贴剪贴板里的"纯图片"（截图等）。只在剪贴板**没有文本**时
                        // 拦——有文本时（Office 复制常见图字同贴）让输入框走正常文字粘贴。
                        // 剪贴板读图 + PNG 编码在 IO 线程异步做，这里只做便宜的可用性判断。
                        (event.isCtrlPressed && event.key == Key.V &&
                            clipboardHasImageOnly() && run { pasteClipboardImage(appScope); true })
                    )
            }
        ) {
            // 最小尺寸（M6 桌面布局）：布局分支假设至少能排下侧边栏 + 内容区，
            // 拖到太小时由系统挡住，而不是让 Compose 去挤一个不可能成立的宽度
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(WindowPrefs.MIN_W, WindowPrefs.MIN_H)
                // M11 ②：托盘宿主挂上窗口；若「关闭时缩小到托盘」已开启，此刻把图标加进系统托盘
                DesktopTray.attach(window, exitApp)
                // 台账 9(a)：启动静默检查一次更新。**与托盘设置无关**——托盘图标默认不存在，
                // 没开托盘的用户此前没有任何自动发现新版本的途径。查到新版本时用窗口顶部的提示条
                // 告知（DesktopUpdateNoticeHost），开了托盘的额外收一个气泡；已是最新/失败只写日志。
                DesktopTray.startupSilentCheck()
                // M11 ④：把窗口接进系统的文件拖放（从资源管理器拖 JSON 角色卡 / 图片进来）
                attachFileDrop(window, appScope)
            }
            // 「跟随系统」用 Compose 的 isSystemInDarkTheme()，运行时切换即时生效（与 Android 同）
            val systemDark = isSystemInDarkTheme()
            // 第一栏底部的 logo（M11 ③）：共享层不能引用 desktopApp 资源，走 CompositionLocal 注入
            val logoPainter = painterResource("icon/whale-icon.png")
            Box(Modifier.fillMaxSize()) {
                MystiCatTheme(darkTheme = ThemeState.isDark(systemDark)) {
                    Column(Modifier.fillMaxSize()) {
                        // 自绘标题栏（窗口已 undecorated）：拖动/双击最大化 + 最小化/最大化/关闭
                        DesktopTitleBar(
                            windowState = windowState,
                            window = window,
                            title = APP_TITLE,
                            onClose = handleClose
                        )
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                LaunchedEffect(Unit) { DesktopCrashLog.note("主界面已渲染") }
                                CompositionLocalProvider(LocalDesktopLogo provides logoPainter) {
                                    AppRoot(startRoute = startRoute, initialTab = startTab)
                                }
                                // M13 ③：首启引导页覆盖层（锚点目录无完成标记时自动出现）
                                DesktopOnboardingOverlay()
                                // 台账 3：旧数据目录还有用户数据时的处理弹层（绝不默认删）
                                DesktopOldDirDialog()
                                // 桌面自己的提示浮层（Android 是系统 Toast）
                                DesktopToastHost()
                                // 台账 9(a)：启动静默检查查到新版本时的提示条。挂在窗口顶层而不是某一页里
                                // ⇒ 主界面/聊天/编辑器三条路由都盖得住，且不依赖托盘图标（默认关）
                                DesktopUpdateNoticeHost()
                                // 拖拽提示遮罩（M11 ④）：有文件拖在窗口上方时高亮一圈
                                DesktopDragOverlay()
                            }
                        }
                    }
                }
                // 八方向缩放命中条（最大化时不挂）：盖在内容之上，只在四边/四角吃指针
                DesktopWindowResizeOverlay(windowState, window, Modifier.fillMaxSize())
            }
        }
    }
}

// ─────────────────── 拖拽导入与剪贴板贴图（M11 ④，窗口层的事件翻译） ───────────────────

/**
 * 把系统的文件拖放接进窗口：AWT DropTarget 挂在顶层窗口上，OLE 拖放命中时按鼠标下的组件
 * 上溯找 DropTarget，整个客户区（含 Compose 画布）都能收到，不需要逐个挂子组件。
 * 拖放回调全在 AWT EDT 上——Compose Desktop 的 UI 线程就是它，直接写快照状态是安全的；
 * 文件处理本身仍交给协程（IO 线程，见 DesktopDragDrop.handleFiles 的说明）。
 */
private fun attachFileDrop(window: java.awt.Window, scope: CoroutineScope) {
    val listener = object : DropTargetListener {
        /** 只收文件列表；返回 true＝已 accept（不 accept 时 Windows 显示"禁止"光标且 drop 不来） */
        private fun acceptCopy(e: DropTargetDragEvent): Boolean =
            runCatching { e.transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) }
                .getOrDefault(false)
                .also { ok -> if (ok) e.acceptDrag(DnDConstants.ACTION_COPY) else e.rejectDrag() }

        override fun dragEnter(e: DropTargetDragEvent) {
            DesktopDragDrop.dragOver = acceptCopy(e)
        }

        override fun dragOver(e: DropTargetDragEvent) {
            acceptCopy(e)
        }

        override fun dropActionChanged(e: DropTargetDragEvent) {}

        override fun dragExit(e: DropTargetEvent) {
            DesktopDragDrop.dragOver = false
        }

        override fun drop(e: DropTargetDropEvent) {
            DesktopDragDrop.dragOver = false
            val paths = runCatching {
                (e.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>)
                    .filterIsInstance<java.io.File>()
                    .map { it.absolutePath }
            }.getOrNull().orEmpty()
            if (paths.isEmpty()) {
                e.rejectDrop()
                return
            }
            DesktopLog.mark("拖入 ${paths.size} 个文件：${paths.joinToString() { it.substringAfterLast('/') }}")
            e.acceptDrop(DnDConstants.ACTION_COPY)
            scope.launch {
                val msg = DesktopDragDrop.handleFiles(paths)
                msg?.let { Platform.ui.toast(it, long = true) }
            }
            e.dropComplete(true)
        }
    }
    window.dropTarget = DropTarget(window, DnDConstants.ACTION_COPY, listener, true)
}

/** 剪贴板里是否是"纯图片"（有 imageFlavor 且**没有** stringFlavor——图字同贴时不抢文本粘贴） */
private fun clipboardHasImageOnly(): Boolean = runCatching {
    val cb = Toolkit.getDefaultToolkit().systemClipboard
    cb.isDataFlavorAvailable(DataFlavor.imageFlavor) &&
        !cb.isDataFlavorAvailable(DataFlavor.stringFlavor)
}.getOrDefault(false)

/** 读剪贴板图片 → 编码 PNG → 交共享层落盘并路由给当前页的接收器。IO 线程；返回给 toast 的消息 */
private suspend fun clipboardImageToSink(): String = withContext(Dispatchers.IO) {
    runCatching {
        val image = Toolkit.getDefaultToolkit().systemClipboard
            .getData(DataFlavor.imageFlavor) as? BufferedImage
            ?: return@runCatching "剪贴板里没有图片"
        val buf = ByteArrayOutputStream()
        if (!ImageIO.write(image, "png", buf)) return@runCatching "这种图片格式粘贴不了"
        DesktopDragDrop.handleImageBytes(buf.toByteArray(), "png")
    }.getOrNull() ?: "读取剪贴板图片失败"
}

/** [clipboardImageToSink] 的窗口层入口：协程里做完，结果记应用日志 + toast */
private fun pasteClipboardImage(scope: CoroutineScope) {
    scope.launch {
        val msg = clipboardImageToSink()
        // 应用日志留痕（M9 精神）：粘贴链路是否触发、结果如何，事后可查——
        // 拖放与粘贴都只有瞬时 toast，没有日志就无法区分"没触发"和"触发了没反应"。
        DesktopLog.mark("Ctrl+V 粘贴剪贴板图片 → $msg")
        Platform.ui.toast(msg, long = true)
    }
}

/** 初始化顺序与 Android 的 Application.onCreate 对齐：目录 → 平台实现 → 崩溃守卫 → 网络开关 → 语音 */
private fun bootstrap(paths: DesktopPaths, dev: Boolean) {
    DesktopCrashLog.init(paths.filesRoot)
    DesktopCrashLog.installUncaughtHandler()
    // 应用日志落盘（M9）：**必须尽早接上**——事故现场最缺的恰恰是"启动到出事"之间那几行，
    // 而这一刻 Platform.ui 还是兜底实现（版本号是"未知"），所以版本行等注入完之后再补一条。
    DesktopLog.init(paths.filesRoot, "启动（dev=$dev，数据目录 ${paths.filesRoot.absolutePath}）")

    Repository.init(paths.filesRoot, paths.cacheRoot)
    // API Key 加密：桌面＝Windows DPAPI（用户 2026-09-17 拍板）。取不到（非 Windows / JNA 起不来）
    // 就保持既有回退——明文保存 + 设置页警告，不阻塞使用。见 WindowsDpapiKeyProvider 的说明。
    Security.keyProvider = WindowsDpapiKeyProvider.createIfAvailable(File(paths.filesRoot, "secret.key"))
        .also { if (it != null) Security.keyProviderName = "Windows DPAPI" }
    Platform.ui = DesktopPlatformUi(paths.exportDir, paths.cacheRoot)
    // 这两条要在 Platform.ui 注入**之后**：原来写在注入之前，启动轨迹里的版本号一直是兜底的"未知"
    DesktopLog.mark("桌面版 ${Platform.ui.appVersionName()}(${Platform.ui.appVersionCode()}) 启动就绪")
    DesktopCrashLog.note("启动（桌面 ${Platform.ui.appVersionName()} / dev=$dev）")
    // 语音四件套：系统朗读（PowerShell+System.Speech）/ 播放（javax.sound+mp3spi）/ 录音（WAV）/ 系统识别
    installDesktopVoice(File(paths.filesRoot, "tools"), paths.cacheRoot)
    DesktopSpeechCapability.init(File(paths.filesRoot, "tools"))

    // 背景音乐（第 59 轮）：上次开着就接着放（与 Android 的 App.onCreate 同一句）。
    // 必须放在 installDesktopVoice **之后**——它要先拿到 Voice.audioPlayerFactory 才有播放器可用。
    BgmPlayer.syncFromSettings()

    ThemeState.ensureLoaded()
    AiClient.allowInsecureLoopback = dev
    installCoilLoader()
}

/** 全局 Coil 加载器：与 Android 侧同一套策略（限内存缓存 + 公网 http 升 https） */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
private fun installCoilLoader() {
    SingletonImageLoader.setSafe { context: PlatformContext ->
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            okhttp3.OkHttpClient.Builder()
                                .addInterceptor { chain ->
                                    val req = chain.request()
                                    if (req.url.scheme == "http") {
                                        val upgraded = AiClient.httpsUrl(req.url.toString())
                                        if (upgraded != req.url.toString()) {
                                            return@addInterceptor chain.proceed(
                                                req.newBuilder().url(upgraded).build()
                                            )
                                        }
                                    }
                                    chain.proceed(req)
                                }
                                .build()
                        },
                        cacheStrategy = { coil3.network.CacheStrategy.DEFAULT }
                    )
                )
            }
            .build()
    }
}

// ─────────────────────────── 数据目录与窗口参数 ───────────────────────────

/**
 * 桌面各处路径（约定：数据目录 `%APPDATA%\MysticatRoleplay\roleplay\`，不挨着 exe）。
 * Repository 会自己在 [filesRoot] 下面建 `roleplay/`，所以这里给的是它的父目录。
 *
 * 数据/缓存目录可配置（M13 ③，台账 9(b)/14）：配置读 [anchorDir] 下的 `paths.properties`
 * （`dataDir`/`cacheDir` 两个绝对路径键）——锚点目录固定在默认 %APPDATA% 位置、**不随数据目录走**，
 * 否则改了数据目录后下次启动就找不到配置了。值非法（空/相对路径）一律回默认，解析端不做纠正。
 * 锚点名按运行语境分家（用户 2026-09-21 要求，见 [Companion.anchorNameForRuntime]）：
 * 打包态 `MysticatRoleplay` / 开发态 `MysticatRoleplay-dev`——正式版与开发机的数据、密钥、
 * 首启标记互不可见，且 app.lock 跟着 filesRoot 走 ⇒ 两边可同时运行。
 */
data class DesktopPaths(
    val filesRoot: File,
    val cacheRoot: File,
    val exportDir: File,
    val windowStateFile: File,
    val anchorDir: File
) {
    companion object {
        /** paths.properties 里的键：数据根目录 / 缓存根目录（均为绝对路径） */
        const val CONFIG_FILE = "paths.properties"
        const val ONBOARDING_MARKER = "onboarding.done"

        fun resolve(): DesktopPaths = resolve(
            appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() },
            localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() },
            home = System.getProperty("user.home"),
            anchorName = anchorNameForRuntime()
        )

        /**
         * 锚点目录名按运行语境分家（用户 2026-09-21 要求）：打包态（安装版/便携包，jpackage 启动器
         * 注入了 `jpackage.app-path`，判定与 [com.mysticat.roleplay.data.DesktopRelaunch.currentExe] 同源）
         * 用 `MysticatRoleplay`，开发态（`:desktopApp:run` / --smoke / --shot）用 `MysticatRoleplay-dev`。
         * 此前两边共用一个锚点：开发机的 onboarding 完成标记、paths.properties、密钥与全部会话数据
         * 全混在正式版目录里，正式版用户首启还会被开发机的标记跳过引导。paths.properties / secret.key /
         * app.lock / whale.log 都跟着锚点走 ⇒ 分家后各自 Key 与数据互不可见、可同时运行。
         * 本机迁移（2026-09-21）：原共用目录已整体挪作 `-dev`，正式版下次启动即重走首启引导。
         */
        internal fun anchorNameForRuntime(): String =
            if (com.mysticat.roleplay.data.DesktopRelaunch.currentExe() != null) "MysticatRoleplay"
            else "MysticatRoleplay-dev"

        /** 环境参数化（--smoke 用临时目录测配置往返，不碰真锚点）；[anchorName] 默认原名，dev 语境由调用方传后缀 */
        internal fun resolve(appData: String?, localAppData: String?, home: String, anchorName: String = "MysticatRoleplay"): DesktopPaths {
            val appDataDir = appData ?: home
            val anchor = File(appDataDir, anchorName)
            val config = loadPathConfig(File(anchor, CONFIG_FILE))
            val filesRoot = config["dataDir"]?.let { absoluteDir(it) } ?: anchor
            val localDir = localAppData ?: appDataDir
            val cacheRoot = config["cacheDir"]?.let { absoluteDir(it) }
                ?: File(File(localDir, anchorName), "cache")
            return DesktopPaths(
                filesRoot = filesRoot,
                cacheRoot = cacheRoot,
                // 导出图片跟着数据目录走（用户 2026-09-21：图片要跟随应用数据保存在用户设置的路径里，
                // 不要落到系统 Pictures）——这样换数据目录/做备份时导出的图也在一起
                exportDir = File(filesRoot, "导出的图片"),
                windowStateFile = File(filesRoot, "window.properties"),
                anchorDir = anchor
            )
        }

        /** 读 paths.properties；只认绝对路径且非空的值，其余当没配 */
        private fun loadPathConfig(file: File): Map<String, String> = runCatching {
            if (!file.isFile) return@runCatching emptyMap()
            val p = Properties()
            file.inputStream().use { p.load(it) }
            mapOf("dataDir" to p.getProperty("dataDir"), "cacheDir" to p.getProperty("cacheDir"))
                .filterValues { v -> absoluteDir(v) != null }
        }.getOrDefault(emptyMap())

        private fun absoluteDir(v: String?): File? {
            val t = v?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
            val f = runCatching { File(t) }.getOrNull() ?: return null
            return f.takeIf { it.isAbsolute }
        }
    }
}

/** 窗口尺寸/位置持久化：下次启动回到原处（正常桌面应用的基本期待） */
data class WindowPrefs(
    val x: Int?,
    val y: Int?,
    val width: Int,
    val height: Int,
    val maximized: Boolean
) {
    fun position(): WindowPosition = if (x != null && y != null) {
        WindowPosition.Absolute(x.dp, y.dp)
    } else {
        WindowPosition.Aligned(Alignment.Center)
    }

    fun size(): DpSize = DpSize(width.dp, height.dp)

    fun save(file: File) = runCatching {
        file.parentFile?.mkdirs()
        Properties().apply {
            x?.let { setProperty("x", it.toString()) }
            y?.let { setProperty("y", it.toString()) }
            setProperty("width", width.toString())
            setProperty("height", height.toString())
            setProperty("maximized", maximized.toString())
            // 三栏外壳的第二栏（宽度 / 是否收起）：与窗口状态一起存，用户不必每次重新拖
            setProperty("railWidth", DesktopShellPrefs.railWidth.toString())
            setProperty("railCollapsed", DesktopShellPrefs.railCollapsed.toString())
        }.store(file.outputStream(), "鲸鱼桌面窗口状态")
    }

    companion object {
        /** 首次启动的窗口尺寸：桌面形态（M6）——大窗口，不做"手机比例窄窗" */
        private const val DEFAULT_W = 1440
        private const val DEFAULT_H = 900

        /**
         * 最小窗口尺寸（M6）：侧边栏 156dp + 会话栏 292dp + 聊天区，再窄就排不下了。
         * 同时兜住"用户把窗口拖到很小时布局崩掉"——两个数值与 [window.minimumSize] 一致。
         */
        const val MIN_W = 1024
        const val MIN_H = 680

        fun from(state: androidx.compose.ui.window.WindowState): WindowPrefs {
            val size = state.size
            val pos = state.position
            val maximized = state.placement == WindowPlacement.Maximized
            val x = if (pos is WindowPosition.Absolute) pos.x.value.toInt() else null
            val y = if (pos is WindowPosition.Absolute) pos.y.value.toInt() else null
            return WindowPrefs(
                // 最大化状态下不记位置：还原时用系统给的位置，免得记下最大化时的坐标
                x = if (maximized) null else x,
                y = if (maximized) null else y,
                width = size.width.value.toInt().coerceAtLeast(MIN_W),
                height = size.height.value.toInt().coerceAtLeast(MIN_H),
                maximized = maximized
            )
        }

        fun load(file: File): WindowPrefs {
            val p = Properties()
            runCatching { if (file.isFile) file.inputStream().use { p.load(it) } }
            val w = p.getProperty("width")?.toIntOrNull()?.coerceAtLeast(MIN_W) ?: DEFAULT_W
            val h = p.getProperty("height")?.toIntOrNull()?.coerceAtLeast(MIN_H) ?: DEFAULT_H
            val x = p.getProperty("x")?.toIntOrNull()
            val y = p.getProperty("y")?.toIntOrNull()
            // 三栏外壳的第二栏（宽度 / 收起）：读回进程内单例，界面组合时直接用它排版
            DesktopShellPrefs.railWidth = DesktopShellPrefs.clamp(
                p.getProperty("railWidth")?.toIntOrNull() ?: DesktopShellPrefs.DEFAULT_RAIL_WIDTH
            )
            DesktopShellPrefs.railCollapsed = p.getProperty("railCollapsed")?.toBoolean() ?: false
            // 上次的坐标可能落在已拔掉的显示器上（窗口会"看不见"）——出界就回到居中
            val onScreen = x != null && y != null && isOnScreen(x, y, w, h)
            return WindowPrefs(
                x = if (onScreen) x else null,
                y = if (onScreen) y else null,
                width = w,
                height = h,
                maximized = p.getProperty("maximized")?.toBoolean() ?: false
            )
        }

        private fun isOnScreen(x: Int, y: Int, w: Int, h: Int): Boolean = runCatching {
            val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
            // 至少留 80px 的标题栏在可见区域内，否则用户没法用鼠标把它拖回来
            x + w > bounds.x + 80 && x < bounds.x + bounds.width - 80 &&
                y + 40 > bounds.y && y < bounds.y + bounds.height - 40
        }.getOrDefault(true)
    }
}

// ─────────────────────────── 单实例锁 ───────────────────────────

/**
 * 文件锁式单实例（`app.lock` + [`FileChannel.tryLock`]）。
 * 锁随进程退出自动释放（OS 层面），所以不需要清理陈旧锁文件。
 */
private class SingleInstanceLock(private val lockFile: File) {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    fun tryAcquire(): Boolean = try {
        lockFile.parentFile?.mkdirs()
        val ch = FileChannel.open(
            lockFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE
        )
        val acquired = ch.tryLock()
        if (acquired == null) {
            ch.close()
            false
        } else {
            runCatching {
                ch.truncate(0)
                ch.write(ByteBuffer.wrap("pid=${ProcessHandle.current().pid()}\n".toByteArray()))
            }
            channel = ch
            lock = acquired
            true
        }
    } catch (e: Exception) {
        // OverlappingFileLockException（同 JVM 重复持有）/文件被占 都归到"已经有一个实例"
        false
    }

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}

// ─────────────────────────── 离屏渲染截图（--shot） ───────────────────────────

/**
 * 把界面渲染成一张 PNG（**不开窗口**），用于布局验收。
 *
 * 为什么需要它：桌面屏幕截图抓不到内容的情况很常见（开发机锁屏、窗口被别的窗口盖住、
 * 远程会话没有可见桌面）——离屏渲染走的是同一套 Compose 组合与 Skia 绘制，不受这些影响，
 * 而且 `--route=` 能直接落到某张页面上（窗口截图还得靠人点过去）。
 *
 * 与真窗口的差别：没有真实窗口计时器，所以这里手动推若干帧、每帧之间留出时间，
 * 让 LaunchedEffect 与 Coil 的异步图片加载有机会跑完。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun runShot(
    paths: DesktopPaths,
    dev: Boolean,
    out: File,
    route: String?,
    rightClickAt: List<Float>?,
    holdAt: List<Float>?,
    /** `--click=x,y`，**可以给多次**：按给的顺序依次注入（"先改一下、再切页"这类两步交互靠它） */
    clicks: List<List<Float>>,
    initialTab: Int?
): Int {
    bootstrap(paths, dev)
    // 引导页覆盖层也进离屏渲染（impl=null：浏览/快捷方式置灰，纯布局验收）；
    // 不设 firstLaunch（离屏不去点「开始使用」，避免写真锚点）
    DesktopOnboarding.install(
        config = File(paths.anchorDir, DesktopPaths.CONFIG_FILE),
        marker = File(paths.anchorDir, DesktopPaths.ONBOARDING_MARKER),
        effectiveDataDir = paths.filesRoot.absolutePath,
        effectiveCacheDir = paths.cacheRoot.absolutePath,
        firstLaunchNow = false,
        implNow = null
    )
    if (ShotOverrides.openOnboarding) DesktopOnboarding.reopen()
    // 截图要按**用户当前的窗口状态**渲染：窗口尺寸与三栏外壳的第二栏宽度/收起状态都读回来
    // （副作用写进 DesktopShellPrefs），否则截出来的布局跟用户屏幕上看到的不是同一个。
    runCatching { WindowPrefs.load(paths.windowStateFile) }
    var scene: androidx.compose.ui.ImageComposeScene? = null
    val edt = { block: () -> Unit -> javax.swing.SwingUtilities.invokeAndWait(block) }
    try {
        // 整个渲染必须跑在 AWT 事件分发线程（EDT）上：Compose 的导航/lifecycle 有
        // `Method addObserver must be called on the main thread` 的硬校验，主线程直接跑会抛。
        edt {
            scene = androidx.compose.ui.ImageComposeScene(
                width = 1440,
                height = 900,
                density = androidx.compose.ui.unit.Density(1f)
            ) {
                MystiCatTheme(darkTheme = false) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AppRoot(startRoute = route, initialTab = initialTab)
                            DesktopOnboardingOverlay()
                            // 与真窗口的壳保持一致：这条提示条在窗口内容是顶层浮层，
                            // 离屏画布不摆它，`--open=update` 就看不到自己造的那条提示
                            DesktopUpdateNoticeHost()
                        }
                    }
                }
            }
        }
        val s = scene ?: return 1
        // 一帧一次 invokeAndWait，**中间把睡留在 EDT 之外**：LaunchedEffect 与 Coil 的图片加载
        // 要靠 EDT 的事件队列跑起来，整段霸占 EDT 会让它们一帧都跑不到（首版实测：列表停在"还没有角色卡"）
        var frame = 0
        repeat(40) {
            edt { s.render(frame * 16_000_000L) }
            frame++
            Thread.sleep(40)
        }
        // --hold=x,y,ms：在 (x,y) 按住指针 ms 毫秒不松（进程内注入指针事件并持续渲染），
        // 用来验"按住说话"这类**持续按住**的手势。松手前的界面另存成 `<输出名>-hold.png`，
        // 松手后的界面照旧存到输出名——两相对照就能看出"按住期间"到底发生了什么。
        if (holdAt != null) {
            val at = androidx.compose.ui.geometry.Offset(holdAt[0], holdAt[1])
            val holdMs = holdAt[2].toLong().coerceIn(200L, 30_000L)
            edt {
                s.sendPointerEvent(
                    eventType = androidx.compose.ui.input.pointer.PointerEventType.Press,
                    position = at,
                    buttons = androidx.compose.ui.input.pointer.PointerButtons(isPrimaryPressed = true),
                    button = androidx.compose.ui.input.pointer.PointerButton.Primary
                )
            }
            var held = 0L
            while (held < holdMs) {
                edt { s.render(frame * 16_000_000L) }
                frame++
                Thread.sleep(40)
                held += 40
            }
            var holdPng: ByteArray? = null
            edt {
                holdPng = s.render(frame * 16_000_000L)
                    .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes
            }
            frame++
            val holdFile = File(out.parentFile, out.nameWithoutExtension + "-hold.png")
            holdPng?.let { holdFile.parentFile?.mkdirs(); holdFile.writeBytes(it) }
            println("按住期间截图：${holdFile.absolutePath}")
            edt {
                s.sendPointerEvent(
                    eventType = androidx.compose.ui.input.pointer.PointerEventType.Release,
                    position = at,
                    buttons = androidx.compose.ui.input.pointer.PointerButtons(),
                    button = androidx.compose.ui.input.pointer.PointerButton.Primary
                )
            }
            repeat(12) {
                edt { s.render(frame * 16_000_000L) }
                frame++
                Thread.sleep(40)
            }
        }
        for (click in clicks) {
            // --click=x,y：注入一次左键单击。**必须"按下 → 渲一帧 → 抬起"**：Compose 的点击
            // 由抬起触发，两次事件之间不给一帧，某些手势识别器会把它们当成同一次采样丢掉。
            val at = androidx.compose.ui.geometry.Offset(click[0], click[1])
            edt {
                s.sendPointerEvent(
                    eventType = androidx.compose.ui.input.pointer.PointerEventType.Press,
                    position = at,
                    buttons = androidx.compose.ui.input.pointer.PointerButtons(isPrimaryPressed = true),
                    button = androidx.compose.ui.input.pointer.PointerButton.Primary
                )
            }
            edt { s.render(frame * 16_000_000L) }
            frame++
            Thread.sleep(40)
            edt {
                s.sendPointerEvent(
                    eventType = androidx.compose.ui.input.pointer.PointerEventType.Release,
                    position = at,
                    buttons = androidx.compose.ui.input.pointer.PointerButtons(),
                    button = androidx.compose.ui.input.pointer.PointerButton.Primary
                )
            }
            // 点击后的异步加载（列表重读、聊天页载入会话）要跑几拍才画得出来
            repeat(14) {
                edt { s.render(frame * 16_000_000L) }
                frame++
                Thread.sleep(40)
            }
        }
        if (rightClickAt != null) {
            val at = androidx.compose.ui.geometry.Offset(rightClickAt[0], rightClickAt[1])
            edt {
                val buttons = androidx.compose.ui.input.pointer.PointerButtons(isSecondaryPressed = true)
                s.sendPointerEvent(
                    eventType = androidx.compose.ui.input.pointer.PointerEventType.Press,
                    position = at,
                    buttons = buttons,
                    button = androidx.compose.ui.input.pointer.PointerButton.Secondary
                )
                s.sendPointerEvent(
                    eventType = androidx.compose.ui.input.pointer.PointerEventType.Release,
                    position = at,
                    buttons = androidx.compose.ui.input.pointer.PointerButtons(),
                    button = androidx.compose.ui.input.pointer.PointerButton.Secondary
                )
            }
            // 菜单要一帧才画出来
            repeat(6) {
                edt { s.render(frame * 16_000_000L) }
                frame++
                Thread.sleep(40)
            }
        }
        var png: ByteArray? = null
        var dims = ""
        edt {
            val image = s.render(frame * 16_000_000L)
            png = image.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes
            dims = "${image.width}×${image.height}"
        }
        val bytes = png ?: return 1
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
        println("已渲染界面截图：${out.absolutePath}（$dims${route?.let { "，路由 $it" } ?: ""}）")
        return 0
    } catch (e: Throwable) {
        System.err.println("离屏渲染失败：${e.cause?.message ?: e.message}")
        return 1
    } finally {
        scene?.let { s -> runCatching { edt { s.close() } } }
    }
}

// ─────────────────────────── 无窗口自检（--smoke） ───────────────────────────

/**
 * 自检项的**三态**结果。
 * 为什么要有 SKIP：破坏性流程（全量备份的导入会清空数据根）只能在空数据根上跑，
 * 非空时必须显式报"跳过"——"没跑"不能冒充"跑过且通过"，否则这条自检项就成了摆设。
 */
private data class SmokeItem(val state: String, val name: String, val reason: String = "")

/**
 * 无人值守自检：把"平台层是否真的接上了"逐项验一遍，全部不依赖窗口。
 * 目的：每轮桌面改动的验收不必靠人肉点界面（GUI 可用性验证留给人做）。
 */
private fun runSmoke(paths: DesktopPaths, dev: Boolean): Int {
    val results = mutableListOf<SmokeItem>()
    fun check(name: String, block: () -> Boolean) {
        val ok = runCatching(block).getOrDefault(false)
        results += SmokeItem(if (ok) "OK" else "FAIL", name)
    }
    /** 同 [check]，但名字在**块跑完之后**才算 —— 给"要把量到的数写进名字里"的计时项用 */
    fun checkNamed(name: () -> String, block: () -> Boolean) {
        val ok = runCatching(block).getOrDefault(false)
        results += SmokeItem(if (ok) "OK" else "FAIL", name())
    }
    fun skip(name: String, reason: String) {
        results += SmokeItem("SKIP", name, reason)
    }

    DesktopCrashLog.init(paths.filesRoot)
    // 自检也写应用日志（M9）：命令行下能看到 stdout，打包进产物后只剩这个文件，
    // 所以"自检跑过没有、结论是什么"也得在里面留痕
    DesktopLog.init(paths.filesRoot, "自检 --smoke（数据目录 ${paths.filesRoot.absolutePath}）")
    Repository.init(paths.filesRoot, paths.cacheRoot)
    Security.keyProvider = WindowsDpapiKeyProvider.createIfAvailable(File(paths.filesRoot, "secret.key"))
        .also { if (it != null) Security.keyProviderName = "Windows DPAPI" }
    Platform.ui = DesktopPlatformUi(paths.exportDir, paths.cacheRoot)
    DesktopLog.mark("平台实现已注入（${Platform.ui.appVersionName()}）")
    installDesktopVoice(File(paths.filesRoot, "tools"), paths.cacheRoot)
    DesktopSpeechCapability.init(File(paths.filesRoot, "tools"))
    AiClient.allowInsecureLoopback = dev

    check("数据根目录可写：${paths.filesRoot.absolutePath}") {
        paths.filesRoot.mkdirs()
        val probe = File(paths.filesRoot, ".smoke-probe")
        probe.writeText("ok")
        val ok = probe.readText() == "ok"
        probe.delete()
        ok
    }
    check("会话数据根目录存在：${File(paths.filesRoot, "roleplay").absolutePath}") {
        File(paths.filesRoot, "roleplay").isDirectory
    }
    // 兜底实现的版本号是"未知"/0，所以这一项同时说明"桌面实现真的注入进去了"。
    // ⚠ 版本核对（第 45 轮补）：打包环境里 jpackage 会注入 -Djpackage.app-version=<真实版本>，
    // 它必须与 appVersionName() 一致——否则说明 whale-build.properties 又没加载成功、走了回退值
    // （buildInfo 曾因 apply{} 里 javaClass 绑错接收者，从第 21 轮起坏到第 45 轮，被恰好相等的回退值掩盖）。
    // 开发运行没有该属性，跳过这项核对。
    check("平台实现已注入 + 应用信息：${Platform.ui.appVersionName()} (${Platform.ui.appVersionCode()})") {
        Platform.ui.appVersionName().isNotBlank() && Platform.ui.appVersionName() != "未知" &&
            Platform.ui.appVersionCode() > 0 &&
            System.getProperty("jpackage.app-version")?.let { Platform.ui.appVersionName() == it } ?: true
    }
    // 数据层真的接上了：设置/角色/会话/账号四类读接口都能跑（check 已含异常兜底）
    val charCount = runCatching { Repository.listCharacters().size }.getOrDefault(-1)
    val convCount = runCatching { Repository.listConversationsForAll().size }.getOrDefault(-1)
    check("数据层可读（角色 $charCount 张 / 会话 $convCount 个）") {
        Repository.loadSettings()
        Repository.listProfiles()
        Repository.currentAccountId()
        charCount >= 0 && convCount >= 0
    }
    // 第 64 轮「角色/会话列表读取搬到后台」的两条常驻守护：
    // ① 扫描缓存真的生效 —— 判据是**解析次数不涨**，不是"第二次更快"（计时在冷启动、杀毒扫描下会抖）；
    // ② 冷读一次到底多少钱 —— 合成数据集量出来的数，就是"不搬走的话界面线程每次进列表要付的钱"。
    check("目录扫描缓存：不动的文件不重解析 / 改过的那个才重解析 / 删掉的条目清掉") {
        val dir = File(paths.cacheRoot, "scan-cache-probe").also { it.deleteRecursively(); it.mkdirs() }
        // 用 readText 当"解析"：被测的是缓存语义，不是序列化（序列化由下面那条计时项覆盖）
        val cache = DirScanCache<String> { f -> f.readText().ifBlank { null } }
        File(dir, "a.json").writeText("A")
        File(dir, "b.json").writeText("B")
        val first = cache.read(dir).sorted()
        val n1 = cache.parseCount
        // 文件一个都没动 ⇒ 一次都不该再解析
        val second = cache.read(dir).sorted()
        val n2 = cache.parseCount
        // 改一个（内容长度也不同 ⇒ mtime 与 size 两个判据都变）⇒ 只有它重解析，且读到新值
        File(dir, "b.json").writeText("BB")
        val third = cache.read(dir).sorted()
        val n3 = cache.parseCount
        // 删一个 ⇒ 读出来少一项，且缓存里不留旧条目（否则缓存会一直涨）
        File(dir, "a.json").delete()
        val fourth = cache.read(dir)
        val n4 = cache.parseCount
        dir.deleteRecursively()
        first == listOf("A", "B") && n1 == 2 &&
            second == listOf("A", "B") && n2 == 2 &&
            third == listOf("A", "BB") && n3 == 3 &&
            fourth == listOf("BB") && n4 == 3
    }
    // 合成数据集的体量按"重度用户"取：200 张角色卡（每张带 ~2KB 人设/场景/开场白）
    // ＋ 200×8 个会话（每个 12 条消息）。只判一个很松的上限（抓"缓存整个失效"这种量级的退化），
    // 具体毫秒数打进自检项名字里，供每轮读一眼。
    var benchChars = 0L
    var benchConvs = 0L
    checkNamed({ "列表冷读计时（合成 200 张角色 / 1600 个会话）：角色 ${benchChars}ms、会话 ${benchConvs}ms" }) {
        val bench = File(paths.cacheRoot, "scan-bench").also { it.deleteRecursively(); it.mkdirs() }
        val charDir = File(bench, "characters").also { it.mkdirs() }
        val convDir = File(bench, "conversations").also { it.mkdirs() }
        val prose = "设定与人物小传。".repeat(180)
        val line = "这是一段消息正文，用来把会话文件撑到真实体量。".repeat(6)
        repeat(200) { i ->
            File(charDir, "c$i.json").writeText(
                """{"id":"c$i","name":"角色$i","tagline":"一句话简介$i",""" +
                    """"persona":"$prose","scenario":"$prose","greeting":"$prose",""" +
                    """"categories":["测试"],"createdAt":$i}"""
            )
        }
        repeat(1600) { i ->
            val msgs = (0 until 12).joinToString(",") { m ->
                val role = if (m % 2 == 0) "user" else "assistant"
                """{"role":"$role","content":"$line","timestamp":${1000 + m}}"""
            }
            File(convDir, "v$i.json").writeText(
                """{"id":"v$i","characterId":"c${i % 200}","title":"会话$i","messages":[$msgs],""" +
                    """"createdAt":$i,"updatedAt":${1000 + i}}"""
            )
        }
        val tChars = System.nanoTime()
        val parsedChars = Repository.parseCharactersIn(charDir).size
        benchChars = (System.nanoTime() - tChars) / 1_000_000
        val tConvs = System.nanoTime()
        val parsedConvs = Repository.parseConversationsIn(convDir).size
        benchConvs = (System.nanoTime() - tConvs) / 1_000_000
        bench.deleteRecursively()
        // 解析条数必须对得上（说明合成数据本身合法），耗时给一个很松的上限
        parsedChars == 200 && parsedConvs == 1600 && benchChars in 0..10_000 && benchConvs in 0..30_000
    }
    // A 批次「聊天背景分端」的存储口径：这是**纯逻辑**，但它是整个特性最容易悄悄改错的地方
    // （回退写错 = 老卡背景消失；空串当 null = "移除桌面端背景"移不掉），所以放进自检里常驻。
    check("聊天背景分端（老数据两端回退 / 空串不回退 / 会话覆盖优先）") {
        val legacy = CharacterCard(id = "smoke-bg", name = "自检", backgroundUri = "/tmp/phone.png")
        val conv = Conversation(id = "smoke-conv", characterId = "smoke-bg")
        legacy.backgroundFor(BgTarget.Phone) == "/tmp/phone.png" &&
            // 老卡只有手机端那份 ⇒ 桌面端也用它（用户不必重设）
            legacy.backgroundFor(BgTarget.Desktop) == "/tmp/phone.png" &&
            // 单独设了桌面端 ⇒ 各用各的
            legacy.copy(backgroundUriDesktop = "/tmp/desk.png").backgroundFor(BgTarget.Desktop) == "/tmp/desk.png" &&
            legacy.copy(backgroundUriDesktop = "/tmp/desk.png").backgroundFor(BgTarget.Phone) == "/tmp/phone.png" &&
            // 桌面端"移除"写空串 ⇒ 不再继承手机端那张
            legacy.copy(backgroundUriDesktop = "").backgroundFor(BgTarget.Desktop) == "" &&
            // 本平台那一端 = 桌面端（桌面壳）
            BgTarget.current == BgTarget.Desktop &&
            // 会话覆盖优先；会话里点过"移除"（空串）就不该回退到角色卡那张
            effectiveBackground(legacy, conv) == "/tmp/phone.png" &&
            effectiveBackground(legacy, conv.copy(backgroundUriDesktop = "/tmp/c.png")) == "/tmp/c.png" &&
            effectiveBackground(legacy, conv.copy(backgroundUriDesktop = "")) == ""
    }
    // 角色卡单卡导出/导入（chara_card_v2）也要带上两份背景：用户 2026-09-18 明确要"手机端导出的卡
    // 拿到桌面端导入，桌面端能优先用桌面端那张"。纯逻辑 + 两个临时图片文件，跑完自己清掉。
    check("角色卡导出/导入带上两份聊天背景（桌面端那份不丢、清过的背景不复活）") {
        val phoneBytes = byteArrayOf(11, 22, 33, 44)
        val deskBytes = byteArrayOf(55, 66, 77)
        val phone = Repository.saveImageBytes(phoneBytes, "png")
        val desk = Repository.saveImageBytes(deskBytes, "png")
        val back = CardImport.fromTavernJson(
            CardImport.toTavernJson(
                CharacterCard(
                    id = "smoke-card-io", name = "自检卡", persona = "人设",
                    backgroundUri = phone, backgroundUriDesktop = desk
                )
            )
        )
        // 导回来的是**新落盘的路径**（换机后原路径不存在），内容要一致
        val phoneOk = back.backgroundUri?.let { java.io.File(it).takeIf(java.io.File::isFile)?.readBytes() }
            ?.contentEquals(phoneBytes) == true
        val deskOk = back.backgroundUriDesktop?.let { java.io.File(it).takeIf(java.io.File::isFile)?.readBytes() }
            ?.contentEquals(deskBytes) == true
        // 空串 = "桌面端明确不要背景"：导出/导入走一趟不能变成"那张图又回来了"
        val cleared = CardImport.fromTavernJson(
            CardImport.toTavernJson(
                CharacterCard(
                    id = "smoke-card-io2", name = "清过背景的卡",
                    backgroundUri = phone, backgroundUriDesktop = ""
                )
            )
        )
        val cleanUp = listOfNotNull(phone, desk, back.backgroundUri, back.backgroundUriDesktop, cleared.backgroundUri)
        cleanUp.forEach { runCatching { File(it).delete() } }
        phoneOk && deskOk && cleared.backgroundUriDesktop == null
    }
    // 第 59 轮修的桌面端事故：全项目过去一律用 `startsWith("/")` 当"本机文件路径"判据，那是 **Android 形态**，
    // 而桌面端 `saveImageBytes` 返回的是盘符路径（`D:\…`）⇒ 判据恒假。它坏掉时的表现全在"看不见的地方"
    // （用户的图片被当孤儿删掉、备份不内嵌图片、删图/删字体不执行），所以判据本身常驻自检。
    check("本机文件路径判据（Android 与桌面盘符都算；外链 / 空串 / content:// 不算）") {
        val saved = Repository.saveImageBytes(byteArrayOf(1, 2, 3), "png")
        val ok = isLocalFilePath(saved) &&                                          // 真实形态：本机刚存下的图
            isLocalFilePath("/data/user/0/com.mysticat.roleplay/files/a.png") &&     // Android
            isLocalFilePath("D:\\whale\\images\\a.png") &&                           // Windows：盘符 + 反斜杠
            isLocalFilePath("C:/Users/x/a.png") &&                                   // 盘符 + 正斜杠
            isLocalFilePath("\\\\NAS\\share\\a.png") &&                              // UNC
            !isLocalFilePath("https://example.com/a.png") &&
            !isLocalFilePath("content://media/external/images/1") &&
            !isLocalFilePath("") && !isLocalFilePath(null)
        runCatching { File(saved).delete() }
        ok
    }
    // 同一条判据的**下游后果**：引用集合算成空集 ⇒ 用户所有图片都成了"孤儿"，「清除缓存」会把它们删掉
    // （这是本次事故里最重的一条，用户看到的是头像/背景/聊天图凭空消失）。集合口径本身也常驻自检。
    check("孤儿图判定的引用集合（两端路径都算已引用；分端背景与消息图不缺席）") {
        val img = Repository.saveImageBytes(byteArrayOf(4, 5), "png")
        val refs = collectReferencedImagePaths(
            characters = listOf(
                CharacterCard(
                    id = "s-bgref", name = "自检",
                    avatarUri = img, backgroundUri = "/phone/a.png", backgroundUriDesktop = img
                )
            ),
            conversations = listOf(
                Conversation(
                    id = "s-bgref-conv", characterId = "s-bgref", backgroundUri = "D:/desk/conv.png",
                    messages = listOf(ChatMessage(role = "user", content = "带图", imageUri = img))
                )
            ),
            profiles = listOf(Profile(id = "s-bgref-p", nickname = "我", avatarUri = "D:\\me\\avatar.png"))
        )
        val ok = img in refs && "/phone/a.png" in refs && "D:/desk/conv.png" in refs &&
            "D:\\me\\avatar.png" in refs &&
            // 外链不该进集合（否则会被当路径去删）
            "https://example.com/a.png" !in refs
        runCatching { File(img).delete() }
        ok
    }
    // 角色专属音色（E 批次，2026-09-21）：随卡往返 + "认家"解析 + 缓存键。
    // 全是纯逻辑，但"换角色换不换声音""同一句话要不要重新付费"都由它决定，
    // 而这两件事在界面上都看不出对错（只会表现为"听起来还是上一个声音"），所以常驻在自检里。
    check("角色专属音色（随卡往返 / 认家 / 混合音色 / 缓存键随音色变）") {
        val voice = CharacterVoice(
            enabled = true, provider = "siliconflow", baseUrl = "https://api.siliconflow.cn/v1",
            model = "FunAudioLLM/CosyVoice2-0.5B", voice = "FunAudioLLM/CosyVoice2-0.5B:anna",
            speed = 1.2f, pitch = 0.9f
        )
        val card = CharacterCard(id = "smoke-voice", name = "音色自检卡", voice = voice)
        val global = AiSettings(
            ttsProvider = "builtin", ttsBaseUrl = "https://api.siliconflow.cn/v1",
            ttsModel = "FunAudioLLM/CosyVoice2-0.5B", ttsVoice = "FunAudioLLM/CosyVoice2-0.5B:alex"
        )
        val eff = CharacterVoices.effective(global, card)
        // 卡里没带音色的老卡、以及开关关着的卡：必须原样返回全局（＝本轮之前的行为）
        val old = CharacterCard(id = "smoke-voice-old", name = "老卡")
        val off = card.copy(voice = voice.copy(enabled = false))
        // 认家：卡里的地址本机没有时改用**本机同一家**的地址（换镜像 / 别处分享的卡）
        val foreign = card.copy(voice = voice.copy(baseUrl = "https://dead-mirror.example/v1"))
        // 往返：导出再导入，字段一个字都不丢（含关闭状态的卡）
        val round = CardImport.fromTavernJson(CardImport.toTavernJson(card)).voice
        val roundOff = CardImport.fromTavernJson(CardImport.toTavernJson(off)).voice
        // 混合音色也算角色音色（2026-09-22 用户反馈"角色专属音色也要支持混合音色"）：
        // 混音卡可以**不填单音色**（火山混音时 speaker 固定 custom_mix_bigtts），所以单独造一张。
        val volcUrl = "https://openspeech.bytedance.com"
        val volcGlobal = AiSettings(
            ttsProvider = "builtin", ttsBaseUrl = volcUrl, ttsModel = "seed-tts-1.0",
            ttsVoice = "zh_female_xiaohe_uranus_bigtts",
            // 全局正混着音：卡没开混音时**必须把全局混音关掉**（否则卡的音色会被 custom_mix_bigtts 顶掉）
            ttsMixEnabled = true, ttsMixSpeakers = listOf(MixSpeaker("global_mix_source", 1f))
        )
        val mixSources = ModelCatalog.volcMixSourceVoices().take(2).map { MixSpeaker(it, 0.5f) }
        val mixVoice = CharacterVoice(
            enabled = true, provider = "volcengine", baseUrl = volcUrl, model = "seed-tts-1.0",
            voice = "", mixEnabled = true, mixSpeakers = mixSources
        )
        val mixCard = CharacterCard(id = "smoke-voice-mix", name = "混音自检卡", voice = mixVoice)
        val mixEff = CharacterVoices.effective(volcGlobal, mixCard)
        // 往返：源与权重原样回来（空 voice 也要回来，不能被"没写音色"判成空卡丢掉整块）
        val mixRound = CardImport.fromTavernJson(CardImport.toTavernJson(mixCard)).voice
        // 非火山的家带混音：不套用（`custom_mix_bigtts` 只有火山认），按单音色走
        val mixForeign = CharacterVoices.effective(
            volcGlobal, mixCard.copy(voice = mixVoice.copy(provider = "siliconflow", baseUrl = "https://api.siliconflow.cn/v1"))
        )
        round == voice && roundOff == voice.copy(enabled = false) &&
            // 生效设置拿的是卡里的音色与语速音高，且引擎必然是供应商合成
            eff.ttsVoice.endsWith(":anna") && eff.ttsSpeed == 1.2f && eff.ttsPitch == 0.9f &&
            eff.ttsProvider == "builtin" &&
            // 缓存键必须跟着音色变（不变＝"换了音色还播旧缓存"，界面上看不出来）
            TtsCache.cacheKey(global, "你好") != TtsCache.cacheKey(eff, "你好") &&
            TtsCache.cacheKey(eff, "你好") == TtsCache.cacheKey(eff, "你好") &&
            // 没开 / 没带 ⇒ 与全局同一个对象（行为零变化）
            CharacterVoices.effective(global, old) === global &&
            CharacterVoices.effective(global, off) === global &&
            CharacterVoices.effective(global, null) === global &&
            // 认家成功，且报告为"本机认得这个家"
            CharacterVoices.effective(global, foreign).ttsBaseUrl == "https://api.siliconflow.cn/v1" &&
            CharacterVoices.availability(global, voice).homeFound &&
            // ── 混合音色（2026-09-22）──────────────────────────────────────────
            // ① 混音卡：单音色栏是空的也要生效，源与权重来自卡；② 全局混音被卡的混音替换（不是叠加）
            mixEff.ttsMixEnabled && mixEff.ttsMixSpeakers == mixSources && mixEff.ttsVoice.isEmpty() &&
            // ③ 缓存键随混音变（只换源不换文本也必须换键，否则"改了权重听起来没变"）
            TtsCache.cacheKey(volcGlobal, "你好") != TtsCache.cacheKey(mixEff, "你好") &&
            // ④ 卡没开混音 ⇒ 显式关掉全局混音（老口径，防"个性化被全局混音顶掉"）
            CharacterVoices.effective(volcGlobal, card).ttsMixEnabled.not() &&
            // ⑤ 别家带混音：不套用，但单音色照旧生效
            mixForeign.ttsMixEnabled.not() && mixForeign.ttsVoice.isEmpty() &&
            // ⑥ 往返保真（源/权重/空 voice 一字不差），且混音源按"混音源清单"判可用（不误报）
            mixRound == mixVoice && mixRound.mixSpeakers == mixSources &&
            CharacterVoices.availability(volcGlobal, mixVoice).voiceKnown &&
            // ⑦ 只剩 1 个源等于没混：走单音色，且**不继承全局的混音**（与全局同一口径）
            CharacterVoices.effective(volcGlobal, mixCard.copy(
                voice = mixVoice.copy(
                    voice = ModelCatalog.volcMixSourceVoices().firstOrNull().orEmpty(),
                    mixSpeakers = mixSources.take(1)
                )
            )).ttsMixEnabled.not() &&
            CardImport.toTavernJson(old).contains("voice").not()
    }
    // 声音类型（第 69 轮）：单一 / 混合 / 复刻的判定 + 点胶囊时的档位纠错。
    // 类型**不落库、由数据推**，所以唯一的风险就是"推错了"：推成混合会把单音色栏藏起来（用户找不到），
    // 推成单一又会让混音看起来没生效；档位纠错错一条就是 400（resource ID is mismatched with speaker）。
    check("声音类型（单一/混合/复刻判定 + 档位纠错 + 别家不给混音类型）") {
        val mix = listOf(
            MixSpeaker("zh_female_xiaohe_moon_bigtts", 0.5f),
            MixSpeaker("zh_male_wennuan_mars_bigtts", 0.5f)
        )
        fun k(
            voice: String = "",
            model: String = "",
            mixOn: Boolean = false,
            mixList: List<MixSpeaker> = emptyList(),
            volc: Boolean = true
        ): CharacterVoices.VoiceKind =
            CharacterVoices.kindOf(voice, model, mixOn, mixList, mixSupported = volc)

        val SINGLE = CharacterVoices.VoiceKind.SINGLE
        val MIX = CharacterVoices.VoiceKind.MIX
        val CLONE = CharacterVoices.VoiceKind.CLONE
        k(mixOn = true, mixList = mix) == MIX &&
            // 只有 1 个源＝没混（与 `effective` 的生效判定同一条）→ 类型回单一，音色栏不会被藏
            k(voice = "zh_female_xiaohe_moon_bigtts", mixOn = true, mixList = mix.take(1)) == SINGLE &&
            // 别家（火山之外）即使带着混音数据也不算混合：`effective` 同样忽略它，两处必须同一口径，
            // 否则换供应商后会出现"类型是混合、音色栏被藏、又看不到混音源"的死角
            k(mixOn = true, mixList = mix, volc = false) == SINGLE &&
            // 复刻：`S_` / `icl_` 前缀，或档位已指到 ICL（用户点了复刻胶囊、还没粘 id 时就是这个状态）
            k(voice = "S_abc123", model = "seed-icl-2.0") == CLONE &&
            k(voice = "icl_xyz", model = "seed-icl-2.0") == CLONE &&
            k(model = "seed-icl-2.0") == CLONE &&
            k(voice = "zh_female_xiaohe_uranus_bigtts", model = "seed-tts-2.0") == SINGLE &&
            // 档位纠错：混合 → 1.0（留着 2.0 第一次合成必 400）；复刻 → ICL；单一 → 按音色回推
            ModelCatalog.volcModelForKind(MIX, "", "seed-tts-2.0") == "seed-tts-1.0" &&
            // 混音那一档要看源：全为复刻源才用 ICL（与 AiClient 的自动判定同一条口径）
            ModelCatalog.volcModelForKind(MIX, "", "seed-tts-2.0", mix) == "seed-tts-1.0" &&
            ModelCatalog.volcModelForKind(
                MIX, "", "seed-tts-2.0",
                listOf(MixSpeaker("S_abc", 0.5f), MixSpeaker("icl_xyz", 0.5f))
            ) == "seed-icl-2.0" &&
            // "复刻 → 混合"不许把 ICL 档留下（真机点查发现：那会让界面写着"声音复刻音色"而实际混 1.0 音色）
            ModelCatalog.volcModelForKind(MIX, "", "seed-icl-2.0", mix) == "seed-tts-1.0" &&
            // 手动填过别的档位（例如凭据里指定了 resourceId）就不动他
            ModelCatalog.volcModelForKind(MIX, "", "whatever-custom", mix) == "whatever-custom" &&
            ModelCatalog.volcModelForKind(CLONE, "S_abc", "seed-tts-2.0") == "seed-icl-2.0" &&
            ModelCatalog.volcModelForKind(SINGLE, "zh_female_xiaohe_uranus_bigtts", "seed-icl-2.0") == "seed-tts-2.0" &&
            // 起手源：不足 2 个才补；已经够了两条不重建（用户可能只是想换个类型标签）
            ModelCatalog.volcMixSeedSources(emptyList()).size == 2 &&
            ModelCatalog.volcMixSeedSources(mix) == mix &&
            // 「这张卡的音色到底会不会生效」：解析后不是原样退回全局＝生效 —— 界面用它拦住
            // "试听实际会放出全局音色"这种最坑的假象
            run {
                val g = AiSettings(
                    ttsProvider = "builtin", ttsBaseUrl = "https://openspeech.bytedance.com",
                    ttsModel = "seed-tts-1.0", ttsVoice = "zh_female_xiaohe_uranus_bigtts"
                )
                val on = CharacterCard(
                    id = "k-on", name = "k", voice = CharacterVoice(
                        enabled = true, provider = "volcengine",
                        baseUrl = "https://openspeech.bytedance.com", model = "seed-icl-2.0", voice = "S_abc"
                    )
                )
                val off = CharacterCard(
                    id = "k-off", name = "k", voice = CharacterVoice(enabled = false, voice = "S_abc")
                )
                CharacterVoices.applies(g, on) && !CharacterVoices.applies(g, off) &&
                    !CharacterVoices.applies(g, null)
            }
    }
    // 复刻音色（第 70 轮，用户要求"复刻音色那里做一下应用内录音与合成"）。
    // 这条链路的价值全在网络代码里：URL 拼接、`Bearer;`（分号！）、base64 请求体、状态码解析、
    // 两处"换一种发法再试一次"——只做纯函数断言验不到它们，而真 key 又不可能天天在手边。
    // ⇒ 起一个**本地假服务端**按真接口的形状回话，把用户会走的那几行完整跑一遍。
    val cloneProbe = StringBuilder()
    checkNamed({ "复刻音色·全链路（本地假服务端）$cloneProbe" }) {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        /** 每次上传请求：(Resource-Id, Authorization, 请求体) */
        val reqs = java.util.Collections.synchronizedList(mutableListOf<Triple<String, String, String>>())
        var mode = "ok"
        var polls = 0
        fun reply(ex: com.sun.net.httpserver.HttpExchange, body: String) {
            val b = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.createContext("/api/v1/mega_tts/audio/upload") { ex ->
            val body = ex.requestBody.readBytes().decodeToString()
            paths.add(ex.requestURI.path)
            val res = ex.requestHeaders.getFirst("Resource-Id").orEmpty()
            reqs.add(Triple(res, ex.requestHeaders.getFirst("Authorization").orEmpty(), body))
            // 三个模式分别验：正常 / 参考文本被 WER 拒（要去掉文本重发）/ 资源名不认（要退到旧资源名）
            reply(ex, when {
                mode == "wer" && body.contains("\"text\"") ->
                    """{"BaseResp":{"StatusCode":1109,"StatusMessage":"WERError"}}"""
                mode == "resource" && res != VoiceCloneService.RES_LEGACY ->
                    """{"BaseResp":{"StatusCode":3001,"StatusMessage":"resource ID is mismatched"}}"""
                mode == "fatal" -> """{"BaseResp":{"StatusCode":1002,"StatusMessage":"invalid audio"}}"""
                else -> """{"BaseResp":{"StatusCode":0,"StatusMessage":"success"}}"""
            })
        }
        server.createContext("/api/v1/mega_tts/status") { ex ->
            ex.requestBody.readBytes()
            paths.add(ex.requestURI.path)
            polls++
            // 第一次回"训练中"、第二次回"成功"：验轮询循环真的会等到好为止（而不是第一次就当成完成）
            reply(ex, if (polls < 2) """{"BaseResp":{"StatusCode":0},"status":1}""" else """{"status":2}""")
        }
        server.start()
        val creds = VoiceCloneService.Creds(
            "http://127.0.0.1:${server.address.port}", "app-smoke", "tok-smoke"
        )
        val audio = ByteArray(2048) { (it % 251).toByte() }
        val id = VoiceCloneService.newSpeakerId(1712345678901L)
        val outcome = runBlocking { VoiceCloneService.create(creds, id, audio, "wav") }
        // ① 正常路径：上传体逐字段核对 + 轮询等到"成功"
        val body0 = reqs.firstOrNull()?.third.orEmpty()
        val happy = outcome == VoiceCloneService.Outcome.Ready && polls >= 2 &&
            reqs.size == 1 &&
            // 鉴权头是 `Bearer;token`（**分号**，写成空格会被判未授权）＋ 2.0 那一档资源名
            reqs.first().first == VoiceCloneService.RES_MODERN &&
            reqs.first().second == "Bearer;tok-smoke" &&
            // 路径没拼歪（base 带不带斜杠都要对）
            paths.toSet() == setOf("/api/v1/mega_tts/audio/upload", "/api/v1/mega_tts/status") &&
            // 请求体逐字段核对（断言写成"报文里必须出现什么"，不依赖 JSON 解析顺序）
            body0.contains("\"appid\":\"app-smoke\"") &&
            body0.contains("\"speaker_id\":\"$id\"") &&
            body0.contains("\"audio_format\":\"wav\"") &&
            body0.contains(VoiceCloneService.REFERENCE_TEXT) &&
            body0.contains("\"model_type\":${VoiceCloneService.MODEL_TYPE}") &&
            body0.contains("\"source\":2") &&
            body0.contains("\"language\":${VoiceCloneService.LANGUAGE_CN}") &&
            // 音频要**原样**到服务端（base64 编错一个字节，复刻出来就是别人）
            body0.contains(java.util.Base64.getEncoder().encodeToString(audio))
        // ② 参考文本被 WER 拒 → 去掉文本重发一次（用户不必因为"读错一个字"重录）
        reqs.clear(); mode = "wer"
        val werOk = runCatching { VoiceCloneService.upload(creds, id, audio, "m4a") }.isSuccess &&
            reqs.size == 2 && reqs[0].third.contains("\"text\"") && !reqs[1].third.contains("\"text\"")
        // ③ 资源名不认 → 退到旧资源名；两次换发法可以叠加（先换文本、再换资源）
        reqs.clear(); mode = "resource"
        val resOk = runCatching { VoiceCloneService.upload(creds, id, audio, "wav") }.isSuccess &&
            reqs.size == 3 && reqs[2].first == VoiceCloneService.RES_LEGACY && reqs[0].first == VoiceCloneService.RES_MODERN
        // ④ 音频不合规这类错误**不许重试**：重试只会把真相拖慢、还可能多花一次钱
        reqs.clear(); mode = "fatal"
        val fatalOk = runCatching { VoiceCloneService.upload(creds, id, audio, "wav") }.isFailure && reqs.size == 1
        server.stop(0)
        cloneProbe.append("（正常 ${if (happy) "✓" else "✗轮询$polls"}；WER 退一步 ${if (werOk) "✓" else "✗"}；")
        cloneProbe.append("资源退一步 ${if (resOk) "✓" else "✗"}；不重试 ${if (fatalOk) "✓" else "✗"}）")
        happy && werOk && resOk && fatalOk
    }
    // 复刻音色·纯逻辑（不联网那半：代号命名 / 状态码 / 音色库决定档位 / 落盘往返 / 凭据与格式闸门）
    check("复刻音色·代号与状态（命名合规 / 状态码 / 音色库决定档位与 Resource-Id / 落盘往返）") {
        val id = VoiceCloneService.newSpeakerId(1712345678901L)
        val lib = listOf(CloneVoice(id = id, name = "我的声音 1", createdAt = 1L, ready = true))
        // 官方命名规则：`S_` / `ICL_` / 两个小写字母加下划线 / 行星名 / `_bigtts` 这类保留前后缀一律拒
        // —— 自己造一个 `S_` 开头 id 会被服务端直接判 invalid，所以生成器要绕开它们
        val naming = VoiceCloneService.isValidSpeakerId(id) && id.startsWith("whale") && !id.contains("_") &&
            VoiceCloneService.isValidSpeakerId("whale-abc123") &&
            !VoiceCloneService.isValidSpeakerId("S_abc1234") &&
            !VoiceCloneService.isValidSpeakerId("ICL_abc1234") &&
            !VoiceCloneService.isValidSpeakerId("ab_whatever") &&
            !VoiceCloneService.isValidSpeakerId("short") &&
            !VoiceCloneService.isValidSpeakerId("1abcdefgh") &&
            !VoiceCloneService.isValidSpeakerId("whale-abc_") &&
            !VoiceCloneService.isValidSpeakerId("zh_female_xiaohe_moon_bigtts")
        // 状态码：0 未找到 / 1 训练中 / **2 成功** / 3 失败 / **4 已激活**（只有 2 与 4 能合成）；
        // 网关把码摊平到顶层 `status` 的写法也要认
        fun st(s: String) = VoiceCloneService.parseStatus(s)
        val states = !st("""{"BaseResp":{"StatusCode":0},"status":1}""").ready &&
            !st("""{"BaseResp":{"StatusCode":0},"status":0}""").ready &&
            st("""{"BaseResp":{"StatusCode":0},"status":2}""").ready &&
            st("""{"status":4}""").ready &&
            st("""{"status":3}""").failed && st("""{"status":0}""").notFound &&
            VoiceCloneService.serverError("""{"BaseResp":{"StatusCode":0}}""") == null &&
            VoiceCloneService.serverError("""{"BaseResp":{"StatusCode":1109,"StatusMessage":"WERError"}}""")
                ?.contains("WERError") == true &&
            // "换一种发法可能就过"的只认资源与 WER 两类
            VoiceCloneService.retryable("WERError") &&
            VoiceCloneService.retryable("resource ID is mismatched") &&
            !VoiceCloneService.retryable("invalid audio") && !VoiceCloneService.retryable("HTTP 500")
        // ⚠ 这一条是**回归**：录音复刻出来的音色 id 没有 `S_` 前缀，不查音色库就被当成 2.0 官方音色
        // （界面显示"单一音色"、Resource-Id 发 seed-tts-2.0 ⇒ 合成必报 resource ID is mismatched）
        val g = AiSettings(
            ttsProvider = "builtin", ttsBaseUrl = "https://openspeech.bytedance.com",
            ttsModel = "seed-tts-2.0", ttsVoice = "zh_female_xiaohe_uranus_bigtts",
            ttsCloneVoices = lib
        )
        val withLib = CharacterVoices.kindOf(id, "", false, emptyList(), cloneIds = lib.map { it.id })
        val typeOk = CharacterVoices.kindOf(id, "", false, emptyList(), cloneIds = emptyList()) ==
            CharacterVoices.VoiceKind.SINGLE &&
            withLib == CharacterVoices.VoiceKind.CLONE &&
            ModelCatalog.volcResourceIdForVoice(id) == "seed-tts-2.0" &&
            ModelCatalog.volcResourceIdForVoice(id, lib.map { it.id }) == "seed-icl-2.0" &&
            ModelCatalog.volcModelForKind(CharacterVoices.VoiceKind.CLONE, id, "seed-tts-2.0") == "seed-icl-2.0"
        // 合成侧吃的是**生效设置**：卡里指向自建音色时，音色库要跟着过去（否则真机报 mismatch）
        val card = CharacterCard(
            id = "smoke-clone", name = "复刻自检卡",
            voice = CharacterVoice(
                enabled = true, provider = "volcengine",
                baseUrl = "https://openspeech.bytedance.com", model = "seed-icl-2.0", voice = id
            )
        )
        val eff = CharacterVoices.effective(g, card)
        val synthOk = eff !== g && VoiceCloneService.knownIds(eff) == listOf(id) &&
            ModelCatalog.volcResourceIdForVoice(eff.ttsVoice, VoiceCloneService.knownIds(eff)) == "seed-icl-2.0"
        // 落盘往返：音色库是"录过一次就不该再录"的唯一凭据，存不住就等于白录（还白花一次音色费）。
        // 走 `settingsJsonRoundTrip` 而**不是真写盘**——自检跑在用户真实数据目录上，不能动 settings.json
        val persistOk = Repository.settingsJsonRoundTrip(g).ttsCloneVoices == lib
        // 格式闸门：认得出才发；认不出要当场给用户一句话（服务端不认的后缀发过去只会得到一句看不懂的报错）
        val fmtOk = VoiceCloneService.audioFormatOf("rec-1.m4a") == "m4a" &&
            // Android 录的就是 m4a、桌面录的是 wav —— 两端都在官方支持列表里
            VoiceCloneService.audioFormatOf("rec-1.WAV") == "wav" &&
            VoiceCloneService.audioFormatOf("a.mp3") == "mp3" &&
            VoiceCloneService.audioFormatOf("a.pcm") == "pcm" &&
            VoiceCloneService.audioFormatOf("a.exe") == null
        // 凭据：AppID 与 Token 缺一不可（旧版控制台那对）；只填了新版 API Key 时拿它当 token
        val volcUrl = "https://openspeech.bytedance.com"
        val credOk = VoiceCloneService.creds(AiSettings(), volcUrl) == null &&
            VoiceCloneService.creds(
                AiSettings(speechCredentials = mapOf("volcengine" to mapOf("appId" to "a", "accessToken" to "t"))),
                volcUrl
            )?.token == "t" &&
            VoiceCloneService.creds(
                AiSettings(speechCredentials = mapOf("volcengine" to mapOf("appId" to "a", "apiKey" to "k"))),
                volcUrl
            )?.token == "k"
        naming && states && typeOk && synthOk && persistOk && fmtOk && credOk
    }
    // 朗读调度 · 「念多遍」（第 69 轮，用户反馈"开混合音色会念多遍"）：**后一次朗读必须让前一次彻底退出**。
    // 根因：作废用的是布尔位，而新一次朗读进门就 stop() → 又把它置假；老循环只要当时停在"合成请求"
    // 这个网络挂起点上，醒来就会以为自己还该继续念。两个循环并行念同一串句子 ⇒ 同一条回复念两遍，
    // 而且停止只停得住后一个（`player` 字段被覆盖）。混合音色（整条回复按句合成、每句一个往返）
    // 把挂起窗口拉长，所以只有它身上看得见。这里用可控合成器把那个时序**精确复现**。
    check("朗读调度（后一次朗读让前一次彻底退出：不叠着念 / 停止停得住）") {
        val savedPlayer = Voice.audioPlayerFactory
        val savedEngine = Voice.ttsEngineFactory
        Voice.ttsEngineFactory = { null } // 这条路径不该落到系统 TTS；落到了下面的断言会失败
        val played = mutableListOf<String>()
        val fileText = HashMap<String, String>()
        Voice.audioPlayerFactory = {
            object : AudioPlayerEngine {
                override fun start(file: File, loop: Boolean, volume: Float, onFinished: () -> Unit): Boolean {
                    fileText[file.name]?.let { played.add(it) }
                    // 播完回调必须**异步**（真播放器也是起播返回之后才播完），否则会被当成"起播期间已被停"
                    Thread { Thread.sleep(5); onFinished() }.start()
                    return true
                }

                override fun stopAndRelease() {}
            }
        }
        val ok = runBlocking {
            val speaker = TtsSpeaker()
            val reachedSecond = kotlinx.coroutines.CompletableDeferred<Unit>()
            val releaseFirst = kotlinx.coroutines.CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            // ⚠ 用匿名对象而不是 SAM 糖：`Synth` 是带 suspend 方法的 fun interface，
            //   显式 `Synth { }` 构造形式不做 suspend 转换（编译不过），给了期望类型的变量初始化才行
            val synth = object : TtsSpeaker.Synth {
                override suspend fun file(text: String): File {
                    val n = synchronized(calls) { calls.add(text); calls.size }
                    // 老那一次在第 2 句的**合成**上停住——真实场景就是"请求发出去了、还没回来"
                    if (text.startsWith("第二句")) {
                        reachedSecond.complete(Unit)
                        releaseFirst.await()
                    }
                    val f = File(System.getProperty("java.io.tmpdir"), "whale-tts-smoke-$n.wav")
                    synchronized(fileText) { fileText[f.name] = text }
                    return f
                }
            }
            val first = launch { speaker.speakBuiltin("第一句内容。第二句内容。第三句内容。", synth) }
            reachedSecond.await() // 前一次确实停在第二句的合成上（这个时序就是缺陷的入口）
            val second = launch { speaker.speakBuiltin("后一句内容。", synth) }
            second.join()
            releaseFirst.complete(Unit) // 放它回来：它必须认出自己已作废、不再念第 2/3 句
            first.join()
            speaker.lastFallbackReason == null
        }
        Voice.audioPlayerFactory = savedPlayer
        Voice.ttsEngineFactory = savedEngine
        // 第一句（老）与后一句（新）各念一次；第 2、3 句**一次都不该响**
        ok && played == listOf("第一句内容。", "后一句内容。")
    }
    // 角色卡引擎（E1＋E2，2026-09-22）：分层字段往返 / 老卡回退 / 宏 / PNG 立绘当头像 / 别人的扩展。
    // 这些都是"看起来没事但悄悄丢数据"的地方 —— 界面全绿、只有导出去的文件少了一段，所以常驻自检。
    check("角色卡引擎（v2 分层字段往返 / 老卡整块回退 / 宏 / 立绘当头像 / 扩展不覆写）") {
        // ① 新卡（吃到分层字段）：导出再导入，每个字段一字不差
        val layered = CharacterCard(
            id = "smoke-layered", name = "林晚", tagline = "深夜电台主播",
            description = "{{char}}是深夜电台主播，声音偏低。", personality = "话不多，但记性好。",
            mesExample = "<START>\n{{char}}: 这里是凌晨三点。\n{{user}}: 我睡不着。",
            scenario = "凌晨的直播间，窗外在下雨。", greeting = "「喂？还醒着？」",
            creatorNotes = "适合慢慢聊。", creator = "鲸鱼官方", characterVersion = "1.2",
            systemPrompt = "You are an assistant.", postHistory = "始终称呼用户为「听友」。",
            taskBrief = "根据素材起名字。", outputFormat = "只给 10 个编号名字。",
            extensionsRaw = """{"other_app":{"x":1}}"""
        )
        val back = CardImport.fromTavernJson(CardImport.toTavernJson(layered))
        val layeredRound = back.description == layered.description &&
            back.personality == layered.personality && back.mesExample == layered.mesExample &&
            back.creatorNotes == layered.creatorNotes && back.creator == layered.creator &&
            back.characterVersion == layered.characterVersion && back.systemPrompt == layered.systemPrompt &&
            back.postHistory == layered.postHistory && back.taskBrief == layered.taskBrief &&
            back.outputFormat == layered.outputFormat && back.persona.isBlank()
        // ② 别人的扩展**不覆写**：对方扩展里的数据要原样回来（我们只往里加 whale 节点）
        val extKept = back.extensionsRaw.replace(" ", "") == """{"other_app":{"x":1}}""" &&
            CardImport.toTavernJson(layered).contains("other_app")

        // ③ 老卡（只有合并块 persona）：装配口径不许变 —— 导出再导入仍是"老式整块"，不长出分层字段
        val legacy = CharacterCard(id = "smoke-legacy", name = "老卡", persona = "【背景故事】\n从前有座山。")
        val legacyBack = CardImport.fromTavernJson(CardImport.toTavernJson(legacy))
        val legacyKept = legacyBack.persona == legacy.persona && !legacyBack.hasLayeredPersona() &&
            !legacyBack.description.contains("从前有座山")

        // ④ 装配：分层走分层、老卡整块，两侧互不串味
        val st = AiSettings(chatBaseUrl = "https://api.deepseek.com", chatApiKey = "k", chatModel = "deepseek-flash")
        val pLayered = AiClient.buildSystemPrompt(layered, st)
        val pLegacy = AiClient.buildSystemPrompt(legacy, st)
        val layeredShape = pLayered.contains("【角色描述】") && pLayered.contains("【性格特点】") &&
            pLayered.contains("【对话示例】") && pLayered.contains("林晚是深夜电台主播") &&
            // 宏要展开：卡名替进去，「{{user}}」变「你」，一个生宏都不许留在提示词里
            !pLayered.contains("{{") && pLayered.contains("你: 我睡不着。")
        val legacyShape = pLegacy.contains("从前有座山") && !pLegacy.contains("【角色描述】") &&
            !pLegacy.contains("【对话示例】")

        // ⑤ 工具形态（E4 的装配先落地）：有任务说明与输出格式，没有视角约定、没有"用户设定"默认段
        val pTool = AiClient.buildSystemPrompt(layered, st, mode = PromptMode.TOOL)
        val toolShape = pTool.contains("【任务说明】") && pTool.contains("【输出格式】") &&
            pTool.contains("只给 10 个编号名字。") && !pTool.contains("【视角约定") &&
            !pTool.contains("【用户的设定】") && !pTool.contains("【叙事风格")
        // ⑥ 灵感回复：仍是"不扮演"，资料块照旧在
        val pSuggest = AiClient.buildSystemPrompt(layered, st, mode = PromptMode.SUGGEST)
        val suggestShape = pSuggest.contains("不扮演任何角色") && !pSuggest.contains("【视角约定") &&
            pSuggest.contains("【角色描述】")

        // ⑦ 宏展开本身（含空格写法与 <BOT>）：不认识的宏原样留着，别乱猜
        val macroOk = AiClient.expandMacros("{{ char }}说{{user}}好", layered) == "林晚说你好" &&
            AiClient.expandMacros("<BOT>: 在。", layered) == "林晚: 在。" &&
            AiClient.expandMacros("{{personality}} 未实现", layered) == "{{personality}} 未实现" &&
            // 卡名带 $ 时不能被当成组引用（替换用 lambda 形式的理由）
            AiClient.expandMacros("{{char}}", layered.copy(name = "A\$B")) == "A\$B"

        // ⑧ PNG 立绘当头像：没有 `avatar` 的 PNG 卡补上整张立绘；已有 avatar 的一个字不动。
        // 载具用随包的那个真 PNG（icon/whale-icon.png）——手搓 PNG 只是另一条自检的事。
        val carrier = runCatching {
            Class.forName("com.mysticat.roleplay.desktop.MainKt")
                .getResourceAsStream("/icon/whale-icon.png")?.use { it.readBytes() }
        }.getOrNull()
        val bare = """{"spec":"chara_card_v2","data":{"name":"立绘卡","first_mes":"在。"}}"""
        val withAv = """{"spec":"chara_card_v2","data":{"name":"带头像卡","avatar":"data:image/png;base64,AAAA"}}"""
        val patchedJson = carrier?.let { PngCardCodec.embed(it, bare) }?.let { CardImport.cardJsonFromBytes(it) }
        val keptJson = carrier?.let { PngCardCodec.embed(it, withAv) }?.let { CardImport.cardJsonFromBytes(it) }
        val pngAvatar = patchedJson != null && patchedJson.contains("data:image/png;base64,") &&
            CardImport.fromTavernJson(patchedJson).avatarUri != null &&
            // 自家导出的卡里已经带了内嵌头像（形状同样是 data URL）⇒ 不许被整张立绘顶掉
            keptJson != null && keptJson.contains("base64,AAAA")

        layeredRound && extKept && legacyKept && layeredShape && legacyShape &&
            toolShape && suggestShape && macroOk && pngAvatar
    }
    // 工具形态（E4）：这一条**按"用户会失去什么"写**，不按"实现做了什么"写 ——
    // ① 素材被裁掉＝模型对着空气输出（用户看到的是"答非所问"）；② 工具卡混进角色扮演的口径
    // ＝用户看到故事续写而不是成品；③ 徽标丢了＝列表里认不出哪张是工具。
    check("工具形态（徽标与形态取值 / 装配走任务契约 / 首条素材钉住不裁）") {
        // ① 形态取值与徽标：陪伴是默认、不挂徽标；多线与工具各挂自己的
        val tags = CharacterFormTags.map { it.second }
        val badgeOk = CharacterCard(id = "b0", name = "x").formTagBadge() == null &&
            CharacterCard(id = "b1", name = "x", formTag = "experience").formTagBadge() == "多线" &&
            CharacterCard(id = "b2", name = "x", formTag = "tool").formTagBadge() == "工具" &&
            CharacterCard(id = "b3", name = "x", formTag = "tool").formTagLabel() == "工具" &&
            tags.containsAll(listOf("", "experience", "tool"))

        // ② 装配契约：工具卡走任务契约（不扮演、不要用户设定默认段），陪伴卡仍是角色扮演那一套
        val settings = AiSettings(chatBaseUrl = "https://example.com/v1", chatApiKey = "k", chatModel = "m")
        val toolCard = CharacterCard(
            id = "smoke-tool",
            name = "整理器",
            taskBrief = "把素材整理成待办清单",
            outputFormat = "每行一条，不要标题",
            description = "书面、简洁",
            // 世界观/对话示例在工具形态下**不该**被注入（编辑器里也不显示这两栏）
            scenario = "某个不该出现的故事场景",
            mesExample = "不该出现的对话示例"
        )
        val rpCard = CharacterCard(id = "smoke-rp", name = "将军", persona = "冷面的将军")
        val toolPrompt = AiClient.buildSystemPrompt(
            toolCard, settings, mode = PromptMode.TOOL
        )
        val rpPrompt = AiClient.buildSystemPrompt(rpCard, settings)
        val promptOk = toolPrompt.contains("任务处理工具") &&
            toolPrompt.contains("【任务说明】") && toolPrompt.contains("把素材整理成待办清单") &&
            toolPrompt.contains("【输出格式】") && toolPrompt.contains("每行一条，不要标题") &&
            toolPrompt.contains("【风格参考】") &&
            // 工具形态下这三样都不许出现：世界观、对话示例、以及"把用户当陌生人"那段默认设定
            !toolPrompt.contains("不该出现的故事场景") && !toolPrompt.contains("不该出现的对话示例") &&
            !toolPrompt.contains("刚认识的陌生人") &&
            // 陪伴卡反向断言：仍是角色扮演、且**没有**任务契约
            rpPrompt.contains("【视角约定（必须遵守）】") && !rpPrompt.contains("【任务说明】")

        // ③ 首条素材钉住：素材 2 万字 + 40 条 1000 字的补充要求，条数上限 40（老口径连条数那一关都过不去）
        val material = ChatMessage(role = "user", content = "素".repeat(20000))
        val follow = (1..40).map { ChatMessage(role = "user", content = "补".repeat(1000)) }
        val history = listOf(material) + follow
        val without = AiClient.trimHistory(history, 40)
        val with = AiClient.trimHistory(history, 40, pinFirstUserMessage = true)
        val pinOk =
            // 老口径：素材**确实会丢**（所以这不是"顺手加的开关"，是真实存在的坑）
            without.none { it.content.length == 20000 } &&
            // 新口径：素材在、顺序没乱（素材在最前）、最近的要求也还在
            with.firstOrNull()?.content?.length == 20000 &&
            with.last().content == "补".repeat(1000) &&
            with.size in 2..40 &&
            // 不带素材卡/陪伴形态一个字不变：默认参数下仍然是把最早那条丢出去
            AiClient.trimHistory(listOf(ChatMessage("user", "a".repeat(30000))), 40)
                .isEmpty() &&
            // 预算判据：素材自己就超线时要能报出来（界面据此提示用户）
            AiClient.exceedsContextBudget("素".repeat(24001)) &&
            !AiClient.exceedsContextBudget("素".repeat(24000))

        badgeOk && promptOk && pinOk
    }
    // 工具形态的**接缝**（E4）：上面那条验的是"装配器写得对"，这条验的是"聊天真的按卡的形态发出去"。
    // 装配模式有没有从卡上派生、钉住的素材有没有真的走到请求里、温度有没有换成引擎默认 —— 这三件事
    // 都接在 `chatStream` 那条路径上，**纯函数断言盖不住**（把 mode 传成默认值一样全绿，而用户看到的是
    // 角色扮演的续写而不是成品）。所以起一个本地假服务端，把报文抓下来逐条核对。
    check("工具形态·真链路（假服务端收到的是任务契约；素材没被裁掉；温度按引擎默认）") {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/chat/completions") { ex ->
            bodies.add(ex.requestBody.readBytes().decodeToString())
            val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"- [ ] 交方案（张三）\"}}]}\n\ndata: [DONE]\n\n"
            val b = sse.toByteArray()
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        // 回环明文只在这一条自检里放行，跑完立刻还原（与调试包的 allowInsecureLoopback 同一个闸门）
        val prevInsecure = AiClient.allowInsecureLoopback
        AiClient.allowInsecureLoopback = true
        val tool = CharacterCard(
            id = "smoke-tool-wire",
            name = "整理器",
            formTag = "tool",
            taskBrief = "整理成待办清单",
            outputFormat = "每行一条",
            scenario = "不该出现的场景"
        )
        val settings = AiSettings(
            chatBaseUrl = "http://127.0.0.1:${server.address.port}",
            chatApiKey = "k",
            chatModel = "m"
        )
        // 素材 3 万字 + 40 条补充：老口径连"最近 40 条"那一关都过不去，素材必然凭空消失
        val material = ChatMessage(role = "user", content = "素".repeat(30000))
        val history = listOf(material) + (1..40).map { ChatMessage(role = "user", content = "补".repeat(1000)) }
        val reply = runCatching {
            runBlocking {
                AiClient.chatStream(
                    settings = settings,
                    card = tool,
                    history = history,
                    tailHint = "【输出格式（必须逐条满足，格式不对就重做）】\n每行「- [ ] 事项（负责人）」",
                    onDelta = {}
                )
            }
        }.getOrNull()
        AiClient.allowInsecureLoopback = prevInsecure
        server.stop(0)
        val sent = bodies.firstOrNull().orEmpty()
        reply == "- [ ] 交方案（张三）" &&
            // ① 装配模式从**卡**上派生（任务契约在报文里）
            sent.contains("任务处理工具") && sent.contains("【任务说明】") &&
            sent.contains("整理成待办清单") && sent.contains("【输出格式】") &&
            !sent.contains("不该出现的场景") &&
            // ② 素材真的上了路（钉住不是写在注释里）
            sent.contains("素".repeat(200)) &&
            // ③ 输出格式那条从 tailHint 贴到了用户消息末尾（不是躺在系统提示词里）
            sent.contains("每行「- [ ] 事项（负责人）」") &&
            // ④ 温度走引擎默认 0.3（用户没动过设置 ⇒ 出厂 0.85 应被接管）
            sent.contains("\"temperature\":0.3")
    }
    // E4 收尾（第 74 轮）·形态筛选：列表页按形态（陪伴 / 多线 / 工具）筛。这条同样按"用户会失去什么"
    // 写——**口径与引擎不一致就是"我的卡不见了"**：不认识的 formTag（老数据 / 手改过的 JSON / 将来某个
    // 形态）在引擎那侧一律当陪伴，筛选这侧必须把它归进「陪伴」桶，否则那张卡在"全部"里看得见、
    // 点哪个形态都查不到。② 库里只有一种形态时筛选必须整条失效：否则那个值留在状态里（比如工具卡
    // 都被删了）列表会**凭空空掉**。
    check("E4·形态筛选（未知取值归陪伴 / 单形态时不生效 / 不重不漏）") {
        val rp = CharacterCard(id = "f-rp", name = "陪伴卡")
        val exp = CharacterCard(id = "f-exp", name = "多线卡", formTag = "experience")
        val tool = CharacterCard(id = "f-tool", name = "工具卡", formTag = "tool")
        val play = CharacterCard(id = "f-play", name = "玩法卡", formTag = "play")
        // 将来才有的形态值：引擎当陪伴（Engines.of 的既定口径），筛选必须跟着
        val unknown = CharacterCard(id = "f-unknown", name = "未知形态卡", formTag = "game")
        val cards = listOf(rp, exp, tool, play, unknown)
        // 逐桶筛一遍：[[全部],[陪伴],[多线],[玩法],[工具]]，每桶是卡 id 的集合
        // （桶序跟着 [Engines.all] 走；第 78 轮用户把玩法提到工具前面，这里就跟着换）
        val buckets = FormFilter.options.map { (tag, _) ->
            cards.filter { FormFilter.matches(it, tag) }.map { it.id }.toSet()
        }
        // 四个**形态桶**两两不许重叠（同一张卡同时出现在两个形态下 = 用户会以为筛选坏了）；
        // 「全部」那桶本来就包含所有人，不参与这条
        val formBuckets = buckets.drop(1)
        val overlapping = formBuckets.indices.any { i ->
            (i + 1 until formBuckets.size).any { j ->
                formBuckets[i].intersect(formBuckets[j]).isNotEmpty()
            }
        }
        !overlapping &&
            // 全部 = 所有卡；陪伴 = 陪伴 ＋ 未知取值；多线 / 玩法 / 工具各只有自己
            buckets[0] == setOf("f-rp", "f-exp", "f-tool", "f-play", "f-unknown") &&
            buckets[1] == setOf("f-rp", "f-unknown") &&
            buckets[2] == setOf("f-exp") &&
            buckets[3] == setOf("f-play") &&
            buckets[4] == setOf("f-tool") &&
            // 单形态（只有陪伴卡）时不显示筛选条 —— 显示了一选就会空
            !FormFilter.worthShowing(listOf(rp)) &&
            FormFilter.worthShowing(cards) &&
            // 值域与引擎注册表同一份（加第四个形态时这里自动跟着长，不会漏）
            FormFilter.options.map { it.second } == listOf("全部", "陪伴", "多线", "玩法", "工具") &&
            FormFilter.options.drop(1).map { it.first } == Engines.all.map { it.formTag }
    }
    // E4 收尾（第 74 轮）·「输出格式预设」＋「按格式重排」。两件事都**只有用户点一下才发生**，
    // 所以闸门要按"用户会失去什么"写：① 预设点了什么都没填进去（那一栏还是空的，用户以为设好了）；
    // ② 重排发出去的请求里**丢掉了待重排的那一版成品**（模型于是照素材重做，用户看到的排版没变——
    // 这正是"重排"必须不走 `regenerate` 那条路的原因，见 OutputFormats.reformatHistory 的注释）。
    check("E4 收尾·输出格式预设 + 按格式重排（预设可用 / 成品与素材都在请求里）") {
        // ① 四个预设：都在、互不相同、都是**可核对**的具体要求（不是"输出得清楚一点"这种空话），
        // 且"当前用的是哪个"能反过来认出来（编辑器那颗 chip 的选中态走这个映射）
        val labels = OutputFormats.presets.map { it.label }
        val specs = OutputFormats.presets.map { it.spec }
        val presetsOk = labels == listOf("纯文本", "JSON", "表格", "编号") &&
            specs.none { it.isBlank() } && specs.toSet().size == specs.size &&
            specs.all { OutputFormats.selectedLabel(it) != null } &&
            specs.mapNotNull { OutputFormats.selectedLabel(it) }.toSet().size == 4 &&
            // 用户手改过就不该错认成某个预设（否则 chip 会亮着而栏里的内容不是它）
            OutputFormats.selectedLabel("先给结论，再列三条依据") == null

        // ② 重排请求的形状：成品与素材都留在上下文里 + 末尾一条不入库的指令
        val material = ChatMessage(role = "user", content = "素".repeat(3000))
        val draft = ChatMessage(role = "assistant", content = "这一版排版不对")
        val msgs = listOf(material, draft)
        val hist = OutputFormats.reformatHistory(msgs, 1)
        val reformatOk = hist != null &&
            hist.size == msgs.size + 1 && hist.contains(material) && hist.contains(draft) &&
            // 顺序没乱：素材仍在最前、重排指令在最后（贴在生成点正前方才有近因）
            hist.first() == material && hist.last().role == "user" &&
            hist.last().content == OutputFormats.reformatInstruction() &&
            // 指令要说清"按【输出格式】重排"——它靠这个名字引用 tailHint/系统提示词里的那段要求
            OutputFormats.reformatInstruction().contains("【输出格式】") &&
            // 只对**最后一条** assistant 消息重排：否则新成品会落到对话末尾，位置就错了
            OutputFormats.reformatHistory(msgs, 0) == null &&
            OutputFormats.reformatHistory(emptyList(), 0) == null

        // ③ 接缝：拿这条历史真发一次（假服务端抓报文）——素材、待重排的成品、重排指令、
        // 预设文本（进【输出格式】）四样都要在报文里
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/chat/completions") { ex ->
            bodies.add(ex.requestBody.readBytes().decodeToString())
            val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"1. 交方案\"}}]}\n\ndata: [DONE]\n\n"
            val b = sse.toByteArray()
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        val prevInsecure = AiClient.allowInsecureLoopback
        AiClient.allowInsecureLoopback = true
        val card = CharacterCard(
            id = "smoke-reformat", name = "整理器", formTag = "tool",
            taskBrief = "整理成待办清单", outputFormat = specs.first()
        )
        runCatching {
            runBlocking {
                AiClient.chatStream(
                    settings = AiSettings(
                        chatBaseUrl = "http://127.0.0.1:${server.address.port}",
                        chatApiKey = "k", chatModel = "m"
                    ),
                    card = card,
                    history = hist.orEmpty(),
                    onDelta = {}
                )
            }
        }
        AiClient.allowInsecureLoopback = prevInsecure
        server.stop(0)
        val sent = bodies.firstOrNull().orEmpty()
        presetsOk && reformatOk &&
            sent.contains("素".repeat(200)) &&
            sent.contains("这一版排版不对") &&
            sent.contains("按格式重排") &&
            sent.contains(specs.first())
    }
    // 第 75 轮·灵感创作的「工具」形态：生成器必须**按形态要字段**。这条按"用户会失去什么"写——
    // 照角色卡那套要（persona / scenario / greeting）时，模型不会给出 task_brief 与 output_format，
    // 而工具卡的装配器**只读这两个**（见 `AiClient.buildSystemPrompt` 的 TOOL 分支），于是用户点
    // 「生成角色卡 → 保存并开聊」，拿到的是一张任务说明与输出格式全空的工具卡：不报错、不崩，就是没法用。
    // 只断言 prompt 文本不够（"要对了字段"与"落对了卡"是两件事），所以照旧起假服务端走真链路。
    check("工具 / 玩法形态·灵感创作生成（各按形态要字段 / 落卡对 / 装配真读得到）") {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        fun jsonStr(s: String) =
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        server.createContext("/chat/completions") { ex ->
            val body = ex.requestBody.readBytes().decodeToString()
            bodies.add(body)
            // 模型照新口径作答：要什么给什么（这一条要验的是"生成器**要对了字段**、落卡也落对了"）。
            // 玩法形态（E4 拆档）与工具是两套字段，所以按请求里出现的字段名分派。
            val cardJson = if (body.contains("play_rules")) {
                """{"name":"二十问猜人","tagline":"我想一个人，你来猜",""" +
                    """"play_rules":"只回答是或不是，不可报出具体名字。\n答不是时必须排除该候选，此后不再提。",""" +
                    """"play_state":"已排除的候选、当前第几问。","play_options":3,""" +
                    """"opening":"我想好了一个人，你开始提问吧。","setup":"你想一个人物，我用二十个问题猜出来。",""" +
                    """"style":"轻松、爱吐槽","style_habit":"一次只问一句","categories":["其他"]}"""
            } else {
                // 工具：只有工具字段，没有 persona / scenario / greeting
                """{"name":"会议纪要整理","tagline":"把记录变成待办",""" +
                    """"task_brief":"把用户发来的会议记录整理成待办清单。一条一事，合并重复项，去掉寒暄。",""" +
                    """"output_format":"每行「- [ ] 事项（负责人）」，不要标题、不要总结段。",""" +
                    """"style":"书面、简洁、只用短句","style_habit":"直接给结论、不寒暄",""" +
                    """"examples":["把这段会议记录整理成 5 条待办，每条不超过 15 字",""" +
                    """"把这段纪要里谁负责什么列成一张表","把这段记录里没定下来的事单独列出来"],""" +
                    """"categories":["其他"]}"""
            }
            val reply = "{\"choices\":[{\"message\":{\"content\":${jsonStr(cardJson)}}}]}"
            val b = reply.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        val prevInsecure = AiClient.allowInsecureLoopback
        AiClient.allowInsecureLoopback = true
        val settings = AiSettings(
            chatBaseUrl = "http://127.0.0.1:${server.address.port}",
            chatApiKey = "k", chatModel = "m"
        )
        val card = runCatching {
            runBlocking {
                com.mysticat.roleplay.data.CharacterGenerator.generate(
                    settings, "把会议记录整理成待办清单", emptyList(), "tool"
                )
            }
        }.getOrNull()
        // 玩法形态（E4 拆档）：同一台假服务端，第二次生成要的是**另一套字段**
        val playCard = runCatching {
            runBlocking {
                com.mysticat.roleplay.data.CharacterGenerator.generate(
                    settings, "二十问猜人", emptyList(), "play"
                )
            }
        }.getOrNull()
        AiClient.allowInsecureLoopback = prevInsecure
        server.stop(0)
        val sent = bodies.firstOrNull().orEmpty()
        val sentPlay = bodies.getOrNull(1).orEmpty()
        if (card == null || playCard == null) return@check false
        // 装配这一段走**生产同一条路**：模式从卡上派生（Engines.of），不是这里手填一个 TOOL
        val prompt = AiClient.buildSystemPrompt(card, settings, mode = Engines.of(card).promptMode)
        // 玩法卡同样走生产那条路装配：生成得对、装配读不到 ⇒ 用户还是拿到一局没有规则的玩法
        val playPrompt = AiClient.buildSystemPrompt(playCard, settings, mode = Engines.of(playCard).promptMode)
        // ⑤ 玩法形态要的是它自己那套字段，且**不再**索要角色卡那一套（要了 persona，模型就去写人设、
        //    而不写规则与状态项 —— 这正是"生成出来是废卡"的机制）
        val playOk = sentPlay.contains("play_rules") && sentPlay.contains("play_state") &&
            sentPlay.contains("play_options") && sentPlay.contains("opening") &&
            !sentPlay.contains("persona") && !sentPlay.contains("greeting") &&
            // 落到的卡是玩法卡该有的形状：规则 / 状态项 / 选项数 / 开局引导 / 玩法设定都在
            playCard.formTag == "play" && playCard.playRules.isNotBlank() &&
            playCard.playState.isNotBlank() && playCard.playOptions == 3 &&
            playCard.greeting.isNotBlank() && playCard.scenario.isNotBlank() &&
            playCard.persona.isBlank() &&
            // 与工具那套**井水不犯河水**：玩法卡里不该出现任务说明 / 输出格式
            playCard.taskBrief.isBlank() && playCard.outputFormat.isBlank() &&
            // 生成出来的东西真的进得了系统提示词
            playPrompt.contains("【玩法规则】") && playPrompt.contains(playCard.playRules) &&
            playPrompt.contains("【本局要记的状态】") && playPrompt.contains("【玩法设定】") &&
            playPrompt.contains("3 个编号选项") &&
            // 角色扮演那一套一个字都不许混进来
            !playPrompt.contains("【角色设定】") && !playPrompt.contains("【任务说明】")
        // ① 请求里索要的是工具字段，且**不再**索要角色卡那一套 —— 要了 persona，模型就会去写人设、
        //    而不写任务说明，这正是"生成出来是废卡"的机制
        sent.contains("task_brief") && sent.contains("output_format") && sent.contains("examples") &&
            !sent.contains("persona") && !sent.contains("scenario") && !sent.contains("开场白") &&
            // ② 落到的卡是工具卡该有的形状：任务说明 / 输出格式 / 风格参考 / 三条使用示例都在，
            //    人设与世界观留空（这两个槽位在工具形态下已被风格参考与使用示例占用）
            card.formTag == "tool" && card.taskBrief.isNotBlank() && card.outputFormat.isNotBlank() &&
            card.description.isNotBlank() && card.personality.isNotBlank() &&
            card.effectiveGreetings().size == 3 && card.persona.isBlank() && card.scenario.isBlank() &&
            // ③ 生成出来的东西真的进得了系统提示词（生成得对、装配读不到 ⇒ 用户还是拿到空工具）
            prompt.contains("【任务说明】") && prompt.contains(card.taskBrief) &&
            prompt.contains(card.outputFormat) && prompt.contains("【风格参考】") &&
            prompt.contains(card.description) &&
            // ④ 角色扮演那一套一个字都不许混进来
            !prompt.contains("【世界观 / 当前场景】") && !prompt.contains("【对话示例】") &&
            // ⑤ 玩法那一套（见下）
            playOk
    }
    // 第 78 轮·①（用户反馈：「工具」既然已经是形态，就不要再作为「主题」出现了）
    // 真事：卡站的 tags 常带 Tool / 工具，导入时被 [CategoryManager.ensure] 自动登记进类型表 ——
    // 真机的 settings.json 里就躺着一条「工具」，于是它既是形态筛选的第四档、又能当类型/标签选，
    // 同一件事两个入口，用户会以为"选了类型=选了形态"。
    // 按"已有数据也自愈"写：把真机那种类型表原样塞进去，读写两端都不该再看见形态名，
    // 而且**导入那条路不许再登记**（只修显示、不修登记的话，用户下次导一张卡它就回来了）。
    check("类型/标签·形态名（工具/玩法…）不进类型表：已有数据自愈 + 导入不再登记") {
        val before = Repository.loadSettings()
        try {
            Repository.saveSettings(
                before.copy(
                    categories = listOf("古风", "工具", "校园", "玩法"),
                    categoriesInitialized = true
                )
            )
            val healed = CategoryManager.all()
            // ensure 正是导入那条路（Repository.saveCharacter 调的）
            CategoryManager.ensure(listOf("工具", "科幻"))
            val afterEnsure = CategoryManager.all()
            healed.none { it in Engines.formLabels } && healed.contains("古风") &&
                afterEnsure.none { it in Engines.formLabels } &&
                afterEnsure.contains("科幻") && afterEnsure.contains("校园")
        } finally {
            Repository.saveSettings(before)
        }
    }
    // 第 78 轮·②（用户反馈：生成玩法卡报「Element class kotlinx.serialization.json.JsonArray
    // (Kotlin reflection is not available) is not a JsonPrimitive」）
    // 玩法卡的规则与状态项天然是"一条一条"的，模型经常直接给 JSON 数组（`"play_rules": ["…","…"]`），
    // 而解析器原来一律走 `jsonPrimitive` —— 碰见数组即抛，**整张卡生成失败**，用户看到的是一个写着
    // 那行类名的「出错了」弹窗，一整轮创作白费（真机必现）。
    // 按"用户会失去什么"写：归一后两条规则 / 两条状态项都在，且**真的进得了系统提示词**
    // （成卡但装配读不到 = 用户拿到的还是一局没有规则的玩法）。
    check("玩法卡·模型把规则与状态项给成数组（形状归一后照样成卡、且装配读得到）") {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        fun jsonStr(s: String) =
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        server.createContext("/chat/completions") { ex ->
            ex.requestBody.readBytes()
            // 模型的三种"不老实"一次给全：规则与状态项给数组、play_options 给字符串、
            // categories 不套数组（没套数组时也要认，否则这张卡会掉进「其他」）
            val cardJson = """{"name":"二十问猜人","tagline":"我想一个人，你来猜",""" +
                """"play_rules":["只回答是或不是，不可报出具体名字","答不是时必须排除该候选，此后不再提"],""" +
                """"play_state":["已排除的候选","当前第几问"],"play_options":"3",""" +
                """"opening":"我想好了一个人，你开始提问吧。","setup":"你用二十个问题猜我心里那个人。",""" +
                """"style":"轻松、爱吐槽","style_habit":"一次只问一句","categories":"其他"}"""
            val reply = "{\"choices\":[{\"message\":{\"content\":${jsonStr(cardJson)}}}]}"
            val b = reply.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        val prevInsecure = AiClient.allowInsecureLoopback
        AiClient.allowInsecureLoopback = true
        val settings = AiSettings(
            chatBaseUrl = "http://127.0.0.1:${server.address.port}", chatApiKey = "k", chatModel = "m"
        )
        val card = runCatching {
            runBlocking {
                com.mysticat.roleplay.data.CharacterGenerator.generate(
                    settings, "二十问猜人", emptyList(), "play"
                )
            }
        }.getOrNull()
        AiClient.allowInsecureLoopback = prevInsecure
        server.stop(0)
        if (card == null) return@check false
        val prompt = AiClient.buildSystemPrompt(card, settings, mode = Engines.of(card).promptMode)
        card.playRules == "只回答是或不是，不可报出具体名字\n答不是时必须排除该候选，此后不再提" &&
            card.playState == "已排除的候选\n当前第几问" &&
            card.playOptions == 3 &&
            card.greeting == "我想好了一个人，你开始提问吧。" &&
            card.categories == listOf("其他") &&
            prompt.contains("【玩法规则】") && prompt.contains("答不是时必须排除该候选") &&
            prompt.contains("【本局要记的状态】") && prompt.contains("当前第几问")
    }
    // 第 78 轮·③（用户反馈：桌面端生成玩法卡是「timeout」）
    // 整卡 JSON 一次要吐 8192 max_tokens，创作思考强度开到深度时两三分钟很常见；用对话那档 120 秒，
    // 用户等到的是一句**光秃秃的 "timeout"**（OkHttp 的裸异常消息），既分不清是慢还是错、也不知道改什么。
    // 这条验两件事：① **超时长度真的接到了那次请求上** —— 自检传 1 秒、服务端睡 3 秒，
    // 参数没接上的话请求会正常返回，这条即红（变异测试：把 generate 里的转发删掉就会红）；
    // ② 文案是给人看的（含"请求超时"与"换更快的型号"），而不是 "timeout" 三个字母；
    // ③ 常量闸门：创作档必须比对话档长（写回 120 就等于这条反馈没修）。
    // ⚠ 能覆盖到的是"参数转发 + 文案 + 常量"，**不是"默认 300 秒真的等满 5 分钟"**（那样的自检跑不动）。
    check("创作·超时（长度接到请求上；文案可读，不是裸 timeout）") {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { ex ->
            ex.requestBody.readBytes()
            Thread.sleep(3000) // 比下面传进去的 1 秒长 ⇒ 必然走到超时那条路
            // 超时后 OkHttp 已关连接，这里写可能失败——自检不关心，吃掉免得刷一屏栈
            runCatching {
                val b = "{}".toByteArray()
                ex.sendResponseHeaders(200, b.size.toLong())
                ex.responseBody.use { it.write(b) }
            }
        }
        server.start()
        val prevInsecure = AiClient.allowInsecureLoopback
        AiClient.allowInsecureLoopback = true
        val settings = AiSettings(
            chatBaseUrl = "http://127.0.0.1:${server.address.port}", chatApiKey = "k", chatModel = "m"
        )
        val err = runCatching {
            runBlocking {
                com.mysticat.roleplay.data.CharacterGenerator.generate(
                    settings, "二十问猜人", emptyList(), "play", readTimeoutSeconds = 1
                )
            }
        }.exceptionOrNull()
        AiClient.allowInsecureLoopback = prevInsecure
        server.stop(0)
        val msg = err?.message.orEmpty()
        AiClient.CREATION_READ_TIMEOUT_SECONDS > AiClient.DEFAULT_READ_TIMEOUT_SECONDS &&
            AiClient.CREATION_READ_TIMEOUT_SECONDS >= 300 &&
            msg.contains("请求超时") && msg.contains("更快的型号") &&
            !msg.equals("timeout", ignoreCase = true)
    }
    // 玩法形态（E4 拆档，2026-09-23）：与「工具」并列的第四档。按"用户会失去什么"写：
    // ① 徽标 / 筛选取值不对 ⇒ 列表里认不出哪张是玩法；
    // ② 装配仍走工具或角色扮演那两套 ⇒ 用户拿到的不是"一回合"（要么是成品、要么是剧情续写）；
    // ③ 温度被接管成 0.3 ⇒ 猜谜推理退化成只报最显著的答案（二十问"逐个报人名"的第一号根因，报告 §四）；
    // ④ 开场引导不铺或铺成场景旁白 ⇒ 一局没有开局第一步 / 玩法设定被讲成剧情。
    check("玩法形态（徽标与筛选取值 / 走玩法契约 / 开场按卡铺 / 温度不接管）") {
        // ① 形态取值、徽标与筛选项：第四档在册，值域仍由引擎注册表说了算。
        //    **顺序**也是口径的一部分（第 78 轮用户要"玩法排在工具前面"）：两处列表对不上，
        //    就会一个地方先玩法、另一个地方先工具。
        val tags = CharacterFormTags.map { it.second }
        val badgeOk = CharacterCard(id = "p0", name = "x", formTag = "play").formTagBadge() == "玩法" &&
            CharacterCard(id = "p1", name = "x", formTag = "play").formTagLabel() == "玩法" &&
            tags == listOf("", "experience", "play", "tool") &&
            FormFilter.options.map { it.second } == listOf("全部", "陪伴", "多线", "玩法", "工具")

        // ② 装配契约：玩法卡拿到的是一局玩法的契约，工具与角色扮演两套都不许混进来
        val settings = AiSettings(chatBaseUrl = "https://example.com/v1", chatApiKey = "k", chatModel = "m")
        val playCard = CharacterCard(
            id = "smoke-play",
            name = "二十问猜人",
            formTag = "play",
            playRules = "只回答「是」或「不是」，不可报出具体名字。",
            playState = "已排除的候选、当前第几问。",
            playOptions = 3,
            scenario = "这一局的前提：你想一个人物，我来猜。",
            greeting = "我想好了一个人，你开始提问吧。",
            description = "轻松、爱吐槽",
            // 这三样在玩法形态下**不该**被注入：任务说明与对话示例没有位置，世界观走【玩法设定】
            taskBrief = "不该出现的任务说明",
            outputFormat = "不该出现的输出格式",
            mesExample = "不该出现的对话示例"
        )
        val playPrompt = AiClient.buildSystemPrompt(
            playCard, settings, memory = "已排除：蒋介石、孙中山", mode = Engines.of(playCard).promptMode
        )
        val promptOk = playPrompt.contains("【玩法契约（必须遵守）】") &&
            playPrompt.contains("【玩法规则】") && playPrompt.contains("只回答「是」或「不是」") &&
            playPrompt.contains("【本局要记的状态】") && playPrompt.contains("已排除的候选") &&
            // 选项数按卡走；用户回一个数字就能继续
            playPrompt.contains("3 个编号选项") &&
            // 世界观字段在玩法下是【玩法设定】；段名换成进度向的【当前进度】
            playPrompt.contains("【玩法设定】") && playPrompt.contains("你想一个人物，我来猜") &&
            playPrompt.contains("【当前进度】") && !playPrompt.contains("【共同记忆】") &&
            playPrompt.contains("【风格参考】") && playPrompt.contains("轻松、爱吐槽") &&
            // 工具契约、角色扮演的视角约定与三块角色卡内容一个都不许出现
            !playPrompt.contains("任务处理工具") && !playPrompt.contains("【任务说明】") &&
            !playPrompt.contains("不该出现的任务说明") && !playPrompt.contains("不该出现的输出格式") &&
            !playPrompt.contains("不该出现的对话示例") && !playPrompt.contains("【角色设定】") &&
            !playPrompt.contains("【视角约定（必须遵守）】") && !playPrompt.contains("【世界观 / 当前场景】")

        // ③ 开场按卡铺（用户 2026-09-23 拍板："允许，但不是任何情况、视角色卡而定"）：
        //    有开局引导就写进新会话（且**不带**场景气泡），卡里没写就什么都不铺，直接等用户开始
        val seeded = AiClient.seedOpening(playCard, Engines.of(playCard))
        val barePlay = AiClient.seedOpening(
            CharacterCard(id = "p2", name = "没写开局引导的玩法卡", formTag = "play"), Engines.of("play")
        )
        val toolCard = CharacterCard(id = "p3", name = "工具卡", formTag = "tool", greeting = "不该被铺的示例")
        val seedOk = seeded.size == 1 && seeded[0].role == "assistant" &&
            seeded[0].content == "我想好了一个人，你开始提问吧。" &&
            barePlay.isEmpty() && AiClient.seedOpening(toolCard, Engines.of("tool")).isEmpty()

        // ④ 每轮纪律与温度：都只在**报文里**看得见（`effectiveTemperature` 是私有的，传参传错一样全绿）。
        //    起本地假服务端，把玩法会话的那一次请求抓下来核对。
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/chat/completions") { ex ->
            bodies.add(ex.requestBody.readBytes().decodeToString())
            val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"你平常喝什么？\"}}]}\n\ndata: [DONE]\n\n"
            val b = sse.toByteArray()
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        val prevInsecure = AiClient.allowInsecureLoopback
        AiClient.allowInsecureLoopback = true
        val tail = AiClient.playTurnRequirement(playCard)
        runCatching {
            runBlocking {
                AiClient.chatStream(
                    settings = AiSettings(
                        chatBaseUrl = "http://127.0.0.1:${server.address.port}",
                        chatApiKey = "k", chatModel = "m"
                    ),
                    card = playCard,
                    history = listOf(ChatMessage("user", "1")),
                    // 与 ChatViewModel.tailHint 同一条路径（那里就是 append 这段）
                    tailHint = tail,
                    onDelta = {}
                )
            }
        }
        AiClient.allowInsecureLoopback = prevInsecure
        server.stop(0)
        val sent = bodies.firstOrNull().orEmpty()
        val wireOk = sent.contains("\"temperature\":0.85") && !sent.contains("\"temperature\":0.3") &&
            sent.contains("【玩法硬性要求（这一条必须满足）】") && sent.contains("3 个编号选项") &&
            // 叙事风格那份硬性要求（"本回合写满几百字"）**一条都不许**贴到玩法会话的尾巴上：
            // 它与玩法"一次一回合、要短"正好相反
            !sent.contains("【本条硬性要求】") &&
            // 系统提示词里也真的带着玩法规则（生成得对、发不出去 ⇒ 用户还是一次次看到它乱来）
            sent.contains("【玩法规则】") && sent.contains("只回答「是」或「不是」")

        badgeOk && promptOk && seedOk && wireOk
    }
    // 发现页清单（台账 12 第三批）：cover/thumb 是**可选**字段——清单是热更新的，用户手里那台 App
    // 很可能先看到旧版清单，解析不能因此失败；三类文件（卡/封面/缩略图）的路径校验也各验一遍。
    check("发现页清单解析（新版 cover/thumb 可选 + 老清单兼容 + 路径越界拒绝）") {
        val fresh = DiscoverCatalog.parseManifest(
            """{"version":2,"packs":[{"id":"p","title":"自检包","cover":"cover-p.jpg","cards":""" +
                """[{"id":"c","name":"自检卡","file":"card-c.png","thumb":"thumb-c.jpg"}]}]}"""
        )
        val old = DiscoverCatalog.parseManifest(
            """{"version":1,"packs":[{"id":"p","title":"老清单","cards":""" +
                """[{"id":"c","name":"自检卡","file":"card-c.png"}]}]}"""
        )
        val freshPack = fresh.packs.firstOrNull() ?: return@check false
        val freshEntry = freshPack.cards.firstOrNull() ?: return@check false
        val oldPack = old.packs.firstOrNull() ?: return@check false
        val oldEntry = oldPack.cards.firstOrNull() ?: return@check false
        freshPack.cover == "cover-p.jpg" && freshEntry.thumb == "thumb-c.jpg" &&
            // 老清单没有这两个键 → 空串 → UI 退回纯文字形态；卡文件照旧可用
            oldPack.cover == "" && oldEntry.thumb == "" &&
            DiscoverCatalog.coverUrls(freshPack).isNotEmpty() &&
            DiscoverCatalog.thumbUrls(freshEntry).isNotEmpty() &&
            DiscoverCatalog.cardUrls(oldEntry).isNotEmpty() &&
            // 封面/缩略图与卡文件走同一套候选展开（都是"镜像优先 + 原始兜底"）
            DiscoverCatalog.coverUrls(freshPack).size == DiscoverCatalog.cardUrls(oldEntry).size &&
            // 越界路径必须被拒：镜像只能换前缀，不能借清单把 App 指到别处
            listOf("http://evil.example/x.png", "../x.png", "/etc/x.png").all { bad ->
                runCatching { DiscoverCatalog.thumbUrls(oldEntry.copy(thumb = bad)) }.isFailure
            }
    }
    // 发现页「按类型」轴（第 80 轮）：kind / form 与 cover/thumb 一样是**可选**字段——清单热更新，
    // 老 App 会读到新清单、新 App 也会读到老清单；类型分组是纯函数，**空组必须照样返回**，
    // 因为故事/模板这两类现在就是空的（用户 2026-09-23 口径：可以暂时先不填充内容）。
    check("发现页类型轴（kind/form 可缺省 + 分组与形态徽标口径）") {
        val m = DiscoverCatalog.parseManifest(
            """{"version":3,"packs":[{"id":"theme","title":"主题包","cards":[""" +
                """{"id":"a","name":"多线角色","file":"card-a.png","kind":"角色","form":"experience"},""" +
                """{"id":"b","name":"故事","file":"card-b.png","kind":"故事"},""" +
                """{"id":"c","name":"玩法卡","file":"card-c.png","form":"play"},""" +
                """{"id":"d","name":"老条目","file":"card-d.png"}]},""" +
                """{"id":"theme2","title":"另一个主题","cards":[""" +
                """{"id":"c","name":"重复的玩法卡","file":"card-c.png"}]}]}"""
        )
        val entries = m.packs.firstOrNull()?.cards.orEmpty()
        val groups = DiscoverCatalog.packsByKind(m.packs)
        entries.size == 4 &&
            groups.map { it.id } == DiscoverCatalog.kinds.map { DiscoverCatalog.kindPackId(it) } &&
            groups.map { it.title } == listOf("角色精选", "故事精选", "模板精选") &&
            // 缺省 kind＝角色：老清单的卡一张都没写它，而那些内容确实全是角色卡
            entries.map { DiscoverCatalog.kindOf(it) } == listOf("角色", "故事", "角色", "角色") &&
            // 跨主题包重复的同一张卡只留一份（主题轴允许一张卡摆进多个主题）
            groups[0].cards.map { it.id } == listOf("a", "c", "d") &&
            groups[1].cards.map { it.id } == listOf("b") &&
            groups[2].cards.isEmpty() &&
            // 徽标：缺省（陪伴）与认不出的取值都返回 null——看不见比看错好
            entries.map { Engines.labelOrNull(it.form) } == listOf("多线", null, "玩法", null) &&
            Engines.labelOrNull("tool") == "工具" && Engines.labelOrNull("陪伴") == null
    }
    // PNG 角色卡（台账 11）：编解码是纯字节逻辑，最容易错在"chunk 插错位置"（IEND 之后=白带），
    // 这条自检把嵌→抽→导入的闭环常驻在冒烟里。
    check("PNG 角色卡编解码（tEXt chara 位置正确 / 旧卡替换 / JSON 往返一致）") {
        val w = 8; val h = 8
        val raw = ByteArray(h * (1 + w * 3))
        fun png(vararg chunks: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            chunks.forEach { out.write(it) }
            return out.toByteArray()
        }
        fun crcChunk(type: String, data: ByteArray): ByteArray {
            val c = java.util.zip.CRC32().apply { update(type.toByteArray()); update(data) }
            val out = java.io.ByteArrayOutputStream()
            fun u32(v: Int) { repeat(4) { out.write((v ushr (24 - 8 * it)) and 0xFF) } }
            u32(data.size); out.write(type.toByteArray()); out.write(data); u32(c.value.toInt())
            return out.toByteArray()
        }
        val pngBytes = png(
            crcChunk("IHDR", java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h)
                .put(byteArrayOf(8, 2, 0, 0, 0)).array()),
            crcChunk("IDAT", java.util.zip.Deflater().let { d ->
                val o = java.io.ByteArrayOutputStream(); val buf = ByteArray(64)
                while (!d.finished()) { val n = d.deflate(buf); if (n == 0) break; o.write(buf, 0, n) }; o.toByteArray()
            }),
            crcChunk("IEND", ByteArray(0))
        )
        val json = """{"spec":"chara_card_v2","data":{"name":"自检PNG卡"}}"""
        val embedded = PngCardCodec.embed(pngBytes, json) ?: return@check false
        // 抽回来的 JSON 与原文一致，且 chunk 在 IEND 之前（不是尾巴上的死数据）
        val extracted = PngCardCodec.extractCardJson(embedded) ?: return@check false
        val iend = embedded.size - 12 // 末尾就是 IEND chunk（12 字节）
        val before = PngCardCodec.extractCardJson(embedded.copyOfRange(0, iend)) != null
        // 再嵌一次：旧 chara 被替换而不是叠加，且再抽内容是新的
        val re = PngCardCodec.embed(embedded, json) ?: return@check false
        val reJson = PngCardCodec.extractCardJson(re) ?: return@check false
        extracted == json && before && reJson == json &&
            PngCardCodec.extractCardJson(pngBytes) == null
    }
    // 酒馆系（SillyTavern）真卡形状（第 69 轮）：字段清单照抄 SillyTavern 自带卡实测结果
    // （2026-09-22 下载 `default/content/default_Seraphina.png` 实测：chara 与 ccv3 两个 chunk、
    // 16 个 data 字段、带 character_book 与 extensions{talkativeness,fav,world,depth_prompt}）。
    // 正文换成本地短句——**测的是形状不是内容**，所以不必把别人的卡正文抄进仓库。
    check("酒馆系卡导入（v3 规范 / 只有 ccv3 chunk 的 PNG / character_book / 外来扩展不丢）") {
        val v3 = """{"spec":"chara_card_v3","spec_version":"3.0","data":{"name":"真卡形状",""" +
            """"description":"开场在雨里。","personality":"寡言","scenario":"雨夜车站",""" +
            """"first_mes":"你好。","mes_example":"<START>","creator_notes":"备注",""" +
            """"system_prompt":"S","post_history_instructions":"P","tags":["亚文化","日常"],""" +
            """"creator":"someone","character_version":"1.2","alternate_greetings":["第二个开场"],""" +
            """"nickname":"小真","group_only_greetings":[],""" +
            """"extensions":{"talkativeness":"0.5","fav":true,"world":"世界观A",""" +
            """"depth_prompt":{"depth":4,"prompt":"x"}},""" +
            """"character_book":{"name":"世界观A","entries":[{"keys":["雨"],"content":"下雨了","insertion_order":10,""" +
            // 条目故意做成"真卡的形状"：既有 spec 字段，也有 ST 的 extensions 镜像（含一堆我们**不实现**
            // 的键）——那些键必须逐字保住，否则用户的卡被我们导一次就永远丢了设置
            """"use_regex":true,"extensions":{"position":0,"depth":4,"probability":100,"sticky":0,""" +
            """"match_whole_words":null,"case_sensitive":null,"automation_id":""}}]}}}"""
        // 只有 `ccv3` chunk 的 PNG（v3 规范的关键字，卡站有人这么发）：`chara` 缺失也要能导
        fun u32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        fun chunk(type: String, data: ByteArray): ByteArray {
            val crc = java.util.zip.CRC32().apply { update(type.toByteArray()); update(data) }
            return u32(data.size) + type.toByteArray() + data + u32(crc.value.toInt())
        }
        val ihdr = java.nio.ByteBuffer.allocate(13).putInt(8).putInt(8).put(byteArrayOf(8, 2, 0, 0, 0)).array()
        val ccv3B64 = java.util.Base64.getEncoder().encodeToString(v3.toByteArray(Charsets.UTF_8))
        val pngV3 = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) +
            chunk("IHDR", ihdr) + chunk("IDAT", ByteArray(4)) +
            chunk("tEXt", ("ccv3\u0000$ccv3B64").toByteArray(Charsets.ISO_8859_1)) + chunk("IEND", ByteArray(0))

        val direct = CardImport.fromTavernJson(v3)
        val viaPng = CardImport.cardJsonFromBytes(pngV3)?.let { CardImport.fromTavernJson(it) }
        val back = viaPng ?: return@check false
        direct.name == "真卡形状" && back.name == "真卡形状" &&
            // v2/v3 分层字段各自独立落库（E1）：persona **不许**被拼出来，否则装配口径又退回整块注入
            back.persona.isBlank() && back.description == "开场在雨里。" &&
            back.personality == "寡言" && back.scenario == "雨夜车站" && back.mesExample == "<START>" &&
            back.creatorNotes == "备注" && back.systemPrompt == "S" && back.postHistory == "P" &&
            back.creator == "someone" && back.characterVersion == "1.2" &&
            back.greetings == listOf("你好。", "第二个开场") && back.categories == listOf("亚文化", "日常") &&
            // 立绘当头像：PNG 里没有 avatar 字段也要把整张图补上（老口径下这种卡导入后没有头像）
            back.avatarUri != null && File(back.avatarUri!!).isFile &&
            // 别人的 extensions 原样留一份（talkativeness / depth_prompt 是人家的数据），
            // 且导出时写回去——"导入再导出"不该清空别人的扩展
            "talkativeness" in back.extensionsRaw && "depth_prompt" in back.extensionsRaw &&
            "whale" !in back.extensionsRaw &&
            "depth_prompt" in CardImport.toTavernJson(back) &&
            // character_book（世界书，E3）：**吃进来**（进 worldBook），且绝不能被误当成人设正文
            // 塞进 description —— 书里的正文是作者写给模型的问答体资料，混进人设会改掉角色口吻
            back.worldBook?.name == "世界观A" &&
            back.worldBook?.entries?.size == 1 &&
            back.worldBook?.entries?.first()?.keys == listOf("雨") &&
            back.worldBook?.entries?.first()?.content == "下雨了" &&
            back.worldBook?.entries?.first()?.insertionOrder == 10 &&
            // ST 的 extensions 镜像：我们认识的键读进模型字段，不认识的（probability / sticky /
            // automation_id）逐字留住并在导出时写回 —— 少一个，用户的卡就被我们改了一遍
            back.worldBook?.entries?.first()?.depth == 4 &&
            back.worldBook?.entries?.first()?.useRegex == true &&
            // JSON null 是"没设过"，不许被解析成 false 再写回成 false（那等于我们替用户改了设置）
            back.worldBook?.entries?.first()?.matchWholeWords == null &&
            "probability" in CardImport.toTavernJson(back) &&
            "sticky" in CardImport.toTavernJson(back) &&
            "下雨了" !in back.description && "下雨了" !in back.scenario &&
            // 导出去要写回标准位置 `data.character_book`（别的应用认的就是这里），
            // 且"导入→导出→再导入"两份世界书完全一致（往返稳定）
            "character_book" in CardImport.toTavernJson(back) &&
            CardImport.fromTavernJson(CardImport.toTavernJson(back)).worldBook == back.worldBook
    }
    // 世界书（E3）引擎：**按"用户会看到什么"写断言**——
    // ① 该触发的没触发 = 模型不知道世界观（用户："我明明写了它却说不知道"）；
    // ② 不该触发的触发了 = 每轮白付这段 token（BYOK 用户按字数付费）；
    // ③ 老卡提示词变了 = 纯回归。
    check("世界书引擎（关键词 / 常驻 / 副关键词为空 / 中文子串 / 位置与深度 / 老卡零影响）") {
        val st = AiSettings(chatBaseUrl = "https://example.com/v1", chatApiKey = "k", chatModel = "m")
        val book = WorldBook(
            name = "测试书",
            entries = listOf(
                // 关键词触发、子串匹配（中文没有词边界，必须按子串）
                WorldBookEntry(id = 0, keys = listOf("王城"), content = "王城在河对岸。"),
                // 常驻：不看关键词，每轮都注入
                WorldBookEntry(id = 1, constant = true, content = "这世界有两轮月亮。"),
                // `selective: true` 但副关键词为空 —— 真卡的形状，**必须仍能触发**
                WorldBookEntry(id = 2, keys = listOf("Eldoria"), secondaryKeys = emptyList(),
                    selective = true, content = "Eldoria 是一片魔法森林。"),
                // 副关键词非空：只命中主关键词不够，还得命中副关键词之一
                WorldBookEntry(id = 3, keys = listOf("森林"), secondaryKeys = listOf("夜晚"),
                    selective = true, content = "夜里的森林会发光。"),
                // 大小写默认不敏感（ST 默认亦如此）
                WorldBookEntry(id = 4, keys = listOf("Moonlight"), content = "月光有名字。"),
                // 关掉的条目不参与
                WorldBookEntry(id = 5, keys = listOf("王城"), content = "【不该出现】", enabled = false),
                // 位置：角色设定之后
                WorldBookEntry(id = 6, keys = listOf("王城"), content = "王城建了三百年。",
                    position = WorldBookEntry.POSITION_AFTER_CHAR),
                // 按深度插进消息数组
                WorldBookEntry(id = 7, keys = listOf("王城"), content = "王城的城门是铜的。",
                    position = WorldBookEntry.POSITION_AT_DEPTH, depth = 4)
            )
        )
        // 卡自己的设定里就提过 Eldoria ⇒ 只扫聊天的话它永远触发不了（这条最容易漏）
        val card = CharacterCard(
            id = "wb", name = "测试", description = "守林人阿石。", personality = "寡言。",
            scenario = "你在 Eldoria 的森林里醒来。", mesExample = "{{char}}: 嗯。",
            worldBook = book
        )
        // 场景一：最近消息提到王城
        val h1 = WorldBookEngine.hits(book, WorldBookEngine.cardScanText(card), listOf("我们去王城吧"))
        val h1keys = h1.map { it.entry.id }.toSet()
        val hitOk = h1keys == setOf(0, 1, 2, 6, 7) && 5 !in h1keys &&
            // 副关键词没出现 ⇒ 森林那条不该注入；Eldoria 那条（selective 但副关键词为空）该注入
            3 !in h1keys &&
            // 大小写不敏感：卡里写的是 Eldoria、条目关键词也是 Eldoria 就不算这条的例证，
            // 所以另起一句小写消息来验
            WorldBookEngine.hits(book, "", listOf("moonlight everywhere")).any { it.entry.id == 4 } &&
            // 命中理由要能说清是哪个词触发的（记账本 / 命中面板读它）
            h1.first { it.entry.id == 0 }.matchedKey == "王城" && h1.first { it.entry.id == 1 }.matchedKey == ""

        // 场景二：副关键词出现 ⇒ 森林那条一起注入
        val h2 = WorldBookEngine.hits(book, "", listOf("夜晚的森林")).map { it.entry.id }.toSet()
        val selectiveOk = 3 in h2

        // 场景三：扫描深度按"最近 N 条"算 —— 王城只在很旧的消息里时，默认深度 4 看不到它
        val old = listOf("我们去王城吧") + List(6) { "闲聊" }
        val depthOk = WorldBookEngine.hits(book, "", old).none { it.entry.id == 0 }

        // 场景四：落点分区 + 深度插入下标
        val parts = WorldBookEngine.partition(h1)
        val partOk = parts.before.map { it.id }.toSet() == setOf(0, 1, 2) &&
            parts.after.map { it.id }.toSet() == setOf(6) &&
            parts.depth.keys == setOf(4) && parts.depth[4]?.map { it.id } == listOf(7) &&
            // depth=4、共 3 条消息 ⇒ 插在下标 max(1, 3-4)=1（紧跟系统提示词，不许插到系统提示词之前）
            WorldBookEngine.depthInsertIndex(3, 4) == 1 &&
            WorldBookEngine.depthInsertIndex(10, 4) == 6 &&
            WorldBookEngine.depthInsertIndex(10, 0) == 10

        // 场景五：装配。命中段要落在该在的位置上 —— before_char 在【角色设定】之前、
        // after_char 在【对话示例】之后（ST 的两个锚点）
        val p = AiClient.buildSystemPrompt(card, st, worldBookHits = h1)
        val iBefore = p.indexOf("【${WorldBookEngine.SECTION}】")
        val iChar = p.indexOf("【角色设定】")
        val iEx = p.indexOf("【对话示例】")
        val iAfterWord = p.indexOf("王城建了三百年。")
        val orderOk = iBefore in 1 until iChar && iBefore < iChar && iAfterWord > iEx &&
            p.contains("王城在河对岸。") && p.contains("这世界有两轮月亮。") &&
            p.contains("Eldoria 是一片魔法森林。") &&
            // @depth 那条**不在系统提示词里**（它进消息数组），否则就白设了位置
            !p.contains("王城的城门是铜的。")

        // 场景六：**老卡零影响**（纯回归）。没有世界书、以及有书但一条都没命中，
        // 两种情况下提示词必须与"改动前"逐字节一致。
        val plain = CharacterCard(id = "plain", name = "普通卡", description = "一个角色。")
        val base = AiClient.buildSystemPrompt(plain, st)
        val noHit = AiClient.buildSystemPrompt(plain.copy(worldBook = book), st, worldBookHits = emptyList())
        val zeroImpact = base == noHit && !base.contains(WorldBookEngine.SECTION) &&
            !noHit.contains("王城在河对岸。") && !noHit.contains(WorldBookEngine.SECTION)

        // 场景七：书里有"没有关键词又非常驻"的死条目时，要说出来（作者写错了不该静默）
        val dead = WorldBookEngine.describe(WorldBook(entries = listOf(WorldBookEntry(content = "孤儿条目"))))
        val described = "永远不会触发" in dead

        hitOk && selectiveOk && depthOk && partOk && orderOk && zeroImpact && described
    }

    // 世界书·独立 JSON（E3 下半，第 99 轮）：编辑器「导入世界书」吃三种文件，导出的能原样导回来。
    // 断言按**用户会看到什么**写：从卡里导入有没有拿到那本书、老版文件的字段有没有读成模型值、
    // 一条被"停用"的条目还会不会注入 —— 而不是"函数被调用过"。
    check("世界书独立 JSON（整卡 / 平铺卡 / 书本身 / 老版 World Info 形状 / 往返）") {
        val book = WorldBook(
            name = "Eldoria",
            entries = listOf(
                WorldBookEntry(id = 0, keys = listOf("王城"), content = "王城在河对岸。"),
                WorldBookEntry(
                    id = 1, keys = listOf("森林"), content = "森林里有雾。",
                    position = WorldBookEntry.POSITION_AT_DEPTH, depth = 3
                )
            )
        )
        // ① 整张 v2 卡（书在 data.character_book）
        val fromCard = WorldBookEngine.fromStandaloneJson(
            CardImport.toTavernJson(
                CharacterCard(id = "wb-card", name = "带书的卡", description = "一个角色。", worldBook = book)
            )
        )
        // ② 书本身（我们导出的形态）→ 再导回来
        val standalone = WorldBookEngine.toStandaloneJson(book)
        val fromBook = WorldBookEngine.fromStandaloneJson(standalone)
        // ⚠ "按深度插入"必须活过往返：规范那个字段只有 before/after，只有镜像装得下它
        val depthSurvives = fromBook?.entries?.get(1)?.position == WorldBookEntry.POSITION_AT_DEPTH &&
            fromBook?.entries?.get(1)?.depth == 3
        // ③ 老版 World Info 导出形状：entries 是 `uid → 条目` 对象，键名是驼峰（SillyTavern 面板导出的就是它）
        val legacy = """
            {"entries":{
              "0":{"uid":0,"key":["王城"],"keysecondary":[],"comment":"王城",
                   "content":"王城在河对岸。","disable":false,"order":100,"position":0,"depth":4},
              "1":{"uid":1,"key":"森林","comment":"森林","content":"森林里有雾。","disable":true}}}
        """.trimIndent()
        val fromLegacy = WorldBookEngine.fromStandaloneJson(legacy)
        // 停用那条不注入：给它那个词也没用（"disable 读成了 true"的唯一可观察后果）
        val legacyHits = WorldBookEngine.hits(fromLegacy, "", listOf("王城与森林")).map { it.entry.keys.first() }
        // ④ 不是书的东西不该被当成本书：否则"导入世界书"会把卡名当书名、把用户的书记成空的
        val notBook = WorldBookEngine.fromStandaloneJson(
            CardImport.toTavernJson(CharacterCard(id = "wb-plain", name = "没有书的卡"))
        )
        // ⑤ 平铺卡（顶层直接有 character_book）
        val fromFlat = WorldBookEngine.fromStandaloneJson("""{"name":"某卡","character_book":$standalone}""")

        (fromCard?.entries?.size == 2) && (fromCard?.name == "Eldoria") &&
            (fromBook?.entries?.size == 2) && depthSurvives &&
            (fromBook?.entries?.map { it.keys } == book.entries.map { it.keys }) &&
            (fromLegacy?.entries?.size == 2) &&
            (fromLegacy?.entries?.get(0)?.keys == listOf("王城")) &&
            // `key` 写成单个字符串也认（手写与老格式里出现过这种形状）
            (fromLegacy?.entries?.get(1)?.keys == listOf("森林")) &&
            (fromLegacy?.entries?.get(1)?.enabled == false) &&
            (legacyHits == listOf("王城")) &&
            (fromFlat?.entries?.size == 2) &&
            (notBook == null)
    }

    // 世界书·未命中诊断与文本批量生成（第 110 轮）：断言按**用户会看到什么**写——
    // 写错的正则要点名、大小写/全词挡住的要给出解法、近似键要被指认（台账 64 的"艾瑟兰/艾瑟林"真例）；
    // 拆文本要拿到标题当条目名，正文为空的段不生成。
    check("世界书诊断与批量生成（正则错/大小写/全词/副关键词/近似键；三种分段拆文本）") {
        fun reasonsOf(
            key: String,
            text: String,
            useRegex: Boolean = false,
            caseSensitive: Boolean = false,
            wholeWords: Boolean? = null,
            selective: Boolean = false,
            secondary: List<String> = emptyList(),
        ): List<String> {
            val e = WorldBookEntry(
                id = 0, keys = listOf(key), content = "x", useRegex = useRegex,
                caseSensitive = caseSensitive, matchWholeWords = wholeWords,
                selective = selective, secondaryKeys = secondary
            )
            return WorldBookEngine.diagnoseMisses(WorldBook(entries = listOf(e)), "", listOf(text)).first().reasons
        }
        // ① 正则写错：要点名"正则写错了"，而不是沉默地不命中
        val regexOk = reasonsOf("王城[", "我们去了王城", useRegex = true).any { it.contains("正则") }
        // ② 大小写挡住：要指出关掉「区分大小写」就能命中
        val caseOk = reasonsOf("Moonlight", "moonlight everywhere", caseSensitive = true).any { it.contains("大小写") }
        // ③ 全词匹配挡住：cat 在 scatter 里明明有，却被词边界拦下
        val wholeOk = reasonsOf("cat", "scatter the seeds", wholeWords = true).any { it.contains("全词") }
        // ④ 副关键词没凑齐：主词出现、副词缺席（反过来也该说清）
        val selectiveOk = reasonsOf("森林", "森林里有雾", selective = true, secondary = listOf("夜晚"))
            .any { it.contains("副关键词") } &&
            reasonsOf("森林", "夜晚的雾", selective = true, secondary = listOf("夜晚"))
                .any { it.contains("主关键词") }
        // ⑤ 近似键：字面差一个字的变体要被指认出来
        val nearOk = reasonsOf("艾瑟兰", "艾瑟林高举长枪。").any { it.contains("近似") && it.contains("艾瑟林") }
        // ⑥ 词压根没出现的未命中＝没毛病，不该瞎诊断
        val silentOk = reasonsOf("王城", "今天天气不错").isEmpty()

        // ⑦ 拆文本：三种分段都要认出标题、标题能当关键词；纯标题行（没有正文）不生成
        val sample = "【王城】大河对岸的都城。\n\n艾瑟兰王国：\n大陆中央的古老国度。\n杂项一行"
        val byHeading = WorldBookEngine.entriesFromText(sample, WorldBookEngine.WorldBookSplitMode.HEADING, true, 0)
        val byPara = WorldBookEngine.entriesFromText(sample, WorldBookEngine.WorldBookSplitMode.PARAGRAPH, true, 0)
        val byLine = WorldBookEngine.entriesFromText(sample, WorldBookEngine.WorldBookSplitMode.LINE, true, 0)
        val genOk = byHeading.size == 2 && byHeading[0].name == "王城" && byHeading[0].keys == listOf("王城") &&
            byHeading[1].name == "艾瑟兰王国" && byHeading[1].content.contains("古老国度") &&
            byPara.size == 2 && byPara[0].name == "王城" &&
            byLine.size == 3 && byLine[2].name == "" && byLine[2].content == "杂项一行" &&
            // 标题不当关键词时 keys 为空
            WorldBookEngine.entriesFromText(sample, WorldBookEngine.WorldBookSplitMode.HEADING, false, 0)
                .first().keys.isEmpty()

        regexOk && caseOk && wholeOk && selectiveOk && nearOk && silentOk && genOk
    }

    // 真卡实测（发版/排查时用）：`WHALE_SMOKE_CARD=<卡文件路径> :desktopApp:run --args="--smoke"`
    // 设了就把那张真卡走一遍导入，并把"什么进来了、什么没进来"打在 stdout 上。
    // 不设则显式报 SKIP：它是**语境相关**的自检（依赖外部文件），不能混在"全绿"里。
    val realCard = System.getenv("WHALE_SMOKE_CARD")?.takeIf { it.isNotBlank() }?.let(::File)
    if (realCard == null || !realCard.isFile) {
        skip(
            "真酒馆卡导入实测（外部卡文件）",
            realCard?.let { "找不到 ${it.absolutePath}" } ?: "未设 WHALE_SMOKE_CARD（给一张真卡即可实测导入保真）"
        )
    } else {
        check("真酒馆卡导入（${realCard.name}）") {
            val json = CardImport.cardJsonFromBytes(realCard.readBytes()) ?: return@check false
            val c = CardImport.fromTavernJson(json)
            // 卡文件里有什么、我们吃进了什么、什么被丢了：**逐项写清楚**。
            // 落一份 UTF-8 报告到卡文件旁边：Gradle 转发 stdout 时会把中文糊掉（本机实测），
            // 报告文件是排查"这张卡为什么看着不对"时唯一读得清的东西。
            // 这里不引 kotlinx.serialization（它只是 shared 的实现依赖，桌面模块看不到），
            // 所以世界书走 `WorldBookEngine.describe` 读解析结果，而不是自己再 parse 一遍 JSON。
            val hasBook = "\"character_book\"" in json
            val spec = Regex("\"spec\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1) ?: "（无 spec）"
            val bookNote = when {
                !hasBook -> "卡里没有这个节点"
                else -> WorldBookEngine.describe(c.worldBook)
            }
            val report = buildString {
                appendLine("真酒馆卡导入报告 · ${realCard.name}（${realCard.length()} 字节）")
                appendLine("卡文件 spec = $spec")
                appendLine("── 吃进来的 ──")
                appendLine("name = ${c.name}")
                appendLine("人物设定 description = ${c.description.length} 字（合并块 persona = ${c.persona.length} 字）")
                appendLine("性格 personality = ${c.personality.length} 字；场景 scenario = ${c.scenario.length} 字")
                appendLine("对话示例 mes_example = ${c.mesExample.length} 字")
                appendLine("开场白 ${c.greetings.size} 条（首条 ${c.greeting.length} 字）")
                appendLine("作者 creator=${c.creator}；版本 character_version=${c.characterVersion}；备注 ${c.creatorNotes.length} 字")
                appendLine("分类 = ${c.categories}")
                appendLine("原卡 system_prompt = ${c.systemPrompt.length} 字（只保存不生效）；post_history = ${c.postHistory.length} 字")
                appendLine("头像 = ${if (c.avatarUri != null) "已从 PNG 立绘落盘" else "无"}")
                appendLine("别人的 extensions 保留 = ${c.extensionsRaw.length} 字节")
                // 世界书（E3）：卡里有什么、我们吃成了什么形状。逐条列出来才看得出"位置/深度对不对"。
                appendLine("世界书（character_book）：$bookNote")
                c.worldBook?.entries?.take(8)?.forEachIndexed { i, e ->
                    appendLine(
                        "  [$i] 关键词=${e.keys}" +
                            (if (e.secondaryKeys.isNotEmpty()) " 副关键词=${e.secondaryKeys}" else "") +
                            " 位置=" + when (e.position) {
                                WorldBookEntry.POSITION_AT_DEPTH -> "按深度 ${e.depth}"
                                WorldBookEntry.POSITION_BEFORE_CHAR -> "角色设定前"
                                else -> "角色设定后"
                            } +
                            " 常驻=${e.constant} 正文 ${e.content.length} 字"
                    )
                }
                appendLine("── 没吃进来的（本轮已知缺口）──")
                appendLine("probability 随机命中 / group 互斥分组 / 递归扫描：字段逐字保真，但本轮不生效")
                appendLine("group_only_greetings / nickname / assets 一类 v3 字段：不参与对话，本轮不落库")
            }
            File(realCard.parentFile, "${realCard.name}.import-report.txt")
                .writeText(report, Charsets.UTF_8)
            println(report)
            c.name.isNotBlank() && c.description.isNotBlank() &&
                (c.greeting.isNotBlank() || c.greetings.isNotEmpty()) &&
                // PNG 卡必须补上头像；v2 分层字段不该被揉成合并块
                c.avatarUri != null && c.persona.isBlank() &&
                // 卡里有 character_book 就必须吃进来（"有节点却解析成空"是最难发现的静默丢失：
                // 用户看到的是一张正常导入的卡，只是世界观永远不生效）
                (!hasBook || c.worldBook != null)
        }
    }
    check("字体校验（系统字体 msyh.ttc）") {
        val f = File("C:/Windows/Fonts/msyh.ttc")
        !f.isFile || Platform.ui.isUsableFontFile(f.absolutePath)
    }
    check("自定义字体加载（CMP Font 构造）") {
        val f = File("C:/Windows/Fonts/msyh.ttc")
        !f.isFile || Platform.ui.loadCustomFontFamily(f.absolutePath) != null
    }
    // 「衬线」在桌面必须挑到一个真实的中文字体文件（FontFamily.Serif 通用族解析不到中文衬线体，
    // 用户 2026-09-17 反馈过）。挑不到不算失败——那是"本机没装中文衬线体"的环境事实，会回退通用族。
    check("衬线字族可解析（中文衬线，挑不到则回退通用族）") {
        Platform.ui.serifFontFamily() != null || !File("C:/Windows/Fonts/simsun.ttc").isFile
    }
    // 「系统默认」档在桌面显式挑微软雅黑（方案①，用户反馈 #9）。本机实测与 FontFamily.Default
    // 逐像素一致（fallback 本来就是雅黑），所以这项查的是"挑得到"，不是"更好看"。
    check("系统默认字族可解析（微软雅黑，挑不到则回退通用族）") {
        Platform.ui.sansFontFamily() != null || !File("C:/Windows/Fonts/msyh.ttc").isFile
    }
    check("图片 PNG 编码 → 解码往返") {
        val bitmap = ImageBitmap(8, 8)
        val png = runBlocking { Platform.ui.encodePng(bitmap) } ?: return@check false
        if (png.size < 8) return@check false
        val tmp = File.createTempFile("whale-smoke", ".png")
        tmp.writeBytes(png)
        val decoded = runBlocking { Platform.ui.decodeImageBitmap(tmp.absolutePath, 64) }
        tmp.delete()
        decoded != null && decoded.width == 8
    }
    check("提示浮层计数（toast 不抛异常）") {
        Platform.ui.toast("自检提示")
        true
    }
    // API Key 加密链路：桌面＝DPAPI 密文（enc1: 前缀）；取不到 DPAPI 时必须是"明文回退"而不是乱码
    check("API Key 加密链路（${if (Security.keyProvider != null) "DPAPI" else "明文回退"}）") {
        // 假值刻意不写成 sk- 开头：密钥扫描脚本会把它当疑似真 Key 报出来（第 22 轮实测）
        val plain = "whale-smoke-value-not-a-key"
        val enc = Security.encrypt(plain)
        val back = Security.decrypt(enc)
        if (Security.keyProvider != null) {
            enc.startsWith("enc1:") && back == plain
        } else {
            enc == plain && back == plain
        }
    }
    // 图片卡：纯排版出一张 1080×1920 PNG（不带底图，走纯色底分支），验证渲染链路真的通
    check("图片卡渲染（1080×1920 PNG）") {
        val png = renderCardImagePng(
            CharacterCard(
                id = "smoke",
                name = "自检角色",
                tagline = "一行简介",
                persona = "人设正文（自检用）：".repeat(12),
                greeting = "开场白正文（自检用）：".repeat(8)
            )
        ) ?: return@check false
        val header = png.size > 24 && png[0] == 0x89.toByte() && png[1] == 'P'.code.toByte()
        val dims = pngHeaderSize(png)
        header && dims == (1080 to 1920)
    }
    check("语音链路已装配（引擎 / 播放器 / 录音器 / 识别器）") {
        Voice.ttsEngineFactory() != null &&
            Voice.recorderFactory() != null &&
            Voice.audioPlayerFactory() != null &&
            Voice.recognizer != null
    }
    // ── 音频播放（2026-09-17 #6 补：这条链路此前**一项自检都没有**，所以"配好供应商后朗读不发声"
    //    静默了一整轮）。分两段查：① PCM（系统朗读那条 WAV 路）能不能真起播；
    //    ② 供应商 MP3 能不能解码成 PCM（mp3spi 那一步，真根因就在这里）。
    check("音频播放（PCM WAV → Clip 起播）") {
        val wav = File.createTempFile("whale-smoke", ".wav")
        wav.writeBytes(silentWav(24000, 1, 200))
        val player = DesktopAudioPlayer()
        val started = player.start(wav) {}
        player.stopAndRelease()
        wav.delete()
        started
    }
    // ── 背景音乐（第 59 轮，第 60 轮把内置音源从"合成"换成"真采样"）──────────────────────
    // 第 60 轮那条用户反馈是「用声频合成的背景音有点伤耳朵」，所以内置音源改成素材库里的真录音，
    // **随包分发**（`shared/assets/bgm/*.wav`）。这条自检盯的就是"包里到底带上没有、带的对不对"：
    // 漏带资源的后果是每一档都退回合成音（用户听起来像"根本没改"），而这正是本轮要修的东西。
    // 判据：WAV 头合法、22050/单声道/16bit、够长、不是静音、循环接缝不爆音（判据同下面那条合成自检）。
    check("背景音乐·内置音源是真采样资源（每档都有资源 / 头 / 时长 / 响度 / 循环接缝）") {
        fun i32(b: ByteArray, p: Int) = (b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or ((b[p + 3].toInt() and 0xFF) shl 24)

        fun i16(b: ByteArray, p: Int) =
            ((b[p].toInt() and 0xFF) or ((b[p + 1].toInt() and 0xFF) shl 8)).toShort().toInt()

        // 只查"本该有资源"的档（噪声三档是合成的，见 BgmSource.sampled）
        BgmSources.builtin.filter { it.sampled }.all { src ->
            val b = BgmSources.assetBytes(src.id) ?: return@check false
            if (b.size < 44 + 22050 * 2) return@check false
            val hdr = String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "WAVE" &&
                String(b, 12, 4) == "fmt " && String(b, 36, 4) == "data" &&
                (b[20].toInt() and 0xFF) == 1 && (b[22].toInt() and 0xFF) == 1 &&
                (b[34].toInt() and 0xFF) == 16
            val frames = i32(b, 40) / 2
            var peak = 0
            var maxDelta = 0
            var prev = 0
            var i = 0
            while (i < frames) {
                val v = i16(b, 44 + i * 2)
                if (i > 0) {
                    val d = kotlin.math.abs(v - prev)
                    if (d > maxDelta) maxDelta = d
                }
                if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
                prev = v
                i++
            }
            val seam = kotlin.math.abs(i16(b, 44) - prev)
            hdr && i32(b, 24) == 22050 && frames >= 22050 * 8 &&
                peak > 16000 && seam <= maxDelta * 3 / 2
        }
    }
    // 合成器没删：它是"包坏了/裁剪过"时的兜底（宁可听感差点，也不要点了没声），所以继续逐档验。
    // 自检全程**不出声**（只验"合成得对不对"与"该不该起播"）。
    check("背景音乐·合成兜底（每档都有内容 / WAV 头与长度正确 / 同 id 确定性）") {
        val a = BgmSources.synth("rain")
        val rate = (a[24].toInt() and 0xFF) or ((a[25].toInt() and 0xFF) shl 8) or
            ((a[26].toInt() and 0xFF) shl 16) or ((a[27].toInt() and 0xFF) shl 24)
        val headerOk = String(a, 0, 4) == "RIFF" && String(a, 8, 4) == "WAVE" &&
            String(a, 12, 4) == "fmt " && String(a, 36, 4) == "data" &&
            a[20].toInt() and 0xFF == 1 && a[22].toInt() and 0xFF == 1 && a[34].toInt() and 0xFF == 16
        // 峰值必须接近归一化目标 0.7 —— 否则就是"合成出来了但其实是静音"
        var peak = 0
        var p = 44
        while (p + 1 < a.size) {
            val v = ((a[p].toInt() and 0xFF) or ((a[p + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
            if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
            p += 2
        }
        val same = a.contentEquals(BgmSources.synth("rain"))
        val allSameShape = BgmSources.builtin.all { BgmSources.synth(it.id).size == a.size }
        headerOk && rate == 22050 && peak > 20000 && same && allSameShape
    }
    // 循环点不能"啪"一声：接缝处的跳变必须落在**文件内部正常跳变**的量级里。
    // 判据只在被低通的音源（海浪/布朗/篝火）上真正有区分度——白噪声本来相邻样本就差得远，
    // 但那条更该由耳朵听（属人工验收项）。
    check("背景音乐·循环接缝（接缝跳变 ≤ 内部最大跳变 ×1.5，即不会爆音）") {
        fun seamOk(id: String): Boolean {
            val wav = BgmSources.synth(id)
            val n = (wav.size - 44) / 2
            var maxDelta = 0
            var prev = 0
            var i = 0
            while (i < n) {
                val p = 44 + i * 2
                val v = ((wav[p].toInt() and 0xFF) or ((wav[p + 1].toInt() and 0xFF) shl 8)).toShort().toInt()
                if (i > 0) {
                    val d = kotlin.math.abs(v - prev)
                    if (d > maxDelta) maxDelta = d
                }
                prev = v
                i++
            }
            val first = ((wav[44].toInt() and 0xFF) or ((wav[45].toInt() and 0xFF) shl 8)).toShort().toInt()
            return kotlin.math.abs(first - prev) <= maxDelta * 1.5
        }
        BgmSources.builtin.all { seamOk(it.id) }
    }
    check("背景音乐·落盘缓存（生成到 cacheRoot/bgm、二次取用同一文件）") {
        val f1 = BgmSources.fileFor("rain")
        val f2 = BgmSources.fileFor("rain")
        val ok = f1 != null && f1.isFile && f1.length() > 100_000 &&
            f1.parentFile == File(paths.cacheRoot, "bgm") &&
            f1.absolutePath == f2?.absolutePath
        // 自检不留下 700KB 的样本文件（清缓存本来也会清它，但让它留在真目录里没意义）
        if (ok) runCatching { f1?.delete() }
        ok
    }
    check("背景音乐·上传格式闸门（本机能放的放行，其它必须给出原因）") {
        val exts = Voice.supportedAudioExtensions
        bgmFormatRejection("a.mp3") == null && bgmFormatRejection("A.WAV") == null &&
            bgmFormatRejection("a.m4a") == null && bgmFormatRejection("a.flac") == null &&
            bgmFormatRejection("a.ogg") == null &&
            bgmFormatRejection("a.wma") != null && bgmFormatRejection("noext") != null &&
            // 第 60 轮：桌面加了三个 Java Sound SPI（aac/flac/vorbis），这份集合必须与之一致
            exts.containsAll(listOf("mp3", "wav", "m4a", "flac", "ogg"))
    }
    // 第 60 轮：**声明支持就必须真能放**——闸门放行的格式如果解不出来，
    // 用户拿到的就是本项目最忌讳的那种故障（选了、开关开着、一声不响）。
    // 三个自带的小样本（1 秒 800Hz 正弦，共约 19KB）就是为此打进包里的。
    // 判据不是"我自己解一遍看看"，而是走**播放器真路径**（解容器 → 转 PCM → Clip.open → 起播），
    // 音量给 0（不出声）——自检若另写一套判断，就会出现"自检绿、真放没声"的经典偏差。
    check("背景音乐·新格式解码（m4a / flac / ogg 走真播放路径起播，静音跑）") {
        listOf("m4a", "flac", "ogg").all { ext ->
            val bytes = BgmSources::class.java.classLoader
                ?.getResourceAsStream("selftest/decode.$ext")?.use { it.readBytes() }
                ?: return@check false
            val tmp = File.createTempFile("whale-selftest", ".$ext")
            tmp.writeBytes(bytes)
            val player = DesktopAudioPlayer()
            val ok = runCatching { player.start(tmp, loop = false, volume = 0f) {} }.getOrDefault(false)
            player.stopAndRelease()
            tmp.delete()
            ok
        }
    }
    // "该不该响"的判定（第 63 轮会话级架构；第 58 轮把"在不在会话"与"会话配了什么"拆开）：
    // 不在会话里就不响（开机不再自动出声）；会话里四种否定情形：全局开关关着 / 没选音源 /
    // 指向的音乐库条目已删 / 音源 id 不认识。
    // 若这条判断写错，用户看到的就是"开关开着却不出声"或"关掉了还在响"，所以值得单独验一次。
    check("背景音乐·只在会话中生效，不该响的情形都不起播") {
        // ① 不在会话里：即使全局开着也不响（进会话再离开，离开后必须停）
        BgmPlayer.sync(AiSettings(bgmEnabled = true, bgmSource = "white", bgmVolume = 0f))
        val outside = BgmPlayer.enterSession(null)
        BgmPlayer.leaveSession(outside)
        Thread.sleep(400) // 起播/停播都在后台线程上，给它一点时间
        val silentOutsideSession = BgmPlayer.currentPath == null
        // ② 会话里的否定情形
        var last = 0L
        listOf(
            SessionBgm(SessionBgm.MODE_GLOBAL) to AiSettings(bgmEnabled = false, bgmSource = "rain", bgmVolume = 0f),
            SessionBgm(SessionBgm.MODE_GLOBAL) to AiSettings(bgmEnabled = true, bgmSource = "", bgmVolume = 0f),
            SessionBgm(SessionBgm.MODE_ON, "lib:no-such-entry") to AiSettings(bgmEnabled = true, bgmVolume = 0f),
            SessionBgm(SessionBgm.MODE_ON, "no-such-source") to AiSettings(bgmEnabled = true, bgmVolume = 0f)
        ).forEach { (bgm, s) ->
            BgmPlayer.sync(s)
            last = BgmPlayer.enterSession(bgm)
        }
        Thread.sleep(400)
        val silentInBrokenSessions = BgmPlayer.currentPath == null
        BgmPlayer.leaveSession(last)
        silentOutsideSession && silentInBrokenSessions
    }
    // 第 58 轮两处用户反馈的回归闸门（两处都是"听着不对但看不出错"的静默故障，所以单独验）：
    // ① 从没配过 BGM（Conversation.bgm = null）的新会话进去就该跟随全局默认出声，
    //    而不是要用户去「背景音乐」里点一下「跟随全局默认」；
    // ② 切会话时新会话先进入、旧会话后离开（Compose 的真实顺序）——旧的那次离开**不许**停掉新的。
    check("背景音乐·新会话默认跟随全局出声；旧会话迟到的离开不停新会话") {
        BgmPlayer.sync(AiSettings(bgmEnabled = true, bgmSource = "white", bgmVolume = 0f))
        val h1 = BgmPlayer.enterSession(null)
        Thread.sleep(500)
        val playsWithoutManualPick = BgmPlayer.currentPath != null
        // 切到下一个会话，然后才轮到旧会话离开（顺序与桌面三栏切换时一致）
        val h2 = BgmPlayer.enterSession(null)
        BgmPlayer.leaveSession(h1)
        Thread.sleep(500)
        val staleLeaveIgnored = BgmPlayer.currentPath != null
        // 交回**自己**的句柄时才是真的离开
        BgmPlayer.leaveSession(h2)
        Thread.sleep(500)
        val ownLeaveStops = BgmPlayer.currentPath == null
        playsWithoutManualPick && staleLeaveIgnored && ownLeaveStops
    }
    // 音乐库（第 63 轮，取代旧的"只留一份"）：入库多首共存；重命名只改应用内显示名、文件本体不动；
    // 删除＝清单移除＋文件删除。bgm/ 目录放用户文件（images/ 会被孤图回收、cacheRoot 会被清缓存，都不能用）。
    check("背景音乐·音乐库：入库/重命名只改显示名/删除连文件一起删") {
        val probe = ByteArray(64) { 0x5A }
        val e1 = Repository.addBgmLibraryEntry(probe, "mp3", "第一首")
        val e2 = Repository.addBgmLibraryEntry(probe, "wav", "第二首")
        val f1 = Repository.bgmLibraryFileFor(e1.id)
        val f2 = Repository.bgmLibraryFileFor(e2.id)
        val okAdd = Repository.listBgmLibrary().any { it.id == e1.id } &&
            Repository.listBgmLibrary().any { it.id == e2.id } &&
            f1 != null && f2 != null && f1.isFile && f2.isFile &&
            f1.absolutePath.startsWith(paths.filesRoot.absolutePath) &&
            f1.parentFile?.name == "bgm"
        Repository.renameBgmLibraryEntry(e1.id, "改名了")
        val renamed = Repository.listBgmLibrary().firstOrNull { it.id == e1.id }?.displayName == "改名了" &&
            Repository.bgmLibraryFileFor(e1.id)?.name == f1?.name // 文件本体不动（重命名只改应用内显示名）
        val deleted = Repository.deleteBgmLibraryEntry(e2.id) &&
            Repository.bgmLibraryFileFor(e2.id) == null &&
            Repository.listBgmLibrary().none { it.id == e2.id }
        // 清场：别把探针数据留给真账号
        Repository.deleteBgmLibraryEntry(e1.id)
        okAdd && renamed && deleted
    }
    // 编辑草稿（第 63 轮）：AI 起草的生图提示词 / 创作页提示词退出应用后要还在，
    // 且**不自动删** —— 只有显式清空（空串）才删掉那个键。这是"退出下次重新开启还能看到离开前状态"的落点。
    check("编辑草稿·退出应用后仍在（空串才删键）") {
        val key = "selftest:draft"
        Repository.saveDraft(key, "一段还没用掉的提示词")
        val persisted = Repository.loadDraft(key) == "一段还没用掉的提示词"
        // 重新读盘（绕过内存缓存）要能拿到同一份 —— 这条才真正代表"重启后还在"
        // 落点问 Repository 要（有账号在 accounts/<id>/ 下、没有就在根）：自己拼路径会在冷根上落空
        val file = File(Repository.draftsFilePath())
        val onDisk = file.isFile && file.readText().contains("一段还没用掉的提示词")
        // 生成/使用不删草稿：再写一次不同内容（模拟用户改了提示词），旧值不该"用完就消失"
        Repository.saveDraft(key, "改过的提示词")
        val updated = Repository.loadDraft(key) == "改过的提示词"
        // 只有空串才删键
        Repository.saveDraft(key, "")
        val cleared = Repository.loadDraft(key).isEmpty() &&
            !file.readText().contains("改过的提示词")
        // 其它键不受影响（按用途分开存，三处输入互不覆盖）
        Repository.saveDraft("selftest:other", "另一处输入")
        val isolated = Repository.loadDraft("selftest:other") == "另一处输入"
        Repository.saveDraft("selftest:other", "")
        persisted && onDisk && updated && cleared && isolated
    }
    /** 从引擎里掏出它的 `Clip`（`DesktopAudioPlayer.clip`）；不是桌面播放器 ⇒ null */
    fun clipOf(engine: Any?): javax.sound.sampled.Clip? = runCatching {
        engine?.javaClass?.getDeclaredField("clip")?.apply { isAccessible = true }?.get(engine)
    }.getOrNull() as? javax.sound.sampled.Clip

    fun masterGainOf(clip: javax.sound.sampled.Clip?): javax.sound.sampled.FloatControl? = runCatching {
        clip?.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN) as? javax.sound.sampled.FloatControl
    }.getOrNull()

    /** 引擎此刻的**实际增益（dB）**：MASTER_GAIN 优先，退回 VOLUME（线性）也折成 dB。
     *  读不到 ⇒ null（那才是"滑条是摆设"）。断言一律打在它身上 —— 只断言"setVolume 没抛异常"
     *  是假闸门：用户听不听得见变化，与"调用没报错"根本不是一回事（第 63 轮的教训）。 */
    fun engineGainDb(engine: Any?): Float? {
        val clip = clipOf(engine) ?: return null
        masterGainOf(clip)?.let { return it.value }
        val lin = runCatching {
            clip.getControl(javax.sound.sampled.FloatControl.Type.VOLUME) as? javax.sound.sampled.FloatControl
        }.getOrNull() ?: return null
        return if (lin.value <= 0.0001f) -80f
        else (20.0 * kotlin.math.log10(lin.value.toDouble())).toFloat()
    }

    /** 音量（0~1）→ 它该对应的分贝（与 `DesktopAudioPlayer.applyGain` 同一套换算） */
    fun gainDbForVolume(v: Float): Float =
        if (v <= 0.0001f) -80f else (20.0 * kotlin.math.log10(v.toDouble())).toFloat()

    fun nearDb(a: Float?, b: Float, tol: Float = 0.5f) = a != null && kotlin.math.abs(a - b) <= tol

    /** `BgmPlayer` 里正在响的那个引擎（正式 BGM / 试听各一个，都是 private 字段） */
    fun bgmEngine(): Any? = runCatching {
        BgmPlayer.javaClass.getDeclaredField("engine").apply { isAccessible = true }.get(BgmPlayer)
    }.getOrNull()

    fun bgmPreviewEngine(): Any? = runCatching {
        BgmPlayer.javaClass.getDeclaredField("previewEngine").apply { isAccessible = true }.get(BgmPlayer)
    }.getOrNull()

    // 循环起播 + 音量控件（**静音跑**：音量留在 0 上出声，只把控件读回来验）。
    // 桌面音量靠 MASTER_GAIN（分贝）或 VOLUME（线性），两个都没有的话滑条就是摆设——
    // 所以这里要求的不是"没抛异常"，而是**增益真的等于这个音量对应的分贝**、滑到 0 真的落到最小值。
    check("背景音乐·循环起播与音量控件可用（静音跑）") {
        val f = BgmSources.fileFor("white") ?: return@check false
        val player = DesktopAudioPlayer()
        val started = player.start(f, loop = true, volume = 0f) {}
        val clip = clipOf(player)
        val master = masterGainOf(clip)
        val hasGain = master != null || runCatching {
            clip?.isControlSupported(javax.sound.sampled.FloatControl.Type.VOLUME) == true
        }.getOrDefault(false)
        player.setVolume(0.35f)
        val midOk = nearDb(engineGainDb(player), gainDbForVolume(0.35f))
        player.setVolume(0f)
        val muted = (engineGainDb(player) ?: 0f) <= -79f ||
            (master != null && kotlin.math.abs(master.value - master.minimum) < 0.01f)
        Thread.sleep(300) // 让它真的循环播放一小会儿
        player.stopAndRelease()
        f.delete()
        started && hasGain && midOk && muted
    }
    // 第 72 轮（用户报「BGM 声音调节好像不生效」）。曾经的两条**静默无效**：
    // ① 会话配过自定义音量时，拖滑条（`setVolumeLive` 只改全局音量）在解析里被会话音量盖掉 ⇒ 拖动期间毫无反应；
    // ② 同一类会话里，设置页那个音量滑条**永远无效**（音乐归会话音量管）。
    // 新口径：全 App 只有一个音量，所以断言就是"播放器拿到的增益＝设置里的音量"。
    // 音量刻意压得很低（0.05 ≈ -26dB）——自检不该在开发机上放一嗓子。
    check("背景音乐·音量到得了播放器：会话共享设置音量、拖动即刻生效") {
        BgmPlayer.sync(AiSettings(bgmEnabled = true, bgmSource = "white", bgmVolume = 0.05f))
        // 老会话里存的 90% 会话音量必须被忽略（会话不再持有自己的音量）
        val h = BgmPlayer.enterSession(SessionBgm(SessionBgm.MODE_ON, "white", 0.9f))
        Thread.sleep(500)
        val plays = BgmPlayer.currentPath != null
        val sharedVolume = nearDb(engineGainDb(bgmEngine()), gainDbForVolume(0.05f))
        // 拖动滑条（不落盘）也要立刻听见变化 —— 这条以前是 FAIL 的
        BgmPlayer.setVolumeLive(0.25f)
        Thread.sleep(150)
        val liveDrag = nearDb(engineGainDb(bgmEngine()), gainDbForVolume(0.25f))
        BgmPlayer.setVolumeLive(0f) // 收回静音再收场
        BgmPlayer.leaveSession(h)
        Thread.sleep(400)
        plays && sharedVolume && liveDrag
    }
    // 设置页里**唯一听得到的声音就是试听**（那时聊天页已离开组合，正式 BGM 不响）。
    // 试听音量原先写死 1.0 ⇒ 用户拖音量滑条一点变化都听不到，就是"调节不生效"这句话本身。
    check("背景音乐·试听跟随音量设置（拖动也实时变）") {
        BgmPlayer.sync(AiSettings(bgmEnabled = true, bgmSource = "white", bgmVolume = 0.05f))
        BgmPlayer.previewSource("white", seconds = 1)
        Thread.sleep(500)
        val started = BgmPlayer.previewPath != null
        val follows = nearDb(engineGainDb(bgmPreviewEngine()), gainDbForVolume(0.05f))
        BgmPlayer.setVolumeLive(0.4f) // 试听中拖滑条
        Thread.sleep(150)
        val liveDrag = nearDb(engineGainDb(bgmPreviewEngine()), gainDbForVolume(0.4f))
        BgmPlayer.setVolumeLive(0f)
        BgmPlayer.stopPreview()
        Thread.sleep(200)
        started && follows && liveDrag
    }
    // 供应商 mp3 的解码路径：拿缓存目录里**真实合成的样本**验（本机没跑过供应商朗读就没样本，
    // 跳过不算失败）。判据是"拿到的是 PCM_SIGNED"——压缩格式（MPEG2L3）就是 #6 的病根。
    val cachedMp3 = File(paths.cacheRoot, "tts").listFiles { f -> f.name.endsWith(".mp3") }?.firstOrNull()
    check("供应商 MP3 解码为 PCM（${cachedMp3?.name ?: "无缓存样本，跳过"}）") {
        if (cachedMp3 == null) return@check true
        val src = javax.sound.sampled.AudioSystem.getAudioInputStream(cachedMp3)
        val fmt = src.format
        val target = pcmTargetOf(fmt)
        val decoded = javax.sound.sampled.AudioSystem.getAudioInputStream(target, src)
        val pcm = decoded.format.encoding == javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED
        decoded.close()
        src.close()
        pcm
    }
    // ⚠ 这一项是**为第 28 轮那次真实的 EDT 死循环补的**：转换流是惰性读源流的，
    // 谁要是"转换完就把源流关掉"，mp3spi 的解码循环就会一直重试读到 EOF —— 表现是
    // 点一次朗读、界面永远卡死（EDT 空烧 CPU）。所以这里必须**真的读到 EOF**，
    // 而且带超时：读不完就是 FAIL，不能让自检自己也挂在那里。
    check("MP3 解码流能读完（8 秒内读到 EOF）") {
        if (cachedMp3 == null) return@check true
        var bytes = 0L
        var failure: String? = null
        val worker = Thread {
            try {
                val src = javax.sound.sampled.AudioSystem.getAudioInputStream(cachedMp3)
                val decoded = javax.sound.sampled.AudioSystem.getAudioInputStream(pcmTargetOf(src.format), src)
                val buf = ByteArray(8192)
                while (true) {
                    val n = decoded.read(buf)
                    if (n < 0) break
                    bytes += n
                }
                decoded.close()
                src.close()
            } catch (t: Throwable) {
                failure = "${t.javaClass.simpleName}：${t.message}"
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(8_000)
        if (worker.isAlive) return@check false
        failure == null && bytes > 1000
    }
    // PowerShell + System.Speech 探测（M1 探针第 8 条：本机有 zh-CN 音色）。探不到不算失败——
    // 那是"本机没装语音包"的环境事实，UI 有提示路径；这里只要求探测本身能跑完并给出结论。
    val speechOk = DesktopSpeechCapability.await(30_000)
    val voices = DesktopSpeechCapability.voices()
    val zhVoice = voices.firstOrNull { it.culture.equals("zh-CN", ignoreCase = true) }?.name
        ?: voices.firstOrNull { it.culture.startsWith("zh", ignoreCase = true) }?.name
    check("系统语音探测有结论（音色 ${voices.size} 个 / 中文 ${zhVoice ?: "无"}）") {
        if (speechOk) voices.isNotEmpty() else DesktopSpeechCapability.failureReason() != null
    }
    check("剪贴板可写") {
        Platform.ui.copyToClipboard("自检", "whale-smoke")
        true
    }
    // E4 收尾：「分享」（长按/右键菜单里那条）。桌面没有系统分享面板，实现是"写剪贴板 + 提示"
    // （见 DesktopPlatformUi.shareText）。这条按"用户会失去什么"写：点了「分享」却什么都没进剪贴板
    // = 用户以为发出去了，实际什么都没有。读回来核对，不看它有没有抛异常。
    check("消息「分享」真的送出去了（桌面＝写剪贴板，读回核对）") {
        val marker = "whale-share-smoke-${System.nanoTime()}"
        Platform.ui.shareText("分享消息", marker)
        var read: String? = null
        // 剪贴板偶尔被别的程序占着：给它三次机会（真坏掉时三次都读不回同一段文本）
        repeat(3) {
            if (read != marker) {
                read = runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard
                        .getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                }.getOrNull()
                if (read != marker) Thread.sleep(30)
            }
        }
        read == marker
    }
    check("崩溃日志落盘 → 读取 → 清除（走真实 record 路径）") {
        Platform.ui.clearCrashLog()
        DesktopCrashLog.note("自检轨迹")
        DesktopCrashLog.record(Thread.currentThread(), RuntimeException(CRASH_SMOKE_MARKER))
        val text = runBlocking { Platform.ui.readCrashLog() }
        val hasMarker = text?.contains(CRASH_SMOKE_MARKER) == true
        val hasBreadcrumb = text?.contains("自检轨迹") == true
        Platform.ui.clearCrashLog()
        val cleared = runBlocking { Platform.ui.readCrashLog() } == null
        hasMarker && hasBreadcrumb && cleared
    }
    // 应用日志落盘（M9，第 30 轮）。这条**不只查"文件里有字"**，而是真触发一次播放失败，
    // 再确认"播放失败（xx.mp3）：原因"完整落进了文件——第 28 轮"配好供应商却不发声"之所以
    // 查了一整轮，就是因为这句话只进了被 Gradle 缓冲走的 stdout，事后一行都拿不到。
    check("应用日志落盘 → 读取（含一次真实播放失败留痕）") {
        if (Platform.ui.appLogPath() == null) return@check false
        val bad = File.createTempFile("whale-smoke-bad", ".mp3")
        bad.writeBytes(ByteArray(64) { 0x11 })  // 垃圾字节：解码必然失败，走 fail() 那条留痕出口
        DesktopAudioPlayer().start(bad) {}
        val text = runBlocking { Platform.ui.readAppLog() }
        bad.delete()
        text?.contains("播放失败（${bad.name}）") == true
    }
    check("单实例锁可获取") {
        val lock = SingleInstanceLock(File(paths.filesRoot, "app.lock"))
        val ok = lock.tryAcquire()
        lock.release()
        ok
    }
    // 窗口图标资源（2026-09-17 换新图标时加）：必须能从 classpath 读到。
    // 单列一条是因为"开发能跑、打包后没图标"是资源类改动的经典坑——jlink/jar 少一个资源
    // 运行时不报错，只是静默少个图标。校验文件头而不是只看"流非空"，免得读进一个错误页。
    check("窗口图标资源可加载（icon/whale-icon.png）") {
        val stream = Class.forName("com.mysticat.roleplay.desktop.MainKt")
            .getResourceAsStream("/icon/whale-icon.png") ?: return@check false
        val head = stream.use { ins ->
            ByteArray(8).also { buf -> if (ins.read(buf) != buf.size) return@check false }
        }
        head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()
    }
    check("窗口状态持久化往返（含出界回中）") {
        val tmp = File.createTempFile("whale-window", ".properties")
        WindowPrefs(200, 106, 1280, 860, maximized = false).save(tmp)
        val loaded = WindowPrefs.load(tmp)
        val roundTrip = loaded.width == 1280 && loaded.height == 860 &&
            loaded.x == 200 && loaded.y == 106 && !loaded.maximized
        // 上次的坐标可能落在已拔掉的显示器上：必须回落成居中，而不是把窗口丢在看不见的地方
        WindowPrefs(99999, 99999, 1280, 860, maximized = false).save(tmp)
        val offScreen = WindowPrefs.load(tmp)
        tmp.delete()
        roundTrip && offScreen.x == null && offScreen.y == null
    }
    // M11 ③：无边框窗口的缩放走"自绘命中条 + 改 WindowState"（不引 JNA），几何计算抽成了纯函数。
    // 这里把八个方向与"拖过最小尺寸要钉住"各验一遍——真窗口的拖动手感留人工，但算错方向/丢对边
    // 这类错误（左缘缩放把窗口整体平移走）在纯函数里就能拦住。
    check("无边框窗口缩放几何（八方向 + 最小尺寸钳制 + 对边钉住）") {
        val start = WindowRect(100.dp, 100.dp, 1200.dp, 800.dp)
        val minW = WindowPrefs.MIN_W.dp
        val minH = WindowPrefs.MIN_H.dp
        val east = applyResize(start, ResizeEdge.E, 50.dp, 0.dp, minW, minH)
        val west = applyResize(start, ResizeEdge.W, 50.dp, 0.dp, minW, minH)
        val westOver = applyResize(start, ResizeEdge.W, 5000.dp, 0.dp, minW, minH)
        val north = applyResize(start, ResizeEdge.N, 0.dp, -40.dp, minW, minH)
        val northOver = applyResize(start, ResizeEdge.N, 0.dp, 5000.dp, minW, minH)
        val south = applyResize(start, ResizeEdge.S, 0.dp, 30.dp, minW, minH)
        val corner = applyResize(start, ResizeEdge.SE, -30.dp, 30.dp, minW, minH)
        east.w == 1250.dp && east.x == 100.dp &&
            west.w == 1150.dp && west.x == 150.dp &&
            westOver.w == minW && westOver.x == start.x + (start.w - minW) &&
            north.h == 840.dp && north.y == 60.dp &&
            northOver.h == minH && northOver.y == start.y + (start.h - minH) &&
            south.h == 830.dp && south.y == 100.dp &&
            corner.w == 1170.dp && corner.h == 830.dp
    }
    // M11 ②：托盘/自启桥。stub 实现不碰真托盘与注册表（--smoke 是无头路径），
    // 只验"偏好随改随落 + 重启读回"的链路；注册表的真实写入走设置页 + 人工/真窗口验收。
    check("桌面行为设置桥（hideToTray 落盘往返 / 未安装时安全默认）") {
        val prefsFile = File(paths.filesRoot, "smoke-desktop-prefs.properties")
        prefsFile.delete()
        val stub = object : DesktopFeatures.Impl {
            override val traySupported = true
            override val launchAtLoginSupported = false
            override fun applyTraySetting(enabled: Boolean) {}
            override fun applyLaunchAtLogin(enabled: Boolean) = false
            override fun queryLaunchAtLogin() = false
        }
        DesktopFeatures.install(stub, prefsFile)
        val defaultedOff = !DesktopFeatures.hideToTrayOnClose
        DesktopFeatures.setHideToTray(true)
        val writtenNow = DesktopFeatures.hideToTrayOnClose && prefsFile.isFile
        DesktopFeatures.install(stub, prefsFile) // 模拟重启：重新从文件读
        val reloaded = DesktopFeatures.hideToTrayOnClose
        DesktopFeatures.setHideToTray(false)
        val cleaned = !DesktopFeatures.hideToTrayOnClose
        prefsFile.delete()
        defaultedOff && writtenNow && reloaded && cleaned
    }
    check("开机自启注册表桥（只读查询；dev 无启动器 → supported=false）") {
        runCatching { WindowsAutostart.isEnabled() }.isSuccess &&
            (WindowsAutostart.supported == (System.getProperty("jpackage.app-path") != null))
    }
    // M11 ④：拖拽导入/粘贴图片的桥。真拖放与真剪贴板验不了（无头路径），这里验业务链路：
    // 临时 JSON 卡真的能走 handleFiles 进库（完事删干净），文件分流与图片接收器语义正确。
    check("拖入角色卡导入链路（临时卡 → handleFiles 导入 → 清理）") {
        val tmp = File.createTempFile("whale-smoke-card", ".json")
        tmp.writeText("""{"name":"自检拖拽卡","description":"smoke 人设","first_mes":"开场白"}""")
        val msg = runBlocking { DesktopDragDrop.handleFiles(listOf(tmp.absolutePath)) }
        tmp.delete()
        val imported = Repository.listCharacters().filter { it.name == "自检拖拽卡" }
        imported.forEach { Repository.deleteCharacter(it.id) }
        msg != null && msg.contains("自检拖拽卡") && imported.isNotEmpty()
    }
    check("拖拽/粘贴桥（文件分流判断 + 图片接收器注册撤销）") {
        val card = File("卡.json")
        val png = File("图.png")
        val other = File("a.wav")
        val split = DesktopDragDrop.isCardFile(card) && !DesktopDragDrop.isImageFile(card) &&
            DesktopDragDrop.isImageFile(png) && !DesktopDragDrop.isCardFile(png) &&
            !DesktopDragDrop.isCardFile(other) && !DesktopDragDrop.isImageFile(other)
        var got: String? = null
        val sink: (String) -> Boolean = { got = it; true }
        DesktopDragDrop.imageSink = sink
        val delivered = DesktopDragDrop.imageSink?.invoke("x.png") == true && got == "x.png"
        DesktopDragDrop.imageSink = null
        split && delivered && DesktopDragDrop.imageSink == null
    }
    // M11 ④：Ctrl+V 贴图链路。**确定性部分**（每次必验）：内存 BufferedImage → PNG 编码 →
    // handleImageBytes 落盘 → 接收器收到本地路径。**机会性部分**：系统剪贴板没被环境里的
    // 常驻软件锁住时，顺带验"写内存图 → clipboardHasImageOnly → 真实读回"那段 OS 往返
    // （本机实测剪贴板会被锁十几秒以上，所以它失败只降级不判负——键入路由与第 38 轮已验证的
    // Ctrl+B 同一入口，留人工验收）。
    check("剪贴板贴图链路（PNG 编码 → 落盘 → 接收器；剪贴板未被环境锁住时加验 OS 读回）") {
        val img = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, 0xFF3366)
        val png = ByteArrayOutputStream().let { buf ->
            ImageIO.write(img, "png", buf)
            buf.toByteArray()
        }
        var got: String? = null
        DesktopDragDrop.imageSink = { got = it; true }
        val direct = runBlocking { DesktopDragDrop.handleImageBytes(png, "png") }
        val savedOk = got?.let { p -> val f = File(p); val ok = f.isFile && f.length() > 0; f.delete(); ok }
        DesktopDragDrop.imageSink = null
        var osRoundTrip = false
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        val prev = runCatching { cb.getContents(null) }.getOrNull()
        val transferable = object : java.awt.datatransfer.Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(f: DataFlavor) = f == DataFlavor.imageFlavor
            override fun getTransferData(f: DataFlavor) = img
        }
        var wrote = false
        repeat(3) {
            if (!wrote) {
                runCatching { cb.setContents(transferable, null) }
                    .onSuccess { wrote = true }
                    .onFailure { println("[smoke-diag] system clipboard busy: ${it.message}") }
            }
            if (!wrote) Thread.sleep(1000)
        }
        if (wrote) {
            var readBack: String? = null
            DesktopDragDrop.imageSink = { readBack = it; true }
            val msg = runBlocking { clipboardImageToSink() }
            DesktopDragDrop.imageSink = null
            File(readBack ?: "").delete()
            osRoundTrip = clipboardHasImageOnly() && readBack != null && msg == "已粘贴图片"
        }
        prev?.let { runCatching { cb.setContents(it, null) } }
        direct == "已粘贴图片" && savedOk == true && (!wrote || osRoundTrip)
    }

    // 发版前用真便携包自检：`WHALE_SMOKE_PORTABLE_ZIP=<zip 路径> :desktopApp:run --args="--smoke"`
    // 验的是更新器真读得出包内版本、真解压得出文件、真生成了替换脚本（M5 的更新链路）
    // ⚠ 用 bash 直接跑 gradle 时要加 `--no-daemon`：JavaExec 继承的是 **Gradle 常驻进程**的环境，
    //   在 shell 里 export 的这个变量到不了应用（第 59 轮实测），而那会让这两项**静默消失**。
    // 没设变量时**显式报 SKIP**：这两项是发版前的必过项，"没跑"不能混在"全绿"里（同一课：自检项要写清它在哪种语境下成立）。
    val portableZip = System.getenv("WHALE_SMOKE_PORTABLE_ZIP")?.takeIf { it.isNotBlank() }?.let(::File)
    if (portableZip == null || !portableZip.isFile) {
        skip(
            "便携包解析 + 暂存/替换脚本（更新链路）",
            portableZip?.let { "找不到 ${it.absolutePath}" } ?: "未设 WHALE_SMOKE_PORTABLE_ZIP（发版前须用真便携包再跑一次）"
        )
    } else {
        check("便携包解析（包内 build-info.json → 版本）") {
            val v = DesktopUpdater.readPackageVersion(portableZip)
            v != null && v.first == Platform.ui.appPackageName() && v.second > 0 && v.third.isNotBlank()
        }
        check("便携包暂存与替换脚本生成") {
            val tmpDir = File(System.getProperty("java.io.tmpdir"), "whale-stage-smoke").apply {
                deleteRecursively()
                mkdirs()
            }
            val script = DesktopUpdater.stageInto(portableZip, tmpDir, ProcessHandle.current().pid())
            val staged = File(tmpDir, "update-staging")
            val ok = script.isFile && script.readText(Charsets.US_ASCII).contains("xcopy") &&
                staged.listFiles().orEmpty().any { it.name.endsWith(".exe") }
            tmpDir.deleteRecursively()
            ok
        }
    }

    // M13：更新分流的可写性探测。probeWritable 对真目录应返回 true；appDirWritable()
    // 无 jpackage.app-path（开发运行）时为 null，有（产物包自检，第 45 轮起）时应等于对应用目录的真实探测值——
    // 便携包解在可写目录里跑 smoke 是 true、属预期，不能再按"必为 null"断言。
    check("更新分流·可写性探测（probe=true / appDirWritable 与运行语境一致）") {
        val tmp = File(System.getProperty("java.io.tmpdir"), "whale-writable-smoke").apply { mkdirs() }
        val appDir = DesktopUpdater.appImageDir()
        val ok = DesktopUpdater.probeWritable(tmp) &&
            (appDir == null || DesktopUpdater.appDirWritable() == DesktopUpdater.probeWritable(appDir))
        tmp.deleteRecursively()
        ok
    }

    // 台账 8：托盘菜单的更新入口与「版本与安全」页共用 UpdateChecker.target 这一份分流口径。
    // 断言写成"用户会拿到什么"——分流错了的两个后果分别是：装进 Program Files 的用户被指去替换
    // 便携包（他写不进去，更新永远失败），以及清单漏写 url 时拿空地址去下载（静默转圈）。
    check("更新分流·下载目标选择（安装程序形态不指去替换便携包 / 空地址不下载）") {
        val full = UpdateInfo(
            versionCode = 99, versionName = "9.9.9", url = "https://example.invalid/whale.zip",
            notes = "", sha256 = "zip-sha", installerUrl = "https://example.invalid/setup.exe",
            installerSha256 = "exe-sha"
        )
        // ① 安装程序形态 + 清单给了安装器 → 必须拿 exe
        val a = UpdateChecker.target(full, viaInstaller = true, fallbackUrl = "")
        // ② 安装程序形态但清单没给安装器（老清单）→ 退回便携包，不能变成"没有更新"
        val oldList = full.copy(installerUrl = "", installerSha256 = "")
        val b = UpdateChecker.target(oldList, viaInstaller = true, fallbackUrl = "")
        // ③ 便携包形态 + 清单给了安装器 → 仍走 zip，不能被 exe 抢走
        val c = UpdateChecker.target(full, viaInstaller = false, fallbackUrl = "")
        // ④ 清单没有 url、也没有编译期兜底 → null（调用方提示未配置，不是拿空串去请求）
        val noUrl = oldList.copy(url = "")
        val d = UpdateChecker.target(noUrl, viaInstaller = false, fallbackUrl = "")
        // ⑤ 编译期兜底存在时优先用它（历史口径：DOWNLOAD_URL 非空即固定地址）
        val e = UpdateChecker.target(noUrl, viaInstaller = false, fallbackUrl = "https://example.invalid/pin.zip")
        a == UpdateTarget("https://example.invalid/setup.exe", "exe-sha", isInstaller = true) &&
            b?.isInstaller == false && b?.url == "https://example.invalid/whale.zip" && b?.sha256 == "zip-sha" &&
            c?.isInstaller == false && c?.url == "https://example.invalid/whale.zip" && c?.sha256 == "zip-sha" &&
            d == null &&
            e?.url == "https://example.invalid/pin.zip" &&
            // 同版本号不算有更新，否则每次检查都报「发现新版本」
            !full.copy(versionCode = 6).isNewerThan(6) && full.isNewerThan(6)
    }

    // 台账 9(a)（第 89 轮）：启动静默检查**只在真查到更新的版本时**出声。反过来的三种
    // （已是最新 / 清单里没有本平台节点 / 网络失败）都必须一声不响——否则每次启动都要给
    // 没主动要这个结果的用户弹一句"检查更新失败"。断言写在用户看得见的那一层：提示条状态。
    check("启动静默检查的出声口径（有新版才提示；最新/无节点/断网一律不打扰）") {
        val cur = Platform.ui.appVersionCode()
        val newer = UpdateInfo(
            versionCode = cur + 1, versionName = "9.9.9",
            url = "https://example.invalid/whale.zip", notes = ""
        )
        val quiet: List<Result<UpdateInfo?>> = listOf(
            Result.success(newer.copy(versionCode = cur)),       // 已是最新（同版本号）
            Result.success(null),                                // 清单里没有本平台节点 / 解析失败
            Result.failure(IllegalStateException("网络不可达"))   // 断网
        )
        val silent = quiet.all {
            DesktopTray.applySilentCheckResult(it, cur) == null && DesktopUpdateNotice.info == null
        }
        // 真查到新版：提示条要有内容（托盘菜单那条路读的是同一份结果，开了托盘的人还多个气泡）
        val heard = DesktopTray.applySilentCheckResult(Result.success(newer), cur)
        val shown = heard == newer && DesktopUpdateNotice.info == newer && !DesktopUpdateNotice.dismissed
        // 「忽略」＝本次运行不再提示（不持久化：下次启动重新查，"忽略一次"不该变成永久静音）
        DesktopUpdateNotice.dismiss()
        val dismissed = DesktopUpdateNotice.info == newer && DesktopUpdateNotice.dismissed
        DesktopUpdateNotice.clear()
        silent && shown && dismissed && DesktopUpdateNotice.info == null
    }

    // M13 ③：首启引导页的路径配置往返——环境参数化的 resolve（不碰真锚点）。
    // 非法值（相对路径/空）必须回默认锚点，不能让配置文件把启动弄挂。
    check("引导页·目录配置往返（paths.properties 生效 + 非法值回退默认）") {
        val base = File(System.getProperty("java.io.tmpdir"), "whale-paths-smoke")
        // resolve() 的参数语义是 APPDATA 父目录（它自己往下拼 MysticatRoleplay 锚点）
        val appData = File(base, "appdata").apply { mkdirs() }
        val anchor = File(appData, "MysticatRoleplay").apply { mkdirs() }
        val dataDir = File(base, "mydata").apply { mkdirs() }
        val cacheDir = File(base, "mycache").apply { mkdirs() }
        val cfg = File(anchor, DesktopPaths.CONFIG_FILE)
        // 用 Properties.store 写：Windows 路径的反斜杠在 properties 格式里是转义符，手拼必踩坑
        java.util.Properties().apply {
            setProperty("dataDir", dataDir.absolutePath)
            setProperty("cacheDir", cacheDir.absolutePath)
        }.store(cfg.outputStream(), null)
        val home = base.absolutePath
        val ok1 = runCatching {
            val p = DesktopPaths.resolve(appData = appData.absolutePath, localAppData = appData.absolutePath, home = home)
            p.filesRoot == dataDir && p.cacheRoot == cacheDir && p.anchorDir == anchor
        }.getOrDefault(false)
        // 非法值回退：dataDir 写相对路径 → 回锚点；cacheDir 没配 → 回 LOCALAPPDATA 默认
        cfg.writeText("dataDir=relative/path\n")
        val ok2 = runCatching {
            val p = DesktopPaths.resolve(appData = appData.absolutePath, localAppData = appData.absolutePath, home = home)
            p.filesRoot == anchor && p.cacheRoot == File(anchor, "cache")
        }.getOrDefault(false)
        cfg.delete()
        base.deleteRecursively()
        ok1 && ok2
    }

    // 用户 2026-09-21：开发态与打包态锚点分家——锚点名必须与运行语境一致（dev＝-dev 后缀、打包＝原名），
    // 且 no-arg resolve() 的真锚点名跟着走。写成"与语境一致"而不是"必为 -dev"：两种运行语境都成立
    //（第 55 轮教训：自检项要写清它在哪种运行语境下成立）。
    check("数据目录·双锚点分家（锚点名与运行语境一致，开发数据不进正式版目录）") {
        val expected = if (com.mysticat.roleplay.data.DesktopRelaunch.currentExe() != null) {
            "MysticatRoleplay"
        } else {
            "MysticatRoleplay-dev"
        }
        DesktopPaths.anchorNameForRuntime() == expected &&
            DesktopPaths.resolve().anchorDir.name == expected
    }

    // M13 ③：引导桥的安装与收尾——stub impl 验「写配置+写标记+关覆盖层+改目录提示」全链路。
    // shortcutAvailable=false：smoke 里不该尝试建快捷方式（dev/无头路径本来也没有 exe）。
    check("引导页·桥收尾链路（stub impl：配置+标记落盘、覆盖层关闭、改目录给提示）") {
        val base = File(System.getProperty("java.io.tmpdir"), "whale-onboard-smoke").apply { mkdirs() }
        val cfg = File(base, DesktopPaths.CONFIG_FILE)
        val marker = File(base, DesktopPaths.ONBOARDING_MARKER)
        val effData = File(base, "eff-data").absolutePath
        val effCache = File(base, "eff-cache").absolutePath
        val stub = object : DesktopOnboarding.Impl {
            override val shortcutAvailable = false
            override fun createDesktopShortcut(): Boolean = error("smoke 不应尝试建快捷方式")
            override fun pickDirectory(current: String): String? = null
            override fun isInsideInstallDir(path: String): Boolean = false
            override fun scheduleRestart(): Boolean = false
        }
        DesktopOnboarding.install(cfg, marker, effData, effCache, firstLaunchNow = true, implNow = stub)
        val shownAtStart = DesktopOnboarding.showOverlay && DesktopOnboarding.firstLaunch
        val newData = File(base, "new-data").absolutePath
        DesktopOnboarding.updateDataDir(newData)
        val msg = kotlinx.coroutines.runBlocking { DesktopOnboarding.complete() }
        val p = java.util.Properties()
        if (cfg.isFile) cfg.inputStream().use { p.load(it) }
        val ok = shownAtStart &&
            DesktopOnboarding.showOverlay == false &&
            DesktopOnboarding.firstLaunch == false &&
            marker.isFile &&
            p.getProperty("dataDir") == newData &&
            p.getProperty("cacheDir") == effCache &&
            msg.contains("下次启动生效") && msg.contains("不会自动搬移")
        base.deleteRecursively()
        ok
    }

    // 第 44 轮（台账 9a）：一键清理＝清缓存（tts 保留）。纯目录口径在临时目录上验
    // （第 37 轮事故后铁律：破坏性操作绝不指向真数据/真缓存目录）。
    // ⚠ 2026-09-21 第 63 轮重写（用户实测 P0：桌面端「清除缓存」把角色卡也清了）：
    //    数据目录与缓存目录**用户可以把它们设成同一个**，而旧口径是"缓存根下除 tts 全删" ⇒
    //    数据树 `roleplay/`（角色卡/会话/图片/账号 settings）连同 `<缓存根>/backups`（导出备份）一起被删。
    //    这条自检现在**把"缓存根里混着数据"这个真实布局造出来**，断言：数据树与 backups 一个字节不动，
    //    只有白名单里的缓存目录被清、tts 保留，且"统计出来的可清字节 == 实际清掉的字节"。
    check("缓存清理（缓存根里混着数据树也不动它；只清白名单；tts 保留；统计==实清）") {
        val tmp = File(System.getProperty("java.io.tmpdir"), "whale-cache-smoke").apply { deleteRecursively(); mkdirs() }
        fun put(rel: String, bytes: Int): File =
            File(tmp, rel).apply { parentFile.mkdirs(); writeText("x".repeat(bytes)) }
        // 缓存（该清）
        val bgm = put("bgm/rain.wav", 512)
        val drop = put("voice-input/a.wav", 2048)
        // tts（保留）
        val keep = put("tts/a.mp3", 1024)
        // 用户数据：数据树（角色卡/会话/图片/账号 settings）与导出的备份——**一个都不能少**
        val card = put("roleplay/accounts/acc-1/characters/c1.json", 4096)
        val conv = put("roleplay/accounts/acc-1/conversations/v1.json", 4096)
        val img = put("roleplay/accounts/acc-1/images/i1.png", 4096)
        val backup = put("backups/鲸鱼-备份.json", 8192)
        val before = Repository.cacheBreakdownIn(tmp)
        val freed = Repository.clearCachesIn(tmp)
        val dataSurvived = listOf(card, conv, img, backup).all { it.isFile }
        val cacheCleared = !bgm.exists() && !drop.exists()
        // 统计口径必须等于实清口径（旧实现会把这个数算成"全部文件"⇒ 谎报可清理量）
        val statsMatch = before.cacheDirBytes == 512L + 2048L && before.ttsBytes == 1024L &&
            freed == 512L + 2048L
        val ok = dataSurvived && cacheCleared && keep.isFile && statsMatch
        tmp.deleteRecursively()
        ok
    }

    // 第 49 轮（台账 1）：自重启外壳脚本——内容纯 ASCII、有等退出的 wait 循环与自清理；
    // dev（无 jpackage.app-path）scheduleRestart 拒绝执行。
    // ⚠ 在便携包/安装版里跑自检时 `jpackage.app-path` 有值，scheduleRestart() 会**真的**往临时目录
    //    写脚本并 `cmd /c start` 拉起一只等我们退出的外壳 —— 那是副作用，自检不能调它；
    //    所以打包态只核脚本内容，dev 态才核"拒绝排程"。（此前这项在打包态恒 FAIL。）
    check("自重启外壳脚本（ASCII + wait 循环 + 自清理；dev 拒绝排程）") {
        val s = com.mysticat.roleplay.data.DesktopRelaunch.buildWaitAndStartScript(12345, "C:\\app\\whale.exe")
        val asciiOk = s.all { it.code < 128 } && s.startsWith("@echo off") &&
            s.contains(":wait") && s.contains("tasklist /FI \"PID eq %WAITPID%\"") &&
            s.contains("start \"\" \"%TARGET%\"") && s.contains("del \"%~f0\"")
        val packaged = com.mysticat.roleplay.data.DesktopRelaunch.currentExe() != null
        asciiOk && (packaged || com.mysticat.roleplay.data.DesktopRelaunch.scheduleRestart() == false)
    }

    // 第 49 轮（台账 12）：安装目录树判定——dev 无安装目录恒 false；子目录/相等/外部各一例（用锚点冒充）
    check("改目录·安装目录树判定（dev 恒 false）") {
        val tmp = File(System.getProperty("java.io.tmpdir"), "whale-inside-smoke").apply { mkdirs() }
        val ok = !com.mysticat.roleplay.data.DesktopRelaunch.isInsideAppDir(tmp.absolutePath) &&
            !com.mysticat.roleplay.data.DesktopRelaunch.isInsideAppDir("")
        tmp.deleteRecursively()
        ok
    }

    // 第 49 轮（台账 3）：旧目录判定与清理——白名单残留判"干净"且能清；roleplay 有 JSON 判"有数据"；
    // 删除保配置（paths.properties / onboarding.done 不动）。全程临时目录，不碰真数据。
    check("旧目录清理·判定（白名单干净 / JSON 有数据 / 删除保配置）") {
        val base = File(System.getProperty("java.io.tmpdir"), "whale-oldclean-smoke").apply { deleteRecursively(); mkdirs() }
        val clean = File(base, "clean").apply {
            File(this, "roleplay").mkdirs()
            File(this, "app.lock").writeText("x")
            File(this, "secret.key").writeText("x")
            File(this, "paths.properties").writeText("dataDir=D:\\new")
        }
        val dirty = File(base, "dirty").apply {
            File(this, "roleplay").mkdirs()
            File(this, "roleplay/chat.json").writeText("{}")
            File(this, "app.lock").writeText("x")
        }
        val c = com.mysticat.roleplay.data.DesktopOldDataCleanup
        val ok1 = c.onlyLeftovers(clean) && c.cleanLeftovers(clean) > 0 &&
            File(clean, "paths.properties").isFile && File(clean, "roleplay").exists() == false
        val ok2 = !c.onlyLeftovers(dirty) && c.deleteAllButConfig(dirty) &&
            File(dirty, "roleplay").exists() == false
        base.deleteRecursively()
        ok1 && ok2
    }

    // ── 使用手册（第 65 轮）：内容随包走，所以"资源到底有没有打进包"必须自检 ──────────────
    // 专治这类静默故障：手册页照常打开、只是空白（`manualText()` 的兜底文案不会崩，只有断言正文才看得出来）。
    // 解析那条把"目录"也一起守住——目录项与节一一对应是手册页唯一的导航，节标题空掉或重名就等于导航废了。
    check("使用手册·资源随包（读到真正文，不是兜底文案）") {
        val text = manualText()
        text.contains("使用手册") && !text.contains("手册内容缺失")
    }

    check("使用手册·解析（节数 ≥ 8 / 标题非空不重名 / 每节都有内容）") {
        val doc = parseManual(manualText())
        val titles = doc.sections.map { it.title }
        doc.sections.size >= 8 &&
            titles.all { it.isNotBlank() } &&
            titles.toSet().size == titles.size &&
            doc.sections.all { it.lines.isNotEmpty() }
    }

    // ── 破坏性项：全量备份/恢复的**端到端往返**（第 59 轮）─────────────────────────────
    // 台账里"聊天背景分端的恢复（import）链路端到端未验"就是这一条。查到的实况比"没验过"严重：
    // 判据是 Android 专属写法 ⇒ 导出的 `assets` 恒为空 ⇒ **换机恢复后图片全断链**，导出时却毫无异常。
    // ⚠️ 导入是**恢复语义**（先清空当前数据根再重建）⇒ 只在**空根**上跑：有角色或会话一律 SKIP。
    //    要真跑它＝把数据根换成沙箱目录：
    //      set APPDATA=%TEMP%\whale-smoke-sandbox   →   MysticatRoleplay.exe --smoke
    // 位置必须在所有"读真数据"的项之后 —— 它会把角色与会话清掉。
    val rootChars = runCatching { Repository.listCharacters() }.getOrDefault(emptyList())
    val rootConvs = runCatching { Repository.listConversationsForAll() }.getOrDefault(emptyList())
    if (rootChars.isEmpty() && rootConvs.isEmpty()) {
        check("全量备份往返（导出内嵌两份背景 / 恢复各回各端 / 空串不复活 / 消息图不断链）") {
            val phoneBytes = byteArrayOf(1, 2, 3, 4)
            val deskBytes = byteArrayOf(5, 6, 7)
            val chatBytes = byteArrayOf(8, 9)
            val phone = Repository.saveImageBytes(phoneBytes, "png")
            val desk = Repository.saveImageBytes(deskBytes, "png")
            val chat = Repository.saveImageBytes(chatBytes, "png")
            Repository.saveCharacter(
                CharacterCard(
                    id = "s-bk", name = "备份自检",
                    avatarUri = phone, backgroundUri = phone, backgroundUriDesktop = desk
                )
            )
            Repository.saveCharacter(
                CharacterCard(id = "s-bk2", name = "清过桌面背景", backgroundUri = phone, backgroundUriDesktop = "")
            )
            Repository.saveConversation(
                Conversation(
                    id = "s-bk-conv", characterId = "s-bk",
                    backgroundUri = phone, backgroundUriDesktop = desk,
                    messages = listOf(ChatMessage(role = "user", content = "带图", imageUri = chat))
                )
            )
            val text = Backup.exportJson()
            val assets = runCatching { Backup.parseBundle(text).assets }.getOrDefault(emptyMap())
            // 导出侧：三张图都得在 assets 里（桌面端的盘符路径同样算本地路径 —— 这里曾恒为空）
            val exported = listOf(phone, desk, chat).all { assets.containsKey(it) }
            val result = Backup.importJson(text)
            val back = Repository.getCharacter("s-bk")
            val back2 = Repository.getCharacter("s-bk2")
            val backConv = Repository.getConversation("s-bk-conv")
            fun restored(uri: String?, bytes: ByteArray): Boolean =
                uri != null && uri.isNotEmpty() && File(uri).isFile && File(uri).readBytes().contentEquals(bytes)
            val ok = exported && result.characters == 2 && result.conversations == 1 && result.images == 3 &&
                // 各回各端：两个字段得是**两张不同的图**（写错端 = 恢复后桌面端显示成手机端那张）
                back?.backgroundUri != null && back.backgroundUriDesktop != null &&
                back.backgroundUri != back.backgroundUriDesktop &&
                restored(back.backgroundUri, phoneBytes) && restored(back.backgroundUriDesktop, deskBytes) &&
                // 头像与手机端背景本是同一个文件：只落一次盘、两处引用同一条新路径
                back.avatarUri == back.backgroundUri &&
                // 空串不复活：该端明确"不要背景"，恢复后仍是"不要"（不是 null）
                back2?.backgroundUriDesktop == "" &&
                restored(backConv?.messages?.firstOrNull()?.imageUri, chatBytes)
            // 清场：删掉这次往返造出来的角色与会话（角色连带它的会话与图片引用）
            listOf("s-bk", "s-bk2").forEach { runCatching { Repository.deleteCharacter(it) } }
            runCatching { Repository.deleteConversation("s-bk-conv") }
            ok
        }
    } else {
        skip(
            "全量备份往返（导出内嵌两份背景 / 恢复各回各端 / 空串不复活 / 消息图不断链）",
            "需空数据根（导入＝恢复语义会清空现有数据），当前 ${rootChars.size} 张角色卡 / ${rootConvs.size} 个会话"
        )
    }

    println("")
    println("鲸鱼桌面版自检（--smoke）")
    println("  数据目录：${File(paths.filesRoot, "roleplay").absolutePath}")
    println("  缓存目录：${paths.cacheRoot.absolutePath}")
    println("  导出目录：${paths.exportDir.absolutePath}")
    println("  应用日志：${Platform.ui.appLogPath() ?: "未接上"}")
    println("")
    results.forEach { r ->
        val tag = when (r.state) {
            "OK" -> "[OK]  "
            "SKIP" -> "[SKIP]"
            else -> "[FAIL]"
        }
        println("  $tag ${r.name}${if (r.reason.isEmpty()) "" else "  —— ${r.reason}"}")
    }
    val failed = results.count { it.state == "FAIL" }
    val skipped = results.count { it.state == "SKIP" }
    val ran = results.size - skipped
    println("")
    println(
        if (failed == 0) "自检通过：$ran/$ran${if (skipped > 0) "（跳过 $skipped 项）" else ""}"
        else "自检失败：$failed/${results.size}"
    )

    // 收尾：把本次自检的轨迹也落一份，方便出问题时有据可查（不删，桌面日志本就不大）
    runCatching {
        File(paths.filesRoot, "smoke.log").writeText(
            results.joinToString("\n") { "${it.state} ${it.name}${if (it.reason.isEmpty()) "" else "  —— ${it.reason}"}" }
        )
    }
    return if (failed == 0) 0 else 1
}

/**
 * 供应商 MP3 → 要请求的 **PCM 目标格式**（自检用；与 DesktopAudioPlayer 内那份同口径）。
 * 关键点：声道数取**源声道数**，不强制降混（mp3spi 的越界坑，实测记录）。
 */
private fun pcmTargetOf(fmt: javax.sound.sampled.AudioFormat): javax.sound.sampled.AudioFormat {
    val channels = if (fmt.channels > 0) fmt.channels else 1
    val rate = if (fmt.sampleRate > 0f) fmt.sampleRate else 24000f
    return javax.sound.sampled.AudioFormat(
        javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
        rate,
        16,
        channels,
        channels * 2,
        rate,
        false
    )
}

/**
 * 生成一段**静音** PCM WAV 字节（自检用）。
 *
 * 为什么要自己拼：播放链路（`Clip` 起播）必须先有一份 PCM 音频才验得了，
 * 而项目里没有现成的音频资源；静音（而不是正弦波）是为了自检不发出任何声音。
 */
private fun silentWav(sampleRate: Int, channels: Int, millis: Int): ByteArray {
    val frames = sampleRate * millis / 1000
    val dataSize = frames * channels * 2
    val out = java.io.ByteArrayOutputStream(44 + dataSize)
    fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
    fun le32(v: Int) = out.write(
        byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte())
    )
    fun le16(v: Int) = out.write(byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte()))
    ascii("RIFF"); le32(36 + dataSize); ascii("WAVE")
    ascii("fmt "); le32(16); le16(1); le16(channels); le32(sampleRate)
    le32(sampleRate * channels * 2); le16(channels * 2); le16(16)
    ascii("data"); le32(dataSize)
    repeat(dataSize) { out.write(0) }
    return out.toByteArray()
}

/** 从 PNG 字节里读 IHDR 的宽高（自检用：确认出图尺寸真的是 1080×1920） */
private fun pngHeaderSize(png: ByteArray): Pair<Int, Int>? {    if (png.size < 24) return null
    fun be32(offset: Int): Int =
        ((png[offset].toInt() and 0xFF) shl 24) or ((png[offset + 1].toInt() and 0xFF) shl 16) or
            ((png[offset + 2].toInt() and 0xFF) shl 8) or (png[offset + 3].toInt() and 0xFF)
    return be32(16) to be32(20)
}
