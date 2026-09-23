package com.mysticat.roleplay.data

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * 桌面自重启与外部脚本外壳（1.0.2 台账 1/2）。
 *
 * 为什么必须有外部 cmd：单实例锁（app.lock）要求旧进程**完全退出**后新进程才能拿锁，
 * 进程内直接 start 新实例会撞锁互退。所以"重启"一律走一个 cmd 外壳——先等当前 pid 退出，
 * 再拉目标。与替换脚本（[DesktopUpdater] 的 updateScript）是同一个模式。
 *
 * dev（`:desktopApp:run`，无 jpackage.app-path）没有 exe 可拉，全部入口返回 false，
 * 由界面降级为"请手动重启生效"。
 */
object DesktopRelaunch {

    /** 当前可执行文件（jpackage 启动器注入的 `jpackage.app-path`）；dev 返回 null */
    fun currentExe(): File? =
        System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
            ?.let(::File)?.takeIf { it.isFile }

    /**
     * 生成"等 [pid] 退出 → 启动 [target]"的外壳脚本内容（**纯 ASCII**，cmd 按 ANSI 码页解析，
     * 中文会崩）。单独抽出来给 --smoke 验内容（wait 循环、自清理）。
     */
    fun buildWaitAndStartScript(pid: Long, target: String): String = """
        @echo off
        setlocal
        set "WAITPID=$pid"
        set "TARGET=$target"
        echo Whale: waiting for pid %WAITPID% to exit ...
        :wait
        tasklist /FI "PID eq %WAITPID%" 2>nul | find "%WAITPID%" >nul
        if not errorlevel 1 (
          timeout /t 2 /nobreak >nul
          goto wait
        )
        echo Whale: relaunching ...
        start "" "%TARGET%"
        del "%~f0"
        exit /b 0
    """.trimIndent() + "\r\n"

    /**
     * 排一个"本进程退出后拉起当前 exe"的重启计划，然后调用方自己优雅退出。
     * 返回 false＝无法自动重启（dev），界面降级提示"请手动重启"。
     * 脚本放系统临时目录：应用目录可能不可写（Program Files 形态正是要重启换目录的场景）。
     */
    fun scheduleRestart(): Boolean {
        val exe = currentExe() ?: return false
        val script = File(System.getProperty("java.io.tmpdir"), "whale-restart-${ProcessHandle.current().pid()}.cmd")
        return runCatching {
            script.writeText(buildWaitAndStartScript(ProcessHandle.current().pid(), exe.absolutePath), StandardCharsets.US_ASCII)
            launchDetached(script)
        }.getOrDefault(false)
    }

    /**
     * 脱离本进程拉起一个脚本/程序（`cmd /c start`，走 ShellExecute）。不等待、不读输出：
     * 调用方的下一步就是退出。路径只来自我们自己生成的文件，不拼用户输入。
     */
    fun launchDetached(file: File): Boolean = runCatching {
        // start 的第一个引号参数是窗口标题，占位成空串，路径才不会被当成标题
        ProcessBuilder("cmd", "/c", "start", "", file.absolutePath).start()
        true
    }.getOrDefault(false)

    /**
     * [path] 是否在本应用安装目录树之内（含等于安装目录本身）。
     * 改数据/缓存目录的入口用它挡"把数据放进安装目录"——1.0.1 实测后果是运行时残缺
     * （缺 jsound.dll / java.security → TTS 全挂、加密明文回退），且 MSI 卸载会连根删除安装目录。
     * dev（无安装目录）恒 false。
     */
    fun isInsideAppDir(path: String): Boolean = runCatching {
        val appDir = DesktopUpdater.appImageDir()?.canonicalFile ?: return@runCatching false
        val target = File(path.trim()).canonicalFile
        target == appDir || target.path.startsWith(appDir.path + File.separator)
    }.getOrDefault(false)
}
