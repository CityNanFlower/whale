package com.mysticat.roleplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Window
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 桌面端的平台支撑件。三件事：
 * ① 提示覆盖层（Android 是 Toast，桌面自己画一层会消失的浮层）；
 * ② Esc 返回栈（Android 是系统返回键，桌面由窗口壳把 Esc 转进同一个回调）；
 * ③ 崩溃日志（Android 是 CrashGuard + :crashrescue 独立进程，桌面就是日志文件 + 查看页）。
 */

// ─────────────────────────── 提示覆盖层 ───────────────────────────

/** 一条桌面提示。普通提示 2.5s 后自动消失，[long] 的 4s（口径对齐 Android Toast 的 SHORT/LONG） */
data class DesktopToast(val id: Long, val message: String, val long: Boolean = false)

/**
 * 提示队列。可从任意线程调用（数据层在后台线程报错时也要能弹），
 * 真正的渲染在 UI 线程由 [DesktopToastHost] 消费。
 */
object DesktopToasts {
    private val _items = MutableStateFlow<List<DesktopToast>>(emptyList())
    val items: StateFlow<List<DesktopToast>> = _items.asStateFlow()

    private val seq = AtomicLong(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun show(message: String, long: Boolean = false) {
        if (message.isBlank()) return
        val toast = DesktopToast(seq.incrementAndGet(), message, long)
        _items.update { (it + toast).takeLast(4) }
        scope.launch {
            delay(if (long) 4000 else 2500)
            dismiss(toast.id)
        }
    }

    fun dismiss(id: Long) = _items.update { list -> list.filterNot { it.id == id } }
}

/** 把提示浮层叠在内容之上（窗口壳里和 AppRoot 并排放在同一个 Box 里） */
@Composable
fun DesktopToastHost() {
    val items by DesktopToasts.items.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier.padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { t ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = t.message,
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 「发现新版本」提醒（启动检查）：挂在窗口最顶层（与 [DesktopToastHost] 同层），
 * 三条路由（主界面 / 聊天 / 编辑）都盖得住。
 *
 * 为什么需要它：托盘的更新入口**与托盘气泡**都只在「关闭时缩小到托盘」开着时才存在（默认关），
 * 而 Win11 还会把新注册的托盘图标塞进「显示隐藏的图标」浮层——只靠托盘，没开托盘的用户
 * 此前完全没有自动发现新版本的途径。
 *
 * 出声口径：只有 desktopApp 的启动静默检查**真查到更新的版本**时才有内容；已是最新 / 清单解析失败 /
 * 断网一律不出现（启动时被告知"检查更新失败"是打扰，用户没主动要这个结果）。
 * 形态＝**弹窗**（用户要求：通知与提醒一律走弹窗），与手机端是同一个 `UpdateFlowHost`，
 * 所以两端的提醒长得一样、状态也只有一份。
 */
@Composable
fun DesktopUpdateNoticeHost() {
    UpdateFlowHost()
}

// ─────────────────────────── Esc 返回 ───────────────────────────

/**
 * 返回栈。共享 UI 里的 `WhaleBackHandler` 在桌面走这里注册；窗口壳收到 Esc 时调 [dispatch]。
 * 只有栈顶响应（与 Android 返回键"最后压入的最先响应"一致），返回 false 交给系统默认行为。
 * 全部调用都在 UI 线程（组合 + 按键事件），无需加锁。
 */
object DesktopBack {
    private val stack = ArrayDeque<() -> Unit>()

    fun push(handler: () -> Unit) {
        stack.remove(handler)
        stack.addLast(handler)
    }

    fun remove(handler: () -> Unit) {
        stack.remove(handler)
    }

    /** 返回是否消费了这次 Esc */
    fun dispatch(): Boolean {
        val top = stack.lastOrNull() ?: return false
        top()
        return true
    }
}

// ─────────────────────────── 崩溃日志 ───────────────────────────

/**
 * 桌面崩溃现场记录（对齐 Android 的 CrashGuard 的"记录现场"职责）。
 *
 * 差异（会写进对外日志的已知差异）：桌面**不做**自动重启与安全模式——
 * Android 那套依赖 `:crashrescue` 独立进程 + Activity 重建，桌面没有对应物；
 * 这里只保证「崩了有现场可查」，查看入口走设置页的异常记录。
 */
object DesktopCrashLog {
    private const val MAX_BREADCRUMBS = 40
    private lateinit var logFile: File
    private val breadcrumbs = ArrayDeque<String>()
    @Volatile
    private var ready = false

    fun init(filesRoot: File) {
        logFile = File(filesRoot, "crash.log")
        ready = true
    }

    fun note(tag: String) {
        if (!ready) return
        synchronized(breadcrumbs) {
            breadcrumbs.addLast("${stamp()} · $tag")
            while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeFirst()
        }
    }

    /** 记录一次异常现场（含最近的操作轨迹） */
    fun record(thread: Thread, error: Throwable) {
        if (!ready) {
            error.printStackTrace()
            return
        }
        runCatching {
            val sb = StringBuilder()
            sb.appendLine("===== ${stamp()} =====")
            sb.appendLine("线程：${thread.name}")
            sb.appendLine("异常：${error.javaClass.name}: ${error.message}")
            synchronized(breadcrumbs) {
                if (breadcrumbs.isNotEmpty()) {
                    sb.appendLine("--- 最近操作 ---")
                    breadcrumbs.forEach { sb.appendLine(it) }
                }
            }
            sb.appendLine("--- 堆栈 ---")
            error.stackTraceToString().lineSequence().forEach { sb.appendLine(it) }
            logFile.parentFile?.mkdirs()
            logFile.appendText(sb.toString())
        }
        error.printStackTrace()
    }

    /** 装全局兜底：桌面没有 Android 的进程级守卫，"不崩"做不到，但"崩了留痕"要做 */
    fun installUncaughtHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(thread, error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** 崩溃自测（设置页长按版本号触发；只在开发运行下可达） */
    fun markSelfTest() {
        note("崩溃自测（用户手动触发）")
        throw RuntimeException("鲸鱼桌面版崩溃自测：这是一次故意抛出的异常，用于验证崩溃日志链路")
    }

    suspend fun read(): String? = runCatching {
        if (!ready || !logFile.isFile) null else logFile.readText(Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        runCatching { if (ready) logFile.delete() }
    }

    private fun stamp() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date())
}

// ─────────────────────────── 应用日志落盘 ───────────────────────────

/**
 * 桌面应用日志：把共享层的 [com.mysticat.roleplay.data.WhaleLog] 接到
 * `%APPDATA%\MysticatRoleplay\whale.log`。
 *
 * 为什么非有不可：`:desktopApp:run` 的 stdout 被 Gradle 缓冲、打包后的 exe 没有控制台——
 * 那次 EDT 死循环，应用自己的输出一行都拿不到，只能 `jstack` 猜。
 * 三处入口都接：正常启动（`bootstrap`）、`--smoke`、`--shot`（后两者共用 `bootstrap`，自检自己接一次）。
 *
 * 与崩溃日志 [DesktopCrashLog] 的分工：那份只在**崩溃那一刻**写一份现场（含操作轨迹），
 * 这份是**全过程流水**——"卡死但没崩"只能靠它。
 */
object DesktopLog {
    fun init(filesRoot: File, header: String) {
        runCatching { com.mysticat.roleplay.data.WhaleLog.installFileSink(File(filesRoot, LOG_NAME), header) }
    }

    /** 写一条无级别的应用级标记（启动信息、自检开始等） */
    fun mark(text: String) = com.mysticat.roleplay.data.WhaleLog.i("WhaleApp", text)

    /** 日志尾部（「异常记录」页展示 + 复制反馈用） */
    fun readTail(maxChars: Int = 30_000): String? =
        runCatching { com.mysticat.roleplay.data.WhaleLog.readTail(maxChars) }.getOrNull()

    /** 日志文件路径（界面上告诉用户"文件在哪"，日志被截断时还能自己去拿整份） */
    fun path(): String? = com.mysticat.roleplay.data.WhaleLog.filePath()

    const val LOG_NAME = "whale.log"
}

// ─────────────────────────── AWT 对话框与窗口助手 ───────────────────────────

/** 当前活动的 AWT 窗口（当作原生对话框的父窗口，保证对话框挂在应用上、居中显示） */
fun activeAwtWindow(): Frame? =
    Window.getWindows().firstOrNull { it is Frame && it.isActive && it.isShowing } as? Frame

/**
 * 桌面原生"打开文件"对话框。
 * 用 AWT 的 [FileDialog]（Windows 上就是系统文件框）而不是 Swing 的 JFileChooser：
 * 前者是原生外观、且 `FileDialog` 的 `directory`/`file` 能直接给出绝对路径。
 * 必须在 UI 线程调用（模态对话框自身会泵事件，Compose 照常重绘）。
 *
 * ⚠ **Windows 上的类型过滤只有"一个扩展名"这一种表达方式**（探针实测，
 * ＋真窗口截图）：原生框的过滤项是从**预填的文件名**里取扩展名生成的——`dialog.file = "*.aac"`
 * 之后文件列表里**只剩 .aac**，而类型下拉照样写"所有文件 (*.*)"（假象）；`setFilenameFilter`
 * 在 Windows 上完全无效（只在 Unix 生效，「对象类型」那一栏 AWT 也改不了）。
 * 于是"多扩展名"用通配符过滤 = **把用户真正想选的文件藏起来**：BGM 支持 m4a 之后扩展名表排序
 * 第一个是 `.aac`，用户手里的 m4a/flac/mp3 在框里全看不见（用户报的"桌面端还是传不了这些格式"）。
 * 所以这里：**唯一扩展名才用通配符过滤，多个一律不预填**——全都看得见，永远好过"只看得见一个"；
 * 真正不支持的格式由调用方的事后闸门（如 `bgmFormatRejection`）给出明确原因。
 * 可选的扩展名清单同时写进**标题**，用户至少知道该准备什么格式。
 */
fun pickFile(
    title: String,
    extensions: List<String>,
    description: String
): File? {
    val withHint = if (extensions.isEmpty()) title
    else "$title（${extensions.joinToString(" / ") { ".$it" }}）"
    val dialog = FileDialog(activeAwtWindow(), withHint, FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val lower = name.lowercase(Locale.ROOT)
        extensions.any { lower.endsWith(".$it") }
    }
    if (extensions.size == 1) dialog.file = "*.${extensions.first()}"
    dialog.isMultipleMode = false
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val picked = File(dir, name)
    return if (picked.isFile) picked else null
}

/** 桌面原生"保存文件"对话框；返回用户选定的目标文件（可能已存在），取消返回 null */
fun pickSaveFile(title: String, defaultName: String, extension: String): File? {
    val dialog = FileDialog(activeAwtWindow(), title, FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    val picked = File(dir, if (name.lowercase(Locale.ROOT).endsWith(".$extension")) name else "$name.$extension")
    return picked
}

/** 在系统文件管理器里定位到该文件（"打开所在目录"——桌面没有分享面板，用这个代替） */
fun revealInFileManager(file: File): Boolean = runCatching {
    if (java.awt.Desktop.isDesktopSupported()) {
        java.awt.Desktop.getDesktop().open(file.parentFile ?: file)
        true
    } else false
}.getOrDefault(false)
