package com.mysticat.roleplay.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mysticat.roleplay.data.CharacterCard
import java.io.File

/**
 * 平台服务注入点（注入优先、expect/actual 兜底）。
 *
 * 全部 UI 住 jvmSharedMain，SAF/Toast/相册/分享/权限/剪贴板/编解码这些平台能力
 * 从这里注入：宿主入口（Android=App.kt，桌面=desktopApp main）在启动时把
 * [Platform.ui] 覆盖成各端实现。选择器类方法必须在**组合期**调用（Android 侧
 * 内部要挂 ActivityResult 启动器），所以带 @Composable。
 *
 * [FallbackPlatformUi] 是兜底：宿主忘记注入也不崩——提示走 stdout、选择器一律返回"取消"。
 */
interface PlatformUi {
    // ────────────────────── 选择器（组合期创建，返回触发函数） ──────────────────────

    /** 选一张图片并复制进应用目录；回调本地路径，取消/失败回调 null */
    @Composable
    fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit

    /** 选一份字体文件并复制进应用目录（含可用性校验）；回调 (路径, 显示名)，取消/失败回调 (null, "") */
    @Composable
    fun rememberFontFilePicker(onPicked: (String?, String) -> Unit): () -> Unit

    /** 选一份 JSON/文本文件并读出 UTF-8 内容；取消/读取失败回调 null */
    @Composable
    fun rememberJsonFilePicker(onPicked: (String?) -> Unit): () -> Unit

    /**
     * 选一个**音频文件**并复制进应用目录（背景音乐用）；回调 (路径, 原文件名)，取消/失败回调 (null, "")。
     *
     * 落点是 `accounts/<id>/bgm/`（用户文件，不是缓存），格式闸门见 `data.bgmFormatRejection`
     * ——不支持时**必须**给出原因（桌面只认 mp3/wav），不能让它变成"选了却不出声"。
     */
    @Composable
    fun rememberAudioFilePicker(onPicked: (String?, String) -> Unit): () -> Unit

    /** 选一个保存位置并写出 JSON（角色卡导出用）；回调是否成功 */
    @Composable
    fun rememberJsonFileSaver(onSaved: (Boolean) -> Unit): (fileName: String, json: String) -> Unit

    /** 保存图片到系统相册（桌面：落盘到用户目录）；返回触发函数，参数是本地路径 */
    @Composable
    fun rememberGallerySaver(): (String) -> Unit

    /** 选一个 APK 安装包并安装（应用内更新用）；回调是否成功唤起安装 */
    @Composable
    fun rememberApkPicker(onResult: (Boolean) -> Unit): () -> Unit

    /** 请求麦克风权限；回调是否授予（桌面无权限概念，授予即 true） */
    @Composable
    fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit

    /** 返回键拦截（Android=系统返回键；桌面=Esc，由窗口壳转成同一回调） */
    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    /**
     * 软键盘是否在屏上（桌面恒 `false`，没有软键盘这一说）。
     *
     * 共享层里 `WindowInsets.ime` 与 `isImeVisible` **都看不到**——那两个只在 Android 侧源集有；
     * 而"括呼出键盘就出现"这类判断只有共享层的输入栏要用，所以由各端提供：Android＝IME 插入量的高度。
     */
    @Composable
    fun rememberKeyboardVisible(): Boolean = false

    /** 裁剪框比例：当前"屏幕"宽高（桌面=窗口客户区）；实现端组合期读取 */
    @Composable
    fun rememberScreenAspect(fallback: Float): Float

    /** 动态取色（Android 12 壁纸取色）；桌面实现返回 null，回退固定配色 */
    @Composable
    fun dynamicColorScheme(dark: Boolean): ColorScheme?

    /** 用户上传的自定义字体 → 字体族；失败/不支持返回 null（回退内置字族） */
    fun loadCustomFontFamily(path: String): FontFamily?

