package com.mysticat.roleplay

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局异常守卫。
 *
 * 背景：#14「偶发闪退」跨了四个版本都没抓到崩溃栈——用户的手机不接 adb，
 * 现场只留在系统 dropbox 里，我们拿不到。于是这里自己做两件事：
 *
 * 1. **记录现场**：崩溃时把异常栈 + 机型版本 + 最近的操作轨迹（[note] 攒的面包屑）写进
 *    `filesDir/crash.log`，用户在「我的 → 关于鲸鱼 → 异常记录」里能看到并复制粘贴给我们。
 * 2. **自动恢复**：记完就重启回首页，而不是留下一个死掉的窗口。本 App 的数据是即时落盘的
 *    （每次改动都 writeAtomically），重启最多丢掉"正在生成的那条回复"，
 *    与既有「永不需要卸载重装」的承诺一致。
 *
 * 防循环：30 秒内发生第二次崩溃就不再重启（只记录，然后交给系统默认处理），
 * 免得坏状态导致无限重启，用户想手动进应用都进不去。
 */
object CrashGuard {

    private const val LOG_NAME = "crash.log"

    /** 日志上限（字符数）：只留最近的现场，避免文件无限增长 */
    private const val MAX_CHARS = 80_000

    /** 两次重启之间的最小间隔，超过就认为"不是同一个坏状态" */
    private const val RESTART_GUARD_MS = 30_000L

    /** 面包屑条数上限 */
    private const val BREADCRUMB_KEEP = 40

    @Volatile
    private var lastRestartAt = 0L

    /**
     * 下一条记录是不是"调试包自检"。
     *
     * 自测抛出的异常和真崩溃在栈上一模一样，混在 crash.log 里会让排查的人（和用户）
     * 白查一轮——2026-09-15 就发生过：三条自检记录被当成"用户 tab 偶发闪退"报了上来。
     * 所以在记录头部直接标明，读日志的人一眼能排除。
     */
    @Volatile
    private var selfTestPending = false

    private val breadcrumbs = ArrayDeque<String>()

    private fun stamp(): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date())

    /**
     * 记一条操作轨迹（面包屑）。
     *
     * 刻意只记"用户点了哪一栏/哪一个入口"这类极轻量的事实：崩溃栈只说明炸在哪，
     * 而"炸之前用户在点哪里"才是定位偶发问题的关键（#14 就是死在这一步）。
     * 只放内存，不写盘，不吃 IO。
     */
    fun note(tag: String) {
        runCatching {
            synchronized(breadcrumbs) {
                breadcrumbs.addLast("${stamp()}  $tag")
                while (breadcrumbs.size > BREADCRUMB_KEEP) breadcrumbs.removeFirst()
            }
        }
    }

    /**
     * 标记"接下来这条是自检"（仅调试包长按版本号时调用）。
     * 与 [note] 分开：这是对**这一条记录**的标注，不是操作轨迹。
     */
    fun markSelfTest() {
        selfTestPending = true
    }

    /** 在 Application.onCreate 里装一次 */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val shouldRestart = runCatching {
                writeRecord(app, thread, error)
                System.currentTimeMillis() - lastRestartAt > RESTART_GUARD_MS
            }.getOrDefault(false)

            if (!shouldRestart) {
                // 交给系统：弹一次「应用已停止」，让用户看到真实情况而不是我们静默吞掉
                previous?.uncaughtException(thread, error)
                return@setDefaultUncaughtExceptionHandler
            }

            lastRestartAt = System.currentTimeMillis()
            runCatching { relaunch(app) }
            // 等新首页拉起来再结束自己：直接 kill 会让刚发出的 startActivity 一起没掉
            runCatching {
                Thread {
                    runCatching { Thread.sleep(700) }
                    runCatching { Process.killProcess(Process.myPid()) }
                    kotlin.system.exitProcess(10)
                }.start()
            }
        }
    }

    /**
     * 拉起救援页（**不是**直接 startActivity 主界面）。
     *
     * 主进程崩溃后主线程 Looper 已死：同进程里启动的界面跑不起来，而紧接着的 killProcess
     * 还会把它一起带走——实测结果就是"崩溃后停在桌面，并没有恢复"。
     * [RescueActivity] 声明在独立进程（`:crashrescue`），由系统新开、不受主进程死亡影响，
     * 再由它去拉 MainActivity：那时主进程已被回收，AMS 会 fork 一个干净的主进程。
     */
    private fun relaunch(app: Application) {
        val intent = Intent(app, RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    private fun writeRecord(context: android.content.Context, thread: Thread, error: Throwable) {
        val sb = StringBuilder()
        sb.append("==== ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            .append(" ====\n")
        sb.append("版本：").append(versionName(context)).append('\n')
        sb.append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE)
            .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("线程：").append(thread.name).append('\n')
        if (selfTestPending) {
            selfTestPending = false
            sb.append("⚠️ 自检：是（调试包长按版本号故意抛出，不是真实崩溃）\n")
        }
        sb.append("操作轨迹：\n")
        synchronized(breadcrumbs) {
            if (breadcrumbs.isEmpty()) sb.append("  · （无）\n")
            else breadcrumbs.forEach { sb.append("  · ").append(it).append('\n') }
        }
        sb.append("异常：\n").append(Log.getStackTraceString(error)).append('\n')

        val file = File(context.filesDir, LOG_NAME)
        // 新记录放最前面（最新的现场最有用），整份截到上限
        val previous = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
        runCatching { file.writeText((sb.toString() + "\n" + previous).take(MAX_CHARS)) }
    }

    private fun versionName(context: android.content.Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** 读崩溃日志（没有就返回 null）；供「关于鲸鱼 → 异常记录」展示 */
    fun readLog(context: android.content.Context): String? =
        runCatching {
            val file = File(context.filesDir, LOG_NAME)
            if (file.isFile) file.readText().takeIf { it.isNotBlank() } else null
        }.getOrNull()

    fun clearLog(context: android.content.Context) {
        runCatching { File(context.filesDir, LOG_NAME).delete() }
        synchronized(breadcrumbs) { breadcrumbs.clear() }
    }
}
