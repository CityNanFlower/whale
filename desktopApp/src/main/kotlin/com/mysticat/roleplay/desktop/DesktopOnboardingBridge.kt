package com.mysticat.roleplay.desktop

import com.mysticat.roleplay.data.DesktopRelaunch
import com.mysticat.roleplay.ui.DesktopLog
import com.mysticat.roleplay.ui.DesktopOnboarding
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * 首启引导页的桌面端实现：建桌面快捷方式 ＋ 目录选择框。
 * 只在这里摸 PowerShell / JFileChooser（AWT 不进共享层，模式同 DesktopTray/WindowsAutostart）。
 */
internal object DesktopOnboardingBridge : DesktopOnboarding.Impl {

    private const val SHORTCUT_NAME = "鲸鱼.lnk"
    private const val TIMEOUT_S = 15L

    private fun exePath(): File? =
        System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
            ?.let(::File)?.takeIf { it.isFile }

    override val shortcutAvailable: Boolean
        get() = exePath() != null

    /**
     * 写 .lnk 用 PowerShell 的 WScript.Shell COM。走**临时 .ps1 文件**而不是命令行直传：
     * ① 文件名含中文（「鲸鱼.lnk」），命令行传参会撞系统编码（GBK），.ps1 必须 UTF-8 **带 BOM**——
     * Windows PowerShell 5.1 没有 BOM 就按 ANSI 读，中文全成乱码（实测踩过的坑）；
     * ② 桌面路径用 `[Environment]::GetFolderPath('Desktop')` 现查——OneDrive 重定向时
     * USERPROFILE\Desktop 不是真桌面。
     */
    override fun createDesktopShortcut(): Boolean {
        val exe = exePath() ?: run {
            DesktopLog.mark("快捷方式创建失败：不是 jpackage 安装版（无 jpackage.app-path）")
            return false
        }
        val ps = File(System.getProperty("java.io.tmpdir"), "whale-shortcut-${System.currentTimeMillis()}.ps1")
        try {
            val script = buildString {
                appendLine("\$desktop = [Environment]::GetFolderPath('Desktop')")
                appendLine("\$s = (New-Object -ComObject WScript.Shell).CreateShortcut(\"\$desktop\\$SHORTCUT_NAME\")")
                appendLine("\$s.TargetPath = '${exe.absolutePath.replace("'", "''")}'")
                appendLine("\$s.WorkingDirectory = '${exe.parentFile.absolutePath.replace("'", "''")}'")
                appendLine("\$s.IconLocation = '${exe.absolutePath.replace("'", "''")},0'")
                appendLine("\$s.Save()")
            }
            // UTF-8 BOM：没有它 PowerShell 5.1 按 ANSI 读，SHORTCUT_NAME 的中文会乱码
            ps.outputStream().use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(script.toByteArray(Charsets.UTF_8))
            }
            val p = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", ps.absolutePath
            ).redirectErrorStream(true).start()
            p.inputStream.readBytes() // 吃掉输出防管道满
            val ok = p.waitFor(TIMEOUT_S, TimeUnit.SECONDS) && p.exitValue() == 0
            DesktopLog.mark("桌面快捷方式创建：${if (ok) "成功" else "失败（exit=${runCatching { p.exitValue() }.getOrDefault(-1)}）"} → $exe")
            return ok
        } catch (t: Throwable) {
            DesktopLog.mark("桌面快捷方式创建异常：${t.message}")
            return false
        } finally {
            ps.delete()
        }
    }

    /**
     * 目录选择框（AWT JFileChooser）。调用方在 Compose UI 线程（＝AWT EDT）上，若是 EDT 就直接弹
     * 模态框；保险起见非 EDT 时切 EDT 同步等结果（JFileChooser 不是线程安全的）。
     */
    override fun pickDirectory(current: String): String? {
        val r = if (SwingUtilities.isEventDispatchThread()) pickOnEdt(current) else {
            var result: String? = null
            SwingUtilities.invokeAndWait { result = pickOnEdt(current) }
            result
        }
        return r
    }

    private fun pickOnEdt(current: String): String? {
        val chooser = JFileChooser(current.takeIf { File(it).isDirectory }).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择目录"
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else null
    }

    /** 改目录入口挡安装目录树（判定在 DesktopRelaunch，同一份给 --smoke 测） */
    override fun isInsideInstallDir(path: String): Boolean = DesktopRelaunch.isInsideAppDir(path)

    /** 排"等本进程退出→拉起当前 exe"的外壳（dev 无 exe 返回 false，界面降级提示） */
    override fun scheduleRestart(): Boolean = DesktopRelaunch.scheduleRestart()
}
