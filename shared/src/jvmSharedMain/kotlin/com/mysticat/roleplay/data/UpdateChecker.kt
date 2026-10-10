package com.mysticat.roleplay.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 远端版本清单（`version.json`）的读取与比对。
 *
 * 从 `VersionSecurityScreen` 里抽出来：**托盘菜单也要能查更新**，而托盘住在 desktopApp、
 * 拿不到那个 Composable 里的私有实现。抽成不依赖 UI 的一层后，两个入口（「关于鲸鱼」页 / 托盘菜单）
 * 共用同一份口径——包括那个"追加时间戳穿透 CDN 缓存"的细节，漏了它会变成"改了 version.json 没生效"。
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
    /** 可选：安装包 SHA-256（version.json 提供时才校验；旧格式省略该字段依旧可用） */
    val sha256: String = "",
    /** 可选：安装程序形态用的安装器地址（缺省时退回 zip 链路） */
    val installerUrl: String = "",
    /** 可选：安装器 SHA-256（同上，随 installerUrl 一起给） */
    val installerSha256: String = ""
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
}

/**
 * 这次该下载哪一个包（[isInstaller] 决定拿到文件后的安装方式：拉起安装器 vs 解压替换）。
 * 由 [UpdateChecker.target] 按"是不是安装程序形态"分流，调用方不再自己判。
 */
data class UpdateTarget(val url: String, val sha256: String, val isInstaller: Boolean)

object UpdateChecker {

    private const val TIMEOUT_CONNECT = 4L
    private const val TIMEOUT_READ = 4L
    private const val TIMEOUT_CALL = 6L

    private val http = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_CONNECT, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_READ, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_CALL, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 按 [urls] 顺序依次尝试，第一个成功的即采用；全部失败才抛异常，由调用处转成给用户看的文案。
     * 公开链接直接拉；[token] 非空时带 Bearer（私有仓库场景）。
     *
     * [nodeKey] 是这份 JSON 里本平台节点的键（桌面 `windows`；Android 传 null＝读顶层字段）。
     * 节点不存在＝这个平台还没发过版，返回 null（按"没有更新信息"处理，不当成错误）。
     */
    fun fetch(urls: List<String>, token: String = "", nodeKey: String? = null): UpdateInfo? {
        var lastError: Exception? = null
        val stamp = System.currentTimeMillis()
        for (url in urls) {
            try {
                return fetchOne(withCacheBuster(url, stamp), token, nodeKey)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("网络不可达")
    }

    /**
     * 给 URL 追加/替换 `_t` 时间戳参数（缓存键每次都不同 → 穿透 CDN 缓存）。
     *
     * 缓存穿透：GitHub 源站与两个镜像都带 CDN 缓存（实测 gh-proxy max-age=60、ghfast max-age=300），
     * 改完 version.json 后最长 5 分钟内仍会拿到旧内容——发版验证时极容易被误判成"改了没生效"。
     */
    internal fun withCacheBuster(url: String, stamp: Long): String {
        val sep = if ('?' in url) '&' else '?'
        return "$url${sep}_t=$stamp"
    }

    /**
     * 挑出这次要下载的包：[viaInstaller]（桌面程序目录不可写）且远端给了安装器地址时走 exe，
     * 否则退回便携包/APK。清单里连 url 都没给时用 [fallbackUrl]（编译期常量），两条链路都不空转。
     * 地址仍为空则返回 null（调用方提示"下载地址尚未配置"）。
     */
    fun target(info: UpdateInfo, viaInstaller: Boolean, fallbackUrl: String = ""): UpdateTarget? {
        if (viaInstaller && info.installerUrl.isNotBlank()) {
            return UpdateTarget(info.installerUrl, info.installerSha256, isInstaller = true)
        }
        val url = fallbackUrl.ifBlank { info.url }
        return UpdateTarget(url, info.sha256, isInstaller = false).takeIf { it.url.isNotBlank() }
    }

    private fun fetchOne(url: String, token: String, nodeKey: String?): UpdateInfo? {
        val builder = Request.Builder().url(url).get()
        // 双保险：有的 CDN 会忽略查询串而按 Cache-Control 复用，显式声明不用缓存
        builder.header("Cache-Control", "no-cache")
        builder.header("Pragma", "no-cache")
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = resp.body?.string() ?: return null
            val root = json.parseToJsonElement(body).jsonObject
            // 两个平台共用同一份 version.json，桌面读 `windows` 节点（Android 仍是顶层字段）
            val node = nodeKey?.let { key -> root[key]?.jsonObject ?: return null } ?: root
            return UpdateInfo(
                versionCode = node["versionCode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                versionName = node["versionName"]?.jsonPrimitive?.content ?: "",
                url = node["url"]?.jsonPrimitive?.content ?: "",
                notes = node["notes"]?.jsonPrimitive?.content ?: "",
                sha256 = node["sha256"]?.jsonPrimitive?.content ?: "",
                installerUrl = node["installerUrl"]?.jsonPrimitive?.content ?: "",
                installerSha256 = node["installerSha256"]?.jsonPrimitive?.content ?: ""
            )
        }
    }
}
