package com.mysticat.roleplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.theme.MystiCatTheme

class MainActivity : ComponentActivity() {
    /**
     * 崩溃自愈守卫：首帧渲染成功前留一个标记文件；下次启动发现标记还在
     * = 上一次启动没能撑过首帧（比如坏字体/坏状态炸了首帧）→ 进安全模式，
     * 重置「会炸首帧的外观设置」（自定义字体等），**账号/角色/会话数据一律不动**，
     * 免得用户只能卸载重装、数据全丢。
     */
    private fun startupGuard() {
        val flag = java.io.File(filesDir, "startup_crash_flag")
        val previousCrashed = runCatching { flag.isFile && flag.readText()?.trim() == "1" }.getOrDefault(false)
        if (previousCrashed) {
            runCatching {
                ThemeState.setCustomFont(null, "", persist = true) // 回退内置字体
                // 预留：以后若有其它「会炸首帧的外观设置」，也在这里一并重置
            }
            android.widget.Toast.makeText(
                this,
                "检测到上次启动异常，已重置外观设置；角色与会话数据不受影响",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
        runCatching { flag.writeText("1") }
        // 首帧真的画出来了 = 本次启动成功，清掉守卫标记
        androidx.core.view.OneShotPreDrawListener.add(window.decorView) {
            runCatching { flag.delete() }
        }
    }

    /**
     * 顶层「双击返回退出」的句柄：共享层 `Platform.exitApp()` 的 Android 实现在 AndroidPlatformUi，
     * 而它手里只有 Application context（finish 不了任何界面），所以这里挂出"当前活着的 Activity"。
     * 旋转会重建 Activity：旧实例的 onDestroy 只在 current 仍是自己时才摘牌，不会误清新实例。
     */
    companion object {
        @Volatile
        var current: MainActivity? = null
            private set
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        // edge-to-edge：窗口不随键盘 resize（否则创作页等页面的底部导航栏会被键盘顶起），
        // 各页面自行消费 insets——聊天输入栏用 imePadding 顶起，主导航栏保持贴底被键盘覆盖
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // 关掉系统给导航栏加的「对比度蒙层」：Android 10+ 默认会在手势条/导航栏区域叠一层
        // 半透明黑（navigationBarContrastEnforced），叠在应用自己绘制的颜色上就是一条黑边，
        // 底部导航栏怎么铺色都盖不住。关掉后该区域的颜色完全由应用负责。
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        ThemeState.ensureLoaded()
        // ⚠️ 只在"全新启动"时布防/自愈（2026-09-15）：旋转现在会重建 Activity，
        // 若每次都重新写下崩溃标记，重建期间进程被回收就会把上一次正常启动误判成
        // "没撑过首帧"，进而莫名重置用户的外观设置（字体）。
        if (savedInstanceState == null) {
            startupGuard()
            // 启动自愈（wingding.ttf 崩溃环教训）：设置里挂着的自定义字体若没通过渲染探针
            // （老版本写入的坏字体一应用就在首帧炸掉整个 App），先摘掉保命；
            // 用户之后在字体列表里重新选中时会走探针，能渲染才恢复
            runCatching {
                val stored = Repository.loadSettings().customFontPath
                if (!stored.isNullOrBlank() && !FontProbe.hasPassMarker(this, stored)) {
                    ThemeState.setCustomFont(null, "", persist = true)
                }
            }
        }
        setContent {
            // 用 Compose 的 isSystemInDarkTheme()，这样「跟随系统」能响应运行时切换，而不是启动时的快照
            val systemDark = isSystemInDarkTheme()
            MystiCatTheme(darkTheme = ThemeState.isDark(systemDark)) {
                // 必须显式铺主题背景色：之前用 Color.Unspecified（=不绘制），暗色主题下会露出
                // themes.xml 里白色的 windowBackground，导致透明容器（如聊天页顶栏）白字压白底看不清。
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}

// Routes / AppRoot 已在上移到 jvmSharedMain 的 AppRoot.kt——两端共用同一张导航图。
