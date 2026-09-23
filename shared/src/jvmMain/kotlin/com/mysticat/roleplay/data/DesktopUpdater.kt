package com.mysticat.roleplay.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * 桌面版更新器（M5；对齐 Android 的 [ApkUpdater]，但桌面的"安装"是**换便携包**而不是装 APK）。
 *
 * 链路：`version.json` 的 `windows` 节点 → 下载 `鲸鱼-Windows-<版本>-win.zip`（镜像优先）→
 * **SHA-256 校验** → 读包内 `build-info.json` 核对版本 → 解压到 `update-staging/` →
 * 生成一个替换脚本（`应用更新.cmd`）交给用户：关掉鲸鱼后双击，它会等进程退出、覆盖文件、重启应用。
 *
 * 为什么不自己直接覆盖正在运行的程序：Windows 上运行中的 exe/dll 被占用，覆盖必然失败；
 * 而"退出后自动重启替换"要在没有第二个可执行体的前提下做，只能靠一个外部脚本。**这是刻意的差异**
 * （Android 是系统安装器接管，桌面没有对应物），会写进对外日志的说明里。
 */
object DesktopUpdater {

    private const val TIMEOUT_CONNECT = 15L
    private const val TIMEOUT_READ = 60L

    /** 便携包里的版本标记文件名（由 :desktopApp 的 writePortableBuildInfo 生成） */
    private const val BUILD_INFO_ENTRY = "build-info.json"

