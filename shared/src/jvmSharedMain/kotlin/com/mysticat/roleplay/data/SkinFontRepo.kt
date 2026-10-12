package com.mysticat.roleplay.data

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * 皮肤自带字体的按需下载仓库（2026-10-07 第 17 轮；D2 拍板：动态下载、不进包）。
 *
 * 四份 TTF 按 GB2312 常用字表（7543 字符）子集化，共约 12.8MB——字体绑定关系在
 * `WhaleSkin` 的内置皮肤表（米白→思源宋、手账→Yozai、书信→文楷、暗房→Noto 等宽；
 * 米白即设计原型里的「羊皮纸」，2026-10-08 改显示名）。
 * 文件挂公开仓 release（tag `fonts-v1`），下载走与发现页/更新同一条镜像链
 * （[RemoteFetch.githubCandidates]），sha256 校验后落到数据根 `fonts/`
 * （全局一份，不随账号分家；不在清理白名单——删了要重新下载）。
 *
 * 状态是 Compose 可观察 map：预览卡与主题读 [state] / [readyPath] 即自动响应下载完成。
 * 下载不阻塞套用皮肤——先换配色/圆角/贴纸，字体到位后全局换字。
 */
object SkinFontRepo {

    /** 一份皮肤字体：[id] 与 `WhaleSkinSpec.fonts.ui` 里的令牌名对应。 */
    data class SkinFontFile(
        val id: String,
        val fileName: String,
        val label: String,
        val sha256: String,
        val sizeBytes: Long,
    )

    /** 按常用字表子集化后的四份文件；sha256 与字节数是构建产物的冻结值，改文件必须同步改这里。 */
    val FILES = listOf(
        SkinFontFile(
            "notoserif", "whale-notoserif.ttf", "思源宋体",
            "65b0fb30fc5b5d0f18396a07298dd2b28bf157745db8cb19362c4f80dc559912", 2485376L,
        ),
        SkinFontFile(
            "yozai", "whale-yozai.ttf", "Yozai",
            "0e6efd707ad20146c6e991b626f7ca70b1b7469600f7096d0ab72d339303ee8d", 4162080L,
        ),
        SkinFontFile(
            "wenkai", "whale-wenkai.ttf", "霞鹜文楷",
            "2f76c3cbf8335f5673b2e84e4f07daadaa51f564f7530301693cf4a5481fc77e", 3601100L,
        ),
        SkinFontFile(
            "notomono", "whale-notomono.ttf", "Noto 等宽",
            "e30d030e3b4d56f7fac2426fd23089a5c4a84199ee871195fbb36174175c76e4", 3133756L,
        ),
    )

    fun byId(id: String?): SkinFontFile? = FILES.firstOrNull { it.id == id }

    private const val BASE_URL = "https://github.com/CityNanFlower/whale/releases/download/fonts-v1/"

    /** 下载状态；缺省＝[Absent]（本地没有、也没在下载）。 */
    sealed interface State {
        data object Absent : State
        data object Downloading : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val states = mutableStateMapOf<String, State>()

    fun state(id: String): State = states[id] ?: State.Absent

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 本地已就绪的文件路径。文件在下载时已经过 sha256 与字体引擎双验，存在即认为可用。 */
    fun readyPath(id: String): String? {
        val f = byId(id) ?: return null
        val file = Repository.skinFontFile(f.fileName)
        return if (file.isFile && file.length() > 0L) file.absolutePath else null
    }

    /**
     * 确保字体在本地：没有且没在下载就拉一次。调用方不等它——完成后经
     * [ThemeState.onSkinFontReady] 回接主题，失败留在 [State.Failed] 由卡片状态行给重试。
     */
    fun ensure(id: String) {
        val f = byId(id) ?: return
        if (readyPath(id) != null || states[f.id] is State.Downloading) return
        states[f.id] = State.Downloading
        scope.launch {
            try {
                val bytes = RemoteFetch.fetchBytes(RemoteFetch.githubCandidates(BASE_URL + f.fileName))
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                if (!digest.equals(f.sha256, ignoreCase = true)) error("字体校验失败（sha256 不符）")
                Repository.saveSkinFont(f.fileName, bytes)
                states[f.id] = State.Ready
                ThemeState.onSkinFontReady(f.id)
            } catch (t: Throwable) {
                states[f.id] = State.Failed(t.message ?: "下载失败")
            }
        }
    }
}
