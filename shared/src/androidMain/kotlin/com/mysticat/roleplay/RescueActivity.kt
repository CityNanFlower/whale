package com.mysticat.roleplay

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * 崩溃救援页（2026-09-15，第 14 轮）：只做一件事——把主进程的首页重新拉起来。
 *
 * **为什么要单独一个进程**（`android:process=":crashrescue"`）：
 * 崩溃后主进程的主线程 Looper 已经死了，同进程里 startActivity 起来的界面根本跑不起来，
 * 而紧接着的 killProcess 还会把它一起带走（实测结果就是"崩溃后停在桌面，并没有恢复"）。
 * 独立进程由系统新开、不受主进程死亡影响，再由它去拉 MainActivity：
 * 那时主进程已被回收，AMS 会 fork 一个干净的主进程。
 *
 * **为什么在 onResume 里拉、而不是 onCreate**：Android 10 起限制"后台进程启动界面"（BAL），
 * onCreate 阶段本页还没可见，启动请求会被静默丢掉（第一版就踩了这个坑：
 * 日志里能看到 :crashrescue 起来了、却没有第二次 START MainActivity）。
 * 等到 onResume（本页已可见 = 进程处于前台）再启动就放行。
 *
 * 与 `FontProbeActivity` 同一套路（独立进程 + 独立任务栈 + 不留在最近任务里）。
 */
class RescueActivity : Activity() {
    override fun onResume() {
        super.onResume()
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
        // 稍等一下再结束自己：确保启动请求已经交到 AMS 手上（本页是透明的，用户看不到）
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { finish() } }, 800)
    }
}