    /**
     * 「衬线」这一档真正用哪个字族（2026-09-17 新增）。
     *
     * 默认返回 null ＝"本平台直接用通用的 `FontFamily.Serif` 就行"。Android 正是如此（实测会落到
     * Noto Serif CJK，中文有衬线）；但**桌面（Skia）拿通用族解析中文挑不到中文衬线体**，
     * 用户反馈"衬线在 windows 中不对中文生效"，所以桌面实现会显式挑一个系统中文字体文件。
     */
    fun serifFontFamily(): FontFamily? = null

    /**
     * 「系统默认」这一档真正用哪个字族（2026-09-17 新增，方案①）。
     *
     * 默认返回 null ＝"用 `FontFamily.Default` 就行"，Android 正是如此（系统字体就是中文字体，
     * 没有挑的必要）。桌面实现显式挑**微软雅黑**，理由不是"现在挑不到雅黑"，而是**确定性**：
     * `FontFamily.Default` 是 Skia 的通用无衬线族，中文靠 fallback 链解析，落到哪个中文字体
     * 取决于系统装了哪些字体、系统语言、以及 Skia 自己的 fallback 表——同一份代码在别人机器上
     * 可能就落到宋体这类细笔画字体上（灰阶抗锯齿下最不好认）。挑文件就没有这个不确定性。
     *
     * ⚠ **本机实测：挑与不挑，中文正文逐像素一致**（本机 fallback 本来就是雅黑），
     * 所以这一档不要指望观感有变化——用户报的 #9"不清晰"主因是**「衬线＝宋体」的细笔画**与
     * Skia 无 hinting，不是这里。真正受影响的是**西文/数字**（雅黑的西文 vs Skia 默认西文，
     * 全图 0.8% 像素有差异）。
     *
     * ⚠ 只影响「系统默认」这一档；用户选了「衬线」或上传了自己的字体时**不受影响**
     * （见 `Theme.currentFontFamily` 的优先级：上传字体 > 内置档）。
     */
    fun sansFontFamily(): FontFamily? = null

    // ────────────────────── 系统能力（随时可调） ──────────────────────

    /** Toast 级提示（Android 实现内部切主线程；桌面换覆盖层提示） */
    fun toast(message: String, long: Boolean = false)

    /**
     * 把一段代码切到 UI 线程执行（Android=主线程 Looper，桌面=Swing EDT）；已在 UI 线程则直接跑。
     *
     * 给"在后台线程干活、结果要变成 Compose 状态"的场景用（BGM 起播：解码/落盘必须在
     * 后台，而 `mutableStateOf` 要在主线程写）。兜底实现直接同步调用——宿主没注入时不会丢状态。
     */
    fun runOnUiThread(block: () -> Unit) {
        block()
    }

    /**
     * 退出应用：Android 顶层返回键**二次确认**的第二次才走这里（用户 2026-09-24 反馈）。
     * 桌面默认空实现——关窗口属窗口壳的职责，Esc / 返回不该承担"退出应用"。
     */
    fun exitApp() {}

    /** 存图到系统相册；[uri] 可以是本地路径或 http(s) 直链 */
    suspend fun saveImageToGallery(uri: String): Boolean

    /** 字体文件可用性一级校验（文件头魔数 + 平台排版引擎试加载） */
    fun isUsableFontFile(path: String): Boolean

    /** 字体文件二级校验（渲染探针；桌面用 Font(ByteArray) 试构造代替） */
    suspend fun probeFontRenderable(path: String): Boolean

    /**
     * 复制文本到剪贴板。[feedback] 非空时给出"已复制"类提示——Android 13+ 系统自带
     * 复制提示，实现侧按平台口径决定是否再弹自己的 Toast（桌面始终弹）。
     */
    fun copyToClipboard(label: String, text: String, feedback: String? = null)

    // ────────────────────── 鼠标指针（桌面；Android 无鼠标） ──────────────────────

