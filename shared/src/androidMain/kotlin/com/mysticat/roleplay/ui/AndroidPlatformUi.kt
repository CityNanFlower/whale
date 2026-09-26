package com.mysticat.roleplay.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler as ActivityBackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.mysticat.roleplay.FontProbe
import com.mysticat.roleplay.CrashGuard
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.ApkUpdater
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.CharacterCard
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.bgmFormatRejection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 平台服务的 Android 实现。App.kt 启动时 `Platform.ui = AndroidPlatformUi(...)` 注入。
 * 各实现与拆分前（Common.kt 的 SAF/MediaStore 段、CardExport 的分享/出图段、VersionSecurity 的
 * ApkUpdater 段）逐行对应，只有"从私有函数变成接口实现"这一层变化。
 */
class AndroidPlatformUi(private val appContext: Context) : PlatformUi {

    /**
     * 这里的 `appContext` 是 **Application**（App.kt 注入 `this`），不是 Activity。
     * 从 Activity 之外起 Activity **必须**带 NEW_TASK，否则 AMS 直接抛 `AndroidRuntimeException`
     * （跨平台拆分把 Activity 换成 Application 时漏了这一步：异常又被 runCatching 吞掉，
     * 表现成"点了不跳转/分享没反应"——2026-09-21 用户反馈「使用文档」「价格与模型页」点不动）。
     * 本项目其它从 app context 起 Activity 的地方（CrashGuard / ApkUpdater / FontProbe）也都带这个 flag。
     */
    private val NEW_TASK = Intent.FLAG_ACTIVITY_NEW_TASK

    private val main = Handler(Looper.getMainLooper())

    // ────────────────────── 提示与剪贴板 ──────────────────────

