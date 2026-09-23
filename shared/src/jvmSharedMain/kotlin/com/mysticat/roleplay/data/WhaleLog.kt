package com.mysticat.roleplay.data

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 极简共享日志：android.util.Log 的共享层替身。
 * Android 上 println 进 logcat 一样可见；桌面端出 stdout，**并且落盘**（见 [installFileSink]）。
 *
 * 带毫秒时间戳：排查"点了没反应/自动结束"这类问题时，**只有知道两行之间隔了几秒**
 * 才能判断是"卡住"还是"立刻失败"（用户报的"按住三秒就自动取消"就是靠这个量出来的）。
 *
 * ## 为什么要落盘（M9，第 30 轮）
 *
 * 第 28 轮那次 EDT 死循环事故里，应用自己的输出**一行都拿不到**：`:desktopApp:run` 的 stdout
 * 被 Gradle 缓冲（现场只捞到一行），打包后的 exe 更是没有控制台，只能靠 jstack 猜。
 * 控制台是给人看着玩的，**要事后取证就必须落文件**——所以落盘是默认行为，不是可选开关。
 */
internal object WhaleLog {
    /** 单文件上限：到了就轮转（whale.log → whale.log.1 → …），磁盘占用封顶 (KEEP+1)×MAX */
    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val KEEP = 2

    private val lock = Any()
    private val fileStamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)

    @Volatile
    private var file: File? = null

    /** 当前文件已写字节数（持有 [lock] 时读写） */
    private var size = 0L

    /** 文件落点是否已接上（自检用） */
    val fileSinkReady: Boolean get() = file != null

    /** 日志文件路径（没接落点时为 null） */
    fun filePath(): String? = file?.absolutePath

    /**
     * 接上文件落点，并写一条会话头（时间 / 版本行 / 进程 / JVM / 工作目录）。
     * 头必须写：日志是追加的、跨多次运行，出事时第一件要分清的就是"这是哪一次运行"。
     */
    fun installFileSink(target: File, header: String) {
        synchronized(lock) {
            runCatching {
                target.parentFile?.mkdirs()
                size = target.length()
                file = target
            }.onFailure { file = null }
        }
        raw("===== ${fullStamp()} 会话开始 =====")
        raw(header)
        raw(
            "进程 pid=${ProcessHandle.current().pid()} · JVM ${System.getProperty("java.version")}" +
                " · 工作目录 ${System.getProperty("user.dir")}"
        )
        raw("文件日志：${file?.absolutePath ?: "未接上"}（单文件 ${MAX_BYTES / 1024 / 1024}MB，轮转 ${KEEP + 1} 份）")
    }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)
    fun e(tag: String, msg: String) = write("E", tag, msg)

    /**
     * 读日志尾部（「异常记录」页展示 / 反馈用）。
     * 按字节从文件尾往回取一把，不把 2MB 整个读进内存；截断处半个字/半行直接从第一行丢掉。
     */
    fun readTail(maxChars: Int = 30_000): String? {
        val target = file ?: return null
        return runCatching {
            if (!target.isFile) return@runCatching null
            RandomAccessFile(target, "r").use { raf ->
                val total = raf.length()
                // 中文一个字 3 字节，按 3 倍取字节再按行数对齐，避免"取 3 万字却只有 1 万字"
                val from = (total - maxChars.toLong() * 3).coerceAtLeast(0L)
                raf.seek(from)
                val buf = ByteArray((total - from).toInt())
                raf.readFully(buf)
                val text = String(buf, Charsets.UTF_8)
                if (from > 0) text.substringAfter('\n', text) else text
            }
        }.getOrNull()
    }

    private fun write(level: String, tag: String, msg: String) {
        // 控制台先用旧格式（时间只有"当天第几秒"）——开发时看的是这一份，别改它的样子
        println("[${stamp()}] $level/$tag: $msg")
        val target = file ?: return
        synchronized(lock) {
            val line = "[${fullStamp()}] $level/$tag: $msg"
            append(target, line)
        }
    }

    /** 不走级别前缀的原始行（会话头） */
    private fun raw(text: String) {
        println(text)
        val target = file ?: return
        synchronized(lock) { append(target, text) }
    }

    private fun append(target: File, line: String) {
        runCatching {
            val bytes = line.toByteArray(Charsets.UTF_8)
            if (size + bytes.size + 1L > MAX_BYTES) rotate(target)
            // 追加、每条一行、写完就关：崩溃/被强杀时也不丢已写下的内容（缓冲流恰恰会丢）
            FileOutputStream(target, true).use {
                it.write(bytes)
                it.write('\n'.code)
            }
            size += bytes.size + 1L
        }.onFailure {
            // 写日志失败绝不能反过来影响应用（磁盘满 / 文件被占用）：关掉落点，只往控制台喊一声
            file = null
            println("[WhaleLog] 文件落点已关闭（$it）")
        }
    }

    private fun rotate(target: File) {
        val dir = target.parentFile
        runCatching { File(dir, "${target.name}.$KEEP").takeIf { it.isFile }?.delete() }
        for (i in KEEP - 1 downTo 1) {
            val from = File(dir, "${target.name}.$i")
            if (from.isFile) runCatching { from.renameTo(File(dir, "${target.name}.${i + 1}")) }
        }
        runCatching { target.renameTo(File(dir, "${target.name}.1")) }
        size = 0L
    }

    private fun stamp(): String {
        val now = System.currentTimeMillis()
        return "${now % 100_000L / 1000}.${(now % 1000).toString().padStart(3, '0')}"
    }

    /** 文件行的时间戳带日期：文件是跨天追加的，只有"第几秒"没法定位（调用处已持锁） */
    private fun fullStamp(): String = fileStamp.format(Date())
}