    /**
     * 「左右可拖」手柄的鼠标光标（用户反馈：拖侧边栏时指针要变成左右箭头     * 否则用户不知道那里能拖）。默认实现**原样返回**——Android 没有鼠标指针，套上也没意义。
     *
     * 为什么做成注入而不是在 [com.mysticat.roleplay.ui.DesktopShell] 里直接写
     * `pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(...)))`：那个手柄住在
     * `jvmSharedMain`，同一份代码 Android 也要编译，而 `java.awt.Cursor` 是 JVM 桌面专有的。
     */
    @Composable
    fun horizontalResizeCursorModifier(): Modifier = Modifier

    /** 系统分享纯文本（桌面落为复制 + 提示） */
    fun shareText(title: String, text: String)

    /** 把本地文件交给系统分享面板（备份 JSON 导出用；Android 走 FileProvider） */
    fun shareFile(file: File, mime: String, title: String)

    /** 渲染竖版图片卡（1080×1920 PNG）并落盘到临时目录；失败返回 null */
    suspend fun buildCardImageFile(card: CharacterCard): File?

    /**
     * 生成 PNG 角色卡：头像图（无头像时用渲染的竖版长图）作载体，
     * 把 [json] 嵌进 tEXt chunk（编解码见 data.PngCardCodec）后落盘到临时目录；失败返回 null。
     */
    suspend fun buildPngCardFile(card: CharacterCard, json: String): File? = null

    /** 把渲染好的图片卡交给系统分享面板 */
    fun shareImageFile(file: File)

    /** 图片解码为共享的 [ImageBitmap]（裁剪逻辑共享、解码平台实现）；失败返回 null */
    suspend fun decodeImageBitmap(uri: String, maxDim: Int = 2048): ImageBitmap?

    /** 把 [ImageBitmap] 编码为 PNG 字节 */
    suspend fun encodePng(bitmap: ImageBitmap): ByteArray?

    // ────────────────────── 应用信息与系统集成 ──────────────────────

    fun appVersionName(): String
    fun appVersionCode(): Int
    fun appPackageName(): String
    fun isDebuggableBuild(): Boolean
    fun hasMicPermission(): Boolean

    /** 打开外部链接；返回是否成功唤起 */
    fun openUrl(url: String): Boolean

    /** 崩溃现场记录（Android=CrashGuard；桌面=日志文件） */
    suspend fun readCrashLog(): String?
    fun clearCrashLog()
    fun noteBreadcrumb(tag: String)

    /**
     * 应用运行日志：「异常记录」页用。
     * 桌面＝`whale.log` 尾部（卡死但没崩时唯一的取证物）；Android＝null——
     * 它那边有 logcat 与 CrashGuard 两条路，日志不上界面。
     */
    suspend fun readAppLog(): String? = null

    /** 应用日志文件路径（桌面＝真实路径；Android＝null）。写进界面，用户能自己去拿整份文件。 */
    fun appLogPath(): String? = null

    /** 崩溃自测（调试包长按版本号故意抛异常，验证崩溃守卫链路；release 不可达） */
    fun markCrashSelfTest()

    // ────────────────────── 平台标识与更新源口径 ──────────────────────

    /**
     * 平台标识："android" / "desktop"。
     * 别拿 [appPackageName] 当判断依据——两端包名是同一个（`com.mysticat.roleplay`）。
     */
    fun platformId(): String

    /**
     * `version.json` 里本平台读哪个节点：Android＝null（顶层平铺字段），
     * 桌面＝`"windows"`（与 Android 字段并列）。null = 读顶层。
     */
    fun updateJsonNode(): String?

    /**
     * 更新包就绪后要用户双击的"替换脚本"文件名（桌面＝便携包替换脚本，Android 无此物返回空串）。
     * 放在这里是为了让界面文案不必硬编码一个只有桌面存在的文件名。
     */
    fun updateScriptName(): String

    /**
     * 更新分流（桌面安装程序形态）：程序目录不可写（装进 Program Files）时改走
     * "下载新安装器"；便携包 / 装在用户可写目录维持"下载 zip → 替换脚本"。
     * Android 恒为 false（接口默认值），桌面实现按 DesktopUpdater.appDirWritable() 探测。
     */
    fun updatesViaInstaller(): Boolean = false

    // ────────────────────── 应用内更新（Android=ApkUpdater；桌面=便携包更新器） ──────────────────────