    override fun toast(message: String, long: Boolean) {
        main.post {
            Toast.makeText(
                appContext, message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun runOnUiThread(block: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) block() else main.post(block)
    }

    override fun copyToClipboard(label: String, text: String, feedback: String?) {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13 起系统自带复制提示，不再叠一个 Toast（与拆分前 copyCardText 的口径一致）
        if (feedback != null && Build.VERSION.SDK_INT < 33) toast(feedback)
    }

    override fun shareText(title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        appContext.startActivity(Intent.createChooser(intent, title).addFlags(NEW_TASK))
    }

    override fun shareFile(file: File, mime: String, title: String) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            appContext, "${appContext.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(Intent.createChooser(intent, title).addFlags(NEW_TASK))
    }

    // ────────────────────── 选择器 ──────────────────────

    @Composable
    override fun rememberImagePicker(onPicked: (String?) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    // 先按元数据拦一道超大图，并**给出人话提示**（否则只会静默失败）。
                    // 真正的硬保险在 copyImageToStorage 里（readAtMost），因为大小元数据可能缺/不准。
                    val declared = withContext(Dispatchers.IO) {
                        runCatching { appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.length }.getOrNull()
                    }
                    if (declared != null && declared > MAX_IMAGE_BYTES) {
                        toast(
                            "图片太大了（${declared / 1024 / 1024} MB，上限 ${MAX_IMAGE_BYTES / 1024 / 1024} MB），换一张或先裁剪",
                            long = true
                        )
                        onPicked(null)
                    } else {
                        onPicked(copyImageToStorage(appContext, uri))
                    }
                }
            } else {
                onPicked(null)
            }
        }
        return {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    /** 单张图片的读取上限：超限直接返回 null，不把整图读进堆 */
    private val MAX_IMAGE_BYTES = 64L * 1024 * 1024

    private suspend fun copyImageToStorage(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { readAtMost(it, MAX_IMAGE_BYTES) }
                    ?: return@runCatching null
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                    mime.contains("gif") -> "gif"
                    mime.contains("webp") -> "webp"
                    else -> "png"
                }
                Repository.saveImageBytes(bytes, ext)
            }.getOrNull()
        }

    @Composable
    override fun rememberFontFilePicker(onPicked: (String?, String) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val mimeTypes = arrayOf(
            "font/ttf", "font/otf", "font/ttc",
            "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"
        )
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) {
                onPicked(null, "")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                var failReason: String? = null
                var pickedPath: String? = null
                var pickedLabel = ""
                withContext(Dispatchers.IO) {
                    runCatching {
                        val rawName = queryDisplayName(appContext, uri)
                        val size = queryFileSize(appContext, uri)
                        if (size != null && size > MAX_FONT_BYTES) {
                            failReason = "字体文件太大了（上限 ${MAX_FONT_BYTES / 1024 / 1024} MB），换一个再试"
                            return@runCatching
                        }
                        val ext = rawName.substringAfterLast('.', "ttf").lowercase()
                            .takeIf { it in setOf("ttf", "otf", "ttc") } ?: "ttf"
                        val bytes = appContext.contentResolver.openInputStream(uri)
                            ?.use { readAtMost(it, MAX_FONT_BYTES) }
                            ?: return@runCatching
                        if (bytes.isEmpty()) {
                            failReason = "读不到这个字体文件的内容，换一个再试"
                            return@runCatching
                        }
                        val path = Repository.saveFontBytes(bytes, ext)
                        if (!isUsableFontFile(path)) {
                            Repository.deleteFontFile(path)
                            failReason = "这个字体文件用不了（可能已损坏，或是不支持的字体集合），换一个再试"
                            return@runCatching
                        }
                        pickedPath = path
                        pickedLabel = rawName.substringBeforeLast('.').ifBlank { "自定义字体" }
                    }.onFailure { failReason = failReason ?: "读取字体文件失败，换一个再试" }
                }
                // 二级校验（wingding.ttf 教训）：框架 Typeface 能加载 ≠ Compose 能渲染，
                // Compose 渲染失败会炸进程且不可捕获，交给独立进程探针实测
                if (pickedPath != null) {
                    val renderable = runCatching { FontProbe.check(appContext, pickedPath!!) }.getOrDefault(false)
                    if (!renderable) {
                        Repository.deleteFontFile(pickedPath!!)
                        pickedPath = null
                        failReason = "这个字体文件不受支持（无法用于界面排版），换一个再试"
                    }
                }
                when {
                    pickedPath != null -> onPicked(pickedPath!!, pickedLabel)
                    failReason != null -> {
                        toast(failReason, long = true)
                        onPicked(null, "")
                    }
                    else -> onPicked(null, "")
                }
            }
        }
        return { launcher.launch(mimeTypes) }
    }

    /** 自定义字体体积上限：再大基本是整包 CJK 字体，加载进内存会 OOM */
    private val MAX_FONT_BYTES = 32L * 1024 * 1024

    @Composable
    override fun rememberAudioFilePicker(onPicked: (String?, String) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        // audio/* 之外再放 octet-stream：wav / 部分网盘、下载管理器来的 mp3 会被标成后者，
        // 只给 audio/* 的话用户在选文件框里根本看不到它们（字体选择器同理，见上面那个实现）
        val mimeTypes = arrayOf("audio/*", "application/octet-stream")
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri == null) {
                onPicked(null, "")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                var failReason: String? = null
                var pickedPath: String? = null
                var pickedLabel = ""
                withContext(Dispatchers.IO) {
                    runCatching {
                        val rawName = queryDisplayName(appContext, uri)
                        val size = queryFileSize(appContext, uri)
                        if (size != null && size > MAX_AUDIO_BYTES) {
                            failReason = "音频文件太大了（上限 ${MAX_AUDIO_BYTES / 1024 / 1024} MB），换一个或先压缩"
                            return@runCatching
                        }
                        bgmFormatRejection(rawName)?.let { failReason = it; return@runCatching }
                        val ext = rawName.substringAfterLast('.', "mp3").lowercase()
                        val bytes = appContext.contentResolver.openInputStream(uri)
                            ?.use { readAtMost(it, MAX_AUDIO_BYTES) }
                            ?: return@runCatching
                        if (bytes.isEmpty()) {
                            failReason = "读不到这个音频文件的内容，换一个再试"
                            return@runCatching
                        }
                        pickedPath = Repository.saveBgmFile(bytes, ext)
                        pickedLabel = rawName
                    }.onFailure { failReason = failReason ?: "读取音频文件失败，换一个再试" }
                }
                when {
                    pickedPath != null -> onPicked(pickedPath!!, pickedLabel)
                    failReason != null -> {
                        toast(failReason, long = true)
                        onPicked(null, "")
                    }
                    else -> onPicked(null, "")
                }
            }
        }
        return { launcher.launch(mimeTypes) }
    }

    /**
     * 上传音频体积上限：48MB ≈ 128kbps 的 mp3 能装 50 分钟，够长；
     * 上限存在的意义是"整段读进内存"这件事不能没有边界（与图片/字体同一口径）。
     */
    private val MAX_AUDIO_BYTES = 48L * 1024 * 1024

    private fun queryFileSize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst() && c.columnCount > 0 && !c.isNull(0)) c.getLong(0) else null
            }
    }.getOrNull()

    private fun queryDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst() && c.columnCount > 0) {
                        val n = c.getString(0)
                        if (!n.isNullOrBlank()) return n
                    }
                }
        }
        return "font.${uri.lastPathSegment?.substringAfterLast('.') ?: "ttf"}"
    }

    /**
     * 真能加载吗：①文件头魔数 ②构造 Typeface 并量一次字形。
     * Android 的 Typeface.createFromFile 对坏字节**不抛异常**、静默回退默认字体；
     * 魔数检查把这类文件在上传前判死（#19 实测崩溃栈）。
     */
    override fun isUsableFontFile(path: String): Boolean = runCatching {
        val f = File(path)
        if (!f.exists() || f.length() == 0L) return false
        if (!fontMagicOk(f)) return false
        val tf = Typeface.createFromFile(f)
        val paint = Paint().apply {
            typeface = tf
            textSize = 32f
        }
        paint.measureText("字体校验 Whal3") > 0f
    }.getOrDefault(false)

    override fun loadCustomFontFamily(path: String): FontFamily? = runCatching {
        val f = File(path)
        if (f.exists() && f.length() > 0L) FontFamily(Font(f)) else null
    }.getOrNull()

    override suspend fun probeFontRenderable(path: String): Boolean =
        runCatching { FontProbe.check(appContext, path) }.getOrDefault(false)

    @Composable
    override fun rememberJsonFilePicker(onPicked: (String?) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        // image/png 一并放进来：PNG 角色卡与 JSON 走同一个导入入口
        val mimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream", "image/png")
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        runCatching {
                            appContext.contentResolver.openInputStream(uri)?.use {
                                // PNG 卡（tEXt chara）抽出 JSON，其余按 UTF-8 文本（JSON）给出
                                CardImport.cardJsonFromBytes(it.readBytes())
                            }
                        }.getOrNull()
                    }
                    onPicked(text)
                }
            } else {
                onPicked(null)
            }
        }
        return { launcher.launch(mimeTypes) }
    }

    @Composable
    override fun rememberJsonFileSaver(onSaved: (Boolean) -> Unit): (String, String) -> Unit {
        val scope = rememberCoroutineScope()
        var pendingJson by remember { mutableStateOf("") }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) {
                onSaved(false)
                return@rememberLauncherForActivityResult
            }
            val json = pendingJson
            scope.launch { onSaved(writeTextTo(appContext, uri, json)) }
        }
        return { fileName, json ->
            pendingJson = json
            launcher.launch(fileName)
        }
    }

    private suspend fun writeTextTo(context: Context, uri: Uri, text: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        }

    @Composable
    override fun rememberGallerySaver(): (String) -> Unit {
        val scope = rememberCoroutineScope()
        var saving by remember { mutableStateOf(false) }
        return { uri ->
            if (!saving && uri.isNotBlank()) {
                saving = true
                scope.launch {
                    val ok = saveImageToGallery(uri)
                    saving = false
                    toast(if (ok) "已保存到相册（Pictures/MystiCat）" else "保存失败，请重试")
                }
            }
        }
    }

    @Composable
    override fun rememberApkPicker(onResult: (Boolean) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                onResult(false)
                return@rememberLauncherForActivityResult
            }
            scope.launch { onResult(ApkUpdater.installFromUri(appContext, uri)) }
        }
        return { launcher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }
    }

    @Composable
    override fun rememberMicPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> onResult(granted) }
        return { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }

    // ────────────────────── 相册 / 图片 ──────────────────────

    private val galleryHttpClient: OkHttpClient by lazy { OkHttpClient() }

    override suspend fun saveImageToGallery(uri: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = when {
            uri.startsWith("http://") || uri.startsWith("https://") -> {
                // 远端图片也过一遍 httpsUrl：服务端给的 http 链接（OSS 预签名等）在 targetSdk 34 下
                // 会被明文策略直接拦下。私网/回环地址保持原样（本地部署本来就该走 http）。
                val req = Request.Builder().url(AiClient.httpsUrl(uri)).build()
                runCatching { galleryHttpClient.newCall(req).execute().use { it.body?.bytes() } }.getOrNull()
            }
            else -> runCatching { File(uri).readBytes() }.getOrNull()
        } ?: return@withContext false
        val (ext, mime) = sniffImageType(bytes)
        try {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "mysticat_${System.currentTimeMillis()}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
            }
            val pending = Build.VERSION.SDK_INT >= 29
            if (pending) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MystiCat")
                values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            // 插入成功后任一步失败都要把这条 pending 记录删掉，别在相册里留垃圾
            val written = runCatching {
                val out = resolver.openOutputStream(itemUri) ?: return@runCatching false
                out.use { it.write(bytes) }
                true
            }.getOrDefault(false)
            if (!written) {
                runCatching { resolver.delete(itemUri, null, null) }
                return@withContext false
            }
            if (pending) {
                val upd = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(itemUri, upd, null, null)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun decodeImageBitmap(uri: String, maxDim: Int): ImageBitmap? =
        decodeImageBitmapOnAndroid(uri, maxDim)

    override suspend fun encodePng(bitmap: ImageBitmap): ByteArray? = encodeImageBitmapPng(bitmap)

    // ────────────────────── 角色卡图片卡 ──────────────────────

    override suspend fun buildCardImageFile(card: CharacterCard): File? =
        buildCardImageFile(appContext, card)

    override suspend fun buildPngCardFile(card: CharacterCard, json: String): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = pngCardBytes(card, json) ?: return@runCatching null
                // 与图片卡同一目录：FileProvider 暴露的就是这里，分享/存相册直接复用
                val dir = File(appContext.cacheDir, "card-export").apply { mkdirs() }
                val file = File(dir, "pngcard-${System.currentTimeMillis()}.png")
                file.writeBytes(bytes)
                file
            }.getOrNull()
        }

    override fun shareImageFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            appContext, "${appContext.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(Intent.createChooser(intent, "分享角色卡图片").addFlags(NEW_TASK))
    }

    // ────────────────────── 组合期杂项 ──────────────────────

    @Composable
    override fun rememberScreenAspect(fallback: Float): Float {
        val cfg = LocalConfiguration.current
        return if (cfg.screenWidthDp <= 0 || cfg.screenHeightDp <= 0) fallback
        else cfg.screenWidthDp.toFloat() / cfg.screenHeightDp.toFloat()
    }

    @Composable
    override fun dynamicColorScheme(dark: Boolean): ColorScheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val context = LocalContext.current
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        // 不能无限定调用同名函数：Kotlin 里成员优先于顶层 import，会递归到自己（StackOverflowError）
        ActivityBackHandler(enabled = enabled, onBack = onBack)
    }

    /** 软键盘在屏上＝IME 插入量有高度（聊天页本来就用了 `imePadding()`，插入量一直在往下传） */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun rememberKeyboardVisible(): Boolean = WindowInsets.isImeVisible

    override fun exitApp() {
        // finishAffinity：关掉本任务里全部 Activity（含返回栈）＝真正退出应用。
        // 句柄不在（理论到不了）就当无事发生，总比抛异常好。只在主线程被调（返回键回调）。
        runCatching { com.mysticat.roleplay.MainActivity.current?.finishAffinity() }
    }

    // ────────────────────── 应用信息与系统集成 ──────────────────────

    override fun appVersionName(): String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "未知"
    }.getOrDefault("未知")

    override fun appVersionCode(): Int = runCatching {
        val pi = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrDefault(0)

    override fun appPackageName(): String = appContext.packageName

    override fun platformId(): String = "android"

    /** Android 的更新源是顶层平铺字段（桌面那份才在 `windows` 节点里） */
    override fun updateJsonNode(): String? = null

    /** 桌面才需要"关掉应用后双击替换脚本"这一步（Android 交给系统安装器） */
    override fun updateScriptName(): String = ""

    override fun isDebuggableBuild(): Boolean =
        (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    override fun hasMicPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    override fun openUrl(url: String): Boolean = runCatching {
        appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(NEW_TASK))
        true
    }.getOrDefault(false)

    override suspend fun readCrashLog(): String? = withContext(Dispatchers.IO) { CrashGuard.readLog(appContext) }

    override fun clearCrashLog() = CrashGuard.clearLog(appContext)

    override fun noteBreadcrumb(tag: String) = CrashGuard.note(tag)

    override fun markCrashSelfTest() = CrashGuard.markSelfTest()

    // ────────────────────── 应用内更新（ApkUpdater 委托） ──────────────────────

    override suspend fun downloadUpdate(
        candidates: List<String>,
        expectedSha256: String,
        onProgress: (Long, Long) -> Unit
    ): Result<File> = runCatching {
        ApkUpdater.download(appContext, candidates, expectedSha256 = expectedSha256, onProgress = onProgress)
    }

    override fun needsInstallPermission(): Boolean = ApkUpdater.needsInstallPermission(appContext)

    override fun requestInstallPermission(): Boolean = runCatching {
        appContext.startActivity(ApkUpdater.installPermissionIntent(appContext))
        true
    }.getOrDefault(false)

    override fun installUpdate(apk: File): Boolean = ApkUpdater.install(appContext, apk)

    override fun readUpdatePackageVersion(apk: File): Triple<String, Int, String>? =
        ApkUpdater.readApkVersion(appContext, apk)
}