    /** 解压暂存目录名（在应用目录下的子目录，更新脚本按它取源） */
    private const val STAGING_DIR = "update-staging"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_CONNECT, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_READ, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 依次尝试候选地址下载便携包；[expectedSha256] 非空时逐字节校验，不匹配就删掉换下一个源。
     * 返回落盘的 zip；全部失败抛异常（消息给用户看）。
     */
    suspend fun download(
        candidates: List<String>,
        expectedSha256: String,
        destDir: File,
        onProgress: (done: Long, total: Long) -> Unit,
        /** 安装器形态（M13）传 "exe"：扩展名决定 installUpdate 的分流（exe＝拉起安装器，zip＝解压替换） */
        fileExt: String = "zip"
    ): File = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) throw IllegalStateException("下载地址尚未配置")
        destDir.mkdirs()
        var last: Throwable? = null
        for (url in candidates) {
            val target = File(destDir, "whale-win-${System.currentTimeMillis()}.$fileExt")
            try {
                val request = Request.Builder().url(url).get().build()
                http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IllegalStateException("响应为空")
                    val total = body.contentLength()
                    body.byteStream().use { ins ->
                        target.outputStream().buffered().use { os ->
                            val buf = ByteArray(1 shl 16)
                            var done = 0L
                            while (true) {
                                val n = ins.read(buf)
                                if (n <= 0) break
                                os.write(buf, 0, n)
                                done += n
                                onProgress(done, total)
                            }
                        }
                    }
                }
                if (target.length() == 0L) throw IllegalStateException("下载到 0 字节")
                if (expectedSha256.isNotBlank()) {
                    val actual = sha256(target)
                    if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                        throw IllegalStateException("校验失败：期望 ${expectedSha256.take(12)}…，实际 ${actual.take(12)}…")
                    }
                }
                return@withContext target
            } catch (t: Throwable) {
                last = t
                target.delete()
            }
        }
        throw IllegalStateException(last?.message ?: "网络不可达")
    }

    /**
     * 读便携包内的 `build-info.json`，返回 `(包名, versionCode, versionName)`。
     * 读不到 / 不是便携包就返回 null（与 Android 侧读 APK Manifest 的返回口径一致）。
     */
    fun readPackageVersion(zip: File): Triple<String, Int, String>? = runCatching {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == BUILD_INFO_ENTRY) {
                    val text = zis.readBytes().toString(StandardCharsets.UTF_8)
                    val obj = Json.parseToJsonElement(text).jsonObject
                    fun str(k: String) = obj[k]?.jsonPrimitive?.content ?: ""
                    return Triple(
                        str("packageName"),
                        str("versionCode").toIntOrNull() ?: 0,
                        str("versionName")
                    )
                }
                entry = zis.nextEntry
            }
        }
        null
    }.getOrNull()

    /**
     * 把便携包解压到 `应用目录/update-staging/` 并生成替换脚本；返回脚本文件。
     *
     * [selfPid] 写进脚本用来等本次进程退出（按 PID 判，用户改了 exe 名字也不会误判成"已退出"）。
     * 应用目录取不到（开发运行 `:desktopApp:run` 没有 jpackage 启动器）时抛异常，由调用方给提示。
     */
    fun stage(zip: File, selfPid: Long): File {
        val appDir = appImageDir()
            ?: throw IllegalStateException("当前不是从便携包运行（开发运行不替换程序目录）")
        return stageInto(zip, appDir, selfPid)
    }

    /**
     * [stage] 的"指定目标目录"版本：自检用它在一个临时目录上跑完整流程
     * （解压 + 生成脚本），不必真的从便携包运行。
     */
    fun stageInto(zip: File, appDir: File, selfPid: Long): File {
        val staging = File(appDir, STAGING_DIR)
        staging.deleteRecursively()
        staging.mkdirs()
        extractZip(zip, staging)
        val exes = staging.listFiles().orEmpty().filter { it.name.endsWith(".exe") }
        if (exes.isEmpty()) {
            staging.deleteRecursively()
            throw IllegalStateException("便携包里没找到启动程序（下载的可能是安装包或损坏的文件）")
        }
        val script = File(appDir, UPDATE_SCRIPT_NAME)
        script.writeBytes(updateScript(selfPid, exes.first().name).toByteArray(StandardCharsets.US_ASCII))
        return script
    }

    /** 应用目录：jpackage 启动器会注入 `jpackage.app-path`（指向 exe），取它的父目录 */
    fun appImageDir(): File? {
        val appPath = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() } ?: return null
        val exe = File(appPath)
        return exe.parentFile?.takeIf { it.isDirectory }
    }

    // ────────────────────── 安装程序形态（M13） ──────────────────────

    /**
     * 应用目录可写性（M13 更新分流）：安装程序形态默认装进 Program Files，普通用户没有写权限，
     * 「下载 zip → 解压覆盖」链路失效。据此分流：可写＝便携包替换（便携 zip / 装在用户可写目录）、
     * 不可写＝下载新安装器。返回 null ＝ 不在程序目录语境（开发运行 `:desktopApp:run`），按"维持旧链路"处理。
     */
    fun appDirWritable(): Boolean? {
        val dir = appImageDir() ?: return null
        return probeWritable(dir)
    }

    /**
     * 往目录里真建一个临时文件再删掉。不用 [File.canWrite]：它只看属性位，
     * 在 Program Files 上会被 UAC 虚拟化和继承 ACL 骗过（属性写着可写、真写就 Access denied）。
     */
    fun probeWritable(dir: File): Boolean = runCatching {
        val probe = File(dir, ".whale-write-probe-${System.currentTimeMillis()}")
        val ok = dir.isDirectory && probe.createNewFile()
        if (ok) probe.delete()
        ok
    }.getOrDefault(false)

    /**
     * 拉起 jpackage 安装器。必须经 `cmd /c start`（走 ShellExecute）：安装器要管理员权限，
     * 直接 ProcessBuilder 走 CreateProcess 会报 740（需要提升），ShellExecute 才会弹 UAC。
     * 用户在 UAC 取消时拿不到回执，界面文案按"已启动、按向导走"写，失败兜底是给路径让用户手动双击。
     */
    fun launchInstaller(installer: File): Boolean = runCatching {
        // start 的第一个引号参数是窗口标题，占位成空串，路径才不会被当成标题
        ProcessBuilder("cmd", "/c", "start", "", installer.absolutePath).start()
        true
    }.getOrDefault(false)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 解压并防"目录穿越"（zip 条目名里带 .. 时跳过，不往目标目录外写） */
    private fun extractZip(zip: File, dest: File) {
        val destPath = dest.canonicalFile.toPath()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                val outPath = out.canonicalFile.toPath()
                if (!outPath.startsWith(destPath)) {
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { os -> zis.copyTo(os) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * 替换脚本（**纯 ASCII**，遵守本仓"`.bat`/`.cmd` 一律纯 ASCII"的规矩：
     * cmd 按系统 ANSI 码页解析批处理，中文内容会直接崩掉）。用户可见的中文说明放在界面提示里。
     */
    private fun updateScript(selfPid: Long, exeName: String): String = """
        @echo off
        setlocal
        set "ROOT=%~dp0"
        set "SRC=%ROOT%$STAGING_DIR"
        set "WAITPID=$selfPid"
        echo Whale update: waiting for the running app to exit (pid %WAITPID%) ...
        :wait
        tasklist /FI "PID eq %WAITPID%" 2>nul | find "%WAITPID%" >nul
        if not errorlevel 1 (
          timeout /t 2 /nobreak >nul
          goto wait
        )
        echo Whale update: copying new files ...
        xcopy "%SRC%\*" "%ROOT%" /E /Y /I /Q >nul
        if errorlevel 1 (
          echo Whale update: copy failed. Please unzip the package manually.
          pause
          exit /b 1
        )
        rmdir /S /Q "%SRC%"
        echo Whale update: done. Starting the app ...
        start "" "%ROOT%$exeName"
        exit /b 0
    """.trimIndent() + "\r\n"

    /** 替换脚本文件名（中文名方便用户认，内容是 ASCII） */
    const val UPDATE_SCRIPT_NAME = "应用更新.cmd"
}
