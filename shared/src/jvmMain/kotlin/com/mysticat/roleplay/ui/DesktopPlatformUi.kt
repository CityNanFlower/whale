package com.mysticat.roleplay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.mysticat.roleplay.data.DesktopOldDataCleanup
import com.mysticat.roleplay.data.DesktopRelaunch
import com.mysticat.roleplay.data.DesktopUpdater
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.Voice
import com.mysticat.roleplay.data.bgmFormatRejection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.util.Locale
import java.util.Properties

/**
 * 桌面端平台实现（M4 第 21 轮起；平台替身清单见 Platform.kt）。
 *
 * 继承 [FallbackPlatformUi]（M4 起由 object 改为 open class），**只覆盖桌面实现得出来的**；
 * 还没实现的继续走兜底，兜底行为是"提示 + 返回取消/失败"，不会静默假装成功。
 * 第 22 轮补齐了图片卡渲染（[buildCardImageFile]）——更新器仍是 M5 的活。
 */
class DesktopPlatformUi(
    /** 「保存图片到相册」在桌面的落点：用户图片目录下的应用文件夹 */
    private val exportDir: File,
    /** 缓存目录：图片卡渲染的临时落盘地（`%LOCALAPPDATA%\MysticatRoleplay\cache`） */
    private val cacheDir: File
) : FallbackPlatformUi() {

    // ────────────────────── 选择器 ──────────────────────

    @Composable
    override fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        return {
            scope.launch {
                // 对话框必须在 UI 线程（模态框自身泵事件，Compose 照常重绘）
                val picked = pickFile("选择图片", IMAGE_EXTS, "图片文件")
                if (picked == null) {
                    onPicked(null)
                    return@launch
                }
                // 与 Android 同一口径的大小闸门：先看文件长度（便宜），再按 64MB 上限读
                if (picked.length() > MAX_IMAGE_BYTES) {
                    toast(tooLargeHint(picked.length()), long = true)
                    onPicked(null)
                    return@launch
                }
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = FileInputStream(picked).use { readAtMost(it, MAX_IMAGE_BYTES) }
                            ?: return@runCatching null
                        val (ext, _) = sniffImageType(bytes)
                        Repository.saveImageBytes(bytes, ext)
                    }.getOrNull()
                }
                if (saved == null) toast("这张图片读取失败，换一张试试", long = true)
                onPicked(saved)
            }
        }
    }

    @Composable
    override fun rememberFontFilePicker(onPicked: (String?, String) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        return {
            scope.launch {
                val picked = pickFile("选择字体文件", FONT_EXTS, "字体文件")
                if (picked == null) {
                    onPicked(null, "")
                    return@launch
                }
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        if (!picked.isFile || picked.length() == 0L || !fontMagicOk(picked)) return@runCatching null
                        val ext = picked.extension.lowercase(Locale.ROOT).ifBlank { "ttf" }
                        Repository.saveFontBytes(picked.readBytes(), ext)
                    }.getOrNull()
                }
                if (saved == null) {
                    toast("这个字体文件不可用（格式不支持或文件损坏）", long = true)
                    onPicked(null, "")
                } else {
                    onPicked(saved, picked.nameWithoutExtension)
                }
            }
        }
    }

    @Composable
    override fun rememberAudioFilePicker(onPicked: (String?, String) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        return {
            scope.launch {
                // 文件框的类型过滤直接用**本平台真正支持的扩展名**（mp3/wav）——
                // 用户在框里就看不到 ogg/flac，比选完再报错更省事
                val exts = Voice.supportedAudioExtensions.sorted()
                val picked = pickFile("选择音频文件", exts, "音频文件")
                if (picked == null) {
                    onPicked(null, "")
                    return@launch
                }
                bgmFormatRejection(picked.name)?.let {
                    toast(it, long = true)
                    onPicked(null, "")
                    return@launch
                }
                if (picked.length() > MAX_AUDIO_BYTES) {
                    toast("音频文件太大了（上限 ${MAX_AUDIO_BYTES / 1024 / 1024} MB），换一个或先压缩", long = true)
                    onPicked(null, "")
                    return@launch
                }
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        Repository.saveBgmFile(picked.readBytes(), picked.extension.lowercase(Locale.ROOT).ifBlank { "mp3" })
                    }.getOrNull()
                }
                if (saved == null) {
                    toast("这个音频文件读不了，换一个试试", long = true)
                    onPicked(null, "")
                } else {
                    onPicked(saved, picked.name)
                }
            }
        }
    }

    @Composable
    override fun rememberJsonFilePicker(onPicked: (String?) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        return {
            scope.launch {
                // png 一并放进来（台账 11）：PNG 角色卡与 JSON 走同一个导入入口
                val picked = pickFile("选择角色卡文件", listOf("json", "txt", "png"), "角色卡 JSON / PNG")
                if (picked == null) {
                    onPicked(null)
                    return@launch
                }
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        // PNG 卡（tEXt chara）抽出 JSON，其余按 UTF-8 文本（JSON）给出
                        CardImport.cardJsonFromBytes(picked.readBytes())
                    }.getOrNull()
                }
                if (text == null) toast("文件读取失败（PNG 里没有角色卡数据，或 JSON 无法解析）", long = true)
                onPicked(text)
            }
        }
    }

    @Composable
    override fun rememberJsonFileSaver(onSaved: (Boolean) -> Unit): (String, String) -> Unit {
        val scope = rememberCoroutineScope()
        return { fileName, json ->
            scope.launch {
                val target = pickSaveFile("导出 JSON", fileName, "json")
                if (target == null) {
                    onSaved(false)
                    return@launch
                }
                val ok = withContext(Dispatchers.IO) {
                    runCatching { target.writeText(json, Charsets.UTF_8); true }.getOrDefault(false)
                }
                if (ok) toast("已导出到 ${target.absolutePath}")
                onSaved(ok)
            }
        }
    }

    @Composable
    override fun rememberGallerySaver(): (String) -> Unit {
        val scope = rememberCoroutineScope()
        return { uri ->
            scope.launch {
                val ok = desktopSaveImageToGallery(uri, exportDir)
                if (ok) {
                    toast("已保存到 ${exportDir.absolutePath}（桌面无系统相册）", long = true)
                } else {
                    toast("保存失败，检查一下目标文件夹是否可写", long = true)
                }
            }
        }
    }

    @Composable
    override fun rememberApkPicker(onResult: (Boolean) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        // 桌面没有"选 APK 安装"这条路，但"我已经从群里下好了便携包"是真实场景：
        // 这里让用户选一个 zip，校验后走与自动更新同一套 staging（M5）
        return {
            scope.launch {
                val picked = pickFile("选择便携包（zip）", listOf("zip"), "鲸鱼便携包")
                if (picked == null) {
                    onResult(false)
                    return@launch
                }
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val version = DesktopUpdater.readPackageVersion(picked)
                            ?: throw IllegalStateException("这个 zip 里没有 build-info.json，不是鲸鱼的便携包")
                        if (version.first != appPackageName()) {
                            throw IllegalStateException("这个包不是鲸鱼（包名 ${version.first}）")
                        }
                        val script = DesktopUpdater.stage(picked, ProcessHandle.current().pid())
                        version to script
                    }.onSuccess { (version, script) ->
                        // 台账 2：就绪后不再 toast 让用户翻资源管理器，交给「版本与安全」弹窗的确认层
                        DesktopUpdatePrompt.pendingScript = script.absolutePath
                        DesktopUpdatePrompt.pendingVersion = version.third
                    }.onFailure { toast("无法使用所选便携包：${it.message}", long = true) }
                        .isSuccess
                }
                onResult(ok)
            }
        }
    }

    @Composable
    override fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit = {
        // 桌面没有运行时权限门槛（是否真的有麦克风由录音实现判断）
        onResult(true)
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        if (!enabled) return
        DisposableEffect(onBack) {
            DesktopBack.push(onBack)
            onDispose { DesktopBack.remove(onBack) }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun rememberScreenAspect(fallback: Float): Float {
        val size = LocalWindowInfo.current.containerSize
        if (size.width <= 0 || size.height <= 0) return fallback
        // ⚠ 语义是「宽高比」（ImageTools 里 frameW = frameH * aspect），这里**必须**是 宽/高。
        // 2026-09-17 修正：原先写成 height/width，桌面横屏窗口（1440×900）因此得到 0.625 的**竖框**，
        // 而 Android 侧一直返回宽/高（约 0.46 的竖框，对手机是正确的）——
        // 表现就是用户报的"桌面端聊天背景尺寸和手机端不一样"：用竖框裁出来的图铺到横向分栏上被 Crop 放大。
        return size.width.toFloat() / size.height.toFloat()
    }

    @Composable
    override fun dynamicColorScheme(dark: Boolean): androidx.compose.material3.ColorScheme? = null

    override fun loadCustomFontFamily(path: String): FontFamily? = runCatching {
        val f = File(path)
        if (!f.isFile || f.length() == 0L) return null
        // 桌面用 CMP 通用的 Font(File)（两端同一套代码，Android 侧同样走这条）
        FontFamily(Font(f))
    }.getOrNull()

    /**
     * 「衬线」在 Windows 上要显式挑字体（见 [PlatformUi.serifFontFamily] 的说明）。
     *
     * 候选顺序＝"最像宋体正文"优先：宋体 → 思源宋体/Noto Serif CJK（若装了）→ 仿宋 → 楷体。
     * 用系统字体文件而不是按名字找：Skia 的 `Typeface.makeFromName` 对中文名/英文名混着来不好使，
     * 而 `Font(File)` 是这套代码里已经验证过的加载路径（自检里就是用 msyh.ttc 验的，.ttc 也吃）。
     * 一个都挑不到就返回 null，让调用方回退通用族（宁可没衬线，也不要崩）。
     */
    override fun serifFontFamily(): FontFamily? = DesktopSerifCjk.family

    override fun sansFontFamily(): FontFamily? = DesktopSansCjk.family

    override suspend fun probeFontRenderable(path: String): Boolean =
        withContext(Dispatchers.IO) { fontMagicOk(File(path)) && loadCustomFontFamily(path) != null }

    // ────────────────────── 系统能力 ──────────────────────

    override fun toast(message: String, long: Boolean) {
        DesktopToasts.show(message, long)
    }

    /** 桌面 UI 线程＝Swing EDT（与 Compose Desktop 同一根事件队列） */
    override fun runOnUiThread(block: () -> Unit) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) block()
        else javax.swing.SwingUtilities.invokeLater(block)
    }

    override suspend fun saveImageToGallery(uri: String): Boolean =
        desktopSaveImageToGallery(uri, exportDir)

    override fun copyToClipboard(label: String, text: String, feedback: String?) {
        val ok = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            true
        }.getOrDefault(false)
        toast(if (ok) (feedback ?: "已复制到剪贴板") else "复制失败：剪贴板不可用")
    }

    override fun shareText(title: String, text: String) {
        copyToClipboard(title, text, "已复制到剪贴板（桌面无系统分享面板）")
    }

    override fun shareFile(file: File, mime: String, title: String) = revealExported(file)

    override fun shareImageFile(file: File) = revealExported(file)

    override suspend fun decodeImageBitmap(uri: String, maxDim: Int): ImageBitmap? =
        desktopDecodeImageBitmap(uri, maxDim)

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray? = desktopEncodePng(bitmap)

    /** 角色卡图片卡：1080×1920 竖版长图，落 `缓存目录/card-export/`（与 Android 侧同一版式，见 DesktopCardExport.kt） */
    override suspend fun buildCardImageFile(card: com.mysticat.roleplay.data.CharacterCard): File? =
        desktopBuildCardImageFile(card, cacheDir)

    /** PNG 角色卡（台账 11）：头像作载体嵌 JSON，落 `缓存目录/card-export/`，分享/保存与图片卡同链路 */
    override suspend fun buildPngCardFile(card: com.mysticat.roleplay.data.CharacterCard, json: String): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = pngCardBytes(card, json) ?: return@runCatching null
                val dir = File(cacheDir, "card-export").apply { mkdirs() }
                val file = File(dir, "pngcard-${System.currentTimeMillis()}.png")
                file.writeBytes(bytes)
                file
            }.getOrNull()
        }

    // ────────────────────── 鼠标指针（第 58 轮） ──────────────────────

    /**
     * 可拖宽手柄上给**水平双向箭头**（AWT 的 `E_RESIZE_CURSOR`）——
     * 与窗口八方向缩放条同一套做法（`DesktopTitleBar` 里那份是按方向逐个映射，这里只有"左右"一种）。
     *
     * 光标实例做成**共享一份**：`PointerIcon` 在桌面就是包一个 AWT `Cursor`，
     * 没有 equals、按引用比较，每次重组新建一个的话 `pointerHoverIcon` 的元素每帧都不相等。
     */
    private val resizeCursorIcon: PointerIcon by lazy {
        PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
    }

    @Composable
    override fun horizontalResizeCursorModifier(): Modifier =
        Modifier.pointerHoverIcon(resizeCursorIcon)

    // ────────────────────── 应用信息与系统集成 ──────────────────────

    override fun appVersionName(): String = buildInfo.getProperty("versionName") ?: DESKTOP_FALLBACK_VERSION

    override fun appVersionCode(): Int = buildInfo.getProperty("versionCode")?.toIntOrNull() ?: 1

    override fun appPackageName(): String = "com.mysticat.roleplay"

    override fun platformId(): String = "desktop"

    /** 桌面读 `version.json` 的 `windows` 节点（与 Android 的顶层字段并列） */
    override fun updateJsonNode(): String? = "windows"

    override fun updateScriptName(): String = DesktopUpdater.UPDATE_SCRIPT_NAME

    /** 安装程序形态（M13）：程序目录不可写（Program Files）时更新改走"下载新安装器" */
    override fun updatesViaInstaller(): Boolean = DesktopUpdater.appDirWritable() == false

    /** `:desktopApp:run` 传 `-Dwhale.debug=true`；打包产物不带这个属性（崩溃自测只在开发运行下可达） */
    override fun isDebuggableBuild(): Boolean = System.getProperty("whale.debug") == "true"

    override fun hasMicPermission(): Boolean = true

    override fun openUrl(url: String): Boolean = runCatching {
        if (url.isBlank() || !Desktop.isDesktopSupported()) return false
        Desktop.getDesktop().browse(URI(url))
        true
    }.getOrDefault(false)

    override suspend fun readCrashLog(): String? = DesktopCrashLog.read()

    override fun clearCrashLog() = DesktopCrashLog.clear()

    override fun noteBreadcrumb(tag: String) = DesktopCrashLog.note(tag)

    /** 应用流水日志（M9）：卡死类问题的唯一取证物，见 [DesktopLog] 的说明 */
    override suspend fun readAppLog(): String? = DesktopLog.readTail()

    override fun appLogPath(): String? = DesktopLog.path()

    override fun markCrashSelfTest() {
        DesktopCrashLog.markSelfTest()
    }

    // ────────────────────── 应用内更新（M5：便携包替换） ──────────────────────

    /** 下载更新包（镜像优先 + SHA-256 校验），落到缓存目录；按 URL 扩展名落盘（.exe＝安装器） */
    override suspend fun downloadUpdate(
        candidates: List<String>,
        expectedSha256: String,
        onProgress: (done: Long, total: Long) -> Unit
    ): Result<File> = runCatching {
        // 扩展名决定 installUpdate 的分流（exe＝拉起安装器，zip＝解压替换），去掉查询串再判
        val first = candidates.firstOrNull()?.substringBefore('?') ?: ""
        val ext = if (first.endsWith(".exe")) "exe" else "zip"
        DesktopUpdater.download(candidates, expectedSha256, File(cacheDir, "update"), onProgress, fileExt = ext)
    }

    /** 读包内 `build-info.json`（对应 Android 侧读 APK Manifest）：返回 (包名, versionCode, versionName) */
    override fun readUpdatePackageVersion(apk: File): Triple<String, Int, String>? =
        DesktopUpdater.readPackageVersion(apk)

    /**
     * 桌面的"安装"＝解压暂存 + 生成替换脚本，然后交给「版本与安全」弹窗的确认层
     * （台账 2：默认"立即安装"＝拉起脚本＋退出，脚本会等进程退出、覆盖、自动重启；
     * 用户选"稍后"才落回旧的"去资源管理器里双击"口径）。
     */
    override fun installUpdate(apk: File): Boolean {
        // 安装器形态（M13）：下载到的是 jpackage 安装器 exe，拉起它走系统安装向导
        // （UAC 由安装器自己要），不再走"解压 + 替换脚本"。
        if (apk.isFile && apk.extension.equals("exe", ignoreCase = true)) {
            val launched = DesktopUpdater.launchInstaller(apk)
            toast(
                if (launched) "安装器已启动：按向导完成安装（弹出管理员确认时选「是」）。" +
                    "安装前请先退出本程序，装完再打开新版本（数据不受影响）"
                else "无法启动安装器，请手动双击运行：${apk.absolutePath}",
                long = true
            )
            return launched
        }
        val staged = runCatching { DesktopUpdater.stage(apk, ProcessHandle.current().pid()) }
        return staged.fold(
            onSuccess = { script ->
                DesktopUpdatePrompt.pendingScript = script.absolutePath
                true
            },
            onFailure = { t ->
                // 开发运行（`:desktopApp:run`）没有便携程序目录，只能提示手工替换
                toast("无法自动替换程序目录：${t.message}。便携包已下载到 ${apk.absolutePath}", long = true)
                false
            }
        )
    }

    /** 拉起替换脚本（台账 2）；失败返回 false，调用方提示脚本路径 */
    override fun launchUpdateScript(scriptPath: String): Boolean =
        DesktopRelaunch.launchDetached(File(scriptPath))

    override fun revealFile(path: String): Boolean = revealInFileManager(File(path))

    /** 旧目录弹层里用户确认后的删除（写明路径的二次确认在共享层弹窗里） */
    override fun deleteOldDataDir(): Boolean {
        val dir = DesktopOldDirNotice.dirWithData ?: return false
        return runCatching { DesktopOldDataCleanup.deleteAllButConfig(File(dir)) }.getOrDefault(false)
    }

    // ────────────────────── 内部 ──────────────────────

    /** 桌面没有分享面板：把文件定位到资源管理器里，并给出路径提示（桌面端差异口径） */
    private fun revealExported(file: File) {
        val opened = revealInFileManager(file)
        toast(
            if (opened) "已导出：${file.absolutePath}"
            else "已导出到 ${file.absolutePath}",
            long = true
        )
    }

    private fun tooLargeHint(bytes: Long) =
        "图片太大了（${bytes / 1024 / 1024} MB，上限 ${MAX_IMAGE_BYTES / 1024 / 1024} MB），换一张或先裁剪"

    /** 版本元数据来自 :desktopApp 生成的 `whale-build.properties`（版本源＝libs.versions.toml）。
     *  ⚠ 必须用显式 `::class.java`：写在 `apply {}` 里的 `javaClass` 绑定的是 apply 接收者（Properties，
     *  启动类加载器），看不到应用类路径，getResourceAsStream 恒为 null——第 21 轮起坏到第 45 轮，
     *  一直被恰好等于真实版本的回退值 "1.0.0" 掩盖，1.0.1 才显形。 */
    private val buildInfo: Properties by lazy {
        Properties().apply {
            runCatching {
                DesktopPlatformUi::class.java.getResourceAsStream("/whale-build.properties")
                    ?.use { load(it) }
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024

        /** 上传背景音乐的上限（与 Android 侧同一口径的理由：整段读进内存要有边界） */
        const val MAX_AUDIO_BYTES = 48L * 1024 * 1024
        const val DESKTOP_FALLBACK_VERSION = "1.0.0"
        val IMAGE_EXTS = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        val FONT_EXTS = listOf("ttf", "otf", "ttc")
    }
}

/**
 * 桌面「衬线」字族的候选查找（2026-09-17，用户反馈"衬线在 windows 中对中文不生效"）。
 *
 * 结果缓存（`by lazy`）：字体文件不会变，而 [Platform.ui] 的字族查询在主题重组时会被调用。
 */
private object DesktopSerifCjk {
    /** 最像宋体正文的排前面；思源/Noto 排前面是因为装了它的机器上观感通常更好 */
    private val FILES = listOf(
        "NotoSerifCJKsc-Regular.otf",
        "SourceHanSerifSC-Regular.otf",
        "simsun.ttc",   // 宋体（中文 Windows 基本一定在）
        "simfang.ttf",  // 仿宋
        "simkai.ttf"    // 楷体
    )

    val family: FontFamily? by lazy {
        val dir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        for (name in FILES) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) continue
            if (!fontMagicOk(f)) continue
            runCatching { FontFamily(Font(f)) }.getOrNull()?.let { return@lazy it }
        }
        null
    }
}

