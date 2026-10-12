package com.mysticat.roleplay.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 应用内下载新安装包并唤起系统安装器。
 *
 * 用户反馈：原来点「前往下载」会跳到 github.com 的 Release 页面，国内必须挂 VPN。
 * 这里下载走 GitHub 加速镜像（候选地址由 AppLinks.downloadCandidates 生成，镜像优先、
 * 原始地址兜底），下完直接交给系统安装器，全程不用离开 App、也不用浏览器。
 *
 * 注意：APK 必须放应用自己的私有目录再由 FileProvider 授权给安装器，
 * 不能放在外部存储随便一个位置——Android 7.0 起 file:// 会抛 FileUriExposedException。
 */
object ApkUpdater {

    /** 下载专用客户端：connect 短、read 长（安装包 12MB+，给足时间） */
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    /** 安装包落盘目录（私有 filesDir，无需任何存储权限） */
    fun downloadDir(context: Context): File = File(context.filesDir, "apk").apply { mkdirs() }

    /** 计算文件 SHA-256（十六进制小写），用于安装包完整性校验 */
    fun sha256Of(file: File): String = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrDefault("")

    /**
     * 按候选地址依次尝试下载，第一个成功的即采用。
     *
     * @param onProgress 下载进度回调（已下载字节, 总字节；总线长度未知时为 -1），
     *   回调在 IO 线程，调用方自行切主线程。
     * @return 下载完成的本地文件
     */
    suspend fun download(
        context: Context,
        candidates: List<String>,
        fileName: String = "whale-update.apk",
        /**
         * version.json 里声明的安装包 SHA-256（可留空 = 不校验，保持旧版行为）。
         * 非空时下载完成后逐源校验：不一致就换下一个源重试，全部失败则报错。
         */
        expectedSha256: String = "",
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) throw IllegalStateException("下载地址为空")

        var lastError: Exception? = null
        for (url in candidates) {
            val target = File(downloadDir(context), fileName)
            try {
                val request = Request.Builder().url(url).get().build()
                http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}")
                    }
                    val body = resp.body ?: throw IllegalStateException("响应为空")
                    val total = body.contentLength()
                    // 先写临时文件，下完再改名——中途失败不会留下半个包被误当成完整包
                    val tmp = File(target.parentFile, "$fileName.part")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastReport = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                done += n
                                // 节流：每 256KB 报一次，避免高频回调把主线程刷爆
                                if (done - lastReport >= 256 * 1024) {
                                    lastReport = done
                                    onProgress(done, total)
                                }
                            }
                            output.flush()
                        }
                    }
                    onProgress(total.coerceAtLeast(0), total.coerceAtLeast(0))
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        throw IllegalStateException("无法写入安装包文件")
                    }
                    // 完整性校验（version.json 提供 sha256 时才做）：不一致说明下载被篡改或文件损坏，
                    // 抛异常让外层换下一个源重试（与"下载失败"同一处理路径）
                    if (expectedSha256.isNotBlank()) {
                        val actual = sha256Of(target)
                        if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                            throw IllegalStateException("SHA-256 校验不通过（期望 ${expectedSha256.take(12)}…，实际 ${actual.take(12)}…）")
                        }
                    }
                    return@withContext target
                }
            } catch (e: Exception) {
                lastError = e
                // 半成品必须删掉：否则下载中断会在 filesDir/apk 留下十几 MB 的 .part 一直堆着
                // （清除缓存原先只管 cacheDir，"更新包残留"就是这么攒出来的）
                runCatching { File(downloadDir(context), "$fileName.part").delete() }
                // 换下一个源重试
            }
        }
        throw lastError ?: IllegalStateException("所有下载源均不可用")
    }

    /** 是否需要先授予「安装未知应用」权限（Android 8.0+ 按应用授权） */
    fun needsInstallPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    /**
     * 读取一个**未安装** APK 的版本信息（包名 / versionCode / versionName），读不到返回 null。
     *
     * 用途：发版时若 version.json 的 versionCode 先改了、安装包还没传（或传错版本），
     * 用户会下到一个与提示不符的包。下载完成后先核对一次，能当场提示而不是让人装完才发现。
     */
    fun readApkVersion(context: Context, apk: File): Triple<String, Int, String>? =
        runCatching {
            val info = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.GET_ACTIVITIES
            ) ?: return null
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            Triple(info.packageName, code, info.versionName ?: "")
        }.getOrNull()

    /** 跳系统设置里的「安装未知应用」授权页 */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 唤起系统安装器。未授予「安装未知应用」时返回 false，调用方应改为引导去设置页。
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists() || apk.length() == 0L) return false
        if (needsInstallPermission(context)) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /**
     * 把用户从系统文件选择器挑的安装包复制到私有目录再安装。
     * 直接从 SAF 的 content:// 交给安装器会因读取授权不稳而失败，复制一份最省事。
     */
    suspend fun installFromUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val target = File(downloadDir(context), "whale-picked.apk")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false
        }.getOrElse { return@withContext false }
        withContext(Dispatchers.Main) { install(context, target) }
    }
}
