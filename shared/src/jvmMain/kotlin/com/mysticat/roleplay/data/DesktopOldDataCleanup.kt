package com.mysticat.roleplay.data

import com.mysticat.roleplay.ui.DesktopOldDirNotice
import com.mysticat.roleplay.ui.showToast
import java.io.File

/**
 * 旧数据/缓存目录清理（1.0.2）。
 *
 * 背景：改数据目录后，旧默认目录（打包态＝`%APPDATA%\MysticatRoleplay`；开发态＝`MysticatRoleplay-dev`，
 * 2026-09-21 起两边锚点分家）会残留 app.lock / secret.key /
 * 空的 roleplay/ 等。用户问"可不可以删掉"。事故铁律：**有数据绝不默认删**——
 * 所以"目录里有没有用户数据"必须是可测的判定，而不是看目录名猜：
 *  - 只剩白名单内的可再生残留（锁/密钥/日志/窗口与行为偏好/崩溃记录/语音工具脚本/空目录）
 *    → 判"干净"，启动时自动清掉并 toast 告知；
 *  - 出现任何白名单之外的文件（典型＝roleplay/ 下的角色卡与会话 JSON）→ 判"有数据"，
 *    只弹提示（打开 / 删除 / 暂不处理），删除必须由用户在弹层里对着写明路径的文案点确认。
 *
 * 锚点目录里还有 `paths.properties` 与 `onboarding.done`（**新目录的配置**，就在旧目录里）——
 * 它们既不算残留也绝不能删，见 [KEEP_CONFIG]。锚点根目录本身永远保留。
 */
object DesktopOldDataCleanup {

    private const val TAG = "WhaleCleanup"

    /** 删了也不丢用户内容的文件名（可再生或一次性现场物） */
    private val DELETABLE_NAMES = setOf(
        "app.lock", "secret.key", "whale.log", "whale.log.1", "whale.log.2",
        "window.properties", "desktop.properties", "crash.log"
    )

    /** 崩溃现场文件前缀（hs_err_pid*.log），可再生 */
    private const val HS_ERR_PREFIX = "hs_err_pid"

    /** 新目录的配置，住在锚点目录里——判定时中性、清理与删除时都绝不动 */
    private val KEEP_CONFIG = setOf("paths.properties", "onboarding.done")

    /** 整棵子树可再生，不必逐文件判断（语音工具脚本 tools/） */
    private val REGENERABLE_DIRS = setOf("tools")

    /**
     * 启动时调用（此时 [currentDataDir]/[currentCacheDir] 已按 paths.properties 生效）。
     * [defaultAnchorName] 必须与运行语境的锚点名一致（打包态 `MysticatRoleplay` / dev `MysticatRoleplay-dev`，
     * 2026-09-21 起）——① 里"旧默认缓存目录"按它拼，传错语境会把**对方**的默认缓存当旧缓存无条件清掉。
     * 前置：平台 UI 已注入（bootstrap 之后），toast 与提示状态才有落点。
     */
    fun check(
        anchorDir: File,
        currentDataDir: File,
        currentCacheDir: File,
        defaultAnchorName: String = "MysticatRoleplay"
    ) {
        val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: return
        val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: appData

        // ① 旧缓存目录：整体就是缓存，没有"用户数据"概念，换目录后直接清
        val defaultCache = File(File(localAppData, defaultAnchorName), "cache")
        runCatching {
            if (defaultCache.isDirectory &&
                defaultCache.canonicalPath != File(currentCacheDir.canonicalPath).canonicalPath
            ) {
                val ok = defaultCache.deleteRecursively()
                WhaleLog.i(TAG, "旧缓存目录${if (ok) "已清理" else "清理失败"}：${defaultCache.absolutePath}")
            }
        }.onFailure { WhaleLog.w(TAG, "旧缓存目录检查失败：${it.message}") }

        // ② 旧数据目录（默认锚点）：仅当当前数据目录真的不在默认位置时才有"旧"可言。
        //    防呆：当前目录若是旧目录的子目录（把数据指进了默认目录内部），绝不能清理。
        runCatching {
            val old = anchorDir.canonicalFile
            val cur = currentDataDir.canonicalFile
            if (!old.isDirectory || cur == old || cur.path.startsWith(old.path + File.separator)) return
            if (onlyLeftovers(old)) {
                val n = cleanLeftovers(old)
                if (n > 0) {
                    WhaleLog.i(TAG, "旧数据目录仅剩可再生残留，已清理 $n 项：${old.absolutePath}")
                    showToast("已清理旧数据目录残留：${old.absolutePath}")
                }
            } else {
                DesktopOldDirNotice.dirWithData = old.absolutePath
                WhaleLog.i(TAG, "旧数据目录还有用户数据，等用户决定：${old.absolutePath}")
            }
        }.onFailure { WhaleLog.w(TAG, "旧数据目录检查失败：${it.message}") }
    }

    /**
     * 判定 [dir] 是否只剩可再生残留（true＝无用户数据；目录不存在/为空也算）。
     * 递归对整棵树生效：`roleplay/` 下只要有一个非白名单文件就判"有数据"。
     */
    fun onlyLeftovers(dir: File): Boolean {
        if (!dir.isDirectory) return true
        return dir.listFiles().orEmpty().all { f ->
            when {
                f.isDirectory && f.name in REGENERABLE_DIRS -> true
                f.isDirectory -> onlyLeftovers(f)
                f.name in KEEP_CONFIG -> true
                f.name in DELETABLE_NAMES || f.name.startsWith(HS_ERR_PREFIX) -> true
                else -> false
            }
        }
    }

    /**
     * 删掉白名单内的残留文件、可再生目录与清空后的空目录（如空的 `roleplay/`）。
     * 返回删除条数。配置文件（[KEEP_CONFIG]）与根目录本身绝不动。
     */
    fun cleanLeftovers(dir: File): Int {
        if (!dir.isDirectory) return 0
        var n = 0
        dir.listFiles().orEmpty().forEach { f ->
            when {
                f.isDirectory && f.name in REGENERABLE_DIRS -> if (f.deleteRecursively()) n++
                f.isDirectory -> n += cleanLeftovers(f)
                f.name in DELETABLE_NAMES || f.name.startsWith(HS_ERR_PREFIX) -> if (f.delete()) n++
            }
        }
        dir.listFiles().orEmpty()
            .filter { it.isDirectory && it.listFiles().isNullOrEmpty() }
            .forEach { if (it.delete()) n++ }
        return n
    }

    /**
     * 用户在弹层里确认后的删除：删掉锚点目录里**配置文件之外**的整棵树（含用户数据），
     * 根目录与 paths.properties / onboarding.done 保留——新目录的配置还指着它们。
     */
    fun deleteAllButConfig(dir: File): Boolean {
        if (!dir.isDirectory) return true
        var ok = true
        dir.listFiles().orEmpty().forEach { f ->
            if (f.name in KEEP_CONFIG) return@forEach
            ok = runCatching {
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }.getOrDefault(false) && ok
        }
        WhaleLog.i(TAG, "用户确认删除旧数据目录（${if (ok) "成功" else "部分失败"}）：${dir.absolutePath}")
        return ok
    }
}