    suspend fun downloadUpdate(
        candidates: List<String>,
        expectedSha256: String,
        onProgress: (done: Long, total: Long) -> Unit
    ): Result<File>

    fun needsInstallPermission(): Boolean
    fun requestInstallPermission(): Boolean
    fun installUpdate(apk: File): Boolean
    fun readUpdatePackageVersion(apk: File): Triple<String, Int, String>?

    // ────────────────────── 桌面专属三件（1.0.2；Android 用不到，恒 false） ──────────────────────

    /**
     * 拉起"等本进程退出→替换→重启"的更新脚本并返回 true；调用方随后自行优雅退出。
     * 失败（false）时界面提示脚本路径让用户手动双击。
     */
    fun launchUpdateScript(scriptPath: String): Boolean = false

    /** 在系统文件管理器里定位一个文件/目录（旧目录提示、更新脚本"稍后手动装"） */
    fun revealFile(path: String): Boolean = false

    /** 删除旧数据目录（弹层里用户对着写明路径的文案点确认后才调；锚点配置文件除外） */
    fun deleteOldDataDir(): Boolean = false
}

/** 注入点：宿主入口在启动时覆盖（App.kt / desktopApp main） */
object Platform {
    var ui: PlatformUi = FallbackPlatformUi()
}

/** 便捷入口：UI 里的 Toast 调用点统一走这里（保持一句话调用风格） */
fun showToast(message: String, long: Boolean = false) = Platform.ui.toast(message, long)

/**
 * 无参 ViewModel 的**跨平台**工厂（新增，桌面回归的根因）。
 *
 * 为什么每个页面都必须显式传 factory：桌面（非 Android）的 `ViewModelProvider.Factory`
 * **只有** `create(KClass, CreationExtras)` 一个方法，默认实现直接抛
 * `UnsupportedOperationException`；而 `viewModel()` 不传 factory 时用的是宿主默认工厂
 * （桌面走 `SavedStateViewModelFactory`，它转手调的就是那个抛异常的默认实现）。
 * 于是**裸 `viewModel()` 在桌面必崩**——实测现象：桌面上登录进主界面立刻落到
 * CharacterListScreen（默认 tab=2）就抛异常。Android 上有反射式默认工厂，所以一直没暴露。
 *
 * 用 `viewModelFactory { initializer { ... } }`（`InitializerViewModelFactory` 两端都实现了
 * `create(KClass, CreationExtras)`）显式给出构造方式即可，与 ChatViewModel/CharacterEditorViewModel
 * 早先的写法一致。
 */
inline fun <reified T : ViewModel> noArgViewModelFactory(noinline build: () -> T): ViewModelProvider.Factory =
    viewModelFactory { initializer { build() } }


/**
 * 崩溃 breadcrumbs 的共享门面。
 * 原来各页面直接调 `CrashGuard.note(...)`（Android 专有），现在改调这里：
 * Android 实现转发给 CrashGuard，桌面先落 WhaleLog/忽略。
 */
object CrashNote {
    fun note(tag: String) = Platform.ui.noteBreadcrumb(tag)
}

/** 字体文件头魔数检查（sfnt/true/OTTO/ttcf/typ1）——纯字节逻辑，两端共用 */
internal fun fontMagicOk(f: File): Boolean = runCatching {
    if (f.length() < 12) return false
    java.io.RandomAccessFile(f, "r").use { raf ->
        val tag = ByteArray(4).also { raf.readFully(it) }
        val magic = ((tag[0].toInt() and 0xFF) shl 24) or ((tag[1].toInt() and 0xFF) shl 16) or
            ((tag[2].toInt() and 0xFF) shl 8) or (tag[3].toInt() and 0xFF)
        magic == 0x00010000 || magic == 0x74727565 || // 0x00010000 / 'true'
            magic == 0x4F54544F || magic == 0x74746366 ||    // 'OTTO' / 'ttcf'
            magic == 0x74797031                              // 'typ1'
    }
}.getOrDefault(false)