/**
 * 桌面「系统默认」档实际用的中文字族（2026-09-17，用户反馈 #9"字体不清晰"，方案①）。
 *
 * 为什么要显式挑文件：`FontFamily.Default` 是 Skia 的**通用无衬线族**，中文靠 fallback 链解析，
 * 落到哪个中文字体取决于系统字体集合/语言/ Skia 的 fallback 表——不受我们控制。挑文件＝确定性。
 * 选微软雅黑是因为它是 Windows 中文界面的事实标准：笔画粗细均匀、x-height 大，小字号下好认。
 *
 * ⚠ 本机实测（第 27 轮）：不挑时 fallback 本来就落到雅黑，**中文正文逐像素一致**——所以
 * 这一档不要指望观感变化，它只在"别人机器上 fallback 跑偏"时才起作用。
 *
 * 候选顺序：微软雅黑 → 等线 → 黑体。挑不到返回 null，调用方回退 `FontFamily.Default`。
 * 与 [DesktopSerifCjk] 同样缓存（`by lazy`）——主题重组时会反复问。
 */
private object DesktopSansCjk {
    private val FILES = listOf(
        "msyh.ttc",     // 微软雅黑（Vista 起自带，中文界面的事实标准）
        "Deng.ttf",     // 等线（Win10 起自带，比雅黑更轻）
        "simhei.ttf"    // 黑体（老系统兜底）
    )

    val family: FontFamily? by lazy {
        val dir = File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
        for (name in FILES) {
            val f = File(dir, name)
            if (!f.isFile || f.length() == 0L) continue
            if (!fontMagicOk(f)) continue
            runCatching { FontFamily(Font(f)) }.getOrNull()?.let { return@lazy it }
        }
        null
    }
}
