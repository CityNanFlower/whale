package com.mysticat.roleplay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.OneShotPreDrawListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 字体渲染探针（wingding.ttf 崩溃问题的根治）：
 *
 * Compose 的 Font(File) 在**渲染期**加载字体，加载失败会直接抛 IllegalStateException
 * 把整个进程带走——无法在主进程里 try/catch（异常从 measure 阶段穿出，且部分字体
 * 如 wingding.ttf 能通过框架 Typeface 加载校验，却过不了 Compose 自己的加载器）。
 *
 * 所以「这个字体能不能被 Compose 渲染」交给一个**独立进程**的一次性透明 Activity 去试：
 * 排版成功 → 首帧前写结果文件 "ok" 并退出；失败 → 探针进程崩溃（主进程毫发无损），
 * 上传侧等不到 "ok" 即判定不受支持。
 */
object FontProbe {
    const val EXTRA_PATH = "font_path"

    fun resultFile(context: Context): File = File(context.filesDir, "font_probe_result.txt")

    /** 探针通过的标记文件（键 = 字体路径的 SHA-1）。见 MainActivity 的启动自愈。 */
    private fun markerFile(context: Context, fontPath: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(fontPath.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "font_probe_ok"), name)
    }

    fun hasPassMarker(context: Context, fontPath: String): Boolean =
        runCatching { markerFile(context, fontPath).isFile }.getOrDefault(false)

    private fun markPass(context: Context, fontPath: String) {
        runCatching {
            markerFile(context, fontPath).let { it.parentFile?.mkdirs(); it.writeText("ok") }
        }
    }

    /** true = Compose 能实际渲染这个字体；false = 会崩溃，不能收 */
    suspend fun check(context: Context, fontPath: String): Boolean = withContext(Dispatchers.IO) {
        val result = resultFile(context)
        result.delete()
        val intent = Intent(context, FontProbeActivity::class.java)
            .putExtra(EXTRA_PATH, fontPath)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.getOrElse { return@withContext false }
        // 首帧通常 <300ms；探针进程若崩溃则永远等不到 ok，超时即判负
        repeat(30) {
            if (runCatching { result.takeIf { it.isFile }?.readText()?.trim() == "ok" }.getOrDefault(false)) {
                markPass(context, fontPath)
                return@withContext true
            }
            delay(100)
        }
        false
    }
}

/** 独立进程（:fontprobe）的一次性透明探针页，见 [FontProbe] 注释 */
class FontProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent?.getStringExtra(FontProbe.EXTRA_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }
        val result = FontProbe.resultFile(this)
        result.delete()
        setContent {
            Column {
                // 首帧排版这个字体若失败，异常会在 measure 阶段炸掉探针进程（这正是探针的用途）
                Text("Whale 字体探针 The quick brown fox 1234。", fontFamily = FontFamily(Font(File(path))))
                Text("0123456789 楷宋仿黑，。！？")
            }
        }
        OneShotPreDrawListener.add(window.decorView) {
            runCatching { result.writeText("ok") }
            finish()
        }
    }
}