/** 有上限地读取流，超限返回 null（不信任来源报的 SIZE） */
internal fun readAtMost(input: java.io.InputStream, max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** 按文件头猜图片类型：返回 (扩展名, MIME)。猜不出就按 jpg（大多数生图服务返回的就是 jpeg） */
internal fun sniffImageType(bytes: ByteArray): Pair<String, String> = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "png" to "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
        "jpg" to "image/jpeg"
    bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" ->
        "webp" to "image/webp"
    else -> "jpg" to "image/jpeg"
}

/**
 * 兜底实现：宿主没注入时保证不崩。
 * 由 object 改为 **open class**：桌面实现 [DesktopPlatformUi] 继承它 * 只覆盖自己真正实现得出来的那些方法，其余继续走兜底——不必为没实现的能力写一遍空壳。
 */
open class FallbackPlatformUi : PlatformUi {
    override fun toast(message: String, long: Boolean) {
        println("[whale-toast] $message")
    }

    @Composable
    override fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit = { onPicked(null) }

    @Composable
    override fun rememberFontFilePicker(onPicked: (String?, String) -> Unit): () -> Unit = { onPicked(null, "") }

    @Composable
    override fun rememberJsonFilePicker(onPicked: (String?) -> Unit): () -> Unit = { onPicked(null) }

    @Composable
    override fun rememberAudioFilePicker(onPicked: (String?, String) -> Unit): () -> Unit =
        { onPicked(null, "") }

    @Composable
    override fun rememberJsonFileSaver(onSaved: (Boolean) -> Unit): (String, String) -> Unit =
        { _, _ -> onSaved(false) }

    @Composable
    override fun rememberGallerySaver(): (String) -> Unit = { }

    @Composable
    override fun rememberApkPicker(onResult: (Boolean) -> Unit): () -> Unit = { onResult(false) }

    @Composable
    override fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {}

    @Composable
    override fun rememberScreenAspect(fallback: Float): Float = fallback

    @Composable
    override fun dynamicColorScheme(dark: Boolean): ColorScheme? = null

    override suspend fun saveImageToGallery(uri: String): Boolean = false

    override fun isUsableFontFile(path: String): Boolean {
        val f = File(path)
        return f.exists() && f.length() > 0L && fontMagicOk(f)
    }

    override suspend fun probeFontRenderable(path: String): Boolean = isUsableFontFile(path)

    override fun loadCustomFontFamily(path: String): FontFamily? = null

    override fun copyToClipboard(label: String, text: String, feedback: String?) {
        println("[whale-clipboard] $label${if (feedback != null) "（$feedback）" else ""}")
    }

    override fun shareText(title: String, text: String) {
        copyToClipboard(title, text)
    }

    override fun shareFile(file: File, mime: String, title: String) {}

    override suspend fun buildCardImageFile(card: CharacterCard): File? = null

    override fun shareImageFile(file: File) {}

    override suspend fun decodeImageBitmap(uri: String, maxDim: Int): ImageBitmap? = null

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray? = null

    override fun appVersionName(): String = "未知"
    override fun appVersionCode(): Int = 0
    override fun appPackageName(): String = "com.mysticat.roleplay"
    override fun platformId(): String = "unknown"
    override fun updateJsonNode(): String? = null
    override fun updateScriptName(): String = ""
    override fun isDebuggableBuild(): Boolean = false
    override fun hasMicPermission(): Boolean = false
    override fun openUrl(url: String): Boolean = false

    override suspend fun readCrashLog(): String? = null
    override fun clearCrashLog() {}
    override fun noteBreadcrumb(tag: String) {}
    override fun markCrashSelfTest() {}

    override suspend fun downloadUpdate(
        candidates: List<String>,
        expectedSha256: String,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = Result.failure(IllegalStateException("此平台的更新器尚未接入"))

    override fun needsInstallPermission(): Boolean = false
    override fun requestInstallPermission(): Boolean = false
    override fun installUpdate(apk: File): Boolean = false
    override fun readUpdatePackageVersion(apk: File): Triple<String, Int, String>? = null
}
